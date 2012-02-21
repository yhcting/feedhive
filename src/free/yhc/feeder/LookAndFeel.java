package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

public class LookAndFeel {
    private static void
    showToast(Context context, ViewGroup root) {
        Toast t = Toast.makeText(context, "", Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.setView(root);
        t.show();
    }

    public static LinearLayout
    inflateLayout(Context context, int layout) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return (LinearLayout)inflater.inflate(layout, null);
    }

    public static void
    showIconToast(Context context, int icon, int textid) {
        showTextToast(context, textid);
        /*
        LinearLayout root = inflateLayout(context, R.layout.icon_toast);
        ((TextView)root.findViewById(R.id.text)).setText(stringid);
        ((ImageView)root.findViewById(R.id.icon)).setImageResource(R.drawable.icon);
        showToast(context, root);
        */
    }

    public static void
    showTextToast(Context context, String text) {
        Toast t = Toast.makeText(context, text, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    public static void
    showTextToast(Context context, int textid) {
        Toast t = Toast.makeText(context, textid, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
        /*
        LinearLayout root = inflateLayout(context, R.layout.text_toast);
        ((TextView)root.findViewById(R.id.text)).setText(textid);
        showToast(context, root);
        */
    }

    public static AlertDialog
    createAlertDialog(Context context, int icon, CharSequence title, CharSequence message) {
        eAssert(null != title);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        AlertDialog dialog = builder.create();
        if (0 != icon)
            dialog.setIcon(icon);
        dialog.setTitle(title);
        if (null != message)
            dialog.setMessage(message);
        return dialog;
    }

    public static AlertDialog
    createAlertDialog(Context context, int icon, int title, int message) {
        eAssert(0 != title);
        CharSequence t = context.getResources().getText(title);
        CharSequence msg = (0 == message)? null: context.getResources().getText(message);
        return createAlertDialog(context, icon, t, msg);
    }

    public static AlertDialog
    createWarningDialog(Context context, int title, int message) {
        return createAlertDialog(context, R.drawable.ic_alert, title, message);
    }
}
