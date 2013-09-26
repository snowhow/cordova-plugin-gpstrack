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

