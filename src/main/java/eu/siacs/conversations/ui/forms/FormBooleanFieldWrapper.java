package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.widget.CheckBox;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;

public class FormBooleanFieldWrapper extends FormFieldWrapper {

	protected CheckBox checkBox;

	protected FormBooleanFieldWrapper(Context context, Field field) {
		super(context, field);
		checkBox = (CheckBox) view.findViewById(R.id.field);
	}

	@Override
	protected void setLabel(String label, boolean required) {
		CheckBox checkBox = (CheckBox) view.findViewById(R.id.field);
		checkBox.setText(createSpannableLabelString(label, required));
	}

	@Override
	public List<String> getValues() {
		List<String> values = new ArrayList<>();
		values.add(Boolean.toString(checkBox.isChecked()));
		return values;
	}

	@Override
	public boolean validates() {
		return checkBox.isChecked() || !field.isRequired();
	}

	@Override
	protected int getLayoutResource() {
		return R.layout.form_boolean;
	}
}
