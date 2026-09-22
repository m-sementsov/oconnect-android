/*
 * Small shared helper for the app's manual RU/EN language switcher.
 * Applies the "app_lang" preference to a given Context's Resources
 * Configuration. Needs to be called for every process entry point that
 * reads string resources independently of the Activity stack (i.e. the
 * Application object itself and the VPN Service), since patching a single
 * Activity's Configuration does not affect other Contexts.
 */

package app.openconnect;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleUtil {

    public static void apply(Context ctx) {
        SharedPreferences appSp = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String lang = appSp.getString("app_lang", "ru");
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(ctx.getResources().getConfiguration());
        config.setLocale(locale);
        ctx.getResources().updateConfiguration(config, ctx.getResources().getDisplayMetrics());
    }
}
