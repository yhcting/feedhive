/*****************************************************************************
 *    Copyright (C) 2012, 2013 Younghyung Cho. <yhcting77@gmail.com>
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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.ViewGroup;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.Utils;

public class ChannelListPagerAdapter extends FragmentPagerAdapterEx {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ChannelListPagerAdapter.class);

    private final DBPolicy          mDbp = DBPolicy.get();
    private long[]                  mCatIds;
    private ChannelListFragment[]   mFragments;

    private void
    reset(Feed.Category[] cats) {
        long[] catIds = new long[cats.length];
        for (int i = 0; i < catIds.length; i++)
            catIds[i] = cats[i].id;
        reset(catIds);
    }

    private void
    reset(long[] catIds) {
        mCatIds = catIds;
        mFragments = new ChannelListFragment[catIds.length];
        for (int i = 0; i < mFragments.length; i++)
            mFragments[i] = null;
    }

    public ChannelListPagerAdapter(FragmentManager fm,
                                   Feed.Category[] cats) {
        super(fm);
        reset(cats);
    }

    public int
    getPosition(long catId) {
        for (int i = 0; i < mCatIds.length; i++) {
            if (mCatIds[i] == catId)
                return i;
        }
        return -1;
    }

    public void
    refreshDataSet() {
        if (DBG) P.v("Enter");
        reset(mDbp.getCategories());
        super.notifyDataSetChanged();
    }

    public void
    newCategoryAdded(long newCatId) {
        if (DBG) P.v("id : " + newCatId);
        long[] newCatIds = new long[mCatIds.length + 1];
        System.arraycopy(mCatIds, 0, newCatIds, 0, mCatIds.length);
        newCatIds[mCatIds.length] = newCatId;
        ChannelListFragment[] newFragments = new ChannelListFragment[newCatIds.length];
        System.arraycopy(mFragments, 0, newFragments, 0, mFragments.length);
        newFragments[mFragments.length] = null;
        mCatIds = newCatIds;
        mFragments = newFragments;
        super.notifyDataSetChanged();
    }

    public void
    categoryDeleted(long catId) {
        if (DBG) P.v("id : " + catId);
        long[] newCatIds = new long[mCatIds.length - 1];
        ChannelListFragment[] newFragments = new ChannelListFragment[newCatIds.length];
        try {
            int j = 0;
            for (int i = 0; i < mCatIds.length; i++) {
                if (mCatIds[i] != catId) {
                    newCatIds[j] = mCatIds[i];
                    newFragments[j++] = mFragments[i];
                }
            }
            mCatIds = newCatIds;
            mFragments = newFragments;
            super.notifyDataSetChanged();
        } catch (ArrayIndexOutOfBoundsException ignored) { }
    }

    public ChannelListFragment
    getPrimaryFragment() {
        ChannelListFragment clf = (ChannelListFragment)super.getCurrentPrimaryFragment();
        return clf;
    }

    public ChannelListFragment
    getFragment(long catId) {
        String fragmentName = super.getFragmentName(getPosition(catId));
        return (ChannelListFragment)getFragmentManager().findFragmentByTag(fragmentName);
    }

    @Override
    public void
    setPrimaryItem(ViewGroup container, int position, Object object) {
        ChannelListFragment oldf = getPrimaryFragment();
        super.setPrimaryItem(container, position, object);
        ChannelListFragment newf = getPrimaryFragment();
        if (oldf != newf) {
            if (null != oldf)
                oldf.setToPrimary(false);
            if (null != newf)
                newf.setToPrimary(true);
        }
    }

    @Override
    public int
    getCount() {
        return mCatIds.length;
    }

    @Override
    public Fragment
    getItem(int position) {
        if (DBG) P.v("pos : " + position);
        return ChannelListFragment.newInstance(mCatIds[position]);

    }

    @Override
    public long
    getItemId(int position) {
        if (DBG) P.v("pos : " + position + "/" + mCatIds[position]);
        return mCatIds[position];
    }

    @Override
    public void
    startUpdate(ViewGroup container) {
        //if (DBG) P.v("startUpdate");
        super.startUpdate(container);
    }

    @Override
    public void
    finishUpdate(ViewGroup container) {
        //if (DBG) P.v("finishUpdate");
        super.finishUpdate(container);
    }

    @Override
    public Object
    instantiateItem(ViewGroup container, int position) {
        if (DBG) P.v("pos : " + position);
        return super.instantiateItem(container, position);
    }

    @Override
    public void
    destroyItem(ViewGroup container, int position, Object object) {
        if (DBG) P.v("pos : " + position);
        super.destroyItem(container, position, object);
    }

    @Override
    protected void
    onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void
    onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
    }
}
