package app.mojiscope.Dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import app.mojiscope.MOJI_PREF_FILE
import app.mojiscope.MOJI_PREF_FIRST_LAUNCH
import app.mojiscope.MainActivity

class GrantPermissionDialogFragment : DialogFragment()
{
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog
    {
        return activity?.let {

            val builder = AlertDialog.Builder(it)

            builder.setTitle("Grant Mojiscope Permissions")
                    .setMessage("Mojiscope uses optical character recognition (OCR) to detect text from images and works by automatically taking screenshots of your screen when active. After granting permissions, please restart Mojiscope.\n\nMojiscope works completely offline and WILL NEVER transmit ANY user data encountered during usage.")
                    .setPositiveButton("GRANT")
                    {
                        _, _ ->
                        run {
                            val prefs = requireContext().getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE)
                            prefs.edit().putBoolean(MOJI_PREF_FIRST_LAUNCH, false).apply()

                            startActivity(Intent(activity, MainActivity::class.java))
                            (activity as FragmentActivity).finish()
                        }
                    }
                    .setNegativeButton("CANCEL")
                    {
                        _, _ ->
                        run {
                        }
                    }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
}