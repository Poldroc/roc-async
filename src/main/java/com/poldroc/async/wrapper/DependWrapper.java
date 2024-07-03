package com.poldroc.async.wrapper;

/**
 * wrapping of dependent wrappers
 * that is, the step before referencing the work of this class
 *
 * @author Poldroc
 * @date 2024/7/3
 */

public class DependWrapper {
    private WorkerWrapper<?, ?> dependWrapper;

    /**
     * whether the dependency must be completed before executing itself.
     */
    private boolean must = true;

    public DependWrapper(WorkerWrapper<?, ?> dependWrapper, boolean must) {
        this.dependWrapper = dependWrapper;
        this.must = must;
    }

    public DependWrapper() {
    }

    public WorkerWrapper<?, ?> getDependWrapper() {
        return dependWrapper;
    }

    public void setDependWrapper(WorkerWrapper<?, ?> dependWrapper) {
        this.dependWrapper = dependWrapper;
    }

    public boolean isMust() {
        return must;
    }

    public void setMust(boolean must) {
        this.must = must;
    }

    @Override
    public String toString() {
        return "DependWrapper{" +
                "dependWrapper=" + dependWrapper +
                ", must=" + must +
                '}';
    }
}
