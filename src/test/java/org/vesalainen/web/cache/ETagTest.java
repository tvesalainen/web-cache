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
import java.time.temporal.ChronoUnit;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.vesalainen.web.cache.Base.server;

/**
 *
 * @author tkv
 */
public class ETagTest extends Base
{
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
        exp = "Hei maailma";;
        server.setContent(path, exp);
        
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        assertEquals(3, server.getRequestCount());

    }
    
}
