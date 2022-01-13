package com.crakac.localparty

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Rational
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import com.crakac.localparty.databinding.ActivityCaptureBinding
import com.crakac.localparty.encode.Chunk
import com.crakac.localparty.encode.ChunkType
import com.crakac.localparty.encode.MediaDecoder
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class CaptureActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private val TAG = CaptureActivity::class.simpleName
    }

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaSync: MediaDecoder? = null
    private lateinit var serverSocket: ServerSocket

    @Volatile
    private var networkThread: Thread? = null

    private fun listen() {
        networkThread = thread {
            fun isActive() = networkThread == Thread.currentThread()
            serverSocket.accept().use { socket ->
                Log.d(TAG, "socket accepted!")
                Log.d(TAG, "receive buffer size: ${socket.receiveBufferSize}")
                socket.getInputStream().use { stream ->
                    readChunkFromStream(stream, ::isActive)
                }
            }
            runCatching {
                serverSocket.close()
            }
            Log.d(TAG, "network thread stopped")
        }
    }

    private var connectionManager: ConnectionManager? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NearByConnectionService.NearByConnectionServiceBinder
            connectionManager = binder.getConnectionManager()
            connectionManager?.setCallback { stream ->
                networkThread = thread {
                    readChunkFromStream(
                        stream,
                        isActive = { networkThread == Thread.currentThread() })
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    @WorkerThread
    private fun readChunkFromStream(stream: InputStream, isActive: () -> Boolean) {
        val buffer = ByteArray(16 * 1024)
        var chunk: Chunk? = null
        var pending: ByteArray? = null
        while (isActive()) {
            try {
                val readBytes = stream.read(buffer)
                if (readBytes == -1) {
                    Log.d(TAG, "End of stream")
                    break
                }
                val source = if (pending != null) pending + buffer else buffer
                var position = 0
                val limit = readBytes + (pending?.size ?: 0)
                while (position < limit) {
                    if (chunk?.isPartial == true) {
                        position += chunk.append(source, position, limit)
                    } else {
                        // if source has not enough size to build a chunk, read next.
                        if (limit - position < Chunk.HeaderSize) {
                            pending = source.sliceArray(position until limit)
                            break
                        }
                        chunk = Chunk.fromByteArray(source, position, limit)
                        position += chunk.actualSize
                    }
                    pending = null
                    // read next stream
                    if (chunk.isPartial) break

                    when (chunk.type) {
                        ChunkType.VideoCSD -> {
                            mediaSync?.configureVideoCodec(chunk.data)
                        }
                        ChunkType.AudioCSD -> {
                            mediaSync?.configureAudioCodec(chunk.data)
                        }
                        ChunkType.Video -> {
                            mediaSync?.enqueueVideoData(
                                chunk.data,
                                chunk.presentationTimeUs
                            )
                        }
                        ChunkType.Audio -> {
                            mediaSync?.enqueueAudioData(
                                chunk.data,
                                chunk.presentationTimeUs
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            Toast.makeText(this@CaptureActivity, "Not permitted", Toast.LENGTH_SHORT)
                .show()
            finish()
            return@registerForActivityResult
        }
        mediaSync = MediaDecoder(
            binding.surface.holder.surface,
            binding.surface.width,
            binding.surface.height
        ).also {
            it.start()
        }

        serverSocket = ServerSocket()
        serverSocket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        val address = serverSocket.inetAddress
        val port = serverSocket.localPort
//        listen()

        startRecordingService(result.data!!, address, port)
        enterPictureInPictureMode(createPictureInPictureParams())
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?
    ) {
        if (isInPictureInPictureMode) {
            binding.stopButton.visibility = View.GONE
        } else {
            binding.stopButton.visibility = View.VISIBLE
        }
    }

    override fun onUserLeaveHint() {
        enterPictureInPictureMode(createPictureInPictureParams())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding = ActivityCaptureBinding.inflate(layoutInflater)
        binding.stopButton.setOnClickListener {
            stopRecording()
            finish()
        }
        binding.surface.holder.addCallback(this)
        setContentView(binding.root)
        projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())

        bindService(
            Intent(this, NearByConnectionService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        unbindService(serviceConnection)
        connectionManager?.setCallback(null)
    }

    private fun startRecordingService(data: Intent, address: InetAddress, port: Int) {
        val intent = ScreenRecordService.createIntent(this, data, address, port)
        startForegroundService(intent)
    }

    private fun stopRecording() {
        mediaSync?.stop()
        mediaSync?.release()
        mediaSync = null
        networkThread = null

        stopService(Intent(this, ScreenRecordService::class.java))
    }

    private fun createPictureInPictureParams(): PictureInPictureParams {
        return PictureInPictureParams.Builder()
            .setAspectRatio(
                Rational(
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels
                )
            )
            .build()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        stopRecording()
    }
}