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
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.vesalainen.util.LongMap;
import org.vesalainen.util.LongReference;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class FileLastAccessStatistics implements Consumer<BasicFileAttributes>
{
    private long interval;
    private TreeMap<Long,LongReference> creationTreeMap;
    private LongMap<Long> creationMap;
    private TreeMap<Long,LongReference> accessTreeMap;
    private LongMap<Long> accessMap;
    private long sum;
    private long count;
    private long max = Long.MIN_VALUE;
    private long min = Long.MAX_VALUE;

    public FileLastAccessStatistics(long interval, TimeUnit unit)
    {
        this.interval = unit.toMillis(interval);
        this.creationTreeMap = new TreeMap<>();
        this.creationMap = new LongMap<>(creationTreeMap);
        this.accessTreeMap = new TreeMap<>();
        this.accessMap = new LongMap<>(accessTreeMap);
    }
    
    public static FileLastAccessStatistics getStats(Path path, long interval, TimeUnit unit) throws IOException
    {
        return attributeStream(path)
        .collect(()->{return new FileLastAccessStatistics(interval, unit);},
            FileLastAccessStatistics::accept,
            FileLastAccessStatistics::combine
        );
    }
    public static Stream<BasicFileAttributes> attributeStream(Path dir) throws IOException
    {
        return regularPaths(dir)
        .map((Path p)->
        {
            try
            {
                return Files.getFileAttributeView(p, BasicFileAttributeView.class).readAttributes();
            }
            catch (IOException ex)
            {
                throw new IllegalArgumentException(ex);
            }
        });
    }
    public static Stream<Path> regularPaths(Path dir) throws IOException
    {
        return Files.find(dir, Integer.MAX_VALUE, (Path p, BasicFileAttributes b) ->
        {
            return b.isRegularFile();
        });
    }
    @Override
    public void accept(BasicFileAttributes attrs)
    {
        long size = attrs.size();
        count++;
        sum += size;
        max = Math.max(max, size);
        min = Math.min(min, size);
        FileTime lastAccessTime = attrs.lastAccessTime();
        long accessMillis = lastAccessTime.toMillis();
        long accessKey = (accessMillis / interval) * interval;
        add(accessMap, accessKey, size);
        FileTime creationTime = attrs.creationTime();
        long creationMillis = creationTime.toMillis();
        long creationKey = (creationMillis / interval) * interval;
        add(creationMap, creationKey, size);
    }
    private void add(LongMap<Long> map, long key, long size)
    {
        if (map.containsKey(key))
        {
            map.put(key, map.getLong(key) + size);
        }
        else
        {
            map.put(key, size);
        }
    }
    public void combine(FileLastAccessStatistics stats)
    {
        count += stats.count;
        sum += stats.sum;
        max = Math.max(max, stats.max);
        min = Math.min(min, stats.min);
        stats.creationMap.entrySet().stream().forEach((e) ->
        {
            add(creationMap, e.getKey(), e.getValue().getValue());
        });
        stats.accessMap.entrySet().stream().forEach((e) ->
        {
            add(accessMap, e.getKey(), e.getValue().getValue());
        });
    }
    /**
     * Returns growth speed in bytes / milliseconds
     * @return 
     */
    public double growthSpeed()
    {
        NavigableSet<Long> keySet = accessTreeMap.navigableKeySet();
        long first = keySet.first();
        long last = keySet.last();
        double delta = last - first;
        return (double)sum / delta;
    }
    /**
     * Return millis from epoch where files older than will sum up to current
     * growth in interval.
     * @return 
     */
    public long intervalDeletePoint()
    {
        NavigableSet<Long> accessKeySet = accessTreeMap.navigableKeySet();
        NavigableSet<Long> creationKeySet = creationTreeMap.navigableKeySet();
        long needsDelete = rate(creationKeySet.descendingIterator());
        for (Long time : accessKeySet)
        {
            long size = accessMap.getLong(time);
            needsDelete -= size;
            if (needsDelete <= 0)
            {
                return time + interval;
            }
        }
        long first = accessKeySet.first();
        return first + interval;
    }
    private long rate(Iterator<Long> it)
    {
        long res = 0;
        for (int ii=0;ii<2;ii++)
        {
            if (it.hasNext())
            {
                res = Math.max(res, accessMap.getLong(it.next()));
            }
        }
        return res;
    }
    public long getFirst()
    {
        NavigableSet<Long> keySet = accessTreeMap.navigableKeySet();
        return keySet.first();
    }
    public long getLast()
    {
        NavigableSet<Long> keySet = accessTreeMap.navigableKeySet();
        return keySet.last();
    }
    public long getAverage()
    {
        return sum / count;
    }
    
    public LongMap<Long> getAccessMap()
    {
        return accessMap;
    }

    public long getSum()
    {
        return sum;
    }

    public long getCount()
    {
        return count;
    }

    public long getMax()
    {
        return max;
    }

    public long getMin()
    {
        return min;
    }
    
}
