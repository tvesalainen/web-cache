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

import org.vesalainen.web.parser.HttpHeaderParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.nio.PeekReadCharSequence;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class RequestBuilder extends HeaderBuilder
{
    public static final byte[] Get = "GET ".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] HTTP11 = " HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII);

    public RequestBuilder(ByteBuffer bb, HttpHeaderParser request, CharSequence... exclude)
    {
        super(bb);
        PeekReadCharSequence peek = new PeekReadCharSequence(bb);
        bb.clear();
        bb.put(Get);
        put(request.getOriginFormRequestTarget());
        bb.put(HTTP11);
        Set<CharSequence> excl = Arrays.stream(exclude).collect(Collectors.toSet());
        Map<CharSequence, List<ByteBufferCharSequence>> headers = request.getHeaders();
        for (CharSequence name : headers.keySet())
        {
            if (!excl.contains(name))
            {
                for (ByteBufferCharSequence h : headers.get(name))
                {
                    put(h);
                    bb.put(CRLF);
                }
            }
        }
    }
    
}
