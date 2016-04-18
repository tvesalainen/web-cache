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

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import org.junit.Test;

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
            System.setProperty("javax.net.ssl.keyStore", "keystore");
            System.setProperty("javax.net.ssl.keyStorePassword", "sala");
            
            System.setProperty("javax.net.debug", "true");
            System.setProperty("javax.net.ssl.debug", "all");
            
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket ss = (SSLServerSocket) factory.createServerSocket(443);
            while (true)
            {
                System.err.println("ready");
                Socket accept = ss.accept();
                System.err.println("accept");
                byte[] buf = new byte[1024];
                int rc = accept.getInputStream().read(buf);
                System.err.println(rc);
                if (rc != -1)
                {
                    String s = new String(buf, 0, rc);
                    System.err.println(s);
                    OutputStream out = accept.getOutputStream();
                    out.write("HTTP/1.1 200\r\nContent-Length: 11\r\nConnection: close\r\n\r\nHello World".getBytes());
                    out.flush();
                    accept.close();
                }
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(HttpsT.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
