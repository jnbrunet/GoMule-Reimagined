package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2FileManager;
import gomule.gui.D2ImageCache;
import gomule.item.D2Item;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import static java.util.Collections.emptyList;

public class SharedStashPanel extends JPanel {

    public static final int BG_WIDTH = 552;
    public static final int BG_HEIGHT = 567;

    // Left x of each of the 7 tab slots, plus the strip's right edge as the final entry; matches
    // the click regions in SharedStashPanelMouseClickHandler.getPossibleStashTabClick().
    private static final int[] TAB_X_BOUNDS = {27, 87, 150, 212, 275, 337, 400, 462};
    private static final int TAB_BAND_TOP = 46;     // inclusive; top of the tab buttons' band
    private static final int TAB_BAND_BOTTOM = 75;  // exclusive; bottom of that band
    // A column of empty recessed frame just right of the tab strip, used as the "no tab" texture.
    private static final int RECESS_SAMPLE_X = 503;
    private final D2FileManager fileManager;
    private final D2ViewSharedStash sharedStashView;
    private int selectedStashPaneIndex = 0;
    private Image background;

    public SharedStashPanel(D2FileManager fileManager, D2ViewSharedStash sharedStashView) {
        this.fileManager = fileManager;
        this.sharedStashView = sharedStashView;
        setLayout(new BorderLayout());
        setSize(BG_WIDTH, BG_HEIGHT);
        Dimension lSize = new Dimension(BG_WIDTH, BG_HEIGHT);
        setPreferredSize(lSize);
        addMouseListener(new SharedStashPanelMouseClickHandler(this));
        addMouseMotionListener(new SharedStashMouseMotionListener(this));
        setVisible(true);
        ToolTipManager.sharedInstance().setDismissDelay(40000);
        ToolTipManager.sharedInstance().setInitialDelay(300);
        build();
    }

    // The stash images bake in 7 tab buttons. Tabs past the 7th (the game has had 15 shared tabs
    // since the 2026-11-03 update) are reached through the view's tab selector instead.
    public static final int BAKED_TAB_SLOTS = 7;

    public void build() {
        // selectedStashPaneIndex is a PANE index (what replacePane() etc. need); only panes that
        // are tabs may be selected -- fall back to the first tab otherwise (e.g. a stash reloaded
        // with a different layout).
        int lTab = getSelectedTab();
        if (getSharedStash() != null && lTab < 0) {
            selectedStashPaneIndex = getSharedStash().getTabPaneIndices().get(0);
            lTab = 0;
        }
        boolean lOnBakedSlot = lTab >= 0 && lTab < BAKED_TAB_SLOTS;
        Image lEmptyBackground = D2ImageCache.getImage("stash" + (lOnBakedSlot ? lTab + 1 : 1) + ".png");
        background = fileManager.getGraphicsConfiguration().createCompatibleImage(BG_WIDTH, BG_HEIGHT, Transparency.BITMASK);
        Graphics2D lGraphics = (Graphics2D) background.getGraphics();
        lGraphics.drawImage(lEmptyBackground, 0, 0, this);
        if (getSharedStash() != null) {
            // On tab 8 and up no baked button is the selected one, so blank the whole strip rather
            // than highlight the wrong tab; the selector shows which tab this is.
            maskHiddenTabs(lOnBakedSlot ? Math.min(getVisibleTabCount(), BAKED_TAB_SLOTS) : 0);
            placeItemsInView();
        }
        sharedStashView.syncTabSelector();
        repaint();
    }

    // How many tabs to show -- the model hides the DLC's converted "stackable stash" tabs (see
    // D2SharedStash.getVisibleTabCount()). Returns 0 only when there's no stash connected.
    public int getVisibleTabCount() {
        D2SharedStash sharedStash = getSharedStash();
        return sharedStash == null ? 0 : sharedStash.getVisibleTabCount();
    }

    // The selected tab's position in the tab strip, or -1 (no stash, or a pane that isn't a tab).
    public int getSelectedTab() {
        D2SharedStash sharedStash = getSharedStash();
        return sharedStash == null ? -1 : sharedStash.getTabPaneIndices().indexOf(selectedStashPaneIndex);
    }

    public void selectTab(int pTab) {
        D2SharedStash sharedStash = getSharedStash();
        if (sharedStash == null || pTab < 0 || pTab >= getVisibleTabCount() || pTab == getSelectedTab()) return;
        selectedStashPaneIndex = sharedStash.getTabPaneIndices().get(pTab);
        build();
    }

    // Each stashN.png bakes in all 7 "Shared" tab buttons, so the hidden ones are painted over
    // with the empty recessed-frame texture sampled from just right of the strip -- making it look
    // like the stash natively has only the visible number of tabs. Purely cosmetic; no item or
    // pane data is touched.
    private void maskHiddenTabs(int visibleTabs) {
        if (visibleTabs >= TAB_X_BOUNDS.length - 1) return; // all 7 shown -> nothing to hide
        if (!(background instanceof BufferedImage)) return;
        BufferedImage image = (BufferedImage) background;
        int xStart = TAB_X_BOUNDS[visibleTabs];
        int xEnd = TAB_X_BOUNDS[TAB_X_BOUNDS.length - 1];
        for (int y = TAB_BAND_TOP; y < TAB_BAND_BOTTOM; y++) {
            int recessColor = image.getRGB(RECESS_SAMPLE_X, y);
            for (int x = xStart; x < xEnd; x++) {
                image.setRGB(x, y, recessColor);
            }
        }
    }

    private void placeItemsInView() {
        D2SharedStash.D2SharedStashPane pane = getSelectedStashPane();
        pane.getItems().forEach(item -> {
            if (item.get_location() != 0 && item.get_body_position() != 0 && item.get_panel() != 5) return;
            Image image = D2ImageCache.getDC6Image(item);
            int col = item.get_col();
            int row = item.get_row();
            int x = getXCoordForCol(col);
            int y = getYCoordForRow(row);
            background.getGraphics().drawImage(image, x, y, this);
        });
        background.getGraphics().drawString(Long.toString(pane.getGold()), 248, 516);
    }

    public static int getXCoordForCol(int col) {
        int diffx = (col / 2);
        return (int)(30 + (col * 28.4) + ((diffx * 3) + ((col - diffx) * 2)));
    }

    public static int getYCoordForRow(int row) {
        int diffy = (row / 2);
        return (int)(77 + (row * 28.4) + ((diffy * 3) + ((row - diffy) * 2)));
    }

    public static int getColForXCoord(int x) {
        if (x < 29) return -1;
        return ((2 * x) - 58) / 61;
    }

    public static int getRowForYCoord(int y) {
        if (y < 75) return -1;
        return ((2 * y) - 150) / 61;
    }

    @Override
    public void paint(Graphics pGraphics) {
        super.paint(pGraphics);
        Graphics2D lGraphics = (Graphics2D) pGraphics;
        lGraphics.drawImage(background, 0, 0, this);
    }

    public void setCursorPickupItem() {
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public void setCursorDropItem() {
        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void setCursorNormal() {
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    public java.util.List<D2Item> removeAllItems() {
        D2SharedStash sharedStash = getSharedStash();
        if (sharedStash == null) return emptyList();
        D2SharedStash.D2SharedStashPane stashPane = getSelectedStashPane();
        sharedStash.replacePane(selectedStashPaneIndex, D2SharedStash.D2SharedStashPane.fromItems(emptyList(), stashPane.getGold()));
        sharedStash.setModified(true);
        return stashPane.getItems();
    }

    public java.util.List<D2Item> tryToAddItems(java.util.List<D2Item> items) {
        D2SharedStash sharedStash = getSharedStash();
        if (sharedStash == null) return emptyList();
        D2SharedStash.D2SharedStashPane stashPane = getSelectedStashPane();
        java.util.List<D2Item> successfullyAddedItems = new ArrayList<>();
        for (D2Item item : items) {
            stashPane = getD2SharedStashPane(stashPane, successfullyAddedItems, item);
        }
        sharedStash.replacePane(selectedStashPaneIndex, stashPane);
        sharedStash.setModified(true);
        return successfullyAddedItems;
    }

    public D2SharedStash.D2SharedStashPane getSelectedStashPane() {
        return getSharedStash().getPane(selectedStashPaneIndex);
    }

    public D2SharedStash getSharedStash() {
        return sharedStashView.getSharedStash();
    }

    public int getSelectedStashPaneIndex() {
        return selectedStashPaneIndex;
    }

    public void setSelectedStashPaneIndex(int selectedStashPaneIndex) {
        this.selectedStashPaneIndex = selectedStashPaneIndex;
    }

    private D2SharedStash.D2SharedStashPane getD2SharedStashPane(D2SharedStash.D2SharedStashPane stashPane, java.util.List<D2Item> successfullyAddedItems, D2Item item) {
        for (int i = 0; i < 13; i++) {
            for (int j = 0; j < 16; j++) {
                if (stashPane.canDropItem(j, i, item)) {
                    stashPane = stashPane.addItem(j, i, item);
                    successfullyAddedItems.add(item);
                    return stashPane;
                }
            }
        }
        return stashPane;
    }
}