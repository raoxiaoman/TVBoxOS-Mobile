package com.github.tvbox.osc.search;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.live.LiveCatalog;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LiveCatalogTest {
    private LiveChannelGroup group(int index, String name, String password) {
        LiveChannelGroup group = new LiveChannelGroup();
        group.setGroupIndex(index);
        group.setGroupName(name);
        group.setGroupPassword(password);
        LiveChannelItem channel = new LiveChannelItem();
        channel.setChannelName("Sport 1");
        channel.setChannelIndex(0);
        group.setLiveChannels(new ArrayList<>(Arrays.asList(channel)));
        return group;
    }
    @Test public void searchRetainsOriginalGroupAndHidesLockedChannels() {
        List<LiveChannelGroup> groups = Arrays.asList(group(0,"A",""), group(1,"B","secret"), group(2,"C",""));
        List<LiveCatalog.Channel> channels = LiveCatalog.channels(groups, Collections.emptySet());
        assertEquals(2, LiveCatalog.filter(channels, "SPORT", null).size());
        assertEquals(2, channels.get(1).groupIndex);
        assertEquals(0, channels.get(1).channelIndex);
        assertNotEquals(channels.get(0).key, channels.get(1).key);
    }
    @Test public void duplicateIdentityIsNotSilentlyRestored() {
        List<LiveCatalog.Channel> channels = LiveCatalog.channels(Arrays.asList(group(0,"A",""),group(1,"A","")), Collections.emptySet());
        assertEquals(0, LiveCatalog.filter(channels, "", Arrays.asList(LiveCatalog.key("A","Sport 1"))).size());
    }
    @Test public void recentOrderSurvivesPlaylistReordering() {
        List<LiveCatalog.Channel> channels = LiveCatalog.channels(Arrays.asList(group(0,"A",""),group(1,"B","")), Collections.emptySet());
        List<LiveCatalog.Channel> recent = LiveCatalog.filter(channels,"", Arrays.asList(LiveCatalog.key("B","Sport 1"),LiveCatalog.key("A","Sport 1")));
        assertEquals(1, recent.get(0).groupIndex);
        assertEquals(0, recent.get(1).groupIndex);
    }
}
