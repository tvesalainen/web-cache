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
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.util.CharSequences;
import org.vesalainen.util.logging.JavaLogging;
import static org.vesalainen.web.cache.CacheConstants.OP;

/**
 *
 * @author tkv
 */
public class HttpServer extends JavaLogging implements Runnable
{
    public static final int PacketSize = 32;
    private int port;
    private final Map<String, String> contentMap = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private Thread thread;
    private final ByteBuffer bb = ByteBuffer.allocateDirect(4096);
    private final HttpHeaderParser parser = HttpHeaderParser.getInstance(bb);
    private URI uri;
    private StringBuilder sb = new StringBuilder();
    
    private AtomicInteger requestCount = new AtomicInteger();
    private Supplier<Clock> clockFactory;
    private boolean failSend;
    private String eTag;
    private ZonedDateTime lastModified;
    private long millisBetweenPackets;
    private long offset;
    private boolean noContentLength;

    public HttpServer(int port, Supplier<Clock> clockFactory)
    {
        super(HttpServer.class);
        this.port = port;
        this.clockFactory = clockFactory;
    }

    public void clear()
    {
        info("server clear");
        eTag = null;
        lastModified = null;
        failSend = false;
        millisBetweenPackets = 0;
        headers.clear();
        contentMap.clear();
        requestCount.set(0);
        offset = 0;
        noContentLength = false;
    }

    public void start() throws URISyntaxException
    {
        thread = new Thread(this, HttpServer.class.getSimpleName());
        thread.start();
    }
    public void stop()
    {
        thread.interrupt();
    }
    @Override
    public void run()
    {
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open())
        {
            serverChannel.bind(new InetSocketAddress(port));
            while (true)
            {
                SocketChannel channel = serverChannel.accept();
                bb.clear();
                int rc = channel.read(bb);
                if (rc > 0)
                {
                    bb.flip();
                    parser.parseRequest();
                    uri = new URI(parser.getRequestTarget().toString());
                    info("server received \n%s", parser);
                    Request request = new Request(parser, uri);
                    sb.setLength(0);
                    Response response = new Response(sb);
                    service(request, response);
                    int len = sb.length();
                    bb.clear();
                    int packetCounter = 0;
                    for (int ii=0;ii<len;ii++)
                    {
                        bb.put((byte)sb.charAt(ii));
                        packetCounter++;
                        if (packetCounter == PacketSize)
                        {
                            bb.flip();
                            channel.write(bb);
                            Thread.sleep(millisBetweenPackets);
                            packetCounter = 0;
                            bb.clear();
                        }
                    }
                    bb.flip();
                    channel.write(bb);
                    info("server sent:\n%s", sb);
                    channel.close();
                }
            }
        }
        catch (ClosedByInterruptException | InterruptedException ex)
        {
        }
        catch (IOException | URISyntaxException ex)
        {
            log(Level.SEVERE, ex, "%s", ex.getMessage());
        }
    }

    protected void service(Request request, Response response) throws IOException
    {
        requestCount.incrementAndGet();
        ZonedDateTime now = ZonedDateTime.now(clockFactory.get());
        response.setHeader("Date", now.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        response.setHeader("Server", HttpServer.class.getName());
        response.setHeader("Accept-Ranges", "bytes");
        for (Entry<String,String> e : headers.entrySet())
        {
            response.setHeader(e.getKey(), e.getValue());
        }
        String range = request.getHeader("Range");
        if (range != null)
        {
            String[] s = range.substring(6).split("-");
            int start = Integer.parseInt(s[0]);
            if (eTagNotModified(request, "If-Range") || dateNotModified(request, "If-Range"))
            {
                response.setStatus(206);
                sendContent(request, response, start);
            }
            else
            {
                sendContent(request, response, 0);
            }
        }
        else
        {
            if (eTagPreconditionFail(request, "If-Match"))
            {
                response.sendError(412);
                return;
            }
            if (datePreconditionFail(request, "If-Unmodified-Since"))
            {
                response.sendError(412);
                return;
            }
            if (eTagNotModified(request, "If-None-Match"))
            {
                response.sendError(304);
                return;
            }
            if (dateNotModified(request, "If-Modified-Since"))
            {
                response.sendError(304);
                return;
            }
            sendContent(request, response, 0);
        }
    }

    public int getRequestCount()
    {
        return requestCount.get();
    }

    public void setETag(String eTag)
    {
        eTag = "\""+eTag+"\"";
        this.eTag = eTag;
        addHeader("ETag", eTag);
        info("set eTag %s", eTag);
    }

    public void setLastModified(Clock clock)
    {
        setLastModified(ZonedDateTime.now(clock));
    }
    public void setLastModified(ZonedDateTime lastModified)
    {
        this.lastModified = lastModified;
        addHeader("Last-Modified", lastModified.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        info("set LastModified %s", lastModified);
    }
    
    public void setContent(String path, String content)
    {
        if (!path.startsWith("/"))
        {
            path = "/"+path;
        }
        contentMap.put(path, content);
    }
    
    public void addHeader(String name, String value)
    {
        headers.put(name, value);
    }

    private boolean eTagPreconditionFail(Request request, String name)
    {
        if (eTag != null)
        {
            String ifMatch = request.getHeader(name);
            if (ifMatch != null)
            {
                return !eTag.equals(ifMatch);
            }
        }
        return false;
    }

    private boolean eTagNotModified(Request request, String name)
    {
        if (eTag != null)
        {
            String ifNoneMatch = request.getHeader(name);
            return eTag.equals(ifNoneMatch);
        }
        return false;
    }

    private boolean datePreconditionFail(Request request, String name)
    {
        if (lastModified != null)
        {
            String ims = request.getHeader(name);
            if (ims != null)
            {
                ZonedDateTime ifModifiedSince = ZonedDateTime.parse(ims, DateTimeFormatter.RFC_1123_DATE_TIME);
                return lastModified.isAfter(ifModifiedSince);
            }
        }
        return false;
    }

    private boolean dateNotModified(Request request, String name)
    {
        if (lastModified != null)
        {
            String ims = request.getHeader(name);
            if (ims != null)
            {
                ZonedDateTime ifModifiedSince = ZonedDateTime.parse(ims, DateTimeFormatter.RFC_1123_DATE_TIME);
                return !lastModified.isAfter(ifModifiedSince);
            }
        }
        return false;
    }

    private void sendContent(Request request, Response response, int start) throws IOException
    {
        String pathInfo = request.getPathInfo();
        String lang = request.getHeader("Accept-Language");
        if (lang != null)
        {
            pathInfo = pathInfo+lang;
        }
        String content = contentMap.get(pathInfo);
        if (content != null)
        {
            int length = content.length();
            int last = length - 1;
            if (start == 0)
            {
                if (!noContentLength)
                {
                    response.setIntHeader("Content-Length", length);
                }
                if (failSend)
                {
                    response.write(content.substring(0, length/2));
                }
                else
                {
                    response.write(content);
                }
            }
            else
            {
                response.setHeader("Content-Range", "bytes "+start+"-"+last+"/"+length);
                response.setIntHeader("Content-Length", length - start);
                response.write(content.substring(start));
            }
        }
        else
        {
            response.sendError(404);
        }
    }

    public void seteTag(String eTag)
    {
        this.eTag = eTag;
    }

    public void setFailSend(boolean failSend)
    {
        this.failSend = failSend;
    }

    public void setMillisBetweenPackets(long millisBetweenPackets)
    {
        this.millisBetweenPackets = millisBetweenPackets;
    }

    public void setOffset(long offset)
    {
        this.offset = offset;
    }

    void setNoContentLength()
    {
        this.noContentLength = true;
    }

    private static class Request
    {
        private final HttpHeaderParser parser;
        private URI uri;

        public Request(HttpHeaderParser parser, URI uri)
        {
            this.parser = parser;
            this.uri = uri;
        }
        
        private String getHeader(String name)
        {
            ByteBufferCharSequence header = parser.getHeader(CharSequences.getConstant(name, OP));
            if (header != null)
            {
                return header.toString();
            }
            return null;
        }

        private String getPathInfo()
        {
            return uri.getPath();
        }
    }

    private static class Response
    {
        private StringBuilder sb;
        private final List<String> headers = new ArrayList<>();
        private int sc = 200;
        private boolean flushed;

        public Response(StringBuilder sb)
        {
            this.sb = sb;
        }

        private void setStatus(int sc)
        {
            this.sc = sc;
        }

        private void sendError(int sc)
        {
            this.sc = sc;
            sendResponse();
        }

        private void setIntHeader(String name, int value)
        {
            setHeader(name, String.valueOf(value));
        }

        private void setHeader(String name, String value)
        {
            headers.add(name+": "+value);
        }

        private void write(String str)
        {
            sendResponse();
            sb.append(str);
        }

        private void sendResponse()
        {
            sb.append("HTTP/1.1 ");
            sb.append(sc);
            sb.append("\r\n");
            for (String hdr : headers)
            {
                sb.append(hdr);
                sb.append("\r\n");
            }
            sb.append("\r\n");
        }
    }
}
