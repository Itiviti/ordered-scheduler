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
import java.util.Arrays;

public class OrderedScheduler
{
    private long seq = 0;
    private OrderedPipe[] orderedPipes = new OrderedPipe[0];

    // The Unsafe
    static final Unsafe UNSAFE;
    private static final long seqOffset;
    static
    {
        try
        {
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
            seqOffset = UNSAFE.objectFieldOffset(OrderedScheduler.class.getDeclaredField("seq"));
        }
        catch (Exception e)
        {
            throw new Error(e);
        }
    }

    public OrderedScheduler()
    {
    }

    public OrderedPipe createPipe()
    {
        return createPipe(1024);
    }

    public OrderedPipe createPipe(int pipeSize)
    {
        if (seq > 0)
        {
            throw new IllegalStateException("Can't create a new pipe after first ticket has been acquired");
        }

        OrderedPipe op = new OrderedPipeLockFreeUnsafe(pipeSize);
        OrderedPipe[] newOrderedPipes = Arrays.copyOf(orderedPipes, orderedPipes.length+1);
        newOrderedPipes[orderedPipes.length] = op;
        orderedPipes = newOrderedPipes;
        return op;
    }

    public Ticket getNextTicket()
    {
        Ticket t = new Ticket();
        t.orderedPipes = orderedPipes;
        t.seq = seq++;
        return t;
    }

    public Ticket getNextTicketAtomic()
    {
        Ticket t = new Ticket();
        t.orderedPipes = orderedPipes;
        t.seq = UNSAFE.getAndAddLong(this, seqOffset, 1L);
        return t;
    }
}
