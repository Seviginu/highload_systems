package itmo.files.application;

public class FileForbidden extends RuntimeException {
    public FileForbidden() {
        super("Only the owner or ADMIN may access this file");
    }
}
