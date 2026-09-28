package com.vermouthx.stocker.listeners;

import com.vermouthx.stocker.views.StockerTableView;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class StockerQuoteReloadListener implements StockerQuoteReloadNotifier {
    private final StockerTableView myTableView;

    public StockerQuoteReloadListener(StockerTableView myTableView) {
        this.myTableView = myTableView;
    }

    @Override
    public void clear() {
        if (SwingUtilities.isEventDispatchThread()) {
            clearRows();
        } else {
            SwingUtilities.invokeLater(this::clearRows);
        }
    }

    private void clearRows() {
        if (myTableView.isDisposed()) {
            return;
        }
        DefaultTableModel tableModel = myTableView.getTableModel();
        synchronized (tableModel) {
            tableModel.setRowCount(0);
            myTableView.reapplySortAfterDataChange();
        }
    }
}
