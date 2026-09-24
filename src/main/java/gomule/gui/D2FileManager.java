/*******************************************************************************
 *
 * Copyright 2007 Andy Theuninck & Randall
 *
 * This file is part of gomule.
 *
 * gomule is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * gomule is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * gomlue; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 *
 ******************************************************************************/
package gomule.gui;

import com.google.common.collect.Streams;
import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2s.D2Character;
import gomule.d2x.D2Stash;
import gomule.gui.sharedStash.D2ViewSharedStash;
import gomule.item.D2Item;
import gomule.util.D2Project;
import gomule.util.D2ProjectRegistry;
import gomule.util.D2UserData;
import randall.d2files.D2TxtFile;
import randall.flavie.Flavie;
import randall.util.RandallPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static javax.swing.JOptionPane.OK_CANCEL_OPTION;

/**
 * this class is the top-level administrative window.
 * It contains all internal frames
 * it contains all open files
 */
public class D2FileManager extends JFrame {
    /**
     *
     */
    private static final long serialVersionUID = 4010435064410504579L;

    private static final String CURRENT_VERSION = "R0.44: Resurrected";
    private static final D2FileManager iCurrent = new D2FileManager();
    private final D2SharedStashReader sharedStashReader;
    private final JSplitPane lSplit;
    private final JSplitPane rSplit;
    private HashMap iItemLists = new HashMap();
    private ArrayList iOpenWindows;
    private JMenuBar iMenuBar;
    private JPanel iContentPane;
    private JDesktopPane iDesktopPane;
    private JToolBar iToolbar;
    private Properties iProperties;
    private D2Project iProject;
    //	private JButton              iBtnProjectSelection;
    private D2ViewProject iViewProject;
    private D2ViewClipboard iClipboard;
    private D2ViewStash iViewAll;
    // The single (at most one, per getFileName()'s dedup) open Holy Grail window, if any. Tracked
    // the same way iViewAll is, so addItemList()/removeItemList() below can push it a fresh
    // snapshot of every open file whenever one opens or closes -- see D2ViewGrail.refreshLists().
    private D2ViewGrail iGrailView;
    private boolean iIgnoreCheckAll = false;
    //	private JMenuBar D2JMenu;
    //	private JMenu file;
    //	private JMenu edit;

    private JPanel iRightPane;
    private RandallPanel iLeftPane;

    private DefaultComboBoxModel iProjectModel;

    private JComboBox iChangeProject;

    private JButton dropAll;

    private JButton pickFrom;

    private JComboBox pickChooser;

    private JButton dropTo;

    private JComboBox dropChooser;

    private JButton pickAll;

    private JButton dumpBut;

    private JButton flavieSingle;

    // Project-control widgets kept as fields (rather than createLeftPane() locals, as before) so
    // updateProjectDependentUI() can enable/disable them from outside that method -- plan section
    // 5, step 2.
    private JButton iDelProjButton;
    private JButton iClProjButton;
    private JButton iFlavieButton;
    private JButton iProjTextDumpButton;

    // The "Project Control" box holding those four buttons. With no project open they are not
    // merely unusable but meaningless -- there is no project to delete, clear or report on -- so
    // the whole box is hidden rather than greyed out, leaving the empty state showing only what
    // can actually be acted on.
    private RandallPanel iProjControlPanel;

    // File-menu items whose enabled state depends on a project being open (plan section 5, step
    // 2) -- likewise promoted to fields for the same reason. "New Project..."/"Open Project..."
    // are deliberately NOT among these: they must stay clickable with no project open, since they
    // are the only way to ever get one.
    private JMenuItem iMenuItemCloseProject;
    private JMenuItem iMenuItemOpenChar;
    private JMenuItem iMenuItemNewStash;
    private JMenuItem iMenuItemOpenStash;
    private JMenuItem iMenuItemSaveAll;
    private JMenuItem iMenuItemOpenGrail;
    private JMenu iProjMenu;

    // Set while the combo box's own selection is being reset programmatically (a Cancel from
    // confirmCloseProject(), or a plain model rebuild) so its ItemListener below can tell that
    // apart from a real user pick -- without this flag, restoring the previous selection after a
    // Cancel would itself fire itemStateChanged() and recurse into confirmCloseProject() again
    // (plan section 6, "Cancel qui ne annule pas").
    private boolean iIgnoreProjectSelection = false;

    // Reused by "New Project..."'s validation (plan section 5, step 4) -- the exact same
    // characters the old left-pane "New Proj" button used to reject.
    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("[^/?*:;{}\\\\]+", Pattern.UNIX_LINES);

    private D2FileManager() {
        D2TxtFile.constructTxtFiles("d2111");
        sharedStashReader = new D2SharedStashReader();
        iOpenWindows = new ArrayList();
        iContentPane = new JPanel();
        iDesktopPane = new JDesktopPane();
        iDesktopPane.setDragMode(1);

        iContentPane.setLayout(new BorderLayout());

        createToolbar();
        createMenubar();
        createLeftPane();
        createRightPane();

        lSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, iLeftPane, iDesktopPane);
        rSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, lSplit, iRightPane);

        setExtendedState(parseInt(iProperties.getProperty("win-state", "0")));
        lSplit.setDividerLocation(parseInt(iProperties.getProperty("win-ldiv-loc", "200")));
        rSplit.setDividerLocation(parseInt(iProperties.getProperty("win-rdiv-loc", "814")));
        rSplit.setResizeWeight(1.0);
        iContentPane.add(rSplit, BorderLayout.CENTER);
        setContentPane(iContentPane);
        setBounds(new Rectangle(
                parseInt(iProperties.getProperty("win-x", "0")),
                parseInt(iProperties.getProperty("win-y", "0")),
                parseInt(iProperties.getProperty("win-width", "1024")),
                parseInt(iProperties.getProperty("win-height", "768"))));
        setTitle(true);
        // Every widget this touches (menu items, iToolbar's buttons, the left-pane project
        // controls) now exists -- this is the first point in the constructor where it's safe to
        // call, and it's what puts the app into the right state for whatever checkProjects()
        // decided above (a project open, or none at all after a previous explicit Close).
        updateProjectDependentUI();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.getGlassPane().setVisible(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeListener();
            }

            public void windowActivated(WindowEvent e) {
                checkAll(false);
            }
        });
        setVisible(true);
        iClipboard.scrollbarBottom();
        new ApplicationRunningChecker(
                Runtime.getRuntime(),
                "D2R.exe",
                () -> JOptionPane.showMessageDialog(
                        this,
                        "Diablo 2 Resurrected is currently running, changes in GoMule are unlikely to be applied and you may lose changes when you exit D2R.",
                        "Warning: D2R.exe Running",
                        JOptionPane.INFORMATION_MESSAGE));
    }

    private void setTitle(boolean saved) {
        // The project name (or its absence) is the main visual feedback for the new "no project
        // open" state (plan section 5, step 4) -- there is otherwise nothing else on screen that
        // says so at a glance once the tree and clipboard are both empty.
        String lProjectLabel = (iProject != null) ? iProject.getProjectName() : "(no project)";
        setTitle("GoMule " + CURRENT_VERSION + " [" + lProjectLabel + "]" + (saved ? " - Saved" : ""));
    }

    public static D2FileManager getInstance() {
        return iCurrent;
    }

    public static void displayErrorDialog(Exception pException) {
        displayErrorDialog(iCurrent, pException);
    }

    public static void displayErrorDialog(Window pParent, Exception pException) {
        pException.printStackTrace();

        String lText = "Error\n\n" + pException.getMessage() + "\n";

        StackTraceElement trace[] = pException.getStackTrace();
        for (int i = 0; i < trace.length; i++) {
            lText += "\tat " + trace[i] + "\n";
        }

        displayTextDialog(pParent, "Error", lText);
    }

    public static void displayTextDialog(String pTitle, String pText) {
        displayTextDialog(iCurrent, pTitle, pText);
    }

    public static void displayTextDialog(Window pParent, String pTitle, String pText) {
        JDialog lDialog;
        if (pParent instanceof JFrame) {
            lDialog = new JDialog((JFrame) pParent, pTitle, true);
        } else {
            lDialog = new JDialog((JDialog) pParent, pTitle, true);
        }
        RandallPanel lPanel = new RandallPanel();
        JTextArea lTextArea = new JTextArea();
        JScrollPane lScroll = new JScrollPane(lTextArea);

        if (pTitle.equalsIgnoreCase("error")) {
            lScroll.setPreferredSize(new Dimension(640, 480));
        }
        lPanel.addToPanel(lScroll, 0, 0, 1, RandallPanel.BOTH);

        lTextArea.setText(pText);
        if (pText.length() > 1) {
            lTextArea.setCaretPosition(0);
        }
        lTextArea.setEditable(false);

        lDialog.setContentPane(lPanel);
        lDialog.pack();
        lDialog.setLocationRelativeTo(null);
        lDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        lDialog.setVisible(true);
    }

    /**
     * Rebuilds the combo box's model from the persisted recent-projects registry (plan section
     * 4) -- no longer a directory listing of {@code projects/}, now that a project can live
     * anywhere on disk. Elements are the project directories themselves (a File, not a bare
     * name): two projects that happen to share a folder name in two different locations must
     * stay distinguishable, which is exactly what the combo's renderer (see createLeftPane())
     * uses the full path for.
     */
    private void checkProjectsModel() {
        // The whole rebuild runs under iIgnoreProjectSelection, and that is load-bearing, not
        // belt-and-braces: DefaultComboBoxModel.addElement() SELECTS the element it adds when the
        // model was empty and nothing was selected, firing a SELECTED ItemEvent. Unguarded, the
        // very first addElement() below therefore looked to the combo's listener like the user
        // picking a project -- so "Close Project", which calls this right after clearing the
        // project, immediately re-opened one behind the user's back: the tree stayed full of the
        // supposedly closed project's characters and stashes while the combo showed blank.
        iIgnoreProjectSelection = true;
        try {
            D2ProjectRegistry.purgeMissing(iProperties);
            iProjectModel.removeAllElements();
            for (File lDir : D2ProjectRegistry.getRecentProjects(iProperties)) {
                iProjectModel.addElement(lDir.getAbsoluteFile());
            }
            if (iProject != null
                    && iProjectModel.getIndexOf(iProject.getProjectDirFile().getAbsoluteFile()) == -1) {
                iProjectModel.addElement(iProject.getProjectDirFile().getAbsoluteFile());
            }
        } finally {
            iIgnoreProjectSelection = false;
        }
    }

    private void createLeftPane() {

        iViewProject = new D2ViewProject(this);
        iViewProject.setPreferredSize(new Dimension(190, 500));
        iViewProject.setProject(iProject);
        iViewProject.refreshTreeModel(true, true, true);
        iLeftPane = new RandallPanel();

        iProjectModel = new DefaultComboBoxModel();
        checkProjectsModel();
        iChangeProject = new JComboBox(iProjectModel);
        iChangeProject.setPreferredSize(new Dimension(190, 20));
        // The model holds project directories (File), not names, now that a project can live
        // anywhere on disk (plan section 4) -- shown as just the folder name, with the full path
        // as a tooltip so two same-named projects in different locations stay distinguishable.
        iChangeProject.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                    JList lList, Object lValue, int lIndex, boolean lIsSelected, boolean lHasFocus) {
                Component lComponent = super.getListCellRendererComponent(lList, lValue, lIndex, lIsSelected, lHasFocus);
                if (lValue instanceof File && lComponent instanceof JLabel) {
                    File lDir = (File) lValue;
                    ((JLabel) lComponent).setText(lDir.getName());
                    ((JLabel) lComponent).setToolTipText(lDir.getAbsolutePath());
                }
                return lComponent;
            }
        });
        if (iProject != null) {
            iChangeProject.setSelectedItem(iProject.getProjectDirFile().getAbsoluteFile());
        }
        iChangeProject.addItemListener(new ItemListener() {

            public void itemStateChanged(ItemEvent arg0) {
                // Set only while THIS code is itself resetting the selection (a Cancel restoring
                // the previous project, or checkProjectsModel() re-adding it) -- see
                // iIgnoreProjectSelection's own field javadoc for why this guard exists at all.
                if (iIgnoreProjectSelection) {
                    return;
                }
                if (arg0.getStateChange() != ItemEvent.SELECTED) {
                    return;
                }
                File lNewProjectDir = ((File) arg0.getItem()).getAbsoluteFile();
                if (iProject != null && lNewProjectDir.equals(iProject.getProjectDirFile().getAbsoluteFile())) {
                    return; // already the open project -- nothing to switch.
                }
                if (!confirmCloseProject()) {
                    // Cancel: put the combo back on the project that is actually still open,
                    // WITHOUT re-firing this very listener (that would recurse straight back into
                    // confirmCloseProject() -- plan section 6's flagged trap).
                    iIgnoreProjectSelection = true;
                    try {
                        iChangeProject.setSelectedItem(
                                iProject == null ? null : iProject.getProjectDirFile().getAbsoluteFile());
                    } finally {
                        iIgnoreProjectSelection = false;
                    }
                    return;
                }
                closeWindows();
                openProject(lNewProjectDir);
            }
        });

        RandallPanel projControl = new RandallPanel();
        iProjControlPanel = projControl;
        projControl.setPreferredSize(new Dimension(190, 150));
        projControl.setBorder(new TitledBorder(
                null, ("Project Control"), TitledBorder.LEFT, TitledBorder.TOP, iLeftPane.getFont(), Color.gray));

        // No "New Proj" button any more: File / New Project... (createMenubar()) is now the only
        // way to create a project (plan section 5, step 4: "on garde New Project... comme unique
        // chemin de création") -- keeping this button too, on top of the removal of the old
        // (D2FileManager, String) constructor it relied on, would mean reimplementing the exact
        // same dialog twice.
        iDelProjButton = new JButton("Del Proj");

        iDelProjButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (iProject == null) {
                    return; // guarded by updateProjectDependentUI() anyway; defensive no-op.
                }
                if (iProject.getProjectDirFile().getAbsoluteFile()
                        .equals(D2UserData.getDefaultProjectDir().getAbsoluteFile())) {
                    JOptionPane.showMessageDialog(
                            iContentPane, "Cannot delete the default project!", "Error!", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (JOptionPane.showConfirmDialog(
                                iContentPane,
                                "Are you sure you want to delete this project? (Your clipboard will be lost!)",
                                "Really?",
                                JOptionPane.YES_NO_OPTION)
                        != 0) {
                    return;
                }
                // Deleting the directory out from under an open clipboard/tree would mean reading
                // from files that are about to vanish -- close everything down (with the usual
                // save prompt) before switching to the default project, exactly like New/Open
                // Project do.
                if (!confirmCloseProject()) {
                    return;
                }
                D2Project lToDelete = iProject;
                closeWindows();
                openProject(D2UserData.getDefaultProjectDir());
                if (!lToDelete.delProj()) {
                    JOptionPane.showMessageDialog(
                            iContentPane, "Error deleting project!", "Error!", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        iClProjButton = new JButton("Clear Proj");

        iClProjButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (iProject == null) {
                    return;
                }
                if (JOptionPane.showConfirmDialog(
                                iContentPane,
                                "Are you sure you want to clear this project?",
                                "Really?",
                                JOptionPane.YES_NO_OPTION)
                        != 0) {
                    return;
                }
                if (!confirmCloseProject()) {
                    return;
                }
                closeWindows();
                if (!iProject.clearProj()) {
                    JOptionPane.showMessageDialog(
                            iContentPane, "Error clearing project!", "Error!", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        iFlavieButton = new JButton("Proj Flavie Report");

        iFlavieButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent pEvent) {

                ArrayList dFileNames = new ArrayList();

                ArrayList lCharList = iProject.getCharList();
                if (lCharList != null) {
                    dFileNames.addAll(lCharList);
                }
                ArrayList lStashList = iProject.getStashList();
                if (lStashList != null) {
                    dFileNames.addAll(lStashList);
                }
                ArrayList sharedStashList = iProject.getSharedStashList();
                if (sharedStashList != null) {
                    dFileNames.addAll(sharedStashList);
                }
                if (dFileNames.size() < 1) {
                    JOptionPane.showMessageDialog(
                            iContentPane, "No Chars/Stashes in Project!", "Fail!", JOptionPane.ERROR_MESSAGE);
                } else {
                    flavieDump(dFileNames, false);
                }
            }
        });

        iProjTextDumpButton = new JButton("Proj Txt Dump");

        iProjTextDumpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent pEvent) {
                workCursor();
                ArrayList lDumpList = iProject.getCharList();
                String errStr = "";
                if (lDumpList != null) {
                    for (int x = 0; x < lDumpList.size(); x++) {
                        try {
                            D2Character d2Char = new D2Character((String) lDumpList.get(x));
                            if (!projTxtDump(
                                    (String) lDumpList.get(x),
                                    (D2ItemList) d2Char,
                                    iProject.getProjectName() + "Dumps")) {
                                errStr = errStr + "Char: " + (String) lDumpList.get(x) + " failed.\n";
                            }
                        } catch (Exception e) {
                            errStr = errStr + "Char: " + (String) lDumpList.get(x) + " failed.\n";
                            e.printStackTrace();
                        }
                    }
                }

                lDumpList = iProject.getStashList();
                if (lDumpList != null) {
                    for (int x = 0; x < lDumpList.size(); x++) {
                        try {
                            D2Stash d2Stash = new D2Stash((String) lDumpList.get(x));
                            if (!projTxtDump(
                                    (String) lDumpList.get(x),
                                    (D2ItemList) d2Stash,
                                    iProject.getProjectName() + "Dumps")) {
                                errStr = errStr + "Stash: " + (String) lDumpList.get(x) + " failed.\n";
                            }
                        } catch (Exception e) {
                            errStr = errStr + "Stash: " + (String) lDumpList.get(x) + " failed.\n";
                            e.printStackTrace();
                        }
                    }
                }
                lDumpList = iProject.getSharedStashList();
                if (lDumpList != null) {
                    for (int x = 0; x < lDumpList.size(); x++) {
                        try {
                            D2SharedStash d2SharedStash =
                                    new D2SharedStashReader().readStash((String) lDumpList.get(x));
                            if (!projTxtDump(
                                    (String) lDumpList.get(x),
                                    (D2ItemList) d2SharedStash,
                                    iProject.getProjectName() + "Dumps")) {
                                errStr = errStr + "Shared Stash: " + (String) lDumpList.get(x) + " failed.\n";
                            }
                        } catch (Exception e) {
                            errStr = errStr + "Shared Stash: " + (String) lDumpList.get(x) + " failed.\n";
                            e.printStackTrace();
                        }
                    }
                }
                if ((iProject.getCharList().size() + iProject.getStashList().size()) < 1) {
                    JOptionPane.showMessageDialog(
                            iContentPane, "No Chars/Stashes in Project!", "Fail!", JOptionPane.ERROR_MESSAGE);
                } else if (errStr.equals("")) {
                    JOptionPane.showMessageDialog(
                            iContentPane,
                            "Dumps generated successfully.\nOutput Folder: " + System.getProperty("user.dir")
                                    + File.separatorChar + iProject.getProjectName() + "Dumps",
                            "Success!",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(
                            iContentPane,
                            "Some txt dumps failed (error msg below).\nOutput Folder: " + System.getProperty("user.dir")
                                    + File.separatorChar + iProject.getProjectName() + "Dumps" + "\n\nError: \n"
                                    + errStr,
                            "Fail!",
                            JOptionPane.ERROR_MESSAGE);
                }
                defaultCursor();
            }
        });

        projControl.addToPanel(iDelProjButton, 0, 0, 2, RandallPanel.HORIZONTAL);
        projControl.addToPanel(iClProjButton, 0, 1, 2, RandallPanel.HORIZONTAL);
        projControl.addToPanel(iFlavieButton, 0, 2, 2, RandallPanel.HORIZONTAL);
        projControl.addToPanel(iProjTextDumpButton, 0, 3, 2, RandallPanel.HORIZONTAL);

        iLeftPane.addToPanel(iChangeProject, 0, 0, 1, RandallPanel.HORIZONTAL);
        iLeftPane.addToPanel(iViewProject, 0, 1, 1, RandallPanel.BOTH);
        iLeftPane.addToPanel(projControl, 0, 2, 1, RandallPanel.NONE);
    }

    private void flavieDump(ArrayList dFileNames, boolean singleDump) {
        try {
            String reportName;
            if (singleDump) {
                String fileName = ((D2ItemContainer)
                                iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                        .getFileName();
                if (fileName.endsWith(".d2s")) {
                    reportName = (((D2ViewChar) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                                    .getChar()
                                    .getCharName()
                            + iProject.getReportName());

                } else if (fileName.endsWith(".d2i")) {
                    reportName = ((((D2ViewSharedStash)
                                            iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()))))
                                    .getSharedStashName()
                            + iProject.getReportName());
                    reportName = reportName.replace(".d2i", "");
                } else {
                    reportName =
                            ((((D2ViewStash) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()))))
                                            .getStashName()
                                    + iProject.getReportName());
                    reportName = reportName.replace(".d2x", "");
                }
            } else {
                reportName = iProject.getProjectName() + iProject.getReportName();
            }
            new Flavie(
                    reportName,
                    iProject.getReportTitle(),
                    iProject.getDataName(),
                    iProject.getStyleName(),
                    dFileNames,
                    iProject.isCountAll(),
                    iProject.isCountEthereal(),
                    iProject.isCountStash(),
                    iProject.isCountChar());
            //			JOptionPane.showMessageDialog(iContentPane,
            //			"Flavie says reports generated successfully.\nFile: " + System.getProperty("user.dir") +
            // File.separatorChar + reportName + ".html",
            //			"Success!", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception pEx) {
            JOptionPane.showMessageDialog(iContentPane, "Flavie report failed!", "Fail!", JOptionPane.ERROR_MESSAGE);
            displayErrorDialog(pEx);
        }
    }

    private void createRightPane() {

        iRightPane = new JPanel();
        iRightPane.setPreferredSize(new Dimension(190, 768));
        iRightPane.setMinimumSize(new Dimension(190, 0));
        iRightPane.setLayout(new BoxLayout(iRightPane, BoxLayout.Y_AXIS));
        try {
            iClipboard = D2ViewClipboard.getInstance(this);
        } catch (Exception pEx) {
            pEx.printStackTrace();
            JTextArea lText = new JTextArea();
            lText.setText(pEx.getMessage());
            JScrollPane lScroll = new JScrollPane(lText);
            iContentPane.removeAll();
            iContentPane.add(lScroll, BorderLayout.CENTER);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            addWindowListener(new java.awt.event.WindowAdapter() {
                public void windowClosing(java.awt.event.WindowEvent e) {
                    System.exit(0);
                }
            });
        }

        RandallPanel itemControl = new RandallPanel();
        itemControl.setBorder(new TitledBorder(
                null, ("Item Control"), TitledBorder.LEFT, TitledBorder.TOP, iRightPane.getFont(), Color.gray));

        itemControl.setPreferredSize(new Dimension(190, 160));
        itemControl.setSize(new Dimension(190, 160));
        itemControl.setMaximumSize(new Dimension(190, 160));
        itemControl.setMinimumSize(new Dimension(190, 160));

        pickAll = new JButton("Pick All");
        pickAll.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()) > -1) {
                    D2ItemContainer d2ItemContainer =
                            (D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()));
                    D2ItemList iList = d2ItemContainer.getItemLists();
                    iList.ignoreItemListEvents();
                    try {

                        if (iList.getFilename().endsWith(".d2s") && getProject().getIgnoreItems()) {

                            for (int x = 0; x < iList.getNrItems(); x++) {

                                if (((D2Item) iList.getItemList().get(x)).isMoveable()) {
                                    moveToClipboard(
                                            ((D2Item) iList.getItemList().get(x)), iList);
                                    x--;
                                }
                            }

                        } else if (d2ItemContainer instanceof D2ViewSharedStash) {
                            D2ViewSharedStash viewSharedStash = ((D2ViewSharedStash) d2ItemContainer);
                            D2ViewClipboard.addItems(
                                    viewSharedStash.getSharedStashPanel().removeAllItems());
                        } else {

                            for (int x = 0; x < iList.getNrItems(); x++) {
                                moveToClipboard(((D2Item) iList.getItemList().get(x)), iList);
                                x--;
                            }
                        }
                    } finally {
                        iList.listenItemListEvents();
                        iList.fireD2ItemListEvent();
                    }
                }
            }
        });

        dropAll = new JButton("Drop All");
        dropAll.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()) > -1) {
                    D2ItemContainer d2ItemContainer =
                            (D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()));
                    D2ItemList iList = d2ItemContainer.getItemLists();
                    iList.ignoreItemListEvents();
                    try {
                        if (((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                                .getFileName()
                                .endsWith(".d2s")) {

                            D2ViewChar iCharacter = ((D2ViewChar)
                                    iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())));
                            for (int x = 2; x > -1; x--) {
                                iCharacter.putOnCharacter(x, D2ViewClipboard.getItemList());
                            }
                        } else if (d2ItemContainer instanceof D2ViewSharedStash) {
                            D2ViewSharedStash viewSharedStash = ((D2ViewSharedStash) d2ItemContainer);
                            //noinspection unchecked
                            List<D2Item> successfullyAddedItems =
                                    viewSharedStash.getSharedStashPanel().tryToAddItems(D2ViewClipboard.getItemList());
                            successfullyAddedItems.forEach(D2ViewClipboard::removeItem);
                        } else {
                            ArrayList lItemList = D2ViewClipboard.removeAllItems();
                            while (lItemList.size() > 0) {
                                iList.addItem((D2Item) lItemList.remove(0));
                            }
                        }
                    } finally {
                        iList.listenItemListEvents();
                        iList.fireD2ItemListEvent();
                    }
                }
            }
        });

        pickFrom = new JButton("Pickup From ...");
        pickChooser = new JComboBox(new String[] {"Stash", "Inventory", "Cube", "Equipped"});
        pickFrom.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {

                if (iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()) > -1) {
                    D2ItemList iList = ((D2ItemContainer)
                                    iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                            .getItemLists();
                    iList.ignoreItemListEvents();
                    try {
                        for (int x = 0; x < iList.getNrItems(); x++) {
                            D2Item remItem = ((D2Item) iList.getItemList().get(x));
                            if (!remItem.isMoveable()
                                    && pickChooser.getSelectedIndex() != 3
                                    && getProject().getIgnoreItems()) {
                                continue;
                            }
                            switch (pickChooser.getSelectedIndex()) {
                                case 0:
                                    if (remItem.get_location() == 0 && remItem.get_panel() == 5) {
                                        moveToClipboard(remItem, iList);
                                        x--;
                                    }
                                    break;
                                case 1:
                                    if (remItem.get_location() == 0 && remItem.get_panel() == 1) {
                                        moveToClipboard(remItem, iList);
                                        x--;
                                    }
                                    break;
                                case 2:
                                    if (remItem.get_location() == 0 && remItem.get_panel() == 4) {
                                        moveToClipboard(remItem, iList);
                                        x--;
                                    }
                                    break;
                                case 3:
                                    if (remItem.get_location() == 1) {
                                        moveToClipboard(remItem, iList);
                                        x--;
                                    }
                                    break;
                            }
                        }
                    } finally {
                        iList.listenItemListEvents();
                        iList.fireD2ItemListEvent();
                    }
                }
            }
        });

        dropTo = new JButton("Drop To ...");
        dropChooser = new JComboBox(new String[] {"Stash", "Inventory", "Cube"});

        dropTo.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
                if (iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()) > -1) {
                    D2ViewChar iCharacter =
                            ((D2ViewChar) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())));
                    iCharacter.putOnCharacter(dropChooser.getSelectedIndex(), D2ViewClipboard.getItemList());
                }
            }
        });

        itemControl.addToPanel(pickAll, 0, 0, 1, RandallPanel.HORIZONTAL);
        itemControl.addToPanel(dropAll, 1, 0, 1, RandallPanel.HORIZONTAL);

        itemControl.addToPanel(pickFrom, 0, 1, 2, RandallPanel.HORIZONTAL);

        itemControl.addToPanel(pickChooser, 0, 2, 2, RandallPanel.HORIZONTAL);

        itemControl.addToPanel(dropTo, 0, 3, 2, RandallPanel.HORIZONTAL);

        itemControl.addToPanel(dropChooser, 0, 4, 2, RandallPanel.HORIZONTAL);

        RandallPanel charControl = new RandallPanel();
        charControl.setBorder(new TitledBorder(
                null, ("Output Control"), TitledBorder.LEFT, TitledBorder.TOP, iRightPane.getFont(), Color.gray));
        charControl.setPreferredSize(new Dimension(190, 80));
        charControl.setSize(new Dimension(190, 80));
        charControl.setMaximumSize(new Dimension(190, 80));
        charControl.setMinimumSize(new Dimension(190, 80));

        dumpBut = new JButton("Perform txt Dump");
        dumpBut.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
                if (iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()) > -1) {
                    if (singleTxtDump(
                            ((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                                    .getFileName())) {
                        JOptionPane.showMessageDialog(
                                iContentPane,
                                "Char/Stash dump was a success.\nFile: "
                                        + (((D2ItemContainer) iOpenWindows.get(
                                                        iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                                                .getFileName())
                                        + ".txt",
                                "Success!",
                                JOptionPane.INFORMATION_MESSAGE);

                    } else {
                        JOptionPane.showMessageDialog(
                                iContentPane, "Char/Stash dump failed!", "Fail!", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        flavieSingle = new JButton("Single Flavie Report");
        flavieSingle.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
                if (iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()) > -1) {
                    ArrayList dFileNames = new ArrayList();
                    dFileNames.add(
                            ((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                                    .getFileName());
                    flavieDump(dFileNames, true);
                }
            }
        });

        charControl.addToPanel(dumpBut, 0, 0, 1, RandallPanel.HORIZONTAL);
        charControl.addToPanel(flavieSingle, 0, 1, 1, RandallPanel.HORIZONTAL);

        iRightPane.add(iClipboard);
        iRightPane.add(itemControl);
        iRightPane.add(charControl);
        iRightPane.add(Box.createVerticalGlue());
    }

    private void moveToClipboard(D2Item remItem, D2ItemList iList) {
        iList.removeItem(remItem);
        if (((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                .getFileName()
                .endsWith(".d2s")) {
            ((D2Character) iList).unequipItem(remItem);
            ((D2ViewChar) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame()))).paintCharStats();
        }
        D2ViewClipboard.addItem(remItem);
    }

    private void createMenubar() {

        iMenuBar = new JMenuBar();
        iMenuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.lightGray));
        JMenu fileMenu = new JMenu("File");

        // The three project entries (plan section 5, step 4). Deliberately addActionListener
        // (below), not the addMouseListener the rest of this menu still uses: a MouseAdapter
        // only fires on an actual mouse press/release, so it silently breaks keyboard activation
        // (Enter on a focused menu item) and, more importantly here, the KeyStroke accelerators
        // these three carry -- Ctrl+Shift+N/Ctrl+O/Ctrl+W would be visible in the menu but simply
        // wouldn't do anything if wired the old way. The style divergence from the rest of this
        // method is intentional, not an oversight.
        JMenuItem newProject = new JMenuItem("New Project...");
        newProject.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));
        newProject.addActionListener(e -> doNewProject());

        JMenuItem openProject = new JMenuItem("Open Project...");
        openProject.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK));
        openProject.addActionListener(e -> doOpenProject());

        iMenuItemCloseProject = new JMenuItem("Close Project");
        iMenuItemCloseProject.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_MASK));
        iMenuItemCloseProject.addActionListener(e -> doCloseProject());

        iMenuItemOpenChar = new JMenuItem("Open Character");
        iMenuItemNewStash = new JMenuItem("New Stash");
        iMenuItemOpenStash = new JMenuItem("Open Stash");
        iMenuItemSaveAll = new JMenuItem("Save All");
        iMenuItemOpenGrail = new JMenuItem("Holy Grail");
        JMenu switchLookAndFeelMenu = new JMenu("Switch Appearance");
        for (LookAndFeelOptions lookAndFeelOption : LookAndFeelOptions.values()) {
            JMenuItem menuItem = new JMenuItem(lookAndFeelOption.getNameString());
            switchLookAndFeelMenu.add(menuItem);
            menuItem.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // "will be automatically saved" used to be true: closing the app wrote every
                    // modified file without asking. It now asks (confirmCloseProject(), plan
                    // section 5, step 3), so the wording has to say so -- and the choice has to be
                    // persisted through closeListener() rather than before it. Writing the
                    // property upfront meant that answering Cancel to the unsaved-changes prompt
                    // left the new appearance recorded on disk while GoMule kept running with the
                    // old one: the switch looked like it had silently failed, then applied itself
                    // out of nowhere at the next restart.
                    int check = JOptionPane.showConfirmDialog(
                            null,
                            "GoMule will exit to switch appearance, you'll need to manually start GoMule again. You will be asked what to do with any unsaved changes.",
                            "",
                            OK_CANCEL_OPTION);
                    if (check == 0) {
                        D2FileManager.getInstance()
                                .closeListener(() -> iProperties.setProperty(
                                        LookAndFeelOptions.PROPERTY_NAME, lookAndFeelOption.name()));
                    }
                }
            });
        }
        JMenuItem exitProg = new JMenuItem("Exit");

        iProjMenu = new JMenu("Project");
        JMenuItem projOpt = new JMenuItem("Preferences");
        JMenu aboutMenu = new JMenu("About...");
        iMenuBar.add(fileMenu);
        iMenuBar.add(iProjMenu);
        iMenuBar.add(aboutMenu);

        fileMenu.add(newProject);
        fileMenu.add(openProject);
        fileMenu.add(iMenuItemCloseProject);
        fileMenu.addSeparator();
        fileMenu.add(iMenuItemOpenChar);
        fileMenu.addSeparator();
        fileMenu.add(iMenuItemNewStash);
        fileMenu.add(iMenuItemOpenStash);
        fileMenu.addSeparator();
        fileMenu.add(iMenuItemSaveAll);
        fileMenu.addSeparator();
        fileMenu.add(iMenuItemOpenGrail);
        fileMenu.addSeparator();
        fileMenu.add(switchLookAndFeelMenu);
        fileMenu.addSeparator();
        fileMenu.add(exitProg);

        iProjMenu.add(projOpt);

        this.setJMenuBar(iMenuBar);

        aboutMenu.addMouseListener(new MouseAdapter() {

            public void mousePressed(MouseEvent e) {

                displayAbout();
            }
        });

        projOpt.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {

                D2ProjectSettingsDialog lDialog = new D2ProjectSettingsDialog(D2FileManager.this);
                lDialog.setVisible(true);
            }
        });

        iMenuItemOpenChar.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                openChar(true);
            }
        });

        iMenuItemNewStash.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                newStash(true);
            }
        });

        iMenuItemOpenStash.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                openStash(true);
            }
        });

        iMenuItemSaveAll.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                saveAll();
            }
        });

        iMenuItemOpenGrail.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                openGrailWindow();
            }
        });

        exitProg.addMouseListener(new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                closeListener();
            }
        });

        checkProjects();
    }

    public D2Project getProject() {
        return iProject;
    }

    /**
     * Switches the current project object and pushes it to the two views that hold a direct
     * reference to it (plan section 5, step 1). Accepts null -- both
     * {@code D2ViewClipboard.setProject()} and {@code D2ViewProject.setProject()} already clear
     * themselves to an empty state rather than throwing when handed one (see
     * D2ViewClipboard.clearProject() and D2ViewProject.refreshTree()'s null checks). Callers are
     * responsible for closeWindows()/confirmCloseProject() around this -- see openProject() and
     * doCloseProject() -- this method only ever swaps the reference and refreshes dependent UI.
     */
    public void setProject(D2Project pProject) throws Exception {
        iProject = pProject;
        iClipboard.setProject(iProject);
        iViewProject.setProject(pProject);
        updateProjectDependentUI();
        setTitle(true);
    }

    /**
     * Enables/disables every piece of UI whose meaning depends on a project being open (plan
     * section 5, step 2) -- called once at the end of the constructor (for whatever
     * checkProjects() decided) and again every time setProject() runs. "New Project..."/
     * "Open Project..." are deliberately excluded: they are the only way to ever get out of the
     * empty state, so they must stay clickable in it.
     */
    private void updateProjectDependentUI() {
        boolean lHasProject = iProject != null;

        iMenuItemCloseProject.setEnabled(lHasProject);
        iMenuItemOpenChar.setEnabled(lHasProject);
        iMenuItemNewStash.setEnabled(lHasProject);
        iMenuItemOpenStash.setEnabled(lHasProject);
        iMenuItemSaveAll.setEnabled(lHasProject);
        iMenuItemOpenGrail.setEnabled(lHasProject);
        iProjMenu.setEnabled(lHasProject);

        // iToolbar's buttons (Open/Add Character, New/Open/Add Stash, Open/Add Shared Stash,
        // Save All, Drop Calc, Cancel All, Rearrange Windows, the Holy Grail duplicate button)
        // are all meaningless with no project open, since with no project there cannot be any
        // open window either (setProject(null)'s invariant) -- walking the container rather than
        // naming each button keeps this in sync automatically if one is ever added.
        for (Component lComponent : iToolbar.getComponents()) {
            if (lComponent instanceof AbstractButton) {
                lComponent.setEnabled(lHasProject);
            }
        }

        if (iChangeProject != null) {
            iChangeProject.setEnabled(lHasProject);
        }
        // Hidden, not disabled -- see iProjControlPanel's own comment. revalidate()/repaint() on
        // the containing pane is required: hiding a component does not by itself re-run the
        // layout, so the space it occupied would otherwise stay blank instead of closing up.
        if (iProjControlPanel != null) {
            iProjControlPanel.setVisible(lHasProject);
            if (iLeftPane != null) {
                iLeftPane.revalidate();
                iLeftPane.repaint();
            }
        }
    }

    /**
     * A defensive-copy list of every open file that currently has unsaved edits (plan section 3,
     * step 1) -- what confirmCloseProject()'s dialog below shows.
     * <p>
     * The clipboard is deliberately NOT in this list, even though it is a real .d2x that can be
     * modified. It is the project's own scratch space, not a file the user opened and chose to
     * edit: being asked whether to save "Clipboard" means nothing to them, and answering No would
     * silently drop items they had parked there. confirmCloseProject() just saves it, the same way
     * it saves the project's settings.
     */
    public List<String> getModifiedFileNames() {
        List<String> lResult = new ArrayList<String>();
        Iterator lIterator = iItemLists.keySet().iterator();
        while (lIterator.hasNext()) {
            String lFileName = (String) lIterator.next();
            D2ItemList lList = getItemList(lFileName);
            if (lList != null && lList.isModified()) {
                lResult.add(lFileName);
            }
        }
        return lResult;
    }

    /**
     * The single gate every project-closing action must pass through before it tears anything
     * down (plan section 5, step 3): Close/New/Open Project, the combo box, closeListener()
     * (window X / File / Exit), and the Del Proj / Clear Proj buttons. Nothing in this class
     * saves on window close any more -- closeWindows() is now a pure "close every window" (see
     * its own comment) -- so "No" here is genuinely safe: it simply never calls
     * saveAllItemLists().
     *
     * @return true if the caller may proceed (nothing was modified, or the user chose Yes/No);
     * false if the user chose Cancel or dismissed the dialog, in which case the caller MUST abort
     * its action entirely rather than close/switch anything.
     */
    public boolean confirmCloseProject() {
        // The project's own settings (file list, bank, Flavie preferences) and its clipboard are
        // not "files the user edited" -- losing them would silently degrade the app's own state
        // rather than discard something the user chose to type -- so both are written regardless
        // of the Yes/No/Cancel choice below, the piece of the old unconditional closeWindows()
        // saveAll() that is deliberately kept unconditional. See getModifiedFileNames() for why
        // the clipboard is never offered as a choice.
        if (iProject != null) {
            iProject.saveProject();
            if (iClipboard != null) {
                // Null only if createRightPane() failed to build the clipboard at startup, which
                // it handles by replacing the whole content pane with the error -- not a state
                // worth crashing this save path over.
                iClipboard.saveView();
            }
        }

        List<String> lModified = getModifiedFileNames();
        if (lModified.isEmpty()) {
            return true;
        }

        StringBuilder lMessage = new StringBuilder("The following files have unsaved changes:\n\n");
        int lShown = 0;
        for (String lFile : lModified) {
            if (lShown >= 15) {
                lMessage.append("... and ").append(lModified.size() - lShown).append(" more.\n");
                break;
            }
            lMessage.append(lFile).append("\n");
            lShown++;
        }
        lMessage.append("\nSave changes before closing?");

        int lChoice = JOptionPane.showConfirmDialog(
                iContentPane, lMessage.toString(), "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
        if (lChoice == JOptionPane.YES_OPTION) {
            saveAllItemLists();
            return true;
        }
        if (lChoice == JOptionPane.NO_OPTION) {
            return true;
        }
        // CANCEL_OPTION, or the dialog was dismissed via its own close button (CLOSED_OPTION,
        // -1) -- both mean "abort", never just "treat like No".
        return false;
    }

    /**
     * Switches to pProjectDir as the current project: constructs the D2Project (creating the
     * directory/project.properties if this is genuinely new -- see D2Project's constructor),
     * records it as the most recent project so it survives to the next combo rebuild/restart,
     * and refreshes the combo/tree/clipboard accordingly. Callers are responsible for
     * confirmCloseProject() + closeWindows() on whatever project was open before calling this --
     * see doNewProject()/doOpenProject()/doCloseProject() and the combo box's ItemListener.
     */
    private void openProject(File pProjectDir) {
        try {
            D2Project lProject = new D2Project(this, pProjectDir);
            setProject(lProject);
            D2ProjectRegistry.recordOpened(iProperties, pProjectDir.getAbsoluteFile());
            FileManagerProperties.saveFileManagerProperties(iProperties);
            checkProjectsModel();
            iIgnoreProjectSelection = true;
            try {
                iChangeProject.setSelectedItem(lProject.getProjectDirFile().getAbsoluteFile());
            } finally {
                iIgnoreProjectSelection = false;
            }
        } catch (Exception pEx) {
            D2FileManager.displayErrorDialog(pEx);
        }
    }

    /**
     * File / New Project... (plan section 5, step 4): a small composite dialog (name + location +
     * Browse), rather than a JFileChooser in DIRECTORIES_ONLY mode repurposed as "pick a parent
     * folder and type a name into its filename field" -- the plan calls that notoriously
     * confusing, and this avoids it entirely.
     */
    private void doNewProject() {
        JTextField lNameField = new JTextField("MyProject");
        JTextField lLocationField = new JTextField(D2UserData.getProjectsDir().getAbsolutePath());
        JButton lBrowseButton = new JButton("Browse...");
        lBrowseButton.addActionListener(e -> {
            JFileChooser lChooser = new JFileChooser(lLocationField.getText());
            lChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (lChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                lLocationField.setText(lChooser.getSelectedFile().getAbsolutePath());
            }
        });

        RandallPanel lPanel = new RandallPanel();
        lPanel.addToPanel(new JLabel("Name:"), 0, 0, 1, RandallPanel.NONE);
        lPanel.addToPanel(lNameField, 1, 0, 2, RandallPanel.HORIZONTAL);
        lPanel.addToPanel(new JLabel("Location:"), 0, 1, 1, RandallPanel.NONE);
        lPanel.addToPanel(lLocationField, 1, 1, 1, RandallPanel.HORIZONTAL);
        lPanel.addToPanel(lBrowseButton, 2, 1, 1, RandallPanel.NONE);

        // Looping re-shows the same dialog (with whatever the user already typed still in it) on
        // a validation error, instead of making a single typo throw the whole thing away.
        while (true) {
            int lChoice = JOptionPane.showConfirmDialog(
                    iContentPane, lPanel, "New Project", JOptionPane.OK_CANCEL_OPTION);
            if (lChoice != JOptionPane.OK_OPTION) {
                return; // Cancel: nothing has been closed or created yet at this point.
            }
            String lName = lNameField.getText() == null ? "" : lNameField.getText().trim();
            String lLocation = lLocationField.getText() == null ? "" : lLocationField.getText().trim();
            String lError = validateNewProject(lName, lLocation);
            if (lError != null) {
                JOptionPane.showMessageDialog(iContentPane, lError, "Error!", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            File lNewProjectDir = new File(lLocation, lName);
            if (!confirmCloseProject()) {
                return;
            }
            closeWindows();
            openProject(lNewProjectDir);
            return;
        }
    }

    /**
     * Validation for doNewProject() (plan section 5, step 4): a non-empty name using the same
     * character restriction the old left-pane "New Proj" button used to enforce, an existing
     * (non-project) target folder that is either absent or empty, and a writable parent location.
     *
     * @return null if pName/pLocation are valid, or a user-facing error message otherwise.
     */
    private String validateNewProject(String pName, String pLocation) {
        if (pName.isEmpty()) {
            return "Please enter a project name.";
        }
        if (!PROJECT_NAME_PATTERN.matcher(pName).matches()) {
            return "Project name contains invalid characters (/ ? * : ; { } \\ are not allowed).";
        }
        File lLocationDir = new File(pLocation);
        if (!lLocationDir.isDirectory() || !lLocationDir.canWrite()) {
            return "Location does not exist or is not writable: " + pLocation;
        }
        File lTarget = new File(lLocationDir, pName);
        if (lTarget.exists()) {
            String[] lContents = lTarget.list();
            if (!lTarget.isDirectory() || (lContents != null && lContents.length > 0)) {
                return "That folder already exists and is not empty: " + lTarget.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * File / Open Project... (plan section 5, step 4): a FILES_AND_DIRECTORIES chooser accepting
     * either the project.properties file itself (resolved to its parent folder as a convenience)
     * or the project's folder directly.
     */
    private void doOpenProject() {
        JFileChooser lChooser = new JFileChooser(D2UserData.getProjectsDir());
        lChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        lChooser.setFileFilter(new FileFilter() {
            public boolean accept(File pFile) {
                return pFile.isDirectory() || pFile.getName().equalsIgnoreCase("project.properties");
            }

            public String getDescription() {
                return "GoMule projects (a folder, or its project.properties)";
            }
        });
        if (lChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File lSelected = lChooser.getSelectedFile();
        File lProjectDir = lSelected.isFile() ? lSelected.getParentFile() : lSelected;
        if (lProjectDir == null || !D2Project.isProjectDir(lProjectDir)) {
            JOptionPane.showMessageDialog(
                    iContentPane,
                    "This folder does not contain a project.properties file.",
                    "Not a GoMule project",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (iProject != null && lProjectDir.getAbsoluteFile().equals(iProject.getProjectDirFile().getAbsoluteFile())) {
            return; // already open.
        }
        if (!confirmCloseProject()) {
            return;
        }
        closeWindows();
        openProject(lProjectDir);
    }

    /**
     * File / Close Project (plan sections 2 and 5, step 4): the real "no project open" state --
     * NOT a fallback to the default project. current-project-dir is persisted empty immediately,
     * not just at exit, so even a crash right after Close still reopens empty on the next launch
     * (plan's decisions table: Close is a real, remembered state).
     */
    private void doCloseProject() {
        if (iProject == null) {
            return; // the menu item is disabled in this state anyway; defensive no-op.
        }
        if (!confirmCloseProject()) {
            return;
        }
        closeWindows();
        try {
            setProject((D2Project) null);
        } catch (Exception pEx) {
            displayErrorDialog(pEx);
        }
        iProperties.setProperty("current-project-dir", "");
        FileManagerProperties.saveFileManagerProperties(iProperties);
        checkProjectsModel();
        iIgnoreProjectSelection = true;
        try {
            iChangeProject.setSelectedItem(null);
        } finally {
            iIgnoreProjectSelection = false;
        }
    }

    private void createToolbar() {
        iToolbar = new JToolBar();
        // sets whether the toolbar can be made to float
        iToolbar.setFloatable(false);

        // sets whether the border should be painted
        iToolbar.setBorderPainted(false);
        iToolbar.add(new JLabel("Character"));

        JButton lOpenD2S = new JButton(D2ImageCache.getIcon("open.gif"));
        lOpenD2S.setToolTipText("<html><font color=white>Open Character</font></html>");

        lOpenD2S.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openChar(true);
            }
        });
        iToolbar.add(lOpenD2S);

        JButton lAddD2S = new JButton(D2ImageCache.getIcon("add.gif"));
        lAddD2S.setToolTipText("<html><font color=white>Add Character</font></html>");
        lAddD2S.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openChar(false);
            }
        });
        iToolbar.add(lAddD2S);
        iToolbar.addSeparator();

        iToolbar.add(new JLabel("Stash"));

        JButton lNewD2X = new JButton(D2ImageCache.getIcon("new.gif"));
        lNewD2X.setToolTipText("<html><font color=white>New ATMA Stash</font></html>");
        lNewD2X.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                newStash(true);
            }
        });
        iToolbar.add(lNewD2X);

        JButton lOpenD2X = new JButton(D2ImageCache.getIcon("open.gif"));
        lOpenD2X.setToolTipText("<html><font color=white>Open ATMA Stash</font></html>");
        lOpenD2X.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openStash(true);
            }
        });
        iToolbar.add(lOpenD2X);

        JButton lAddD2X = new JButton(D2ImageCache.getIcon("add.gif"));
        lAddD2X.setToolTipText("<html><font color=white>Add ATMA Stash</font></html>");
        lAddD2X.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openStash(false);
            }
        });
        iToolbar.add(lAddD2X);

        iToolbar.addSeparator();
        iToolbar.add(new JLabel("Shared Stash"));

        JButton openSharedStashButton = new JButton(D2ImageCache.getIcon("open.gif"));
        openSharedStashButton.setToolTipText("<html><font color=white>Open Shared Stash</font></html>");

        openSharedStashButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSharedStash(true);
            }
        });
        iToolbar.add(openSharedStashButton);

        JButton addSharedStashButton = new JButton(D2ImageCache.getIcon("add.gif"));
        addSharedStashButton.setToolTipText("<html><font color=white>Add Shared Stash</font></html>");
        addSharedStashButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSharedStash(false);
            }
        });
        iToolbar.add(addSharedStashButton);

        iToolbar.addSeparator();

        iToolbar.add(new JLabel("     "));

        JButton lSaveAll = new JButton(D2ImageCache.getIcon("save.gif"));
        lSaveAll.setToolTipText("<html><font color=white>Save All</font></html>");
        lSaveAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveAll();
            }
        });
        iToolbar.add(lSaveAll);

        JButton lDropCalc = new JButton(D2ImageCache.getIcon("dc.gif"));
        lDropCalc.setToolTipText("<html><font color=white>Run Drop Calculator</font></html>");
        lDropCalc.addActionListener(e -> {
            JEditorPane dropcalcOptionPaneContents = new JEditorPane(
                    "text/html",
                    "<html>A new in-app dropcalc is under construction, the old one was not reliable. For now you can find one online at <a href=\"https://dropcalc.silospen.com\">dropcalc.silospen.com</a></html>");
            dropcalcOptionPaneContents.setEditable(false);
            dropcalcOptionPaneContents.addHyperlinkListener(hyperlinkEvent -> {
                if (hyperlinkEvent.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    try {
                        Desktop.getDesktop().browse(hyperlinkEvent.getURL().toURI());
                    } catch (IOException | URISyntaxException ex) {
                        ex.printStackTrace();
                    }
                }
            });
            JOptionPane.showMessageDialog(iContentPane, dropcalcOptionPaneContents);
        });
        iToolbar.add(lDropCalc);

        JButton lCancelAll = new JButton(D2ImageCache.getIcon("cancel.gif"));
        lCancelAll.setToolTipText("<html><font color=white>Cancel (reload all)</font></html>");
        lCancelAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelAll();
            }
        });
        iToolbar.add(lCancelAll);
        JButton rearrangeWindows = new JButton(D2ImageCache.getIcon("rearrange.gif"));
        rearrangeWindows.setToolTipText("<html><font color=white>Rearrange Windows</font></html>");
        rearrangeWindows.addActionListener(e -> rearrangeWindows());
        iToolbar.addSeparator();
        iToolbar.add(rearrangeWindows);

        iToolbar.addSeparator();

        // No dedicated icon asset exists for this yet (unlike the other toolbar buttons, which all
        // have one under resources/icons), so this is a plain text button rather than introducing
        // a new binary asset just for this.
        JButton lHolyGrail = new JButton("Holy Grail");
        lHolyGrail.setToolTipText("<html><font color=white>Open the Holy Grail window</font></html>");
        lHolyGrail.addActionListener(e -> openGrailWindow());
        iToolbar.add(lHolyGrail);

        iToolbar.addSeparator();

        iContentPane.add(iToolbar, BorderLayout.NORTH);
    }

    private void rearrangeWindows() {
        Dimension desktopSize = iDesktopPane.getSize();
        Map<String, List<JInternalFrame>> framesByFileSuffix = getFramesByFileSuffix();
        Queue<JInternalFrame> orderedFrames = Streams.concat(
                        framesByFileSuffix.getOrDefault(".d2s", Collections.emptyList()).stream()
                                .sorted(Comparator.comparing(it -> (((D2ItemContainer) it).getFileName()))),
                        framesByFileSuffix.getOrDefault(".d2i", Collections.emptyList()).stream()
                                .sorted(Comparator.comparing(it -> (((D2ItemContainer) it).getFileName()))),
                        framesByFileSuffix.getOrDefault(".d2x", Collections.emptyList()).stream()
                                .sorted(Comparator.comparing(it -> (((D2ItemContainer) it).getFileName()))))
                .collect(Collectors.toCollection(LinkedList::new));

        int x = 0;
        int y = 0;
        JInternalFrame nextFrame;
        while ((nextFrame = orderedFrames.peek()) != null) {
            if ((y + nextFrame.getHeight()) > (desktopSize.height + (nextFrame.getWidth() * 0.05))) {
                x += iDesktopPane.getComponentAt(x, 0).getWidth();
                y = 0;
            }
            if ((x + nextFrame.getWidth()) > (desktopSize.width + (nextFrame.getWidth() * 0.05))) {
                break;
            }
            nextFrame.setLocation(x, y);
            y += nextFrame.getHeight();
            orderedFrames.remove();
        }
        cascadeWindows(orderedFrames);
    }

    private void cascadeWindows(Queue<JInternalFrame> orderedFrames) {
        JInternalFrame nextFrame;
        int x;
        int y;
        x = 20;
        y = 20;
        while ((nextFrame = orderedFrames.poll()) != null) {
            nextFrame.setLocation(x, y);
            x += 20;
            y += 20;
            nextFrame.toFront();
        }
    }

    private Map<String, List<JInternalFrame>> getFramesByFileSuffix() {
        return Arrays.stream(iDesktopPane.getAllFrames())
                .filter(it -> it instanceof D2ItemContainer)
                .collect(Collectors.groupingBy(it -> {
                    String fileName = ((D2ItemContainer) it).getFileName();
                    return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
                }));
    }

    /**
     * Resolves the project GoMule should open at startup (plan section 4): "current-project-dir"
     * (a full path) if present, else the pre-registry "current-project" (a bare name, always
     * resolved under the OLD projects/ directory) for a one-time upgrade, else -- a genuinely
     * first run -- the default project. An empty "current-project-dir" is not an error case to
     * recover from: it is exactly what doCloseProject() persists on an explicit Close, and it
     * means "start with nothing open" (plan's decisions table).
     */
    private void checkProjects() {
        try {
            iProperties = FileManagerProperties.loadFileManagerProperties();
            String lCurrentDirPath = iProperties.getProperty("current-project-dir");

            if (lCurrentDirPath == null) {
                // Back-compat with a pre-registry projects.properties: only the old bare-name key
                // exists. Resolve it under the user-data projects dir (already migrated, by the
                // time GoMule.main() reaches here, from the legacy ./projects) and fall through to
                // persisting the new key below so this branch is only ever taken once.
                String lLegacyName = iProperties.getProperty("current-project");
                File lLegacyDir = (lLegacyName == null || lLegacyName.trim().isEmpty())
                        ? null : new File(D2UserData.getProjectsDir(), lLegacyName);
                lCurrentDirPath = (lLegacyDir != null && D2Project.isProjectDir(lLegacyDir))
                        ? lLegacyDir.getAbsolutePath()
                        // No trace of a previous session at all: open the default project rather
                        // than starting empty, so a brand-new install looks ready to use right
                        // away instead of looking broken.
                        : D2UserData.getDefaultProjectDir().getAbsolutePath();
            }

            if (lCurrentDirPath.trim().isEmpty()) {
                iProject = null;
            } else {
                File lProjectDir = new File(lCurrentDirPath);
                if (!D2Project.isProjectDir(lProjectDir)) {
                    // The remembered project is gone (deleted, renamed, an unmounted drive) --
                    // fall back to the default project rather than refusing to start.
                    lProjectDir = D2UserData.getDefaultProjectDir();
                }
                iProject = new D2Project(this, lProjectDir);
                D2ProjectRegistry.recordOpened(iProperties, iProject.getProjectDirFile().getAbsoluteFile());
            }
            iProperties.setProperty(
                    "current-project-dir",
                    iProject == null ? "" : iProject.getProjectDirFile().getAbsolutePath());
        } catch (Exception pEx) {
            displayErrorDialog(pEx);
            iProject = null;
            iProperties = null;
        }
    }

    /**
     * Called on exit (window X, or File / Exit) to persist window state and shut down. Now goes
     * through confirmCloseProject() FIRST (plan section 5, step 3): a Cancel there must genuinely
     * cancel the exit, so this returns early with no System.exit(0) at all in that case, rather
     * than the old behaviour of silently saveAll()-ing everything on the way out.
     */
    public void closeListener() {
        closeListener(null);
    }

    /**
     * Same as {@link #closeListener()}, plus one action applied only once the close is actually
     * going ahead -- i.e. after confirmCloseProject() has returned true, and before the single
     * FileManagerProperties.saveFileManagerProperties() call below writes iProperties out. Exists
     * for the "Switch Appearance" menu (createMenubar()), whose whole job is to record a setting
     * and restart: a Cancel at the unsaved-changes prompt must leave that setting unwritten.
     * pOnConfirmed is therefore for iProperties changes only -- it must not save them itself.
     */
    public void closeListener(Runnable pOnConfirmed) {
        if (!confirmCloseProject()) {
            return;
        }
        if (pOnConfirmed != null) {
            pOnConfirmed.run();
        }
        iProperties.setProperty(
                "current-project-dir", iProject == null ? "" : iProject.getProjectDirFile().getAbsolutePath());
        Rectangle bounds = getBounds();
        iProperties.setProperty("win-state", String.valueOf(getExtendedState()));
        iProperties.setProperty("win-ldiv-loc", String.valueOf(lSplit.getDividerLocation()));
        iProperties.setProperty("win-rdiv-loc", String.valueOf(rSplit.getDividerLocation()));
        if (getExtendedState() == NORMAL) {
            iProperties.setProperty("win-x", String.valueOf(bounds.x));
            iProperties.setProperty("win-y", String.valueOf(bounds.y));
            iProperties.setProperty("win-height", String.valueOf(bounds.height));
            iProperties.setProperty("win-width", String.valueOf(bounds.width));
        }
        FileManagerProperties.saveFileManagerProperties(iProperties);
        closeWindows();
        System.exit(0);
    }

    public void closeFileName(String pFileName) {
        saveAll();
        for (int i = 0; i < iOpenWindows.size(); i++) {
            D2ItemContainer lItemContainer = (D2ItemContainer) iOpenWindows.get(i);

            if (lItemContainer.getFileName().equalsIgnoreCase(pFileName)) {
                lItemContainer.closeView();
            }
        }
    }

    public boolean projTxtDump(String pFileName, D2ItemList lList, String folder) {
        String lFileName = null;
        if (folder == null) {

            lFileName = pFileName + ".txt";

        } else {

            if (lList.getFilename().endsWith(".d2s")) {
                lFileName = ((D2Character) lList).getCharName() + ".d2s";
            } else if (lList.getFilename().endsWith(".d2i")) {
                lFileName = ((D2SharedStash) lList)
                        .getFilename()
                        .substring(((D2SharedStash) lList).getFilename().lastIndexOf(File.separator) + 1);
            } else {
                lFileName = ((D2Stash) lList).getFileNameEnd();
            }
            lFileName = folder + File.separator + lFileName + ".txt";

            File lFile = new File(folder);
            if (!lFile.exists()) {
                if (!lFile.mkdir()) {
                    return false;
                }
            }
        }
        return writeTxtDump(lFileName, lList);
    }

    public boolean singleTxtDump(String pFileName) {
        D2ItemList lList = null;
        String lFileName = null;

        if (pFileName.equalsIgnoreCase("all")) {
            if (iViewAll != null) {
                lFileName = "." + File.separator + "all.txt";
                lList = iViewAll.getItemLists();
            }

        } else {

            lList = (D2ItemList) iItemLists.get(pFileName);
            lFileName = pFileName + ".txt";
        }

        return writeTxtDump(lFileName, lList);
    }

    private boolean writeTxtDump(String lFileName, D2ItemList lList) {
        if (lList != null && lFileName != null) {
            try {
                File lFile = new File(lFileName);
                System.err.println("File: " + lFile.getCanonicalPath());

                PrintWriter lWriter = new PrintWriter(new FileWriter(lFile.getCanonicalPath()));

                lList.fullDump(lWriter);
                lWriter.flush();
                lWriter.close();
                return true;
            } catch (Exception pEx) {
                pEx.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Pure "close every open item window" (plan section 5, step 3) -- it used to start with an
     * unconditional saveAll(), silently writing every open .d2s/.d2x/.d2i before the caller had
     * any say at all. Every caller now goes through confirmCloseProject() first (which itself
     * still always saves iProject's own settings, just never a game file without asking), so by
     * the time this runs the save/don't-save decision has already been made.
     */
    public void closeWindows() {
        while (iOpenWindows.size() > 0) {
            D2ItemContainer lItemContainer = (D2ItemContainer) iOpenWindows.get(0);
            if (lItemContainer != null) {
                lItemContainer.closeView();
            }
        }
    }

    //	private void handleLoadError(String pFileName, Exception pEx){
    //	// close this view & all view
    //	for ( int i = 0 ; i < iOpenWindows.size() ; i++ ){
    //	D2ItemContainer lItemContainer = (D2ItemContainer) iOpenWindows.get(i);
    //	if (lItemContainer.getFileName().equalsIgnoreCase(pFileName) ||
    // lItemContainer.getFileName().toLowerCase().equals("all")){
    //	lItemContainer.closeView();
    //	}
    //	}
    //	displayErrorDialog( pEx );
    //	}

    /**
     * called on exit or when this window is closed
     * zip through and make sure all the character
     * windows close properly, because character
     * windows save on close
     */
    public void saveAll() {
        try {
            if (iProject != null) {
                iProject.saveProject();
            }
            saveAllItemLists();
        } catch (Exception ex) {
            displayErrorDialog(ex);
        }
    }

    public void saveAllItemLists() {
        checkAll(false);

        iClipboard.saveView();
        if (iProject == null) {
            // No project ⇒ no open item windows (setProject(null)'s invariant, plan section 5,
            // step 1) ⇒ iItemLists should already be empty here -- but this is still the one
            // programmatic entry point the plan calls out by name (step 3), so it gets the same
            // explicit guard rather than relying on that invariant alone.
            return;
        }
        Iterator lIterator = iItemLists.keySet().iterator();
        while (lIterator.hasNext()) {
            String lFileName = (String) lIterator.next();
            D2ItemList lList = getItemList(lFileName);
            if (lList.isModified()) {
                lList.save(iProject);
            }
        }
    }

    public void cancelAll() {
        checkAll(true);
    }

    private void checkAll(boolean pCancel) {
        if (iIgnoreCheckAll) {
            return;
        }
        if (iProject == null) {
            // With no project open, iClipboard.getItemLists() returns null (D2ViewClipboard.
            // clearProject() detaches its D2Stash entirely) and iItemLists is empty anyway (the
            // invariant on setProject(null): no project ⇒ no open windows) -- nothing here to
            // poll for on-disk changes. Without this guard, this NPEs on windowActivated() alone,
            // since that fires regardless of whether a project is open.
            return;
        }
        try {
            iIgnoreCheckAll = true;
            boolean lChanges = pCancel;
            boolean lModifiedChanges = pCancel;
            D2ItemList lClipboardStash = iClipboard.getItemLists();

            if (!lClipboardStash.checkTimestamp()) {
                lChanges = true;
                if (iClipboard.isModified()) {
                    lModifiedChanges = true;
                }
            }

            Iterator lIterator = iItemLists.keySet().iterator();
            while (lIterator.hasNext()) {
                String lFileName = (String) lIterator.next();
                D2ItemList lList = (D2ItemList) iItemLists.get(lFileName);
                if (!(lList instanceof D2ItemListAll) && !lList.checkTimestamp()) {
                    lChanges = true;
                    if (lList.isModified()) {
                        lModifiedChanges = true;
                    }
                }
            }

            if (lChanges) {
                if (pCancel) {
                    displayTextDialog("Info", "Reloading on request");
                } else if (lModifiedChanges) {
                    displayTextDialog("Info", "Changes on file system detected, reloading files.");
                } else {
                    displayTextDialog("Info", "Changes on file system detected, reloading files.");
                }
            }

            if (lChanges) {
                if (!lClipboardStash.checkTimestamp() || (lModifiedChanges && lClipboardStash.isModified())) {
                    try {
                        iClipboard.setProject(iProject);
                    } catch (Exception pEx) {
                        displayErrorDialog(pEx);
                    }
                }

                for (int i = 0; i < iOpenWindows.size(); i++) {
                    D2ItemContainer lContainer = (D2ItemContainer) iOpenWindows.get(i);
                    D2ItemList lList = lContainer.getItemLists();
                    if (iViewAll != null && lList == iViewAll.getStash()) {
                        lContainer.disconnect(null);
                        lContainer.connect();
                    } else {
                        if (!lList.checkTimestamp() || (lModifiedChanges && lList.isModified())) {
                            String lFileName = lList.getFilename();
                            lContainer.disconnect(null);
                            if (iViewAll != null && iViewAll.getItemLists() instanceof D2ItemListAll) {
                                ((D2ItemListAll) iViewAll.getItemLists()).disconnect(lFileName);
                            }

                            lContainer.connect();
                            if (iViewAll != null && iViewAll.getItemLists() instanceof D2ItemListAll) {
                                ((D2ItemListAll) iViewAll.getItemLists()).connect(lFileName);
                            }
                        }
                    }
                }
            }
        } catch (Exception pEx) {
            pEx.printStackTrace();
        } finally {
            iIgnoreCheckAll = false;
            TITLE_SETTING_LIST_LISTENER.itemListChanged();
        }
    }

    private JFileChooser getCharDialog() {
        return iProject.getCharDialog();
    }

    private JFileChooser getStashDialog() {
        return iProject.getStashDialog();
    }

    private JFileChooser getSharedStashDialog() {
        return iProject.getSharedStashDialog();
    }

    // file->open callback
    // throw up a dialog for picking d2s files
    // then open that character and add it to the
    // vector of character windows
    public void openChar(boolean load) {
        JFileChooser lCharChooser = getCharDialog();
        lCharChooser.setMultiSelectionEnabled(true);
        if (lCharChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            //			String[] fNamesOut = new String[lCharChooser.getSelectedFiles().length];
            for (int x = 0; x < lCharChooser.getSelectedFiles().length; x = x + 1) {
                java.io.File lFile = lCharChooser.getSelectedFiles()[x];
                if (!canRead(lFile)) {
                    D2FileManager.displayErrorDialog(new Exception(
                            "Access denied to file, please move it to a location that GoMule can access"));
                    return;
                }
                try {
                    String lFilename = lFile.getAbsolutePath();
                    openChar(lFilename, load);
                } catch (Exception pEx) {
                    D2FileManager.displayErrorDialog(pEx);
                }
            }
        }
    }

    public void openChar(String pCharName, boolean load) {
        if (iProject == null) {
            // Defensive: this is one of the programmatic entry points the plan calls out by name
            // (section 5, step 3) that don't go through a menu/toolbar item already gated by
            // updateProjectDependentUI() -- e.g. D2ViewProject.CharTreeNode.view().
            return;
        }
        D2ItemContainer lExisting = null;
        for (int i = 0; i < iOpenWindows.size(); i++) {
            D2ItemContainer lItemContainer = (D2ItemContainer) iOpenWindows.get(i);
            if (lItemContainer.getFileName().equals(pCharName)) {
                lExisting = lItemContainer;
            }
        }
        if (load) {
            if (lExisting != null) {
                internalWindowForward(((JInternalFrame) lExisting));

            } else {
                D2ViewChar lCharView = new D2ViewChar(D2FileManager.this, pCharName);
                lCharView.setLocation(10 + (iOpenWindows.size() * 10), 10 + (iOpenWindows.size() * 10));
                addToOpenWindows(lCharView);
                internalWindowForward(lCharView);
            }
        }
        iProject.addChar(pCharName);
    }

    private void internalWindowForward(JInternalFrame frame) {

        frame.toFront();
        try {
            frame.setSelected(true);
        } catch (PropertyVetoException e) {
            // Shouldn't worry too much if this happens I guess?
            e.printStackTrace();
        }
    }

    public D2ViewProject getViewProject() {
        return iViewProject;
    }

    private void addToOpenWindows(D2ItemContainer pContainer) {
        iOpenWindows.add(pContainer);
        iDesktopPane.add((JInternalFrame) pContainer);
        ((JInternalFrame) pContainer).setOpaque(true);
        ((JInternalFrame) pContainer).addInternalFrameListener(new InternalFrameListener() {

            public void internalFrameActivated(InternalFrameEvent arg0) {
                if (((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                        .getFileName()
                        .endsWith(".d2x")) {
                    pickFrom.setEnabled(false);
                    pickChooser.setEnabled(false);
                    dropTo.setEnabled(false);
                    dropChooser.setEnabled(false);
                    dropAll.setEnabled(true);
                    flavieSingle.setEnabled(true);
                    dumpBut.setEnabled(true);
                } else if (((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                        .getFileName()
                        .endsWith(".d2s")) {
                    pickFrom.setEnabled(true);
                    pickChooser.setEnabled(true);
                    dropTo.setEnabled(true);
                    dropChooser.setEnabled(true);
                    dropAll.setEnabled(true);
                    flavieSingle.setEnabled(true);
                    dumpBut.setEnabled(true);
                } else if (((D2ItemContainer) iOpenWindows.get(iOpenWindows.indexOf(iDesktopPane.getSelectedFrame())))
                        .getFileName()
                        .endsWith(".d2i")) {
                    pickFrom.setEnabled(false);
                    pickChooser.setEnabled(false);
                    dropTo.setEnabled(false);
                    dropChooser.setEnabled(false);
                    pickAll.setEnabled(true);
                    dropAll.setEnabled(true);
                    flavieSingle.setEnabled(true);
                    dumpBut.setEnabled(true);
                } else {
                    pickFrom.setEnabled(false);
                    pickChooser.setEnabled(false);
                    dropTo.setEnabled(false);
                    dropChooser.setEnabled(false);
                    dropAll.setEnabled(false);
                    flavieSingle.setEnabled(false);
                    dumpBut.setEnabled(false);
                }
            }

            public void internalFrameClosed(InternalFrameEvent arg0) {}

            public void internalFrameClosing(InternalFrameEvent arg0) {}

            public void internalFrameDeactivated(InternalFrameEvent arg0) {}

            public void internalFrameDeiconified(InternalFrameEvent arg0) {}

            public void internalFrameIconified(InternalFrameEvent arg0) {}

            public void internalFrameOpened(InternalFrameEvent arg0) {}
        });
        iViewProject.notifyFileOpened(pContainer.getFileName());

        if (pContainer.getFileName().equalsIgnoreCase("all")) {
            iViewAll = (D2ViewStash) pContainer;
        }
        if (pContainer instanceof D2ViewGrail) {
            iGrailView = (D2ViewGrail) pContainer;
        }
    }

    public void removeFromOpenWindows(D2ItemContainer pContainer) {
        iOpenWindows.remove(pContainer);
        iDesktopPane.remove((JInternalFrame) pContainer);
        iViewProject.notifyFileClosed(pContainer.getFileName());
        repaint();

        if (pContainer.getFileName().equalsIgnoreCase("all")) {
            iViewAll = null;
        }
        if (pContainer instanceof D2ViewGrail) {
            iGrailView = null;
        }

        //		System.gc();
    }

    public void openSharedStash(boolean load) {
        JFileChooser lSharedStashChooser = getSharedStashDialog();
        lSharedStashChooser.setMultiSelectionEnabled(true);
        if (lSharedStashChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            handleSharedStash(lSharedStashChooser, load);
        }
    }

    public void openStash(boolean load) {
        JFileChooser lStashChooser = getStashDialog();
        lStashChooser.setMultiSelectionEnabled(true);
        if (lStashChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            handleStash(lStashChooser, load);
        }
    }

    public void newStash(boolean load) {
        JFileChooser lStashChooser = getStashDialog();
        lStashChooser.setMultiSelectionEnabled(true);
        if (lStashChooser.showDialog(this, "New Stash") == JFileChooser.APPROVE_OPTION) {
            String[] lFileName = handleStash(lStashChooser, load);
            for (int x = 0; x < lFileName.length; x = x + 1) {
                if (lFileName[x] != null) {
                    D2ItemList lList = (D2ItemList) iItemLists.get(lFileName[x]);

                    lList.save(iProject);
                }
            }
        }
    }

    private String[] handleStash(JFileChooser pStashChooser, boolean load) {

        String[] fNamesOut = new String[pStashChooser.getSelectedFiles().length];
        File[] stashList = pStashChooser.getSelectedFiles();
        if (pStashChooser.getSelectedFiles().length == 0 && pStashChooser.getSelectedFile() != null) {
            stashList = new File[] {pStashChooser.getSelectedFile()};
            fNamesOut = new String[1];
        }

        for (int x = 0; x < stashList.length; x = x + 1) {
            System.out.println(stashList.length);
            java.io.File lFile = stashList[x];
            // A new stash (typed in but not yet on disk) is NOT created here -- D2Stash's
            // constructor (via D2BitReader.load_file()) only builds an empty in-memory
            // representation for a file that doesn't exist yet; it never touches the filesystem
            // for writing. The actual write only happens later, at save time, and D2BitReader's
            // own save() already throws a clear, real I/O error there if the directory genuinely
            // isn't writable -- so unlike the read check below, there's nothing useful to
            // pre-check for a not-yet-existing file. This used to call canWrite() on the parent
            // directory upfront, with the same Files.isWritable() unreliability problem as the
            // read-side fix above (confirmed real: it blocked creating a brand-new stash in a
            // directory that was, in fact, writable).
            if (lFile.exists() && !canRead(lFile)) {
                D2FileManager.displayErrorDialog(
                        new Exception("Access denied to file, please move it to a location that GoMule can access"));
                return new String[0];
            }
            try {
                String lFilename = lFile.getAbsolutePath();
                if (!lFilename.endsWith(".d2x")) {
                    // force stash name to end with .d2x
                    lFilename += ".d2x";
                }

                openStash(lFilename, load);
                fNamesOut[x] = lFilename;
            } catch (Exception pEx) {
                D2FileManager.displayErrorDialog(pEx);
                fNamesOut[x] = null;
            }
        }
        return fNamesOut;
    }

    // Used when OPENING a file (to view it), not when saving one -- opening only needs read
    // access. This used to also require Files.isWritable(), but that check is unreliable over
    // network/UNC paths on Windows: it performs a native ACL lookup that frequently comes back
    // false for files that are actually both readable and writable, blocking users from even
    // opening (let alone editing) a character stored on a network drive. A save that genuinely
    // can't write back will still fail with its own, more specific I/O error at save time.
    private boolean canRead(File lFile) {
        return Files.isReadable(lFile.toPath());
    }

    public void openStash(String pStashName, boolean load) {
        if (iProject == null) {
            // See openChar(String, boolean)'s comment.
            return;
        }
        D2ItemContainer lExisting = null;
        for (int i = 0; i < iOpenWindows.size(); i++) {
            D2ItemContainer lItemContainer = (D2ItemContainer) iOpenWindows.get(i);
            if (lItemContainer.getFileName().equals(pStashName)) {
                lExisting = lItemContainer;
            }
        }

        D2ViewStash lStashView = null;
        if (load) {
            if (lExisting != null) {
                lStashView = ((D2ViewStash) lExisting);
            } else {
                lStashView = new D2ViewStash(D2FileManager.this, pStashName);
                lStashView.setLocation(10 + (iOpenWindows.size() * 10), 10 + (iOpenWindows.size() * 10));
                addToOpenWindows(lStashView);
            }
            lStashView.activateView();
            internalWindowForward(lStashView);
        }

        iProject.addStash(pStashName);
    }

    private String[] handleSharedStash(JFileChooser pSharedStashChooser, boolean load) {

        String[] fNamesOut = new String[pSharedStashChooser.getSelectedFiles().length];
        File[] stashList = pSharedStashChooser.getSelectedFiles();
        if (pSharedStashChooser.getSelectedFiles().length == 0 && pSharedStashChooser.getSelectedFile() != null) {
            stashList = new File[] {pSharedStashChooser.getSelectedFile()};
            fNamesOut = new String[1];
        }

        for (int x = 0; x < stashList.length; x = x + 1) {
            System.out.println(stashList.length);
            java.io.File lFile = stashList[x];
            if (!canRead(lFile)) {
                D2FileManager.displayErrorDialog(
                        new Exception("Access denied to file, please move it to a location that GoMule can access"));
                return new String[0];
            }
            try {
                String lFilename = lFile.getAbsolutePath();
                openSharedStash(lFilename, load);
                fNamesOut[x] = lFilename;
            } catch (Exception pEx) {
                D2FileManager.displayErrorDialog(pEx);
                fNamesOut[x] = null;
            }
        }
        return fNamesOut;
    }

    public void openSharedStash(String pSharedStashName, boolean load) {
        if (iProject == null) {
            // See openChar(String, boolean)'s comment.
            return;
        }
        D2ItemContainer lExisting = null;
        for (int i = 0; i < iOpenWindows.size(); i++) {
            D2ItemContainer lItemContainer = (D2ItemContainer) iOpenWindows.get(i);
            if (lItemContainer.getFileName().equals(pSharedStashName)) {
                lExisting = lItemContainer;
            }
        }

        D2ViewSharedStash lStashView;
        if (load) {
            if (lExisting != null) {
                lStashView = ((D2ViewSharedStash) lExisting);
            } else {
                lStashView = new D2ViewSharedStash(D2FileManager.this, pSharedStashName);
                lStashView.setLocation(10 + (iOpenWindows.size() * 10), 10 + (iOpenWindows.size() * 10));
                addToOpenWindows(lStashView);
            }
            lStashView.activateView();
            internalWindowForward(lStashView);
        }

        iProject.addSharedStash(pSharedStashName);
    }

    public void displayAbout() {
        JOptionPane.showMessageDialog(
                this,
                "A java-based Diablo II muling application\n\noriniginally created by Andy Theuninck (Gohanman)\nVersion 0.1a"
                        + "\n\ncurrent release by Randall & Silospen\nVersion " + CURRENT_VERSION
                        + "\n\nAnd special thanks to:"
                        + "\n\tHakai_no_Tenshi & Gohanman for helping me out with the file formats"
                        + "\nRTB for all his help.\n\tThe Super Beta Testers:\nSkinhead On The MBTA\nnubikon\nOscuro\nThyiad\nMoiselvus\nPurpleLocust\nAnd anyone else I've forgotten..!",
                "About",
                JOptionPane.PLAIN_MESSAGE);
    }

    public D2ItemList addItemList(String pFileName, D2ItemListListener pListener) throws Exception {
        if (iProject == null) {
            // See openChar(String, boolean)'s comment -- this is the other named entry point
            // (plan section 5, step 3), reachable e.g. from a stale D2ItemContainer if one were
            // ever kept alive past a Close, which the invariant says should never happen, but a
            // clear exception here beats an NPE three lines below on getProject().getType().
            throw new Exception("No project is open.");
        }
        D2ItemList lList;

        if (iItemLists.containsKey(pFileName)) {
            lList = getItemList(pFileName);
        } else if (pFileName.equalsIgnoreCase("all")) {
            lList = new D2ItemListAll(this, iProject);
            //			iViewProject.notifyItemListOpened("all");
        } else if (pFileName.toLowerCase().endsWith(".d2s")) {
            lList = new D2Character(pFileName);

            int lType = getProject().getType();
            if (lType == D2Project.TYPE_SC && (!lList.isSC() || lList.isHC())) {
                throw new Exception("Character is not Softcore (SC), this is a project requirement");
            }
            if (lType == D2Project.TYPE_HC && (lList.isSC() || !lList.isHC())) {
                throw new Exception("Character is not Hardcore (HC), this is a project requirement");
            }

            System.err.println("Add Char: " + pFileName);
            iItemLists.put(pFileName, lList);
            iViewProject.notifyItemListRead(pFileName);
        } else if (pFileName.toLowerCase().endsWith(".d2x")) {
            lList = new D2Stash(pFileName);

            int lType = getProject().getType();
            if (lType == D2Project.TYPE_SC && (!lList.isSC() || lList.isHC())) {
                throw new Exception("Stash is not Softcore (SC), this is a project requirement");
            }
            if (lType == D2Project.TYPE_HC && (lList.isSC() || !lList.isHC())) {
                throw new Exception("Stash is not Hardcore (HC), this is a project requirement");
            }
            System.err.println("Add Stash: " + pFileName);
            iItemLists.put(pFileName, lList);
            iViewProject.notifyItemListRead(pFileName);
        } else if (pFileName.toLowerCase().endsWith(".d2i")) {
            lList = sharedStashReader.readStash(pFileName);

            int lType = getProject().getType();
            if (lType == D2Project.TYPE_SC && (!lList.isSC() || lList.isHC())) {
                throw new Exception("Shared Stash is not Softcore (SC), this is a project requirement");
            }
            if (lType == D2Project.TYPE_HC && (lList.isSC() || !lList.isHC())) {
                throw new Exception("Shared Stash is not Hardcore (HC), this is a project requirement");
            }
            System.err.println("Add Stash: " + pFileName);
            iItemLists.put(pFileName, lList);
            iViewProject.notifyItemListRead(pFileName);
        } else {
            throw new Exception("Incorrect filename: " + pFileName);
        }

        if (pListener != null) {
            lList.addD2ItemListListener(pListener);
            lList.addD2ItemListListener(TITLE_SETTING_LIST_LISTENER);
        }

        // A new file may just have become open (or an "all" pseudo-list rebuilt) -- a live Holy
        // Grail window needs to know so it can subscribe to it and rescan. Harmless to call this
        // even when pFileName was already open (the view only actually (re)subscribes to lists it
        // isn't already listening to -- see D2ViewGrail.refreshLists()).
        if (iGrailView != null) {
            iGrailView.refreshLists();
        }

        return lList;
    }

    private static D2ItemListListener TITLE_SETTING_LIST_LISTENER = new D2ItemListListener() {
        @SuppressWarnings("unchecked")
        @Override
        public void itemListChanged() {
            boolean noModifiedWindows = D2FileManager.getInstance().iOpenWindows.stream()
                    .noneMatch(it -> ((D2ItemContainer) it).isModified());
            D2FileManager.getInstance().setTitle(noModifiedWindows);
        }
    };

    public D2ItemList getItemList(String pFileName) {
        return (D2ItemList) iItemLists.get(pFileName);
    }

    public void removeItemList(String pFileName, D2ItemListListener pListener) {
        D2ItemList lList = getItemList(pFileName);
        if (lList == null) {
            return;
        }
        if (pListener != null) {
            lList.removeD2ItemListListener(TITLE_SETTING_LIST_LISTENER);
            lList.removeD2ItemListListener(pListener);
        }
        if (!lList.hasD2ItemListListener()) {
            System.err.println("Remove file: " + pFileName);
            iItemLists.remove(pFileName);
            iViewProject.notifyItemListClosed(pFileName);
            // The set of open files just shrank -- a live Holy Grail window needs to drop its
            // subscription to this list (and rescan) or it would keep reporting items from a file
            // that is no longer open. See D2ViewGrail.refreshLists().
            if (iGrailView != null) {
                iGrailView.refreshLists();
            }
        }
    }

    /**
     * A defensive-copy snapshot of every currently open .d2s/.d2x/.d2i list, keyed by nothing (the
     * caller only ever needs the values) -- what D2GrailScanner scans. Deliberately NOT
     * D2ItemListAll: that class only aggregates getCharList()/getStashList() (D2ItemListAll.java),
     * so .d2i shared stashes are invisible to it, and the Holy Grail window explicitly must not
     * miss those (plan section 6.1).
     */
    @SuppressWarnings("unchecked")
    public java.util.Collection<D2ItemList> getOpenItemLists() {
        return new ArrayList<D2ItemList>(iItemLists.values());
    }

    /**
     * The Holy Grail window's "Include non-Chronicle items" checkbox state, persisted the same way
     * the look-and-feel choice is (FileManagerProperties / projects/projects.properties) so it
     * survives closing and reopening the window. D2FileManager is the sole owner of iProperties,
     * so the view asks here rather than touching FileManagerProperties directly.
     */
    public boolean isGrailIncludeNonChronicle() {
        return iProperties != null
                && Boolean.parseBoolean(iProperties.getProperty("grail-include-non-chronicle", "false"));
    }

    public void setGrailIncludeNonChronicle(boolean pValue) {
        if (iProperties == null) {
            return;
        }
        iProperties.setProperty("grail-include-non-chronicle", String.valueOf(pValue));
        FileManagerProperties.saveFileManagerProperties(iProperties);
    }

    /**
     * Opens the (singleton) Holy Grail window, or brings the existing one to front if it is
     * already open -- mirrors openChar()/openStash()'s "already open" dedup, keyed here on
     * D2ViewGrail's fixed getFileName() instead of a real file name.
     */
    /**
     * Brings the open window for pFileName to the front, if one is currently open -- used by
     * D2ViewGrail's double-click-to-focus-source-file (plan section 3.3/7). A no-op if that file
     * isn't open any more (it may have been closed since the grail entry was found); the caller
     * doesn't need to check first.
     */
    public void focusFileWindow(String pFileName) {
        for (int i = 0; i < iOpenWindows.size(); i++) {
            D2ItemContainer lContainer = (D2ItemContainer) iOpenWindows.get(i);
            if (lContainer.getFileName().equalsIgnoreCase(pFileName)) {
                internalWindowForward((JInternalFrame) lContainer);
                return;
            }
        }
    }

    public void openGrailWindow() {
        if (iProject == null) {
            // See openChar(String, boolean)'s comment. The toolbar's own Holy Grail button is
            // already disabled by updateProjectDependentUI() in this state; this only guards a
            // direct call.
            return;
        }
        if (iGrailView != null) {
            internalWindowForward(iGrailView);
            return;
        }
        D2ViewGrail lView = new D2ViewGrail(this);
        lView.setLocation(10 + (iOpenWindows.size() * 10), 10 + (iOpenWindows.size() * 10));
        addToOpenWindows(lView);
        internalWindowForward(lView);
    }

    public void workCursor() {
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
    }

    public void defaultCursor() {
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    class D2MenuListener implements ActionListener {

        public void actionPerformed(ActionEvent arg0) {

            new RandallPanel();
        }
    }
}
