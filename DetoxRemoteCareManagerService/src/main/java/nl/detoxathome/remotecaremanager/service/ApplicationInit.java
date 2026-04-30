package nl.detoxathome.remotecaremanager.service;

import nl.detoxathome.remotecaremanager.client.MobileAppRepository;
import nl.detoxathome.remotecaremanager.client.project.BaseProject;
import nl.detoxathome.remotecaremanager.client.project.ProjectRepository;
import nl.detoxathome.remotecaremanager.dao.DatabaseConnection;
import nl.detoxathome.remotecaremanager.dao.DatabaseFactory;
import nl.detoxathome.remotecaremanager.dao.DatabaseTableDef;
import nl.detoxathome.remotecaremanager.dao.mariadb.MariaDBDatabaseFactory;
import nl.detoxathome.remotecaremanager.service.access.ProjectUserAccessControlRepository;
import nl.detoxathome.remotecaremanager.service.export.DataExporterFactory;
import nl.detoxathome.remotecaremanager.service.mail.EmailTemplateRepository;
import nl.detoxathome.remotecaremanager.service.sso.SSOTokenRepository;
import nl.rrd.utils.AppComponents;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.schedule.DefaultTaskScheduler;
import nl.rrd.utils.schedule.TaskScheduler;
import org.slf4j.Logger;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.event.ContextClosedEvent;

import java.net.URL;
import java.util.List;

public abstract class ApplicationInit {
	private final Object LOCK = new Object();
	private boolean dbInitStarted = false;

	/**
	 * Constructs a new application. It reads service.properties and
	 * initialises the {@link Configuration Configuration} and the {@link
	 * AppComponents AppComponents} with the {@link DatabaseFactory
	 * DatabaseFactory}.
	 * 
	 * @throws Exception if the application can't be initialised
	 */
	public ApplicationInit() throws Exception {
		AppComponents components = AppComponents.getInstance();
		ClassLoader classLoader = ApplicationInit.class.getClassLoader();
		URL propsUrl = classLoader.getResource("service.properties");
		if (propsUrl == null) {
			throw new Exception("Can't find resource service.properties. " +
					"Did you run gradlew updateConfig?");
		}
		Configuration config = createConfiguration();
		config.loadProperties(propsUrl);
		propsUrl = classLoader.getResource("deployment.properties");
		config.loadProperties(propsUrl);
		if (components.findComponent(Configuration.class) == null)
			components.addComponent(config);
		if (components.findComponent(DatabaseFactory.class) == null)
			components.addComponent(createDatabaseFactory());
		if (components.findComponent(OAuthTableRepository.class) == null)
			components.addComponent(createOAuthTableRepository());
		if (components.findComponent(SSOTokenRepository.class) == null)
			components.addComponent(createSSOTokenRepository());
		if (components.findComponent(
				EmailTemplateRepository.class) == null) {
			components.addComponent(createResetPasswordTemplateRepository());
		}
		if (components.findComponent(ProjectRepository.class) == null)
			components.addComponent(createProjectRepository());
		if (components.findComponent(
				ProjectUserAccessControlRepository.class) == null) {
			components.addComponent(createProjectUserAccessControlRepository());
		}
		if (components.findComponent(PermissionRepository.class) == null)
			components.addComponent(createPermissionRepository());
		if (components.findComponent(MobileAppRepository.class) == null)
			components.addComponent(createMobileAppRepository());
		if (components.findComponent(DataExporterFactory.class) == null)
			components.addComponent(createDataExporterFactory());
		if (components.findComponent(TaskScheduler.class) == null)
			components.addComponent(new DefaultTaskScheduler());
		final Logger logger = AppComponents.getLogger(RemoteCareManagerContext.LOGTAG);
		Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
			logger.error("Uncaught exception: " + ex.getMessage(), ex)
		);
		String dataDir = config.get(Configuration.DATA_DIR);
		System.setOut(StdOutLogger.createStdOut(dataDir));
		System.setErr(StdOutLogger.createStdErr(dataDir));
		logger.info("Detox version: " + config.get(Configuration.VERSION));
		PushNotificationService pushService = AppComponents.get(
				PushNotificationService.class);
		pushService.startService();
		new Thread(() -> {
			try {
				initDatabases();
			} catch (Exception ex) {
				logger.error("Can't initialize databases: " +
						ex.getMessage(), ex);
			}
		}).start();
	}
	
	/**
	 * Initializes the authentication database and all project databases.
	 * 
	 * @throws Exception if any error occurs
	 */
	private void initDatabases() throws Exception {
		synchronized (LOCK) {
			if (dbInitStarted)
				return;
			dbInitStarted = true;
		}
		Logger logger = AppComponents.getLogger(RemoteCareManagerContext.LOGTAG);
		logger.info("Start initialization of databases");
		DatabaseLoader dbLoader = DatabaseLoader.getInstance();
		DatabaseConnection dbConn = dbLoader.openConnection();
		try {
			dbLoader.initAuthDatabase(dbConn);
			ProjectRepository projectRepo = AppComponents.get(
					ProjectRepository.class);
			List<BaseProject> projects = projectRepo.getProjects();
			for (BaseProject project : projects) {
				logger.info(
						"Start database initialization for project " +
						project.getCode());
				List<? extends DatabaseTableDef<?>> tables =
						project.getDatabaseTables();
				if (tables != null && !tables.isEmpty()) {
					dbLoader.initProjectDatabase(dbConn, project.getCode());
				}
				logger.info(
						"Completed database initialization for project " +
						project.getCode());
			}
		} finally {
			dbConn.close();
		}
		logger.info("Completed initialization of databases");
	}

	protected abstract Configuration createConfiguration();

	/**
	 * Creates a DatabaseFactory using properties in the configuration.
	 * 
	 * @return the DatabaseFactory
	 * @throws ParseException if the configuration is invalid
	 */
	protected abstract DatabaseFactory createDatabaseFactory()
			throws ParseException;

	protected DatabaseFactory createMariaDBDatabaseFactory()
			throws ParseException {
		Configuration config = AppComponents.get(Configuration.class);
		MariaDBDatabaseFactory dbFactory = new MariaDBDatabaseFactory();
		String host = config.get(Configuration.MYSQL_HOST);
		if (host != null)
			dbFactory.setHost(host);
		String portStr = config.get(Configuration.MYSQL_PORT);
		if (portStr != null) {
			try {
				dbFactory.setPort(Integer.parseInt(portStr));
			} catch (NumberFormatException ex) {
				throw new ParseException(
						"Invalid value for property mysqlPort: " + portStr);
			}
		}
		dbFactory.setUser("root");
		dbFactory.setSyncEnabled(true);
		String password = config.get(Configuration.MYSQL_ROOT_PASSWORD);
		if (password == null)
			throw new ParseException("Property mysqlRootPassword not found");
		dbFactory.setPassword(password);
		return dbFactory;
	}

	protected abstract OAuthTableRepository createOAuthTableRepository();

	protected abstract SSOTokenRepository createSSOTokenRepository();

	protected abstract EmailTemplateRepository
	createResetPasswordTemplateRepository();

	protected abstract ProjectRepository createProjectRepository();

	protected abstract ProjectUserAccessControlRepository
	createProjectUserAccessControlRepository();

	protected PermissionRepository createPermissionRepository() {
		return new PermissionRepository();
	}

	protected abstract MobileAppRepository createMobileAppRepository();

	protected abstract DataExporterFactory createDataExporterFactory();

	public void onApplicationEvent(ContextClosedEvent event) {
		PushNotificationService pushService = AppComponents.get(
				PushNotificationService.class);
		pushService.stopService();
		DatabaseLoader.getInstance().close();
		Logger logger = AppComponents.getLogger(RemoteCareManagerContext.LOGTAG);
		logger.info("Shutdown Detox");
	}

	public OperationCustomizer getOperationCustomizer() {
		return (operation, handlerMethod) -> {
			String controller = handlerMethod.getMethod().getDeclaringClass()
					.getSimpleName().toLowerCase();
			String suffix = "controller";
			if (controller.endsWith("controller")) {
				controller = controller.substring(0,
						controller.length() - suffix.length());
			}
			operation.setOperationId(controller + "_" +
					handlerMethod.getMethod().getName());
			return operation;
		};
	}
}
