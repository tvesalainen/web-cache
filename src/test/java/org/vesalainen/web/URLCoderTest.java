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
package org.vesalainen.web;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tkv
 */
public class URLCoderTest
{
    
    public URLCoderTest()
    {
    }

    @Test
    public void test1()
    {
        try
        {
            String exp = "foo bar\t\n öäå";
            String encoded = URLEncoder.encode(exp, "UTF-8");
            StringBuilder sb = new StringBuilder();
            URLCoder.decode(sb, encoded);
            assertEquals(exp, sb.toString());
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(URLCoderTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
