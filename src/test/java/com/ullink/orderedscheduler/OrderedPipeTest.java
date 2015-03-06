/*
 * Copyright 2015 ULLINK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ullink.orderedscheduler;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class OrderedPipeTest implements Runnable
{
    private final static int SIZE = 2000000;
    private ExecutorService x;
    private OrderedScheduler os;
    private OrderedPipe pipe;

    private long expectedTicket;
    private long failures;

    @Before
    public void setup()
    {
        // Create Executor to simulate multiple thread processing
        x = new ThreadPoolExecutor(10, 10, 1, TimeUnit.DAYS, new ArrayBlockingQueue(SIZE));

        // Create Ordered Pipe with 1024 slots
        os = new OrderedScheduler();
        pipe = os.createPipe(1024);

        expectedTicket = 0;
        failures = 0;
    }

    @Test
    public void testSample() throws Exception
    {
        fireProcessingThreads(this);
        waitEndOfProcessing();
        assertTrue(failures == 0);
    }

    @Test
    public void testBench() throws Exception
    {
        for(int i=0; i<10; i++)
        {
            setup();
            long startTime = System.nanoTime();
            testSample();
            long time = System.nanoTime()-startTime;
            System.out.println("" + i + " " + time / 1000 / 1000 + "ms");
        }
    }

    /**
     * Method called by the executor with multiple threads
     */
    public void run()
    {
        Ticket t;

        synchronized(this)
        {
            t = os.getNextTicket();
        }

        try (Ticket i = t)
        {
            // Some processing - executed in parallel
            someProcessing();

            pipe.run(i, new Runnable() {
                @Override
                public void run() {
                    if (i.seq == expectedTicket) {
                        // Good order
                        expectedTicket++;
                    } else {
                        // Wrong order
                        failures++;
                    }
                }
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void someProcessing()
    {
        // Nothing here
    }

    private void fireProcessingThreads(Runnable r)
    {
        for(long i=0; i<SIZE; i++)
        {
            x.execute(r);
        }
    }

    private void waitEndOfProcessing() throws Exception
    {
        x.shutdown();
        x.awaitTermination(1, TimeUnit.MINUTES);
    }


}
