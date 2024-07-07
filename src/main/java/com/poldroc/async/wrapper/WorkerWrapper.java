package com.poldroc.async.wrapper;

import com.poldroc.async.callback.DefaultCallback;
import com.poldroc.async.callback.ICallback;
import com.poldroc.async.exception.SkippedException;
import com.poldroc.async.timer.SystemClock;
import com.poldroc.async.worker.IWorker;
import com.poldroc.async.worker.ResultState;
import com.poldroc.async.worker.WorkResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.poldroc.async.worker.WorkResult.defaultResult;

/**
 * 对每个worker和callback进行包装
 */
public class WorkerWrapper<T, V> {

    private String id;

    private T param;

    private IWorker<T, V> worker;

    private ICallback<T, V> callback;

    private List<WorkerWrapper<?, ?>> nextWrappers;

    private List<DependWrapper> dependWrappers;

    /**
     * 标记该事件是否已经被处理过了
     * <p>
     * 1-finish, 2-error, 3-working
     */
    private AtomicInteger state = new AtomicInteger(0);

    private volatile WorkResult<V> workResult = defaultResult();

    private Map<String, WorkerWrapper> allWrappers;

    /**
     * 是否在执行自己前，去校验nextWrapper的执行结果
     * (因为可能nextWrapper有多个依赖)
     * <p>
     * 注意，该属性仅在nextWrapper数量<=1时有效，>1时的情况是不存在的
     */
    private volatile boolean needCheckNextWrapperResult = true;

    private static final int FINISH = 1;
    private static final int ERROR = 2;
    private static final int WORKING = 3;
    private static final int INIT = 0;

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

    /**
     * 开始工作
     *
     * @param executorService     线程池
     * @param fromWrapper         这次work是由哪个上游wrapper发起的
     * @param remainTime          剩余时间
     * @param forParamUseWrappers 用于存放所有wrapper的map
     */
    public void work(ExecutorService executorService, WorkerWrapper fromWrapper, long remainTime, Map<String, WorkerWrapper> forParamUseWrappers) {
        forParamUseWrappers.put(id, this);
        this.allWrappers = forParamUseWrappers;
        long now = SystemClock.now();
        // 总的已经超时了，就快速失败，进行下一个
        if (remainTime <= 0) {
            fastFail(INIT, null);
            beginNext(executorService, now, remainTime);
            return;
        }

        // 如果已经执行完毕了，直接进行下一个，防止其他依赖进来重复执行
        if (getState() == FINISH || getState() == ERROR) {
            beginNext(executorService, now, remainTime);
            return;
        }

        // 如果在执行前需要校验nextWrapper的状态
        if (needCheckNextWrapperResult) {
            // 如果自己的next链上有已经出结果或已经开始执行的任务了，自己就不用继续了
            if (!checkNextWrapperResult()) {
                fastFail(INIT, new SkippedException());
                beginNext(executorService, now, remainTime);
                return;
            }
        }

        // 如果没有依赖，说明为第一批任务，直接执行
        if (dependWrappers == null || dependWrappers.isEmpty()) {
            fire();
            beginNext(executorService, now, remainTime);
            return;
        }
        if (dependWrappers.size() == 1) {
            doDependsOneJob(dependWrappers.get(0).getDependWrapper());
            beginNext(executorService, now, remainTime);
        } else {
            // 有多个依赖
            doDependsJobs(executorService, dependWrappers, fromWrapper, now, remainTime);
        }
    }

    public void work(ExecutorService executorService, long remainTime, Map<String, WorkerWrapper> forParamUseWrappers) {
        work(executorService, null, remainTime, forParamUseWrappers);
    }

    private void doDependsOneJob(WorkerWrapper dependWrapper) {
        if (ResultState.TIMEOUT == dependWrapper.getWorkResult().getResultState()) {
            workResult = defaultTimeOutResult();
            fastFail(INIT, null);
        } else if (ResultState.EXCEPTION == dependWrapper.getWorkResult().getResultState()) {
            workResult = defaultExResult(dependWrapper.getWorkResult().getEx());
            fastFail(INIT, null);
        } else {
            fire();
        }
    }

    private synchronized void doDependsJobs(ExecutorService executorService, List<DependWrapper> dependWrappers, WorkerWrapper fromWrapper, long now, long remainTime) {
        // 如果当前任务已经完成了，依赖的其他任务拿到锁再进来时，不需要执行下面的逻辑了
        if (getState() != INIT) {
            return;
        }
        boolean nowDependIsMust = false;
        // 必须完成的上游wrapper集合
        Set<DependWrapper> mustWrapper = new HashSet<>();
        for (DependWrapper dependWrapper : dependWrappers) {
            if (dependWrapper.isMust()) {
                mustWrapper.add(dependWrapper);
            }
            if (dependWrapper.getDependWrapper().equals(fromWrapper)) {
                nowDependIsMust = dependWrapper.isMust();
            }
        }

        // 如果全部是不必须的条件，那么只要到了这里，就执行自己
        if (mustWrapper.isEmpty()) {
            if (ResultState.TIMEOUT == fromWrapper.getWorkResult().getResultState()) {
                fastFail(INIT, null);
            } else {
                fire();
            }
            beginNext(executorService, now, remainTime);
            return;
        }

        // 如果存在需要必须完成的，且fromWrapper不是必须的，就什么也不干
        if (!nowDependIsMust) {
            return;
        }

        // 如果fromWrapper是必须的
        boolean existNoFinish = false;
        // 先判断前面必须要执行的依赖任务的执行结果，只要有任何一个失败，就不action，快速失败，进行下一步
        for (DependWrapper dependWrapper : mustWrapper) {
            WorkerWrapper<?, ?> workerWrapper = dependWrapper.getDependWrapper();
            WorkResult<?> tempWorkResult = workerWrapper.getWorkResult();
            // 检查任务状态是否为INIT或WORKING
            if (workerWrapper.getState() == INIT || workerWrapper.getState() == WORKING) {
                existNoFinish = true;
                break;
            }
            // 检查任务结果状态是否为TIMEOUT或EXCEPTION
            ResultState resultState = tempWorkResult.getResultState();
            if (resultState == ResultState.TIMEOUT || resultState == ResultState.EXCEPTION) {
                workResult = (resultState == ResultState.TIMEOUT) ? defaultTimeOutResult() : defaultExResult(workerWrapper.getWorkResult().getEx());
                // 上游只要有失败 本任务就不执行
                fastFail(INIT, null);
                beginNext(executorService, now, remainTime);
                return;
            }
        }

        // 上游没有失败
        // 1.都finish 2.有的在working （现在不需要处理）
        if (!existNoFinish) {
            // 都finish
            fire();
            beginNext(executorService, now, remainTime);
        }
    }

    /**
     * 执行自己的job.具体的执行是在另一个线程里,但判断阻塞超时是在work线程
     */
    private void fire() {
        workResult = workerDoJob();
    }

    /**
     * 具体的单个worker执行任务
     */
    private WorkResult<V> workerDoJob() {
        // 避免重复执行
        if (!checkIsNullResult()) {
            return workResult;
        }
        try {
            // 重要: 如果已经不是INIT状态，说明已经被其他线程执行或已执行完毕，直接返回
            if (!compareAndSetState(INIT, WORKING)) {
                return workResult;
            }

            callback.begin();
            V resultValue = worker.action(param, allWrappers);

            if (!compareAndSetState(WORKING, FINISH)) {
                return workResult;
            }

            workResult.setResultState(ResultState.SUCCESS);
            workResult.setResult(resultValue);
            callback.result(true, param, workResult);
            return workResult;
        } catch (Exception e) {
            if (!checkIsNullResult()) {
                return workResult;
            }
            fastFail(WORKING, e);
            return workResult;
        }
    }

    /**
     * 判断自己下游链路上，是否存在已经出结果的或已经开始执行的
     * 如果没有返回true，如果有返回false
     */
    private boolean checkNextWrapperResult() {
        // 如果自己是最后一个，或者后面是并行的多个，就返回true
        if (nextWrappers == null || nextWrappers.size() != 1) {
            return getState() == INIT;
        }
        WorkerWrapper<?, ?> nextWrapper = nextWrappers.get(0);
        boolean state = nextWrapper.getState() == INIT;
        // 继续校验自己的next的状态
        return state && nextWrapper.checkNextWrapperResult();
    }

    private void fastFail(int expect, Exception e) {
        // 试图将状态从expect改为ERROR
        if (!compareAndSetState(expect, ERROR)) {
            return;
        }
        // 未处理过结果
        if (checkIsNullResult()) {
            if (e == null) {
                workResult = defaultTimeOutResult();
            } else {
                workResult = defaultExResult(e);
            }
        }
        callback.result(false, param, workResult);
    }

    private void beginNext(ExecutorService executorService, long now, long remainTime) {
        // 耗时计算
        long costTime = SystemClock.now() - now;
        if (nextWrappers == null || nextWrappers.isEmpty()) {
            return;
        }
        if (nextWrappers.size() == 1) {
            nextWrappers.get(0).work(executorService, WorkerWrapper.this, remainTime - costTime, allWrappers);
            return;
        }
        // 并行执行
        CompletableFuture[] futures = new CompletableFuture[nextWrappers.size()];
        for (int i = 0; i < nextWrappers.size(); i++) {
            WorkerWrapper<?, ?> nextWrapper = nextWrappers.get(i);
            futures[i] = CompletableFuture.runAsync(() ->
                    nextWrapper.work(executorService, WorkerWrapper.this, remainTime - costTime, allWrappers), executorService);
        }
        try {
            // 等待所有的next执行完毕
            CompletableFuture.allOf(futures).get(remainTime - costTime, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getState() {
        return state.get();
    }

    public WorkResult<V> getWorkResult() {
        return workResult;
    }

    public List<WorkerWrapper<?, ?>> getNextWrappers() {
        return nextWrappers;
    }

    public void setParam(T param) {
        this.param = param;
    }

    private void setNeedCheckNextWrapperResult(boolean needCheckNextWrapperResult) {
        this.needCheckNextWrapperResult = needCheckNextWrapperResult;
    }

    private void addDepend(WorkerWrapper<?, ?> workerWrapper, boolean must) {
        addDepend(new DependWrapper(workerWrapper, must));
    }

    private void addDepend(DependWrapper dependWrapper) {
        if (dependWrappers == null) {
            dependWrappers = new ArrayList<>();
        }
        // 如果依赖的是重复的同一个，就不重复添加了
        for (DependWrapper wrapper : dependWrappers) {
            if (wrapper.equals(dependWrapper)) {
                return;
            }
        }
        dependWrappers.add(dependWrapper);
    }

    private void addNext(WorkerWrapper<?, ?> workerWrapper) {
        if (nextWrappers == null) {
            nextWrappers = new ArrayList<>();
        }
        // 避免添加重复
        for (WorkerWrapper wrapper : nextWrappers) {
            if (workerWrapper.equals(wrapper)) {
                return;
            }
        }
        nextWrappers.add(workerWrapper);
    }

    /**
     * 如果当前wrapper的状态是expect，那么将状态更新为update，返回 true
     */
    private boolean compareAndSetState(int expect, int update) {
        return this.state.compareAndSet(expect, update);
    }

    private boolean checkIsNullResult() {
        return ResultState.DEFAULT == workResult.getResultState();
    }

    private WorkResult<V> defaultTimeOutResult() {
        workResult.setResultState(ResultState.TIMEOUT);
        workResult.setResult(worker.defaultValue());
        return workResult;
    }

    private WorkResult<V> defaultExResult(Exception ex) {
        workResult.setResultState(ResultState.EXCEPTION);
        workResult.setResult(worker.defaultValue());
        workResult.setEx(ex);
        return workResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkerWrapper<?, ?> that = (WorkerWrapper<?, ?>) o;
        return needCheckNextWrapperResult == that.needCheckNextWrapperResult &&
                state.get() == that.state.get() &&
                Objects.equals(id, that.id) &&
                Objects.equals(param, that.param) &&
                Objects.equals(worker, that.worker) &&
                Objects.equals(callback, that.callback) &&
                Objects.equals(nextWrappers, that.nextWrappers) &&
                Objects.equals(dependWrappers, that.dependWrappers) &&
                Objects.equals(workResult, that.workResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, param, worker, callback, nextWrappers, dependWrappers, state.get(), workResult, needCheckNextWrapperResult);
    }


}
