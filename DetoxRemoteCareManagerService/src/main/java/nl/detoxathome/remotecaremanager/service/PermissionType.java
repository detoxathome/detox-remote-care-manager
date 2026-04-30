package nl.detoxathome.remotecaremanager.service;

import nl.detoxathome.remotecaremanager.client.model.PermissionRecord;

import java.util.ArrayList;
import java.util.List;

public class PermissionType {
	private String name;

	public PermissionType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public List<PermissionRecord> getChildren(PermissionRecord record) {
		return new ArrayList<>();
	}
}
