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
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author tkv
 */
public class KeyStoreManagerTest
{
    
    public KeyStoreManagerTest()
    {
    }

    @BeforeClass
    public static void init()
    {
        Security.addProvider(new BouncyCastleProvider());
    }
    @Test
    public void test1() throws IOException
    {
        KeyStoreManager ksm =  new KeyStoreManager(new File("c:\\temp\\testStore"));
        ksm.store();
        ksm =  new KeyStoreManager(new File("c:\\temp\\testStore"));
    }
    
}
