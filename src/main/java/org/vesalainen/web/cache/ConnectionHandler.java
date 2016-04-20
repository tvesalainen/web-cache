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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.*;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.vesalainen.util.logging.JavaLogging;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
public class ConnectionHandler extends JavaLogging implements Callable<Void>
{
    private SocketChannel userAgent;
    private final ByteBuffer bb = ByteBuffer.allocateDirect(BufferSize);
    private final HttpHeaderParser parser = HttpHeaderParser.getInstance(bb);

    public ConnectionHandler(SocketChannel channel)
    {
        super(ConnectionHandler.class);
        this.userAgent = channel;
    }

    static int num;
    @Override
    public Void call() throws Exception
    {
        try
        {
            bb.clear();
            while (!parser.hasWholeHeader())
            {
                int rc = userAgent.read(bb);
                if (rc == -1)
                {
                    return null;
                }
            }
            bb.flip();
            parser.parseRequest();
            fine("cache received: %s", parser);
            if (Cache.tryCache(parser, userAgent))
            {
                return null;
            }
            CharSequence csHost = parser.getHeader(Host);
            if (csHost == null)
            {
                return null;
            }
            String host = csHost.toString();
            int port = 80;
            if (Method.CONNECT.equals(parser.getMethod()))
            {
                String u = parser.getRequestTarget().toString();
                String[] uu = u.split(":");
                if (uu.length > 1)
                {
                    port = Integer.parseInt(uu[1]);
                }
            }
            String[] h = host.split(":");
            try (SocketChannel originServer = open(h[0], port))
            {
                virtualCircuit(originServer);
            }
            catch (IOException ex)
            {
                fine("VC ended %s %s", userAgent, ex.getMessage());
            }
        }
        catch (Exception ex)
        {
            log(Level.SEVERE, ex, "%s", ex.getMessage());
        }
        finally
        {
            finest("close %s", userAgent);
            userAgent.close();
        }
        return null;
    }

    private void virtualCircuit(SocketChannel originServer) throws IOException
    {
        int up = 0;
        int down = 0;
        boolean upload = true;
        fine("start: %s", originServer);
        if (!Method.CONNECT.equals(parser.getMethod()))
        {
            originServer.write(bb);
        }
        SelectorProvider provider = SelectorProvider.provider();
        Selector selector = provider.openSelector();
        userAgent.configureBlocking(false);
        userAgent.register(selector, OP_READ, originServer);
        originServer.configureBlocking(false);
        originServer.register(selector, OP_READ, userAgent);
        try
        {
            while (true)
            {
                int count = selector.select();
                if (count > 0)
                {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext())
                    {
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove();
                        SocketChannel source = (SocketChannel) selectionKey.channel();
                        SocketChannel target = (SocketChannel) selectionKey.attachment();
                        boolean upld = source == userAgent;
                        bb.clear();
                        int rc = source.read(bb);
                        int cnt = 0;
                        while (rc > 0)
                        {
                            cnt += rc;
                            bb.flip();
                            while (bb.hasRemaining())
                            {
                                target.write(bb);
                            }
                            bb.clear();
                            rc = source.read(bb);
                        }
                        if (rc == -1)
                        {
                            fine("%s <-- %d %s", target.getRemoteAddress(), cnt, source.getRemoteAddress());
                            return;
                        }
                        if (upld)
                        {
                            up += cnt;
                            fine("%s --> %d %s", target.getRemoteAddress(), cnt, source.getRemoteAddress());
                        }
                        else
                        {
                            down += cnt;
                            fine("%s <-- %d %s", target.getRemoteAddress(), cnt, source.getRemoteAddress());
                        }
                    }
                }
            }
        }
        finally
        {
            fine("end: up=%d down=%d %s", up, down, originServer);
        }
    }

    public boolean isActive()
    {
        return !userAgent.socket().isInputShutdown();
    }

    public static SocketChannel open(String host, int port) throws IOException
    {
        try
        {
            while (true)
            {
                List<Connector> connectors = new ArrayList<>();
                Set<InetAddress> set = Cache.resolver.resolv(host, 10, 1, TimeUnit.SECONDS);
                if (set != null)
                {
                    for (InetAddress addr : set)
                    {
                        connectors.add(new Connector(new InetSocketAddress(addr, port)));
                    }
                }
                if (!connectors.isEmpty())
                {
                    Cache.log().finest("trying connect to %s", host);
                    SocketChannel channel = Cache.getExecutor().invokeAny(connectors);
                    if (channel != null)
                    {
                        connectors.stream().forEach((connector) -> connector.closeExtra(channel));
                        Cache.log().finest("connected %s", channel);
                        return channel;
                    }
                }
                else
                {
                    Cache.log().finest("no address for %s", host);
                }
                Thread.sleep(1000);
            }
        }
        catch (InterruptedException | ExecutionException ex)
        {
            Cache.log().finest("open failed %s: %s", ex.getClass().getSimpleName(), ex.getMessage());
            throw new IOException(ex);
        }
    }
    @Override
    public String toString()
    {
        return "Connection{" + userAgent + '}';
    }
    
    private static class Connector implements Callable<SocketChannel>
    {
        private InetSocketAddress remote;
        private SocketChannel channel;

        public Connector(InetSocketAddress isa)
        {
            this.remote = isa;
        }

        @Override
        public SocketChannel call() throws Exception
        {
            Cache.log().finest("trying to open %s", remote);
            channel = SocketChannel.open(remote);
            return channel;
        }
        
        public void closeExtra(SocketChannel channel)
        {
            if (this.channel != null && !this.channel.equals(channel))
            {
                try
                {
                    Cache.log().finest("close extra %s", this.channel);
                    this.channel.close();
                }
                catch (IOException ex)
                {
                }
            }
        }
    }
}
