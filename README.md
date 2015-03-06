# Ordered Scheduler [![Build Status] (https://travis-ci.org/Ullink/ordered-scheduler.svg?branch=master)](https://travis-ci.org/Ullink/ordered-scheduler)
Unlock code that have sequence / ordering requirements

Inspired by the article [Exploiting Data Parallelism in Ordered Data Streams](https://software.intel.com/en-us/articles/exploiting-data-parallelism-in-ordered-data-streams)
from the [Intel Guide for Developing Multithreaded Applications](https://software.intel.com/en-us/articles/intel-guide-for-developing-multithreaded-applications).

This implementation brings a lightweight solution for unlocking code that it only synchronized because of ordered/sequential requirements.

## Use case

```java
// called by multiple threads (e.g. thread pool)
public void execute()
{
  FooInput input;
  synchronized (this)
  {
    // this call needs to be synchronized along the write() to guarantee same ordering
    input = read();
    // this call is therefore not executed conccurently (#1)
    BarOutput output = process(input);
    // both write calls need to done in the same order as the read(),
    // forcing them to be under the same lock
    write1(output);
    write2(output);
  }
}
```

Performance drawbacks:
- even though `process()` call may be thread-safe, it's not executed concurrently
- even though `write1()` and `write2()` may be independent one from each other, they won't be executed in parallel

### Ordered Scheduler

```java
OrderedScheduler scheduler = new OrderedScheduler()
OrderedPipe pipe1 = scheduler.createPipe();
OrderedPipe pipe2 = scheduler.createPipe();

public void execute()
{
  Ticket ticket;
  FooInput input;
  synchronized (this)
  {
    // ticket will "record" the ordering of read() calls, and use it to guarantee same write() ordering
    ticket = scheduler.getNextTicket();
    input = read();
  }
  
  try (Ticket t = ticket)
  {
    // this will be executed concurrently (obviously needs to be thread-safe)
    BarOutput output = process(input);
    
    // each pipe will be sequentialy processed (in the order of the ticket)
    // pipe.run() will return true if the task was executed by the current thread, and false if it will be executed by another thread
    pipe1.run(t, () => { write1(output); } );
    pipe2.run(t, () => { write2(output); } );
  }
}
```

Performance benefits:
- critical section has been reduced to the minimum (`read()` ordering)
- `process()` is executed concurrently by the incoming threads
- `write1()` and `write2()` may be executed in parallel (while still keeping the ordering in each pipe)
- no extra thread/pool introduced (uses only the incoming/current ones)
- implementation is *fast*: lock-free and wait-free (CAS only)

Drawbacks:
- a bit more user code
- exceptions need to be handled in a separate callback


