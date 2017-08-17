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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import org.vesalainen.lang.Primitives;
import org.vesalainen.util.CharSequences;
import org.vesalainen.util.stream.Streams;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Headers
{
    private static final Map<CharSequence,BiPredicate<CharSequence,CharSequence>> map = new HashMap<>();
    static
    {
        map.put(ETag, Headers::eTagWeakEquals);
    }
    
    public static boolean equals(CharSequence hdr, CharSequence e1, CharSequence e2)
    {
        if (e1 == null)
        {
            return e2 == null;
        }
        if (e2 == null)
        {
            return e1 == null;
        }
        BiPredicate<CharSequence, CharSequence> op = map.get(hdr);
        if (op != null)
        {
            return op.test(e1, e2);
        }
        return weightedEquals(e1, e2);
    }
    
    public static boolean eTagStrongEquals(CharSequence e1, CharSequence e2)
    {
        if (oneIsNull(e1, e2))
        {
            return false;
        }
        if (CharSequences.indexOf(e1, '*') != -1 || CharSequences.indexOf(e2, '*') != -1)
        {
            return false;
        }
        if (e1.charAt(0) == 'W')
        {
            return false;
        }
        if (e2.charAt(0) == 'W')
        {
            return false;
        }
        if (e1.length() == e2.length())
        {
            int len = e1.length();
            for (int ii=0;ii<len;ii++)
            {
                if (e1.charAt(ii) != e2.charAt(ii))
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    public static boolean eTagWeakEquals(CharSequence e1, CharSequence e2)
    {
        if (oneIsNull(e1, e2))
        {
            return false;
        }
        if (CharSequences.indexOf(e1, '*') != -1 || CharSequences.indexOf(e2, '*') != -1)
        {
            return false;
        }
        int i1 = 0;
        int i2 = 0;
        if (e1.charAt(0) == 'W')
        {
            i1 = 2;
        }
        if (e2.charAt(0) == 'W')
        {
            i2 = 2;
        }
        if (e1.length() - i1 == e2.length() - i2)
        {
            int len = e1.length() - i1;
            for (int ii=0;ii<len;ii++)
            {
                if (e1.charAt(ii+i1) != e2.charAt(ii+i2))
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    public static boolean oneIsNull(Object o1, Object o2)
    {
        return 
                (o1 == null && o2 != null) ||
                (o1 != null && o2 == null);
    }
    public static boolean weightedEquals(CharSequence e1, CharSequence e2)
    {
        return Streams.compare(
                CharSequences.split(e1, ',').map(CharSequences::trim).sorted(Headers::compareWeighted),
                CharSequences.split(e2, ',').map(CharSequences::trim).sorted(Headers::compareWeighted),
                Headers::compareToken
        ) == 0;
    }
    private static int compareToken(CharSequence e1, CharSequence e2)
    {
        return Streams.compare(
                e1.codePoints().map(Character::toLowerCase).filter(Character::isLetterOrDigit),
                e2.codePoints().map(Character::toLowerCase).filter(Character::isLetterOrDigit)
        );
    }
    private static int compareWeighted(CharSequence e1, CharSequence e2)
    {
        float w1 = weight(e1);
        float w2 = weight(e2);
        int c = (int) Math.signum(w1 - w2);
        if (c != 0)
        {
            return c;
        }
        return CharSequences.compare(e1, e2, OP);
    }
    private static float weight(CharSequence seq)
    {
        int idx = CharSequences.indexOf(seq, "q=", (int i1, int i2)->{return Character.toLowerCase(i1)==Character.toLowerCase(i2);});
        if (idx != -1)
        {
            int i1 = CharSequences.indexOf(seq, Headers::isDecimal, idx+2);
            if (i1 == -1)
            {
                return 1;
            }
            int i2 = CharSequences.indexOf(seq, Headers::notDecimal, i1);
            if (i2 == -1)
            {
                i2 = seq.length();
            }
            return Primitives.parseFloat(seq, i1, i2);
        }
        return 1;
    }
    private static boolean notDecimal(int cc)
    {
        return !isDecimal(cc);
    }
    private static boolean isDecimal(int cc)
    {
        return Character.isDigit(cc) || cc == '.';
    }
}
