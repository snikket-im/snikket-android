package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.xmpp.forms.Field;

public abstract class FormFieldWrapper {

	protected final Context context;
	protected final Field field;
	protected final View view;
	OnFormFieldValuesEdited onFormFieldValuesEditedListener;

	FormFieldWrapper(Context context, Field field) {
		this.context = context;
		this.field = field;
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.view = inflater.inflate(getLayoutResource(), null);
		String label = field.getLabel();
		if (label == null) {
			label = field.getFieldName();
		}
		setLabel(label, field.isRequired());
	}

	public final void submit() {
		this.field.setValues(getValues());
	}

	public final View getView() {
		return view;
	}

	protected abstract void setLabel(String label, boolean required);

	abstract List<String> getValues();

	protected abstract void setValues(List<String> values);

	abstract boolean validates();

	abstract protected int getLayoutResource();

	abstract void setReadOnly(boolean readOnly);

	protected SpannableString createSpannableLabelString(String label, boolean required) {
		SpannableString spannableString = new SpannableString(label + (required ? " *" : ""));
		if (required) {
			int start = label.length();
			int end = label.length() + 2;
			spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, 0);
			spannableString.setSpan(new ForegroundColorSpan(StyledAttributes.getColor(context,R.attr.colorAccent)), start, end, 0);
		}
		return spannableString;
	}

	protected void invokeOnFormFieldValuesEdited() {
		if (this.onFormFieldValuesEditedListener != null) {
			this.onFormFieldValuesEditedListener.onFormFieldValuesEdited();
		}
	}

	public boolean edited() {
		return !field.getValues().equals(getValues());
	}

	public void setOnFormFieldValuesEditedListener(OnFormFieldValuesEdited listener) {
		this.onFormFieldValuesEditedListener = listener;
	}

	protected static <F extends FormFieldWrapper> FormFieldWrapper createFromField(Class<F> c, Context context, Field field) {
		try {
			F fieldWrapper = c.getDeclaredConstructor(Context.class, Field.class).newInstance(context,field);
			fieldWrapper.setValues(field.getValues());
			return fieldWrapper;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public interface OnFormFieldValuesEdited {
		void onFormFieldValuesEdited();
	}
}
