// StockGlance.java
//
// Copyright (c) 2015, James Larus
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without
//  modification, are permitted provided that the following conditions are
//  met:
//
//  1. Redistributions of source code must retain the above copyright
//  notice, this list of conditions and the following disclaimer.
//
//  2. Redistributions in binary form must reproduce the above copyright
//  notice, this list of conditions and the following disclaimer in the
//  documentation and/or other materials provided with the distribution.
//
//  3. Neither the name of the copyright holder nor the names of its
//  contributors may be used to endorse or promote products derived from
//  this software without specific prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
//  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
//  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
//  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
//  HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
//  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
//  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
//  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
//  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.moneydance.modules.features.stockglance;

import com.infinitekind.moneydance.model.*;
import com.moneydance.apps.md.view.HomePageView;
import com.moneydance.awt.CollapsibleRefresher;

import java.math.RoundingMode;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;


// Home page component to display active stock prices and returns.

public class StockGlance implements HomePageView {
    private JScrollPane tablePane;
    private AccountBook book;
    private currencyCallback currencyTableCallback;

    //
    // HomePageView interface:
    //

    // Returns a GUI component that provides a view of the info pane for the given data file.
    public javax.swing.JComponent getGUIView(AccountBook book) {
        this.book = book;
        tablePane = new JScrollPane(makeTable());
        return tablePane;
    }

    // Returns a unique identifier for this view.
    public String getID() {
        return "StockGlance";
    }


    private CollapsibleRefresher refresher = new CollapsibleRefresher(new Runnable() {
        @Override
        public void run() { reallyRefresh(); }
    });

    public void refresh() {
        refresher.enqueueRefresh();
    }

    // Forces a refresh of the information in the view.
    private void reallyRefresh() {
        tablePane.revalidate();
        tablePane.repaint();
    }
  
    // Called when the view should clean up everything.
    public void reset() {
        tablePane.removeAll();
        tablePane.revalidate();
        tablePane.repaint();
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
        currencyTableCallback = new currencyCallback(this);
    }


    // Per column metadata
    private final String[] names =  {"Symbol",     "Stock",      "Price",      "Change",     "Balance",     "% Day",      "% 7Day",     "% 30Day",    "% 365Day"};
    private final String[] types =  {"Text",       "Text",       "Currency2",  "Currency2",  "Currency0",    "Percent",    "Percent",    "Percent",    "Percent"};
    private final Class[] classes = {String.class, String.class, Double.class, Double.class, Double.class,  Double.class, Double.class, Double.class, Double.class};

    // Per row metadata
    private Vector<CurrencyType> securityCurrencies = new Vector<>(); // Type of security in each row

    private JTable makeTable() {
        Vector<String> columnNames = new Vector<>(Arrays.asList(names));
        Vector<Vector<Object>> data = getTableData(book);

        DefaultTableModel sortableTableModel = new DefaultTableModel(data, columnNames) {
            @Override
            public Class<?> getColumnClass(int col) {
                return classes[col];
            }
        };

        JTable table = new JTable(sortableTableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                // Alternating row color bands
                if (!isRowSelected(row))
                    c.setBackground(row % 2 == 0 ? getBackground() : new Color(0xDCDCDC));
                // Balance needs to be wider than other columns
                if (types[column].equals("Currency0")) {
                    int rendererWidth = c.getPreferredSize().width;
                    TableColumn tableColumn = getColumnModel().getColumn(column);
                    tableColumn.setMinWidth(Math.max(rendererWidth + getIntercellSpacing().width, tableColumn.getMinWidth()));
                }
                return c;
            }

            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            // Rendering depends on row (i.e. security's currency) as well as column
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                DefaultTableCellRenderer renderer;
                switch (types[column]) {
                    case "Text":
                        renderer = new DefaultTableCellRenderer();
                        renderer.setHorizontalAlignment(JLabel.LEFT);
                        break;

                    case "Currency0":
                    case "Currency2":
                        CurrencyType security = securityCurrencies.get(row);
                        CurrencyTable table = security.getTable();
                        CurrencyType curr = table.getBaseType();
                        renderer = new CurrencyRenderer(curr.getPrefix(), types[column].equals("Currency0") ? 0 : 2); // Currency symbol
                        renderer.setHorizontalAlignment(JLabel.RIGHT);
                        break;

                    case "Percent":
                        renderer = new PercentRenderer();
                        renderer.setHorizontalAlignment(JLabel.RIGHT);
                        break;

                    default:
                        throw new UnsupportedOperationException();
                }
                return renderer;
            }
        };

        for (String name: names) {
            TableColumn col = table.getColumn(name);
            HeaderRenderer renderer = new HeaderRenderer();
            renderer.setHorizontalAlignment(JLabel.CENTER);
            col.setHeaderRenderer(renderer);
        }

        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0); // Default is to sort by symbol
        table.setFillsViewportHeight(true);
        return table;
    }

    private Vector<Vector<Object>> getTableData(AccountBook book) {
        CurrencyTable ct = book.getCurrencies();
        java.util.List<CurrencyType> allCurrencies = ct.getAllCurrencies();

        GregorianCalendar cal = new GregorianCalendar();
        int today = makeDateInt(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, // Jan == 1
                cal.get(Calendar.DAY_OF_MONTH));
        Vector<Vector<Object>> table = new Vector<>();

        HashMap<CurrencyType, Long> balances = sumBalancesByCurrency(book);
        Double totalBalance = 0.0;

        for (CurrencyType curr : allCurrencies) {
            if (!curr.getHideInUI() && curr.getCurrencyType() == CurrencyType.Type.SECURITY) {
                Double price = priceOrNaN(curr, today, 0);
                Double price1 = priceOrNaN(curr, today, 1);
                Double price7 = priceOrNaN(curr, today, 7);
                Double price30 = priceOrNaN(curr, today, 30);
                Double price365 = priceOrNaN(curr, today, 365);

                if (!Double.isNaN(price) && (!Double.isNaN(price1) || !Double.isNaN(price7)
                        || !Double.isNaN(price30) || !Double.isNaN(price365))) {
                    Vector<Object> entry = new Vector<>();
                    Long bal = balances.get(curr);
                    Double balance = (bal == null) ? 0.0 : bal / 10000.0 * price;
                    totalBalance += balance;

                    entry.add(curr.getTickerSymbol());
                    entry.add(curr.getName());
                    entry.add(price);
                    entry.add(price - price1);
                    entry.add(balance);
                    entry.add((price - price1) / price1);
                    entry.add((price - price7) / price7);
                    entry.add((price - price30) / price30);
                    entry.add((price - price365) / price365);

                    table.add(entry);
                    securityCurrencies.add(curr);
                }
            }
        }
        Vector<Object> entry = new Vector<>();
        entry.add("\u03A3"); // Sigma (sorts after all letters in stock names)
        entry.add(null);entry.add(null); entry.add(null);
        entry.add(totalBalance);
        entry.add(null); entry.add(null); entry.add(null); entry.add(null);
        table.add(entry);

        // Add callback to refresh table when stock's price changes.
        ct.addCurrencyListener(currencyTableCallback);

        return table;
    }

    private Double priceOrNaN(CurrencyType curr, int date, int delta) {
        try {
            int backDate = backDays(date, delta);
            if (haveSnapshotWithinWeek(curr, backDate))  {
                return 1.0 / curr.getUserRateByDateInt(backDate);
            } else {
                return Double.NaN;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }

    // Date int is yyyymmdd
    private int makeDateInt(int year, int month, int day) {
        return year * 10000 + month * 100 + day;
    }

    private static int[] DaysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private boolean isLeapYear(int year) {return (year % 4 == 0) && (year % 100 != 0);}

    // Month starts at 1 (Jan), but 0 = Dec, -1 = Nov, ...
    private int daysInMonth(int month, int year) {
        if (month <= 0) {
            month += 12;
        }
        if (month == 2 && isLeapYear(year)) {
            return 29;
        } else {
            return DaysInMonth[month - 1];
        }
    }

    private int prevMonth(int month) { return month == 1 ? 12 : month - 1; }

    // Return the DateInt that is delta days before dateInt
    public int backDays(int dateInt, int delta) {
        int year = dateInt / 10000;
        int month = (dateInt / 100) % 100;
        int day = dateInt % 100;
        int daysPerYear = isLeapYear(year) ? 366 : 365;

        while (delta >= daysPerYear)
        {
            delta -= daysPerYear;
            year -= 1;
            daysPerYear = isLeapYear(year) ? 366 : 365;
        }
        while (delta >= daysInMonth(month - 1, year)) {
            delta = delta - daysInMonth(month - 1, year);
            month = month - 1;
            if (month == 0) {
                month = 12;
                year -= 1;
            }
        }
        day = day - delta;
        if (day <= 0) {
            month -= 1;
            if (month == 0) {
                month = 12;
                year -= 1;
            }
            day += daysInMonth(month, year);
        }

        return makeDateInt(year, month, day);
    }

    // MD function getRawRateByDateInt(int dt) returns last known value, even if wildly out of date.
    // Return true if the snapshots contain a rate within a week before the date.
    private boolean haveSnapshotWithinWeek(CurrencyType curr, int date) {
        List<CurrencySnapshot> snapshots = curr.getSnapshots();
        for (CurrencySnapshot snap : snapshots) {
            if (Math.abs(date - snap.getDateInt()) <= 7) { // within a week
                return true;
            }
        }
        return snapshots.isEmpty(); // If no snapshots, use fixed rate; otherwise didn't find snapshot
    }

    private HashMap<CurrencyType, Long> sumBalancesByCurrency(AccountBook book) {
        HashMap<CurrencyType, Long> totals = new HashMap<>();
        for (Account acct : AccountUtil.allMatchesForSearch(book.getRootAccount(), AcctFilter.ALL_ACCOUNTS_FILTER)) {
            CurrencyType curr = acct.getCurrencyType();
            Long total = totals.get(curr);
            total = ((total == null) ? 0L : total) + acct.getCurrentBalance();
            totals.put(curr, total);
        }
        return totals;
    }

    //
    // Private classes:
    //

    // CurrencyListener
    static private class currencyCallback implements CurrencyListener {
        private StockGlance thisSG;

        public currencyCallback(StockGlance sg) {
            thisSG = sg;
        }

        public void currencyTableModified(CurrencyTable table) {
            thisSG.refresh();
        }
    }

    // Render a currency with given number of fractional digits. NaN or null is an empty cell.
    // Negative values are red.
    static class CurrencyRenderer extends DefaultTableCellRenderer {
        protected NumberFormat formatter;
        private String prefix;

        public CurrencyRenderer(String prefix, int precision) {
            super();
            this.prefix = prefix;
            formatter = NumberFormat.getNumberInstance();
            formatter.setMinimumFractionDigits(precision);
            formatter.setMaximumFractionDigits(precision);
            formatter.setRoundingMode(RoundingMode.HALF_EVEN);
        }

        protected boolean isZero(Double value) {
            return Math.abs(value) < 0.01;
        }

        @Override
        public void setValue(Object value) {
            if (value == null) {
                setText("");
            } else if (Double.isNaN((Double)value)) {
                setText("");
            } else {
                if (isZero((Double)value)) {
                    value = 0.0;
                }
                setText(prefix + formatter.format(value));
                if ((Double) value < 0.0) {
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.BLACK);
                }
            }
        }
    }

    // Render a percentage with 2 digits after the decimal point. Conventions as CurrencyRenderer
    static private class PercentRenderer extends CurrencyRenderer {
        public PercentRenderer() {
            super("", 2);
            formatter = NumberFormat.getPercentInstance();
            formatter.setMinimumFractionDigits(2);
            formatter.setRoundingMode(RoundingMode.HALF_EVEN);
        }

        @Override protected boolean isZero(Double value) {
            return Math.abs(value) < 0.0001;
        }
    }

    static private class HeaderRenderer extends DefaultTableCellRenderer {
        public HeaderRenderer() {
            super();
        }

        @Override
        public void setValue(Object value) {
            super.setValue(value);
            setForeground(Color.WHITE);
            setBackground(Color.BLACK);
        }
    }
}
