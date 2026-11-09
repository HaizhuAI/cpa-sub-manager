# 中转站管家 (CpaSubManager)

CPA(CLIProxyAPI) / SUB(sub2api) 中转站的 Android 远程运维客户端。
用户只需站点地址 + 管理密钥即可管理站点，内置跨站数据汇总大屏。

## 构建

```bash
cd /d/Devin/cpa-sub-manager
/d/Devin/gradle/gradle-8.9/bin/gradle :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk (已用 cpasub.keystore 签名)
# 发布副本: D:\Devin\中转站管家-v1.0.0.apk
```

- AGP 8.5.2 + Kotlin 2.0.21 + Compose(ui 1.7.5 / material3 1.3.1) + OkHttp 4.12 + Gson 2.10.1
- minSdk 26 / targetSdk 35 / compileSdk 35；release lint 已禁用（SDK XML v4 与 AGP 不兼容）
- keystore: `cpasub.keystore` / 别名 `cpasub` / 密码 `cpasub123`

## 对接的管理 API

### SUB 站 (sub2api, Wei-Shaw/sub2api)
- 认证头: `x-api-key: <管理API密钥>`（后台「系统设置→管理API密钥」生成，支持 `adm_sc_*`）
- Base: `{host}/api/v1/admin`，响应封套 `{code,message,data}`
- 用到: `dashboard/stats|trend|models|groups`、`accounts(含 test/refresh/clear-error/schedulable/recover-state)`、`users`、`usage`、`ops/*`
- 参考: `_refs/sub2api/backend/internal/server/routes/admin.go`

### CPA 站 (CLIProxyAPI, router-for-me/CLIProxyAPI)
- 认证头: `Authorization: Bearer <secret-key>` + `X-Management-Key`；需 `remote-management.allow-remote: true`
- 优先 v8 `/v8/management`，404 回落 v0 `/v0/management`
- 用到: `config`、`credentials(status/refresh/verify/delete)`、`observability/usage/queue`(弹出式!)、`observability/usage/api-keys`、`observability/logs`、`routing/cooldown/reset`、`v0/management/api-keys`
- 参考: `_refs/CLIProxyAPI/internal/api/server_management_v8.go`、`docs/management-api-v8.md`

## 本地账本

`UsageLedger`(filesDir/ledger_<stationId>.json)：
- CPA usage-queue 是**消费式队列**，App 每次刷新 drain ≤500 条按 timestamp 聚合到日桶（requests/failed/input/output/total/models）
- SUB 直接用服务端 30d trend 覆盖写入 + models 汇总区
- 大屏跨站按日期合并展示

## 结构

```
app/src/main/java/com/relay/manager/
  MainActivity.kt          单 Activity + 状态导航(Stations/Dashboard/Detail)
  data/Models.kt           Station/StationHealth/DayStat
  data/StationStore.kt     SharedPreferences+JSON 站点存储
  data/UsageLedger.kt      每日用量本地账本
  net/ApiClient.kt         OkHttp 封装(SUB:x-api-key / CPA:Bearer)
  net/Sub2Api.kt           sub2api 管理接口
  net/CpaApi.kt            CLIProxyAPI v8/v0 双栈接口
  ui/Common.kt             StatCard/GlassCard/SectionTitle 等大屏组件
  ui/Charts.kt             Canvas 自绘 TrendChart/DayBars/RankBars/DonutChart
  ui/StationsScreen.kt     站点列表 + 添加/编辑对话框
  ui/DashboardScreen.kt    跨站汇总大屏(60s 自动刷新)
  ui/SubDetailScreen.kt    SUB: 概览/账号/用户/记录
  ui/CpaDetailScreen.kt    CPA: 概览/凭证/密钥/日志
```

注意: 本机无嵌套虚拟化，x86/arm64 模拟器均无法启动，验证靠编译+签名+aapt 静态检查。
_refs/ 下是两个上游仓库的浅克隆，仅供 API 参考。
