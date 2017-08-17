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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import static java.util.logging.Level.FINEST;
import org.vesalainen.lang.Primitives;
import org.vesalainen.time.SimpleMutableDateTime;
import org.vesalainen.util.LongMap;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
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
            FileLastAccessStatistics stats = FileLastAccessStatistics.getStats(path, Config.getRemovalInterval(), TimeUnit.MILLISECONDS);
            long cacheSize = stats.getSum();
            double growthSpeed = stats.growthSpeed();
            fine("cache size %dM / %dM %d%% in use. Max size %d average %d count %d growth speed %f B/ms", 
                    cacheSize/Mega, 
                    cacheMaxSize/Mega,
                    100*cacheSize/cacheMaxSize,
                    stats.getMax(),
                    (long)stats.getAverage(),
                    stats.getCount(),
                    growthSpeed
            );
            if (isLoggable(FINEST))
            {
                LongMap<Long> map = stats.getAccessMap();
                map.keySet().stream().forEach((key) ->
                {
                    SimpleMutableDateTime dt = SimpleMutableDateTime.ofEpochMilli(key);
                    finest("%s %d", dt, map.getLong(key));
                });
            }            
            if (Double.isFinite(growthSpeed))
            {
                long freeSpace = cacheMaxSize - cacheSize;
                long estimatedFullMillis = (long) ((double)freeSpace / growthSpeed);
                SimpleMutableDateTime estimatedFullDateTime = SimpleMutableDateTime.ofEpochMilli(estimatedFullMillis + System.currentTimeMillis());
                fine("estimated cache overflow at %s", estimatedFullDateTime);
                long intervalDeletePoint = stats.intervalDeletePoint();
                SimpleMutableDateTime intervalDeleteDateTime = SimpleMutableDateTime.ofEpochMilli(intervalDeletePoint);
                fine("delete point at %s", intervalDeleteDateTime);
                SimpleMutableDateTime firstSample = SimpleMutableDateTime.ofEpochMilli(stats.getFirst());
                SimpleMutableDateTime lastSample = SimpleMutableDateTime.ofEpochMilli(stats.getLast());
                fine("samples %s - %s", firstSample, lastSample);
                if (estimatedFullMillis < Config.getRemovalInterval())
                {
                    removeFiles(path, intervalDeletePoint);
                }
                long nextCheckPointDelta = estimatedFullMillis / 2;
                SimpleMutableDateTime nextCheckPointDateTime = SimpleMutableDateTime.ofEpochMilli(nextCheckPointDelta + System.currentTimeMillis());
                fine("next check at %s", nextCheckPointDateTime);
                Cache.getScheduler().schedule(this, nextCheckPointDelta, TimeUnit.MILLISECONDS);
            }
            else
            {
                SimpleMutableDateTime nextCheckPointDateTime = SimpleMutableDateTime.ofEpochMilli(Config.getRemovalInterval() + System.currentTimeMillis());
                fine("next check at %s", nextCheckPointDateTime);
                Cache.getScheduler().schedule(this, Config.getRemovalInterval(), TimeUnit.MILLISECONDS);
            }
        }
        catch (Exception ex)
        {
            log(DEBUG, ex, "Remover: %s", ex.getMessage());
        }
    }

    private void removeFiles(Path path, long intervalDeletePoint)
    {
        try
        {
            Files.find(path, Integer.MAX_VALUE, (Path p, BasicFileAttributes b) ->
            {
                FileTime ft = b.lastAccessTime();
                return 
                        !p.toString().endsWith(".atr") &&
                        b.isRegularFile() &&
                        ft.toMillis() < intervalDeletePoint
                        ;
            }).forEach((Path p) ->
            {
                fine("enqueued for deletion %s", p);
                Cache.queueDelete(p);
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
