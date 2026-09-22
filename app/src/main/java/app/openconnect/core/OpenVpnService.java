/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
 * Copyright (c) 2013, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */

package app.openconnect.core;

import android.Manifest.permission;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.*;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import app.openconnect.MainActivity;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.VPNLog.LogArrayAdapter;
import app.openconnect.fragments.FeedbackFragment;

import java.net.InetAddress;
import java.util.Date;
import java.util.Locale;

import org.infradead.libopenconnect.LibOpenConnect;
import org.infradead.libopenconnect.LibOpenConnect.VPNStats;

public class OpenVpnService extends VpnService {

	public static final String TAG = "OConnect";

	public static final String START_SERVICE = "io.pengyue.oconnect.START_SERVICE";
	public static final String START_SERVICE_STICKY = "io.pengyue.oconnect.START_SERVICE_STICKY";
	public static final String ALWAYS_SHOW_NOTIFICATION = "io.pengyue.oconnect.NOTIFICATION_ALWAYS_VISIBLE";

	public static final String ACTION_VPN_STATUS = "io.pengyue.oconnect.VPN_STATUS";
	public static final String EXTRA_CONNECTION_STATE = "io.pengyue.oconnect.connectionState";
	public static final String EXTRA_UUID = "io.pengyue.oconnect.UUID";
	public static final String PREF_NOTIFY_USER_INPUT = "notify_user_input";

	// These are valid in the CONNECTED state
	public VpnProfile profile;
	public LibOpenConnect.IPInfo ipInfo;
	public String serverName;
	public Date startTime;

	private DeviceStateReceiver mDeviceStateReceiver;
	private SharedPreferences mPrefs;
	private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
			(preferences, key) -> {
				if (PREF_NOTIFY_USER_INPUT.equals(key)) {
					updateNotification();
				}
			};

	private KeepAlive mKeepAlive;

	private final IBinder mBinder = new LocalBinder();

	private String mUUID;
	private int mStartId;

	private Thread mVPNThread;
	private OpenConnectManagementThread mVPN;

	private UserDialog mDialog;
	private Context mDialogContext;

	private final int NOTIFICATION_ID = 1;
	private static final String NOTIFICATION_CHANNEL_CONNECTION = "vpn_connection";
	private static final String NOTIFICATION_CHANNEL_INPUT = "vpn_input";
	private int mActivityConnections;
	private boolean mForegroundActive;

	private int mConnectionState = OpenConnectManagementThread.STATE_DISCONNECTED;
	private String mConnectionStateNames[];
	private VPNStats mStats = new VPNStats();

	private VPNLog mVPNLog = new VPNLog();
	private Handler mHandler = new Handler();

	public class LocalBinder extends Binder {
		public OpenVpnService getService() {
			// Return this instance of LocalService so clients can call public methods
			return OpenVpnService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		String action = intent.getAction();
		if( action !=null && action.equals(START_SERVICE))
			return mBinder;
		else
			return super.onBind(intent);
	}

	@Override
	public void onRevoke() {
		Log.i(TAG, "VPN access has been revoked");
		stopVPN();
	}

	@Override
	public void onCreate() {
		// Restore service state from disk if available
		// This gets overwritten if somebody calls startService()
                try {
                        VpnService.prepare(this);
                } catch (Exception ignored) {}
		app.openconnect.LocaleUtil.apply(getApplicationContext());

		mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mPrefs.registerOnSharedPreferenceChangeListener(mPreferenceListener);
		mUUID = mPrefs.getString(ProfileManager.LAST_USED_PROFILE, "");

		mVPNLog.restoreFromFile(getCacheDir().getAbsolutePath() + "/logdata.ser");
		mConnectionStateNames = getResources().getStringArray(R.array.connection_states);
		createNotificationChannel();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return;
		}

		NotificationChannel connectionChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_CONNECTION,
				getString(R.string.notification_channel_vpn),
				NotificationManager.IMPORTANCE_LOW);
		connectionChannel.setDescription(getString(R.string.notification_touch_here));

		NotificationChannel inputChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_INPUT,
				getString(R.string.notification_channel_input),
				NotificationManager.IMPORTANCE_DEFAULT);
		inputChannel.setDescription(getString(R.string.notification_touch_here));

		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.createNotificationChannel(connectionChannel);
			manager.createNotificationChannel(inputChannel);
		}
	}

	@Override
	public void onDestroy() {
		mPrefs.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
		killVPNThread(true);
		unregisterReceivers();
		stopForegroundNotification();
		mVPNLog.saveToFile(getCacheDir().getAbsolutePath() + "/logdata.ser");
		super.onDestroy();
	}

	private synchronized boolean doStopVPN() {
		if (mVPN != null) {
			mVPN.stopVPN();
			return true;
		}
		return false;
	}

	private void killVPNThread(boolean joinThread) {
		Thread vpnThread = mVPNThread;
		if (doStopVPN() && joinThread) {
			try {
				if (vpnThread != null) {
					vpnThread.join(1000);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Log.e(TAG, "OpenConnect thread did not exit");
			}
		}
	}

	private PendingIntent getMainActivityIntent() {
		// Touching "Configure" on the system VPN dialog will restore the app
		// (same as clicking the launcher icon)
		Intent intent = new Intent(getBaseContext(), MainActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);

		PendingIntent startLW = PendingIntent.getActivity(this, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		return startLW;
	}

	private void registerDeviceStateReceiver(OpenVPNManagement management) {
		// Registers BroadcastReceiver to track network connection changes.
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(DeviceStateReceiver.PREF_CHANGED);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		mDeviceStateReceiver = new DeviceStateReceiver(management, mPrefs);
		ContextCompat.registerReceiver(this, mDeviceStateReceiver, filter,
				ContextCompat.RECEIVER_NOT_EXPORTED);
	}

	private synchronized void registerKeepAlive() {
		String DNSServer = "8.8.8.8";
		try {
			String dns = ipInfo.DNS.get(0);
			if (InetAddress.getByName(dns) != null) {
				DNSServer = dns;
			}
		} catch (IndexOutOfBoundsException e) {
			/* empty DNS server list */
		} catch (Exception e) {
			Log.i(TAG, "server DNS IP is bogus, falling back to " + DNSServer + " for KeepAlive", e);
		}

		int idle = 1800;
		try {
			int val = Integer.parseInt(ipInfo.CSTPOptions.get("X-CSTP-Idle-Timeout"));
			if (val >= 60 && val <= 7200) {
				idle = val;
			}
		} catch (Exception e) {
		}

		// set to 40% of the idle timeout value, to buy a little margin in case
		// the first 1-2 attempts fail
		idle = idle * 4 / 10;
		Log.d(TAG, "calculated KeepAlive interval: " + idle + " seconds");

		IntentFilter filter = new IntentFilter(KeepAlive.ACTION_KEEPALIVE_ALARM);
		mKeepAlive = new KeepAlive(idle, DNSServer, mDeviceStateReceiver);
		ContextCompat.registerReceiver(this, mKeepAlive, filter,
				ContextCompat.RECEIVER_NOT_EXPORTED);
		mKeepAlive.start(this);
	}

	private void unregisterReceivers() {
		try {
			if (mDeviceStateReceiver != null) {
				unregisterReceiver(mDeviceStateReceiver);
			}
			mDeviceStateReceiver = null;
		} catch (IllegalArgumentException iae) {
			// catch "Receiver not registered" error
			Log.w(TAG, "can't unregister DeviceStateReceiver", iae);
		}

		try {
			if (mKeepAlive != null) {
				mKeepAlive.stop(this);
				unregisterReceiver(mKeepAlive);
			}
			mKeepAlive = null;
		} catch (IllegalArgumentException iae) {
			Log.w(TAG, "can't unregister KeepAlive", iae);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		if (intent == null) {
			Log.e(TAG, "OpenVpnService started with null intent");
			stopSelf();
			return START_NOT_STICKY;
		}

		String action = intent.getAction();
		if (START_SERVICE.equals(action)) {
			return START_NOT_STICKY;
		} else if (START_SERVICE_STICKY.equals(action)) {
			return START_REDELIVER_INTENT;
		}

		// Extract information from the intent.
		mUUID = intent.getStringExtra(EXTRA_UUID);
		if (mUUID == null) {
			stopSelf(startId);
			return START_NOT_STICKY;
		}
		mPrefs.edit().putString(ProfileManager.LAST_USED_PROFILE, mUUID).apply();

		profile = ProfileManager.get(mUUID);
		if (profile == null) {
			stopSelf(startId);
			return START_NOT_STICKY;
		}

		startForegroundNotification();
		killVPNThread(true);

		// stopSelfResult(most_recent_startId) will kill the service
		// stopSelfResult(previous_startId) will not
		mStartId = startId;

        mVPN = new OpenConnectManagementThread(getApplicationContext(), profile, this);
        mVPNThread = new Thread(mVPN, "OpenVPNManagementThread");
        mVPNThread.start();

		unregisterReceivers();
		registerDeviceStateReceiver(mVPN);

		ProfileManager.setConnectedVpnProfile(profile);

        return START_NOT_STICKY;
    }

	public Builder getVpnServiceBuilder() {
		VpnService.Builder b = new VpnService.Builder();
		b.setSession(profile.mName);
		b.setConfigureIntent(getMainActivityIntent());
		return b;
	}

	// From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
	public static String humanReadableByteCount(long bytes, boolean mbit) {
		if(mbit)
			bytes = bytes *8;
		int unit = mbit ? 1000 : 1024;
		if (bytes < unit)
			return bytes + (mbit ? " bit" : " B");

		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (mbit ? "" : "");
		if(mbit)
			return String.format(Locale.getDefault(),"%.1f %sbit", bytes / Math.pow(unit, exp), pre);
		else 
			return String.format(Locale.getDefault(),"%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	public static String formatElapsedTime(long startTime) {
		StringBuilder sb = new StringBuilder();
		startTime = (new Date().getTime() - startTime) / 1000;
		if (startTime >= 60 * 60 * 24) {
			// days
			sb.append(String.format("%1$d:", startTime / (60 * 60 * 24)));
		}
		if (startTime >= 60 * 60) {
			// hours
			startTime %= 60 * 60 * 24;
			sb.append(String.format("%1$02d:", startTime / (60 * 60)));
			startTime %= 60 * 60;
		}
		// minutes:seconds
		sb.append(String.format("%1$02d:%2$02d", startTime / 60, startTime % 60));
		return sb.toString();
	}

	/* called from the activity on broadcast receipt, or startup */
	public synchronized void startActiveDialog(Context context) {
		if (mDialog != null && mDialogContext == null) {
			mDialogContext = context;
			mDialog.onStart(context);
		}
	}

	/* called when the activity shuts down (mDialog will be re-rendered when the activity starts again) */
	public synchronized void stopActiveDialog(Context context) {
		if (mDialogContext != context) {
			return;
		}
		if (mDialog != null) {
			mDialog.onStop(mDialogContext);
		}
		mDialogContext = null;
	}

	private synchronized void setDialog(Context context, UserDialog dialog) {
		mDialogContext = context;
		mDialog = dialog;
	}

	@SuppressWarnings("deprecation")
        private Notification buildNotification() {
                boolean inputNeeded = mDialog != null && mActivityConnections == 0 &&
                                mPrefs.getBoolean(PREF_NOTIFY_USER_INPUT, true);
                String channelId = inputNeeded
                                ? NOTIFICATION_CHANNEL_INPUT : NOTIFICATION_CHANNEL_CONNECTION;
                Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? new Notification.Builder(this, channelId)
                                : new Notification.Builder(this);

                String target = "";
                if (serverName != null && !serverName.isEmpty()) {
                        target = serverName;
                } else if (profile != null && profile.mPrefs != null) {
                        target = profile.mPrefs.getString("server_address", "").trim();
                }
                if (target.isEmpty() && profile != null) {
                        target = profile.getName();
                }

                String title = getString(R.string.app);
                String text;
                if (inputNeeded) {
                        title = getString(R.string.notification_input_needed);
                        text = getString(R.string.notification_touch_here);
                } else if (mConnectionState == OpenConnectManagementThread.STATE_CONNECTED &&
                                profile != null) {
                        text = getString(R.string.notification_connected_to, target);
                } else if (profile != null) {
                        text = getString(R.string.notification_connecting_to, target);
                } else {
                        text = getString(R.string.notification_vpn_active);
                }

                return builder.setSmallIcon(R.drawable.ic_stat_vpn)
                                .setContentTitle(title)
                                .setContentText(text)
                                .setContentIntent(getMainActivityIntent())
                                .setCategory(Notification.CATEGORY_SERVICE)
                                .setOngoing(true)
                                .setOnlyAlertOnce(true)
                                .build();
        }

	private void startForegroundNotification() {
		int foregroundType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
				? ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED : 0;
		ServiceCompat.startForeground(
				this, NOTIFICATION_ID, buildNotification(), foregroundType);
		mForegroundActive = true;
	}

	private void stopForegroundNotification() {
		if (!mForegroundActive) {
			return;
		}
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
		mForegroundActive = false;
	}

	private void updateNotification() {
		if (!mForegroundActive) {
			return;
		}
		NotificationManager manager =
				(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.notify(NOTIFICATION_ID, buildNotification());
		}
	}

	private void wakeUpActivity() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				Intent vpnstatus = new Intent(ACTION_VPN_STATUS);
				vpnstatus.setPackage(getPackageName());
				vpnstatus.putExtra(EXTRA_CONNECTION_STATE, mConnectionState);
				vpnstatus.putExtra(EXTRA_UUID, mUUID);
				sendBroadcast(vpnstatus, permission.ACCESS_NETWORK_STATE);

				updateNotification();

				if (mConnectionState == OpenConnectManagementThread.STATE_CONNECTED &&
						mKeepAlive == null) {
					registerKeepAlive();
					FeedbackFragment.recordUse(getApplicationContext(), true);
				}
			}
		});
	}

	public void updateActivityRefcount(int num) {
		mActivityConnections += num;
		updateNotification();
	}

	/* called from the VPN thread; blocks until user responds */
	public Object promptUser(UserDialog dialog) {
		Object ret;

		ret = dialog.earlyReturn();
		if (ret != null) {
			return ret;
		}

		setDialog(null, dialog);
		wakeUpActivity();
		ret = mDialog.waitForResponse();

		setDialog(null, null);
		wakeUpActivity();
		return ret;
	}

	public synchronized void threadDone(OpenConnectManagementThread management) {
		if (mVPN != management) {
			Log.i(TAG, "ignoring termination from an obsolete VPN thread");
			return;
		}
		final int startId = mStartId;

		Log.i(TAG, "VPN thread has terminated");
		mVPN = null;
		mVPNThread = null;
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				if (stopSelfResult(startId) == false) {
					Log.w(TAG, "not stopping service due to startId mismatch");
				} else {
					stopForegroundNotification();
					unregisterReceivers();
				}
			}
		});
	}

	public synchronized void setConnectionState(OpenConnectManagementThread management, int state) {
		if (mVPN != management) {
			return;
		}
		if (state == OpenConnectManagementThread.STATE_CONNECTED &&
				mConnectionState != OpenConnectManagementThread.STATE_CONNECTED) {
			startTime = new Date();
                	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                	        setUnderlyingNetworks(null);
                	}
		}
		mConnectionState = state;
		wakeUpActivity();
	}

	public synchronized int getConnectionState() {
		return mConnectionState;
	}

	public String getConnectionStateName() {
		return mConnectionStateNames[getConnectionState()];
	}

	public void requestStats() {
		if (mVPN != null) {
			mVPN.requestStats();
		}
	}

	public synchronized void setStats(OpenConnectManagementThread management, VPNStats stats) {
		if (mVPN != management) {
			return;
		}
		if (stats != null) {
			mStats = stats;
		}
		wakeUpActivity();
	}

	public synchronized VPNStats getStats() {
		return mStats;
	}

	public synchronized void setIPInfo(OpenConnectManagementThread management,
			LibOpenConnect.IPInfo ipInfo, String serverName) {
		if (mVPN != management) {
			return;
		}
		this.ipInfo = ipInfo;
		this.serverName = serverName;
	}

	public LogArrayAdapter getArrayAdapter(Context context) {
		return mVPNLog.getArrayAdapter(context);
	}

	public void putArrayAdapter(LogArrayAdapter adapter) {
		if (adapter != null) {
			mVPNLog.putArrayAdapter(adapter);
		}
	}

	public void log(final int level, final String msg) {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				mVPNLog.add(level, msg);
			}
		});
	}

	public void clearLog() {
		mVPNLog.clear();
	}

	public String dumpLog() {
		return mVPNLog.dump();
	}

        public String getReconnectName() {
                VpnProfile p = ProfileManager.get(mUUID);
                if (p == null) return null;
                String server = p.mPrefs != null ? p.mPrefs.getString("server_address", "").trim() : "";
                return !server.isEmpty() ? server : p.getName();
        }

	public String getReconnectUUID() {
		return getReconnectName() == null ? null : mUUID;
	}

	public void startReconnectActivity(Context context) {
		Intent intent = new Intent(context, GrantPermissionsActivity.class);
		intent.putExtra(getPackageName() + GrantPermissionsActivity.EXTRA_UUID, mUUID);
		context.startActivity(intent);
	}

	public void stopVPN() {
		killVPNThread(false);
		ProfileManager.setConnectedVpnProfileDisconnected();
	}
}
