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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.GatheringByteChannel;
import java.time.Clock;
import static java.time.Month.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;
import org.vesalainen.nio.channels.ChannelHelper;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
public class HttpRequestParserTest
{
    private ByteBuffer bb;
    private HttpHeaderParser parser;
    
    public HttpRequestParserTest()
    {
        bb = ByteBuffer.allocate(4096);
        parser = HttpHeaderParser.getInstance(bb);
        Cache.setClock(Clock.systemUTC());
    }

    @Test
    public void test1()
    {
        try 
        {
            URL url = HttpRequestParserTest.class.getResource("/header0");
            File file = new File(url.toURI());
            try (FileInputStream fis = new FileInputStream(file))
            {
                byte[] buf = new byte[(int)file.length()];
                fis.read(buf);
                bb.clear();
                bb.put(buf);
                bb.flip();
                parser.parseRequest();
                assertEquals(Method.CONNECT, parser.getMethod());
                assertTrue("1.1".contentEquals(parser.getVersion()));
                assertTrue("notify.dropbox.com:443".contentEquals(parser.getRequestTarget()));
                assertTrue("notify.dropbox.com".contentEquals(parser.getHeader(Host)));
                assertTrue("keep-alive".contentEquals(parser.getHeader(ProxyConnection)));
                assertEquals(file.length(), parser.getHeaderSize());
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        catch (URISyntaxException ex)
        {
            Logger.getLogger(HttpRequestParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Test
    public void test2()
    {
        try 
        {
            URL url = HttpRequestParserTest.class.getResource("/header1");
            File file = new File(url.toURI());
            try (FileInputStream fis = new FileInputStream(file))
            {
                byte[] buf = new byte[(int)file.length()];
                fis.read(buf);
                bb.clear();
                bb.put(buf);
                bb.flip();
                parser.parseRequest();
                assertEquals(Method.GET, parser.getMethod());
                assertTrue("1.1".contentEquals(parser.getVersion()));
                assertTrue("http://yle.fi/uutiset/".contentEquals(parser.getRequestTarget()));
                assertTrue("yle.fi".contentEquals(parser.getHeader(Host)));
                assertTrue("keep-alive".contentEquals(parser.getHeader(ProxyConnection)));
                assertEquals(file.length(), parser.getHeaderSize());
                GatheringByteChannel channel = ChannelHelper.newGatheringByteChannel(Channels.newChannel(System.err));
                parser.addHeader("Foo", "Bar");
                parser.removeHeader(ProxyConnection);
                parser.write(channel);
                parser.write(channel);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        catch (URISyntaxException ex)
        {
            Logger.getLogger(HttpRequestParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Test
    public void test3()
    {
        try 
        {
            URL url = HttpRequestParserTest.class.getResource("/response0");
            File file = new File(url.toURI());
            try (FileInputStream fis = new FileInputStream(file))
            {
                byte[] buf = new byte[(int)file.length()];
                fis.read(buf);
                bb.clear();
                bb.put(buf);
                bb.flip();
                parser.parseResponse();
                assertTrue("1.1".contentEquals(parser.getVersion()));
                assertEquals(200, parser.getStatusCode());
                assertTrue("OK".contentEquals(parser.getReasonPhrase()));
                assertEquals(205, parser.getHeaderSize());
                // Date: Sat, 02 Apr 2016 11:36:36 GMT
                ZonedDateTime date = parser.getDateHeader(Date);
                ZonedDateTime now = ZonedDateTime.now(Cache.getClock());
                assertEquals(now.getYear(), date.getYear());
                assertEquals(now.getMonth(), date.getMonth());
                assertEquals(now.getDayOfMonth(), date.getDayOfMonth());
                assertEquals(now.getHour(), date.getHour());
                assertEquals(now.getMinute(), date.getMinute());
                assertEquals(now.getSecond(), date.getSecond());
                assertEquals(ZoneId.of("Z"), date.getZone());
                ZonedDateTime expires = parser.getDateHeader(Expires);
                assertEquals(1970, expires.getYear());
                assertEquals(JANUARY, expires.getMonth());
                assertEquals(1, expires.getDayOfMonth());
                assertEquals(0, expires.getHour());
                assertEquals(0, expires.getMinute());
                assertEquals(0, expires.getSecond());
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        catch (URISyntaxException ex)
        {
            Logger.getLogger(HttpRequestParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Test
    public void test4()
    {
        try 
        {
            URL url = HttpRequestParserTest.class.getResource("/response1");
            File file = new File(url.toURI());
            try (FileInputStream fis = new FileInputStream(file))
            {
                byte[] buf = new byte[(int)file.length()];
                fis.read(buf);
                bb.clear();
                bb.put(buf);
                bb.flip();
                parser.parseResponse();
                assertTrue("1.0".contentEquals(parser.getVersion()));
                assertEquals(301, parser.getStatusCode());
            }
            catch (IOException ex)
            {
                Logger.getLogger(HttpRequestParserTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        catch (URISyntaxException ex)
        {
            Logger.getLogger(HttpRequestParserTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
