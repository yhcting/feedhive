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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class LookAndFeel implements
UnexpectedExceptionHandler.TrackedModule {
    // Even if LookAndFeel looks like suitable for static class,
    //   singleton is used because multiple instance may be used with high possibility at future.
    private static LookAndFeel sInstance = null;

    public interface EditTextDialogAction {
        void prepare(Dialog dialog, EditText edit);
        void onOk(Dialog dialog, EditText edit);
    }

    public interface ConfirmDialogAction {
        void onOk(Dialog dialog);
    }

    private LookAndFeel() {
        // Dependency on only following modules are allowed
        // - Utils
        // - UnexpectedExceptionHandler
        // - DB / DBThread
        // - UIPolicy
        // - DBPolicy
        // - RTTask
        UnexpectedExceptionHandler.get().registerModule(this);
    }

    public static LookAndFeel
    get() {
        if (null == sInstance)
            sInstance = new LookAndFeel();
        return sInstance;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ LookAndFeel ]";
    }

    /**
     * This is for future use...
     * @param context
     * @param root
     */
    private void
    showToast(Context context, ViewGroup root) {
        Toast t = Toast.makeText(context, "", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.setView(root);
        t.show();
    }

    public View
    inflateLayout(Context context, int layout) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(layout, null);
    }

    public void
    showTextToast(Context context, String text) {
        Toast t = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    public void
    showTextToast(Context context, int textid) {
        Toast t = Toast.makeText(context, textid, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    public AlertDialog
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

    public AlertDialog
    createAlertDialog(Context context, int icon, int title, int message) {
        eAssert(0 != title);
        CharSequence t = context.getResources().getText(title);
        CharSequence msg = (0 == message)? null: context.getResources().getText(message);
        return createAlertDialog(context, icon, t, msg);
    }

    public AlertDialog
    createWarningDialog(Context context, CharSequence title, CharSequence message) {
        return createAlertDialog(context, R.drawable.ic_alert, title, message);
    }

    public AlertDialog
    createWarningDialog(Context context, int title, int message) {
        return createWarningDialog(context,
                                   context.getResources().getText(title),
                                   context.getResources().getText(message));
    }

    public AlertDialog
    createEditTextDialog(Context context, View layout, CharSequence title) {
        // Create "Enter Url" dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(layout);

        final AlertDialog dialog = builder.create();
        dialog.setTitle(title);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        return dialog;
    }

    public AlertDialog
    createEditTextDialog(Context context, View layout, int title) {
        return createEditTextDialog(context, layout, context.getResources().getText(title));
    }

    public AlertDialog
    buildOneLineEditTextDialog(final Context context, final CharSequence title, final EditTextDialogAction action) {
        // Create "Enter Url" dialog
        View layout = inflateLayout(context, R.layout.oneline_editbox_dialog);
        final AlertDialog dialog = createEditTextDialog(context, layout, title);
        // Set action for dialog.
        final EditText edit = (EditText)layout.findViewById(R.id.editbox);
        edit.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((KeyEvent.ACTION_DOWN == event.getAction()) && (KeyEvent.KEYCODE_ENTER == keyCode)) {
                    dialog.dismiss();
                    if (!edit.getText().toString().isEmpty())
                        action.onOk(dialog, ((EditText)v));
                    return true;
                }
                return false;
            }
        });
        action.prepare(dialog, edit);

        dialog.setButton(context.getResources().getText(R.string.ok),
                         new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dia, int which) {
                dialog.dismiss();
                if (!edit.getText().toString().isEmpty())
                    action.onOk(dialog, edit);
            }
        });

        dialog.setButton2(context.getResources().getText(R.string.cancel),
                          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return dialog;
    }

    public AlertDialog
    buildOneLineEditTextDialog(final Context context, final int title, final EditTextDialogAction action) {
        return buildOneLineEditTextDialog(context, context.getResources().getText(title), action);
    }

    public AlertDialog
    buildConfirmDialog(final Context context,
                       final CharSequence title,
                       final CharSequence description,
                       final ConfirmDialogAction action) {
        final AlertDialog dialog = createAlertDialog(context, R.drawable.ic_info, title, description);
        dialog.setButton(context.getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface diag, int which) {
                dialog.dismiss();
                action.onOk(dialog);
            }
        });

        dialog.setButton2(context.getResources().getText(R.string.no),
                          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return dialog;
    }

    public AlertDialog
    buildConfirmDialog(final Context context,
                       final int title,
                       final int description,
                       final ConfirmDialogAction action) {
        return buildConfirmDialog(context,
                                  context.getResources().getText(title),
                                  context.getResources().getText(description),
                                  action);
    }
}
