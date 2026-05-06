package nl.detoxathome.remotecaremanager.service.sso;

import jakarta.servlet.http.HttpServletResponse;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.service.ProtocolVersion;
import nl.detoxathome.remotecaremanager.service.exception.HttpException;
import nl.detoxathome.remotecaremanager.service.model.User;

public interface SSOUserValidator {
	User findAuthenticatedUser(ProtocolVersion version,
			HttpServletResponse response, Database authDb, String subject)
			throws HttpException, Exception;
}
