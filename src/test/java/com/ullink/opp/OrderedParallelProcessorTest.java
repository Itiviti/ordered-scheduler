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

package com.ullink.opp;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class OrderedParallelProcessorTest
{
    public static long count = 0;
    public static long failures = 0;

    @Test
    public void testExecute() throws Exception
    {
        bench();
    }

    public long bench() throws Exception
    {
        count = 0;

        final OrderedParallelProcessor seqx = new OrderedParallelProcessor(1024);

        final long LOOPS = 2 * 1000 * 1000;
        ExecutorService x = new ThreadPoolExecutor(6, 6, 1, TimeUnit.DAYS, new ArrayBlockingQueue<Runnable>((int)LOOPS));

        long start = System.nanoTime();
        for (long i = 0; i < LOOPS; i++)
        {
            final long n = i;
            x.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    seqx.runSequentially(n, new Runnable() {
                        @Override
                        public void run() {
                            if (count == n)
                            {
                                count++;
                            }
                            else
                            {
                                failures++;
                            }
                        }
                    });
                }
            });
        }

        x.shutdown();
        x.awaitTermination(1, TimeUnit.MINUTES);

        assertTrue(failures == 0);

        return System.nanoTime() - start;
    }

    public static void main(String[] args) throws Exception {
        OrderedParallelProcessorTest test = new OrderedParallelProcessorTest();
        long[] results = new long[20];
        for (int i = 0; i < results.length; i++) {
            results[i] = test.bench();
            System.out.println(i + ": " + (results[i] / 1000 / 1000) + "ms");
            Thread.sleep(1000);
        }
        // Average last n
        int last = 10;
        long sum = 0;
        for (int i = results.length - last; i < results.length; i++) {
            sum += results[i];
        }
        System.out.println("Average last " + last + ": " + ((sum / last) / 1000 / 1000) + "ms");
    }

}