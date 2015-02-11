# Ordered Parallel Processor
Ordered processing going parallel

Inspired by the article [Exploiting Data Parallelism in Ordered Data Streams](https://software.intel.com/en-us/articles/exploiting-data-parallelism-in-ordered-data-streams)
from the [Intel Guide for Developing Multithreaded Applications](https://software.intel.com/en-us/articles/intel-guide-for-developing-multithreaded-applications).

This implementation brings a lightweigth solution for unlocking code that it only synchronized because of ordered/sequential requirements.

## Example

Multiple threads can execute the following code.

```
UpdateIndex(A);
B = EncodeProcessing(A);
WriteToNetwork(B);
WriteToDisk(B);
```

We need to need to have all operations in the right order to guarantee consistency between the Index, the Network remote and the Disk.

### Synchronized

```
synchronized(this)
{
  UpdateIndex(A);
  B = EncodeProcessing(A);
  WriteToNetwork(B);
  WriteToDisk(B);
}
```

Not very efficient because only 1 thread can EncodeProcessing() at a time.
And Thread n+1 can't WriteToNetwork() while Thread n moved to WriteToDisk()

### Ordered Parallel

```
synchronized(this)
{
  ticket = getNextTicket();
  UpdateIndex(A);
}
  
B = EncodeProcessing(A);

orderedParalellProcessor1.runSequentially(ticket, WriteToNetwork(B));
orderedParalellProcessor2.runSequentially(ticket, WriteToDisk(B);
```

Here:
- UpdateIndex() is synchronized and we get the ordering via the ticket
- EncodeProcessing() is executed concurrently by multiple threads
- WriteToNetWork() and WriteToDisk() are executed in parallel but always with the right ordering.
