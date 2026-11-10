# RCDIS 校园生活社区

基于 Spring Boot 和 Vue 3 的校园周边生活社区，围绕沈阳药科大学提供社区帖子、附近商户、城市指南、积分和 AI 助手等功能。

## 功能

- 社区：发布图文帖子、按话题分页浏览及发表评论；后端提供点赞、关注和关注动态能力。
- 附近生活：结合 Redis GEO 与高德地点搜索，按分类和位置发现周边地点；地图与列表联动，并区分高德地点与已关联的社区商户。
- 个人中心：邮箱验证码或密码登录、编辑昵称及头像、签到、积分记录和数字主题兑换。
- 商户优惠：查询商户优惠信息，支持限量券的异步领取与状态查询。
- AI 助手：接入 SenseNova `sensenova-6.8-flash-lite`，通过 Function Calling 调用站内业务服务，使用 SSE 返回对话结果；客户端包含针对限流的重试逻辑。
- MCP：提供独立的只读工具接口，供外部 AI 应用接入；站内 AI 当前直接调用内部工具，并不经过 MCP。

## 技术栈与目录

- 后端：Java 8 目标版本、Spring Boot 2.3、MyBatis Plus、MySQL、Redis、Redisson。
- 前端：Vue 3、TypeScript、Vite、Element Plus。
- `src/main/java/com/hmdp`：后端业务与 AI 实现。
- `src/main/resources/db`：初始化 SQL 和业务增量脚本。
- `frontend-vue3`：前端源码。
- `scripts`：本地数据和运维辅助脚本，运行前请检查内容。

## 本地运行

### 1. 准备数据库与配置

1. 准备 MySQL 和支持 GEO、BITFIELD、Stream 的 Redis。默认配置见 `src/main/resources/application.yaml`：MySQL 为 `127.0.0.1:3306/hmdianping`，Redis 为 `127.0.0.1:6380`。根据本机环境设置数据库与 Redis 凭据。
2. 全新数据库可参考 `src/main/resources/db/hmdp.sql` 初始化，然后根据已有表结构依次检查并执行 `community_topic.sql`、`community_visibility.sql`、`community_points.sql`、`points_shop.sql`、`email_login.sql`、`shop_amap_link.sql`。这些脚本并非全部可重复执行；初始化脚本包含 `DROP TABLE`，执行前请备份已有数据。
3. 商户 GEO 数据需要与数据库商户坐标保持一致；`geo_relocate_shenyang.sql` 是历史模拟坐标脚本，不应作为真实商户位置数据使用。商户来源及坐标约定见 [商户数据梳理](docs/shop-data-audit.md)。
4. 图片上传目录当前在 `SystemConstants.IMAGE_UPLOAD_DIR` 中配置为 `D:\hm\nginx-1.18.0\html\hmdp\imgs\`，部署到其他路径时需调整，并配置静态图片访问。单文件上限 5 MB、请求上限 6 MB，反向代理也需允许相应大小。
5. 需要使用 AI 时，在启动后端的环境中设置 `SENSENOVA_API_KEY`；可通过 `SENSENOVA_API_BASE_URL` 覆盖默认 API 地址。
6. 高德附近地点搜索使用 Web 服务 Key，在后端启动环境中设置 `AMAP_WEB_KEY`。网页地图使用独立的 Web 端 JS API Key 和安全密钥，见下方地图配置。密钥不要提交到仓库。

### 2. 启动后端

使用 Maven 和与项目兼容的 JDK 编译运行；后端默认监听 **8081**：

```powershell
mvn -DskipTests package
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

默认会启动秒杀订单 Stream 消费者；不需要消费订单的独立验证实例可添加 `--hmdp.seckill.consumer-enabled=false`。

### 3. 启动前端

```powershell
cd frontend-vue3
npm install
npm run dev
```

按 Vite 终端输出的地址访问；开发服务器将 `/api` 转发至 `http://localhost:8081`。`npm run build` 会输出到 `../../nginx-1.18.0/html/hmdp/app`，该路径针对当前本机目录布局；其他环境请先调整 `vite.config.ts`。当前 Nginx 环境的访问地址为 `http://localhost:8080/`。

支持邮箱验证码登录、邮箱密码登录和通过邮箱验证码设置或重置密码。首次邮箱验证码登录会创建独立账号，不会自动合并原有手机号账号；原手机号验证码登录入口保留，验证码仍在后端日志中查看。

### 地图配置

- 后端地点搜索使用 `AMAP_WEB_KEY`，调用高德 Web 服务；该 Key 不应传给浏览器。
- 前端地图使用 Web 端 JS API Key。可在 `frontend-vue3/.env.local` 中设置 `VITE_AMAP_JS_KEY`，重新构建前端；此文件已被 Git 忽略。JS API Key 会在浏览器侧使用，应在高德控制台配置适当的使用限制。
- 高德 JS API 对应的安全密钥通过同源 `/_AMapService` 代理在服务端注入，不应写入前端代码。当前本地 Nginx 使用单独的 `conf/amap-security.local.conf` 保存代理配置，其他环境需按高德官方安全密钥文档部署代理。
- 社区商户坐标使用近似 WGS-84，高德 POI 使用 GCJ-02。附近搜索与地图中心做了转换；历史模拟商户和近似校园位置仍需核实。已确认的商户关联存于 `shop_amap_link`，建表脚本不会自动写入本地已有的关联数据。

### 登录配置

邮箱验证码通过 SMTP 发送。请在发件邮箱开启 SMTP 并生成客户端授权码，在后端启动环境中分别设置 `MAIL_USERNAME`（发件邮箱）和 `MAIL_AUTH_CODE`（客户端授权码），然后重启后端。验证码有效期为 5 分钟，成功验证后失效。

## AI 与 MCP

站内助手调用流程：`用户 → SenseNova Function Calling → AiToolRegistry → 业务 Service`。AI 对话接口包括 `POST /ai/chat` 和 `POST /ai/chat/stream`，后者通过 SSE 输出；调用前需要登录。

外部接入端点为后端 `POST /mcp`，当前实现 MCP `2025-03-26` Streamable HTTP 的工具调用子集：

| 工具 | 作用 | 必填参数 |
| --- | --- | --- |
| `query_shop` | 按 ID 查商户 | `id` |
| `query_shop_types` | 查询商户分类 | 无 |
| `query_vouchers` | 查询商户优惠券 | `shopId` |
| `query_nearby_shops` | 按分类和坐标查询附近商户 | `typeId`、`current`、`x`、`y`；可选 `radius` |

请求沿用项目登录鉴权：`authorization` 请求头填写登录后获得的原始 token，**不带 `Bearer` 前缀**。当前端点拒绝带 `Origin` 的请求，不提供 GET SSE 流；支持初始化、`ping`、`tools/list` 和 `tools/call`。例如将 `<登录token>` 替换为有效 token：

```powershell
$headers = @{ authorization = '<登录token>'; Accept = 'application/json, text/event-stream' }
$body = @{ jsonrpc = '2.0'; id = 1; method = 'tools/list'; params = @{} } | ConvertTo-Json -Depth 8
Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/mcp' -Headers $headers -ContentType 'application/json' -Body $body
```

该实现是为外部接入准备的协议子集，尚未通过官方 MCP 客户端及完整协议兼容性验证，也尚未实现面向第三方应用的独立授权。优惠券为演示数据；工具返回信息的准确性依赖实际业务数据。
