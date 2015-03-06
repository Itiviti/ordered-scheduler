/*
 * Copyright 2015 ULLINK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ullink.orderedscheduler.patterns;

import com.ullink.orderedscheduler.OrderedScheduler;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ReadProcessWritePattern<IN, OUT>
{
    private long seq = 0;
    private Function<IN, OUT> process;
    private Consumer<OUT> consumer;
    private OrderedScheduler os;

    public ReadProcessWritePattern(Function<IN, OUT> process, Consumer<OUT> consumer)
    {
        this.process = process;
        this.consumer = consumer;

        os = new OrderedScheduler(1024);
    }

    public boolean execute(Supplier<IN> supplier)
    {
        final IN in;
        final long ticket;
        synchronized (this)
        {
            in = supplier.get();

            // supplier.get() has success without exception
            // take ticket
            ticket = seq++;
        }

        final OUT out;
        try
        {
            out = process.apply(in);
        }
        catch (Throwable t)
        {
            os.trash(ticket);
            throw new RuntimeException(t);
        }

        return os.run(ticket, new Runnable() {
            @Override
            public void run() {
                consumer.accept(out);
            }
        });
    }
}
