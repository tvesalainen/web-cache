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
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.*;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.vesalainen.nio.channels.ChannelHelper;
import org.vesalainen.nio.channels.vc.VirtualCircuit;
import org.vesalainen.nio.channels.vc.VirtualCircuitFactory;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.Scheme;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
public class ConnectionHandler extends JavaLogging implements Callable<Void>
{
    private Scheme scheme;
    private SocketChannel userAgent;
    private final ByteBuffer bb;
    private final HttpHeaderParser parser;

    public ConnectionHandler(Scheme protocol, SocketChannel channel)
    {
        super(ConnectionHandler.class);
        this.scheme = protocol;
        this.userAgent = channel;
        bb = ByteBuffer.allocateDirect(BufferSize);
        parser = HttpHeaderParser.getInstance(protocol, bb);
    }

    static int num;
    @Override
    public Void call() throws Exception
    {
        Thread currentThread = Thread.currentThread();
        String safeName = currentThread.getName();
        currentThread.setName("CH: "+userAgent.getRemoteAddress());
        try
        {
            finest("start reading header %s", userAgent);
            userAgent.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            bb.clear();
            while (!parser.hasWholeHeader())
            {
                if (!bb.hasRemaining())
                {
                    throw new IOException("ByteBuffer capacity reached "+bb);
                }
                int rc = userAgent.read(bb);
                if (rc == -1)
                {
                    return null;
                }
            }
            bb.flip();
            parser.parseRequest();
            fine("cache received from user: %s\n%s", userAgent, parser);
            if (Cache.tryCache(parser, userAgent))
            {
                userAgent.setOption(StandardSocketOptions.SO_LINGER, 5);
                return null;
            }
            CharSequence csHost = parser.getHeader(Host);
            if (csHost == null)
            {
                return null;
            }
            String host = parser.getHost();
            int port = parser.getPort();
            ByteChannel originServer = open(scheme, host, port);
            if (!Method.CONNECT.equals(parser.getMethod()))
            {
                originServer.write(bb);
            }
            VirtualCircuit vc = VirtualCircuitFactory.create(userAgent, originServer, BufferSize, true);
            vc.start(Cache.getExecutor());
            userAgent = null;
        }
        catch (Exception ex)
        {
            log(Level.SEVERE, ex, "%s", ex.getMessage());
        }
        finally
        {
            if (userAgent != null)
            {
                finest("close %s", userAgent);
                userAgent.close();
            }
            currentThread.setName(safeName);
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

    public static ByteChannel open(Scheme scheme, String host, int port) throws IOException
    {
        InetAddress[] allByName = InetAddress.getAllByName(host);
        if (allByName != null && allByName.length > 0)
        {
            for (InetAddress addr : allByName)
            {
                Cache.log().finest("trying connect to %s:%d", addr, port);
                SocketChannel channel;
                switch (scheme)
                {
                    case HTTP:
                        InetSocketAddress inetSocketAddress = new InetSocketAddress(addr, port);
                        channel = SocketChannel.open(inetSocketAddress);
                        Cache.log().finest("connected to http %s", channel);
                        return channel;
                    case HTTPS:
                        SocketFactory sf = SSLSocketFactory.getDefault();
                        Socket socket = sf.createSocket(addr, port);
                        return ChannelHelper.newByteChannel(socket);
                    default:
                        throw new UnsupportedOperationException(scheme+ "unsupported");
                }
            }
        }
        else
        {
            Cache.log().finest("no address for %s", host);
        }
        return null;
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
