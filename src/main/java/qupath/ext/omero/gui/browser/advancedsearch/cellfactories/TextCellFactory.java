package qupath.ext.omero.gui.browser.advancedsearch.cellfactories;

import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import qupath.ext.omero.core.entities.search.SearchResult;

/**
 * Cell factory that displays a text in the cell and in an associated tooltip.
 */
public class TextCellFactory extends TableCell<SearchResult, String> {

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        setText(null);
        setTooltip(null);

        if (item != null && !empty) {
            setText(item);
            setTooltip(new Tooltip(item));
        }
    }
}
