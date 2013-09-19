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

    var tracker = new GPSTrack();
    tracker.onlocationupdate = function(loc) {
      SH.map.addGPSTrackPoint(loc);
    };
    tracker.record(fileName, function(res) {
      console.log("success for tracker");
    }, function(err) { console.log("error on tracker"); } );

Full Example
------------
    
    // TODO
