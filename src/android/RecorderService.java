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
  private static final long MINIMUM_TIME_BETWEEN_UPDATES = 2000; // in Milliseconds

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
      minimumPrecision = intent.getFloatExtra("precision", 0);
      editor.putString("runningTrackFile", tf);
      editor.putFloat("runningPrecision", minimumPrecision);
      editor.commit();
    }
    // Intent bcRecI = new Intent(this, RecorderServiceBroadcastReceiver.class);
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
      // .addAction(android.R.drawable.ic_menu_camera, "Stop", pend);
    nm = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
    nm.notify(0, note.build());

    recording = true;
    Log.d(LOG_TAG, "recording in handleIntent");
    try {
      // FileWriter file = new FileWriter(trackFile);
      File trackFile = new File(tf).getParentFile();
      if (!trackFile.exists()) {
        trackFile.mkdirs();
        Log.d(LOG_TAG, "done creating path for trackfile: "+trackFile);
      }
      myWriter = new RandomAccessFile(tf, "rw");
      // Log.d(LOG_TAG, "going to create lockfile");
      // createLockFile();
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
    // mNM.cancel(NOTIFICATION);
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

  public void createLockFile() {
    Log.d(LOG_TAG, "creating lockfile ...");
    try {
      File file = new File(tf+".lock");
      Log.d(LOG_TAG, "creating lockfile "+file);
      file.createNewFile();
    } catch(Exception e) {
      Log.d(LOG_TAG, "io err: cannot create lockfile");
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
//      byte[] ipAddr = new byte[]{127, 0, 0, 1};
//      InetAddress addr = Inet4Address.getByAddress(ipAddr);
//      gpss = new GPSServer(new InetSocketAddress(addr, 8887));
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

  public String getTrackFilename() {
    return tf;
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

    private boolean firstGPSfix = false;

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
      if (location.getProvider().equals(LocationManager.GPS_PROVIDER) && firstGPSfix == false) {
        Log.d(LOG_TAG, "removed network LocationListener");
        Toast.makeText(RecorderService.this, "snowhow: on GPS logging now", Toast.LENGTH_SHORT).show();
        firstGPSfix = true;
        locationManager.removeUpdates(mnetll);
      }
      locations = sharedPref.getInt("count", 0);
      locations += 1;
      editor.putInt("count", locations);
      editor.commit();
      note.setContentText("Click to stop track recording ("+locations+" points).");
      try {
        String locString = "["+location.getLongitude()+","+location.getLatitude()+","+location.getAltitude()
              +","+location.getTime()+","+location.getAccuracy()+",\""+location.getProvider()+"\"]";
        Log.d(LOG_TAG, "GPSS is "+ gpss);
        int pointCount = sharedPref.getInt("count", 0);
        if (gpss != null) {
          gpss.sendString("{ \"type\": \"coords\", \"coords\": "+locString+", \"pointCount\": "+pointCount+"}");
        } else {
          startGPSS();
          Log.d(LOG_TAG, "started new GPSS");
          gpss.sendString("{ \"type\": \"coords\", \"coords\": "+locString+", \"pointCount\": "+pointCount+"}");
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
