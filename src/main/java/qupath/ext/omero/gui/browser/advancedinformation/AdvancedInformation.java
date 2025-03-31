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
import qupath.ext.omero.core.entities.annotations.Annotation;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.annotations.CommentAnnotation;
import qupath.ext.omero.core.entities.annotations.FileAnnotation;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.annotations.RatingAnnotation;
import qupath.ext.omero.core.entities.annotations.TagAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.gui.UiUtilities;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;

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
     * @param owner the stage who should own this window.
     * @param serverEntity the OMERO entity whose information should be displayed.
     * @param annotationGroup the OMERO annotations who belong to the OMERO entity.
     * @throws IOException if an error occurs while creating the window
     */
    public AdvancedInformation(Stage owner, ServerEntity serverEntity, AnnotationGroup annotationGroup) throws IOException {
        logger.debug("Creating advanced information window for {} displaying {}", serverEntity, annotationGroup);
        this.serverEntity = serverEntity;

        UiUtilities.loadFXML(this, AdvancedInformation.class.getResource("advanced_information.fxml"));

        setTitle(serverEntity.getLabel());

        content.getChildren().add(createObjectDetailPane());
        setAnnotationPanes(annotationGroup);

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

    private void setAnnotationPanes(AnnotationGroup annotationGroup) throws IOException {
        logger.debug("Setting annotation panes to display {}", annotationGroup);

        for (var entry: annotationGroup.annotations().entrySet()) {
            logger.debug("Adding annotation pane for {}", entry);

            TitledPane pane = null;
            if (entry.getKey().equals(TagAnnotation.class)) {
                pane = createTagPane(entry.getValue());
            } else if (entry.getKey().equals(MapAnnotation.class)) {
                pane = createMapPane(entry.getValue());
            } else if (entry.getKey().equals(FileAnnotation.class)) {
                pane = createFilePane(entry.getValue());
            } else if (entry.getKey().equals(CommentAnnotation.class)) {
                pane = createCommentPane(entry.getValue());
            } else if (entry.getKey().equals(RatingAnnotation.class)) {
                pane = createRatingPane(entry.getValue());
            }

            if (pane == null) {
                logger.warn("Annotation type {} not recognized. Skipping it", entry.getKey());
            } else {
                logger.debug("Pane {} added for {}", pane, entry.getValue());
                pane.setExpanded(!entry.getValue().isEmpty());
                content.getChildren().add(pane);
            }
        }
    }

    private TitledPane createTagPane(List<Annotation> annotations) throws IOException {
        InformationPane tagPane = new InformationPane(String.format("%s (%d)", TagAnnotation.getTitle(), annotations.size()));

        for (Annotation annotation : annotations) {
            if (!(annotation instanceof TagAnnotation tagAnnotation)) {
                logger.warn("Annotation {} is not a tag annotation even though it should be", annotation);
                continue;
            }

            tagPane.addRow(
                    tagAnnotation.getValue().orElse(""),
                    MessageFormat.format(
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.addedOwned"),
                            tagAnnotation.getAdderFullName(),
                            tagAnnotation.getOwnerFullName()
                    )
            );
        }
        return tagPane;
    }

    private TitledPane createMapPane(List<Annotation> annotations) throws IOException {
        FormPane mapPane = new FormPane();
        int numberOfValues = 0;

        for (Annotation annotation : annotations) {
            if (!(annotation instanceof MapAnnotation mapAnnotation)) {
                logger.warn("Annotation {} is not a map annotation even though it should be", annotation);
                continue;
            }

            for (MapAnnotation.Pair pair: mapAnnotation.getPairs()) {
                mapPane.addRow(
                        pair.key(),
                        pair.value().isEmpty() ? "-" : pair.value(),
                        MessageFormat.format(
                                resources.getString("Browser.ServerBrowser.AdvancedInformation.addedOwned"),
                                mapAnnotation.getAdderFullName(),
                                mapAnnotation.getOwnerFullName()
                        )
                );
            }
            numberOfValues += mapAnnotation.getPairs().size();
        }
        mapPane.setTitle(String.format("%s (%d)", MapAnnotation.getTitle(), numberOfValues));
        return mapPane;
    }

    private TitledPane createFilePane(List<Annotation> annotations) throws IOException {
        InformationPane attachmentPane = new InformationPane(String.format("%s (%d)", FileAnnotation.getTitle(), annotations.size()));

        for (Annotation annotation : annotations) {
            if (!(annotation instanceof FileAnnotation fileAnnotation)) {
                logger.warn("Annotation {} is not a file annotation even though it should be", annotation);
                continue;
            }

            attachmentPane.addRow(
                    String.format(
                            "%s (%d %s)",
                            fileAnnotation.getFilename().orElse(""),
                            fileAnnotation.getFileSize().orElse(0L),
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.bytes")
                    ),
                    MessageFormat.format(
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.addedOwnedType"),
                            fileAnnotation.getAdderFullName(),
                            fileAnnotation.getOwnerFullName(),
                            fileAnnotation.getMimeType().orElse("")
                    )
            );
        }
        return attachmentPane;
    }

    private TitledPane createCommentPane(List<Annotation> annotations) throws IOException {
        InformationPane commentPane = new InformationPane(String.format("%s (%d)", CommentAnnotation.getTitle(), annotations.size()));

        for (Annotation annotation : annotations) {
            if (!(annotation instanceof CommentAnnotation commentAnnotation)) {
                logger.warn("Annotation {} is not a comment annotation even though it should be", annotation);
                continue;
            }

            commentPane.addRow(
                    commentAnnotation.getValue().orElse(""),
                    MessageFormat.format(
                            resources.getString("Browser.ServerBrowser.AdvancedInformation.added"),
                            commentAnnotation.getAdderFullName()
                    )
            );
        }
        return commentPane;
    }

    private TitledPane createRatingPane(List<Annotation> annotations) throws IOException {
        InformationPane ratingPane = new InformationPane(String.format("%s (%d)", RatingAnnotation.getTitle(), annotations.size()));

        int averageRating = 0;
        for (Annotation annotation : annotations) {
            if (!(annotation instanceof RatingAnnotation ratingAnnotation)) {
                logger.warn("Annotation {} is not a rating annotation even though it should be", annotation);
                continue;
            }

            averageRating += ratingAnnotation.getValue();
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
}
