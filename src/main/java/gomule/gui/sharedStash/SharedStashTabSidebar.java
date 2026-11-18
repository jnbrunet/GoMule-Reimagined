package gomule.gui.sharedStash;

import javax.swing.*;
import java.awt.*;
import java.util.List;

// A list of every shared tab beside the stash, so any of them is one click away. The stash images
// only bake in 7 tab buttons and the game has had 15 shared tabs since the 2026-11-03 update
// (D2RLoader can add hundreds); this is GoMule's take on the Pyre Stash Search plugin's "Side Tab
// Flyout", showing each tab's custom name (see D2StashTabNames) and item count.
class SharedStashTabSidebar extends JScrollPane {

    static final class Tab {
        final int number;   // 1-based, as the plugin numbers them
        final String name;  // custom name, or null
        final int itemCount;

        Tab(int number, String name, int itemCount) {
            this.number = number;
            this.name = name;
            this.itemCount = itemCount;
        }
    }

    interface Listener {
        void tabChosen(int tab);
    }

    private static final int WIDTH = 160;
    private final DefaultListModel<Tab> model = new DefaultListModel<>();
    private final JList<Tab> list = new JList<>(model);
    private boolean syncing = false;

    SharedStashTabSidebar(Listener listener) {
        setViewportView(list);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setPreferredSize(new Dimension(WIDTH, SharedStashPanel.BG_HEIGHT));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(list); // the full name, if cut off
        list.addListSelectionListener(e -> {
            if (!syncing && !e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
                listener.tabChosen(list.getSelectedIndex());
            }
        });
    }

    // Replaces the list (names and counts change as items move) and selects pSelectedTab.
    void setTabs(List<Tab> pTabs, int pSelectedTab) {
        syncing = true;
        try {
            boolean lSameTabs = model.size() == pTabs.size();
            for (int i = 0; lSameTabs && i < pTabs.size(); i++) {
                Tab lOld = model.get(i);
                Tab lNew = pTabs.get(i);
                lSameTabs = lOld.itemCount == lNew.itemCount && java.util.Objects.equals(lOld.name, lNew.name);
            }
            if (!lSameTabs) {
                model.clear();
                for (Tab lTab : pTabs) model.addElement(lTab);
            }
            if (pSelectedTab >= 0 && pSelectedTab < model.size()) {
                list.setSelectedIndex(pSelectedTab);
                list.ensureIndexIsVisible(pSelectedTab);
            } else {
                list.clearSelection();
            }
        } finally {
            syncing = false;
        }
    }

    private static final class Renderer extends JPanel implements ListCellRenderer<Tab> {
        private final JLabel label = new JLabel();
        private final JLabel count = new JLabel();

        Renderer() {
            super(new BorderLayout(6, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            add(label, BorderLayout.CENTER);
            add(count, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Tab> pList, Tab pTab, int pIndex,
                                                      boolean pSelected, boolean pFocused) {
            label.setText(pTab.number + ". " + (pTab.name == null ? "Shared" : pTab.name));
            label.setFont(pList.getFont().deriveFont(pTab.name == null ? Font.PLAIN : Font.BOLD));
            count.setText(pTab.itemCount == 0 ? "empty" : Integer.toString(pTab.itemCount));
            setToolTipText(pTab.name);
            Color lFg = pSelected ? pList.getSelectionForeground() : pList.getForeground();
            Color lDim = pSelected ? lFg : Color.GRAY;
            label.setForeground(pTab.itemCount == 0 ? lDim : lFg);
            count.setForeground(lDim);
            setBackground(pSelected ? pList.getSelectionBackground() : pList.getBackground());
            return this;
        }
    }
}
