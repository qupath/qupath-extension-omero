package qupath.ext.omero.gui.browser.advancedsearch;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.browser.advancedsearch.cellfactories.LinkCellFactory;
import qupath.ext.omero.gui.browser.advancedsearch.cellfactories.TypeCellFactory;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;

/**
 * Window allowing to perform a search on Omero entities.
 * <p>
 * It displays a table that uses cell factories of the
 * {@link qupath.ext.omero.gui.browser.advancedsearch.cellfactories cell factories} package.
 */
public class AdvancedSearch extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedSearch.class);
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final ResourceBundle resources = Utils.getResources();
    private final ApisHandler apisHandler;
    private final Server server;
    @FXML
    private TextField query;
    @FXML
    private CheckBox name;
    @FXML
    private CheckBox description;
    @FXML
    private GridPane objectsContainer;
    @FXML
    private CheckBox images;
    @FXML
    private CheckBox datasets;
    @FXML
    private CheckBox projects;
    @FXML
    private CheckBox wells;
    @FXML
    private CheckBox plates;
    @FXML
    private CheckBox screens;
    @FXML
    private ComboBox<Owner> owner;
    @FXML
    private ComboBox<Group> group;
    @FXML
    private Button search;
    @FXML
    private Button importImage;
    @FXML
    private TableView<SearchResult> results;
    @FXML
    private TableColumn<SearchResult, SearchResult> typeColumn;
    @FXML
    private TableColumn<SearchResult, String> nameColumn;
    @FXML
    private TableColumn<SearchResult, String> acquiredColumn;
    @FXML
    private TableColumn<SearchResult, String> importedColumn;
    @FXML
    private TableColumn<SearchResult, String> groupColumn;
    @FXML
    private TableColumn<SearchResult, SearchResult> linkColumn;

    /**
     * Creates the advanced search window.
     *
     * @param ownerWindow the stage who should own this window
     * @param apisHandler the apis handler to use when making requests
     * @param server the server to search
     * @throws IOException if an error occurs while creating the window
     */
    public AdvancedSearch(Stage ownerWindow, ApisHandler apisHandler, Server server) throws IOException {
        this.apisHandler = apisHandler;
        this.server = server;

        initUI(ownerWindow);
        setUpListeners();
    }

    @FXML
    private void onResetClicked(ActionEvent ignoredEvent) {
        query.setText("");

        for (Node object: objectsContainer.getChildren()) {
            ((CheckBox) object).setSelected(true);
        }

        owner.getSelectionModel().selectFirst();
        group.getSelectionModel().selectFirst();

        results.getItems().clear();
    }

    @FXML
    private void onSearchClicked(ActionEvent ignoredEvent) {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(30, 30);
        progressIndicator.setMinSize(30, 30);

        search.setGraphic(progressIndicator);
        search.setText(null);
        results.getItems().clear();

        apisHandler.getSearchResults(new SearchQuery(
                query.getText(),
                name.isSelected(),
                description.isSelected(),
                images.isSelected(),
                datasets.isSelected(),
                projects.isSelected(),
                wells.isSelected(),
                plates.isSelected(),
                screens.isSelected(),
                group.getSelectionModel().getSelectedItem(),
                owner.getSelectionModel().getSelectedItem()
        )).handle((searchResults, error) -> {
            Platform.runLater(() -> {
                search.setGraphic(null);
                search.setText(resources.getString("Browser.ServerBrowser.AdvancedSearch.search"));

                if (error == null) {
                    results.getItems().setAll(searchResults);
                } else {
                    logger.error("Error when searching", error);
                    Dialogs.showErrorNotification(
                            resources.getString("Browser.ServerBrowser.AdvancedSearch.search"),
                            MessageFormat.format(
                                    resources.getString("Browser.ServerBrowser.AdvancedSearch.errorOccurred"),
                                    error.getLocalizedMessage()
                            )
                    );
                }
            });
            return null;
        });
    }

    @FXML
    private void onImportButtonClicked(ActionEvent ignoredEvent) {
        importSelectedImages();
    }

    private void initUI(Stage ownerWindow) throws IOException {
        UiUtilities.loadFXML(this, AdvancedSearch.class.getResource("advanced_search.fxml"));

        owner.getItems().setAll(server.getOwners());
        owner.getItems().add(Owner.getAllMembersOwner());
        owner.getSelectionModel().select(server.getConnectedOwner());
        owner.setConverter(new StringConverter<>() {
            @Override
            public String toString(Owner owner) {
                return owner == null ? "" : owner.getFullName();
            }
            @Override
            public Owner fromString(String string) {
                return null;
            }
        });

        group.getItems().setAll(server.getGroups());
        group.getItems().add(Group.getAllGroupsGroup());
        group.getSelectionModel().select(server.getDefaultGroup());
        group.setConverter(new StringConverter<>() {
            @Override
            public String toString(Group object) {
                return object == null ? "" : object.getName();
            }

            @Override
            public Group fromString(String string) {
                return null;
            }
        });

        results.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        typeColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue()));
        nameColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().getName()));
        acquiredColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().getDateAcquired().isPresent() ?
                DATE_FORMAT.format(n.getValue().getDateAcquired().get()) : ""
        ));
        importedColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().getDateImported().isPresent() ?
                DATE_FORMAT.format(n.getValue().getDateImported().get()) : ""
        ));
        groupColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().getGroupName()));
        linkColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue()));

        typeColumn.setCellFactory(n -> new TypeCellFactory(apisHandler));
        linkColumn.setCellFactory(n -> new LinkCellFactory());

        initOwner(ownerWindow);
        show();
    }

    private void setUpListeners() {
        importImage.textProperty().bind(Bindings.createStringBinding(
                () -> results.getSelectionModel().getSelectedItems().size() == 1 ?
                        resources.getString("Browser.ServerBrowser.AdvancedSearch.import") + " " + results.getSelectionModel().getSelectedItems().getFirst().getName() :
                        resources.getString("Browser.ServerBrowser.AdvancedSearch.importObjects"),
                results.getSelectionModel().getSelectedItems()
        ));
        importImage.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());

        results.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                importSelectedImages();
            }
        });

        typeColumn.prefWidthProperty().bind(results.widthProperty().multiply(0.16));
        nameColumn.prefWidthProperty().bind(results.widthProperty().multiply(0.16));
        acquiredColumn.prefWidthProperty().bind(results.widthProperty().multiply(0.16));
        importedColumn.prefWidthProperty().bind(results.widthProperty().multiply(0.16));
        groupColumn.prefWidthProperty().bind(results.widthProperty().multiply(0.16));
        linkColumn.prefWidthProperty().bind(results.widthProperty().multiply(0.16));

        getScene().addEventFilter(
                KeyEvent.KEY_PRESSED,
                keyEvent -> {
                    switch (keyEvent.getCode()) {
                        case ENTER:
                            onSearchClicked(null);
                            break;
                        case ESCAPE:
                            close();
                            break;
                    }
                }
        );
    }

    private void importSelectedImages() {
        UiUtilities.openImages(results.getSelectionModel().getSelectedItems().stream()
                .map(SearchResult::getLink)
                .toList()
        );
    }
}
