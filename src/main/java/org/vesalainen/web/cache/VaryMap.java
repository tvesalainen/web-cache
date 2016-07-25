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
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.nio.file.attribute.UserDefinedFileAttributes;
import static org.vesalainen.web.cache.CacheConstants.OP;
import static org.vesalainen.web.cache.CacheConstants.Vary;
import static org.vesalainen.web.cache.CacheConstants.XOrigVary;
import org.vesalainen.web.parser.HttpHeaderParser;

/**
 *
 * @author tkv
 */
public class VaryMap
{
    public static final VaryMap Empty = new VaryMap();
    
    private Map<CharSequence,CharSequence> map = new HashMap<>();

    public static final VaryMap create(HttpHeaderParser response, HttpHeaderParser request) throws IOException
    {
        List<CharSequence> vary = response.getCommaSplittedHeader(Vary);
        if (vary != null)
        {
            VaryMap varyMap = new VaryMap();
            for (CharSequence hdr : vary)
            {
                varyMap.put(hdr, request.getHeader(hdr));
            }
            return varyMap;
        }
        else
        {
            return Empty;
        }
    }
    
    public static final VaryMap create(HttpHeaderParser response, UserDefinedFileAttributes userAttr) throws IOException
    {
        List<CharSequence> vary = response.getCommaSplittedHeader(Vary);
        if (vary != null)
        {
            VaryMap varyMap = new VaryMap();
            for (CharSequence hdr : vary)
            {
                String name = XOrigVary+hdr.toString();
                if (userAttr.has(name))
                {
                    ByteBuffer b = ByteBuffer.allocate(userAttr.size(name));
                    userAttr.read(name, b);
                    b.flip();
                    varyMap.put(hdr, new ByteBufferCharSequence(b, OP));
                }
            }
            return varyMap;
        }
        else
        {
            return Empty;
        }
    }
    
    public void put(CharSequence hdr, CharSequence value)
    {
        map.put(hdr, value);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.map);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        if (this == obj)
        {
            return true;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final VaryMap other = (VaryMap) obj;
        Map<CharSequence,CharSequence> otherMap = other.map;
        if (map.size() != otherMap.size())
        {
            return false;
        }
        for (Entry<CharSequence,CharSequence> e : map.entrySet())
        {
            CharSequence hdr = e.getKey();
            if (!otherMap.containsKey(hdr))
            {
                return false;
            }
            if (!Headers.equals(hdr, e.getValue(), otherMap.get(hdr)))
            {
                return false;
            }
        }
        return true;
    }
    
    public boolean isMatch(HttpHeaderParser request)
    {
        for (Entry<CharSequence,CharSequence> e : map.entrySet())
        {
            CharSequence hdr = e.getKey();
            if (!Headers.equals(hdr, e.getValue(), request.getHeader(hdr)))
            {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty()
    {
        return map.isEmpty();
    }
    
    @Override
    public String toString()
    {
        return "VaryMap{" + "map=" + map + '}';
    }
    
}
