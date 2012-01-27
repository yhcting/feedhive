package free.yhc.feeder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class AddChannelActivity extends Activity {
    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_channel);
    }

    protected boolean
    verifyFeedUrl(String url) {
        return true;
    }

    public void
    onActionButtonClicked(View v) {
        String url = ((EditText)findViewById(R.id.url)).getText().toString();

        if (!verifyFeedUrl(url)) {
            // TODO Error handling here!
            return;
        }

        // for test
        //url = "http://old.ddanzi.com/appstream/ddradio.xml";
        //url = "file:///data/test/total_news.xml";
        url = "http://www.khan.co.kr/rss/rssdata/total_news.xml";

        Intent data = new Intent();
        data.putExtra("url", url);
        setResult(RESULT_OK, data);
        finish();
    }
}
