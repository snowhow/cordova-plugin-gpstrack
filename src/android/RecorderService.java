/*
 * MIT License
 * (c) 2013 bernd@snowhow.info
 *
*/
package info.snowhow.plugin;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

// import android.app.AlertDialog;
// import android.app.AlertDialog.Builder;


public class RecorderService extends Service {
  private static final String GPS_TRACK_VERSION = "0.3.0";

  private static final String LOG_TAG = "GPSTrack";
  private static final long MINIMUM_DISTANCE_CHANGE_FOR_UPDATES = 10; // in Meters
  private static final long MINIMUM_TIME_BETWEEN_UPDATES = 5000; // in Milliseconds
  private static final long MINIMUM_TIME_BETWEEN_UPDATES_FAST = 1000; // in Milliseconds
  private static final long SPEED_LIMIT = 5; // in meter per second

  protected float minimumPrecision = 0;
  protected long distanceChange = MINIMUM_DISTANCE_CHANGE_FOR_UPDATES;
  protected long updateTime = MINIMUM_TIME_BETWEEN_UPDATES;
  protected long updateTimeFast = MINIMUM_TIME_BETWEEN_UPDATES_FAST;
  protected long speedLimit = SPEED_LIMIT;
  protected int speedChangeDelay = 0;
  private static final long SPEED_CHANGE_COUNT = 5;

  protected boolean recording = false;
  protected boolean firstPoint = true;

  protected LocationManager locationManager;
  protected int locations = 0;
  public NotificationManager nm;
  public NotificationCompat.Builder note;
  protected RandomAccessFile myWriter;
  protected long start_ts = System.currentTimeMillis();

  protected MyLocationListener mgpsll, mnetll;
  protected String tf;
  protected String ifString = "snowhow_gpstrack_intent";
  protected int runningID;
  protected SharedPreferences sharedPref;
  protected SharedPreferences.Editor editor;
  public GPSServer gpss;
  protected Location lastLoc;
  protected boolean goingFast = false;
  protected boolean adaptiveRecording = false;
  public boolean gpsDisabled = false;
  protected String applicationName = "snowhow";


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
    editor.apply();
    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    Log.d(LOG_TAG, "locationManager initialized, starting intent");

    try {
      PackageManager packageManager = this.getPackageManager();
      PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
      applicationName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
        /* do nothing, fallback is used as name */
    }


    registerReceiver(RecorderServiceBroadcastReceiver, new IntentFilter(ifString));
    startGPSS();
  }

  private void showNoGPSAlert() {
    Log.i(LOG_TAG, "No GPS available --- send error msg via websocket");
    if (gpss != null) {
      gpss.sendString("{ \"type\": \"error\", \"msg\": \"gpsUnavailable\" }");
    }
//     AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
//     alertDialogBuilder.setMessage("GPS is disabled on your device. Would you like to enable it?")
//     .setCancelable(false)
//     .setPositiveButton("GPS Settings", new DialogInterface.OnClickListener() {
//       public void onClick(DialogInterface dialog, int id) {
//         Intent callGPSSettingIntent = new Intent(
//           android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//           callGPSSettingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//           startActivity(callGPSSettingIntent);
//         }
//     });
//     alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//       public void onClick(DialogInterface dialog, int id){
//         stopRecording();
//         dialog.cancel();
//       }
//     });
//     AlertDialog alert = alertDialogBuilder.create();
//     alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
//     alert.show();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(LOG_TAG, "Received start id " + startId + ": " + intent);
    if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
      if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        gpsDisabled = true;
        showNoGPSAlert();
      }
    }
    runningID = startId;
    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.

    if (intent == null) {
      tf = sharedPref.getString("runningTrackFile", "");
      Log.w(LOG_TAG, "Intent is null, trying to continue to write to file " + tf + " lm " + locationManager);
      minimumPrecision = sharedPref.getFloat("runningPrecision", 0);
      distanceChange = sharedPref.getLong("runningDistanceChange", MINIMUM_DISTANCE_CHANGE_FOR_UPDATES);
      updateTime = sharedPref.getLong("runningUpdateTime", MINIMUM_TIME_BETWEEN_UPDATES);
      updateTimeFast = sharedPref.getLong("runningUpdateTimeFast", MINIMUM_TIME_BETWEEN_UPDATES_FAST);
      speedLimit = sharedPref.getLong("runningSpeedLimit", SPEED_LIMIT);
      adaptiveRecording = sharedPref.getBoolean("adaptiveRecording", false);
      int count = sharedPref.getInt("count", 0);
      if (count > 0) {
        firstPoint = false;
      }
      if (tf == null || tf.equals("")) {
        Log.e(LOG_TAG, "No trackfile found ... exit clean here");
        cleanUp();
        return START_NOT_STICKY;
      }
    } else {
      tf = intent.getStringExtra("fileName");
      Log.d(LOG_TAG, "FILENAME for recording is " + tf);
      minimumPrecision = intent.getFloatExtra("precision", 0);
      distanceChange = intent.getLongExtra("distance_change", MINIMUM_DISTANCE_CHANGE_FOR_UPDATES);
      updateTime = intent.getLongExtra("update_time", MINIMUM_TIME_BETWEEN_UPDATES);
      updateTimeFast = intent.getLongExtra("update_time_fast", MINIMUM_TIME_BETWEEN_UPDATES_FAST);
      speedLimit = intent.getLongExtra("speed_limit", SPEED_LIMIT);
      adaptiveRecording = intent.getBooleanExtra("adaptiveRecording", false);
      editor.putString("runningTrackFile", tf);
      editor.putFloat("runningPrecision", minimumPrecision);
      editor.putLong("runningDistanceChange", distanceChange);
      editor.putLong("runningUpdateTime", updateTime);
      editor.putLong("runningUpdateTimeFast", updateTimeFast);
      editor.putLong("runningSpeedLimit", speedLimit);
      editor.putBoolean("adaptiveRecording", adaptiveRecording);
      editor.commit();
    }

    Intent cordovaMainIntent;
    try {
      PackageManager packageManager = this.getPackageManager();
      cordovaMainIntent = packageManager.getLaunchIntentForPackage(this.getPackageName());
      Log.d(LOG_TAG, "got cordovaMainIntent " + cordovaMainIntent);
      if (cordovaMainIntent == null) {
        throw new PackageManager.NameNotFoundException();
      }
    } catch (PackageManager.NameNotFoundException e) {
      cordovaMainIntent = new Intent();
    }
    PendingIntent pend = PendingIntent.getActivity(this, 0, cordovaMainIntent, 0);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ifString), 0);
    NotificationCompat.Action stop =
            new NotificationCompat.Action.Builder(android.R.drawable.ic_delete, "Stop recording", pendingIntent).build();
    note = new NotificationCompat.Builder(this)
            .setContentTitle(applicationName + " GPS tracking")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pend)
            .setContentText("No location yet.");
    note.addAction(stop);

    nm = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
    nm.notify(0, note.build());

    recording = true;
    Log.d(LOG_TAG, "recording in handleIntent");
    try {   // create directory first, if it does not exist
      File trackFile = new File(tf).getParentFile();
      if (!trackFile.exists()) {
        trackFile.mkdirs();
        Log.d(LOG_TAG, "done creating path for trackfile: " + trackFile);
      }
      Log.d(LOG_TAG, "going to create RandomAccessFile " + tf);
      myWriter = new RandomAccessFile(tf, "rw");
      if (intent != null) {   // start new file
        // myWriter.setLength(0);    // delete all contents from file
        String trackName = intent.getStringExtra("trackName");
        String trackHead = initTrack(trackName).toString();
        myWriter.write(trackHead.getBytes());
        // we want to write JSON manually for streamed writing
        myWriter.seek(myWriter.length() - 1);
        myWriter.write(",\"coordinates\":[]}".getBytes());
      }
    } catch (IOException e) {
      Log.d(LOG_TAG, "io error. cannot write to file " + tf);
    }
    if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
      if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        Log.d(LOG_TAG, "GPS_PROVIDER not enabled, gpsDiabled = true ...");
        gpsDisabled = true;
        showNoGPSAlert();
        return START_NOT_STICKY;
      }
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Log.d(LOG_TAG, "No access to GPS_PROVIDER, gpsDiabled = true ...");
        gpsDisabled = true;
        showNoGPSAlert();
        // cannot request permissions as we are in a service here
        return START_NOT_STICKY;
      }
      Log.d(LOG_TAG, "access to GPS_PROVIDER granted, start...");
      gpsDisabled = false;
      locationManager.requestLocationUpdates(
              LocationManager.GPS_PROVIDER,
              updateTime,
              distanceChange,
              mgpsll
      );
    } else {
      gpsDisabled = true;
      Log.d(LOG_TAG, "NO GPS_PROVIDER in AllProvides .... show error");
      showNoGPSAlert();
    }
    if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
      Log.d(LOG_TAG, "access to NETWORK_PROVIDER granted, start...");
      locationManager.requestLocationUpdates(
              LocationManager.NETWORK_PROVIDER,
              updateTime,
              distanceChange,
              mnetll
      );
    }
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override
  public void onDestroy() {
    // Cancel the persistent notification.
    nm.cancel(0);
    // Tell the user we stopped.
    Toast.makeText(this, applicationName + ": GPS logging stopped", Toast.LENGTH_SHORT).show();
  }


  public void cleanUp() {
    try {
      gpss.sendString("{ \"type\": \"status\", \"msg\": \"disconnect\" }");
      Log.d(LOG_TAG, "stopping gpss ...");
      gpss.stop(3000);
      Log.d(LOG_TAG, "stopped gpss ...");
    } catch (Exception e) {
      Log.d(LOG_TAG, "unable to stop gpss");
      Log.d(LOG_TAG, "stack", e);
    }
    locationManager.removeUpdates(mgpsll);
    locationManager.removeUpdates(mnetll);
    locationManager = null;
    // SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
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
      Log.d(LOG_TAG, "deleting file " + tf);
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
      prop.put("name", trackName);
      prop.put("givenName", trackName);
      prop.put("localname", "snowhow_" + start_ts + ".json");
      prop.put("rec_type", "gpstrack_plugin");
      prop.put("plugin_version", GPS_TRACK_VERSION);
      obj.put("type", "LineString");
      obj.put("properties", prop);
    } catch (JSONException e) {
      Log.d(LOG_TAG, "jsonObject error: " + e);
    }
    return obj;
  }

  public void startGPSS() {
    Log.d(LOG_TAG, "starting WS Server on Port 8887");
    try {
      gpss = new GPSServer(8887, this);
      gpss.start();
      Log.d(LOG_TAG, "starting WS Server on : " + gpss.getAddress());
      Log.d(LOG_TAG, "Gpssss is " + gpss);
      gpss.sendString("{ \"type\": \"start\", \"msg\": \"start on " + gpss.getAddress() + "\" }");
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

  public JSONObject getTrack() {
    JSONObject t = new JSONObject();
    try {
      File file = new File(tf);
      FileInputStream fis = new FileInputStream(file);
      byte[] data = new byte[(int) file.length()];
      fis.read(data);
      fis.close();
      String str = new String(data, "UTF-8");
      t = new JSONObject(str);
    } catch (Exception e) {
      Log.d(LOG_TAG, "getTrack:", e);
    }
    return t;
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

    MyLocationListener() {
      Log.d(LOG_TAG, "MyLocationListener started successfully");
    }

    public void onLocationChanged(Location location) {
      float speed = location.getSpeed();
      String speedType = "gps";
      if (!recording) {
        return;
      }
      if (minimumPrecision > 0 && location.getAccuracy() > minimumPrecision) {
        Log.d(LOG_TAG, "precision of position not good enough: " + location.getAccuracy() + "m, required: " + minimumPrecision + "m");
        return;
      }
      if (location.getProvider().equals(LocationManager.GPS_PROVIDER) && !firstGPSfix) {
        Log.d(LOG_TAG, "removed network LocationListener");
        Toast.makeText(RecorderService.this, applicationName + ": GPS logging started", Toast.LENGTH_SHORT).show();
        firstGPSfix = true;
        locationManager.removeUpdates(mnetll);
      }
      Log.d(LOG_TAG, "adaptiveRecording: " + adaptiveRecording);
      if (lastLoc != null && adaptiveRecording) {
        if (speed == 0) {
          long timeDiff = (location.getTime() - lastLoc.getTime()) / 1000;
          speed = lastLoc.distanceTo(location) / timeDiff;
          speedType = "calc";
          Log.d(LOG_TAG, "speed calc from lastLoc " + timeDiff + " makes speed " + speed);
        }
        if (speed > SPEED_LIMIT && !goingFast) {  // faster than 5 m/s, switch to faster GPS interval
          speedChangeDelay++;
          if (speedChangeDelay > SPEED_CHANGE_COUNT) {
            speedChangeDelay = 0;
            Log.d(LOG_TAG, "travelling fast --- switch to fast update");
            if (gpss != null) {
              gpss.sendString("{ \"type\": \"status\", \"msg\": \"fastUpdate\", \"interval\": "
                      + updateTimeFast + "}");
            }
            locationManager.removeUpdates(mgpsll);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    updateTimeFast,
                    distanceChange,
                    mgpsll
            );
            goingFast = true;
          }
        } else if (speed <= SPEED_LIMIT && goingFast) {
          speedChangeDelay++;
          if (speedChangeDelay > SPEED_CHANGE_COUNT) {
            speedChangeDelay = 0;
            Log.d(LOG_TAG, "travelling slow --- switch to slow update");
            if (gpss != null) {
              gpss.sendString("{ \"type\": \"status\", \"msg\": \"slowUpdate\", \"interval\": "
                  +updateTime+"}");
            }
            locationManager.removeUpdates(mgpsll);
            locationManager.requestLocationUpdates(
              LocationManager.GPS_PROVIDER,
              updateTime,
              distanceChange,
              mgpsll
            );
            goingFast = false;
          }
        }
      }
      lastLoc = location;
      locations = sharedPref.getInt("count", 0);
      locations += 1;
      editor.putInt("count", locations);
      editor.commit();
      long gpsInterval = (goingFast) ? (updateTimeFast/1000) : (updateTime/1000);
      note.setContentText("recording ("+locations+" points, "+gpsInterval+" secs tracking interval).");
      try {
        String locString = "["+location.getLongitude()+","+location.getLatitude()+","
              +Math.round(location.getAltitude())
              +","+location.getTime()+","+location.getAccuracy()+",\""+location.getProvider()+"\""
              +","+gpsInterval+","+speed+","+adaptiveRecording+"]";
        int pointCount = sharedPref.getInt("count", 0);
        String gpssString = "{\"type\":\"coords\",\"coords\":"+locString
          +",\"pointCount\":"+pointCount+",\"speed\":"+speed+",\"speedType\":\""+speedType+"\","
          +"\"interval\":"+gpsInterval
          +",\"bearing\":"+Math.round(location.getBearing())
          +"}";
        if (gpss != null) {
          gpss.sendString(gpssString);
        } else {
          startGPSS();
          Log.d(LOG_TAG, "started new GPSS");
          gpss.sendString(gpssString);
        }
        locString += "]}";
        myWriter.seek(myWriter.length()-2); // remove last 2 byte
        if (firstPoint) {
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
