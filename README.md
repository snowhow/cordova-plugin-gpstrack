cordova-plugin-gpstrack
=======================

Apache Cordova plugin to record GPS positions in a background service on android.

Usage
-----

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

This plugin starts GPS recording as an android **Service** and therefor keeps running, even if android kills the main cordova application.


