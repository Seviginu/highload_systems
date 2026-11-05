package itmo.user.application.model;

public record TokenResult(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() { return "TokenResult[accessToken=<redacted>, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]"; }
}
