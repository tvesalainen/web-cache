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
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.vesalainen.util.CharSequences;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class HttpClient extends JavaLogging implements Callable<Integer>
{
    private final ByteBuffer bb = ByteBuffer.allocate(8192);
    private final HttpHeaderParser parser = HttpHeaderParser.getInstance(bb);
    private final InetSocketAddress proxy;
    private Method method;
    private final URI uri;
    private List<String> headers = new ArrayList<>();
    private SocketChannel channel;
    private int count;
    private long timeout;

    public HttpClient(String host, int port, long timeout, Method method, URI uri, String... headers)
    {
        this(new InetSocketAddress(host, port), timeout, method, uri);
    }

    public HttpClient(InetSocketAddress proxy, long timeout, Method method, URI uri, String... headers)
    {
        super(HttpClient.class);
        this.proxy = proxy;
        this.timeout = timeout;
        this.method = method;
        this.uri = uri;
        for (String hdr : headers)
        {
            addHeader(hdr);
        }
    }

    @Override
    public Integer call() throws Exception
    {
        write();
        return read();
    }
    
    public int retrieve() throws IOException
    {
        Future<Integer> future = Cache.getExecutor().submit(this);
        try
        {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException ex)
        {
            severe("TEST CONNECTION %s: %s", ex.getClass().getSimpleName(), ex.getMessage());
            return -1;
        }
    }
    private void write() throws IOException
    {
        info("TEST WRITE %s %d", uri, ++count);
        channel = SocketChannel.open(proxy);
        bb.clear();
        StringBuilder sb = new StringBuilder();
        sb.append(method.name());
        sb.append(' ');
        sb.append(uri);
        sb.append(" HTTP/1.1\r\n");
        sb.append("Host: ");
        sb.append(uri.getHost());
        sb.append("\r\n");
        for (String hdr : headers)
        {
            sb.append(hdr);
            sb.append("\r\n");
        }
        sb.append("\r\n");
        int len = sb.length();
        for (int ii=0;ii<len;ii++)
        {
            bb.put((byte)sb.charAt(ii));
        }
        bb.flip();
        while (bb.hasRemaining())
        {
            channel.write(bb);
        }
    }
    private int read() throws IOException
    {
        info("TEST START READ %s %d", uri, count);
        bb.clear();
        while (!parser.hasWholeHeader())
        {
            int rc = channel.read(bb);
            if (rc == -1)
            {
                throw new IllegalArgumentException("cache broke connection "+bb);
            }
        }
        bb.flip();
        parser.parseResponse();
        long contentSize = parser.getContentSize();
        int headerSize = parser.getHeaderSize();
        long size = contentSize + headerSize;
        bb.position(bb.limit());
        bb.limit(bb.capacity());
        while (bb.position() < size)
        {
            int rc = channel.read(bb);
            if (rc == -1)
            {
                break;
            }
        }
        bb.flip();
        info("TEST END READ %s %d", uri, count);
        return parser.getStatusCode();
    }

    public ZonedDateTime getDateHeader(String name)
    {
        String header = getHeader(name);
        if (header != null)
        {
            return ZonedDateTime.parse(name, DateTimeFormatter.RFC_1123_DATE_TIME);
        }
        return null;
    }
    public String getHeader(String name)
    {
        return parser.getHeader(CharSequences.getConstant(name)).toString();
    }
    
    public String getContent()
    {
        int headerSize = parser.getHeaderSize();
        bb.position(headerSize);
        StringBuilder sb = new StringBuilder();
        while (bb.hasRemaining())
        {
            sb.append((char)bb.get());
        }
        return sb.toString();
    }
    public void addHeader(String name, String value)
    {
        addHeader(name+": "+value);
    }
    public void addHeader(String header)
    {
        headers.add(header.trim());
    }

    public void setMethod(Method method)
    {
        this.method = method;
    }
    
}
