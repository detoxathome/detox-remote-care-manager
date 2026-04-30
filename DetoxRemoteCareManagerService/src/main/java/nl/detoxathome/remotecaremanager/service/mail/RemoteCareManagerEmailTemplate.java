package nl.detoxathome.remotecaremanager.service.mail;

import jakarta.servlet.http.HttpServletRequest;
import nl.detoxathome.remotecaremanager.client.model.User;
import nl.detoxathome.remotecaremanager.service.Configuration;
import nl.rrd.utils.AppComponents;

import java.io.IOException;
import java.util.Map;

public abstract class RemoteCareManagerEmailTemplate extends EmailTemplate {
	public RemoteCareManagerEmailTemplate() {
		super(AppComponents.get(Configuration.class).toEmailConfig());
	}

	@Override
	public String getHtmlContent(HttpServletRequest request, User user,
			Map<String, Object> params) throws IOException {
		Configuration config = AppComponents.get(Configuration.class);
		String webBase = config.get(Configuration.WEB_URL);
		String html = readHtmlContent(request, user,
				"mail_templates/remote_care_manager_mail_template");
		String contentHtml = getHtmlMailContent(request, user, params);
		html = html.replaceAll("\\{content\\}", contentHtml);
		html = html.replaceAll("\\{web-base\\}", webBase);
		return html;
	}

	protected abstract String getHtmlMailContent(HttpServletRequest request,
			User user, Map<String, Object> params) throws IOException;
}
