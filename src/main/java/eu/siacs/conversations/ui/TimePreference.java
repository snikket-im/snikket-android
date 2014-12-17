package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

public class TimePreference extends DialogPreference implements Preference.OnPreferenceChangeListener {
	private TimePicker picker = null;
	public final static long DEFAULT_VALUE = 0;

	public TimePreference(final Context context, final AttributeSet attrs) {
		super(context, attrs, 0);
		this.setOnPreferenceChangeListener(this);
	}

	protected void setTime(final long time) {
		persistLong(time);
		notifyDependencyChange(shouldDisableDependents());
		notifyChanged();
	}

	protected void updateSummary(final long time) {
		final DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(getContext());
		final Date date = new Date(time);
		setSummary(dateFormat.format(date.getTime()));
	}

	@Override
	protected View onCreateDialogView() {
		picker = new TimePicker(getContext());
		picker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(getContext()));
		return picker;
	}

	protected Calendar getPersistedTime() {
		final Calendar c = Calendar.getInstance();
		c.setTimeInMillis(getPersistedLong(DEFAULT_VALUE));

		return c;
	}

	@SuppressWarnings("NullableProblems")
	@Override
	protected void onBindDialogView(final View v) {
		super.onBindDialogView(v);
		final Calendar c = getPersistedTime();

		picker.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
		picker.setCurrentMinute(c.get(Calendar.MINUTE));
	}

	@Override
	protected void onDialogClosed(final boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (positiveResult) {
			final Calendar c = Calendar.getInstance();
			c.set(Calendar.MINUTE, picker.getCurrentMinute());
			c.set(Calendar.HOUR_OF_DAY, picker.getCurrentHour());


			if (!callChangeListener(c.getTimeInMillis())) {
				return;
			}

			setTime(c.getTimeInMillis());
		}
	}

	@Override
	protected Object onGetDefaultValue(final TypedArray a, final int index) {
		return a.getInteger(index, 0);
	}

	@Override
	protected void onSetInitialValue(final boolean restorePersistedValue, final Object defaultValue) {
		long time;
		if (defaultValue == null) {
			time = restorePersistedValue ? getPersistedLong(DEFAULT_VALUE) : DEFAULT_VALUE;
		} else if (defaultValue instanceof Long) {
			time = restorePersistedValue ? getPersistedLong((Long) defaultValue) : (Long) defaultValue;
		} else if (defaultValue instanceof Calendar) {
			time = restorePersistedValue ? getPersistedLong(((Calendar)defaultValue).getTimeInMillis()) : ((Calendar)defaultValue).getTimeInMillis();
		} else {
			time = restorePersistedValue ? getPersistedLong(DEFAULT_VALUE) : DEFAULT_VALUE;
		}

		setTime(time);
		updateSummary(time);
	}

	@Override
	public boolean onPreferenceChange(final Preference preference, final Object newValue) {
		((TimePreference) preference).updateSummary((Long)newValue);
		return true;
	}
}
