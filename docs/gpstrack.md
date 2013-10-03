---
license:  MIT License
 (c) 2013 bernd@snowhow.info
 
---

gpstrack
====================

Record a GPS track using an android service. Saves a GeoJSON Linestring.

Supported Platforms
-------------------

- Android

Quick Example
-------------

    // record track

    var fileName = "/mnt/sdcard/mytrackfile_"+Date.now()+".json";
    var precision = 30;   // GPS signal needs at least 30 meters precision
    var tracker = new GPSTrack();
    // FIXME: add callback to app
    tracker.onlocationupdate = function(loc) {
      SH.map.addGPSTrackPoint(loc);
    };
    tracker.record(fileName, precision, function(res) {
      console.log("success for tracker");
    }, function(err) { console.log("error on tracker"); } );

Full Example
------------
    
    // TODO
