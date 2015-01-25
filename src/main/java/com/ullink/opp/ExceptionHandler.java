package com.ullink.opp;

public interface ExceptionHandler
{
    void handle(long seq, Runnable runnable, Throwable exception);
}
