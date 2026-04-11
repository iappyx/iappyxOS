import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

class PreviewScreen extends StatefulWidget {
  final String htmlContent;
  const PreviewScreen({super.key, required this.htmlContent});
  @override
  State<PreviewScreen> createState() => _PreviewScreenState();
}

class _ConsoleLine {
  final String type;
  final String message;
  _ConsoleLine(this.type, this.message);
}

class _PreviewScreenState extends State<PreviewScreen> {
  late final WebViewController _controller;
  final List<_ConsoleLine> _console = [];
  bool _consoleExpanded = true;
  bool _bannerVisible = true;
  static const _channel = MethodChannel('com.iappyx.container/generator');

  // ── Console capture (injected before app code) ──
  static const _consoleCapture = '''
<script>
(function(){
  var _log=console.log,_err=console.error,_warn=console.warn,_info=console.info;
  function s(t,a){
    try{ConsoleChannel.postMessage(JSON.stringify({type:t,msg:Array.prototype.slice.call(a).map(function(x){
      try{return typeof x==='object'?JSON.stringify(x):String(x)}catch(e){return String(x)}
    }).join(' ')}));}catch(e){}
  }
  console.log=function(){s('log',arguments);_log.apply(console,arguments);};
  console.error=function(){s('error',arguments);_err.apply(console,arguments);};
  console.warn=function(){s('warn',arguments);_warn.apply(console,arguments);};
  console.info=function(){s('info',arguments);_info.apply(console,arguments);};
  window.onerror=function(msg,url,line,col){s('error',['['+line+':'+col+'] '+msg]);};
  window.onunhandledrejection=function(e){s('error',['Unhandled promise: '+e.reason]);};
})();
</script>
''';

  // ── Bridge shim (creates window.iappyx with working subset) ──
  static const _bridgeShim = r'''
<script>
(function(){
  // In-memory storage (persists within preview session)
  var _store={};

  // Callback registry (mirrors real bridge pattern)
  window._iappyxCb=window._iappyxCb||{};

  function _deliver(cbId,json){
    if(window._iappyxCb&&window._iappyxCb[cbId]){
      window._iappyxCb[cbId](json);
      delete window._iappyxCb[cbId];
    }
  }

  // Send async bridge call to Dart
  function _call(bridge,method,args){
    try{BridgeChannel.postMessage(JSON.stringify({bridge:bridge,method:method,args:args||{}}));}catch(e){}
  }

  // Unsupported bridge method — delivers error via callback or logs warning
  function _unsupported(name,cbId){
    var msg='[Preview] '+name+' is not available in preview. Build the APK to test this feature.';
    console.warn(msg);
    if(cbId){_deliver(cbId,{ok:false,error:msg});}
  }

  // ── Storage (fully in-memory) ──
  var _files={};
  var storage={
    save:function(k,v){_store[k]=v;},
    load:function(k){return _store.hasOwnProperty(k)?_store[k]:null;},
    remove:function(k){delete _store[k];},
    clear:function(){_store={};},
    saveFile:function(name,content){_files[name]=content;},
    loadFile:function(name){return _files.hasOwnProperty(name)?_files[name]:null;},
    deleteFile:function(name){delete _files[name];},
    listFiles:function(){return JSON.stringify(Object.keys(_files));},
    getFileInfo:function(name){var f=_files[name];return JSON.stringify(f!=null?{exists:true,size:f.length,name:name}:{exists:false});},
    readFileBase64:function(path){return null;},
    moveFile:function(src,dst){if(_files[src]){_files[dst]=_files[src];delete _files[src];return true;}return false;},
    copyFileToDownloads:function(){console.warn('[Preview] copyFileToDownloads not available');return false;},
    pickFile:function(cbId){_unsupported('storage.pickFile',cbId);}
  };

  // ── Device (mock data) ──
  var device={
    getPackageName:function(){return 'com.iappyx.preview';},
    getAppName:function(){return 'Preview App';},
    getDeviceInfo:function(){return JSON.stringify({
      brand:'Preview',model:'iappyxOS Preview',sdk:33,
      battery:100,charging:true,
      screenWidth:window.innerWidth,screenHeight:window.innerHeight,
      density:window.devicePixelRatio||1,language:navigator.language||'en'
    });},
    getConnectivity:function(){return JSON.stringify({connected:true,type:'wifi',metered:false});},
    getThemeColors:function(){return JSON.stringify({primary:'#4FC3F7',background:'#0D0D1A',surface:'#1A1A2E',onPrimary:'#FFFFFF',onBackground:'#FFFFFF',onSurface:'#FFFFFF'});},
    isDarkMode:function(){return true;},
    viewPdf:function(){console.warn('[Preview] viewPdf not available');},
    print:function(){console.warn('[Preview] print not available');},
    ping:function(h,t,cbId){_unsupported('device.ping',cbId);},
    setTorch:function(on){console.log('[Preview] Torch: '+on);},
    setShortcuts:function(){console.log('[Preview] setShortcuts not available');},
    setShareCallback:function(fn){console.log('[Preview] setShareCallback registered');},
    setDndMode:function(on){console.log('[Preview] DND: '+on);},
    isDndActive:function(){return false;},
    onClipboardChange:function(fn){console.log('[Preview] onClipboardChange registered');},
    readFromDownloads:function(){return null;},
    setWallpaper:function(){console.warn('[Preview] setWallpaper not available');},
  };

  // ── Vibration (sends to Dart for haptic feedback) ──
  var vibration={
    vibrate:function(ms){_call('vibration','vibrate',{ms:ms});},
    pattern:function(p){_call('vibration','pattern',{pattern:p});},
    click:function(){_call('vibration','click');},
    tick:function(){_call('vibration','tick');},
    heavyClick:function(){_call('vibration','heavyClick');}
  };

  // ── Clipboard (sends to Dart) ──
  var clipboard={
    write:function(t){_call('clipboard','write',{text:t});},
    read:function(){
      // Synchronous read not possible via channel — return from in-memory fallback
      return _store['__clipboard']||null;
    }
  };

  // ── Notification (mock — logs to console) ──
  var notification={
    send:function(title,body){console.log('[Preview Notification] '+title+': '+body);_call('notification','send',{title:title,body:body});},
    sendWithId:function(id,title,body){console.log('[Preview Notification #'+id+'] '+title+': '+body);},
    cancel:function(id){console.log('[Preview] Notification #'+id+' cancelled');},
    cancelAll:function(){console.log('[Preview] All notifications cancelled');},
    sendWithActions:function(id,title,body,actionsJson,fn){console.log('[Preview Notification #'+id+'] '+title+': '+body+' (actions: '+actionsJson+')');},
    schedule:function(id,title,body,tsMs){console.log('[Preview] Notification "'+id+'" scheduled for '+new Date(tsMs).toLocaleTimeString());}
  };

  // ── Screen (mock) ──
  var screen={
    keepOn:function(on){console.log('[Preview] Screen keepOn: '+on);},
    setBrightness:function(level){console.log('[Preview] Brightness: '+level);},
    wakeLock:function(on){console.log('[Preview] Wake lock: '+on);},
    isScreenOn:function(){return true;}
  };

  // ── TTS (sends to Dart for real speech) ──
  var tts={
    speak:function(text){_call('tts','speak',{text:text});},
    stop:function(){_call('tts','stop');},
    setLanguage:function(lang){_call('tts','setLanguage',{lang:lang});},
    setPitch:function(p){_call('tts','setPitch',{pitch:p});},
    setRate:function(r){_call('tts','setRate',{rate:r});},
    speakWithCallback:function(text,fn){
      _call('tts','speak',{text:text});
      // Fire callback after a reasonable delay
      setTimeout(function(){
        if(typeof window[fn]==='function') window[fn]();
        else{try{eval(fn+'()')}catch(e){}}
      },Math.max(500,text.length*60));
    }
  };

  // ── Audio (sends to Dart for real playback) ──
  var audio={
    play:function(url){_call('audio','play',{url:url});},
    pause:function(){_call('audio','pause');},
    resume:function(){_call('audio','resume');},
    stop:function(){_call('audio','stop');},
    seekTo:function(ms){_call('audio','seekTo',{ms:ms});},
    setVolume:function(v){_call('audio','setVolume',{volume:v});},
    setSystemVolume:function(v){console.log('[Preview] System volume: '+v);},
    setLooping:function(l){_call('audio','setLooping',{loop:l});},
    isPlaying:function(){return false;},
    getDuration:function(){return 0;},
    getCurrentPosition:function(){return 0;},
    onComplete:function(fn){console.log('[Preview] Audio onComplete registered');},
    startRecording:function(cbId){_unsupported('audio.startRecording',cbId);},
    stopRecording:function(cbId){_unsupported('audio.stopRecording',cbId);},
    isRecording:function(){return false;},
    playSound:function(url){console.log('[Preview] playSound: '+url);},
    stopSounds:function(){},
    setSpeed:function(s){console.log('[Preview] Audio speed: '+s);},
    addToQueue:function(url){console.log('[Preview] addToQueue: '+url);},
    clearQueue:function(){},
    skipToNext:function(){},
    skipToPrevious:function(){},
    getEqualizerPresets:function(){return '[]';},
    setEqualizerPreset:function(){},
    getEqualizerBands:function(){return '{}';},
    setEqualizerBand:function(){},
    disableEqualizer:function(){},
    setMediaSession:function(){console.log('[Preview] setMediaSession');},
    onMetadata:function(fn){console.log('[Preview] onMetadata registered');},
    requestFocus:function(fn){if(fn)try{eval(fn+'({type:\"gain\"})')}catch(e){}},
    abandonFocus:function(){},
    startVisualizer:function(fn){console.warn('[Preview] Visualizer not available in preview');},
    stopVisualizer:function(){},
    speechToText:function(cbId){_unsupported('audio.speechToText',cbId);}
  };

  // ── Sensor (simulated — multiple concurrent sensors supported) ──
  var _sensorTimers={};
  function _stopSensor(key){if(_sensorTimers[key]){clearInterval(_sensorTimers[key]);delete _sensorTimers[key];}}
  var sensor={
    startAccelerometer:function(fn){
      _stopSensor('accel');
      _sensorTimers['accel']=setInterval(function(){
        try{eval(fn+'({x:'+(Math.random()*2-1).toFixed(2)+',y:9.8,z:'+(Math.random()*0.5).toFixed(2)+',t:'+Date.now()+'})');}catch(e){}
      },100);
      console.log('[Preview] Accelerometer started (simulated)');
    },
    startGyroscope:function(fn){
      _stopSensor('gyro');
      _sensorTimers['gyro']=setInterval(function(){
        try{eval(fn+'({x:0,y:0,z:0,t:'+Date.now()+'})');}catch(e){}
      },100);
      console.log('[Preview] Gyroscope started (simulated)');
    },
    startMagnetometer:function(fn){
      _stopSensor('mag');
      _sensorTimers['mag']=setInterval(function(){
        try{eval(fn+'({x:'+(Math.random()*60-30).toFixed(1)+',y:'+(Math.random()*60-30).toFixed(1)+',z:'+(Math.random()*60-30).toFixed(1)+',t:'+Date.now()+'})');}catch(e){}
      },100);
      console.log('[Preview] Magnetometer started (simulated)');
    },
    startProximity:function(fn){
      _stopSensor('prox');
      _sensorTimers['prox']=setInterval(function(){
        try{eval(fn+'({distance:5,near:false,t:'+Date.now()+'})');}catch(e){}
      },500);
      console.log('[Preview] Proximity started (simulated)');
    },
    startLight:function(fn){
      _stopSensor('light');
      _sensorTimers['light']=setInterval(function(){
        try{eval(fn+'({lux:'+(200+Math.random()*100).toFixed(0)+',t:'+Date.now()+'})');}catch(e){}
      },500);
      console.log('[Preview] Light sensor started (simulated)');
    },
    startPressure:function(fn){
      _stopSensor('press');
      _sensorTimers['press']=setInterval(function(){
        try{eval(fn+'({hPa:'+(1013+Math.random()*5).toFixed(1)+',t:'+Date.now()+'})');}catch(e){}
      },500);
      console.log('[Preview] Pressure sensor started (simulated)');
    },
    startStepCounter:function(fn){
      _stopSensor('step');
      var steps=0;
      _sensorTimers['step']=setInterval(function(){
        steps++;
        try{eval(fn+'({steps:'+steps+',t:'+Date.now()+'})');}catch(e){}
      },1000);
      console.log('[Preview] Step counter started (simulated — 1 step/sec)');
    },
    startCompass:function(fn){
      _stopSensor('compass');
      _sensorTimers['compass']=setInterval(function(){
        try{eval(fn+'({heading:'+(Math.random()*360).toFixed(1)+',accuracy:1,t:'+Date.now()+'})');}catch(e){}
      },200);
      console.log('[Preview] Compass started (simulated)');
    },
    stop:function(){for(var k in _sensorTimers){clearInterval(_sensorTimers[k]);} _sensorTimers={};}
  };

  // ── Alarm (mock — uses setTimeout) ──
  var _alarms={};
  var alarm={
    set:function(tsMs,fn){
      var delay=Math.max(0,tsMs-Date.now());
      if(_alarms['default'])clearTimeout(_alarms['default']);
      _alarms['default']=setTimeout(function(){
        console.log('[Preview] Alarm fired');
        try{eval('if(typeof '+fn+"==='function')"+fn+'({})');}catch(e){}
      },delay);
      console.log('[Preview] Alarm set for '+new Date(tsMs).toLocaleTimeString()+' ('+Math.round(delay/1000)+'s)');
    },
    setWithId:function(id,tsMs,fn){
      var delay=Math.max(0,tsMs-Date.now());
      if(_alarms[id])clearTimeout(_alarms[id]);
      _alarms[id]=setTimeout(function(){
        console.log('[Preview] Alarm "'+id+'" fired');
        try{eval('if(typeof '+fn+"==='function')"+fn+'({})');}catch(e){}
      },delay);
      console.log('[Preview] Alarm "'+id+'" set for '+new Date(tsMs).toLocaleTimeString());
    },
    cancel:function(){if(_alarms['default']){clearTimeout(_alarms['default']);delete _alarms['default'];}},
    cancelById:function(id){if(_alarms[id]){clearTimeout(_alarms[id]);delete _alarms[id];}},
    getScheduled:function(){return _alarms['default']?'scheduled':null;},
    setRepeating:function(id,intervalMs,fn){console.log('[Preview] Repeating alarm "'+id+'" every '+intervalMs+'ms');}
  };

  // ── Share (sends to Dart) ──
  function sharePhoto(b64){_call('share','sharePhoto',{base64:b64});}
  function shareText(text,subject){_call('share','shareText',{text:text,subject:subject||''});}

  // ── SQLite (in-memory mock using JS objects) ──
  var _sqlTables={};
  var sqlite={
    exec:function(sql,params){
      console.log('[Preview SQLite] exec: '+sql);
      return JSON.stringify({ok:true});
    },
    query:function(sql,params){
      console.log('[Preview SQLite] query: '+sql);
      return JSON.stringify({ok:true,rows:[]});
    },
    beginTransaction:function(){return JSON.stringify({ok:true});},
    commit:function(){return JSON.stringify({ok:true});},
    rollback:function(){return JSON.stringify({ok:true});}
  };

  // ── Capabilities ──
  function capabilities(){
    return {
      version:1,sdk:33,preview:true,
      bridges:{storage:true,device:true,vibration:true,clipboard:true,notification:true,
        screen:true,tts:true,audio:true,sensor:true,alarm:true,sqlite:true,
        camera:false,location:false,contacts:false,sms:false,calendar:false,
        biometric:false,nfc:false,ble:false,ssh:false,smb:false,
        httpServer:false,httpClient:false,tcp:false,udp:false,nsd:false,
        wifiDirect:false,push:false,download:false,media:false},
      permissions:{}
    };
  }

  // ── Unsupported bridges (return graceful errors) ──
  var camera={
    takePhoto:function(cbId){_unsupported('camera.takePhoto',cbId);},
    takeVideo:function(cbId){_unsupported('camera.takeVideo',cbId);},
    scanQR:function(cbId){_unsupported('camera.scanQR',cbId);},
    scanText:function(cbId){_unsupported('camera.scanText',cbId);},
    classify:function(cbId){_unsupported('camera.classify',cbId);},
    removeBackground:function(cbId){_unsupported('camera.removeBackground',cbId);},
    getExif:function(path,cbId){_unsupported('camera.getExif',cbId);},
    sharePhoto:sharePhoto,
    shareText:shareText
  };
  // Sync camera methods (called directly on iappyxCamera, not through wrapper)
  window.iappyxCamera={
    scanFrameQRSync:function(){return JSON.stringify({ok:true,results:[]});},
    scanFrameTextSync:function(){return JSON.stringify({ok:true,text:'',blocks:[]});},
    scanFrameQR:function(b64,cbId){_deliver(cbId,{ok:true,results:[]});},
    scanFrameText:function(b64,cbId){_deliver(cbId,{ok:true,text:'',blocks:[]});}
  };

  var location={
    getLocation:function(cbId){_unsupported('location.getLocation',cbId);},
    watchPosition:function(fn){console.warn('[Preview] location.watchPosition not available in preview');},
    watchPositionWithError:function(fn,errFn){
      console.warn('[Preview] location not available in preview');
      try{eval(errFn+"('Location not available in preview')");}catch(e){}
    },
    stopWatching:function(){},
    startTracking:function(fn){console.warn('[Preview] location.startTracking not available');},
    startTrackingWithOptions:function(fn,opts){console.warn('[Preview] location tracking not available');},
    stopTracking:function(){},
    addGeofence:function(id,lat,lon,radius,fn){console.warn('[Preview] Geofencing not available');},
    removeGeofence:function(id){},
    removeAllGeofences:function(){}
  };

  var contacts={
    getContacts:function(cbId){_unsupported('contacts.getContacts',cbId);}
  };

  var sms={
    send:function(number,msg,cbId){_unsupported('sms.send',cbId);}
  };

  var calendar={
    getEvents:function(cbId){_unsupported('calendar.getEvents',cbId);},
    addEvent:function(cbId){_unsupported('calendar.addEvent',cbId);}
  };

  var biometric={
    authenticate:function(title,subtitle,cbId){_unsupported('biometric.authenticate',cbId);}
  };

  var nfc={
    isAvailable:function(){return false;},
    startReading:function(fn){console.warn('[Preview] NFC not available in preview');},
    stopReading:function(){},
    writeText:function(text,cbId){_unsupported('nfc.writeText',cbId);},
    writeUri:function(uri,cbId){_unsupported('nfc.writeUri',cbId);}
  };

  // ── Networking bridges (stubs — not available in preview) ──
  var ble={
    isEnabled:function(){return false;},
    startScan:function(fn){_unsupported('ble.startScan');},
    stopScan:function(){},
    connect:function(addr,cbId){_unsupported('ble.connect',cbId);},
    disconnect:function(){},
    read:function(a,s,c,cbId){_unsupported('ble.read',cbId);},
    write:function(a,s,c,d,cbId){_unsupported('ble.write',cbId);},
    subscribe:function(){console.warn('[Preview] BLE not available');},
    unsubscribe:function(){},
    getConnectedDevices:function(){return '[]';}
  };

  var ssh={
    connect:function(opts,cbId){_unsupported('ssh.connect',cbId);},
    exec:function(cmd,cbId){_unsupported('ssh.exec',cbId);},
    shell:function(cbId){_unsupported('ssh.shell',cbId);},
    send:function(){},resize:function(){},
    onData:function(){},onClose:function(){},
    disconnect:function(){},
    isConnected:function(){return false;},
    upload:function(l,r,cbId){_unsupported('ssh.upload',cbId);},
    download:function(r,l,cbId){_unsupported('ssh.download',cbId);},
    listDir:function(p,cbId){_unsupported('ssh.listDir',cbId);},
    forwardLocal:function(lp,rh,rp,cbId){_unsupported('ssh.forwardLocal',cbId);},
    forwardRemote:function(rp,lh,lp,cbId){_unsupported('ssh.forwardRemote',cbId);},
    removeForward:function(){},removeRemoteForward:function(){}
  };

  var smb={
    connect:function(opts,cbId){_unsupported('smb.connect',cbId);},
    listDir:function(p,cbId){_unsupported('smb.listDir',cbId);},
    download:function(r,l,cbId){_unsupported('smb.download',cbId);},
    upload:function(l,r,cbId){_unsupported('smb.upload',cbId);},
    delete:function(p,cbId){_unsupported('smb.delete',cbId);},
    mkdir:function(p,cbId){_unsupported('smb.mkdir',cbId);},
    copy:function(s,d,cbId){_unsupported('smb.copy',cbId);},
    rename:function(o,n,cbId){_unsupported('smb.rename',cbId);},
    getFileInfo:function(p,cbId){_unsupported('smb.getFileInfo',cbId);},
    exists:function(p,cbId){_unsupported('smb.exists',cbId);},
    disconnect:function(){},
    isConnected:function(){return false;},
    listShares:function(h,o,cbId){_unsupported('smb.listShares',cbId);}
  };

  var httpServer={
    start:function(p,tls,cbId){_unsupported('httpServer.start',cbId);},
    stop:function(){},
    onRequest:function(){},
    respond:function(){},respondFile:function(){},
    getCertificatePem:function(){return '';},
    getCertificateFingerprint:function(){return '';},
    getLocalIpAddress:function(){return '127.0.0.1';}
  };

  var httpClient={
    request:function(opts,cbId){_unsupported('httpClient.request',cbId);},
    requestFile:function(opts,dest,cbId){_unsupported('httpClient.requestFile',cbId);},
    uploadFile:function(opts,fp,cbId){_unsupported('httpClient.uploadFile',cbId);},
    uploadMultipart:function(opts,parts,cbId){_unsupported('httpClient.uploadMultipart',cbId);},
    getCookies:function(){return '[]';},
    setCookie:function(){},clearCookies:function(){}
  };

  var tcp={
    open:function(h,p,tls,cbId){_unsupported('tcp.open',cbId);},
    openTrustPin:function(h,p,fp,cbId){_unsupported('tcp.openTrustPin',cbId);},
    send:function(){},sendHex:function(){},sendFile:function(){},
    onData:function(){},onClose:function(){},
    close:function(){},
    isConnected:function(){return false;}
  };

  var udp={
    open:function(p,cbId){_unsupported('udp.open',cbId);},
    close:function(){},
    send:function(){},sendHex:function(){},
    onReceive:function(){},
    joinMulticast:function(){},leaveMulticast:function(){}
  };

  var nsd={
    register:function(t,n,p,txt,cbId){_unsupported('nsd.register',cbId);},
    unregister:function(){},
    startDiscovery:function(t,fn){console.warn('[Preview] NSD not available');},
    stopDiscovery:function(){},
    resolve:function(t,n,cbId){_unsupported('nsd.resolve',cbId);}
  };

  var wifiDirect={
    createGroup:function(cbId){_unsupported('wifiDirect.createGroup',cbId);},
    removeGroup:function(){},
    discoverPeers:function(fn){console.warn('[Preview] WiFi Direct not available');},
    stopDiscovery:function(){},
    connect:function(addr,cbId){_unsupported('wifiDirect.connect',cbId);},
    disconnect:function(){},
    getConnectionInfo:function(cbId){_unsupported('wifiDirect.getConnectionInfo',cbId);},
    onConnectionChanged:function(){}
  };

  var push={
    isAvailable:function(){return false;},
    getToken:function(cbId){_unsupported('push.getToken',cbId);},
    onMessage:function(){console.warn('[Preview] Push not available');},
    onTokenRefresh:function(){}
  };

  var widget={
    update:function(json){console.log('[Preview] Widget update:', JSON.stringify(typeof json==='string'?JSON.parse(json):json));},
    clear:function(){console.log('[Preview] Widget cleared');},
    onAction:function(fn){console.log('[Preview] Widget onAction registered');}
  };

  var download={
    enqueue:function(url,name,fn){console.warn('[Preview] Download not available in preview');},
    cancel:function(){}
  };

  var media={
    getImages:function(cbId){_deliver(cbId,{ok:true,images:[]});},
    loadThumbnail:function(cbId){_unsupported('media.loadThumbnail',cbId);},
    loadImage:function(cbId){_unsupported('media.loadImage',cbId);},
    getVideos:function(cbId){_deliver(cbId,{ok:true,videos:[]});},
    getAudio:function(cbId){_deliver(cbId,{ok:true,audio:[]});},
    playAudio:function(){console.warn('[Preview] media.playAudio not available');},
    saveToGallery:function(cbId){_unsupported('media.saveToGallery',cbId);},
    getMetadata:function(cbId){_unsupported('media.getMetadata',cbId);},
    pickImage:function(cbId){_unsupported('media.pickImage',cbId);}
  };

  // ── Assemble the iappyx object (same structure as ShellActivity wrapper) ──
  window.iappyx={
    storage:storage, device:device, camera:camera,
    location:location, notification:notification,
    vibration:vibration, clipboard:clipboard,
    sensor:sensor, tts:tts,
    alarm:alarm, audio:audio, screen:screen,
    contacts:contacts, sms:sms, calendar:calendar,
    biometric:biometric, nfc:nfc, sqlite:sqlite,
    ble:ble, ssh:ssh, smb:smb,
    httpServer:httpServer, httpClient:httpClient,
    tcp:tcp, udp:udp, nsd:nsd,
    wifiDirect:wifiDirect, push:push,
    download:download, media:media, widget:widget,
    // Top-level convenience methods (same as ShellActivity)
    save:function(k,v){storage.save(k,v);},
    load:function(k){return storage.load(k);},
    remove:function(k){storage.remove(k);},
    getPackageName:function(){return device.getPackageName();},
    getAppName:function(){return device.getAppName();},
    sharePhoto:sharePhoto,
    shareText:shareText,
    capabilities:capabilities,
    onTextSelected:function(fn){console.log('[Preview] onTextSelected registered');}
  };

  console.log('[Preview] iappyx bridge loaded (preview mode — some features simulated)');
})();
</script>
''';

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..addJavaScriptChannel('ConsoleChannel', onMessageReceived: _onConsoleMessage)
      ..addJavaScriptChannel('BridgeChannel', onMessageReceived: _onBridgeMessage)
      ..loadHtmlString(_injectScripts(widget.htmlContent), baseUrl: 'https://localhost/');
  }

  void _onConsoleMessage(JavaScriptMessage msg) {
    try {
      final data = jsonDecode(msg.message);
      setState(() {
        _console.add(_ConsoleLine(data['type'] ?? 'log', data['msg'] ?? ''));
        if (_console.length > 200) _console.removeRange(0, _console.length - 200);
      });
    } catch (_) {
      setState(() {
        _console.add(_ConsoleLine('log', msg.message));
        if (_console.length > 200) _console.removeRange(0, _console.length - 200);
      });
    }
  }

  void _onBridgeMessage(JavaScriptMessage msg) {
    try {
      final data = jsonDecode(msg.message) as Map<String, dynamic>;
      final bridge = data['bridge'] as String? ?? '';
      final method = data['method'] as String? ?? '';
      final args = data['args'] as Map<String, dynamic>? ?? {};
      _handleBridge(bridge, method, args);
    } catch (e) {
      debugPrint('Bridge message error: $e');
    }
  }

  void _handleBridge(String bridge, String method, Map<String, dynamic> args) {
    switch (bridge) {
      case 'vibration':
        _handleVibration(method);
        break;
      case 'clipboard':
        if (method == 'write') {
          final text = args['text'] as String? ?? '';
          Clipboard.setData(ClipboardData(text: text));
          if (mounted) {
            _controller.runJavaScript("window.iappyx._store=window.iappyx._store||{};window.iappyx._store['__clipboard']=${jsonEncode(text)};");
          }
        }
        break;
      case 'tts':
        _handleTts(method, args);
        break;
      case 'audio':
        _handleAudio(method, args);
        break;
      case 'share':
        _handleShare(method, args);
        break;
      case 'notification':
        // Show a snackbar as a visual cue
        if (method == 'send' && mounted) {
          final title = args['title'] ?? '';
          final body = args['body'] ?? '';
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('$title: $body'), duration: const Duration(seconds: 2)),
          );
        }
        break;
    }
  }

  void _handleVibration(String method) {
    switch (method) {
      case 'click':
        HapticFeedback.mediumImpact();
        break;
      case 'tick':
        HapticFeedback.lightImpact();
        break;
      case 'heavyClick':
        HapticFeedback.heavyImpact();
        break;
      case 'vibrate':
      case 'pattern':
        HapticFeedback.vibrate();
        break;
    }
  }

  void _handleTts(String method, Map<String, dynamic> args) {
    // Route TTS to native Android via existing MethodChannel
    switch (method) {
      case 'speak':
        _channel.invokeMethod('ttsSpeak', args['text'] ?? '');
        break;
      case 'stop':
        _channel.invokeMethod('ttsStop');
        break;
      case 'setLanguage':
        _channel.invokeMethod('ttsSetLanguage', args['lang'] ?? 'en');
        break;
      case 'setPitch':
        _channel.invokeMethod('ttsSetPitch', args['pitch'] ?? '1.0');
        break;
      case 'setRate':
        _channel.invokeMethod('ttsSetRate', args['rate'] ?? '1.0');
        break;
    }
  }

  void _handleAudio(String method, Map<String, dynamic> args) {
    switch (method) {
      case 'play':
        _channel.invokeMethod('audioPlay', args['url'] ?? '');
        break;
      case 'pause':
        _channel.invokeMethod('audioPause');
        break;
      case 'resume':
        _channel.invokeMethod('audioResume');
        break;
      case 'stop':
        _channel.invokeMethod('audioStop');
        break;
      case 'seekTo':
        _channel.invokeMethod('audioSeekTo', args['ms'] ?? 0);
        break;
      case 'setVolume':
        _channel.invokeMethod('audioSetVolume', args['volume'] ?? 1.0);
        break;
      case 'setLooping':
        _channel.invokeMethod('audioSetLooping', args['loop'] ?? false);
        break;
    }
  }

  void _handleShare(String method, Map<String, dynamic> args) {
    switch (method) {
      case 'shareText':
        try {
          _channel.invokeMethod('shareText', {
            'content': args['text'] ?? '',
            'filename': 'shared.txt',
          });
        } catch (_) {
          debugPrint('[Preview] shareText not available');
        }
        break;
      case 'sharePhoto':
        debugPrint('[Preview] sharePhoto not available in preview');
        break;
    }
  }

  String _injectScripts(String html) {
    // Insert console capture + bridge shim right after <head> or at the very start
    final scripts = _consoleCapture + _bridgeShim;
    final headIdx = html.toLowerCase().indexOf('<head');
    if (headIdx != -1) {
      final closeIdx = html.indexOf('>', headIdx);
      if (closeIdx != -1) {
        return html.substring(0, closeIdx + 1) + scripts + html.substring(closeIdx + 1);
      }
    }
    return scripts + html;
  }

  Color _colorForType(String type) {
    switch (type) {
      case 'error': return const Color(0xFFFF6B6B);
      case 'warn': return Colors.amber;
      case 'info': return const Color(0xFF4FC3F7);
      default: return Colors.white70;
    }
  }

  String _prefixForType(String type) {
    switch (type) {
      case 'error': return '[ERR]';
      case 'warn': return '[WRN]';
      case 'info': return '[INF]';
      default: return '[LOG]';
    }
  }

  @override
  void dispose() {
    // Stop any audio/TTS started during preview
    try { _channel.invokeMethod('audioStop'); } catch (_) {}
    try { _channel.invokeMethod('ttsStop'); } catch (_) {}
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0D0D1A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0D0D1A),
        title: const Text('Preview', style: TextStyle(fontSize: 16)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, size: 20),
            onPressed: () {
              setState(() => _console.clear());
              _controller.loadHtmlString(
                _injectScripts(widget.htmlContent),
                baseUrl: 'https://localhost/',
              );
            },
          ),
          Stack(children: [
            IconButton(
              icon: Icon(_consoleExpanded ? Icons.expand_less : Icons.expand_more, size: 20,
                color: _consoleExpanded ? const Color(0xFF4FC3F7) : Colors.white54),
              onPressed: () => setState(() => _consoleExpanded = !_consoleExpanded),
            ),
            if (!_consoleExpanded && _console.any((l) => l.type == 'error'))
              Positioned(right: 8, top: 8, child: Container(
                width: 8, height: 8,
                decoration: BoxDecoration(color: const Color(0xFFFF6B6B), borderRadius: BorderRadius.circular(4)),
              )),
          ]),
        ],
      ),
      body: Column(
        children: [
          // Bridge status banner
          if (_bannerVisible)
            GestureDetector(
              onTap: () => setState(() => _bannerVisible = false),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                color: const Color(0xFF1A1A2E),
                child: const Row(children: [
                  Expanded(child: Text(
                    'Preview mode — bridges simulated. Camera, location, contacts, NFC require a built APK.',
                    style: TextStyle(fontSize: 11, color: Colors.white38),
                  )),
                  SizedBox(width: 8),
                  Icon(Icons.close, size: 14, color: Colors.white24),
                ]),
              ),
            ),
          // WebView
          Expanded(
            flex: _consoleExpanded ? 6 : 10,
            child: WebViewWidget(controller: _controller),
          ),
          // Console
          if (_consoleExpanded)
            Expanded(
              flex: 4,
              child: Container(
                width: double.infinity,
                decoration: const BoxDecoration(
                  color: Color(0xFF0A0A14),
                  border: Border(top: BorderSide(color: Color(0xFF1A1A2E))),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('Console (${_console.length})',
                              style: const TextStyle(fontSize: 12, color: Colors.white38)),
                          Row(mainAxisSize: MainAxisSize.min, children: [
                            if (_console.isNotEmpty) GestureDetector(
                              onTap: () {
                                final text = _console.where((l) => !l.message.startsWith('[Preview]')).map((l) => '${_prefixForType(l.type)} ${l.message}').join('\n');
                                Clipboard.setData(ClipboardData(text: text));
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('Console copied to clipboard'), duration: Duration(seconds: 1)),
                                );
                              },
                              child: const Text('Copy', style: TextStyle(fontSize: 12, color: Color(0xFF4FC3F7))),
                            ),
                            if (_console.isNotEmpty) const SizedBox(width: 16),
                            GestureDetector(
                              onTap: () => setState(() => _console.clear()),
                              child: const Text('Clear', style: TextStyle(fontSize: 12, color: Colors.white38)),
                            ),
                          ]),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: Color(0xFF1A1A2E)),
                    Expanded(
                      child: _console.isEmpty
                          ? const Center(
                              child: Text('No console output yet.',
                                  style: TextStyle(fontSize: 12, color: Colors.white24)),
                            )
                          : ListView.builder(
                              padding: const EdgeInsets.all(8),
                              itemCount: _console.length,
                              itemBuilder: (_, i) {
                                final line = _console[i];
                                return Padding(
                                  padding: const EdgeInsets.only(bottom: 4),
                                  child: Text(
                                    '${_prefixForType(line.type)} ${line.message}',
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontFamily: 'monospace',
                                      color: _colorForType(line.type),
                                    ),
                                  ),
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
