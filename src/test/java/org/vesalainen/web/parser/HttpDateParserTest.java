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
package org.vesalainen.web.parser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.vesalainen.time.SimpleMutableDateTime;

/**
 *
 * @author tkv
 */
public class HttpDateParserTest
{
    
    public HttpDateParserTest()
    {
    }

    @Test
    public void testJan()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1978, 1, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Jan 1978 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Jan-78 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Jan  6 08:49:37 1978"));
    }
    
    @Test
    public void testFeb()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 2, 6);
        exp.setHour(18);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Feb 1994 18:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Feb-94 18:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Feb  6 18:49:37 1994"));
    }
    
    @Test
    public void testMar()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 3, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(31);
        
        assertEquals(exp, parser.parse("Sun, 06 Mar 1994 08:49:31 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Mar-94 08:49:31 GMT"));
        assertEquals(exp, parser.parse("Sun Mar  6 08:49:31 1994"));
    }
    
    @Test
    public void testApr()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 4, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Apr 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Apr-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Apr  6 08:49:37 1994"));
    }
    
    @Test
    public void testMay()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 5, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 May 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-May-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun May  6 08:49:37 1994"));
    }
    
    @Test
    public void testJun()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 6, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Jun 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Jun-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Jun  6 08:49:37 1994"));
    }
    
    @Test
    public void testJul()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 7, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Jul 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Jul-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Jul  6 08:49:37 1994"));
    }
    
    @Test
    public void testAug()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 8, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Aug 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Aug-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Aug  6 08:49:37 1994"));
    }
    
    @Test
    public void testSep()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 9, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Sep 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Sep-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Sep  6 08:49:37 1994"));
    }
    
    @Test
    public void testOct()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 10, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Oct 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Oct-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Oct  6 08:49:37 1994"));
    }
    
    @Test
    public void testNov()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 11, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Nov 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Nov-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun Nov  6 08:49:37 1994"));
    }
    
    @Test
    public void testDec()
    {
        HttpDateParser parser = HttpDateParser.getInstance();
        SimpleMutableDateTime exp = new SimpleMutableDateTime();
        exp.setDate(1994, 12, 6);
        exp.setHour(8);
        exp.setMinute(49);
        exp.setSecond(37);
        
        assertEquals(exp, parser.parse("Sun, 06 Dec 1994 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sunday, 06-Dec-94 08:49:37 GMT"));
        assertEquals(exp, parser.parse("Sun, 06 Dec 1994 08:49:37 UTC"));
        assertEquals(exp, parser.parse("Sunday, 06-Dec-94 08:49:37 UTC"));
        assertEquals(exp, parser.parse("Sun Dec  6 08:49:37 1994"));
    }
    
}
