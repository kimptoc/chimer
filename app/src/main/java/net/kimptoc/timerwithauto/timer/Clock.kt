package net.kimptoc.timerwithauto.timer

/**
 * Indirection over wall-clock time so tests can substitute a fake.
 * Always returns System.currentTimeMillis() in production.
 */
fun interface Clock {
    fun nowEpochMs(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}
