package gomule.gui;

import gomule.d2i.D2SharedStash;
import gomule.d2s.D2Character;
import gomule.grail.D2GrailCategories;
import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailFinding;
import gomule.grail.D2GrailFirstSeenStore;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import gomule.grail.D2GrailRunewords;
import gomule.grail.D2GrailScanner;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import gomule.util.D2Project;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ButtonGroup;
import javax.swing.Box;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Holy Grail window (plan section 2/3): lists every collectible unique, set item and complete
 * runeword the Reimagined mod defines, marks each as found/missing against every currently open
 * .d2s/.d2x/.d2i file, and lets the player filter by tab/tier/category/status/search text and
 * Chronicle scope.
 * <p>
 * Modelled on D2ViewStash (JInternalFrame implementing D2ItemContainer + D2ItemListListener), but
 * NOT tied to one file: it subscribes to every open D2ItemList at once (D2FileManager.
 * getOpenItemLists(), deliberately not D2ItemListAll -- see D2GrailScanner's class javadoc for
 * why) and D2FileManager pushes it a refresh whenever a file opens or closes (see
 * D2FileManager.addItemList()/removeItemList()). All filtering/statistics logic lives in
 * D2GrailModel, which stays Swing-free and unit-testable; this class only wires Swing components
 * to it.
 */
public class D2ViewGrail extends JInternalFrame implements D2ItemContainer, D2ItemListListener {

    private static final long serialVersionUID = 1L;

    // Not a real file name -- this window represents every open file at once, not one. Deliberately
    // contains a "." so D2FileManager.getFramesByFileSuffix() (fileName.substring(lastIndexOf(".")))
    // does not throw StringIndexOutOfBoundsException on a name with no dot at all, and deliberately
    // not ".d2s"/".d2x"/".d2i" so this window is simply excluded from the per-suffix window
    // rearrangement rather than mis-sorted into one of those groups.
    private static final String FILE_NAME = "Holy Grail.grail";

    private final D2FileManager iFileManager;
    private final D2GrailModel iModel = new D2GrailModel();
    private final D2ItemList iInertItemList = new InertItemList();

    // Loaded once at construction time from the CURRENT project (plan section 8); a project
    // switch while this window stays open is not tracked (a known, minor simplification -- see
    // the constructor's comment). Null only if there is no current project at all (D2FileManager.
    // getProject() can be null after a failed checkProjects()), in which case "First seen" is
    // simply never shown rather than this window failing to open over a nice-to-have.
    private final D2GrailFirstSeenStore iFirstSeenStore;

    // Every D2ItemList this window is currently listening to -- kept so refreshLists() can diff
    // against a fresh D2FileManager.getOpenItemLists() snapshot and (un)subscribe only what
    // actually changed, rather than blindly re-adding the same listener twice (which would double-
    // fire itemListChanged() forever).
    private final Set<D2ItemList> iSubscribedLists = new HashSet<D2ItemList>();

    private JToggleButton iTabUnique;
    private JToggleButton iTabSet;
    private JToggleButton iTabRuneword;
    private JTextField iSearchField;
    private JCheckBox iTierNormal;
    private JCheckBox iTierExceptional;
    private JCheckBox iTierElite;
    private JComboBox<String> iShowCombo;
    private JCheckBox iIncludeNonChronicle;
    private JPanel iRuneFilterPanel;
    private final List<JToggleButton> iRuneButtons = new ArrayList<JToggleButton>();
    private JCheckBox iRunePartialMatch;
    private JLabel iPartialLoadBanner;
    private JTree iTree;
    private DefaultTreeModel iTreeModel;
    private DefaultListModel<Object> iListModel;
    private JList<Object> iList;
    private JLabel iCategoryProgressLabel;
    private JProgressBar iCategoryProgressBar;
    private JLabel iTotalProgressLabel;
    private JProgressBar iTotalProgressBar;
    private JLabel iNoFilesHint;

    public D2ViewGrail(D2FileManager pFileManager) {
        super("Holy Grail", true, true, true, true);
        iFileManager = pFileManager;

        // Restore the persisted Chronicle-scope choice (FileManagerProperties, same mechanism as
        // the look-and-feel setting -- plan section 4.2) before the first render, so the window
        // never flashes the default scope before switching to the remembered one.
        iModel.setIncludeNonChronicle(iFileManager.isGrailIncludeNonChronicle());
        // Search matches an item's properties as well as its name -- see
        // D2GrailModel.SearchTextProvider for why the model is handed this rather than computing it.
        iModel.setSearchTextProvider(D2GrailListRenderer::searchTextFor);

        // D2FileManager.getProject() can be null (a failed checkProjects() -- see its catch
        // block); "First seen" tracking degrades to "not available" rather than this window
        // failing to open over it.
        D2Project lProject = iFileManager.getProject();
        iFirstSeenStore = lProject == null ? null : D2GrailFirstSeenStore.load(lProject);

        buildUi();

        addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent pEvent) {
                closeView();
            }
        });

        connect();
        // Taller and wider than the other views on purpose: the Runewords tab's rune panel is three
        // rows of eleven toggles, and at the old 820x560 it left the list itself only a few visible
        // rows on that tab.
        setSize(900, 680);
        // A JInternalFrame is NOT visible by default: adding it to the JDesktopPane (which is what
        // D2FileManager.addToOpenWindows does) is not enough to make it appear on screen. Without
        // this the window is constructed, sized and wired up correctly but stays invisible, so
        // clicking the toolbar button looks like it does nothing at all. D2ViewStash and D2ViewChar
        // both end their constructors the same way.
        setVisible(true);
    }

    // ------------------------------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------------------------------

    private void buildUi() {
        JPanel lContent = new JPanel(new BorderLayout());

        JPanel lTopPanel = new JPanel();
        lTopPanel.setLayout(new BoxLayout(lTopPanel, BoxLayout.Y_AXIS));

        JPanel lTabsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup lTabGroup = new ButtonGroup();
        iTabUnique = new JToggleButton("Uniques", true);
        iTabSet = new JToggleButton("Sets");
        iTabRuneword = new JToggleButton("Runewords");
        lTabGroup.add(iTabUnique);
        lTabGroup.add(iTabSet);
        lTabGroup.add(iTabRuneword);
        ActionListener lTabListener = pEvent -> onTabChanged();
        iTabUnique.addActionListener(lTabListener);
        iTabSet.addActionListener(lTabListener);
        iTabRuneword.addActionListener(lTabListener);
        lTabsRow.add(iTabUnique);
        lTabsRow.add(iTabSet);
        lTabsRow.add(iTabRuneword);
        lTabsRow.add(Box.createHorizontalStrut(20));
        lTabsRow.add(new JLabel("Search:"));
        iSearchField = new JTextField(16);
        iSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent pEvent) {
                onSearchChanged();
            }

            public void removeUpdate(DocumentEvent pEvent) {
                onSearchChanged();
            }

            public void changedUpdate(DocumentEvent pEvent) {
                onSearchChanged();
            }
        });
        lTabsRow.add(iSearchField);
        lTopPanel.add(lTabsRow);

        JPanel lFilterRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        iTierNormal = new JCheckBox("Normal", true);
        iTierExceptional = new JCheckBox("Exceptional", true);
        iTierElite = new JCheckBox("Elite", true);
        ActionListener lTierListener = pEvent -> onTierChanged();
        iTierNormal.addActionListener(lTierListener);
        iTierExceptional.addActionListener(lTierListener);
        iTierElite.addActionListener(lTierListener);
        lFilterRow.add(iTierNormal);
        lFilterRow.add(iTierExceptional);
        lFilterRow.add(iTierElite);
        lFilterRow.add(Box.createHorizontalStrut(20));
        lFilterRow.add(new JLabel("Show:"));
        iShowCombo = new JComboBox<String>(new String[]{"All", "Found", "Missing"});
        iShowCombo.addActionListener(pEvent -> onStatusChanged());
        lFilterRow.add(iShowCombo);
        lTopPanel.add(lFilterRow);

        JPanel lChronicleRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        iIncludeNonChronicle = new JCheckBox("Include non-Chronicle items", iModel.isIncludeNonChronicle());
        iIncludeNonChronicle.addActionListener(pEvent -> onIncludeNonChronicleChanged());
        lChronicleRow.add(iIncludeNonChronicle);
        lTopPanel.add(lChronicleRow);

        iRuneFilterPanel = buildRuneFilterPanel();
        iRuneFilterPanel.setVisible(false);
        lTopPanel.add(iRuneFilterPanel);

        iPartialLoadBanner = new JLabel(" ");
        iPartialLoadBanner.setOpaque(true);
        iPartialLoadBanner.setBackground(new Color(255, 250, 205));
        iPartialLoadBanner.setForeground(Color.BLACK);
        iPartialLoadBanner.setVisible(false);
        lTopPanel.add(iPartialLoadBanner);

        lContent.add(lTopPanel, BorderLayout.NORTH);

        DefaultMutableTreeNode lRootNode = new DefaultMutableTreeNode("root");
        iTreeModel = new DefaultTreeModel(lRootNode);
        iTree = new JTree(iTreeModel) {
            private static final long serialVersionUID = 1L;

            // A tree-node tooltip "where it makes sense" (plan section 3.3): the found/total for
            // whichever node the mouse is over, independent of which node is actually selected --
            // D2GrailModel.getProgressFor() exists specifically so this doesn't have to touch (or
            // restore) the real selection just to compute it.
            @Override
            public String getToolTipText(MouseEvent pEvent) {
                TreePath lPath = getPathForLocation(pEvent.getX(), pEvent.getY());
                if (lPath == null) {
                    return null;
                }
                Object lLast = lPath.getLastPathComponent();
                if (!(lLast instanceof DefaultMutableTreeNode)) {
                    return null;
                }
                Object lUserObject = ((DefaultMutableTreeNode) lLast).getUserObject();
                if (!(lUserObject instanceof TreeNodeData)) {
                    return null;
                }
                TreeNodeData lData = (TreeNodeData) lUserObject;
                D2GrailModel.Progress lProgress = iModel.getProgressFor(lData.id);
                return lData.label + ": " + lProgress.getFound() + " / " + lProgress.getTotal();
            }
        };
        iTree.setToolTipText("");
        iTree.setRootVisible(false);
        iTree.setShowsRootHandles(true);
        iTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        iTree.addTreeSelectionListener(pEvent -> onTreeSelectionChanged());
        JScrollPane lTreeScroll = new JScrollPane(iTree);
        lTreeScroll.setPreferredSize(new Dimension(200, 300));

        iListModel = new DefaultListModel<Object>();
        iList = new JList<Object>(iListModel) {
            private static final long serialVersionUID = 1L;

            // Plan section 3.3: a rich hover tooltip per row. JList does not automatically ask its
            // cell renderer for a tooltip -- overriding getToolTipText(MouseEvent) is the standard
            // Swing idiom for a per-cell tooltip; setToolTipText("") below is what registers this
            // component with ToolTipManager in the first place (a null argument would do the
            // opposite -- unregister it).
            @Override
            public String getToolTipText(MouseEvent pEvent) {
                int lIndex = locationToIndex(pEvent.getPoint());
                if (lIndex < 0) {
                    return null;
                }
                Object lValue = getModel().getElementAt(lIndex);
                if (D2GrailListRenderer.rendersDescriptionInline(lValue)) {
                    // A runeword row already draws its whole description in the cell; a popup
                    // repeating it would just cover the rows below.
                    return null;
                }
                return D2GrailListRenderer.tooltipFor(lValue, iFirstSeenStore);
            }
        };
        iList.setToolTipText("");
        iList.setCellRenderer(new D2GrailListRenderer(iFirstSeenStore));
        iList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent pEvent) {
                if (pEvent.getClickCount() != 2) {
                    return;
                }
                onListDoubleClicked(pEvent);
            }
        });
        JScrollPane lListScroll = new JScrollPane(iList);

        iNoFilesHint = new JLabel("No files open - open a character, stash or shared stash to track your progress");
        iNoFilesHint.setOpaque(true);
        iNoFilesHint.setBackground(new Color(255, 250, 205));
        iNoFilesHint.setForeground(Color.BLACK);
        iNoFilesHint.setVisible(false);
        JPanel lListPanel = new JPanel(new BorderLayout());
        lListPanel.add(iNoFilesHint, BorderLayout.NORTH);
        lListPanel.add(lListScroll, BorderLayout.CENTER);

        JPanel lStatsPanel = new JPanel();
        lStatsPanel.setLayout(new BoxLayout(lStatsPanel, BoxLayout.Y_AXIS));
        iCategoryProgressLabel = new JLabel(" ");
        iCategoryProgressBar = new JProgressBar(0, 100);
        iTotalProgressLabel = new JLabel(" ");
        iTotalProgressBar = new JProgressBar(0, 100);
        JButton lExportButton = new JButton("Export...");
        lExportButton.addActionListener(pEvent -> onExport());
        lStatsPanel.add(iCategoryProgressLabel);
        lStatsPanel.add(iCategoryProgressBar);
        lStatsPanel.add(iTotalProgressLabel);
        lStatsPanel.add(iTotalProgressBar);
        lStatsPanel.add(lExportButton);

        JPanel lLeftPanel = new JPanel(new BorderLayout());
        lLeftPanel.add(lTreeScroll, BorderLayout.CENTER);
        lLeftPanel.add(lStatsPanel, BorderLayout.SOUTH);

        JSplitPane lSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lLeftPanel, lListPanel);
        lSplit.setDividerLocation(220);

        lContent.add(lSplit, BorderLayout.CENTER);

        setContentPane(lContent);

        rebuildTree();
    }

    /**
     * The Runewords tab's "which runes do I have?" panel: one toggle per rune, El (1) through Zod
     * (33), laid out three rows of eleven, plus Select All / Deselect All and the partial-match
     * switch. Shown only on that tab (see onTabChanged) -- a unique or set item is not made of
     * runes, so the panel is hidden rather than greyed out there: unlike the tier checkboxes, which
     * stay visible because they DO apply to two of the three tabs, this one applies to exactly one
     * and would be 33 dead controls everywhere else.
     * <p>
     * Nothing selected means the filter is off and every runeword is listed -- see
     * D2GrailModel.setSelectedRunes -- so the panel starts empty rather than fully ticked.
     */
    private JPanel buildRuneFilterPanel() {
        JPanel lPanel = new JPanel(new BorderLayout());
        lPanel.setBorder(BorderFactory.createTitledBorder("Runes I have"));

        JPanel lGrid = new JPanel(new GridLayout(3, 11, 2, 2));
        ActionListener lRuneListener = pEvent -> onRuneSelectionChanged();
        for (int lRune = 1; lRune <= D2GrailRunewords.RUNE_COUNT; lRune++) {
            JToggleButton lButton = new JToggleButton(D2GrailRunewords.runeShortName(lRune) + " " + lRune);
            lButton.setMargin(new Insets(1, 2, 1, 2));
            lButton.setToolTipText(D2GrailRunewords.runeShortName(lRune) + " Rune (#" + lRune + ")");
            lButton.addActionListener(lRuneListener);
            iRuneButtons.add(lButton);
            lGrid.add(lButton);
        }
        lPanel.add(lGrid, BorderLayout.CENTER);

        JPanel lRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton lSelectAll = new JButton("Select All");
        lSelectAll.addActionListener(pEvent -> setAllRunesSelected(true));
        JButton lDeselectAll = new JButton("Deselect All");
        lDeselectAll.addActionListener(pEvent -> setAllRunesSelected(false));
        iRunePartialMatch = new JCheckBox("Show partial results (only some runes required)");
        iRunePartialMatch.addActionListener(pEvent -> onRuneSelectionChanged());
        lRow.add(lSelectAll);
        lRow.add(lDeselectAll);
        lRow.add(Box.createHorizontalStrut(12));
        lRow.add(iRunePartialMatch);
        lPanel.add(lRow, BorderLayout.SOUTH);

        return lPanel;
    }

    private void setAllRunesSelected(boolean pSelected) {
        for (JToggleButton lButton : iRuneButtons) {
            lButton.setSelected(pSelected);
        }
        onRuneSelectionChanged();
    }

    private void onRuneSelectionChanged() {
        Set<Integer> lSelected = new TreeSet<Integer>();
        for (int i = 0; i < iRuneButtons.size(); i++) {
            if (iRuneButtons.get(i).isSelected()) {
                lSelected.add(Integer.valueOf(i + 1));
            }
        }
        iModel.setSelectedRunes(lSelected);
        iModel.setRunePartialMatch(iRunePartialMatch.isSelected());
        refreshList();
        // Deliberately no refreshStats(): like search, this narrows what is listed without changing
        // what the grail is -- see D2GrailModel.passesRuneFilter.
    }

    // ------------------------------------------------------------------------------------------
    // Control state -> model
    // ------------------------------------------------------------------------------------------

    private void onTabChanged() {
        D2GrailKey.Type lNewTab = iTabUnique.isSelected() ? D2GrailKey.Type.UNIQUE
                : iTabSet.isSelected() ? D2GrailKey.Type.SET : D2GrailKey.Type.RUNEWORD;
        iModel.setTab(lNewTab);
        // A category/class selection from the previous tab means nothing on the new one (plan
        // section 10: switching tabs with a category selected must not leave a stale selection) --
        // rebuilding the tree below and resetting to "All" sidesteps that entirely rather than
        // trying to translate a selection across two different trees.
        iModel.setCategorySelection(D2GrailModel.ALL_ID);

        // Tier checkboxes and the Chronicle-scope checkbox are both inert on Runewords (plan
        // sections 5.3/7: every runeword is Tier.NONE and runes.txt has no disableChronicle column
        // at all) -- greyed out rather than hidden, so the player can see they exist and why they
        // have no effect here.
        boolean lTiersApply = lNewTab != D2GrailKey.Type.RUNEWORD;
        iTierNormal.setEnabled(lTiersApply);
        iTierExceptional.setEnabled(lTiersApply);
        iTierElite.setEnabled(lTiersApply);
        iIncludeNonChronicle.setEnabled(lTiersApply);
        iRuneFilterPanel.setVisible(!lTiersApply);
        // setVisible() repaints but does not re-run the enclosing BoxLayout on its own, so without
        // this the list below would keep the space the rune panel used to occupy (or not get it
        // back) until the window is resized.
        revalidate();
        repaint();

        rebuildTree();
        refreshList();
        refreshStats();
    }

    private void onSearchChanged() {
        iModel.setSearchText(iSearchField.getText());
        refreshList();
        refreshStats();
    }

    private void onTierChanged() {
        if (!iTierNormal.isSelected() && !iTierExceptional.isSelected() && !iTierElite.isSelected()) {
            // Plan section 7: at least one tier checkbox must stay checked. Which one was just
            // unchecked isn't tracked, so this simply re-checks Normal as a safe, always-valid
            // default rather than leaving the filter meaninglessly empty.
            iTierNormal.setSelected(true);
        }
        iModel.setTierEnabled(D2GrailEntry.Tier.NORMAL, iTierNormal.isSelected());
        iModel.setTierEnabled(D2GrailEntry.Tier.EXCEPTIONAL, iTierExceptional.isSelected());
        iModel.setTierEnabled(D2GrailEntry.Tier.ELITE, iTierElite.isSelected());
        refreshList();
        refreshStats();
    }

    private void onStatusChanged() {
        int lIndex = iShowCombo.getSelectedIndex();
        D2GrailModel.Status lStatus = lIndex == 1 ? D2GrailModel.Status.FOUND
                : lIndex == 2 ? D2GrailModel.Status.MISSING : D2GrailModel.Status.ALL;
        iModel.setStatus(lStatus);
        refreshList();
        // Deliberately no refreshStats() here: plan section 7 -- the progress bars ignore the
        // status filter on purpose (else Show: Missing would always read 0%).
    }

    private void onIncludeNonChronicleChanged() {
        boolean lInclude = iIncludeNonChronicle.isSelected();
        // Filtering only -- see D2GrailModel.setIncludeNonChronicle's javadoc: this never touches
        // the findings map, so toggling this checkbox never rescans.
        iModel.setIncludeNonChronicle(lInclude);
        iFileManager.setGrailIncludeNonChronicle(lInclude);
        refreshList();
        refreshStats();
    }

    /**
     * Plan section 7: double-clicking a found entry focuses the file it came from; a missing
     * entry (no file to focus) or a set header (a String, not a Row) does nothing.
     * D2GrailFinding's file set preserves insertion order (see its class javadoc), so "the file it
     * came from" for a multi-copy find is, deliberately, the first one this window ever scanned
     * it out of -- consistent with "first seen" elsewhere in this window.
     * <p>
     * Deliberately getFileNames() (the full path), not getFileDisplayNames(): D2FileManager's
     * iItemLists/iOpenWindows are keyed by the exact string D2ItemList.getFilename() returns, so
     * focusFileWindow() would silently find nothing if handed the short display name instead.
     */
    private void onListDoubleClicked(MouseEvent pEvent) {
        int lIndex = iList.locationToIndex(pEvent.getPoint());
        if (lIndex < 0) {
            return;
        }
        Object lValue = iListModel.getElementAt(lIndex);
        if (!(lValue instanceof D2GrailModel.Row)) {
            return;
        }
        D2GrailModel.Row lRow = (D2GrailModel.Row) lValue;
        if (!lRow.isFound()) {
            return;
        }
        java.util.Iterator<String> lFullPaths = lRow.getFinding().getFileNames().iterator();
        if (lFullPaths.hasNext()) {
            iFileManager.focusFileWindow(lFullPaths.next());
        }
    }

    private void onTreeSelectionChanged() {
        Object lComponent = iTree.getLastSelectedPathComponent();
        if (!(lComponent instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object lUserObject = ((DefaultMutableTreeNode) lComponent).getUserObject();
        if (!(lUserObject instanceof TreeNodeData)) {
            return;
        }
        iModel.setCategorySelection(((TreeNodeData) lUserObject).id);
        refreshList();
        refreshStats();
    }

    // ------------------------------------------------------------------------------------------
    // Tree construction
    // ------------------------------------------------------------------------------------------

    /**
     * Uniques tab: All, then Armor/Weapons/Misc (each with its UICategory children in
     * D2GrailCategories's declared order) plus a final Uncategorized sibling holding the 25 real
     * entries with no resolvable category (plan section 5.5) -- without this node they would be
     * reachable only via search, never by browsing.
     * <p>
     * Sets tab: All, then one leaf per sets.txt UIClass (General first, then one per class, in
     * D2GrailCategories.knownUiClassCodes()'s order).
     * <p>
     * Runewords tab: All, then one leaf per base type any complete runeword allows -- Amazon Bow,
     * Any Armor, Any Shield, Any Weapon, Armor, Assassin Claw, ... Warlock Grimoire -- read out of
     * runes.txt's own itype1..itype6 columns (D2GrailRunewords.allBaseTypeLabels), alphabetically.
     * A runeword allowing several of them appears under each: unlike a unique, which has exactly
     * one base, a runeword's bases genuinely are a list, so the node means "words I can put in
     * this" rather than "words whose base is this".
     */
    private void rebuildTree() {
        DefaultMutableTreeNode lRoot = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode lAllNode = new DefaultMutableTreeNode(new TreeNodeData(D2GrailModel.ALL_ID, "All"));
        lRoot.add(lAllNode);

        D2GrailKey.Type lTab = iModel.getTab();
        if (lTab == D2GrailKey.Type.UNIQUE) {
            for (D2GrailCategories.RootGroup lRootGroup : D2GrailCategories.RootGroup.values()) {
                DefaultMutableTreeNode lRootGroupNode = new DefaultMutableTreeNode(
                        new TreeNodeData(D2GrailModel.rootId(lRootGroup), capitalize(lRootGroup.name())));
                for (String lCode : D2GrailCategories.knownCodes()) {
                    if (D2GrailCategories.getRootGroup(lCode) == lRootGroup) {
                        lRootGroupNode.add(new DefaultMutableTreeNode(
                                new TreeNodeData(D2GrailModel.categoryId(lCode), D2GrailCategories.getLabel(lCode))));
                    }
                }
                lRoot.add(lRootGroupNode);
            }
            lRoot.add(new DefaultMutableTreeNode(new TreeNodeData(D2GrailModel.UNCATEGORIZED_ID, "Uncategorized")));
        } else if (lTab == D2GrailKey.Type.SET) {
            for (String lCode : D2GrailCategories.knownUiClassCodes()) {
                lRoot.add(new DefaultMutableTreeNode(
                        new TreeNodeData(D2GrailModel.classId(lCode), D2GrailCategories.getUiClassLabel(lCode))));
            }
        } else if (lTab == D2GrailKey.Type.RUNEWORD) {
            for (String lLabel : D2GrailRunewords.allBaseTypeLabels()) {
                lRoot.add(new DefaultMutableTreeNode(
                        new TreeNodeData(D2GrailModel.baseTypeId(lLabel), lLabel)));
            }
        }

        iTreeModel.setRoot(lRoot);
        for (int i = 0; i < iTree.getRowCount(); i++) {
            iTree.expandRow(i);
        }
        iTree.setSelectionPath(new TreePath(new Object[]{lRoot, lAllNode}));
    }

    private static String capitalize(String pText) {
        return pText.isEmpty() ? pText : pText.charAt(0) + pText.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * The plain (String id, display label) pair a tree node carries as its user object.
     * DefaultMutableTreeNode's default renderer calls toString() on it, hence the override.
     */
    private static final class TreeNodeData {
        final String id;
        final String label;

        TreeNodeData(String pId, String pLabel) {
            id = pId;
            label = pLabel;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Rendering the model into the list/stats/banner
    // ------------------------------------------------------------------------------------------

    private void refreshList() {
        iListModel.clear();
        if (iModel.getTab() == D2GrailKey.Type.SET) {
            for (D2GrailModel.SetGroup lGroup : iModel.getSetGroups()) {
                iListModel.addElement("== " + lGroup.getSetName() + " ==   "
                        + lGroup.getFound() + " / " + lGroup.getTotal());
                for (D2GrailModel.Row lRow : lGroup.getRows()) {
                    iListModel.addElement(lRow);
                }
            }
        } else {
            for (D2GrailModel.Row lRow : iModel.getRows()) {
                iListModel.addElement(lRow);
            }
        }
    }

    private void refreshStats() {
        D2GrailModel.Progress lCategoryProgress = iModel.getCategoryProgress();
        D2GrailModel.Progress lTotalProgress = iModel.getTabProgress();

        iCategoryProgressLabel.setText(
                selectedNodeLabel() + ": " + lCategoryProgress.getFound() + " / " + lCategoryProgress.getTotal());
        iCategoryProgressBar.setValue((int) Math.round(lCategoryProgress.getRatio() * 100));

        int lTotalPercent = (int) Math.round(lTotalProgress.getRatio() * 100);
        iTotalProgressLabel.setText(
                "TOTAL: " + lTotalProgress.getFound() + " / " + lTotalProgress.getTotal() + "   " + lTotalPercent + "%");
        iTotalProgressBar.setValue(lTotalPercent);
    }

    private String selectedNodeLabel() {
        Object lComponent = iTree.getLastSelectedPathComponent();
        if (lComponent instanceof DefaultMutableTreeNode) {
            Object lUserObject = ((DefaultMutableTreeNode) lComponent).getUserObject();
            if (lUserObject instanceof TreeNodeData) {
                return ((TreeNodeData) lUserObject).label;
            }
        }
        return "Selection";
    }

    /**
     * Plan section 6.2's warning banner: a file that only loaded part way through (a real, if
     * rare, failure mode -- see CLAUDE.md) makes every count in this window a potential
     * undercount, and a player who doesn't know that could easily mistake "not shown" for "not
     * owned". D2Character.isItemsIncomplete() and D2SharedStash.hasVisibleIncompletePane() are the
     * two existing signals for this (plan section 6.2); .d2x ATMA stashes have no equivalent
     * tracked failure mode today, so they are silently assumed complete.
     */
    private void refreshPartialLoadBanner() {
        List<String> lIncompleteFiles = new ArrayList<String>();
        for (D2ItemList lList : iFileManager.getOpenItemLists()) {
            try {
                if (lList instanceof D2Character && ((D2Character) lList).isItemsIncomplete()) {
                    lIncompleteFiles.add(lList.getFilename());
                } else if (lList instanceof D2SharedStash && ((D2SharedStash) lList).hasVisibleIncompletePane()) {
                    lIncompleteFiles.add(lList.getFilename());
                }
            } catch (RuntimeException pEx) {
                // One list's incompleteness check failing must not hide the banner for every
                // other genuinely-incomplete file, nor crash the refresh.
            }
        }

        if (lIncompleteFiles.isEmpty()) {
            iPartialLoadBanner.setVisible(false);
            iPartialLoadBanner.setToolTipText(null);
            return;
        }

        iPartialLoadBanner.setVisible(true);
        iPartialLoadBanner.setText(lIncompleteFiles.size() + " file(s) loaded partially - counts may be incomplete");
        StringBuilder lTooltip = new StringBuilder();
        for (String lFile : lIncompleteFiles) {
            if (lTooltip.length() > 0) {
                lTooltip.append(", ");
            }
            lTooltip.append(lFile);
        }
        iPartialLoadBanner.setToolTipText(lTooltip.toString());
    }

    // ------------------------------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------------------------------

    private void onExport() {
        JFileChooser lChooser = new JFileChooser();
        lChooser.setSelectedFile(new File("holy-grail-export.txt"));
        if (lChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        PrintWriter lWriter = null;
        try {
            lWriter = new PrintWriter(new FileWriter(lChooser.getSelectedFile()));
            if (iModel.getTab() == D2GrailKey.Type.SET) {
                for (D2GrailModel.SetGroup lGroup : iModel.getSetGroups()) {
                    lWriter.println("== " + lGroup.getSetName() + " ==  " + lGroup.getFound() + " / " + lGroup.getTotal());
                    for (D2GrailModel.Row lRow : lGroup.getRows()) {
                        lWriter.println("  " + exportLine(lRow));
                    }
                }
            } else {
                for (D2GrailModel.Row lRow : iModel.getRows()) {
                    lWriter.println(exportLine(lRow));
                }
            }
        } catch (IOException pEx) {
            D2FileManager.displayErrorDialog(pEx);
        } finally {
            if (lWriter != null) {
                lWriter.close();
            }
        }
    }

    private static String exportLine(D2GrailModel.Row pRow) {
        D2GrailEntry lEntry = pRow.getEntry();
        String lName = D2ItemRenderer.stripColorCodes(lEntry.getDisplayName());
        if (!lEntry.isChronicle()) {
            lName = lName + " *";
        }
        if (pRow.isFound()) {
            D2GrailFinding lFinding = pRow.getFinding();
            StringBuilder lLine = new StringBuilder(lName).append(" - Found x").append(lFinding.getCopies());
            // Display names only (e.g. "Barbarian.d2s") -- never getFileNames()'s full paths,
            // which exist solely for D2FileManager.focusFileWindow()'s double-click lookup.
            java.util.Iterator<String> lFiles = lFinding.getFileDisplayNames().iterator();
            if (lFiles.hasNext()) {
                lLine.append(" (");
                while (lFiles.hasNext()) {
                    lLine.append(lFiles.next());
                    if (lFiles.hasNext()) {
                        lLine.append(", ");
                    }
                }
                lLine.append(")");
            }
            return lLine.toString();
        }
        return lName + " - Missing";
    }

    // ------------------------------------------------------------------------------------------
    // Scanning / D2ItemListListener / D2ItemContainer
    // ------------------------------------------------------------------------------------------

    /**
     * Keeps this window's D2ItemListListener subscriptions in sync with whatever is actually open
     * right now, then rescans. Called both by D2FileManager whenever a file opens or closes (plan
     * section 6.3) and once from {@link #connect()} at construction time. Safe to call repeatedly:
     * it only (un)subscribes lists whose open/closed state actually changed since the last call.
     */
    public void refreshLists() {
        Collection<D2ItemList> lCurrent = iFileManager.getOpenItemLists();
        Set<D2ItemList> lCurrentSet = new HashSet<D2ItemList>(lCurrent);

        Iterator<D2ItemList> lIt = iSubscribedLists.iterator();
        while (lIt.hasNext()) {
            D2ItemList lList = lIt.next();
            if (!lCurrentSet.contains(lList)) {
                try {
                    lList.removeD2ItemListListener(this);
                } catch (RuntimeException pEx) {
                    // A list that is already gone/broken can't be unsubscribed from cleanly, but
                    // it is being dropped from iSubscribedLists either way.
                }
                lIt.remove();
            }
        }
        for (D2ItemList lList : lCurrent) {
            if (lList != null && iSubscribedLists.add(lList)) {
                try {
                    lList.addD2ItemListListener(this);
                } catch (RuntimeException pEx) {
                    iSubscribedLists.remove(lList);
                }
            }
        }

        itemListChanged();
    }

    /**
     * The single rescan-and-redraw entry point (plan section 6.3): called directly by any
     * subscribed D2ItemList on its own changes, and indirectly by {@link #refreshLists()} whenever
     * the set of open files itself changes. A rescan is independent of every filter (plan section
     * 6.2): it always records every discovery, Chronicle or not -- see D2GrailScanner's own
     * class javadoc -- so no filter change in this window ever needs to call this.
     */
    public void itemListChanged() {
        try {
            Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(iFileManager.getOpenItemLists());
            iModel.setFindings(lFindings);
            recordFirstSeen(lFindings);
        } catch (RuntimeException pEx) {
            // D2GrailScanner.scan() already never throws by contract, but a rescan triggered by
            // some future caller must still never be able to take this window down; keep whatever
            // findings were last computed rather than losing them.
        }
        refreshList();
        refreshStats();
        refreshPartialLoadBanner();
        refreshNoFilesHint();
    }

    /**
     * A UX fix found by actually running the window: with nothing open, every count reads
     * "0 / 419" -- correct (the grail only counts files open in a window, per the plan), but
     * indistinguishable from a bug at a glance. An explicit hint in the list area, styled like the
     * partial-load banner, replaces the otherwise-empty list only in that one case.
     */
    private void refreshNoFilesHint() {
        iNoFilesHint.setVisible(iFileManager.getOpenItemLists().isEmpty());
    }

    /**
     * Plan section 8: timestamp every key this scan saw, Chronicle or not -- filtering which
     * dates are ever SHOWN is a display concern (the "Include non-Chronicle items" checkbox), but
     * an entry found today while unchecked must already have a date the moment the box is later
     * checked, not a blank one. D2GrailFirstSeenStore itself refuses to overwrite an existing
     * date, so calling this on every single rescan is always safe.
     */
    private void recordFirstSeen(Map<D2GrailKey, D2GrailFinding> pFindings) {
        if (iFirstSeenStore == null || pFindings.isEmpty()) {
            return;
        }
        if (iFirstSeenStore.recordFirstSeenIfAbsent(pFindings.keySet(), System.currentTimeMillis())) {
            iFirstSeenStore.save();
        }
    }

    public void connect() {
        refreshLists();
    }

    public void disconnect(Exception pEx) {
        for (D2ItemList lList : new ArrayList<D2ItemList>(iSubscribedLists)) {
            try {
                lList.removeD2ItemListListener(this);
            } catch (RuntimeException pIgnored) {
                // Best-effort: the goal here is leaving no dangling subscription, not surfacing
                // a problem with a list that is likely already being torn down itself.
            }
        }
        iSubscribedLists.clear();
    }

    public String getFileName() {
        return FILE_NAME;
    }

    /**
     * True if ANY currently open file is hardcore/softcore -- there is no single meaningful answer
     * for a window that aggregates every open file at once, so this errs toward "true" rather than
     * silently reporting "false" for a project that actually has HC content open.
     */
    public boolean isHC() {
        for (D2ItemList lList : iFileManager.getOpenItemLists()) {
            try {
                if (lList.isHC()) {
                    return true;
                }
            } catch (RuntimeException pEx) {
                // one broken list's HC/SC check must not stop this from answering for the rest.
            }
        }
        return false;
    }

    public boolean isSC() {
        for (D2ItemList lList : iFileManager.getOpenItemLists()) {
            try {
                if (lList.isSC()) {
                    return true;
                }
            } catch (RuntimeException pEx) {
                // see isHC()'s comment.
            }
        }
        return false;
    }

    public void closeView() {
        disconnect(null);
        iFileManager.removeFromOpenWindows(this);
        dispose();
    }

    /**
     * This window never edits an item -- it is a read-only report over whatever is already open --
     * so it is never "modified" in the save-on-close sense D2ItemContainer's other implementers
     * use this for.
     */
    public boolean isModified() {
        return false;
    }

    /**
     * D2ItemContainer's contract wants a single D2ItemList, but this window genuinely aggregates
     * every open one; returning D2ItemListAll here would silently reintroduce the exact ".d2i is
     * invisible" bug this whole feature exists to avoid (see D2GrailScanner's class javadoc). This
     * inert stub satisfies every blind caller in D2FileManager (Pick All/Drop All acting on it is a
     * no-op rather than dumping every found item into the clipboard, checkAll()'s timestamp poll
     * sees "unchanged" and leaves this window alone) without ever being the thing D2GrailScanner
     * actually scans -- that is always D2FileManager.getOpenItemLists() directly, in
     * {@link #itemListChanged()}.
     */
    public D2ItemList getItemLists() {
        return iInertItemList;
    }

    private static final class InertItemList implements D2ItemList {
        public void ignoreItemListEvents() {
        }

        public void listenItemListEvents() {
        }

        public boolean containsItem(D2Item pItem) {
            return false;
        }

        public void removeItem(D2Item pItem) {
        }

        public void addItem(D2Item pItem) {
        }

        public List getItemList() {
            return Collections.emptyList();
        }

        public int getNrItems() {
            return 0;
        }

        public String getFilename() {
            return FILE_NAME;
        }

        public boolean isModified() {
            return false;
        }

        public void addD2ItemListListener(D2ItemListListener pListener) {
        }

        public void removeD2ItemListListener(D2ItemListListener pListener) {
        }

        public boolean hasD2ItemListListener() {
            return false;
        }

        public void save(D2Project pProject) {
        }

        public boolean isSC() {
            return false;
        }

        public boolean isHC() {
            return false;
        }

        public void fullDump(PrintWriter pWriter) {
        }

        public void initTimestamp() {
        }

        public boolean checkTimestamp() {
            return true;
        }

        public void fireD2ItemListEvent() {
        }
    }
}
