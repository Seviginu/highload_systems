package itmo.files.application;

public class FileTooLarge extends RuntimeException {
    public FileTooLarge() {
        super("File exceeds the configured size limit");
    }
}
