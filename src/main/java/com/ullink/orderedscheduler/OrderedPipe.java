package com.ullink.orderedscheduler;

public interface OrderedPipe
{
    boolean run(Ticket ticket, Runnable runnable);
    void close(Ticket ticket);
}
