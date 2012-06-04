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

import java.text.DateFormat;
import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class ChannelListAdapter extends CustomResourceCursorAdapter implements
UnexpectedExceptionHandler.TrackedModule {
    private OnAction  onAction = null;

    interface OnAction {
        void onUpdateClick(ImageView ibtn, long cid);
        void onMoveUpClick(ImageView ibtn, long cid);
        void onMoveDownClick(ImageView ibtn, long cid);
    }

    public static class ImageViewEx extends ImageView {
        long cid = -1;

        public
        ImageViewEx(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelListAdapter ]";
    }

    ChannelListAdapter(Context context, int layout, Cursor c, OnAction actionListener) {
        super(context, layout, c);
        UnexpectedExceptionHandler.S().registerModule(this);
        onAction = actionListener;
    }

    @Override
    public void
    bindView(View view, final Context context, final Cursor c) {
        long cid = getCursorLong(c, DB.ColumnChannel.ID);

        try {
            if (!isChanged(cid))
                return;
        } finally {
            clearChangeState(cid);
        }

        String title = getCursorString(c, DB.ColumnChannel.TITLE);
        String desc = getCursorString(c, DB.ColumnChannel.DESCRIPTION);

        // date to readable string
        Date lastupdate = new Date(getCursorLong(c, DB.ColumnChannel.LASTUPDATE));
        String date = DateFormat.getInstance().format(lastupdate);

        // === Set 'age' ===
        // calculate age and convert to readable string.
        String age;
        { // just for temporal variable scope
            long ageTime = new Date().getTime() - lastupdate.getTime();
            // Show "day:hours"
            long ageHours = ageTime/ (1000 * 60 * 60);
            long ageDay = ageHours / 24;
            ageHours %= 24;
            age = String.format("%2d:%2d", ageDay, ageHours);
        }

        long nrNew = DBPolicy.S().getItemInfoMaxId(cid)
                        - DBPolicy.S().getChannelInfoLong(cid, ColumnChannel.OLDLAST_ITEMID);

        Bitmap bm = null;
        byte[] imgRaw = getCursorBlob(c, DB.ColumnChannel.IMAGEBLOB);
        if (imgRaw.length > 0)
            bm = BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length);

        ImageViewEx chIcon = (ImageViewEx)view.findViewById(R.id.image);
        chIcon.cid = cid;
        chIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onUpdateClick(iv, iv.cid);
            }
        });

        ImageViewEx ibtn = (ImageViewEx)view.findViewById(R.id.imgup);
        ibtn.cid = cid;
        ibtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onMoveUpClick(iv, iv.cid);
            }
        });

        ibtn = (ImageViewEx)view.findViewById(R.id.imgdown);
        ibtn.cid = cid;
        ibtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onMoveDownClick(iv, iv.cid);
            }
        });

        if (null == bm)
            // fail to decode.
            chIcon.setImageResource(R.drawable.ic_warn_image);
        else
            chIcon.setImageBitmap(bm);

        ImageView noti_up = (ImageView)view.findViewById(R.id.noti_update);
        ImageView noti_dn = (ImageView)view.findViewById(R.id.noti_download);

        RTTask.TaskState state = RTTask.S().getState(cid, RTTask.Action.Update);
        noti_up.setVisibility(View.VISIBLE);
        if (RTTask.TaskState.Idle == state)
            noti_up.setVisibility(View.GONE);
        else if (RTTask.TaskState.Ready == state)
            noti_up.setImageResource(R.drawable.ic_pause);
        else if (RTTask.TaskState.Running == state)
            noti_up.setImageResource(R.drawable.ic_refresh);
        else if (RTTask.TaskState.Canceling == state)
            noti_up.setImageResource(R.drawable.ic_block);
        else if (RTTask.TaskState.Failed == state)
            noti_up.setImageResource(R.drawable.ic_info);
        else
            eAssert(false);

        if (0 == RTTask.S().getItemsDownloading(cid).length)
            noti_dn.setVisibility(View.GONE);
        else
            noti_dn.setVisibility(View.VISIBLE);

        ((TextView)view.findViewById(R.id.title)).setText(title);
        ((TextView)view.findViewById(R.id.description)).setText(desc);
        ((TextView)view.findViewById(R.id.date)).setText(date);
        ((TextView)view.findViewById(R.id.age)).setText(age);
        ImageView msgImage = ((ImageView)view.findViewById(R.id.msg_img));
        if (nrNew > 0)
            msgImage.setVisibility(View.VISIBLE);
        else
            msgImage.setVisibility(View.GONE);
    }

    @Override
    protected void finalize() throws Throwable {
        UnexpectedExceptionHandler.S().unregisterModule(this);
        super.finalize();
    }
}
