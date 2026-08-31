package com.github.catvod.crawler;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class JarLoaderClearContractTest {

    @Test
    public void subscriptionSwitchCanClearJarRuntimeWithoutLoadingAMainSpider() {
        JarLoader loader = new JarLoader();

        loader.clear();

        assertFalse(loader.isRuntimeReady(""));
    }
}
