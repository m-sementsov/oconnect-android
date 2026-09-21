/*
 * Copyright (c) 2013, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 */

package app.openconnect.fragments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import app.openconnect.ConnectionEditorActivity;
import app.openconnect.FragActivity;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.EditText;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.materialswitch.MaterialSwitch;
import app.openconnect.AppSelectActivity;
import app.openconnect.core.ProfileManager;

import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.ProfileManager;
import app.openconnect.core.VPNConnector;
import app.openconnect.update.GitHubUpdateChecker;

public class StatusFragment extends Fragment {
    private static final int REQ_PICK_P12 = 7001;
    private static final int REQ_SELECT_APPS = 7002;


	private static final int MENU_CHECK_UPDATES = 1;
	private static final int MENU_SECURID = 2;

	private View mView;
	private VPNConnector mConn;
	private MaterialButton mDisconnectButton;
	private VpnProfile mSelectedProfile;
	private int mConnectionState = OpenConnectManagementThread.STATE_DISCONNECTED;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mView = inflater.inflate(R.layout.status, container, false);
		mDisconnectButton = (MaterialButton)mView.findViewById(R.id.disconnect_button);

		styleWordmark();
		mDisconnectButton.setOnClickListener(view -> handlePrimaryAction());

		mView.findViewById(R.id.dashboard_overflow).setVisibility(View.GONE);
		initDashboardControls();

		mConn = new VPNConnector(getActivity(), false) {
			@Override
			public void onUpdate(OpenVpnService service) {
				updateUI(service);
			}
		};

		return mView;
	}

	@Override
	public void onDestroyView() {
		if (mConn != null) {
			mConn.unbind();
		}
		super.onDestroyView();
	}

	private void styleWordmark() {
		TextView wordmarkView = (TextView)mView.findViewById(R.id.dashboard_wordmark);
		String wordmarkText = getString(R.string.app);
		SpannableString wordmark = new SpannableString(wordmarkText);
		int accentStart = wordmarkText.lastIndexOf("Next");
		if (accentStart >= 0) {
			wordmark.setSpan(
					new ForegroundColorSpan(ContextCompat.getColor(
							getActivity(), R.color.dashboard_accent)),
					accentStart,
					wordmarkText.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		wordmarkView.setText(wordmark);
	}

	private void updateUI(OpenVpnService service) {
		if (service == null || mView == null) {
			return;
		}

		int state = service.getConnectionState();
		mConnectionState = state;
		mSelectedProfile = resolveProfile(service);

		if (state == OpenConnectManagementThread.STATE_CONNECTED) {
			showConnectedState(service);
		} else if (state == OpenConnectManagementThread.STATE_DISCONNECTED) {
			showDisconnectedState(service);
		} else {
			showProgressState(service);
		}
	}

	private void showConnectedState(OpenVpnService service) {
		setStatusBadgeVisible(true);
		setTrafficVisible(true);
		writeProfile(service);
		writeText(R.id.connection_state, getString(R.string.dashboard_connected));
		writeText(R.id.connection_time, service.startTime == null
				? getString(R.string.connection_status_progress_message)
				: OpenVpnService.formatElapsedTime(service.startTime.getTime()));
		writeText(R.id.connection_traffic, mConn.statsValid
				? mConn.getByteCountSummary()
				: getString(R.string.dashboard_traffic_loading));
		mDisconnectButton.setText(R.string.disconnect);
		mDisconnectButton.setIconResource(R.drawable.ic_power_settings_new_24);
		updateCardUI();
	}

	private void showProgressState(OpenVpnService service) {
		setStatusBadgeVisible(false);
		setTrafficVisible(true);
		writeProfile(service);
		writeText(R.id.connection_state, service.getConnectionStateName());
		writeText(R.id.connection_time,
				getString(R.string.connection_status_progress_message));
		writeText(R.id.connection_traffic, getServerName(service, mSelectedProfile));
		mDisconnectButton.setText(R.string.disconnect);
		mDisconnectButton.setIconResource(R.drawable.ic_close_24);
		updateCardUI();
	}

	private void showDisconnectedState(OpenVpnService service) {
		setStatusBadgeVisible(false);
		setTrafficVisible(false);
		writeText(R.id.connection_state, getString(R.string.dashboard_disconnected));

		if (mSelectedProfile != null) {
			writeText(R.id.connection_profile, mSelectedProfile.getName());
			writeText(R.id.connection_time, getString(R.string.dashboard_ready_to_connect));
			mDisconnectButton.setText(R.string.dashboard_connect);
			mDisconnectButton.setIconResource(R.drawable.ic_power_settings_new_24);
		} else {
			writeText(R.id.connection_profile, "VPN");
			writeText(R.id.connection_time, getString(R.string.dashboard_ready_to_connect));
			mDisconnectButton.setText(R.string.dashboard_connect);
			mDisconnectButton.setIconResource(R.drawable.ic_power_settings_new_24);
		}
		updateCardUI();
	}

	private void writeProfile(OpenVpnService service) {
		String profileName = mSelectedProfile == null
				? "VPN"
				: mSelectedProfile.getName();
		writeText(R.id.connection_profile, profileName);
	}

	private VpnProfile resolveProfile(OpenVpnService service) {
		if (service != null && service.profile != null) {
			return service.profile;
		}
		List<VpnProfile> profiles = new ArrayList<VpnProfile>(ProfileManager.getProfiles());
		if (!profiles.isEmpty()) {
			Collections.sort(profiles);
			return profiles.get(0);
		}
		return ProfileManager.create("My VPN");
	}

	private void setStatusBadgeVisible(boolean visible) {
		mView.findViewById(R.id.connection_status_badge).setVisibility(
				visible ? View.VISIBLE : View.GONE);
	}

	private void setConnectionDetailsVisible(boolean visible) {
	}

	private void setTrafficVisible(boolean visible) {
		mView.findViewById(R.id.connection_traffic).setVisibility(
				visible ? View.VISIBLE : View.GONE);
	}

	private String getServerName(OpenVpnService service, VpnProfile profile) {
		String server = service.serverName;
		if (TextUtils.isEmpty(server) && profile != null) {
			server = profile.mPrefs.getString("server_address", "");
		}
		if (TextUtils.isEmpty(server)) {
			return getString(R.string.profile_server_not_set);
		}
		return server;
	}

	private void writeText(int id, CharSequence value) {
		TextView textView = (TextView)mView.findViewById(id);
		textView.setText(value);
	}

	private void handlePrimaryAction() {
		if (mConn == null || mConn.service == null) {
			return;
		}
		if (mConnectionState != OpenConnectManagementThread.STATE_DISCONNECTED) {
			mConn.service.stopVPN();
			return;
		}
		if (mSelectedProfile == null) {
			openProfiles(true);
			return;
		}
		Intent intent = new Intent(getActivity(), GrantPermissionsActivity.class);
		String pkg = getActivity().getPackageName();
		intent.putExtra(pkg + GrantPermissionsActivity.EXTRA_UUID,
				mSelectedProfile.getUUID().toString());
		intent.setAction(Intent.ACTION_MAIN);
		startActivity(intent);
	}

	private void handleProfileRow() {
		if (mConnectionState == OpenConnectManagementThread.STATE_DISCONNECTED) {
			openProfiles(false);
		} else {
			editCurrentProfile();
		}
	}

	private void editCurrentProfile() {
		if (mSelectedProfile == null) {
			return;
		}
		String prefix = getActivity().getPackageName();
		Intent intent = new Intent(getActivity(), ConnectionEditorActivity.class)
				.putExtra(prefix + ".profileUUID", mSelectedProfile.getUUID().toString())
				.putExtra(prefix + ".profileName", mSelectedProfile.getName());
		startActivity(intent);
	}

	private void openProfiles(boolean openAddProfile) {
		Intent intent = new Intent(getActivity(), FragActivity.class);
		intent.putExtra(FragActivity.EXTRA_FRAGMENT_NAME, "VPNProfileList");
		intent.putExtra(FragActivity.EXTRA_OPEN_ADD_PROFILE, openAddProfile);
		startActivity(intent);
	}

	private void startFragment(String fragmentName) {
		Intent intent = new Intent(getActivity(), FragActivity.class);
		intent.putExtra(FragActivity.EXTRA_FRAGMENT_NAME, fragmentName);
		startActivity(intent);
	}

	private void showHelpAndAbout() {
		CharSequence[] items = {
				getString(R.string.faq),
				getString(R.string.about_openconnect)
		};
		new AlertDialog.Builder(getActivity())
				.setTitle(R.string.dashboard_help_about)
				.setItems(items, (dialog, which) -> startFragment(
						which == 0 ? "FaqFragment" : "AboutFragment"))
				.show();
	}

	private void showOverflowMenu(View anchor) {
		PopupMenu popup = new PopupMenu(getActivity(), anchor);
		popup.getMenu().add(0, MENU_CHECK_UPDATES, 0, R.string.check_for_updates);
		popup.getMenu().add(0, MENU_SECURID, 1, R.string.securid_info);
		popup.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == MENU_CHECK_UPDATES) {
				GitHubUpdateChecker.checkManually(getActivity());
				return true;
			}
			if (item.getItemId() == MENU_SECURID) {
				startFragment("TokenParentFragment");
				return true;
			}
			return false;
		});
		popup.show();
	}

    private void initDashboardControls() {
        mSelectedProfile = resolveProfile(mConn != null ? mConn.service : null);
        if (mSelectedProfile == null) return;
        SharedPreferences sp = mSelectedProfile.mPrefs;

        // 1. Сервер
        View rowServer = mView.findViewById(R.id.row_server);
        if (rowServer != null) {
            rowServer.setOnClickListener(v -> showEditServerDialog());
        }

        // 2. P12 Key
        View rowP12 = mView.findViewById(R.id.row_p12_key);
        if (rowP12 != null) {
            rowP12.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQ_PICK_P12);
            });
        }

        // 3. Тумблер Per-app VPN
        MaterialSwitch perAppSwitch = mView.findViewById(R.id.switch_per_app);
        if (perAppSwitch != null) {
            perAppSwitch.setChecked(sp.getBoolean("split_tunnel_apps_enabled", false));
            perAppSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                sp.edit().putBoolean("split_tunnel_apps_enabled", isChecked).commit();
                updateCardUI();
            });
        }

        // 4. Выбор приложений
        View rowApps = mView.findViewById(R.id.row_select_apps);
        if (rowApps != null) {
            rowApps.setOnClickListener(v -> {
                String current = sp.getString("split_tunnel_apps_list", "");
                Intent intent = new Intent(getActivity(), AppSelectActivity.class);
                intent.putExtra(AppSelectActivity.EXTRA_SELECTED, current);
                startActivityForResult(intent, REQ_SELECT_APPS);
            });
        }

        updateCardUI();
    }

    private void updateCardUI() {
        if (mView == null) return;
        if (mSelectedProfile == null) {
            mSelectedProfile = resolveProfile(mConn != null ? mConn.service : null);
        }
        if (mSelectedProfile == null) return;
        SharedPreferences sp = mSelectedProfile.mPrefs;

        // Сервер
        TextView textServer = mView.findViewById(R.id.text_server_address);
        if (textServer != null) {
            String s = sp.getString("server_address", "");
            textServer.setText(s.isEmpty() ? getString(R.string.add_profile_hostname_prompt) : s);
        }

        // P12 Key статус
        TextView textP12 = mView.findViewById(R.id.text_p12_status);
        if (textP12 != null) {
            String cert = sp.getString("user_certificate", "");
            String customName = sp.getString("user_certificate_display_name", "");
            if (cert.isEmpty()) {
                textP12.setText(R.string.unknown);
            } else if (!customName.isEmpty()) {
                textP12.setText(customName);
            } else {
                textP12.setText("user.p12");
            }
        }

        // Количество выбранных приложений
        boolean perAppOn = sp.getBoolean("split_tunnel_apps_enabled", false);
        View dividerApps = mView.findViewById(R.id.divider_select_apps);
        View rowApps = mView.findViewById(R.id.row_select_apps);
        if (dividerApps != null) dividerApps.setVisibility(perAppOn ? View.VISIBLE : View.GONE);
        if (rowApps != null) rowApps.setVisibility(perAppOn ? View.VISIBLE : View.GONE);

        TextView textApps = mView.findViewById(R.id.text_apps_count);
        if (textApps != null) {
            String list = sp.getString("split_tunnel_apps_list", "");
            int count = (list == null || list.trim().isEmpty()) ? 0 : list.split(",").length;
            textApps.setText(count == 0 ? getString(R.string.split_tunnel_apps_none_selected)
                    : getString(R.string.split_tunnel_apps_selected_count, count));
        }
    }

    private void showEditServerDialog() {
        if (mSelectedProfile == null) return;
        EditText input = new EditText(getActivity());
        input.setSingleLine(true);
        input.setHint(R.string.add_profile_hostname_prompt);
        String currentServer = mSelectedProfile.mPrefs.getString("server_address", "");
        input.setText(currentServer);
        input.setSelection(currentServer.length());

        // Добавляем правильные отступы Material Design
        FrameLayout container = new FrameLayout(getActivity());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int marginH = (int) (24 * getResources().getDisplayMetrics().density);
        int marginV = (int) (8 * getResources().getDisplayMetrics().density);
        params.leftMargin = marginH;
        params.rightMargin = marginH;
        params.topMargin = marginV;
        params.bottomMargin = marginV;
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.server_address)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newServer = input.getText().toString().trim();
                    mSelectedProfile.mPrefs.edit().putString("server_address", newServer).commit();
                    updateCardUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (mSelectedProfile == null) {
            mSelectedProfile = resolveProfile(mConn != null ? mConn.service : null);
        }
        if (mSelectedProfile == null) return;
        SharedPreferences sp = mSelectedProfile.mPrefs;

        if (requestCode == REQ_PICK_P12) {
            Uri uri = data.getData();
            if (uri != null) {
                // Извлекаем настоящее имя файла (например, user.p12)
                String displayName = null;
                Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1) {
                                displayName = cursor.getString(nameIndex);
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
                if (displayName == null) {
                    displayName = uri.getLastPathSegment();
                }

                String storedPath = ProfileManager.storeFilePref(
                        mSelectedProfile, "user_certificate", getActivity().getContentResolver(), uri);
                if (storedPath != null) {
                    SharedPreferences.Editor ed = sp.edit().putString("user_certificate", storedPath);
                    if (displayName != null) {
                        ed.putString("user_certificate_display_name", displayName);
                    }
                    ed.commit();
                    updateCardUI();
                    Toast.makeText(getActivity(), "P12 key imported!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), R.string.import_error_message, Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQ_SELECT_APPS) {
            String result = data.getStringExtra(AppSelectActivity.EXTRA_RESULT);
            sp.edit().putString("split_tunnel_apps_list", result == null ? "" : result).commit();
            updateCardUI();
        }
    }

}