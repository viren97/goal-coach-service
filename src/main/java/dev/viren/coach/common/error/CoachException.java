package dev.viren.coach.common.error;

/** The only domain exception. The {@link #code()} decides the HTTP status. */
public class CoachException extends RuntimeException {
    private final ErrorCode code;

    public CoachException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() { return code; }

    public static CoachException notFound(String resource, String id) {
        return new CoachException(ErrorCode.RESOURCE_NOT_FOUND, resource + " '" + id + "' not found");
    }

    public static CoachException invalid(String message) {
        return new CoachException(ErrorCode.INVALID_REQUEST, message);
    }
}
