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
import android.database.StaleDataException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;

public class ChannelListAdapter extends AsyncCursorAdapter implements
AsyncCursorAdapter.ItemBuilder {
    private static Date dummyDate = new Date();
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

    private static class ItemInfo {
        long        cid             = -1;
        String      title           = "";
        String      desc            = "";
        Date        lastUpdate      = dummyDate;
        long        maxItemId       = 0;
        long        oldLastItemId   = 0;
        Bitmap      bm              = null;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return super.dump(lv) + "[ ChannelListAdapter ]";
    }

    ChannelListAdapter(Context        context,
                       Cursor         cursor,
                       int            rowLayout,
                       ListView       lv,
                       final int      dataReqSz,
                       final int      maxArrSz,
                       OnAction       actionListener) {
        super(context, cursor, null, rowLayout, lv, new ItemInfo(), dataReqSz, maxArrSz);
        setItemBuilder(this);
        UnexpectedExceptionHandler.S().registerModule(this);
        onAction = actionListener;
    }

    public int
    findPosition(long cid) {
        for (int i = 0; i < getCount(); i++) {
            if (getItemInfo_cid(i) == cid)
                    return i;
        }
        return -1;
    }

    public int
    findItemId(long cid) {
        int pos = findPosition(cid);
        if (pos < 0)
            return -1;
        else
            return (int)getItemId(pos);
    }

    public void
    switchPos(int pos0, int pos1) {
        eAssert(isUiThread());
        Object sv = getItem(pos0);
        setItem(pos0, getItem(pos1));
        setItem(pos1, sv);
        notifyDataSetChanged();
    }

    public void
    setChannelIcon(long cid, Bitmap bm) {
        eAssert(isUiThread());
        ItemInfo ii = (ItemInfo)getItem(findItemId(cid));
        if (null != ii) {
            if (null != ii.bm)
                ii.bm.recycle();
            ii.bm = bm;
        }
        notifyDataSetChanged();
    }

    public long
    getItemInfo_cid(int position) {
        return ((ItemInfo)super.getItem(position)).cid;
    }

    @Override
    public Object
    buildItem(AsyncCursorAdapter adapter, Cursor c) {
        //logI("ChannelListAdapter : buildItem - START");
        ItemInfo i = new ItemInfo();
        try {
            i.cid = getCursorLong(c, DB.ColumnChannel.ID);
            i.title = getCursorString(c, DB.ColumnChannel.TITLE);
            i.desc = getCursorString(c, DB.ColumnChannel.DESCRIPTION);
            i.lastUpdate = new Date(getCursorLong(c, DB.ColumnChannel.LASTUPDATE));
            i.maxItemId = DBPolicy.S().getItemInfoMaxId(i.cid);
            i.oldLastItemId = DBPolicy.S().getChannelInfoLong(i.cid, ColumnChannel.OLDLAST_ITEMID);
            i.bm = null;
            byte[] imgRaw = getCursorBlob(c, DB.ColumnChannel.IMAGEBLOB);
            if (imgRaw.length > 0)
                i.bm = BitmapFactory.decodeByteArray(imgRaw, 0, imgRaw.length);
        } catch (StaleDataException e) {
            eAssert(false);
        }
        //logI("ChannelListAdapter : buildItem - END");
        return i;
    }

    @Override
    public int
    requestData(final AsyncAdapter adapter, Object priv, long nrseq, final int from, final int sz) {
        // Override to use "delayed item update"
        int ret;
        try {
            DBPolicy.S().getDelayedChannelUpdate();
            ret = super.requestData(adapter, priv, nrseq, from, sz);
        } finally {
            DBPolicy.S().putDelayedChannelUpdate();
        }
        return ret;
    }

    @Override
    protected void
    bindView(View v, final Context context, int position)  {
        ItemInfo ii = ((ItemInfo)getItem(position));

        long nrNew = ii.maxItemId - ii.oldLastItemId;

        ImageViewEx chIcon = (ImageViewEx)v.findViewById(R.id.image);
        chIcon.cid = ii.cid;
        chIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onUpdateClick(iv, iv.cid);
            }
        });

        ImageViewEx ibtn = (ImageViewEx)v.findViewById(R.id.imgup);
        ibtn.cid = ii.cid;
        ibtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onMoveUpClick(iv, iv.cid);
            }
        });

        ibtn = (ImageViewEx)v.findViewById(R.id.imgdown);
        ibtn.cid = ii.cid;
        ibtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == onAction)
                    return;
                ImageViewEx iv = (ImageViewEx)v;
                onAction.onMoveDownClick(iv, iv.cid);
            }
        });

        if (null == ii.bm)
            // fail to decode.
            chIcon.setImageResource(R.drawable.ic_warn_image);
        else
            chIcon.setImageBitmap(ii.bm);

        ImageView noti_up = (ImageView)v.findViewById(R.id.noti_update);
        ImageView noti_dn = (ImageView)v.findViewById(R.id.noti_download);

        RTTask.TaskState state = RTTask.S().getState(ii.cid, RTTask.Action.Update);
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

        if (0 == RTTask.S().getItemsDownloading(ii.cid).length)
            noti_dn.setVisibility(View.GONE);
        else
            noti_dn.setVisibility(View.VISIBLE);

        String date = DateFormat.getInstance().format(ii.lastUpdate);
        // === Set 'age' ===
        // calculate age and convert to readable string.
        String age;
        { // just for temporal variable scope
            long ageTime = new Date().getTime() - ii.lastUpdate.getTime();
            // Show "day:hours"
            long ageHours = ageTime/ (1000 * 60 * 60);
            long ageDay = ageHours / 24;
            ageHours %= 24;
            age = String.format("%2d:%2d", ageDay, ageHours);
        }

        ((TextView)v.findViewById(R.id.title)).setText(ii.title);
        ((TextView)v.findViewById(R.id.description)).setText(ii.desc);
        ((TextView)v.findViewById(R.id.date)).setText(date);
        ((TextView)v.findViewById(R.id.age)).setText(age);
        ImageView msgImage = ((ImageView)v.findViewById(R.id.msg_img));
        if (nrNew > 0)
            msgImage.setVisibility(View.VISIBLE);
        else
            msgImage.setVisibility(View.GONE);
    }

    @Override
    protected void
    finalize() throws Throwable {
        super.finalize();
        UnexpectedExceptionHandler.S().unregisterModule(this);
    }
}
