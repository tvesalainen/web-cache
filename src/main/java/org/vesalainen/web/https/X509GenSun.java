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
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;


/**
 *
 * @author tkv
 */
public class X509GenSun
{

    /**
     * Create a self-signed X.509 Certificate
     *
     * @param subjectDN the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
     * @param pair the KeyPair
     * @param days how many days from now the Certificate is valid for
     * @param algorithm the signing algorithm, e.g. "SHA1withRSA"
     * @return 
     */
    public X509Certificate generateSelfSignedCertificate(String subjectDN, KeyPair pair, int days, String algorithm)
            throws GeneralSecurityException, IOException
    {
        return generateCertificate(subjectDN, null, pair, null, days, algorithm);
    }
    /**
     * Create a signed X.509 Certificate
     * @param subjectDN the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
     * @param issuerDN Signers X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
     * @param pair the KeyPair
     * @param privkey Signers private key
     * @param days how many days from now the Certificate is valid for
     * @param algorithm the signing algorithm, e.g. "SHA1withRSA"
     * @return
     * @throws GeneralSecurityException
     * @throws IOException 
     */
    public X509Certificate generateCertificate(String subjectDN, String issuerDN, KeyPair pair, PrivateKey privkey, int days, String algorithm)
            throws GeneralSecurityException, IOException
    {
        if (privkey == null)
        {
            privkey = pair.getPrivate();
        }
        X509CertInfo info = new X509CertInfo();
        Date from = new Date(System.currentTimeMillis()-86400000l);
        Date to = new Date(from.getTime() + days * 86400000l);
        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger sn = new BigInteger(64, new SecureRandom());
        X500Name subject = new X500Name(subjectDN);
        X500Name issuer;
        if (issuerDN == null)
        {
            issuer = new X500Name(subjectDN);
        }
        else
        {
            issuer = new X500Name(issuerDN);
        }

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        info.set(X509CertInfo.SUBJECT, subject);
        info.set(X509CertInfo.ISSUER, issuer);
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privkey, algorithm);

        // Update the algorith, and resign.
        algo = (AlgorithmId) cert.get(X509CertImpl.SIG_ALG);
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
        cert = new X509CertImpl(info);
        cert.sign(privkey, algorithm);
        return cert;
    }
}
