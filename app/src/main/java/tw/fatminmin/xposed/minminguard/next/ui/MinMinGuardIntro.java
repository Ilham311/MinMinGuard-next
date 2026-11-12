package tw.fatminmin.xposed.minminguard.next.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

import tw.fatminmin.xposed.minminguard.next.R;

/**
 * Pengenalan singkat saat aplikasi pertama kali dibuka.
 * <p>
 * Dulu memakai pustaka AppIntro v4.2.3 dari JitPack. Pustaka itu sudah tidak
 * dirawat dan menjadi satu-satunya alasan proyek ini memaksa Jetifier — setiap
 * library ditulis ulang bytecode-nya saat build, memperlambat semuanya, demi
 * satu layar berisi lima slide. Implementasi di sini hanya memakai ViewPager2
 * dan komponen Material yang sudah ada.
 */
public class MinMinGuardIntro extends AppCompatActivity
{
    private ViewPager2 mPager;
    private MaterialButton mNext;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        mPager = findViewById(R.id.intro_pager);
        mNext = findViewById(R.id.intro_next);

        mPager.setAdapter(new SlideAdapter());
        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback()
        {
            @Override
            public void onPageSelected(int position)
            {
                updateButton();
            }
        });

        findViewById(R.id.intro_skip).setOnClickListener(v -> finish());
        mNext.setOnClickListener(v -> {
            int next = mPager.getCurrentItem() + 1;
            if (next >= mPager.getAdapter().getItemCount())
            {
                finish();
            }
            else
            {
                mPager.setCurrentItem(next, true);
            }
        });

        updateButton();
    }

    private void updateButton()
    {
        int count = mPager.getAdapter() == null ? 0 : mPager.getAdapter().getItemCount();
        boolean last = mPager.getCurrentItem() >= count - 1;
        mNext.setText(last ? R.string.intro_done : R.string.intro_next);
    }

    /** Satu slide memakai layout yang sama; hanya isinya yang berbeda. */
    private static final class Slide
    {
        final int title;
        final int content;
        final int image;

        Slide(int title, int content, int image)
        {
            this.title = title;
            this.content = content;
            this.image = image;
        }
    }

    private static final Slide[] SLIDES = {
            new Slide(R.string.intro_scope_title, R.string.intro_scope_content, R.drawable.intro1),
            new Slide(R.string.intro_list_title, R.string.intro_list_content, R.drawable.intro2),
            new Slide(R.string.intro_tap_icon_title, R.string.intro_tap_icon_content, R.drawable.intro3),
            new Slide(R.string.intro_app_detail_title, R.string.intro_app_detail_content, R.drawable.intro4),
            new Slide(R.string.intro_url_filtering_title, R.string.intro_url_filtering_content, R.drawable.intro5),
    };

    private static final class SlideAdapter extends RecyclerView.Adapter<SlideHolder>
    {
        @NonNull
        @Override
        public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_intro_slide, parent, false);
            return new SlideHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SlideHolder holder, int position)
        {
            Slide slide = SLIDES[position];
            holder.title.setText(slide.title);
            holder.content.setText(slide.content);
            holder.image.setImageResource(slide.image);
        }

        @Override
        public int getItemCount()
        {
            return SLIDES.length;
        }
    }

    private static final class SlideHolder extends RecyclerView.ViewHolder
    {
        final ImageView image;
        final TextView title;
        final TextView content;

        SlideHolder(View itemView)
        {
            super(itemView);
            image = itemView.findViewById(R.id.intro_image);
            title = itemView.findViewById(R.id.intro_title);
            content = itemView.findViewById(R.id.intro_content);
        }
    }
}
