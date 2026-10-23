package com.github.tvbox.osc.util.live;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LiveCatalog {
    public static final class Channel {
        public final int groupIndex;
        public final int channelIndex;
        public final String key;
        public final String group;
        public final LiveChannelItem item;
        private Channel(LiveChannelGroup group, LiveChannelItem item) {
            groupIndex = group.getGroupIndex();
            channelIndex = item.getChannelIndex();
            this.group = group.getGroupName();
            key = key(this.group, item.getChannelName());
            this.item = item;
        }
    }

    public static String key(String group, String channel) {
        return new Gson().toJson(new String[]{group, channel});
    }

    public static List<Channel> channels(List<LiveChannelGroup> groups, Set<Integer> unlocked) {
        List<Channel> channels = new ArrayList<>();
        for (LiveChannelGroup group : groups) {
            if (!group.getGroupPassword().isEmpty() && !unlocked.contains(group.getGroupIndex())) {
                continue;
            }
            for (LiveChannelItem item : group.getLiveChannels()) {
                channels.add(new Channel(group, item));
            }
        }
        return channels;
    }

    public static List<Channel> filter(List<Channel> channels, String query, List<String> keys) {
        List<Channel> ordered = new ArrayList<>();
        if (keys == null) {
            ordered.addAll(channels);
        } else {
            Map<String, Channel> unique = new HashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            for (Channel channel : channels) {
                unique.put(channel.key, channel);
                counts.put(channel.key, counts.getOrDefault(channel.key, 0) + 1);
            }
            for (String key : keys) {
                if (counts.getOrDefault(key, 0) == 1) {
                    ordered.add(unique.get(key));
                }
            }
        }
        String search = query.trim().toLowerCase(Locale.ROOT);
        List<Channel> result = new ArrayList<>();
        for (Channel channel : ordered) {
            if (channel.item.getChannelName().toLowerCase(Locale.ROOT).contains(search)) {
                result.add(channel);
            }
        }
        return result;
    }
}
