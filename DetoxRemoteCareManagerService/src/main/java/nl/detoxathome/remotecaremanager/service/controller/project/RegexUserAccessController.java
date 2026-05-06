package nl.detoxathome.remotecaremanager.service.controller.project;

import nl.detoxathome.remotecaremanager.client.exception.HttpFieldError;
import nl.detoxathome.remotecaremanager.client.model.RegexUserAccess;
import nl.detoxathome.remotecaremanager.client.model.RegexUserAccessTable;
import nl.detoxathome.remotecaremanager.client.model.Role;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.dao.DatabaseCriteria;
import nl.detoxathome.remotecaremanager.dao.DatabaseSort;
import nl.detoxathome.remotecaremanager.service.ProtocolVersion;
import nl.detoxathome.remotecaremanager.service.exception.BadRequestException;
import nl.detoxathome.remotecaremanager.service.exception.ForbiddenException;
import nl.detoxathome.remotecaremanager.service.exception.HttpException;
import nl.detoxathome.remotecaremanager.service.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RegexUserAccessController {
	private static final Object LOCK = new Object();

	private RegexUserAccessTable table;

	public RegexUserAccessController(RegexUserAccessTable table) {
		this.table = table;
	}

	public List<String> getUserAccess(ProtocolVersion version, Database authDb,
			User user, String subject) throws HttpException, Exception {
		if (user.getRole() != Role.ADMIN)
			throw new ForbiddenException();
		User subjectUser = User.findAccessibleUser(version, subject, authDb,
				user);
		DatabaseCriteria criteria = new DatabaseCriteria.Equal("user",
				subjectUser.getUserid());
		DatabaseSort[] sort = new DatabaseSort[] {
				new DatabaseSort("emailRegex", true)
		};
		List<RegexUserAccess> userAccessList = authDb.select(table, criteria, 0,
				sort);
		List<String> result = new ArrayList<>();
		for (RegexUserAccess userAccess : userAccessList) {
			result.add(userAccess.getEmailRegex());
		}
		return result;
	}

	public Object addUserAccess(ProtocolVersion version, Database authDb,
			User user, String subject, String emailRegex) throws HttpException,
			Exception {
		if (user.getRole() != Role.ADMIN)
			throw new ForbiddenException();
		User subjectUser = User.findAccessibleUser(version, subject, authDb,
				user);
		try {
			Pattern.compile(emailRegex);
		} catch (IllegalArgumentException ex) {
			HttpFieldError error = new HttpFieldError("emailRegex",
					"Invalid regular expression: " + emailRegex);
			throw BadRequestException.withInvalidInput(error);
		}
		synchronized (LOCK) {
			DatabaseCriteria criteria = new DatabaseCriteria.And(
					new DatabaseCriteria.Equal("user", subjectUser.getUserid()),
					new DatabaseCriteria.Equal("emailRegex", emailRegex)
			);
			RegexUserAccess userAccess = authDb.selectOne(table, criteria,
					null);
			if (userAccess != null)
				return null;
			userAccess = new RegexUserAccess(subjectUser.getUserid(),
					emailRegex);
			authDb.insert(table.getName(), userAccess);
			return null;
		}
	}

	public Object removeUserAccess(ProtocolVersion version, Database authDb,
			User user, String subject, String emailRegex) throws HttpException,
			Exception {
		if (user.getRole() != Role.ADMIN)
			throw new ForbiddenException();
		User subjectUser = User.findAccessibleUser(version, subject, authDb,
				user);
		synchronized (LOCK) {
			DatabaseCriteria criteria;
			if (emailRegex == null || emailRegex.isEmpty()) {
				criteria = new DatabaseCriteria.Equal("user",
						subjectUser.getUserid());
 			} else {
				criteria = new DatabaseCriteria.And(
					new DatabaseCriteria.Equal("user", subjectUser.getUserid()),
					new DatabaseCriteria.Equal("emailRegex", emailRegex)
				);
			}
			authDb.delete(table, criteria);
		}
		return null;
	}
}
