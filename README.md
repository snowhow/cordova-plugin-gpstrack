cordova-plugin-gpstrack
=======================

Apache Cordova plugin to record GPS positions in a background service on android.

Usage
-----

### Javascript

```javascript
var fileName = "/mnt/sdcard/mytrackfile_"+Date.now()+".json";
var precision = 30;   // GPS signal needs at least 30 meters precision
var tracker = new GPSTrack();
// FIXME: this is not working now, add callback to app
tracker.onlocationupdate = function(loc) {
  SH.map.addGPSTrackPoint(loc);
};
tracker.record(fileName, precision, function(res) {
  console.log("success for tracker");
}, function(err) { 
  console.log("error on tracker"); 
} );

```
Uses cordova 3.0 plugin infrastructure, see http://cordova.apache.org/blog/releases/2013/07/23/cordova-3

Install this plugin from a command line like:
```shell
cordova plugin add https://github.com/snowhow/cordova-plugin-gpstrack.git
```

This plugin starts GPS recording as an android **Service** and therefore keeps running, even if android kills the main cordova application.

