package com.fongmi.android.tv.ui.activity;
import com.github.catvod.utils.Logger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.CastMember;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCastWorksBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 演职人员作品列表页面
 * 展示某个演员或导演参与的所有影视作品
 */
public class CastWorksActivity extends BaseActivity implements VodAdapter.OnClickListener {
    
    private ActivityCastWorksBinding mBinding;
    private SiteViewModel mViewModel;
    private VodAdapter mAdapter;
    private ExecutorService mExecutor;
    private String mCastName;
    private CastMember.CastType mCastType;
    
    /**
     * 启动作品列表页面
     * @param activity 当前 Activity
     * @param name 演职人员名字
     * @param type 演职人员类型（演员或导演）
     */
    public static void start(Activity activity, String name, CastMember.CastType type) {
        Intent intent = new Intent(activity, CastWorksActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("type", type.name());
        activity.startActivity(intent);
    }
    
    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCastWorksBinding.inflate(getLayoutInflater());
    }
    
    @Override
    protected void initView(Bundle savedInstanceState) {
        // 获取传递的参数
        mCastName = getIntent().getStringExtra("name");
        String typeStr = getIntent().getStringExtra("type");
        mCastType = CastMember.CastType.valueOf(typeStr);
        
        // 设置标题
        String title = mCastType == CastMember.CastType.ACTOR 
            ? getString(R.string.cast_works_title_actor, mCastName)
            : getString(R.string.cast_works_title_director, mCastName);
        mBinding.toolbar.setTitle(title);
        
        // 设置返回按钮
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());
        
        // 设置菜单点击事件
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.home) {
                // 清除所有Activity栈，回到首页
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            }
            return false;
        });
        
        // 设置 RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        mBinding.recycler.setLayoutManager(layoutManager);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.recycler.setAdapter(mAdapter = new VodAdapter(this, Style.rect(), Product.getSpec(this)));
        
        // 设置 ViewModel
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.search.observe(this, this::setResult);
        
        // 开始搜索
        searchWorks();
    }
    
    @Override
    protected void initEvent() {
        // 事件已在 initView 中设置
    }
    
    /**
     * 搜索演职人员的作品
     */
    private void searchWorks() {
        // 停止之前的搜索
        stopSearch();
        
        // 创建线程池并发搜索
        mExecutor = Executors.newFixedThreadPool(20);
        for (Site site : VodConfig.get().getSites()) {
            if (site.isSearchable()) {
                mExecutor.execute(() -> search(site));
            }
        }
    }
    
    /**
     * 在指定视频源中搜索
     */
    private void search(Site site) {
        try {
            mViewModel.searchContent(site, mCastName, false);
        } catch (Throwable e) {
            Logger.e("Error", e);
        }
    }
    
    /**
     * 停止搜索
     */
    private void stopSearch() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }
    
    /**
     * 处理搜索结果
     */
    private void setResult(Result result) {
        if (result.getList().isEmpty()) return;
        runOnUiThread(() -> mAdapter.addAll(result.getList()));
    }
    
    /**
     * 作品点击事件
     * 跳转到视频详情页
     */
    @Override
    public void onItemClick(Vod item) {
        VideoActivity.start(
            this, 
            item.getSiteKey(), 
            item.getVodId(), 
            item.getVodName(), 
            item.getVodPic()
        );
    }
    
    /**
     * 作品长按事件
     */
    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearch();
    }
}
