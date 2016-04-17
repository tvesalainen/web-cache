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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class CacheTest extends JavaLogging
{
    private static final String proxyHost = "localhost";
    private static final int proxyPort = 1234;
    private static final int httpPort = 80;
    private static final File dir = new File("c:\\temp\\cache");
    private static HttpServer server;
    private Clock now = Clock.systemUTC();

    public CacheTest()
    {
        super(CacheTest.class);
    }
    @BeforeClass
    public static void init() throws Exception
    {
        if (dir.exists())
        {
            Path path = dir.toPath();
            Stream<Path> stream = Files.walk(path, 3);
            stream.forEach((Path p)->p.toFile().delete());
        }
        Cache cache = new Cache();
        cache.start(dir, proxyPort);
        Cache.setRefreshTimeout(500000);
        server = new HttpServer(httpPort, Cache::getClock);
        server.start();
    }
    @AfterClass
    public static void cleanup()
    {
        server.stop();
        Cache.stop();
    }
    @Before
    public void clear() throws IOException
    {
        info("TEST CLEAR");
        Cache.gc();
        server.clear();
        Cache.setClock(now);
    }
    private void moveClock(long amount, ChronoUnit unit)
    {
        now = Clock.offset(now, Duration.of(amount, unit));
        Cache.setClock(now);
        info("MOVE CLOCK TO %s", now);
    }
    private ZonedDateTime fromClock(long amount, ChronoUnit unit)
    {
        return ZonedDateTime.now(now).plus(amount, unit);
    }
    private static final String Content = "ABCDEFGHIJKLMNOPQRSTUWVXYZ1234567890abcdefghijklmnopqrstuwvxyz";
    private String createContent(int size)
    {
        StringBuilder sb = new StringBuilder();
        int mod = Content.length();
        for (int ii=0;ii<size;ii++)
        {
            sb.append(Content.charAt(ii%mod));
        }
        return sb.toString();
    }
    
    private HttpClient createClient(String path)
    {
        return createClient(path, null, null);
    }
    private HttpClient createClient(String path, String query)
    {
        return createClient(path, null, null);
    }
    private HttpClient createClient(String path, String query, String fragment)
    {
        try
        {
            int port = httpPort == 80 ? -1 : httpPort;
            URI uri = new URI("http", null, proxyHost, port, path, query, fragment);
            return new HttpClient(proxyHost, proxyPort, Method.GET, uri);
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    @Test
    public void testPartialLM() throws IOException
    {
        String exp = createContent(4000);
        String path = "/testPartialLM";
        server.setContent(path, exp);
        server.setFailSend(true);
        server.setLastModified(fromClock(-5, ChronoUnit.DAYS));
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(2, server.getRequestCount());
    }
    
    @Test
    public void testPartialETag() throws IOException
    {
        String exp = createContent(4000);
        String path = "/testPartialETag";
        server.setContent(path, exp);
        server.setFailSend(true);
        server.setETag("123456");
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(2, server.getRequestCount());
    }
    
    @Test
    public void testPartialETagChange() throws IOException
    {
        String exp = createContent(4000);
        String path = "/testPartialETagChange";
        server.setContent(path, exp);
        server.setFailSend(true);
        server.setETag("123456");
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        
        server.setETag("muuttunut");
        cl.addHeader("If-None-Match", "\"123456\"");
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(4, server.getRequestCount());
    }
    
    @Test
    public void testVary() throws IOException
    {
        String expFI = "Hei maailma!";
        String expEN = "Hello World!";
        server.setContent("/varyFI", expFI);
        server.setContent("/varyEN", expEN);
        server.addHeader("Vary", "Accept-Language");
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        
        HttpClient clFI = createClient("/vary");
        clFI.addHeader("Accept-Language", "FI");
        int sc = clFI.retrieve();
        assertEquals(200, sc);
        assertEquals(expFI, clFI.getContent());
        
        HttpClient clEN = createClient("/vary");
        clEN.addHeader("Accept-Language", "EN");
        sc = clEN.retrieve();
        assertEquals(200, sc);
        assertEquals(expEN, clEN.getContent());
        
        sc = clFI.retrieve();
        assertEquals(200, sc);
        assertEquals(expFI, clFI.getContent());
        
        sc = clEN.retrieve();
        assertEquals(200, sc);
        assertEquals(expEN, clEN.getContent());
        
        assertEquals(2, server.getRequestCount());
    }
    @Test
    public void testBasic() throws IOException
    {
        String exp = "Hello World!";
        String path = "/testBasic";
        server.setContent(path, exp);
        server.setLastModified(fromClock(-10, ChronoUnit.MINUTES));
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());

        moveClock(10, ChronoUnit.MINUTES);
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(2, server.getRequestCount());
    }
    
    @Test
    public void testOrder() throws IOException
    {
        String exp = "Last 1!";
        String path = "/testOrder";
        server.setContent(path, exp);
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        
        exp = "Last 2!";
        moveClock(10, ChronoUnit.DAYS);
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        server.setContent(path, exp);

        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());

        exp = "Last 3!";
        moveClock(10, ChronoUnit.DAYS);
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        server.setContent(path, exp);

        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
    }
    @Test
    public void testOffline() throws IOException
    {
        String exp = "Last 1!";
        String path = "/testOffline";
        server.setContent(path, exp);
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        Cache.setRefreshTimeout(1);
        server.setMillisBetweenPackets(10);
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        
        moveClock(10, ChronoUnit.DAYS);
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        server.setContent(path, "Last 2");

        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());

        moveClock(10, ChronoUnit.DAYS);
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        server.setContent(path, "Last 3");

        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
    }
    @Test
    public void testEtag() throws IOException
    {
        String exp = "Hello World!";
        String path = "/testEtag";
        server.setContent(path, exp);
        server.setETag("e1");
        server.setLastModified(fromClock(-1, ChronoUnit.HOURS));
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        
        cl.addHeader("If-None-Match", "\"e1\"");
        sc = cl.retrieve();
        assertEquals(304, sc);
        assertEquals(1, server.getRequestCount());
        
        moveClock(10, ChronoUnit.DAYS);
        
        sc = cl.retrieve();
        assertEquals(304, sc);
        assertEquals(2, server.getRequestCount());

        moveClock(10, ChronoUnit.DAYS);
        server.setETag("e2");
        
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(3, server.getRequestCount());

    }
    
    @Test
    public void testPOST() throws IOException
    {
        String exp = "Hello World!";
        String path = "/testPOST";
        server.setContent(path, exp);
        
        HttpClient cl = createClient(path);
        cl.setMethod(Method.POST);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(2, server.getRequestCount());
    }
    
    //@Test
    public void testNotFound() throws IOException
    {
        HttpClient cl = createClient("/testNotFound");
        cl.setMethod(Method.GET);
        int sc = cl.retrieve();
        assertEquals(404, sc);
        sc = cl.retrieve();
        assertEquals(404, sc);
        assertEquals(1, server.getRequestCount());
    }

}
