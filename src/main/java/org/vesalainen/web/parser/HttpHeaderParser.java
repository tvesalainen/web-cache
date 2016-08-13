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

import java.io.EOFException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
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
import org.vesalainen.regex.Regex;
import org.vesalainen.regex.SyntaxErrorException;
import org.vesalainen.time.SimpleMutableDateTime;
import org.vesalainen.util.CharSequences;
import org.vesalainen.util.LinkedMap;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.Scheme;
import org.vesalainen.web.URLCoder;
import org.vesalainen.web.cache.Cache;
import org.vesalainen.web.cache.Method;
import static org.vesalainen.web.cache.CacheConstants.*;

/**
 *
 * @author tkv
 */
@GenClassname("org.vesalainen.web.parser.HttpHeaderParserImpl")
@GrammarDef()
@Terminals({
@Terminal(left="COLON", expression="[ \t]*:[ \t]*"),
@Terminal(left="CRLF", expression="\r?\n"),
@Terminal(left="SP", expression="[ ]+"),
@Terminal(left="LWS", expression="\r[ \t]+")
})
public abstract class HttpHeaderParser extends JavaLogging
{
    protected static final ByteBufferCharSequence Asterisk = new ByteBufferCharSequence("*");
    protected static final HttpDateParser dateParser = HttpDateParser.getInstance();
    protected ByteBuffer bb;
    protected ByteBufferCharSequenceFactory factory;
    private final Map<CharSequence,List<ByteBufferCharSequence>> headers = new LinkedMap<>();
    private ByteBufferCharSequence pathEtc;
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
    private SimpleMutableDateTime time;
    private Scheme scheme;
    private String host;
    private int port;
    private String userinfo;

    protected HttpHeaderParser(Scheme scheme, ByteBuffer bb)
    {
        this.scheme = scheme;
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
    public static HttpHeaderParser getInstance(Scheme protocol, ByteBuffer bb)
    {
        return (HttpHeaderParser) GenClassFactory.getGenInstance(HttpHeaderParser.class, protocol, bb);
    }

    public void parseRequest() throws IOException
    {
        host = null;
        port = 0;
        headers.clear();
        factory.reset();
        headerPart = extractHeader();
        parseReq(headerPart);
        isRequest = true;
        offset = 0;
        time = SimpleMutableDateTime.now(Cache.getClock());
        if (host == null)
        {
            ByteBufferCharSequence header = getHeader(Host);
            int idx = CharSequences.indexOf(header, ':');
            if (idx != -1)
            {
                host = header.subSequence(0, idx).toString();
                port = Primitives.parseInt(header, idx+1, header.length());
            }
            else
            {
                host = header.toString();
            }
        }
    }
    
    public void parseResponse(long millis) throws IOException
    {
        host = null;
        port = 0;
        headers.clear();
        factory.reset();
        headerPart = extractHeader();
        parseResp(headerPart);
        isRequest = false;
        offset = 0;
        SimpleMutableDateTime date = getDateHeader(Date);
        if (date != null)
        {
            offset = millis/1000 - date.seconds();
        }
        time = SimpleMutableDateTime.ofEpochMilli(millis);
    }

    private boolean hasWholeHeader()
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

    @Rule("method SP pathEtc SP httpVersion CRLF headers CRLF")
    protected void httpRequest(Method method, ByteBufferCharSequence pathEtc, ByteBufferCharSequence version, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.method = method;
        this.pathEtc = pathEtc;
        this.version = version;
        this.size = input.getEnd();
    }
    @Rule("method SP scheme '://' authority pathEtc SP httpVersion CRLF headers CRLF")
    protected void httpRequest(Method method, Scheme scheme, Authority authority, ByteBufferCharSequence pathEtc, ByteBufferCharSequence version, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.method = method;
        this.scheme = scheme;
        this.userinfo = authority.getUserinfo();
        this.host = authority.getHost();
        this.port = authority.getPort();
        this.pathEtc = pathEtc;
        this.version = version;
        this.size = input.getEnd();
    }
    @Rule("connect SP authority SP httpVersion CRLF headers CRLF")
    protected void httpRequest(Method method, Authority authority, ByteBufferCharSequence version, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.method = method;
        this.userinfo = authority.getUserinfo();
        this.host = authority.getHost();
        this.port = authority.getPort();
        this.version = version;
        this.size = input.getEnd();
    }
    @Rule("options SP '\\*' SP httpVersion CRLF headers CRLF")
    protected void httpRequest(Method method, ByteBufferCharSequence version, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.method = method;
        this.pathEtc = Asterisk;
        this.version = version;
        this.size = input.getEnd();
    }
    
    @Terminal(expression="[^/ \\?#]+")
    protected Authority authority(String authority)
    {
        String ui = null;
        String h;
        int p = 0;
        int idx = authority.indexOf('@');
        if (idx != -1)
        {
            ui = authority.substring(0, idx);
            authority = authority.substring(idx+1);
        }
        idx = authority.indexOf(':');
        if (idx != -1)
        {
            h = authority.substring(0, idx);
            p = Primitives.parseInt(authority, idx+1, authority.length());
        }
        else
        {
            h = authority;
        }
        return new Authority(ui, h, p);
    }
    @Rule("httpVersion SP statusCode reasonPhrase? CRLF headers CRLF")
    protected void httpResponse(ByteBufferCharSequence version, int code, ByteBufferCharSequence reason, @ParserContext(ParserConstants.InputReader) InputReader input)
    {
        this.version = version;
        this.statusCode = code;
        this.reasonPhrase = reason;
        this.size = input.getEnd();
    }
    @Rule("get")
    @Rule("head")
    @Rule("post")
    @Rule("delete")
    @Rule("put")
    @Rule("trace")
    @Rule("options")
    protected abstract Method method(Method method);

    @Terminal(expression="GET", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  get()
    {
        return Method.GET;
    }
    @Terminal(expression="HEAD", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  head()
    {
        return Method.HEAD;
    }
    @Terminal(expression="POST", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  post()
    {
        return Method.POST;
    }
    @Terminal(expression="CONNECT", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  connect()
    {
        return Method.CONNECT;
    }
    @Terminal(expression="OPTIONS", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  options()
    {
        return Method.OPTIONS;
    }
    @Terminal(expression="DELETE", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  delete()
    {
        return Method.DELETE;
    }
    @Terminal(expression="PUT", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  put()
    {
        return Method.PUT;
    }
    @Terminal(expression="TRACE", options={Regex.Option.CASE_INSENSITIVE})
    protected Method  trace()
    {
        return Method.TRACE;
    }
    @Rules({
    @Rule("http"),
    @Rule("https")
    })
    protected abstract Scheme scheme(Scheme scheme);
    
    @Terminal(expression="HTTP", options={Regex.Option.CASE_INSENSITIVE})
    protected Scheme  http()
    {
        return Scheme.HTTP;
    }
    @Terminal(expression="HTTPS", options={Regex.Option.CASE_INSENSITIVE})
    protected Scheme  https()
    {
        return Scheme.HTTPS;
    }
    
    @Terminal(expression="/[^ ]*")
    protected ByteBufferCharSequence pathEtc(InputReader input)
    {
        return get(input);
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
    public String getRequestTarget()
    {
        StringBuilder sb = new StringBuilder();
        appendLowerCase(sb, scheme.name());
        sb.append("://");
        ByteBufferCharSequence hostHdr = getHeader(Host);
        ByteBufferCharSequence hdrHost = hostHdr;
        int hdrPort = 0;
        int idx = CharSequences.indexOf(hostHdr, ':');
        if (idx != -1)
        {
            hdrHost = (ByteBufferCharSequence) hostHdr.subSequence(0, idx);
            hdrPort = Primitives.parseInt(hostHdr, idx+1, hostHdr.length());
        }
        if (host != null)
        {
            appendLowerCase(sb, host);
        }
        else
        {
            if (hdrHost == null)
            {
                throw new IllegalArgumentException("missing Host: header");
            }
            appendLowerCase(sb, hdrHost);
        }
        if (port != 0)
        {
            appendPort(sb, port);
        }
        else
        {
            if (hdrPort != 0)
            {
                appendPort(sb, hdrPort);
            }
        }
        if (pathEtc != null)
        {
            URLCoder.decode(sb, pathEtc);
        }
        return sb.toString();
    }

    public ByteBufferCharSequence getOriginFormRequestTarget()
    {
        return pathEtc;
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
        return (ByteBufferCharSequence) CharSequences.trim(reasonPhrase);
    }

    public int getHeaderSize()
    {
        return size;
    }

    public long getMaxAge()
    {
        return getCacheControl("max-age");
    }
    public long getCacheControl(String token)
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
                    IntPredicate ip = Character::isDigit;
                    int idx3 = CharSequences.indexOf(cacheControl, ip.negate(), idx2);
                    if (idx3 != -1)
                    {
                        return Primitives.parseLong(cacheControl, idx2, idx3);
                    }
                    else
                    {
                        return Primitives.parseLong(cacheControl, idx2, cacheControl.length());
                    }
                }
            }
        }
        return -1;
    }
    /**
     * Return content length or Integer.MAX_VALUE if not found.
     * @return 
     */
    public long getContentLength()
    {
        long contentSize = getNumericHeader(ContentLength);
        if (contentSize == -1)
        {
            return Integer.MAX_VALUE;
        }
        return contentSize;
    }

    public long getNumericHeader(CharSequence name)
    {
        ByteBufferCharSequence hdr = getHeader(name);
        if (hdr != null)
        {
            int idx = CharSequences.indexOf(hdr, Character::isDigit);
            if (idx == -1)
            {
                return Primitives.parseLong(hdr);
            }
            else
            {
                return Primitives.parseLong(hdr, idx, hdr.length());
            }
        }
        return -1;
    }

    public SimpleMutableDateTime getDateHeader(CharSequence name)
    {
        ByteBufferCharSequence date = getHeader(name);
        if (date != null)
        {
            SimpleMutableDateTime res = dateParser.parse(date);
            res.plusSeconds(offset);
            return res;
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
        if (Method.GET == method)
        {
            return true;
        }
        else
        {
            finest("not cacheable because method=%s", method);
            return false;
        }
    }

    /**
     * the response status code is understood by the cache
     * @return 
     */
    private boolean statusOk()
    {
        if (statusCode == 200 || statusCode == 206)
        {
            return true;
        }
        else
        {
            finest("not cacheable because status=%d", statusCode);
            return false;
        }
    }
    /**
     * the "no-store" cache directive (see Section 5.2) does not appear
     * in request or response header fields
     * @return 
     */
    private boolean noStoreOk()
    {
        if (contains(CacheControl, "no-store"))
        {
            finest("not cacheable because cache-Control: no-store");
            return false;
        }
        else
        {
            return true;
        }
    }
    /**
     * the "private" response directive (see Section 5.2.2.6) does not
     * appear in the response
     * @return 
     */
    private boolean privateOk()
    {
        if (contains(CacheControl, "private"))
        {
            finest("not cacheable because cache-Control: private");
            return false;
        }
        else
        {
            return true;
        }
    }
    private boolean varyOk()
    {
        if (contains(Vary, "*"))
        {
            finest("not cacheable because Vary: *");
            return false;
        }
        else
        {
            return true;
        }
    }
    private boolean zeroExpireOk()
    {
        ByteBufferCharSequence exp = getHeader(Expires);
        if (exp != null && exp.length() < 3)
        {
            finest("not cacheable because Expires: %s", exp);
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
        if (headers.containsKey(Authorization))
        {
            finest("not cacheable because Authorization");
            return false;
        }
        else
        {
            return true;
        }
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
        if (!contains(CacheControl, "max-age"))
        {
            return true;
        }
        else
        {
            finest("not cacheable because Cache-Control : max-age");
            return false;
        }
    }
    /**
     * contains a s-maxage response directive
     * @return 
     */
    private boolean sMaxAgeOk()
    {
        if (!contains(CacheControl, "s-maxage"))
        {
            return true;
        }
        else
        {
            finest("not cacheable because Cache-Control : s-maxage");
            return false;
        }
    }

    private boolean publicOk()
    {
        if (!contains(CacheControl, "public"))
        {
            return true;
        }
        else
        {
            finest("not cacheable because Cache-Control : public");
            return false;
        }
    }

    private boolean contentLengthOk()
    {
        if (headers.containsKey(ContentLength))
        {
            return true;
        }
        else
        {
            finest("not cacheable because no Content-Length");
            return false;
        }
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
        return "HttpHeaderParser{"+scheme+"=\n" + headerPart + '}';
    }

    public ByteBufferCharSequence getResponseLine()
    {
        return firstLine;
    }

    public ByteBufferCharSequence getHeaderPart()
    {
        return headerPart;
    }

    public Map<CharSequence, List<ByteBufferCharSequence>> getHeaders()
    {
        return headers;
    }
    /**
     * 
     * @return 
     */
    public long freshnessLifetime()
    {
        long freshnessLifetime = getCacheControl("s-maxage");
        if (freshnessLifetime != -1)
        {
            return freshnessLifetime;
        }
        freshnessLifetime = getCacheControl("max-age");
        if (freshnessLifetime != -1)
        {
            return freshnessLifetime;
        }
        SimpleMutableDateTime date = getDateHeader(Date);
        if (date != null)
        {
            SimpleMutableDateTime expires = getDateHeader(Expires);
            if (expires != null)
            {
                return (int) (expires.seconds() - date.seconds());
            }
            SimpleMutableDateTime lastModified = getDateHeader(LastModified);
            if (lastModified != null)
            {
                return (int) ((date.seconds() - lastModified.seconds())/10);
            }
        }
        return -1;
    }

    public SimpleMutableDateTime getTime()
    {
        return time;
    }

    private void appendLowerCase(StringBuilder sb, CharSequence txt)
    {
        int len = txt.length();
        for (int ii=0;ii<len;ii++)
        {
            sb.append(Character.toLowerCase(txt.charAt(ii)));
        }
    }

    private void appendPort(StringBuilder sb, int port)
    {
        switch (scheme)
        {
            case HTTP:
                if (port != 80)
                {
                    sb.append(':');
                    sb.append(port);
                }
                break;
            case HTTPS:
                if (port != 443)
                {
                    sb.append(':');
                    sb.append(port);
                }
                break;
            default:
                throw new UnsupportedOperationException(scheme+" not supported");
        }
    }

    public Scheme getScheme()
    {
        return scheme;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        if (port > 0)
        {
            return port;
        }
        switch (scheme)
        {
            case HTTP:
                return 80;
            case HTTPS:
                return 443;
            default:
                throw new UnsupportedOperationException(scheme+"not supported");
        }
    }
    /**
     * Returns path[query[fragment]]
     * @return 
     */
    public ByteBufferCharSequence getPathEtc()
    {
        return pathEtc;
    }

    public void readHeader(ByteChannel channel) throws IOException
    {
        bb.clear();
        while (!hasWholeHeader())
        {
            if (!bb.hasRemaining())
            {
                throw new IOException("ByteBuffer capacity reached "+bb);
            }
            int rc = channel.read(bb);
            if (rc == -1)
            {
                throw new EOFException(channel+"\n["+peek+"]");
            }
        }
        bb.flip();
    }

    public void checkContent(SocketChannel channel) throws IOException
    {
        long contentLength = getNumericHeader(ContentLength);
        if (contentLength > 0)
        {
            long cl = contentLength;
            int headerSize = getHeaderSize();
            cl -= bb.remaining()- headerSize;
            ByteBuffer b = ByteBuffer.allocate(4096);
            while (cl > 0)
            {
                b.clear();
                int rc = channel.read(b);
                if (rc <= 0)
                {
                    throw new EOFException(channel+"\n"+peek);
                }
                cl -= rc;
            }
        }
    }

}
