package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.R.style;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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


    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ .AppWidgetCategoryChooserActivity ]";
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int appWidgetId = getIntent().getIntExtra(AppWidgetUtils.MAP_KEY_APPWIDGETID,
                                                        AppWidgetUtils.INVALID_APPWIDGETID);
        eAssert(AppWidgetUtils.INVALID_APPWIDGETID != appWidgetId);
        UiUtils.OnCategorySelectedListener action = new UiUtils.OnCategorySelectedListener() {
            @Override
            public void
            onSelected(final long category, Object user) {
                AppWidgetUtils.putWidgetToCategoryMap(appWidgetId, category);
                UpdateService.update(AppWidgetCategoryChooserActivity.this,
                                     new long[] { category });
            }
        };

        AlertDialog diag = UiUtils.selectCategoryDialog(this,
                                                        R.string.select_category,
                                                        action,
                                                        DB.INVALID_ITEM_ID,
                                                        null);
        diag.setCancelable(false);
        diag.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void
            onDismiss(DialogInterface dialog) {
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
