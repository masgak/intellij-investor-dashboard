package com.vermouthx.stocker.listeners;

import com.vermouthx.stocker.entities.StockerQuote;
import com.vermouthx.stocker.settings.StockerSetting;
import com.vermouthx.stocker.utils.StockerTableModelUtil;
import com.vermouthx.stocker.views.StockerTableView;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StockerQuoteUpdateListener implements StockerQuoteUpdateNotifier {
    private final StockerTableView myTableView;

    private static String formatCostPrice(Double costPrice) {
        return costPrice != null ? String.format("%.3f", costPrice) : "-";
    }

    private static Object formatHoldings(Integer holdings) {
        return holdings != null ? holdings : "-";
    }

    private static Object formatNetProfit(StockerQuote quote, Double costPrice, Integer holdings) {
        if (costPrice == null || holdings == null) {
            return "-";
        }
        return String.format("%.3f", (quote.getCurrent() - costPrice) * holdings);
    }

    public StockerQuoteUpdateListener(StockerTableView myTableView) {
        this.myTableView = myTableView;
    }

    @Override
    public void syncQuotes(List<StockerQuote> quotes, int size) {
        if (quotes == null) {
            return;
        }
        // Message bus may publish from the background scheduler; Swing models must update on EDT.
        if (SwingUtilities.isEventDispatchThread()) {
            applyQuoteUpdates(quotes, size);
        } else {
            SwingUtilities.invokeLater(() -> applyQuoteUpdates(quotes, size));
        }
    }

    private void applyQuoteUpdates(List<StockerQuote> quotes, int size) {
        if (myTableView.isDisposed()) {
            return;
        }
        DefaultTableModel tableModel = myTableView.getTableModel();
        StockerSetting setting = StockerSetting.Companion.getInstance();
        Map<String, Integer> rowIndexByCode = StockerTableModelUtil.buildCodeIndex(tableModel);
        boolean structureChanged = false;

        synchronized (tableModel) {
            for (StockerQuote quote : quotes) {
                String displayName = setting.getDisplayName(quote.getCode(), quote.getName());
                Integer rowIndexObj = rowIndexByCode.get(quote.getCode());
                if (rowIndexObj != null) {
                    int rowIndex = rowIndexObj;
                    setIfChanged(tableModel, rowIndex, 1, displayName);
                    setIfChanged(tableModel, rowIndex, 2, quote.getCurrent());
                    setIfChanged(tableModel, rowIndex, 3, quote.getOpening());
                    setIfChanged(tableModel, rowIndex, 4, quote.getClose());
                    setIfChanged(tableModel, rowIndex, 5, quote.getLow());
                    setIfChanged(tableModel, rowIndex, 6, quote.getHigh());
                    setIfChanged(tableModel, rowIndex, 7, quote.getChange());
                    setIfChanged(tableModel, rowIndex, 8, quote.getPercentage() + "%");

                    Double costPrice = setting.getCostPrice(quote.getCode());
                    Integer holdings = setting.getHoldings(quote.getCode());
                    setIfChanged(tableModel, rowIndex, 9, formatCostPrice(costPrice));
                    setIfChanged(tableModel, rowIndex, 10, formatHoldings(holdings));
                    setIfChanged(tableModel, rowIndex, 11, formatNetProfit(quote, costPrice, holdings));
                } else if (quotes.size() == size) {
                    Double costPrice = setting.getCostPrice(quote.getCode());
                    Integer holdings = setting.getHoldings(quote.getCode());
                    tableModel.addRow(new Object[]{
                            quote.getCode(),
                            displayName,
                            quote.getCurrent(),
                            quote.getOpening(),
                            quote.getClose(),
                            quote.getLow(),
                            quote.getHigh(),
                            quote.getChange(),
                            quote.getPercentage() + "%",
                            formatCostPrice(costPrice),
                            formatHoldings(holdings),
                            formatNetProfit(quote, costPrice, holdings)
                    });
                    rowIndexByCode.put(quote.getCode(), tableModel.getRowCount() - 1);
                    structureChanged = true;
                }
            }
        }

        if (structureChanged) {
            myTableView.reapplySortAfterDataChange();
        }
    }

    private static void setIfChanged(DefaultTableModel tableModel, int row, int column, Object newValue) {
        Object oldValue = tableModel.getValueAt(row, column);
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        tableModel.setValueAt(newValue, row, column);
    }

    @Override
    public void syncIndices(List<StockerQuote> indices) {
        if (indices == null) {
            return;
        }
        // syncIndices already hops to EDT inside StockerTableView.
        myTableView.syncIndices(indices);
    }
}
