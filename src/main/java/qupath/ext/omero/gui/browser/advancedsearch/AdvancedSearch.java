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
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.entities.search.SearchQuery;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.core.entities.search.SearchResultWithParentInfo;
import qupath.ext.omero.gui.ImageOpener;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.browser.advancedsearch.cellfactories.EntityCell;
import qupath.ext.omero.gui.browser.advancedsearch.cellfactories.LinkCell;
import qupath.ext.omero.gui.browser.advancedsearch.cellfactories.TextCell;
import qupath.ext.omero.gui.browser.advancedsearch.cellfactories.TypeCell;
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
    private static final int PROGRESS_INDICATOR_SIZE = 30;
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
    private ComboBox<Group> group;
    @FXML
    private ComboBox<Owner> owner;
    @FXML
    private Button search;
    @FXML
    private Button importImage;
    @FXML
    private TableView<SearchResultWithParentInfo> results;
    @FXML
    private TableColumn<SearchResultWithParentInfo, SearchResult> typeColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, String> nameColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, String> acquiredColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, String> importedColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, String> groupColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, SearchResult> linkColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, Project> projectColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, Dataset> datasetColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, Screen> screenColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, Plate> plateColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, PlateAcquisition> plateAcquisitionColumn;
    @FXML
    private TableColumn<SearchResultWithParentInfo, Well> wellColumn;

    /**
     * Creates the advanced search window.
     *
     * @param ownerWindow the stage who should own this window
     * @param apisHandler the apis handler to use when making requests
     * @param server the server to search
     * @throws IOException if an error occurs while creating the window
     */
    public AdvancedSearch(Stage ownerWindow, ApisHandler apisHandler, Server server) throws IOException {
        logger.debug("Creating advanced search window for {}", server);
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

        group.getSelectionModel().select(server.getDefaultGroup());
        owner.getSelectionModel().select(server.getConnectedOwner());

        results.getItems().clear();
    }

    @FXML
    private void onSearchClicked(ActionEvent ignoredEvent) {
        logger.debug("Search started");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(PROGRESS_INDICATOR_SIZE, PROGRESS_INDICATOR_SIZE);

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
        )).whenComplete((searchResults, error) -> Platform.runLater(() -> {
            search.setGraphic(null);
            search.setText(resources.getString("Browser.ServerBrowser.AdvancedSearch.search"));

            if (error == null) {
                logger.debug("Got results {} from search", searchResults);
                results.getItems().setAll(searchResults);
            } else {
                logger.error("Error when searching", error);
                Dialogs.showErrorMessage(
                        resources.getString("Browser.ServerBrowser.AdvancedSearch.search"),
                        MessageFormat.format(
                                resources.getString("Browser.ServerBrowser.AdvancedSearch.errorOccurred"),
                                error.getLocalizedMessage()
                        )
                );
            }
        }));
    }

    @FXML
    private void onImportButtonClicked(ActionEvent ignoredEvent) {
        logger.debug("Import button clicked. Importing selected images");
        importSelectedImages();
    }

    private void initUI(Stage ownerWindow) throws IOException {
        UiUtilities.loadFXML(this, AdvancedSearch.class.getResource("advanced_search.fxml"));

        group.getItems().setAll(Group.getAllGroupsGroup());
        group.getItems().addAll(server.getGroups());
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

        owner.getItems().setAll(Owner.getAllMembersOwner());
        owner.getItems().addAll(group.getSelectionModel().getSelectedItem().getOwners());
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

        results.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        typeColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().searchResult()));
        nameColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().searchResult().name()));
        acquiredColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(
                n.getValue().searchResult().dateAcquired() == null ? "" : DATE_FORMAT.format(n.getValue().searchResult().dateAcquired())
        ));
        importedColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(
                n.getValue().searchResult().dateImported() == null ? "" : DATE_FORMAT.format(n.getValue().searchResult().dateImported())
        ));
        groupColumn.setCellValueFactory(n -> new ReadOnlyStringWrapper(n.getValue().searchResult().group()));
        linkColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().searchResult()));
        projectColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().parentProject()));
        datasetColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().parentDataset()));
        screenColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().parentScreen()));
        plateColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().parentPlate()));
        plateAcquisitionColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().parentPlateAcquisition()));
        wellColumn.setCellValueFactory(n -> new ReadOnlyObjectWrapper<>(n.getValue().parentWell()));

        typeColumn.setCellFactory(n -> new TypeCell(apisHandler));
        nameColumn.setCellFactory(n -> new TextCell());
        acquiredColumn.setCellFactory(n -> new TextCell());
        importedColumn.setCellFactory(n -> new TextCell());
        groupColumn.setCellFactory(n -> new TextCell());
        linkColumn.setCellFactory(n -> new LinkCell());
        projectColumn.setCellFactory(n -> new EntityCell<>());
        datasetColumn.setCellFactory(n -> new EntityCell<>());
        screenColumn.setCellFactory(n -> new EntityCell<>());
        plateColumn.setCellFactory(n -> new EntityCell<>());
        plateAcquisitionColumn.setCellFactory(n -> new EntityCell<>());
        wellColumn.setCellFactory(n -> new EntityCell<>());

        initOwner(ownerWindow);
        show();
    }

    private void setUpListeners() {
        group.getSelectionModel().selectedItemProperty().addListener((p, o, n) -> {
            owner.getItems().setAll(Owner.getAllMembersOwner());

            if (n == null) {
                owner.getSelectionModel().selectFirst();
            } else {
                if (n.equals(Group.getAllGroupsGroup())) {
                    owner.getItems().addAll(server.getOwners());
                    owner.getSelectionModel().select(Owner.getAllMembersOwner());
                } else {
                    owner.getItems().addAll(n.getOwners());

                    if (n.getOwners().contains(server.getConnectedOwner())) {
                        owner.getSelectionModel().select(server.getConnectedOwner());
                    } else {
                        owner.getSelectionModel().selectFirst();
                    }
                }
            }
        });

        importImage.textProperty().bind(Bindings.createStringBinding(
                () -> results.getSelectionModel().getSelectedItems().size() == 1 ?
                        String.format(
                                "%s %s",
                                resources.getString("Browser.ServerBrowser.AdvancedSearch.import"),
                                results.getSelectionModel().getSelectedItems().getFirst().searchResult().name()
                        ) :
                        resources.getString("Browser.ServerBrowser.AdvancedSearch.importObjects"),
                results.getSelectionModel().getSelectedItems()
        ));
        importImage.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());

        results.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                logger.debug("Double click on results detected. Importing selected images");
                importSelectedImages();
            }
        });

        getScene().addEventFilter(
                KeyEvent.KEY_PRESSED,
                keyEvent -> {
                    switch (keyEvent.getCode()) {
                        case ENTER:
                            logger.debug("Enter key pressed. Starting search");
                            onSearchClicked(null);
                            break;
                        case ESCAPE:
                            logger.debug("Escape key pressed. Closing advanced search window");
                            close();
                            break;
                    }
                }
        );
    }

    private void importSelectedImages() {
        ImageOpener.openImageFromUris(
                results.getSelectionModel().getSelectedItems().stream()
                        .map(SearchResultWithParentInfo::searchResult)
                        .map(SearchResult::link)
                        .toList(),
                apisHandler
        );
    }
}
