package com.github.tvbox.osc.ui.adapter;

import android.widget.TextView;
import com.blankj.utilcode.util.ColorUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.live.LiveCatalog;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class LiveChannelItemNewAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private final Map<LiveChannelItem, LiveCatalog.Channel> references = new IdentityHashMap<>();
    private List<String> favorites = new ArrayList<>();
    private LiveChannelItem playing;

    public LiveChannelItemNewAdapter() {
        super(R.layout.item_live_channel_new, new ArrayList<>());
    }

    public void showChannels(List<LiveCatalog.Channel> channels, List<String> favorites, LiveChannelItem playing) {
        this.favorites = favorites;
        this.playing = playing;
        references.clear();
        List<LiveChannelItem> items = new ArrayList<>();
        for (LiveCatalog.Channel channel : channels) {
            references.put(channel.item, channel);
            items.add(channel.item);
        }
        setNewData(items);
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        LiveCatalog.Channel channel = references.get(item);
        TextView name = holder.getView(R.id.tvChannelName);
        name.setText(item.getChannelName());
        name.setTextColor(ColorUtils.getColor(item == playing ? R.color.white : R.color.text_foreground));
        holder.setText(R.id.live_channel_detail, (channel == null ? "" : channel.group + " · ") + item.getSourceNum() + " 条线路");
        holder.setText(R.id.live_favorite, channel != null && favorites.contains(channel.key) ? "已收藏" : "收藏");
        holder.getView(R.id.live_favorite).setContentDescription((channel != null && favorites.contains(channel.key) ? "取消收藏 " : "收藏 ") + item.getChannelName());
        holder.addOnClickListener(R.id.live_favorite);
        holder.getView(R.id.root).setBackgroundResource(item == playing ? R.drawable.bg_r_common_solid_primary : R.drawable.bg_transparent);
        TextView detail = holder.getView(R.id.live_channel_detail);
        detail.setTextColor(ColorUtils.getColor(item == playing ? R.color.white : R.color.text_sub_foreground));
        TextView favorite = holder.getView(R.id.live_favorite);
        favorite.setTextColor(ColorUtils.getColor(item == playing ? R.color.white : R.color.colorPrimary));
    }

}
