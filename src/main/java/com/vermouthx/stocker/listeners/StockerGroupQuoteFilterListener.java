package com.vermouthx.stocker.listeners;

import com.vermouthx.stocker.entities.StockerQuote;
import com.vermouthx.stocker.settings.StockerSetting;
import com.vermouthx.stocker.views.StockerTableView;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filtered listeners that restrict quote data to the current members of one custom group.
 * Membership is read on each event so watchlist deletions stay in sync without a stale snapshot.
 */
public class StockerGroupQuoteFilterListener {

    public static class Update implements StockerQuoteUpdateNotifier {
        private final StockerTableView tableView;
        private final String groupName;

        public Update(StockerTableView tableView, String groupName) {
            this.tableView = tableView;
            this.groupName = groupName;
        }

        @Override
        public void syncQuotes(List<StockerQuote> quotes, int size) {
            if (quotes == null) {
                return;
            }
            Set<String> memberCodes = currentMembers();
            List<StockerQuote> filtered = quotes.stream()
                    .filter(quote -> memberCodes.contains(quote.getCode()))
                    .collect(Collectors.toList());
            Runnable apply = () -> {
                if (tableView.isDisposed()) {
                    return;
                }
                boolean removed = removeRowsOutside(memberCodes);
                new StockerQuoteUpdateListener(tableView).syncQuotes(filtered, memberCodes.size());
                if (removed) {
                    tableView.reapplySortAfterDataChange();
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                apply.run();
            } else {
                SwingUtilities.invokeLater(apply);
            }
        }

        @Override
        public void syncIndices(List<StockerQuote> indices) {
            tableView.syncIndices(indices);
        }

        private boolean removeRowsOutside(Set<String> memberCodes) {
            DefaultTableModel model = tableView.getTableModel();
            boolean removed = false;
            synchronized (model) {
                for (int i = model.getRowCount() - 1; i >= 0; i--) {
                    Object value = model.getValueAt(i, 0);
                    if (value == null || !memberCodes.contains(value.toString())) {
                        model.removeRow(i);
                        removed = true;
                    }
                }
            }
            return removed;
        }

        private Set<String> currentMembers() {
            return Set.copyOf(StockerSetting.Companion.getInstance().getGroupMembers(groupName));
        }
    }

    public static class Delete implements StockerQuoteDeleteNotifier {
        private final StockerTableView tableView;

        public Delete(StockerTableView tableView) {
            this.tableView = tableView;
        }

        @Override
        public void after(String code) {
            new StockerQuoteDeleteListener(tableView).after(code);
        }
    }

    public static class Reload implements StockerQuoteReloadNotifier {
        private final StockerTableView tableView;

        public Reload(StockerTableView tableView) {
            this.tableView = tableView;
        }

        @Override
        public void clear() {
            new StockerQuoteReloadListener(tableView).clear();
        }
    }
}
