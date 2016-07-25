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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import org.vesalainen.web.cache.Config;

/**
 *
 * @author tkv
 */
public class KeyStoreManager extends X509ExtendedKeyManager
{
    private KeyStore keyStore;
    private KeyPairGenerator kpg;
    private X509GenSun gen;
    private X509Certificate ssCert;
    private PrivateKey issuerPrivateKey;
    private ThreadLocal<String> serverName = new ThreadLocal<>();
    
    public KeyStoreManager()
    {
        try
        {
            File keyStoreFile = Config.getKeystoreFile();
            char[] password = Config.getKeystorePassword();
            String caAlias = Config.getCaAlias();
            
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (keyStoreFile.exists())
            {
                keyStore.load(new FileInputStream(keyStoreFile), password);
            }
            else
            {
                keyStore.load(null, null);
            }
            gen = new X509GenSun();
            kpg = KeyPairGenerator.getInstance(Config.getKeyPairAlgorithm());
            if (!keyStore.isKeyEntry(caAlias))
            {
                KeyPair ssKeyPair = kpg.generateKeyPair();
                ssCert = gen.generateSelfSignedCertificate(Config.getCaDN(), ssKeyPair, Config.getValidDays(), Config.getSigningAlgorithm());
                issuerPrivateKey = ssKeyPair.getPrivate();
                keyStore.setKeyEntry(caAlias, issuerPrivateKey, password, new X509Certificate[]{ssCert});
            }
            else
            {
                Certificate[] chain = keyStore.getCertificateChain(caAlias);
                ssCert = (X509Certificate) chain[chain.length-1];
                issuerPrivateKey = (PrivateKey) keyStore.getKey(caAlias, password);
            }
        }
        catch (IOException | GeneralSecurityException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    
    public void ensureAlias(String hostname)
    {
        try
        {
            if (!keyStore.containsAlias(hostname))
            {
                KeyPair keyPair = kpg.generateKeyPair();
                X509Certificate cert = gen.generateCertificate("CN="+hostname, Config.getCaDN(), keyPair, issuerPrivateKey, Config.getValidDays(), Config.getSigningAlgorithm());
                keyStore.setKeyEntry(hostname, keyPair.getPrivate(), Config.getKeystorePassword(), new X509Certificate[]{cert, ssCert});
                store();
            }
        }
        catch (GeneralSecurityException | IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    
    public void store()
    {
        try
        {
            FileOutputStream file = new FileOutputStream(Config.getKeystoreFile());
            keyStore.store(file, Config.getKeystorePassword());
        }
        catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }
    public void setServerName(String serverName)
    {
        this.serverName.set(serverName);
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
        return serverName.get();
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
            return (PrivateKey) keyStore.getKey(alias, Config.getKeystorePassword());
        }
        catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    public void setSNIMatcher(SSLServerSocket sslServerSocket)
    {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        List<SNIMatcher> matchers = new ArrayList<>();
        matchers.add(new SNIMatcherImpl());
        sslParameters.setSNIMatchers(matchers);
        sslServerSocket.setSSLParameters(sslParameters);
    }
    public void setSNIMatcher(SSLSocket sslSocket)
    {
        SSLParameters sslParameters = sslSocket.getSSLParameters();
        List<SNIMatcher> matchers = new ArrayList<>();
        matchers.add(new SNIMatcherImpl());
        sslParameters.setSNIMatchers(matchers);
        sslSocket.setSSLParameters(sslParameters);
    }
    private class SNIMatcherImpl extends SNIMatcher
    {

        public SNIMatcherImpl()
        {
            super(0);
        }

        @Override
        public boolean matches(SNIServerName snisn)
        {
            serverName.set(new String(snisn.getEncoded(), StandardCharsets.UTF_8));
            ensureAlias(serverName.get());
            return true;
        }
        
    }
}
