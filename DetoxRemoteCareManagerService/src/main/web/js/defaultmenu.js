class DefaultMenuController {
	constructor() {
		menuController.appendMenuItem('home',
			i18next.t('home'),
			basePath + '/');
		menuController.appendMenuItem('account',
			i18next.t('my_account'),
			basePath + '/me/account');
		menuController.appendMenuItem('me',
			i18next.t('my_remote_care_manager'),
			basePath + '/me');
		menuController.appendSubMenuItem('me', 'me-download',
			i18next.t('download_data'),
			basePath + '/me/download');
		menuController.appendSubMenuItem('me', 'me-tasks',
			i18next.t('remote_task_editor'),
			basePath + '/me/tasks');
		menuController.appendSubMenuItem('me', 'me-link-phone',
			i18next.t('link_phone'),
			basePath + '/me/link-phone');
		menuController.appendMenuItem('info',
			i18next.t('about'),
			basePath + '/info');
	}
}

new DefaultMenuController();
