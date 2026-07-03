package com.bachatas4.android.runtime.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Surface
import com.winlator.alsaserver.ALSAClient
import com.winlator.alsaserver.ALSAClientConnectionHandler
import com.winlator.alsaserver.ALSARequestHandler
import com.winlator.xconnector.UnixSocketConfig
import com.winlator.xconnector.XConnectorEpoll
import com.winlator.xserver.ScreenInfo
import com.winlator.xserver.Window
import com.winlator.xserver.XClientConnectionHandler
import com.winlator.xserver.XClientRequestHandler
import com.winlator.xserver.XServer
import java.io.File
import java.nio.Buffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WinlatorEmbeddedXServer(
    context: Context,
    private val socketRoot: File,
    private val useAbstractXSocket: Boolean = false,
    private val xSocketPath: String = UnixSocketConfig.XSERVER_PATH,
    private val useSharedMemoryAudio: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : EmbeddedXServer {
    override val display: String = ":0"

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var session: Session? = null
    private var stopped = false

    override suspend fun start(surface: Surface, width: Int, height: Int) = mutex.withLock {
        require(width > 0 && height > 0) { "X server dimensions must be positive" }
        check(session == null && !stopped) { "Embedded X server already started or stopped" }
        check(surface.isValid) { "Display surface is invalid" }

        val xServer = XServer(ScreenInfo(width, height))
        val xConnector = XConnectorEpoll(
            when {
                useAbstractXSocket -> UnixSocketConfig.createAbstract(UnixSocketConfig.XSERVER_PATH)
                else -> UnixSocketConfig.create(socketRoot.path, xSocketPath)
            },
            XClientConnectionHandler(xServer),
            XClientRequestHandler(),
        ).apply {
            setInitialInputBufferCapacity(4096)
            setInitialOutputBufferCapacity(4096)
            setCanReceiveAncillaryMessages(true)
        }
        ALSAClient.assignFramesPerBuffer(appContext)
        val audioOptions = ALSAClient.Options().apply { useSharedMemory = useSharedMemoryAudio }
        val alsaConnector = XConnectorEpoll(
            UnixSocketConfig.create(socketRoot.path, UnixSocketConfig.ALSA_SERVER_PATH),
            ALSAClientConnectionHandler(audioOptions),
            ALSARequestHandler(),
        ).apply { setMultithreadedClients(true) }
        val renderer = SurfaceWindowRenderer(scope, surface, xServer)

        try {
            xConnector.start()
            alsaConnector.start()
            renderer.start()
            session = Session(xConnector, alsaConnector, renderer)
        } catch (error: Throwable) {
            renderer.stop()
            alsaConnector.destroy()
            xConnector.destroy()
            throw error
        }
    }

    override suspend fun resize(width: Int, height: Int) = mutex.withLock {
        require(width > 0 && height > 0) { "X server dimensions must be positive" }
        checkNotNull(session) { "Embedded X server is not running" }.renderer.resize(width, height)
    }

    override suspend fun stop() = mutex.withLock {
        if (stopped) return@withLock
        session?.let {
            it.renderer.stop()
            it.alsaConnector.destroy()
            it.xConnector.destroy()
        }
        session = null
        stopped = true
    }

    private data class Session(
        val xConnector: XConnectorEpoll,
        val alsaConnector: XConnectorEpoll,
        val renderer: SurfaceWindowRenderer,
    )
}

private class SurfaceWindowRenderer(
    private val scope: CoroutineScope,
    private val surface: Surface,
    private val xServer: XServer,
) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var renderJob: Job? = null
    private var targetWidth = xServer.screenInfo.width.toInt()
    private var targetHeight = xServer.screenInfo.height.toInt()

    fun start() {
        check(renderJob == null) { "Surface renderer already started" }
        renderJob = scope.launch {
            while (isActive && surface.isValid) {
                renderFrame()
                delay(16)
            }
        }
    }

    fun resize(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height
    }

    suspend fun stop() {
        renderJob?.cancelAndJoin()
        renderJob = null
    }

    private fun renderFrame() {
        var canvas: Canvas? = null
        try {
            canvas = surface.lockHardwareCanvas()
            canvas.drawColor(Color.BLACK)
            canvas.save()
            canvas.scale(
                targetWidth.toFloat() / xServer.screenInfo.width,
                targetHeight.toFloat() / xServer.screenInfo.height,
            )
            xServer.windowManager.rootWindow.children.forEach { drawWindow(canvas, it) }
            canvas.restore()
        } catch (_: IllegalArgumentException) {
            // Surface was replaced between isValid and lockHardwareCanvas.
        } finally {
            if (canvas != null) surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawWindow(canvas: Canvas, window: Window) {
        if (!window.isRenderable) return
        val drawable = window.content ?: return
        val data = drawable.data ?: return
        synchronized(drawable.renderLock) {
            val bitmap = Bitmap.createBitmap(drawable.width.toInt(), drawable.height.toInt(), Bitmap.Config.ARGB_8888)
            (data as Buffer).rewind()
            bitmap.copyPixelsFromBuffer(data)
            (data as Buffer).rewind()
            canvas.drawBitmap(bitmap, window.rootX.toFloat(), window.rootY.toFloat(), paint)
            bitmap.recycle()
        }
        window.children.forEach { drawWindow(canvas, it) }
    }
}
