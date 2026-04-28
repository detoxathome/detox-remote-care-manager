class MyAccountPage {
	/**
	 * Properties:
	 * 
	 * - _user (SenSeeAct user object)
	 * - _onsQueueCleanupEnabled (boolean|null)
	 * - _logoutButton (jQuery element)
	 */
	constructor() {
		this._onsQueueCleanupEnabled = null;
		var self = this;
		checkLogin((data) => {
			self._onGetUserDone(data);
		})
	}

	_onGetUserDone(data) {
		this._user = data;
		this._createView();
	}

	_createView() {
		let background = new BackgroundImage($('#background-image'), true);
		background.render();

		let header = new PageBackHeader($('.page-back-header'));
		header.title = i18next.t('my_account');
		header.backUrl = basePath + '/me';
		header.render();

		let user = this._user;
		new MyAccountAuthForm(this, user);
		new MyAccountMfaForm();
		this._createProfileForm();
		this._createAdminOnsQueueCleanupForm();

		menuController.showSidebar();
		menuController.selectMenuItem('me-account');
		$(document.body).addClass('tinted-background');
		let content = $('#content');
		content.addClass('white-background');
		content.css('visibility', 'visible');
	}

	_createProfileForm() {
		let user = this._user;
		let self = this;

		let firstNameLabel = $('#first-name-label');
		firstNameLabel.text(i18next.t('first_name') + ':');
		let firstNameEdit = new EditableTextValue($('#first-name-value'));
		firstNameEdit.value = user.firstName;
		firstNameEdit.onEdit = (value) => {
			return self._onFirstNameEdit(value);
		}
		firstNameEdit.render();

		let lastNameLabel = $('#last-name-label');
		lastNameLabel.text(i18next.t('last_name') + ':');
		let lastNameEdit = new EditableTextValue($('#last-name-value'));
		lastNameEdit.value = user.lastName;
		lastNameEdit.onEdit = (value) => {
			return self._onLastNameEdit(value);
		}
		lastNameEdit.render();
	}

	_onFirstNameEdit(value) {
		return this._updateUserFailUnexpected((user) => {
			user.firstName = value;
		});
	}

	_onLastNameEdit(value) {
		return this._updateUserFailUnexpected((user) => {
			user.lastName = value;
		});
	}

	_createAdminOnsQueueCleanupForm() {
		let section = $('#admin-ons-cleanup-form');
		if (!this._isAdminUser()) {
			section.hide();
			return;
		}
		section.show();
		$('#admin-ons-cleanup-title').text(
			i18next.t('admin_ons_cleanup_title'));
		$('#admin-ons-cleanup-intro').text(
			i18next.t('admin_ons_cleanup_intro'));
		$('#admin-ons-cleanup-status-label').text(
			i18next.t('admin_ons_cleanup_status') + ':');
		this._loadAdminOnsQueueCleanupSetting();
	}

	_isAdminUser() {
		return this._user && this._user.role === 'ADMIN';
	}

	_loadAdminOnsQueueCleanupSetting() {
		let client = new SenSeeActClient();
		var self = this;
		client.getDetoxOnsQueueAutoCleanupSetting()
		.done((result) => {
			self._onGetAdminOnsQueueCleanupSettingDone(result);
		})
		.fail(() => {
			showToast(i18next.t('unexpected_error'));
		});
	}

	_onGetAdminOnsQueueCleanupSettingDone(result) {
		this._onsQueueCleanupEnabled = result &&
			result.enabled === true;
		this._renderAdminOnsQueueCleanupForm();
	}

	_renderAdminOnsQueueCleanupForm() {
		let enabled = this._onsQueueCleanupEnabled === true;
		let statusValue = enabled ?
			i18next.t('admin_ons_cleanup_enabled') :
			i18next.t('admin_ons_cleanup_disabled');
		$('#admin-ons-cleanup-status-value').text(statusValue);
		let button = $('#admin-ons-cleanup-toggle-button');
		button.text(enabled ?
			i18next.t('admin_ons_cleanup_disable_button') :
			i18next.t('admin_ons_cleanup_enable_button'));
		let self = this;
		animator.clearAnimatedClickHandler(button);
		animator.addAnimatedClickHandler(button, button,
			'animate-blue-button-click',
			null,
			() => {
				self._onAdminOnsQueueCleanupToggleClick();
			}
		);
	}

	_onAdminOnsQueueCleanupToggleClick() {
		let targetEnabled = !(this._onsQueueCleanupEnabled === true);
		let dlg = Dialogue.openDialogue();
		dlg.initMainForm(i18next.t('admin_ons_cleanup_confirm_title'));
		dlg.addText(targetEnabled ?
			i18next.t('admin_ons_cleanup_confirm_enable') :
			i18next.t('admin_ons_cleanup_confirm_disable'));
		dlg.centralButtons.addButton(i18next.t('yes'),
			(clickId) => {
				this._onAdminOnsQueueCleanupConfirmYesClick(clickId,
					targetEnabled);
			},
			(result) => {
				this._onAdminOnsQueueCleanupConfirmYesDone(result, dlg);
			});
		dlg.centralButtons.addButton(i18next.t('no'), null, () => {
			dlg.close();
		});
	}

	_onAdminOnsQueueCleanupConfirmYesClick(clickId, targetEnabled) {
		let client = new SenSeeActClient();
		client.setDetoxOnsQueueAutoCleanupSetting(targetEnabled)
		.done((result) => {
			animator.onAnimatedClickHandlerCompleted(clickId, {
				success: true,
				enabled: result && result.enabled === true
			});
		})
		.fail(() => {
			animator.onAnimatedClickHandlerCompleted(clickId, {
				success: false
			});
		});
	}

	_onAdminOnsQueueCleanupConfirmYesDone(result, dlg) {
		if (!result || result.success !== true) {
			showToast(i18next.t('unexpected_error'));
			return;
		}
		dlg.close();
		this._onsQueueCleanupEnabled = result.enabled === true;
		this._renderAdminOnsQueueCleanupForm();
		showToast(i18next.t('admin_ons_cleanup_updated'));
	}

	updateUser(updateUserFunction) {
		let newUser = JSON.parse(JSON.stringify(this._user));
		updateUserFunction(newUser);
		let client = new SenSeeActClient();
		let xhr = client.updateUser(null, newUser);
		var self = this;
		xhr.done((result) => {
			self._user = result;
		});
		return xhr;
	}

	_updateUserFailUnexpected(updateUserFunction) {
		let xhr = this._updateUser(updateUserFunction);
		xhr.fail(() => {
			showToast(i18next.t('unexpected_error'));
		});
		return xhr;
	}
}

new MyAccountPage();
