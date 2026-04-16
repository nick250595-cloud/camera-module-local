var exec = require('cordova/exec');

var CameraModule = {
  checkPermission: function(success, error) {
    exec(success, error, 'CameraModule', 'checkPermission', []);
  },

  requestPermission: function(success, error) {
    exec(success, error, 'CameraModule', 'requestPermission', []);
  },

  checkAndRequestPermission: function(success, error) {
    exec(success, error, 'CameraModule', 'checkAndRequestPermission', []);
  },

  startPreview: function(success, error) {
    exec(success, error, 'CameraModule', 'startPreview', []);
  },

  stopPreview: function(success, error) {
    exec(success, error, 'CameraModule', 'stopPreview', []);
  },

  takePhotoBase64: function(success, error) {
    exec(success, error, 'CameraModule', 'takePhotoBase64', []);
  },

  startBarcodeScan: function(success, error) {
    exec(success, error, 'CameraModule', 'startBarcodeScan', []);
  },

  stopBarcodeScan: function(success, error) {
    exec(success, error, 'CameraModule', 'stopBarcodeScan', []);
  }
};

window.CameraModule = CameraModule;
module.exports = CameraModule;
