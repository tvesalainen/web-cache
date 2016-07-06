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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.After;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author tkv
 */
public class YleT
{
    private Cache cache;
    @Before
    public void before() throws IOException, InterruptedException
    {
        File dir = new File("c:\\temp\\cache");
        if (dir.exists())
        {
            Path path = dir.toPath();
            Stream<Path> stream = Files.walk(path, 3);
            stream.forEach((Path p)->p.toFile().delete());
        }
        Main cmdLine = new Main();
        cmdLine.command("-ll", "FINEST", "-pl", "FINEST", "c:\\temp\\cache");
        cache = new Cache();
        cache.start(cmdLine.getArgument("cacheDir"), 3128);
    }
    @After
    public void after()
    {
        Cache.stop();
    }
    @Test
    public void server()
    {
        try
        {
            String[] headers = new String[] 
            {
                "Proxy-Connection: keep-alive",
                "Upgrade-Insecure-Requests: 1",
                "User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36",
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                "Accept-Encoding: gzip, deflate, sdch",
                "Accept-Language: fi-FI,fi;q=0.8,en-US;q=0.6,en;q=0.4",
                "Cookie: _chartbeat2=DUDf2gIKmmDB8MFuI.1440011851826.1440011851826.1; yle_selva=14442404600957365619; _ga=GA1.2.645176044.1427213894; __utma=232409815.645176044.1427213894.1467806519.1467811845.806; __utmb=232409815.6.10.1467811845; __utmc=232409815; __utmz=232409815.1467740857.801.11.utmcsr=yle.fi|utmccn=(referral)|utmcmd=referral|utmcct=/uutiset"
            };
            URI uri = new URI("http://www.yle.fi/uutiset");
            HttpClient client = new HttpClient("localhost", 3128, 10000, Method.GET, uri, headers);
            client.retrieve();
        }
        catch (IOException | URISyntaxException ex)
        {
            Logger.getLogger(YleT.class.getName()).log(Level.SEVERE, null, ex);
            fail(ex.getMessage());
        }
    }
}
