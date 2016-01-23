package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;

public class FormTextFieldWrapper extends FormFieldWrapper {

	protected EditText editText;

	protected FormTextFieldWrapper(Context context, Field field) {
		super(context, field);
		editText = (EditText) view.findViewById(R.id.field);
		editText.setSingleLine(!"text-multi".equals(field.getType()));
		if ("text-private".equals(field.getType())) {
			editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		}
	}

	@Override
	protected void setLabel(String label, boolean required) {
		TextView textView = (TextView) view.findViewById(R.id.label);
		SpannableString spannableString = new SpannableString(label + (required ? " *" : ""));
		if (required) {
			int start = label.length();
			int end = label.length() + 2;
			spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, 0);
			spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.accent)), start, end, 0);
		}
		textView.setText(spannableString);
	}

	protected String getValue() {
		return editText.getText().toString();
	}

	@Override
	public List<String> getValues() {
		List<String> values = new ArrayList<>();
		for (String line : getValue().split("\\n")) {
			values.add(line);
		}
		return values;
	}

	@Override
	public boolean validates() {
		return getValue().trim().length() > 0 || !field.isRequired();
	}

	@Override
	protected int getLayoutResource() {
		return R.layout.form_text;
	}
}
