package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import java.util.List;

import eu.siacs.conversations.xmpp.forms.Field;

public abstract class FormFieldWrapper {

	protected final Context context;
	protected final Field field;
	protected final View view;

	protected FormFieldWrapper(Context context, Field field) {
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

	public void submit() {
		this.field.setValues(getValues());
	}

	public View getView() {
		return view;
	}

	protected abstract void setLabel(String label, boolean required);

	abstract List<String> getValues();

	abstract boolean validates();

	abstract protected int getLayoutResource();

	protected static <F extends FormFieldWrapper> FormFieldWrapper createFromField(Class<F> c, Context context, Field field) {
		try {
			return c.getDeclaredConstructor(Context.class, Field.class).newInstance(context,field);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
