package nl.detoxathome.remotecaremanager.service.sso;

import jakarta.servlet.http.HttpServletResponse;
import nl.detoxathome.remotecaremanager.client.model.Role;
import nl.detoxathome.remotecaremanager.client.model.TokenResult;
import nl.detoxathome.remotecaremanager.client.project.BaseProject;
import nl.detoxathome.remotecaremanager.client.project.ProjectRepository;
import nl.detoxathome.remotecaremanager.dao.Database;
import nl.detoxathome.remotecaremanager.service.AuthToken;
import nl.detoxathome.remotecaremanager.service.ProtocolVersion;
import nl.detoxathome.remotecaremanager.service.UserListenerRepository;
import nl.detoxathome.remotecaremanager.service.controller.AuthControllerExecution;
import nl.detoxathome.remotecaremanager.service.exception.ForbiddenException;
import nl.detoxathome.remotecaremanager.service.exception.HttpException;
import nl.detoxathome.remotecaremanager.service.model.User;
import nl.detoxathome.remotecaremanager.service.model.UserCache;
import nl.detoxathome.remotecaremanager.service.model.UserProject;
import nl.detoxathome.remotecaremanager.service.model.UserProjectTable;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.validation.ValidationException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class SSOTokenUserCreator {
	public static TokenResult create(ProtocolVersion version,
			HttpServletResponse response, Database authDb,
			List<String> projects, String email) throws HttpException,
			Exception {
		synchronized (AuthControllerExecution.AUTH_LOCK) {
			UserCache userCache = UserCache.getInstance();
			User user = userCache.findByEmail(email);
			if (user == null) {
				return createNewUser(version, response, authDb, projects,
						email);
			} else {
				for (String project : projects) {
					addUserToProject(authDb, project, user);
				}
				return createToken(version, response, user);
			}
		}
	}

	private static TokenResult createNewUser(ProtocolVersion version,
			HttpServletResponse response, Database authDb,
			List<String> projects, String email) throws HttpException,
			Exception {
		String password = UUID.randomUUID().toString().toLowerCase()
				.replaceAll("-", "");
		TokenResult result = AuthControllerExecution.signupSSO(version,
				response, email, password, null, authDb);
		UserCache userCache = UserCache.getInstance();
		User user = userCache.findByEmail(email);
		for (String project : projects) {
			addUserToProject(authDb, project, user);
		}
		return result;
	}

	private static void addUserToProject(Database authDb, String projectCode,
			User user) throws HttpException, DatabaseException {
		ProjectRepository projects = AppComponents.get(ProjectRepository.class);
		BaseProject project = projects.findProjectByCode(projectCode);
		if (!User.isProjectUser(authDb, project.getCode(), user.getUserid(),
				Role.PATIENT)) {
			try {
				project.validateAddUser(user, user, authDb);
			} catch (ValidationException ex) {
				throw new ForbiddenException(ex.getMessage());
			}
			UserProject userProject = new UserProject();
			userProject.setUser(user.getUserid());
			userProject.setProjectCode(project.getCode());
			userProject.setAsRole(Role.PATIENT);
			authDb.insert(UserProjectTable.NAME, userProject);
			UserListenerRepository.getInstance().notifyUserAddedToProject(
					user, project.getCode(), Role.PATIENT);
		}
	}

	private static TokenResult createToken(ProtocolVersion version,
			HttpServletResponse response, User user) {
		ZonedDateTime now = DateTimeUtils.nowMs();
		String token = AuthToken.createToken(version, user, false, null, now,
				null, false, false, response);
		return new TokenResult(user.getUserid(), token);
	}
}
