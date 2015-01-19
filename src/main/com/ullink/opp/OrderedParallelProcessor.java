package com.ullink.opp;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OrderedParallelProcessor<OUT>
{
    private final int           nSlots;
    private final int           mask;
    private OUT[]               slots;

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
        slots = (OUT[]) new Object[nSlot];
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

    public void runSequentially(long seq, OUT outObject)
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
                    slots[slotOf(seq)] = outObject;
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
                consumeSequentiallyProtected(localTail, outObject);

                // Look for more to process
                int index = slotOf(++localTail);
                while (true)
                {
                    OUT slot;
                    while ((slot = slots[index])!=null)
                    {
                        consumeSequentiallyProtected(localTail, slot);
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


    private void consumeSequentiallyProtected(long seq, OUT object)
    {
        try
        {
            //consumeSequentially(object);
        }
        catch (Throwable e)
        {
            //exceptionHandler(object, e);
        }
    }

}
