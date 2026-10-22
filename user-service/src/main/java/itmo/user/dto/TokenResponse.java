package itmo.user.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
        return "TokenResponse[accessToken=<redacted>, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
