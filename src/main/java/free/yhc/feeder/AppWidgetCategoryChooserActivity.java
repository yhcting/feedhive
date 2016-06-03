/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015, 2016
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

import android.R.style;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;

import free.yhc.baselib.Logger;
import free.yhc.feeder.appwidget.AppWidgetUtils;
import free.yhc.feeder.appwidget.UpdateService;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.core.UnexpectedExceptionHandler;

public class AppWidgetCategoryChooserActivity extends Activity  implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(AppWidgetCategoryChooserActivity.class, Logger.LOGLV_DEFAULT);

    public static final String KEY_CANCELABLE = "cancelable";

    @SuppressWarnings("FieldCanBeLocal")
    private boolean mCancelable = false;

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .AppWidgetCategoryChooserActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                        AppWidgetUtils.INVALID_APPWIDGETID);
        P.bug(AppWidgetUtils.INVALID_APPWIDGETID != appWidgetId);
        mCancelable = getIntent().getBooleanExtra(KEY_CANCELABLE, false);

        final Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        UiHelper.OnCategorySelectedListener action = new UiHelper.OnCategorySelectedListener() {
            @Override
            public void
            onSelected(final long category, Object user) {
                AppWidgetUtils.putWidgetToCategoryMap(appWidgetId, category);
                UpdateService.update(AppWidgetCategoryChooserActivity.this,
                                     new long[] { category });
                setResult(RESULT_OK, resultValue);
                finish();
            }
        };

        AlertDialog diag = UiHelper.selectCategoryDialog(this,
                                                         R.string.select_category,
                                                         action,
                                                         DB.INVALID_ITEM_ID,
                                                         null);
        diag.setCancelable(mCancelable);
        diag.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void
            onDismiss(DialogInterface dialog) {
                setResult(RESULT_CANCELED, resultValue);
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
    onApplyThemeResource(@NonNull Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        // no background panel is shown
        theme.applyStyle(style.Theme_Panel, true);
    }
}
