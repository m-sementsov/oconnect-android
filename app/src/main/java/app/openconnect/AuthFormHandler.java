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

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.infradead.libopenconnect.LibOpenConnect;

import app.openconnect.core.UserDialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class AuthFormHandler extends UserDialog
		implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

	public static final String TAG = "OConnect";

	private LibOpenConnect.AuthForm mForm;
	private Context mContext;
	private boolean isOK;
	private AlertDialog mAlert;

	private CheckBox savePassword = null;
	private CheckBox automaticLogin = null;
	private boolean noSave = false;
	private String formPfx;

	private int batchMode = BATCH_MODE_DISABLED;
	private boolean mAuthgroupSet;
	private boolean mAllFilled = true;

	private TextView mFirstEmptyText;
	private TextView mFirstText;

	private static final int BATCH_MODE_DISABLED = 0;
	private static final int BATCH_MODE_EMPTY_ONLY = 1;
	private static final int BATCH_MODE_ENABLED = 2;
	private static final int BATCH_MODE_ABORTED = 3;

	public AuthFormHandler(SharedPreferences prefs, LibOpenConnect.AuthForm form, boolean authgroupSet,
			String lastFormDigest) {
		super(prefs);

		mForm = form;
		mAuthgroupSet = authgroupSet;
		formPfx = getFormPrefix(mForm);
		noSave = getBooleanPref("disable_username_caching");

		String s = getStringPref("batch_mode");
		if (s.equals("empty_only")) {
			batchMode = BATCH_MODE_EMPTY_ONLY;
		} else if (s.equals("enabled")) {
			batchMode = BATCH_MODE_ENABLED;
		}

		// If the server is sending us the same form twice in a row, that probably
		// means there's a problem with the data we are sending back.  Either prompt
		// the user, or abort.
		if (formPfx.equals(lastFormDigest)) {
			if (batchMode == BATCH_MODE_EMPTY_ONLY) {
				batchMode = BATCH_MODE_DISABLED;
			} else if (batchMode == BATCH_MODE_ENABLED) {
				batchMode = BATCH_MODE_ABORTED;
			}
		}
	}

	public String getFormDigest() {
		return formPfx;
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		// catches OK, Cancel, and Back button presses
		if (isOK) {
			saveAndStore();
		}
		finish(isOK ? LibOpenConnect.OC_FORM_RESULT_OK : LibOpenConnect.OC_FORM_RESULT_CANCELLED);
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			isOK = true;
		}
	}

	private String digest(String s) {
		String out = "";
		if (s == null) {
			s = "";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			StringBuilder sb = new StringBuilder();
			byte d[] = digest.digest(s.getBytes("UTF-8"));
			for (byte dd : d) {
				sb.append(String.format("%02x", dd));
			}
			out = sb.toString();
		} catch (Exception e) {
			Log.e(TAG, "MessageDigest failed", e);
		}
		return out;
	}

	private String getOptDigest(LibOpenConnect.FormOpt opt) {
		StringBuilder in = new StringBuilder();

		switch (opt.type) {
		case LibOpenConnect.OC_FORM_OPT_SELECT:
			for (LibOpenConnect.FormChoice ch : opt.choices) {
				in.append(digest(ch.name));
				in.append(digest(ch.label));
			}
			/* falls through */
		case LibOpenConnect.OC_FORM_OPT_TEXT:
		case LibOpenConnect.OC_FORM_OPT_PASSWORD:
			in.append(":" + Integer.toString(opt.type) + ":");
			in.append(digest(opt.name));
			in.append(digest(opt.label));
		}
		return digest(in.toString());
	}

	private String getFormPrefix(LibOpenConnect.AuthForm form) {
		StringBuilder in = new StringBuilder();

		for (LibOpenConnect.FormOpt opt : form.opts) {
			in.append(getOptDigest(opt));
		}
		return "FORMDATA-" + digest(in.toString()) + "-";
	}

	private void fixPadding(View v) {
	}

	private LinearLayout.LayoutParams fillWidth =
			new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

	private int dp(float value) {
		float scale = mContext.getResources().getDisplayMetrics().density;
		return (int)(value * scale + 0.5f);
	}

	private LinearLayout.LayoutParams blockParams() {
		LinearLayout.LayoutParams lp =
				new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		lp.setMargins(0, 0, 0, dp(12));
		return lp;
	}

	private String cleanLabel(String label) {
		if (label == null) {
			return "";
		}
		return label.replaceFirst("\\s*[:：]\\s*$", "");
	}

	private LinearLayout newLabeledBlock(String label) {
		LinearLayout ll = new LinearLayout(mContext);
		ll.setOrientation(LinearLayout.VERTICAL);
		ll.setLayoutParams(blockParams());
		fixPadding(ll);

		TextView tv = new TextView(mContext);
		tv.setText(cleanLabel(label));
		tv.setTextSize(13);
		tv.setAlpha(0.72f);
		tv.setPadding(0, 0, 0, dp(6));
		ll.addView(tv);

		return ll;
	}

	private LinearLayout newTextBlank(
			LibOpenConnect.FormOpt opt, String defval, String displayLabel) {
		TextInputLayout ll = new TextInputLayout(mContext);
		ll.setLayoutParams(blockParams());
		ll.setHint(displayLabel);
		ll.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

		TextInputEditText tv = new TextInputEditText(mContext);
		tv.setLayoutParams(fillWidth);
		tv.setSingleLine(true);
		tv.setMinHeight(dp(56));
		if (defval == null) {
			defval = opt.value != null ? opt.value : "";
		}
		tv.setText(defval);

		if (mFirstEmptyText == null && defval.equals("")) {
			mFirstEmptyText = tv;
		}
		if (mFirstText == null) {
			mFirstText = tv;
		}

		int baseType = (opt.flags & LibOpenConnect.OC_FORM_OPT_NUMERIC) != 0 ?
				InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT;
		if (opt.type == LibOpenConnect.OC_FORM_OPT_PASSWORD) {
			int variation = baseType == InputType.TYPE_CLASS_NUMBER ?
					InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD;
			tv.setInputType(baseType | variation);
			ll.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
			ll.setEndIconContentDescription(R.string.show_password);
		} else {
			tv.setInputType(baseType | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
		}
		tv.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
				if (actionId == EditorInfo.IME_ACTION_DONE ||
						(keyEvent != null &&
								keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
								keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
					isOK = true;
					mAlert.dismiss();
					return true;
				} else {
					return false;
				}
			}
		});

		opt.userData = tv;
		ll.addView(tv);
		return ll;
	}

	private String labelKey(String label) {
		return cleanLabel(label).trim().toLowerCase(Locale.ROOT);
	}

	private String localizedFieldLabel(String label) {
		String cleaned = cleanLabel(label);
		String normalized = cleaned.trim().toLowerCase(Locale.ROOT);
		if ("username".equals(normalized) || "user name".equals(normalized) ||
				"user".equals(normalized)) {
			return mContext.getString(R.string.login_username);
		}
		if ("password".equals(normalized) || "passcode".equals(normalized) ||
				"passwd".equals(normalized)) {
			return mContext.getString(R.string.login_password);
		}
		if (normalized.contains("pkcs#12") || normalized.contains("pass phrase") ||
				normalized.contains("passphrase") || normalized.contains("private key")) {
			return mContext.getString(R.string.p12_password_prompt);
		}
		return cleaned;
	}

	private String getFieldLabel(
			LibOpenConnect.FormOpt opt,
			Map<String, Integer> labelCounts,
			Map<String, Integer> labelIndexes) {
		String key = labelKey(opt.label);
		int index = labelIndexes.containsKey(key) ? labelIndexes.get(key) + 1 : 1;
		labelIndexes.put(key, index);
		String label = localizedFieldLabel(opt.label);
		if (labelCounts.get(key) != null && labelCounts.get(key) > 1) {
			return mContext.getString(R.string.login_field_numbered, label, index);
		}
		return label;
	}

	private void spinnerSelect(LibOpenConnect.FormOpt opt, int index) {
		LibOpenConnect.FormChoice fc = opt.choices.get((int)index);
		String s = fc.name != null ? fc.name : "";

		if (opt.userData == null) {
			// first run
			opt.userData = s;
		} else if (!s.equals(opt.userData)) {
			opt.value = s;
			mAlert.dismiss();
			finish(LibOpenConnect.OC_FORM_RESULT_NEWGROUP);
		}
	}

	private LinearLayout newDropdown(final LibOpenConnect.FormOpt opt, int selection) {
		List<String> choiceList = new ArrayList<String>();

	    ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
	    		android.R.layout.simple_spinner_item, choiceList);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

	    for (LibOpenConnect.FormChoice fc : opt.choices) {
	    	choiceList.add(fc.label);
	    };

		Spinner sp = new Spinner(mContext);
		sp.setAdapter(adapter);
		sp.setLayoutParams(fillWidth);

		sp.setSelection(selection);
		spinnerSelect(opt, selection);

		sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				spinnerSelect(opt, (int)id);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		LinearLayout ll = newLabeledBlock(opt.label);
		ll.addView(sp);

		return ll;
	}

	private CheckBox newSavePasswordView(boolean isChecked) {
		CheckBox cb = new CheckBox(mContext);
		cb.setText(R.string.save_password);
		cb.setChecked(isChecked);
		fixPadding(cb);
		return cb;
	}

	private CheckBox newAutomaticLoginView(boolean isChecked) {
		CheckBox cb = new CheckBox(mContext);
		cb.setText(R.string.automatic_login_after_save);
		cb.setChecked(isChecked);
		fixPadding(cb);
		return cb;
	}

	private View newAutomaticLoginHintView() {
		View v = new View(mContext);
		v.setVisibility(View.GONE);
		return v;
	}

	private String getDisplayMessage() {
		if (mForm.message == null) {
			return "";
		}
		String message = mForm.message.trim();
		if ("please enter your username and password.".equalsIgnoreCase(message) ||
				"please enter your username and password".equalsIgnoreCase(message)) {
			return "";
		}
		if (message.length() > 128) {
			message = message.substring(0, 128);
		}
		return message;
	}

	private void saveAndStore() {
		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			if ((opt.flags & LibOpenConnect.OC_FORM_OPT_IGNORE) != 0) {
				continue;
			}
			switch (opt.type) {
			case LibOpenConnect.OC_FORM_OPT_TEXT: {
				TextView tv = (TextView)opt.userData;
				String s = tv.getText().toString();
				if (!noSave) {
					setStringPref(formPfx + getOptDigest(opt), s);
				}
				opt.value = s;
				break;
			}
			case LibOpenConnect.OC_FORM_OPT_PASSWORD: {
				TextView tv = (TextView)opt.userData;
				String s = tv.getText().toString();
				if (savePassword != null) {
					boolean checked = savePassword.isChecked();
					setStringPref(formPfx + getOptDigest(opt), checked ? s : "");
					setStringPref(formPfx + "savePass", checked ? "true" : "false");
				}
				opt.value = s;
				break;
			}
			case LibOpenConnect.OC_FORM_OPT_SELECT:
				String s = (String)opt.userData;
				if (!noSave) {
					setStringPref(formPfx + getOptDigest(opt), s);
					if ("group_list".equals(opt.name)) {
						setStringPref("authgroup", s);
					}
				}
				opt.value = s;
				break;
			}
		}
		if (automaticLogin != null) {
			setStringPref("batch_mode",
					automaticLogin.isChecked() ? "empty_only" : "disabled");
		}
	}

	// If the user had saved a preferred authgroup, submit a NEWGROUP request before rendering the form
	public boolean setAuthgroup() {
		LibOpenConnect.FormOpt opt = mForm.authgroupOpt;
		if (opt == null) {
			return false;
		}

		String authgroup = getStringPref("authgroup");
		if (authgroup.equals("")) {
			return false;
		}

		LibOpenConnect.FormChoice selected = opt.choices.get(mForm.authgroupSelection);
		if (mAuthgroupSet || authgroup.equals(selected.name)) {
			// already good to go
			opt.value = authgroup;
			return false;
		}
		for (LibOpenConnect.FormChoice ch : opt.choices) {
			if (authgroup.equals(ch.name)) {
				opt.value = authgroup;
				return true;
			}
		}
		Log.w(TAG, "saved authgroup '" + authgroup + "' not present in " + opt.name + " dropdown");
		return false;
	}

	public Object earlyReturn() {
		if (setAuthgroup()) {
			return LibOpenConnect.OC_FORM_RESULT_NEWGROUP;
		}
		if (batchMode != BATCH_MODE_EMPTY_ONLY && batchMode != BATCH_MODE_ENABLED) {
			return null;
		}

		// do a quick pass through all prompts to see if we can fill in the
		// answers without bugging the user
		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			if ((opt.flags & LibOpenConnect.OC_FORM_OPT_IGNORE) != 0) {
				continue;
			}
			switch (opt.type) {
			case LibOpenConnect.OC_FORM_OPT_PASSWORD:
			case LibOpenConnect.OC_FORM_OPT_TEXT:
				String defval = noSave ? "" : getStringPref(formPfx + getOptDigest(opt));
				if (defval.equals("")) {
					return null;
				}
				opt.value = defval;
				break;
			case LibOpenConnect.OC_FORM_OPT_SELECT:
				if (opt.value == null) {
					return null;
				}
				break;
			}
		}
		return LibOpenConnect.OC_FORM_RESULT_OK;
	}

	public void onStart(Context context) {
		final AuthFormHandler h = this;

		super.onStart(context);
		mContext = context;
		isOK = false;

		float scale = mContext.getResources().getDisplayMetrics().density;
		LinearLayout v = new LinearLayout(mContext);
		v.setOrientation(LinearLayout.VERTICAL);
		v.setPadding((int)(16*scale), (int)(2*scale), (int)(16*scale), (int)(2*scale));

		boolean hasPassword = false, hasUserOptions = false;
		String defval;
		Map<String, Integer> labelCounts = new HashMap<String, Integer>();
		Map<String, Integer> labelIndexes = new HashMap<String, Integer>();
		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			if ((opt.flags & LibOpenConnect.OC_FORM_OPT_IGNORE) != 0 ||
					(opt.type != LibOpenConnect.OC_FORM_OPT_TEXT &&
							opt.type != LibOpenConnect.OC_FORM_OPT_PASSWORD)) {
				continue;
			}
			String key = labelKey(opt.label);
			Integer count = labelCounts.get(key);
			labelCounts.put(key, count == null ? 1 : count + 1);
		}

		mFirstText = mFirstEmptyText = null;
		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			if ((opt.flags & LibOpenConnect.OC_FORM_OPT_IGNORE) != 0) {
				continue;
			}
			switch (opt.type) {
			case LibOpenConnect.OC_FORM_OPT_PASSWORD:
				hasPassword = true;
				/* falls through */
			case LibOpenConnect.OC_FORM_OPT_TEXT:
				defval = noSave ? "" : getStringPref(formPfx + getOptDigest(opt));
				if (defval.equals("")) {
					if (opt.value != null && !opt.value.equals("")) {
						defval = opt.value;
					} else {
						/* note that this gets remembered across redraws */
						mAllFilled = false;
					}
				}
				v.addView(newTextBlank(
						opt, defval, getFieldLabel(opt, labelCounts, labelIndexes)));
				hasUserOptions = true;
				break;
			case LibOpenConnect.OC_FORM_OPT_SELECT:
				if (opt.choices.size() == 0) {
					break;
				}

				int selection = 0;
				if (opt == mForm.authgroupOpt) {
					selection = mForm.authgroupSelection;
				} else {
					// do any servers actually use non-authgroup downdowns?
					defval = noSave ? "" : getStringPref(formPfx + getOptDigest(opt));
					for (int i = 0; i < opt.choices.size(); i++) {
						if (opt.choices.get(i).name.equals(defval)) {
							selection = i;
						}
					}
				}
				v.addView(newDropdown(opt, selection));
				hasUserOptions = true;
				break;
			}
		}
		if (hasPassword) {
			if (!noSave) {
				savePassword = newSavePasswordView(true);
				savePassword.setChecked(true);
				savePassword.setVisibility(View.GONE);
				automaticLogin = newAutomaticLoginView(true);
				automaticLogin.setChecked(true);
				automaticLogin.setVisibility(View.GONE);
				v.addView(savePassword);
				v.addView(automaticLogin);
			}
			// hint removed
		}

		if (batchMode == BATCH_MODE_ABORTED) {
			finish(LibOpenConnect.OC_FORM_RESULT_CANCELLED);
			return;
		}

		if ((batchMode == BATCH_MODE_EMPTY_ONLY && mAllFilled) ||
			batchMode == BATCH_MODE_ENABLED || !hasUserOptions) {
			saveAndStore();
			finish(LibOpenConnect.OC_FORM_RESULT_OK);
			return;
		}

		android.content.SharedPreferences appSp = mContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
		boolean isRu = !"en".equals(appSp.getString("app_lang", "ru"));
		String btnOk = isRu ? "ОК" : "OK";
		String btnCancel = isRu ? "Отмена" : "Cancel";

		v.setPadding(dp(24), dp(20), dp(24), dp(8));
                mAlert = new AlertDialog.Builder(mContext)
				.setView(v)
				.setPositiveButton(btnOk, h)
				.setNegativeButton(btnCancel, h)
				.create();
		mAlert.setOnDismissListener(h);

		String displayMessage = getDisplayMessage();
		if (!displayMessage.isEmpty()) {
			mAlert.setMessage(displayMessage);
		}

		mAlert.show();
		if (mAlert.getWindow() != null) {
			int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
			int availableWidth = screenWidth - dp(48);
			int dialogWidth = Math.min(availableWidth, dp(560));
			mAlert.getWindow().getDecorView().setBackgroundResource(
					R.drawable.bg_dialog_surface);
			mAlert.getWindow().setLayout(
					Math.max(dialogWidth, dp(280)),
					LayoutParams.WRAP_CONTENT);
		}

		TextView focus = mFirstEmptyText != null ? mFirstEmptyText : mFirstText;
		if (focus != null) {
			focus.append("");
			focus.requestFocus();
		}
	}

	public void onStop(Context context) {
		super.onStop(context);
		if (mAlert != null) {
			saveAndStore();
			mAlert.dismiss();
			mAlert = null;
		}
	}
}
