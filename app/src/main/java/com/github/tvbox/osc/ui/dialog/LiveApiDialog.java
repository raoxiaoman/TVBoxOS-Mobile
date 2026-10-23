package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogInputSubsriptionBinding;
import com.github.tvbox.osc.databinding.DialogLiveApiBinding;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.live.LiveStore;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.CenterPopupView;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class LiveApiDialog extends CenterPopupView {

    public interface OnImportListener {
        void onImportLiveSource();
    }

    private com.github.tvbox.osc.databinding.DialogLiveApiBinding mBinding;
    private final OnImportListener importListener;

    public LiveApiDialog(@NonNull Context context) {
        this(context, null);
    }

    public LiveApiDialog(@NonNull Context context, OnImportListener importListener) {
        super(context);
        this.importListener = importListener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_live_api;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        mBinding = DialogLiveApiBinding.bind(getPopupImplView());
        String liveApi = Hawk.get(HawkConfig.LIVE_URL, "");
        updateEt(liveApi);

        mBinding.ivHistory.setOnClickListener(view -> {
            LiveStore store = LiveStore.get();
            List<LiveStore.Subscription> subscriptions = store.subscriptions();
            String[] names = new String[subscriptions.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = subscriptions.get(i).name;
            }
            new XPopup.Builder(getContext()).asBottomList("直播订阅", names,
                    (position, text) -> updateEt(subscriptions.get(position).url)).show();
        });

        mBinding.ivImport.setOnClickListener(view -> {
            if (importListener != null) dismissWith(importListener::onImportLiveSource);
        });

        mBinding.btnCancel.setOnClickListener(v -> dismiss());
        mBinding.btnConfirm.setOnClickListener(view -> {
            String newLive = mBinding.etUrl.getText().toString().trim();
            // Capture Live input into Settings & Live History (max 20)
            LiveStore store = LiveStore.get();
            store.select(store.upsert("", newLive).id);
            store.save();
            Hawk.put(HawkConfig.LIVE_URL, newLive);
            if (!newLive.isEmpty()) {
                ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>());
                if (!liveHistory.contains(newLive))
                    liveHistory.add(0, newLive);
                if (liveHistory.size() > 20)
                    liveHistory.remove(20);
                Hawk.put(HawkConfig.LIVE_HISTORY, liveHistory);
            }
            ToastUtils.showShort("设置成功");
            dismiss();
        });
    }

    private void updateEt(String text){
        mBinding.etUrl.setText(text);
        mBinding.etUrl.setSelection(text.length());
    }
}
