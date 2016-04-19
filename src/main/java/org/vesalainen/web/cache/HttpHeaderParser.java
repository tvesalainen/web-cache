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
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.vesalainen.lang.Primitives;
import org.vesalainen.nio.ByteBufferCharSequenceFactory;
import org.vesalainen.nio.ByteBufferCharSequence;
import org.vesalainen.parser.GenClassFactory;
import org.vesalainen.parser.ParserConstants;
import org.vesalainen.parser.annotation.GenClassname;
import org.vesalainen.parser.annotation.GrammarDef;
import org.vesalainen.parser.annotation.ParseMethod;
import org.vesalainen.parser.annotation.ParserContext;
import org.vesalainen.parser.annotation.Rule;
import org.vesalainen.parser.annotation.Rules;
import org.vesalainen.parser.annotation.Terminal;
import org.vesalainen.parser.annotation.Terminals;
import org.vesalainen.parser.util.InputReader;
import org.vesalainen.regex.SyntaxErrorException;
import org.vesalainen.util.CharSequences;
import org.vesalainen.util.LinkedMap;
import org.vesalainen.util.logging.JavaLogging;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
@GenClassname("org.vesalainen.web.cache.HttpHeaderParserImpl")
@GrammarDef()
@Terminals({
@Terminal(left="COLON", expression="[ \t]*:[ \t]*"),
@Terminal(left="CRLF", expression="\r\n"),
@Terminal(left="SP", expression="[ ]+"),
@Terminal(left="LWS", expression="\r[ \t]+")
})
public abstract class HttpHeaderParser extends JavaLogging
{
    protected ByteBuffer bb;
    protected ByteBufferCharSequenceFactory factory;
    private final Map<CharSequence,List<ByteBufferCharSequence>> headers = new LinkedMap<>();
    private ByteBufferCharSequence requestTarget;
    private ByteBufferCharSequence version;
    private ByteBufferCharSequence firstLine;
    private final List<ByteBufferCharSequence> bbcsList = new ArrayList<>();
    private Method method;
    private int statusCode;
    private ByteBufferCharSequence reasonPhrase;
    private int size;
    private boolean isRequest;
    private ByteBufferCharSequence headerPart;
    private final CharSequence peek;
    private long offset;
    private ZonedDateTime time;

    protected HttpHeaderParser(ByteBuffer bb)
    {
        this.bb = bb;
        this.factory = new ByteBufferCharSequenceFactory(bb, OP);
        this.peek = factory.peekRead();
    }
    
    public boolean isCacheable()
    {
        if (isRequest)
        {
            return
                    methodOk() &&
                    authorizationOk() &&
                    noStoreOk()
                    ;
        }
        else
        {
            return 
                    contentLengthOk() &&
                    statusOk() &&
                    noStoreOk() &&
                    privateOk() &&
                    varyOk() &&
                    zeroExpireOk() &&
                    (
                        expiresOk() ||
                        maxAgeOk() ||
                        sMaxAgeOk() ||
                        publicOk()
                    );
        }
    }
    
    private ByteBufferCharSequence get(InputReader input)
    {
        return factory.create(input.getStart(), input.getEnd());
    }
    public static HttpHeaderParser getInstance(ByteBuffer bb)
    {
        return (HttpHeaderParser) GenClassFactory.getGenInstance(HttpHeaderParser.class, bb);
    }

    public void parseRequest() throws IOException
    {
        headers.clear();
        factory.reset();
        headerPart = extractHeader();
        parseReq(headerPart);
        isRequest = true;
        offset = 0;
        time = ZonedDateTime.now(Cache.getClock());
    }
    
    public void parseResponse() throws IOException
    {
        headers.clear();
        factory.reset();
        headerPart = extractHeader();
        parseResp(headerPart);
        isRequest = false;
        ZonedDateTime date = getDateHeader(Date);
        if (date != null)
        {
            ZonedDateTime now = ZonedDateTime.now(Cache.getClock());
            Duration duration = Duration.between(date, now);
            offset = duration.getSeconds();
        }
        else
        {
            offset = 0;
        }
        time = ZonedDateTime.now(Cache.getClock());
    }

    public boolean hasWholeHeader()
    {
        return CharSequences.indexOf(peek, "\r\n\r\n") != -1;
    }
    
    public boolean acceptRanges()
    {
        ByteBufferCharSequence unit = getHeader(AcceptRanges);
        if (unit != null)
        {
            return CharSequences.equals(Bytes, unit);
        }
        return false;
    }
    private ByteBufferCharSequence extractHeader()
    {
        ByteBufferCharSequence hdr = factory.allRemaining();
        int idx = CharSequences.indexOf(hdr, "\r\n\r\n");
        if (idx == -1)
        {
            throw new SyntaxErrorException("end of header missing: "+hdr);
        }
        int i2 = CharSequences.indexOf(hdr, "\r\n");
        firstLine = (ByteBufferCharSequence) hdr.subSequence(0, i2+2);
        return (ByteBufferCharSequence) hdr.subSequence(0, idx+4);
    }
    /**
     * Writes headers content
     * @param channel
     * @return Written bytes
     * @throws IOException 
     */
    public int write(GatheringByteChannel channel) throws IOException
    {
        int size = 0;
        bbcsList.clear();
        bbcsList.add(firstLine);
        size += firstLine.length();
        for (CharSequence key : headers.keySet())
        {
            for (ByteBufferCharSequence hdr : headers.get(key))
            {
                bbcsList.add(hdr);
                bbcsList.add(crlf());
                size += hdr.length() + 2;
            }
        }
        bbcsList.add(crlf());
        size += 2;
        ByteBufferCharSequence.writeAll(channel, bbcsList);
        return size;
    }
    private ByteBufferCharSequence crlf()
    {
        return (ByteBufferCharSequence) firstLine.subSequence("\r\n");
    }
    @ParseMethod(start="httpRequest")
    protected abstract void parseReq(ByteBufferCharSequence text);

    @ParseMethod(start="httpResponse")
    protected abstract void parseResp(ByteBufferCharSequence text);

    @Rule("method SP requestTarget SP httpVersion CRLF headers CRLF")
    protected void httpRequest(Method method, ByteBufferCharSequence requestTarget, ByteBufferCharSequence version, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.method = method;
        this.requestTarget = requestTarget;
        this.version = version;
        this.size = input.getEnd();
    }
    @Rule("httpVersion SP statusCode (SP reasonPhrase)? CRLF headers CRLF")
    protected void httpResponse(ByteBufferCharSequence version, int code, ByteBufferCharSequence reason, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.version = version;
        this.statusCode = code;
        this.reasonPhrase = reason;
        this.size = input.getEnd();
    }
    @Rules({
    @Rule("get"),
    @Rule("head"),
    @Rule("post"),
    @Rule("connect")
    })
    protected abstract Method method(Method method);

    @Terminal(expression="GET")
    protected Method  get()
    {
        return Method.GET;
    }
    @Terminal(expression="HEAD")
    protected Method  head()
    {
        return Method.HEAD;
    }
    @Terminal(expression="POST")
    protected Method  post()
    {
        return Method.POST;
    }
    @Terminal(expression="CONNECT")
    protected Method  connect()
    {
        return Method.CONNECT;
    }
    @Rule("string")
    protected ByteBufferCharSequence requestTarget(ByteBufferCharSequence url) throws MalformedURLException
    {
        return url;
    }
    @Rule("'HTTP/' string")
    protected ByteBufferCharSequence httpVersion(ByteBufferCharSequence version) throws MalformedURLException
    {
        return version;
    }
    @Rule()
    protected void headers()
    {
    }
    @Rule({"headers fieldName COLON fieldValue? CRLF"})
    protected void headers(ByteBufferCharSequence name, ByteBufferCharSequence value)
    {
        if (value != null)
        {
            addHeader(name, value);
        }
    }
    @Terminal(expression="[^\\x00-\\x20\\(\\)<>@\\,;:\\\\\"/\\[\\]\\?=\\{\\}\t]+")
    protected ByteBufferCharSequence fieldName(InputReader input)
    {
        return get(input);
    }

    @Terminal(expression="[^ \t\r\n]+")
    protected ByteBufferCharSequence string(InputReader input)
    {
        return get(input);
    }

    @Terminal(expression="[^\r\n]+")
    protected ByteBufferCharSequence reasonPhrase(InputReader input)
    {
        return get(input);
    }

    @Terminal(expression="[^\r\n]+")
    protected ByteBufferCharSequence line(InputReader input)
    {
        return get(input);
    }

    @Terminal(expression="[0-9]{3}")
    protected int statusCode(int code)
    {
        return code;
    }

    @Rule({"line"})
    protected ByteBufferCharSequence fieldValue(ByteBufferCharSequence line)
    {
        return line;
    }
    @Rule({"line LWS fieldValue"})
    protected ByteBufferCharSequence fieldValue(ByteBufferCharSequence s1, ByteBufferCharSequence s2)
    {
        return factory.concat(s1, s2);
    }
    public boolean hasHeader(CharSequence name)
    {
        return headers.containsKey(name);
    }
    public List<ByteBufferCharSequence> getHeaders(CharSequence name)
    {
        return headers.get(name);
    }
    public ByteBufferCharSequence getRawHeader(CharSequence name)
    {
        List<ByteBufferCharSequence> list = headers.get(name);
        if (list != null && !list.isEmpty())
        {
            return list.get(0);
        }
        else
        {
            return null;
        }
    }

    public ByteBufferCharSequence getHeader(CharSequence name)
    {
        ByteBufferCharSequence rawHeader = getRawHeader(name);
        if (rawHeader != null)
        {
            int idx = CharSequences.indexOf(rawHeader, ' ');
            return (ByteBufferCharSequence) rawHeader.subSequence(idx+1, rawHeader.length());
        }
        else
        {
            return null;
        }
    }
    public ByteBufferCharSequence getRequestTarget()
    {
        return requestTarget;
    }

    public ByteBufferCharSequence getVersion()
    {
        return version;
    }

    public Method getMethod()
    {
        return method;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public ByteBufferCharSequence getReasonPhrase()
    {
        return reasonPhrase;
    }

    public int getHeaderSize()
    {
        return size;
    }

    public int getMaxAge()
    {
        return getCacheControl("max-age");
    }
    public int getCacheControl(String token)
    {
        ByteBufferCharSequence cacheControl = getHeader(CacheControl);
        if (cacheControl != null)
        {
            int idx = CharSequences.indexOf(cacheControl, token, HttpHeaderParser::caseInsensitive);
            if (idx != -1)
            {
                int idx2 = CharSequences.indexOf(cacheControl, Character::isDigit);
                if (idx == -1)
                {
                    return -1;
                }
                else
                {
                    return Primitives.parseInt(cacheControl, idx2, cacheControl.length());
                }
            }
        }
        return -1;
    }
    
    public int getContentSize()
    {
        return getNumericHeader(ContentLength);
    }

    public int getNumericHeader(CharSequence name)
    {
        ByteBufferCharSequence hdr = getHeader(name);
        if (hdr != null)
        {
            int idx = CharSequences.indexOf(hdr, Character::isDigit);
            if (idx == -1)
            {
                return Primitives.parseInt(hdr);
            }
            else
            {
                return Primitives.parseInt(hdr, idx, hdr.length());
            }
        }
        return -1;
    }

    public ZonedDateTime getDateHeader(CharSequence name)
    {
        ByteBufferCharSequence date = getHeader(name);
        if (date != null)
        {
            if (CharSequences.equals(date, "0"))
            {
                return ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("Z"));
            }
            return ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).plusSeconds(offset);
        }
        return null;
    }

    private void addHeader(ByteBufferCharSequence name, ByteBufferCharSequence value)
    {
        List<ByteBufferCharSequence> list = headers.get(name);
        if (list == null)
        {
            list = new ArrayList<>();
            headers.put(name, list);
        }
        list.add(factory.concat(name, value));
    }

    public void addHeader(CharSequence name,  String value)
    {
        List<ByteBufferCharSequence> list = headers.get(name);
        if (list == null)
        {
            list = new ArrayList<>();
            headers.put(name, list);
        }
        list.add(new ByteBufferCharSequence(name+": "+value));
    }

    public void removeHeader(CharSequence name)
    {
        headers.remove(name);
    }
    
    /**
     * The request method is understood by the cache and defined as being
     * cacheable
     * @return 
     */
    private boolean methodOk()
    {
        return Method.GET == method;
    }

    /**
     * the response status code is understood by the cache
     * @return 
     */
    private boolean statusOk()
    {
        return statusCode == 200;
    }
    /**
     * the "no-store" cache directive (see Section 5.2) does not appear
     * in request or response header fields
     * @return 
     */
    private boolean noStoreOk()
    {
        return !contains(CacheControl, "no-store");
    }
    /**
     * the "private" response directive (see Section 5.2.2.6) does not
     * appear in the response
     * @return 
     */
    private boolean privateOk()
    {
        return !contains(CacheControl, "private");
    }
    private boolean varyOk()
    {
        return !contains(Vary, "*");
    }
    private boolean zeroExpireOk()
    {
        ByteBufferCharSequence exp = getHeader(Expires);
        if (exp != null && exp.length() < 3)
        {
            return false;
        }
        return true;
    }

    /**
     * the Authorization header field (see Section 4.2 of [RFC7235]) does
     * not appear in the request
     * @return 
     */
    private boolean authorizationOk()
    {
        return !headers.containsKey(Authorization);
    }
    /**
     * contains an Expires header field
     * @return 
     */
    private boolean expiresOk()
    {
        return headers.containsKey(Expires);
    }
    /**
     * contains a max-age response directive
     * @return 
     */
    private boolean maxAgeOk()
    {
        return !contains(CacheControl, "max-age");
    }
    /**
     * contains a s-maxage response directive
     * @return 
     */
    private boolean sMaxAgeOk()
    {
        return !contains(CacheControl, "s-maxage");
    }

    private boolean publicOk()
    {
        return !contains(CacheControl, "public");
    }

    private boolean contentLengthOk()
    {
        return headers.containsKey(ContentLength);
    }
    private static boolean caseInsensitive(int i1, int i2)
    {
        return Character.toLowerCase(i1) == Character.toLowerCase(i2);
    }

    private boolean contains(CharSequence hdr, String value)
    {
        ByteBufferCharSequence cacheControl = getHeader(hdr);
        if (cacheControl == null)
        {
            return false;
        }
        else
        {
            int idx = CharSequences.indexOf(cacheControl, value, HttpHeaderParser::caseInsensitive);
            return idx != -1;
        }
    }

    public void stripExtra()
    {
        removeHeader(ProxyConnection);
        removeHeader(Connection);
    }
    
    public boolean isRefreshAttempt()
    {
        return hasHeader(IfNoneMatch) || hasHeader(IfModifiedSince);
    }
    public List<CharSequence> getCommaSplittedHeader(CharSequence name)
    {
        ByteBufferCharSequence header = getHeader(name);
        if (header != null)
        {
            return CharSequences.split(header, ',').map(CharSequences::trim).collect(Collectors.toList());
        }
        else
        {
            return null;
        }
    }
    @Override
    public String toString()
    {
        return "HttpHeaderParser{=\n" + headerPart + '}';
    }

    public ByteBufferCharSequence getResponseLine()
    {
        return firstLine;
    }

    public ByteBufferCharSequence getHeaderPart()
    {
        return headerPart;
    }

    Map<CharSequence, List<ByteBufferCharSequence>> getHeaders()
    {
        return headers;
    }
    /**
     * 
     * @return 
     */
    public int freshnessLifetime()
    {
        int freshnessLifetime = getCacheControl("s-maxage");
        if (freshnessLifetime != -1)
        {
            return freshnessLifetime;
        }
        freshnessLifetime = getCacheControl("max-age");
        if (freshnessLifetime != -1)
        {
            return freshnessLifetime;
        }
        ZonedDateTime date = getDateHeader(Date);
        if (date != null)
        {
            ZonedDateTime expires = getDateHeader(Expires);
            if (expires != null)
            {
                return (int) Duration.between(date, expires).getSeconds();
            }
            ZonedDateTime lastModified = getDateHeader(LastModified);
            if (lastModified != null)
            {
                return (int) (Duration.between(lastModified, date).getSeconds()/10);
            }
        }
        return -1;
    }

    public ZonedDateTime getTime()
    {
        return time;
    }
    
}
