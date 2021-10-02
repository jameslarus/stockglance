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
import com.moneydance.awt.GridC;

import java.text.*;
import java.util.*;
import java.util.List;

import java.awt.*;
import java.awt.Component;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.*;

import static javax.swing.SwingConstants.LEFT;
import static javax.swing.SwingConstants.RIGHT;


// Home page component to display active stock prices and returns.

class StockGlance implements HomePageView {
    private MoneydanceGUI mdGUI;
    private AccountBook book;
    private SGTable table;
    private SGPanel tablePane;

    private String displayedSecuritiesList;         // Comma-separated list of security to display
    private boolean allowPartialPrices = false;     // Display even if not all prices are available
    private boolean displayZeroShares = false;      // Display even if no shares are owned
    private int timelySnapshotInterval = 7;         // Days to look back to find security price (-1 => infinity)

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
                table.recomputeModel(book, getDisplayedSecurities(), allowPartialPrices, displayZeroShares, timelySnapshotInterval);
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
        displayedSecuritiesList = rootAccount.getPreference("StockGlance_displayedSecurities", "");
        allowPartialPrices = rootAccount.getPreferenceBoolean("StockGlance_DisplayPartialPrices", false);
        displayZeroShares = rootAccount.getPreferenceBoolean("StockGlance_DisplayZeroShares", false);
        timelySnapshotInterval = rootAccount.getPreferenceInt("StockGlance_TimelyWindow", 7);
    }

    private void savePreferences() {
        Account rootAccount = book.getRootAccount();
        rootAccount.setPreference("StockGlance_displayedSecurities", displayedSecuritiesList);
        rootAccount.setPreference("StockGlance_DisplayPartialPrices", allowPartialPrices);
        rootAccount.setPreference("StockGlance_DisplayZeroShares", displayZeroShares);
        rootAccount.setPreference("StockGlance_TimelyWindow", timelySnapshotInterval);
    }

    public Set<String> getDisplayedSecurities() { 
        return decodeDisplayedSecurities(displayedSecuritiesList);
    }

    public void setDisplayedSecurities(Set<String> securities) {
        displayedSecuritiesList = encodeDisplayedSecurities(securities);
        savePreferences();
    }

    private static final String SECURITY_SEPARATOR = ", ";

    private String encodeDisplayedSecurities(Set<String> securities) {
        StringBuilder encoding = new StringBuilder("");
        for (String s : securities) {
            encoding.append(s + SECURITY_SEPARATOR);
        }
        return encoding.toString();
    }

    private Set<String> decodeDisplayedSecurities(String encodedSecurities) {
        Set<String> displayedSecurities = new HashSet<>();
        Collections.addAll(displayedSecurities, encodedSecurities.split(SECURITY_SEPARATOR));
        return displayedSecurities;
    }

    public boolean getAllowPartialPrices() {
        return allowPartialPrices;
    }

    public void setAllowPartialPrices(boolean flag) {
        allowPartialPrices = flag;
        savePreferences();
    }

    public boolean getZeroShares() { return displayZeroShares; }

    public void setZeroShares(boolean flag) {
        displayZeroShares = flag;
        savePreferences();
    }

    public int getTimelySnapshotInterval() { return timelySnapshotInterval; }

    public void setTimelySnapshotInterval(int value) {
        timelySnapshotInterval = value;
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

                recomputeModel(book, getDisplayedSecurities(), getAllowPartialPrices(), getZeroShares(), getTimelySnapshotInterval());
            }
        }

        public void recomputeModel(AccountBook book, Set<String> displayedSecurities, boolean allowPartialPrices, boolean displayZeroShares, int timelySnapshotInterval) 
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
                if (!curr.getHideInUI()
                    && curr.getCurrencyType() == CurrencyType.Type.SECURITY
                    && (displayedSecurities != null && displayedSecurities.contains(curr.getName()))) {
                    Double price = timelyPriceOrNaN(curr, today, 0, timelySnapshotInterval);
                    Double price1 = timelyPriceOrNaN(curr, today, 1, timelySnapshotInterval);
                    Double price7 = timelyPriceOrNaN(curr, today, 7, timelySnapshotInterval);
                    Double price30 = timelyPriceOrNaN(curr, today, 30, timelySnapshotInterval);
                    Double price365 = timelyPriceOrNaN(curr, today, 365, timelySnapshotInterval);
    
                    if (allowPartialPrices
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

        private Double timelyPriceOrNaN(CurrencyType curr, Calendar date, int delta, int timelySnapshotInterval) {
            try {
                int backDate = backDays(date, delta);
                if (haveSnapshotWithinInterval(curr, backDate, timelySnapshotInterval)) {
                    double adjRate = curr.adjustRateForSplitsInt(backDate, curr.getRelativeRate(backDate));
                    //System.err.println(curr.getName()+" ("+backDate+ " d " + timelySnapshotInterval+") ("+curr.getRelativeCurrency().getName()+"): "+1.0/curr.getRelativeRate(backDate)+", "+1.0/adjRate);
                    return 1.0 / adjRate;
                } else {
                    //System.err.println(curr.getName()+" ("+backDate+ " d " + timelySnapshotInterval+") No snap");
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
        private boolean haveSnapshotWithinInterval(CurrencyType curr, int date, int timelySnapshotInterval) {
            List<CurrencySnapshot> snapshots = curr.getSnapshots();
            if (timelySnapshotInterval == INFINITY) {
                return !snapshots.isEmpty();
            }
            for (CurrencySnapshot snap : snapshots) {
                int daysBetween = date - snap.getDateInt();  // > 0 => snap before date
                if (0 <= daysBetween && daysBetween <= timelySnapshotInterval) {
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

        private Set<String> getDisplayedSecurities() {
            return thisSG.getDisplayedSecurities();
        }

        private void setDisplayedSecurities(Set<String>  securities) {
            thisSG.setDisplayedSecurities(securities);
            thisSG.refresh();
        }

        private boolean getAllowPartialPrices() { return thisSG.getAllowPartialPrices(); }

        private void setAllowPartialPrices(boolean flag) {
            thisSG.setAllowPartialPrices(flag);
            thisSG.refresh();
        }

        private boolean getZeroShares() { return thisSG.getZeroShares(); }

        private void setZeroShares(boolean flag) {
            thisSG.setZeroShares(flag);
            thisSG.refresh();
        }
        
        private int getTimelySnapshotInterval() { return thisSG.getTimelySnapshotInterval(); }

        private void setTimelySnapshotInterval(int value) {
            thisSG.setTimelySnapshotInterval(value);
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
        private SGTable table;
        private JPanel configPanel;
        private JFrame frame;

        SGPanel(MoneydanceGUI mdGUI, SGTable table) {
            super();
            this.mdGUI = mdGUI;
            this.table = table;

            JPanel headerPanel = new JPanel();
            headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.LINE_AXIS));
            headerPanel.setForeground(mdGUI.getColors().filterBarFG);
            headerPanel.setBackground(mdGUI.getColors().filterBarBG);
            JLabel titleLabel = new JLabel("StockGlance");
            titleLabel.setForeground(mdGUI.getColors().filterBarFG);
            JButton editButton = new JButton("Edit");
            editButton.setForeground(mdGUI.getColors().filterBarFG);
            editButton.setBackground(mdGUI.getColors().filterBarBtnBG);
            headerPanel.add(titleLabel);
            headerPanel.add(Box.createHorizontalGlue());
            headerPanel.add(editButton);
            editButton.addActionListener(e -> openConfigPanel());

            this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            this.add(headerPanel);
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

                cPanel.setForeground(mdGUI.getColors().defaultTextForeground);
                cPanel.setBackground(mdGUI.getColors().defaultBackground);

                JCheckBox partialPriceCheckbox = new JCheckBox("Display securities without full price history");
                JCheckBox zeroSharesCheckbox = new JCheckBox("Display securities with zero shares");
                JPanel checkboxPanel = new JPanel(new GridLayout(0, 1));
                checkboxPanel.add(partialPriceCheckbox);
                checkboxPanel.add(zeroSharesCheckbox);

                JPanel sliderPanel = new JPanel(new GridLayout(0, 1));
                JLabel sliderLabel = new JLabel("Valid price window", javax.swing.SwingConstants.CENTER);
                sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                JSlider windowSlider = new JSlider(javax.swing.SwingConstants.HORIZONTAL, 1, 40, 7);
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

                CheckBoxList securitySelectionList = makeSecuritySelectionList(this.table.getDisplayedSecurities());
                JScrollPane listScroller = new JScrollPane(securitySelectionList);

                resetUI(securitySelectionList, partialPriceCheckbox, zeroSharesCheckbox, windowSlider);

                JPanel buttonPanel = new JPanel(new GridLayout(1, 0));
                JButton resetButton = new JButton("Reset");
                resetButton.addActionListener(e -> resetUI(securitySelectionList, partialPriceCheckbox, zeroSharesCheckbox, windowSlider));
                JButton cancelButton = new JButton("Cancel");
                cancelButton.addActionListener(e -> {
                    resetUI(securitySelectionList, partialPriceCheckbox, zeroSharesCheckbox, windowSlider);
                    this.frame.setVisible(false);
                });
                JButton okButton = new JButton("OK");
                okButton.addActionListener(e -> {
                    Set<String> selectedSecurities = securitySelectionList.getSelected();
                    this.table.setDisplayedSecurities(selectedSecurities);
                    this.table.setAllowPartialPrices(partialPriceCheckbox.isSelected());
                    this.table.setZeroShares(zeroSharesCheckbox.isSelected());
                    this.table.setTimelySnapshotInterval(label2window(windowSlider.getValue()));
                    this.frame.setVisible(false);
                });
                buttonPanel.add(resetButton);
                buttonPanel.add(Box.createHorizontalGlue());
                buttonPanel.add(cancelButton);
                buttonPanel.add(okButton);

                int y = 0;
                cPanel.add(checkboxPanel, GridC.getc(1, y).field());
                cPanel.add(sliderPanel, GridC.getc(2, y++).field());
                cPanel.add(new JLabel("Securities:"),GridC.getc(0, y++).label());
                cPanel.add(listScroller, GridC.getc(1, y++).colspan(2).field().wxy(1.0F, 1.0F).fillboth());
                cPanel.add(buttonPanel, GridC.getc(0, y).west().colspan(3).label());
                return cPanel;
            }
        }

        private CheckBoxList makeSecuritySelectionList(Set<String> displayedSecurities) {
            Vector<JCheckBox> securities = new Vector<>();
            for (CurrencyType curr : book.getCurrencies().getAllCurrencies()) {
                if (!curr.getHideInUI() && curr.getCurrencyType() == CurrencyType.Type.SECURITY) {
                    String securityName = curr.getName();
                    JCheckBox checkBox = new JCheckBox(securityName);
                    if (displayedSecurities.contains(securityName)) {
                        checkBox.setSelected(true);
                    }
                    securities.add(checkBox);
                }
            }
            securities.sort((JCheckBox b1, JCheckBox b2) -> b1.getText().compareTo(b2.getText()));
            return new CheckBoxList(securities);
        }
        
        private void resetUI(CheckBoxList securitySelectionList, JCheckBox partialPriceCheckbox, JCheckBox zeroSharesCheckbox, JSlider windowSlider) {
            securitySelectionList.setSelected(this.table.getDisplayedSecurities());
            partialPriceCheckbox.setSelected(this.table.getAllowPartialPrices());
            zeroSharesCheckbox.setSelected(this.table.getZeroShares());
            windowSlider.setValue(this.table.getTimelySnapshotInterval());
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

    // CheckBoxList
    class CheckBoxList extends JList<JCheckBox> {
        protected transient Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

        public CheckBoxList(Vector<JCheckBox> listData) {
            // https://stackoverflow.com/questions/19766/how-do-i-make-a-list-with-checkboxes-in-java-swing
            super(listData);
            setCellRenderer(new CellRenderer());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int index = locationToIndex(e.getPoint());
                    if (index != -1) {
                        JCheckBox checkbox = getModel().getElementAt(index);
                        checkbox.setSelected(!checkbox.isSelected());
                        repaint();
                    }
                }
            });
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        }

        public Set<String> getSelected() {
            Set<String> selectedCheckBoxes = new HashSet<>();
            ListModel<JCheckBox> model = this.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                JCheckBox element = model.getElementAt(i);
                if (element.isSelected()) {
                    selectedCheckBoxes.add(element.getText());
                }
            }
            return selectedCheckBoxes;
        }

        public void setSelected(Set<String> selectedEntries) {
            ListModel<JCheckBox> model = this.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                JCheckBox element = model.getElementAt(i);
                if (selectedEntries.contains(element.getText())) {
                    element.setSelected(true);
                }
            }

        }

        protected class CellRenderer implements ListCellRenderer<JCheckBox> {
            public Component getListCellRendererComponent(JList<? extends JCheckBox> list, JCheckBox value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JCheckBox checkbox = value;

                // Drawing checkbox, change the appearance here
                checkbox.setBackground(isSelected ? getSelectionBackground() : getBackground());
                checkbox.setForeground(isSelected ? getSelectionForeground() : getForeground());
                checkbox.setEnabled(isEnabled());
                checkbox.setFont(getFont());
                checkbox.setFocusPainted(false);
                checkbox.setBorderPainted(true);
                checkbox.setBorder(isSelected ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
                return checkbox;
            }
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