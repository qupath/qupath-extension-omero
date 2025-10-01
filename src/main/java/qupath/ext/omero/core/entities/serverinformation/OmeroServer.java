package qupath.ext.omero.core.entities.serverinformation;

public record OmeroServer(
        String host,
        Integer id,
        Integer port
) {
    public boolean allFieldsDefined() {
        return host != null && id != null && port != null;
    }
}
