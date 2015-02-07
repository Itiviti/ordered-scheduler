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

import java.util.concurrent.atomic.AtomicReferenceArray;

public class OrderedParallelProcessorLockFree
{
    private final int           nSlots;
    private final int           mask;

    private static final Runnable LOCK = new Runnable() {
        @Override
        public void run() {

        }
    };

    private final AtomicReferenceArray<Runnable> rSlots;

    private final ExceptionHandler exceptionHandler;

    private volatile long       tail;

    public OrderedParallelProcessorLockFree(int nSlot, ExceptionHandler exceptionHandler)
    {
        // Next power of 2
        nSlot = 1 << (32 - Integer.numberOfLeadingZeros(nSlot - 1));

        // Init
        this.nSlots = nSlot;
        this.mask = nSlot - 1;
        rSlots = new AtomicReferenceArray<Runnable>(nSlots);
        tail = 0;
        this.exceptionHandler = exceptionHandler;
    }

    public OrderedParallelProcessorLockFree(int nSlot)
    {
        this(nSlot, new ExceptionHandler() {
            @Override
            public void handle(long seq, Runnable runnable, Throwable exception) {
                // By default, don't do anything
            }
        });
    }

    private int slotOf(long i)
    {
        return (int) i & mask;
    }

    /**
     *
     * @param seq
     * @param runnable
     * @return next sequence to be processed.
     */
    public long runSequentially(long seq, Runnable runnable)
    {
        // Check available slot
        while (seq >= (tail + nSlots))
        {
            Thread.yield();
        }

        while (true)
        {
            long localTail = tail;

            if (seq == localTail)
            {
                // I'm alone on my slot I can go for it
                runProtected(localTail, runnable);

                // Look for more to process
                int index = slotOf(++localTail);
                while (true)
                {
                    Runnable slot;
                    while ((slot = rSlots.get(index))!=null)
                    {
                        if (slot == LOCK)
                        {
                            continue;
                        }

                        runProtected(localTail, slot);
                        rSlots.lazySet(index, null);

                        // Move to next slot
                        index = slotOf(++localTail);
                    }

                    // Synchronization point with competing threads on the slot
                    if (rSlots.compareAndSet(index, null, LOCK))
                    {
                        tail = localTail;
                        rSlots.lazySet(index, null);
                        return localTail;
                    }
                }
            }
            else
            {
                int index = slotOf(seq);
                if (rSlots.compareAndSet(index, null, runnable))
                {
                    localTail = tail;
                    if (seq == localTail)
                    {
                        // set back to null
                        rSlots.lazySet(index, null);
                        // loop
                    }
                    else
                    {
                        return localTail;
                    }
                }
            }
        }
    }


    private void runProtected(long seq, Runnable runnable)
    {
        try
        {
            runnable.run();
        }
        catch (Throwable e)
        {
            try
            {
                exceptionHandler.handle(seq, runnable, e);
            }
            catch(Throwable ignored)
            {
                // Ignore exceptions in the ExceptionHandler
                // It's important that we move forward
            }
        }
    }

}
