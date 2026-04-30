class InfoPage {
	constructor() {
		this._createView();
	}

	_createView() {
		menuController.selectMenuItem('info');
		$('#content').css('visibility', 'visible');
	}
}

new InfoPage();
