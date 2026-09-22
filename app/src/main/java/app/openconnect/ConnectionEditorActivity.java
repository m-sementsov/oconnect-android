/*
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

package app.openconnect;

import app.openconnect.core.ProfileManager;
import app.openconnect.fragments.ConnectionEditorFragment;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class ConnectionEditorActivity extends ToolbarActivity {

    public static final String EXTRA_PROFILE_UUID = "io.msem.oconnect.profile_uuid";
    public static final String EXTRA_SCREEN = "io.msem.oconnect.profile_settings_screen";
    public static final String SCREEN_MAIN = "main";
    public static final String SCREEN_AUTHENTICATION = "authentication";
    public static final String SCREEN_ADVANCED = "advanced";

    private String mName = "";
    private String mUUID;
    private String mScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_profile_settings);

        ConnectionEditorFragment frag = new ConnectionEditorFragment();
        mUUID = getIntent().getStringExtra(EXTRA_PROFILE_UUID);
        if (mUUID == null) {
            mUUID = getIntent().getStringExtra(getPackageName() + ".profileUUID");
        }
        mScreen = getIntent().getStringExtra(EXTRA_SCREEN);
        if (mScreen == null) {
            mScreen = SCREEN_MAIN;
        }

        VpnProfile profile = ProfileManager.get(mUUID);
        mName = profile == null ? "" : profile.getName();
        setupToolbar(R.id.toolbar, getScreenTitle(), true);

        Bundle args = new Bundle();
        args.putString("profileUUID", mUUID);
        args.putString(EXTRA_SCREEN, mScreen);
        frag.setArguments(args);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.content_frame, frag)
                .commit();

        View saveButton = findViewById(R.id.save_button);
        if (SCREEN_MAIN.equals(mScreen)) {
            saveButton.setOnClickListener(v -> finish());
        } else {
            saveButton.setVisibility(View.GONE);
        }
		invalidateToolbarMenu();
    }

	@Override
	protected void onCreateToolbarMenu(Menu menu) {
        if (SCREEN_MAIN.equals(mScreen)) {
		    getMenuInflater().inflate(R.menu.vpnpreferences_menu, menu);
        }
	}

	@Override
	protected boolean onToolbarMenuItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.remove_vpn) {
			askProfileRemoval();
			return true;
		}
		return super.onToolbarMenuItemSelected(item);
	}

	public void setProfileName(String name) {
        mName = name;
        if (SCREEN_MAIN.equals(mScreen)) {
            setTitle(getString(R.string.edit_profile_title, mName));
        }
	}

    private CharSequence getScreenTitle() {
        if (SCREEN_AUTHENTICATION.equals(mScreen)) {
            return getString(R.string.authentication);
        }
        if (SCREEN_ADVANCED.equals(mScreen)) {
            return getString(R.string.advanced);
        }
        return getString(R.string.edit_profile_title, mName);
    }

	private void askProfileRemoval() {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle(R.string.confirm_deletion);
		dialog.setMessage(getString(R.string.remove_vpn_query, mName));

		dialog.setPositiveButton(android.R.string.yes,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ProfileManager.delete(mUUID);
				finish();
			}
		});
		dialog.setNegativeButton(android.R.string.no,null);
		dialog.create().show();
	}
}
