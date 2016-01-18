package org.multibit.hd.ui.error_reporting;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.miginfocom.swing.MigLayout;
import org.multibit.commons.concurrent.SafeExecutors;
import org.multibit.hd.common.error_reporting.ErrorReportResult;
import org.multibit.hd.common.error_reporting.ErrorReportStatus;
import org.multibit.hd.core.error_reporting.ExceptionHandler;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.ShutdownEvent;
import org.multibit.hd.core.logging.LogbackFactory;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.*;
import org.multibit.hd.ui.views.components.borders.TextBubbleBorder;
import org.multibit.hd.ui.views.themes.Themes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * <p>Swing dialog to provide the following to application:</p>
 * <ul>
 * <li>Reporting of error messages to user</li>
 * <li>Gathering of extra information from user</li>
 * <li>Uploading of encrypted logs to ELK stack</li>
 * </ul>
 * <p/>
 * <p>Notes:</p>
 * <ol>
 * <li>We must extend JFrame to allow for exceptions occurring before the UI has rendered</li>
 * <li>Lower level modules use Reflection to invoke this dialog to gain i18n capabilities</li>
 * </ol>
 *
 * @since 0.1.0
 *  
 */
public class ErrorReportingDialog extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(ErrorReportingDialog.class);

  private JTextArea userMessage;

  private JLabel currentLogLabel;
  private JScrollPane currentLogScrollPane;

  private JLabel uploadProgressLabel;

  private final boolean showApology;

  /**
   * A flag tracking whether the ErrorReportingDialog is being shown (to avoid stacking from repeated errors)
   */
  private static boolean dialogBeingShown = false;

  /**
   * The instance of the ErrorReportingDialog that is actually being shown to the user
   */
  private static ErrorReportingDialog currentDialog = null;

  /**
   * @param showApology True if the apology message should be displayed
   */
  public ErrorReportingDialog(boolean showApology) {
    this.showApology = showApology;

    initComponents();
  }

  @SuppressWarnings("unchecked")
  private void initComponents() {

    setTitle(Languages.safeText(MessageKey.ERROR_REPORTING_TITLE));
    setIconImage(Images.newLogoIconImage());

    JPanel contentPanel = Panels.newPanel(
      new MigLayout(
        Panels.migXYDetailLayout() + ",hidemode 1", // Ensure the details do not take up space
        "[][]", // Columns
        "[]10[][][][][shrink][shrink]" // Rows
      ));


    JLabel preambleLabel;
    if (showApology) {
      preambleLabel = Labels.newNoteLabel(
        new MessageKey[]{
          MessageKey.ERROR_REPORTING_APOLOGY_NOTE_1,
          MessageKey.ERROR_REPORTING_APOLOGY_NOTE_2,
          MessageKey.ERROR_REPORTING_APOLOGY_NOTE_3
        },
        new Object[][]{}
      );
    } else {
      preambleLabel = Labels.newNoteLabel(
        new MessageKey[]{
          MessageKey.ERROR_REPORTING_MANUAL_NOTE_1,
          MessageKey.ERROR_REPORTING_MANUAL_NOTE_2
        },
        new Object[][]{}
      );
    }

    JLabel notesLabel = Labels.newLabel(MessageKey.ERROR_REPORTING_NOTES);

    // User message

    // Provide space for a user message
    userMessage = TextBoxes.newTextArea(3, 40);

    // The message is a wall of text so needs scroll bars in many cases
    userMessage.setBorder(null);

    // Message requires its own scroll pane
    JScrollPane userMessageScrollPane = new JScrollPane();
    userMessageScrollPane.setOpaque(true);
    userMessageScrollPane.setBackground(Themes.currentTheme.dataEntryBackground());
    userMessageScrollPane.setBorder(null);
    userMessageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    userMessageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

    // View port requires special handling
    userMessageScrollPane.setViewportView(userMessage);
    userMessageScrollPane.getViewport().setBackground(Themes.currentTheme.dataEntryBackground());
    userMessageScrollPane.setViewportBorder(new TextBubbleBorder(Themes.currentTheme.dataEntryBorder()));

    // Ensure we maintain the overall theme
    ScrollBarUIDecorator.apply(userMessageScrollPane, true);

    // Current log

    // Provide space for current log
    currentLogLabel = Labels.newLabel(MessageKey.ERROR_REPORTING_CONTENTS);
    final JTextArea currentLog = TextBoxes.newReadOnlyTextArea(10, 40);

    Optional<File> currentLoggingFile = LogbackFactory.getCurrentLoggingFile();
    if (currentLoggingFile.isPresent()) {
      try {
        FileInputStream fis = new FileInputStream(currentLoggingFile.get());
        currentLog.setText(ExceptionHandler.readAndTruncateInputStream(fis, 204_800));
      } catch (FileNotFoundException e) {
        log.error("Could not find the current log file", e);
        currentLog.setText("No log available");
      }
    } else {
      currentLog.setText("No log available");
    }

    // The message is a wall of text so needs scroll bars in many cases
    currentLog.setBorder(null);

    // Message requires its own scroll pane
    currentLogScrollPane = new JScrollPane();
    currentLogScrollPane.setOpaque(true);
    currentLogScrollPane.setBackground(Themes.currentTheme.dataEntryBackground());
    currentLogScrollPane.setBorder(null);
    currentLogScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    currentLogScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

    // View port requires special handling
    currentLogScrollPane.setViewportView(currentLog);
    currentLogScrollPane.getViewport().setBackground(Themes.currentTheme.dataEntryBackground());
    currentLogScrollPane.setViewportBorder(new TextBubbleBorder(Themes.currentTheme.dataEntryBorder()));

    // Ensure we maintain the overall theme
    ScrollBarUIDecorator.apply(currentLogScrollPane, true);

    // Set the current log details to be invisible at the start
    currentLogLabel.setVisible(false);
    currentLogScrollPane.setVisible(false);

    // Upload progress
    uploadProgressLabel = Labels.newLabel(MessageKey.ERROR_REPORTING_UPLOADING);
    uploadProgressLabel.setVisible(false);

    // Add them to the panel
    contentPanel.add(preambleLabel, "span 2,push,wrap");

    contentPanel.add(notesLabel, "span 2,wrap");
    contentPanel.add(userMessageScrollPane, "span 2,wrap");

    contentPanel.add(Buttons.newDetailsButton(getDetailsAction()), "span 2,wrap");

    contentPanel.add(currentLogLabel, "span 2,wrap");
    contentPanel.add(currentLogScrollPane, "span 2,grow,push,wrap,wmin 10"); // wmin ensures a resize

    contentPanel.add(uploadProgressLabel, "span 2,growx,wrap");

    if (showApology) {
      // User must be informed they will be forced to Exit
      contentPanel.add(Buttons.newExitButton(getCancelAction(), true), "align left");
    } else {
      // User has option to continue
      contentPanel.add(Buttons.newCancelButton(getCancelAction()), "align left");
    }
    contentPanel.add(Buttons.newUploadErrorReportButton(getUploadAction()), "align right,wrap");

    getContentPane().add(contentPanel);

    if (dialogBeingShown) {
      log.debug("There is already an error being shown so not displaying another one");
      if (currentDialog != null) {
        currentDialog.toFront();
      }
    } else {
      currentDialog = this;
      dialogBeingShown = true;

      addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            handleClose();
          }


        });

      setMinimumSize(new Dimension(400, 200));

      setLocationRelativeTo(null);

      pack();
      setVisible(true);

      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {

            if (showApology) {
              // Ensure the user is locked out of any activity since we're going to crash
              Panels.getApplicationFrame().setEnabled(false);
            }

            // Ensure the error dialog gets to the front (even if the frame shows up later)
            currentDialog.toFront();
          }
        });
    }
  }

  private Action getDetailsAction() {

    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Toggle the visibility of the current log details
        currentLogLabel.setVisible(!currentLogLabel.isVisible());
        currentLogScrollPane.setVisible(!currentLogScrollPane.isVisible());
        pack();
      }
    };

  }

  private Action getUploadAction() {

    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String truncatedUserMessage = userMessage.getText();
        if (Strings.isNullOrEmpty(truncatedUserMessage)) {
          truncatedUserMessage = "";
        } else if (truncatedUserMessage.length() > 1000) {
          truncatedUserMessage = truncatedUserMessage.substring(0, 1000);
        }

        // Build the upload URL (do it first to fail fast)
        final URL liveErrorReportingUrl;
        try {
          liveErrorReportingUrl = new URL(ExceptionHandler.LIVE_ERROR_REPORTING_URL);
        } catch (MalformedURLException e1) {
          log.error("Failed to create the live URL", e1);
          handleClose();
          return;
        }

        // Prevent further upload attempts
        ((JButton) e.getSource()).setEnabled(false);

        // Show the upload status (triggers a resize to attract the eye)
        uploadProgressLabel.setVisible(true);

        final String finalTruncatedMessage = truncatedUserMessage;

        // Upload off the EDT
        final ListenableFuture<ErrorReportResult> future = SafeExecutors.newSingleThreadExecutor("error-reporting").submit(
          new Callable<ErrorReportResult>() {
            @Override
            public ErrorReportResult call() throws Exception {
              // Upload error report
              return ExceptionHandler.handleErrorReportUpload(
                finalTruncatedMessage,
                liveErrorReportingUrl
              );
            }
          });
        Futures.addCallback(
          future, new FutureCallback<ErrorReportResult>() {
            @Override
            public void onSuccess(ErrorReportResult result) {
              handleErrorReportResult(result);
            }

            @Override
            public void onFailure(Throwable t) {
              ErrorReportResult result = new ErrorReportResult();
              result.setErrorReportStatus(ErrorReportStatus.UPLOAD_FAILED);
              handleErrorReportResult(result);
            }
          });

      }
    };

  }

  private Action getCancelAction() {

    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleClose();
      }
    };

  }

  /**
   * Performs final actions on close
   */
  private void handleClose() {
    currentDialog = null;
    dialogBeingShown = false;
    dispose();
    if (showApology) {
      // Perform a hard shutdown if we've crashed
      CoreEvents.fireShutdownEvent(ShutdownEvent.ShutdownType.HARD);
    }
  }

  /**
   * Performs final actions on close
   */
  private void handleErrorReportResult(final ErrorReportResult errorReportResult) {

    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {

          final MessageKey uploadProgressKey;

          switch (errorReportResult.getErrorReportStatus()) {
            case UPLOAD_OK_KNOWN:
              uploadProgressKey = MessageKey.ERROR_REPORTING_UPLOAD_COMPLETE;
              break;
            case UPLOAD_OK_UNKNOWN:
              uploadProgressKey = MessageKey.ERROR_REPORTING_UPLOAD_COMPLETE;
              break;
            case UPLOAD_FAILED:
              uploadProgressKey = MessageKey.ERROR_REPORTING_UPLOAD_FAILED;
              break;
            default:
              throw new IllegalStateException("Unknown error report result: " + errorReportResult.getErrorReportStatus().name());
          }

          uploadProgressLabel.setText(Languages.safeText(uploadProgressKey));
          uploadProgressLabel.setVisible(true);

        }
      });
  }
}
