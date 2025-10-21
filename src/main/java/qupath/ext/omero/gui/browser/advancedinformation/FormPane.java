package qupath.ext.omero.gui.browser.advancedinformation;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import qupath.ext.omero.gui.UiUtils;
import qupath.fx.utils.GridPaneUtils;

import java.io.IOException;

/**
 * Pane showing a two-column table. It is suited to display key-value pairs.
 */
class FormPane extends TitledPane {

    @FXML
    private GridPane content;

    /**
     * Creates a new FormPane.
     * @throws IOException if an error occurs while creating the pane
     */
    public FormPane() throws IOException {
        UiUtils.loadFXML(this, FormPane.class.getResource("form_pane.fxml"));
    }

    /**
     * Creates a new FormPane.
     *
     * @param title the title the pane should have
     * @throws IOException if an error occurs while creating the pane
     */
    public FormPane(String title) throws IOException {
        this();

        setTitle(title);
    }

    /**
     * Set the text displayed on top of the pane
     *
     * @param title the text to display
     */
    public void setTitle(String title) {
        setText(title);
    }

    /**
     * Add a row to the table.
     *
     * @param key the text that should appear in the first column
     * @param value the text that should appear in the second column
     * @param tooltip the text that should appear when the user hovers over this row
     */
    public void addRow(String key, String value, String tooltip) {
        if (content.getRowCount() > 0) {
            content.add(new Separator(), 0, content.getRowCount(), content.getColumnCount(), 1);
        }

        GridPaneUtils.addGridRow(content, content.getRowCount(), 0, tooltip, new Label(key), UiUtils.createSelectableLabel(value));
    }

    /**
     * Add a row to the table.
     *
     * @param key the text that should appear in the first column
     * @param value the text that should appear in the second column
     */
    public void addRow(String key, String value) {
        addRow(key, value, key);
    }
}
