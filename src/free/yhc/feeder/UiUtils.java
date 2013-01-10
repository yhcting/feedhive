package free.yhc.feeder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ArrayAdapter;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.Utils;
import free.yhc.feeder.model.Utils.Logger;

public class UiUtils {
    private static final boolean DBG = false;
    private static final Logger P = new Logger(UiUtils.class);

    interface OnCategorySelectedListener {
        void onSelected(long category, Object user);
    }

    public static AlertDialog
    selectCategoryDialog(final Context  context,
                         final int      title,
                         final OnCategorySelectedListener action,
                         final long     catIdExcluded,
                         final Object   user) {
        // Create Adapter for list and set it.
        DBPolicy dbp = DBPolicy.get();
        Feed.Category[] cats = dbp.getCategories();

        int nrExcluded = 0;
        for (Feed.Category cat : cats) {
            if (cat.id == catIdExcluded) {
                nrExcluded = 1;
                break;
            }
        }

        final String[] menus = new String[cats.length - nrExcluded];
        final long[] catIds = new long[cats.length - nrExcluded];
        int i = 0;
        for (Feed.Category cat : cats) {
            if (cat.id != catIdExcluded) {
                if (!Utils.isValidValue(cat.name)
                    && dbp.isDefaultCategoryId(cat.id))
                    menus[i] = context.getResources().getText(R.string.default_category_name).toString();
                else
                    menus[i] = cat.name;

                catIds[i++] = cat.id;
            }
        }

        AlertDialog.Builder bldr = new AlertDialog.Builder(context);
        ArrayAdapter<String> adapter
            = new ArrayAdapter<String>(context, android.R.layout.select_dialog_item, menus);
        bldr.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialog, int which) {
                action.onSelected(catIds[which], user);
                dialog.dismiss();
            }
        });
        bldr.setTitle(title);
        return bldr.create();
    }
}
