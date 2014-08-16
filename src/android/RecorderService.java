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
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;
import android.view.WindowManager;

import java.io.RandomAccessFile;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.InetAddress;

import android.content.BroadcastReceiver;

import android.app.NotificationManager;
import android.app.Notification;
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
  private static final String GPS_TRACK_VERSION = "0.2.1";

  private static final String LOG_TAG = "GPSTrack";
  private static final long MINIMUM_DISTANCE_CHANGE_FOR_UPDATES = 10; // in Meters
  private static final long MINIMUM_TIME_BETWEEN_UPDATES = 5000; // in Milliseconds
  private static final long MINIMUM_TIME_BETWEEN_UPDATES_FAST = 1000; // in Milliseconds
  private static final long SPEED_LIMIT = 5; // in meter per second

  protected float minimumPrecision = 0;

  protected boolean recording = false;
  protected boolean firstPoint = true;

  protected LocationManager locationManager;
  protected int locations = 0;
  public NotificationManager nm;
  public Notification.Builder note;
  protected RandomAccessFile myWriter;
  protected long start_ts = System.currentTimeMillis();
  protected CallbackContext cbctx;

  protected MyLocationListener mgpsll, mnetll;
  protected BroadcastReceiver bcrc;
  protected String tf;
  protected String ifString = "snowhow_gpstrack_intent";
  protected Context cordova;
  protected int runningID;
  protected SharedPreferences sharedPref;
  protected SharedPreferences.Editor editor;
  public GPSServer gpss;
  protected Location lastLoc;
  protected boolean goingFast = false;


  public class LocalBinder extends Binder {
    RecorderService getService() {
      return RecorderService.this;
    }
  }

  private final IBinder mBinder = new LocalBinder();


  @Override
  public void onCreate() {
    Log.d(LOG_TAG, "onCreate called in service");
    mgpsll = new MyLocationListener();
    mnetll = new MyLocationListener();
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    editor = sharedPref.edit();
    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    Log.d(LOG_TAG, "locationManager initialized, starting intent");
    registerReceiver(RecorderServiceBroadcastReceiver, new IntentFilter(ifString));
    startGPSS();
  }

  private void showNoGPSAlert() {
    Log.i(LOG_TAG, "No GPS available --- show Dialog");
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    alertDialogBuilder.setMessage("GPS is disabled on your device. Would you like to enable it?")
    .setCancelable(false)
    .setPositiveButton("GPS Settings", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        Intent callGPSSettingIntent = new Intent(
          android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
          callGPSSettingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(callGPSSettingIntent);
        }
    });
    alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id){
        stopRecording();
        dialog.cancel();
      }
    });
    AlertDialog alert = alertDialogBuilder.create();
    alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
    alert.show();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(LOG_TAG, "Received start id " + startId + ": " + intent);
    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
      showNoGPSAlert();
    }
    runningID = startId;
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
      Log.d(LOG_TAG, "FILENAME for recording is "+tf);
      minimumPrecision = intent.getFloatExtra("precision", 0);
      editor.putString("runningTrackFile", tf);
      editor.putFloat("runningPrecision", minimumPrecision);
      editor.commit();
    }
    Intent bcRecI = new Intent(ifString);
    PendingIntent pend = PendingIntent.getBroadcast(this, 0, bcRecI, 0);
    note = new Notification.Builder(this)
      .setContentTitle("snowhow gps tracking")
      .setSmallIcon(R.drawable.icon)
      .setOngoing(true)
      .setAutoCancel(true)
      .setDeleteIntent(pend)
      .setContentIntent(pend)
      .setContentText("No location yet. Click to quit recording.");
    nm = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
    nm.notify(0, note.build());

    recording = true;
    Log.d(LOG_TAG, "recording in handleIntent");
    try {   // create directory first, if it does not exist
      File trackFile = new File(tf).getParentFile();
      if (!trackFile.exists()) {
        trackFile.mkdirs();
        Log.d(LOG_TAG, "done creating path for trackfile: "+trackFile);
      }
      Log.d(LOG_TAG, "going to create RandomAccessFile "+tf);
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
      Log.d(LOG_TAG, "io error. cannot write to file "+tf);
    }
    locationManager.requestLocationUpdates(
      LocationManager.GPS_PROVIDER,
      MINIMUM_TIME_BETWEEN_UPDATES,
      MINIMUM_DISTANCE_CHANGE_FOR_UPDATES,
      mgpsll
    );
    if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
      locationManager.requestLocationUpdates(
        LocationManager.NETWORK_PROVIDER,
        MINIMUM_TIME_BETWEEN_UPDATES,
        MINIMUM_DISTANCE_CHANGE_FOR_UPDATES,
        mnetll
      );
    }
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
    nm.cancel(0);
    // Tell the user we stopped.
    Toast.makeText(this, "snowhow: track recording service stopped", Toast.LENGTH_SHORT).show();
  }


  public void cleanUp() {
    try {
      gpss.sendString("{ \"type\": \"status\", \"msg\": \"disconnect\" }");
      Log.d(LOG_TAG, "stopping gpss ...");
      gpss.stop(1000);
      Log.d(LOG_TAG, "stopped gpss ...");
    } catch (Exception e) {
      Log.d(LOG_TAG, "unable to stop gpss");
      Log.d(LOG_TAG, "stack", e);
    }
    // deleteLockFile();
    locationManager.removeUpdates(mgpsll);
    locationManager.removeUpdates(mnetll);
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

  public void deleteLockFile() {
    if (tf == null || tf == "") {
      return;
    }
    try {
      File lockFile = new File(tf+".lock");
      lockFile.delete();
    } catch(Exception e) {
      Log.d(LOG_TAG, "io err: cannot delete lockfile");
    }
  }

  public JSONObject initTrack(String trackName) {
    JSONObject obj = new JSONObject();
    JSONObject prop = new JSONObject();
    try {
      prop.put("user", "anonymous@snowhow.info");
      prop.put("start_ts", start_ts);
      prop.put("localname", "snowhow_"+start_ts+".json");
      prop.put("rec_type", "gpstrack_plugin");
      prop.put("plugin_version", GPS_TRACK_VERSION);
      obj.put("type", "LineString");
      obj.put("properties", prop);
    } catch (JSONException e) {
      Log.d(LOG_TAG, "jsonObject error: "+e);
    }
    return obj;
  }

  public void startGPSS() {
    Log.d(LOG_TAG, "starting WS Server on Port 8887");
    try {
      gpss = new GPSServer(8887, this);
      gpss.start();
      Log.d(LOG_TAG, "starting WS Server on : "+gpss.getAddress());
      Log.d(LOG_TAG, "Gpssss is "+gpss);
      gpss.sendString("{ \"type\": \"start\", \"msg\": \"start on "+gpss.getAddress()+"\" }");
    } catch (Exception e) {
      Log.d(LOG_TAG, "ERROR starting WS Server on Port 8887");
      Log.d(LOG_TAG, "bad:", e);
    }
  }

  public void stopRecording() {
    nm.cancel(0);
    if (locations == 0) {   // no locations recorded, delete file
      deleteFile();
      cleanUp();
      Log.i(LOG_TAG, "cleaned up, file deleted (was empty)");
      return;
    }
    writeFile();
    cleanUp();
    Log.i(LOG_TAG, "cleaned up, file saved");
  }

  public String getTrackFilename() {
    return tf;
  }


  private BroadcastReceiver RecorderServiceBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (locations == 0) {   // no locations recorded, delete file
        deleteFile();
        cleanUp();
        Log.i(LOG_TAG, "cleaned up, file deleted (was empty)");
        return;
      }
      writeFile();
      cleanUp();
      Log.i(LOG_TAG, "cleaned up, file saved");
    }
  };

  private class MyLocationListener implements LocationListener {

    private boolean firstGPSfix = false;

    public MyLocationListener() {
      Log.d(LOG_TAG, "MyLocationListener started successfully");
    }

    public void onLocationChanged(Location location) {
      float speed = location.getSpeed();
      String speedType = "gps";
      if (recording != true) {
        return;
      }
      if (minimumPrecision > 0 && location.getAccuracy() > minimumPrecision) {
        Log.d(LOG_TAG, "precision of position not good enough: "+location.getAccuracy()+"m, required: "+minimumPrecision+"m");
        return;
      }
      if (location.getProvider().equals(LocationManager.GPS_PROVIDER) && firstGPSfix == false) {
        Log.d(LOG_TAG, "removed network LocationListener");
        Toast.makeText(RecorderService.this, "snowhow: on GPS logging now", Toast.LENGTH_SHORT).show();
        firstGPSfix = true;
        locationManager.removeUpdates(mnetll);
      }
      if (lastLoc != null) {
        if (speed == 0) {
          long timeDiff = (location.getTime() - lastLoc.getTime())/1000;
          speed = lastLoc.distanceTo(location)/timeDiff;
          speedType = "calc";
          Log.d(LOG_TAG, "speed calc from lastLoc "+timeDiff+" makes speed "+speed);
        }
        if (speed > SPEED_LIMIT && goingFast == false) {  // faster than 5 m/s, switch to faster GPS interval
          Log.d(LOG_TAG, "travelling fast --- switch to fast update");
          if (gpss != null) {
            gpss.sendString("{ \"type\": \"status\", \"msg\": \"fastUpdate\", \"interval\": "
                +MINIMUM_TIME_BETWEEN_UPDATES_FAST+"}");
          }
          locationManager.removeUpdates(mgpsll);
          locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            MINIMUM_TIME_BETWEEN_UPDATES_FAST,
            MINIMUM_DISTANCE_CHANGE_FOR_UPDATES,
            mgpsll
          );
          goingFast = true;
        } else if (speed <= SPEED_LIMIT && goingFast == true) {
          Log.d(LOG_TAG, "travelling slow --- switch to slow update");
          if (gpss != null) {
            gpss.sendString("{ \"type\": \"status\", \"msg\": \"slowUpdate\", \"interval\": "
                +MINIMUM_TIME_BETWEEN_UPDATES+"}");
          }
          locationManager.removeUpdates(mgpsll);
          locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            MINIMUM_TIME_BETWEEN_UPDATES,
            MINIMUM_DISTANCE_CHANGE_FOR_UPDATES,
            mgpsll
          );
          goingFast = false;
        }
      }
      lastLoc = location;
      locations = sharedPref.getInt("count", 0);
      locations += 1;
      editor.putInt("count", locations);
      editor.commit();
      long gpsInterval = (goingFast) ? (MINIMUM_TIME_BETWEEN_UPDATES_FAST/1000) : (MINIMUM_TIME_BETWEEN_UPDATES/1000);
      note.setContentText("Click to stop track recording ("+locations+" points, "+gpsInterval+" secs tracking interval).");
      try {
        String locString = "["+location.getLongitude()+","+location.getLatitude()+","+location.getAltitude()
              +","+location.getTime()+","+location.getAccuracy()+",\""+location.getProvider()+"\"]";
        int pointCount = sharedPref.getInt("count", 0);
        String gpssString = "{\"type\":\"coords\",\"coords\":"+locString
          +",\"pointCount\":"+pointCount+",\"speed\":"+speed+",\"speedType\":\""+speedType+"\","
          +"\"interval\":"+gpsInterval+"}";
        if (gpss != null) {
          gpss.sendString(gpssString);
        } else {
          startGPSS();
          Log.d(LOG_TAG, "started new GPSS");
          gpss.sendString(gpssString);
        }
        locString += "]}";
        myWriter.seek(myWriter.length()-2); // remove last 2 byte
        if (firstPoint == true) {
          firstPoint = false;
        } else {
          locString = ","+locString;
        }
        myWriter.write(locString.getBytes());
      } catch (IOException e) {
        Log.d(LOG_TAG, "io error writing pos");
      }
      nm.notify(0, note.build());
    }

    public void onStatusChanged(String s, int i, Bundle b) {
    }

    public void onProviderDisabled(String s) {
    }

    public void onProviderEnabled(String s) {
    }
  }
}
