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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.vesalainen.test.DebugHelper;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class Base extends JavaLogging
{
    public static boolean debugging = DebugHelper.guessDebugging();
    protected static final String proxyHost = "localhost";
    protected static final int proxyPort = 8080;
    protected static final int httpPort = 80;
    protected static final File dir = new File("c:\\temp\\cache");
    protected static HttpServer server;
    protected static final String Content = "ABCDEFGHIJKLMNOPQRSTUWVXYZ1234567890abcdefghijklmnopqrstuwvxyz";
    protected long timeout = 5000; 

    public Base()
    {
        super(Base.class);
    }
    @BeforeClass
    public static void init() throws Exception
    {
        if (dir.exists())
        {
            JavaLogging.getLogger(Base.class).info("clean directory %s", dir);
            Path path = dir.toPath();
            Stream<Path> stream = Files.find(path, 3, (p, b)->{return b.isRegularFile();});
            stream.forEach((Path p) ->
            {
                try
                {
                    boolean success = Files.deleteIfExists(p);
                    JavaLogging.getLogger(Base.class).info("deleted %s %b", p, success);
                }
                catch (IOException ex)
                {
                    throw new RuntimeException(ex);
                }
            });
        }
        Main.main("-dontWait", "true", "-lx", "src\\test\\resources\\log.xml", "src\\test\\resources\\web-cache.xml");
        JavaLogging.setClockSupplier(Cache::getClock);
        server = new HttpServer(httpPort, Cache::getClock);
        server.start();
    }

    @AfterClass
    public static void cleanup() throws InterruptedException
    {
        Thread.sleep(1000);
        server.stop();
        Cache.stop();
    }
    @Before
    public void before()
    {
        if (debugging)
        {
            setTimeout(500000);
        }
        else
        {
            setTimeout(4000);
        }
    }
    protected Clock now = Clock.tickSeconds(ZoneId.of("Z"));

    public Base(Class<?> cls)
    {
        super(cls);
    }

    public void setTimeout(long timeout)
    {
        info("SET TIMEOUT %d", timeout);
        Config.setRefreshTimeout((int) timeout);
        this.timeout = timeout;
    }

    @Before
    public void clear() throws IOException
    {
        info("TEST CLEAR");
        Cache.gc();
        server.clear();
        Cache.setClock(now);
    }

    protected void moveClock(long amount, ChronoUnit unit)
    {
        now = Clock.offset(now, Duration.of(amount, unit));
        Cache.setClock(now);
        info("MOVE CLOCK TO %s", now);
    }

    protected ZonedDateTime fromClock(long amount, ChronoUnit unit)
    {
        return ZonedDateTime.now(now).plus(amount, unit);
    }

    protected String createContent(int size)
    {
        StringBuilder sb = new StringBuilder();
        int mod = Content.length();
        for (int ii = 0; ii < size; ii++)
        {
            sb.append(Content.charAt(ii % mod));
        }
        return sb.toString();
    }

    protected HttpClient createClient(String path)
    {
        return createClient(path, null, null);
    }

    protected HttpClient createClient(String path, String query)
    {
        return createClient(path, null, null);
    }

    protected HttpClient createClient(String path, String query, String fragment)
    {
        try
        {
            int port = httpPort == 80 ? -1 : httpPort;
            URI uri = new URI("http", null, proxyHost, port, path, query, fragment);
            return new HttpClient(proxyHost, proxyPort, timeout, Method.GET, uri);
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    
}
