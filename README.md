cordova-plugin-gpstrack
=======================

Apache Cordova plugin to record GPS positions in a background service on android.

Usage
-----

### Javascript

```javascript
var tracker = new GPSTrack();
tracker.onlocationupdate = function(loc) {
  SH.map.addGPSTrackPoint(loc);
};
tracker.record(fileName, function(res) {
  console.log("success for tracker");
}, function(err) { 
  console.log("error on tracker"); 
} );

```
Uses cordova 3.0 plugin infrastructure, see http://cordova.apache.org/blog/releases/2013/07/23/cordova-3

This plugin starts GPS recording as an android **Service** and therefore keeps running, even if android kills the main cordova application.

You need to modify your Androidmanifest.xml file for the service to work correctly
```xml
<service android:name="info.snowhow.plugin.RecorderService">
  <intent-filter>
      <action android:name="info.snowhow.plugin.GPSTrack" />
  </intent-filter>
</service>
<receiver android:name="info.snowhow.plugin.RecorderService$RecorderServiceBroadcastReceiver" />

```
