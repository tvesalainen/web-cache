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
public class VaryTest extends Base
{
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
}
