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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tkv
 */
public class X509SunTest
{
    
    public X509SunTest()
    {
    }

    @Test
    public void testit() throws NoSuchAlgorithmException, GeneralSecurityException, IOException
    {
        X509GenSun gen = new X509GenSun();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair ssKeyPair = kpg.generateKeyPair();
        X509Certificate ssCert = gen.generateCertificate("CN=timo, C=FI", null, ssKeyPair, null, 1000, "SHA1withRSA");
        System.err.println(ssCert);
        KeyPair keyPair = kpg.generateKeyPair();
        X509Certificate cert = gen.generateCertificate("CN=uhri", "CN=timo, C=FI", keyPair, ssKeyPair.getPrivate(), 1000, "SHA1withRSA");
        System.err.println(cert);
    }
    
}