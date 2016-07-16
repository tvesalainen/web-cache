/*
 * Copyright (C) 2016 tkv
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vesalainen.web.cache;

import org.vesalainen.web.parser.HttpHeaderParser;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import static java.nio.file.LinkOption.*;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import org.vesalainen.lang.Primitives;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.nio.PeekReadCharSequence;
import org.vesalainen.nio.file.attribute.UserDefinedFileAttributes;
import org.vesalainen.regex.SyntaxErrorException;
import org.vesalainen.time.SimpleMutableDateTime;
import org.vesalainen.util.ThreadSafeTemporary;
import org.vesalainen.util.concurrent.WaiterList;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.Scheme;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
public class CacheEntry extends JavaLogging implements Callable<Boolean>, Comparable<CacheEntry>
{
    private boolean heuristic;

    public enum State {UserAgentGaveUp, Timeout, NoMatch, Error, NotCached, NotModified, New, Partial, Full};
    private State state;
    private final Path path;
    private FileChannel fileChannel;
    private ThreadSafeTemporary<ByteBuffer> bbStore;
    private ByteBuffer responseBuffer;
    private HttpHeaderParser response;
    private HttpHeaderParser request;
    private String requestTarget;
    private WaiterList<Receiver> receiverList;
    private WaiterList<Object> fullWaiters;
    private long contentLength;
    private ByteChannel originServer;
    private BasicFileAttributeView basicAttr;
    private UserDefinedFileAttributes userAttr;
    private CacheEntry stale;
    private int startCount;
    private boolean running;
    private VaryMap varyMap = VaryMap.Empty;

    public CacheEntry(Path path, HttpHeaderParser request)
    {
        this(path.toFile(), request, null);
    }

    public CacheEntry(File file, HttpHeaderParser request, CacheEntry stale)
    {
        super(CacheEntry.class);
        try
        {
            this.path = file.toPath();
            basicAttr = Files.getFileAttributeView(path, BasicFileAttributeView.class, NOFOLLOW_LINKS);
            userAttr = new UserDefinedFileAttributes(path, BufferSize, NOFOLLOW_LINKS);
            this.request = request;
            this.requestTarget = request.getRequestTarget();
            this.stale = stale;
            fileChannel = FileChannel.open(path, READ, WRITE);
            if (userAttr.has(XOrigRequestTarget))
            {
                String req = userAttr.getString(XOrigRequestTarget);
                if (req != null)
                {
                    if (!req.equals(requestTarget))
                    {
                        throw new IllegalArgumentException("stored "+req+" != used "+requestTarget);
                    }
                }
            }
            receiverList = new WaiterList<>();
            fullWaiters = new WaiterList<>();
            bbStore = new ThreadSafeTemporary<>(()->{return ByteBuffer.allocateDirect(BufferSize);});
            responseBuffer = ByteBuffer.allocateDirect(BufferSize);
            response = HttpHeaderParser.getInstance(Scheme.HTTP, responseBuffer);
            refresh();
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        if (originServer != null)
        {
            originServer.close();
        }
        if (fileChannel != null)
        {
            fileChannel.close();
        }
    }

    public State readFromCache(HttpHeaderParser request, SocketChannel channel) throws IOException
    {
        return readFromCache(request, channel, Long.MAX_VALUE);
    }
    public State readFromCache(HttpHeaderParser req, SocketChannel userAgent, long timeoutMillis) throws IOException
    {
        switch (state)
        {
            case Error:
            case NotModified:
            case NotCached:
                return state;
            case Full:
                return sendFullResponse(req, userAgent);
            default:
                ensureRunning();
                if (timeoutMillis == Long.MAX_VALUE)
                {
                    Receiver receiver = new Receiver(req, userAgent, Thread.currentThread());
                    receiver.update();
                    switch (receiverList.wait(receiver, timeoutMillis, TimeUnit.MILLISECONDS))
                    {
                        case Release:
                            return state;
                        case Interrupt:
                            return State.NoMatch;
                        default:
                            throw new IllegalStateException();
                    }
                }
                else
                {
                    switch (fullWaiters.wait(this, timeoutMillis, TimeUnit.MILLISECONDS))
                    {
                        case Release:
                            if (State.Full.equals(state))
                            {
                                return sendFullResponse(req, userAgent);
                            }
                            return state;
                        case Timeout:
                            return State.Timeout;
                        default:
                            throw new IllegalStateException();
                    }
                }
        }
    }
    @Override
    public Boolean call() throws Exception
    {
        running = true;
        startCount++;
        fine("%d start with new thread %s", startCount, this);
        try
        {
            if (startTransfer())
            {
                transferFrom();
            }
            if (contentLength == Integer.MAX_VALUE)
            {
                contentLength = fileChannel.size();
            }
            updateState();
            switch (state)
            {
                case Error:
                case NotModified:
                    try
                    {
                        if (stale != null)
                        {
                            stale.updateNotModifiedResponse(null);
                        }
                        stale  = null;
                        deleteFile();
                        finest("release full-waiters %s", this);
                        return true;
                    }
                    finally
                    {
                        receiverList.releaseAll();
                        fullWaiters.releaseAll();
                    }
                case Full:
                    try
                    {
                        if (!response.isCacheable())
                        {
                            deleteFile();
                            state = State.NotCached;
                            return true;
                        }
                        storeDigest();
                        if (stale != null && UserDefinedFileAttributes.equals(SHA1, userAttr, stale.userAttr))
                        {
                            stale.updateNotModifiedResponse(response);
                            deleteFile();
                            state = State.NotModified;
                        }
                        stale  = null;
                        finest("release full-waiters %s", this);
                        return true;
                    }
                    finally
                    {
                        receiverList.releaseAll();
                        fullWaiters.releaseAll();
                    }
                default:
                    return false;
            }
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, ex, "%s", ex.getMessage());
            return false;
        }
        finally
        {
            running = false;
            if (originServer != null)
            {
                originServer.close();
            }
        }
    }

    private void deleteFile() throws IOException
    {
        if (fileChannel != null)
        {
            fileChannel.close();
            fileChannel = null;
        }
        Cache.queueDelete(path);
        fine("enqueued for deletion %s", path);
    }
    private boolean startTransfer() throws IOException
    {   
        switch (state)
        {
            case New:
                if (stale != null && request.isRefreshAttempt())
                {
                    return conditionalGet();
                }
                else
                {
                    return initialGet();
                }
            case Partial:
                return partialGet();
            default:
                throw new UnsupportedOperationException(state+" not supported");
        }
    }

    private void transferFrom() throws IOException
    {
        long quitTime = 0;
        int maxTransferSize = Cache.getMaxTransferSize();
        long fileSize = contentLength;
        long currentSize = fileChannel.size();
        while (currentSize < fileSize)
        {
            if (quitTime > 0 && Cache.getClock().millis() > quitTime)
            {
                fine("giving up because all clients did so %s %d / %d rc=%d", requestTarget, currentSize, fileSize, receiverList.size());
                return;
            }
            long rc = fileChannel.transferFrom(originServer, currentSize, Math.min(maxTransferSize, fileSize - currentSize));
            if (rc == 0)
            {
                finest("transferFrom:%s %d / %d rc=0", requestTarget, currentSize, fileSize);
                return;
            }
            currentSize += rc;
            if (quitTime == 0 && !hasClients())
            {
                fine("no more clients %s %d / %d rc=%d", requestTarget, currentSize, fileSize, receiverList.size());
                quitTime = Cache.getClock().millis() + Cache.getTimeoutAfterUserQuit();
            }
            receiverList.stream().forEach(Receiver::update);
            debug("transferFrom:%s %d / %d", requestTarget, currentSize, fileSize);
        }
        finest("transferFrom:%s %d / %d ready", requestTarget, currentSize, fileSize);
    }

    private boolean initialGet() throws IOException
    {
        fine("initialGet()");
        RequestBuilder builder = new RequestBuilder(bbStore.get(), request, Connection, ProxyConnection, IfModifiedSince, IfNoneMatch, Range, IfRange);
        builder.addHeader(Connection, "close");
        if (fetchHeader(builder))
        {
            if (response.getStatusCode() == 200)
            {
                responseBuffer.position(response.getHeaderSize());
                fileChannel.write(responseBuffer, 0);
                updateState();
                receiverList.stream().filter(Receiver::noMatch).forEach(Receiver::interrupt);
                receiverList.stream().forEach(Receiver::header);
                receiverList.stream().forEach(Receiver::update);
                return true;
            }
            else
            {
                if (response.getStatusCode() < 500)
                {
                    receiverList.stream().forEach(Receiver::received);
                    fine("set to error because: %s", response);
                    state = State.Error;
                }
                return contentLength != Integer.MAX_VALUE;
            }
        }
        return false;
    }

    private boolean conditionalGet() throws IOException
    {
        fine("conditionalGet()");
        RequestBuilder builder = new RequestBuilder(bbStore.get(), request, Connection, ProxyConnection);
        builder.addHeader(Connection, "close");
        if (fetchHeader(builder))
        {
            if (response.getStatusCode() == 304)
            {
                receiverList.stream().forEach(Receiver::notModified);
                stale.updateNotModifiedResponse(response);
                state = State.NotModified;
                return false;
            }
            fine("%s %d", request.getRequestTarget(), response.getStatusCode());
            if (response.getStatusCode() == 200)
            {
                responseBuffer.position(response.getHeaderSize());
                fileChannel.write(responseBuffer, 0);
                updateState();
                receiverList.stream().filter(Receiver::noMatch).forEach(Receiver::interrupt);
                receiverList.stream().forEach(Receiver::header);
                receiverList.stream().forEach(Receiver::update);
                return true;
            }
            else
            {
                if (response.getStatusCode() < 500)
                {
                    receiverList.stream().forEach(Receiver::received);
                    fine("set to error because: %s", response);
                    state = State.Error;
                }
            }
        }
        return false;
    }

    private boolean partialGet() throws IOException
    {
        fine("partialGet()");
        RequestBuilder builder = new RequestBuilder(bbStore.get(), request, Connection, ProxyConnection, IfModifiedSince, IfNoneMatch, IfRange, Range);
        builder.addHeader(Connection, "close");
        if (response.hasHeader(ETag) || response.hasHeader(LastModified))
        {
            if (response.acceptRanges())
            {
                if (response.hasHeader(ETag))
                {
                    ByteBufferCharSequence eTag = response.getHeader(ETag);
                    builder.addHeader(IfRange, eTag.toString());
                }
                else
                {
                    ByteBufferCharSequence lm = response.getHeader(LastModified);
                    builder.addHeader(IfRange, lm.toString());
                }
                long range = fileChannel.size();
                builder.addHeader(Range, "bytes="+range+"-");
            }
            else
            {
                if (response.hasHeader(ETag))
                {
                    ByteBufferCharSequence eTag = response.getHeader(ETag);
                    builder.addHeader(IfMatch, eTag.toString());
                }
                else
                {
                    ByteBufferCharSequence lm = response.getHeader(LastModified);
                    builder.addHeader(IfUnmodifiedSince, lm.toString());
                }
            }
        }
        long originalContentSize = contentLength;
        try
        {
            if (fetchHeader(builder))
            {
                int statusCode = response.getStatusCode();
                fine("%s %d", request.getRequestTarget(), statusCode);
                switch (statusCode)
                {
                    case 200:
                        fileChannel.truncate(0);
                    case 206:   // note missing break!!!
                        responseBuffer.position(response.getHeaderSize());
                        fileChannel.write(responseBuffer, fileChannel.size());
                        updateState();
                        receiverList.stream().forEach(Receiver::update);
                        return true;
                    default:
                        receiverList.stream().forEach(Receiver::received);
                        fine("set to error because: %s", response);
                        state = State.Error;
                        return false;
                }
            }
        }
        finally
        {
            contentLength = originalContentSize;
        }
        return false;
    }

    private State sendFullResponse(HttpHeaderParser req, SocketChannel userAgent) throws IOException
    {
        if (req.isRefreshAttempt())
        {
            if (notModified(req))
            {
                sendHeader(userAgent, 304);
                return state;
            }
        }
        sendHeader(userAgent, 200);
        sendAll(userAgent);
        return state;
    }

    private boolean fetchHeader(RequestBuilder builder) throws IOException
    {
        String host = request.getHost();
        int port = request.getPort();
        originServer = ConnectionHandler.open(request.getScheme(), host, port);
        if (originServer != null)
        {
            fine("send to origin %s", builder.getString());
            builder.send(originServer);
            responseBuffer.clear();
            while (!response.hasWholeHeader())
            {
                int rc = originServer.read(responseBuffer);
                if (rc == -1)
                {
                    fine("originServer closed while reading header %s", response);
                    return false;
                }
            }
            parseResponse(Cache.getClock().millis());
            updateNotModifiedResponse(response);
            return true;
        }
        return false;
    }

    public void setCreateTime() throws IOException
    {
        FileTime ft = FileTime.from(Instant.now(Cache.getClock()));
        basicAttr.setTimes(ft, ft, ft);
    }
    
    public void setLastAccessTime() throws IOException
    {
        FileTime ft = FileTime.from(Instant.now(Cache.getClock()));
        basicAttr.setTimes(null, ft, null);
    }
    
    public void setLastModifiedTime() throws IOException
    {
        FileTime ft = FileTime.from(Instant.now(Cache.getClock()));
        basicAttr.setTimes(ft, ft, null);
    }
    
    public long getCreated() throws IOException
    {
        return basicAttr.readAttributes().creationTime().toMillis();
    }
    public long getLastModified() throws IOException
    {
        return basicAttr.readAttributes().lastModifiedTime().toMillis();
    }
    public long getLastAccess() throws IOException
    {
        return basicAttr.readAttributes().lastAccessTime().toMillis();
    }
    public void setAttribute(String name, String value) throws IOException
    {
        setAttribute(name, new ByteBufferCharSequence(value));
    }
    public void setAttribute(String name, ByteBufferCharSequence value) throws IOException
    {
        ByteBuffer byteBuffer = value.getByteBuffer();
        userAttr.write(name, byteBuffer);
        value.reset();
    }
    public CharSequence getAttribute(String name) throws IOException
    {
        if (userAttr.list().contains(name))
        {
            ByteBuffer b = ByteBuffer.allocate(userAttr.size(name));
            userAttr.read(name, b);
            b.flip();
            return new ByteBufferCharSequence(b, OP);
        }
        return null;
    }
    public Path getPath()
    {
        return path;
    }

    public VaryMap getVaryMap()
    {
        return varyMap;
    }
    
    private byte[] storeDigest() throws IOException
    {
        try
        {
            MessageDigest sha1 = MessageDigest.getInstance(SHA1);
            
            ByteBuffer bb = bbStore.get();
            long position = 0;
            long size = fileChannel.size();
            while (position < size)
            {
                bb.clear();
                int rc = fileChannel.read(bb, position);
                assert rc != -1;
                position += rc;
                bb.flip();
                int lim = bb.limit();
                for (int ii=bb.position();ii<lim;ii++)
                {
                    sha1.update(bb.get());
                }
            }
            byte[] digest = sha1.digest();
            userAttr.set(SHA1, digest);
            return digest;
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    private void updateState() throws IOException
    {
        if (State.NotCached.equals(state) || State.NotModified.equals(state) || State.Error.equals(state))
        {
            return;
        }
        long size = fileChannel.size();
        if (size > 0 || response.hasWholeHeader())
        {
            if (size >= contentLength)
            {
                state = State.Full;
            }
            else
            {
                state = State.Partial;
            }
        }
        else
        {
            state = State.New;
        }
        if (!State.Full.equals(state) && requestTarget == null)
        {
            throw new IllegalArgumentException(state+" but no requestTarget");
        }
    }
    private void refresh()
    {
        try
        {
            if (fileChannel.size() > 0)
            {
                checkFileHeader();
            }
            updateState();
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    public void ensureRunning()
    {
        if (!running)
        {
            switch (state)
            {
                case New:
                case Partial:
                    Cache.submit(this);
                    break;
            }
        }
    }
    public boolean isStale() throws IOException
    {
        if (State.Full.equals(state))
        {
            long freshnessLifetime = freshnessLifetime();
            long currentAge = currentAge();
            finest("freshnessLifetime %d currentAge %d for %s", freshnessLifetime, currentAge, requestTarget);
            return  freshnessLifetime <= currentAge;
        }
        return false;
    }

    public long refreshness()
    {
        if (State.New.equals(state))
        {
            return 0;
        }
        return freshnessLifetime() - currentAge();
    }
    
    private long freshnessLifetime()
    {
        try
        {
            heuristic = false;
            long freshnessLifetime = response.freshnessLifetime();
            if (freshnessLifetime == -1)
            {
                if (userAttr.has(NotModifiedCount))
                {
                    heuristic = true;
                    int notModifiedCount = userAttr.getInt(NotModifiedCount);
                    long lastNotModified = userAttr.getLong(LastNotModified);
                    long created = getCreated();
                    long seconds = (lastNotModified - created) / 1000;
                    finest("heuristic cnt=%d cr=%s lm=%s d=%d", notModifiedCount, created, lastNotModified, seconds);
                    return seconds + notModifiedCount*seconds/10;
                }
                else
                {
                    return 0;
                }
            }
            else
            {
                return freshnessLifetime;
            }
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    private long currentAge()
    {
        try
        {
            long ageValue = response.getNumericHeader(Age);
            debug("ageValue=%d", ageValue);
            ageValue = ageValue != -1 ? ageValue : 0;
            SimpleMutableDateTime date = response.getDateHeader(Date);
            debug("date=%s", date);
            SimpleMutableDateTime responseTime = response.getTime();
            debug("responseTime=%s", responseTime);
            SimpleMutableDateTime requestTime = request.getTime();
            debug("requestTime=%s", requestTime);
            long apparentAge = 0;
            if (date != null)
            {
                apparentAge = Math.max(0, responseTime.seconds() - date.seconds());
            }
            else
            {
                long created = getCreated();
                debug("created=%s", created);
                apparentAge = Math.max(0, responseTime.seconds() - created/1000);
            }
            debug("apparentAge=%d", apparentAge);
            long responseDelay = Math.max(0, responseTime.seconds() - requestTime.seconds());
            debug("responseDelay=%d", responseDelay);
            long correctedAgeValue = ageValue + responseDelay;
            debug("correctedAgeValue=%d", correctedAgeValue);
            long correctedInitialAge = Math.max(apparentAge, correctedAgeValue);
            debug("correctedInitialAge=%d", correctedInitialAge);
            SimpleMutableDateTime now = SimpleMutableDateTime.now(Cache.getClock());
            debug("now=%s", now);
            long residentTime = now.seconds() - responseTime.seconds();
            debug("residentTime=%d", residentTime);
            long currentAge = correctedInitialAge + residentTime;
            debug("currentAge=%d", currentAge);
            return currentAge;
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    public String getRequestTarget()
    {
        return requestTarget;
    }

    public boolean notModified(HttpHeaderParser request)
    {
        finest("check not modified");
        List<CharSequence> eTags = request.getCommaSplittedHeader(IfNoneMatch);
        if (eTags != null)
        {
            ByteBufferCharSequence eTag = response.getHeader(ETag);
            boolean ok = eTags.stream().anyMatch((s)->{return Headers.eTagWeakEquals(s, eTag);});
            finest("ETag match %s", eTag);
            return ok;
        }
        SimpleMutableDateTime ifModifiedSince = request.getDateHeader(IfModifiedSince);
        if (ifModifiedSince != null)
        {
            SimpleMutableDateTime lastModified = response.getDateHeader(LastModified);
            if (lastModified != null)
            {
                boolean ok = !lastModified.isAfter(ifModifiedSince);
                finest("%s match %s", ifModifiedSince, lastModified);
                return ok;
            }
        }
        finest("no match");
        return false;
    }
    public boolean matchRequest(HttpHeaderParser request)
    {
        if (State.NotModified.equals(state) || State.Error.equals(state) || State.NotCached.equals(state))
        {
            finest("not match because is %s", state);
            return false;
        }
        String requestTarget2 = request.getRequestTarget();
        if (!requestTarget.equals(requestTarget2))
        {
            finest("not match %s <> %s", requestTarget, request.getRequestTarget());
            return false;
        }
        if (!varyMap.isMatch(request))
        {
            finest("Vary not match %s <> %s", requestTarget, request.getRequestTarget());
            return false;
        }
        finest("match %s == %s", requestTarget, request.getRequestTarget());
        return true;
    }
    private boolean checkFileHeader() throws IOException
    {
        try
        {
            if (userAttr.has(XOrigHdr))
            {
                responseBuffer.clear();
                userAttr.read(XOrigHdr, responseBuffer);
                long millis = userAttr.getLong(LastNotModified);
                parseResponse(millis);
                return true;
            }
        }
        catch (SyntaxErrorException ex)
        {
            log(SEVERE, ex, "%s", ex.getMessage());
        }
        return false;
    }
    private void parseResponse(long millis) throws IOException
    {
        responseBuffer.flip();
        response.parseResponse(millis);
        fine("cache received response from %s\n%s", originServer, response);
        contentLength = response.getContentLength();
        varyMap = VaryMap.create(response, request);
    }

    private void sendAll(WritableByteChannel channel) throws IOException
    {
        long size = fileChannel.size();
        long pos = 0;
        while (size > 0)
        {
            long rc = fileChannel.transferTo(pos, size, channel);
            size -= rc;
            pos += rc;
        }
    }

    private void updateNotModifiedResponse(HttpHeaderParser resp) throws IOException
    {
        if (resp != null && resp.getStatusCode() == 200)
        {
            ByteBufferCharSequence headerPart = resp.getHeaderPart();
            setAttribute(XOrigHdr, headerPart);
        }
        int notModifiedCount = 0;
        if (userAttr.has(NotModifiedCount))
        {
            notModifiedCount = userAttr.getInt(NotModifiedCount);
        }
        userAttr.setInt(NotModifiedCount, ++notModifiedCount);
        userAttr.setLong(LastNotModified, Cache.getClock().millis());
        userAttr.setString(XOrigRequestTarget, requestTarget);
    }
    private void sendReceivedHeader(SocketChannel userAgent) throws IOException
    {
        ByteBuffer bb = bbStore.get();
        PeekReadCharSequence peek = null;
        ResponseBuilder builder = new ResponseBuilder(bb, response);
        fine("send to user %s\n%s", userAgent, builder.getString());
        builder.send(userAgent);
    }
    private void sendHeader(SocketChannel userAgent) throws IOException
    {
        sendHeader(userAgent, 200);
    }
    private void sendHeader(SocketChannel userAgent, int responseCode, byte[]... extraHeaders) throws IOException
    {
        ByteBuffer bb = bbStore.get();
        ResponseBuilder builder = new ResponseBuilder(bb, responseCode, response, extraHeaders);
        fine("send to user %s\n%s", userAgent, builder.getString());
        builder.send(userAgent);
    }
    /**
     * Sort in refresh order. Most refresh first.
     * @param o
     * @return 
     */
    @Override
    public int compareTo(CacheEntry o)
    {
        if (state.equals(o.state))
        {
            return Primitives.signum(-refreshness() + o.refreshness());
        }
        else
        {
            return -state.ordinal() + o.state.ordinal();
        }
    }
    
    public boolean isRefreshing(HttpHeaderParser request)
    {
        return stale != null && stale.matchRequest(request);
    }

    public boolean hasClients()
    {
        return !fullWaiters.isEmpty() || 
        !(
            receiverList.isEmpty() ||
            receiverList.stream().allMatch(Receiver::gaveUp)
        );
    }

    public int getStartCount()
    {
        return startCount;
    }

    public State getState()
    {
        return state;
    }
    
    @Override
    public String toString()
    {
        return "CacheEntry{" + requestTarget + " "+path+'}';
    }
    
    private class Receiver
    {
        private final HttpHeaderParser request;
        private SocketChannel userAgent;
        private final Thread thread;
        private long position;

        public Receiver(HttpHeaderParser request, SocketChannel userAgent, Thread thread)
        {
            this.request = request;
            this.userAgent = userAgent;
            this.thread = thread;
        }

        public boolean transferedAny()
        {
            return position > 0;
        }
        
        public boolean gaveUp()
        {
            return userAgent == null;
        }

        public void interrupt()
        {
            thread.interrupt();
        }
        
        public boolean noMatch()
        {
            if (!matchRequest(request))
            {
                finest("set userAgent = null because no match");
                userAgent = null;
                return true;
            }
            else
            {
                return false;
            }
        }
        
        public void received()
        {
            if (userAgent != null)
            {
                try
                {
                    sendReceivedHeader(userAgent);
                }
                catch (IOException ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
        public void header()
        {
            if (userAgent != null)
            {
                try
                {
                    sendHeader(userAgent);
                }
                catch (IOException ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
        public void notModified()
        {
            if (userAgent != null)
            {
                try
                {
                    sendHeader(userAgent, 304);
                }
                catch (IOException ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
        public void update()
        {
            if (userAgent != null)
            {
                try
                {
                    long size = fileChannel.size();
                    long length = size - position;
                    try
                    {
                        while (length > 0)
                        {
                            long rc = fileChannel.transferTo(position, length, userAgent);
                            if (rc == 0)
                            {
                                break;
                            }
                            length -= rc;
                            position += rc;
                        }
                    }
                    catch (IOException ex)
                    {
                        log(Level.FINER, ex, "gave up? %s", requestTarget);
                        userAgent = null;
                    }
                }
                catch (IOException ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
    }
}
