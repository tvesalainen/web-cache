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

import java.io.ByteArrayInputStream;
import org.vesalainen.web.parser.HttpHeaderParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.time.Clock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.vesalainen.nio.channels.ChannelHelper;
import org.vesalainen.nio.file.attribute.ExternalFileAttributes;
import org.vesalainen.util.WeakList;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.Scheme;
import static org.vesalainen.web.cache.CacheConstants.*;
import org.vesalainen.web.cache.CacheEntry.State;
import org.vesalainen.web.https.KeyStoreManager;

/**
 *
 * @author tkv
 */
public class Cache
{
    private static ThreadPoolExecutor executor;
    private static ScheduledExecutorService scheduler;
    private static Clock clock;
    
    private static JavaLogging log;
    private static Map<String,WeakList<CacheEntry>> cacheMap;
    private static ReentrantLock lock;
    private static Map<Future<Boolean>,CacheEntry> requestMap;
    private static BlockingQueue<Path> deleteQueue = new LinkedBlockingQueue<>();
    private static SSLContext sslCtx;
    private static KeyStoreManager keyStoreManager;

    static
    {
        Security.addProvider(new BouncyCastleProvider());
    }
    private Future<Void> keyStoreLoaderFuture;
    
    public Future<Void> start() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException
    {
        log = new JavaLogging(Cache.class);
        log.config("start executor");
        executor = new ThreadPoolExecutor(Config.getCorePoolSize(), Integer.MAX_VALUE, 1, TimeUnit.MINUTES, new SynchronousQueue());
        log.config("start scheduler");
        scheduler = Executors.newScheduledThreadPool(2);
        clock = Clock.systemUTC();
        cacheMap = new WeakHashMap<>();
        lock = new ReentrantLock();
        requestMap = new ConcurrentHashMap<>();
        log.config("start KeyStoreLoader");
        keyStoreManager  = new KeyStoreManager(Config.getKeyStoreFile());
        sslCtx = SSLContext.getInstance("TLSv1.2");
        sslCtx.init(new KeyManager[]{keyStoreManager}, null, null);
        log.config("started keyStoreManager");
        log.config("start EntryHandler");
        scheduler.scheduleWithFixedDelay(new EntryHandler(), Config.getRestartInterval(), Config.getRestartInterval(), TimeUnit.MILLISECONDS);
        log.config("start Remover");
        executor.submit(new Remover());
        log.config("start Deleter");
        executor.submit(new Deleter());
        log.config("start HttpsSocketServer");
        executor.submit(new HttpsSocketServer());
        log.config("start  HttpsProxyServer");
        executor.submit(new HttpsProxyServer());
        log.config("start HttpSocketServer");
        Future<Void> httpServerFuture = executor.submit(new HttpSocketServer());
        return httpServerFuture;
    }
    public void startAndWait() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, KeyManagementException
    {
        Future<Void> future = start();
        future.get();
    }

    public static void stop()
    {
        log.config("shutdownNow");
        executor.shutdownNow();
    }
    
    static void gc()
    {
        cacheMap.clear();
    }

    public static boolean tryCache(HttpHeaderParser request, ByteChannel userAgent) throws IOException, URISyntaxException
    {
        if (!request.isCacheable())
        {
            return false;
        }
        while (true)
        {
            CacheEntry entry = null;
            CacheEntry stale = null;
            lock.lock();
            try
            {
                String requestTarget = request.getRequestTarget();
                log.finer("tryCache %s from map", requestTarget);
                WeakList<CacheEntry> weakList = cacheMap.get(requestTarget);
                if (weakList == null)
                {
                    weakList = new WeakList<>();
                    cacheMap.put(requestTarget, weakList);
                }
                weakList.lock();
                try
                {
                    // remove entries with no file.
                    weakList.removeIf((CacheEntry e)->{return Files.notExists(e.getPath());});
                    if (weakList.isEmpty() || weakList.isGarbageCollected())
                    {
                        String digest = getDigest(requestTarget);
                        File dir2 = getDirectory2(digest);
                        if (dir2.exists())
                        {
                            final WeakList<CacheEntry> fwl = weakList;
                            Set<Path> paths = weakList.stream()
                                    .map(CacheEntry::getPath)
                                    .collect(Collectors.toSet());
                            // re-create entries which are garbage collected.
                            Files.find(dir2.toPath(), 1, (Path p, BasicFileAttributes u) ->
                                    {
                                        String fn = p.getFileName().toString();
                                        return fn.startsWith(digest) && !fn.endsWith(".atr");
                                    }) 
                                    .filter((p)->{return !paths.contains(p);}).map((p)->{return new CacheEntry(false, p, request);})
                                    .collect(Collectors.toCollection(()->{return fwl;}));
                        }
                    }
                    Map<VaryMap, List<CacheEntry>> groupBy = weakList.stream().collect(Collectors.groupingBy((CacheEntry e)->{return e.getVaryMap();}));
                    CacheEntry emptyVaryMapEntry = null;
                    for (Entry<VaryMap, List<CacheEntry>> e : groupBy.entrySet())
                    {
                        List<CacheEntry> list = e.getValue();
                        VaryMap varyMap = e.getKey();
                        list.sort(null);
                        log.fine("%s", e.getKey());
                        if (log.isLoggable(Level.FINEST))
                        {
                            list.stream().forEach((c)->log.finest("order %s %d %s", c, c.refreshness(), c.getState()));
                        }
                        if (varyMap.isEmpty())  // empty will match all
                        {
                            emptyVaryMapEntry = list.get(0);
                        }
                        else
                        {
                            if (varyMap.isMatch(request))
                            {
                                entry = list.get(0);
                            }
                        }
                        int size = list.size();
                        for (int ii=1;ii<size;ii++)
                        {
                            CacheEntry ce = list.get(ii);
                            log.fine("remove  old %s", ce);
                            weakList.remove(ce);
                            try
                            {
                                Files.deleteIfExists(ce.getPath());
                            }
                            catch (Exception ex)
                            {
                                log.log(Level.SEVERE, ex, "remove: ", ex.getMessage());
                            }
                        }
                    }
                    if (entry == null)
                    {   // if not found but there was empty varyMap entry, use it
                        entry = emptyVaryMapEntry;
                    }
                    if (entry != null)
                    {
                        if (entry.isStale())
                        {
                            log.finer("stale entry %s", entry);
                            stale = entry;
                            entry = weakList.stream().filter((x)->{return x.isRefreshing(request);}).findAny().orElse(null);
                            if (entry != null)
                            {
                                log.fine("found running refresh entry %s", entry);
                            }
                        }
                        else
                        {
                            if (entry.matchRequest(request))
                            {
                                log.info("cache hit %s", entry);
                            }
                            else
                            {
                                entry = null;
                            }
                        }
                    }
                    if (entry == null)
                    {
                        if (entry == null || !entry.matchRequest(request))
                        {
                            log.finer("new entry for %s", requestTarget);
                            entry = new CacheEntry(true, createUniqueFile(requestTarget), request, stale);
                            weakList.add(entry);
                        }
                        else
                        {
                            log.finer("couldn't start another refresh %s", requestTarget);
                        }
                    }
                }
                finally
                {
                    weakList.unlock();
                }
            }
            finally
            {
                lock.unlock();
            }
            State state = null;
            if (stale == null)
            {
                log.info("start new request %s", entry);
                state = entry.readFromCache(request, userAgent);
                log.finer("end new request %s %s", state, entry);
            }
            else
            {
                log.finer("try to refresh %s timeout=%d", entry, Config.getRefreshTimeout());
                state = entry.readFromCache(request, userAgent, Config.getRefreshTimeout());
                log.finer("refresh attempt resulted %s %s", state, entry);
            }
            switch (state)
            {
                case Full:
                case NotCached:
                case Error:
                    return true;
                case NoMatch:
                    continue;
                case Timeout:
                case NotModified:
                    log.info("using stale %s", stale);
                    stale.readFromCache(request, userAgent);
                    return true;
                default:
                    throw new IllegalArgumentException(state+" unexpected");
            }
        }
    }

    public static ExecutorService getExecutor()
    {
        return executor;
    }

    public static ScheduledExecutorService getScheduler()
    {
        return scheduler;
    }

    public static void submit(CacheEntry entry)
    {
        lock.lock();
        try
        {
            Future<Boolean> future = executor.submit(entry);
            requestMap.put(future, entry);
        }
        finally
        {
            lock.unlock();
        }
    }

    public static void queueDelete(Path path)
    {
        try
        {
            deleteQueue.put(path);
        }
        catch (InterruptedException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    public static String getDigest(CharSequence seq)
    {
        try
        {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1", "BC");
            int len = seq.length();
            for (int ii=0;ii<len;ii++)
            {
                sha1.update((byte) seq.charAt(ii));
            }
            byte[] digest = sha1.digest();
            StringBuilder sb  = new StringBuilder();
            for (int ii=0;ii<digest.length;ii++)
            {
                sb.append(Integer.toHexString(digest[ii]&0xff));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    public static File getDirectory2(String digest)
    {
        File dir1 = new File(Config.getCacheDir(), Integer.toHexString(digest.charAt(0)&0xff));
        return new File(dir1, Integer.toHexString(digest.charAt(1)&0xff));
    }

    public static File createUniqueFile(String requestTarget) throws IOException
    {
        String digest = getDigest(requestTarget);
        File dir2 = getDirectory2(digest);
        dir2.mkdirs();
        for (int ii=0;ii<1000;ii++)
        {
            File file = new File(dir2, digest + Integer.toHexString(ii));
            try
            {
                if (file.createNewFile())
                {
                    return file;
                }
            }
            catch (IOException ex)
            {
                log.finest("failed to create %s %s", file, ex.getMessage());
            }
        }
        throw new IllegalArgumentException("too many");
    }

    public static Clock getClock()
    {
        return clock;
    }

    public static void setClock(Clock clock)
    {
        Cache.clock = clock;
    }

    public static JavaLogging log()
    {
        return log;
    }

    private class HttpSocketServer implements Callable<Void>
    {
        @Override
        public Void call() throws Exception
        {
            log.config("started HttpSocketServer on port %d", Config.getHttpCachePort());
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(Config.getHttpCachePort()));
            while (true)
            {
                try
                {
                    SocketChannel socketChannel = serverSocket.accept();
                    log.finer("http accept: %s", socketChannel);
                    ConnectionHandler connection = new ConnectionHandler(Scheme.HTTP, socketChannel);
                    executor.submit(connection);
                }
                catch (ClosedByInterruptException ex)
                {
                    log.log(Level.INFO, ex, ex.getMessage());
                    return null;
                }
                catch (Exception ex)
                {
                    log.log(Level.SEVERE, ex, ex.getMessage());
                }
            }
        }
    }
    private class HttpsProxyServer implements Callable<Void>
    {
        private final ByteBuffer bb;
        private final HttpHeaderParser request;

        public HttpsProxyServer()
        {
            bb = ByteBuffer.allocateDirect(BufferSize);
            request = HttpHeaderParser.getInstance(Scheme.HTTPS, bb);
        }
        
        @Override
        public Void call() throws Exception
        {
            log.config("started HttpsProxyServer on port %d", Config.getHttpsProxyPort());
            SSLSocketFactory sslSocketFactory = sslCtx.getSocketFactory();
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(Config.getHttpsProxyPort()));
            while (true)
            {
                try
                {
                    SocketChannel socketChannel = serverSocket.accept();
                    log.finer("https proxy accept: %s", socketChannel);
                    
                    request.readHeader(socketChannel);
                    request.parseRequest();
                    log.fine("https proxy received from user: %s\n%s", socketChannel, request);
                    keyStoreManager.setServerName(request.getHost());
                    bb.position(request.getHeaderSize());
                    InputStream consumed = null;
                    if (bb.hasRemaining())
                    {
                        log.finest("buffer after header %s", bb);
                        int remaining = bb.remaining();
                        byte[] buff = new byte[remaining];
                        bb.get(buff);
                        consumed = new ByteArrayInputStream(buff);
                    }
                    Socket socket = socketChannel.socket();
                    socket.getOutputStream().write(ConnectResponse);
                    SSLSocket sslsocket = (SSLSocket) sslSocketFactory.createSocket(socket, consumed, true);
                    keyStoreManager.setSNIMatcher(sslsocket);
                    ChannelHelper.SocketByteChannel socketByteChannel = ChannelHelper.newSocketByteChannel(sslsocket);
                    ConnectionHandler connection = new ConnectionHandler(Scheme.HTTPS, socketByteChannel);
                    executor.submit(connection);
                }
                catch (IOException ex)
                {
                    log.log(Level.SEVERE, ex, ex.getMessage());
                }
            }
        }
    }
    private class HttpsSocketServer implements Callable<Void>
    {
        @Override
        public Void call() throws Exception
        {
            log.config("started HttpsSocketServer on port %d", Config.getHttpsCachePort());
            SSLServerSocketFactory factory = sslCtx.getServerSocketFactory();
            SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(Config.getHttpsCachePort());
            keyStoreManager.setSNIMatcher(sslServerSocket);

            while (true)
            {
                try
                {
                    Socket socket = sslServerSocket.accept();
                    ByteChannel channel = ChannelHelper.newSocketByteChannel(socket);
                    log.finer("https accept: %s", channel);
                    ConnectionHandler connection = new ConnectionHandler(Scheme.HTTPS, channel);
                    executor.submit(connection);
                }
                catch (IOException ex)
                {
                    log.log(Level.SEVERE, ex, ex.getMessage());
                }
            }
        }
    }
    private class EntryHandler implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                Iterator<Entry<Future<Boolean>,CacheEntry>> iterator1 = requestMap.entrySet().iterator();
                while (iterator1.hasNext())
                {
                    Entry<Future<Boolean>,CacheEntry> e = iterator1.next();
                    Future<Boolean> f = e.getKey();
                    CacheEntry entry = e.getValue();
                    if (f.isDone())
                    {
                        iterator1.remove();
                        Boolean success = f.get();
                        if (!success)
                        {
                            if (entry.getStartCount() > Config.getMaxRestartCount())
                            {
                                log.info("%s restarted more times than allowed %d", entry, Config.getMaxRestartCount());
                            }
                            else
                            {
                                if (entry.hasClients())
                                {
                                    log.fine("restart %s", entry);
                                    submit(entry);
                                }
                                else
                                {
                                    log.fine("not restarted because no one is waiting %s", entry);
                                }
                            }
                        }
                        else
                        {
                            log.fine("success %s", entry);
                        }
                    }
                    else
                    {
                        if (executor.getActiveCount() > 50 && !entry.hasClients())
                        {
                            f.cancel(true);
                            iterator1.remove();
                            log.fine("cancelled because no one is waiting %s", entry);
                        }
                    }
                    Thread.sleep(Config.getRestartInterval());
                }
            }
            catch (InterruptedException | ExecutionException ex)
            {
                log.log(Level.SEVERE, ex, ex.getMessage());
            }
            log.fine("%s", executor);
        }

    }
    private class Deleter implements Callable<Void>
    {

        @Override
        public Void call() throws Exception
        {
            log.config("started Deleter");
            while (true)
            {
                try
                {
                    Path path = deleteQueue.take();
                    lock.lock();
                    try
                    {
                        boolean success = Files.deleteIfExists(path);
                        log.fine("deleted %s success=%b", path, success);
                        Files.deleteIfExists(ExternalFileAttributes.getAttributePath(path));
                    }
                    finally
                    {
                        lock.unlock();
                    }
                }
                catch (IOException ex)
                {
                    log.log(Level.SEVERE, ex, "Deleter %s", ex.getMessage());
                }
            }
        }
    }
}
