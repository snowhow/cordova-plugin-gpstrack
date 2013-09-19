/*
 * MIT License
 * (c) 2013 bernd@snowhow.info
 *
*/
package info.snowhow.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import android.os.Environment;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.io.RandomAccessFile;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;

import  android.content.BroadcastReceiver;

import android.app.NotificationManager;
import android.app.Notification;
import android.support.v4.app.NotificationCompat;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.IntentService;
import android.content.Intent;
import android.content.IntentFilter;

public class GPSTrack extends CordovaPlugin {

  private static final String GPS_TRACK_VERSION = "0.1";

  private static final String LOG_TAG = "GPSTrack";
  private static final long MINIMUM_DISTANCE_CHANGE_FOR_UPDATES = 1; // in Meters
  private static final long MINIMUM_TIME_BETWEEN_UPDATES = 1000; // in Milliseconds

  protected double minimumPrecision = 0;

  public NotificationManager nm;
  public NotificationCompat.Builder note;
  protected RandomAccessFile myWriter;
  protected long start_ts = System.currentTimeMillis();
  protected CallbackContext cbctx;

  protected BroadcastReceiver bcrc;
  protected String tf;

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    cbctx = callbackContext;
    if (action.equals("record")) {
      final String trackFile = args.getString(0);
      tf = trackFile;
      record(trackFile);
//       cordova.getActivity().runOnUiThread(new Runnable() {
//         @Override
//         public void run() {
//           GPSTrack.this.record(trackFile);
//           // callbackContext.success();
//         }
//       });
    } else {
      return false;
    }
    return true;
  }

  public void record(final String trackFile) {
    Context context = cordova.getActivity().getApplicationContext();
    Intent intent = new Intent(context, RecorderService.class);
    // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra("fileName", trackFile);
    Log.d(LOG_TAG, "in record ... should start intent now");
    context.startService(intent);
  }

}
