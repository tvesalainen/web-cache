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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.util.WeakList;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.Scheme;
import org.vesalainen.web.cache.CacheEntry.State;

/**
 *
 * @author tkv
 */
public class Cache
{
    private static ExecutorService executor;
    private static Clock clock;
    
    private static JavaLogging log;
    private static ServerSocketChannel serverSocket;
    private static File cacheDir;
    private static int port;
    private static int timeout = 500;
    private static Map<URI,WeakList<CacheEntry>> cacheMap;
    private static ReentrantLock lock;
    private static Map<Future<Boolean>,CacheEntry> requestMap;

    public Future<Void> start(File cacheDir, int port) throws IOException, InterruptedException
    {
        log = new JavaLogging(Cache.class);
        executor = Executors.newCachedThreadPool();
        clock = Clock.systemUTC();
        cacheMap = new WeakHashMap<>();
        lock = new ReentrantLock();
        requestMap = new ConcurrentHashMap<>();
        Cache.cacheDir = cacheDir;
        Cache.port = port;
        executor.submit(new FutureHandler());
        Future<Void> future = executor.submit(new SocketServer());
        return future;
    }
    public void startAndWait(File cacheDir, int port) throws IOException, InterruptedException, ExecutionException
    {
        Future<Void> future = start(cacheDir, port);
        future.get();
    }

    public static void stop()
    {
        executor.shutdownNow();
    }
    
    static void gc()
    {
        cacheMap.clear();
    }
    
    public static boolean tryCache(HttpHeaderParser request, SocketChannel userAgent) throws IOException, URISyntaxException
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
                ByteBufferCharSequence requestTarget = request.getRequestTarget();
                URI uri = new URI(requestTarget.toString());
                log.finer("tryCache %s from map", uri);
                WeakList<CacheEntry> weakList = cacheMap.get(uri);
                if (weakList == null)
                {
                    weakList = new WeakList<>();
                    cacheMap.put(uri, weakList);
                }
                weakList.lock();
                try
                {
                    if (weakList.isEmpty() || weakList.isGarbageCollected())
                    {
                        String digest = getDigest(requestTarget);
                        File dir2 = getDirectory2(digest);
                        if (dir2.exists())
                        {
                            final WeakList<CacheEntry> fwl = weakList;
                            Set<Path> paths = weakList.stream().map(CacheEntry::getPath).collect(Collectors.toSet());
                            Files.find(dir2.toPath(), 1, (Path p, BasicFileAttributes u) -> p.getFileName().toString().startsWith(digest))
                                    //.parallel()
                                    .filter((p)->{return !paths.contains(p);}).map((p)->{return new CacheEntry(p, request);})
                                    .collect(Collectors.toCollection(()->{return fwl;}));
                        }
                    }
                    entry = weakList.stream().filter((x)->{return x.matchRequest(request);}).sorted().findFirst().orElse(null);
                    if (log.isLoggable(Level.FINEST))
                    {
                        weakList.stream().filter((x)->{return x.matchRequest(request);}).sorted().forEach((c)->log.finest("order %s", c));
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
                    }
                    if (entry == null)
                    {
                        if (entry == null || !entry.matchRequest(request))
                        {
                            log.finer("new entry for %s", uri);
                            entry = new CacheEntry(createUniqueFile(requestTarget), request, stale);
                            weakList.add(entry);
                        }
                        else
                        {
                            log.finer("couldn't start another refresh %s", uri);
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
                log.finer("try to get %s", entry);
                state = entry.readFromCache(request, userAgent);
                log.finer("got %s %s", state, entry);
            }
            else
            {
                log.finer("try to get fresh timeout=%d %s", timeout, entry);
                state = entry.readFromCache(request, userAgent, timeout);
                log.finer("got for fresh %s %s", state, entry);
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
                    log.finer("using stale %s", stale);
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

    public static String getDigest(CharSequence seq)
    {
        try
        {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
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
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    public static File getDirectory2(String digest)
    {
        File dir1 = new File(cacheDir, Integer.toHexString(digest.charAt(0)&0xff));
        return new File(dir1, Integer.toHexString(digest.charAt(1)&0xff));
    }

    public static File createUniqueFile(ByteBufferCharSequence requestTarget) throws IOException
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

    public static void setTimeout(int timeout)
    {
        Cache.timeout = timeout;
    }

    public static JavaLogging log()
    {
        return log;
    }
    
    private class SocketServer implements Callable<Void>
    {
        @Override
        public Void call() throws Exception
        {
            try
            {
                serverSocket = ServerSocketChannel.open();
                serverSocket.bind(new InetSocketAddress(port));
                while (true)
                {
                    SocketChannel socketChannel = serverSocket.accept();
                    log.finer("accept: %s", socketChannel);
                    ConnectionHandler connection = new ConnectionHandler(Scheme.HTTP, socketChannel);
                    Future<Void> future = executor.submit(connection);
                }
            }
            catch (IOException ex)
            {
                log.log(Level.SEVERE, ex, ex.getMessage());
            }
            return null;
        }
    }
    private class FutureHandler implements Callable<Void>
    {
        @Override
        public Void call() throws Exception
        {
            while (true)
            {
                try
                {
                    Iterator<Entry<Future<Boolean>,CacheEntry>> iterator1 = requestMap.entrySet().iterator();
                    while (iterator1.hasNext())
                    {
                        Entry<Future<Boolean>,CacheEntry> e = iterator1.next();
                        Future<Boolean> f = e.getKey();
                        if (f.isDone())
                        {
                            iterator1.remove();
                            CacheEntry entry = e.getValue();
                            Boolean success = f.get();
                            if (!success)
                            {
                                log.fine("restart %s", entry);
                                submit(entry);
                            }
                            else
                            {
                                log.fine("success %s", entry);
                            }
                        }
                    }
                    Thread.sleep(100);
                }
                catch (InterruptedException ex)
                {
                }
                catch (ExecutionException ex)
                {
                    log.log(Level.SEVERE, ex, ex.getMessage());
                }
            }
        }

    }
}
