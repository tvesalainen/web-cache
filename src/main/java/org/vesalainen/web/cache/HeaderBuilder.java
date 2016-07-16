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
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.nio.channels.ChannelHelper;
import org.vesalainen.util.CharSequences;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class HeaderBuilder extends JavaLogging
{
    public static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] DELIM = ": ".getBytes(StandardCharsets.US_ASCII);
    protected final ByteBuffer bb;
    protected boolean finished;

    public HeaderBuilder(ByteBuffer bb)
    {
        super(HeaderBuilder.class);
        this.bb = bb;
    }

    public void addHeader(CharSequence name, CharSequence value)
    {
        check();
        put(name);
        bb.put(DELIM);
        put(CharSequences.trim(value));
        bb.put(CRLF);
    }

    public void addHeader(byte[] b)
    {
        check();
        bb.put(b);
        if (b[b.length - 1] != '\n')
        {
            bb.put(CRLF);
        }
    }

    public void finish()
    {
        if (!finished)
        {
            bb.put(CRLF);
            bb.flip();
            finished = true;
        }
    }
    /**
     * Returns header as string. This method calls finish, so it should be called
     * after build and before send.
     * @return 
     */
    public String getString()
    {
        finish();
        StringBuilder sb = new StringBuilder();
        for (int ii=0;ii<bb.limit();ii++)
        {
            sb.append((char)bb.get(ii));
        }
        return sb.toString();
    }
    public void send(ByteChannel channel) throws IOException
    {
        finish();
        ChannelHelper.writeAll(channel, bb);
    }

    protected void check()
    {
        if (finished)
        {
            throw new IllegalStateException("finished");
        }
    }

    protected void put(CharSequence seq)
    {
        if (seq instanceof ByteBufferCharSequence)
        {
            ByteBufferCharSequence bbcs = (ByteBufferCharSequence) seq;
            bb.put(bbcs.getByteBuffer());
            bbcs.reset();
        }
        else
        {
            bb.put(seq.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }
    
}
