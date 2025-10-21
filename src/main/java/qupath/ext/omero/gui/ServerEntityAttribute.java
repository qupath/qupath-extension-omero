package qupath.ext.omero.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.Server;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A label-value pair showing information on a {@link ServerEntity}.
 *
 * @param label a localizable text describing what the value represents
 * @param value a text representing a piece of information of a {@link ServerEntity}
 */
public record ServerEntityAttribute(String label, String value) {

    private static final Logger logger = LoggerFactory.getLogger(ServerEntityAttribute.class);
    private static final DateFormat ACQUISITION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final ResourceBundle resources = Utils.getResources();

    /**
     * Create a list of {@link ServerEntityAttribute} that describes the provided {@link ServerEntity}.
     * <p>
     * Only {@link Dataset}, {@link Image}, {@link Plate}, {@link PlateAcquisition}, {@link Project}, {@link Screen} and {@link Well}
     * are supported.
     *
     * @param server the server containing the entity to describe
     * @param serverEntity the entity to describe
     * @return a list of {@link ServerEntityAttribute} that describes the provided {@link ServerEntity}
     */
    public static List<ServerEntityAttribute> create(Server server, ServerEntity serverEntity) {
        return switch (serverEntity) {
            case Dataset dataset -> List.of(
                    getName(dataset),
                    getId(dataset),
                    new ServerEntityAttribute(
                            resources.getString("Entities.description"),
                            dataset.getDescription().isPresent() && !dataset.getDescription().get().isBlank() ? dataset.getDescription().get() : "-"
                    ),
                    getOwnerFullName(server, dataset),
                    getGroupName(server, dataset),
                    new ServerEntityAttribute(
                            resources.getString("Entities.nbImages"),
                            String.valueOf(dataset.getChildCount())
                    )
            );
            case Image image -> List.of(
                    getName(image),
                    getId(image),
                    getOwnerFullName(server, image),
                    getGroupName(server, image),
                    new ServerEntityAttribute(
                            resources.getString("Entities.acquisitionDate"),
                            image.getAcquisitionDate().map(ACQUISITION_DATE_FORMAT::format).orElse("-")
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.uncompressedSize"),
                            image.getSizeMebibyte().map(sizeMebibyte -> String.format(
                                    "%.1f %s",
                                    sizeMebibyte > 1000 ? sizeMebibyte / 1024 : sizeMebibyte,
                                    sizeMebibyte > 1000 ? "GiB" : "MiB"
                            )).orElse("-")
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.imageWidth"),
                            String.format("%d px", image.getSizeX())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.imageHeight"),
                            String.format("%d px", image.getSizeY())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.nbZSlices"),
                            String.valueOf(image.getSizeZ())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.nbChannels"),
                            String.valueOf(image.getSizeC())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.nbTimePoints"),
                            String.valueOf(image.getSizeT())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.pixelSizeX"),
                            image.getPhysicalSizeX()
                                    .map(physicalSize -> String.format("%s %s", physicalSize.value(), physicalSize.symbol()))
                                    .orElse("-")
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.pixelSizeY"),
                            image.getPhysicalSizeY()
                                    .map(physicalSize -> String.format("%s %s", physicalSize.value(), physicalSize.symbol()))
                                    .orElse("-")
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.pixelSizeZ"),
                            image.getPhysicalSizeZ()
                                    .map(physicalSize -> String.format("%s %s", physicalSize.value(), physicalSize.symbol()))
                                    .orElse("-")
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.pixelType"),
                            image.getPixelType().map(Enum::name).orElse("-")
                    )
            );
            case Plate plate -> List.of(
                    getName(plate),
                    getId(plate),
                    getOwnerFullName(server, plate),
                    getGroupName(server, plate),
                    new ServerEntityAttribute(
                            resources.getString("Entities.columns"),
                            String.valueOf(plate.getColumns())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.rows"),
                            String.valueOf(plate.getRows())
                    )
            );
            case PlateAcquisition plateAcquisition -> List.of(
                    getName(plateAcquisition),
                    getId(plateAcquisition),
                    getOwnerFullName(server, plateAcquisition),
                    getGroupName(server, plateAcquisition),
                    new ServerEntityAttribute(
                            resources.getString("Entities.acquisitionDate"),
                            plateAcquisition.getStartTime().map(ACQUISITION_DATE_FORMAT::format).orElse("-")
                    )
            );
            case Project project -> List.of(
                    getName(project),
                    getId(project),
                    new ServerEntityAttribute(
                            resources.getString("Entities.description"),
                            project.getDescription().isPresent() && !project.getDescription().get().isBlank() ? project.getDescription().get() : "-"
                    ),
                    getOwnerFullName(server, project),
                    getGroupName(server, project),
                    new ServerEntityAttribute(
                            resources.getString("Entities.nbDatasets"),
                            String.valueOf(project.getChildCount())
                    )
            );
            case Screen screen -> List.of(
                    getName(screen),
                    getId(screen),
                    new ServerEntityAttribute(
                            resources.getString("Entities.description"),
                            screen.getDescription().isPresent() && !screen.getDescription().get().isBlank() ? screen.getDescription().get() : "-"
                    ),
                    getOwnerFullName(server, screen),
                    getGroupName(server, screen),
                    new ServerEntityAttribute(
                            resources.getString("Entities.nbPlates"),
                            String.valueOf(screen.getChildCount())
                    )
            );
            case Well well -> List.of(
                    getName(well),
                    getId(well),
                    getOwnerFullName(server, well),
                    getGroupName(server, well),
                    new ServerEntityAttribute(
                            resources.getString("Entities.column"),
                            String.valueOf(well.getColumn())
                    ),
                    new ServerEntityAttribute(
                            resources.getString("Entities.row"),
                            String.valueOf(well.getRow())
                    )
            );
            default -> {
                logger.warn("Server entity {} not recognized. Cannot create attributes", serverEntity);
                yield List.of();
            }
        };
    }

    private static ServerEntityAttribute getName(ServerEntity serverEntity) {
        return new ServerEntityAttribute(
                resources.getString("Entities.name"),
                serverEntity.getName().isPresent() && !serverEntity.getName().get().isBlank() ? serverEntity.getName().get() : serverEntity.getLabel()
        );
    }

    private static ServerEntityAttribute getId(ServerEntity serverEntity) {
        return new ServerEntityAttribute(
                resources.getString("Entities.id"),
                String.valueOf(serverEntity.getId())
        );
    }

    private static ServerEntityAttribute getOwnerFullName(Server server, ServerEntity serverEntity) {
        return new ServerEntityAttribute(
                resources.getString("Entities.owner"),
                server.getExperimenters().stream()
                        .filter(experimenter -> experimenter.getId() == serverEntity.getOwnerId())
                        .findAny()
                        .map(Experimenter::getFullName)
                        .orElse("-")
        );
    }

    private static ServerEntityAttribute getGroupName(Server server, ServerEntity serverEntity) {
        return new ServerEntityAttribute(
                resources.getString("Entities.group"),
                server.getGroups().stream()
                        .filter(group -> group.getId() == serverEntity.getGroupId())
                        .findAny()
                        .flatMap(ExperimenterGroup::getName)
                        .orElse("-")
        );
    }
}
