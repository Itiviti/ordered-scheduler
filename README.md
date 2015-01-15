# Ordered Parallel Processor
Ordered processing going parallel

Inspired by the article [Exploiting Data Parallelism in Ordered Data Streams](https://software.intel.com/en-us/articles/exploiting-data-parallelism-in-ordered-data-streams)
from the [Intel Guide for Developing Multithreaded Applications](https://software.intel.com/en-us/articles/intel-guide-for-developing-multithreaded-applications).

This implementation brings a lightweigth solution for unlocking code that it only synchronized because of ordered/sequential requirements.

## Example

Multiple threads can execute the following code.

```
synchronized(this)
{
  Input A = queue.poll();
  Output B = processing(A);
  write(B);
}
```
We need to write B in the same order than A is read from the queue.
So the reading and the writing are synchronized so no thread can race each other in this execution.

