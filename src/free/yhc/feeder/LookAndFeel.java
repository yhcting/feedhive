package free.yhc.feeder;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

public class LookAndFeel {
    private static LinearLayout
    inflateLayout(Context context, int layout) {
        LinearLayout root = new LinearLayout(context);
        root.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                                                           LinearLayout.LayoutParams.WRAP_CONTENT));
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(layout, root);
        return root;
    }

    private static void
    showToast(Context context, ViewGroup root) {
        Toast t = Toast.makeText(context, "", Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.setView(root);
        t.show();
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
}
