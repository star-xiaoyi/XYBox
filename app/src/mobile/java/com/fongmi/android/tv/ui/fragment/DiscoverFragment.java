package com.fongmi.android.tv.ui.fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.fongmi.android.tv.R;
public class DiscoverFragment extends com.fongmi.android.tv.ui.base.BaseFragment {
    @Override protected androidx.viewbinding.ViewBinding getBinding(@NonNull LayoutInflater inflater,@Nullable ViewGroup container){
        LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(32,32,32,120);root.setBackgroundColor(requireContext().getColor(R.color.screen_background));
        ImageView image=new ImageView(requireContext());image.setImageResource(R.drawable.ic_nav_discover);image.setColorFilter(requireContext().getColor(R.color.text_secondary));int size=(int)(56*getResources().getDisplayMetrics().density);root.addView(image,new LinearLayout.LayoutParams(size,size));
        TextView title=new TextView(requireContext());title.setText("发现");title.setTextSize(26);title.setTextColor(requireContext().getColor(R.color.text_primary));title.setPadding(0,24,0,16);root.addView(title);
        TextView subtitle=new TextView(requireContext());subtitle.setText("新的观影灵感，即将与您见面");subtitle.setTextSize(14);subtitle.setTextColor(requireContext().getColor(R.color.text_secondary));root.addView(subtitle);return () -> root;
    }
}
