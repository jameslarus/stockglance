package com.moneydance.modules.features.stockglanceextension;

import com.moneydance.apps.md.controller.FeatureModule;
import com.moneydance.apps.md.controller.FeatureModuleContext;
import com.moneydance.apps.md.controller.ModuleUtil;
import com.moneydance.apps.md.controller.UserPreferences;

import com.moneydance.apps.md.model.*;
import com.moneydance.apps.md.view.HomePageView;

import java.io.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;


// Home page component to display active stock prices and returns.

public class StockGlance implements HomePageView
{
    private JPanel tablePanel;
    private RootAccount root;


    //
    // CurrencyListener Interface:
    //
    static class currencyCallback implements CurrencyListener
    {
        private StockGlance thisSG;

        public currencyCallback(StockGlance sg)
        {
            thisSG = sg;
        }

        public void currencyTableModified(CurrencyTable table)
        {
            thisSG.refresh();
        }
    }

    private currencyCallback currencyTableCallback;


    //
    // HomePageView interface:
    //

    // Returns a GUI component that provides a view of the info panel for the given data file.
    public javax.swing.JComponent getGUIView(RootAccount rootAccount)
    {
        root = rootAccount;

        addTableToPanel(tablePanel, makeTable());

        return tablePanel;
    }

    // Returns a unique identifier for this view.
    public String getID()
    {
        return "StockGlance";
    }

    // Forces a refresh of the information in the view.
    public void refresh()
    {
        tablePanel.removeAll();
        addTableToPanel(tablePanel, makeTable());
        tablePanel.invalidate();
    }

    // Called when the view should clean up everything.
    public void reset()
    {
        tablePanel.removeAll();
        if (root != null && currencyTableCallback != null)
        {
            root.getCurrencyTable().removeCurrencyListener(currencyTableCallback);
        }
    }

    // Sets the view as active or inactive.
    public void setActive(boolean active)
    {
        if (root != null && active)
        {
            root.getCurrencyTable().addCurrencyListener(currencyTableCallback);
        }
        else if (root != null)
        {
            root.getCurrencyTable().removeCurrencyListener(currencyTableCallback);
        }
    }

    // Returns a short descriptive name of this view.
    public String toString()
    {
        return "Stock Glance";
    }


    //
    // Implementation:
    //

    public StockGlance()
    {
        root = null;
        tablePanel = new JPanel();
        currencyTableCallback = new currencyCallback(this);
    }


    private void addTableToPanel(JPanel tablePanel, JTable table)
    {
        table.setFillsViewportHeight(true);
        tablePanel.setLayout(new BorderLayout());
        tablePanel.add(table.getTableHeader(), BorderLayout.NORTH);
        tablePanel.add(table, BorderLayout.CENTER);
    }

    private JTable makeTable()
    {
        final String[] names = {"Symbol",
                                "Stock",
                                "Price",
                                "Change",
                                "Day %",
                                "Week %",
                                "Month %",
                                "Year %"};
        final String[] types = {"Text",
                                "Text",
                                "Currency",
                                "Currency",
                                "Percent",
                                "Percent",
                                "Percent",
                                "Percent"};

        Vector<Object> columnNames = new Vector<Object>(Arrays.asList(names));
        Vector<Vector<Object>> data = getTableData(root);
        JTable table = new JTable(data, columnNames)
            {
                // Alternating color bands for table
                public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
                {
                    Component c = super.prepareRenderer(renderer, row, column);

                    //  Alternate row color
                    if (!isRowSelected(row))
                        c.setBackground(row % 2 == 0 ? getBackground() : new Color(0xDCDCDC));

                    return c;
                }
            };

        for (int i = 0; i < names.length; i++)
        {
            TableColumn col = table.getColumn(names[i]);
            DefaultTableCellRenderer renderer;
            if (types[i] == "Text")
            {
                renderer = new DefaultTableCellRenderer();
                renderer.setHorizontalAlignment(JLabel.LEFT);
            }
            else if (types[i] == "Currency")
            {
                renderer = new CurrencyRenderer();
                renderer.setHorizontalAlignment(JLabel.RIGHT);
            }
            else if (types[i] == "Percent")
            {
                renderer = new PercentRenderer();
                renderer.setHorizontalAlignment(JLabel.RIGHT);
            }
            else
            {
                renderer = new DefaultTableCellRenderer();
            }
            col.setCellRenderer(renderer);

            renderer = new HeaderRenderer();
            renderer.setHorizontalAlignment(JLabel.CENTER);
            col.setHeaderRenderer(renderer);
        }

        return table;
    }

    private Vector<Vector<Object>> getTableData(RootAccount root)
    {
        CurrencyTable ct = root.getCurrencyTable();
        CurrencyType[] currencies = ct.getAllCurrencies();

        GregorianCalendar cal = new GregorianCalendar();
        int nowdi = makeDateInt(cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1, // Jan == 1
                                cal.get(Calendar.DAY_OF_MONTH));
        Vector<Vector<Object>> table = new Vector<Vector<Object>>();

        for (int i = 0; i < currencies.length; i++)
        {
            CurrencyType cur = currencies[i];
            if (!cur.getHideInUI() && cur.getCurrencyType() == CurrencyType.CURRTYPE_SECURITY)
            {
                int ssc = cur.getSnapshotCount();
                if (ssc > 0
                    && cur.getSnapshot(ssc - 1).getDateInt() > backDays(nowdi, 365)) //Has prices in last year
                {
                    double price = 1.0 / cur.getUserRate();//cur.getUserRateByDateInt(nowdi);
                    double price1 = 1.0 / cur.getUserRateByDateInt(backDays(nowdi, 1));
                    double price7 = 1.0 / cur.getUserRateByDateInt(backDays(nowdi, 7));
                    double price30 = 1.0 / cur.getUserRateByDateInt(backDays(nowdi, 30));
                    double price365 = 1.0 / cur.getUserRateByDateInt(backDays(nowdi, 365));
                    Vector<Object> entry = new Vector<Object>();
                    entry.add(cur.getTickerSymbol());
                    entry.add(cur.getName());
                    entry.add(price);
                    entry.add(price-price1);
                    entry.add((price-price1)/price1);
                    entry.add((price-price7)/price7);
                    entry.add((price-price30)/price30);
                    entry.add((price-price365)/price365);

                    table.add(entry);
                }
            }
        }

        // BUG: This causes Moneydance to hang when adding a stock price in History tab of Securities Detail Window
        // Add callback to refresh table when stock's price changes.
        //ct.addCurrencyListener(currencyTableCallback);

        return table;
    }

    // Date int is yyyyMMdd
    private int makeDateInt(int year, int month, int day)
    {
        return year * 10000 + month * 100 + day;
    }


    private static int[] DaysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private int backDays(int date, int delta)
    {
        int year = date / 10000;
        int month = (date / 100) % 100;
        int day = date % 100;

        while (delta >= 365)   // BUG: leap year
        {
            delta = delta - 365;
            year = year - 1;
        }
        while (month > 0 && delta >= DaysInMonth[month-1])
        {
            delta = delta - DaysInMonth[month-1];
            month = month - 1;
        }
        day = day - delta;
        return makeDateInt(year, month, day);
    }



    static class CurrencyRenderer extends DefaultTableCellRenderer
    {
        NumberFormat formatter;
        public CurrencyRenderer() { super(); }

        public void setValue(Object value) {
            if (formatter==null) {
                formatter = NumberFormat.getCurrencyInstance();
                formatter.setMinimumFractionDigits(2);
            }
            setText((value == null) ? "" : formatter.format(value));
            double num = Double.valueOf(value.toString());
            if (num < 0.0)
            {
                setForeground(Color.RED);
            }
            else
            {
                setForeground(Color.BLACK);
            }
        }
    }

    static class PercentRenderer extends DefaultTableCellRenderer
    {
        NumberFormat formatter;
        public PercentRenderer() { super(); }

        public void setValue(Object value) {
            if (formatter==null) {
                formatter = NumberFormat.getPercentInstance();
                formatter.setMinimumFractionDigits(2);
            }
            setText((value == null) ? "" : formatter.format(value));
            double num = Double.valueOf(value.toString());
            if (num < 0.0)
            {
                setForeground(Color.RED);
            }
            else
            {
                setForeground(Color.BLACK);
            }
        }
    }

    static class HeaderRenderer extends DefaultTableCellRenderer
    {
        public HeaderRenderer() { super(); }

        public void setValue(Object value) {
            super.setValue(value);
            setForeground(Color.WHITE);
            setBackground(Color.BLACK);
        }
    }
}


