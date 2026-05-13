package nl.detoxathome.remotecaremanager.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.detoxathome.remotecaremanager.client.exception.ErrorCode;
import nl.detoxathome.remotecaremanager.client.exception.HttpError;
import nl.detoxathome.remotecaremanager.client.exception.HttpFieldError;
import nl.detoxathome.remotecaremanager.client.model.*;
import nl.detoxathome.remotecaremanager.client.model.compat.ProjectV1;
import nl.detoxathome.remotecaremanager.client.model.compat.ProjectV2;
import nl.detoxathome.remotecaremanager.client.model.compat.ProjectV3;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxDigitalGuideDialogueCapability;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxDigitalGuideDialogueCapabilityTable;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxDigitalGuideDialogueOptions;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxLinkedSubjectSummary;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxMessageQueue;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxMessageQueueTable;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskConfiguration;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskConfigurationTable;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskRefreshRequest;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskRefreshRequestResult;
import nl.detoxathome.remotecaremanager.client.model.detox.DetoxTaskRefreshRequestTable;
import nl.detoxathome.remotecaremanager.client.model.sample.LocalTimeSample;
import nl.detoxathome.remotecaremanager.client.model.sample.Sample;
import nl.detoxathome.remotecaremanager.client.model.sample.UTCSample;
import nl.detoxathome.remotecaremanager.client.project.BaseProject;
import nl.detoxathome.remotecaremanager.client.project.ProjectRepository;
import nl.detoxathome.remotecaremanager.dao.*;
import nl.detoxathome.remotecaremanager.service.*;
import nl.detoxathome.remotecaremanager.service.controller.model.SelectFilterParser;
import nl.detoxathome.remotecaremanager.service.detox.DetoxDigitalGuideDialogueCapabilityValidator;
import nl.detoxathome.remotecaremanager.service.detox.DetoxDigitalGuideDialogueCapabilityValidator.DialogueCapabilityValidationException;
import nl.detoxathome.remotecaremanager.service.detox.DetoxDigitalGuideDialogueRegistry;
import nl.detoxathome.remotecaremanager.service.detox.DetoxTaskConfigurationValidator;
import nl.detoxathome.remotecaremanager.service.detox.DetoxTaskConfigurationValidator.TaskValidationException;
import nl.detoxathome.remotecaremanager.service.exception.BadRequestException;
import nl.detoxathome.remotecaremanager.service.exception.ForbiddenException;
import nl.detoxathome.remotecaremanager.service.exception.HttpException;
import nl.detoxathome.remotecaremanager.service.exception.NotFoundException;
import nl.detoxathome.remotecaremanager.service.model.User;
import nl.detoxathome.remotecaremanager.service.model.*;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.beans.PropertyReader;
import nl.rrd.utils.beans.PropertyWriter;
import nl.rrd.utils.exception.DatabaseException;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.json.JsonAtomicToken;
import nl.rrd.utils.json.JsonMapper;
import nl.rrd.utils.json.JsonObjectStreamReader;
import nl.rrd.utils.json.JsonParseException;
import nl.rrd.utils.datetime.DateTimeUtils;
import nl.rrd.utils.validation.TypeConversion;
import nl.rrd.utils.validation.ValidationException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

public class ProjectControllerExecution {
	private static final int BATCH_SIZE = 1000;
	public static final int HANGING_GET_TIMEOUT = 60000;
	private static final ObjectMapper DETOX_PAYLOAD_JSON_MAPPER =
			new ObjectMapper();
	private static final Map<String,DetoxCodedOption> AFWIJKING_OPENINGSVRAAG_OPTIONS =
			createDetoxCodedOptions(
					new DetoxCodedOption("at3",
							"Het gaat niet goed met mij"),
					new DetoxCodedOption("at4", "Het gaat goed met mij"));
	private static final Map<String,DetoxCodedOption> AFWIJKING_GEBRUIK_OPTIONS =
			createDetoxCodedOptions(
					new DetoxCodedOption("at15", "Ja, ik heb gebruikt"),
					new DetoxCodedOption("at16",
							"Ja, ik ben van plan te gebruiken"),
					new DetoxCodedOption("at17",
							"Nee, ik heb niet gebruikt en geen plannen om te gaan gebruiken"));
	private static final Map<String,DetoxCodedOption> AFWIJKING_ALLEEN_OPTIONS =
			createDetoxCodedOptions(
					new DetoxCodedOption("at21", "Ja, ik ben alleen"),
					new DetoxCodedOption("at22",
							"Nee, ik ben met mensen die mij steunen"),
					new DetoxCodedOption("at23",
							"Nee, ik ben samen met andere mensen"));
	private static final Map<String,DetoxCodedOption> AFWIJKING_HOE_SNEL_OPTIONS =
			createDetoxCodedOptions(
					new DetoxCodedOption("at27", "Binnen een uur"),
					new DetoxCodedOption("at28", "Binnen een dagdeel"),
					new DetoxCodedOption("at29",
							"Bij de volgende afspraak met mijn zorgverlener"));
	private static final int[] DASS21_ITEM_IDS = new int[] {52, 12, 14, 16, 18,
			20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50};

	/**
	 * Runs the list query.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param user the user
	 * @return the project codes
	 * @throws DatabaseException if a database error occurs
	 */
	public List<?> list(ProtocolVersion version, Database authDb, User user)
			throws DatabaseException {
		List<BaseProject> projects = getUserProjects(authDb, user);
		if (version.ordinal() >= ProtocolVersion.V6_0_8.ordinal()) {
			List<Project> result = new ArrayList<>();
			for (BaseProject baseProject : projects) {
				Project project = new Project();
				project.setCode(baseProject.getCode());
				project.setName(baseProject.getName());
				result.add(project);
			}
			return result;
		} else if (version.ordinal() >= ProtocolVersion.V6_0_4.ordinal()) {
			List<ProjectV3> result = new ArrayList<>();
			for (BaseProject baseProject : projects) {
				ProjectV3 project = new ProjectV3();
				project.setCode(baseProject.getCode());
				result.add(project);
			}
			return result;
		} else if (version.ordinal() >= ProtocolVersion.V5_0_5.ordinal()) {
			List<ProjectV2> result = new ArrayList<>();
			for (BaseProject baseProject : projects) {
				ProjectV2 project = new ProjectV2();
				project.setCode(baseProject.getCode());
				if (version.ordinal() >= ProtocolVersion.V6_0_0.ordinal())
					project.setSyncUser(user.getUserid());
				else
					project.setSyncUser(user.getEmail());
				project.setSyncGroup(null);
				result.add(project);
			}
			return result;
		} else if (version.ordinal() >= ProtocolVersion.V5_0_4.ordinal()) {
			List<ProjectV1> result = new ArrayList<>();
			for (BaseProject baseProject : projects) {
				ProjectV1 project = new ProjectV1();
				project.setCode(baseProject.getCode());
				project.setSyncUser(user.getEmail());
				result.add(project);
			}
			return result;
		} else {
			List<String> result = new ArrayList<>();
			for (BaseProject project : projects) {
				result.add(project.getCode());
			}
			return result;
		}
	}

	/**
	 * Runs the list all query.
	 *
	 * @return the project codes
	 */
	public List<String> listAll() {
		List<String> result = new ArrayList<>();
		ProjectRepository projectRepo = AppComponents.get(
				ProjectRepository.class);
		List<BaseProject> projects = projectRepo.getProjects();
		for (BaseProject project : projects) {
			result.add(project.getCode());
		}
		Collections.sort(result);
		return result;
	}

	/**
	 * Runs the query checkProject.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param user the user who is accessing the project
	 * @param subject the user ID or email address of the user that is accessed
	 * @throws HttpException if the request is invalid
	 * @throws DatabaseException if a database error occurs
	 */
	public Object checkProject(ProtocolVersion version, Database authDb,
			User user, String subject) throws HttpException, DatabaseException {
		if (subject != null && !subject.isEmpty())
			User.findAccessibleUser(version, subject, authDb, user);
		return null;
	}

	/**
	 * Returns the projects that the specified user can access. If the user is
	 * an admin, that is all projects. Otherwise it checks the {@link
	 * UserProjectTable UserProjectTable}. The projects will be sorted by code.
	 *
	 * @param authDb the authentication database
	 * @param user the user
	 * @return the projects
	 * @throws DatabaseException if a database error occurs
	 */
	private static List<BaseProject> getUserProjects(Database authDb, User user)
			throws DatabaseException {
		List<String> projectCodes = user.findProjects(authDb);
		List<BaseProject> projects = new ArrayList<>();
		ProjectRepository projectRepo = AppComponents.get(
				ProjectRepository.class);
		for (String code : projectCodes) {
			BaseProject project = projectRepo.findProjectByCode(code);
			if (project != null)
				projects.add(project);
		}
		return projects;
	}

	/**
	 * Runs the query addUser.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param user the user who is logged in
	 * @param projectCode the code of the project to which the user should be
	 * added
	 * @param subject the "user" parameter containing the user ID or email
	 * address of the user to add. This is preferred to the "email" parameter.
	 * @param compatEmail the "email" parameter containing the email address of
	 * the user to add
	 * @param asRole the role as which the user should be added to the project
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Object addUser(ProtocolVersion version, Database authDb,
			User user, String projectCode, String subject, String compatEmail,
			String asRole) throws HttpException, Exception {
		User userToAdd;
		if (compatEmail != null && !compatEmail.isEmpty()) {
			userToAdd = User.findAccessibleUserByEmail(compatEmail, authDb,
					user);
		} else {
			userToAdd = User.findAccessibleUser(version, subject, authDb, user);
		}
		BaseProject project;
		if (user.getUserid().equals(userToAdd.getUserid()))
			project = findProject(projectCode);
		else
			project = findUserProject(projectCode, authDb, user);
		Role asRoleEnum;
		try {
			asRoleEnum = TypeConversion.getEnum(asRole, Role.class);
		} catch (ParseException ex) {
			String msg = "Invalid role: " + asRole;
			HttpError error = new HttpError(ErrorCode.INVALID_INPUT, msg);
			error.addFieldError(new HttpFieldError("asRole", msg));
			throw new BadRequestException(error);
		}
		int userRoleIndex = userToAdd.getRole().ordinal();
		int asRoleIndex = asRoleEnum.ordinal();
		if (asRoleIndex < userRoleIndex) {
			throw new ForbiddenException(
					"Can't add users to project as higher role than their own role");
		}
		DatabaseCriteria criteria = new DatabaseCriteria.And(
				new DatabaseCriteria.Equal("user", userToAdd.getUserid()),
				new DatabaseCriteria.Equal("projectCode", project.getCode()),
				new DatabaseCriteria.Equal("asRole", asRoleEnum.toString()));
		UserProject userProject = authDb.selectOne(new UserProjectTable(),
				criteria, null);
		if (userProject != null)
			return null;
		try {
			project.validateAddUser(user, userToAdd, authDb);
		} catch (ValidationException ex) {
			throw new ForbiddenException(ex.getMessage());
		}
		userProject = new UserProject();
		userProject.setUser(userToAdd.getUserid());
		userProject.setProjectCode(project.getCode());
		userProject.setAsRole(asRoleEnum);
		authDb.insert(UserProjectTable.NAME, userProject);
		UserListenerRepository.getInstance().notifyUserAddedToProject(userToAdd,
				projectCode, asRoleEnum);
		return null;
	}

	public Object removeUser(ProtocolVersion version, Database authDb,
			User user, String projectCode, String subject, String compatEmail,
			String asRole)
			throws HttpException, Exception {
		User removeUser;
		if (compatEmail != null && !compatEmail.isEmpty()) {
			removeUser = User.findAccessibleUserByEmail(compatEmail, authDb,
					user);
		} else {
			removeUser = User.findAccessibleUser(version, subject, authDb,
					user);
		}
		BaseProject project;
		if (user.getUserid().equals(removeUser.getUserid()))
			project = findProject(projectCode);
		else
			project = findUserProject(projectCode, authDb, user);
		Role asRoleEnum = null;
		if (asRole != null && !asRole.isEmpty()) {
			try {
				asRoleEnum = TypeConversion.getEnum(asRole, Role.class);
			} catch (ParseException ex) {
				String msg = "Invalid role: " + asRole;
				HttpError error = new HttpError(ErrorCode.INVALID_INPUT, msg);
				error.addFieldError(new HttpFieldError("asRole", msg));
				throw new BadRequestException(error);
			}
		}
		DatabaseCriteria criteria = new DatabaseCriteria.And(
				new DatabaseCriteria.Equal("user", removeUser.getUserid()),
				new DatabaseCriteria.Equal("projectCode", project.getCode())
		);
		List<UserProject> userProjects = authDb.select(
				new UserProjectTable(), criteria, 0, null);
		List<Role> oldRoles = new ArrayList<>();
		for (UserProject userProject : userProjects) {
			oldRoles.add(userProject.getAsRole());
		}
		List<Role> removedRoles;
		if (asRoleEnum == null)
			removedRoles = oldRoles;
		else if (oldRoles.contains(asRoleEnum))
			removedRoles = Collections.singletonList(asRoleEnum);
		else
			removedRoles = new ArrayList<>();
		if (!removedRoles.isEmpty()) {
			List<DatabaseCriteria> andList = new ArrayList<>();
			andList.add(new DatabaseCriteria.Equal("user",
					removeUser.getUserid()));
			andList.add(new DatabaseCriteria.Equal("projectCode",
					project.getCode()));
			if (asRoleEnum != null) {
				andList.add(new DatabaseCriteria.Equal("asRole",
						asRoleEnum.toString()));
			}
			criteria = new DatabaseCriteria.And(andList.toArray(
					new DatabaseCriteria[0]));
			authDb.delete(new UserProjectTable(), criteria);
			for (Role removedRole : removedRoles) {
				UserListenerRepository.getInstance()
						.notifyUserRemovedFromProject(removeUser, projectCode,
								removedRole);
			}
		}
		criteria = new DatabaseCriteria.And(
				new DatabaseCriteria.Equal("project", project.getCode()),
				new DatabaseCriteria.Equal("subject", removeUser.getUserid())
		);
		authDb.delete(new ProjectUserAccessTable(), criteria);
		criteria = new DatabaseCriteria.And(
				new DatabaseCriteria.Equal("user", removeUser.getUserid()),
				new DatabaseCriteria.Equal("project", project.getCode())
		);
		authDb.delete(new SyncPushRegistrationTable(), criteria);
		PushNotificationService pushService = AppComponents.get(
				PushNotificationService.class);
		pushService.removeUserProject(user.getUserid(), project.getCode());
		return null;
	}

	public List<DatabaseObject> getUsers(ProtocolVersion version,
			Database authDb, User user, BaseProject project, String forUserid,
			String roleStr, String includeInactiveStr) throws HttpException,
			Exception {
		UserController.GetSubjectListInput input =
				UserController.getSubjectListInput(version, authDb, user,
				forUserid, roleStr, includeInactiveStr);
		List<User> users = User.findProjectUsers(project.getCode(), authDb,
				input.getForUser(), input.getRole(), input.isIncludeInactive());
		return UserController.getCompatUserList(version, users);
	}

	public List<DatabaseObject> getSubjects(ProtocolVersion version,
			Database authDb, User user, BaseProject project, String forUserid,
			String includeInactiveStr) throws HttpException, Exception {
		User forUser = User.findAccessibleUser(version, forUserid, authDb,
				user);
		if (!forUser.getUserid().equals(user.getUserid()) &&
				user.getRole() != Role.ADMIN) {
			throw new ForbiddenException();
		}
		boolean includeInactive;
		try {
			includeInactive = TypeConversion.getBoolean(includeInactiveStr);
		} catch (ParseException ex) {
			throw BadRequestException.withInvalidInput(
					new HttpFieldError("includeInactive", ex.getMessage()));
		}
		List<User> subjects = User.findProjectUsers(project.getCode(), authDb,
				forUser, Role.PATIENT, includeInactive);
		return UserController.getCompatUserList(version, subjects);
	}

	public List<DetoxLinkedSubjectSummary> getDetoxSubjects(
			ProtocolVersion version, Database authDb, User user,
			BaseProject project, String includeInactiveStr)
			throws HttpException, Exception {
		if (user.getRole() == Role.PATIENT)
			throw new ForbiddenException();
		boolean includeInactive;
		try {
			includeInactive = TypeConversion.getBoolean(includeInactiveStr);
		} catch (ParseException ex) {
			throw BadRequestException.withInvalidInput(
					new HttpFieldError("includeInactive", ex.getMessage()));
		}
		List<User> projectSubjects = User.findProjectUsers(project.getCode(),
				authDb, user, Role.PATIENT, includeInactive);
		String databaseName = DatabaseLoader.getProjectDatabaseName(
				project.getCode());
		List<DetoxLinkedSubjectSummary> result = new ArrayList<>();
		for (User subjectUser : projectSubjects) {
			try {
				User.findAccessibleProjectUserByUserid(subjectUser.getUserid(),
						project.getCode(), DetoxTaskConfigurationTable.NAME,
						AccessMode.W, authDb, user);
			} catch (ForbiddenException ex) {
				continue;
			}
			DetoxOnsLookup lookup = DetoxOnsLookup.findBySsaId(authDb,
					subjectUser.getUserid());
			if (lookup == null)
				continue;
			int pushDeviceCount = countTaskPushRegistrations(authDb,
					subjectUser.getUserid(), project.getCode(), databaseName);
			DetoxLinkedSubjectSummary summary =
					new DetoxLinkedSubjectSummary();
			summary.setUserId(subjectUser.getUserid());
			summary.setDisplayName(getUserDisplayName(subjectUser));
			summary.setActive(subjectUser.isActive());
			summary.setOnsId(lookup.getOnsId());
			summary.setOnsInstance(lookup.getOnsInstance());
			summary.setPushReady(pushDeviceCount > 0);
			summary.setPushRegisteredDeviceCount(pushDeviceCount);
			result.add(summary);
		}
		result.sort(Comparator.comparing(DetoxLinkedSubjectSummary::getDisplayName,
				String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	public DetoxTaskRefreshRequestResult createDetoxTaskRefreshRequest(
			ProtocolVersion version, Database authDb, Database db, User user,
			BaseProject project, String subject)
			throws HttpException, Exception {
		if (user.getRole() == Role.PATIENT)
			throw new ForbiddenException();
		ProjectUserAccess userAccess = User.findAccessibleProjectUser(version,
				subject, project.getCode(), DetoxTaskRefreshRequestTable.NAME,
				AccessMode.W, authDb, user);
		userAccess.checkMatchesRange(null, null);
		User subjectUser = userAccess.getUser();
		String requestToken = UUID.randomUUID().toString();
		DetoxTaskRefreshRequest refreshRequest = new DetoxTaskRefreshRequest();
		refreshRequest.setUser(subjectUser.getUserid());
		refreshRequest.updateDateTime(DateTimeUtils.nowMs(subjectUser.toTimeZone()));
		refreshRequest.setRequestToken(requestToken);
		refreshRequest.setRequestedByUser(user.getUserid());
		db.insert(DetoxTaskRefreshRequestTable.NAME, refreshRequest);
		DetoxTaskRefreshRequestResult result =
				new DetoxTaskRefreshRequestResult();
		result.setRequestToken(requestToken);
		return result;
	}

	public DetoxDigitalGuideDialogueOptions getDetoxDigitalGuideDialogues(
			ProtocolVersion version, Database authDb, Database db, User user,
			BaseProject project, String subject)
			throws HttpException, Exception {
		ProjectUserAccess userAccess = User.findAccessibleProjectUser(version,
				subject, project.getCode(),
				DetoxDigitalGuideDialogueCapabilityTable.NAME, AccessMode.R,
				authDb, user);
		userAccess.checkMatchesRange(null, null);
		User subjectUser = userAccess.getUser();
		DatabaseCriteria criteria = new DatabaseCriteria.Equal("user",
				subjectUser.getUserid());
		DatabaseSort[] sort = new DatabaseSort[] {
				new DatabaseSort("utcTime", false),
				new DatabaseSort("id", false)
		};
		List<DetoxDigitalGuideDialogueCapability> records = db.select(
				new DetoxDigitalGuideDialogueCapabilityTable(), criteria, 0,
				sort);
		Map<String,DetoxDigitalGuideDialogueCapability> latestByDevice =
				new LinkedHashMap<>();
		DetoxDigitalGuideDialogueCapability latestRecord = null;
		for (DetoxDigitalGuideDialogueCapability record : records) {
			String sourceDeviceId = record.getSourceDeviceId();
			if (sourceDeviceId == null || sourceDeviceId.isBlank())
				continue;
			if (!latestByDevice.containsKey(sourceDeviceId))
				latestByDevice.put(sourceDeviceId, record);
			if (latestRecord == null)
				latestRecord = record;
		}

		DetoxDigitalGuideDialogueOptions result =
				new DetoxDigitalGuideDialogueOptions();
		List<String> defaultDialogueIds =
				DetoxDigitalGuideDialogueRegistry.getDefaultDialogueIds();
		result.setDefaultDialogueIds(defaultDialogueIds);
		result.setEffectiveDialogueIds(defaultDialogueIds);
		if (latestByDevice.isEmpty())
			return result;

		List<List<String>> dialogueIdLists = new ArrayList<>();
		List<String> sourceDeviceIds = new ArrayList<>(latestByDevice.keySet());
		Collections.sort(sourceDeviceIds);
		for (DetoxDigitalGuideDialogueCapability capability :
				latestByDevice.values()) {
			dialogueIdLists.add(capability.getDialogueIdsList());
		}
		List<String> compatibleDialogueIds =
				DetoxDigitalGuideDialogueRegistry.intersectDialogueIds(
						dialogueIdLists);
		result.setDeviceReported(true);
		result.setDeviceSnapshotCount(latestByDevice.size());
		result.setSourceDeviceIds(sourceDeviceIds);
		result.setReportedDialogueIds(compatibleDialogueIds);
		result.setEffectiveDialogueIds(compatibleDialogueIds);
		if (latestRecord != null) {
			result.setLanguage(latestRecord.getLanguage());
			result.setAppVersion(latestRecord.getAppVersion());
			result.setReportedAt(DateTimeUtils.ZONED_FORMAT.format(
					ZonedDateTime.ofInstant(
							Instant.ofEpochMilli(latestRecord.getUtcTime()),
							ZoneOffset.UTC)));
		}
		return result;
	}

	/**
	 * Runs the query registerWatchSubjects.
	 *
	 * @param authDb the authentication database
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @return the registration ID
	 * @throws HttpException if the request is invalid
	 * @throws DatabaseException if a database error occurs
	 */
	public String registerWatchSubjects(Database authDb, User user,
			BaseProject project, boolean reset) throws HttpException,
			DatabaseException {
		return WatchSubjectListener.addRegistration(authDb, user,
				project.getCode(), reset);
	}

	/**
	 * Runs the query watchSubjects.
	 *
	 * @param request the HTTP request
	 * @param response the HTTP response
	 * @param versionName the protocol version
	 * @param project the project code
	 * @param id the registration ID
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public void watchSubjects(final HttpServletRequest request,
			HttpServletResponse response, String versionName, String project,
			String id) throws HttpException, Exception {
		long queryStart = System.currentTimeMillis();
		long queryEnd = queryStart + HANGING_GET_TIMEOUT;
		// verify authentication and input
		WatchSubjectListener listener = QueryRunner.runProjectQuery(
				(version, authDb, projectDb, user, baseProject) ->
						parseWatchSubjectInput(authDb, id, user, baseProject),
				versionName, project, request, response);
		// watch listener
		Object currentWatch = new Object();
		final Object lock = listener.getLock();
		synchronized (lock) {
			listener.setCurrentWatch(currentWatch);
			long now = System.currentTimeMillis();
			while (now < queryEnd &&
					listener.getCurrentWatch() == currentWatch &&
					listener.getSubjectEvents().isEmpty()) {
				lock.wait(queryEnd - now);
				now = System.currentTimeMillis();
			}
			List<SubjectEvent> events;
			if (listener.getCurrentWatch() == currentWatch)
				events = listener.getSubjectEvents();
			else
				events = new ArrayList<>();
			response.setContentType("application/json;charset=UTF-8");
			try (Writer writer = new OutputStreamWriter(
					response.getOutputStream(), StandardCharsets.UTF_8)) {
				ObjectMapper mapper = new ObjectMapper();
				String json = mapper.writeValueAsString(events);
				writer.write(json);
				writer.flush();
			}
			listener.clearSubjectEvents();
		}
	}

	/**
	 * Runs the query unregisterWatchSubjects.
	 *
	 * @param authDb the authentication database
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param regId the registration ID
	 * @throws HttpException if the request is invalid
	 * @throws DatabaseException if a database error occurs
	 */
	public Object unregisterWatchSubjects(Database authDb, User user,
			BaseProject project, String regId) throws HttpException,
			DatabaseException {
		WatchSubjectListener listener;
		try {
			listener = findWatchSubjectListener(regId, user, project);
		} catch (NotFoundException ex) {
			return null;
		}
		WatchSubjectListener.removeRegistration(authDb, listener);
		return null;
	}

	private WatchSubjectListener parseWatchSubjectInput(Database authDb,
			String regId, User user, BaseProject project) throws HttpException,
			DatabaseException {
		WatchSubjectListener listener = findWatchSubjectListener(regId, user,
				project);
		if (!WatchSubjectListener.setRegistrationWatchTime(authDb,
				listener.getRegistration())) {
			throw new NotFoundException();
		}
		return listener;
	}

	private WatchSubjectListener findWatchSubjectListener(String regId,
			User user, BaseProject project) throws NotFoundException {
		WatchSubjectListener listener = WatchSubjectListener.findListener(
				regId);
		if (listener == null)
			throw new NotFoundException();
		WatchSubjectRegistration reg = listener.getRegistration();
		// check if registration properties correspond to parameters
		if (!reg.getUser().equals(user.getUserid()))
			throw new NotFoundException();
		if (!reg.getProject().equals(project.getCode()))
			throw new NotFoundException();
		return listener;
	}

	/**
	 * Runs the query getTableList.
	 *
	 * @param project the project
	 * @return the names of the database tables
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public List<String> getTableList(BaseProject project)
			throws HttpException, Exception {
		return project.getDatabaseTableNames();
	}

	/**
	 * Runs the query getTableSpec.
	 *
	 * @param project the project
	 * @param table the table name
	 * @return the table specification
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public TableSpec getTableSpec(BaseProject project, String table)
			throws HttpException, Exception {
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		return TableSpec.fromDatabaseTableDef(tableDef);
	}

	/**
	 * Runs the query getRecords or getRecordsWithFilter.
	 *
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the table
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @param start the start time or an empty string or null
	 * @param end the end time or an empty string or null
	 * @param request if getRecordsWithFilter was called, this is the request
	 * with the filter in the content. Otherwise it's null.
	 * @param response the HTTP response to which the records should be written
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Object getRecords(ProtocolVersion version, Database authDb,
			Database db, User user, BaseProject project, String table,
			String subject, String start, String end,
			HttpServletRequest request, HttpServletResponse response)
			throws HttpException, Exception {
		List<? extends DatabaseObject> records = getRecords(version, authDb,
				db, user, project, table, subject, start, end, request);
		response.setContentType("application/json");
		try (Writer writer = new OutputStreamWriter(response.getOutputStream(),
				StandardCharsets.UTF_8)) {
			writer.write("[");
			DatabaseObjectMapper dbMapper = new DatabaseObjectMapper();
			ObjectMapper jsonMapper = new ObjectMapper();
			boolean first = true;
			for (DatabaseObject record : records) {
				if (!first)
					writer.write(",");
				else
					first = false;
				Map<String,Object> map = dbMapper.objectToMap(record, true);
				String json = jsonMapper.writeValueAsString(map);
				writer.write(json);
			}
			writer.write("]");
		}
		return null;
	}

	public Object getDetoxSubjectRecords(ProtocolVersion version,
			Database authDb, Database db, User user, BaseProject project,
			String table, String subject, String start, String end,
			HttpServletRequest request, HttpServletResponse response)
			throws HttpException, Exception {
		List<? extends DatabaseObject> records = getRecords(version, authDb,
				db, user, project, table, subject, start, end, request);
		response.setContentType("application/json");
		try (Writer writer = new OutputStreamWriter(response.getOutputStream(),
				StandardCharsets.UTF_8)) {
			writer.write("[");
			DatabaseObjectMapper dbMapper = new DatabaseObjectMapper();
			ObjectMapper jsonMapper = new ObjectMapper();
			boolean first = true;
			for (DatabaseObject record : records) {
				if (!first)
					writer.write(",");
				else
					first = false;
				Map<String,Object> map = dbMapper.objectToMap(record, true);
				map = addTaskSyncCompatMetadata(map);
				String json = jsonMapper.writeValueAsString(map);
				writer.write(json);
			}
			writer.write("]");
		}
		return null;
	}

	public static List<? extends DatabaseObject> getRecords(
			ProtocolVersion version, Database authDb, Database db, User user,
			BaseProject project, String table, String subject, String start,
			String end, HttpServletRequest request) throws HttpException,
			Exception {
		TableSelectCriteria tableCriteria = getTableSelectCriteria(version,
				authDb, user, project, table, subject, start, end, request,
				Arrays.asList("filter", "sort", "limit"), true);
		// TODO stream to response, add Database.selectCursor()
		List<? extends DatabaseObject> result = db.select(
				tableCriteria.tableDef, tableCriteria.criteria,
				tableCriteria.limit, tableCriteria.sort);
		setCompatUser(version, subject, tableCriteria.subjectUser, result);
		return result;
	}

	private static void setCompatUser(ProtocolVersion version,
			String subjectName, User subjectUser,
			List<? extends DatabaseObject> records) {
		if (version.ordinal() >= ProtocolVersion.V6_0_0.ordinal())
			return;
		for (DatabaseObject record : records) {
			List<String> fields = DatabaseFieldScanner.getDatabaseFieldNames(
					record.getClass());
			if (!fields.contains("user"))
				return;
			String compatUser;
			if (subjectUser.getUserid().contains("@"))
				compatUser = subjectUser.getUserid();
			else if (subjectName != null && !subjectName.isEmpty())
				compatUser = subjectName;
			else
				compatUser = subjectUser.getEmail();
			PropertyWriter.writeProperty(record, "user", compatUser);
		}
	}

	private Map<String,Object> addTaskSyncCompatMetadata(Map<String,Object> map) {
		Map<String,Object> result = new LinkedHashMap<>(map);
		Object id = result.get("id");
		if (id != null) {
			result.putIfAbsent("_id", id);
			result.putIfAbsent("recordId", id);
		}
		String createdAt = utcTimeToIsoString(result.get("utcTime"));
		if (createdAt != null) {
			result.putIfAbsent("createdAt", createdAt);
			result.putIfAbsent("timestamp", createdAt);
			result.putIfAbsent("_created", createdAt);
		}
		return result;
	}

	private String utcTimeToIsoString(Object utcTime) {
		if (utcTime == null)
			return null;
		try {
			long epochMillis;
			if (utcTime instanceof Number) {
				epochMillis = ((Number)utcTime).longValue();
			} else {
				String s = utcTime.toString().trim();
				if (s.isEmpty())
					return null;
				epochMillis = Long.parseLong(s);
			}
			return Instant.ofEpochMilli(epochMillis).toString();
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Runs the query getRecord.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the table
	 * @param recordId the record ID
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Map<?,?> getRecord(ProtocolVersion version, Database authDb,
			Database db, User user, BaseProject project, String table,
			String recordId, String subject) throws HttpException, Exception {
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		DatabaseObject record;
		DatabaseCache cache = DatabaseCache.getInstance();
		List<String> fields = cache.getTableFields(db, table);
		if (fields.contains("user")) {
			ProjectUserAccess userAccess = User.findAccessibleProjectUser(
					version, subject, project.getCode(), table, AccessMode.R,
					authDb, user);
			User subjectUser = userAccess.getUser();
			DatabaseCriteria criteria = new DatabaseCriteria.And(
					new DatabaseCriteria.Equal("user", subjectUser.getUserid()),
					new DatabaseCriteria.Equal("id", recordId));
			record = db.selectOne(tableDef, criteria, null);
			if (record == null) {
				throw new NotFoundException(String.format(
						"Record with ID \"%s\" not found for project %s, table %s, user %s",
						recordId, project.getCode(), table,
						subjectUser.getUserid(version)));
			}
			userAccess.checkMatchesRange(record);
		} else {
			checkPermissionWriteResourceTable(authDb, user, project.getCode(),
					table);
			DatabaseCriteria criteria = new DatabaseCriteria.Equal(
					"id", recordId);
			record = db.selectOne(tableDef, criteria, null);
			if (record == null) {
				throw new NotFoundException(String.format(
						"Record with ID \"%s\" not found for project %s, table %s",
						recordId, project.getCode(), table));
			}
		}
		DatabaseObjectMapper mapper = new DatabaseObjectMapper();
		return mapper.objectToMap(record, true);
	}

	/**
	 * Result of {@link
	 * #getTableSelectCriteria(ProtocolVersion, Database, User, BaseProject, String, String, String, String, HttpServletRequest, List, boolean)
	 * getTableSelectCriteria()}.
	 */
	private static class TableSelectCriteria {
		public DatabaseTableDef<?> tableDef;
		public DatabaseCriteria criteria;
		public DatabaseSort[] sort;
		public int limit;
		public User subjectUser;

		public TableSelectCriteria(DatabaseTableDef<?> tableDef,
				DatabaseCriteria criteria, DatabaseSort[] sort, int limit,
				User subjectUser) {
			this.tableDef = tableDef;
			this.criteria = criteria;
			this.sort = sort;
			this.limit = limit;
			this.subjectUser = subjectUser;
		}
	}

	/**
	 * Returns a table definition and database criteria for a select or delete
	 * query. The specified table, subject, start and end come from user input.
	 * This method will validate them and throw an HttpException in case of
	 * invalid input.
	 *
	 * @param authDb the authentication database
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the table name
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @param start the start time as an ISO date/time string, or the start date
	 * as an SQL date string, or an empty string or null
	 * @param end the end time as an ISO date/time string, or the end date as an
	 * SQL date string, or an empty string or null
	 * @param request if a select/delete query was called with a filter, this is
	 * the request with the filter in the content. Otherwise it's null.
	 * @param allowedContentParams the allowed parameters in the request body.
	 * This should be a set of "filter", "sort" and "limit".
	 * @param isRead true if the select criteria will be used for a select
	 * query, which does not require write permission for resource tables; false
	 * if the select criteria will be used for a delete query
	 * @return the table definition and database criteria
	 * @throws HttpException if the request is invalid
	 * @throws DatabaseException if a database error occurs
	 * @throws IOException if an error occurs while reading the request content
	 */
	private static TableSelectCriteria getTableSelectCriteria(
			ProtocolVersion version, Database authDb, User user,
			BaseProject project, String table, String subject, String start,
			String end, HttpServletRequest request,
			List<String> allowedContentParams, boolean isRead)
			throws HttpException, DatabaseException, IOException {
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		List<String> fields = DatabaseFieldScanner.getDatabaseFieldNames(
				tableDef.getDataClass());
		boolean isUserTable = fields.contains("user");
		boolean isTimeTable = Sample.class.isAssignableFrom(
				tableDef.getDataClass());
		boolean isUtcTable = UTCSample.class.isAssignableFrom(
				tableDef.getDataClass());
		List<DatabaseCriteria> andCriteria = new ArrayList<>();
		StringBuilder errorBuilder = new StringBuilder();
		List<HttpFieldError> fieldErrors = new ArrayList<>();
		ParameterParser paramParser = new ParameterParser();
		Object startObj = null;
		if (isTimeTable && start != null && !start.isEmpty()) {
			try {
				startObj = paramParser.parseSelectDateTime(isUtcTable, start);
			} catch (ParseException ex) {
				if (!errorBuilder.isEmpty())
					errorBuilder.append("\n");
				errorBuilder.append("Invalid value for parameter \"start\": " +
						ex.getMessage());
				fieldErrors.add(new HttpFieldError("start", ex.getMessage()));
			}
		}
		LocalDateTime startTime = null;
		if (startObj instanceof ZonedDateTime && isUtcTable) {
			long startMillis = ((ZonedDateTime)startObj).toInstant()
					.toEpochMilli();
			startTime = ((ZonedDateTime)startObj).toLocalDateTime();
			andCriteria.add(new DatabaseCriteria.GreaterEqual(
					"utcTime", startMillis));
		} else if (startObj instanceof ZonedDateTime) {
			String startTimeStr = ((ZonedDateTime)startObj).toLocalDateTime()
					.format(Sample.LOCAL_TIME_FORMAT);
			startTime = ((ZonedDateTime)startObj).toLocalDateTime();
			andCriteria.add(new DatabaseCriteria.GreaterEqual(
					"localTime", startTimeStr));
		} else if (startObj != null) {
			String startTimeStr = ((LocalDateTime)startObj).format(
					Sample.LOCAL_TIME_FORMAT);
			startTime = (LocalDateTime)startObj;
			andCriteria.add(new DatabaseCriteria.GreaterEqual(
					"localTime", startTimeStr));
		}
		Object endObj = null;
		if (isTimeTable && end != null && !end.isEmpty()) {
			try {
				endObj = paramParser.parseSelectDateTime(isUtcTable, end);
			} catch (ParseException ex) {
				if (!errorBuilder.isEmpty())
					errorBuilder.append("\n");
				errorBuilder.append("Invalid value for parameter \"end\": " +
						ex.getMessage());
				fieldErrors.add(new HttpFieldError("end", ex.getMessage()));
			}
		}
		LocalDateTime endTime = null;
		if (endObj instanceof ZonedDateTime && isUtcTable) {
			long endMillis = ((ZonedDateTime)endObj).toInstant().toEpochMilli();
			endTime = ((ZonedDateTime)endObj).toLocalDateTime();
			andCriteria.add(new DatabaseCriteria.LessThan(
					"utcTime", endMillis));
		} else if (endObj instanceof ZonedDateTime) {
			String endTimeStr = ((ZonedDateTime)endObj).toLocalDateTime()
					.format(Sample.LOCAL_TIME_FORMAT);
			endTime = ((ZonedDateTime)endObj).toLocalDateTime();
			andCriteria.add(new DatabaseCriteria.LessThan(
					"localTime", endTimeStr));
		} else if (endObj != null) {
			String endTimeStr = ((LocalDateTime)endObj).format(
					Sample.LOCAL_TIME_FORMAT);
			endTime = (LocalDateTime)endObj;
			andCriteria.add(new DatabaseCriteria.LessThan(
					"localTime", endTimeStr));
		}
		if (!fieldErrors.isEmpty()) {
			HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
					errorBuilder.toString());
			error.setFieldErrors(fieldErrors);
			throw new BadRequestException(error);
		}
		User subjectUser = null;
		if (isUserTable) {
			ProjectUserAccess userAccess = User.findAccessibleProjectUser(
					version, subject, project.getCode(), table, AccessMode.R,
					authDb, user);
			userAccess.checkMatchesRange(startTime, endTime);
			subjectUser = userAccess.getUser();
			andCriteria.add(0, new DatabaseCriteria.Equal("user",
					subjectUser.getUserid()));
		} else if (!isRead) {
			checkPermissionWriteResourceTable(authDb, user, project.getCode(),
					table);
		}
		String content = null;
		if (request != null)
			content = HttpContentReader.readString(request);
		Object filterObj = null;
		Object sortObj = null;
		Object limitObj = null;
		if (content != null && !content.isEmpty()) {
			Map<?,?> map;
			try {
				map = JsonMapper.parse(content, Map.class);
			} catch (ParseException ex) {
				HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
						"Content is not a valid JSON object: " +
								ex.getMessage());
				throw new BadRequestException(error);
			}
			List<String> invalidKeys = new ArrayList<>();
			for (Object key : map.keySet()) {
				if (!allowedContentParams.contains((String)key))
					invalidKeys.add(key == null ? "null" : key.toString());
			}
			if (!invalidKeys.isEmpty()) {
				HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
						"Invalid parameters in JSON content: " +
								String.join(", ", invalidKeys));
				throw new BadRequestException(error);
			}
			filterObj = map.get("filter");
			sortObj = map.get("sort");
			limitObj = map.get("limit");
		}
		SelectFilterParser parser = new SelectFilterParser(tableDef);
		if (filterObj != null) {
			if (!(filterObj instanceof Map<?,?> map)) {
				throw new BadRequestException(ErrorCode.INVALID_INPUT,
						"Filter is not a JSON object");
			}
			try {
				andCriteria.add(parser.parseFilter(map));
			} catch (ParseException ex) {
				HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
						"Invalid filter: " + ex.getMessage());
				throw new BadRequestException(error);
			}
		}
		DatabaseSort[] sort = null;
		if (sortObj != null) {
			try {
				sort = parser.parseSort(sortObj);
			} catch (ParseException ex) {
				HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
						"Invalid sort: " + ex.getMessage());
				throw new BadRequestException(error);
			}
		}
		int limit = 0;
		if (limitObj != null) {
			try {
				limit = JsonMapper.convert(limitObj, Integer.class);
			} catch (ParseException ex) {
				HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
						"Invalid limit: " + ex.getMessage());
				throw new BadRequestException(error);
			}
		}
		DatabaseCriteria criteria = null;
		if (!andCriteria.isEmpty()) {
			criteria = new DatabaseCriteria.And(andCriteria.toArray(
					new DatabaseCriteria[0]));
		}
		Class<?> dataClass = tableDef.getDataClass();
		if (sort == null) {
			String sortField;
			if (UTCSample.class.isAssignableFrom(dataClass) &&
					criteria.containsColumn("utcTime")) {
				sortField = "utcTime";
			} else if (Sample.class.isAssignableFrom(dataClass) &&
					criteria.containsColumn("localTime")) {
				sortField = "localTime";
			} else if (UTCSample.class.isAssignableFrom(dataClass)) {
				sortField = "utcTime";
			} else if (Sample.class.isAssignableFrom(dataClass)) {
				sortField = "localTime";
			} else {
				sortField = "id";
			}
			sort = new DatabaseSort[] { new DatabaseSort(sortField, true) };
		}
		return new TableSelectCriteria(tableDef, criteria, sort, limit,
				subjectUser);
	}

	/**
	 * Runs the query insertRecords.
	 *
	 * @param version the protocol version
	 * @param request the HTTP request
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project code
	 * @param table the name of the table
	 * @param subject the user ID or email address of the subject or null
	 * @return the record IDs
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public List<String> insertRecords(ProtocolVersion version,
			HttpServletRequest request, Database authDb, Database db, User user,
			BaseProject project, String table, String subject)
			throws HttpException, Exception {
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		ensureDetoxTableSupportsInsert(table);
		User subjectUser = null;
		DatabaseCache cache = DatabaseCache.getInstance();
		List<String> fields = cache.getTableFields(db, table);
		if (fields.contains("user")) {
			ProjectUserAccess userAccess = User.findAccessibleProjectUser(
					version, subject, project.getCode(), table, AccessMode.W,
					authDb, user);
			userAccess.checkMatchesRange(null, null);
			subjectUser = userAccess.getUser();
		} else {
			checkPermissionWriteResourceTable(authDb, user, project.getCode(),
					table);
		}
		List<String> idList = new ArrayList<>();
		InputStream input = request.getInputStream();
		JsonObjectStreamReader jsonReader = null;
		try {
			jsonReader = new JsonObjectStreamReader(input);
			jsonReader.readToken(JsonAtomicToken.Type.START_LIST);
			while (jsonReader.getToken().getType() !=
					JsonAtomicToken.Type.END_LIST) {
				insertRecordBatch(version, user, jsonReader, db, tableDef,
						subjectUser, idList);
			}
			return idList;
		} catch (JsonParseException ex) {
			String msg = "Invalid records: " + ex.getMessage();
			HttpError error = new HttpError(ErrorCode.INVALID_INPUT, msg);
			throw new BadRequestException(error);
		} finally {
			if (jsonReader != null)
				jsonReader.close();
			else
				input.close();
		}
	}

	private static void checkPermissionWriteResourceTable(Database authDb,
			User user, String project, String table) throws HttpException,
			DatabaseException {
		if (user.getRole() == Role.ADMIN)
			return;
		PermissionManager permMgr = PermissionManager.getInstance();
		Map<String,Object> permParams = new LinkedHashMap<>();
		permParams.put("project", project);
		permParams.put("table", table);
		permMgr.checkPermission(authDb, user.getUserid(),
				PermissionName.PERMISSION_WRITE_RESOURCE_TABLE, permParams);
	}

	/**
	 * Runs the query updateRecord.
	 *
	 * @param version the protocol version
	 * @param request the HTTP request
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project code
	 * @param table the name of the table
	 * @param recordId the record ID that is updated
	 * @param subject the user ID or email address of the subject or null
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Object updateRecord(ProtocolVersion version,
			HttpServletRequest request, Database authDb, Database db, User user,
			BaseProject project, String table, String recordId, String subject)
			throws HttpException, Exception {
		ensureDetoxTableSupportsNoMutation(table, "update");
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		ProjectUserAccess userAccess = null;
		User subjectUser = null;
		DatabaseObject record;
		DatabaseCache cache = DatabaseCache.getInstance();
		List<String> fields = cache.getTableFields(db, table);
		if (fields.contains("user")) {
			userAccess = User.findAccessibleProjectUser(version, subject,
					project.getCode(), table, AccessMode.W, authDb, user);
			subjectUser = userAccess.getUser();
			DatabaseCriteria criteria = new DatabaseCriteria.And(
					new DatabaseCriteria.Equal("user", subjectUser.getUserid()),
					new DatabaseCriteria.Equal("id", recordId));
			record = db.selectOne(tableDef, criteria, null);
			if (record == null) {
				throw new NotFoundException(String.format(
						"Record with ID \"%s\" not found for project %s, table %s, user %s",
						recordId, project.getCode(), table,
						subjectUser.getUserid(version)));
			}
			userAccess.checkMatchesRange(record);
		} else {
			checkPermissionWriteResourceTable(authDb, user, project.getCode(),
					table);
			DatabaseCriteria criteria = new DatabaseCriteria.Equal(
					"id", recordId);
			record = db.selectOne(tableDef, criteria, null);
			if (record == null) {
				throw new NotFoundException(String.format(
						"Record with ID \"%s\" not found for project %s, table %s",
						recordId, project.getCode(), table));
			}
		}
		Map<?,?> recordMap;
		try (InputStream input = request.getInputStream()) {
			ObjectMapper mapper = new ObjectMapper();
			recordMap = mapper.readValue(input, Map.class);
		}
		DatabaseObject updatedRecord = createUpdateRecord(version, user,
				recordMap, tableDef, recordId, subjectUser);
		if (userAccess != null)
			userAccess.checkMatchesRange(updatedRecord);
		db.update(table, updatedRecord);
		return null;
	}

	/**
	 * Runs the query deleteRecords or deleteRecordsWithFilter.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the table
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @param start the start time or an empty string or null
	 * @param end the end time or an empty string or null
	 * @param request if deleteRecordsWithFilter was called, this is the request
	 * with the filter in the content. Otherwise it's null.
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Object deleteRecords(ProtocolVersion version, Database authDb,
			Database db, User user, BaseProject project, String table,
			String subject, String start, String end,
			HttpServletRequest request) throws HttpException, Exception {
		ensureDetoxTableSupportsNoMutation(table, "delete");
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		TableSelectCriteria tableCriteria = getTableSelectCriteria(version,
				authDb, user, project, table, subject, start, end, request,
				Collections.singletonList("filter"), false);
		db.delete(tableCriteria.tableDef, tableCriteria.criteria);
		return null;
	}

	/**
	 * Runs the query deleteRecord.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the table
	 * @param recordId the record ID to delete
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Object deleteRecord(ProtocolVersion version, Database authDb,
			Database db, User user, BaseProject project, String table,
			String recordId, String subject) throws HttpException, Exception {
		ensureDetoxTableSupportsNoMutation(table, "delete");
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table, project.getCode()));
		}
		DatabaseCriteria criteria;
		DatabaseCache cache = DatabaseCache.getInstance();
		List<String> fields = cache.getTableFields(db, table);
		if (fields.contains("user")) {
			ProjectUserAccess userAccess = User.findAccessibleProjectUser(version,
					subject, project.getCode(), table, AccessMode.W, authDb, user);
			User subjectUser = userAccess.getUser();
			criteria = new DatabaseCriteria.And(
					new DatabaseCriteria.Equal("user", subjectUser.getUserid()),
					new DatabaseCriteria.Equal("id", recordId)
			);
			DatabaseObject record = db.selectOne(tableDef, criteria, null);
			if (record == null)
				return null;
			userAccess.checkMatchesRange(record);
		} else {
			checkPermissionWriteResourceTable(authDb, user, project.getCode(),
					table);
			criteria = new DatabaseCriteria.Equal("id", recordId);
			DatabaseObject record = db.selectOne(tableDef, criteria, null);
			if (record == null)
				return null;
		}
		db.delete(tableDef, criteria);
		return null;
	}

	/**
	 * Runs the query purgeTable.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the table
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Object purgeTable(ProtocolVersion version, Database authDb,
			Database db, User user, BaseProject project, String table,
			String subject) throws HttpException, Exception {
		ensureDetoxTableSupportsNoMutation(table, "purge");
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			throw new NotFoundException(String.format(
					"Table \"%s\" not found in project \"%s\"",
					table,
					project.getCode()));
		}
		DatabaseCache cache = DatabaseCache.getInstance();
		List<String> fields = cache.getTableFields(db, table);
		if (fields.contains("user")) {
			User subjectUser = User.findAccessibleUser(version, subject, authDb,
					user);
			db.purgeUserTable(tableDef.getName(), subjectUser.getUserid());
		} else {
			checkPermissionWriteResourceTable(authDb, user, project.getCode(),
					table);
			db.purgeResourceTable(table);
		}
		return null;
	}

	/**
	 * Runs the query getFirstRecord(WithFilter) or getLastRecord(WithFilter).
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param db the project database or null
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the table
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null
	 * @param start the start time as an ISO date/time string, or the start date
	 * as an SQL date string, or an empty string or null
	 * @param end the end time as an ISO date/time string, or the end date as an
	 * SQL date string, or an empty string or null
	 * @param getFirst true to get the first record, false to get the last
	 * record
	 * @param request if getFirst|LastRecordWithFilter was called, this is the
	 * request with the filter in the content. Otherwise it's null.
	 * @return the first or last record or null
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public Map<?,?> getFirstLastRecord(ProtocolVersion version, Database authDb,
			Database db, User user, BaseProject project, String table,
			String subject, String start, String end, boolean getFirst,
			HttpServletRequest request) throws HttpException, Exception {
		TableSelectCriteria tableCriteria = getTableSelectCriteria(version,
				authDb, user, project, table, subject, start, end, request,
				Arrays.asList("filter", "sort"), true);
		if (!getFirst)
			tableCriteria.sort = DatabaseSort.reverse(tableCriteria.sort);
		DatabaseObject record = db.selectOne(tableCriteria.tableDef,
				tableCriteria.criteria, tableCriteria.sort);
		if (record == null)
			return null;
		DatabaseObjectMapper mapper = new DatabaseObjectMapper();
		return mapper.objectToMap(record, true);
	}

	/**
	 * Reads a batch of record maps from the JSON reader, validates the maps
	 * and converts them to database objects (see {@link
	 * #createInsertRecord(ProtocolVersion, User, Map, DatabaseTableDef, User)
	 * createInsertRecord()}, and inserts them into the database. The record IDs
	 * will be added to the specified idList.
	 *
	 * @param version the protocol version
	 * @param user the calling user
	 * @param jsonReader the JSON reader, positioned at the start of a record
	 * map
	 * @param db the project database
	 * @param table the table
	 * @param subjectUser the subject user that the records should belong to, or
	 * null if the table is not a user table
	 * @param idList list with record IDs
	 * @throws JsonParseException if the JSON input is invalid
	 * @throws HttpException if a parsed record map is invalid
	 * @throws DatabaseException if a database error occurs
	 * @throws IOException if a reading error occurs
	 */
	private void insertRecordBatch(ProtocolVersion version, User user,
			JsonObjectStreamReader jsonReader, Database db,
			DatabaseTableDef<?> table, User subjectUser, List<String> idList)
			throws JsonParseException, HttpException, DatabaseException,
			IOException {
		List<Map<?,?>> recordMaps = new ArrayList<>();
		while (jsonReader.getToken().getType() !=
				JsonAtomicToken.Type.END_LIST &&
				recordMaps.size() < BATCH_SIZE) {
			recordMaps.add(jsonReader.readObject());
			if (jsonReader.getToken().getType() !=
					JsonAtomicToken.Type.END_LIST) {
				jsonReader.readToken(JsonAtomicToken.Type.LIST_ITEM_SEPARATOR);
			}
		}
		List<DatabaseObject> records = new ArrayList<>();
		while (!recordMaps.isEmpty()) {
			Map<?,?> map = recordMaps.remove(0);
			records.add(createInsertRecord(version, user, map, table,
					subjectUser));
		}
		db.insert(table.getName(), records);
		for (DatabaseObject record : records) {
			idList.add(record.getId());
		}
	}

	/**
	 * Validates a record map inserted by a user and returns a database object
	 * for the specified table. If the table is a user table, this method
	 * ensures that the field "user" is set. If the data class of the table is a
	 * {@link Sample Sample}, it also ensures that "localTime" is set. If the
	 * data class is a {@link UTCSample UTCSample}, it also ensures that
	 * "utcTime" and "timezone" are set. Furthermore it sets the ID field to
	 * null.
	 *
	 * <p>For tables with {@link LocalTimeSample LocalTimeSample}, the user must
	 * have set "localTime". For tables with {@link UTCSample UTCSample}, the
	 * user must have set "timezone", and "localTime" or "utcTime". For any of
	 * these fields that were set, this method validates the values.</p>
	 *
	 * @param version the protocol version
	 * @param user the calling user
	 * @param recordMap the inserted record map
	 * @param table the table to which the record should be written
	 * @param subjectUser the subject user that the record should belong to,
	 * or null if the table is not a user table
	 * @return the database object
	 * @throws HttpException if the record map has invalid input
	 */
	private DatabaseObject createInsertRecord(ProtocolVersion version,
			User user, Map<?,?> recordMap, DatabaseTableDef<?> table,
			User subjectUser) throws HttpException {
		DatabaseObject result = createWriteDatabaseObject(version, user,
				subjectUser, recordMap, table);
		result.setId(null);
		return result;
	}

	/**
	 * Validates a record map updated by a user and returns a database object
	 * for the specified table. If the table is a user table, this method
	 * ensures that the field "user" is set. If the data class of the table is a
	 * {@link Sample Sample}, it also ensures that "localTime" is set. If the
	 * data class is a {@link UTCSample UTCSample}, it also ensures that
	 * "utcTime" and "timezone" are set.
	 *
	 * <p>If the "id" and "user" were already in the record map, this method
	 * validates that they have not changed.</p>
	 *
	 * <p>For tables with {@link LocalTimeSample LocalTimeSample}, the user must
	 * have set "localTime". For tables with {@link UTCSample UTCSample}, the
	 * user must have set "timezone", and "localTime" or "utcTime". For any of
	 * these fields that were set, this method validates the values.</p>
	 *
	 * @param version the protocol version
	 * @param user the calling user
	 * @param recordMap the updated record map
	 * @param table the table to which the record should be written
	 * @param subjectUser the subject user that the record should belong to,
	 * or null if the table is not a user table
	 * @param recordId the record ID that is updated
	 * @return the record
	 * @throws HttpException if the record map has invalid input
	 */
	private DatabaseObject createUpdateRecord(ProtocolVersion version,
			User user, Map<?,?> recordMap, DatabaseTableDef<?> table,
			String recordId, User subjectUser) throws HttpException {
		DatabaseObject record = createWriteDatabaseObject(version, user,
				subjectUser, recordMap, table);
		if (record.getId() != null && !record.getId().equals(recordId)) {
			String msg = "Changing record ID not allowed";
			HttpError error = new HttpError(ErrorCode.INVALID_INPUT, msg);
			throw new BadRequestException(error);
		}
		record.setId(recordId);
		return record;
	}

	/**
	 * Converts the specified map from a JSON object to a DatabaseObject for
	 * the specified table. This method is called when a record is inserted or
	 * updated. It calls the DatabaseObjectMapper and verifies the data types.
	 *
	 * <p>If the table is a user table, then this method validate the "user"
	 * field. If the "user" field is specified, it should match the specified
	 * subject user. If the field is empty, this method will set the user ID of
	 * the subject user.</p>
	 *
	 * <p>Then it validates the time fields using {@link
	 * CommonCrudController#validateWriteRecordTime(ZoneId, DatabaseObject, Map)
	 * CommonCrudController.validateWriteRecordTime()}.</p>
	 *
	 * <p>If the mapping or user and time validation fails, this method throws a
	 * BadRequestException.</p>
	 *
	 * @param version the protocol version
	 * @param user the calling user
	 * @param subject the subject user, or null if the table is not a user table
	 * @param map the map
	 * @param table the database table
	 * @return the database object
	 * @throws HttpException if the mapping fails
	 */
	private <T extends DatabaseObject> T createWriteDatabaseObject(
			ProtocolVersion version, User user, User subject, Map<?,?> map,
			DatabaseTableDef<T> table) throws HttpException {
		DatabaseObjectMapper mapper = new DatabaseObjectMapper();
		T result;
		try {
			result = mapper.mapToObject(map, table.getDataClass(), true);
		} catch (DatabaseFieldException ex) {
			String msg = String.format(
					"Invalid value in field \"%s\" for table \"%s\": ",
					ex.getField(), table.getName()) + map;
			Logger logger = AppComponents.getLogger(RemoteCareManagerContext.LOGTAG);
			logger.error(msg + ": " + ex.getMessage());
			HttpError error = new HttpError(ErrorCode.INVALID_INPUT, msg);
			String fieldMsg = String.format("Invalid value for table \"%s\": ",
					table.getName()) + map.get(ex.getField());
			error.addFieldError(new HttpFieldError(ex.getField(), fieldMsg));
			throw new BadRequestException(error);
		}
		if (subject != null) {
			String subjectName = (String)PropertyReader.readProperty(result,
					"user");
			if (subjectName == null) {
				PropertyWriter.writeProperty(result, "user",
						subject.getUserid());
			} else if (version.ordinal() >= ProtocolVersion.V6_0_0.ordinal()) {
				if (!subjectName.equals(subject.getUserid())) {
					String msg = String.format(
							"Record user \"%s\" does not match query subject \"%s\"",
							subjectName, subject.getUserid()) + ": " + result;
					HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
							msg);
					throw new BadRequestException(error);
				}
			} else {
				if (!subjectName.toLowerCase().equals(subject.getEmail())) {
					String msg = String.format(
							"Record user \"%s\" does not match query subject \"%s\"",
							subjectName, subject.getEmail()) + ": " + result;
					HttpError error = new HttpError(ErrorCode.INVALID_INPUT,
							msg);
					throw new BadRequestException(error);
				}
				PropertyWriter.writeProperty(result, "user",
						subject.getUserid());
			}
		}
		ZoneId defaultTz = subject != null ? subject.toTimeZone() :
				user.toTimeZone();
		validateDetoxQueueWriteRecord(table, result, defaultTz, map);
		CommonCrudController.validateWriteRecordTime(defaultTz, result, map);
		validateDetoxTaskWriteRecord(table, result, user, subject);
		validateDetoxDialogueCapabilityWriteRecord(table, result, user, subject);
		return result;
	}

	private static void ensureDetoxTableSupportsInsert(String table)
			throws ForbiddenException {
		if (DetoxTaskRefreshRequestTable.NAME.equals(table)) {
			throw new ForbiddenException(
					"Use the task refresh endpoint to create task refresh requests");
		}
	}

	private static void ensureDetoxTableSupportsNoMutation(String table,
			String action) throws ForbiddenException {
		if (DetoxTaskConfigurationTable.NAME.equals(table) ||
				DetoxTaskRefreshRequestTable.NAME.equals(table) ||
				DetoxDigitalGuideDialogueCapabilityTable.NAME.equals(table)) {
			throw new ForbiddenException(String.format(
					"Append-only Detox table \"%s\" does not allow %s operations",
					table, action));
		}
	}

	private void validateDetoxTaskWriteRecord(DatabaseTableDef<?> table,
			DatabaseObject record, User user, User subject)
			throws HttpException {
		if (!DetoxTaskConfigurationTable.NAME.equals(table.getName()) ||
				!(record instanceof DetoxTaskConfiguration taskConfig)) {
			return;
		}
		try {
			DetoxTaskConfigurationValidator.validateTaskConfigurationRecord(
					taskConfig, user, subject);
		} catch (TaskValidationException ex) {
			throw BadRequestException.withInvalidInput(ex.toFieldError());
		}
	}

	private void validateDetoxDialogueCapabilityWriteRecord(
			DatabaseTableDef<?> table, DatabaseObject record, User user,
			User subject) throws HttpException {
		if (!DetoxDigitalGuideDialogueCapabilityTable.NAME.equals(
				table.getName()) ||
				!(record instanceof DetoxDigitalGuideDialogueCapability capability)) {
			return;
		}
		try {
			DetoxDigitalGuideDialogueCapabilityValidator
					.validateDialogueCapabilityRecord(capability, user, subject);
		} catch (DialogueCapabilityValidationException ex) {
			throw BadRequestException.withInvalidInput(
					new HttpFieldError(ex.getField(), ex.getMessage()));
		}
	}

	private int countTaskPushRegistrations(Database authDb, String userId,
			String projectCode, String databaseName) throws DatabaseException {
		DatabaseCriteria criteria = new DatabaseCriteria.And(
				new DatabaseCriteria.Equal("user", userId),
				new DatabaseCriteria.Equal("project", projectCode),
				new DatabaseCriteria.Equal("database", databaseName)
		);
		List<SyncPushRegistration> registrations = authDb.select(
				new SyncPushRegistrationTable(), criteria, 0, null);
		int result = 0;
		for (SyncPushRegistration registration : registrations) {
			try {
				if (registration.toSyncReadRestriction().matchesTable(
						DetoxTaskConfigurationTable.NAME) ||
						registration.toSyncReadRestriction().matchesTable(
								DetoxTaskRefreshRequestTable.NAME)) {
					result++;
				}
			} catch (RuntimeException ignored) {
				// Ignore malformed registration filters in the summary.
			}
		}
		return result;
	}

	private String getUserDisplayName(User user) {
		if (user.getFullName() != null && !user.getFullName().isBlank())
			return user.getFullName();
		StringBuilder builder = new StringBuilder();
		appendNamePart(builder, user.getFirstName());
		appendNamePart(builder, user.getPrefixes());
		appendNamePart(builder, user.getLastName());
		if (!builder.isEmpty())
			return builder.toString();
		if (user.getEmail() != null && !user.getEmail().isBlank())
			return user.getEmail();
		return user.getUserid();
	}

	private void appendNamePart(StringBuilder builder, String value) {
		if (value == null || value.isBlank())
			return;
		if (!builder.isEmpty())
			builder.append(' ');
		builder.append(value.trim());
	}

	private void validateDetoxQueueWriteRecord(DatabaseTableDef<?> table,
			DatabaseObject record, ZoneId defaultTz, Map<?,?> recordMap)
			throws HttpException {
		if (!DetoxMessageQueueTable.NAME.equals(table.getName()) ||
				!(record instanceof DetoxMessageQueue detoxMessage)) {
			return;
		}
		String normalizedType = normalizeDetoxType(detoxMessage.getType());
		if (normalizedType == null) {
			throw BadRequestException.withInvalidInput(new HttpFieldError("type",
					"Unsupported type. Allowed values: heartrate, bloodpressure/blood_pressure, detox_dagstart, afwijkingsbeoordeling, vas, sos, dass21, temperature, saturation, bac"));
		}
		detoxMessage.setType(normalizedType);
		if (detoxMessage.getPayload() == null || detoxMessage.getPayload().isBlank()) {
			String payloadFromRecord = buildDetoxPayloadFromRecordMap(recordMap);
			if (payloadFromRecord != null)
				detoxMessage.setPayload(payloadFromRecord);
		}
		Map<String,Object> payload = normalizeDetoxPayload(
				parseDetoxPayload(detoxMessage.getPayload()), normalizedType);
		ZoneId payloadZone;
		Long payloadUtcMillis = null;
		if ("heartrate".equals(normalizedType)) {
			requireDetoxNumber(payload, "value");
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("bloodpressure".equals(normalizedType)) {
			requireDetoxNumber(payload, "diastolic");
			requireDetoxNumber(payload, "systolic");
			if (payload.containsKey("meanArterialPressure")) {
				requireDetoxNumber(payload, "meanArterialPressure");
			} else if (payload.containsKey("map")) {
				requireDetoxNumber(payload, "map");
			} else if (payload.containsKey("pulseBpm")) {
				requireDetoxNumber(payload, "pulseBpm");
			} else {
				throw BadRequestException.withInvalidInput(new HttpFieldError(
						"payload",
							"Missing required field \"meanArterialPressure\", \"map\" or \"pulseBpm\""));
			}
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("vas".equals(normalizedType)) {
			requireDetoxNumber(payload, "value");
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("sos".equals(normalizedType)) {
			requireDetoxQuestionnaireAnswers(payload, 33, 0, 4);
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("dass21".equals(normalizedType)) {
			requireDetoxQuestionnaireAnswers(payload, DASS21_ITEM_IDS.length, 0, 3);
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("temperature".equals(normalizedType)) {
			requireDetoxNumber(payload, "value");
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("saturation".equals(normalizedType)) {
			requireDetoxNumber(payload, "value");
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("bac".equals(normalizedType)) {
			requireDetoxNumber(payload, "value");
			payloadUtcMillis = requireDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("detox_dagstart".equals(normalizedType)) {
			requireDetoxString(payload, "hoeGaatHetVanochtend",
					"goedemorgenHoeGaatHetVanochtend",
					"Goedemorgen, hoe gaat het vanochtend?");
			requireDetoxString(payload, "hebJeVertrouwenInVandaag",
					"vertrouwenInVandaag", "Heb je vertrouwen in vandaag?");
			requireDetoxBoolean(payload, "wilJeVandaagContactMetJouwHulpverlener",
					"contactMetHulpverlener",
					"Wil je vandaag contact met jouw hulpverlener?");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else if ("afwijkingsbeoordeling".equals(normalizedType)) {
			payloadUtcMillis = findOptionalDetoxLong(payload, "timestampUtcMillis");
			payloadZone = resolveDetoxPayloadZone(payload, defaultTz);
		} else {
			payloadZone = defaultTz != null ? defaultTz : ZoneOffset.UTC;
		}
		if (payloadUtcMillis != null) {
			ZoneId zone = payloadZone != null ? payloadZone : ZoneOffset.UTC;
			ZonedDateTime normalizedTzTime = ZonedDateTime.ofInstant(
					Instant.ofEpochMilli(payloadUtcMillis), zone);
			detoxMessage.updateDateTime(normalizedTzTime);
			return;
		}
		if (recordMap.containsKey("utcTime") || recordMap.containsKey("localTime") ||
				recordMap.containsKey("timezone") || recordMap.containsKey("timeZone")) {
			ZoneId recordZone = payloadZone != null ? payloadZone : ZoneOffset.UTC;
			ZoneId explicitRecordZone = parseDetoxPayloadZone(detoxMessage.getTimezone());
			if (explicitRecordZone != null)
				recordZone = explicitRecordZone;
			ZonedDateTime normalizedTzTime = ZonedDateTime.ofInstant(
					Instant.ofEpochMilli(detoxMessage.getUtcTime()), recordZone);
			detoxMessage.updateDateTime(normalizedTzTime);
		}
	}

	private ZoneId validateDetoxPayloadTime(Map<?,?> payload, ZoneId defaultTz)
			throws HttpException {
		requireDetoxLong(payload, "timestampUtcMillis");
		return resolveDetoxPayloadZone(payload, defaultTz);
	}

	private ZoneId resolveDetoxPayloadZone(Map<?,?> payload, ZoneId defaultTz)
			throws HttpException {
		Object timeZoneValue = payload.get("timeZone");
		if (timeZoneValue == null || timeZoneValue.toString().isBlank())
			return defaultTz != null ? defaultTz : ZoneOffset.UTC;
		try {
			return ZoneId.of(timeZoneValue.toString().trim());
		} catch (Exception ex) {
			return defaultTz != null ? defaultTz : ZoneOffset.UTC;
		}
	}

	private ZoneId parseDetoxPayloadZone(String zoneValue) {
		if (zoneValue == null || zoneValue.isBlank())
			return null;
		try {
			return ZoneId.of(zoneValue.trim());
		} catch (Exception ex) {
			return null;
		}
	}

	private Map<String,Object> parseDetoxPayload(String payloadValue)
			throws HttpException {
		if (payloadValue == null || payloadValue.isBlank()) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "payload must be a non-empty JSON object string"));
		}
		Object parsed;
		try {
			parsed = DETOX_PAYLOAD_JSON_MAPPER.readValue(payloadValue,
					Object.class);
		} catch (IOException ex) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "payload must be valid JSON object text"));
		}
		if (!(parsed instanceof Map<?,?> map)) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "payload must be a JSON object"));
		}
		@SuppressWarnings("unchecked")
		Map<String,Object> result = (Map<String,Object>)map;
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String,Object> normalizeDetoxPayload(Map<String,Object> payload,
			String expectedType) {
		if ("afwijkingsbeoordeling".equals(expectedType) &&
				payload.get("values") instanceof Map<?,?> valuesMap) {
			Map<String,Object> normalized = flattenAfwijkingsValues(valuesMap);
			copyIfAbsentDetox(normalized, "timestampUtcMillis", payload,
					"timestampUtcMillis");
			copyIfAbsentDetox(normalized, "timeZone", payload, "timeZone");
			copyIfAbsentDetox(normalized, "localTime", payload, "localTime");
			return normalized;
		}
		Map<String,Object> result = payload;
		Map<String,Object> selectedValue = null;
		if (payload.get("values") instanceof Map<?,?> valuesMap) {
			selectedValue = selectDetoxWrappedPayloadValue(valuesMap, expectedType);
			if (selectedValue != null &&
					selectedValue.get("result") instanceof Map<?,?> resultMap) {
				result = (Map<String,Object>)resultMap;
			} else if (selectedValue != null) {
				result = selectedValue;
			}
		} else if (payload.get("result") instanceof Map<?,?> resultMap) {
			result = (Map<String,Object>)resultMap;
		}
		Map<String,Object> normalized = new LinkedHashMap<>(result);
		copyIfAbsentDetox(normalized, "timestampUtcMillis", payload,
				"timestampUtcMillis");
		copyIfAbsentDetox(normalized, "timeZone", payload, "timeZone");
		copyIfAbsentDetox(normalized, "localTime", payload, "localTime");
		if (selectedValue != null) {
			copyIfAbsentDetox(normalized, "inputDefinitionId", selectedValue,
					"inputDefinitionId");
		}
		return normalized;
	}

	@SuppressWarnings("unchecked")
	private Map<String,Object> flattenAfwijkingsValues(Map<?,?> valuesMap) {
		Map<String,Object> result = new LinkedHashMap<>();
		List<Object> orderedValues = new ArrayList<>();
		for (Map.Entry<?,?> entry : valuesMap.entrySet()) {
			if (!(entry.getValue() instanceof Map<?,?> valueMap))
				continue;
			Map<String,Object> typedValue = (Map<String,Object>)valueMap;
			Map<String,Object> response = typedValue;
			if (typedValue.get("result") instanceof Map<?,?> resultMap)
				response = (Map<String,Object>)resultMap;
			Object extracted = extractResultValue(response);
			if (extracted == null)
				continue;
			orderedValues.add(extracted);
			String inputId = findOptionalDetoxString(response, "inputDefinitionId");
			if (inputId == null || inputId.isBlank()) {
				inputId = findOptionalDetoxString(typedValue, "inputDefinitionId");
			}
			if (inputId == null || inputId.isBlank()) {
				inputId = entry.getKey() != null ? entry.getKey().toString() : null;
			}
			String normalizedInputId = normalizeDetoxOptionValue(inputId != null ?
					inputId : "");
			if ("deviationhowareyou".equals(normalizedInputId)) {
				result.put("openingsvraag", extracted);
			} else if ("deviationneedshelp".equals(normalizedInputId)) {
				result.put("hebJeHulpNodig", extracted);
			} else if ("deviationhelprequest".equals(normalizedInputId)) {
				result.put("waarHebJeHulpBijNodig", extracted);
			} else if ("deviationsubstanceuserisk".equals(normalizedInputId)) {
				result.put("gebruik", extracted);
			} else if ("deviationisalone".equals(normalizedInputId)) {
				result.put("alleenOfNiet", extracted);
			} else if ("deviationtrustedenvironment".equals(normalizedInputId)) {
				result.put("vertrouwdeOmgeving", extracted);
			} else if ("deviationhelpurgency".equals(normalizedInputId)) {
				result.put("hoeSnelHulp", extracted);
			}
		}
		String[] fallbackOrder = new String[] {"openingsvraag", "hebJeHulpNodig",
				"waarHebJeHulpBijNodig", "gebruik", "alleenOfNiet",
				"vertrouwdeOmgeving", "hoeSnelHulp"};
		for (int i = 0; i < fallbackOrder.length && i < orderedValues.size(); i++) {
			result.putIfAbsent(fallbackOrder[i], orderedValues.get(i));
		}
		return result;
	}

	private Object extractResultValue(Map<String,Object> resultMap) {
		if (resultMap.containsKey("text"))
			return resultMap.get("text");
		if (resultMap.containsKey("value"))
			return resultMap.get("value");
		return null;
	}

	@SuppressWarnings("unchecked")
	private Map<String,Object> selectDetoxWrappedPayloadValue(Map<?,?> valuesMap,
			String expectedType) {
		Map<String,Object> firstMap = null;
		for (Map.Entry<?,?> entry : valuesMap.entrySet()) {
			if (!(entry.getValue() instanceof Map<?,?> valueMap))
				continue;
			Map<String,Object> candidate = (Map<String,Object>)valueMap;
			if (firstMap == null)
				firstMap = candidate;
			Map<String,Object> candidateResult = candidate;
			if (candidate.get("result") instanceof Map<?,?> resultMap) {
				candidateResult = (Map<String,Object>)resultMap;
			}
			String inferredType = inferDetoxTypeFromPayload(candidateResult,
					entry.getKey() != null ? entry.getKey().toString() : null);
			if (expectedType != null && expectedType.equals(inferredType))
				return candidate;
		}
		return firstMap;
	}

	private void copyIfAbsentDetox(Map<String,Object> target, String targetKey,
			Map<String,Object> source, String sourceKey) {
		if (target.containsKey(targetKey))
			return;
		if (!source.containsKey(sourceKey))
			return;
		target.put(targetKey, source.get(sourceKey));
	}

	private String inferDetoxTypeFromPayload(Map<String,Object> payload,
			String fallbackInputDefinition) {
		String directType = normalizeDetoxType(findOptionalDetoxString(payload,
				"type"));
		if (directType != null)
			return directType;
		String inputDefinition = findOptionalDetoxString(payload,
				"inputDefinitionId");
		if (inputDefinition == null || inputDefinition.isBlank())
			inputDefinition = fallbackInputDefinition;
		if (inputDefinition == null)
			return directType;
		String normalizedId = inputDefinition.toLowerCase()
				.replaceAll("[_\\s\\-/]", "");
		String payloadType = findOptionalDetoxString(payload, "type");
		if ("questionnaire".equalsIgnoreCase(payloadType)) {
			if (normalizedId.contains("sos"))
				return "sos";
			if (normalizedId.contains("dass21"))
				return "dass21";
		}
		if (normalizedId.contains("bloodpressure"))
			return "bloodpressure";
		if (normalizedId.contains("heartrate"))
			return "heartrate";
		if (normalizedId.contains("dagstart"))
			return "detox_dagstart";
		if (normalizedId.contains("afwijking") || normalizedId.contains("deviation"))
			return "afwijkingsbeoordeling";
		if (normalizedId.contains("cravingvas") || normalizedId.equals("vas"))
			return "vas";
		if (normalizedId.contains("temperature"))
			return "temperature";
		if (normalizedId.contains("saturation") || normalizedId.contains("spo2"))
			return "saturation";
		if (normalizedId.contains("bac") ||
				normalizedId.contains("bloodalcohol"))
			return "bac";
		return directType;
	}

	private String buildDetoxPayloadFromRecordMap(Map<?,?> recordMap)
			throws HttpException {
		if (recordMap == null)
			return null;
		Map<String,Object> payloadMap = new LinkedHashMap<>();
		for (Map.Entry<?,?> entry : recordMap.entrySet()) {
			Object keyObj = entry.getKey();
			if (!(keyObj instanceof String key))
				continue;
			if ("id".equalsIgnoreCase(key) ||
					"user".equalsIgnoreCase(key) ||
					"localTime".equalsIgnoreCase(key) ||
					"utcTime".equalsIgnoreCase(key) ||
					"timezone".equalsIgnoreCase(key) ||
					"payload".equalsIgnoreCase(key)) {
				continue;
			}
			payloadMap.put(key, entry.getValue());
		}
		if (payloadMap.isEmpty())
			return null;
		try {
			return DETOX_PAYLOAD_JSON_MAPPER.writeValueAsString(payloadMap);
		} catch (Exception ex) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "payload must be valid JSON object text"));
		}
	}

	private static String normalizeDetoxType(String type) {
		if (type == null)
			return null;
		String normalized = type.toLowerCase().replaceAll("[_\\s-]", "");
		if ("heartrate".equals(normalized) || "heart".equals(normalized) ||
				"pulse".equals(normalized))
			return "heartrate";
		if ("bloodpressure".equals(normalized) || "blood".equals(normalized))
			return "bloodpressure";
		if ("detoxdagstart".equals(normalized) || "dagstart".equals(normalized))
			return "detox_dagstart";
		if ("afwijkingsbeoordeling".equals(normalized) ||
				"afwijking".equals(normalized) ||
				"deviationassessmentquestions".equals(normalized))
			return "afwijkingsbeoordeling";
		if ("vas".equals(normalized) || "cravingvas".equals(normalized))
			return "vas";
		if ("sos".equals(normalized) ||
				"subjectieveonthoudingsschaal".equals(normalized) ||
				"questionnairesos".equals(normalized))
			return "sos";
		if ("dass21".equals(normalized) ||
				"depressieangststress".equals(normalized) ||
				"questionnairedass21".equals(normalized))
			return "dass21";
		if ("temperature".equals(normalized) ||
				"temperatuur".equals(normalized) ||
				"bodytemperature".equals(normalized))
			return "temperature";
		if ("saturation".equals(normalized) || "saturatie".equals(normalized) ||
				"spo2".equals(normalized))
			return "saturation";
		if ("bac".equals(normalized) ||
				"bloodalcoholconcentration".equals(normalized) ||
				"bloodalcohol".equals(normalized) ||
				"alcoholpromillage".equals(normalized))
			return "bac";
		return null;
	}

	private static void requireDetoxNumber(Map<?,?> map, String key)
			throws HttpException {
		Object value = map.get(key);
		if (value == null) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Missing required numeric field \"" + key + "\""));
		}
		if (value instanceof Number)
			return;
		if (value instanceof String stringValue) {
			try {
				Double.parseDouble(stringValue);
				return;
			} catch (NumberFormatException ignored) {
				// throw below
			}
		}
		throw BadRequestException.withInvalidInput(new HttpFieldError("payload",
				"Invalid numeric field \"" + key + "\""));
	}

	private static long requireDetoxLong(Map<?,?> map, String key)
			throws HttpException {
		Object value = map.get(key);
		if (value == null) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Missing required field \"" + key + "\""));
		}
		if (value instanceof Number number)
			return number.longValue();
		if (value instanceof String stringValue) {
			try {
				return Long.parseLong(stringValue);
			} catch (NumberFormatException ignored) {
				// throw below
			}
		}
		throw BadRequestException.withInvalidInput(new HttpFieldError("payload",
				"Invalid numeric field \"" + key + "\""));
	}

	private static Long findOptionalDetoxLong(Map<?,?> map, String key)
			throws HttpException {
		Object value = map.get(key);
		if (value == null)
			return null;
		if (value instanceof Number number)
			return number.longValue();
		if (value instanceof String stringValue) {
			try {
				return Long.parseLong(stringValue);
			} catch (NumberFormatException ignored) {
				// throw below
			}
		}
		throw BadRequestException.withInvalidInput(new HttpFieldError("payload",
				"Invalid numeric field \"" + key + "\""));
	}

	private static String requireDetoxString(Map<?,?> map, String... keys)
			throws HttpException {
		for (String key : keys) {
			Object value = map.get(key);
			if (value == null || value.toString().isBlank())
				continue;
			return value.toString();
		}
		throw BadRequestException.withInvalidInput(new HttpFieldError(
				"payload", "Missing required field \"" + keys[0] + "\""));
	}

	private static boolean requireDetoxBoolean(Map<?,?> map, String... keys)
			throws HttpException {
		for (String key : keys) {
			Object value = map.get(key);
			if (value == null)
				continue;
			if (value instanceof Boolean boolValue)
				return boolValue;
			String stringValue = value.toString().trim();
			if ("true".equalsIgnoreCase(stringValue))
				return true;
			if ("false".equalsIgnoreCase(stringValue))
				return false;
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Invalid boolean field \"" + keys[0] + "\""));
		}
		throw BadRequestException.withInvalidInput(new HttpFieldError(
				"payload", "Missing required field \"" + keys[0] + "\""));
	}

	private static DetoxCodedOption requireDetoxCodedOption(Map<?,?> map,
			Map<String,DetoxCodedOption> options, String... keys)
			throws HttpException {
		DetoxCodedOption option = findDetoxCodedOption(map, options, keys);
		if (option != null)
			return option;
		throw BadRequestException.withInvalidInput(new HttpFieldError(
				"payload", "Missing required field \"" + keys[0] + "\""));
	}

	private static DetoxCodedOption findDetoxCodedOption(Map<?,?> map,
			Map<String,DetoxCodedOption> options, String... keys)
			throws HttpException {
		for (String key : keys) {
			Object value = map.get(key);
			if (value == null || value.toString().isBlank())
				continue;
			String optionValue = value.toString().trim();
			DetoxCodedOption option = options.get(optionValue.toLowerCase());
			if (option == null)
				option = options.get(normalizeDetoxOptionValue(optionValue));
			if (option == null) {
				throw BadRequestException.withInvalidInput(new HttpFieldError(
						"payload", "Invalid option field \"" + keys[0] + "\""));
			}
			return option;
		}
		return null;
	}

	private static String findOptionalDetoxString(Map<?,?> map, String... keys) {
		for (String key : keys) {
			Object value = map.get(key);
			if (value == null || value.toString().isBlank())
				continue;
			return value.toString();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Map<String,Object> requireDetoxAnswersMap(Map<?,?> payload)
			throws HttpException {
		Object answers = payload.get("answers");
		if (!(answers instanceof Map<?,?> answerMap)) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Missing required field \"answers\""));
		}
		return (Map<String,Object>)answerMap;
	}

	private static void requireDetoxQuestionnaireAnswers(Map<?,?> payload,
			int questionCount, int minValue, int maxValue) throws HttpException {
		Map<String,Object> answers = requireDetoxAnswersMap(payload);
		for (int i = 1; i <= questionCount; i++) {
			String key = String.format("q%02d", i);
			requireDetoxIntInRange(answers, key, minValue, maxValue);
		}
	}

	private static void requireDetoxIntInRange(Map<?,?> map, String key,
			int minValue, int maxValue) throws HttpException {
		Object value = map.get(key);
		if (value == null) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Missing required numeric field \"" + key + "\""));
		}
		double numberValue;
		if (value instanceof Number number) {
			numberValue = number.doubleValue();
		} else if (value instanceof String stringValue) {
			try {
				numberValue = Double.parseDouble(stringValue);
			} catch (NumberFormatException ex) {
				throw BadRequestException.withInvalidInput(new HttpFieldError(
						"payload", "Invalid numeric field \"" + key + "\""));
			}
		} else {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Invalid numeric field \"" + key + "\""));
		}
		long rounded = Math.round(numberValue);
		if (Math.abs(numberValue - rounded) > 0.000001d ||
				rounded < minValue || rounded > maxValue) {
			throw BadRequestException.withInvalidInput(new HttpFieldError(
					"payload", "Invalid numeric field \"" + key + "\""));
		}
	}

	private static Map<String,DetoxCodedOption> createDetoxCodedOptions(
			DetoxCodedOption... options) {
		Map<String,DetoxCodedOption> result = new LinkedHashMap<>();
		for (DetoxCodedOption option : options) {
			result.put(option.codeString.toLowerCase(), option);
			result.put(normalizeDetoxOptionValue(option.value), option);
		}
		return result;
	}

	private static String normalizeDetoxOptionValue(String value) {
		return value.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", "");
	}

	private static class DetoxCodedOption {
		private final String codeString;
		private final String value;

		private DetoxCodedOption(String codeString, String value) {
			this.codeString = codeString;
			this.value = value;
		}
	}

	/**
	 * Runs the query registerWatchTable.
	 *
	 * @param version the protocol version
	 * @param authDb the authentication database
	 * @param projectDb the project database
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the project table
	 * @param subject the user ID or email address of the subject or an empty
	 * string or null (ignored if "anySubject" is set)
	 * @param anySubject true if database actions for any subject should be
	 * watched, false if only the actions for the specified subject should be
	 * watched
	 * @return the registration ID
	 * @throws HttpException if the request is invalid
	 * @throws DatabaseException if a database error occurs
	 */
	public String registerWatchTable(ProtocolVersion version,
			Database authDb, Database projectDb, User user, BaseProject project,
			String table, String subject, boolean anySubject,
			String callbackUrl, boolean reset)
			throws HttpException, DatabaseException {
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			String msg = "Table \"%s\" not found in project \"%s\"";
			throw new NotFoundException(String.format(msg, table,
					project.getCode()));
		}
		String subjectUserid = null;
		if (anySubject) {
			if (user.getRole() != Role.ADMIN) {
				throw new ForbiddenException(
						"Watch table for any user not allowed");
			}
		} else {
			ProjectUserAccess userAccess = User.findAccessibleProjectUser(
					version, subject, project.getCode(), table, AccessMode.R,
					authDb, user);
			userAccess.checkMatchesRange(null, null);
			subjectUserid = userAccess.getUser().getUserid();
		}
		if (callbackUrl != null && !callbackUrl.isEmpty()) {
			try {
				callbackUrl = new URI(callbackUrl).toURL().toString();
			} catch (URISyntaxException | MalformedURLException ex) {
				throw BadRequestException.withInvalidInput(new HttpFieldError(
						"callbackUrl", "Invalid value: " + callbackUrl));
			}
		} else {
			callbackUrl = null;
		}
		return WatchTableListener.addRegistration(authDb, projectDb,
				user.getUserid(), project.getCode(), table, callbackUrl, reset,
				subjectUserid);
	}

	/**
	 * Runs the query watchTable.
	 *
	 * @param request the HTTP request
	 * @param response the HTTP response
	 * @param versionName the protocol version
	 * @param project the project code
	 * @param table the table name
	 * @param id the registration ID
	 * @throws HttpException if the request is invalid
	 * @throws Exception if any other error occurs
	 */
	public void watchTable(final HttpServletRequest request,
			HttpServletResponse response, String versionName, String project,
			String table, String id) throws HttpException, Exception {
		long queryStart = System.currentTimeMillis();
		long queryEnd = queryStart + HANGING_GET_TIMEOUT;
		// verify authentication and input
		WatchTableListener listener = QueryRunner.runProjectQuery(
				(version, authDb, projectDb, user, baseProject) ->
				parseWatchTableInput(authDb, projectDb, id, user, baseProject,
						table),
				versionName, project, request, response);
		// watch listener
		Object currentWatch = new Object();
		final Object lock = listener.getLock();
		List<String> triggeredSubjects;
		synchronized (lock) {
			listener.setCurrentWatch(currentWatch);
			long now = System.currentTimeMillis();
			while (now < queryEnd &&
					listener.getCurrentWatch() == currentWatch &&
					listener.getTriggeredSubjects().isEmpty()) {
				lock.wait(queryEnd - now);
				now = System.currentTimeMillis();
			}
			if (listener.getCurrentWatch() == currentWatch) {
				triggeredSubjects = new ArrayList<>(
						listener.getTriggeredSubjects());
			} else {
				triggeredSubjects = new ArrayList<>();
			}
			listener.clearTriggeredSubjects();
		}
		List<String> result = QueryRunner.runAuthQuery(
				(version, authDb, user, authDetails) -> {
					if (version.ordinal() >= ProtocolVersion.V6_0_0.ordinal()) {
						return triggeredSubjects;
					} else {
						UserCache userCache = UserCache.getInstance();
						List<String> users = new ArrayList<>();
						for (String subject : triggeredSubjects) {
							users.add(userCache.findByUserid(subject)
									.getEmail());
						}
						return users;
					}
				},
				versionName, request, response);
		response.setContentType("application/json;charset=UTF-8");
		try (Writer writer = new OutputStreamWriter(
				response.getOutputStream(), StandardCharsets.UTF_8)) {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writeValueAsString(result);
			writer.write(json);
			writer.flush();
		}
	}

	/**
	 * Runs the query unregisterWatchTable.
	 *
	 * @param authDb the authentication database
	 * @param projectDb the project database
	 * @param user the user who is currently logged in
	 * @param project the project
	 * @param table the name of the project table
	 * @param regId the registration ID
	 * @throws HttpException if the request is invalid
	 * @throws DatabaseException if a database error occurs
	 */
	public Object unregisterWatchTable(Database authDb, Database projectDb,
			User user, BaseProject project, String table, String regId)
			throws HttpException, DatabaseException {
		WatchTableListener listener;
		try {
			listener = findWatchTableListener(projectDb, regId, user, project,
					table);
		} catch (NotFoundException ex) {
			return null;
		}
		WatchTableListener.removeRegistration(authDb, projectDb, listener);
		return null;
	}

	private WatchTableListener parseWatchTableInput(Database authDb,
			Database projectDb, String regId, User user, BaseProject project,
			String table) throws HttpException, DatabaseException {
		WatchTableListener listener = findWatchTableListener(projectDb, regId,
				user, project, table);
		WatchTableRegistration reg = listener.getRegistration();
		// check if user can still access the subject specified in the
		// registration
		if (reg.getSubject() == null) {
			if (user.getRole() != Role.ADMIN) {
				throw new ForbiddenException(
						"Watch table for any user not allowed");
			}
		} else {
			ProjectUserAccess userAccess =
					User.findAccessibleProjectUserByUserid(reg.getSubject(),
							project.getCode(), table, AccessMode.R, authDb, user);
			userAccess.checkMatchesRange(null, null);
		}
		if (!WatchTableListener.setRegistrationWatchTime(authDb,
				projectDb, reg)) {
			throw new NotFoundException();
		}
		return listener;
	}

	private WatchTableListener findWatchTableListener(Database projectDb,
			String regId, User user, BaseProject project, String table)
			throws NotFoundException {
		WatchTableListener listener = WatchTableListener.findListener(projectDb,
				regId);
		if (listener == null)
			throw new NotFoundException();
		WatchTableRegistration reg = listener.getRegistration();
		// check if registration properties correspond to parameters
		if (!reg.getUser().equals(user.getUserid()))
			throw new NotFoundException();
		if (!reg.getTable().equals(table))
			throw new NotFoundException();
		// check if the table in the registration still exists
		DatabaseTableDef<?> tableDef = project.findTable(table);
		if (tableDef == null) {
			String msg = "Table \"%s\" not found in project \"%s\"";
			throw new NotFoundException(String.format(msg, table,
					project.getCode()));
		}
		return listener;
	}

	/**
	 * Finds the {@link BaseProject BaseProject} for the specified project code.
	 * It checks if the user can access the project. If the user is an admin,
	 * that is all projects. Otherwise it checks the {@link UserProjectTable
	 * UserProjectTable}. If the project does not exist or the user can't access
	 * it, this method will throw a {@link NotFoundException NotFoundException}.
	 *
	 * @param projectCode the project code
	 * @param authDb the authentication database
	 * @param user the user
	 * @return the project
	 * @throws HttpException if the project does not exist or the user can't
	 * access it
	 * @throws Exception if any other error occurs
	 */
	public static BaseProject findUserProject(String projectCode,
			Database authDb, User user) throws HttpException, Exception {
		List<BaseProject> projects = getUserProjects(authDb, user);
		for (BaseProject project : projects) {
			if (project.getCode().equals(projectCode))
				return project;
		}
		throw new NotFoundException(String.format(
				"Project \"%s\" not found or not accessible", projectCode));
	}

	/**
	 * Finds the {@link BaseProject BaseProject} for the specified project code.
	 * If the project does not exist, this method will throw a {@link
	 * NotFoundException NotFoundException}. If you want to know whether a user
	 * can access a project, use {@link #findUserProject(String, Database, User)
	 * findUserProject()}.
	 *
	 * @param projectCode the project code
	 * @return the project
	 * @throws HttpException if the project does not exist
	 */
	private BaseProject findProject(String projectCode) throws HttpException {
		ProjectRepository projects = AppComponents.get(ProjectRepository.class);
		BaseProject project = projects.findProjectByCode(projectCode);
		if (project != null)
			return project;
		throw new NotFoundException(String.format("Project \"%s\" not found",
				projectCode));
	}
}
