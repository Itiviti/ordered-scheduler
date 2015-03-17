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

public class GetInputProcessPushPattern<IN, OUT>
{
    private long seq = 0;
    private OrderedScheduler os;

    public GetInputProcessPushPattern(int size)
    {
        os = new OrderedScheduler(size);
    }

    public boolean execute(Supplier<IN> supplier, Function<IN, OUT> process, final Consumer<OUT> consumer)
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
