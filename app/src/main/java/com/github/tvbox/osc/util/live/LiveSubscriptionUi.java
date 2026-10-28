package com.github.tvbox.osc.util.live;

import android.content.Context;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播订阅的重命名/删除弹窗，直播页与设置页共用，保证两处操作口径一致。
 */
public final class LiveSubscriptionUi {

    public interface ChangeListener {
        /**
         * @param currentRemoved 是否删除了当前正在使用的订阅
         */
        void onChanged(boolean currentRemoved);
    }

    private LiveSubscriptionUi() {
    }

    public static void showRenameDialog(Context context, LiveStore store, ChangeListener listener) {
        LiveStore.Subscription current = store.current();
        if (current.url.isEmpty()) {
            ToastUtils.showShort("跟随点播订阅无需重命名");
            return;
        }
        EditText name = new EditText(context);
        name.setSingleLine(true);
        name.setText(current.name);
        new AlertDialog.Builder(context).setTitle("订阅名称").setView(name)
                .setPositiveButton("保存", (dialog, which) -> {
                    store.rename(current.id, name.getText().toString());
                    store.save();
                    if (listener != null) {
                        listener.onChanged(false);
                    }
                }).setNegativeButton("取消", null).show();
    }

    public static void showDeleteDialog(Context context, LiveStore store, ChangeListener listener) {
        List<LiveStore.Subscription> removable = new ArrayList<>();
        for (LiveStore.Subscription subscription : store.subscriptions()) {
            if (!"follow".equals(subscription.id)) {
                removable.add(subscription);
            }
        }
        if (removable.isEmpty()) {
            ToastUtils.showShort("没有可删除的直播订阅");
            return;
        }
        String currentId = store.current().id;
        String[] names = new String[removable.size()];
        boolean[] checked = new boolean[removable.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = removable.get(i).name
                    + (removable.get(i).id.equals(currentId) ? "（当前）" : "");
        }
        new AlertDialog.Builder(context).setTitle("勾选要删除的订阅")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean removedCurrent = false;
                    int count = 0;
                    // 倒序删除，避免索引在移除过程中错位
                    for (int i = removable.size() - 1; i >= 0; i--) {
                        if (!checked[i]) {
                            continue;
                        }
                        LiveStore.Subscription subscription = removable.get(i);
                        if (subscription.id.equals(currentId)) {
                            removedCurrent = true;
                        }
                        if (store.remove(subscription.id)) {
                            count++;
                        }
                    }
                    if (count == 0) {
                        ToastUtils.showShort("未选择订阅");
                        return;
                    }
                    store.save();
                    ToastUtils.showShort("已删除 " + count + " 个订阅");
                    if (removedCurrent) {
                        // 回退到跟随点播订阅
                        Hawk.put(HawkConfig.LIVE_URL, "");
                    }
                    if (listener != null) {
                        listener.onChanged(removedCurrent);
                    }
                }).setNegativeButton("取消", null).show();
    }
}
