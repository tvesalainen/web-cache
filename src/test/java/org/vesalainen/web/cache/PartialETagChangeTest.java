/*
 * Copyright (C) 2016 Timo Vesalainen <timo.vesalainen@iki.fi>
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
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class PartialETagChangeTest extends Base
{
    @Test
    public void testPartialETagChange() throws IOException
    {
        String exp = createContent(4000);
        String path = "/testPartialETagChange";
        server.setContent(path, exp);
        server.setFailSend(true);
        server.setETag("123456");
        //setTimeout(500000);
        
        HttpClient cl = createClient(path);
        int sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(exp, cl.getContent());
        
        moveClock(10, ChronoUnit.DAYS);
        server.setETag("muuttunut");
        cl.addHeader("If-None-Match", "\"123456\"");
        sc = cl.retrieve();
        assertEquals(200, sc);
        assertEquals(4000, cl.getContentLength());
        assertEquals(4, server.getRequestCount());
    }
    
}
