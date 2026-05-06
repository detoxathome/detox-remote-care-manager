package nl.detoxathome.remotecaremanager.service.exception;

public class InvalidAuthTokenException extends AuthTokenException {
	public InvalidAuthTokenException(String message) {
		super(message);
	}

	public InvalidAuthTokenException(String message, Throwable cause) {
		super(message, cause);
	}
}
