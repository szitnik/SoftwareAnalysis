package java.util.stream;

/**
 * A stateful operation. State is accumulated as elements are processed.
 * <p>The parallel evaluation returns a conc-tree of output elements.</p>
 *
 * @param <E> Type of input and output elements.
 *
 */
interface StatefulOp<E> extends IntermediateOp<E, E> {

    @Override
    public default boolean isStateful() {
        return true;
    }

    /**
     * Evaluate the result of the operation in parallel.
     *
     *
     * @param helper
     * @return the result of the operation.
     */
    <P_IN> Node<E> evaluateParallel(PipelineHelper<P_IN, E> helper);
}
