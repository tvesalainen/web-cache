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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.concurrent.locks.ReentrantLock;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author tkv
 */
public class KeyStoreManagerTest
{
    private Path dir;
    @Before
    public void before() throws IOException
    {
        Security.addProvider(new BouncyCastleProvider());
        dir = Files.createTempDirectory("test");
    }

    @After
    public void after() throws IOException
    {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir))
        {
            ds.forEach((p)->
            {
                try
                {
                    Files.delete(p);
                }
                catch (IOException ex)
                {
                    throw new RuntimeException(ex);
                }
            });
        }
        Files.delete(dir);
    }

    @Test
    public void test1()
    {
        Path path = dir.resolve("keystore");
        ReentrantLock lock = new ReentrantLock();
        KeyStoreManager ksm = new KeyStoreManager(path.toFile(), lock);
        ksm = new KeyStoreManager(path.toFile(), lock);
    }
    
}
