package app.mojiscope.Search

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Minimal replacement for the removed AsyncTask: [doInBackground] runs serially on a shared
 * background thread and [onPostExecute] is delivered on the main thread.
 */
abstract class BackgroundTask<Result>
{
    protected abstract fun doInBackground(): Result

    protected abstract fun onPostExecute(result: Result)

    fun execute()
    {
        EXECUTOR.execute {
            val result = doInBackground()
            MAIN_HANDLER.post { onPostExecute(result) }
        }
    }

    companion object
    {
        private val EXECUTOR: Executor = Executors.newSingleThreadExecutor()
        private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    }
}
