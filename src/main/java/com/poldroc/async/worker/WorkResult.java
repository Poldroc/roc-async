package com.poldroc.async.worker;

/**
 * the result of the work
 *
 * @author Poldroc
 * @date 2024/6/27
 */

public class WorkResult<V> {

    private V result;

    private ResultState resultState;

    private Exception ex;

    public WorkResult(V result, ResultState resultState) {
        this(result, resultState, null);
    }

    public WorkResult(V result, ResultState resultState, Exception ex) {
        this.result = result;
        this.resultState = resultState;
        this.ex = ex;
    }

    public Exception getEx() {
        return ex;
    }

    public void setEx(Exception ex) {
        this.ex = ex;
    }

    public V getResult() {
        return result;
    }

    public void setResult(V result) {
        this.result = result;
    }

    public ResultState getResultState() {
        return resultState;
    }

    public void setResultState(ResultState resultState) {
        this.resultState = resultState;
    }

    public static <V> WorkResult<V> defaultResult() {
        return new WorkResult<>(null, ResultState.DEFAULT);
    }

    @Override
    public String toString() {
        return "WorkResult{" +
                "result=" + result +
                ", resultState=" + resultState +
                ", ex=" + ex +
                '}';
    }


}
