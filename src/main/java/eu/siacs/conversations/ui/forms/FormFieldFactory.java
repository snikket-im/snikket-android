package eu.siacs.conversations.ui.forms;

import android.content.Context;

import java.util.Hashtable;

import eu.siacs.conversations.xmpp.forms.Field;



public class FormFieldFactory {

	private static final Hashtable<String, Class> typeTable = new Hashtable<>();

	static {
		typeTable.put("text-single", FormTextFieldWrapper.class);
		typeTable.put("text-multi", FormTextFieldWrapper.class);
	}

	public static FormFieldWrapper createFromField(Context context, Field field) {
		Class clazz = typeTable.get(field.getType());
		return FormFieldWrapper.createFromField(clazz, context, field);
	}
}
