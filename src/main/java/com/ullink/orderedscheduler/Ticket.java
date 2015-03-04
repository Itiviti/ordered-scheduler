package com.ullink.orderedscheduler;

import java.io.Closeable;
import java.io.IOException;

public class Ticket implements Closeable
{
    long seq;
    OrderedPipe[] orderedPipes;

    @Override
    public void close() throws IOException
    {
        for(OrderedPipe op : orderedPipes)
        {
            op.close(this);
        }
    }
}
