# Permission Test Matrix — 区三/区四：认证上下文与特殊组织默认行为

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 2 M9 实现：** Task 7（`MultiTenantTestFixture` 内置 org 扩展）评估后确认无需改动；Task 8（`PermissionMatrixSpecialTest`）区四已落地，区三里 Google OAuth SSO 已落地（Task 8b，enterprise 侧 `StyleBIGoogleSSOFilterTest`），账号非活跃状态评估后确认 `[不补]`，Login As 分析中发现一个已实测确认的认证层 bug（密码校验用错了用户，且失败是静默的），`checkLoginAs()` 权限门槛已落地，端到端身份切换阻塞于该 bug 待补，见 `2026-06-30-permission-test-phase2.md`。
**姊妹文档：** 区一 `permission-matrix-resources.md`、区二 `permission-matrix-actions.md`。

图例：✓ = allowed　✗ = denied　— = n/a

**"测试状态"列取值**（与 `permission-matrix-resources.md`/`permission-matrix-actions.md` 一致）：
- `[M9]` = 已落地并通过，后面附对应 `@Test` 方法名
- `[待补]` = 已在设计文档确认、尚未落到测试代码，后面附未落地的具体原因
- `[不补]` = 已评估过是否要补，结论是不补，后面附具体原因

区一和区二测的是"有没有权限"；区三测的是**权限检查能否被触达**——认证状态或账号状态异常时，授权层根本不应该被执行；区四测的是两个内置组织（`host-org`/`SELF`）相对普通创建 org 的默认权限行为差异。两部分场景完全独立，合在一个文件里只是因为都不适合放进区一/区二的"用户类型 × 资源/Action"矩阵形状。

---

## 区三：认证上下文 / 账号状态（通路测试）

三个场景完全独立，每个只需少量通路用例，不展开矩阵。

| 场景 | 触发条件 | 核心断言 | 测试状态 |
|---|---|---|---|
| **Google OAuth SSO** | Google OAuth SSO（`googleUser`）| 认证成功后权限解析与本地用户一致；org 归属、role 映射正确落地；不因认证来源差异绕过权限检查 | `[M9]`（enterprise 侧认证链路已落地，15 个 `@Test` 全部通过，具体方法名见下方"Google OAuth SSO 详细场景"小节；community 侧回归护栏未补，评估为可选） |
| **账号非活跃状态** | 用户账号被禁用（inactive / disabled）| 登录被拒；已有 session 不会因账号被禁用立即失效——只在下次重新登录时生效 | `[不补]`（详见下方"账号非活跃状态"小节，登录拒绝已有测试覆盖，session 不立即失效是确认的设计行为） |
| **Login As 代理登录** | siteAdmin / 持有 `LOGIN_AS` Security Action 的用户切换到目标用户身份 | 代理期间权限以**目标用户**的授权为准，而非操作者本身；退出代理后恢复原身份权限；`LOGIN_AS` Action 本身受区二 Security Actions 管控（For Org √）| `[待补]`（详见下方"Login As 详细场景"小节——权限门槛可测，端到端身份切换阻塞于一个已实测确认的认证层 bug） |

每个场景通路测试只需验证：
1. 认证/状态变更后 session 建立或失效符合预期
2. 至少一个资源访问断言（allowed 或 denied）与预期一致
3. 不会因该特殊路径绕过区一/区二的正常权限逻辑

> org self-signup 的组织落地/默认权限场景不放在这里——测的是"落地哪个 org、该 org 默认给什么权限"，跟本区"认证能否被触达"是不同维度，见下方区四场景表（评估后确认 `[不补]`）。

### 账号非活跃状态（评估后确认 `[不补]`）

拆成两半，两半都不需要在 `PermissionMatrixSpecialTest.java` 里新增代码：

- **登录时拒绝——已有测试覆盖，无需重复**：`community/core/src/test/java/inetsoft/sree/security/provider/AuthenticationProviderContractTest.java` 的 `C10 authenticate_inactiveUser_returnsFalse()` 是跨 Provider 共享契约测试（File/Database/LDAP 各自的测试类都继承它，每个 provider 实现都会跑一遍），断言"inactive 用户即使密码正确也不能通过 `authenticate()`"；`FileAuthenticationProviderTest.java` 还有一条更具体的 `[Auth: inactive]` 用例断言同样的结论。`FileAuthenticationProvider.authenticate()`（L339）：`if(null == uobj || !uobj.isActive()) { return false; }`，一路往上（`AuthenticationService`/`SecurityEngine.authenticate()`）principal 保持 `null`，登录失败——跟密码错误走同一条失败路径，没有专门的"账号已禁用"提示。
- **已登录 session 不会立即失效——确认是设计行为，不是待测缺口**：`InvalidateSessionFilter` 是唯一看起来相关的机制，但它调用的 `SecurityEngine.isActiveUser(Principal)` 实际检查的是"principal 是否还在 `SecurityEngine.users` 这个当前登录会话缓存 map 里、且身份对得上"，**不读 `User.isActive()` 这个持久化字段**——方法名容易让人误以为是查账号禁用状态。管理端"编辑用户"把 `active` 改成 `false` 的代码（`IdentityService.setUserInfo()`）也没有任何踢下线/清缓存的动作。已跟用户确认：账号被禁用只在下次重新登录时生效，已登录的 session 不会被立即踢下线，这是预期行为。

### Login As 详细场景

**确认的产品 bug（已实测复现，不是靠读代码猜的）：** Login As 第二步"切换到目标用户身份"重新走了一遍认证，但密码校验比对的是**目标用户存的密码**，不是管理员自己刚提交的密码——只要两人密码不一样（现实里几乎总是这样），这一步认证就会失败。

调用链：`BasicAuthenticationFilter`（`checkLoginAs()` 通过后）→ `authenticate(request, adminID, adminPassword, targetID, locale, true)` → `AuthenticationService.authenticate(userId=adminID, loginAsUser=targetID, password=adminPassword, ...)`：
```java
IdentityID userName = userId;                          // adminID
if(login.loginAs=on && loginAsUser != null) {
   userName = loginAsUser;                              // 变成 targetID
}
ClientInfo clientInfo = new ClientInfo(userName, ...);       // userID 字段 = targetID
clientInfo.setLoginUserName(loginAsUser);                    // loginUser 字段也是 targetID
DefaultTicket ticket = new DefaultTicket(userId, password);  // ticket 里仍是 adminID + 管理员的密码
principal = authenticate(clientInfo, ticket);
```
→ `SecurityEngine.authenticate()`：`provider.authenticate(user.getLoginUserID() /* = targetID */, credential /* = ticket(adminID, 管理员密码) */)` → `FileAuthenticationProvider.authenticate()`：用 `userIdentity` 参数（targetID）取到**目标用户自己**的密码 hash，去跟 ticket 里的**管理员密码**比对。

**实测确认**（直接调用 `FileAuthenticationProvider.authenticate()`，不是静态读代码）：
```
provider.authenticate(targetId, ticket(adminId, adminPassword))   → false
provider.authenticate(targetId, ticket(targetId, targetPassword)) → true
provider.authenticate(adminId,  ticket(adminId, adminPassword))   → true
```
查过 `LoginController.java`，它只往登录页模板塞一个 `loginAs` 布尔值控制 UI 显示，真正提交凭据走的还是同一个 `BasicAuthenticationFilter`（Basic Auth header），没找到绕开这条路径的现代化 REST 端点。已整理成独立 bug 报告，未修复（本次任务范围是补测试不是修 bug）。

**补充一个更关键的细节**：`BasicAuthenticationFilter.doFilter()` 里调这第二次 `authenticate()`（L318-322）时，**返回值直接被丢弃，完全没有判断成功还是失败**：
```java
if(checkLoginAs(principal, provider, loginAsUser)) {
   authenticate(request, new IdentityID(userKey, recordedOrgID), password, loginAsUser, locale, true);
   // 返回值没接、没判断
}
```
`authorized` 在这之前（L296）已经因为第一次（管理员自己）认证成功被设成了 `true`，不会因为这第二次调用失败而改变。也就是说这个 bug 触发时**不会报错**——请求会带着 `authorized=true` 直接往下走 `chain.doFilter()`，但因为 `authenticate()` 内部认证失败、`principal` 是 `null`，不会创建新 session（`createSession` 只在 `principal != null` 时才执行），管理员自己原来的 session 原样保留。表现出来就是：点了"以某某身份登录"，界面上什么错误都没有，但实际操作用的还是管理员自己的身份/权限——跟你之前说的"权限容易出问题，可能用了 admin 的权限"完全对上了。

**因此本次范围收窄成两块不受这个 bug 影响的独立场景（都是纯逻辑，直接调用相关方法，不用真的走两次 HTTP 认证）：**

| 场景 | 关联机制 | 核心断言 | 测试状态 |
|---|---|---|---|
| `checkLoginAs()` 权限门槛 | `BasicAuthenticationFilter.checkLoginAs(principal, provider, targetUser)`（`private`，测试里用 `ReflectionTestUtils.invokeMethod()` 直接调） | siteAdmin 可以代理任何目标；非 siteAdmin 要求同 org + 对目标有 `SECURITY_USER`/`ADMIN` 权限；目标本身是 siteAdmin 时非 siteAdmin 操作者被拒绝 | `[M9]` `checkLoginAs_siteAdminOperator_anyTarget_allowed` / `checkLoginAs_nonSiteAdminOperator_sameOrgTargetWithSecurityUserAdminGrant_allowed` / `checkLoginAs_nonSiteAdminOperator_sameOrgTargetWithoutGrant_denied` / `checkLoginAs_nonSiteAdminOperator_targetInDifferentOrg_denied` / `checkLoginAs_nonSiteAdminOperator_targetIsSiteAdmin_denied` |
| `LOGIN_AS`/`ACCESS` 权限门槛 | `provider.checkPermission(principal, LOGIN_AS, "*", ACCESS)` | 已在区二 `PermissionMatrixActionsS8Test` 的 S8-SWEEP 覆盖过 | `[不补]`（重复，见 `permission-matrix-actions.md`） |
| 端到端"代理期间权限以目标用户为准" | 依赖上面确认的 bug 修复后的第二次 `authenticate()` | 目前无法测——身份切换这一步本身会认证失败（且失败是静默的） | `[待补]`（阻塞于上述 bug，修复前无法测） |

**`checkLoginAs()` 测试的 classpath 限制**：`OrganizationManager.getUserOrgId(Principal)`（`checkLoginAs()` 的"同 org"判断依据）在 `community/core` 测试 classpath 上没有 enterprise 覆写，社区基类实现**无条件返回 `Organization.getDefaultOrganizationID()`（host-org），完全不看传进来的 principal**。所以这里测的"同 org"实际上对所有非 siteAdmin 操作者都等价于"目标在 host-org"，不是真的按操作者各自的 org 比较——跟 `SUtil.isMultiTenant()` 那种 community/enterprise classpath 差异是同一类问题。

### Google OAuth SSO 详细场景

Google SSO 的整条 filter 链（`StyleBIGoogleSSOFilter`/`OpenIDFilterBaseFilter`/`DelegatingSSOFilter`）只存在于 `enterprise` 模块，`community/core` 测试 classpath 上没有这些类——不是像 `isMultiTenant()` 那种"行为被结构性关掉"，是类根本不存在，本文档所属的 `PermissionMatrixSpecialTest.java`（community/core）没法直接测。这一行拆成两半：

- **enterprise 侧（真实认证链路）— `[M9]` 已落地**：`enterprise/src/test/java/inetsoft/enterprise/sso/StyleBIGoogleSSOFilterTest.java`，覆盖 `StyleBIGoogleSSOFilter.createLoginIdentityID()`（org/身份映射）+ `allowLogIn()`（self-signup 拒绝规则）+ `OpenIDFilterBaseFilter.correctOrgForNewMultiTenantUser()`（新落地 org 的二次校正，见下方说明），跟 `SSOTokenServiceTest.java` 同风格（`@ExtendWith(MockitoExtension.class)` + `@Tag("enterprise")`，纯 Mockito，不起 Spring）。用了 `Mockito.mockStatic(SecurityEngine.class)`（`getSecurityProvider()` 走的是静态 `SecurityEngine.getSecurity()`，不是构造函数注入字段）+ `Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS)`（`isMultiTenant()`/`getLoginOrganization()`）+ `Mockito.mockStatic(XSessionService.class)`（`SRPrincipal` 构造需要）。15 个 `@Test` 全部通过。
- **community 侧（回归护栏，可选补）**：一个打了 `googleSSOId` 标记的 `FSUser` vs 一个结构相同没打标记的本地用户，跑同一批 `SecurityEngine.checkPermission()` 断言结果一致——`checkPermission()` 全链路不读 `googleSSOId`/任何 SSO 相关属性，这条断言目前是同义反复，价值是给未来误加 SSO 专属分支的回归护栏，不是验证已知缺陷。

**重构说明——`correctOrgForNewMultiTenantUser()`：** `createLoginIdentityID()`（`StyleBIGoogleSSOFilter`）单独调用，在"主域名 + 多租户 + 都不匹配"这一格会返回 host-org，不是 SELF（`ssoUserId != null && recordedOrgID != null` 这个判断在该调用路径上恒为真，导致 SELF 分支不可达）。但它唯一的调用方 `OpenIDFilterBaseFilter.createSessionFromToken()` 紧接着会对结果做一次独立复查——原来是内嵌在该方法中间的一段 if 块，现已提取成 `correctOrgForNewMultiTenantUser(IdentityID, JWTClaimsSet)`：重新扫一遍整条 `AuthenticationChain`，如果这个人确实哪个 org 都不存在，就把 org 校正回 SELF。也就是说 `createLoginIdentityID()` 单独看这一格返回 host-org是真实的，但端到端登录流程（`createSessionFromToken()` 调用完 `createLoginIdentityID()` 后必定紧跟着调用 `correctOrgForNewMultiTenantUser()`）落地的确实是 SELF，跟"系统里一个用户都没有"分支（`users == null`，无条件落 SELF）以及前端实测结果一致——**不是功能缺陷**，只是 `createLoginIdentityID()` 这一个方法单独看返回值容易让人误判，提取成独立方法之后两层职责边界清楚了，也都各自测到了。

**`createLoginIdentityID()` 场景（按访问域名分组）：**

| 分组 | 场景 | 核心断言 | 测试方法（`StyleBIGoogleSSOFilterTest`） |
|---|---|---|---|
| 主域名（`recordedOrgID = host-org`，`defaultOrg = true`） | host-org 下存在匹配用户（`googleSSOId` 或 email） | 返回该 host-org 用户的 `IdentityID` | `[M9]` `mainDomain_matchInHostOrg_returnsHostOrgUser` |
| | SELF 下存在匹配用户 | 返回该 SELF 用户的 `IdentityID` | `[M9]` `mainDomain_matchInSelf_returnsSelfUser` |
| | host-org 和 SELF 同时存在匹配用户（同一 `googleSSOId`/email，分属两个 org） | **SELF 优先**——循环内 SELF 分支命中即时 `return`，host-org 分支命中只是记到 `defaultOrgUser`，要等循环扫完才返回 | `[M9]` `mainDomain_matchInBothHostOrgAndSelf_selfWins` |
| | 都不匹配 + `SUtil.isMultiTenant()=true` | `createLoginIdentityID()` 单独返回落地 **host-org**（自身的 SELF 分支不可达，见上方"重构说明"）；接上 `correctOrgForNewMultiTenantUser()` 后端到端校正回 **SELF** | `[M9]` `mainDomain_noMatch_multiTenant_landsInSelf_afterCorrectOrgForNewMultiTenantUser`（一个方法内两段断言都测） |
| | 都不匹配 + `SUtil.isMultiTenant()=false` | 落地 **host-org**（非多租户下没有"多租户隔离"概念，SELF 桶不需要单独存在，`correctOrgForNewMultiTenantUser()` 也不会介入——它只在 `isMultiTenant()=true` 时才生效） | `[M9]` `mainDomain_noMatch_notMultiTenant_landsInHostOrg` |
| | 匹配到用户但 `!user.isActive()` | 抛 `NotActiveUserException`（host-org 分支和 SELF 分支都要各测一次，因为判断分别发生在两个不同的 return 点） | `[M9]` `mainDomain_matchedHostOrgUserInactive_throwsNotActiveUserException` / `mainDomain_matchedSelfUserInactive_throwsNotActiveUserException` |
| 子组织域名（`recordedOrgID = "org1"`，`defaultOrg = false`） | org1 下存在匹配用户 | 返回该用户的 `IdentityID`，跟主域名逻辑一致 | `[M9]` `subOrgDomain_matchInThatOrg_returnsThatUser` |
| | org1 下不存在匹配用户 | 抛 `AuthenticationFailureException(INVALID_CREDENTIALS)`——**不会自动注册**，需要管理员手动建号；这一步在 `allowLogIn()` 之前就已失败，跟 self-signup 开关无关 | `[M9]` `subOrgDomain_noMatch_throwsAuthenticationFailureException` |
| 系统内一个用户都没有（`users == null`，独立早退分支，跟上面的循环分支是两段不同代码，需单独测做分支覆盖） | 主域名 + 多租户 | 落地 SELF（这一分支本身就无条件返回 SELF，不需要 `correctOrgForNewMultiTenantUser()` 介入） | `[M9]` `noUsersAtAll_mainDomain_multiTenant_landsInSelf` |
| | 主域名 + 非多租户 | 落地 host-org | `[M9]` `noUsersAtAll_mainDomain_notMultiTenant_landsInHostOrg` |
| | 子组织域名 | 抛 `AuthenticationFailureException`（跟"有用户但不匹配"结论一致） | `[M9]` `noUsersAtAll_subOrgDomain_throwsAuthenticationFailureException` |

**`allowLogIn()` 场景（跟访问域名/落地 org 无关，只看该身份是否已存在）：**

> 排查代码发现 `allowLogIn()` 内 org 相关的判断分支实际不可达：进入 `!selfSignUp` 分支时 `user` 变量恒为 `null`（因为如果不是 null，函数在这之前已经 `return true`），所以 `Tool.equals(orgId, SELF) || user == null || (...)` 里的 `user == null` 恒成立——只要身份不存在且 `selfSignUp=false` 就无条件拒绝，跟落地哪个 org 无关。

| 场景 | 核心断言 | 测试方法（`StyleBIGoogleSSOFilterTest`） |
|---|---|---|
| `identityID` 已存在（对应一个真实 `User`） | 放行，不看 `selfSignUp` 开关 | `[M9]` `allowLogIn_identityAlreadyExists_allowsRegardlessOfSelfSignUp` |
| `identityID` 不存在（新落地的 SELF 或 host-org 身份）+ `security.selfSignUp.enabled=false` | 拒绝（forward 到 `GOOGLE_USER_SIGN_UP_DENIED` 错误页） | `[M9]` `allowLogIn_newIdentity_selfSignUpDisabled_deniesAndForwardsToErrorPage` |
| `identityID` 不存在 + `security.selfSignUp.enabled=true` | 放行 | `[M9]` `allowLogIn_newIdentity_selfSignUpEnabled_allows` |

---

## 区四：特殊组织默认行为（host-org / SELF）

本节覆盖两个内置组织（`Organization.getDefaultOrganizationID()` = `host-org`，`Organization.getSelfOrganizationID()` = `SELF`，全局唯一、供 self-signup 用户共用）相对普通创建 org 的默认行为差异。测试类用 `SecurityTestDataBuilder` 直接构造（跟 `MultiTenantIsolationTest` 同样理由：拿到完整控制权），不经过 `MultiTenantTestFixture`——host-org/SELF 由 `FileAuthenticationProvider.init()` 自动播种，不需要 fixture 层的"引用支持"。

### 场景表

| 场景 | 关联机制 | 触发条件 | 核心断言 | 测试状态 |
|---|---|---|---|---|
| self-signup org 落地（多租户/非多租户） | `UserSignupService.java:169-170`：`SUtil.isMultiTenant() ? SELF : host-org` | self-signup | 新用户落地 SELF 或 host-org | `[不补]`（单行三元表达式，bug 风险低；落地后该 org 实际给什么权限已由下面两条验证，测三元表达式本身要起 `UserSignupService`/HTTP 层，跟本类"直调 `SecurityEngine.checkPermission()`"的层次不符） |
| SELF `DATA_SOURCE_FOLDER` 回退不对称 | 原文档误标于 `SecurityEngine.checkPermission(Principal, ResourceType, String, ResourceAction)` — 实际 `isSelfAndNotAdmin` 分支只存在于姊妹重载 `checkPermission(Principal, ResourceType, IdentityID, ResourceAction)` | SELF 非 admin 用户，`DATA_SOURCE_FOLDER` 未配置权限 | 走生产真实调用路径（String 重载）的默认 READ：SELF 和普通创建 org 用户**都是** ✓，无不对称 | `[不补]`（查证 `DatasourcesTreeService`/`DataSourceBrowserService`/`GettingStartedService` 等全部生产调用点，DATA_SOURCE_FOLDER 检查一律走 String 重载；`isSelfAndNotAdmin` 所在的 IdentityID 重载对这个资源类型没有任何生产调用点，是不可达死代码，不值得测） |
| SELF 默认权限清单 | `FileAuthorizationProvider.addDefaultPermissionForSelfOrg()` L416-436 | SELF org 用户，未经任何显式 `grantPermission` | 默认放行 `CREATE_DATA_SOURCE`（通配符）、`PHYSICAL_TABLE`（通配符）、`CROSS_JOIN`（通配符）、`FREE_FORM_SQL`（通配符）、`PORTAL_TAB:Data`（均 ACCESS）、`REPORT:/`、`ASSET:/`（均 READ）；普通创建 org 同样未配置时全部 ✗（对照） | `[M9]` `selfOrgUser_defaultPermissionList_allowedWithoutAnyExplicitGrant` |
| host-org CHART_TYPE/SHARE 全局默认可见性 | `DefaultCheckPermissionStrategy.isOpeningShareGlobalAsset()` L511-517 + `isAllowedDefaultGlobalVSAction()` L493-509 | 非 host-org 用户 + 当前 org 上下文 = host-org（`ThreadContext` 未设置时的默认值）+ `SUtil.isDefaultVSGloballyVisible()` 为真 | 机制关闭时该用户对 SHARE 资源的显式空授权按预期拒绝；机制开启时同一用户同一资源无条件放行（不看任何显式授权） | `[M9]` `nonHostOrgUser_shareDefaultVisibility_deniedWhenMechanismOff` / `nonHostOrgUser_shareDefaultVisibility_allowedWhenMechanismOn` |

### 附注

- **host-org 机制的方向订正**：`isOpeningShareGlobalAsset()` 受益的是**非 host-org 的用户**（`orgID != currOrgID` 且 `currOrgID == host-org`），不是"host-org 自己的用户有特殊默认值"——跟直觉相反，命名容易让人误解。
- `SUtil.isDefaultVSGloballyVisible()` 要求 `SUtil.isMultiTenant()` 为真，而这在 `community/core` 测试 classpath 里结构性恒为 `false`（`LicenseManager.isEnterprise()` 探测不到 classpath 上的 `inetsoft.enterprise.EnterpriseConfig`），跟 `PermissionMatrixActionsS6Test` 同样的环境限制，需要 `Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS)`。
- SHARE 资源类型本身在 `DefaultCheckPermissionStrategy` 的默认放行清单上（跟 `CHART_TYPE_FOLDER` 等同属一批），所以测试对照用的 SHARE 资源需要 `markPermissionEdited()` 显式标记成"已配置但无授权"，才能拿到真实的 baseline 拒绝；`CHART_TYPE` 未选用，因为它有自己已测过的另一套默认放行机制（`SecurityEngine` 的 CHART_TYPE 父级重试回退，Bug #70538），混在一起会分不清是哪个机制起作用。
- `hasOrgEditedGrantAll()` 的 org 判断走的是 `OrganizationManager.getCurrentOrgID()`（ThreadContext 相关的当前上下文 org），不是被检查用户自己的 org——跟 `MultiTenantIsolationTest.scenario18A` 遇到的问题同源。"机制关闭"用例必须显式 `ThreadContext.setContextPrincipal(该用户)` 让两者对齐，否则会因为默认落到 host-org 而误触发跟本场景无关的默认放行清单；"机制开启"用例则相反，必须保持 `ThreadContext` 为空，让当前 org 上下文默认落到 host-org，这正是 `isOpeningShareGlobalAsset()` 需要的条件。
- SELF 和 host-org 都会在 `FileAuthorizationProvider.LoadPermissionsTask.initialize()`（L335-341）里各自跑一遍 `addDefaultAdminPermissions()` + `addDefaultRoleGrants()`，两者共享的默认授权路径（`COMPOSER`/`WORKSHEET`/`VIEWSHEET`/`DASHBOARD`/`SCHEDULE_TIME_RANGE`）走的是角色/组织授权，不是本文档要覆盖的差异点；本文档只聚焦 SELF **额外**拿到的 `addDefaultPermissionForSelfOrg()` 授权，以及 host-org **专属**的 `isOpeningShareGlobalAsset()` 机制。
