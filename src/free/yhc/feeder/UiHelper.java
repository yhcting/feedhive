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

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;

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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.ContentsManager;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.Utils;

public class UiHelper {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(UiHelper.class);

    public interface EditTextDialogAction {
        void prepare(Dialog dialog, EditText edit);
        void onOk(Dialog dialog, EditText edit);
    }

    public interface OnCategorySelectedListener {
        void onSelected(long category, Object user);
    }

    public interface OnPostExecuteListener {
        void onPostExecute(Err err, Object user);
    }

    public interface OnConfirmDialogAction {
        void onOk(Dialog dialog);
        void onCancel(Dialog dialog);
    }

    public static class DeleteAllDnfilesWorker extends DiagAsyncTask.Worker {
        private final Context                 _mContext;
        private final OnPostExecuteListener   _mOnPostExecute;
        private final Object                  _mUser;
        public DeleteAllDnfilesWorker(Context context,
                                      OnPostExecuteListener onPostExecute,
                                      Object user) {
            _mContext = context;
            _mOnPostExecute = onPostExecute;
            _mUser = user;
        }

        @Override
        public Err
        doBackgroundWork(DiagAsyncTask task) {
            ContentsManager.get().cleanAllChannelDirs();
            return Err.NO_ERR;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            if (Err.NO_ERR != result)
                UiHelper.showTextToast(_mContext, R.string.delete_downloadded_files_errmsg);
            if (null != _mOnPostExecute)
                _mOnPostExecute.onPostExecute(result, _mUser);
        }
    }

    public static class DeleteUsedDnfilesWorker extends DiagAsyncTask.Worker {
        private final long                    _mCid;
        private final Context                 _mContext;
        private final OnPostExecuteListener   _mOnPostExecute;
        private final Object                  _mUser;
        public DeleteUsedDnfilesWorker(long cid,
                                       Context context,
                                       OnPostExecuteListener onPostExecute,
                                       Object user) {
            _mCid = cid;
            _mContext = context;
            _mOnPostExecute = onPostExecute;
            _mUser = user;
        }

        @Override
        public Err
        doBackgroundWork(DiagAsyncTask task) {
            DBPolicy dbp = DBPolicy.get();
            ContentsManager cm = ContentsManager.get();
            LinkedList<File> l = new LinkedList<File>();
            if (_mCid > 0)
                l = cm.getContentFiles(new long[] { _mCid });
            else
                l = cm.getContentFiles();

            // check each content file to know whether they are valid or not.
            LinkedList<Long> idsl = new LinkedList<Long>();
            Iterator<File> i = l.iterator();
            while (i.hasNext()) {
                File f = i.next();
                long id = cm.getIdFromContentFileName(f.getName());
                if (0 > id)
                    // unexpected content file.
                    continue;
                long state = dbp.getItemInfoLong(id, ColumnItem.STATE);
                if (!Feed.Item.isStateOpenNew(state))
                    idsl.add(id);
            }
            cm.deleteItemContents(Utils.convertArrayLongTolong(idsl.toArray(new Long[0])));
            return Err.NO_ERR;
        }

        @Override
        public void
        onPostExecute(DiagAsyncTask task, Err result) {
            if (Err.NO_ERR != result)
                UiHelper.showTextToast(_mContext, R.string.delete_downloadded_files_errmsg);
            if (null != _mOnPostExecute)
                _mOnPostExecute.onPostExecute(result, _mUser);
        }
    }


    /**
     * This is for future use...
     * @param context
     * @param root
     */
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
    createWarningDialog(Context context, CharSequence title, CharSequence message) {
        return createAlertDialog(context, R.drawable.ic_alert, title, message);
    }

    public static AlertDialog
    createWarningDialog(Context context, int title, int message) {
        return createWarningDialog(context,
                                   context.getResources().getText(title),
                                   context.getResources().getText(message));
    }

    public static AlertDialog
    createEditTextDialog(Context context, View layout, CharSequence title) {
        // Create "Enter Url" dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(layout);

        final AlertDialog dialog = builder.create();
        dialog.setTitle(title);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        return dialog;
    }

    public static AlertDialog
    createEditTextDialog(Context context, View layout, int title) {
        return createEditTextDialog(context, layout, context.getResources().getText(title));
    }

    public static AlertDialog
    buildOneLineEditTextDialog(final Context context, final CharSequence title, final EditTextDialogAction action) {
        // Create "Enter Url" dialog
        View layout = inflateLayout(context, R.layout.oneline_editbox_dialog);
        final AlertDialog dialog = createEditTextDialog(context, layout, title);
        // Set action for dialog.
        final EditText edit = (EditText)layout.findViewById(R.id.editbox);
        edit.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean
            onKey(View v, int keyCode, KeyEvent event) {
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

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getResources().getText(R.string.ok),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dia, int which) {
                dialog.dismiss();
                if (!edit.getText().toString().isEmpty())
                    action.onOk(dialog, edit);
            }
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getResources().getText(R.string.cancel),
                          new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return dialog;
    }

    public static AlertDialog
    buildOneLineEditTextDialog(final Context context, final int title, final EditTextDialogAction action) {
        return buildOneLineEditTextDialog(context, context.getResources().getText(title), action);
    }

    public static AlertDialog
    buildConfirmDialog(final Context context,
                       final CharSequence title,
                       final CharSequence description,
                       final OnConfirmDialogAction action) {
        final AlertDialog dialog = createAlertDialog(context, R.drawable.ic_info, title, description);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getResources().getText(R.string.yes),
                         new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface diag, int which) {
                action.onOk(dialog);
                dialog.dismiss();
            }
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getResources().getText(R.string.no),
                          new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface diag, int which) {
                action.onCancel(dialog);
                dialog.dismiss();
            }
        });
        return dialog;
    }

    public static AlertDialog
    buildConfirmDialog(final Context context,
                       final int title,
                       final int description,
                       final OnConfirmDialogAction action) {
        return buildConfirmDialog(context,
                                  context.getResources().getText(title),
                                  context.getResources().getText(description),
                                  action);
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

    public static AlertDialog
    buildDeleteAllDnFilesConfirmDialog(final Context context,
                                       final OnPostExecuteListener onPostExecute,
                                       final Object postExecuteUser) {
        // check constraints
        if (RTTask.get().getItemsDownloading().length > 0)
            return null;

        OnConfirmDialogAction action = new OnConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                DiagAsyncTask task = new DiagAsyncTask(context,
                                                       new DeleteAllDnfilesWorker(context,
                                                                                  onPostExecute,
                                                                                  postExecuteUser),
                                                       DiagAsyncTask.Style.SPIN,
                                                       R.string.delete_all_downloadded_file);
                task.run();
            }

            @Override
            public void
            onCancel(Dialog dialog) {
                if (null != onPostExecute)
                    onPostExecute.onPostExecute(Err.USER_CANCELLED, postExecuteUser);
            }
        };

        return buildConfirmDialog(context,
                                  R.string.delete_all_downloadded_file,
                                  R.string.delete_all_downloadded_file_msg,
                                  action);
    }

    /**
     * @param cid
     *     0 means 'all channels'
     * @param context
     * @param onPostExecute
     * @param postExecuteUser
     * @return
     */
    public static AlertDialog
    buildDeleteUsedDnFilesConfirmDialog(final long cid,
                                        final Context context,
                                        final OnPostExecuteListener onPostExecute,
                                        final Object postExecuteUser) {
        // check constraints
        if (RTTask.get().getItemsDownloading().length > 0)
            return null;

        final int resTitle;
        final int resMsg;
        if (0 < cid) {
            resTitle = R.string.delete_used_downloadded_file;
            resMsg = R.string.delete_channel_used_downloadded_file_msg;
        } else {
            resTitle = R.string.delete_all_used_downloadded_file;
            resMsg = R.string.delete_all_used_downloadded_file_msg;
        }

        OnConfirmDialogAction action = new OnConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                DiagAsyncTask task = new DiagAsyncTask(context,
                                                       new DeleteUsedDnfilesWorker(cid,
                                                                                   context,
                                                                                   onPostExecute,
                                                                                   postExecuteUser),
                                                       DiagAsyncTask.Style.SPIN,
                                                       resTitle);
                task.run();
            }

            @Override
            public void
            onCancel(Dialog dialog) {
                if (null != onPostExecute)
                    onPostExecute.onPostExecute(Err.USER_CANCELLED, postExecuteUser);
            }
        };

        return buildConfirmDialog(context, resTitle, resMsg, action);
    }
}
