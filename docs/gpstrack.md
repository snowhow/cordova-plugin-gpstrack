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

    tracker.record(fileName, { precision: precision }, function(res) {
      console.log("GPStracker record: success for tracker");
    }, function(err) { console.log("error on tracker"); } );
    tracker.listen(function(succ) {
      // make sure websocket server has time to start up
      setTimeout(function() {
        setupWebSocket();
      }, 3000);
    }, function(err) {
      console.log("GPStracker listen: error in listener "+err);
    });

    function setupWebSocket() {
      var ws = new WebSocket('ws://localhost:8887/snowhow');
      setTimeout(function() {
        if (ws.readyState === 1) {
          ws.send("getFilename");
        }
      }, 3000);
      ws.onmessage = function (evt) {
        var data;
        try {
          data = JSON.parse(evt.data);
        } catch (e) {
          console.log("illegal json data via websocket", evt.data);
        }
        handleWSResopnse(data);
      };
    }

Full Example
------------
    
    // TODO
