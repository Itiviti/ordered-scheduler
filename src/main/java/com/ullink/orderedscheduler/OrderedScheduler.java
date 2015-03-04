package com.ullink.orderedscheduler;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class OrderedScheduler
{
    private long seq = 0;
    private AtomicLong aseq = new AtomicLong(0);

    private OrderedPipe[] orderedPipes = new OrderedPipe[0];

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
            throw new IllegalStateException("Can't create a new pipe after first ticket");
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

    // temp
    Ticket getNextTicketAtomic()
    {
        Ticket t = new Ticket();
        t.orderedPipes = orderedPipes;
        t.seq = aseq.getAndIncrement();
        return t;
    }
}
