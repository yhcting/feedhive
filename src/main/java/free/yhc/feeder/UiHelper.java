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

import java.io.File;
import java.util.LinkedList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import free.yhc.abaselib.AppEnv;
import free.yhc.baselib.Logger;
import free.yhc.baselib.async.HelperHandler;
import free.yhc.baselib.async.Task;
import free.yhc.baselib.async.ThreadEx;
import free.yhc.abaselib.util.AUtil;
import free.yhc.abaselib.util.UxUtil;
import free.yhc.abaselib.ux.DialogTask;
import free.yhc.feeder.core.Util;
import free.yhc.feeder.db.ColumnItem;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.ContentsManager;
import free.yhc.feeder.core.Err;
import free.yhc.feeder.feed.Feed;
import free.yhc.feeder.core.RTTask;

import static free.yhc.baselib.util.Util.convertArrayLongTolong;

public class UiHelper {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(UiHelper.class, Logger.LOGLV_DEFAULT);

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

    public static class DeleteAllDnfilesTask extends Task<Void> {
        private final Context _mContext;
        private final OnPostExecuteListener _mOnPostExecute;
        private final Object _mUser;
        public DeleteAllDnfilesTask(
                Context context,
                OnPostExecuteListener onPostExecute,
                Object user) {
            super(DeleteAllDnfilesTask.class.getSimpleName(),
                  HelperHandler.get(),
                  ThreadEx.TASK_PRIORITY_NORM,
                  false);
            _mContext = context;
            _mOnPostExecute = onPostExecute;
            _mUser = user;
        }

        @Override
        protected Void
        doAsync() {
            ContentsManager.get().cleanAllChannelDirs();
            AppEnv.getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    UxUtil.showTextToast(R.string.delete_downloadded_files_errmsg);
                    if (null != _mOnPostExecute)
                        _mOnPostExecute.onPostExecute(Err.NO_ERR, _mUser);
                }
            });
            return null;
        }
    }

    public static class DeleteUsedDnfilesTask extends Task<Void> {
        private final long _mCid;
        private final Context _mContext;
        private final OnPostExecuteListener _mOnPostExecute;
        private final Object _mUser;
        public DeleteUsedDnfilesTask(long cid,
                                     Context context,
                                     OnPostExecuteListener onPostExecute,
                                     Object user) {
            super(DeleteAllDnfilesTask.class.getSimpleName(),
                  HelperHandler.get(),
                  ThreadEx.TASK_PRIORITY_NORM,
                  false);
            _mCid = cid;
            _mContext = context;
            _mOnPostExecute = onPostExecute;
            _mUser = user;
        }

        @Override
        protected Void
        doAsync() {
            DBPolicy dbp = DBPolicy.get();
            ContentsManager cm = ContentsManager.get();
            LinkedList<File> l;
            if (_mCid > 0)
                l = cm.getContentFiles(new long[] { _mCid });
            else
                l = cm.getContentFiles();

            // check each content file to know whether they are valid or not.
            LinkedList<Long> idsl = new LinkedList<>();
            for (File f : l) {
                long id = cm.getIdFromContentFileName(f.getName());
                if (0 > id)
                    // unexpected content file.
                    continue;
                long state = dbp.getItemInfoLong(id, ColumnItem.STATE);
                if (!Feed.Item.isStateOpenNew(state))
                    idsl.add(id);
            }
            final int failcnt = cm.deleteItemContents(
                    convertArrayLongTolong(idsl.toArray(new Long[idsl.size()])));
            AppEnv.getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (0 < failcnt)
                        UxUtil.showTextToast(R.string.delete_downloadded_files_errmsg);
                    if (null != _mOnPostExecute)
                        _mOnPostExecute.onPostExecute(Err.NO_ERR, _mUser);
                }
            });
            return null;
        }
    }

    public static AlertDialog
    createWarningDialog(Context context, CharSequence title, CharSequence message) {
        return UxUtil.createAlertDialog(context, R.drawable.ic_alert, title, message);
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
        View layout = AUtil.inflateLayout(R.layout.oneline_editbox_dialog);
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
                       final UxUtil.ConfirmAction action) {
        return UxUtil.buildConfirmDialog(
                context,
                title,
                description,
                R.drawable.ic_info,
                AUtil.getResText(R.string.yes),
                AUtil.getResText(R.string.no),
                action);
    }

    public static AlertDialog
    buildConfirmDialog(final Context context,
                       final int title,
                       final int description,
                       final UxUtil.ConfirmAction action) {
        return buildConfirmDialog(context,
                                  context.getResources().getText(title),
                                  context.getResources().getText(description),
                                  action);
    }

    public static AlertDialog
    selectCategoryDialog(final Context  context,
                         final int title,
                         final OnCategorySelectedListener action,
                         final long catIdExcluded,
                         final Object tag) {
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
                if (!Util.isValidValue(cat.name)
                    && dbp.isDefaultCategoryId(cat.id))
                    menus[i] = context.getResources().getText(R.string.default_category_name).toString();
                else
                    menus[i] = cat.name;

                catIds[i++] = cat.id;
            }
        }

        AlertDialog.Builder bldr = new AlertDialog.Builder(context);
        ArrayAdapter<String> adapter
            = new ArrayAdapter<>(context, android.R.layout.select_dialog_item, menus);
        bldr.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialog, int which) {
                action.onSelected(catIds[which], tag);
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

        UxUtil.ConfirmAction action = new UxUtil.ConfirmAction() {
            @Override
            public void
            onPositive(@NonNull Dialog dialog) {
                DialogTask.Builder<DialogTask.Builder> bldr
                        = new DialogTask.Builder<>(
                        context,
                        new DeleteAllDnfilesTask(context,
                                                 onPostExecute,
                                                 postExecuteUser));
                bldr.setMessage(R.string.delete_all_downloadded_file);
                if (!bldr.create().start())
                    P.bug();
            }

            @Override
            public void
            onNegative(@NonNull Dialog dialog) {
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
     * @param cid 0 means 'all channels'
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

        UxUtil.ConfirmAction action = new UxUtil.ConfirmAction() {
            @Override
            public void
            onPositive(@NonNull Dialog dialog) {
                DialogTask.Builder<DialogTask.Builder> bldr
                        = new DialogTask.Builder<>(
                        context,
                        new DeleteUsedDnfilesTask(cid,
                                                  context,
                                                  onPostExecute,
                                                  postExecuteUser));
                bldr.setMessage(resTitle);
                if (!bldr.create().start())
                    P.bug();
            }

            @Override
            public void
            onNegative(@NonNull Dialog dialog) {
                if (null != onPostExecute)
                    onPostExecute.onPostExecute(Err.USER_CANCELLED, postExecuteUser);
            }
        };

        return buildConfirmDialog(context, resTitle, resMsg, action);
    }
}
