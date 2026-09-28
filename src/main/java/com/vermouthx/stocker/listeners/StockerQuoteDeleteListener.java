package com.vermouthx.stocker.listeners;

import com.vermouthx.stocker.utils.StockerTableModelUtil;
import com.vermouthx.stocker.views.StockerTableView;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class StockerQuoteDeleteListener implements StockerQuoteDeleteNotifier {

    private final StockerTableView myTableView;

    public StockerQuoteDeleteListener(StockerTableView myTableView) {
        this.myTableView = myTableView;
    }

    @Override
    public void after(String code) {
        if (code == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            removeRow(code);
        } else {
            SwingUtilities.invokeLater(() -> removeRow(code));
        }
    }

    private void removeRow(String code) {
        if (myTableView.isDisposed()) {
            return;
        }
        DefaultTableModel tableModel = myTableView.getTableModel();
        synchronized (tableModel) {
            int rowIndex = StockerTableModelUtil.existAt(tableModel, code);
            if (rowIndex != -1) {
                tableModel.removeRow(rowIndex);
                myTableView.reapplySortAfterDataChange();
            }
        }
    }
}
