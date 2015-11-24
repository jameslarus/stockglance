package com.moneydance.modules.features.stockglance;

import com.infinitekind.moneydance.model.*;
import com.moneydance.apps.md.view.HomePageView;

import java.awt.geom.Arc2D;
import java.util.*;
import java.text.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;


// Home page component to display active stock prices and returns.

public class StockGlance implements HomePageView {
    private JPanel tablePanel;
    private AccountBook book;
    private currencyCallback currencyTableCallback;


    //
    // CurrencyListener Interface:
    //
    static class currencyCallback implements CurrencyListener {
        private StockGlance thisSG;

        public currencyCallback(StockGlance sg) {
            thisSG = sg;
        }

        public void currencyTableModified(CurrencyTable table) {
            thisSG.refresh();
        }
    }


    //
    // HomePageView interface:
    //

    // Returns a GUI component that provides a view of the info panel for the given data file.
    public javax.swing.JComponent getGUIView(AccountBook book) {
        this.book = book;

        addTableToPanel(tablePanel, makeTable());

        return tablePanel;
    }

    // Returns a unique identifier for this view.
    public String getID() {
        return "StockGlance";
    }

    // Forces a refresh of the information in the view.
    public void refresh() {
        tablePanel.removeAll();
        addTableToPanel(tablePanel, makeTable());
        tablePanel.invalidate();
    }

    // Called when the view should clean up everything.
    public void reset() {
        tablePanel.removeAll();
        if (book != null && currencyTableCallback != null) {
            book.getCurrencies().removeCurrencyListener(currencyTableCallback);
        }
    }

    // Sets the view as active or inactive.
    public void setActive(boolean active) {
        if (book != null) {
            if (active) {
                book.getCurrencies().addCurrencyListener(currencyTableCallback);
            } else {
                book.getCurrencies().removeCurrencyListener(currencyTableCallback);
            }
        }
    }

    // Returns a short descriptive name of this view.
    public String toString() {
        return "Stock Glance";
    }


    //
    // Implementation:
    //

    public StockGlance() {
        System.out.println("Start of StockGlance");

        book = null;
        tablePanel = new JPanel();
        currencyTableCallback = new currencyCallback(this);
    }

    private void addTableToPanel(JPanel tablePanel, JTable table) {
        table.setFillsViewportHeight(true);
        tablePanel.setLayout(new BorderLayout());
        tablePanel.add(table.getTableHeader(), BorderLayout.NORTH);
        tablePanel.add(table, BorderLayout.CENTER);
    }

    private JTable makeTable() {
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
        Vector<Vector<Object>> data = getTableData(book);
        JTable table = new JTable(data, columnNames) {
            // Alternating color bands for table
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);

                //  Alternate row color
                if (!isRowSelected(row))
                    c.setBackground(row % 2 == 0 ? getBackground() : new Color(0xDCDCDC));

                return c;
            }
        };

        for (int i = 0; i < names.length; i++) {
            TableColumn col = table.getColumn(names[i]);
            DefaultTableCellRenderer renderer;
            if (types[i].equals("Text")) {
                renderer = new DefaultTableCellRenderer();
                renderer.setHorizontalAlignment(JLabel.LEFT);
            } else if (types[i].equals("Currency")) {
                renderer = new CurrencyRenderer();
                renderer.setHorizontalAlignment(JLabel.RIGHT);
            } else if (types[i].equals("Percent")) {
                renderer = new PercentRenderer();
                renderer.setHorizontalAlignment(JLabel.RIGHT);
            } else {
                renderer = new DefaultTableCellRenderer();
            }
            col.setCellRenderer(renderer);

            renderer = new HeaderRenderer();
            renderer.setHorizontalAlignment(JLabel.CENTER);
            col.setHeaderRenderer(renderer);
        }

        return table;
    }

    private Vector<Vector<Object>> getTableData(AccountBook book) {
        CurrencyTable ct = book.getCurrencies();
        java.util.List<CurrencyType> currencies = ct.getAllCurrencies();

        GregorianCalendar cal = new GregorianCalendar();
        int today = makeDateInt(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, // Jan == 1
                cal.get(Calendar.DAY_OF_MONTH));
        Vector<Vector<Object>> table = new Vector<Vector<Object>>();

        for (CurrencyType cur : currencies) {
            if (!cur.getHideInUI() && cur.getCurrencyType() == CurrencyType.Type.SECURITY) {
                Double price = priceOrNaN(cur, today, 0);
                Double price1 = priceOrNaN(cur, today, 1);
                Double price7 = priceOrNaN(cur, today, 7);
                Double price30 = priceOrNaN(cur, today, 30);
                Double price365 = priceOrNaN(cur, today, 365);
                Vector<Object> entry = new Vector<Object>();

                entry.add(cur.getTickerSymbol());
                entry.add(cur.getName());
                entry.add(price);
                entry.add(price - price1);
                entry.add((price - price1) / price1);
                entry.add((price - price7) / price7);
                entry.add((price - price30) / price30);
                entry.add((price - price365) / price365);

                table.add(entry);
            }
        }

        // BUG: This causes Moneydance to hang when adding a stock price in History tab of Securities Detail Window
        // Add callback to refresh table when stock's price changes.
        //ct.addCurrencyListener(currencyTableCallback);

        return table;
    }

    // Date int is yyyyMMdd
    private int makeDateInt(int year, int month, int day) {
        return year * 10000 + month * 100 + day;
    }


    private static int[] DaysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private int backDays(int date, int delta) {
        int year = date / 10000;
        int month = (date / 100) % 100;
        int day = date % 100;

        while (delta >= 365)   // BUG: leap year
        {
            delta = delta - 365;
            year = year - 1;
        }
        while (month > 0 && delta >= DaysInMonth[month - 1]) {
            delta = delta - DaysInMonth[month - 1];
            month = month - 1;
        }
        day = day - delta;
        return makeDateInt(year, month, day);
    }

    private Double priceOrNaN(CurrencyType cur, int today, int delta) {
        try {
            return 1.0 / cur.getUserRateByDateInt(backDays(today, delta));
        } catch (ArrayIndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }

    static class CurrencyRenderer extends DefaultTableCellRenderer {
        NumberFormat formatter;

        public CurrencyRenderer() {
            super();
        }

        public void setValue(Object value) {
            if (formatter == null) {
                formatter = NumberFormat.getCurrencyInstance();
                formatter.setMinimumFractionDigits(2);
            }
            setText((value == null) ? "" : formatter.format(value));
            double num = Double.valueOf(value.toString());
            if (num < -0.001) {
                setForeground(Color.RED);
            } else {
                setForeground(Color.BLACK);
            }
        }
    }

    static class PercentRenderer extends DefaultTableCellRenderer {
        NumberFormat formatter;

        public PercentRenderer() {
            super();
        }

        public void setValue(Object value) {
            if (formatter == null) {
                formatter = NumberFormat.getPercentInstance();
                formatter.setMinimumFractionDigits(2);
            }
            if (value == null) {
                setText("");
            } else if (Double.isNaN((Double)value)){
                setText("");
            } else {
                setText(formatter.format(value));
                if ((Double)value < -0.001) {
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.BLACK);
                }
            }
        }
    }

    static class HeaderRenderer extends DefaultTableCellRenderer {
        public HeaderRenderer() {
            super();
        }

        public void setValue(Object value) {
            super.setValue(value);
            setForeground(Color.WHITE);
            setBackground(Color.BLACK);
        }
    }
}


