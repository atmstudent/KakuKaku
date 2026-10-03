package app.mojiscope.Dictionary

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the import service is doing right now. The screen listens to this so that it can show the
 * progress again when you come back to it, however long you were away.
 */
object DictionaryImport
{
    data class Progress(val percent: Int, val entries: Int, val finishing: Boolean, val hasPercent: Boolean)

    /** Not null while an import is running */
    @Volatile
    var progress: Progress? = null
        private set

    /** The result of the last import, until the screen has shown it */
    @Volatile
    private var pendingMessage: String? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    val isRunning: Boolean get() = progress != null

    fun addListener(listener: () -> Unit)
    {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit)
    {
        listeners.remove(listener)
    }

    fun publish(progress: Progress)
    {
        this.progress = progress
        notifyListeners()
    }

    fun finish(message: String)
    {
        progress = null
        pendingMessage = message
        notifyListeners()
    }

    /** Returns the result message once; null if there is none or it has been shown */
    @Synchronized
    fun takeMessage(): String?
    {
        val message = pendingMessage
        pendingMessage = null
        return message
    }

    private fun notifyListeners()
    {
        mainHandler.post { listeners.forEach { it() } }
    }
}
