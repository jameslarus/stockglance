package com.moneydance.modules.features.stockglanceextension;

import com.moneydance.apps.md.controller.FeatureModule;


/** Home page component to display active stock prices and returns.*/

public class Main
extends FeatureModule
{
    private StockGlance glance;

    public void init()
    {
        glance = new StockGlance();
        getContext().registerHomePageView(this, glance);
    }

    public void invoke(String uri)
    {
    }

    public String getName() 
    {
        return "Stock Glance";
    }

    public void cleanup()
    {
        glance.reset();
        glance = null;
    }

    public void unload()
    {
        cleanup();
    }
}