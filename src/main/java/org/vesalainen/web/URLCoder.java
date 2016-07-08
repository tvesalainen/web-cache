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

import org.vesalainen.lang.Primitives;
import org.vesalainen.regex.RegexReplacer;
import org.vesalainen.util.stream.Streams;

/**
 *
 * @author tkv
 */
public class URLCoder
{
    private static final URLReplacer replacer = new URLReplacer();
    public static final void decode(StringBuilder sb, CharSequence encoded)
    {
        replacer.replace(sb, encoded);
    }
    /**
        0000 0
        0001 1
        0010 2
        0011 3
        0100 4
        0101 5
        0110 6
        0111 7
        1000 8
        1001 9
        1010 a
        1011 b
        1100 c
        1101 d
        1110 e
        1111 f     
        */
    private static class URLReplacer extends RegexReplacer
    {

        public URLReplacer()
        {
            addExpression("[\\+]", (sb,c,s,e)->
            {
                sb.append(' ');
            });
            addExpression("%[0-7][0-9a-fA-F]", (sb,c,s,e)->
            {
                int cc = Primitives.parseInt(c, 16, s+1, e);
                sb.append((char) cc);
            });
        }
        
    }
}
