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

/**
 * URLCoder is a replacement to java.net.URLDecoder.
 * URLCoder decodes url-encoded CharSequences when you don't know the original 
 * charset. UTF-8, ISO8859-1 or ASCII. It accepts badly encoded strings. 
 * '%' starting sequences, which cannot be processed, are left as-they-are.
 * <p>
 * All Unicode (and ASCII) strings are parsed correctly.
 * <p>
 * ISO8859-1 encoded strings are parsed correctly in practically all cases. 
 * Character combinations which will be parsed as UTF-8 are extremely rare in 
 * any normal text. E.g. 'Ã¤' will be wrongly parsed to 'ä'
 * @author tkv
 */
public class URLCoder
{
    private static final UTF8Replacer replacer = new UTF8Replacer();
    /**
     * Returns decoded string
     * @param encoded URL encoded Unicode/ISO8859-1/ASCII text.
     * @return 
     */
    public static final String decode(CharSequence encoded)
    {
        StringBuilder sb = new StringBuilder();
        decode(sb, encoded);
        return sb.toString();
    }
    /**
     * Appends decoded text to sb.
     * @param sb
     * @param encoded URL encoded Unicode/ISO8859-1/ASCII text.
     */
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
    private static final String AnyHex = "[0-9a-zA-Z]";
    private static final String Any7 = "[0-7]";
    private static final String Continuation = "%[89abAB]";
    private static final String Pre1 = "%[0-9a-zA-Z]";
    private static final String Pre2 = "%[cdCD]";
    private static final String Pre3 = "%[eE]";
    private static final String Pre4 = "%[fF]";
    private static class UTF8Replacer extends RegexReplacer
    {

        public UTF8Replacer()
        {
            addExpression("[\\+]", (sb,c,s,e)->
            {
                sb.append(' ');
            });
            addExpression(Pre1 + AnyHex, (sb,c,s,e)->
            {
                int cc = Primitives.parseInt(c, 16, s+1, e);
                sb.append((char) cc);
            });
            addExpression(Pre2 + AnyHex + Continuation + AnyHex, (sb,c,s,e)->
            {
                int x1 = Primitives.parseInt(c, 16, s+1, s+3);
                int x2 = Primitives.parseInt(c, 16, s+4, s+6);
                int cp = ((x1 & 0b11111)<<6) | (x2 & 0b111111);
                sb.append((char) cp);
            });
            addExpression(Pre3 + AnyHex + Continuation + AnyHex + Continuation + AnyHex, (sb,c,s,e)->
            {
                int x1 = Primitives.parseInt(c, 16, s+1, s+3);
                int x2 = Primitives.parseInt(c, 16, s+4, s+6);
                int x3 = Primitives.parseInt(c, 16, s+7, s+9);
                int cp = ((x1 & 0b1111)<<12) | ((x2 & 0b111111)<<6) | (x3 & 0b111111);
                sb.append((char) cp);
            });
            addExpression(Pre4 + Any7 + Continuation + AnyHex + Continuation + AnyHex + Continuation + AnyHex, (sb,c,s,e)->
            {
                int x1 = Primitives.parseInt(c, 16, s+1, s+3);
                int x2 = Primitives.parseInt(c, 16, s+4, s+6);
                int x3 = Primitives.parseInt(c, 16, s+7, s+9);
                int x4 = Primitives.parseInt(c, 16, s+10, s+12);
                int cp = ((x1 & 0b111)<<18) | ((x2 & 0b111111)<<12) | ((x3 & 0b111111)<<6) | (x4 & 0b111111);
                sb.append(Character.highSurrogate(cp));
                sb.append(Character.lowSurrogate(cp));
            });
            compile();
        }
        
    }
}
