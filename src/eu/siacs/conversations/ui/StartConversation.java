package eu.siacs.conversations.ui;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import eu.siacs.conversations.R;

public class StartConversation extends XmppActivity {

	private Tab mContactsTab;
	private Tab mConferencesTab;
	private ViewPager mViewPager;
	private ListFragment mContactsListFragment = new ListFragment();
	private ListFragment mConferenceListFragment = new ListFragment();

	private TabListener mTabListener = new TabListener() {

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			mViewPager.setCurrentItem(tab.getPosition());
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {
			// TODO Auto-generated method stub

		}
	};

	private ViewPager.SimpleOnPageChangeListener mOnPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
		@Override
		public void onPageSelected(int position) {
			getActionBar().setSelectedNavigationItem(position);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start_conversation);
		mViewPager = (ViewPager) findViewById(R.id.start_conversation_view_pager);
		ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		mContactsTab = actionBar.newTab().setText(R.string.contacts)
				.setTabListener(mTabListener);
		mConferencesTab = actionBar.newTab().setText(R.string.conferences)
				.setTabListener(mTabListener);
		actionBar.addTab(mContactsTab);
		actionBar.addTab(mConferencesTab);

		
		mViewPager.setOnPageChangeListener(mOnPageChangeListener);
		mViewPager.setAdapter(new FragmentPagerAdapter(getFragmentManager()) {
			
			@Override
			public int getCount() {
				return 2;
			}
			
			@Override
			public Fragment getItem(int position) {
				if (position==0) {
					return mContactsListFragment;
				} else {
					return mConferenceListFragment;
				}
			}
		});

	}

	@Override
	void onBackendConnected() {
		// TODO Auto-generated method stub

	}

}
