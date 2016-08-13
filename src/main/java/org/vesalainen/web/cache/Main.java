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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import org.vesalainen.util.JAXBCommandLine;

/**
 *
 * @author tkv
 */
public class Main extends JAXBCommandLine
{
    private static Cache cache;
    private static Config config;

    public Main()
    {
        super("org.vesalainen.web.cache.jaxb.cache", true);
        addOption("-dontWait", "don't wait (for testing)", null, false);
    }
    
    public static void main(String... args)
    {
        Main cmdLine = new Main();
        cmdLine.command(args);
        try
        {
            cache = new Cache();
            cmdLine.attachStatic(Config.class);
            cmdLine.checkMandatory();
            boolean dontWait = cmdLine.getOption("-dontWait");
            if (dontWait)
            {
                cache.start();
            }
            else
            {
                cache.startAndWait();
            }
        }
        catch (IOException | InterruptedException | ExecutionException ex)
        {
            ex.printStackTrace();
        }
    }
    public static void stop()
    {
        Cache.stop();
    }
}
