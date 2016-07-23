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

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.vesalainen.util.LongMap;

/**
 *
 * @author tkv
 */
public class FileLastAccessStatistics implements Consumer<BasicFileAttributes>
{
    private long granularity;
    private LongMap<Long> map = new LongMap<>(new TreeMap<>());
    private long sum;
    private long count;
    private long max = Long.MIN_VALUE;
    private long min = Long.MAX_VALUE;

    public FileLastAccessStatistics(long cranularity)
    {
        this.granularity = cranularity;
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
        long millis = lastAccessTime.toMillis();
        long key = (millis / granularity) * granularity;
        add(key, size);
    }
    private void add(long key, long size)
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
        stats.map.entrySet().stream().forEach((e) ->
        {
            add(e.getKey(), e.getValue().getValue());
        });
    }

    public long getAverage()
    {
        return sum / count;
    }
    
    public LongMap<Long> getMap()
    {
        return map;
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
