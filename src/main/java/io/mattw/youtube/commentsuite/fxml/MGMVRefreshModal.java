package io.mattw.youtube.commentsuite.fxml;

import io.mattw.youtube.commentsuite.ImageLoader;
import io.mattw.youtube.commentsuite.MGMVGroupRefresh;
import io.mattw.youtube.commentsuite.RefreshInterface;
import io.mattw.youtube.commentsuite.db.Group;
import io.mattw.youtube.commentsuite.util.ClipboardUtil;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import static javafx.application.Platform.runLater;

/**
 * This modal allows the user to start a group refresh. The group refresh will use the YouTube API to download
 * videos under the GroupItems of the Group in the ManageGroupsManager.
 *
 * @author mattwright324
 * @see RefreshInterface
 * @see MGMVGroupRefresh
 * @see ManageGroupsManager
 */
public class MGMVRefreshModal extends HBox {

    private static final Logger logger = LogManager.getLogger();

    private ClipboardUtil clipboard = new ClipboardUtil();

    @FXML private Label alert;
    @FXML private Label statusStep;
    @FXML private Button btnClose;
    @FXML private Button btnStart;
    @FXML private ProgressBar progressBar;
    @FXML private VBox statusPane;

    @FXML private HBox warningsPane;
    @FXML private Label warnings, elapsedTime, newVideos, totalVideos, newComments, totalComments, newViewers, totalViewers;
    @FXML private ImageView expandIcon;
    @FXML private ListView<String> errorList;
    @FXML private Hyperlink expand;
    @FXML private ImageView endStatus;
    @FXML private ProgressIndicator statusIndicator;

    private Group group;
    private RefreshInterface refreshThread;
    private boolean running = false;
    private boolean hasBeenStarted = false;

    private boolean expanded = false;

    public MGMVRefreshModal(Group group) {
        logger.debug("Initialize for Group [id={},name={}]", group.getId(), group.getName());

        this.group = group;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("MGMVRefreshModal.fxml"));
        loader.setController(this);
        loader.setRoot(this);
        try {
            loader.load();

            expandIcon.setImage(ImageLoader.ANGLE_RIGHT.getImage());

            errorList.prefHeightProperty().bind(statusPane.heightProperty());
            errorList.maxHeightProperty().bind(statusPane.heightProperty());

            expand.setOnAction(ae -> {
                expanded = !expanded;
                runLater(() -> {
                    if (expanded) {
                        expandIcon.setImage(ImageLoader.ANGLE_LEFT.getImage());
                        errorList.setManaged(true);
                        errorList.setVisible(true);
                    } else {
                        expandIcon.setImage(ImageLoader.ANGLE_RIGHT.getImage());
                        errorList.setManaged(false);
                        errorList.setVisible(false);
                    }
                });
            });

            errorList.setOnKeyPressed(ke -> {
                if (ke.getCode() == KeyCode.C && ke.isControlDown()) {
                    clipboard.setClipboard(errorList.getItems());
                }
            });

            btnStart.setOnAction(ae -> new Thread(() -> {
                if (running) {
                    logger.debug("Requesting group refresh stopped for group [id={},name={}]", group.getId(), group.getName());
                    runLater(() -> {
                        btnStart.setDisable(true);
                        endStatus.setImage(ImageLoader.MINUS_CIRCLE.getImage());
                    });
                    refreshThread.hardShutdown();
                    while (refreshThread.isAlive()) {
                        try {
                            Thread.sleep(97);
                        } catch (Exception ignored) {
                        }
                    }
                    running = false;
                    runLater(() -> btnClose.setDisable(false));
                } else {
                    running = true;
                    logger.debug("Starting group refresh for group [id={},name={}]", group.getId(), group.getName());
                    runLater(() -> {
                        statusPane.setVisible(true);
                        statusPane.setManaged(true);
                        btnClose.setDisable(true);
                        btnStart.setText("Stop");
                        alert.setVisible(false);
                        alert.setManaged(false);
                    });

                    refreshThread = new MGMVGroupRefresh(group);
                    runLater(() -> {
                        refreshThread.getObservableErrorList().addListener((ListChangeListener<String>) (lcl) -> runLater(() -> {
                            int items = lcl.getList().size();
                            warningsPane.setManaged(items > 0);
                            warningsPane.setVisible(items > 0);
                            warnings.setText(items + " message(s)");
                        }));
                        errorList.setItems(refreshThread.getObservableErrorList());
                        elapsedTime.textProperty().bind(refreshThread.elapsedTimeProperty());
                        progressBar.progressProperty().bind(refreshThread.progressProperty());
                        statusStep.textProperty().bind(refreshThread.statusStepProperty());

                        newVideos.textProperty().bind(Bindings.format("%,d", refreshThread.newVideosProperty()));
                        totalVideos.textProperty().bind(
                                Bindings.concat("of ")
                                        .concat(Bindings.format("%,d", refreshThread.totalVideosProperty()))
                                        .concat(" total"));

                        newComments.textProperty().bind(Bindings.format("%,d", refreshThread.newCommentsProperty()));
                        totalComments.textProperty().bind(
                                Bindings.concat("of ")
                                        .concat(Bindings.format("%,d", refreshThread.totalCommentsProperty()))
                                        .concat(" total"));

                        newViewers.textProperty().bind(Bindings.format("%,d", refreshThread.newViewersProperty()));
                        totalViewers.textProperty().bind(
                                Bindings.concat("of ")
                                        .concat(Bindings.format("%,d", refreshThread.totalViewersProperty()))
                                        .concat(" total"));
                    });
                    refreshThread.start();
                    hasBeenStarted = true;
                    refreshThread.endedProperty().addListener((o, ov, nv) -> {
                        progressBar.progressProperty().unbind();
                        runLater(() -> {
                            btnStart.setVisible(false);
                            btnStart.setManaged(false);
                            btnClose.setDisable(false);
                            endStatus.setImage(refreshThread.isEndedOnError() ?
                                    ImageLoader.TIMES_CIRCLE.getImage() : ImageLoader.CHECK_CIRCLE.getImage());
                            endStatus.setManaged(true);
                            endStatus.setVisible(true);
                            statusIndicator.setManaged(false);
                            statusIndicator.setVisible(false);
                        });
                    });
                }
            }).start());
        } catch (IOException e) {
            logger.error(e);
        }
    }

    /**
     * Reset the modal back to its original state when being opened.
     */
    public void reset() {
        logger.debug("Resetting state of Refresh Modal");
        running = false;
        hasBeenStarted = false;
        runLater(() -> {
            if (expanded) {
                expand.fire();
            }
            endStatus.setManaged(false);
            endStatus.setVisible(false);
            statusIndicator.setManaged(true);
            statusIndicator.setVisible(true);
            errorList.getItems().clear();
            alert.getStyleClass().remove("alertSuccess");
            alert.getStyleClass().add("alertWarning");
            alert.setVisible(true);
            alert.setManaged(true);
            statusPane.setVisible(false);
            statusPane.setManaged(false);
            btnStart.setVisible(true);
            btnStart.setManaged(true);
            btnStart.setDisable(false);
            btnStart.setText("Start");
            btnStart.getStyleClass().remove("btnWarning");
            btnStart.getStyleClass().add("btnPrimary");
        });
    }

    public Button getBtnClose() {
        return btnClose;
    }

    public Button getBtnStart() {
        return btnStart;
    }

    public ListView<String> getErrorList() {
        return errorList;
    }

    public boolean isHasBeenStarted() {
        return hasBeenStarted;
    }
}
