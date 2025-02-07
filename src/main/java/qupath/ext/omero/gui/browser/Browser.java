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
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.gui.browser.hierarchy.HierarchyCellFactory;
import qupath.ext.omero.gui.browser.hierarchy.HierarchyItem;
import qupath.ext.omero.gui.browser.settings.Settings;
import qupath.ext.omero.gui.login.LoginForm;
import qupath.fx.controls.PredicateTextField;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.ext.omero.gui.browser.advancedsearch.AdvancedSearch;
import qupath.ext.omero.gui.browser.advancedinformation.AdvancedInformation;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.pixelapis.PixelApi;

import java.io.IOException;
import java.text.MessageFormat;
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
 */
class Browser extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(Browser.class);
    private static final float DESCRIPTION_ATTRIBUTE_PROPORTION = 0.25f;
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final Client client;
    private final Server server;
    private final Consumer<Client> openClientBrowser;
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
     * Create the browser window.
     *
     * @param owner the owner of this window
     * @param client the client which will be used by this browser to retrieve data from the corresponding OMERO server
     * @param server the server of the client
     * @param openClientBrowser a function that will be called to request opening the browser of a client
     * @throws IOException if an error occurs while creating the browser
     */
    public Browser(Stage owner, Client client, Server server, Consumer<Client> openClientBrowser) throws IOException {
        this.client = client;
        this.server = server;
        this.openClientBrowser = openClientBrowser;
        this.browserModel = new BrowserModel(client, server);

        UiUtilities.loadFXML(this, Browser.class.getResource("browser.fxml"));

        initUI(owner);
        setUpListeners();
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

        try {
            new LoginForm(
                    this,
                    client.getApisHandler().getWebServerURI(),
                    null,
                    client -> Platform.runLater(() -> openClientBrowser.accept(client))
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

        try {
            new LoginForm(
                    this,
                    client.getApisHandler().getWebServerURI(),
                    new Credentials(),
                    client -> Platform.runLater(() -> openClientBrowser.accept(client))
            ).show();
        } catch (IOException e) {
            logger.error("Error while creating the login server form", e);
        }
    }

    @FXML
    private void onSettingsClicked(ActionEvent ignoredEvent) {
        if (settings == null) {
            try {
                settings = new Settings(this, client);
            } catch (IOException e) {
                logger.error("Error while creating the settings window", e);
            }
        } else {
            settings.show();
            settings.requestFocus();
        }
    }

    @FXML
    private void onImagesTreeClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            var selectedItem = hierarchy.getSelectionModel().getSelectedItem();
            RepositoryEntity selectedObject = selectedItem == null ? null : selectedItem.getValue();

            if (selectedObject instanceof Image image && image.isSupported().get()) {
                UiUtilities.openImages(client.getApisHandler().getItemURI(image));
            }
        }
    }

    @FXML
    private void onMoreInformationMenuClicked(ActionEvent ignoredEvent) {
        var selectedItem = hierarchy.getSelectionModel().getSelectedItem();

        if (selectedItem != null && selectedItem.getValue() instanceof ServerEntity serverEntity) {
            client.getApisHandler().getAnnotations(serverEntity.getId(), serverEntity.getClass())
                    .exceptionally(error -> {
                        logger.error("Error while retrieving annotations", error);

                        Platform.runLater(() -> Dialogs.showErrorMessage(
                                resources.getString("Browser.ServerBrowser.cantDisplayInformation"),
                                MessageFormat.format(
                                        resources.getString("Browser.ServerBrowser.errorWhenFetchingInformation"),
                                        serverEntity.getLabel(),
                                        error.getLocalizedMessage()
                                )
                        ));

                        return null;
                    }).thenAccept(annotations -> Platform.runLater(() -> {
                        if (annotations != null) {
                            try {
                                new AdvancedInformation(this, serverEntity, annotations);
                            } catch (IOException e) {
                                logger.error("Error while creating the advanced information window", e);
                            }
                        }
                    }));
        }
    }

    @FXML
    private void onOpenInBrowserMenuClicked(ActionEvent ignoredEvent) {
        var selectedItem = hierarchy.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() instanceof ServerEntity serverEntity) {
            QuPathGUI.openInBrowser(client.getApisHandler().getItemURI(serverEntity));
        }
    }

    @FXML
    private void onCopyToClipboardMenuClicked(ActionEvent ignoredEvent) {
        List<String> URIs = hierarchy.getSelectionModel().getSelectedItems().stream()
                .map(item -> {
                    if (item.getValue() instanceof ServerEntity serverEntity) {
                        return client.getApisHandler().getItemURI(serverEntity);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (!URIs.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            if (URIs.size() == 1) {
                content.putString(URIs.getFirst());
            } else {
                content.putString("[" + String.join(", ", URIs) + "]");
            }
            Clipboard.getSystemClipboard().setContent(content);

            Dialogs.showInfoNotification(
                    resources.getString("Browser.ServerBrowser.copyURIToClipboard"),
                    resources.getString("Browser.ServerBrowser.uriSuccessfullyCopied")
            );
        } else {
            Dialogs.showWarningNotification(
                    resources.getString("Browser.ServerBrowser.copyURIToClipboard"),
                    resources.getString("Browser.ServerBrowser.itemNeedsSelected")
            );
        }
    }

    @FXML
    private void onCollapseAllItemsMenuClicked(ActionEvent ignoredEvent) {
        collapseTreeView(hierarchy.getRoot());
    }

    @FXML
    private void onAdvancedClicked(ActionEvent ignoredEvent) {
        if (advancedSearch == null) {
            try {
                advancedSearch = new AdvancedSearch(this, client.getApisHandler(), server);
            } catch (IOException e) {
                logger.error("Error while creating the settings window", e);
            }
        } else {
            advancedSearch.show();
            advancedSearch.requestFocus();
        }
    }

    @FXML
    private void onImportButtonClicked(ActionEvent ignoredEvent) {
        UiUtilities.openImages(
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
                                client.getApisHandler().getItemURI(serverEntity)
                        )
                        .toArray(String[]::new)
        );
    }

    private void initUI(Stage ownerWindow) {
        serverHost.setText(client.getApisHandler().getWebServerURI().getHost());

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
        pixelAPI.getSelectionModel().select(browserModel.getSelectedPixelAPI().get());

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
        hierarchy.setRoot(new HierarchyItem(
                server,
                browserModel.getSelectedOwner(),
                browserModel.getSelectedGroup(),
                predicateTextField.predicateProperty()
        ));
        hierarchy.setCellFactory(n -> new HierarchyCellFactory(client.getApisHandler()));

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
                () -> browserModel.getSelectedPixelAPI().get() != null && browserModel.getSelectedPixelAPI().get().canAccessRawPixels(),
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
                owner.getSelectionModel().select(null);
            } else {
                if (n.equals(Group.getAllGroupsGroup())) {
                    owner.getItems().addAll(server.getOwners());
                    owner.getSelectionModel().select(Owner.getAllMembersOwner());
                } else {
                    owner.getItems().addAll(n.getOwners());
                    owner.getSelectionModel().selectFirst();
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

        BooleanBinding isSelectedItemOrphanedFolderBinding = Bindings.createBooleanBinding(() ->
                        hierarchy.getSelectionModel().getSelectedItem() != null && hierarchy.getSelectionModel().getSelectedItem().getValue() instanceof OrphanedFolder,
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

        canvas.managedProperty().bind(Bindings.createBooleanBinding(() ->
                        hierarchy.getSelectionModel().getSelectedItems().size() == 1 &&
                                hierarchy.getSelectionModel().getSelectedItems().getFirst().getValue() instanceof Image,
                hierarchy.getSelectionModel().getSelectedItems()));

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
            client.getApisHandler().getThumbnail(image.getId())
                    .exceptionally(error -> {
                        logger.error("Error when retrieving thumbnail", error);
                        return null;
                    })
                    .thenAccept(thumbnail -> Platform.runLater(() -> {
                        if (thumbnail != null) {
                            UiUtilities.paintBufferedImageOnCanvas(thumbnail, canvas);
                        }
                    }));
        }
    }

    private void updateDescription() {
        description.getItems().clear();

        var selectedItems = hierarchy.getSelectionModel().getSelectedItems();
        if (selectedItems.size() == 1 && selectedItems.getFirst() != null && selectedItems.getFirst().getValue() instanceof ServerEntity serverEntity) {
            description.getItems().setAll(
                    IntStream.rangeClosed(0, serverEntity.getNumberOfAttributes()).boxed().collect(Collectors.toList())
            );
        } else {
            description.getItems().clear();
        }
    }

    private void updateImportButton() {
        var importableEntities = hierarchy.getSelectionModel().getSelectedItems().stream()
                .map(item -> item == null ? null : item.getValue())
                .filter(Objects::nonNull)
                .filter(repositoryEntity -> {
                    if (repositoryEntity instanceof Image image) {
                        return image.isSupported().get();
                    } else {
                        return repositoryEntity instanceof ServerEntity;
                    }
                })
                .toList();

        importImage.setDisable(importableEntities.isEmpty());

        if (importableEntities.isEmpty()) {
            importImage.setText(resources.getString("Browser.ServerBrowser.cantImportSelectedToQuPath"));
        } else if (importableEntities.size() == 1) {
            importImage.setText(MessageFormat.format(
                    resources.getString("Browser.ServerBrowser.importToQuPath"),
                    importableEntities.getFirst().getLabel()
            ));
        } else {
            importImage.setText(resources.getString("Browser.ServerBrowser.importSelectedToQuPath"));
        }
    }
}
