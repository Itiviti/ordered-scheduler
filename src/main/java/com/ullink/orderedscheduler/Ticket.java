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

package com.ullink.orderedscheduler;

import java.io.Closeable;
import java.io.IOException;

public class Ticket implements Closeable
{
    long seq;

    private OrderedPipe[] orderedPipes;
    private int toProcessCount = 0;

    void setOrderedPipes(OrderedPipe[] ops)
    {
        this.orderedPipes = ops;
        toProcessCount = ops.length;
    }

    void plusOneProcessed()
    {
        --toProcessCount;
    }

    @Override
    public void close() throws IOException
    {
        if (toProcessCount>0)
        {
            for(OrderedPipe op : orderedPipes)
            {
                op.close(this);
            }
        }
    }
}
