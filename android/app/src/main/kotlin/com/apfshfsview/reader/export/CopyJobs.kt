package com.apfshfsview.reader.export

import android.net.Uri
import java.util.concurrent.CopyOnWriteArrayList

enum class CopyState(val wireName: String) {
    QUEUED("queued"),
    RUNNING("running"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    FAILED("failed"),
}

data class CopyJob(
    val jobId: Long,
    val volumeHandle: Long,
    val nodeIds: List<Long>,
    val destination: Uri,
    val destinationName: String,
)

data class CopyProgress(
    val jobId: Long,
    val currentName: String,
    val copiedBytes: Long,
    val totalBytes: Long,
    val copiedFiles: Int,
    val totalFiles: Int,
    val skippedFiles: Int,
    val bytesPerSecond: Long,
    val state: CopyState,
    val errorMessage: String?,
    val destinationName: String,
) {
    companion object {
        fun idle(job: CopyJob, state: CopyState, errorMessage: String? = null): CopyProgress =
            CopyProgress(job.jobId, "", 0, 0, 0, 0, 0, 0, state, errorMessage, job.destinationName)
    }
}

object CopyJobs {
    private val queue = ArrayDeque<CopyJob>()
    private val activeJobs = HashMap<Long, CopyJob>()
    private val cancelledJobIds = HashSet<Long>()
    private val listeners = CopyOnWriteArrayList<(CopyProgress) -> Unit>()
    private var nextJobId = 1L

    fun enqueue(volumeHandle: Long, nodeIds: List<Long>, destination: Uri, destinationName: String): CopyJob {
        val job = synchronized(this) {
            CopyJob(nextJobId++, volumeHandle, nodeIds, destination, destinationName).also { queue.addLast(it) }
        }
        publish(CopyProgress.idle(job, CopyState.QUEUED))
        return job
    }

    @Synchronized
    fun next(): CopyJob? = queue.removeFirstOrNull()?.also { activeJobs[it.jobId] = it }

    @Synchronized
    fun finish(jobId: Long) {
        activeJobs.remove(jobId)
    }

    fun cancelForVolumes(volumeHandles: Set<Long>) {
        val jobIds = synchronized(this) {
            (queue + activeJobs.values).filter { it.volumeHandle in volumeHandles }.map { it.jobId }
        }
        jobIds.forEach(::cancel)
    }

    @Synchronized
    fun hasPending(): Boolean = queue.isNotEmpty()

    fun cancel(jobId: Long) {
        val removed = synchronized(this) {
            cancelledJobIds += jobId
            queue.firstOrNull { it.jobId == jobId }?.also { queue.remove(it) }
        }
        removed?.let { publish(CopyProgress.idle(it, CopyState.CANCELLED)) }
    }

    @Synchronized
    fun isCancelled(jobId: Long): Boolean = jobId in cancelledJobIds

    fun publish(progress: CopyProgress) {
        listeners.forEach { it(progress) }
    }

    fun addListener(listener: (CopyProgress) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (CopyProgress) -> Unit) {
        listeners -= listener
    }
}
