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

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class OrderedParallelProcessorLowLevelLockFreeTest
{
    public static long count = 0;
    public static volatile long failures = 0;

    @Test
    public void testExecute() throws Exception
    {
        for(int i=0; i<10; i++) {
            long time = bench();
            System.out.println(i + ": " + (time / 1000 / 1000) + "ms");
        }
    }

    public long bench() throws Exception {
        count = 0;

        final OrderedParallelProcessorLowLevelLockFree seqx = new OrderedParallelProcessorLowLevelLockFree(1024);

        final long LOOPS = 2*1000*1000;
        ExecutorService x = new ThreadPoolExecutor(10, 10, 1, TimeUnit.DAYS, new ArrayBlockingQueue<Runnable>((int) LOOPS));

        long start = System.nanoTime();
        for (long i = 0; i < LOOPS; i++)
        {
            final long n = i;
            x.execute(new Runnable() {
                @Override
                public void run() {
                        seqx.runSequentially(n, new Runnable() {
                            @Override
                            public void run() {
                                if (count == n) {
                                    //System.out.println(n);
                                    count++;
                                } else {
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
}