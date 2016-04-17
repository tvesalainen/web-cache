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

import static java.lang.Thread.MIN_PRIORITY;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class DNS extends JavaLogging implements Runnable
{
    private static final InetAddress[] Empty = new InetAddress[0];
    private static final Map<String,InetAddress[]> map = new ConcurrentHashMap<>();
    private Thread thread;

    public DNS()
    {
        super(DNS.class);
    }
    
    public void start()
    {
        thread = new Thread(this, DNS.class.getSimpleName());
        thread.setPriority(MIN_PRIORITY);
        thread.start();
    }
    
    public void stop()
    {
        thread.interrupt();
    }
    
    public static InetAddress[] getAddresses(String host)
    {
        InetAddress[] array = map.get(host);
        int index = 0;
        while (index < 10 && (array == null || array.length == 0))
        {
            try
            {
                array = InetAddress.getAllByName(host);
                if (array.length > 0)
                {
                    map.put(host, array);
                }
            }
            catch (UnknownHostException ex)
            {
            }
            index++;
        }
        if (array == null)
        {
            map.put(host, Empty);
            return Empty;
        }
        return array;
    }
    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                for (String host : map.keySet())
                {
                    try
                    {
                        InetAddress[] old = map.get(host);
                        InetAddress[] arr = InetAddress.getAllByName(host);
                        if (!Arrays.equals(old, arr))
                        {
                            map.replace(host, arr);
                            fine("%s -> %s", old, arr);
                        }
                    }
                    catch (UnknownHostException ex)
                    {
                    }
                }
                Thread.sleep(1000);
            }
            catch (InterruptedException ex)
            {
                return;
            }
        }
    }
    
}
