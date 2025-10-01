package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.entities.repositoryentities2.Server;
import qupath.ext.omero.gui.ImageOpener;
import qupath.ext.omero.gui.browser.hierarchy.HierarchyCell;
import qupath.ext.omero.gui.browser.hierarchy.HierarchyItem2;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.fx.controls.PredicateTextField;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.ext.omero.gui.browser.advancedsearch.AdvancedSearch;
import qupath.ext.omero.gui.browser.advancedinformation.AdvancedInformation;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.Image;
import qupath.ext.omero.core.entities.repositoryentities2.OrphanedFolder;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.pixelapis.PixelApi;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Window allowing the user to browse an OMERO server, get information about OMERO entities
 * and open OMERO images.
 * <p>
 * It displays a hierarchy of OMERO entities using classes of {@link qupath.ext.omero.gui.browser.hierarchy hierarchy}.
 * <p>
 * It can launch a window showing details on an OMERO entity, described in {@link qupath.ext.omero.gui.browser.advancedinformation advanced_information}.
 * <p>
 * It can launch a window that performs a search on OMERO entities, described in {@link qupath.ext.omero.gui.browser.advancedsearch advanced_search}.
 * <p>
 * It uses a {@link BrowserModel} to update its state.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
class Browser extends Stage implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Browser.class);
    private static final float DESCRIPTION_ATTRIBUTE_PROPORTION = 0.25f;
    private static final ResourceBundle resources = Utils.getResources();
    private final List<HierarchyCell> hierarchyCells = new ArrayList<>();
    private final Client client;
    private final Server server;
    private final Consumer<Client> onClientCreated;
    private final BrowserModel browserModel;
    @FXML
    private Label serverHost;
    @FXML
    private Label username;
    @FXML
    private HBox usernameContainer;
    @FXML
    private Button login;
    @FXML
    private Button logout;
    @FXML
    private Label numberOpenImages;
    @FXML
    private Label rawPixelAccess;
    @FXML
    private ComboBox<PixelApi> pixelAPI;
    @FXML
    private Label loadingObjects;
    @FXML
    private Label loadingOrphaned;
    @FXML
    private Label loadingThumbnail;
    @FXML
    private ChoiceBox<Group> group;
    @FXML
    private ChoiceBox<Owner> owner;
    @FXML
    private TreeView<RepositoryEntity> hierarchy;
    @FXML
    private MenuItem moreInfo;
    @FXML
    private MenuItem openBrowser;
    @FXML
    private MenuItem copyToClipboard;
    @FXML
    private MenuItem collapseAllItems;
    @FXML
    private HBox filterContainer;
    @FXML
    private Button importImage;
    @FXML
    private Canvas canvas;
    @FXML
    private TableView<Integer> description;
    @FXML
    private TableColumn<Integer, String> attributeColumn;
    @FXML
    private TableColumn<Integer, String> valueColumn;
    private AdvancedSearch advancedSearch = null;
    private Settings settings = null;

    /**
     * Create and show the browser window.
     *
     * @param owner the owner of this window
     * @param client the client which will be used by this browser to retrieve data from the corresponding OMERO server
     * @param server the server of the client
     * @param onClientCreated a function that will be called if this browser creates a new client. It will be called from
     *                        the JavaFX Application Thread
     * @throws IOException if an error occurs while creating the browser
     */
    public Browser(Stage owner, Client client, Server server, Consumer<Client> onClientCreated) throws IOException {
        logger.debug("Creating browser for {}", client);

        this.client = client;
        this.server = server;
        this.onClientCreated = onClientCreated;
        this.browserModel = new BrowserModel(client, server);

        UiUtilities.loadFXML(this, Browser.class.getResource("browser.fxml"));

        initUI(owner);
        setUpListeners();
    }

    @Override
    public void close() {
        if (hierarchy.getRoot() instanceof HierarchyItem2 hierarchyItem) {
            hierarchyItem.close();
        }

        for (HierarchyCell hierarchyCell: hierarchyCells) {
            hierarchyCell.close();
        }

        if (settings != null) {
            settings.close();
        }

        browserModel.close();
    }

    @FXML
    private void onLoginClicked(ActionEvent ignoredEvent) {
        if (!client.canBeClosed()) {
            Dialogs.showMessageDialog(
                    resources.getString("Browser.ServerBrowser.login"),
                    resources.getString("Browser.ServerBrowser.closeImages")
            );
            return;
        }

        logger.debug("Login button clicked. Creating login form with no credentials");
        try {
            new LoginForm(
                    this,
                    client.getApisHandler().getWebServerURI(),
                    null,
                    client -> Platform.runLater(() -> {
                        logger.debug("Client {} created from login button of browser", client);
                        onClientCreated.accept(client);
                    })
            ).show();
        } catch (IOException e) {
            logger.error("Error while creating the login server form", e);
        }
    }

    @FXML
    private void onLogoutClicked(ActionEvent ignoredEvent) {
        if (!client.canBeClosed()) {
            Dialogs.showMessageDialog(
                    resources.getString("Browser.ServerBrowser.logout"),
                    resources.getString("Browser.ServerBrowser.closeImages")
            );
            return;
        }

        logger.debug("Logout button clicked. Creating login form with public user credentials");
        try {
            new LoginForm(
                    this,
                    client.getApisHandler().getWebServerURI(),
                    new Credentials(),
                    client -> Platform.runLater(() -> {
                        logger.debug("Client {} created from logout button of browser", client);
                        onClientCreated.accept(client);
                    })
            ).show();
        } catch (IOException e) {
            logger.error("Error while creating the login server form", e);
        }
    }

    @FXML
    private void onSettingsClicked(ActionEvent ignoredEvent) {
        if (settings == null) {
            logger.debug("Settings window not created. Creating and showing it");
            try {
                settings = new Settings(this, client);
            } catch (IOException e) {
                logger.error("Error while creating the settings window", e);
            }
        } else {
            logger.debug("Settings window created. Showing it");

            if (settings.isShowing()) {
                settings.requestFocus();
            } else {
                settings.resetEntries();
                settings.show();
            }
        }
    }

    @FXML
    private void onImagesTreeClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            var selectedItem = hierarchy.getSelectionModel().getSelectedItem();
            RepositoryEntity selectedObject = selectedItem == null ? null : selectedItem.getValue();

            if (selectedObject instanceof Image image && image.isSupported(client.getSelectedPixelApi().getValue())) {
                logger.debug("Double click on tree detected while {} is selected and supported. Opening it", image);
                ImageOpener.openImageFromUris(List.of(client.getApisHandler().getEntityUri(image)), client.getApisHandler());
            }
        }
    }

    @FXML
    private void onMoreInformationMenuClicked(ActionEvent ignoredEvent) {
        var selectedItem = hierarchy.getSelectionModel().getSelectedItem();

        if (selectedItem != null && selectedItem.getValue() instanceof ServerEntity serverEntity) {
            logger.debug("More info menu clicked on {}. Fetching annotations of it", serverEntity);

            client.getApisHandler().getAnnotations(serverEntity.getId(), serverEntity.getClass()).whenComplete((annotations, error) -> Platform.runLater(() -> {
                if (annotations == null) {
                    logger.error("Error while retrieving annotations of {}. Cannot open advanced information window", serverEntity, error);

                    Dialogs.showErrorMessage(
                            resources.getString("Browser.ServerBrowser.cantDisplayInformation"),
                            MessageFormat.format(
                                    resources.getString("Browser.ServerBrowser.errorWhenFetchingInformation"),
                                    serverEntity.getLabel(),
                                    error.getLocalizedMessage()
                            )
                    );
                    return;
                }

                logger.debug("Got annotations {} for {}. Opening advanced information window", annotations, serverEntity);
                try {
                    new AdvancedInformation(this, serverEntity, annotations);
                } catch (IOException e) {
                    logger.error("Error while creating the advanced information window", e);
                }
            }));
        }
    }

    @FXML
    private void onOpenInBrowserMenuClicked(ActionEvent ignoredEvent) {
        var selectedItem = hierarchy.getSelectionModel().getSelectedItem();

        if (selectedItem != null && selectedItem.getValue() instanceof ServerEntity serverEntity) {
            logger.debug("Open in browser menu clicked on {}. Opening it", serverEntity);
            QuPathGUI.openInBrowser(client.getApisHandler().getEntityUri(serverEntity));
        }
    }

    @FXML
    private void onCopyToClipboardMenuClicked(ActionEvent ignoredEvent) {
        logger.debug("Copy to clipboard menu clicked on {}. Getting URIs of them", hierarchy.getSelectionModel().getSelectedItems());

        List<String> URIs = hierarchy.getSelectionModel().getSelectedItems().stream()
                .map(item -> {
                    if (item.getValue() instanceof ServerEntity serverEntity) {
                        return client.getApisHandler().getEntityUri(serverEntity);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (URIs.isEmpty()) {
            logger.debug("No URI found. Nothing will be copied to clipboard");
            Dialogs.showWarningNotification(
                    resources.getString("Browser.ServerBrowser.copyURIToClipboard"),
                    resources.getString("Browser.ServerBrowser.itemNeedsSelected")
            );
            return;
        }

        ClipboardContent content = new ClipboardContent();
        if (URIs.size() == 1) {
            content.putString(URIs.getFirst());
        } else {
            content.putString("[" + String.join(", ", URIs) + "]");
        }
        Clipboard.getSystemClipboard().setContent(content);
        logger.debug("{} copied to clipboard", content);

        Dialogs.showInfoNotification(
                resources.getString("Browser.ServerBrowser.copyURIToClipboard"),
                resources.getString("Browser.ServerBrowser.uriSuccessfullyCopied")
        );
    }

    @FXML
    private void onCollapseAllItemsMenuClicked(ActionEvent ignoredEvent) {
        collapseTreeView(hierarchy.getRoot());
    }

    @FXML
    private void onAdvancedSearchClicked(ActionEvent ignoredEvent) {
        if (advancedSearch == null) {
            logger.debug("Advanced search button clicked but advanced search window doesn't exist. Creating and showing it");
            try {
                advancedSearch = new AdvancedSearch(this, client.getApisHandler(), server);
            } catch (IOException e) {
                logger.error("Error while creating the settings window", e);
            }
        } else {
            logger.debug("Advanced search button clicked and advanced search window already exists. Showing it");
            advancedSearch.show();
            advancedSearch.requestFocus();
        }
    }

    @FXML
    private void onImportButtonClicked(ActionEvent ignoredEvent) {
        logger.debug("Import button clicked. Opening server entities in selected items {}", hierarchy.getSelectionModel().getSelectedItems());

        ImageOpener.openImageFromUris(
                hierarchy.getSelectionModel().getSelectedItems().stream()
                        .map(TreeItem::getValue)
                        .map(repositoryEntity -> {
                            if (repositoryEntity instanceof ServerEntity serverEntity) {
                                return serverEntity;
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .map(serverEntity ->
                                client.getApisHandler().getEntityUri(serverEntity)
                        )
                        .toList(),
                client.getApisHandler()
        );
    }

    private void initUI(Stage ownerWindow) {
        serverHost.setText(client.getApisHandler().getWebServerURI().toString());

        username.setText(switch (client.getApisHandler().getCredentials().userType()) {
            case PUBLIC_USER -> resources.getString("Browser.ServerBrowser.publicUser");
            case REGULAR_USER -> client.getApisHandler().getCredentials().username();
        });

        usernameContainer.getChildren().remove(switch (client.getApisHandler().getCredentials().userType()) {
            case PUBLIC_USER -> logout;
            case REGULAR_USER -> login;
        });

        pixelAPI.setItems(browserModel.getAvailablePixelAPIs());
        pixelAPI.setConverter(new StringConverter<>() {
            @Override
            public String toString(PixelApi pixelAPI) {
                return pixelAPI == null ? "" : pixelAPI.getName();
            }
            @Override
            public PixelApi fromString(String string) {
                return null;
            }
        });
        pixelAPI.getSelectionModel().select(browserModel.getSelectedPixelAPI().getValue());

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
            public String toString(Owner object) {
                return object == null ? "" : object.getFullName();
            }

            @Override
            public Owner fromString(String string) {
                return null;
            }
        });

        PredicateTextField<RepositoryEntity> predicateTextField = new PredicateTextField<>(RepositoryEntity::getLabel);
        predicateTextField.setPromptText(resources.getString("Browser.ServerBrowser.filterNames"));
        predicateTextField.setIgnoreCase(true);
        HBox.setHgrow(predicateTextField, Priority.ALWAYS);

        hierarchy.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        hierarchy.setRoot(new HierarchyItem2(
                server,
                browserModel.getSelectedOwner(),
                browserModel.getSelectedGroup(),
                predicateTextField.predicateProperty()
        ));
        hierarchy.setCellFactory(n -> {
            HierarchyCell hierarchyCell = new HierarchyCell(client);
            hierarchyCells.add(hierarchyCell);
            return hierarchyCell;
        });

        filterContainer.getChildren().addFirst(predicateTextField);

        attributeColumn.setCellValueFactory(cellData -> {
            var selectedItems = hierarchy.getSelectionModel().getSelectedItems();
            if (cellData != null && selectedItems.size() == 1 && selectedItems.getFirst().getValue() instanceof ServerEntity serverEntity) {
                return new ReadOnlyObjectWrapper<>(serverEntity.getAttributeName(cellData.getValue()));
            } else {
                return new ReadOnlyObjectWrapper<>("");
            }
        });

        valueColumn.setCellValueFactory(cellData -> {
            var selectedItems = hierarchy.getSelectionModel().getSelectedItems();
            if (cellData != null && selectedItems.size() == 1 && selectedItems.getFirst().getValue() instanceof ServerEntity serverEntity) {
                return new ReadOnlyObjectWrapper<>(serverEntity.getAttributeValue(cellData.getValue()));
            } else {
                return new ReadOnlyObjectWrapper<>("");
            }
        });
        valueColumn.setCellFactory(n -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        initOwner(ownerWindow);
        show();
    }

    private void setUpListeners() {
        numberOpenImages.textProperty().bind(Bindings.size(browserModel.getOpenedImagesURIs()).asString());

        BooleanBinding rawPixelBindings = Bindings.createBooleanBinding(
                () -> browserModel.getSelectedPixelAPI().getValue() != null && browserModel.getSelectedPixelAPI().getValue().canAccessRawPixels(),
                browserModel.getSelectedPixelAPI()
        );
        rawPixelAccess.textProperty().bind(Bindings
                .when(rawPixelBindings)
                .then(resources.getString("Browser.ServerBrowser.accessRawPixels"))
                .otherwise(resources.getString("Browser.ServerBrowser.noAccessRawPixels"))
        );
        rawPixelAccess.graphicProperty().bind(Bindings
                .when(rawPixelBindings)
                .then(UiUtilities.createStateNode(true))
                .otherwise(UiUtilities.createStateNode(false)));

        browserModel.getSelectedPixelAPI().addListener((p, o, n) -> pixelAPI.getSelectionModel().select(n));
        pixelAPI.valueProperty().addListener((p, o, n) -> {
            if (pixelAPI.getValue() != null) {
                client.setSelectedPixelAPI(pixelAPI.getValue());
            }
        });

        loadingObjects.visibleProperty().bind(Bindings.notEqual(browserModel.getNumberOfEntitiesLoading(), 0));
        loadingObjects.managedProperty().bind(loadingObjects.visibleProperty());

        loadingOrphaned.textProperty().bind(Bindings.concat(
                resources.getString("Browser.ServerBrowser.loadingOrphanedImages"),
                " (",
                browserModel.getNumberOfOrphanedImagesLoaded(),
                "/",
                browserModel.getNumberOfOrphanedImages(),
                ")"
        ));
        loadingOrphaned.visibleProperty().bind(browserModel.areOrphanedImagesLoading());
        loadingOrphaned.managedProperty().bind(loadingOrphaned.visibleProperty());

        loadingThumbnail.visibleProperty().bind(Bindings.notEqual(browserModel.getNumberOfThumbnailsLoading(), 0));
        loadingThumbnail.managedProperty().bind(loadingThumbnail.visibleProperty());

        browserModel.getSelectedGroup().addListener((p, o, n) -> {
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
        browserModel.getSelectedGroup().bind(group.getSelectionModel().selectedItemProperty());
        browserModel.getSelectedOwner().bind(owner.getSelectionModel().selectedItemProperty());

        hierarchy.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
            updateCanvas();
            updateDescription();
            updateImportButton();
        });

        BooleanBinding isSelectedItemOrphanedFolderBinding = Bindings.createBooleanBinding(
                () -> hierarchy.getSelectionModel().getSelectedItem() != null &&
                        hierarchy.getSelectionModel().getSelectedItem().getValue() instanceof OrphanedFolder,
                hierarchy.getSelectionModel().selectedItemProperty()
        );
        moreInfo.disableProperty().bind(isSelectedItemOrphanedFolderBinding);
        openBrowser.disableProperty().bind(isSelectedItemOrphanedFolderBinding);
        copyToClipboard.disableProperty().bind(isSelectedItemOrphanedFolderBinding);

        attributeColumn.prefWidthProperty().bind(description.widthProperty().multiply(DESCRIPTION_ATTRIBUTE_PROPORTION));
        valueColumn.prefWidthProperty().bind(description.widthProperty().multiply(1 - DESCRIPTION_ATTRIBUTE_PROPORTION));

        description.placeholderProperty().bind(Bindings.when(Bindings.isEmpty(hierarchy.getSelectionModel().getSelectedItems()))
                .then(new Label(resources.getString("Browser.ServerBrowser.noElementSelected")))
                .otherwise(new Label(resources.getString("Browser.ServerBrowser.multipleElementsSelected")))
        );

        canvas.managedProperty().bind(Bindings.createBooleanBinding(
                () -> hierarchy.getSelectionModel().getSelectedItems().size() == 1 &&
                        hierarchy.getSelectionModel().getSelectedItems().getFirst().getValue() instanceof Image,
                hierarchy.getSelectionModel().getSelectedItems()
        ));

        browserModel.getSelectedPixelAPI().addListener(change -> updateImportButton());
    }

    private static void collapseTreeView(TreeItem<RepositoryEntity> item){
        if (item != null) {
            for (var child : item.getChildren()) {
                child.setExpanded(false);
            }
        }
    }

    private void updateCanvas() {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        var selectedItems = hierarchy.getSelectionModel().getSelectedItems();
        if (selectedItems.size() == 1 && selectedItems.getFirst() != null && selectedItems.getFirst().getValue() instanceof Image image) {
            logger.trace("One image {} is selected. Fetching its thumbnail", image);

            client.getApisHandler().getThumbnail(image.getId()).whenComplete((thumbnail, error) -> Platform.runLater(() ->{
                if (thumbnail == null) {
                    logger.error("Error when retrieving thumbnail of {}. Cannot update canvas", image, error);
                    return;
                }

                logger.trace("Got thumbnail {} for {}. Updating canvas with it", thumbnail, image);
                UiUtilities.paintBufferedImageOnCanvas(thumbnail, canvas);
            }));
        }
    }

    private void updateDescription() {
        description.getItems().clear();

        var selectedItems = hierarchy.getSelectionModel().getSelectedItems();
        if (selectedItems.size() == 1 && selectedItems.getFirst() != null && selectedItems.getFirst().getValue() instanceof ServerEntity serverEntity) {
            logger.trace("One server entity {} selected. Updating description", serverEntity);
            description.getItems().setAll(
                    IntStream.rangeClosed(0, serverEntity.getNumberOfAttributes()).boxed().collect(Collectors.toList())
            );
        } else {
            logger.trace("Zero or more than one server entity selected. Clearing description");
            description.getItems().clear();
        }
    }

    private void updateImportButton() {
        var importableEntities = hierarchy.getSelectionModel().getSelectedItems().stream()
                .map(item -> item == null ? null : item.getValue())
                .filter(Objects::nonNull)
                .filter(repositoryEntity -> {
                    if (repositoryEntity instanceof Image image) {
                        return image.isSupported(client.getSelectedPixelApi().getValue());
                    } else {
                        return repositoryEntity instanceof ServerEntity;
                    }
                })
                .toList();

        importImage.setDisable(importableEntities.isEmpty());

        if (importableEntities.isEmpty()) {
            logger.trace("No importable entity selected. Disabling import button");
            importImage.setText(resources.getString("Browser.ServerBrowser.cantImportSelectedToQuPath"));
        } else if (importableEntities.size() == 1) {
            logger.trace("One importable entity selected {}. Enabling import button", importableEntities.getFirst());
            importImage.setText(MessageFormat.format(
                    resources.getString("Browser.ServerBrowser.importToQuPath"),
                    importableEntities.getFirst().getLabel()
            ));
        } else {
            logger.trace("Several importable entity selected {}. Enabling import button", importableEntities);
            importImage.setText(resources.getString("Browser.ServerBrowser.importSelectedToQuPath"));
        }
    }
}
