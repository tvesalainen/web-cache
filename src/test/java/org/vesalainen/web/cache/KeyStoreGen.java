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

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
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
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            KeyPair ssKeyPair = kpg.generateKeyPair();
            X509Gen gen = new X509Gen();
            String dn = "CN=Timo in the middle, C=FI";
            X509Certificate ssCert = gen.generateCertificate(dn, null, ssKeyPair, null, 1000, "SHA256withRSA");
            
            KeyPair keyPair = kpg.generateKeyPair();
            X509Certificate cert = gen.generateCertificate("CN=localhost, C=FI", dn, keyPair, ssKeyPair.getPrivate(), 1000, "SHA256withRSA");
            
            
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("local", keyPair.getPrivate(), "sala".toCharArray(), new X509Certificate[]{cert, ssCert});
            FileOutputStream file = new FileOutputStream("keystore");
            keyStore.store(file, "sala".toCharArray());
        }
        catch (GeneralSecurityException | IOException ex)
        {
            Logger.getLogger(KeyStoreGen.class.getName()).log(Level.SEVERE, null, ex);
        }
  }
}
