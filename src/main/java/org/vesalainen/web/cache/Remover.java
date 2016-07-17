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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.LongSummaryStatistics;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.vesalainen.lang.Primitives;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class Remover extends JavaLogging implements Runnable
{
    private static final long Mega = 1048576;
    public Remover()
    {
        super(Remover.class);
    }

    @Override
    public void run()
    {
        config("started Remover");
        try
        {
            long cacheMaxSize = Config.getCacheMaxSize();
            Path path = Config.getCacheDir().toPath();
            LongSummaryStatistics stats = getCacheSize(path);
            long cacheSize = stats.getSum();
            fine("cache size %dM / %dM %d%% in use. Max size %d average %d count %d", 
                    cacheSize/Mega, 
                    cacheMaxSize/Mega,
                    100*cacheSize/cacheMaxSize,
                    stats.getMax(),
                    (long)stats.getAverage(),
                    stats.getCount()
            );
            if (cacheSize > Config.getCacheMaxSize())
            {
                removeFiles(path);
            }
            
        }
        catch (Exception ex)
        {
            log(DEBUG, ex, "Remover: %s", ex.getMessage());
        }
    }

    private LongSummaryStatistics getCacheSize(Path path) throws IOException
    {
        return Files.find(path, Integer.MAX_VALUE, (Path p, BasicFileAttributes b) ->
        {
            return b.isRegularFile();
        }).collect(Collectors.summarizingLong((Path p) ->
        {
            try
            {
                long s = (long) Files.getAttribute(p, "size");
                return s;
            }
            catch (IOException ex)
            {
                throw new IllegalArgumentException(ex);
            }
        }));
        
    }
    
    private void removeFiles(Path path)
    {
        try
        {
            Files.find(path, Integer.MAX_VALUE, (Path p, BasicFileAttributes b) ->
            {
                return b.isRegularFile();
            }).map((Path p) ->
            {
                try
                {
                    FileTime ft = (FileTime) Files.getAttribute(p, "lastAccessTime");
                    Long s = (Long) Files.getAttribute(p, "size");
                    finest("%s lastAccess %s size %d", p, ft, s);
                    return new FileEntry(p, ft.toMillis(), s);
                }
                catch (IOException ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }).sorted().filter(new SizeFilter(Config.getCacheMaxSize())).forEach((FileEntry t) ->
            {
                Cache.queueDelete(path);
                fine("enqueued for deletion %s", path);
            });
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, ex, "%s", ex.getMessage());
        }
    }

    private class SizeFilter implements Predicate<FileEntry>
    {
        private long cacheMaxSize;
        private long sum;

        public SizeFilter(long cacheMaxSize)
        {
            this.cacheMaxSize = cacheMaxSize;
        }
        
        @Override
        public boolean test(FileEntry t)
        {
            sum += t.size;
            finest("Cache sum %d/%d %s", sum, cacheMaxSize, t);
            return sum >= cacheMaxSize;
        }
        
    }
    private static class FileEntry implements Comparable<FileEntry>
    {
        private Path path;
        private long time;
        private long size;

        public FileEntry(Path path, long time, long size)
        {
            this.path = path;
            this.time = time;
            this.size = size;
        }

        @Override
        public int compareTo(FileEntry o)
        {
            return Primitives.signum(o.time - time);
        }

        @Override
        public String toString()
        {
            return "FileEntry{" + "path=" + path + ", time=" + time + ", size=" + size + '}';
        }

    }
}
