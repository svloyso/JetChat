!function(t,e){"object"==typeof exports&&"object"==typeof module?module.exports=e(require("react")):"function"==typeof define&&define.amd?define(["react"],e):"object"==typeof exports?exports.ReactAutolink=e(require("react")):t.ReactAutolink=e(t.React)}(this,function(t){return function(t){function e(n){if(r[n])return r[n].exports;var o=r[n]={exports:{},id:n,loaded:!1};return t[n].call(o.exports,o,o.exports,e),o.loaded=!0,o.exports}var r={};return e.m=t,e.c=r,e.p="",e(0)}([function(t,e,r){"use strict";var n=function(t){return t&&t.__esModule?t["default"]:t},o=n(r(2)),u=n(r(1)),a=function(){var t=/((?:https?:\/\/)?(?:(?:[a-z0-9]?(?:[a-z0-9\-]{1,61}[a-z0-9])?\.[^\.|\s])+[a-z\.]*[a-z]+|(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3})(?::\d{1,5})*[a-z0-9.,_\/~#&=;%+?\-\\(\\)]*)/gi,e=function(t,e){return t.slice(0,e.length)===e};return{autolink:function(r){var n=void 0===arguments[1]?{}:arguments[1];return r?r.split(t).map(function(r){var a=r.match(t);if(a){var i=a[0],c=i.split("/");return""!==c[1]&&c[0].length<5?r:o.createElement("a",u({href:e(i,"http")?i:"http://"+i},n),i)}return r}):[]}}};t.exports=a()},function(t,e,r){"use strict";function n(t){if(null==t)throw new TypeError("Object.assign cannot be called with null or undefined");return Object(t)}t.exports=Object.assign||function(t,e){for(var r,o,u=n(t),a=1;a<arguments.length;a++){r=arguments[a],o=Object.keys(Object(r));for(var i=0;i<o.length;i++)u[o[i]]=r[o[i]]}return u}},function(e,r,n){e.exports=t}])});