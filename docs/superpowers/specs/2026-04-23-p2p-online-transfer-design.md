# 在线快传 P2P 改造设计

## 目标

将“在线快传”从只创建取件码的会话模式改为浏览器到浏览器的 P2P 文件传输。后端继续归属 `transfer` 模块，只承担会话、取件码和信令中转职责；文件内容不经过后端。

## 范围

- 在线模式使用 WebRTC `RTCDataChannel` 传输文件分片。
- 复用现有 `/api/transfer/sessions`、`/api/transfer/sessions/lookup`、`/api/transfer/sessions/{sessionId}/join`、`/api/transfer/sessions/{sessionId}/signals`。
- 新增接收页，用户输入取件码后加入会话并接收文件。
- 离线快传保持现有服务器/对象存储中转逻辑，不在本轮重构。
- 第一版只配置公共 STUN，不引入 TURN 兜底；严格 NAT 下失败时展示明确错误。

## 数据流

1. 发送方选择在线模式和文件，前端创建后端 transfer session，获得 `sessionId` 和 `pickupCode`。
2. 发送方创建 `RTCPeerConnection` 和可靠 `RTCDataChannel`，生成 offer，通过后端 signal 接口发给 receiver。
3. 接收方输入取件码，lookup 后 join session，轮询 sender 信令，收到 offer 后创建 answer 并回传。
4. 双方通过同一 signal 接口交换 ICE candidate。
5. DataChannel 打开后，发送方发送 `file-start` 元数据、二进制分片、`file-end` 事件。
6. 接收方按文件组装 Blob，生成浏览器下载链接。

## 前端组件

- `frontend/src/lib/transfer.ts`：补齐 lookup/join/postSignal/pollSignals API。
- `frontend/src/lib/p2p-transfer.ts`：封装 WebRTC 发送端和接收端 runtime。
- `frontend/src/pages/TransferSend.tsx`：在线模式改为真实 P2P 发送流程；离线模式保留原逻辑。
- `frontend/src/pages/TransferReceive.tsx`：新增接收页，输入取件码并接收文件。
- `frontend/src/App.tsx` 和 `DashboardLayout.tsx`：挂载接收路由和入口。

## 错误处理

- 浏览器不支持 WebRTC 时阻止在线发送/接收并显示错误。
- 取件码无效、会话过期、对方未加入、连接失败、DataChannel 关闭都显示可理解状态。
- 发送端和接收端都支持清理连接，避免轮询和 peer connection 泄漏。

## 验证

- `cd frontend && npm run lint`
- `cd frontend && npm run build`
- 本机两个浏览器标签页手动验证：发送方创建在线取件码，接收方输入取件码，文件通过 DataChannel 接收并生成下载链接。
