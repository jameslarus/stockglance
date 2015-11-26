package com.moneydance.modules.features.stockglance;

import com.infinitekind.moneydance.model.*;
import com.moneydance.apps.md.view.HomePageView;

import java.util.*;
import java.text.*;
import java.awt.*;
import java.util.List;
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
        addTableToPanel(tablePanel, makeTable());
        tablePanel.revalidate();
        tablePanel.repaint();
    }

    // Called when the view should clean up everything.
    public void reset() {
        tablePanel.removeAll();
        tablePanel.revalidate();
        tablePanel.repaint();
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

    private final String[] names =  {"Symbol",     "Stock",      "Price",      "Change",     "Day%",       "Week%",      "Month%",     "Year%"};
    private final String[] types =  {"Text",       "Text",       "Currency",   "Currency",   "Percent",    "Percent",    "Percent",    "Percent"};
    private final Class[] classes = {String.class, String.class, Double.class, Double.class, Double.class, Double.class, Double.class, Double.class};

    private JTable makeTable() {
        Vector<String> columnNames = new Vector<>(Arrays.asList(names));
        Vector<Vector<Object>> data = getTableData(book);

        DefaultTableModel sortableTableModel = new DefaultTableModel(data, columnNames) {
            public Class<?> getColumnClass(int col) {
                return classes[col];
            }
        };

        JTable table = new JTable(sortableTableModel) {
            // Alternating color bands for table
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) //  Alternate row color
                    c.setBackground(row % 2 == 0 ? getBackground() : new Color(0xDCDCDC));
                return c;
            }
        };

        for (int i = 0; i < names.length; i++) {
            TableColumn col = table.getColumn(names[i]);
            DefaultTableCellRenderer renderer;
            switch (types[i]) {
                case "Text":
                    renderer = new DefaultTableCellRenderer();
                    renderer.setHorizontalAlignment(JLabel.LEFT);
                    break;

                case "Currency":
                    renderer = new Currency2Renderer();
                    renderer.setHorizontalAlignment(JLabel.RIGHT);
                    break;

                case "Percent":
                    renderer = new Percent2Renderer();
                    renderer.setHorizontalAlignment(JLabel.RIGHT);
                    break;

                default:
                    renderer = new DefaultTableCellRenderer();
                    break;
            }
            col.setCellRenderer(renderer);

            renderer = new HeaderRenderer();
            renderer.setHorizontalAlignment(JLabel.CENTER);
            col.setHeaderRenderer(renderer);
        }

        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0); // Default is sort by symbol

        return table;
    }

    private Vector<Vector<Object>> getTableData(AccountBook book) {
        CurrencyTable ct = book.getCurrencies();
        java.util.List<CurrencyType> currencies = ct.getAllCurrencies();

        GregorianCalendar cal = new GregorianCalendar();
        int today = makeDateInt(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, // Jan == 1
                cal.get(Calendar.DAY_OF_MONTH));
        Vector<Vector<Object>> table = new Vector<>();

        for (CurrencyType cur : currencies) {
            if (!cur.getHideInUI() && cur.getCurrencyType() == CurrencyType.Type.SECURITY) {
                Double price = priceOrNaN(cur, today, 0);
                Double price1 = priceOrNaN(cur, today, 1);
                Double price7 = priceOrNaN(cur, today, 7);
                Double price30 = priceOrNaN(cur, today, 30);
                Double price365 = priceOrNaN(cur, today, 365);
                Vector<Object> entry = new Vector<>();

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

    private Double priceOrNaN(CurrencyType cur, int date, int delta) {
        try {
            int backDate = backDays(date, delta);
            if (snapshotExistsForDate(cur, backDate))  {
                return 1.0 / cur.getUserRateByDateInt(backDate);
            } else {
                return Double.NaN;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return Double.NaN;
        }
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
        int daysPerYear = ((year % 4 == 0) && (year % 100 != 0)) ? 366 : 365;

        while (delta >= daysPerYear)
        {
            delta = delta - daysPerYear;
            year = year - 1;
            daysPerYear = ((year % 4 == 0) && (year % 100 != 0)) ? 366 : 365;
        }
        while (month > 0 && delta >= DaysInMonth[month - 1]) {
            delta = delta - DaysInMonth[month - 1];
            month = month - 1;
        }
        day = day - delta;
        return makeDateInt(year, month, day);
    }

    private boolean snapshotExistsForDate(CurrencyType cur, int date) {
        List<CurrencySnapshot> snapshots = cur.getSnapshots();
        for (CurrencySnapshot snap : snapshots) {
            if (snap.getDateInt() <= date) {
                return true;
            }
        }
        return snapshots.isEmpty(); // If no snapshots, use fixed rate; otherwise didn't find snapshot
    }

    // Render a currency with 2 digits after the decimal point. NaN is empty cell.
    // Negative values are red.
    static class Currency2Renderer extends DefaultTableCellRenderer {
        protected NumberFormat formatter;

        public Currency2Renderer() {
            super();
            formatter = NumberFormat.getCurrencyInstance();
            formatter.setMinimumFractionDigits(2);
        }

        public void setValue(Object value) {
            if (value == null) {
                setText("");
            } else if (Double.isNaN((Double) value)) {
                setText("");
            } else {
                setText(formatter.format(value));
                if ((Double) value < -0.001) {
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.BLACK);
                }
            }
        }
    }

    // Render a percentage with 2 digits after the decimal point. Conventions as Currency2Renderer
    static class Percent2Renderer extends Currency2Renderer {
        public Percent2Renderer() {
            super();
            formatter = NumberFormat.getPercentInstance();
            formatter.setMinimumFractionDigits(2);
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


