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

import android.content.Intent;

public class GPSTrack extends CordovaPlugin {

  private static final String LOG_TAG = "GPSTrack";

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (action.equals("record")) {
      String trackFile = args.getString(0);
      final JSONObject details = args.getJSONObject(1);
      final boolean adaptiveRecording = (boolean) args.getBoolean(2);
      final String trackName = args.getString(3);

      final float precision = (float) details.getDouble("precision");
      final long distanceChange = details.getLong("distance_change");
      final long updateTime = details.getLong("update_time");
      final long updateTimeFast = details.getLong("update_time_fast");
      final long speedLimit = details.getLong("speed_limit");

      if (trackFile.indexOf("file:///") > -1) {
          trackFile = trackFile.substring(7);
      }
      record(trackFile, precision, distanceChange, updateTime, updateTimeFast, speedLimit, adaptiveRecording, trackName);
      JSONObject obj = new JSONObject();
      obj.put("test", 1);
      PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
      res.setKeepCallback(true);
      callbackContext.sendPluginResult(res);
      Log.d(LOG_TAG, "done sending to webapp");
//       cordova.getActivity().runOnUiThread(new Runnable() {
//         @Override
//         public void run() {
//           GPSTrack.this.record(trackFile);
//           // callbackContext.success();
//         }
//       });
    } else if (action.equals("listen")) {
      JSONObject obj = new JSONObject();
      obj.put("test", 1);
      PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
      res.setKeepCallback(true);
      callbackContext.sendPluginResult(res);
      Log.d(LOG_TAG, "done sending to webapp");
    } else {
      return false;
    }
    return true;
  }

  public void record(final String trackFile, final float precision, final long distanceChange, final long updateTime, final long updateTimeFast, final long speedLimit, final boolean adaptiveRecording, 
      String trackName) {
    Context context = cordova.getActivity().getApplicationContext();
    Intent intent = new Intent(context, RecorderService.class);
    // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra("fileName", trackFile);
    intent.putExtra("precision", precision);
    if (distanceChange > 0) {
        intent.putExtra("distance_change", distanceChange);
    }
    if (updateTime > 0) {
        intent.putExtra("update_time", updateTime);
    }
    if (updateTimeFast > 0) {
        intent.putExtra("update_time_fast", updateTimeFast);
    }
    if (speedLimit > 0) {
        intent.putExtra("speed_limit", speedLimit);
    }
    intent.putExtra("adaptiveRecording", adaptiveRecording);
    intent.putExtra("trackName", trackName);
    Log.d(LOG_TAG, "in record ... should start intent now");
    context.startService(intent);
  }

}
