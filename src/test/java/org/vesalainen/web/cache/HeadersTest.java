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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tkv
 */
public class HeadersTest
{
    
    public HeadersTest()
    {
    }

    @Test
    public void testETagWeakEquals()
    {
        assertTrue(Headers.eTagWeakEquals("W/\"1\"", "W/\"1\""));
        assertFalse(Headers.eTagWeakEquals("W/\"1\"", "W/\"2\""));
        assertTrue(Headers.eTagWeakEquals("W/\"1\"", "\"1\""));
        assertTrue(Headers.eTagWeakEquals("\"1\"", "\"1\""));
    }
    
    @Test
    public void testETagStrongEquals()
    {
        assertFalse(Headers.eTagStrongEquals("W/\"1\"", "W/\"1\""));
        assertFalse(Headers.eTagStrongEquals("W/\"1\"", "W/\"2\""));
        assertFalse(Headers.eTagStrongEquals("W/\"1\"", "\"1\""));
        assertTrue(Headers.eTagStrongEquals("\"1\"", "\"1\""));
    }
    
    @Test
    public void testWeightedEquals0()
    {
        String s1 = "text/plain; q=0.5, text/html, text/x-dvi; q=0.8, text/x-c";
        String s2 = "text/plain;  q=0.5,   text/html,  text/x-dvi; q=0.8, text/x-c";
        assertTrue(Headers.weightedEquals(s1, s2));
    }
    @Test
    public void testWeightedEquals1()
    {
        String s1 = "text/plain; q=0.5, text/html, text/x-dvi; q=0.8, text/x-c";
        String s2 = "text/html, text/plain; q=0.5, text/x-dvi; q=0.8, text/x-c";
        assertTrue(Headers.weightedEquals(s1, s2));
    }
    @Test
    public void testWeightedEquals2()
    {
        String s1 = "text/*, text/plain, text/plain;format=flowed, */*";
        String s2 = "text/*, text/plain, text/plain; format=flowed, */*";
        assertTrue(Headers.weightedEquals(s1, s2));
    }
    @Test
    public void testWeightedEquals3()
    {
        String s1 = "text/html;charset=UTF-8";
        String s2 = "Text/HTML;Charset=\"utf-8\"";
        assertTrue(Headers.weightedEquals(s1, s2));
    }
}
