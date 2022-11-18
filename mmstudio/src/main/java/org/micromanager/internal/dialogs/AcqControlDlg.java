///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.dialogs;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Objects;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import net.miginfocom.swing.MigLayout;
import org.micromanager.UserProfile;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.events.ChannelExposureEvent;
import org.micromanager.events.ChannelGroupChangedEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.ChannelColorEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.ColorEditor;
import org.micromanager.internal.utils.ColorRenderer;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TooltipTextMaker;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Time-lapse, channel and z-stack acquisition setup dialog.
 * This dialog specifies all parameters for the MDA acquisition.
 *
 * <p>TODO: GUI settings and acquisition engine settings are continuously synchronized.
 * This causes a lot of overhead and a lot of room for bugs.  Separate these better,
 * and only synchronize when really needed. </p>
 */
public final class AcqControlDlg extends JFrame implements PropertyChangeListener,
      AcqSettingsListener, TableModelListener {

   private static final long serialVersionUID = 1L;
   private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 10);
   private static final String RELATIVE_Z = "Relative Z";
   private static final String ABSOLUTE_Z = "Absolute Z";
   private static final String SHOULD_SYNC_EXPOSURE =
         "should sync exposure times between main window and Acquire dialog";
   private static final String SHOULD_HIDE_DISPLAY =
         "should hide image display windows for multi-dimensional acquisitions";
   private static final String SHOULD_CHECK_EXPOSURE_SANITY =
         "whether to prompt the user if their exposure times seem excessively long";
   private static final String BUTTON_SIZE = "width 80!, height 22!";
   private static final String PANEL_CONSTRAINT = "fillx, gap 2, insets 2";

   protected JButton listButton_;
   private JSpinner afSkipInterval_;
   private JComboBox<AcqOrderMode> acqOrderBox_;
   private JTextArea acquisitionOrderText_;
   private JComboBox<String> channelGroupCombo_;
   private final JTextArea commentTextArea_;
   private JComboBox<String> zValCombo_;
   private JTextField nameField_;
   private JTextField rootField_;
   private JTextArea summaryTextArea_;
   private JComboBox<String> timeUnitCombo_;
   private JFormattedTextField interval_;
   private JFormattedTextField zStep_;
   private JFormattedTextField zTop_;
   private JFormattedTextField zBottom_;
   private final JScrollPane channelTablePane_;
   private final JTable channelTable_;
   private ChannelCellEditor channelCellEditor_;
   private JSpinner numFrames_;
   private ChannelTableModel model_;
   private int zRelativeAbsolute_ = 0;
   private JButton setBottomButton_;
   private JButton setTopButton_;
   private final MMStudio mmStudio_;
   private final MutablePropertyMapView settings_;
   private final NumberFormat numberFormat_;
   private JRadioButton singleButton_;
   private JRadioButton multiButton_;
   private JRadioButton ndtiffButton_;
   private JCheckBox stackKeepShutterOpenCheckBox_;
   private JCheckBox chanKeepShutterOpenCheckBox_;
   private AcqOrderMode[] acqOrderModes_;
   private CustomTimesDialog customTimesWindow;
   private final UserProfile profile_;
   // persistent properties (app settings), most are only for backward compatibility
   private static final String MDA_SEQUENCE_SETTINGS = "MDA_SEQUENCE_SETTINGS";
   /*
   private static final String ACQ_INTERVAL = "acqInterval";
   private static final String ACQ_ZBOTTOM = "acqZbottom";
   private static final String ACQ_ZTOP = "acqZtop";
   private static final String ACQ_ZSTEP = "acqZstep";
   private static final String ACQ_ENABLE_SLICE_SETTINGS = "enableSliceSettings";
   private static final String ACQ_ENABLE_MULTI_POSITION = "enableMultiPosition";
   private static final String ACQ_ENABLE_MULTI_FRAME = "enableMultiFrame";
   private static final String ACQ_ENABLE_MULTI_CHANNEL = "enableMultiChannels";
   private static final String ACQ_ORDER_MODE = "acqOrderMode";
   private static final String ACQ_NUMFRAMES = "acqNumframes";
   private static final String ACQ_CHANNEL_GROUP = "acqChannelGroup";
   private static final String ACQ_CHANNELS_KEEP_SHUTTER_OPEN = "acqChannelsKeepShutterOpen";
   private static final String ACQ_STACK_KEEP_SHUTTER_OPEN = "acqStackKeepShutterOpen";
   private static final String ACQ_Z_VALUES = "acqZValues";
   private static final String ACQ_DIR_NAME = "acqDirName";
   private static final String ACQ_ROOT_NAME = "acqRootName";
   private static final String ACQ_SAVE_FILES = "acqSaveFiles";
   private static final String ACQ_AF_ENABLE = "autofocus_enabled";
   private static final String ACQ_AF_SKIP_INTERVAL = "autofocusSkipInterval";
   private static final String CUSTOM_INTERVAL_PREFIX = "customInterval";
   private static final String ACQ_ENABLE_CUSTOM_INTERVALS = "enableCustomIntervals";
    */
   private static final String ACQ_COLUMN_WIDTH = "column_width";
   private static final String ACQ_COLUMN_ORDER = "column_order";
   private static final int ACQ_DEFAULT_COLUMN_WIDTH = 77;

   private final int[] columnWidth_;
   private final int[] columnOrder_;
   private CheckBoxPanel framesPanel_;
   private JPanel defaultTimesPanel_;
   private JPanel customTimesPanel_;
   private final CheckBoxPanel channelsPanel_;
   private CheckBoxPanel slicesPanel_;
   protected CheckBoxPanel positionsPanel_;
   private JPanel acquisitionOrderPanel_;
   private CheckBoxPanel afPanel_;
   private CheckBoxPanel savePanel_;
   private boolean disableGUItoSettings_ = false;
   private final FocusListener focusListener_;

   /**
    * Acquisition control dialog box.
    * Specification of all parameters required for the acquisition.
    *
    * @param mmStudio - ScriptInterface
    */
   public AcqControlDlg(MMStudio mmStudio) {
      super("acquisition configuration dialog");

      mmStudio_ = mmStudio;
      profile_ = mmStudio_.getUserProfile();
      settings_ = profile_.getSettings(this.getClass());

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            MMStudio.class.getResource(
                  "/org/micromanager/icons/microscope.gif")));

      super.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      numberFormat_ = NumberFormat.getNumberInstance();

      super.addWindowListener(new WindowAdapter() {

         @Override
         public void windowClosing(final WindowEvent e) {
            close();
         }
      });
      mmStudio_.addMDAWindowSettingsToAcqEngines(this);

      super.setTitle("Multi-Dimensional Acquisition");
      super.setLayout(new MigLayout("fill, flowy, gap 2, insets 6",
            "[grow, fill]",
            "[][grow][][]"));

      // Contains timepoints, multiple positions, and Z-slices; acquisition
      // order, autofocus, and summary; control buttons, in three columns.

      JPanel topLeftPanel = new JPanel(new MigLayout(
            "fill, flowy, insets 0",
            "[grow, fill]",
            "[]push[]push[]"));
      topLeftPanel.add(createTimePoints());
      topLeftPanel.add(createMultiPositions());
      topLeftPanel.add(createZStacks());

      JPanel topMiddlePanel = new JPanel(new MigLayout(
            "fill, flowy, insets 0",
            "[grow, fill]",
            "[]push[]push[]"));
      topMiddlePanel.add(createAcquisitionOrder());
      topMiddlePanel.add(createAutoFocus());
      topMiddlePanel.add(createSummary());

      JPanel topRightPanel = new JPanel(new MigLayout(
            "flowy, insets 0",
            "[]",
            "10[]10[]10[]push"));
      topRightPanel.add(createCloseButton(), BUTTON_SIZE);
      topRightPanel.add(createRunButtons());
      topRightPanel.add(createSaveButtons());

      JPanel topPanel = new JPanel(new MigLayout(
            "fill, insets 0",
            "[grow, fill]6[grow, fill]6[]",
            "[grow, fill]"));
      topPanel.add(topLeftPanel);
      topPanel.add(topMiddlePanel);
      topPanel.add(topRightPanel);

      // add update event listeners
      positionsPanel_.addActionListener((ActionEvent arg0) -> applySettingsFromGUI());
      acqOrderBox_.addActionListener((ActionEvent e) -> {
         updateAcquisitionOrderText();
         applySettingsFromGUI();
      });

      // load acquisition settings
      SequenceSettings sequenceSettings = loadAcqSettingsFromProfile();

      // protect from bugs caused by zero z step
      if (sequenceSettings.sliceZStepUm() < 0.000000001) {
         sequenceSettings = sequenceSettings.copyBuilder().sliceZStepUm(0.1).build();
      }

      // Restore Column Width and Column order
      int columnCount = 7;
      columnWidth_ = new int[columnCount];
      columnOrder_ = new int[columnCount];
      for (int k = 0; k < columnCount; k++) {
         columnWidth_[k] = settings_.getInteger(ACQ_COLUMN_WIDTH + k,
               ACQ_DEFAULT_COLUMN_WIDTH);
         columnOrder_[k] = settings_.getInteger(ACQ_COLUMN_ORDER + k, k);
      }

      // create the table of channels
      channelTablePane_ = new JScrollPane();
      channelTablePane_.setFont(DEFAULT_FONT);
      channelTable_ = createChannelTable();
      channelTablePane_.setViewportView(channelTable_);
      channelsPanel_ = createChannelsPanel();

      focusListener_ = new FocusListener() {
         @Override
         public void focusGained(FocusEvent e) {
            AbstractCellEditor ae = (AbstractCellEditor) channelTable_.getCellEditor();
            if (ae != null) {
               ae.stopCellEditing();
            }
         }

         @Override
         public void focusLost(FocusEvent e) {
            applySettingsFromGUI();
         }
      };

      super.add(topPanel, "grow");
      super.add(channelsPanel_, "grow");
      super.add(createSavePanel(), "growx");
      commentTextArea_ = new JTextArea();
      super.add(createCommentsPanel(commentTextArea_, focusListener_), "growx");

      framesPanel_.setToolTipText("Acquire images over a repeating time interval");
      positionsPanel_.setToolTipText("Acquire images from a series of positions in the XY plane");
      slicesPanel_.setToolTipText("Acquire images from a series of Z positions");
      acquisitionOrderPanel_.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
            "Determine the precedence of different acquisition axes (time, slice, channel, "
                  + "and stage position). The rightmost axis will be cycled through most quickly, "
                  + "so e.g. \"Time, Channel\" means \"Collect all channels for each timepoint "
                  + "before going to the next timepoint\"."));
      afPanel_.setToolTipText("Toggle autofocus on/off");
      channelsPanel_.setToolTipText("Lets you acquire images in multiple channels (groups of "
            + "properties with multiple preset values");
      savePanel_.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
            "Save images continuously to disk as the acquisition proceeds. If not enabled, "
                  + "then images will be stored in RAM and may be saved later."));

      super.pack();
      Dimension size = super.getPreferredSize();
      size.height += 10; // Compensate for inaccurate size given by Apple Java 6
      super.setMinimumSize(size);
      super.setBounds(100, 100, size.width, size.height);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), "MDA");

      // listener will call updateGUIContent()
      getAcquisitionEngine().setSequenceSettings(sequenceSettings);

      mmStudio_.events().registerForEvents(this);

      // when focus is lots, ensure that channel cell editors close
      WindowFocusListener windowFocusListener = new WindowFocusListener() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
         }

         @Override
         public void windowLostFocus(WindowEvent e) {
            // may need to run the full applySettingsFromGUI()
            AbstractCellEditor ae = (AbstractCellEditor) channelTable_.getCellEditor();
            if (ae != null) {
               ae.stopCellEditing();
            }
         }
      };
      mmStudio_.events().registerForEvents(this);
      super.addWindowFocusListener(windowFocusListener);
   }

   private AcquisitionEngine getAcquisitionEngine() {
      return mmStudio_.getAcquisitionEngine();
   }

   /**
    * Creates the table displaying channels.
    *
    * @return the table with channels.
    */
   public final JTable createChannelTable() {
      model_ = new ChannelTableModel(mmStudio_, getAcquisitionEngine());
      model_.addTableModelListener(this);

      JTable channelTable = new DaytimeNighttime.Table() {
         @Override
         protected JTableHeader createDefaultTableHeader() {
            return new JTableHeader(columnModel) {
               @Override
               public String getToolTipText(MouseEvent e) {
                  java.awt.Point p = e.getPoint();
                  int index = columnModel.getColumnIndexAtX(p.x);
                  int realIndex = columnModel.getColumn(index).getModelIndex();
                  return model_.getToolTipText(realIndex);
               }
            };
         }
      };

      channelTable.setFont(new Font("Dialog", Font.PLAIN, 10));
      channelTable.setAutoCreateColumnsFromModel(false);
      channelTable.setModel(model_);

      channelCellEditor_ = new ChannelCellEditor();
      ChannelCellRenderer cellRenderer = new ChannelCellRenderer(getAcquisitionEngine());
      channelTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

      for (int k = 0; k < model_.getColumnCount(); k++) {
         int colIndex = search(columnOrder_, k);
         if (colIndex < 0) {
            colIndex = k;
         }
         TableColumn column;
         if (colIndex == model_.getColumnCount() - 1) {
            // The color selection column has different properties.
            ColorRenderer cr = new ColorRenderer(true);
            ColorEditor ce = new ColorEditor(model_, model_.getColumnCount() - 1);
            column = new TableColumn(model_.getColumnCount() - 1, 200, cr, ce);
            column.setPreferredWidth(columnWidth_[model_.getColumnCount() - 1]);

         } else {
            column = new TableColumn(colIndex, 200, cellRenderer, channelCellEditor_);
            column.setPreferredWidth(columnWidth_[colIndex]);
            // HACK: the "Configuration" tab should be wider than the others.
            if (colIndex == 1) {
               column.setMinWidth((int) (ACQ_DEFAULT_COLUMN_WIDTH * 1.25));
            }
         }
         channelTable.addColumn(column);
      }

      return channelTable;
   }

   private CheckBoxPanel createCheckBoxPanel(String text) {
      CheckBoxPanel thePanel = new CheckBoxPanel(text);
      setupPanel(thePanel);
      return thePanel;
   }

   private LabelPanel createLabelPanel(String text) {
      LabelPanel thePanel = new LabelPanel(text);
      setupPanel(thePanel);
      return thePanel;
   }

   private void setupPanel(ComponentTitledPanel thePanel) {
      thePanel.setTitleFont(new Font("Dialog", Font.BOLD, 12));
      thePanel.setLayout(null);
   }

   /**
    * Creates the panel for showing timepoints settings. This one can have
    * its contents overridden by the custom time intervals system, in which
    * case its normal contents get hidden.
    */
   private JPanel createTimePoints() {
      framesPanel_ = createCheckBoxPanel("Time Points");
      framesPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT + ", hidemode 3",
            "[grow, fill]", "[grow, fill]"));

      defaultTimesPanel_ = new JPanel(
            new MigLayout("fill, gap 2, insets 0",
                  "push[][][]push", "[][][]"));
      framesPanel_.add(defaultTimesPanel_, "grow");

      customTimesPanel_ = new JPanel(
            new MigLayout("fill, gap 0, insets 0"));
      customTimesPanel_.setVisible(false);
      framesPanel_.add(customTimesPanel_, "grow");

      final JLabel numberLabel = new JLabel("Count:");
      numberLabel.setFont(DEFAULT_FONT);

      defaultTimesPanel_.add(numberLabel, "alignx label");

      SpinnerModel sModel = new SpinnerNumberModel(1, 1, null, 1);

      numFrames_ = new JSpinner(sModel);
      JTextField field = ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField();
      field.setColumns(5);
      ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField().setFont(DEFAULT_FONT);
      numFrames_.addChangeListener((ChangeEvent e) -> applySettingsFromGUI());

      defaultTimesPanel_.add(numFrames_, "wrap");

      final JLabel intervalLabel = new JLabel("Interval:");
      intervalLabel.setFont(DEFAULT_FONT);
      intervalLabel.setToolTipText(
            "Interval between successive time points.  Setting an interval "
                  + "less than the exposure time will cause micromanager to acquire "
                  + "a 'burst' of images as fast as possible");
      defaultTimesPanel_.add(intervalLabel, "alignx label");

      interval_ = new JFormattedTextField(numberFormat_);
      interval_.setColumns(5);
      interval_.setFont(DEFAULT_FONT);
      interval_.setValue(1.0);
      interval_.addPropertyChangeListener("value", this);
      defaultTimesPanel_.add(interval_);

      timeUnitCombo_ = new JComboBox<>();
      timeUnitCombo_.setModel(new DefaultComboBoxModel<>(new String[] {"ms", "s", "min"}));
      timeUnitCombo_.setFont(DEFAULT_FONT);
      // We shove this thing to the left a bit so that it takes up the same
      // vertical space as the spinner for the number of timepoints.
      defaultTimesPanel_.add(timeUnitCombo_, "pad 0 -15 0 0, wrap");

      JButton advancedButton = new JButton("Advanced...");
      advancedButton.setFont(DEFAULT_FONT);
      advancedButton.addActionListener((ActionEvent e) -> {
         showCustomTimesDialog();
         updateGUIContents();
      });

      defaultTimesPanel_.add(advancedButton, "skip, span 2, align left");

      JLabel overrideLabel = new JLabel("Custom time intervals enabled");
      overrideLabel.setFont(new Font("Arial", Font.BOLD, 12));
      overrideLabel.setForeground(Color.red);

      JButton disableCustomIntervalsButton = new JButton("Disable custom intervals");
      disableCustomIntervalsButton.addActionListener((ActionEvent e) ->
            getAcquisitionEngine().setSequenceSettings(getAcquisitionEngine()
                  .getSequenceSettings().copyBuilder().useCustomIntervals(false).build()));
      disableCustomIntervalsButton.setFont(DEFAULT_FONT);

      customTimesPanel_.add(overrideLabel, "alignx center, wrap");
      customTimesPanel_.add(disableCustomIntervalsButton, "alignx center");

      framesPanel_.addActionListener((ActionEvent e) -> applySettingsFromGUI());
      return framesPanel_;
   }

   private JPanel createMultiPositions() {
      positionsPanel_ = createCheckBoxPanel("Multiple Positions (XY)");
      positionsPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT,
            "[grow]", "[grow, fill]"));
      listButton_ = new JButton("Edit Position List...");
      listButton_.setToolTipText("Open XY list dialog");
      listButton_.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/application_view_list.png"));
      listButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      listButton_.addActionListener((ActionEvent e) -> mmStudio_.app().showPositionList());

      // Not sure why 'span' is needed to prevent second column from appearing
      // (interaction with CheckBoxPanel layout??)
      positionsPanel_.add(listButton_, "span, alignx center");
      return positionsPanel_;
   }

   private JPanel createZStacks() {
      slicesPanel_ = createCheckBoxPanel("Z-Stacks (Slices)");
      slicesPanel_.setLayout(new MigLayout("fillx, gap 2, insets 2",
            "push[][][][]push", ""));

      String labelConstraint = "pushx 100, alignx label";

      // Simplify inserting unit labels slightly.
      final Runnable addUnits = () -> {
         JLabel label = new JLabel("\u00b5m"); // Micro Sign
         label.setFont(DEFAULT_FONT);
         slicesPanel_.add(label, "gapleft 0, gapright 4");
      };

      final JLabel zbottomLabel = new JLabel("Start Z:");
      zbottomLabel.setFont(DEFAULT_FONT);
      slicesPanel_.add(zbottomLabel, labelConstraint);

      zBottom_ = new JFormattedTextField(numberFormat_);
      zBottom_.setColumns(5);
      zBottom_.setFont(DEFAULT_FONT);
      zBottom_.setValue(1.0);
      zBottom_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zBottom_);
      addUnits.run();

      // Slightly smaller than BUTTON_SIZE
      final String buttonSize = "width 50!, height 20!";

      setBottomButton_ = new JButton("Set");
      setBottomButton_.setMargin(new Insets(-5, -5, -5, -5));
      setBottomButton_.setFont(new Font("", Font.PLAIN, 10));
      setBottomButton_.setToolTipText("Set value as microscope's current Z position");
      setBottomButton_.addActionListener((final ActionEvent e) -> setBottomPosition());
      slicesPanel_.add(setBottomButton_, buttonSize + ", pushx 100, wrap");

      final JLabel ztopLabel = new JLabel("End Z:");
      ztopLabel.setFont(DEFAULT_FONT);
      slicesPanel_.add(ztopLabel, labelConstraint);

      zTop_ = new JFormattedTextField(numberFormat_);
      zTop_.setColumns(5);
      zTop_.setFont(DEFAULT_FONT);
      zTop_.setValue(1.0);
      zTop_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zTop_);
      addUnits.run();

      setTopButton_ = new JButton("Set");
      setTopButton_.setMargin(new Insets(-5, -5, -5, -5));
      setTopButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      setTopButton_.setToolTipText("Set value as microscope's current Z position");
      setTopButton_.addActionListener((final ActionEvent e) -> setTopPosition());
      slicesPanel_.add(setTopButton_, buttonSize + ", pushx 100, wrap");

      final JLabel zstepLabel = new JLabel("Step size:");
      zstepLabel.setFont(DEFAULT_FONT);
      slicesPanel_.add(zstepLabel, labelConstraint);

      zStep_ = new JFormattedTextField(numberFormat_);
      zStep_.setColumns(5);
      zStep_.setFont(DEFAULT_FONT);
      zStep_.setValue(1.0);
      zStep_.addPropertyChangeListener("value", this);
      slicesPanel_.add(zStep_);
      addUnits.run();
      slicesPanel_.add(new JLabel(""), "wrap");

      zValCombo_ = new JComboBox<>(new String[] {RELATIVE_Z, ABSOLUTE_Z});
      zValCombo_.setFont(DEFAULT_FONT);
      zValCombo_.addActionListener((final ActionEvent e) -> zvalCalcChanged());
      slicesPanel_.add(zValCombo_,
            "skip 1, spanx, gaptop 4, gapbottom 0, alignx left, wrap");

      stackKeepShutterOpenCheckBox_ = new JCheckBox("Keep shutter open");
      stackKeepShutterOpenCheckBox_.setFont(DEFAULT_FONT);
      stackKeepShutterOpenCheckBox_.setSelected(false);
      stackKeepShutterOpenCheckBox_.addActionListener((final ActionEvent e) ->
            applySettingsFromGUI());
      slicesPanel_.add(stackKeepShutterOpenCheckBox_,
            "skip 1, spanx, gaptop 0, alignx left");

      slicesPanel_.addActionListener((final ActionEvent e) -> {
         // enable disable all related controls
         applySettingsFromGUI();
      });
      return slicesPanel_;
   }

   private JPanel createAcquisitionOrder() {
      acquisitionOrderPanel_ = createLabelPanel("Acquisition Order");
      acquisitionOrderPanel_.setLayout(
            new MigLayout(PANEL_CONSTRAINT + ", flowy"));
      acqOrderBox_ = new JComboBox<>();
      acqOrderBox_.setFont(new Font("", Font.PLAIN, 10));
      acquisitionOrderPanel_.add(acqOrderBox_, "alignx center");
      acquisitionOrderText_ = new JTextArea(3, 25);
      acquisitionOrderText_.setEditable(false);
      acquisitionOrderText_.setFont(new Font("", Font.PLAIN, 9));
      acquisitionOrderPanel_.add(acquisitionOrderText_, "alignx center");

      acqOrderModes_ = new AcqOrderMode[4];
      acqOrderModes_[0] = new AcqOrderMode(AcqOrderMode.TIME_POS_SLICE_CHANNEL);
      acqOrderModes_[1] = new AcqOrderMode(AcqOrderMode.TIME_POS_CHANNEL_SLICE);
      acqOrderModes_[2] = new AcqOrderMode(AcqOrderMode.POS_TIME_SLICE_CHANNEL);
      acqOrderModes_[3] = new AcqOrderMode(AcqOrderMode.POS_TIME_CHANNEL_SLICE);
      acqOrderBox_.addItem(acqOrderModes_[0]);
      acqOrderBox_.addItem(acqOrderModes_[1]);
      acqOrderBox_.addItem(acqOrderModes_[2]);
      acqOrderBox_.addItem(acqOrderModes_[3]);
      return acquisitionOrderPanel_;
   }

   private JPanel createAutoFocus() {
      afPanel_ = createCheckBoxPanel("Autofocus");
      afPanel_.setLayout(new MigLayout(PANEL_CONSTRAINT));

      JButton afButton = new JButton("Options...");
      afButton.setToolTipText("Set autofocus options");
      afButton.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/wrench_orange.png"));
      afButton.setMargin(new Insets(2, 5, 2, 5));
      afButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      afButton.addActionListener((ActionEvent arg0) -> mmStudio_.app().showAutofocusDialog());
      afPanel_.add(afButton, "alignx center, wrap");

      final JLabel afSkipFrame1 = new JLabel("Skip frame(s):");
      afSkipFrame1.setFont(new Font("Dialog", Font.PLAIN, 10));
      afSkipFrame1.setToolTipText("How many frames to skip between running autofocus. "
            + "Autofocus is always run at new stage positions");

      afPanel_.add(afSkipFrame1, "split, spanx, alignx center");

      afSkipInterval_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
      JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) afSkipInterval_.getEditor();
      editor.setFont(DEFAULT_FONT);
      editor.getTextField().setColumns(3);
      afSkipInterval_.setValue(getAcquisitionEngine().getSequenceSettings().skipAutofocusCount());
      afSkipInterval_.addChangeListener((ChangeEvent e) -> {
         applySettingsFromGUI();
         afSkipInterval_.setValue(getAcquisitionEngine().getSequenceSettings()
               .skipAutofocusCount());
      });
      afPanel_.add(afSkipInterval_);

      afPanel_.addActionListener((ActionEvent arg0) -> applySettingsFromGUI());
      return afPanel_;
   }

   private JPanel createSummary() {
      JPanel summaryPanel = createLabelPanel("Summary");
      summaryPanel.setLayout(new MigLayout(PANEL_CONSTRAINT + ", filly, insets 4 8 4 8"));
      summaryTextArea_ = new JTextArea(8, 25);
      summaryTextArea_.setFont(new Font("Arial", Font.PLAIN, 11));
      summaryTextArea_.setEditable(false);
      summaryTextArea_.setOpaque(false);
      summaryPanel.add(summaryTextArea_, "grow");
      return summaryPanel;
   }

   private CheckBoxPanel createChannelsPanel() {
      CheckBoxPanel channelsPanel = createCheckBoxPanel("Channels");
      channelsPanel.setLayout(new MigLayout("fill, gap 2, insets 2",
            "[grow][]", "[][grow]"));

      final JLabel channelsLabel = new JLabel("Channel group:");
      channelsLabel.setFont(DEFAULT_FONT);
      channelsPanel.add(channelsLabel, "split, alignx label");

      channelGroupCombo_ = new JComboBox<>();
      channelGroupCombo_.setFont(new Font("", Font.PLAIN, 10));
      updateGroupsCombo();
      channelGroupCombo_.addActionListener((ActionEvent arg0) -> {
         String newGroup = (String) channelGroupCombo_.getSelectedItem();
         if (getAcquisitionEngine().setChannelGroup(newGroup)) {
            channelCellEditor_.stopCellEditing();
            if (mmStudio_.getAutofocusManager() != null) {
               mmStudio_.getAutofocusManager().refresh();
            }
         } else {
            updateGroupsCombo(); // NS 2021-05-6: not sure what this is needed for.
         }
      });
      channelsPanel.add(channelGroupCombo_, "alignx left");

      chanKeepShutterOpenCheckBox_ = new JCheckBox("Keep shutter open");
      chanKeepShutterOpenCheckBox_.setFont(DEFAULT_FONT);
      chanKeepShutterOpenCheckBox_.addActionListener((final ActionEvent e) ->
            applySettingsFromGUI());
      chanKeepShutterOpenCheckBox_.setSelected(false);
      channelsPanel.add(chanKeepShutterOpenCheckBox_, "gapleft push, wrap");

      channelsPanel.add(channelTablePane_, "height 60:60:, grow");

      // Slightly smaller than BUTTON_SIZE, and the gap matches the insets of the panel.
      final String buttonConstraint = "width 60!, height 20!, gapleft 2";

      final JButton addButton = new JButton("New");
      addButton.setFont(DEFAULT_FONT);
      addButton.setMargin(new Insets(0, 0, 0, 0));
      addButton.setToolTipText("Add an additional channel");
      addButton.addActionListener((ActionEvent e) -> {
         applySettingsFromGUI();
         model_.addNewChannel();
         model_.fireTableStructureChanged();
      });
      channelsPanel.add(addButton, buttonConstraint + ", flowy, split, aligny top");

      final JButton removeButton = new JButton("Remove");
      removeButton.setFont(DEFAULT_FONT);
      removeButton.setMargin(new Insets(-5, -5, -5, -5));
      removeButton.setToolTipText("Remove currently selected channel");
      removeButton.addActionListener((ActionEvent e) -> {
         int sel = channelTable_.getSelectedRow();
         if (sel > -1) {
            applySettingsFromGUI();
            model_.removeChannel(sel);
            model_.fireTableStructureChanged();
            if (channelTable_.getRowCount() > sel) {
               channelTable_.setRowSelectionInterval(sel, sel);
            }
         }
      });
      channelsPanel.add(removeButton, buttonConstraint);

      final JButton upButton = new JButton("Up");
      upButton.setFont(DEFAULT_FONT);
      upButton.setMargin(new Insets(0, 0, 0, 0));
      upButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
            "Move currently selected channel up (Channels higher on list are acquired first)"));
      upButton.addActionListener((ActionEvent e) -> {
         int sel = channelTable_.getSelectedRow();
         if (sel > -1) {
            applySettingsFromGUI();
            int newSel = model_.rowUp(sel);
            model_.fireTableStructureChanged();
            channelTable_.setRowSelectionInterval(newSel, newSel);
         }
      });
      channelsPanel.add(upButton, buttonConstraint);

      final JButton downButton = new JButton("Down");
      downButton.setFont(DEFAULT_FONT);
      downButton.setMargin(new Insets(0, 0, 0, 0));
      downButton.setText("Down");
      downButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
            "Move currently selected channel down (Channels lower on list are acquired later)"));
      downButton.addActionListener((ActionEvent e) -> {
         int sel = channelTable_.getSelectedRow();
         if (sel > -1) {
            applySettingsFromGUI();
            int newSel = model_.rowDown(sel);
            model_.fireTableStructureChanged();
            channelTable_.setRowSelectionInterval(newSel, newSel);
         }
      });
      channelsPanel.add(downButton, buttonConstraint);

      channelsPanel.addActionListener((ActionEvent e) -> applySettingsFromGUI());
      return channelsPanel;
   }

   private JComponent createCloseButton() {
      final JButton closeButton = new JButton("Close");
      closeButton.setFont(DEFAULT_FONT);
      closeButton.addActionListener((ActionEvent e) -> close());
      return closeButton;
   }

   private JPanel createRunButtons() {
      final JPanel result = new JPanel(new MigLayout("flowy, insets 0, gapx 0, gapy 2"));
      final JButton acquireButton = new JButton("Acquire!");
      acquireButton.setMargin(new Insets(-9, -9, -9, -9));
      acquireButton.setFont(new Font("Arial", Font.BOLD, 12));
      acquireButton.addActionListener((ActionEvent e) -> {
         AbstractCellEditor ae = (AbstractCellEditor) channelTable_.getCellEditor();
         if (ae != null) {
            ae.stopCellEditing();
         }
         runAcquisition();
      });
      result.add(acquireButton, BUTTON_SIZE);

      final JButton stopButton = new JButton("Stop");
      stopButton.addActionListener((final ActionEvent e) -> getAcquisitionEngine().abortRequest());
      stopButton.setFont(new Font("Arial", Font.BOLD, 12));
      result.add(stopButton, BUTTON_SIZE);
      return result;
   }

   private JPanel createSaveButtons() {
      final JPanel result = new JPanel(new MigLayout("flowy, insets 0, gapx 0, gapy 2"));
      final JButton loadButton = new JButton("Load...");
      loadButton.setToolTipText("Load acquisition settings");
      loadButton.setFont(DEFAULT_FONT);
      loadButton.setMargin(new Insets(-5, -5, -5, -5));
      loadButton.addActionListener((ActionEvent e) -> loadAcqSettingsFromFile());
      result.add(loadButton, BUTTON_SIZE);

      final JButton saveAsButton = new JButton("Save as...");
      saveAsButton.setToolTipText("Save current acquisition settings as");
      saveAsButton.setFont(DEFAULT_FONT);
      saveAsButton.setMargin(new Insets(-5, -5, -5, -5));
      saveAsButton.addActionListener((ActionEvent e) -> saveAcqSettingsToFile());
      result.add(saveAsButton, BUTTON_SIZE);
      return result;
   }

   private JPanel createSavePanel() {
      savePanel_ = createCheckBoxPanel("Save Images");
      savePanel_.setLayout(new MigLayout(PANEL_CONSTRAINT,
            "[][grow, fill][]", "[][][]"));

      final JLabel rootLabel = new JLabel("Directory root:");
      rootLabel.setFont(DEFAULT_FONT);
      savePanel_.add(rootLabel, "alignx label");

      rootField_ = new JTextField();
      rootField_.setFont(DEFAULT_FONT);
      rootField_.addFocusListener(focusListener_);
      savePanel_.add(rootField_);

      JButton browseRootButton = new JButton("...");
      browseRootButton.setToolTipText("Browse");
      browseRootButton.setMargin(new Insets(2, 5, 2, 5));
      browseRootButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      browseRootButton.addActionListener((final ActionEvent e) -> setRootDirectory());
      savePanel_.add(browseRootButton, "wrap");

      JLabel namePrefixLabel = new JLabel("Name prefix:");
      namePrefixLabel.setFont(DEFAULT_FONT);
      savePanel_.add(namePrefixLabel, "alignx label");

      nameField_ = new JTextField();
      nameField_.setFont(DEFAULT_FONT);
      nameField_.addFocusListener(focusListener_);

      savePanel_.add(nameField_, "wrap");

      JLabel saveTypeLabel = new JLabel("Saving format:");
      saveTypeLabel.setFont(DEFAULT_FONT);
      savePanel_.add(saveTypeLabel, "alignx label");

      singleButton_ = new JRadioButton("Separate image files");
      singleButton_.setFont(DEFAULT_FONT);
      singleButton_.addActionListener(e -> {
         DefaultDatastore.setPreferredSaveMode(mmStudio_,
               Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
         applySettingsFromGUI();
      });
      savePanel_.add(singleButton_, "spanx, split");

      multiButton_ = new JRadioButton("Image stack file");
      multiButton_.setFont(DEFAULT_FONT);
      multiButton_.addActionListener(e -> {
         DefaultDatastore.setPreferredSaveMode(mmStudio_,
               Datastore.SaveMode.MULTIPAGE_TIFF);
         applySettingsFromGUI();
      });
      savePanel_.add(multiButton_, "spanx, split");


      ndtiffButton_ = new JRadioButton("NDTiff");
      ndtiffButton_.setFont(DEFAULT_FONT);
      ndtiffButton_.addActionListener(e -> {
         DefaultDatastore.setPreferredSaveMode(mmStudio_,
                 Datastore.SaveMode.ND_TIFF);
         applySettingsFromGUI();
      });
      savePanel_.add(ndtiffButton_, "spanx, split");

      JButton helpButton = new JButton();
      helpButton.setText("<HTML><font color=\"#70A3CC\" size = \"3\">Which to use?</font></HTML>");
      helpButton.setBorderPainted(false);
      helpButton.setOpaque(false);
      helpButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               Desktop.getDesktop().browse(new URL(
                       "https://micro-manager.org/Micro-Manager_File_Formats").toURI());
            } catch (IOException | URISyntaxException ex) {
               ReportingUtils.logError(ex);
            }
         }
      });
      helpButton.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseEntered(MouseEvent e) {
            helpButton.setText("<HTML><font color=\"#70A3CC\" size ="
                    + " \"3\"><u>Which to use?</u></font></HTML>");
         }

         @Override
         public void mouseExited(MouseEvent e) {
            helpButton.setText("<HTML><font color=\"#70A3CC\" size = "
                    + "\"3\">Which to use?</font></HTML>");
         }
      });
      savePanel_.add(helpButton, "gapafter push");

      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(singleButton_);
      buttonGroup.add(multiButton_);
      buttonGroup.add(ndtiffButton_);

      Datastore.SaveMode mode = mmStudio_.data().getPreferredSaveMode();
      if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
         singleButton_.setSelected(true);
      } else if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
         multiButton_.setSelected(true);
      } else if (mode == Datastore.SaveMode.ND_TIFF) {
         ndtiffButton_.setSelected(true);
      } else {
         ReportingUtils.logError("Unrecognized save mode " + mode);
      }

      savePanel_.addActionListener((final ActionEvent e) -> applySettingsFromGUI());
      return savePanel_;
   }

   private JPanel createCommentsPanel(JTextArea commentTextArea, FocusListener focusListener) {
      ComponentTitledPanel commentsPanel = createLabelPanel("Acquisition Comments");
      commentsPanel.setLayout(new MigLayout(PANEL_CONSTRAINT,
            "[grow, fill]", "[]"));

      commentTextArea.setRows(4);
      commentTextArea.setFont(new Font("", Font.PLAIN, 10));
      commentTextArea.setToolTipText("Comment for the acquisition to be run");
      commentTextArea.setWrapStyleWord(true);
      commentTextArea.setLineWrap(true);
      commentTextArea.addFocusListener(focusListener);

      JScrollPane commentScrollPane = new JScrollPane();
      commentScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLoweredBevelBorder(),
            BorderFactory.createEtchedBorder()));
      commentScrollPane.setViewportView(commentTextArea);

      commentsPanel.add(commentScrollPane, "wmin 0, height pref!, span");
      return commentsPanel;
   }

   /**
    * Update the example text describing how the acquisition will proceed.
    */
   private void updateAcquisitionOrderText() {
      if (acquisitionOrderText_ != null && acqOrderBox_ != null
            && acqOrderBox_.getSelectedItem() != null) {
         acquisitionOrderText_.setText(
               ((AcqOrderMode) (acqOrderBox_.getSelectedItem())).getExample());
      }
   }

   /**
    * Called when a field's "value" property changes.
    * Causes the Summary to be updated.
    *
    * @param e Property that changed.
    */
   @Override
   public void propertyChange(PropertyChangeEvent e) {
      // update summary
      applySettingsFromGUI();
      summaryTextArea_.setText(getAcquisitionEngine().getVerboseSummary());
   }


   /**
    * Called when exposure time of a channel changes.
    *
    * @param event Information about the channel and new exposure time.
    */
   @Subscribe
   public void onChannelExposure(ChannelExposureEvent event) {
      String channel = event.getChannel();
      if (!channel.equals("") && getShouldSyncExposure()) {
         String channelGroup = event.getChannelGroup();
         double exposure = event.getNewExposureTime();
         model_.setChannelExposureTime(channelGroup, channel, exposure);
      }
   }

   /**
    * Called when the color of a channel changes.
    *
    * @param event Information about the channel and new color.
    */
   @Subscribe
   public void onChannelColorEvent(ChannelColorEvent event) {
      model_.setChannelColor(event.getChannelGroup(), event.getChannel(), event.getColor());
      ChannelDisplaySettings newCDS =
            RememberedDisplaySettings.loadChannel(mmStudio_, event.getChannelGroup(),
                  event.getChannel(), null).copyBuilder().color(event.getColor())
                  .build();
      RememberedDisplaySettings.storeChannel(mmStudio_, event.getChannelGroup(),
            event.getChannel(), newCDS);
   }

   /**
    * Called when the GUI needs updating.
    *
    * @param event Event signaling GUI Refresh request.
    */
   @Subscribe
   public void onGUIRefresh(GUIRefreshEvent event) {
      // The current active channel may have changed, necessitating a refresh
      // of the main exposure time.
      if (getShouldSyncExposure()) {
         try {
            String channelGroup = mmStudio_.core().getChannelGroup();
            String channel = mmStudio_.core().getCurrentConfig(channelGroup);
            if (model_.hasChannel(channelGroup, channel)) {
               double exposure = model_.getChannelExposureTime(
                     channelGroup, channel, 10.0);
               mmStudio_.app().setChannelExposureTime(channelGroup, channel,
                     exposure);
            }
         } catch (Exception e) {
            mmStudio_.logs().logError(e, "Error getting channel exposure time");
         }
      }
   }

   /**
    * Called when the channel group changes.
    *
    * @param event Event signaling the new channel group.
    */
   @Subscribe
   public void onChannelGroupChanged(ChannelGroupChangedEvent event) {
      getAcquisitionEngine().setSequenceSettings(getAcquisitionEngine().getSequenceSettings()
            .copyBuilder().channelGroup(event.getNewChannelGroup()).build());
      updateChannelAndGroupCombo();
   }

   private boolean inArray(String member, String[] group) {
      for (String group1 : group) {
         if (member.equals(group1)) {
            return true;
         }
      }
      return false;
   }


   /**
    * Closes a(and disposes) the MDA window.
    */
   public void close() {
      try {
         saveAcqSettingsToProfile();
      } catch (Throwable t) {
         ReportingUtils.logError(t, "in saveAcqSettings");
      }
      try {
         dispose();
      } catch (Throwable t) {
         ReportingUtils.logError(t, "in dispose");
      }
      if (null != mmStudio_) {
         try {
            mmStudio_.app().makeActive();
         } catch (Throwable t) {
            ReportingUtils.logError(t, "in makeActive");
         }
      }
   }

   /**
    * Updates the channel group drop down box.
    */
   public final void updateGroupsCombo() {
      String[] groups = getAcquisitionEngine().getAvailableGroups();
      if (groups.length != 0) {
         channelGroupCombo_.setModel(new DefaultComboBoxModel<>(groups));
         if (!inArray(getAcquisitionEngine().getChannelGroup(), groups)) {
            getAcquisitionEngine().setChannelGroup(getAcquisitionEngine().getFirstConfigGroup());
         }

         channelGroupCombo_.setSelectedItem(getAcquisitionEngine().getChannelGroup());
      }
   }

   public void updateChannelAndGroupCombo() {
      updateGroupsCombo();
      model_.cleanUpConfigurationList();
   }

   /**
    * Loads the Acquisition settings from the use profile.
    *
    * @return Object defining acquisitions settings.
    */
   public final synchronized SequenceSettings loadAcqSettingsFromProfile() {
      String seqString = settings_.getString(MDA_SEQUENCE_SETTINGS, "");
      if (!seqString.isEmpty()) {
         return SequenceSettings.fromJSONStream(seqString);
      }
      return (new SequenceSettings.Builder()).build();
   }

   /**
    * Updates the dialog (with acqEngine settings) and only returns once this
    * is actually executed (on the EDT).
    */
   public void updateGUIBlocking() {
      if (!SwingUtilities.isEventDispatchThread()) {
         try {
            SwingUtilities.invokeAndWait(this::updateGUIContents);
         } catch (InterruptedException | InvocationTargetException e) {
            mmStudio_.logs().logError(e);
         }
      }
   }

   public final void updateGUIContents() {
      SequenceSettings sequenceSettings = getAcquisitionEngine().getSequenceSettings();
      updateGUIFromSequenceSettings(sequenceSettings);
   }

   private void updateGUIFromSequenceSettings(final SequenceSettings sequenceSettings) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> updateGUIFromSequenceSettings(sequenceSettings));
      } else {
         if (disableGUItoSettings_) {
            return;
         }
         disableGUItoSettings_ = true;

         numFrames_.setValue(sequenceSettings.numFrames());
         timeUnitCombo_.setSelectedIndex(sequenceSettings.displayTimeUnit());
         interval_.setText(numberFormat_.format(convertMsToTime(
               sequenceSettings.intervalMs(),
               timeUnitCombo_.getSelectedIndex())));

         boolean framesEnabled = sequenceSettings.useFrames();
         framesPanel_.setSelected(framesEnabled);
         defaultTimesPanel_.setVisible(!framesEnabled);
         customTimesPanel_.setVisible(framesEnabled);

         boolean isCustom = sequenceSettings.useCustomIntervals();
         defaultTimesPanel_.setVisible(!isCustom);
         customTimesPanel_.setVisible(isCustom);

         positionsPanel_.setSelected(sequenceSettings.usePositionList());

         zBottom_.setText(NumberUtils.doubleToDisplayString(sequenceSettings.sliceZBottomUm()));
         zTop_.setText(NumberUtils.doubleToDisplayString(sequenceSettings.sliceZTopUm()));
         zStep_.setText(NumberUtils.doubleToDisplayString(sequenceSettings.sliceZStepUm()));
         zBottom_.setEnabled(sequenceSettings.useSlices());
         zTop_.setEnabled(sequenceSettings.useSlices());
         zStep_.setEnabled(sequenceSettings.useSlices());
         zValCombo_.setEnabled(sequenceSettings.useSlices());
         zRelativeAbsolute_ = sequenceSettings.relativeZSlice() ? 0 : 1;
         zValCombo_.setSelectedIndex(zRelativeAbsolute_);
         stackKeepShutterOpenCheckBox_.setSelected(sequenceSettings.keepShutterOpenSlices());
         slicesPanel_.setSelected(sequenceSettings.useSlices());

         afPanel_.setSelected(sequenceSettings.useAutofocus());

         channelsPanel_.setSelected(sequenceSettings.useChannels());
         channelGroupCombo_.setSelectedItem(sequenceSettings.channelGroup());
         getAcquisitionEngine().setChannelGroup(sequenceSettings.channelGroup());
         model_.setChannels(sequenceSettings.channels());
         model_.fireTableStructureChanged();
         chanKeepShutterOpenCheckBox_.setSelected(sequenceSettings.keepShutterOpenChannels());
         channelTable_.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
         boolean selected = channelsPanel_.isSelected();
         channelTable_.setEnabled(selected);
         channelTable_.getTableHeader().setForeground(selected ? Color.black : Color.gray);

         for (AcqOrderMode mode : acqOrderModes_) {
            mode.setEnabled(framesPanel_.isSelected(), positionsPanel_.isSelected(),
                  slicesPanel_.isSelected(), channelsPanel_.isSelected());
         }
         // add correct acquisition order options
         int selectedIndex = sequenceSettings.acqOrderMode();
         ActionListener[] actionListeners = acqOrderBox_.getActionListeners();
         for (ActionListener al : actionListeners) {
            acqOrderBox_.removeActionListener(al);
         }
         acqOrderBox_.removeAllItems();
         if (framesPanel_.isSelected() && positionsPanel_.isSelected()
               && slicesPanel_.isSelected() && channelsPanel_.isSelected()) {
            acqOrderBox_.addItem(acqOrderModes_[0]);
            acqOrderBox_.addItem(acqOrderModes_[1]);
            acqOrderBox_.addItem(acqOrderModes_[2]);
            acqOrderBox_.addItem(acqOrderModes_[3]);
         } else if (framesPanel_.isSelected() && positionsPanel_.isSelected()) {
            if (selectedIndex == 0 || selectedIndex == 2) {
               acqOrderBox_.addItem(acqOrderModes_[0]);
               acqOrderBox_.addItem(acqOrderModes_[2]);
            } else {
               acqOrderBox_.addItem(acqOrderModes_[1]);
               acqOrderBox_.addItem(acqOrderModes_[3]);
            }
         } else if (channelsPanel_.isSelected() && slicesPanel_.isSelected()) {
            if (selectedIndex == 0 || selectedIndex == 1) {
               acqOrderBox_.addItem(acqOrderModes_[0]);
               acqOrderBox_.addItem(acqOrderModes_[1]);
            } else {
               acqOrderBox_.addItem(acqOrderModes_[2]);
               acqOrderBox_.addItem(acqOrderModes_[3]);
            }
         } else {
            acqOrderBox_.addItem(acqOrderModes_[selectedIndex]);
         }
         acqOrderBox_.setSelectedItem(acqOrderModes_[sequenceSettings.acqOrderMode()]);
         for (ActionListener al : actionListeners) {
            acqOrderBox_.addActionListener(al);
         }
         savePanel_.setSelected(sequenceSettings.save());
         nameField_.setText(sequenceSettings.prefix());
         rootField_.setText(sequenceSettings.root());

         commentTextArea_.setText(sequenceSettings.comment());

         DefaultDatastore.setPreferredSaveMode(mmStudio_, sequenceSettings.saveMode());
         if (sequenceSettings.saveMode() == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
            singleButton_.setSelected(true);
         } else if (sequenceSettings.saveMode() == Datastore.SaveMode.MULTIPAGE_TIFF) {
            multiButton_.setSelected(true);
         } else if (sequenceSettings.saveMode() == Datastore.SaveMode.ND_TIFF) {
            ndtiffButton_.setSelected(true);
         }

         // update summary
         summaryTextArea_.setText(getAcquisitionEngine().getVerboseSummary());

         framesPanel_.repaint();
         positionsPanel_.repaint();
         afPanel_.repaint();
         slicesPanel_.repaint();
         channelsPanel_.repaint();
         savePanel_.repaint();

         disableGUItoSettings_ = false;
      }
   }

   /**
    * Signals that the position list was replaced with a new one.
    *
    * @param newPositionListEvent Contains the new (immutable) Postion List.
    */
   @Subscribe
   public void onNewPositionList(NewPositionListEvent newPositionListEvent) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> onNewPositionList(newPositionListEvent));
      } else {
         getAcquisitionEngine().setPositionList(newPositionListEvent.getPositionList());
         // update summary
         summaryTextArea_.setText(getAcquisitionEngine().getVerboseSummary());
      }
   }

   /**
    * Save acquisition settings to the user profile.
    */
   public synchronized void saveAcqSettingsToProfile() {
      applySettingsFromGUI();
      SequenceSettings sequenceSettings = getAcquisitionEngine().getSequenceSettings();
      settings_.putString(MDA_SEQUENCE_SETTINGS, SequenceSettings.toJSONStream(sequenceSettings));

      // Save model column widths and order
      for (int k = 0; k < model_.getColumnCount(); k++) {
         TableColumn tableColumn = findTableColumn(channelTable_, k);
         if (tableColumn != null) {
            settings_.putInteger(ACQ_COLUMN_WIDTH + k, tableColumn.getWidth());
         }
         settings_.putInteger(ACQ_COLUMN_ORDER + k, channelTable_.convertColumnIndexToView(k));
      }
   }

   /**
    * Returns the TableColumn associated with the specified column
    * index in the model.
    *
    * @param table            table in which to find the column.
    * @param columnModelIndex index to look for the TableColumn.
    * @return the TableColumn or null if not found.
    */
   public TableColumn findTableColumn(JTable table, int columnModelIndex) {
      Enumeration<?> e = table.getColumnModel().getColumns();
      while (e.hasMoreElements()) {
         TableColumn col = (TableColumn) e.nextElement();
         if (col.getModelIndex() == columnModelIndex) {
            return col;
         }
      }
      return null;
   }

   protected void setRootDirectory() {
      File result = FileDialogs.openDir(this,
            "Please choose a directory root for image data",
            FileDialogs.MM_DATA_SET);
      if (result != null) {
         rootField_.setText(result.getAbsolutePath().trim());
         getAcquisitionEngine().setSequenceSettings(getAcquisitionEngine().getSequenceSettings()
               .copyBuilder().root(result.getAbsolutePath().trim()).build());
      }
   }

   /**
    * Sets the z top position based on current value of the z drive.
    */
   public void setTopPosition() {
      try {
         double z = mmStudio_.core().getPosition();
         zTop_.setText(NumberUtils.doubleToDisplayString(z));
         applySettingsFromGUI();
         summaryTextArea_.setText(getAcquisitionEngine().getVerboseSummary());
      } catch (Exception e) {
         mmStudio_.logs().showError(e, "Error getting Z Position");
      }
   }

   protected void setBottomPosition() {
      try {
         double z = mmStudio_.core().getPosition();
         zBottom_.setText(NumberUtils.doubleToDisplayString(z));
         applySettingsFromGUI();
         summaryTextArea_.setText(getAcquisitionEngine().getVerboseSummary());
      } catch (Exception e) {
         mmStudio_.logs().showError(e, "Error getting Z Position");
      }
   }

   protected void loadAcqSettingsFromFile() {
      File f = FileDialogs.openFile(this,
            "Load acquisition settings", FileDialogs.ACQ_SETTINGS_FILE);
      if (f != null) {
         try {
            loadAcqSettingsFromFile(f.getAbsolutePath());
         } catch (IOException ex) {
            ReportingUtils.showError(ex, "Failed to load Acquisition setting file");
         }
      }
   }

   /**
    * Load the MDA settings from file.
    *
    * @param path File (path) containing previously saved acquisition settings.
    * @throws IOException when file can not be read.
    */
   public void loadAcqSettingsFromFile(String path) throws IOException {
      if (new File(path).canRead()) {
         final SequenceSettings settings = mmStudio_.acquisitions().loadSequenceSettings(path);
         try {
            GUIUtils.invokeAndWait(() -> getAcquisitionEngine().setSequenceSettings(settings));
         } catch (InterruptedException e) {
            ReportingUtils.logError(e, "Interrupted while updating GUI");
         } catch (InvocationTargetException e) {
            ReportingUtils.logError(e, "Error updating GUI");
         }
      } else {
         throw new IOException("Can not read file: " + path);
      }
   }

   protected void saveAcqSettingsToFile() {
      saveAcqSettingsToProfile();
      File file = FileDialogs.save(this, "Save the acquisition settings file",
            FileDialogs.ACQ_SETTINGS_FILE);
      if (file != null) {
         try {
            SequenceSettings settings = getAcquisitionEngine().getSequenceSettings();
            mmStudio_.acquisitions().saveSequenceSettings(settings,
                  file.getAbsolutePath());
         } catch (IOException e) {
            ReportingUtils.showError(e);
         }
      }
   }

   /**
    * Asks acqEngine to estimate memory usage.
    * Use this method only after
    * settings have been send to acqEngine.
    * Prompt the user if there may not be enough memory.
    *
    * @return true if user chooses to cancel after being prompted.
    */
   private boolean warnMemoryMayNotBeSufficient() {
      if (savePanel_.isSelected()) {
         return false;
      }

      long acqTotalBytes = getAcquisitionEngine().getTotalMemory();
      if (acqTotalBytes < 0) {
         return true;
      }

      // get memory than can be used within JVM:
      // https://stackoverflow.com/questions/12807797/java-get-available-memory
      long allocatedMemory = (Runtime.getRuntime().totalMemory()
            - Runtime.getRuntime().freeMemory());
      long freeRam = Runtime.getRuntime().maxMemory() - allocatedMemory;

      // There is no hard reason for the 80% factor.
      if (acqTotalBytes > 0.8 * freeRam) {
         // for copying style
         JLabel label = new JLabel();
         Font font = label.getFont();
         // create some css from the label's font
         StringBuilder style = new StringBuilder("font-family:" + font.getFamily() + ";");
         style.append("font-weight:").append(font.isBold() ? "bold" : "normal").append(";");
         style.append("font-size:").append(font.getSize()).append("pt;");
         Color c = label.getForeground();
         style.append(("color:rgb(")).append(c.getRed()).append(",")
               .append(c.getGreen()).append(",").append(c.getBlue()).append(")");

         int availableMemoryMB = (int) (freeRam / (1024 * 1024));

         String paneTxt = "<html><body style="
               + style + "><p width='400'>"
               + "Available memory (approximate estimate: " + availableMemoryMB
               + " MB) may not be sufficient. "
               + "Once memory is full, the acquisition may slow down or fail.</p>"
               + "<p width='400'>See <a style=\"" + style + ""
               +
               "\" href=https://micro-manager.org/wiki/Micro-Manager_Configuration_Guide#Memory_Settings> "
               + " the configuration guide</a> for ways to make more memory available.</p>"
               + "<p width='400'>Would you like to start the acquisition anyway?</p>"
               + "</body></html>";
         JEditorPane ep = new JEditorPane("text/html", paneTxt);

         // handle link events
         ep.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
               try {
                  Desktop.getDesktop().browse(e.getURL().toURI());
               } catch (IOException | URISyntaxException ex) {
                  mmStudio_.logs().logError(ex);
               }
            }
         });
         ep.setEditable(false);
         ep.setBackground(label.getBackground());

         int answer = JOptionPane.showConfirmDialog(this, ep,
               "Not enough memory", JOptionPane.YES_NO_OPTION);
         return answer != JOptionPane.YES_OPTION;
      }
      return false;
   }

   /**
    * Starts running an acquisition with the current settings in the MDA window.
    * Does not block, i.e. returns after starting the acquisition.
    *
    * @return Datastore destination of the acquisition that was started.
    */
   public Datastore runAcquisition() {
      if (getAcquisitionEngine().isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this,
               "Cannot start acquisition: previous acquisition still in progress.");
         return null;
      }

      if (warnMemoryMayNotBeSufficient()) {
         return null;
      }

      try {
         applySettingsFromGUI();
         saveAcqSettingsToProfile();
         ChannelTableModel model = (ChannelTableModel) channelTable_.getModel();
         if (getAcquisitionEngine().getSequenceSettings().useChannels()
               && model.duplicateChannels()) {
            JOptionPane.showMessageDialog(this,
                  "Cannot start acquisition using the same channel twice");
            return null;
         }
         // Check for excessively long exposure times.
         ArrayList<String> badChannels = new ArrayList<>();
         for (ChannelSpec spec : model.getChannels()) {
            if (spec.exposure() > 30000) { // More than 30s
               badChannels.add(spec.config());
            }
         }
         if (badChannels.size() > 0 && getShouldCheckExposureSanity()) {
            String channelString = (badChannels.size() == 1)
                  ? String.format("the %s channel", badChannels.get(0))
                  : String.format("these channels: %s", badChannels);
            String message = String.format(
                  "Found unusually long exposure times for %s. "
                        + "Are you sure you want to run this acquisition?",
                  channelString);
            JCheckBox neverAgain = new JCheckBox("Do not ask me again.");
            int response = JOptionPane.showConfirmDialog(this,
                  new Object[] {message, neverAgain},
                  "Confirm exposure times", JOptionPane.YES_NO_OPTION);
            setShouldCheckExposureSanity(!neverAgain.isSelected());
            if (response != JOptionPane.YES_OPTION) {
               return null;
            }
         }
         return getAcquisitionEngine().acquire();
      } catch (MMException | RuntimeException e) {
         getAcquisitionEngine().shutdown();
         ReportingUtils.showError(e);
         return null;
      }
   }

   /**
    * Runs an acquisition with the current settings in the MDA window except that
    * the name and root where data are saved are replaced with the input parameters.
    *
    * @param acqName Name under which these data should be stored.
    * @param acqRoot The root (directory) in which the data should be stored.
    * @return Datastore destination of the acquisition that was started.
    */
   public Datastore runAcquisition(String acqName, String acqRoot) {
      if (getAcquisitionEngine().isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this,
               "Unable to start the new acquisition task: previous acquisition still in progress.");
         return null;
      }

      applySettingsFromGUI();
      if (warnMemoryMayNotBeSufficient()) {
         return null;
      }

      try {
         ChannelTableModel model = (ChannelTableModel) channelTable_.getModel();
         if (getAcquisitionEngine().getSequenceSettings().useChannels()
               && model.duplicateChannels()) {
            JOptionPane.showMessageDialog(this,
                  "Cannot start acquisition using the same channel twice");
            return null;
         }
         SequenceSettings.Builder sb = getAcquisitionEngine().getSequenceSettings().copyBuilder();
         getAcquisitionEngine().setSequenceSettings(sb.prefix(acqName).root(acqRoot).save(true)
               .build());

         return getAcquisitionEngine().acquire();
      } catch (MMException e) {
         ReportingUtils.showError(e);
         return null;
      }
   }

   /**
    * Finds a value in an array of ints.
    * Can this be replaced with something more solid?
    *
    * @param numbers array to search in.
    * @param key     value to look for.
    * @return index to the value if found, -1 otherwise.
    */
   public static int search(int[] numbers, int key) {
      for (int index = 0; index < numbers.length; index++) {
         if (numbers[index] == key) {
            return index;
         }
      }
      return -1;
   }

   @Override
   public void settingsChanged() {
      if (this.isDisplayable()) {
         updateGUIContents();
      }
   }

   private void applySettingsFromGUI() {
      if (disableGUItoSettings_) {
         return;
      }
      disableGUItoSettings_ = true;

      final int editingRow = channelTable_.getEditingRow();
      final int editingColumn = channelTable_.getEditingColumn();

      AbstractCellEditor ae = (AbstractCellEditor) channelTable_.getCellEditor();
      if (ae != null) {
         ae.stopCellEditing();
      }

      SequenceSettings.Builder ssb = new SequenceSettings.Builder();

      try {
         if (acqOrderBox_.getSelectedItem() != null) {
            ssb.acqOrderMode(((AcqOrderMode) acqOrderBox_.getSelectedItem()).getID());
         }

         ssb.useFrames(framesPanel_.isSelected());
         ssb.numFrames((Integer) numFrames_.getValue());
         ssb.intervalMs(convertTimeToMs(
               NumberUtils.displayStringToDouble(interval_.getText()),
               timeUnitCombo_.getSelectedIndex()));
         ssb.displayTimeUnit(timeUnitCombo_.getSelectedIndex());
         ssb.useCustomIntervals(getAcquisitionEngine().getSequenceSettings().useCustomIntervals());
         ssb.customIntervalsMs(getAcquisitionEngine().getSequenceSettings().customIntervalsMs());

         ssb.sliceZBottomUm(NumberUtils.displayStringToDouble(zBottom_.getText()));
         ssb.sliceZTopUm(NumberUtils.displayStringToDouble(zTop_.getText()));
         ssb.sliceZStepUm(NumberUtils.displayStringToDouble(zStep_.getText()));
         ssb.relativeZSlice(zRelativeAbsolute_ == 0);  // 0 == relative, 1 == absolute
         ssb.useSlices(slicesPanel_.isSelected());

         ssb.usePositionList(positionsPanel_.isSelected());

         ssb.useChannels(channelsPanel_.isSelected());
         ssb.channelGroup(getAcquisitionEngine().getChannelGroup());
         ssb.channels(((ChannelTableModel) channelTable_.getModel()).getChannels());

         ssb.skipAutofocusCount(
               NumberUtils.displayStringToInt(afSkipInterval_.getValue().toString()));
         ssb.keepShutterOpenChannels(chanKeepShutterOpenCheckBox_.isSelected());
         ssb.keepShutterOpenSlices(stackKeepShutterOpenCheckBox_.isSelected());

      } catch (ParseException p) {
         ReportingUtils.showError(p);
         // TODO: throw error
      }

      ssb.save(savePanel_.isSelected());

      // avoid dangerous characters in the name that will be used as a directory name
      String name = nameField_.getText().replaceAll("[/\\\\*!':]", "-");
      ssb.prefix(name.trim());
      ssb.root(rootField_.getText().trim());

      // update summary

      ssb.comment(commentTextArea_.getText());
      ssb.useAutofocus(afPanel_.isSelected());
      ssb.shouldDisplayImages(!getShouldHideMDADisplay());

      // Save preferred save mode.
      if (singleButton_.isSelected()) {
         DefaultDatastore.setPreferredSaveMode(mmStudio_,
               Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
      } else if (multiButton_.isSelected()) {
         DefaultDatastore.setPreferredSaveMode(mmStudio_,
                 Datastore.SaveMode.MULTIPAGE_TIFF);
      } else if (ndtiffButton_.isSelected()) {
         DefaultDatastore.setPreferredSaveMode(mmStudio_, Datastore.SaveMode.ND_TIFF);
      } else {
         ReportingUtils.logError(
               "Unknown save mode button or no save mode buttons selected");
      }
      ssb.saveMode(DefaultDatastore.getPreferredSaveMode(mmStudio_));
      ssb.cameraTimeout(getAcquisitionEngine().getSequenceSettings().cameraTimeout());

      try {
         getAcquisitionEngine().setSequenceSettings(ssb.build());
      } catch (UnsupportedOperationException uoex) {
         mmStudio_.logs().showError(
               "Zero Z step size is not supported, resetting to 1 micron", this);
         getAcquisitionEngine().setSequenceSettings(ssb.sliceZStepUm(1.0).build());
      }

      disableGUItoSettings_ = false;
      updateGUIContents();
      channelTable_.editCellAt(editingRow, editingColumn, null);
   }


   private double convertTimeToMs(double interval, int units) {
      switch (units) {
         case 1:
            return interval * 1000; // sec
         case 2:
            return interval * 60.0 * 1000.0; // min
         case 0:
            return interval; // ms
         default:
            break;
      }
      ReportingUtils.showError("Unknown units supplied for acquisition interval!");
      return interval;
   }

   private double convertMsToTime(double intervalMs, int units) {
      switch (units) {
         case 1:
            return intervalMs / 1000; // sec
         case 2:
            return intervalMs / (60.0 * 1000.0); // min
         case 0:
            return intervalMs; // ms
         default:
            break;
      }
      ReportingUtils.showError("Unknown units supplied for acquisition interval!");
      return intervalMs;
   }

   private void zvalCalcChanged() {
      final boolean isEnabled = Objects.equals(zValCombo_.getSelectedItem(), ABSOLUTE_Z);
      // HACK: push this to a later call; even though this method should only
      // be called from the EDT, for some reason if we do this action
      // immediately, then the buttons don't visually become disabled.
      SwingUtilities.invokeLater(() -> {
         setTopButton_.setEnabled(isEnabled);
         setBottomButton_.setEnabled(isEnabled);
      });

      if (zRelativeAbsolute_ == zValCombo_.getSelectedIndex()) {
         return;
      }

      zRelativeAbsolute_ = zValCombo_.getSelectedIndex();
      double zBottomUm;
      double zTopUm;
      try {
         zBottomUm = NumberUtils.displayStringToDouble(zBottom_.getText());
         zTopUm = NumberUtils.displayStringToDouble(zTop_.getText());
      } catch (ParseException e) {
         ReportingUtils.logError(e);
         return;
      }

      double curZ = getAcquisitionEngine().getCurrentZPos();
      double newTop;
      double newBottom;
      if (zRelativeAbsolute_ == 0) {
         // convert from absolute to relative
         newTop = zTopUm - curZ;
         newBottom = zBottomUm - curZ;
      } else {
         // convert from relative to absolute
         newTop = zTopUm + curZ;
         newBottom = zBottomUm + curZ;
      }
      zBottom_.setText(NumberUtils.doubleToDisplayString(newBottom));
      zTop_.setText(NumberUtils.doubleToDisplayString(newTop));
      applySettingsFromGUI();
   }

   private void showCustomTimesDialog() {
      if (customTimesWindow == null) {
         customTimesWindow = new CustomTimesDialog(getAcquisitionEngine(), mmStudio_);
      }
      customTimesWindow.setVisible(true);
   }

   @Override
   public void tableChanged(TableModelEvent e) {
      summaryTextArea_.setText(getAcquisitionEngine().getVerboseSummary());
   }

   /**
    * Utility class to make a panel with a border with a title.
    */
   public static class ComponentTitledPanel extends JPanel {
      public ComponentTitledBorder compTitledBorder;
      public boolean borderSet_ = false;
      public Component titleComponent;

      @Override
      public void setBorder(Border border) {
         if (compTitledBorder != null && borderSet_) {
            compTitledBorder.setBorder(border);
         } else {
            super.setBorder(border);
         }
      }

      @Override
      public Border getBorder() {
         return compTitledBorder;
      }

      /**
       * HACK: when the look and feel changes, the background colors of all
       * components are supposed to get updated. For some reason, our custom
       * titled components don't receive this update, so we have to propagate
       * it manually.
       *
       * @param c Color to use for custom title components
       */
      @Override
      public void setBackground(Color c) {
         super.setBackground(c);
         // This can get called from the super constructor, at which point
         // titleComponent won't be defined yet.
         if (titleComponent != null) {
            titleComponent.setBackground(c);
         }
      }

      public void setTitleFont(Font font) {
         titleComponent.setFont(font);
      }
   }

   /**
    * Utility class to make a panel with a title.
    */
   public static class LabelPanel extends ComponentTitledPanel {
      LabelPanel(String title) {
         super();
         JLabel label = new JLabel(title);
         titleComponent = label;
         label.setOpaque(true);
         label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
         compTitledBorder = new ComponentTitledBorder(label, this,
               BorderFactory.createEtchedBorder());
         super.setBorder(compTitledBorder);
         borderSet_ = true;
      }

   }

   /**
    * Utility class to make a checkboxPanel with a border with a title.
    */
   public static class CheckBoxPanel extends ComponentTitledPanel {

      JCheckBox checkBox;

      CheckBoxPanel(String title) {
         super();
         titleComponent = new JCheckBox(title);
         checkBox = (JCheckBox) titleComponent;

         compTitledBorder = new ComponentTitledBorder(checkBox, this,
               BorderFactory.createEtchedBorder());
         super.setBorder(compTitledBorder);
         borderSet_ = true;

         final CheckBoxPanel thisPanel = this;

         checkBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               boolean enable = checkBox.isSelected();
               thisPanel.setChildrenEnabled(enable);
            }

            public void writeObject(java.io.ObjectOutputStream stream) throws MMException {
               throw new MMException("Do not serialize this class");
            }
         });
      }

      /**
       * Sets enable/disable for all children of this component.
       *
       * @param enabled selects enable or disable.
       */
      public void setChildrenEnabled(boolean enabled) {
         Component[] comp = this.getComponents();
         for (Component comp1 : comp) {
            if (comp1.getClass() == JPanel.class) {
               Component[] subComp = ((JPanel) comp1).getComponents();
               for (Component subComp1 : subComp) {
                  subComp1.setEnabled(enabled);
               }
            } else {
               comp1.setEnabled(enabled);
            }
         }
      }

      public boolean isSelected() {
         return checkBox.isSelected();
      }

      public void setSelected(boolean selected) {
         checkBox.setSelected(selected);
         setChildrenEnabled(selected);
      }

      public void addActionListener(ActionListener actionListener) {
         checkBox.addActionListener(actionListener);
      }

      /**
       * Removes all action listeners.
       */
      public void removeActionListeners() {
         for (ActionListener l : checkBox.getActionListeners()) {
            checkBox.removeActionListener(l);
         }
      }
   }

   public static boolean getShouldSyncExposure() {
      return MMStudio.getInstance().profile().getSettings(
            AcqControlDlg.class).getBoolean(SHOULD_SYNC_EXPOSURE, true);
   }

   public static void setShouldSyncExposure(boolean shouldSync) {
      MMStudio.getInstance().profile().getSettings(AcqControlDlg.class)
            .putBoolean(SHOULD_SYNC_EXPOSURE, shouldSync);
   }

   public static boolean getShouldHideMDADisplay() {
      return MMStudio.getInstance().profile().getSettings(AcqControlDlg.class)
            .getBoolean(SHOULD_HIDE_DISPLAY, false);
   }

   public static void setShouldHideMDADisplay(boolean shouldHide) {
      MMStudio.getInstance().profile().getSettings(AcqControlDlg.class)
            .putBoolean(SHOULD_HIDE_DISPLAY, shouldHide);
   }

   public boolean getShouldCheckExposureSanity() {
      return profile_.getSettings(AcqControlDlg.class)
            .getBoolean(SHOULD_CHECK_EXPOSURE_SANITY, true);
   }

   public void setShouldCheckExposureSanity(boolean shouldCheck) {
      profile_.getSettings(AcqControlDlg.class)
            .putBoolean(SHOULD_CHECK_EXPOSURE_SANITY, shouldCheck);
   }
}