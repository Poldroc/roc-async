package com.poldroc.async.wrapper;

import com.poldroc.async.callback.DefaultCallback;
import com.poldroc.async.callback.ICallback;
import com.poldroc.async.worker.IWorker;
import com.poldroc.async.worker.WorkResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * wrapping each worker and callback, one-to-one
 *
 * @param <T>
 * @param <V>
 */
public class WorkerWrapper<T, V> {

    private String id;

    private T param;

    private IWorker<T, V> worker;

    private ICallback<T, V> callback;

    private List<WorkerWrapper<?, ?>> nextWrappers;

    private List<DependWrapper> dependWrappers;

    /**
     * 0: not executed, 1: executed, 2: error 3: executing
     */
    private AtomicInteger isExecuted = new AtomicInteger(0);

    private volatile WorkResult<V> workResult = WorkResult.defaultResult();

    private Map<String, WorkerWrapper> allWrappers;

    /**
     * whether to check the nextWrapper's execution result before executing itself
     * (the case where the nextWrapper has multiple dependWrappers)
     */
    private volatile boolean needCheckNextWrapperResult = true;

    private static final int NOT_EXECUTED = 0;

    private static final int EXECUTED = 1;

    private static final int ERROR = 2;

    private static final int EXECUTING = 3;

    public WorkerWrapper(String id, T param, IWorker<T, V> worker, ICallback<T, V> callback) {
        if (worker == null) {
            throw new NullPointerException("async.worker cannot be null");
        }
        if (callback == null) {
            callback = new DefaultCallback<>();
        }
        this.id = id;
        this.param = param;
        this.worker = worker;
        this.callback = callback;
    }
}
