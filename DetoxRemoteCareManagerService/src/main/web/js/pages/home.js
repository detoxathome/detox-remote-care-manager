class HomePage {
	constructor() {
		this._createView();
	}
	
	_createView() {
		menuController.selectMenuItem('home');
		$('#content').css('visibility', 'visible');
	}
}

new HomePage();
