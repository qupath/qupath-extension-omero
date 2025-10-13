package qupath.ext.omero.core.apis.webclient.search;

import java.util.Objects;

/**
 * Contain information about a search query.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param query the text to search. Required
 * @param searchOnName whether to restrict the search on names
 * @param searchOnDescription whether to restrict the search on descriptions
 * @param searchForImages whether to includes images on the result
 * @param searchForDatasets whether to includes datasets on the result
 * @param searchForProjects whether to includes projects on the result
 * @param searchForWells whether to includes wells on the result
 * @param searchForPlates whether to includes plates on the result
 * @param searchForScreens whether to includes screens on the result
 * @param experimenterId the ID of the experimenter that the results should belong to
 * @param groupId the ID of the group that the results should belong to
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
        long experimenterId,
        long groupId
) {
    public SearchQuery {
        Objects.requireNonNull(query);
    }
}
