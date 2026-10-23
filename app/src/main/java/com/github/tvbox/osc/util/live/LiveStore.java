package com.github.tvbox.osc.util.live;

import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LiveStore {
    public static final class Subscription {
        public String id;
        public String name;
        public String url;
        public Subscription(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }
    }

    public static final class State {
        public boolean migrated;
        public String selected = "follow";
        public ArrayList<Subscription> subscriptions = new ArrayList<>();
        public Map<String, ArrayList<String>> favorites = new LinkedHashMap<>();
        public Map<String, ArrayList<String>> recent = new LinkedHashMap<>();
    }

    private final State state;

    public LiveStore(State state) {
        this.state = state;
        if (state.subscriptions.isEmpty()) {
            state.subscriptions.add(new Subscription("follow", "跟随点播订阅", ""));
        }
    }

    public static LiveStore get() {
        String json = Hawk.get(HawkConfig.LIVE_STATE, "");
        State state = json.isEmpty() ? new State() : new Gson().fromJson(json, State.class);
        LiveStore store = new LiveStore(state);
        store.migrate(Hawk.get(HawkConfig.LIVE_URL, ""), Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>()));
        // 设置页或旧版导入仍可能写入当前地址。
        String currentUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        if (!store.current().url.equals(currentUrl)) {
            store.select(store.upsert("", currentUrl).id);
        }
        store.save();
        return store;
    }

    public void save() {
        Hawk.put(HawkConfig.LIVE_STATE, new Gson().toJson(state));
    }

    public void migrate(String current, List<String> history) {
        if (state.migrated) {
            return;
        }
        Subscription selected = upsert("", current);
        for (String url : history) {
            upsert("", url);
        }
        state.selected = selected.id;
        state.migrated = true;
    }

    public List<Subscription> subscriptions() {
        return new ArrayList<>(state.subscriptions);
    }

    public Subscription current() {
        for (Subscription subscription : state.subscriptions) {
            if (subscription.id.equals(state.selected)) {
                return subscription;
            }
        }
        return state.subscriptions.get(0);
    }

    public Subscription upsert(String name, String url) {
        url = url.trim();
        for (Subscription subscription : state.subscriptions) {
            if (subscription.url.equals(url)) {
                if (!name.trim().isEmpty() && !url.isEmpty()) {
                    subscription.name = name.trim();
                }
                return subscription;
            }
        }
        Subscription subscription = new Subscription(UUID.randomUUID().toString(),
                name.trim().isEmpty() ? "直播订阅 " + state.subscriptions.size() : name.trim(), url);
        state.subscriptions.add(subscription);
        return subscription;
    }

    public void rename(String id, String name) {
        if (name.trim().isEmpty()) {
            return;
        }
        for (Subscription subscription : state.subscriptions) {
            if (subscription.id.equals(id) && !subscription.url.isEmpty()) {
                subscription.name = name.trim();
            }
        }
    }

    public void select(String id) {
        for (Subscription subscription : state.subscriptions) {
            if (subscription.id.equals(id)) {
                state.selected = id;
                return;
            }
        }
    }

    public String scope(String vodUrl) {
        return new Gson().toJson(new String[]{current().id, current().url.isEmpty() ? vodUrl : ""});
    }

    public List<String> favorites(String scope) {
        return new ArrayList<>(state.favorites.getOrDefault(scope, new ArrayList<>()));
    }

    public List<String> recent(String scope) {
        return new ArrayList<>(state.recent.getOrDefault(scope, new ArrayList<>()));
    }

    public String last(String scope) {
        List<String> recent = recent(scope);
        return recent.isEmpty() ? "" : recent.get(0);
    }

    public boolean toggleFavorite(String scope, String key) {
        ArrayList<String> favorites = state.favorites.computeIfAbsent(scope, ignored -> new ArrayList<>());
        if (favorites.remove(key)) {
            return false;
        }
        favorites.add(key);
        return true;
    }

    public void recordPlayed(String scope, String key) {
        ArrayList<String> recent = state.recent.computeIfAbsent(scope, ignored -> new ArrayList<>());
        recent.remove(key);
        recent.add(0, key);
        while (recent.size() > 30) {
            recent.remove(recent.size() - 1);
        }
    }
}
