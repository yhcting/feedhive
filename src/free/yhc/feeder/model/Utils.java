package free.yhc.feeder.model;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;

import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Layout;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.TextView;

public class Utils {
    public static final boolean DBG = true;
    private static final String TAG = "[Feeder]";

    // Format of nString (Number String)
    // <number>/<number>/ ... '/' is delimiter between number.
    private static final String nStringDelimiter = "/";
    private static final long   dayInMs = 24 * 60 * 60 * 1000;
    // Characters that is not allowed as filename in Android.
    private static final char[] noFileNameChars = new char[] {
        '/', '?', '"', '*', '|', '\\', '<', '>'
    };

    // Belows are not used.
    // Instead of self-implementation, predefined-android-classes are used.
    // index of each value.
    private static final int EXT2MIME_OFFSET_EXT = 0;
    private static final int EXT2MIME_OFFSET_MIME = 1;
    private static final int EXT2MIME_OFFSET_SZ = 2;
    private static String[] ext2mimeMap = {
            // Image
            "gif",           "image/gif",
            "jpeg",          "image/jpeg",
            "jpg",           "image/jpeg",
            "png",           "image/png",
            "tiff",          "image/tiff",
            "bmp",           "image/bmp", // is this standard???

            // Audio
            "mp3",           "audio/mpeg",
            "aac",           "audio/*",

            // Video
            "mpeg",          "video/mpeg",
            "mp4",           "video/mp4",
            "ogg",           "video/ogg",

            // Text
            "txt",           "text/plain",
            "html",          "text/html",
            "xml",           "text/xml",
    };

    private static String[] mimeTypes = {
            "application",
            "audio",
            "image",
            "message",
            "model",
            "multipart",
            "text",
            "video",
    };

    // NOTE
    // Too many format may drop parsing performance very much.
    // So, be careful!
    private static final String[] dateFormats = new String[] {
            DateUtils.PATTERN_RFC1036,
            DateUtils.PATTERN_RFC1123,
            // To support W3CDTF
            "yyyy-MM-d'T'HH:mm:ssZ",
            "yyyy-MM-d'T'HH:mm:ss:SSSZ",
            // To support some non-standard formats.
            // (I hate this! But lot's of sites don't obey standard!!)
            "yyyy-MM-d HH:mm:ss",
            "yyyy.MM.d HH:mm:ss",
        };

    // =======================
    // Private
    // =======================
    private static Bitmap
    decodeImageRaw(Object image, BitmapFactory.Options opt) {
        if (image instanceof String) {
            return BitmapFactory.decodeFile((String) image, opt);
        } else if (image instanceof byte[]) {
            byte[] data = (byte[]) image;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        }
        eAssert(false);
        return null;
    }


    // =======================
    // Public
    // =======================
    public static void
    eAssert(boolean cond) {
        if (!DBG)
            return;

        if (!cond)
            throw new AssertionError();
    }

    public static void
    logI(String msg) {
        if (!DBG)
            return;
        if (null != msg)
            Log.i(TAG, msg);
    }

    public static void
    logW(String msg) {
        if (!DBG)
            return;

        if (null != msg)
            Log.w(TAG, msg);
    }

    public static void
    logE(String msg) {
        if (!DBG)
            return;

        if (null != msg)
            Log.e(TAG, msg);
    }

    public static long[]
    arrayLongTolong(Long[] L) {
        long[] l = new long[L.length];
        for (int i = 0; i < L.length; i++)
            l[i] = L[i];
        return l;
    }

    public static boolean
    isValidValue(String v) {
        return !(null == v || v.isEmpty());
    }

    public static int
    dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }


    public static long
    hourToMs(long hour) {
        return hour * 60 * 60 * 1000;
    }

    public static long
    secToMs(long sec) {
        return sec * 1000;
    }

    public static long[]
    nStringToNrs(String timeString) {
        if (!isValidValue(timeString))
            return new long[0];

        String[] timestrs = timeString.split(nStringDelimiter);
        long[] times = new long[timestrs.length];
        try {
            for (int i = 0; i < times.length; i++)
                times[i] = Long.parseLong(timestrs[i]);
        } catch (NumberFormatException e) {
            logW("Invalid time string! [" + timeString + "]");
            eAssert(false);
        }
        return times;
    }

    public static String
    nrsToNString(long[] nrs) {
        String nrstr = "";
        if (nrs.length < 1)
            return "";

        for (int i = 0; i < nrs.length - 1; i ++)
            nrstr += nrs[i] + nStringDelimiter;
        nrstr += nrs[nrs.length - 1];
        return nrstr;
    }

    /**
     *
     * @param dateString
     * @return times in mills. -1 if failed to parse.
     */
    public static long
    dateStringToTime(String dateString) {
        // try to parse with http dates format
        String date = removeLeadingTrailingWhiteSpace(dateString);
        try {
            return DateUtils.parseDate(date).getTime();
        } catch (DateParseException e) { }
        try {
            return DateUtils.parseDate(date, dateFormats).getTime();
        } catch (DateParseException e) {
            return -1;
        }
    }

    /**
     * Get size(width, height) of given image.
     *
     * @param image : 'image file path' or 'byte[]' image data
     * @param out : out[0] : width of image / out[1] : height of image
     * @return : false if image cannot be decode. true if success
     */
    public static boolean
    imageSize(Object image, int[] out) {
        eAssert(null != image);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        decodeImageRaw(image, opt);
        if (opt.outWidth <= 0 || opt.outHeight <= 0 || null == opt.outMimeType) {
            return false;
        }
        out[0] = opt.outWidth;
        out[1] = opt.outHeight;
        return true;
    }

    //
    // Calculate rectangle(out[]). This is got by shrinking
    // rectangle(width,height) to bound rectangle(boundW, boundH) with fixed
    // ratio. If input rectangle is included in bound, then input rectangle
    // itself will be returned.(we don't need to adjust)
    //
    // @param boundW : width of bound rect
    // @param boundH : height of bound rect
    // @param width : width of rect to be shrunk
    // @param height : height of rect to be shrunk
    // @param out : calculated value [ out[0](width) out[1](height) ]
    // @return : false(not shrunk) / true(shrunk)
    //
    public static boolean
    shrinkFixedRatio(int boundW, int boundH, int width, int height, int[] out) {
        boolean ret;
        // Check size of picture..
        float rw = (float) boundW / (float) width, // width ratio
        rh = (float) boundH / (float) height; // height ratio

        // check whether shrinking is needed or not.
        if (rw >= 1.0f && rh >= 1.0f) {
            // we don't need to shrink
            out[0] = width;
            out[1] = height;
            ret = false;
        } else {
            // shrinking is essential.
            float ratio = (rw > rh) ? rh : rw; // choose minimum
            // integer-type-casting(rounding down) guarantees that value cannot
            // be greater than bound!!
            out[0] = (int) (ratio * width);
            out[1] = (int) (ratio * height);
            ret = true;
        }
        return ret;
    }

    //
    // Make fixed-ration-bounded-bitmap with file. if (0 >= boundW || 0 >=
    // boundH), original-size-bitmap is trying to be created.
    //
    // @param fpath : image file path (absolute path)
    // @param boundW : bound width
    // @param boundH : bound height
    // @return : null (fail)
    //
    public static Bitmap
    decodeImage(Object image, int boundW, int boundH) {
        eAssert(null != image);

        BitmapFactory.Options opt = null;
        if (0 < boundW && 0 < boundH) {
            int[] imgsz = new int[2]; // image size : [0]=width / [1] = height
            if (false == imageSize(image, imgsz)) {
                // This is not proper image data
                return null;
            }

            int[] bsz = new int[2]; // adjusted bitmap size
            boolean bShrink = shrinkFixedRatio(boundW, boundH, imgsz[0], imgsz[1], bsz);

            opt = new BitmapFactory.Options();
            opt.inDither = false;
            if (bShrink) {
                // To save memory we need to control sampling rate. (based on
                // width!)
                // for performance reason, we use power of 2.
                if (0 >= bsz[0])
                    return null;

                int sampleSize = 1;
                while (1 < imgsz[0] / (bsz[0] * sampleSize))
                    sampleSize *= 2;

                // shrinking based on width ratio!!
                // NOTE : width-based-shrinking may make 1-pixel error in height
                // side!
                // (This is not Math!! And we are using integer!!! we cannot
                // make it exactly!!!)
                opt.inScaled = true;
                opt.inSampleSize = sampleSize;
                opt.inDensity = imgsz[0] / sampleSize;
                opt.inTargetDensity = bsz[0];
            }
        }
        return decodeImageRaw(image, opt);
    }

    public static byte[]
    compressBitmap(Bitmap bm) {
        long time = System.currentTimeMillis();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        logI("TIME: Compress Image : " + (System.currentTimeMillis() - time));
        return baos.toByteArray();
    }

    public static boolean
    isEllipsed(TextView tv) {
        Layout l = tv.getLayout();
        if (null != l){
            int lines = l.getLineCount();
            if (lines > 0)
                if (l.getEllipsisCount(lines - 1) > 0)
                    return true;
        }
        return false;
    }

    public static Err
    writeToFile(File file, byte[] data) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
        } catch(FileNotFoundException e) {
            return Err.IOFile;
        } catch(IOException e) {
            return Err.IOFile;
        } finally {
            try {
                if (null != fos)
                    fos.close();
            } catch (IOException e) {}
        }
        return Err.NoErr;
    }

    public static String
    readTextFile(File file) {
        try {
            StringBuffer fileData = new StringBuffer(4096);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            char[] buf = new char[4096];
            int bytes;
            while(-1 != (bytes = reader.read(buf)))
                fileData.append(buf, 0, bytes);
            reader.close();
            return fileData.toString();
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public static String
    getExtentionFromUrl(String url) {
        return MimeTypeMap.getFileExtensionFromUrl(url);
        /* -- obsoleted implementation.
        int idx = str.lastIndexOf(".");
        if (idx < 0)
            return null;
        return str.substring(idx + 1);
        */
    }

    public static String
    guessMimeTypeFromUrl(String url) {
        String ext = getExtentionFromUrl(url);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        /* -- obsoleted implementation.
        String ext = getExtention(filename);
        if (null == ext)
            return null;

        for (int i = 0; i < ext2mimeMap.length; i += EXT2MIME_OFFSET_SZ)
            if (ext2mimeMap[EXT2MIME_OFFSET_EXT].equalsIgnoreCase(ext))
                return ext2mimeMap[EXT2MIME_OFFSET_MIME];

        return null;
        */
    }

    public static boolean
    isMimeType(String str) {
        // Let's reuse Android class
        return MimeTypeMap.getSingleton().hasMimeType(str);

        /* -- obsoleted implementation.
        int idx = str.lastIndexOf("/");
        if (idx < 0)
            return false;

        String type = str.substring(0, idx);
        for (String t : mimeTypes)
            if (type.equalsIgnoreCase(t))
                return true;

        return false;
        */
    }


    // convert give string to filename-form
    public static String
    convertToFilename(String str) {
        // Most Unix (including Linux) allows all 8bit-character as file name
        //   except for ('/' and 'null').
        // But android shell doens't allows some characters.
        // So, we need to handle those...
        for (char c : noFileNameChars)
            str = str.replace(c, '~');
        return str;
    }

    // return : false(fail to full-delete)
    public static boolean
    removeFileRecursive(File f, boolean bDeleteMe) {
        boolean ret = true;
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                if (!removeFileRecursive(c, true))
                    ret = false;
        }
        if (ret && bDeleteMe)
            return f.delete();
        return ret;
    }

    public static String
    removeLeadingTrailingWhiteSpace(String s) {
        s = s.replaceFirst("^\\s+", "");
        return s.replaceFirst("\\s+$", "");
    }

    public static boolean
    isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (null != ni)
            return ni.isConnectedOrConnecting();
        else
            return false;
    }


    public static long
    dayBaseMs(Calendar cal) {
        Calendar temp = Calendar.getInstance();
        temp.setTime(cal.getTime());
        // Set to 00:00:00:000
        temp.set(Calendar.HOUR_OF_DAY, 0);
        temp.set(Calendar.MINUTE, 0);
        temp.set(Calendar.SECOND, 0);
        temp.set(Calendar.MILLISECOND, 0);
        return temp.getTimeInMillis();
    }

    // SIDE EFFECT!
    //   'secs' is sorted by ascending numerical order!!
    //
    // @secs
    //      00:00:00 based value.
    //      seconds since 00:00:00 (12:00 AM)
    //      (negative value is NOT ALLOWED)
    //      Order may be changed (sorted) to ascending order.
    // @return : time to next nearest time (ms based on 1970....)
    public static long
    nextNearestTime(Calendar calNow, long[] secs) {
        eAssert(secs.length > 0);
        Calendar cal = Calendar.getInstance();
        cal.setTime(calNow.getTime());

        long now = cal.getTimeInMillis();
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long dayBase = cal.getTimeInMillis();
        long dayTime = now - dayBase;
        if (dayTime < 0)
            dayTime = 0; // To compensate error from '/' operation.

        Arrays.sort(secs);
        // Just linear search... it's enough.
        // Binary search is not considered yet.(over-engineering)
        for (long s : secs) {
            eAssert(s >= 0);
            if (s * 1000 > dayTime)
                return dayTime + s * 1000;
        }
        // All scheduled time is passed for day.
        // smallest of tomorrow is nearest one.
        return dayBase + dayInMs + secs[0] * 1000;
    }
}
