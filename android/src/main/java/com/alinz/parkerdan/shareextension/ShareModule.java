package com.alinz.parkerdan.shareextension;

import com.facebook.common.internal.ByteStreams;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

public class ShareModule extends ReactContextBaseJavaModule {
    public ShareModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }
    
    @Override
    public String getName() {
        return "ReactNativeShareExtension";
    }
    
    @ReactMethod
    public void close() {
        getCurrentActivity().finish();
    }
    
    @ReactMethod
    public void data(Promise promise) {
        promise.resolve(processIntent());
    }
    
    public WritableMap processIntent() {
        WritableMap map = Arguments.createMap();
        
        String value = "";
        String type = "";
        String action;
        
        Activity currentActivity = getReactApplicationContext().getCurrentActivity();
        
        if (currentActivity != null) {
            Intent intent = currentActivity.getIntent();
            action = intent.getAction();
            type = intent.getType();
            
            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if ("text/plain".equals(type)) {
                    value = handleSendText(intent); // Handle text being sent
                } else if (type.startsWith("image/")) {
                    Uri imageUri = handleSendImage(intent);
                    
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                        value = "file://" + RealPathUtil.getRealPathFromURI(currentActivity, imageUri);
                    } else {
                        if (imageUri.toString().startsWith("content://")) {
                            value = getImageUrlWithAuthority(currentActivity, imageUri);
                        } else {
                            value = imageUri.toString();//RealPathUtil.getRealPathFromURI(currentActivity, handleSendImage(intent)); // Handle single image being sent
                        }
                    }
                } else {
                    value = handleSendImage(intent).toString();
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
                if (type.startsWith("image/")) {
                    handleSendMultipleImages(intent); // Handle multiple images being sent
                }
            }
        }
        
        map.putString("type", type);
        map.putString("value", value);
        
        return map;
    }
    
    private static String getImageUrlWithAuthority(Context context, Uri uri) {
        InputStream is = null;
        if (uri.getAuthority() != null) {
            try {
                is = context.getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                
                return "file://" + writeToTempImageAndGetPathUri(context, bmp).toString();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
    
    private static byte[] getImageBytes(Context context, Uri uri) {
        if (uri.getAuthority() != null) {
            try {
                InputStream is = context.getContentResolver().openInputStream(uri);
                
                if (is != null) {
                    return ByteStreams.toByteArray(is);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    private static File writeToTempImageAndGetPathUri(Context inContext, Bitmap inImage) {
        PackageManager packageManager = inContext.getPackageManager();
        String packageName = inContext.getPackageName();
        String storageDirectory;
        
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            storageDirectory = packageInfo.applicationInfo.dataDir;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("File resolver", "Error Package name not found ", e);
            
            return null;
        }
        
        OutputStream fOut;
        Date currentDate = new Date();
        
        // the File to save , append increasing numeric counter to prevent files from getting overwritten.
        File file = new File(storageDirectory,  currentDate.getTime() + ".jpg");
        
        try {
            fOut = new FileOutputStream(file);
            
            // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
            inImage.compress(Bitmap.CompressFormat.JPEG, 90, fOut);
            fOut.flush(); // Not really required
            fOut.close(); // do not forget to close the stream
            
            MediaStore.Images.Media.insertImage(
                                                inContext.getContentResolver(),
                                                file.getAbsolutePath(),
                                                file.getName(),
                                                file.getName());
        } catch (FileNotFoundException ex) {
            Log.w("File resolver", "Couldn't write file to device");
        } catch (IOException ex) {
            Log.w("File resolver", "Could not write file");
        }
        
        return file;
    }
    
    private String handleSendText(Intent intent) {
        return intent.getStringExtra(Intent.EXTRA_TEXT);
    }
    
    private Uri handleSendImage(Intent intent) {
        return (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }
    
    private void handleSendMultipleImages(Intent intent) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            // Update UI to reflect multiple images being shared
        }
    }
}
