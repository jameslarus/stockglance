// StockGlance.java
//
// Copyright (c) 2015-16, James Larus
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
import com.infinitekind.util.DateUtil;
import com.infinitekind.util.StringUtils;
import com.moneydance.apps.md.view.HomePageView;
import com.moneydance.awt.CollapsibleRefresher;
import com.moneydance.apps.md.view.gui.MoneydanceLAF;

import java.util.*;
import java.text.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.*;


// Home page component to display active stock prices and returns.

class StockGlance implements HomePageView {
    private AccountBook book;
    private SGTable table;
    private SGPanel tablePane;
    private final currencyCallback currencyTableCallback = new currencyCallback(this);
    private final accountCallback allAccountsCallback = new accountCallback(this);
    private final CollapsibleRefresher refresher;
    private final Color lightLightGray = new Color(0xDCDCDC);

    // Per column metadata
    private final String[] names = {"Symbol", "Stock", "Price", "Change", "Balance", "Day", "7 Day", "30 Day", "365 Day"};
    private final Vector<String> columnNames = new Vector<>(Arrays.asList(names));
    private final String[] columnTypes = {"Text", "Text", "Currency2", "Currency2", "Currency0", "Percent", "Percent", "Percent", "Percent"};


    StockGlance() {
        this.refresher = new CollapsibleRefresher(StockGlance.this::actuallyRefresh);
    }


    //
    // HomePageView interface:
    //

    // Returns a unique identifier for this view.
    @Override
    public String getID() {
        return "StockGlance";
    }

    // Returns a short descriptive name of this view.
    @Override
    public String toString() {
        return "Stock Glance";
    }

    // Returns a GUI component that provides a view of the info pane for the given data file.
    @Override
    public javax.swing.JComponent getGUIView(AccountBook book) {
        synchronized (this) {
            if (tablePane == null) {
                this.book = book;
                SGTableModel tableModel = getTableModel(book);
                table = new SGTable(tableModel);
                tablePane = new SGPanel(table);
            }
            return tablePane;
        }
    }

    // Sets the view as active or inactive. When not active, a view should not have any registered listeners
    // with other parts of the program. This will be called when an view is added to the home page,
    // or the home page is refreshed after not being visible for a while.
    @Override
    public void setActive(boolean active) {
        if (book != null) {
            book.getCurrencies().removeCurrencyListener(currencyTableCallback); // At most one listener
            book.removeAccountListener(allAccountsCallback);
            if (active) {
                book.getCurrencies().addCurrencyListener(currencyTableCallback);
                book.addAccountListener(allAccountsCallback);
            }
        }
    }

    // Forces a refresh of the information in the view. For example, this is called after the preferences are updated.
    // Like the other home page controls, we actually do this lazily to avoid repeatedly recalculating after stock
    // price updates.
    @Override
    public void refresh() {
        refresher.enqueueRefresh();
    }

    // Actually recompute and redisplay table.
    private void actuallyRefresh() {
        synchronized (this) {
            TableModel tableModel = getTableModel(book);
            if (table != null) {
                table.setModel(tableModel);
                table.fixColumnHeaders();
            }
        }
        if (tablePane != null) {
            tablePane.setVisible(true);
            tablePane.validate();
        }
    }

    // Called when the view should clean up everything. For example, this is called when a file is closed and the GUI
    // is reset. The view should disconnect from any resources that are associated with the currently opened data file.
    @Override
    public void reset() {
        setActive(false);
        if (tablePane != null) {
            tablePane.removeAll();
        }
        tablePane = null;
        table = null;
    }


    //
    // Implementation:
    //

    private SGTableModel getTableModel(AccountBook book) {
        CurrencyTable ct = book.getCurrencies();
        java.util.List<CurrencyType> allCurrencies = ct.getAllCurrencies();
        final Vector<CurrencyType> rowCurrencies = new Vector<>(); // Type of security in each row
        Vector<Vector<Object>> data = new Vector<>();
        Calendar today = Calendar.getInstance();
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
                    Vector<Object> entry = new Vector<>(names.length);
                    Long bal = balances.get(curr);
                    Double balance = (bal == null) ? 0.0 : curr.getDoubleValue(bal) * price;
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

                    data.add(entry);
                    rowCurrencies.add(curr);
                }
            }
        }
        Vector<Object> footer = new Vector<>();
        footer.add("Total");
        footer.add(null);
        footer.add(null);
        footer.add(null);
        footer.add(totalBalance);
        footer.add(null);
        footer.add(null);
        footer.add(null);
        footer.add(null);

        return new SGTableModel(data, columnNames, rowCurrencies, footer);
    }

    private Double priceOrNaN(CurrencyType curr, Calendar date, int delta) {
        try {
            int backDate = backDays(date, delta);
            if (haveSnapshotWithinWeek(curr, backDate)) {
                    return 1.0 / curr.adjustRateForSplitsInt(backDate, curr.getUserRateByDateInt(backDate));
            } else {
                return Double.NaN;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return Double.NaN;
        }
    }

    // Return the date that is delta days before startDate
    private int backDays(Calendar startDate, int delta) {
        Calendar newDate = (Calendar) startDate.clone();
        newDate.add(Calendar.DAY_OF_MONTH, -delta);
        return DateUtil.convertCalToInt(newDate);
    }

    // MD function getRawRateByDateInt(int dt) returns last known value, even if wildly out of date.
    // Return true if the snapshots contain a rate within a week before the date.
    private boolean haveSnapshotWithinWeek(CurrencyType curr, int date) {
        List<CurrencySnapshot> snapshots = curr.getSnapshots();
        for (CurrencySnapshot snap : snapshots) {
            if (DateUtil.calculateDaysBetween(snap.getDateInt(), date) <= 7) { // within a week
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
        private final StockGlance thisSG;

        currencyCallback(StockGlance sg) {
            thisSG = sg;
        }

        public void currencyTableModified(CurrencyTable table) {
            thisSG.refresh();
        }
    }

    // AccountListener
    static private class accountCallback implements AccountListener {
        private final StockGlance thisSG;

        accountCallback(StockGlance sg) {
            thisSG = sg;
        }

        public void accountAdded(Account parentAccount, Account newAccount) {
            thisSG.refresh();
        }

        public void accountBalanceChanged(Account newAccount) {
            thisSG.refresh();
        }

        public void accountDeleted(Account parentAccount, Account newAccount) {
            thisSG.refresh();
        }

        public void accountModified(Account newAccount) {
            thisSG.refresh();
        }
    }

    // JPanel
    private class SGPanel extends JPanel {
        SGPanel(SGTable table) {
            super();
            this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            add(table.getTableHeader());
            add(table);
            add(table.getFooterTable());
            setBorder(BorderFactory.createCompoundBorder(MoneydanceLAF.homePageBorder, BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        }
    }

    // TableModel
    private class SGTableModel extends DefaultTableModel {
        private final Vector<CurrencyType> rowCurrencies;
        private final Vector<Object> footer;

        SGTableModel(Vector data, Vector columnNames, Vector<CurrencyType> rowCurrencies, Vector<Object> footer) {
            super(data, columnNames);
            this.rowCurrencies = rowCurrencies;
            this.footer = footer;
        }

        Vector<CurrencyType> getRowCurrencies() {
            return rowCurrencies;
        }

        Vector<Object> getFooterVector() {
            return footer;
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            if(columnIndex == 0 || columnIndex == 1) {
                return String.class;
            }
            else { return Double.class; }
        }
    }

    // JTable
    // Basic functional for tables that display StockGlance information
    private class BaseSGTable extends JTable {
        BaseSGTable(TableModel tableModel) {
            super(tableModel);
        }

        SGTableModel getDataModel() {
            return (SGTableModel) dataModel;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        // Rendering depends on row (i.e. security's currency) as well as column
        @Override
        public TableCellRenderer getCellRenderer(int row, int column) {
            DefaultTableCellRenderer renderer;
            switch (columnTypes[column]) {
                case "Text":
                    renderer = new DefaultTableCellRenderer();
                    renderer.setHorizontalAlignment(JLabel.LEFT);
                    break;

                case "Currency0":
                case "Currency2":
                    Vector<CurrencyType> rowCurrencies = getDataModel().getRowCurrencies();
                    CurrencyType curr;
                    if (0 <= row && row < rowCurrencies.size()) {
                        curr = rowCurrencies.get(row);
                    } else {
                        curr = book.getCurrencies().getBaseType(); // Footer reports base currency
                    }
                    renderer = new CurrencyRenderer(curr, columnTypes[column].equals("Currency0"));
                    renderer.setHorizontalAlignment(JLabel.RIGHT);
                    break;

                case "Percent":
                    renderer = new PercentRenderer();
                    renderer.setHorizontalAlignment(JLabel.RIGHT);
                    break;

                default:
                    renderer = new DefaultTableCellRenderer();
            }
            return renderer;
        }

        @Override
        public void columnMarginChanged(ChangeEvent event) {
            final TableColumnModel eventModel = (DefaultTableColumnModel) event.getSource();
            final TableColumnModel thisModel = this.getColumnModel();
            final int columnCount = eventModel.getColumnCount();

            for (int i = 0; i < columnCount; i++) {
                thisModel.getColumn(i).setWidth(eventModel.getColumn(i).getWidth());
            }
            repaint();
        }
    }

    private class SGTable extends BaseSGTable {
        private final JTable footerTable;

        SGTable(SGTableModel tableModel) {
            super(tableModel);
            fixColumnHeaders();
            setAutoCreateRowSorter(true);
            getRowSorter().toggleSortOrder(0); // Default: sort by symbol

            // Create footer table
            Vector<Object> footerData = new Vector<>();
            footerData.add(tableModel.getFooterVector());
            footerTable = new BaseSGTable(new SGTableModel(footerData, columnNames, new Vector<>(), new Vector<>()));

            // Link body and footer columns
            // http://stackoverflow.com/questions/2666758/issue-with-resizing-columns-in-a-double-jtable
            footerTable.setColumnModel(this.getColumnModel());
            this.getColumnModel().addColumnModelListener(footerTable);
            footerTable.getColumnModel().addColumnModelListener(this);
        }

        // Changing table data model changes headers, which erases their formatting.
        void fixColumnHeaders() {
            TableColumnModel cm = getColumnModel();
            for (int i = 0; i < cm.getColumnCount(); i++) {
                TableColumn col = cm.getColumn(i);
                col.setHeaderRenderer(new HeaderRenderer());
            }
        }

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            if (!isRowSelected(row))
                c.setBackground(row % 2 == 0 ? getBackground() : lightLightGray);   // Banded rows
            return c;
        }

        JTable getFooterTable() {
            return footerTable;
        }
    }

    // Render a currency with given number of fractional digits. NaN or null is an empty cell.
    // Negative values are red.
    static private class CurrencyRenderer extends DefaultTableCellRenderer {
        private final boolean noDecimals;
        private final CurrencyType relativeTo;
        private final char decimalSeparator = '.'; // ToDo: Set from preferences (how?)
        private final NumberFormat noDecimalFormatter;


        CurrencyRenderer(CurrencyType currency, boolean noDecimals) {
            super();
            this.noDecimals = noDecimals;
            CurrencyTable ct = currency.getTable();
            String relativeToName = currency.getParameter(CurrencyType.TAG_RELATIVE_TO_CURR);
            if (relativeToName != null) {
                relativeTo = ct.getCurrencyByIDString(relativeToName);
            } else {
                relativeTo = ct.getBaseType();
            }
            noDecimalFormatter = NumberFormat.getNumberInstance();
            noDecimalFormatter.setMinimumFractionDigits(0);
            noDecimalFormatter.setMaximumFractionDigits(0);
        }

        boolean isZero(Double value) {
            return Math.abs(value) < 0.01;
        }

        @Override
        public void setValue(Object value) {
            if (value == null) {
                setText("");
            } else if (Double.isNaN((Double) value)) {
                setText("");
            } else {
                if (isZero((Double) value)) {
                    value = 0.0;
                }
                if (noDecimals) {
                    // MD format functions can't print comma-separated values without a decimal point so
                    // we have to do it ourselves
                    final double scaledValue = (Double) value * relativeTo.getUserRate();
                    setText(relativeTo.getPrefix() + " " + noDecimalFormatter.format(scaledValue) + relativeTo.getSuffix());
                } else {
                    final long scaledValue = relativeTo.convertValue(relativeTo.getLongValue((Double) value));
                    setText(relativeTo.formatFancy(scaledValue, decimalSeparator));
                }
                if ((Double) value < 0.0) {
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.BLACK);
                }
            }
        }
    }

    // Render a percentage with 2 digits after the decimal point. Conventions as CurrencyRenderer
    static private class PercentRenderer extends DefaultTableCellRenderer {
        private final char decimalSeparator = '.'; // ToDo: Set from preferences (how?)

        PercentRenderer() {
            super();
        }

        private boolean isZero(Double value) {
            return Math.abs(value) < 0.0001;
        }

        @Override
        public void setValue(Object value) {
            if (value == null) {
                setText("");
            } else if (Double.isNaN((Double) value)) {
                setText("");
            } else {
                if (isZero((Double) value)) {
                    value = 0.0;
                }
                setText(StringUtils.formatPercentage((Double) value, decimalSeparator) + "%");
                if ((Double) value < 0.0) {
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.BLACK);
                }
            }
        }
    }

    static private class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            super();
            setForeground(Color.BLACK);
            setBackground(Color.lightGray);
            setHorizontalAlignment(JLabel.CENTER);
        }
    }
}
