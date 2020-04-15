package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.context.TaskContextImpl;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calls the specified task. Responsible for preparing the task's input
 * and processing the output.
 */
public class TaskCallCommand extends StepCommand<TaskCall> {

    private static final long serialVersionUID = 1L;

    public TaskCallCommand(TaskCall step) {
        super(step);
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        TaskProviders taskProviders = runtime.getService(TaskProviders.class);
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep(), UUID.randomUUID());

        TaskCall call = getStep();
        String taskName = call.getName();
        Task t = taskProviders.createTask(ctx, taskName);
        if (t == null) {
            throw new IllegalStateException("Task not found: " + taskName);
        }

        TaskCallOptions opts = call.getOptions();
        Map<String, Object> input = VMUtils.prepareInput(expressionEvaluator, ctx, opts.input());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Serializable> resultRef = new AtomicReference<>();

        ThreadGroup threadGroup = new TaskThreadGroup(call.getName(), call.getName());
        new Thread(threadGroup, () -> {
            Serializable out = ThreadLocalContext.withContext(ctx, () -> t.execute(new TaskContextImpl(ctx, taskName, input)));
            resultRef.set(out);
            latch.countDown();
        }).start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Serializable result = resultRef.get();

        String out = opts.out();
        if (out != null) {
            GlobalVariables gv = runtime.getService(GlobalVariables.class);
            gv.put(out, result); // TODO a custom result structure
        }
    }

    public static final class TaskThreadGroup extends ThreadGroup {

        private final String segmentId;

        public TaskThreadGroup(String name, String segmentId) {
            super(name);
            this.segmentId = segmentId;
        }

        public String getSegmentId() {
            return segmentId;
        }
    }
}
