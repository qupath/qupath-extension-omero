package qupath.ext.omero.gui.browser.advancedsearch.cellfactories;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.lib.gui.QuPathGUI;

/**
 * Cell factory that displays a button that opens the link of a search result in a browser.
 */
public class LinkCellFactory extends TableCell<SearchResult, SearchResult> {

    private final Hyperlink hyperlink;

    public LinkCellFactory() {
        hyperlink = new Hyperlink();
        hyperlink.setTextOverrun(OverrunStyle.LEADING_WORD_ELLIPSIS);
    }

    @Override
    protected void updateItem(SearchResult item, boolean empty) {
        super.updateItem(item, empty);

        setGraphic(null);

        if (item != null && !empty) {
            hyperlink.setText(item.link());
            hyperlink.setOnAction(e -> QuPathGUI.openInBrowser(item.link()));
            setGraphic(hyperlink);
        }
    }
}
