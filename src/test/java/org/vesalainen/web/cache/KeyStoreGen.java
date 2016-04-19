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
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;

/**
 *
 * @author tkv
 */
public class KeyStoreGen
{
    @Test
    public void get()
    {
        try
        {
            File keyStoreFile = new File("keystore");
            char[] password = "sala".toCharArray();
            String ca = "CA";
            
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (keyStoreFile.exists())
            {
                keyStore.load(new FileInputStream(keyStoreFile), password);
            }
            else
            {
                keyStore.load(null, null);
            }
            X509Gen gen = new X509Gen();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            X509Certificate ssCert = null;
            PrivateKey ssKey = null;
            String ssDN = "CN=Timo in the middle, C=FI";
            if (!keyStore.isKeyEntry(ca))
            {
                KeyPair ssKeyPair = kpg.generateKeyPair();
                ssCert = gen.generateCertificate(ssDN, null, ssKeyPair, null, 1000, "SHA256withRSA");
                ssKey = ssKeyPair.getPrivate();
                keyStore.setKeyEntry(ca, ssKey, password, new X509Certificate[]{ssCert});
            }
            else
            {
                Certificate[] chain = keyStore.getCertificateChain(ca);
                ssCert = (X509Certificate) chain[chain.length-1];
                ssKey = (PrivateKey) keyStore.getKey(ca, password);
            }
            
            KeyPair keyPair = kpg.generateKeyPair();
            X509Certificate cert = gen.generateCertificate("CN=localhost, C=FI", ssDN, keyPair, ssKey, 1000, "SHA256withRSA");
            
            keyStore.setKeyEntry(ca, keyPair.getPrivate(), password, new X509Certificate[]{cert, ssCert});
            FileOutputStream file = new FileOutputStream(keyStoreFile);
            keyStore.store(file, password);
        }
        catch (GeneralSecurityException | IOException ex)
        {
            Logger.getLogger(KeyStoreGen.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
}
