package qupath.ext.omero.gui.browser.advancedinformation;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.annotations.CommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.FileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.MapAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.Pair;
import qupath.ext.omero.core.apis.webclient.annotations.RatingAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.TagAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.gui.UiUtilities;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Window displaying OMERO annotations of an OMERO entity. OMERO annotations are <b>not</b>
 * similar to QuPath annotations. Rather, they represent metadata associated with OMERO entities.
 * <p>
 * Annotations are displayed within several panes. Some annotations use a {@link FormPane}, while
 * others use an {@link InformationPane}.
 */
public class AdvancedInformation extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedInformation.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final ServerEntity serverEntity;
    @FXML
    private VBox content;

    /**
     * Create the advanced information window.
     *
     * @param owner the stage who should own this window
     * @param serverEntity the OMERO entity whose information should be displayed
     * @param annotations the OMERO annotations who belong to the OMERO entity
     * @throws IOException if an error occurs while creating the window
     */
    public AdvancedInformation(Stage owner, ServerEntity serverEntity, List<Annotation> annotations) throws IOException {
        logger.debug("Creating advanced information window for {} displaying {}", serverEntity, annotations);
        this.serverEntity = serverEntity;

        UiUtilities.loadFXML(this, AdvancedInformation.class.getResource("advanced_information.fxml"));

        setTitle(serverEntity.getLabel());

        content.getChildren().add(createObjectDetailPane());
        setAnnotationPanes(annotations);

        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                logger.debug("Escape key pressed. Closing advanced information window of {}", serverEntity);
                close();
            }
        });

        initOwner(owner);
        show();
    }

    private Node createObjectDetailPane() throws IOException {
        logger.debug("Creating entity details pane with {} attributes", serverEntity.getNumberOfAttributes());

        FormPane formPane = new FormPane(resources.getString("Browser.ServerBrowser.AdvancedInformation.details"));
        for (int i=0; i<serverEntity.getNumberOfAttributes(); ++i) {
            formPane.addRow(serverEntity.getAttributeName(i), serverEntity.getAttributeValue(i));
        }
        return formPane;
    }

    private void setAnnotationPanes(List<Annotation> annotations) {
        logger.debug("Setting annotation panes to display {}", annotations);

        createPane(annotations, this::createCommentPane, CommentAnnotation.class);
        createPane(annotations, this::createFilePane, FileAnnotation.class);
        createPane(annotations, this::createMapPane, MapAnnotation.class);
        createPane(annotations, this::createRatingPane, RatingAnnotation.class);
        createPane(annotations, this::createTagPane, TagAnnotation.class);
    }

    private <T extends Annotation> void createPane(List<Annotation> annotations, Function<List<T>, TitledPane> paneCreator, Class<T> annotationType) {
        List<T> filteredAnnotations = annotations.stream()
                .filter(annotationType::isInstance)
                .map(annotationType::cast)
                .toList();

        TitledPane pane = paneCreator.apply(filteredAnnotations);
        if (pane == null) {
            return;
        }

        pane.setExpanded(!filteredAnnotations.isEmpty());

        content.getChildren().add(pane);
    }

    private TitledPane createCommentPane(List<CommentAnnotation> annotations) {
        InformationPane commentPane;
        try {
            commentPane = new InformationPane(String.format(
                    "%s (%d)",
                    resources.getString("Browser.ServerBrowser.AdvancedInformation.comments"),
                    annotations.size()
            ));
        } catch (IOException e) {
            logger.error("Cannot create comment pane", e);
            return null;
        }

        for (CommentAnnotation annotation : annotations) {
            commentPane.addRow(
                    annotation.getComment(),
                    MessageFormat.format(
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.added"),
                            annotation.getAdderName().orElse("-")
                    )
            );
        }
        return commentPane;
    }

    private TitledPane createFilePane(List<FileAnnotation> annotations) {
        InformationPane attachmentPane;
        try {
            attachmentPane = new InformationPane(String.format(
                    "%s (%d)",
                    resources.getString("Browser.ServerBrowser.AdvancedInformation.files"),
                    annotations.size()
            ));
        } catch (IOException e) {
            logger.error("Cannot create file pane", e);
            return null;
        }

        for (FileAnnotation annotation : annotations) {
            attachmentPane.addRow(
                    String.format(
                            "%s (%d %s)",
                            annotation.getFilename(),
                            annotation.getFileSize(),
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.bytes")
                    ),
                    MessageFormat.format(
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.addedOwnedType"),
                            annotation.getAdderName().orElse("-"),
                            annotation.getOwnerName().orElse("-"),
                            annotation.getMimeType()
                    )
            );
        }
        return attachmentPane;
    }

    private TitledPane createMapPane(List<MapAnnotation> annotations) {
        FormPane mapPane;
        try {
            mapPane = new FormPane();
        } catch (IOException e) {
            logger.error("Cannot create map pane", e);
            return null;
        }
        int numberOfValues = 0;

        for (MapAnnotation annotation : annotations) {
            for (Pair pair: annotation.getPairs()) {
                mapPane.addRow(
                        pair.key(),
                        pair.value().isEmpty() ? "-" : pair.value(),
                        MessageFormat.format(
                                resources.getString("Browser.ServerBrowser.AdvancedInformation.addedOwned"),
                                annotation.getAdderName().orElse("-"),
                                annotation.getOwnerName().orElse("-")
                        )
                );
            }
            numberOfValues += annotation.getPairs().size();
        }
        mapPane.setTitle(String.format("%s (%d)", resources.getString("Browser.ServerBrowser.AdvancedInformation.maps"), numberOfValues));
        return mapPane;
    }

    private TitledPane createRatingPane(List<RatingAnnotation> annotations) {
        InformationPane ratingPane;
        try {
            ratingPane = new InformationPane(String.format(
                    "%s (%d)",
                    resources.getString("Browser.ServerBrowser.AdvancedInformation.ratings"),
                    annotations.size()
            ));
        } catch (IOException e) {
            logger.error("Cannot create rating pane", e);
            return null;
        }

        int averageRating = 0;
        for (RatingAnnotation annotation : annotations) {
            averageRating += annotation.getRating();
        }
        if (!annotations.isEmpty()) {
            averageRating /= annotations.size();
        }

        Glyph fullStarGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.STAR).size(QuPathGUI.TOOLBAR_ICON_SIZE);
        Glyph fullStar = GuiTools.ensureDuplicatableGlyph(fullStarGlyph);
        for (int i = 0; i < averageRating; i++) {
            ratingPane.addColum(fullStar.duplicate());
        }

        Glyph emptyStarGlyph = fullStarGlyph.color(new Color(0, 0, 0, .2));
        Glyph emptyStar = GuiTools.ensureDuplicatableGlyph(emptyStarGlyph);
        for (int i = 0; i < RatingAnnotation.getMaxValue() - averageRating; i++) {
            ratingPane.addColum(emptyStar.duplicate());
        }

        return ratingPane;
    }

    private TitledPane createTagPane(List<TagAnnotation> annotations) {
        InformationPane tagPane;
        try {
            tagPane = new InformationPane(String.format(
                    "%s (%d)",
                    resources.getString("Browser.ServerBrowser.AdvancedInformation.tags"),
                    annotations.size()
            ));
        } catch (IOException e) {
            logger.error("Cannot create tag pane", e);
            return null;
        }

        for (TagAnnotation annotation : annotations) {
            tagPane.addRow(
                    annotation.getTag(),
                    MessageFormat.format(
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.addedOwned"),
                            annotation.getAdderName().orElse("-"),
                            annotation.getOwnerName().orElse("-")
                    )
            );
        }
        return tagPane;
    }
}
