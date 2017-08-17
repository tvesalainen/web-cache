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
public class OrderTest extends Base
{
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
}
