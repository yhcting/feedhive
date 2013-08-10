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
