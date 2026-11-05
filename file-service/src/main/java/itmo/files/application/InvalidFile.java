package itmo.files.application;

public class InvalidFile extends RuntimeException {
    public InvalidFile() {
        super("A non-empty file is required");
    }
}
