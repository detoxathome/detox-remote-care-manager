package nl.detoxathome.remotecaremanager.service.exception;

public class ExpiredAuthTokenException extends AuthTokenException {
	public ExpiredAuthTokenException(String message) {
		super(message);
	}

	public ExpiredAuthTokenException(String message, Throwable cause) {
		super(message, cause);
	}
}
