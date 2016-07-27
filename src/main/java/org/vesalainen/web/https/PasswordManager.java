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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;
import org.vesalainen.lang.Primitives;
import org.vesalainen.nio.file.attribute.UserDefinedFileAttributes;

/**
 *
 * @author tkv
 */
public class PasswordManager
{
    private final UserDefinedFileAttributes userAttr;
    
    public PasswordManager(File file) throws IOException
    {
        userAttr = new UserDefinedFileAttributes(file, 256);
    }
    public char[] password() throws IOException
    {
        byte[] seed;
        if (userAttr.has("seed"))
        {
            seed = userAttr.get("seed");
        }
        else
        {
            seed = new byte[256];
            SecureRandom random = new SecureRandom(Primitives.writeLong(System.currentTimeMillis()));
            random.nextBytes(seed);
            userAttr.set("seed", seed);
        }
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
            throw new IllegalArgumentException(ex);
        }
    }
}
