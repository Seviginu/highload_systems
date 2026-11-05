package itmo.files.application;

public class FileNotFound extends RuntimeException {
    public FileNotFound() {
        super("File not found");
    }
}
