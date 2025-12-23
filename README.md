# Reskit Demo (Android)

## update2

本次更新聚焦 EMG strength 计算与参数设置，图表保持 200 Hz 显示、约 5 秒窗口不变。

- 默认归一化上限：`strengthMax` 初始值改为 `200`，用于 EMGBar 的归一化分母。
- Strength 计算优化：
  - 更新频率约 50 Hz（200 Hz 输入下每 4 个样本计算一次）。
  - 窗口长度 50：以最近 50 个样本为一窗。
  - 计算方式：先减去窗口均值，取绝对偏差；然后删除 5 个最大和 5 个最小偏差，最后对剩余偏差求平均，降低突峰影响。
  - 后台计算：在 `Dispatchers.Default` 协程中执行，避免阻塞 UI 主线程。
- 图表：仍按 200 Hz 抽稀后显示最近 5 秒；支持“平移至 0（用当前窗口中值）”。

### 参数调节按钮使用说明

- `调节参数`（测量页右上区域按钮）：打开参数弹窗。
  - `显示倍率`：包含两条滑杆
    - `Strength Index`：强度显示倍率（线性因子）。
    - `Fatigue Index`：疲劳显示倍率（线性因子）。
  - `数据源`：单选按钮
    - `parserEMGData` 或 `parserSensorData`，切换数据解析来源。
  - `波形平移至0（用当前窗口中值）`：复选框
    - 勾选后按当前显示窗口的中值对波形做基线校正，使波形围绕 0。
  - `EMG Bar 最大值 (0~2000)`：输入框
    - 设置归一化上限 `strengthMax`（数值范围 0~2000；初始 200）。

## 概览
- 基于 Jetpack Compose 的 BLE 测试/演示应用。
- 引用本地 AAR `app/libs/ResearchKit_SDK_1.00.03.aar`，其中包含 `BluetoothLeService` 等 SDK 逻辑。
- 已在 `app/build.gradle.kts` 添加 `org.greenrobot:eventbus:3.3.1` 以满足 AAR 的运行时依赖。
- 主界面：仅保留 “选择设备” 与 “查看日志” 两个入口；测量页使用 `LazyColumn` 以保证可滚动并展示可视化组件。

## 主要功能
- **设备选择对话框**：
  - 打开时自动请求必要的蓝牙权限并开始扫描。
  - 支持切换“仅显示有名称的设备 / 显示所有设备”。
  - 选择设备后会尝试启动 SDK 服务并连接，连接成功后切换到测量页。
- **测量页（可滚动）**：
  - 顶部可断开连接并返回主界面。
  - 开始/停止测量按钮（模拟数据生成与可视化）。
  - EMGBar 显示强度/疲劳；滑杆可调节 Strength/Fatigue index。
  - 校准按钮：基线/最大 RMS、基线/最小 MF。
  - FI/FL 灵敏度调节滑杆。
  - 多通道折线图（EMG/HRM_RAW/SPO2），每个 160dp 高度。
- **日志**：
  - 弹窗显示运行日志，支持清除、复制到剪贴板。

## 构建与运行
前置：JDK 11+、Android SDK 已配置（Windows PowerShell 示例）。
```powershell
cd E:\projects\andr_s\Reskit
.\u005cgradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
首次安装前建议卸载旧包：
```powershell
adb uninstall com.example.reskit
```

## 已知问题 / 排查
- **NoClassDefFoundError: org.greenrobot.eventbus.EventBus**
  - AAR 的 `BluetoothLeService` 依赖 EventBus；当前 Gradle 依赖已添加。如果仍然崩溃，请确认安装的 APK 内含 EventBus（解压 `classes*.dex` 搜索 `Lorg/greenrobot/eventbus/EventBus;`）。
  - 如依旧缺类，可将 `eventbus-3.3.1.jar` 放入 `app/libs/` 并在 `app/build.gradle.kts` 使用 `implementation(files("libs/eventbus-3.3.1.jar"))` 强制打包。
- **Compose 测量页滚动问题**
  - 已改为 `LazyColumn`，避免嵌套 `verticalScroll` 的无限高度约束。如果再遇到约束异常，检查父布局是否给出无界高度。
- **数据计算待优化 / 长期运行可能卡顿**
  - 当前每 20 个样本计算一次 RMS/疲劳，仍存在长期运行卡死风险，后续需优化计算负载与缓存管理。
- **Bar 显示频率高、可能不稳定**
  - EMG Bar 刷新频率较高，视觉上可能抖动；后续可增加平滑或降低刷新频率。
- **设备可能拒绝后续访问**
  - 某些情况下连接后设备返回 onFail（args=[1]），需要重启仪器再测；请在仪器侧确认采样参数与状态。

## Update Log (新增说明，不改动旧内容)
- **数据流最新调整（与 app/libs/readme.md 同步）**：
  - 通知分流：Sensor 通道按小端拆分 `appData` 为 short 流，每 9 点取组内首个非零；EMG 通道 `parserEMGData` 后同样 9 取 1，输出诊断（Δt、devTs、nzRate）。
  - 核心处理：先写入原始点缓冲 `rawCapture`，再用约 300ms 中值扣除基线后带通滤波 20–450 Hz（fs=1000 Hz 假设）；图表每 5 点抽样（~200 Hz），20 ms 节流；指标每 20 点（~50 Hz）用全速序列算 RMS/中值频率生成 `emgStrength`/`emgFatigue`。
  - 原始数据导出：导出 `rawCapture`（约 1000 Hz 单点流）到 `emg_raw_yyyyMMdd_HHmmss_<dur>s.csv`；包级真实点仍存 `rawPacketCapture`（EMG 9 取 1）。
  - 简单原始记录：测量页“开始/停止并保存原始记录”按钮，直接存处理前样本，不做滤波/抽样。
- **UI & 控件布局调整**：测量页顶部为“开始/停止”“调节参数”“返回”三按钮同排；参数调节集中在弹窗，释放更多空间给 EMG Bar 与波形。
- **性能与刷新节流**：EMG 指标计算节流（每 40 个样本），图表/状态更新加 30ms 节流；强度采用窗口内绝对值前 8 点均值，减少抖动。
- **后台计算**：RMS/频域计算转至 `Dispatchers.Default`，主线程仅做快照与结果回写，降低长时间运行卡顿风险。
- **可视化尺寸**：EMG Bar 高度 64dp，EMG 波形 200dp，提升可读性。

## 文件位置速览
- `app/src/main/java/com/example/reskit/MainActivity.kt`：主要 UI 和 BLE 流程逻辑。
- `app/build.gradle.kts`：应用模块依赖、Compose 启用、AAR 引用、EventBus 依赖。
- `app/libs/ResearchKit_SDK_1.00.03.aar`：本地 SDK。

## 概览
- 基于 Jetpack Compose 的 BLE EMG 测试/演示应用。
- 引用本地 AAR `app/libs/ResearchKit_SDK_1.00.03.aar`（包含 BLE/Protocol/EMG API）。
- 依赖 `org.greenrobot:eventbus:3.3.1` 以满足 AAR 的运行时依赖。
- 主界面：仅保留 “选择设备” 与 “查看日志” 两个入口；测量页为滚动布局，包含控制/指标/波形。 

## 使用指南（操作步骤）
1. 打开应用，点击 **“选择设备”**：自动请求蓝牙权限并开始扫描。
2. 在弹窗列表中选择目标设备（显示名+MAC），连接成功后自动进入测量页。
3. 在测量页点击 **“开始测量”** 启动 EMG 采集；点击 **“停止测量”** 结束采集。
4. 如需断开并返回主页，点击 **“断开连接并返回”**（会停止采集并清理监听）。
5. 运行日志：主页点击 **“查看日志”** 可查看/清除/复制日志，便于排查回调与错误。

## 界面与控件说明
- **主页按钮**
  - `选择设备`：打开扫描弹窗并开始 BLE 扫描。
  - `查看日志`：弹出日志对话框，可清空或复制到剪贴板。
- **测量页按钮**
  - `开始测量` / `停止测量`：控制 EMG 采集的开始/停止。
  - `断开连接并返回`：停止采集、注销监听，返回主页并清空设备列表。
- **指标滑杆**
  - `Strength Index`：放大/缩小强度条显示（乘法系数）。
  - `Fatigue Index`：放大/缩小疲劳条显示（乘法系数）。
  - `FI Sensitivity`：频域衰减对疲劳的权重；
  - `FL Sensitivity`：幅值上升对疲劳的权重。
- **校准按钮**
  - `校准基线/最大 RMS`：记录当前 RMS 为基线或最大值，影响强度/疲劳归一化。
  - `校准基线/最小 MF`：记录当前中值频率为基线或最小值，影响疲劳归一化。
- **可视化**
  - EMG Bar：显示实时强度/疲劳（0~1）。
  - EMG 波形：最新 2000 点（约 1s@2000Hz 或 4s@500Hz），纵轴 [-1,1]。
  - 其他传感器占位图：HRM_RAW/SPO2 等（如数据为空则保持空图）。

## 当前算法（简述）
- **数据来源**：
  - 使用 `ProtocolManager` + `BLEManager` 连接；
  - 通过 `MBBAppDataHelper.startEMGSample(EMGSampleParam, callback)` 开启采集；
  - 收到 EMG 上传包时，用 `parserEMGData(byte[], EMGSampleData)` 解析，取每包 `short[] emgData` 推入缓冲；
  - 缓冲上限 2000 点，停止时清理监听。
- **强度/疲劳计算**（每 20 个样本计算一次，降低 CPU 占用）：
  - 强度：直接取当前样本（未剪裁）作为瞬时强度展示；RMS（窗口 64）用于疲劳归一化的幅值部分。
  - 疲劳：频域中值频率（窗口 128 的简单 DFT）+ RMS 双因素归一化；用户可用 `FI/FL Sensitivity` 调节权重；`Strength/Fatigue Index` 作为显示倍率。
  - 校准：
    - 基线/最大 RMS：影响幅值归一化区间。
    - 基线/最小 MF：影响频率归一化区间。
- **日志与诊断**：
  - 回调日志：`syncEMGTime/startEMGSample/stopEMGSample` 会打印 onSuccess/onFail。
  - 数据诊断：周期性输出 EMG 包平均到达间隔、样本数、设备时间戳增量；若无样本会提示。


Optimizing tool selection...可以直接用 Gradle 打包：

调试包（免签名配置）：
```powershell
cd E:\projects\andr_s\Reskit
.\gradlew.bat assembleDebug
```
生成的 APK 在 app-debug.apk，可用：
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

发布包（需签名）：
```powershell
cd E:\projects\andr_s\Reskit
.\gradlew.bat assembleRelease
```


## 当前应用内的数据流（Reskit App）

本节仅说明 App 侧的实际数据处理链路，不影响上方 SDK 接口文档。

- 通知分流：`NotifyListener.onNotify` 按 `serviceId/commandId` 分两条路径：
    - Sensor 通道（`SERVICE_ID_SENSOR` + `COMMAND_ID_SENSOR_REPORT_DATA` 且 `emgDataSource == "sensor"`）：直接按小端将 `appData` 切分为 `short` 流，每 9 点一组仅保留组内首个非零样本，送入处理。
    - EMG 通道（`SERVICE_ID_EMG` + `COMMAND_ID_UPLOAD_EMG_SAMPLE_DATA` 且 `emgDataSource == "emg"`）：`parserEMGData(appData, EMGSampleData)` 读取每帧 `short[] emgData`，同样 9 取 1 只留首个非零样本，定期输出包诊断（Δt、设备时间戳、非零比等）。
- 核心处理：所有样本统一经 `pushEmgSample`，流程为：
    - 原样本追加到原始点缓冲 `rawCapture`；
    - 先用约 300 ms 中值基线扣除，再带通滤波 20–450 Hz（fs=1000 Hz 假设）；
    - 图表抽样：每 5 点追加一次至 `emgSeries`（~200 Hz），并以 20 ms 节流刷新；
    - 指标：每 20 点（约 50 Hz）用全速序列计算 RMS/中值频率，生成疲劳 `emgFatigue` 与力量 `emgStrength`。
- 原始数据保存：
    - 包级原始 `short`（EMG 通道 9 取 1 后的真实点）存 `rawPacketCapture`；
    - 点级原始（所有进入处理的真实单点流，约 1000 Hz）存 `rawCapture`；
    - “导出原始数据” 按 `emg_raw_yyyyMMdd_HHmmss_<dur>s.csv` 写文件，导出 `rawCapture`（1000 Hz 单点序列）。
- 简单原始记录：可在测量页点击“开始原始记录/停止并保存原始记录”，直接把处理前的样本写入独立缓冲并导出同名规则的 CSV（不做滤波/抽样）。

### 当前已知输入侧问题
- 观测到的问题：
    - 数据中含有大量 0，非零比低（日志“nzRate”显示）；
    - 实测包到达频率/样本率偏离预期（当前按 ~1000 Hz 假设，日志可见 Δt 与累积率）。
- 相关日志：EMG 通道每 20 包输出诊断行，包含 Δt、设备时间戳 devTs/偏移 devΔ、非零比 nzRate、累计样本 total/nz。
- 建议排查：
    - 确认设备端采样频率/通道配置与 `startEMGSample` 参数一致；
    - 检查上行是否有压缩/抽样或协议头尾，需要按协议去零或重组帧；
    - 如设备支持，抓取一帧原始 appData 对照协议文档确认字节序与有效负载位置。

