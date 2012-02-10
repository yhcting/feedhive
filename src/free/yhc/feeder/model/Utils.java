package free.yhc.feeder.model;

import java.io.File;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class Utils {
    private static final boolean DBG = true;
    private static final String TAG = "[Feeder]";

    // index of each value.
    private static final int EXT2MIME_OFFSET_EXT  = 0;
    private static final int EXT2MIME_OFFSET_MIME = 1;
    private static final int EXT2MIME_OFFSET_SZ   = 2;
    private static String[] ext2mimeMap = {
        // Image
        "gif",          "image/gif",
        "jpeg",         "image/jpeg",
        "jpg",          "image/jpeg",
        "png",          "image/png",
        "tiff",         "image/tiff",
        "bmp",          "image/bmp", // is this standard???

        // Audio
        "mp3",          "audio/mpeg",
        "aac",          "audio/*",

        // Video
        "mpeg",         "video/mpeg",
        "mp4",          "video/mp4",
        "ogg",          "video/ogg",

        // Text
        "txt",          "text/plain",
        "html",         "text/html",
        "xml",          "text/xml",
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
        return (int)(dp * context.getResources().getDisplayMetrics().density);
    }

    private static Bitmap
    decodeImageRaw(Object image, BitmapFactory.Options opt) {
        if (image instanceof String) {
            return BitmapFactory.decodeFile((String)image, opt);
        } else if (image instanceof byte[]) {
            byte[] data = (byte[])image;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opt);
        }
        eAssert(false);
        return null;
    }

    /*
     * Get size(width, height) of given image.
     * @param image : 'image file path' or 'byte[]' image data
     * @param out : out[0] : width of image / out[1] : height of image
     * @return
     *   false if image cannot be decode.
     *   true if success
     */
    // out[0] : width / out[1] : height
    public static boolean
    imageSize(Object image, int[] out) {
        eAssert(null != image);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        decodeImageRaw(image, opt);
        if(opt.outWidth <= 0 || opt.outHeight <= 0 || null == opt.outMimeType) {
            return false;
        }
        out[0] = opt.outWidth;
        out[1] = opt.outHeight;
        return true;
    }

    /*
     * Calculate rectangle(out[]). This is got by shrinking rectangle(width,height) to bound rectangle(boundW, boundH) with fixed ratio.
     * If input rectangle is included in bound, then input rectangle itself will be returned.(we don't need to adjust)
     * @param boundW : width of bound rect
     * @param boundH : height of bound rect
     * @param width : width of rect to be shrunk
     * @param height : height of rect to be shrunk
     * @param out : calculated value [ out[0](width) out[1](height) ]
     * @return : false(not shrunk) / true(shrunk)
     */
    public static boolean
    shrinkFixedRatio(int boundW, int boundH, int width, int height, int[] out) {
        boolean ret;
        // Check size of picture..
        float rw = (float)boundW / (float)width, // width ratio
              rh = (float)boundH / (float)height; // height ratio

        // check whether shrinking is needed or not.
        if(rw >= 1.0f && rh >= 1.0f) {
            // we don't need to shrink
            out[0] = width;
            out[1] = height;
            ret = false;
        } else {
            // shrinking is essential.
            float ratio = (rw > rh)? rh: rw; // choose minimum
            // integer-type-casting(rounding down) guarantees that value cannot be greater than bound!!
            out[0] = (int) ( ratio * width );
            out[1] = (int) ( ratio * height );
            ret = true;
        }
        return ret;
    }
    /*
     * Make fixed-ration-bounded-bitmap with file.
     * if (0 >= boundW || 0 >= boundH), original-size-bitmap is trying to be created.
     * @param fpath : image file path (absolute path)
     * @param boundW : bound width
     * @param boundH : bound height
     * @return : null (fail)
     */
    public static Bitmap
    decodeImage(Object image, int boundW, int boundH) {
        eAssert(null != image);

        BitmapFactory.Options opt = null;
        if (0 < boundW && 0 < boundH) {
            int[] imgsz = new int[2]; // image size : [0]=width / [1] = height
            if (false == imageSize(image, imgsz) ) {
                // This is not proper image data
                return null;
            }

            int[] bsz = new int[2]; // adjusted bitmap size
            boolean bShrink = shrinkFixedRatio(boundW, boundH, imgsz[0], imgsz[1], bsz);

            opt = new BitmapFactory.Options();
            opt.inDither = false;
            if (bShrink) {
                // To save memory we need to control sampling rate. (based on width!)
                // for performance reason, we use power of 2.
                if (0 >= bsz[0])
                    return null;

                int sampleSize = 1;
                while (1 < imgsz[0] / (bsz[0] * sampleSize))
                    sampleSize *= 2;

                // shrinking based on width ratio!!
                // NOTE : width-based-shrinking may make 1-pixel error in height side!
                // (This is not Math!! And we are using integer!!! we cannot make it exactly!!!)
                opt.inScaled = true;
                opt.inSampleSize = sampleSize;
                opt.inDensity = imgsz[0] / sampleSize;
                opt.inTargetDensity = bsz[0];
            }
        }
        return decodeImageRaw(image, opt);
    }

    public static boolean
    isMimeType(String str) {
        int idx = str.lastIndexOf("/");
        if (idx < 0)
            return false;

        String type = str.substring(0, idx);
        for (String t : mimeTypes)
            if (type.equalsIgnoreCase(t))
                return true;

        return false;
    }

    public static String
    getExtention(String str) {
        int idx = str.lastIndexOf(".");
        if (idx < 0)
            return null;
        return str.substring(idx + 1);
    }

    public static String
    guessMimeType(String filename) {
        String ext = getExtention(filename);
        if (null == ext)
            return null;

        for (int i = 0; i < ext2mimeMap.length; i += EXT2MIME_OFFSET_SZ)
            if (ext2mimeMap[EXT2MIME_OFFSET_EXT].equalsIgnoreCase(ext))
                return ext2mimeMap[EXT2MIME_OFFSET_MIME];

        return null;
    }

    // convert give string to filename-form
    public static String
    convertToFilename(String str) {
        // Most Unix (including Linux) allows all 8bit-character as file name except for ('/' and 'null').
        return str.replace('/', '~');
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

    public static boolean
    isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (null != ni)
            return ni.isConnectedOrConnecting();
        else
            return false;
    }
}
