package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2StashTabNames;
import gomule.gui.D2FileManager;
import gomule.gui.D2ItemContainer;
import gomule.gui.D2ItemList;
import gomule.gui.D2ItemListListener;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.Map;

public class D2ViewSharedStash extends JInternalFrame implements D2ItemContainer, D2ItemListListener {
    private final D2FileManager fileManager;
    private final String sharedStashFilename;
    private final SharedStashPanel sharedStashPanel;
    private D2SharedStash sharedStash;
    private final SharedStashTabSidebar tabSidebar = new SharedStashTabSidebar(tab -> getSharedStashPanel().selectTab(tab));
    // Custom tab names from the Pyre Stash Search plugin (see D2StashTabNames), by 1-based tab.
    private Map<Integer, String> tabNames = Collections.emptyMap();

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
        sharedStashPanel = new SharedStashPanel(fileManager, this);
        JPanel lContent = new JPanel(new BorderLayout());
        lContent.add(tabSidebar, BorderLayout.WEST);
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
            // Re-read on every change, so a tab renamed in game shows up without reopening.
            tabNames = D2StashTabNames.forStash(new File(sharedStashFilename));
        }
        setTitle(lTitle);
        sharedStashPanel.build();
    }

    // Called by SharedStashPanel.build(): refreshes the tab list beside the stash (item counts and
    // names change) and its selection.
    void syncTabList() {
        if (sharedStashPanel == null) return; // during SharedStashPanel's own construction
        java.util.List<SharedStashTabSidebar.Tab> lTabs = new java.util.ArrayList<>();
        if (sharedStash != null) {
            java.util.List<Integer> lPanes = sharedStash.getTabPaneIndices();
            for (int i = 0; i < lPanes.size(); i++) {
                lTabs.add(new SharedStashTabSidebar.Tab(i + 1, getTabName(i), sharedStash.getPane(lPanes.get(i)).getItems().size()));
            }
        }
        tabSidebar.setTabs(lTabs, sharedStashPanel.getSelectedTab());
    }

    // The custom name of the tab at pTab (0-based, as SharedStashPanel.getSelectedTab()), or null.
    String getTabName(int pTab) {
        return tabNames.get(pTab + 1);
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
