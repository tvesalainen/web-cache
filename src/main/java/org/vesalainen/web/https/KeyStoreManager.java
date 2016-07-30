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
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import org.vesalainen.lang.Primitives;
import org.vesalainen.util.logging.JavaLogging;
import org.vesalainen.web.cache.Config;

/**
 *
 * @author tkv
 */
public class KeyStoreManager extends X509ExtendedKeyManager
{
    private KeyStore keyStore;
    private KeyPairGenerator keyPairGenerator;
    private X509Generator generator;
    private X509Certificate caCert;
    private PrivateKey issuerPrivateKey;
    private ThreadLocal<String> serverName = new ThreadLocal<>();
    private JavaLogging log = new JavaLogging(KeyStoreManager.class);
    private File keyStoreFile;
    private byte[] seed;
    private char[] password;
    
    public KeyStoreManager(File keyStoreFile)
    {
        this.keyStoreFile = keyStoreFile;
        try
        {
            password = Config.getKeyStorePassword();
            String caAlias = Config.getCaAlias();
            log.config("starting key store manager");
            keyStore = KeyStore.getInstance(Config.getKeyStoreType(), "BC");
            if (keyStoreFile.exists())
            {
                log.config("loading %s", keyStoreFile);
                keyStore.load(new FileInputStream(keyStoreFile), password);
                Key key = keyStore.getKey("seed", password);
                seed = key.getEncoded();
            }
            else
            {
                log.config("creating %s", keyStoreFile);
                keyStore.load(null, null);
                seed = new byte[256];
                SecureRandom random = new SecureRandom(Primitives.writeLong(System.currentTimeMillis()));
                random.nextBytes(seed);
                SecretKeySpec secretKey = new SecretKeySpec(seed, "seed");
                keyStore.setKeyEntry("seed", secretKey, password, null);
                store();
            }
            generator = new X509Generator();
            keyPairGenerator = KeyPairGenerator.getInstance(Config.getKeyPairAlgorithm(), "BC");
            keyPairGenerator.initialize(Config.getKeySize());
            if (!keyStore.isKeyEntry(caAlias))
            {
                KeyPair ssKeyPair = keyPairGenerator.generateKeyPair();
                caCert = generator.generateSelfSignedCertificate(Config.getCaDN(), ssKeyPair, Config.getValidDays(), Config.getSigningAlgorithm());
                issuerPrivateKey = ssKeyPair.getPrivate();
                keyStore.setKeyEntry(caAlias, issuerPrivateKey, password(), new X509Certificate[]{caCert});
                log.config("generated %s", caCert);
            }
            else
            {
                Certificate[] chain = keyStore.getCertificateChain(caAlias);
                caCert = (X509Certificate) chain[chain.length-1];
                issuerPrivateKey = (PrivateKey) keyStore.getKey(caAlias, password());
                log.config("loaded %s", caCert);
            }
        }
        catch (IOException | GeneralSecurityException ex)
        {
            log.log(Level.SEVERE, ex, "%s", ex.getMessage());
            throw new IllegalArgumentException(ex);
        }
    }
    
    private char[] password()
    {
        try 
        {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1", "BC");
            byte[] digest = sha1.digest(seed);
            char[] password = new char[digest.length/2];
            for (int ii=0;ii<digest.length;ii+=2)
            {
                password[ii/2] = (char) ((digest[ii]<<8) + digest[ii+1]);
            }
            return password;
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException ex) 
        {
            log.log(Level.SEVERE, ex, "%s", ex.getMessage());
            throw new IllegalArgumentException(ex);
        }
    }
    public void ensureAlias(String hostname)
    {
        try
        {
            if (!keyStore.containsAlias(hostname))
            {
                KeyPair keyPair = keyPairGenerator.generateKeyPair();
                X509Certificate cert = generator.generateCertificate("CN="+hostname, Config.getCaDN(), keyPair, issuerPrivateKey, Config.getValidDays(), Config.getSigningAlgorithm());
                keyStore.setKeyEntry(hostname, keyPair.getPrivate(), Config.getKeyStorePassword(), new X509Certificate[]{cert, caCert});
                store();
                log.config("generated %s", cert);
            }
        }
        catch (GeneralSecurityException ex)
        {
            log.log(Level.SEVERE, ex, "%s", ex.getMessage());
            throw new IllegalArgumentException(ex);
        }
    }
    
    public void store()
    {
        try
        {
            FileOutputStream file = new FileOutputStream(keyStoreFile);
            keyStore.store(file, password);
            log.config("stored %s", file);
        }
        catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException ex)
        {
            log.log(Level.SEVERE, ex, "%s", ex.getMessage());
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
        log.severe("unsupported operation");
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] prncpls, Socket socket)
    {
        log.severe("unsupported operation");
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String[] getServerAliases(String string, Principal[] prncpls)
    {
        log.severe("unsupported operation");
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String chooseServerAlias(String string, Principal[] prncpls, Socket socket)
    {
        log.fine("chooseServerAlias(%s, %s, %s) -> %s", string, Arrays.toString(prncpls), socket, serverName.get());
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
            log.log(Level.SEVERE, ex, "%s", ex.getMessage());
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public PrivateKey getPrivateKey(String alias)
    {
        try
        {
            return (PrivateKey) keyStore.getKey(alias, Config.getKeyStorePassword());
        }
        catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException ex)
        {
            log.log(Level.SEVERE, ex, "%s", ex.getMessage());
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
    public static final String makeWildcard(String name)
    {
        int idx = name.lastIndexOf('.');
        if (idx != -1)
        {
            idx = name.lastIndexOf('.', idx - 1);
            if (idx != -1)
            {
                return "*"+name.substring(idx);
            }
        }
        return name;
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
            String sn = makeWildcard(new String(snisn.getEncoded(), StandardCharsets.UTF_8));
            serverName.set(sn);
            ensureAlias(sn);
            return true;
        }
        
    }
}
