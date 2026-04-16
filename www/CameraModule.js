var exec = require('cordova/exec');

var CameraModule = {

  checkPermission: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'checkPermission', []);
    });
  },

  requestPermission: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'requestPermission', []);
    });
  },

  checkAndRequestPermission: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'checkAndRequestPermission', []);
    });
  },

  startPreview: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'startPreview', []);
    });
  },

  stopPreview: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'stopPreview', []);
    });
  },

  takePhotoBase64: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'takePhotoBase64', []);
    });
  },

  startBarcodeScan: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'startBarcodeScan', []);
    });
  },

  stopBarcodeScan: function () {
    return new Promise(function (resolve, reject) {
      exec(resolve, reject, 'CameraModule', 'stopBarcodeScan', []);
    });
  }
};

// 👇 MUY IMPORTANTE para OutSystems
window.CameraModule = CameraModule;

module.exports = CameraModule;
