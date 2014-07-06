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
import android.R.style;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import free.yhc.feeder.appwidget.AppWidgetUtils;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class AppWidgetMenuActivity extends Activity  implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(AppWidgetMenuActivity.class);

    private final Intent mResultValue = new Intent();
    private boolean      mFinishOnDismiss = true;

    private void
    onSelectDeleteAllDownloadded() {
        AlertDialog diag = UiHelper.buildDeleteAllDnFilesConfirmDialog(
                this,
                new UiHelper.OnPostExecuteListener() {
                    @Override
                    public void
                    onPostExecute(Err err, Object user) {
                        if (Err.USER_CANCELLED == err)
                            setResult(RESULT_CANCELED, mResultValue);
                        else
                            setResult(RESULT_OK, mResultValue);
                        finish();
                    }
                },
                null);
        if (null == diag) {
            UiHelper.showTextToast(this, R.string.del_dnfiles_not_allowed_msg);
            finish();
        } else {
            diag.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean
                onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    if (KeyEvent.KEYCODE_BACK == keyCode) {
                        finish();
                        return true;
                    }
                    return false;
                }
            });
            diag.show();
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .AppWidgetMenuActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                        AppWidgetUtils.INVALID_APPWIDGETID);
        eAssert(AppWidgetUtils.INVALID_APPWIDGETID != appWidgetId);

        mResultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        final String[] menus = {
                getResources().getText(R.string.delete_all_downloadded_file).toString(),
        };

        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        ArrayAdapter<String> adapter
            = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, menus);
        bldr.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialog, int which) {
                if (getResources()
                        .getText(R.string.delete_all_downloadded_file)
                        .toString()
                        .equals(menus[which])) {
                    mFinishOnDismiss = false;
                    onSelectDeleteAllDownloadded();
                }
            }
        });
        AlertDialog diag = bldr.create();
        diag.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void
            onDismiss(DialogInterface dialog) {
                if (mFinishOnDismiss)
                    finish();
            }
        });
        diag.show();
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void
    onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        // no background panel is shown
        theme.applyStyle(style.Theme_Panel, true);
    }
}
