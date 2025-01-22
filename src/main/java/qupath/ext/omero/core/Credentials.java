package qupath.ext.omero.core;

public class Credentials {

    private final UserType userType;
    private final String username;
    private final String password;  //TODO: use char array and clear once no longer used
    public enum UserType {
        PUBLIC_USER,
        REGULAR_USER
    }

    public Credentials() {
        this.username = null;
        this.password = null;
        this.userType = UserType.PUBLIC_USER;
    }

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
        this.userType = UserType.REGULAR_USER;
    }

    //TODO: override equals and don't take password into account
    //TODO: override to string (return public user or username)

    public UserType getUserType() {
        return userType;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
