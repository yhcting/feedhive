/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
 *
 *    This file is part of Feeder.
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as
 *    published by the Free Software Foundation either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License
 *    (<http://www.gnu.org/licenses/lgpl.html>) for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;

import java.io.File;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import free.yhc.feeder.model.Environ;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class FeederPreferenceActivity extends PreferenceActivity implements
SharedPreferences.OnSharedPreferenceChangeListener,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(FeederPreferenceActivity.class);

    private String mAppRootOld = null;

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ FeederPreferenceActivity ]";
    }

    @Override
    public void
    onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Environ.PREF_KEY_APP_ROOT)) {
            String appRoot = sharedPreferences.getString(Environ.PREF_KEY_APP_ROOT, null);
            File appRootFile = new File(appRoot);
            if (!appRootFile.canWrite()) {
                UiHelper.showTextToast(this, R.string.warn_file_access_denied);
                SharedPreferences.Editor prefEd = sharedPreferences.edit();
                prefEd.putString(Environ.PREF_KEY_APP_ROOT, mAppRootOld);
                prefEd.apply();
            } else {
                Environ.get().setAppDirectories(appRoot);
                mAppRootOld = appRoot;
            }
        }
    }

    @Override
    protected void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAppRootOld = prefs.getString(Environ.PREF_KEY_APP_ROOT, null);
        prefs.registerOnSharedPreferenceChangeListener(this);
        eAssert(null != mAppRootOld);
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }
}
