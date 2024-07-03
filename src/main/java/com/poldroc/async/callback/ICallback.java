package com.poldroc.async.callback;

import com.poldroc.async.worker.WorkResult;

/**
 * each execution unit will call back this interface after finished</p>
 *
 * @author Poldroc
 * @date 2024/6/27
 */

public interface ICallback<T, V> {

        /**
        * callback when task begin
        */
        default void begin() {

        }

        /**
        * callback when task finished
        */
        void result(boolean success, T param, WorkResult<V> workResult);
}
