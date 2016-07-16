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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.StandardConstants;
import javax.net.ssl.X509ExtendedKeyManager;
import org.junit.Test;
import org.vesalainen.nio.channels.ChannelHelper;

/**
 *
 * @author tkv
 */
public class HttpsT
{
    @Test
    public void https()
    {
        try
        {
            SSLContext sslCtx = SSLContext.getInstance("TLSv1.2");
            KeyMan keyMan  = new KeyMan();
            sslCtx.init(new KeyManager[]{keyMan}, null, null);
            //System.setProperty("javax.net.ssl.keyStore", "keystore");
            //System.setProperty("javax.net.ssl.keyStorePassword", "sala");
            
            //System.setProperty("javax.net.debug", "true");
            //System.setProperty("javax.net.ssl.debug", "all");
            Matcher matcher = new Matcher();
            List<SNIMatcher> matchers = new ArrayList<>();
            matchers.add(matcher);
            SSLServerSocketFactory factory = sslCtx.getServerSocketFactory();
            SSLServerSocket ss = (SSLServerSocket) factory.createServerSocket(443);
            SSLParameters sslParameters = ss.getSSLParameters();
            sslParameters.setSNIMatchers(matchers);
            ss.setSSLParameters(sslParameters);
            while (true)
            {
                System.err.println("ready");
                Socket accept = ss.accept();
                ByteChannel channel = ChannelHelper.newByteChannel(accept);
                System.err.println("accept");
                byte[] buf = new byte[1024];
                ByteBuffer bb = ByteBuffer.wrap(buf);
                int rc = channel.read(bb);
                System.err.println(rc);
                if (rc != -1)
                {
                    String s = new String(buf, 0, rc);
                    System.err.println(s);
                    bb.clear();
                    bb.put("HTTP/1.1 200\r\nContent-Length: 11\r\nConnection: close\r\n\r\nHello World".getBytes());
                    bb.flip();
                    channel.write(bb);
                    accept.close();
                }
            }
        }
        catch (IOException | NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException ex)
        {
            Logger.getLogger(HttpsT.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private static class Matcher extends SNIMatcher
    {

        public Matcher()
        {
            super(0);
        }

        @Override
        public boolean matches(SNIServerName snisn)
        {
            return true;
        }
        
    }
    private static class KeyMan extends X509ExtendedKeyManager
    {
        private final KeyStore keyStore;
        private final char[] password;

        public KeyMan() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException
        {
            File keyStoreFile = new File("keystore");
            password = "sala".toCharArray();
            String ca = "CA";
            
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (keyStoreFile.exists())
            {
                keyStore.load(new FileInputStream(keyStoreFile), password);
            }
            else
            {
                keyStore.load(null, null);
            }
        }

        @Override
        public String[] getClientAliases(String string, Principal[] prncpls)
        {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String chooseClientAlias(String[] strings, Principal[] prncpls, Socket socket)
        {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String[] getServerAliases(String string, Principal[] prncpls)
        {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String chooseServerAlias(String string, Principal[] prncpls, Socket socket)
        {
            SSLSocket sslSocket = (SSLSocket) socket;
            List<SNIServerName> serverNames = sslSocket.getSSLParameters().getServerNames();
            return null;    //"hp.iiris";
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias)
        {
            try
            {
                Certificate[] chain = keyStore.getCertificateChain(alias);
                X509Certificate[] x509Chain = new X509Certificate[chain.length];
                for (int ii=0;ii<chain.length;ii++)
                {
                    x509Chain[ii] = (X509Certificate) chain[ii];
                }
                return x509Chain;
            }
            catch (KeyStoreException ex)
            {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public PrivateKey getPrivateKey(String alias)
        {
            try
            {
                return (PrivateKey) keyStore.getKey(alias, password);
            }
            catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException ex)
            {
                throw new IllegalArgumentException(ex);
            }
        }

    }
}
