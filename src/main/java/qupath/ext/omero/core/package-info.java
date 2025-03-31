/**
 * This package contains the core functionalities of the extension.
 * <ul>
 *     <li>
 *         The {@link qupath.ext.omero.core.RequestSender RequestSender} class
 *         provides generic methods to send HTTP requests to a server.
 *     </li>
 *     <li>
 *         The {@link qupath.ext.omero.core.preferences preferences} package
 *         stores server information in a permanent way. This is useful for the user to not have to write again server URIs
 *         after each application restart.
 *     </li>
 *     <li>
 *         The {@link qupath.ext.omero.core.Client Client} class handle all active connections
 *         and is used to perform any operation related to a server.
 *     </li>
 *     <li>
 *         The {@link qupath.ext.omero.core.imageserver ImageServer} package contains the
 *         {@link qupath.lib.images.servers.ImageServer ImageServer} used when opening images coming from OMERO servers.
 *     </li>
 *     <li>
 *         Other classes and packages are used internally by the entities described above.
 *     </li>
 * </ul>
 */
package qupath.ext.omero.core;