package io.github.wkoller.pollenplot.ml.text

/** Turns text into unit-length vectors that are close together when the texts mean similar things. */
interface Embedder : AutoCloseable {
    /** The vector for a record's text. */
    fun embedPassage(text: String): FloatArray

    /** The vector for something typed into search. */
    fun embedQuery(text: String): FloatArray

    override fun close() = Unit
}
