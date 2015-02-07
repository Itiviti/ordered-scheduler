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

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class OrderedParallelProcessorLockFreeUnsafe
{
    private final int           nSlots;
    private final long          mask;

    private static final Runnable LOCK = new Runnable() {
        @Override
        public void run() {

        }
    };

    private final Runnable[] array; // must have exact type Object[]
    private static final Unsafe unsafe;
    private static final int base;
    private static final int shift;
    static {
        try {
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            base = unsafe.arrayBaseOffset(Runnable[].class);
            int scale = unsafe.arrayIndexScale(Runnable[].class);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            shift = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private final ExceptionHandler exceptionHandler;

    private volatile long       tail;

    public OrderedParallelProcessorLockFreeUnsafe(int nSlot, ExceptionHandler exceptionHandler)
    {
        // Next power of 2
        nSlot = 1 << (32 - Integer.numberOfLeadingZeros(nSlot - 1));

        // Init
        this.nSlots = nSlot;
        this.mask = nSlot - 1;
        this.array = new Runnable[nSlots];
        this.tail = 0;
        this.exceptionHandler = exceptionHandler;
    }

    public OrderedParallelProcessorLockFreeUnsafe(int nSlot)
    {
        this(nSlot, new ExceptionHandler() {
            @Override
            public void handle(long seq, Runnable runnable, Throwable exception) {
                // By default, don't do anything
            }
        });
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
                long offset = byteOffsetOf(++localTail);
                while (true)
                {
                    Runnable slot;
                    while ((slot = get(offset))!=null)
                    {
                        if (slot == LOCK)
                        {
                            continue;
                        }

                        runProtected(localTail, slot);
                        set(offset, null);

                        // Move to next slot
                        offset = byteOffsetOf(++localTail);
                    }

                    // Synchronization point with competing threads on the slot
                    if (compareAndSet(offset, null, LOCK))
                    {
                        tail = localTail;
                        setOrdered(offset, null);
                        return localTail;
                    }
                }
            }
            else
            {
                long offset = byteOffsetOf(seq);
                if (compareAndSet(offset, null, runnable))
                {
                    localTail = tail;
                    if (seq == localTail)
                    {
                        // set back to null
                        set(offset, null);
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

    private long byteOffsetOf(long i)
    {
        return ((i & mask) << shift) + base;
    }

    private Runnable get(long offset)
    {
        return (Runnable) unsafe.getObject(array, offset);
    }

    private void set(long offset, Runnable o)
    {
        unsafe.putObject(array, offset, o);
    }

    private void setOrdered(long offset, Runnable o)
    {
        unsafe.putOrderedObject(array, offset, o);
    }

    private boolean compareAndSet(long offset, Runnable expect, Runnable update)
    {
        return unsafe.compareAndSwapObject(array, offset, expect, update);
    }

}

