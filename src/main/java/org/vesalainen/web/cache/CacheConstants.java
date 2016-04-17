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

import java.nio.charset.StandardCharsets;
import java.util.function.IntUnaryOperator;
import org.vesalainen.util.CharSequences;

/**
 *
 * @author tkv
 */
public class CacheConstants
{
    public static final int BufferSize = 1024;
    public static final String XOrigVary  = "X-Orig-Vary-";
    public static final String XOrigHdr  = "X-Orig-Hdr";

    public static final String SHA1  = "SHA-1";
    public static final String NotModifiedCount  = "Not-Modified-Count";
    public static final String LastNotModified  = "Last-Not-Modified";

    public static final byte[] Warn110 = "Warning: 110 - \"Response is Stale\"\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Warn111 = "Warning: 111 - \"Revalidation Failed\"\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Warn112 = "Warning: 112 - \"Disconnected Operation\"\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Warn113 = "Warning: 113 - \"Heuristic Expiration\"\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Warn199 = "Warning: 199 - \"Miscellaneous Warning\"\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Warn214 = "Warning: 214 - \"Transformation Applied\"\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Warn299 = "Warning: 299 - \"Miscellaneous Persistent Warning\"\r\n".getBytes(StandardCharsets.US_ASCII);
    
    public static final IntUnaryOperator OP = Character::toLowerCase;
    public static final CharSequence Date = CharSequences.getConstant("Date", OP);
    public static final CharSequence Authorization = CharSequences.getConstant("Authorization", OP);
    public static final CharSequence Warning = CharSequences.getConstant("Warning", OP);
    public static final CharSequence Pragma = CharSequences.getConstant("Pragma", OP);
    public static final CharSequence Expires = CharSequences.getConstant("Expires", OP);
    public static final CharSequence CacheControl = CharSequences.getConstant("Cache-Control", OP);
    public static final CharSequence Age = CharSequences.getConstant("Age", OP);
    public static final CharSequence Host = CharSequences.getConstant("Host", OP);
    public static final CharSequence Connection = CharSequences.getConstant("Connection", OP);
    public static final CharSequence ProxyConnection = CharSequences.getConstant("Proxy-Connection", OP);
    public static final CharSequence ContentLength = CharSequences.getConstant("Content-Length", OP);
    public static final CharSequence Vary = CharSequences.getConstant("Vary", OP);
    public static final CharSequence LastModified = CharSequences.getConstant("Last-Modified", OP);
    public static final CharSequence ETag = CharSequences.getConstant("ETag", OP);
    public static final CharSequence IfMatch = CharSequences.getConstant("If-Match", OP);
    public static final CharSequence IfNoneMatch = CharSequences.getConstant("If-None-Match", OP);
    public static final CharSequence IfModifiedSince = CharSequences.getConstant("If-Modified-Since", OP);
    public static final CharSequence IfUnmodifiedSince = CharSequences.getConstant("If-Unmodified-Since", OP);
    public static final CharSequence Range = CharSequences.getConstant("Range", OP);
    public static final CharSequence IfRange = CharSequences.getConstant("If-Range", OP);
    public static final CharSequence AcceptRanges = CharSequences.getConstant("Accept-Ranges", OP);
    public static final CharSequence ContentLocation = CharSequences.getConstant("ContentLocation", OP);
    public static final CharSequence Bytes = CharSequences.getConstant("bytes", OP);
    
    public static final byte[] Resp200 = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] Resp304 = "HTTP/1.1 304 Not Modified\r\n".getBytes(StandardCharsets.US_ASCII);
    public static final CharSequence[] Resp304Incl = new CharSequence[] {CacheControl, ContentLocation, Date, ETag, Expires, Vary};
}
