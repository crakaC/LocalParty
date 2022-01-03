package com.crakac.localparty

import android.app.*
import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import com.crakac.localparty.encode.Chunk
import com.crakac.localparty.encode.ChunkType
import com.crakac.localparty.encode.Encoder
import com.crakac.localparty.encode.MyMediaRecorder
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class ScreenRecordService : MyMediaRecorder.RecorderCallback, Service() {
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var recorder: MyMediaRecorder
    private lateinit var projection: MediaProjection
    private lateinit var contentUri: Uri

    private lateinit var socket: Socket
    private var networkThread: Thread? = null

    private val queue = LinkedBlockingDeque<Chunk>()

    private fun connect(address: InetAddress, port: Int) {
        networkThread = thread {
            socket = Socket(address, port)
            socket.tcpNoDelay = true
            socket.getOutputStream().use { stream ->
                while (networkThread == Thread.currentThread()) {
                    if (queue.isEmpty()) {
                        Thread.sleep(10)
                        continue
                    }
                    try {
                        val data = queue.poll() ?: continue
                        stream.write(data.toByteArray())
                    } catch (e: IOException) {
                        Log.w(TAG, e.stackTraceToString())
                        break
                    }
                }
            }
            socket.close()
        }
    }

    override fun onRecorded(data: ByteArray, presentationTimeUs: Long, type: Encoder.Type) {
        val chunkType = when (type) {
            Encoder.Type.Video -> ChunkType.Video
            Encoder.Type.Audio -> ChunkType.Audio
        }
        queue.offer(Chunk(chunkType, data.size, presentationTimeUs, data))
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            throw IllegalArgumentException("intent is null")
        }
        val data = intent.getParcelableExtra<Intent>(KEY_DATA)
            ?: throw IllegalArgumentException("data is not set")
        val address = intent.getSerializableExtra(KEY_ADDRESS) as InetAddress
        val port = intent.getIntExtra(KEY_PORT, 0)

        connect(address, port)

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
        val fd = contentResolver.openFileDescriptor(contentUri, "w")!!.fileDescriptor
        recorder = MyMediaRecorder(fd, width, height, this)
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
        recorder.release()
        projection.stop()
        virtualDisplay.release()
        networkThread = null

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
        private val TAG = ScreenRecordService::class.simpleName
        const val ACTION_STOP = "stop"
        private const val KEY_DATA = "data"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PORT = "port"
        private const val CHANNEL_ID = "LocalParty"
        private const val NOTIFICATION_ID = 1
        fun createIntent(context: Context, data: Intent, address: InetAddress, port: Int): Intent {
            return Intent(context, ScreenRecordService::class.java)
                .putExtra(KEY_DATA, data)
                .putExtra(KEY_ADDRESS, address)
                .putExtra(KEY_PORT, port)
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