package com.vermouthx.stocker.utils;

import javax.swing.table.DefaultTableModel;
import java.util.HashMap;
import java.util.Map;

public final class StockerTableModelUtil {
    public static int existAt(DefaultTableModel tableModel, String code) {
        if (code == null) {
            return -1;
        }
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object value = tableModel.getValueAt(i, 0);
            if (value != null && code.equals(value.toString())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds a code → row-index map for O(1) lookups during a batch update.
     */
    public static Map<String, Integer> buildCodeIndex(DefaultTableModel tableModel) {
        int rowCount = tableModel.getRowCount();
        Map<String, Integer> index = new HashMap<>(Math.max(16, rowCount * 2));
        for (int i = 0; i < rowCount; i++) {
            Object value = tableModel.getValueAt(i, 0);
            if (value != null) {
                index.put(value.toString(), i);
            }
        }
        return index;
    }

    private StockerTableModelUtil() {
    }
}
