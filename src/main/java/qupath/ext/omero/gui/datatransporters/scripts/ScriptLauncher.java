package qupath.ext.omero.gui.datatransporters.scripts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.lib.gui.QuPathGUI;

import java.util.ResourceBundle;

public class ScriptLauncher implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(ScriptLauncher.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final QuPathGUI quPath;
    private final String menuTitle;
    private final String script;
    /**
     * Create the annotation sender.
     *
     * @param quPath the quPath window
     */
    public ScriptLauncher(QuPathGUI quPath, String menuTitle, String script) {
        logger.debug("Creating script launcher for {}", quPath);
        this.quPath = quPath;
        this.menuTitle = menuTitle;
        this.script = script;
    }

    @Override
    public String getMenuTitle() {
        return this.menuTitle;
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return true;
    }

    @Override
    public void transportData() {
        var editor = this.quPath.getScriptEditor();
        if (editor == null) {
            logger.error("No script editor is available!");
            return;
        }
        this.quPath.getScriptEditor().showScript("OMERO sample script", this.script);
    }

    @Override
    public String toString() {
        return String.format("Script launcher for %s %s", this.menuTitle, this.quPath);
    }
}
