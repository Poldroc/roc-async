package com.poldroc.async.worker;

import com.poldroc.async.wrapper.WorkerWrapper;

import java.util.Map;
/**
 * each minimal execution unit needs to implement this interface
 * @author Poldroc
 * @date 2024/6/20
 */

public interface IWorker<T, V> {

    /**
     * do time-consuming operations here, such as rpc requests, IO, etc.
     */
    V action(T param, Map<String, WorkerWrapper> allWrappers);


    /**
     * default value when timeout or exception
     */
    default V defaultValue() {
        return null;
    }
}
