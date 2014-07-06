/******************************************************************************
 * Copyright (C) 2012, 2013, 2014
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of FeedHive
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
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
