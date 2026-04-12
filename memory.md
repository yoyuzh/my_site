## 任务目标
将项目主线稳定在“网盘 + 快传 + 管理台”，并按 Cloudreve gap 计划持续补齐上传会话、分享、任务、存储策略和管理治理能力；当前优先把管理台与核心业务链路做成可持续迭代、可发布、可验证状态。

## 已做决策
| 决策 | 理由 | 排除的方案及原因|
|---|---|---|
| 产品主线固定为“网盘 + 快传 + 管理台” | 与当前用户场景和已有代码投资一致，迭代效率最高 | 继续保留旧教务主线：目标已偏离，维护成本高 |
| 网盘采用“StoredFile 逻辑层 + FileBlob/FileEntity 物理层” | 支持去重、复用、后续版本化和迁移任务 | 继续把对象 key 绑定用户路径：复制/导入会重复写对象，浪费存储 |
| 上传主链路切到 v2 upload session（按策略自动选 PROXY / DIRECT_SINGLE / DIRECT_MULTIPART） | 后端能力清晰、前端可按模式统一处理，便于扩展多存储策略 | 继续长期混用旧 `/api/files/upload/**`：能力分散、演进困难 |
| 管理台系统设置改为整页可编辑（`PUT /api/admin/settings`） | 前端可一次提交全部系统设置，管理行为更完整 | 只保留零散 patch 接口：只能改少数字段，治理效率低 |
| 注册邀请码是否必填由可编辑设置驱动 | 让运营策略可直接在管理台调整并立即生效 | 写死在配置文件：每次变更都要改配置和重启 |
| 前端管理台路径与命名统一为 `operations-admin` 语义化结构 | 降低页面/API 组织混乱，便于多人协作和后续拆分 | 保留旧命名（如 `oauthapps.tsx`、`users-list.tsx`）：语义不清、引用维护成本高 |

## 待解决问题
- 系统设置目前是运行态内存保存，重启后会回到默认值；需要升级为数据库持久化。
- 前端构建仍有 chunk size warning，暂不阻塞发布，但需要后续拆包优化。
- Android 构建链路依赖镜像源稳定性，后续升级 Capacitor 时需复核镜像配置。
- `front/README.md` 仍偏旧模板，和当前仓库主 README 存在信息重复与偏差。

##最新进度（前端后端各一段）
前端：管理台完成一轮结构化重命名与目录收口，`operations-admin` 下页面与 API 分层更清晰；“系统设置”页面已改为整页可编辑表单，支持全字段保存、邀请码轮换与复制，`npm run lint`（`tsc --noEmit`）已通过。

后端：已新增 `PUT /api/admin/settings` 并打通 `site/registration/userSession/transfer/mediaProcessing/queue/appearance/server` 全量写入，同时 `GET /api/admin/settings` 各分组 `writeSupported` 统一为 `true`；注册流程已接入 `inviteCodeRequired` 开关，相关管理设置测试集（`AdminMutableSettingsServiceTest`、`AdminConfigSnapshotServiceTest`、`AdminControllerIntegrationTest`）已通过。
