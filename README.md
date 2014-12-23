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
```
Uses cordova 3.0 plugin infrastructure, see http://cordova.apache.org/blog/releases/2013/07/23/cordova-3

Install this plugin from a command line like:
```shell
cordova plugin add https://github.com/snowhow/cordova-plugin-gpstrack.git
```

This plugin starts GPS recording as an android **Service** and therefore keeps running, even if android kills the main cordova application. Communication with Cordova Web-app is done via Websockets.

