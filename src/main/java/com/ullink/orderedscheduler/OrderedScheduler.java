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

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class OrderedScheduler
{
    private final int           nSlots;
    private final long          mask;

    private volatile long       tail;
    private static final Runnable TAIL = new Runnable() {
        @Override
        public void run() {
            throw new AssertionError("Executing TAIL, not possible");
        }
    };
    private static final Runnable EMPTY = new Runnable() {
        @Override
        public void run() {}
    };

    private final Runnable[] array;
    private static final int base;
    private static final int shift;
    private static final Unsafe unsafe;
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
    private static final ExceptionHandler NO_EXCEPTION_HANDLER = new ExceptionHandler() {
        @Override
        public void handle(Runnable runnable, Throwable exception) {}
    };

    public OrderedScheduler(int nSlot, ExceptionHandler exceptionHandler)
    {
        // Next power of 2
        nSlot = 1 << (32 - Integer.numberOfLeadingZeros(nSlot - 1));

        // Init
        this.nSlots = nSlot;
        this.mask = nSlot - 1;
        this.array = new Runnable[nSlots];
        this.tail = 0;
        this.exceptionHandler = exceptionHandler;

        this.array[0] = TAIL;
    }

    public OrderedScheduler(int nSlot)
    {
        this(nSlot, NO_EXCEPTION_HANDLER);
    }

    public void trash(long seq)
    {
        run(seq, EMPTY);
    }

    public boolean run(long seq, Runnable runnable)
    {
        long localTail = tail;

        // Check duplicate sequence
        //if (seq < localTail)
        //{
        //    throw new IllegalArgumentException("Duplicate ticket requested for processing");
        //}

        // Check available slot
        while (seq >= (localTail + nSlots))
        {
            Thread.yield();
            localTail = tail;
        }

        long offset = byteOffsetOf(seq);

        while (true)
        {
            if (getVolatile(offset) == TAIL)
            {
                // I'm alone on my slot I can go for it
                runProtected(runnable);
                set(offset, null);

                // Look for more to process
                localTail = seq+1;
                offset = byteOffsetOf(localTail);
                while (true)
                {
                    Runnable slot;
                    while ((slot = getVolatile(offset))!=null)
                    {
                        runProtected(slot);
                        set(offset, null);

                        // Move to next slot
                        offset = byteOffsetOf(++localTail);
                    }

                    tail = localTail; // Wake up threads

                    // Synchronization point with competing threads on the slot
                    if (compareAndSet(offset, null, TAIL))
                    {
                        return true;
                    }
                }
            }
            else
            {
                if (compareAndSet(offset, null, runnable))
                {
                    return false;
                }
            }
        }
    }


    private void runProtected(Runnable runnable)
    {
        try
        {
            runnable.run();
        }
        catch (Throwable e)
        {
            try
            {
                exceptionHandler.handle(runnable, e);
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

    private Runnable getVolatile(long offset)
    {
        return (Runnable) unsafe.getObjectVolatile(array, offset);
    }

    //private void setVolatile(long offset, Runnable o) {
    //    unsafe.putObjectVolatile(array, offset, o);
    //}

    //private Runnable get(long offset)
    //{
    //    return (Runnable) unsafe.getObject(array, offset);
    //}

    private void set(long offset, Runnable o)
    {
        unsafe.putObject(array, offset, o);
    }

    //private void setOrdered(long offset, Runnable o)
    //{
    //    unsafe.putOrderedObject(array, offset, o);
    //}

    private boolean compareAndSet(long offset, Runnable expect, Runnable update)
    {
        return unsafe.compareAndSwapObject(array, offset, expect, update);
    }

}

