package de.ilazlow.velosonic.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/** Thrown when too many per-item failures suggest the network dropped mid-sync, rather than
 *  one-off bad luck on a handful of items — mirrors the iOS sync engine's "10 failures ->
 *  abort" threshold in its withThrowingTaskGroup loops. */
class SyncAbortedException(message: String) : Exception(message)

/**
 * Bounded-parallelism fetch, mirroring the iOS sync engine's `withThrowingTaskGroup(maxConcurrent: 10)`
 * sliding-window pattern (Swift's TaskGroup has no built-in bounded-concurrency primitive, so it
 * manually tracked in-flight count; a Semaphore gives the same bound directly in Kotlin).
 *
 * A per-item failure is caught and returned as `null` in the result list rather than failing the
 * whole batch — but once [failureThreshold] items have failed, the remaining work is aborted
 * (this is what actually detects "network dropped" instead of retrying forever against a dead
 * connection).
 */
suspend fun <T, R> fetchConcurrently(
    items: List<T>,
    concurrency: Int = 10,
    failureThreshold: Int = 10,
    /** Invoked (from whichever concurrent coroutine finishes) after every item, success or
     *  failure — the live "X/Y" counters SyncScreen/the sync notification show during a long
     *  fetch phase. */
    onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    fetch: suspend (T) -> R
): List<R?> = coroutineScope {
    val semaphore = Semaphore(concurrency)
    val failures = AtomicInteger(0)
    val completed = AtomicInteger(0)
    val total = items.size
    items.map { item ->
        async {
            semaphore.withPermit {
                val result = try {
                    fetch(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (failures.incrementAndGet() >= failureThreshold) {
                        throw SyncAbortedException(
                            "Aborting sync — $failureThreshold consecutive item fetches failed"
                        )
                    }
                    null
                }
                onProgress?.invoke(completed.incrementAndGet(), total)
                result
            }
        }
    }.awaitAll()
}
