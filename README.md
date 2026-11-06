# Blogweb

个人技术博客后端骨架，基于 Java 17、Spring Boot 3.5.16 和 Maven。

## 当前范围

- Spring MVC、参数校验、统一 JSON 响应及异常处理。
- Spring Security、CSRF Token 接口、JSON 401/403。
- MyBatis-Plus、MySQL、Redis 和 Redis Session 的依赖及本地配置。
- 应用探活和自动化启动/安全边界测试。

已实现邮箱验证码注册、邮箱密码登录、当前用户查询和退出登录；没有预置账号。
注册只创建 USER，管理员不能通过注册接口创建。
除探活、CSRF、发送邮箱验证码、注册、登录和错误路径外，接口默认需要认证。Elasticsearch、MinIO、
文章模块在相应功能开发时接入，当前未引入。

## 环境

JDK 17、Maven 3.6.3+；使用 local 配置还需要 MySQL 和 Redis。
Docker Desktop 用于运行 compose.yml 中的本地依赖。

## 快速启动（不需要数据库）

在项目根目录执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=standalone"
```

此处显式启用 standalone，仅验证 Web 和安全基础设施，不连接 MySQL 或 Redis；项目默认环境以 application.yml 为准。
打开 http://localhost:8080/api/health 可看到 Blogweb 和 UP。
http://localhost:8080/actuator/health 是标准 Actuator 健康接口。
根路径没有网页，前端尚未创建。

## 连接 MySQL 和 Redis

启动 Docker Desktop，然后在项目根目录执行：

```powershell
docker compose up -d --wait
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

也可以在 IDE 运行 BlogwebApplication，并将 Active profiles 设置为 local。
数据库为 blogweb，宿主机 MySQL 端口 3306，Redis 端口 16379，HTTP 端口 8080。
Redis 容器内部使用标准端口 6379，宿主机映射端口用于避免与已有服务冲突。
默认密码仅供本机开发，见 compose.yml 和 application-local.yml。
容器端口只绑定本机；持久化数据保存在 Docker 命名卷。
停止依赖使用 `docker compose stop`，保留数据库内容。

local 配置连接 MySQL 并启用 Redis Session：MySQL/Redis 应先启动。
首次使用空数据库时，手动执行 sql/users.sql 建表；已有 users 表无需重复执行。
表字段为 id、email、password、nickname、role、status，应用不自动建表或修改表结构。
email 唯一且忽略大小写；password 原样保存前端提交的密码值；role 为 USER/ADMIN，status 为 1（正常）/0（禁用）。
User 通过 MyBatis-Plus UserMapper 读写 MySQL，使用 Lambda 条件构造器绑定参数；密码字段不会被 Jackson 序列化。
`/api/health` 仅表示 Web 服务可响应，`/actuator/health` 在 local 模式还检查数据库和 Redis。

可通过 DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD、REDIS_HOST、
REDIS_PORT、REDIS_PASSWORD、SERVER_PORT 环境变量覆盖配置。
Docker Compose 的 .env 文件不会自动传递给宿主机运行的 Spring Boot；
若修改凭证，需要同时设置运行 Java 进程的环境变量。已有数据库卷中的密码
不会因修改 compose 配置自动改变。

## 构建和测试

```powershell
mvn test
mvn clean package
java -jar target/blogweb-0.0.1-SNAPSHOT.jar
# 使用真实本地依赖：
java -jar target/blogweb-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

默认测试使用 standalone，不依赖外部服务；真实 MySQL/Redis 集成测试默认跳过，见下文。

## 代码结构

```text
com.ziv
├── BlogwebApplication
├── user                 用户 Controller、Service、Mapper、DTO、实体及基础设施
├── common/api           统一响应
├── common/exception     异常处理
├── config               安全配置
└── health               探活接口
```

业务逐步按模块增加 service、dto、mapper 等包，不预建空类。
注册仅允许创建 USER；管理员通过受控初始化流程创建。
浏览器使用 Cookie 会话认证，写请求需要先获取 `/api/csrf-token`，
保留同一 Cookie，并将返回 token 放进 headerName 指定的请求头。
standalone 会话在进程内存中，local 会话保存在 Redis。

## QQ 邮箱验证码

`POST /api/sendcode`，JSON 请求体：`{"email":"你的QQ邮箱@qq.com"}`。
目前接受以 .com 结尾的邮箱地址，如 @qq.com、@example.com，会去除首尾空格并统一为小写。
此接口不要求登录，但保留 CSRF 保护。

验证码为 SecureRandom 生成的六位数字（可能以 0 开头），Redis 保存 300 秒；
同一邮箱 60 秒内只能请求一次，通过 Lua 原子设置验证码和发送冷却时间。
重新发送成功后旧验证码被替换。后台 SMTP 发送失败会清除本次验证码，保留冷却时间。
响应仅包含有效期和重发间隔，不包含验证码；SMTP 接受邮件不代表最终投递成功。
注册时原子校验并消费验证码，同一验证码只能成功使用一次；连续输错 5 次立即失效。

使用步骤：在 QQ 邮箱网页设置中开启 SMTP 服务并取得授权码，然后修改
`EmailConstant.SMTP_USERNAME`（发件邮箱）、`SMTP_PASSWORD`（SMTP 授权码）及
`EMAIL_ENABLED = true`。配置已硬编码到常量类，不再读取邮件环境变量或 YAML。
QQ SMTP 默认 smtp.qq.com:465，SSL 开启，连接、读取、写入超时各为 5 秒。
修改后需要重新编译并启动：

```powershell
docker compose up -d --wait
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

另开一个 PowerShell 窗口调用接口（将收件地址替换成实际 QQ 邮箱）：

```powershell
$csrf = Invoke-RestMethod 'http://localhost:8080/api/csrf-token' -SessionVariable blogwebSession
$headers = @{}
$headers[$csrf.data.headerName] = $csrf.data.token
$body = @{ email = '收件QQ邮箱@qq.com' } | ConvertTo-Json
Invoke-RestMethod 'http://localhost:8080/api/sendcode' `
    -Method Post -WebSession $blogwebSession -Headers $headers `
    -ContentType 'application/json' -Body $body
```

成功返回 `{"code":200,"message":"success","data":{"status":"QUEUED","expiresInSeconds":300,"resendAfterSeconds":60}}`。
无效邮箱返回 400；缺少 CSRF 返回 403；发送频繁返回 429；邮件未配置、Redis
不可用或 队列满时返回 503，业务错误码用于区分原因。未配置真实 SMTP 时不会模拟成功。
默认 EmailConstant.EMAIL_ENABLED=false，账号和授权码为空；standalone 下没有 Redis，发送需使用 local 配置。
邮件发送不再使用 Spring Mail 的 YAML 配置或邮件健康检查。

参考：[Spring Boot 邮件配置](https://docs.spring.io/spring-boot/3.5/reference/io/email.html)、
[QQ 邮箱授权码说明](https://help.mail.qq.com/detail/106/985)。

## 邮箱注册与登录

使用 local 配置并启动 MySQL、Redis；真实发送验证码还需上面的 QQ SMTP 配置。
发送验证码、注册及两种登录统一校验邮箱，要求账号@域名.com 格式；支持邮箱密码登录及邮箱验证码登录，密码登录不需要邮件验证码。

1. `GET /api/csrf-token`：保留 Cookie，取得 CSRF Token。
2. `POST /api/sendcode`：发送注册验证码。
3. `POST /api/users/register`：提交下面的注册 JSON，成功后不自动登录。
4. `POST /api/sessions`：提交邮箱、密码；成功后保留更新后的会话 Cookie。
5. `GET /api/users/me`：使用 Cookie 获取当前用户，无密码字段。
6. 登录后重新 `GET /api/csrf-token`，用新 Token 调用 `DELETE /api/sessions/current`。

注册请求：

```json
{
  "email": "123456@qq.com",
  "password": "YourPassword123!",
  "nickname": "博主",
  "code": "012345"
}
```

登录请求：

```json
{
  "email": "123456@qq.com",
  "password": "YourPassword123!"
}
```

所有写请求（POST、DELETE 等）均保留 CSRF 保护。邮箱去除首尾空格并转小写，昵称去除首尾空格。
注册 password 字段长度为 8～255 个字符，登录最多 255 个字符，不修改其中的空格。
密码值由前端处理后提交，后端原样保存并精确比对，不进行加密或哈希，响应只包含 id、email、nickname、role、status。
客户端传入 role/status 不会影响注册权限，新用户固定为 USER、正常状态。

错误码：重复邮箱 409；验证码错误、过期、已使用或
尝试次数耗尽 400；密码错误、用户不存在或禁用统一返回
401；未登录、会话过期或当前用户禁用时 `/api/users/me` 返回 401。
同一邮箱每 15 分钟最多尝试登录 10 次（包括成功登录），超出返回 429。
数据库或 Redis 不可用返回 503，不降级绕过验证。

登录会更新 Session ID，清除旧 CSRF Token；Redis Session 默认 30 分钟无活动过期。
退出会使服务端会话失效。`/api/admin/**` 只允许 ADMIN 角色访问。
验证码在写 MySQL 前消费；若随后数据库写入失败，需要等待冷却后重新发送验证码。

真实依赖集成验证（模拟 EmailUtil 邮件发送，不会向真实邮箱发信）：

```powershell
docker compose up -d --wait
$env:BLOGWEB_INTEGRATION_TEST = 'true'
mvn test "-Dtest=UserInfrastructureTests"
Remove-Item Env:BLOGWEB_INTEGRATION_TEST
```

集成测试会创建随机测试用户，验证数据库密码值原样存储、Redis 验证码原子消费和次数限制、
真实 HTTP 会话登录/退出，并清理本次测试用户及验证码键。

会话实现参考：[Spring Security 6.5 会话持久化](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)。

## MyBatis-Plus 数据访问

使用 Spring Boot 3 专用的 `mybatis-plus-spring-boot3-starter`，版本 3.5.18。
`User` 通过 `@TableName("users")` 映射表，`@TableId(type = IdType.AUTO)` 使用 MySQL 自增主键。
`UserMapper` 标注 `@Mapper` 并继承 `BaseMapper<User>`，注册、登录和用户查询直接通过 Mapper 完成，
不再使用自定义 UserRepository 或手写 JDBC。无需为基础增删改查编写 XML。
local 模式有数据源时自动扫描 Mapper；standalone 没有数据源，业务接口仍返回数据库未配置错误。

参考：[MyBatis-Plus 官方安装指南](https://baomidou.com/getting-started/install/)。

## 邮件发送封装

验证码路由调用链：`UserController → UserService.sendEmailCode → EmailUtil.sendCode`。
`UserService` 管理安全随机验证码、Redis 限流和发送失败清理；`EmailUtil` 使用 Apache
Commons Email 1.6.0 的 `org.apache.commons.mail.SimpleEmail` 创建独立邮件对象并发送。
`EmailConstant` 集中管理验证码位数、有效期、重发间隔、尝试次数、Redis 键前缀和邮件文案。
SMTP 主机、端口、用户名、授权码、字符集、SSL、超时及启用开关均由 EmailConstant 直接定义，
EmailUtil 创建 SimpleEmail 时直接读取常量，不再注入 MailProperties 或 @Value。接口路径不变。

参考：[Apache Commons Email SimpleEmail](https://release-catalog.apache.org/commons-email/1.6.0/index.html)。

## RESTful 用户模块

- POST /api/users/register：创建普通用户，成功返回 201。
- POST /api/sessions：校验邮箱密码并创建登录会话，成功返回 201。
- GET /api/users/me：读取当前用户，成功返回 200。
- DELETE /api/sessions/current：删除当前会话，成功返回 204，无响应体。
- POST /api/sendcode：提交验证码发送任务，成功返回 202。
- GET /api/csrf-token：取得 CSRF Token，成功返回 200。

旧 /api/auth 下的动作式路由已移除，请按以上路径调用。注册和登录请求 JSON 不变。
登录后须重新获取 CSRF Token，再调用 DELETE 退出接口。

用户模块按 controller、service、mapper、entity、dto、infrastructure、security 分包。
UserController / SessionController → UserService → UserMapper 是用户数据库操作调用链；
Controller 不调用 Mapper，不编写业务规则或会话实现。
UserService 调用 EmailUtil 发邮件，调用 UserSessionManager 管理 Spring Security 会话，
调用 infrastructure 中的验证码存储和登录限流组件访问 Redis。
CsrfController 只暴露 Spring Security 提供的 Token，不访问数据库。

依赖注入统一使用 `@Autowired` 字段注入，配置值使用 `@Value`。需要在依赖就绪后执行的初始化逻辑使用 `@PostConstruct`，不再通过业务组件构造方法注入依赖。

密码存储规则：前端在注册与登录时必须提交一致的处理结果。该结果本身是可用于登录的凭据。
旧 BCrypt 数据无法还原为前端提交值；本次不会自动改写已有密码，旧账号需通过受控方式重置密码值或重新注册。

## 两种登录方式

两种方式均创建相同的 Redis Session，成功返回 201，不返回密码或验证码。
Controller → UserService → UserMapper 的调用结构与 @Autowired 注入方式保持一致。

密码登录：`POST /api/sessions/loginbypassword`（原 `POST /api/sessions` 仍可用）。

```json
{"email":"123456@qq.com","password":"前端处理后的密码值"}
```

验证码登录先调用 `POST /api/sendcode` 发送登录用途验证码：

```json
{"email":"123456@qq.com","purpose":"LOGIN"}
```

再调用 `POST /api/sessions/loginbycode`：

```json
{"email":"123456@qq.com","code":"012345"}
```

发送接口 purpose 支持 REGISTER / LOGIN，默认 REGISTER 以兼容已有注册请求。
验证码存储按用途隔离，注册验证码不能登录，登录验证码不能注册。
同一邮箱两种用途共享 60 秒发送冷却；有效期 5 分钟，最多输错 5 次，成功原子消费后不可重用。
登录验证码错误、过期、已使用、账号不存在或禁用统一返回 401。
验证码登录不会自动创建用户；两种登录方式共享同一邮箱每 15 分钟最多 10 次的尝试限额。
继续保留 CSRF 校验与 Cookie 会话；登录成功后重新获取 CSRF Token。
注册接口为 `/api/users/register`，均只创建普通用户。

邮箱格式统一通过 `UserServiceImpl.validateEmail(String)` 独立方法进行正则完整匹配，
由服务实现类在各业务方法入口直接调用。允许 `user@qq.com`、`user@example.com`；
拒绝多个 @、空账号、无效域名、非 .com 后缀及 .com 后继续追加后缀的输入。
请求 DTO 仍先去除首尾空白并统一转小写，格式错误返回 HTTP 400。QQ SMTP 发件配置不变。

## 通用错误码

`common.constant.ErrorCode` 统一维护 HTTP 状态、前端 code、默认 message。
覆盖参数错误、未登录、无权限、资源不存在、请求方法不支持、冲突、媒体类型不支持、
请求过于频繁、服务器异常、服务不可用和数据存储不可用。
统一异常处理器和 Spring Security 的 401/403 响应已使用该枚举；未知 HTTP 状态保留 HTTP 状态整数兜底。
响应格式仍为 `{"code":400,"message":"提示信息","data":null}`。
业务错误码由你配置：`BusinessException(HttpStatus, int code, String message)` 保留自定义入口，
不会将业务错误统一覆盖为通用错误码，也不在 ErrorCode 中新增或分配业务错误码。

## 验证码异步发送

邮件使用独立的 Spring 线程池 emailExecutor，核心线程数和最大线程数均固定为 5，
线程名为 blogweb-email-*，不使用请求线程发送 SMTP 邮件。任务队列默认容量 100，
可修改 EmailConstant.MAIL_QUEUE_CAPACITY 调整；队列满或线程池停止时立即拒绝，不退回请求线程执行。
应用关闭时等待任务完成，最多等待 30 秒。

发送接口返回 HTTP 202，data.status 为 QUEUED，表示任务已受理，不表示投递成功。
配置校验、Redis 验证码保存及频率限制在提交前完成；配置或 Redis 错误仍同步返回。
队列拒绝返回 HTTP 503 / 503，并清除本次验证码，保留发送冷却。
后台发送失败无法改变已返回的 HTTP 响应，会记录不包含验证码或 SMTP 密钥的错误日志，
并按本次 reservation 标识清理对应验证码，避免误删后续重发的验证码。
排队任务执行前再次检查 reservation 和验证码是否有效，失效或被替换的任务直接跳过。
验证码 5 分钟有效期从请求提交前写入 Redis 开始计算，包含排队时间。

这是进程内队列，服务异常退出时尚未执行的任务不会持久化重放，用户可在冷却结束后重试。

## 统一返回工具

ApiResponse 仅包含 int code、String message、T data 三个字段，以及 Lombok 生成的构造方法和成员访问方法。
所有返回工厂方法统一放入 common.util.ApiResponseUtil：

```java
ApiResponseUtil.success(data);                 // code = 200
ApiResponseUtil.success();                     // data = null
ApiResponseUtil.error(ErrorCode.BAD_REQUEST);  // code = 400
ApiResponseUtil.error(10001, "自定义业务错误");
```

ErrorCode、BusinessException 和响应体 code 已统一为整数。未配置专属编号的业务异常暂用
对应 HTTP 状态整数；你可以通过 BusinessException(HttpStatus, int, String) 自定义业务码，
HTTP 状态与响应体业务码可以不同。现有业务错误提示保留，工具类不参与业务规则或 HTTP 状态设置。

邮件相关 YAML 配置已移除。线程数、队列容量、线程名前缀和关闭等待时间也统一定义在 EmailConstant 中。

邮箱正则校验已简化到 user.service.impl.UserServiceImpl 的私有 validateEmail 方法，
注册、密码登录、验证码登录和发送验证码均先在类内调用该方法；无效邮箱在访问 Mapper、
Redis 或邮件配置前返回 400。独立 EmailValidator、@AccountEmail 及其验证器已移除。
Controller 注入 UserService 接口，由 Spring 自动装配 UserServiceImpl。

## CSRF Token 的作用与调用流程

CSRF（Cross-Site Request Forgery，跨站请求伪造）是指攻击者诱导用户浏览器向已登录的
网站发送操作请求，借用浏览器可能自动携带的登录 Cookie 执行非用户本意的操作。
CSRF Token 是后端生成并校验的随机值，前端需要在写请求中显式携带它，防止其他网站
仅凭浏览器自动附带的 Cookie 发起伪造操作。

Session Cookie 用于关联当前登录用户，CSRF Token 用于防跨站请求伪造；
它不替代登录认证、密码或邮箱验证码，也不是 JWT。

当前项目使用 Spring Security 的 HttpSessionCsrfTokenRepository：
standalone 模式 Token 关联内存会话，local 模式会话保存在 Redis。
即使尚未登录，注册、登录和发送验证码等写请求也需要先获取 Token。

1. 调用 `GET /api/csrf-token`，保留响应设置的会话 Cookie。
2. 从响应的 `data.headerName` 和 `data.token` 获取请求头名称及值；当前名称为 `X-CSRF-TOKEN`。
3. 发送 POST、PUT、PATCH、DELETE 等写请求时，携带同一会话的 Cookie，并设置该请求头。
4. 后端校验失败、Token 缺失或不属于当前会话时返回 HTTP 403；GET 等只读请求不要求 Token。
5. 密码或验证码登录成功后，会话 ID 更新，旧 Token 失效；重新调用获取接口再执行后续写请求。
6. 退出登录会销毁会话；再次注册、登录或发送验证码前，重新获取 Token 和会话 Cookie。

获取接口的响应结构示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "headerName": "X-CSRF-TOKEN",
    "token": "后端返回的Token"
  }
}
```

后续写请求携带 `X-CSRF-TOKEN: 后端返回的Token`，同时使用原会话 Cookie。
已有 PowerShell 调用示例中的 `-SessionVariable`、`-WebSession` 用于维持 Cookie，
`-Headers` 用于携带 Token。邮箱验证码仍通过请求 JSON 的 code 字段提交，与该 Token 相互独立。

参考：[Spring Security CSRF 文档](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)。

## Commons SimpleEmail 依赖

邮件实现使用 `org.apache.commons:commons-email:1.6.0`，类为 `org.apache.commons.mail.SimpleEmail`，
配套使用 `org.apache.commons.mail.EmailException`、`DefaultAuthenticator` 和 `javax.mail.Session`。
已移除 commons-email2-jakarta 与未再使用的 spring-boot-starter-mail，由 Commons Email 的
传递依赖提供对应邮件运行库。Spring Boot 的 Servlet、Validation 等其他 Jakarta 类型不受影响。
邮件仍从 EmailConstant 读取设置，固定 5 线程的异步发送及队列行为保持不变。


## 手动维护数据库

项目使用 MySQL + MyBatis-Plus，数据库结构通过手动 SQL 管理，已移除 Flyway。
应用启动不会建立迁移历史表、自动建表或执行 ALTER TABLE；spring.sql.init.mode=never。

- 新数据库：先创建 blogweb 数据库，再选择该库手动执行 sql/users.sql。
- 已有数据库：直接使用现有 users 表，不要重复执行建表 SQL。
- 若现有 users.id 没有自增属性，需自行审核并执行 sql/enable-users-id-auto-increment.sql，
  以配合 User 实体的 IdType.AUTO；应用不会自动修复。
- local 的 MySQL、Redis 地址及凭证以 application-local.yml 为准，使用 Docker Compose 示例时需对齐端口。

正常启动即可，无需任何迁移或基线参数：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```
