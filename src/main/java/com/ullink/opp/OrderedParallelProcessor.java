package com.ullink.opp;

public interface OrderedParallelProcessor
{
    boolean runSequentially(long seq, Runnable runnable);
}
