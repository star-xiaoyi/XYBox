package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.OnDeviceRegistryListener;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.event.ScanEvent;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.CastManager;
import com.fongmi.android.tv.utils.DLNADevice;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class CastDialog extends BaseCenterDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, OnDeviceRegistryListener, CastManager.Callback, okhttp3.Callback {

    private final FormBody.Builder body;
    private final OkHttpClient client;
    private final ScanTask scanTask;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private Listener listener;
    private CastVideo video;
    private boolean fm;

    private final Runnable mStopSearching = () -> {
        if (binding == null) return;
        binding.searching.setVisibility(View.GONE);
        checkEmpty();
    };

    public static CastDialog create() {
        return new CastDialog();
    }

    public CastDialog() {
        scanTask = new ScanTask(this);
        body = new FormBody.Builder();
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public CastDialog history(History history) {
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/")) fd = Server.get().getAddress() + "/file" + fd.replace(Path.rootPath(), "");
        if (fd.startsWith("file")) fd = Server.get().getAddress() + "/" + fd.replace(Path.rootPath(), "").replace("://", "");
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        body.add("history", history.toString().replace(id, fd));
        return this;
    }

    public CastDialog video(CastVideo video) {
        this.video = video;
        return this;
    }

    public CastDialog fm(boolean fm) {
        this.fm = fm;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DialogFragment && f.isAdded()) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm ? View.VISIBLE : View.GONE);
        EventBus.getDefault().register(this);
        setRecyclerView();
        getDevice();
        initDLNA();
        startSearching();
    }

    /**
     * DLNA 搜索没有"结束"回调，这里用一个固定时长的转圈表示正在找，
     * 超时就收起来，列表还空就给出提示。
     */
    private void startSearching() {
        binding.searching.setVisibility(View.VISIBLE);
        binding.empty.setVisibility(View.GONE);
        App.removeCallbacks(mStopSearching);
        App.post(mStopSearching, 8000);
    }

    private void checkEmpty() {
        boolean searching = binding.searching.getVisibility() == View.VISIBLE;
        binding.empty.setVisibility(adapter.getItemCount() == 0 && !searching ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                checkEmpty();
            }
        });
    }

    private void getDevice() {
        // 地址里的端口是服务起来才定的，没启动会拿到 -1，所以先把服务拉起来再算地址
        Server.get().start();
        // 浏览器不用搜，固定摆一条：用户没有电视时这是唯一能投的目标。
        // 没连 WiFi 时也照样摆出来，但换成一句说明——直接不显示的话用户只会
        // 纳闷投屏入口哪去了，不如告诉他为什么不能用。
        adapter.addAll(java.util.Collections.singletonList(Device.browser(getString(R.string.device_browser), CastManager.hasLan())));
        if (fm) adapter.addAll(Device.getAll());
        adapter.addAll(DLNADevice.get().getAll());
    }

    private void initDLNA() {
        CastManager.get().bind();
        DLNACastManager.INSTANCE.registerDeviceListener(this);
    }

    private void onScan() {
        ScanActivity.start(getActivity());
    }

    private void onRefresh() {
        if (fm) scanTask.start(adapter.getIps());
        DLNACastManager.INSTANCE.search(null);
        adapter.clear();
        getDevice();
        startSearching();
    }

    private void onCasted() {
        listener.onCasted();
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScanEvent(ScanEvent event) {
        scanTask.start(event.getAddress());
    }

    @Override
    public void onFind(List<Device> devices) {
        if (!devices.isEmpty()) adapter.addAll(devices);
    }

    @Override
    public void onDeviceAdded(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        adapter.addAll(DLNADevice.get().add(device));
    }

    @Override
    public void onDeviceRemoved(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        adapter.remove(DLNADevice.get().remove(device));
    }

    @Override
    public void onCastSuccess() {
        onCasted();
    }

    @Override
    public void onCastFailure(@NonNull String s) {
        Notify.show(s);
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> Notify.show(e.getMessage()));
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        if (response.body().string().equals("OK")) App.post(this::onCasted);
        else App.post(() -> Notify.show(R.string.device_offline));
    }

    @Override
    public void onItemClick(Device item) {
        if (item.isBrowser()) onBrowser(item);
        else if (item.isDLNA()) CastManager.get().connect(DLNADevice.get().find(item), item.getName(), video, this);
        else OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), body.build()).enqueue(this);
    }

    /**
     * 投到浏览器：先把内容准备好，再告诉用户去电脑上开哪个地址。
     * 顺序反过来的话，用户打开页面看到的会是"等待投屏"，白等一轮轮询。
     */
    private void onBrowser(Device item) {
        // 没连 WiFi 就没有能给电脑的地址，别让它进投屏态干等
        if (item.getIp().isEmpty()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getActivity())
                    .setTitle(R.string.device_browser_nolan)
                    .setMessage(R.string.device_browser_nolan_tip)
                    .setPositiveButton(R.string.dialog_positive, null)
                    .show();
            return;
        }
        CastManager.get().connectBrowser(item.getName(), video, null);
        String address = Server.get().getAddress() + "/pc";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.cast_browser_title)
                .setMessage(address + "\n\n" + getString(R.string.cast_browser_tip))
                .setNeutralButton(R.string.cast_browser_copy, (d, w) -> {
                    com.fongmi.android.tv.utils.Util.copy(address);
                    Notify.show(R.string.cast_browser_copied);
                })
                .setPositiveButton(R.string.dialog_positive, null)
                .setOnDismissListener(d -> onCasted())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.removeCallbacks(mStopSearching);
        EventBus.getDefault().unregister(this);
        DLNACastManager.INSTANCE.unregisterListener(this);
        // 不再 disconnect：投屏会话由 CastManager 持有，弹窗关掉连接要继续活着
        CastManager.get().unbind();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        scanTask.stop();
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    public interface Listener {

        void onCasted();
    }
}
