package qupath.ext.omero.gui;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.lib.gui.QuPathGUI;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * A menu whose items are sample scripts.
 */
class SampleScriptMenu extends Menu {

    private static final Logger logger = LoggerFactory.getLogger(SampleScriptMenu.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final LinkedHashMap<String, String> SCRIPTS = new LinkedHashMap<>() {{
        put(resources.getString("SampleScriptMenu.openServer"), "sample-scripts/open_server.groovy");
        put(resources.getString("SampleScriptMenu.openImagesOfProject"), "sample-scripts/open_images_of_project.groovy");
        put(resources.getString("SampleScriptMenu.openImageFromCommandLine"), "sample-scripts/open_image_from_command_line.groovy");
        put(resources.getString("SampleScriptMenu.sendAnnotations"), "sample-scripts/send_annotations.groovy");
        put(resources.getString("SampleScriptMenu.sendMeasurements"), "sample-scripts/send_measurements.groovy");
        put(resources.getString("SampleScriptMenu.sendKVP"), "sample-scripts/send_key_value_pairs.groovy");
        put(resources.getString("SampleScriptMenu.sendImageSettings"), "sample-scripts/send_image_settings.groovy");
        put(resources.getString("SampleScriptMenu.importAnnotations"), "sample-scripts/import_annotations.groovy");
        put(resources.getString("SampleScriptMenu.importKVP"), "sample-scripts/import_key_value_pairs.groovy");
        put(resources.getString("SampleScriptMenu.importImageSettings"), "sample-scripts/import_image_settings.groovy");
    }};

    /**
     * Create the menu.
     *
     * @param quPath the currently used QuPath GUI
     */
    public SampleScriptMenu(QuPathGUI quPath) {
        setText(resources.getString("SampleScriptMenu.sampleScripts"));

        getItems().addAll(SCRIPTS.entrySet().stream()
                .map(entry -> {
                    try (InputStream stream = SampleScriptMenu.class.getResourceAsStream(entry.getValue())) {
                        if (stream == null) {
                            logger.warn("Cannot find script in {}", entry.getValue());
                            return null;
                        }

                        String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        if (script.isEmpty()) {
                            logger.warn("Empty script found in {}. Not adding it to the list", entry.getValue());
                        }

                        MenuItem menuItem = new MenuItem(entry.getKey());
                        menuItem.setOnAction(ignored -> quPath.getScriptEditor().showScript(entry.getKey(), script));
                        return menuItem;
                    } catch (Exception e) {
                        logger.warn("Cannot read script {}", entry.getValue(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList()
        );
    }
}
