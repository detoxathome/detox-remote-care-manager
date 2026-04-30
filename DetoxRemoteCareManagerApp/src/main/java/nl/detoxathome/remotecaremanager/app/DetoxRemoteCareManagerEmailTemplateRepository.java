package nl.detoxathome.remotecaremanager.app;

import nl.detoxathome.remotecaremanager.service.mail.*;
import nl.rrd.utils.AppComponent;

@AppComponent
public class DetoxRemoteCareManagerEmailTemplateRepository
		implements EmailTemplateRepository {
	@Override
	public EmailTemplateCollection getResetPasswordTemplates() {
		return new ResetPasswordTemplateCollection();
	}

	@Override
	public EmailTemplateCollection getNewUserTemplates() {
		return new NewUserTemplateCollection();
	}

	@Override
	public EmailTemplateCollection getEmailChangedOldVerifiedTemplates() {
		return new EmailChangedOldVerifiedTemplateCollection();
	}

	@Override
	public EmailTemplateCollection getEmailChangedOldUnverifiedTemplates() {
		return new EmailChangedOldUnverifiedTemplateCollection();
	}

	@Override
	public EmailTemplateCollection getEmailChangedNewVerifiedTemplates() {
		return new EmailChangedNewVerifiedTemplateCollection();
	}

	@Override
	public EmailTemplateCollection getEmailChangedNewUnverifiedTemplates() {
		return new EmailChangedNewUnverifiedTemplateCollection();
	}
}
