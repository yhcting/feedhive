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
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DB.ColumnChannel;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.RTTask;
import free.yhc.feeder.model.UnexpectedExceptionHandler;
import free.yhc.feeder.model.Utils;

public class ChannelListAdapter extends AsyncCursorAdapter implements
AsyncCursorAdapter.ItemBuilder {
    private static final Date sDummyDate = new Date();

    private final DBPolicy mDbp = DBPolicy.get();
    private final RTTask   mRtt = RTTask.get();

    private final OnActionListener     mActionListener;
    private final View.OnClickListener mChIconOnClick;
    private final View.OnClickListener mPosUpOnClick;
    private final View.OnClickListener mPosDnOnClick;

    interface OnActionListener {
        void onUpdateClick(ImageView ibtn, long cid);
        void onMoveUpClick(ImageView ibtn, long cid);
        void onMoveDownClick(ImageView ibtn, long cid);
    }

    private static class ItemInfo {
        long        cid             = -1;
        String      title           = "";
        String      desc            = "";
        Date        lastUpdate      = sDummyDate;
        long        maxItemId       = 0;
        long        oldLastItemId   = 0;
        Bitmap      bm              = null;
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return super.dump(lv) + "[ ChannelListAdapter ]";
    }

    ChannelListAdapter(Context          context,
                       Cursor           cursor,
                       int              rowLayout,
                       ListView         lv,
                       final int        dataReqSz,
                       final int        maxArrSz,
                       OnActionListener listener) {
        super(context, cursor, null, rowLayout, lv, new ItemInfo(), dataReqSz, maxArrSz);
        setItemBuilder(this);
        UnexpectedExceptionHandler.get().registerModule(this);
        mActionListener = listener;

        mChIconOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == mActionListener)
                    return;
                mActionListener.onUpdateClick((ImageView)v, (long)(Long)v.getTag());
            }
        };

        mPosUpOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == mActionListener)
                    return;
                mActionListener.onMoveUpClick((ImageView)v, (long)(Long)v.getTag());
            }
        };

        mPosDnOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == mActionListener)
                    return;
                mActionListener.onMoveDownClick((ImageView)v, (long)(Long)v.getTag());
            }
        };
    }

    public int
    findPosition(long cid) {
        eAssert(Utils.isUiThread());
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

    /**
     * Data is NOT reloaded.
     * Only item array is changed.
     * @param pos0
     * @param pos1
     */
    public void
    switchPos(int pos0, int pos1) {
        eAssert(Utils.isUiThread());
        Object sv = setItem(pos0, getItem(pos1));
        eAssert(null != sv);
        setItem(pos1, sv);
        notifyDataSetChanged();
    }

    /**
     * Data is NOT reloaded.
     * Only item array is changed.
     * @param pos0
     * @param pos1
     */
    public void
    setChannelIcon(long cid, Bitmap bm) {
        eAssert(Utils.isUiThread());
        ItemInfo ii = (ItemInfo)getItem(findPosition(cid));
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
            i.maxItemId = mDbp.getItemInfoMaxId(i.cid);
            i.oldLastItemId = mDbp.getChannelInfoLong(i.cid, ColumnChannel.OLDLAST_ITEMID);
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
    public void
    destroyItem(AsyncCursorAdapter adapter, Object item) {
        ItemInfo ii = (ItemInfo)item;
        // NOTE
        // bm will be destroyed by GC
        // But, we cannot control when GC is triggered.
        // Actually, sometimes I experienced OutOfMeory error.
        // To avoid OOM, bm.recycle() is needed to be called in manual whenever item is no-longer used.
        if (null != ii.bm)
            ii.bm.recycle();
    }

    @Override
    public int
    requestData(final AsyncAdapter adapter, Object priv, long nrseq, final int from, final int sz) {
        // Override to use "delayed item update"
        int ret;
        try {
            mDbp.getDelayedChannelUpdate();
            ret = super.requestData(adapter, priv, nrseq, from, sz);
        } finally {
            mDbp.putDelayedChannelUpdate();
        }
        return ret;
    }

    @Override
    protected void
    bindView(View v, final Context context, int position)  {
        ItemInfo ii = ((ItemInfo)getItem(position));

        long nrNew = ii.maxItemId - ii.oldLastItemId;

        ImageView chIcon = (ImageView)v.findViewById(R.id.image);
        chIcon.setTag(ii.cid);
        chIcon.setOnClickListener(mChIconOnClick);

        ImageView ibtn = (ImageView)v.findViewById(R.id.imgup);
        ibtn.setTag(ii.cid);
        ibtn.setOnClickListener(mPosUpOnClick);

        ibtn = (ImageView)v.findViewById(R.id.imgdown);
        ibtn.setTag(ii.cid);
        ibtn.setOnClickListener(mPosDnOnClick);

        if (null == ii.bm)
            // fail to decode.
            chIcon.setImageResource(R.drawable.ic_warn_image);
        else
            chIcon.setImageBitmap(ii.bm);

        ImageView noti_up = (ImageView)v.findViewById(R.id.noti_update);
        ImageView noti_dn = (ImageView)v.findViewById(R.id.noti_download);

        RTTask.TaskState state = mRtt.getState(ii.cid, RTTask.Action.UPDATE);
        noti_up.setVisibility(View.VISIBLE);
        switch(state) {
        case IDLE:
            noti_up.setVisibility(View.GONE);
            break;

        case READY:
            noti_up.setImageResource(R.drawable.ic_pause);
            break;

        case RUNNING:
            noti_up.setImageResource(R.drawable.ic_refresh);
            break;

        case CANCELING:
            noti_up.setImageResource(R.drawable.ic_block);
            break;

        case FAILED:
            noti_up.setImageResource(R.drawable.ic_info);
            break;

        default:
            eAssert(false);
        }

        if (0 == mRtt.getItemsDownloading(ii.cid).length)
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
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }
}
