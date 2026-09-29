package com.fongmi.android.tv.ui.activity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.viewbinding.ViewBinding;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityProfileSettingsBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
public class ProfileSettingsActivity extends BaseActivity {
    public static void start(Activity activity) { activity.startActivity(new Intent(activity, ProfileSettingsActivity.class)); }
    @Override protected ViewBinding getBinding() { return ActivityProfileSettingsBinding.inflate(getLayoutInflater()); }
    @Override protected void initView(Bundle state) {
        if (state == null) getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, SettingFragment.newInstance()).commit();
    }
    private SettingFragment settings() { return (SettingFragment) getSupportFragmentManager().findFragmentById(R.id.settings_container); }
    @Override protected boolean shouldInterceptBack() { return settings() != null && settings().isSearchActive(); }
    @Override protected void onBackPress() { if (settings() == null || !settings().closeSearchIfActive()) super.onBackPress(); }
}
