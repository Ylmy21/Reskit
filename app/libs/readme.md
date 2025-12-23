# Researchkit SDK 说明

## 目录

- BLE 连接
- 事件（EventBus）
- 功能接口
    - Sensor 数据采集
    - OFN 相关
    - EMG 采集

---

## 1. BLE 连接

### 注册 BluetoothManager
```
ProtocolManager.getInstance().registerBluetoothManager(BluetoothManagerInterface interface)
```
输入：`interface`：直接传入 `BLEManager.getInstance()`

输出：`void`

说明：注册 BluetoothManager

### 启动服务
```
BLEManager.getInstance().startService(Context context，ManagerCallback cb)
```
输入：
- `context`：上下文
- `cb`：用于接收服务是否启动成功的回调函数

输出：`boolean`

说明：启动服务

### 根据 MAC 地址连接设备
```
BLEManager.getInstance().connectDevice()
```
输入：BLE MAC 地址

输出：`boolean`

说明：根据 mac 地址连接；连接状态通过 EventBus 发送。

### 依赖（在 `build.gradle` 中添加）
```
dependencies {
        api 'org.greenrobot:eventbus:3.1.1'
}
```

### Event 内容示例
- Code：`EventCode.CONNECTED_STATE_CODE`  // 连接状态变化
- Data：
    - `ConnectState.STATE_DISCONNECTED`  // 连接断开
    - `ConnectState.STATE_CONNECTING`    // 连接中
    - `ConnectState.STATE_CONNECTED`     // 连接成功
    - `ConnectState.STATE_DATA_READY`    // 读写通道建立完成

---

## 2. 功能接口

### Sensor 数据：开始采集
```
MBBAppDataHelper.startSensorCmd(int sensorType, int sampling, int latency, Boolean update, SendMbbCallbackInterface cb)
```
输入：
- `sensorType`：
    - `SensorDataInfo.HRM_RAW_SENSOR_ID`   // 心率原始数据
    - `SensorDataInfo.SPO2_RAW_SENSOR_ID`  // 血氧原始数据
    - `SensorDataInfo.HRM_ALGO_SENSOR_ID`  // 心率算法数据
    - `SensorDataInfo.SPO2_ALGO_SENSOR_ID` // 血氧算法数据
    - `SensorDataInfo.SENSOR_ACC_ID`       // ACC 数据
    - `SensorDataInfo.SENSOR_GYRO_ID`      // GYRO 数据
- `sampling`：采样率
- `latency`：延迟
- `upload`：直接传 `true`
- `SendMbbCallbackInterface`：callback

输出：`void`

说明：开始 sensor 数据采集

### Sensor 数据：停止采集
```
MBBAppDataHelper.stopSensorCmd(int sensorType, Boolean upload, SendMbbCallbackInterface cb)
```
输入：同上（`sensorType` 列表同开始采集）
- `upload`：直接传 `true`
- `SendMbbCallbackInterface`：callback

输出：`void`

说明：结束 sensor 数据采集

---

### 业务层数据接收（NotifyListener）
```
ProtocolManager.getInstance().registerNotifyListener(NotifyListener listener)
```
输入：`listener`：接收业务数据的回调函数

输出：`void`

说明：注册业务层蓝牙数据接收函数

示例 Listener：
```
OfnDataInfo mCurOfnInfo = null;
public void onNotify(ParseResultEvent result) {
        int serviceid = result.getServiceID();
        int commandid = result.getCommandID();
        byte[] appdata = result.getAppData();
        if (serviceid == MBBCommand.SERVICE_ID_SENSOR && commandid == MBBCommand.COMMAND_ID_SENSOR_REPORT_DATA) {
                MBBAppDataHelper.parserSensorData(appdata);
        } else if (serviceid == MBBCommand.SERVICE_ID_SENSOR && commandid == MBBCommand.COMMAND_ID_OFN_UPDATE) {
                if (mCurOfnInfo == null) {
                        mCurOfnInfo = new OfnDataInfo();
                }
                MBBAppDataHelper.parserOfnData(result, mCurOfnInfo);
                if ((mCurOfnInfo.getMode() == 3 && mCurOfnInfo.getMoveDatas().upDown == 0)
                        || mCurOfnInfo.getMode() == 2 && mCurOfnInfo.getIsLast() == 0) {
                        mCurOfnInfo = null;
                }
        }
}
```

注销 Listener：
```
ProtocolManager.getInstance().unregisterNotifyListener(NotifyListener listener)
```
输入：`listener`：接收业务数据的回调函数

输出：`void`

说明：注销业务层蓝牙数据接收函数

---

### 数据解析与 OFN
```
MBBAppDataHelper.parserSensorData(byte[] buf)
```
输入：`buf`：sensor 原始数据

输出：`SensorDataInfo`：解析后的 sensor 数据

说明：解析 sensor 数据

```
MBBAppDataHelper.setOfnMode(byte mode, SendMbbCallbackInterface cb)
```
输入：`mode`：
- `0` // normal
- `1` // coordinate
- `2` // image
- `cb`：callback

输出：`void`

说明：设置 ofn 模式

```
MBBAppDataHelper.parserOfnData(byte[] buf, OfnDataInfo info)
```
输入：
- `buf`：ofn 原始数据
- `OfnDataInfo`：用于接收解析后的 ofn 数据

输出：`void`

说明：解析 ofn 数据

---

### EMG 相关
```
MBBAppDataHelper.syncEMGTime(SendMbbCallbackInterface cb)
```
输入：`cb`：callback

输出：`void`

说明：同步设备时间

```
MBBAppDataHelper.startEMGSample(EMGSampleParam param, SendMbbCallbackInterface cb)
```
输入：`param`：
- `sensorOnOff`:
    - `[0]:0x01/0x00:emg_start/stop`
    - `[1]:0x01/0x00:imu_start/stop`
    - `[2]:0x01/0x00:ppg_start/stop`
    - `[3]:0x01/0x00:eda_start/stop`
- `samplingFreq`:
    - `[0]:emg 采样频率档位 <1~3>  1/2/3：500/1000/2000Hz`
    - `[1]:imu 采样频率档位 <1~5> 1/2/3/4/5：25/50/100/200/500Hz`
    - `[2]:ppg 采样频率档位 <1~5> 1/2/3/4/5：25/50/100/200/500Hz`
    - `[3]:eda 采样频率档位 <1~3>  1/2/3：5/10/20Hz`
- `dataWay`:
    - `0x00`: 数据在线传输，不存储
    - `0x01`: 数据在线传输（抽样），存储
    - `0x02`: 数据离线存储
- `personName`: 被采集者姓名（ASCII 码），用于离线缓存数据文件名称
- `personRL`: 被采集者左右手（76/82：左手/右手）
- `actionNum`: 动作编号 <1-99>
- `cb`：callback

输出：`void`

说明：开始采集数据

```
MBBAppDataHelper.stopEMGSample(SendMbbCallbackInterface cb)
```
输入：`cb`：callback

输出：`void`

说明：结束采集数据

---

## 当前应用内的数据流（Reskit App）

本节仅说明 App 侧的实际数据处理链路，不影响上方 SDK 接口文档。

- 通知分流：`NotifyListener.onNotify` 按 `serviceId/commandId` 分两条路径：
    - Sensor 通道（`SERVICE_ID_SENSOR` + `COMMAND_ID_SENSOR_REPORT_DATA` 且 `emgDataSource == "sensor"`）：调用 `parserSensorData(appData)`，从解析结果中尝试提取包含 "emg" 的字段/方法；若未提取到且包非空，用首字节兜底送入后续处理。
    - EMG 通道（`SERVICE_ID_EMG` + `COMMAND_ID_UPLOAD_EMG_SAMPLE_DATA` 且 `emgDataSource == "emg"`）：`parserEMGData(appData, EMGSampleData)` 读取每帧 `short[] emgData`，去零后进入处理，并定期输出包诊断（Δt、设备时间戳、非零比等）。
- 核心处理：所有样本统一经 `pushEmgSample`，流程为：
    - 原样本追加到原始点缓冲 `rawCapture`；
    - 带通滤波 20–450 Hz（fs=2000 Hz）；
    - 每 10 点抽样至约 200 Hz 写入 `emgSeries`，并以 20 ms 节流刷新图表；
    - 周期计算窗口特征（RMS、中值频率），生成疲劳 `emgFatigue` 与力量 `emgStrength`，供 UI 组件实时显示。
- 原始数据保存：
    - 包级原始 `short`（EMG 通道）存 `rawPacketCapture`；
    - 点级原始（所有进入处理的样本）存 `rawCapture`；
    - “导出原始数据” 按 `emg_raw_yyyyMMdd_HHmmss_<dur>s.csv` 写文件，优先用包级原始，否则用点级。
- 简单原始记录：可在测量页点击“开始原始记录/停止并保存原始记录”，直接把处理前的样本写入独立缓冲并导出同名规则的 CSV（不做滤波/抽样）。

### 当前已知输入侧问题
- 导出原始数据的数据源：
    - 优先来自 EMG 通道 `parserEMGData` 的 `short[] emgData`（保存在 `rawPacketCapture`）；
    - 若包级为空则使用所有进入 `pushEmgSample` 的点级样本（`rawCapture`）。
- 观测到的问题：
    - 数据中含有大量 0，非零比低（日志“nzRate”显示）；
    - 实测包到达频率/样本率偏离预期（日志诊断显示 Δt 和累积率与 2000 Hz 设定不符）。
- 相关日志：EMG 通道每 20 包输出诊断行，包含 Δt、设备时间戳 devTs/偏移 devΔ、非零比 nzRate、累计样本 total/nz。
- 建议排查：
    - 确认设备端采样频率/通道配置与 `startEMGSample` 参数一致；
    - 检查上行是否有压缩/抽样或协议头尾，需要按协议去零或重组帧；
    - 如设备支持，抓取一帧原始 appData 对照协议文档确认字节序与有效负载位置。


