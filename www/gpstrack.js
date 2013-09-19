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
 */
GPSTrack.prototype.record = function(track, succ, err) {
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
  exec(win, err, "GPSTrack", "record", [track]);
};


module.exports = GPSTrack;

