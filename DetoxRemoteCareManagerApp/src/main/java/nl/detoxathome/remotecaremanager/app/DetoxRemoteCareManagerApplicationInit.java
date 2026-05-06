package nl.detoxathome.remotecaremanager.app;

import nl.detoxathome.remotecaremanager.client.MobileAppRepository;
import nl.detoxathome.remotecaremanager.client.project.ProjectRepository;
import nl.detoxathome.remotecaremanager.dao.DatabaseFactory;
import nl.detoxathome.remotecaremanager.clientextensions.DetoxMobileAppRepository;
import nl.detoxathome.remotecaremanager.clientextensions.project.DetoxProjectRepository;
import nl.detoxathome.remotecaremanager.service.ApplicationInit;
import nl.detoxathome.remotecaremanager.service.Configuration;
import nl.detoxathome.remotecaremanager.service.OAuthTableRepository;
import nl.detoxathome.remotecaremanager.service.access.ProjectUserAccessControlRepository;
import nl.detoxathome.remotecaremanager.service.export.DataExporterFactory;
import nl.detoxathome.remotecaremanager.service.mail.EmailTemplateRepository;
import nl.detoxathome.remotecaremanager.service.sso.SSOTokenRepository;
import nl.rrd.utils.exception.ParseException;

public class DetoxRemoteCareManagerApplicationInit extends ApplicationInit {
	public DetoxRemoteCareManagerApplicationInit() throws Exception {
	}

	@Override
	protected Configuration createConfiguration() {
		return DetoxRemoteCareManagerConfiguration.getInstance();
	}

	@Override
	protected DatabaseFactory createDatabaseFactory() throws ParseException {
		return createMariaDBDatabaseFactory();
	}

	@Override
	protected OAuthTableRepository createOAuthTableRepository() {
		return new DetoxRemoteCareManagerOauthTableRepository();
	}

	@Override
	protected SSOTokenRepository createSSOTokenRepository() {
		return new DetoxRemoteCareManagerSsoTokenRepository();
	}

	@Override
	protected EmailTemplateRepository
	createResetPasswordTemplateRepository() {
		return new DetoxRemoteCareManagerEmailTemplateRepository();
	}

	@Override
	protected ProjectRepository createProjectRepository() {
		return new DetoxProjectRepository();
	}

	@Override
	protected ProjectUserAccessControlRepository
	createProjectUserAccessControlRepository() {
		return new DetoxProjectUserAccessControlRepository();
	}

	@Override
	protected MobileAppRepository createMobileAppRepository() {
		return new DetoxMobileAppRepository();
	}

	@Override
	protected DataExporterFactory createDataExporterFactory() {
		return new DetoxRemoteCareManagerDataExporterFactory();
	}
}
