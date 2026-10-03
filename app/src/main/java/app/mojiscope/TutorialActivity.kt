package app.mojiscope

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.mojiscope.databinding.ActivityTutorialBinding
import com.google.android.material.tabs.TabLayoutMediator

class TutorialActivity : AppCompatActivity()
{
    private inner class SectionsPagerAdapter : FragmentStateAdapter(this)
    {
        override fun createFragment(position: Int): Fragment
        {
            if (position == 0)
            {
                return TutorialWelcomeFragment.newInstance()
            }
            if (position in 1..9)
            {
                return TutorialFragment.newInstance(position)
            }

            return TutorialEndFragment.newInstance()
        }

        override fun getItemCount(): Int
        {
            return 11
        }
    }

    private lateinit var mBinding: ActivityTutorialBinding

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        mBinding = ActivityTutorialBinding.inflate(layoutInflater)

        supportActionBar?.hide()
        setContentView(mBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        mBinding.container.adapter = SectionsPagerAdapter()
        mBinding.container.offscreenPageLimit = 1
        TabLayoutMediator(mBinding.tabIndicator, mBinding.container) { _, _ -> }.attach()
    }

    companion object
    {
        private val TAG = TutorialActivity::class.java.name
    }
}
