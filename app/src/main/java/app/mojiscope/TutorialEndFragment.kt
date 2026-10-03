package app.mojiscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class TutorialEndFragment : Fragment()
{
    private lateinit var rootView : View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        rootView = inflater.inflate(R.layout.fragment_end, container, false)

        val button = rootView.findViewById<Button>(R.id.tutorial_end_start_moji)

        button.setOnClickListener {
            requireActivity().finish()
        }

        return rootView
    }

    companion object
    {
        fun newInstance() : TutorialEndFragment {
            return TutorialEndFragment()
        }
    }
}