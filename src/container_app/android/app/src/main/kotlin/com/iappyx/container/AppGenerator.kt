package com.iappyx.container

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppGenerator(private val context: Context) {

    companion object {
        private const val TAG = "iappyxOS"
        private const val TEMPLATE_ASSET = "flutter_assets/assets/shell_template.apk"
        private const val TEMPLATE_ASSET_WEB = "flutter_assets/assets/shell_template_web.apk"
    }

    private val injector = ApkInjector(KeyManager.KEY_ALIAS)
    private val installer = ApkInstaller(context)
    private val workDir get() = File(context.filesDir, "iappyxos_work")
    @Volatile private var building = false

    fun cleanWorkDir() {
        val dir = workDir
        if (!dir.exists()) return
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("unsigned_") || f.name.startsWith("generated_") || f.name == "shell_template.apk") {
                f.delete()
            }
        }
        Log.i(TAG, "Cleaned work directory")
    }

    suspend fun generateAndInstall(
        appLabel: String,
        templateId: String,
        existingPackageName: String? = null,
        iconConfig: String? = null,
        onProgress: (String) -> Unit,
    ): Map<String, String> {
        if (building) throw IllegalStateException("A build is already in progress")
        building = true
        try {
        val packageName = existingPackageName?.takeIf { it.isNotBlank() } ?: generatePackageName(appLabel)

        val outputApk = withContext(Dispatchers.IO) {
            workDir.mkdirs()

            withContext(Dispatchers.Main) { onProgress("\uD83D\uDCE6 Package: $packageName") }

            withContext(Dispatchers.Main) { onProgress("\uD83D\uDCC2 Loading template APK...") }
            val templateApk = extractTemplateApk()

            withContext(Dispatchers.Main) { onProgress("\u270D\uFE0F Generating app content...") }
            val assets = getPresetApp(templateId, appLabel)

            withContext(Dispatchers.Main) { onProgress("\uD83C\uDFA8 Generating icon...") }
            val icons = IconGenerator.generateAllFromConfig(iconConfig, appLabel)

            withContext(Dispatchers.Main) { onProgress("\uD83D\uDD27 Injecting and signing APK...") }
            val outputApk = File(workDir, "generated_${System.currentTimeMillis()}.apk")
            injector.inject(
                templateApk = templateApk,
                outputApk = outputApk,
                packageName = packageName,
                appLabel = appLabel,
                assets = assets,
                icons = icons,
            )
            outputApk
        }

        val sigStatus = KeyManager.checkSignatureMatch(context, packageName)
        if (sigStatus == "mismatch") {
            throw Exception("SIGNATURE_CONFLICT:$packageName")
        }

        withContext(Dispatchers.Main) {
            onProgress("\uD83D\uDCF2 Launching installer...")
            installer.install(outputApk)
            onProgress("\u2705 Done! Check your launcher for \"$appLabel\"")
        }

        cleanWorkDirExcept(outputApk)
        return mapOf("packageName" to packageName, "apkPath" to outputApk.absolutePath)
        } finally { building = false }
    }

    suspend fun injectAndInstall(
        appLabel: String,
        htmlContent: String,
        existingPackageName: String? = null,
        iconConfig: String? = null,
        firebaseConfig: String? = null,
        webOnly: Boolean = false,
        onProgress: (String) -> Unit,
    ): Map<String, String> {
        if (building) throw IllegalStateException("A build is already in progress")
        building = true
        try {
        val packageName = existingPackageName?.takeIf { it.isNotBlank() } ?: generatePackageName(appLabel)

        val outputApk = withContext(Dispatchers.IO) {
            workDir.mkdirs()

            withContext(Dispatchers.Main) { onProgress("\uD83D\uDCE6 Package: $packageName") }

            withContext(Dispatchers.Main) { onProgress("\uD83D\uDCC2 Loading template APK...") }
            val templateApk = extractTemplateApk(if (webOnly) TEMPLATE_ASSET_WEB else TEMPLATE_ASSET)

            val cleanHtml = htmlContent.replace("<!-- Built with iappyxOS — https://github.com/iappyx/iappyxOS -->\n", "")
            val taggedHtml = "<!-- Built with iappyxOS — https://github.com/iappyx/iappyxOS -->\n$cleanHtml"
            val assets = mutableMapOf("index.html" to taggedHtml.toByteArray(Charsets.UTF_8))
            if (!firebaseConfig.isNullOrBlank()) {
                assets["firebase_config.json"] = firebaseConfig.toByteArray(Charsets.UTF_8)
            }

            withContext(Dispatchers.Main) { onProgress("\uD83C\uDFA8 Generating icon...") }
            val icons = IconGenerator.generateAllFromConfig(iconConfig, appLabel)

            withContext(Dispatchers.Main) { onProgress("\uD83D\uDD27 Injecting and signing APK...") }
            val outputApk = File(workDir, "generated_${System.currentTimeMillis()}.apk")
            injector.inject(
                templateApk = templateApk,
                outputApk = outputApk,
                packageName = packageName,
                appLabel = appLabel,
                assets = assets,
                icons = icons,
            )
            outputApk
        }

        // Check for signature conflict before installing
        val sigStatus = KeyManager.checkSignatureMatch(context, packageName)
        if (sigStatus == "mismatch") {
            throw Exception("SIGNATURE_CONFLICT:$packageName")
        }

        withContext(Dispatchers.Main) {
            onProgress("\uD83D\uDCF2 Launching installer...")
            installer.install(outputApk)
            onProgress("\u2705 Done! Check your launcher for \"$appLabel\"")
        }

        cleanWorkDirExcept(outputApk)
        return mapOf("packageName" to packageName, "apkPath" to outputApk.absolutePath)
        } finally { building = false }
    }

    private fun cleanWorkDirExcept(keep: File) {
        workDir.listFiles()?.forEach { f ->
            if (f.absolutePath != keep.absolutePath) {
                try { f.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun extractTemplateApk(assetPath: String = TEMPLATE_ASSET): File {
        val dest = File(workDir, "shell_template.apk")
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        Log.i(TAG, "Template extracted: ${dest.length() / 1024}KB from $assetPath")
        return dest
    }

    private fun getPresetApp(templateId: String, label: String): Map<String, ByteArray> {
        val html = when (templateId) {
            "todo"      -> todoApp(label)
            "notes"     -> notesApp(label)
            "counter"   -> counterApp(label)
            "timer"     -> timerApp(label)
            "calculator" -> calculatorApp(label)
            "camera"    -> getCameraTestApp(label)
            "location"  -> getLocationTestApp(label)
            "device"    -> getDeviceTestApp(label)
            "clipboard" -> getClipboardTestApp(label)
            "sensor"    -> getSensorTestApp(label)
            "tts"       -> getTtsTestApp(label)
            "filepicker"  -> getFilePickerTestApp(label)
            "photoeditor"  -> getPhotoEditorApp(label)
            "alarmtest"    -> getAlarmTestApp(label)
            "audiotest"    -> getAudioTestApp(label)
            "screentest"   -> getScreenTestApp(label)
            "contactstest" -> getContactsTestApp(label)
            "smstest"      -> getSmsTestApp(label)
            "calendartest" -> getCalendarTestApp(label)
            "biotest"      -> getBiometricTestApp(label)
            "nfctest"      -> getNfcTestApp(label)
            "sqlitetest"   -> getSqliteTestApp(label)
            "stepcounter"  -> getStepCounterApp(label)
            "qrscanner"    -> getQrScannerApp(label)
            "voicerecorder"-> getVoiceRecorderApp(label)
            "connectivity" -> getConnectivityApp(label)
            "ocrtest"      -> getOcrTestApp(label)
            "speechtest"   -> getSpeechTestApp(label)
            "pdftest"      -> getPdfTestApp(label)
            "qrgen"        -> getQrGeneratorApp(label)
            "dashboard"    -> getDashboardApp(label)
            "mediatest"    -> getMediaTestApp(label)
            "flashlight"   -> getFlashlightApp(label)
            "classifier"   -> getClassifierApp(label)
            "runtracker"   -> getRunTrackerApp(label)
            "soundtools"   -> getSoundToolsApp(label)
            "printexport"  -> getPrintExportApp(label)
            "bgremover"    -> getBgRemoverApp(label)
            "smartnotif"   -> getSmartNotifApp(label)
            "sharemedia"   -> getShareMediaApp(label)
            "reminders"    -> getRemindersApp(label)
            "powertools"   -> getPowerToolsApp(label)
            "compass"      -> getCompassApp(label)
            "wallpaper"    -> getWallpaperApp(label)
            "mediagallery" -> getMediaGalleryApp(label)
            "downloadmgr"  -> getDownloadMgrApp(label)
            "blescan"      -> getBleScanApp(label)
            "lanshare"     -> getLanShareApp(label)
            "wifidirect"   -> getWifiDirectApp(label)
            "httpclient"   -> getHttpClientApp(label)
            "sshclient"    -> getSshClientApp(label)
            "networkfiles" -> getNetworkFilesApp(label)
            "tcpsocket"    -> getTcpSocketApp(label)
            "udpchat"      -> getUdpChatApp(label)
            else           -> todoApp(label)
        }
        val cleanHtml = html.replace("<!-- Built with iappyxOS — https://github.com/iappyx/iappyxOS -->\n", "")
        val taggedHtml = "<!-- Built with iappyxOS — https://github.com/iappyx/iappyxOS -->\n$cleanHtml"
        return mapOf("index.html" to taggedHtml.toByteArray(Charsets.UTF_8))
    }

    private fun generatePackageName(label: String): String {
        val prefix = getValidPrefix()
        val filtered = label.lowercase().filter { it.isLetterOrDigit() }.take(6).ifEmpty { "app" }
        // Ensure segment starts with a letter (Android package name requirement)
        val safe = if (filtered.first().isDigit()) "a$filtered".take(6) else filtered
        val base = "$prefix.$safe${System.currentTimeMillis() % 100_000_000L}"
        // Max 42 chars — must not exceed TEMPLATE_PACKAGE placeholder length (42 chars)
        return if (base.length > 42) base.take(42) else base
    }

    private fun getValidPrefix(): String {
        val adjectives = arrayOf("moai","kreas","noflik","leaf","sierlik",
            "prachtich","swiid","skjin","ljocht","skoander","smuk")
        val nouns = arrayOf("ljouwert","snits","drylts","sleat","starum",
            "hylpen","warkum","boalsert","harns","frjentsjer","dokkum")
        fun generatePrefix(): String {
            val r = System.currentTimeMillis()
            return "${adjectives[(r % adjectives.size).toInt()]}.${nouns[((r / 100) % nouns.size).toInt()]}"
        }

        val maxLen = 20
        return try {
            val prefs = context.getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
            val prefix = prefs.getString("flutter.package_prefix", null)
            if (prefix == null || prefix.isEmpty() || prefix == "com.iappyx.g") {
                val gen = generatePrefix()
                prefs.edit().putString("flutter.package_prefix", gen).apply()
                return gen
            }
            if (prefix.length > maxLen) return generatePrefix()
            if (!prefix.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$"))) return generatePrefix()
            prefix
        } catch (e: Exception) {
            generatePrefix()
        }
    }

    // ── Preset Apps ──

    private fun todoApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh}
header{padding:20px 20px 12px;border-bottom:1px solid #1a1a2e}
h1{font-size:1.4rem;font-weight:700}
.subtitle{font-size:.8rem;color:rgba(255,255,255,.4);margin-top:2px}
.input-row{display:flex;gap:8px;padding:16px 20px;border-bottom:1px solid #1a1a2e}
input{flex:1;background:#1a1a2e;border:none;border-radius:10px;padding:12px 14px;color:#fff;font-size:1rem;outline:none}
input::placeholder{color:rgba(255,255,255,.3)}
button.add{background:#0f3460;border:none;border-radius:10px;padding:12px 16px;color:#fff;font-size:1.2rem;cursor:pointer}
.list{padding:8px 20px}
.item{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.item:last-child{border-bottom:none}
.check{width:22px;height:22px;border-radius:50%;border:2px solid #0f3460;cursor:pointer;flex-shrink:0;display:flex;align-items:center;justify-content:center;transition:.15s}
.item.done .check{background:#0f3460;border-color:#0f3460}
.item.done .check::after{content:'✓';color:#fff;font-size:.75rem}
.item.done .text{text-decoration:line-through;color:rgba(255,255,255,.3)}
.text{flex:1;font-size:.95rem}
.del{background:none;border:none;color:rgba(255,255,255,.2);font-size:1.1rem;cursor:pointer;padding:4px 8px}
.empty{text-align:center;color:rgba(255,255,255,.3);padding:40px;font-size:.9rem}
.footer{padding:12px 20px;font-size:.75rem;color:rgba(255,255,255,.3);text-align:center}
</style></head><body>
<header><h1>$label</h1><div class="subtitle" id="sub">0 tasks</div></header>
<div class="input-row">
  <input id="inp" type="text" placeholder="Add a task..." autocomplete="off">
  <button class="add" onclick="add()">+</button>
</div>
<div class="list" id="list"></div>
<div class="footer">⚡ iappyxOS</div>
<script>
var todos=[];
function save(){iappyx.save('todos',JSON.stringify(todos))}
function render(){
  var list=document.getElementById('list');
  var done=todos.filter(function(t){return t.done}).length;
  document.getElementById('sub').textContent=todos.length+' tasks, '+done+' done';
  if(!todos.length){list.innerHTML='<div class="empty">No tasks yet. Add one above!</div>';return}
  list.innerHTML=todos.map(function(t,i){
    return '<div class="item'+(t.done?' done':'')+'">'+
      '<div class="check" onclick="toggle('+i+')"></div>'+
      '<div class="text">'+t.text+'</div>'+
      '<button class="del" onclick="del('+i+')">✕</button>'+
    '</div>'
  }).join('');
}
function add(){
  var inp=document.getElementById('inp');
  var text=inp.value.trim();
  if(!text)return;
  todos.push({text:text,done:false});
  inp.value='';save();render();
}
function toggle(i){todos[i].done=!todos[i].done;save();render()}
function del(i){todos.splice(i,1);save();render()}
document.getElementById('inp').addEventListener('keydown',function(e){if(e.key==='Enter')add()});
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  todos=JSON.parse(iappyx.load('todos')||'[]');
  render();
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun notesApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;margin:0}
.screen{display:flex;flex-direction:column;min-height:100vh;transition:.15s}
.screen.hidden{display:none}
header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #1a1a2e;flex-shrink:0}
h1{font-size:1.3rem;font-weight:700}
.btn{background:#0f3460;border:none;border-radius:8px;padding:8px 14px;color:#fff;font-size:.85rem;cursor:pointer}
.btn.ghost{background:transparent;color:rgba(255,255,255,.5);border:1px solid rgba(255,255,255,.15)}
.list{padding:12px}
.note-card{background:#1a1a2e;border-radius:12px;padding:14px;margin-bottom:10px;cursor:pointer}
.note-card:active{opacity:.7}
.note-title{font-weight:600;font-size:.95rem;margin-bottom:4px}
.note-preview{font-size:.8rem;color:rgba(255,255,255,.4);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.note-date{font-size:.7rem;color:rgba(255,255,255,.25);margin-top:6px}
.editor-body{flex:1;display:flex;flex-direction:column;padding:12px 16px;gap:10px}
#note-title-inp{background:#1a1a2e;border:none;border-radius:8px;padding:12px;color:#fff;font-size:1.1rem;font-weight:600;outline:none;flex-shrink:0}
#note-body-inp{flex:1;background:transparent;border:none;color:#eaeaea;font-size:.95rem;resize:none;outline:none;font-family:inherit;line-height:1.7;min-height:200px}
.empty{text-align:center;color:rgba(255,255,255,.3);padding:60px 20px;font-size:.9rem}
.footer{padding:10px;text-align:center;font-size:.7rem;color:rgba(255,255,255,.2);flex-shrink:0}
</style></head><body>
<div class="screen" id="list-screen">
  <header><h1>$label</h1><button class="btn" onclick="newNote()">+ New</button></header>
  <div class="list" id="note-list"></div>
  <div class="footer">⚡ iappyxOS</div>
</div>
<div class="screen hidden" id="edit-screen">
  <header>
    <button class="btn ghost" onclick="closeNote()">← Back</button>
    <button class="btn ghost" onclick="deleteNote()">Delete</button>
  </header>
  <div class="editor-body">
    <input type="text" id="note-title-inp" placeholder="Title">
    <textarea id="note-body-inp" placeholder="Start writing..."></textarea>
  </div>
</div>
<script>
var notes=[], current=-1;
function save(){iappyx.save('notes',JSON.stringify(notes))}
function renderList(){
  var el=document.getElementById('note-list');
  if(!notes.length){el.innerHTML='<div class="empty">No notes yet.<br>Tap + New to start.</div>';return}
  el.innerHTML=notes.slice().reverse().map(function(n,ri){
    var i=notes.length-1-ri;
    return '<div class="note-card" onclick="openNote('+i+')">'+
      '<div class="note-title">'+(n.title||'Untitled')+'</div>'+
      '<div class="note-preview">'+(n.body||'No content')+'</div>'+
      '<div class="note-date">'+new Date(n.updated).toLocaleDateString()+'</div>'+
    '</div>';
  }).join('');
}
function newNote(){
  notes.push({title:'',body:'',updated:Date.now()});
  save(); openNote(notes.length-1);
}
function openNote(i){
  current=i;
  document.getElementById('note-title-inp').value=notes[i].title;
  document.getElementById('note-body-inp').value=notes[i].body;
  document.getElementById('list-screen').classList.add('hidden');
  document.getElementById('edit-screen').classList.remove('hidden');
  // Delay focus so screen transition completes first
  setTimeout(function(){
    var t=document.getElementById('note-title-inp');
    t.focus(); t.setSelectionRange(t.value.length,t.value.length);
  },200);
}
function closeNote(){
  notes[current].title=document.getElementById('note-title-inp').value.trim()||'Untitled';
  notes[current].body=document.getElementById('note-body-inp').value;
  notes[current].updated=Date.now();
  save(); renderList();
  document.getElementById('edit-screen').classList.add('hidden');
  document.getElementById('list-screen').classList.remove('hidden');
}
function deleteNote(){
  notes.splice(current,1); save();
  document.getElementById('edit-screen').classList.add('hidden');
  document.getElementById('list-screen').classList.remove('hidden');
  renderList();
}
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  notes=JSON.parse(iappyx.load('notes')||'[]');
  renderList();
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun counterApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:32px;user-select:none}
h1{font-size:1.2rem;font-weight:600;color:rgba(255,255,255,.6)}
.count{font-size:6rem;font-weight:700;letter-spacing:-2px;line-height:1}
.buttons{display:flex;gap:20px}
.btn{width:72px;height:72px;border-radius:50%;border:none;font-size:2rem;cursor:pointer;transition:.1s;display:flex;align-items:center;justify-content:center}
.btn:active{transform:scale(.92)}
.btn-minus{background:#1a1a2e;color:#fff}
.btn-plus{background:#0f3460;color:#fff}
.reset{background:none;border:1px solid rgba(255,255,255,.15);border-radius:20px;padding:8px 20px;color:rgba(255,255,255,.4);font-size:.8rem;cursor:pointer;margin-top:8px}
.footer{position:fixed;bottom:16px;font-size:.7rem;color:rgba(255,255,255,.2)}
</style></head><body>
<h1>$label</h1>
<div class="count" id="count">0</div>
<div class="buttons">
  <button class="btn btn-minus" onclick="change(-1)">−</button>
  <button class="btn btn-plus" onclick="change(1)">+</button>
</div>
<button class="reset" onclick="reset()">Reset</button>
<div class="footer">⚡ iappyxOS</div>
<script>
var n=0;
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}n=parseInt(iappyx.load('count')||'0');render();}
function render(){document.getElementById('count').textContent=n}
function change(d){if(typeof iappyx==='undefined')return;n+=d;iappyx.save('count',n.toString());render()}
function reset(){if(typeof iappyx==='undefined')return;n=0;iappyx.save('count','0');render()}
window.addEventListener('load',function(){setTimeout(init,200)});
</script></body></html>""".trimIndent()

    private fun timerApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:40px;user-select:none}
h1{font-size:1.2rem;color:rgba(255,255,255,.5)}
.time{font-size:5.5rem;font-weight:300;letter-spacing:2px;font-variant-numeric:tabular-nums}
.buttons{display:flex;gap:16px}
.btn{padding:14px 28px;border:none;border-radius:50px;font-size:1rem;cursor:pointer;transition:.1s}
.btn:active{opacity:.7}
.start{background:#0f3460;color:#fff}
.pause{background:#1a1a2e;color:#fff}
.reset{background:#1a1a2e;color:#fff}
.laps{max-height:200px;overflow-y:auto;width:240px}
.lap{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.85rem;color:rgba(255,255,255,.6)}
.footer{position:fixed;bottom:16px;font-size:.7rem;color:rgba(255,255,255,.2)}
</style></head><body>
<h1>$label</h1>
<div class="time" id="time">00:00.00</div>
<div class="buttons">
  <button class="btn start" id="startBtn" onclick="toggle()">Start</button>
  <button class="btn reset" onclick="addLap()" id="lapBtn" disabled>Lap</button>
  <button class="btn reset" onclick="resetTimer()">Reset</button>
</div>
<div class="laps" id="laps"></div>
<div class="footer">⚡ iappyxOS</div>
<script>
var running=false,start=0,elapsed=0,timer=null,laps=[];
function fmt(ms){
  var s=Math.floor(ms/1000),m=Math.floor(s/60),cs=Math.floor((ms%1000)/10);
  return (m<10?'0':'')+m+':'+(s%60<10?'0':'')+s%60+'.'+(cs<10?'0':'')+cs;
}
function tick(){elapsed=Date.now()-start;document.getElementById('time').textContent=fmt(elapsed)}
function toggle(){
  if(running){clearInterval(timer);running=false;document.getElementById('startBtn').textContent='Resume';document.getElementById('startBtn').style.background='#1a3060'}
  else{start=Date.now()-elapsed;timer=setInterval(tick,10);running=true;document.getElementById('startBtn').textContent='Pause';document.getElementById('startBtn').style.background='#0f3460';document.getElementById('lapBtn').disabled=false}
}
function addLap(){if(!running)return;laps.unshift(fmt(elapsed));var el=document.getElementById('laps');el.innerHTML=laps.map(function(l,i){return '<div class="lap"><span>Lap '+(laps.length-i)+'</span><span>'+l+'</span></div>'}).join('')}
function resetTimer(){clearInterval(timer);running=false;elapsed=0;laps=[];document.getElementById('time').textContent='00:00.00';document.getElementById('startBtn').textContent='Start';document.getElementById('startBtn').style.background='#0f3460';document.getElementById('lapBtn').disabled=true;document.getElementById('laps').innerHTML=''}
</script></body></html>""".trimIndent()

    private fun calculatorApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column}
.display{flex:1;display:flex;flex-direction:column;justify-content:flex-end;padding:24px 20px 16px;text-align:right}
.expr{font-size:.9rem;color:rgba(255,255,255,.4);min-height:20px;margin-bottom:4px;word-break:break-all}
.result{font-size:3.2rem;font-weight:300;letter-spacing:-1px;word-break:break-all}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#0a0a12}
.k{padding:0;border:none;font-size:1.4rem;cursor:pointer;display:flex;align-items:center;justify-content:center;height:80px;transition:.08s;font-family:inherit}
.k:active{opacity:.6}
.k.op{background:#0f3460;color:#fff}
.k.eq{background:#e94560;color:#fff}
.k.fn{background:#1a1a2e;color:rgba(255,255,255,.7)}
.k.num{background:#16213e;color:#fff}
.footer{text-align:center;padding:8px;font-size:.65rem;color:rgba(255,255,255,.15)}
</style></head><body>
<div class="display"><div class="expr" id="expr"></div><div class="result" id="result">0</div></div>
<div class="grid">
  <button class="k fn" onclick="ac()">AC</button>
  <button class="k fn" onclick="sign()">+/−</button>
  <button class="k fn" onclick="pct()">%</button>
  <button class="k op" onclick="op('/')">÷</button>
  <button class="k num" onclick="num('7')">7</button>
  <button class="k num" onclick="num('8')">8</button>
  <button class="k num" onclick="num('9')">9</button>
  <button class="k op" onclick="op('*')">×</button>
  <button class="k num" onclick="num('4')">4</button>
  <button class="k num" onclick="num('5')">5</button>
  <button class="k num" onclick="num('6')">6</button>
  <button class="k op" onclick="op('-')">−</button>
  <button class="k num" onclick="num('1')">1</button>
  <button class="k num" onclick="num('2')">2</button>
  <button class="k num" onclick="num('3')">3</button>
  <button class="k op" onclick="op('+')">+</button>
  <button class="k num" style="grid-column:span 2" onclick="num('0')">0</button>
  <button class="k num" onclick="dot()">.</button>
  <button class="k eq" onclick="eq()">=</button>
</div>
<div class="footer">⚡ iappyxOS</div>
<script>
var cur='0',prev='',oper='',fresh=false;
function upd(){document.getElementById('result').textContent=cur;document.getElementById('expr').textContent=prev+(oper?(' '+oper+' '):'')+(fresh?'':cur)}
function num(d){if(fresh){cur=d;fresh=false}else{cur=cur==='0'?d:cur+d}upd()}
function dot(){if(fresh){cur='0.';fresh=false}else if(!cur.includes('.'))cur+='.';upd()}
function ac(){cur='0';prev='';oper='';fresh=false;upd()}
function sign(){cur=String(-parseFloat(cur));upd()}
function pct(){cur=String(parseFloat(cur)/100);upd()}
function op(o){if(oper&&!fresh)eq();prev=cur;oper=o;fresh=true;upd()}
function eq(){if(!oper)return;var a=parseFloat(prev),b=parseFloat(cur),r;
  if(oper==='+')r=a+b;else if(oper==='-')r=a-b;else if(oper==='*')r=a*b;else if(oper==='/')r=a/b;
  cur=String(parseFloat(r.toPrecision(12)));prev='';oper='';fresh=true;upd()}
upd();
</script></body></html>""".trimIndent()

    // ── Phase 3 Test Apps ──

    fun getCameraTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;padding:24px}
h1{font-size:1.4rem}
.btn{background:#0f3460;border:none;border-radius:12px;padding:16px 32px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:280px}
.btn:active{opacity:.7}
#photo{width:100%;max-width:280px;border-radius:12px;display:none}
.status{font-size:.85rem;color:rgba(255,255,255,.4)}
</style></head><body>
<h1>📸 $label</h1>
<img id="photo">
<button class="btn" onclick="snap()">Take Photo</button>
<div class="status" id="status">Tap to take a photo</div>
<script>
function snap(){
  document.getElementById('status').textContent='Opening camera...';
  var cbId='photo_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){var img=document.getElementById('photo');img.src=r.dataUrl;img.style.display='block';document.getElementById('status').textContent='Photo taken!';}
    else{document.getElementById('status').textContent='Error: '+r.error;}
  };
  iappyx.camera.takePhoto(cbId);
}
</script></body></html>""".trimIndent()

    fun getLocationTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;padding:24px;text-align:center}
h1{font-size:1.4rem}.btn{background:#0f3460;border:none;border-radius:12px;padding:16px 32px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:280px}.btn:active{opacity:.7}
.card{background:#1a1a2e;border-radius:16px;padding:20px 24px;width:100%;max-width:280px;text-align:left}
.row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.9rem}
.row:last-child{border:none}.label{color:rgba(255,255,255,.4)}.val{font-weight:600}
.status{font-size:.85rem;color:rgba(255,255,255,.4)}
</style></head><body>
<h1>📍 $label</h1>
<div class="card" id="result" style="display:none">
  <div class="row"><span class="label">Latitude</span><span class="val" id="lat">-</span></div>
  <div class="row"><span class="label">Longitude</span><span class="val" id="lon">-</span></div>
  <div class="row"><span class="label">Accuracy</span><span class="val" id="acc">-</span></div>
</div>
<button class="btn" onclick="locate()">Get My Location</button>
<div class="status" id="status">Tap to get GPS location</div>
<script>
function locate(){
  document.getElementById('status').textContent='Getting location...';
  var cbId='loc_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){
      document.getElementById('lat').textContent=r.lat.toFixed(6);
      document.getElementById('lon').textContent=r.lon.toFixed(6);
      document.getElementById('acc').textContent=r.accuracy.toFixed(0)+'m';
      document.getElementById('result').style.display='block';
      document.getElementById('status').textContent='Location retrieved!';
    }else{document.getElementById('status').textContent='Error: '+r.error;}
  };
  iappyx.location.getLocation(cbId);
}
</script></body></html>""".trimIndent()

    fun getDeviceTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px}
h1{font-size:1.4rem}
.card{background:#1a1a2e;border-radius:16px;padding:20px 24px;width:100%;max-width:320px}
.row{display:flex;justify-content:space-between;align-items:center;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.9rem}
.row:last-child{border:none}.label{color:rgba(255,255,255,.4)}.val{font-weight:600}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:320px}
.btn:active{opacity:.7}
</style></head><body>
<h1>📱 $label</h1>
<div class="card">
  <div class="row"><span class="label">App Name</span><span class="val" id="appname">-</span></div>
  <div class="row"><span class="label">Package</span><span class="val" id="pkg" style="font-size:.75rem">-</span></div>
  <div class="row"><span class="label">Brand</span><span class="val" id="brand">-</span></div>
  <div class="row"><span class="label">Model</span><span class="val" id="model">-</span></div>
  <div class="row"><span class="label">Android SDK</span><span class="val" id="sdk">-</span></div>
  <div class="row"><span class="label">Battery</span><span class="val" id="bat">-</span></div>
</div>
<button class="btn" onclick="iappyx.vibration.pattern('100,50,100,50,300')">📳 Vibrate</button>
<script>
function init(){
  if(typeof iappyx==='undefined'||typeof iappyx.device==='undefined'){setTimeout(init,50);return;}
  try{
    var info=JSON.parse(iappyx.device.getDeviceInfo());
    document.getElementById('appname').textContent=iappyx.device.getAppName();
    document.getElementById('pkg').textContent=iappyx.device.getPackageName();
    document.getElementById('brand').textContent=info.brand;
    document.getElementById('model').textContent=info.model;
    document.getElementById('sdk').textContent='API '+info.sdk;
    document.getElementById('bat').textContent=info.battery+'%';
  }catch(e){document.getElementById('brand').textContent='Error: '+e.message;}
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── Phase 3 Bridge Test Apps ──

    fun getClipboardTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px}
h1{font-size:1.4rem}
textarea{width:100%;max-width:320px;height:100px;background:#1a1a2e;border:none;border-radius:12px;padding:14px;color:#fff;font-size:.95rem;resize:none;outline:none;font-family:inherit}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:320px}
.btn:active{opacity:.7}.btn+.btn{margin-top:8px;background:#1a1a2e}
.status{font-size:.8rem;color:rgba(255,255,255,.4);min-height:1.2em;text-align:center}
</style></head><body>
<h1>📋 $label</h1>
<textarea id="txt" placeholder="Type something to copy...">Hello from iappyxOS!</textarea>
<button class="btn" onclick="copy()">📋 Copy to Clipboard</button>
<button class="btn" onclick="paste()">📄 Read from Clipboard</button>
<div class="status" id="status"></div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function copy(){
  var text=document.getElementById('txt').value;
  iappyx.clipboard.write(text);
  document.getElementById('status').textContent='Copied: "'+text.slice(0,40)+'"';
}
function paste(){
  var text=iappyx.clipboard.read();
  if(text){document.getElementById('txt').value=text;document.getElementById('status').textContent='Read: "'+text.slice(0,40)+'"';}
  else{document.getElementById('status').textContent='Clipboard is empty';}
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    fun getSensorTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px}
h1{font-size:1.4rem}
.card{background:#1a1a2e;border-radius:16px;padding:20px 24px;width:100%;max-width:320px}
.row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.9rem}
.row:last-child{border:none}.label{color:rgba(255,255,255,.4)}.val{font-weight:600;font-variant-numeric:tabular-nums}
.btns{display:flex;gap:10px;width:100%;max-width:320px}
.btn{flex:1;background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:.9rem;cursor:pointer}
.btn:active{opacity:.7}.btn.stop{background:#1a1a2e}
canvas{width:100%;max-width:320px;height:100px;background:#1a1a2e;border-radius:12px}
</style></head><body>
<h1>📡 $label</h1>
<div class="card">
  <div class="row"><span class="label">X</span><span class="val" id="x">-</span></div>
  <div class="row"><span class="label">Y</span><span class="val" id="y">-</span></div>
  <div class="row"><span class="label">Z</span><span class="val" id="z">-</span></div>
  <div class="row"><span class="label">Sensor</span><span class="val" id="type">stopped</span></div>
</div>
<canvas id="c"></canvas>
<div class="btns">
  <button class="btn" onclick="startAccel()">📱 Accel</button>
  <button class="btn" onclick="startGyro()">🌀 Gyro</button>
  <button class="btn stop" onclick="stop()">⏹ Stop</button>
</div>
<script>
var history=[], canvas=document.getElementById('c'), ctx=canvas.getContext('2d');
canvas.width=320; canvas.height=100;
window.onSensor=function(d){
  document.getElementById('x').textContent=d.x.toFixed(3);
  document.getElementById('y').textContent=d.y.toFixed(3);
  document.getElementById('z').textContent=d.z.toFixed(3);
  history.push(d.x); if(history.length>64) history.shift();
  ctx.fillStyle='#1a1a2e'; ctx.fillRect(0,0,320,100);
  ctx.strokeStyle='#4FC3F7'; ctx.lineWidth=2; ctx.beginPath();
  history.forEach(function(v,i){
    var x=i*(320/64), y=50-v*4;
    i===0?ctx.moveTo(x,y):ctx.lineTo(x,y);
  }); ctx.stroke();
};
function startAccel(){
  if(typeof iappyx==='undefined')return;
  iappyx.sensor.startAccelerometer('window.onSensor');
  document.getElementById('type').textContent='accelerometer';
}
function startGyro(){
  if(typeof iappyx==='undefined')return;
  iappyx.sensor.startGyroscope('window.onSensor');
  document.getElementById('type').textContent='gyroscope';
}
function stop(){
  if(typeof iappyx!=='undefined') iappyx.sensor.stop();
  document.getElementById('type').textContent='stopped';
}
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;} startAccel();}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    fun getTtsTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px}
h1{font-size:1.4rem}
textarea{width:100%;max-width:320px;height:120px;background:#1a1a2e;border:none;border-radius:12px;padding:14px;color:#fff;font-size:.95rem;resize:none;outline:none;font-family:inherit;line-height:1.6}
select{width:100%;max-width:320px;background:#1a1a2e;border:none;border-radius:12px;padding:14px;color:#fff;font-size:.95rem;outline:none}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:320px}
.btn:active{opacity:.7}.btn+.btn{margin-top:8px;background:#1a1a2e}
</style></head><body>
<h1>🗣️ $label</h1>
<textarea id="txt">Hello! This is iappyxOS text to speech. It uses the native Android TTS engine.</textarea>
<select id="lang" onchange="setLang()">
  <option value="en">English</option>
  <option value="nl">Nederlands</option>
  <option value="de">Deutsch</option>
  <option value="fr">Français</option>
  <option value="es">Español</option>
</select>
<button class="btn" onclick="speak()">🔊 Speak</button>
<button class="btn" onclick="stop()">⏹ Stop</button>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function speak(){
  if(typeof iappyx==='undefined')return;
  iappyx.tts.speak(document.getElementById('txt').value);
}
function stop(){if(typeof iappyx!=='undefined') iappyx.tts.stop();}
function setLang(){if(typeof iappyx!=='undefined') iappyx.tts.setLanguage(document.getElementById('lang').value);}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    fun getFilePickerTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px}
h1{font-size:1.4rem}
.drop{width:100%;max-width:320px;height:160px;background:#1a1a2e;border-radius:16px;border:2px dashed rgba(255,255,255,.15);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;cursor:pointer}
.drop:active{opacity:.7}
.drop-icon{font-size:2.5rem}
.drop-text{font-size:.85rem;color:rgba(255,255,255,.4)}
img{max-width:320px;max-height:280px;border-radius:12px;display:none}
.info{font-size:.8rem;color:rgba(255,255,255,.4);text-align:center;min-height:1.2em}
input[type=file]{display:none}
</style></head><body>
<h1>📁 $label</h1>
<div class="drop" onclick="document.getElementById('fp').click()">
  <div class="drop-icon">📂</div>
  <div class="drop-text">Tap to pick a file</div>
</div>
<input type="file" id="fp" accept="image/*,text/*,.pdf" onchange="picked(this)">
<img id="preview">
<div class="info" id="info">Supports images, text files, PDFs</div>
<script>
function picked(input){
  var f=input.files[0];
  if(!f){return;}
  document.getElementById('info').textContent=f.name+' ('+Math.round(f.size/1024)+'KB, '+f.type+')';
  if(f.type.startsWith('image/')){
    var r=new FileReader();
    r.onload=function(e){
      var img=document.getElementById('preview');
      img.src=e.target.result; img.style.display='block';
    };
    r.readAsDataURL(f);
  }
}
</script></body></html>""".trimIndent()

    fun getPhotoEditorApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;overflow:hidden;user-select:none}

/* Header */
header{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;flex-shrink:0;border-bottom:1px solid rgba(255,255,255,.08)}
.header-title{font-size:1rem;font-weight:600}
.btn-text{background:none;border:none;color:#4FC3F7;font-size:.9rem;cursor:pointer;padding:6px 10px;border-radius:8px}
.btn-text:disabled{color:rgba(255,255,255,.2)}

/* Canvas area */
.canvas-wrap{flex:1;display:flex;align-items:center;justify-content:center;background:#0a0a12;position:relative;overflow:hidden}
canvas{max-width:100%;max-height:100%;display:block}
.placeholder{display:flex;flex-direction:column;align-items:center;gap:12px;color:rgba(255,255,255,.3)}
.placeholder-icon{font-size:4rem}
.placeholder-text{font-size:.9rem}

/* Bottom toolbar */
.toolbar{flex-shrink:0;background:#0d0d1a;border-top:1px solid rgba(255,255,255,.08)}
.tool-row{display:flex;gap:0;overflow-x:auto;padding:8px 12px;scrollbar-width:none}
.tool-row::-webkit-scrollbar{display:none}
.tool-btn{display:flex;flex-direction:column;align-items:center;gap:4px;padding:8px 12px;border:none;background:none;color:rgba(255,255,255,.6);cursor:pointer;border-radius:10px;flex-shrink:0;min-width:56px;transition:.15s}
.tool-btn.active{background:rgba(79,195,247,.15);color:#4FC3F7}
.tool-btn .icon{font-size:1.5rem}
.tool-btn .lbl{font-size:.65rem}

/* Emoji picker */
.emoji-tray{display:none;flex-wrap:wrap;gap:6px;padding:10px 12px;max-height:120px;overflow-y:auto;border-top:1px solid rgba(255,255,255,.08)}
.emoji-tray.open{display:flex}
.emoji-btn{font-size:1.8rem;background:none;border:none;cursor:pointer;padding:4px;border-radius:8px;line-height:1}
.emoji-btn:active{background:rgba(255,255,255,.1)}

/* Filter row */
.filter-tray{display:none;gap:8px;padding:10px 12px;overflow-x:auto;scrollbar-width:none;border-top:1px solid rgba(255,255,255,.08)}
.filter-tray::-webkit-scrollbar{display:none}
.filter-tray.open{display:flex}
.filter-btn{flex-shrink:0;display:flex;flex-direction:column;align-items:center;gap:4px;background:none;border:2px solid transparent;border-radius:10px;padding:6px 10px;cursor:pointer;color:rgba(255,255,255,.6);font-size:.7rem;transition:.15s}
.filter-btn.active{border-color:#4FC3F7;color:#4FC3F7}

/* Placed emoji on canvas */
.emoji-overlay{position:absolute;font-size:2.5rem;cursor:grab;line-height:1;touch-action:none}
.emoji-overlay.selected{outline:2px dashed rgba(79,195,247,.8);border-radius:4px}

/* Action buttons */
.actions{display:flex;gap:8px;padding:10px 12px;border-top:1px solid rgba(255,255,255,.08)}
.action-btn{flex:1;padding:13px;border:none;border-radius:12px;font-size:.9rem;font-weight:600;cursor:pointer;transition:.15s}
.action-btn:active{opacity:.7}
.btn-primary{background:#0f3460;color:#fff}
.btn-success{background:#1a5c3a;color:#fff}
</style></head><body>

<header>
  <div class="header-title">📸 $label</div>
  <button class="btn-text" id="shareBtn" onclick="sharePhoto()" disabled>Share ↗</button>
</header>

<div class="canvas-wrap" id="canvasWrap">
  <div class="placeholder" id="placeholder">
    <div class="placeholder-icon">📷</div>
    <div class="placeholder-text">Take a photo to get started</div>
  </div>
  <canvas id="canvas" style="display:none"></canvas>
  <!-- Emoji overlays rendered here as DOM elements for easy dragging -->
  <div id="emojiLayer" style="position:absolute;inset:0;pointer-events:none"></div>
</div>

<div class="toolbar">
  <!-- Filter tray -->
  <div class="filter-tray" id="filterTray">
    <button class="filter-btn active" onclick="applyFilter('none',this)">Original</button>
    <button class="filter-btn" onclick="applyFilter('grayscale',this)">B&W</button>
    <button class="filter-btn" onclick="applyFilter('sepia',this)">Sepia</button>
    <button class="filter-btn" onclick="applyFilter('vivid',this)">Vivid</button>
    <button class="filter-btn" onclick="applyFilter('cool',this)">Cool</button>
    <button class="filter-btn" onclick="applyFilter('warm',this)">Warm</button>
    <button class="filter-btn" onclick="applyFilter('fade',this)">Fade</button>
    <button class="filter-btn" onclick="applyFilter('noir',this)">Noir</button>
  </div>

  <!-- Emoji tray -->
  <div class="emoji-tray" id="emojiTray">
    ${listOf("😂","😍","🔥","💯","✨","🎉","❤️","😎","🤩","👑","💀","🫡",
             "🌈","⚡","🎸","🍕","🐶","🦋","🌸","💎","🚀","🎯","💪","🙌",
             "👀","🤔","😴","🥳","😤","🫶").joinToString("") {
        "<button class='emoji-btn' onclick='addEmoji(\"$it\")'>$it</button>"
    }}
  </div>

  <!-- Tool buttons -->
  <div class="tool-row">
    <button class="tool-btn" onclick="takePhoto()">
      <span class="icon">📷</span><span class="lbl">Camera</span>
    </button>
    <button class="tool-btn" id="filterToolBtn" onclick="toggleTray('filter')">
      <span class="icon">🎨</span><span class="lbl">Filter</span>
    </button>
    <button class="tool-btn" id="emojiToolBtn" onclick="toggleTray('emoji')">
      <span class="icon">😊</span><span class="lbl">Emoji</span>
    </button>
    <button class="tool-btn" onclick="rotatePhoto()">
      <span class="icon">🔄</span><span class="lbl">Rotate</span>
    </button>
    <button class="tool-btn" onclick="clearEmojis()">
      <span class="icon">🗑️</span><span class="lbl">Clear</span>
    </button>
  </div>

  <div class="actions">
    <button class="action-btn btn-primary" onclick="takePhoto()">📷 Take Photo</button>
    <button class="action-btn btn-success" id="shareBtn2" onclick="sharePhoto()" disabled>↗ Share</button>
  </div>
</div>

<script>
var originalImage = null;
var currentFilter = 'none';
var rotation = 0;
var emojis = []; // {el, x, y, emoji, size}
var hasPhoto = false;

// ── Wait for bridge ──
function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
}
window.addEventListener('load', () => setTimeout(init, 100));

// ── Camera ──
function takePhoto() {
  var cbId = 'photo_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    if (!r.ok) { alert('Camera error: ' + r.error); return; }
    loadPhoto(r.dataUrl);
  };
  iappyx.camera.takePhoto(cbId);
}

function loadPhoto(dataUrl) {
  var img = new Image();
  img.onload = function() {
    originalImage = img;
    rotation = 0;
    currentFilter = 'none';
    document.querySelectorAll('.filter-btn').forEach(function(b,i){b.classList.toggle('active',i===0)});
    clearEmojis();
    renderCanvas();
    document.getElementById('placeholder').style.display = 'none';
    document.getElementById('canvas').style.display = 'block';
    document.getElementById('shareBtn').disabled = false;
    document.getElementById('shareBtn2').disabled = false;
    hasPhoto = true;
  };
  img.src = dataUrl;
}

// ── Canvas rendering ──
function renderCanvas() {
  if (!originalImage) return;
  var canvas = document.getElementById('canvas');
  var wrap = document.getElementById('canvasWrap');
  var ww = wrap.clientWidth, wh = wrap.clientHeight;

  var sw = rotation % 180 === 0 ? originalImage.width : originalImage.height;
  var sh = rotation % 180 === 0 ? originalImage.height : originalImage.width;
  var scale = Math.min(ww / sw, wh / sh, 1);
  canvas.width  = Math.round(sw * scale);
  canvas.height = Math.round(sh * scale);

  var ctx = canvas.getContext('2d');
  ctx.save();
  ctx.translate(canvas.width/2, canvas.height/2);
  ctx.rotate(rotation * Math.PI / 180);
  ctx.drawImage(originalImage,
    -originalImage.width/2 * scale, -originalImage.height/2 * scale,
    originalImage.width * scale, originalImage.height * scale);
  ctx.restore();
  applyFilterToCanvas(ctx, canvas.width, canvas.height);
}

// ── Filters ──
var FILTERS = {
  none:      null,
  grayscale: function(d){for(var i=0;i<d.length;i+=4){var g=d[i]*.3+d[i+1]*.59+d[i+2]*.11;d[i]=d[i+1]=d[i+2]=g;}},
  sepia:     function(d){for(var i=0;i<d.length;i+=4){var r=d[i],g=d[i+1],b=d[i+2];d[i]=Math.min(255,r*.393+g*.769+b*.189);d[i+1]=Math.min(255,r*.349+g*.686+b*.168);d[i+2]=Math.min(255,r*.272+g*.534+b*.131);}},
  vivid:     function(d){for(var i=0;i<d.length;i+=4){d[i]=Math.min(255,d[i]*1.3);d[i+1]=Math.min(255,d[i+1]*1.2);d[i+2]=Math.min(255,d[i+2]*1.1);}},
  cool:      function(d){for(var i=0;i<d.length;i+=4){d[i]=Math.max(0,d[i]-20);d[i+2]=Math.min(255,d[i+2]+40);}},
  warm:      function(d){for(var i=0;i<d.length;i+=4){d[i]=Math.min(255,d[i]+40);d[i+2]=Math.max(0,d[i+2]-20);}},
  fade:      function(d){for(var i=0;i<d.length;i+=4){d[i]=d[i]*.8+51;d[i+1]=d[i+1]*.8+51;d[i+2]=d[i+2]*.8+51;}},
  noir:      function(d){for(var i=0;i<d.length;i+=4){var g=d[i]*.3+d[i+1]*.59+d[i+2]*.11;var c=g>128?Math.min(255,g*1.4):Math.max(0,g*.6);d[i]=d[i+1]=d[i+2]=c;}}
};

function applyFilterToCanvas(ctx, w, h) {
  if (!FILTERS[currentFilter]) return;
  var id = ctx.getImageData(0, 0, w, h);
  FILTERS[currentFilter](id.data);
  ctx.putImageData(id, 0, 0);
}

function applyFilter(name, btn) {
  currentFilter = name;
  document.querySelectorAll('.filter-btn').forEach(function(b){b.classList.remove('active')});
  btn.classList.add('active');
  renderCanvas();
}

// ── Rotation ──
function rotatePhoto() {
  rotation = (rotation + 90) % 360;
  renderCanvas();
}

// ── Emoji placement ──
function addEmoji(emoji) {
  var layer = document.getElementById('emojiLayer');
  var canvas = document.getElementById('canvas');
  var rect = canvas.getBoundingClientRect();

  var el = document.createElement('div');
  el.className = 'emoji-overlay';
  el.textContent = emoji;
  el.style.pointerEvents = 'auto';

  // Place at center of canvas
  var x = rect.left + rect.width/2 - 20;
  var y = rect.top  + rect.height/2 - 20;
  // Position relative to layer
  var layerRect = layer.parentElement.getBoundingClientRect();
  el.style.left = (x - layerRect.left) + 'px';
  el.style.top  = (y - layerRect.top)  + 'px';
  el.style.fontSize = '2.5rem';

  layer.style.pointerEvents = 'auto';
  layer.appendChild(el);
  emojis.push({el: el, emoji: emoji});

  makeDraggable(el);
  makePinchResizable(el);
}

function makeDraggable(el) {
  var ox, oy, sx, sy;
  el.addEventListener('touchstart', function(e) {
    if (e.touches.length === 1) {
      e.preventDefault();
      var t = e.touches[0];
      ox = parseInt(el.style.left)||0;
      oy = parseInt(el.style.top)||0;
      sx = t.clientX; sy = t.clientY;
      el.style.zIndex = 10;
    }
  }, {passive:false});
  el.addEventListener('touchmove', function(e) {
    if (e.touches.length === 1) {
      e.preventDefault();
      var t = e.touches[0];
      el.style.left = (ox + t.clientX - sx) + 'px';
      el.style.top  = (oy + t.clientY - sy) + 'px';
    }
  }, {passive:false});
}

function makePinchResizable(el) {
  var initDist, initSize;
  el.addEventListener('touchstart', function(e) {
    if (e.touches.length === 2) {
      e.preventDefault();
      initDist = Math.hypot(
        e.touches[0].clientX - e.touches[1].clientX,
        e.touches[0].clientY - e.touches[1].clientY);
      initSize = parseFloat(el.style.fontSize) || 40;
    }
  }, {passive:false});
  el.addEventListener('touchmove', function(e) {
    if (e.touches.length === 2) {
      e.preventDefault();
      var d = Math.hypot(
        e.touches[0].clientX - e.touches[1].clientX,
        e.touches[0].clientY - e.touches[1].clientY);
      var size = Math.max(20, Math.min(120, initSize * d / initDist));
      el.style.fontSize = size + 'px';
    }
  }, {passive:false});
}

function clearEmojis() {
  var layer = document.getElementById('emojiLayer');
  layer.innerHTML = '';
  emojis = [];
}

// ── Trays ──
function toggleTray(which) {
  var ft = document.getElementById('filterTray');
  var et = document.getElementById('emojiTray');
  var fb = document.getElementById('filterToolBtn');
  var eb = document.getElementById('emojiToolBtn');
  if (which === 'filter') {
    var open = ft.classList.toggle('open');
    et.classList.remove('open');
    fb.classList.toggle('active', open);
    eb.classList.remove('active');
  } else {
    var open = et.classList.toggle('open');
    ft.classList.remove('open');
    eb.classList.toggle('active', open);
    fb.classList.remove('active');
  }
}

// ── Share — flatten emojis onto canvas, then share as blob ──
function sharePhoto() {
  if (!hasPhoto) return;

  var srcCanvas = document.getElementById('canvas');
  var out = document.createElement('canvas');
  out.width  = srcCanvas.width;
  out.height = srcCanvas.height;
  var ctx = out.getContext('2d');

  // Draw filtered photo
  ctx.drawImage(srcCanvas, 0, 0);

  // Composite emoji overlays onto the output canvas
  // Emoji elements are positioned in screen px relative to canvasWrap
  // We need to map their screen position to canvas pixel coordinates
  var canvasRect = srcCanvas.getBoundingClientRect();
  var scaleX = out.width  / canvasRect.width;
  var scaleY = out.height / canvasRect.height;

  var emojiEls = document.getElementById('emojiLayer').children;
  for (var i = 0; i < emojiEls.length; i++) {
    var el = emojiEls[i];
    var r = el.getBoundingClientRect();
    var cx = (r.left - canvasRect.left) * scaleX;
    var cy = (r.top  - canvasRect.top)  * scaleY;
    // getComputedStyle gives the actual rendered font size in screen px
    var screenFontSize = parseFloat(window.getComputedStyle(el).fontSize);
    var canvasFontSize = screenFontSize * scaleX;
    ctx.font = canvasFontSize + 'px serif';
    ctx.textBaseline = 'top';
    ctx.fillText(el.textContent.trim(), cx, cy);
  }

  // Share via native bridge
  var dataUrl = out.toDataURL('image/jpeg', 0.92);
  var base64 = dataUrl.split(',')[1];
  iappyx.camera.sharePhoto(base64);
}
</script></body></html>""".trimIndent()

    // ── Alarm Test ──
    fun getAlarmTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:24px;text-align:center}
h1{font-size:1.4rem}
input[type=time]{background:#1a1a2e;border:none;border-radius:12px;padding:16px;color:#fff;font-size:1.5rem;outline:none;text-align:center;width:100%;max-width:280px}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:280px}
.btn:active{opacity:.7}.btn.danger{background:#3d1a1a}
.status{font-size:.85rem;color:rgba(255,255,255,.5);min-height:1.2em}
.scheduled{font-size:.8rem;color:#4FC3F7}
</style></head><body>
<h1>⏰ $label</h1>
<input type="time" id="timePicker">
<button class="btn" onclick="setAlarm()">⏰ Set Alarm</button>
<button class="btn danger" onclick="cancelAlarm()">✕ Cancel</button>
<div class="status" id="status">Pick a time and tap Set Alarm</div>
<div class="scheduled" id="scheduled"></div>
<script>
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  var ts=iappyx.alarm.getScheduled();
  if(ts) document.getElementById('scheduled').textContent='Alarm: '+new Date(parseInt(ts)).toLocaleTimeString();
}
window.onAlarm=function(){
  document.getElementById('status').textContent='ALARM FIRED!';
  document.body.style.background='#1a0000';
  iappyx.vibration.pattern('0,500,200,500,200,500');
  iappyx.notification.send('Alarm','Wake up!');
};
function setAlarm(){
  var t=document.getElementById('timePicker').value;
  if(!t){document.getElementById('status').textContent='Pick a time';return;}
  var p=t.split(':'), now=new Date();
  var alarm=new Date(now.getFullYear(),now.getMonth(),now.getDate(),parseInt(p[0]),parseInt(p[1]),0);
  if(alarm<=now) alarm.setDate(alarm.getDate()+1);
  iappyx.alarm.set(alarm.getTime(),'window.onAlarm');
  document.getElementById('status').textContent='Alarm set!';
  document.getElementById('scheduled').textContent='Fires at '+alarm.toLocaleTimeString();
}
function cancelAlarm(){
  iappyx.alarm.cancel();
  document.getElementById('status').textContent='Cancelled';
  document.getElementById('scheduled').textContent='';
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── Audio Test ──
    fun getAudioTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;padding:24px}
h1{font-size:1.4rem}
input{width:100%;max-width:320px;background:#1a1a2e;border:none;border-radius:12px;padding:12px 14px;color:#fff;font-size:.85rem;outline:none}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:320px}
.btn:active{opacity:.7}.btn+.btn{margin-top:8px;background:#1a1a2e}
input[type=range]{width:100%;max-width:320px;accent-color:#4FC3F7}
.label{font-size:.8rem;color:rgba(255,255,255,.4);align-self:flex-start;max-width:320px}
.status{font-size:.85rem;color:rgba(255,255,255,.5)}
</style></head><body>
<h1>🎵 $label</h1>
<div class="label">Audio URL</div>
<input id="url" type="url" placeholder="https://example.com/audio.mp3">
<div class="label">Volume</div>
<input type="range" id="vol" min="0" max="1" step="0.1" value="1" oninput="setVol(this.value)">
<button class="btn" onclick="play()">▶ Play</button>
<button class="btn" onclick="iappyx.audio.setLooping(true)">🔁 Enable Loop</button>
<button class="btn" onclick="stop()">⏹ Stop</button>
<div class="label" style="margin-top:8px">Audio Focus</div>
<button class="btn" onclick="reqFocus()">🎧 Request Focus</button>
<button class="btn" onclick="iappyx.audio.abandonFocus();st('Focus released')">🔇 Abandon Focus</button>
<div class="status" id="status">Enter a URL and tap Play</div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function play(){
  var url=document.getElementById('url').value.trim();
  if(!url){document.getElementById('status').textContent='Enter a URL';return;}
  iappyx.audio.requestFocus('window.onFocus');
  iappyx.audio.play(url);
  st('Playing...');
}
function stop(){iappyx.audio.stop();iappyx.audio.abandonFocus();st('Stopped');}
function setVol(v){if(typeof iappyx!=='undefined')iappyx.audio.setVolume(parseFloat(v));}
function reqFocus(){iappyx.audio.requestFocus('window.onFocus');st('Focus requested');}
function st(m){document.getElementById('status').textContent=m;}
window.onFocus=function(e){
  if(e.type==='loss'||e.type==='lossTransient'){iappyx.audio.pause();st('Paused (focus '+e.type+')');}
  else if(e.type==='duck'){iappyx.audio.setVolume(0.2);st('Ducking...');}
  else if(e.type==='gain'){iappyx.audio.setVolume(parseFloat(document.getElementById('vol').value));iappyx.audio.resume();st('Resumed (focus gain)');}
};
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── Screen Test ──
    fun getScreenTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:24px}
h1{font-size:1.4rem}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:.95rem;cursor:pointer;width:100%;max-width:280px}
.btn:active{opacity:.7}.btn+.btn{margin-top:8px;background:#1a1a2e}
input[type=range]{width:100%;max-width:280px;accent-color:#4FC3F7}
.label{font-size:.8rem;color:rgba(255,255,255,.4)}
.status{font-size:.85rem;color:rgba(255,255,255,.5);text-align:center}
</style></head><body>
<h1>💡 $label</h1>
<div class="label">Brightness</div>
<input type="range" id="bright" min="0" max="1" step="0.05" value="0.5"
  oninput="iappyx.screen.setBrightness(parseFloat(this.value))">
<button class="btn" onclick="iappyx.screen.keepOn(true);st('Screen kept ON')">🔆 Keep Screen On</button>
<button class="btn" onclick="iappyx.screen.keepOn(false);st('Auto-off restored')">🔅 Allow Screen Off</button>
<button class="btn" onclick="iappyx.screen.wakeLock(true);st('Wake lock acquired')">🔒 Wake Lock On</button>
<button class="btn" onclick="iappyx.screen.wakeLock(false);st('Wake lock released')">🔓 Wake Lock Off</button>
<button class="btn" onclick="iappyx.vibration.click();st('Click')">📳 Haptic Click</button>
<button class="btn" onclick="iappyx.vibration.tick();st('Tick')">📳 Haptic Tick</button>
<button class="btn" onclick="iappyx.vibration.heavyClick();st('Heavy click')">📳 Heavy Click</button>
<div class="status" id="status">Test controls</div>
<script>
function st(msg){document.getElementById('status').textContent=msg;}
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── Contacts Test ──
    fun getContactsTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column}
header{padding:16px 20px;border-bottom:1px solid #1a1a2e;flex-shrink:0}
h1{font-size:1.3rem;margin-bottom:8px}
.btn{background:#0f3460;border:none;border-radius:8px;padding:9px 14px;color:#fff;font-size:.85rem;cursor:pointer}
input{width:100%;background:#1a1a2e;border:none;border-radius:8px;padding:9px 12px;color:#fff;font-size:.9rem;outline:none;margin-top:8px;display:none}
.list{padding:8px 20px}
.contact{padding:12px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.name{font-weight:600;font-size:.9rem}
.phones{font-size:.78rem;color:rgba(255,255,255,.45);margin-top:2px}
.empty{text-align:center;color:rgba(255,255,255,.3);padding:40px;font-size:.9rem}
</style></head><body>
<header>
  <h1>👥 $label</h1>
  <button class="btn" onclick="load()">Load Contacts</button>
  <input id="search" type="text" placeholder="Search..." oninput="filter(this.value)">
</header>
<div class="list" id="list"><div class="empty">Tap Load Contacts</div></div>
<script>
var all=[];
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function load(){
  document.getElementById('list').innerHTML='<div class="empty">Loading...</div>';
  var cbId='c'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(!r.ok){document.getElementById('list').innerHTML='<div class="empty">'+r.error+'</div>';return;}
    all=r.contacts; document.getElementById('search').style.display='block'; render(all);
  };
  iappyx.contacts.getContacts(cbId);
}
function render(contacts){
  if(!contacts.length){document.getElementById('list').innerHTML='<div class="empty">No contacts</div>';return;}
  document.getElementById('list').innerHTML=contacts.map(function(c){
    return '<div class="contact"><div class="name">'+c.name+'</div><div class="phones">'+(c.phones.join(', ')||'—')+'</div></div>';
  }).join('');
}
function filter(q){render(!q?all:all.filter(function(c){return c.name.toLowerCase().includes(q.toLowerCase());}));}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── SMS Test ──
    fun getSmsTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:24px}
h1{font-size:1.4rem}
input,textarea{width:100%;max-width:320px;background:#1a1a2e;border:none;border-radius:12px;padding:12px 14px;color:#fff;font-size:.95rem;outline:none}
textarea{height:100px;resize:none;font-family:inherit}
.btn{background:#0f3460;border:none;border-radius:12px;padding:14px;color:#fff;font-size:1rem;cursor:pointer;width:100%;max-width:320px}
.btn:active{opacity:.7}
.status{font-size:.85rem;color:rgba(255,255,255,.5);text-align:center}
.warn{font-size:.75rem;color:rgba(255,165,0,.7);text-align:center;max-width:320px}
</style></head><body>
<h1>💬 $label</h1>
<input id="num" type="tel" placeholder="+31612345678">
<textarea id="msg" placeholder="Message...">Hello from iappyxOS!</textarea>
<button class="btn" onclick="sendSms()">Send SMS</button>
<div class="status" id="status">Enter number and message</div>
<div class="warn">⚠️ Sends a real SMS. Standard rates apply.</div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function sendSms(){
  var num=document.getElementById('num').value.trim();
  var msg=document.getElementById('msg').value.trim();
  if(!num||!msg){document.getElementById('status').textContent='Fill all fields';return;}
  var cbId='sms'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){document.getElementById('status').textContent=r.ok?'✅ Sent!':'❌ '+r.error;};
  iappyx.sms.send(num,msg,cbId);
  document.getElementById('status').textContent='Sending...';
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── Calendar Test ──
    fun getCalendarTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column}
header{padding:16px 20px;border-bottom:1px solid #1a1a2e;flex-shrink:0}
h1{font-size:1.3rem;margin-bottom:8px}
.btns{display:flex;gap:8px}
.btn{flex:1;background:#0f3460;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.82rem;cursor:pointer}
.btn.sec{background:#1a1a2e}
.list{padding:8px 20px}
.event{padding:12px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.event-title{font-weight:600;font-size:.9rem}
.event-time{font-size:.78rem;color:rgba(255,255,255,.45);margin-top:2px}
.empty{text-align:center;color:rgba(255,255,255,.3);padding:40px;font-size:.9rem}
</style></head><body>
<header>
  <h1>📅 $label</h1>
  <div class="btns">
    <button class="btn" onclick="loadEvents()">Load Events</button>
    <button class="btn sec" onclick="addEvent()">+ Add Event</button>
  </div>
</header>
<div class="list" id="list"><div class="empty">Tap Load Events</div></div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function loadEvents(){
  var cbId='cal'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(!r.ok){document.getElementById('list').innerHTML='<div class="empty">'+r.error+'</div>';return;}
    if(!r.events.length){document.getElementById('list').innerHTML='<div class="empty">No events</div>';return;}
    document.getElementById('list').innerHTML=r.events.map(function(e){
      return '<div class="event"><div class="event-title">'+e.title+'</div><div class="event-time">'+new Date(e.start).toLocaleString()+'</div></div>';
    }).join('');
  };
  iappyx.calendar.getEvents(cbId,String(Date.now()),String(Date.now()+30*24*60*60*1000));
}
function addEvent(){
  var cbId='caladd'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(){};
  var s=Date.now()+3600000;
  iappyx.calendar.addEvent(cbId,'iappyxOS Event',String(s),String(s+3600000),'Created by iappyxOS');
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── Biometric Test ──
    fun getBiometricTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;padding:24px;text-align:center}
h1{font-size:1.4rem}
.icon{font-size:5rem}
.btn{background:#0f3460;border:none;border-radius:12px;padding:16px 32px;color:#fff;font-size:1rem;cursor:pointer}
.btn:active{opacity:.7}
.status{font-size:1rem;min-height:1.4em;font-weight:500}
.ok{color:#69F0AE}.err{color:#FF6B6B}
</style></head><body>
<h1>🔐 $label</h1>
<div class="icon">👆</div>
<button class="btn" onclick="auth()">Authenticate</button>
<div class="status" id="status">Tap to use biometrics</div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
function auth(){
  var el=document.getElementById('status');
  el.className='status'; el.textContent='Waiting...';
  var cbId='bio'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){el.textContent='✅ Authenticated!';el.className='status ok';iappyx.vibration.click();}
    else{el.textContent='❌ '+r.error;el.className='status err';}
  };
  iappyx.biometric.authenticate('Verify Identity','Confirm it is you',cbId);
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── NFC Test ──
    fun getNfcTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;padding:20px;gap:12px}
h1{font-size:1.3rem;text-align:center}
.ring{width:120px;height:120px;border-radius:50%;border:3px solid #0f3460;display:flex;align-items:center;justify-content:center;font-size:2.5rem;transition:.3s;margin:0 auto}
.ring.active{border-color:#4FC3F7;animation:pulse 1.5s infinite}
@keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(79,195,247,.4)}50%{box-shadow:0 0 0 16px rgba(79,195,247,0)}}
.btns{display:flex;gap:10px;justify-content:center}
.btn{background:#0f3460;border:none;border-radius:12px;padding:12px 24px;color:#fff;font-size:.85rem;cursor:pointer}
.btn.sec{background:#1a1a2e}
.card{background:#1a1a2e;border-radius:12px;padding:14px;text-align:left}
.row{display:flex;justify-content:space-between;padding:5px 0;font-size:.8rem;border-bottom:1px solid rgba(255,255,255,.06)}
.row:last-child{border:none}.k{color:rgba(255,255,255,.4);flex-shrink:0;margin-right:8px}.v{word-break:break-all;text-align:right}
.status{font-size:.8rem;color:rgba(255,255,255,.5);text-align:center}
.records{display:flex;flex-direction:column;gap:8px}
.rec{background:#0d0d1a;border-radius:8px;padding:10px}
.rec-type{font-size:.7rem;color:#4FC3F7;margin-bottom:4px}
.rec-val{font-size:.85rem;word-break:break-all}
.rec-hex{font-size:.65rem;color:rgba(255,255,255,.25);margin-top:4px;font-family:monospace;word-break:break-all}
.uri{color:#4FC3F7;text-decoration:underline}
</style></head><body>
<h1>📡 $label</h1>
<div class="ring" id="ring">📶</div>
<div class="btns">
  <button class="btn" onclick="start()">Start</button>
  <button class="btn sec" onclick="stop()">Stop</button>
</div>
<div class="status" id="status">Hold NFC tag near device</div>
<div class="card" id="card" style="display:none">
  <div class="row"><span class="k">Tag ID</span><span class="v" id="tagId">-</span></div>
  <div class="row"><span class="k">Tech</span><span class="v" id="tagTech">-</span></div>
</div>
<div id="recordsSection" style="display:none">
  <div style="font-size:.75rem;color:rgba(255,255,255,.4);margin-top:4px">NDEF Records</div>
  <div class="records" id="records"></div>
</div>
<script>
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  document.getElementById('status').textContent=iappyx.nfc.isAvailable()?'NFC ready — tap Start':'NFC not available';
}
function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
window.onTag=function(t){
  document.getElementById('tagId').textContent=t.id||'(empty)';
  var techs=Array.isArray(t.tech)?t.tech.join(', '):t.tech;
  document.getElementById('tagTech').textContent=techs;
  document.getElementById('card').style.display='block';
  document.getElementById('ring').textContent='✅';
  document.getElementById('status').textContent='Tag detected!';
  iappyx.vibration.click();
  var recs=t.records||[];
  var el=document.getElementById('records');
  if(recs.length>0){
    document.getElementById('recordsSection').style.display='block';
    var html='';
    for(var i=0;i<recs.length;i++){
      var r=recs[i];
      html+='<div class="rec">';
      html+='<div class="rec-type">TNF='+r.tnf+' Type: '+esc(r.type||'')+'</div>';
      if(r.uri)html+='<div class="rec-val"><a class="uri" href="'+esc(r.uri)+'">'+esc(r.uri)+'</a></div>';
      if(r.text)html+='<div class="rec-val">'+esc(r.text)+'</div>';
      if(!r.uri&&!r.text)html+='<div class="rec-val" style="color:rgba(255,255,255,.4)">(no text/uri)</div>';
      if(r.payloadHex)html+='<div class="rec-hex">'+r.payloadHex+'</div>';
      html+='</div>';
    }
    el.innerHTML=html;
  }else{
    document.getElementById('recordsSection').style.display='none';
  }
};
function start(){iappyx.nfc.startReading('window.onTag');document.getElementById('ring').className='ring active';document.getElementById('status').textContent='Scanning...';}
function stop(){iappyx.nfc.stopReading();document.getElementById('ring').className='ring';document.getElementById('status').textContent='Stopped';}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    // ── SQLite Test ──
    fun getSqliteTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column}
.top{padding:14px 16px;border-bottom:1px solid #1a1a2e;flex-shrink:0}
h1{font-size:1.2rem;margin-bottom:10px}
.row{display:flex;gap:8px}
input{flex:1;background:#1a1a2e;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.9rem;outline:none}
.btn{background:#0f3460;border:none;border-radius:8px;padding:10px 14px;color:#fff;font-size:.82rem;cursor:pointer;white-space:nowrap}
.btn:active{opacity:.7}.btn.del{background:#3d1a1a}
textarea{width:100%;background:#0a0a14;border:none;border-radius:8px;padding:10px;color:#4FC3F7;font-size:.78rem;font-family:monospace;resize:none;outline:none;margin-top:8px;height:52px}
.list{padding:8px 16px}
.item{display:flex;align-items:center;justify-content:space-between;padding:11px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.item-text{font-size:.9rem}.item-id{font-size:.68rem;color:rgba(255,255,255,.3)}
.empty{text-align:center;color:rgba(255,255,255,.3);padding:40px;font-size:.9rem}
</style></head><body>
<div class="top">
  <h1>🗄️ $label</h1>
  <div class="row">
    <input id="inp" placeholder="Add item..." autocomplete="off">
    <button class="btn" onclick="addItem()">Add</button>
  </div>
  <textarea id="sql">SELECT * FROM items ORDER BY id DESC</textarea>
  <div class="row" style="margin-top:8px">
    <button class="btn" onclick="runSql()">Run SQL</button>
    <button class="btn del" onclick="clearAll()">Clear All</button>
  </div>
</div>
<div class="list" id="list"></div>
<script>
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  iappyx.sqlite.exec('CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT,created INTEGER)',null);
  load();
}
function addItem(){
  var t=document.getElementById('inp').value.trim(); if(!t)return;
  iappyx.sqlite.exec('INSERT INTO items(text,created) VALUES(?,?)',JSON.stringify([t,Date.now()]));
  document.getElementById('inp').value=''; load();
}
function load(){
  var r=JSON.parse(iappyx.sqlite.query('SELECT * FROM items ORDER BY id DESC',null));
  if(!r.ok){document.getElementById('list').innerHTML='<div class="empty">'+r.error+'</div>';return;}
  if(!r.rows.length){document.getElementById('list').innerHTML='<div class="empty">No items yet</div>';return;}
  document.getElementById('list').innerHTML=r.rows.map(function(row){
    return '<div class="item"><div><div class="item-text">'+row.text+'</div><div class="item-id">#'+row.id+'</div></div>'+
      '<button class="btn del" onclick="del('+row.id+')">✕</button></div>';
  }).join('');
}
function del(id){iappyx.sqlite.exec('DELETE FROM items WHERE id=?',JSON.stringify([String(id)]));load();}
function clearAll(){iappyx.sqlite.exec('DELETE FROM items',null);load();}
function runSql(){
  var sql=document.getElementById('sql').value.trim();
  var r=JSON.parse(sql.trim().toUpperCase().startsWith('SELECT')?
    iappyx.sqlite.query(sql,null):iappyx.sqlite.exec(sql,null));
  alert(r.ok?(r.rows?JSON.stringify(r.rows,null,2):'Done'):r.error);
  load();
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun getStepCounterApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center}
.count{font-size:72px;font-weight:bold;color:#4FC3F7}
.label{font-size:14px;color:rgba(255,255,255,0.5);margin-top:8px}
.status{font-size:13px;color:rgba(255,255,255,0.3);margin-top:24px;text-align:center;padding:0 40px}
button{background:#0f3460;color:#eaeaea;border:none;padding:14px 32px;border-radius:50px;font-size:16px;margin-top:32px;cursor:pointer;min-height:44px}
button.stop{background:#1a1a2e}
.info{font-size:11px;color:rgba(255,255,255,0.2);position:fixed;bottom:20px;text-align:center;padding:0 20px}
</style>
</head><body>
<div class="count" id="steps">--</div>
<div class="label">steps (since boot)</div>
<div class="status" id="statusMsg">Tap Start to begin counting</div>
<button id="btn" onclick="toggle()">Start</button>
<div class="info">Uses TYPE_STEP_COUNTER — shows total steps since device boot. Walk around to see it update.</div>
<script>
var running=false,baseSteps=null;
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
}
window.onStepData=function(data){
  if(baseSteps===null)baseSteps=data.steps;
  document.getElementById('steps').textContent=data.steps;
  document.getElementById('statusMsg').textContent='Counting... (session: +'+(data.steps-baseSteps)+')';
};
function toggle(){
  if(!running){
    if(typeof iappyx==='undefined'){document.getElementById('statusMsg').textContent='Bridge not ready';return;}
    iappyx.sensor.startStepCounter('window.onStepData');
    running=true;baseSteps=null;
    document.getElementById('btn').textContent='Stop';
    document.getElementById('btn').className='stop';
    document.getElementById('statusMsg').textContent='Waiting for first step event...';
  }else{
    iappyx.sensor.stop();
    running=false;
    document.getElementById('btn').textContent='Start';
    document.getElementById('btn').className='';
    document.getElementById('statusMsg').textContent='Stopped';
  }
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun getQrScannerApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;padding:20px;gap:12px}
h1{font-size:1.3rem}
.mode-btns{display:flex;gap:8px}
.mode-btn{background:#1a1a2e;border:none;border-radius:10px;padding:10px 20px;color:rgba(255,255,255,.4);font-size:.85rem;cursor:pointer}
.mode-btn.active{background:#0f3460;color:#fff}
.btn{background:#0f3460;border:none;border-radius:50px;padding:14px 36px;color:#fff;font-size:1rem;cursor:pointer;min-height:44px}
.btn.stop{background:#c62828}
.video-wrap{position:relative;width:100%;max-width:320px;aspect-ratio:4/3;border-radius:12px;overflow:hidden;background:#000;display:none}
.video-wrap video{width:100%;height:100%;object-fit:cover;display:block}
.video-wrap canvas{display:none}
.scan-line{position:absolute;left:10%;right:10%;height:2px;background:#4fc3f7;top:50%;box-shadow:0 0 8px #4fc3f7;animation:scanMove 2s ease-in-out infinite}
@keyframes scanMove{0%,100%{top:20%}50%{top:80%}}
.result{background:#1a1a2e;border-radius:12px;padding:14px;width:100%;max-width:320px;word-break:break-all;font-family:monospace;font-size:13px;display:none}
.label{font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:4px}
.history{width:100%;max-width:320px}
.hist-item{background:#1a1a2e;border-radius:8px;padding:10px 12px;margin-bottom:6px;font-size:12px;font-family:monospace;word-break:break-all;cursor:pointer}
.hist-item:active{opacity:.7}
.status{font-size:13px;color:rgba(255,255,255,0.4)}
</style></head><body>
<h1>$label</h1>
<div class="mode-btns">
  <button class="mode-btn active" id="mPhoto" onclick="setMode('photo')">📸 Photo</button>
  <button class="mode-btn" id="mLive" onclick="setMode('live')">🔴 Live</button>
</div>
<div id="photoMode">
  <button class="btn" onclick="doScan()">Scan QR / Barcode</button>
</div>
<div id="liveMode" style="display:none">
  <div class="video-wrap" id="videoWrap">
    <video id="cam" autoplay playsinline muted></video>
    <canvas id="cvs"></canvas>
    <div class="scan-line"></div>
  </div>
  <div style="margin-top:8px;display:flex;gap:8px">
    <button class="btn" onclick="startLive()">Start</button>
    <button class="btn stop" onclick="stopLive()">Stop</button>
  </div>
</div>
<div class="status" id="status">Tap scan or start live mode</div>
<div class="result" id="result">
  <div class="label">Last scan</div>
  <div id="scanText"></div>
</div>
<div class="history" id="history"></div>
<script>
var scanHistory=[], liveStream=null, liveTimer=null, scanning=false;
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  var s=iappyx.load('qr_history');
  if(s){try{scanHistory=JSON.parse(s);renderHistory();}catch(e){}}
}
function setMode(m){
  document.getElementById('mPhoto').className='mode-btn'+(m==='photo'?' active':'');
  document.getElementById('mLive').className='mode-btn'+(m==='live'?' active':'');
  document.getElementById('photoMode').style.display=m==='photo'?'':'none';
  document.getElementById('liveMode').style.display=m==='live'?'':'none';
  if(m==='photo') stopLive();
}
function doScan(){
  document.getElementById('status').textContent='Opening camera...';
  var cbId='qr_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){ addResult(r.text); }
    else{ document.getElementById('status').textContent=r.error||'Scan failed'; }
  };
  iappyx.camera.scanQR(cbId);
}
function startLive(){
  if(liveStream) return;
  document.getElementById('videoWrap').style.display='block';
  navigator.mediaDevices.getUserMedia({video:{facingMode:'environment',width:{ideal:640},height:{ideal:480}}})
  .then(function(stream){
    liveStream=stream;
    var vid=document.getElementById('cam');
    vid.srcObject=stream;
    vid.play().then(function(){
      document.getElementById('status').textContent='Scanning... ('+vid.videoWidth+'x'+vid.videoHeight+')';
      liveTimer=setInterval(captureFrame,300);
    }).catch(function(e){
      document.getElementById('status').textContent='Play failed: '+e.message+'. Retrying...';
      // Fallback: wait and retry
      setTimeout(function(){
        vid.play();
        liveTimer=setInterval(captureFrame,300);
      },500);
    });
  }).catch(function(e){
    document.getElementById('status').textContent='Camera error: '+e.message;
  });
}
function captureFrame(){
  if(scanning||!liveStream) return;
  var vid=document.getElementById('cam');
  if(vid.videoWidth===0) return;
  var cvs=document.getElementById('cvs');
  cvs.width=vid.videoWidth; cvs.height=vid.videoHeight;
  cvs.getContext('2d').drawImage(vid,0,0);
  var dataUrl=cvs.toDataURL('image/jpeg',0.85);
  var b64=dataUrl.substring(dataUrl.indexOf(',')+1);
  scanning=true;
  try {
    var r=JSON.parse(iappyxCamera.scanFrameQRSync(b64));
    scanning=false;
    if(r.ok&&r.results&&r.results.length>0){
      addResult(r.results[0].text);
    }
  } catch(e) { scanning=false; }
}
function stopLive(){
  if(liveTimer){clearInterval(liveTimer);liveTimer=null;}
  scanning=false;
  if(liveStream){liveStream.getTracks().forEach(function(t){t.stop()});liveStream=null;}
  document.getElementById('videoWrap').style.display='none';
  try{document.getElementById('cam').srcObject=null;}catch(e){}
  document.getElementById('status').textContent='Stopped';
}
function addResult(text){
  document.getElementById('scanText').textContent=text;
  document.getElementById('result').style.display='block';
  document.getElementById('status').textContent='Found!';
  if(scanHistory.length===0||scanHistory[0].text!==text){
    scanHistory.unshift({text:text,time:Date.now()});
    if(scanHistory.length>20)scanHistory=scanHistory.slice(0,20);
    iappyx.save('qr_history',JSON.stringify(scanHistory));
    renderHistory();
    iappyx.vibration.click();
  }
}
function renderHistory(){
  var el=document.getElementById('history');
  el.innerHTML=scanHistory.map(function(s){
    return '<div class="hist-item" onclick="copyText(\''+esc(s.text).replace(/'/g,"\\'")+'\')">'+esc(s.text)+'</div>';
  }).join('');
}
function copyText(t){iappyx.clipboard.write(t);document.getElementById('status').textContent='Copied!';}
function esc(t){return t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun getVoiceRecorderApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;padding:24px}
h1{font-size:1.3rem}
.circle{width:120px;height:120px;border-radius:50%;background:#1a1a2e;display:flex;align-items:center;justify-content:center;font-size:3rem;border:3px solid #0f3460;transition:.3s}
.circle.recording{border-color:#FF6B6B;animation:pulse 1.5s infinite}
@keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(255,107,107,.4)}50%{box-shadow:0 0 0 16px rgba(255,107,107,0)}}
.btn{background:#0f3460;border:none;border-radius:50px;padding:14px 32px;color:#fff;font-size:1rem;cursor:pointer;min-height:44px}
.btn.stop{background:#FF6B6B}
.btn.play{background:#1a1a2e}
.status{font-size:13px;color:rgba(255,255,255,0.4)}
audio{width:100%;max-width:300px;margin-top:8px}
.recordings{width:100%;max-width:320px}
.rec-item{background:#1a1a2e;border-radius:8px;padding:10px;margin-bottom:6px}
.rec-item audio{width:100%}
.rec-label{font-size:11px;color:rgba(255,255,255,0.3);margin-bottom:4px}
</style></head><body>
<h1>$label</h1>
<div class="circle" id="mic">🎙️</div>
<div>
  <button class="btn" id="recBtn" onclick="toggleRecord()">Record</button>
</div>
<div class="status" id="status">Tap Record to start</div>
<div class="recordings" id="recordings"></div>
<script>
var appRecording=false,recList=[];
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  var s=iappyx.load('recordings');
  if(s){try{recList=JSON.parse(s);renderList();}catch(e){}}
}
function toggleRecord(){
  if(!appRecording){
    var cbId='rec_'+Date.now();
    window._iappyxCb=window._iappyxCb||{};
    window._iappyxCb[cbId]=function(r){
      if(r.ok){
        appRecording=true;
        document.getElementById('mic').className='circle recording';
        document.getElementById('recBtn').textContent='Stop';
        document.getElementById('recBtn').className='btn stop';
        document.getElementById('status').textContent='Recording...';
      }else{
        document.getElementById('status').textContent=r.error||'Failed';
      }
    };
    iappyx.audio.startRecording(cbId);
  }else{
    var cbId='stop_'+Date.now();
    window._iappyxCb=window._iappyxCb||{};
    window._iappyxCb[cbId]=function(r){
      appRecording=false;
      document.getElementById('mic').className='circle';
      document.getElementById('recBtn').textContent='Record';
      document.getElementById('recBtn').className='btn';
      if(r.ok){
        recList.unshift({dataUrl:r.dataUrl,time:new Date().toLocaleTimeString()});
        if(recList.length>10)recList=recList.slice(0,10);
        iappyx.save('recordings',JSON.stringify(recList));
        renderList();
        document.getElementById('status').textContent='Recording saved!';
        iappyx.vibration.click();
      }else{
        document.getElementById('status').textContent=r.error||'Stop failed';
      }
    };
    iappyx.audio.stopRecording(cbId);
  }
}
function renderList(){
  var el=document.getElementById('recordings');
  el.innerHTML=recList.map(function(r){
    return '<div class="rec-item"><div class="rec-label">'+r.time+'</div><audio controls src="'+r.dataUrl+'"></audio></div>';
  }).join('');
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun getConnectivityApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:24px}
h1{font-size:1.3rem;margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.row{display:flex;justify-content:space-between;padding:6px 0;font-size:.85rem}
.k{color:rgba(255,255,255,0.4)}.v{font-weight:600}
.online{color:#69F0AE}.offline{color:#FF6B6B}
.btn{background:#0f3460;border:none;border-radius:12px;padding:12px;color:#fff;font-size:.9rem;width:100%;cursor:pointer;margin-top:12px;min-height:44px}
.status{text-align:center;font-size:2rem;margin:16px 0}
</style></head><body>
<h1>$label</h1>
<div class="status" id="icon">📶</div>
<div class="card" id="info"></div>
<button class="btn" onclick="refresh()">Refresh</button>
<script>
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  refresh();
}
function refresh(){
  var net=JSON.parse(iappyx.device.getConnectivity());
  var dev=JSON.parse(iappyx.device.getDeviceInfo());
  document.getElementById('icon').textContent=net.connected?'📶':'📵';
  var html='';
  html+='<div class="row"><span class="k">Connected</span><span class="v '+(net.connected?'online':'offline')+'">'+(net.connected?'Yes':'No')+'</span></div>';
  html+='<div class="row"><span class="k">Type</span><span class="v">'+net.type+'</span></div>';
  html+='<div class="row"><span class="k">Metered</span><span class="v">'+(net.metered?'Yes':'No')+'</span></div>';
  html+='<div class="row"><span class="k">Device</span><span class="v">'+dev.brand+' '+dev.model+'</span></div>';
  html+='<div class="row"><span class="k">Battery</span><span class="v">'+dev.battery+'%'+(dev.charging?' ⚡':'')+'</span></div>';
  html+='<div class="row"><span class="k">Screen</span><span class="v">'+dev.screenWidth+'×'+dev.screenHeight+'</span></div>';
  html+='<div class="row"><span class="k">Language</span><span class="v">'+dev.language+'</span></div>';
  document.getElementById('info').innerHTML=html;
}
window.addEventListener('load',function(){setTimeout(init,100)});
</script></body></html>""".trimIndent()

    private fun getOcrTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:12px}
.btn:active{opacity:0.8}
.result{background:#1a1a2e;border-radius:12px;padding:16px;margin-top:12px;white-space:pre-wrap;font-size:14px;line-height:1.6;max-height:60vh;overflow-y:auto}
.block{background:#0f3460;border-radius:8px;padding:10px;margin-bottom:8px}
.block-title{font-size:11px;color:#4fc3f7;margin-bottom:4px}
.empty{text-align:center;color:rgba(255,255,255,0.3);padding:40px}
.copy-btn{background:transparent;border:1px solid #4fc3f7;color:#4fc3f7;border-radius:8px;padding:8px 16px;font-size:13px;cursor:pointer;margin-top:8px}
</style></head><body>
<h1>$label</h1>
<button class="btn" onclick="scan()">Scan Text from Camera</button>
<div id="output"><div class="empty">Take a photo of text to scan it</div></div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
window.addEventListener('load',function(){setTimeout(init,200)});
function scan(){
  var cbId='ocr_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    var el=document.getElementById('output');
    if(r.ok){
      var html='<div style="margin-bottom:12px"><strong>Full text:</strong></div>';
      html+='<div class="block">'+esc(r.text)+'</div>';
      html+='<button class="copy-btn" onclick="copyText()">Copy to clipboard</button>';
      if(r.blocks&&r.blocks.length>0){
        html+='<div style="margin-top:16px;margin-bottom:8px"><strong>Blocks ('+r.blocks.length+'):</strong></div>';
        for(var i=0;i<r.blocks.length;i++){
          html+='<div class="block"><div class="block-title">Block '+(i+1)+'</div>'+esc(r.blocks[i].text)+'</div>';
        }
      }
      el.innerHTML=html;
      window._lastOcrText=r.text;
      iappyx.vibration.click();
    } else {
      el.innerHTML='<div class="empty">'+esc(r.error||'Scan failed')+'</div>';
    }
  };
  iappyx.camera.scanText(cbId);
}
function copyText(){
  if(window._lastOcrText){
    iappyx.clipboard.write(window._lastOcrText);
    iappyx.vibration.tick();
  }
}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\n/g,'<br>'):''}
</script></body></html>""".trimIndent()

    private fun getSpeechTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:12px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.result{background:#1a1a2e;border-radius:12px;padding:16px;margin-top:12px;font-size:16px;line-height:1.6;min-height:80px}
.alts{font-size:12px;color:rgba(255,255,255,0.4);margin-top:12px}
.history{margin-top:16px}
.history-item{background:#0f3460;border-radius:8px;padding:10px;margin-bottom:6px;font-size:14px}
.empty{text-align:center;color:rgba(255,255,255,0.3);padding:40px}
.lang-row{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap;justify-content:center}
.lang-btn{padding:8px 14px;border-radius:50px;border:1px solid #333;color:#888;font-size:12px;cursor:pointer;background:transparent}
.lang-btn.active{border-color:#4fc3f7;color:#4fc3f7;background:#0f3460}
</style></head><body>
<h1>$label</h1>
<div class="lang-row">
  <div class="lang-btn active" onclick="setLang('')" id="lang-">Auto</div>
  <div class="lang-btn" onclick="setLang('en')" id="lang-en">English</div>
  <div class="lang-btn" onclick="setLang('nl')" id="lang-nl">Dutch</div>
  <div class="lang-btn" onclick="setLang('de')" id="lang-de">German</div>
  <div class="lang-btn" onclick="setLang('fr')" id="lang-fr">French</div>
  <div class="lang-btn" onclick="setLang('es')" id="lang-es">Spanish</div>
</div>
<button class="btn" onclick="listen()">Start Listening</button>
<div id="output"><div class="empty">Tap to start speech recognition</div></div>
<div class="history" id="historySection" style="display:none">
  <div style="font-size:13px;color:rgba(255,255,255,0.5);margin-bottom:8px">History</div>
  <div id="historyList"></div>
</div>
<button class="btn btn-outline" onclick="copyAll()" style="margin-top:12px" id="copyBtn" style="display:none">Copy All to Clipboard</button>
<script>
var appLang='';
var appHistory=[];
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  appHistory=JSON.parse(iappyx.load('speech_history')||'[]');
  renderHistory();
}
window.addEventListener('load',function(){setTimeout(init,200)});
function setLang(l){
  appLang=l;
  document.querySelectorAll('.lang-btn').forEach(function(b){b.classList.remove('active')});
  document.getElementById('lang-'+l).classList.add('active');
}
function listen(){
  var cbId='stt_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    var el=document.getElementById('output');
    if(r.ok){
      var html='<div class="result">'+esc(r.text)+'</div>';
      if(r.alternatives&&r.alternatives.length>1){
        html+='<div class="alts">Alternatives: '+r.alternatives.slice(1).map(esc).join(' | ')+'</div>';
      }
      el.innerHTML=html;
      appHistory.unshift({text:r.text,time:new Date().toLocaleTimeString()});
      if(appHistory.length>50)appHistory.pop();
      iappyx.save('speech_history',JSON.stringify(appHistory));
      renderHistory();
      iappyx.vibration.click();
    } else {
      el.innerHTML='<div class="result" style="color:#ff6b6b">'+esc(r.error||'Recognition failed')+'</div>';
    }
  };
  iappyx.audio.speechToText(cbId,appLang);
}
function renderHistory(){
  var el=document.getElementById('historyList');
  var sec=document.getElementById('historySection');
  if(appHistory.length===0){sec.style.display='none';return;}
  sec.style.display='block';
  el.innerHTML=appHistory.map(function(h){
    return '<div class="history-item"><span style="color:#4fc3f7;font-size:11px">'+h.time+'</span><br>'+esc(h.text)+'</div>';
  }).join('');
}
function copyAll(){
  if(appHistory.length>0){
    iappyx.clipboard.write(appHistory.map(function(h){return h.text}).join('\n'));
    iappyx.vibration.tick();
  }
}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getPdfTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.btn-sm{padding:10px 16px;font-size:13px;width:auto;border-radius:8px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.row{display:flex;gap:8px;margin-bottom:8px}
input,textarea{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none}
.status{text-align:center;padding:20px;color:rgba(255,255,255,0.3);font-size:13px}
.loading{text-align:center;padding:30px;color:#4fc3f7;font-size:13px}
.preview{background:#fff;border-radius:8px;margin-top:12px;min-height:200px;display:flex;align-items:center;justify-content:center;overflow:hidden}
.preview canvas{max-width:100%;height:auto}
.page-nav{display:flex;align-items:center;justify-content:center;gap:16px;margin-top:10px}
.page-btn{width:40px;height:40px;border-radius:50%;border:1.5px solid #4fc3f7;background:transparent;color:#4fc3f7;font-size:18px;cursor:pointer;display:flex;align-items:center;justify-content:center}
.page-btn:disabled{border-color:#333;color:#333}
.tab-bar{display:flex;gap:6px;margin-bottom:14px}
.tab{flex:1;text-align:center;padding:8px;border-radius:8px;font-size:13px;cursor:pointer;color:#888;border:1px solid transparent}
.tab.active{color:#4fc3f7;border-color:#4fc3f7;background:#0f3460}
.hidden{display:none}
</style></head><body>
<h1>$label</h1>
<p class="sub">Create &amp; view PDFs (uses pdf-lib from CDN on first launch)</p>

<div class="tab-bar">
  <div class="tab active" onclick="showTab('create')" id="tab-create">Create PDF</div>
  <div class="tab" onclick="showTab('view')" id="tab-view">View PDF</div>
</div>

<div id="page-create">
  <div class="card">
    <div class="card-title">Document Details</div>
    <input id="pdfTitle" placeholder="Title" value="My Document">
    <div style="height:8px"></div>
    <textarea id="pdfContent" rows="6" placeholder="Type your content here...">Hello from iappyxOS!

This PDF was generated entirely on your phone — no server, no cloud.

The pdf-lib library was downloaded once and cached offline.</textarea>
  </div>
  <button class="btn" onclick="createPdf()" id="createBtn">Create PDF</button>
  <div id="createStatus"></div>
</div>

<div id="page-view" class="hidden">
  <button class="btn btn-outline" onclick="pickPdf()">Open PDF from device</button>
  <div id="viewArea"><div class="status">Open a PDF or create one above</div></div>
</div>

<div id="libStatus" class="loading">Loading pdf-lib...</div>

<input type="file" id="filePicker" accept=".pdf,application/pdf" style="display:none">

<script>
function runScript(code){var s=document.createElement('script');s.textContent=code;document.head.appendChild(s);}
var pdfLibReady=false;
var pdfDoc=null;
var currentPage=1;
var totalPages=0;

function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  loadLibrary();
}
window.addEventListener('load',function(){setTimeout(init,200)});

function loadLibrary(){
  var el=document.getElementById('libStatus');
  var cached=iappyx.storage.loadFile('pdf-lib.min.js');
  if(cached&&cached.length>100){
    try{runScript(cached);pdfLibReady=true;el.style.display='none';return;}
    catch(e){iappyx.storage.deleteFile('pdf-lib.min.js');}
  }
  el.textContent='Downloading pdf-lib (first time only)...';
  fetch('https://unpkg.com/pdf-lib@1.17.1/dist/pdf-lib.min.js')
    .then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})
    .then(function(code){
      if(code.length<100)throw new Error('incomplete');
      iappyx.storage.saveFile('pdf-lib.min.js',code);
      runScript(code);
      pdfLibReady=true;
      el.style.display='none';
    })
    .catch(function(e){
      el.textContent='Could not load pdf-lib. Connect to internet and restart.';
      el.style.color='#ff6b6b';
    });
}

function createPdf(){
  if(!pdfLibReady){alert('pdf-lib not loaded yet');return;}
  var title=document.getElementById('pdfTitle').value||'Untitled';
  var content=document.getElementById('pdfContent').value||'';
  var statusEl=document.getElementById('createStatus');
  statusEl.innerHTML='<div class="loading">Creating PDF...</div>';

  setTimeout(function(){
    try{
      PDFLib.PDFDocument.create().then(function(doc){
        var page=doc.addPage([595,842]);
        return doc.embedFont(PDFLib.StandardFonts.Helvetica).then(function(f){
          page.drawText(title,{x:50,y:780,size:24,font:f,color:PDFLib.rgb(0.1,0.1,0.1)});
          var lines=content.split('\n');
          var y=740;
          for(var i=0;i<lines.length&&y>50;i++){
            page.drawText(lines[i],{x:50,y:y,size:12,font:f,color:PDFLib.rgb(0.2,0.2,0.2)});
            y-=18;
          }
          page.drawText('Generated by iappyxOS',{x:50,y:30,size:8,font:f,color:PDFLib.rgb(0.6,0.6,0.6)});
          return doc.save();
        });
      }).then(function(bytes){
        window._lastPdfBytes=bytes;
        window._lastPdfTitle=title;
        statusEl.innerHTML='<div class="card"><div class="card-title">PDF Created!</div>'+
          '<div class="row"><button class="btn btn-sm" onclick="sharePdf()" style="flex:1">Share PDF</button></div></div>';
        iappyx.vibration.click();
      }).catch(function(e){
        statusEl.innerHTML='<div class="status" style="color:#ff6b6b">Error: '+e.message+'</div>';
      });
    }catch(e){
      statusEl.innerHTML='<div class="status" style="color:#ff6b6b">Error: '+e.message+'</div>';
    }
  },100);
}

function sharePdf(){
  if(!window._lastPdfBytes)return;
  var bytes=window._lastPdfBytes;
  var bin='';
  for(var i=0;i<bytes.length;i++) bin+=String.fromCharCode(bytes[i]);
  var b64=btoa(bin);
  var safeName=(window._lastPdfTitle||'document').replace(/[^a-zA-Z0-9 ]/g,'')+'.pdf';
  iappyx.storage.shareFile(safeName,b64,'application/pdf');
}

function pickPdf(){
  document.getElementById('filePicker').click();
}

document.getElementById('filePicker').addEventListener('change',function(e){
  var file=e.target.files[0];
  if(!file)return;
  var reader=new FileReader();
  reader.onload=function(ev){
    var data=new Uint8Array(ev.target.result);
    loadPdfData(data);
  };
  reader.readAsArrayBuffer(file);
});

function loadPdfData(data){
  if(!pdfLibReady){alert('pdf-lib not loaded');return;}
  var area=document.getElementById('viewArea');
  area.innerHTML='<div class="loading">Loading PDF...</div>';
  PDFLib.PDFDocument.load(data).then(function(doc){
    pdfDoc=doc;
    totalPages=doc.getPageCount();
    currentPage=1;
    area.innerHTML='<div class="card"><div class="card-title">PDF loaded — '+totalPages+' page'+(totalPages>1?'s':'')+'</div>'+
      '<div style="font-size:12px;color:rgba(255,255,255,0.5)">Title: '+(doc.getTitle()||'(none)')+'<br>'+
      'Author: '+(doc.getAuthor()||'(none)')+'<br>'+
      'Pages: '+totalPages+'</div></div>';
    iappyx.vibration.click();
  }).catch(function(e){
    area.innerHTML='<div class="status" style="color:#ff6b6b">Could not load PDF: '+e.message+'</div>';
  });
}

function showTab(t){
  document.getElementById('page-create').classList.toggle('hidden',t!=='create');
  document.getElementById('page-view').classList.toggle('hidden',t!=='view');
  document.getElementById('tab-create').classList.toggle('active',t==='create');
  document.getElementById('tab-view').classList.toggle('active',t==='view');
}
</script></body></html>""".trimIndent()

    private fun getQrGeneratorApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
textarea,input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.btn-sm{padding:10px 16px;font-size:13px;width:auto;border-radius:8px}
.qr-box{background:#fff;border-radius:12px;padding:20px;display:flex;justify-content:center;margin:12px 0}
.presets{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}
.preset{padding:6px 12px;border-radius:50px;border:1px solid #333;color:#888;font-size:12px;cursor:pointer;background:transparent}
.preset.active{border-color:#4fc3f7;color:#4fc3f7;background:#0f3460}
.row{display:flex;gap:8px}
.loading{text-align:center;padding:20px;color:#4fc3f7;font-size:13px}
.history-item{background:#0f3460;border-radius:8px;padding:10px;margin-bottom:6px;font-size:13px;cursor:pointer;display:flex;justify-content:space-between;align-items:center}
</style></head><body>
<h1>$label</h1>
<p class="sub">Generate QR codes from text, URLs, contacts, WiFi</p>

<div class="card">
  <div class="card-title">Type</div>
  <div class="presets">
    <div class="preset active" onclick="setType('text')" id="t-text">Text</div>
    <div class="preset" onclick="setType('url')" id="t-url">URL</div>
    <div class="preset" onclick="setType('wifi')" id="t-wifi">WiFi</div>
    <div class="preset" onclick="setType('contact')" id="t-contact">Contact</div>
  </div>
  <div id="inputArea">
    <textarea id="qrInput" rows="3" placeholder="Enter text..."></textarea>
  </div>
  <button class="btn" onclick="generate()">Generate QR Code</button>
</div>

<div id="qrResult" style="display:none">
  <div class="qr-box"><div id="qrTarget"></div></div>
  <div class="row">
    <button class="btn btn-sm btn-outline" onclick="shareQr()" style="flex:1">Share</button>
    <button class="btn btn-sm btn-outline" onclick="copyContent()" style="flex:1">Copy Content</button>
  </div>
</div>

<div class="card" style="margin-top:12px">
  <div class="card-title">History</div>
  <div id="historyList"><div style="text-align:center;color:rgba(255,255,255,0.2);padding:10px;font-size:12px">No QR codes yet</div></div>
</div>

<div id="libStatus" class="loading">Loading QR library...</div>

<script>
function runScript(code){var s=document.createElement('script');s.textContent=code;document.head.appendChild(s);}
var qrReady=false;
var appType='text';
var qrHistory=[];
var lastContent='';

function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  qrHistory=JSON.parse(iappyx.load('qr_history')||'[]');
  renderHistory();
  loadQrLib();
}
window.addEventListener('load',function(){setTimeout(init,200)});

function loadQrLib(){
  var el=document.getElementById('libStatus');
  var cached=iappyx.storage.loadFile('qrcode.min.js');
  if(cached&&cached.length>100){try{runScript(cached);qrReady=true;el.style.display='none';return;}catch(e){iappyx.storage.deleteFile('qrcode.min.js');}}
  el.textContent='Downloading QR library...';
  fetch('https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js')
    .then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.text()}).then(function(code){
      if(code.length<100)throw new Error('incomplete');
      iappyx.storage.saveFile('qrcode.min.js',code);runScript(code);qrReady=true;el.style.display='none';
    }).catch(function(){el.textContent='Need internet on first launch';el.style.color='#ff6b6b';});
}

function setType(t){
  appType=t;
  document.querySelectorAll('.preset').forEach(function(b){b.classList.remove('active')});
  document.getElementById('t-'+t).classList.add('active');
  var area=document.getElementById('inputArea');
  if(t==='text') area.innerHTML='<textarea id="qrInput" rows="3" placeholder="Enter text..."></textarea>';
  else if(t==='url') area.innerHTML='<input id="qrInput" placeholder="https://example.com" type="url">';
  else if(t==='wifi') area.innerHTML='<input id="wifiSsid" placeholder="Network name (SSID)"><input id="wifiPass" placeholder="Password" type="password"><div class="presets" style="margin-top:4px"><div class="preset active" id="wpa" onclick="this.classList.add(\'active\');document.getElementById(\'wep\').classList.remove(\'active\')">WPA</div><div class="preset" id="wep" onclick="this.classList.add(\'active\');document.getElementById(\'wpa\').classList.remove(\'active\')">WEP</div></div>';
  else if(t==='contact') area.innerHTML='<input id="cName" placeholder="Name"><input id="cPhone" placeholder="Phone"><input id="cEmail" placeholder="Email">';
}

function getContent(){
  if(appType==='text'||appType==='url') return document.getElementById('qrInput').value.trim();
  if(appType==='wifi'){
    var ssid=document.getElementById('wifiSsid').value.trim();
    var pass=document.getElementById('wifiPass').value;
    var enc=document.getElementById('wpa').classList.contains('active')?'WPA':'WEP';
    return 'WIFI:T:'+enc+';S:'+ssid+';P:'+pass+';;';
  }
  if(appType==='contact'){
    var n=document.getElementById('cName').value.trim();
    var p=document.getElementById('cPhone').value.trim();
    var e=document.getElementById('cEmail').value.trim();
    return 'BEGIN:VCARD\nVERSION:3.0\nFN:'+n+'\nTEL:'+p+'\nEMAIL:'+e+'\nEND:VCARD';
  }
  return '';
}

function generate(){
  if(!qrReady){alert('QR library loading...');return;}
  var content=getContent();
  if(!content)return;
  lastContent=content;
  document.getElementById('qrResult').style.display='block';
  var target=document.getElementById('qrTarget');
  target.innerHTML='';
  new QRCode(target,{text:content,width:256,height:256,correctLevel:QRCode.CorrectLevel.M});
  iappyx.vibration.click();
  qrHistory.unshift({content:content,type:appType,time:new Date().toLocaleString()});
  if(qrHistory.length>20)qrHistory.pop();
  iappyx.save('qr_history',JSON.stringify(qrHistory));
  renderHistory();
}

function shareQr(){
  var target=document.getElementById('qrTarget');
  var el=target.querySelector('canvas')||target.querySelector('img');
  if(!el)return;
  var c=document.createElement('canvas');c.width=256;c.height=256;
  c.getContext('2d').drawImage(el,0,0,256,256);
  var b64=c.toDataURL('image/png').split(',')[1];
  iappyx.sharePhoto(b64);
}

function copyContent(){
  if(lastContent){iappyx.clipboard.write(lastContent);iappyx.vibration.tick();}
}

function renderHistory(){
  var el=document.getElementById('historyList');
  if(qrHistory.length===0){el.innerHTML='<div style="text-align:center;color:rgba(255,255,255,0.2);padding:10px;font-size:12px">No QR codes yet</div>';return;}
  el.innerHTML=qrHistory.map(function(h,i){
    var label=h.content.length>40?h.content.substring(0,40)+'...':h.content;
    if(h.type==='wifi')label='WiFi: '+h.content.match(/S:([^;]*)/)[1];
    if(h.type==='contact')label='Contact: '+(h.content.match(/FN:([^\n]*)/)||['','?'])[1];
    return '<div class="history-item" onclick="reuse('+i+')"><div><div>'+esc(label)+'</div><div style="font-size:10px;color:rgba(255,255,255,0.3)">'+h.time+'</div></div><span style="color:#4fc3f7;font-size:18px">&#8635;</span></div>';
  }).join('');
}

function reuse(i){
  var h=qrHistory[i];
  if(!h)return;
  lastContent=h.content;
  setType(h.type);
  if(h.type==='text'||h.type==='url'){setTimeout(function(){document.getElementById('qrInput').value=h.content},50);}
  generate();
}

function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getDashboardApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.stat-row{display:flex;gap:10px;margin-bottom:10px}
.stat{flex:1;background:#0f3460;border-radius:10px;padding:12px;text-align:center}
.stat-value{font-size:24px;font-weight:700;color:#4fc3f7}
.stat-label{font-size:10px;color:rgba(255,255,255,0.4);margin-top:2px}
.chart-container{position:relative;height:250px;margin:8px 0}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:12px;font-size:14px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-sm{padding:8px 14px;font-size:12px;width:auto;border-radius:8px}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.row{display:flex;gap:8px;margin-bottom:8px}
input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none}
.loading{text-align:center;padding:30px;color:#4fc3f7;font-size:13px}
.tab-bar{display:flex;gap:6px;margin-bottom:14px}
.tab{flex:1;text-align:center;padding:8px;border-radius:8px;font-size:12px;cursor:pointer;color:#888;border:1px solid transparent}
.tab.active{color:#4fc3f7;border-color:#4fc3f7;background:#0f3460}
</style></head><body>
<h1>$label</h1>
<p class="sub">Track data &amp; visualize with charts (Chart.js)</p>

<div class="tab-bar">
  <div class="tab active" onclick="showTab('dash')" id="tab-dash">Dashboard</div>
  <div class="tab" onclick="showTab('add')" id="tab-add">Add Data</div>
</div>

<div id="page-dash">
  <div class="stat-row">
    <div class="stat"><div class="stat-value" id="totalEntries">0</div><div class="stat-label">Entries</div></div>
    <div class="stat"><div class="stat-value" id="avgValue">0</div><div class="stat-label">Average</div></div>
    <div class="stat"><div class="stat-value" id="maxValue">0</div><div class="stat-label">Max</div></div>
  </div>
  <div class="card">
    <div class="card-title">Trend</div>
    <div class="chart-container"><canvas id="lineChart"></canvas></div>
  </div>
  <div class="card">
    <div class="card-title">Distribution</div>
    <div class="chart-container" style="height:200px"><canvas id="barChart"></canvas></div>
  </div>
  <button class="btn btn-outline" onclick="exportCsv()">Export as CSV</button>
</div>

<div id="page-add" style="display:none">
  <div class="card">
    <div class="card-title">Add Entry</div>
    <input id="entryLabel" placeholder="Label (e.g. Monday, Jan 5)">
    <div style="height:6px"></div>
    <input id="entryValue" placeholder="Value" type="number" step="any">
    <div style="height:10px"></div>
    <button class="btn" onclick="addEntry()">Add</button>
  </div>
  <div class="card">
    <div class="card-title">Recent Entries</div>
    <div id="entryList"><div style="text-align:center;color:rgba(255,255,255,0.2);padding:10px;font-size:12px">No entries yet</div></div>
  </div>
  <button class="btn" style="background:#ff6b6b" onclick="clearAll()">Clear All Data</button>
</div>

<div id="libStatus" class="loading">Loading Chart.js...</div>

<script>
function runScript(code){var s=document.createElement('script');s.textContent=code;document.head.appendChild(s);}
var chartReady=false;
var entries=[];
var lineChartObj=null;
var barChartObj=null;

function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  entries=JSON.parse(iappyx.load('dashboard_entries')||'[]');
  loadChartLib();
}
window.addEventListener('load',function(){setTimeout(init,200)});

function loadChartLib(){
  var el=document.getElementById('libStatus');
  var cached=iappyx.storage.loadFile('chart.umd.min.js');
  if(cached&&cached.length>100){try{runScript(cached);chartReady=true;el.style.display='none';renderAll();return;}catch(e){iappyx.storage.deleteFile('chart.umd.min.js');}}
  el.textContent='Downloading Chart.js...';
  fetch('https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js')
    .then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.text()}).then(function(code){
      if(code.length<100)throw new Error('incomplete');
      iappyx.storage.saveFile('chart.umd.min.js',code);runScript(code);chartReady=true;el.style.display='none';renderAll();
    }).catch(function(){el.textContent='Need internet on first launch';el.style.color='#ff6b6b';});
}

function addEntry(){
  var label=document.getElementById('entryLabel').value.trim()||('Entry '+(entries.length+1));
  var val=parseFloat(document.getElementById('entryValue').value);
  if(isNaN(val))return;
  entries.push({label:label,value:val,time:Date.now()});
  iappyx.save('dashboard_entries',JSON.stringify(entries));
  document.getElementById('entryLabel').value='';
  document.getElementById('entryValue').value='';
  iappyx.vibration.click();
  renderAll();
}

function renderAll(){
  updateStats();
  renderCharts();
  renderEntryList();
}

function updateStats(){
  document.getElementById('totalEntries').textContent=entries.length;
  if(entries.length===0){
    document.getElementById('avgValue').textContent='0';
    document.getElementById('maxValue').textContent='0';
    return;
  }
  var sum=0,max=-Infinity;
  for(var i=0;i<entries.length;i++){sum+=entries[i].value;if(entries[i].value>max)max=entries[i].value;}
  document.getElementById('avgValue').textContent=(sum/entries.length).toFixed(1);
  document.getElementById('maxValue').textContent=max.toFixed(1);
}

function renderCharts(){
  if(!chartReady||entries.length===0)return;
  var labels=entries.map(function(e){return e.label});
  var values=entries.map(function(e){return e.value});
  var colors=values.map(function(v,i){return 'rgba(79,195,247,'+(0.4+0.6*i/values.length)+')'});

  if(lineChartObj)lineChartObj.destroy();
  lineChartObj=new Chart(document.getElementById('lineChart'),{
    type:'line',
    data:{labels:labels,datasets:[{label:'Value',data:values,borderColor:'#4fc3f7',backgroundColor:'rgba(79,195,247,0.1)',tension:0.3,fill:true,pointRadius:4,pointBackgroundColor:'#4fc3f7'}]},
    options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{x:{ticks:{color:'#666',maxRotation:45},grid:{color:'rgba(255,255,255,0.05)'}},y:{ticks:{color:'#666'},grid:{color:'rgba(255,255,255,0.05)'}}}}
  });

  if(barChartObj)barChartObj.destroy();
  barChartObj=new Chart(document.getElementById('barChart'),{
    type:'bar',
    data:{labels:labels,datasets:[{data:values,backgroundColor:colors,borderRadius:6}]},
    options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{x:{ticks:{color:'#666'},grid:{display:false}},y:{ticks:{color:'#666'},grid:{color:'rgba(255,255,255,0.05)'}}}}
  });
}

function renderEntryList(){
  var el=document.getElementById('entryList');
  if(entries.length===0){el.innerHTML='<div style="text-align:center;color:rgba(255,255,255,0.2);padding:10px;font-size:12px">No entries yet</div>';return;}
  el.innerHTML=entries.slice().reverse().slice(0,20).map(function(e,i){
    var idx=entries.length-1-i;
    return '<div style="display:flex;justify-content:space-between;padding:8px 10px;background:#0f3460;border-radius:8px;margin-bottom:4px;font-size:13px"><span>'+esc(e.label)+'</span><span style="color:#4fc3f7;font-weight:600">'+e.value+'</span></div>';
  }).join('');
}

function clearAll(){
  if(!confirm('Delete all entries?'))return;
  entries=[];
  iappyx.save('dashboard_entries','[]');
  if(lineChartObj){lineChartObj.destroy();lineChartObj=null;}
  if(barChartObj){barChartObj.destroy();barChartObj=null;}
  renderAll();
}

function exportCsv(){
  if(entries.length===0)return;
  var csv='Label,Value,Timestamp\n';
  for(var i=0;i<entries.length;i++){
    csv+='"'+entries[i].label.replace(/"/g,'""')+'",'+entries[i].value+','+new Date(entries[i].time).toISOString()+'\n';
  }
  iappyx.storage.shareFile('dashboard_export.csv',btoa(unescape(encodeURIComponent(csv))),'text/csv');
}

function showTab(t){
  document.getElementById('page-dash').style.display=t==='dash'?'block':'none';
  document.getElementById('page-add').style.display=t==='add'?'block':'none';
  document.getElementById('tab-dash').classList.toggle('active',t==='dash');
  document.getElementById('tab-add').classList.toggle('active',t==='add');
  if(t==='dash')renderCharts();
}

function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getMediaTestApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
video{width:100%;border-radius:8px;background:#000}
canvas{width:100%;height:100px;background:#0f3460;border-radius:8px;margin-top:8px}
.log{font-size:11px;font-family:monospace;color:rgba(255,255,255,0.5);margin-top:8px;max-height:150px;overflow-y:auto}
.ok{color:#69f0ae}.err{color:#ff6b6b}
</style></head><body>
<h1>$label</h1>
<p class="sub">Test getUserMedia — camera viewfinder &amp; mic stream</p>

<div class="card">
  <div class="card-title">Camera (getUserMedia video)</div>
  <button class="btn" onclick="startCamera()">Start Camera</button>
  <video id="vid" autoplay playsinline muted style="display:none"></video>
  <div id="camLog" class="log"></div>
</div>

<div class="card">
  <div class="card-title">Microphone (getUserMedia audio + Web Audio API)</div>
  <button class="btn btn-outline" onclick="startMic()">Start Microphone</button>
  <canvas id="spectrum"></canvas>
  <div id="micLog" class="log"></div>
</div>

<div class="card">
  <div class="card-title">Capabilities Check</div>
  <div id="capLog" class="log"></div>
</div>

<script>
function log(id,msg,ok){
  var el=document.getElementById(id);
  el.innerHTML+='<div class="'+(ok?'ok':'err')+'">'+msg+'</div>';
  el.scrollTop=el.scrollHeight;
}

// Check capabilities
(function(){
  var el=document.getElementById('capLog');
  el.innerHTML='<div>navigator.mediaDevices: '+(navigator.mediaDevices?'<span class="ok">YES</span>':'<span class="err">NO</span>')+'</div>';
  el.innerHTML+='<div>getUserMedia: '+((navigator.mediaDevices&&navigator.mediaDevices.getUserMedia)?'<span class="ok">YES</span>':'<span class="err">NO</span>')+'</div>';
  el.innerHTML+='<div>AudioContext: '+(window.AudioContext||window.webkitAudioContext?'<span class="ok">YES</span>':'<span class="err">NO</span>')+'</div>';
  el.innerHTML+='<div>Secure origin: '+(location.protocol==='https:'||location.protocol==='file:'?'<span class="ok">YES ('+location.protocol+')</span>':'<span class="err">NO ('+location.protocol+')</span>')+'</div>';
})();

function startCamera(){
  log('camLog','Requesting camera...',true);
  if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){
    log('camLog','getUserMedia not supported',false);return;
  }
  navigator.mediaDevices.getUserMedia({video:{facingMode:'environment'},audio:false})
    .then(function(stream){
      log('camLog','Camera stream obtained!',true);
      var vid=document.getElementById('vid');
      vid.srcObject=stream;
      vid.style.display='block';
      var track=stream.getVideoTracks()[0];
      var settings=track.getSettings();
      log('camLog','Resolution: '+settings.width+'x'+settings.height,true);
      log('camLog','FPS: '+(settings.frameRate||'?'),true);
      log('camLog','Facing: '+(settings.facingMode||'?'),true);
    })
    .catch(function(e){
      log('camLog','Camera error: '+e.message,false);
      log('camLog','Error name: '+e.name,false);
    });
}

function startMic(){
  log('micLog','Requesting microphone...',true);
  if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){
    log('micLog','getUserMedia not supported',false);return;
  }
  navigator.mediaDevices.getUserMedia({audio:true,video:false})
    .then(function(stream){
      log('micLog','Mic stream obtained!',true);
      var AudioCtx=window.AudioContext||window.webkitAudioContext;
      var ctx=new AudioCtx();
      var src=ctx.createMediaStreamSource(stream);
      var analyser=ctx.createAnalyser();
      analyser.fftSize=256;
      src.connect(analyser);
      var data=new Uint8Array(analyser.frequencyBinCount);
      var canvas=document.getElementById('spectrum');
      var cCtx=canvas.getContext('2d');
      canvas.width=canvas.offsetWidth;
      canvas.height=100;
      log('micLog','Audio context: '+ctx.sampleRate+'Hz',true);
      log('micLog','FFT bins: '+analyser.frequencyBinCount,true);

      function draw(){
        requestAnimationFrame(draw);
        analyser.getByteFrequencyData(data);
        cCtx.fillStyle='#0f3460';
        cCtx.fillRect(0,0,canvas.width,canvas.height);
        var barW=canvas.width/data.length;
        for(var i=0;i<data.length;i++){
          var h=data[i]/255*canvas.height;
          cCtx.fillStyle='hsl('+(i/data.length*240)+',80%,60%)';
          cCtx.fillRect(i*barW,canvas.height-h,barW-1,h);
        }
      }
      draw();
    })
    .catch(function(e){
      log('micLog','Mic error: '+e.message,false);
      log('micLog','Error name: '+e.name,false);
    });
}
</script></body></html>""".trimIndent()

    private fun getFlashlightApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;min-height:100vh;padding:16px;transition:background 0.3s,color 0.3s}
body.dark{background:#0d0d1a;color:#eaeaea}
body.light{background:#f5f5f5;color:#1a1a1a}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.card{border-radius:12px;padding:20px;margin-bottom:12px;transition:background 0.3s}
.dark .card{background:#1a1a2e} .light .card{background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.1)}
.card-title{font-size:13px;font-weight:600;margin-bottom:12px;transition:color 0.3s}
.dark .card-title{color:#4fc3f7} .light .card-title{color:#1565c0}
.torch-btn{width:120px;height:120px;border-radius:50%;border:none;font-size:40px;cursor:pointer;margin:20px auto;display:block;transition:all 0.2s}
.torch-btn.off{background:#1a1a2e;box-shadow:0 0 0 rgba(255,235,59,0)} .torch-btn.on{background:#ffeb3b;box-shadow:0 0 40px rgba(255,235,59,0.5)}
.slider-row{display:flex;align-items:center;gap:12px;margin:8px 0}
.slider-row label{font-size:13px;min-width:80px;opacity:0.6}
input[type=range]{flex:1;accent-color:#4fc3f7}
.val{font-size:13px;min-width:35px;text-align:right;opacity:0.5}
.theme-badge{text-align:center;padding:8px;border-radius:8px;font-size:12px;margin-top:8px}
.dark .theme-badge{background:#0f3460;color:#4fc3f7} .light .theme-badge{background:#e3f2fd;color:#1565c0}
</style></head><body class="dark">
<h1>$label</h1>
<div class="card" style="text-align:center">
  <div class="card-title">Flashlight</div>
  <button class="torch-btn off" id="torchBtn" onclick="toggleTorch()">🔦</button>
  <div id="torchStatus" style="font-size:12px;opacity:0.5">Tap to toggle</div>
</div>
<div class="card">
  <div class="card-title">Screen Brightness</div>
  <div class="slider-row">
    <label>Brightness</label>
    <input type="range" min="0" max="100" value="50" id="brightness" oninput="setBrightness(this.value)">
    <span class="val" id="brightVal">50%</span>
  </div>
</div>
<div class="card">
  <div class="card-title">System Theme</div>
  <div class="theme-badge" id="themeBadge">Detecting...</div>
</div>
<script>
var torchOn=false;
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}detectTheme();}
window.addEventListener('load',function(){setTimeout(init,200)});
function toggleTorch(){
  torchOn=!torchOn;
  iappyx.device.setTorch(torchOn);
  var btn=document.getElementById('torchBtn');
  btn.className='torch-btn '+(torchOn?'on':'off');
  document.getElementById('torchStatus').textContent=torchOn?'ON':'OFF';
  iappyx.vibration.click();
}
function setBrightness(v){
  iappyx.screen.setBrightness(v/100);
  document.getElementById('brightVal').textContent=v+'%';
}
function detectTheme(){
  var dark=iappyx.device.isDarkMode();
  document.body.className=dark?'dark':'light';
  document.getElementById('themeBadge').textContent='System theme: '+(dark?'Dark':'Light');
}
</script></body></html>""".trimIndent()

    private fun getClassifierApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.label-row{display:flex;justify-content:space-between;align-items:center;padding:10px;background:#0f3460;border-radius:8px;margin-bottom:6px}
.label-name{font-size:14px;font-weight:500}
.label-conf{font-size:13px;font-weight:700}
.bar{height:4px;border-radius:2px;margin-top:4px}
.empty{text-align:center;color:rgba(255,255,255,0.3);padding:30px;font-size:13px}
.history-item{background:#0f3460;border-radius:8px;padding:10px;margin-bottom:6px;font-size:13px;cursor:pointer}
</style></head><body>
<h1>$label</h1>
<button class="btn" onclick="classify()">Identify with Camera</button>
<div id="result"><div class="empty">Take a photo to identify objects, plants, animals, scenes</div></div>
<div class="card" style="margin-top:12px">
  <div class="card-title">History</div>
  <div id="historyList"><div class="empty">No scans yet</div></div>
</div>
<button class="btn btn-outline" onclick="shareHistory()">Share History</button>
<script>
var appHistory=[];
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  appHistory=JSON.parse(iappyx.load('classify_history')||'[]');renderHistory();}
window.addEventListener('load',function(){setTimeout(init,200)});
function classify(){
  var cbId='cls_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    var el=document.getElementById('result');
    if(r.ok&&r.labels&&r.labels.length>0){
      var html='<div class="card"><div class="card-title">Results</div>';
      var topLabels=[];
      for(var i=0;i<r.labels.length;i++){
        var l=r.labels[i];
        var color=l.confidence>70?'#69f0ae':l.confidence>40?'#4fc3f7':'#ff6b6b';
        html+='<div class="label-row"><span class="label-name">'+esc(l.label)+'</span><span class="label-conf" style="color:'+color+'">'+l.confidence+'%</span></div>';
        html+='<div class="bar" style="width:'+l.confidence+'%;background:'+color+'"></div>';
        topLabels.push(l.label+'('+l.confidence+'%)');
      }
      html+='</div>';
      el.innerHTML=html;
      appHistory.unshift({labels:topLabels.join(', '),time:new Date().toLocaleString()});
      if(appHistory.length>30)appHistory.pop();
      iappyx.save('classify_history',JSON.stringify(appHistory));
      renderHistory();
      iappyx.vibration.click();
    } else {
      el.innerHTML='<div class="empty">'+(r.error||'No objects identified')+'</div>';
    }
  };
  iappyx.camera.classify(cbId);
}
function renderHistory(){
  var el=document.getElementById('historyList');
  if(appHistory.length===0){el.innerHTML='<div class="empty">No scans yet</div>';return;}
  el.innerHTML=appHistory.map(function(h){
    return '<div class="history-item"><div>'+esc(h.labels)+'</div><div style="font-size:10px;color:rgba(255,255,255,0.3);margin-top:2px">'+h.time+'</div></div>';
  }).join('');
}
function shareHistory(){
  if(appHistory.length===0)return;
  var text=appHistory.map(function(h){return h.time+': '+h.labels}).join('\n');
  iappyx.shareText(text,'Classification History');
}
function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getRunTrackerApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.stat-row{display:flex;gap:10px;margin-bottom:10px}
.stat{flex:1;background:#1a1a2e;border-radius:12px;padding:14px;text-align:center}
.stat-value{font-size:22px;font-weight:700;color:#4fc3f7}
.stat-label{font-size:10px;color:rgba(255,255,255,0.4);margin-top:2px}
.btn{border:none;border-radius:50px;padding:16px;font-size:16px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-start{background:#69f0ae;color:#0d0d1a}
.btn-stop{background:#ff6b6b;color:#fff}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7;padding:12px;font-size:14px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.coord{font-size:11px;font-family:monospace;color:rgba(255,255,255,0.4);margin-top:8px}
.history-item{background:#0f3460;border-radius:8px;padding:10px;margin-bottom:6px;font-size:13px}
</style></head><body>
<h1>$label</h1>
<div class="stat-row">
  <div class="stat"><div class="stat-value" id="distance">0.00</div><div class="stat-label">km</div></div>
  <div class="stat"><div class="stat-value" id="duration">00:00</div><div class="stat-label">time</div></div>
  <div class="stat"><div class="stat-value" id="speed">0.0</div><div class="stat-label">km/h</div></div>
</div>
<div class="stat-row">
  <div class="stat"><div class="stat-value" id="steps">0</div><div class="stat-label">steps</div></div>
  <div class="stat"><div class="stat-value" id="pace">--:--</div><div class="stat-label">min/km</div></div>
</div>
<button class="btn btn-start" id="mainBtn" onclick="toggleRun()">Start Run</button>
<div class="card">
  <div class="card-title">GPS</div>
  <div class="coord" id="gpsInfo">Waiting for GPS...</div>
</div>
<div class="card">
  <div class="card-title">Run History</div>
  <div id="historyList"><div style="text-align:center;color:rgba(255,255,255,0.2);padding:10px;font-size:12px">No runs yet</div></div>
</div>
<button class="btn btn-outline" onclick="exportRuns()">Export History</button>
<script>
var running=false,startTime=0,totalDist=0,lastLat=0,lastLon=0,timerInterval=null,startSteps=0,currentSteps=0;
var runHistory=[];
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  runHistory=JSON.parse(iappyx.load('run_history')||'[]');renderHistory();
  iappyx.screen.keepOn(false);}
window.addEventListener('load',function(){setTimeout(init,200)});
function toggleRun(){
  if(!running){
    running=true;startTime=Date.now();totalDist=0;lastLat=0;lastLon=0;startSteps=currentSteps;
    document.getElementById('mainBtn').textContent='Stop Run';
    document.getElementById('mainBtn').className='btn btn-stop';
    iappyx.screen.keepOn(true);
    iappyx.location.startTracking('window.onTrackUpdate');
    iappyx.sensor.startStepCounter('window.onStepUpdate');
    timerInterval=setInterval(updateTimer,1000);
    iappyx.vibration.heavyClick();
  } else {
    running=false;
    document.getElementById('mainBtn').textContent='Start Run';
    document.getElementById('mainBtn').className='btn btn-start';
    iappyx.screen.keepOn(false);
    iappyx.location.stopTracking();
    iappyx.sensor.stop();
    clearInterval(timerInterval);
    var elapsed=Math.floor((Date.now()-startTime)/1000);
    if(totalDist>0.01){
      runHistory.unshift({dist:totalDist.toFixed(2),time:formatTime(elapsed),steps:currentSteps-startSteps,date:new Date().toLocaleDateString()});
      if(runHistory.length>50)runHistory.pop();
      iappyx.save('run_history',JSON.stringify(runHistory));
      renderHistory();
    }
    iappyx.vibration.heavyClick();
  }
}
window.onTrackUpdate=function(pos){
  if(!running)return;
  document.getElementById('gpsInfo').textContent=pos.lat.toFixed(6)+', '+pos.lon.toFixed(6)+' (±'+Math.round(pos.accuracy)+'m)';
  if(lastLat!==0){
    var d=haversine(lastLat,lastLon,pos.lat,pos.lon);
    if(d>0.003&&pos.accuracy<50) totalDist+=d;
  }
  lastLat=pos.lat;lastLon=pos.lon;
  document.getElementById('distance').textContent=totalDist.toFixed(2);
  document.getElementById('speed').textContent=((pos.speed||0)*3.6).toFixed(1);
  var elapsed=(Date.now()-startTime)/1000;
  if(totalDist>0.01){var paceS=elapsed/totalDist;document.getElementById('pace').textContent=formatTime(Math.round(paceS));}
};
window.onStepUpdate=function(d){currentSteps=d.steps;if(running)document.getElementById('steps').textContent=currentSteps-startSteps;};
function updateTimer(){if(!running)return;var s=Math.floor((Date.now()-startTime)/1000);document.getElementById('duration').textContent=formatTime(s);}
function formatTime(s){var m=Math.floor(s/60);var sec=s%60;return(m<10?'0':'')+m+':'+(sec<10?'0':'')+sec;}
function haversine(a,b,c,d){var R=6371;var dLat=(c-a)*Math.PI/180;var dLon=(d-b)*Math.PI/180;var x=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(a*Math.PI/180)*Math.cos(c*Math.PI/180)*Math.sin(dLon/2)*Math.sin(dLon/2);return R*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));}
function renderHistory(){var el=document.getElementById('historyList');if(runHistory.length===0){el.innerHTML='<div style="text-align:center;color:rgba(255,255,255,0.2);padding:10px;font-size:12px">No runs yet</div>';return;}
  el.innerHTML=runHistory.map(function(r){return '<div class="history-item"><div style="display:flex;justify-content:space-between"><span>'+r.dist+' km</span><span>'+r.time+'</span></div><div style="font-size:10px;color:rgba(255,255,255,0.3)">'+r.steps+' steps · '+r.date+'</div></div>';}).join('');}
function exportRuns(){if(runHistory.length===0)return;var csv='Date,Distance(km),Time,Steps\n';for(var i=0;i<runHistory.length;i++){var r=runHistory[i];csv+=r.date+','+r.dist+','+r.time+','+r.steps+'\n';}
  var b64=btoa(unescape(encodeURIComponent(csv)));iappyx.storage.shareFile('runs.csv',b64,'text/csv');}
</script></body></html>""".trimIndent()

    private fun getSoundToolsApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
canvas{width:100%;height:80px;background:#0f3460;border-radius:8px}
.db-display{font-size:48px;font-weight:700;text-align:center;color:#4fc3f7;margin:12px 0}
.db-label{text-align:center;font-size:12px;color:rgba(255,255,255,0.4)}
.slider-row{display:flex;align-items:center;gap:10px;margin:8px 0}
.slider-row label{font-size:12px;min-width:70px;color:rgba(255,255,255,0.5)}
input[type=range]{flex:1;accent-color:#4fc3f7}
.val{font-size:12px;min-width:30px;text-align:right;color:rgba(255,255,255,0.4)}
.sfx-grid{display:flex;gap:8px;flex-wrap:wrap}
.sfx-btn{padding:10px 16px;border-radius:8px;border:1px solid #333;background:#0f3460;color:#eaeaea;font-size:13px;cursor:pointer}
.sfx-btn:active{background:#4fc3f7;color:#0d0d1a}
</style></head><body>
<h1>$label</h1>
<div class="card">
  <div class="card-title">Sound Level Meter</div>
  <button class="btn" id="micBtn" onclick="toggleMic()">Start Microphone</button>
  <div class="db-display" id="dbVal">--</div>
  <div class="db-label">dB (approximate)</div>
  <canvas id="spectrum"></canvas>
</div>
<div class="card">
  <div class="card-title">Sound Effects (overlay test)</div>
  <div class="sfx-grid">
    <div class="sfx-btn" onclick="playSfx('click')">Click</div>
    <div class="sfx-btn" onclick="playSfx('beep')">Beep</div>
    <div class="sfx-btn" onclick="playSfx('ding')">Ding</div>
    <div class="sfx-btn" onclick="iappyx.vibration.click()">Haptic</div>
    <div class="sfx-btn" onclick="iappyx.vibration.tick()">Tick</div>
    <div class="sfx-btn" onclick="iappyx.vibration.heavyClick()">Heavy</div>
  </div>
</div>
<div class="card">
  <div class="card-title">Volume Streams</div>
  <div class="slider-row"><label>Music</label><input type="range" min="0" max="100" value="50" oninput="setVol('music',this.value,this)"><span class="val">50%</span></div>
  <div class="slider-row"><label>Alarm</label><input type="range" min="0" max="100" value="50" oninput="setVol('alarm',this.value,this)"><span class="val">50%</span></div>
  <div class="slider-row"><label>Ring</label><input type="range" min="0" max="100" value="50" oninput="setVol('ring',this.value,this)"><span class="val">50%</span></div>
  <div class="slider-row"><label>Notification</label><input type="range" min="0" max="100" value="50" oninput="setVol('notification',this.value,this)"><span class="val">50%</span></div>
</div>
<script>
var micActive=false,audioCtx,analyser,dataArr,animId;
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
window.addEventListener('load',function(){setTimeout(init,200)});
function toggleMic(){
  if(micActive){micActive=false;document.getElementById('micBtn').textContent='Start Microphone';if(animId)cancelAnimationFrame(animId);return;}
  navigator.mediaDevices.getUserMedia({audio:true}).then(function(stream){
    micActive=true;document.getElementById('micBtn').textContent='Stop Microphone';
    var AC=window.AudioContext||window.webkitAudioContext;
    audioCtx=new AC();var src=audioCtx.createMediaStreamSource(stream);
    analyser=audioCtx.createAnalyser();analyser.fftSize=256;
    src.connect(analyser);
    dataArr=new Uint8Array(analyser.frequencyBinCount);
    var canvas=document.getElementById('spectrum');
    canvas.width=canvas.offsetWidth;canvas.height=80;
    drawSpectrum(canvas);
  }).catch(function(e){document.getElementById('dbVal').textContent='ERR';});
}
function drawSpectrum(canvas){
  if(!micActive)return;
  animId=requestAnimationFrame(function(){drawSpectrum(canvas)});
  analyser.getByteFrequencyData(dataArr);
  var ctx=canvas.getContext('2d');ctx.fillStyle='#0f3460';ctx.fillRect(0,0,canvas.width,canvas.height);
  var barW=canvas.width/dataArr.length;var sum=0;
  for(var i=0;i<dataArr.length;i++){var h=dataArr[i]/255*canvas.height;
    ctx.fillStyle='hsl('+(i/dataArr.length*240)+',80%,60%)';ctx.fillRect(i*barW,canvas.height-h,barW-1,h);sum+=dataArr[i];}
  var avg=sum/dataArr.length;var db=Math.round(20*Math.log10(avg/255+0.001)+90);
  document.getElementById('dbVal').textContent=Math.max(0,db);
}
function playSfx(type){
  var AC=window.AudioContext||window.webkitAudioContext;var ctx=new AC();
  var osc=ctx.createOscillator();var gain=ctx.createGain();osc.connect(gain);gain.connect(ctx.destination);
  gain.gain.value=0.3;
  if(type==='click'){osc.frequency.value=800;gain.gain.setValueAtTime(0.3,ctx.currentTime);gain.gain.exponentialRampToValueAtTime(0.001,ctx.currentTime+0.1);osc.start();osc.stop(ctx.currentTime+0.1);}
  else if(type==='beep'){osc.frequency.value=1000;osc.start();osc.stop(ctx.currentTime+0.2);}
  else if(type==='ding'){osc.frequency.value=1200;gain.gain.exponentialRampToValueAtTime(0.001,ctx.currentTime+0.5);osc.start();osc.stop(ctx.currentTime+0.5);}
}
function setVol(stream,val,el){
  iappyx.audio.setStreamVolume(stream,val/100);
  el.parentElement.querySelector('.val').textContent=val+'%';
}
</script></body></html>""".trimIndent()

    private fun getPrintExportApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
textarea{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.row{display:flex;gap:8px}
.status{font-size:12px;color:rgba(255,255,255,0.4);text-align:center;margin-top:8px}
.preview{background:#fff;color:#111;border-radius:8px;padding:20px;margin-bottom:12px;font-size:14px;line-height:1.6}
.preview h2{font-size:18px;margin-bottom:8px;color:#111}
.preview table{width:100%;border-collapse:collapse;margin:8px 0}
.preview td,.preview th{border:1px solid #ddd;padding:6px 8px;text-align:left;font-size:12px}
.preview th{background:#f0f0f0}
@media print{body{background:#fff!important;padding:0!important}h1,.card,.btn,.btn-outline,.row,.status{display:none!important}.preview{box-shadow:none;margin:0;padding:10px}}
</style></head><body>
<h1>$label</h1>
<div class="card">
  <div class="card-title">Create a Document</div>
  <textarea id="docTitle" rows="1" placeholder="Title">My Report</textarea>
  <textarea id="docContent" rows="5" placeholder="Content">This is a sample document created with iappyxOS.

It demonstrates printing and file export capabilities.</textarea>
</div>
<div id="previewArea" class="preview">
  <h2 id="pTitle">My Report</h2>
  <p id="pContent">This is a sample document...</p>
  <table><tr><th>Feature</th><th>Status</th></tr><tr><td>Print to PDF</td><td>✓</td></tr><tr><td>Save to Downloads</td><td>✓</td></tr><tr><td>Share as file</td><td>✓</td></tr></table>
  <p style="font-size:10px;color:#999;margin-top:12px">Generated by iappyxOS</p>
</div>
<button class="btn" onclick="updatePreview()">Update Preview</button>
<div class="row">
  <button class="btn btn-outline" onclick="printDoc()" style="flex:1">Print / PDF</button>
  <button class="btn btn-outline" onclick="saveDoc()" style="flex:1">Save to Downloads</button>
</div>
<button class="btn btn-outline" onclick="shareDoc()">Share as Text File</button>
<div class="status" id="status"></div>
<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}updatePreview();}
window.addEventListener('load',function(){setTimeout(init,200)});
function updatePreview(){
  var title=document.getElementById('docTitle').value||'Untitled';
  var content=document.getElementById('docContent').value||'';
  document.getElementById('pTitle').textContent=title;
  document.getElementById('pContent').textContent=content;
  iappyx.vibration.tick();
}
function printDoc(){
  updatePreview();
  iappyx.device.print();
}
function saveDoc(){
  var title=document.getElementById('docTitle').value||'document';
  var content=document.getElementById('docContent').value||'';
  var text=title+'\n'+'='.repeat(title.length)+'\n\n'+content+'\n\nGenerated by iappyxOS';
  var b64=btoa(unescape(encodeURIComponent(text)));
  var safeName=title.replace(/[^a-zA-Z0-9 ]/g,'').replace(/ /g,'_')+'.txt';
  var ok=iappyx.storage.saveToDownloads(safeName,b64,'text/plain');
  document.getElementById('status').textContent=ok?'Saved to Downloads: '+safeName:'Could not save';
  iappyx.vibration.click();
}
function shareDoc(){
  var title=document.getElementById('docTitle').value||'document';
  var content=document.getElementById('docContent').value||'';
  var text=title+'\n\n'+content;
  var b64=btoa(unescape(encodeURIComponent(text)));
  var safeName=title.replace(/[^a-zA-Z0-9 ]/g,'').replace(/ /g,'_')+'.txt';
  iappyx.storage.shareFile(safeName,b64,'text/plain');
}
</script></body></html>""".trimIndent()

    private fun getBgRemoverApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:16px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.preview{border-radius:8px;overflow:hidden;margin:10px 0;text-align:center}
.preview img{max-width:100%;border-radius:8px}
.checkerboard{background-image:linear-gradient(45deg,#333 25%,transparent 25%),linear-gradient(-45deg,#333 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#333 75%),linear-gradient(-45deg,transparent 75%,#333 75%);background-size:20px 20px;background-position:0 0,0 10px,10px -10px,-10px 0;border-radius:8px;padding:10px;display:inline-block}
.bg-options{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0}
.bg-opt{width:36px;height:36px;border-radius:8px;cursor:pointer;border:2px solid transparent}
.bg-opt.active{border-color:#4fc3f7}
.empty{text-align:center;color:rgba(255,255,255,0.3);padding:30px;font-size:13px}
.row{display:flex;gap:8px}
</style></head><body>
<h1>$label</h1>
<button class="btn" onclick="capture()">Take Photo</button>
<div id="result"><div class="empty">Take a photo of a person to remove the background</div></div>
<div id="actions" style="display:none">
  <div class="card">
    <div class="card-title">Background</div>
    <div class="bg-options">
      <div class="bg-opt active" style="background-image:linear-gradient(45deg,#666 25%,#999 25%,#999 50%,#666 50%,#666 75%,#999 75%);background-size:10px 10px" onclick="setBg('transparent')"></div>
      <div class="bg-opt" style="background:#fff" onclick="setBg('#ffffff')"></div>
      <div class="bg-opt" style="background:#000" onclick="setBg('#000000')"></div>
      <div class="bg-opt" style="background:#4fc3f7" onclick="setBg('#4fc3f7')"></div>
      <div class="bg-opt" style="background:#69f0ae" onclick="setBg('#69f0ae')"></div>
      <div class="bg-opt" style="background:#ff6b6b" onclick="setBg('#ff6b6b')"></div>
      <div class="bg-opt" style="background:linear-gradient(135deg,#667eea,#764ba2)" onclick="setBg('gradient1')"></div>
      <div class="bg-opt" style="background:linear-gradient(135deg,#f093fb,#f5576c)" onclick="setBg('gradient2')"></div>
    </div>
  </div>
  <div class="row">
    <button class="btn btn-outline" onclick="sharePng()" style="flex:1">Share</button>
    <button class="btn btn-outline" onclick="savePng()" style="flex:1">Save</button>
  </div>
</div>
<canvas id="canvas" style="display:none"></canvas>
<script>
var currentDataUrl=null;
var currentBg='transparent';
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
window.addEventListener('load',function(){setTimeout(init,200)});
function capture(){
  var cbId='seg_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok&&r.dataUrl){
      currentDataUrl=r.dataUrl;
      showResult();
      document.getElementById('actions').style.display='block';
      iappyx.vibration.click();
    } else {
      document.getElementById('result').innerHTML='<div class="empty">'+(r.error||'Failed')+'</div>';
    }
  };
  iappyx.camera.removeBackground(cbId);
}
function showResult(){
  var el=document.getElementById('result');
  var bgStyle='';
  if(currentBg==='transparent') bgStyle='class="checkerboard"';
  else if(currentBg==='gradient1') bgStyle='style="background:linear-gradient(135deg,#667eea,#764ba2);border-radius:8px;padding:10px;display:inline-block"';
  else if(currentBg==='gradient2') bgStyle='style="background:linear-gradient(135deg,#f093fb,#f5576c);border-radius:8px;padding:10px;display:inline-block"';
  else bgStyle='style="background:'+currentBg+';border-radius:8px;padding:10px;display:inline-block"';
  el.innerHTML='<div class="preview"><div '+bgStyle+'><img id="resultImg" src="'+currentDataUrl+'"></div></div>';
}
function setBg(bg){
  currentBg=bg;
  document.querySelectorAll('.bg-opt').forEach(function(e){e.classList.remove('active')});
  event.target.classList.add('active');
  if(currentDataUrl) showResult();
}
function getComposited(callback){
  var img=document.getElementById('resultImg');
  var c=document.getElementById('canvas');
  c.width=img.naturalWidth;c.height=img.naturalHeight;
  var ctx=c.getContext('2d');
  if(currentBg==='transparent'){ctx.clearRect(0,0,c.width,c.height);}
  else if(currentBg==='gradient1'){var g=ctx.createLinearGradient(0,0,c.width,c.height);g.addColorStop(0,'#667eea');g.addColorStop(1,'#764ba2');ctx.fillStyle=g;ctx.fillRect(0,0,c.width,c.height);}
  else if(currentBg==='gradient2'){var g=ctx.createLinearGradient(0,0,c.width,c.height);g.addColorStop(0,'#f093fb');g.addColorStop(1,'#f5576c');ctx.fillStyle=g;ctx.fillRect(0,0,c.width,c.height);}
  else{ctx.fillStyle=currentBg;ctx.fillRect(0,0,c.width,c.height);}
  ctx.drawImage(img,0,0);
  callback(c.toDataURL('image/png'));
}
function sharePng(){
  getComposited(function(dataUrl){
    var b64=dataUrl.split(',')[1];
    iappyx.sharePhoto(b64);
  });
}
function savePng(){
  getComposited(function(dataUrl){
    var b64=dataUrl.split(',')[1];
    var ok=iappyx.storage.saveToDownloads('photo_no_bg_'+Date.now()+'.png',b64,'image/png');
    if(ok) iappyx.vibration.click();
  });
}
</script></body></html>""".trimIndent()

    private fun getSmartNotifApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.log{font-size:12px;color:rgba(255,255,255,0.5);max-height:200px;overflow-y:auto;margin-top:8px}
.log-item{padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.05)}
</style></head><body>
<h1>$label</h1>
<p class="sub">Notification actions, alarms &amp; app shortcuts</p>

<div class="card">
  <div class="card-title">Notification with Actions</div>
  <input id="notifTitle" placeholder="Title" value="Task Reminder">
  <input id="notifBody" placeholder="Body" value="Don't forget to check your tasks">
  <button class="btn" onclick="sendNotif()">Send Notification with Actions</button>
</div>

<div class="card">
  <div class="card-title">Alarm (fires when app is closed)</div>
  <button class="btn btn-outline" onclick="setAlarm()">Set Alarm in 10 seconds</button>
</div>

<div class="card">
  <div class="card-title">App Shortcuts (long-press app icon)</div>
  <button class="btn btn-outline" onclick="setupShortcuts()">Register Shortcuts</button>
  <div style="font-size:11px;color:rgba(255,255,255,0.3);margin-top:4px">After registering, go to home screen and long-press this app's icon</div>
</div>

<div class="card">
  <div class="card-title">Event Log</div>
  <div id="log"><div style="color:rgba(255,255,255,0.2);text-align:center;padding:10px">Waiting for events...</div></div>
</div>

<script>
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
window.addEventListener('load',function(){setTimeout(init,200)});

function addLog(msg){
  var el=document.getElementById('log');
  var time=new Date().toLocaleTimeString();
  el.innerHTML='<div class="log-item"><span style="color:#4fc3f7">'+time+'</span> '+msg+'</div>'+el.innerHTML;
}

window.onNotifAction=function(e){
  addLog('Notification action: <b>'+e.actionId+'</b> (notification #'+e.notificationId+')');
  iappyx.vibration.click();
};

function sendNotif(){
  var title=document.getElementById('notifTitle').value||'Reminder';
  var body=document.getElementById('notifBody').value||'Check your tasks';
  var actions=JSON.stringify([
    {id:'done',label:'Done'},
    {id:'snooze',label:'Snooze 5min'},
    {id:'dismiss',label:'Dismiss'}
  ]);
  iappyx.notification.sendWithActions('42',title,body,actions,'window.onNotifAction');
  addLog('Sent notification with 3 action buttons');
  iappyx.vibration.tick();
}

window.onAlarmFired=function(){
  addLog('Alarm fired!');
  iappyx.notification.send('Alarm','Your alarm just fired!');
  iappyx.vibration.heavyClick();
};

function setAlarm(){
  iappyx.alarm.set(Date.now()+10000,'window.onAlarmFired');
  addLog('Alarm set for 10 seconds from now');
  iappyx.vibration.tick();
}

window.onShortcut=function(e){
  addLog('Shortcut tapped: <b>'+e.shortcutId+'</b>');
  iappyx.vibration.click();
};

function setupShortcuts(){
  iappyx.device.setShortcuts(JSON.stringify([
    {id:'notify',label:'Quick Notification',callback:'window.onShortcut'},
    {id:'alarm',label:'Set Quick Alarm',callback:'window.onShortcut'}
  ]));
  addLog('Registered 2 app shortcuts');
  iappyx.vibration.tick();
}
</script></body></html>""".trimIndent()

    private fun getShareMediaApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.preview{background:#1a1a2e;border-radius:8px;padding:12px;margin:8px 0;font-size:13px}
.preview img{max-width:100%;border-radius:8px;margin-top:8px}
.empty{text-align:center;color:rgba(255,255,255,0.2);padding:20px;font-size:13px}
.media-info{background:#0f3460;border-radius:10px;padding:14px;text-align:center;margin:10px 0}
.media-title{font-size:16px;font-weight:600}
.media-artist{font-size:13px;color:rgba(255,255,255,0.5);margin-top:2px}
.controls{display:flex;justify-content:center;gap:20px;margin:12px 0}
.ctrl-btn{width:44px;height:44px;border-radius:50%;border:1.5px solid #4fc3f7;background:transparent;color:#4fc3f7;font-size:18px;cursor:pointer;display:flex;align-items:center;justify-content:center}
.ctrl-btn:active{background:#4fc3f7;color:#0d0d1a}
.log{font-size:12px;color:rgba(255,255,255,0.5);max-height:150px;overflow-y:auto;margin-top:8px}
.log-item{padding:4px 0;border-bottom:1px solid rgba(255,255,255,0.05)}
</style></head><body>
<h1>$label</h1>
<p class="sub">Share target &amp; media session controls</p>

<div class="card">
  <div class="card-title">Share Target</div>
  <div style="font-size:12px;color:rgba(255,255,255,0.4);margin-bottom:8px">Share text or images from any app — this app will receive them</div>
  <div id="shareContent"><div class="empty">Nothing shared yet. Try sharing text or an image from another app.</div></div>
</div>

<div class="card">
  <div class="card-title">Media Session</div>
  <div style="font-size:12px;color:rgba(255,255,255,0.4);margin-bottom:8px">Set up lock screen controls and headphone button handling</div>
  <input id="songTitle" placeholder="Song title" value="My Podcast">
  <input id="songArtist" placeholder="Artist" value="iappyxOS Radio">
  <button class="btn" onclick="startMediaSession()">Activate Media Session</button>
  <div id="mediaArea" style="display:none">
    <div class="media-info">
      <div class="media-title" id="mTitle">-</div>
      <div class="media-artist" id="mArtist">-</div>
    </div>
    <div class="controls">
      <button class="ctrl-btn" onclick="addLog('Previous')">⏮</button>
      <button class="ctrl-btn" onclick="addLog('Play')" style="width:52px;height:52px;font-size:22px">▶</button>
      <button class="ctrl-btn" onclick="addLog('Next')">⏭</button>
    </div>
    <div style="font-size:11px;color:rgba(255,255,255,0.3);text-align:center">Try: lock screen controls, headphone buttons, or car mode</div>
  </div>
</div>

<div class="card">
  <div class="card-title">Event Log</div>
  <div id="log"><div class="empty">Waiting for events...</div></div>
</div>

<script>
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  iappyx.device.setShareCallback('window.onShareReceived');
  addLog('Share target registered');
}
window.addEventListener('load',function(){setTimeout(init,200)});

function addLog(msg){
  var el=document.getElementById('log');
  var time=new Date().toLocaleTimeString();
  el.innerHTML='<div class="log-item"><span style="color:#4fc3f7">'+time+'</span> '+esc(msg)+'</div>'+el.innerHTML;
}

window.onShareReceived=function(data){
  var el=document.getElementById('shareContent');
  if(data.type==='text'){
    el.innerHTML='<div class="preview"><strong>Shared text:</strong><br>'+esc(data.text)+'</div>';
    addLog('Received shared text ('+data.text.length+' chars)');
  } else if(data.type==='image'){
    el.innerHTML='<div class="preview"><strong>Shared image:</strong><img src="'+data.dataUrl+'"></div>';
    addLog('Received shared image');
  }
  iappyx.vibration.click();
};

function startMediaSession(){
  var title=document.getElementById('songTitle').value||'Unknown';
  var artist=document.getElementById('songArtist').value||'Unknown';
  iappyx.audio.setMediaSession(JSON.stringify({title:title,artist:artist,album:'iappyxOS'}));
  document.getElementById('mTitle').textContent=title;
  document.getElementById('mArtist').textContent=artist;
  document.getElementById('mediaArea').style.display='block';
  addLog('Media session active: '+title);
  iappyx.vibration.click();
}

window.onMediaButton=function(e){
  addLog('Media button: '+e.action);
  iappyx.vibration.tick();
};

function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getRemindersApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.btn-sm{padding:10px 14px;font-size:13px;width:auto;border-radius:8px}
input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.row{display:flex;gap:8px}
.log{font-size:12px;color:rgba(255,255,255,0.5);max-height:200px;overflow-y:auto;margin-top:8px}
.log-item{padding:6px 0;border-bottom:1px solid rgba(255,255,255,0.05)}
.badge-row{display:flex;align-items:center;justify-content:center;gap:16px;margin:10px 0}
.badge-btn{width:40px;height:40px;border-radius:50%;border:1.5px solid #4fc3f7;background:transparent;color:#4fc3f7;font-size:18px;cursor:pointer}
.badge-num{font-size:28px;font-weight:700;color:#4fc3f7}
</style></head><body>
<h1>$label</h1>
<p class="sub">Scheduled notifications, repeating alarms, badge count</p>

<div class="card">
  <div class="card-title">Schedule a Notification</div>
  <input id="notifTitle" placeholder="Title" value="Don't forget!">
  <input id="notifBody" placeholder="Message" value="Time to check in">
  <div class="row">
    <button class="btn-sm btn-outline" onclick="scheduleNotif(10)" style="flex:1">In 10s</button>
    <button class="btn-sm btn-outline" onclick="scheduleNotif(60)" style="flex:1">In 1min</button>
    <button class="btn-sm btn-outline" onclick="scheduleNotif(300)" style="flex:1">In 5min</button>
  </div>
</div>

<div class="card">
  <div class="card-title">Repeating Alarm</div>
  <button class="btn btn-outline" onclick="startRepeating()">Start every 60 seconds</button>
  <button class="btn-sm btn-outline" onclick="stopRepeating()" style="width:100%">Stop repeating</button>
</div>

<div class="card">
  <div class="card-title">App Badge Count</div>
  <div class="badge-row">
    <button class="badge-btn" onclick="changeBadge(-1)">-</button>
    <div class="badge-num" id="badgeNum">0</div>
    <button class="badge-btn" onclick="changeBadge(1)">+</button>
  </div>
  <button class="btn-sm btn-outline" onclick="clearBadge()" style="width:100%">Clear badge</button>
</div>

<div class="card">
  <div class="card-title">Do Not Disturb</div>
  <button class="btn btn-outline" id="dndBtn" onclick="toggleDnd()">Enable DND</button>
</div>

<div class="card">
  <div class="card-title">Event Log</div>
  <div id="log"><div style="color:rgba(255,255,255,0.2);text-align:center;padding:10px">Waiting...</div></div>
</div>

<script>
var badgeCount=0;
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  updateDndBtn();
}
window.addEventListener('load',function(){setTimeout(init,200)});

function addLog(msg){
  var el=document.getElementById('log');
  el.innerHTML='<div class="log-item"><span style="color:#4fc3f7">'+new Date().toLocaleTimeString()+'</span> '+msg+'</div>'+el.innerHTML;
}

function scheduleNotif(secs){
  var title=document.getElementById('notifTitle').value||'Reminder';
  var body=document.getElementById('notifBody').value||'';
  var id=String(Date.now()%100000);
  iappyx.notification.schedule(id,title,body,Date.now()+secs*1000);
  addLog('Scheduled notification in '+secs+'s (id: '+id+')');
  iappyx.vibration.tick();
}

window.onRepeatAlarm=function(){
  addLog('Repeating alarm fired!');
  iappyx.vibration.click();
};

function startRepeating(){
  iappyx.alarm.setRepeating('repeat60',60000,'window.onRepeatAlarm');
  addLog('Started repeating alarm every 60s');
  iappyx.vibration.tick();
}

function stopRepeating(){
  iappyx.alarm.cancelById('repeat60');
  addLog('Stopped repeating alarm');
}

function changeBadge(d){
  badgeCount=Math.max(0,badgeCount+d);
  document.getElementById('badgeNum').textContent=badgeCount;
  iappyx.notification.setBadge(badgeCount);
  iappyx.vibration.tick();
}

function clearBadge(){badgeCount=0;document.getElementById('badgeNum').textContent='0';iappyx.notification.setBadge(0);}

function toggleDnd(){
  var active=iappyx.device.isDndActive();
  iappyx.device.setDndMode(!active);
  setTimeout(updateDndBtn,500);
  addLog('DND '+(active?'disabled':'enabled'));
}

function updateDndBtn(){
  var active=iappyx.device.isDndActive();
  document.getElementById('dndBtn').textContent=active?'Disable DND':'Enable DND';
}
</script></body></html>""".trimIndent()

    private fun getPowerToolsApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.result{background:#0f3460;border-radius:8px;padding:12px;margin:8px 0;font-size:13px;white-space:pre-wrap;max-height:200px;overflow-y:auto;word-break:break-all}
.log{font-size:12px;color:rgba(255,255,255,0.5);max-height:150px;overflow-y:auto;margin-top:8px}
.log-item{padding:4px 0;border-bottom:1px solid rgba(255,255,255,0.05)}
p.hint{font-size:11px;color:rgba(255,255,255,0.3);margin-bottom:8px}
</style></head><body>
<h1>$label</h1>
<p class="sub">Clipboard monitor, read Downloads, text selection</p>

<div class="card">
  <div class="card-title">Clipboard Monitor</div>
  <p class="hint">Copy any text on your device — it will appear here automatically</p>
  <button class="btn btn-outline" onclick="startClipboard()">Start Monitoring</button>
  <div id="clipLog"><div style="color:rgba(255,255,255,0.2);text-align:center;padding:10px">Not monitoring yet</div></div>
</div>

<div class="card">
  <div class="card-title">Read from Downloads</div>
  <p class="hint">Read a text file from your Downloads folder by name</p>
  <input id="dlFilename" placeholder="filename.txt" value="">
  <button class="btn btn-outline" onclick="readDl()">Read File</button>
  <div id="dlResult"></div>
</div>

<div class="card">
  <div class="card-title">Text Selection</div>
  <p class="hint">Select any text below to see the selection callback fire</p>
  <div style="background:#0f3460;border-radius:8px;padding:12px;font-size:14px;line-height:1.8;user-select:text">
    The quick brown fox jumps over the lazy dog. iappyxOS is a platform for creating real Android apps from prompts. Select any text in this paragraph to see the text selection callback in action.
  </div>
  <div id="selResult" style="margin-top:8px;font-size:12px;color:#69f0ae"></div>
</div>

<div class="card">
  <div class="card-title">Event Log</div>
  <div id="log"><div style="color:rgba(255,255,255,0.2);text-align:center;padding:10px">Waiting...</div></div>
</div>

<script>
function init(){
  if(typeof iappyx==='undefined'){setTimeout(init,50);return;}
  // Register text selection callback
  iappyx.onTextSelected(function(e){
    document.getElementById('selResult').textContent='Selected: "'+e.text+'"';
    addLog('Text selected: "'+e.text.substring(0,50)+(e.text.length>50?'...':'')+'"');
  });
  addLog('Ready');
}
window.addEventListener('load',function(){setTimeout(init,200)});

function addLog(msg){
  var el=document.getElementById('log');
  el.innerHTML='<div class="log-item"><span style="color:#4fc3f7">'+new Date().toLocaleTimeString()+'</span> '+esc(msg)+'</div>'+el.innerHTML;
}

function startClipboard(){
  iappyx.device.onClipboardChange('window.onClipChange');
  document.getElementById('clipLog').innerHTML='<div style="color:#69f0ae;text-align:center;padding:10px">Monitoring active — copy something!</div>';
  addLog('Clipboard monitoring started');
  iappyx.vibration.tick();
}

window.onClipChange=function(e){
  var el=document.getElementById('clipLog');
  el.innerHTML='<div class="result">'+esc(e.text)+'</div>'+el.innerHTML;
  addLog('Clipboard: "'+e.text.substring(0,30)+(e.text.length>30?'...':'')+'"');
  iappyx.vibration.tick();
};

function readDl(){
  var filename=document.getElementById('dlFilename').value.trim();
  if(!filename){addLog('Enter a filename');return;}
  var content=iappyx.device.readFromDownloads(filename);
  var el=document.getElementById('dlResult');
  if(content){
    el.innerHTML='<div class="result">'+esc(content.substring(0,2000))+(content.length>2000?'\n...truncated':'')+'</div>';
    addLog('Read '+filename+' ('+content.length+' chars)');
  } else {
    el.innerHTML='<div class="result" style="color:#ff6b6b">File not found: '+esc(filename)+'</div>';
    addLog('File not found: '+filename);
  }
  iappyx.vibration.click();
}

function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getCompassApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;display:flex;flex-direction:column;align-items:center;padding:16px}
h1{font-size:20px;margin-bottom:4px}
.sub{font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:24px}
.compass-wrap{position:relative;width:280px;height:280px;margin-bottom:24px}
.compass-ring{width:280px;height:280px;border-radius:50%;border:3px solid #1a1a2e;position:relative;transition:transform 0.3s ease-out}
.compass-ring svg{width:100%;height:100%}
.needle{position:absolute;top:50%;left:50%;width:4px;height:120px;margin-left:-2px;margin-top:-120px;background:linear-gradient(to bottom,#ff6b6b 50%,#eaeaea 50%);border-radius:2px;transform-origin:bottom center;z-index:2}
.center-dot{position:absolute;top:50%;left:50%;width:12px;height:12px;margin:-6px;background:#4fc3f7;border-radius:50%;z-index:3}
.heading-display{font-size:64px;font-weight:700;color:#4fc3f7;margin-bottom:4px}
.direction{font-size:24px;color:rgba(255,255,255,0.7);margin-bottom:24px}
.info{background:#1a1a2e;border-radius:12px;padding:16px;width:100%;max-width:320px}
.info-row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid rgba(255,255,255,0.05)}
.info-row:last-child{border:none}
.info-label{color:rgba(255,255,255,0.4);font-size:13px}
.info-val{font-size:13px;font-weight:600}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;max-width:320px;margin-top:16px}
.btn:active{opacity:0.8}
</style></head><body>
<h1>$label</h1>
<p class="sub">Digital compass with heading</p>

<div class="heading-display" id="deg">---°</div>
<div class="direction" id="dir">--</div>

<div class="compass-wrap">
  <div class="compass-ring" id="ring">
    <svg viewBox="0 0 280 280">
      <circle cx="140" cy="140" r="130" fill="none" stroke="#1a1a2e" stroke-width="2"/>
      <text x="140" y="28" text-anchor="middle" fill="#ff6b6b" font-size="18" font-weight="700">N</text>
      <text x="258" y="145" text-anchor="middle" fill="rgba(255,255,255,0.5)" font-size="16">E</text>
      <text x="140" y="262" text-anchor="middle" fill="rgba(255,255,255,0.5)" font-size="16">S</text>
      <text x="22" y="145" text-anchor="middle" fill="rgba(255,255,255,0.5)" font-size="16">W</text>
      <line x1="140" y1="36" x2="140" y2="46" stroke="rgba(255,255,255,0.2)" stroke-width="1"/>
      <line x1="244" y1="140" x2="234" y2="140" stroke="rgba(255,255,255,0.2)" stroke-width="1"/>
      <line x1="140" y1="244" x2="140" y2="234" stroke="rgba(255,255,255,0.2)" stroke-width="1"/>
      <line x1="36" y1="140" x2="46" y2="140" stroke="rgba(255,255,255,0.2)" stroke-width="1"/>
    </svg>
  </div>
  <div class="needle" id="needle"></div>
  <div class="center-dot"></div>
</div>

<div class="info">
  <div class="info-row"><span class="info-label">Heading</span><span class="info-val" id="headingVal">--</span></div>
  <div class="info-row"><span class="info-label">Accuracy</span><span class="info-val" id="accVal">--</span></div>
  <div class="info-row"><span class="info-label">Status</span><span class="info-val" id="statusVal">Starting...</span></div>
</div>

<button class="btn" id="startBtn" onclick="toggleCompass()">Stop</button>

<script>
var running=false;
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}startCompass();}
window.addEventListener('load',function(){setTimeout(init,200)});

function getDirection(h){
  var dirs=['N','NNE','NE','ENE','E','ESE','SE','SSE','S','SSW','SW','WSW','W','WNW','NW','NNW'];
  return dirs[Math.round(h/22.5)%16];
}

var accLabels=['Unreliable','Low','Medium','High'];

window.onCompass=function(e){
  if(e.error){document.getElementById('statusVal').textContent=e.error;return;}
  var h=Math.round(e.heading*10)/10;
  document.getElementById('deg').textContent=Math.round(h)+'°';
  document.getElementById('dir').textContent=getDirection(h);
  document.getElementById('headingVal').textContent=h+'°';
  document.getElementById('accVal').textContent=accLabels[e.accuracy]||'Unknown';
  document.getElementById('statusVal').textContent='Active';
  document.getElementById('ring').style.transform='rotate('+ (-h) +'deg)';
  document.getElementById('needle').style.transform='rotate(0deg)';
};

function startCompass(){
  iappyx.sensor.startCompass('window.onCompass');
  running=true;
  document.getElementById('startBtn').textContent='Stop';
  document.getElementById('statusVal').textContent='Starting...';
}

function toggleCompass(){
  if(running){iappyx.sensor.stop();running=false;document.getElementById('startBtn').textContent='Start';document.getElementById('statusVal').textContent='Stopped';}
  else{startCompass();}
}
</script></body></html>""".trimIndent()

    private fun getWallpaperApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.preview{width:100%;max-height:300px;object-fit:contain;border-radius:8px;margin-bottom:10px;display:none}
.colors{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:12px}
.color-btn{height:60px;border-radius:8px;border:2px solid transparent;cursor:pointer}
.color-btn.active{border-color:#4fc3f7}
.gradient-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin-bottom:12px}
.gradient-btn{height:80px;border-radius:8px;border:2px solid transparent;cursor:pointer}
.gradient-btn.active{border-color:#4fc3f7}
.target-row{display:flex;gap:6px;margin-bottom:12px}
.target-btn{flex:1;padding:10px;border-radius:8px;border:1.5px solid rgba(255,255,255,0.15);background:transparent;color:rgba(255,255,255,0.5);font-size:12px;cursor:pointer;text-align:center}
.target-btn.active{border-color:#4fc3f7;color:#4fc3f7;background:rgba(79,195,247,0.1)}
.status{text-align:center;font-size:13px;color:#69f0ae;margin-top:8px;min-height:20px}
canvas{display:none}
</style></head><body>
<h1>$label</h1>
<p class="sub">Set your device wallpaper</p>

<div class="card">
  <div class="card-title">Apply To</div>
  <div class="target-row">
    <button class="target-btn active" onclick="setTarget('both',this)">Both</button>
    <button class="target-btn" onclick="setTarget('home',this)">Home Screen</button>
    <button class="target-btn" onclick="setTarget('lock',this)">Lock Screen</button>
  </div>
</div>

<div class="card">
  <div class="card-title">From Camera</div>
  <img id="photoPreview" class="preview">
  <button class="btn btn-outline" onclick="takePhoto()">Take Photo</button>
  <button class="btn" onclick="setFromPhoto()" id="setPhotoBtn" style="display:none">Set as Wallpaper</button>
</div>

<div class="card">
  <div class="card-title">Solid Color</div>
  <div class="colors" id="colors"></div>
  <button class="btn" onclick="setColorWallpaper()">Set Color Wallpaper</button>
</div>

<div class="card">
  <div class="card-title">Gradient</div>
  <div class="gradient-grid" id="gradients"></div>
  <button class="btn" onclick="setGradientWallpaper()">Set Gradient Wallpaper</button>
</div>

<div class="status" id="status"></div>
<canvas id="cv" width="1080" height="1920"></canvas>

<script>
var photoData=null, selColor='#0d0d1a', selGradient=0, wpTarget='both';
var solidColors=['#0d0d1a','#1a1a2e','#0f3460','#16213e','#1b262c','#2d4059','#3a0ca3','#480ca8','#560bad','#7209b7','#b5179e','#f72585','#ef233c','#d90429','#e63946','#264653','#2a9d8f','#e9c46a','#f4a261','#e76f51'];
var gradients=[
  ['#0d0d1a','#0f3460'],['#16213e','#e94560'],['#1a1a2e','#4fc3f7'],['#0f0c29','#302b63'],
  ['#2d4059','#ea5455'],['#1b262c','#3282b8'],['#0d0d1a','#69f0ae'],['#1a1a2e','#f72585']
];

function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}buildUI();}
window.addEventListener('load',function(){setTimeout(init,200)});

function setTarget(t,el){
  wpTarget=t;
  document.querySelectorAll('.target-btn').forEach(function(b){b.className='target-btn'});
  el.className='target-btn active';
}

function applyWallpaper(data){
  iappyx.device.setWallpaperTarget(data, wpTarget);
  var label=wpTarget==='both'?'home + lock':wpTarget+' screen';
  setStatus('Wallpaper set on '+label+'!');
  iappyx.vibration.click();
}

function buildUI(){
  var ch=document.getElementById('colors');
  solidColors.forEach(function(c,i){
    var d=document.createElement('div');
    d.className='color-btn'+(i===0?' active':'');
    d.style.background=c;
    d.onclick=function(){selColor=c;document.querySelectorAll('.color-btn').forEach(function(b){b.className='color-btn'});d.className='color-btn active';};
    ch.appendChild(d);
  });
  var gh=document.getElementById('gradients');
  gradients.forEach(function(g,i){
    var d=document.createElement('div');
    d.className='gradient-btn'+(i===0?' active':'');
    d.style.background='linear-gradient(135deg,'+g[0]+','+g[1]+')';
    d.onclick=function(){selGradient=i;document.querySelectorAll('.gradient-btn').forEach(function(b){b.className='gradient-btn'});d.className='gradient-btn active';};
    gh.appendChild(d);
  });
}

function takePhoto(){
  var cbId='wp_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){
      photoData=r.dataUrl;
      var img=document.getElementById('photoPreview');
      img.src=r.dataUrl;img.style.display='block';
      document.getElementById('setPhotoBtn').style.display='block';
      setStatus('Photo ready — tap to set as wallpaper');
    }else{setStatus('Camera error: '+(r.error||'unknown'));}
  };
  iappyx.camera.takePhoto(cbId);
}

function setFromPhoto(){
  if(!photoData){setStatus('Take a photo first');return;}
  applyWallpaper(photoData);
}

function setColorWallpaper(){
  var cv=document.getElementById('cv'),ctx=cv.getContext('2d');
  ctx.fillStyle=selColor;ctx.fillRect(0,0,1080,1920);
  applyWallpaper(cv.toDataURL('image/jpeg',0.9));
}

function setGradientWallpaper(){
  var cv=document.getElementById('cv'),ctx=cv.getContext('2d');
  var g=ctx.createLinearGradient(0,0,1080,1920);
  g.addColorStop(0,gradients[selGradient][0]);
  g.addColorStop(1,gradients[selGradient][1]);
  ctx.fillStyle=g;ctx.fillRect(0,0,1080,1920);
  applyWallpaper(cv.toDataURL('image/jpeg',0.9));
}

function setStatus(msg){document.getElementById('status').textContent=msg;setTimeout(function(){document.getElementById('status').textContent='';},3000);}
</script></body></html>""".trimIndent()

    private fun getMediaGalleryApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.tabs{display:flex;gap:4px;margin-bottom:16px}
.tab{flex:1;padding:10px;border-radius:8px;border:1.5px solid rgba(255,255,255,0.15);background:transparent;color:rgba(255,255,255,0.5);font-size:13px;cursor:pointer;text-align:center}
.tab.active{border-color:#4fc3f7;color:#4fc3f7;background:rgba(79,195,247,0.1)}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin-bottom:12px}
.thumb{aspect-ratio:1;border-radius:8px;object-fit:cover;width:100%;cursor:pointer;background:#1a1a2e}
.list-item{background:#1a1a2e;border-radius:10px;padding:12px;margin-bottom:8px;display:flex;align-items:center;gap:12px;cursor:pointer}
.list-icon{width:44px;height:44px;border-radius:8px;background:#0f3460;display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0}
.list-info{flex:1;min-width:0}
.list-name{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.list-meta{font-size:11px;color:rgba(255,255,255,0.4)}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.9);z-index:10;display:none;align-items:center;justify-content:center;flex-direction:column;padding:16px;overflow-y:auto}
.overlay img{max-width:100%;max-height:60vh;border-radius:8px}
.overlay .close{position:absolute;top:16px;right:16px;font-size:28px;color:#fff;cursor:pointer;z-index:11}
.meta{background:#1a1a2e;border-radius:8px;padding:12px;margin-top:12px;font-size:12px;color:rgba(255,255,255,0.5);width:100%;max-width:400px}
.meta-row{display:flex;justify-content:space-between;padding:3px 0}
.meta-label{color:rgba(255,255,255,0.3)}
.overlay-actions{display:flex;gap:8px;margin-top:10px}
.overlay-btn{padding:10px 20px;border-radius:50px;border:1.5px solid #4fc3f7;background:transparent;color:#4fc3f7;font-size:13px;cursor:pointer}
.empty{text-align:center;color:rgba(255,255,255,0.3);padding:40px 0;font-size:14px}
.status{text-align:center;font-size:13px;color:#69f0ae;min-height:20px;margin-bottom:8px}
</style></head><body>
<h1>$label</h1>
<p class="sub">Browse photos, videos & music on your device</p>

<div class="tabs">
  <button class="tab active" onclick="switchTab('photos',this)">Photos</button>
  <button class="tab" onclick="switchTab('videos',this)">Videos</button>
  <button class="tab" onclick="switchTab('audio',this)">Music</button>
</div>

<button class="btn btn-outline" onclick="pickImage()" id="pickBtn">Pick from Gallery</button>
<div class="status" id="status"></div>
<div id="content"><div class="empty">Tap a tab to load media</div></div>

<div class="overlay" id="overlay">
  <span class="close" onclick="closeOverlay()">&times;</span>
  <img id="overlayImg">
  <div id="metaInfo" class="meta" style="display:none"></div>
  <div class="overlay-actions">
    <button class="overlay-btn" id="saveBtn" style="display:none" onclick="saveCurrentImage()">Save to Gallery</button>
    <button class="overlay-btn" onclick="closeOverlay()">Close</button>
  </div>
</div>

<script>
var currentTab='photos';
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}loadPhotos();}
window.addEventListener('load',function(){setTimeout(init,200)});

function switchTab(tab,el){
  currentTab=tab;
  document.querySelectorAll('.tab').forEach(function(t){t.className='tab'});
  el.className='tab active';
  document.getElementById('pickBtn').style.display=tab==='photos'?'block':'none';
  if(tab==='photos') loadPhotos();
  else if(tab==='videos') loadVideos();
  else loadAudio();
}

function loadPhotos(){
  document.getElementById('content').innerHTML='<div class="empty">Loading...</div>';
  var cbId='gimg_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(!r.ok){document.getElementById('content').innerHTML='<div class="empty">'+r.error+'</div>';return;}
    if(!r.images.length){document.getElementById('content').innerHTML='<div class="empty">No photos found</div>';return;}
    var html='<div class="grid">';
    r.images.forEach(function(img){
      html+='<div class="thumb" style="background:#1a1a2e" onclick="loadThumb('+img.id+')" id="t'+img.id+'"></div>';
    });
    html+='</div>';
    document.getElementById('content').innerHTML=html;
    // Load thumbnails
    r.images.slice(0,30).forEach(function(img){loadThumb(img.id);});
  };
  iappyx.media.getImages(cbId,30);
}

function loadThumb(id){
  var cbId='th_'+id+'_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){
      var el=document.getElementById('t'+id);
      if(el){
        var img=document.createElement('img');
        img.className='thumb';img.src=r.dataUrl;
        img.onclick=function(){showFull(id);};
        el.replaceWith(img);
      }
    }
  };
  iappyx.media.loadThumbnail(cbId,id);
}

var currentImgDataUrl=null;
function showFull(id){
  document.getElementById('status').textContent='Loading...';
  var cbId='full_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    document.getElementById('status').textContent='';
    if(r.ok){
      currentImgDataUrl=r.dataUrl;
      document.getElementById('overlayImg').src=r.dataUrl;
      document.getElementById('saveBtn').style.display='inline-block';
      document.getElementById('overlay').style.display='flex';
      loadMeta(id,'image');
    }
  };
  iappyx.media.loadImage(cbId,id);
}

function loadMeta(id,type){
  var cbId='meta_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    var el=document.getElementById('metaInfo');
    if(!r.ok){el.style.display='none';return;}
    var html='';
    if(r.title) html+='<div class="meta-row"><span class="meta-label">Title</span><span>'+esc(r.title)+'</span></div>';
    if(r.artist) html+='<div class="meta-row"><span class="meta-label">Artist</span><span>'+esc(r.artist)+'</span></div>';
    if(r.album) html+='<div class="meta-row"><span class="meta-label">Album</span><span>'+esc(r.album)+'</span></div>';
    if(r.duration) html+='<div class="meta-row"><span class="meta-label">Duration</span><span>'+Math.round(r.duration/1000)+'s</span></div>';
    if(r.width&&r.height) html+='<div class="meta-row"><span class="meta-label">Size</span><span>'+r.width+'×'+r.height+'</span></div>';
    if(r.bitrate) html+='<div class="meta-row"><span class="meta-label">Bitrate</span><span>'+Math.round(r.bitrate/1000)+' kbps</span></div>';
    if(r.mimeType) html+='<div class="meta-row"><span class="meta-label">Type</span><span>'+r.mimeType+'</span></div>';
    if(r.date) html+='<div class="meta-row"><span class="meta-label">Date</span><span>'+r.date+'</span></div>';
    el.innerHTML=html;el.style.display=html?'block':'none';
  };
  iappyx.media.getMetadata(cbId,id,type);
}

function saveCurrentImage(){
  if(!currentImgDataUrl) return;
  var cbId='save_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    document.getElementById('status').textContent=r.ok?'Saved to gallery!':'Save failed: '+(r.error||'');
  };
  iappyx.media.saveToGallery(cbId,currentImgDataUrl,'');
  iappyx.vibration.tick();
}

function closeOverlay(){
  document.getElementById('overlay').style.display='none';
  document.getElementById('metaInfo').style.display='none';
  document.getElementById('saveBtn').style.display='none';
  currentImgDataUrl=null;
}

function loadVideos(){
  document.getElementById('content').innerHTML='<div class="empty">Loading...</div>';
  var cbId='gvid_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(!r.ok){document.getElementById('content').innerHTML='<div class="empty">'+r.error+'</div>';return;}
    if(!r.videos.length){document.getElementById('content').innerHTML='<div class="empty">No videos found</div>';return;}
    var html='';
    r.videos.forEach(function(v){
      var dur=v.duration?Math.round(v.duration/1000)+'s':'';
      var size=v.size?(v.size/1048576).toFixed(1)+' MB':'';
      html+='<div class="list-item"><div class="list-icon">🎬</div><div class="list-info"><div class="list-name">'+esc(v.name)+'</div><div class="list-meta">'+dur+' · '+size+' · '+v.width+'×'+v.height+'</div></div></div>';
    });
    document.getElementById('content').innerHTML=html;
  };
  iappyx.media.getVideos(cbId,30);
}

function loadAudio(){
  document.getElementById('content').innerHTML='<div class="empty">Loading...</div>';
  var cbId='gaud_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(!r.ok){document.getElementById('content').innerHTML='<div class="empty">'+r.error+'</div>';return;}
    if(!r.audio.length){document.getElementById('content').innerHTML='<div class="empty">No music found</div>';return;}
    var html='';
    r.audio.forEach(function(a){
      var dur=a.duration?Math.floor(a.duration/60000)+':'+('0'+Math.floor(a.duration/1000%60)).slice(-2):'';
      html+='<div class="list-item" onclick="playTrack('+a.id+')"><div class="list-icon">🎵</div><div class="list-info"><div class="list-name">'+esc(a.title||a.name)+'</div><div class="list-meta">'+(a.artist||'Unknown')+' · '+dur+'</div></div></div>';
    });
    document.getElementById('content').innerHTML=html;
  };
  iappyx.media.getAudio(cbId,50);
}

function playTrack(id){
  iappyx.audio.requestFocus('window.onAudioFocus');
  iappyx.media.playAudio(id);
  document.getElementById('status').textContent='Playing...';
  loadMeta(id,'audio');
}
window.onAudioFocus=function(e){
  if(e.type==='loss'||e.type==='lossTransient') document.getElementById('status').textContent='Paused (focus lost)';
  else if(e.type==='gain') document.getElementById('status').textContent='Playing...';
};

function pickImage(){
  var cbId='pick_'+Date.now();
  window._iappyxCb=window._iappyxCb||{};
  window._iappyxCb[cbId]=function(r){
    if(r.ok){
      document.getElementById('overlayImg').src=r.dataUrl;
      document.getElementById('overlay').style.display='flex';
    }else{document.getElementById('status').textContent=r.error||'Cancelled';}
  };
  iappyx.media.pickImage(cbId);
}

function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    private fun getDownloadMgrApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px}
h1{font-size:20px;text-align:center;margin-bottom:4px}
.sub{text-align:center;font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:16px}
.card{background:#1a1a2e;border-radius:12px;padding:16px;margin-bottom:12px}
.card-title{font-size:13px;color:#4fc3f7;font-weight:600;margin-bottom:10px}
input{background:#0f3460;border:1px solid #1a4a8a;color:#fff;border-radius:8px;padding:10px 12px;font-size:14px;width:100%;outline:none;margin-bottom:8px}
.btn{background:#4fc3f7;color:#0d0d1a;border:none;border-radius:50px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;width:100%;margin-bottom:10px}
.btn:active{opacity:0.8}
.btn-outline{background:transparent;border:1.5px solid #4fc3f7;color:#4fc3f7}
.btn-sm{padding:8px 14px;font-size:12px;width:auto;border-radius:8px}
.sample-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:12px}
.sample{background:#0f3460;border-radius:8px;padding:12px;cursor:pointer;text-align:center}
.sample:active{opacity:0.8}
.sample-icon{font-size:24px;margin-bottom:4px}
.sample-name{font-size:12px;color:rgba(255,255,255,0.7)}
.sample-size{font-size:10px;color:rgba(255,255,255,0.3)}
.dl-item{background:#0f3460;border-radius:10px;padding:12px;margin-bottom:8px}
.dl-name{font-size:13px;font-weight:600;margin-bottom:6px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dl-bar{height:6px;background:rgba(255,255,255,0.1);border-radius:3px;overflow:hidden;margin-bottom:4px}
.dl-fill{height:100%;background:#4fc3f7;border-radius:3px;transition:width 0.3s}
.dl-meta{display:flex;justify-content:space-between;font-size:11px;color:rgba(255,255,255,0.4)}
.dl-done{color:#69f0ae}
.dl-fail{color:#ff6b6b}
.empty{text-align:center;color:rgba(255,255,255,0.3);padding:20px 0;font-size:13px}
</style></head><body>
<h1>$label</h1>
<p class="sub">Download files with progress tracking</p>

<div class="card">
  <div class="card-title">Custom URL</div>
  <input id="url" placeholder="https://example.com/file.pdf" value="">
  <input id="fname" placeholder="Filename (optional)">
  <button class="btn" onclick="startDownload()">Download</button>
</div>

<div class="card">
  <div class="card-title">Sample Files</div>
  <div class="sample-grid">
    <div class="sample" onclick="dlSample('https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf','test.pdf')">
      <div class="sample-icon">📄</div>
      <div class="sample-name">PDF Document</div>
      <div class="sample-size">~13 KB</div>
    </div>
    <div class="sample" onclick="dlSample('https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/280px-PNG_transparency_demonstration_1.png','test.png')">
      <div class="sample-icon">🖼️</div>
      <div class="sample-name">PNG Image</div>
      <div class="sample-size">~60 KB</div>
    </div>
    <div class="sample" onclick="dlSample('https://filesamples.com/samples/audio/mp3/sample3.mp3','sample.mp3')">
      <div class="sample-icon">🎵</div>
      <div class="sample-name">MP3 Audio</div>
      <div class="sample-size">~320 KB</div>
    </div>
    <div class="sample" onclick="dlSample('https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-zip-file.zip','sample.zip')">
      <div class="sample-icon">📦</div>
      <div class="sample-name">ZIP Archive</div>
      <div class="sample-size">~1 KB</div>
    </div>
  </div>
</div>

<div class="card">
  <div class="card-title">Downloads</div>
  <div id="downloads"><div class="empty">No downloads yet</div></div>
</div>

<script>
var downloads={};
function init(){if(typeof iappyx==='undefined'){setTimeout(init,50);return;}}
window.addEventListener('load',function(){setTimeout(init,200)});

function startDownload(){
  var url=document.getElementById('url').value.trim();
  if(!url){return;}
  var fname=document.getElementById('fname').value.trim();
  doDownload(url,fname);
}

function dlSample(url,fname){doDownload(url,fname);}

function doDownload(url,fname){
  var key='dl_'+Date.now();
  downloads[key]={url:url,name:fname||url.split('/').pop().split('?')[0]||'download',progress:0,status:'starting'};
  renderDownloads();
  iappyx.vibration.tick();

  window[key]=function(r){
    if(r.status==='downloading'){
      downloads[key].progress=r.progress;
      downloads[key].status='downloading';
      downloads[key].downloaded=r.downloaded;
      downloads[key].total=r.total;
    }else if(r.status==='complete'){
      downloads[key].progress=100;
      downloads[key].status='complete';
      if(r.filename) downloads[key].name=r.filename;
    }else if(r.status==='failed'){
      downloads[key].status='failed';
      downloads[key].error=r.error;
    }
    renderDownloads();
  };
  iappyx.download.enqueue(url,fname||'','window.'+key);
}

function renderDownloads(){
  var el=document.getElementById('downloads');
  var keys=Object.keys(downloads);
  if(!keys.length){el.innerHTML='<div class="empty">No downloads yet</div>';return;}
  var html='';
  keys.reverse().forEach(function(k){
    var d=downloads[k];
    var statusClass=d.status==='complete'?' dl-done':d.status==='failed'?' dl-fail':'';
    var statusText=d.status==='downloading'?d.progress+'%':d.status==='complete'?'Complete':d.status==='failed'?'Failed: '+(d.error||'unknown'):'Starting...';
    var sizeText='';
    if(d.total) sizeText=formatSize(d.downloaded||0)+' / '+formatSize(d.total);
    html+='<div class="dl-item"><div class="dl-name">'+esc(d.name)+'</div>';
    html+='<div class="dl-bar"><div class="dl-fill" style="width:'+d.progress+'%"></div></div>';
    html+='<div class="dl-meta"><span class="'+statusClass+'">'+statusText+'</span><span>'+sizeText+'</span></div></div>';
  });
  el.innerHTML=html;
}

function formatSize(b){
  if(b<1024) return b+' B';
  if(b<1048576) return (b/1024).toFixed(1)+' KB';
  return (b/1048576).toFixed(1)+' MB';
}

function esc(s){return s?s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'):''}
</script></body></html>""".trimIndent()

    fun getLanShareApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:20px;display:flex;flex-direction:column;gap:12px}
h1{font-size:1.3rem;text-align:center}
.info{background:#1a1a2e;border-radius:12px;padding:14px;font-size:.85rem}
.info .label{color:rgba(255,255,255,.4);font-size:.75rem}
.info .val{font-weight:600;font-variant-numeric:tabular-nums;word-break:break-all}
.peers{background:#1a1a2e;border-radius:12px;padding:14px;flex:0 0 auto}
.peers h3{font-size:.9rem;margin-bottom:8px;color:rgba(255,255,255,.6)}
.peer{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.peer:last-child{border:none}
.dot{width:8px;height:8px;border-radius:50%;background:#69f0ae;flex-shrink:0}
.peer-name{flex:1;font-size:.9rem}
.peer-host{font-size:.75rem;color:rgba(255,255,255,.35)}
.btn{background:#0f3460;border:none;border-radius:10px;padding:10px 14px;color:#fff;font-size:.85rem;cursor:pointer;flex-shrink:0}
.btn:active{opacity:.7}
.btn.sm{padding:6px 12px;font-size:.8rem}
.send-area{background:#1a1a2e;border-radius:12px;padding:14px}
.send-area textarea{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.9rem;resize:none;height:60px;outline:none;font-family:inherit}
.send-btns{display:flex;gap:8px;margin-top:8px}
.msgs{background:#1a1a2e;border-radius:12px;padding:14px;flex:1;overflow-y:auto;min-height:80px}
.msgs h3{font-size:.9rem;margin-bottom:8px;color:rgba(255,255,255,.6)}
.msg{padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.85rem}
.msg:last-child{border:none}
.msg .from{color:#4fc3f7;font-size:.75rem}
.msg img{max-width:100%;border-radius:8px;margin-top:4px}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3)}
</style></head><body>
<h1>📡 $label</h1>
<div class="info">
  <div class="label">Your IP</div>
  <div class="val" id="ip">Starting...</div>
  <div class="label" style="margin-top:6px">Device</div>
  <div class="val" id="devname">—</div>
</div>

<div class="peers">
  <h3>Nearby Devices</h3>
  <div id="peerlist"><div class="status">Searching...</div></div>
</div>

<div class="send-area">
  <textarea id="msgtxt" placeholder="Type a message..."></textarea>
  <div class="send-btns">
    <button class="btn" onclick="sendTextToAll()" style="flex:1">Send Text</button>
    <button class="btn" onclick="sendPhoto()" style="flex:1">📸 Send Photo</button>
  </div>
</div>

<div class="msgs">
  <h3>Messages</h3>
  <div id="msglist"><div class="status">No messages yet</div></div>
</div>

<div class="status" id="log"></div>

<script>
var PORT = 8080;
var SERVICE = '_lanshare._tcp';
var peers = {};
var deviceName = '';
var registeredName = ''; // actual NSD-registered name (may differ from deviceName)
var myIp = '';
var resolveCounter = 0;

function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
  try { deviceName = JSON.parse(iappyx.device.getDeviceInfo()).model || 'Device'; } catch(e) { deviceName = 'Device'; }
  document.getElementById('devname').textContent = deviceName;

  myIp = iappyx.httpServer.getLocalIpAddress() || '?';
  document.getElementById('ip').textContent = myIp + ':' + PORT;

  iappyx.httpServer.onRequest('onHttpRequest');
  iappyx.httpServer.start('' + PORT, 'false', 'onServerStart');
}

window._iappyxCb = window._iappyxCb || {};
window._iappyxCb.onServerStart = function(r) {
  if (!r.ok) { log('Server failed: ' + r.error); return; }
  PORT = r.port; // use actual port (may differ if requested port was in use)
  document.getElementById('ip').textContent = myIp + ':' + PORT;
  log('Server running on port ' + PORT);
  iappyx.nsd.register(SERVICE, deviceName, '' + PORT,
    JSON.stringify({alias: deviceName, ip: myIp}), 'onNsdReg');
  iappyx.nsd.startDiscovery(SERVICE, 'onNsdEvent');
};

window._iappyxCb.onNsdReg = function(r) {
  if (r.ok) {
    registeredName = r.serviceName; // store actual registered name for self-filtering
    log('Registered as: ' + r.serviceName);
  } else {
    log('NSD register failed: ' + r.error);
  }
};

function onNsdEvent(evt) {
  if (evt.event === 'found' && evt.serviceName !== registeredName) {
    var cbId = 'resolve_' + (++resolveCounter);
    window._iappyxCb[cbId] = function(r) {
      if (r.ok) {
        var name = r.txtRecords && r.txtRecords.alias ? r.txtRecords.alias : evt.serviceName;
        peers[evt.serviceName] = {host: r.host, port: r.port, name: name};
        renderPeers();
      }
    };
    iappyx.nsd.resolve(evt.serviceType, evt.serviceName, cbId);
  } else if (evt.event === 'lost') {
    delete peers[evt.serviceName];
    renderPeers();
  }
}

function renderPeers() {
  var el = document.getElementById('peerlist');
  var keys = Object.keys(peers);
  if (keys.length === 0) { el.innerHTML = '<div class="status">Searching...</div>'; return; }
  el.innerHTML = keys.map(function(k) {
    var p = peers[k];
    return '<div class="peer"><div class="dot"></div><div class="peer-name">' + esc(p.name) +
      '<div class="peer-host">' + p.host + ':' + p.port + '</div></div></div>';
  }).join('');
}

function onHttpRequest(req) {
  if (req.method === 'POST' && req.path === '/text') {
    try {
      var data = JSON.parse(req.body);
      addMsg(data.from || 'Unknown', data.text || '');
    } catch(e) {}
    iappyx.httpServer.respond(req.requestId, '200', '{"Content-Type":"application/json"}', '{"ok":true}');
  } else if (req.method === 'POST' && req.path === '/photo') {
    try {
      var data = JSON.parse(req.body);
      if (data.photo) addMsgImg(data.from || 'Unknown', data.photo);
    } catch(e) {}
    iappyx.httpServer.respond(req.requestId, '200', '{"Content-Type":"application/json"}', '{"ok":true}');
  } else {
    iappyx.httpServer.respond(req.requestId, '200', '{"Content-Type":"text/plain"}', 'LAN Share running');
  }
}

function sendTextToAll() {
  var txt = document.getElementById('msgtxt').value.trim();
  if (!txt) return;
  var body = JSON.stringify({from: deviceName, text: txt});
  Object.keys(peers).forEach(function(k) {
    var p = peers[k];
    fetch('http://' + p.host + ':' + p.port + '/text', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: body
    }).catch(function(e) { log('Send failed to ' + p.name); });
  });
  addMsg('You', txt);
  document.getElementById('msgtxt').value = '';
}

function sendPhoto() {
  iappyx.camera.takePhoto('onPhotoTaken');
}

window._iappyxCb.onPhotoTaken = function(r) {
  if (!r || !r.ok || !r.dataUrl) return;
  var b64 = r.dataUrl.replace(/^data:image\/\w+;base64,/, '');
  addMsgImg('You (photo)', b64);
  var body = JSON.stringify({from: deviceName, photo: b64});
  Object.keys(peers).forEach(function(k) {
    var p = peers[k];
    fetch('http://' + p.host + ':' + p.port + '/photo', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: body
    }).catch(function(e) { log('Photo send failed to ' + p.name); });
  });
};

function addMsg(from, text) {
  var el = document.getElementById('msglist');
  if (el.querySelector('.status')) el.innerHTML = '';
  el.innerHTML = '<div class="msg"><div class="from">' + esc(from) + '</div>' + esc(text) + '</div>' + el.innerHTML;
}

function addMsgImg(from, b64) {
  var el = document.getElementById('msglist');
  if (el.querySelector('.status')) el.innerHTML = '';
  el.innerHTML = '<div class="msg"><div class="from">' + esc(from) + '</div><img src="data:image/jpeg;base64,' + b64 + '"></div>' + el.innerHTML;
}

function log(msg) { document.getElementById('log').textContent = msg; }
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getWifiDirectApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:20px;display:flex;flex-direction:column;gap:12px}
h1{font-size:1.3rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:14px}
.card h3{font-size:.9rem;margin-bottom:8px;color:rgba(255,255,255,.6)}
.label{color:rgba(255,255,255,.4);font-size:.75rem}
.val{font-weight:600;word-break:break-all}
.peer{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06)}
.peer:last-child{border:none}
.dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.dot.available{background:#69f0ae}.dot.connected{background:#4fc3f7}.dot.invited{background:#ffb74d}.dot.unavailable{background:#555}
.peer-name{flex:1;font-size:.9rem}
.peer-status{font-size:.7rem;color:rgba(255,255,255,.35)}
.btn{background:#0f3460;border:none;border-radius:10px;padding:10px 14px;color:#fff;font-size:.85rem;cursor:pointer}
.btn:active{opacity:.7}
.btn.danger{background:#c62828}
.btns{display:flex;gap:8px;flex-wrap:wrap}
textarea{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.9rem;resize:none;height:50px;outline:none;font-family:inherit;margin-bottom:8px}
.msgs{max-height:150px;overflow-y:auto}
.msg{padding:6px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.85rem}
.msg:last-child{border:none}
.msg .from{color:#4fc3f7;font-size:.75rem}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3);min-height:1.2em}
</style></head><body>
<h1>📶 $label</h1>

<div class="card">
  <div class="label">Status</div>
  <div class="val" id="connStatus">Disconnected</div>
  <div class="label" style="margin-top:6px">Group Owner IP</div>
  <div class="val" id="goIp">—</div>
</div>

<div class="btns">
  <button class="btn" onclick="discover()" id="btnDiscover">🔍 Discover</button>
  <button class="btn danger" onclick="doDisconnect()">✕ Disconnect</button>
</div>

<div class="card">
  <h3>Peers</h3>
  <div id="peerlist"><div class="status">Tap Discover to scan</div></div>
</div>

<div class="card" id="msgArea" style="display:none">
  <h3>Messages</h3>
  <textarea id="msgtxt" placeholder="Type a message..."></textarea>
  <button class="btn" onclick="sendMsg()" style="width:100%">Send</button>
  <div class="msgs" id="msglist"></div>
</div>

<div class="status" id="log"></div>

<script>
var SERVER_PORT = 9090;
var connected = false, isGroupOwner = false, goAddress = '';
var serverRunning = false;

function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
  iappyx.wifiDirect.onConnectionChanged('onConnChange');
  iappyx.httpServer.onRequest('onHttpReq');
  log('Ready — tap Discover to find peers');
}

function discover() {
  iappyx.wifiDirect.discoverPeers('onPeerEvent');
  log('Discovering...');
}

function onPeerEvent(evt) {
  if (evt.event === 'error') { log('Discovery error: ' + evt.error); return; }
  if (evt.event !== 'peers') return;
  var el = document.getElementById('peerlist');
  if (!evt.peers || evt.peers.length === 0) { el.innerHTML = '<div class="status">No peers found</div>'; return; }
  el.innerHTML = evt.peers.map(function(p) {
    return '<div class="peer" onclick="connectTo(\'' + p.address + '\')">' +
      '<div class="dot ' + p.status + '"></div>' +
      '<div class="peer-name">' + esc(p.name) +
      '<div class="peer-status">' + p.status + ' — ' + p.address + '</div></div></div>';
  }).join('');
}

function connectTo(addr) {
  log('Connecting to ' + addr + '...');
  iappyx.wifiDirect.connect(addr, 'onConnect');
}
window._iappyxCb = window._iappyxCb || {};
window._iappyxCb.onConnect = function(r) {
  if (!r.ok) log('Connect failed: ' + r.error);
  else log('Connect initiated — waiting for group...');
};

var peerAddress = ''; // learned from incoming requests

function onConnChange(evt) {
  connected = evt.connected;
  if (connected) {
    isGroupOwner = evt.isGroupOwner;
    goAddress = evt.groupOwnerAddress;
    document.getElementById('connStatus').textContent = 'Connected (' + (isGroupOwner ? 'Group Owner' : 'Client') + ')';
    document.getElementById('goIp').textContent = goAddress;
    document.getElementById('msgArea').style.display = '';
    log('Connected! ' + (isGroupOwner ? 'You are the group owner.' : 'Group owner: ' + goAddress));
    // Both sides start HTTP server
    if (!serverRunning) {
      iappyx.httpServer.start('' + SERVER_PORT, 'false', 'onSrvStart');
    }
  } else {
    document.getElementById('connStatus').textContent = 'Disconnected';
    document.getElementById('goIp').textContent = '—';
    document.getElementById('msgArea').style.display = 'none';
    if (serverRunning) { iappyx.httpServer.stop(); serverRunning = false; }
    peerAddress = '';
    log('Disconnected');
  }
}

window._iappyxCb.onSrvStart = function(r) {
  if (r.ok) { serverRunning = true; log('Server running on port ' + r.port); }
  else log('Server failed: ' + r.error);
};

function onHttpReq(req) {
  if (req.method === 'POST' && req.path === '/msg') {
    try {
      var data = JSON.parse(req.body);
      addMsg(data.from || 'Peer', data.text || '');
      // Learn peer's IP from the request headers
      if (data.replyTo) peerAddress = data.replyTo;
    } catch(e) {}
    iappyx.httpServer.respond(req.requestId, '200', '{"Content-Type":"application/json"}', '{"ok":true}');
  } else {
    iappyx.httpServer.respond(req.requestId, '200', '{"Content-Type":"text/plain"}', 'WiFi Direct P2P');
  }
}

function sendMsg() {
  var txt = document.getElementById('msgtxt').value.trim();
  if (!txt || !connected) return;
  var myIp = iappyx.httpServer.getLocalIpAddress() || '';
  var target = isGroupOwner ? peerAddress : goAddress;
  if (!target) { log('Waiting for peer to send first message...'); addMsg('You', txt); return; }
  fetch('http://' + target + ':' + SERVER_PORT + '/msg', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({from: isGroupOwner ? 'Owner' : 'Client', text: txt, replyTo: myIp})
  }).then(function() { addMsg('You', txt); })
  .catch(function(e) { log('Send failed: ' + e.message); });
  document.getElementById('msgtxt').value = '';
}

function doDisconnect() {
  if (serverRunning) { iappyx.httpServer.stop(); serverRunning = false; }
  iappyx.wifiDirect.disconnect();
}

function addMsg(from, text) {
  var el = document.getElementById('msglist');
  el.innerHTML = '<div class="msg"><div class="from">' + esc(from) + '</div>' + esc(text) + '</div>' + el.innerHTML;
}
function log(msg) { document.getElementById('log').textContent = msg; }
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getHttpClientApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:20px;display:flex;flex-direction:column;gap:12px}
h1{font-size:1.3rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:14px}
.card h3{font-size:.9rem;margin-bottom:8px;color:rgba(255,255,255,.6)}
input,textarea{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.9rem;outline:none;font-family:inherit}
textarea{height:60px;resize:none}
.btn{background:#0f3460;border:none;border-radius:10px;padding:10px 14px;color:#fff;font-size:.85rem;cursor:pointer;width:100%}
.btn:active{opacity:.7}
.row{display:flex;gap:8px;margin-top:8px}
.row .btn{flex:1}
label{font-size:.75rem;color:rgba(255,255,255,.4);margin-bottom:4px;display:block}
.toggle{display:flex;align-items:center;gap:8px;font-size:.85rem;padding:8px 0}
.toggle input{width:auto}
.result{background:#0d0d1a;border-radius:8px;padding:10px;font-size:.8rem;font-family:monospace;white-space:pre-wrap;word-break:break-all;max-height:200px;overflow-y:auto;color:#69f0ae}
.result.error{color:#ff6b6b}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3)}
</style></head><body>
<h1>🌐 $label</h1>

<div class="card">
  <h3>Request</h3>
  <label>URL</label>
  <input id="url" value="https://httpbin.org/get" placeholder="https://...">
  <div style="margin-top:8px">
    <label>Method</label>
    <div class="row" style="margin-top:0">
      <button class="btn" onclick="setMethod('GET')" id="mGET" style="background:#0f3460">GET</button>
      <button class="btn" onclick="setMethod('POST')" id="mPOST" style="background:#1a1a2e">POST</button>
      <button class="btn" onclick="setMethod('PUT')" id="mPUT" style="background:#1a1a2e">PUT</button>
      <button class="btn" onclick="setMethod('DELETE')" id="mDELETE" style="background:#1a1a2e">DELETE</button>
    </div>
  </div>
  <div style="margin-top:8px">
    <label>Body (for POST/PUT)</label>
    <textarea id="body" placeholder='{"key":"value"}'></textarea>
  </div>
  <div class="toggle">
    <input type="checkbox" id="trustAll"> <span>Trust all certificates (self-signed)</span>
  </div>
  <button class="btn" onclick="doRequest()" style="margin-top:8px">🚀 Send Request</button>
</div>

<div class="card">
  <h3>TLS Test — Self-Signed Server</h3>
  <p style="font-size:.8rem;color:rgba(255,255,255,.4);margin-bottom:8px">Start HTTPS server on this device, then request it from another device with "Trust all certs" enabled.</p>
  <div class="row">
    <button class="btn" onclick="startTlsServer()">Start HTTPS Server</button>
    <button class="btn" onclick="stopTlsServer()" style="background:#1a1a2e">Stop</button>
  </div>
  <div class="status" id="srvStatus"></div>
</div>

<div class="card">
  <h3>Response</h3>
  <div class="status" id="respStatus"></div>
  <div class="result" id="result">No request sent yet</div>
</div>

<script>
var method = 'GET';

function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
}

function setMethod(m) {
  method = m;
  ['GET','POST','PUT','DELETE'].forEach(function(x) {
    document.getElementById('m'+x).style.background = x === m ? '#0f3460' : '#1a1a2e';
  });
}

function doRequest() {
  var url = document.getElementById('url').value.trim();
  if (!url) return;
  var opts = {url: url, method: method, headers: {'Content-Type': 'application/json'}};
  var body = document.getElementById('body').value.trim();
  if (body && (method === 'POST' || method === 'PUT')) opts.body = body;
  if (document.getElementById('trustAll').checked) opts.trustAllCerts = true;
  opts.timeout = 10000;
  document.getElementById('respStatus').textContent = 'Sending...';
  document.getElementById('result').textContent = '';
  document.getElementById('result').className = 'result';
  var cbId = 'req_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      document.getElementById('respStatus').textContent = 'Status: ' + r.status;
      var bodyText = r.body;
      try { bodyText = JSON.stringify(JSON.parse(r.body), null, 2); } catch(e) {}
      document.getElementById('result').textContent = bodyText;
      document.getElementById('result').className = 'result';
    } else {
      document.getElementById('respStatus').textContent = 'Error';
      document.getElementById('result').textContent = r.error;
      document.getElementById('result').className = 'result error';
    }
  };
  iappyx.httpClient.request(JSON.stringify(opts), cbId);
}

var srvRunning = false;
function startTlsServer() {
  iappyx.httpServer.onRequest('onTlsReq');
  var cbId = 'srv_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      srvRunning = true;
      var ip = iappyx.httpServer.getLocalIpAddress() || '?';
      document.getElementById('srvStatus').textContent = 'HTTPS server running at https://' + ip + ':' + r.port + '  Fingerprint: ' + (r.fingerprint || 'N/A');
    } else {
      document.getElementById('srvStatus').textContent = 'Failed: ' + r.error;
    }
  };
  iappyx.httpServer.start('8443', 'true', cbId);
}

function onTlsReq(req) {
  iappyx.httpServer.respond(req.requestId, '200', '{"Content-Type":"application/json"}',
    JSON.stringify({message: 'Hello from self-signed HTTPS server!', method: req.method, path: req.path}));
}

function stopTlsServer() {
  iappyx.httpServer.stop();
  srvRunning = false;
  document.getElementById('srvStatus').textContent = 'Stopped';
}

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getUdpChatApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:20px;display:flex;flex-direction:column;gap:12px}
h1{font-size:1.3rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:14px}
.card h3{font-size:.9rem;margin-bottom:8px;color:rgba(255,255,255,.6)}
input{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.9rem;outline:none;font-family:inherit}
.btn{background:#0f3460;border:none;border-radius:10px;padding:10px 14px;color:#fff;font-size:.85rem;cursor:pointer}
.btn:active{opacity:.7}
.row{display:flex;gap:8px;align-items:center}
label{font-size:.75rem;color:rgba(255,255,255,.4);margin-bottom:4px;display:block}
.info .val{font-weight:600;word-break:break-all;font-size:.9rem}
.msgs{flex:1;overflow-y:auto;min-height:100px;max-height:250px}
.msg{padding:6px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.85rem}
.msg:last-child{border:none}
.msg .from{color:#4fc3f7;font-size:.75rem}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3)}
.mode-btns{display:flex;gap:8px;margin-bottom:8px}
.mode-btns .btn{flex:1;font-size:.8rem}
</style></head><body>
<h1>📨 $label</h1>

<div class="card info">
  <div class="row" style="justify-content:space-between">
    <div><label>Your IP</label><div class="val" id="myIp">—</div></div>
    <div><label>UDP Port</label><div class="val" id="myPort">—</div></div>
  </div>
</div>

<div class="card">
  <h3>Mode</h3>
  <div class="mode-btns">
    <button class="btn" id="mUnicast" onclick="setMode('unicast')" style="background:#0f3460">Unicast</button>
    <button class="btn" id="mMulticast" onclick="setMode('multicast')" style="background:#1a1a2e">Multicast</button>
  </div>
  <div id="unicastOpts">
    <label>Peer IP</label>
    <input id="peerIp" placeholder="192.168.1.x">
    <label style="margin-top:8px">Peer Port</label>
    <input id="peerPort" value="9999" placeholder="9999">
  </div>
  <div id="multicastOpts" style="display:none">
    <label>Multicast Group</label>
    <input id="mcastGroup" value="239.1.2.3" placeholder="239.x.x.x">
    <div class="row" style="margin-top:8px">
      <button class="btn" onclick="joinGroup()" style="flex:1">Join Group</button>
      <button class="btn" onclick="leaveGroup()" style="flex:1;background:#1a1a2e">Leave</button>
    </div>
    <div class="status" id="mcastStatus"></div>
  </div>
</div>

<div class="card">
  <h3>Send</h3>
  <div class="row">
    <input id="msgtxt" placeholder="Type a message..." style="flex:1">
    <button class="btn" onclick="sendMsg()">Send</button>
  </div>
</div>

<div class="card">
  <h3>Messages</h3>
  <div class="msgs" id="msglist"><div class="status">No messages yet</div></div>
</div>

<script>
var PORT = 9999;
var mode = 'unicast';
var mcastJoined = false;
var deviceName = '';

function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
  try { deviceName = JSON.parse(iappyx.device.getDeviceInfo()).model || 'Device'; } catch(e) { deviceName = 'Device'; }

  var ip = iappyx.httpServer.getLocalIpAddress() || '?';
  document.getElementById('myIp').textContent = ip;

  iappyx.udp.onReceive('onUdpMsg');
  var cbId = 'udp_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      document.getElementById('myPort').textContent = r.port;
    } else {
      document.getElementById('myPort').textContent = 'Error: ' + r.error;
    }
  };
  iappyx.udp.open('' + PORT, cbId);
}

function setMode(m) {
  mode = m;
  document.getElementById('mUnicast').style.background = m === 'unicast' ? '#0f3460' : '#1a1a2e';
  document.getElementById('mMulticast').style.background = m === 'multicast' ? '#0f3460' : '#1a1a2e';
  document.getElementById('unicastOpts').style.display = m === 'unicast' ? '' : 'none';
  document.getElementById('multicastOpts').style.display = m === 'multicast' ? '' : 'none';
}

function joinGroup() {
  var group = document.getElementById('mcastGroup').value.trim();
  if (!group) return;
  iappyx.udp.joinMulticast(group);
  mcastJoined = true;
  document.getElementById('mcastStatus').textContent = 'Joined ' + group;
}

function leaveGroup() {
  var group = document.getElementById('mcastGroup').value.trim();
  if (!group) return;
  iappyx.udp.leaveMulticast(group);
  mcastJoined = false;
  document.getElementById('mcastStatus').textContent = 'Left group';
}

function onUdpMsg(evt) {
  try {
    var data = JSON.parse(evt.data);
    addMsg(data.from || evt.from, data.text || evt.data);
  } catch(e) {
    addMsg(evt.from + ':' + evt.port, evt.data);
  }
}

function sendMsg() {
  var txt = document.getElementById('msgtxt').value.trim();
  if (!txt) return;
  var payload = JSON.stringify({from: deviceName, text: txt});
  if (mode === 'unicast') {
    var ip = document.getElementById('peerIp').value.trim();
    var port = document.getElementById('peerPort').value.trim() || '9999';
    if (!ip) return;
    iappyx.udp.send(ip, port, payload);
  } else {
    var group = document.getElementById('mcastGroup').value.trim();
    iappyx.udp.send(group, '' + PORT, payload);
  }
  addMsg('You', txt);
  document.getElementById('msgtxt').value = '';
}

function addMsg(from, text) {
  var el = document.getElementById('msglist');
  if (el.querySelector('.status')) el.innerHTML = '';
  el.innerHTML = '<div class="msg"><div class="from">' + esc(from) + '</div>' + esc(text) + '</div>' + el.innerHTML;
}
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getTcpSocketApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:20px;display:flex;flex-direction:column;gap:12px}
h1{font-size:1.3rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:14px}
.card h3{font-size:.9rem;margin-bottom:8px;color:rgba(255,255,255,.6)}
input{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:10px;color:#fff;font-size:.9rem;outline:none;font-family:inherit}
.btn{background:#0f3460;border:none;border-radius:10px;padding:10px 14px;color:#fff;font-size:.85rem;cursor:pointer}
.btn:active{opacity:.7}
.btn.danger{background:#c62828}
.row{display:flex;gap:8px;align-items:center}
label{font-size:.75rem;color:rgba(255,255,255,.4);margin-bottom:4px;display:block}
.toggle{display:flex;align-items:center;gap:8px;font-size:.85rem;padding:4px 0}
.toggle input{width:auto}
.log{background:#0d0d1a;border-radius:8px;padding:10px;font-size:.8rem;font-family:monospace;white-space:pre-wrap;word-break:break-all;max-height:250px;overflow-y:auto;color:#69f0ae;min-height:60px}
.log .rx{color:#4fc3f7}.log .err{color:#ff6b6b}.log .sys{color:rgba(255,255,255,.3)}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3)}
.dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px}
.dot.on{background:#69f0ae}.dot.off{background:#555}
</style></head><body>
<h1>🔌 $label</h1>

<div class="card">
  <h3>Connect</h3>
  <div class="row">
    <div style="flex:2"><label>Host</label><input id="host" placeholder="192.168.1.x"></div>
    <div style="flex:1"><label>Port</label><input id="port" value="8080"></div>
  </div>
  <div class="toggle">
    <input type="checkbox" id="tls"> <span>Use TLS</span>
  </div>
  <div class="row" style="margin-top:8px">
    <button class="btn" onclick="doConnect()" style="flex:1">Connect</button>
    <button class="btn danger" onclick="doClose()" style="flex:1">Disconnect</button>
  </div>
  <div style="margin-top:8px"><span class="dot" id="dot"></span><span id="connLabel">Disconnected</span></div>
</div>

<div class="card">
  <h3>Send</h3>
  <div class="row">
    <input id="msg" placeholder="Type data to send..." style="flex:1">
    <button class="btn" onclick="doSend()">Send</button>
  </div>
  <div class="toggle">
    <input type="checkbox" id="hexMode"> <span>Hex mode</span>
  </div>
</div>

<div class="card">
  <h3>Log</h3>
  <div class="log" id="log"><span class="sys">Ready to connect...</span></div>
</div>

<script>
function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
  iappyx.tcp.onData('onTcpData');
  iappyx.tcp.onClose('onTcpClose');
}

function doConnect() {
  var host = document.getElementById('host').value.trim();
  var port = document.getElementById('port').value.trim();
  var tls = document.getElementById('tls').checked;
  if (!host || !port) return;
  addLog('sys', 'Connecting to ' + host + ':' + port + (tls ? ' (TLS)' : '') + '...');
  var cbId = 'tcp_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      addLog('sys', 'Connected!');
      setStatus(true);
    } else {
      addLog('err', 'Connect failed: ' + r.error);
      setStatus(false);
    }
  };
  iappyx.tcp.open(host, port, tls ? 'true' : 'false', cbId);
}

function doSend() {
  var txt = document.getElementById('msg').value;
  if (!txt) return;
  if (document.getElementById('hexMode').checked) {
    iappyx.tcp.sendHex(txt);
    addLog('sys', 'TX (hex): ' + txt);
  } else {
    iappyx.tcp.send(txt);
    addLog('sys', 'TX: ' + txt);
  }
  document.getElementById('msg').value = '';
}

function onTcpData(evt) {
  addLog('rx', 'RX [' + evt.length + 'b]: ' + evt.data);
}

function onTcpClose() {
  addLog('err', 'Connection closed');
  setStatus(false);
}

function doClose() {
  iappyx.tcp.close();
  addLog('sys', 'Disconnected');
  setStatus(false);
}

function setStatus(on) {
  document.getElementById('dot').className = 'dot ' + (on ? 'on' : 'off');
  document.getElementById('connLabel').textContent = on ? 'Connected' : 'Disconnected';
}

function addLog(cls, text) {
  var el = document.getElementById('log');
  el.innerHTML += '\n<span class="' + cls + '">' + esc(text) + '</span>';
  el.scrollTop = el.scrollHeight;
}
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getSshClientApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px;display:flex;flex-direction:column;gap:10px}
h1{font-size:1.2rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:12px}
.card h3{font-size:.85rem;margin-bottom:6px;color:rgba(255,255,255,.6)}
input{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:8px 10px;color:#fff;font-size:.85rem;outline:none;font-family:inherit}
.btn{background:#0f3460;border:none;border-radius:10px;padding:8px 12px;color:#fff;font-size:.8rem;cursor:pointer}
.btn:active{opacity:.7}
.btn.danger{background:#c62828}
.btn.sm{padding:6px 10px;font-size:.75rem}
.row{display:flex;gap:6px;align-items:center}
label{font-size:.7rem;color:rgba(255,255,255,.4);margin-bottom:2px;display:block}
.dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px}
.dot.on{background:#69f0ae}.dot.off{background:#555}
.term{background:#000;border-radius:8px;padding:8px;font-family:'Courier New',monospace;font-size:.75rem;color:#69f0ae;white-space:pre-wrap;word-break:break-all;overflow-y:auto;flex:1;min-height:120px;max-height:300px;line-height:1.3}
.input-row{display:flex;gap:6px}
.input-row input{flex:1;background:#000;color:#69f0ae;font-family:'Courier New',monospace;font-size:.8rem;border-radius:6px}
.tabs{display:flex;gap:4px;margin-bottom:6px}
.tab{padding:6px 12px;border-radius:8px 8px 0 0;background:#0d0d1a;color:rgba(255,255,255,.4);font-size:.8rem;cursor:pointer;border:none}
.tab.active{background:#1a1a2e;color:#4fc3f7}
.panel{display:none}.panel.active{display:flex;flex-direction:column;gap:8px;flex:1}
.files{max-height:200px;overflow-y:auto}
.file{display:flex;align-items:center;gap:8px;padding:6px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:.8rem}
.file:last-child{border:none}
.file .icon{font-size:1rem}
.file .name{flex:1;word-break:break-all}
.file .size{color:rgba(255,255,255,.3);font-size:.7rem}
</style></head><body>
<h1>🖥️ $label</h1>

<div class="card">
  <h3>Connect</h3>
  <div class="row">
    <div style="flex:2"><label>Host</label><input id="host" placeholder="192.168.1.x"></div>
    <div style="flex:1"><label>Port</label><input id="port" value="22"></div>
  </div>
  <div class="row" style="margin-top:4px">
    <div style="flex:1"><label>User</label><input id="user" placeholder="root"></div>
    <div style="flex:1"><label>Password</label><input id="pass" type="password" placeholder="••••"></div>
  </div>
  <div class="row" style="margin-top:6px">
    <button class="btn" onclick="doConnect()" style="flex:1">Connect</button>
    <button class="btn danger" onclick="doDisconnect()" style="flex:1">Disconnect</button>
  </div>
  <div style="margin-top:4px;font-size:.8rem"><span class="dot off" id="dot"></span><span id="connLabel">Disconnected</span></div>
</div>

<div class="tabs">
  <button class="tab active" onclick="showTab('terminal')">Terminal</button>
  <button class="tab" onclick="showTab('exec')">Execute</button>
  <button class="tab" onclick="showTab('sftp')">SFTP</button>
</div>

<div class="panel active" id="p_terminal" style="flex:1">
  <div class="term" id="term"></div>
  <div class="input-row">
    <input id="cmd" placeholder="Type command..." onkeydown="if(event.key==='Enter')sendCmd()">
    <button class="btn sm" onclick="sendCmd()">↵</button>
  </div>
</div>

<div class="panel" id="p_exec">
  <div class="row">
    <input id="execCmd" placeholder="ls -la /tmp" style="flex:1">
    <button class="btn sm" onclick="doExec()">Run</button>
  </div>
  <div class="term" id="execOut" style="min-height:80px;max-height:250px">Output will appear here</div>
</div>

<div class="panel" id="p_sftp">
  <div class="row">
    <input id="sftpPath" value="/" style="flex:1">
    <button class="btn sm" onclick="listDir()">List</button>
  </div>
  <div class="files" id="fileList"><div style="color:rgba(255,255,255,.3);font-size:.8rem;text-align:center;padding:16px">Connect and tap List</div></div>
  <div class="row">
    <button class="btn sm" onclick="uploadFile()" style="flex:1">⬆ Upload File</button>
    <button class="btn sm" onclick="downloadFile()" style="flex:1">⬇ Download</button>
  </div>
  <input id="remoteDl" placeholder="Remote path to download" style="margin-top:4px;font-size:.75rem">
</div>

<script>
var connected = false;

function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
  iappyx.ssh.onData('onSshData');
  iappyx.ssh.onClose('onSshClose');
}

function doConnect() {
  var opts = {
    host: document.getElementById('host').value.trim(),
    port: parseInt(document.getElementById('port').value) || 22,
    user: document.getElementById('user').value.trim(),
    password: document.getElementById('pass').value
  };
  if (!opts.host || !opts.user) return;
  document.getElementById('term').textContent = 'Connecting...\n';
  var cbId = 'ssh_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      connected = true;
      setStatus(true);
      document.getElementById('term').textContent += 'Connected! Fingerprint: ' + r.fingerprint + '\n';
      // Start interactive shell
      var shellCb = 'shell_' + Date.now();
      window._iappyxCb[shellCb] = function(r2) {
        if (!r2.ok) document.getElementById('term').textContent += 'Shell error: ' + r2.error + '\n';
      };
      iappyx.ssh.shell(shellCb);
    } else {
      document.getElementById('term').textContent += 'Failed: ' + r.error + '\n';
      setStatus(false);
    }
  };
  iappyx.ssh.connect(JSON.stringify(opts), cbId);
}

function onSshData(evt) {
  var el = document.getElementById('term');
  el.textContent += evt.data;
  el.scrollTop = el.scrollHeight;
}

function onSshClose() {
  document.getElementById('term').textContent += '\n[Connection closed]\n';
  connected = false;
  setStatus(false);
}

function sendCmd() {
  var cmd = document.getElementById('cmd').value;
  iappyx.ssh.send(cmd + '\n');
  document.getElementById('cmd').value = '';
}

function doExec() {
  var cmd = document.getElementById('execCmd').value.trim();
  if (!cmd) return;
  document.getElementById('execOut').textContent = 'Running...\n';
  var cbId = 'exec_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    var el = document.getElementById('execOut');
    if (r.ok) {
      el.textContent = r.stdout || '';
      if (r.stderr) el.textContent += '\n--- STDERR ---\n' + r.stderr;
      el.textContent += '\n[exit ' + r.exitCode + ']';
    } else {
      el.textContent = 'Error: ' + r.error;
    }
  };
  iappyx.ssh.exec(cmd, cbId);
}

function listDir() {
  var path = document.getElementById('sftpPath').value.trim() || '/';
  var cbId = 'ls_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    var el = document.getElementById('fileList');
    if (!r.ok) { el.innerHTML = '<div style="color:#ff6b6b;padding:8px">' + esc(r.error) + '</div>'; return; }
    if (!r.files || r.files.length === 0) { el.innerHTML = '<div style="color:rgba(255,255,255,.3);padding:8px">Empty</div>'; return; }
    el.innerHTML = r.files.filter(function(f) { return f.name !== '.' && f.name !== '..'; }).map(function(f) {
      var icon = f.isDir ? '📁' : '📄';
      var sz = f.isDir ? '' : formatSize(f.size);
      var click = f.isDir ? ' onclick="navDir(\'' + esc(path + (path.endsWith('/') ? '' : '/') + f.name) + '\')"' : '';
      return '<div class="file"' + click + '><span class="icon">' + icon + '</span><span class="name">' + esc(f.name) + '</span><span class="size">' + sz + '</span></div>';
    }).join('');
  };
  iappyx.ssh.listDir(path, cbId);
}

function navDir(path) {
  document.getElementById('sftpPath').value = path;
  listDir();
}

function uploadFile() {
  var cbId = 'pick_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (!r.ok) return;
    var remote = document.getElementById('sftpPath').value.trim() || '/';
    if (!remote.endsWith('/')) remote += '/';
    remote += r.name;
    var ulCb = 'ul_' + Date.now();
    window._iappyxCb[ulCb] = function(r2) {
      if (r2.ok) { listDir(); } else { alert('Upload failed: ' + r2.error); }
    };
    iappyx.ssh.upload(r.filePath, remote, ulCb);
  };
  iappyx.storage.pickFile(cbId);
}

function downloadFile() {
  var remote = document.getElementById('remoteDl').value.trim();
  if (!remote) return;
  var name = remote.split('/').pop();
  var cbId = 'dl_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      iappyx.storage.copyFileToDownloads(r.filePath, name, 'application/octet-stream');
      alert('Downloaded to Downloads: ' + name);
    } else { alert('Download failed: ' + r.error); }
  };
  iappyx.ssh.download(remote, 'ssh_dl_' + Date.now(), cbId);
}

function doDisconnect() {
  iappyx.ssh.disconnect();
  connected = false;
  setStatus(false);
}

function setStatus(on) {
  document.getElementById('dot').className = 'dot ' + (on ? 'on' : 'off');
  document.getElementById('connLabel').textContent = on ? 'Connected' : 'Disconnected';
}

function showTab(name) {
  document.querySelectorAll('.tab').forEach(function(t) { t.className = 'tab'; });
  document.querySelectorAll('.panel').forEach(function(p) { p.className = 'panel'; });
  event.target.className = 'tab active';
  document.getElementById('p_' + name).className = 'panel active';
}

function formatSize(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b/1024).toFixed(1) + ' KB';
  return (b/1048576).toFixed(1) + ' MB';
}
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/'/g,'&#39;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getNetworkFilesApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px;display:flex;flex-direction:column;gap:10px}
h1{font-size:1.2rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:12px}
.card h3{font-size:.85rem;margin-bottom:6px;color:rgba(255,255,255,.6)}
input{width:100%;background:#0d0d1a;border:none;border-radius:8px;padding:8px 10px;color:#fff;font-size:.85rem;outline:none;font-family:inherit}
.btn{background:#0f3460;border:none;border-radius:10px;padding:8px 12px;color:#fff;font-size:.8rem;cursor:pointer}
.btn:active{opacity:.7}
.btn.danger{background:#c62828}
.btn.sm{padding:6px 10px;font-size:.75rem}
.row{display:flex;gap:6px;align-items:center}
label{font-size:.7rem;color:rgba(255,255,255,.4);margin-bottom:2px;display:block}
.dot{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px}
.dot.on{background:#69f0ae}.dot.off{background:#555}
.path-bar{background:#0d0d1a;border-radius:8px;padding:8px 10px;font-size:.8rem;color:#4fc3f7;word-break:break-all;margin-bottom:6px}
.files{flex:1;overflow-y:auto;min-height:150px;max-height:400px}
.file{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06);cursor:pointer}
.file:last-child{border:none}
.file:active{opacity:.7}
.file .icon{font-size:1.2rem;flex-shrink:0}
.file .info{flex:1;min-width:0}
.file .name{font-size:.85rem;word-break:break-all}
.file .meta{font-size:.7rem;color:rgba(255,255,255,.3)}
.actions{display:flex;gap:6px;margin-top:8px}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3);padding:16px}
</style></head><body>
<h1>📂 $label</h1>

<div class="card">
  <h3>Server</h3>
  <div class="row">
    <div style="flex:2"><label>Host</label><input id="host" placeholder="192.168.1.x"></div>
  </div>
  <div class="row" style="margin-top:4px">
    <div style="flex:1"><label>User</label><input id="user" placeholder="guest"></div>
    <div style="flex:1"><label>Password</label><input id="pass" type="password" placeholder="••••"></div>
  </div>
  <div class="row" style="margin-top:6px">
    <button class="btn" onclick="discoverShares()" style="flex:1">🔍 Discover Shares</button>
    <button class="btn danger" onclick="doDisconnect()" style="flex:1">Disconnect</button>
  </div>
  <div id="shareList"></div>
  <div style="margin-top:4px;font-size:.8rem"><span class="dot off" id="dot"></span><span id="connLabel">Disconnected</span></div>
</div>

<div class="card" style="flex:1;display:flex;flex-direction:column">
  <div class="path-bar" id="pathBar">/</div>
  <div class="files" id="fileList"><div class="status">Discover shares to browse</div></div>
  <div class="actions">
    <button class="btn sm" onclick="goUp()">⬆ Up</button>
    <button class="btn sm" onclick="uploadFile()">⬆ Upload</button>
    <button class="btn sm" onclick="createFolder()">📁 New Folder</button>
  </div>
</div>

<div id="actionSheet" style="display:none;position:fixed;bottom:0;left:0;right:0;background:#1a1a2e;border-radius:16px 16px 0 0;padding:16px;z-index:10">
  <div style="font-size:.9rem;font-weight:600;margin-bottom:12px" id="actionTitle">File</div>
  <div id="actionInfo" style="font-size:.75rem;color:rgba(255,255,255,.4);margin-bottom:12px"></div>
  <button class="btn sm" onclick="actionDownload()" style="width:100%;margin-bottom:6px">⬇ Download</button>
  <button class="btn sm" onclick="actionRename()" style="width:100%;margin-bottom:6px">✏️ Rename</button>
  <button class="btn sm" onclick="actionDelete()" style="width:100%;margin-bottom:6px;background:#c62828">🗑 Delete</button>
  <button class="btn sm" onclick="closeAction()" style="width:100%;background:#0d0d1a">Cancel</button>
</div>

<script>
var currentPath = '/';
var connected = false;
var selectedFile = null;

function init() { if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; } }

function getAuth() {
  return { user: document.getElementById('user').value.trim() || 'guest', password: document.getElementById('pass').value };
}

function discoverShares() {
  var host = document.getElementById('host').value.trim();
  if (!host) return;
  var auth = getAuth();
  var cbId = 'shares_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    var el = document.getElementById('shareList');
    if (!r.ok) { el.innerHTML = '<div style="color:#ff6b6b;font-size:.8rem;padding:6px">' + esc(r.error) + '</div>'; return; }
    if (!r.shares || r.shares.length === 0) { el.innerHTML = '<div style="color:rgba(255,255,255,.3);font-size:.8rem;padding:6px">No shares found</div>'; return; }
    el.innerHTML = r.shares.map(function(s) {
      return '<div class="file" onclick="connectToShare(\'' + esc(s) + '\')"><div class="icon">📁</div><div class="info"><div class="name">' + esc(s) + '</div></div></div>';
    }).join('');
  };
  iappyx.smb.listShares(host, JSON.stringify(auth), cbId);
}

function connectToShare(share) {
  var host = document.getElementById('host').value.trim();
  var auth = getAuth();
  var opts = { host: host, share: share,
    user: document.getElementById('user').value.trim() || 'guest',
    password: document.getElementById('pass').value
  };
  var cbId = 'smb_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) { connected = true; setStatus(true); document.getElementById('shareList').innerHTML = ''; currentPath = '/'; listDir(); }
    else { setStatus(false); alert('Connect failed: ' + r.error); }
  };
  iappyx.smb.connect(JSON.stringify(opts), cbId);
}

function listDir() {
  document.getElementById('pathBar').textContent = currentPath;
  document.getElementById('fileList').innerHTML = '<div class="status">Loading...</div>';
  var cbId = 'ls_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    var el = document.getElementById('fileList');
    if (!r.ok) { el.innerHTML = '<div class="status" style="color:#ff6b6b">' + esc(r.error) + '</div>'; return; }
    if (!r.files || r.files.length === 0) { el.innerHTML = '<div class="status">Empty folder</div>'; return; }
    var sorted = r.files.sort(function(a,b) { return (b.isDir?1:0)-(a.isDir?1:0) || a.name.localeCompare(b.name); });
    el.innerHTML = sorted.map(function(f) {
      var icon = f.isDir ? '📁' : fileIcon(f.name);
      var sz = f.isDir ? '' : formatSize(f.size);
      var date = f.modified ? new Date(f.modified).toLocaleDateString() : '';
      var tap = f.isDir ? "navDir('" + esc(f.name) + "')" : "showActions('" + esc(f.name) + "')";
      return '<div class="file" onclick="' + tap + '">' +
        '<div class="icon">' + icon + '</div><div class="info"><div class="name">' + esc(f.name) + '</div>' +
        '<div class="meta">' + sz + (sz && date ? ' · ' : '') + date + '</div></div></div>';
    }).join('');
  };
  iappyx.smb.listDir(currentPath, cbId);
}

function navDir(name) { currentPath += name + '/'; listDir(); }
function goUp() {
  if (currentPath === '/') return;
  var parts = currentPath.split('/').filter(function(p) { return p; });
  parts.pop();
  currentPath = '/' + (parts.length ? parts.join('/') + '/' : '');
  listDir();
}

function showActions(name) {
  selectedFile = name;
  document.getElementById('actionTitle').textContent = name;
  document.getElementById('actionInfo').textContent = 'Loading...';
  document.getElementById('actionSheet').style.display = '';
  var cbId = 'info_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (r.ok && r.exists) {
      document.getElementById('actionInfo').textContent = formatSize(r.size) + (r.modified ? ' · ' + new Date(r.modified).toLocaleString() : '') + (r.hidden ? ' · Hidden' : '');
    } else { document.getElementById('actionInfo').textContent = ''; }
  };
  iappyx.smb.getFileInfo(currentPath + name, cbId);
}
function closeAction() { document.getElementById('actionSheet').style.display = 'none'; selectedFile = null; }
function actionDownload() {
  if (!selectedFile) return; closeAction(); var name = selectedFile;
  var cbId = 'dl_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) { iappyx.storage.copyFileToDownloads(r.filePath, name, 'application/octet-stream'); alert('Saved to Downloads: ' + name); }
    else alert('Download failed: ' + r.error);
  };
  iappyx.smb.download(currentPath + name, 'smb_dl_' + Date.now(), cbId);
}
function actionRename() {
  if (!selectedFile) return;
  var newName = prompt('New name:', selectedFile);
  if (!newName || newName === selectedFile) { closeAction(); return; }
  closeAction();
  var cbId = 'ren_' + Date.now();
  window._iappyxCb[cbId] = function(r) { if (r.ok) listDir(); else alert('Rename failed: ' + r.error); };
  iappyx.smb.rename(currentPath + selectedFile, currentPath + newName, cbId);
}
function actionDelete() {
  if (!selectedFile) return;
  if (!confirm('Delete ' + selectedFile + '?')) { closeAction(); return; }
  closeAction(); var name = selectedFile;
  var cbId = 'del_' + Date.now();
  window._iappyxCb[cbId] = function(r) { if (r.ok) listDir(); else alert('Delete failed: ' + r.error); };
  iappyx.smb.delete(currentPath + name, cbId);
}

function uploadFile() {
  var cbId = 'pick_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (!r.ok) return;
    var remote = currentPath + r.name;
    var ulCb = 'ul_' + Date.now();
    window._iappyxCb[ulCb] = function(r2) {
      if (r2.ok) listDir();
      else alert('Upload failed: ' + r2.error);
    };
    iappyx.smb.upload(r.filePath, remote, ulCb);
  };
  iappyx.storage.pickFile(cbId);
}

function createFolder() {
  var name = prompt('Folder name:');
  if (!name) return;
  var cbId = 'mkdir_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) listDir();
    else alert('Failed: ' + r.error);
  };
  iappyx.smb.mkdir(currentPath + name, cbId);
}

function doDisconnect() {
  iappyx.smb.disconnect();
  connected = false;
  setStatus(false);
  document.getElementById('fileList').innerHTML = '<div class="status">Discover shares to browse</div>';
  document.getElementById('pathBar').textContent = '/';
  document.getElementById('shareList').innerHTML = '';
}

function setStatus(on) {
  document.getElementById('dot').className = 'dot ' + (on ? 'on' : 'off');
  document.getElementById('connLabel').textContent = on ? 'Connected' : 'Disconnected';
}

function fileIcon(name) {
  var ext = name.split('.').pop().toLowerCase();
  if (['jpg','jpeg','png','gif','webp','bmp'].includes(ext)) return '🖼️';
  if (['mp4','avi','mkv','mov','wmv'].includes(ext)) return '🎬';
  if (['mp3','wav','flac','aac','ogg','m4a'].includes(ext)) return '🎵';
  if (['pdf'].includes(ext)) return '📕';
  if (['doc','docx','txt','rtf'].includes(ext)) return '📝';
  if (['zip','rar','7z','tar','gz'].includes(ext)) return '📦';
  if (['apk'].includes(ext)) return '📱';
  return '📄';
}

function formatSize(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b/1024).toFixed(1) + ' KB';
  if (b < 1073741824) return (b/1048576).toFixed(1) + ' MB';
  return (b/1073741824).toFixed(1) + ' GB';
}
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/'/g,'&#39;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

    fun getBleScanApp(label: String) = """<!DOCTYPE html>
<html lang="en"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>$label</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,sans-serif;background:#0d0d1a;color:#eaeaea;min-height:100vh;padding:16px;display:flex;flex-direction:column;gap:10px}
h1{font-size:1.2rem;text-align:center}
.card{background:#1a1a2e;border-radius:12px;padding:12px}
.card h3{font-size:.85rem;margin-bottom:6px;color:rgba(255,255,255,.6)}
.btn{background:#0f3460;border:none;border-radius:10px;padding:8px 14px;color:#fff;font-size:.8rem;cursor:pointer}
.btn:active{opacity:.7}
.btn.danger{background:#c62828}
.btn.sm{padding:6px 10px;font-size:.75rem}
.row{display:flex;gap:6px;align-items:center}
.device{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.06);cursor:pointer}
.device:last-child{border:none}
.device:active{opacity:.7}
.device .icon{font-size:1.2rem}
.device .info{flex:1}
.device .name{font-size:.85rem}
.device .addr{font-size:.7rem;color:rgba(255,255,255,.3)}
.rssi{font-size:.7rem;color:#4fc3f7;min-width:40px;text-align:right}
.svc{margin-top:6px;padding:8px;background:#0d0d1a;border-radius:8px;font-size:.8rem}
.svc-title{color:#4fc3f7;font-size:.75rem;margin-bottom:4px}
.char{padding:4px 0;border-bottom:1px solid rgba(255,255,255,.04);font-size:.75rem}
.char:last-child{border:none}
.char-uuid{color:rgba(255,255,255,.4);font-family:monospace;font-size:.65rem}
.char-props{color:#69f0ae;font-size:.65rem}
.char-val{color:#4fc3f7;font-family:monospace;margin-top:2px}
.status{text-align:center;font-size:.75rem;color:rgba(255,255,255,.3);padding:12px}
input{background:#0d0d1a;border:none;border-radius:6px;padding:6px 8px;color:#fff;font-size:.8rem;outline:none;font-family:monospace;width:100%}
</style></head><body>
<h1>🔵 $label</h1>

<div class="row">
  <button class="btn" onclick="startScan()" id="scanBtn" style="flex:1">🔍 Scan</button>
  <button class="btn danger" onclick="stopScan()" style="flex:1">Stop</button>
</div>

<div class="card" id="deviceCard">
  <h3>Devices <span id="devCount" style="color:rgba(255,255,255,.3);font-weight:normal"></span></h3>
  <input id="filter" placeholder="Filter by name or address..." oninput="renderDevices()" style="margin-bottom:8px">
  <div id="deviceList"><div class="status">Tap Scan to find BLE devices</div></div>
</div>

<div class="card" id="connCard" style="display:none">
  <h3>Connected: <span id="connName"></span></h3>
  <button class="btn sm danger" onclick="doDisconnect()" style="margin-bottom:8px">Disconnect</button>
  <div id="serviceList"></div>
</div>

<script>
var devices = {};
var connectedAddr = null;

function init() {
  if (typeof iappyx === 'undefined') { setTimeout(init, 50); return; }
}

function startScan() {
  devices = {};
  document.getElementById('deviceList').innerHTML = '<div class="status">Scanning...</div>';
  iappyx.ble.startScan('onBleEvent');
}

function stopScan() {
  iappyx.ble.stopScan();
}

function onBleEvent(evt) {
  if (evt.event === 'error') {
    document.getElementById('deviceList').innerHTML = '<div class="status" style="color:#ff6b6b">' + esc(evt.error) + '</div>';
    return;
  }
  if (evt.event === 'found') {
    devices[evt.address] = {name: evt.name || 'Unknown', address: evt.address, rssi: evt.rssi};
    renderDevices();
  }
}

function renderDevices() {
  var el = document.getElementById('deviceList');
  var q = (document.getElementById('filter').value || '').toLowerCase();
  var arr = Object.values(devices).filter(function(d) {
    if (!q) return true;
    return (d.name || '').toLowerCase().indexOf(q) >= 0 || d.address.toLowerCase().indexOf(q) >= 0;
  }).sort(function(a,b) { return b.rssi - a.rssi; });
  document.getElementById('devCount').textContent = '(' + Object.keys(devices).length + ' total' + (q ? ', ' + arr.length + ' matched' : '') + ')';
  if (arr.length === 0) { el.innerHTML = '<div class="status">' + (q ? 'No matches for "' + esc(q) + '"' : 'No devices found') + '</div>'; return; }
  el.innerHTML = arr.map(function(d) {
    var icon = d.name && d.name.length > 0 ? '📱' : '📡';
    var signal = d.rssi > -50 ? '▰▰▰▰' : d.rssi > -70 ? '▰▰▰▱' : d.rssi > -85 ? '▰▰▱▱' : '▰▱▱▱';
    return '<div class="device" onclick="connectTo(\'' + d.address + '\')">' +
      '<div class="icon">' + icon + '</div>' +
      '<div class="info"><div class="name">' + esc(d.name || 'Unknown') + '</div>' +
      '<div class="addr">' + d.address + '</div></div>' +
      '<div class="rssi">' + signal + ' ' + d.rssi + '</div></div>';
  }).join('');
}

function connectTo(addr) {
  iappyx.ble.stopScan();
  document.getElementById('deviceList').innerHTML = '<div class="status">Connecting to ' + addr + '...</div>';
  var cbId = 'ble_' + Date.now();
  window._iappyxCb = window._iappyxCb || {};
  window._iappyxCb[cbId] = function(r) {
    if (r.ok) {
      connectedAddr = addr;
      var dev = devices[addr] || {name: 'Device'};
      document.getElementById('connName').textContent = dev.name || addr;
      document.getElementById('connCard').style.display = '';
      renderServices(r.services);
    } else {
      document.getElementById('deviceList').innerHTML = '<div class="status" style="color:#ff6b6b">' + esc(r.error) + '</div>';
    }
  };
  iappyx.ble.connect(addr, cbId);
}

function renderServices(services) {
  var el = document.getElementById('serviceList');
  if (!services || services.length === 0) { el.innerHTML = '<div class="status">No services found</div>'; return; }
  el.innerHTML = services.map(function(s) {
    var sName = knownService(s.uuid) || s.uuid;
    var chars = s.characteristics.map(function(c) {
      var cName = knownChar(c.uuid) || '';
      var props = c.properties.join(', ');
      var actions = '';
      if (c.properties.indexOf('read') >= 0) actions += '<button class="btn sm" onclick="doRead(\'' + s.uuid + '\',\'' + c.uuid + '\')">Read</button> ';
      if (c.properties.indexOf('notify') >= 0 || c.properties.indexOf('indicate') >= 0)
        actions += '<button class="btn sm" onclick="doSubscribe(\'' + s.uuid + '\',\'' + c.uuid + '\')">Subscribe</button> ';
      if (c.properties.indexOf('write') >= 0 || c.properties.indexOf('writeNoResponse') >= 0)
        actions += '<div class="row" style="margin-top:4px"><input id="w_' + c.uuid.substring(0,8) + '" placeholder="hex bytes (e.g. 01ff)"><button class="btn sm" onclick="doWrite(\'' + s.uuid + '\',\'' + c.uuid + '\')">Write</button></div>';
      return '<div class="char">' + (cName ? '<b>' + cName + '</b><br>' : '') +
        '<div class="char-uuid">' + c.uuid + '</div>' +
        '<div class="char-props">' + props + '</div>' +
        '<div class="row" style="margin-top:4px;flex-wrap:wrap">' + actions + '</div>' +
        '<div class="char-val" id="v_' + c.uuid.substring(0,8) + '"></div></div>';
    }).join('');
    return '<div class="svc"><div class="svc-title">' + esc(sName) + '</div>' + chars + '</div>';
  }).join('');
}

function doRead(svc, ch) {
  var cbId = 'rd_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    var el = document.getElementById('v_' + ch.substring(0,8));
    if (el) el.textContent = r.ok ? r.hex + ' (' + r.value + ')' : r.error;
  };
  iappyx.ble.read(connectedAddr, svc, ch, cbId);
}

function doWrite(svc, ch) {
  var hex = document.getElementById('w_' + ch.substring(0,8)).value.replace(/\s/g,'');
  if (!hex) return;
  var cbId = 'wr_' + Date.now();
  window._iappyxCb[cbId] = function(r) {
    var el = document.getElementById('v_' + ch.substring(0,8));
    if (el) el.textContent = r.ok ? 'Written!' : r.error;
  };
  iappyx.ble.write(connectedAddr, svc, ch, hex, cbId);
}

function doSubscribe(svc, ch) {
  iappyx.ble.subscribe(connectedAddr, svc, ch, 'onBleNotify_' + ch.substring(0,8));
  window['onBleNotify_' + ch.substring(0,8)] = function(evt) {
    var el = document.getElementById('v_' + ch.substring(0,8));
    if (el) el.textContent = evt.hex + ' (' + evt.value + ')';
  };
  var el = document.getElementById('v_' + ch.substring(0,8));
  if (el) el.textContent = 'Subscribed, waiting...';
}

function doDisconnect() {
  if (connectedAddr) iappyx.ble.disconnect(connectedAddr);
  connectedAddr = null;
  document.getElementById('connCard').style.display = 'none';
}

function knownService(uuid) {
  var u=uuid.toLowerCase(),p='0000-1000-8000-00805f9b34fb',map={
    ['00001800-'+p]:'Generic Access',['00001801-'+p]:'Generic Attribute',['00001802-'+p]:'Immediate Alert',
    ['00001803-'+p]:'Link Loss',['00001804-'+p]:'Tx Power',['00001805-'+p]:'Current Time',
    ['00001806-'+p]:'Reference Time',['00001808-'+p]:'Glucose',['00001809-'+p]:'Health Thermometer',
    ['0000180a-'+p]:'Device Info',['0000180d-'+p]:'Heart Rate',['0000180e-'+p]:'Phone Alert',
    ['0000180f-'+p]:'Battery',['00001810-'+p]:'Blood Pressure',['00001811-'+p]:'Alert Notification',
    ['00001812-'+p]:'HID',['00001813-'+p]:'Scan Parameters',['00001814-'+p]:'Running Speed',
    ['00001816-'+p]:'Cycling Speed',['00001818-'+p]:'Cycling Power',['0000181a-'+p]:'Environment Sensing',
    ['0000181c-'+p]:'User Data',['0000181d-'+p]:'Weight Scale',['0000181e-'+p]:'Bond Management',
    ['00001822-'+p]:'Pulse Oximeter',['0000fe95-'+p]:'Xiaomi',['0000fee0-'+p]:'Mi Band'};
  return map[u]||null;
}
function knownChar(uuid) {
  var u=uuid.toLowerCase(),p='0000-1000-8000-00805f9b34fb',map={
    ['00002a00-'+p]:'Device Name',['00002a01-'+p]:'Appearance',['00002a04-'+p]:'Connection Params',
    ['00002a05-'+p]:'Service Changed',['00002a19-'+p]:'Battery Level',['00002a23-'+p]:'System ID',
    ['00002a24-'+p]:'Model Number',['00002a25-'+p]:'Serial Number',['00002a26-'+p]:'Firmware Rev',
    ['00002a27-'+p]:'Hardware Rev',['00002a28-'+p]:'Software Rev',['00002a29-'+p]:'Manufacturer',
    ['00002a37-'+p]:'Heart Rate',['00002a38-'+p]:'Body Sensor Location',['00002a39-'+p]:'HR Control Point',
    ['00002a49-'+p]:'Blood Pressure Feature',['00002a35-'+p]:'Blood Pressure',
    ['00002a1c-'+p]:'Temperature',['00002a1d-'+p]:'Temp Type',['00002a1e-'+p]:'Intermediate Temp',
    ['00002a6d-'+p]:'Pressure',['00002a6e-'+p]:'Temperature (env)',['00002a6f-'+p]:'Humidity',
    ['00002a70-'+p]:'True Wind Speed',['00002a76-'+p]:'UV Index',['00002a77-'+p]:'Irradiance',
    ['00002a9d-'+p]:'Weight',['00002a9e-'+p]:'Weight Scale Feature',['00002a98-'+p]:'Weight Resolution',
    ['00002a5b-'+p]:'CSC Measurement',['00002a5d-'+p]:'Sensor Location',
    ['00002a53-'+p]:'RSC Measurement',['00002a63-'+p]:'Cycling Power',
    ['00002a5a-'+p]:'Aggregate Input',['00002a58-'+p]:'Analog Input',['00002a56-'+p]:'Digital Input'};
  return map[u]||null;
}
function esc(s) { return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

window.addEventListener('load', function() { setTimeout(init, 200); });
</script></body></html>""".trimIndent()

}
