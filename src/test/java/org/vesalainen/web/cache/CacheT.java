/*
 * Copyright (C) 2016 Timo Vesalainen <timo.vesalainen@iki.fi>
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.Test;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class CacheT
{
    @Test
    public void server()
    {
        try
        {
            
            File dir = new File("c:\\temp\\cache");
            if (false && dir.exists())
            {
                Path path = dir.toPath();
                Stream<Path> stream = Files.walk(path, 3);
                stream.forEach((Path p)->p.toFile().delete());
            }
            Main.main("-ll", "FINEST", "-pl", "FINEST", "src\\test\\resources\\web-cache.xml");
        }
        catch (IOException ex)
        {
            Logger.getLogger(CacheT.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
