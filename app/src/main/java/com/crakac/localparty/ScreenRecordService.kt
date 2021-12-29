package com.crakac.localparty

import android.app.*
import android.app.Activity.RESULT_OK
import android.content.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.crakac.localparty.encode.MyMediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class ScreenRecordService : Service() {
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var recorder: MyMediaRecorder
    private lateinit var projection: MediaProjection
    private lateinit var contentUri: Uri

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            throw IllegalArgumentException("intent is null")
        }
        val data = intent.getParcelableExtra<Intent>(KEY_DATA)
            ?: throw IllegalArgumentException("data is not set")
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Sharing", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("LocalParty")
            .setContentText("Tap to stop recording.")
            .setSmallIcon(R.drawable.ic_cast)
            .setContentIntent(pendingIntent(applicationContext))
            .build()
        startForeground(NOTIFICATION_ID, notification)
        startRecording(data)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    private fun startRecording(data: Intent) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(RESULT_OK, data)

        val metrics = resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels

        val scale = if (maxOf(rawWidth, rawHeight) > 960) {
            960f / maxOf(rawWidth, rawHeight)
        } else 1f
        val width = (scale * rawWidth).roundToInt() / 2 * 2
        val height = (scale * rawHeight).roundToInt() / 2 * 2

        contentUri = createContentUri()
        Log.d("File Uri", contentUri.toString())
        val fd = contentResolver.openFileDescriptor(contentUri, "w")!!.fileDescriptor
        recorder = MyMediaRecorder(fd, width, height)
        recorder.prepare()
        virtualDisplay = projection.createVirtualDisplay(
            "LocalParty",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            null
        )
        recorder.start()
    }

    private fun stopRecording() {
        recorder.stop()
        projection.stop()
        virtualDisplay.release()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(contentUri, values, null, null)
        }
    }

    private fun createContentUri(): Uri {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val formattedTime = sdf.format(Date())
        val filename = "$formattedTime.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.TITLE, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/avc")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val storageUri =
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            return contentResolver.insert(storageUri, values)!!
        } else {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), filename)
            return FileProvider.getUriForFile(
                applicationContext,
                getString(R.string.file_provider_authority), file
            )
        }
    }

    companion object {
        const val ACTION_STOP = "stop"
        const val KEY_DATA = "data"
        const val CHANNEL_ID = "LocalParty"
        const val NOTIFICATION_ID = 1
        fun createIntent(context: Context, data: Intent): Intent {
            return Intent(context, ScreenRecordService::class.java)
                .putExtra(KEY_DATA, data)
        }

        fun pendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, NotificationEventReceiver::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    class NotificationEventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) {
                return
            }
            if (intent.action == ACTION_STOP) {
                context.stopService(Intent(context, ScreenRecordService::class.java))
            }
        }
    }
}