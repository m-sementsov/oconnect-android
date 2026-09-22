/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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


import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.widget.Toast;
import app.openconnect.fragments.FileSelectionFragment;
import app.openconnect.fragments.InlineFileTab;
import app.openconnect.core.StreamUtils;

import com.google.android.material.tabs.TabLayout;

public class FileSelect extends ToolbarActivity {
	public static final String RESULT_DATA = "RESULT_PATH";
	public static final String START_DATA = "START_DATA";
	public static final String WINDOW_TITLE = "WINDOW_TILE";
	public static final String NO_INLINE_SELECTION = "io.msem.oconnect.NO_INLINE_SELECTION";
	public static final String FORCE_INLINE_SELECTION = "io.msem.oconnect.FORCE_INLINE_SELECTION";
	public static final String SHOW_CLEAR_BUTTON = "io.msem.oconnect.SHOW_CLEAR_BUTTON";
	public static final String DO_BASE64_ENCODE = "io.msem.oconnect.BASE64ENCODE";

	private static final int MAX_FILE_LEN = 32768;

	private FileSelectionFragment mFSFragment;
	private InlineFileTab mInlineFragment;
	private String mData;
	private TabLayout mTabs;
	private TabLayout.Tab inlineFileTab;
	private TabLayout.Tab fileExplorerTab;
	private Fragment mActiveFragment;
	private boolean mNoInline;
	private boolean mForceInline;
	private boolean mShowClear;
	private boolean mBase64Encode;
	private boolean mTabsConfigured;

	private static final int REQUEST_READ_STORAGE = 1;
	
		
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState); 
		setContentView(R.layout.activity_tabbed);
		mTabs = (TabLayout)findViewById(R.id.tabs);

		mData = getIntent().getStringExtra(START_DATA);
		if(mData==null)
			mData=Environment.getExternalStorageDirectory().getPath();
		
		String title = getIntent().getStringExtra(WINDOW_TITLE);
		int titleId = getIntent().getIntExtra(WINDOW_TITLE, 0);
		if(titleId!=0) 
			title =getString(titleId);
		if(title!=null) {
			setTitle(title);
		} else {
			title = getString(R.string.file_select);
		}
		setupToolbar(R.id.toolbar, title, true);
		
		mNoInline = getIntent().getBooleanExtra(NO_INLINE_SELECTION, false);
		mForceInline = getIntent().getBooleanExtra(FORCE_INLINE_SELECTION, false);
		mShowClear = getIntent().getBooleanExtra(SHOW_CLEAR_BUTTON, false);
		mBase64Encode = getIntent().getBooleanExtra(DO_BASE64_ENCODE, false);

		if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2 &&
				checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
				PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
					REQUEST_READ_STORAGE);
		} else {
			setupTabs();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQUEST_READ_STORAGE) {
			if (grantResults.length == 0 ||
					grantResults[0] != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(this, R.string.file_access_unavailable,
						Toast.LENGTH_LONG).show();
			}
			setupTabs();
		}
	}

	private void setupTabs() {
		if (mTabsConfigured) {
			return;
		}
		mTabsConfigured = true;

		fileExplorerTab = mTabs.newTab().setText(R.string.file_explorer_tab);
		inlineFileTab = mTabs.newTab().setText(R.string.inline_file_tab);

		mTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				Fragment fragment = (Fragment)tab.getTag();
				if (fragment != null) {
					showFragment(fragment);
				}
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab) {
			}

			@Override
			public void onTabReselected(TabLayout.Tab tab) {
			}
		});

		mFSFragment = new FileSelectionFragment();
		if (mNoInline) {
			mFSFragment.setNoInLine();
		} else if (mForceInline) {
			mFSFragment.setForceInLine();
		}
		fileExplorerTab.setTag(mFSFragment);
		mTabs.addTab(fileExplorerTab);
		
		if(!mNoInline) {
			mInlineFragment = new InlineFileTab();
			inlineFileTab.setTag(mInlineFragment);
			mTabs.addTab(inlineFileTab, false);
		}
	}
	
	public boolean showClear() {
		if(mData == null || mData.equals(""))
			return false;
		else
			return mShowClear;
	}

	private void showFragment(Fragment fragment) {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		if (mActiveFragment != null && mActiveFragment.isAdded()) {
			ft.detach(mActiveFragment);
		}
		if (fragment.isAdded()) {
			ft.attach(fragment);
		} else {
			ft.add(R.id.content_frame, fragment);
			}
			mActiveFragment = fragment;
			ft.commit();
			getFragmentManager().executePendingTransactions();
			setToolbarMenuFragment(fragment);
		}
	
	public void importFile(String path) {
		File ifile = new File(path);
		String error = null;

		try {

			String data = "";
			
			if (ifile.length() > MAX_FILE_LEN) {
				error = getString(R.string.file_too_large);
			} else {
				byte[] filedata = readBytesFromFile(ifile) ;
				if(mBase64Encode)
					data += Base64.encodeToString(filedata, Base64.DEFAULT);
				else
					data += new String(filedata);
				mData = data;

				saveInlineData(data);
			}
		} catch (FileNotFoundException e) {
			error = e.getLocalizedMessage();
		} catch (IOException e) {
			error = e.getLocalizedMessage();
		}

		if (error != null) {
			Builder ab = new AlertDialog.Builder(this);
			ab.setTitle(R.string.error_importing_file);
			ab.setMessage(getString(R.string.import_error_message) + ": " + error);
			ab.setPositiveButton(android.R.string.ok, null);
			ab.show();
		}
	}

	private byte[] readBytesFromFile(File file) throws IOException {
		try (InputStream input = new FileInputStream(file);
				ByteArrayOutputStream output = new ByteArrayOutputStream((int)file.length())) {
			StreamUtils.copy(input, output);
			return output.toByteArray();
		}
	}
	
	
	public void setFile(String path) {
		Intent intent = new Intent();
		intent.putExtra(RESULT_DATA, path);
		setResult(RESULT_OK,intent);
		finish();		
	}

	public String getSelectPath() {
		if(!mData.startsWith(VpnProfile.INLINE_TAG))
			return mData;
		else
			return Environment.getExternalStorageDirectory().getPath();
	}

	public CharSequence getInlineData() {
		if(mData.startsWith(VpnProfile.INLINE_TAG))
			return mData.substring(VpnProfile.INLINE_TAG.length());
		else
			return "";
	}
	
	public void clearData() {
		Intent intent = new Intent();
		intent.putExtra(RESULT_DATA, (String)null);
		setResult(RESULT_OK,intent);
		finish();
		
	}

	public void saveInlineData(String string) {
		Intent intent = new Intent();
		
		intent.putExtra(RESULT_DATA,VpnProfile.INLINE_TAG + string);
		setResult(RESULT_OK,intent);
		finish();
		
	}
}
