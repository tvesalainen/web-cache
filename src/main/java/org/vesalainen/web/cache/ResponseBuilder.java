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

import org.vesalainen.web.parser.HttpHeaderParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.nio.PeekReadCharSequence;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
public class ResponseBuilder extends HeaderBuilder
{
    public static final byte[] HTTP11 = "HTTP/1.1 ".getBytes(StandardCharsets.US_ASCII);

    public ResponseBuilder(ByteBuffer bb, HttpHeaderParser header, Collection<byte[]> extraHeaders)
    {
        super(bb);
        bb.clear();
        put(header.getResponseLine());
        Map<CharSequence, List<ByteBufferCharSequence>> headers = header.getHeaders();
        for (CharSequence name : headers.keySet())
        {
            for (ByteBufferCharSequence h : headers.get(name))
            {
                put(h);
                bb.put(CRLF);
            }
        }
        for (byte[] hdr : extraHeaders)
        {
            addHeader(hdr);
        }
    }
    public ResponseBuilder(ByteBuffer bb, int responseCode, HttpHeaderParser header, Collection<byte[]> extraHeaders)
    {
        super(bb);
        byte[] response = null;
        Set<CharSequence> incl = null;
        Set<CharSequence> excl = null;
        switch (responseCode)
        {
            case 200:
                response = Resp200;
                excl = Arrays.stream(Resp200Excl).collect(Collectors.toSet());
                break;
            case 304:
                response = Resp304;
                incl = Arrays.stream(Resp304Incl).collect(Collectors.toSet());
                break;
            default:
                throw new UnsupportedOperationException(responseCode+ "not supported");
        }
        bb.clear();
        bb.put(response);
        Map<CharSequence, List<ByteBufferCharSequence>> headers = header.getHeaders();
        for (CharSequence name : headers.keySet())
        {
            if (
                    (incl == null || incl.contains(name)) &&
                    (excl == null || !excl.contains(name))
                    )
            {
                for (ByteBufferCharSequence h : headers.get(name))
                {
                    put(h);
                    bb.put(CRLF);
                }
            }
        }
        for (byte[] hdr : extraHeaders)
        {
            addHeader(hdr);
        }
    }
    
}
