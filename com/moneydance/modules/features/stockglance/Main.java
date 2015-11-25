package com.moneydance.modules.features.stockglance;

import com.moneydance.apps.md.controller.FeatureModule;
import com.moneydance.apps.md.controller.FeatureModule;


/**
 * Home page component to display active stock prices and returns.
 */

public class Main
        extends FeatureModule {
    private StockGlance glance;

    public void init() {
        try {
            glance = new StockGlance();
            getContext().registerHomePageView(this, glance);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void invoke(String uri) {
    }

    public String getName() {
        return "Stock Glance";
    }

    public void cleanup() {
        glance = null;
    }

    public void unload() {
        cleanup();
    }
}