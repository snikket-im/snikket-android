package eu.siacs.conversations.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.adapter.ListItemAdapter;

public abstract class AbstractSearchableListItemActivity extends XmppActivity {
	private ListView mListView;
	private final List<ListItem> listItems = new ArrayList<>();
	private ArrayAdapter<ListItem> mListItemsAdapter;

	private EditText mSearchEditText;

	private final MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {

		@Override
		public boolean onMenuItemActionExpand(final MenuItem item) {
			mSearchEditText.post(new Runnable() {

				@Override
				public void run() {
					mSearchEditText.requestFocus();
					final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.showSoftInput(mSearchEditText,
							InputMethodManager.SHOW_IMPLICIT);
				}
			});

			return true;
		}

		@Override
		public boolean onMenuItemActionCollapse(final MenuItem item) {
			final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(),
					InputMethodManager.HIDE_IMPLICIT_ONLY);
			mSearchEditText.setText("");
			filterContacts();
			return true;
		}
	};

	private final TextWatcher mSearchTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(final Editable editable) {
			filterContacts(editable.toString());
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count,
				final int after) {
		}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before,
				final int count) {
		}
	};

	public ListView getListView() {
		return mListView;
	}

	public List<ListItem> getListItems() {
		return listItems;
	}

	public EditText getSearchEditText() {
		return mSearchEditText;
	}

	public ArrayAdapter<ListItem> getListItemAdapter() {
		return mListItemsAdapter;
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_choose_contact);
		mListView = (ListView) findViewById(R.id.choose_contact_list);
		mListView.setFastScrollEnabled(true);
		mListItemsAdapter = new ListItemAdapter(this, listItems);
		mListView.setAdapter(mListItemsAdapter);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.choose_contact, menu);
		final MenuItem menuSearchView = menu.findItem(R.id.action_search);
		final View mSearchView = menuSearchView.getActionView();
		mSearchEditText = (EditText) mSearchView
			.findViewById(R.id.search_field);
		mSearchEditText.addTextChangedListener(mSearchTextWatcher);
		menuSearchView.setOnActionExpandListener(mOnActionExpandListener);
		return true;
	}

	protected void filterContacts() {
		filterContacts(null);
	}

	protected abstract void filterContacts(final String needle);

	@Override
	void onBackendConnected() {
		filterContacts();
	}
}
