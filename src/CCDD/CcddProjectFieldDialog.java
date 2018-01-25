/**
 * CFS Command & Data Dictionary project data field manager dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLEAR_ICON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.FIELD_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.Border;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command & Data Dictionary project data field manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddProjectFieldDialog extends CcddDialogHandler
{
    // CLass references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private CcddUndoManager undoManager;
    private CcddUndoHandler undoHandler;
    private CcddInputFieldPanelHandler fieldPnlHndlr;
    private CcddFieldHandler fieldHandler;

    // Components referenced by multiple methods
    private JButton btnManageFields;
    private JButton btnClearValues;

    // Storage for the project description and data fields as they currently exist in the database
    private String committedDescription;
    private List<FieldInformation> committedFieldInformation;

    // Flag that indicates if the project data field manager dialog is undergoing initialization
    private boolean isInitializing;

    // Initial (minimum) dialog width in pixels
    private int minDialogWidth;

    // Dialog title
    private static final String DIALOG_TITLE = "Manage Project Fields";

    /**********************************************************************************************
     * Project data field manager dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddProjectFieldDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbControl = ccddMain.getDbControlHandler();

        // Create the project data field manager dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the project data field manager dialog. This is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        minDialogWidth = 0;

        // Build the project data field manager dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel dialogPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /**************************************************************************************
             * Build the project data field manager dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                0,
                                                                1,
                                                                1,
                                                                1.0,
                                                                0.0,
                                                                GridBagConstraints.LINE_START,
                                                                GridBagConstraints.BOTH,
                                                                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                           0,
                                                                           0),
                                                                0,
                                                                0);

                Border emptyBorder = BorderFactory.createEmptyBorder();

                // Set the flag to indicate the project data field manager dialog is being
                // initialized
                isInitializing = true;

                // Add an undo edit manager
                undoManager = new CcddUndoManager()
                {
                    /******************************************************************************
                     * Update the change indicator if the editor panel has changed
                     *****************************************************************************/
                    @Override
                    protected void ownerHasChanged()
                    {
                        // Check if the project data field manager dialog is not being initialized
                        // - changes during initialization are ignored
                        if (!isInitializing)
                        {
                            updateChangeIndicator();
                        }
                    }
                };

                // Create the undo handler for the components with undoable actions. Disable
                // storage of edit actions during dialog creation
                undoHandler = new CcddUndoHandler(undoManager);
                undoHandler.setAllowUndo(false);

                // Create the field panel for the project description and data fields
                fieldPnlHndlr = new CcddInputFieldPanelHandler()
                {
                    /******************************************************************************
                     * Update the project data field manager change indicator
                     *****************************************************************************/
                    @Override
                    protected void updateOwnerChangeIndicator()
                    {
                        updateChangeIndicator();
                    }
                };

                // Get the project description
                committedDescription = dbControl.getDatabaseDescription(dbControl.getDatabaseName());

                // Set the undo/redo manager and handler for the description and data field values
                fieldPnlHndlr.setEditPanelUndo(undoManager, undoHandler);

                // Create a field handler containing all data fields
                fieldHandler = new CcddFieldHandler(ccddMain,
                                                    null,
                                                    CcddProjectFieldDialog.this);

                // Set the data field handler reference in the undo handler so that data field
                // edits can be undone/redone
                undoHandler.setFieldHandler(fieldHandler);

                // Build the field information for the project data fields
                fieldHandler.buildFieldInformation(CcddFieldHandler.getFieldProjectName());
                committedFieldInformation = fieldHandler.getFieldInformationCopy();

                // Create the input field panel
                fieldPnlHndlr.createDescAndDataFieldPanel(CcddProjectFieldDialog.this,
                                                          null,
                                                          null,
                                                          committedDescription,
                                                          fieldHandler);

                // Set the modal undo manager in the keyboard handler while the project data
                // field manager is active
                ccddMain.getKeyboardHandler().setModalDialogReference(undoManager, null);

                dialogPnl.setBorder(emptyBorder);

                // Create the project data field manager dialog labels and fields
                JLabel dlgLabel = new JLabel("Project: " + dbControl.getProjectName());
                dlgLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                dialogPnl.add(dlgLabel, gbc);

                // Add the field panel to the dialog
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.left = 0;
                gbc.insets.bottom = 0;
                gbc.insets.right = 0;
                gbc.weighty = 1.0;
                gbc.gridy++;
                dialogPnl.add(fieldPnlHndlr.getFieldPanel(), gbc);

                // Re-enable storage of edit actions
                undoHandler.setAllowUndo(true);

                // Manage fields button
                btnManageFields = CcddButtonPanelHandler.createButton("Fields",
                                                                      FIELD_ICON,
                                                                      KeyEvent.VK_F,
                                                                      "Manage the data fields");

                // Add a listener for the Manage Fields command
                btnManageFields.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Manage the data fields
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Create the field editor dialog showing the fields for this project
                        new CcddFieldEditorDialog(ccddMain,
                                                  fieldPnlHndlr,
                                                  CcddFieldHandler.getFieldProjectName(),
                                                  false,
                                                  minDialogWidth);

                        // Set the undo manager in the keyboard handler back to the project data
                        // field manager
                        ccddMain.getKeyboardHandler().setModalDialogReference(undoManager, null);

                        // Enable/disable the Clear values button depending on if any data fields
                        // remain
                        btnClearValues.setEnabled(!fieldHandler.getFieldInformation().isEmpty());
                    }
                });

                // Clear fields button
                btnClearValues = CcddButtonPanelHandler.createButton("Clear",
                                                                     CLEAR_ICON,
                                                                     KeyEvent.VK_C,
                                                                     "Clear the data fields");

                // Enable/disable the Clear values button depending on if any data fields
                // remain
                btnClearValues.setEnabled(!fieldHandler.getFieldInformation().isEmpty());

                // Add a listener for the Clear values command
                btnClearValues.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Clear the table data field values
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Clear all of the data field values for the project
                        fieldPnlHndlr.clearFieldValues();
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit action");

                // Create a listener for the Undo command
                ActionListener undoAction = new ActionListener()
                {
                    /******************************************************************************
                     * Undo the last edit
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        undoManager.undo();

                        // Update the data field background colors
                        fieldPnlHndlr.setFieldBackgound();
                    }
                };

                // Add the undo listener to the Undo button and menu command
                btnUndo.addActionListener(undoAction);

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit action");

                // Create a listener for the Redo command
                ActionListener redoAction = new ActionListener()
                {
                    /******************************************************************************
                     * Redo the last cell that was undone
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        undoManager.redo();

                        // Update the data field background colors
                        fieldPnlHndlr.setFieldBackgound();
                    }
                };

                // Add the redo listener to the Redo button and menu command
                btnRedo.addActionListener(redoAction);

                // Store project data fields button
                JButton btnStoreFields = CcddButtonPanelHandler.createButton("Store",
                                                                             STORE_ICON,
                                                                             KeyEvent.VK_S,
                                                                             "Store project data field updates in the database");

                // Add a listener for the Store button
                btnStoreFields.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Store the project description and data fields in the database
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the project description and/or data fields have changed since
                        // the last database commit, that the user confirms storing the project
                        // fields, and, if the data field table editor is open and has changes that
                        // the user confirms discarding them
                        if (isFieldsChanged()
                            && new CcddDialogHandler().showMessageDialog(CcddProjectFieldDialog.this,
                                                                         "<html><b>Store project data fields?",
                                                                         "Store Project Fields",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Store the project description and data fields in the database
                            ccddMain.getDbTableCommandHandler().modifyProjectFields(fieldPnlHndlr.getDescription(),
                                                                                    fieldHandler.getFieldInformation(),
                                                                                    CcddProjectFieldDialog.this);

                            // Store the updated description and fields for comparison with a
                            // subsequent store operation
                            committedDescription = fieldPnlHndlr.getDescription();
                            committedFieldInformation = fieldHandler.getFieldInformationCopy();
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the group manager");

                // Add a listener for the Close button
                btnClose.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Close the group selection dialog
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if there are no changes to the project data fields or if the user
                        // elects to discard the changes
                        if (!isFieldsChanged()
                            || new CcddDialogHandler().showMessageDialog(CcddProjectFieldDialog.this,
                                                                         "<html><b>Discard changes?",
                                                                         "Discard Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Close the dialog
                            closeDialog();

                            // Clear the modal dialog references in the keyboard handler
                            ccddMain.getKeyboardHandler().setModalDialogReference(null, null);
                        }
                    }
                });

                // Add buttons in the order in which they'll appear (left to right, top to bottom)
                buttonPnl.add(btnManageFields);
                buttonPnl.add(btnUndo);
                buttonPnl.add(btnStoreFields);
                buttonPnl.add(btnClearValues);
                buttonPnl.add(btnRedo);
                buttonPnl.add(btnClose);

                // Distribute the buttons across two rows
                setButtonRows(2);

                // Store the current data field information in the event an undo/redo operation
                // occurs
                fieldPnlHndlr.storeCurrentFieldInformation();
                undoManager.endEditSequence();

                // Reset the flag now that initialization is complete
                isInitializing = false;

                // Add a listener to capture when the dialog first appears
                addComponentListener(new ComponentAdapter()
                {
                    /******************************************************************************
                     * Handle the group manager dialog becoming visible
                     *****************************************************************************/
                    @Override
                    public void componentShown(ComponentEvent ce)
                    {
                        // Check if the minimum dialog width hasn't been stored
                        if (minDialogWidth == 0)
                        {
                            // Store the dialog's width as the minimum and remove this listener
                            minDialogWidth = ce.getComponent().getPreferredSize().width;
                            removeComponentListener(this);
                        }
                    }
                });
            }

            /**************************************************************************************
             * Project data field manager dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the project data field manager dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  dialogPnl,
                                  buttonPnl,
                                  btnClose,
                                  DIALOG_TITLE,
                                  true);
            }

        });
    }

    /**********************************************************************************************
     * Check if the project description or data fields differ from those last committed to the
     * database
     *
     * @return true if the project description or data field definitions have changed
     *********************************************************************************************/
    private boolean isFieldsChanged()
    {
        // Update the field information with the current text field values
        fieldPnlHndlr.updateCurrentFieldValues(fieldHandler.getFieldInformation());

        return CcddFieldHandler.isFieldChanged(fieldHandler.getFieldInformation(),
                                               committedFieldInformation,
                                               false)
               || !committedDescription.equals(fieldPnlHndlr.getDescription());
    }

    /**********************************************************************************************
     * Update the change indicator for the project data field manager
     *********************************************************************************************/
    private void updateChangeIndicator()
    {
        // Replace the dialog title, appending the change indicator if changes exist
        setTitle(DIALOG_TITLE
                 + (isFieldsChanged()
                                      ? "*"
                                      : ""));
    }
}