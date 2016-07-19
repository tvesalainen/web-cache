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

import java.io.File;
import org.vesalainen.parsers.unit.parser.UnitParser;
import org.vesalainen.util.AbstractProvisioner.Setting;

/**
 *
 * @author tkv
 */
public class Config
{
    private static final UnitParser unitParser = UnitParser.getInstance();
    private static File cacheDir;
    private static long cacheMaxSize;
    private static int httpCachePort = 8080;
    private static int httpsCachePort = 8443;
    private static int httpsProxyPort = 8444;
    private static int refreshTimeout = 1000;
    private static int maxRestartCount = 10;
    private static int corePoolSize = 10;
    private static long restartInterval = 1000;
    private static long removalInterval = 1000000;
    private static int maxTransferSize = 4096;
    private static long timeoutAfterUserQuit;
    // tls
    private static File keystoreFile = new File("keystore");
    private static char[] keystorePassword = "sala".toCharArray();
    private static String caDN = "CN=Timo in the middle, C=FI";
    
    @Setting(value="cacheDir", mandatory=true)
    public static void setCacheDir(File cacheDir)
    {
        Config.cacheDir = cacheDir;
    }
    @Setting(value="cacheMaxSize", mandatory=true)
    public static void setCacheMaxSize(String maxSize)
    {
        Config.cacheMaxSize = (long) unitParser.parse(maxSize);
    }
    @Setting(value="restartInterval", mandatory=true)
    public static void setRestartInterval(String restartInterval)
    {
        Config.restartInterval = (int) unitParser.parse(restartInterval);
    }
    @Setting(value="removalInterval", mandatory=true)
    public static void setRemovalInterval(String removalInterval)
    {
        Config.removalInterval = (long) unitParser.parse(removalInterval);
    }
    @Setting(value="httpCachePort")
    public static void setHttpCachePort(int httpCachePort)
    {
        Config.httpCachePort = httpCachePort;
    }
    @Setting(value="httpsCachePort")
    public static void setHttpsCachePort(int httpsCachePort)
    {
        Config.httpsCachePort = httpsCachePort;
    }
    @Setting(value="httpsProxyPort")
    public static void setHttpsProxyPort(int httpsProxyPort)
    {
        Config.httpsProxyPort = httpsProxyPort;
    }
    @Setting(value="freshTimeout")
    public static void setRefreshTimeout(int refreshTimeout)
    {
        Config.refreshTimeout = refreshTimeout;
    }
    @Setting(value="maxRestartCount")
    public static void setMaxRestartCount(int maxRestartCount)
    {
        Config.maxRestartCount = maxRestartCount;
    }
    @Setting(value="corePoolSize")
    public static void setCorePoolSize(int corePoolSize)
    {
        Config.corePoolSize = corePoolSize;
    }
    @Setting(value="maxTransferSize")
    public static void setMaxTransferSize(int maxTransferSize)
    {
        Config.maxTransferSize = maxTransferSize;
    }
    @Setting(value="timeoutAfterUserQuit")
    public static void setTimeoutAfterUserQuit(String timeoutAfterUserQuit)
    {
        Config.timeoutAfterUserQuit = (long) unitParser.parse(timeoutAfterUserQuit);
    }

    public static UnitParser getUnitParser()
    {
        return unitParser;
    }

    public static File getCacheDir()
    {
        return cacheDir;
    }

    public static long getCacheMaxSize()
    {
        return cacheMaxSize;
    }

    public static int getHttpCachePort()
    {
        return httpCachePort;
    }

    public static int getHttpsCachePort()
    {
        return httpsCachePort;
    }

    public static int getHttpsProxyPort()
    {
        return httpsProxyPort;
    }

    public static int getRefreshTimeout()
    {
        return refreshTimeout;
    }

    public static int getMaxRestartCount()
    {
        return maxRestartCount;
    }

    public static int getCorePoolSize()
    {
        return corePoolSize;
    }

    public static long getRestartInterval()
    {
        return restartInterval;
    }

    public static long getRemovalInterval()
    {
        return removalInterval;
    }

    public static int getMaxTransferSize()
    {
        return maxTransferSize;
    }

    public static long getTimeoutAfterUserQuit()
    {
        return timeoutAfterUserQuit;
    }

    public static File getKeystoreFile()
    {
        return keystoreFile;
    }

    public static char[] getKeystorePassword()
    {
        return keystorePassword;
    }

    public static String getCaDN()
    {
        return caDN;
    }
}
