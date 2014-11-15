/*
 * MIT License
 * (c) 2013 bernd@snowhow.info
 *
*/

var exec = require('cordova/exec');

/**
 * record a GPSTrack to sdcard
 */

var GPSTrack = function() {
  this.onlocationupdate = null;
};


/**
 * record a track
 *
 * @param {String} track       Destination file
 * @param {Object} options     multiple options
 * @param {double} options.precision    minimum precision of location (horizontal)
 * @param {Boolean} options.adaptiveRecording    switch recording interval based on ground speed
 * @param {String} options.trackName    internal trackname
 * @param {function} succ   success callback function
 * @param {function} err    error callback function
 */
GPSTrack.prototype.record = function(track, options, succ, err) {
  var self = this;
  var win = function(result) {
    if (result.location) {
      if (self.onlocationupdate) {
        self.onlocationupdate(result);
      }
    } else if (succ) {
      succ(result);
    }
  };
  var precision = options.precision || 35,
    adaptiveRecording = options.adaptiveRecording || false,
    trackName = options.trackName || "snowhowtrack";
  exec(win, err, "GPSTrack", "record", [track, precision, adaptiveRecording, trackName]);
};

/**
 * subscribe to a track running recording
 *
 */
GPSTrack.prototype.listen = function(succ, err) {
  var self = this;
  var win = function(result) {
    if (result.location) {
      if (self.onlocationupdate) {
        self.onlocationupdate(result);
      }
    } else if (succ) {
      succ(result);
    }
  };
  exec(win, err, "GPSTrack", "listen", []);
};


module.exports = GPSTrack;

