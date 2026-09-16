import { createRequire as __createRequire } from 'module'; const require = __createRequire(import.meta.url);
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __require = /* @__PURE__ */ ((x) => typeof require !== "undefined" ? require : typeof Proxy !== "undefined" ? new Proxy(x, {
  get: (a, b) => (typeof require !== "undefined" ? require : a)[b]
}) : x)(function(x) {
  if (typeof require !== "undefined") return require.apply(this, arguments);
  throw Error('Dynamic require of "' + x + '" is not supported');
});
var __commonJS = (cb, mod) => function __require2() {
  return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
};
var __export = (target, all3) => {
  for (var name in all3)
    __defProp(target, name, { get: all3[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// ../shared/core/node_modules/delayed-stream/lib/delayed_stream.js
var require_delayed_stream = __commonJS({
  "../shared/core/node_modules/delayed-stream/lib/delayed_stream.js"(exports, module) {
    var Stream = __require("stream").Stream;
    var util4 = __require("util");
    module.exports = DelayedStream;
    function DelayedStream() {
      this.source = null;
      this.dataSize = 0;
      this.maxDataSize = 1024 * 1024;
      this.pauseStream = true;
      this._maxDataSizeExceeded = false;
      this._released = false;
      this._bufferedEvents = [];
    }
    util4.inherits(DelayedStream, Stream);
    DelayedStream.create = function(source, options) {
      var delayedStream = new this();
      options = options || {};
      for (var option in options) {
        delayedStream[option] = options[option];
      }
      delayedStream.source = source;
      var realEmit = source.emit;
      source.emit = function() {
        delayedStream._handleEmit(arguments);
        return realEmit.apply(source, arguments);
      };
      source.on("error", function() {
      });
      if (delayedStream.pauseStream) {
        source.pause();
      }
      return delayedStream;
    };
    Object.defineProperty(DelayedStream.prototype, "readable", {
      configurable: true,
      enumerable: true,
      get: function() {
        return this.source.readable;
      }
    });
    DelayedStream.prototype.setEncoding = function() {
      return this.source.setEncoding.apply(this.source, arguments);
    };
    DelayedStream.prototype.resume = function() {
      if (!this._released) {
        this.release();
      }
      this.source.resume();
    };
    DelayedStream.prototype.pause = function() {
      this.source.pause();
    };
    DelayedStream.prototype.release = function() {
      this._released = true;
      this._bufferedEvents.forEach(function(args) {
        this.emit.apply(this, args);
      }.bind(this));
      this._bufferedEvents = [];
    };
    DelayedStream.prototype.pipe = function() {
      var r = Stream.prototype.pipe.apply(this, arguments);
      this.resume();
      return r;
    };
    DelayedStream.prototype._handleEmit = function(args) {
      if (this._released) {
        this.emit.apply(this, args);
        return;
      }
      if (args[0] === "data") {
        this.dataSize += args[1].length;
        this._checkIfMaxDataSizeExceeded();
      }
      this._bufferedEvents.push(args);
    };
    DelayedStream.prototype._checkIfMaxDataSizeExceeded = function() {
      if (this._maxDataSizeExceeded) {
        return;
      }
      if (this.dataSize <= this.maxDataSize) {
        return;
      }
      this._maxDataSizeExceeded = true;
      var message = "DelayedStream#maxDataSize of " + this.maxDataSize + " bytes exceeded.";
      this.emit("error", new Error(message));
    };
  }
});

// ../shared/core/node_modules/combined-stream/lib/combined_stream.js
var require_combined_stream = __commonJS({
  "../shared/core/node_modules/combined-stream/lib/combined_stream.js"(exports, module) {
    var util4 = __require("util");
    var Stream = __require("stream").Stream;
    var DelayedStream = require_delayed_stream();
    module.exports = CombinedStream;
    function CombinedStream() {
      this.writable = false;
      this.readable = true;
      this.dataSize = 0;
      this.maxDataSize = 2 * 1024 * 1024;
      this.pauseStreams = true;
      this._released = false;
      this._streams = [];
      this._currentStream = null;
      this._insideLoop = false;
      this._pendingNext = false;
    }
    util4.inherits(CombinedStream, Stream);
    CombinedStream.create = function(options) {
      var combinedStream = new this();
      options = options || {};
      for (var option in options) {
        combinedStream[option] = options[option];
      }
      return combinedStream;
    };
    CombinedStream.isStreamLike = function(stream4) {
      return typeof stream4 !== "function" && typeof stream4 !== "string" && typeof stream4 !== "boolean" && typeof stream4 !== "number" && !Buffer.isBuffer(stream4);
    };
    CombinedStream.prototype.append = function(stream4) {
      var isStreamLike = CombinedStream.isStreamLike(stream4);
      if (isStreamLike) {
        if (!(stream4 instanceof DelayedStream)) {
          var newStream = DelayedStream.create(stream4, {
            maxDataSize: Infinity,
            pauseStream: this.pauseStreams
          });
          stream4.on("data", this._checkDataSize.bind(this));
          stream4 = newStream;
        }
        this._handleErrors(stream4);
        if (this.pauseStreams) {
          stream4.pause();
        }
      }
      this._streams.push(stream4);
      return this;
    };
    CombinedStream.prototype.pipe = function(dest, options) {
      Stream.prototype.pipe.call(this, dest, options);
      this.resume();
      return dest;
    };
    CombinedStream.prototype._getNext = function() {
      this._currentStream = null;
      if (this._insideLoop) {
        this._pendingNext = true;
        return;
      }
      this._insideLoop = true;
      try {
        do {
          this._pendingNext = false;
          this._realGetNext();
        } while (this._pendingNext);
      } finally {
        this._insideLoop = false;
      }
    };
    CombinedStream.prototype._realGetNext = function() {
      var stream4 = this._streams.shift();
      if (typeof stream4 == "undefined") {
        this.end();
        return;
      }
      if (typeof stream4 !== "function") {
        this._pipeNext(stream4);
        return;
      }
      var getStream = stream4;
      getStream(function(stream5) {
        var isStreamLike = CombinedStream.isStreamLike(stream5);
        if (isStreamLike) {
          stream5.on("data", this._checkDataSize.bind(this));
          this._handleErrors(stream5);
        }
        this._pipeNext(stream5);
      }.bind(this));
    };
    CombinedStream.prototype._pipeNext = function(stream4) {
      this._currentStream = stream4;
      var isStreamLike = CombinedStream.isStreamLike(stream4);
      if (isStreamLike) {
        stream4.on("end", this._getNext.bind(this));
        stream4.pipe(this, { end: false });
        return;
      }
      var value = stream4;
      this.write(value);
      this._getNext();
    };
    CombinedStream.prototype._handleErrors = function(stream4) {
      var self2 = this;
      stream4.on("error", function(err) {
        self2._emitError(err);
      });
    };
    CombinedStream.prototype.write = function(data) {
      this.emit("data", data);
    };
    CombinedStream.prototype.pause = function() {
      if (!this.pauseStreams) {
        return;
      }
      if (this.pauseStreams && this._currentStream && typeof this._currentStream.pause == "function") this._currentStream.pause();
      this.emit("pause");
    };
    CombinedStream.prototype.resume = function() {
      if (!this._released) {
        this._released = true;
        this.writable = true;
        this._getNext();
      }
      if (this.pauseStreams && this._currentStream && typeof this._currentStream.resume == "function") this._currentStream.resume();
      this.emit("resume");
    };
    CombinedStream.prototype.end = function() {
      this._reset();
      this.emit("end");
    };
    CombinedStream.prototype.destroy = function() {
      this._reset();
      this.emit("close");
    };
    CombinedStream.prototype._reset = function() {
      this.writable = false;
      this._streams = [];
      this._currentStream = null;
    };
    CombinedStream.prototype._checkDataSize = function() {
      this._updateDataSize();
      if (this.dataSize <= this.maxDataSize) {
        return;
      }
      var message = "DelayedStream#maxDataSize of " + this.maxDataSize + " bytes exceeded.";
      this._emitError(new Error(message));
    };
    CombinedStream.prototype._updateDataSize = function() {
      this.dataSize = 0;
      var self2 = this;
      this._streams.forEach(function(stream4) {
        if (!stream4.dataSize) {
          return;
        }
        self2.dataSize += stream4.dataSize;
      });
      if (this._currentStream && this._currentStream.dataSize) {
        this.dataSize += this._currentStream.dataSize;
      }
    };
    CombinedStream.prototype._emitError = function(err) {
      this._reset();
      this.emit("error", err);
    };
  }
});

// ../shared/core/node_modules/mime-db/db.json
var require_db = __commonJS({
  "../shared/core/node_modules/mime-db/db.json"(exports, module) {
    module.exports = {
      "application/1d-interleaved-parityfec": {
        source: "iana"
      },
      "application/3gpdash-qoe-report+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/3gpp-ims+xml": {
        source: "iana",
        compressible: true
      },
      "application/3gpphal+json": {
        source: "iana",
        compressible: true
      },
      "application/3gpphalforms+json": {
        source: "iana",
        compressible: true
      },
      "application/a2l": {
        source: "iana"
      },
      "application/ace+cbor": {
        source: "iana"
      },
      "application/activemessage": {
        source: "iana"
      },
      "application/activity+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-costmap+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-costmapfilter+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-directory+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-endpointcost+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-endpointcostparams+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-endpointprop+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-endpointpropparams+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-error+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-networkmap+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-networkmapfilter+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-updatestreamcontrol+json": {
        source: "iana",
        compressible: true
      },
      "application/alto-updatestreamparams+json": {
        source: "iana",
        compressible: true
      },
      "application/aml": {
        source: "iana"
      },
      "application/andrew-inset": {
        source: "iana",
        extensions: ["ez"]
      },
      "application/applefile": {
        source: "iana"
      },
      "application/applixware": {
        source: "apache",
        extensions: ["aw"]
      },
      "application/at+jwt": {
        source: "iana"
      },
      "application/atf": {
        source: "iana"
      },
      "application/atfx": {
        source: "iana"
      },
      "application/atom+xml": {
        source: "iana",
        compressible: true,
        extensions: ["atom"]
      },
      "application/atomcat+xml": {
        source: "iana",
        compressible: true,
        extensions: ["atomcat"]
      },
      "application/atomdeleted+xml": {
        source: "iana",
        compressible: true,
        extensions: ["atomdeleted"]
      },
      "application/atomicmail": {
        source: "iana"
      },
      "application/atomsvc+xml": {
        source: "iana",
        compressible: true,
        extensions: ["atomsvc"]
      },
      "application/atsc-dwd+xml": {
        source: "iana",
        compressible: true,
        extensions: ["dwd"]
      },
      "application/atsc-dynamic-event-message": {
        source: "iana"
      },
      "application/atsc-held+xml": {
        source: "iana",
        compressible: true,
        extensions: ["held"]
      },
      "application/atsc-rdt+json": {
        source: "iana",
        compressible: true
      },
      "application/atsc-rsat+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rsat"]
      },
      "application/atxml": {
        source: "iana"
      },
      "application/auth-policy+xml": {
        source: "iana",
        compressible: true
      },
      "application/bacnet-xdd+zip": {
        source: "iana",
        compressible: false
      },
      "application/batch-smtp": {
        source: "iana"
      },
      "application/bdoc": {
        compressible: false,
        extensions: ["bdoc"]
      },
      "application/beep+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/calendar+json": {
        source: "iana",
        compressible: true
      },
      "application/calendar+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xcs"]
      },
      "application/call-completion": {
        source: "iana"
      },
      "application/cals-1840": {
        source: "iana"
      },
      "application/captive+json": {
        source: "iana",
        compressible: true
      },
      "application/cbor": {
        source: "iana"
      },
      "application/cbor-seq": {
        source: "iana"
      },
      "application/cccex": {
        source: "iana"
      },
      "application/ccmp+xml": {
        source: "iana",
        compressible: true
      },
      "application/ccxml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["ccxml"]
      },
      "application/cdfx+xml": {
        source: "iana",
        compressible: true,
        extensions: ["cdfx"]
      },
      "application/cdmi-capability": {
        source: "iana",
        extensions: ["cdmia"]
      },
      "application/cdmi-container": {
        source: "iana",
        extensions: ["cdmic"]
      },
      "application/cdmi-domain": {
        source: "iana",
        extensions: ["cdmid"]
      },
      "application/cdmi-object": {
        source: "iana",
        extensions: ["cdmio"]
      },
      "application/cdmi-queue": {
        source: "iana",
        extensions: ["cdmiq"]
      },
      "application/cdni": {
        source: "iana"
      },
      "application/cea": {
        source: "iana"
      },
      "application/cea-2018+xml": {
        source: "iana",
        compressible: true
      },
      "application/cellml+xml": {
        source: "iana",
        compressible: true
      },
      "application/cfw": {
        source: "iana"
      },
      "application/city+json": {
        source: "iana",
        compressible: true
      },
      "application/clr": {
        source: "iana"
      },
      "application/clue+xml": {
        source: "iana",
        compressible: true
      },
      "application/clue_info+xml": {
        source: "iana",
        compressible: true
      },
      "application/cms": {
        source: "iana"
      },
      "application/cnrp+xml": {
        source: "iana",
        compressible: true
      },
      "application/coap-group+json": {
        source: "iana",
        compressible: true
      },
      "application/coap-payload": {
        source: "iana"
      },
      "application/commonground": {
        source: "iana"
      },
      "application/conference-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/cose": {
        source: "iana"
      },
      "application/cose-key": {
        source: "iana"
      },
      "application/cose-key-set": {
        source: "iana"
      },
      "application/cpl+xml": {
        source: "iana",
        compressible: true,
        extensions: ["cpl"]
      },
      "application/csrattrs": {
        source: "iana"
      },
      "application/csta+xml": {
        source: "iana",
        compressible: true
      },
      "application/cstadata+xml": {
        source: "iana",
        compressible: true
      },
      "application/csvm+json": {
        source: "iana",
        compressible: true
      },
      "application/cu-seeme": {
        source: "apache",
        extensions: ["cu"]
      },
      "application/cwt": {
        source: "iana"
      },
      "application/cybercash": {
        source: "iana"
      },
      "application/dart": {
        compressible: true
      },
      "application/dash+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mpd"]
      },
      "application/dash-patch+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mpp"]
      },
      "application/dashdelta": {
        source: "iana"
      },
      "application/davmount+xml": {
        source: "iana",
        compressible: true,
        extensions: ["davmount"]
      },
      "application/dca-rft": {
        source: "iana"
      },
      "application/dcd": {
        source: "iana"
      },
      "application/dec-dx": {
        source: "iana"
      },
      "application/dialog-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/dicom": {
        source: "iana"
      },
      "application/dicom+json": {
        source: "iana",
        compressible: true
      },
      "application/dicom+xml": {
        source: "iana",
        compressible: true
      },
      "application/dii": {
        source: "iana"
      },
      "application/dit": {
        source: "iana"
      },
      "application/dns": {
        source: "iana"
      },
      "application/dns+json": {
        source: "iana",
        compressible: true
      },
      "application/dns-message": {
        source: "iana"
      },
      "application/docbook+xml": {
        source: "apache",
        compressible: true,
        extensions: ["dbk"]
      },
      "application/dots+cbor": {
        source: "iana"
      },
      "application/dskpp+xml": {
        source: "iana",
        compressible: true
      },
      "application/dssc+der": {
        source: "iana",
        extensions: ["dssc"]
      },
      "application/dssc+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xdssc"]
      },
      "application/dvcs": {
        source: "iana"
      },
      "application/ecmascript": {
        source: "iana",
        compressible: true,
        extensions: ["es", "ecma"]
      },
      "application/edi-consent": {
        source: "iana"
      },
      "application/edi-x12": {
        source: "iana",
        compressible: false
      },
      "application/edifact": {
        source: "iana",
        compressible: false
      },
      "application/efi": {
        source: "iana"
      },
      "application/elm+json": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/elm+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.cap+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/emergencycalldata.comment+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.control+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.deviceinfo+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.ecall.msd": {
        source: "iana"
      },
      "application/emergencycalldata.providerinfo+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.serviceinfo+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.subscriberinfo+xml": {
        source: "iana",
        compressible: true
      },
      "application/emergencycalldata.veds+xml": {
        source: "iana",
        compressible: true
      },
      "application/emma+xml": {
        source: "iana",
        compressible: true,
        extensions: ["emma"]
      },
      "application/emotionml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["emotionml"]
      },
      "application/encaprtp": {
        source: "iana"
      },
      "application/epp+xml": {
        source: "iana",
        compressible: true
      },
      "application/epub+zip": {
        source: "iana",
        compressible: false,
        extensions: ["epub"]
      },
      "application/eshop": {
        source: "iana"
      },
      "application/exi": {
        source: "iana",
        extensions: ["exi"]
      },
      "application/expect-ct-report+json": {
        source: "iana",
        compressible: true
      },
      "application/express": {
        source: "iana",
        extensions: ["exp"]
      },
      "application/fastinfoset": {
        source: "iana"
      },
      "application/fastsoap": {
        source: "iana"
      },
      "application/fdt+xml": {
        source: "iana",
        compressible: true,
        extensions: ["fdt"]
      },
      "application/fhir+json": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/fhir+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/fido.trusted-apps+json": {
        compressible: true
      },
      "application/fits": {
        source: "iana"
      },
      "application/flexfec": {
        source: "iana"
      },
      "application/font-sfnt": {
        source: "iana"
      },
      "application/font-tdpfr": {
        source: "iana",
        extensions: ["pfr"]
      },
      "application/font-woff": {
        source: "iana",
        compressible: false
      },
      "application/framework-attributes+xml": {
        source: "iana",
        compressible: true
      },
      "application/geo+json": {
        source: "iana",
        compressible: true,
        extensions: ["geojson"]
      },
      "application/geo+json-seq": {
        source: "iana"
      },
      "application/geopackage+sqlite3": {
        source: "iana"
      },
      "application/geoxacml+xml": {
        source: "iana",
        compressible: true
      },
      "application/gltf-buffer": {
        source: "iana"
      },
      "application/gml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["gml"]
      },
      "application/gpx+xml": {
        source: "apache",
        compressible: true,
        extensions: ["gpx"]
      },
      "application/gxf": {
        source: "apache",
        extensions: ["gxf"]
      },
      "application/gzip": {
        source: "iana",
        compressible: false,
        extensions: ["gz"]
      },
      "application/h224": {
        source: "iana"
      },
      "application/held+xml": {
        source: "iana",
        compressible: true
      },
      "application/hjson": {
        extensions: ["hjson"]
      },
      "application/http": {
        source: "iana"
      },
      "application/hyperstudio": {
        source: "iana",
        extensions: ["stk"]
      },
      "application/ibe-key-request+xml": {
        source: "iana",
        compressible: true
      },
      "application/ibe-pkg-reply+xml": {
        source: "iana",
        compressible: true
      },
      "application/ibe-pp-data": {
        source: "iana"
      },
      "application/iges": {
        source: "iana"
      },
      "application/im-iscomposing+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/index": {
        source: "iana"
      },
      "application/index.cmd": {
        source: "iana"
      },
      "application/index.obj": {
        source: "iana"
      },
      "application/index.response": {
        source: "iana"
      },
      "application/index.vnd": {
        source: "iana"
      },
      "application/inkml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["ink", "inkml"]
      },
      "application/iotp": {
        source: "iana"
      },
      "application/ipfix": {
        source: "iana",
        extensions: ["ipfix"]
      },
      "application/ipp": {
        source: "iana"
      },
      "application/isup": {
        source: "iana"
      },
      "application/its+xml": {
        source: "iana",
        compressible: true,
        extensions: ["its"]
      },
      "application/java-archive": {
        source: "apache",
        compressible: false,
        extensions: ["jar", "war", "ear"]
      },
      "application/java-serialized-object": {
        source: "apache",
        compressible: false,
        extensions: ["ser"]
      },
      "application/java-vm": {
        source: "apache",
        compressible: false,
        extensions: ["class"]
      },
      "application/javascript": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["js", "mjs"]
      },
      "application/jf2feed+json": {
        source: "iana",
        compressible: true
      },
      "application/jose": {
        source: "iana"
      },
      "application/jose+json": {
        source: "iana",
        compressible: true
      },
      "application/jrd+json": {
        source: "iana",
        compressible: true
      },
      "application/jscalendar+json": {
        source: "iana",
        compressible: true
      },
      "application/json": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["json", "map"]
      },
      "application/json-patch+json": {
        source: "iana",
        compressible: true
      },
      "application/json-seq": {
        source: "iana"
      },
      "application/json5": {
        extensions: ["json5"]
      },
      "application/jsonml+json": {
        source: "apache",
        compressible: true,
        extensions: ["jsonml"]
      },
      "application/jwk+json": {
        source: "iana",
        compressible: true
      },
      "application/jwk-set+json": {
        source: "iana",
        compressible: true
      },
      "application/jwt": {
        source: "iana"
      },
      "application/kpml-request+xml": {
        source: "iana",
        compressible: true
      },
      "application/kpml-response+xml": {
        source: "iana",
        compressible: true
      },
      "application/ld+json": {
        source: "iana",
        compressible: true,
        extensions: ["jsonld"]
      },
      "application/lgr+xml": {
        source: "iana",
        compressible: true,
        extensions: ["lgr"]
      },
      "application/link-format": {
        source: "iana"
      },
      "application/load-control+xml": {
        source: "iana",
        compressible: true
      },
      "application/lost+xml": {
        source: "iana",
        compressible: true,
        extensions: ["lostxml"]
      },
      "application/lostsync+xml": {
        source: "iana",
        compressible: true
      },
      "application/lpf+zip": {
        source: "iana",
        compressible: false
      },
      "application/lxf": {
        source: "iana"
      },
      "application/mac-binhex40": {
        source: "iana",
        extensions: ["hqx"]
      },
      "application/mac-compactpro": {
        source: "apache",
        extensions: ["cpt"]
      },
      "application/macwriteii": {
        source: "iana"
      },
      "application/mads+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mads"]
      },
      "application/manifest+json": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["webmanifest"]
      },
      "application/marc": {
        source: "iana",
        extensions: ["mrc"]
      },
      "application/marcxml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mrcx"]
      },
      "application/mathematica": {
        source: "iana",
        extensions: ["ma", "nb", "mb"]
      },
      "application/mathml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mathml"]
      },
      "application/mathml-content+xml": {
        source: "iana",
        compressible: true
      },
      "application/mathml-presentation+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-associated-procedure-description+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-deregister+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-envelope+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-msk+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-msk-response+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-protection-description+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-reception-report+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-register+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-register-response+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-schedule+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbms-user-service-description+xml": {
        source: "iana",
        compressible: true
      },
      "application/mbox": {
        source: "iana",
        extensions: ["mbox"]
      },
      "application/media-policy-dataset+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mpf"]
      },
      "application/media_control+xml": {
        source: "iana",
        compressible: true
      },
      "application/mediaservercontrol+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mscml"]
      },
      "application/merge-patch+json": {
        source: "iana",
        compressible: true
      },
      "application/metalink+xml": {
        source: "apache",
        compressible: true,
        extensions: ["metalink"]
      },
      "application/metalink4+xml": {
        source: "iana",
        compressible: true,
        extensions: ["meta4"]
      },
      "application/mets+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mets"]
      },
      "application/mf4": {
        source: "iana"
      },
      "application/mikey": {
        source: "iana"
      },
      "application/mipc": {
        source: "iana"
      },
      "application/missing-blocks+cbor-seq": {
        source: "iana"
      },
      "application/mmt-aei+xml": {
        source: "iana",
        compressible: true,
        extensions: ["maei"]
      },
      "application/mmt-usd+xml": {
        source: "iana",
        compressible: true,
        extensions: ["musd"]
      },
      "application/mods+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mods"]
      },
      "application/moss-keys": {
        source: "iana"
      },
      "application/moss-signature": {
        source: "iana"
      },
      "application/mosskey-data": {
        source: "iana"
      },
      "application/mosskey-request": {
        source: "iana"
      },
      "application/mp21": {
        source: "iana",
        extensions: ["m21", "mp21"]
      },
      "application/mp4": {
        source: "iana",
        extensions: ["mp4s", "m4p"]
      },
      "application/mpeg4-generic": {
        source: "iana"
      },
      "application/mpeg4-iod": {
        source: "iana"
      },
      "application/mpeg4-iod-xmt": {
        source: "iana"
      },
      "application/mrb-consumer+xml": {
        source: "iana",
        compressible: true
      },
      "application/mrb-publish+xml": {
        source: "iana",
        compressible: true
      },
      "application/msc-ivr+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/msc-mixer+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/msword": {
        source: "iana",
        compressible: false,
        extensions: ["doc", "dot"]
      },
      "application/mud+json": {
        source: "iana",
        compressible: true
      },
      "application/multipart-core": {
        source: "iana"
      },
      "application/mxf": {
        source: "iana",
        extensions: ["mxf"]
      },
      "application/n-quads": {
        source: "iana",
        extensions: ["nq"]
      },
      "application/n-triples": {
        source: "iana",
        extensions: ["nt"]
      },
      "application/nasdata": {
        source: "iana"
      },
      "application/news-checkgroups": {
        source: "iana",
        charset: "US-ASCII"
      },
      "application/news-groupinfo": {
        source: "iana",
        charset: "US-ASCII"
      },
      "application/news-transmission": {
        source: "iana"
      },
      "application/nlsml+xml": {
        source: "iana",
        compressible: true
      },
      "application/node": {
        source: "iana",
        extensions: ["cjs"]
      },
      "application/nss": {
        source: "iana"
      },
      "application/oauth-authz-req+jwt": {
        source: "iana"
      },
      "application/oblivious-dns-message": {
        source: "iana"
      },
      "application/ocsp-request": {
        source: "iana"
      },
      "application/ocsp-response": {
        source: "iana"
      },
      "application/octet-stream": {
        source: "iana",
        compressible: false,
        extensions: ["bin", "dms", "lrf", "mar", "so", "dist", "distz", "pkg", "bpk", "dump", "elc", "deploy", "exe", "dll", "deb", "dmg", "iso", "img", "msi", "msp", "msm", "buffer"]
      },
      "application/oda": {
        source: "iana",
        extensions: ["oda"]
      },
      "application/odm+xml": {
        source: "iana",
        compressible: true
      },
      "application/odx": {
        source: "iana"
      },
      "application/oebps-package+xml": {
        source: "iana",
        compressible: true,
        extensions: ["opf"]
      },
      "application/ogg": {
        source: "iana",
        compressible: false,
        extensions: ["ogx"]
      },
      "application/omdoc+xml": {
        source: "apache",
        compressible: true,
        extensions: ["omdoc"]
      },
      "application/onenote": {
        source: "apache",
        extensions: ["onetoc", "onetoc2", "onetmp", "onepkg"]
      },
      "application/opc-nodeset+xml": {
        source: "iana",
        compressible: true
      },
      "application/oscore": {
        source: "iana"
      },
      "application/oxps": {
        source: "iana",
        extensions: ["oxps"]
      },
      "application/p21": {
        source: "iana"
      },
      "application/p21+zip": {
        source: "iana",
        compressible: false
      },
      "application/p2p-overlay+xml": {
        source: "iana",
        compressible: true,
        extensions: ["relo"]
      },
      "application/parityfec": {
        source: "iana"
      },
      "application/passport": {
        source: "iana"
      },
      "application/patch-ops-error+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xer"]
      },
      "application/pdf": {
        source: "iana",
        compressible: false,
        extensions: ["pdf"]
      },
      "application/pdx": {
        source: "iana"
      },
      "application/pem-certificate-chain": {
        source: "iana"
      },
      "application/pgp-encrypted": {
        source: "iana",
        compressible: false,
        extensions: ["pgp"]
      },
      "application/pgp-keys": {
        source: "iana",
        extensions: ["asc"]
      },
      "application/pgp-signature": {
        source: "iana",
        extensions: ["asc", "sig"]
      },
      "application/pics-rules": {
        source: "apache",
        extensions: ["prf"]
      },
      "application/pidf+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/pidf-diff+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/pkcs10": {
        source: "iana",
        extensions: ["p10"]
      },
      "application/pkcs12": {
        source: "iana"
      },
      "application/pkcs7-mime": {
        source: "iana",
        extensions: ["p7m", "p7c"]
      },
      "application/pkcs7-signature": {
        source: "iana",
        extensions: ["p7s"]
      },
      "application/pkcs8": {
        source: "iana",
        extensions: ["p8"]
      },
      "application/pkcs8-encrypted": {
        source: "iana"
      },
      "application/pkix-attr-cert": {
        source: "iana",
        extensions: ["ac"]
      },
      "application/pkix-cert": {
        source: "iana",
        extensions: ["cer"]
      },
      "application/pkix-crl": {
        source: "iana",
        extensions: ["crl"]
      },
      "application/pkix-pkipath": {
        source: "iana",
        extensions: ["pkipath"]
      },
      "application/pkixcmp": {
        source: "iana",
        extensions: ["pki"]
      },
      "application/pls+xml": {
        source: "iana",
        compressible: true,
        extensions: ["pls"]
      },
      "application/poc-settings+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/postscript": {
        source: "iana",
        compressible: true,
        extensions: ["ai", "eps", "ps"]
      },
      "application/ppsp-tracker+json": {
        source: "iana",
        compressible: true
      },
      "application/problem+json": {
        source: "iana",
        compressible: true
      },
      "application/problem+xml": {
        source: "iana",
        compressible: true
      },
      "application/provenance+xml": {
        source: "iana",
        compressible: true,
        extensions: ["provx"]
      },
      "application/prs.alvestrand.titrax-sheet": {
        source: "iana"
      },
      "application/prs.cww": {
        source: "iana",
        extensions: ["cww"]
      },
      "application/prs.cyn": {
        source: "iana",
        charset: "7-BIT"
      },
      "application/prs.hpub+zip": {
        source: "iana",
        compressible: false
      },
      "application/prs.nprend": {
        source: "iana"
      },
      "application/prs.plucker": {
        source: "iana"
      },
      "application/prs.rdf-xml-crypt": {
        source: "iana"
      },
      "application/prs.xsf+xml": {
        source: "iana",
        compressible: true
      },
      "application/pskc+xml": {
        source: "iana",
        compressible: true,
        extensions: ["pskcxml"]
      },
      "application/pvd+json": {
        source: "iana",
        compressible: true
      },
      "application/qsig": {
        source: "iana"
      },
      "application/raml+yaml": {
        compressible: true,
        extensions: ["raml"]
      },
      "application/raptorfec": {
        source: "iana"
      },
      "application/rdap+json": {
        source: "iana",
        compressible: true
      },
      "application/rdf+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rdf", "owl"]
      },
      "application/reginfo+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rif"]
      },
      "application/relax-ng-compact-syntax": {
        source: "iana",
        extensions: ["rnc"]
      },
      "application/remote-printing": {
        source: "iana"
      },
      "application/reputon+json": {
        source: "iana",
        compressible: true
      },
      "application/resource-lists+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rl"]
      },
      "application/resource-lists-diff+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rld"]
      },
      "application/rfc+xml": {
        source: "iana",
        compressible: true
      },
      "application/riscos": {
        source: "iana"
      },
      "application/rlmi+xml": {
        source: "iana",
        compressible: true
      },
      "application/rls-services+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rs"]
      },
      "application/route-apd+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rapd"]
      },
      "application/route-s-tsid+xml": {
        source: "iana",
        compressible: true,
        extensions: ["sls"]
      },
      "application/route-usd+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rusd"]
      },
      "application/rpki-ghostbusters": {
        source: "iana",
        extensions: ["gbr"]
      },
      "application/rpki-manifest": {
        source: "iana",
        extensions: ["mft"]
      },
      "application/rpki-publication": {
        source: "iana"
      },
      "application/rpki-roa": {
        source: "iana",
        extensions: ["roa"]
      },
      "application/rpki-updown": {
        source: "iana"
      },
      "application/rsd+xml": {
        source: "apache",
        compressible: true,
        extensions: ["rsd"]
      },
      "application/rss+xml": {
        source: "apache",
        compressible: true,
        extensions: ["rss"]
      },
      "application/rtf": {
        source: "iana",
        compressible: true,
        extensions: ["rtf"]
      },
      "application/rtploopback": {
        source: "iana"
      },
      "application/rtx": {
        source: "iana"
      },
      "application/samlassertion+xml": {
        source: "iana",
        compressible: true
      },
      "application/samlmetadata+xml": {
        source: "iana",
        compressible: true
      },
      "application/sarif+json": {
        source: "iana",
        compressible: true
      },
      "application/sarif-external-properties+json": {
        source: "iana",
        compressible: true
      },
      "application/sbe": {
        source: "iana"
      },
      "application/sbml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["sbml"]
      },
      "application/scaip+xml": {
        source: "iana",
        compressible: true
      },
      "application/scim+json": {
        source: "iana",
        compressible: true
      },
      "application/scvp-cv-request": {
        source: "iana",
        extensions: ["scq"]
      },
      "application/scvp-cv-response": {
        source: "iana",
        extensions: ["scs"]
      },
      "application/scvp-vp-request": {
        source: "iana",
        extensions: ["spq"]
      },
      "application/scvp-vp-response": {
        source: "iana",
        extensions: ["spp"]
      },
      "application/sdp": {
        source: "iana",
        extensions: ["sdp"]
      },
      "application/secevent+jwt": {
        source: "iana"
      },
      "application/senml+cbor": {
        source: "iana"
      },
      "application/senml+json": {
        source: "iana",
        compressible: true
      },
      "application/senml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["senmlx"]
      },
      "application/senml-etch+cbor": {
        source: "iana"
      },
      "application/senml-etch+json": {
        source: "iana",
        compressible: true
      },
      "application/senml-exi": {
        source: "iana"
      },
      "application/sensml+cbor": {
        source: "iana"
      },
      "application/sensml+json": {
        source: "iana",
        compressible: true
      },
      "application/sensml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["sensmlx"]
      },
      "application/sensml-exi": {
        source: "iana"
      },
      "application/sep+xml": {
        source: "iana",
        compressible: true
      },
      "application/sep-exi": {
        source: "iana"
      },
      "application/session-info": {
        source: "iana"
      },
      "application/set-payment": {
        source: "iana"
      },
      "application/set-payment-initiation": {
        source: "iana",
        extensions: ["setpay"]
      },
      "application/set-registration": {
        source: "iana"
      },
      "application/set-registration-initiation": {
        source: "iana",
        extensions: ["setreg"]
      },
      "application/sgml": {
        source: "iana"
      },
      "application/sgml-open-catalog": {
        source: "iana"
      },
      "application/shf+xml": {
        source: "iana",
        compressible: true,
        extensions: ["shf"]
      },
      "application/sieve": {
        source: "iana",
        extensions: ["siv", "sieve"]
      },
      "application/simple-filter+xml": {
        source: "iana",
        compressible: true
      },
      "application/simple-message-summary": {
        source: "iana"
      },
      "application/simplesymbolcontainer": {
        source: "iana"
      },
      "application/sipc": {
        source: "iana"
      },
      "application/slate": {
        source: "iana"
      },
      "application/smil": {
        source: "iana"
      },
      "application/smil+xml": {
        source: "iana",
        compressible: true,
        extensions: ["smi", "smil"]
      },
      "application/smpte336m": {
        source: "iana"
      },
      "application/soap+fastinfoset": {
        source: "iana"
      },
      "application/soap+xml": {
        source: "iana",
        compressible: true
      },
      "application/sparql-query": {
        source: "iana",
        extensions: ["rq"]
      },
      "application/sparql-results+xml": {
        source: "iana",
        compressible: true,
        extensions: ["srx"]
      },
      "application/spdx+json": {
        source: "iana",
        compressible: true
      },
      "application/spirits-event+xml": {
        source: "iana",
        compressible: true
      },
      "application/sql": {
        source: "iana"
      },
      "application/srgs": {
        source: "iana",
        extensions: ["gram"]
      },
      "application/srgs+xml": {
        source: "iana",
        compressible: true,
        extensions: ["grxml"]
      },
      "application/sru+xml": {
        source: "iana",
        compressible: true,
        extensions: ["sru"]
      },
      "application/ssdl+xml": {
        source: "apache",
        compressible: true,
        extensions: ["ssdl"]
      },
      "application/ssml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["ssml"]
      },
      "application/stix+json": {
        source: "iana",
        compressible: true
      },
      "application/swid+xml": {
        source: "iana",
        compressible: true,
        extensions: ["swidtag"]
      },
      "application/tamp-apex-update": {
        source: "iana"
      },
      "application/tamp-apex-update-confirm": {
        source: "iana"
      },
      "application/tamp-community-update": {
        source: "iana"
      },
      "application/tamp-community-update-confirm": {
        source: "iana"
      },
      "application/tamp-error": {
        source: "iana"
      },
      "application/tamp-sequence-adjust": {
        source: "iana"
      },
      "application/tamp-sequence-adjust-confirm": {
        source: "iana"
      },
      "application/tamp-status-query": {
        source: "iana"
      },
      "application/tamp-status-response": {
        source: "iana"
      },
      "application/tamp-update": {
        source: "iana"
      },
      "application/tamp-update-confirm": {
        source: "iana"
      },
      "application/tar": {
        compressible: true
      },
      "application/taxii+json": {
        source: "iana",
        compressible: true
      },
      "application/td+json": {
        source: "iana",
        compressible: true
      },
      "application/tei+xml": {
        source: "iana",
        compressible: true,
        extensions: ["tei", "teicorpus"]
      },
      "application/tetra_isi": {
        source: "iana"
      },
      "application/thraud+xml": {
        source: "iana",
        compressible: true,
        extensions: ["tfi"]
      },
      "application/timestamp-query": {
        source: "iana"
      },
      "application/timestamp-reply": {
        source: "iana"
      },
      "application/timestamped-data": {
        source: "iana",
        extensions: ["tsd"]
      },
      "application/tlsrpt+gzip": {
        source: "iana"
      },
      "application/tlsrpt+json": {
        source: "iana",
        compressible: true
      },
      "application/tnauthlist": {
        source: "iana"
      },
      "application/token-introspection+jwt": {
        source: "iana"
      },
      "application/toml": {
        compressible: true,
        extensions: ["toml"]
      },
      "application/trickle-ice-sdpfrag": {
        source: "iana"
      },
      "application/trig": {
        source: "iana",
        extensions: ["trig"]
      },
      "application/ttml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["ttml"]
      },
      "application/tve-trigger": {
        source: "iana"
      },
      "application/tzif": {
        source: "iana"
      },
      "application/tzif-leap": {
        source: "iana"
      },
      "application/ubjson": {
        compressible: false,
        extensions: ["ubj"]
      },
      "application/ulpfec": {
        source: "iana"
      },
      "application/urc-grpsheet+xml": {
        source: "iana",
        compressible: true
      },
      "application/urc-ressheet+xml": {
        source: "iana",
        compressible: true,
        extensions: ["rsheet"]
      },
      "application/urc-targetdesc+xml": {
        source: "iana",
        compressible: true,
        extensions: ["td"]
      },
      "application/urc-uisocketdesc+xml": {
        source: "iana",
        compressible: true
      },
      "application/vcard+json": {
        source: "iana",
        compressible: true
      },
      "application/vcard+xml": {
        source: "iana",
        compressible: true
      },
      "application/vemmi": {
        source: "iana"
      },
      "application/vividence.scriptfile": {
        source: "apache"
      },
      "application/vnd.1000minds.decision-model+xml": {
        source: "iana",
        compressible: true,
        extensions: ["1km"]
      },
      "application/vnd.3gpp-prose+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp-prose-pc3ch+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp-v2x-local-service-information": {
        source: "iana"
      },
      "application/vnd.3gpp.5gnas": {
        source: "iana"
      },
      "application/vnd.3gpp.access-transfer-events+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.bsf+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.gmop+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.gtpc": {
        source: "iana"
      },
      "application/vnd.3gpp.interworking-data": {
        source: "iana"
      },
      "application/vnd.3gpp.lpp": {
        source: "iana"
      },
      "application/vnd.3gpp.mc-signalling-ear": {
        source: "iana"
      },
      "application/vnd.3gpp.mcdata-affiliation-command+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcdata-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcdata-payload": {
        source: "iana"
      },
      "application/vnd.3gpp.mcdata-service-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcdata-signalling": {
        source: "iana"
      },
      "application/vnd.3gpp.mcdata-ue-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcdata-user-profile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-affiliation-command+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-floor-request+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-location-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-mbms-usage-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-service-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-signed+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-ue-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-ue-init-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcptt-user-profile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-affiliation-command+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-affiliation-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-location-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-mbms-usage-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-service-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-transmission-request+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-ue-config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mcvideo-user-profile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.mid-call+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.ngap": {
        source: "iana"
      },
      "application/vnd.3gpp.pfcp": {
        source: "iana"
      },
      "application/vnd.3gpp.pic-bw-large": {
        source: "iana",
        extensions: ["plb"]
      },
      "application/vnd.3gpp.pic-bw-small": {
        source: "iana",
        extensions: ["psb"]
      },
      "application/vnd.3gpp.pic-bw-var": {
        source: "iana",
        extensions: ["pvb"]
      },
      "application/vnd.3gpp.s1ap": {
        source: "iana"
      },
      "application/vnd.3gpp.sms": {
        source: "iana"
      },
      "application/vnd.3gpp.sms+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.srvcc-ext+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.srvcc-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.state-and-event-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp.ussd+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp2.bcmcsinfo+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.3gpp2.sms": {
        source: "iana"
      },
      "application/vnd.3gpp2.tcap": {
        source: "iana",
        extensions: ["tcap"]
      },
      "application/vnd.3lightssoftware.imagescal": {
        source: "iana"
      },
      "application/vnd.3m.post-it-notes": {
        source: "iana",
        extensions: ["pwn"]
      },
      "application/vnd.accpac.simply.aso": {
        source: "iana",
        extensions: ["aso"]
      },
      "application/vnd.accpac.simply.imp": {
        source: "iana",
        extensions: ["imp"]
      },
      "application/vnd.acucobol": {
        source: "iana",
        extensions: ["acu"]
      },
      "application/vnd.acucorp": {
        source: "iana",
        extensions: ["atc", "acutc"]
      },
      "application/vnd.adobe.air-application-installer-package+zip": {
        source: "apache",
        compressible: false,
        extensions: ["air"]
      },
      "application/vnd.adobe.flash.movie": {
        source: "iana"
      },
      "application/vnd.adobe.formscentral.fcdt": {
        source: "iana",
        extensions: ["fcdt"]
      },
      "application/vnd.adobe.fxp": {
        source: "iana",
        extensions: ["fxp", "fxpl"]
      },
      "application/vnd.adobe.partial-upload": {
        source: "iana"
      },
      "application/vnd.adobe.xdp+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xdp"]
      },
      "application/vnd.adobe.xfdf": {
        source: "iana",
        extensions: ["xfdf"]
      },
      "application/vnd.aether.imp": {
        source: "iana"
      },
      "application/vnd.afpc.afplinedata": {
        source: "iana"
      },
      "application/vnd.afpc.afplinedata-pagedef": {
        source: "iana"
      },
      "application/vnd.afpc.cmoca-cmresource": {
        source: "iana"
      },
      "application/vnd.afpc.foca-charset": {
        source: "iana"
      },
      "application/vnd.afpc.foca-codedfont": {
        source: "iana"
      },
      "application/vnd.afpc.foca-codepage": {
        source: "iana"
      },
      "application/vnd.afpc.modca": {
        source: "iana"
      },
      "application/vnd.afpc.modca-cmtable": {
        source: "iana"
      },
      "application/vnd.afpc.modca-formdef": {
        source: "iana"
      },
      "application/vnd.afpc.modca-mediummap": {
        source: "iana"
      },
      "application/vnd.afpc.modca-objectcontainer": {
        source: "iana"
      },
      "application/vnd.afpc.modca-overlay": {
        source: "iana"
      },
      "application/vnd.afpc.modca-pagesegment": {
        source: "iana"
      },
      "application/vnd.age": {
        source: "iana",
        extensions: ["age"]
      },
      "application/vnd.ah-barcode": {
        source: "iana"
      },
      "application/vnd.ahead.space": {
        source: "iana",
        extensions: ["ahead"]
      },
      "application/vnd.airzip.filesecure.azf": {
        source: "iana",
        extensions: ["azf"]
      },
      "application/vnd.airzip.filesecure.azs": {
        source: "iana",
        extensions: ["azs"]
      },
      "application/vnd.amadeus+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.amazon.ebook": {
        source: "apache",
        extensions: ["azw"]
      },
      "application/vnd.amazon.mobi8-ebook": {
        source: "iana"
      },
      "application/vnd.americandynamics.acc": {
        source: "iana",
        extensions: ["acc"]
      },
      "application/vnd.amiga.ami": {
        source: "iana",
        extensions: ["ami"]
      },
      "application/vnd.amundsen.maze+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.android.ota": {
        source: "iana"
      },
      "application/vnd.android.package-archive": {
        source: "apache",
        compressible: false,
        extensions: ["apk"]
      },
      "application/vnd.anki": {
        source: "iana"
      },
      "application/vnd.anser-web-certificate-issue-initiation": {
        source: "iana",
        extensions: ["cii"]
      },
      "application/vnd.anser-web-funds-transfer-initiation": {
        source: "apache",
        extensions: ["fti"]
      },
      "application/vnd.antix.game-component": {
        source: "iana",
        extensions: ["atx"]
      },
      "application/vnd.apache.arrow.file": {
        source: "iana"
      },
      "application/vnd.apache.arrow.stream": {
        source: "iana"
      },
      "application/vnd.apache.thrift.binary": {
        source: "iana"
      },
      "application/vnd.apache.thrift.compact": {
        source: "iana"
      },
      "application/vnd.apache.thrift.json": {
        source: "iana"
      },
      "application/vnd.api+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.aplextor.warrp+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.apothekende.reservation+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.apple.installer+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mpkg"]
      },
      "application/vnd.apple.keynote": {
        source: "iana",
        extensions: ["key"]
      },
      "application/vnd.apple.mpegurl": {
        source: "iana",
        extensions: ["m3u8"]
      },
      "application/vnd.apple.numbers": {
        source: "iana",
        extensions: ["numbers"]
      },
      "application/vnd.apple.pages": {
        source: "iana",
        extensions: ["pages"]
      },
      "application/vnd.apple.pkpass": {
        compressible: false,
        extensions: ["pkpass"]
      },
      "application/vnd.arastra.swi": {
        source: "iana"
      },
      "application/vnd.aristanetworks.swi": {
        source: "iana",
        extensions: ["swi"]
      },
      "application/vnd.artisan+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.artsquare": {
        source: "iana"
      },
      "application/vnd.astraea-software.iota": {
        source: "iana",
        extensions: ["iota"]
      },
      "application/vnd.audiograph": {
        source: "iana",
        extensions: ["aep"]
      },
      "application/vnd.autopackage": {
        source: "iana"
      },
      "application/vnd.avalon+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.avistar+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.balsamiq.bmml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["bmml"]
      },
      "application/vnd.balsamiq.bmpr": {
        source: "iana"
      },
      "application/vnd.banana-accounting": {
        source: "iana"
      },
      "application/vnd.bbf.usp.error": {
        source: "iana"
      },
      "application/vnd.bbf.usp.msg": {
        source: "iana"
      },
      "application/vnd.bbf.usp.msg+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.bekitzur-stech+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.bint.med-content": {
        source: "iana"
      },
      "application/vnd.biopax.rdf+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.blink-idb-value-wrapper": {
        source: "iana"
      },
      "application/vnd.blueice.multipass": {
        source: "iana",
        extensions: ["mpm"]
      },
      "application/vnd.bluetooth.ep.oob": {
        source: "iana"
      },
      "application/vnd.bluetooth.le.oob": {
        source: "iana"
      },
      "application/vnd.bmi": {
        source: "iana",
        extensions: ["bmi"]
      },
      "application/vnd.bpf": {
        source: "iana"
      },
      "application/vnd.bpf3": {
        source: "iana"
      },
      "application/vnd.businessobjects": {
        source: "iana",
        extensions: ["rep"]
      },
      "application/vnd.byu.uapi+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.cab-jscript": {
        source: "iana"
      },
      "application/vnd.canon-cpdl": {
        source: "iana"
      },
      "application/vnd.canon-lips": {
        source: "iana"
      },
      "application/vnd.capasystems-pg+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.cendio.thinlinc.clientconf": {
        source: "iana"
      },
      "application/vnd.century-systems.tcp_stream": {
        source: "iana"
      },
      "application/vnd.chemdraw+xml": {
        source: "iana",
        compressible: true,
        extensions: ["cdxml"]
      },
      "application/vnd.chess-pgn": {
        source: "iana"
      },
      "application/vnd.chipnuts.karaoke-mmd": {
        source: "iana",
        extensions: ["mmd"]
      },
      "application/vnd.ciedi": {
        source: "iana"
      },
      "application/vnd.cinderella": {
        source: "iana",
        extensions: ["cdy"]
      },
      "application/vnd.cirpack.isdn-ext": {
        source: "iana"
      },
      "application/vnd.citationstyles.style+xml": {
        source: "iana",
        compressible: true,
        extensions: ["csl"]
      },
      "application/vnd.claymore": {
        source: "iana",
        extensions: ["cla"]
      },
      "application/vnd.cloanto.rp9": {
        source: "iana",
        extensions: ["rp9"]
      },
      "application/vnd.clonk.c4group": {
        source: "iana",
        extensions: ["c4g", "c4d", "c4f", "c4p", "c4u"]
      },
      "application/vnd.cluetrust.cartomobile-config": {
        source: "iana",
        extensions: ["c11amc"]
      },
      "application/vnd.cluetrust.cartomobile-config-pkg": {
        source: "iana",
        extensions: ["c11amz"]
      },
      "application/vnd.coffeescript": {
        source: "iana"
      },
      "application/vnd.collabio.xodocuments.document": {
        source: "iana"
      },
      "application/vnd.collabio.xodocuments.document-template": {
        source: "iana"
      },
      "application/vnd.collabio.xodocuments.presentation": {
        source: "iana"
      },
      "application/vnd.collabio.xodocuments.presentation-template": {
        source: "iana"
      },
      "application/vnd.collabio.xodocuments.spreadsheet": {
        source: "iana"
      },
      "application/vnd.collabio.xodocuments.spreadsheet-template": {
        source: "iana"
      },
      "application/vnd.collection+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.collection.doc+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.collection.next+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.comicbook+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.comicbook-rar": {
        source: "iana"
      },
      "application/vnd.commerce-battelle": {
        source: "iana"
      },
      "application/vnd.commonspace": {
        source: "iana",
        extensions: ["csp"]
      },
      "application/vnd.contact.cmsg": {
        source: "iana",
        extensions: ["cdbcmsg"]
      },
      "application/vnd.coreos.ignition+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.cosmocaller": {
        source: "iana",
        extensions: ["cmc"]
      },
      "application/vnd.crick.clicker": {
        source: "iana",
        extensions: ["clkx"]
      },
      "application/vnd.crick.clicker.keyboard": {
        source: "iana",
        extensions: ["clkk"]
      },
      "application/vnd.crick.clicker.palette": {
        source: "iana",
        extensions: ["clkp"]
      },
      "application/vnd.crick.clicker.template": {
        source: "iana",
        extensions: ["clkt"]
      },
      "application/vnd.crick.clicker.wordbank": {
        source: "iana",
        extensions: ["clkw"]
      },
      "application/vnd.criticaltools.wbs+xml": {
        source: "iana",
        compressible: true,
        extensions: ["wbs"]
      },
      "application/vnd.cryptii.pipe+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.crypto-shade-file": {
        source: "iana"
      },
      "application/vnd.cryptomator.encrypted": {
        source: "iana"
      },
      "application/vnd.cryptomator.vault": {
        source: "iana"
      },
      "application/vnd.ctc-posml": {
        source: "iana",
        extensions: ["pml"]
      },
      "application/vnd.ctct.ws+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.cups-pdf": {
        source: "iana"
      },
      "application/vnd.cups-postscript": {
        source: "iana"
      },
      "application/vnd.cups-ppd": {
        source: "iana",
        extensions: ["ppd"]
      },
      "application/vnd.cups-raster": {
        source: "iana"
      },
      "application/vnd.cups-raw": {
        source: "iana"
      },
      "application/vnd.curl": {
        source: "iana"
      },
      "application/vnd.curl.car": {
        source: "apache",
        extensions: ["car"]
      },
      "application/vnd.curl.pcurl": {
        source: "apache",
        extensions: ["pcurl"]
      },
      "application/vnd.cyan.dean.root+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.cybank": {
        source: "iana"
      },
      "application/vnd.cyclonedx+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.cyclonedx+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.d2l.coursepackage1p0+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.d3m-dataset": {
        source: "iana"
      },
      "application/vnd.d3m-problem": {
        source: "iana"
      },
      "application/vnd.dart": {
        source: "iana",
        compressible: true,
        extensions: ["dart"]
      },
      "application/vnd.data-vision.rdz": {
        source: "iana",
        extensions: ["rdz"]
      },
      "application/vnd.datapackage+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dataresource+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dbf": {
        source: "iana",
        extensions: ["dbf"]
      },
      "application/vnd.debian.binary-package": {
        source: "iana"
      },
      "application/vnd.dece.data": {
        source: "iana",
        extensions: ["uvf", "uvvf", "uvd", "uvvd"]
      },
      "application/vnd.dece.ttml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["uvt", "uvvt"]
      },
      "application/vnd.dece.unspecified": {
        source: "iana",
        extensions: ["uvx", "uvvx"]
      },
      "application/vnd.dece.zip": {
        source: "iana",
        extensions: ["uvz", "uvvz"]
      },
      "application/vnd.denovo.fcselayout-link": {
        source: "iana",
        extensions: ["fe_launch"]
      },
      "application/vnd.desmume.movie": {
        source: "iana"
      },
      "application/vnd.dir-bi.plate-dl-nosuffix": {
        source: "iana"
      },
      "application/vnd.dm.delegation+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dna": {
        source: "iana",
        extensions: ["dna"]
      },
      "application/vnd.document+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dolby.mlp": {
        source: "apache",
        extensions: ["mlp"]
      },
      "application/vnd.dolby.mobile.1": {
        source: "iana"
      },
      "application/vnd.dolby.mobile.2": {
        source: "iana"
      },
      "application/vnd.doremir.scorecloud-binary-document": {
        source: "iana"
      },
      "application/vnd.dpgraph": {
        source: "iana",
        extensions: ["dpg"]
      },
      "application/vnd.dreamfactory": {
        source: "iana",
        extensions: ["dfac"]
      },
      "application/vnd.drive+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ds-keypoint": {
        source: "apache",
        extensions: ["kpxx"]
      },
      "application/vnd.dtg.local": {
        source: "iana"
      },
      "application/vnd.dtg.local.flash": {
        source: "iana"
      },
      "application/vnd.dtg.local.html": {
        source: "iana"
      },
      "application/vnd.dvb.ait": {
        source: "iana",
        extensions: ["ait"]
      },
      "application/vnd.dvb.dvbisl+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.dvbj": {
        source: "iana"
      },
      "application/vnd.dvb.esgcontainer": {
        source: "iana"
      },
      "application/vnd.dvb.ipdcdftnotifaccess": {
        source: "iana"
      },
      "application/vnd.dvb.ipdcesgaccess": {
        source: "iana"
      },
      "application/vnd.dvb.ipdcesgaccess2": {
        source: "iana"
      },
      "application/vnd.dvb.ipdcesgpdd": {
        source: "iana"
      },
      "application/vnd.dvb.ipdcroaming": {
        source: "iana"
      },
      "application/vnd.dvb.iptv.alfec-base": {
        source: "iana"
      },
      "application/vnd.dvb.iptv.alfec-enhancement": {
        source: "iana"
      },
      "application/vnd.dvb.notif-aggregate-root+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.notif-container+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.notif-generic+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.notif-ia-msglist+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.notif-ia-registration-request+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.notif-ia-registration-response+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.notif-init+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.dvb.pfr": {
        source: "iana"
      },
      "application/vnd.dvb.service": {
        source: "iana",
        extensions: ["svc"]
      },
      "application/vnd.dxr": {
        source: "iana"
      },
      "application/vnd.dynageo": {
        source: "iana",
        extensions: ["geo"]
      },
      "application/vnd.dzr": {
        source: "iana"
      },
      "application/vnd.easykaraoke.cdgdownload": {
        source: "iana"
      },
      "application/vnd.ecdis-update": {
        source: "iana"
      },
      "application/vnd.ecip.rlp": {
        source: "iana"
      },
      "application/vnd.eclipse.ditto+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ecowin.chart": {
        source: "iana",
        extensions: ["mag"]
      },
      "application/vnd.ecowin.filerequest": {
        source: "iana"
      },
      "application/vnd.ecowin.fileupdate": {
        source: "iana"
      },
      "application/vnd.ecowin.series": {
        source: "iana"
      },
      "application/vnd.ecowin.seriesrequest": {
        source: "iana"
      },
      "application/vnd.ecowin.seriesupdate": {
        source: "iana"
      },
      "application/vnd.efi.img": {
        source: "iana"
      },
      "application/vnd.efi.iso": {
        source: "iana"
      },
      "application/vnd.emclient.accessrequest+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.enliven": {
        source: "iana",
        extensions: ["nml"]
      },
      "application/vnd.enphase.envoy": {
        source: "iana"
      },
      "application/vnd.eprints.data+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.epson.esf": {
        source: "iana",
        extensions: ["esf"]
      },
      "application/vnd.epson.msf": {
        source: "iana",
        extensions: ["msf"]
      },
      "application/vnd.epson.quickanime": {
        source: "iana",
        extensions: ["qam"]
      },
      "application/vnd.epson.salt": {
        source: "iana",
        extensions: ["slt"]
      },
      "application/vnd.epson.ssf": {
        source: "iana",
        extensions: ["ssf"]
      },
      "application/vnd.ericsson.quickcall": {
        source: "iana"
      },
      "application/vnd.espass-espass+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.eszigno3+xml": {
        source: "iana",
        compressible: true,
        extensions: ["es3", "et3"]
      },
      "application/vnd.etsi.aoc+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.asic-e+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.etsi.asic-s+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.etsi.cug+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvcommand+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvdiscovery+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvprofile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvsad-bc+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvsad-cod+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvsad-npvr+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvservice+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvsync+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.iptvueprofile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.mcid+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.mheg5": {
        source: "iana"
      },
      "application/vnd.etsi.overload-control-policy-dataset+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.pstn+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.sci+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.simservs+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.timestamp-token": {
        source: "iana"
      },
      "application/vnd.etsi.tsl+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.etsi.tsl.der": {
        source: "iana"
      },
      "application/vnd.eu.kasparian.car+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.eudora.data": {
        source: "iana"
      },
      "application/vnd.evolv.ecig.profile": {
        source: "iana"
      },
      "application/vnd.evolv.ecig.settings": {
        source: "iana"
      },
      "application/vnd.evolv.ecig.theme": {
        source: "iana"
      },
      "application/vnd.exstream-empower+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.exstream-package": {
        source: "iana"
      },
      "application/vnd.ezpix-album": {
        source: "iana",
        extensions: ["ez2"]
      },
      "application/vnd.ezpix-package": {
        source: "iana",
        extensions: ["ez3"]
      },
      "application/vnd.f-secure.mobile": {
        source: "iana"
      },
      "application/vnd.familysearch.gedcom+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.fastcopy-disk-image": {
        source: "iana"
      },
      "application/vnd.fdf": {
        source: "iana",
        extensions: ["fdf"]
      },
      "application/vnd.fdsn.mseed": {
        source: "iana",
        extensions: ["mseed"]
      },
      "application/vnd.fdsn.seed": {
        source: "iana",
        extensions: ["seed", "dataless"]
      },
      "application/vnd.ffsns": {
        source: "iana"
      },
      "application/vnd.ficlab.flb+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.filmit.zfc": {
        source: "iana"
      },
      "application/vnd.fints": {
        source: "iana"
      },
      "application/vnd.firemonkeys.cloudcell": {
        source: "iana"
      },
      "application/vnd.flographit": {
        source: "iana",
        extensions: ["gph"]
      },
      "application/vnd.fluxtime.clip": {
        source: "iana",
        extensions: ["ftc"]
      },
      "application/vnd.font-fontforge-sfd": {
        source: "iana"
      },
      "application/vnd.framemaker": {
        source: "iana",
        extensions: ["fm", "frame", "maker", "book"]
      },
      "application/vnd.frogans.fnc": {
        source: "iana",
        extensions: ["fnc"]
      },
      "application/vnd.frogans.ltf": {
        source: "iana",
        extensions: ["ltf"]
      },
      "application/vnd.fsc.weblaunch": {
        source: "iana",
        extensions: ["fsc"]
      },
      "application/vnd.fujifilm.fb.docuworks": {
        source: "iana"
      },
      "application/vnd.fujifilm.fb.docuworks.binder": {
        source: "iana"
      },
      "application/vnd.fujifilm.fb.docuworks.container": {
        source: "iana"
      },
      "application/vnd.fujifilm.fb.jfi+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.fujitsu.oasys": {
        source: "iana",
        extensions: ["oas"]
      },
      "application/vnd.fujitsu.oasys2": {
        source: "iana",
        extensions: ["oa2"]
      },
      "application/vnd.fujitsu.oasys3": {
        source: "iana",
        extensions: ["oa3"]
      },
      "application/vnd.fujitsu.oasysgp": {
        source: "iana",
        extensions: ["fg5"]
      },
      "application/vnd.fujitsu.oasysprs": {
        source: "iana",
        extensions: ["bh2"]
      },
      "application/vnd.fujixerox.art-ex": {
        source: "iana"
      },
      "application/vnd.fujixerox.art4": {
        source: "iana"
      },
      "application/vnd.fujixerox.ddd": {
        source: "iana",
        extensions: ["ddd"]
      },
      "application/vnd.fujixerox.docuworks": {
        source: "iana",
        extensions: ["xdw"]
      },
      "application/vnd.fujixerox.docuworks.binder": {
        source: "iana",
        extensions: ["xbd"]
      },
      "application/vnd.fujixerox.docuworks.container": {
        source: "iana"
      },
      "application/vnd.fujixerox.hbpl": {
        source: "iana"
      },
      "application/vnd.fut-misnet": {
        source: "iana"
      },
      "application/vnd.futoin+cbor": {
        source: "iana"
      },
      "application/vnd.futoin+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.fuzzysheet": {
        source: "iana",
        extensions: ["fzs"]
      },
      "application/vnd.genomatix.tuxedo": {
        source: "iana",
        extensions: ["txd"]
      },
      "application/vnd.gentics.grd+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.geo+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.geocube+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.geogebra.file": {
        source: "iana",
        extensions: ["ggb"]
      },
      "application/vnd.geogebra.slides": {
        source: "iana"
      },
      "application/vnd.geogebra.tool": {
        source: "iana",
        extensions: ["ggt"]
      },
      "application/vnd.geometry-explorer": {
        source: "iana",
        extensions: ["gex", "gre"]
      },
      "application/vnd.geonext": {
        source: "iana",
        extensions: ["gxt"]
      },
      "application/vnd.geoplan": {
        source: "iana",
        extensions: ["g2w"]
      },
      "application/vnd.geospace": {
        source: "iana",
        extensions: ["g3w"]
      },
      "application/vnd.gerber": {
        source: "iana"
      },
      "application/vnd.globalplatform.card-content-mgt": {
        source: "iana"
      },
      "application/vnd.globalplatform.card-content-mgt-response": {
        source: "iana"
      },
      "application/vnd.gmx": {
        source: "iana",
        extensions: ["gmx"]
      },
      "application/vnd.google-apps.document": {
        compressible: false,
        extensions: ["gdoc"]
      },
      "application/vnd.google-apps.presentation": {
        compressible: false,
        extensions: ["gslides"]
      },
      "application/vnd.google-apps.spreadsheet": {
        compressible: false,
        extensions: ["gsheet"]
      },
      "application/vnd.google-earth.kml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["kml"]
      },
      "application/vnd.google-earth.kmz": {
        source: "iana",
        compressible: false,
        extensions: ["kmz"]
      },
      "application/vnd.gov.sk.e-form+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.gov.sk.e-form+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.gov.sk.xmldatacontainer+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.grafeq": {
        source: "iana",
        extensions: ["gqf", "gqs"]
      },
      "application/vnd.gridmp": {
        source: "iana"
      },
      "application/vnd.groove-account": {
        source: "iana",
        extensions: ["gac"]
      },
      "application/vnd.groove-help": {
        source: "iana",
        extensions: ["ghf"]
      },
      "application/vnd.groove-identity-message": {
        source: "iana",
        extensions: ["gim"]
      },
      "application/vnd.groove-injector": {
        source: "iana",
        extensions: ["grv"]
      },
      "application/vnd.groove-tool-message": {
        source: "iana",
        extensions: ["gtm"]
      },
      "application/vnd.groove-tool-template": {
        source: "iana",
        extensions: ["tpl"]
      },
      "application/vnd.groove-vcard": {
        source: "iana",
        extensions: ["vcg"]
      },
      "application/vnd.hal+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.hal+xml": {
        source: "iana",
        compressible: true,
        extensions: ["hal"]
      },
      "application/vnd.handheld-entertainment+xml": {
        source: "iana",
        compressible: true,
        extensions: ["zmm"]
      },
      "application/vnd.hbci": {
        source: "iana",
        extensions: ["hbci"]
      },
      "application/vnd.hc+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.hcl-bireports": {
        source: "iana"
      },
      "application/vnd.hdt": {
        source: "iana"
      },
      "application/vnd.heroku+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.hhe.lesson-player": {
        source: "iana",
        extensions: ["les"]
      },
      "application/vnd.hl7cda+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/vnd.hl7v2+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/vnd.hp-hpgl": {
        source: "iana",
        extensions: ["hpgl"]
      },
      "application/vnd.hp-hpid": {
        source: "iana",
        extensions: ["hpid"]
      },
      "application/vnd.hp-hps": {
        source: "iana",
        extensions: ["hps"]
      },
      "application/vnd.hp-jlyt": {
        source: "iana",
        extensions: ["jlt"]
      },
      "application/vnd.hp-pcl": {
        source: "iana",
        extensions: ["pcl"]
      },
      "application/vnd.hp-pclxl": {
        source: "iana",
        extensions: ["pclxl"]
      },
      "application/vnd.httphone": {
        source: "iana"
      },
      "application/vnd.hydrostatix.sof-data": {
        source: "iana",
        extensions: ["sfd-hdstx"]
      },
      "application/vnd.hyper+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.hyper-item+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.hyperdrive+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.hzn-3d-crossword": {
        source: "iana"
      },
      "application/vnd.ibm.afplinedata": {
        source: "iana"
      },
      "application/vnd.ibm.electronic-media": {
        source: "iana"
      },
      "application/vnd.ibm.minipay": {
        source: "iana",
        extensions: ["mpy"]
      },
      "application/vnd.ibm.modcap": {
        source: "iana",
        extensions: ["afp", "listafp", "list3820"]
      },
      "application/vnd.ibm.rights-management": {
        source: "iana",
        extensions: ["irm"]
      },
      "application/vnd.ibm.secure-container": {
        source: "iana",
        extensions: ["sc"]
      },
      "application/vnd.iccprofile": {
        source: "iana",
        extensions: ["icc", "icm"]
      },
      "application/vnd.ieee.1905": {
        source: "iana"
      },
      "application/vnd.igloader": {
        source: "iana",
        extensions: ["igl"]
      },
      "application/vnd.imagemeter.folder+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.imagemeter.image+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.immervision-ivp": {
        source: "iana",
        extensions: ["ivp"]
      },
      "application/vnd.immervision-ivu": {
        source: "iana",
        extensions: ["ivu"]
      },
      "application/vnd.ims.imsccv1p1": {
        source: "iana"
      },
      "application/vnd.ims.imsccv1p2": {
        source: "iana"
      },
      "application/vnd.ims.imsccv1p3": {
        source: "iana"
      },
      "application/vnd.ims.lis.v2.result+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ims.lti.v2.toolconsumerprofile+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ims.lti.v2.toolproxy+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ims.lti.v2.toolproxy.id+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ims.lti.v2.toolsettings+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ims.lti.v2.toolsettings.simple+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.informedcontrol.rms+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.informix-visionary": {
        source: "iana"
      },
      "application/vnd.infotech.project": {
        source: "iana"
      },
      "application/vnd.infotech.project+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.innopath.wamp.notification": {
        source: "iana"
      },
      "application/vnd.insors.igm": {
        source: "iana",
        extensions: ["igm"]
      },
      "application/vnd.intercon.formnet": {
        source: "iana",
        extensions: ["xpw", "xpx"]
      },
      "application/vnd.intergeo": {
        source: "iana",
        extensions: ["i2g"]
      },
      "application/vnd.intertrust.digibox": {
        source: "iana"
      },
      "application/vnd.intertrust.nncp": {
        source: "iana"
      },
      "application/vnd.intu.qbo": {
        source: "iana",
        extensions: ["qbo"]
      },
      "application/vnd.intu.qfx": {
        source: "iana",
        extensions: ["qfx"]
      },
      "application/vnd.iptc.g2.catalogitem+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.iptc.g2.conceptitem+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.iptc.g2.knowledgeitem+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.iptc.g2.newsitem+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.iptc.g2.newsmessage+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.iptc.g2.packageitem+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.iptc.g2.planningitem+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ipunplugged.rcprofile": {
        source: "iana",
        extensions: ["rcprofile"]
      },
      "application/vnd.irepository.package+xml": {
        source: "iana",
        compressible: true,
        extensions: ["irp"]
      },
      "application/vnd.is-xpr": {
        source: "iana",
        extensions: ["xpr"]
      },
      "application/vnd.isac.fcs": {
        source: "iana",
        extensions: ["fcs"]
      },
      "application/vnd.iso11783-10+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.jam": {
        source: "iana",
        extensions: ["jam"]
      },
      "application/vnd.japannet-directory-service": {
        source: "iana"
      },
      "application/vnd.japannet-jpnstore-wakeup": {
        source: "iana"
      },
      "application/vnd.japannet-payment-wakeup": {
        source: "iana"
      },
      "application/vnd.japannet-registration": {
        source: "iana"
      },
      "application/vnd.japannet-registration-wakeup": {
        source: "iana"
      },
      "application/vnd.japannet-setstore-wakeup": {
        source: "iana"
      },
      "application/vnd.japannet-verification": {
        source: "iana"
      },
      "application/vnd.japannet-verification-wakeup": {
        source: "iana"
      },
      "application/vnd.jcp.javame.midlet-rms": {
        source: "iana",
        extensions: ["rms"]
      },
      "application/vnd.jisp": {
        source: "iana",
        extensions: ["jisp"]
      },
      "application/vnd.joost.joda-archive": {
        source: "iana",
        extensions: ["joda"]
      },
      "application/vnd.jsk.isdn-ngn": {
        source: "iana"
      },
      "application/vnd.kahootz": {
        source: "iana",
        extensions: ["ktz", "ktr"]
      },
      "application/vnd.kde.karbon": {
        source: "iana",
        extensions: ["karbon"]
      },
      "application/vnd.kde.kchart": {
        source: "iana",
        extensions: ["chrt"]
      },
      "application/vnd.kde.kformula": {
        source: "iana",
        extensions: ["kfo"]
      },
      "application/vnd.kde.kivio": {
        source: "iana",
        extensions: ["flw"]
      },
      "application/vnd.kde.kontour": {
        source: "iana",
        extensions: ["kon"]
      },
      "application/vnd.kde.kpresenter": {
        source: "iana",
        extensions: ["kpr", "kpt"]
      },
      "application/vnd.kde.kspread": {
        source: "iana",
        extensions: ["ksp"]
      },
      "application/vnd.kde.kword": {
        source: "iana",
        extensions: ["kwd", "kwt"]
      },
      "application/vnd.kenameaapp": {
        source: "iana",
        extensions: ["htke"]
      },
      "application/vnd.kidspiration": {
        source: "iana",
        extensions: ["kia"]
      },
      "application/vnd.kinar": {
        source: "iana",
        extensions: ["kne", "knp"]
      },
      "application/vnd.koan": {
        source: "iana",
        extensions: ["skp", "skd", "skt", "skm"]
      },
      "application/vnd.kodak-descriptor": {
        source: "iana",
        extensions: ["sse"]
      },
      "application/vnd.las": {
        source: "iana"
      },
      "application/vnd.las.las+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.las.las+xml": {
        source: "iana",
        compressible: true,
        extensions: ["lasxml"]
      },
      "application/vnd.laszip": {
        source: "iana"
      },
      "application/vnd.leap+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.liberty-request+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.llamagraphics.life-balance.desktop": {
        source: "iana",
        extensions: ["lbd"]
      },
      "application/vnd.llamagraphics.life-balance.exchange+xml": {
        source: "iana",
        compressible: true,
        extensions: ["lbe"]
      },
      "application/vnd.logipipe.circuit+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.loom": {
        source: "iana"
      },
      "application/vnd.lotus-1-2-3": {
        source: "iana",
        extensions: ["123"]
      },
      "application/vnd.lotus-approach": {
        source: "iana",
        extensions: ["apr"]
      },
      "application/vnd.lotus-freelance": {
        source: "iana",
        extensions: ["pre"]
      },
      "application/vnd.lotus-notes": {
        source: "iana",
        extensions: ["nsf"]
      },
      "application/vnd.lotus-organizer": {
        source: "iana",
        extensions: ["org"]
      },
      "application/vnd.lotus-screencam": {
        source: "iana",
        extensions: ["scm"]
      },
      "application/vnd.lotus-wordpro": {
        source: "iana",
        extensions: ["lwp"]
      },
      "application/vnd.macports.portpkg": {
        source: "iana",
        extensions: ["portpkg"]
      },
      "application/vnd.mapbox-vector-tile": {
        source: "iana",
        extensions: ["mvt"]
      },
      "application/vnd.marlin.drm.actiontoken+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.marlin.drm.conftoken+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.marlin.drm.license+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.marlin.drm.mdcf": {
        source: "iana"
      },
      "application/vnd.mason+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.maxar.archive.3tz+zip": {
        source: "iana",
        compressible: false
      },
      "application/vnd.maxmind.maxmind-db": {
        source: "iana"
      },
      "application/vnd.mcd": {
        source: "iana",
        extensions: ["mcd"]
      },
      "application/vnd.medcalcdata": {
        source: "iana",
        extensions: ["mc1"]
      },
      "application/vnd.mediastation.cdkey": {
        source: "iana",
        extensions: ["cdkey"]
      },
      "application/vnd.meridian-slingshot": {
        source: "iana"
      },
      "application/vnd.mfer": {
        source: "iana",
        extensions: ["mwf"]
      },
      "application/vnd.mfmp": {
        source: "iana",
        extensions: ["mfm"]
      },
      "application/vnd.micro+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.micrografx.flo": {
        source: "iana",
        extensions: ["flo"]
      },
      "application/vnd.micrografx.igx": {
        source: "iana",
        extensions: ["igx"]
      },
      "application/vnd.microsoft.portable-executable": {
        source: "iana"
      },
      "application/vnd.microsoft.windows.thumbnail-cache": {
        source: "iana"
      },
      "application/vnd.miele+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.mif": {
        source: "iana",
        extensions: ["mif"]
      },
      "application/vnd.minisoft-hp3000-save": {
        source: "iana"
      },
      "application/vnd.mitsubishi.misty-guard.trustweb": {
        source: "iana"
      },
      "application/vnd.mobius.daf": {
        source: "iana",
        extensions: ["daf"]
      },
      "application/vnd.mobius.dis": {
        source: "iana",
        extensions: ["dis"]
      },
      "application/vnd.mobius.mbk": {
        source: "iana",
        extensions: ["mbk"]
      },
      "application/vnd.mobius.mqy": {
        source: "iana",
        extensions: ["mqy"]
      },
      "application/vnd.mobius.msl": {
        source: "iana",
        extensions: ["msl"]
      },
      "application/vnd.mobius.plc": {
        source: "iana",
        extensions: ["plc"]
      },
      "application/vnd.mobius.txf": {
        source: "iana",
        extensions: ["txf"]
      },
      "application/vnd.mophun.application": {
        source: "iana",
        extensions: ["mpn"]
      },
      "application/vnd.mophun.certificate": {
        source: "iana",
        extensions: ["mpc"]
      },
      "application/vnd.motorola.flexsuite": {
        source: "iana"
      },
      "application/vnd.motorola.flexsuite.adsi": {
        source: "iana"
      },
      "application/vnd.motorola.flexsuite.fis": {
        source: "iana"
      },
      "application/vnd.motorola.flexsuite.gotap": {
        source: "iana"
      },
      "application/vnd.motorola.flexsuite.kmr": {
        source: "iana"
      },
      "application/vnd.motorola.flexsuite.ttc": {
        source: "iana"
      },
      "application/vnd.motorola.flexsuite.wem": {
        source: "iana"
      },
      "application/vnd.motorola.iprm": {
        source: "iana"
      },
      "application/vnd.mozilla.xul+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xul"]
      },
      "application/vnd.ms-3mfdocument": {
        source: "iana"
      },
      "application/vnd.ms-artgalry": {
        source: "iana",
        extensions: ["cil"]
      },
      "application/vnd.ms-asf": {
        source: "iana"
      },
      "application/vnd.ms-cab-compressed": {
        source: "iana",
        extensions: ["cab"]
      },
      "application/vnd.ms-color.iccprofile": {
        source: "apache"
      },
      "application/vnd.ms-excel": {
        source: "iana",
        compressible: false,
        extensions: ["xls", "xlm", "xla", "xlc", "xlt", "xlw"]
      },
      "application/vnd.ms-excel.addin.macroenabled.12": {
        source: "iana",
        extensions: ["xlam"]
      },
      "application/vnd.ms-excel.sheet.binary.macroenabled.12": {
        source: "iana",
        extensions: ["xlsb"]
      },
      "application/vnd.ms-excel.sheet.macroenabled.12": {
        source: "iana",
        extensions: ["xlsm"]
      },
      "application/vnd.ms-excel.template.macroenabled.12": {
        source: "iana",
        extensions: ["xltm"]
      },
      "application/vnd.ms-fontobject": {
        source: "iana",
        compressible: true,
        extensions: ["eot"]
      },
      "application/vnd.ms-htmlhelp": {
        source: "iana",
        extensions: ["chm"]
      },
      "application/vnd.ms-ims": {
        source: "iana",
        extensions: ["ims"]
      },
      "application/vnd.ms-lrm": {
        source: "iana",
        extensions: ["lrm"]
      },
      "application/vnd.ms-office.activex+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ms-officetheme": {
        source: "iana",
        extensions: ["thmx"]
      },
      "application/vnd.ms-opentype": {
        source: "apache",
        compressible: true
      },
      "application/vnd.ms-outlook": {
        compressible: false,
        extensions: ["msg"]
      },
      "application/vnd.ms-package.obfuscated-opentype": {
        source: "apache"
      },
      "application/vnd.ms-pki.seccat": {
        source: "apache",
        extensions: ["cat"]
      },
      "application/vnd.ms-pki.stl": {
        source: "apache",
        extensions: ["stl"]
      },
      "application/vnd.ms-playready.initiator+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ms-powerpoint": {
        source: "iana",
        compressible: false,
        extensions: ["ppt", "pps", "pot"]
      },
      "application/vnd.ms-powerpoint.addin.macroenabled.12": {
        source: "iana",
        extensions: ["ppam"]
      },
      "application/vnd.ms-powerpoint.presentation.macroenabled.12": {
        source: "iana",
        extensions: ["pptm"]
      },
      "application/vnd.ms-powerpoint.slide.macroenabled.12": {
        source: "iana",
        extensions: ["sldm"]
      },
      "application/vnd.ms-powerpoint.slideshow.macroenabled.12": {
        source: "iana",
        extensions: ["ppsm"]
      },
      "application/vnd.ms-powerpoint.template.macroenabled.12": {
        source: "iana",
        extensions: ["potm"]
      },
      "application/vnd.ms-printdevicecapabilities+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ms-printing.printticket+xml": {
        source: "apache",
        compressible: true
      },
      "application/vnd.ms-printschematicket+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ms-project": {
        source: "iana",
        extensions: ["mpp", "mpt"]
      },
      "application/vnd.ms-tnef": {
        source: "iana"
      },
      "application/vnd.ms-windows.devicepairing": {
        source: "iana"
      },
      "application/vnd.ms-windows.nwprinting.oob": {
        source: "iana"
      },
      "application/vnd.ms-windows.printerpairing": {
        source: "iana"
      },
      "application/vnd.ms-windows.wsd.oob": {
        source: "iana"
      },
      "application/vnd.ms-wmdrm.lic-chlg-req": {
        source: "iana"
      },
      "application/vnd.ms-wmdrm.lic-resp": {
        source: "iana"
      },
      "application/vnd.ms-wmdrm.meter-chlg-req": {
        source: "iana"
      },
      "application/vnd.ms-wmdrm.meter-resp": {
        source: "iana"
      },
      "application/vnd.ms-word.document.macroenabled.12": {
        source: "iana",
        extensions: ["docm"]
      },
      "application/vnd.ms-word.template.macroenabled.12": {
        source: "iana",
        extensions: ["dotm"]
      },
      "application/vnd.ms-works": {
        source: "iana",
        extensions: ["wps", "wks", "wcm", "wdb"]
      },
      "application/vnd.ms-wpl": {
        source: "iana",
        extensions: ["wpl"]
      },
      "application/vnd.ms-xpsdocument": {
        source: "iana",
        compressible: false,
        extensions: ["xps"]
      },
      "application/vnd.msa-disk-image": {
        source: "iana"
      },
      "application/vnd.mseq": {
        source: "iana",
        extensions: ["mseq"]
      },
      "application/vnd.msign": {
        source: "iana"
      },
      "application/vnd.multiad.creator": {
        source: "iana"
      },
      "application/vnd.multiad.creator.cif": {
        source: "iana"
      },
      "application/vnd.music-niff": {
        source: "iana"
      },
      "application/vnd.musician": {
        source: "iana",
        extensions: ["mus"]
      },
      "application/vnd.muvee.style": {
        source: "iana",
        extensions: ["msty"]
      },
      "application/vnd.mynfc": {
        source: "iana",
        extensions: ["taglet"]
      },
      "application/vnd.nacamar.ybrid+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.ncd.control": {
        source: "iana"
      },
      "application/vnd.ncd.reference": {
        source: "iana"
      },
      "application/vnd.nearst.inv+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.nebumind.line": {
        source: "iana"
      },
      "application/vnd.nervana": {
        source: "iana"
      },
      "application/vnd.netfpx": {
        source: "iana"
      },
      "application/vnd.neurolanguage.nlu": {
        source: "iana",
        extensions: ["nlu"]
      },
      "application/vnd.nimn": {
        source: "iana"
      },
      "application/vnd.nintendo.nitro.rom": {
        source: "iana"
      },
      "application/vnd.nintendo.snes.rom": {
        source: "iana"
      },
      "application/vnd.nitf": {
        source: "iana",
        extensions: ["ntf", "nitf"]
      },
      "application/vnd.noblenet-directory": {
        source: "iana",
        extensions: ["nnd"]
      },
      "application/vnd.noblenet-sealer": {
        source: "iana",
        extensions: ["nns"]
      },
      "application/vnd.noblenet-web": {
        source: "iana",
        extensions: ["nnw"]
      },
      "application/vnd.nokia.catalogs": {
        source: "iana"
      },
      "application/vnd.nokia.conml+wbxml": {
        source: "iana"
      },
      "application/vnd.nokia.conml+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.nokia.iptv.config+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.nokia.isds-radio-presets": {
        source: "iana"
      },
      "application/vnd.nokia.landmark+wbxml": {
        source: "iana"
      },
      "application/vnd.nokia.landmark+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.nokia.landmarkcollection+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.nokia.n-gage.ac+xml": {
        source: "iana",
        compressible: true,
        extensions: ["ac"]
      },
      "application/vnd.nokia.n-gage.data": {
        source: "iana",
        extensions: ["ngdat"]
      },
      "application/vnd.nokia.n-gage.symbian.install": {
        source: "iana",
        extensions: ["n-gage"]
      },
      "application/vnd.nokia.ncd": {
        source: "iana"
      },
      "application/vnd.nokia.pcd+wbxml": {
        source: "iana"
      },
      "application/vnd.nokia.pcd+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.nokia.radio-preset": {
        source: "iana",
        extensions: ["rpst"]
      },
      "application/vnd.nokia.radio-presets": {
        source: "iana",
        extensions: ["rpss"]
      },
      "application/vnd.novadigm.edm": {
        source: "iana",
        extensions: ["edm"]
      },
      "application/vnd.novadigm.edx": {
        source: "iana",
        extensions: ["edx"]
      },
      "application/vnd.novadigm.ext": {
        source: "iana",
        extensions: ["ext"]
      },
      "application/vnd.ntt-local.content-share": {
        source: "iana"
      },
      "application/vnd.ntt-local.file-transfer": {
        source: "iana"
      },
      "application/vnd.ntt-local.ogw_remote-access": {
        source: "iana"
      },
      "application/vnd.ntt-local.sip-ta_remote": {
        source: "iana"
      },
      "application/vnd.ntt-local.sip-ta_tcp_stream": {
        source: "iana"
      },
      "application/vnd.oasis.opendocument.chart": {
        source: "iana",
        extensions: ["odc"]
      },
      "application/vnd.oasis.opendocument.chart-template": {
        source: "iana",
        extensions: ["otc"]
      },
      "application/vnd.oasis.opendocument.database": {
        source: "iana",
        extensions: ["odb"]
      },
      "application/vnd.oasis.opendocument.formula": {
        source: "iana",
        extensions: ["odf"]
      },
      "application/vnd.oasis.opendocument.formula-template": {
        source: "iana",
        extensions: ["odft"]
      },
      "application/vnd.oasis.opendocument.graphics": {
        source: "iana",
        compressible: false,
        extensions: ["odg"]
      },
      "application/vnd.oasis.opendocument.graphics-template": {
        source: "iana",
        extensions: ["otg"]
      },
      "application/vnd.oasis.opendocument.image": {
        source: "iana",
        extensions: ["odi"]
      },
      "application/vnd.oasis.opendocument.image-template": {
        source: "iana",
        extensions: ["oti"]
      },
      "application/vnd.oasis.opendocument.presentation": {
        source: "iana",
        compressible: false,
        extensions: ["odp"]
      },
      "application/vnd.oasis.opendocument.presentation-template": {
        source: "iana",
        extensions: ["otp"]
      },
      "application/vnd.oasis.opendocument.spreadsheet": {
        source: "iana",
        compressible: false,
        extensions: ["ods"]
      },
      "application/vnd.oasis.opendocument.spreadsheet-template": {
        source: "iana",
        extensions: ["ots"]
      },
      "application/vnd.oasis.opendocument.text": {
        source: "iana",
        compressible: false,
        extensions: ["odt"]
      },
      "application/vnd.oasis.opendocument.text-master": {
        source: "iana",
        extensions: ["odm"]
      },
      "application/vnd.oasis.opendocument.text-template": {
        source: "iana",
        extensions: ["ott"]
      },
      "application/vnd.oasis.opendocument.text-web": {
        source: "iana",
        extensions: ["oth"]
      },
      "application/vnd.obn": {
        source: "iana"
      },
      "application/vnd.ocf+cbor": {
        source: "iana"
      },
      "application/vnd.oci.image.manifest.v1+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oftn.l10n+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.contentaccessdownload+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.contentaccessstreaming+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.cspg-hexbinary": {
        source: "iana"
      },
      "application/vnd.oipf.dae.svg+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.dae.xhtml+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.mippvcontrolmessage+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.pae.gem": {
        source: "iana"
      },
      "application/vnd.oipf.spdiscovery+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.spdlist+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.ueprofile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oipf.userprofile+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.olpc-sugar": {
        source: "iana",
        extensions: ["xo"]
      },
      "application/vnd.oma-scws-config": {
        source: "iana"
      },
      "application/vnd.oma-scws-http-request": {
        source: "iana"
      },
      "application/vnd.oma-scws-http-response": {
        source: "iana"
      },
      "application/vnd.oma.bcast.associated-procedure-parameter+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.drm-trigger+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.imd+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.ltkm": {
        source: "iana"
      },
      "application/vnd.oma.bcast.notification+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.provisioningtrigger": {
        source: "iana"
      },
      "application/vnd.oma.bcast.sgboot": {
        source: "iana"
      },
      "application/vnd.oma.bcast.sgdd+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.sgdu": {
        source: "iana"
      },
      "application/vnd.oma.bcast.simple-symbol-container": {
        source: "iana"
      },
      "application/vnd.oma.bcast.smartcard-trigger+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.sprov+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.bcast.stkm": {
        source: "iana"
      },
      "application/vnd.oma.cab-address-book+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.cab-feature-handler+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.cab-pcc+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.cab-subs-invite+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.cab-user-prefs+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.dcd": {
        source: "iana"
      },
      "application/vnd.oma.dcdc": {
        source: "iana"
      },
      "application/vnd.oma.dd2+xml": {
        source: "iana",
        compressible: true,
        extensions: ["dd2"]
      },
      "application/vnd.oma.drm.risd+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.group-usage-list+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.lwm2m+cbor": {
        source: "iana"
      },
      "application/vnd.oma.lwm2m+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.lwm2m+tlv": {
        source: "iana"
      },
      "application/vnd.oma.pal+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.poc.detailed-progress-report+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.poc.final-report+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.poc.groups+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.poc.invocation-descriptor+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.poc.optimized-progress-report+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.push": {
        source: "iana"
      },
      "application/vnd.oma.scidm.messages+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oma.xcap-directory+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.omads-email+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/vnd.omads-file+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/vnd.omads-folder+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/vnd.omaloc-supl-init": {
        source: "iana"
      },
      "application/vnd.onepager": {
        source: "iana"
      },
      "application/vnd.onepagertamp": {
        source: "iana"
      },
      "application/vnd.onepagertamx": {
        source: "iana"
      },
      "application/vnd.onepagertat": {
        source: "iana"
      },
      "application/vnd.onepagertatp": {
        source: "iana"
      },
      "application/vnd.onepagertatx": {
        source: "iana"
      },
      "application/vnd.openblox.game+xml": {
        source: "iana",
        compressible: true,
        extensions: ["obgx"]
      },
      "application/vnd.openblox.game-binary": {
        source: "iana"
      },
      "application/vnd.openeye.oeb": {
        source: "iana"
      },
      "application/vnd.openofficeorg.extension": {
        source: "apache",
        extensions: ["oxt"]
      },
      "application/vnd.openstreetmap.data+xml": {
        source: "iana",
        compressible: true,
        extensions: ["osm"]
      },
      "application/vnd.opentimestamps.ots": {
        source: "iana"
      },
      "application/vnd.openxmlformats-officedocument.custom-properties+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.customxmlproperties+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawing+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawingml.chart+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawingml.chartshapes+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawingml.diagramdata+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.extended-properties+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.commentauthors+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.comments+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.notesmaster+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.notesslide+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.presentation": {
        source: "iana",
        compressible: false,
        extensions: ["pptx"]
      },
      "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.presprops+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slide": {
        source: "iana",
        extensions: ["sldx"]
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slide+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slidelayout+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slidemaster+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slideshow": {
        source: "iana",
        extensions: ["ppsx"]
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.tablestyles+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.tags+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.template": {
        source: "iana",
        extensions: ["potx"]
      },
      "application/vnd.openxmlformats-officedocument.presentationml.template.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.presentationml.viewprops+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.connections+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": {
        source: "iana",
        compressible: false,
        extensions: ["xlsx"]
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.template": {
        source: "iana",
        extensions: ["xltx"]
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.theme+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.themeoverride+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.vmldrawing": {
        source: "iana"
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document": {
        source: "iana",
        compressible: false,
        extensions: ["docx"]
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template": {
        source: "iana",
        extensions: ["dotx"]
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-package.core-properties+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-package.digital-signature-xmlsignature+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.openxmlformats-package.relationships+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oracle.resource+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.orange.indata": {
        source: "iana"
      },
      "application/vnd.osa.netdeploy": {
        source: "iana"
      },
      "application/vnd.osgeo.mapguide.package": {
        source: "iana",
        extensions: ["mgp"]
      },
      "application/vnd.osgi.bundle": {
        source: "iana"
      },
      "application/vnd.osgi.dp": {
        source: "iana",
        extensions: ["dp"]
      },
      "application/vnd.osgi.subsystem": {
        source: "iana",
        extensions: ["esa"]
      },
      "application/vnd.otps.ct-kip+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.oxli.countgraph": {
        source: "iana"
      },
      "application/vnd.pagerduty+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.palm": {
        source: "iana",
        extensions: ["pdb", "pqa", "oprc"]
      },
      "application/vnd.panoply": {
        source: "iana"
      },
      "application/vnd.paos.xml": {
        source: "iana"
      },
      "application/vnd.patentdive": {
        source: "iana"
      },
      "application/vnd.patientecommsdoc": {
        source: "iana"
      },
      "application/vnd.pawaafile": {
        source: "iana",
        extensions: ["paw"]
      },
      "application/vnd.pcos": {
        source: "iana"
      },
      "application/vnd.pg.format": {
        source: "iana",
        extensions: ["str"]
      },
      "application/vnd.pg.osasli": {
        source: "iana",
        extensions: ["ei6"]
      },
      "application/vnd.piaccess.application-licence": {
        source: "iana"
      },
      "application/vnd.picsel": {
        source: "iana",
        extensions: ["efif"]
      },
      "application/vnd.pmi.widget": {
        source: "iana",
        extensions: ["wg"]
      },
      "application/vnd.poc.group-advertisement+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.pocketlearn": {
        source: "iana",
        extensions: ["plf"]
      },
      "application/vnd.powerbuilder6": {
        source: "iana",
        extensions: ["pbd"]
      },
      "application/vnd.powerbuilder6-s": {
        source: "iana"
      },
      "application/vnd.powerbuilder7": {
        source: "iana"
      },
      "application/vnd.powerbuilder7-s": {
        source: "iana"
      },
      "application/vnd.powerbuilder75": {
        source: "iana"
      },
      "application/vnd.powerbuilder75-s": {
        source: "iana"
      },
      "application/vnd.preminet": {
        source: "iana"
      },
      "application/vnd.previewsystems.box": {
        source: "iana",
        extensions: ["box"]
      },
      "application/vnd.proteus.magazine": {
        source: "iana",
        extensions: ["mgz"]
      },
      "application/vnd.psfs": {
        source: "iana"
      },
      "application/vnd.publishare-delta-tree": {
        source: "iana",
        extensions: ["qps"]
      },
      "application/vnd.pvi.ptid1": {
        source: "iana",
        extensions: ["ptid"]
      },
      "application/vnd.pwg-multiplexed": {
        source: "iana"
      },
      "application/vnd.pwg-xhtml-print+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.qualcomm.brew-app-res": {
        source: "iana"
      },
      "application/vnd.quarantainenet": {
        source: "iana"
      },
      "application/vnd.quark.quarkxpress": {
        source: "iana",
        extensions: ["qxd", "qxt", "qwd", "qwt", "qxl", "qxb"]
      },
      "application/vnd.quobject-quoxdocument": {
        source: "iana"
      },
      "application/vnd.radisys.moml+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-audit+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-audit-conf+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-audit-conn+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-audit-dialog+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-audit-stream+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-conf+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog-base+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog-fax-detect+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog-fax-sendrecv+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog-group+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog-speech+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.radisys.msml-dialog-transform+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.rainstor.data": {
        source: "iana"
      },
      "application/vnd.rapid": {
        source: "iana"
      },
      "application/vnd.rar": {
        source: "iana",
        extensions: ["rar"]
      },
      "application/vnd.realvnc.bed": {
        source: "iana",
        extensions: ["bed"]
      },
      "application/vnd.recordare.musicxml": {
        source: "iana",
        extensions: ["mxl"]
      },
      "application/vnd.recordare.musicxml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["musicxml"]
      },
      "application/vnd.renlearn.rlprint": {
        source: "iana"
      },
      "application/vnd.resilient.logic": {
        source: "iana"
      },
      "application/vnd.restful+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.rig.cryptonote": {
        source: "iana",
        extensions: ["cryptonote"]
      },
      "application/vnd.rim.cod": {
        source: "apache",
        extensions: ["cod"]
      },
      "application/vnd.rn-realmedia": {
        source: "apache",
        extensions: ["rm"]
      },
      "application/vnd.rn-realmedia-vbr": {
        source: "apache",
        extensions: ["rmvb"]
      },
      "application/vnd.route66.link66+xml": {
        source: "iana",
        compressible: true,
        extensions: ["link66"]
      },
      "application/vnd.rs-274x": {
        source: "iana"
      },
      "application/vnd.ruckus.download": {
        source: "iana"
      },
      "application/vnd.s3sms": {
        source: "iana"
      },
      "application/vnd.sailingtracker.track": {
        source: "iana",
        extensions: ["st"]
      },
      "application/vnd.sar": {
        source: "iana"
      },
      "application/vnd.sbm.cid": {
        source: "iana"
      },
      "application/vnd.sbm.mid2": {
        source: "iana"
      },
      "application/vnd.scribus": {
        source: "iana"
      },
      "application/vnd.sealed.3df": {
        source: "iana"
      },
      "application/vnd.sealed.csf": {
        source: "iana"
      },
      "application/vnd.sealed.doc": {
        source: "iana"
      },
      "application/vnd.sealed.eml": {
        source: "iana"
      },
      "application/vnd.sealed.mht": {
        source: "iana"
      },
      "application/vnd.sealed.net": {
        source: "iana"
      },
      "application/vnd.sealed.ppt": {
        source: "iana"
      },
      "application/vnd.sealed.tiff": {
        source: "iana"
      },
      "application/vnd.sealed.xls": {
        source: "iana"
      },
      "application/vnd.sealedmedia.softseal.html": {
        source: "iana"
      },
      "application/vnd.sealedmedia.softseal.pdf": {
        source: "iana"
      },
      "application/vnd.seemail": {
        source: "iana",
        extensions: ["see"]
      },
      "application/vnd.seis+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.sema": {
        source: "iana",
        extensions: ["sema"]
      },
      "application/vnd.semd": {
        source: "iana",
        extensions: ["semd"]
      },
      "application/vnd.semf": {
        source: "iana",
        extensions: ["semf"]
      },
      "application/vnd.shade-save-file": {
        source: "iana"
      },
      "application/vnd.shana.informed.formdata": {
        source: "iana",
        extensions: ["ifm"]
      },
      "application/vnd.shana.informed.formtemplate": {
        source: "iana",
        extensions: ["itp"]
      },
      "application/vnd.shana.informed.interchange": {
        source: "iana",
        extensions: ["iif"]
      },
      "application/vnd.shana.informed.package": {
        source: "iana",
        extensions: ["ipk"]
      },
      "application/vnd.shootproof+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.shopkick+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.shp": {
        source: "iana"
      },
      "application/vnd.shx": {
        source: "iana"
      },
      "application/vnd.sigrok.session": {
        source: "iana"
      },
      "application/vnd.simtech-mindmapper": {
        source: "iana",
        extensions: ["twd", "twds"]
      },
      "application/vnd.siren+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.smaf": {
        source: "iana",
        extensions: ["mmf"]
      },
      "application/vnd.smart.notebook": {
        source: "iana"
      },
      "application/vnd.smart.teacher": {
        source: "iana",
        extensions: ["teacher"]
      },
      "application/vnd.snesdev-page-table": {
        source: "iana"
      },
      "application/vnd.software602.filler.form+xml": {
        source: "iana",
        compressible: true,
        extensions: ["fo"]
      },
      "application/vnd.software602.filler.form-xml-zip": {
        source: "iana"
      },
      "application/vnd.solent.sdkm+xml": {
        source: "iana",
        compressible: true,
        extensions: ["sdkm", "sdkd"]
      },
      "application/vnd.spotfire.dxp": {
        source: "iana",
        extensions: ["dxp"]
      },
      "application/vnd.spotfire.sfs": {
        source: "iana",
        extensions: ["sfs"]
      },
      "application/vnd.sqlite3": {
        source: "iana"
      },
      "application/vnd.sss-cod": {
        source: "iana"
      },
      "application/vnd.sss-dtf": {
        source: "iana"
      },
      "application/vnd.sss-ntf": {
        source: "iana"
      },
      "application/vnd.stardivision.calc": {
        source: "apache",
        extensions: ["sdc"]
      },
      "application/vnd.stardivision.draw": {
        source: "apache",
        extensions: ["sda"]
      },
      "application/vnd.stardivision.impress": {
        source: "apache",
        extensions: ["sdd"]
      },
      "application/vnd.stardivision.math": {
        source: "apache",
        extensions: ["smf"]
      },
      "application/vnd.stardivision.writer": {
        source: "apache",
        extensions: ["sdw", "vor"]
      },
      "application/vnd.stardivision.writer-global": {
        source: "apache",
        extensions: ["sgl"]
      },
      "application/vnd.stepmania.package": {
        source: "iana",
        extensions: ["smzip"]
      },
      "application/vnd.stepmania.stepchart": {
        source: "iana",
        extensions: ["sm"]
      },
      "application/vnd.street-stream": {
        source: "iana"
      },
      "application/vnd.sun.wadl+xml": {
        source: "iana",
        compressible: true,
        extensions: ["wadl"]
      },
      "application/vnd.sun.xml.calc": {
        source: "apache",
        extensions: ["sxc"]
      },
      "application/vnd.sun.xml.calc.template": {
        source: "apache",
        extensions: ["stc"]
      },
      "application/vnd.sun.xml.draw": {
        source: "apache",
        extensions: ["sxd"]
      },
      "application/vnd.sun.xml.draw.template": {
        source: "apache",
        extensions: ["std"]
      },
      "application/vnd.sun.xml.impress": {
        source: "apache",
        extensions: ["sxi"]
      },
      "application/vnd.sun.xml.impress.template": {
        source: "apache",
        extensions: ["sti"]
      },
      "application/vnd.sun.xml.math": {
        source: "apache",
        extensions: ["sxm"]
      },
      "application/vnd.sun.xml.writer": {
        source: "apache",
        extensions: ["sxw"]
      },
      "application/vnd.sun.xml.writer.global": {
        source: "apache",
        extensions: ["sxg"]
      },
      "application/vnd.sun.xml.writer.template": {
        source: "apache",
        extensions: ["stw"]
      },
      "application/vnd.sus-calendar": {
        source: "iana",
        extensions: ["sus", "susp"]
      },
      "application/vnd.svd": {
        source: "iana",
        extensions: ["svd"]
      },
      "application/vnd.swiftview-ics": {
        source: "iana"
      },
      "application/vnd.sycle+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.syft+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.symbian.install": {
        source: "apache",
        extensions: ["sis", "sisx"]
      },
      "application/vnd.syncml+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["xsm"]
      },
      "application/vnd.syncml.dm+wbxml": {
        source: "iana",
        charset: "UTF-8",
        extensions: ["bdm"]
      },
      "application/vnd.syncml.dm+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["xdm"]
      },
      "application/vnd.syncml.dm.notification": {
        source: "iana"
      },
      "application/vnd.syncml.dmddf+wbxml": {
        source: "iana"
      },
      "application/vnd.syncml.dmddf+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["ddf"]
      },
      "application/vnd.syncml.dmtnds+wbxml": {
        source: "iana"
      },
      "application/vnd.syncml.dmtnds+xml": {
        source: "iana",
        charset: "UTF-8",
        compressible: true
      },
      "application/vnd.syncml.ds.notification": {
        source: "iana"
      },
      "application/vnd.tableschema+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.tao.intent-module-archive": {
        source: "iana",
        extensions: ["tao"]
      },
      "application/vnd.tcpdump.pcap": {
        source: "iana",
        extensions: ["pcap", "cap", "dmp"]
      },
      "application/vnd.think-cell.ppttc+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.tmd.mediaflex.api+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.tml": {
        source: "iana"
      },
      "application/vnd.tmobile-livetv": {
        source: "iana",
        extensions: ["tmo"]
      },
      "application/vnd.tri.onesource": {
        source: "iana"
      },
      "application/vnd.trid.tpt": {
        source: "iana",
        extensions: ["tpt"]
      },
      "application/vnd.triscape.mxs": {
        source: "iana",
        extensions: ["mxs"]
      },
      "application/vnd.trueapp": {
        source: "iana",
        extensions: ["tra"]
      },
      "application/vnd.truedoc": {
        source: "iana"
      },
      "application/vnd.ubisoft.webplayer": {
        source: "iana"
      },
      "application/vnd.ufdl": {
        source: "iana",
        extensions: ["ufd", "ufdl"]
      },
      "application/vnd.uiq.theme": {
        source: "iana",
        extensions: ["utz"]
      },
      "application/vnd.umajin": {
        source: "iana",
        extensions: ["umj"]
      },
      "application/vnd.unity": {
        source: "iana",
        extensions: ["unityweb"]
      },
      "application/vnd.uoml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["uoml"]
      },
      "application/vnd.uplanet.alert": {
        source: "iana"
      },
      "application/vnd.uplanet.alert-wbxml": {
        source: "iana"
      },
      "application/vnd.uplanet.bearer-choice": {
        source: "iana"
      },
      "application/vnd.uplanet.bearer-choice-wbxml": {
        source: "iana"
      },
      "application/vnd.uplanet.cacheop": {
        source: "iana"
      },
      "application/vnd.uplanet.cacheop-wbxml": {
        source: "iana"
      },
      "application/vnd.uplanet.channel": {
        source: "iana"
      },
      "application/vnd.uplanet.channel-wbxml": {
        source: "iana"
      },
      "application/vnd.uplanet.list": {
        source: "iana"
      },
      "application/vnd.uplanet.list-wbxml": {
        source: "iana"
      },
      "application/vnd.uplanet.listcmd": {
        source: "iana"
      },
      "application/vnd.uplanet.listcmd-wbxml": {
        source: "iana"
      },
      "application/vnd.uplanet.signal": {
        source: "iana"
      },
      "application/vnd.uri-map": {
        source: "iana"
      },
      "application/vnd.valve.source.material": {
        source: "iana"
      },
      "application/vnd.vcx": {
        source: "iana",
        extensions: ["vcx"]
      },
      "application/vnd.vd-study": {
        source: "iana"
      },
      "application/vnd.vectorworks": {
        source: "iana"
      },
      "application/vnd.vel+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.verimatrix.vcas": {
        source: "iana"
      },
      "application/vnd.veritone.aion+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.veryant.thin": {
        source: "iana"
      },
      "application/vnd.ves.encrypted": {
        source: "iana"
      },
      "application/vnd.vidsoft.vidconference": {
        source: "iana"
      },
      "application/vnd.visio": {
        source: "iana",
        extensions: ["vsd", "vst", "vss", "vsw"]
      },
      "application/vnd.visionary": {
        source: "iana",
        extensions: ["vis"]
      },
      "application/vnd.vividence.scriptfile": {
        source: "iana"
      },
      "application/vnd.vsf": {
        source: "iana",
        extensions: ["vsf"]
      },
      "application/vnd.wap.sic": {
        source: "iana"
      },
      "application/vnd.wap.slc": {
        source: "iana"
      },
      "application/vnd.wap.wbxml": {
        source: "iana",
        charset: "UTF-8",
        extensions: ["wbxml"]
      },
      "application/vnd.wap.wmlc": {
        source: "iana",
        extensions: ["wmlc"]
      },
      "application/vnd.wap.wmlscriptc": {
        source: "iana",
        extensions: ["wmlsc"]
      },
      "application/vnd.webturbo": {
        source: "iana",
        extensions: ["wtb"]
      },
      "application/vnd.wfa.dpp": {
        source: "iana"
      },
      "application/vnd.wfa.p2p": {
        source: "iana"
      },
      "application/vnd.wfa.wsc": {
        source: "iana"
      },
      "application/vnd.windows.devicepairing": {
        source: "iana"
      },
      "application/vnd.wmc": {
        source: "iana"
      },
      "application/vnd.wmf.bootstrap": {
        source: "iana"
      },
      "application/vnd.wolfram.mathematica": {
        source: "iana"
      },
      "application/vnd.wolfram.mathematica.package": {
        source: "iana"
      },
      "application/vnd.wolfram.player": {
        source: "iana",
        extensions: ["nbp"]
      },
      "application/vnd.wordperfect": {
        source: "iana",
        extensions: ["wpd"]
      },
      "application/vnd.wqd": {
        source: "iana",
        extensions: ["wqd"]
      },
      "application/vnd.wrq-hp3000-labelled": {
        source: "iana"
      },
      "application/vnd.wt.stf": {
        source: "iana",
        extensions: ["stf"]
      },
      "application/vnd.wv.csp+wbxml": {
        source: "iana"
      },
      "application/vnd.wv.csp+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.wv.ssp+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.xacml+json": {
        source: "iana",
        compressible: true
      },
      "application/vnd.xara": {
        source: "iana",
        extensions: ["xar"]
      },
      "application/vnd.xfdl": {
        source: "iana",
        extensions: ["xfdl"]
      },
      "application/vnd.xfdl.webform": {
        source: "iana"
      },
      "application/vnd.xmi+xml": {
        source: "iana",
        compressible: true
      },
      "application/vnd.xmpie.cpkg": {
        source: "iana"
      },
      "application/vnd.xmpie.dpkg": {
        source: "iana"
      },
      "application/vnd.xmpie.plan": {
        source: "iana"
      },
      "application/vnd.xmpie.ppkg": {
        source: "iana"
      },
      "application/vnd.xmpie.xlim": {
        source: "iana"
      },
      "application/vnd.yamaha.hv-dic": {
        source: "iana",
        extensions: ["hvd"]
      },
      "application/vnd.yamaha.hv-script": {
        source: "iana",
        extensions: ["hvs"]
      },
      "application/vnd.yamaha.hv-voice": {
        source: "iana",
        extensions: ["hvp"]
      },
      "application/vnd.yamaha.openscoreformat": {
        source: "iana",
        extensions: ["osf"]
      },
      "application/vnd.yamaha.openscoreformat.osfpvg+xml": {
        source: "iana",
        compressible: true,
        extensions: ["osfpvg"]
      },
      "application/vnd.yamaha.remote-setup": {
        source: "iana"
      },
      "application/vnd.yamaha.smaf-audio": {
        source: "iana",
        extensions: ["saf"]
      },
      "application/vnd.yamaha.smaf-phrase": {
        source: "iana",
        extensions: ["spf"]
      },
      "application/vnd.yamaha.through-ngn": {
        source: "iana"
      },
      "application/vnd.yamaha.tunnel-udpencap": {
        source: "iana"
      },
      "application/vnd.yaoweme": {
        source: "iana"
      },
      "application/vnd.yellowriver-custom-menu": {
        source: "iana",
        extensions: ["cmp"]
      },
      "application/vnd.youtube.yt": {
        source: "iana"
      },
      "application/vnd.zul": {
        source: "iana",
        extensions: ["zir", "zirz"]
      },
      "application/vnd.zzazz.deck+xml": {
        source: "iana",
        compressible: true,
        extensions: ["zaz"]
      },
      "application/voicexml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["vxml"]
      },
      "application/voucher-cms+json": {
        source: "iana",
        compressible: true
      },
      "application/vq-rtcpxr": {
        source: "iana"
      },
      "application/wasm": {
        source: "iana",
        compressible: true,
        extensions: ["wasm"]
      },
      "application/watcherinfo+xml": {
        source: "iana",
        compressible: true,
        extensions: ["wif"]
      },
      "application/webpush-options+json": {
        source: "iana",
        compressible: true
      },
      "application/whoispp-query": {
        source: "iana"
      },
      "application/whoispp-response": {
        source: "iana"
      },
      "application/widget": {
        source: "iana",
        extensions: ["wgt"]
      },
      "application/winhlp": {
        source: "apache",
        extensions: ["hlp"]
      },
      "application/wita": {
        source: "iana"
      },
      "application/wordperfect5.1": {
        source: "iana"
      },
      "application/wsdl+xml": {
        source: "iana",
        compressible: true,
        extensions: ["wsdl"]
      },
      "application/wspolicy+xml": {
        source: "iana",
        compressible: true,
        extensions: ["wspolicy"]
      },
      "application/x-7z-compressed": {
        source: "apache",
        compressible: false,
        extensions: ["7z"]
      },
      "application/x-abiword": {
        source: "apache",
        extensions: ["abw"]
      },
      "application/x-ace-compressed": {
        source: "apache",
        extensions: ["ace"]
      },
      "application/x-amf": {
        source: "apache"
      },
      "application/x-apple-diskimage": {
        source: "apache",
        extensions: ["dmg"]
      },
      "application/x-arj": {
        compressible: false,
        extensions: ["arj"]
      },
      "application/x-authorware-bin": {
        source: "apache",
        extensions: ["aab", "x32", "u32", "vox"]
      },
      "application/x-authorware-map": {
        source: "apache",
        extensions: ["aam"]
      },
      "application/x-authorware-seg": {
        source: "apache",
        extensions: ["aas"]
      },
      "application/x-bcpio": {
        source: "apache",
        extensions: ["bcpio"]
      },
      "application/x-bdoc": {
        compressible: false,
        extensions: ["bdoc"]
      },
      "application/x-bittorrent": {
        source: "apache",
        extensions: ["torrent"]
      },
      "application/x-blorb": {
        source: "apache",
        extensions: ["blb", "blorb"]
      },
      "application/x-bzip": {
        source: "apache",
        compressible: false,
        extensions: ["bz"]
      },
      "application/x-bzip2": {
        source: "apache",
        compressible: false,
        extensions: ["bz2", "boz"]
      },
      "application/x-cbr": {
        source: "apache",
        extensions: ["cbr", "cba", "cbt", "cbz", "cb7"]
      },
      "application/x-cdlink": {
        source: "apache",
        extensions: ["vcd"]
      },
      "application/x-cfs-compressed": {
        source: "apache",
        extensions: ["cfs"]
      },
      "application/x-chat": {
        source: "apache",
        extensions: ["chat"]
      },
      "application/x-chess-pgn": {
        source: "apache",
        extensions: ["pgn"]
      },
      "application/x-chrome-extension": {
        extensions: ["crx"]
      },
      "application/x-cocoa": {
        source: "nginx",
        extensions: ["cco"]
      },
      "application/x-compress": {
        source: "apache"
      },
      "application/x-conference": {
        source: "apache",
        extensions: ["nsc"]
      },
      "application/x-cpio": {
        source: "apache",
        extensions: ["cpio"]
      },
      "application/x-csh": {
        source: "apache",
        extensions: ["csh"]
      },
      "application/x-deb": {
        compressible: false
      },
      "application/x-debian-package": {
        source: "apache",
        extensions: ["deb", "udeb"]
      },
      "application/x-dgc-compressed": {
        source: "apache",
        extensions: ["dgc"]
      },
      "application/x-director": {
        source: "apache",
        extensions: ["dir", "dcr", "dxr", "cst", "cct", "cxt", "w3d", "fgd", "swa"]
      },
      "application/x-doom": {
        source: "apache",
        extensions: ["wad"]
      },
      "application/x-dtbncx+xml": {
        source: "apache",
        compressible: true,
        extensions: ["ncx"]
      },
      "application/x-dtbook+xml": {
        source: "apache",
        compressible: true,
        extensions: ["dtb"]
      },
      "application/x-dtbresource+xml": {
        source: "apache",
        compressible: true,
        extensions: ["res"]
      },
      "application/x-dvi": {
        source: "apache",
        compressible: false,
        extensions: ["dvi"]
      },
      "application/x-envoy": {
        source: "apache",
        extensions: ["evy"]
      },
      "application/x-eva": {
        source: "apache",
        extensions: ["eva"]
      },
      "application/x-font-bdf": {
        source: "apache",
        extensions: ["bdf"]
      },
      "application/x-font-dos": {
        source: "apache"
      },
      "application/x-font-framemaker": {
        source: "apache"
      },
      "application/x-font-ghostscript": {
        source: "apache",
        extensions: ["gsf"]
      },
      "application/x-font-libgrx": {
        source: "apache"
      },
      "application/x-font-linux-psf": {
        source: "apache",
        extensions: ["psf"]
      },
      "application/x-font-pcf": {
        source: "apache",
        extensions: ["pcf"]
      },
      "application/x-font-snf": {
        source: "apache",
        extensions: ["snf"]
      },
      "application/x-font-speedo": {
        source: "apache"
      },
      "application/x-font-sunos-news": {
        source: "apache"
      },
      "application/x-font-type1": {
        source: "apache",
        extensions: ["pfa", "pfb", "pfm", "afm"]
      },
      "application/x-font-vfont": {
        source: "apache"
      },
      "application/x-freearc": {
        source: "apache",
        extensions: ["arc"]
      },
      "application/x-futuresplash": {
        source: "apache",
        extensions: ["spl"]
      },
      "application/x-gca-compressed": {
        source: "apache",
        extensions: ["gca"]
      },
      "application/x-glulx": {
        source: "apache",
        extensions: ["ulx"]
      },
      "application/x-gnumeric": {
        source: "apache",
        extensions: ["gnumeric"]
      },
      "application/x-gramps-xml": {
        source: "apache",
        extensions: ["gramps"]
      },
      "application/x-gtar": {
        source: "apache",
        extensions: ["gtar"]
      },
      "application/x-gzip": {
        source: "apache"
      },
      "application/x-hdf": {
        source: "apache",
        extensions: ["hdf"]
      },
      "application/x-httpd-php": {
        compressible: true,
        extensions: ["php"]
      },
      "application/x-install-instructions": {
        source: "apache",
        extensions: ["install"]
      },
      "application/x-iso9660-image": {
        source: "apache",
        extensions: ["iso"]
      },
      "application/x-iwork-keynote-sffkey": {
        extensions: ["key"]
      },
      "application/x-iwork-numbers-sffnumbers": {
        extensions: ["numbers"]
      },
      "application/x-iwork-pages-sffpages": {
        extensions: ["pages"]
      },
      "application/x-java-archive-diff": {
        source: "nginx",
        extensions: ["jardiff"]
      },
      "application/x-java-jnlp-file": {
        source: "apache",
        compressible: false,
        extensions: ["jnlp"]
      },
      "application/x-javascript": {
        compressible: true
      },
      "application/x-keepass2": {
        extensions: ["kdbx"]
      },
      "application/x-latex": {
        source: "apache",
        compressible: false,
        extensions: ["latex"]
      },
      "application/x-lua-bytecode": {
        extensions: ["luac"]
      },
      "application/x-lzh-compressed": {
        source: "apache",
        extensions: ["lzh", "lha"]
      },
      "application/x-makeself": {
        source: "nginx",
        extensions: ["run"]
      },
      "application/x-mie": {
        source: "apache",
        extensions: ["mie"]
      },
      "application/x-mobipocket-ebook": {
        source: "apache",
        extensions: ["prc", "mobi"]
      },
      "application/x-mpegurl": {
        compressible: false
      },
      "application/x-ms-application": {
        source: "apache",
        extensions: ["application"]
      },
      "application/x-ms-shortcut": {
        source: "apache",
        extensions: ["lnk"]
      },
      "application/x-ms-wmd": {
        source: "apache",
        extensions: ["wmd"]
      },
      "application/x-ms-wmz": {
        source: "apache",
        extensions: ["wmz"]
      },
      "application/x-ms-xbap": {
        source: "apache",
        extensions: ["xbap"]
      },
      "application/x-msaccess": {
        source: "apache",
        extensions: ["mdb"]
      },
      "application/x-msbinder": {
        source: "apache",
        extensions: ["obd"]
      },
      "application/x-mscardfile": {
        source: "apache",
        extensions: ["crd"]
      },
      "application/x-msclip": {
        source: "apache",
        extensions: ["clp"]
      },
      "application/x-msdos-program": {
        extensions: ["exe"]
      },
      "application/x-msdownload": {
        source: "apache",
        extensions: ["exe", "dll", "com", "bat", "msi"]
      },
      "application/x-msmediaview": {
        source: "apache",
        extensions: ["mvb", "m13", "m14"]
      },
      "application/x-msmetafile": {
        source: "apache",
        extensions: ["wmf", "wmz", "emf", "emz"]
      },
      "application/x-msmoney": {
        source: "apache",
        extensions: ["mny"]
      },
      "application/x-mspublisher": {
        source: "apache",
        extensions: ["pub"]
      },
      "application/x-msschedule": {
        source: "apache",
        extensions: ["scd"]
      },
      "application/x-msterminal": {
        source: "apache",
        extensions: ["trm"]
      },
      "application/x-mswrite": {
        source: "apache",
        extensions: ["wri"]
      },
      "application/x-netcdf": {
        source: "apache",
        extensions: ["nc", "cdf"]
      },
      "application/x-ns-proxy-autoconfig": {
        compressible: true,
        extensions: ["pac"]
      },
      "application/x-nzb": {
        source: "apache",
        extensions: ["nzb"]
      },
      "application/x-perl": {
        source: "nginx",
        extensions: ["pl", "pm"]
      },
      "application/x-pilot": {
        source: "nginx",
        extensions: ["prc", "pdb"]
      },
      "application/x-pkcs12": {
        source: "apache",
        compressible: false,
        extensions: ["p12", "pfx"]
      },
      "application/x-pkcs7-certificates": {
        source: "apache",
        extensions: ["p7b", "spc"]
      },
      "application/x-pkcs7-certreqresp": {
        source: "apache",
        extensions: ["p7r"]
      },
      "application/x-pki-message": {
        source: "iana"
      },
      "application/x-rar-compressed": {
        source: "apache",
        compressible: false,
        extensions: ["rar"]
      },
      "application/x-redhat-package-manager": {
        source: "nginx",
        extensions: ["rpm"]
      },
      "application/x-research-info-systems": {
        source: "apache",
        extensions: ["ris"]
      },
      "application/x-sea": {
        source: "nginx",
        extensions: ["sea"]
      },
      "application/x-sh": {
        source: "apache",
        compressible: true,
        extensions: ["sh"]
      },
      "application/x-shar": {
        source: "apache",
        extensions: ["shar"]
      },
      "application/x-shockwave-flash": {
        source: "apache",
        compressible: false,
        extensions: ["swf"]
      },
      "application/x-silverlight-app": {
        source: "apache",
        extensions: ["xap"]
      },
      "application/x-sql": {
        source: "apache",
        extensions: ["sql"]
      },
      "application/x-stuffit": {
        source: "apache",
        compressible: false,
        extensions: ["sit"]
      },
      "application/x-stuffitx": {
        source: "apache",
        extensions: ["sitx"]
      },
      "application/x-subrip": {
        source: "apache",
        extensions: ["srt"]
      },
      "application/x-sv4cpio": {
        source: "apache",
        extensions: ["sv4cpio"]
      },
      "application/x-sv4crc": {
        source: "apache",
        extensions: ["sv4crc"]
      },
      "application/x-t3vm-image": {
        source: "apache",
        extensions: ["t3"]
      },
      "application/x-tads": {
        source: "apache",
        extensions: ["gam"]
      },
      "application/x-tar": {
        source: "apache",
        compressible: true,
        extensions: ["tar"]
      },
      "application/x-tcl": {
        source: "apache",
        extensions: ["tcl", "tk"]
      },
      "application/x-tex": {
        source: "apache",
        extensions: ["tex"]
      },
      "application/x-tex-tfm": {
        source: "apache",
        extensions: ["tfm"]
      },
      "application/x-texinfo": {
        source: "apache",
        extensions: ["texinfo", "texi"]
      },
      "application/x-tgif": {
        source: "apache",
        extensions: ["obj"]
      },
      "application/x-ustar": {
        source: "apache",
        extensions: ["ustar"]
      },
      "application/x-virtualbox-hdd": {
        compressible: true,
        extensions: ["hdd"]
      },
      "application/x-virtualbox-ova": {
        compressible: true,
        extensions: ["ova"]
      },
      "application/x-virtualbox-ovf": {
        compressible: true,
        extensions: ["ovf"]
      },
      "application/x-virtualbox-vbox": {
        compressible: true,
        extensions: ["vbox"]
      },
      "application/x-virtualbox-vbox-extpack": {
        compressible: false,
        extensions: ["vbox-extpack"]
      },
      "application/x-virtualbox-vdi": {
        compressible: true,
        extensions: ["vdi"]
      },
      "application/x-virtualbox-vhd": {
        compressible: true,
        extensions: ["vhd"]
      },
      "application/x-virtualbox-vmdk": {
        compressible: true,
        extensions: ["vmdk"]
      },
      "application/x-wais-source": {
        source: "apache",
        extensions: ["src"]
      },
      "application/x-web-app-manifest+json": {
        compressible: true,
        extensions: ["webapp"]
      },
      "application/x-www-form-urlencoded": {
        source: "iana",
        compressible: true
      },
      "application/x-x509-ca-cert": {
        source: "iana",
        extensions: ["der", "crt", "pem"]
      },
      "application/x-x509-ca-ra-cert": {
        source: "iana"
      },
      "application/x-x509-next-ca-cert": {
        source: "iana"
      },
      "application/x-xfig": {
        source: "apache",
        extensions: ["fig"]
      },
      "application/x-xliff+xml": {
        source: "apache",
        compressible: true,
        extensions: ["xlf"]
      },
      "application/x-xpinstall": {
        source: "apache",
        compressible: false,
        extensions: ["xpi"]
      },
      "application/x-xz": {
        source: "apache",
        extensions: ["xz"]
      },
      "application/x-zmachine": {
        source: "apache",
        extensions: ["z1", "z2", "z3", "z4", "z5", "z6", "z7", "z8"]
      },
      "application/x400-bp": {
        source: "iana"
      },
      "application/xacml+xml": {
        source: "iana",
        compressible: true
      },
      "application/xaml+xml": {
        source: "apache",
        compressible: true,
        extensions: ["xaml"]
      },
      "application/xcap-att+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xav"]
      },
      "application/xcap-caps+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xca"]
      },
      "application/xcap-diff+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xdf"]
      },
      "application/xcap-el+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xel"]
      },
      "application/xcap-error+xml": {
        source: "iana",
        compressible: true
      },
      "application/xcap-ns+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xns"]
      },
      "application/xcon-conference-info+xml": {
        source: "iana",
        compressible: true
      },
      "application/xcon-conference-info-diff+xml": {
        source: "iana",
        compressible: true
      },
      "application/xenc+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xenc"]
      },
      "application/xhtml+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xhtml", "xht"]
      },
      "application/xhtml-voice+xml": {
        source: "apache",
        compressible: true
      },
      "application/xliff+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xlf"]
      },
      "application/xml": {
        source: "iana",
        compressible: true,
        extensions: ["xml", "xsl", "xsd", "rng"]
      },
      "application/xml-dtd": {
        source: "iana",
        compressible: true,
        extensions: ["dtd"]
      },
      "application/xml-external-parsed-entity": {
        source: "iana"
      },
      "application/xml-patch+xml": {
        source: "iana",
        compressible: true
      },
      "application/xmpp+xml": {
        source: "iana",
        compressible: true
      },
      "application/xop+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xop"]
      },
      "application/xproc+xml": {
        source: "apache",
        compressible: true,
        extensions: ["xpl"]
      },
      "application/xslt+xml": {
        source: "iana",
        compressible: true,
        extensions: ["xsl", "xslt"]
      },
      "application/xspf+xml": {
        source: "apache",
        compressible: true,
        extensions: ["xspf"]
      },
      "application/xv+xml": {
        source: "iana",
        compressible: true,
        extensions: ["mxml", "xhvml", "xvml", "xvm"]
      },
      "application/yang": {
        source: "iana",
        extensions: ["yang"]
      },
      "application/yang-data+json": {
        source: "iana",
        compressible: true
      },
      "application/yang-data+xml": {
        source: "iana",
        compressible: true
      },
      "application/yang-patch+json": {
        source: "iana",
        compressible: true
      },
      "application/yang-patch+xml": {
        source: "iana",
        compressible: true
      },
      "application/yin+xml": {
        source: "iana",
        compressible: true,
        extensions: ["yin"]
      },
      "application/zip": {
        source: "iana",
        compressible: false,
        extensions: ["zip"]
      },
      "application/zlib": {
        source: "iana"
      },
      "application/zstd": {
        source: "iana"
      },
      "audio/1d-interleaved-parityfec": {
        source: "iana"
      },
      "audio/32kadpcm": {
        source: "iana"
      },
      "audio/3gpp": {
        source: "iana",
        compressible: false,
        extensions: ["3gpp"]
      },
      "audio/3gpp2": {
        source: "iana"
      },
      "audio/aac": {
        source: "iana"
      },
      "audio/ac3": {
        source: "iana"
      },
      "audio/adpcm": {
        source: "apache",
        extensions: ["adp"]
      },
      "audio/amr": {
        source: "iana",
        extensions: ["amr"]
      },
      "audio/amr-wb": {
        source: "iana"
      },
      "audio/amr-wb+": {
        source: "iana"
      },
      "audio/aptx": {
        source: "iana"
      },
      "audio/asc": {
        source: "iana"
      },
      "audio/atrac-advanced-lossless": {
        source: "iana"
      },
      "audio/atrac-x": {
        source: "iana"
      },
      "audio/atrac3": {
        source: "iana"
      },
      "audio/basic": {
        source: "iana",
        compressible: false,
        extensions: ["au", "snd"]
      },
      "audio/bv16": {
        source: "iana"
      },
      "audio/bv32": {
        source: "iana"
      },
      "audio/clearmode": {
        source: "iana"
      },
      "audio/cn": {
        source: "iana"
      },
      "audio/dat12": {
        source: "iana"
      },
      "audio/dls": {
        source: "iana"
      },
      "audio/dsr-es201108": {
        source: "iana"
      },
      "audio/dsr-es202050": {
        source: "iana"
      },
      "audio/dsr-es202211": {
        source: "iana"
      },
      "audio/dsr-es202212": {
        source: "iana"
      },
      "audio/dv": {
        source: "iana"
      },
      "audio/dvi4": {
        source: "iana"
      },
      "audio/eac3": {
        source: "iana"
      },
      "audio/encaprtp": {
        source: "iana"
      },
      "audio/evrc": {
        source: "iana"
      },
      "audio/evrc-qcp": {
        source: "iana"
      },
      "audio/evrc0": {
        source: "iana"
      },
      "audio/evrc1": {
        source: "iana"
      },
      "audio/evrcb": {
        source: "iana"
      },
      "audio/evrcb0": {
        source: "iana"
      },
      "audio/evrcb1": {
        source: "iana"
      },
      "audio/evrcnw": {
        source: "iana"
      },
      "audio/evrcnw0": {
        source: "iana"
      },
      "audio/evrcnw1": {
        source: "iana"
      },
      "audio/evrcwb": {
        source: "iana"
      },
      "audio/evrcwb0": {
        source: "iana"
      },
      "audio/evrcwb1": {
        source: "iana"
      },
      "audio/evs": {
        source: "iana"
      },
      "audio/flexfec": {
        source: "iana"
      },
      "audio/fwdred": {
        source: "iana"
      },
      "audio/g711-0": {
        source: "iana"
      },
      "audio/g719": {
        source: "iana"
      },
      "audio/g722": {
        source: "iana"
      },
      "audio/g7221": {
        source: "iana"
      },
      "audio/g723": {
        source: "iana"
      },
      "audio/g726-16": {
        source: "iana"
      },
      "audio/g726-24": {
        source: "iana"
      },
      "audio/g726-32": {
        source: "iana"
      },
      "audio/g726-40": {
        source: "iana"
      },
      "audio/g728": {
        source: "iana"
      },
      "audio/g729": {
        source: "iana"
      },
      "audio/g7291": {
        source: "iana"
      },
      "audio/g729d": {
        source: "iana"
      },
      "audio/g729e": {
        source: "iana"
      },
      "audio/gsm": {
        source: "iana"
      },
      "audio/gsm-efr": {
        source: "iana"
      },
      "audio/gsm-hr-08": {
        source: "iana"
      },
      "audio/ilbc": {
        source: "iana"
      },
      "audio/ip-mr_v2.5": {
        source: "iana"
      },
      "audio/isac": {
        source: "apache"
      },
      "audio/l16": {
        source: "iana"
      },
      "audio/l20": {
        source: "iana"
      },
      "audio/l24": {
        source: "iana",
        compressible: false
      },
      "audio/l8": {
        source: "iana"
      },
      "audio/lpc": {
        source: "iana"
      },
      "audio/melp": {
        source: "iana"
      },
      "audio/melp1200": {
        source: "iana"
      },
      "audio/melp2400": {
        source: "iana"
      },
      "audio/melp600": {
        source: "iana"
      },
      "audio/mhas": {
        source: "iana"
      },
      "audio/midi": {
        source: "apache",
        extensions: ["mid", "midi", "kar", "rmi"]
      },
      "audio/mobile-xmf": {
        source: "iana",
        extensions: ["mxmf"]
      },
      "audio/mp3": {
        compressible: false,
        extensions: ["mp3"]
      },
      "audio/mp4": {
        source: "iana",
        compressible: false,
        extensions: ["m4a", "mp4a"]
      },
      "audio/mp4a-latm": {
        source: "iana"
      },
      "audio/mpa": {
        source: "iana"
      },
      "audio/mpa-robust": {
        source: "iana"
      },
      "audio/mpeg": {
        source: "iana",
        compressible: false,
        extensions: ["mpga", "mp2", "mp2a", "mp3", "m2a", "m3a"]
      },
      "audio/mpeg4-generic": {
        source: "iana"
      },
      "audio/musepack": {
        source: "apache"
      },
      "audio/ogg": {
        source: "iana",
        compressible: false,
        extensions: ["oga", "ogg", "spx", "opus"]
      },
      "audio/opus": {
        source: "iana"
      },
      "audio/parityfec": {
        source: "iana"
      },
      "audio/pcma": {
        source: "iana"
      },
      "audio/pcma-wb": {
        source: "iana"
      },
      "audio/pcmu": {
        source: "iana"
      },
      "audio/pcmu-wb": {
        source: "iana"
      },
      "audio/prs.sid": {
        source: "iana"
      },
      "audio/qcelp": {
        source: "iana"
      },
      "audio/raptorfec": {
        source: "iana"
      },
      "audio/red": {
        source: "iana"
      },
      "audio/rtp-enc-aescm128": {
        source: "iana"
      },
      "audio/rtp-midi": {
        source: "iana"
      },
      "audio/rtploopback": {
        source: "iana"
      },
      "audio/rtx": {
        source: "iana"
      },
      "audio/s3m": {
        source: "apache",
        extensions: ["s3m"]
      },
      "audio/scip": {
        source: "iana"
      },
      "audio/silk": {
        source: "apache",
        extensions: ["sil"]
      },
      "audio/smv": {
        source: "iana"
      },
      "audio/smv-qcp": {
        source: "iana"
      },
      "audio/smv0": {
        source: "iana"
      },
      "audio/sofa": {
        source: "iana"
      },
      "audio/sp-midi": {
        source: "iana"
      },
      "audio/speex": {
        source: "iana"
      },
      "audio/t140c": {
        source: "iana"
      },
      "audio/t38": {
        source: "iana"
      },
      "audio/telephone-event": {
        source: "iana"
      },
      "audio/tetra_acelp": {
        source: "iana"
      },
      "audio/tetra_acelp_bb": {
        source: "iana"
      },
      "audio/tone": {
        source: "iana"
      },
      "audio/tsvcis": {
        source: "iana"
      },
      "audio/uemclip": {
        source: "iana"
      },
      "audio/ulpfec": {
        source: "iana"
      },
      "audio/usac": {
        source: "iana"
      },
      "audio/vdvi": {
        source: "iana"
      },
      "audio/vmr-wb": {
        source: "iana"
      },
      "audio/vnd.3gpp.iufp": {
        source: "iana"
      },
      "audio/vnd.4sb": {
        source: "iana"
      },
      "audio/vnd.audiokoz": {
        source: "iana"
      },
      "audio/vnd.celp": {
        source: "iana"
      },
      "audio/vnd.cisco.nse": {
        source: "iana"
      },
      "audio/vnd.cmles.radio-events": {
        source: "iana"
      },
      "audio/vnd.cns.anp1": {
        source: "iana"
      },
      "audio/vnd.cns.inf1": {
        source: "iana"
      },
      "audio/vnd.dece.audio": {
        source: "iana",
        extensions: ["uva", "uvva"]
      },
      "audio/vnd.digital-winds": {
        source: "iana",
        extensions: ["eol"]
      },
      "audio/vnd.dlna.adts": {
        source: "iana"
      },
      "audio/vnd.dolby.heaac.1": {
        source: "iana"
      },
      "audio/vnd.dolby.heaac.2": {
        source: "iana"
      },
      "audio/vnd.dolby.mlp": {
        source: "iana"
      },
      "audio/vnd.dolby.mps": {
        source: "iana"
      },
      "audio/vnd.dolby.pl2": {
        source: "iana"
      },
      "audio/vnd.dolby.pl2x": {
        source: "iana"
      },
      "audio/vnd.dolby.pl2z": {
        source: "iana"
      },
      "audio/vnd.dolby.pulse.1": {
        source: "iana"
      },
      "audio/vnd.dra": {
        source: "iana",
        extensions: ["dra"]
      },
      "audio/vnd.dts": {
        source: "iana",
        extensions: ["dts"]
      },
      "audio/vnd.dts.hd": {
        source: "iana",
        extensions: ["dtshd"]
      },
      "audio/vnd.dts.uhd": {
        source: "iana"
      },
      "audio/vnd.dvb.file": {
        source: "iana"
      },
      "audio/vnd.everad.plj": {
        source: "iana"
      },
      "audio/vnd.hns.audio": {
        source: "iana"
      },
      "audio/vnd.lucent.voice": {
        source: "iana",
        extensions: ["lvp"]
      },
      "audio/vnd.ms-playready.media.pya": {
        source: "iana",
        extensions: ["pya"]
      },
      "audio/vnd.nokia.mobile-xmf": {
        source: "iana"
      },
      "audio/vnd.nortel.vbk": {
        source: "iana"
      },
      "audio/vnd.nuera.ecelp4800": {
        source: "iana",
        extensions: ["ecelp4800"]
      },
      "audio/vnd.nuera.ecelp7470": {
        source: "iana",
        extensions: ["ecelp7470"]
      },
      "audio/vnd.nuera.ecelp9600": {
        source: "iana",
        extensions: ["ecelp9600"]
      },
      "audio/vnd.octel.sbc": {
        source: "iana"
      },
      "audio/vnd.presonus.multitrack": {
        source: "iana"
      },
      "audio/vnd.qcelp": {
        source: "iana"
      },
      "audio/vnd.rhetorex.32kadpcm": {
        source: "iana"
      },
      "audio/vnd.rip": {
        source: "iana",
        extensions: ["rip"]
      },
      "audio/vnd.rn-realaudio": {
        compressible: false
      },
      "audio/vnd.sealedmedia.softseal.mpeg": {
        source: "iana"
      },
      "audio/vnd.vmx.cvsd": {
        source: "iana"
      },
      "audio/vnd.wave": {
        compressible: false
      },
      "audio/vorbis": {
        source: "iana",
        compressible: false
      },
      "audio/vorbis-config": {
        source: "iana"
      },
      "audio/wav": {
        compressible: false,
        extensions: ["wav"]
      },
      "audio/wave": {
        compressible: false,
        extensions: ["wav"]
      },
      "audio/webm": {
        source: "apache",
        compressible: false,
        extensions: ["weba"]
      },
      "audio/x-aac": {
        source: "apache",
        compressible: false,
        extensions: ["aac"]
      },
      "audio/x-aiff": {
        source: "apache",
        extensions: ["aif", "aiff", "aifc"]
      },
      "audio/x-caf": {
        source: "apache",
        compressible: false,
        extensions: ["caf"]
      },
      "audio/x-flac": {
        source: "apache",
        extensions: ["flac"]
      },
      "audio/x-m4a": {
        source: "nginx",
        extensions: ["m4a"]
      },
      "audio/x-matroska": {
        source: "apache",
        extensions: ["mka"]
      },
      "audio/x-mpegurl": {
        source: "apache",
        extensions: ["m3u"]
      },
      "audio/x-ms-wax": {
        source: "apache",
        extensions: ["wax"]
      },
      "audio/x-ms-wma": {
        source: "apache",
        extensions: ["wma"]
      },
      "audio/x-pn-realaudio": {
        source: "apache",
        extensions: ["ram", "ra"]
      },
      "audio/x-pn-realaudio-plugin": {
        source: "apache",
        extensions: ["rmp"]
      },
      "audio/x-realaudio": {
        source: "nginx",
        extensions: ["ra"]
      },
      "audio/x-tta": {
        source: "apache"
      },
      "audio/x-wav": {
        source: "apache",
        extensions: ["wav"]
      },
      "audio/xm": {
        source: "apache",
        extensions: ["xm"]
      },
      "chemical/x-cdx": {
        source: "apache",
        extensions: ["cdx"]
      },
      "chemical/x-cif": {
        source: "apache",
        extensions: ["cif"]
      },
      "chemical/x-cmdf": {
        source: "apache",
        extensions: ["cmdf"]
      },
      "chemical/x-cml": {
        source: "apache",
        extensions: ["cml"]
      },
      "chemical/x-csml": {
        source: "apache",
        extensions: ["csml"]
      },
      "chemical/x-pdb": {
        source: "apache"
      },
      "chemical/x-xyz": {
        source: "apache",
        extensions: ["xyz"]
      },
      "font/collection": {
        source: "iana",
        extensions: ["ttc"]
      },
      "font/otf": {
        source: "iana",
        compressible: true,
        extensions: ["otf"]
      },
      "font/sfnt": {
        source: "iana"
      },
      "font/ttf": {
        source: "iana",
        compressible: true,
        extensions: ["ttf"]
      },
      "font/woff": {
        source: "iana",
        extensions: ["woff"]
      },
      "font/woff2": {
        source: "iana",
        extensions: ["woff2"]
      },
      "image/aces": {
        source: "iana",
        extensions: ["exr"]
      },
      "image/apng": {
        compressible: false,
        extensions: ["apng"]
      },
      "image/avci": {
        source: "iana",
        extensions: ["avci"]
      },
      "image/avcs": {
        source: "iana",
        extensions: ["avcs"]
      },
      "image/avif": {
        source: "iana",
        compressible: false,
        extensions: ["avif"]
      },
      "image/bmp": {
        source: "iana",
        compressible: true,
        extensions: ["bmp"]
      },
      "image/cgm": {
        source: "iana",
        extensions: ["cgm"]
      },
      "image/dicom-rle": {
        source: "iana",
        extensions: ["drle"]
      },
      "image/emf": {
        source: "iana",
        extensions: ["emf"]
      },
      "image/fits": {
        source: "iana",
        extensions: ["fits"]
      },
      "image/g3fax": {
        source: "iana",
        extensions: ["g3"]
      },
      "image/gif": {
        source: "iana",
        compressible: false,
        extensions: ["gif"]
      },
      "image/heic": {
        source: "iana",
        extensions: ["heic"]
      },
      "image/heic-sequence": {
        source: "iana",
        extensions: ["heics"]
      },
      "image/heif": {
        source: "iana",
        extensions: ["heif"]
      },
      "image/heif-sequence": {
        source: "iana",
        extensions: ["heifs"]
      },
      "image/hej2k": {
        source: "iana",
        extensions: ["hej2"]
      },
      "image/hsj2": {
        source: "iana",
        extensions: ["hsj2"]
      },
      "image/ief": {
        source: "iana",
        extensions: ["ief"]
      },
      "image/jls": {
        source: "iana",
        extensions: ["jls"]
      },
      "image/jp2": {
        source: "iana",
        compressible: false,
        extensions: ["jp2", "jpg2"]
      },
      "image/jpeg": {
        source: "iana",
        compressible: false,
        extensions: ["jpeg", "jpg", "jpe"]
      },
      "image/jph": {
        source: "iana",
        extensions: ["jph"]
      },
      "image/jphc": {
        source: "iana",
        extensions: ["jhc"]
      },
      "image/jpm": {
        source: "iana",
        compressible: false,
        extensions: ["jpm"]
      },
      "image/jpx": {
        source: "iana",
        compressible: false,
        extensions: ["jpx", "jpf"]
      },
      "image/jxr": {
        source: "iana",
        extensions: ["jxr"]
      },
      "image/jxra": {
        source: "iana",
        extensions: ["jxra"]
      },
      "image/jxrs": {
        source: "iana",
        extensions: ["jxrs"]
      },
      "image/jxs": {
        source: "iana",
        extensions: ["jxs"]
      },
      "image/jxsc": {
        source: "iana",
        extensions: ["jxsc"]
      },
      "image/jxsi": {
        source: "iana",
        extensions: ["jxsi"]
      },
      "image/jxss": {
        source: "iana",
        extensions: ["jxss"]
      },
      "image/ktx": {
        source: "iana",
        extensions: ["ktx"]
      },
      "image/ktx2": {
        source: "iana",
        extensions: ["ktx2"]
      },
      "image/naplps": {
        source: "iana"
      },
      "image/pjpeg": {
        compressible: false
      },
      "image/png": {
        source: "iana",
        compressible: false,
        extensions: ["png"]
      },
      "image/prs.btif": {
        source: "iana",
        extensions: ["btif"]
      },
      "image/prs.pti": {
        source: "iana",
        extensions: ["pti"]
      },
      "image/pwg-raster": {
        source: "iana"
      },
      "image/sgi": {
        source: "apache",
        extensions: ["sgi"]
      },
      "image/svg+xml": {
        source: "iana",
        compressible: true,
        extensions: ["svg", "svgz"]
      },
      "image/t38": {
        source: "iana",
        extensions: ["t38"]
      },
      "image/tiff": {
        source: "iana",
        compressible: false,
        extensions: ["tif", "tiff"]
      },
      "image/tiff-fx": {
        source: "iana",
        extensions: ["tfx"]
      },
      "image/vnd.adobe.photoshop": {
        source: "iana",
        compressible: true,
        extensions: ["psd"]
      },
      "image/vnd.airzip.accelerator.azv": {
        source: "iana",
        extensions: ["azv"]
      },
      "image/vnd.cns.inf2": {
        source: "iana"
      },
      "image/vnd.dece.graphic": {
        source: "iana",
        extensions: ["uvi", "uvvi", "uvg", "uvvg"]
      },
      "image/vnd.djvu": {
        source: "iana",
        extensions: ["djvu", "djv"]
      },
      "image/vnd.dvb.subtitle": {
        source: "iana",
        extensions: ["sub"]
      },
      "image/vnd.dwg": {
        source: "iana",
        extensions: ["dwg"]
      },
      "image/vnd.dxf": {
        source: "iana",
        extensions: ["dxf"]
      },
      "image/vnd.fastbidsheet": {
        source: "iana",
        extensions: ["fbs"]
      },
      "image/vnd.fpx": {
        source: "iana",
        extensions: ["fpx"]
      },
      "image/vnd.fst": {
        source: "iana",
        extensions: ["fst"]
      },
      "image/vnd.fujixerox.edmics-mmr": {
        source: "iana",
        extensions: ["mmr"]
      },
      "image/vnd.fujixerox.edmics-rlc": {
        source: "iana",
        extensions: ["rlc"]
      },
      "image/vnd.globalgraphics.pgb": {
        source: "iana"
      },
      "image/vnd.microsoft.icon": {
        source: "iana",
        compressible: true,
        extensions: ["ico"]
      },
      "image/vnd.mix": {
        source: "iana"
      },
      "image/vnd.mozilla.apng": {
        source: "iana"
      },
      "image/vnd.ms-dds": {
        compressible: true,
        extensions: ["dds"]
      },
      "image/vnd.ms-modi": {
        source: "iana",
        extensions: ["mdi"]
      },
      "image/vnd.ms-photo": {
        source: "apache",
        extensions: ["wdp"]
      },
      "image/vnd.net-fpx": {
        source: "iana",
        extensions: ["npx"]
      },
      "image/vnd.pco.b16": {
        source: "iana",
        extensions: ["b16"]
      },
      "image/vnd.radiance": {
        source: "iana"
      },
      "image/vnd.sealed.png": {
        source: "iana"
      },
      "image/vnd.sealedmedia.softseal.gif": {
        source: "iana"
      },
      "image/vnd.sealedmedia.softseal.jpg": {
        source: "iana"
      },
      "image/vnd.svf": {
        source: "iana"
      },
      "image/vnd.tencent.tap": {
        source: "iana",
        extensions: ["tap"]
      },
      "image/vnd.valve.source.texture": {
        source: "iana",
        extensions: ["vtf"]
      },
      "image/vnd.wap.wbmp": {
        source: "iana",
        extensions: ["wbmp"]
      },
      "image/vnd.xiff": {
        source: "iana",
        extensions: ["xif"]
      },
      "image/vnd.zbrush.pcx": {
        source: "iana",
        extensions: ["pcx"]
      },
      "image/webp": {
        source: "apache",
        extensions: ["webp"]
      },
      "image/wmf": {
        source: "iana",
        extensions: ["wmf"]
      },
      "image/x-3ds": {
        source: "apache",
        extensions: ["3ds"]
      },
      "image/x-cmu-raster": {
        source: "apache",
        extensions: ["ras"]
      },
      "image/x-cmx": {
        source: "apache",
        extensions: ["cmx"]
      },
      "image/x-freehand": {
        source: "apache",
        extensions: ["fh", "fhc", "fh4", "fh5", "fh7"]
      },
      "image/x-icon": {
        source: "apache",
        compressible: true,
        extensions: ["ico"]
      },
      "image/x-jng": {
        source: "nginx",
        extensions: ["jng"]
      },
      "image/x-mrsid-image": {
        source: "apache",
        extensions: ["sid"]
      },
      "image/x-ms-bmp": {
        source: "nginx",
        compressible: true,
        extensions: ["bmp"]
      },
      "image/x-pcx": {
        source: "apache",
        extensions: ["pcx"]
      },
      "image/x-pict": {
        source: "apache",
        extensions: ["pic", "pct"]
      },
      "image/x-portable-anymap": {
        source: "apache",
        extensions: ["pnm"]
      },
      "image/x-portable-bitmap": {
        source: "apache",
        extensions: ["pbm"]
      },
      "image/x-portable-graymap": {
        source: "apache",
        extensions: ["pgm"]
      },
      "image/x-portable-pixmap": {
        source: "apache",
        extensions: ["ppm"]
      },
      "image/x-rgb": {
        source: "apache",
        extensions: ["rgb"]
      },
      "image/x-tga": {
        source: "apache",
        extensions: ["tga"]
      },
      "image/x-xbitmap": {
        source: "apache",
        extensions: ["xbm"]
      },
      "image/x-xcf": {
        compressible: false
      },
      "image/x-xpixmap": {
        source: "apache",
        extensions: ["xpm"]
      },
      "image/x-xwindowdump": {
        source: "apache",
        extensions: ["xwd"]
      },
      "message/cpim": {
        source: "iana"
      },
      "message/delivery-status": {
        source: "iana"
      },
      "message/disposition-notification": {
        source: "iana",
        extensions: [
          "disposition-notification"
        ]
      },
      "message/external-body": {
        source: "iana"
      },
      "message/feedback-report": {
        source: "iana"
      },
      "message/global": {
        source: "iana",
        extensions: ["u8msg"]
      },
      "message/global-delivery-status": {
        source: "iana",
        extensions: ["u8dsn"]
      },
      "message/global-disposition-notification": {
        source: "iana",
        extensions: ["u8mdn"]
      },
      "message/global-headers": {
        source: "iana",
        extensions: ["u8hdr"]
      },
      "message/http": {
        source: "iana",
        compressible: false
      },
      "message/imdn+xml": {
        source: "iana",
        compressible: true
      },
      "message/news": {
        source: "iana"
      },
      "message/partial": {
        source: "iana",
        compressible: false
      },
      "message/rfc822": {
        source: "iana",
        compressible: true,
        extensions: ["eml", "mime"]
      },
      "message/s-http": {
        source: "iana"
      },
      "message/sip": {
        source: "iana"
      },
      "message/sipfrag": {
        source: "iana"
      },
      "message/tracking-status": {
        source: "iana"
      },
      "message/vnd.si.simp": {
        source: "iana"
      },
      "message/vnd.wfa.wsc": {
        source: "iana",
        extensions: ["wsc"]
      },
      "model/3mf": {
        source: "iana",
        extensions: ["3mf"]
      },
      "model/e57": {
        source: "iana"
      },
      "model/gltf+json": {
        source: "iana",
        compressible: true,
        extensions: ["gltf"]
      },
      "model/gltf-binary": {
        source: "iana",
        compressible: true,
        extensions: ["glb"]
      },
      "model/iges": {
        source: "iana",
        compressible: false,
        extensions: ["igs", "iges"]
      },
      "model/mesh": {
        source: "iana",
        compressible: false,
        extensions: ["msh", "mesh", "silo"]
      },
      "model/mtl": {
        source: "iana",
        extensions: ["mtl"]
      },
      "model/obj": {
        source: "iana",
        extensions: ["obj"]
      },
      "model/step": {
        source: "iana"
      },
      "model/step+xml": {
        source: "iana",
        compressible: true,
        extensions: ["stpx"]
      },
      "model/step+zip": {
        source: "iana",
        compressible: false,
        extensions: ["stpz"]
      },
      "model/step-xml+zip": {
        source: "iana",
        compressible: false,
        extensions: ["stpxz"]
      },
      "model/stl": {
        source: "iana",
        extensions: ["stl"]
      },
      "model/vnd.collada+xml": {
        source: "iana",
        compressible: true,
        extensions: ["dae"]
      },
      "model/vnd.dwf": {
        source: "iana",
        extensions: ["dwf"]
      },
      "model/vnd.flatland.3dml": {
        source: "iana"
      },
      "model/vnd.gdl": {
        source: "iana",
        extensions: ["gdl"]
      },
      "model/vnd.gs-gdl": {
        source: "apache"
      },
      "model/vnd.gs.gdl": {
        source: "iana"
      },
      "model/vnd.gtw": {
        source: "iana",
        extensions: ["gtw"]
      },
      "model/vnd.moml+xml": {
        source: "iana",
        compressible: true
      },
      "model/vnd.mts": {
        source: "iana",
        extensions: ["mts"]
      },
      "model/vnd.opengex": {
        source: "iana",
        extensions: ["ogex"]
      },
      "model/vnd.parasolid.transmit.binary": {
        source: "iana",
        extensions: ["x_b"]
      },
      "model/vnd.parasolid.transmit.text": {
        source: "iana",
        extensions: ["x_t"]
      },
      "model/vnd.pytha.pyox": {
        source: "iana"
      },
      "model/vnd.rosette.annotated-data-model": {
        source: "iana"
      },
      "model/vnd.sap.vds": {
        source: "iana",
        extensions: ["vds"]
      },
      "model/vnd.usdz+zip": {
        source: "iana",
        compressible: false,
        extensions: ["usdz"]
      },
      "model/vnd.valve.source.compiled-map": {
        source: "iana",
        extensions: ["bsp"]
      },
      "model/vnd.vtu": {
        source: "iana",
        extensions: ["vtu"]
      },
      "model/vrml": {
        source: "iana",
        compressible: false,
        extensions: ["wrl", "vrml"]
      },
      "model/x3d+binary": {
        source: "apache",
        compressible: false,
        extensions: ["x3db", "x3dbz"]
      },
      "model/x3d+fastinfoset": {
        source: "iana",
        extensions: ["x3db"]
      },
      "model/x3d+vrml": {
        source: "apache",
        compressible: false,
        extensions: ["x3dv", "x3dvz"]
      },
      "model/x3d+xml": {
        source: "iana",
        compressible: true,
        extensions: ["x3d", "x3dz"]
      },
      "model/x3d-vrml": {
        source: "iana",
        extensions: ["x3dv"]
      },
      "multipart/alternative": {
        source: "iana",
        compressible: false
      },
      "multipart/appledouble": {
        source: "iana"
      },
      "multipart/byteranges": {
        source: "iana"
      },
      "multipart/digest": {
        source: "iana"
      },
      "multipart/encrypted": {
        source: "iana",
        compressible: false
      },
      "multipart/form-data": {
        source: "iana",
        compressible: false
      },
      "multipart/header-set": {
        source: "iana"
      },
      "multipart/mixed": {
        source: "iana"
      },
      "multipart/multilingual": {
        source: "iana"
      },
      "multipart/parallel": {
        source: "iana"
      },
      "multipart/related": {
        source: "iana",
        compressible: false
      },
      "multipart/report": {
        source: "iana"
      },
      "multipart/signed": {
        source: "iana",
        compressible: false
      },
      "multipart/vnd.bint.med-plus": {
        source: "iana"
      },
      "multipart/voice-message": {
        source: "iana"
      },
      "multipart/x-mixed-replace": {
        source: "iana"
      },
      "text/1d-interleaved-parityfec": {
        source: "iana"
      },
      "text/cache-manifest": {
        source: "iana",
        compressible: true,
        extensions: ["appcache", "manifest"]
      },
      "text/calendar": {
        source: "iana",
        extensions: ["ics", "ifb"]
      },
      "text/calender": {
        compressible: true
      },
      "text/cmd": {
        compressible: true
      },
      "text/coffeescript": {
        extensions: ["coffee", "litcoffee"]
      },
      "text/cql": {
        source: "iana"
      },
      "text/cql-expression": {
        source: "iana"
      },
      "text/cql-identifier": {
        source: "iana"
      },
      "text/css": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["css"]
      },
      "text/csv": {
        source: "iana",
        compressible: true,
        extensions: ["csv"]
      },
      "text/csv-schema": {
        source: "iana"
      },
      "text/directory": {
        source: "iana"
      },
      "text/dns": {
        source: "iana"
      },
      "text/ecmascript": {
        source: "iana"
      },
      "text/encaprtp": {
        source: "iana"
      },
      "text/enriched": {
        source: "iana"
      },
      "text/fhirpath": {
        source: "iana"
      },
      "text/flexfec": {
        source: "iana"
      },
      "text/fwdred": {
        source: "iana"
      },
      "text/gff3": {
        source: "iana"
      },
      "text/grammar-ref-list": {
        source: "iana"
      },
      "text/html": {
        source: "iana",
        compressible: true,
        extensions: ["html", "htm", "shtml"]
      },
      "text/jade": {
        extensions: ["jade"]
      },
      "text/javascript": {
        source: "iana",
        compressible: true
      },
      "text/jcr-cnd": {
        source: "iana"
      },
      "text/jsx": {
        compressible: true,
        extensions: ["jsx"]
      },
      "text/less": {
        compressible: true,
        extensions: ["less"]
      },
      "text/markdown": {
        source: "iana",
        compressible: true,
        extensions: ["markdown", "md"]
      },
      "text/mathml": {
        source: "nginx",
        extensions: ["mml"]
      },
      "text/mdx": {
        compressible: true,
        extensions: ["mdx"]
      },
      "text/mizar": {
        source: "iana"
      },
      "text/n3": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["n3"]
      },
      "text/parameters": {
        source: "iana",
        charset: "UTF-8"
      },
      "text/parityfec": {
        source: "iana"
      },
      "text/plain": {
        source: "iana",
        compressible: true,
        extensions: ["txt", "text", "conf", "def", "list", "log", "in", "ini"]
      },
      "text/provenance-notation": {
        source: "iana",
        charset: "UTF-8"
      },
      "text/prs.fallenstein.rst": {
        source: "iana"
      },
      "text/prs.lines.tag": {
        source: "iana",
        extensions: ["dsc"]
      },
      "text/prs.prop.logic": {
        source: "iana"
      },
      "text/raptorfec": {
        source: "iana"
      },
      "text/red": {
        source: "iana"
      },
      "text/rfc822-headers": {
        source: "iana"
      },
      "text/richtext": {
        source: "iana",
        compressible: true,
        extensions: ["rtx"]
      },
      "text/rtf": {
        source: "iana",
        compressible: true,
        extensions: ["rtf"]
      },
      "text/rtp-enc-aescm128": {
        source: "iana"
      },
      "text/rtploopback": {
        source: "iana"
      },
      "text/rtx": {
        source: "iana"
      },
      "text/sgml": {
        source: "iana",
        extensions: ["sgml", "sgm"]
      },
      "text/shaclc": {
        source: "iana"
      },
      "text/shex": {
        source: "iana",
        extensions: ["shex"]
      },
      "text/slim": {
        extensions: ["slim", "slm"]
      },
      "text/spdx": {
        source: "iana",
        extensions: ["spdx"]
      },
      "text/strings": {
        source: "iana"
      },
      "text/stylus": {
        extensions: ["stylus", "styl"]
      },
      "text/t140": {
        source: "iana"
      },
      "text/tab-separated-values": {
        source: "iana",
        compressible: true,
        extensions: ["tsv"]
      },
      "text/troff": {
        source: "iana",
        extensions: ["t", "tr", "roff", "man", "me", "ms"]
      },
      "text/turtle": {
        source: "iana",
        charset: "UTF-8",
        extensions: ["ttl"]
      },
      "text/ulpfec": {
        source: "iana"
      },
      "text/uri-list": {
        source: "iana",
        compressible: true,
        extensions: ["uri", "uris", "urls"]
      },
      "text/vcard": {
        source: "iana",
        compressible: true,
        extensions: ["vcard"]
      },
      "text/vnd.a": {
        source: "iana"
      },
      "text/vnd.abc": {
        source: "iana"
      },
      "text/vnd.ascii-art": {
        source: "iana"
      },
      "text/vnd.curl": {
        source: "iana",
        extensions: ["curl"]
      },
      "text/vnd.curl.dcurl": {
        source: "apache",
        extensions: ["dcurl"]
      },
      "text/vnd.curl.mcurl": {
        source: "apache",
        extensions: ["mcurl"]
      },
      "text/vnd.curl.scurl": {
        source: "apache",
        extensions: ["scurl"]
      },
      "text/vnd.debian.copyright": {
        source: "iana",
        charset: "UTF-8"
      },
      "text/vnd.dmclientscript": {
        source: "iana"
      },
      "text/vnd.dvb.subtitle": {
        source: "iana",
        extensions: ["sub"]
      },
      "text/vnd.esmertec.theme-descriptor": {
        source: "iana",
        charset: "UTF-8"
      },
      "text/vnd.familysearch.gedcom": {
        source: "iana",
        extensions: ["ged"]
      },
      "text/vnd.ficlab.flt": {
        source: "iana"
      },
      "text/vnd.fly": {
        source: "iana",
        extensions: ["fly"]
      },
      "text/vnd.fmi.flexstor": {
        source: "iana",
        extensions: ["flx"]
      },
      "text/vnd.gml": {
        source: "iana"
      },
      "text/vnd.graphviz": {
        source: "iana",
        extensions: ["gv"]
      },
      "text/vnd.hans": {
        source: "iana"
      },
      "text/vnd.hgl": {
        source: "iana"
      },
      "text/vnd.in3d.3dml": {
        source: "iana",
        extensions: ["3dml"]
      },
      "text/vnd.in3d.spot": {
        source: "iana",
        extensions: ["spot"]
      },
      "text/vnd.iptc.newsml": {
        source: "iana"
      },
      "text/vnd.iptc.nitf": {
        source: "iana"
      },
      "text/vnd.latex-z": {
        source: "iana"
      },
      "text/vnd.motorola.reflex": {
        source: "iana"
      },
      "text/vnd.ms-mediapackage": {
        source: "iana"
      },
      "text/vnd.net2phone.commcenter.command": {
        source: "iana"
      },
      "text/vnd.radisys.msml-basic-layout": {
        source: "iana"
      },
      "text/vnd.senx.warpscript": {
        source: "iana"
      },
      "text/vnd.si.uricatalogue": {
        source: "iana"
      },
      "text/vnd.sosi": {
        source: "iana"
      },
      "text/vnd.sun.j2me.app-descriptor": {
        source: "iana",
        charset: "UTF-8",
        extensions: ["jad"]
      },
      "text/vnd.trolltech.linguist": {
        source: "iana",
        charset: "UTF-8"
      },
      "text/vnd.wap.si": {
        source: "iana"
      },
      "text/vnd.wap.sl": {
        source: "iana"
      },
      "text/vnd.wap.wml": {
        source: "iana",
        extensions: ["wml"]
      },
      "text/vnd.wap.wmlscript": {
        source: "iana",
        extensions: ["wmls"]
      },
      "text/vtt": {
        source: "iana",
        charset: "UTF-8",
        compressible: true,
        extensions: ["vtt"]
      },
      "text/x-asm": {
        source: "apache",
        extensions: ["s", "asm"]
      },
      "text/x-c": {
        source: "apache",
        extensions: ["c", "cc", "cxx", "cpp", "h", "hh", "dic"]
      },
      "text/x-component": {
        source: "nginx",
        extensions: ["htc"]
      },
      "text/x-fortran": {
        source: "apache",
        extensions: ["f", "for", "f77", "f90"]
      },
      "text/x-gwt-rpc": {
        compressible: true
      },
      "text/x-handlebars-template": {
        extensions: ["hbs"]
      },
      "text/x-java-source": {
        source: "apache",
        extensions: ["java"]
      },
      "text/x-jquery-tmpl": {
        compressible: true
      },
      "text/x-lua": {
        extensions: ["lua"]
      },
      "text/x-markdown": {
        compressible: true,
        extensions: ["mkd"]
      },
      "text/x-nfo": {
        source: "apache",
        extensions: ["nfo"]
      },
      "text/x-opml": {
        source: "apache",
        extensions: ["opml"]
      },
      "text/x-org": {
        compressible: true,
        extensions: ["org"]
      },
      "text/x-pascal": {
        source: "apache",
        extensions: ["p", "pas"]
      },
      "text/x-processing": {
        compressible: true,
        extensions: ["pde"]
      },
      "text/x-sass": {
        extensions: ["sass"]
      },
      "text/x-scss": {
        extensions: ["scss"]
      },
      "text/x-setext": {
        source: "apache",
        extensions: ["etx"]
      },
      "text/x-sfv": {
        source: "apache",
        extensions: ["sfv"]
      },
      "text/x-suse-ymp": {
        compressible: true,
        extensions: ["ymp"]
      },
      "text/x-uuencode": {
        source: "apache",
        extensions: ["uu"]
      },
      "text/x-vcalendar": {
        source: "apache",
        extensions: ["vcs"]
      },
      "text/x-vcard": {
        source: "apache",
        extensions: ["vcf"]
      },
      "text/xml": {
        source: "iana",
        compressible: true,
        extensions: ["xml"]
      },
      "text/xml-external-parsed-entity": {
        source: "iana"
      },
      "text/yaml": {
        compressible: true,
        extensions: ["yaml", "yml"]
      },
      "video/1d-interleaved-parityfec": {
        source: "iana"
      },
      "video/3gpp": {
        source: "iana",
        extensions: ["3gp", "3gpp"]
      },
      "video/3gpp-tt": {
        source: "iana"
      },
      "video/3gpp2": {
        source: "iana",
        extensions: ["3g2"]
      },
      "video/av1": {
        source: "iana"
      },
      "video/bmpeg": {
        source: "iana"
      },
      "video/bt656": {
        source: "iana"
      },
      "video/celb": {
        source: "iana"
      },
      "video/dv": {
        source: "iana"
      },
      "video/encaprtp": {
        source: "iana"
      },
      "video/ffv1": {
        source: "iana"
      },
      "video/flexfec": {
        source: "iana"
      },
      "video/h261": {
        source: "iana",
        extensions: ["h261"]
      },
      "video/h263": {
        source: "iana",
        extensions: ["h263"]
      },
      "video/h263-1998": {
        source: "iana"
      },
      "video/h263-2000": {
        source: "iana"
      },
      "video/h264": {
        source: "iana",
        extensions: ["h264"]
      },
      "video/h264-rcdo": {
        source: "iana"
      },
      "video/h264-svc": {
        source: "iana"
      },
      "video/h265": {
        source: "iana"
      },
      "video/iso.segment": {
        source: "iana",
        extensions: ["m4s"]
      },
      "video/jpeg": {
        source: "iana",
        extensions: ["jpgv"]
      },
      "video/jpeg2000": {
        source: "iana"
      },
      "video/jpm": {
        source: "apache",
        extensions: ["jpm", "jpgm"]
      },
      "video/jxsv": {
        source: "iana"
      },
      "video/mj2": {
        source: "iana",
        extensions: ["mj2", "mjp2"]
      },
      "video/mp1s": {
        source: "iana"
      },
      "video/mp2p": {
        source: "iana"
      },
      "video/mp2t": {
        source: "iana",
        extensions: ["ts"]
      },
      "video/mp4": {
        source: "iana",
        compressible: false,
        extensions: ["mp4", "mp4v", "mpg4"]
      },
      "video/mp4v-es": {
        source: "iana"
      },
      "video/mpeg": {
        source: "iana",
        compressible: false,
        extensions: ["mpeg", "mpg", "mpe", "m1v", "m2v"]
      },
      "video/mpeg4-generic": {
        source: "iana"
      },
      "video/mpv": {
        source: "iana"
      },
      "video/nv": {
        source: "iana"
      },
      "video/ogg": {
        source: "iana",
        compressible: false,
        extensions: ["ogv"]
      },
      "video/parityfec": {
        source: "iana"
      },
      "video/pointer": {
        source: "iana"
      },
      "video/quicktime": {
        source: "iana",
        compressible: false,
        extensions: ["qt", "mov"]
      },
      "video/raptorfec": {
        source: "iana"
      },
      "video/raw": {
        source: "iana"
      },
      "video/rtp-enc-aescm128": {
        source: "iana"
      },
      "video/rtploopback": {
        source: "iana"
      },
      "video/rtx": {
        source: "iana"
      },
      "video/scip": {
        source: "iana"
      },
      "video/smpte291": {
        source: "iana"
      },
      "video/smpte292m": {
        source: "iana"
      },
      "video/ulpfec": {
        source: "iana"
      },
      "video/vc1": {
        source: "iana"
      },
      "video/vc2": {
        source: "iana"
      },
      "video/vnd.cctv": {
        source: "iana"
      },
      "video/vnd.dece.hd": {
        source: "iana",
        extensions: ["uvh", "uvvh"]
      },
      "video/vnd.dece.mobile": {
        source: "iana",
        extensions: ["uvm", "uvvm"]
      },
      "video/vnd.dece.mp4": {
        source: "iana"
      },
      "video/vnd.dece.pd": {
        source: "iana",
        extensions: ["uvp", "uvvp"]
      },
      "video/vnd.dece.sd": {
        source: "iana",
        extensions: ["uvs", "uvvs"]
      },
      "video/vnd.dece.video": {
        source: "iana",
        extensions: ["uvv", "uvvv"]
      },
      "video/vnd.directv.mpeg": {
        source: "iana"
      },
      "video/vnd.directv.mpeg-tts": {
        source: "iana"
      },
      "video/vnd.dlna.mpeg-tts": {
        source: "iana"
      },
      "video/vnd.dvb.file": {
        source: "iana",
        extensions: ["dvb"]
      },
      "video/vnd.fvt": {
        source: "iana",
        extensions: ["fvt"]
      },
      "video/vnd.hns.video": {
        source: "iana"
      },
      "video/vnd.iptvforum.1dparityfec-1010": {
        source: "iana"
      },
      "video/vnd.iptvforum.1dparityfec-2005": {
        source: "iana"
      },
      "video/vnd.iptvforum.2dparityfec-1010": {
        source: "iana"
      },
      "video/vnd.iptvforum.2dparityfec-2005": {
        source: "iana"
      },
      "video/vnd.iptvforum.ttsavc": {
        source: "iana"
      },
      "video/vnd.iptvforum.ttsmpeg2": {
        source: "iana"
      },
      "video/vnd.motorola.video": {
        source: "iana"
      },
      "video/vnd.motorola.videop": {
        source: "iana"
      },
      "video/vnd.mpegurl": {
        source: "iana",
        extensions: ["mxu", "m4u"]
      },
      "video/vnd.ms-playready.media.pyv": {
        source: "iana",
        extensions: ["pyv"]
      },
      "video/vnd.nokia.interleaved-multimedia": {
        source: "iana"
      },
      "video/vnd.nokia.mp4vr": {
        source: "iana"
      },
      "video/vnd.nokia.videovoip": {
        source: "iana"
      },
      "video/vnd.objectvideo": {
        source: "iana"
      },
      "video/vnd.radgamettools.bink": {
        source: "iana"
      },
      "video/vnd.radgamettools.smacker": {
        source: "iana"
      },
      "video/vnd.sealed.mpeg1": {
        source: "iana"
      },
      "video/vnd.sealed.mpeg4": {
        source: "iana"
      },
      "video/vnd.sealed.swf": {
        source: "iana"
      },
      "video/vnd.sealedmedia.softseal.mov": {
        source: "iana"
      },
      "video/vnd.uvvu.mp4": {
        source: "iana",
        extensions: ["uvu", "uvvu"]
      },
      "video/vnd.vivo": {
        source: "iana",
        extensions: ["viv"]
      },
      "video/vnd.youtube.yt": {
        source: "iana"
      },
      "video/vp8": {
        source: "iana"
      },
      "video/vp9": {
        source: "iana"
      },
      "video/webm": {
        source: "apache",
        compressible: false,
        extensions: ["webm"]
      },
      "video/x-f4v": {
        source: "apache",
        extensions: ["f4v"]
      },
      "video/x-fli": {
        source: "apache",
        extensions: ["fli"]
      },
      "video/x-flv": {
        source: "apache",
        compressible: false,
        extensions: ["flv"]
      },
      "video/x-m4v": {
        source: "apache",
        extensions: ["m4v"]
      },
      "video/x-matroska": {
        source: "apache",
        compressible: false,
        extensions: ["mkv", "mk3d", "mks"]
      },
      "video/x-mng": {
        source: "apache",
        extensions: ["mng"]
      },
      "video/x-ms-asf": {
        source: "apache",
        extensions: ["asf", "asx"]
      },
      "video/x-ms-vob": {
        source: "apache",
        extensions: ["vob"]
      },
      "video/x-ms-wm": {
        source: "apache",
        extensions: ["wm"]
      },
      "video/x-ms-wmv": {
        source: "apache",
        compressible: false,
        extensions: ["wmv"]
      },
      "video/x-ms-wmx": {
        source: "apache",
        extensions: ["wmx"]
      },
      "video/x-ms-wvx": {
        source: "apache",
        extensions: ["wvx"]
      },
      "video/x-msvideo": {
        source: "apache",
        extensions: ["avi"]
      },
      "video/x-sgi-movie": {
        source: "apache",
        extensions: ["movie"]
      },
      "video/x-smv": {
        source: "apache",
        extensions: ["smv"]
      },
      "x-conference/x-cooltalk": {
        source: "apache",
        extensions: ["ice"]
      },
      "x-shader/x-fragment": {
        compressible: true
      },
      "x-shader/x-vertex": {
        compressible: true
      }
    };
  }
});

// ../shared/core/node_modules/mime-db/index.js
var require_mime_db = __commonJS({
  "../shared/core/node_modules/mime-db/index.js"(exports, module) {
    module.exports = require_db();
  }
});

// ../shared/core/node_modules/mime-types/index.js
var require_mime_types = __commonJS({
  "../shared/core/node_modules/mime-types/index.js"(exports) {
    "use strict";
    var db = require_mime_db();
    var extname = __require("path").extname;
    var EXTRACT_TYPE_REGEXP = /^\s*([^;\s]*)(?:;|\s|$)/;
    var TEXT_TYPE_REGEXP = /^text\//i;
    exports.charset = charset;
    exports.charsets = { lookup: charset };
    exports.contentType = contentType;
    exports.extension = extension;
    exports.extensions = /* @__PURE__ */ Object.create(null);
    exports.lookup = lookup;
    exports.types = /* @__PURE__ */ Object.create(null);
    populateMaps(exports.extensions, exports.types);
    function charset(type) {
      if (!type || typeof type !== "string") {
        return false;
      }
      var match = EXTRACT_TYPE_REGEXP.exec(type);
      var mime = match && db[match[1].toLowerCase()];
      if (mime && mime.charset) {
        return mime.charset;
      }
      if (match && TEXT_TYPE_REGEXP.test(match[1])) {
        return "UTF-8";
      }
      return false;
    }
    function contentType(str) {
      if (!str || typeof str !== "string") {
        return false;
      }
      var mime = str.indexOf("/") === -1 ? exports.lookup(str) : str;
      if (!mime) {
        return false;
      }
      if (mime.indexOf("charset") === -1) {
        var charset2 = exports.charset(mime);
        if (charset2) mime += "; charset=" + charset2.toLowerCase();
      }
      return mime;
    }
    function extension(type) {
      if (!type || typeof type !== "string") {
        return false;
      }
      var match = EXTRACT_TYPE_REGEXP.exec(type);
      var exts = match && exports.extensions[match[1].toLowerCase()];
      if (!exts || !exts.length) {
        return false;
      }
      return exts[0];
    }
    function lookup(path2) {
      if (!path2 || typeof path2 !== "string") {
        return false;
      }
      var extension2 = extname("x." + path2).toLowerCase().substr(1);
      if (!extension2) {
        return false;
      }
      return exports.types[extension2] || false;
    }
    function populateMaps(extensions, types) {
      var preference = ["nginx", "apache", void 0, "iana"];
      Object.keys(db).forEach(function forEachMimeType(type) {
        var mime = db[type];
        var exts = mime.extensions;
        if (!exts || !exts.length) {
          return;
        }
        extensions[type] = exts;
        for (var i = 0; i < exts.length; i++) {
          var extension2 = exts[i];
          if (types[extension2]) {
            var from = preference.indexOf(db[types[extension2]].source);
            var to = preference.indexOf(mime.source);
            if (types[extension2] !== "application/octet-stream" && (from > to || from === to && types[extension2].substr(0, 12) === "application/")) {
              continue;
            }
          }
          types[extension2] = type;
        }
      });
    }
  }
});

// ../shared/core/node_modules/asynckit/lib/defer.js
var require_defer = __commonJS({
  "../shared/core/node_modules/asynckit/lib/defer.js"(exports, module) {
    module.exports = defer;
    function defer(fn) {
      var nextTick = typeof setImmediate == "function" ? setImmediate : typeof process == "object" && typeof process.nextTick == "function" ? process.nextTick : null;
      if (nextTick) {
        nextTick(fn);
      } else {
        setTimeout(fn, 0);
      }
    }
  }
});

// ../shared/core/node_modules/asynckit/lib/async.js
var require_async = __commonJS({
  "../shared/core/node_modules/asynckit/lib/async.js"(exports, module) {
    var defer = require_defer();
    module.exports = async;
    function async(callback) {
      var isAsync = false;
      defer(function() {
        isAsync = true;
      });
      return function async_callback(err, result) {
        if (isAsync) {
          callback(err, result);
        } else {
          defer(function nextTick_callback() {
            callback(err, result);
          });
        }
      };
    }
  }
});

// ../shared/core/node_modules/asynckit/lib/abort.js
var require_abort = __commonJS({
  "../shared/core/node_modules/asynckit/lib/abort.js"(exports, module) {
    module.exports = abort;
    function abort(state) {
      Object.keys(state.jobs).forEach(clean.bind(state));
      state.jobs = {};
    }
    function clean(key) {
      if (typeof this.jobs[key] == "function") {
        this.jobs[key]();
      }
    }
  }
});

// ../shared/core/node_modules/asynckit/lib/iterate.js
var require_iterate = __commonJS({
  "../shared/core/node_modules/asynckit/lib/iterate.js"(exports, module) {
    var async = require_async();
    var abort = require_abort();
    module.exports = iterate;
    function iterate(list, iterator2, state, callback) {
      var key = state["keyedList"] ? state["keyedList"][state.index] : state.index;
      state.jobs[key] = runJob(iterator2, key, list[key], function(error, output) {
        if (!(key in state.jobs)) {
          return;
        }
        delete state.jobs[key];
        if (error) {
          abort(state);
        } else {
          state.results[key] = output;
        }
        callback(error, state.results);
      });
    }
    function runJob(iterator2, key, item, callback) {
      var aborter;
      if (iterator2.length == 2) {
        aborter = iterator2(item, async(callback));
      } else {
        aborter = iterator2(item, key, async(callback));
      }
      return aborter;
    }
  }
});

// ../shared/core/node_modules/asynckit/lib/state.js
var require_state = __commonJS({
  "../shared/core/node_modules/asynckit/lib/state.js"(exports, module) {
    module.exports = state;
    function state(list, sortMethod) {
      var isNamedList = !Array.isArray(list), initState = {
        index: 0,
        keyedList: isNamedList || sortMethod ? Object.keys(list) : null,
        jobs: {},
        results: isNamedList ? {} : [],
        size: isNamedList ? Object.keys(list).length : list.length
      };
      if (sortMethod) {
        initState.keyedList.sort(isNamedList ? sortMethod : function(a, b) {
          return sortMethod(list[a], list[b]);
        });
      }
      return initState;
    }
  }
});

// ../shared/core/node_modules/asynckit/lib/terminator.js
var require_terminator = __commonJS({
  "../shared/core/node_modules/asynckit/lib/terminator.js"(exports, module) {
    var abort = require_abort();
    var async = require_async();
    module.exports = terminator;
    function terminator(callback) {
      if (!Object.keys(this.jobs).length) {
        return;
      }
      this.index = this.size;
      abort(this);
      async(callback)(null, this.results);
    }
  }
});

// ../shared/core/node_modules/asynckit/parallel.js
var require_parallel = __commonJS({
  "../shared/core/node_modules/asynckit/parallel.js"(exports, module) {
    var iterate = require_iterate();
    var initState = require_state();
    var terminator = require_terminator();
    module.exports = parallel;
    function parallel(list, iterator2, callback) {
      var state = initState(list);
      while (state.index < (state["keyedList"] || list).length) {
        iterate(list, iterator2, state, function(error, result) {
          if (error) {
            callback(error, result);
            return;
          }
          if (Object.keys(state.jobs).length === 0) {
            callback(null, state.results);
            return;
          }
        });
        state.index++;
      }
      return terminator.bind(state, callback);
    }
  }
});

// ../shared/core/node_modules/asynckit/serialOrdered.js
var require_serialOrdered = __commonJS({
  "../shared/core/node_modules/asynckit/serialOrdered.js"(exports, module) {
    var iterate = require_iterate();
    var initState = require_state();
    var terminator = require_terminator();
    module.exports = serialOrdered;
    module.exports.ascending = ascending;
    module.exports.descending = descending;
    function serialOrdered(list, iterator2, sortMethod, callback) {
      var state = initState(list, sortMethod);
      iterate(list, iterator2, state, function iteratorHandler(error, result) {
        if (error) {
          callback(error, result);
          return;
        }
        state.index++;
        if (state.index < (state["keyedList"] || list).length) {
          iterate(list, iterator2, state, iteratorHandler);
          return;
        }
        callback(null, state.results);
      });
      return terminator.bind(state, callback);
    }
    function ascending(a, b) {
      return a < b ? -1 : a > b ? 1 : 0;
    }
    function descending(a, b) {
      return -1 * ascending(a, b);
    }
  }
});

// ../shared/core/node_modules/asynckit/serial.js
var require_serial = __commonJS({
  "../shared/core/node_modules/asynckit/serial.js"(exports, module) {
    var serialOrdered = require_serialOrdered();
    module.exports = serial;
    function serial(list, iterator2, callback) {
      return serialOrdered(list, iterator2, null, callback);
    }
  }
});

// ../shared/core/node_modules/asynckit/index.js
var require_asynckit = __commonJS({
  "../shared/core/node_modules/asynckit/index.js"(exports, module) {
    module.exports = {
      parallel: require_parallel(),
      serial: require_serial(),
      serialOrdered: require_serialOrdered()
    };
  }
});

// ../shared/core/node_modules/es-object-atoms/index.js
var require_es_object_atoms = __commonJS({
  "../shared/core/node_modules/es-object-atoms/index.js"(exports, module) {
    "use strict";
    module.exports = Object;
  }
});

// ../shared/core/node_modules/es-errors/index.js
var require_es_errors = __commonJS({
  "../shared/core/node_modules/es-errors/index.js"(exports, module) {
    "use strict";
    module.exports = Error;
  }
});

// ../shared/core/node_modules/es-errors/eval.js
var require_eval = __commonJS({
  "../shared/core/node_modules/es-errors/eval.js"(exports, module) {
    "use strict";
    module.exports = EvalError;
  }
});

// ../shared/core/node_modules/es-errors/range.js
var require_range = __commonJS({
  "../shared/core/node_modules/es-errors/range.js"(exports, module) {
    "use strict";
    module.exports = RangeError;
  }
});

// ../shared/core/node_modules/es-errors/ref.js
var require_ref = __commonJS({
  "../shared/core/node_modules/es-errors/ref.js"(exports, module) {
    "use strict";
    module.exports = ReferenceError;
  }
});

// ../shared/core/node_modules/es-errors/syntax.js
var require_syntax = __commonJS({
  "../shared/core/node_modules/es-errors/syntax.js"(exports, module) {
    "use strict";
    module.exports = SyntaxError;
  }
});

// ../shared/core/node_modules/es-errors/type.js
var require_type = __commonJS({
  "../shared/core/node_modules/es-errors/type.js"(exports, module) {
    "use strict";
    module.exports = TypeError;
  }
});

// ../shared/core/node_modules/es-errors/uri.js
var require_uri = __commonJS({
  "../shared/core/node_modules/es-errors/uri.js"(exports, module) {
    "use strict";
    module.exports = URIError;
  }
});

// ../shared/core/node_modules/math-intrinsics/abs.js
var require_abs = __commonJS({
  "../shared/core/node_modules/math-intrinsics/abs.js"(exports, module) {
    "use strict";
    module.exports = Math.abs;
  }
});

// ../shared/core/node_modules/math-intrinsics/floor.js
var require_floor = __commonJS({
  "../shared/core/node_modules/math-intrinsics/floor.js"(exports, module) {
    "use strict";
    module.exports = Math.floor;
  }
});

// ../shared/core/node_modules/math-intrinsics/max.js
var require_max = __commonJS({
  "../shared/core/node_modules/math-intrinsics/max.js"(exports, module) {
    "use strict";
    module.exports = Math.max;
  }
});

// ../shared/core/node_modules/math-intrinsics/min.js
var require_min = __commonJS({
  "../shared/core/node_modules/math-intrinsics/min.js"(exports, module) {
    "use strict";
    module.exports = Math.min;
  }
});

// ../shared/core/node_modules/math-intrinsics/pow.js
var require_pow = __commonJS({
  "../shared/core/node_modules/math-intrinsics/pow.js"(exports, module) {
    "use strict";
    module.exports = Math.pow;
  }
});

// ../shared/core/node_modules/math-intrinsics/round.js
var require_round = __commonJS({
  "../shared/core/node_modules/math-intrinsics/round.js"(exports, module) {
    "use strict";
    module.exports = Math.round;
  }
});

// ../shared/core/node_modules/math-intrinsics/isNaN.js
var require_isNaN = __commonJS({
  "../shared/core/node_modules/math-intrinsics/isNaN.js"(exports, module) {
    "use strict";
    module.exports = Number.isNaN || function isNaN2(a) {
      return a !== a;
    };
  }
});

// ../shared/core/node_modules/math-intrinsics/sign.js
var require_sign = __commonJS({
  "../shared/core/node_modules/math-intrinsics/sign.js"(exports, module) {
    "use strict";
    var $isNaN = require_isNaN();
    module.exports = function sign(number) {
      if ($isNaN(number) || number === 0) {
        return number;
      }
      return number < 0 ? -1 : 1;
    };
  }
});

// ../shared/core/node_modules/gopd/gOPD.js
var require_gOPD = __commonJS({
  "../shared/core/node_modules/gopd/gOPD.js"(exports, module) {
    "use strict";
    module.exports = Object.getOwnPropertyDescriptor;
  }
});

// ../shared/core/node_modules/gopd/index.js
var require_gopd = __commonJS({
  "../shared/core/node_modules/gopd/index.js"(exports, module) {
    "use strict";
    var $gOPD = require_gOPD();
    if ($gOPD) {
      try {
        $gOPD([], "length");
      } catch (e) {
        $gOPD = null;
      }
    }
    module.exports = $gOPD;
  }
});

// ../shared/core/node_modules/es-define-property/index.js
var require_es_define_property = __commonJS({
  "../shared/core/node_modules/es-define-property/index.js"(exports, module) {
    "use strict";
    var $defineProperty = Object.defineProperty || false;
    if ($defineProperty) {
      try {
        $defineProperty({}, "a", { value: 1 });
      } catch (e) {
        $defineProperty = false;
      }
    }
    module.exports = $defineProperty;
  }
});

// ../shared/core/node_modules/has-symbols/shams.js
var require_shams = __commonJS({
  "../shared/core/node_modules/has-symbols/shams.js"(exports, module) {
    "use strict";
    module.exports = function hasSymbols() {
      if (typeof Symbol !== "function" || typeof Object.getOwnPropertySymbols !== "function") {
        return false;
      }
      if (typeof Symbol.iterator === "symbol") {
        return true;
      }
      var obj = {};
      var sym = Symbol("test");
      var symObj = Object(sym);
      if (typeof sym === "string") {
        return false;
      }
      if (Object.prototype.toString.call(sym) !== "[object Symbol]") {
        return false;
      }
      if (Object.prototype.toString.call(symObj) !== "[object Symbol]") {
        return false;
      }
      var symVal = 42;
      obj[sym] = symVal;
      for (var _ in obj) {
        return false;
      }
      if (typeof Object.keys === "function" && Object.keys(obj).length !== 0) {
        return false;
      }
      if (typeof Object.getOwnPropertyNames === "function" && Object.getOwnPropertyNames(obj).length !== 0) {
        return false;
      }
      var syms = Object.getOwnPropertySymbols(obj);
      if (syms.length !== 1 || syms[0] !== sym) {
        return false;
      }
      if (!Object.prototype.propertyIsEnumerable.call(obj, sym)) {
        return false;
      }
      if (typeof Object.getOwnPropertyDescriptor === "function") {
        var descriptor = (
          /** @type {PropertyDescriptor} */
          Object.getOwnPropertyDescriptor(obj, sym)
        );
        if (descriptor.value !== symVal || descriptor.enumerable !== true) {
          return false;
        }
      }
      return true;
    };
  }
});

// ../shared/core/node_modules/has-symbols/index.js
var require_has_symbols = __commonJS({
  "../shared/core/node_modules/has-symbols/index.js"(exports, module) {
    "use strict";
    var origSymbol = typeof Symbol !== "undefined" && Symbol;
    var hasSymbolSham = require_shams();
    module.exports = function hasNativeSymbols() {
      if (typeof origSymbol !== "function") {
        return false;
      }
      if (typeof Symbol !== "function") {
        return false;
      }
      if (typeof origSymbol("foo") !== "symbol") {
        return false;
      }
      if (typeof Symbol("bar") !== "symbol") {
        return false;
      }
      return hasSymbolSham();
    };
  }
});

// ../shared/core/node_modules/get-proto/Reflect.getPrototypeOf.js
var require_Reflect_getPrototypeOf = __commonJS({
  "../shared/core/node_modules/get-proto/Reflect.getPrototypeOf.js"(exports, module) {
    "use strict";
    module.exports = typeof Reflect !== "undefined" && Reflect.getPrototypeOf || null;
  }
});

// ../shared/core/node_modules/get-proto/Object.getPrototypeOf.js
var require_Object_getPrototypeOf = __commonJS({
  "../shared/core/node_modules/get-proto/Object.getPrototypeOf.js"(exports, module) {
    "use strict";
    var $Object = require_es_object_atoms();
    module.exports = $Object.getPrototypeOf || null;
  }
});

// ../shared/core/node_modules/function-bind/implementation.js
var require_implementation = __commonJS({
  "../shared/core/node_modules/function-bind/implementation.js"(exports, module) {
    "use strict";
    var ERROR_MESSAGE = "Function.prototype.bind called on incompatible ";
    var toStr = Object.prototype.toString;
    var max = Math.max;
    var funcType = "[object Function]";
    var concatty = function concatty2(a, b) {
      var arr = [];
      for (var i = 0; i < a.length; i += 1) {
        arr[i] = a[i];
      }
      for (var j = 0; j < b.length; j += 1) {
        arr[j + a.length] = b[j];
      }
      return arr;
    };
    var slicy = function slicy2(arrLike, offset) {
      var arr = [];
      for (var i = offset || 0, j = 0; i < arrLike.length; i += 1, j += 1) {
        arr[j] = arrLike[i];
      }
      return arr;
    };
    var joiny = function(arr, joiner) {
      var str = "";
      for (var i = 0; i < arr.length; i += 1) {
        str += arr[i];
        if (i + 1 < arr.length) {
          str += joiner;
        }
      }
      return str;
    };
    module.exports = function bind2(that) {
      var target = this;
      if (typeof target !== "function" || toStr.apply(target) !== funcType) {
        throw new TypeError(ERROR_MESSAGE + target);
      }
      var args = slicy(arguments, 1);
      var bound;
      var binder = function() {
        if (this instanceof bound) {
          var result = target.apply(
            this,
            concatty(args, arguments)
          );
          if (Object(result) === result) {
            return result;
          }
          return this;
        }
        return target.apply(
          that,
          concatty(args, arguments)
        );
      };
      var boundLength = max(0, target.length - args.length);
      var boundArgs = [];
      for (var i = 0; i < boundLength; i++) {
        boundArgs[i] = "$" + i;
      }
      bound = Function("binder", "return function (" + joiny(boundArgs, ",") + "){ return binder.apply(this,arguments); }")(binder);
      if (target.prototype) {
        var Empty = function Empty2() {
        };
        Empty.prototype = target.prototype;
        bound.prototype = new Empty();
        Empty.prototype = null;
      }
      return bound;
    };
  }
});

// ../shared/core/node_modules/function-bind/index.js
var require_function_bind = __commonJS({
  "../shared/core/node_modules/function-bind/index.js"(exports, module) {
    "use strict";
    var implementation = require_implementation();
    module.exports = Function.prototype.bind || implementation;
  }
});

// ../shared/core/node_modules/call-bind-apply-helpers/functionCall.js
var require_functionCall = __commonJS({
  "../shared/core/node_modules/call-bind-apply-helpers/functionCall.js"(exports, module) {
    "use strict";
    module.exports = Function.prototype.call;
  }
});

// ../shared/core/node_modules/call-bind-apply-helpers/functionApply.js
var require_functionApply = __commonJS({
  "../shared/core/node_modules/call-bind-apply-helpers/functionApply.js"(exports, module) {
    "use strict";
    module.exports = Function.prototype.apply;
  }
});

// ../shared/core/node_modules/call-bind-apply-helpers/reflectApply.js
var require_reflectApply = __commonJS({
  "../shared/core/node_modules/call-bind-apply-helpers/reflectApply.js"(exports, module) {
    "use strict";
    module.exports = typeof Reflect !== "undefined" && Reflect && Reflect.apply;
  }
});

// ../shared/core/node_modules/call-bind-apply-helpers/actualApply.js
var require_actualApply = __commonJS({
  "../shared/core/node_modules/call-bind-apply-helpers/actualApply.js"(exports, module) {
    "use strict";
    var bind2 = require_function_bind();
    var $apply = require_functionApply();
    var $call = require_functionCall();
    var $reflectApply = require_reflectApply();
    module.exports = $reflectApply || bind2.call($call, $apply);
  }
});

// ../shared/core/node_modules/call-bind-apply-helpers/index.js
var require_call_bind_apply_helpers = __commonJS({
  "../shared/core/node_modules/call-bind-apply-helpers/index.js"(exports, module) {
    "use strict";
    var bind2 = require_function_bind();
    var $TypeError = require_type();
    var $call = require_functionCall();
    var $actualApply = require_actualApply();
    module.exports = function callBindBasic(args) {
      if (args.length < 1 || typeof args[0] !== "function") {
        throw new $TypeError("a function is required");
      }
      return $actualApply(bind2, $call, args);
    };
  }
});

// ../shared/core/node_modules/dunder-proto/get.js
var require_get = __commonJS({
  "../shared/core/node_modules/dunder-proto/get.js"(exports, module) {
    "use strict";
    var callBind = require_call_bind_apply_helpers();
    var gOPD = require_gopd();
    var hasProtoAccessor;
    try {
      hasProtoAccessor = /** @type {{ __proto__?: typeof Array.prototype }} */
      [].__proto__ === Array.prototype;
    } catch (e) {
      if (!e || typeof e !== "object" || !("code" in e) || e.code !== "ERR_PROTO_ACCESS") {
        throw e;
      }
    }
    var desc = !!hasProtoAccessor && gOPD && gOPD(
      Object.prototype,
      /** @type {keyof typeof Object.prototype} */
      "__proto__"
    );
    var $Object = Object;
    var $getPrototypeOf = $Object.getPrototypeOf;
    module.exports = desc && typeof desc.get === "function" ? callBind([desc.get]) : typeof $getPrototypeOf === "function" ? (
      /** @type {import('./get')} */
      function getDunder(value) {
        return $getPrototypeOf(value == null ? value : $Object(value));
      }
    ) : false;
  }
});

// ../shared/core/node_modules/get-proto/index.js
var require_get_proto = __commonJS({
  "../shared/core/node_modules/get-proto/index.js"(exports, module) {
    "use strict";
    var reflectGetProto = require_Reflect_getPrototypeOf();
    var originalGetProto = require_Object_getPrototypeOf();
    var getDunderProto = require_get();
    module.exports = reflectGetProto ? function getProto(O) {
      return reflectGetProto(O);
    } : originalGetProto ? function getProto(O) {
      if (!O || typeof O !== "object" && typeof O !== "function") {
        throw new TypeError("getProto: not an object");
      }
      return originalGetProto(O);
    } : getDunderProto ? function getProto(O) {
      return getDunderProto(O);
    } : null;
  }
});

// ../shared/core/node_modules/hasown/index.js
var require_hasown = __commonJS({
  "../shared/core/node_modules/hasown/index.js"(exports, module) {
    "use strict";
    var call = Function.prototype.call;
    var $hasOwn = Object.prototype.hasOwnProperty;
    var bind2 = require_function_bind();
    module.exports = bind2.call(call, $hasOwn);
  }
});

// ../shared/core/node_modules/get-intrinsic/index.js
var require_get_intrinsic = __commonJS({
  "../shared/core/node_modules/get-intrinsic/index.js"(exports, module) {
    "use strict";
    var undefined2;
    var $Object = require_es_object_atoms();
    var $Error = require_es_errors();
    var $EvalError = require_eval();
    var $RangeError = require_range();
    var $ReferenceError = require_ref();
    var $SyntaxError = require_syntax();
    var $TypeError = require_type();
    var $URIError = require_uri();
    var abs = require_abs();
    var floor = require_floor();
    var max = require_max();
    var min = require_min();
    var pow = require_pow();
    var round = require_round();
    var sign = require_sign();
    var $Function = Function;
    var getEvalledConstructor = function(expressionSyntax) {
      try {
        return $Function('"use strict"; return (' + expressionSyntax + ").constructor;")();
      } catch (e) {
      }
    };
    var $gOPD = require_gopd();
    var $defineProperty = require_es_define_property();
    var throwTypeError = function() {
      throw new $TypeError();
    };
    var ThrowTypeError = $gOPD ? function() {
      try {
        arguments.callee;
        return throwTypeError;
      } catch (calleeThrows) {
        try {
          return $gOPD(arguments, "callee").get;
        } catch (gOPDthrows) {
          return throwTypeError;
        }
      }
    }() : throwTypeError;
    var hasSymbols = require_has_symbols()();
    var getProto = require_get_proto();
    var $ObjectGPO = require_Object_getPrototypeOf();
    var $ReflectGPO = require_Reflect_getPrototypeOf();
    var $apply = require_functionApply();
    var $call = require_functionCall();
    var needsEval = {};
    var TypedArray = typeof Uint8Array === "undefined" || !getProto ? undefined2 : getProto(Uint8Array);
    var INTRINSICS = {
      __proto__: null,
      "%AggregateError%": typeof AggregateError === "undefined" ? undefined2 : AggregateError,
      "%Array%": Array,
      "%ArrayBuffer%": typeof ArrayBuffer === "undefined" ? undefined2 : ArrayBuffer,
      "%ArrayIteratorPrototype%": hasSymbols && getProto ? getProto([][Symbol.iterator]()) : undefined2,
      "%AsyncFromSyncIteratorPrototype%": undefined2,
      "%AsyncFunction%": needsEval,
      "%AsyncGenerator%": needsEval,
      "%AsyncGeneratorFunction%": needsEval,
      "%AsyncIteratorPrototype%": needsEval,
      "%Atomics%": typeof Atomics === "undefined" ? undefined2 : Atomics,
      "%BigInt%": typeof BigInt === "undefined" ? undefined2 : BigInt,
      "%BigInt64Array%": typeof BigInt64Array === "undefined" ? undefined2 : BigInt64Array,
      "%BigUint64Array%": typeof BigUint64Array === "undefined" ? undefined2 : BigUint64Array,
      "%Boolean%": Boolean,
      "%DataView%": typeof DataView === "undefined" ? undefined2 : DataView,
      "%Date%": Date,
      "%decodeURI%": decodeURI,
      "%decodeURIComponent%": decodeURIComponent,
      "%encodeURI%": encodeURI,
      "%encodeURIComponent%": encodeURIComponent,
      "%Error%": $Error,
      "%eval%": eval,
      // eslint-disable-line no-eval
      "%EvalError%": $EvalError,
      "%Float16Array%": typeof Float16Array === "undefined" ? undefined2 : Float16Array,
      "%Float32Array%": typeof Float32Array === "undefined" ? undefined2 : Float32Array,
      "%Float64Array%": typeof Float64Array === "undefined" ? undefined2 : Float64Array,
      "%FinalizationRegistry%": typeof FinalizationRegistry === "undefined" ? undefined2 : FinalizationRegistry,
      "%Function%": $Function,
      "%GeneratorFunction%": needsEval,
      "%Int8Array%": typeof Int8Array === "undefined" ? undefined2 : Int8Array,
      "%Int16Array%": typeof Int16Array === "undefined" ? undefined2 : Int16Array,
      "%Int32Array%": typeof Int32Array === "undefined" ? undefined2 : Int32Array,
      "%isFinite%": isFinite,
      "%isNaN%": isNaN,
      "%IteratorPrototype%": hasSymbols && getProto ? getProto(getProto([][Symbol.iterator]())) : undefined2,
      "%JSON%": typeof JSON === "object" ? JSON : undefined2,
      "%Map%": typeof Map === "undefined" ? undefined2 : Map,
      "%MapIteratorPrototype%": typeof Map === "undefined" || !hasSymbols || !getProto ? undefined2 : getProto((/* @__PURE__ */ new Map())[Symbol.iterator]()),
      "%Math%": Math,
      "%Number%": Number,
      "%Object%": $Object,
      "%Object.getOwnPropertyDescriptor%": $gOPD,
      "%parseFloat%": parseFloat,
      "%parseInt%": parseInt,
      "%Promise%": typeof Promise === "undefined" ? undefined2 : Promise,
      "%Proxy%": typeof Proxy === "undefined" ? undefined2 : Proxy,
      "%RangeError%": $RangeError,
      "%ReferenceError%": $ReferenceError,
      "%Reflect%": typeof Reflect === "undefined" ? undefined2 : Reflect,
      "%RegExp%": RegExp,
      "%Set%": typeof Set === "undefined" ? undefined2 : Set,
      "%SetIteratorPrototype%": typeof Set === "undefined" || !hasSymbols || !getProto ? undefined2 : getProto((/* @__PURE__ */ new Set())[Symbol.iterator]()),
      "%SharedArrayBuffer%": typeof SharedArrayBuffer === "undefined" ? undefined2 : SharedArrayBuffer,
      "%String%": String,
      "%StringIteratorPrototype%": hasSymbols && getProto ? getProto(""[Symbol.iterator]()) : undefined2,
      "%Symbol%": hasSymbols ? Symbol : undefined2,
      "%SyntaxError%": $SyntaxError,
      "%ThrowTypeError%": ThrowTypeError,
      "%TypedArray%": TypedArray,
      "%TypeError%": $TypeError,
      "%Uint8Array%": typeof Uint8Array === "undefined" ? undefined2 : Uint8Array,
      "%Uint8ClampedArray%": typeof Uint8ClampedArray === "undefined" ? undefined2 : Uint8ClampedArray,
      "%Uint16Array%": typeof Uint16Array === "undefined" ? undefined2 : Uint16Array,
      "%Uint32Array%": typeof Uint32Array === "undefined" ? undefined2 : Uint32Array,
      "%URIError%": $URIError,
      "%WeakMap%": typeof WeakMap === "undefined" ? undefined2 : WeakMap,
      "%WeakRef%": typeof WeakRef === "undefined" ? undefined2 : WeakRef,
      "%WeakSet%": typeof WeakSet === "undefined" ? undefined2 : WeakSet,
      "%Function.prototype.call%": $call,
      "%Function.prototype.apply%": $apply,
      "%Object.defineProperty%": $defineProperty,
      "%Object.getPrototypeOf%": $ObjectGPO,
      "%Math.abs%": abs,
      "%Math.floor%": floor,
      "%Math.max%": max,
      "%Math.min%": min,
      "%Math.pow%": pow,
      "%Math.round%": round,
      "%Math.sign%": sign,
      "%Reflect.getPrototypeOf%": $ReflectGPO
    };
    if (getProto) {
      try {
        null.error;
      } catch (e) {
        errorProto = getProto(getProto(e));
        INTRINSICS["%Error.prototype%"] = errorProto;
      }
    }
    var errorProto;
    var doEval = function doEval2(name) {
      var value;
      if (name === "%AsyncFunction%") {
        value = getEvalledConstructor("async function () {}");
      } else if (name === "%GeneratorFunction%") {
        value = getEvalledConstructor("function* () {}");
      } else if (name === "%AsyncGeneratorFunction%") {
        value = getEvalledConstructor("async function* () {}");
      } else if (name === "%AsyncGenerator%") {
        var fn = doEval2("%AsyncGeneratorFunction%");
        if (fn) {
          value = fn.prototype;
        }
      } else if (name === "%AsyncIteratorPrototype%") {
        var gen = doEval2("%AsyncGenerator%");
        if (gen && getProto) {
          value = getProto(gen.prototype);
        }
      }
      INTRINSICS[name] = value;
      return value;
    };
    var LEGACY_ALIASES = {
      __proto__: null,
      "%ArrayBufferPrototype%": ["ArrayBuffer", "prototype"],
      "%ArrayPrototype%": ["Array", "prototype"],
      "%ArrayProto_entries%": ["Array", "prototype", "entries"],
      "%ArrayProto_forEach%": ["Array", "prototype", "forEach"],
      "%ArrayProto_keys%": ["Array", "prototype", "keys"],
      "%ArrayProto_values%": ["Array", "prototype", "values"],
      "%AsyncFunctionPrototype%": ["AsyncFunction", "prototype"],
      "%AsyncGenerator%": ["AsyncGeneratorFunction", "prototype"],
      "%AsyncGeneratorPrototype%": ["AsyncGeneratorFunction", "prototype", "prototype"],
      "%BooleanPrototype%": ["Boolean", "prototype"],
      "%DataViewPrototype%": ["DataView", "prototype"],
      "%DatePrototype%": ["Date", "prototype"],
      "%ErrorPrototype%": ["Error", "prototype"],
      "%EvalErrorPrototype%": ["EvalError", "prototype"],
      "%Float32ArrayPrototype%": ["Float32Array", "prototype"],
      "%Float64ArrayPrototype%": ["Float64Array", "prototype"],
      "%FunctionPrototype%": ["Function", "prototype"],
      "%Generator%": ["GeneratorFunction", "prototype"],
      "%GeneratorPrototype%": ["GeneratorFunction", "prototype", "prototype"],
      "%Int8ArrayPrototype%": ["Int8Array", "prototype"],
      "%Int16ArrayPrototype%": ["Int16Array", "prototype"],
      "%Int32ArrayPrototype%": ["Int32Array", "prototype"],
      "%JSONParse%": ["JSON", "parse"],
      "%JSONStringify%": ["JSON", "stringify"],
      "%MapPrototype%": ["Map", "prototype"],
      "%NumberPrototype%": ["Number", "prototype"],
      "%ObjectPrototype%": ["Object", "prototype"],
      "%ObjProto_toString%": ["Object", "prototype", "toString"],
      "%ObjProto_valueOf%": ["Object", "prototype", "valueOf"],
      "%PromisePrototype%": ["Promise", "prototype"],
      "%PromiseProto_then%": ["Promise", "prototype", "then"],
      "%Promise_all%": ["Promise", "all"],
      "%Promise_reject%": ["Promise", "reject"],
      "%Promise_resolve%": ["Promise", "resolve"],
      "%RangeErrorPrototype%": ["RangeError", "prototype"],
      "%ReferenceErrorPrototype%": ["ReferenceError", "prototype"],
      "%RegExpPrototype%": ["RegExp", "prototype"],
      "%SetPrototype%": ["Set", "prototype"],
      "%SharedArrayBufferPrototype%": ["SharedArrayBuffer", "prototype"],
      "%StringPrototype%": ["String", "prototype"],
      "%SymbolPrototype%": ["Symbol", "prototype"],
      "%SyntaxErrorPrototype%": ["SyntaxError", "prototype"],
      "%TypedArrayPrototype%": ["TypedArray", "prototype"],
      "%TypeErrorPrototype%": ["TypeError", "prototype"],
      "%Uint8ArrayPrototype%": ["Uint8Array", "prototype"],
      "%Uint8ClampedArrayPrototype%": ["Uint8ClampedArray", "prototype"],
      "%Uint16ArrayPrototype%": ["Uint16Array", "prototype"],
      "%Uint32ArrayPrototype%": ["Uint32Array", "prototype"],
      "%URIErrorPrototype%": ["URIError", "prototype"],
      "%WeakMapPrototype%": ["WeakMap", "prototype"],
      "%WeakSetPrototype%": ["WeakSet", "prototype"]
    };
    var bind2 = require_function_bind();
    var hasOwn = require_hasown();
    var $concat = bind2.call($call, Array.prototype.concat);
    var $spliceApply = bind2.call($apply, Array.prototype.splice);
    var $replace = bind2.call($call, String.prototype.replace);
    var $strSlice = bind2.call($call, String.prototype.slice);
    var $exec = bind2.call($call, RegExp.prototype.exec);
    var rePropName = /[^%.[\]]+|\[(?:(-?\d+(?:\.\d+)?)|(["'])((?:(?!\2)[^\\]|\\.)*?)\2)\]|(?=(?:\.|\[\])(?:\.|\[\]|%$))/g;
    var reEscapeChar = /\\(\\)?/g;
    var stringToPath = function stringToPath2(string) {
      var first = $strSlice(string, 0, 1);
      var last = $strSlice(string, -1);
      if (first === "%" && last !== "%") {
        throw new $SyntaxError("invalid intrinsic syntax, expected closing `%`");
      } else if (last === "%" && first !== "%") {
        throw new $SyntaxError("invalid intrinsic syntax, expected opening `%`");
      }
      var result = [];
      $replace(string, rePropName, function(match, number, quote, subString) {
        result[result.length] = quote ? $replace(subString, reEscapeChar, "$1") : number || match;
      });
      return result;
    };
    var getBaseIntrinsic = function getBaseIntrinsic2(name, allowMissing) {
      var intrinsicName = name;
      var alias;
      if (hasOwn(LEGACY_ALIASES, intrinsicName)) {
        alias = LEGACY_ALIASES[intrinsicName];
        intrinsicName = "%" + alias[0] + "%";
      }
      if (hasOwn(INTRINSICS, intrinsicName)) {
        var value = INTRINSICS[intrinsicName];
        if (value === needsEval) {
          value = doEval(intrinsicName);
        }
        if (typeof value === "undefined" && !allowMissing) {
          throw new $TypeError("intrinsic " + name + " exists, but is not available. Please file an issue!");
        }
        return {
          alias,
          name: intrinsicName,
          value
        };
      }
      throw new $SyntaxError("intrinsic " + name + " does not exist!");
    };
    module.exports = function GetIntrinsic(name, allowMissing) {
      if (typeof name !== "string" || name.length === 0) {
        throw new $TypeError("intrinsic name must be a non-empty string");
      }
      if (arguments.length > 1 && typeof allowMissing !== "boolean") {
        throw new $TypeError('"allowMissing" argument must be a boolean');
      }
      if ($exec(/^%?[^%]*%?$/, name) === null) {
        throw new $SyntaxError("`%` may not be present anywhere but at the beginning and end of the intrinsic name");
      }
      var parts = stringToPath(name);
      var intrinsicBaseName = parts.length > 0 ? parts[0] : "";
      var intrinsic = getBaseIntrinsic("%" + intrinsicBaseName + "%", allowMissing);
      var intrinsicRealName = intrinsic.name;
      var value = intrinsic.value;
      var skipFurtherCaching = false;
      var alias = intrinsic.alias;
      if (alias) {
        intrinsicBaseName = alias[0];
        $spliceApply(parts, $concat([0, 1], alias));
      }
      for (var i = 1, isOwn = true; i < parts.length; i += 1) {
        var part = parts[i];
        var first = $strSlice(part, 0, 1);
        var last = $strSlice(part, -1);
        if ((first === '"' || first === "'" || first === "`" || (last === '"' || last === "'" || last === "`")) && first !== last) {
          throw new $SyntaxError("property names with quotes must have matching quotes");
        }
        if (part === "constructor" || !isOwn) {
          skipFurtherCaching = true;
        }
        intrinsicBaseName += "." + part;
        intrinsicRealName = "%" + intrinsicBaseName + "%";
        if (hasOwn(INTRINSICS, intrinsicRealName)) {
          value = INTRINSICS[intrinsicRealName];
        } else if (value != null) {
          if (!(part in value)) {
            if (!allowMissing) {
              throw new $TypeError("base intrinsic for " + name + " exists, but the property is not available.");
            }
            return void 0;
          }
          if ($gOPD && i + 1 >= parts.length) {
            var desc = $gOPD(value, part);
            isOwn = !!desc;
            if (isOwn && "get" in desc && !("originalValue" in desc.get)) {
              value = desc.get;
            } else {
              value = value[part];
            }
          } else {
            isOwn = hasOwn(value, part);
            value = value[part];
          }
          if (isOwn && !skipFurtherCaching) {
            INTRINSICS[intrinsicRealName] = value;
          }
        }
      }
      return value;
    };
  }
});

// ../shared/core/node_modules/has-tostringtag/shams.js
var require_shams2 = __commonJS({
  "../shared/core/node_modules/has-tostringtag/shams.js"(exports, module) {
    "use strict";
    var hasSymbols = require_shams();
    module.exports = function hasToStringTagShams() {
      return hasSymbols() && !!Symbol.toStringTag;
    };
  }
});

// ../shared/core/node_modules/es-set-tostringtag/index.js
var require_es_set_tostringtag = __commonJS({
  "../shared/core/node_modules/es-set-tostringtag/index.js"(exports, module) {
    "use strict";
    var GetIntrinsic = require_get_intrinsic();
    var $defineProperty = GetIntrinsic("%Object.defineProperty%", true);
    var hasToStringTag = require_shams2()();
    var hasOwn = require_hasown();
    var $TypeError = require_type();
    var toStringTag2 = hasToStringTag ? Symbol.toStringTag : null;
    module.exports = function setToStringTag(object, value) {
      var overrideIfSet = arguments.length > 2 && !!arguments[2] && arguments[2].force;
      var nonConfigurable = arguments.length > 2 && !!arguments[2] && arguments[2].nonConfigurable;
      if (typeof overrideIfSet !== "undefined" && typeof overrideIfSet !== "boolean" || typeof nonConfigurable !== "undefined" && typeof nonConfigurable !== "boolean") {
        throw new $TypeError("if provided, the `overrideIfSet` and `nonConfigurable` options must be booleans");
      }
      if (toStringTag2 && (overrideIfSet || !hasOwn(object, toStringTag2))) {
        if ($defineProperty) {
          $defineProperty(object, toStringTag2, {
            configurable: !nonConfigurable,
            enumerable: false,
            value,
            writable: false
          });
        } else {
          object[toStringTag2] = value;
        }
      }
    };
  }
});

// ../shared/core/node_modules/form-data/lib/populate.js
var require_populate = __commonJS({
  "../shared/core/node_modules/form-data/lib/populate.js"(exports, module) {
    "use strict";
    module.exports = function(dst, src) {
      Object.keys(src).forEach(function(prop) {
        dst[prop] = dst[prop] || src[prop];
      });
      return dst;
    };
  }
});

// ../shared/core/node_modules/form-data/lib/form_data.js
var require_form_data = __commonJS({
  "../shared/core/node_modules/form-data/lib/form_data.js"(exports, module) {
    "use strict";
    var CombinedStream = require_combined_stream();
    var util4 = __require("util");
    var path2 = __require("path");
    var http3 = __require("http");
    var https2 = __require("https");
    var parseUrl2 = __require("url").parse;
    var fs6 = __require("fs");
    var Stream = __require("stream").Stream;
    var crypto6 = __require("crypto");
    var mime = require_mime_types();
    var asynckit = require_asynckit();
    var setToStringTag = require_es_set_tostringtag();
    var hasOwn = require_hasown();
    var populate = require_populate();
    function escapeHeaderParam(str) {
      return String(str).replace(/\r/g, "%0D").replace(/\n/g, "%0A").replace(/"/g, "%22");
    }
    function FormData3(options) {
      if (!(this instanceof FormData3)) {
        return new FormData3(options);
      }
      this._overheadLength = 0;
      this._valueLength = 0;
      this._valuesToMeasure = [];
      CombinedStream.call(this);
      options = options || {};
      for (var option in options) {
        this[option] = options[option];
      }
    }
    util4.inherits(FormData3, CombinedStream);
    FormData3.LINE_BREAK = "\r\n";
    FormData3.DEFAULT_CONTENT_TYPE = "application/octet-stream";
    FormData3.prototype.append = function(field, value, options) {
      options = options || {};
      if (typeof options === "string") {
        options = { filename: options };
      }
      var append2 = CombinedStream.prototype.append.bind(this);
      if (typeof value === "number" || value == null) {
        value = String(value);
      }
      if (Array.isArray(value)) {
        this._error(new Error("Arrays are not supported."));
        return;
      }
      var header = this._multiPartHeader(field, value, options);
      var footer = this._multiPartFooter();
      append2(header);
      append2(value);
      append2(footer);
      this._trackLength(header, value, options);
    };
    FormData3.prototype._trackLength = function(header, value, options) {
      var valueLength = 0;
      if (options.knownLength != null) {
        valueLength += Number(options.knownLength);
      } else if (Buffer.isBuffer(value)) {
        valueLength = value.length;
      } else if (typeof value === "string") {
        valueLength = Buffer.byteLength(value);
      }
      this._valueLength += valueLength;
      this._overheadLength += Buffer.byteLength(header) + FormData3.LINE_BREAK.length;
      if (!value || !value.path && !(value.readable && hasOwn(value, "httpVersion")) && !(value instanceof Stream)) {
        return;
      }
      if (!options.knownLength) {
        this._valuesToMeasure.push(value);
      }
    };
    FormData3.prototype._lengthRetriever = function(value, callback) {
      if (hasOwn(value, "fd")) {
        if (value.end != void 0 && value.end != Infinity && value.start != void 0) {
          callback(null, value.end + 1 - (value.start ? value.start : 0));
        } else {
          fs6.stat(value.path, function(err, stat) {
            if (err) {
              callback(err);
              return;
            }
            var fileSize = stat.size - (value.start ? value.start : 0);
            callback(null, fileSize);
          });
        }
      } else if (hasOwn(value, "httpVersion")) {
        callback(null, Number(value.headers["content-length"]));
      } else if (hasOwn(value, "httpModule")) {
        value.on("response", function(response) {
          value.pause();
          callback(null, Number(response.headers["content-length"]));
        });
        value.resume();
      } else {
        callback("Unknown stream");
      }
    };
    FormData3.prototype._multiPartHeader = function(field, value, options) {
      if (typeof options.header === "string") {
        return options.header;
      }
      var contentDisposition = this._getContentDisposition(value, options);
      var contentType = this._getContentType(value, options);
      var contents = "";
      var headers = {
        // add custom disposition as third element or keep it two elements if not
        "Content-Disposition": ["form-data", 'name="' + escapeHeaderParam(field) + '"'].concat(contentDisposition || []),
        // if no content type. allow it to be empty array
        "Content-Type": [].concat(contentType || [])
      };
      if (typeof options.header === "object") {
        populate(headers, options.header);
      }
      var header;
      for (var prop in headers) {
        if (hasOwn(headers, prop)) {
          header = headers[prop];
          if (header == null) {
            continue;
          }
          if (!Array.isArray(header)) {
            header = [header];
          }
          if (header.length) {
            contents += prop + ": " + header.join("; ") + FormData3.LINE_BREAK;
          }
        }
      }
      return "--" + this.getBoundary() + FormData3.LINE_BREAK + contents + FormData3.LINE_BREAK;
    };
    FormData3.prototype._getContentDisposition = function(value, options) {
      var filename;
      if (typeof options.filepath === "string") {
        filename = path2.normalize(options.filepath).replace(/\\/g, "/");
      } else if (options.filename || value && (value.name || value.path)) {
        filename = path2.basename(options.filename || value && (value.name || value.path));
      } else if (value && value.readable && hasOwn(value, "httpVersion")) {
        filename = path2.basename(value.client._httpMessage.path || "");
      }
      if (filename) {
        return 'filename="' + escapeHeaderParam(filename) + '"';
      }
    };
    FormData3.prototype._getContentType = function(value, options) {
      var contentType = options.contentType;
      if (!contentType && value && value.name) {
        contentType = mime.lookup(value.name);
      }
      if (!contentType && value && value.path) {
        contentType = mime.lookup(value.path);
      }
      if (!contentType && value && value.readable && hasOwn(value, "httpVersion")) {
        contentType = value.headers["content-type"];
      }
      if (!contentType && (options.filepath || options.filename)) {
        contentType = mime.lookup(options.filepath || options.filename);
      }
      if (!contentType && value && typeof value === "object") {
        contentType = FormData3.DEFAULT_CONTENT_TYPE;
      }
      return contentType;
    };
    FormData3.prototype._multiPartFooter = function() {
      return function(next) {
        var footer = FormData3.LINE_BREAK;
        var lastPart = this._streams.length === 0;
        if (lastPart) {
          footer += this._lastBoundary();
        }
        next(footer);
      }.bind(this);
    };
    FormData3.prototype._lastBoundary = function() {
      return "--" + this.getBoundary() + "--" + FormData3.LINE_BREAK;
    };
    FormData3.prototype.getHeaders = function(userHeaders) {
      var header;
      var formHeaders = {
        "content-type": "multipart/form-data; boundary=" + this.getBoundary()
      };
      for (header in userHeaders) {
        if (hasOwn(userHeaders, header)) {
          formHeaders[header.toLowerCase()] = userHeaders[header];
        }
      }
      return formHeaders;
    };
    FormData3.prototype.setBoundary = function(boundary) {
      if (typeof boundary !== "string") {
        throw new TypeError("FormData boundary must be a string");
      }
      this._boundary = boundary;
    };
    FormData3.prototype.getBoundary = function() {
      if (!this._boundary) {
        this._generateBoundary();
      }
      return this._boundary;
    };
    FormData3.prototype.getBuffer = function() {
      var dataBuffer = new Buffer.alloc(0);
      var boundary = this.getBoundary();
      for (var i = 0, len = this._streams.length; i < len; i++) {
        if (typeof this._streams[i] !== "function") {
          if (Buffer.isBuffer(this._streams[i])) {
            dataBuffer = Buffer.concat([dataBuffer, this._streams[i]]);
          } else {
            dataBuffer = Buffer.concat([dataBuffer, Buffer.from(this._streams[i])]);
          }
          if (typeof this._streams[i] !== "string" || this._streams[i].substring(2, boundary.length + 2) !== boundary) {
            dataBuffer = Buffer.concat([dataBuffer, Buffer.from(FormData3.LINE_BREAK)]);
          }
        }
      }
      return Buffer.concat([dataBuffer, Buffer.from(this._lastBoundary())]);
    };
    FormData3.prototype._generateBoundary = function() {
      this._boundary = "--------------------------" + crypto6.randomBytes(12).toString("hex");
    };
    FormData3.prototype.getLengthSync = function() {
      var knownLength = this._overheadLength + this._valueLength;
      if (this._streams.length) {
        knownLength += this._lastBoundary().length;
      }
      if (!this.hasKnownLength()) {
        this._error(new Error("Cannot calculate proper length in synchronous way."));
      }
      return knownLength;
    };
    FormData3.prototype.hasKnownLength = function() {
      var hasKnownLength = true;
      if (this._valuesToMeasure.length) {
        hasKnownLength = false;
      }
      return hasKnownLength;
    };
    FormData3.prototype.getLength = function(cb) {
      var knownLength = this._overheadLength + this._valueLength;
      if (this._streams.length) {
        knownLength += this._lastBoundary().length;
      }
      if (!this._valuesToMeasure.length) {
        process.nextTick(cb.bind(this, null, knownLength));
        return;
      }
      asynckit.parallel(this._valuesToMeasure, this._lengthRetriever, function(err, values) {
        if (err) {
          cb(err);
          return;
        }
        values.forEach(function(length) {
          knownLength += length;
        });
        cb(null, knownLength);
      });
    };
    FormData3.prototype.submit = function(params, cb) {
      var request;
      var options;
      var defaults2 = { method: "post" };
      if (typeof params === "string") {
        params = parseUrl2(params);
        options = populate({
          port: params.port,
          path: params.pathname,
          host: params.hostname,
          protocol: params.protocol
        }, defaults2);
      } else {
        options = populate(params, defaults2);
        if (!options.port) {
          options.port = options.protocol === "https:" ? 443 : 80;
        }
      }
      options.headers = this.getHeaders(params.headers);
      if (options.protocol === "https:") {
        request = https2.request(options);
      } else {
        request = http3.request(options);
      }
      this.getLength(function(err, length) {
        if (err && err !== "Unknown stream") {
          this._error(err);
          return;
        }
        if (length) {
          request.setHeader("Content-Length", length);
        }
        this.pipe(request);
        if (cb) {
          var onResponse;
          var callback = function(error, responce) {
            request.removeListener("error", callback);
            request.removeListener("response", onResponse);
            return cb.call(this, error, responce);
          };
          onResponse = callback.bind(this, null);
          request.on("error", callback);
          request.on("response", onResponse);
        }
      }.bind(this));
      return request;
    };
    FormData3.prototype._error = function(err) {
      if (!this.error) {
        this.error = err;
        this.pause();
        this.emit("error", err);
      }
    };
    FormData3.prototype.toString = function() {
      return "[object FormData]";
    };
    setToStringTag(FormData3.prototype, "FormData");
    module.exports = FormData3;
  }
});

// ../shared/core/node_modules/ms/index.js
var require_ms = __commonJS({
  "../shared/core/node_modules/ms/index.js"(exports, module) {
    var s = 1e3;
    var m = s * 60;
    var h = m * 60;
    var d = h * 24;
    var w = d * 7;
    var y = d * 365.25;
    module.exports = function(val, options) {
      options = options || {};
      var type = typeof val;
      if (type === "string" && val.length > 0) {
        return parse(val);
      } else if (type === "number" && isFinite(val)) {
        return options.long ? fmtLong(val) : fmtShort(val);
      }
      throw new Error(
        "val is not a non-empty string or a valid number. val=" + JSON.stringify(val)
      );
    };
    function parse(str) {
      str = String(str);
      if (str.length > 100) {
        return;
      }
      var match = /^(-?(?:\d+)?\.?\d+) *(milliseconds?|msecs?|ms|seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w|years?|yrs?|y)?$/i.exec(
        str
      );
      if (!match) {
        return;
      }
      var n = parseFloat(match[1]);
      var type = (match[2] || "ms").toLowerCase();
      switch (type) {
        case "years":
        case "year":
        case "yrs":
        case "yr":
        case "y":
          return n * y;
        case "weeks":
        case "week":
        case "w":
          return n * w;
        case "days":
        case "day":
        case "d":
          return n * d;
        case "hours":
        case "hour":
        case "hrs":
        case "hr":
        case "h":
          return n * h;
        case "minutes":
        case "minute":
        case "mins":
        case "min":
        case "m":
          return n * m;
        case "seconds":
        case "second":
        case "secs":
        case "sec":
        case "s":
          return n * s;
        case "milliseconds":
        case "millisecond":
        case "msecs":
        case "msec":
        case "ms":
          return n;
        default:
          return void 0;
      }
    }
    function fmtShort(ms) {
      var msAbs = Math.abs(ms);
      if (msAbs >= d) {
        return Math.round(ms / d) + "d";
      }
      if (msAbs >= h) {
        return Math.round(ms / h) + "h";
      }
      if (msAbs >= m) {
        return Math.round(ms / m) + "m";
      }
      if (msAbs >= s) {
        return Math.round(ms / s) + "s";
      }
      return ms + "ms";
    }
    function fmtLong(ms) {
      var msAbs = Math.abs(ms);
      if (msAbs >= d) {
        return plural(ms, msAbs, d, "day");
      }
      if (msAbs >= h) {
        return plural(ms, msAbs, h, "hour");
      }
      if (msAbs >= m) {
        return plural(ms, msAbs, m, "minute");
      }
      if (msAbs >= s) {
        return plural(ms, msAbs, s, "second");
      }
      return ms + " ms";
    }
    function plural(ms, msAbs, n, name) {
      var isPlural = msAbs >= n * 1.5;
      return Math.round(ms / n) + " " + name + (isPlural ? "s" : "");
    }
  }
});

// ../shared/core/node_modules/debug/src/common.js
var require_common = __commonJS({
  "../shared/core/node_modules/debug/src/common.js"(exports, module) {
    function setup(env) {
      createDebug.debug = createDebug;
      createDebug.default = createDebug;
      createDebug.coerce = coerce;
      createDebug.disable = disable;
      createDebug.enable = enable;
      createDebug.enabled = enabled;
      createDebug.humanize = require_ms();
      createDebug.destroy = destroy;
      Object.keys(env).forEach((key) => {
        createDebug[key] = env[key];
      });
      createDebug.names = [];
      createDebug.skips = [];
      createDebug.formatters = {};
      function selectColor(namespace) {
        let hash = 0;
        for (let i = 0; i < namespace.length; i++) {
          hash = (hash << 5) - hash + namespace.charCodeAt(i);
          hash |= 0;
        }
        return createDebug.colors[Math.abs(hash) % createDebug.colors.length];
      }
      createDebug.selectColor = selectColor;
      function createDebug(namespace) {
        let prevTime;
        let enableOverride = null;
        let namespacesCache;
        let enabledCache;
        function debug(...args) {
          if (!debug.enabled) {
            return;
          }
          const self2 = debug;
          const curr = Number(/* @__PURE__ */ new Date());
          const ms = curr - (prevTime || curr);
          self2.diff = ms;
          self2.prev = prevTime;
          self2.curr = curr;
          prevTime = curr;
          args[0] = createDebug.coerce(args[0]);
          if (typeof args[0] !== "string") {
            args.unshift("%O");
          }
          let index = 0;
          args[0] = args[0].replace(/%([a-zA-Z%])/g, (match, format) => {
            if (match === "%%") {
              return "%";
            }
            index++;
            const formatter = createDebug.formatters[format];
            if (typeof formatter === "function") {
              const val = args[index];
              match = formatter.call(self2, val);
              args.splice(index, 1);
              index--;
            }
            return match;
          });
          createDebug.formatArgs.call(self2, args);
          const logFn = self2.log || createDebug.log;
          logFn.apply(self2, args);
        }
        debug.namespace = namespace;
        debug.useColors = createDebug.useColors();
        debug.color = createDebug.selectColor(namespace);
        debug.extend = extend2;
        debug.destroy = createDebug.destroy;
        Object.defineProperty(debug, "enabled", {
          enumerable: true,
          configurable: false,
          get: () => {
            if (enableOverride !== null) {
              return enableOverride;
            }
            if (namespacesCache !== createDebug.namespaces) {
              namespacesCache = createDebug.namespaces;
              enabledCache = createDebug.enabled(namespace);
            }
            return enabledCache;
          },
          set: (v) => {
            enableOverride = v;
          }
        });
        if (typeof createDebug.init === "function") {
          createDebug.init(debug);
        }
        return debug;
      }
      function extend2(namespace, delimiter) {
        const newDebug = createDebug(this.namespace + (typeof delimiter === "undefined" ? ":" : delimiter) + namespace);
        newDebug.log = this.log;
        return newDebug;
      }
      function enable(namespaces) {
        createDebug.save(namespaces);
        createDebug.namespaces = namespaces;
        createDebug.names = [];
        createDebug.skips = [];
        const split = (typeof namespaces === "string" ? namespaces : "").trim().replace(/\s+/g, ",").split(",").filter(Boolean);
        for (const ns of split) {
          if (ns[0] === "-") {
            createDebug.skips.push(ns.slice(1));
          } else {
            createDebug.names.push(ns);
          }
        }
      }
      function matchesTemplate(search, template) {
        let searchIndex = 0;
        let templateIndex = 0;
        let starIndex = -1;
        let matchIndex = 0;
        while (searchIndex < search.length) {
          if (templateIndex < template.length && (template[templateIndex] === search[searchIndex] || template[templateIndex] === "*")) {
            if (template[templateIndex] === "*") {
              starIndex = templateIndex;
              matchIndex = searchIndex;
              templateIndex++;
            } else {
              searchIndex++;
              templateIndex++;
            }
          } else if (starIndex !== -1) {
            templateIndex = starIndex + 1;
            matchIndex++;
            searchIndex = matchIndex;
          } else {
            return false;
          }
        }
        while (templateIndex < template.length && template[templateIndex] === "*") {
          templateIndex++;
        }
        return templateIndex === template.length;
      }
      function disable() {
        const namespaces = [
          ...createDebug.names,
          ...createDebug.skips.map((namespace) => "-" + namespace)
        ].join(",");
        createDebug.enable("");
        return namespaces;
      }
      function enabled(name) {
        for (const skip of createDebug.skips) {
          if (matchesTemplate(name, skip)) {
            return false;
          }
        }
        for (const ns of createDebug.names) {
          if (matchesTemplate(name, ns)) {
            return true;
          }
        }
        return false;
      }
      function coerce(val) {
        if (val instanceof Error) {
          return val.stack || val.message;
        }
        return val;
      }
      function destroy() {
        console.warn("Instance method `debug.destroy()` is deprecated and no longer does anything. It will be removed in the next major version of `debug`.");
      }
      createDebug.enable(createDebug.load());
      return createDebug;
    }
    module.exports = setup;
  }
});

// ../shared/core/node_modules/debug/src/browser.js
var require_browser = __commonJS({
  "../shared/core/node_modules/debug/src/browser.js"(exports, module) {
    exports.formatArgs = formatArgs;
    exports.save = save;
    exports.load = load;
    exports.useColors = useColors;
    exports.storage = localstorage();
    exports.destroy = /* @__PURE__ */ (() => {
      let warned = false;
      return () => {
        if (!warned) {
          warned = true;
          console.warn("Instance method `debug.destroy()` is deprecated and no longer does anything. It will be removed in the next major version of `debug`.");
        }
      };
    })();
    exports.colors = [
      "#0000CC",
      "#0000FF",
      "#0033CC",
      "#0033FF",
      "#0066CC",
      "#0066FF",
      "#0099CC",
      "#0099FF",
      "#00CC00",
      "#00CC33",
      "#00CC66",
      "#00CC99",
      "#00CCCC",
      "#00CCFF",
      "#3300CC",
      "#3300FF",
      "#3333CC",
      "#3333FF",
      "#3366CC",
      "#3366FF",
      "#3399CC",
      "#3399FF",
      "#33CC00",
      "#33CC33",
      "#33CC66",
      "#33CC99",
      "#33CCCC",
      "#33CCFF",
      "#6600CC",
      "#6600FF",
      "#6633CC",
      "#6633FF",
      "#66CC00",
      "#66CC33",
      "#9900CC",
      "#9900FF",
      "#9933CC",
      "#9933FF",
      "#99CC00",
      "#99CC33",
      "#CC0000",
      "#CC0033",
      "#CC0066",
      "#CC0099",
      "#CC00CC",
      "#CC00FF",
      "#CC3300",
      "#CC3333",
      "#CC3366",
      "#CC3399",
      "#CC33CC",
      "#CC33FF",
      "#CC6600",
      "#CC6633",
      "#CC9900",
      "#CC9933",
      "#CCCC00",
      "#CCCC33",
      "#FF0000",
      "#FF0033",
      "#FF0066",
      "#FF0099",
      "#FF00CC",
      "#FF00FF",
      "#FF3300",
      "#FF3333",
      "#FF3366",
      "#FF3399",
      "#FF33CC",
      "#FF33FF",
      "#FF6600",
      "#FF6633",
      "#FF9900",
      "#FF9933",
      "#FFCC00",
      "#FFCC33"
    ];
    function useColors() {
      if (typeof window !== "undefined" && window.process && (window.process.type === "renderer" || window.process.__nwjs)) {
        return true;
      }
      if (typeof navigator !== "undefined" && navigator.userAgent && navigator.userAgent.toLowerCase().match(/(edge|trident)\/(\d+)/)) {
        return false;
      }
      let m;
      return typeof document !== "undefined" && document.documentElement && document.documentElement.style && document.documentElement.style.WebkitAppearance || // Is firebug? http://stackoverflow.com/a/398120/376773
      typeof window !== "undefined" && window.console && (window.console.firebug || window.console.exception && window.console.table) || // Is firefox >= v31?
      // https://developer.mozilla.org/en-US/docs/Tools/Web_Console#Styling_messages
      typeof navigator !== "undefined" && navigator.userAgent && (m = navigator.userAgent.toLowerCase().match(/firefox\/(\d+)/)) && parseInt(m[1], 10) >= 31 || // Double check webkit in userAgent just in case we are in a worker
      typeof navigator !== "undefined" && navigator.userAgent && navigator.userAgent.toLowerCase().match(/applewebkit\/(\d+)/);
    }
    function formatArgs(args) {
      args[0] = (this.useColors ? "%c" : "") + this.namespace + (this.useColors ? " %c" : " ") + args[0] + (this.useColors ? "%c " : " ") + "+" + module.exports.humanize(this.diff);
      if (!this.useColors) {
        return;
      }
      const c = "color: " + this.color;
      args.splice(1, 0, c, "color: inherit");
      let index = 0;
      let lastC = 0;
      args[0].replace(/%[a-zA-Z%]/g, (match) => {
        if (match === "%%") {
          return;
        }
        index++;
        if (match === "%c") {
          lastC = index;
        }
      });
      args.splice(lastC, 0, c);
    }
    exports.log = console.debug || console.log || (() => {
    });
    function save(namespaces) {
      try {
        if (namespaces) {
          exports.storage.setItem("debug", namespaces);
        } else {
          exports.storage.removeItem("debug");
        }
      } catch (error) {
      }
    }
    function load() {
      let r;
      try {
        r = exports.storage.getItem("debug") || exports.storage.getItem("DEBUG");
      } catch (error) {
      }
      if (!r && typeof process !== "undefined" && "env" in process) {
        r = process.env.DEBUG;
      }
      return r;
    }
    function localstorage() {
      try {
        return localStorage;
      } catch (error) {
      }
    }
    module.exports = require_common()(exports);
    var { formatters } = module.exports;
    formatters.j = function(v) {
      try {
        return JSON.stringify(v);
      } catch (error) {
        return "[UnexpectedJSONParseError]: " + error.message;
      }
    };
  }
});

// ../shared/core/node_modules/has-flag/index.js
var require_has_flag = __commonJS({
  "../shared/core/node_modules/has-flag/index.js"(exports, module) {
    "use strict";
    module.exports = (flag, argv = process.argv) => {
      const prefix = flag.startsWith("-") ? "" : flag.length === 1 ? "-" : "--";
      const position = argv.indexOf(prefix + flag);
      const terminatorPosition = argv.indexOf("--");
      return position !== -1 && (terminatorPosition === -1 || position < terminatorPosition);
    };
  }
});

// ../shared/core/node_modules/supports-color/index.js
var require_supports_color = __commonJS({
  "../shared/core/node_modules/supports-color/index.js"(exports, module) {
    "use strict";
    var os4 = __require("os");
    var tty = __require("tty");
    var hasFlag = require_has_flag();
    var { env } = process;
    var forceColor;
    if (hasFlag("no-color") || hasFlag("no-colors") || hasFlag("color=false") || hasFlag("color=never")) {
      forceColor = 0;
    } else if (hasFlag("color") || hasFlag("colors") || hasFlag("color=true") || hasFlag("color=always")) {
      forceColor = 1;
    }
    if ("FORCE_COLOR" in env) {
      if (env.FORCE_COLOR === "true") {
        forceColor = 1;
      } else if (env.FORCE_COLOR === "false") {
        forceColor = 0;
      } else {
        forceColor = env.FORCE_COLOR.length === 0 ? 1 : Math.min(parseInt(env.FORCE_COLOR, 10), 3);
      }
    }
    function translateLevel(level) {
      if (level === 0) {
        return false;
      }
      return {
        level,
        hasBasic: true,
        has256: level >= 2,
        has16m: level >= 3
      };
    }
    function supportsColor(haveStream, streamIsTTY) {
      if (forceColor === 0) {
        return 0;
      }
      if (hasFlag("color=16m") || hasFlag("color=full") || hasFlag("color=truecolor")) {
        return 3;
      }
      if (hasFlag("color=256")) {
        return 2;
      }
      if (haveStream && !streamIsTTY && forceColor === void 0) {
        return 0;
      }
      const min = forceColor || 0;
      if (env.TERM === "dumb") {
        return min;
      }
      if (process.platform === "win32") {
        const osRelease = os4.release().split(".");
        if (Number(osRelease[0]) >= 10 && Number(osRelease[2]) >= 10586) {
          return Number(osRelease[2]) >= 14931 ? 3 : 2;
        }
        return 1;
      }
      if ("CI" in env) {
        if (["TRAVIS", "CIRCLECI", "APPVEYOR", "GITLAB_CI", "GITHUB_ACTIONS", "BUILDKITE"].some((sign) => sign in env) || env.CI_NAME === "codeship") {
          return 1;
        }
        return min;
      }
      if ("TEAMCITY_VERSION" in env) {
        return /^(9\.(0*[1-9]\d*)\.|\d{2,}\.)/.test(env.TEAMCITY_VERSION) ? 1 : 0;
      }
      if (env.COLORTERM === "truecolor") {
        return 3;
      }
      if ("TERM_PROGRAM" in env) {
        const version = parseInt((env.TERM_PROGRAM_VERSION || "").split(".")[0], 10);
        switch (env.TERM_PROGRAM) {
          case "iTerm.app":
            return version >= 3 ? 3 : 2;
          case "Apple_Terminal":
            return 2;
        }
      }
      if (/-256(color)?$/i.test(env.TERM)) {
        return 2;
      }
      if (/^screen|^xterm|^vt100|^vt220|^rxvt|color|ansi|cygwin|linux/i.test(env.TERM)) {
        return 1;
      }
      if ("COLORTERM" in env) {
        return 1;
      }
      return min;
    }
    function getSupportLevel(stream4) {
      const level = supportsColor(stream4, stream4 && stream4.isTTY);
      return translateLevel(level);
    }
    module.exports = {
      supportsColor: getSupportLevel,
      stdout: translateLevel(supportsColor(true, tty.isatty(1))),
      stderr: translateLevel(supportsColor(true, tty.isatty(2)))
    };
  }
});

// ../shared/core/node_modules/debug/src/node.js
var require_node = __commonJS({
  "../shared/core/node_modules/debug/src/node.js"(exports, module) {
    var tty = __require("tty");
    var util4 = __require("util");
    exports.init = init;
    exports.log = log;
    exports.formatArgs = formatArgs;
    exports.save = save;
    exports.load = load;
    exports.useColors = useColors;
    exports.destroy = util4.deprecate(
      () => {
      },
      "Instance method `debug.destroy()` is deprecated and no longer does anything. It will be removed in the next major version of `debug`."
    );
    exports.colors = [6, 2, 3, 4, 5, 1];
    try {
      const supportsColor = require_supports_color();
      if (supportsColor && (supportsColor.stderr || supportsColor).level >= 2) {
        exports.colors = [
          20,
          21,
          26,
          27,
          32,
          33,
          38,
          39,
          40,
          41,
          42,
          43,
          44,
          45,
          56,
          57,
          62,
          63,
          68,
          69,
          74,
          75,
          76,
          77,
          78,
          79,
          80,
          81,
          92,
          93,
          98,
          99,
          112,
          113,
          128,
          129,
          134,
          135,
          148,
          149,
          160,
          161,
          162,
          163,
          164,
          165,
          166,
          167,
          168,
          169,
          170,
          171,
          172,
          173,
          178,
          179,
          184,
          185,
          196,
          197,
          198,
          199,
          200,
          201,
          202,
          203,
          204,
          205,
          206,
          207,
          208,
          209,
          214,
          215,
          220,
          221
        ];
      }
    } catch (error) {
    }
    exports.inspectOpts = Object.keys(process.env).filter((key) => {
      return /^debug_/i.test(key);
    }).reduce((obj, key) => {
      const prop = key.substring(6).toLowerCase().replace(/_([a-z])/g, (_, k) => {
        return k.toUpperCase();
      });
      let val = process.env[key];
      if (/^(yes|on|true|enabled)$/i.test(val)) {
        val = true;
      } else if (/^(no|off|false|disabled)$/i.test(val)) {
        val = false;
      } else if (val === "null") {
        val = null;
      } else {
        val = Number(val);
      }
      obj[prop] = val;
      return obj;
    }, {});
    function useColors() {
      return "colors" in exports.inspectOpts ? Boolean(exports.inspectOpts.colors) : tty.isatty(process.stderr.fd);
    }
    function formatArgs(args) {
      const { namespace: name, useColors: useColors2 } = this;
      if (useColors2) {
        const c = this.color;
        const colorCode = "\x1B[3" + (c < 8 ? c : "8;5;" + c);
        const prefix = `  ${colorCode};1m${name} \x1B[0m`;
        args[0] = prefix + args[0].split("\n").join("\n" + prefix);
        args.push(colorCode + "m+" + module.exports.humanize(this.diff) + "\x1B[0m");
      } else {
        args[0] = getDate() + name + " " + args[0];
      }
    }
    function getDate() {
      if (exports.inspectOpts.hideDate) {
        return "";
      }
      return (/* @__PURE__ */ new Date()).toISOString() + " ";
    }
    function log(...args) {
      return process.stderr.write(util4.formatWithOptions(exports.inspectOpts, ...args) + "\n");
    }
    function save(namespaces) {
      if (namespaces) {
        process.env.DEBUG = namespaces;
      } else {
        delete process.env.DEBUG;
      }
    }
    function load() {
      return process.env.DEBUG;
    }
    function init(debug) {
      debug.inspectOpts = {};
      const keys = Object.keys(exports.inspectOpts);
      for (let i = 0; i < keys.length; i++) {
        debug.inspectOpts[keys[i]] = exports.inspectOpts[keys[i]];
      }
    }
    module.exports = require_common()(exports);
    var { formatters } = module.exports;
    formatters.o = function(v) {
      this.inspectOpts.colors = this.useColors;
      return util4.inspect(v, this.inspectOpts).split("\n").map((str) => str.trim()).join(" ");
    };
    formatters.O = function(v) {
      this.inspectOpts.colors = this.useColors;
      return util4.inspect(v, this.inspectOpts);
    };
  }
});

// ../shared/core/node_modules/debug/src/index.js
var require_src = __commonJS({
  "../shared/core/node_modules/debug/src/index.js"(exports, module) {
    if (typeof process === "undefined" || process.type === "renderer" || process.browser === true || process.__nwjs) {
      module.exports = require_browser();
    } else {
      module.exports = require_node();
    }
  }
});

// ../shared/core/node_modules/agent-base/dist/src/promisify.js
var require_promisify = __commonJS({
  "../shared/core/node_modules/agent-base/dist/src/promisify.js"(exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    function promisify(fn) {
      return function(req, opts) {
        return new Promise((resolve, reject) => {
          fn.call(this, req, opts, (err, rtn) => {
            if (err) {
              reject(err);
            } else {
              resolve(rtn);
            }
          });
        });
      };
    }
    exports.default = promisify;
  }
});

// ../shared/core/node_modules/agent-base/dist/src/index.js
var require_src2 = __commonJS({
  "../shared/core/node_modules/agent-base/dist/src/index.js"(exports, module) {
    "use strict";
    var __importDefault = exports && exports.__importDefault || function(mod) {
      return mod && mod.__esModule ? mod : { "default": mod };
    };
    var events_1 = __require("events");
    var debug_1 = __importDefault(require_src());
    var promisify_1 = __importDefault(require_promisify());
    var debug = debug_1.default("agent-base");
    function isAgent(v) {
      return Boolean(v) && typeof v.addRequest === "function";
    }
    function isSecureEndpoint() {
      const { stack } = new Error();
      if (typeof stack !== "string")
        return false;
      return stack.split("\n").some((l) => l.indexOf("(https.js:") !== -1 || l.indexOf("node:https:") !== -1);
    }
    function createAgent(callback, opts) {
      return new createAgent.Agent(callback, opts);
    }
    (function(createAgent2) {
      class Agent extends events_1.EventEmitter {
        constructor(callback, _opts) {
          super();
          let opts = _opts;
          if (typeof callback === "function") {
            this.callback = callback;
          } else if (callback) {
            opts = callback;
          }
          this.timeout = null;
          if (opts && typeof opts.timeout === "number") {
            this.timeout = opts.timeout;
          }
          this.maxFreeSockets = 1;
          this.maxSockets = 1;
          this.maxTotalSockets = Infinity;
          this.sockets = {};
          this.freeSockets = {};
          this.requests = {};
          this.options = {};
        }
        get defaultPort() {
          if (typeof this.explicitDefaultPort === "number") {
            return this.explicitDefaultPort;
          }
          return isSecureEndpoint() ? 443 : 80;
        }
        set defaultPort(v) {
          this.explicitDefaultPort = v;
        }
        get protocol() {
          if (typeof this.explicitProtocol === "string") {
            return this.explicitProtocol;
          }
          return isSecureEndpoint() ? "https:" : "http:";
        }
        set protocol(v) {
          this.explicitProtocol = v;
        }
        callback(req, opts, fn) {
          throw new Error('"agent-base" has no default implementation, you must subclass and override `callback()`');
        }
        /**
         * Called by node-core's "_http_client.js" module when creating
         * a new HTTP request with this Agent instance.
         *
         * @api public
         */
        addRequest(req, _opts) {
          const opts = Object.assign({}, _opts);
          if (typeof opts.secureEndpoint !== "boolean") {
            opts.secureEndpoint = isSecureEndpoint();
          }
          if (opts.host == null) {
            opts.host = "localhost";
          }
          if (opts.port == null) {
            opts.port = opts.secureEndpoint ? 443 : 80;
          }
          if (opts.protocol == null) {
            opts.protocol = opts.secureEndpoint ? "https:" : "http:";
          }
          if (opts.host && opts.path) {
            delete opts.path;
          }
          delete opts.agent;
          delete opts.hostname;
          delete opts._defaultAgent;
          delete opts.defaultPort;
          delete opts.createConnection;
          req._last = true;
          req.shouldKeepAlive = false;
          let timedOut = false;
          let timeoutId = null;
          const timeoutMs = opts.timeout || this.timeout;
          const onerror = (err) => {
            if (req._hadError)
              return;
            req.emit("error", err);
            req._hadError = true;
          };
          const ontimeout = () => {
            timeoutId = null;
            timedOut = true;
            const err = new Error(`A "socket" was not created for HTTP request before ${timeoutMs}ms`);
            err.code = "ETIMEOUT";
            onerror(err);
          };
          const callbackError = (err) => {
            if (timedOut)
              return;
            if (timeoutId !== null) {
              clearTimeout(timeoutId);
              timeoutId = null;
            }
            onerror(err);
          };
          const onsocket = (socket) => {
            if (timedOut)
              return;
            if (timeoutId != null) {
              clearTimeout(timeoutId);
              timeoutId = null;
            }
            if (isAgent(socket)) {
              debug("Callback returned another Agent instance %o", socket.constructor.name);
              socket.addRequest(req, opts);
              return;
            }
            if (socket) {
              socket.once("free", () => {
                this.freeSocket(socket, opts);
              });
              req.onSocket(socket);
              return;
            }
            const err = new Error(`no Duplex stream was returned to agent-base for \`${req.method} ${req.path}\``);
            onerror(err);
          };
          if (typeof this.callback !== "function") {
            onerror(new Error("`callback` is not defined"));
            return;
          }
          if (!this.promisifiedCallback) {
            if (this.callback.length >= 3) {
              debug("Converting legacy callback function to promise");
              this.promisifiedCallback = promisify_1.default(this.callback);
            } else {
              this.promisifiedCallback = this.callback;
            }
          }
          if (typeof timeoutMs === "number" && timeoutMs > 0) {
            timeoutId = setTimeout(ontimeout, timeoutMs);
          }
          if ("port" in opts && typeof opts.port !== "number") {
            opts.port = Number(opts.port);
          }
          try {
            debug("Resolving socket for %o request: %o", opts.protocol, `${req.method} ${req.path}`);
            Promise.resolve(this.promisifiedCallback(req, opts)).then(onsocket, callbackError);
          } catch (err) {
            Promise.reject(err).catch(callbackError);
          }
        }
        freeSocket(socket, opts) {
          debug("Freeing socket %o %o", socket.constructor.name, opts);
          socket.destroy();
        }
        destroy() {
          debug("Destroying agent %o", this.constructor.name);
        }
      }
      createAgent2.Agent = Agent;
      createAgent2.prototype = createAgent2.Agent.prototype;
    })(createAgent || (createAgent = {}));
    module.exports = createAgent;
  }
});

// ../shared/core/node_modules/https-proxy-agent/dist/parse-proxy-response.js
var require_parse_proxy_response = __commonJS({
  "../shared/core/node_modules/https-proxy-agent/dist/parse-proxy-response.js"(exports) {
    "use strict";
    var __importDefault = exports && exports.__importDefault || function(mod) {
      return mod && mod.__esModule ? mod : { "default": mod };
    };
    Object.defineProperty(exports, "__esModule", { value: true });
    var debug_1 = __importDefault(require_src());
    var debug = debug_1.default("https-proxy-agent:parse-proxy-response");
    function parseProxyResponse(socket) {
      return new Promise((resolve, reject) => {
        let buffersLength = 0;
        const buffers = [];
        function read() {
          const b = socket.read();
          if (b)
            ondata(b);
          else
            socket.once("readable", read);
        }
        function cleanup() {
          socket.removeListener("end", onend);
          socket.removeListener("error", onerror);
          socket.removeListener("close", onclose);
          socket.removeListener("readable", read);
        }
        function onclose(err) {
          debug("onclose had error %o", err);
        }
        function onend() {
          debug("onend");
        }
        function onerror(err) {
          cleanup();
          debug("onerror %o", err);
          reject(err);
        }
        function ondata(b) {
          buffers.push(b);
          buffersLength += b.length;
          const buffered = Buffer.concat(buffers, buffersLength);
          const endOfHeaders = buffered.indexOf("\r\n\r\n");
          if (endOfHeaders === -1) {
            debug("have not received end of HTTP headers yet...");
            read();
            return;
          }
          const firstLine = buffered.toString("ascii", 0, buffered.indexOf("\r\n"));
          const statusCode = +firstLine.split(" ")[1];
          debug("got proxy server response: %o", firstLine);
          resolve({
            statusCode,
            buffered
          });
        }
        socket.on("error", onerror);
        socket.on("close", onclose);
        socket.on("end", onend);
        read();
      });
    }
    exports.default = parseProxyResponse;
  }
});

// ../shared/core/node_modules/https-proxy-agent/dist/agent.js
var require_agent = __commonJS({
  "../shared/core/node_modules/https-proxy-agent/dist/agent.js"(exports) {
    "use strict";
    var __awaiter = exports && exports.__awaiter || function(thisArg, _arguments, P, generator) {
      function adopt(value) {
        return value instanceof P ? value : new P(function(resolve) {
          resolve(value);
        });
      }
      return new (P || (P = Promise))(function(resolve, reject) {
        function fulfilled(value) {
          try {
            step(generator.next(value));
          } catch (e) {
            reject(e);
          }
        }
        function rejected(value) {
          try {
            step(generator["throw"](value));
          } catch (e) {
            reject(e);
          }
        }
        function step(result) {
          result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
        }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
      });
    };
    var __importDefault = exports && exports.__importDefault || function(mod) {
      return mod && mod.__esModule ? mod : { "default": mod };
    };
    Object.defineProperty(exports, "__esModule", { value: true });
    var net_1 = __importDefault(__require("net"));
    var tls_1 = __importDefault(__require("tls"));
    var url_1 = __importDefault(__require("url"));
    var assert_1 = __importDefault(__require("assert"));
    var debug_1 = __importDefault(require_src());
    var agent_base_1 = require_src2();
    var parse_proxy_response_1 = __importDefault(require_parse_proxy_response());
    var debug = debug_1.default("https-proxy-agent:agent");
    var HttpsProxyAgent2 = class extends agent_base_1.Agent {
      constructor(_opts) {
        let opts;
        if (typeof _opts === "string") {
          opts = url_1.default.parse(_opts);
        } else {
          opts = _opts;
        }
        if (!opts) {
          throw new Error("an HTTP(S) proxy server `host` and `port` must be specified!");
        }
        debug("creating new HttpsProxyAgent instance: %o", opts);
        super(opts);
        const proxy = Object.assign({}, opts);
        this.secureProxy = opts.secureProxy || isHTTPS(proxy.protocol);
        proxy.host = proxy.hostname || proxy.host;
        if (typeof proxy.port === "string") {
          proxy.port = parseInt(proxy.port, 10);
        }
        if (!proxy.port && proxy.host) {
          proxy.port = this.secureProxy ? 443 : 80;
        }
        if (this.secureProxy && !("ALPNProtocols" in proxy)) {
          proxy.ALPNProtocols = ["http 1.1"];
        }
        if (proxy.host && proxy.path) {
          delete proxy.path;
          delete proxy.pathname;
        }
        this.proxy = proxy;
      }
      /**
       * Called when the node-core HTTP client library is creating a
       * new HTTP request.
       *
       * @api protected
       */
      callback(req, opts) {
        return __awaiter(this, void 0, void 0, function* () {
          const { proxy, secureProxy } = this;
          let socket;
          if (secureProxy) {
            debug("Creating `tls.Socket`: %o", proxy);
            socket = tls_1.default.connect(proxy);
          } else {
            debug("Creating `net.Socket`: %o", proxy);
            socket = net_1.default.connect(proxy);
          }
          const headers = Object.assign({}, proxy.headers);
          const hostname = `${opts.host}:${opts.port}`;
          let payload = `CONNECT ${hostname} HTTP/1.1\r
`;
          if (proxy.auth) {
            headers["Proxy-Authorization"] = `Basic ${Buffer.from(proxy.auth).toString("base64")}`;
          }
          let { host, port, secureEndpoint } = opts;
          if (!isDefaultPort(port, secureEndpoint)) {
            host += `:${port}`;
          }
          headers.Host = host;
          headers.Connection = "close";
          for (const name of Object.keys(headers)) {
            payload += `${name}: ${headers[name]}\r
`;
          }
          const proxyResponsePromise = parse_proxy_response_1.default(socket);
          socket.write(`${payload}\r
`);
          const { statusCode, buffered } = yield proxyResponsePromise;
          if (statusCode === 200) {
            req.once("socket", resume);
            if (opts.secureEndpoint) {
              debug("Upgrading socket connection to TLS");
              const servername = opts.servername || opts.host;
              return tls_1.default.connect(Object.assign(Object.assign({}, omit(opts, "host", "hostname", "path", "port")), {
                socket,
                servername
              }));
            }
            return socket;
          }
          socket.destroy();
          const fakeSocket = new net_1.default.Socket({ writable: false });
          fakeSocket.readable = true;
          req.once("socket", (s) => {
            debug("replaying proxy buffer for failed request");
            assert_1.default(s.listenerCount("data") > 0);
            s.push(buffered);
            s.push(null);
          });
          return fakeSocket;
        });
      }
    };
    exports.default = HttpsProxyAgent2;
    function resume(socket) {
      socket.resume();
    }
    function isDefaultPort(port, secure) {
      return Boolean(!secure && port === 80 || secure && port === 443);
    }
    function isHTTPS(protocol) {
      return typeof protocol === "string" ? /^https:?$/i.test(protocol) : false;
    }
    function omit(obj, ...keys) {
      const ret = {};
      let key;
      for (key in obj) {
        if (!keys.includes(key)) {
          ret[key] = obj[key];
        }
      }
      return ret;
    }
  }
});

// ../shared/core/node_modules/https-proxy-agent/dist/index.js
var require_dist = __commonJS({
  "../shared/core/node_modules/https-proxy-agent/dist/index.js"(exports, module) {
    "use strict";
    var __importDefault = exports && exports.__importDefault || function(mod) {
      return mod && mod.__esModule ? mod : { "default": mod };
    };
    var agent_1 = __importDefault(require_agent());
    function createHttpsProxyAgent(opts) {
      return new agent_1.default(opts);
    }
    (function(createHttpsProxyAgent2) {
      createHttpsProxyAgent2.HttpsProxyAgent = agent_1.default;
      createHttpsProxyAgent2.prototype = agent_1.default.prototype;
    })(createHttpsProxyAgent || (createHttpsProxyAgent = {}));
    module.exports = createHttpsProxyAgent;
  }
});

// ../shared/core/node_modules/follow-redirects/debug.js
var require_debug = __commonJS({
  "../shared/core/node_modules/follow-redirects/debug.js"(exports, module) {
    var debug;
    module.exports = function() {
      if (!debug) {
        try {
          debug = require_src()("follow-redirects");
        } catch (error) {
        }
        if (typeof debug !== "function") {
          debug = function() {
          };
        }
      }
      debug.apply(null, arguments);
    };
  }
});

// ../shared/core/node_modules/follow-redirects/index.js
var require_follow_redirects = __commonJS({
  "../shared/core/node_modules/follow-redirects/index.js"(exports, module) {
    var url2 = __require("url");
    var URL2 = url2.URL;
    var http3 = __require("http");
    var https2 = __require("https");
    var Writable = __require("stream").Writable;
    var assert = __require("assert");
    var debug = require_debug();
    (function detectUnsupportedEnvironment() {
      var looksLikeNode = typeof process !== "undefined";
      var looksLikeBrowser = typeof window !== "undefined" && typeof document !== "undefined";
      var looksLikeV8 = isFunction3(Error.captureStackTrace);
      if (!looksLikeNode && (looksLikeBrowser || !looksLikeV8)) {
        console.warn("The follow-redirects package should be excluded from browser builds.");
      }
    })();
    var useNativeURL = false;
    try {
      assert(new URL2(""));
    } catch (error) {
      useNativeURL = error.code === "ERR_INVALID_URL";
    }
    var sensitiveHeaders = [
      "Authorization",
      "Proxy-Authorization",
      "Cookie"
    ];
    var preservedUrlFields = [
      "auth",
      "host",
      "hostname",
      "href",
      "path",
      "pathname",
      "port",
      "protocol",
      "query",
      "search",
      "hash"
    ];
    var events = ["abort", "aborted", "connect", "error", "socket", "timeout"];
    var eventHandlers = /* @__PURE__ */ Object.create(null);
    events.forEach(function(event) {
      eventHandlers[event] = function(arg1, arg2, arg3) {
        this._redirectable.emit(event, arg1, arg2, arg3);
      };
    });
    var InvalidUrlError = createErrorType(
      "ERR_INVALID_URL",
      "Invalid URL",
      TypeError
    );
    var RedirectionError = createErrorType(
      "ERR_FR_REDIRECTION_FAILURE",
      "Redirected request failed"
    );
    var TooManyRedirectsError = createErrorType(
      "ERR_FR_TOO_MANY_REDIRECTS",
      "Maximum number of redirects exceeded",
      RedirectionError
    );
    var MaxBodyLengthExceededError = createErrorType(
      "ERR_FR_MAX_BODY_LENGTH_EXCEEDED",
      "Request body larger than maxBodyLength limit"
    );
    var WriteAfterEndError = createErrorType(
      "ERR_STREAM_WRITE_AFTER_END",
      "write after end"
    );
    var destroy = Writable.prototype.destroy || noop2;
    function RedirectableRequest(options, responseCallback) {
      Writable.call(this);
      this._sanitizeOptions(options);
      this._options = options;
      this._ended = false;
      this._ending = false;
      this._redirectCount = 0;
      this._redirects = [];
      this._requestBodyLength = 0;
      this._requestBodyBuffers = [];
      if (responseCallback) {
        this.on("response", responseCallback);
      }
      var self2 = this;
      this._onNativeResponse = function(response) {
        try {
          self2._processResponse(response);
        } catch (cause) {
          self2.emit("error", cause instanceof RedirectionError ? cause : new RedirectionError({ cause }));
        }
      };
      this._headerFilter = new RegExp("^(?:" + sensitiveHeaders.concat(options.sensitiveHeaders).map(escapeRegex).join("|") + ")$", "i");
      this._performRequest();
    }
    RedirectableRequest.prototype = Object.create(Writable.prototype);
    RedirectableRequest.prototype.abort = function() {
      destroyRequest(this._currentRequest);
      this._currentRequest.abort();
      this.emit("abort");
    };
    RedirectableRequest.prototype.destroy = function(error) {
      destroyRequest(this._currentRequest, error);
      destroy.call(this, error);
      return this;
    };
    RedirectableRequest.prototype.write = function(data, encoding, callback) {
      if (this._ending) {
        throw new WriteAfterEndError();
      }
      if (!isString2(data) && !isBuffer2(data)) {
        throw new TypeError("data should be a string, Buffer or Uint8Array");
      }
      if (isFunction3(encoding)) {
        callback = encoding;
        encoding = null;
      }
      if (data.length === 0) {
        if (callback) {
          callback();
        }
        return;
      }
      if (this._requestBodyLength + data.length <= this._options.maxBodyLength) {
        this._requestBodyLength += data.length;
        this._requestBodyBuffers.push({ data, encoding });
        this._currentRequest.write(data, encoding, callback);
      } else {
        this.emit("error", new MaxBodyLengthExceededError());
        this.abort();
      }
    };
    RedirectableRequest.prototype.end = function(data, encoding, callback) {
      if (isFunction3(data)) {
        callback = data;
        data = encoding = null;
      } else if (isFunction3(encoding)) {
        callback = encoding;
        encoding = null;
      }
      if (!data) {
        this._ended = this._ending = true;
        this._currentRequest.end(null, null, callback);
      } else {
        var self2 = this;
        var currentRequest = this._currentRequest;
        this.write(data, encoding, function() {
          self2._ended = true;
          currentRequest.end(null, null, callback);
        });
        this._ending = true;
      }
    };
    RedirectableRequest.prototype.setHeader = function(name, value) {
      this._options.headers[name] = value;
      this._currentRequest.setHeader(name, value);
    };
    RedirectableRequest.prototype.removeHeader = function(name) {
      delete this._options.headers[name];
      this._currentRequest.removeHeader(name);
    };
    RedirectableRequest.prototype.setTimeout = function(msecs, callback) {
      var self2 = this;
      function destroyOnTimeout(socket) {
        socket.setTimeout(msecs);
        socket.removeListener("timeout", socket.destroy);
        socket.addListener("timeout", socket.destroy);
      }
      function startTimer(socket) {
        if (self2._timeout) {
          clearTimeout(self2._timeout);
        }
        self2._timeout = setTimeout(function() {
          self2.emit("timeout");
          clearTimer();
        }, msecs);
        destroyOnTimeout(socket);
      }
      function clearTimer() {
        if (self2._timeout) {
          clearTimeout(self2._timeout);
          self2._timeout = null;
        }
        self2.removeListener("abort", clearTimer);
        self2.removeListener("error", clearTimer);
        self2.removeListener("response", clearTimer);
        self2.removeListener("close", clearTimer);
        if (callback) {
          self2.removeListener("timeout", callback);
        }
        if (!self2.socket) {
          self2._currentRequest.removeListener("socket", startTimer);
        }
      }
      if (callback) {
        this.on("timeout", callback);
      }
      if (this.socket) {
        startTimer(this.socket);
      } else {
        this._currentRequest.once("socket", startTimer);
      }
      this.on("socket", destroyOnTimeout);
      this.on("abort", clearTimer);
      this.on("error", clearTimer);
      this.on("response", clearTimer);
      this.on("close", clearTimer);
      return this;
    };
    [
      "flushHeaders",
      "getHeader",
      "setNoDelay",
      "setSocketKeepAlive"
    ].forEach(function(method) {
      RedirectableRequest.prototype[method] = function(a, b) {
        return this._currentRequest[method](a, b);
      };
    });
    ["aborted", "connection", "socket"].forEach(function(property) {
      Object.defineProperty(RedirectableRequest.prototype, property, {
        get: function() {
          return this._currentRequest[property];
        }
      });
    });
    RedirectableRequest.prototype._sanitizeOptions = function(options) {
      if (!options.headers) {
        options.headers = {};
      }
      if (!isArray2(options.sensitiveHeaders)) {
        options.sensitiveHeaders = [];
      }
      if (options.host) {
        if (!options.hostname) {
          options.hostname = options.host;
        }
        delete options.host;
      }
      if (!options.pathname && options.path) {
        var searchPos = options.path.indexOf("?");
        if (searchPos < 0) {
          options.pathname = options.path;
        } else {
          options.pathname = options.path.substring(0, searchPos);
          options.search = options.path.substring(searchPos);
        }
      }
    };
    RedirectableRequest.prototype._performRequest = function() {
      var protocol = this._options.protocol;
      var nativeProtocol = this._options.nativeProtocols[protocol];
      if (!nativeProtocol) {
        throw new TypeError("Unsupported protocol " + protocol);
      }
      if (this._options.agents) {
        var scheme = protocol.slice(0, -1);
        this._options.agent = this._options.agents[scheme];
      }
      var request = this._currentRequest = nativeProtocol.request(this._options, this._onNativeResponse);
      request._redirectable = this;
      for (var event of events) {
        request.on(event, eventHandlers[event]);
      }
      this._currentUrl = /^\//.test(this._options.path) ? url2.format(this._options) : (
        // When making a request to a proxy, […]
        // a client MUST send the target URI in absolute-form […].
        this._options.path
      );
      if (this._isRedirect) {
        var i = 0;
        var self2 = this;
        var buffers = this._requestBodyBuffers;
        (function writeNext(error) {
          if (request === self2._currentRequest) {
            if (error) {
              self2.emit("error", error);
            } else if (i < buffers.length) {
              var buffer = buffers[i++];
              if (!request.finished) {
                request.write(buffer.data, buffer.encoding, writeNext);
              }
            } else if (self2._ended) {
              request.end();
            }
          }
        })();
      }
    };
    RedirectableRequest.prototype._processResponse = function(response) {
      var statusCode = response.statusCode;
      if (this._options.trackRedirects) {
        this._redirects.push({
          url: this._currentUrl,
          headers: response.headers,
          statusCode
        });
      }
      var location = response.headers.location;
      if (!location || this._options.followRedirects === false || statusCode < 300 || statusCode >= 400) {
        response.responseUrl = this._currentUrl;
        response.redirects = this._redirects;
        this.emit("response", response);
        this._requestBodyBuffers = [];
        return;
      }
      destroyRequest(this._currentRequest);
      response.destroy();
      if (++this._redirectCount > this._options.maxRedirects) {
        throw new TooManyRedirectsError();
      }
      var requestHeaders;
      var beforeRedirect = this._options.beforeRedirect;
      if (beforeRedirect) {
        requestHeaders = Object.assign({
          // The Host header was set by nativeProtocol.request
          Host: response.req.getHeader("host")
        }, this._options.headers);
      }
      var method = this._options.method;
      if ((statusCode === 301 || statusCode === 302) && this._options.method === "POST" || // RFC7231§6.4.4: The 303 (See Other) status code indicates that
      // the server is redirecting the user agent to a different resource […]
      // A user agent can perform a retrieval request targeting that URI
      // (a GET or HEAD request if using HTTP) […]
      statusCode === 303 && !/^(?:GET|HEAD)$/.test(this._options.method)) {
        this._options.method = "GET";
        this._requestBodyBuffers = [];
        removeMatchingHeaders(/^content-/i, this._options.headers);
      }
      var currentHostHeader = removeMatchingHeaders(/^host$/i, this._options.headers);
      var currentUrlParts = parseUrl2(this._currentUrl);
      var currentHost = currentHostHeader || currentUrlParts.host;
      var currentUrl = /^\w+:/.test(location) ? this._currentUrl : url2.format(Object.assign(currentUrlParts, { host: currentHost }));
      var redirectUrl = resolveUrl(location, currentUrl);
      debug("redirecting to", redirectUrl.href);
      this._isRedirect = true;
      spreadUrlObject(redirectUrl, this._options);
      if (redirectUrl.protocol !== currentUrlParts.protocol && redirectUrl.protocol !== "https:" || redirectUrl.host !== currentHost && !isSubdomain(redirectUrl.host, currentHost)) {
        removeMatchingHeaders(this._headerFilter, this._options.headers);
      }
      if (isFunction3(beforeRedirect)) {
        var responseDetails = {
          headers: response.headers,
          statusCode
        };
        var requestDetails = {
          url: currentUrl,
          method,
          headers: requestHeaders
        };
        beforeRedirect(this._options, responseDetails, requestDetails);
        this._sanitizeOptions(this._options);
      }
      this._performRequest();
    };
    function wrap(protocols) {
      var exports2 = {
        maxRedirects: 21,
        maxBodyLength: 10 * 1024 * 1024
      };
      var nativeProtocols = {};
      Object.keys(protocols).forEach(function(scheme) {
        var protocol = scheme + ":";
        var nativeProtocol = nativeProtocols[protocol] = protocols[scheme];
        var wrappedProtocol = exports2[scheme] = Object.create(nativeProtocol);
        function request(input, options, callback) {
          if (isURL(input)) {
            input = spreadUrlObject(input);
          } else if (isString2(input)) {
            input = spreadUrlObject(parseUrl2(input));
          } else {
            callback = options;
            options = validateUrl(input);
            input = { protocol };
          }
          if (isFunction3(options)) {
            callback = options;
            options = null;
          }
          options = Object.assign({
            maxRedirects: exports2.maxRedirects,
            maxBodyLength: exports2.maxBodyLength
          }, input, options);
          options.nativeProtocols = nativeProtocols;
          if (!isString2(options.host) && !isString2(options.hostname)) {
            options.hostname = "::1";
          }
          assert.equal(options.protocol, protocol, "protocol mismatch");
          debug("options", options);
          return new RedirectableRequest(options, callback);
        }
        function get(input, options, callback) {
          var wrappedRequest = wrappedProtocol.request(input, options, callback);
          wrappedRequest.end();
          return wrappedRequest;
        }
        Object.defineProperties(wrappedProtocol, {
          request: { value: request, configurable: true, enumerable: true, writable: true },
          get: { value: get, configurable: true, enumerable: true, writable: true }
        });
      });
      return exports2;
    }
    function noop2() {
    }
    function parseUrl2(input) {
      var parsed;
      if (useNativeURL) {
        parsed = new URL2(input);
      } else {
        parsed = validateUrl(url2.parse(input));
        if (!isString2(parsed.protocol)) {
          throw new InvalidUrlError({ input });
        }
      }
      return parsed;
    }
    function resolveUrl(relative, base) {
      return useNativeURL ? new URL2(relative, base) : parseUrl2(url2.resolve(base, relative));
    }
    function validateUrl(input) {
      if (/^\[/.test(input.hostname) && !/^\[[:0-9a-f]+\]$/i.test(input.hostname)) {
        throw new InvalidUrlError({ input: input.href || input });
      }
      if (/^\[/.test(input.host) && !/^\[[:0-9a-f]+\](:\d+)?$/i.test(input.host)) {
        throw new InvalidUrlError({ input: input.href || input });
      }
      return input;
    }
    function spreadUrlObject(urlObject, target) {
      var spread3 = target || {};
      for (var key of preservedUrlFields) {
        spread3[key] = urlObject[key];
      }
      if (spread3.hostname.startsWith("[")) {
        spread3.hostname = spread3.hostname.slice(1, -1);
      }
      if (spread3.port !== "") {
        spread3.port = Number(spread3.port);
      }
      spread3.path = spread3.search ? spread3.pathname + spread3.search : spread3.pathname;
      return spread3;
    }
    function removeMatchingHeaders(regex, headers) {
      var lastValue;
      for (var header in headers) {
        if (regex.test(header)) {
          lastValue = headers[header];
          delete headers[header];
        }
      }
      return lastValue === null || typeof lastValue === "undefined" ? void 0 : String(lastValue).trim();
    }
    function createErrorType(code, message, baseClass) {
      function CustomError(properties) {
        if (isFunction3(Error.captureStackTrace)) {
          Error.captureStackTrace(this, this.constructor);
        }
        Object.assign(this, properties || {});
        this.code = code;
        this.message = this.cause ? message + ": " + this.cause.message : message;
      }
      CustomError.prototype = new (baseClass || Error)();
      Object.defineProperties(CustomError.prototype, {
        constructor: {
          value: CustomError,
          enumerable: false
        },
        name: {
          value: "Error [" + code + "]",
          enumerable: false
        }
      });
      return CustomError;
    }
    function destroyRequest(request, error) {
      for (var event of events) {
        request.removeListener(event, eventHandlers[event]);
      }
      request.on("error", noop2);
      request.destroy(error);
    }
    function isSubdomain(subdomain, domain) {
      assert(isString2(subdomain) && isString2(domain));
      var dot = subdomain.length - domain.length - 1;
      return dot > 0 && subdomain[dot] === "." && subdomain.endsWith(domain);
    }
    function isArray2(value) {
      return value instanceof Array;
    }
    function isString2(value) {
      return typeof value === "string" || value instanceof String;
    }
    function isFunction3(value) {
      return typeof value === "function";
    }
    function isBuffer2(value) {
      return typeof value === "object" && "length" in value;
    }
    function isURL(value) {
      return URL2 && value instanceof URL2;
    }
    function escapeRegex(regex) {
      return regex.replace(/[\]\\/()*+?.$]/g, "\\$&");
    }
    module.exports = wrap({ http: http3, https: https2 });
    module.exports.wrap = wrap;
  }
});

// ../shared/core/src/auth/tokenStore.ts
import { promises as fs } from "fs";
import path from "path";
var EMPTY = { deployments: {}, activeDeployment: null };
var TokenStore = class {
  constructor(filePath) {
    this.filePath = filePath;
  }
  async read() {
    try {
      const buf = await fs.readFile(this.filePath, "utf8");
      const parsed = JSON.parse(buf);
      if (!parsed || typeof parsed !== "object") return { ...EMPTY };
      const deployments = parsed.deployments && typeof parsed.deployments === "object" && !Array.isArray(parsed.deployments) ? parsed.deployments : {};
      const activeDeployment = typeof parsed.activeDeployment === "string" ? parsed.activeDeployment : null;
      return { deployments, activeDeployment };
    } catch (err) {
      if (err.code === "ENOENT") return { ...EMPTY };
      throw err;
    }
  }
  async writeAtomic(data) {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const tmp = `${this.filePath}.tmp.${process.pid}`;
    await fs.writeFile(tmp, JSON.stringify(data, null, 2), { mode: 384 });
    await fs.rename(tmp, this.filePath);
    await fs.chmod(this.filePath, 384);
  }
  async save(deployment, creds) {
    const state = await this.read();
    state.deployments[deployment] = creds;
    state.activeDeployment = deployment;
    await this.writeAtomic(state);
  }
  async get(deployment) {
    const state = await this.read();
    return state.deployments[deployment] ?? null;
  }
  async getActive() {
    const state = await this.read();
    if (!state.activeDeployment) return null;
    const creds = state.deployments[state.activeDeployment];
    if (!creds) return null;
    return { deployment: state.activeDeployment, creds };
  }
  async setActive(deployment) {
    const state = await this.read();
    if (!state.deployments[deployment]) {
      throw new Error(`No credentials stored for deployment: ${deployment}`);
    }
    state.activeDeployment = deployment;
    await this.writeAtomic(state);
  }
  async delete(deployment) {
    const state = await this.read();
    delete state.deployments[deployment];
    if (state.activeDeployment === deployment) {
      const remaining = Object.keys(state.deployments);
      state.activeDeployment = remaining[0] ?? null;
    }
    await this.writeAtomic(state);
  }
};
function defaultCredentialsPath(pluginName) {
  const home = process.env.HOME ?? process.env.USERPROFILE ?? ".";
  return path.join(home, ".claude", "stylebi", `credentials-${pluginName}.json`);
}

// ../shared/core/src/auth/sessionManager.ts
import crypto from "crypto";
var DEFAULT_TTL_MS = 5 * 60 * 1e3;
var SessionManager = class {
  entries = /* @__PURE__ */ new Map();
  ttlMs;
  constructor(opts = {}) {
    this.ttlMs = opts.ttlMs ?? DEFAULT_TTL_MS;
  }
  store(nonce) {
    const id = crypto.randomBytes(12).toString("hex");
    const timer = setTimeout(() => {
      this.entries.delete(id);
    }, this.ttlMs);
    timer.unref?.();
    this.entries.set(id, { nonce, timer });
    return id;
  }
  get(sessionId) {
    return this.entries.get(sessionId)?.nonce ?? null;
  }
  getAndConsume(sessionId) {
    const entry = this.entries.get(sessionId);
    if (!entry) return null;
    clearTimeout(entry.timer);
    this.entries.delete(sessionId);
    return { nonce: entry.nonce };
  }
  shutdown() {
    for (const { timer } of this.entries.values()) {
      clearTimeout(timer);
    }
    this.entries.clear();
  }
};

// ../shared/core/node_modules/axios/lib/helpers/bind.js
function bind(fn, thisArg) {
  return function wrap() {
    return fn.apply(thisArg, arguments);
  };
}

// ../shared/core/node_modules/axios/lib/utils.js
var { toString } = Object.prototype;
var { getPrototypeOf } = Object;
var { iterator, toStringTag } = Symbol;
var hasOwnProperty = (({ hasOwnProperty: hasOwnProperty2 }) => (obj, prop) => hasOwnProperty2.call(obj, prop))(Object.prototype);
var hasOwnInPrototypeChain = (thing, prop) => {
  let obj = thing;
  const seen = [];
  while (obj != null && obj !== Object.prototype) {
    if (seen.indexOf(obj) !== -1) {
      return false;
    }
    seen.push(obj);
    if (hasOwnProperty(obj, prop)) {
      return true;
    }
    obj = getPrototypeOf(obj);
  }
  return false;
};
var getSafeProp = (obj, prop) => obj != null && hasOwnInPrototypeChain(obj, prop) ? obj[prop] : void 0;
var kindOf = /* @__PURE__ */ ((cache) => (thing) => {
  const str = toString.call(thing);
  return cache[str] || (cache[str] = str.slice(8, -1).toLowerCase());
})(/* @__PURE__ */ Object.create(null));
var kindOfTest = (type) => {
  type = type.toLowerCase();
  return (thing) => kindOf(thing) === type;
};
var typeOfTest = (type) => (thing) => typeof thing === type;
var { isArray } = Array;
var isUndefined = typeOfTest("undefined");
function isBuffer(val) {
  return val !== null && !isUndefined(val) && val.constructor !== null && !isUndefined(val.constructor) && isFunction(val.constructor.isBuffer) && val.constructor.isBuffer(val);
}
var isArrayBuffer = kindOfTest("ArrayBuffer");
function isArrayBufferView(val) {
  let result;
  if (typeof ArrayBuffer !== "undefined" && ArrayBuffer.isView) {
    result = ArrayBuffer.isView(val);
  } else {
    result = val && val.buffer && isArrayBuffer(val.buffer);
  }
  return result;
}
var isString = typeOfTest("string");
var isFunction = typeOfTest("function");
var isNumber = typeOfTest("number");
var isObject = (thing) => thing !== null && typeof thing === "object";
var isBoolean = (thing) => thing === true || thing === false;
var isPlainObject = (val) => {
  if (!isObject(val)) {
    return false;
  }
  const prototype2 = getPrototypeOf(val);
  return (prototype2 === null || prototype2 === Object.prototype || getPrototypeOf(prototype2) === null) && // Treat any genuine (non-Object.prototype-polluted) Symbol.toStringTag or
  // Symbol.iterator as evidence the value is a tagged/iterable type rather
  // than a plain object, while ignoring keys injected onto Object.prototype.
  !hasOwnInPrototypeChain(val, toStringTag) && !hasOwnInPrototypeChain(val, iterator);
};
var isEmptyObject = (val) => {
  if (!isObject(val) || isBuffer(val)) {
    return false;
  }
  try {
    return Object.keys(val).length === 0 && Object.getPrototypeOf(val) === Object.prototype;
  } catch (e) {
    return false;
  }
};
var isDate = kindOfTest("Date");
var isFile = kindOfTest("File");
var isReactNativeBlob = (value) => {
  return !!(value && typeof value.uri !== "undefined");
};
var isReactNative = (formData) => formData && typeof formData.getParts !== "undefined";
var isBlob = kindOfTest("Blob");
var isFileList = kindOfTest("FileList");
var isStream = (val) => isObject(val) && isFunction(val.pipe);
function getGlobal() {
  if (typeof globalThis !== "undefined") return globalThis;
  if (typeof self !== "undefined") return self;
  if (typeof window !== "undefined") return window;
  if (typeof global !== "undefined") return global;
  return {};
}
var G = getGlobal();
var FormDataCtor = typeof G.FormData !== "undefined" ? G.FormData : void 0;
var isFormData = (thing) => {
  if (!thing) return false;
  if (FormDataCtor && thing instanceof FormDataCtor) return true;
  const proto = getPrototypeOf(thing);
  if (!proto || proto === Object.prototype) return false;
  if (!isFunction(thing.append)) return false;
  const kind = kindOf(thing);
  return kind === "formdata" || // detect form-data instance
  kind === "object" && isFunction(thing.toString) && thing.toString() === "[object FormData]";
};
var isURLSearchParams = kindOfTest("URLSearchParams");
var [isReadableStream, isRequest, isResponse, isHeaders] = [
  "ReadableStream",
  "Request",
  "Response",
  "Headers"
].map(kindOfTest);
var trim = (str) => {
  return str.trim ? str.trim() : str.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, "");
};
function forEach(obj, fn, { allOwnKeys = false } = {}) {
  if (obj === null || typeof obj === "undefined") {
    return;
  }
  let i;
  let l;
  if (typeof obj !== "object") {
    obj = [obj];
  }
  if (isArray(obj)) {
    for (i = 0, l = obj.length; i < l; i++) {
      fn.call(null, obj[i], i, obj);
    }
  } else {
    if (isBuffer(obj)) {
      return;
    }
    const keys = allOwnKeys ? Object.getOwnPropertyNames(obj) : Object.keys(obj);
    const len = keys.length;
    let key;
    for (i = 0; i < len; i++) {
      key = keys[i];
      fn.call(null, obj[key], key, obj);
    }
  }
}
function findKey(obj, key) {
  if (isBuffer(obj)) {
    return null;
  }
  key = key.toLowerCase();
  const keys = Object.keys(obj);
  let i = keys.length;
  let _key;
  while (i-- > 0) {
    _key = keys[i];
    if (key === _key.toLowerCase()) {
      return _key;
    }
  }
  return null;
}
var _global = (() => {
  if (typeof globalThis !== "undefined") return globalThis;
  return typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : global;
})();
var isContextDefined = (context) => !isUndefined(context) && context !== _global;
function merge(...objs) {
  const { caseless, skipUndefined } = isContextDefined(this) && this || {};
  const result = {};
  const assignValue = (val, key) => {
    if (key === "__proto__" || key === "constructor" || key === "prototype") {
      return;
    }
    const targetKey = caseless && typeof key === "string" && findKey(result, key) || key;
    const existing = hasOwnProperty(result, targetKey) ? result[targetKey] : void 0;
    if (isPlainObject(existing) && isPlainObject(val)) {
      result[targetKey] = merge(existing, val);
    } else if (isPlainObject(val)) {
      result[targetKey] = merge({}, val);
    } else if (isArray(val)) {
      result[targetKey] = val.slice();
    } else if (!skipUndefined || !isUndefined(val)) {
      result[targetKey] = val;
    }
  };
  for (let i = 0, l = objs.length; i < l; i++) {
    const source = objs[i];
    if (!source || isBuffer(source)) {
      continue;
    }
    forEach(source, assignValue);
    if (typeof source !== "object" || isArray(source)) {
      continue;
    }
    const symbols = Object.getOwnPropertySymbols(source);
    for (let j = 0; j < symbols.length; j++) {
      const symbol = symbols[j];
      if (propertyIsEnumerable.call(source, symbol)) {
        assignValue(source[symbol], symbol);
      }
    }
  }
  return result;
}
var extend = (a, b, thisArg, { allOwnKeys } = {}) => {
  forEach(
    b,
    (val, key) => {
      if (thisArg && isFunction(val)) {
        Object.defineProperty(a, key, {
          // Null-proto descriptor so a polluted Object.prototype.get cannot
          // hijack defineProperty's accessor-vs-data resolution.
          __proto__: null,
          value: bind(val, thisArg),
          writable: true,
          enumerable: true,
          configurable: true
        });
      } else {
        Object.defineProperty(a, key, {
          __proto__: null,
          value: val,
          writable: true,
          enumerable: true,
          configurable: true
        });
      }
    },
    { allOwnKeys }
  );
  return a;
};
var stripBOM = (content) => {
  if (content.charCodeAt(0) === 65279) {
    content = content.slice(1);
  }
  return content;
};
var inherits = (constructor, superConstructor, props, descriptors) => {
  constructor.prototype = Object.create(superConstructor.prototype, descriptors);
  Object.defineProperty(constructor.prototype, "constructor", {
    __proto__: null,
    value: constructor,
    writable: true,
    enumerable: false,
    configurable: true
  });
  Object.defineProperty(constructor, "super", {
    __proto__: null,
    value: superConstructor.prototype
  });
  props && Object.assign(constructor.prototype, props);
};
var toFlatObject = (sourceObj, destObj, filter2, propFilter) => {
  let props;
  let i;
  let prop;
  const merged = {};
  destObj = destObj || {};
  if (sourceObj == null) return destObj;
  do {
    props = Object.getOwnPropertyNames(sourceObj);
    i = props.length;
    while (i-- > 0) {
      prop = props[i];
      if ((!propFilter || propFilter(prop, sourceObj, destObj)) && !merged[prop]) {
        destObj[prop] = sourceObj[prop];
        merged[prop] = true;
      }
    }
    sourceObj = filter2 !== false && getPrototypeOf(sourceObj);
  } while (sourceObj && (!filter2 || filter2(sourceObj, destObj)) && sourceObj !== Object.prototype);
  return destObj;
};
var endsWith = (str, searchString, position) => {
  str = String(str);
  if (position === void 0 || position > str.length) {
    position = str.length;
  }
  position -= searchString.length;
  const lastIndex = str.indexOf(searchString, position);
  return lastIndex !== -1 && lastIndex === position;
};
var toArray = (thing) => {
  if (!thing) return null;
  if (isArray(thing)) return thing;
  let i = thing.length;
  if (!isNumber(i)) return null;
  const arr = new Array(i);
  while (i-- > 0) {
    arr[i] = thing[i];
  }
  return arr;
};
var isTypedArray = /* @__PURE__ */ ((TypedArray) => {
  return (thing) => {
    return TypedArray && thing instanceof TypedArray;
  };
})(typeof Uint8Array !== "undefined" && getPrototypeOf(Uint8Array));
var forEachEntry = (obj, fn) => {
  const generator = obj && obj[iterator];
  const _iterator = generator.call(obj);
  let result;
  while ((result = _iterator.next()) && !result.done) {
    const pair = result.value;
    fn.call(obj, pair[0], pair[1]);
  }
};
var matchAll = (regExp, str) => {
  let matches;
  const arr = [];
  while ((matches = regExp.exec(str)) !== null) {
    arr.push(matches);
  }
  return arr;
};
var isHTMLForm = kindOfTest("HTMLFormElement");
var toCamelCase = (str) => {
  return str.toLowerCase().replace(/[-_\s]([a-z\d])(\w*)/g, function replacer(m, p1, p2) {
    return p1.toUpperCase() + p2;
  });
};
var { propertyIsEnumerable } = Object.prototype;
var isRegExp = kindOfTest("RegExp");
var reduceDescriptors = (obj, reducer) => {
  const descriptors = Object.getOwnPropertyDescriptors(obj);
  const reducedDescriptors = {};
  forEach(descriptors, (descriptor, name) => {
    let ret;
    if ((ret = reducer(descriptor, name, obj)) !== false) {
      reducedDescriptors[name] = ret || descriptor;
    }
  });
  Object.defineProperties(obj, reducedDescriptors);
};
var freezeMethods = (obj) => {
  reduceDescriptors(obj, (descriptor, name) => {
    if (isFunction(obj) && ["arguments", "caller", "callee"].includes(name)) {
      return false;
    }
    const value = obj[name];
    if (!isFunction(value)) return;
    descriptor.enumerable = false;
    if ("writable" in descriptor) {
      descriptor.writable = false;
      return;
    }
    if (!descriptor.set) {
      descriptor.set = () => {
        throw Error("Can not rewrite read-only method '" + name + "'");
      };
    }
  });
};
var toObjectSet = (arrayOrString, delimiter) => {
  const obj = {};
  const define = (arr) => {
    arr.forEach((value) => {
      obj[value] = true;
    });
  };
  isArray(arrayOrString) ? define(arrayOrString) : define(String(arrayOrString).split(delimiter));
  return obj;
};
var noop = () => {
};
var toFiniteNumber = (value, defaultValue) => {
  return value != null && Number.isFinite(value = +value) ? value : defaultValue;
};
function isSpecCompliantForm(thing) {
  return !!(thing && isFunction(thing.append) && thing[toStringTag] === "FormData" && thing[iterator]);
}
var toJSONObject = (obj) => {
  const visited = /* @__PURE__ */ new WeakSet();
  const visit = (source) => {
    if (isObject(source)) {
      if (visited.has(source)) {
        return;
      }
      if (isBuffer(source)) {
        return source;
      }
      if (!("toJSON" in source)) {
        visited.add(source);
        const target = isArray(source) ? [] : {};
        forEach(source, (value, key) => {
          const reducedValue = visit(value);
          !isUndefined(reducedValue) && (target[key] = reducedValue);
        });
        visited.delete(source);
        return target;
      }
    }
    return source;
  };
  return visit(obj);
};
var isAsyncFn = kindOfTest("AsyncFunction");
var isThenable = (thing) => thing && (isObject(thing) || isFunction(thing)) && isFunction(thing.then) && isFunction(thing.catch);
var _setImmediate = ((setImmediateSupported, postMessageSupported) => {
  if (setImmediateSupported) {
    return setImmediate;
  }
  return postMessageSupported ? ((token, callbacks) => {
    _global.addEventListener(
      "message",
      ({ source, data }) => {
        if (source === _global && data === token) {
          callbacks.length && callbacks.shift()();
        }
      },
      false
    );
    return (cb) => {
      callbacks.push(cb);
      _global.postMessage(token, "*");
    };
  })(`axios@${Math.random()}`, []) : (cb) => setTimeout(cb);
})(typeof setImmediate === "function", isFunction(_global.postMessage));
var asap = typeof queueMicrotask !== "undefined" ? queueMicrotask.bind(_global) : typeof process !== "undefined" && process.nextTick || _setImmediate;
var isIterable = (thing) => thing != null && isFunction(thing[iterator]);
var isSafeIterable = (thing) => thing != null && hasOwnInPrototypeChain(thing, iterator) && isIterable(thing);
var utils_default = {
  isArray,
  isArrayBuffer,
  isBuffer,
  isFormData,
  isArrayBufferView,
  isString,
  isNumber,
  isBoolean,
  isObject,
  isPlainObject,
  isEmptyObject,
  isReadableStream,
  isRequest,
  isResponse,
  isHeaders,
  isUndefined,
  isDate,
  isFile,
  isReactNativeBlob,
  isReactNative,
  isBlob,
  isRegExp,
  isFunction,
  isStream,
  isURLSearchParams,
  isTypedArray,
  isFileList,
  forEach,
  merge,
  extend,
  trim,
  stripBOM,
  inherits,
  toFlatObject,
  kindOf,
  kindOfTest,
  endsWith,
  toArray,
  forEachEntry,
  matchAll,
  isHTMLForm,
  hasOwnProperty,
  hasOwnProp: hasOwnProperty,
  // an alias to avoid ESLint no-prototype-builtins detection
  hasOwnInPrototypeChain,
  getSafeProp,
  reduceDescriptors,
  freezeMethods,
  toObjectSet,
  toCamelCase,
  noop,
  toFiniteNumber,
  findKey,
  global: _global,
  isContextDefined,
  isSpecCompliantForm,
  toJSONObject,
  isAsyncFn,
  isThenable,
  setImmediate: _setImmediate,
  asap,
  isIterable,
  isSafeIterable
};

// ../shared/core/node_modules/axios/lib/helpers/parseHeaders.js
var ignoreDuplicateOf = utils_default.toObjectSet([
  "age",
  "authorization",
  "content-length",
  "content-type",
  "etag",
  "expires",
  "from",
  "host",
  "if-modified-since",
  "if-unmodified-since",
  "last-modified",
  "location",
  "max-forwards",
  "proxy-authorization",
  "referer",
  "retry-after",
  "user-agent"
]);
var parseHeaders_default = (rawHeaders) => {
  const parsed = {};
  let key;
  let val;
  let i;
  rawHeaders && rawHeaders.split("\n").forEach(function parser(line) {
    i = line.indexOf(":");
    key = line.substring(0, i).trim().toLowerCase();
    val = line.substring(i + 1).trim();
    if (!key || parsed[key] && ignoreDuplicateOf[key]) {
      return;
    }
    if (key === "set-cookie") {
      if (parsed[key]) {
        parsed[key].push(val);
      } else {
        parsed[key] = [val];
      }
    } else {
      parsed[key] = parsed[key] ? parsed[key] + ", " + val : val;
    }
  });
  return parsed;
};

// ../shared/core/node_modules/axios/lib/helpers/sanitizeHeaderValue.js
function trimSPorHTAB(str) {
  let start = 0;
  let end = str.length;
  while (start < end) {
    const code = str.charCodeAt(start);
    if (code !== 9 && code !== 32) {
      break;
    }
    start += 1;
  }
  while (end > start) {
    const code = str.charCodeAt(end - 1);
    if (code !== 9 && code !== 32) {
      break;
    }
    end -= 1;
  }
  return start === 0 && end === str.length ? str : str.slice(start, end);
}
var INVALID_UNICODE_HEADER_VALUE_CHARS = new RegExp("[\\u0000-\\u0008\\u000a-\\u001f\\u007f]+", "g");
var INVALID_BYTE_STRING_HEADER_VALUE_CHARS = new RegExp("[^\\u0009\\u0020-\\u007e\\u0080-\\u00ff]+", "g");
function sanitizeValue(value, invalidChars) {
  if (utils_default.isArray(value)) {
    return value.map((item) => sanitizeValue(item, invalidChars));
  }
  return trimSPorHTAB(String(value).replace(invalidChars, ""));
}
var sanitizeHeaderValue = (value) => sanitizeValue(value, INVALID_UNICODE_HEADER_VALUE_CHARS);
var sanitizeByteStringHeaderValue = (value) => sanitizeValue(value, INVALID_BYTE_STRING_HEADER_VALUE_CHARS);
function toByteStringHeaderObject(headers) {
  const byteStringHeaders = /* @__PURE__ */ Object.create(null);
  utils_default.forEach(headers.toJSON(), (value, header) => {
    byteStringHeaders[header] = sanitizeByteStringHeaderValue(value);
  });
  return byteStringHeaders;
}

// ../shared/core/node_modules/axios/lib/core/AxiosHeaders.js
var $internals = Symbol("internals");
function normalizeHeader(header) {
  return header && String(header).trim().toLowerCase();
}
function normalizeValue(value) {
  if (value === false || value == null) {
    return value;
  }
  return utils_default.isArray(value) ? value.map(normalizeValue) : sanitizeHeaderValue(String(value));
}
function parseTokens(str) {
  const tokens = /* @__PURE__ */ Object.create(null);
  const tokensRE = /([^\s,;=]+)\s*(?:=\s*([^,;]+))?/g;
  let match;
  while (match = tokensRE.exec(str)) {
    tokens[match[1]] = match[2];
  }
  return tokens;
}
var isValidHeaderName = (str) => /^[-_a-zA-Z0-9^`|~,!#$%&'*+.]+$/.test(str.trim());
function matchHeaderValue(context, value, header, filter2, isHeaderNameFilter) {
  if (utils_default.isFunction(filter2)) {
    return filter2.call(this, value, header);
  }
  if (isHeaderNameFilter) {
    value = header;
  }
  if (!utils_default.isString(value)) return;
  if (utils_default.isString(filter2)) {
    return value.indexOf(filter2) !== -1;
  }
  if (utils_default.isRegExp(filter2)) {
    return filter2.test(value);
  }
}
function formatHeader(header) {
  return header.trim().toLowerCase().replace(/([a-z\d])(\w*)/g, (w, char, str) => {
    return char.toUpperCase() + str;
  });
}
function buildAccessors(obj, header) {
  const accessorName = utils_default.toCamelCase(" " + header);
  ["get", "set", "has"].forEach((methodName) => {
    Object.defineProperty(obj, methodName + accessorName, {
      // Null-proto descriptor so a polluted Object.prototype.get cannot turn
      // this data descriptor into an accessor descriptor on the way in.
      __proto__: null,
      value: function(arg1, arg2, arg3) {
        return this[methodName].call(this, header, arg1, arg2, arg3);
      },
      configurable: true
    });
  });
}
var AxiosHeaders = class {
  constructor(headers) {
    headers && this.set(headers);
  }
  set(header, valueOrRewrite, rewrite) {
    const self2 = this;
    function setHeader(_value, _header, _rewrite) {
      const lHeader = normalizeHeader(_header);
      if (!lHeader) {
        return;
      }
      const key = utils_default.findKey(self2, lHeader);
      if (!key || self2[key] === void 0 || _rewrite === true || _rewrite === void 0 && self2[key] !== false) {
        self2[key || _header] = normalizeValue(_value);
      }
    }
    const setHeaders = (headers, _rewrite) => utils_default.forEach(headers, (_value, _header) => setHeader(_value, _header, _rewrite));
    if (utils_default.isPlainObject(header) || header instanceof this.constructor) {
      setHeaders(header, valueOrRewrite);
    } else if (utils_default.isString(header) && (header = header.trim()) && !isValidHeaderName(header)) {
      setHeaders(parseHeaders_default(header), valueOrRewrite);
    } else if (utils_default.isObject(header) && utils_default.isSafeIterable(header)) {
      let obj = /* @__PURE__ */ Object.create(null), dest, key;
      for (const entry of header) {
        if (!utils_default.isArray(entry)) {
          throw new TypeError("Object iterator must return a key-value pair");
        }
        key = entry[0];
        if (utils_default.hasOwnProp(obj, key)) {
          dest = obj[key];
          obj[key] = utils_default.isArray(dest) ? [...dest, entry[1]] : [dest, entry[1]];
        } else {
          obj[key] = entry[1];
        }
      }
      setHeaders(obj, valueOrRewrite);
    } else {
      header != null && setHeader(valueOrRewrite, header, rewrite);
    }
    return this;
  }
  get(header, parser) {
    header = normalizeHeader(header);
    if (header) {
      const key = utils_default.findKey(this, header);
      if (key) {
        const value = this[key];
        if (!parser) {
          return value;
        }
        if (parser === true) {
          return parseTokens(value);
        }
        if (utils_default.isFunction(parser)) {
          return parser.call(this, value, key);
        }
        if (utils_default.isRegExp(parser)) {
          return parser.exec(value);
        }
        throw new TypeError("parser must be boolean|regexp|function");
      }
    }
  }
  has(header, matcher) {
    header = normalizeHeader(header);
    if (header) {
      const key = utils_default.findKey(this, header);
      return !!(key && this[key] !== void 0 && (!matcher || matchHeaderValue(this, this[key], key, matcher)));
    }
    return false;
  }
  delete(header, matcher) {
    const self2 = this;
    let deleted = false;
    function deleteHeader(_header) {
      _header = normalizeHeader(_header);
      if (_header) {
        const key = utils_default.findKey(self2, _header);
        if (key && (!matcher || matchHeaderValue(self2, self2[key], key, matcher))) {
          delete self2[key];
          deleted = true;
        }
      }
    }
    if (utils_default.isArray(header)) {
      header.forEach(deleteHeader);
    } else {
      deleteHeader(header);
    }
    return deleted;
  }
  clear(matcher) {
    const keys = Object.keys(this);
    let i = keys.length;
    let deleted = false;
    while (i--) {
      const key = keys[i];
      if (!matcher || matchHeaderValue(this, this[key], key, matcher, true)) {
        delete this[key];
        deleted = true;
      }
    }
    return deleted;
  }
  normalize(format) {
    const self2 = this;
    const headers = {};
    utils_default.forEach(this, (value, header) => {
      const key = utils_default.findKey(headers, header);
      if (key) {
        self2[key] = normalizeValue(value);
        delete self2[header];
        return;
      }
      const normalized = format ? formatHeader(header) : String(header).trim();
      if (normalized !== header) {
        delete self2[header];
      }
      self2[normalized] = normalizeValue(value);
      headers[normalized] = true;
    });
    return this;
  }
  concat(...targets) {
    return this.constructor.concat(this, ...targets);
  }
  toJSON(asStrings) {
    const obj = /* @__PURE__ */ Object.create(null);
    utils_default.forEach(this, (value, header) => {
      value != null && value !== false && (obj[header] = asStrings && utils_default.isArray(value) ? value.join(", ") : value);
    });
    return obj;
  }
  [Symbol.iterator]() {
    return Object.entries(this.toJSON())[Symbol.iterator]();
  }
  toString() {
    return Object.entries(this.toJSON()).map(([header, value]) => header + ": " + value).join("\n");
  }
  getSetCookie() {
    return this.get("set-cookie") || [];
  }
  get [Symbol.toStringTag]() {
    return "AxiosHeaders";
  }
  static from(thing) {
    return thing instanceof this ? thing : new this(thing);
  }
  static concat(first, ...targets) {
    const computed = new this(first);
    targets.forEach((target) => computed.set(target));
    return computed;
  }
  static accessor(header) {
    const internals = this[$internals] = this[$internals] = {
      accessors: {}
    };
    const accessors = internals.accessors;
    const prototype2 = this.prototype;
    function defineAccessor(_header) {
      const lHeader = normalizeHeader(_header);
      if (!accessors[lHeader]) {
        buildAccessors(prototype2, _header);
        accessors[lHeader] = true;
      }
    }
    utils_default.isArray(header) ? header.forEach(defineAccessor) : defineAccessor(header);
    return this;
  }
};
AxiosHeaders.accessor([
  "Content-Type",
  "Content-Length",
  "Accept",
  "Accept-Encoding",
  "User-Agent",
  "Authorization"
]);
utils_default.reduceDescriptors(AxiosHeaders.prototype, ({ value }, key) => {
  let mapped = key[0].toUpperCase() + key.slice(1);
  return {
    get: () => value,
    set(headerValue) {
      this[mapped] = headerValue;
    }
  };
});
utils_default.freezeMethods(AxiosHeaders);
var AxiosHeaders_default = AxiosHeaders;

// ../shared/core/node_modules/axios/lib/core/AxiosError.js
var REDACTED = "[REDACTED ****]";
function hasOwnOrPrototypeToJSON(source) {
  if (utils_default.hasOwnProp(source, "toJSON")) {
    return true;
  }
  let prototype2 = Object.getPrototypeOf(source);
  while (prototype2 && prototype2 !== Object.prototype) {
    if (utils_default.hasOwnProp(prototype2, "toJSON")) {
      return true;
    }
    prototype2 = Object.getPrototypeOf(prototype2);
  }
  return false;
}
function redactConfig(config, redactKeys) {
  const lowerKeys = new Set(redactKeys.map((k) => String(k).toLowerCase()));
  const seen = [];
  const visit = (source) => {
    if (source === null || typeof source !== "object") return source;
    if (utils_default.isBuffer(source)) return source;
    if (seen.indexOf(source) !== -1) return void 0;
    if (source instanceof AxiosHeaders_default) {
      source = source.toJSON();
    }
    seen.push(source);
    let result;
    if (utils_default.isArray(source)) {
      result = [];
      source.forEach((v, i) => {
        const reducedValue = visit(v);
        if (!utils_default.isUndefined(reducedValue)) {
          result[i] = reducedValue;
        }
      });
    } else {
      if (!utils_default.isPlainObject(source) && hasOwnOrPrototypeToJSON(source)) {
        seen.pop();
        return source;
      }
      result = /* @__PURE__ */ Object.create(null);
      for (const [key, value] of Object.entries(source)) {
        const reducedValue = lowerKeys.has(key.toLowerCase()) ? REDACTED : visit(value);
        if (!utils_default.isUndefined(reducedValue)) {
          result[key] = reducedValue;
        }
      }
    }
    seen.pop();
    return result;
  };
  return visit(config);
}
var AxiosError = class _AxiosError extends Error {
  static from(error, code, config, request, response, customProps) {
    const axiosError = new _AxiosError(error.message, code || error.code, config, request, response);
    axiosError.cause = error;
    axiosError.name = error.name;
    if (error.status != null && axiosError.status == null) {
      axiosError.status = error.status;
    }
    customProps && Object.assign(axiosError, customProps);
    return axiosError;
  }
  /**
   * Create an Error with the specified message, config, error code, request and response.
   *
   * @param {string} message The error message.
   * @param {string} [code] The error code (for example, 'ECONNABORTED').
   * @param {Object} [config] The config.
   * @param {Object} [request] The request.
   * @param {Object} [response] The response.
   *
   * @returns {Error} The created error.
   */
  constructor(message, code, config, request, response) {
    super(message);
    Object.defineProperty(this, "message", {
      // Null-proto descriptor so a polluted Object.prototype.get cannot turn
      // this data descriptor into an accessor descriptor on the way in.
      __proto__: null,
      value: message,
      enumerable: true,
      writable: true,
      configurable: true
    });
    this.name = "AxiosError";
    this.isAxiosError = true;
    code && (this.code = code);
    config && (this.config = config);
    request && (this.request = request);
    if (response) {
      this.response = response;
      this.status = response.status;
    }
  }
  toJSON() {
    const config = this.config;
    const redactKeys = config && utils_default.hasOwnProp(config, "redact") ? config.redact : void 0;
    const serializedConfig = utils_default.isArray(redactKeys) && redactKeys.length > 0 ? redactConfig(config, redactKeys) : utils_default.toJSONObject(config);
    return {
      // Standard
      message: this.message,
      name: this.name,
      // Microsoft
      description: this.description,
      number: this.number,
      // Mozilla
      fileName: this.fileName,
      lineNumber: this.lineNumber,
      columnNumber: this.columnNumber,
      stack: this.stack,
      // Axios
      config: serializedConfig,
      code: this.code,
      status: this.status
    };
  }
};
AxiosError.ERR_BAD_OPTION_VALUE = "ERR_BAD_OPTION_VALUE";
AxiosError.ERR_BAD_OPTION = "ERR_BAD_OPTION";
AxiosError.ECONNABORTED = "ECONNABORTED";
AxiosError.ETIMEDOUT = "ETIMEDOUT";
AxiosError.ECONNREFUSED = "ECONNREFUSED";
AxiosError.ERR_NETWORK = "ERR_NETWORK";
AxiosError.ERR_FR_TOO_MANY_REDIRECTS = "ERR_FR_TOO_MANY_REDIRECTS";
AxiosError.ERR_DEPRECATED = "ERR_DEPRECATED";
AxiosError.ERR_BAD_RESPONSE = "ERR_BAD_RESPONSE";
AxiosError.ERR_BAD_REQUEST = "ERR_BAD_REQUEST";
AxiosError.ERR_CANCELED = "ERR_CANCELED";
AxiosError.ERR_NOT_SUPPORT = "ERR_NOT_SUPPORT";
AxiosError.ERR_INVALID_URL = "ERR_INVALID_URL";
AxiosError.ERR_FORM_DATA_DEPTH_EXCEEDED = "ERR_FORM_DATA_DEPTH_EXCEEDED";
var AxiosError_default = AxiosError;

// ../shared/core/node_modules/axios/lib/platform/node/classes/FormData.js
var import_form_data = __toESM(require_form_data(), 1);
var FormData_default = import_form_data.default;

// ../shared/core/node_modules/axios/lib/helpers/toFormData.js
var DEFAULT_FORM_DATA_MAX_DEPTH = 100;
function isVisitable(thing) {
  return utils_default.isPlainObject(thing) || utils_default.isArray(thing);
}
function removeBrackets(key) {
  return utils_default.endsWith(key, "[]") ? key.slice(0, -2) : key;
}
function renderKey(path2, key, dots) {
  if (!path2) return key;
  return path2.concat(key).map(function each(token, i) {
    token = removeBrackets(token);
    return !dots && i ? "[" + token + "]" : token;
  }).join(dots ? "." : "");
}
function isFlatArray(arr) {
  return utils_default.isArray(arr) && !arr.some(isVisitable);
}
var predicates = utils_default.toFlatObject(utils_default, {}, null, function filter(prop) {
  return /^is[A-Z]/.test(prop);
});
function toFormData(obj, formData, options) {
  if (!utils_default.isObject(obj)) {
    throw new TypeError("target must be an object");
  }
  formData = formData || new (FormData_default || FormData)();
  options = utils_default.toFlatObject(
    options,
    {
      metaTokens: true,
      dots: false,
      indexes: false
    },
    false,
    function defined(option, source) {
      return !utils_default.isUndefined(source[option]);
    }
  );
  const metaTokens = options.metaTokens;
  const visitor = options.visitor || defaultVisitor;
  const dots = options.dots;
  const indexes = options.indexes;
  const _Blob = options.Blob || typeof Blob !== "undefined" && Blob;
  const maxDepth = options.maxDepth === void 0 ? DEFAULT_FORM_DATA_MAX_DEPTH : options.maxDepth;
  const useBlob = _Blob && utils_default.isSpecCompliantForm(formData);
  const stack = [];
  if (!utils_default.isFunction(visitor)) {
    throw new TypeError("visitor must be a function");
  }
  function convertValue(value) {
    if (value === null) return "";
    if (utils_default.isDate(value)) {
      return value.toISOString();
    }
    if (utils_default.isBoolean(value)) {
      return value.toString();
    }
    if (!useBlob && utils_default.isBlob(value)) {
      throw new AxiosError_default("Blob is not supported. Use a Buffer instead.");
    }
    if (utils_default.isArrayBuffer(value) || utils_default.isTypedArray(value)) {
      return useBlob && typeof Blob === "function" ? new Blob([value]) : Buffer.from(value);
    }
    return value;
  }
  function throwIfMaxDepthExceeded(depth) {
    if (depth > maxDepth) {
      throw new AxiosError_default(
        "Object is too deeply nested (" + depth + " levels). Max depth: " + maxDepth,
        AxiosError_default.ERR_FORM_DATA_DEPTH_EXCEEDED
      );
    }
  }
  function stringifyWithDepthLimit(value, depth) {
    if (maxDepth === Infinity) {
      return JSON.stringify(value);
    }
    const ancestors = [];
    return JSON.stringify(value, function limitDepth(_key, currentValue) {
      if (!utils_default.isObject(currentValue)) {
        return currentValue;
      }
      while (ancestors.length && ancestors[ancestors.length - 1] !== this) {
        ancestors.pop();
      }
      ancestors.push(currentValue);
      throwIfMaxDepthExceeded(depth + ancestors.length - 1);
      return currentValue;
    });
  }
  function defaultVisitor(value, key, path2) {
    let arr = value;
    if (utils_default.isReactNative(formData) && utils_default.isReactNativeBlob(value)) {
      formData.append(renderKey(path2, key, dots), convertValue(value));
      return false;
    }
    if (value && !path2 && typeof value === "object") {
      if (utils_default.endsWith(key, "{}")) {
        key = metaTokens ? key : key.slice(0, -2);
        value = stringifyWithDepthLimit(value, 1);
      } else if (utils_default.isArray(value) && isFlatArray(value) || (utils_default.isFileList(value) || utils_default.endsWith(key, "[]")) && (arr = utils_default.toArray(value))) {
        key = removeBrackets(key);
        arr.forEach(function each(el, index) {
          !(utils_default.isUndefined(el) || el === null) && formData.append(
            // eslint-disable-next-line no-nested-ternary
            indexes === true ? renderKey([key], index, dots) : indexes === null ? key : key + "[]",
            convertValue(el)
          );
        });
        return false;
      }
    }
    if (isVisitable(value)) {
      return true;
    }
    formData.append(renderKey(path2, key, dots), convertValue(value));
    return false;
  }
  const exposedHelpers = Object.assign(predicates, {
    defaultVisitor,
    convertValue,
    isVisitable
  });
  function build(value, path2, depth = 0) {
    if (utils_default.isUndefined(value)) return;
    throwIfMaxDepthExceeded(depth);
    if (stack.indexOf(value) !== -1) {
      throw new Error("Circular reference detected in " + path2.join("."));
    }
    stack.push(value);
    utils_default.forEach(value, function each(el, key) {
      const result = !(utils_default.isUndefined(el) || el === null) && visitor.call(formData, el, utils_default.isString(key) ? key.trim() : key, path2, exposedHelpers);
      if (result === true) {
        build(el, path2 ? path2.concat(key) : [key], depth + 1);
      }
    });
    stack.pop();
  }
  if (!utils_default.isObject(obj)) {
    throw new TypeError("data must be an object");
  }
  build(obj);
  return formData;
}
var toFormData_default = toFormData;

// ../shared/core/node_modules/axios/lib/helpers/AxiosURLSearchParams.js
function encode(str) {
  const charMap = {
    "!": "%21",
    "'": "%27",
    "(": "%28",
    ")": "%29",
    "~": "%7E",
    "%20": "+"
  };
  return encodeURIComponent(str).replace(/[!'()~]|%20/g, function replacer(match) {
    return charMap[match];
  });
}
function AxiosURLSearchParams(params, options) {
  this._pairs = [];
  params && toFormData_default(params, this, options);
}
var prototype = AxiosURLSearchParams.prototype;
prototype.append = function append(name, value) {
  this._pairs.push([name, value]);
};
prototype.toString = function toString2(encoder) {
  const _encode = encoder ? function(value) {
    return encoder.call(this, value, encode);
  } : encode;
  return this._pairs.map(function each(pair) {
    return _encode(pair[0]) + "=" + _encode(pair[1]);
  }, "").join("&");
};
var AxiosURLSearchParams_default = AxiosURLSearchParams;

// ../shared/core/node_modules/axios/lib/helpers/buildURL.js
function encode2(val) {
  return encodeURIComponent(val).replace(/%3A/gi, ":").replace(/%24/g, "$").replace(/%2C/gi, ",").replace(/%20/g, "+");
}
function buildURL(url2, params, options) {
  if (!params) {
    return url2;
  }
  const _options = utils_default.isFunction(options) ? {
    serialize: options
  } : options;
  const _encode = utils_default.getSafeProp(_options, "encode") || encode2;
  const serializeFn = utils_default.getSafeProp(_options, "serialize");
  let serializedParams;
  if (serializeFn) {
    serializedParams = serializeFn(params, _options);
  } else {
    serializedParams = utils_default.isURLSearchParams(params) ? params.toString() : new AxiosURLSearchParams_default(params, _options).toString(_encode);
  }
  if (serializedParams) {
    const hashmarkIndex = url2.indexOf("#");
    if (hashmarkIndex !== -1) {
      url2 = url2.slice(0, hashmarkIndex);
    }
    url2 += (url2.indexOf("?") === -1 ? "?" : "&") + serializedParams;
  }
  return url2;
}

// ../shared/core/node_modules/axios/lib/core/InterceptorManager.js
var InterceptorManager = class {
  constructor() {
    this.handlers = [];
  }
  /**
   * Add a new interceptor to the stack
   *
   * @param {Function} fulfilled The function to handle `then` for a `Promise`
   * @param {Function} rejected The function to handle `reject` for a `Promise`
   * @param {Object} options The options for the interceptor, synchronous and runWhen
   *
   * @return {Number} An ID used to remove interceptor later
   */
  use(fulfilled, rejected, options) {
    this.handlers.push({
      fulfilled,
      rejected,
      synchronous: options ? options.synchronous : false,
      runWhen: options ? options.runWhen : null
    });
    return this.handlers.length - 1;
  }
  /**
   * Remove an interceptor from the stack
   *
   * @param {Number} id The ID that was returned by `use`
   *
   * @returns {void}
   */
  eject(id) {
    if (this.handlers[id]) {
      this.handlers[id] = null;
    }
  }
  /**
   * Clear all interceptors from the stack
   *
   * @returns {void}
   */
  clear() {
    if (this.handlers) {
      this.handlers = [];
    }
  }
  /**
   * Iterate over all the registered interceptors
   *
   * This method is particularly useful for skipping over any
   * interceptors that may have become `null` calling `eject`.
   *
   * @param {Function} fn The function to call for each interceptor
   *
   * @returns {void}
   */
  forEach(fn) {
    utils_default.forEach(this.handlers, function forEachHandler(h) {
      if (h !== null) {
        fn(h);
      }
    });
  }
};
var InterceptorManager_default = InterceptorManager;

// ../shared/core/node_modules/axios/lib/defaults/transitional.js
var transitional_default = {
  silentJSONParsing: true,
  forcedJSONParsing: true,
  clarifyTimeoutError: false,
  legacyInterceptorReqResOrdering: true,
  advertiseZstdAcceptEncoding: false,
  validateStatusUndefinedResolves: true
};

// ../shared/core/node_modules/axios/lib/platform/node/index.js
import crypto2 from "crypto";

// ../shared/core/node_modules/axios/lib/platform/node/classes/URLSearchParams.js
import url from "url";
var URLSearchParams_default = url.URLSearchParams;

// ../shared/core/node_modules/axios/lib/platform/node/index.js
var ALPHA = "abcdefghijklmnopqrstuvwxyz";
var DIGIT = "0123456789";
var ALPHABET = {
  DIGIT,
  ALPHA,
  ALPHA_DIGIT: ALPHA + ALPHA.toUpperCase() + DIGIT
};
var generateString = (size = 16, alphabet = ALPHABET.ALPHA_DIGIT) => {
  let str = "";
  const { length } = alphabet;
  const randomValues = new Uint32Array(size);
  crypto2.randomFillSync(randomValues);
  for (let i = 0; i < size; i++) {
    str += alphabet[randomValues[i] % length];
  }
  return str;
};
var node_default = {
  isNode: true,
  classes: {
    URLSearchParams: URLSearchParams_default,
    FormData: FormData_default,
    Blob: typeof Blob !== "undefined" && Blob || null
  },
  ALPHABET,
  generateString,
  protocols: ["http", "https", "file", "data"]
};

// ../shared/core/node_modules/axios/lib/platform/common/utils.js
var utils_exports = {};
__export(utils_exports, {
  hasBrowserEnv: () => hasBrowserEnv,
  hasStandardBrowserEnv: () => hasStandardBrowserEnv,
  hasStandardBrowserWebWorkerEnv: () => hasStandardBrowserWebWorkerEnv,
  navigator: () => _navigator,
  origin: () => origin
});
var hasBrowserEnv = typeof window !== "undefined" && typeof document !== "undefined";
var _navigator = typeof navigator === "object" && navigator || void 0;
var hasStandardBrowserEnv = hasBrowserEnv && (!_navigator || ["ReactNative", "NativeScript", "NS"].indexOf(_navigator.product) < 0);
var hasStandardBrowserWebWorkerEnv = (() => {
  return typeof WorkerGlobalScope !== "undefined" && // eslint-disable-next-line no-undef
  self instanceof WorkerGlobalScope && typeof self.importScripts === "function";
})();
var origin = hasBrowserEnv && window.location.href || "http://localhost";

// ../shared/core/node_modules/axios/lib/platform/index.js
var platform_default = {
  ...utils_exports,
  ...node_default
};

// ../shared/core/node_modules/axios/lib/helpers/toURLEncodedForm.js
function toURLEncodedForm(data, options) {
  return toFormData_default(data, new platform_default.classes.URLSearchParams(), {
    visitor: function(value, key, path2, helpers) {
      if (platform_default.isNode && utils_default.isBuffer(value)) {
        this.append(key, value.toString("base64"));
        return false;
      }
      return helpers.defaultVisitor.apply(this, arguments);
    },
    ...options
  });
}

// ../shared/core/node_modules/axios/lib/helpers/formDataToJSON.js
var MAX_DEPTH = DEFAULT_FORM_DATA_MAX_DEPTH;
function throwIfDepthExceeded(index) {
  if (index > MAX_DEPTH) {
    throw new AxiosError_default(
      "FormData field is too deeply nested (" + index + " levels). Max depth: " + MAX_DEPTH,
      AxiosError_default.ERR_FORM_DATA_DEPTH_EXCEEDED
    );
  }
}
function parsePropPath(name) {
  const path2 = [];
  const pattern = /\w+|\[(\w*)]/g;
  let match;
  while ((match = pattern.exec(name)) !== null) {
    throwIfDepthExceeded(path2.length);
    path2.push(match[0] === "[]" ? "" : match[1] || match[0]);
  }
  return path2;
}
function arrayToObject(arr) {
  const obj = {};
  const keys = Object.keys(arr);
  let i;
  const len = keys.length;
  let key;
  for (i = 0; i < len; i++) {
    key = keys[i];
    obj[key] = arr[key];
  }
  return obj;
}
function formDataToJSON(formData) {
  function buildPath(path2, value, target, index) {
    throwIfDepthExceeded(index);
    let name = path2[index++];
    if (name === "__proto__") return true;
    const isNumericKey = Number.isFinite(+name);
    const isLast = index >= path2.length;
    name = !name && utils_default.isArray(target) ? target.length : name;
    if (isLast) {
      if (utils_default.hasOwnProp(target, name)) {
        target[name] = utils_default.isArray(target[name]) ? target[name].concat(value) : [target[name], value];
      } else {
        target[name] = value;
      }
      return !isNumericKey;
    }
    if (!utils_default.hasOwnProp(target, name) || !utils_default.isObject(target[name])) {
      target[name] = [];
    }
    const result = buildPath(path2, value, target[name], index);
    if (result && utils_default.isArray(target[name])) {
      target[name] = arrayToObject(target[name]);
    }
    return !isNumericKey;
  }
  if (utils_default.isFormData(formData) && utils_default.isFunction(formData.entries)) {
    const obj = {};
    utils_default.forEachEntry(formData, (name, value) => {
      buildPath(parsePropPath(name), value, obj, 0);
    });
    return obj;
  }
  return null;
}
var formDataToJSON_default = formDataToJSON;

// ../shared/core/node_modules/axios/lib/defaults/index.js
var own = (obj, key) => obj != null && utils_default.hasOwnProp(obj, key) ? obj[key] : void 0;
function stringifySafely(rawValue, parser, encoder) {
  if (utils_default.isString(rawValue)) {
    try {
      (parser || JSON.parse)(rawValue);
      return utils_default.trim(rawValue);
    } catch (e) {
      if (e.name !== "SyntaxError") {
        throw e;
      }
    }
  }
  return (encoder || JSON.stringify)(rawValue);
}
var defaults = {
  transitional: transitional_default,
  adapter: ["xhr", "http", "fetch"],
  transformRequest: [
    function transformRequest(data, headers) {
      const contentType = headers.getContentType() || "";
      const hasJSONContentType = contentType.indexOf("application/json") > -1;
      const isObjectPayload = utils_default.isObject(data);
      if (isObjectPayload && utils_default.isHTMLForm(data)) {
        data = new FormData(data);
      }
      const isFormData2 = utils_default.isFormData(data);
      if (isFormData2) {
        return hasJSONContentType ? JSON.stringify(formDataToJSON_default(data)) : data;
      }
      if (utils_default.isArrayBuffer(data) || utils_default.isBuffer(data) || utils_default.isStream(data) || utils_default.isFile(data) || utils_default.isBlob(data) || utils_default.isReadableStream(data)) {
        return data;
      }
      if (utils_default.isArrayBufferView(data)) {
        return data.buffer;
      }
      if (utils_default.isURLSearchParams(data)) {
        headers.setContentType("application/x-www-form-urlencoded;charset=utf-8", false);
        return data.toString();
      }
      let isFileList2;
      if (isObjectPayload) {
        const formSerializer = own(this, "formSerializer");
        if (contentType.indexOf("application/x-www-form-urlencoded") > -1) {
          return toURLEncodedForm(data, formSerializer).toString();
        }
        if ((isFileList2 = utils_default.isFileList(data)) || contentType.indexOf("multipart/form-data") > -1) {
          const env = own(this, "env");
          const _FormData = env && env.FormData;
          return toFormData_default(
            isFileList2 ? { "files[]": data } : data,
            _FormData && new _FormData(),
            formSerializer
          );
        }
      }
      if (isObjectPayload || hasJSONContentType) {
        headers.setContentType("application/json", false);
        return stringifySafely(data);
      }
      return data;
    }
  ],
  transformResponse: [
    function transformResponse(data) {
      const transitional2 = own(this, "transitional") || defaults.transitional;
      const forcedJSONParsing = transitional2 && transitional2.forcedJSONParsing;
      const responseType = own(this, "responseType");
      const JSONRequested = responseType === "json";
      if (utils_default.isResponse(data) || utils_default.isReadableStream(data)) {
        return data;
      }
      if (data && utils_default.isString(data) && (forcedJSONParsing && !responseType || JSONRequested)) {
        const silentJSONParsing = transitional2 && transitional2.silentJSONParsing;
        const strictJSONParsing = !silentJSONParsing && JSONRequested;
        try {
          return JSON.parse(data, own(this, "parseReviver"));
        } catch (e) {
          if (strictJSONParsing) {
            if (e.name === "SyntaxError") {
              throw AxiosError_default.from(e, AxiosError_default.ERR_BAD_RESPONSE, this, null, own(this, "response"));
            }
            throw e;
          }
        }
      }
      return data;
    }
  ],
  /**
   * A timeout in milliseconds to abort a request. If set to 0 (default) a
   * timeout is not created.
   */
  timeout: 0,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  maxContentLength: -1,
  maxBodyLength: -1,
  env: {
    FormData: platform_default.classes.FormData,
    Blob: platform_default.classes.Blob
  },
  validateStatus: function validateStatus(status) {
    return status >= 200 && status < 300;
  },
  headers: {
    common: {
      Accept: "application/json, text/plain, */*",
      "Content-Type": void 0
    }
  }
};
utils_default.forEach(["delete", "get", "head", "post", "put", "patch", "query"], (method) => {
  defaults.headers[method] = {};
});
var defaults_default = defaults;

// ../shared/core/node_modules/axios/lib/core/transformData.js
function transformData(fns, response) {
  const config = this || defaults_default;
  const context = response || config;
  const headers = AxiosHeaders_default.from(context.headers);
  let data = context.data;
  utils_default.forEach(fns, function transform(fn) {
    data = fn.call(config, data, headers.normalize(), response ? response.status : void 0);
  });
  headers.normalize();
  return data;
}

// ../shared/core/node_modules/axios/lib/cancel/isCancel.js
function isCancel(value) {
  return !!(value && value.__CANCEL__);
}

// ../shared/core/node_modules/axios/lib/cancel/CanceledError.js
var CanceledError = class extends AxiosError_default {
  /**
   * A `CanceledError` is an object that is thrown when an operation is canceled.
   *
   * @param {string=} message The message.
   * @param {Object=} config The config.
   * @param {Object=} request The request.
   *
   * @returns {CanceledError} The created error.
   */
  constructor(message, config, request) {
    super(message == null ? "canceled" : message, AxiosError_default.ERR_CANCELED, config, request);
    this.name = "CanceledError";
    this.__CANCEL__ = true;
  }
};
var CanceledError_default = CanceledError;

// ../shared/core/node_modules/axios/lib/core/settle.js
function settle(resolve, reject, response) {
  const validateStatus2 = response.config.validateStatus;
  if (!response.status || !validateStatus2 || validateStatus2(response.status)) {
    resolve(response);
  } else {
    reject(new AxiosError_default(
      "Request failed with status code " + response.status,
      response.status >= 400 && response.status < 500 ? AxiosError_default.ERR_BAD_REQUEST : AxiosError_default.ERR_BAD_RESPONSE,
      response.config,
      response.request,
      response
    ));
  }
}

// ../shared/core/node_modules/axios/lib/helpers/isAbsoluteURL.js
function isAbsoluteURL(url2) {
  if (typeof url2 !== "string") {
    return false;
  }
  return /^([a-z][a-z\d+\-.]*:)?\/\//i.test(url2);
}

// ../shared/core/node_modules/axios/lib/helpers/combineURLs.js
function combineURLs(baseURL, relativeURL) {
  return relativeURL ? baseURL.replace(/\/?\/$/, "") + "/" + relativeURL.replace(/^\/+/, "") : baseURL;
}

// ../shared/core/node_modules/axios/lib/core/buildFullPath.js
var malformedHttpProtocol = /^https?:(?!\/\/)/i;
var httpProtocolControlCharacters = /[\t\n\r]/g;
function stripLeadingC0ControlOrSpace(url2) {
  let i = 0;
  while (i < url2.length && url2.charCodeAt(i) <= 32) {
    i++;
  }
  return url2.slice(i);
}
function normalizeURLForProtocolCheck(url2) {
  return stripLeadingC0ControlOrSpace(url2).replace(httpProtocolControlCharacters, "");
}
function assertValidHttpProtocolURL(url2, config) {
  if (typeof url2 === "string" && malformedHttpProtocol.test(normalizeURLForProtocolCheck(url2))) {
    throw new AxiosError_default(
      'Invalid URL: missing "//" after protocol',
      AxiosError_default.ERR_INVALID_URL,
      config
    );
  }
}
function buildFullPath(baseURL, requestedURL, allowAbsoluteUrls, config) {
  assertValidHttpProtocolURL(requestedURL, config);
  let isRelativeUrl = !isAbsoluteURL(requestedURL);
  if (baseURL && (isRelativeUrl || allowAbsoluteUrls === false)) {
    assertValidHttpProtocolURL(baseURL, config);
    return combineURLs(baseURL, requestedURL);
  }
  return requestedURL;
}

// ../shared/core/node_modules/proxy-from-env/index.js
var DEFAULT_PORTS = {
  ftp: 21,
  gopher: 70,
  http: 80,
  https: 443,
  ws: 80,
  wss: 443
};
function parseUrl(urlString) {
  try {
    return new URL(urlString);
  } catch {
    return null;
  }
}
function getProxyForUrl(url2) {
  var parsedUrl = (typeof url2 === "string" ? parseUrl(url2) : url2) || {};
  var proto = parsedUrl.protocol;
  var hostname = parsedUrl.host;
  var port = parsedUrl.port;
  if (typeof hostname !== "string" || !hostname || typeof proto !== "string") {
    return "";
  }
  proto = proto.split(":", 1)[0];
  hostname = hostname.replace(/:\d*$/, "");
  port = parseInt(port) || DEFAULT_PORTS[proto] || 0;
  if (!shouldProxy(hostname, port)) {
    return "";
  }
  var proxy = getEnv(proto + "_proxy") || getEnv("all_proxy");
  if (proxy && proxy.indexOf("://") === -1) {
    proxy = proto + "://" + proxy;
  }
  return proxy;
}
function shouldProxy(hostname, port) {
  var NO_PROXY = getEnv("no_proxy").toLowerCase();
  if (!NO_PROXY) {
    return true;
  }
  if (NO_PROXY === "*") {
    return false;
  }
  return NO_PROXY.split(/[,\s]/).every(function(proxy) {
    if (!proxy) {
      return true;
    }
    var parsedProxy = proxy.match(/^(.+):(\d+)$/);
    var parsedProxyHostname = parsedProxy ? parsedProxy[1] : proxy;
    var parsedProxyPort = parsedProxy ? parseInt(parsedProxy[2]) : 0;
    if (parsedProxyPort && parsedProxyPort !== port) {
      return true;
    }
    if (!/^[.*]/.test(parsedProxyHostname)) {
      return hostname !== parsedProxyHostname;
    }
    if (parsedProxyHostname.charAt(0) === "*") {
      parsedProxyHostname = parsedProxyHostname.slice(1);
    }
    return !hostname.endsWith(parsedProxyHostname);
  });
}
function getEnv(key) {
  return process.env[key.toLowerCase()] || process.env[key.toUpperCase()] || "";
}

// ../shared/core/node_modules/axios/lib/adapters/http.js
var import_https_proxy_agent = __toESM(require_dist(), 1);
var import_follow_redirects = __toESM(require_follow_redirects(), 1);
import http from "http";
import https from "https";
import http22 from "http2";
import util3 from "util";
import { resolve as resolvePath } from "path";
import zlib from "zlib";

// ../shared/core/node_modules/axios/lib/env/data.js
var VERSION = "1.18.0";

// ../shared/core/node_modules/axios/lib/helpers/parseProtocol.js
function parseProtocol(url2) {
  const match = /^([-+\w]{1,25}):(?:\/\/)?/.exec(url2);
  return match && match[1] || "";
}

// ../shared/core/node_modules/axios/lib/helpers/fromDataURI.js
var DATA_URL_PATTERN = /^([^,;]+\/[^,;]+)?((?:;[^,;=]+=[^,;]+)*)(;base64)?,([\s\S]*)$/;
function fromDataURI(uri, asBlob, options) {
  const _Blob = options && options.Blob || platform_default.classes.Blob;
  const protocol = parseProtocol(uri);
  if (asBlob === void 0 && _Blob) {
    asBlob = true;
  }
  if (protocol === "data") {
    uri = protocol.length ? uri.slice(protocol.length + 1) : uri;
    const match = DATA_URL_PATTERN.exec(uri);
    if (!match) {
      throw new AxiosError_default("Invalid URL", AxiosError_default.ERR_INVALID_URL);
    }
    const type = match[1];
    const params = match[2];
    const encoding = match[3] ? "base64" : "utf8";
    const body = match[4];
    let mime;
    if (type) {
      mime = params ? type + params : type;
    } else if (params) {
      mime = "text/plain" + params;
    }
    const buffer = Buffer.from(decodeURIComponent(body), encoding);
    if (asBlob) {
      if (!_Blob) {
        throw new AxiosError_default("Blob is not supported", AxiosError_default.ERR_NOT_SUPPORT);
      }
      return new _Blob([buffer], { type: mime });
    }
    return buffer;
  }
  throw new AxiosError_default("Unsupported protocol " + protocol, AxiosError_default.ERR_NOT_SUPPORT);
}

// ../shared/core/node_modules/axios/lib/adapters/http.js
import stream3 from "stream";

// ../shared/core/node_modules/axios/lib/helpers/AxiosTransformStream.js
import stream from "stream";
var kInternals = Symbol("internals");
var AxiosTransformStream = class extends stream.Transform {
  constructor(options) {
    options = utils_default.toFlatObject(
      options,
      {
        maxRate: 0,
        chunkSize: 64 * 1024,
        minChunkSize: 100,
        timeWindow: 500,
        ticksRate: 2,
        samplesCount: 15
      },
      null,
      (prop, source) => {
        return !utils_default.isUndefined(source[prop]);
      }
    );
    super({
      readableHighWaterMark: options.chunkSize
    });
    const internals = this[kInternals] = {
      timeWindow: options.timeWindow,
      chunkSize: options.chunkSize,
      maxRate: options.maxRate,
      minChunkSize: options.minChunkSize,
      bytesSeen: 0,
      isCaptured: false,
      notifiedBytesLoaded: 0,
      ts: Date.now(),
      bytes: 0,
      onReadCallback: null
    };
    this.on("newListener", (event) => {
      if (event === "progress") {
        if (!internals.isCaptured) {
          internals.isCaptured = true;
        }
      }
    });
  }
  _read(size) {
    const internals = this[kInternals];
    if (internals.onReadCallback) {
      internals.onReadCallback();
    }
    return super._read(size);
  }
  _transform(chunk, encoding, callback) {
    const internals = this[kInternals];
    const maxRate = internals.maxRate;
    const readableHighWaterMark = this.readableHighWaterMark;
    const timeWindow = internals.timeWindow;
    const divider = 1e3 / timeWindow;
    const bytesThreshold = maxRate / divider;
    const minChunkSize = internals.minChunkSize !== false ? Math.max(internals.minChunkSize, bytesThreshold * 0.01) : 0;
    const pushChunk = (_chunk, _callback) => {
      const bytes = Buffer.byteLength(_chunk);
      internals.bytesSeen += bytes;
      internals.bytes += bytes;
      internals.isCaptured && this.emit("progress", internals.bytesSeen);
      if (this.push(_chunk)) {
        process.nextTick(_callback);
      } else {
        internals.onReadCallback = () => {
          internals.onReadCallback = null;
          process.nextTick(_callback);
        };
      }
    };
    const transformChunk = (_chunk, _callback) => {
      const chunkSize = Buffer.byteLength(_chunk);
      let chunkRemainder = null;
      let maxChunkSize = readableHighWaterMark;
      let bytesLeft;
      let passed = 0;
      if (maxRate) {
        const now = Date.now();
        if (!internals.ts || (passed = now - internals.ts) >= timeWindow) {
          internals.ts = now;
          bytesLeft = bytesThreshold - internals.bytes;
          internals.bytes = bytesLeft < 0 ? -bytesLeft : 0;
          passed = 0;
        }
        bytesLeft = bytesThreshold - internals.bytes;
      }
      if (maxRate) {
        if (bytesLeft <= 0) {
          return setTimeout(() => {
            _callback(null, _chunk);
          }, timeWindow - passed);
        }
        if (bytesLeft < maxChunkSize) {
          maxChunkSize = bytesLeft;
        }
      }
      if (maxChunkSize && chunkSize > maxChunkSize && chunkSize - maxChunkSize > minChunkSize) {
        chunkRemainder = _chunk.subarray(maxChunkSize);
        _chunk = _chunk.subarray(0, maxChunkSize);
      }
      pushChunk(
        _chunk,
        chunkRemainder ? () => {
          process.nextTick(_callback, null, chunkRemainder);
        } : _callback
      );
    };
    transformChunk(chunk, function transformNextChunk(err, _chunk) {
      if (err) {
        return callback(err);
      }
      if (_chunk) {
        transformChunk(_chunk, transformNextChunk);
      } else {
        callback(null);
      }
    });
  }
};
var AxiosTransformStream_default = AxiosTransformStream;

// ../shared/core/node_modules/axios/lib/adapters/http.js
import { EventEmitter } from "events";

// ../shared/core/node_modules/axios/lib/helpers/formDataToStream.js
import util from "util";
import { Readable } from "stream";

// ../shared/core/node_modules/axios/lib/helpers/readBlob.js
var { asyncIterator } = Symbol;
var readBlob = async function* (blob) {
  if (blob.stream) {
    yield* blob.stream();
  } else if (blob.arrayBuffer) {
    yield await blob.arrayBuffer();
  } else if (blob[asyncIterator]) {
    yield* blob[asyncIterator]();
  } else {
    yield blob;
  }
};
var readBlob_default = readBlob;

// ../shared/core/node_modules/axios/lib/helpers/formDataToStream.js
var BOUNDARY_ALPHABET = platform_default.ALPHABET.ALPHA_DIGIT + "-_";
var textEncoder = typeof TextEncoder === "function" ? new TextEncoder() : new util.TextEncoder();
var CRLF = "\r\n";
var CRLF_BYTES = textEncoder.encode(CRLF);
var CRLF_BYTES_COUNT = 2;
var FormDataPart = class {
  constructor(name, value) {
    const { escapeName } = this.constructor;
    const isStringValue = utils_default.isString(value);
    let headers = `Content-Disposition: form-data; name="${escapeName(name)}"${!isStringValue && value.name ? `; filename="${escapeName(value.name)}"` : ""}${CRLF}`;
    if (isStringValue) {
      value = textEncoder.encode(String(value).replace(/\r?\n|\r\n?/g, CRLF));
    } else {
      const safeType = String(value.type || "application/octet-stream").replace(/[\r\n]/g, "");
      headers += `Content-Type: ${safeType}${CRLF}`;
    }
    this.headers = textEncoder.encode(headers + CRLF);
    this.contentLength = isStringValue ? value.byteLength : value.size;
    this.size = this.headers.byteLength + this.contentLength + CRLF_BYTES_COUNT;
    this.name = name;
    this.value = value;
  }
  async *encode() {
    yield this.headers;
    const { value } = this;
    if (utils_default.isTypedArray(value)) {
      yield value;
    } else {
      yield* readBlob_default(value);
    }
    yield CRLF_BYTES;
  }
  static escapeName(name) {
    return String(name).replace(
      /[\r\n"]/g,
      (match) => ({
        "\r": "%0D",
        "\n": "%0A",
        '"': "%22"
      })[match]
    );
  }
};
var formDataToStream = (form, headersHandler, options) => {
  const {
    tag = "form-data-boundary",
    size = 25,
    boundary = tag + "-" + platform_default.generateString(size, BOUNDARY_ALPHABET)
  } = options || {};
  if (!utils_default.isFormData(form)) {
    throw new TypeError("FormData instance required");
  }
  if (boundary.length < 1 || boundary.length > 70) {
    throw new Error("boundary must be 1-70 characters long");
  }
  const boundaryBytes = textEncoder.encode("--" + boundary + CRLF);
  const footerBytes = textEncoder.encode("--" + boundary + "--" + CRLF);
  let contentLength = footerBytes.byteLength;
  const parts = Array.from(form.entries()).map(([name, value]) => {
    const part = new FormDataPart(name, value);
    contentLength += part.size;
    return part;
  });
  contentLength += boundaryBytes.byteLength * parts.length;
  contentLength = utils_default.toFiniteNumber(contentLength);
  const computedHeaders = {
    "Content-Type": `multipart/form-data; boundary=${boundary}`
  };
  if (Number.isFinite(contentLength)) {
    computedHeaders["Content-Length"] = contentLength;
  }
  headersHandler && headersHandler(computedHeaders);
  return Readable.from(
    async function* () {
      for (const part of parts) {
        yield boundaryBytes;
        yield* part.encode();
      }
      yield footerBytes;
    }()
  );
};
var formDataToStream_default = formDataToStream;

// ../shared/core/node_modules/axios/lib/helpers/ZlibHeaderTransformStream.js
import stream2 from "stream";
var ZlibHeaderTransformStream = class extends stream2.Transform {
  __transform(chunk, encoding, callback) {
    this.push(chunk);
    callback();
  }
  _transform(chunk, encoding, callback) {
    if (chunk.length !== 0) {
      this._transform = this.__transform;
      if (chunk[0] !== 120) {
        const header = Buffer.alloc(2);
        header[0] = 120;
        header[1] = 156;
        this.push(header, encoding);
      }
    }
    this.__transform(chunk, encoding, callback);
  }
};
var ZlibHeaderTransformStream_default = ZlibHeaderTransformStream;

// ../shared/core/node_modules/axios/lib/helpers/Http2Sessions.js
import http2 from "http2";
import util2 from "util";
var Http2Sessions = class {
  constructor() {
    this.sessions = /* @__PURE__ */ Object.create(null);
  }
  getSession(authority, options) {
    options = Object.assign(
      {
        sessionTimeout: 1e3
      },
      options
    );
    let authoritySessions = this.sessions[authority];
    if (authoritySessions) {
      let len = authoritySessions.length;
      for (let i = 0; i < len; i++) {
        const [sessionHandle, sessionOptions] = authoritySessions[i];
        if (!sessionHandle.destroyed && !sessionHandle.closed && util2.isDeepStrictEqual(sessionOptions, options)) {
          return sessionHandle;
        }
      }
    }
    const session = http2.connect(authority, options);
    let removed;
    let timer;
    const removeSession = () => {
      if (removed) {
        return;
      }
      removed = true;
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      let entries = authoritySessions, len = entries.length, i = len;
      while (i--) {
        if (entries[i][0] === session) {
          if (len === 1) {
            delete this.sessions[authority];
          } else {
            entries.splice(i, 1);
          }
          if (!session.closed) {
            session.close();
          }
          return;
        }
      }
    };
    const originalRequestFn = session.request;
    const { sessionTimeout } = options;
    if (sessionTimeout != null) {
      let streamsCount = 0;
      session.request = function() {
        const stream4 = originalRequestFn.apply(this, arguments);
        streamsCount++;
        if (timer) {
          clearTimeout(timer);
          timer = null;
        }
        stream4.once("close", () => {
          if (!--streamsCount) {
            timer = setTimeout(() => {
              timer = null;
              removeSession();
            }, sessionTimeout);
          }
        });
        return stream4;
      };
    }
    session.once("close", removeSession);
    let entry = [session, options];
    authoritySessions ? authoritySessions.push(entry) : authoritySessions = this.sessions[authority] = [entry];
    return session;
  }
};
var Http2Sessions_default = Http2Sessions;

// ../shared/core/node_modules/axios/lib/helpers/callbackify.js
var callbackify = (fn, reducer) => {
  return utils_default.isAsyncFn(fn) ? function(...args) {
    const cb = args.pop();
    fn.apply(this, args).then((value) => {
      try {
        reducer ? cb(null, ...reducer(value)) : cb(null, value);
      } catch (err) {
        cb(err);
      }
    }, cb);
  } : fn;
};
var callbackify_default = callbackify;

// ../shared/core/node_modules/axios/lib/helpers/shouldBypassProxy.js
var LOOPBACK_HOSTNAMES = /* @__PURE__ */ new Set(["localhost", "0.0.0.0"]);
var isIPv4Loopback = (host) => {
  const parts = host.split(".");
  if (parts.length !== 4) return false;
  if (parts[0] !== "127") return false;
  return parts.every((p) => /^\d+$/.test(p) && Number(p) >= 0 && Number(p) <= 255);
};
var isIPv6ZeroGroup = (group) => /^0{1,4}$/.test(group);
var isIPv6Unspecified = (host) => {
  if (host === "::") return true;
  const compressionIndex = host.indexOf("::");
  if (compressionIndex !== -1) {
    if (compressionIndex !== host.lastIndexOf("::")) return false;
    const left = host.slice(0, compressionIndex);
    const right = host.slice(compressionIndex + 2);
    const leftGroups = left ? left.split(":") : [];
    const rightGroups = right ? right.split(":") : [];
    const explicitGroups = leftGroups.length + rightGroups.length;
    return explicitGroups < 8 && leftGroups.every(isIPv6ZeroGroup) && rightGroups.every(isIPv6ZeroGroup);
  }
  const groups = host.split(":");
  return groups.length === 8 && groups.every(isIPv6ZeroGroup);
};
var isIPv6Loopback = (host) => {
  if (host === "::1") return true;
  const v4MappedDotted = host.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/i);
  if (v4MappedDotted) return isIPv4Loopback(v4MappedDotted[1]);
  const v4MappedHex = host.match(/^::ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/i);
  if (v4MappedHex) {
    const high = parseInt(v4MappedHex[1], 16);
    return high >= 32512 && high <= 32767;
  }
  const groups = host.split(":");
  if (groups.length === 8) {
    for (let i = 0; i < 7; i++) {
      if (!/^0+$/.test(groups[i])) return false;
    }
    return /^0*1$/.test(groups[7]);
  }
  return false;
};
var isLoopback = (host) => {
  if (!host) return false;
  if (LOOPBACK_HOSTNAMES.has(host)) return true;
  if (isIPv4Loopback(host)) return true;
  if (isIPv6Unspecified(host)) return true;
  return isIPv6Loopback(host);
};
var DEFAULT_PORTS2 = {
  http: 80,
  https: 443,
  ws: 80,
  wss: 443,
  ftp: 21
};
var parseNoProxyEntry = (entry) => {
  let entryHost = entry;
  let entryPort = 0;
  if (entryHost.charAt(0) === "[") {
    const bracketIndex = entryHost.indexOf("]");
    if (bracketIndex !== -1) {
      const host = entryHost.slice(1, bracketIndex);
      const rest = entryHost.slice(bracketIndex + 1);
      if (rest.charAt(0) === ":" && /^\d+$/.test(rest.slice(1))) {
        entryPort = Number.parseInt(rest.slice(1), 10);
      }
      return [host, entryPort];
    }
  }
  const firstColon = entryHost.indexOf(":");
  const lastColon = entryHost.lastIndexOf(":");
  if (firstColon !== -1 && firstColon === lastColon && /^\d+$/.test(entryHost.slice(lastColon + 1))) {
    entryPort = Number.parseInt(entryHost.slice(lastColon + 1), 10);
    entryHost = entryHost.slice(0, lastColon);
  }
  return [entryHost, entryPort];
};
var IPV4_MAPPED_DOTTED_RE = /^(?:::|(?:0{1,4}:){1,4}:|(?:0{1,4}:){5})ffff:(\d+\.\d+\.\d+\.\d+)$/i;
var IPV4_MAPPED_HEX_RE = /^(?:::|(?:0{1,4}:){1,4}:|(?:0{1,4}:){5})ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/i;
var unmapIPv4MappedIPv6 = (host) => {
  if (typeof host !== "string" || host.indexOf(":") === -1) return host;
  const dotted = host.match(IPV4_MAPPED_DOTTED_RE);
  if (dotted) return dotted[1];
  const hex = host.match(IPV4_MAPPED_HEX_RE);
  if (hex) {
    const high = parseInt(hex[1], 16);
    const low = parseInt(hex[2], 16);
    return `${high >> 8}.${high & 255}.${low >> 8}.${low & 255}`;
  }
  return host;
};
var normalizeNoProxyHost = (hostname) => {
  if (!hostname) {
    return hostname;
  }
  if (hostname.charAt(0) === "[" && hostname.charAt(hostname.length - 1) === "]") {
    hostname = hostname.slice(1, -1);
  }
  return unmapIPv4MappedIPv6(hostname.replace(/\.+$/, ""));
};
function shouldBypassProxy(location) {
  let parsed;
  try {
    parsed = new URL(location);
  } catch (_err) {
    return false;
  }
  const noProxy = (process.env.no_proxy || process.env.NO_PROXY || "").toLowerCase();
  if (!noProxy) {
    return false;
  }
  if (noProxy === "*") {
    return true;
  }
  const port = Number.parseInt(parsed.port, 10) || DEFAULT_PORTS2[parsed.protocol.split(":", 1)[0]] || 0;
  const hostname = normalizeNoProxyHost(parsed.hostname.toLowerCase());
  return noProxy.split(/[\s,]+/).some((entry) => {
    if (!entry) {
      return false;
    }
    let [entryHost, entryPort] = parseNoProxyEntry(entry);
    entryHost = normalizeNoProxyHost(entryHost);
    if (!entryHost) {
      return false;
    }
    if (entryPort && entryPort !== port) {
      return false;
    }
    if (entryHost.charAt(0) === "*") {
      entryHost = entryHost.slice(1);
    }
    if (entryHost.charAt(0) === ".") {
      return hostname.endsWith(entryHost);
    }
    return hostname === entryHost || isLoopback(hostname) && isLoopback(entryHost);
  });
}

// ../shared/core/node_modules/axios/lib/helpers/speedometer.js
function speedometer(samplesCount, min) {
  samplesCount = samplesCount || 10;
  const bytes = new Array(samplesCount);
  const timestamps = new Array(samplesCount);
  let head = 0;
  let tail = 0;
  let firstSampleTS;
  min = min !== void 0 ? min : 1e3;
  return function push(chunkLength) {
    const now = Date.now();
    const startedAt = timestamps[tail];
    if (!firstSampleTS) {
      firstSampleTS = now;
    }
    bytes[head] = chunkLength;
    timestamps[head] = now;
    let i = tail;
    let bytesCount = 0;
    while (i !== head) {
      bytesCount += bytes[i++];
      i = i % samplesCount;
    }
    head = (head + 1) % samplesCount;
    if (head === tail) {
      tail = (tail + 1) % samplesCount;
    }
    if (now - firstSampleTS < min) {
      return;
    }
    const passed = startedAt && now - startedAt;
    return passed ? Math.round(bytesCount * 1e3 / passed) : void 0;
  };
}
var speedometer_default = speedometer;

// ../shared/core/node_modules/axios/lib/helpers/throttle.js
function throttle(fn, freq) {
  let timestamp = 0;
  let threshold = 1e3 / freq;
  let lastArgs;
  let timer;
  const invoke = (args, now = Date.now()) => {
    timestamp = now;
    lastArgs = null;
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    fn(...args);
  };
  const throttled = (...args) => {
    const now = Date.now();
    const passed = now - timestamp;
    if (passed >= threshold) {
      invoke(args, now);
    } else {
      lastArgs = args;
      if (!timer) {
        timer = setTimeout(() => {
          timer = null;
          invoke(lastArgs);
        }, threshold - passed);
      }
    }
  };
  const flush = () => lastArgs && invoke(lastArgs);
  return [throttled, flush];
}
var throttle_default = throttle;

// ../shared/core/node_modules/axios/lib/helpers/progressEventReducer.js
var progressEventReducer = (listener, isDownloadStream, freq = 3) => {
  let bytesNotified = 0;
  const _speedometer = speedometer_default(50, 250);
  return throttle_default((e) => {
    if (!e || typeof e.loaded !== "number") {
      return;
    }
    const rawLoaded = e.loaded;
    const total = e.lengthComputable ? e.total : void 0;
    const loaded = total != null ? Math.min(rawLoaded, total) : rawLoaded;
    const progressBytes = Math.max(0, loaded - bytesNotified);
    const rate = _speedometer(progressBytes);
    bytesNotified = Math.max(bytesNotified, loaded);
    const data = {
      loaded,
      total,
      progress: total ? loaded / total : void 0,
      bytes: progressBytes,
      rate: rate ? rate : void 0,
      estimated: rate && total ? (total - loaded) / rate : void 0,
      event: e,
      lengthComputable: total != null,
      [isDownloadStream ? "download" : "upload"]: true
    };
    listener(data);
  }, freq);
};
var progressEventDecorator = (total, throttled) => {
  const lengthComputable = total != null;
  return [
    (loaded) => throttled[0]({
      lengthComputable,
      total,
      loaded
    }),
    throttled[1]
  ];
};
var asyncDecorator = (fn) => (...args) => utils_default.asap(() => fn(...args));

// ../shared/core/node_modules/axios/lib/helpers/estimateDataURLDecodedBytes.js
var isHexDigit = (charCode) => charCode >= 48 && charCode <= 57 || charCode >= 65 && charCode <= 70 || charCode >= 97 && charCode <= 102;
var isPercentEncodedByte = (str, i, len) => i + 2 < len && isHexDigit(str.charCodeAt(i + 1)) && isHexDigit(str.charCodeAt(i + 2));
function estimateDataURLDecodedBytes(url2) {
  if (!url2 || typeof url2 !== "string") return 0;
  if (!url2.startsWith("data:")) return 0;
  const comma = url2.indexOf(",");
  if (comma < 0) return 0;
  const meta = url2.slice(5, comma);
  const body = url2.slice(comma + 1);
  const isBase64 = /;base64/i.test(meta);
  if (isBase64) {
    let effectiveLen = body.length;
    const len = body.length;
    for (let i = 0; i < len; i++) {
      if (body.charCodeAt(i) === 37 && i + 2 < len) {
        const a = body.charCodeAt(i + 1);
        const b = body.charCodeAt(i + 2);
        const isHex = isHexDigit(a) && isHexDigit(b);
        if (isHex) {
          effectiveLen -= 2;
          i += 2;
        }
      }
    }
    let pad = 0;
    let idx = len - 1;
    const tailIsPct3D = (j) => j >= 2 && body.charCodeAt(j - 2) === 37 && // '%'
    body.charCodeAt(j - 1) === 51 && // '3'
    (body.charCodeAt(j) === 68 || body.charCodeAt(j) === 100);
    if (idx >= 0) {
      if (body.charCodeAt(idx) === 61) {
        pad++;
        idx--;
      } else if (tailIsPct3D(idx)) {
        pad++;
        idx -= 3;
      }
    }
    if (pad === 1 && idx >= 0) {
      if (body.charCodeAt(idx) === 61) {
        pad++;
      } else if (tailIsPct3D(idx)) {
        pad++;
      }
    }
    const groups = Math.floor(effectiveLen / 4);
    const bytes2 = groups * 3 - (pad || 0);
    return bytes2 > 0 ? bytes2 : 0;
  }
  let bytes = 0;
  for (let i = 0, len = body.length; i < len; i++) {
    const c = body.charCodeAt(i);
    if (c === 37 && isPercentEncodedByte(body, i, len)) {
      bytes += 1;
      i += 2;
    } else if (c < 128) {
      bytes += 1;
    } else if (c < 2048) {
      bytes += 2;
    } else if (c >= 55296 && c <= 56319 && i + 1 < len) {
      const next = body.charCodeAt(i + 1);
      if (next >= 56320 && next <= 57343) {
        bytes += 4;
        i++;
      } else {
        bytes += 3;
      }
    } else {
      bytes += 3;
    }
  }
  return bytes;
}

// ../shared/core/node_modules/axios/lib/adapters/http.js
var zlibOptions = {
  flush: zlib.constants.Z_SYNC_FLUSH,
  finishFlush: zlib.constants.Z_SYNC_FLUSH
};
var brotliOptions = {
  flush: zlib.constants.BROTLI_OPERATION_FLUSH,
  finishFlush: zlib.constants.BROTLI_OPERATION_FLUSH
};
var zstdOptions = {
  flush: zlib.constants.ZSTD_e_flush,
  finishFlush: zlib.constants.ZSTD_e_flush
};
var isBrotliSupported = utils_default.isFunction(zlib.createBrotliDecompress);
var isZstdSupported = utils_default.isFunction(zlib.createZstdDecompress);
var ACCEPT_ENCODING = "gzip, compress, deflate" + (isBrotliSupported ? ", br" : "");
var ACCEPT_ENCODING_WITH_ZSTD = ACCEPT_ENCODING + (isZstdSupported ? ", zstd" : "");
var { http: httpFollow, https: httpsFollow } = import_follow_redirects.default;
var isHttps = /https:?/;
var FORM_DATA_CONTENT_HEADERS = ["content-type", "content-length"];
function setFormDataHeaders(headers, formHeaders, policy) {
  if (policy !== "content-only") {
    headers.set(formHeaders);
    return;
  }
  Object.entries(formHeaders).forEach(([key, val]) => {
    if (FORM_DATA_CONTENT_HEADERS.includes(key.toLowerCase())) {
      headers.set(key, val);
    }
  });
}
var kAxiosSocketListener = Symbol("axios.http.socketListener");
var kAxiosCurrentReq = Symbol("axios.http.currentReq");
var kAxiosInstalledTunnel = Symbol("axios.http.installedTunnel");
var tunnelingAgentCache = /* @__PURE__ */ new Map();
var tunnelingAgentCacheUser = /* @__PURE__ */ new WeakMap();
function getTunnelingAgent(agentOptions, userHttpsAgent) {
  const key = agentOptions.protocol + "//" + agentOptions.hostname + ":" + (agentOptions.port || "") + "#" + (agentOptions.auth || "");
  const cache = userHttpsAgent ? tunnelingAgentCacheUser.get(userHttpsAgent) || tunnelingAgentCacheUser.set(userHttpsAgent, /* @__PURE__ */ new Map()).get(userHttpsAgent) : tunnelingAgentCache;
  let agent = cache.get(key);
  if (agent) return agent;
  const merged = userHttpsAgent && userHttpsAgent.options ? { ...userHttpsAgent.options, ...agentOptions } : agentOptions;
  agent = new import_https_proxy_agent.default(merged);
  if (userHttpsAgent && userHttpsAgent.options) {
    const originTLSOptions = { ...userHttpsAgent.options };
    const callback = agent.callback;
    agent.callback = function axiosTunnelingAgentCallback(req, opts) {
      return callback.call(this, req, { ...originTLSOptions, ...opts });
    };
  }
  agent[kAxiosInstalledTunnel] = true;
  cache.set(key, agent);
  return agent;
}
var supportedProtocols = platform_default.protocols.map((protocol) => {
  return protocol + ":";
});
var decodeURIComponentSafe = (value) => {
  if (!utils_default.isString(value)) {
    return value;
  }
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
};
var flushOnFinish = (stream4, [throttled, flush]) => {
  stream4.on("end", flush).on("error", flush);
  return throttled;
};
var http2Sessions = new Http2Sessions_default();
function dispatchBeforeRedirect(options, responseDetails, requestDetails) {
  if (options.beforeRedirects.proxy) {
    options.beforeRedirects.proxy(options);
  }
  if (options.beforeRedirects.auth) {
    options.beforeRedirects.auth(options);
  }
  if (options.beforeRedirects.sensitiveHeaders) {
    options.beforeRedirects.sensitiveHeaders(options, requestDetails);
  }
  if (options.beforeRedirects.config) {
    options.beforeRedirects.config(options, responseDetails, requestDetails);
  }
}
function stripMatchingHeaders(headers, sensitiveSet) {
  if (!headers) {
    return;
  }
  Object.keys(headers).forEach((header) => {
    if (sensitiveSet.has(header.toLowerCase())) {
      delete headers[header];
    }
  });
}
function isSameOriginRedirect(redirectOptions, requestDetails) {
  if (!requestDetails) {
    return false;
  }
  try {
    return new URL(requestDetails.url).origin === new URL(redirectOptions.href).origin;
  } catch (e) {
    return false;
  }
}
function setProxy(options, configProxy, location, isRedirect, configHttpsAgent) {
  let proxy = configProxy;
  if (!proxy && proxy !== false) {
    const proxyUrl = getProxyForUrl(location);
    if (proxyUrl) {
      if (!shouldBypassProxy(location)) {
        proxy = new URL(proxyUrl);
      }
    }
  }
  if (isRedirect && options.headers) {
    for (const name of Object.keys(options.headers)) {
      if (name.toLowerCase() === "proxy-authorization") {
        delete options.headers[name];
      }
    }
  }
  if (isRedirect && options.agent && options.agent[kAxiosInstalledTunnel]) {
    options.agent = void 0;
  }
  if (proxy) {
    const isProxyURL = proxy instanceof URL;
    const readProxyField = (key) => isProxyURL || utils_default.hasOwnProp(proxy, key) ? proxy[key] : void 0;
    const proxyUsername = readProxyField("username");
    const proxyPassword = readProxyField("password");
    let proxyAuth = utils_default.hasOwnProp(proxy, "auth") ? proxy.auth : void 0;
    if (proxyUsername) {
      proxyAuth = (proxyUsername || "") + ":" + (proxyPassword || "");
    }
    if (proxyAuth) {
      const authIsObject = typeof proxyAuth === "object";
      const authUsername = authIsObject && utils_default.hasOwnProp(proxyAuth, "username") ? proxyAuth.username : void 0;
      const authPassword = authIsObject && utils_default.hasOwnProp(proxyAuth, "password") ? proxyAuth.password : void 0;
      const validProxyAuth = Boolean(authUsername || authPassword);
      if (validProxyAuth) {
        proxyAuth = (authUsername || "") + ":" + (authPassword || "");
      } else if (authIsObject) {
        throw new AxiosError_default("Invalid proxy authorization", AxiosError_default.ERR_BAD_OPTION, { proxy });
      }
    }
    const targetIsHttps = isHttps.test(options.protocol);
    if (targetIsHttps) {
      if (!(configHttpsAgent instanceof import_https_proxy_agent.default)) {
        const proxyHost = readProxyField("hostname") || readProxyField("host");
        const proxyPort = readProxyField("port");
        const rawProxyProtocol = readProxyField("protocol");
        const normalizedProtocol = rawProxyProtocol ? rawProxyProtocol.includes(":") ? rawProxyProtocol : `${rawProxyProtocol}:` : "http:";
        const proxyHostForURL = proxyHost && proxyHost.includes(":") && !proxyHost.startsWith("[") ? `[${proxyHost}]` : proxyHost;
        const proxyURL = new URL(
          `${normalizedProtocol}//${proxyHostForURL}${proxyPort ? ":" + proxyPort : ""}`
        );
        const agentOptions = {
          protocol: proxyURL.protocol,
          hostname: proxyURL.hostname.replace(/^\[|\]$/g, ""),
          port: proxyURL.port,
          auth: proxyAuth && typeof proxyAuth === "string" ? proxyAuth : void 0
        };
        if (proxyURL.protocol === "https:") {
          agentOptions.ALPNProtocols = ["http/1.1"];
        }
        const tunnelingAgent = getTunnelingAgent(agentOptions, configHttpsAgent);
        options.agent = tunnelingAgent;
        if (options.agents) {
          options.agents.https = tunnelingAgent;
        }
      }
    } else {
      if (proxyAuth) {
        const base64 = Buffer.from(proxyAuth, "utf8").toString("base64");
        options.headers["Proxy-Authorization"] = "Basic " + base64;
      }
      let hasUserHostHeader = false;
      for (const name of Object.keys(options.headers)) {
        if (name.toLowerCase() === "host") {
          hasUserHostHeader = true;
          break;
        }
      }
      if (!hasUserHostHeader) {
        options.headers.host = options.hostname + (options.port ? ":" + options.port : "");
      }
      const proxyHost = readProxyField("hostname") || readProxyField("host");
      options.hostname = proxyHost;
      options.host = proxyHost;
      options.port = readProxyField("port");
      options.path = location;
      const proxyProtocol = readProxyField("protocol");
      if (proxyProtocol) {
        options.protocol = proxyProtocol.includes(":") ? proxyProtocol : `${proxyProtocol}:`;
      }
    }
  }
  options.beforeRedirects.proxy = function beforeRedirect(redirectOptions) {
    setProxy(redirectOptions, configProxy, redirectOptions.href, true, configHttpsAgent);
  };
}
var isHttpAdapterSupported = typeof process !== "undefined" && utils_default.kindOf(process) === "process";
var wrapAsync = (asyncExecutor) => {
  return new Promise((resolve, reject) => {
    let onDone;
    let isDone;
    const done = (value, isRejected) => {
      if (isDone) return;
      isDone = true;
      onDone && onDone(value, isRejected);
    };
    const _resolve = (value) => {
      done(value);
      resolve(value);
    };
    const _reject = (reason) => {
      done(reason, true);
      reject(reason);
    };
    asyncExecutor(_resolve, _reject, (onDoneHandler) => onDone = onDoneHandler).catch(_reject);
  });
};
var resolveFamily = ({ address, family }) => {
  if (!utils_default.isString(address)) {
    throw TypeError("address must be a string");
  }
  return {
    address,
    family: family || (address.indexOf(".") < 0 ? 6 : 4)
  };
};
var buildAddressEntry = (address, family) => resolveFamily(utils_default.isObject(address) ? address : { address, family });
var http2Transport = {
  request(options, cb) {
    const authority = options.protocol + "//" + options.hostname + ":" + (options.port || (options.protocol === "https:" ? 443 : 80));
    const { http2Options, headers } = options;
    const session = http2Sessions.getSession(authority, http2Options);
    const { HTTP2_HEADER_SCHEME, HTTP2_HEADER_METHOD, HTTP2_HEADER_PATH, HTTP2_HEADER_STATUS } = http22.constants;
    const http2Headers = {
      [HTTP2_HEADER_SCHEME]: options.protocol.replace(":", ""),
      [HTTP2_HEADER_METHOD]: options.method,
      [HTTP2_HEADER_PATH]: options.path
    };
    utils_default.forEach(headers, (header, name) => {
      name.charAt(0) !== ":" && (http2Headers[name] = header);
    });
    const req = session.request(http2Headers);
    req.once("response", (responseHeaders) => {
      const response = req;
      responseHeaders = Object.assign({}, responseHeaders);
      const status = responseHeaders[HTTP2_HEADER_STATUS];
      delete responseHeaders[HTTP2_HEADER_STATUS];
      response.headers = responseHeaders;
      response.statusCode = +status;
      cb(response);
    });
    return req;
  }
};
var http_default = isHttpAdapterSupported && function httpAdapter(config) {
  return wrapAsync(async function dispatchHttpRequest(resolve, reject, onDone) {
    const own2 = (key) => utils_default.getSafeProp(config, key);
    const transitional2 = own2("transitional") || transitional_default;
    let data = own2("data");
    let lookup = own2("lookup");
    let family = own2("family");
    let httpVersion = own2("httpVersion");
    if (httpVersion === void 0) httpVersion = 1;
    let http2Options = own2("http2Options");
    const responseType = own2("responseType");
    const responseEncoding = own2("responseEncoding");
    const httpAgent = own2("httpAgent");
    const httpsAgent = own2("httpsAgent");
    const method = own2("method").toUpperCase();
    const maxRedirects = own2("maxRedirects");
    const maxBodyLength = own2("maxBodyLength");
    const maxContentLength = own2("maxContentLength");
    const decompress = own2("decompress");
    let isDone;
    let rejected = false;
    let req;
    let connectPhaseTimer;
    httpVersion = +httpVersion;
    if (Number.isNaN(httpVersion)) {
      throw TypeError(`Invalid protocol version: '${config.httpVersion}' is not a number`);
    }
    if (httpVersion !== 1 && httpVersion !== 2) {
      throw TypeError(`Unsupported protocol version '${httpVersion}'`);
    }
    const isHttp2 = httpVersion === 2;
    if (lookup) {
      const _lookup = callbackify_default(lookup, (value) => utils_default.isArray(value) ? value : [value]);
      lookup = (hostname, opt, cb) => {
        _lookup(hostname, opt, (err, arg0, arg1) => {
          if (err) {
            return cb(err);
          }
          const addresses = utils_default.isArray(arg0) ? arg0.map((addr) => buildAddressEntry(addr)) : [buildAddressEntry(arg0, arg1)];
          opt.all ? cb(err, addresses) : cb(err, addresses[0].address, addresses[0].family);
        });
      };
    }
    const abortEmitter = new EventEmitter();
    function abort(reason) {
      try {
        abortEmitter.emit(
          "abort",
          !reason || reason.type ? new CanceledError_default(null, config, req) : reason
        );
      } catch (err) {
      }
    }
    function clearConnectPhaseTimer() {
      if (connectPhaseTimer) {
        clearTimeout(connectPhaseTimer);
        connectPhaseTimer = null;
      }
    }
    function createTimeoutError() {
      const configTimeout = own2("timeout");
      let timeoutErrorMessage = configTimeout ? "timeout of " + configTimeout + "ms exceeded" : "timeout exceeded";
      const configTimeoutErrorMessage = own2("timeoutErrorMessage");
      if (configTimeoutErrorMessage) {
        timeoutErrorMessage = configTimeoutErrorMessage;
      }
      return new AxiosError_default(
        timeoutErrorMessage,
        transitional2.clarifyTimeoutError ? AxiosError_default.ETIMEDOUT : AxiosError_default.ECONNABORTED,
        config,
        req
      );
    }
    abortEmitter.once("abort", reject);
    const onFinished = () => {
      clearConnectPhaseTimer();
      if (config.cancelToken) {
        config.cancelToken.unsubscribe(abort);
      }
      if (config.signal) {
        config.signal.removeEventListener("abort", abort);
      }
      abortEmitter.removeAllListeners();
    };
    if (config.cancelToken || config.signal) {
      config.cancelToken && config.cancelToken.subscribe(abort);
      if (config.signal) {
        config.signal.aborted ? abort() : config.signal.addEventListener("abort", abort);
      }
    }
    onDone((response, isRejected) => {
      isDone = true;
      clearConnectPhaseTimer();
      if (isRejected) {
        rejected = true;
        onFinished();
        return;
      }
      const { data: data2 } = response;
      if (data2 instanceof stream3.Readable || data2 instanceof stream3.Duplex) {
        const offListeners = stream3.finished(data2, () => {
          offListeners();
          onFinished();
        });
      } else {
        onFinished();
      }
    });
    const fullPath = buildFullPath(own2("baseURL"), own2("url"), own2("allowAbsoluteUrls"), config);
    const parsed = new URL(fullPath, platform_default.hasBrowserEnv ? platform_default.origin : void 0);
    const protocol = parsed.protocol || supportedProtocols[0];
    if (protocol === "data:") {
      if (maxContentLength > -1) {
        const dataUrl = String(own2("url") || fullPath || "");
        const estimated = estimateDataURLDecodedBytes(dataUrl);
        if (estimated > maxContentLength) {
          return reject(
            new AxiosError_default(
              "maxContentLength size of " + maxContentLength + " exceeded",
              AxiosError_default.ERR_BAD_RESPONSE,
              config
            )
          );
        }
      }
      let convertedData;
      if (method !== "GET") {
        return settle(resolve, reject, {
          status: 405,
          statusText: "method not allowed",
          headers: {},
          config
        });
      }
      try {
        convertedData = fromDataURI(own2("url"), responseType === "blob", {
          Blob: config.env && config.env.Blob
        });
      } catch (err) {
        throw AxiosError_default.from(err, AxiosError_default.ERR_BAD_REQUEST, config);
      }
      if (responseType === "text") {
        convertedData = convertedData.toString(responseEncoding);
        if (!responseEncoding || responseEncoding === "utf8") {
          convertedData = utils_default.stripBOM(convertedData);
        }
      } else if (responseType === "stream") {
        convertedData = stream3.Readable.from(convertedData);
      }
      return settle(resolve, reject, {
        data: convertedData,
        status: 200,
        statusText: "OK",
        headers: new AxiosHeaders_default(),
        config
      });
    }
    if (supportedProtocols.indexOf(protocol) === -1) {
      return reject(
        new AxiosError_default("Unsupported protocol " + protocol, AxiosError_default.ERR_BAD_REQUEST, config)
      );
    }
    const headers = AxiosHeaders_default.from(config.headers).normalize();
    headers.set("User-Agent", "axios/" + VERSION, false);
    const { onUploadProgress, onDownloadProgress } = config;
    const maxRate = config.maxRate;
    let maxUploadRate = void 0;
    let maxDownloadRate = void 0;
    if (utils_default.isSpecCompliantForm(data)) {
      const userBoundary = headers.getContentType(/boundary=([-_\w\d]{10,70})/i);
      data = formDataToStream_default(
        data,
        (formHeaders) => {
          headers.set(formHeaders);
        },
        {
          tag: `axios-${VERSION}-boundary`,
          boundary: userBoundary && userBoundary[1] || void 0
        }
      );
    } else if (utils_default.isFormData(data) && utils_default.isFunction(data.getHeaders) && data.getHeaders !== Object.prototype.getHeaders) {
      setFormDataHeaders(headers, data.getHeaders(), own2("formDataHeaderPolicy"));
      if (!headers.hasContentLength()) {
        try {
          const knownLength = await util3.promisify(data.getLength).call(data);
          Number.isFinite(knownLength) && knownLength >= 0 && headers.setContentLength(knownLength);
        } catch (e) {
        }
      }
    } else if (utils_default.isBlob(data) || utils_default.isFile(data)) {
      data.size && headers.setContentType(data.type || "application/octet-stream");
      headers.setContentLength(data.size || 0);
      data = stream3.Readable.from(readBlob_default(data));
    } else if (data && !utils_default.isStream(data)) {
      if (Buffer.isBuffer(data)) {
      } else if (utils_default.isArrayBuffer(data)) {
        data = Buffer.from(new Uint8Array(data));
      } else if (utils_default.isString(data)) {
        data = Buffer.from(data, "utf-8");
      } else {
        return reject(
          new AxiosError_default(
            "Data after transformation must be a string, an ArrayBuffer, a Buffer, or a Stream",
            AxiosError_default.ERR_BAD_REQUEST,
            config
          )
        );
      }
      headers.setContentLength(data.length, false);
      if (maxBodyLength > -1 && data.length > maxBodyLength) {
        return reject(
          new AxiosError_default(
            "Request body larger than maxBodyLength limit",
            AxiosError_default.ERR_BAD_REQUEST,
            config
          )
        );
      }
    }
    const contentLength = utils_default.toFiniteNumber(headers.getContentLength());
    if (utils_default.isArray(maxRate)) {
      maxUploadRate = maxRate[0];
      maxDownloadRate = maxRate[1];
    } else {
      maxUploadRate = maxDownloadRate = maxRate;
    }
    if (data && (onUploadProgress || maxUploadRate)) {
      if (!utils_default.isStream(data)) {
        data = stream3.Readable.from(data, { objectMode: false });
      }
      data = stream3.pipeline(
        [
          data,
          new AxiosTransformStream_default({
            maxRate: utils_default.toFiniteNumber(maxUploadRate)
          })
        ],
        utils_default.noop
      );
      onUploadProgress && data.on(
        "progress",
        flushOnFinish(
          data,
          progressEventDecorator(
            contentLength,
            progressEventReducer(asyncDecorator(onUploadProgress), false, 3)
          )
        )
      );
    }
    let auth = void 0;
    const configAuth = own2("auth");
    if (configAuth) {
      const username = utils_default.getSafeProp(configAuth, "username") || "";
      const password = utils_default.getSafeProp(configAuth, "password") || "";
      auth = username + ":" + password;
    }
    if (!auth && (parsed.username || parsed.password)) {
      const urlUsername = decodeURIComponentSafe(parsed.username);
      const urlPassword = decodeURIComponentSafe(parsed.password);
      auth = urlUsername + ":" + urlPassword;
    }
    auth && headers.delete("authorization");
    let path2;
    try {
      path2 = buildURL(
        parsed.pathname + parsed.search,
        own2("params"),
        own2("paramsSerializer")
      ).replace(/^\?/, "");
    } catch (err) {
      const customErr = new Error(err.message);
      customErr.config = config;
      customErr.url = own2("url");
      customErr.exists = true;
      return reject(customErr);
    }
    headers.set(
      "Accept-Encoding",
      utils_default.hasOwnProp(transitional2, "advertiseZstdAcceptEncoding") && transitional2.advertiseZstdAcceptEncoding === true ? ACCEPT_ENCODING_WITH_ZSTD : ACCEPT_ENCODING,
      false
    );
    const options = Object.assign(/* @__PURE__ */ Object.create(null), {
      path: path2,
      method,
      headers: toByteStringHeaderObject(headers),
      agents: { http: httpAgent, https: httpsAgent },
      auth,
      protocol,
      family,
      beforeRedirect: dispatchBeforeRedirect,
      beforeRedirects: /* @__PURE__ */ Object.create(null),
      http2Options
    });
    !utils_default.isUndefined(lookup) && (options.lookup = lookup);
    const socketPath = own2("socketPath");
    if (socketPath) {
      if (typeof socketPath !== "string") {
        return reject(
          new AxiosError_default("socketPath must be a string", AxiosError_default.ERR_BAD_OPTION_VALUE, config)
        );
      }
      const allowedSocketPaths = own2("allowedSocketPaths");
      if (allowedSocketPaths != null) {
        const allowed = Array.isArray(allowedSocketPaths) ? allowedSocketPaths : [allowedSocketPaths];
        const resolvedSocket = resolvePath(socketPath);
        const isAllowed = allowed.some(
          (entry) => typeof entry === "string" && resolvePath(entry) === resolvedSocket
        );
        if (!isAllowed) {
          return reject(
            new AxiosError_default(
              `socketPath "${socketPath}" is not permitted by allowedSocketPaths`,
              AxiosError_default.ERR_BAD_OPTION_VALUE,
              config
            )
          );
        }
      }
      options.socketPath = socketPath;
    } else {
      options.hostname = parsed.hostname.startsWith("[") ? parsed.hostname.slice(1, -1) : parsed.hostname;
      options.port = parsed.port;
      setProxy(
        options,
        own2("proxy"),
        protocol + "//" + parsed.hostname + (parsed.port ? ":" + parsed.port : "") + options.path,
        false,
        httpsAgent
      );
    }
    let transport;
    let isNativeTransport = false;
    let transportEnforcesMaxBodyLength = false;
    const isHttpsRequest = isHttps.test(options.protocol);
    if (options.agent == null) {
      options.agent = isHttpsRequest ? httpsAgent : httpAgent;
    }
    if (isHttp2) {
      transport = http2Transport;
    } else {
      const configTransport = own2("transport");
      if (configTransport) {
        transport = configTransport;
      } else if (maxRedirects === 0) {
        transport = isHttpsRequest ? https : http;
        isNativeTransport = true;
      } else {
        transportEnforcesMaxBodyLength = true;
        options.sensitiveHeaders = [];
        if (maxRedirects) {
          options.maxRedirects = maxRedirects;
        }
        const configBeforeRedirect = own2("beforeRedirect");
        if (configBeforeRedirect) {
          options.beforeRedirects.config = configBeforeRedirect;
        }
        if (auth) {
          const requestOrigin = parsed.origin;
          const authToRestore = auth;
          options.beforeRedirects.auth = function beforeRedirectAuth(redirectOptions) {
            try {
              if (new URL(redirectOptions.href).origin === requestOrigin) {
                redirectOptions.auth = authToRestore;
              }
            } catch (e) {
            }
          };
        }
        const sensitiveHeaders = own2("sensitiveHeaders");
        if (sensitiveHeaders != null) {
          if (!utils_default.isArray(sensitiveHeaders)) {
            return reject(
              new AxiosError_default(
                "sensitiveHeaders must be an array of strings",
                AxiosError_default.ERR_BAD_OPTION_VALUE,
                config
              )
            );
          }
          const sensitiveSet = /* @__PURE__ */ new Set();
          for (const header of sensitiveHeaders) {
            if (!utils_default.isString(header)) {
              return reject(
                new AxiosError_default(
                  "sensitiveHeaders must be an array of strings",
                  AxiosError_default.ERR_BAD_OPTION_VALUE,
                  config
                )
              );
            }
            sensitiveSet.add(header.toLowerCase());
          }
          if (sensitiveSet.size) {
            options.sensitiveHeaders = Array.from(sensitiveSet);
            options.beforeRedirects.sensitiveHeaders = function beforeRedirectSensitiveHeaders(redirectOptions, requestDetails) {
              if (!isSameOriginRedirect(redirectOptions, requestDetails)) {
                stripMatchingHeaders(redirectOptions.headers, sensitiveSet);
              }
            };
          }
        }
        transport = isHttpsRequest ? httpsFollow : httpFollow;
      }
    }
    if (maxBodyLength > -1) {
      options.maxBodyLength = maxBodyLength;
    } else {
      options.maxBodyLength = Infinity;
    }
    options.insecureHTTPParser = Boolean(own2("insecureHTTPParser"));
    req = transport.request(options, function handleResponse(res) {
      clearConnectPhaseTimer();
      if (req.destroyed) return;
      const streams = [res];
      const responseLength = utils_default.toFiniteNumber(res.headers["content-length"]);
      if (onDownloadProgress || maxDownloadRate) {
        const transformStream = new AxiosTransformStream_default({
          maxRate: utils_default.toFiniteNumber(maxDownloadRate)
        });
        onDownloadProgress && transformStream.on(
          "progress",
          flushOnFinish(
            transformStream,
            progressEventDecorator(
              responseLength,
              progressEventReducer(asyncDecorator(onDownloadProgress), true, 3)
            )
          )
        );
        streams.push(transformStream);
      }
      let responseStream = res;
      const lastRequest = res.req || req;
      if (decompress !== false && res.headers["content-encoding"]) {
        if (method === "HEAD" || res.statusCode === 204) {
          delete res.headers["content-encoding"];
        }
        switch ((res.headers["content-encoding"] || "").toLowerCase()) {
          /*eslint default-case:0*/
          case "gzip":
          case "x-gzip":
          case "compress":
          case "x-compress":
            streams.push(zlib.createUnzip(zlibOptions));
            delete res.headers["content-encoding"];
            break;
          case "deflate":
            streams.push(new ZlibHeaderTransformStream_default());
            streams.push(zlib.createUnzip(zlibOptions));
            delete res.headers["content-encoding"];
            break;
          case "br":
            if (isBrotliSupported) {
              streams.push(zlib.createBrotliDecompress(brotliOptions));
              delete res.headers["content-encoding"];
            }
            break;
          case "zstd":
            if (isZstdSupported) {
              streams.push(zlib.createZstdDecompress(zstdOptions));
              delete res.headers["content-encoding"];
            }
            break;
        }
      }
      responseStream = streams.length > 1 ? stream3.pipeline(streams, utils_default.noop) : streams[0];
      const response = {
        status: res.statusCode,
        statusText: res.statusMessage,
        headers: new AxiosHeaders_default(res.headers),
        config,
        request: lastRequest
      };
      if (responseType === "stream") {
        if (maxContentLength > -1) {
          const limit = maxContentLength;
          const source = responseStream;
          async function* enforceMaxContentLength() {
            let totalResponseBytes = 0;
            for await (const chunk of source) {
              totalResponseBytes += chunk.length;
              if (totalResponseBytes > limit) {
                throw new AxiosError_default(
                  "maxContentLength size of " + limit + " exceeded",
                  AxiosError_default.ERR_BAD_RESPONSE,
                  config,
                  lastRequest
                );
              }
              yield chunk;
            }
          }
          responseStream = stream3.Readable.from(enforceMaxContentLength(), {
            objectMode: false
          });
        }
        response.data = responseStream;
        settle(resolve, reject, response);
      } else {
        const responseBuffer = [];
        let totalResponseBytes = 0;
        responseStream.on("data", function handleStreamData(chunk) {
          responseBuffer.push(chunk);
          totalResponseBytes += chunk.length;
          if (maxContentLength > -1 && totalResponseBytes > maxContentLength) {
            rejected = true;
            responseStream.destroy();
            abort(
              new AxiosError_default(
                "maxContentLength size of " + maxContentLength + " exceeded",
                AxiosError_default.ERR_BAD_RESPONSE,
                config,
                lastRequest
              )
            );
          }
        });
        responseStream.on("aborted", function handlerStreamAborted() {
          if (rejected) {
            return;
          }
          const err = new AxiosError_default(
            "stream has been aborted",
            AxiosError_default.ERR_BAD_RESPONSE,
            config,
            lastRequest,
            response
          );
          responseStream.destroy(err);
          reject(err);
        });
        responseStream.on("error", function handleStreamError(err) {
          if (rejected) return;
          reject(AxiosError_default.from(err, null, config, lastRequest, response));
        });
        responseStream.on("end", function handleStreamEnd() {
          try {
            let responseData = responseBuffer.length === 1 ? responseBuffer[0] : Buffer.concat(responseBuffer);
            if (responseType !== "arraybuffer") {
              responseData = responseData.toString(responseEncoding);
              if (!responseEncoding || responseEncoding === "utf8") {
                responseData = utils_default.stripBOM(responseData);
              }
            }
            response.data = responseData;
          } catch (err) {
            return reject(AxiosError_default.from(err, null, config, response.request, response));
          }
          settle(resolve, reject, response);
        });
      }
      abortEmitter.once("abort", (err) => {
        if (!responseStream.destroyed) {
          responseStream.emit("error", err);
          responseStream.destroy();
        }
      });
    });
    abortEmitter.once("abort", (err) => {
      if (req.close) {
        req.close();
      } else {
        req.destroy(err);
      }
    });
    req.on("error", function handleRequestError(err) {
      reject(AxiosError_default.from(err, null, config, req));
    });
    const boundSockets = /* @__PURE__ */ new Set();
    req.on("socket", function handleRequestSocket(socket) {
      socket.setKeepAlive(true, 1e3 * 60);
      if (!socket[kAxiosSocketListener]) {
        socket.on("error", function handleSocketError(err) {
          const current = socket[kAxiosCurrentReq];
          if (current && !current.destroyed) {
            current.destroy(err);
          }
        });
        socket[kAxiosSocketListener] = true;
      }
      socket[kAxiosCurrentReq] = req;
      boundSockets.add(socket);
    });
    req.once("close", function clearCurrentReq() {
      clearConnectPhaseTimer();
      for (const socket of boundSockets) {
        if (socket[kAxiosCurrentReq] === req) {
          socket[kAxiosCurrentReq] = null;
        }
      }
      boundSockets.clear();
    });
    if (own2("timeout")) {
      const timeout = parseInt(own2("timeout"), 10);
      if (Number.isNaN(timeout)) {
        abort(
          new AxiosError_default(
            "error trying to parse `config.timeout` to int",
            AxiosError_default.ERR_BAD_OPTION_VALUE,
            config,
            req
          )
        );
        return;
      }
      const handleTimeout = function handleTimeout2() {
        if (isDone) return;
        abort(createTimeoutError());
      };
      if (isNativeTransport && timeout > 0) {
        connectPhaseTimer = setTimeout(handleTimeout, timeout);
      }
      req.setTimeout(timeout, handleTimeout);
    } else {
      req.setTimeout(0);
    }
    if (utils_default.isStream(data)) {
      let ended = false;
      let errored = false;
      data.on("end", () => {
        ended = true;
      });
      data.once("error", (err) => {
        errored = true;
        req.destroy(err);
      });
      data.on("close", () => {
        if (!ended && !errored) {
          abort(new CanceledError_default("Request stream has been aborted", config, req));
        }
      });
      let uploadStream = data;
      if (maxBodyLength > -1 && !transportEnforcesMaxBodyLength) {
        const limit = maxBodyLength;
        let bytesSent = 0;
        uploadStream = stream3.pipeline(
          [
            data,
            new stream3.Transform({
              transform(chunk, _enc, cb) {
                bytesSent += chunk.length;
                if (bytesSent > limit) {
                  return cb(
                    new AxiosError_default(
                      "Request body larger than maxBodyLength limit",
                      AxiosError_default.ERR_BAD_REQUEST,
                      config,
                      req
                    )
                  );
                }
                cb(null, chunk);
              }
            })
          ],
          utils_default.noop
        );
        uploadStream.on("error", (err) => {
          if (!req.destroyed) req.destroy(err);
        });
      }
      uploadStream.pipe(req);
    } else {
      data && req.write(data);
      req.end();
    }
  });
};

// ../shared/core/node_modules/axios/lib/helpers/isURLSameOrigin.js
var isURLSameOrigin_default = platform_default.hasStandardBrowserEnv ? /* @__PURE__ */ ((origin2, isMSIE) => (url2) => {
  url2 = new URL(url2, platform_default.origin);
  return origin2.protocol === url2.protocol && origin2.host === url2.host && (isMSIE || origin2.port === url2.port);
})(
  new URL(platform_default.origin),
  platform_default.navigator && /(msie|trident)/i.test(platform_default.navigator.userAgent)
) : () => true;

// ../shared/core/node_modules/axios/lib/helpers/cookies.js
var cookies_default = platform_default.hasStandardBrowserEnv ? (
  // Standard browser envs support document.cookie
  {
    write(name, value, expires, path2, domain, secure, sameSite) {
      if (typeof document === "undefined") return;
      const cookie = [`${name}=${encodeURIComponent(value)}`];
      if (utils_default.isNumber(expires)) {
        cookie.push(`expires=${new Date(expires).toUTCString()}`);
      }
      if (utils_default.isString(path2)) {
        cookie.push(`path=${path2}`);
      }
      if (utils_default.isString(domain)) {
        cookie.push(`domain=${domain}`);
      }
      if (secure === true) {
        cookie.push("secure");
      }
      if (utils_default.isString(sameSite)) {
        cookie.push(`SameSite=${sameSite}`);
      }
      document.cookie = cookie.join("; ");
    },
    read(name) {
      if (typeof document === "undefined") return null;
      const cookies = document.cookie.split(";");
      for (let i = 0; i < cookies.length; i++) {
        const cookie = cookies[i].replace(/^\s+/, "");
        const eq = cookie.indexOf("=");
        if (eq !== -1 && cookie.slice(0, eq) === name) {
          return decodeURIComponent(cookie.slice(eq + 1));
        }
      }
      return null;
    },
    remove(name) {
      this.write(name, "", Date.now() - 864e5, "/");
    }
  }
) : (
  // Non-standard browser env (web workers, react-native) lack needed support.
  {
    write() {
    },
    read() {
      return null;
    },
    remove() {
    }
  }
);

// ../shared/core/node_modules/axios/lib/core/mergeConfig.js
var headersToObject = (thing) => thing instanceof AxiosHeaders_default ? { ...thing } : thing;
function mergeConfig(config1, config2) {
  config2 = config2 || {};
  const config = /* @__PURE__ */ Object.create(null);
  Object.defineProperty(config, "hasOwnProperty", {
    // Null-proto descriptor so a polluted Object.prototype.get cannot turn
    // this data descriptor into an accessor descriptor on the way in.
    __proto__: null,
    value: Object.prototype.hasOwnProperty,
    enumerable: false,
    writable: true,
    configurable: true
  });
  function getMergedValue(target, source, prop, caseless) {
    if (utils_default.isPlainObject(target) && utils_default.isPlainObject(source)) {
      return utils_default.merge.call({ caseless }, target, source);
    } else if (utils_default.isPlainObject(source)) {
      return utils_default.merge({}, source);
    } else if (utils_default.isArray(source)) {
      return source.slice();
    }
    return source;
  }
  function mergeDeepProperties(a, b, prop, caseless) {
    if (!utils_default.isUndefined(b)) {
      return getMergedValue(a, b, prop, caseless);
    } else if (!utils_default.isUndefined(a)) {
      return getMergedValue(void 0, a, prop, caseless);
    }
  }
  function valueFromConfig2(a, b) {
    if (!utils_default.isUndefined(b)) {
      return getMergedValue(void 0, b);
    }
  }
  function defaultToConfig2(a, b) {
    if (!utils_default.isUndefined(b)) {
      return getMergedValue(void 0, b);
    } else if (!utils_default.isUndefined(a)) {
      return getMergedValue(void 0, a);
    }
  }
  function getMergedTransitionalOption(prop) {
    const transitional2 = utils_default.hasOwnProp(config2, "transitional") ? config2.transitional : void 0;
    if (!utils_default.isUndefined(transitional2)) {
      if (utils_default.isPlainObject(transitional2)) {
        if (utils_default.hasOwnProp(transitional2, prop)) {
          return transitional2[prop];
        }
      } else {
        return void 0;
      }
    }
    const transitional1 = utils_default.hasOwnProp(config1, "transitional") ? config1.transitional : void 0;
    if (utils_default.isPlainObject(transitional1) && utils_default.hasOwnProp(transitional1, prop)) {
      return transitional1[prop];
    }
    return void 0;
  }
  function mergeDirectKeys(a, b, prop) {
    if (utils_default.hasOwnProp(config2, prop)) {
      return getMergedValue(a, b);
    } else if (utils_default.hasOwnProp(config1, prop)) {
      return getMergedValue(void 0, a);
    }
  }
  const mergeMap = {
    url: valueFromConfig2,
    method: valueFromConfig2,
    data: valueFromConfig2,
    baseURL: defaultToConfig2,
    transformRequest: defaultToConfig2,
    transformResponse: defaultToConfig2,
    paramsSerializer: defaultToConfig2,
    timeout: defaultToConfig2,
    timeoutMessage: defaultToConfig2,
    withCredentials: defaultToConfig2,
    withXSRFToken: defaultToConfig2,
    adapter: defaultToConfig2,
    responseType: defaultToConfig2,
    xsrfCookieName: defaultToConfig2,
    xsrfHeaderName: defaultToConfig2,
    onUploadProgress: defaultToConfig2,
    onDownloadProgress: defaultToConfig2,
    decompress: defaultToConfig2,
    maxContentLength: defaultToConfig2,
    maxBodyLength: defaultToConfig2,
    beforeRedirect: defaultToConfig2,
    transport: defaultToConfig2,
    httpAgent: defaultToConfig2,
    httpsAgent: defaultToConfig2,
    cancelToken: defaultToConfig2,
    socketPath: defaultToConfig2,
    allowedSocketPaths: defaultToConfig2,
    responseEncoding: defaultToConfig2,
    validateStatus: mergeDirectKeys,
    headers: (a, b, prop) => mergeDeepProperties(headersToObject(a), headersToObject(b), prop, true)
  };
  utils_default.forEach(Object.keys({ ...config1, ...config2 }), function computeConfigValue(prop) {
    if (prop === "__proto__" || prop === "constructor" || prop === "prototype") return;
    const merge2 = utils_default.hasOwnProp(mergeMap, prop) ? mergeMap[prop] : mergeDeepProperties;
    const a = utils_default.hasOwnProp(config1, prop) ? config1[prop] : void 0;
    const b = utils_default.hasOwnProp(config2, prop) ? config2[prop] : void 0;
    const configValue = merge2(a, b, prop);
    utils_default.isUndefined(configValue) && merge2 !== mergeDirectKeys || (config[prop] = configValue);
  });
  if (utils_default.hasOwnProp(config2, "validateStatus") && utils_default.isUndefined(config2.validateStatus) && getMergedTransitionalOption("validateStatusUndefinedResolves") === false) {
    if (utils_default.hasOwnProp(config1, "validateStatus")) {
      config.validateStatus = getMergedValue(void 0, config1.validateStatus);
    } else {
      delete config.validateStatus;
    }
  }
  return config;
}

// ../shared/core/node_modules/axios/lib/helpers/resolveConfig.js
var FORM_DATA_CONTENT_HEADERS2 = ["content-type", "content-length"];
function setFormDataHeaders2(headers, formHeaders, policy) {
  if (policy !== "content-only") {
    headers.set(formHeaders);
    return;
  }
  Object.entries(formHeaders).forEach(([key, val]) => {
    if (FORM_DATA_CONTENT_HEADERS2.includes(key.toLowerCase())) {
      headers.set(key, val);
    }
  });
}
var encodeUTF8 = (str) => encodeURIComponent(str).replace(
  /%([0-9A-F]{2})/gi,
  (_, hex) => String.fromCharCode(parseInt(hex, 16))
);
function resolveConfig(config) {
  const newConfig = mergeConfig({}, config);
  const own2 = (key) => utils_default.hasOwnProp(newConfig, key) ? newConfig[key] : void 0;
  const data = own2("data");
  let withXSRFToken = own2("withXSRFToken");
  const xsrfHeaderName = own2("xsrfHeaderName");
  const xsrfCookieName = own2("xsrfCookieName");
  let headers = own2("headers");
  const auth = own2("auth");
  const baseURL = own2("baseURL");
  const allowAbsoluteUrls = own2("allowAbsoluteUrls");
  const url2 = own2("url");
  newConfig.headers = headers = AxiosHeaders_default.from(headers);
  newConfig.url = buildURL(
    buildFullPath(baseURL, url2, allowAbsoluteUrls, newConfig),
    own2("params"),
    own2("paramsSerializer")
  );
  if (auth) {
    const username = utils_default.getSafeProp(auth, "username") || "";
    const password = utils_default.getSafeProp(auth, "password") || "";
    headers.set(
      "Authorization",
      "Basic " + btoa(username + ":" + (password ? encodeUTF8(password) : ""))
    );
  }
  if (utils_default.isFormData(data)) {
    if (platform_default.hasStandardBrowserEnv || platform_default.hasStandardBrowserWebWorkerEnv || utils_default.isReactNative(data)) {
      headers.setContentType(void 0);
    } else if (utils_default.isFunction(data.getHeaders)) {
      setFormDataHeaders2(headers, data.getHeaders(), own2("formDataHeaderPolicy"));
    }
  }
  if (platform_default.hasStandardBrowserEnv) {
    if (utils_default.isFunction(withXSRFToken)) {
      withXSRFToken = withXSRFToken(newConfig);
    }
    const shouldSendXSRF = withXSRFToken === true || withXSRFToken == null && isURLSameOrigin_default(newConfig.url);
    if (shouldSendXSRF) {
      const xsrfValue = xsrfHeaderName && xsrfCookieName && cookies_default.read(xsrfCookieName);
      if (xsrfValue) {
        headers.set(xsrfHeaderName, xsrfValue);
      }
    }
  }
  return newConfig;
}
var resolveConfig_default = resolveConfig;

// ../shared/core/node_modules/axios/lib/adapters/xhr.js
var isXHRAdapterSupported = typeof XMLHttpRequest !== "undefined";
var xhr_default = isXHRAdapterSupported && function(config) {
  return new Promise(function dispatchXhrRequest(resolve, reject) {
    const _config = resolveConfig_default(config);
    let requestData = _config.data;
    const requestHeaders = AxiosHeaders_default.from(_config.headers).normalize();
    let { responseType, onUploadProgress, onDownloadProgress } = _config;
    let onCanceled;
    let uploadThrottled, downloadThrottled;
    let flushUpload, flushDownload;
    function done() {
      flushUpload && flushUpload();
      flushDownload && flushDownload();
      _config.cancelToken && _config.cancelToken.unsubscribe(onCanceled);
      _config.signal && _config.signal.removeEventListener("abort", onCanceled);
    }
    let request = new XMLHttpRequest();
    request.open(_config.method.toUpperCase(), _config.url, true);
    request.timeout = _config.timeout;
    function onloadend() {
      if (!request) {
        return;
      }
      const responseHeaders = AxiosHeaders_default.from(
        "getAllResponseHeaders" in request && request.getAllResponseHeaders()
      );
      const responseData = !responseType || responseType === "text" || responseType === "json" ? request.responseText : request.response;
      const response = {
        data: responseData,
        status: request.status,
        statusText: request.statusText,
        headers: responseHeaders,
        config,
        request
      };
      settle(
        function _resolve(value) {
          resolve(value);
          done();
        },
        function _reject(err) {
          reject(err);
          done();
        },
        response
      );
      request = null;
    }
    if ("onloadend" in request) {
      request.onloadend = onloadend;
    } else {
      request.onreadystatechange = function handleLoad() {
        if (!request || request.readyState !== 4) {
          return;
        }
        if (request.status === 0 && !(request.responseURL && request.responseURL.startsWith("file:"))) {
          return;
        }
        setTimeout(onloadend);
      };
    }
    request.onabort = function handleAbort() {
      if (!request) {
        return;
      }
      reject(new AxiosError_default("Request aborted", AxiosError_default.ECONNABORTED, config, request));
      done();
      request = null;
    };
    request.onerror = function handleError(event) {
      const msg = event && event.message ? event.message : "Network Error";
      const err = new AxiosError_default(msg, AxiosError_default.ERR_NETWORK, config, request);
      err.event = event || null;
      reject(err);
      done();
      request = null;
    };
    request.ontimeout = function handleTimeout() {
      let timeoutErrorMessage = _config.timeout ? "timeout of " + _config.timeout + "ms exceeded" : "timeout exceeded";
      const transitional2 = _config.transitional || transitional_default;
      if (_config.timeoutErrorMessage) {
        timeoutErrorMessage = _config.timeoutErrorMessage;
      }
      reject(
        new AxiosError_default(
          timeoutErrorMessage,
          transitional2.clarifyTimeoutError ? AxiosError_default.ETIMEDOUT : AxiosError_default.ECONNABORTED,
          config,
          request
        )
      );
      done();
      request = null;
    };
    requestData === void 0 && requestHeaders.setContentType(null);
    if ("setRequestHeader" in request) {
      utils_default.forEach(toByteStringHeaderObject(requestHeaders), function setRequestHeader(val, key) {
        request.setRequestHeader(key, val);
      });
    }
    if (!utils_default.isUndefined(_config.withCredentials)) {
      request.withCredentials = !!_config.withCredentials;
    }
    if (responseType && responseType !== "json") {
      request.responseType = _config.responseType;
    }
    if (onDownloadProgress) {
      [downloadThrottled, flushDownload] = progressEventReducer(onDownloadProgress, true);
      request.addEventListener("progress", downloadThrottled);
    }
    if (onUploadProgress && request.upload) {
      [uploadThrottled, flushUpload] = progressEventReducer(onUploadProgress);
      request.upload.addEventListener("progress", uploadThrottled);
      request.upload.addEventListener("loadend", flushUpload);
    }
    if (_config.cancelToken || _config.signal) {
      onCanceled = (cancel) => {
        if (!request) {
          return;
        }
        reject(!cancel || cancel.type ? new CanceledError_default(null, config, request) : cancel);
        request.abort();
        done();
        request = null;
      };
      _config.cancelToken && _config.cancelToken.subscribe(onCanceled);
      if (_config.signal) {
        _config.signal.aborted ? onCanceled() : _config.signal.addEventListener("abort", onCanceled);
      }
    }
    const protocol = parseProtocol(_config.url);
    if (protocol && !platform_default.protocols.includes(protocol)) {
      reject(
        new AxiosError_default(
          "Unsupported protocol " + protocol + ":",
          AxiosError_default.ERR_BAD_REQUEST,
          config
        )
      );
      return;
    }
    request.send(requestData || null);
  });
};

// ../shared/core/node_modules/axios/lib/helpers/composeSignals.js
var composeSignals = (signals, timeout) => {
  signals = signals ? signals.filter(Boolean) : [];
  if (!timeout && !signals.length) {
    return;
  }
  const controller = new AbortController();
  let aborted = false;
  const onabort = function(reason) {
    if (!aborted) {
      aborted = true;
      unsubscribe();
      const err = reason instanceof Error ? reason : this.reason;
      controller.abort(
        err instanceof AxiosError_default ? err : new CanceledError_default(err instanceof Error ? err.message : err)
      );
    }
  };
  let timer = timeout && setTimeout(() => {
    timer = null;
    onabort(new AxiosError_default(`timeout of ${timeout}ms exceeded`, AxiosError_default.ETIMEDOUT));
  }, timeout);
  const unsubscribe = () => {
    if (!signals) {
      return;
    }
    timer && clearTimeout(timer);
    timer = null;
    signals.forEach((signal2) => {
      signal2.unsubscribe ? signal2.unsubscribe(onabort) : signal2.removeEventListener("abort", onabort);
    });
    signals = null;
  };
  signals.forEach((signal2) => signal2.addEventListener("abort", onabort));
  const { signal } = controller;
  signal.unsubscribe = () => utils_default.asap(unsubscribe);
  return signal;
};
var composeSignals_default = composeSignals;

// ../shared/core/node_modules/axios/lib/helpers/trackStream.js
var streamChunk = function* (chunk, chunkSize) {
  let len = chunk.byteLength;
  if (!chunkSize || len < chunkSize) {
    yield chunk;
    return;
  }
  let pos = 0;
  let end;
  while (pos < len) {
    end = pos + chunkSize;
    yield chunk.slice(pos, end);
    pos = end;
  }
};
var readBytes = async function* (iterable, chunkSize) {
  for await (const chunk of readStream(iterable)) {
    yield* streamChunk(chunk, chunkSize);
  }
};
var readStream = async function* (stream4) {
  if (stream4[Symbol.asyncIterator]) {
    yield* stream4;
    return;
  }
  const reader = stream4.getReader();
  try {
    for (; ; ) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      yield value;
    }
  } finally {
    await reader.cancel();
  }
};
var trackStream = (stream4, chunkSize, onProgress, onFinish) => {
  const iterator2 = readBytes(stream4, chunkSize);
  let bytes = 0;
  let done;
  let _onFinish = (e) => {
    if (!done) {
      done = true;
      onFinish && onFinish(e);
    }
  };
  return new ReadableStream(
    {
      async pull(controller) {
        try {
          const { done: done2, value } = await iterator2.next();
          if (done2) {
            _onFinish();
            controller.close();
            return;
          }
          let len = value.byteLength;
          if (onProgress) {
            let loadedBytes = bytes += len;
            onProgress(loadedBytes);
          }
          controller.enqueue(new Uint8Array(value));
        } catch (err) {
          _onFinish(err);
          throw err;
        }
      },
      cancel(reason) {
        _onFinish(reason);
        return iterator2.return();
      }
    },
    {
      highWaterMark: 2
    }
  );
};

// ../shared/core/node_modules/axios/lib/adapters/fetch.js
var DEFAULT_CHUNK_SIZE = 64 * 1024;
var { isFunction: isFunction2 } = utils_default;
var encodeUTF82 = (str) => encodeURIComponent(str).replace(
  /%([0-9A-F]{2})/gi,
  (_, hex) => String.fromCharCode(parseInt(hex, 16))
);
var decodeURIComponentSafe2 = (value) => {
  if (!utils_default.isString(value)) {
    return value;
  }
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
};
var test = (fn, ...args) => {
  try {
    return !!fn(...args);
  } catch (e) {
    return false;
  }
};
var maybeWithAuthCredentials = (url2) => {
  const protocolIndex = url2.indexOf("://");
  let urlToCheck = url2;
  if (protocolIndex !== -1) {
    urlToCheck = urlToCheck.slice(protocolIndex + 3);
  }
  return urlToCheck.includes("@") || urlToCheck.includes(":");
};
var factory = (env) => {
  const globalObject = utils_default.global !== void 0 && utils_default.global !== null ? utils_default.global : globalThis;
  const { ReadableStream: ReadableStream2, TextEncoder: TextEncoder2 } = globalObject;
  env = utils_default.merge.call(
    {
      skipUndefined: true
    },
    {
      Request: globalObject.Request,
      Response: globalObject.Response
    },
    env
  );
  const { fetch: envFetch, Request, Response } = env;
  const isFetchSupported = envFetch ? isFunction2(envFetch) : typeof fetch === "function";
  const isRequestSupported = isFunction2(Request);
  const isResponseSupported = isFunction2(Response);
  if (!isFetchSupported) {
    return false;
  }
  const isReadableStreamSupported = isFetchSupported && isFunction2(ReadableStream2);
  const encodeText = isFetchSupported && (typeof TextEncoder2 === "function" ? /* @__PURE__ */ ((encoder) => (str) => encoder.encode(str))(new TextEncoder2()) : async (str) => new Uint8Array(await new Request(str).arrayBuffer()));
  const supportsRequestStream = isRequestSupported && isReadableStreamSupported && test(() => {
    let duplexAccessed = false;
    const request = new Request(platform_default.origin, {
      body: new ReadableStream2(),
      method: "POST",
      get duplex() {
        duplexAccessed = true;
        return "half";
      }
    });
    const hasContentType = request.headers.has("Content-Type");
    if (request.body != null) {
      request.body.cancel();
    }
    return duplexAccessed && !hasContentType;
  });
  const supportsResponseStream = isResponseSupported && isReadableStreamSupported && test(() => utils_default.isReadableStream(new Response("").body));
  const resolvers = {
    stream: supportsResponseStream && ((res) => res.body)
  };
  isFetchSupported && (() => {
    ["text", "arrayBuffer", "blob", "formData", "stream"].forEach((type) => {
      !resolvers[type] && (resolvers[type] = (res, config) => {
        let method = res && res[type];
        if (method) {
          return method.call(res);
        }
        throw new AxiosError_default(
          `Response type '${type}' is not supported`,
          AxiosError_default.ERR_NOT_SUPPORT,
          config
        );
      });
    });
  })();
  const getBodyLength = async (body) => {
    if (body == null) {
      return 0;
    }
    if (utils_default.isBlob(body)) {
      return body.size;
    }
    if (utils_default.isSpecCompliantForm(body)) {
      const _request = new Request(platform_default.origin, {
        method: "POST",
        body
      });
      return (await _request.arrayBuffer()).byteLength;
    }
    if (utils_default.isArrayBufferView(body) || utils_default.isArrayBuffer(body)) {
      return body.byteLength;
    }
    if (utils_default.isURLSearchParams(body)) {
      body = body + "";
    }
    if (utils_default.isString(body)) {
      return (await encodeText(body)).byteLength;
    }
  };
  const resolveBodyLength = async (headers, body) => {
    const length = utils_default.toFiniteNumber(headers.getContentLength());
    return length == null ? getBodyLength(body) : length;
  };
  return async (config) => {
    let {
      url: url2,
      method,
      data,
      signal,
      cancelToken,
      timeout,
      onDownloadProgress,
      onUploadProgress,
      responseType,
      headers,
      withCredentials = "same-origin",
      fetchOptions,
      maxContentLength,
      maxBodyLength
    } = resolveConfig_default(config);
    const hasMaxContentLength = utils_default.isNumber(maxContentLength) && maxContentLength > -1;
    const hasMaxBodyLength = utils_default.isNumber(maxBodyLength) && maxBodyLength > -1;
    const own2 = (key) => utils_default.hasOwnProp(config, key) ? config[key] : void 0;
    let _fetch = envFetch || fetch;
    responseType = responseType ? (responseType + "").toLowerCase() : "text";
    let composedSignal = composeSignals_default(
      [signal, cancelToken && cancelToken.toAbortSignal()],
      timeout
    );
    let request = null;
    const unsubscribe = composedSignal && composedSignal.unsubscribe && (() => {
      composedSignal.unsubscribe();
    });
    let requestContentLength;
    let pendingBodyError = null;
    const maxBodyLengthError = () => new AxiosError_default(
      "Request body larger than maxBodyLength limit",
      AxiosError_default.ERR_BAD_REQUEST,
      config,
      request
    );
    try {
      let auth = void 0;
      const configAuth = own2("auth");
      if (configAuth) {
        const username = utils_default.getSafeProp(configAuth, "username") || "";
        const password = utils_default.getSafeProp(configAuth, "password") || "";
        auth = {
          username,
          password
        };
      }
      if (maybeWithAuthCredentials(url2)) {
        const parsedURL = new URL(url2, platform_default.origin);
        if (!auth && (parsedURL.username || parsedURL.password)) {
          const urlUsername = decodeURIComponentSafe2(parsedURL.username);
          const urlPassword = decodeURIComponentSafe2(parsedURL.password);
          auth = {
            username: urlUsername,
            password: urlPassword
          };
        }
        if (parsedURL.username || parsedURL.password) {
          parsedURL.username = "";
          parsedURL.password = "";
          url2 = parsedURL.href;
        }
      }
      if (auth) {
        headers.delete("authorization");
        headers.set(
          "Authorization",
          "Basic " + btoa(encodeUTF82((auth.username || "") + ":" + (auth.password || "")))
        );
      }
      if (hasMaxContentLength && typeof url2 === "string" && url2.startsWith("data:")) {
        const estimated = estimateDataURLDecodedBytes(url2);
        if (estimated > maxContentLength) {
          throw new AxiosError_default(
            "maxContentLength size of " + maxContentLength + " exceeded",
            AxiosError_default.ERR_BAD_RESPONSE,
            config,
            request
          );
        }
      }
      if (hasMaxBodyLength && method !== "get" && method !== "head") {
        const outboundLength = await getBodyLength(data);
        if (typeof outboundLength === "number" && isFinite(outboundLength)) {
          requestContentLength = outboundLength;
          if (outboundLength > maxBodyLength) {
            throw maxBodyLengthError();
          }
        }
      }
      const mustEnforceStreamBody = hasMaxBodyLength && (utils_default.isReadableStream(data) || utils_default.isStream(data));
      const trackRequestStream = (stream4, onProgress, flush) => trackStream(
        stream4,
        DEFAULT_CHUNK_SIZE,
        (loadedBytes) => {
          if (hasMaxBodyLength && loadedBytes > maxBodyLength) {
            throw pendingBodyError = maxBodyLengthError();
          }
          onProgress && onProgress(loadedBytes);
        },
        flush
      );
      if (supportsRequestStream && method !== "get" && method !== "head" && (onUploadProgress || mustEnforceStreamBody)) {
        requestContentLength = requestContentLength == null ? await resolveBodyLength(headers, data) : requestContentLength;
        if (requestContentLength !== 0 || mustEnforceStreamBody) {
          let _request = new Request(url2, {
            method: "POST",
            body: data,
            duplex: "half"
          });
          let contentTypeHeader;
          if (utils_default.isFormData(data) && (contentTypeHeader = _request.headers.get("content-type"))) {
            headers.setContentType(contentTypeHeader);
          }
          if (_request.body) {
            const [onProgress, flush] = onUploadProgress && progressEventDecorator(
              requestContentLength,
              progressEventReducer(asyncDecorator(onUploadProgress))
            ) || [];
            data = trackRequestStream(_request.body, onProgress, flush);
          }
        }
      } else if (mustEnforceStreamBody && !isRequestSupported && isReadableStreamSupported && method !== "get" && method !== "head") {
        data = trackRequestStream(data);
      } else if (mustEnforceStreamBody && isRequestSupported && !supportsRequestStream && method !== "get" && method !== "head") {
        throw new AxiosError_default(
          "Stream request bodies are not supported by the current fetch implementation",
          AxiosError_default.ERR_NOT_SUPPORT,
          config,
          request
        );
      }
      if (!utils_default.isString(withCredentials)) {
        withCredentials = withCredentials ? "include" : "omit";
      }
      const isCredentialsSupported = isRequestSupported && "credentials" in Request.prototype;
      if (utils_default.isFormData(data)) {
        const contentType = headers.getContentType();
        if (contentType && /^multipart\/form-data/i.test(contentType) && !/boundary=/i.test(contentType)) {
          headers.delete("content-type");
        }
      }
      headers.set("User-Agent", "axios/" + VERSION, false);
      const resolvedOptions = {
        ...fetchOptions,
        signal: composedSignal,
        method: method.toUpperCase(),
        headers: toByteStringHeaderObject(headers.normalize()),
        body: data,
        duplex: "half",
        credentials: isCredentialsSupported ? withCredentials : void 0
      };
      request = isRequestSupported && new Request(url2, resolvedOptions);
      let response = await (isRequestSupported ? _fetch(request, fetchOptions) : _fetch(url2, resolvedOptions));
      const responseHeaders = AxiosHeaders_default.from(response.headers);
      if (hasMaxContentLength) {
        const declaredLength = utils_default.toFiniteNumber(responseHeaders.getContentLength());
        if (declaredLength != null && declaredLength > maxContentLength) {
          throw new AxiosError_default(
            "maxContentLength size of " + maxContentLength + " exceeded",
            AxiosError_default.ERR_BAD_RESPONSE,
            config,
            request
          );
        }
      }
      const isStreamResponse = supportsResponseStream && (responseType === "stream" || responseType === "response");
      if (supportsResponseStream && response.body && (onDownloadProgress || hasMaxContentLength || isStreamResponse && unsubscribe)) {
        const options = {};
        ["status", "statusText", "headers"].forEach((prop) => {
          options[prop] = response[prop];
        });
        const responseContentLength = utils_default.toFiniteNumber(responseHeaders.getContentLength());
        const [onProgress, flush] = onDownloadProgress && progressEventDecorator(
          responseContentLength,
          progressEventReducer(asyncDecorator(onDownloadProgress), true)
        ) || [];
        let bytesRead = 0;
        const onChunkProgress = (loadedBytes) => {
          if (hasMaxContentLength) {
            bytesRead = loadedBytes;
            if (bytesRead > maxContentLength) {
              throw new AxiosError_default(
                "maxContentLength size of " + maxContentLength + " exceeded",
                AxiosError_default.ERR_BAD_RESPONSE,
                config,
                request
              );
            }
          }
          onProgress && onProgress(loadedBytes);
        };
        response = new Response(
          trackStream(response.body, DEFAULT_CHUNK_SIZE, onChunkProgress, () => {
            flush && flush();
            unsubscribe && unsubscribe();
          }),
          options
        );
      }
      responseType = responseType || "text";
      let responseData = await resolvers[utils_default.findKey(resolvers, responseType) || "text"](
        response,
        config
      );
      if (hasMaxContentLength && !supportsResponseStream && !isStreamResponse) {
        let materializedSize;
        if (responseData != null) {
          if (typeof responseData.byteLength === "number") {
            materializedSize = responseData.byteLength;
          } else if (typeof responseData.size === "number") {
            materializedSize = responseData.size;
          } else if (typeof responseData === "string") {
            materializedSize = typeof TextEncoder2 === "function" ? new TextEncoder2().encode(responseData).byteLength : responseData.length;
          }
        }
        if (typeof materializedSize === "number" && materializedSize > maxContentLength) {
          throw new AxiosError_default(
            "maxContentLength size of " + maxContentLength + " exceeded",
            AxiosError_default.ERR_BAD_RESPONSE,
            config,
            request
          );
        }
      }
      !isStreamResponse && unsubscribe && unsubscribe();
      return await new Promise((resolve, reject) => {
        settle(resolve, reject, {
          data: responseData,
          headers: AxiosHeaders_default.from(response.headers),
          status: response.status,
          statusText: response.statusText,
          config,
          request
        });
      });
    } catch (err) {
      unsubscribe && unsubscribe();
      if (composedSignal && composedSignal.aborted && composedSignal.reason instanceof AxiosError_default) {
        const canceledError = composedSignal.reason;
        canceledError.config = config;
        request && (canceledError.request = request);
        err !== canceledError && (canceledError.cause = err);
        throw canceledError;
      }
      if (pendingBodyError) {
        request && !pendingBodyError.request && (pendingBodyError.request = request);
        throw pendingBodyError;
      }
      if (err instanceof AxiosError_default) {
        request && !err.request && (err.request = request);
        throw err;
      }
      if (err && err.name === "TypeError" && /Load failed|fetch/i.test(err.message)) {
        throw Object.assign(
          new AxiosError_default(
            "Network Error",
            AxiosError_default.ERR_NETWORK,
            config,
            request,
            err && err.response
          ),
          {
            cause: err.cause || err
          }
        );
      }
      throw AxiosError_default.from(err, err && err.code, config, request, err && err.response);
    }
  };
};
var seedCache = /* @__PURE__ */ new Map();
var getFetch = (config) => {
  let env = config && config.env || {};
  const { fetch: fetch2, Request, Response } = env;
  const seeds = [Request, Response, fetch2];
  let len = seeds.length, i = len, seed, target, map = seedCache;
  while (i--) {
    seed = seeds[i];
    target = map.get(seed);
    target === void 0 && map.set(seed, target = i ? /* @__PURE__ */ new Map() : factory(env));
    map = target;
  }
  return target;
};
var adapter = getFetch();

// ../shared/core/node_modules/axios/lib/adapters/adapters.js
var knownAdapters = {
  http: http_default,
  xhr: xhr_default,
  fetch: {
    get: getFetch
  }
};
utils_default.forEach(knownAdapters, (fn, value) => {
  if (fn) {
    try {
      Object.defineProperty(fn, "name", { __proto__: null, value });
    } catch (e) {
    }
    Object.defineProperty(fn, "adapterName", { __proto__: null, value });
  }
});
var renderReason = (reason) => `- ${reason}`;
var isResolvedHandle = (adapter2) => utils_default.isFunction(adapter2) || adapter2 === null || adapter2 === false;
function getAdapter(adapters, config) {
  adapters = utils_default.isArray(adapters) ? adapters : [adapters];
  const { length } = adapters;
  let nameOrAdapter;
  let adapter2;
  const rejectedReasons = {};
  for (let i = 0; i < length; i++) {
    nameOrAdapter = adapters[i];
    let id;
    adapter2 = nameOrAdapter;
    if (!isResolvedHandle(nameOrAdapter)) {
      adapter2 = knownAdapters[(id = String(nameOrAdapter)).toLowerCase()];
      if (adapter2 === void 0) {
        throw new AxiosError_default(`Unknown adapter '${id}'`);
      }
    }
    if (adapter2 && (utils_default.isFunction(adapter2) || (adapter2 = adapter2.get(config)))) {
      break;
    }
    rejectedReasons[id || "#" + i] = adapter2;
  }
  if (!adapter2) {
    const reasons = Object.entries(rejectedReasons).map(
      ([id, state]) => `adapter ${id} ` + (state === false ? "is not supported by the environment" : "is not available in the build")
    );
    let s = length ? reasons.length > 1 ? "since :\n" + reasons.map(renderReason).join("\n") : " " + renderReason(reasons[0]) : "as no adapter specified";
    throw new AxiosError_default(
      `There is no suitable adapter to dispatch the request ` + s,
      "ERR_NOT_SUPPORT"
    );
  }
  return adapter2;
}
var adapters_default = {
  /**
   * Resolve an adapter from a list of adapter names or functions.
   * @type {Function}
   */
  getAdapter,
  /**
   * Exposes all known adapters
   * @type {Object<string, Function|Object>}
   */
  adapters: knownAdapters
};

// ../shared/core/node_modules/axios/lib/core/dispatchRequest.js
function throwIfCancellationRequested(config) {
  if (config.cancelToken) {
    config.cancelToken.throwIfRequested();
  }
  if (config.signal && config.signal.aborted) {
    throw new CanceledError_default(null, config);
  }
}
function dispatchRequest(config) {
  throwIfCancellationRequested(config);
  config.headers = AxiosHeaders_default.from(config.headers);
  config.data = transformData.call(config, config.transformRequest);
  if (["post", "put", "patch"].indexOf(config.method) !== -1) {
    config.headers.setContentType("application/x-www-form-urlencoded", false);
  }
  const adapter2 = adapters_default.getAdapter(config.adapter || defaults_default.adapter, config);
  return adapter2(config).then(
    function onAdapterResolution(response) {
      throwIfCancellationRequested(config);
      config.response = response;
      try {
        response.data = transformData.call(config, config.transformResponse, response);
      } finally {
        delete config.response;
      }
      response.headers = AxiosHeaders_default.from(response.headers);
      return response;
    },
    function onAdapterRejection(reason) {
      if (!isCancel(reason)) {
        throwIfCancellationRequested(config);
        if (reason && reason.response) {
          config.response = reason.response;
          try {
            reason.response.data = transformData.call(
              config,
              config.transformResponse,
              reason.response
            );
          } finally {
            delete config.response;
          }
          reason.response.headers = AxiosHeaders_default.from(reason.response.headers);
        }
      }
      return Promise.reject(reason);
    }
  );
}

// ../shared/core/node_modules/axios/lib/helpers/validator.js
var validators = {};
["object", "boolean", "number", "function", "string", "symbol"].forEach((type, i) => {
  validators[type] = function validator(thing) {
    return typeof thing === type || "a" + (i < 1 ? "n " : " ") + type;
  };
});
var deprecatedWarnings = {};
validators.transitional = function transitional(validator, version, message) {
  function formatMessage(opt, desc) {
    return "[Axios v" + VERSION + "] Transitional option '" + opt + "'" + desc + (message ? ". " + message : "");
  }
  return (value, opt, opts) => {
    if (validator === false) {
      throw new AxiosError_default(
        formatMessage(opt, " has been removed" + (version ? " in " + version : "")),
        AxiosError_default.ERR_DEPRECATED
      );
    }
    if (version && !deprecatedWarnings[opt]) {
      deprecatedWarnings[opt] = true;
      console.warn(
        formatMessage(
          opt,
          " has been deprecated since v" + version + " and will be removed in the near future"
        )
      );
    }
    return validator ? validator(value, opt, opts) : true;
  };
};
validators.spelling = function spelling(correctSpelling) {
  return (value, opt) => {
    console.warn(`${opt} is likely a misspelling of ${correctSpelling}`);
    return true;
  };
};
function assertOptions(options, schema, allowUnknown) {
  if (typeof options !== "object") {
    throw new AxiosError_default("options must be an object", AxiosError_default.ERR_BAD_OPTION_VALUE);
  }
  const keys = Object.keys(options);
  let i = keys.length;
  while (i-- > 0) {
    const opt = keys[i];
    const validator = Object.prototype.hasOwnProperty.call(schema, opt) ? schema[opt] : void 0;
    if (validator) {
      const value = options[opt];
      const result = value === void 0 || validator(value, opt, options);
      if (result !== true) {
        throw new AxiosError_default(
          "option " + opt + " must be " + result,
          AxiosError_default.ERR_BAD_OPTION_VALUE
        );
      }
      continue;
    }
    if (allowUnknown !== true) {
      throw new AxiosError_default("Unknown option " + opt, AxiosError_default.ERR_BAD_OPTION);
    }
  }
}
var validator_default = {
  assertOptions,
  validators
};

// ../shared/core/node_modules/axios/lib/core/Axios.js
var validators2 = validator_default.validators;
var Axios = class {
  constructor(instanceConfig) {
    this.defaults = instanceConfig || {};
    this.interceptors = {
      request: new InterceptorManager_default(),
      response: new InterceptorManager_default()
    };
  }
  /**
   * Dispatch a request
   *
   * @param {String|Object} configOrUrl The config specific for this request (merged with this.defaults)
   * @param {?Object} config
   *
   * @returns {Promise} The Promise to be fulfilled
   */
  async request(configOrUrl, config) {
    try {
      return await this._request(configOrUrl, config);
    } catch (err) {
      if (err instanceof Error) {
        let dummy = {};
        Error.captureStackTrace ? Error.captureStackTrace(dummy) : dummy = new Error();
        const stack = (() => {
          if (!dummy.stack) {
            return "";
          }
          const firstNewlineIndex = dummy.stack.indexOf("\n");
          return firstNewlineIndex === -1 ? "" : dummy.stack.slice(firstNewlineIndex + 1);
        })();
        try {
          if (!err.stack) {
            err.stack = stack;
          } else if (stack) {
            const firstNewlineIndex = stack.indexOf("\n");
            const secondNewlineIndex = firstNewlineIndex === -1 ? -1 : stack.indexOf("\n", firstNewlineIndex + 1);
            const stackWithoutTwoTopLines = secondNewlineIndex === -1 ? "" : stack.slice(secondNewlineIndex + 1);
            if (!String(err.stack).endsWith(stackWithoutTwoTopLines)) {
              err.stack += "\n" + stack;
            }
          }
        } catch (e) {
        }
      }
      throw err;
    }
  }
  _request(configOrUrl, config) {
    if (typeof configOrUrl === "string") {
      config = config || {};
      config.url = configOrUrl;
    } else {
      config = configOrUrl || {};
    }
    config = mergeConfig(this.defaults, config);
    const { transitional: transitional2, paramsSerializer, headers } = config;
    if (transitional2 !== void 0) {
      validator_default.assertOptions(
        transitional2,
        {
          silentJSONParsing: validators2.transitional(validators2.boolean),
          forcedJSONParsing: validators2.transitional(validators2.boolean),
          clarifyTimeoutError: validators2.transitional(validators2.boolean),
          legacyInterceptorReqResOrdering: validators2.transitional(validators2.boolean),
          advertiseZstdAcceptEncoding: validators2.transitional(validators2.boolean),
          validateStatusUndefinedResolves: validators2.transitional(validators2.boolean)
        },
        false
      );
    }
    if (paramsSerializer != null) {
      if (utils_default.isFunction(paramsSerializer)) {
        config.paramsSerializer = {
          serialize: paramsSerializer
        };
      } else {
        validator_default.assertOptions(
          paramsSerializer,
          {
            encode: validators2.function,
            serialize: validators2.function
          },
          true
        );
      }
    }
    if (config.allowAbsoluteUrls !== void 0) {
    } else if (this.defaults.allowAbsoluteUrls !== void 0) {
      config.allowAbsoluteUrls = this.defaults.allowAbsoluteUrls;
    } else {
      config.allowAbsoluteUrls = true;
    }
    validator_default.assertOptions(
      config,
      {
        baseUrl: validators2.spelling("baseURL"),
        withXsrfToken: validators2.spelling("withXSRFToken")
      },
      true
    );
    config.method = (config.method || this.defaults.method || "get").toLowerCase();
    let contextHeaders = headers && utils_default.merge(headers.common, headers[config.method]);
    headers && utils_default.forEach(["delete", "get", "head", "post", "put", "patch", "query", "common"], (method) => {
      delete headers[method];
    });
    config.headers = AxiosHeaders_default.concat(contextHeaders, headers);
    const requestInterceptorChain = [];
    let synchronousRequestInterceptors = true;
    this.interceptors.request.forEach(function unshiftRequestInterceptors(interceptor) {
      if (typeof interceptor.runWhen === "function" && interceptor.runWhen(config) === false) {
        return;
      }
      synchronousRequestInterceptors = synchronousRequestInterceptors && interceptor.synchronous;
      const transitional3 = config.transitional || transitional_default;
      const legacyInterceptorReqResOrdering = transitional3 && transitional3.legacyInterceptorReqResOrdering;
      if (legacyInterceptorReqResOrdering) {
        requestInterceptorChain.unshift(interceptor.fulfilled, interceptor.rejected);
      } else {
        requestInterceptorChain.push(interceptor.fulfilled, interceptor.rejected);
      }
    });
    const responseInterceptorChain = [];
    this.interceptors.response.forEach(function pushResponseInterceptors(interceptor) {
      responseInterceptorChain.push(interceptor.fulfilled, interceptor.rejected);
    });
    let promise;
    let i = 0;
    let len;
    if (!synchronousRequestInterceptors) {
      const chain = [dispatchRequest.bind(this), void 0];
      chain.unshift(...requestInterceptorChain);
      chain.push(...responseInterceptorChain);
      len = chain.length;
      promise = Promise.resolve(config);
      while (i < len) {
        promise = promise.then(chain[i++], chain[i++]);
      }
      return promise;
    }
    len = requestInterceptorChain.length;
    let newConfig = config;
    while (i < len) {
      const onFulfilled = requestInterceptorChain[i++];
      const onRejected = requestInterceptorChain[i++];
      try {
        newConfig = onFulfilled(newConfig);
      } catch (error) {
        onRejected.call(this, error);
        break;
      }
    }
    try {
      promise = dispatchRequest.call(this, newConfig);
    } catch (error) {
      return Promise.reject(error);
    }
    i = 0;
    len = responseInterceptorChain.length;
    while (i < len) {
      promise = promise.then(responseInterceptorChain[i++], responseInterceptorChain[i++]);
    }
    return promise;
  }
  getUri(config) {
    config = mergeConfig(this.defaults, config);
    const fullPath = buildFullPath(config.baseURL, config.url, config.allowAbsoluteUrls, config);
    return buildURL(fullPath, config.params, config.paramsSerializer);
  }
};
utils_default.forEach(["delete", "get", "head", "options"], function forEachMethodNoData(method) {
  Axios.prototype[method] = function(url2, config) {
    return this.request(
      mergeConfig(config || {}, {
        method,
        url: url2,
        data: config && utils_default.hasOwnProp(config, "data") ? config.data : void 0
      })
    );
  };
});
utils_default.forEach(["post", "put", "patch", "query"], function forEachMethodWithData(method) {
  function generateHTTPMethod(isForm) {
    return function httpMethod(url2, data, config) {
      return this.request(
        mergeConfig(config || {}, {
          method,
          headers: isForm ? {
            "Content-Type": "multipart/form-data"
          } : {},
          url: url2,
          data
        })
      );
    };
  }
  Axios.prototype[method] = generateHTTPMethod();
  if (method !== "query") {
    Axios.prototype[method + "Form"] = generateHTTPMethod(true);
  }
});
var Axios_default = Axios;

// ../shared/core/node_modules/axios/lib/cancel/CancelToken.js
var CancelToken = class _CancelToken {
  constructor(executor) {
    if (typeof executor !== "function") {
      throw new TypeError("executor must be a function.");
    }
    let resolvePromise;
    this.promise = new Promise(function promiseExecutor(resolve) {
      resolvePromise = resolve;
    });
    const token = this;
    this.promise.then((cancel) => {
      if (!token._listeners) return;
      let i = token._listeners.length;
      while (i-- > 0) {
        token._listeners[i](cancel);
      }
      token._listeners = null;
    });
    this.promise.then = (onfulfilled) => {
      let _resolve;
      const promise = new Promise((resolve) => {
        token.subscribe(resolve);
        _resolve = resolve;
      }).then(onfulfilled);
      promise.cancel = function reject() {
        token.unsubscribe(_resolve);
      };
      return promise;
    };
    executor(function cancel(message, config, request) {
      if (token.reason) {
        return;
      }
      token.reason = new CanceledError_default(message, config, request);
      resolvePromise(token.reason);
    });
  }
  /**
   * Throws a `CanceledError` if cancellation has been requested.
   */
  throwIfRequested() {
    if (this.reason) {
      throw this.reason;
    }
  }
  /**
   * Subscribe to the cancel signal
   */
  subscribe(listener) {
    if (this.reason) {
      listener(this.reason);
      return;
    }
    if (this._listeners) {
      this._listeners.push(listener);
    } else {
      this._listeners = [listener];
    }
  }
  /**
   * Unsubscribe from the cancel signal
   */
  unsubscribe(listener) {
    if (!this._listeners) {
      return;
    }
    const index = this._listeners.indexOf(listener);
    if (index !== -1) {
      this._listeners.splice(index, 1);
    }
  }
  toAbortSignal() {
    const controller = new AbortController();
    const abort = (err) => {
      controller.abort(err);
    };
    this.subscribe(abort);
    controller.signal.unsubscribe = () => this.unsubscribe(abort);
    return controller.signal;
  }
  /**
   * Returns an object that contains a new `CancelToken` and a function that, when called,
   * cancels the `CancelToken`.
   */
  static source() {
    let cancel;
    const token = new _CancelToken(function executor(c) {
      cancel = c;
    });
    return {
      token,
      cancel
    };
  }
};
var CancelToken_default = CancelToken;

// ../shared/core/node_modules/axios/lib/helpers/spread.js
function spread(callback) {
  return function wrap(arr) {
    return callback.apply(null, arr);
  };
}

// ../shared/core/node_modules/axios/lib/helpers/isAxiosError.js
function isAxiosError(payload) {
  return utils_default.isObject(payload) && payload.isAxiosError === true;
}

// ../shared/core/node_modules/axios/lib/helpers/HttpStatusCode.js
var HttpStatusCode = {
  Continue: 100,
  SwitchingProtocols: 101,
  Processing: 102,
  EarlyHints: 103,
  Ok: 200,
  Created: 201,
  Accepted: 202,
  NonAuthoritativeInformation: 203,
  NoContent: 204,
  ResetContent: 205,
  PartialContent: 206,
  MultiStatus: 207,
  AlreadyReported: 208,
  ImUsed: 226,
  MultipleChoices: 300,
  MovedPermanently: 301,
  Found: 302,
  SeeOther: 303,
  NotModified: 304,
  UseProxy: 305,
  Unused: 306,
  TemporaryRedirect: 307,
  PermanentRedirect: 308,
  BadRequest: 400,
  Unauthorized: 401,
  PaymentRequired: 402,
  Forbidden: 403,
  NotFound: 404,
  MethodNotAllowed: 405,
  NotAcceptable: 406,
  ProxyAuthenticationRequired: 407,
  RequestTimeout: 408,
  Conflict: 409,
  Gone: 410,
  LengthRequired: 411,
  PreconditionFailed: 412,
  PayloadTooLarge: 413,
  UriTooLong: 414,
  UnsupportedMediaType: 415,
  RangeNotSatisfiable: 416,
  ExpectationFailed: 417,
  ImATeapot: 418,
  MisdirectedRequest: 421,
  UnprocessableEntity: 422,
  Locked: 423,
  FailedDependency: 424,
  TooEarly: 425,
  UpgradeRequired: 426,
  PreconditionRequired: 428,
  TooManyRequests: 429,
  RequestHeaderFieldsTooLarge: 431,
  UnavailableForLegalReasons: 451,
  InternalServerError: 500,
  NotImplemented: 501,
  BadGateway: 502,
  ServiceUnavailable: 503,
  GatewayTimeout: 504,
  HttpVersionNotSupported: 505,
  VariantAlsoNegotiates: 506,
  InsufficientStorage: 507,
  LoopDetected: 508,
  NotExtended: 510,
  NetworkAuthenticationRequired: 511,
  WebServerIsDown: 521,
  ConnectionTimedOut: 522,
  OriginIsUnreachable: 523,
  TimeoutOccurred: 524,
  SslHandshakeFailed: 525,
  InvalidSslCertificate: 526
};
Object.entries(HttpStatusCode).forEach(([key, value]) => {
  HttpStatusCode[value] = key;
});
var HttpStatusCode_default = HttpStatusCode;

// ../shared/core/node_modules/axios/lib/axios.js
function createInstance(defaultConfig) {
  const context = new Axios_default(defaultConfig);
  const instance = bind(Axios_default.prototype.request, context);
  utils_default.extend(instance, Axios_default.prototype, context, { allOwnKeys: true });
  utils_default.extend(instance, context, null, { allOwnKeys: true });
  instance.create = function create2(instanceConfig) {
    return createInstance(mergeConfig(defaultConfig, instanceConfig));
  };
  return instance;
}
var axios = createInstance(defaults_default);
axios.Axios = Axios_default;
axios.CanceledError = CanceledError_default;
axios.CancelToken = CancelToken_default;
axios.isCancel = isCancel;
axios.VERSION = VERSION;
axios.toFormData = toFormData_default;
axios.AxiosError = AxiosError_default;
axios.Cancel = axios.CanceledError;
axios.all = function all(promises) {
  return Promise.all(promises);
};
axios.spread = spread;
axios.isAxiosError = isAxiosError;
axios.mergeConfig = mergeConfig;
axios.AxiosHeaders = AxiosHeaders_default;
axios.formToJSON = (thing) => formDataToJSON_default(utils_default.isHTMLForm(thing) ? new FormData(thing) : thing);
axios.getAdapter = adapters_default.getAdapter;
axios.HttpStatusCode = HttpStatusCode_default;
axios.default = axios;
var axios_default = axios;

// ../shared/core/node_modules/axios/index.js
var {
  Axios: Axios2,
  AxiosError: AxiosError2,
  CanceledError: CanceledError2,
  isCancel: isCancel2,
  CancelToken: CancelToken2,
  VERSION: VERSION2,
  all: all2,
  Cancel,
  isAxiosError: isAxiosError2,
  spread: spread2,
  toFormData: toFormData2,
  AxiosHeaders: AxiosHeaders2,
  HttpStatusCode: HttpStatusCode2,
  formToJSON,
  getAdapter: getAdapter2,
  mergeConfig: mergeConfig2,
  create
} = axios_default;

// ../shared/core/src/client/wizClient.ts
import fs2 from "fs";
import nodePath from "path";
import { buffer as streamToBuffer } from "stream/consumers";
import { pipeline } from "stream/promises";
var WizClientError = class extends Error {
  constructor(code, message, hint, detail, path2) {
    super(message);
    this.code = code;
    this.hint = hint;
    this.detail = detail;
    this.path = path2;
  }
};
function firstNonBlank(...candidates) {
  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim() !== "") return candidate;
  }
  return null;
}
function timeoutHint(path2) {
  const domain = /\/agent\/viewsheet\//.test(path2) ? "viewsheet" : /\/agent\/worksheet\//.test(path2) ? "worksheet" : void 0;
  const reread = domain ? `re-read the model with read_${domain}_model before retrying` : "re-read the current state before retrying";
  return `The server did not respond within the timeout. This does not necessarily mean the request failed: if this was an edit, the change may already be applied \u2014 ${reread}, to avoid applying it twice. If this was a render, the underlying data may still be computing \u2014 retrying shortly may succeed.`;
}
var WizClient = class {
  constructor(tokenStore) {
    this.tokenStore = tokenStore;
  }
  /**
   * `timeoutMs` overrides the 30s default. It exists for probes — a call whose answer is
   * "is the server there", where waiting the full 30s for a black-holed host is itself the
   * wrong answer to give a caller who asked a liveness question.
   */
  async get(path2, opts) {
    return this.request({ method: "GET", path: path2, timeoutMs: opts?.timeoutMs });
  }
  /**
   * `timeoutMs` overrides the 30s default — mirrors {@link get}'s override, for a POST whose
   * server side is expected to sometimes take longer than an ordinary write (e.g. an edit op
   * that triggers a first-time query execution on the sheet).
   */
  async post(path2, body, opts) {
    return this.request({ method: "POST", path: path2, body, timeoutMs: opts?.timeoutMs });
  }
  /**
   * Posts a `multipart/form-data` body (e.g. a file upload). `form` should be a native
   * `FormData` — axios encodes it and sets the `Content-Type` (including boundary) itself,
   * so the caller must not set that header.
   */
  async postMultipart(path2, form, opts) {
    return this.request({ method: "POST", path: path2, body: form, isMultipart: true, timeoutMs: opts?.timeoutMs });
  }
  async put(path2, body) {
    return this.request({ method: "PUT", path: path2, body });
  }
  async delete(path2) {
    return this.request({ method: "DELETE", path: path2 });
  }
  /**
   * Shared by {@link request} and {@link downloadToFile}: resolves the active login into the
   * headers/base-URL every real request needs, or throws the same AUTH_REQUIRED a caller with no
   * session (or a blank token) has always gotten from {@link request}.
   */
  async resolveAuth() {
    const active = await this.tokenStore.getActive();
    if (!active) {
      throw new WizClientError(
        "AUTH_REQUIRED",
        "Not logged in to any StyleBI deployment.",
        "Run login_start to log in"
      );
    }
    if (typeof active.creds.accessToken !== "string" || active.creds.accessToken.trim() === "") {
      throw new WizClientError(
        "AUTH_REQUIRED",
        `Stored credentials for ${active.deployment} have no access token.`,
        "Run login_start to log in"
      );
    }
    const headers = { Authorization: `Bearer ${active.creds.accessToken}` };
    const base = active.deployment.replace(/\/$/, "");
    const apiPrefix = "/api/wiz";
    return { headers, base, apiPrefix };
  }
  /**
   * Shared by {@link request} and {@link downloadToFile}: shapes a caught axios failure into a
   * {@link WizClientError}, given the failure's HTTP status (if any) and its already-extracted
   * response body. `downloadToFile` uses `responseType: "stream"`, so its error body arrives as
   * a stream rather than pre-parsed JSON — it reads and JSON-parses that stream itself before
   * calling this, so both callers can share this one shaping logic.
   */
  toWizClientError(status, detail, rawMessage, code, requestPath) {
    const errorField = typeof detail === "object" && detail !== null ? detail.error ?? detail.message ?? detail.detail : detail;
    const errMessage = firstNonBlank(errorField, rawMessage, code) ?? "unknown error";
    const semanticStatus = typeof detail === "object" && detail !== null && typeof detail.status === "string" ? detail.status.trim() : "";
    const message = semanticStatus && !errMessage.toLowerCase().includes(semanticStatus.toLowerCase()) ? `${semanticStatus}: ${errMessage}` : errMessage;
    if (status === 401) {
      return new WizClientError(
        "AUTH_REQUIRED",
        `Session expired: ${message}`,
        "Run login_start to log in",
        detail,
        requestPath
      );
    }
    if (status === void 0) {
      const hint = code === "ECONNABORTED" || /timeout/i.test(errMessage) ? timeoutHint(requestPath) : void 0;
      return new WizClientError("NETWORK_ERROR", `Network error: ${message}`, hint, detail, requestPath);
    }
    return new WizClientError(`HTTP_${status}`, String(message), void 0, detail, requestPath);
  }
  async request(opts) {
    const { headers, base, apiPrefix } = await this.resolveAuth();
    const config = {
      method: opts.method,
      url: `${base}${apiPrefix}${opts.path.startsWith("/") ? opts.path : "/" + opts.path}`,
      headers,
      timeout: opts.timeoutMs ?? 3e4
    };
    if (opts.body !== void 0) {
      config.data = opts.body;
      if (!opts.isMultipart) {
        headers["Content-Type"] = "application/json";
      }
    }
    try {
      const res = await axios_default.request(config);
      return res.data;
    } catch (err) {
      const e = err;
      throw this.toWizClientError(
        e?.response?.status,
        e?.response?.data,
        e?.message,
        e?.code,
        opts.path
      );
    }
  }
  /**
   * Downloads a binary response (export_viewsheet's actual payload) straight to `destPath`,
   * without ever buffering the file's bytes into a JS string or JSON — a multi-MB export costs
   * no more memory, and crucially no more conversation tokens, than a tiny one, since the tool
   * result only ever carries the small metadata this resolves with, never the file content.
   * Creates `destPath`'s parent directory if it doesn't already exist.
   */
  async downloadToFile(requestPath, destPath, opts) {
    const { headers, base, apiPrefix } = await this.resolveAuth();
    const config = {
      method: "GET",
      url: `${base}${apiPrefix}${requestPath.startsWith("/") ? requestPath : "/" + requestPath}`,
      headers,
      timeout: opts?.timeoutMs ?? 3e4,
      responseType: "stream"
    };
    let res;
    try {
      res = await axios_default.request(config);
    } catch (err) {
      const e = err;
      let detail;
      const errorStream = e?.response?.data;
      if (errorStream && typeof errorStream.pipe === "function") {
        try {
          const raw = await streamToBuffer(errorStream);
          detail = JSON.parse(raw.toString("utf8"));
        } catch {
        }
      }
      throw this.toWizClientError(e?.response?.status, detail, e?.message, e?.code, requestPath);
    }
    const destDir = nodePath.dirname(destPath);
    if (!fs2.existsSync(destDir)) {
      await fs2.promises.mkdir(destDir, { recursive: true });
    }
    const writer = fs2.createWriteStream(destPath);
    let byteLength = 0;
    res.data.on("data", (chunk) => {
      byteLength += chunk.length;
    });
    try {
      await pipeline(res.data, writer);
    } catch (err) {
      try {
        fs2.unlinkSync(destPath);
      } catch {
      }
      throw err;
    }
    const disposition = res.headers["content-disposition"];
    const fileNameMatch = disposition?.match(/filename\*=[^']*'[^']*'"?([^";\r\n]+)"?/i) ?? disposition?.match(/filename=(?:UTF-8'')?"?([^";\r\n]+)"?/i);
    return {
      mimeType: res.headers["content-type"],
      fileName: fileNameMatch ? decodeURIComponent(fileNameMatch[1]) : void 0,
      byteLength
    };
  }
};

// ../shared/core/src/tools/loginStart.ts
import crypto3 from "crypto";
import { spawn } from "child_process";
function openInBrowser(url2) {
  const platform = process.platform;
  const [cmd, args] = platform === "darwin" ? ["open", [url2]] : platform === "win32" ? ["cmd", ["/c", "start", "", url2]] : ["xdg-open", [url2]];
  try {
    const child = spawn(cmd, args, { detached: true, stdio: "ignore" });
    child.on("error", () => {
    });
    child.unref();
    return true;
  } catch {
    return false;
  }
}
function makeLoginStartTool(deps) {
  return {
    name: "login_start",
    description: "Begin a StyleBI login. Opens the authorize URL in the user's default browser and also returns it. The browser page shows an Authorize button \u2014 the session is only established when the user clicks it. They can copy the URL into another browser or incognito window before clicking if they prefer. Always print the authorize URL as a clickable link as a fallback. Then call login_complete with the returned session id to wait for the user to click Authorize. IMPORTANT: always pass the exact StyleBI URL the user most recently stated in this conversation \u2014 never substitute a URL you got from a prior status/login result or an earlier session. A previously stored deployment can be stale or wrong (e.g. missing a reverse-proxy path such as /sree, or pointing at a server the user is no longer using) even though it looks authoritative.",
    inputSchema: {
      type: "object",
      properties: {
        styleBIUrl: {
          type: "string",
          description: "Base URL of the StyleBI deployment, e.g. https://stylebi.example.com. Use exactly what the user most recently told you in this conversation \u2014 do not reuse a URL surfaced by a previous status or login call without confirming it's still what the user means."
        }
      },
      required: ["styleBIUrl"]
    },
    call: (args) => runLoginStart(args, { sessionManager: deps.sessionManager })
  };
}
async function runLoginStart(args, ctx) {
  if (!args.styleBIUrl || typeof args.styleBIUrl !== "string") {
    throw new Error("styleBIUrl is required");
  }
  const deployment = args.styleBIUrl.replace(/\/$/, "");
  const nonce = crypto3.randomBytes(32).toString("hex");
  const sessionId = ctx.sessionManager.store(nonce);
  const callback = `${deployment}/api/wiz/auth/callback?nonce=${nonce}`;
  const authorizeUrl = `${deployment}/sso/authorize?callback=${encodeURIComponent(callback)}`;
  const opener = ctx.openBrowser ?? openInBrowser;
  const browserOpened = opener(authorizeUrl);
  return { authorizeUrl, sessionId, deployment, browserOpened };
}

// ../shared/core/src/tools/loginComplete.ts
var sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function makeLoginCompleteTool(deps) {
  return {
    name: "login_complete",
    description: "Finish a StyleBI login started by login_start. Waits up to 5 minutes for the user to complete SSO in their browser, then polls the deployment's auth-pickup endpoint for the JWT (delivered via the StyleBI auth-callback) and stores credentials. Call this AFTER calling login_start and printing the authorize URL.",
    inputSchema: {
      type: "object",
      properties: {
        sessionId: { type: "string", description: "The sessionId returned by login_start." },
        styleBIUrl: { type: "string", description: "Same StyleBI URL passed to login_start." }
      },
      required: ["sessionId", "styleBIUrl"]
    },
    call: (args) => runLoginComplete(args, { sessionManager: deps.sessionManager, tokenStore: deps.tokenStore })
  };
}
async function runLoginComplete(args, ctx) {
  if (!args.sessionId || typeof args.sessionId !== "string") {
    throw new Error("sessionId is required");
  }
  if (!args.styleBIUrl || typeof args.styleBIUrl !== "string") {
    throw new Error("styleBIUrl is required");
  }
  const deployment = args.styleBIUrl.replace(/\/$/, "");
  const session = ctx.sessionManager.getAndConsume(args.sessionId);
  if (!session) {
    throw new Error(`Unknown login session: ${args.sessionId}. Run login_start again.`);
  }
  const { nonce } = session;
  const pollIntervalMs = ctx.pollIntervalMs ?? 2e3;
  const timeoutMs = ctx.timeoutMs ?? 5 * 60 * 1e3;
  const url2 = `${deployment}/api/wiz/v1/auth/pickup?nonce=${encodeURIComponent(nonce)}`;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const resp = await axios_default.request({ method: "GET", url: url2, timeout: 15e3 });
      const data = resp.data;
      if (!data || typeof data !== "object" || !data.accessToken || !data.user) {
        throw new Error(
          `Pickup endpoint returned an unexpected response (got ${typeof data === "object" ? JSON.stringify(data) : String(resp.data).slice(0, 120)}). Check that ${deployment} is reachable and is a StyleBI deployment with the wiz agent API enabled.`
        );
      }
      await ctx.tokenStore.save(deployment, {
        accessToken: data.accessToken,
        expiresAt: data.expiresAt,
        user: data.user
      });
      return {
        deployment,
        user: data.user,
        summary: `Logged in as ${data.user.username} (org=${data.user.organizationId}) at ${deployment}.`
      };
    } catch (err) {
      const status = err?.response?.status;
      if (status !== 404) {
        const detail = err?.response?.data?.error ?? err?.message ?? "unknown";
        throw new Error(`Login failed: ${detail}`);
      }
      await sleep(pollIntervalMs);
    }
  }
  throw new Error("Login timed out waiting for SSO. Run login_start again and complete sign-in in the browser.");
}

// ../shared/core/src/tools/searchProductDocs.ts
var MAX_TOP_K = 25;
var KNOWN_DOC_MODULES = [
  "administration",
  "chartAPI",
  "commonscript",
  "dataworksheet",
  "install",
  "integration",
  "ROOT",
  "user",
  "viewsheet",
  "viewsheetscript"
];
var CORPUS_ALIASES = {
  docs: "docs",
  doc: "docs",
  documentation: "docs",
  properties: "properties",
  property: "properties",
  props: "properties"
};
function normalizeArgs(args) {
  const a = typeof args === "object" && args !== null ? args : {};
  const rawQuery = a.query ?? a.q;
  if (typeof rawQuery !== "string" || rawQuery.trim() === "") {
    throw new Error(
      "query: required (a non-blank natural-language question; `q` is an accepted alias)"
    );
  }
  const body = { query: rawQuery.trim() };
  if (a.modules !== void 0 && a.modules !== null) {
    const raw = typeof a.modules === "string" ? [a.modules] : a.modules;
    if (!Array.isArray(raw)) {
      throw new Error(
        `modules: expected an array of module names, or a single name as a string; got ${typeof a.modules}`
      );
    }
    const modules = raw.map((m, i) => {
      if (typeof m !== "string" || m.trim() === "") {
        throw new Error(
          `modules[${i}]: expected a non-blank module name, got ${m === null ? "null" : typeof m}`
        );
      }
      return m.trim();
    });
    if (modules.length > 0) {
      body.modules = modules;
    }
  }
  if (a.corpus !== void 0 && a.corpus !== null) {
    if (typeof a.corpus !== "string") {
      throw new Error(
        `corpus: expected "docs" or "properties", got ${typeof a.corpus}`
      );
    }
    const corpus = CORPUS_ALIASES[a.corpus.trim().toLowerCase()];
    if (corpus === void 0) {
      throw new Error(
        `corpus: unknown value ${JSON.stringify(a.corpus)}; expected "docs" or "properties"`
      );
    }
    if (corpus === "properties" && body.modules !== void 0) {
      throw new Error(
        'modules: not applicable when corpus is "properties" \u2014 omit it, or search corpus "docs"'
      );
    }
    if (corpus !== "docs") {
      body.corpus = corpus;
    }
  }
  if (a.topK !== void 0 && a.topK !== null) {
    const topK = a.topK;
    if (typeof topK !== "number" || !Number.isInteger(topK) || topK < 1 || topK > MAX_TOP_K) {
      throw new Error(`topK: expected an integer between 1 and ${MAX_TOP_K}, got ${JSON.stringify(topK)}`);
    }
    body.topK = topK;
  }
  return body;
}
function makeSearchProductDocsTool(deps) {
  return {
    name: "search_product_docs",
    description: 'Semantic search over the StyleBI documentation. Returns ranked chunks with the text, its module, and a doc URL \u2014 use it to learn how a feature works, what a setting is called, or which property controls a behaviour, when you cannot read the product source. Two corpora are searchable. `corpus: "docs"` (the default) is the end-user product documentation: use it to find the feature and the page. `corpus: "properties"` is per-property reference documentation for Enterprise Manager server properties, written from the source: use it when you need the property NAME, its default, its type, or whether it is org-scoped. A task like "make cached report results last longer" is usually best served by searching `properties` first and falling back to `docs` if nothing fits. Results are ranked but not filtered: judge relevance from the `score` and the text. `modules` applies only to `docs` and is rejected with `properties`. If a filtered search returns nothing, retry without `modules` before concluding the documentation does not cover the topic. Documentation describes behaviour, not the live server \u2014 always confirm a property named in a doc against the server with get_property before acting on it.',
    inputSchema: {
      type: "object",
      properties: {
        query: {
          type: "string",
          description: "Natural language question, e.g. 'how long are report results cached'."
        },
        modules: {
          type: "array",
          items: { type: "string" },
          description: `Optional documentation modules to restrict the search to. Known modules: ${KNOWN_DOC_MODULES.join(", ")}. Matching is case-insensitive; an unknown name is rejected with the full list. Omit to search everything.`
        },
        corpus: {
          type: "string",
          enum: ["docs", "properties"],
          description: 'Which body of documentation to search. "docs" (default) is the end-user product documentation. "properties" is per-property reference documentation for Enterprise Manager server properties, generated from the source and covering the property name, default, type and organization scope. Cannot be combined with `modules`.'
        },
        topK: {
          type: "number",
          description: `Number of matches to return. Integer 1..${MAX_TOP_K}, default 8.`
        }
      },
      required: ["query"]
    },
    call: async (args) => {
      if (deps.preflight) {
        await deps.preflight();
      }
      return deps.wizClient.post("/v1/docs/search", normalizeArgs(args));
    }
  };
}

// ../shared/core/src/tools/routing.ts
async function assertDocsSearchRouting(tokenStore) {
  const active = await tokenStore.getActive();
  if (!active) {
    return;
  }
  const wizServicesUrl = misroutingUrl(active.creds);
  if (wizServicesUrl !== null) {
    throw new Error(
      `Cannot search product docs with these credentials: the login for ${active.deployment} carries a legacy wizServicesUrl=${wizServicesUrl} field from an older plugin version. It no longer affects routing (every call goes straight to StyleBI now), but this stored credential predates that and should be refreshed. Log in again with login_start \u2014 a fresh login cannot recreate this field.`
    );
  }
}
function misroutingUrl(creds) {
  const url2 = creds.wizServicesUrl;
  return typeof url2 === "string" && url2.trim() !== "" ? url2 : null;
}

// src/tools/routing.ts
function misroutingUrl2(creds) {
  const url2 = creds.wizServicesUrl;
  return typeof url2 === "string" && url2.trim() !== "" ? url2 : null;
}
async function assertStyleBIRouting(tokenStore) {
  const active = await tokenStore.getActive();
  if (!active) {
    return;
  }
  const wizServicesUrl = misroutingUrl2(active.creds);
  if (wizServicesUrl !== null) {
    throw new Error(
      `Cannot administer StyleBI with these credentials: the login for ${active.deployment} carries a legacy wizServicesUrl=${wizServicesUrl} field from an older plugin version. It no longer affects routing (every call goes straight to StyleBI now), but this stored credential predates that and should be refreshed. Log in again with login_start \u2014 a fresh login cannot recreate this field.`
    );
  }
}

// src/tools/adminStatus.ts
function makeAdminStatusTool(deps) {
  return {
    name: "status",
    description: "Show the current StyleBI login for admin-chat: which deployment, which user, and whether admin calls will reach the StyleBI server. Verifies the login with a real authenticated call to the admin API, so `adminReachable: false` covers an expired credential, an unreachable server and a server that answered with an error, as well as a misrouted login; `reachability` says which, and the remedies differ. Admin-chat holds no server-side session and no open changeset \u2014 preview_changes and apply_changes are each self-contained \u2014 so there is no session state to report. The deployment reflects the last successful login and can be stale; if the user names a different URL, trust the user and log in again.",
    inputSchema: { type: "object", properties: {} },
    call: () => runAdminStatus(deps)
  };
}
var PROBE_PATH = "/v1/admin/properties?filter=__adminchat_status_probe__";
var PROBE_TIMEOUT_MS = 5e3;
function classify(err) {
  const code = err?.code;
  if (code === "AUTH_REQUIRED") return "unauthorized";
  if (code === "HTTP_403") return "forbidden";
  if (typeof code === "string" && code.startsWith("HTTP_")) return "server-error";
  return "unreachable";
}
function probeMessage(err) {
  const message = err?.message;
  return typeof message === "string" && message.trim() !== "" ? message : "no further detail";
}
async function runAdminStatus(deps) {
  const active = await deps.tokenStore.getActive();
  if (!active) {
    return {
      loggedIn: false,
      adminReachable: false,
      reachability: "not-logged-in",
      summary: "Not logged in. Run /stylebi-admin-chat:login <stylebi-url> first."
    };
  }
  const corruption = [];
  if (!active.creds.user || typeof active.creds.user.username !== "string") {
    corruption.push("missing user info");
  }
  if (typeof active.creds.accessToken !== "string" || active.creds.accessToken.trim() === "") {
    corruption.push("no access token");
  }
  if (corruption.length > 0) {
    return {
      loggedIn: false,
      deployment: active.deployment,
      adminReachable: false,
      reachability: "not-logged-in",
      summary: `Stored credentials for ${active.deployment} are corrupted (${corruption.join(", ")}). Run login_start again to re-authenticate.`
    };
  }
  const username = active.creds.user.username;
  const misrouted = misroutingUrl2(active.creds);
  if (misrouted !== null) {
    return {
      loggedIn: true,
      deployment: active.deployment,
      username,
      adminReachable: false,
      reachability: "misrouted",
      summary: `Logged in as ${username} at ${active.deployment}, but this credential carries a legacy wizServicesUrl=${misrouted} field from an older plugin version.
\u26A0 It no longer affects routing (every call goes straight to StyleBI now), but this stored credential predates that and should be refreshed. Log in again with login_start \u2014 a fresh login cannot recreate this field.`
    };
  }
  let reachability;
  let detail = "";
  try {
    await deps.wizClient.get(PROBE_PATH, { timeoutMs: PROBE_TIMEOUT_MS });
    reachability = "reachable";
  } catch (err) {
    reachability = classify(err);
    detail = probeMessage(err);
  }
  if (reachability === "reachable") {
    return {
      loggedIn: true,
      deployment: active.deployment,
      username,
      adminReachable: true,
      reachability,
      summary: `Logged in as ${username} at ${active.deployment}.`
    };
  }
  if (reachability === "unauthorized") {
    return {
      // Not a usable login, and the caller's next move is the same as having none at all.
      // Reporting true here is what made an expired credential look like a healthy session.
      loggedIn: false,
      deployment: active.deployment,
      username,
      adminReachable: false,
      reachability,
      summary: `Your StyleBI login has expired or been rejected (was ${username} at ${active.deployment}).
\u26A0 Run login_start, then login_complete, to re-authenticate. Server said: ${detail}`
    };
  }
  if (reachability === "forbidden") {
    return {
      loggedIn: true,
      deployment: active.deployment,
      username,
      adminReachable: false,
      reachability,
      summary: `Logged in as ${username} at ${active.deployment}, but this account cannot administer properties.
\u26A0 The admin API requires a Site Administrator with Enterprise Manager properties access. Log in as such a user. Server said: ${detail}`
    };
  }
  if (reachability === "server-error") {
    return {
      loggedIn: true,
      deployment: active.deployment,
      username,
      adminReachable: false,
      reachability,
      summary: `Logged in as ${username} at ${active.deployment}, and the server answered the admin API with an error.
\u26A0 Every admin tool will fail the same way. The login is fine and the server is up, so look at the server's own logs \u2014 or, for a 404, at whether this deployment exposes the /api/wiz admin API at all. Server said: ${detail}`
    };
  }
  return {
    loggedIn: true,
    deployment: active.deployment,
    username,
    adminReachable: false,
    reachability,
    summary: `Logged in as ${username} at ${active.deployment}, but the StyleBI server did not answer.
\u26A0 Every admin tool will fail until it does. Check that the server is running and that ${active.deployment} is the right URL. Probe said: ${detail}`
  };
}

// src/tools/logout.ts
var NOT_UNDONE = "Already-applied property changes are NOT undone by logging out \u2014 use list_changesets and get_changeset while logged in to review or revert them.";
function makeLogoutTool(deps) {
  return {
    name: "logout",
    description: "Clear the stored StyleBI credentials for a deployment, defaulting to the active one. This actually deletes the saved token; a later admin call will report not-logged-in until you run login_start again. It does NOT undo any property change that was already applied.",
    inputSchema: {
      type: "object",
      properties: {
        deployment: {
          type: "string",
          description: "the deployment URL to clear; omit to clear the currently active one"
        }
      }
    },
    call: (args) => runLogout(args ?? {}, deps)
  };
}
async function runLogout(args, deps) {
  const requested = typeof args.deployment === "string" ? args.deployment.trim() : "";
  const target = requested === "" ? null : requested.replace(/\/$/, "");
  if (target === null) {
    const active = await deps.tokenStore.getActive();
    if (!active) {
      return {
        loggedOut: false,
        deployment: null,
        summary: `Not logged in to any StyleBI deployment \u2014 nothing to clear. ${NOT_UNDONE}`
      };
    }
    await deps.tokenStore.delete(active.deployment);
    return {
      loggedOut: true,
      deployment: active.deployment,
      summary: `Cleared the stored credentials for ${active.deployment}. Run /stylebi-admin-chat:login <stylebi-url> to log in again. ${NOT_UNDONE}`
    };
  }
  const existing = await deps.tokenStore.get(target);
  if (!existing) {
    return {
      loggedOut: false,
      deployment: target,
      summary: `No stored credentials for ${target}, so nothing was cleared. Check the URL, or call logout with no arguments to clear the active deployment. ${NOT_UNDONE}`
    };
  }
  await deps.tokenStore.delete(target);
  return {
    loggedOut: true,
    deployment: target,
    summary: `Cleared the stored credentials for ${target}. Run /stylebi-admin-chat:login <stylebi-url> to log in again. ${NOT_UNDONE}`
  };
}

// src/tools/normalize.ts
function isPlainObject2(v) {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}
function coerceValue(raw, label) {
  if (raw === null) {
    return null;
  }
  if (typeof raw === "string") {
    return raw;
  }
  if (typeof raw === "number" || typeof raw === "boolean") {
    return String(raw);
  }
  throw new Error(
    `${label}: expected a string, number, boolean, or null (null resets the property to its default); got ${Array.isArray(raw) ? "array" : typeof raw}`
  );
}
function readProperty(entry, label) {
  const raw = entry.property ?? entry.name ?? entry.key;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      `${label}.property: required (a non-blank property name; \`name\` and \`key\` are accepted aliases)`
    );
  }
  return raw.trim();
}
function readEntry(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { property, value }, got ${typeof raw}`);
  }
  const property = readProperty(raw, label);
  if (!("value" in raw)) {
    throw new Error(
      `${label}.value: required \u2014 pass the new value as a string, or null to reset the property to its default. It is not inferred from omission, because a reset is destructive.`
    );
  }
  return { property, value: coerceValue(raw.value, `${label}.value`) };
}
function looksLikeValueMap(obj) {
  const keys = Object.keys(obj);
  return keys.length > 0 && keys.every((k) => {
    const v = obj[k];
    return v === null || typeof v === "string" || typeof v === "number" || typeof v === "boolean";
  });
}
function normalizeChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { property, value }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    if ("property" in input || "name" in input || "key" in input) {
      entries = [input];
    } else if (looksLikeValueMap(input)) {
      entries = Object.entries(input).map(([property, value]) => ({ property, value }));
    } else {
      throw new Error(
        "changes: required \u2014 an array of { property, value }, a single { property, value }, or a property->value map. The object given is none of those."
      );
    }
  } else {
    throw new Error(
      "changes: required \u2014 an array of { property, value } (use null as the value to reset a property to its default)"
    );
  }
  const normalized = entries.map(readEntry);
  const seen = /* @__PURE__ */ new Map();
  for (const change of normalized) {
    const fold = change.property.toLowerCase();
    const prior = seen.get(fold);
    if (prior !== void 0) {
      throw new Error(
        `changes: duplicate property "${change.property}"` + (prior === change.property ? "" : ` (already present as "${prior}")`) + " \u2014 list each property at most once"
      );
    }
    seen.set(fold, change.property);
  }
  return normalized;
}
function requireTask(input) {
  if (typeof input !== "string" || input.trim() === "") {
    throw new Error(
      "task: required \u2014 a short description of what this change accomplishes. It is written into every audit record for the transaction, so it is how an operator later understands why the change was made."
    );
  }
  return input.trim();
}
function requirePropertyName(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.name ?? obj.property ?? obj.key;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "name: required (the property name; `property` and `key` are accepted aliases). It may be an alias or an org-qualified `inetsoft.org.{orgId}.{property}`."
    );
  }
  return raw.trim();
}
function normalizeScheduleVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "run" || v === "run_now" || v === "trigger") {
    return "run";
  }
  if (v === "stop" || v === "stop_now" || v === "kill") {
    return "stop";
  }
  throw new Error(
    `${label}.verb: must be "create", "delete", "run", or "stop" (also accepts "add"/"remove" as aliases for "create"/"delete", "run_now"/"trigger" as aliases for "run", "stop_now"/"kill" as aliases for "stop"); got ${JSON.stringify(raw)}`
  );
}
function requireTaskId(args, label = "taskId") {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.taskId ?? obj.id ?? obj.name;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(`${label}: required (the schedule task id; \`id\` and \`name\` are accepted aliases)`);
  }
  return raw.trim();
}
var TIME_CONDITION_TYPE_ORDINALS = {
  AT: 0,
  DAY_OF_WEEK: 2,
  EVERY_DAY: 3,
  EVERY_WEEK: 4,
  EVERY_MONTH: 5,
  EVERY_HOUR: 7
};
var DEPRECATED_TIME_CONDITION_TYPE_ORDINALS = {
  DAY_OF_MONTH: 1,
  WEEK_OF_MONTH: 6
};
function foldTimeConditionTypeKey(s) {
  return s.trim().toUpperCase().replace(/[\s_-]+/g, "");
}
var TIME_CONDITION_TYPE_BY_FOLDED_KEY = {};
var DEPRECATED_TIME_CONDITION_TYPE_BY_FOLDED_KEY = {};
for (const name of Object.keys(TIME_CONDITION_TYPE_ORDINALS)) {
  TIME_CONDITION_TYPE_BY_FOLDED_KEY[foldTimeConditionTypeKey(name)] = name;
}
for (const name of Object.keys(DEPRECATED_TIME_CONDITION_TYPE_ORDINALS)) {
  DEPRECATED_TIME_CONDITION_TYPE_BY_FOLDED_KEY[foldTimeConditionTypeKey(name)] = name;
}
function normalizeTimeConditionType(raw, label) {
  const recommendedList = Object.keys(TIME_CONDITION_TYPE_ORDINALS).map((name) => `"${name}" (${TIME_CONDITION_TYPE_ORDINALS[name]})`).join(", ");
  if (typeof raw === "number" && Number.isInteger(raw)) {
    const deprecatedName = Object.keys(DEPRECATED_TIME_CONDITION_TYPE_ORDINALS).find((name) => DEPRECATED_TIME_CONDITION_TYPE_ORDINALS[name] === raw);
    if (deprecatedName !== void 0) {
      throw new Error(
        `${label}.type: ${raw} ("${deprecatedName}") is deprecated and not supported in this area's first cut \u2014 use "EVERY_MONTH" (5) instead.`
      );
    }
    if (Object.values(TIME_CONDITION_TYPE_ORDINALS).includes(raw)) {
      return raw;
    }
    throw new Error(
      `${label}.type: ${raw} is not a recognized time-condition type ordinal \u2014 must be one of ${recommendedList}`
    );
  }
  if (typeof raw === "string") {
    const folded = foldTimeConditionTypeKey(raw);
    const deprecatedName = DEPRECATED_TIME_CONDITION_TYPE_BY_FOLDED_KEY[folded];
    if (deprecatedName !== void 0) {
      throw new Error(
        `${label}.type: "${raw}" is deprecated and not supported in this area's first cut \u2014 use "EVERY_MONTH" instead.`
      );
    }
    const canonicalName = TIME_CONDITION_TYPE_BY_FOLDED_KEY[folded];
    if (canonicalName !== void 0) {
      return TIME_CONDITION_TYPE_ORDINALS[canonicalName];
    }
  }
  throw new Error(
    `${label}.type: must be one of ${recommendedList} (case-insensitive; spaces/hyphens/underscores are folded, e.g. "every-day"/"everyday" both match "EVERY_DAY"), or the equivalent ordinal number; got ${JSON.stringify(raw)}`
  );
}
var ISO_OFFSET_DATE_TIME_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})$/;
function normalizeTimeConditionDate(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  const fromEpochMillis = (ms) => {
    const date = new Date(ms);
    if (Number.isNaN(date.getTime())) {
      throw new Error(`${label}.date: ${ms} is not a valid epoch-millis timestamp`);
    }
    return date.toISOString();
  };
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return fromEpochMillis(raw);
  }
  if (typeof raw === "string") {
    if (/^-?\d+$/.test(raw)) {
      return fromEpochMillis(Number(raw));
    }
    if (ISO_OFFSET_DATE_TIME_RE.test(raw)) {
      return raw;
    }
  }
  throw new Error(
    `${label}.date: expected an ISO-8601 string with an explicit UTC offset (e.g. "2026-09-10T03:00:00Z"), or the equivalent epoch-millis number/numeric string; got ${JSON.stringify(raw)} \u2014 note this field is an OffsetDateTime server-side, NOT an epoch-millis long like the sibling startDate/endDate fields`
  );
}
function normalizeScheduleDate(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === "string" && /^-?\d+$/.test(raw)) {
    return Number(raw);
  }
  throw new Error(
    `${label}: expected an epoch-millis number, or numeric string; got ${JSON.stringify(raw)} \u2014 this field is a plain long server-side, NOT an OffsetDateTime like the sibling conditions[].date field`
  );
}
function requireToken(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.token;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error("token: required \u2014 the token returned by the matching kick-off tool");
  }
  return raw.trim();
}
function optionalTimeoutMs(raw) {
  if (raw === void 0 || raw === null) {
    return 3e5;
  }
  const n = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isInteger(n) || n <= 0) {
    throw new Error(`timeoutMs: expected a positive integer number of milliseconds, got ${JSON.stringify(raw)}`);
  }
  return n;
}
function normalizeActionStringListField(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (typeof raw === "string") {
    if (raw.trim() === "") {
      throw new Error(`${label}: expected a non-blank string, or an array of non-blank strings; got an empty string`);
    }
    return [raw];
  }
  if (Array.isArray(raw) && raw.every((v) => typeof v === "string" && v.trim() !== "")) {
    return raw;
  }
  throw new Error(
    `${label}: expected an array of non-blank strings (a single string is accepted as a one-element-array alias); got ${JSON.stringify(raw)}`
  );
}
function requireNoAtConditionTimeOfDay(condition, label) {
  for (const field of ["hour", "minute", "second"]) {
    const v = condition[field];
    if (v !== void 0 && v !== null && v !== -1) {
      throw new Error(
        `${label}.${field}: not used for an AT condition \u2014 an AT condition's run time is governed entirely by date server-side; remove ${field} or set it to -1 ("not used")`
      );
    }
  }
}
function requireIntInRange(value, min, max, label) {
  if (typeof value !== "number" || !Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${label}: expected an integer between ${min} and ${max}; got ${JSON.stringify(value)}`);
  }
  return value;
}
function requireNonZeroIntInRange(value, min, max, label) {
  if (value === 0) {
    throw new Error(`${label}: 0 is not a legal value here (it silently matches every day server-side, not "unset"); expected a non-zero integer between ${min} and ${max}`);
  }
  return requireIntInRange(value, min, max, label);
}
function requireIntArrayInRange(value, min, max, label) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error(`${label}: required, a non-empty array of integers between ${min} and ${max}; got ${JSON.stringify(value)}`);
  }
  value.forEach((v, i) => requireIntInRange(v, min, max, `${label}[${i}]`));
}
function requireTimeConditionFieldsForType(condition, ordinal, label) {
  const T = TIME_CONDITION_TYPE_ORDINALS;
  if (ordinal === T.DAY_OF_WEEK || ordinal === T.EVERY_WEEK || ordinal === T.EVERY_HOUR) {
    requireIntArrayInRange(condition.daysOfWeek, 1, 7, `${label}.daysOfWeek`);
  }
  if (ordinal === T.EVERY_MONTH) {
    requireIntArrayInRange(condition.monthsOfYear, 0, 11, `${label}.monthsOfYear`);
    const hasDayOfMonth = condition.dayOfMonth !== void 0 && condition.dayOfMonth !== null;
    const weekOfMonthGiven = condition.weekOfMonth !== void 0 && condition.weekOfMonth !== null;
    const hasWeekOfMonth = weekOfMonthGiven && !(hasDayOfMonth && typeof condition.weekOfMonth === "number" && condition.weekOfMonth <= 0);
    if (hasDayOfMonth === hasWeekOfMonth) {
      throw new Error(
        `${label}: EVERY_MONTH requires exactly one of dayOfMonth (a specific day, e.g. the 15th; negative counts from the end of the month) OR weekOfMonth+dayOfWeek (e.g. the 2nd Tuesday) \u2014 got ${hasDayOfMonth ? "both" : "neither"}`
      );
    }
    if (hasDayOfMonth) {
      requireNonZeroIntInRange(condition.dayOfMonth, -31, 31, `${label}.dayOfMonth`);
      condition.weekOfMonth = -1;
    } else {
      requireNonZeroIntInRange(condition.weekOfMonth, 1, 5, `${label}.weekOfMonth`);
      requireIntInRange(condition.dayOfWeek, 1, 7, `${label}.dayOfWeek`);
    }
  }
}
var MASKED_SCHEDULE_TASK_PASSWORD = "**********";
function validateScheduleAlerts(raw, label) {
  if (raw === void 0 || raw === null) {
    return;
  }
  if (!Array.isArray(raw)) {
    throw new Error(`${label}.alerts: expected an array of { elementId, highlightName }, got ${typeof raw}`);
  }
  raw.forEach((alert, alertIndex) => {
    const alertLabel = `${label}.alerts[${alertIndex}]`;
    if (!isPlainObject2(alert)) {
      throw new Error(`${alertLabel}: expected an object with elementId/highlightName, got ${typeof alert}`);
    }
    if (typeof alert.elementId !== "string" || alert.elementId.trim() === "") {
      throw new Error(`${alertLabel}.elementId: required non-blank string`);
    }
    if (typeof alert.highlightName !== "string" || alert.highlightName.trim() === "") {
      throw new Error(`${alertLabel}.highlightName: required non-blank string`);
    }
  });
}
function normalizeScheduleParameterValue(raw, label) {
  if (raw === null || typeof raw === "string" || typeof raw === "number" || typeof raw === "boolean") {
    return;
  }
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected a scalar, or an object like { value, dataType }, got ${typeof raw}`);
  }
  if (!("value" in raw)) {
    throw new Error(`${label}.value: required when a parameter is given as an object, not a bare scalar`);
  }
  if (typeof raw.dataType !== "string" || raw.dataType.trim() === "") {
    throw new Error(`${label}.dataType: required non-blank string when a parameter is given as an object`);
  }
  if ("type" in raw) {
    const rawType = raw.type;
    const foldedType = typeof rawType === "string" ? rawType.trim().toUpperCase() : rawType;
    if (foldedType !== "EXPRESSION") {
      throw new Error(
        `${label}.type: only "expression" is recognized here (any case; omit type for a plain value); got ${JSON.stringify(rawType)}`
      );
    }
    const value = raw.value;
    if (typeof value !== "string") {
      throw new Error(
        `${label}.value: must be a string when type is "expression" (the server expects the raw expression source text, optionally prefixed with "="); got ${typeof value}`
      );
    }
    raw.type = "EXPRESSION";
    raw.value = value.startsWith("=") ? value : "=" + value;
  }
  if ("array" in raw && typeof raw.array !== "boolean") {
    throw new Error(`${label}.array: must be boolean when present`);
  }
}
function validateScheduleParameters(raw, label) {
  if (raw === void 0 || raw === null) {
    return;
  }
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}.parameters: expected an object of paramName -> value, got ${typeof raw}`);
  }
  for (const key of Object.keys(raw)) {
    normalizeScheduleParameterValue(raw[key], `${label}.parameters.${key}`);
  }
}
function validateSaveToServerFilePaths(raw, label) {
  if (raw === void 0 || raw === null) {
    return;
  }
  if (!Array.isArray(raw)) {
    throw new Error(`${label}.saveToServerFilePaths: expected an array of { format, path, ... }, got ${typeof raw}`);
  }
  raw.forEach((entry, entryIndex) => {
    const entryLabel = `${label}.saveToServerFilePaths[${entryIndex}]`;
    if (!isPlainObject2(entry)) {
      throw new Error(`${entryLabel}: expected an object with format/path, got ${typeof entry}`);
    }
    if (typeof entry.format !== "string" || entry.format.trim() === "") {
      throw new Error(`${entryLabel}.format: required non-blank string`);
    }
    if (typeof entry.path !== "string" || entry.path.trim() === "") {
      throw new Error(`${entryLabel}.path: required non-blank string`);
    }
    const useCredential = entry.useCredential === true;
    const hasSecretId = typeof entry.secretId === "string" && entry.secretId.trim() !== "";
    const hasUsername = typeof entry.username === "string" && entry.username.trim() !== "";
    const hasPassword = typeof entry.password === "string" && entry.password.trim() !== "";
    if (hasPassword && entry.password === MASKED_SCHEDULE_TASK_PASSWORD) {
      throw new Error(
        `${entryLabel}.password: this is the masked placeholder get_schedule_task returns on read, not a real password \u2014 omit password (and username) here, or supply the real credential; sending the placeholder back would persist it as a literal password`
      );
    }
    if (useCredential) {
      if (!hasSecretId) {
        throw new Error(
          `${entryLabel}.secretId: required when useCredential=true (the credential is resolved by reference, not supplied inline)`
        );
      }
      if (hasUsername || hasPassword) {
        throw new Error(
          `${entryLabel}.username/password: not used when useCredential=true \u2014 the credential comes from secretId instead; remove username/password or set useCredential=false`
        );
      }
    } else {
      if (!hasUsername || !hasPassword) {
        throw new Error(
          `${entryLabel}.username/password: both required when useCredential=false (the default) \u2014 pass both, or set useCredential=true and provide secretId instead`
        );
      }
      if (hasSecretId) {
        throw new Error(
          `${entryLabel}.secretId: not used when useCredential=false (or omitted) \u2014 remove it or set useCredential=true`
        );
      }
    }
  });
}
function normalizeScheduleBoolean(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (typeof raw === "boolean") {
    return raw;
  }
  throw new Error(`${label}: must be a boolean; got ${typeof raw}`);
}
function normalizeExecuteAsID(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { type: "USER"|"GROUP", identityID: {name, orgID} }, got ${typeof raw}`
    );
  }
  const type = typeof raw.type === "string" ? raw.type.trim().toUpperCase() : raw.type;
  if (type !== "USER" && type !== "GROUP") {
    throw new Error(`${label}.type: must be "USER" or "GROUP"; got ${JSON.stringify(raw.type)}`);
  }
  if (!isPlainObject2(raw.identityID)) {
    throw new Error(`${label}.identityID: required object { name, orgID }, got ${typeof raw.identityID}`);
  }
  if (typeof raw.identityID.name !== "string" || raw.identityID.name.trim() === "") {
    throw new Error(`${label}.identityID.name: required non-blank string`);
  }
  if ("orgID" in raw.identityID && raw.identityID.orgID !== void 0 && raw.identityID.orgID !== null && (typeof raw.identityID.orgID !== "string" || raw.identityID.orgID.trim() === "")) {
    throw new Error(`${label}.identityID.orgID: must be a non-blank string when present`);
  }
  return { type, identityID: raw.identityID };
}
function isWellFormedIdentityRef(raw) {
  if (!isPlainObject2(raw)) {
    return false;
  }
  if (typeof raw.name !== "string" || raw.name.trim() === "") {
    return false;
  }
  if ("orgID" in raw && raw.orgID !== void 0 && raw.orgID !== null && (typeof raw.orgID !== "string" || raw.orgID.trim() === "")) {
    return false;
  }
  return true;
}
function normalizeBookmarkUsers(action, actionLabel, owner) {
  const bookmarkNames = action.bookmarkNames;
  if (!Array.isArray(bookmarkNames) || bookmarkNames.length === 0) {
    return;
  }
  if (action.bookmarkUsers === void 0 || action.bookmarkUsers === null) {
    if (!isWellFormedIdentityRef(owner)) {
      throw new Error(
        `${actionLabel}.bookmarkUsers: required when bookmarkNames is set and bookmarkUsers is omitted, and spec.owner (the value this would otherwise default to) is missing or malformed \u2014 supply either a well-formed spec.owner or an explicit bookmarkUsers array`
      );
    }
    action.bookmarkUsers = bookmarkNames.map(() => ({ name: owner.name, orgID: owner.orgID }));
    return;
  }
  if (!Array.isArray(action.bookmarkUsers) || action.bookmarkUsers.length !== bookmarkNames.length) {
    throw new Error(
      `${actionLabel}.bookmarkUsers: must be an array with one entry per bookmarkNames entry (bookmarkNames has ${bookmarkNames.length} entries, bookmarkUsers has ${Array.isArray(action.bookmarkUsers) ? action.bookmarkUsers.length : typeof action.bookmarkUsers})`
    );
  }
  action.bookmarkUsers.forEach((entry, entryIndex) => {
    if (!isWellFormedIdentityRef(entry)) {
      throw new Error(
        `${actionLabel}.bookmarkUsers[${entryIndex}]: expected an object like {name, orgID}; got ${JSON.stringify(entry)}`
      );
    }
  });
}
function readScheduleChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, taskId, spec }, got ${typeof raw}`);
  }
  const verb = normalizeScheduleVerb(raw.verb, label);
  if (verb === "delete" || verb === "run" || verb === "stop") {
    if ("spec" in raw && raw.spec !== void 0 && raw.spec !== null) {
      throw new Error(`${label}.spec: not used for verb=${verb}; remove it or use verb=create`);
    }
    return { verb, taskId: requireTaskId(raw, `${label}.taskId`) };
  }
  if (!isPlainObject2(raw.spec)) {
    throw new Error(`${label}.spec: required for verb=create (an object with name/owner/conditions)`);
  }
  raw.spec.startDate = normalizeScheduleDate(raw.spec.startDate, `${label}.spec.startDate`);
  raw.spec.endDate = normalizeScheduleDate(raw.spec.endDate, `${label}.spec.endDate`);
  raw.spec.enabled = normalizeScheduleBoolean(raw.spec.enabled, `${label}.spec.enabled`);
  raw.spec.deleteIfNotScheduledToRun = normalizeScheduleBoolean(
    raw.spec.deleteIfNotScheduledToRun,
    `${label}.spec.deleteIfNotScheduledToRun`
  );
  raw.spec.executeAsID = normalizeExecuteAsID(raw.spec.executeAsID, `${label}.spec.executeAsID`);
  const conditions = raw.spec.conditions;
  if (conditions !== void 0 && conditions !== null && !Array.isArray(conditions)) {
    throw new Error(`${label}.spec.conditions: expected an array of condition objects, got ${typeof conditions}`);
  }
  if (Array.isArray(conditions)) {
    conditions.forEach((condition, conditionIndex) => {
      const conditionLabel = `${label}.spec.conditions[${conditionIndex}]`;
      if (!isPlainObject2(condition) || condition.conditionType !== "time") {
        throw new Error(
          `${conditionLabel}.conditionType: only "time" is supported in this area's first cut \u2014 a completion condition (or any other conditionType) is refused, not silently forwarded; got ${JSON.stringify(isPlainObject2(condition) ? condition.conditionType : condition)}`
        );
      }
      condition.type = normalizeTimeConditionType(condition.type, conditionLabel);
      condition.date = normalizeTimeConditionDate(condition.date, conditionLabel);
      if (condition.type === TIME_CONDITION_TYPE_ORDINALS.AT) {
        requireNoAtConditionTimeOfDay(condition, conditionLabel);
      }
      requireTimeConditionFieldsForType(condition, condition.type, conditionLabel);
    });
  }
  const actions = raw.spec.actions;
  const owner = raw.spec.owner;
  if (actions !== void 0 && actions !== null && !Array.isArray(actions)) {
    throw new Error(`${label}.spec.actions: expected an array of action objects, got ${typeof actions}`);
  }
  if (Array.isArray(actions)) {
    actions.forEach((action, actionIndex) => {
      const actionLabel = `${label}.spec.actions[${actionIndex}]`;
      if (!isPlainObject2(action) || action.actionType !== "viewsheet") {
        throw new Error(
          `${actionLabel}.actionType: only "viewsheet" is supported in this area's first cut \u2014 a batch or asset-backup action (or any other actionType) is refused, not silently forwarded; got ${JSON.stringify(isPlainObject2(action) ? action.actionType : action)}`
        );
      }
      action.viewsheet = requireViewsheetAssetId(action.viewsheet, actionLabel, "viewsheet");
      validateScheduleAlerts(action.alerts, actionLabel);
      validateScheduleParameters(action.parameters, actionLabel);
      validateSaveToServerFilePaths(action.saveToServerFilePaths, actionLabel);
      for (const field of ["emails", "notifies", "bookmarkNames"]) {
        const normalized = normalizeActionStringListField(action[field], `${actionLabel}.${field}`);
        if (normalized !== void 0) {
          action[field] = normalized;
        }
      }
      normalizeBookmarkUsers(action, actionLabel, owner);
    });
  }
  return { verb, spec: raw.spec };
}
function normalizeScheduleChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, taskId|spec }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, taskId|spec } (verb is "create", "delete", "run", or "stop")'
    );
  }
  return entries.map(readScheduleChange);
}
var ALLOWED_RESOURCE_TYPES = /* @__PURE__ */ new Set([
  "ASSET",
  "REPORT",
  "DASHBOARD",
  "DATA_SOURCE",
  "DATA_SOURCE_FOLDER",
  "QUERY",
  "QUERY_FOLDER",
  "DATA_MODEL_FOLDER",
  "SCRIPT",
  "SCRIPT_LIBRARY",
  "TABLE_STYLE",
  "TABLE_STYLE_LIBRARY",
  "CHART_TYPE",
  "CHART_TYPE_FOLDER",
  "CUBE",
  "PROTOTYPE",
  "LIBRARY",
  "VIEWSHEET_ACTION",
  "SCHEDULE_OPTION",
  "AI_ASSISTANT",
  "CROSS_JOIN",
  "CREATE_DATA_SOURCE",
  "VIEWSHEET_CALCULATED_FIELD",
  "WORKSHEET_EXPRESSION_COLUMN",
  "FREE_FORM_SQL",
  "MATERIALIZATION",
  "MY_DASHBOARDS",
  "PHYSICAL_TABLE",
  "PORTAL_REPOSITORY_TREE_DRAG_AND_DROP",
  "PROFILE",
  "UPLOAD_DRIVERS",
  "DEVICE",
  "VIEWSHEET",
  "WORKSHEET",
  "VIEWSHEET_TOOLBAR_ACTION",
  "SHARE",
  "SCHEDULER",
  "PORTAL_TAB"
]);
function normalizePermissionVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "update" || v === "set" || v === "modify") {
    return "update";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  throw new Error(
    `${label}.verb: must be "create", "update", or "delete" (also accepts "add"/"set"|"modify"/"remove" as aliases); got ${JSON.stringify(raw)}`
  );
}
function normalizeIdentityType(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toUpperCase() : raw;
  if (v === "USER" || v === "GROUP" || v === "ROLE" || v === "ORGANIZATION") {
    return v;
  }
  throw new Error(
    `${label}.identityType: must be "USER", "GROUP", "ROLE", or "ORGANIZATION"; got ${JSON.stringify(raw)}`
  );
}
function requireAllowedResourceType(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toUpperCase() : raw;
  if (typeof v !== "string" || v === "") {
    throw new Error(`${label}.resourceType: required`);
  }
  if (!ALLOWED_RESOURCE_TYPES.has(v)) {
    if (v === "SCHEDULE_TASK" || v === "SCHEDULE_TASK_FOLDER") {
      throw new Error(
        `${label}.resourceType: "${v}" is not supported in this area's first cut \u2014 the EM path's owner-based permission check is not replicated by this API, a found parity gap, not an oversight. Manage schedule-task permissions through Enterprise Manager directly.`
      );
    }
    throw new Error(
      `${label}.resourceType: "${v}" is not in this area's resource-type allowlist (${[...ALLOWED_RESOURCE_TYPES].join(", ")}). SECURITY_*/LOGIN_AS are deliberately excluded pending the identities area; EM/EM_COMPONENT are excluded because the server routes every Security Actions capability type through a single EM_COMPONENT/"settings/security/actions" ACCESS check on the caller, so making EM_COMPONENT itself settable here would let a caller grant themselves that master gate and, through it, editing rights over every capability type in this list at once.`
    );
  }
  return v;
}
function requireIdentityId(raw, label) {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(`${label}.identityId: required (a bare identity name, or "name:orgId")`);
  }
  return raw.trim();
}
function parseNameOrgRef(raw) {
  const idx = raw.indexOf(":");
  if (idx < 0) {
    return { name: raw };
  }
  return { name: raw.slice(0, idx), orgId: raw.slice(idx + 1) };
}
function normalizeOrgQualifiedRefs(raw, specOrgId, label, relationLabel, unitLabel) {
  const arr = typeof raw === "string" ? [raw] : raw;
  if (!Array.isArray(arr)) {
    throw new Error(
      `${label}: expected an array of strings (a bare name, or "name:orgId"); a single string is accepted as a one-element-array alias; got ${typeof raw}`
    );
  }
  return arr.map((entry, i) => {
    if (typeof entry !== "string") {
      throw new Error(
        `${label}[${i}]: expected a string (a bare name, or "name:orgId"), got ${typeof entry} (${JSON.stringify(entry)})`
      );
    }
    if (!entry.includes(":")) {
      return entry;
    }
    const { name, orgId } = parseNameOrgRef(entry);
    if (specOrgId === void 0) {
      throw new Error(
        `${label}[${i}]: "${entry}" looks like a "name:orgId"-shaped reference, but ${relationLabel} has no per-entry organization concept server-side \u2014 it is always resolved within the ${unitLabel}'s own organization (spec.orgId), which is not set on this change. Set spec.orgId explicitly, or use a bare name ("${name}") if this member is in the same organization as the ${unitLabel}.`
      );
    }
    if (orgId !== specOrgId) {
      throw new Error(
        `${label}[${i}]: "${entry}" names organization "${orgId}", but this ${unitLabel}'s own spec.orgId is "${specOrgId}" \u2014 ${relationLabel} is always resolved within the ${unitLabel}'s own organization, not a per-entry one. Remove the ":${orgId}" qualifier if "${name}" is actually in "${specOrgId}", or drop this entry.`
      );
    }
    return name;
  });
}
function normalizeGroupMemberRefs(raw, specOrgId, label) {
  return normalizeOrgQualifiedRefs(raw, specOrgId, label, "group membership", "group");
}
function normalizeRoleAssignedRefs(raw, specOrgId, label) {
  return normalizeOrgQualifiedRefs(raw, specOrgId, label, "role assignment", "role");
}
function normalizeUserGroupRefs(raw, specOrgId, label) {
  return normalizeOrgQualifiedRefs(raw, specOrgId, label, "group membership", "user");
}
function normalizeUserRoleRefs(raw, specOrgId, label) {
  return normalizeOrgQualifiedRefs(raw, specOrgId, label, "role assignment", "user");
}
function normalizeGroupRoleRefs(raw, specOrgId, label) {
  return normalizeOrgQualifiedRefs(raw, specOrgId, label, "role assignment", "group");
}
function readPermissionChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { verb, resourceType, resourcePath, identityType, identityId, actions }, got ${typeof raw}`
    );
  }
  const verb = normalizePermissionVerb(raw.verb, label);
  const resourceType = requireAllowedResourceType(raw.resourceType, label);
  if (typeof raw.resourcePath !== "string" || raw.resourcePath.trim() === "") {
    throw new Error(`${label}.resourcePath: required`);
  }
  const identityType = normalizeIdentityType(raw.identityType, label);
  const identityId = requireIdentityId(raw.identityId, label);
  if (verb === "delete") {
    if ("actions" in raw && raw.actions !== void 0 && raw.actions !== null) {
      throw new Error(`${label}.actions: not used for verb=delete; remove it or use verb=create/update`);
    }
    return { verb, resourceType, resourcePath: raw.resourcePath.trim(), identityType, identityId };
  }
  if (!Array.isArray(raw.actions) || raw.actions.length === 0 || !raw.actions.every((a) => typeof a === "string" && a.trim() !== "")) {
    throw new Error(
      `${label}.actions: required for verb=${verb} \u2014 a non-empty array of action names (READ, WRITE, DELETE, ACCESS, SHARE, ASSIGN, ADMIN)`
    );
  }
  return {
    verb,
    resourceType,
    resourcePath: raw.resourcePath.trim(),
    identityType,
    identityId,
    actions: raw.actions.map((a) => a.trim().toUpperCase())
  };
}
function normalizePermissionChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one permission-grant change");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, resourceType, resourcePath, identityType, identityId, actions } (verb is "create", "update", or "delete")'
    );
  }
  return entries.map(readPermissionChange);
}
function normalizeUnitType(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase().replace(/s$/, "") : raw;
  if (v === "user" || v === "group" || v === "role" || v === "organization") {
    return v;
  }
  throw new Error(
    `${label}.unitType: must be "user", "group", "role", or "organization"; got ${JSON.stringify(raw)}`
  );
}
var IDENTITY_SPEC_FIELDS = {
  user: /* @__PURE__ */ new Set(["name", "orgId", "password", "alias", "locale", "active", "emails", "groups", "theme"]),
  group: /* @__PURE__ */ new Set(["name", "orgId", "parentGroups", "memberUsers", "memberGroups", "theme"]),
  role: /* @__PURE__ */ new Set(["name", "orgId", "description", "inheritedRoles", "assignedUsers", "assignedGroups", "theme"]),
  organization: /* @__PURE__ */ new Set(["id", "orgName", "locale", "theme"])
};
var FIELD_OWNER = {
  password: "user",
  alias: "user",
  active: "user",
  emails: "user",
  parentGroups: "group",
  memberUsers: "group",
  memberGroups: "group",
  description: "role",
  inheritedRoles: "role",
  assignedUsers: "role",
  assignedGroups: "role",
  id: "organization",
  orgName: "organization"
};
function validateIdentitySpecFields(unitType, spec, label) {
  const legal = IDENTITY_SPEC_FIELDS[unitType];
  for (const key of Object.keys(spec)) {
    if (spec[key] === void 0 || spec[key] === null) {
      continue;
    }
    if (key === "roles") {
      if (unitType !== "user" && unitType !== "group") {
        throw new Error(
          `${label}.spec.roles: not used for unitType="${unitType}" (did you mean unitType="user" or unitType="group"?)`
        );
      }
      continue;
    }
    if (key === "theme" || legal.has(key)) {
      continue;
    }
    const owner = FIELD_OWNER[key];
    const suggestion = owner ? ` (did you mean unitType="${owner}"?)` : "";
    throw new Error(`${label}.spec.${key}: not used for unitType="${unitType}"${suggestion}`);
  }
}
function normalizeIdentityVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "update") {
    return "update";
  }
  throw new Error(
    `${label}.verb: must be "create", "delete", or "update" (also accepts "add"/"remove" as aliases for create/delete \u2014 update has no alias); got ${JSON.stringify(raw)}`
  );
}
var IDENTITY_LIST_FIELDS = /* @__PURE__ */ new Set([
  "emails",
  "groups",
  "roles",
  "parentGroups",
  "memberUsers",
  "memberGroups",
  "assignedUsers",
  "assignedGroups",
  "inheritedRoles"
]);
function describeIdentityUpdateFieldClear(key) {
  if (key === "active") {
    return "omit it to leave unchanged, or send true/false explicitly";
  }
  if (key === "name") {
    return "omit it to leave unchanged (an empty name is not allowed either)";
  }
  if (IDENTITY_LIST_FIELDS.has(key)) {
    return "omit it to leave unchanged, or send [] to clear it";
  }
  return 'omit it to leave unchanged, or send "" to clear it';
}
function readIdentityChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, unitType, id|spec }, got ${typeof raw}`);
  }
  const verb = normalizeIdentityVerb(raw.verb, label);
  const unitType = normalizeUnitType(raw.unitType, label);
  if (verb === "update") {
    if (typeof raw.id !== "string" || raw.id.trim() === "") {
      throw new Error(`${label}.id: required for verb=update (a bare identity name, or "name:orgId")`);
    }
    const id = raw.id.trim();
    if (!isPlainObject2(raw.spec)) {
      throw new Error(
        `${label}.spec: required for verb=update \u2014 a partial object naming only the fields to change, shaped for unitType="${unitType}"`
      );
    }
    validateIdentitySpecFields(unitType, raw.spec, label);
    if (unitType === "user" && "password" in raw.spec && raw.spec.password !== void 0) {
      throw new Error(
        `${label}.spec.password: not used for verb="update" \u2014 password rotation is out of scope for update in this cut (spec.password is silently unreachable by SecurityApiService.updateUser anyway; refusing here prevents you from believing it took effect). Remove it; the account's password is left completely untouched by every other field you do change.`
      );
    }
    if ((unitType === "user" || unitType === "group" || unitType === "role") && "orgId" in raw.spec && raw.spec.orgId !== void 0) {
      throw new Error(
        `${label}.spec.orgId: not used for verb="update" \u2014 an identity's organization is fixed by the id you're targeting, not settable via spec (this closes an org-smuggling path: the validated org comes from the resolved id, never from the request body). Omit it; use "name:orgId" in the top-level id if you need to disambiguate which organization's identity you're targeting.`
      );
    }
    if (unitType === "organization" && "id" in raw.spec && raw.spec.id !== void 0) {
      throw new Error(
        `${label}.spec.id: not used for verb="update" on unitType="organization" \u2014 the organization's own id is fixed by the id argument you're targeting; renaming an organization's id (as opposed to its display name, spec.orgName) is out of scope for update in this cut.`
      );
    }
    if (Object.keys(raw.spec).length === 0) {
      throw new Error(
        `${label}.spec: at least one field is required for verb="update" (nothing to change) \u2014 shaped for unitType="${unitType}"`
      );
    }
    const spec = { ...raw.spec };
    const specOrgId = unitType === "organization" ? id : parseNameOrgRef(id).orgId;
    if (unitType === "user") {
      if (spec.groups !== void 0 && spec.groups !== null) {
        spec.groups = normalizeUserGroupRefs(spec.groups, specOrgId, `${label}.spec.groups`);
      }
      if (spec.roles !== void 0 && spec.roles !== null) {
        spec.roles = normalizeUserRoleRefs(spec.roles, specOrgId, `${label}.spec.roles`);
      }
    } else if (unitType === "group") {
      if (spec.memberUsers !== void 0 && spec.memberUsers !== null) {
        spec.memberUsers = normalizeGroupMemberRefs(spec.memberUsers, specOrgId, `${label}.spec.memberUsers`);
      }
      if (spec.memberGroups !== void 0 && spec.memberGroups !== null) {
        spec.memberGroups = normalizeGroupMemberRefs(spec.memberGroups, specOrgId, `${label}.spec.memberGroups`);
      }
      if (spec.roles !== void 0 && spec.roles !== null) {
        spec.roles = normalizeGroupRoleRefs(spec.roles, specOrgId, `${label}.spec.roles`);
      }
      if (spec.parentGroups !== void 0 && spec.parentGroups !== null) {
        spec.parentGroups = normalizeGroupMemberRefs(spec.parentGroups, specOrgId, `${label}.spec.parentGroups`);
      }
    } else if (unitType === "role") {
      if (spec.assignedUsers !== void 0 && spec.assignedUsers !== null) {
        spec.assignedUsers = normalizeRoleAssignedRefs(spec.assignedUsers, specOrgId, `${label}.spec.assignedUsers`);
      }
      if (spec.assignedGroups !== void 0 && spec.assignedGroups !== null) {
        spec.assignedGroups = normalizeRoleAssignedRefs(spec.assignedGroups, specOrgId, `${label}.spec.assignedGroups`);
      }
      if (spec.inheritedRoles !== void 0 && spec.inheritedRoles !== null) {
        spec.inheritedRoles = normalizeRoleAssignedRefs(spec.inheritedRoles, specOrgId, `${label}.spec.inheritedRoles`);
      }
    }
    for (const key of Object.keys(spec)) {
      const value = spec[key];
      if (key === "name" && typeof value === "string" && value.trim() === "") {
        throw new Error(
          `${label}.spec.name: an empty name is not allowed for verb="update" (omit "name" to leave it unchanged)`
        );
      }
      if (value === null) {
        throw new Error(
          `${label}.spec.${key}: explicit null is not allowed for verb="update" (the server cannot tell an explicit null apart from an omitted key) \u2014 ${describeIdentityUpdateFieldClear(key)}.`
        );
      }
    }
    return { verb, unitType, id, spec };
  }
  if (verb === "delete") {
    if ("spec" in raw && raw.spec !== void 0 && raw.spec !== null) {
      throw new Error(`${label}.spec: not used for verb=delete; remove it or use verb=create`);
    }
    if (typeof raw.id !== "string" || raw.id.trim() === "") {
      throw new Error(`${label}.id: required for verb=delete (a bare identity name, or "name:orgId")`);
    }
    return { verb, unitType, id: raw.id.trim() };
  }
  if ("id" in raw && raw.id !== void 0 && raw.id !== null) {
    throw new Error(`${label}.id: not used for verb=create; the id is derived from spec`);
  }
  if (!isPlainObject2(raw.spec)) {
    throw new Error(
      `${label}.spec: required for verb=create (an object shaped for unitType="${unitType}")`
    );
  }
  validateIdentitySpecFields(unitType, raw.spec, label);
  if (unitType === "user") {
    const spec = { ...raw.spec };
    const specOrgId = typeof spec.orgId === "string" && spec.orgId.trim() !== "" ? spec.orgId.trim() : void 0;
    if (spec.groups !== void 0 && spec.groups !== null) {
      spec.groups = normalizeUserGroupRefs(spec.groups, specOrgId, `${label}.spec.groups`);
    }
    if (spec.roles !== void 0 && spec.roles !== null) {
      spec.roles = normalizeUserRoleRefs(spec.roles, specOrgId, `${label}.spec.roles`);
    }
    return { verb, unitType, spec };
  }
  if (unitType === "group") {
    const spec = { ...raw.spec };
    const specOrgId = typeof spec.orgId === "string" && spec.orgId.trim() !== "" ? spec.orgId.trim() : void 0;
    if (spec.memberUsers !== void 0 && spec.memberUsers !== null) {
      spec.memberUsers = normalizeGroupMemberRefs(spec.memberUsers, specOrgId, `${label}.spec.memberUsers`);
    }
    if (spec.memberGroups !== void 0 && spec.memberGroups !== null) {
      spec.memberGroups = normalizeGroupMemberRefs(spec.memberGroups, specOrgId, `${label}.spec.memberGroups`);
    }
    if (spec.roles !== void 0 && spec.roles !== null) {
      spec.roles = normalizeGroupRoleRefs(spec.roles, specOrgId, `${label}.spec.roles`);
    }
    if (spec.parentGroups !== void 0 && spec.parentGroups !== null) {
      spec.parentGroups = normalizeGroupMemberRefs(spec.parentGroups, specOrgId, `${label}.spec.parentGroups`);
    }
    return { verb, unitType, spec };
  }
  if (unitType === "role") {
    const spec = { ...raw.spec };
    const specOrgId = typeof spec.orgId === "string" && spec.orgId.trim() !== "" ? spec.orgId.trim() : void 0;
    if (spec.assignedUsers !== void 0 && spec.assignedUsers !== null) {
      spec.assignedUsers = normalizeRoleAssignedRefs(spec.assignedUsers, specOrgId, `${label}.spec.assignedUsers`);
    }
    if (spec.assignedGroups !== void 0 && spec.assignedGroups !== null) {
      spec.assignedGroups = normalizeRoleAssignedRefs(spec.assignedGroups, specOrgId, `${label}.spec.assignedGroups`);
    }
    if (spec.inheritedRoles !== void 0 && spec.inheritedRoles !== null) {
      spec.inheritedRoles = normalizeRoleAssignedRefs(spec.inheritedRoles, specOrgId, `${label}.spec.inheritedRoles`);
    }
    return { verb, unitType, spec };
  }
  return { verb, unitType, spec: raw.spec };
}
function normalizeIdentityChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, unitType, id|spec }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, unitType, id|spec } (verb is "create", "delete", or "update"; unitType is "user", "group", "role", or "organization")'
    );
  }
  return entries.map(readIdentityChange);
}
function normalizeChain(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "authentication" || v === "authorization") {
    return v;
  }
  throw new Error(
    `${label}.chain: must be exactly "authentication" or "authorization" \u2014 no abbreviations are accepted here ("auth" is ambiguous between the two); got ${JSON.stringify(raw)}`
  );
}
function normalizeProviderType(raw, chain, label) {
  const v = typeof raw === "string" ? raw.trim().toUpperCase() : raw;
  if (v !== "FILE" && v !== "LDAP" && v !== "DATABASE" && v !== "CUSTOM") {
    throw new Error(
      `${label}.providerType: required, must be "FILE" or "LDAP" (chain="authorization" accepts only "FILE"); got ${JSON.stringify(raw)}`
    );
  }
  if (v === "DATABASE") {
    throw new Error(
      `${label}.providerType: "DATABASE" is not supported in this area's first cut \u2014 the underlying service does not itself enforce the license gate the Enterprise Manager UI uses to hide this option on a non-enterprise license, and this area's own service self-imposes the restriction instead of inheriting that gap. Use "FILE" or "LDAP" instead.`
    );
  }
  if (v === "CUSTOM") {
    throw new Error(
      `${label}.providerType: "CUSTOM" is not supported in this area's first cut \u2014 it loads a caller-named class from the server's classpath and feeds it a schema-free configuration blob, an unbounded secrets/injection surface this area does not attempt to reason about safely. Use "FILE" or "LDAP" instead.`
    );
  }
  if (chain === "authorization" && v !== "FILE") {
    throw new Error(
      `${label}.providerType: the authorization chain accepts only "FILE" in this area's first cut; "${v}" is authentication-chain-only.`
    );
  }
  return v;
}
var LDAP_SPEC_FIELDS = /* @__PURE__ */ new Set([
  "ldapServer",
  "protocol",
  "hostName",
  "hostPort",
  "rootDN",
  "useCredential",
  "adminID",
  "password",
  "secretId",
  "userFilter",
  "userBase",
  "userAttr",
  "mailAttr",
  "groupFilter",
  "groupBase",
  "groupAttr",
  "roleFilter",
  "roleBase",
  "roleAttr",
  "userRoleFilter",
  "roleRoleFilter",
  "groupRoleFilter",
  "startTls",
  "searchTree",
  "sysAdminRoles"
]);
function validateLdapSpecFields(spec, label) {
  for (const key of Object.keys(spec)) {
    if (spec[key] === void 0 || spec[key] === null) {
      continue;
    }
    if (!LDAP_SPEC_FIELDS.has(key)) {
      throw new Error(`${label}.spec.${key}: not a recognized LDAP provider field`);
    }
  }
}
function validateLdapPartialCredentialMode(spec, label) {
  const hasSecretId = typeof spec.secretId === "string" && spec.secretId.trim() !== "";
  const hasAdminId = typeof spec.adminID === "string" && spec.adminID.trim() !== "";
  const hasPassword = typeof spec.password === "string" && spec.password.trim() !== "";
  if (hasSecretId && (hasAdminId || hasPassword)) {
    throw new Error(
      `${label}.spec.secretId/adminID/password: secretId (credential-by-reference) cannot be sent together with adminID/password (credential-by-value) in the same update \u2014 these belong to opposite useCredential modes; send only the fields for the mode you intend to (re)set`
    );
  }
}
function validateLdapCredentialMode(spec, label) {
  const useCredential = spec.useCredential === true;
  const hasSecretId = typeof spec.secretId === "string" && spec.secretId.trim() !== "";
  const hasAdminId = typeof spec.adminID === "string" && spec.adminID.trim() !== "";
  const hasPassword = typeof spec.password === "string" && spec.password.trim() !== "";
  if (useCredential) {
    if (!hasSecretId) {
      throw new Error(
        `${label}.spec.secretId: required when useCredential=true (the credential is resolved by reference, not supplied inline)`
      );
    }
    if (hasAdminId || hasPassword) {
      throw new Error(
        `${label}.spec.adminID/password: not used when useCredential=true \u2014 the credential comes from secretId instead; remove adminID/password or set useCredential=false`
      );
    }
  } else {
    if (!hasAdminId || !hasPassword) {
      throw new Error(
        `${label}.spec.adminID/password: both required when useCredential=false (the default) \u2014 pass both, or set useCredential=true and provide secretId instead`
      );
    }
    if (hasSecretId) {
      throw new Error(
        `${label}.spec.secretId: not used when useCredential=false (or omitted) \u2014 remove it or set useCredential=true`
      );
    }
  }
}
function normalizeProviderVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "duplicate" || v === "copy" || v === "clone") {
    return "duplicate";
  }
  if (v === "update" || v === "modify" || v === "edit") {
    return "update";
  }
  throw new Error(
    `${label}.verb: must be "create", "delete", "duplicate", or "update" (also accepts "add"/"remove" as aliases for create/delete, "copy"/"clone" as aliases for duplicate, "modify"/"edit" as aliases for update); got ${JSON.stringify(raw)}`
  );
}
function readProviderChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { verb, chain, name, providerType, spec }, got ${typeof raw}`
    );
  }
  const verb = normalizeProviderVerb(raw.verb, label);
  const chain = normalizeChain(raw.chain, label);
  const nameRaw = raw.name ?? raw.providerName;
  if (typeof nameRaw !== "string" || nameRaw.trim() === "") {
    throw new Error(
      `${label}.name: required (a non-blank provider name; \`providerName\` is an accepted alias)`
    );
  }
  const name = nameRaw.trim();
  if (verb === "delete") {
    if ("providerType" in raw && raw.providerType !== void 0 && raw.providerType !== null) {
      throw new Error(`${label}.providerType: not used for verb=delete; remove it or use verb=create`);
    }
    if ("spec" in raw && raw.spec !== void 0 && raw.spec !== null) {
      throw new Error(`${label}.spec: not used for verb=delete; remove it or use verb=create`);
    }
    return { verb, chain, name };
  }
  if (verb === "duplicate") {
    if ("providerType" in raw && raw.providerType !== void 0 && raw.providerType !== null) {
      throw new Error(
        `${label}.providerType: not used for verb=duplicate; a duplicate keeps the source provider's own type`
      );
    }
    if ("spec" in raw && raw.spec !== void 0 && raw.spec !== null) {
      throw new Error(
        `${label}.spec: not used for verb=duplicate; a duplicate keeps the source provider's own configuration \u2014 use newName to control only its name`
      );
    }
    const newNameRaw = raw.newName;
    if (newNameRaw === void 0 || newNameRaw === null) {
      return { verb, chain, name };
    }
    if (typeof newNameRaw !== "string" || newNameRaw.trim() === "") {
      throw new Error(`${label}.newName: must be a non-blank string if given`);
    }
    return { verb, chain, name, newName: newNameRaw.trim() };
  }
  if (verb === "update") {
    if ("providerType" in raw && raw.providerType !== void 0 && raw.providerType !== null) {
      throw new Error(
        `${label}.providerType: not used for verb=update; an update cannot change a provider's type \u2014 delete and create a new provider instead if the type itself needs to change`
      );
    }
    if ("newName" in raw && raw.newName !== void 0 && raw.newName !== null) {
      throw new Error(
        `${label}.newName: not used for verb=update; renaming is not supported through this verb`
      );
    }
    if (!isPlainObject2(raw.spec)) {
      throw new Error(
        `${label}.spec: required for verb=update (a partial-field object with at least one LDAP field to change; NOT a full provider passthrough \u2014 any field you omit is preserved unchanged from the current provider)`
      );
    }
    const updateSpec = raw.spec;
    const specKeys = Object.keys(updateSpec).filter(
      (k) => updateSpec[k] !== void 0 && updateSpec[k] !== null
    );
    if (specKeys.length === 0) {
      throw new Error(`${label}.spec: must not be empty \u2014 specify at least one field to change`);
    }
    validateLdapSpecFields(updateSpec, label);
    validateLdapPartialCredentialMode(updateSpec, label);
    return { verb, chain, name, spec: updateSpec };
  }
  const providerType = normalizeProviderType(raw.providerType, chain, label);
  if (providerType === "FILE") {
    const fileSpec = raw.spec;
    if (isPlainObject2(fileSpec) && Object.keys(fileSpec).some((k) => fileSpec[k] !== void 0 && fileSpec[k] !== null)) {
      throw new Error(
        `${label}.spec: not used for providerType="FILE" (a FILE provider has no configuration beyond its name)`
      );
    }
    return { verb, chain, name, providerType };
  }
  if (!isPlainObject2(raw.spec)) {
    throw new Error(
      `${label}.spec: required for providerType="LDAP" (an object with ldapServer/protocol/hostName/hostPort/rootDN and related fields)`
    );
  }
  validateLdapSpecFields(raw.spec, label);
  validateLdapCredentialMode(raw.spec, label);
  return { verb, chain, name, providerType, spec: raw.spec };
}
function normalizeProviderChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, chain, name, ... }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, chain, name, providerType?, spec?, newName? } (verb is "create", "delete", "duplicate", or "update"; chain is "authentication" or "authorization")'
    );
  }
  return entries.map(readProviderChange);
}
function requireProviderName(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.name ?? obj.providerName;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error("name: required (a non-blank provider name; `providerName` is an accepted alias)");
  }
  return raw.trim();
}
function requireProviderChain(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.chain;
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "authentication" || v === "authorization") {
    return v;
  }
  throw new Error(
    `chain: required, must be exactly "authentication" or "authorization" \u2014 no abbreviations are accepted here ("auth" is ambiguous between the two); got ${JSON.stringify(raw)}`
  );
}
function requireAuthenticationChainIfGiven(raw, toolName) {
  if (raw === void 0 || raw === null) {
    return;
  }
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "authentication") {
    return;
  }
  throw new Error(
    `chain: ${toolName} is authentication-chain only \u2014 there is no authorization-chain equivalent in the underlying product, refused rather than silently ignored or guessed; got ${JSON.stringify(raw)}`
  );
}
function normalizeDirectoryKind(raw) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "users" || v === "user") {
    return "users";
  }
  if (v === "groups" || v === "group") {
    return "groups";
  }
  if (v === "roles" || v === "role") {
    return "roles";
  }
  throw new Error(`kind: must be "users", "groups", or "roles"; got ${JSON.stringify(raw)}`);
}
function normalizeReviewOutcome(input) {
  if (input === void 0 || input === null) {
    return void 0;
  }
  const text = typeof input === "string" ? input : String(input);
  return text.trim() === "" ? void 0 : text.trim();
}
function normalizeDataSourceVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "update" || v === "modify" || v === "edit") {
    return "update";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "create" || v === "add") {
    return "create";
  }
  throw new Error(
    `${label}.verb: must be "update", "delete", or "create" (also accepts "modify"/"edit" as aliases for "update", "remove" as an alias for "delete", "add" as an alias for "create"); got ${JSON.stringify(raw)}`
  );
}
var MASKED_DATA_SOURCE_PASSWORD_LITERALS = /* @__PURE__ */ new Set(["******", "**********"]);
var DATA_SOURCE_SPEC_FIELDS = /* @__PURE__ */ new Set([
  "name",
  "url",
  "driver",
  "defaultDatabase",
  "tableName",
  "isolation",
  "ansiJoin",
  "requireLogin",
  "user",
  "password",
  "useCredentialId",
  "credentialID"
]);
function requireDataSourceIdOrName(raw, label) {
  const id = typeof raw.id === "string" && raw.id.trim() !== "" ? raw.id.trim() : void 0;
  const name = typeof raw.name === "string" && raw.name.trim() !== "" ? raw.name.trim() : void 0;
  if (!id && !name) {
    throw new Error(
      `${label}: at least one of id/name is required (name is preferred as the input a caller naturally has; id is accepted for round-tripping a prior response)`
    );
  }
  return { id, name };
}
function validateDataSourceSpecFields(spec, label) {
  for (const key of Object.keys(spec)) {
    if (spec[key] === void 0 || spec[key] === null) {
      continue;
    }
    if (!DATA_SOURCE_SPEC_FIELDS.has(key)) {
      throw new Error(
        `${label}.spec.${key}: not a recognized data source field \u2014 this tier can update name/url/driver/defaultDatabase/tableName/isolation/ansiJoin/requireLogin/user/password/useCredentialId/credentialID. For a tabular-type data source, only "name" is actually editable through this API tier at all; the server refuses any other field once it resolves this data source's type, rather than silently accepting and dropping it.`
      );
    }
  }
}
function validateDataSourcePasswordField(spec, label) {
  if (typeof spec.password !== "string") {
    return;
  }
  if (MASKED_DATA_SOURCE_PASSWORD_LITERALS.has(spec.password)) {
    throw new Error(
      `${label}.spec.password: looks like a masked placeholder ("${spec.password}"), not a real value \u2014 omit this field entirely to leave the password unchanged, or supply the real new password to rotate it. Sending a masked literal back would overwrite the live password with that literal string, breaking every connection through this data source.`
    );
  }
}
function validateDataSourceCredentialMode(spec, label) {
  const useCredentialId = spec.useCredentialId === true;
  const hasCredentialID = typeof spec.credentialID === "string" && spec.credentialID.trim() !== "";
  const hasPassword = typeof spec.password === "string" && spec.password.trim() !== "";
  const hasUser = typeof spec.user === "string" && spec.user.trim() !== "";
  if (useCredentialId) {
    if (!hasCredentialID) {
      throw new Error(
        `${label}.spec.credentialID: required when useCredentialId=true (the credential is resolved by reference, not supplied inline)`
      );
    }
    if (hasPassword || hasUser) {
      throw new Error(
        `${label}.spec.password/user: not used when useCredentialId=true \u2014 the credential comes from credentialID instead; remove password/user or set useCredentialId=false`
      );
    }
  } else if (hasCredentialID) {
    throw new Error(
      `${label}.spec.credentialID: not used when useCredentialId is not true \u2014 set useCredentialId=true to use a referenced credential, or remove credentialID`
    );
  }
}
function normalizeDataSourceTestConnectionSpec(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}.spec: must be an object (a partial-field spec), got ${typeof raw}`);
  }
  validateDataSourceSpecFields(raw, label);
  validateDataSourcePasswordField(raw, label);
  validateDataSourceCredentialMode(raw, label);
  return raw;
}
function readDataSourceFolderCreate(raw, label) {
  for (const field of ["id", "name", "spec", "force", "confirmRename"]) {
    if (field in raw && raw[field] !== void 0 && raw[field] !== null) {
      throw new Error(`${label}.${field}: not used for verb=create; refused rather than silently ignored`);
    }
  }
  if (typeof raw.folderPath !== "string" || raw.folderPath.trim() === "") {
    throw new Error(`${label}.folderPath: required for verb=create (the full folder path to create)`);
  }
  return { verb: "create", folderPath: raw.folderPath.trim() };
}
function readDataSourceChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { verb, id|name, spec|force } or { verb: "create", folderPath }, got ${typeof raw}`
    );
  }
  const verb = normalizeDataSourceVerb(raw.verb, label);
  if (verb === "create") {
    return readDataSourceFolderCreate(raw, label);
  }
  const { id, name } = requireDataSourceIdOrName(raw, label);
  if (verb === "delete") {
    if ("spec" in raw && raw.spec !== void 0 && raw.spec !== null) {
      throw new Error(`${label}.spec: not used for verb=delete; remove it or use verb=update`);
    }
    if ("confirmRename" in raw && raw.confirmRename !== void 0 && raw.confirmRename !== null) {
      throw new Error(
        `${label}.confirmRename: not used for verb=delete; remove it or use verb=update`
      );
    }
    let force;
    if ("force" in raw && raw.force !== void 0 && raw.force !== null) {
      if (typeof raw.force !== "boolean") {
        throw new Error(`${label}.force: must be a boolean; got ${typeof raw.force}`);
      }
      force = raw.force;
    }
    return { verb, id, name, force };
  }
  if ("force" in raw && raw.force !== void 0 && raw.force !== null) {
    throw new Error(`${label}.force: not used for verb=update; remove it or use verb=delete`);
  }
  if (!isPlainObject2(raw.spec)) {
    throw new Error(
      `${label}.spec: required for verb=update (a partial-field object; NOT a full DTO passthrough \u2014 include only the fields you want to change)`
    );
  }
  validateDataSourceSpecFields(raw.spec, label);
  validateDataSourcePasswordField(raw.spec, label);
  validateDataSourceCredentialMode(raw.spec, label);
  if ("confirmRename" in raw && raw.confirmRename !== void 0 && raw.confirmRename !== null && typeof raw.confirmRename !== "boolean") {
    throw new Error(`${label}.confirmRename: must be a boolean; got ${typeof raw.confirmRename}`);
  }
  const specName = raw.spec.name;
  const hasName = typeof specName === "string" && specName.trim() !== "";
  const confirmRename = raw.confirmRename === true;
  if (hasName && !confirmRename) {
    throw new Error(
      `${label}.confirmRename: required and must be true when the update spec includes "name" \u2014 renaming/moving a data source can auto-create missing parent folders as a side effect of what looks like an unrelated field edit. Set confirmRename:true to proceed, or remove spec.name if you did not intend to rename or move this data source.`
    );
  }
  return {
    verb,
    id,
    name,
    spec: raw.spec,
    confirmRename: hasName || confirmRename ? true : void 0
  };
}
function normalizeDataSourceChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, id|name, spec|force }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, id|name, spec|force } (verb is "update" or "delete")'
    );
  }
  return entries.map(readDataSourceChange);
}
function requireAcknowledgeIrreversibleDelete(changes, raw) {
  const hasDelete = changes.some((c) => c.verb === "delete");
  if (!hasDelete) {
    return void 0;
  }
  if (raw !== true) {
    throw new Error(
      "acknowledgeIrreversibleDelete: required and must be true \u2014 this plan contains a data source delete, which has NO rollback in this cut (declared non-compensable); the Tier-2 storage snapshot is the only recovery path, and it is manual and offline. Set acknowledgeIrreversibleDelete:true only after the human reviewing this plan has confirmed they understand the delete cannot be undone through admin-chat."
    );
  }
  return true;
}
function normalizeClusterVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "pause" || v === "stop") {
    return "pause";
  }
  if (v === "resume" || v === "unpause" || v === "start") {
    return "resume";
  }
  throw new Error(
    `${label}.verb: must be "pause" or "resume" (also accepts "stop" as an alias for "pause", "unpause"/"start" as aliases for "resume"); got ${JSON.stringify(raw)}`
  );
}
function requireClusterServer(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.server ?? obj.node ?? obj.serverName;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "server: required (a non-blank server name; `node`/`serverName` are accepted aliases)"
    );
  }
  return raw.trim();
}
function readClusterChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, server }, got ${typeof raw}`);
  }
  const verb = normalizeClusterVerb(raw.verb, label);
  const serverRaw = raw.server ?? raw.node ?? raw.serverName;
  if (typeof serverRaw !== "string" || serverRaw.trim() === "") {
    throw new Error(
      `${label}.server: required (a non-blank server name; \`node\`/\`serverName\` are accepted aliases)`
    );
  }
  return { verb, server: serverRaw.trim() };
}
function normalizeClusterChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, server }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, server } (verb is "pause" or "resume")'
    );
  }
  const normalized = entries.map(readClusterChange);
  const firstIndexByKey = /* @__PURE__ */ new Map();
  const verbIndexByServer = /* @__PURE__ */ new Map();
  normalized.forEach((change, i) => {
    const key = `${change.verb}:${change.server}`;
    const priorSameKey = firstIndexByKey.get(key);
    if (priorSameKey !== void 0) {
      throw new Error(
        `changes[${priorSameKey}] and changes[${i}] both target server "${change.server}" with verb "${change.verb}" \u2014 list each (verb, server) pair at most once`
      );
    }
    firstIndexByKey.set(key, i);
    const verbsForServer = verbIndexByServer.get(change.server) ?? /* @__PURE__ */ new Map();
    for (const [otherVerb, otherIndex] of verbsForServer) {
      if (otherVerb !== change.verb) {
        throw new Error(
          `changes[${otherIndex}] and changes[${i}] both target server "${change.server}" with opposite verbs \u2014 split into two plans applied in sequence, or drop one`
        );
      }
    }
    verbsForServer.set(change.verb, i);
    verbIndexByServer.set(change.server, verbsForServer);
  });
  return normalized;
}
function normalizeViewsheetUnitType(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "viewsheet" || v === "folder" || v === "worksheet") {
    return v;
  }
  throw new Error(
    `${label}.unitType: must be exactly "viewsheet", "folder", or "worksheet" \u2014 no abbreviations are accepted here; got ${JSON.stringify(raw)}`
  );
}
function normalizeViewsheetVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "rename" || v === "move") {
    return "rename";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "update") {
    return "update";
  }
  throw new Error(
    `${label}.verb: must be "rename", "delete", or "update" (also accepts "move" as an alias for "rename", "remove" as an alias for "delete"); got ${JSON.stringify(raw)}`
  );
}
function normalizeFolderVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "rename") {
    return "rename";
  }
  if (v === "update") {
    return "update";
  }
  throw new Error(
    `${label}.verb: for unitType="folder", must be "create", "delete", "rename", or "update" (also accepts "add" as an alias for "create", "remove" as an alias for "delete"); got ${JSON.stringify(raw)}`
  );
}
function requireViewsheetAssetId(raw, label, field = "assetId") {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      `${label}.${field}: required (the viewsheet's asset identifier, as returned by list_viewsheets, e.g. "1^128^__NULL__^Examples/Census^host-org")`
    );
  }
  const assetId = raw.trim();
  const segments = assetId.split("^");
  if (segments.length < 5 || !/^-?\d+$/.test(segments[0]) || !/^-?\d+$/.test(segments[1])) {
    throw new Error(
      `${label}.${field}: "${assetId}" is not a valid asset identifier \u2014 expected the scope^type^user^path^orgId form returned by list_viewsheets (e.g. "1^128^__NULL__^Examples/Census^host-org"), not a hand-typed or guessed value. A malformed identifier reaching the server can throw an unstructured error instead of a clean not-found response, so this is checked before any call is made.`
    );
  }
  return assetId;
}
function requireViewsheetFolderPath(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.path;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(`path: required (the folder's registry path, e.g. "Examples/Old Folder")`);
  }
  return raw.trim();
}
function readViewsheetOwner(raw) {
  return typeof raw.owner === "string" && raw.owner.trim() !== "" ? raw.owner.trim() : void 0;
}
function readMetadataFields(raw, label, requireAtLeastOne) {
  const hasAlias = "alias" in raw && raw.alias !== void 0;
  const hasDescription = "description" in raw && raw.description !== void 0;
  if (requireAtLeastOne && !hasAlias && !hasDescription) {
    throw new Error(`${label}: at least one of alias/description is required for verb="update"`);
  }
  if (hasAlias && typeof raw.alias !== "string") {
    throw new Error(
      `${label}.alias: must be a string (an empty string clears it, omit it to leave unchanged); got ${typeof raw.alias}`
    );
  }
  if (hasDescription && typeof raw.description !== "string") {
    throw new Error(
      `${label}.description: must be a string (an empty string clears it, omit it to leave unchanged); got ${typeof raw.description}`
    );
  }
  const result = {};
  if (hasAlias) {
    result.alias = raw.alias;
  }
  if (hasDescription) {
    result.description = raw.description;
  }
  return result;
}
function readSheetUnitChange(raw, label, unitType) {
  const verb = normalizeViewsheetVerb(raw.verb, label);
  const assetId = requireViewsheetAssetId(raw.assetId, label);
  const unitLabel = unitType;
  if (verb === "delete") {
    for (const f of ["newPath", "global", "owner", "alias", "description"]) {
      if (raw[f] !== void 0 && raw[f] !== null) {
        throw new Error(
          `${label}.${f}: not used for verb="delete" on a ${unitLabel}; remove it or use verb="rename"/"update"`
        );
      }
    }
    let force;
    if ("force" in raw && raw.force !== void 0 && raw.force !== null) {
      if (typeof raw.force !== "boolean") {
        throw new Error(`${label}.force: must be a boolean; got ${typeof raw.force}`);
      }
      force = raw.force;
    }
    return { unitType, verb, assetId, force };
  }
  if (verb === "update") {
    for (const f of ["newPath", "global", "owner", "force"]) {
      if (raw[f] !== void 0 && raw[f] !== null) {
        throw new Error(
          `${label}.${f}: not used for verb="update" on a ${unitLabel}; remove it or use verb="rename"/"delete"`
        );
      }
    }
    const metadata = readMetadataFields(raw, label, true);
    return { unitType, verb, assetId, ...metadata };
  }
  for (const f of ["alias", "description"]) {
    if (raw[f] !== void 0 && raw[f] !== null) {
      throw new Error(
        `${label}.${f}: not used for verb="rename" on a ${unitLabel}; remove it or use verb="update"`
      );
    }
  }
  if ("force" in raw && raw.force !== void 0 && raw.force !== null) {
    throw new Error(
      `${label}.force: not used for verb="rename" on a ${unitLabel}; remove it or use verb="delete"`
    );
  }
  if (typeof raw.newPath !== "string" || raw.newPath.trim() === "") {
    throw new Error(`${label}.newPath: required for verb="rename" on a ${unitLabel}`);
  }
  if (typeof raw.global !== "boolean") {
    throw new Error(`${label}.global: required for verb="rename" on a ${unitLabel}, must be a boolean`);
  }
  const owner = readViewsheetOwner(raw);
  if (raw.global) {
    if (owner !== void 0) {
      throw new Error(`${label}.owner: not used when global=true; remove it or set global=false`);
    }
  } else if (owner === void 0) {
    throw new Error(`${label}.owner: required when global=false`);
  }
  return {
    unitType,
    verb,
    assetId,
    newPath: raw.newPath.trim(),
    global: raw.global,
    owner
  };
}
function resolveFolderPathAlias(raw, label) {
  const path2 = typeof raw.path === "string" && raw.path.trim() !== "" ? raw.path.trim() : void 0;
  const oldPath = typeof raw.oldPath === "string" && raw.oldPath.trim() !== "" ? raw.oldPath.trim() : void 0;
  if (path2 !== void 0 && oldPath !== void 0 && path2 !== oldPath) {
    throw new Error(
      `${label}: path ("${path2}") and oldPath ("${oldPath}") were both given and disagree -- an ambiguous combined input is refused rather than silently resolved one way`
    );
  }
  return path2 ?? oldPath;
}
function readFolderChange(raw, label) {
  const verb = normalizeFolderVerb(raw.verb, label);
  const owner = readViewsheetOwner(raw);
  if (verb === "create") {
    for (const f of ["path", "oldPath", "newPath", "force"]) {
      if (raw[f] !== void 0 && raw[f] !== null) {
        throw new Error(
          `${label}.${f}: not used for verb="create" on a folder; remove it or use verb="delete"/"rename"/"update"`
        );
      }
    }
    if (typeof raw.folderName !== "string" || raw.folderName.trim() === "") {
      throw new Error(`${label}.folderName: required for verb="create" on a folder`);
    }
    const parentFolder = typeof raw.parentFolder === "string" && raw.parentFolder.trim() !== "" ? raw.parentFolder.trim() : void 0;
    const metadata = readMetadataFields(raw, label, false);
    return {
      unitType: "folder",
      verb,
      parentFolder,
      folderName: raw.folderName.trim(),
      owner,
      ...metadata
    };
  }
  if (verb === "delete") {
    for (const f of ["parentFolder", "folderName", "newPath", "alias", "description"]) {
      if (raw[f] !== void 0 && raw[f] !== null) {
        throw new Error(
          `${label}.${f}: not used for verb="delete" on a folder; remove it or use verb="create"/"rename"/"update"`
        );
      }
    }
    const pathRaw = resolveFolderPathAlias(raw, label);
    if (pathRaw === void 0) {
      throw new Error(
        `${label}.path: required for verb="delete" on a folder (\`oldPath\` is an accepted alias)`
      );
    }
    let force;
    if ("force" in raw && raw.force !== void 0 && raw.force !== null) {
      if (typeof raw.force !== "boolean") {
        throw new Error(`${label}.force: must be a boolean; got ${typeof raw.force}`);
      }
      force = raw.force;
    }
    return { unitType: "folder", verb, path: pathRaw, owner, force };
  }
  if (verb === "update") {
    for (const f of ["parentFolder", "folderName", "newPath", "force"]) {
      if (raw[f] !== void 0 && raw[f] !== null) {
        throw new Error(
          `${label}.${f}: not used for verb="update" on a folder; remove it or use verb="create"/"rename"/"delete"`
        );
      }
    }
    const pathRaw = resolveFolderPathAlias(raw, label);
    if (pathRaw === void 0) {
      throw new Error(
        `${label}.path: required for verb="update" on a folder (\`oldPath\` is an accepted alias)`
      );
    }
    const metadata = readMetadataFields(raw, label, true);
    return { unitType: "folder", verb, path: pathRaw, owner, ...metadata };
  }
  for (const f of ["parentFolder", "folderName", "force", "alias", "description"]) {
    if (raw[f] !== void 0 && raw[f] !== null) {
      throw new Error(
        `${label}.${f}: not used for verb="rename" on a folder; remove it or use verb="create"/"update"`
      );
    }
  }
  const oldPathRaw = resolveFolderPathAlias(raw, label);
  if (oldPathRaw === void 0) {
    throw new Error(
      `${label}.oldPath: required for verb="rename" on a folder (\`path\` is an accepted alias)`
    );
  }
  if (typeof raw.newPath !== "string" || raw.newPath.trim() === "") {
    throw new Error(`${label}.newPath: required for verb="rename" on a folder`);
  }
  return { unitType: "folder", verb, path: oldPathRaw, newPath: raw.newPath.trim(), owner };
}
function readViewsheetPlanChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { unitType, verb, ... }, got ${typeof raw}`);
  }
  const unitType = normalizeViewsheetUnitType(raw.unitType, label);
  if (unitType === "folder") {
    return readFolderChange(raw, label);
  }
  return readSheetUnitChange(raw, label, unitType);
}
function normalizeViewsheetChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { unitType, verb, ... }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { unitType, verb, ... } (unitType is "viewsheet", "folder", or "worksheet")'
    );
  }
  return entries.map(readViewsheetPlanChange);
}
function requireAcknowledgeIrreversibleViewsheetDelete(changes, raw, planChanges) {
  const hasIrreversibleViewsheetDelete = changes.some((c) => (c.unitType === "viewsheet" || c.unitType === "worksheet") && c.verb === "delete");
  const hasHighRiskFolderDelete = changes.some((c, i) => {
    if (c.unitType !== "folder" || c.verb !== "delete") {
      return false;
    }
    const planChange = planChanges?.[i];
    return planChange?.risk === "high";
  });
  if (!hasIrreversibleViewsheetDelete && !hasHighRiskFolderDelete) {
    return void 0;
  }
  if (raw !== true) {
    const causes = [];
    if (hasIrreversibleViewsheetDelete) {
      causes.push(
        "a viewsheet or worksheet delete, which has NO rollback in this cut (declared non-compensable); the Tier-2 storage snapshot is the only recovery path, and it is manual and offline"
      );
    }
    if (hasHighRiskFolderDelete) {
      causes.push(
        "a delete of a non-empty folder, which permanently and irrecoverably deletes every contained viewsheet/worksheet, recursively, with no recycle bin and no rollback (StyleBI itself refuses this without force:true on that entry too, per the Redmine #76469 fix)"
      );
    }
    throw new Error(
      "acknowledgeIrreversibleDelete: required and must be true \u2014 this plan contains " + causes.join(" and ") + ". Set acknowledgeIrreversibleDelete:true only after the human reviewing this plan has confirmed they understand this cannot be undone through admin-chat."
    );
  }
  return true;
}
var DASHBOARD_GLOBAL_SUFFIX = "__GLOBAL";
function stripDashboardGlobalSuffix(name) {
  return name.endsWith(DASHBOARD_GLOBAL_SUFFIX) ? name.slice(0, -DASHBOARD_GLOBAL_SUFFIX.length) : name;
}
function appendDashboardGlobalSuffix(name) {
  return name.endsWith(DASHBOARD_GLOBAL_SUFFIX) ? name : name + DASHBOARD_GLOBAL_SUFFIX;
}
function stripDashboardGlobalSuffixFromSettings(raw) {
  if (!isPlainObject2(raw)) {
    return raw;
  }
  const out = { ...raw };
  if (typeof out.name === "string") {
    out.name = stripDashboardGlobalSuffix(out.name);
  }
  if (typeof out.oname === "string") {
    out.oname = stripDashboardGlobalSuffix(out.oname);
  }
  return out;
}
function stripDashboardGlobalSuffixFromList(raw) {
  return Array.isArray(raw) ? raw.map(stripDashboardGlobalSuffixFromSettings) : raw;
}
function stripDashboardGlobalSuffixFromFolder(raw) {
  if (!isPlainObject2(raw) || !Array.isArray(raw.dashboards)) {
    return raw;
  }
  return {
    ...raw,
    dashboards: raw.dashboards.map((d) => typeof d === "string" ? stripDashboardGlobalSuffix(d) : d)
  };
}
function requireDashboardName(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.name;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "name: required (the dashboard's current display name, as returned by list_portal_dashboards)"
    );
  }
  return raw.trim();
}
function readDashboardOwner(raw) {
  return typeof raw.owner === "string" && raw.owner.trim() !== "" ? raw.owner.trim() : void 0;
}
function normalizeDashboardUnitType(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "dashboard") {
    return "dashboard";
  }
  if (v === "dashboardfolder") {
    return "dashboardFolder";
  }
  throw new Error(
    `${label}.unitType: must be exactly "dashboard" or "dashboardFolder"; got ${JSON.stringify(raw)}`
  );
}
function normalizeDashboardVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "update") {
    return "update";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  throw new Error(
    `${label}.verb: for unitType="dashboard", must be "create", "update", or "delete" (also accepts "add" as an alias for "create", "remove" as an alias for "delete"); got ${JSON.stringify(raw)}`
  );
}
function normalizeDashboardFolderVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "reorder" || v === "arrange") {
    return "reorder";
  }
  throw new Error(
    `${label}.verb: for unitType="dashboardFolder", must be "reorder" (also accepts "arrange" as an alias); got ${JSON.stringify(raw)}`
  );
}
function forbidDashboardFields(raw, label, forbidden, context) {
  for (const field of forbidden) {
    if (raw[field] !== void 0 && raw[field] !== null) {
      throw new Error(`${label}.${field}: not used for ${context}; remove it`);
    }
  }
}
function readDashboardCreateChange(raw, label, owner) {
  forbidDashboardFields(raw, label, ["oname", "dashboards"], 'verb="create" on a dashboard');
  const name = typeof raw.name === "string" ? raw.name.trim() : "";
  if (name === "") {
    throw new Error(`${label}.name: required for verb="create" on a dashboard`);
  }
  const viewsheet = requireViewsheetAssetId(raw.viewsheet, label, "viewsheet");
  let description;
  if ("description" in raw && raw.description !== void 0) {
    if (typeof raw.description !== "string") {
      throw new Error(`${label}.description: must be a string; got ${typeof raw.description}`);
    }
    description = raw.description;
  }
  let enable;
  if ("enable" in raw && raw.enable !== void 0) {
    if (typeof raw.enable !== "boolean") {
      throw new Error(`${label}.enable: must be a boolean; got ${typeof raw.enable}`);
    }
    enable = raw.enable;
  }
  return { unitType: "dashboard", verb: "create", owner, name, description, viewsheet, enable };
}
function readDashboardUpdateFields(raw, label) {
  const hasName = "name" in raw && raw.name !== void 0;
  const hasDescription = "description" in raw && raw.description !== void 0;
  const hasViewsheet = "viewsheet" in raw && raw.viewsheet !== void 0;
  const hasEnable = "enable" in raw && raw.enable !== void 0;
  if (!hasName && !hasDescription && !hasViewsheet && !hasEnable) {
    throw new Error(
      `${label}: at least one of name/description/viewsheet/enable is required for verb="update"`
    );
  }
  const result = {};
  if (hasName) {
    if (typeof raw.name !== "string" || raw.name.trim() === "") {
      throw new Error(`${label}.name: must be a non-blank string when present`);
    }
    result.name = raw.name.trim();
  }
  if (hasDescription) {
    if (typeof raw.description !== "string") {
      throw new Error(
        `${label}.description: must be a string (an empty string clears it, omit it to leave unchanged); got ${typeof raw.description}`
      );
    }
    result.description = raw.description;
  }
  if (hasViewsheet) {
    result.viewsheet = requireViewsheetAssetId(raw.viewsheet, label, "viewsheet");
  }
  if (hasEnable) {
    if (typeof raw.enable !== "boolean") {
      throw new Error(`${label}.enable: must be a boolean; got ${typeof raw.enable}`);
    }
    result.enable = raw.enable;
  }
  return result;
}
function readDashboardUpdateChange(raw, label, owner) {
  forbidDashboardFields(raw, label, ["dashboards"], 'verb="update" on a dashboard');
  const onameRaw = typeof raw.oname === "string" ? raw.oname.trim() : "";
  if (onameRaw === "") {
    throw new Error(
      `${label}.oname: required for verb="update" on a dashboard (the EXISTING dashboard's current display name, used to locate it)`
    );
  }
  const fields = readDashboardUpdateFields(raw, label);
  return { unitType: "dashboard", verb: "update", owner, oname: onameRaw, ...fields };
}
function readDashboardDeleteChange(raw, label, owner) {
  forbidDashboardFields(
    raw,
    label,
    ["name", "description", "viewsheet", "enable", "dashboards"],
    'verb="delete" on a dashboard'
  );
  const onameRaw = typeof raw.oname === "string" ? raw.oname.trim() : "";
  if (onameRaw === "") {
    throw new Error(
      `${label}.oname: required for verb="delete" on a dashboard (the EXISTING dashboard's current display name, used to locate it)`
    );
  }
  return { unitType: "dashboard", verb: "delete", owner, oname: onameRaw };
}
function readDashboardFolderChange(raw, label, owner) {
  normalizeDashboardFolderVerb(raw.verb, label);
  forbidDashboardFields(
    raw,
    label,
    ["name", "oname", "description", "viewsheet", "enable"],
    'unitType="dashboardFolder"'
  );
  const requested = raw.dashboards;
  if (!Array.isArray(requested) || requested.length === 0 || !requested.every((d) => typeof d === "string" && d.trim() !== "")) {
    throw new Error(
      `${label}.dashboards: required for unitType="dashboardFolder" \u2014 a non-empty array of dashboard names (the full replacement ordered list for this owner scope)`
    );
  }
  const dashboards = requested.map((d) => appendDashboardGlobalSuffix(d.trim()));
  return { unitType: "dashboardFolder", verb: "reorder", owner, dashboards };
}
function readDashboardPlanChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { unitType, verb, ... }, got ${typeof raw}`);
  }
  const unitType = normalizeDashboardUnitType(raw.unitType, label);
  const owner = readDashboardOwner(raw);
  if (unitType === "dashboardFolder") {
    return readDashboardFolderChange(raw, label, owner);
  }
  const verb = normalizeDashboardVerb(raw.verb, label);
  if (verb === "create") {
    return readDashboardCreateChange(raw, label, owner);
  }
  if (verb === "update") {
    return readDashboardUpdateChange(raw, label, owner);
  }
  return readDashboardDeleteChange(raw, label, owner);
}
function normalizeDashboardChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { unitType, verb, ... }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { unitType, verb, ... } (unitType is "dashboard" or "dashboardFolder")'
    );
  }
  return entries.map(readDashboardPlanChange);
}
function normalizeLicenseVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "add" || v === "install") {
    return "add";
  }
  if (v === "remove" || v === "uninstall" || v === "delete") {
    return "remove";
  }
  if (v === "update" || v === "replace" || v === "set") {
    throw new Error(
      `${label}.verb: no "update"/"replace"/"set" verb exists for license keys \u2014 a key is not edited in place, only "add"ed or "remove"d. To change which key is installed, submit two separate entries: remove the old key and add the new one.`
    );
  }
  throw new Error(
    `${label}.verb: must be "add" or "remove" (also accepts "install" as an alias for "add", "uninstall"/"delete" as aliases for "remove"); got ${JSON.stringify(raw)}`
  );
}
function requireLicenseKey(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.key ?? obj.id ?? obj.name ?? obj.licenseKey;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "key: required (a non-blank license key string; `id`/`name`/`licenseKey` are accepted aliases)"
    );
  }
  return raw.trim();
}
var LICENSE_CHANGE_FIELDS = /* @__PURE__ */ new Set(["verb", "key", "id", "name", "licenseKey"]);
function readLicenseChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, key }, got ${typeof raw}`);
  }
  for (const field of Object.keys(raw)) {
    if (!LICENSE_CHANGE_FIELDS.has(field)) {
      throw new Error(
        `${label}.${field}: unrecognized field \u2014 a license change entry accepts only "verb" and the key itself ("key", or its aliases "id"/"name"/"licenseKey"); no other field is meaningful for this resource.`
      );
    }
  }
  const verb = normalizeLicenseVerb(raw.verb, label);
  const keyRaw = raw.key ?? raw.id ?? raw.name ?? raw.licenseKey;
  if (typeof keyRaw !== "string" || keyRaw.trim() === "") {
    throw new Error(
      `${label}.key: required (a non-blank license key string; \`id\`/\`name\`/\`licenseKey\` are accepted aliases)`
    );
  }
  return { verb, key: keyRaw.trim() };
}
function normalizeLicenseChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, key }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, key } (verb is "add" or "remove")'
    );
  }
  const normalized = entries.map(readLicenseChange);
  const firstIndexByKey = /* @__PURE__ */ new Map();
  const verbIndexByKeyString = /* @__PURE__ */ new Map();
  normalized.forEach((change, i) => {
    const dupeKey = `${change.verb}:${change.key}`;
    const priorSameKey = firstIndexByKey.get(dupeKey);
    if (priorSameKey !== void 0) {
      throw new Error(
        `changes[${priorSameKey}] and changes[${i}] both target key "${change.key}" with verb "${change.verb}" \u2014 list each (verb, key) pair at most once`
      );
    }
    firstIndexByKey.set(dupeKey, i);
    const verbsForKey = verbIndexByKeyString.get(change.key) ?? /* @__PURE__ */ new Map();
    for (const [otherVerb, otherIndex] of verbsForKey) {
      if (otherVerb !== change.verb) {
        throw new Error(
          `changes[${otherIndex}] and changes[${i}] both target key "${change.key}" with opposite verbs \u2014 split into two plans applied in sequence, or drop one`
        );
      }
    }
    verbsForKey.set(change.verb, i);
    verbIndexByKeyString.set(change.key, verbsForKey);
  });
  return normalized;
}
function normalizeAcknowledgeDelicensing(raw) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (raw !== true) {
    throw new Error(
      "acknowledgeDelicensing: if provided, must be true \u2014 this flag is required only when the freshly re-resolved plan at apply time would leave zero license keys installed (a full de-licensing event); omit it entirely otherwise. If apply_license_changes refuses your call naming this field, confirm with the human that full de-licensing is intended, then resend with acknowledgeDelicensing:true."
    );
  }
  return true;
}
var PRESENTATION_SUB_MODELS = [
  "formats",
  "dashboard",
  "viewsheetToolbar",
  "lookAndFeel",
  "welcomePage",
  "loginBanner",
  "portalIntegration",
  "pdfGeneration",
  "exportMenu",
  "fontMapping",
  "share",
  "composerMessage",
  "time",
  "dataSourceVisibility",
  "webMap",
  "ai"
];
var PRESENTATION_SUB_MODEL_SET = new Set(PRESENTATION_SUB_MODELS);
var STORAGE_SCOPE_PRESENTATION_SUB_MODELS = /* @__PURE__ */ new Set([
  "lookAndFeel",
  "welcomePage",
  "loginBanner",
  "portalIntegration"
]);
var GLOBAL_ONLY_PRESENTATION_SUB_MODELS = /* @__PURE__ */ new Set(["fontMapping", "ai"]);
var DEAD_PRESENTATION_SUB_MODEL_FIELDS = /* @__PURE__ */ new Set([
  "reportToolbarOptionsModel",
  "reportViewerSettingsModel"
]);
function normalizeScope(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "global") {
    return "global";
  }
  if (v === "organization" || v === "org") {
    return "organization";
  }
  throw new Error(
    `${label}.scope: required, must be "global" or "organization" (also accepts "org" as an alias for "organization"); got ${JSON.stringify(raw)}. There is no default. "organization" always means the CALLING PRINCIPAL'S OWN organization \u2014 there is no way to target a different org's presentation settings through this tool at all.`
  );
}
function requireSubModel(raw, label) {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      `${label}.subModel: required, must be exactly one of the 16 catalogued sub-models (case-sensitive, no abbreviations): ${PRESENTATION_SUB_MODELS.join(", ")}`
    );
  }
  const v = raw.trim();
  if (DEAD_PRESENTATION_SUB_MODEL_FIELDS.has(v)) {
    throw new Error(
      `${label}.subModel: "${v}" is declared on the underlying model but never read or written by the server (a dead field) \u2014 it is not a real, administrable sub-model. Use one of: ${PRESENTATION_SUB_MODELS.join(", ")}`
    );
  }
  if (!PRESENTATION_SUB_MODEL_SET.has(v)) {
    throw new Error(
      `${label}.subModel: "${v}" is not one of the 16 catalogued sub-models (exact, case-sensitive match required): ${PRESENTATION_SUB_MODELS.join(", ")}`
    );
  }
  return v;
}
function normalizePresentationVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "update" || v === "modify" || v === "edit") {
    return "update";
  }
  if (v === "create" || v === "add" || v === "delete" || v === "remove") {
    throw new Error(
      `${label}.verb: "${v}" is not supported \u2014 presentation sub-models are never created or destroyed, nothing is created and nothing is destroyed here. Use "update" (also accepts "modify"/"edit" as aliases) to change one.`
    );
  }
  throw new Error(
    `${label}.verb: must be "update" (also accepts "modify"/"edit" as aliases); got ${JSON.stringify(raw)}`
  );
}
function validateGlobalOnlyScope(subModel, scope, label) {
  if (GLOBAL_ONLY_PRESENTATION_SUB_MODELS.has(subModel) && scope === "organization") {
    throw new Error(
      `${label}.scope: "${subModel}" has no organization-scoped layer \u2014 it is GLOBAL-ONLY. The underlying server silently drops an organization-scoped write to this sub-model today (and, for "ai", nulls out the value entirely on an organization-scoped read) rather than erroring, so this tool refuses the request here instead of reproducing that silent behavior. Set scope="global" instead.`
    );
  }
}
var FILENAME_TRAVERSAL_PATTERN = /[\\/]|\.\./;
function requireBackupTask(input) {
  const task = requireTask(input);
  if (FILENAME_TRAVERSAL_PATTERN.test(task)) {
    throw new Error(
      `task: "${task}" is not a safe value \u2014 it contains a path separator ("/" or "\\\\") or a ".." segment. The server writes this value verbatim as part of the backup's stored dataspace/path segment, so this tool refuses it here instead of forwarding it. Pass a plain description with no directory components, e.g. "pre-upgrade snapshot".`
    );
  }
  return task;
}
function validateViewsheetFileName(scope, fileData, label) {
  if (scope !== "organization" || !isPlainObject2(fileData) || typeof fileData.name !== "string") {
    return;
  }
  if (FILENAME_TRAVERSAL_PATTERN.test(fileData.name)) {
    throw new Error(
      `${label}.spec.viewsheetFile.name: "${fileData.name}" is not a safe filename \u2014 it contains a path separator ("/" or "\\\\") or a ".." segment. On an organization-scoped lookAndFeel update, the server writes this value verbatim as the stored CSS filename with no path sanitization (a confirmed, independently filed gap in LookAndFeelService/DataSpace) \u2014 this tool refuses it here instead of forwarding it. Pass a plain basename with no directory components, e.g. "format.css".`
    );
  }
}
function validateLogoOrFaviconFileName(fileData, field, label) {
  if (!isPlainObject2(fileData) || typeof fileData.name !== "string") {
    return;
  }
  const dotIndex = fileData.name.lastIndexOf(".");
  if (dotIndex < 0) {
    return;
  }
  const suffix = fileData.name.substring(dotIndex);
  if (FILENAME_TRAVERSAL_PATTERN.test(suffix)) {
    throw new Error(
      `${label}.spec.${field}.name: the portion after the last "." ("${suffix}") contains a path separator or a ".." segment. The server derives this sub-model's stored filename from exactly that suffix (e.g. "${field === "logoFile" ? "logo" : "favicon"}" + suffix), with no validation of its contents, on both the global and organization-scoped write paths \u2014 this tool refuses it here instead of forwarding it. Use a plain file extension only, e.g. "${fileData.name.substring(0, dotIndex)}.png".`
    );
  }
}
function validateLookAndFeelSpec(scope, spec, label) {
  validateViewsheetFileName(scope, spec.viewsheetFile, label);
  validateLogoOrFaviconFileName(spec.logoFile, "logoFile", label);
  validateLogoOrFaviconFileName(spec.faviconFile, "faviconFile", label);
}
function readPresentationChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { verb, subModel, scope, spec }, got ${typeof raw}`
    );
  }
  const verb = normalizePresentationVerb(raw.verb, label);
  const subModel = requireSubModel(raw.subModel, label);
  const scope = normalizeScope(raw.scope, label);
  validateGlobalOnlyScope(subModel, scope, label);
  if (!isPlainObject2(raw.spec)) {
    throw new Error(
      `${label}.spec: required for verb="update" (a partial-field object for subModel="${subModel}" only \u2014 never a full-document passthrough)`
    );
  }
  if (subModel === "lookAndFeel") {
    validateLookAndFeelSpec(scope, raw.spec, label);
  }
  return { verb, subModel, scope, spec: raw.spec };
}
function normalizePresentationChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, subModel, scope, spec }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, subModel, scope, spec } (verb is "update" only)'
    );
  }
  return entries.map(readPresentationChange);
}
function requireAcknowledgeIrreversibleUpdate(changes, raw) {
  const hasStorageScope = changes.some((c) => STORAGE_SCOPE_PRESENTATION_SUB_MODELS.has(c.subModel));
  if (!hasStorageScope) {
    return void 0;
  }
  if (raw !== true) {
    throw new Error(
      "acknowledgeIrreversibleUpdate: required and must be true \u2014 this plan updates at least one of lookAndFeel/welcomePage/loginBanner/portalIntegration, which have NO live rollback in this cut (declared non-compensable \u2014 a whole-file DataSpace rewrite with no before-image captured); the Tier-2 storage snapshot is the only recovery path, and it is manual and offline. Set acknowledgeIrreversibleUpdate:true only after the human reviewing this plan has confirmed they understand that entry cannot be undone through admin-chat."
    );
  }
  return true;
}
function normalizeRecycleBinVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "restore") {
    return "restore";
  }
  if (v === "purge" || v === "remove" || v === "delete") {
    return "purge";
  }
  throw new Error(
    `${label}.verb: must be "restore" or "purge" ("remove"/"delete" accepted as aliases for "purge"); got ${JSON.stringify(raw)}`
  );
}
function requireRecycleBinPath(raw, label) {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      `${label}.path: required \u2014 the recycle-bin storage key exactly as returned by list_recycle_bin_entries/get_recycle_bin_entry, never hand-constructed`
    );
  }
  return raw.trim();
}
function readRecycleBinChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, path, overwrite }, got ${typeof raw}`);
  }
  const verb = normalizeRecycleBinVerb(raw.verb, label);
  const path2 = requireRecycleBinPath(raw.path, label);
  const hasOverwrite = "overwrite" in raw && raw.overwrite !== void 0 && raw.overwrite !== null;
  if (verb === "purge") {
    if (hasOverwrite) {
      throw new Error(`${label}.overwrite: not used for verb="purge"; remove it or use verb="restore"`);
    }
    return { verb, path: path2 };
  }
  if (!hasOverwrite) {
    return { verb, path: path2 };
  }
  if (typeof raw.overwrite !== "boolean") {
    throw new Error(`${label}.overwrite: must be boolean when present; got ${typeof raw.overwrite}`);
  }
  return { verb, path: path2, overwrite: raw.overwrite };
}
function normalizeRecycleBinChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, path }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, path, overwrite } (verb is "restore" or "purge")'
    );
  }
  return entries.map(readRecycleBinChange);
}
function requireAcknowledgeIrreversibleRecycleBinDelete(changes, raw, planChanges) {
  const hasPurge = changes.some((c) => c.verb === "purge");
  const hasHighRiskRestore = changes.some((c, i) => {
    if (c.verb !== "restore") {
      return false;
    }
    const planChange = planChanges?.[i];
    return planChange?.risk === "high";
  });
  if (!hasPurge && !hasHighRiskRestore) {
    return void 0;
  }
  if (raw !== true) {
    const causes = [];
    if (hasPurge) {
      causes.push(
        "a purge entry, which permanently deletes the recycled item with no live inverse \u2014 the recycle bin is the product's own last line of defense, and this removes it"
      );
    }
    if (hasHighRiskRestore) {
      causes.push(
        "a restore entry whose destination is already occupied, accepted via overwrite:true \u2014 restoring PERMANENTLY DESTROYS the existing asset currently at that path, bypassing the recycle bin, before the restore itself proceeds"
      );
    }
    throw new Error(
      "acknowledgeIrreversibleDelete: required and must be true because this plan contains " + causes.join(" and ") + ". Set it only after the human reviewing this plan has confirmed they understand this cannot be undone through admin-chat."
    );
  }
  return true;
}
function normalizeMvVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "set_cycle" || v === "setcycle" || v === "set-cycle" || v === "cycle") {
    return "set_cycle";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  throw new Error(
    `${label}.verb: must be "create", "set_cycle", or "delete" (also accepts "add" as an alias for "create", "remove" as an alias for "delete"); got ${JSON.stringify(raw)}`
  );
}
function requireMvNames(raw, label) {
  const value = raw.mvNames;
  if (Array.isArray(value)) {
    if (value.length === 0) {
      throw new Error(`${label}.mvNames: must not be empty \u2014 list at least one mv name`);
    }
    return value.map((name, i) => {
      if (typeof name !== "string" || name.trim() === "") {
        throw new Error(`${label}.mvNames[${i}]: must be a non-empty string, got ${JSON.stringify(name)}`);
      }
      return name.trim();
    });
  }
  if (typeof value === "string" && value.trim() !== "") {
    return [value.trim()];
  }
  throw new Error(`${label}.mvNames: required \u2014 an array of mv names (a single string is accepted too)`);
}
function requireMvAnalysisIdField(raw, label) {
  const value = raw.analysisId;
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`${label}.analysisId: required for verb="${raw.verb}"`);
  }
  return value.trim();
}
function readMvChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, mvNames, ... }, got ${typeof raw}`);
  }
  const verb = normalizeMvVerb(raw.verb, label);
  const mvNames = requireMvNames(raw, label);
  if (verb === "delete") {
    for (const f of ["analysisId", "cycle", "noData", "runInBackground"]) {
      if (raw[f] !== void 0 && raw[f] !== null) {
        throw new Error(
          `${label}.${f}: not used for verb="delete"; remove it or use verb="create"/"set_cycle"`
        );
      }
    }
    return { verb, mvNames };
  }
  const analysisId = requireMvAnalysisIdField(raw, label);
  let cycle;
  if (raw.cycle !== void 0 && raw.cycle !== null) {
    if (typeof raw.cycle !== "string") {
      throw new Error(`${label}.cycle: must be a string, got ${typeof raw.cycle}`);
    }
    cycle = raw.cycle;
  }
  if (verb === "set_cycle") {
    if (raw.noData !== void 0 && raw.noData !== null) {
      throw new Error(`${label}.noData: not used for verb="set_cycle"; remove it or use verb="create"`);
    }
    if (raw.runInBackground !== void 0 && raw.runInBackground !== null) {
      throw new Error(
        `${label}.runInBackground: not used for verb="set_cycle"; remove it or use verb="create"`
      );
    }
    return { verb, mvNames, analysisId, cycle };
  }
  let noData;
  let runInBackground;
  if (raw.noData !== void 0 && raw.noData !== null) {
    if (typeof raw.noData !== "boolean") {
      throw new Error(`${label}.noData: must be a boolean, got ${typeof raw.noData}`);
    }
    noData = raw.noData;
  }
  if (raw.runInBackground !== void 0 && raw.runInBackground !== null) {
    if (typeof raw.runInBackground !== "boolean") {
      throw new Error(`${label}.runInBackground: must be a boolean, got ${typeof raw.runInBackground}`);
    }
    runInBackground = raw.runInBackground;
  }
  return { verb, mvNames, analysisId, cycle, noData, runInBackground };
}
function normalizeMvChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, mvNames, ... }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, mvNames, ... } (verb is "create", "set_cycle", or "delete")'
    );
  }
  return entries.map(readMvChange);
}
function requireAcknowledgeIrreversibleMvDelete(changes, raw) {
  const hasDelete = changes.some((c) => c.verb === "delete");
  if (!hasDelete) {
    return void 0;
  }
  if (raw !== true) {
    throw new Error(
      "acknowledgeIrreversibleDelete: required and must be true \u2014 this plan contains a delete, which has NO live inverse (disposing a materialized view removes both its definition and its cluster files); the Tier-2 storage snapshot taken for this apply (when one is taken) is the only recovery path. Set acknowledgeIrreversibleDelete:true only after the human reviewing this plan has confirmed they understand this cannot be undone through admin-chat."
    );
  }
  return true;
}
function requireMvViewsheetAssetId(args) {
  const obj = isPlainObject2(args) ? args : {};
  return requireViewsheetAssetId(obj.viewsheetAssetId, "analyze_mv", "viewsheetAssetId");
}
function requireMvAnalysisId(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.analysisId;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error("analysisId: required \u2014 the analysisId returned by analyze_mv");
  }
  return raw.trim();
}
var STORED_ASSET_DRIVE_LETTER = /^[A-Za-z]:$/;
function requireSafeStoredAssetSegment(segment, raw, label) {
  if (segment === "") {
    throw new Error(`${label}: must not contain an empty path segment (got "${raw}")`);
  }
  if (segment === "." || segment === "..") {
    throw new Error(`${label}: must not contain a "." or ".." segment (got "${raw}")`);
  }
  if (STORED_ASSET_DRIVE_LETTER.test(segment)) {
    throw new Error(`${label}: must not contain a drive-letter segment (got "${raw}")`);
  }
  if (segment.startsWith("~")) {
    throw new Error(`${label}: must not contain a segment starting with "~" (got "${raw}")`);
  }
}
function requireDataSpacePath(raw, label) {
  if (typeof raw !== "string") {
    throw new Error(`${label}: required (a DataSpace-relative path, or "" for the root)`);
  }
  if (raw.indexOf("\0") >= 0) {
    throw new Error(`${label}: must not contain a NUL byte`);
  }
  const normalized = raw.replace(/\\/g, "/").trim();
  if (normalized === "/" || normalized === "." || normalized === "") {
    return "";
  }
  if (normalized.startsWith("/")) {
    throw new Error(
      `${label}: must be relative to the DataSpace root, not start with "/" (got "${raw}")`
    );
  }
  if (normalized.endsWith("/")) {
    throw new Error(`${label}: must not end with "/" (got "${raw}")`);
  }
  for (const segment of normalized.split("/")) {
    requireSafeStoredAssetSegment(segment, raw, label);
  }
  return normalized;
}
function requireStoredAssetSiblingName(raw, label) {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(`${label}: required for verb="rename" (a bare sibling name, not a path)`);
  }
  if (raw.indexOf("\0") >= 0) {
    throw new Error(`${label}: must not contain a NUL byte`);
  }
  if (raw.indexOf("/") >= 0 || raw.indexOf("\\") >= 0) {
    throw new Error(`${label}: must be a bare name, not a path (got "${raw}")`);
  }
  requireSafeStoredAssetSegment(raw, raw, label);
  return raw;
}
function readStoredAssetChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { unitType, verb, path, newName|content }, got ${typeof raw}`
    );
  }
  const unitTypeRaw = typeof raw.unitType === "string" ? raw.unitType.trim().toLowerCase() : raw.unitType;
  if (unitTypeRaw !== "file" && unitTypeRaw !== "folder") {
    throw new Error(
      `${label}.unitType: must be "file" or "folder", got ${JSON.stringify(raw.unitType)}`
    );
  }
  const unitType = unitTypeRaw;
  const verbRaw = typeof raw.verb === "string" ? raw.verb.trim().toLowerCase() : raw.verb;
  const validVerbs = unitType === "folder" ? ["create", "rename", "delete"] : ["write", "rename", "delete"];
  if (!validVerbs.includes(verbRaw)) {
    throw new Error(
      `${label}.verb: must be one of ${JSON.stringify(validVerbs)} for unitType="${unitType}", got ${JSON.stringify(raw.verb)}`
    );
  }
  const verb = verbRaw;
  const path2 = requireDataSpacePath(raw.path, `${label}.path`);
  if (verb === "delete" && path2 === "") {
    throw new Error(`${label}.path: the DataSpace root itself cannot be deleted through this tool`);
  }
  const change = { unitType, verb, path: path2 };
  if (verb === "rename") {
    change.newName = requireStoredAssetSiblingName(raw.newName, `${label}.newName`);
  } else if ("newName" in raw && raw.newName !== void 0 && raw.newName !== null) {
    throw new Error(`${label}.newName: not used for verb="${verb}"; only verb="rename" accepts it`);
  }
  if (verb === "write") {
    if (typeof raw.content !== "string") {
      throw new Error(
        `${label}.content: required for verb="write" (plain UTF-8 text; a binary sourcePath upload is not supported yet)`
      );
    }
    change.content = raw.content;
  } else if ("content" in raw && raw.content !== void 0 && raw.content !== null) {
    throw new Error(`${label}.content: not used for verb="${verb}"; only verb="write" accepts it`);
  }
  return change;
}
function normalizeStoredAssetChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error(
        "changes: must not be empty \u2014 pass at least one { unitType, verb, path, newName|content }"
      );
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error("changes: required \u2014 an array of { unitType, verb, path, newName|content }");
  }
  return entries.map(readStoredAssetChange);
}
function requireAcknowledgeIrreversibleStoredAssetDelete(changes, raw) {
  const hasFolderDelete = changes.some((c) => c.verb === "delete" && c.unitType === "folder");
  if (!hasFolderDelete) {
    return raw === true ? true : void 0;
  }
  if (raw !== true) {
    throw new Error(
      "acknowledgeIrreversibleDelete: required and must be true \u2014 this plan deletes a folder, which the server ALWAYS treats as non-compensable (no live rollback exists for a whole subtree); the Tier-2 storage snapshot is the only recovery path, and it is manual and offline. Set acknowledgeIrreversibleDelete:true only after the human reviewing this plan has confirmed they understand this delete cannot be undone through admin-chat. A file write/delete in the SAME plan may also require this flag depending on live server-side state (size, encoding) \u2014 if apply_stored_asset_changes itself refuses citing this same flag even after you set it here, that refusal is the authoritative one; this check only catches the folder case ahead of time."
    );
  }
  return true;
}
function requireUploadId(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.uploadId;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error("uploadId: required \u2014 the id returned by upload_driver_or_plugin");
  }
  return raw.trim();
}
function requirePluginIds(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.pluginIds;
  if (typeof raw === "string" && raw.trim() !== "") {
    return [raw.trim()];
  }
  if (Array.isArray(raw) && raw.length > 0 && raw.every((v) => typeof v === "string" && v.trim() !== "")) {
    return raw.map((v) => v.trim());
  }
  throw new Error(
    "pluginIds: required \u2014 a non-empty array of currently-installed driver/plugin ids (list_drivers_and_plugins' plugins[].id); a single id string is accepted as a one-element array alias"
  );
}
function requireAcknowledgeServerCodeExecution(args) {
  const obj = isPlainObject2(args) ? args : {};
  if (obj.acknowledgeServerCodeExecution !== true) {
    throw new Error(
      "acknowledgeServerCodeExecution: required and must be true \u2014 installing a plugin or driver executes arbitrary code as the server. Set this to true only after the human reviewing this action has confirmed it."
    );
  }
  return true;
}
function requireAcknowledgeIrreversibleRemove(args) {
  const obj = isPlainObject2(args) ? args : {};
  if (obj.acknowledgeIrreversibleRemove !== true) {
    throw new Error(
      "acknowledgeIrreversibleRemove: required and must be true \u2014 set this to true only after the human reviewing this action has confirmed it."
    );
  }
  return true;
}
function requireReviewOutcome(args) {
  const obj = isPlainObject2(args) ? args : {};
  const raw = obj.reviewOutcome;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "reviewOutcome: required and must not be blank \u2014 a short note on what the human reviewer confirmed before this action was taken."
    );
  }
  return raw.trim();
}
function normalizeAsDriverPlugin(raw) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  if (!isPlainObject2(raw)) {
    throw new Error(
      `asDriverPlugin: expected an object like { pluginId, pluginName, pluginVersion, drivers }, got ${typeof raw}`
    );
  }
  const label = "asDriverPlugin";
  for (const field of ["pluginId", "pluginName", "pluginVersion"]) {
    if (typeof raw[field] !== "string" || raw[field].trim() === "") {
      throw new Error(`${label}.${field}: required non-blank string when asDriverPlugin is given`);
    }
  }
  if (!Array.isArray(raw.drivers) || raw.drivers.length === 0 || !raw.drivers.every((d) => typeof d === "string" && d.trim() !== "")) {
    throw new Error(
      `${label}.drivers: required non-empty array of driver class names when asDriverPlugin is given \u2014 typically the drivers[] scan_uploaded_drivers returned`
    );
  }
  return {
    pluginId: raw.pluginId.trim(),
    pluginName: raw.pluginName.trim(),
    pluginVersion: raw.pluginVersion.trim(),
    drivers: raw.drivers.map((d) => d.trim())
  };
}
function normalizeShapeVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "upload" || v === "add") {
    return "upload";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  throw new Error(
    `${label}.verb: must be "upload" or "delete" (also accepts "add"/"remove" as aliases); got ${JSON.stringify(raw)}`
  );
}
function requireShapeName(raw, label) {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      `${label}.name: required \u2014 the shape's full filename (e.g. "arrow.svg"), a bare name only`
    );
  }
  if (raw.indexOf("\0") >= 0) {
    throw new Error(`${label}.name: must not contain a NUL byte`);
  }
  if (raw.indexOf("/") >= 0 || raw.indexOf("\\") >= 0) {
    throw new Error(
      `${label}.name: "${raw}" must be a bare filename, not a path \u2014 it must not contain "/" or "\\\\". Use subPath to target a nested folder inside the shapes tree instead.`
    );
  }
  requireSafeStoredAssetSegment(raw, raw, `${label}.name`);
  return raw;
}
function requireShapeSubPath(raw, label) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  const normalized = requireDataSpacePath(raw, `${label}.subPath`);
  return normalized === "" ? void 0 : normalized;
}
function readShapeChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(
      `${label}: expected an object like { verb, scope, name, subPath?, content? }, got ${typeof raw}`
    );
  }
  const verb = normalizeShapeVerb(raw.verb, label);
  const scope = normalizeScope(raw.scope, label);
  const name = requireShapeName(raw.name, label);
  const subPath = requireShapeSubPath(raw.subPath, label);
  const change = { verb, scope, name };
  if (subPath !== void 0) {
    change.subPath = subPath;
  }
  if (verb === "upload") {
    if (typeof raw.content !== "string" || raw.content.trim() === "") {
      throw new Error(
        `${label}.content: required for verb="upload" (the shape file's full bytes, base64-encoded)`
      );
    }
    change.content = raw.content;
  } else if ("content" in raw && raw.content !== void 0 && raw.content !== null) {
    throw new Error(`${label}.content: not used for verb="delete"; only verb="upload" accepts it`);
  }
  return change;
}
function normalizeShapeChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error(
        "changes: must not be empty \u2014 pass at least one { verb, scope, name, subPath?, content? }"
      );
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, scope, name, subPath?, content? } (verb is "upload" or "delete")'
    );
  }
  return entries.map(readShapeChange);
}
function normalizeThemeVerb(raw, label) {
  const v = typeof raw === "string" ? raw.trim().toLowerCase() : raw;
  if (v === "create" || v === "add") {
    return "create";
  }
  if (v === "delete" || v === "remove") {
    return "delete";
  }
  if (v === "update") {
    return "update";
  }
  throw new Error(
    `${label}.verb: must be "create", "delete", or "update" (also accepts "add"/"remove" as aliases for create/delete \u2014 update has no alias); got ${JSON.stringify(raw)}`
  );
}
function requireThemeId(raw, label) {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(`${label}.id: required \u2014 a theme's id, as returned by list_themes/get_theme`);
  }
  return raw.trim();
}
var THEME_SPEC_FIELDS = /* @__PURE__ */ new Set([
  "name",
  "global",
  "defaultThemeGlobal",
  "defaultThemeOrg",
  "jar",
  "portalCss",
  "emCss"
]);
function validateThemeSpecFields(spec, label) {
  for (const key of Object.keys(spec)) {
    if (spec[key] === void 0) {
      continue;
    }
    if (!THEME_SPEC_FIELDS.has(key)) {
      throw new Error(
        `${label}.spec.${key}: not a recognized Themes field \u2014 expected one of name, global, defaultThemeGlobal, defaultThemeOrg, jar, portalCss, emCss`
      );
    }
  }
}
function normalizeThemeFileData(raw, label) {
  if (!isPlainObject2(raw) || typeof raw.name !== "string" || raw.name.trim() === "" || typeof raw.content !== "string" || raw.content.trim() === "") {
    throw new Error(
      `${label}: expected an object { name, content } \u2014 content is the base64 of a FULL theme JAR archive (theme CSS plus any bundled assets), not raw CSS text; for hand-editing CSS variables directly, use portalCss/emCss instead of building a jar`
    );
  }
  return { name: raw.name, content: raw.content };
}
function normalizeThemeCssSpec(raw, label) {
  if (!isPlainObject2(raw) || !Array.isArray(raw.variables)) {
    throw new Error(`${label}: expected an object { variables: [{ name, value }] }`);
  }
  const variables = raw.variables.map((v, i) => {
    if (!isPlainObject2(v) || typeof v.name !== "string" || v.name.trim() === "" || typeof v.value !== "string") {
      throw new Error(`${label}.variables[${i}]: expected an object { name, value }`);
    }
    return { name: v.name, value: v.value };
  });
  return { variables };
}
function normalizeThemeSpecValues(raw, label) {
  const spec = {};
  if (raw.name !== void 0) {
    spec.name = raw.name;
  }
  for (const boolField of ["global", "defaultThemeGlobal", "defaultThemeOrg"]) {
    if (raw[boolField] !== void 0) {
      if (typeof raw[boolField] !== "boolean") {
        throw new Error(`${label}.spec.${boolField}: must be a boolean; got ${typeof raw[boolField]}`);
      }
      spec[boolField] = raw[boolField];
    }
  }
  if (raw.jar !== void 0) {
    spec.jar = normalizeThemeFileData(raw.jar, `${label}.spec.jar`);
  }
  if (raw.portalCss !== void 0) {
    spec.portalCss = normalizeThemeCssSpec(raw.portalCss, `${label}.spec.portalCss`);
  }
  if (raw.emCss !== void 0) {
    spec.emCss = normalizeThemeCssSpec(raw.emCss, `${label}.spec.emCss`);
  }
  return spec;
}
function readThemeChange(raw, index) {
  const label = `changes[${index}]`;
  if (!isPlainObject2(raw)) {
    throw new Error(`${label}: expected an object like { verb, id|spec }, got ${typeof raw}`);
  }
  const verb = normalizeThemeVerb(raw.verb, label);
  if (verb === "delete") {
    if ("spec" in raw && raw.spec !== void 0 && raw.spec !== null) {
      throw new Error(`${label}.spec: not used for verb=delete; remove it or use verb=create/update`);
    }
    const id2 = requireThemeId(raw.id, label);
    return { verb, id: id2 };
  }
  if (verb === "create") {
    if ("id" in raw && raw.id !== void 0 && raw.id !== null) {
      throw new Error(`${label}.id: not used for verb=create; the id is derived server-side`);
    }
    if (!isPlainObject2(raw.spec)) {
      throw new Error(`${label}.spec: required for verb=create \u2014 must include at least name`);
    }
    validateThemeSpecFields(raw.spec, label);
    if (typeof raw.spec.name !== "string" || raw.spec.name.trim() === "") {
      throw new Error(`${label}.spec.name: required non-blank string for verb=create`);
    }
    return { verb, spec: normalizeThemeSpecValues(raw.spec, label) };
  }
  const id = requireThemeId(raw.id, label);
  if (!isPlainObject2(raw.spec)) {
    throw new Error(
      `${label}.spec: required for verb=update \u2014 a partial object naming only the fields to change`
    );
  }
  validateThemeSpecFields(raw.spec, label);
  if (Object.keys(raw.spec).length === 0) {
    throw new Error(`${label}.spec: at least one field is required for verb="update" (nothing to change)`);
  }
  for (const key of Object.keys(raw.spec)) {
    if (raw.spec[key] === null) {
      throw new Error(
        `${label}.spec.${key}: explicit null is not allowed for verb="update" (the server cannot tell an explicit null apart from an omitted key) \u2014 omit it to leave unchanged.`
      );
    }
  }
  if (typeof raw.spec.name === "string" && raw.spec.name.trim() === "") {
    throw new Error(
      `${label}.spec.name: an empty name is not allowed for verb="update" \u2014 omit "name" entirely to leave it unchanged. NOTE: name is unconditionally applied when present (there is no separate rename flag) \u2014 resupply the theme's CURRENT name if you don't intend to rename it.`
    );
  }
  return { verb, id, spec: normalizeThemeSpecValues(raw.spec, label) };
}
function normalizeThemeChanges(input) {
  let entries;
  if (Array.isArray(input)) {
    if (input.length === 0) {
      throw new Error("changes: must not be empty \u2014 pass at least one { verb, id|spec }");
    }
    entries = input;
  } else if (isPlainObject2(input)) {
    entries = [input];
  } else {
    throw new Error(
      'changes: required \u2014 an array of { verb, id|spec } (verb is "create", "delete", or "update")'
    );
  }
  return entries.map(readThemeChange);
}

// src/tools/orgValidation.ts
var ORG_PREFIX = "inetsoft.org.";
function isPlainObject3(v) {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}
function parseOrgQualifiedPropertyName(name) {
  if (!name.startsWith(ORG_PREFIX)) {
    return void 0;
  }
  const rest = name.slice(ORG_PREFIX.length);
  const dot = rest.indexOf(".");
  if (dot <= 0) {
    return void 0;
  }
  return { orgId: rest.slice(0, dot), property: rest.slice(dot + 1) };
}
async function checkOrgRecognized(deps, orgId) {
  let orgs;
  try {
    orgs = await deps.wizClient.get("/v1/admin/identities/organizations");
  } catch (err) {
    if (err?.code === "HTTP_404") {
      return "unavailable";
    }
    throw err;
  }
  if (!Array.isArray(orgs)) {
    return "unavailable";
  }
  const folded = orgId.toLowerCase();
  return orgs.some((entry) => isPlainObject3(entry) && typeof entry.id === "string" && entry.id.toLowerCase() === folded);
}
var orgGuidance = (orgId) => `"${orgId}" is not a real organization on this deployment \u2014 exists here only reflects the property name, not this org.`;
async function augmentPropertyResponseWithOrgValidation(deps, name, response) {
  const parsed = parseOrgQualifiedPropertyName(name);
  if (!parsed || !isPlainObject3(response)) {
    return response;
  }
  const recognized = await checkOrgRecognized(deps, parsed.orgId);
  if (recognized === "unavailable") {
    return { ...response, orgValidation: "unavailable-community-deployment" };
  }
  if (!recognized) {
    return { ...response, orgRecognized: false, orgGuidance: orgGuidance(parsed.orgId) };
  }
  return response;
}
async function augmentChangesResponseWithOrgValidation(deps, response) {
  if (!isPlainObject3(response) || !Array.isArray(response.changes)) {
    return response;
  }
  const changes = response.changes;
  const orgIds = /* @__PURE__ */ new Set();
  for (const entry of changes) {
    if (isPlainObject3(entry) && typeof entry.property === "string") {
      const parsed = parseOrgQualifiedPropertyName(entry.property);
      if (parsed) {
        orgIds.add(parsed.orgId);
      }
    }
  }
  if (orgIds.size === 0) {
    return response;
  }
  const recognizedByOrgId = /* @__PURE__ */ new Map();
  for (const orgId of orgIds) {
    recognizedByOrgId.set(orgId, await checkOrgRecognized(deps, orgId));
  }
  const newChanges = changes.map((entry) => {
    if (!isPlainObject3(entry) || typeof entry.property !== "string") {
      return entry;
    }
    const parsed = parseOrgQualifiedPropertyName(entry.property);
    if (!parsed) {
      return entry;
    }
    const recognized = recognizedByOrgId.get(parsed.orgId);
    if (recognized === "unavailable") {
      return { ...entry, orgValidation: "unavailable-community-deployment" };
    }
    if (!recognized) {
      return { ...entry, orgRecognized: false, orgGuidance: orgGuidance(parsed.orgId) };
    }
    return entry;
  });
  return { ...response, changes: newChanges };
}

// src/tools/readTools.ts
function makeListPropertiesTool(deps) {
  return {
    name: "list_properties",
    description: "List StyleBI server properties from the admin catalog, each with its metadata (type, allowed values, min/max, description, risk, snapshotScope) and its current value. `filter` is a case-insensitive substring matched against the property name or any alias; omit it to list everything. Secret properties are listed but come back with currentValue null \u2014 they can never be READ through admin-chat. Two things make a property secret: its name (contains password/secret/credential, or ends .key, or starts license.), or its being one of the seven application credentials the server encrypts on write \u2014 openid.client.secret, stylebi.google.openid.client.secret, mail.smtp.pass, mail.smtp.clientSecret, mail.smtp.accessToken, mail.smtp.refreshToken and log.fluentd.security.sharedKey. Those seven are withheld on read AND settable through preview_changes/apply_changes, so a null currentValue for one of them means the read rule fired \u2014 never that the property is unset, absent, or unwritable. Neither rule is exhaustive: the list is maintained by hand, and a secret can live under a name matching no pattern (a connection string with an inline password, a `*.token`, a `*.pat`). If a value that looks like a credential ever comes back, treat it as one \u2014 do not repeat it to the user, put it in a summary, or carry it into another tool call.",
    inputSchema: {
      type: "object",
      properties: {
        filter: {
          type: "string",
          description: "case-insensitive substring of the property name or one of its aliases"
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const filter2 = typeof args?.filter === "string" ? args.filter.trim() : "";
      const query = filter2 === "" ? "" : `?filter=${encodeURIComponent(filter2)}`;
      return deps.wizClient.get(`/v1/admin/properties${query}`);
    }
  };
}
function makeGetPropertyTool(deps) {
  return {
    name: "get_property",
    description: 'Read one StyleBI server property: its catalog metadata and current value. Works for an uncatalogued property too \u2014 that comes back recognized:false, risk:"high", snapshotScope:"storage", with null type and description, meaning the server cannot validate values for it and will take a full storage snapshot before changing it. `name` may be an alias, or an org-qualified `inetsoft.org.{orgId}.{property}` -- the server resolves `{property}` regardless of whether `{orgId}` names a real organization, so this tool separately validates it: a fabricated org adds `orgRecognized:false` and an `orgGuidance` string to the response (a real org adds nothing, keeping the response byte-identical to a bare name); on a community deployment (organizations are enterprise-only) it cannot check at all and adds `orgValidation:"unavailable-community-deployment"` instead. Neither signal touches `recognized`/`exists`, which are about the property name only, never the org.\nREAD `exists` BEFORE DRAWING ANY CONCLUSION FROM A NULL currentValue. `exists:"confirmed"` means the property is real \u2014 it is catalogued, it is one of the seven application credentials, or it holds a value. `exists:"unknown"` means the server cannot tell: the property is uncatalogued AND unset, which is indistinguishable from a name that does not exist, and the response carries a `guidance` string saying so. A null currentValue is NOT evidence that a property is absent or that the setting lives somewhere other than server properties \u2014 on a server where a feature has never been configured, every one of its properties reads null. Confirm the spelling with search_product_docs(corpus:"properties") instead of inferring it, because an unrecognised name is written verbatim: a typo becomes an inert property nothing reads, while the change reports success.',
    inputSchema: {
      type: "object",
      properties: {
        name: {
          type: "string",
          description: "the property name, an alias, or inetsoft.org.{orgId}.{property}"
        }
      },
      required: ["name"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = requirePropertyName(args);
      const response = await deps.wizClient.get(`/v1/admin/properties/${encodeURIComponent(name)}`);
      return augmentPropertyResponseWithOrgValidation(deps, name, response);
    }
  };
}
function makeReadTools(deps) {
  return [makeListPropertiesTool(deps), makeGetPropertyTool(deps)];
}

// src/tools/changeTools.ts
var CHANGES_SCHEMA = {
  type: "array",
  description: "the proposed changes; list each property at most once",
  items: {
    type: "object",
    properties: {
      property: {
        type: "string",
        description: "the property name, or an alias. Seven application credentials belong here like any other property, because the server encrypts them on write exactly as Enterprise Manager does: openid.client.secret, stylebi.google.openid.client.secret, mail.smtp.pass, mail.smtp.clientSecret, mail.smtp.accessToken, mail.smtp.refreshToken, log.fluentd.security.sharedKey. All seven are also withheld on READ, and being unable to read a secret does not mean you cannot set one. Two near neighbours are stored as plain text despite their names and are NOT on that list, but they differ from each other: mail.smtp.tokenUri is an ordinary property, set the ordinary way; log.fluentd.security.password is refused for both reading and writing, because its NAME matches the secret pattern even though its value is not encrypted. Send the user to Enterprise Manager for that one."
      },
      value: {
        description: "the new value as a string, or null to reset the property to its default. Required \u2014 it is never inferred from omission, because a reset is destructive."
      }
    },
    required: ["property", "value"]
  }
};
var TASK_SCHEMA = {
  type: "string",
  description: "a short description of what this change accomplishes; it is written into every audit record for the transaction"
};
function makePreviewChangesTool(deps) {
  return {
    name: "preview_changes",
    description: 'Resolve a set of proposed property changes into a reviewable plan WITHOUT changing anything on the server. Returns each property\'s currentValue -> proposedValue with its risk, snapshotScope and recognized flag, plus requiresStorageBackup, requiresAgentSignoff, a planHash, and a taskToken. You MUST pass both planHash and taskToken to apply_changes: the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken -- the one reviewed here -- never from whatever task text apply_changes itself is called with. Always show the returned plan to the user and get explicit confirmation before applying. A `property` may be org-qualified (`inetsoft.org.{orgId}.{property}`); each such entry is separately checked against the real organization list, adding `orgRecognized:false` and an `orgGuidance` string when `{orgId}` is fabricated (a real org adds nothing), or `orgValidation:"unavailable-community-deployment"` when organizations cannot be checked at all (community deployment) -- never inferred from `recognized`/`exists`, which are about the property name only.',
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA, changes: CHANGES_SCHEMA },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const response = await deps.wizClient.post("/v1/admin/preview", {
        task: requireTask(args?.task),
        changes: normalizeChanges(args?.changes)
      });
      return augmentChangesResponseWithOrgValidation(deps, response);
    }
  };
}
function makeApplyChangesTool(deps) {
  return {
    name: "apply_changes",
    description: `Apply a previewed plan. Pass back the same changes you previewed, the planHash AND the taskToken from preview_changes \u2014 the request body IS the plan; the server never trusts a stored one. It re-resolves the changes, recomputes the hash, and refuses on mismatch. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_changes. If the previewed plan had requiresAgentSignoff:true you MUST also pass a non-blank reviewOutcome (the admin-reviewer subagent's verdict), or the apply is refused. Take a snapshot first when the plan says requiresStorageBackup. Result status is one of: "applied" (every change verified), "rolled-back" (something failed and every applied change was undone), or "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate to the operator, do not retry). A returned status of "conflict" means the plan drifted and nothing was applied.`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA,
        changes: CHANGES_SCHEMA,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record. Required when the plan's requiresAgentSignoff is true."
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_changes for this plan. Without it the server cannot verify that what you are applying is what was reviewed. Call preview_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_changes first. If preview_changes's response contained no taskToken at all, this StyleBI deployment predates the pinned-audit contract and must be upgraded \u2014 do not retry."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      let response;
      try {
        response = await deps.wizClient.post("/v1/admin/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_changes and apply_changes \u2014 a property's current value changed, so the plan you reviewed is not the plan that would execute. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call preview_changes again and use THAT response's planHash and taskToken \u2014 the returned `plan` here has no usable taskToken (the server never returns one in a conflict, so a stale narrative can't be replayed), so reusing anything from it will only produce another conflict."
          };
        }
        throw err;
      }
      return augmentChangesResponseWithOrgValidation(deps, response);
    }
  };
}
function makeChangeTools(deps) {
  return [makePreviewChangesTool(deps), makeApplyChangesTool(deps)];
}

// src/tools/scheduleTools.ts
import crypto5 from "crypto";

// src/tools/scheduleTaskIntegrity.ts
import crypto4 from "crypto";
var PROTECTED_FIELDS = [
  "alerts",
  "parameters",
  "saveToServerFilePaths",
  "sender",
  "ccAddresses",
  "bccAddresses",
  "attachmentName",
  "bookmarkUsers",
  "bookmarkTypes",
  "bookmarkName",
  "bookmarkType",
  "bookmarkUser",
  "emailZip",
  "emailLink",
  "notifyLink",
  "notifyIfFailed",
  "htmlMessage",
  "emailMatch",
  "emailExpandSelections",
  "emailOnlyDataComponents",
  "exportAllTabbedTables",
  "emailCSVConfig",
  "saveToServerMatch",
  "saveToServerExpandSelections",
  "saveToServerOnlyDataComponents",
  "saveExportAllTabbedTables",
  "saveToServerCsvConfig"
];
var ENVELOPE_PREFIX = "pgi1:";
function isPlainObject4(v) {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}
function sortKeysDeep(value) {
  if (Array.isArray(value)) {
    return value.map(sortKeysDeep);
  }
  if (isPlainObject4(value)) {
    const sorted = {};
    for (const key of Object.keys(value).sort()) {
      sorted[key] = sortKeysDeep(value[key]);
    }
    return sorted;
  }
  return value;
}
function digestOf(value) {
  return crypto4.createHash("sha256").update(JSON.stringify(sortKeysDeep(value))).digest("hex");
}
function digestsForChange(change) {
  if (change.verb !== "create") {
    return null;
  }
  const actions = change.spec?.actions;
  if (!Array.isArray(actions) || actions.length === 0) {
    return null;
  }
  const perFieldValues = PROTECTED_FIELDS.map(
    (field) => actions.map((a) => isPlainObject4(a) ? a[field] ?? null : null)
  );
  const present = perFieldValues.map((values) => values.some((v) => v !== null));
  if (!present.some(Boolean)) {
    return null;
  }
  const digests = {};
  PROTECTED_FIELDS.forEach((field, i) => {
    digests[field] = present[i] ? digestOf(perFieldValues[i]) : null;
  });
  return digests;
}
function computeProtectedFieldDigests(changes) {
  return changes.map(digestsForChange);
}
function hasAnyProtectedField(digests) {
  return digests.some((d) => d !== null && PROTECTED_FIELDS.some((field) => d[field] !== null));
}
function wrapScheduleTaskToken(realToken, changes) {
  const digests = computeProtectedFieldDigests(changes);
  if (!hasAnyProtectedField(digests)) {
    return realToken;
  }
  const envelope = { v: 1, token: realToken, digests };
  return ENVELOPE_PREFIX + Buffer.from(JSON.stringify(envelope), "utf8").toString("base64");
}
function unwrapScheduleTaskToken(taskToken, changes) {
  const applyDigests = computeProtectedFieldDigests(changes);
  if (!taskToken.startsWith(ENVELOPE_PREFIX)) {
    if (hasAnyProtectedField(applyDigests)) {
      throw new Error(
        `taskToken: this plan sets ${PROTECTED_FIELDS.join("/")} on a create action, but the given taskToken was not issued by previewing this exact plan (or has been tampered with) \u2014 call preview_schedule_task_changes again with the plan you intend to apply.`
      );
    }
    return taskToken;
  }
  let envelope;
  try {
    envelope = JSON.parse(Buffer.from(taskToken.slice(ENVELOPE_PREFIX.length), "base64").toString("utf8"));
  } catch {
    throw new Error(
      "taskToken: could not be decoded \u2014 call preview_schedule_task_changes again for this plan."
    );
  }
  if (!isPlainObject4(envelope) || envelope.v !== 1 || typeof envelope.token !== "string" || !Array.isArray(envelope.digests)) {
    throw new Error(
      "taskToken: not a recognized schedule-task token \u2014 call preview_schedule_task_changes again for this plan."
    );
  }
  if (envelope.digests.length !== applyDigests.length) {
    throw new Error(
      `taskToken: was issued for a plan with ${envelope.digests.length} change(s), but this call carries ${applyDigests.length} \u2014 call preview_schedule_task_changes again for this exact plan.`
    );
  }
  const mismatches = [];
  envelope.digests.forEach((expected, index) => {
    const actual = applyDigests[index];
    PROTECTED_FIELDS.forEach((field) => {
      const expectedDigest = expected && typeof expected === "object" ? expected[field] ?? null : null;
      const actualDigest = actual ? actual[field] : null;
      if (expectedDigest !== actualDigest) {
        mismatches.push(`changes[${index}].spec.actions[].${field}`);
      }
    });
  });
  if (mismatches.length > 0) {
    throw new Error(
      `taskToken: ${mismatches.join(", ")} no longer match what was reviewed at preview_schedule_task_changes \u2014 refusing to apply a plan that drifted from what was approved. Call preview_schedule_task_changes again with the current values and get it re-reviewed.`
    );
  }
  return envelope.token;
}

// src/tools/scheduleTools.ts
var SPEC_DESCRIPTION = `required when verb is "create" (also accepts "add"); ignored/refused when verb is "delete"/"run"/"stop" (also accepts "remove"/"run_now"/"trigger"/"stop_now"/"kill" as aliases for those three). Fields, matching the server's CreateScheduleTaskRequest exactly: name (string, required), owner ({name, orgID}, required), enabled (boolean), deleteIfNotScheduledToRun (boolean), startDate/endDate (epoch millis), description (string), locale (string), executeAsID ({type: "USER"|"GROUP", identityID: {name, orgID}}) \u2014 omit to run as the owner. conditions (required, non-empty array) \u2014 this area's first cut supports only time-based conditions: {conditionType: "time", type, hour, minute, second, interval, daysOfWeek, dayOfMonth, weekOfMonth, dayOfWeek, monthsOfYear, weekdayOnly, date (ISO-8601 string with an explicit UTC offset, e.g. "2026-09-10T03:00:00Z" \u2014 NOT epoch millis like startDate/endDate), timeRange, timeZone}. type is one of "AT", "DAY_OF_WEEK", "EVERY_DAY", "EVERY_WEEK", "EVERY_MONTH", "EVERY_HOUR" (case-insensitive; spaces/hyphens/underscores are folded, e.g. "every-day" matches "EVERY_DAY"), or the equivalent ordinal number; "DAY_OF_MONTH"/"WEEK_OF_MONTH" are deprecated aliases and refused, not silently accepted \u2014 use "EVERY_MONTH" instead. an AT condition's hour/minute/second are refused if set to anything other than -1/omitted \u2014 AT's run time is governed entirely by date server-side, and hour/minute/second are silently ignored there, so this area refuses them rather than accept a value that would quietly do nothing. each type requires the fields the server actually reads to compute a fire time, refused loud (not silently defaulted) if missing \u2014 "DAY_OF_WEEK"/"EVERY_WEEK"/"EVERY_HOUR" require a non-empty daysOfWeek (each element 1-7, 1=Sunday); "EVERY_MONTH" requires a non-empty monthsOfYear (each element 0-11, 0=January) plus exactly one of: dayOfMonth (a non-zero integer -31..31; positive counts from the start of the month, negative from the end; 0 is refused, not "unset", since it silently matches every day server-side) OR weekOfMonth (1-5) together with dayOfWeek (1-7, e.g. weekOfMonth=2, dayOfWeek=3 means "the 2nd Tuesday"); giving both or neither alternative is refused. a completion condition (runs after another task) is refused, not silently dropped. actions (optional array) \u2014 only {actionType: "viewsheet", viewsheet, bookmarkNames (array of bookmark name strings), bookmarkUsers (array of {name, orgID}, index-paired 1:1 with bookmarkNames), emails (array of email address strings), notifies (array of notification recipient strings), format, subject, message, alerts, parameters, saveToServerFilePaths, ...} is supported; a single string is accepted as a one-element-array alias for bookmarkNames/emails/notifies. a batch or asset-backup action is refused, not silently dropped. bookmarkUsers is the owner of each named bookmark; required if any named bookmark is NOT owned by this task's own owner. Omitting it defaults every entry to this task's own owner (same convention as executeAsID's "omit to run as the owner") \u2014 but bookmark ownership is independent of task ownership, so if any bookmark in bookmarkNames actually belongs to a different user, this default is WRONG and the task will fail silently at its next scheduled run (not at preview or apply time) with a "bookmark not found" error. Supply bookmarkUsers explicitly whenever a bookmark's owner might differ from the task owner. alerts (optional array) gates whether a time-scheduled task's delivery actually fires: {elementId, highlightName} \u2014 the assembly and highlight name whose condition must currently be met (this is how a real "Alert"/"Automated Alert" is built; there is no separate alert conditionType). parameters (optional object, paramName -> value) supplies parameters when opening the viewsheet \u2014 each value is either a bare scalar, or one of {value, dataType}, {value, type: "expression", dataType}, {value, array: true, dataType}. type is accepted in any case ("Expression"/"EXPRESSION"/"expression") and is normalized to the server's own uppercase "EXPRESSION" before the request is sent; anything not recognizably "expression" is refused, naming the field. For an expression parameter, value must be a string and does not need a leading "=" \u2014 one is prepended automatically if missing (and left alone if already present, never doubled). saveToServerFilePaths (optional array) delivers the export to disk/FTP instead of (or in addition to) email: {format, path, username?, password?, useCredential?, secretId?} \u2014 useCredential: true requires secretId and forbids username/password; useCredential: false (the default) requires both username and password and forbids secretId.`;
var CHANGES_SCHEMA2 = {
  type: "array",
  description: `the proposed schedule-task changes; each entry creates one task, deletes one, triggers one task's next run immediately (verb=run), or stops one task's currently-executing run if any (verb=stop). List each task id at most once. run/stop are non-rollback-eligible (no "undo" \u2014 running/stopping a task cannot be un-done the way a create/delete's rollback can, the same treatment data source/viewsheet/organization deletes and Cluster's pause/resume already get) and are recommended NOT to be mixed with create/delete entries in the same call: when mixed, only the create/delete portion is protected by planHash/taskToken drift-detection between preview and apply \u2014 the run/stop portion is always re-resolved fresh at apply time instead.`,
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"create", "delete", "run", or "stop" (also accepts "add"/"remove" as aliases for "create"/"delete", "run_now"/"trigger" as aliases for "run", "stop_now"/"kill" as aliases for "stop")'
      },
      taskId: {
        type: "string",
        description: "required for verb=delete/run/stop (also accepts `id`/`name`); not used for verb=create"
      },
      spec: { type: "object", description: SPEC_DESCRIPTION }
    },
    required: ["verb"]
  }
};
var TASK_SCHEMA2 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListScheduleTasksTool(deps) {
  return {
    name: "list_schedule_tasks",
    description: "List schedule tasks visible to the caller. Automatically scoped to the caller's organization and permissions by the server \u2014 no filter argument in this area's first cut. Each task also carries a top-level taskId field (a duplicate of its own nested status.task value) alongside name, so the fully-qualified id get_schedule_task's lookup actually needs is visible without having to dig into status -- a bare name still works there too, via a fallback resolution. Served by the StyleBI enterprise module's ScheduleApiService.",
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      const response = await deps.wizClient.get("/v1/admin/schedule/tasks");
      if (typeof response === "object" && response !== null && Array.isArray(response.tasks)) {
        const tasks = response.tasks;
        const withTaskId = tasks.map((entry) => {
          if (typeof entry !== "object" || entry === null) {
            return entry;
          }
          const record = entry;
          const status = record.status;
          const qualifiedId = typeof status === "object" && status !== null ? status.task : void 0;
          if (typeof qualifiedId !== "string" || qualifiedId.trim() === "") {
            return record;
          }
          return { ...record, taskId: qualifiedId };
        });
        return { ...response, tasks: withTaskId };
      }
      return response;
    }
  };
}
function looksLikeQualifiedScheduleTaskId(taskId) {
  return taskId.includes("~;~");
}
async function fetchScheduleTaskList(deps) {
  const response = await deps.wizClient.get("/v1/admin/schedule/tasks");
  const tasks = typeof response === "object" && response !== null ? response.tasks : void 0;
  return Array.isArray(tasks) ? tasks : void 0;
}
function matchQualifiedScheduleTaskId(taskId, tasks) {
  if (!Array.isArray(tasks)) {
    return taskId;
  }
  const qualifiedMatches = [];
  for (const entry of tasks) {
    if (typeof entry !== "object" || entry === null) {
      continue;
    }
    const record = entry;
    if (record.name !== taskId) {
      continue;
    }
    const status = record.status;
    const qualifiedId = typeof status === "object" && status !== null ? status.task : void 0;
    if (typeof qualifiedId === "string" && qualifiedId.trim() !== "") {
      qualifiedMatches.push(qualifiedId);
    }
  }
  if (qualifiedMatches.length === 1) {
    return qualifiedMatches[0];
  }
  if (qualifiedMatches.length > 1) {
    const owners = qualifiedMatches.map((id) => id.split("~;~")[0]);
    throw new Error(
      `taskId: "${taskId}" matches ${qualifiedMatches.length} tasks with different owners (${owners.join(", ")}) \u2014 use the fully-qualified id from list_schedule_tasks's own status.task field to disambiguate`
    );
  }
  return taskId;
}
async function resolveScheduleTaskId(deps, taskId) {
  if (looksLikeQualifiedScheduleTaskId(taskId)) {
    return taskId;
  }
  const tasks = await fetchScheduleTaskList(deps);
  return matchQualifiedScheduleTaskId(taskId, tasks);
}
function referencesExistingTask(verb) {
  return verb === "delete" || verb === "run" || verb === "stop";
}
async function resolveExistingTaskChangeIds(deps, changes) {
  const needsResolution = changes.some(
    (change) => referencesExistingTask(change.verb) && typeof change.taskId === "string" && !looksLikeQualifiedScheduleTaskId(change.taskId)
  );
  const tasks = needsResolution ? await fetchScheduleTaskList(deps) : void 0;
  return changes.map((change) => {
    if (!referencesExistingTask(change.verb) || typeof change.taskId !== "string" || looksLikeQualifiedScheduleTaskId(change.taskId)) {
      return change;
    }
    return { ...change, taskId: matchQualifiedScheduleTaskId(change.taskId, tasks) };
  });
}
function splitScheduleChanges(changes) {
  const planChanges = [];
  const actionEntries = [];
  for (const change of changes) {
    if ((change.verb === "run" || change.verb === "stop") && typeof change.taskId === "string") {
      actionEntries.push({ taskId: change.taskId, verb: change.verb });
    } else {
      planChanges.push(change);
    }
  }
  return { planChanges, actionEntries };
}
function describeScheduleAction(entry) {
  return entry.verb === "run" ? `Trigger task "${entry.taskId}"'s next run immediately.` : `Stop task "${entry.taskId}"'s currently-executing run, if any.`;
}
function actionPlanChangeEntries(actionEntries) {
  return actionEntries.map((entry) => ({
    property: entry.taskId,
    orgId: null,
    currentValue: null,
    proposedValue: null,
    risk: "high",
    snapshotScope: "none",
    recognized: true,
    description: describeScheduleAction(entry),
    verb: entry.verb
  }));
}
var ACTION_PLAN_HASH_PREFIX = "schedule-action-plan:";
function computeActionPlanHash(actionEntries) {
  const canonical = actionEntries.map((entry) => ({ taskId: entry.taskId, verb: entry.verb })).sort((a, b) => a.taskId === b.taskId ? a.verb.localeCompare(b.verb) : a.taskId.localeCompare(b.taskId));
  return ACTION_PLAN_HASH_PREFIX + crypto5.createHash("sha256").update(JSON.stringify(canonical)).digest("hex");
}
async function applyScheduleActions(deps, actionEntries) {
  const outcomesByTaskId = /* @__PURE__ */ new Map();
  for (const verb of ["run", "stop"]) {
    const taskNames = actionEntries.filter((entry) => entry.verb === verb).map((entry) => entry.taskId);
    if (taskNames.length === 0) {
      continue;
    }
    const path2 = verb === "run" ? "/v1/admin/schedule/run-tasks" : "/v1/admin/schedule/stop-tasks";
    const response = await deps.wizClient.post(path2, { taskNames });
    const results = typeof response === "object" && response !== null ? response.results : void 0;
    (Array.isArray(results) ? results : []).forEach((outcome) => {
      if (typeof outcome !== "object" || outcome === null) {
        return;
      }
      const record = outcome;
      if (typeof record.taskName !== "string") {
        return;
      }
      outcomesByTaskId.set(record.taskName, {
        property: record.taskName,
        before: null,
        after: null,
        status: record.status,
        error: record.error ?? null
      });
    });
  }
  return actionEntries.map((entry) => outcomesByTaskId.get(entry.taskId) ?? {
    property: entry.taskId,
    before: null,
    after: null,
    status: "failed",
    error: `the server returned no result for task "${entry.taskId}" (verb=${entry.verb})`
  });
}
var SCHEDULE_ACTION_SUCCESS_STATUSES = ["started", "stopped"];
function skippedScheduleActionResults(actionEntries, createDeleteStatus) {
  return actionEntries.map((entry) => ({
    property: entry.taskId,
    before: null,
    after: null,
    status: "skipped",
    error: `run/stop was not attempted: the create/delete portion of this mixed batch came back "${createDeleteStatus}", not "applied"`
  }));
}
function maskSaveToServerFilePasswords(response) {
  if (typeof response !== "object" || response === null) {
    return response;
  }
  const record = response;
  const actions = record.actions;
  if (!Array.isArray(actions)) {
    return response;
  }
  const maskedActions = actions.map((action) => {
    if (typeof action !== "object" || action === null) {
      return action;
    }
    const actionRecord = action;
    const paths = actionRecord.saveToServerFilePaths;
    if (!Array.isArray(paths)) {
      return action;
    }
    const maskedPaths = paths.map((entry) => {
      if (typeof entry !== "object" || entry === null) {
        return entry;
      }
      const entryRecord = entry;
      if (typeof entryRecord.password !== "string") {
        return entry;
      }
      return { ...entryRecord, password: MASKED_SCHEDULE_TASK_PASSWORD };
    });
    return { ...actionRecord, saveToServerFilePaths: maskedPaths };
  });
  return { ...record, actions: maskedActions };
}
function maskXmlPasswordAttributes(xml) {
  return xml.replace(/ password="[^"]*"/g, ` password="${MASKED_SCHEDULE_TASK_PASSWORD}"`);
}
function maskPreviewPlanPasswords(plan) {
  if (typeof plan !== "object" || plan === null) {
    return plan;
  }
  const record = plan;
  const changes = record.changes;
  if (!Array.isArray(changes)) {
    return plan;
  }
  const maskedChanges = changes.map((change) => {
    if (typeof change !== "object" || change === null) {
      return change;
    }
    const changeRecord = change;
    if (typeof changeRecord.currentValue !== "string" || !changeRecord.currentValue.includes('password="')) {
      return change;
    }
    return { ...changeRecord, currentValue: maskXmlPasswordAttributes(changeRecord.currentValue) };
  });
  return { ...record, changes: maskedChanges };
}
function maskApplyResultPasswords(response) {
  if (typeof response !== "object" || response === null) {
    return response;
  }
  const record = response;
  const results = record.results;
  if (!Array.isArray(results)) {
    return response;
  }
  const maskedResults = results.map((outcome) => {
    if (typeof outcome !== "object" || outcome === null) {
      return outcome;
    }
    const outcomeRecord = outcome;
    const masked = { ...outcomeRecord };
    ["before", "after"].forEach((field) => {
      const value = outcomeRecord[field];
      if (typeof value === "string" && value.includes('password="')) {
        masked[field] = maskXmlPasswordAttributes(value);
      }
    });
    return masked;
  });
  return { ...record, results: maskedResults };
}
function applyTaskTokenEnvelope(response, changes) {
  if (typeof response !== "object" || response === null) {
    return response;
  }
  const record = response;
  if (typeof record.taskToken !== "string") {
    return response;
  }
  return { ...record, taskToken: wrapScheduleTaskToken(record.taskToken, changes) };
}
function makeGetScheduleTaskTool(deps) {
  return {
    name: "get_schedule_task",
    description: "Read one schedule task in full: the task itself plus its conditions and actions, combined into one response (the server's own task DTO alone carries neither). An unrecognized taskId comes back as a structured not-found error, not an empty/null result. taskId accepts either the fully-qualified id (list_schedule_tasks' nested status.task field) or the bare name list_schedule_tasks shows prominently -- a bare name is resolved against a fresh task list before the lookup. Task names are unique only per-owner, so a bare name matching more than one task (different owners) is refused loud rather than silently resolved to one of them. A viewsheet action's saveToServerFilePaths[].password, if present, comes back as a fixed masked placeholder, never the real value -- the same convention get_data_source/get_auth_provider already use for their own credential fields.",
    inputSchema: {
      type: "object",
      properties: {
        taskId: { type: "string", description: "the task id; `id` and `name` are accepted aliases" }
      },
      required: ["taskId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const taskId = requireTaskId(args);
      const resolvedId = await resolveScheduleTaskId(deps, taskId);
      const response = await deps.wizClient.get(`/v1/admin/schedule/tasks/${encodeURIComponent(resolvedId)}`);
      return maskSaveToServerFilePasswords(response);
    }
  };
}
function makePreviewScheduleTaskChangesTool(deps) {
  return {
    name: "preview_schedule_task_changes",
    description: "Resolve a set of proposed schedule-task creates/deletes/runs/stops into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true); create/delete additionally always require a storage snapshot (requiresStorageBackup is true whenever the plan contains either) \u2014 a task's actions can email or export data externally, and its executeAsID can run it under another identity's authority. run/stop are NON-ROLLBACK-ELIGIBLE \u2014 triggering or stopping a task's execution cannot be un-done the way a create/delete's rollback can, the same treatment data source/viewsheet/organization deletes and Cluster's pause/resume already document for their own non-compensable actions \u2014 a run/stop entry's own changes[] description says so plainly rather than implying an undo guarantee that cannot exist; `spec` is not used for either (refused if present). You MUST pass the returned planHash AND taskToken to apply_schedule_task_changes. For a create/delete entry, the server re-resolves the plan there and refuses with a conflict if anything drifted in between (including another caller creating a task with the same id, or deleting/editing the task you asked to delete); the server writes the audit record from the task narrative embedded in taskToken \u2014 the one reviewed here \u2014 never from whatever task text apply_schedule_task_changes itself is called with. A preview containing a create/delete entry can itself be refused if that entry would be un-rollback-able for you specifically \u2014 e.g. you may CREATE a task but not hold the DELETE permission its rollback would need, or you may DELETE a task you do not own without holding ADMIN over its owner, which its rollback (re-creating it) would need. A delete entry's changes[].currentValue is the existing task's own raw XML serialization (used for the plan hash/apply-time drift check) \u2014 any saveToServerFilePaths/email-zip password embedded in it comes back masked, the same as get_schedule_task. For a run/stop entry, there is no stored \"before\" value to diff (the closest analog, the task's live run status, is not what changes) \u2014 its changes[] entry is a plain description of the intended action, and its taskId is re-resolved fresh again at apply time rather than diffed against anything captured here. A plan with NO create/delete entries (run/stop only, the common case for these two verbs) never reaches the server's create/delete plan resolver at all \u2014 planHash/taskToken are computed locally by this plugin instead, and still detect drift (e.g. a taskId changing) between this call and apply_schedule_task_changes. RECOMMENDATION: do not mix run/stop entries with create/delete entries in the same call \u2014 if mixed, only the create/delete portion is protected by planHash/taskToken drift-detection; the run/stop portion is always re-resolved fresh at apply time regardless. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint (a deployment new enough for create/delete but predating this fix 404s specifically on the run-tasks/stop-tasks endpoints run/stop rely on).",
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA2, changes: CHANGES_SCHEMA2 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const allChanges = await resolveExistingTaskChangeIds(deps, normalizeScheduleChanges(args?.changes));
      const { planChanges, actionEntries } = splitScheduleChanges(allChanges);
      if (planChanges.length === 0) {
        const hash = computeActionPlanHash(actionEntries);
        return {
          task,
          changes: actionPlanChangeEntries(actionEntries),
          requiresAgentSignoff: true,
          requiresStorageBackup: false,
          planHash: hash,
          taskToken: hash
        };
      }
      const response = await deps.wizClient.post("/v1/admin/schedule/preview", {
        task,
        changes: planChanges
      });
      const withToken = applyTaskTokenEnvelope(maskPreviewPlanPasswords(response), planChanges);
      if (actionEntries.length === 0) {
        return withToken;
      }
      const plan = withToken;
      const existingChanges = Array.isArray(plan.changes) ? plan.changes : [];
      return { ...plan, changes: [...existingChanges, ...actionPlanChangeEntries(actionEntries)] };
    }
  };
}
function makeApplyScheduleTaskChangesTool(deps) {
  return {
    name: "apply_schedule_task_changes",
    description: `Apply a previewed schedule-task plan. Pass back the SAME task and changes you previewed plus the planHash AND the taskToken from preview_schedule_task_changes. Every plan in this area requires a non-blank reviewOutcome (the admin-reviewer subagent's verdict) because every verb is always high risk. For a plan containing create/delete entries, the request body IS that portion of the plan \u2014 the server never trusts a stored one, re-resolves it, recomputes the hash, and refuses on mismatch. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field. Result status for that portion is one of: "applied" (every change verified), "rolled-back" (something failed and every applied change was undone \u2014 a create's rollback deletes the task it created; a delete's rollback re-creates the task from a snapshot taken at apply time), or "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate to the operator, do not retry). A returned status of "conflict" means the plan drifted and NOTHING was applied, including any run/stop entries in the same call \u2014 the fresh plan carried in its plan field has any embedded saveToServerFilePaths/email-zip password masked, the same as preview_schedule_task_changes and get_schedule_task. On "applied"/"rolled-back"/"rollback-failed", each results[] entry's before/after value has any embedded saveToServerFilePaths/email-zip password masked too, the same convention. For a run/stop entry, there is nothing to roll back (non-rollback-eligible \u2014 see preview_schedule_task_changes' own description) \u2014 it is executed directly against the new run-tasks/stop-tasks endpoints, and its own results[] entry's status is one of "started"/"stopped" (success), "scheduler-not-running"/"task-disabled" (a clear, field-named refusal \u2014 never a bare, locale-dependent message string to pattern-match), or "failed" (any other error, with a human-readable error field). In a MIXED batch, the run/stop portion is executed ONLY when the create/delete portion's own status came back "applied" \u2014 for "rolled-back"/"rollback-failed", run/stop is never attempted (an irreversible action must not fire for a create/delete that did not actually succeed), and each skipped entry's own results[] status is "skipped", naming the create/delete status that caused the skip. When the create/delete portion IS "applied" but one or more run/stop entries did not succeed, the merged top-level status is downgraded from "applied" to "partial" \u2014 never report a mixed batch as "applied" while an actionResults entry reads "failed"/"scheduler-not-running"/"task-disabled". A plan with NO create/delete entries (run/stop only) never calls the server's create/delete apply endpoint at all; its overall status is "applied" (every run/stop succeeded), "partial" (a mix), or "failed" (every run/stop failed) \u2014 matching Cluster's own status vocabulary for the identical "independent, self-inverse, no rollback" action shape. Served by the StyleBI enterprise module for create/delete; a community-only deployment 404s on this endpoint for a create/delete entry, and a community-only or pre-fix deployment 404s specifically on the run-tasks/stop-tasks endpoints a run/stop entry needs.`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA2,
        changes: CHANGES_SCHEMA2,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_schedule_task_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_schedule_task_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_schedule_task_changes for this plan. Call preview_schedule_task_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_schedule_task_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_schedule_task_changes first. If preview_schedule_task_changes's response contained no taskToken at all, this StyleBI deployment predates the pinned-audit contract and must be upgraded \u2014 do not retry."
        );
      }
      const planHash = args.planHash.trim();
      const taskToken = args.taskToken.trim();
      const task = requireTask(args.task);
      const allChanges = await resolveExistingTaskChangeIds(deps, normalizeScheduleChanges(args.changes));
      const { planChanges, actionEntries } = splitScheduleChanges(allChanges);
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (planChanges.length === 0) {
        const expectedHash = computeActionPlanHash(actionEntries);
        if (planHash !== expectedHash || taskToken !== expectedHash) {
          throw new Error(
            "planHash/taskToken: do not match this plan \u2014 a run/stop-only plan's hash is computed locally by this plugin (there is no create/delete changeset here for the server to verify). Call preview_schedule_task_changes again for the exact plan you intend to apply."
          );
        }
        const results = await applyScheduleActions(deps, actionEntries);
        const succeeded = (status) => status === "started" || status === "stopped";
        return {
          transactionId: null,
          status: results.every((r) => succeeded(r.status)) ? "applied" : results.some((r) => succeeded(r.status)) ? "partial" : "failed",
          backupRef: null,
          results
        };
      }
      const body = {
        task,
        changes: planChanges,
        planHash,
        taskToken: unwrapScheduleTaskToken(taskToken, planChanges)
      };
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        const response = maskApplyResultPasswords(await deps.wizClient.post("/v1/admin/schedule/apply", body));
        if (actionEntries.length === 0) {
          return response;
        }
        const record = response;
        const existingResults = Array.isArray(record.results) ? record.results : [];
        if (record.status !== "applied") {
          return {
            ...record,
            results: [...existingResults, ...skippedScheduleActionResults(actionEntries, record.status)]
          };
        }
        const actionResults = await applyScheduleActions(deps, actionEntries);
        const allActionsSucceeded = actionResults.every(
          (r) => SCHEDULE_ACTION_SUCCESS_STATUSES.includes(r.status)
        );
        return {
          ...record,
          status: allActionsSucceeded ? record.status : "partial",
          results: [...existingResults, ...actionResults]
        };
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: applyTaskTokenEnvelope(maskPreviewPlanPasswords(detail.plan ?? null), planChanges),
            guidance: "The plan drifted between preview_schedule_task_changes and apply_schedule_task_changes \u2014 a task was created, deleted, or edited by someone else in between, so the plan you reviewed is not the plan that would execute. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_schedule_task_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeScheduleTools(deps) {
  return [
    makeListScheduleTasksTool(deps),
    makeGetScheduleTaskTool(deps),
    makePreviewScheduleTaskChangesTool(deps),
    makeApplyScheduleTaskChangesTool(deps)
  ];
}

// src/tools/auditTools.ts
function requireTransactionId(args) {
  const obj = typeof args === "object" && args !== null ? args : {};
  const raw = obj.transactionId ?? obj.txId ?? obj.transaction_id;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "transactionId: required (the id returned by apply_changes; `txId` is an accepted alias)"
    );
  }
  return raw.trim();
}
function optionalCount(raw, field) {
  if (raw === void 0 || raw === null) {
    return void 0;
  }
  const n = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isInteger(n) || n < 0) {
    throw new Error(`${field}: expected a non-negative integer, got ${JSON.stringify(raw)}`);
  }
  return n;
}
function makeListChangesetsTool(deps) {
  return {
    name: "list_changesets",
    description: 'List past admin-chat transactions, newest first: transactionId, the task description, how many properties changed, and the last status and timestamp. Timestamps are formatted strings in server-local time ("yyyy-MM-dd HH:mm:ss"), not epoch numbers \u2014 show them as-is. Served by the StyleBI enterprise module; a community-only deployment returns 404.',
    inputSchema: {
      type: "object",
      properties: {
        limit: { type: "integer", description: "maximum number of transactions to return" },
        offset: { type: "integer", description: "how many to skip, for paging" }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const limit = optionalCount(args?.limit, "limit");
      const offset = optionalCount(args?.offset, "offset");
      const params = new URLSearchParams();
      if (limit !== void 0) {
        params.set("limit", String(limit));
      }
      if (offset !== void 0) {
        params.set("offset", String(offset));
      }
      const query = params.toString();
      return deps.wizClient.get(`/v1/admin/changesets${query === "" ? "" : `?${query}`}`);
    }
  };
}
function maskChangesetPasswords(response) {
  if (!Array.isArray(response)) {
    return response;
  }
  return response.map((record) => {
    if (typeof record !== "object" || record === null) {
      return record;
    }
    const recordObj = record;
    const masked = { ...recordObj };
    ["beforeValue", "afterValue"].forEach((field) => {
      const value = recordObj[field];
      if (typeof value === "string" && value.includes('password="')) {
        masked[field] = maskXmlPasswordAttributes(value);
      }
    });
    return masked;
  });
}
function makeGetChangesetTool(deps) {
  return {
    name: "get_changeset",
    description: `Read the audit records for one admin-chat transaction, time-ordered: per property the before and after values, the status, the reviewer's recorded verdict, and the snapshot reference if one was taken. This is how a past change is understood \u2014 and how it is manually reverted, by applying the before-values as a new change. Timestamps are formatted strings ("yyyy-MM-dd HH:mm:ss"), not epoch numbers. Any embedded saveToServerFilePaths/email-zip password in a schedule-task record's before/after value comes back masked, the same as get_schedule_task/preview_schedule_task_changes/apply_schedule_task_changes. Served by the StyleBI enterprise module; a community-only deployment returns 404.`,
    inputSchema: {
      type: "object",
      properties: {
        transactionId: { type: "string", description: "the id returned by apply_changes" }
      },
      required: ["transactionId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireTransactionId(args);
      return maskChangesetPasswords(await deps.wizClient.get(`/v1/admin/changesets/${encodeURIComponent(id)}`));
    }
  };
}
function makeBackupTool(deps) {
  return {
    name: "backup",
    description: "Take a full storage snapshot for a transaction and return its external-storage path. This path is NOT recorded in the audit trail and is not otherwise recoverable through get_changeset: backup() writes no audit record of its own, and get_changeset's backupRef field only ever reflects the automatic snapshot apply_changes itself took, never a later standalone backup() call. To check, at any later point, whether a transaction's snapshot still survives ai.snapshot.count's retention window, use get_transaction_snapshots(transactionId). apply_changes already takes its own snapshot when the plan requires one, so you do not normally need this. The snapshot is NOT restorable through this API: restoring storage on a running server is untested and needs a restart, so recovery is a manual offline operation by an administrator, or a revert of the individual changes recorded in the audit records.",
    inputSchema: {
      type: "object",
      properties: {
        transactionId: {
          type: "string",
          description: "the transaction this snapshot protects; becomes part of the snapshot name"
        }
      },
      required: ["transactionId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/backup", { transactionId: requireTransactionId(args) });
    }
  };
}
function makeGetTransactionSnapshotsTool(deps) {
  return {
    name: "get_transaction_snapshots",
    description: "List every ai-snapshot that currently survives for a transaction, oldest first: each entry's external-storage `path` and the 14-digit `timestamp` (yyyyMMddHHmmss) baked into its filename at creation time. This is the read-only counterpart to `backup` \u2014 the only way to confirm, after enough churn, whether a transaction `backup` protected (or that apply_changes snapshotted automatically) actually still has a surviving snapshot, since neither `get_changeset`'s `backupRef` nor any other audit record can answer that (see `backup`'s own description). An empty array is a normal answer, not an error \u2014 it means no snapshot for this transaction currently survives (or none was ever taken), not that the transaction id itself is unrecognized. Note ai.snapshot.count's eviction is a global, timestamp-only trim across EVERY transaction's snapshots combined, not scoped per transaction \u2014 a snapshot returned here is not thereby guaranteed to survive future churn from any transaction, including a different one. NOT enterprise-gated: works on a community-only deployment too, the same tier as `backup`/`backup_storage`.",
    inputSchema: {
      type: "object",
      properties: {
        transactionId: {
          type: "string",
          description: "the transaction to check; the same id passed to `backup`/apply_changes"
        }
      },
      required: ["transactionId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireTransactionId(args);
      return deps.wizClient.get(`/v1/admin/backup/${encodeURIComponent(id)}`);
    }
  };
}
function makeBackupStorageTool(deps) {
  return {
    name: "backup_storage",
    description: "Take a full, human-requested storage backup and return its external-storage path \u2014 the same underlying export the Enterprise Manager \"Backup Now\" button uses, just triggered through chat. Unlike `backup` (which snapshots a specific admin-chat TRANSACTION into the ai-snapshots retention pool), this lands in the SAME backup/ folder EM's own \"Backup Now\" action uses, pruned by asset.backup.count \u2014 so a deliberately-requested export here is never silently evicted by ai.snapshot.count's much smaller retention window, and never competes with a pending transaction's own safety snapshot for that pool's slots. Read-only from the product's own data's point of view: it mutates nothing observable to a user, it only adds a file to external storage, so there is no planHash/reviewOutcome/preview step, matching `backup`'s own no-signoff precedent. `task` is required and is written verbatim into the backup's stored path segment, so it is refused loud if it contains a path separator or a \"..\" segment \u2014 pass a plain description, not a path. UNLIKE every other tool in this plugin's admin-chat surface (all of which are enterprise-gated), this tool is NOT enterprise-gated: it wraps a community-tier service and works on a community-only deployment too.",
    inputSchema: {
      type: "object",
      properties: {
        task: {
          type: "string",
          description: `a short, plain description of why this backup was taken; written into the audit record and into the backup's own stored path segment \u2014 no "/", "\\\\", or ".."`
        }
      },
      required: ["task"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const obj = typeof args === "object" && args !== null ? args : {};
      return deps.wizClient.post("/v1/admin/file/backup", { task: requireBackupTask(obj.task) });
    }
  };
}
function makeAuditTools(deps) {
  return [
    makeListChangesetsTool(deps),
    makeGetChangesetTool(deps),
    makeBackupTool(deps),
    makeGetTransactionSnapshotsTool(deps),
    makeBackupStorageTool(deps)
  ];
}

// src/tools/permissionTools.ts
function requireResourcePath(raw, label = "resourcePath") {
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(`${label}: required`);
  }
  return raw.trim();
}
var CHANGES_SCHEMA3 = {
  type: "array",
  description: "the proposed permission-grant changes; each entry creates, updates, or deletes one grant (one identity's actions on one resource). List each (resourceType, resourcePath, identityType, identityId) combination at most once.",
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"create", "update", or "delete" (also accepts "add"/"set"|"modify"/"remove")'
      },
      resourceType: {
        type: "string",
        description: `one of ASSET, REPORT, DASHBOARD, DATA_SOURCE, DATA_SOURCE_FOLDER, QUERY, QUERY_FOLDER, DATA_MODEL_FOLDER, SCRIPT, SCRIPT_LIBRARY, TABLE_STYLE, TABLE_STYLE_LIBRARY, CHART_TYPE, CHART_TYPE_FOLDER, CUBE, PROTOTYPE, LIBRARY (repository-asset-shaped types), or one of VIEWSHEET_ACTION, SCHEDULE_OPTION, AI_ASSISTANT, CROSS_JOIN, CREATE_DATA_SOURCE, VIEWSHEET_CALCULATED_FIELD, WORKSHEET_EXPRESSION_COLUMN, FREE_FORM_SQL, MATERIALIZATION, MY_DASHBOARDS, PHYSICAL_TABLE, PORTAL_REPOSITORY_TREE_DRAG_AND_DROP, PROFILE, UPLOAD_DRIVERS, DEVICE, VIEWSHEET, WORKSHEET, VIEWSHEET_TOOLBAR_ACTION, SHARE, SCHEDULER, PORTAL_TAB (capability-level "Security Actions" types \u2014 the resourcePath for these is not a repository path; use list_grantable_permission_actions to discover the exact (resourceType, resourcePath) pairs). SCHEDULE_TASK and identity-/console-privilege-adjacent types are refused \u2014 see this tool's own description.`
      },
      resourcePath: {
        type: "string",
        description: "the resource's path, exactly as it appears in Enterprise Manager or the repository. NOT validated to exist \u2014 confirm the resource exists through another area first."
      },
      identityType: { type: "string", description: "USER, GROUP, ROLE, or ORGANIZATION" },
      identityId: {
        type: "string",
        description: `a bare identity name (defaults to the caller's own organization), or a "name:orgId"-shaped string \u2014 an explicit org other than the caller's own is refused.`
      },
      actions: {
        type: "array",
        items: { type: "string" },
        description: "required for verb=create/update (also accepts verb=add/set/modify): a non-empty array of READ, WRITE, DELETE, ACCESS, SHARE, ASSIGN, ADMIN. Not used for verb=delete/remove \u2014 refused if present, not silently dropped."
      }
    },
    required: ["verb", "resourceType", "resourcePath", "identityType", "identityId"]
  }
};
var TASK_SCHEMA3 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
async function getResourcePermission(deps, resourceType, resourcePath) {
  const query = `resourceType=${encodeURIComponent(resourceType)}&resourcePath=${encodeURIComponent(resourcePath)}`;
  return deps.wizClient.get(`/v1/admin/permissions?${query}`);
}
function makeListPermissionGrantsTool(deps) {
  return {
    name: "list_permission_grants",
    description: "List every permission grant on one resource (all identity types, all actions), one call. Resource existence is NOT validated by the server \u2014 confirm the resource exists through another area (repository browsing, composer, EM) before granting permissions on it. SCHEDULE_TASK and identity-/console-privilege-adjacent resource types are refused \u2014 see this tool's resourceType argument description. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: {
        resourceType: CHANGES_SCHEMA3.items.properties.resourceType,
        resourcePath: CHANGES_SCHEMA3.items.properties.resourcePath
      },
      required: ["resourceType", "resourcePath"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const resourceType = requireAllowedResourceType(args?.resourceType, "arguments");
      const resourcePath = requireResourcePath(args?.resourcePath);
      return getResourcePermission(deps, resourceType, resourcePath);
    }
  };
}
function makeListGrantablePermissionActionsTool(deps) {
  return {
    name: "list_grantable_permission_actions",
    description: 'Discover the (label, resourceType, resourcePath, actions, category) leaves of the capability-level "Security Actions" resource types this area now administers (bug 76600) \u2014 e.g. Bookmark\'s "Open Bookmark" leaf is { resourceType: "VIEWSHEET_ACTION", resourcePath: "OpenBookmark" }. These resourcePaths are NOT guessable the way a repository asset path is \u2014 call this tool first, then pass the exact resourceType/resourcePath pair it returns into preview_permission_changes/list_permission_grants/get_permission_grant. Only returns leaves whose resourceType is in this area\'s allowlist \u2014 an EM/EM_COMPONENT/SCHEDULE_TASK/LOGIN_AS leaf (also present in the underlying Enterprise Manager tree) is never returned here, since every permission tool in this area would refuse it anyway. `category` is the nearest ancestor grouping label (e.g. "Bookmark", "Chart Types", "Schedule Options") when one exists. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.',
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/permissions/actions");
    }
  };
}
function makeGetPermissionGrantTool(deps) {
  return {
    name: "get_permission_grant",
    description: "Read one identity's grant on one resource. Returns { found: false, ... } when no grant exists for that identity \u2014 this is a NORMAL answer, not an error; most identity/resource pairs have no explicit grant. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: {
        resourceType: CHANGES_SCHEMA3.items.properties.resourceType,
        resourcePath: CHANGES_SCHEMA3.items.properties.resourcePath,
        identityType: CHANGES_SCHEMA3.items.properties.identityType,
        identityId: CHANGES_SCHEMA3.items.properties.identityId
      },
      required: ["resourceType", "resourcePath", "identityType", "identityId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const resourceType = requireAllowedResourceType(args?.resourceType, "arguments");
      const resourcePath = requireResourcePath(args?.resourcePath);
      const identityType = normalizeIdentityType(args?.identityType, "arguments");
      const identityId = requireIdentityId(args?.identityId, "arguments");
      const query = `resourceType=${encodeURIComponent(resourceType)}&resourcePath=${encodeURIComponent(resourcePath)}&identityType=${encodeURIComponent(identityType)}&identityId=${encodeURIComponent(identityId)}`;
      return deps.wizClient.get(`/v1/admin/permissions/grant?${query}`);
    }
  };
}
function makePreviewPermissionChangesTool(deps) {
  return {
    name: "preview_permission_changes",
    description: "Resolve a set of proposed permission-grant creates/updates/deletes into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true); requiresStorageBackup is always false \u2014 a permission grant is a single reversible key-value entry, unlike a schedule task. You MUST pass the returned planHash to apply_permission_changes: the server re-resolves the plan there and refuses with a conflict if anything drifted, including a concurrent, unrelated grant change on the SAME resource (the hash covers every grant on the resource, not just the one you're touching, because the underlying write replaces the whole resource's grant set). A preview can itself be refused if the change would remove YOUR OWN ADMIN grant on the resource \u2014 the server treats that as a plan whose own undo you could not perform, the same reasoning it applies to a schedule-task delete you couldn't roll back. Returns a planHash AND a taskToken; you MUST pass both to apply_permission_changes \u2014 the server writes the audit record from the task narrative embedded in taskToken, the one reviewed here, never from whatever task text apply_permission_changes itself is called with. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA3, changes: CHANGES_SCHEMA3 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/permissions/preview", {
        task: requireTask(args?.task),
        changes: normalizePermissionChanges(args?.changes)
      });
    }
  };
}
function makeApplyPermissionChangesTool(deps) {
  return {
    name: "apply_permission_changes",
    description: `Apply a previewed permission-grant plan. Pass back the SAME task and changes you previewed plus the planHash AND the taskToken from preview_permission_changes \u2014 the request body IS the plan; the server never trusts a stored one. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_permission_changes. Every plan in this area requires a non-blank reviewOutcome because every verb is always high risk. Result status is one of: "applied" (every change verified), "rolled-back" (something failed and every applied change was undone), or "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate to the operator, do not retry). A returned status of "conflict" means the plan drifted and nothing was applied. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA3,
        changes: CHANGES_SCHEMA3,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_permission_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_permission_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_permission_changes for this plan. Call preview_permission_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_permission_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_permission_changes first."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizePermissionChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/permissions/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_permission_changes and apply_permission_changes \u2014 a grant on one of the SAME resources was created, changed, or removed by someone else in between (the hash covers every grant on each touched resource, not just the ones this plan names). NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_permission_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function normalizeCopyTargets(raw) {
  let entries;
  if (Array.isArray(raw)) {
    if (raw.length === 0) {
      throw new Error("targets: must not be empty \u2014 pass at least one { resourceType, resourcePath }");
    }
    entries = raw;
  } else if (typeof raw === "object" && raw !== null) {
    entries = [raw];
  } else {
    throw new Error(
      "targets: required \u2014 an array of { resourceType, resourcePath } (a single { resourceType, resourcePath } object is accepted as a one-element-array alias)"
    );
  }
  return entries.map((entry, i) => {
    const label = `targets[${i}]`;
    if (typeof entry !== "object" || entry === null || Array.isArray(entry)) {
      throw new Error(`${label}: expected an object { resourceType, resourcePath }, got ${typeof entry}`);
    }
    const e = entry;
    return {
      resourceType: requireAllowedResourceType(e.resourceType, label),
      resourcePath: requireResourcePath(e.resourcePath, `${label}.resourcePath`)
    };
  });
}
async function readCurrentGrants(deps, resourceType, resourcePath, label) {
  let response;
  try {
    response = await getResourcePermission(deps, resourceType, resourcePath);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(
      `${label}: failed to read current grants for (${resourceType}, "${resourcePath}"): ${message}`
    );
  }
  return response?.permissionGrants ?? [];
}
function grantKey(type, name, orgId) {
  return `${type}::${name}::${orgId ?? ""}`;
}
function formatIdentityId(name, orgId) {
  return orgId ? `${name}:${orgId}` : name;
}
function actionSetsEqual(a, b) {
  const setA = new Set(a.map((x) => x.toUpperCase()));
  const setB = new Set(b.map((x) => x.toUpperCase()));
  if (setA.size !== setB.size) {
    return false;
  }
  for (const x of setA) {
    if (!setB.has(x)) {
      return false;
    }
  }
  return true;
}
function diffGrantsForTarget(sourceGrants, targetGrants, targetResourceType, targetResourcePath) {
  const sourceByKey = /* @__PURE__ */ new Map();
  for (const g of sourceGrants) {
    sourceByKey.set(grantKey(g.type, g.identityID.name, g.identityID.orgID), g);
  }
  const targetByKey = /* @__PURE__ */ new Map();
  for (const g of targetGrants) {
    targetByKey.set(grantKey(g.type, g.identityID.name, g.identityID.orgID), g);
  }
  const changes = [];
  for (const [key, sourceGrant] of sourceByKey) {
    const targetGrant = targetByKey.get(key);
    const identityId = formatIdentityId(sourceGrant.identityID.name, sourceGrant.identityID.orgID);
    if (!targetGrant) {
      changes.push({
        verb: "create",
        resourceType: targetResourceType,
        resourcePath: targetResourcePath,
        identityType: sourceGrant.type,
        identityId,
        actions: [...sourceGrant.actions]
      });
    } else if (!actionSetsEqual(sourceGrant.actions, targetGrant.actions)) {
      changes.push({
        verb: "update",
        resourceType: targetResourceType,
        resourcePath: targetResourcePath,
        identityType: sourceGrant.type,
        identityId,
        actions: [...sourceGrant.actions]
      });
    }
  }
  for (const [key, targetGrant] of targetByKey) {
    if (!sourceByKey.has(key)) {
      changes.push({
        verb: "delete",
        resourceType: targetResourceType,
        resourcePath: targetResourcePath,
        identityType: targetGrant.type,
        identityId: formatIdentityId(targetGrant.identityID.name, targetGrant.identityID.orgID)
        // no `actions` field - C4: a genuine delete entry, not a placeholder-actions workaround.
      });
    }
  }
  return changes;
}
function makeCopyPermissionGrantsTool(deps) {
  return {
    name: "preview_copy_permission_grants",
    description: "REPLACE a target resource's ENTIRE permission-grant set with a copy of a source resource's \u2014 this is NOT a merge/overlay. Any grant currently on a target for an identity NOT present in the source's grant set is DELETED as part of the plan, exactly matching what Enterprise Manager's own Copy Permissions/Paste Permissions action pair does via its bulk-replace Save. Reads the source's and every target's CURRENT grants (the same read list_permission_grants performs, one call per resource), computes the minimal create/update/delete entries needed to make each target match the source exactly \u2014 a grant already identical on both sides produces NO entry, it is left untouched \u2014 and resolves the result through the exact same call preview_permission_changes uses (/v1/admin/permissions/preview); apply the returned plan with the unmodified apply_permission_changes, exactly like any other permission plan. Accepts multiple targets in one call, producing ONE plan covering all of them, not one plan per target. A source with zero grants is a legal 'clear every target' copy, not an error. If every target's grants already exactly match the source, no server call is made and the result says so (noChangesNeeded: true) rather than submitting an empty plan. Naming the source itself as one of the targets is refused loud \u2014 copying a resource onto itself is meaningless under REPLACE semantics. The self-lockout guard already in preview_permission_changes applies unchanged and with no special-casing: replacing a target's grants is refused if doing so would remove YOUR OWN ADMIN grant there. Resource existence is NOT validated for the source or any target \u2014 confirm each one exists through another area first. SCHEDULE_TASK and identity-/console-privilege-adjacent resource types are refused for source and every target, the same allowlist every other tool in this area uses. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA3,
        sourceResourceType: CHANGES_SCHEMA3.items.properties.resourceType,
        sourceResourcePath: CHANGES_SCHEMA3.items.properties.resourcePath,
        targets: {
          type: "array",
          description: "one or more { resourceType, resourcePath } targets whose ENTIRE grant set will be REPLACED to match the source's exactly. A single { resourceType, resourcePath } object is accepted as a one-element-array alias. Every target is resolved into ONE combined plan \u2014 call this tool once for all targets, not once per target.",
          items: {
            type: "object",
            properties: {
              resourceType: CHANGES_SCHEMA3.items.properties.resourceType,
              resourcePath: CHANGES_SCHEMA3.items.properties.resourcePath
            },
            required: ["resourceType", "resourcePath"]
          }
        }
      },
      required: ["task", "sourceResourceType", "sourceResourcePath", "targets"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const sourceResourceType = requireAllowedResourceType(args?.sourceResourceType, "arguments.source");
      const sourceResourcePath = requireResourcePath(args?.sourceResourcePath, "sourceResourcePath");
      const targets = normalizeCopyTargets(args?.targets);
      targets.forEach((t, i) => {
        if (t.resourceType === sourceResourceType && t.resourcePath === sourceResourcePath) {
          throw new Error(
            `targets[${i}]: names the same resource as the source (${sourceResourceType} "${sourceResourcePath}") \u2014 copying a resource onto itself is refused`
          );
        }
      });
      for (let i = 0; i < targets.length; i++) {
        for (let j = i + 1; j < targets.length; j++) {
          if (targets[i].resourceType === targets[j].resourceType && targets[i].resourcePath === targets[j].resourcePath) {
            throw new Error(
              `targets[${i}] and targets[${j}]: name the same resource (${targets[i].resourceType} "${targets[i].resourcePath}") \u2014 duplicate targets are refused`
            );
          }
        }
      }
      const sourceGrants = await readCurrentGrants(deps, sourceResourceType, sourceResourcePath, "source");
      const changes = [];
      for (let i = 0; i < targets.length; i++) {
        const target = targets[i];
        const targetGrants = await readCurrentGrants(
          deps,
          target.resourceType,
          target.resourcePath,
          `targets[${i}]`
        );
        changes.push(...diffGrantsForTarget(sourceGrants, targetGrants, target.resourceType, target.resourcePath));
      }
      if (changes.length === 0) {
        return {
          noChangesNeeded: true,
          changes: [],
          summary: "every target's grant set already matches the source exactly \u2014 nothing to change"
        };
      }
      return deps.wizClient.post("/v1/admin/permissions/preview", {
        task,
        changes: normalizePermissionChanges(changes)
      });
    }
  };
}
function makePermissionTools(deps) {
  return [
    makeListPermissionGrantsTool(deps),
    makeGetPermissionGrantTool(deps),
    makeListGrantablePermissionActionsTool(deps),
    makePreviewPermissionChangesTool(deps),
    makeApplyPermissionChangesTool(deps),
    makeCopyPermissionGrantsTool(deps)
  ];
}

// src/tools/identityTools.ts
var CHANGES_SCHEMA4 = {
  type: "array",
  description: 'the proposed identity creates/deletes/updates; each entry names a unitType FIRST \u2014 set it correctly before building spec, since a field that belongs to a different unit type (e.g. parentGroups on unitType="user") is refused loud, not silently dropped. List each (unitType, id) at most once.',
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"create", "delete", or "update" ("create"/"delete" also accept "add"/"remove"; "update" has no alias)'
      },
      unitType: {
        type: "string",
        description: '"user", "group", "role", or "organization"'
      },
      id: {
        type: "string",
        description: `required for verb=delete/update, forbidden for verb=create (the id is derived from spec). For verb=update, id targets the identity by its CURRENT name/id \u2014 never the new name (see spec.name below for renaming). For user/group/role: a bare name (defaults to the caller's own organization) or a "name:orgId"-shaped string. For organization: its bare id (NOT its display name \u2014 use list_identity_organizations to resolve one from the other).`
      },
      spec: {
        type: "object",
        description: "required for verb=create/update, forbidden for verb=delete. Shape depends on unitType: user={name,orgId?,password,alias?,locale?,active?,emails?,groups?,roles?}; group={name,orgId?,parentGroups?,memberUsers?,memberGroups?,roles?}; role={name,orgId?,description?,inheritedRoles?,assignedUsers?,assignedGroups?}; organization={id,orgName,locale?}. `groups`/`memberUsers`/`memberGroups`/`assignedUsers`/`assignedGroups` reference EXISTING identities only \u2014 the server filters out any the caller cannot administer rather than failing, so check the preview's warnings, if any, before applying. `theme` is accepted on every unitType. `adminIdentities` is not supported by this tool on any unitType \u2014 grant admin rights over an identity separately after creating it. For verb=update, spec is PARTIAL: an omitted field leaves the identity's current value unchanged, an explicit \"\"/[] clears it, and an explicit null is refused loud (it cannot be distinguished from omitted once sent, so it's rejected rather than silently doing nothing). user/group/role may be renamed via spec.name (the top-level id above still targets the CURRENT name). Three fields are refused loud on update regardless of value: spec.password (user \u2014 password rotation is out of scope for update), spec.orgId (user/group/role \u2014 an identity's organization is fixed by the id you're targeting, not settable via spec), and spec.id (organization \u2014 its own id is fixed by the id argument; only its display name, spec.orgName, is renameable)."
      }
    },
    required: ["verb", "unitType"]
  }
};
var TASK_SCHEMA4 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListUsersTool(deps) {
  return {
    name: "list_identity_users",
    description: "List security users, optionally filtered by organization id. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: {
        orgId: { type: "string", description: "optional; omit to list every organization's users" }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const query = typeof args?.orgId === "string" && args.orgId.trim() !== "" ? `?orgId=${encodeURIComponent(args.orgId.trim())}` : "";
      return deps.wizClient.get(`/v1/admin/identities/users${query}`);
    }
  };
}
function makeGetUserTool(deps) {
  return {
    name: "get_identity_user",
    description: `Read one user by id (a bare name, defaulting to the caller's own organization, or "name:orgId"). Returns a 404-shaped error naming the id if not found or not permitted \u2014 the server cannot distinguish "doesn't exist" from "you lack permission" at this layer, so the error is worded that way deliberately, not vaguely. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.`,
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "id, name, or identityId (all accepted)" } },
      required: ["id"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireIdentityId(args?.id, "arguments");
      return deps.wizClient.get(`/v1/admin/identities/users/${encodeURIComponent(id)}`);
    }
  };
}
function makeListGroupsTool(deps) {
  return {
    name: "list_identity_groups",
    description: "List security groups, optionally filtered by organization id. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: {
        orgId: { type: "string", description: "optional; omit to list every organization's groups" }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const query = typeof args?.orgId === "string" && args.orgId.trim() !== "" ? `?orgId=${encodeURIComponent(args.orgId.trim())}` : "";
      return deps.wizClient.get(`/v1/admin/identities/groups${query}`);
    }
  };
}
function makeGetGroupTool(deps) {
  return {
    name: "get_identity_group",
    description: "Read one group by id. Returns a 404-shaped error naming the id if not found or not permitted (same caveat as get_identity_user). Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "id, name, or identityId (all accepted)" } },
      required: ["id"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireIdentityId(args?.id, "arguments");
      return deps.wizClient.get(`/v1/admin/identities/groups/${encodeURIComponent(id)}`);
    }
  };
}
function makeListRolesTool(deps) {
  return {
    name: "list_identity_roles",
    description: "List security roles, optionally filtered by organization id. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: {
        orgId: { type: "string", description: "optional; omit to list every organization's roles" }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const query = typeof args?.orgId === "string" && args.orgId.trim() !== "" ? `?orgId=${encodeURIComponent(args.orgId.trim())}` : "";
      return deps.wizClient.get(`/v1/admin/identities/roles${query}`);
    }
  };
}
function makeGetRoleTool(deps) {
  return {
    name: "get_identity_role",
    description: "Read one role by id. Returns a 404-shaped error naming the id if not found or not permitted (same caveat as get_identity_user). Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "id, name, or identityId (all accepted)" } },
      required: ["id"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireIdentityId(args?.id, "arguments");
      return deps.wizClient.get(`/v1/admin/identities/roles/${encodeURIComponent(id)}`);
    }
  };
}
function makeListOrganizationsTool(deps) {
  return {
    name: "list_identity_organizations",
    description: "List every organization. Each entry carries both its id and its display name \u2014 use this to resolve one from the other before calling get_identity_organization, whose `id` argument is the id field, not the display name. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/identities/organizations");
    }
  };
}
function makeGetOrganizationTool(deps) {
  return {
    name: "get_identity_organization",
    description: "Read one organization by ID (NOT its display name \u2014 an organization's id and name are distinct fields; use list_identity_organizations to resolve one from the other). Returns a 404-shaped error naming the id if not found or not permitted. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "the organization's id field" } },
      required: ["id"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.id !== "string" || args.id.trim() === "") {
        throw new Error("id: required (the organization's id field, not its display name)");
      }
      return deps.wizClient.get(`/v1/admin/identities/organizations/${encodeURIComponent(args.id.trim())}`);
    }
  };
}
function makePreviewIdentityChangesTool(deps) {
  return {
    name: "preview_identity_changes",
    description: "Resolve a set of proposed identity creates/deletes/updates (any mix of user/group/role/organization) into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true) and always requires a Tier-2 storage backup (requiresStorageBackup is always true) \u2014 even a create, because it can mutate a second record as a side effect (adding the new identity to its organization's member list, or reassigning existing members to a new group/role). You MUST pass the returned planHash to apply_identity_changes. IMPORTANT things to know before previewing a delete: (1) deleting a user has only a PARTIAL undo \u2014 a rolled-back delete recreates the account with a freshly generated password disclosed once in the apply result, not the original password, which the server never exposes; (2) deleting an organization has NO undo at all \u2014 it is declared non-compensable, gated behind the mandatory backup, and the default/self organization cannot be deleted through this tool at all (refused at preview time); (3) deleting a user or group may affect schedule tasks (ownership transfer or deletion) \u2014 this is surfaced as an advisory in the apply result, not blocked here, so relay it to the human as part of plan review. Returns a planHash and a taskToken. You MUST pass both planHash and taskToken to apply_identity_changes: the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken -- the one reviewed here -- never from whatever task text apply_identity_changes itself is called with. Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.",
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA4, changes: CHANGES_SCHEMA4 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/identities/preview", {
        task: requireTask(args?.task),
        changes: normalizeIdentityChanges(args?.changes)
      });
    }
  };
}
function makeApplyIdentityChangesTool(deps) {
  return {
    name: "apply_identity_changes",
    description: `Apply a previewed identity change plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_identity_changes. Every plan in this area requires a non-blank reviewOutcome. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_identity_changes. Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted \u2014 note a deleted user's rollback advisory carries a freshly generated password that appears ONLY here, one time; relay it to the human verbatim, never summarize it away), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied). An organization-delete entry's own outcome is always "applied" or "failed" \u2014 it is never individually rolled back, by design (there is no live inverse for deleting an organization). Served by the StyleBI enterprise module; a community-only deployment 404s on this endpoint.`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA4,
        changes: CHANGES_SCHEMA4,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_identity_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_identity_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_identity_changes for this plan. Call preview_identity_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_identity_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_identity_changes first."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeIdentityChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/identities/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_identity_changes and apply_identity_changes \u2014 one of the named identities was created, changed, or removed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_identity_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeIdentityTools(deps) {
  return [
    makeListUsersTool(deps),
    makeGetUserTool(deps),
    makeListGroupsTool(deps),
    makeGetGroupTool(deps),
    makeListRolesTool(deps),
    makeGetRoleTool(deps),
    makeListOrganizationsTool(deps),
    makeGetOrganizationTool(deps),
    makePreviewIdentityChangesTool(deps),
    makeApplyIdentityChangesTool(deps)
  ];
}

// src/tools/providerTools.ts
var CHANGES_SCHEMA5 = {
  type: "array",
  description: `the proposed provider creates/deletes/duplicates/updates; each entry names a chain FIRST ("authentication" or "authorization", no abbreviations \u2014 "auth" is refused loud, not guessed, since it is ambiguous between the two). Provider chains are DEPLOYMENT-WIDE, not org-scoped \u2014 there is no orgId argument here, and the resolved plan's orgId field will be null: that is not a bug or a missing value, it means the change applies to the whole deployment, not any single organization. List each (chain, name) at most once.`,
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: `"create", "delete", "duplicate", or "update" (also accepts "add"/"remove" as aliases for create/delete, "copy"/"clone" as aliases for duplicate, and "modify"/"edit" as aliases for update). "update" changes one or more fields on an EXISTING provider IN PLACE, at its current chain position \u2014 unlike delete+create, it never moves the provider to the end of the chain (see spec below and apply_provider_changes' own rollback note).`
      },
      chain: {
        type: "string",
        description: '"authentication" or "authorization", spelled in full \u2014 no abbreviations accepted'
      },
      name: {
        type: "string",
        description: "the provider's own name (unique within its chain; an authentication provider and an authorization provider MAY share a name, they are different lists). `providerName` is an accepted alias. Required for create/delete/duplicate/update \u2014 for duplicate, this is the SOURCE being copied, not the copy (see newName); for update, this is the provider being edited."
      },
      providerType: {
        type: "string",
        description: `required for verb=create, forbidden for verb=delete/duplicate/update (a duplicate always keeps the source provider's own type, matching the real EM "Duplicate" action; an update cannot change a provider's type at all \u2014 delete and create a new provider instead if the type itself needs to change). "FILE" or "LDAP" \u2014 chain="authorization" accepts only "FILE" (LDAP is authentication-chain-only). "DATABASE"/"CUSTOM" are refused for either chain in this area's first cut (license-gating gap and unbounded class-loading/secrets surface, respectively) for verb=create; a DATABASE/CUSTOM provider CAN still be duplicated \u2014 its apply step re-instantiates the source's class (CUSTOM) or re-runs a live connection test (DATABASE), the same risk create's exclusion guards against, but only an Enterprise-licensed deployment can already have such a provider to duplicate, and the server refuses the attempt loud, before any instantiation, on any other deployment.`
      },
      spec: {
        type: "object",
        description: `for verb=create: required for providerType="LDAP", forbidden (or must be empty) for providerType="FILE" (a FILE provider has no configuration beyond its name). Forbidden for verb=delete/duplicate. For verb=update: required, and PARTIAL \u2014 an object with at least one LDAP field to change; this is NOT a full provider passthrough, any field you omit is preserved UNCHANGED from the current provider (the server merges your partial spec onto the provider's live, currently-stored configuration). LDAP shape (same field set for both create and update): {ldapServer:"ACTIVE_DIRECTORY"|"GENERIC", protocol, hostName, hostPort, rootDN, useCredential?, adminID?, password?, secretId?, userFilter?, userBase?, userAttr?, mailAttr?, groupFilter?, groupBase?, groupAttr?, roleFilter?, roleBase?, roleAttr?, userRoleFilter?, roleRoleFilter?, groupRoleFilter?, startTls?, searchTree?, sysAdminRoles?}. useCredential:true REQUIRES secretId and forbids adminID/password; useCredential:false (the default) REQUIRES BOTH adminID and password and forbids secretId \u2014 sending the wrong combination for the resolved mode is a field-named error, never a silent drop of whichever field doesn't apply. For verb=update specifically, this credential-mode check is only enforced fully once merged with the CURRENT provider's stored mode (server-side, since this plugin layer has no live view of what mode the provider is currently in) \u2014 but sending secretId together with adminID/password in the SAME update is refused loud client-side too, since that combination can never be valid regardless of what it merges onto.`
      },
      newName: {
        type: "string",
        description: `verb=duplicate ONLY, optional: the copy's name. When omitted, the copy is named the same way the real EM "Duplicate" button does ("Copy of <name>", "Copy of <name>1", ... on a collision) \u2014 the server computes this, never the caller. Forbidden for create/delete/update \u2014 update does not support renaming in this cut.`
      }
    },
    required: ["verb", "chain", "name"]
  }
};
var TASK_SCHEMA5 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListAuthProvidersTool(deps) {
  return {
    name: "list_auth_providers",
    description: "List the authentication chain's providers, in chain order. Order is MEANINGFUL, not incidental \u2014 it is the resolution order: for a role defined by more than one provider, the first provider in this list that defines it decides whether that role is treated as system-administrator for every caller who holds it, including this admin-chat session's own caller. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/providers/authentication");
    }
  };
}
function makeListAuthzProvidersTool(deps) {
  return {
    name: "list_authz_providers",
    description: "List the authorization chain's providers, in chain order. Order is MEANINGFUL, not incidental, the same resolution-order caveat as list_auth_providers \u2014 this chain governs checkPermission resolution for every non-admin-chat caller in the product. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/providers/authorization");
    }
  };
}
async function requireLiveProviderName(deps, chain, name) {
  const list = await deps.wizClient.get(`/v1/admin/providers/${chain}`);
  const providers = list && typeof list === "object" ? list.providers : void 0;
  const known = Array.isArray(providers) ? providers.map((p) => p && typeof p === "object" ? p.name : void 0).filter((n) => typeof n === "string") : [];
  if (!known.includes(name)) {
    const listTool = chain === "authentication" ? "list_auth_providers" : "list_authz_providers";
    throw new Error(
      `name: no ${chain} provider named "${name}" (checked against ${listTool}'s live provider list)`
    );
  }
}
function makeGetAuthProviderTool(deps) {
  return {
    name: "get_auth_provider",
    description: "Read one authentication-chain provider by name. `name`/`providerName` accepted as aliases, trimmed, required. Returns a structured 404-shaped error naming the id on a miss, resolved by checking a fresh provider list before ever reaching the per-provider endpoint (bug 76611: this tool's own call previously had no such check at all and let the raw per-provider HTTP error through unstructured \u2014 the underlying endpoint DOES already guard against an unresolved name itself, refuting an earlier claim here that it could throw an unrelated NullPointerException, but its own error is still a raw, unstructured HTTP error at this layer, not this tool's own field-named shape). `password` is never the real value, always a fixed placeholder string. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: { name: { type: "string", description: "name or providerName (both accepted)" } },
      required: ["name"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = requireProviderName(args);
      await requireLiveProviderName(deps, "authentication", name);
      return deps.wizClient.get(`/v1/admin/providers/authentication/${encodeURIComponent(name)}`);
    }
  };
}
function makeGetAuthzProviderTool(deps) {
  return {
    name: "get_authz_provider",
    description: "Read one authorization-chain provider by name. `name`/`providerName` accepted as aliases, trimmed, required. Returns a structured 404-shaped error naming the id on a miss, resolved by checking a fresh provider list before ever reaching the per-provider endpoint (bug 76611, the same missing-precheck gap get_auth_provider had, found in this sibling tool during that fix). Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: { name: { type: "string", description: "name or providerName (both accepted)" } },
      required: ["name"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = requireProviderName(args);
      await requireLiveProviderName(deps, "authorization", name);
      return deps.wizClient.get(`/v1/admin/providers/authorization/${encodeURIComponent(name)}`);
    }
  };
}
function makeTestAuthProviderConnectionTool(deps) {
  return {
    name: "test_auth_provider_connection",
    description: 'Test an EXISTING authentication-chain provider\'s live connection, using its OWN currently-stored configuration. Authentication chain ONLY \u2014 there is no authorization-chain equivalent in the underlying product; passing chain="authorization" is refused loud, not silently ignored. `name`/`providerName` accepted as aliases, trimmed, required. Deliberately takes only a name, never a spec/model: the server resolves and tests the REAL, currently-saved credential itself, so a masked placeholder password (the fixed string get_auth_provider always returns for `password`) can never be sent here, let alone tested literally and misreported as a bind failure. Returns `{status, connected}` from the server \u2014 `status` is a human-readable message (e.g. "Connection is OK."), not reliably parseable across locales; `connected` mirrors the underlying EM endpoint\'s own field, which that endpoint does not actually populate on success, so treat `status` as authoritative and `connected` as informational only. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.',
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string", description: "name or providerName (both accepted)" },
        chain: {
          type: "string",
          description: 'must be "authentication" if given at all (the default) \u2014 there is no authorization-chain equivalent for Test Connection'
        }
      },
      required: ["name"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = requireProviderName(args);
      requireAuthenticationChainIfGiven(args?.chain, "test_auth_provider_connection");
      await requireLiveProviderName(deps, "authentication", name);
      return deps.wizClient.get(
        `/v1/admin/providers/authentication/${encodeURIComponent(name)}/test-connection`
      );
    }
  };
}
function makeListAuthProviderDirectoryEntriesTool(deps) {
  return {
    name: "list_auth_provider_directory_entries",
    description: 'List one authentication-chain provider\'s LIVE directory entries \u2014 users, groups, or roles \u2014 queried directly from the provider\'s own directory (e.g. a real LDAP bind) right now, NOT StyleBI\'s already-resolved identity store: `list_identity_users`/`list_identity_groups`/`list_identity_roles` have no provider-scoped view at all and cannot answer "what would this LDAP config\'s directory return." Authentication chain ONLY \u2014 there is no authorization-chain equivalent; passing chain="authorization" is refused loud. `name`/`providerName` accepted as aliases, trimmed, required. `kind` selects which entries: "users", "groups", or "roles" (singular forms accepted as aliases). Same placeholder-password safety as test_auth_provider_connection: only a name is accepted, the server resolves the real stored credential itself. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.',
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string", description: "name or providerName (both accepted)" },
        kind: { type: "string", description: '"users", "groups", or "roles"' },
        chain: {
          type: "string",
          description: 'must be "authentication" if given at all (the default) \u2014 there is no authorization-chain equivalent for this tool'
        }
      },
      required: ["name", "kind"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = requireProviderName(args);
      requireAuthenticationChainIfGiven(args?.chain, "list_auth_provider_directory_entries");
      const kind = normalizeDirectoryKind(args?.kind);
      await requireLiveProviderName(deps, "authentication", name);
      return deps.wizClient.get(
        `/v1/admin/providers/authentication/${encodeURIComponent(name)}/directory/${kind}`
      );
    }
  };
}
function makeClearProviderCacheTool(deps) {
  return {
    name: "clear_provider_cache",
    description: "Clear one provider's cache, on either chain, resolved by NAME (the underlying server method is index-only; this tool resolves the index fresh, right before clearing, the same way a delete does). Bare action \u2014 no preview/apply, no planHash, no reviewOutcome, self-inverse in effect (the cache simply repopulates on next use), matching Cluster's pause/resume risk shape rather than create/delete/duplicate's. A provider whose type does not enable caching (most notably FILE) is REFUSED LOUD, naming it, rather than silently reporting success for a call that changed nothing \u2014 LDAP always has an active cache; FILE never does. `chain` is required and spelled in full, no abbreviations, same discipline as preview/apply_provider_changes' own `chain` field. `name`/`providerName` accepted as aliases. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: {
        chain: {
          type: "string",
          description: '"authentication" or "authorization", spelled in full \u2014 no abbreviations accepted'
        },
        name: { type: "string", description: "name or providerName (both accepted)" }
      },
      required: ["chain", "name"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const chain = requireProviderChain(args);
      const name = requireProviderName(args);
      await requireLiveProviderName(deps, chain, name);
      return deps.wizClient.post(
        `/v1/admin/providers/${chain}/${encodeURIComponent(name)}/clear-cache`,
        {}
      );
    }
  };
}
function makePreviewProviderChangesTool(deps) {
  return {
    name: "preview_provider_changes",
    description: "Resolve a set of proposed provider creates/deletes/duplicates/updates (either or both chains) into a reviewable plan WITHOUT changing anything on the server. `duplicate` (aliases \"copy\"/\"clone\") copies an EXISTING provider under a new, auto-generated or explicit `newName` \u2014 unlike create, it needs no `providerType`/`spec` (the copy keeps the source's own configuration verbatim) and, unlike create's own DATABASE/CUSTOM exclusion, works on ANY existing provider type \u2014 its apply step does re-instantiate a CUSTOM provider's class or re-run a live DATABASE connection test just as create would, but that is only reachable on an Enterprise-licensed deployment (the same license check create's exclusion exists for refuses it loud otherwise). `update` (aliases \"modify\"/\"edit\") changes one or more fields \u2014 an LDAP bind password, a hostname, a port \u2014 on an EXISTING provider WITHOUT the delete+create reorder problem: the provider is edited IN PLACE, at its current chain position, never moved. It takes a PARTIAL `spec`; any field you omit is preserved unchanged from the provider's current, live configuration. `update` cannot change `providerType` or rename the provider (both are refused loud) \u2014 delete+create is still the only path for those. Every verb in this area is always high risk (requiresAgentSignoff is always true) and always requires a Tier-2 storage backup (requiresStorageBackup is always true). Returns each provider's status plus requiresStorageBackup, requiresAgentSignoff, a planHash, and a taskToken. You MUST pass both planHash and taskToken to apply_provider_changes: the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken -- the one reviewed here -- never from whatever task text apply_provider_changes itself is called with. READ THIS BEFORE PREVIEWING A DELETE OR AN UPDATE ON THE AUTHENTICATION CHAIN: deleting, OR EDITING, the provider that currently recognizes THIS CALLING SESSION's own role as system-administrator is a HARD, UNRECOVERABLE-THROUGH-ADMIN-CHAT LOCKOUT \u2014 every admin-chat tool in every area is gated by the identical mechanism a wrong delete OR a wrong update can break, so either one locks out not just this area but the entire plugin, with no remedy short of direct EM/database access. The server runs the same two independent preflight checks before accepting either kind of plan (a deployment-wide check and a caller-specific check, simulating the post-change chain for delete's removal or update's field edit) and refuses one that would self-lock the calling session or leave the deployment with no system-administrator authentication provider at all \u2014 do not attempt to route around a refusal, explain the risk to the user instead. UNLIKE EVERY OTHER VERB, previewing an LDAP `update` on the authentication chain performs a LIVE connection test against the PROPOSED configuration (the self-lockout preflight above has to actually construct the edited provider to check it) \u2014 `create`'s own preview only validates field shape and defers its live bind to apply time. This means a transient LDAP outage or slow network can cause preview_provider_changes itself to fail or hang for an `update` entry, even when the change is otherwise entirely valid \u2014 explain this to the user as a possible, retryable cause of a preview failure, not necessarily a sign the proposed change itself is wrong. The authorization chain has a narrower floor for delete: a delete that would leave zero authorization providers is refused (an update never changes the count of providers in a chain, so this floor does not apply to update). Provider chains are deployment-wide configuration, not org-scoped \u2014 see the changes schema for the orgId-is-null caveat. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA5, changes: CHANGES_SCHEMA5 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/providers/preview", {
        task: requireTask(args?.task),
        changes: normalizeProviderChanges(args?.changes)
      });
    }
  };
}
function makeApplyProviderChangesTool(deps) {
  return {
    name: "apply_provider_changes",
    description: `Apply a previewed provider change plan. Pass back the same changes you previewed, the planHash AND the taskToken from preview_provider_changes \u2014 the request body IS the plan; the server never trusts a stored one. It re-resolves the changes, recomputes the hash, and refuses on mismatch. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_provider_changes. Every plan in this area requires a non-blank reviewOutcome. Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied). A rolled-back authentication- or authorization-chain DELETE has only a PARTIAL undo: the provider is recreated at the END of its chain, not its original position, because the underlying create operation can only append. If order mattered for that provider's resolution (list order is meaningful, see list_auth_providers/list_authz_providers), an operator may need a follow-up reorder through Enterprise Manager directly \u2014 admin-chat has no reorder tool in this cut. This advisory is carried as a first-class structured field on that change's result, not buried in free text \u2014 relay it to the human verbatim, never summarize it away. A \`duplicate\`'s own rollback is a plain delete of the COPY only (by its actual resolved name, whether auto-generated or the requested newName) \u2014 the source provider is never touched by a duplicate's own rollback. An \`update\`'s rollback is a genuinely BETTER story than delete's or create's: since an edit replaces a provider IN PLACE at its existing chain index rather than removing/appending, a rolled-back update restores the provider's PRE-EDIT configuration at its ORIGINAL chain position \u2014 no "reorder may be needed" caveat applies to update's rollback at all. Unlike schedule tasks/permissions/identities, this area is NOT enterprise-gated \u2014 this tool works on a community-only deployment; reading back a provider changeset (get_changeset) is still enterprise-only and 404s on community.`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA5,
        changes: CHANGES_SCHEMA5,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_provider_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_provider_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_provider_changes for this plan. Call preview_provider_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_provider_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_provider_changes first. If preview_provider_changes's response contained no taskToken at all, this StyleBI deployment predates the pinned-audit contract and must be upgraded \u2014 do not retry."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeProviderChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/providers/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_provider_changes and apply_provider_changes \u2014 one of the named providers, or another provider in the same chain, was created, changed, or removed by someone else in between (any change to a chain invalidates its hash, not just the entry this plan touches). NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call preview_provider_changes again and use THAT response's planHash and taskToken \u2014 the returned `plan` here has no usable taskToken (the server never returns one in a conflict, so a stale narrative can't be replayed), so reusing anything from it will only produce another conflict."
          };
        }
        throw err;
      }
    }
  };
}
function makeProviderTools(deps) {
  return [
    makeListAuthProvidersTool(deps),
    makeListAuthzProvidersTool(deps),
    makeGetAuthProviderTool(deps),
    makeGetAuthzProviderTool(deps),
    makeTestAuthProviderConnectionTool(deps),
    makeListAuthProviderDirectoryEntriesTool(deps),
    makeClearProviderCacheTool(deps),
    makePreviewProviderChangesTool(deps),
    makeApplyProviderChangesTool(deps)
  ];
}

// src/tools/dataSourceTools.ts
function extractDataSourceEntries(listResult) {
  if (Array.isArray(listResult)) {
    return listResult;
  }
  if (typeof listResult === "object" && listResult !== null && Array.isArray(listResult.dataSources)) {
    return listResult.dataSources;
  }
  return [];
}
async function resolveDataSourceIdByName(deps, name) {
  const listResult = await deps.wizClient.get(`/v1/admin/data-sources?name=${encodeURIComponent(name)}`);
  const match = extractDataSourceEntries(listResult).find((e) => e.name === name);
  if (!match) {
    throw new Error(`name: no data source named "${name}" was found`);
  }
  return match.id;
}
async function resolveDataSourceNameById(deps, id) {
  const listResult = await deps.wizClient.get("/v1/admin/data-sources");
  const match = extractDataSourceEntries(listResult).find((e) => e.id === id);
  if (!match) {
    throw new Error(`no data source found with id "${id}"`);
  }
  return match.name;
}
async function attachResolvedNames(deps, changes) {
  return Promise.all(changes.map(async (change) => {
    if (change.verb === "create") {
      return change;
    }
    let name = change.name;
    if (!name) {
      try {
        name = await resolveDataSourceNameById(deps, change.id);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        throw new Error(`id: could not resolve data source id "${change.id}" to a name \u2014 ${message}`);
      }
    }
    const { id: _id, ...rest } = change;
    return { ...rest, name };
  }));
}
var CHANGES_SCHEMA6 = {
  type: "array",
  description: 'the proposed data source updates/deletes/folder-creates; each update/delete entry identifies a data source by id and/or name (at least one required); a create entry (verb="create") identifies a FOLDER by folderPath instead and uses no id/name/spec/force/confirmRename. List each (id or name, or folderPath) at most once. To move N data sources into the same folder in one call, send N separate verb="update" entries with a folder-qualified spec.name (e.g. "NewFolder/DS1", "NewFolder/DS2") and confirmRename:true on each \u2014 this already works today and needs no create entry; verb="create" is only for a BARE, empty folder with no data source moved into it yet.',
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"update", "delete", or "create" (also accepts "modify"/"edit" for update, "remove" for delete, "add" for create). "create" targets a bare, empty data source FOLDER only \u2014 there is still no way to create a new data source connection itself through this tool.'
      },
      id: {
        type: "string",
        description: `verb="update"/"delete" only; not used for verb="create". The data source's id, as returned by list_data_sources/get_data_source. Its underlying cache can go cold after about an hour of inactivity \u2014 prefer name for a plan you expect a human to take time reviewing before apply.`
      },
      name: {
        type: "string",
        description: `verb="update"/"delete" only; not used for verb="create". The data source's exact name \u2014 the durable identity, preferred over id here.`
      },
      folderPath: {
        type: "string",
        description: `verb="create" only, required \u2014 the full folder path to create (e.g. "A/B" creates both "A" and "A/B" if missing, the same nested-ancestor auto-creation an update's spec.name rename already does as a side effect). Not used for verb="update"/"delete".`
      },
      spec: {
        type: "object",
        description: 'required for verb="update", forbidden for verb="delete"/"create". A PARTIAL-FIELD object \u2014 include only the fields you want to change, never a full passthrough of a prior get_data_source response. Legal fields: name, url, driver, defaultDatabase, tableName, isolation, ansiJoin, requireLogin, user, password, useCredentialId, credentialID. For a tabular-type data source, this API tier can only ever change name \u2014 any other field is refused once the server resolves the type, even though this schema accepts it up front. password: never send back a masked placeholder ("******" or the ten-character Util.PLACEHOLDER_PASSWORD string) \u2014 omit the field entirely to leave the password unchanged; either literal is refused loud, not forwarded, because forwarding it would overwrite the live password with that literal string. useCredentialId:true requires credentialID and forbids password/user in cleartext form; useCredentialId:false (default) allows password/user but forbids credentialID.'
      },
      confirmRename: {
        type: "boolean",
        description: 'REQUIRED and must be true whenever spec.name is present \u2014 renaming/moving a data source can auto-create missing parent folders as a side effect of what looks like an unrelated field edit. Omit spec.name entirely if you did not intend to rename or move this data source. Not used for verb="delete"/"create".'
      },
      force: {
        type: "boolean",
        description: `verb="delete" only, default false; not used for verb="update"/"create". When dependent assets (queries/viewsheets/logical models) are found and force is not set, preview_data_source_changes itself refuses the plan, naming the dependents. Setting force:true does NOT bypass this area's own dependency check \u2014 it is deliberately re-implemented independently, because the wrapped server API's own force parameter is a confirmed no-op that deletes unconditionally regardless of its value.`
      }
    },
    required: ["verb"]
  }
};
var TASK_SCHEMA6 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListDataSourcesTool(deps) {
  return {
    name: "list_data_sources",
    description: "List data sources (JDBC or tabular connection-level entries), in the caller's resolved organization. `name` is an optional EXACT-match filter, not a substring/prefix search \u2014 try an unfiltered call first if a name guess comes back empty. Returns id, name, and type (jdbc/text/xml/tabular) per entry. This area is enterprise-only end to end; every tool in it 404s on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string", description: "optional; EXACT name match, not a substring search" }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = typeof args?.name === "string" ? args.name.trim() : "";
      const query = name === "" ? "" : `?name=${encodeURIComponent(name)}`;
      return deps.wizClient.get(`/v1/admin/data-sources${query}`);
    }
  };
}
function makeGetDataSourceTool(deps) {
  return {
    name: "get_data_source",
    description: "Read one data source by id and/or name (at least one required). If both are given and they resolve to different data sources, this is refused loud naming the mismatch \u2014 never silently preferring one over the other. password on a JDBC hit is always a fixed placeholder string, never the real value. This area is enterprise-only end to end; every tool in it 404s on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: {
        id: { type: "string", description: "the data source's id (accepted for round-tripping a prior response)" },
        name: { type: "string", description: "the data source's exact name (preferred \u2014 the durable identity)" }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const { id, name } = requireDataSourceIdOrName(args ?? {}, "arguments");
      if (!name) {
        return deps.wizClient.get(`/v1/admin/data-sources/${encodeURIComponent(id)}`);
      }
      if (!id) {
        const resolvedId = await resolveDataSourceIdByName(deps, name);
        return deps.wizClient.get(`/v1/admin/data-sources/${encodeURIComponent(resolvedId)}`);
      }
      const result = await deps.wizClient.get(`/v1/admin/data-sources/${encodeURIComponent(id)}`);
      const resultName = result?.name;
      if (typeof resultName === "string" && resultName !== name) {
        throw new Error(
          `id/name: "${id}" and "${name}" resolve to different data sources \u2014 id "${id}" is named "${resultName}", not "${name}". Pass only one of id/name, or make sure both refer to the same data source.`
        );
      }
      return result;
    }
  };
}
function makeTestConnectionTool(deps) {
  return {
    name: "test_connection",
    description: "Live JDBC connectivity probe against a PENDING (not-yet-applied) set of connection fields (bug 76599, Gap 1) \u2014 opens and closes a real JDBC connection and reports success/failure; performs no mutation of any kind. Recommended before apply_data_source_changes on any plan touching url/driver/credentials \u2014 preview_data_source_changes does NOT itself verify connectivity, it only resolves and hashes the plan. Accepts the same id/name resolution as get_data_source and the same PARTIAL spec shape preview_data_source_changes' update verb accepts: omit spec entirely to test the data source's current, already-saved connection fields, or include only the fields you're about to change to test them before committing. Only applies to jdbc-type data sources \u2014 refused loud for a tabular-type one. This area is enterprise-only end to end; every tool in it 404s on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: {
        id: { type: "string", description: "the data source's id (accepted for round-tripping a prior response)" },
        name: { type: "string", description: "the data source's exact name (preferred \u2014 the durable identity)" },
        spec: {
          type: "object",
          description: "optional PARTIAL-FIELD object \u2014 include only the fields you want to test with a value different from what's currently saved (e.g. a new url/driver/password before committing it). Same legal fields and password/credential rules as preview_data_source_changes' update verb; omitting password tests the current, real (unmasked) saved password, never a placeholder."
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const { id, name: rawName } = requireDataSourceIdOrName(args ?? {}, "arguments");
      const spec = normalizeDataSourceTestConnectionSpec(args?.spec, "arguments");
      const name = rawName ?? await resolveDataSourceNameById(deps, id);
      const body = { name };
      if (spec) {
        body.spec = spec;
      }
      return deps.wizClient.post("/v1/admin/data-sources/test-connection", body);
    }
  };
}
function makePreviewDataSourceChangesTool(deps) {
  return {
    name: "preview_data_source_changes",
    description: `Resolve a set of proposed data source updates/deletes into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true) and always requires a Tier-2 storage backup (requiresStorageBackup is always true). You MUST pass the returned planHash AND taskToken to apply_data_source_changes \u2014 the server writes the audit record from the task narrative embedded in taskToken (the one reviewed here), never from whatever task text apply_data_source_changes itself is called with. READ THIS BEFORE PREVIEWING AN UPDATE ON A JDBC DATA SOURCE WITH requireLogin=true: omitting password from spec leaves the real password unchanged \u2014 the server resolves it internally rather than trusting an echoed masked value, because the wrapped API's own read path can never return the real password in the first place. Sending back either masking literal as password is refused loud by this tool before the server is ever called. READ THIS BEFORE PREVIEWING A DELETE: this area's own dependency check (referencing queries/viewsheets/logical models) is independent, purpose-built logic \u2014 the wrapped server API's own "force" parameter is a confirmed no-op that deletes unconditionally regardless of its value, so this area's own preflight is the ONLY real safety net here, not a redundant belt-and-suspenders check. Delete has NO rollback in this cut (declared non-compensable) \u2014 apply_data_source_changes requires an explicit acknowledgeIrreversibleDelete:true for any plan containing a delete. An update whose spec includes "name" additionally requires confirmRename:true on that change entry, since a rename can auto-create missing parent folders as a side effect of what looks like an unrelated field edit. verb="create" (bug 76599, Gap 2a) creates a BARE, empty data source folder \u2014 no id/name/spec, and metadata-only (does not move or touch any data source); it needs no force/acknowledgeIrreversibleDelete/confirmRename, and its own rollback (if the overall apply fails) removes only the originally-requested leaf path, not any parent folder auto-created as a side effect. To move existing data sources into a folder, use verb="update" with a folder-qualified spec.name instead \u2014 see CHANGES_SCHEMA's own note on batch-moving N data sources in one call. This area is enterprise-only end to end; every tool in it 404s on a community-only deployment.`,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA6, changes: CHANGES_SCHEMA6 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const changes = await attachResolvedNames(deps, normalizeDataSourceChanges(args?.changes));
      return deps.wizClient.post("/v1/admin/data-sources/preview", { task, changes });
    }
  };
}
function makeApplyDataSourceChangesTool(deps) {
  return {
    name: "apply_data_source_changes",
    description: `Apply a previewed data source change plan. Pass back the SAME changes you previewed plus the planHash AND taskToken from preview_data_source_changes. The task field is required but no longer needs to match what you previewed: the server writes the audit record from taskToken's embedded narrative, not from this field, so the audit trail always reflects what was actually reviewed at preview_data_source_changes. Every plan in this area requires a non-blank reviewOutcome. If the plan contains any delete entry you MUST also pass acknowledgeIrreversibleDelete:true \u2014 delete has NO rollback in this cut, and the Tier-2 storage snapshot is the only recovery path, manual and offline. Result status is one of: "applied", "rolled-back" (something failed and every undoable change \u2014 update and verb="create" folder entries, never delete \u2014 was reverted; a reverted folder create's own rollback removes only the originally-requested leaf path, not any parent folder auto-created as a side effect), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied). A delete entry's own outcome is always "applied" or "failed", never individually rolled back. For a delete where force was used with dependencies present, the response carries the dependent-asset advisory as a first-class field on that change's result, not buried in free text \u2014 relay it to the human verbatim. This area is enterprise-only end to end; every tool in it 404s on a community-only deployment.`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA6,
        changes: CHANGES_SCHEMA6,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_data_source_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_data_source_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        },
        acknowledgeIrreversibleDelete: {
          type: "boolean",
          description: 'required and must be true whenever changes contains any verb="delete" entry \u2014 delete has no rollback in this cut. Omit entirely for an update-only plan.'
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_data_source_changes for this plan. Call preview_data_source_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_data_source_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_data_source_changes first."
        );
      }
      const changes = normalizeDataSourceChanges(args.changes);
      const acknowledgeIrreversibleDelete = requireAcknowledgeIrreversibleDelete(
        changes,
        args.acknowledgeIrreversibleDelete
      );
      const resolvedChanges = await attachResolvedNames(deps, changes);
      const body = {
        task: requireTask(args.task),
        changes: resolvedChanges,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeIrreversibleDelete !== void 0) {
        body.acknowledgeIrreversibleDelete = acknowledgeIrreversibleDelete;
      }
      try {
        return await deps.wizClient.post("/v1/admin/data-sources/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_data_source_changes and apply_data_source_changes \u2014 the named data source, or its dependency graph, changed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_data_source_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeDataSourceTools(deps) {
  return [
    makeListDataSourcesTool(deps),
    makeGetDataSourceTool(deps),
    makeTestConnectionTool(deps),
    makePreviewDataSourceChangesTool(deps),
    makeApplyDataSourceChangesTool(deps)
  ];
}

// src/tools/clusterTools.ts
var PAUSE_DURABILITY_DISCLOSURE = `A successful pause is a LIVE SIGNAL, not a persistent policy: it does NOT survive the paused node restarting or temporarily leaving the cluster. A crash, a planned restart (the very event "pause for maintenance" exists to precede), or a transient network partition all silently clear the paused flag on rejoin \u2014 no error, no log line above DEBUG, and no signal to any caller, including this area's own tools, that the operator's last explicit pause silently stopped being true. If the intent is to keep a node out of rotation across such an event, re-issue pause after it rejoins; do not assume a past "verified" pause result still holds.`;
var CHANGES_SCHEMA7 = {
  type: "array",
  description: "the proposed pause/resume actions; each entry names a verb and a server. List each (verb, server) pair at most once. A server named with BOTH verbs in the same request is refused as contradictory, not a duplicate \u2014 split into two plans applied in sequence, or drop one.",
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"pause" (alias "stop") or "resume" (aliases "unpause"/"start")'
      },
      server: {
        type: "string",
        description: "the server name, exactly as returned by list_cluster_nodes/get_cluster_node. `node`/`serverName` are accepted aliases. A name not currently in the cluster's configured server set is refused loud, naming the unrecognized server(s) \u2014 there is no node to pause, so this is a hard refusal, not a downgrade-and-proceed."
      }
    },
    required: ["verb", "server"]
  }
};
var TASK_SCHEMA7 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListClusterNodesTool(deps) {
  return {
    name: "list_cluster_nodes",
    description: `List every configured cluster server node with its current status ("Running"/"Paused"/"Stopped") and reachable (false when the node's live status is DOWN \u2014 a pause/resume against a DOWN node silently no-ops server-side today, so check this before previewing). No filters. Does not require cluster.pause.enabled to be on \u2014 matches the real Enterprise Manager UI, which keeps the whole node/status table visible and only hides the Pause/Resume buttons when the property is off. Cluster nodes are whole-deployment, not org-scoped \u2014 there is no orgId argument here or anywhere in this area. This area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.`,
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/cluster/nodes");
    }
  };
}
async function requireLiveClusterServer(deps, server) {
  const nodes = await deps.wizClient.get("/v1/admin/cluster/nodes");
  const known = Array.isArray(nodes) ? nodes.map((node) => node && typeof node === "object" ? node.server : void 0).filter((s) => typeof s === "string") : [];
  if (!known.includes(server)) {
    throw new Error(
      `server: no server named "${server}" in this cluster (checked against list_cluster_nodes' live configured server set)`
    );
  }
}
function makeGetClusterNodeTool(deps) {
  return {
    name: "get_cluster_node",
    description: "Read one cluster server node's current status and reachability by name. `server`/`node`/`serverName` accepted as aliases, trimmed, required. Returns a structured 404-shaped error naming the server on a miss \u2014 never a default-constructed, misleadingly-DOWN-looking record for a name that isn't actually part of this cluster. Does not require cluster.pause.enabled to be on. This area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: { server: { type: "string", description: "server, node, or serverName (all accepted)" } },
      required: ["server"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const server = requireClusterServer(args);
      await requireLiveClusterServer(deps, server);
      return deps.wizClient.get(`/v1/admin/cluster/nodes/${encodeURIComponent(server)}`);
    }
  };
}
function makePreviewClusterChangesTool(deps) {
  return {
    name: "preview_cluster_changes",
    description: "Resolve a set of proposed cluster server pause/resume actions into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true); no verb ever requires a Tier-2 storage backup (requiresStorageBackup is always false \u2014 the only state touched is a live, in-memory cluster-wide flag, not SreeEnv/storage). You MUST pass the returned planHash to apply_cluster_changes. An unrecognized server name is refused loud, naming it \u2014 there is no node to pause/resume, this is a hard refusal, not a downgrade. A pause on an already-Paused server (or resume on an already-non-Paused server) still produces a plan entry, described as a no-op, rather than being silently dropped. The whole plan is refused if cluster.pause.enabled is not turned on (a real, pre-existing gap this area's own service closes: the raw server endpoint enforces nothing here itself) \u2014 this applies to BOTH pause and resume, not just pause. Cluster node state can drift between preview and apply without any admin-chat action at all (a crash, another admin, another cluster member) \u2014 the plan hash captures each server's live current status, so any such drift is refused as a conflict on apply, never silently acted on with a stale assumption. " + PAUSE_DURABILITY_DISCLOSURE + " This area is NOT enterprise-gated \u2014 this tool works on a community-only deployment.",
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA7, changes: CHANGES_SCHEMA7 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/cluster/preview", {
        task: requireTask(args?.task),
        changes: normalizeClusterChanges(args?.changes)
      });
    }
  };
}
function makeApplyClusterChangesTool(deps) {
  return {
    name: "apply_cluster_changes",
    description: `Apply a previewed cluster pause/resume plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_cluster_changes \u2014 the server writes the audit record from the task narrative embedded in taskToken -- the one reviewed at preview_cluster_changes -- never from whatever task text apply_cluster_changes itself is called with. Every plan in this area requires a non-blank reviewOutcome. UNLIKE EVERY OTHER AREA IN THIS PLUGIN, this area never rolls back: each server's pause/resume is independent and self-inverse, so a per-server failure is reported, not automatically undone (force-reverting other, unrelated servers' successful changes to "fix" one failure would be actively worse). Overall result status is one of: "applied" (every server verified), "partial" (a mix of verified and failed servers), "failed" (every server failed), or "conflict" (the plan drifted since preview \u2014 a server's status changed, nothing was applied). There is no "rolled-back"/"rollback-failed" status in this area at all \u2014 do not expect one. Each entry's own outcome is "verified" (a fresh post-action status read-back matched the proposed state) or "failed" (including the pre-existing silent-failure case: the node was DOWN/unreachable, or the pause/resume message round-trip timed out) \u2014 this per-server read-back is this area's own fix for a real, pre-existing gap where the raw endpoint discards that signal entirely. "Rollback", to the extent this area has one at all, is calling this SAME tool again with the opposite verb on the same server(s) \u2014 resume undoes a prior pause and vice versa; there is no separate undo action. ` + PAUSE_DURABILITY_DISCLOSURE + " This area is NOT enterprise-gated \u2014 this tool works on a community-only deployment; reading back a cluster changeset (get_changeset) is still enterprise-only and 404s on community.",
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA7,
        changes: CHANGES_SCHEMA7,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_cluster_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_cluster_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_cluster_changes for this plan. Call preview_cluster_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_cluster_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_cluster_changes first. If preview_cluster_changes's response contained no taskToken at all, this StyleBI deployment predates the pinned-audit contract and must be upgraded \u2014 do not retry."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeClusterChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/cluster/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_cluster_changes and apply_cluster_changes \u2014 a named server's status changed (it crashed, rejoined, or another admin/session paused or resumed it) between preview and apply. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_cluster_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeClusterTools(deps) {
  return [
    makeListClusterNodesTool(deps),
    makeGetClusterNodeTool(deps),
    makePreviewClusterChangesTool(deps),
    makeApplyClusterChangesTool(deps)
  ];
}

// src/tools/repositoryMaintenanceTools.ts
var ENTERPRISE_GATING = "This area is enterprise-gated end to end \u2014 every tool here 404s on a community-only deployment (FileApiService/FileApiController, the classes these tools wrap, exist only in the StyleBI enterprise module). backup_storage, documented alongside this area, is a different case: it is NOT enterprise-gated and works on both tiers.";
function sanitizeMaintenanceError(raw) {
  if (typeof raw !== "string" || raw.trim() === "") {
    return void 0;
  }
  const trimmed = raw.trim();
  if (!trimmed.includes("\n") && !/\bat\s[\w$.]+\(/.test(trimmed)) {
    return trimmed;
  }
  const firstLine = trimmed.split(/\r?\n/, 1)[0].trim();
  return firstLine.length > 0 ? firstLine : "an internal error occurred (details omitted)";
}
function sanitizeStatusResult(result) {
  if (typeof result !== "object" || result === null || Array.isArray(result)) {
    return result;
  }
  const obj = result;
  if (typeof obj.error !== "string") {
    return result;
  }
  return { ...obj, error: sanitizeMaintenanceError(obj.error) };
}
function makeRebuildDependenciesTool(deps) {
  return {
    name: "rebuild_dependencies",
    description: `Kick off an asynchronous rebuild of the asset dependency graph (a derived cross-reference index over viewsheets/worksheets/schedule tasks/data sources) \u2014 idempotent by construction: it clears and fully recomputes the graph from the live asset set every time, so running it twice converges to the same result, and it never touches real user content, only this derived index. Returns { token }; poll it with get_rebuild_dependencies_status. The underlying rebuild takes a cluster-wide lock before it starts recomputing: a rebuild already in progress (triggered by admin-chat, Enterprise Manager, or another admin-chat call) makes THIS call's own status poll eventually report failed:true with a timeout-shaped message once timeoutMs elapses waiting for that lock \u2014 that means "try again once the current rebuild finishes," not "rebuild is broken." No preview/planHash/signoff step: there is no diffable "before" value this operation could hash, only a live index it unconditionally recomputes. ` + ENTERPRISE_GATING,
    inputSchema: {
      type: "object",
      properties: {
        timeoutMs: {
          type: "integer",
          description: "how long, in milliseconds, to wait for the dependency-graph lock before giving up; optional, defaults to 300000 (5 minutes), matching the underlying service's own documented default"
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/repository-maintenance/rebuild-dependencies", {
        timeoutMs: optionalTimeoutMs(args?.timeoutMs)
      });
    }
  };
}
function makeGetRebuildDependenciesStatusTool(deps) {
  return {
    name: "get_rebuild_dependencies_status",
    description: "Poll the status of a rebuild_dependencies call by token: { token, complete, failed, error? }. An unrecognized token is refused loud with a structured 404-shaped error naming it \u2014 never a default-looking incomplete/not-failed record for a token that was never issued. `error`, when present, is a short, clean message \u2014 never a raw stack trace, even if the underlying call somehow produced one. " + ENTERPRISE_GATING,
    inputSchema: {
      type: "object",
      properties: { token: { type: "string", description: "the token returned by rebuild_dependencies" } },
      required: ["token"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const token = requireToken(args);
      const result = await deps.wizClient.get(
        `/v1/admin/repository-maintenance/rebuild-dependencies/${encodeURIComponent(token)}`
      );
      return sanitizeStatusResult(result);
    }
  };
}
function makeRepairRepositoryFoldersTool(deps) {
  return {
    name: "repair_repository_folders",
    description: `Kick off an asynchronous repair pass over the repository folder tree: it adds any child entry a folder's own stored entry list is missing, removes any it has that no longer belongs, and drops a duplicate reference when the same schedule task is discovered under two folders. All three are reconciling operations on a folder's own entry LIST, never a deletion of the underlying asset's actual stored content \u2014 additive/reconciling, not destructive, and idempotent by construction (a second run with no intervening drift is a no-op). No arguments. Returns { token }; poll it with get_repair_repository_folders_status. Unlike rebuild_dependencies, the underlying repair has no lock or dedup of its own \u2014 this area's own wrapper closes that gap with a single-flight guard: a second kickoff issued while a repair THIS wrapper itself started is still outstanding is refused with a clear, non-stack-trace error naming the outstanding token (surfaced here as a thrown error, not a 200 response \u2014 treat it as "wait and poll the existing token," not a failure to retry). It does not detect a concurrent raw-API or Enterprise Manager call outside admin-chat, only this wrapper racing itself. No preview/planHash/signoff step: there is no diffable "before" value here either. ` + ENTERPRISE_GATING,
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/repository-maintenance/repair-repository-folders", {});
    }
  };
}
function makeGetRepairRepositoryFoldersStatusTool(deps) {
  return {
    name: "get_repair_repository_folders_status",
    description: "Poll the status of a repair_repository_folders call by token: { token, complete, failed, error? }. An unrecognized token is refused loud with a structured 404-shaped error naming it. `error`, when present, is a short, clean message \u2014 never a raw stack trace, even if the underlying call somehow produced one. " + ENTERPRISE_GATING,
    inputSchema: {
      type: "object",
      properties: { token: { type: "string", description: "the token returned by repair_repository_folders" } },
      required: ["token"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const token = requireToken(args);
      const result = await deps.wizClient.get(
        `/v1/admin/repository-maintenance/repair-repository-folders/${encodeURIComponent(token)}`
      );
      return sanitizeStatusResult(result);
    }
  };
}
function makeRepositoryMaintenanceTools(deps) {
  return [
    makeRebuildDependenciesTool(deps),
    makeGetRebuildDependenciesStatusTool(deps),
    makeRepairRepositoryFoldersTool(deps),
    makeGetRepairRepositoryFoldersStatusTool(deps)
  ];
}

// src/tools/storedAssetTools.ts
import fs3 from "fs";
import os from "os";
import nodePath2 from "path";
var CHANGES_SCHEMA8 = {
  type: "array",
  description: 'the proposed stored-asset changes; each entry is { unitType, verb, path, newName|content }. unitType ("file"|"folder") is a discriminator FIRST \u2014 valid verbs depend on it: "folder" accepts create/rename/delete, "file" accepts write/rename/delete. path is a DataSpace-relative path (no leading "/", no ".." segment \u2014 refused loud, never silently sanitized). List each path at most once.',
  items: {
    type: "object",
    properties: {
      unitType: { type: "string", description: '"file" or "folder"' },
      verb: {
        type: "string",
        description: '"create" (folder only), "write" (file only \u2014 create-or-overwrite), "rename", or "delete"'
      },
      path: {
        type: "string",
        description: 'the DataSpace-relative path to the entry, e.g. "scripts/lib/utils.js". Never an absolute path or one containing "..".'
      },
      newName: {
        type: "string",
        description: 'verb="rename" only \u2014 a bare sibling name (not a path); the entry stays in the same parent folder under this new name.'
      },
      content: {
        type: "string",
        description: `verb="write" only \u2014 the file's full new plain-UTF-8-text content (not a diff/patch). A binary upload is not supported by this tool.`
      }
    },
    required: ["unitType", "verb", "path"]
  }
};
var TASK_SCHEMA8 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function defaultDownloadDir() {
  return nodePath2.join(os.homedir(), "Downloads");
}
function claimUniqueDest(dir, fileName, fromPath) {
  const ext = nodePath2.extname(fileName);
  const base = fileName.slice(0, fileName.length - ext.length);
  for (let i = 0; i < 1e3; i++) {
    const candidate = i === 0 ? nodePath2.join(dir, fileName) : nodePath2.join(dir, `${base} (${i})${ext}`);
    try {
      fs3.linkSync(fromPath, candidate);
      fs3.unlinkSync(fromPath);
      return candidate;
    } catch (err) {
      if (err.code !== "EEXIST") throw err;
    }
  }
  throw new Error(
    `could not find a free filename for '${fileName}' in '${dir}' after 1000 attempts.`
  );
}
async function downloadStoredAssetToDir(deps, requestPath, savePath, fallbackName) {
  const dir = savePath ?? defaultDownloadDir();
  const provisionalDest = nodePath2.join(
    dir,
    `stored-asset-${Date.now()}-${Math.random().toString(36).slice(2)}.tmp`
  );
  const downloaded = await deps.wizClient.downloadToFile(requestPath, provisionalDest);
  const fileName = downloaded.fileName ?? fallbackName;
  const filePath = claimUniqueDest(dir, fileName, provisionalDest);
  return {
    filePath,
    fileName: nodePath2.basename(filePath),
    mimeType: downloaded.mimeType,
    byteLength: downloaded.byteLength
  };
}
function makeListStoredAssetsTool(deps) {
  return {
    name: "list_stored_assets",
    description: 'List one level (no recursion) of the DataSpace file tree under `path` \u2014 every plugin/theme asset, uploaded driver, custom script, and any other file the server stores outside the regular asset repository. Omit `path` (or pass ""/"/") for the root. Returns { path, entries: [{ name, path, folder }] }, folders first then files, both case-insensitively sorted.',
    inputSchema: {
      type: "object",
      properties: {
        path: {
          type: "string",
          description: "the DataSpace-relative folder to list; omit for the root"
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireDataSpacePath(args?.path ?? "", "path");
      const query = path2 === "" ? "" : `?path=${encodeURIComponent(path2)}`;
      return deps.wizClient.get(`/v1/admin/file/content/tree${query}`);
    }
  };
}
function makeGetStoredAssetTool(deps) {
  return {
    name: "get_stored_asset",
    description: "Read one stored asset's metadata \u2014 no content. Returns { path, name, folder, sizeBytes, lastModified, editableAsText } (the last three are null for a folder). Use get_stored_asset_content for a file's actual text, or download_stored_asset/download_stored_asset_zip for its raw bytes.",
    inputSchema: {
      type: "object",
      properties: {
        path: { type: "string", description: "the DataSpace-relative path to the entry" }
      },
      required: ["path"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireDataSpacePath(args?.path, "path");
      return deps.wizClient.get(`/v1/admin/file/content/node?path=${encodeURIComponent(path2)}`);
    }
  };
}
function makeGetStoredAssetContentTool(deps) {
  return {
    name: "get_stored_asset_content",
    description: "Read one file's content as plain UTF-8 text (not base64) \u2014 returns { path, content, editable }. Refused loud for a folder, a non-text/non-UTF-8 file (use download_stored_asset instead), or a file over the server's inline-read size cap unless preview:true, which returns a truncated excerpt instead of the full content.",
    inputSchema: {
      type: "object",
      properties: {
        path: { type: "string", description: "the DataSpace-relative path to the file" },
        preview: {
          type: "boolean",
          description: "return a truncated excerpt instead of refusing an over-cap file"
        }
      },
      required: ["path"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireDataSpacePath(args?.path, "path");
      const preview = args?.preview === true;
      const query = `?path=${encodeURIComponent(path2)}${preview ? "&preview=true" : ""}`;
      return deps.wizClient.get(`/v1/admin/file/content/text${query}`);
    }
  };
}
function makeDownloadStoredAssetTool(deps) {
  return {
    name: "download_stored_asset",
    description: "Download one file's raw bytes to local disk (binary-safe, unlike get_stored_asset_content). Returns { filePath, fileName, mimeType, byteLength } \u2014 never the file's own bytes, which keeps a multi-MB download cheap regardless of size. Refused loud for a folder \u2014 use download_stored_asset_zip instead.",
    inputSchema: {
      type: "object",
      properties: {
        path: { type: "string", description: "the DataSpace-relative path to the file" },
        savePath: {
          type: "string",
          description: "directory to save the file into (not a full file path \u2014 the server's own filename is used). Defaults to this machine's Downloads folder. Created if it doesn't already exist."
        }
      },
      required: ["path"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireDataSpacePath(args?.path, "path");
      const savePath = typeof args?.savePath === "string" && args.savePath.trim() !== "" ? args.savePath : void 0;
      const fallbackName = path2.split("/").pop() || "download";
      const result = await downloadStoredAssetToDir(
        deps,
        `/v1/admin/file/content/download?path=${encodeURIComponent(path2)}`,
        savePath,
        fallbackName
      );
      return {
        ...result,
        summary: `Downloaded "${path2}" to ${result.filePath} (${result.byteLength.toLocaleString()} bytes).`
      };
    }
  };
}
function makeDownloadStoredAssetZipTool(deps) {
  return {
    name: "download_stored_asset_zip",
    description: "Download a folder (or the whole DataSpace root, if `path` is omitted) as a zip archive to local disk. Returns { filePath, fileName, mimeType, byteLength } \u2014 never the archive's own bytes.",
    inputSchema: {
      type: "object",
      properties: {
        path: {
          type: "string",
          description: "the DataSpace-relative folder to zip; omit for the whole root"
        },
        savePath: {
          type: "string",
          description: "directory to save the archive into (not a full file path \u2014 the server's own filename is used). Defaults to this machine's Downloads folder. Created if it doesn't already exist."
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireDataSpacePath(args?.path ?? "", "path");
      const savePath = typeof args?.savePath === "string" && args.savePath.trim() !== "" ? args.savePath : void 0;
      const query = path2 === "" ? "" : `?path=${encodeURIComponent(path2)}`;
      const fallbackName = (path2 === "" ? "Storage" : path2.split("/").pop() || "download") + ".zip";
      const result = await downloadStoredAssetToDir(
        deps,
        `/v1/admin/file/content/download-folder${query}`,
        savePath,
        fallbackName
      );
      return {
        ...result,
        summary: `Downloaded "${path2 === "" ? "/" : path2}" to ${result.filePath} (${result.byteLength.toLocaleString()} bytes).`
      };
    }
  };
}
function makePreviewStoredAssetChangesTool(deps) {
  return {
    name: "preview_stored_asset_changes",
    description: 'Resolve a set of proposed stored-asset changes (create/write/rename/delete) into a reviewable plan WITHOUT changing anything on the server. Every plan in this area always requires a Tier-2 storage backup (requiresStorageBackup is always true); requiresAgentSignoff is true whenever any entry overwrites/deletes existing content. You MUST pass the returned planHash AND taskToken to apply_stored_asset_changes \u2014 the server writes the audit record from the task narrative embedded in taskToken (the one reviewed here), never from whatever task text apply_stored_asset_changes itself is called with. A "create" over an existing path, or a "rename" onto an existing destination, is refused loud rather than silently overwritten. A folder delete is ALWAYS non-compensable (no live rollback); a file write/delete MAY be non-compensable depending on live server-side state (size cap, UTF-8 decodability) \u2014 either case requires acknowledgeIrreversibleDelete:true on the matching apply_stored_asset_changes call.',
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA8, changes: CHANGES_SCHEMA8 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const changes = normalizeStoredAssetChanges(args?.changes);
      return deps.wizClient.post("/v1/admin/file/content/preview", { task, changes });
    }
  };
}
function makeApplyStoredAssetChangesTool(deps) {
  return {
    name: "apply_stored_asset_changes",
    description: `Apply a previewed stored-asset change plan. Pass back the SAME changes you previewed plus the planHash AND taskToken from preview_stored_asset_changes. The task field is required but no longer needs to match what you previewed: the server writes the audit record from taskToken's embedded narrative, not from this field. Every plan in this area requires a non-blank reviewOutcome. If the plan contains a folder delete (or the server otherwise determines an entry is non-compensable) you MUST also pass acknowledgeIrreversibleDelete:true \u2014 the Tier-2 storage snapshot is the only recovery path for that entry, manual and offline. Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted or the taskToken no longer matches, nothing was applied).`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA8,
        changes: CHANGES_SCHEMA8,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_stored_asset_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_stored_asset_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        },
        acknowledgeIrreversibleDelete: {
          type: "boolean",
          description: "required and must be true whenever changes contains a folder delete; may also be required for a file write/delete depending on live server-side state. Omit entirely for a plan the server confirms is fully compensable."
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_stored_asset_changes for this plan. Call preview_stored_asset_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_stored_asset_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_stored_asset_changes first."
        );
      }
      const changes = normalizeStoredAssetChanges(args.changes);
      const acknowledgeIrreversibleDelete = requireAcknowledgeIrreversibleStoredAssetDelete(
        changes,
        args.acknowledgeIrreversibleDelete
      );
      const body = {
        task: requireTask(args.task),
        changes,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeIrreversibleDelete !== void 0) {
        body.acknowledgeIrreversibleDelete = acknowledgeIrreversibleDelete;
      }
      try {
        return await deps.wizClient.post("/v1/admin/file/content/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_stored_asset_changes and apply_stored_asset_changes \u2014 the named path, or its content, changed by someone else in between (or this taskToken was issued for a different plan). NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call preview_stored_asset_changes again and apply THAT plan's own planHash/taskToken."
          };
        }
        throw err;
      }
    }
  };
}
function makeStoredAssetTools(deps) {
  return [
    makeListStoredAssetsTool(deps),
    makeGetStoredAssetTool(deps),
    makeGetStoredAssetContentTool(deps),
    makeDownloadStoredAssetTool(deps),
    makeDownloadStoredAssetZipTool(deps),
    makePreviewStoredAssetChangesTool(deps),
    makeApplyStoredAssetChangesTool(deps)
  ];
}

// src/tools/pluginManagementTools.ts
function makeListDriversAndPluginsTool(deps) {
  return {
    name: "list_drivers_and_plugins",
    description: "List every installed JDBC driver and StyleBI plugin: { clustered, supportUploadDriver, plugins: [{ id, name, version, readOnly, vendor? }] }. A read-only bare list \u2014 no arguments, no acknowledgment gate. Community-tier: works on a community-only deployment.",
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/plugins");
    }
  };
}
function makeUploadDriverOrPluginTool(deps) {
  return {
    name: "upload_driver_or_plugin",
    description: `Upload a local .jar (a raw JDBC driver) or .zip (a full StyleBI plugin) file for later install. Returns { uploadId, fileName }. Nothing is installed yet and nothing observable to a user changes \u2014 read-only from the deployment's own installed-state point of view, so there is no acknowledgment gate here, matching upload_csv_table/upload_excel_table and export_assets's own "nothing mutates yet" precedent. Next step: for a bare .jar, call scan_uploaded_drivers to find its driver class name(s) before install_driver_or_plugin; for a .zip plugin, call install_driver_or_plugin directly.`,
    inputSchema: {
      type: "object",
      properties: {
        filePath: { type: "string", description: "Absolute path to a local .jar or .zip file." }
      },
      required: ["filePath"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const filePath = requireFilePath(args);
      const ext = filePath.split(".").pop()?.toLowerCase();
      if (ext !== "jar" && ext !== "zip") {
        throw new Error(
          `filePath: must be a .jar (raw JDBC driver) or .zip (StyleBI plugin) file, got: ${filePath}`
        );
      }
      const { readFile: readFile2 } = await import("fs/promises");
      const bytes = await readFile2(filePath);
      const fileName = filePath.split(/[\\/]/).pop() ?? `upload.${ext}`;
      const form = new FormData();
      form.append("file", new Blob([bytes]), fileName);
      return deps.wizClient.postMultipart("/v1/admin/plugins/upload", form);
    }
  };
}
function makeScanUploadedDriversTool(deps) {
  return {
    name: "scan_uploaded_drivers",
    description: "Scan an uploaded .jar for its JDBC driver class name(s): { drivers: [className...] }. Only meaningful for a bare driver jar upload \u2014 the server refuses loud (not an empty list) both when uploadId is unrecognized/expired and when the upload scans clean of any driver class (e.g. because it was actually a full plugin zip, which does not need scanning at all \u2014 call install_driver_or_plugin directly for that case instead).",
    inputSchema: {
      type: "object",
      properties: {
        uploadId: { type: "string", description: "the id returned by upload_driver_or_plugin" }
      },
      required: ["uploadId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const uploadId = requireUploadId(args);
      return deps.wizClient.get(`/v1/admin/plugins/drivers/scan/${encodeURIComponent(uploadId)}`);
    }
  };
}
function makeInstallDriverOrPluginTool(deps) {
  return {
    name: "install_driver_or_plugin",
    description: 'Install an uploaded driver or plugin \u2014 the one genuinely irreversible-consequence call in this area: it executes arbitrary code AS THE SERVER. If asDriverPlugin is given (the scan-then-create path for a bare driver jar: { pluginId, pluginName, pluginVersion, drivers }, drivers from scan_uploaded_drivers), a new plugin wrapper is generated around the uploaded jar; if omitted, the upload is installed directly as a StyleBI plugin zip. Returns the refreshed list_drivers_and_plugins-shaped model. acknowledgeServerCodeExecution must be true unconditionally (deliberately distinct from acknowledgeIrreversibleRemove used by remove_driver_or_plugin \u2014 this is a different risk class, "this executes as the server", not "this cannot be undone") and reviewOutcome is required, naming what the human reviewer confirmed before this action was taken.',
    inputSchema: {
      type: "object",
      properties: {
        task: { type: "string", description: "short description of why this install was requested" },
        uploadId: { type: "string", description: "the id returned by upload_driver_or_plugin" },
        asDriverPlugin: {
          type: "object",
          description: "present only for a bare driver-jar upload (the scan-then-create path); omit for a full plugin-zip upload",
          properties: {
            pluginId: { type: "string" },
            pluginName: { type: "string" },
            pluginVersion: { type: "string" },
            drivers: { type: "array", items: { type: "string" } }
          }
        },
        acknowledgeServerCodeExecution: {
          type: "boolean",
          description: "required, must be true \u2014 installing a plugin/driver executes arbitrary code as the server; set true only after a human has confirmed this"
        },
        reviewOutcome: {
          type: "string",
          description: "what the human reviewer confirmed before this action was taken"
        }
      },
      required: ["uploadId", "acknowledgeServerCodeExecution", "reviewOutcome"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const obj = typeof args === "object" && args !== null ? args : {};
      const uploadId = requireUploadId(obj);
      const acknowledgeServerCodeExecution = requireAcknowledgeServerCodeExecution(obj);
      const reviewOutcome = requireReviewOutcome(obj);
      const asDriverPlugin = normalizeAsDriverPlugin(obj.asDriverPlugin);
      return deps.wizClient.post("/v1/admin/plugins/install", {
        task: typeof obj.task === "string" ? obj.task : null,
        uploadId,
        asDriverPlugin,
        acknowledgeServerCodeExecution,
        reviewOutcome
      });
    }
  };
}
function makeRemoveDriverOrPluginTool(deps) {
  return {
    name: "remove_driver_or_plugin",
    description: "Uninstall one or more currently-installed drivers/plugins by id. Server-side is already audited. No automatic Tier-2 backup is proposed (unlike e.g. import_assets): removing an installed plugin/driver is trivially reversible by re-installing the same uploaded file, unlike an asset-import's silent overwrite of arbitrary repository content. acknowledgeIrreversibleRemove must be true and reviewOutcome is required, naming what the human reviewer confirmed before this action was taken. Returns the refreshed list_drivers_and_plugins-shaped model.",
    inputSchema: {
      type: "object",
      properties: {
        task: { type: "string", description: "short description of why this removal was requested" },
        pluginIds: {
          type: "array",
          items: { type: "string" },
          description: "ids of currently-installed drivers/plugins to remove (list_drivers_and_plugins' plugins[].id)"
        },
        acknowledgeIrreversibleRemove: {
          type: "boolean",
          description: "required, must be true \u2014 set true only after a human has confirmed this"
        },
        reviewOutcome: {
          type: "string",
          description: "what the human reviewer confirmed before this action was taken"
        }
      },
      required: ["pluginIds", "acknowledgeIrreversibleRemove", "reviewOutcome"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const obj = typeof args === "object" && args !== null ? args : {};
      const pluginIds = requirePluginIds(obj);
      const acknowledgeIrreversibleRemove = requireAcknowledgeIrreversibleRemove(obj);
      const reviewOutcome = requireReviewOutcome(obj);
      return deps.wizClient.post("/v1/admin/plugins/remove", {
        task: typeof obj.task === "string" ? obj.task : null,
        pluginIds,
        acknowledgeIrreversibleRemove,
        reviewOutcome
      });
    }
  };
}
function requireFilePath(args) {
  const obj = typeof args === "object" && args !== null ? args : {};
  const raw = obj.filePath;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error("filePath: required (absolute path to a local .jar or .zip file)");
  }
  return raw.trim();
}
function makePluginManagementTools(deps) {
  return [
    makeListDriversAndPluginsTool(deps),
    makeUploadDriverOrPluginTool(deps),
    makeScanUploadedDriversTool(deps),
    makeInstallDriverOrPluginTool(deps),
    makeRemoveDriverOrPluginTool(deps)
  ];
}

// src/tools/viewsheetTools.ts
var CHANGES_SCHEMA9 = {
  type: "array",
  description: `the proposed viewsheet/folder/worksheet changes; each entry names unitType FIRST \u2014 set it correctly before adding other fields, since a field belonging to a different unit type, or to a verb this entry doesn't use, is refused loud, never silently dropped. A single plan may freely mix unitType="viewsheet", unitType="folder", and unitType="worksheet" entries.`,
  items: {
    type: "object",
    properties: {
      unitType: {
        type: "string",
        description: `exactly "viewsheet", "folder", or "worksheet" \u2014 NO abbreviations are accepted (the three unit types have different key shapes and different verb sets, so a wrong guess here is unusually costly). worksheet and viewsheet asset ids look identical in shape but identify different asset types \u2014 passing a worksheet's assetId under unitType="viewsheet" (or vice versa) is refused loud, naming the actual mismatch, before any not-found lookup runs.`
      },
      verb: {
        type: "string",
        description: `for unitType="viewsheet" or unitType="worksheet" (identical verb set for both): "rename", "delete", or "update" (also accepts "move" as an alias for "rename", "remove" as an alias for "delete"). For unitType="folder": "create", "delete", "rename", or "update" (also accepts "add" as an alias for "create", "remove" as an alias for "delete"). A verb that doesn't exist for the given unitType (e.g. "create" on a viewsheet or worksheet) is refused loud, naming the valid verbs for that unit type. "update" (alias/description only) never renames or moves anything, regardless of unitType \u2014 it is a separate, RISK_LOW verb, never folded into rename.`
      },
      assetId: {
        type: "string",
        description: `unitType="viewsheet" or unitType="worksheet" only, required for all three verbs \u2014 the asset identifier as returned by list_viewsheets/list_worksheets (e.g. "1^128^__NULL__^Examples/Census^host-org"). Its shape is validated before any server call \u2014 a hand-typed or malformed value is refused loud here rather than reaching the server, which would otherwise throw an unstructured error instead of a clean not-found response. A successful rename changes the asset's own identifier (its path is embedded in it) \u2014 re-read via list_viewsheets/list_worksheets afterward rather than reusing a pre-rename assetId in a later change.`
      },
      newPath: {
        type: "string",
        description: 'required for verb="rename" on any unitType; not used for any other verb. For a viewsheet/worksheet rename, the destination folder must already exist \u2014 unlike a folder create, this does NOT auto-create missing parent folders; targeting a nonexistent destination folder is refused loud.'
      },
      global: {
        type: "boolean",
        description: 'unitType="viewsheet" or unitType="worksheet", verb="rename" only, required. true targets the shared repository tree (owner is then forbidden); false targets a per-user "My Dashboards"/worksheet-equivalent tree (owner is then required).'
      },
      owner: {
        type: "string",
        description: 'an identity id. For unitType="viewsheet"/"worksheet" verb="rename": required when global=false, forbidden when global=true. For unitType="folder" (any verb): always optional \u2014 selects a per-user folder registry instead of the global one.'
      },
      force: {
        type: "boolean",
        description: `default false. For unitType="viewsheet"/"worksheet" verb="delete": when the asset has dependents (other assets referencing it), preview_viewsheet_changes itself refuses the plan naming them, unless force is set. This is NOT a no-op the way the underlying Public API's own delete is (it always deletes unconditionally, with no dependency check of its own) \u2014 force here gates this area's own independently re-implemented safety check; for a worksheet this matters at least as much as for a viewsheet, since a worksheet is commonly the data-binding source for one or more viewsheets. For unitType="folder" verb="delete": required (true) whenever the folder is non-empty \u2014 preview_viewsheet_changes classifies that entry risk:"high" and names the contained viewsheet/worksheet(s); apply_viewsheet_changes refuses a non-empty folder delete without force:true on this entry (StyleBI-side gate, Redmine #76469 fix). Not used/required for an empty folder delete (risk:"low"), and not used for verb="create"/"rename"/"update" on a folder, or verb="rename"/"update" on a viewsheet/worksheet.`
      },
      folderName: {
        type: "string",
        description: `unitType="folder" verb="create" only, required \u2014 the new folder's own name, not a full path.`
      },
      parentFolder: {
        type: "string",
        description: 'unitType="folder" verb="create" only, optional (root if omitted). Missing ancestor folders along this path are auto-created \u2014 disclosed "mkdir -p"-shaped behavior, not a safety gap, because creating a folder label has no effect on any viewsheet.'
      },
      path: {
        type: "string",
        description: 'unitType="folder" verb="delete", "rename", or "update" only, required \u2014 the folder\'s current registry path (e.g. "Examples/Old Folder"). `oldPath` is an accepted alias for this same field (the underlying API\'s two request shapes name it differently for the same concept). IMPORTANT for delete: a folder delete PERMANENTLY AND IRRECOVERABLY DELETES every viewsheet and worksheet stored under this path, recursively including nested folders \u2014 despite the verb name this is NOT a registry-label-only operation. There is no recycle bin and no undo: StyleBI hard-deletes each contained asset from storage the moment the folder delete is applied. Re-creating the folder afterward only restores an empty label \u2014 it does NOT restore any content. preview_viewsheet_changes classifies this entry risk:"low" only when the folder is empty; a NON-EMPTY folder is risk:"high", and apply_viewsheet_changes then requires BOTH force:true on this entry AND acknowledgeIrreversibleDelete:true on the request \u2014 omitting either is refused loud, naming the folder and its contained-asset count (StyleBI-side gate, Redmine #76469 fix). An empty-folder delete needs neither flag. IMPORTANT for rename: a folder rename is NOT metadata-only \u2014 StyleBI keeps the registry folder label and the underlying asset-tree folder in sync, so renaming this folder REWRITES the path/asset-identifier of every viewsheet and worksheet currently stored under it, recursively including nested folders. Any external reference to those viewsheets by full path/asset id (schedule tasks, bookmarks, hyperlinks, other automation) can go stale as a result. For update, this only changes alias/description \u2014 it never moves or renames the folder.'
      },
      alias: {
        type: "string",
        description: 'verb="update" (any unitType, required unless description is given) or unitType="folder" verb="create" (optional) \u2014 the display alias. Omitting this field entirely leaves the current alias unchanged; an explicit empty string ("") CLEARS it. This distinction matters: passing an empty string is not the same as leaving the field out. Never used for verb="rename"/"delete".'
      },
      description: {
        type: "string",
        description: 'same omit-vs-clear semantics as alias, for the free-text description field. At least one of alias/description is required for verb="update" (both optional and independent for a folder verb="create").'
      }
    },
    required: ["unitType", "verb"]
  }
};
var TASK_SCHEMA9 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListViewsheetsTool(deps) {
  return {
    name: "list_viewsheets",
    description: `List viewsheets (dashboards) the caller can read, in the resolved organization \u2014 both the shared repository tree and the caller's own "My Dashboards" tree, recycle-bin entries excluded. organizationid is optional and defaults to the caller's own current organization. When security.exposedefaultorgtoall is enabled, the result may also include the host organization's own global viewsheets for a caller in a different organization \u2014 that is expected, not a bug. There is no single-item get_viewsheet tool (none exists on the underlying service) \u2014 filter this list's result by assetId, or rely on preview_viewsheet_changes' own structured not-found error on a bad assetId.`,
    inputSchema: {
      type: "object",
      properties: {
        organizationid: {
          type: "string",
          description: "optional; defaults to the caller's own current organization"
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const orgId = typeof args?.organizationid === "string" ? args.organizationid.trim() : "";
      const query = orgId === "" ? "" : `?organizationid=${encodeURIComponent(orgId)}`;
      return deps.wizClient.get(`/v1/admin/viewsheets${query}`);
    }
  };
}
function makeGetViewsheetFolderTool(deps) {
  return {
    name: "get_viewsheet_folder",
    description: 'Read one viewsheet FOLDER \u2014 a registry label path used to organize the repository navigation tree, NOT an asset and NOT the same thing as a directory of viewsheet content (a folder can exist with no viewsheets "in" it, and a viewsheet\'s own path can exist with no folder label covering it \u2014 the two are independent). `found:false` is a normal answer, not an error: most paths have no explicit folder registry entry. owner is optional and selects a per-user folder registry (e.g. "My Dashboards") instead of the global one.',
    inputSchema: {
      type: "object",
      properties: {
        path: { type: "string", description: `the folder's registry path, e.g. "Examples/Old Folder"` },
        owner: { type: "string", description: "optional; selects a per-user folder registry" }
      },
      required: ["path"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireViewsheetFolderPath(args ?? {});
      const owner = typeof args?.owner === "string" && args.owner.trim() !== "" ? args.owner.trim() : "";
      const query = owner === "" ? `path=${encodeURIComponent(path2)}` : `path=${encodeURIComponent(path2)}&owner=${encodeURIComponent(owner)}`;
      return deps.wizClient.get(`/v1/admin/viewsheets/folder?${query}`);
    }
  };
}
function makePreviewViewsheetChangesTool(deps) {
  return {
    name: "preview_viewsheet_changes",
    description: `Resolve a set of proposed viewsheet renames/deletes and folder creates/deletes/renames into a reviewable plan WITHOUT changing anything on the server -- unitType="worksheet" (Track B/Redmine #76604 Gap7) shares viewsheet's own rename/delete verbs, plus a new "update" verb (alias/description only, RISK_LOW, available on all three unit types) that never renames or moves anything, regardless of unitType. UNLIKE every other storage-scoped area in this plugin, requiresAgentSignoff is NOT unconditionally true here \u2014 risk genuinely varies by verb, and (for folder delete) by folder content: a plan containing any viewsheet or worksheet verb="rename"/"delete" (rename or delete) is high risk and requires signoff; a plan containing ONLY folder verbs is low-medium risk UNLESS it includes a delete of a NON-EMPTY folder \u2014 that single entry is risk:"high" and drives requiresAgentSignoff:true for the whole plan, exactly like a viewsheet delete does (Redmine #76469 fix). A plan containing only "update" entries needs no signoff. requiresStorageBackup is always true for every verb in this area regardless of risk. READ THIS BEFORE PREVIEWING A VIEWSHEET DELETE: this area's own dependency check (referencing other assets) is independent, purpose-built logic \u2014 the underlying Public API always deletes unconditionally with no dependency check of its own, so this area's own preflight is the ONLY real safety net here. A viewsheet OR WORKSHEET delete has NO rollback in this cut (declared non-compensable) \u2014 apply_viewsheet_changes requires an explicit acknowledgeIrreversibleDelete:true for any plan containing either. A worksheet delete's own dependency check matters at least as much as a viewsheet's, since a worksheet is commonly the data-binding source for one or more viewsheets. READ THIS BEFORE PREVIEWING A FOLDER DELETE: this PERMANENTLY AND IRRECOVERABLY deletes every viewsheet and worksheet stored under the folder, recursively including nested folders \u2014 StyleBI hard-deletes each contained asset from storage the moment the delete is applied. There is no recycle bin and no rollback. Risk and gating now genuinely depend on folder content (Redmine #76469 fix): an EMPTY folder is risk:"low" and applies with neither flag; a NON-EMPTY folder is risk:"high", names every contained viewsheet/worksheet in this plan's description, and apply_viewsheet_changes then requires BOTH force:true on that entry AND acknowledgeIrreversibleDelete:true on the request \u2014 exactly as irreversible as a viewsheet delete, just at the scale of an entire subtree, and now gated the same way. READ THIS BEFORE PREVIEWING A FOLDER RENAME: unlike delete, a folder rename is NOT metadata-only \u2014 it REWRITES the path/asset-identifier of every viewsheet and worksheet stored under the renamed folder (recursively, including nested folders), because StyleBI keeps the registry folder label and the underlying asset-tree folder in sync for a direct rename. Disclose this plainly to the human before a folder-rename plan is applied \u2014 any external reference to the affected viewsheets by full path/asset id (schedule tasks, bookmarks, hyperlinks, other automation) can go stale as a result. You MUST pass the returned planHash AND taskToken to apply_viewsheet_changes: the server writes the audit record from the task narrative embedded in taskToken \u2014 the one reviewed here \u2014 never from whatever task text apply_viewsheet_changes itself is called with.`,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA9, changes: CHANGES_SCHEMA9 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/viewsheets/preview", {
        task: requireTask(args?.task),
        changes: normalizeViewsheetChanges(args?.changes)
      });
    }
  };
}
async function resolveFolderDeleteRisks(deps, task, changes) {
  const preview = await deps.wizClient.post("/v1/admin/viewsheets/preview", { task, changes });
  const planChanges = preview?.changes;
  return Array.isArray(planChanges) ? planChanges : void 0;
}
function makeApplyViewsheetChangesTool(deps) {
  return {
    name: "apply_viewsheet_changes",
    description: `Apply a previewed viewsheet/folder/worksheet change plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_viewsheet_changes \u2014 the request body IS the plan; the server never trusts a stored one. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_viewsheet_changes. reviewOutcome is required whenever the previewed plan had requiresAgentSignoff:true (a plan containing any viewsheet or worksheet rename/delete verb) \u2014 not unconditionally required the way every other storage-scoped area's own apply tool is. If the plan contains any viewsheet OR WORKSHEET delete entry you MUST also pass acknowledgeIrreversibleDelete:true \u2014 neither has a rollback in this cut, and the Tier-2 storage snapshot is the only recovery path, manual and offline. If the plan contains a delete of a NON-EMPTY folder (preview_viewsheet_changes classified that entry risk:"high") you MUST also pass force:true on that entry AND acknowledgeIrreversibleDelete:true on the request \u2014 StyleBI refuses a non-empty folder delete without both (Redmine #76469 fix), since it permanently and irrecoverably deletes every viewsheet/worksheet stored under it, recursively, with no recycle bin and no rollback. An empty-folder delete (risk:"low") needs neither flag. Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted \u2014 a viewsheet delete entry is never individually rolled back, only applied/failed; folder entries and viewsheet renames roll back normally around it), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied). For a viewsheet delete where force was used with dependencies present, the response carries the dependency advisory as a first-class field on that change's result \u2014 relay it to the human verbatim. A folder rename's own result carries the cascade warning (every viewsheet under the renamed folder had its path/asset-identifier rewritten) \u2014 relay it verbatim too. A folder delete's own server-supplied result/description text is now computed from real folder contents (Redmine #76469 Java fix: an empty folder says so plainly, a non-empty folder names exactly what was permanently deleted) \u2014 relay it verbatim, UNLESS you have concrete reason to believe the connected StyleBI deployment predates that fix (e.g. it applies an unforced non-empty folder delete without refusing), in which case fall back to this description's own warning instead (permanent, recursive, unrecoverable).`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA9,
        changes: CHANGES_SCHEMA9,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_viewsheet_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_viewsheet_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record. Required when the previewed plan's requiresAgentSignoff is true (any plan containing a viewsheet verb)."
        },
        acknowledgeIrreversibleDelete: {
          type: "boolean",
          description: 'required and must be true whenever changes contains any unitType="viewsheet" or unitType="worksheet" verb="delete" entry (no rollback in this cut for either), OR any unitType="folder" verb="delete" entry that preview_viewsheet_changes classified risk:"high" (a non-empty folder \u2014 Redmine #76469 fix; that entry also needs force:true). Not required when the plan contains no delete verb, or only an empty-folder delete (risk:"low"). Omit entirely when not required.'
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_viewsheet_changes for this plan. Call preview_viewsheet_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_viewsheet_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_viewsheet_changes first."
        );
      }
      const changes = normalizeViewsheetChanges(args.changes);
      const task = requireTask(args.task);
      const hasIrreversibleViewsheetDelete = changes.some((c) => (c.unitType === "viewsheet" || c.unitType === "worksheet") && c.verb === "delete");
      const hasFolderDelete = changes.some((c) => c.unitType === "folder" && c.verb === "delete");
      const planChanges = !hasIrreversibleViewsheetDelete && hasFolderDelete ? await resolveFolderDeleteRisks(deps, task, changes) : void 0;
      const acknowledgeIrreversibleDelete = requireAcknowledgeIrreversibleViewsheetDelete(
        changes,
        args.acknowledgeIrreversibleDelete,
        planChanges
      );
      const body = {
        task,
        changes,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeIrreversibleDelete !== void 0) {
        body.acknowledgeIrreversibleDelete = acknowledgeIrreversibleDelete;
      }
      try {
        return await deps.wizClient.post("/v1/admin/viewsheets/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_viewsheet_changes and apply_viewsheet_changes \u2014 the named viewsheet or folder, or its dependency graph, changed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_viewsheet_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeViewsheetTools(deps) {
  return [
    makeListViewsheetsTool(deps),
    makeGetViewsheetFolderTool(deps),
    makePreviewViewsheetChangesTool(deps),
    makeApplyViewsheetChangesTool(deps)
  ];
}

// src/tools/worksheetTools.ts
function makeListWorksheetsTool(deps) {
  return {
    name: "list_worksheets",
    description: `List worksheets the caller can read, in the resolved organization \u2014 both the shared repository tree and the caller's own per-user tree, recycle-bin entries excluded. organizationid is optional and defaults to the caller's own current organization. When security.exposedefaultorgtoall is enabled, the result may also include the host organization's own global worksheets for a caller in a different organization \u2014 that is expected, not a bug (the same filtering/visibility rules list_viewsheets already applies). There is no single-item get_worksheet tool (none exists on the underlying service) \u2014 filter this list's result by assetId, or rely on preview_viewsheet_changes' own structured not-found error on a bad assetId. To rename/delete/update a worksheet's alias or description, use preview_viewsheet_changes/apply_viewsheet_changes with unitType: "worksheet" \u2014 there is no separate worksheet preview/apply pair.`,
    inputSchema: {
      type: "object",
      properties: {
        organizationid: {
          type: "string",
          description: "optional; defaults to the caller's own current organization"
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const orgId = typeof args?.organizationid === "string" ? args.organizationid.trim() : "";
      const query = orgId === "" ? "" : `?organizationid=${encodeURIComponent(orgId)}`;
      return deps.wizClient.get(`/v1/admin/worksheets${query}`);
    }
  };
}
function makeWorksheetTools(deps) {
  return [makeListWorksheetsTool(deps)];
}

// src/tools/dashboardTools.ts
var CHANGES_SCHEMA10 = {
  type: "array",
  description: 'the proposed dashboard/dashboard-folder changes; each entry names unitType FIRST \u2014 set it correctly before adding other fields, since a field belonging to a different unit type or verb is refused loud, never silently dropped. A single plan may freely mix unitType="dashboard" and unitType="dashboardFolder" entries. This is the admin-configured "Portal Dashboard Tab" (global, owner omitted) / "User Portal Dashboard Tab" (owner set) feature \u2014 a genuine structural Repository-tree entry (name, description, bound viewsheet, enable/disable, folder ordering), NOT the self-service per-user "pin a dashboard" feature, which this tool surface does not cover at all.',
  items: {
    type: "object",
    properties: {
      unitType: {
        type: "string",
        description: 'exactly "dashboard" or "dashboardFolder" \u2014 no abbreviations accepted.'
      },
      verb: {
        type: "string",
        description: 'for unitType="dashboard": "create", "update", or "delete" (also accepts "add" as an alias for "create", "remove" as an alias for "delete"). For unitType="dashboardFolder": "reorder" (also accepts "arrange" as an alias) \u2014 the only verb for that unit type.'
      },
      owner: {
        type: "string",
        description: `an identity id (bare name, or "name:orgId"). Omit for the global scope ("Portal Dashboard Tab"); set it to target that user's own scope ("User Portal Dashboard Tab") instead. Always optional, for every verb \u2014 no existence check is performed on it, matching every other owner-scoped area in this plugin: a typo'd owner silently targets a possibly-nonexistent user's scope rather than being refused.`
      },
      name: {
        type: "string",
        description: `unitType="dashboard" only. For verb="create": required \u2014 the new dashboard's display name. For verb="update": optional \u2014 renaming the dashboard to this name; omitting it leaves the current name unchanged. Not used for verb="delete".`
      },
      oname: {
        type: "string",
        description: `unitType="dashboard", verb="update" or "delete" only, required \u2014 the EXISTING dashboard's current display name, used to locate it (as returned by list_portal_dashboards/get_portal_dashboard_settings). Not used for verb="create". You never need to add or strip any internal registry-key suffix yourself here \u2014 pass back the exact name a prior list/get call returned and it round-trips correctly whether the dashboard is global or per-user.`
      },
      description: {
        type: "string",
        description: 'unitType="dashboard", verb="create" or "update" only. For update: omitting this field leaves the current description unchanged; an explicit empty string ("") CLEARS it \u2014 omission and clearing are different outcomes. Not used for verb="delete".'
      },
      viewsheet: {
        type: "string",
        description: `unitType="dashboard", verb="create" (required) or "update" (optional) \u2014 the bound viewsheet's asset identifier, as returned by list_viewsheets (e.g. "1^128^__NULL__^Examples/Census^host-org"). Its shape is validated before any server call. Omitting it on update leaves the current binding unchanged. Not used for verb="delete".`
      },
      enable: {
        type: "boolean",
        description: 'unitType="dashboard", verb="create" or "update" only. Defaults to true on create when omitted. On update, omitting it leaves the current enabled state unchanged. Not used for verb="delete".'
      },
      dashboards: {
        type: "array",
        items: { type: "string" },
        description: 'unitType="dashboardFolder" only, required \u2014 the FULL replacement ordered list of dashboard names for this owner scope (this is a wholesale replacement, not an incremental move \u2014 reorder by submitting the complete list in the new order). Use the exact names returned by get_portal_dashboard_folder/list_portal_dashboards; no internal registry-key suffix handling is needed here either.'
      }
    },
    required: ["unitType", "verb"]
  }
};
var TASK_SCHEMA10 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function ownerQuery(owner) {
  return typeof owner === "string" && owner.trim() !== "" ? `owner=${encodeURIComponent(owner.trim())}` : "";
}
function makeListPortalDashboardsTool(deps) {
  return {
    name: "list_portal_dashboards",
    description: `List every dashboard currently registered in one Portal Dashboard scope: the global "Portal Dashboard Tab" (owner omitted) or one user's "User Portal Dashboard Tab" (owner set). This is the admin-configured Repository-tree feature (Redmine #76695), NOT the self-service per-user "pin a dashboard" feature. Each entry's name/oname is already the clean display name shown in Enterprise Manager \u2014 an internal registry-key suffix global entries carry on the wire is stripped here before you ever see it, and permissions always comes back null from this endpoint (use get_portal_dashboard_settings for a single entry's permissions).`,
    inputSchema: {
      type: "object",
      properties: {
        owner: {
          type: "string",
          description: 'optional; selects a per-user "User Portal Dashboard Tab" scope instead of the global one'
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const query = ownerQuery(args?.owner);
      const response = await deps.wizClient.get(`/v1/admin/dashboards${query === "" ? "" : `?${query}`}`);
      return stripDashboardGlobalSuffixFromList(response);
    }
  };
}
function makeGetPortalDashboardSettingsTool(deps) {
  return {
    name: "get_portal_dashboard_settings",
    description: 'Read one Portal Dashboard\'s full settings: name, description, bound viewsheet, enable/disable state, and permissions. `name` is the dashboard\'s current display name (as returned by list_portal_dashboards) \u2014 the underlying StyleBI query parameter is literally `path`, a naming holdover, but a dashboard has no nested folder path, only a flat display name. owner is optional and selects a per-user "User Portal Dashboard Tab" scope instead of the global "Portal Dashboard Tab".',
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string", description: "the dashboard's current display name" },
        owner: {
          type: "string",
          description: 'optional; selects a per-user "User Portal Dashboard Tab" scope instead of the global one'
        }
      },
      required: ["name"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const name = requireDashboardName(args ?? {});
      const owner = ownerQuery(args?.owner);
      const query = `path=${encodeURIComponent(name)}` + (owner === "" ? "" : `&${owner}`);
      const response = await deps.wizClient.get(`/v1/admin/dashboards/settings?${query}`);
      return stripDashboardGlobalSuffixFromSettings(response);
    }
  };
}
function makeGetPortalDashboardFolderTool(deps) {
  return {
    name: "get_portal_dashboard_folder",
    description: 'Read the ordered list of dashboard names for one Portal Dashboard folder scope \u2014 the "Arrange" ordering, and (for owner omitted) which dashboards are selected/visible in that scope. owner is optional and selects a per-user "User Portal Dashboard Tab" scope instead of the global "Portal Dashboard Tab"; either way, every name here is drawn from the shared global dashboard registry (a per-user scope orders/selects a subset of the SAME dashboards, it is not a separate registry of per-user dashboards) \u2014 pass these exact names straight into a dashboardFolder/"reorder" change without any suffix handling of your own.',
    inputSchema: {
      type: "object",
      properties: {
        owner: {
          type: "string",
          description: 'optional; selects a per-user "User Portal Dashboard Tab" scope instead of the global one'
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const query = ownerQuery(args?.owner);
      const response = await deps.wizClient.get(`/v1/admin/dashboards/folder${query === "" ? "" : `?${query}`}`);
      return stripDashboardGlobalSuffixFromFolder(response);
    }
  };
}
function makePreviewDashboardChangesTool(deps) {
  return {
    name: "preview_dashboard_changes",
    description: `Resolve a set of proposed Portal Dashboard creates/updates/deletes and dashboard-folder reorders into a reviewable plan WITHOUT changing anything on the server. requiresAgentSignoff is true whenever the plan contains a dashboard delete (risk:"high") or a rename (a dashboard update whose name differs from oname, also risk:"high") \u2014 a non-renaming update, a create, and a folder reorder are all risk:"low". requiresStorageBackup is always true for every verb in this area. UNLIKE the viewsheet area, a dashboard delete here only removes the Repository-tree registry binding (name/description/viewsheet reference) \u2014 it never deletes the bound viewsheet's own content \u2014 so it is fully compensable and there is no acknowledgeIrreversibleDelete flag anywhere in this area at all; apply_dashboard_changes only ever needs reviewOutcome, and only when requiresAgentSignoff is true. You MUST pass the returned planHash AND taskToken to apply_dashboard_changes: the server writes the audit record from the task narrative embedded in taskToken \u2014 the one reviewed here \u2014 never from whatever task text apply_dashboard_changes itself is called with.`,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA10, changes: CHANGES_SCHEMA10 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/dashboards/preview", {
        task: requireTask(args?.task),
        changes: normalizeDashboardChanges(args?.changes)
      });
    }
  };
}
function makeApplyDashboardChangesTool(deps) {
  return {
    name: "apply_dashboard_changes",
    description: `Apply a previewed Portal Dashboard change plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_dashboard_changes \u2014 the request body IS the plan; the server never trusts a stored one. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field. reviewOutcome is required whenever the previewed plan's requiresAgentSignoff is true (a dashboard delete, or an update that renames). There is no acknowledgeIrreversibleDelete flag in this area \u2014 a dashboard delete only removes the registry binding, never the bound viewsheet's own content, so it is always compensable and the server always queues it for rollback. Result status is one of: "applied", "rolled-back" (something failed and every applied change was undone), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied).`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA10,
        changes: CHANGES_SCHEMA10,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_dashboard_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_dashboard_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record. Required when the previewed plan's requiresAgentSignoff is true (a dashboard delete, or a rename update)."
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_dashboard_changes for this plan. Call preview_dashboard_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_dashboard_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_dashboard_changes first."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeDashboardChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      if (typeof args.reviewOutcome === "string" && args.reviewOutcome.trim() !== "") {
        body.reviewOutcome = args.reviewOutcome.trim();
      }
      try {
        return await deps.wizClient.post("/v1/admin/dashboards/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_dashboard_changes and apply_dashboard_changes \u2014 the named dashboard or folder order changed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_dashboard_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeDashboardTools(deps) {
  return [
    makeListPortalDashboardsTool(deps),
    makeGetPortalDashboardSettingsTool(deps),
    makeGetPortalDashboardFolderTool(deps),
    makePreviewDashboardChangesTool(deps),
    makeApplyDashboardChangesTool(deps)
  ];
}

// src/tools/licensingTools.ts
var ENTERPRISE_GATE_DISCLOSURE = "This whole area requires the StyleBI enterprise module. On a community-only deployment every tool here refuses cleanly with a named error \u2014 it does not crash and does not return a misleading empty or phantom result.";
var ALREADY_INSTALLED_ADD_DISCLOSURE = 'An "add" of a key that is already installed is NOT guaranteed to be treated as a harmless no-op \u2014 it may be refused outright by the server. Never assume a repeat add is always safe to retry; check list_license_keys/get_license_key first if you are unsure whether a key is already installed.';
async function getInstalledLicenseKeyCount(deps) {
  const response = await deps.wizClient.get("/v1/admin/licensing/keys");
  return Array.isArray(response?.keys) ? response.keys.length : 0;
}
function resolvePreviewDeLicensingWarning(totalInstalled, resolvedChanges) {
  const entries = Array.isArray(resolvedChanges) ? resolvedChanges : [];
  const removedCount = entries.filter((c) => c && c.proposedValue === null).length;
  const addedNewCount = entries.filter((c) => c && c.currentValue === null).length;
  const resultingInstalledCount = totalInstalled - removedCount + addedNewCount;
  return resultingInstalledCount <= 0;
}
function attachDeLicensingWarning(responseBody, deLicensingWarning) {
  if (responseBody !== null && typeof responseBody === "object") {
    responseBody.deLicensingWarning = deLicensingWarning;
  }
  return responseBody;
}
var CHANGES_SCHEMA11 = {
  type: "array",
  description: "the proposed license key add/remove actions; each entry names a verb and the key itself. List each (verb, key) pair at most once. A key named with BOTH verbs in the same request is refused as contradictory, not a duplicate \u2014 split into two plans applied in sequence, or drop one.",
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"add" (alias "install") or "remove" (aliases "uninstall"/"delete"). There is no "update"/"replace"/"set" verb for this resource \u2014 a license key is not edited in place; submitting one of those is refused loud, naming the two real verbs, never silently guessed as "add".'
      },
      key: {
        type: "string",
        description: 'the literal license key string \u2014 the ONLY identifier for this resource, no separate id/name field exists. `id`/`name`/`licenseKey` are accepted aliases for this same field. For "remove", pass the exact string as returned by list_license_keys/get_license_key.'
      }
    },
    required: ["verb", "key"]
  }
};
var TASK_SCHEMA11 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListLicenseKeysTool(deps) {
  return {
    name: "list_license_keys",
    description: "List every installed server license key, each as {key, type, valid, description}. `valid` and `type` are read directly off the real License object the server holds, NOT the Enterprise Manager console's own License Key page data \u2014 that page has a confirmed, independent defect (stylebi#76344) where its own `valid` field is always reported true regardless of the key's real validity; this tool's response does not inherit that defect. No filters \u2014 an installed-key set is expected to be small. License keys are whole-deployment configuration, not org-scoped \u2014 there is no orgId argument here or anywhere in this area. " + ENTERPRISE_GATE_DISCLOSURE,
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/licensing/keys");
    }
  };
}
function makeGetLicenseKeyTool(deps) {
  return {
    name: "get_license_key",
    description: "Look up one license key by its exact literal string and report whether it is currently installed. `key`/`id`/`name`/`licenseKey` are accepted aliases, trimmed, required. Returns {found: false} on a miss \u2014 a NORMAL answer, not an error, matching get_permission_grant's own precedent: most literal key strings a caller might ask about were never installed. Does NOT parse or validate a candidate key that isn't installed \u2014 that is preview_license_changes' own job for a prospective add, a materially different question (\"what would this resolve to if installed\" vs. this tool's \"is this currently installed, and what does it resolve to right now\"). " + ENTERPRISE_GATE_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: { key: { type: "string", description: "key, id, name, or licenseKey (all accepted)" } },
      required: ["key"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const key = requireLicenseKey(args);
      return deps.wizClient.get(`/v1/admin/licensing/key?key=${encodeURIComponent(key)}`);
    }
  };
}
function makePreviewLicenseChangesTool(deps) {
  return {
    name: "preview_license_changes",
    description: 'Resolve a set of proposed license key add/remove actions into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true) and always requires a Tier-2 storage backup (requiresStorageBackup is always true) \u2014 a license key change can gate every licensed feature/session/named-user count across the whole deployment for every organization at once. You MUST pass both the returned planHash AND taskToken to apply_license_changes: the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken -- the one reviewed here -- never from whatever task text apply_license_changes itself is called with. An "add" whose key fails to parse or resolves to an invalid license type is refused loud, naming the key, rather than being silently installed as a permanently-broken entry (the underlying product itself never rejects a malformed key at install time). A "remove" of a key that is not currently installed is refused loud, naming the key, rather than silently succeeding as a no-op. ' + ALREADY_INSTALLED_ADD_DISCLOSURE + " If the resolved plan would leave ZERO license keys installed (a full de-licensing event), the response carries a first-class deLicensingWarning:true field \u2014 surface this to the human explicitly before apply, since apply_license_changes will then require acknowledgeDelicensing:true. deLicensingWarning is computed by this tool from a separate live read of the installed-key count made alongside the preview call, so there is a small race window against a concurrent add/remove elsewhere \u2014 acceptable for an advisory-only preview signal, since the real enforcement gate is apply_license_changes' own acknowledgeDelicensing requirement, re-resolved fresh at apply time. " + ENTERPRISE_GATE_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA11, changes: CHANGES_SCHEMA11 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const changes = normalizeLicenseChanges(args?.changes);
      const totalInstalled = await getInstalledLicenseKeyCount(deps);
      const plan = await deps.wizClient.post("/v1/admin/licensing/preview", {
        task,
        changes
      });
      return attachDeLicensingWarning(plan, resolvePreviewDeLicensingWarning(totalInstalled, plan?.changes));
    }
  };
}
function makeApplyLicenseChangesTool(deps) {
  return {
    name: "apply_license_changes",
    description: `Apply a previewed license key change plan. Pass back the same changes you previewed, the planHash AND the taskToken from preview_license_changes \u2014 the request body IS the plan; the server never trusts a stored one. It re-resolves the changes, recomputes the hash, and refuses on mismatch. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_license_changes. Every plan in this area requires a non-blank reviewOutcome. Pass acknowledgeDelicensing:true ONLY when the freshly re-resolved plan would leave zero license keys installed \u2014 omit it otherwise; if it is required and missing, the call is refused loud naming the field and the resulting count (this is an advisory acknowledgement, not a hard block: nothing in the underlying product itself forbids zero installed keys, and full de-licensing can be an intentional operator action). Result status is one of: "applied", "rolled-back" (something failed and every already-applied change was undone in reverse \u2014 an "add"'s inverse, removing the same key, is exact; a "remove"'s inverse, re-adding the same key, is real but NOT exact \u2014 a re-added key can land on a different cluster node than before, disclosed as a first-class \`advisory\` field on that change's rollback outcome, relay it to the human verbatim), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry, it names the specific key(s) left in an unknown state), or "conflict" (the plan drifted since preview \u2014 a key was installed/removed by someone else in between \u2014 nothing was applied). The response also carries the same first-class deLicensingWarning boolean preview_license_changes returns \u2014 here it is computed from a fresh installed-key count read immediately AFTER the apply resolves (ground truth, not a projection), so it reflects the real post-apply state even on a partial rollback or rollback-failed outcome. Omitted (not sent as false) if that follow-up read itself fails \u2014 the apply has already completed by that point, so this advisory-only field is simply left off rather than guessed. ` + ALREADY_INSTALLED_ADD_DISCLOSURE + " " + ENTERPRISE_GATE_DISCLOSURE + " Reading back a licensing changeset (get_changeset) is still enterprise-only like every other area's.",
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA11,
        changes: CHANGES_SCHEMA11,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_license_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_license_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        },
        acknowledgeDelicensing: {
          type: "boolean",
          description: "required and must be true ONLY when the freshly re-resolved plan would leave zero license keys installed. Omit entirely for any other plan."
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_license_changes for this plan. Call preview_license_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_license_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_license_changes first. If preview_license_changes's response contained no taskToken at all, this StyleBI deployment predates the pinned-audit contract and must be upgraded \u2014 do not retry."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeLicenseChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      const acknowledgeDelicensing = normalizeAcknowledgeDelicensing(args.acknowledgeDelicensing);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeDelicensing !== void 0) {
        body.acknowledgeDelicensing = acknowledgeDelicensing;
      }
      try {
        const result = await deps.wizClient.post("/v1/admin/licensing/apply", body);
        try {
          const totalInstalledAfter = await getInstalledLicenseKeyCount(deps);
          return attachDeLicensingWarning(result, totalInstalledAfter <= 0);
        } catch {
          return result;
        }
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_license_changes and apply_license_changes \u2014 a named key was installed or removed by someone else (or another cluster node) in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_license_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeLicensingTools(deps) {
  return [
    makeListLicenseKeysTool(deps),
    makeGetLicenseKeyTool(deps),
    makePreviewLicenseChangesTool(deps),
    makeApplyLicenseChangesTool(deps)
  ];
}

// src/tools/presentationTools.ts
var SUB_MODEL_LIST = PRESENTATION_SUB_MODELS.join(", ");
var NOT_ENTERPRISE_GATED_DISCLOSURE = "Unlike schedule tasks/permissions/identities/data sources/viewsheets/licensing, this area is NOT enterprise-gated \u2014 every presentation tool works on a community-only deployment. The one exception is the same as every other area's: reading back a presentation changeset (get_changeset) is still enterprise-only and 404s on community.";
var AMBIENT_SCOPE_DISCLOSURE = `scope="organization" always means the CALLING PRINCIPAL'S OWN organization, resolved ambiently from the caller \u2014 there is no orgId argument anywhere in this area, and no way to target a DIFFERENT org's presentation settings through this tool at all.`;
var WHOLE_LIST_GUARDS = [
  { subModel: "portalIntegration", field: "tabs", singular: "tab", plural: "tabs" },
  { subModel: "viewsheetToolbar", field: "options", singular: "option", plural: "options" },
  { subModel: "exportMenu", field: "vsOptions", singular: "export option", plural: "export options" }
];
async function validateWholeListSubModels(deps, changes) {
  for (let i = 0; i < changes.length; i++) {
    const change = changes[i];
    const guard = WHOLE_LIST_GUARDS.find((g) => g.subModel === change.subModel);
    if (!guard) {
      continue;
    }
    const values = change.spec[guard.field];
    if (!Array.isArray(values)) {
      continue;
    }
    const current = await deps.wizClient.get(
      `/v1/admin/presentation/settings?scope=${change.scope}&subModel=${guard.subModel}`
    );
    const liveValues = current?.subModels?.[guard.subModel]?.[guard.field];
    const currentValues = Array.isArray(liveValues) ? liveValues : [];
    if (values.length < currentValues.length) {
      throw new Error(
        `changes[${i}].spec.${guard.field}: this array has ${values.length} entries, but a fresh read of the live ${guard.subModel} settings (scope="${change.scope}") currently has ${currentValues.length} ${guard.plural}. The underlying server write REPLACES THE WHOLE ${guard.singular.toUpperCase()} LIST AT ONCE \u2014 a shorter array would silently DROP every ${guard.singular} not included here, it does not patch only the ones you named. Call get_presentation_settings(scope, subModel:"${guard.subModel}") first and include every current ${guard.singular} in spec.${guard.field} in full, even the ones you are not changing.`
      );
    }
  }
}
var CHANGES_SCHEMA12 = {
  type: "array",
  description: 'the proposed presentation sub-model updates; each entry names a subModel FIRST (one of the 16 catalogued sub-models, see get_presentation_settings), then a scope, then a partial-field spec object for THAT sub-model only. verb is always "update" \u2014 presentation sub-models are never created or destroyed, every one of the 16 always exists.',
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"update" only (also accepts "modify"/"edit" as aliases). "create"/"delete" are refused loud, naming why, never silently coerced to "update".'
      },
      subModel: {
        type: "string",
        description: `one of the 16 catalogued sub-models, EXACT case-sensitive match, no fuzzy/prefix matching: ${SUB_MODEL_LIST}. lookAndFeel/welcomePage/loginBanner/portalIntegration are storage-scope \u2014 updating any of them requires acknowledgeIrreversibleUpdate:true on apply_presentation_changes (see below). fontMapping/ai are GLOBAL-ONLY \u2014 scope="organization" is refused loud for either.`
      },
      scope: {
        type: "string",
        description: '"global" or "organization" (also accepts "org" as an alias for "organization"). REQUIRED, no default \u2014 silently guessing which scope you meant is not safe here. ' + AMBIENT_SCOPE_DISCLOSURE
      },
      spec: {
        type: "object",
        description: 'required \u2014 a PARTIAL-FIELD object for the named subModel only, never a full-document passthrough. For portalIntegration, spec.tabs, if given, MUST be every current tab in full: the underlying write replaces the whole tab list at once, and this tool refuses a shorter array before the server is ever called, naming the live current tab count. For viewsheetToolbar, spec.options is likewise the WHOLE option list, the same whole-value-only convention (no per-element write path exists underneath either) \u2014 this tool refuses a shorter array before the server is ever called, naming the live current option count, the same enforcement portalIntegration.tabs gets. For exportMenu, spec.vsOptions is likewise the WHOLE export-option list, the same whole-value-only convention \u2014 this tool refuses a shorter array before the server is ever called, naming the live current export option count, the same enforcement portalIntegration.tabs/ viewsheetToolbar.options gets. For lookAndFeel, logoFile/faviconFile/viewsheetFile are {name, content} objects; a name containing "/", "\\\\", or ".." is refused loud \u2014 the underlying write path does not sanitize it before it reaches storage. userformatFile is also a {name, content} object on lookAndFeel \u2014 a user-defined number/date format file, written to a fixed "userformat.xml" path; its name is never read by the write path, so it has no filename sanitization check (there is nothing to sanitize).'
      }
    },
    required: ["verb", "subModel", "scope", "spec"]
  }
};
var TASK_SCHEMA12 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeGetPresentationSettingsTool(deps) {
  return {
    name: "get_presentation_settings",
    description: "Read one or all 16 presentation sub-models: formats, dashboard, viewsheetToolbar, lookAndFeel, welcomePage, loginBanner, portalIntegration, pdfGeneration, exportMenu, fontMapping, share, composerMessage, time, dataSourceVisibility, webMap, ai. Omit subModel to get all 16 at once; pass subModel to get just that one's current value (exact, case-sensitive match, no fuzzy/prefix matching \u2014 an unrecognized name is refused loud, naming all 16 valid values). scope is REQUIRED, no default. " + AMBIENT_SCOPE_DISCLOSURE + ' fontMapping/ai are GLOBAL-ONLY: an organization-scoped read of "ai" returns null for that sub-model rather than an error (the underlying service has no org-scope concept at all for it), so a null "ai" value under scope="organization" is expected, not a sign of anything missing \u2014 re-read with scope="global" to see the real value. "reset" is not supported by this area (deferred pending stylebi#76341, a checkPermission escalation gap in the underlying resetSettings) \u2014 there is no reset tool here. ' + NOT_ENTERPRISE_GATED_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: {
        scope: {
          type: "string",
          description: '"global" or "organization" (also accepts "org"). Required, no default.'
        },
        subModel: {
          type: "string",
          description: `optional; one of the 16 names (exact match): ${SUB_MODEL_LIST}`
        }
      },
      required: ["scope"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const scope = normalizeScope(args?.scope, "arguments");
      let query = `?scope=${scope}`;
      if (args?.subModel !== void 0 && args?.subModel !== null) {
        const subModel = requireSubModel(args.subModel, "arguments");
        query += `&subModel=${encodeURIComponent(subModel)}`;
      }
      return deps.wizClient.get(`/v1/admin/presentation/settings${query}`);
    }
  };
}
function makePreviewPresentationChangesTool(deps) {
  return {
    name: "preview_presentation_changes",
    description: `Resolve a set of proposed presentation sub-model updates into a reviewable plan WITHOUT changing anything on the server. Response always has requiresAgentSignoff:true and requiresStorageBackup:true, unconditionally, for every plan in this area regardless of which sub-models are touched. You MUST pass the returned planHash to apply_presentation_changes. The hash covers the CURRENT value of every field the named sub-model's write would touch, not just the ones your spec names \u2014 a concurrent edit to any field inside the same sub-model perturbs the hash, even one your spec did not mention. Response always carries a taskToken bound to this exact planHash+task. You MUST pass both planHash and taskToken to apply_presentation_changes: the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken \u2014 the one reviewed here \u2014 never from whatever task text apply_presentation_changes itself is called with. READ THIS BEFORE PREVIEWING A portalIntegration UPDATE WITH spec.tabs: this tool fetches a fresh read of the live tab list before ever sending your request, and refuses loud, naming the live count, if spec.tabs has fewer entries \u2014 the underlying write replaces the whole tab list at once, so a shorter array would silently drop every tab you did not include. THE SAME APPLIES TO A viewsheetToolbar UPDATE WITH spec.options: this tool fetches a fresh read of the live option list first, and refuses loud, naming the live count, if spec.options has fewer entries \u2014 the underlying write replaces the whole option list at once too. THE SAME APPLIES TO AN exportMenu UPDATE WITH spec.vsOptions: this tool fetches a fresh read of the live export-option list first, and refuses loud, naming the live count, if spec.vsOptions has fewer entries \u2014 the underlying write replaces the whole export-option list at once too. READ THIS BEFORE PREVIEWING A lookAndFeel UPDATE: logoFile/faviconFile/viewsheetFile names containing "/", "\\\\", or ".." are refused loud before the server is ever called \u2014 the underlying write path does not sanitize them. userformatFile is also a {name, content} object on lookAndFeel \u2014 a user-defined number/date format file, written to a fixed "userformat.xml" path; its name is never read by the write path, so no filename check applies to it. fontMapping/ai reject scope="organization" outright, naming the sub-model, rather than silently applying to the global layer (their underlying write silently drops an org-scoped change today). "reset" is not supported \u2014 there is no reset verb in this area (deferred pending stylebi#76341). ` + AMBIENT_SCOPE_DISCLOSURE + " " + NOT_ENTERPRISE_GATED_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA12, changes: CHANGES_SCHEMA12 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const changes = normalizePresentationChanges(args?.changes);
      await validateWholeListSubModels(deps, changes);
      return deps.wizClient.post("/v1/admin/presentation/preview", {
        task: requireTask(args?.task),
        changes
      });
    }
  };
}
function makeApplyPresentationChangesTool(deps) {
  return {
    name: "apply_presentation_changes",
    description: `Apply a previewed presentation change plan. Pass back the SAME task and changes you previewed plus the planHash AND the taskToken from preview_presentation_changes \u2014 the request body IS the plan; the server never trusts a stored one. It re-resolves the changes, recomputes the hash, and refuses on mismatch. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_presentation_changes. Every plan in this area requires a non-blank reviewOutcome. If changes contains any update to lookAndFeel/welcomePage/loginBanner/portalIntegration you MUST also pass acknowledgeIrreversibleUpdate:true \u2014 those 4 (of 16) sub-models have NO live rollback in this cut (a whole-file DataSpace rewrite with no before-image captured), and the Tier-2 storage snapshot is the only recovery path, manual and offline. Omit the flag entirely for a plan touching only the other 12 (value-scope) sub-models. Result status is one of: "applied", "rolled-back" (something failed and every undoable change \u2014 the 12 value-scope sub-models only, never the 4 storage-scope ones \u2014 was reverted via a per-key SreeEnv write-back), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted since preview, nothing was applied). A storage-scope entry's own outcome is always "applied" or "failed", never individually rolled back, even when the rest of the plan rolls back around it. ` + AMBIENT_SCOPE_DISCLOSURE + " " + NOT_ENTERPRISE_GATED_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA12,
        changes: CHANGES_SCHEMA12,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_presentation_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_presentation_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        },
        acknowledgeIrreversibleUpdate: {
          type: "boolean",
          description: "required and must be true whenever changes contains an update to lookAndFeel/welcomePage/loginBanner/portalIntegration \u2014 those have no live rollback in this cut. Omit entirely for a plan touching only value-scope sub-models."
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_presentation_changes for this plan. Call preview_presentation_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_presentation_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_presentation_changes first. If preview_presentation_changes's response contained no taskToken at all, this StyleBI deployment predates the pinned-audit contract and must be upgraded \u2014 do not retry."
        );
      }
      const changes = normalizePresentationChanges(args.changes);
      await validateWholeListSubModels(deps, changes);
      const acknowledgeIrreversibleUpdate = requireAcknowledgeIrreversibleUpdate(
        changes,
        args.acknowledgeIrreversibleUpdate
      );
      const body = {
        task: requireTask(args.task),
        changes,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeIrreversibleUpdate !== void 0) {
        body.acknowledgeIrreversibleUpdate = acknowledgeIrreversibleUpdate;
      }
      try {
        return await deps.wizClient.post("/v1/admin/presentation/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_presentation_changes and apply_presentation_changes \u2014 a field inside a named sub-model changed (by someone else, or through Enterprise Manager directly) in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call preview_presentation_changes again and use THAT response's planHash and taskToken \u2014 the returned `plan` here has no usable taskToken (the server never returns one in a conflict, so a stale narrative can't be replayed), so reusing anything from it will only produce another conflict."
          };
        }
        throw err;
      }
    }
  };
}
function makePresentationTools(deps) {
  return [
    makeGetPresentationSettingsTool(deps),
    makePreviewPresentationChangesTool(deps),
    makeApplyPresentationChangesTool(deps)
  ];
}

// src/tools/shapeTools.ts
var AMBIENT_SCOPE_DISCLOSURE2 = `scope="organization" always means the CALLING PRINCIPAL'S OWN organization, resolved ambiently from the caller \u2014 there is no orgId argument anywhere in this area, matching Presentation's own convention. On a deployment that is NOT multi-tenant, "organization" and "global" resolve to the literal SAME shapes directory \u2014 this is the underlying server's own existing fallback behavior (a non-multi-tenant deployment's org-shapes directory falls back to the global one), not a bug in this tool, so seeing the same shapes under both scopes on such a deployment is expected, not a sign anything is broken.`;
var NOT_ENTERPRISE_GATED_DISCLOSURE2 = "This area is NOT enterprise-gated \u2014 every custom-shapes tool works on a community-only deployment, the same as the rest of Presentation, even though it is served by its own, separate DataSpace-backed controller rather than a lookAndFeel field (apply_presentation_changes cannot touch shapes; there is no shape field on any of the 16 presentation sub-models).";
var CHANGES_SCHEMA13 = {
  type: "array",
  description: `the proposed shape uploads/deletes; each entry is { verb, scope, name, subPath?, content? }. verb is "upload" (create-or-overwrite \u2014 there is no separate non-overwriting create) or "delete". Every entry in this area is fully compensable: an upload's prior bytes (if any existed at that name) and a delete's removed bytes are both captured unconditionally, so there is no acknowledgeIrreversible* flag anywhere in this area.`,
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"upload" (also accepts "add") or "delete" (also accepts "remove")'
      },
      scope: {
        type: "string",
        description: '"global" or "organization" (also accepts "org"). Required, no default. ' + AMBIENT_SCOPE_DISCLOSURE2
      },
      name: {
        type: "string",
        description: `the shape's own full filename (e.g. "arrow.svg") \u2014 a bare name only, no "/" or "\\\\"; unlike lookAndFeel's logoFile/faviconFile (where only a suffix survives), a shape's WHOLE filename is written verbatim and is its own display identity in the shapes tree/palette.`
      },
      subPath: {
        type: "string",
        description: `optional subfolder inside the shapes tree that name is nested under; omit for a shape directly at the scope's shapes root. Same DataSpace-relative-path safety rules as every other path-shaped field in this plugin (no leading "/", no ".." segment).`
      },
      content: {
        type: "string",
        description: `verb="upload" only \u2014 the shape file's full bytes, base64-encoded. Binary-safe (unlike Stored Assets' UTF-8-text-only write verb), since shape images are commonly binary (GIF/PNG) as well as SVG.`
      }
    },
    required: ["verb", "scope", "name"]
  }
};
var TASK_SCHEMA13 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListCustomShapesTool(deps) {
  return {
    name: "list_custom_shapes",
    description: "List the Custom Shapes DataSpace tree (Presentation > Look and Feel's shape library) for one scope. Returns { nodes: [{ label, path, folder, children? }] }. scope is REQUIRED, no default. " + AMBIENT_SCOPE_DISCLOSURE2 + " " + NOT_ENTERPRISE_GATED_DISCLOSURE2,
    inputSchema: {
      type: "object",
      properties: {
        scope: {
          type: "string",
          description: '"global" or "organization" (also accepts "org"). Required, no default.'
        },
        subPath: {
          type: "string",
          description: "optional subfolder inside the shapes tree to list instead of the root"
        }
      },
      required: ["scope"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const scope = normalizeScope(args?.scope, "arguments");
      const subPath = requireShapeSubPath(args?.subPath, "arguments");
      let query = `?scope=${scope}`;
      if (subPath !== void 0) {
        query += `&path=${encodeURIComponent(subPath)}`;
      }
      return deps.wizClient.get(`/v1/admin/shapes/tree${query}`);
    }
  };
}
function makePreviewCustomShapeChangesTool(deps) {
  return {
    name: "preview_custom_shape_changes",
    description: `Resolve a set of proposed Custom Shape uploads/deletes into a reviewable plan WITHOUT changing anything on the server. Response always has requiresAgentSignoff:true and requiresStorageBackup:true, unconditionally, for every entry \u2014 there is no low-risk verb in this area. Every entry is fully compensable by construction (an upload's prior bytes, if any existed at that name, and a delete's removed bytes are both captured unconditionally), so apply_custom_shape_changes never needs an acknowledgeIrreversible* flag \u2014 a stronger guarantee than Stored Assets/Presentation give their own destructive verbs. An "upload" that collides with an existing shape at that name is disclosed in the plan text as an overwrite, not refused \u2014 there is no separate non-overwriting create verb here. A "delete" targeting a name that does not exist is refused loud, not silently a no-op. Response always carries a planHash and a taskToken bound to it. You MUST pass both to apply_custom_shape_changes \u2014 the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken, never from whatever task text apply_custom_shape_changes itself is called with. ` + AMBIENT_SCOPE_DISCLOSURE2 + " " + NOT_ENTERPRISE_GATED_DISCLOSURE2,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA13, changes: CHANGES_SCHEMA13 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/shapes/preview", {
        task: requireTask(args?.task),
        changes: normalizeShapeChanges(args?.changes)
      });
    }
  };
}
function makeApplyCustomShapeChangesTool(deps) {
  return {
    name: "apply_custom_shape_changes",
    description: `Apply a previewed Custom Shape change plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_custom_shape_changes. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field. Every plan in this area requires a non-blank reviewOutcome. There is no acknowledgeIrreversible* flag anywhere in this area \u2014 every entry is fully compensable by construction (see preview_custom_shape_changes). Result status is one of: "applied", "rolled-back" (something failed and every entry was reverted \u2014 including a delete, since its removed bytes were captured at preview time), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted since preview, e.g. someone else uploaded/deleted the same name in between \u2014 nothing was applied). ` + AMBIENT_SCOPE_DISCLOSURE2 + " " + NOT_ENTERPRISE_GATED_DISCLOSURE2,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA13,
        changes: CHANGES_SCHEMA13,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_custom_shape_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_custom_shape_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_custom_shape_changes for this plan. Call preview_custom_shape_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_custom_shape_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_custom_shape_changes first."
        );
      }
      const changes = normalizeShapeChanges(args.changes);
      const body = {
        task: requireTask(args.task),
        changes,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/shapes/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_custom_shape_changes and apply_custom_shape_changes \u2014 a named shape was uploaded, overwritten, or deleted by someone else in between (or this taskToken was issued for a different plan). NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call preview_custom_shape_changes again and apply THAT plan's own planHash/taskToken."
          };
        }
        throw err;
      }
    }
  };
}
function makeShapeTools(deps) {
  return [
    makeListCustomShapesTool(deps),
    makePreviewCustomShapeChangesTool(deps),
    makeApplyCustomShapeChangesTool(deps)
  ];
}

// src/tools/themeTools.ts
import fs4 from "fs";
import os2 from "os";
import nodePath3 from "path";
var ENTERPRISE_GATE_NOTE = "This area requires the StyleBI enterprise module for every tool, reads included \u2014 a community-only deployment refuses cleanly (404) rather than returning a misleading empty list or silently no-op'd success, the same posture Licensing takes for its own area.";
var CHANGES_SCHEMA14 = {
  type: "array",
  description: `the proposed theme creates/updates/deletes; each entry is { verb, id?, spec? }. List each id at most once. IMPORTANT: on verb="update", spec.name is unconditionally APPLIED when present \u2014 there is no separate rename flag \u2014 so resupply the theme's CURRENT name if you don't intend to rename it; omitting name entirely leaves it unchanged. spec.jar is a whole theme JAR archive (base64), NOT raw CSS text \u2014 to hand-edit individual CSS variables, use spec.portalCss/spec.emCss instead of building a jar; omitting jar/portalCss/emCss on an update leaves that content untouched (this is the one part of this area's "omitted means unchanged" contract that is NOT symmetric with name's own behavior).`,
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"create", "delete", or "update" ("create"/"delete" also accept "add"/"remove"; "update" has no alias)'
      },
      id: {
        type: "string",
        description: `required for verb=delete/update, forbidden for verb=create (the id is derived server-side and may differ from any id you propose \u2014 the server de-conflicts a colliding id rather than refusing it; check the preview's proposedValue for the actual id that will be used). A bare, opaque, server-assigned id \u2014 never "name:orgId"-qualified the way an identity id can be.`
      },
      spec: {
        type: "object",
        description: "required for verb=create/update, forbidden for verb=delete. Shape: {name?, global?, defaultThemeGlobal?, defaultThemeOrg?, jar?, portalCss?, emCss?}. name is required non-blank for create, optional for update (omit = unchanged, see the callout above). global/defaultThemeGlobal/defaultThemeOrg are booleans; omit any of them on update to leave it unchanged. jar is { name, content } (content is base64 of a full JAR archive). portalCss/emCss are { variables: [{ name, value }] }. An explicit null on update is refused loud (it cannot be distinguished from omitted once sent). A field outside this set (e.g. role, unitType \u2014 fields that belong to a DIFFERENT area's tool-call shape) is refused loud, naming it, never silently dropped."
      }
    },
    required: ["verb"]
  }
};
var TASK_SCHEMA14 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListThemesTool(deps) {
  return {
    name: "list_themes",
    description: `List every theme currently defined. ${ENTERPRISE_GATE_NOTE}`,
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/themes");
    }
  };
}
function makeGetThemeTool(deps) {
  return {
    name: "get_theme",
    description: `Read one theme by id, including its extracted portalCss/emCss variables. Returns { found: false, ... } when no theme has that id \u2014 this is a NORMAL answer, not an error, matching get_permission_grant's own not-found convention. ${ENTERPRISE_GATE_NOTE}`,
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "the theme's id, from list_themes" } },
      required: ["id"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireThemeId(args?.id, "arguments");
      return deps.wizClient.get(`/v1/admin/themes/${encodeURIComponent(id)}`);
    }
  };
}
function makePreviewThemeChangesTool(deps) {
  return {
    name: "preview_theme_changes",
    description: `Resolve a set of proposed theme creates/updates/deletes into a reviewable plan WITHOUT changing anything on the server. Every verb in this area is always high risk (requiresAgentSignoff is always true) and always requires a Tier-2 storage backup (requiresStorageBackup is always true), the same unconditional treatment identities/permissions/licensing give their own areas. For a create, proposedValue shows the ACTUAL id the server will assign (themes silently de-conflict a colliding id rather than refusing it) and, if spec.jar was given, the REAL extracted CSS variables from that jar \u2014 not just an opaque "a jar was provided" placeholder \u2014 so review the preview's proposedValue rather than assuming your own proposed id/spec is exactly what gets applied. You MUST pass the returned planHash AND taskToken to apply_theme_changes: the server re-resolves the plan and refuses with a conflict if anything drifted, and it writes the audit record from the task narrative embedded in taskToken \u2014 the one reviewed here \u2014 never from whatever task text apply_theme_changes itself is called with. ${ENTERPRISE_GATE_NOTE}`,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA14, changes: CHANGES_SCHEMA14 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/themes/preview", {
        task: requireTask(args?.task),
        changes: normalizeThemeChanges(args?.changes)
      });
    }
  };
}
function makeApplyThemeChangesTool(deps) {
  return {
    name: "apply_theme_changes",
    description: `Apply a previewed theme change plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_theme_changes. Every plan in this area requires a non-blank reviewOutcome. The task field is required but no longer needs to match what you previewed: the audit record is written from taskToken's embedded narrative, not from this field, so it always reflects what was actually reviewed at preview_theme_changes. Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted \u2014 a rolled-back delete's undo may recreate the theme under a DIFFERENT id if something else took the freed id in between, disclosed as an advisory; a rolled-back update's undo re-resolves the theme by its post-update id, which differs from the original if the update itself renamed it), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied). ${ENTERPRISE_GATE_NOTE}`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA14,
        changes: CHANGES_SCHEMA14,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_theme_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_theme_changes for this exact plan. The server writes the audit record from the task narrative embedded in this token, not from this call's own task field \u2014 so the audit trail always reflects what was reviewed at preview, even if this call's task text differs."
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record; always required in this area"
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_theme_changes for this plan. Call preview_theme_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_theme_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_theme_changes first."
        );
      }
      const body = {
        task: requireTask(args.task),
        changes: normalizeThemeChanges(args.changes),
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/themes/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_theme_changes and apply_theme_changes \u2014 one of the named themes was created, changed, or removed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_theme_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function defaultDownloadDir2() {
  return nodePath3.join(os2.homedir(), "Downloads");
}
function claimUniqueDest2(dir, fileName, fromPath) {
  const ext = nodePath3.extname(fileName);
  const base = fileName.slice(0, fileName.length - ext.length);
  for (let i = 0; i < 1e3; i++) {
    const candidate = i === 0 ? nodePath3.join(dir, fileName) : nodePath3.join(dir, `${base} (${i})${ext}`);
    try {
      fs4.linkSync(fromPath, candidate);
      fs4.unlinkSync(fromPath);
      return candidate;
    } catch (err) {
      if (err.code !== "EEXIST") throw err;
    }
  }
  throw new Error(
    `could not find a free filename for '${fileName}' in '${dir}' after 1000 attempts.`
  );
}
function makeDownloadThemeJarTool(deps) {
  return {
    name: "download_theme_jar",
    description: `Download one theme's uploaded JAR archive to local disk (mirrors download_stored_asset's shape). Returns { filePath, fileName, mimeType, byteLength } \u2014 never the file's own bytes, which keeps a multi-MB download cheap regardless of size. This is a bare action: no preview/apply/planHash \u2014 the download itself changes nothing on the server. Refused loud, naming the theme, if it has no uploaded jar (the underlying product's own download endpoint silently returns an empty body in this case; this tool does not reproduce that). ${ENTERPRISE_GATE_NOTE}`,
    inputSchema: {
      type: "object",
      properties: {
        id: { type: "string", description: "the theme's id, from list_themes" },
        savePath: {
          type: "string",
          description: "directory to save the file into (not a full file path \u2014 the server's own filename is used). Defaults to this machine's Downloads folder. Created if it doesn't already exist."
        }
      },
      required: ["id"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const id = requireThemeId(args?.id, "arguments");
      const savePath = typeof args?.savePath === "string" && args.savePath.trim() !== "" ? args.savePath : void 0;
      const dir = savePath ?? defaultDownloadDir2();
      const provisionalDest = nodePath3.join(
        dir,
        `theme-jar-${Date.now()}-${Math.random().toString(36).slice(2)}.tmp`
      );
      const downloaded = await deps.wizClient.downloadToFile(
        `/v1/admin/themes/${encodeURIComponent(id)}/jar`,
        provisionalDest
      );
      const fileName = downloaded.fileName ?? `${id}.jar`;
      const filePath = claimUniqueDest2(dir, fileName, provisionalDest);
      return {
        filePath,
        fileName: nodePath3.basename(filePath),
        mimeType: downloaded.mimeType,
        byteLength: downloaded.byteLength,
        summary: `Downloaded theme "${id}"'s jar to ${filePath} (${downloaded.byteLength.toLocaleString()} bytes).`
      };
    }
  };
}
function makeThemeTools(deps) {
  return [
    makeListThemesTool(deps),
    makeGetThemeTool(deps),
    makePreviewThemeChangesTool(deps),
    makeApplyThemeChangesTool(deps),
    makeDownloadThemeJarTool(deps)
  ];
}

// src/tools/recycleBinTools.ts
var SCOPE_DISCLOSURE = "IMPORTANT: this only recovers assets deleted through a HUMAN path (Enterprise Manager, Composer, Portal) \u2014 a delete made through apply_viewsheet_changes bypasses the recycle bin entirely and is not recoverable through this area at all.";
var CHANGES_SCHEMA15 = {
  type: "array",
  description: "the proposed restore/purge actions against already-recycled entries. There is no unitType discriminator here (unlike the Viewsheets area) \u2014 a recycle-bin entry's own type is intrinsic to the already-recycled item and the server resolves dispatch from path alone.",
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: '"restore" (put the asset/folder back at its originalPath) or "purge" (permanently delete it from the recycle bin \u2014 the EM "Empty Recycle Bin" action, scoped to just this one entry; "remove"/"delete" are accepted as aliases for "purge").'
      },
      path: {
        type: "string",
        description: 'required \u2014 the recycle-bin storage key (e.g. "Recycle Bin/<uuid>"), exactly as returned by list_recycle_bin_entries/get_recycle_bin_entry. Never hand-constructed.'
      },
      overwrite: {
        type: "boolean",
        description: 'verb="restore" only, default false. If something already occupies the destination originalPath, overwrite:false makes preview_recycle_bin_changes REFUSE THE PLAN LOUD, naming the occupied path \u2014 it does NOT silently no-op the way the underlying primitive does on its own. overwrite:true accepts the collision, but PERMANENTLY DESTROYS whatever currently occupies that path first, bypassing the recycle bin \u2014 this is the single most dangerous behavior in this whole area, more dangerous than purge itself. Not used for verb="purge" \u2014 refused loud if set there.'
      }
    },
    required: ["verb", "path"]
  }
};
var TASK_SCHEMA15 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeListRecycleBinEntriesTool(deps) {
  return {
    name: "list_recycle_bin_entries",
    description: "List every entry currently in the recycle bin the caller can see (the same per-entry ADMIN permission filter the Enterprise Manager Recycle Bin page itself applies \u2014 a site admin who is not personally ADMIN on a given resource/owner sees the same restricted list EM would show them). No arguments; recycle bin storage is already scoped to the caller's own current organization. " + SCOPE_DISCLOSURE,
    inputSchema: { type: "object", properties: {} },
    call: async () => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.get("/v1/admin/recycle-bin");
    }
  };
}
function makeGetRecycleBinEntryTool(deps) {
  return {
    name: "get_recycle_bin_entry",
    description: "Read one recycle bin entry by its storage key (path). found:false is a normal answer, not an error \u2014 true both when nothing is at that path AND when an entry exists there but is not visible to the caller (these two cases are indistinguishable by design, the same way the underlying permission filter works). " + SCOPE_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: {
        path: {
          type: "string",
          description: "the recycle-bin storage key, exactly as returned by list_recycle_bin_entries"
        }
      },
      required: ["path"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const path2 = requireRecycleBinPath(args?.path, "args");
      return deps.wizClient.get(`/v1/admin/recycle-bin/entry?path=${encodeURIComponent(path2)}`);
    }
  };
}
function makePreviewRecycleBinChangesTool(deps) {
  return {
    name: "preview_recycle_bin_changes",
    description: `Resolve a set of proposed restore/purge actions into a reviewable plan WITHOUT changing anything on the server. A plan containing any purge entry is risk:"high" and requiresAgentSignoff:true (irreversible, same treatment as a viewsheet delete elsewhere in this plugin) \u2014 a restore-only plan is risk:"low" UNLESS a restore entry's destination is already occupied, in which case (see overwrite above) the plan either refuses outright (overwrite not set) or is accepted at risk:"high" (overwrite:true \u2014 this permanently destroys the existing asset at the destination). requiresStorageBackup is always true for every verb in this area regardless of risk. There is no bulk "purge everything" verb \u2014 to empty the whole bin, compose a plan with a purge entry for every entry list_recycle_bin_entries returned. ` + SCOPE_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA15, changes: CHANGES_SCHEMA15 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      return deps.wizClient.post("/v1/admin/recycle-bin/preview", {
        task: requireTask(args?.task),
        changes: normalizeRecycleBinChanges(args?.changes)
      });
    }
  };
}
async function resolveRestoreRisks(deps, task, changes) {
  const preview = await deps.wizClient.post("/v1/admin/recycle-bin/preview", { task, changes });
  const planChanges = preview?.changes;
  return Array.isArray(planChanges) ? planChanges : void 0;
}
function makeApplyRecycleBinChangesTool(deps) {
  return {
    name: "apply_recycle_bin_changes",
    description: `Apply a previewed restore/purge plan. Pass back the SAME task and changes you previewed plus the planHash from preview_recycle_bin_changes \u2014 the request body IS the plan; the server never trusts a stored one. reviewOutcome is required whenever the previewed plan had requiresAgentSignoff:true. acknowledgeIrreversibleDelete:true is required whenever the plan contains any purge entry, OR a restore entry whose destination collision was accepted via overwrite:true (both are the same "no live inverse for the destroyed asset" class of action). Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted \u2014 purge is never individually rolled back, only applied/failed; a restore's own rollback re-recycles the just-restored item, but if it destroyed an existing asset via overwrite:true that destroyed asset is gone regardless of whether the restore itself later rolls back \u2014 relay that advisory to the human verbatim), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied). ` + SCOPE_DISCLOSURE,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA15,
        changes: CHANGES_SCHEMA15,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_recycle_bin_changes for this exact plan"
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record. Required when the previewed plan's requiresAgentSignoff is true."
        },
        acknowledgeIrreversibleDelete: {
          type: "boolean",
          description: 'required and must be true whenever changes contains any verb="purge" entry, OR a verb="restore" entry that preview_recycle_bin_changes classified risk:"high" (a destination collision accepted via overwrite:true). Not required for a restore-only plan with no collision. Omit entirely when not required.'
        }
      },
      required: ["task", "changes", "planHash"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_recycle_bin_changes for this plan. Call preview_recycle_bin_changes first."
        );
      }
      const changes = normalizeRecycleBinChanges(args.changes);
      const task = requireTask(args.task);
      const hasPurge = changes.some((c) => c.verb === "purge");
      const hasRestore = changes.some((c) => c.verb === "restore");
      const planChanges = !hasPurge && hasRestore ? await resolveRestoreRisks(deps, task, changes) : void 0;
      const acknowledgeIrreversibleDelete = requireAcknowledgeIrreversibleRecycleBinDelete(
        changes,
        args.acknowledgeIrreversibleDelete,
        planChanges
      );
      const body = {
        task,
        changes,
        planHash: args.planHash.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeIrreversibleDelete !== void 0) {
        body.acknowledgeIrreversibleDelete = acknowledgeIrreversibleDelete;
      }
      try {
        return await deps.wizClient.post("/v1/admin/recycle-bin/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_recycle_bin_changes and apply_recycle_bin_changes \u2014 the named entry, or its destination, changed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_recycle_bin_changes with the planHash from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeRecycleBinTools(deps) {
  return [
    makeListRecycleBinEntriesTool(deps),
    makeGetRecycleBinEntryTool(deps),
    makePreviewRecycleBinChangesTool(deps),
    makeApplyRecycleBinChangesTool(deps)
  ];
}

// src/tools/mvTools.ts
var CHANGES_SCHEMA16 = {
  type: "array",
  description: `the proposed MV changes; each entry names verb FIRST. "create" and "set_cycle" both target candidate mv names produced by a prior analyze_mv run (via analysisId) \u2014 they are NOT interchangeable with an already-created mv's own name. "delete" targets an already-created mv by name directly, with no analysisId.`,
  items: {
    type: "object",
    properties: {
      verb: {
        type: "string",
        description: `"create", "set_cycle", or "delete" (also accepts "add" as an alias for "create", "remove" as an alias for "delete"). A "create" entry's own optional \`cycle\` field already sets the mv's data cycle as part of that same apply \u2014 do NOT also add a separate "set_cycle" entry for the same mv name to do this; the server refuses a changeset naming the same mv in more than one entry, regardless of verb. Use "set_cycle" on its own, targeting a different mv name than any "create"/"delete" entry in the same plan, to retarget a pending candidate's cycle without creating it yet.`
      },
      analysisId: {
        type: "string",
        description: `required for "create"/"set_cycle" \u2014 the analysisId from analyze_mv/get_mv_analysis whose candidate list this entry's mvNames must come from. Not used for "delete". An analysisId unknown to the server, or one whose 20-minute idle TTL has elapsed, is refused loud with a distinct analysisExpired error (never silently treated as a stale planHash conflict) \u2014 the only remedy is to re-run analyze_mv and re-review.`
      },
      mvNames: {
        type: "array",
        items: { type: "string" },
        description: `one or more mv names this entry applies to. For "create"/"set_cycle" these must be candidate names from analysisId's own candidate list (get_mv_analysis's candidates[]); a name not in that list is refused loud, naming it, never silently resolved against the wrong analysis. For "delete" these must be already-created mv names (get_mv_status); a name that does not currently exist is refused loud. Each mv name may appear in at most ONE entry across the whole changeset.`
      },
      cycle: {
        type: "string",
        description: `"create"/"set_cycle" only \u2014 a data cycle name from get_mv_analysis's availableCycles. Omit for "no cycle" (the underlying server treats an empty/absent cycle as null, never the empty string).`
      },
      noData: {
        type: "boolean",
        description: `"create" only. Defaults to true (register the mv definition now, materialize data later) \u2014 a SAFER default than the Enterprise Manager UI's own default of false, so a caller that doesn't set this explicitly gets the fast, no-background-job path first. Set to false to also kick off real data materialization as part of this apply.`
      },
      runInBackground: {
        type: "boolean",
        description: '"create" only. Defaults to true. When noData:false, controls whether data materialization runs as a real background schedule task (true) or synchronously inline during apply, with no timeout (false) \u2014 a large source table can make the latter hang the apply call for a long time.'
      }
    },
    required: ["verb", "mvNames"]
  }
};
var TASK_SCHEMA16 = {
  type: "string",
  description: "a short description of what this change accomplishes; written into every audit record for the transaction"
};
function makeGetMvStatusTool(deps) {
  return {
    name: "get_mv_status",
    description: "Read the current materialized-view status for one or more viewsheets/worksheets, or every registered mv in the deployment when viewsheetAssetIds is omitted. Includes wsMvEnabled (whether worksheet-level MV analysis is on server-wide \u2014 analyze_mv on a worksheet asset id is pointless when this is false, since the server silently drops worksheet candidates from the analysis) and canMaterialize (whether the caller holds the MATERIALIZATION permission every mutating tool in this area enforces \u2014 check this before building a whole plan around preview_mv_changes/apply_mv_changes). This is a community-tier area, not enterprise-gated.",
    inputSchema: {
      type: "object",
      properties: {
        viewsheetAssetIds: {
          type: "array",
          items: { type: "string" },
          description: "optional; asset identifiers as returned by list_viewsheets. Omit for every registered mv in the deployment."
        }
      }
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const ids = Array.isArray(args?.viewsheetAssetIds) ? args.viewsheetAssetIds.filter((v) => typeof v === "string" && v.trim() !== "") : [];
      const query = ids.length === 0 ? "" : "?" + ids.map((id) => `assetIds=${encodeURIComponent(id)}`).join("&");
      return deps.wizClient.get(`/v1/admin/mv/status${query}`);
    }
  };
}
function makeAnalyzeMvTool(deps) {
  return {
    name: "analyze_mv",
    description: "Kick off an asynchronous materialized-view analysis for ONE viewsheet/worksheet \u2014 the per-dashboard MV tab workflow (analyze -> review candidates -> create-with-cycle). Returns { analysisId }; poll it with get_mv_analysis. This step writes nothing durable \u2014 the analysis result lives only in a 20-minute-idle-TTL cluster cache, so if a human takes longer than that to review the plan before confirming, preview_mv_changes/apply_mv_changes will refuse with a distinct analysisExpired error and the whole thing must be re-run.",
    inputSchema: {
      type: "object",
      properties: {
        viewsheetAssetId: {
          type: "string",
          description: `the viewsheet/worksheet's asset identifier, as returned by list_viewsheets, e.g. "1^128^__NULL__^Examples/Census^host-org"`
        },
        expandGroups: {
          type: "boolean",
          description: "expand permission groups to their member users when analyzing; defaults to false"
        },
        bypassVpm: {
          type: "boolean",
          description: "bypass VPM (virtual private model) conditions during analysis; defaults to false"
        },
        fullData: {
          type: "boolean",
          description: "include all data (rather than a sample) during analysis; defaults to false"
        },
        applyParentVsParameters: {
          type: "boolean",
          description: "apply the parent viewsheet's own parameters when analyzing a nested viewsheet; defaults to false"
        }
      },
      required: ["viewsheetAssetId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const viewsheetAssetId = requireMvViewsheetAssetId(args);
      return deps.wizClient.post("/v1/admin/mv/analyze", {
        viewsheetAssetId,
        expandGroups: args?.expandGroups === true,
        bypassVpm: args?.bypassVpm === true,
        fullData: args?.fullData === true,
        applyParentVsParameters: args?.applyParentVsParameters === true
      });
    }
  };
}
function makeGetMvAnalysisTool(deps) {
  return {
    name: "get_mv_analysis",
    description: "Poll an analyze_mv run by analysisId. Returns { completed, exception, candidates[] (mvName, sheets, table, cycle, exists, hasData, plan \u2014 plan is the per-candidate query-optimization explanation text), exceptionReasons[] (why a viewsheet could not be materialized for some user, present when exception:true), availableCycles[] (valid `cycle` values for create/set_cycle), defaultCycle, onDemand, serverRunInBackground }. completed:false means the background analysis job is still running \u2014 re-poll. An analysisId unknown to the server, or one whose 20-minute idle TTL has elapsed, is refused loud with a distinct analysisExpired error \u2014 re-run analyze_mv, this is not the same thing as a stale planHash.",
    inputSchema: {
      type: "object",
      properties: { analysisId: { type: "string", description: "the analysisId returned by analyze_mv" } },
      required: ["analysisId"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const analysisId = requireMvAnalysisId(args);
      return deps.wizClient.get(`/v1/admin/mv/analyze/${encodeURIComponent(analysisId)}`);
    }
  };
}
function makePreviewMvChangesTool(deps) {
  return {
    name: "preview_mv_changes",
    description: "Resolve a set of proposed mv changes (create/set_cycle/delete) into a reviewable plan WITHOUT changing anything on the server. A create or delete entry is always high risk (requiresAgentSignoff:true for the whole plan); a plan containing ONLY set_cycle entries is low risk. requiresStorageBackup is true whenever the plan contains any create entry \u2014 a delete alone does not require it, since dispose is itself the rollback primitive create relies on. A create entry naming an mvName not present in its analysisId's own candidate list is refused loud, naming it \u2014 never silently resolved against the wrong analysis. You MUST pass the returned planHash AND taskToken to apply_mv_changes: the server writes the audit record from the task narrative embedded in taskToken, never from whatever task text apply_mv_changes itself is called with.",
    inputSchema: {
      type: "object",
      properties: { task: TASK_SCHEMA16, changes: CHANGES_SCHEMA16 },
      required: ["task", "changes"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      try {
        return await deps.wizClient.post("/v1/admin/mv/preview", {
          task: requireTask(args?.task),
          changes: normalizeMvChanges(args?.changes)
        });
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_410") {
          return {
            status: "analysisExpired",
            error: e.message ?? "the analysisId is unknown or has expired",
            guidance: 'The analysisId this plan depends on is unknown to the server or its 20-minute idle TTL has elapsed. Re-run analyze_mv, review the fresh candidates, and build a new plan \u2014 this is not the same as a stale planHash conflict, there is no "current plan" to re-diff against.'
          };
        }
        throw err;
      }
    }
  };
}
function makeApplyMvChangesTool(deps) {
  return {
    name: "apply_mv_changes",
    description: `Apply a previewed mv change plan. Pass back the SAME task and changes you previewed plus the planHash AND taskToken from preview_mv_changes \u2014 the request body IS the plan; the server never trusts a stored one. reviewOutcome is required whenever the previewed plan's requiresAgentSignoff is true (any plan containing a create or delete entry). If the plan contains any delete entry you MUST also pass acknowledgeIrreversibleDelete:true \u2014 dispose has no live inverse. A create entry's apply performs set_cycle (if a cycle was given) and create back to back inside this one call, so the mv is never left in the intermediate "cycle set but not created" state the raw server primitive otherwise allows. Result status is one of: "applied", "rolled-back" (something failed and every undoable change was reverted \u2014 delete entries are never individually rolled back, only applied/failed), "rollback-failed" (the server may be PARTIALLY CHANGED \u2014 escalate, do not retry), or "conflict" (the plan drifted, nothing was applied \u2014 this includes the analysisId having expired since preview, surfaced by the server as a distinct analysisExpired refusal rather than a generic conflict).`,
    inputSchema: {
      type: "object",
      properties: {
        task: TASK_SCHEMA16,
        changes: CHANGES_SCHEMA16,
        planHash: {
          type: "string",
          description: "the planHash returned by preview_mv_changes for this exact plan"
        },
        taskToken: {
          type: "string",
          description: "the taskToken returned by preview_mv_changes for this exact plan"
        },
        reviewOutcome: {
          type: "string",
          description: "the reviewer's verdict, recorded on every audit record. Required when the previewed plan's requiresAgentSignoff is true (any plan containing a create or delete entry)."
        },
        acknowledgeIrreversibleDelete: {
          type: "boolean",
          description: 'required and must be true whenever changes contains any verb="delete" entry (no live inverse). Omit entirely when the plan contains no delete.'
        }
      },
      required: ["task", "changes", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_mv_changes for this plan. Call preview_mv_changes first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_mv_changes for this plan. Without it the server cannot verify which reviewed task narrative to audit. Call preview_mv_changes first."
        );
      }
      const changes = normalizeMvChanges(args.changes);
      const task = requireTask(args.task);
      const acknowledgeIrreversibleDelete = requireAcknowledgeIrreversibleMvDelete(changes, args.acknowledgeIrreversibleDelete);
      const body = {
        task,
        changes,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim()
      };
      const reviewOutcome = normalizeReviewOutcome(args.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      if (acknowledgeIrreversibleDelete !== void 0) {
        body.acknowledgeIrreversibleDelete = acknowledgeIrreversibleDelete;
      }
      try {
        return await deps.wizClient.post("/v1/admin/mv/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_mv_changes and apply_mv_changes \u2014 a named mv or its analysis changed by someone else in between. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_mv_changes with the planHash from THAT plan."
          };
        }
        if (e?.code === "HTTP_410") {
          return {
            status: "analysisExpired",
            error: e.message ?? "the analysisId is unknown or has expired",
            guidance: 'The analysisId this plan depends on is unknown to the server or its 20-minute idle TTL has elapsed. NOTHING was applied. Re-run analyze_mv, review the fresh candidates, and build a new plan \u2014 this is not the same as a stale planHash conflict, there is no "current plan" to re-diff against.'
          };
        }
        throw err;
      }
    }
  };
}
function makeMvTools(deps) {
  return [
    makeGetMvStatusTool(deps),
    makeAnalyzeMvTool(deps),
    makeGetMvAnalysisTool(deps),
    makePreviewMvChangesTool(deps),
    makeApplyMvChangesTool(deps)
  ];
}

// src/tools/assetTransferTools.ts
import os3 from "os";
import nodePath4 from "path";
import fs5 from "fs";
import { readFile } from "fs/promises";
function isPlainObject5(v) {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}
function stringArray(raw, label) {
  if (raw === void 0 || raw === null) {
    return [];
  }
  if (Array.isArray(raw) && raw.every((v) => typeof v === "string")) {
    return raw;
  }
  throw new Error(`${label}: expected an array of strings, got ${JSON.stringify(raw)}`);
}
function normalizeIncludeDependencies(raw) {
  if (raw === void 0 || raw === null || raw === "all") {
    return { includeDependenciesMode: "all", includeDependencyNames: [] };
  }
  if (raw === "none") {
    return { includeDependenciesMode: "none", includeDependencyNames: [] };
  }
  if (Array.isArray(raw)) {
    return {
      includeDependenciesMode: "list",
      includeDependencyNames: stringArray(raw, "includeDependencies")
    };
  }
  throw new Error(
    `includeDependencies: must be "all", "none", or an array of dependent-asset names; got ${JSON.stringify(raw)}`
  );
}
function requireExportSelection(args) {
  const viewsheetAssetIds = stringArray(args.viewsheetAssetIds, "viewsheetAssetIds");
  const folderPaths = stringArray(args.folderPaths, "folderPaths");
  if (viewsheetAssetIds.length === 0 && folderPaths.length === 0) {
    throw new Error(
      "viewsheetAssetIds/folderPaths: at least one of these is required \u2014 a bare asset id (as returned by list_viewsheets/list_worksheets) or a registry folder path"
    );
  }
  return { viewsheetAssetIds, folderPaths };
}
function requireStagingToken(args) {
  const raw = args.stagingToken;
  if (typeof raw !== "string" || raw.trim() === "") {
    throw new Error(
      "stagingToken: required \u2014 the token returned by stage_repository_import for this upload"
    );
  }
  return raw.trim();
}
function normalizeBookmarkResolutions(raw) {
  if (raw === void 0 || raw === null) {
    return [];
  }
  if (!Array.isArray(raw)) {
    throw new Error(
      `bookmarkResolutions: expected an array of { viewsheetPath, user, bookmarkName, keepImported }, got ${typeof raw}`
    );
  }
  return raw.map((entry, i) => {
    const label = `bookmarkResolutions[${i}]`;
    if (!isPlainObject5(entry)) {
      throw new Error(`${label}: expected an object, got ${typeof entry}`);
    }
    if (typeof entry.viewsheetPath !== "string" || entry.viewsheetPath.trim() === "") {
      throw new Error(`${label}.viewsheetPath: required non-blank string`);
    }
    if (typeof entry.user !== "string" || entry.user.trim() === "") {
      throw new Error(`${label}.user: required non-blank string`);
    }
    if (typeof entry.bookmarkName !== "string" || entry.bookmarkName.trim() === "") {
      throw new Error(`${label}.bookmarkName: required non-blank string`);
    }
    if (entry.keepImported !== void 0 && typeof entry.keepImported !== "boolean") {
      throw new Error(`${label}.keepImported: must be boolean when present`);
    }
    return {
      viewsheetPath: entry.viewsheetPath,
      user: entry.user,
      bookmarkName: entry.bookmarkName,
      keepImported: entry.keepImported !== false
    };
  });
}
function defaultExportDir() {
  return nodePath4.join(os3.homedir(), "Downloads");
}
function claimUniqueDest3(dir, fileName, fromPath) {
  const ext = nodePath4.extname(fileName);
  const base = fileName.slice(0, fileName.length - ext.length);
  for (let i = 0; i < 1e3; i++) {
    const candidate = i === 0 ? nodePath4.join(dir, fileName) : nodePath4.join(dir, `${base} (${i})${ext}`);
    try {
      fs5.linkSync(fromPath, candidate);
      fs5.unlinkSync(fromPath);
      return candidate;
    } catch (err) {
      if (err.code !== "EEXIST") throw err;
    }
  }
  throw new Error(
    `export_repository_assets: could not find a free filename for '${fileName}' in '${dir}' after 1000 attempts.`
  );
}
function makeExportRepositoryAssetsTool(deps) {
  return {
    name: "export_repository_assets",
    description: `Export a set of viewsheets/worksheets (and, for a folder, every viewsheet/worksheet recursively under it) to a local zip file \u2014 a single call that resolves the selection, permission-filters it, computes required dependencies, creates the export, and downloads it to disk. Read-only from the product's own data point of view: nothing on the server is mutated, so there is no preview/apply/planHash for this tool, matching backup_storage's own precedent. Pass viewsheetAssetIds (as returned by list_viewsheets/list_worksheets) and/or folderPaths (registry folder paths, same shape get_viewsheet_folder uses) \u2014 at least one is required. Folder expansion is scoped to viewsheet/worksheet content only; any other asset type under a selected folder (data source, script, table style, schedule task, ...) is silently excluded from the export selection \u2014 this plugin has no path-discovery tool for those other types yet. Any given id/path that does not resolve to an existing viewsheet/worksheet is named in the response's unresolvedEntities, never silently dropped \u2014 check it before telling the user the export is complete. includeDependencies controls the required-dependency set bundled into the zip: "all" (default \u2014 the safest, most portable export), "none" (smallest, relies on dependencies already existing at the destination), or an explicit array of dependency names to include.`,
    inputSchema: {
      type: "object",
      properties: {
        task: {
          type: "string",
          description: "a short description of what this export is for; written into the audit record"
        },
        viewsheetAssetIds: {
          type: "array",
          items: { type: "string" },
          description: "asset ids as returned by list_viewsheets/list_worksheets"
        },
        folderPaths: {
          type: "array",
          items: { type: "string" },
          description: "registry folder paths; every viewsheet/worksheet recursively under each is included"
        },
        includeDependencies: {
          description: '"all" (default), "none", or an array of dependency names to include selectively'
        },
        name: { type: "string", description: "optional export/zip name; a name is generated if omitted" },
        savePath: {
          type: "string",
          description: "directory to save the zip into (not a full file path). Defaults to this machine's Downloads folder."
        }
      },
      required: []
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const { viewsheetAssetIds, folderPaths } = requireExportSelection(args ?? {});
      const { includeDependenciesMode, includeDependencyNames } = normalizeIncludeDependencies(args?.includeDependencies);
      const name = typeof args?.name === "string" && args.name.trim() !== "" ? args.name.trim() : void 0;
      const created = await deps.wizClient.post("/v1/admin/repository/export", {
        task,
        viewsheetAssetIds,
        folderPaths,
        name,
        includeDependenciesMode,
        includeDependencyNames
      });
      const dir = typeof args?.savePath === "string" && args.savePath.trim() !== "" ? args.savePath : defaultExportDir();
      const provisionalDest = nodePath4.join(
        dir,
        `export-${Date.now()}-${Math.random().toString(36).slice(2)}.tmp`
      );
      const downloaded = await deps.wizClient.downloadToFile(
        `/v1/admin/repository/export/download/${encodeURIComponent(created.exportId)}`,
        provisionalDest
      );
      const fileName = downloaded.fileName ?? created.fileName;
      const filePath = claimUniqueDest3(dir, fileName, provisionalDest);
      return {
        filePath,
        fileName: nodePath4.basename(filePath),
        mimeType: downloaded.mimeType ?? "application/zip",
        byteLength: downloaded.byteLength,
        exportedCount: created.exportedCount,
        unresolvedEntities: created.unresolvedEntities,
        skippedForPermission: created.skippedForPermission,
        includedDependencies: created.includedDependencies
      };
    }
  };
}
function makeStageRepositoryImportTool(deps) {
  return {
    name: "stage_repository_import",
    description: "Upload and parse a local zip file (e.g. one produced by export_repository_assets), without changing anything on the server yet. Returns a stagingToken plus the jar's contents (name, deploymentDate, selectedEntities, dependentAssets). newerVersion:true is a plain informational flag, not a refusal \u2014 the underlying server still accepts an older- or newer-format jar. Call preview_repository_import next with the returned stagingToken.",
    inputSchema: {
      type: "object",
      properties: {
        filePath: { type: "string", description: "local path to the zip file to upload" }
      },
      required: ["filePath"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      if (typeof args?.filePath !== "string" || args.filePath.trim() === "") {
        throw new Error("filePath: required \u2014 a local path to the zip file to upload");
      }
      const bytes = await readFile(args.filePath);
      const form = new FormData();
      form.append("file", new Blob([bytes]), args.filePath.split(/[\\/]/).pop() ?? "import.zip");
      const info = await deps.wizClient.postMultipart(
        "/v1/admin/repository/import/stage",
        form
      );
      return { stagingToken: info.importId, ...info };
    }
  };
}
function makePreviewRepositoryImportTool(deps) {
  return {
    name: "preview_repository_import",
    description: "Resolve a staged import against a target (or, if targetFolderPath/targetOwner are omitted, each asset's own recorded original path) into a reviewable plan. Writes nothing. Returns willOverwrite/willCreate (which target assets already exist vs. are new) and bookmarkConflicts, plus planHash and taskToken. You MUST pass the returned planHash AND taskToken to apply_repository_import \u2014 the server writes the audit record from the task narrative embedded in taskToken, the one reviewed here, never from whatever task text apply_repository_import itself is called with. If willOverwrite is non-empty, apply_repository_import will refuse the call unless acknowledgeOverwrite:true is also passed \u2014 surface willOverwrite to the human before applying. NOTE: when targetFolderPath is given, willOverwrite/willCreate are computed against each asset's ORIGINAL recorded path, not the retargeted destination \u2014 a disclosed limitation of this preview, not a guarantee about the retargeted location.",
    inputSchema: {
      type: "object",
      properties: {
        task: { type: "string", description: "a short description of what this import accomplishes" },
        stagingToken: { type: "string", description: "the token returned by stage_repository_import" },
        targetFolderPath: {
          type: "string",
          description: "optional destination folder; omit to import each asset back to its own recorded path"
        },
        targetOwner: { type: "string", description: "optional; a per-user destination folder's owner" },
        ignoreAssetNames: {
          type: "array",
          items: { type: "string" },
          description: "dependent-asset names (from stage_repository_import's dependentAssets) to skip on import"
        },
        bookmarkResolutions: {
          type: "array",
          description: "resolutions for any bookmark conflicts named in an earlier preview's bookmarkConflicts",
          items: {
            type: "object",
            properties: {
              viewsheetPath: { type: "string" },
              user: { type: "string" },
              bookmarkName: { type: "string" },
              keepImported: { type: "boolean", description: "default true" }
            }
          }
        }
      },
      required: ["task", "stagingToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const stagingToken = requireStagingToken(args ?? {});
      const body = {
        task,
        stagingToken,
        ignoreAssetNames: stringArray(args?.ignoreAssetNames, "ignoreAssetNames"),
        bookmarkResolutions: normalizeBookmarkResolutions(args?.bookmarkResolutions)
      };
      if (typeof args?.targetFolderPath === "string" && args.targetFolderPath.trim() !== "") {
        body.targetFolderPath = args.targetFolderPath.trim();
      }
      if (typeof args?.targetOwner === "string" && args.targetOwner.trim() !== "") {
        body.targetOwner = args.targetOwner.trim();
      }
      return deps.wizClient.post("/v1/admin/repository/import/preview", body);
    }
  };
}
function makeApplyRepositoryImportTool(deps) {
  return {
    name: "apply_repository_import",
    description: `Apply a previewed repository import. Pass back the SAME stagingToken/targetFolderPath/targetOwner/ignoreAssetNames/bookmarkResolutions you previewed, plus the planHash AND taskToken from preview_repository_import, and a non-blank reviewOutcome. If the previewed plan's willOverwrite was non-empty, you MUST also pass overwrite:true AND acknowledgeOverwrite:true \u2014 omitting either is refused loud. This mutates the repository and has NO rollback in this cut: result status is "applied" or "failed" only (never "rolled-back"/"rollback-failed") \u2014 a Tier-2 storage snapshot is taken before applying and is the only recovery path. A "conflict" result means the plan drifted since preview \u2014 nothing was applied; re-preview and get fresh confirmation before retrying.`,
    inputSchema: {
      type: "object",
      properties: {
        task: { type: "string" },
        stagingToken: { type: "string" },
        targetFolderPath: { type: "string" },
        targetOwner: { type: "string" },
        ignoreAssetNames: { type: "array", items: { type: "string" } },
        bookmarkResolutions: {
          type: "array",
          items: {
            type: "object",
            properties: {
              viewsheetPath: { type: "string" },
              user: { type: "string" },
              bookmarkName: { type: "string" },
              keepImported: { type: "boolean" }
            }
          }
        },
        planHash: { type: "string", description: "the planHash returned by preview_repository_import" },
        taskToken: { type: "string", description: "the taskToken returned by preview_repository_import" },
        reviewOutcome: { type: "string", description: "the reviewer's verdict, recorded on the audit record" },
        overwrite: {
          type: "boolean",
          description: "must be true whenever the previewed plan's willOverwrite is non-empty"
        },
        acknowledgeOverwrite: {
          type: "boolean",
          description: "must be true whenever the previewed plan's willOverwrite is non-empty"
        }
      },
      required: ["task", "stagingToken", "planHash", "taskToken"]
    },
    call: async (args) => {
      await assertStyleBIRouting(deps.tokenStore);
      const task = requireTask(args?.task);
      const stagingToken = requireStagingToken(args ?? {});
      if (typeof args?.planHash !== "string" || args.planHash.trim() === "") {
        throw new Error(
          "planHash: required \u2014 the hash returned by preview_repository_import for this plan. Call preview_repository_import first."
        );
      }
      if (typeof args?.taskToken !== "string" || args.taskToken.trim() === "") {
        throw new Error(
          "taskToken: required \u2014 the token returned by preview_repository_import for this plan. Call preview_repository_import first."
        );
      }
      if (args?.overwrite === true && args?.acknowledgeOverwrite !== true) {
        throw new Error(
          "acknowledgeOverwrite: required and must be true when overwrite is true \u2014 omitting it is refused loud rather than silently proceeding with a potentially destructive overwrite"
        );
      }
      const body = {
        task,
        stagingToken,
        planHash: args.planHash.trim(),
        taskToken: args.taskToken.trim(),
        ignoreAssetNames: stringArray(args?.ignoreAssetNames, "ignoreAssetNames"),
        bookmarkResolutions: normalizeBookmarkResolutions(args?.bookmarkResolutions),
        overwrite: args?.overwrite === true,
        acknowledgeOverwrite: args?.acknowledgeOverwrite === true
      };
      if (typeof args?.targetFolderPath === "string" && args.targetFolderPath.trim() !== "") {
        body.targetFolderPath = args.targetFolderPath.trim();
      }
      if (typeof args?.targetOwner === "string" && args.targetOwner.trim() !== "") {
        body.targetOwner = args.targetOwner.trim();
      }
      const reviewOutcome = normalizeReviewOutcome(args?.reviewOutcome);
      if (reviewOutcome !== void 0) {
        body.reviewOutcome = reviewOutcome;
      }
      try {
        return await deps.wizClient.post("/v1/admin/repository/import/apply", body);
      } catch (err) {
        const e = err;
        if (e?.code === "HTTP_409") {
          const detail = e.detail ?? {};
          return {
            status: "conflict",
            error: typeof detail.error === "string" ? detail.error : e.message ?? "the plan no longer matches the planHash",
            plan: detail.plan ?? null,
            guidance: "The plan drifted between preview_repository_import and apply_repository_import \u2014 the staged jar or its target changed. NOTHING was applied. Show the returned `plan` to the user as a fresh diff, get confirmation again, then call apply_repository_import with the planHash/taskToken from THAT plan."
          };
        }
        throw err;
      }
    }
  };
}
function makeAssetTransferTools(deps) {
  return [
    makeExportRepositoryAssetsTool(deps),
    makeStageRepositoryImportTool(deps),
    makePreviewRepositoryImportTool(deps),
    makeApplyRepositoryImportTool(deps)
  ];
}

// src/server.ts
function createServer(overrides = {}) {
  const tokenStore = overrides.tokenStore ?? new TokenStore(defaultCredentialsPath("admin-chat"));
  const wizClient = overrides.wizClient ?? new WizClient(tokenStore);
  const sessionManager = new SessionManager();
  const adminDeps = { wizClient, tokenStore };
  const tools = [
    makeLoginStartTool({ sessionManager }),
    makeLoginCompleteTool({ sessionManager, tokenStore }),
    makeAdminStatusTool({ tokenStore, wizClient }),
    makeLogoutTool({ tokenStore }),
    ...makeReadTools(adminDeps),
    ...makeChangeTools(adminDeps),
    ...makeAuditTools(adminDeps),
    ...makeScheduleTools(adminDeps),
    ...makePermissionTools(adminDeps),
    ...makeIdentityTools(adminDeps),
    ...makeProviderTools(adminDeps),
    ...makeDataSourceTools(adminDeps),
    ...makeClusterTools(adminDeps),
    ...makeRepositoryMaintenanceTools(adminDeps),
    ...makeStoredAssetTools(adminDeps),
    ...makePluginManagementTools(adminDeps),
    ...makeViewsheetTools(adminDeps),
    ...makeWorksheetTools(adminDeps),
    ...makeDashboardTools(adminDeps),
    ...makeLicensingTools(adminDeps),
    ...makePresentationTools(adminDeps),
    ...makeShapeTools(adminDeps),
    ...makeThemeTools(adminDeps),
    ...makeRecycleBinTools(adminDeps),
    ...makeMvTools(adminDeps),
    ...makeAssetTransferTools(adminDeps),
    // Documentation search: the agent has no product source in normal use, so this is how a
    // natural-language task becomes a property name. Uses the shared assertDocsSearchRouting
    // rather than this file's own assertStyleBIRouting purely because the guard is shared with
    // every other plugin that registers this tool -- the routing verdict is now identical either
    // way: /v1/docs/search exists only on StyleBI Java, so a wizServicesUrl login is refused for
    // this tool exactly like it is for the rest.
    makeSearchProductDocsTool({ wizClient, preflight: () => assertDocsSearchRouting(tokenStore) })
  ];
  const toolMap = new Map(tools.map((t) => [t.name, t]));
  async function handleRequest(req) {
    const r = req;
    if (r.method === "initialize") {
      return {
        jsonrpc: "2.0",
        id: r.id,
        result: {
          protocolVersion: "2024-11-05",
          capabilities: { tools: {} },
          serverInfo: { name: "admin-chat", version: "1.0.0" }
        }
      };
    }
    if (r.method === "notifications/initialized") {
      return null;
    }
    if (r.method === "tools/list") {
      return {
        jsonrpc: "2.0",
        id: r.id,
        result: {
          tools: tools.map((t) => ({
            name: t.name,
            description: t.description,
            inputSchema: t.inputSchema
          }))
        }
      };
    }
    if (r.method === "tools/call") {
      const p = r.params;
      const tool = toolMap.get(p.name);
      if (!tool) {
        return {
          jsonrpc: "2.0",
          id: r.id,
          error: { code: -32601, message: `Unknown tool: ${p.name}` }
        };
      }
      try {
        const result = await tool.call(p.arguments ?? {});
        return {
          jsonrpc: "2.0",
          id: r.id,
          result: {
            content: [
              {
                type: "text",
                text: typeof result === "string" ? result : JSON.stringify(result, null, 2)
              }
            ]
          }
        };
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        return { jsonrpc: "2.0", id: r.id, error: { code: -32e3, message: msg } };
      }
    }
    return {
      jsonrpc: "2.0",
      id: r.id,
      error: { code: -32601, message: `Unknown method: ${r.method}` }
    };
  }
  return { handleRequest, tools };
}

// ../shared/core/src/rpc/stdioRpcLoop.ts
import readline from "readline";
async function handleRpcLine(server, line, write) {
  let req;
  try {
    req = JSON.parse(line);
  } catch {
    write(
      JSON.stringify({ jsonrpc: "2.0", id: null, error: { code: -32700, message: "Parse error" } }) + "\n"
    );
    return;
  }
  try {
    const resp = await server.handleRequest(req);
    if (resp !== null) {
      write(JSON.stringify(resp) + "\n");
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    write(
      JSON.stringify({ jsonrpc: "2.0", id: req?.id ?? null, error: { code: -32603, message: msg } }) + "\n"
    );
  }
}
function runStdioRpcLoop(server, options = {}) {
  const input = options.input ?? process.stdin;
  const write = options.write ?? ((chunk) => {
    process.stdout.write(chunk);
  });
  const rl = readline.createInterface({ input });
  rl.on("line", (line) => {
    void handleRpcLine(server, line, write);
  });
  return rl;
}

// src/bin.ts
runStdioRpcLoop(createServer());
/*! Bundled license information:

mime-db/index.js:
  (*!
   * mime-db
   * Copyright(c) 2014 Jonathan Ong
   * Copyright(c) 2015-2022 Douglas Christopher Wilson
   * MIT Licensed
   *)

mime-types/index.js:
  (*!
   * mime-types
   * Copyright(c) 2014 Jonathan Ong
   * Copyright(c) 2015 Douglas Christopher Wilson
   * MIT Licensed
   *)
*/
