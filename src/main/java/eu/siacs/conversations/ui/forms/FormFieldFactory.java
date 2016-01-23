package eu.siacs.conversations.ui.forms;

import android.content.Context;

import java.util.Hashtable;

import eu.siacs.conversations.xmpp.forms.Field;



public class FormFieldFactory {

	private static final Hashtable<String, Class> typeTable = new Hashtable<>();

	static {
		typeTable.put("text-single", FormTextFieldWrapper.class);
		typeTable.put("text-multi", FormTextFieldWrapper.class);
		typeTable.put("text-private", FormTextFieldWrapper.class);
		typeTable.put("jid-single", FormJidSingleFieldWrapper.class);
		typeTable.put("boolean", FormBooleanFieldWrapper.class);
	}

	protected static FormFieldWrapper createFromField(Context context, Field field) {
		Class clazz = typeTable.get(field.getType());
		if (clazz == null) {
			clazz = FormTextFieldWrapper.class;
		}
		return FormFieldWrapper.createFromField(clazz, context, field);
	}
}
