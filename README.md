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
    write(output);
  }
}
```

Performance drawbacks:
- even though `process()` call may be thread-safe, it's not executed concurrently

### Ordered Scheduler

```java
OrderedScheduler scheduler = new OrderedScheduler()

public void execute()
{
  long ticket;
  FooInput input;
  synchronized (this)
  {
    input = read();

    // read() is successful. No exceptions. Let's take the ticket.
    // ticket will "record" the ordering of read() calls, and use it to guarantee same write() ordering
    ticket = getNextTicket();
  }
  
  try
  {
    // this will be executed concurrently (obviously needs to be thread-safe)
    BarOutput output = process(input);
  }
  catch(Exception e)
  {
    // Important to trash the ticket in case of a problem during the processing
    // otherwise scheduler.run() will wait infinitely
    scheduler.trash(ticket);
    throw new RuntimeException(e);
  }
  
  // Let run the write() in the ticket order
  scheduler.run(ticket, () => { write(output); } );
}
```

Or, abstracting the OrderedScheduler usage with the provided Pattern classes:

```java
ReadProcessWritePatterm<FooInput,BarOutput> pattern = new ReadProcessWritePatterm<>()

public void execute()
{
  pattern.execute(
            () => { read() },
            (input) => { process(input) },
            (output) => { write(output); } );
}
```

Performance benefits:
- critical section has been reduced to the minimum (`read()` ordering)
- `process()` is executed concurrently by the incoming threads
- no extra thread/pool introduced (uses only the incoming/current ones)
- implementation is *fast*: lock-free and wait-free (CAS only)

Drawbacks:
- a bit more user code
- exceptions need to be handled in a separate callback


