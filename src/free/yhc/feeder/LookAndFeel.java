/*****************************************************************************
 *    Copyright (C) 2012 Younghyung Cho. <yhcting77@gmail.com>
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
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

public class LookAndFeel {
    private static void
    showToast(Context context, ViewGroup root) {
        Toast t = Toast.makeText(context, "", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.setView(root);
        t.show();
    }

    public static View
    inflateLayout(Context context, int layout) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(layout, null);
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
        Toast t = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    public static void
    showTextToast(Context context, int textid) {
        Toast t = Toast.makeText(context, textid, Toast.LENGTH_SHORT);
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

    public static AlertDialog
    createEditTextDialog(Context context, View layout, int title) {
        // Create "Enter Url" dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(layout);

        final AlertDialog dialog = builder.create();
        dialog.setTitle(title);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        return dialog;
    }
}
