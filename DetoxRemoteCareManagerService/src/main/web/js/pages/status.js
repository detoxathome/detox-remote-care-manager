class StatusPage {
	constructor() {
		this._client = new RemoteCareManagerClient();
		this._createView();
		this._loadStatus();
	}

	_createView() {
		menuController.selectMenuItem('status');
		$('#content').css('visibility', 'visible');
	}

	_loadStatus() {
		this._client.getHealthchecksGeneralStatus()
			.done(status => this._renderHealthchecksStatus(status))
			.fail(() => this._renderHealthchecksError());
	}

	_renderHealthchecksStatus(status) {
		let outcomeKey = this._getOutcomeKey(status);
		this._setText('#status-healthchecks-badge', outcomeKey);
		this._setText('#status-healthchecks-outcome', outcomeKey);
		$('#status-healthchecks-badge')
			.removeClass('is-success is-warning is-error')
			.addClass(this._getOutcomeClass(status));
		this._setText('#status-healthchecks-enabled',
			status.enabled ? 'status_enabled' : 'status_disabled');
		this._setText('#status-healthchecks-schedule',
			'status_schedule_value', {
				minutes: this._formatMinutes(status.scheduleDelayMs)
			});
		this._setPlainText('#status-healthchecks-last-attempt',
			this._formatDateTime(status.lastAttemptTime));
		this._setPlainText('#status-healthchecks-last-success',
			this._formatDateTime(status.lastSuccessTime));
		this._setPlainText('#status-healthchecks-last-failure',
			this._formatDateTime(status.lastFailureTime));
		this._setPlainText('#status-healthchecks-last-status-code',
			status.lastStatusCode || i18next.t('status_not_available'));
		this._setText('#status-healthchecks-retry-policy',
			'status_retry_policy_value', {
				retries: status.maxRetries,
				timeout: this._formatSeconds(status.requestTimeoutMs)
			});
	}

	_renderHealthchecksError() {
		this._setText('#status-healthchecks-badge', 'status_unavailable');
		$('#status-healthchecks-badge')
			.removeClass('is-success is-warning is-error')
			.addClass('is-error');
		this._setText('#status-healthchecks-outcome', 'status_unavailable');
	}

	_getOutcomeKey(status) {
		if (!status.configured)
			return 'status_invalid_config';
		if (!status.enabled)
			return 'status_disabled';
		if (status.inProgress)
			return 'status_running';
		if (status.outcome === 'success')
			return 'status_success';
		if (status.outcome === 'failure')
			return 'status_failure';
		return 'status_never_run';
	}

	_getOutcomeClass(status) {
		if (status.outcome === 'success')
			return 'is-success';
		if (!status.configured || status.outcome === 'failure')
			return 'is-error';
		if (!status.enabled || status.inProgress)
			return 'is-warning';
		return '';
	}

	_formatDateTime(value) {
		if (!value)
			return i18next.t('status_not_available');
		let time = luxon.DateTime.fromISO(value);
		if (!time.isValid)
			return i18next.t('status_not_available');
		return time.toFormat(i18next.t('date_hour_minute_format'));
	}

	_formatMinutes(milliseconds) {
		return Math.round(milliseconds / 60000);
	}

	_formatSeconds(milliseconds) {
		return Math.round(milliseconds / 1000);
	}

	_setText(selector, key, params = {}) {
		$(selector).text(i18next.t(key, params));
	}

	_setPlainText(selector, text) {
		$(selector).text(text);
	}
}

new StatusPage();
