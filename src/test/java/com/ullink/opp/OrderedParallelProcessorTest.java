package com.ullink.opp;

import com.ullink.opp.impl.OrderedParallelProcessorLock;
import com.ullink.opp.impl.OrderedParallelProcessorLockFree;
import com.ullink.opp.impl.OrderedParallelProcessorLockFreeUnsafe;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;

public class OrderedParallelProcessorTest
{
    private final static int SIZE = 2000000;
    private ExecutorService x;
    private OrderedParallelProcessor opp;

    private AtomicLong nextTicket;
    private long expectedTicket;
    private long failures;

    @Before
    public void setup()
    {
        // Create Executor to simulate multiple thread processing
        x = new ThreadPoolExecutor(10, 10, 1, TimeUnit.DAYS, new ArrayBlockingQueue(SIZE));

        // Create Ordered Parallel Processor with 1024 slots
        //opp = new OrderedParallelProcessorLock(1024);
        //opp = new OrderedParallelProcessorLockFree(1024);
        opp = new OrderedParallelProcessorLockFreeUnsafe(1024);

        nextTicket = new AtomicLong(0);
        expectedTicket = 0;
        failures = 0;
    }

    @Test
    public void testSample() throws Exception
    {
        fireProcessingThreads(this::execution);
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
    private void execution()
    {
        // Get ticket for ordering
        final long ticket = nextTicket.getAndIncrement();

        // Some processing - executed in parallel
        someProcessing();

        opp.runSequentially(ticket, () -> {
            if (ticket == expectedTicket) {
                // Good order
                expectedTicket++;
            } else {
                // Wrong order
                failures++;
            }
        });
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
