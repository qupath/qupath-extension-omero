package qupath.ext.omero.core.entities.annotations.annotationsentities;

/**
 * An OMERO experimenter represents a person working on an OMERO entity.
 *
 * @param id the unique ID of this experimenter, or 0 if not indicated
 * @param firstName the first name of this experimenter, or an empty String if not indicated
 * @param lastName the last name of this experimenter, or an empty String if not found
 */
public record Experimenter(int id, String firstName, String lastName) {

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Experimenter experimenter))
            return false;
        return experimenter.id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String firstName() {
        return firstName == null ? "": firstName;
    }

    @Override
    public String lastName() {
        return lastName == null ? "" : lastName;
    }

    /**
     * @return the full name (first name last name) of this experimenter,
     * or an empty String if not found
     */
    public String fullName() {
        String firstName = firstName();
        String lastName = lastName();

        if (!firstName.isEmpty() && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        } else {
            return firstName + lastName;
        }
    }
}
