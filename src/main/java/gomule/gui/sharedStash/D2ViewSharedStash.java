package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2FileManager;
import gomule.gui.D2ItemContainer;
import gomule.gui.D2ItemList;
import gomule.gui.D2ItemListListener;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.io.File;

public class D2ViewSharedStash extends JInternalFrame implements D2ItemContainer, D2ItemListListener {
    private final D2FileManager fileManager;
    private final String sharedStashFilename;
    private final SharedStashPanel sharedStashPanel;
    private D2SharedStash sharedStash;
    // Shown above the stash only when it has more tabs than the 7 baked into the stash images
    // (the game has had 15 shared tabs since the 2026-11-03 update); it reaches every tab.
    private final JPanel tabSelectorBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JComboBox<String> tabSelector = new JComboBox<>();
    private boolean syncingTabSelector = false;

    public D2ViewSharedStash(D2FileManager fileManager, String sharedStashFilename) {
        super(sharedStashFilename, false, true, false, true);
        this.fileManager = fileManager;
        this.sharedStashFilename = sharedStashFilename;
        addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent e) {
                fileManager.saveAll();
                closeView();
            }
        });
        ToolTipManager.sharedInstance().setDismissDelay(40000);
        ToolTipManager.sharedInstance().setInitialDelay(300);
        tabSelectorBar.add(new JLabel("Tab:"));
        tabSelectorBar.add(tabSelector);
        tabSelectorBar.setVisible(false);
        tabSelector.addActionListener(e -> {
            if (!syncingTabSelector && tabSelector.getSelectedIndex() >= 0) {
                getSharedStashPanel().selectTab(tabSelector.getSelectedIndex());
            }
        });
        sharedStashPanel = new SharedStashPanel(fileManager, this);
        JPanel lContent = new JPanel(new BorderLayout());
        lContent.add(tabSelectorBar, BorderLayout.NORTH);
        lContent.add(sharedStashPanel, BorderLayout.CENTER);
        setContentPane(lContent);
        connect();
        setVisible(true);
        pack();
    }

    public void activateView() {
        toFront();
        requestFocusInWindow();
    }

    @Override
    public String getFileName() {
        return sharedStashFilename;
    }

    @Override
    public boolean isHC() {
        return sharedStash.isHC();
    }

    @Override
    public boolean isSC() {
        return sharedStash.isSC();
    }

    @Override
    public void closeView() {
        disconnect(null);
        fileManager.removeFromOpenWindows(this);
    }

    @Override
    public boolean isModified() {
        return sharedStash != null && sharedStash.isModified();
    }

    @Override
    public D2ItemList getItemLists() {
        return sharedStash;
    }

    @Override
    public void connect() {
        if (sharedStash != null) {
            return;
        }
        try {
            sharedStash = (D2SharedStash) fileManager.addItemList(sharedStashFilename, this);
            itemListChanged();
        } catch (Exception pEx) {
            disconnect(pEx);
            pEx.printStackTrace();
        }
    }

    @Override
    public void disconnect(Exception pEx) {
        if (sharedStash != null) {
            fileManager.removeItemList(sharedStashFilename, this);
        }
        sharedStash = null;
        itemListChanged();
    }

    @Override
    public void itemListChanged() {
        String lTitle;
        if (sharedStash == null) {
            lTitle = "Disconnected";
        } else {
            lTitle = sharedStashFilename.substring(sharedStashFilename.lastIndexOf(File.separator) + 1);
            lTitle += ((sharedStash.isModified()) ? "*" : "");
            if (sharedStash.isSC()) {
                lTitle += " (SC)";
            } else if (sharedStash.isHC()) {
                lTitle += " (HC)";
            }
            if (sharedStash.hasVisibleIncompletePane()) {
                // Only warn when a *visible* tab failed to load (see
                // D2SharedStash.hasVisibleIncompletePane()). The DLC's converted "stackable stash"
                // tabs are incomplete by nature but hidden (see D2SharedStash.getVisibleTabCount()),
                // so an ordinary DLC stash shows no warning. Items read before the failure are still
                // shown, and the unread bytes are preserved verbatim on save -- so saving is NOT
                // disabled; the old "saving is disabled" wording predated that fix. There's no status
                // label here the way D2ViewChar has, so this goes in the window title instead -- also
                // surfaced as a tooltip since the reason text is usually too long for a title.
                lTitle += " [LOADED PARTIALLY]";
                setToolTipText("Loaded partially -- some items in a visible tab couldn't be read and aren't shown. "
                        + "They're preserved unchanged when you save. " + sharedStash.getVisibleIncompleteReason());
            } else {
                setToolTipText(null);
            }
        }
        setTitle(lTitle);
        sharedStashPanel.build();
    }

    // Called by SharedStashPanel.build(): rebuilds the tab list (item counts change as items move)
    // and shows the selector only when some tab isn't reachable through the baked tab buttons.
    void syncTabSelector() {
        if (sharedStashPanel == null) return; // during SharedStashPanel's own construction
        int lTabs = sharedStashPanel.getVisibleTabCount();
        boolean lShow = lTabs > SharedStashPanel.BAKED_TAB_SLOTS;
        syncingTabSelector = true;
        try {
            tabSelector.removeAllItems();
            if (lShow) {
                java.util.List<Integer> lPanes = sharedStash.getTabPaneIndices();
                for (int i = 0; i < lTabs; i++) {
                    int lCount = sharedStash.getPane(lPanes.get(i)).getItems().size();
                    tabSelector.addItem("Tab " + (i + 1) + (lCount == 0 ? " (empty)" : " (" + lCount + " items)"));
                }
                tabSelector.setSelectedIndex(Math.max(0, sharedStashPanel.getSelectedTab()));
            }
        } finally {
            syncingTabSelector = false;
        }
        if (tabSelectorBar.isVisible() != lShow) {
            tabSelectorBar.setVisible(lShow);
            pack();
        }
    }

    public String getSharedStashName() {
        return sharedStashFilename.substring(sharedStashFilename.lastIndexOf(File.separator) + 1);
    }

    public SharedStashPanel getSharedStashPanel() {
        return sharedStashPanel;
    }

    public D2SharedStash getSharedStash() {
        return sharedStash;
    }
}
