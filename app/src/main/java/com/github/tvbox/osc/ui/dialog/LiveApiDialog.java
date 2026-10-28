package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogLiveApiBinding;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.live.LiveStore;
import com.github.tvbox.osc.util.live.LiveSubscriptionUi;
import com.lxj.xpopup.core.CenterPopupView;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置页「直播源」弹窗：订阅列表点选切换 + 手动输入 + 本地导入 + 重命名/删除，
 * 操作口径与直播页一致。
 */
public class LiveApiDialog extends CenterPopupView {

    public interface OnImportListener {
        void onImportLiveSource();
    }

    public interface OnSelectedListener {
        void onSelected(String url);
    }

    private DialogLiveApiBinding mBinding;
    private final OnImportListener importListener;
    private OnSelectedListener selectListener;

    public LiveApiDialog(@NonNull Context context) {
        this(context, null);
    }

    public LiveApiDialog(@NonNull Context context, OnImportListener importListener) {
        super(context);
        this.importListener = importListener;
    }

    public LiveApiDialog setOnSelectedListener(OnSelectedListener listener) {
        this.selectListener = listener;
        return this;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_live_api;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        mBinding = DialogLiveApiBinding.bind(getPopupImplView());
        updateEt(Hawk.get(HawkConfig.LIVE_URL, ""));

        mBinding.ivHistory.setOnClickListener(view -> { /* 已由订阅列表承担，保留占位 */ });
        mBinding.ivImport.setOnClickListener(view -> {
            if (importListener != null) dismissWith(importListener::onImportLiveSource);
        });
        mBinding.btnCancel.setOnClickListener(v -> dismiss());
        mBinding.btnConfirm.setOnClickListener(view -> applyUrl(mBinding.etUrl.getText().toString().trim(), true));
        mBinding.btnRename.setOnClickListener(v ->
                LiveSubscriptionUi.showRenameDialog(getContext(), LiveStore.get(), changed -> refreshSubs()));
        mBinding.btnDelete.setOnClickListener(v ->
                LiveSubscriptionUi.showDeleteDialog(getContext(), LiveStore.get(), removedCurrent -> {
                    if (removedCurrent) {
                        updateEt("");
                        notifySelected();
                    }
                    refreshSubs();
                }));
        mBinding.lvSubs.setOnItemClickListener((parent, view, position, id) -> {
            List<LiveStore.Subscription> subscriptions = LiveStore.get().subscriptions();
            if (position < 0 || position >= subscriptions.size()) {
                return;
            }
            applyUrl(subscriptions.get(position).url, false);
        });
        refreshSubs();
    }

    private void applyUrl(String url, boolean dismissAfter) {
        LiveStore store = LiveStore.get();
        if (url.isEmpty()) {
            store.select("follow");
        } else {
            store.select(store.upsert("", url).id);
        }
        store.save();
        Hawk.put(HawkConfig.LIVE_URL, url);
        if (!url.isEmpty()) {
            ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>());
            if (!liveHistory.contains(url)) {
                liveHistory.add(0, url);
            }
            if (liveHistory.size() > 20) {
                liveHistory.remove(20);
            }
            Hawk.put(HawkConfig.LIVE_HISTORY, liveHistory);
        }
        updateEt(url);
        refreshSubs();
        ToastUtils.showShort(url.isEmpty() ? "已切换为跟随点播订阅" : "直播源已切换");
        notifySelected();
        if (dismissAfter) {
            dismiss();
        }
    }

    private void notifySelected() {
        if (selectListener != null) {
            selectListener.onSelected(Hawk.get(HawkConfig.LIVE_URL, ""));
        }
    }

    private void refreshSubs() {
        LiveStore store = LiveStore.get();
        List<LiveStore.Subscription> subscriptions = store.subscriptions();
        List<String> names = new ArrayList<>();
        for (LiveStore.Subscription subscription : subscriptions) {
            names.add(subscription.name
                    + (subscription.id.equals(store.current().id) ? "（当前）" : ""));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_list_item_1, names) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(
                        getContext().getResources().getColor(R.color.text_foreground));
                return view;
            }
        };
        mBinding.lvSubs.setAdapter(adapter);
    }

    private void updateEt(String text) {
        mBinding.etUrl.setText(text);
        mBinding.etUrl.setSelection(text.length());
    }
}
