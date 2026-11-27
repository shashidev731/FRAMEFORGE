package com.frameforge.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition as MediaComposition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
@ExperimentalApi
internal class TimelineExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val requestPath = inputData.getString(KEY_REQUEST_PATH)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Export request path is missing."))
        val requestFile = File(requestPath)
        if (!requestFile.isFile) {
            return Result.failure(workDataOf(KEY_ERROR to "The saved export request is unavailable."))
        }

        val request = try {
            withContext(Dispatchers.IO) {
                TimelineExportRequestCodec.decode(requestFile.readText(Charsets.UTF_8))
            }
        } catch (failure: Exception) {
            requestFile.delete()
            return Result.failure(workDataOf(KEY_ERROR to (failure.message ?: "The export request is invalid.")))
        }

        val output = File(request.outputPath)
        val outputParent = output.parentFile
        if (outputParent == null || (!outputParent.exists() && !outputParent.mkdirs())) {
            requestFile.delete()
            return Result.failure(workDataOf(KEY_ERROR to "The export folder is unavailable."))
        }
        // A WorkManager retry after process death starts from the saved edit snapshot.
        // Never expose a partially written file from an interrupted attempt.
        if (output.exists() && !output.delete()) {
            requestFile.delete()
            return Result.failure(workDataOf(KEY_ERROR to "A previous partial export could not be removed."))
        }

        val composition = try {
            buildTimelineComposition(request.clips, request.configuration)
        } catch (failure: Exception) {
            null
        } ?: run {
            requestFile.delete()
            return Result.failure(workDataOf(KEY_ERROR to "The saved timeline could not be composed for export."))
        }

        return try {
            setForeground(createForegroundInfo(0))
            val outputSpec = exportOutputSpec(request.configuration)
            val videoMime = if (request.configuration.codec.equals("H.265", ignoreCase = true)) {
                MimeTypes.VIDEO_H265
            } else {
                MimeTypes.VIDEO_H264
            }
            val encoderFactory = DefaultEncoderFactory.Builder(applicationContext)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(outputSpec.videoBitrateBps)
                        .build()
                )
                .build()
            val completion = CompletableDeferred<String?>()
            val transformer = withContext(Dispatchers.Main.immediate) {
                Transformer.Builder(applicationContext)
                    .setEncoderFactory(encoderFactory)
                    .setVideoMimeType(videoMime)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(comp: MediaComposition, exportResult: ExportResult) {
                            completion.complete(null)
                        }

                        override fun onError(
                            comp: MediaComposition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            completion.complete(exportException.message ?: "The timeline could not be exported.")
                        }
                    })
                    .build()
            }

            try {
                withContext(Dispatchers.Main.immediate) {
                    transformer.start(composition, output.absolutePath)
                }
                val holder = ProgressHolder()
                var lastProgress = -1
                while (!completion.isCompleted) {
                    val progressState = withContext(Dispatchers.Main.immediate) {
                        transformer.getProgress(holder)
                    }
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val progress = holder.progress.coerceIn(0, 99)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            setProgress(workDataOf(KEY_PROGRESS to progress))
                            setForeground(createForegroundInfo(progress))
                        }
                    }
                    delay(500)
                }

                val error = completion.await()
                if (error != null) {
                    output.delete()
                    requestFile.delete()
                    Result.failure(workDataOf(KEY_ERROR to error))
                } else if (!output.isFile || output.length() <= 0L) {
                    output.delete()
                    requestFile.delete()
                    Result.failure(workDataOf(KEY_ERROR to "Export finished without a valid output file."))
                } else {
                    setProgress(workDataOf(KEY_PROGRESS to 100))
                    requestFile.delete()
                    Result.success(workDataOf(KEY_OUTPUT_PATH to output.absolutePath))
                }
            } catch (cancelled: CancellationException) {
                withContext(Dispatchers.Main.immediate) { runCatching { transformer.cancel() } }
                output.delete()
                // Keep the immutable request so WorkManager can replay it after a system stop.
                throw cancelled
            } catch (failure: Exception) {
                withContext(Dispatchers.Main.immediate) { runCatching { transformer.cancel() } }
                output.delete()
                requestFile.delete()
                Result.failure(workDataOf(KEY_ERROR to (failure.message ?: "Export failed.")))
            }
        } catch (cancelled: CancellationException) {
            output.delete()
            // Keep the immutable request so WorkManager can replay it after a system stop.
            throw cancelled
        } catch (failure: Exception) {
            output.delete()
            requestFile.delete()
            Result.failure(workDataOf(KEY_ERROR to (failure.message ?: "Unable to start export.")))
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Video export", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("FRAMEFORGE export")
            .setContentText(if (progress in 1..99) "Exporting video • $progress%" else "Preparing video export…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .build()
        return when {
            Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
            Build.VERSION.SDK_INT >= 29 -> ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            else -> ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_REQUEST_PATH = "export_request_path"
        const val KEY_PROGRESS = "export_progress"
        const val KEY_OUTPUT_PATH = "export_output_path"
        const val KEY_ERROR = "export_error"
        const val TAG = "frameforge-video-export"
        private const val CHANNEL_ID = "frameforge-video-export"
        private const val NOTIFICATION_ID = 4201
    }
}
