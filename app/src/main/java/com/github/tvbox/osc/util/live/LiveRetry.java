package com.github.tvbox.osc.util.live;

public final class LiveRetry {
    private int count;
    private int index;
    private int remaining;

    public void begin(int count, int selectedIndex) {
        this.count = count;
        index = selectedIndex;
        remaining = Math.max(0, count - 1);
    }

    public int next() {
        if (remaining == 0) {
            return -1;
        }
        remaining--;
        index = (index + 1) % count;
        return index;
    }

    public void played() {
        remaining = Math.max(0, count - 1);
    }
}
