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
import android.os.Message;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

import java.io.RandomAccessFile;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;

import android.support.v4.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;

import android.app.NotificationManager;
import android.app.Notification;
import android.support.v4.app.NotificationCompat;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.IntentService;
import android.app.Service;
import android.os.IBinder;
import android.os.Binder;
import android.content.Intent;
import android.content.IntentFilter;

import info.snowhow.R;


public class RecorderService extends Service {
  private static final String GPS_TRACK_VERSION = "0.1";

  private static final String LOG_TAG = "GPSTrack";
  private static final long MINIMUM_DISTANCE_CHANGE_FOR_UPDATES = 1; // in Meters
  private static final long MINIMUM_TIME_BETWEEN_UPDATES = 1000; // in Milliseconds

  protected float minimumPrecision = 0;

  protected boolean recording = false;
  protected boolean firstPoint = true;

  protected LocationManager locationManager;
  protected int locations = 0;
  public NotificationManager nm;
  public NotificationCompat.Builder note;
  protected RandomAccessFile myWriter;
  protected long start_ts = System.currentTimeMillis();
  protected CallbackContext cbctx;

  protected MyLocationListener mll;
  protected BroadcastReceiver bcrc;
  protected String tf;
  protected String ifString = "snowhow_gpstrack_intent";
  protected Context cordova;
  protected int runningID;
  protected SharedPreferences sharedPref;
  protected SharedPreferences.Editor editor;


  public class LocalBinder extends Binder {
    RecorderService getService() {
      return RecorderService.this;
    }
  }

  private final IBinder mBinder = new LocalBinder();


  @Override
  public void onCreate() {
    Log.d(LOG_TAG, "onCreate called in service");
    mll = new MyLocationListener();
    // Context context = intent.getApplicationContext();
    // ctx = getContext();
    // cordova = getBundleExtra("info.snowhow.plugin.GPSTrack.cordova");
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    editor = sharedPref.edit();
    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    Log.d(LOG_TAG, "locationManager initialized, starting intent");
//     bcrc = new BroadcastReceiver() {
//       @Override
//       public void onReceive(Context context, Intent intent) {
//         recording = false;
//         firstPoint = true;
//         writeFile();
//         JSONObject obj = new JSONObject();
//         try {
//           obj.put("status", 0);
//           obj.put("file", tf);
//           PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
//           // cbctx.sendPluginResult(res);
//         } catch (JSONException e) {
//           // cbctx.success();
//         }
//         cleanUp();
//       }
//     };
    // registerReceiver(bcrc, new IntentFilter(ifString));
    // cordova.getActivity().registerReceiver(bcrc, new IntentFilter(ifString));
    registerReceiver(RecorderServiceBroadcastReceiver, new IntentFilter(ifString));
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    runningID = startId;
    Log.i(LOG_TAG, "Received start id " + startId + ": " + intent);
    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.

    if (intent == null) {
      Log.w(LOG_TAG, "Intent is null, trying to continue to write to file "+tf+" lm "+locationManager);
      tf = sharedPref.getString("runningTrackFile", "");
      minimumPrecision = sharedPref.getFloat("runningPrecision", 0);
      int count = sharedPref.getInt("count", 0);
      if (count > 0) {
        firstPoint = false;
      }
      if (tf == null || tf == "") {
        Log.e(LOG_TAG, "No trackfile found ... exit clean here");
        cleanUp();
        return START_NOT_STICKY;
      }
    } else {
      tf = intent.getStringExtra("fileName");
      minimumPrecision = intent.getFloatExtra("precision", 0);
      editor.putString("runningTrackFile", tf);
      editor.putFloat("runningPrecision", minimumPrecision);
      editor.commit();
    }
    // Intent bcRecI = new Intent(this, RecorderServiceBroadcastReceiver.class);
    Intent bcRecI = new Intent(ifString);
    PendingIntent pend = PendingIntent.getBroadcast(this, 0, bcRecI, 0);
    note = new NotificationCompat.Builder(this)
      .setContentTitle("snowhow gps tracking")
      .setSmallIcon(R.drawable.icon)
      .setOngoing(true)
      .setAutoCancel(true)
      .setDeleteIntent(pend)
      .setContentIntent(pend)
      .setContentText("No location yet. Click to quit recording.");
      // .addAction(android.R.drawable.ic_menu_camera, "Stop", pend);
    nm = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
    nm.notify(0, note.build());

    recording = true;
    Log.d(LOG_TAG, "this is handleIntent");
    try {
      // FileWriter file = new FileWriter(trackFile);
      myWriter = new RandomAccessFile(tf, "rw");
      if (intent != null) {   // start new file
        // myWriter.setLength(0);    // delete all contents from file
        String trackHead = initTrack(tf).toString();
        myWriter.write(trackHead.getBytes());
        // we want to write JSON manually for streamed writing
        myWriter.seek(myWriter.length()-1);
        myWriter.write(",\"coordinates\":[]}".getBytes());
      }
    } catch (IOException e) {
      Log.d(LOG_TAG, "io error.");
    }
    locationManager.requestLocationUpdates(
      LocationManager.GPS_PROVIDER, 
      MINIMUM_TIME_BETWEEN_UPDATES, 
      MINIMUM_DISTANCE_CHANGE_FOR_UPDATES,
      mll
    );
    locationManager.requestLocationUpdates(
      LocationManager.NETWORK_PROVIDER, 
      MINIMUM_TIME_BETWEEN_UPDATES, 
      MINIMUM_DISTANCE_CHANGE_FOR_UPDATES,
      mll
    );
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  public void handleMessage(Message msg) {
    Log.i(LOG_TAG, "handleMessage running...");
  }

  @Override
  public void onDestroy() {
    // Cancel the persistent notification.
    // mNM.cancel(NOTIFICATION);
    nm.cancel(0);
    // Tell the user we stopped.
    Toast.makeText(this, "snowhow: track recording service stopped", Toast.LENGTH_SHORT).show();
  }


  public void cleanUp() {
    locationManager.removeUpdates(mll);
    locationManager = null;
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    editor.clear();
    editor.commit();
    unregisterReceiver(RecorderServiceBroadcastReceiver);
    stopSelfResult(runningID);
    Log.i(LOG_TAG, "stopped service ...");
  }

  public void writeFile() {
    try {
      // myWriter.flush();
      myWriter.close();
    } catch (IOException e) {
      Log.d(LOG_TAG, "io error. close");
    }
  }

  public void deleteFile() {
    try {
      myWriter.close();
      File delFile = new File(tf);
      Log.d(LOG_TAG, "deleting file "+tf);
      delFile.delete();
    } catch (IOException e) {
      Log.d(LOG_TAG, "io delete error. delete");
    }
  }

  public JSONObject initTrack(String trackName) {
    JSONObject obj = new JSONObject();
    JSONObject prop = new JSONObject();
    try {
      prop.put("user", "anonymous@snowhow.info");
      prop.put("start_ts", start_ts);
      prop.put("rec_type", "gpstrack_plugin");
      prop.put("plugin_version", GPS_TRACK_VERSION);
      obj.put("type", "LineString");
      obj.put("properties", prop);
    } catch (JSONException e) {
      Log.d(LOG_TAG, "jsonObject error: "+e);
    }
    return obj;
  }

  private BroadcastReceiver RecorderServiceBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      // recording = false;
      // firstPoint = true;
      if (locations == 0) {   // no locations recorded, delete file
        deleteFile();
        cleanUp();
        Log.i(LOG_TAG, "cleaned up, file deleted (was empty)");
        return;
      }
      writeFile();
      JSONObject obj = new JSONObject();
      try {
        obj.put("status", 0);
        obj.put("file", tf);
        PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
        // cbctx.sendPluginResult(res);
      } catch (JSONException e) {
        // cbctx.success();
      }
      cleanUp();
      Log.i(LOG_TAG, "cleaned up, file saved");
    }
  };

  private class MyLocationListener implements LocationListener {

    public MyLocationListener() {
      Log.d(LOG_TAG, "MyLocationListener started successfully");
    }

    public void onLocationChanged(Location location) {
      if (recording != true) {
        return;
      }
      if (minimumPrecision > 0 && location.getAccuracy() > minimumPrecision) {
        Log.d(LOG_TAG, "precision of position not good enough: "+location.getAccuracy()+"m, required: "+minimumPrecision+"m");
        return;
      }
      locations = sharedPref.getInt("count", 0);
      locations += 1;
      editor.putInt("count", locations);
      editor.commit();
      note.setContentText("Click to stop track recording ("+locations+" points).");
      try {
        myWriter.seek(myWriter.length()-2); // kill last 2 byte
        String locString = "["+location.getLongitude()+","+location.getLatitude()+","+location.getAltitude()
              +","+location.getTime()+","+location.getAccuracy()+",\""+location.getProvider()+"\"]]}";
        if (firstPoint == true) {
          firstPoint = false;
        } else {
          locString = ","+locString;
        }
        myWriter.write(locString.getBytes());
      } catch (IOException e) {
        Log.d(LOG_TAG, "io error writing pos");
      }
      // location changed "+location.getLongitude()+"/"+location.getLatitude());
      nm.notify(0, note.build());
      // notifyViaCallback(location);
    }

    public void notifyViaCallback(Location location) {
      JSONObject obj = new JSONObject();
      JSONObject loc = new JSONObject();
      try {
        loc.put("latitude", location.getLatitude());
        loc.put("longitude", location.getLongitude());
        loc.put("altitude", location.getAltitude());
        loc.put("timestamp", location.getTime());
        loc.put("accuracy", location.getAccuracy());
        obj.put("location", loc);
        obj.put("count", locations);
      } catch (JSONException e) {
        Log.d(LOG_TAG, "jsonObject error: "+e);
      }
      // PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
      // res.setKeepCallback(true);
      // cbctx.sendPluginResult(res);
      // Log.d(LOG_TAG, "sending location "+location.getLongitude()+" to webapp");
    }

    public void onStatusChanged(String s, int i, Bundle b) {
    }

    public void onProviderDisabled(String s) {
    }

    public void onProviderEnabled(String s) {
    }
  }
}
