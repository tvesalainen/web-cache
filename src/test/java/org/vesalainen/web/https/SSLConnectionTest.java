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
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.Security;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import static org.junit.Assert.*;
import org.vesalainen.net.ssl.SSLServerSocketChannel;
import org.vesalainen.net.ssl.SSLSocketChannel;
import org.vesalainen.nio.channels.vc.ByteChannelVirtualCircuit;
import org.vesalainen.nio.channels.vc.SelectableVirtualCircuit;
import org.vesalainen.nio.channels.vc.VirtualCircuit;
import org.vesalainen.util.HexDump;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author tkv
 */
public class SSLConnectionTest
{
    private static SSLContext sslCtx;
    public SSLConnectionTest() throws IOException
    {
        JavaLogging.setConsoleHandler("org.vesalainen", Level.FINEST);
        Security.addProvider(new BouncyCastleProvider());
        sslCtx = TestSSLContext.getInstance();
    }

    @Test
    public void testByteChannel() throws IOException, InterruptedException, ExecutionException
    {
        ExecutorService executor = Executors.newCachedThreadPool();
        ByteBuffer bb = ByteBuffer.allocate(2048);
        
        Server sa1 = new Server();
        Future<SSLSocketChannel> f1 = executor.submit(sa1);
        SSLSocketChannel sc11 = SSLSocketChannel.open("localhost", sa1.getPort(), sslCtx);
        
        byte[] exp = new byte[1024];
        Random random = new Random(98765);
        random.nextBytes(exp);
        bb.put(exp);
        bb.flip();
        sc11.write(bb);
        bb.clear();
        int rc = sc11.read(bb);
        assertEquals(1024, rc);
        byte[] array = bb.array();
        byte[] got = Arrays.copyOf(array, 1024);
        assertArrayEquals(exp, got);
        sc11.close();
        executor.shutdownNow();
    }
    private static class Server implements Callable<SSLSocketChannel>
    {
        private final SSLServerSocketChannel ssc;

        public Server() throws IOException
        {
            ssc = SSLServerSocketChannel.open(null, sslCtx);
            ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }

        public int getPort() throws IOException
        {
            InetSocketAddress local = (InetSocketAddress) ssc.getLocalAddress();
            return local.getPort();
        }
        
        @Override
        public SSLSocketChannel call() throws Exception
        {
            SSLSocketChannel sc = ssc.accept();
            ByteBuffer bb = ByteBuffer.allocate(2048);
            sc.read(bb);
            System.err.println(HexDump.toHex(bb.array(), 0, bb.position()));
            bb.flip();
            sc.write(bb);
            bb.clear();
            sc.read(bb);
            return null;
        }
        
    }
    private static class Echo implements Callable<Void>
    {
        private ByteChannel rc;
        private ByteBuffer bb = ByteBuffer.allocate(2048);

        public Echo(ByteChannel rc)
        {
            this.rc = rc;
        }

        @Override
        public Void call() throws Exception
        {
            try
            {
                while (true)
                {
                    bb.clear();
                    rc.read(bb);
                    bb.flip();
                    rc.write(bb);
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
            return null;
        }
    }
}
