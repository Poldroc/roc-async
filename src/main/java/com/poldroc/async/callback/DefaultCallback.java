package com.poldroc.async.callback;

import com.poldroc.async.worker.WorkResult;

public class DefaultCallback<T, V> implements ICallback<T, V> {

    @Override
    public void result(boolean success, T param, WorkResult<V> workResult) {

    }

}
