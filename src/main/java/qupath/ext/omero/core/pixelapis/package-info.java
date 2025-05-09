/**
 * This package contains different methods to read the pixel values of an image.
 * <ul>
 *     <li>
 *         The {@link qupath.ext.omero.core.pixelapis.PixelApi PixelApi} interface contains
 *         information on an API that can read pixel values of an image. It can be used to create a
 *         {@link qupath.ext.omero.core.pixelapis.PixelApiReader PixelApiReader} which is
 *         the class that will actually read pixel values.
 *     </li>
 *     <li>
 *         The {@link qupath.ext.omero.core.pixelapis.ice ice}, {@link qupath.ext.omero.core.pixelapis.web web}
 *         and {@link qupath.ext.omero.core.pixelapis.mspixelbuffer mspixelbuffer} packages contain different
 *         APIs to read pixel values.
 *     </li>
 * </ul>
 */
package qupath.ext.omero.core.pixelapis;