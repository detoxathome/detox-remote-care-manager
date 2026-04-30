class RemoteTaskEditorPage {
	constructor() {
		this._client = new RemoteCareManagerClient();
		this._project = 'detox';
		this._table = 'task_configurations';
		this._refreshTimeoutMs = 20000;
		this._pollIntervalMs = 2000;
		this._user = null;
		this._subjects = [];
		this._subjectMap = {};
		this._selectedSubject = null;
		this._selectedSubjectSummary = null;
		this._tasks = [];
		this._selectedTaskIndex = -1;
		this._dirty = false;
		this._stale = false;
		this._busy = false;
		this._editorLoaded = false;
		this._baseRecordId = null;
		this._latestRecordId = null;
		this._currentSnapshot = null;
		this._watchRegistrationId = null;
		this._watchGeneration = 0;
		this._loadGeneration = 0;
		this._deepLinkSubject = null;
		this._deepLinkOnsId = null;
		this._deepLinkOnsInstance = null;
		this._deepLinkResolved = false;
		this._deepLinkApplied = false;
		this._patientStepCard = $('#remote-task-step-patient');
		this._refreshStepCard = $('#remote-task-step-refresh');
		this._editStepCard = $('#remote-task-step-edit');
		this._publishStepCard = $('#remote-task-step-publish');
		this._status = $('#remote-task-status');
		this._banner = $('#remote-task-banner');
		this._subjectSelect = $('#remote-task-subject-select');
		this._list = $('#remote-task-list');
		this._listSummary = $('#remote-task-list-summary');
		this._detailSummary = $('#remote-task-detail-summary');
		this._empty = $('#remote-task-empty');
		this._detailForm = $('#remote-task-detail-form');
		this._name = $('#remote-task-name');
		this._requestText = $('#remote-task-request-text');
		this._description = $('#remote-task-description');
		this._dialogueId = $('#remote-task-dialogue-id');
		this._triggerType = $('#remote-task-trigger-type');
		this._fixedTime = $('#remote-task-fixed-time');
		this._stateStart = $('#remote-task-state-start');
		this._stateEnd = $('#remote-task-state-end');
		this._state = $('#remote-task-state');
		this._answerableOnWatch = $('#remote-task-answerable-watch');
		this._maxPerDay = $('#remote-task-max-per-day');
		this._minInterval = $('#remote-task-min-interval');
		checkLogin((data) => this._onGetUserDone(data));
	}

	_onGetUserDone(data) {
		this._user = data;
		if (this._user.role === 'PATIENT') {
			this._renderForbidden();
			return;
		}
		this._createView();
		this._loadSubjects();
	}

	_createView() {
		let header = new PageBackHeader($('.page-back-header'));
		header.title = i18next.t('remote_task_editor');
		header.backUrl = basePath + '/me';
		header.render();
		menuController.showSidebar();
		menuController.selectMenuItem('me-tasks');
		$(document.body).addClass('tinted-background');
		$('#content').css('visibility', 'visible');

		this._subjectSelect.on('change', () => this._onSubjectChanged());
		$('#remote-task-refresh-app').on('click', () => this._reloadFromApp());
		$('#remote-task-reload-latest').on('click', () => this._reloadLatest());
		$('#remote-task-discard-draft').on('click', () => this._discardDraft());
		$('#remote-task-publish').on('click', () => this._publish());
		$('#remote-task-add').on('click', () => this._addTask());
		$('#remote-task-duplicate').on('click', () => this._duplicateTask());
		$('#remote-task-delete').on('click', () => this._deleteTask());
		$('#remote-task-move-up').on('click', () => this._moveTask(-1));
		$('#remote-task-move-down').on('click', () => this._moveTask(1));

		[
			this._name,
			this._requestText,
			this._description,
			this._dialogueId,
			this._triggerType,
			this._fixedTime,
			this._stateStart,
			this._stateEnd,
			this._state,
			this._answerableOnWatch,
			this._maxPerDay,
			this._minInterval
		].forEach((input) => {
			input.on('input change', () => this._onDetailChanged());
		});

		$(window).on('beforeunload.remoteTaskEditor', () => {
			if (this._selectedSubject && this._dirty)
				return 'You have non-published task changes.';
			return undefined;
		});

		this._updateStepState();
		this._applyUrlParams();
	}

	_renderForbidden() {
		let header = new PageBackHeader($('.page-back-header'));
		header.title = i18next.t('remote_task_editor');
		header.backUrl = basePath + '/me';
		header.render();
		menuController.showSidebar();
		menuController.selectMenuItem('me-tasks');
		$('#content').css('visibility', 'visible');
		this._setBanner('error',
			'This page is only available for professionals and admins.');
	}

	_loadSubjects() {
		this._setStatus('Loading linked Detox patients...');
		this._client.getDetoxSubjects(this._project, false)
			.done((subjects) => this._onSubjectsLoaded(subjects))
			.fail(() => {
				this._setBanner('error',
					'Could not load linked Detox patients.');
				this._setStatus('');
			});
	}

	_onSubjectsLoaded(subjects) {
		this._subjects = Array.isArray(subjects) ? subjects : [];
		this._subjectMap = {};
		this._subjectSelect.empty();
		this._subjectSelect.append($('<option></option>')
			.attr('value', '')
			.text(this._subjects.length > 0 ?
				'Select a linked patient...' :
				'No linked patients found'));
		for (let subject of this._subjects) {
			this._subjectMap[subject.userId] = subject;
			let label = subject.displayName;
			if (subject.onsId !== null && subject.onsId !== undefined) {
				label += ' | ONS ' + subject.onsId;
				if (subject.onsInstance)
					label += ' (' + subject.onsInstance + ')';
			}
			if (!subject.pushReady)
				label += ' | no push registration';
			this._subjectSelect.append($('<option></option>')
				.attr('value', subject.userId)
				.text(label));
		}
		if (this._subjects.length === 0) {
			this._setStatus('No linked Detox patients are available for task editing.');
			this._clearEditorState();
			return;
		}
		if (!this._applyDeepLinkSelection())
			this._setStatus('Select a linked patient to start an edit session.');
		this._updateStepState();
	}

	_applyUrlParams() {
		let params = parseURL(window.location.href).params || {};
		if (params['subject'])
			this._deepLinkSubject = (params['subject'] + '').trim() || null;
		else if (params['user'])
			this._deepLinkSubject = (params['user'] + '').trim() || null;
		if (params['onsId']) {
			let onsId = (params['onsId'] + '').trim();
			if (onsId.length > 0)
				this._deepLinkOnsId = onsId;
		}
		if (params['onsInstance']) {
			let onsInstance = (params['onsInstance'] + '').trim();
			if (onsInstance.length > 0)
				this._deepLinkOnsInstance = onsInstance;
		}
	}

	_applyDeepLinkSelection() {
		if (this._deepLinkApplied)
			return true;
		if (this._deepLinkSubject) {
			return this._selectSubjectFromDeepLink(this._deepLinkSubject);
		}
		if (this._deepLinkOnsId && !this._deepLinkResolved) {
			this._deepLinkResolved = true;
			this._setStatus('Resolving linked Detox patient from ONS...');
			this._client.getDetoxOnsLookup(this._deepLinkOnsId,
					this._deepLinkOnsInstance)
				.done((lookup) => {
					let subjectId = lookup && lookup.ssaId ? lookup.ssaId : null;
					if (!subjectId) {
						this._setBanner('error',
							'No linked Detox patient was returned for this ONS link.');
						this._setStatus('Select a linked patient to start an edit session.');
						return;
					}
					this._deepLinkSubject = subjectId;
					if (!this._selectSubjectFromDeepLink(subjectId)) {
						this._setBanner('error',
							'The linked Detox patient could not be opened in the task editor.');
						this._setStatus('Select a linked patient to start an edit session.');
					}
				})
				.fail((xhr) => {
					let message = 'The ONS-linked patient could not be resolved.';
					if (xhr && xhr.status === 404)
						message = 'No Detox patient is linked to this ONS record yet.';
					else if (xhr && xhr.status === 403)
						message = 'You do not have access to open this linked Detox patient.';
					this._setBanner('error', message);
					this._setStatus('Select a linked patient to start an edit session.');
				});
			return true;
		}
		return false;
	}

	_selectSubjectFromDeepLink(subjectId) {
		if (!subjectId || !this._subjectMap[subjectId])
			return false;
		this._deepLinkApplied = true;
		this._subjectSelect.val(subjectId);
		this._onSubjectChanged();
		return true;
	}

	_onSubjectChanged() {
		let subjectId = this._subjectSelect.val();
		if (this._dirty && this._selectedSubject !== subjectId &&
				!window.confirm(
					'Switching patients will lose all non-published changes for the current patient. Continue?')) {
			this._subjectSelect.val(this._selectedSubject || '');
			return;
		}
		if (!subjectId) {
			this._selectedSubject = null;
			this._selectedSubjectSummary = null;
			this._stopWatching();
			this._clearEditorState();
			this._setStatus('Select a linked patient to start an edit session.');
			return;
		}
		this._selectedSubject = subjectId;
		this._selectedSubjectSummary = this._subjectMap[subjectId] || null;
		this._clearBanner();
		this._startEditSession(subjectId, true);
	}

	_reloadFromApp() {
		if (!this._selectedSubject)
			return;
		if (this._dirty && !window.confirm(
				'Retrying the phone refresh will replace the current draft with the latest tasks from the linked phone. Continue?')) {
			return;
		}
		this._clearStoredDraft(this._selectedSubject);
		this._startEditSession(this._selectedSubject, true);
	}

	_reloadLatest() {
		if (!this._selectedSubject)
			return;
		if (this._dirty && !window.confirm(
				'Open the latest middleware snapshot and lose all non-published changes in the current draft?')) {
			return;
		}
		this._clearStoredDraft(this._selectedSubject);
		this._startEditSession(this._selectedSubject, false);
	}

	_discardDraft() {
		if (!this._selectedSubject)
			return;
		if (this._dirty && !window.confirm('Discard the current draft?'))
			return;
		this._clearStoredDraft(this._selectedSubject);
		this._dirty = false;
		this._stale = false;
		this._startEditSession(this._selectedSubject, false);
	}

	_startEditSession(subjectId, refreshFromApp) {
		const generation = ++this._loadGeneration;
		this._editorLoaded = false;
		this._stopWatching();
		this._startWatching(subjectId);
		this._setBusy(true);
		this._setStatus('Loading the latest middleware snapshot...');
		this._client.getProjectLastRecord(this._project, this._table, subjectId)
			.done((response) => {
				const fallbackRecord = response ? response.value : null;
				if (refreshFromApp) {
					this._requestFreshSnapshotFromApp(generation, subjectId,
							fallbackRecord);
				} else {
					this._openEditorFromRecord(generation, subjectId,
							fallbackRecord, null, null);
				}
			})
			.fail(() => {
				this._setBusy(false);
				this._setBanner('error',
					'Could not load the latest middleware snapshot.');
			});
	}

	_requestFreshSnapshotFromApp(generation, subjectId, fallbackRecord) {
		const summary = this._selectedSubjectSummary;
		if (summary && !summary.pushReady) {
			this._setBanner('warning',
				'No task push registration is known for this patient. Waiting briefly for a pull-based refresh, then falling back to the latest middleware snapshot.');
		} else {
			this._setStatus('Requesting the linked phone to upload its latest tasks...');
		}
		this._client.createDetoxTaskRefresh(this._project, subjectId)
			.done((result) => {
				this._pollForFreshAppSnapshot(generation, subjectId,
						result.requestToken, fallbackRecord,
						Date.now() + this._refreshTimeoutMs);
			})
			.fail(() => {
				this._openEditorFromRecord(generation, subjectId, fallbackRecord,
					'Could not create an app refresh request. Using the latest middleware snapshot instead.',
					null);
			});
	}

	_pollForFreshAppSnapshot(generation, subjectId, requestToken,
			fallbackRecord, deadline) {
		if (generation !== this._loadGeneration ||
				subjectId !== this._selectedSubject) {
			return;
		}
		const filter = {
			'$and': [
				{ source: DetoxTaskConfigurationSource.APP },
				{ requestToken: requestToken }
			]
		};
		this._client.getProjectLastRecordWithFilter(this._project, this._table,
				subjectId, filter)
			.done((response) => {
				const record = response ? response.value : null;
				if (record) {
					this._openEditorFromRecord(generation, subjectId, record,
						null, requestToken);
					return;
				}
				if (Date.now() >= deadline) {
					this._openEditorFromRecord(generation, subjectId,
						fallbackRecord,
						'The linked phone did not upload a fresh task set in time. Editing continues from the latest middleware snapshot, which may be stale.',
						requestToken);
					return;
				}
				window.setTimeout(() => {
					this._pollForFreshAppSnapshot(generation, subjectId,
							requestToken, fallbackRecord, deadline);
				}, this._pollIntervalMs);
			})
			.fail(() => {
				this._openEditorFromRecord(generation, subjectId, fallbackRecord,
					'The app refresh check failed. Editing continues from the latest middleware snapshot.',
					requestToken);
			});
	}

	_openEditorFromRecord(generation, subjectId, record, warningMessage,
			requestToken) {
		if (generation !== this._loadGeneration ||
				subjectId !== this._selectedSubject) {
			return;
		}
		this._setBusy(false);
		this._currentSnapshot = record;
		this._latestRecordId = record ? record.id : null;
		this._baseRecordId = this._latestRecordId;
		this._editorLoaded = true;
		this._tasks = [];
		this._selectedTaskIndex = -1;
		this._dirty = false;
		this._stale = false;

		if (record && record.xml) {
			try {
				this._tasks = this._parseTaskXml(record.xml);
			} catch (error) {
				this._setBanner('error',
					'The stored task XML could not be parsed: ' + error.message);
				this._tasks = [];
			}
		}

		let restoredDraft = this._loadStoredDraft(subjectId);
		if (restoredDraft) {
			this._tasks = restoredDraft.tasks || this._tasks;
			this._selectedTaskIndex =
				typeof restoredDraft.selectedTaskIndex === 'number' ?
					restoredDraft.selectedTaskIndex : this._selectedTaskIndex;
			this._baseRecordId = restoredDraft.baseRecordId;
			this._dirty = true;
			if (this._baseRecordId !== this._latestRecordId) {
				this._stale = true;
				this._setBanner('warning',
					'A saved draft was restored, but a newer task snapshot now exists on the server. Reload latest before publishing, then re-apply the draft manually.');
			} else if (!warningMessage) {
				this._setBanner('warning',
					'An unsaved draft was restored for this patient.');
			}
		}

		if (this._tasks.length > 0) {
			if (this._selectedTaskIndex < 0 ||
					this._selectedTaskIndex >= this._tasks.length) {
				this._selectedTaskIndex = 0;
			}
		}

		if (warningMessage && !this._stale)
			this._setBanner('warning', warningMessage);
		this._renderTaskList();
		this._renderDetail();
		this._updateStatusText(requestToken);
		this._updateStepState();
	}

	_renderTaskList() {
		this._list.empty();
		if (this._tasks.length === 0) {
			this._listSummary.text('No tasks loaded yet.');
			this._list.append($('<div></div>')
				.addClass('caption')
				.text('No tasks are available in the current snapshot.'));
			return;
		}
		this._listSummary.text(this._tasks.length + ' task(s)');
		this._tasks.forEach((task, index) => {
			let item = $('<div></div>').addClass('remote-task-list-item');
			if (index === this._selectedTaskIndex)
				item.addClass('selected');
			item.append($('<div></div>')
				.addClass('remote-task-list-item-title')
				.text(task.name || 'Nieuwe taak'));
			item.append($('<div></div>')
				.addClass('remote-task-list-item-request')
				.text(task.requestText || 'Geen vraagtekst'));
			item.append($('<div></div>')
				.addClass('remote-task-list-item-summary')
				.text(this._taskSummary(task)));
			item.on('click', () => {
				this._selectedTaskIndex = index;
				this._renderTaskList();
				this._renderDetail();
			});
			this._list.append(item);
		});
	}

	_renderDetail() {
		if (this._selectedTaskIndex < 0 ||
				this._selectedTaskIndex >= this._tasks.length) {
			this._empty.show();
			this._detailForm.hide();
			this._detailSummary.text('');
			return;
		}
		const task = this._tasks[this._selectedTaskIndex];
		this._empty.hide();
		this._detailForm.show();
		this._detailSummary.text('Task ID ' + task.id);
		this._name.val(task.name || '');
		this._requestText.val(task.requestText || '');
		this._description.val(task.description || '');
		this._dialogueId.val(task.digitalGuideDialogueId || '');
		this._triggerType.val(task.triggerType || 'FIXED_TIME');
		this._fixedTime.val(task.fixedTime || '');
		this._stateStart.val(task.stateTimeRangeStart || '');
		this._stateEnd.val(task.stateTimeRangeEnd || '');
		this._state.val(task.requiredState || '');
		this._answerableOnWatch.prop('checked', !!task.answerableOnWatch);
		this._maxPerDay.val(task.maximumRequestsPerDay || '');
		this._minInterval.val(task.minimumIntervalRequestInMinutes || '');
	}

	_onDetailChanged() {
		if (this._selectedTaskIndex < 0 ||
				this._selectedTaskIndex >= this._tasks.length) {
			return;
		}
		let task = this._tasks[this._selectedTaskIndex];
		task.name = this._name.val().trim();
		task.requestText = this._requestText.val().trim();
		task.description = this._description.val().trim();
		task.digitalGuideDialogueId = this._dialogueId.val().trim();
		task.triggerType = this._triggerType.val();
		task.fixedTime = this._normalizeTimeInput(this._fixedTime.val());
		task.stateTimeRangeStart =
			this._normalizeTimeInput(this._stateStart.val());
		task.stateTimeRangeEnd =
			this._normalizeTimeInput(this._stateEnd.val());
		task.requiredState = this._state.val();
		task.answerableOnWatch = this._answerableOnWatch.is(':checked');
		task.maximumRequestsPerDay = this._normalizeNumberInput(
			this._maxPerDay.val());
		task.minimumIntervalRequestInMinutes = this._normalizeNumberInput(
			this._minInterval.val());
		this._markDirty();
		this._renderTaskList();
		this._renderDetail();
	}

	_addTask() {
		if (!this._selectedSubject)
			return;
		this._tasks.push(this._createEmptyTask());
		this._selectedTaskIndex = this._tasks.length - 1;
		this._markDirty();
		this._renderTaskList();
		this._renderDetail();
	}

	_duplicateTask() {
		if (this._selectedTaskIndex < 0)
			return;
		let clone = JSON.parse(JSON.stringify(
			this._tasks[this._selectedTaskIndex]));
		clone.id = this._nextTaskId();
		this._tasks.splice(this._selectedTaskIndex + 1, 0, clone);
		this._selectedTaskIndex += 1;
		this._markDirty();
		this._renderTaskList();
		this._renderDetail();
	}

	_deleteTask() {
		if (this._selectedTaskIndex < 0)
			return;
		if (!window.confirm('Delete the selected task?'))
			return;
		this._tasks.splice(this._selectedTaskIndex, 1);
		if (this._selectedTaskIndex >= this._tasks.length)
			this._selectedTaskIndex = this._tasks.length - 1;
		this._markDirty();
		this._renderTaskList();
		this._renderDetail();
	}

	_moveTask(direction) {
		if (this._selectedTaskIndex < 0)
			return;
		let newIndex = this._selectedTaskIndex + direction;
		if (newIndex < 0 || newIndex >= this._tasks.length)
			return;
		let task = this._tasks[this._selectedTaskIndex];
		this._tasks.splice(this._selectedTaskIndex, 1);
		this._tasks.splice(newIndex, 0, task);
		this._selectedTaskIndex = newIndex;
		this._markDirty();
		this._renderTaskList();
		this._renderDetail();
	}

	_publish() {
		if (!this._selectedSubject)
			return;
		let validationError = this._validateDraft();
		if (validationError) {
			this._setBanner('error', validationError);
			return;
		}
		this._setBusy(true);
		this._setStatus('Checking for newer task snapshots before publish...');
		this._client.getProjectLastRecord(this._project, this._table,
				this._selectedSubject)
			.done((response) => {
				const latestRecord = response ? response.value : null;
				const latestRecordId = latestRecord ? latestRecord.id : null;
				if (latestRecordId !== this._baseRecordId) {
					this._setBusy(false);
					this._stale = true;
					this._storeDraft();
					this._updateStatusText(null);
					this._updateStepState();
					this._setBanner('warning',
						'A newer task snapshot was published while you were editing. Your draft has been preserved locally, but publish is blocked until you reload the latest version.');
					return;
				}
				let xml;
				try {
					xml = this._serializeTasksToXml(this._tasks);
				} catch (error) {
					this._setBusy(false);
					this._setBanner('error',
						'Could not serialize the current draft to XML: ' +
							error.message);
					return;
				}
				let record = {
					xml: xml,
					source: DetoxTaskConfigurationSource.WEB
				};
				this._client.insertProjectRecords(this._project, this._table,
						this._selectedSubject, [record])
					.done(() => {
						this._setBusy(false);
						this._clearStoredDraft(this._selectedSubject);
						this._dirty = false;
						this._stale = false;
						this._setBanner('warning',
							'Task snapshot published. Waiting for linked devices to sync the new version.');
						this._startEditSession(this._selectedSubject, false);
					})
					.fail((xhr) => {
						this._setBusy(false);
						let message = 'Publishing the task snapshot failed.';
						if (xhr && xhr.responseJSON && xhr.responseJSON.message)
							message = xhr.responseJSON.message;
						this._setBanner('error', message);
					});
			})
			.fail(() => {
				this._setBusy(false);
				this._setBanner('error',
					'Could not refetch the latest snapshot before publish.');
			});
	}

	_validateDraft() {
		for (let i = 0; i < this._tasks.length; i++) {
			let task = this._tasks[i];
			if (!task.name || !task.requestText) {
				return 'Each task needs at least a name and request text.';
			}
			if (task.maximumRequestsPerDay !== '' &&
					task.maximumRequestsPerDay !== null &&
					Number.isNaN(parseInt(task.maximumRequestsPerDay, 10))) {
				return 'Maximum requests per day must be an integer.';
			}
			if (task.minimumIntervalRequestInMinutes !== '' &&
					task.minimumIntervalRequestInMinutes !== null &&
					Number.isNaN(parseInt(task.minimumIntervalRequestInMinutes,
						10))) {
				return 'Minimum interval must be an integer.';
			}
		}
		return null;
	}

	_startWatching(subjectId) {
		const generation = ++this._watchGeneration;
		this._client.registerProjectTableWatch(this._project, this._table,
				subjectId, true)
			.done((registrationId) => {
				if (generation !== this._watchGeneration ||
						subjectId !== this._selectedSubject)
					return;
				this._watchRegistrationId = registrationId;
				this._watchLoop(subjectId, registrationId, generation);
			});
	}

	_watchLoop(subjectId, registrationId, generation) {
		if (generation !== this._watchGeneration ||
				subjectId !== this._selectedSubject ||
				this._watchRegistrationId !== registrationId) {
			return;
		}
		this._client.watchProjectTable(this._project, this._table,
				registrationId)
			.done((subjects) => {
				if (generation !== this._watchGeneration)
					return;
				if (Array.isArray(subjects) && subjects.length > 0) {
					this._stale = true;
					this._updateStatusText(null);
					this._updateStepState();
					this._setBanner('warning',
						'A newer app or web task snapshot arrived while this editor was open. Reload latest before publishing.');
				}
				this._watchLoop(subjectId, registrationId, generation);
			})
			.fail(() => {
				if (generation !== this._watchGeneration)
					return;
				window.setTimeout(() => {
					this._watchLoop(subjectId, registrationId, generation);
				}, 3000);
			});
	}

	_stopWatching() {
		if (!this._watchRegistrationId)
			return;
		const registrationId = this._watchRegistrationId;
		this._watchRegistrationId = null;
		this._watchGeneration += 1;
		this._client.unregisterProjectTableWatch(this._project, this._table,
				registrationId);
	}

	_markDirty() {
		this._dirty = true;
		this._storeDraft();
		this._updateStatusText(null);
		this._updateStepState();
	}

	_storeDraft() {
		if (!this._selectedSubject)
			return;
		let draft = {
			baseRecordId: this._baseRecordId,
			selectedTaskIndex: this._selectedTaskIndex,
			tasks: this._tasks
		};
		window.localStorage.setItem(this._draftStorageKey(this._selectedSubject),
				JSON.stringify(draft));
	}

	_loadStoredDraft(subjectId) {
		let raw = window.localStorage.getItem(this._draftStorageKey(subjectId));
		if (!raw)
			return null;
		try {
			return JSON.parse(raw);
		} catch (error) {
			window.localStorage.removeItem(this._draftStorageKey(subjectId));
			return null;
		}
	}

	_clearStoredDraft(subjectId) {
		window.localStorage.removeItem(this._draftStorageKey(subjectId));
	}

	_draftStorageKey(subjectId) {
		return 'remote-task-editor-draft:' + this._project + ':' + subjectId;
	}

	_clearEditorState() {
		this._tasks = [];
		this._selectedTaskIndex = -1;
		this._dirty = false;
		this._stale = false;
		this._editorLoaded = false;
		this._baseRecordId = null;
		this._latestRecordId = null;
		this._currentSnapshot = null;
		this._renderTaskList();
		this._renderDetail();
		this._updateStepState();
	}

	_updateStatusText(requestToken) {
		if (!this._selectedSubjectSummary) {
			this._setStatus('');
			return;
		}
		let parts = [];
		parts.push('Patient: ' + this._selectedSubjectSummary.displayName);
		parts.push(this._selectedSubjectSummary.pushReady ?
			'push ready (' +
				this._selectedSubjectSummary.pushRegisteredDeviceCount +
				' device' +
				(this._selectedSubjectSummary.pushRegisteredDeviceCount === 1 ?
					'' : 's') + ')' :
			'no push registration');
		if (this._currentSnapshot) {
			parts.push('base snapshot ' + this._currentSnapshot.id);
			if (this._currentSnapshot.localTime)
				parts.push(this._currentSnapshot.localTime);
			if (this._currentSnapshot.editorUser)
				parts.push('editor ' + this._currentSnapshot.editorUser);
			if (this._currentSnapshot.source)
				parts.push('source ' + this._currentSnapshot.source);
		} else {
			parts.push('no stored snapshot yet');
		}
		if (requestToken)
			parts.push('refresh token ' + requestToken);
		if (this._stale)
			parts.push('stale draft');
		else if (this._dirty)
			parts.push('unsaved changes');
		else
			parts.push('saved');
		this._setStatus(parts.join(' | '));
	}

	_setBusy(isBusy) {
		this._busy = isBusy;
		$('#remote-task-refresh-app').prop('disabled', isBusy);
		$('#remote-task-reload-latest').prop('disabled', isBusy);
		$('#remote-task-discard-draft').prop('disabled', isBusy);
		$('#remote-task-publish').prop('disabled', isBusy);
		$('#remote-task-add').prop('disabled', isBusy);
		$('#remote-task-duplicate').prop('disabled', isBusy);
		$('#remote-task-delete').prop('disabled', isBusy);
		$('#remote-task-move-up').prop('disabled', isBusy);
		$('#remote-task-move-down').prop('disabled', isBusy);
		this._subjectSelect.prop('disabled', isBusy);
		this._updateStepState();
	}

	_updateStepState() {
		const hasSubject = !!this._selectedSubject;
		const editReady = hasSubject && this._editorLoaded;
		const publishReady = editReady && this._dirty && !this._stale &&
			!this._busy;

		let currentStep = 'patient';
		if (publishReady)
			currentStep = 'publish';
		else if (editReady)
			currentStep = 'edit';
		else if (hasSubject)
			currentStep = 'refresh';

		this._setStepCardState(this._patientStepCard, true,
			currentStep === 'patient');
		this._setStepCardState(this._refreshStepCard, hasSubject,
			currentStep === 'refresh');
		this._setStepCardState(this._editStepCard, editReady,
			currentStep === 'edit');
		this._setStepCardState(this._publishStepCard, publishReady,
			currentStep === 'publish');

		if (!this._busy) {
			$('#remote-task-refresh-app').prop('disabled', !hasSubject);
			$('#remote-task-reload-latest').prop('disabled', !hasSubject);
			$('#remote-task-discard-draft').prop('disabled', !editReady);
			$('#remote-task-publish').prop('disabled', !publishReady);
			$('#remote-task-add').prop('disabled', !editReady);
			$('#remote-task-duplicate').prop('disabled',
				!editReady || this._selectedTaskIndex < 0);
			$('#remote-task-delete').prop('disabled',
				!editReady || this._selectedTaskIndex < 0);
			$('#remote-task-move-up').prop('disabled',
				!editReady || this._selectedTaskIndex <= 0);
			$('#remote-task-move-down').prop('disabled',
				!editReady || this._selectedTaskIndex < 0 ||
				this._selectedTaskIndex >= this._tasks.length - 1);
		}
	}

	_setStepCardState(card, isActive, isCurrent) {
		card.toggleClass('is-inactive', !isActive);
		card.toggleClass('is-current', isCurrent);
	}

	_setStatus(message) {
		this._status.text(message || '');
	}

	_setBanner(type, message) {
		this._banner.removeClass('error');
		if (type === 'error')
			this._banner.addClass('error');
		this._banner.text(message || '');
		this._banner.show();
	}

	_clearBanner() {
		this._banner.hide();
		this._banner.text('');
		this._banner.removeClass('error');
	}

	_createEmptyTask() {
		return {
			id: this._nextTaskId(),
			name: '',
			requestText: '',
			description: '',
			fixedTime: '',
			stateTimeRangeStart: '',
			stateTimeRangeEnd: '',
			requiredState: '',
			answerableOnWatch: false,
			maximumRequestsPerDay: '',
			minimumIntervalRequestInMinutes: '',
			triggerType: 'FIXED_TIME',
			triggerFieldNames: ['combinedModePreferredTrigger'],
			digitalGuideDialogueId: '',
			extraFields: []
		};
	}

	_nextTaskId() {
		let maxId = 0;
		for (let task of this._tasks) {
			let taskId = parseInt(task.id, 10);
			if (!Number.isNaN(taskId) && taskId > maxId)
				maxId = taskId;
		}
		return maxId + 1;
	}

	_parseTaskXml(xml) {
		let parser = new DOMParser();
		let doc = parser.parseFromString(xml, 'application/xml');
		let parseError = doc.getElementsByTagName('parsererror');
		if (parseError.length > 0) {
			throw new Error(parseError[0].textContent.trim());
		}
		if (!doc.documentElement ||
				doc.documentElement.tagName !== 'ArrayList') {
			throw new Error('Root element must be ArrayList');
		}
		let tasks = [];
		for (let node of Array.from(doc.documentElement.children)) {
			if (node.tagName !== 'Task')
				continue;
			let task = this._createEmptyTask();
			task.extraFields = [];
			task.triggerFieldNames = [];
			for (let child of Array.from(node.children)) {
				let value = child.textContent || '';
				switch (child.tagName) {
				case 'id':
					task.id = parseInt(value, 10) || this._nextTaskId();
					break;
				case 'name':
					task.name = value;
					break;
				case 'requestText':
					task.requestText = value;
					break;
				case 'description':
					task.description = value;
					break;
				case 'fixedTime':
					task.fixedTime = this._normalizeParsedTime(value);
					break;
				case 'stateTimeRangeStart':
					task.stateTimeRangeStart = this._normalizeParsedTime(value);
					break;
				case 'stateTimeRangeEnd':
					task.stateTimeRangeEnd = this._normalizeParsedTime(value);
					break;
				case 'requiredState':
					task.requiredState = value;
					break;
				case 'answerableOnWatch':
					task.answerableOnWatch = value.trim().toLowerCase() === 'true';
					break;
				case 'maximumRequestsPerDay':
					task.maximumRequestsPerDay = value.trim();
					break;
				case 'minimumIntervalRequestInMinutes':
					task.minimumIntervalRequestInMinutes = value.trim();
					break;
				case 'combinedModePreferredTrigger':
				case 'TaskTriggerType':
					task.triggerType = value.trim() || 'FIXED_TIME';
					task.triggerFieldNames.push(child.tagName);
					break;
				case 'digitalGuideDialogueId':
					task.digitalGuideDialogueId = value;
					break;
				default:
					task.extraFields.push({
						name: child.tagName,
						value: value
					});
					break;
				}
			}
			if (task.triggerFieldNames.length === 0) {
				task.triggerFieldNames = ['combinedModePreferredTrigger'];
			}
			tasks.push(task);
		}
		return tasks;
	}

	_serializeTasksToXml(tasks) {
		let doc = document.implementation.createDocument('', 'ArrayList', null);
		for (let task of tasks) {
			let taskElement = doc.createElement('Task');
			this._appendXmlField(doc, taskElement, 'id', String(task.id));
			this._appendXmlField(doc, taskElement, 'name', task.name || '');
			this._appendXmlField(doc, taskElement, 'requestText',
				task.requestText || '');
			this._appendXmlField(doc, taskElement, 'description',
				task.description || '');
			this._appendOptionalXmlField(doc, taskElement, 'fixedTime',
				task.fixedTime);
			this._appendOptionalXmlField(doc, taskElement,
				'stateTimeRangeStart', task.stateTimeRangeStart);
			this._appendOptionalXmlField(doc, taskElement,
				'stateTimeRangeEnd', task.stateTimeRangeEnd);
			this._appendOptionalXmlField(doc, taskElement, 'requiredState',
				task.requiredState);
			this._appendXmlField(doc, taskElement, 'answerableOnWatch',
				task.answerableOnWatch ? 'true' : 'false');
			this._appendOptionalXmlField(doc, taskElement,
				'maximumRequestsPerDay', task.maximumRequestsPerDay);
			this._appendOptionalXmlField(doc, taskElement,
				'minimumIntervalRequestInMinutes',
				task.minimumIntervalRequestInMinutes);
			let triggerFields = Array.isArray(task.triggerFieldNames) &&
					task.triggerFieldNames.length > 0 ?
				task.triggerFieldNames : ['combinedModePreferredTrigger'];
			for (let triggerFieldName of triggerFields) {
				this._appendXmlField(doc, taskElement, triggerFieldName,
					task.triggerType || 'FIXED_TIME');
			}
			this._appendOptionalXmlField(doc, taskElement,
				'digitalGuideDialogueId', task.digitalGuideDialogueId);
			if (Array.isArray(task.extraFields)) {
				for (let extraField of task.extraFields) {
					if (!extraField || !extraField.name)
						continue;
					if (triggerFields.includes(extraField.name))
						continue;
					this._appendXmlField(doc, taskElement, extraField.name,
						extraField.value || '');
				}
			}
			doc.documentElement.appendChild(taskElement);
		}
		return new XMLSerializer().serializeToString(doc);
	}

	_appendXmlField(doc, parent, name, value) {
		let element = doc.createElement(name);
		element.textContent = value;
		parent.appendChild(element);
	}

	_appendOptionalXmlField(doc, parent, name, value) {
		if (value === null || value === undefined)
			return;
		let normalized = (value + '').trim();
		if (normalized.length === 0)
			return;
		this._appendXmlField(doc, parent, name, normalized);
	}

	_normalizeTimeInput(value) {
		if (!value)
			return '';
		if (/^\d{2}:\d{2}$/.test(value))
			return value + ':00';
		return value;
	}

	_normalizeParsedTime(value) {
		if (!value)
			return '';
		return this._normalizeTimeInput(value.trim());
	}

	_normalizeNumberInput(value) {
		let text = (value || '').toString().trim();
		return text.length === 0 ? '' : text;
	}

	_taskSummary(task) {
		let parts = [];
		if (task.fixedTime)
			parts.push('Fixed ' + task.fixedTime);
		if (task.stateTimeRangeStart || task.stateTimeRangeEnd) {
			parts.push('Window ' +
				(task.stateTimeRangeStart || '...') + ' - ' +
				(task.stateTimeRangeEnd || '...'));
		}
		if (task.requiredState)
			parts.push('State ' + task.requiredState);
		if (task.maximumRequestsPerDay)
			parts.push('Max ' + task.maximumRequestsPerDay + '/day');
		if (task.minimumIntervalRequestInMinutes) {
			parts.push('Min interval ' +
				task.minimumIntervalRequestInMinutes + ' min');
		}
		return parts.length > 0 ?
			parts.join(' | ') :
			'No trigger details configured';
	}
}

const DetoxTaskConfigurationSource = {
	APP: 'APP',
	WEB: 'WEB'
};

new RemoteTaskEditorPage();
