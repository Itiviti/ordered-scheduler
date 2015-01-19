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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderedParallelProcessor
{
    private final int           nSlots;
    private final int           mask;
    private Runnable[]          slots;

    private long                tail;

    private Lock                producerLock = new ReentrantLock();
    private Condition           condFull     = producerLock.newCondition();

    public OrderedParallelProcessor(int nSlot)
    {
        // Next power of 2
        nSlot = 1 << (32 - Integer.numberOfLeadingZeros(nSlot - 1));

        // Init
        this.nSlots = nSlot;
        this.mask = nSlot - 1;
        slots = new Runnable[nSlot];
        tail = 0;
    }

    private int slotOf(long i)
    {
        return (int) i & mask;
    }

    private boolean isSlotAvailable(long seq)
    {
        return (seq < tail + nSlots);
    }

    public void runSequentially(long seq, Runnable runnable)
    {
        while (true)
        {
            long localTail;

            producerLock.lock();
            try
            {
                // Check available slot
                while (!isSlotAvailable(seq))
                {
                    try
                    {
                        condFull.await();
                    }
                    catch (InterruptedException interrupt)
                    {
                        // TODO error management
                    }
                }

                // Capture tail
                localTail = tail;

                // Is it my turn?
                if (seq > localTail)
                {
                    slots[slotOf(seq)] = runnable;
                    return;
                }
            }
            finally
            {
                producerLock.unlock();
            }

            if (seq == localTail)
            {
                // I'm alone on my slot I can go for it
                runProtected(localTail, runnable);

                // Look for more to process
                int index = slotOf(++localTail);
                while (true)
                {
                    Runnable slot;
                    while ((slot = slots[index])!=null)
                    {
                        runProtected(localTail, slot);
                        slots[index] = null;

                        // Move to next slot
                        index = slotOf(++localTail);
                    }

                    // Synchronization point with competing threads on the slot
                    producerLock.lock();
                    try
                    {
                        if (slots[index] == null)
                        {
                            tail = localTail;
                            condFull.signalAll();
                            return;
                        }
                    }
                    finally
                    {
                        producerLock.unlock();
                    }
                }
            }
            else
            {
                throw new AssertionError("Duplicate sequence asked for processing - ignoring");
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
            //exceptionHandler(object, e);
        }
    }

}
