package free.yhc.feeder.model;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.util.ByteArrayBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class Utils {
    public static final boolean DBG = true;
    private static final String TAG = "[Feeder]";

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

    private static final int NR_TIMELOG_SCRATCH = 10;

    public static void
    eAssert(boolean cond) {
        if (!DBG)
            return;

        if (!cond) {
            Thread.dumpStack();
            throw new AssertionError();
        }
    }

    public static void
    logI(String msg) {
        if (!DBG)
            return;

        Log.i(TAG, msg);
    }

    public static void
    logW(String msg) {
        if (!DBG)
            return;

        Log.w(TAG, msg);
    }

    public static void
    logE(String msg) {
        if (!DBG)
            return;

        Log.e(TAG, msg);
    }

    public static boolean
    isValidValue(String v) {
        return !(null == v || v.isEmpty());
    }

    public static int
    dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

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

    //
    // Get size(width, height) of given image.
    // @param image : 'image file path' or 'byte[]' image data
    // @param out : out[0] : width of image / out[1] : height of image
    // @return false if image cannot be decode. true if success
    //
    // out[0] : width / out[1] : height
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

    public static ByteArrayBuffer
    download(String dnurl) throws FeederException {
        if (null == dnurl)
            return null;

        long time = System.currentTimeMillis();
        ByteArrayBuffer bab = null;
        try {
            URL url = new URL(dnurl);

            //
            // long startTime = System.currentTimeMillis();
            // logI("Start Downloading : " + dnurl);
            //

            // Open a connection to that URL.
            URLConnection ucon = url.openConnection();
            // Define InputStreams to read from the URLConnection.
            InputStream is = ucon.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);

            // Read bytes to the Buffer until there is nothing more to read(-1).
            bab = new ByteArrayBuffer(1024);
            int current = 0;
            while ((current = bis.read()) != -1)
                bab.append((byte) current);

            //
            // logI("End Downloading : " + ((System.currentTimeMillis() -
            // startTime) / 1000) + " sec");
            //

        } catch (IOException e) {
            throw new FeederException(Err.IONet);
        }
        logI("TIME: Downloading [" + dnurl + "] : " + (System.currentTimeMillis() - time));
        return bab;
    }

    public static byte[]
    getDecodedImageData(String url) throws FeederException {
        ByteArrayBuffer imgBab = null;
        imgBab = download(url);

        long time;
        //
        // [ In case of RSS. ]
        // Lots of sites doesn't obey RSS spec. related with channel image.
        // Spec. says max value for width = 144 / for height = 400.
        // But, there are lots of out-of-spec-sites
        // So, we need to consider this case (image size is out-of-spec.)
        // Solution used below is
        // * shrink downloaded image and save it to DB.
        // (To save memory and increase performance.)
        if (null != imgBab && !imgBab.isEmpty()) {
            time = System.currentTimeMillis();
            Bitmap bm = Utils.decodeImage(imgBab.toByteArray(),
                    Feed.CHANNEL_IMAGE_MAX_WIDTH,
                    Feed.CHANNEL_IMAGE_MAX_HEIGHT);
            logI("TIME: Decode Image : " + (System.currentTimeMillis() - time));
            if (null == bm)
                return null;

            time = System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            bm.recycle();
            logI("TIME: Compress Image : " + (System.currentTimeMillis() - time));
            return baos.toByteArray();
        }
        return null;
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
    removeFileRecursive(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                removeFileRecursive(c);
        }
        if (!f.delete())
            return false;
        return true;
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
}
