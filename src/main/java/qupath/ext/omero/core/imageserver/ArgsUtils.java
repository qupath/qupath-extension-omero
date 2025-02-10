package qupath.ext.omero.core.imageserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility methods for working with arguments (e.g. --someParameter someValue).
 */
class ArgsUtils {

    private ArgsUtils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Get and trim an argument from a list corresponding to the specified label.
     * <p>
     * Leading and trailing spaces are not considered. In other words, the provided label
     * will be compared to args[i].trim().
     *
     * @param label the text that indicates that the following argument is the one to retrieve
     * @param args the list of arguments to iterate on
     * @return the trimmed argument corresponding to the specified label, or an empty Optional if not found
     */
    public static Optional<String> findArgInList(String label, List<String> args) {
        String arg = null;
        int i = 0;
        while (i < args.size()-1) {
            String parameter = args.get(i++).trim();
            if (label.equals(parameter)) {
                arg = args.get(i++);
            }
        }

        if (arg == null) {
            return Optional.empty();
        } else {
            return Optional.of(arg.trim());
        }
    }

    /**
     * Replace arguments from the provided list by the provided values. For example, if args[i]
     * is equal to some key of the provided map, then the value of args[i+1] will be updated
     * with the corresponding value of the map.
     * <p>
     * Leading and trailing spaces are not considered. In other words, the keys of the provided map
     * will be compared to args[i].trim().
     * <p>
     * If some labels cannot be found in the list, then the labels and the corresponding values
     * are added to the list.
     * <p>
     * This function does not modify the input list but returns a new one.
     *
     * @param args the list of arguments
     * @param labelsToValues a map of label to values to update in the provided list. If a value is null,
     *                       then the corresponding label/value pair will not be updated but removed from
     *                       the list
     * @return a new list with the replaced arguments
     */
    public static List<String> replaceArgs(List<String> args, Map<String, String> labelsToValues) {
        Map<String, String> labelsToValuesToFind = new HashMap<>(labelsToValues);
        List<String> newArgs = new ArrayList<>();

        int i = 0;
        while (i < args.size()) {
            String parameter = args.get(i++).trim();

            if (labelsToValuesToFind.containsKey(parameter)) {
                if (labelsToValuesToFind.get(parameter) != null) {
                    newArgs.add(parameter);
                    newArgs.add(labelsToValuesToFind.get(parameter));
                }
                labelsToValuesToFind.remove(parameter);
                i++;
            } else {
                newArgs.add(parameter);
            }
        }

        for (var entry: labelsToValuesToFind.entrySet()) {
            if (entry.getValue() != null) {
                newArgs.addAll(List.of(entry.getKey(), entry.getValue()));
            }
        }

        return newArgs;
    }
}
