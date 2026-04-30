package nl.detoxathome.remotecaremanager.client.model.compat;

import nl.detoxathome.remotecaremanager.client.model.Gender;

/**
 * Possible genders.
 * 
 * @author Dennis Hofs (RRD)
 */
public enum GenderV0 {
	MALE,
	FEMALE;

	public static GenderV0 fromGender(Gender gender) {
		if (gender == null)
			return null;
		switch (gender) {
			case MALE:
				return GenderV0.MALE;
			case FEMALE:
				return GenderV0.FEMALE;
			default:
				return null;
		}
	}

	public Gender toGender() {
		if (this == null)
			return null;
		switch (this) {
			case MALE:
				return Gender.MALE;
			case FEMALE:
				return Gender.FEMALE;
			default:
				return null;
		}
	}
}
