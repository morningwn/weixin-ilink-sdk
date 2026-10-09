# 微信 iLink 后端 API 协议

本文说明 `openclaw-weixin` 渠道插件使用的 HTTP JSON 协议，面向实现或接入兼容微信后端的开发者。

**上游依据：** [Tencent/openclaw-weixin 的 protocol.md](https://github.com/Tencent/openclaw-weixin/blob/main/docs/protocol.md)
，本文内容以该仓库当前客户端源码为准。

## 阅读约定

本文明确区分三类信息：

- **字段与示例**：客户端类型和请求构造器表示的线上数据格式。
- **当前客户端行为**：插件目前发送、接受或重试的方式。
- **集成建议**：对其他实现的建议，并非服务端契约。

客户端类型及行为不等于完整服务端契约：TypeScript 中字段可选，不代表服务端接受缺少该字段的请求；类型中存在字段，也不代表插件实现了对应功能。除非特别说明，JSON
示例仅展示部分字段，不保证是最小合法请求或完整响应。应使用真实值替换占位符。示例 `channel_version` 为 `2.4.8`，实际值由包元数据提供。

上游参考源码：[`src/api/api.ts`](https://github.com/Tencent/openclaw-weixin/blob/main/src/api/api.ts)、[
`src/api/types.ts`](https://github.com/Tencent/openclaw-weixin/blob/main/src/api/types.ts)、[
`src/auth/login-qr.ts`](https://github.com/Tencent/openclaw-weixin/blob/main/src/auth/login-qr.ts)。这些源码未体现的服务端要求需要单独验证。

## 1. 范围与传输

- API 使用 HTTPS 上的 JSON。
- API 路径相对于当前 API 基座地址。
- 常规 API 使用 `POST`；二维码状态轮询使用 `GET`。
- JSON 中的字节字段使用 base64 字符串表示。
- 默认 API 基座地址：`https://ilinkai.weixin.qq.com`。
- 默认 CDN 基座地址：`https://novac2c.cdn.weixin.qq.com/c2c`。

二维码流程从固定 API 基座开始。服务端返回重定向后，客户端可在返回的主机继续轮询二维码状态。CDN 媒体若提供完整 URL，应优先使用该 URL，而不是按 CDN
基座拼接。

## 2. 认证与公共元数据

### 2.1 请求头

| Header                    | 值                                                 |
|---------------------------|----------------------------------------------------|
| `Content-Type`            | JSON POST 请求使用 `application/json`              |
| `AuthorizationType`       | `ilink_bot_token`                                  |
| `Authorization`           | 已认证 Bot API 请求使用 `Bearer <bot_token>`       |
| `X-WECHAT-UIN`            | 随机 `uint32` 十进制文本的 base64 编码             |
| `iLink-App-Id`            | 插件应用 ID，当前为 `bot`                          |
| `iLink-App-ClientVersion` | 按 `0x00MMNNPP` 编码的插件版本，以十进制字符串发送 |
| `SKRouteTag`              | 部署方配置的可选路由标签                           |

**当前客户端行为：**

- 二维码状态轮询发送应用请求头（`iLink-App-Id`、`iLink-App-ClientVersion`、可选 `SKRouteTag`），不发送 `AuthorizationType`、`Authorization` 或
  `X-WECHAT-UIN`。
- 获取二维码的 POST 请求使用 JSON POST 请求头（包括 `AuthorizationType`、`X-WECHAT-UIN`），但不携带 `Authorization` 和 `base_info`。

### 2.2 `base_info`

已认证的 Bot POST 请求携带：

```json
{
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

`channel_version` 是插件版本。可选 `bot_agent` 是通过 `channels.openclaw-weixin.botAgent` 配置、经清理的可观测性标识；它不参与认证或路由。

**当前客户端行为：** `buildBaseInfo()` 始终提供这两个字段。 `bot_agent` 默认 `OpenClaw`；自定义值采用可带注释的 ASCII `Name/Version`token，清理后最多
256 字节。无效 token 会移除，结果为空时回退为 `OpenClaw`。同一插件实例下该声明由所有账号共享。

### 2.3 返回值和错误

响应类型可能包含 `ret`、`errcode`、`errmsg`，但并非每个接口均有这些字段。存在 `ret` 时，`ret: 0` 表示成功。

| 操作                         | 当前客户端对业务响应的处理                                                                             |
|------------------------------|--------------------------------------------------------------------------------------------------------|
| `getUpdates`                 | 检查非零 `ret` 或 `errcode`。任一字段为 `-14` 时暂停账号会话 1 小时；其他失败使用监控器重试/退避策略。 |
| `sendMessage`                | 非零 `ret` 会抛出异常；缺少 `ret` 时不触发检查。                                                       |
| `getUploadUrl`               | 要求非空 `upload_full_url` 或 `upload_param`，不显式检查 `ret`。                                       |
| `getConfig`                  | 仅在 `ret === 0` 时接受配置；失败时使用缓存/默认配置并安排再次尝试。                                   |
| `sendTyping`                 | 包装层不解析响应体或检查业务返回码。                                                                   |
| `notifyStart` / `notifyStop` | 将非零 `ret` 或请求失败记录为警告，不阻断启动或停止。                                                  |

JSON 请求封装在 HTTP 状态码非成功时抛出异常。 `getUpdates` 将超时或外部取消转换为空结果；取消会让监控器退出而非继续轮询。二维码状态轮询会把请求失败转换为
`wait`。

**集成建议：** 分开处理 HTTP 状态和业务返回码；按操作定义重试策略；诊断日志中脱敏凭据。

## 3. 二维码登录

### 3.1 获取二维码

```http
POST /ilink/bot/get_bot_qrcode?bot_type=3
```

```json
{
  "local_token_list": []
}
```

`local_token_list` 可为空；当前客户端最多发送本地保存的最近 10 个 Bot token。

```json
{
  "qrcode": "<qrcode 值>",
  "qrcode_img_content": "<用于展示二维码的 URL 或内容>"
}
```

### 3.2 轮询二维码状态

```http
GET /ilink/bot/get_qrcode_status?qrcode=<经编码的 qrcode>
```

服务端要求验证时，客户端额外携带：

```http
GET /ilink/bot/get_qrcode_status?qrcode=<经编码的 qrcode>&verify_code=<经编码的验证码>
```

```json
{
  "status": "confirmed",
  "bot_token": "<bot token>",
  "ilink_bot_id": "<bot 账号 ID>",
  "baseurl": "https://<api-host>",
  "ilink_user_id": "<扫码用户 ID>"
}
```

| 状态                  | 含义                                      |
|-----------------------|-------------------------------------------|
| `wait`                | 等待扫码或状态变化                        |
| `scaned`              | 已扫码，验证继续                          |
| `confirmed`           | 登录成功，可保存凭据                      |
| `expired`             | 二维码过期，可刷新                        |
| `need_verifycode`     | 用户须输入展示的验证码                    |
| `verify_code_blocked` | 验证失败次数过多，应刷新二维码或停止      |
| `scaned_but_redirect` | 存在 `redirect_host` 时，在该主机继续轮询 |
| `binded_redirect`     | Bot 已绑定到当前 OpenClaw 实例            |

## 4. Bot API

### 4.1 接口概览

| 操作           | 方法与路径                        | 用途                          |
|----------------|-----------------------------------|-------------------------------|
| `getUpdates`   | `POST /ilink/bot/getupdates`      | 长轮询接收入站消息            |
| `getUploadUrl` | `POST /ilink/bot/getuploadurl`    | 获取媒体上传参数              |
| `sendMessage`  | `POST /ilink/bot/sendmessage`     | 发送消息                      |
| `getConfig`    | `POST /ilink/bot/getconfig`       | 获取账号配置和输入状态 ticket |
| `sendTyping`   | `POST /ilink/bot/sendtyping`      | 设置或取消输入状态            |
| `notifyStart`  | `POST /ilink/bot/msg/notifystart` | 通知后端客户端已启动          |
| `notifyStop`   | `POST /ilink/bot/msg/notifystop`  | 通知后端客户端已停止          |

除非另有说明，本节请求均使用公共请求头、Bot 授权和 `base_info`。

### 4.2 `getUpdates`

```http
POST /ilink/bot/getupdates
```

```json
{
  "get_updates_buf": "",
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

客户端发送前一次响应的 `get_updates_buf`；首次请求或重置后发送空字符串。服务端应保持请求，直至有消息可用或长轮询超时。

```json
{
  "ret": 0,
  "msgs": [],
  "get_updates_buf": "<下一游标>",
  "longpolling_timeout_ms": 35000
}
```

| 字段                     | 类型              | 说明                           |
|--------------------------|-------------------|--------------------------------|
| `ret`                    | `number`          | `0` 表示成功                   |
| `errcode`                | `number`          | 可选业务错误码                 |
| `errmsg`                 | `string`          | 可选错误描述                   |
| `msgs`                   | `WeixinMessage[]` | 入站消息                       |
| `get_updates_buf`        | `string`          | 下次请求发送的游标             |
| `longpolling_timeout_ms` | `number`          | 可选的服务端建议超时，单位毫秒 |

`sync_buf` 仍存在于 TypeScript 类型中但已废弃。当前请求构造器只发送 `get_updates_buf`，监控器也不会把 `sync_buf` 用作响应回退游标；仅当返回的
`get_updates_buf` 非空时保存它。

### 4.3 `sendMessage`

```http
POST /ilink/bot/sendmessage
```

```json
{
  "msg": {
    "from_user_id": "",
    "to_user_id": "<目标用户 ID>",
    "client_id": "<客户端生成的 ID>",
    "message_type": 2,
    "message_state": 2,
    "context_token": "<会话上下文 token>",
    "item_list": [
      {
        "type": 1,
        "text_item": {
          "text": "Hello"
        }
      }
    ]
  },
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

```json
{
  "ret": 0,
  "errmsg": ""
}
```

**集成建议：** 回复同一会话时回传入站消息的 `context_token`。当前发送助手在 token 缺失时记录警告但仍发送；这不代表服务端一定接受该请求。媒体说明文字和媒体
item 当前会分成独立请求发送，每个请求使用各自的 `client_id`。

### 4.4 `getUploadUrl`

```http
POST /ilink/bot/getuploadurl
```

```json
{
  "filekey": "<客户端生成的文件 key>",
  "media_type": 1,
  "to_user_id": "<目标用户 ID>",
  "rawsize": 12345,
  "rawfilemd5": "<明文 MD5>",
  "filesize": 12352,
  "no_need_thumb": true,
  "aeskey": "<16 字节 key 的十六进制文本>",
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

| 字段               | 类型      | 说明                                   |
|--------------------|-----------|----------------------------------------|
| `media_type`       | `number`  | `1` 图片，`2` 视频，`3` 文件，`4` 语音 |
| `rawsize`          | `number`  | 明文大小，单位字节                     |
| `rawfilemd5`       | `string`  | 明文 MD5                               |
| `filesize`         | `number`  | AES-128-ECB + PKCS#7 填充后的密文大小  |
| `thumb_rawsize`    | `number`  | 需要缩略图时的缩略图明文大小           |
| `thumb_rawfilemd5` | `string`  | 需要缩略图时的缩略图明文 MD5           |
| `thumb_filesize`   | `number`  | 需要缩略图时的缩略图密文大小           |
| `no_need_thumb`    | `boolean` | 无需上传缩略图时设置                   |
| `aeskey`           | `string`  | 十六进制形式的 AES key                 |

```json
{
  "upload_param": "<加密的上传参数>",
  "thumb_upload_param": "<加密的缩略图上传参数>",
  "upload_full_url": "<可选完整上传 URL>"
}
```

当前客户端优先使用 `upload_full_url`；若缺失，则使用 `upload_param` 与 `filekey` 构造 CDN 上传 URL。类型暴露了缩略图字段和 `thumb_upload_param`
，但共享上传链路始终发送 `no_need_thumb: true`，仅上传原文件且不消费 `thumb_upload_param`。这些字段不表示缩略图上传已实现。同样，类型有
`media_type: 4`，但当前文件发送链路只会选择图片、视频或文件。

### 4.5 `getConfig`

```http
POST /ilink/bot/getconfig
```

```json
{
  "ilink_user_id": "<用户 ID>",
  "context_token": "<可选会话上下文 token>",
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

```json
{
  "ret": 0,
  "typing_ticket": "<base64 编码的输入状态 ticket>"
}
```

### 4.6 `sendTyping`

```http
POST /ilink/bot/sendtyping
```

```json
{
  "ilink_user_id": "<用户 ID>",
  "typing_ticket": "<由 getConfig 返回的 ticket>",
  "status": 1,
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

`status` 为 `1` 表示输入中，`2` 表示取消输入状态。

### 4.7 `notifyStart` 与 `notifyStop`

```http
POST /ilink/bot/msg/notifystart
POST /ilink/bot/msg/notifystop
```

```json
{
  "base_info": {
    "channel_version": "2.4.8",
    "bot_agent": "OpenClaw"
  }
}
```

```json
{
  "ret": 0,
  "errmsg": ""
}
```

插件会在渠道客户端启动时调用 `notifyStart`，停止时调用 `notifyStop`。

## 5. 消息模型

下列表格概括客户端侧类型。 `WeixinMessage`、`MessageItem` 及媒体对象属性在 TypeScript 定义中均为可选；表格不声明服务端必填字段。 `group_id`
等字段描述的是类型表面，不保证插件支持对应功能。

### 5.1 `WeixinMessage`

| 字段             | 类型            | 说明                             |
|------------------|-----------------|----------------------------------|
| `seq`            | `number`        | 消息序列号                       |
| `message_id`     | `number`        | 消息 ID                          |
| `from_user_id`   | `string`        | 发送者 ID                        |
| `to_user_id`     | `string`        | 接收者 ID                        |
| `client_id`      | `string`        | 客户端生成或关联的 ID            |
| `create_time_ms` | `number`        | 创建时间戳，单位毫秒             |
| `update_time_ms` | `number`        | 更新时间戳，单位毫秒             |
| `delete_time_ms` | `number`        | 删除时间戳，单位毫秒             |
| `session_id`     | `string`        | 会话 ID                          |
| `group_id`       | `string`        | 适用时的群组 ID                  |
| `message_type`   | `number`        | `1` 用户，`2` Bot                |
| `message_state`  | `number`        | `0` 新建，`1` 生成中，`2` 已完成 |
| `item_list`      | `MessageItem[]` | 消息内容 item                    |
| `context_token`  | `string`        | 用于回复的会话上下文             |
| `run_id`         | `string`        | 适用时的生成或运行 ID            |

### 5.2 `MessageItem`

| `type` | 内容字段                |
|-------:|-------------------------|
|    `1` | `text_item`             |
|    `2` | `image_item`            |
|    `3` | `voice_item`            |
|    `4` | `file_item`             |
|    `5` | `video_item`            |
|   `11` | `tool_call_start_item`  |
|   `12` | `tool_call_result_item` |

公共 item 字段包括 `create_time_ms`、`update_time_ms`、`is_completed`、`msg_id`，以及包含被引用消息 item 的可选 `ref_msg`。 `voice_item.text`
可以携带转写文本。媒体 item 可携带 `media`；图片和视频还可携带 `thumb_media`。

### 5.3 客户端使用的媒体字段

| 字段                                  | 类型     | 当前用途                                                     |
|---------------------------------------|----------|--------------------------------------------------------------|
| `image_item.aeskey`                   | `string` | 入站 AES key，为 32 位十六进制字符，优先于 `media.aes_key`。 |
| `image_item.mid_size`                 | `number` | 图片发送器写入的密文字节数。                                 |
| `video_item.video_size`               | `number` | 视频发送器写入的密文字节数。                                 |
| `file_item.file_name`                 | `string` | 附件文件名。                                                 |
| `file_item.len`                       | `string` | 文件发送器写入的十进制明文字节数。                           |
| `voice_item.text`                     | `string` | 存在时为转写文本。                                           |
| `voice_item.encode_type`              | `number` | 类型中的编解码标识，不代表支持解码全部编解码器。             |
| `voice_item.sample_rate` / `playtime` | `number` | 采样率（Hz）/ 时长（毫秒）。                                 |

其余字段参见上游 [`types.ts`](https://github.com/Tencent/openclaw-weixin/blob/main/src/api/types.ts)，出站消息体参见 [
`send.ts`](https://github.com/Tencent/openclaw-weixin/blob/main/src/messaging/send.ts)。

### 5.4 CDN 媒体引用

```json
{
  "encrypt_query_param": "<下载参数>",
  "aes_key": "<base64 编码的 AES key>",
  "encrypt_type": 1,
  "full_url": "<可选完整下载 URL>"
}
```

客户端优先使用 `full_url`。没有完整 URL 时，兼容实现可构造：

```text
<cdn_base_url>/download?encrypted_query_param=<URL 编码后的 encrypt_query_param>
```

下载解码器接受 base64 解码后为 16 个原始字节或 32 个十六进制字符的 key。当前图片、视频和文件发送器都会将十六进制 key 文本做 base64
编码。这分别是可接受编码和当前出站行为，并不意味着不同媒体类型必须采用不同编码。

## 6. CDN 媒体流程

### 6.1 上传

1. 读取明文文件，计算大小和 MD5。
2. 生成 16 字节 AES key 和文件 key。
3. 计算填充后的密文大小。
4. 调用 `getUploadUrl`。
5. 使用 AES-128-ECB 和 PKCS#7 padding 加密内容。
6. 以 `Content-Type: application/octet-stream` 将密文字节发送至返回的上传 URL。
7. 读取响应头 `x-encrypted-param`。
8. 将返回的下载参数和 AES key 填入通过 `sendMessage` 发送的媒体引用。

**当前客户端行为：** 上传使用 HTTP `POST`，仅上传原文件并设置 `no_need_thumb: true`，没有缩略图上传步骤。

成功要求 HTTP `200` 且响应头 `x-encrypted-param` 非空。HTTP 4xx 立即终止；其他失败（包括缺少该响应头）最多尝试 3 次。这是插件当前的成功判定和重试策略。

### 6.2 下载

1. 优先使用 `full_url`；缺失时当前客户端允许用 `encrypt_query_param` 构造 CDN 下载 URL。
2. 通过 HTTP `GET` 下载字节。
3. 图片优先使用 `image_item.aeskey` 的十六进制 key，其次使用 `image_item.media.aes_key`；两者均缺失时将下载字节视为明文。
4. 加密的图片、语音、文件和视频使用解码后的 key 进行 AES-128-ECB 和 PKCS#7 padding 解密。当前媒体下载器会跳过缺少 `media.aes_key` 的语音、文件和视频
   item。

## 7. 源码参考

- [API 请求实现](https://github.com/Tencent/openclaw-weixin/blob/main/src/api/api.ts)
- [协议类型](https://github.com/Tencent/openclaw-weixin/blob/main/src/api/types.ts)
- [二维码登录流程](https://github.com/Tencent/openclaw-weixin/blob/main/src/auth/login-qr.ts)
- [CDN 上传实现](https://github.com/Tencent/openclaw-weixin/blob/main/src/cdn/upload.ts)
- [AES-ECB 工具](https://github.com/Tencent/openclaw-weixin/blob/main/src/cdn/aes-ecb.ts)
- [消息构造器](https://github.com/Tencent/openclaw-weixin/blob/main/src/messaging/send.ts)
- [入站媒体处理](https://github.com/Tencent/openclaw-weixin/blob/main/src/media/media-download.ts)
- [CDN 上传传输层](https://github.com/Tencent/openclaw-weixin/blob/main/src/cdn/cdn-upload.ts)
- [消息轮询和重试](https://github.com/Tencent/openclaw-weixin/blob/main/src/monitor/monitor.ts)
- [配置缓存](https://github.com/Tencent/openclaw-weixin/blob/main/src/api/config-cache.ts)
- [渠道生命周期](https://github.com/Tencent/openclaw-weixin/blob/main/src/channel.ts)
