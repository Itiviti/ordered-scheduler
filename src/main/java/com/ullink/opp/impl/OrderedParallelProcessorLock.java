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

package com.ullink.opp.impl;

import com.ullink.opp.ExceptionHandler;
import com.ullink.opp.OrderedParallelProcessor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderedParallelProcessorLock implements OrderedParallelProcessor
{
    private final int                 nSlots;
    private final int                 mask;
    private final Runnable[]          slots;

    private final ExceptionHandler exceptionHandler;

    private volatile long             tail;

    private final Lock                producerLock = new ReentrantLock();
    private final Condition           condFull     = producerLock.newCondition();

    public OrderedParallelProcessorLock(int nSlot, ExceptionHandler exceptionHandler)
    {
        // Next power of 2
        nSlot = 1 << (32 - Integer.numberOfLeadingZeros(nSlot - 1));

        // Init
        this.nSlots = nSlot;
        this.mask = nSlot - 1;
        slots = new Runnable[nSlot];
        tail = 0;
        this.exceptionHandler = exceptionHandler;
    }

    public OrderedParallelProcessorLock(int nSlot)
    {
        this(nSlot, (s,r,e) -> {});
    }

    private int slotOf(long i)
    {
        return (int) i & mask;
    }

    @Override
    public boolean runSequentially(long seq, Runnable runnable)
    {
        long localTail = tail;

        while (true)
        {
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
                            return true;
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
                producerLock.lock();
                try
                {
                    // Check available slot
                    while (seq >= (tail + nSlots))
                    {
                        try {
                            condFull.await();
                        } catch (InterruptedException interrupt) {
                            // TODO Interrupt error management
                        }
                    }

                    localTail = tail;

                    if (seq == localTail)
                    {
                        // Loop again
                    }
                    else if (seq > localTail)
                    {
                        slots[slotOf(seq)] = runnable;
                        return false;
                    }
                    else
                    {
                        throw new AssertionError("Can't process ticket twice");
                    }
                }
                finally
                {
                    producerLock.unlock();
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
