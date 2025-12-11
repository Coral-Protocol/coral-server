package org.coralprotocol.coralserver.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A list that can be modified concurrently.  This class also provides a [wait] method that allows subscribers to
 * suspend until new items are posted to this list.
 */
class ConcurrentMutableList<T> {
    private val items = mutableListOf<T>()
    private val mutex = Mutex()
    private val waiters = mutableListOf<CompletableDeferred<T>>()

    suspend fun add(item: T): T {
        mutex.withLock {
            waiters.removeFirstOrNull()?.complete(item)
            items.add(item)
        }

        return item
    }

    suspend fun wait(): T {
        val deferred = CompletableDeferred<T>()

        mutex.withLock { waiters.add(deferred) }
        val awaited = deferred.await()
        mutex.withLock { waiters.remove(deferred) }

        return awaited
    }

    suspend fun remove(item: T) {
        mutex.withLock {
            items.remove(item)
        }
    }

    suspend fun get(index: Int) = mutex.withLock { items[index] }

    suspend fun forEach(fn: (T) -> Unit) {
        mutex.withLock {
            items.forEach(fn)
        }
    }

    suspend fun first(predicate: (T) -> Boolean): T {
        val item = items.firstOrNull(predicate)
        if (item != null)
            return item

        var waited = wait()
        while (!predicate(waited)) {
            waited = wait()
        }

        return waited
    }

    suspend fun isEmpty() = mutex.withLock { items.isEmpty() }

    suspend fun isNotEmpty() = mutex.withLock { items.isNotEmpty() }

    suspend fun size() = mutex.withLock { items.size }
}