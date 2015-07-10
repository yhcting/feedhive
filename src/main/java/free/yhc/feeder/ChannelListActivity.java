/******************************************************************************
 * Copyright (C) 2012, 2013, 2014, 2015
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

import static free.yhc.feeder.core.Utils.eAssert;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import free.yhc.feeder.UiHelper.EditTextDialogAction;
import free.yhc.feeder.UiHelper.OnConfirmDialogAction;
import free.yhc.feeder.db.DB;
import free.yhc.feeder.db.DBPolicy;
import free.yhc.feeder.core.Environ;
import free.yhc.feeder.core.Feed;
import free.yhc.feeder.core.ListenerManager;
import free.yhc.feeder.core.RTTask;
import free.yhc.feeder.core.UnexpectedExceptionHandler;
import free.yhc.feeder.core.UsageReport;
import free.yhc.feeder.core.Utils;

public class ChannelListActivity extends FragmentActivity implements
ActionBar.TabListener,
UnexpectedExceptionHandler.TrackedModule {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(ChannelListActivity.class);

    // Request codes.
    private static final int REQC_PICK_PREDEFINED_CHANNEL = 1;

    // Saved instance
    private static final String KEY_CURRENT_CATEGORY = "current_category";

    private final DBPolicy mDbp = DBPolicy.get();
    @SuppressWarnings("unused")
    private final RTTask mRtt = RTTask.get();
    private final UsageReport mUr = UsageReport.get();

    private ActionBar mAb = null;
    private ViewPager mPager = null;
    private ChannelListFragment mContextMenuOwner = null;
    private DBWatcher mDbWatcher = null;

    private final ViewPager.OnPageChangeListener mPCListener = new OnPageViewChange();

    private static class TabTag {
        long categoryid;
    }

    private static class DBWatcher implements ListenerManager.Listener {
        // NOTE
        // Comparing with "ChannelListFragment" and "ItemListActivity", initial value of
        //   _mCategoryTableUpdated is 'false'
        // Why?
        // Please see 'onResume'.
        // Even if this looses consistency... but... fair enough.
        private boolean _mCategoryTableUpdated = false;

        void
        register() {
            DBPolicy.get().registerUpdatedListener(this, DB.UpdateType.CATEGORY_TABLE.flag());
        }

        void
        unregister() {
            DBPolicy.get().unregisterUpdatedListener(this);
        }

        void
        reset() {
            _mCategoryTableUpdated = false;
        }

        boolean
        isCategoryTableUpdated() {
            return _mCategoryTableUpdated;
        }

        @Override
        public void
        onNotify(Object user, ListenerManager.Type type, Object arg0, Object arg1) {
            if (DB.UpdateType.CATEGORY_TABLE == type)
                _mCategoryTableUpdated = true;
            else
                eAssert(false);
        }
    }

    private class OnPageViewChange implements ViewPager.OnPageChangeListener {
        @Override
        public void
        onPageSelected(int arg0) {
            if (DBG) P.v("arg : " + arg0);
            mAb.setSelectedNavigationItem(arg0);
        }

        @Override
        public void
        onPageScrolled(int arg0, float arg1, int arg2) { }

        @Override
        public void
        onPageScrollStateChanged(int arg0) {
            if (DBG) P.v("arg : " + arg0);
            // Fragment is changed. So, context menu is no more valid.
            ChannelListActivity.this.closeContextMenu();
        }
    }

    private void
    restartThisActivity() {
        // category table is changed outside of this activity.
        // restarting is required!!
        Intent intent = new Intent(this, ChannelListActivity.class);
        startActivity(intent);
        finish();
    }

    private TabTag
    getTag(Tab tab) {
        return (TabTag)tab.getTag();
    }

    private ChannelListPagerAdapter
    getPagerAdapter() {
        if (null != mPager)
            return (ChannelListPagerAdapter)mPager.getAdapter();
        return null;
    }

    private long
    getCategoryId(Tab tab) {
        return getTag(tab).categoryid;
    }

    private long
    getCurrentCategoryId() {
        if (null != getPagerAdapter())
            return getPagerAdapter().getPrimaryFragment().getCategoryId();
        else
            return mDbp.getDefaultCategoryId();
    }

    private int
    getTabPosition(long categoryid) {
        for (int i = 0; i < mAb.getTabCount(); i++) {
            if (categoryid == getCategoryId(mAb.getTabAt(i)))
                return i;
        }
        return -1;
    }

    private void
    selectDefaultAsSelected() {
        // 0 is index of default tab
        mAb.setSelectedNavigationItem(0);
    }

    private void
    setAsCurrentCategory(long categoryid) {
        if (null == getPagerAdapter())
            return; // do nothing.
        mPager.setCurrentItem(getPagerAdapter().getPosition(categoryid));
        mAb.setSelectedNavigationItem(getTabPosition(categoryid));
    }

    private Tab
    addCategory(Feed.Category cat) {
        String text;
        if (mDbp.isDefaultCategoryId(cat.id)
           && !Utils.isValidValue(mDbp.getCategoryName(cat.id)))
            text = getResources().getText(R.string.default_category_name).toString();
        else
            text = cat.name;

        // Add new tab to action bar
        Tab tab = mAb.newTab()
                    .setText(text)
                    .setTag(cat.id)
                    .setTabListener(this);

        TabTag tag = new TabTag();
        tag.categoryid = cat.id;

        tab.setTag(tag);
        mAb.addTab(tab, false);
        if (null != getPagerAdapter())
            getPagerAdapter().newCategoryAdded(cat.id);
        return tab;
    }

    /**
     * All channels belonging to this category will be moved to default category.
     */
    private void
    deleteCategory(long categoryid) {
        mDbp.deleteCategory(categoryid);

        // NOTE
        // PagerView's working mechanism is NOT GOOD for deleting item in the middle.
        // So, just restart channel list activity...to reload all!!!
        restartThisActivity();
        /*
        Tab curTab = mAb.getSelectedTab();
        mAb.removeTab(curTab);
        if (null != getPagerAdapter())
            getPagerAdapter().categoryDeleted(categoryid);
        selectDefaultAsSelected();
        */
    }

    @SuppressWarnings("unused")
    private String
    getTabText(Tab tab) {
        return tab.getText().toString();
    }

    /**
     * Add channel to current selected category.
     * List will be scrolled to newly added channel.
     */
    private void
    addChannel(String url, String iconurl) {
        if (null != getPagerAdapter())
            getPagerAdapter().getPrimaryFragment().addChannel(url, iconurl);
    }


    private void
    onOpt_addChannel_youtubeEditDiag(final MenuItem item) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {}
            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                switch (item.getItemId()) {
                case R.id.uploader:
                    addChannel(Utils.buildYoutubeFeedUrl_uploader(edit.getText().toString()), null);
                    break;
                case R.id.search:
                    addChannel(Utils.buildYoutubeFeedUrl_search(edit.getText().toString()), null);
                    break;
                default:
                    eAssert(false);
                }
            }
        };
        UiHelper.buildOneLineEditTextDialog(this, item.getTitle(), action).show();
    }

    private void
    onOpt_addChannel_youtube(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_addchannel_youtube, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                onOpt_addChannel_youtubeEditDiag(item);
                return true;
            }
        });
        popup.show();
    }


    private void
    onOpt_addChannel_url(@SuppressWarnings("unused") final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {
                // start edit box with 'http://'
                final String prefix = "http://";
                edit.setText(prefix);
                edit.setSelection(prefix.length());
            }

            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                String url = edit.getText().toString();
                if (!url.matches("http\\Q://\\E\\s*")) {
                    addChannel(url, null);
                    mUr.storeUsageReport("URL : " + url + "\n");
                }
            }
        };
        UiHelper.buildOneLineEditTextDialog(this, R.string.channel_url, action).show();
    }

    private void
    onOpt_addChannel_predefined(@SuppressWarnings("unused") View anchor) {
        Intent intent = new Intent(this, PredefinedChannelActivity.class);
        startActivityForResult(intent, REQC_PICK_PREDEFINED_CHANNEL);
    }

    private void
    onOpt_addChannel(final View anchor) {
        if (0 == mAb.getNavigationItemCount()) {
            eAssert(false);
            return;
        }

        if (0 > mAb.getSelectedNavigationIndex()) {
            UiHelper.showTextToast(ChannelListActivity.this, R.string.warn_select_category_to_add);
            return;
        }

        if (!Utils.isNetworkAvailable()) {
            // TODO Handling error
            UiHelper.showTextToast(ChannelListActivity.this, R.string.warn_network_unavailable);
            return;
        }

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_addchannel, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.predefined:
                    onOpt_addChannel_predefined(anchor);
                    break;
                case R.id.url:
                    onOpt_addChannel_url(anchor);
                    break;
                case R.id.youtube:
                    onOpt_addChannel_youtube(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_category_add(@SuppressWarnings("unused") final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (mDbp.isDuplicatedCategoryName(name)) {
                    UiHelper.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    Feed.Category cat = new Feed.Category(name);
                    if (0 > mDbp.insertCategory(cat))
                        UiHelper.showTextToast(ChannelListActivity.this, R.string.warn_add_category);
                    else {
                        eAssert(cat.id >= 0);
                        addCategory(cat);
                    }
                }
            }
        };
        UiHelper.buildOneLineEditTextDialog(this, R.string.add_category, action).show();
    }

    private void
    onOpt_category_rename(@SuppressWarnings("unused") final View anchor) {
        // Set action for dialog.
        final EditTextDialogAction action = new EditTextDialogAction() {
            @Override
            public void
            prepare(Dialog dialog, EditText edit) {
                edit.setHint(R.string.enter_name);
            }
            @Override
            public void
            onOk(Dialog dialog, EditText edit) {
                String name = edit.getText().toString();
                if (mDbp.isDuplicatedCategoryName(name)) {
                    UiHelper.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                } else {
                    mAb.getSelectedTab().setText(name);
                    mDbp.updateCategory(getCurrentCategoryId(), name);
                }
            }
        };
        UiHelper.buildOneLineEditTextDialog(this, R.string.rename_category, action).show();
    }

    private void
    onOpt_category_delete(@SuppressWarnings("unused") final View anchor) {
        final long categoryid = getCurrentCategoryId();
        if (DBG) P.v("category(" + categoryid + ")");
        if (mDbp.isDefaultCategoryId(categoryid)) {
            UiHelper.showTextToast(this, R.string.warn_delete_default_category);
            return;
        }

        // 0 should be default category index!
        eAssert(mAb.getSelectedNavigationIndex() > 0);

        OnConfirmDialogAction action = new OnConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                deleteCategory(categoryid);
            }
            @Override
            public void onCancel(Dialog dialog) { }
        };
        UiHelper.buildConfirmDialog(this, R.string.delete_category, R.string.delete_category_msg, action).show();
    }

    private void
    onOpt_category(final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_category, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.add:
                    onOpt_category_add(anchor);
                    break;
                case R.id.rename:
                    onOpt_category_rename(anchor);
                    break;
                case R.id.delete:
                    onOpt_category_delete(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_itemsAll(@SuppressWarnings("unused") final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_ALL);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        startActivity(intent);
    }

    private void
    onOpt_updateAll(@SuppressWarnings("unused") final View anchor) {
        UiHelper.OnConfirmDialogAction action = new UiHelper.OnConfirmDialogAction() {
            @Override
            public void
            onOk(Dialog dialog) {
                ScheduledUpdateService.scheduleImmediateUpdate(mDbp.getChannelIds());
            }

            @Override
            public void
            onCancel(Dialog dialog) {
            }
        };

        UiHelper.buildConfirmDialog(this,
                                    R.string.update_all_channels,
                                    R.string.update_all_channels_msg,
                                    action)
                .show();
    }

    private void
    onOpt_itemsCategory(@SuppressWarnings("unused") final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_CATEGORY);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        intent.putExtra("categoryid", getCurrentCategoryId());
        startActivity(intent);
    }

    private void
    onOpt_itemsFavorite(@SuppressWarnings("unused") final View anchor) {
        Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
        intent.putExtra(ItemListActivity.IKEY_MODE, ItemListActivity.MODE_FAVORITE);
        intent.putExtra(ItemListActivity.IKEY_FILTER, ItemListActivity.FILTER_NONE);
        startActivity(intent);
    }

    private void
    onOpt_moreMenu_about(@SuppressWarnings("unused") final View anchor) {
        PackageManager pm = getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(getPackageName(), 0);
        }catch (NameNotFoundException ignored) { }

        if (null == pi)
            return; // never happen

        CharSequence title = getResources().getText(R.string.about_app);
        //noinspection StringBufferReplaceableByString
        StringBuilder strbldr = new StringBuilder();
        strbldr.append(getResources().getText(R.string.version)).append(" : ").append(pi.versionName).append("\n")
               .append(getResources().getText(R.string.about_app_email)).append("\n")
               .append(getResources().getText(R.string.about_app_blog)).append("\n")
               .append(getResources().getText(R.string.about_app_page)).append("\n");
        AlertDialog diag = UiHelper.createAlertDialog(this, 0, title, strbldr.toString());
        diag.show();
    }

    private void
    onOpt_moreMenu_license(@SuppressWarnings("unused") final View anchor) {
        View v = UiHelper.inflateLayout(this, R.layout.info_dialog);
        TextView tv = ((TextView)v.findViewById(R.id.text));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setText(R.string.license_desc);
        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setView(v);
        AlertDialog aDiag = bldr.create();
        aDiag.show();
    }

    private void
    onOpt_moreMenu_deleteAllDnFiles(@SuppressWarnings("unused") final View anchor) {
        AlertDialog diag = UiHelper.buildDeleteAllDnFilesConfirmDialog(this, null, null);
        if (null == diag)
            UiHelper.showTextToast(this, R.string.del_dnfiles_not_allowed_msg);
        else
            diag.show();
    }

    private void
    onOpt_moreMenu_deleteAllUsedDnFiles(@SuppressWarnings("unused") final View anchor) {
        AlertDialog diag = UiHelper.buildDeleteUsedDnFilesConfirmDialog(0, this, null, null);
        if (null == diag)
            UiHelper.showTextToast(this, R.string.del_dnfiles_not_allowed_msg);
        else
            diag.show();
    }

    private void
    onOpt_moreMenu_feedbackOpinion(@SuppressWarnings("unused") final View anchor) {
        if (!Utils.isNetworkAvailable()) {
            UiHelper.showTextToast(this, R.string.warn_network_unavailable);
            return;
        }

        if (!mUr.sendFeedbackReportMain(this))
            UiHelper.showTextToast(this, R.string.warn_find_email_app);
    }

    private void
    onOpt_moreMenu_dbManagement(@SuppressWarnings("unused") final View anchor) {
        Intent intent = new Intent(this, DBManagerActivity.class);
        startActivity(intent);
    }

    private void
    onOpt_moreMenu(@SuppressWarnings("unused") final View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_more_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean
            onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                case R.id.about:
                    onOpt_moreMenu_about(anchor);
                    break;
                case R.id.license:
                    onOpt_moreMenu_license(anchor);
                    break;
                case R.id.media_delete_all:
                    onOpt_moreMenu_deleteAllDnFiles(anchor);
                    break;
                case R.id.media_delete_used_all:
                    onOpt_moreMenu_deleteAllUsedDnFiles(anchor);
                    break;
                case R.id.feedback_opinion:
                    onOpt_moreMenu_feedbackOpinion(anchor);
                    break;
                case R.id.db_management:
                    onOpt_moreMenu_dbManagement(anchor);
                    break;
                default:
                    eAssert(false);
                }
                return true;
            }
        });
        popup.show();
    }

    private void
    onOpt_setting(@SuppressWarnings("unused") final View anchor) {
        Intent intent = new Intent(this, FeederPreferenceActivity.class);
        startActivity(intent);
    }

    private void
    onResult_pickPredefinedChannel(int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;

        final String[] urls = data.getStringArrayExtra(PredefinedChannelActivity.KEY_URLS);
        final String[] iconurls = data.getStringArrayExtra(PredefinedChannelActivity.KEY_ICONURLS);
        for (int i = 0; i < urls.length; i++) {
            eAssert(Utils.isValidValue(urls[i]));
            final String url = urls[i];
            final String iconurl = iconurls[i];
            // NOTE
            // Without using 'post', user may feel bad ui response.
            Environ.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    addChannel(url, iconurl);
                }
            });
        }
    }

    private void
    setupToolButtons() {
        findViewById(R.id.btn_items_favorite).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_itemsFavorite(v);
            }
        });

        findViewById(R.id.btn_items_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_itemsCategory(v);
            }
        });

        findViewById(R.id.btn_items_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_itemsAll(v);
            }
        });

        findViewById(R.id.btn_update_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_updateAll(v);
            }
        });

        findViewById(R.id.btn_add_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_addChannel(v);
            }
        });

        findViewById(R.id.btn_category).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_category(v);
            }
        });

        findViewById(R.id.btn_more_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_moreMenu(v);
            }
        });

        findViewById(R.id.btn_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                onOpt_setting(v);
            }
        });
    }

    public boolean
    isContextMenuOwner(ChannelListFragment fragment) {
        return fragment == mContextMenuOwner;
    }

    public void
    categoryDataSetChanged(long catId) {
        if (null == getPagerAdapter())
            return;
        ChannelListFragment fragmentTo = getPagerAdapter().getFragment(catId);
        if (null != fragmentTo)
            fragmentTo.refreshListAsync();
    }

    @Override
    public void
    onTabSelected(Tab tab, FragmentTransaction ft) {
        onTabReselected(tab, ft);
    }

    @Override
    public void
    onTabUnselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void
    onTabReselected(Tab tab, FragmentTransaction ft) {
        if (null == getPagerAdapter())
            return;
        mPager.setCurrentItem(getPagerAdapter().getPosition(getTag(tab).categoryid));
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case REQC_PICK_PREDEFINED_CHANNEL:
            onResult_pickPredefinedChannel(resultCode, data);
            break;
        }
    }

    @Override
    public String
    dump(UnexpectedExceptionHandler.DumpLevel lv) {
        return "[ ChannelListActivity ]";
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (null != getPagerAdapter()) {
            mContextMenuOwner = getPagerAdapter().getPrimaryFragment();
            mContextMenuOwner.onCreateContextMenu2(menu, v, menuInfo);
        }
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        UnexpectedExceptionHandler.get().registerModule(this);
        super.onCreate(savedInstanceState);

        //logI("==> ChannelListActivity : onCreate");

        // TODO
        // Is this best place to put this line of code (sendReportMail())???
        // More consideration is required.

        // Send error report if exists.
        mUr.sendErrReportMail(this);
        // Send usage report if exists and time is passed enough.
        mUr.sendUsageReportMail(this);


        setContentView(R.layout.channel_list);

        setupToolButtons();

        // Setup Tabs
        mAb = getActionBar();
        eAssert(null != mAb);
        mAb.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mAb.setDisplayShowTitleEnabled(false);
        mAb.setDisplayShowHomeEnabled(false);


        Feed.Category[] cats;
        cats = mDbp.getCategories();
        eAssert(cats.length > 0);

        for (Feed.Category cat : cats)
            addCategory(cat);

        mPager = (ViewPager)findViewById(R.id.pager);
        ChannelListPagerAdapter adapter = new ChannelListPagerAdapter(getSupportFragmentManager(),
                                                                      cats);
        mPager.setAdapter(adapter);

        long categoryid = -1;
        if (null != savedInstanceState)
            categoryid = savedInstanceState.getLong(KEY_CURRENT_CATEGORY, -1);

        if (0 < categoryid)
            setAsCurrentCategory(categoryid);
        else
            selectDefaultAsSelected();

        mPager.setOnPageChangeListener(mPCListener);
        mDbWatcher = new DBWatcher();
    }

    @Override
    protected void
    onSaveInstanceState(Bundle outState) {
        // ignore all unexpected operation.
        try {
            outState.putLong(KEY_CURRENT_CATEGORY, getCurrentCategoryId());
        } catch (RuntimeException ignored) { }
    }

    @Override
    protected void
    onStart() {
        super.onStart();
    }

    @Override
    protected void
    onResume() {
        super.onResume();

        mDbWatcher.unregister();
        boolean needReload = mDbWatcher.isCategoryTableUpdated();
        mDbWatcher.reset();
        if (needReload) {
            // category table is changed outside of this activity.
            // restarting is required!!
            restartThisActivity();
            return;
        }

        if (null == mAb) {
            if (DBG) P.w("mAb(action bar) is NULL");
            mAb = getActionBar();
        }

    }

   @Override
    public void
    onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

    }

    @Override
    protected void
    onPause() {
        mDbWatcher.register();
        super.onPause();
    }

    @Override
    protected void
    onStop() {
        super.onStop();
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
        mDbWatcher.unregister();
        UnexpectedExceptionHandler.get().unregisterModule(this);
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }
}
