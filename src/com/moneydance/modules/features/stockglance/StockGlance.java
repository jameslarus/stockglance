// StockGlance.java
//
// Copyright (c) 2015-2021, James Larus
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
import com.moneydance.apps.md.view.gui.MoneydanceGUI;
import com.moneydance.apps.md.view.gui.MoneydanceLAF;
import com.moneydance.apps.md.controller.AccountFilter;
import com.moneydance.apps.md.controller.FullAccountList;
import com.moneydance.apps.md.view.gui.select.AccountSelectList;
import com.moneydance.apps.md.view.resources.MDResourceProvider;
import com.moneydance.awt.GridC;

import java.util.*;
import java.text.*;

import static javax.swing.SwingConstants.LEFT;
import static javax.swing.SwingConstants.RIGHT;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.*;


// Home page component to display active stock prices and returns.

class StockGlance implements HomePageView {
    private MoneydanceGUI mdGUI;
    private AccountBook book;
    private SGTable table;
    private SGPanel tablePane;

    private boolean displayUnknownPrices = false;
    private boolean displayZeroShares = false;
    private int priceWindowSize = 7;

    private final CurrencyCallback currencyTableCallback = new CurrencyCallback(this);
    private final AccountCallback allAccountsCallback = new AccountCallback(this);
    private final CollapsibleRefresher refresher;

    // Per column metadata
    private final String[] names = {"Symbol", "Stock", "Price", "Change", "Balance", "Day", "7 Day", "30 Day", "365 Day"};
    private final Vector<String> columnNames = new Vector<>(Arrays.asList(names));
    private static final String TEXT_COL = "Text";
    private static final String CURR0_COL = "Currency0";
    private static final String CURR2_COL = "Currency2";
    private static final String PERCENT_COL = "Percent";
    private final String[] columnTypes = {TEXT_COL, TEXT_COL, CURR2_COL, CURR2_COL, CURR0_COL, PERCENT_COL, PERCENT_COL, PERCENT_COL, PERCENT_COL};
    static final int INFINITY = -1;


    StockGlance(MoneydanceGUI mdGUI) {
        this.mdGUI = mdGUI;
        this.table = null;
        this.tablePane = null;
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
                getPreferences();
                table = new SGTable(mdGUI, this, book, true);
                tablePane = new SGPanel(mdGUI, table);
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
            if (table != null) {
                table.recomputeModel(book, displayUnknownPrices, displayZeroShares, priceWindowSize);
            }
        }
        if (tablePane != null) {
            tablePane.setVisible(true);
            tablePane.revalidate();
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

    // Preference of which stocks are displayed in the table.
    private void getPreferences() {
        Account rootAccount = book.getRootAccount();
        displayUnknownPrices = rootAccount.getPreferenceBoolean("StockGlance_displayUnknownPrices", false);
        displayZeroShares = rootAccount.getPreferenceBoolean("StockGlance_displayZeroShares", false);
        priceWindowSize = rootAccount.getPreferenceInt("StockGlance_priceWindow", 7);
    }

    private void savePreferences() {
        Account rootAccount = book.getRootAccount();
        rootAccount.setPreference("StockGlance_displayUnknownPrices", displayUnknownPrices);
        rootAccount.setPreference("StockGlance_displayZeroShares", displayZeroShares);
        rootAccount.setPreference("StockGlance_priceWindow", priceWindowSize);
    }

    private boolean getUknownPrices() { return displayUnknownPrices; }

    private void setUnknownPrice(boolean flag) {
        displayUnknownPrices = flag;
        savePreferences();
    }

    private boolean getZeroShares() { return displayZeroShares; }

    private void setZeroShares(boolean flag) {
        displayZeroShares = flag;
        savePreferences();
    }

    private int getPriceWindow() { return priceWindowSize; }

    private void setPriceWindow(int value) {
        priceWindowSize = value;
        savePreferences();
    }

    
    //
    // Implementation and private classes.
    //

    private class SGTable extends JTable {
        transient MoneydanceGUI mdGUI;
        private transient StockGlance thisSG;
        private SGTable footerTable = null;

        SGTable(MoneydanceGUI mdGUI, StockGlance thisSG, AccountBook book, boolean isMainTable) {
            super();

            this.mdGUI = mdGUI;
            this.thisSG = thisSG;

            this.setForeground(mdGUI.getColors().registerTextFG);
            this.setBackground(mdGUI.getColors().registerBG1);
            
            // Body table
            SGTableModel tableModel = new SGTableModel(new Vector<>(), columnNames, new Vector<>());
            this.setModel(tableModel);
            fixColumnHeaders();
            setAutoCreateRowSorter(true);
            getRowSorter().toggleSortOrder(0); // Default: sort by symbol

            if (isMainTable) {
                // Footer table
                this.footerTable = new SGTable(mdGUI, thisSG, book, false);
                SGTableModel footerTableModel = new SGTableModel(new Vector<>(), columnNames, new Vector<>());
                footerTable.setModel(footerTableModel);

                // Link body and footer columns
                // http://stackoverflow.com/questions/2666758/issue-with-resizing-columns-in-a-double-jtable
                footerTable.setColumnModel(this.getColumnModel());
                this.getColumnModel().addColumnModelListener(footerTable);
                footerTable.getColumnModel().addColumnModelListener(this);

                recomputeModel(book, getUknownPrices(), getZeroShares(), getPriceWindow());
            }
        }

        public void recomputeModel(AccountBook book, boolean displayUnknownPrices, boolean displayZeroShares, int priceWindowSize) 
        {
            CurrencyTable ct = book.getCurrencies();
            java.util.List<CurrencyType> allCurrencies = ct.getAllCurrencies();
            Calendar today = Calendar.getInstance();

            SGTableModel model = this.getDataModel();
            Vector<CurrencyType> rowCurrencies = model.getRowCurrencies();      // Type of security in each row
            rowCurrencies.clear();
            Vector<Vector> data = model.getDataVector();
            data.clear();

            HashMap<CurrencyType, Long> balances = sumBalancesByCurrency(book);
            Double totalBalance = 0.0;
    
            for (CurrencyType curr : allCurrencies) {
                if (!curr.getHideInUI() && curr.getCurrencyType() == CurrencyType.Type.SECURITY) {
                    Double price = priceOrNaN(curr, today, 0, priceWindowSize);
                    Double price1 = priceOrNaN(curr, today, 1, priceWindowSize);
                    Double price7 = priceOrNaN(curr, today, 7, priceWindowSize);
                    Double price30 = priceOrNaN(curr, today, 30, priceWindowSize);
                    Double price365 = priceOrNaN(curr, today, 365, priceWindowSize);
    
                    if (displayUnknownPrices
                        || (!Double.isNaN(price)
                            && (!Double.isNaN(price1) || !Double.isNaN(price7) || !Double.isNaN(price30) || !Double.isNaN(price365)))) {
                        Vector<Object> entry = new Vector<>(names.length);
                        Long shares = balances.get(curr);
                        Double dShares = (shares == null) ? 0.0 : curr.getDoubleValue(shares) ;
    
                        if ((shares == null || shares == 0) && !displayZeroShares) {
                            continue;
                        }
                        totalBalance += dShares * 1.0 / curr.getBaseRate();
                        //System.err.println(curr.getName()+" ("+curr.getRelativeCurrency().getName()+") bal="+dShares+", baseRate="+1.0/curr.getBaseRate());
    
                        entry.add(curr.getTickerSymbol());
                        entry.add(curr.getName());
                        entry.add(price);
                        entry.add(price - price1);
                        entry.add(dShares * price);
                        entry.add((price - price1) / price1);
                        entry.add((price - price7) / price7);
                        entry.add((price - price30) / price30);
                        entry.add((price - price365) / price365);
    
                        data.add(entry);
                        rowCurrencies.add(curr);
                    }
                }
            }
            model.setDataVector(data, columnNames);
            fixColumnHeaders();

            SGTableModel footerModel = (SGTableModel)footerTable.getModel();
            Vector<Vector> footerData = footerModel.getDataVector();
            footerData.clear();
            Vector<Object> footerRow = new Vector<>();
            footerRow.add("Total");
            footerRow.add(null);
            footerRow.add(null);
            footerRow.add(null);
            footerRow.add(totalBalance);
            footerRow.add(null);
            footerRow.add(null);
            footerRow.add(null);
            footerRow.add(null);  
            footerData.add(footerRow);
            footerModel.setDataVector(footerData, columnNames);
        }

        private Double priceOrNaN(CurrencyType curr, Calendar date, int delta, int priceWindowSize) {
            try {
                int backDate = backDays(date, delta);
                if (haveSnapshotWithinWeek(curr, backDate, priceWindowSize)) {
                    double adjRate = curr.adjustRateForSplitsInt(backDate, curr.getRelativeRate(backDate));
                    //System.err.println(curr.getName()+" ("+curr.getRelativeCurrency().getName()+"): "+curr.getRelativeRate(backDate)+", "+adjRate);
                    return 1.0 / adjRate;
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

        // MD function getRelativeRate(int dt) returns last known rate, even if
        // price is far from the desired date DT. Return true if the snapshots
        // contain a rate within a window before the given date.
        private boolean haveSnapshotWithinWeek(CurrencyType curr, int date, int priceWindowSize) {
            List<CurrencySnapshot> snapshots = curr.getSnapshots();
            if (priceWindowSize == INFINITY) {
                return !snapshots.isEmpty();
            }
            for (CurrencySnapshot snap : snapshots) {
                if (DateUtil.calculateDaysBetween(snap.getDateInt(), date) <= priceWindowSize) {
                    return true;
                }
            }
            return false;
        }

        private HashMap<CurrencyType, Long> sumBalancesByCurrency(AccountBook book) {
            HashMap<CurrencyType, Long> totals = new HashMap<>();
            for (Account acct : AccountUtil.allMatchesForSearch(book, AcctFilter.ALL_ACCOUNTS_FILTER)) {
                CurrencyType curr = acct.getCurrencyType();
                Long total = totals.get(curr);
                total = ((total == null) ? 0L : total) + acct.getCurrentBalance();
                totals.put(curr, total);
            }
            return totals;
        }
    
        // Changing table data model changes headers, which erases their formatting.
        private void fixColumnHeaders() {
            TableColumnModel cm = getColumnModel();
            for (int i = 0; i < cm.getColumnCount(); i++) {
                TableColumn col = cm.getColumn(i);
                col.setHeaderRenderer(new HeaderRenderer(mdGUI));
            }
        }

        private JTable getFooterTable() {
            return footerTable;
        }

        private boolean getUknownPrices() { return thisSG.getUknownPrices(); }

        private void setUnknownPrice(boolean flag) {
            thisSG.setUnknownPrice(flag);
            thisSG.refresh();
        }

        private boolean getZeroShares() { return thisSG.getZeroShares(); }

        private void setZeroShares(boolean flag) {
            thisSG.setZeroShares(flag);
            thisSG.refresh();
        }
        
        private int getPriceWindow() { return thisSG.getPriceWindow(); }

        private void setPriceWindow(int value) {
            thisSG.setPriceWindow(value);
            thisSG.refresh();
        }

        private SGTableModel getDataModel() {
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
                case TEXT_COL:
                    renderer = new DefaultTableCellRenderer();
                    renderer.setHorizontalAlignment(LEFT);
                    break;

                case CURR0_COL:
                case CURR2_COL:
                    Vector<CurrencyType> rowCurrencies = getDataModel().getRowCurrencies();
                    CurrencyType curr;
                    if (0 <= row && row < rowCurrencies.size()) {
                        curr = rowCurrencies.get(row);              // Security
                    } else {
                        curr = book.getCurrencies().getBaseType(); // Footer reports base currency
                    }
                    renderer = new CurrencyRenderer(mdGUI, curr, columnTypes[column].equals(CURR0_COL));
                    renderer.setHorizontalAlignment(RIGHT);
                    break;

                case PERCENT_COL:
                    renderer = new PercentRenderer(mdGUI);
                    renderer.setHorizontalAlignment(RIGHT);
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

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            if (!isRowSelected(row))
                c.setBackground(row % 2 == 0 ? mdGUI.getColors().registerBG1
                                             : mdGUI.getColors().registerBG2);   // Banded rows
            return c;
        }
    }


    // TableModel
    private class SGTableModel extends DefaultTableModel {
        private final transient Vector<CurrencyType> rowCurrencies;

        SGTableModel(Vector<Vector<Object>> data, Vector<String> columnNames, Vector<CurrencyType> rowCurrencies) {
            super(data, columnNames);
            this.rowCurrencies = rowCurrencies;
        }

        // Need to define so columns are properly sorted, not treated as strings.
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch(columnTypes[columnIndex]) {
                case TEXT_COL:
                    return String.class;

                case CURR0_COL:
                case CURR2_COL:
                    return Double.class;

                case PERCENT_COL:
                    return Double.class;

                default:
                    return String.class;
            }
        }

        Vector<CurrencyType> getRowCurrencies() {
            return rowCurrencies;
        }
    }


    // JPanel
    private class SGPanel extends JPanel {
        transient MoneydanceGUI mdGUI;
        SGTable table;
        private JPanel configPanel;
        private AccountSelectList accountList;
        JFrame frame;

        SGPanel(MoneydanceGUI mdGUI, SGTable table) {
            super();
            this.mdGUI = mdGUI;
            this.table = table;

            JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.LINE_AXIS));

            header.setForeground(mdGUI.getColors().filterBarFG);
            header.setBackground(mdGUI.getColors().filterBarBG);
            JLabel label = new JLabel("StockGlance");
            label.setForeground(mdGUI.getColors().filterBarFG);
            JButton settings = new JButton("Edit");
            settings.setForeground(mdGUI.getColors().filterBarFG);
            settings.setBackground(mdGUI.getColors().filterBarBtnBG);
            header.add(label);
            header.add(Box.createHorizontalGlue());
            header.add(settings);
            header.add(Box.createHorizontalGlue());
            settings.addChangeListener(e -> openConfigPanel());

            this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            this.add(header);
            this.add(this.table.getTableHeader());
            this.add(this.table);
            this.add(this.table.getFooterTable());
            this.setBorder(BorderFactory.createCompoundBorder(MoneydanceLAF.homePageBorder, 
                                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
            
        }

        private void openConfigPanel() {
            this.configPanel = getConfigPanel();
            this.frame = new JFrame();
            this.frame.add(this.configPanel);
            this.frame.pack();
            this.frame.setVisible(true);
        }

        protected synchronized JPanel getConfigPanel() {
            if (this.configPanel != null) {
                return this.configPanel;
            } else {
                JPanel cPanel = new JPanel(new GridBagLayout());
                cPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                this.setupAccountSelector();

                cPanel.setForeground(mdGUI.getColors().defaultTextForeground);
                cPanel.setBackground(mdGUI.getColors().defaultBackground);

                JCheckBox unknownPriceCheckbox = new JCheckBox("Display unknown prices");
                unknownPriceCheckbox.setSelected(this.table.getUknownPrices());
                JCheckBox zeroSharesCheckbox = new JCheckBox("Display zero shares");
                zeroSharesCheckbox.setSelected(this.table.getZeroShares());
                JPanel checkboxPanel = new JPanel(new GridLayout(0, 1));
                checkboxPanel.add(unknownPriceCheckbox);
                checkboxPanel.add(zeroSharesCheckbox);

                unknownPriceCheckbox.addItemListener(e -> this.table.setUnknownPrice(unknownPriceCheckbox.isSelected()));
                zeroSharesCheckbox.addItemListener(e -> this.table.setZeroShares(zeroSharesCheckbox.isSelected()));

                JPanel sliderPanel = new JPanel(new GridLayout(0, 1));
                JLabel sliderLabel = new JLabel("Valid price window", javax.swing.SwingConstants.CENTER);
                sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                JSlider windowSlider = new JSlider(javax.swing.SwingConstants.HORIZONTAL, 1, 40, 7);
                windowSlider.setValue(window2lablel(this.table.getPriceWindow()));
                Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
                labelTable.put(slider_labels[0], new JLabel("day"));
                labelTable.put(slider_labels[1], new JLabel("week"));
                labelTable.put(slider_labels[2], new JLabel("month"));
                labelTable.put(slider_labels[3], new JLabel("year"));
                labelTable.put(slider_labels[4], new JLabel("\u221E"));
                windowSlider.setLabelTable(labelTable);
                windowSlider.setSnapToTicks(true);
                windowSlider.setPaintTicks(true);
                windowSlider.setPaintLabels(true);
                Dimension d = windowSlider.getPreferredSize();
                windowSlider.setPreferredSize(new Dimension((d.width * 2) / 1, d.height));
                sliderPanel.add(sliderLabel);
                sliderPanel.add(windowSlider);

                windowSlider.addChangeListener(e -> this.table.setPriceWindow(label2window(windowSlider.getValue())));

                int y = 0;
                cPanel.add(checkboxPanel, GridC.getc(1, y).field());
                cPanel.add(sliderPanel, GridC.getc(2, y++).field());
                cPanel.add(new JLabel("Accounts:"),GridC.getc(0, y++).label());
                this.accountList.layoutComponentUI();
                cPanel.add(this.accountList.getView(), GridC.getc(1, y).colspan(2).field().wxy(1.0F, 1.0F).fillboth());
                return cPanel;
            }
        }

        private void setupAccountSelector() {
            AccountFilter accountFilter = new AccountFilter("all_securities");
            accountFilter.addAllowedType(Account.AccountType.INVESTMENT);
            accountFilter.addAllowedType(Account.AccountType.SECURITY);
            FullAccountList fullAccountList = new FullAccountList(book, accountFilter, true);
            accountFilter.setFullList(fullAccountList);
            this.accountList = new AccountSelectList(this.mdGUI);
            this.accountList.setAccountFilter(accountFilter);
            this.accountList.setAutoSelectChildAccounts(true);
        }

        @Override
        public void removeAll() {
            if (this.configPanel != null) {
                this.configPanel.removeAll();
                this.configPanel = null;
            }
            super.removeAll();
        }


        // Sliders have a linear scale, but we want to allow a large range of days
        // (1..365). The numberic value of the slider will not correspond to the
        // days, but will be translated through this table.
        final int[] slider_labels = { 1, 7, 14, 30, 40 };
        final int[] date_windows = { 1, 7, 30, 365, INFINITY };

        private int window2lablel(int window) {
            for (int i = 0; i < date_windows.length; i++) {
                if (date_windows[i] == window) {
                    return slider_labels[i];
                }
            }
            return window;
        }

        private int label2window(int label) {
            for (int i = 0; i < slider_labels.length; i++) {
                if (slider_labels[i] == label) {
                    return date_windows[i];
                }
            }
            return label;
        }
    }



    // CurrencyListener
    private static class CurrencyCallback implements CurrencyListener {
        private final StockGlance thisSG;

        CurrencyCallback(StockGlance sg) {
            this.thisSG = sg;
        }

        public void currencyTableModified(CurrencyTable table) {
            thisSG.refresh();
        }
    }

    // AccountListener
    private static class AccountCallback implements AccountListener {
        private final StockGlance thisSG;

        AccountCallback(StockGlance sg) {
            this.thisSG = sg;
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

    // Renderers
    // Render a currency with given number of fractional digits. NaN or null is an empty cell.
    // Negative values are red.
    private static class CurrencyRenderer extends DefaultTableCellRenderer {
        private transient MoneydanceGUI mdGUI;
        private final boolean noDecimals;
        private final transient CurrencyType relativeTo;
        private final char decimalSeparator = '.'; // ToDo: Set from preferences (how?)
        private final NumberFormat noDecimalFormatter;


        CurrencyRenderer(MoneydanceGUI mdGUI, CurrencyType curr, boolean noDecimals) {
            super();
            this.mdGUI = mdGUI;
            this.noDecimals = noDecimals;
            this.relativeTo = curr.getRelativeCurrency();
            this.noDecimalFormatter = NumberFormat.getNumberInstance();
            this.noDecimalFormatter.setMinimumFractionDigits(0);
            this.noDecimalFormatter.setMaximumFractionDigits(0);
        }

        boolean isZero(Double value) {
            return Math.abs(value) < 0.01;
        }

        @Override
        public void setValue(Object value) {
            if (value == null) {
                setText("");
                return;
            }
            Double valueAsDouble = (Double)value;
            if (Double.isNaN(valueAsDouble)) {
                setText("");
                return;
            }

            if (isZero(valueAsDouble)) {
                valueAsDouble = 0.0;
            }
            if (noDecimals) {
                // MD format functions can't print comma-separated values without a decimal point so
                // we have to do it ourselves
                setText(relativeTo.getPrefix() + " " + noDecimalFormatter.format(valueAsDouble) + relativeTo.getSuffix());
            } else {
                setText(relativeTo.formatFancy(relativeTo.getLongValue(valueAsDouble), decimalSeparator));
            }
            if (valueAsDouble < 0.0) {
                setForeground(mdGUI.getColors().negativeBalFG);
            } else {
                setForeground(mdGUI.getColors().registerTextFG);
            }
        }
    }

    // Render a percentage with 2 digits after the decimal point. Conventions as CurrencyRenderer
    private static class PercentRenderer extends DefaultTableCellRenderer {
        private transient MoneydanceGUI mdGUI;
        private final char decimalSeparator = '.'; // ToDo: Set from preferences (how?)

        PercentRenderer(MoneydanceGUI mdGUI) {
            super();
            this.mdGUI = mdGUI;
        }

        private boolean isZero(Double value) {
            return Math.abs(value) < 0.0001;
        }

        @Override
        public void setValue(Object value) {
            if (value == null) {
                setText("");
                return;
            }
            Double valueAsDouble = (Double)value;
            if (Double.isNaN(valueAsDouble)) {
                setText("");
                return;
            }
            if (isZero(valueAsDouble)) {
                valueAsDouble = 0.0;
            }
            setText(StringUtils.formatPercentage(valueAsDouble, decimalSeparator) + "%");
            if (valueAsDouble < 0.0) {
                setForeground(mdGUI.getColors().negativeBalFG);
            } else {
                setForeground(mdGUI.getColors().registerTextFG);
            }
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer(MoneydanceGUI mdGUI) {
            super();
            setForeground(mdGUI.getColors().headerFG);
            setBackground(mdGUI.getColors().headerBG);
            setHorizontalAlignment(CENTER);
        }
    }
}