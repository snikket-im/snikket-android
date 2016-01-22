package eu.siacs.conversations.ui.forms;

import android.content.Context;
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
		editText.setSingleLine("text-single".equals(field.getType()));
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

	@Override
	List<String> getValues() {
		List<String> values = new ArrayList<>();
		for (String line : editText.getText().toString().split("\\n")) {
			values.add(line);
		}
		return values;
	}

	@Override
	protected int getLayoutResource() {
		return R.layout.form_text;
	}
}
