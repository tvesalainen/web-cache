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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.junit.Test;
import org.vesalainen.net.ssl.SSLServerSocketChannel;
import org.vesalainen.net.ssl.SSLSocketChannel;
import org.vesalainen.util.HexDump;

/**
 *
 * @author tkv
 */
public class SSLSocketChannelTest
{
    private SSLContext sslCtx;
    public SSLSocketChannelTest() throws IOException
    {
        sslCtx = TestSSLContext.getInstance();
    }

    @Test
    public void test1() throws IOException
    {
    }

    private class Server implements Callable<SSLSocketChannel>
    {

        @Override
        public SSLSocketChannel call() throws Exception
        {
            try
            {
                SSLServerSocketChannel channel = SSLServerSocketChannel.open(new InetSocketAddress(443), sslCtx);
                return channel.accept();
            }
            catch (IOException ex)
            {
                throw new RuntimeException(ex);
            }
        }
        
    }
}
