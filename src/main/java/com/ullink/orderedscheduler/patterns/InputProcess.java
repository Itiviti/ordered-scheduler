package com.ullink.orderedscheduler.patterns;

public interface InputProcess<I,O>
{
   O process(I input);
}
