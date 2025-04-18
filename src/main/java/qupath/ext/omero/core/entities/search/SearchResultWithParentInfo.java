package qupath.ext.omero.core.entities.search;

import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;

/**
 * The result of a search query, with additional information on the parent entities if the result is
 * about an image.
 *
 * @param searchResult the search result
 * @param parentProject the parent project if it exists and the entity is an image, or null
 * @param parentDataset the parent dataset if it exists and the entity is an image, or null
 * @param parentScreen the parent screen if it exists and the entity is an image, or null
 * @param parentPlate the parent plate if it exists and the entity is an image, or null
 * @param parentPlateAcquisition the parent plate acquisition if it exists and the entity is an image, or null
 * @param parentWell the parent well if it exists and the entity is an image, or null
 */
public record SearchResultWithParentInfo(
        SearchResult searchResult,
        Project parentProject,
        Dataset parentDataset,
        Screen parentScreen,
        Plate parentPlate,
        PlateAcquisition parentPlateAcquisition,
        Well parentWell
) {

    /**
     * Create a search result with no parent information.
     *
     * @param searchResult the search result
     */
    public SearchResultWithParentInfo(SearchResult searchResult) {
        this(searchResult, null, null, null, null, null, null);
    }
}
