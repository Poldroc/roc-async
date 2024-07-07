package com.poldroc.async.exception;

/**
 * This exception is thrown if the next work has already been executed
 * or is being executed before current work is executed.
 */
public class SkippedException extends RuntimeException {
    public SkippedException() {
        super("SkippedException: current work is skipped because the next work has already been executed or is being executed.");
    }

    public SkippedException(String message) {
        super(message);
    }
}
