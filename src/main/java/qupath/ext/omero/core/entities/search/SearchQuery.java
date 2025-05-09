package qupath.ext.omero.core.entities.search;

import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

/**
 * Contain information about a search query.
 *
 * @param query the text to search
 * @param searchOnName whether to restrict the search on names
 * @param searchOnDescription whether to restrict the search on descriptions
 * @param searchForImages whether to includes images on the result
 * @param searchForDatasets whether to includes datasets on the result
 * @param searchForProjects whether to includes projects on the result
 * @param searchForWells whether to includes wells on the result
 * @param searchForPlates whether to includes plates on the result
 * @param searchForScreens whether to includes screens on the result
 * @param group the group that the results should belong to
 * @param owner the owner that the results should belong to
 */
public record SearchQuery(
        String query,
        boolean searchOnName,
        boolean searchOnDescription,
        boolean searchForImages,
        boolean searchForDatasets,
        boolean searchForProjects,
        boolean searchForWells,
        boolean searchForPlates,
        boolean searchForScreens,
        Group group,
        Owner owner
) {}
