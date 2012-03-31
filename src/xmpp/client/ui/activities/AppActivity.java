package xmpp.client.ui.activities;

import xmpp.client.R;
import xmpp.client.service.Service;
import xmpp.client.service.Signals;
import xmpp.client.service.account.AccountInfo;
import xmpp.client.service.chat.ChatMessage;
import xmpp.client.service.chat.ChatSession;
import xmpp.client.service.chat.multi.MultiUserChatInfo;
import xmpp.client.service.chat.multi.MultiUserChatSession;
import xmpp.client.service.handlers.SimpleMessageHandler;
import xmpp.client.service.handlers.SimpleMessageHandlerClient;
import xmpp.client.service.user.User;
import xmpp.client.service.user.UserState;
import xmpp.client.service.user.contact.Contact;
import xmpp.client.ui.account.AccountLogin;
import xmpp.client.ui.adapter.ChatAdapter;
import xmpp.client.ui.adapter.ChatUserListAdapter;
import xmpp.client.ui.adapter.GroupAdapter;
import xmpp.client.ui.adapter.RosterAdapter;
import xmpp.client.ui.dialogs.AddConferenceDialog;
import xmpp.client.ui.dialogs.AddUserDialog;
import xmpp.client.ui.dialogs.ResultListener;
import xmpp.client.ui.dialogs.ResultProducer;
import xmpp.client.ui.dialogs.StatusSelectorDialog;
import xmpp.client.ui.provider.ChatProvider;
import xmpp.client.ui.provider.ConferenceProvider;
import xmpp.client.ui.provider.ContactProvider;
import xmpp.client.ui.provider.ContactProviderListener;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.devsmart.android.ui.HorizontalListView;

public class AppActivity extends Activity implements
		SimpleMessageHandlerClient, ResultListener, ContactProviderListener {
	private static final String TAG = AppActivity.class.getName();
	private static final int DIALOG_STATUSSELECTOR = 1;
	private static final int DIALOG_ADDUSER = 2;
	private static final int DIALOG_ADDCONFERENCE = 3;
	private static final int VIEW_ROSTER = 0;
	private static final int VIEW_CHAT = 1;

	private int mCurrentView = -1;
	private String mUID;
	private String mMUC;

	boolean doCheck = true;

	OnItemClickListener itemClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> arg0, View view, int item,
				long id) {
			if (item == 0) {
				goStatusChange();
			} else if (mCurrentNavigation == 3) {
				goConference(((MultiUserChatInfo)mRosterAdapter.getItem(item)).getJid());
			} else {
				goChat(((Contact) mRosterAdapter.getItem(item)).getUserLogin());
			}
		}
	};
	private ActionBar mActionBar;
	private final ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			handleIntent(getIntent());
			try {
				final Message msg = Message.obtain(null,
						Signals.SIG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);

				if (doCheck) {
					checkState();
				}

			} catch (final RemoteException e) {
				Log.i(TAG, "ServiceConnection.onServiceConnected", e);
			}

		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	int mCurrentNavigation = 2;

	GroupAdapter mGroupAdapter;

	boolean mIsBound;

	ListView mListView;

	private SimpleMessageHandler mMessageHandler;
	private Messenger mMessenger;
	private final OnNavigationListener mNavigationListener = new OnNavigationListener() {

		@Override
		public boolean onNavigationItemSelected(int itemPosition, long itemId) {
			mCurrentNavigation = itemPosition;
			mRosterAdapter.setActiveGroup((String) mGroupAdapter
					.getItem(mCurrentNavigation));
			return true;
		}
	};

	private RosterAdapter mRosterAdapter;

	private ContactProvider mContactProvider;

	private Messenger mService = null;

	private StatusSelectorDialog mStatusSelectorDialog;
	private AddUserDialog mAddUserDialog;
	private AddConferenceDialog mAddConferenceDialog;
	private ListView mMessageHolder;
	private HorizontalListView mUserHolder;
	private EditText mSendText;
	private User mUser;
	private Menu mMenu;
	private ChatProvider mChatProvider;
	private ChatAdapter mChatAdapter;
	private ChatUserListAdapter mChatUserListAdapter;
	private final OnClickListener sendClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			doSend();
		}
	};

	private ChatSession mSession;
	private ConferenceProvider mConferenceProvider;

	public void afterInit() {
		mGroupAdapter = new GroupAdapter(this, mContactProvider);
		if (mCurrentView == VIEW_ROSTER) {
			mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			mActionBar.setDisplayShowTitleEnabled(false);
			mActionBar.setDisplayShowCustomEnabled(true);
			mActionBar.setListNavigationCallbacks(mGroupAdapter,
					mNavigationListener);
			mActionBar.setSelectedNavigationItem(mCurrentNavigation);
			mRosterAdapter = new RosterAdapter(mListView.getContext(),
					mContactProvider, mConferenceProvider);
			mListView.setAdapter(mRosterAdapter);
			mRosterAdapter.notifyDataSetChanged();
		} else if (mCurrentView == VIEW_CHAT) {
			if (mUID != null) {
				final Message msg = Message.obtain(null,
						Signals.SIG_OPEN_CHATSESSION);
				msg.replyTo = mMessenger;
				final Bundle b = new Bundle();
				b.putString("uid", mUID);
				msg.setData(b);
				try {
					mService.send(msg);
				} catch (final RemoteException e) {
					Log.i(TAG, "handleIntent", e);
				}
				setTitle(mUID);
			} else if (mMUC != null) {
				final Message msg = Message.obtain(null,
						Signals.SIG_OPEN_MUC_CHATSESSION);
				msg.replyTo = mMessenger;
				final Bundle b = new Bundle();
				b.putString("muc", mMUC);
				msg.setData(b);
				try {
					mService.send(msg);
				} catch (final RemoteException e) {
					Log.i(TAG, "handleIntent", e);
				}
				setTitle(mMUC);
			}
		}
	}

	void checkState() {
		if (mService != null) {
			final Message msg = Message.obtain(null, Signals.SIG_IS_READY);
			msg.replyTo = mMessenger;
			try {
				mService.send(msg);
			} catch (final RemoteException e) {
				Log.i(TAG, "checkState", e);
			}
			doCheck = false;
		} else {
			doCheck = true;
		}
	}

	@Override
	public void contactProviderChanged(ContactProvider contactProvider) {
		if (mRosterAdapter != null) {
			mRosterAdapter.notifyDataSetChanged();
		}
		if (mGroupAdapter != null) {
			mGroupAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void contactProviderReady(ContactProvider contactProvider) {
		afterInit();
	}

	void doBindService() {
		startService(new Intent(AppActivity.this, Service.class));
		bindService(new Intent(AppActivity.this, Service.class), mConnection,
				Context.BIND_ABOVE_CLIENT);
		mIsBound = true;
	}

	public void doLogin() {
		updateStatus(UserState.STATUS_INITIALIZING);
		final AccountManager am = AccountManager.get(this);
		final Account[] accounts = am
				.getAccountsByType((String) getText(R.string.account_type));
		if (accounts.length > 0) {
			final Account account = accounts[0];
			final String login = account.name;
			mContactProvider.getMeContact().getUser().setUserLogin(login);
			mRosterAdapter.notifyDataSetChanged();
			final String pass = am.getPassword(account);
			final Message msg = Message.obtain(null, Signals.SIG_INIT);
			final Bundle b = new Bundle();
			b.putParcelable("AccountInfo", new AccountInfo(login, pass));
			msg.setData(b);
			msg.replyTo = mMessenger;
			try {
				mService.send(msg);
			} catch (final RemoteException e) {
				Log.i(TAG, "doLogin", e);
			}
		} else {
			goLogin();
		}
	}

	private void doSend() {
		if (mSession != null && mSendText.getText().length() > 0) {
			final Message msg = Message.obtain(null, Signals.SIG_SEND_MESSAGE);
			msg.replyTo = mMessenger;
			final Bundle b = new Bundle();
			b.putParcelable("session", mSession);
			b.putString("text", mSendText.getText().toString());
			msg.setData(b);
			try {
				mService.send(msg);
			} catch (final RemoteException e) {
				Log.e(TAG, "doSend", e);
			}
		}
		mSendText.setText("");
	}

	void doUnbindService() {
		if (mIsBound) {
			if (mService != null) {
				try {
					final Message msg = Message.obtain(null,
							Signals.SIG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);

				} catch (final RemoteException e) {
					Log.i(TAG, "doUnbindService", e);
				}
			}

			unbindService(mConnection);
			mIsBound = false;
		}
	}

	private void goAddConference() {
		showDialog(DIALOG_ADDCONFERENCE);
	}

	private void goAddUser() {
		showDialog(DIALOG_ADDUSER);
	}

	private void goChat(String userLogin) {
		final Intent i = new Intent(AppActivity.this, AppActivity.class);
		i.setData(Uri.parse("imto://jabber/" + userLogin));
		handleIntent(i);
	}

	private void goConference(String muc) {
		final Intent i = new Intent(AppActivity.this, AppActivity.class);
		i.setData(Uri.parse("imto://jabbermuc/" + muc));
		handleIntent(i);
	}

	private void goLogin() {
		startActivity(new Intent(AppActivity.this, AccountLogin.class));
	}

	private void goStatusChange() {
		showDialog(DIALOG_STATUSSELECTOR);
	}

	public void handleIntent(Intent intent) {
		mMUC = null;
		mUID = null;
		if (intent.getData() != null) {
			final Uri uri = intent.getData();
			if (uri.getScheme().equalsIgnoreCase("imto")
					&& uri.getHost().equalsIgnoreCase("jabber")) {
				mUID = uri.getLastPathSegment();
			} else if (uri.getScheme().equalsIgnoreCase("imto")
					&& uri.getHost().equalsIgnoreCase("jabbermuc")) {
				mMUC = uri.getLastPathSegment();
			}
		}
		if ((mUID != null || mMUC != null) && mCurrentView != VIEW_CHAT) {
			openChat();
		} else if ((mUID == null && mMUC == null)
				&& mCurrentView != VIEW_ROSTER) {
			openRoster(null);
		}
		if (mUID != null) {
			if (mService != null) {
				final Message msg = Message.obtain(null,
						Signals.SIG_OPEN_CHATSESSION);
				msg.replyTo = mMessenger;
				final Bundle b = new Bundle();
				b.putString("uid", mUID);
				msg.setData(b);
				try {
					mService.send(msg);
				} catch (final RemoteException e) {
					Log.i(TAG, "handleIntent", e);
				}
			}
			setTitle(mUID);
		} else if (mMUC != null) {
			if (mService != null) {
				final Message msg = Message.obtain(null,
						Signals.SIG_OPEN_MUC_CHATSESSION);
				msg.replyTo = mMessenger;
				final Bundle b = new Bundle();
				b.putString("muc", mMUC);
				msg.setData(b);
				try {
					mService.send(msg);
				} catch (final RemoteException e) {
					Log.i(TAG, "handleIntent", e);
				}
			}
			setTitle(mMUC);
		}
	}

	@Override
	public void handleMessage(Message msg) {
		try {
			final Bundle b = msg.getData();
			switch (msg.what) {
			case Signals.SIG_OPEN_CHATSESSION:
				b.setClassLoader(ChatSession.class.getClassLoader());
				mSession = b.getParcelable("session");
				b.setClassLoader(User.class.getClassLoader());
				mUser = (User) b.getParcelable("user");
				if (mUser.supportsAudio()) {
					mMenu.findItem(R.id.menu_call).setVisible(true);
				} else {
					mMenu.findItem(R.id.menu_call).setVisible(false);
				}
				mActionBar.setTitle(mUser.getDisplayName());
				mActionBar
						.setSubtitle(mUser.getUserState().getStatusText(this));
				mChatProvider = new ChatProvider(
						mContactProvider.getMeContact(), mSession);
				mChatAdapter = new ChatAdapter(this, mChatProvider,
						mContactProvider);
				mMessageHolder.setAdapter(mChatAdapter);
				mUserHolder.setVisibility(View.GONE);
				break;
			case Signals.SIG_OPEN_MUC_CHATSESSION:
				b.setClassLoader(ChatSession.class.getClassLoader());
				mSession = b.getParcelable("session");
				mMenu.findItem(R.id.menu_call).setVisible(false);
				mActionBar.setTitle(mSession.getSessionID());
				mActionBar.setSubtitle(b.getString("subject"));
				mChatProvider = new ChatProvider(
						mContactProvider.getMeContact(), mSession);
				mChatAdapter = new ChatAdapter(this, mChatProvider,
						mContactProvider);
				mMessageHolder.setAdapter(mChatAdapter);
				mChatUserListAdapter = new ChatUserListAdapter(this,
						mContactProvider, mChatProvider);
				mUserHolder.setAdapter(mChatUserListAdapter);
				// TODO: FIX UserHolder and make it nicelooking!
				mUserHolder.setVisibility(View.GONE);
				break;
			case Signals.SIG_CHAT_SESSION_UPDATE:
				b.setClassLoader(ChatSession.class.getClassLoader());
				final ChatSession session1 = b.getParcelable("session");
				if (session1.equals(mSession)) {
					mSession = session1;
					mChatProvider.setSession(mSession);
				}
				if (mSession instanceof MultiUserChatSession) {
					mActionBar.setSubtitle(((MultiUserChatSession) mSession)
							.getSubject());
				}
				break;

			case Signals.SIG_MESSAGE_SENT:
			case Signals.SIG_MESSAGE_GOT:
				b.setClassLoader(ChatMessage.class.getClassLoader());
				final ChatMessage message = b.getParcelable("message");
				b.setClassLoader(ChatSession.class.getClassLoader());
				final ChatSession session = b.getParcelable("session");
				if (session.equals(mSession)) {
					mChatProvider.addMessage(message);
				} else {
					// TODO: Do not hope everything is right!
					mChatProvider.addMessage(message);
				}
				if (mChatAdapter != null) {
					mChatAdapter.notifyDataSetChanged();
				}
				break;
			case Signals.SIG_ROSTER_GET_CONTACTS_ERROR:
				doUnbindService();
				finish();
				break;
			case Signals.SIG_IS_READY:
				updateStatus(UserState.STATUS_AVAILABLE);
				afterInit();
				break;
			case Signals.SIG_IS_NOT_READY:
				doLogin();
				break;
			case Signals.SIG_INIT_ERROR:
			case Signals.SIG_CONNECT_ERROR:
			case Signals.SIG_LOGIN_ERROR:
				goLogin();
			case Signals.SIG_INIT:
				updateStatus(UserState.STATUS_CONNECTING);
				break;
			case Signals.SIG_CONNECT:
				updateStatus(UserState.STATUS_LOGGING_IN);
				break;
			case Signals.SIG_LOGIN:
				updateStatus(UserState.STATUS_LOADING_ROSTER);
				checkState();
				break;
			}
		} catch (final Exception e) {
			Log.i(TAG, "IncomingHandler.handleMessage", e);
			doUnbindService();
		}

	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mMessageHandler = new SimpleMessageHandler(this);
		mMessenger = new Messenger(mMessageHandler);
		doBindService();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		switch (id) {
		case DIALOG_STATUSSELECTOR:
			mStatusSelectorDialog = new StatusSelectorDialog(this,
					mContactProvider.getMeContact().getUserState());
			mStatusSelectorDialog.setResultListener(this);
			dialog = mStatusSelectorDialog.getAlertDialog();
			break;
		case DIALOG_ADDUSER:
			mAddUserDialog = new AddUserDialog(this);
			mAddUserDialog.setResultListener(this);
			dialog = mAddUserDialog.getAlertDialog();
			break;
		case DIALOG_ADDCONFERENCE:
			mAddConferenceDialog = new AddConferenceDialog(this);
			mAddConferenceDialog.setResultListener(this);
			dialog = mAddConferenceDialog.getAlertDialog();
			break;
		default:
			dialog = null;
		}
		return dialog;

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();
		final MenuInflater inflater = getMenuInflater();
		switch (mCurrentView) {
		case VIEW_ROSTER:
			inflater.inflate(R.menu.roster_actionbar, menu);
		case VIEW_CHAT:
			inflater.inflate(R.menu.chat_actionbar, menu);
		}
		mMenu = menu;
		return true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_disconnect:
			stopService(new Intent(AppActivity.this, Service.class));
			finish();
			break;
		case R.id.menu_add_user:
			goAddUser();
			break;
		case R.id.menu_add_conference:
			goAddConference();
			break;
		case R.id.menu_change_status:
			goStatusChange();
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// super.onNewIntent(intent);
		setIntent(intent);
		handleIntent(intent);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			if (mCurrentView == VIEW_CHAT) {
				final Message msg = Message.obtain(null,
						Signals.SIG_DISABLE_CHATSESSION);
				final Bundle b = new Bundle();
				b.putParcelable("session", mSession);
				msg.setData(b);
				msg.replyTo = mMessenger;
				try {
					mService.send(msg);
				} catch (final RemoteException e) {
					Log.e(TAG, "disableChat", e);
				}
			}
			openRoster(null);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onResultAvailable(ResultProducer resultProducer) {
		if (resultProducer.getResult() == null) {
			return;
		}
		if (resultProducer instanceof StatusSelectorDialog) {
			updateStatus((UserState) resultProducer.getResult());
		} else if (resultProducer instanceof AddUserDialog) {
			userAdd((String) resultProducer.getResult());
		} else if (resultProducer instanceof AddConferenceDialog) {
			goConference((String) resultProducer.getResult());
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("tab", mCurrentNavigation);
	}

	public void openChat() {
		setContentView(R.layout.chat);
		Log.i(TAG, "openChat");
		mCurrentView = VIEW_CHAT;
		if (mMenu != null) {
			onCreateOptionsMenu(mMenu);
		}
		final ImageButton btn_send = (ImageButton) findViewById(R.id.btn_send);
		btn_send.setOnClickListener(sendClickListener);
		mMessageHolder = (ListView) findViewById(R.id.message_container);
		mUserHolder = (HorizontalListView) findViewById(R.id.user_container);
		mSendText = (EditText) findViewById(R.id.text_send);
		mSendText.setOnEditorActionListener(new OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					doSend();
					return true;
				} else {
					return false;
				}
			}

		});

		mActionBar = getActionBar();
		mActionBar.setTitle(getText(R.string.process_loading));
		mActionBar.setDisplayHomeAsUpEnabled(true);
		mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		mActionBar.setDisplayShowTitleEnabled(true);
		mActionBar.setDisplayShowCustomEnabled(false);
	}

	public void openRoster(Bundle savedInstanceState) {
		setContentView(R.layout.roster);
		Log.i(TAG, "openRoster");
		mCurrentView = VIEW_ROSTER;
		if (mMenu != null) {
			onCreateOptionsMenu(mMenu);
		}
		mListView = (ListView) findViewById(R.id.list_roster);
		mListView.setOnItemClickListener(itemClickListener);
		mActionBar = getActionBar();
		mActionBar.setTitle(getText(R.string.process_loading));
		mActionBar.setDisplayHomeAsUpEnabled(false);

		if (mConferenceProvider == null) {
			mConferenceProvider = new ConferenceProvider(mMessenger, mService, mMessageHandler);
		}
		if (mContactProvider == null) {
			mContactProvider = new ContactProvider(mMessenger, mService, this,
					this, mMessageHandler);
		}
		if (mRosterAdapter == null) {
			mRosterAdapter = new RosterAdapter(mListView.getContext(),
					mContactProvider, mConferenceProvider);
		}
		mListView.setAdapter(mRosterAdapter);

		if (savedInstanceState != null && savedInstanceState.containsKey("tab")) {
			mCurrentNavigation = savedInstanceState.getInt("tab");
		}

		if (mGroupAdapter != null) {
			mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			mActionBar.setDisplayShowTitleEnabled(false);
			mActionBar.setDisplayShowCustomEnabled(true);
			mActionBar.setListNavigationCallbacks(mGroupAdapter,
					mNavigationListener);
			mActionBar.setSelectedNavigationItem(mCurrentNavigation);
		}
	}

	private void updateStatus(int status) {
		updateStatus(new UserState(status, null));
	}

	private void updateStatus(UserState userState) {
		mContactProvider.getMeContact().getUser().setUserState(userState);
		mRosterAdapter.notifyDataSetChanged();
		final Bundle b = new Bundle();
		b.putParcelable("state", userState);
		final Message msg = Message.obtain(null, Signals.SIG_SET_STATUS);
		msg.replyTo = mMessenger;
		msg.setData(b);
		try {
			mService.send(msg);
		} catch (final RemoteException e) {
			Log.i(TAG, "updateStatus", e);
		}
	}

	private void userAdd(String uid) {
		final Bundle b = new Bundle();
		b.putString("uid", uid);
		final Message msg = Message.obtain(null, Signals.SIG_ROSTER_ADD);
		msg.replyTo = mMessenger;
		msg.setData(b);
		try {
			mService.send(msg);
		} catch (final RemoteException e) {
			Log.i(TAG, "updateStatus", e);
		}
	}
}
