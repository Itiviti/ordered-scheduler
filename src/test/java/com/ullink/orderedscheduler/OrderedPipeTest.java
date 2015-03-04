package com.ullink.orderedscheduler;

import org.junit.Before;
import org.junit.Test;

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
        final Ticket t = os.getNextTicketAtomic();

        // Some processing - executed in parallel
        someProcessing();

        pipe.run(t, new Runnable() {
            @Override
            public void run() {
                if (t.seq == expectedTicket) {
                    // Good order
                    expectedTicket++;
                } else {
                    // Wrong order
                    failures++;
                }
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
