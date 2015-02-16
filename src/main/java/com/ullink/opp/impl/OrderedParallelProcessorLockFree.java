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

import java.util.concurrent.atomic.AtomicReferenceArray;

public class OrderedParallelProcessorLockFree implements OrderedParallelProcessor
{
    private final int           nSlots;
    private final int           mask;

    private static final Runnable TAIL = () -> {
        throw new AssertionError("Executing TAIL, not possible");
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

        rSlots.set(0, TAIL);
    }

    public OrderedParallelProcessorLockFree(int nSlot)
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
        // Check available slot
        long localTail = tail;
        while (seq >= (localTail + nSlots))
        {
            Thread.yield();
            localTail = tail;
        }

        int index = slotOf(seq);

        while (true)
        {
            if (rSlots.get(index) == TAIL)
            {
                // I'm alone on my slot I can go for it
                runProtected(seq, runnable);
                rSlots.set(index, null);

                // Look for more to process
                localTail = seq+1;
                index = slotOf(localTail);
                while (true)
                {
                    Runnable slot;
                    while ((slot = rSlots.get(index))!=null)
                    {
                        runProtected(localTail, slot);
                        rSlots.set(index, null);

                        // Move to next slot
                        index = slotOf(++localTail);
                    }

                    tail = localTail; // Wake up threads

                    // Synchronization point with competing threads on the slot
                    if (rSlots.compareAndSet(index, null, TAIL))
                    {
                        return true;
                    }
                }
            } else {
                if (rSlots.compareAndSet(index, null, runnable))
                {
                    return false;
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
