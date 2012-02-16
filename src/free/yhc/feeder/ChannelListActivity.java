package free.yhc.feeder;

import static free.yhc.feeder.model.Utils.eAssert;
import static free.yhc.feeder.model.Utils.logI;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import free.yhc.feeder.model.DB;
import free.yhc.feeder.model.DBPolicy;
import free.yhc.feeder.model.Err;
import free.yhc.feeder.model.Feed;
import free.yhc.feeder.model.FeederException;
import free.yhc.feeder.model.Utils;

public class ChannelListActivity extends Activity implements ActionBar.TabListener {
    // Request codes.
    private static final int ReqCReadChannel        = 0;
    private static final int ReqCPredefinedChannel  = 1;
    private static final int ReqCPickImage          = 2;

    public static final int ResCReadChannelOk       = 0; // nothing special

    private ListView  list = null;
    private ActionBar ab;

    // Saved cid for Async execution.
    private long      cid_pickImage = -1;

    class PickIconEventHandler implements SpinAsyncTask.OnEvent {
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            Intent data = (Intent)objs[0];
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor c = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            if (!c.moveToFirst()) {
                c.close();
                return Err.MediaGet;
            }

            int columnIndex = c.getColumnIndex(filePathColumn[0]);
            String filePath = c.getString(columnIndex);
            c.close();

            // Make url string from file path
            byte[] imageData = null;
            try {
                imageData = Utils.getDecodedImageData("file://" + filePath);
            } catch (FeederException e) {
                return e.getError();
            }

            if (null == imageData) {
                return Err.CodecDecode;
            }

            if (cid_pickImage < 0) {
                eAssert(false);
                return Err.Unknown; // something evil!!!
            } else {
                try {
                    DBPolicy.get().updateChannel_image(cid_pickImage, imageData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return Err.DBUnknown;
                }
                cid_pickImage = -1;
            }
            return Err.NoErr;
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
            if (Err.NoErr == result)
                //NOTE
                //  "getListAdapter().notifyDataSetChanged();" doesn't works here... why??
                refreshList(ab.getSelectedTab());
            else
                LookAndFeel.showTextToast(ChannelListActivity.this, result.getMsgId());
        }
    }

    class AddChannelEventHandler implements SpinAsyncTask.OnEvent {
        @Override
        public Err
        onDoWork(SpinAsyncTask task, Object... objs) {
            try {
                return task.initialLoad(null, objs);
            } catch (InterruptedException e) {
                return Err.Interrupted;
            }
        }

        @Override
        public void
        onPostExecute(SpinAsyncTask task, Err result) {
            if (Err.NoErr == result)
                refreshList(ab.getSelectedTab());
            else
                LookAndFeel.showTextToast(ChannelListActivity.this, result.getMsgId());
        }
    }

    private ChannelListAdapter
    getListAdapter() {
        return (ChannelListAdapter) list.getAdapter();
    }

    private long
    getCategoryId(Tab tab) {
        return ((Long)tab.getTag()).longValue();
    }

    private long
    getCurrentCategoryId() {
        return getCategoryId(ab.getSelectedTab());
    }

    private Cursor
    adapterCursorQuery(long categoryid) {
        try {
            return DBPolicy.get().queryChannel(categoryid, new DB.ColumnChannel[] {
                    DB.ColumnChannel.ID, // Mandatory.
                    DB.ColumnChannel.TITLE,
                    DB.ColumnChannel.DESCRIPTION,
                    DB.ColumnChannel.LASTUPDATE,
                    DB.ColumnChannel.ORDER,
                    DB.ColumnChannel.IMAGEBLOB });
        } catch (InterruptedException e) {
            finish();
        }
        return null;
    }

    private boolean
    changeCategory(long cid, long from, long to) {
        if (from == to) // nothing to do
            return true;
        try {
            DBPolicy.get().updateChannel_category(cid, to);
        } catch (InterruptedException e) {
            finish();
            return false;
        }
        return true;
    }

    private void
    refreshList(Tab tab) {
        // NOTE
        // Usually, number of channels are not big.
        // So, we don't need to think about async. loading.
        Cursor newCursor = adapterCursorQuery((Long)tab.getTag());
        getListAdapter().changeCursor(newCursor);
        getListAdapter().notifyDataSetChanged();
    }

    private void
    addCategoryTab(Feed.Category cat) {
        String text;
        if (DBPolicy.get().isDefaultCategoryId(cat.id))
            text = getResources().getText(R.string.default_category_name).toString();
        else
            text = cat.name;
        ab.addTab(ab.newTab()
                .setCustomView(createTabView(text))
                .setTag(cat.id)
                .setTabListener(this)
              , false);
    }

    private TextView
    getTabTextView(Tab tab) {
        return (TextView)((LinearLayout)tab.getCustomView()).findViewById(R.id.text);
    }

    private String
    getTabText(Tab tab) {
        return getTabTextView(tab).getText().toString();
    }

    private void
    addChannel(String url) {
        eAssert(url != null);
        new SpinAsyncTask(this, new AddChannelEventHandler(), R.string.load_progress)
                .execute(getCurrentCategoryId(), url);
    }

    private void
    deleteChannel(Tab tab, long cid) {
        eAssert(null != tab);
        try {
            DBPolicy.get().deleteChannel(cid);
        } catch (InterruptedException e) {
            e.printStackTrace();
            finish();
        }
        refreshList(tab);
    }

    private void
    onOpt_addChannel() {
        if (0 == ab.getNavigationItemCount()) {
            eAssert(false);
            return;
        }

        if (0 > ab.getSelectedNavigationIndex()) {
            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_select_category_to_add);
            return;
        }

        if (!Utils.isNetworkAvailable(this)) {
            // TODO Handling error
            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_network_unavailable);
            return;
        }

        // Create "Enter Url" dialog
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.oneline_editbox_dialog, (ViewGroup) findViewById(R.id.root));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);

        final AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.channel_url);
        // Set action for dialog.
        EditText edit = (EditText) layout.findViewById(R.id.editbox);
        edit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((KeyEvent.ACTION_DOWN == event.getAction()) && (KeyEvent.KEYCODE_ENTER == keyCode)) {
                    String url = ((EditText) v).getText().toString();
                    if (url.isEmpty()) {
                        dialog.dismiss();
                        return true;
                    }
                    // Perform action on key press
                    // Toast.makeText(this, "hahah", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    addChannel(url);
                    // url = "http://old.ddanzi.com/appstream/ddradio.xml";
                    // url = "file:///data/test/total_news.xml";
                    // url = "http://www.khan.co.kr/rss/rssdata/total_news.xml";
                    // http://cast.vop.co.kr/kfline.xml
                    // addChannel("http://old.ddanzi.com/appstream/ddradio.xml");
                    // // out-of spec.
                    // addChannel("http://cast.vop.co.kr/kfline.xml"); // good
                    // addChannel("http://cast.vop.co.kr/heenews.xml"); // good
                    // addChannel("http://www.khan.co.kr/rss/rssdata/total_news.xml");
                    // // large xml
                    // addChannel("http://cbspodcast.com/podcast/sisa/sisa.xml");
                    // // large xml
                    // addChannel("file:///sdcard/tmp/heenews.xml");
                    // addChannel("file:///sdcard/tmp/total_news.xml");
                    return true;
                }
                return false;
            }
        });
        dialog.show();
    }

    private View
    createTabView(String text) {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout ll = (LinearLayout)inflater.inflate(R.layout.channel_list_tab, null);
        ((TextView)ll.findViewById(R.id.text)).setText(text);
        return ll;
    }

    private void
    onOpt_addCategory() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.oneline_editbox_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        final AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.channel_url);
        // Set action for dialog.
        EditText edit = (EditText)layout.findViewById(R.id.editbox);
        edit.setHint(R.string.enter_url);
        edit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((KeyEvent.ACTION_DOWN == event.getAction()) && (KeyEvent.KEYCODE_ENTER == keyCode)) {
                    String name = ((EditText) v).getText().toString();
                    if (name.isEmpty()) {
                        dialog.dismiss();
                        return true;
                    }

                    try {
                        if (DBPolicy.get().isDuplicatedCategoryName(name)) {
                            LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_duplicated_category);
                        } else {
                            // TODO -- add to DB!!
                            Feed.Category cat = new Feed.Category(name);
                            if (0 > DBPolicy.get().insertCategory(cat))
                                LookAndFeel.showTextToast(ChannelListActivity.this, R.string.warn_add_category);
                            else {
                                eAssert(cat.id >= 0);
                                addCategoryTab(cat);
                            }
                        }
                    } catch (InterruptedException e) {
                        eAssert(false);
                    }
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });
        dialog.show();
    }

    private void
    onOpt_deleteCategory() {
        long categoryid = getCategoryId(ab.getSelectedTab());
        if (DBPolicy.get().isDefaultCategoryId(categoryid)) {
            LookAndFeel.showTextToast(this, R.string.warn_delete_default_category);
            return;
        }

        // 0 should be default category index!
        eAssert(ab.getSelectedNavigationIndex() > 0);

        try {
            long[] cids = DBPolicy.get().getChannelIds(categoryid);
            for (long cid : cids)
                DBPolicy.get().updateChannel_categoryToDefault(cid);

            DBPolicy.get().deleteCategory(categoryid);
        } catch (InterruptedException e) {
            finish();
            return;
        }
        ab.removeTab(ab.getSelectedTab());
        ab.setSelectedNavigationItem(0);
    }

    private void
    onOpt_selectPredefinedChannel() {
        Intent intent = new Intent(this, PredefinedChannelActivity.class);
        startActivityForResult(intent, ReqCPredefinedChannel);
    }

    private void
    onContext_deleteChannel(final long cid) {
        // Create "Enter Url" dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.confirm_delete_channel);
        dialog.setButton(getResources().getText(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteChannel(ab.getSelectedTab(), cid);
                dialog.dismiss();
            }
        });
        dialog.setButton2(getResources().getText(R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void
    onContext_changeCategory(final long cid) {
        final LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.select_list_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout);
        final AlertDialog dialog = builder.create();

        // Create Adapter for list and set it.
        final ListView list = (ListView)layout.findViewById(R.id.list);
        Tab[] tabs = new Tab[ab.getTabCount()];
        for (int i = 0; i < ab.getTabCount(); i++)
            tabs[i] = ab.getTabAt(i);
        list.setAdapter(new ArrayAdapter<Tab>(this, R.id.text, tabs) {
            @Override
            public View
            getView(int position, View convertView, ViewGroup parent) {
                View row;

                if (null == convertView)
                    row = inflater.inflate(R.layout.change_category_row, null);
                else
                    row = convertView;

                TextView tv = (TextView)row.findViewById(R.id.text);
                tv.setText(getTabText(getItem(position)));

                return row;
            }
        });

        // Set action for dialog.
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                changeCategory(cid,
                               (Long)ab.getSelectedTab().getTag(),
                               (Long)((Tab)list.getAdapter().getItem(position)).getTag());
                refreshList(ab.getSelectedTab());
                dialog.dismiss();
            }
        });


        dialog.setTitle(R.string.select_category);
        dialog.show();
    }

    private void
    onContext_reverseOrder(final long cid) {
        try {
            DBPolicy.get().updateChannel_reverseOrder(cid);
        } catch (InterruptedException e) {
            finish();
            return;
        }
    }

    private void
    onContext_pickIcon(final long cid) {
        Intent i = new Intent(Intent.ACTION_PICK,
                              android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        cid_pickImage = cid;
        try {
            startActivityForResult(i, ReqCPickImage);
        } catch (ActivityNotFoundException e) {
            LookAndFeel.showTextToast(this, R.string.warn_find_gallery_app);
            return;
        }
    }

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Feed.Category[] cats;
        try {
            cats = DBPolicy.get().getCategories();
        } catch (InterruptedException e) {
            finish();
            return;
        }

        eAssert(cats.length > 0);

        // Setup list view
        setContentView(R.layout.channel_list);
        list = ((ListView) findViewById(R.id.list));
        eAssert(null != list);

        list.setAdapter(new ChannelListAdapter(this, R.layout.channel_row, null));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void
            onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(ChannelListActivity.this, ItemListActivity.class);
                intent.putExtra("channelid", id);
                startActivityForResult(intent, ReqCReadChannel);
            }
        });

        registerForContextMenu(list);

        // Setup Tabs
        ab = getActionBar();
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowHomeEnabled(false);

        for (Feed.Category cat : cats)
            addCategoryTab(cat);

        // Select default category as current category.
        ab.setSelectedNavigationItem(0);

        // 0 doesn't have meaning.
        setResult(0);
    }

    @Override
    public void
    onTabSelected(Tab tab, FragmentTransaction ft) {
        // change tab title's format to margee.
        getTabTextView(tab).setEllipsize(TextUtils.TruncateAt.MARQUEE);
        refreshList(tab);
    }

    @Override
    public void
    onTabUnselected(Tab tab, FragmentTransaction ft) {
        getTabTextView(tab).setEllipsize(TextUtils.TruncateAt.END);
    }

    @Override
    public void
    onTabReselected(Tab tab, FragmentTransaction ft) {
        getTabTextView(tab).setEllipsize(TextUtils.TruncateAt.MARQUEE);
        refreshList(tab);
    }

    @Override
    public void
    onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.channel_context, menu);
    }

    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.channel_opt, menu);
        return true;
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem mItem) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) mItem.getMenuInfo();

        switch (mItem.getItemId()) {
        case R.id.delete:
            logI(" ID : " + info.id + " / " + info.position);
            onContext_deleteChannel(info.id);
            return true;

        case R.id.change_category:
            onContext_changeCategory(info.id);
            return true;

        case R.id.reverse_order:
            onContext_reverseOrder(info.id);
            return true;

        case R.id.pick_icon:
            onContext_pickIcon(info.id);
            return true;
        }
        return false;
    }

    private void
    onResult_pickImage(int resultCode, Intent data) {
        if (RESULT_OK != resultCode)
            return;
        // this may takes quite long time (if image size is big!).
        // So, let's do it in background.
        new SpinAsyncTask(this, new PickIconEventHandler(), R.string.pick_icon_progress).execute(data);
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        case R.id.add_channel:
            onOpt_addChannel();
            break;
        case R.id.add_category:
            onOpt_addCategory();
            break;
        case R.id.delete_category:
            onOpt_deleteCategory();
            break;
        case R.id.select_predefined_channel:
            onOpt_selectPredefinedChannel();
            break;

        }
        return true;
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case ReqCReadChannel:
            getListAdapter().notifyDataSetChanged();
            break;
        case ReqCPredefinedChannel:
            refreshList(ab.getSelectedTab());
            break;
        case ReqCPickImage:
            onResult_pickImage(resultCode, data);
            break;
        }
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    protected void
    onDestroy() {
        getListAdapter().getCursor().close();
        super.onDestroy();
    }
}
