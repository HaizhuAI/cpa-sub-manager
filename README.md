# 中转站管家 · Relay Station Manager

一个用于远程运维 **CPA (CLIProxyAPI)** 与 **SUB (sub2api)** 两类 API 中转站的 Android 客户端。

只需 **站点地址 + 管理密钥**，即可在手机上完成站点健康监控、账号/凭证管理、客户端密钥管理与每日 Token 流出数据统计。

## 功能

### 数据大屏（跨站汇总首页）
- KPI 卡片：今日请求数、今日 Token 流出、近 30 日 Token、在线站点/异常账号
- 近 30 日「请求 × Token」双折线趋势图、每日请求量柱状图
- 各站点 Token 流出对比、模型 Token 排行 TOP10、站点健康状态一览
- 60 秒自动刷新；数字滚动/图表生长入场动画

### SUB 站管理（sub2api）
- 概览：今日请求/Token/费用、累计用量、用户数、账号健康、RPM/TPM
- 账号：列表、测试、刷新、清除错误、停用/开启调度、恢复状态
- 用户：搜索、余额查看、启用/禁用
- 用量记录流水

### CPA 站管理（CLIProxyAPI）
- 概览：今日请求/Token（本地账本）、版本与最新版本、客户端 Key 用量
- 凭证：列表（成功/失败计数）、启用/停用、刷新、验证、删除、一键重置冷却、刷新全部凭证
- 密钥：客户端 API Key 列表、新增、删除
- 日志：应用日志最近 300 行
- v8 管理 API 优先，自动回落 v0 兼容路径

### 本地用量账本
CPA 的 `usage-queue` 为消费式弹出队列，App 每次刷新抽取 ≤500 条记录，按日期聚合落盘（请求/失败/输入/输出/总 Token/按模型拆分），形成跨会话持久的每日明细。

## 对接要求

| 站类型 | 服务端 | 认证方式 | 需要的配置 |
|---|---|---|---|
| CPA | [CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI) | `Authorization: Bearer <key>` | config 中 `remote-management.allow-remote: true` 与 `remote-management.secret-key`（或环境变量 `MANAGEMENT_PASSWORD`） |
| SUB | [sub2api](https://github.com/Wei-Shaw/sub2api) | `x-api-key: <key>` | 后台「系统设置 → 管理 API 密钥」生成（支持 `adm_sc_*` 受限密钥或全局密钥） |

支持 `http://` 与 `https://` 站点；密码仅存于本机 SharedPreferences。

## 构建

环境：JDK 17+、Android SDK（compileSdk 35）、Gradle 8.9+

```bash
gradle :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

签名：若存在 `cpasub.keystore`（或环境变量 `RELAY_KEYSTORE`/`RELAY_STORE_PASS`/`RELAY_ALIAS`/`RELAY_KEY_PASS` 指定的 keystore）则用其签名，否则回落 debug 签名。

自行生成发布 keystore：

```bash
keytool -genkeypair -keystore cpasub.keystore -alias cpasub -keyalg RSA -keysize 2048 -validity 10950
```

## 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Material3），Canvas 自绘图表（无三方图表依赖）
- OkHttp 4 + Gson
- minSdk 26 / targetSdk 35

## 项目结构

```
app/src/main/java/com/relay/manager/
  MainActivity.kt          单 Activity + AnimatedContent 导航
  data/                    Station/Store/UsageLedger（本地日度账本）
  net/                     ApiClient / Sub2Api / CpaApi（v8/v0 双栈）
  ui/                      大屏组件、自绘图表、四个页面
```

## License

MIT
