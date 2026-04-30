package nl.detoxathome.remotecaremanager.service.controller.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.servlet.http.HttpServletRequest;
import nl.detoxathome.remotecaremanager.client.SyncTableRestriction;
import nl.detoxathome.remotecaremanager.client.exception.ErrorCode;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.service.HttpContentReader;
import nl.detoxathome.remotecaremanager.service.ProtocolVersion;
import nl.detoxathome.remotecaremanager.service.exception.BadRequestException;
import nl.detoxathome.remotecaremanager.service.exception.HttpException;
import nl.detoxathome.remotecaremanager.service.model.User;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.validation.MapReader;

/**
 * This class models validated input from the sync register push query.
 * 
 * @author Dennis Hofs (RRD)
 */
public class SyncRegisterPushInput {
	private User subjectUser;
	private String databaseName;
	private String fcmToken;
	private String deviceId;
	private SyncTableRestriction restrictions;

	/**
	 * Returns the subject user.
	 * 
	 * @return the subject user
	 */
	public User getSubjectUser() {
		return subjectUser;
	}
	
	/**
	 * Sets the subject user.
	 * 
	 * @param subjectUser the subject user
	 */
	public void setSubjectUser(User subjectUser) {
		this.subjectUser = subjectUser;
	}

	/**
	 * Returns the database name.
	 * 
	 * @return the database name
	 */
	public String getDatabaseName() {
		return databaseName;
	}
	
	/**
	 * Sets the database name.
	 * 
	 * @param databaseName the database name
	 */
	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}
	
	/**
	 * Returns the client token for Firebase Cloud Messaging.
	 * 
	 * @return the client token for Firebase Cloud Messaging
	 */
	public String getFcmToken() {
		return fcmToken;
	}

	/**
	 * Sets the client token for Firebase Cloud Messaging.
	 * 
	 * @param fcmToken the client token for Firebase Cloud Messaging
	 */
	public void setFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
	}

	/**
	 * Returns the ID of the device to which the push message is sent.
	 * 
	 * @return the ID of the device to which the push message is sent
	 */
	public String getDeviceId() {
		return deviceId;
	}

	/**
	 * Sets the ID of the device to which the push message is sent.
	 * 
	 * @param deviceId the ID of the device to which the push message is sent
	 */
	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * Returns the restrictions on what database actions should be pushed.
	 * 
	 * @return the restrictions on what database actions should be pushed
	 */
	public SyncTableRestriction getRestrictions() {
		return restrictions;
	}

	/**
	 * Sets the restrictions on what database actions should be pushed.
	 * 
	 * @param restrictions the restrictions on what database actions should be
	 * pushed
	 */
	public void setRestrictions(SyncTableRestriction restrictions) {
		this.restrictions = restrictions;
	}

	/**
	 * Parses and validates input from the sync register push query. If the
	 * database is null, this method returns null, meaning that there will never
	 * be any database actions.
	 *
	 * @param version the protocol version
	 * @param request the HTTP request
	 * @param authDb the authentication database
	 * @param database the object database or sample database (can be null)
	 * @param user the user
	 * @param subject the user ID of the subject (can be an empty string or
	 * null)
	 * @return the query input or null
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public static SyncRegisterPushInput parse(ProtocolVersion version,
			HttpServletRequest request, Database authDb, Database database,
			User user, String subject) throws HttpException, Exception {
		SyncRegisterPushInput result = new SyncRegisterPushInput();
		result.subjectUser = User.findAccessibleUser(version, subject, authDb,
				user);
		if (database == null)
			return null;
		result.databaseName = database.getName();
		try {
			Map<String,?> params = HttpContentReader.readJsonParams(request,
					false);
			MapReader paramReader = new MapReader(params);
			Object tokenObj = params.get("fcmToken");
			if (tokenObj == null)
				tokenObj = params.get("token");
			if (tokenObj == null || tokenObj.toString().isBlank()) {
				throw new ParseException(
						"Property fcmToken (or token) not found");
			}
			result.fcmToken = tokenObj.toString().trim();
			result.deviceId = paramReader.readString("deviceId");
			result.restrictions = new SyncTableRestriction();
			result.restrictions.setIncludeTables(paramReader.readJson(
					"includeTables", new TypeReference<List<String>>() {},
					null));
			result.restrictions.setExcludeTables(paramReader.readJson(
					"excludeTables", new TypeReference<List<String>>() {},
					null));
			return result;
		} catch (ParseException ex) {
			throw new BadRequestException(ErrorCode.INVALID_INPUT,
					"Invalid content: " + ex.getMessage());
		}
	}
}
