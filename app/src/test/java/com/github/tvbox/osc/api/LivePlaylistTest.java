package com.github.tvbox.osc.api;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LivePlaylistTest {
    @Test public void emptyChannelsCannotBecomePlayableAndIndexesRemainContiguous() {
        JsonArray input = new Gson().fromJson("[{\"group\":\"empty\",\"channels\":[{\"name\":\"bad\",\"urls\":[]}]},{\"group\":\"valid\",\"channels\":[{\"name\":\"bad\",\"urls\":[\"\"]},{\"name\":\"good\",\"urls\":[\"https://example.org/live$HD\"]}]}]", JsonArray.class);
        List<LiveChannelGroup> groups = ApiConfig.parseLiveGroups(input);
        assertEquals(1, groups.size());
        assertEquals(0, groups.get(0).getGroupIndex());
        assertEquals(1, groups.get(0).getLiveChannels().size());
        assertEquals(0, groups.get(0).getLiveChannels().get(0).getChannelIndex());
        assertEquals("good", groups.get(0).getLiveChannels().get(0).getChannelName());
        assertEquals("HD", groups.get(0).getLiveChannels().get(0).getSourceName());
    }
    @Test public void parsingKeepsPasswordAndAlternateLines() {
        JsonArray input = new Gson().fromJson("[{\"group\":\"private_1234\",\"channels\":[{\"name\":\"one\",\"urls\":[\"https://example.org/1\",\"https://example.org/2\"]}]}]", JsonArray.class);
        List<LiveChannelGroup> groups = ApiConfig.parseLiveGroups(input);
        assertEquals("1234", groups.get(0).getGroupPassword());
        assertEquals(2, groups.get(0).getLiveChannels().get(0).getSourceNum());
    }
}
