package qupath.ext.omero.gui.browser.advancedsearch.cellfactories;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.entities.search.SearchResultWithParentInfo;
import qupath.lib.gui.QuPathGUI;

/**
 * Cell that displays a button that opens the link of a search result in a browser.
 */
public class LinkCell extends TableCell<SearchResultWithParentInfo, SearchResult> {

    private final Hyperlink hyperlink = new Hyperlink();

    /**
     * Create the cell.
     */
    public LinkCell() {
        hyperlink.setTextOverrun(OverrunStyle.LEADING_WORD_ELLIPSIS);
    }

    @Override
    protected void updateItem(SearchResult item, boolean empty) {
        super.updateItem(item, empty);

        setGraphic(null);

        if (item != null && !empty) {
            hyperlink.setText(item.link());
            hyperlink.setTooltip(new Tooltip(item.link()));
            hyperlink.setOnAction(e -> QuPathGUI.openInBrowser(item.link()));
            setGraphic(hyperlink);
        }
    }
}
