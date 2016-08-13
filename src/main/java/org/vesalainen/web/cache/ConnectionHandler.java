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
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.net.ssl.SSLException;
import org.vesalainen.net.ssl.HelloForwardException;
import org.vesalainen.net.ssl.SSLSocketChannel;
import org.vesalainen.nio.channels.ChannelHelper;
import org.vesalainen.nio.channels.vc.VirtualCircuit;
import org.vesalainen.nio.channels.vc.VirtualCircuitFactory;
import org.vesalainen.util.HexDump;
import org.vesalainen.util.concurrent.TaggableThread;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.Scheme;
import static org.vesalainen.web.cache.CacheConstants.*;
import org.vesalainen.web.https.KeyStoreManager;

/**
 *
 * @author tkv
 */
public class ConnectionHandler extends JavaLogging implements Callable<Void>
{
    private Scheme scheme;
    private ByteChannel userAgent;
    private final ByteBuffer bb;
    private final HttpHeaderParser parser;

    public ConnectionHandler(Scheme scheme, ByteChannel channel)
    {
        super(ConnectionHandler.class);
        this.scheme = scheme;
        this.userAgent = channel;
        bb = ByteBuffer.allocateDirect(BufferSize);
        parser = HttpHeaderParser.getInstance(scheme, bb);
    }

    static int num;
    @Override
    public Void call() throws Exception
    {
        try
        {
            TaggableThread.tag("Scheme", scheme);
            TaggableThread.tag("Connection Type", "unknown");
            finest("start reading header %s", userAgent);
            setOption(userAgent, StandardSocketOptions.SO_KEEPALIVE, true);
            try
            {
                parser.readHeader(userAgent);
            }
            catch (HelloForwardException hfe)
            {
                TaggableThread.tag("Connection Type", "HTTPS->HTTP VC");
                fine("%s", hfe);
                ByteChannel originServer = open(Scheme.HTTP, hfe.getHost(), 443);
                originServer.write(hfe.getClientHello());
                VirtualCircuit vc = VirtualCircuitFactory.create(hfe.getChannel(), originServer, BufferSize, true);
                fine("start HTTPS->HTTP VC for %s / %s", hfe.getChannel(), originServer);
                vc.join(Cache::getExecutor);
                userAgent = null;
                return null;
            }
            parser.parseRequest();
            fine("cache received from user: %s\n%s", userAgent, parser);
            if (Cache.tryCache(parser, userAgent))
            {
                TaggableThread.tag("Connection Type", "Cache");
                setOption(userAgent, StandardSocketOptions.SO_LINGER, 5);
                return null;
            }
            CharSequence csHost = parser.getHeader(Host);
            if (csHost == null)
            {
                warning("no Host: @%s", parser);
                return null;
            }
            String host = parser.getHost();
            int port = parser.getPort();
            ByteChannel originServer = open(scheme, host, port);
            if (Method.CONNECT.equals(parser.getMethod()))
            {
                TaggableThread.tag("Connection Type", "Connect VC");
                fine("send %s to %s", bb, originServer);
                bb.position(parser.getHeaderSize());
                debug(()->HexDump.toHex(bb));
                ChannelHelper.writeAll(originServer, bb);
                fine("send connect response to %s", userAgent);
                bb.clear();
                bb.put(ConnectResponse);
                bb.flip();
                ChannelHelper.writeAll(userAgent, bb);
            }
            else
            {
                TaggableThread.tag("Connection Type", "VC");
                fine("send %s to %s", bb, originServer);
                debug(()->HexDump.toHex(bb));
                ChannelHelper.writeAll(originServer, bb);
            }
            VirtualCircuit vc = VirtualCircuitFactory.create(userAgent, originServer, BufferSize, true);
            fine("start VC for %s / %s", userAgent, originServer);
            vc.join(Cache::getExecutor);
            userAgent = null;
        }
        catch (SSLException ex)
        {
            String serverName = KeyStoreManager.getServerName();
            log(Level.SEVERE, ex, "%s: %s", serverName, ex.getMessage());
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
        }
        return null;
    }

    public static ByteChannel open(Scheme scheme, String host, int port) throws IOException
    {
        InetAddress[] allByName = InetAddress.getAllByName(host);
        if (allByName != null && allByName.length > 0)
        {
            for (InetAddress addr : allByName)
            {
                Cache.log().finest("trying %s connect to %s:%d", scheme, addr, port);
                SocketChannel channel;
                InetSocketAddress inetSocketAddress = new InetSocketAddress(addr, port);
                switch (scheme)
                {
                    case HTTP:
                        channel = SocketChannel.open(inetSocketAddress);
                        Cache.log().finest("connected to http %s", channel);
                        return channel;
                    case HTTPS:
                        SSLSocketChannel sslSocketChannel = SSLSocketChannel.open(host, port);
                        Cache.log().finest("connected to https %s", sslSocketChannel);
                        return sslSocketChannel;
                    default:
                        throw new UnsupportedOperationException(scheme+" unsupported");
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

    private <T> void setOption(ByteChannel channel, SocketOption<T> name, T value) throws IOException
    {
        if (channel instanceof SocketChannel)
        {
            SocketChannel socketChannel = (SocketChannel) channel;
            socketChannel.setOption(name, value);
        }
        else
        {
            if (channel instanceof SSLSocketChannel)
            {
                SSLSocketChannel sslSocketChannel = (SSLSocketChannel) channel;
                sslSocketChannel.setOption(name, value);
            }
            else
            {
                throw new UnsupportedOperationException(channel+" not supported");
            }
        }
    }

    private void readHeader(ByteChannel userAgent)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
