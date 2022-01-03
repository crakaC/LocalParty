package com.crakac.localparty

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.crakac.localparty.databinding.ActivityCaptureBinding
import com.crakac.localparty.encode.Chunk
import com.crakac.localparty.encode.ChunkType
import com.crakac.localparty.encode.MediaSync
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class CaptureActivity : AppCompatActivity() {

    companion object {
        private val TAG = CaptureActivity::class.simpleName
    }

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaSync: MediaSync
    private lateinit var serverSocket: ServerSocket

    @Volatile
    private var networkThread: Thread? = null

    private fun listen() {
        networkThread = thread {
            val socket = serverSocket.accept()
            Log.d(TAG, "socket accepted!")
            Log.d(TAG, "receive buffer size: ${socket.receiveBufferSize}")
            socket.getInputStream().use { stream ->
                val buffer = ByteArray(32 * 1024)
                var chunk: Chunk? = null
                while (networkThread == Thread.currentThread()) {
                    try {
                        val readBytes = stream.read(buffer)
                        var position = 0
                        while (position < readBytes) {
                            if (chunk?.isPartial == true) {
                                position += chunk.append(buffer, position)
                            } else {
                                chunk = Chunk.fromByteArray(buffer, position)
                                position += chunk.actualSize
                            }

                            // read next stream
                            if (chunk.isPartial) break

                            if (chunk.type == ChunkType.Audio) {
                                mediaSync.enqueueAudioData(chunk.data, chunk.presentationTimeUs)
                            } else {
                                mediaSync.enqueueVideoData(chunk.data, chunk.presentationTimeUs)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, e.stackTraceToString())
                    }
                }
            }
            socket.close()
            serverSocket.close()
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
        mediaSync =
            MediaSync(binding.surface.holder.surface, binding.surface.width, binding.surface.height)
        mediaSync.start()

        serverSocket = ServerSocket()
            .also {
                it.reuseAddress = true
            }
        serverSocket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        val address = serverSocket.inetAddress
        val port = serverSocket.localPort
        listen()

        startRecordingService(result.data!!, address, port)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding = ActivityCaptureBinding.inflate(layoutInflater)
        binding.stopButton.setOnClickListener {
            stopRecordingService()
            finish()
        }
        setContentView(binding.root)
        projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSync.stop()
        mediaSync.release()
        networkThread = null
    }

    private fun startRecordingService(data: Intent, address: InetAddress, port: Int) {
        val intent = ScreenRecordService.createIntent(this, data, address, port)
        startForegroundService(intent)
    }

    private fun stopRecordingService() {
        networkThread = null
        stopService(Intent(this, ScreenRecordService::class.java))
    }
}