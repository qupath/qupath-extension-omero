package qupath.ext.omero.gui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Utility methods related to the user interface.
 */
public class UiUtilities {

    private static final ResourceBundle resources = Utils.getResources();

    private UiUtilities() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * @return whether the graphical user interface is used
     */
    public static boolean usingGUI() {
        return QuPathGUI.getInstance() != null;
    }

    /**
     * Creates a Label whose text is selectable with the cursor.
     *
     * @param text the text to display in the label
     * @return a Label that can be selected
     */
    public static Label createSelectableLabel(String text) {
        Label label = new Label(text);

        StackPane textStack = new StackPane();
        TextField textField = new TextField(text);
        textField.setEditable(false);
        textField.setStyle(
                "-fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 0; -fx-padding: 0;"
        );

        // the invisible label is a hack to get the textField to size like a label.
        Label invisibleLabel = new Label();
        invisibleLabel.textProperty().bind(label.textProperty());
        invisibleLabel.setVisible(false);
        textStack.getChildren().addAll(invisibleLabel, textField);
        label.textProperty().bindBidirectional(textField.textProperty());
        label.setGraphic(textStack);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        return label;
    }

    /**
     * Loads the FXML file located at the URL and set its controller.
     *
     * @param controller the controller of the FXML file to load
     * @param url the path of the FXML file to load
     * @throws IOException if an error occurs while loading the FXML file
     */
    public static void loadFXML(Object controller, URL url) throws IOException {
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(controller);
        loader.setController(controller);
        loader.load();
    }

    /**
     * @return a node with a dot, either filled with green if {@code active} or red otherwise
     */
    public static Node createStateNode(boolean active) {
        Circle circle = new Circle(5);
        circle.getStyleClass().add(active ? "connected" : "disconnected");
        return circle;
    }

    /**
     * Paint the specified image onto the specified canvas.
     * Additionally, it returns the {@code WritableImage} for further use.
     *
     * @param image the image to paint on the canvas
     * @param canvas the canvas to paint
     * @return a copy of the input image
     */
    public static WritableImage paintBufferedImageOnCanvas(BufferedImage image, Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Color the canvas in black, in case no new image can be painted
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        WritableImage wi = SwingFXUtils.toFXImage(image, null);

        GuiTools.paintImage(canvas, wi);
        return wi;
    }

    /**
     * Display an error dialog informing the user that the provided connection was interrupted because
     * some pings failed. This function can be called from any thread. The dialog will only be created
     * if {@link #usingGUI()} is true.
     *
     * @param client the client whose connection was closed because of a failing ping
     */
    public static void displayPingErrorDialogIfUiPresent(Client client) {
        if (usingGUI()) {
            Dialogs.showErrorMessage(
                    resources.getString("Utils.connectionError"),
                    MessageFormat.format(
                            resources.getString("Utils.connectionClosed"),
                            client.getApisHandler().getWebServerUri()
                    )
            );
        }
    }

    /**
     * Propagates changes made to a property to another property.
     * <p>
     * The listening property is updated in the UI thread.
     *
     * @param propertyToUpdate the property to update
     * @param propertyToListen the property to listen
     * @return the listener that was added to the property to listen, so that it can be removed when needed
     * @param <T> the type of the property
     */
    public static <T> ChangeListener<? super T> bindPropertyInUIThread(WritableValue<T> propertyToUpdate, ObservableValue<T> propertyToListen) {
        propertyToUpdate.setValue(propertyToListen.getValue());

        ChangeListener<? super T> listener = (p, o, n) -> {
            if (Platform.isFxApplicationThread()) {
                propertyToUpdate.setValue(n);
            } else {
                Platform.runLater(() -> propertyToUpdate.setValue(n));
            }
        };
        propertyToListen.addListener(listener);

        return listener;
    }

    /**
     * Propagates changes made to an observable set to another observable set.
     * <p>
     * The listening set is updated in the UI thread.
     *
     * @param setToUpdate the set to update
     * @param setToListen the set to listen
     * @return the listener that was added to the set to listen, so that it can be removed when needed
     * @param <T> the type of the elements of the sets
     */
    public static <T> SetChangeListener<? super T> bindSetInUIThread(ObservableSet<T> setToUpdate, ObservableSet<T> setToListen) {
        setToUpdate.addAll(setToListen);

        SetChangeListener<? super T> listener = change -> {
            if (Platform.isFxApplicationThread()) {
                if (change.wasAdded()) {
                    setToUpdate.add(change.getElementAdded());
                }
                if (change.wasRemoved()) {
                    setToUpdate.remove(change.getElementRemoved());
                }
            } else {
                Platform.runLater(() -> {
                    if (change.wasAdded()) {
                        setToUpdate.add(change.getElementAdded());
                    }
                    if (change.wasRemoved()) {
                        setToUpdate.remove(change.getElementRemoved());
                    }
                });
            }
        };
        setToListen.addListener(listener);

        return listener;
    }

    /**
     * Propagates changes made to an observable list to another observable list.
     * <p>
     * The listening list is updated in the UI thread.
     *
     * @param listToUpdate the list to update
     * @param listToListen the list to listen
     * @return the listener that was added to the list to listen, so that it can be removed when needed
     * @param <T> the type of the elements of the lists
     */
    public static <T> ListChangeListener<? super T> bindListInUIThread(ObservableList<T> listToUpdate, ObservableList<T> listToListen) {
        listToUpdate.addAll(listToListen);

        ListChangeListener<? super T> listener = change -> {
            if (Platform.isFxApplicationThread()) {
                while (change.next()) {
                    if (change.wasAdded()) {
                        listToUpdate.addAll(change.getAddedSubList());
                    } else {
                        listToUpdate.removeAll(change.getRemoved());
                    }
                }
                // Change needs to be reset, otherwise calling this function several times
                // with the same listToListen parameter will work for only one of them
                change.reset();
            } else {
                Platform.runLater(() -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            listToUpdate.addAll(change.getAddedSubList());
                        } else {
                            listToUpdate.removeAll(change.getRemoved());
                        }
                    }
                    change.reset();
                });
            }
        };
        listToListen.addListener(listener);

        return listener;
    }
}
