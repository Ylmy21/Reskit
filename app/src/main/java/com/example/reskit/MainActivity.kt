package com.example.reskit

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
// activity result contracts imported below once
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import java.util.UUID
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reskit.ui.theme.ReskitTheme
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.io.StringWriter
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ReskitTheme {
                MainScreen(this)
            }
        }
    }
}

@Composable
fun MeasurementScreen(logs: MutableList<String>, onDisconnect: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "测量界面", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onDisconnect() }) { Text("断开连接并返回") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "运行日志", fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        SelectionContainer {
            Column(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF101010))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
            ) {
                for (ln in logs) {
                    Text(text = ln, color = Color(0xFFEEEEEE))
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

// --- Visualization helpers (moved above MainScreen to avoid forward-reference issues) ---
@Composable
fun EMGBar(
    strength: Float,
    fatigue: Float,
    strengthIndex: Float = 1.0f,
    fatigueIndex: Float = 1.0f,
    modifier: Modifier = Modifier,
    haloRadiusDp: Float = 60f
) {
    // apply indices to compute displayed values
    val displayedStrength = (strength * strengthIndex).coerceIn(0f, 1f)
    val displayedFatigue = (fatigue * fatigueIndex).coerceIn(0f, 1f)

    fun fatigueToColor(f: Float): Color {
        val clamped = f.coerceIn(0f, 1f)
        return when {
            clamped <= 0.5f -> {
                val t = clamped / 0.5f
                Color(0xFF00C853).copy(alpha = 1f).lerp(Color(0xFFFFD600), t)
            }
            else -> {
                val t = (clamped - 0.5f) / 0.5f
                Color(0xFFFFD600).lerp(Color(0xFFD50000), t)
            }
        }
    }

    val color = fatigueToColor(displayedFatigue)
    val haloRadiusPx = with(LocalDensity.current) { haloRadiusDp.dp.toPx() }

    Box(modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val barW = (w * displayedStrength)
            val barH = h * 0.6f
            val barTop = (h - barH) / 2f

            val haloCenterX = (barW).coerceAtLeast(16f)
            val haloCenter = Offset(haloCenterX, h / 2f)
            val haloRadius = haloRadiusPx

            // halo radial gradient
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center = haloCenter, radius = haloRadius),
                radius = haloRadius,
                center = haloCenter,
                style = Fill
            )

            // background track
            drawRoundRect(
                color = Color(0xFF2B2B2B),
                topLeft = Offset(0f, barTop),
                size = androidx.compose.ui.geometry.Size(w, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2f, barH / 2f)
            )

            // foreground bar
            if (barW > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.9f))),
                    topLeft = Offset(0f, barTop),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2f, barH / 2f)
                )
            }

            // numeric overlay (optional subtle)
        }
    }
}

// helper lerp extension for Color
fun Color.lerp(target: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = this.red + (target.red - this.red) * f,
        green = this.green + (target.green - this.green) * f,
        blue = this.blue + (target.blue - this.blue) * f,
        alpha = this.alpha + (target.alpha - this.alpha) * f
    )
}

private const val MAX_EMG_POINTS = 2000  // roughly 1s @2000Hz or 4s @500Hz
private const val METRIC_SAMPLE_SKIP = 40 // compute metrics every N samples to reduce load and bar频率
private const val CHART_THROTTLE_MS = 30L // chart update throttle (~33fps)

@Composable
fun EMGChart(data: List<Float>, modifier: Modifier = Modifier) {
    val pts = data.take(MAX_EMG_POINTS).reversed()
    Box(modifier = modifier.background(Color(0xFF0B0B0B))) {
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            if (pts.isEmpty()) return@Canvas
            val maxPoints = pts.size
            val step = if (maxPoints > 1) w / (maxPoints - 1) else w
            // grid lines
            val gridLevels = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            gridLevels.forEach { g ->
                val y = h * g
                drawLine(color = Color(0xFF222222), start = Offset(0f, y), end = Offset(w, y))
            }
            var prevX = 0f
            var prevY = h * (1f - ((pts[0].coerceIn(-1f, 1f) + 1f) / 2f))
            for (i in pts.indices) {
                val x = i * step
                val norm = (pts[i].coerceIn(-1f, 1f) + 1f) / 2f // map [-1,1] -> [0,1]
                val y = h * (1f - norm)
                if (i > 0) drawLine(color = Color(0xFF00E676), start = Offset(prevX, prevY), end = Offset(x, y), strokeWidth = 4f)
                prevX = x; prevY = y
            }
        }
    }
}

@Composable
fun EMGChartSeries(title: String, data: List<Float>, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Column(modifier = Modifier.width(40.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("+1", fontSize = 12.sp)
            Text("0", fontSize = 12.sp)
            Text("-1", fontSize = 12.sp)
        }
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
            EMGChart(data = data, modifier = Modifier.matchParentSize())
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    var mac by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    // helper to convert throwable to full stack string
    fun throwableToString(t: Throwable?): String {
        if (t == null) return "null"
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    // add log and show a short popup for instant logs (always run on main thread)
    var toastEnabled by remember { mutableStateOf(true) }
    fun addLog(s: String) {
        val entry = "[${java.time.LocalTime.now().withNano(0)}] $s"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            logs.add(0, entry)
            if (logs.size > 200) logs.removeLast()
            try { if (toastEnabled) Toast.makeText(context, s, Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        } else {
            Handler(Looper.getMainLooper()).post {
                try {
                    logs.add(0, entry)
                    if (logs.size > 200) logs.removeLast()
                    if (toastEnabled) Toast.makeText(context, s, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }
    }
    
    // EMG visualization state: strength [0f..1f], fatigue [0f..1f]
    var emgStrength by remember { mutableStateOf(0.25f) }
    var emgFatigue by remember { mutableStateOf(0.0f) }
    // BLE scan state
    var scanning by remember { mutableStateOf(false) }
    val devicesMap = remember { mutableStateMapOf<String, BluetoothDevice>() }
    val devicesNameMap = remember { mutableStateMapOf<String, String>() }
    var selectedDeviceAddr by remember { mutableStateOf<String?>(null) }

    val permissionList = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val granted = perms.entries.all { it.value }
        if (!granted) {
            logs.add(0, "[${java.time.LocalTime.now().withNano(0)}] 需要蓝牙权限以扫描设备")
        }
    }

    fun ensurePermissions() {
        val missing = permissionList.filter { ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // Bluetooth manager and scanner
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager
    val btAdapter = btManager?.adapter
    val btScanner = btAdapter?.bluetoothLeScanner

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device
                if (dev != null && dev.address != null) {
                    val addr = dev.address
                    // prefer advertised name from scanRecord, fallback to device.name
                    val advName = result.scanRecord?.deviceName
                    val name = advName ?: dev.name ?: addr
                    devicesMap[addr] = dev
                    devicesNameMap[addr] = name
                }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (r in results) onScanResult(0, r)
            }
        }
    }

    // GATT map to keep open connections (not a compose state)
    val gattMap = remember { mutableMapOf<String, BluetoothGatt?>() }
    val DEVICE_NAME_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

    fun connectAndReadName(device: BluetoothDevice) {
        val addr = device.address
        // check permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            addLog("缺少 BLUETOOTH_CONNECT 权限，无法读取设备名")
            return
        }
        // close existing
        gattMap[addr]?.close()
        try {
            val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.close()
                        gattMap.remove(addr)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    val service = gatt.getService(UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"))
                    val char = service?.getCharacteristic(DEVICE_NAME_UUID)
                    if (char != null) {
                        gatt.readCharacteristic(char)
                    } else {
                        val name = gatt.device.name ?: gatt.device.address
                        Handler(Looper.getMainLooper()).post { devicesNameMap[gatt.device.address] = name }
                    }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    super.onCharacteristicRead(gatt, characteristic, status)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val name = characteristic.getStringValue(0) ?: gatt.device.address
                        Handler(Looper.getMainLooper()).post { devicesNameMap[gatt.device.address] = name }
                    }
                }
            })
            gattMap[addr] = gatt
        } catch (e: Exception) {
            addLog("GATT 连接失败: ${e.localizedMessage}")
        }
    }

    fun startScan() {
        // don't call ensurePermissions here (permission handled when opening selection).
        if (btAdapter == null) { logs.add(0, "[${java.time.LocalTime.now().withNano(0)}] 设备不支持蓝牙"); return }
        if (!btAdapter.isEnabled) {
            logs.add(0, "[${java.time.LocalTime.now().withNano(0)}] 请先打开蓝牙")
            return
        }
        devicesMap.clear()
        try {
            btScanner?.startScan(scanCallback)
            scanning = true
            logs.add(0, "[${java.time.LocalTime.now().withNano(0)}] 开始扫描 BLE 设备...")
        } catch (e: Exception) {
            logs.add(0, "[${java.time.LocalTime.now().withNano(0)}] 扫描启动失败: ${e.localizedMessage}")
        }
    }

    fun stopScan() {
        try {
            btScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanning = false
        logs.add(0, "[${java.time.LocalTime.now().withNano(0)}] 停止扫描")
    }
    // connection & measurement flow
    var isConnected by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") } // "home" | "measurement"
    var serviceReady by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("") }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showAllDevices by remember { mutableStateOf(false) }
    var showMeasurementDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    // 单通道 EMG 可视化（改为真实 BLE 输入预留管道）
    val measurementOptions = listOf("EMG")
    var measuring by remember { mutableStateOf(false) }
    val emgSeries = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    var emgMetricCounter by remember { mutableStateOf(0) }
    var lastEmgArrivalMs by remember { mutableStateOf(0L) }
    var lastEmgDeviceTs by remember { mutableStateOf(0L) }
    var emgPacketCount by remember { mutableStateOf(0) }
    var lastChartUpdateMs by remember { mutableStateOf(0L) }
    // streaming state
    val streaming = remember { AtomicBoolean(false) }
    var notifyListenerRef by remember { mutableStateOf<Any?>(null) }
    // tunable indices for EMGBar
    var strengthIndex by remember { mutableStateOf(1.0f) }
    var fatigueIndex by remember { mutableStateOf(1.0f) }
    // calibration values for RMS-based fatigue estimation
    var baselineRms by remember { mutableStateOf<Float?>(null) }
    var maxRms by remember { mutableStateOf<Float?>(null) }
    var observedMaxRms by remember { mutableStateOf(0f) }
    // frequency-domain calibration (median frequency normalized)
    var baselineMf by remember { mutableStateOf<Float?>(null) }
    var minMf by remember { mutableStateOf<Float?>(null) }
    var observedMinMf by remember { mutableStateOf(1f) }
    // sensitivities for FI (freq) and FL (amp)
    var fiSensitivity by remember { mutableStateOf(1.0f) }
    var flSensitivity by remember { mutableStateOf(1.0f) }
    var showParamDialog by remember { mutableStateOf(false) }
    fun resetLocalState() {
        emgSeries.clear()
        logs.clear()
        emgMetricCounter = 0
        lastEmgArrivalMs = 0L
        lastEmgDeviceTs = 0L
        emgPacketCount = 0
        observedMaxRms = 0f
        baselineRms = null
        maxRms = null
        baselineMf = null
        minMf = null
        observedMinMf = 1f
        emgFatigue = 0f
        emgStrength = 0f
    }

    // helper: compute RMS from a list of Float (use last N samples)
    fun computeRms(list: List<Float>, windowSize: Int = 64): Float {
        if (list.isEmpty()) return 0f
        val takeCount = windowSize.coerceAtMost(list.size)
        val slice = list.take(takeCount)
        var s = 0.0
        for (v in slice) s += (v * v)
        return kotlin.math.sqrt((s / slice.size).toFloat())
    }

    // compute mean of top-N absolute samples to smooth bar and emphasize高幅值
    fun computeTopMeanAbs(list: List<Float>, topN: Int = 8): Float {
        if (list.isEmpty()) return 0f
        val n = topN.coerceAtLeast(1).coerceAtMost(list.size)
        val sorted = list.asSequence().map { kotlin.math.abs(it) }.sortedDescending().take(n).toList()
        return (sorted.sum() / sorted.size).toFloat()
    }

    // compute normalized median frequency [0..1] using naive DFT (suitable for small N)
    fun computeMedianFreqNormalized(list: List<Float>, windowSize: Int = 128): Float {
        val n = windowSize.coerceAtMost(list.size)
        if (n <= 2) return 0.5f
        val x = list.take(n).reversed() // oldest -> newest order
        // compute power spectrum for k = 0..n/2
        val half = n / 2
        val power = FloatArray(half + 1)
        val twoPi = 2.0 * Math.PI
        for (k in 0..half) {
            var re = 0.0
            var im = 0.0
            for (i in 0 until n) {
                val angle = twoPi * k * i / n
                re += x[i] * kotlin.math.cos(angle).toDouble()
                im -= x[i] * kotlin.math.sin(angle).toDouble()
            }
            val p = re * re + im * im
            power[k] = p.toFloat()
        }
        // cumulative energy to find median bin
        val total = power.sum().takeIf { it > 0f } ?: return 0.5f
        var cum = 0f
        val halfE = total / 2f
        var medianBin = 0
        for (k in power.indices) {
            cum += power[k]
            if (cum >= halfE) { medianBin = k; break }
        }
        // normalize median bin to [0..1] where 1 corresponds to Nyquist (half)
        return medianBin.toFloat() / half.toFloat()
    }

    

    suspend fun callSdkStartService(ctx: Context) {
        try {
            // try several likely class names from the AAR
            val candidates = listOf(
                "BLEManager",
                "com.goertek.ble.client.function.BLEManager",
                "com.goertek.ble.client.function.BLEManager\$Singleton"
            )
            var cls: Class<*>? = null
            for (c in candidates) {
                try {
                    cls = try {
                        Class.forName(c)
                    } catch (_: ClassNotFoundException) {
                        // try using the context classloader
                        context.classLoader.loadClass(c)
                    }
                    addLog("找到 BLEManager 类：$c")
                    break
                } catch (_: Exception) { }
            }
            if (cls == null) throw ClassNotFoundException("BLEManager")
            val getInst = cls.getMethod("getInstance").invoke(null)
            // find startService method
            val methods = cls.methods.filter { it.name == "startService" }
            if (methods.isEmpty()) {
                addLog("startService 方法未找到")
                return
            }
            val method = methods.first { it.parameterTypes.size >= 1 }
            val params = mutableListOf<Any?>()
            // first param is Context
            params.add(ctx)
            // if second param is a callback interface, create proxy that logs calls
            if (method.parameterTypes.size >= 2) {
                val callbackClass = method.parameterTypes[1]
                val proxy = Proxy.newProxyInstance(
                    callbackClass.classLoader,
                    arrayOf(callbackClass)
                ) { _proxy: Any, m: Method, args: Array<Any>? ->
                    addLog("Callback ${m.name} invoked, args=${args?.joinToString()}")
                    // if callback reports service started (common signature: boolean true), mark serviceReady
                    try {
                        if (args != null && args.isNotEmpty()) {
                            val first = args[0]
                            if (first is Boolean && first) {
                                Handler(Looper.getMainLooper()).post { serviceReady = true }
                                addLog("serviceReady = true")
                            }
                        }
                    } catch (_: Exception) {}
                    null
                }
                params.add(proxy)
            }
            // Before calling startService, try to register BLEManager with ProtocolManager if available
            try {
                val pmCandidates = listOf("com.goertek.protocol.ProtocolManager")
                var pmCls: Class<*>? = null
                for (pc in pmCandidates) {
                    try {
                        pmCls = try { Class.forName(pc) } catch (_: ClassNotFoundException) { context.classLoader.loadClass(pc) }
                        break
                    } catch (_: Exception) { }
                }
                if (pmCls != null) {
                    val pmInst = pmCls.getMethod("getInstance").invoke(null)
                    val regM = pmCls.methods.firstOrNull { it.name.contains("registerBluetoothManager") && it.parameterTypes.size == 1 }
                    if (regM != null) {
                        try {
                            regM.invoke(pmInst, getInst)
                            addLog("已向 ProtocolManager 注册 BLEManager")
                        } catch (e: Exception) {
                            addLog("注册到 ProtocolManager 失败: ${e.localizedMessage}")
                        }
                    }
                }
            } catch (_: Exception) {}

            val result = method.invoke(getInst, *params.toTypedArray())
            addLog("startService invoked, result=$result")
        } catch (e: ClassNotFoundException) {
            addLog("BLEManager 类未找到：${e.message}")
        } catch (e: Exception) {
            addLog("启动服务异常：${e.localizedMessage}")
        }
    }

    fun callSdkConnect(macAddr: String): Boolean {
        try {
            val candidates = listOf(
                "BLEManager",
                "com.goertek.ble.client.function.BLEManager",
                "com.goertek.ble.client.function.BLEManager\$Singleton"
            )
            var cls: Class<*>? = null
            for (c in candidates) {
                try {
                    cls = try {
                        Class.forName(c)
                    } catch (_: ClassNotFoundException) {
                        context.classLoader.loadClass(c)
                    }
                    addLog("找到 BLEManager 类：$c")
                    break
                } catch (_: Exception) { }
            }
            if (cls == null) throw ClassNotFoundException("BLEManager")
            val inst = cls.getMethod("getInstance").invoke(null)
            // debug: log available methods and signatures
            val sigs = cls.methods.joinToString(separator = " | ") { m ->
                m.name + "(" + m.parameterTypes.joinToString(",") { it.simpleName } + ")"
            }
            addLog("BLEManager methods: $sigs")
            // try to find connectDevice with String param, else without param
            val mStr = cls.methods.firstOrNull { it.name == "connectDevice" && it.parameterTypes.size == 1 }
            val res = try {
                    if (mStr != null) {
                    try {
                        mStr.invoke(inst, macAddr)
                    } catch (e: Exception) {
                        // InvocationTargetException may wrap the cause; log full stack
                        addLog("connectDevice invoke error: ${e::class.java.name} : ${e.localizedMessage}")
                        addLog(throwableToString(e))
                        null
                    }
                } else {
                    val m0 = cls.methods.firstOrNull { it.name == "connectDevice" && it.parameterTypes.isEmpty() }
                    try {
                        m0?.invoke(inst)
                    } catch (e: Exception) {
                        addLog("connectDevice invoke error: ${e::class.java.name} : ${e.localizedMessage}")
                        addLog(throwableToString(e))
                        null
                    }
                }
            } catch (e: Exception) {
                addLog("反射调用异常: ${e.localizedMessage}")
                null
            }
            addLog("connectDevice invoked, result=$res")
            // interpret result
            if (res is Boolean) return res
            return res != null
        } catch (e: ClassNotFoundException) {
            addLog("BLEManager 类未找到：${e.message}")
        } catch (e: Exception) {
            addLog("connectDevice 异常：${e.localizedMessage}")
        }
        return false
    }

    fun callSdkDisconnect(): Boolean {
        try {
            val candidates = listOf(
                "BLEManager",
                "com.goertek.ble.client.function.BLEManager",
                "com.goertek.ble.client.function.BLEManager\$Singleton"
            )
            var cls: Class<*>? = null
            for (c in candidates) {
                try {
                    cls = try {
                        Class.forName(c)
                    } catch (_: ClassNotFoundException) {
                        context.classLoader.loadClass(c)
                    }
                    addLog("找到 BLEManager 类：$c")
                    break
                } catch (_: Exception) { }
            }
            if (cls == null) throw ClassNotFoundException("BLEManager")
            val inst = cls.getMethod("getInstance").invoke(null)
            val m = cls.methods.firstOrNull { it.name == "disconnectDevice" && it.parameterTypes.isEmpty() }
            val res = try {
                m?.invoke(inst)
            } catch (e: Exception) {
                addLog("disconnectDevice invoke error: ${e::class.java.name} : ${e.localizedMessage}")
                addLog(throwableToString(e))
                null
            }
            addLog("disconnectDevice invoked, result=$res")
            return if (res is Boolean) res else res != null
        } catch (e: Exception) {
            addLog("disconnectDevice 异常：${e.localizedMessage}")
        }
        return false
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "Reskit Demo", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // 自动启动 SDK 服务（在 Composable 启动时）
            LaunchedEffect(Unit) {
                try {
                    callSdkStartService(context)
                } catch (e: Exception) {
                    addLog("启动服务异常: ${e.localizedMessage}")
                }
            }

            // 如果处于测量页面，则不渲染主界面控件
            if (!(currentScreen == "measurement" && isConnected)) {
                // 选择设备与查看日志（扫描与权限在选择时自动处理）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        ensurePermissions()
                        try { startScan() } catch (_: Exception) {}
                        showDeviceDialog = true
                    }) { Text("选择设备") }
                    Button(onClick = { showLogsDialog = true }) { Text("查看日志") }
                }

                Spacer(modifier = Modifier.height(8.dp))

            // 设备选择弹窗：自动在打开时开始扫描并持续更新；自动请求权限
            if (showDeviceDialog) {
                // request permissions automatically
                val missing = permissionList.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isNotEmpty()) {
                    // launch permission request once
                    LaunchedEffect(Unit) { permissionLauncher.launch(missing.toTypedArray()) }
                    // show message that permission is needed
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDeviceDialog = false },
                        title = { Text("权限请求") },
                        text = { Text("需要蓝牙权限以扫描设备；请允许权限后重试。") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { showDeviceDialog = false }) { Text("关闭") } }
                    )
                } else {
                    // start scanning when dialog opens
                    LaunchedEffect(Unit) {
                        try { startScan() } catch (_: Exception) {}
                        // keep scanning while dialog open
                        while (showDeviceDialog) {
                            delay(2000L)
                        }
                        try { stopScan() } catch (_: Exception) {}
                    }

                    val macOnlyPattern = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
                    val filtered = devicesMap.values.filter { dev ->
                        val name = (devicesNameMap[dev.address] ?: dev.name ?: "").trim()
                        name.isNotBlank() && !macOnlyPattern.matches(name)
                    }
                    val listToShow = if (showAllDevices) devicesMap.values.toList() else filtered

                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDeviceDialog = false },
                        title = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("选择设备")
                                Row {
                                    androidx.compose.material3.TextButton(onClick = { /* refresh is automatic */ }) { Text("刷新") }
                                    androidx.compose.material3.TextButton(onClick = { showAllDevices = !showAllDevices }) {
                                        Text(if (showAllDevices) "仅显示带名设备" else "显示所有设备")
                                    }
                                }
                            }
                        },
                        text = {
                            if (listToShow.isEmpty()) {
                                Column { Text("未找到带名称的设备，请确保设备广播名称。") }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(listToShow) { dev ->
                                        val addr = dev.address
                                        val name = devicesNameMap[addr] ?: dev.name ?: "Unknown"
                                        Row(modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable {
                                                try {
                                                    // select and connect (protected)
                                                    showDeviceDialog = false
                                                    selectedDeviceAddr = addr
                                                    addLog("选择设备: $name ($addr)")
                                                    try { connectAndReadName(dev) } catch (e: Exception) { addLog("connectAndReadName 异常: ${e.localizedMessage}") }
                                                    scope.launch {
                                                        try {
                                                            // 直接使用 SDK 流程连接（startService + connectDevice）
                                                            try { callSdkStartService(context) } catch (e: Exception) { addLog("callSdkStartService 异常: ${e.localizedMessage}") }
                                                            val ok = callSdkConnect(addr)
                                                            isConnected = ok
                                                            addLog("连接 $addr: $ok")
                                                            if (ok) currentScreen = "measurement"
                                                        } catch (e: Throwable) {
                                                            addLog("连接流程异常: ${e::class.java.name} ${e.localizedMessage}")
                                                            addLog(throwableToString(e))
                                                        }
                                                    }
                                                } catch (t: Throwable) {
                                                    // catch any UI thread exceptions to avoid crash
                                                    addLog("设备选择点击异常: ${t::class.java.name} ${t.localizedMessage}")
                                                    addLog(throwableToString(t))
                                                    showDeviceDialog = false
                                                }
                                            }
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("$name", color = Color.White)
                                                Text(addr, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { showDeviceDialog = false }) { Text("关闭") } }
                    )
                }
            }


            Spacer(modifier = Modifier.height(12.dp))

            if (showLogsDialog) {
                val clipboardManager = LocalClipboardManager.current
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLogsDialog = false },
                    title = { Text("运行日志") },
                    text = {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                androidx.compose.material3.TextButton(onClick = { logs.clear() }) { Text("清除日志") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (logs.isEmpty()) {
                                Text("暂无日志")
                            } else {
                                SelectionContainer {
                                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                        items(logs) { ln ->
                                            Text(ln)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val all = logs.joinToString(separator = "\n")
                            clipboardManager.setText(AnnotatedString(all))
                            addLog("日志已复制到剪贴板")
                        }) { Text("复制") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showLogsDialog = false }) { Text("关闭") }
                    }
                )
            }

            } // end of home UI block

    // helper: push a new EMG sample (real BLE 回调调用此函数)
    fun pushEmgSample(sample: Float) {
        if (!measuring || !streaming.get()) return
        val now = android.os.SystemClock.uptimeMillis()
        val s = sample // keep signed

        // throttle chart/state updates on main thread
        val shouldUpdateChart = now - lastChartUpdateMs >= CHART_THROTTLE_MS
        if (shouldUpdateChart) lastChartUpdateMs = now

        // always enqueue sample to series, but keep buffer bounded
        emgSeries.add(0, s)
        if (emgSeries.size > MAX_EMG_POINTS) emgSeries.removeLast()

        emgMetricCounter++
        val shouldCalc = emgMetricCounter % METRIC_SAMPLE_SKIP == 0
        if (!shouldCalc && !shouldUpdateChart) return

        // snapshot data for background calc to avoid holding main thread
        val listSnapshot = emgSeries.toList()

        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            if (shouldCalc) {
                val currRms = computeRms(listSnapshot, windowSize = 64)
                val usedMaxLocal = maxRms ?: kotlin.math.max(observedMaxRms, currRms).let { if (it > 0f) it else (baselineRms ?: 0.5f) * 2f }
                val baseRLocal = baselineRms ?: 0f
                val denom = (usedMaxLocal - baseRLocal).let { if (it <= 0f) 1e-6f else it }
                val flRaw = ((currRms - baseRLocal) / denom).coerceIn(0f, 1f)

                val currMf = computeMedianFreqNormalized(listSnapshot, windowSize = 128)
                val baseMfLocal = baselineMf ?: currMf
                val minMfLocal = minMf ?: kotlin.math.min(observedMinMf, currMf)
                val denomMf = (baseMfLocal - minMfLocal).let { if (it <= 0f) 1e-6f else it }
                val fiRaw = ((baseMfLocal - currMf) / denomMf).coerceIn(0f, 1f)

                val fiWeighted = fiRaw * fiSensitivity
                val flWeighted = flRaw * flSensitivity
                val combined = if (fiSensitivity + flSensitivity > 0f) (fiWeighted + flWeighted) / (fiSensitivity + flSensitivity) else (fiRaw + flRaw) / 2f
                val fatigueVal = (combined * fatigueIndex).coerceIn(0f, 1f)
                val peakMean = computeTopMeanAbs(listSnapshot, topN = 8).coerceIn(0f, 1f)

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (currRms > observedMaxRms) observedMaxRms = currRms
                    if (currMf < observedMinMf) observedMinMf = currMf
                    emgFatigue = fatigueVal
                    emgStrength = peakMean
                }
            }
        }
    }

    // helper: convert various EMG container types to float list
    fun toFloatList(emgDataAny: Any?): List<Float>? {
        return when (emgDataAny) {
            is FloatArray -> emgDataAny.toList()
            is DoubleArray -> emgDataAny.map { it.toFloat() }
            is IntArray -> emgDataAny.map { it.toFloat() }
            is LongArray -> emgDataAny.map { it.toFloat() }
            is ByteArray -> emgDataAny.map { (it.toInt() and 0xFF).toFloat() }
            is Array<*> -> emgDataAny.filterIsInstance<Number>().map { it.toFloat() }
            is Iterable<*> -> emgDataAny.filterIsInstance<Number>().map { it.toFloat() }
            else -> null
        }
    }

    // helper: convert EMG short[] to normalized floats
    fun fromShortArray(arr: ShortArray): List<Float> = arr.map { it.toFloat() / 32768f }

    // helper: read static int constant from candidate classes via reflection
    fun getConst(classNames: List<String>, fieldName: String): Int? {
        for (name in classNames) {
            try {
                val cls = Class.forName(name)
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                val v = (field.get(null) as? Number)?.toInt()
                if (v != null) return v
            } catch (_: Exception) { }
        }
        return null
    }

    // helper: resolve class from candidate names
    fun resolveClass(classNames: List<String>): Class<*>? {
        for (name in classNames) {
            try { return Class.forName(name) } catch (_: Exception) { }
        }
        return null
    }

    // extract EMG values from SensorDataInfo-like object using reflection (methods or fields containing "emg")
    fun extractEmgValues(parsed: Any): List<Float>? {
        val cls = parsed.javaClass
        // methods first
        cls.methods.filter { it.parameterTypes.isEmpty() && it.name.contains("emg", ignoreCase = true) }.forEach { m ->
            try {
                val v = m.invoke(parsed)
                val list = toFloatList(v)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {}
        }
        // fields next
        cls.declaredFields.filter { it.name.contains("emg", ignoreCase = true) }.forEach { f ->
            try {
                f.isAccessible = true
                val v = f.get(parsed)
                val list = toFloatList(v)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {}
        }
        return null
    }

    // hooks for real BLE streaming integration using SDK (reflection to avoid compile issues)
    fun startBleStreaming(macAddr: String?) {
        if (streaming.get()) return
        if (macAddr.isNullOrBlank()) {
            addLog("未选择设备，无法启动 EMG 采集")
            return
        }
        try {
            val pmCls = Class.forName("com.goertek.protocol.ProtocolManager")
            val pm = pmCls.getMethod("getInstance").invoke(null)

            // register BluetoothManager with ProtocolManager
            try {
                val bmCls = Class.forName("com.goertek.ble.client.function.BLEManager")
                val bm = bmCls.getMethod("getInstance").invoke(null)
                pmCls.methods.firstOrNull { it.name == "registerBluetoothManager" }?.invoke(pm, bm)

                // ensure service started
                val startSvc = bmCls.methods.firstOrNull { it.name == "startService" && it.parameterTypes.size >= 1 }
                val cbType = startSvc?.parameterTypes?.getOrNull(1)
                val cbProxy = cbType?.let {
                    Proxy.newProxyInstance(it.classLoader, arrayOf(it)) { _, m, args ->
                        if (m.name.contains("on" , true)) {
                            val ok = args?.firstOrNull() as? Boolean
                            addLog("服务启动回调: ${m.name} ok=$ok")
                        }
                        null
                    }
                }
                if (startSvc != null) {
                    val res = if (cbProxy != null && startSvc.parameterTypes.size >= 2) {
                        startSvc.invoke(bm, context, cbProxy)
                    } else {
                        startSvc.invoke(bm, context)
                    }
                    addLog("startService 返回: $res")
                }

                // connect to device by MAC
                val conn = bmCls.methods.firstOrNull { it.name == "connectDevice" && it.parameterTypes.size == 1 }
                val connRes = conn?.invoke(bm, macAddr)
                addLog("connectDevice($macAddr) -> $connRes")
            } catch (e: Exception) {
                addLog("BLE 管理/连接失败: ${e.localizedMessage}")
            }

            // register notify listener for sensor/EMG data
            if (notifyListenerRef == null) {
                val regNotify = pmCls.methods.firstOrNull { it.name == "registerNotifyListener" }
                val pType = regNotify?.parameterTypes?.firstOrNull()
                if (regNotify != null && pType != null) {
                    val listener = Proxy.newProxyInstance(
                        pType.classLoader,
                        arrayOf(pType)
                    ) { _, m, args ->
                        if (m.name.equals("onNotify", ignoreCase = true) && args != null && args.isNotEmpty()) {
                            if (!streaming.get() || !measuring) return@newProxyInstance null
                            try {
                                val result = args[0]
                                val appData = result.javaClass.methods.firstOrNull { it.name.equals("getAppData", true) }?.invoke(result) as? ByteArray
                                val serviceId = result.javaClass.methods.firstOrNull { it.name.contains("getService", true) }?.invoke(result) as? Number
                                val commandId = result.javaClass.methods.firstOrNull { it.name.contains("getCommand", true) }?.invoke(result) as? Number
                                val serviceSensor = getConst(listOf("com.goertek.protocol.mbb.MBBCommand", "com.goertek.protocol.MBBCommand"), "SERVICE_ID_SENSOR")
                                val cmdSensorReport = getConst(listOf("com.goertek.protocol.mbb.MBBCommand", "com.goertek.protocol.MBBCommand"), "COMMAND_ID_SENSOR_REPORT_DATA")
                                val serviceEmg = getConst(listOf("com.goertek.protocol.mbb.MBBCommand", "com.goertek.protocol.MBBCommand"), "SERVICE_ID_EMG")
                                val cmdEmgUpload = getConst(listOf("com.goertek.protocol.mbb.MBBCommand", "com.goertek.protocol.MBBCommand"), "COMMAND_ID_UPLOAD_EMG_SAMPLE_DATA")

                                val isSensorReport = (serviceSensor == null || serviceId?.toInt() == serviceSensor) && (cmdSensorReport == null || commandId?.toInt() == cmdSensorReport)
                                val isEmgUpload = (serviceEmg != null && serviceId?.toInt() == serviceEmg) && (cmdEmgUpload == null || commandId?.toInt() == cmdEmgUpload)

                                if (isSensorReport && appData != null) {
                                    try {
                                        val helperCls = resolveClass(listOf("com.goertek.protocol.mbb.MBBAppDataHelper", "com.goertek.protocol.MBBAppDataHelper", "com.goertek.protocol.utils.MBBAppDataHelper"))
                                        if (helperCls == null) throw ClassNotFoundException("MBBAppDataHelper")
                                        val parser = helperCls.methods.firstOrNull { it.name == "parserSensorData" && it.parameterTypes.size == 1 }
                                        val parsed = parser?.invoke(null, appData)
                                        if (parsed != null) {
                                            val values: List<Float>? = extractEmgValues(parsed)
                                            if (!values.isNullOrEmpty()) {
                                                values.forEach { pushEmgSample(it) }
                                            } else if (appData.isNotEmpty()) {
                                                pushEmgSample((appData[0].toInt() and 0xFF) / 255f)
                                            }
                                        } else if (appData.isNotEmpty()) {
                                            pushEmgSample((appData[0].toInt() and 0xFF) / 255f)
                                        }
                                    } catch (e: Exception) {
                                        if (appData != null && appData.isNotEmpty()) {
                                            pushEmgSample((appData[0].toInt() and 0xFF) / 255f)
                                        }
                                        addLog("解析传感数据异常: ${e.localizedMessage}")
                                    }
                                } else if (isEmgUpload && appData != null) {
                                    try {
                                        val helperCls = resolveClass(listOf("com.goertek.protocol.mbb.MBBAppDataHelper", "com.goertek.protocol.MBBAppDataHelper", "com.goertek.protocol.utils.MBBAppDataHelper"))
                                        val emgDataCls = resolveClass(listOf("com.goertek.protocol.mbb.entity.EMGSampleData"))
                                        if (helperCls != null && emgDataCls != null) {
                                            val sample = emgDataCls.getConstructor().newInstance()
                                            val parserEmg = helperCls.methods.firstOrNull { it.name.equals("parserEMGData", true) && it.parameterTypes.size == 2 }
                                            parserEmg?.invoke(null, appData, sample)
                                            val emgDatas = sample.javaClass.methods.firstOrNull { it.name.equals("getEmgDatas", true) }?.invoke(sample) as? java.util.List<*>
                                            val values = mutableListOf<Float>()
                                            val now = System.currentTimeMillis()
                                            emgPacketCount += 1
                                            emgDatas?.forEach { any ->
                                                try {
                                                    val shortArr = any?.javaClass?.getDeclaredField("emgData")?.apply { isAccessible = true }?.get(any) as? ShortArray
                                                    if (shortArr != null) values.addAll(fromShortArray(shortArr))
                                                } catch (_: Exception) {}
                                            }
                                            if (values.isNotEmpty()) values.forEach { pushEmgSample(it) }
                                            // latency diagnostic: log every 50 packets
                                            if (emgPacketCount % 50 == 0) {
                                                val devTs = runCatching { sample.javaClass.methods.firstOrNull { it.name.equals("getDeviceTimeStamp", true) }?.invoke(sample) as? Number }.getOrNull()?.toLong() ?: 0L
                                                val locTs = runCatching { sample.javaClass.methods.firstOrNull { it.name.equals("getLocalTimeStamp", true) }?.invoke(sample) as? Number }.getOrNull()?.toLong() ?: 0L
                                                val deltaMs = if (lastEmgArrivalMs > 0) now - lastEmgArrivalMs else -1
                                                val devDelta = if (lastEmgDeviceTs > 0 && devTs > 0) devTs - lastEmgDeviceTs else -1
                                                lastEmgArrivalMs = now
                                                if (devTs > 0) lastEmgDeviceTs = devTs
                                                addLog("EMG包诊断: Δt=${deltaMs}ms devTs=${devTs} devΔ=${devDelta}ms size=${values.size} locTs=${locTs}")
                                            } else {
                                                lastEmgArrivalMs = now
                                            }
                                        }
                                    } catch (e: Exception) {
                                        addLog("EMG 数据解析异常: ${e.localizedMessage}")
                                    }
                                }
                            } catch (e: Exception) {
                                addLog("Notify 处理异常: ${e.localizedMessage}")
                            }
                        }
                        null
                    }
                    regNotify.invoke(pm, listener)
                    notifyListenerRef = listener
                }
            }

            // sync time & start EMG sample
            try {
                val helperCls = resolveClass(listOf("com.goertek.protocol.mbb.MBBAppDataHelper", "com.goertek.protocol.MBBAppDataHelper", "com.goertek.protocol.utils.MBBAppDataHelper"))
                val emgParamCls = resolveClass(listOf("com.goertek.protocol.mbb.entity.EMGSampleParam", "com.goertek.protocol.bean.EMGSampleParam"))
                if (helperCls == null || emgParamCls == null) throw ClassNotFoundException("EMG helper/param")

                val sync = helperCls.methods.firstOrNull { it.name.equals("syncEMGTime", true) }
                try {
                    if (sync != null) {
                        when (sync.parameterTypes.size) {
                            1 -> sync.invoke(null, null)
                            0 -> sync.invoke(null)
                            else -> sync.invoke(null, null)
                        }
                    }
                } catch (_: Exception) { }

                val param = emgParamCls.getConstructor().newInstance()
                val TYPE_EMG = getConst(listOf("com.goertek.protocol.mbb.entity.EMGSampleParam"), "TYPE_EMG") ?: 0
                emgParamCls.methods.firstOrNull { it.name.equals("setSensorOnOff", true) }?.invoke(param, TYPE_EMG, 0x01.toByte())
                // 0x01/0x02/0x03 -> 500/1000/2000 Hz
                emgParamCls.methods.firstOrNull { it.name.equals("setSamplingFreq", true) }?.invoke(param, TYPE_EMG, 0x03.toByte())
                emgParamCls.methods.firstOrNull { it.name.equals("setDataWay", true) }?.invoke(param, 0x00.toByte())
                emgParamCls.methods.firstOrNull { it.name.equals("setPersonName", true) }?.invoke(param, "user")
                emgParamCls.methods.firstOrNull { it.name.equals("setPersonRL", true) }?.invoke(param, 'R'.code.toByte())
                emgParamCls.methods.firstOrNull { it.name.equals("setActionNum", true) }?.invoke(param, 1.toByte())

                // choose startEMGSample method
                val start = helperCls.methods.firstOrNull { it.name.equals("startEMGSample", true) }
                if (start != null) {
                    try {
                        when (start.parameterTypes.size) {
                            2 -> start.invoke(null, param, null)
                            1 -> start.invoke(null, param)
                            else -> start.invoke(null, param, null)
                        }
                    } catch (e: Exception) {
                        addLog("startEMGSample 调用异常: ${e.localizedMessage}")
                    }
                } else {
                    addLog("未找到 startEMGSample 方法，可检查 AAR 接口")
                }
            } catch (e: Exception) {
                addLog("启动 EMG 采样异常: ${e.localizedMessage}")
            }

            streaming.set(true)
            addLog("开始订阅真实 EMG 数据")
        } catch (e: Exception) {
            addLog("启动真实 EMG 失败: ${e.localizedMessage}")
        }
    }

    fun stopBleStreaming() {
        if (!streaming.get()) return
        try {
            try {
                val helperCls = resolveClass(listOf("com.goertek.protocol.mbb.MBBAppDataHelper", "com.goertek.protocol.MBBAppDataHelper", "com.goertek.protocol.utils.MBBAppDataHelper"))
                val stop = helperCls?.methods?.firstOrNull { it.name.equals("stopEMGSample", true) }
                if (stop != null) {
                    try {
                        when (stop.parameterTypes.size) {
                            1 -> stop.invoke(null, null)
                            0 -> stop.invoke(null)
                            else -> stop.invoke(null, null)
                        }
                    } catch (e: Exception) {
                        addLog("stopEMGSample 调用异常: ${e.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                addLog("停止 EMG 采样异常: ${e.localizedMessage}")
            }

            try {
                val pmCls = Class.forName("com.goertek.protocol.ProtocolManager")
                val pm = pmCls.getMethod("getInstance").invoke(null)
                val unreg = pmCls.methods.firstOrNull { it.name == "unregisterNotifyListener" }
                if (unreg != null && notifyListenerRef != null) {
                    unreg.invoke(pm, notifyListenerRef)
                    notifyListenerRef = null
                }
            } catch (e: Exception) {
                addLog("注销通知监听异常: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            addLog("停止真实 EMG 失败: ${e.localizedMessage}")
        } finally {
            streaming.set(false)
            addLog("停止订阅真实 EMG 数据")
        }
    }

    // navigation: show measurement screen as full page when connected and currentScreen set
            if (currentScreen == "measurement" && isConnected) {
                // Measurement page: use LazyColumn to ensure proper scrolling and avoid nested unbounded constraints
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!measuring) {
                                Button(onClick = {
                                    val mac = selectedDeviceAddr
                                    if (mac.isNullOrBlank()) {
                                        addLog("未选择设备，无法开始测量")
                                        return@Button
                                    }
                                    measuring = true
                                    toastEnabled = false
                                    addLog("测量开始 (真实数据模式)")
                                    startBleStreaming(mac)
                                }) { Text("开始测量") }
                            } else {
                                Button(onClick = {
                                    measuring = false
                                    toastEnabled = true
                                    stopBleStreaming()
                                    addLog("测量停止")
                                }) { Text("停止测量") }
                            }

                            Button(onClick = { showParamDialog = true }) { Text("调节参数") }

                            Button(onClick = {
                                scope.launch {
                                    measuring = false
                                    stopBleStreaming()
                                    try {
                                        val ok = callSdkDisconnect()
                                        addLog("断开连接：$ok")
                                    } catch (e: Exception) {
                                        addLog("disconnect 调用异常: ${e.localizedMessage}")
                                    }
                                    streaming.set(false)
                                    toastEnabled = true
                                    devicesMap.clear(); devicesNameMap.clear();
                                    try { stopScan() } catch (_: Exception) {}
                                    resetLocalState()
                                    isConnected = false
                                    currentScreen = "home"
                                }
                            }) { Text("返回") }
                        }
                    }

                    item {
                        // EMG bar summary
                        EMGBar(strength = emgStrength, fatigue = emgFatigue, strengthIndex = strengthIndex, fatigueIndex = fatigueIndex, modifier = Modifier.fillMaxWidth().height(64.dp))
                    }

                    // single EMG visualization
                    item {
                        Text(text = "EMG", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        EMGChartSeries(title = "EMG", data = emgSeries.toList(), modifier = Modifier.fillMaxWidth().height(200.dp))
                    }
                }
            }

            // main screen footer (no inline logs)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 参数调节弹窗（全局放置，按钮控制 showParamDialog）
        if (showParamDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showParamDialog = false },
                title = { Text("调节参数") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("显示倍率", fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Strength Index: ${String.format("%.2f", strengthIndex)}", fontSize = 12.sp)
                                Slider(value = strengthIndex, onValueChange = { strengthIndex = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth())
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fatigue Index: ${String.format("%.2f", fatigueIndex)}", fontSize = 12.sp)
                                Slider(value = fatigueIndex, onValueChange = { fatigueIndex = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        Text("灵敏度", fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("FI Sensitivity: ${String.format("%.2f", fiSensitivity)}", fontSize = 12.sp)
                                Slider(value = fiSensitivity, onValueChange = { fiSensitivity = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth())
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("FL Sensitivity: ${String.format("%.2f", flSensitivity)}", fontSize = 12.sp)
                                Slider(value = flSensitivity, onValueChange = { flSensitivity = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        Text("校准", fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            androidx.compose.material3.TextButton(onClick = {
                                val list = emgSeries.toList()
                                val r = computeRms(list, windowSize = 128)
                                baselineRms = if (r > 0f) r else baselineRms
                                addLog("已采集基线 RMS=${String.format("%.4f", baselineRms ?: 0f)}")
                            }) { Text("基线RMS", fontSize = 12.sp) }

                            androidx.compose.material3.TextButton(onClick = {
                                val list = emgSeries.toList()
                                val r = computeRms(list, windowSize = 128)
                                maxRms = if (r > 0f) r else maxRms
                                if (r > 0f) observedMaxRms = kotlin.math.max(observedMaxRms, r)
                                addLog("已采集最大 RMS=${String.format("%.4f", maxRms ?: 0f)}")
                            }) { Text("最大RMS", fontSize = 12.sp) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            androidx.compose.material3.TextButton(onClick = {
                                val list = emgSeries.toList()
                                val mf = computeMedianFreqNormalized(list, windowSize = 128)
                                baselineMf = if (mf > 0f) mf else baselineMf
                                addLog("已采集基线 MF=${String.format("%.4f", baselineMf ?: 0f)}")
                            }) { Text("基线MF", fontSize = 12.sp) }

                            androidx.compose.material3.TextButton(onClick = {
                                val list = emgSeries.toList()
                                val mf = computeMedianFreqNormalized(list, windowSize = 128)
                                minMf = if (mf > 0f) mf else minMf
                                if (mf > 0f) observedMinMf = kotlin.math.min(observedMinMf, mf)
                                addLog("已采集最小 MF=${String.format("%.4f", minMf ?: observedMinMf)}")
                            }) { Text("最小MF", fontSize = 12.sp) }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showParamDialog = false }) { Text("关闭") }
                }
            )
        }
    }

    // end of MainScreen composable
}

