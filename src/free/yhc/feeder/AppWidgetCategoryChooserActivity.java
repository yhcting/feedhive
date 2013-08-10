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
import android.R.style;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import free.yhc.feeder.appwidget.AppWidgetUtils;
import free.yhc.feeder.appwidget.UpdateService;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class AppWidgetCategoryChooserActivity extends Activity  implements
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(AppWidgetCategoryChooserActivity.class);

    public static final String KEY_CANCELABLE = "cancelable";

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
        eAssert(AppWidgetUtils.INVALID_APPWIDGETID != appWidgetId);
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
    onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        // no background panel is shown
        theme.applyStyle(style.Theme_Panel, true);
    }
}
