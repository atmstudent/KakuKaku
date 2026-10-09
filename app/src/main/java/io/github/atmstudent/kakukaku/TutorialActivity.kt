package io.github.atmstudent.kakukaku

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.github.atmstudent.kakukaku.databinding.ActivityTutorialBinding
import io.github.atmstudent.kakukaku.databinding.ItemTutorialBinding

/**
 * A short tutorial: swipeable slides, each an annotated screenshot (made by tools/make_tutorial.py) with a title and
 * a text that explains the numbered frames
 */
class TutorialActivity : AppCompatActivity()
{
    private class Slide(@StringRes val title: Int, @StringRes val text: Int, @DrawableRes val image: Int)

    private val mSlides = listOf(
            Slide(R.string.tutorial_1_title, R.string.tutorial_1_text, R.drawable.tutorial_1),
            Slide(R.string.tutorial_2_title, R.string.tutorial_2_text, R.drawable.tutorial_2),
            Slide(R.string.tutorial_3_title, R.string.tutorial_3_text, R.drawable.tutorial_3),
            Slide(R.string.tutorial_4_title, R.string.tutorial_4_text, R.drawable.tutorial_4),
            Slide(R.string.tutorial_5_title, R.string.tutorial_5_text, R.drawable.tutorial_5),
            Slide(R.string.tutorial_6_title, R.string.tutorial_6_text, R.drawable.tutorial_6),
            Slide(R.string.tutorial_7_title, R.string.tutorial_7_text, R.drawable.tutorial_7))

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

        mBinding.toolbar.setNavigationOnClickListener { finish() }
        mBinding.tutorialSkip.setOnClickListener { finish() }

        mBinding.pager.adapter = SlideAdapter()
        val dotSize = (8 * resources.displayMetrics.density).toInt()
        for (i in mSlides.indices)
        {
            val dot = View(this)
            dot.setBackgroundResource(R.drawable.tutorial_dot)
            dot.layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(dotSize / 2, 0, dotSize / 2, 0) }
            mBinding.dots.addView(dot)
        }

        mBinding.tutorialNext.setOnClickListener {
            if (mBinding.pager.currentItem == mSlides.size - 1) finish() else mBinding.pager.currentItem += 1
        }
        mBinding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback()
        {
            override fun onPageSelected(position: Int)
            {
                for (i in mSlides.indices) mBinding.dots.getChildAt(i).isSelected = i == position
                mBinding.tutorialNext.setText(if (position == mSlides.size - 1) R.string.tutorial_done else R.string.tutorial_next)
            }
        })
    }

    private inner class SlideAdapter : RecyclerView.Adapter<SlideAdapter.Holder>()
    {
        inner class Holder(val binding: ItemTutorialBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
                Holder(ItemTutorialBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int)
        {
            val slide = mSlides[position]
            holder.binding.tutorialSlideTitle.setText(slide.title)
            holder.binding.tutorialSlideText.setText(slide.text)
            holder.binding.tutorialSlideImage.setImageResource(slide.image)

            // The default shadow falls mostly below the card; only the ambient part of it spreads on all sides
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            {
                holder.binding.tutorialCard.outlineSpotShadowColor = Color.TRANSPARENT
                holder.binding.tutorialCard.outlineAmbientShadowColor = Color.BLACK
            }
        }

        override fun getItemCount() = mSlides.size
    }
}
