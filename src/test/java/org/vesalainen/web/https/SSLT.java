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
package org.vesalainen.web.https;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author tkv
 */
public class SSLT
{
    
    public SSLT()
    {
    }

    //@Test
    public void test1() throws IOException
    {
        HttpsURLConnection con = (HttpsURLConnection) new URL("https://aui-cdn.atlassian.com/aui-adg/5.8.13/js/aui-soy.min.js").openConnection();
        con.connect();
        System.err.println(con.getCipherSuite());
    }
    //@Test
    public void test2() throws IOException
    {
        String host = "aui-cdn.atlassian.com";
        InetSocketAddress inetSocketAddress = new InetSocketAddress(host, 443);
        SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) sf.createSocket();
        SSLParameters sslParameters = socket.getSSLParameters();
        SNIServerName  hostName = new SNIHostName(host);
        List<SNIServerName> list = new ArrayList<>();
        list.add(hostName);
        sslParameters.setServerNames(list);
        socket.connect(inetSocketAddress);
    }
    
    @Test
    public void testWildCard() throws IOException
    {
        assertEquals("*.atlassian.com", KeyStoreManager.makeWildcard("aui-cdn.atlassian.com"));
    }
}
