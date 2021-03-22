package org.openjfx;

/**
 * A representation of an operation that can be done and then undone again.
 */
public interface ReversibleOperation {

    /**
     * Executes this operation.
     *
     * @return this very Operation instance again
     * @throws Exception The execution of this operation may throw an Exception.
     */
    public ReversibleOperation _do() throws Exception;

    /**
     * Undoes this operation.
     *
     * @return this very Operation instance again
     * @throws Exception The undoing of this operation may throw an Exception.
     */
    public ReversibleOperation _undo() throws Exception;

}
