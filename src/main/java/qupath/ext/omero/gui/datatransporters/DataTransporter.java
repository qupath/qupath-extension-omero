package qupath.ext.omero.gui.datatransporters;

/**
 * A class that can import or export data between the currently opened image and its corresponding OMERO server.
 */
public interface DataTransporter {

    /**
     * @return the localized name of this command
     */
    String getMenuTitle();

    /**
     * Indicate if this transporter can transport data given the provided conditions.
     *
     * @param projectOpened whether there is a QuPath project currently opened
     * @param isRGB whether the current image uses the RGB format
     * @return whether this transport can work
     */
    boolean canTransportData(boolean projectOpened, boolean isRGB);

    /**
     * Attempt to import or export data between the currently opened image and its corresponding OMERO server.
     * This method doesn't return anything but will show dialogs and notifications indicating the success of the operation.
     */
    void transportData();
}
