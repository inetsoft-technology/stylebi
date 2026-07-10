# Permission Test Matrix — 区三/区四：认证上下文与特殊组织默认行为

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 2 M9 实现：** Task 7（`MultiTenantTestFixture` 内置 org 扩展）评估后确认无需改动；Task 8（`PermissionMatrixSpecialTest`）区四已落地，区三里 Google OAuth SSO 已落地（Task 8b，enterprise 侧 `StyleBIGoogleSSOFilterTest`），账号非活跃状态评估后确认 `[不补]`，Login As 分析中发现一个认证层 bug（密码校验用错了用户，导致操作者与目标密码不同时登录直接失败，已验证，**Bug #75613**，[PR #4222](https://github.com/inetsoft-technology/stylebi/pull/4222) 已修复未合并），`checkLoginAs()` 权限门槛已落地，`LOGIN_AS` action 授权与 `checkLoginAs()` 的组合门槛也已在 `BasicAuthFilterHttpTest` 补齐，跨组织 Login As 的架构顾虑已核实为按设计工作（非新风险），端到端身份切换测试待 PR #4222 合并后补齐，见 `2026-06-30-permission-test-phase2.md`；区四新增 `exposeDefaultOrgToAll` host-org 全局共享 Viewsheet 的完整行为矩阵分析（Repository 展示范围、依赖资源可见性边界、Portal/EM/Composer 差异化限制、clone-org 场景），6 条描述规则均已用源码核实；生效范围（org 专属属性隔离）、写入限制（Composer Save As 预取拒绝）、关联操作限制（VSO 导出拒绝）三项已补齐测试（`PermissionMatrixSpecialTest`/`SaveViewsheetDialogServiceTest`/`VSExportServiceTest`），clone-org 场景与 Repository 树可枚举性评估后确认 `[不补]`（前者已有静态证据+现有测试泛化覆盖，后者层次不符），Composer 内数据可见性标记 `[不确定]`待运行时验证，见下方"host-org 全局共享 Viewsheet 详细场景"小节。
**姊妹文档：** 区一 `permission-matrix-resources.md`、区二 `permission-matrix-actions.md`。

> ⚠️ **本轮排查中发现一个尚未修复的权限漏洞，已提单 Issue #75631**：`checkAssetPermission0()` 的 host-org 全局共享 READ bypass 未按资源类型限制，非 host-org 用户可绕开"只共享 viewsheet"的意图，通过 `/ws/open` 直接打开 host-org 的 WORKSHEET（完整结构，非受限视图）。已端到端验证可复现，详见下方"⚠️ 已知安全漏洞"小节。

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
| **Login As 代理登录** | siteAdmin / 持有 `LOGIN_AS` Security Action 的用户切换到目标用户身份 | 代理期间权限以**目标用户**的授权为准，而非操作者本身；退出代理后恢复原身份权限；`LOGIN_AS` Action 本身受区二 Security Actions 管控（For Org √）| `[待补]`（详见下方"Login As 详细场景"小节——权限门槛可测已落地；端到端身份切换阻塞于 **Bug #75613**（[PR #4222](https://github.com/inetsoft-technology/stylebi/pull/4222) 已修复未合并）） |

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

**Bug #75613（[PR #4222](https://github.com/inetsoft-technology/stylebi/pull/4222)，`bug-75613` 分支，已修复未合并）：** Login As 切换身份时的第二次认证，密码校验错误地比对了目标用户的密码 hash，而不是操作者自己刚提交的密码——两人密码不同时，登录直接失败（已验证），不是"界面无报错、实际仍以操作者自己身份继续操作"。根因：第二次 `authenticate()` 一进入就对当前请求执行 `logout(request, false)`，清理掉操作者原有的登录态；随后密码比对又因为比对了错误的用户而失败、返回 `null`，不会创建新 session。`BasicAuthenticationFilter.doFilter()` 里这次调用的返回值被丢弃、`authorized` 仍是操作者第一次登录成功时置的 `true`，但原 session 已被清理，最终效果是登录失败。

**修复**：`SecurityEngine.authenticate(ClientInfo, Object, SecurityProvider)` 里密码比对的目标从 `user.getLoginUserID()`（目标用户）改成 `credential`（`DefaultTicket`）自带的 `getName()`（操作者自己）——即验证"提交这份密码的人是不是他本人"，不再验证"这份密码是不是目标用户的"。目标用户的身份/角色/组/org 仍然在校验通过后按 `user.getUserIdentity()` 正常构造，不受这次改动影响。

**Login As 规则：**

| | 同组织 | 跨组织 |
|---|---|---|
| 权限门槛 | siteAdmin 放行任意目标；非 siteAdmin 需对目标有 `SECURITY_USER`/`ADMIN` 授权，且目标不能是 siteAdmin（`checkLoginAs()`） | 仅 siteAdmin 允许；非 siteAdmin 一律拒绝（`checkLoginAs()` 里 `userOrgId != target.getOrgID()` 分支） |
| 密码校验（修复后） | 校验操作者自己的密码 vs 操作者自己的 hash | 同左，与目标所在 org 无关 |
| 切换后身份/org | session principal 的角色/组/org 全部来自目标用户（`provider.getUser(user.getUserIdentity())` → `realUser`） | 同左；`realUser.getOrganizationID()` 直接给出目标 org，不依赖请求域名或 `ORG_COOKIE` |
| 后续请求的 org 归属 | `RequestPrincipalFilter` 每次请求直接从 session 取回同一个 principal 设进 `ThreadContext`，不读 `ORG_COOKIE`/`OrganizationContextHolder` | 同左，因此同一域名下 login-as 到任意 org 的用户都不会被带偏回操作者自己的 org |

遗留系统性风险（非本 bug 范围，不在本次修复内）：principal 未正确传播到异步/调度/跨节点线程时，`getCurrentOrgId()` 可能解析不到正确 org——这是所有已认证请求共享的通用风险，不是 login-as 特有。

**Login As 的三层门槛（`BasicAuthenticationFilter.doFilter()` 里是 AND 关系，缺一不可）：**

```java
boolean loginAs = "on".equals(SreeEnv.getProperty("login.loginAs")) && !provider.isVirtual();
...
if(provider.checkPermission(principal, ResourceType.LOGIN_AS, "*", ResourceAction.ACCESS) && loginAs) {
   ...
   if(checkLoginAs(principal, provider, loginAsUser)) {
      authenticate(...);   // 真正切换身份
   }
   else {
      // 拒绝：viewer.securityexception
   }
}
```

1. **全局开关**：`login.loginAs=on`（sree 属性）且 provider 不是 virtual——整个功能的总闸门，跟具体操作者是谁无关。
2. **`LOGIN_AS` Security Action 的 `ACCESS` 授权**——操作者本身要被允许"使用 login-as 这个功能"，是一个跟其他功能开关（AI Assistant、Free Form SQL 等）同构的粗粒度 flat action，只回答"这个人能不能打开 login-as 这个入口"，不回答"能 login-as 到谁"。
3. **`checkLoginAs(principal, provider, target)`**——针对**具体目标用户**的细粒度门槛：siteAdmin 放行任意目标；非 siteAdmin 需要同 org + 对**这个具体目标**有 `SECURITY_USER`/`ADMIN` 授权，且目标不能是 siteAdmin。

**这三层是 AND 关系，不是"满足其一即可"**：只有 `LOGIN_AS` action 授权、没有通过 `checkLoginAs()`（比如对这个具体目标没有 `SECURITY_USER`/`ADMIN` 授权，或目标是 siteAdmin）——命中 `doFilter()` 的 `else` 分支，显式拒绝（`viewer.securityexception`）；反过来，`LOGIN_AS` action 本身未授权（或全局开关关闭）——`checkLoginAs()` 根本不会被调用，不是显式拒绝，而是静默不生效：请求按操作者自己身份正常登录，不会切换成目标用户，也不报错。"有了 LOGIN_AS action 权限就能 login-as 任意用户"这个理解不准确：该 action 只是必要条件之一，不是充分条件；反之缺了它也不是"报错"，是"这个功能对这个人不存在"。

**这个 AND 组合此前没有被任何测试验证过，现已补齐**：区二 `PermissionMatrixActionsS8Test` 的 S8-SWEEP 只把 `LOGIN_AS` 当成一个独立 flat action 测"grant→允许、无 grant→拒绝"，不涉及具体目标用户，也不经过 `checkLoginAs()`；区三 `checkLoginAs()` 的 5 个用例只测目标相关的门槛本身，不涉及 `LOGIN_AS` action 授权。两组测试各自独立成立，但都没有验证过 `doFilter()` 里这两者（以及全局开关）的组合关系——`BasicAuthFilterHttpTest` 新增的两个用例（见下方测试状态表"组合场景 A/B/C"）补上了这一点，不依赖 Bug #75613 的修复。

**`checkLoginAs()` 测试的 classpath 限制**：`OrganizationManager.getUserOrgId(Principal)` 在 `community/core` 测试 classpath 上没有 enterprise 覆写，社区基类实现**无条件返回 host-org，不看传入的 principal**，所以这里测的"同 org"对所有非 siteAdmin 操作者都等价于"目标在 host-org"——跟 `SUtil.isMultiTenant()` 那类 community/enterprise classpath 差异同源。

**测试状态：**

| 场景 | 核心断言 | 测试状态 |
|---|---|---|
| `checkLoginAs()` 权限门槛——同组织 | siteAdmin 放行任意目标；非 siteAdmin 同 org + 授权放行、无授权拒绝；目标是 siteAdmin 时拒绝 | `[M9]` `checkLoginAs_siteAdminOperator_anyTarget_allowed` / `checkLoginAs_nonSiteAdminOperator_sameOrgTargetWithSecurityUserAdminGrant_allowed` / `checkLoginAs_nonSiteAdminOperator_sameOrgTargetWithoutGrant_denied` / `checkLoginAs_nonSiteAdminOperator_targetIsSiteAdmin_denied` |
| `checkLoginAs()` 权限门槛——跨组织 | 非 siteAdmin 跨组织目标一律拒绝 | `[M9]` `checkLoginAs_nonSiteAdminOperator_targetInDifferentOrg_denied` |
| `LOGIN_AS` action 本身的 grant/no-grant | 独立 flat action，跟其他功能开关同构，不涉及具体目标 | `[M8]`（区二 `PermissionMatrixActionsS8Test` S8-SWEEP，跟 checkLoginAs() 无重叠，不是重复） |
| **组合场景 A**——有 `LOGIN_AS` action 授权，但对该具体目标 `checkLoginAs()` 不通过 | 非 siteAdmin 已获 `LOGIN_AS` action 授权，但对目标用户无 `SECURITY_USER`/`ADMIN` 授权 → 命中 `doFilter()` 的 `else` 分支，`viewer.securityexception`，`authorized=false`，**显式拒绝**（401），且身份切换用的第二次 `authenticate()` 从未被调用 | `[M9]` `BasicAuthFilterHttpTest.loginAs_actionGrantedButCheckLoginAsFails_returns401AndNeverAttemptsIdentitySwitch` |
| **组合场景 B/C**——外层 `checkPermission(LOGIN_AS) && loginAs` 本身就不成立（`LOGIN_AS` action 未授权，或全局开关 `login.loginAs` 不是 `"on"`） | `checkLoginAs()` 根本不会被调用（外层 `if` 短路），`loginAsUserKey` 被忽略，请求按操作者自己身份**正常登录成功**（200）——不是拒绝，是静默不生效；`authenticate()` 全程只被调用一次（操作者自己的登录），从未尝试身份切换 | `[M9]` `BasicAuthFilterHttpTest.loginAs_outerGateFails_fallsThroughToSelfLoginWithoutInvokingCheckLoginAs`（`@ParameterizedTest`，两个 case：action 未授予 / 全局开关关闭） |
| 端到端身份切换——同组织 | Login-As 后 session principal 是目标用户（角色/组按目标用户），不是操作者 | `[待补]`，阻塞于 PR #4222 合并 |
| 端到端身份切换——跨组织 | siteAdmin login-as 到其他 org 用户后，session principal 的 org 是目标 org；一次权限判断按目标 org 授权走 | `[待补]`，阻塞于 PR #4222 合并 |
| 密码校验对象 | 操作者与目标密码不同时，login-as 仍能成功（校验的是操作者自己） | `[待补]`，阻塞于 PR #4222 合并 |
| 操作者自己密码错误时的失败路径 | 操作者提交的密码本身就不对时，login-as 明确失败（登出/无有效 session），不会残留一个身份/权限对不上的可用 session | `[待补]`，阻塞于 PR #4222 合并 |

**组合场景 A 和 B/C 观察到的行为不一样**：A 命中的是 `doFilter()` 里 `checkLoginAs()` 失败的 `else` 分支（`message=viewer.securityexception`、`authorized=false`，走 HTTP 错误响应，`chain.doFilter()` 不会被调用）；B/C 命中的是外层 `if(checkPermission(...) && loginAs)` 直接为 `false`，整个 login-as 分支被跳过，`authorized` 沿用第一次登录（操作者自己）成功时的 `true`，正常走 `chain.doFilter()`——即请求成功，只是没有发生身份切换。两者都是"当前用户没能 login-as 成功"，但一个有明确错误提示，一个是静默按自己身份登录，行为不一致；本次不额外提单，只在测试里如实断言这个观察到的差异，避免以后有人凭直觉误改成"两者都应该报错"。两个用例都落在 `community/core/src/test/java/inetsoft/web/security/BasicAuthFilterHttpTest.java`（该文件已有的 `FilterTestSupport`/`MockMvc` 基础设施，Mockito mock 出 `SecurityEngine`/`SecurityProvider`/`AuthenticationService`，不需要 `SecurityTestDataBuilder` 那套真实 provider fixture），不依赖 Bug #75613 的修复——拒绝/短路都发生在第二次 `authenticate()` 之前。

**PR #4222 合并后新增用例：**

1. `authenticate_loginAs_sameOrg_validatesOperatorPasswordNotTargetPassword`（`SecurityEngine`/`AuthenticationService` 层）：操作者与目标用户密码不同，走 login-as 路径，断言认证成功且返回的 principal 身份是目标用户。
2. `authenticate_loginAs_crossOrg_targetOrgResolvedFromRealUser`：siteAdmin login-as 到另一个 org 的用户，断言 principal 的 org 是目标 org，并用一次 `checkPermission()` 断言按目标 org 的授权判定（不是操作者所在 org 的授权）。
3. `doFilter_loginAs_wrongOperatorPassword_failsCleanly`（Filter 级）：操作者提交的密码本身错误，断言 login-as 明确失败，且请求结束后没有残留任何可用 session（不是操作者、也不是目标用户）。

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
| host-org 全局共享 Viewsheet 完整行为矩阵（Repository 展示/依赖资源可见性/Portal-only/EM-Composer 限制/clone-org） | 同上 `SUtil.isDefaultVSGloballyVisible()`/`isSharedDefaultOrgDashboard()` 为基础门槛，行为层还涉及 `VSUtil`/`RepletEngine`/`AbstractAssetEngine`/`SaveViewsheetDialogService` 等十余处调用点 | 见下方"host-org 全局共享 Viewsheet 详细场景"小节 | 见下方小节的规则表 | `[待补]`（上面两条 `[M9]` 只测了 `checkPermission()` 这一层的 SHARE/CHART_TYPE 默认放行，不覆盖 Repository 树注入、依赖资源可见性范围、Portal/EM/Composer 差异化限制、clone-org 场景——详见下方分析与候选用例） |

### host-org 全局共享 Viewsheet（`exposeDefaultOrgToAll`）详细场景

**基础门槛（`SUtil.java` L3010-3046）：** `security.exposeDefaultOrgToAll=true`（全局）或 `security.<orgID>.exposeDefaultOrgToAll=true`（org 专属，key 里的 `<orgID>` 取的是**当前查看者**的 org，不是资源所在 org）二选一为真，且 `isMultiTenant()` 为真，且查看者不是 siteAdmin（`isDefaultVSGloballyVisible(Principal)` 才有这条，无参重载没有）——才会把 host-org 的 viewsheet 以只读方式暴露给该查看者。`isSharedDefaultOrgDashboard(AssetEntry)`（L3035-3046）在此基础上再加一条"资源本身确实是 host-org 的、且和当前 org 不同"，是各处业务代码常用的组合判断。

**规则与衍生行为整理（按主题分类，结论，均已在源码核实；已去重——原"全局属性 vs org 专属属性"与"org 专属属性只放行该 org"是同一段代码的重复描述，合并为一行）：**

| 分类 | 结论 | 关键代码位置 |
|---|---|---|
| **生效范围**（属性怎么控制放行给谁） | `security.exposeDefaultOrgToAll=true` 对所有非 host-org 用户生效；`security.<orgID>.exposeDefaultOrgToAll=true` 只对**当前查看者所在 org 恰好是 `<orgID>`** 时生效——两者是 OR 关系，任一为真即放行；其他 org（既无全局开关、自己的 org 专属开关也没开）拿不到任何访问权 | `SUtil.isDefaultVSGloballyVisible()`/`(Principal)` L3010-3033 |
| **可见性范围**（哪些内容会被看到） | Portal Repository 树里专门注入的"全局根节点"会枚举 host-org 的 folder + viewsheet（`RepletEngine.getDefaultOrgRepositoryEntries()` → `getRepositoryEntries(..., isDefaultOrgAsset=true)`），但依赖资源（MV/WS/数据源/library/format/shape/bookmark）不作为独立可浏览项——只在**渲染共享 dashboard 本身**时按已知名称/id 懒加载回退到 host-org（`MVManager`/`SubMV`/`SubMVTask`/`SnapshotEmbeddedTableAssembly`/`TableViewStylePaneController`/`ImageShapes`/`ShapeFrameWrapper`），不是枚举式列表；Composer 的资源树（`AssetTreeController`/`AssetTreeService`）完全不引用这套机制，普通 Data/Library/Format/Shape 面板或树看不到 host-org 内容。`checkAssetPermission0()` 的 READ 放行是权限层面的放行，不等于枚举层面可见——两者是两套独立机制 | `AbstractAssetEngine.checkAssetPermission0()` L3517-3536；`RepletEngine.java` L664-816 |
| **界面访问范围**（能在哪打开） | Portal 能打开（Repository 树 / Edit 图标），EM/Composer 没有浏览入口去发现它——但"不可打开"更准确的说法是"没有浏览入口 + 写入被硬拒绝"的组合效果，**不是**某个统一入口处判断"这是不是 Portal 请求"：Edit 图标本身可以正常从 Portal 进 Composer 查看/改布局（走查看者自己 org 的常规 Toolbar Action 权限，`isAllowedDefaultGlobalVSAction()` L493-509 只是把 `"Edit"`/`"Import"`/`"Schedule"` 排除在自动放行清单外，没有专门拦截）；触发 org 上下文临时切换的 `ViewsheetEngine.openViewsheet(entry, user, viewer)` 判断本身不看 `viewer` 这个布尔参数，STOMP 层的 `MessageScopeInterceptor`/`EventAspect` 的 `@SwitchOrg` 切面同样不区分 Portal 会话还是 Composer 会话。因此"进 Composer 后是否真的看不到数据"**仍未从静态代码找到确凿证据**，需运行时验证 | `ViewsheetEngine.openViewsheet()` L299-329；`EventAspect.java` L534-555（`@SwitchOrg`） |
| **写入限制**（能不能保存） | Composer 里 Save 和 Save As **都会失败**，都会弹出错误提示，不是只拒绝直接覆盖：Save As 对话框在打开前会先调 `getSaveViewsheetInfo()` 预取表单数据，这一步就已经对**原 host-org entry**做 WRITE 校验并失败（对话框根本不会弹出）；直接 Save（`saveViewsheet()`）同样对原 entry 做 WRITE 校验、同样失败。两条路径抛的都是 `MessageException("deny.access.write.globally.visible")`（文案 "Cannot save Dashboard of Default Organization"），前端 `ModelService.handleError()`/STOMP 侧通用错误处理会弹出可见的 "Error" 提示框，不是静默失败。结论：非 host-org 用户在 Composer 里对共享 viewsheet **没有任何方式能把改动保存下来**（覆盖不行，另存为新副本也不行） | `SaveViewsheetDialogService.getSaveViewsheetInfo()` L71-80；`ComposerViewsheetService.saveViewsheet()` L129-196；前端 `composer-main.component.ts` `saveViewsheetAs0()`/`saveViewsheet0()`；`ModelService.handleError()` |
| **关联操作限制**（写入之外，其他动作也被收紧） | MV 按需重建被强制关闭（`mv.ondemand` 开关对共享 dashboard 无效，非 host-org 查看者能看但不能触发 MV 创建/重建）；导出：PDF/PNG/Excel/CSV 等常规格式放行（重定向到真实 host-org `AssetEntry`），VSO 快照格式显式拒绝；Bookmark 标记只读，且拒绝新增 bookmark | `ViewsheetPropertyDialogService.java` L104-105；`PortalMVController.isOrgAccessingGlobalResourceMV()` L93-99；`VSExportService.handleAttemptExportGloballyVisibleAsset()` L427-467（拒绝时抛 `deny.access.export.vso.globally.visible`）；`VSBookmarkService.java`（`readOnly=true`/`checkAddBookmark` 拒绝） |
| **组织克隆**（clone-org 场景） | `copyOrganization()`/`copyDataSpace()` 只把**新 org 自己名下**的 dataspace 内容复制成独立新副本，不触碰原 host-org 资源的 `orgID` 或任何 `SreeEnv` 属性；共享判断（`entry.getOrgID() == host-org` && 查看者 org 专属或全局开关开启）跟 clone 操作完全正交，clone 出来的新 org 用户仍能看到 host-org 原有的共享 viewsheet，和 clone 之前一样 | `AbstractEditableAuthenticationProvider.java` L98-146（`copyOrganization`/`copyOrganizationInternal`）、L444-464（`copyDataSpace`） |

**测试状态（按上表同一分类分组）：**

| 分类 | 场景 | 核心断言 | 测试状态 |
|---|---|---|---|
| 生效范围 | 全局属性开启，非 host-org 用户对 host-org SHARE 资源默认放行 | 已被现有 `[M9]` 用例覆盖（见上方场景表） | `[M9]`（`nonHostOrgUser_shareDefaultVisibility_allowedWhenMechanismOn`，同一机制） |
| 生效范围 | org 专属属性只放行指定 org，其余 org 仍拒绝 | `SUtil.isDefaultVSGloballyVisible(Principal)` 直测：org-scoped 属性只对 A 生效，对没有设置任何属性的 B 不生效——不经过 `checkPermission()`/SHARE，因为 SHARE 的继承走查/org-admin 分支跟 `isMultiTenant()` 有实测无关的交互（会让基于 SHARE 的组合断言变脆弱，见附注新增一条），直测该方法才是对这条规则本身的验证 | `[M9]` `nonHostOrgUser_shareDefaultVisibility_orgScopedPropertyOnlyAllowsNamedOrg` |
| 可见性范围 | `checkAssetPermission0` 的 READ 放行不代表可枚举 | 直接调用底层 `checkAssetPermission(READ)` 对 host-org 资源放行，但 `AssetTreeController`/普通 Data 树查询返回结果不含 host-org 条目 | `[不补]`（涉及 Spring MVC 树查询控制器层，跟本类"直调 `SecurityEngine.checkPermission()`"的定位不符；"Composer 资源树不引用这套机制"这一事实已通过 grep 静态确认为零引用，是可验证的确定性事实，不是需要运行时兜底的猜测，为这一点单独搭建控制器级测试基础设施收益不成比例。**但这条"没有浏览树"只是没有 UI 入口，不是访问控制——见下方"⚠️ 已知安全漏洞"小节，该 bypass 本身没有按资源类型限制，可以绕开树/UI 直接命中**） |
| 写入限制 | Composer Save As 预取阶段即因 WRITE 硬拒绝失败（对话框根本不弹出） | 非 host-org 用户对共享 host-org entry 调用 `getSaveViewsheetInfo()` 断言抛出 `deny.access.write.globally.visible`（文案 "Cannot save Dashboard of Default Organization"）；对照用例确认同 org（非共享）时该 WRITE 校验整段跳过，未被误拦 | `[M9]` `SaveViewsheetDialogServiceTest.getSaveViewsheetInfo_nonHostOrgUser_sharedHostOrgEntry_deniedBeforeDialogPopulated` / `getSaveViewsheetInfo_sameOrgUser_ownEntry_writeCheckNotBypassed`（直接 Save 的 `ComposerViewsheetService.saveViewsheet()` 路径未覆盖，逻辑同构，评估为可选） |
| 关联操作限制 | VSO 快照导出拒绝，常规格式放行 | 同一共享 viewsheet 分别请求 VSO 导出（拒绝，`deny.access.export.vso.globally.visible`）和 PDF 导出（放行并重定向到真实 host-org entry） | `[M9]` `VSExportServiceTest.handleAttemptExportGloballyVisibleAsset_vsoSnapshot_deniedDespiteEntryResolving` / `handleAttemptExportGloballyVisibleAsset_pdfFormat_allowedAndRedirectsToRealHostOrgEntry` |
| 组织克隆 | clone-org 后共享行为不变 | 静态代码确认：`copyScopedProperties()` 只匹配 `inetsoft.org.<fromOrgId>` 前缀的属性键，跟 `security.<orgID>.exposeDefaultOrgToAll` 这个键的命名空间结构性不相交，clone 不可能复制/触发/清除这个属性；`copyDataSpace()` 只搬运新 org 自己名下的路径，不改原 host-org 资源的 `orgID`。两点结合已经是确定性证明，不是需要运行时验证的猜测 | `[不补]`（已通过静态代码证明，且 `AbstractEditableAuthenticationProviderStaticDepTest` 已有 `copyScopedProperties_noMatchingProperty_onlyFinalSaveCalled` 等测试泛化覆盖"不匹配前缀的属性不受影响"这一路径；再为 `exposeDefaultOrgToAll` 这一个具体属性单独立一个组合测试是重复覆盖） |
| 界面访问范围 | Composer 内是否真的看不到数据 | 需要运行时/集成级验证，静态代码未能确认 viewer 与 composer 在数据加载路径上有实质区别 | `[不确定]`（不建议在 `PermissionMatrixSpecialTest` 这类直调权限方法的单元测试里验证，需要更贴近真实渲染管线的集成测试；是否值得单独立项待用户确认） |

### ⚠️ 已知安全漏洞：`checkAssetPermission0` 的 READ bypass 未按资源类型限制（**Issue #75631**，已提单未修复）

**结论：** `AbstractAssetEngine.checkAssetPermission0()`（L3526-3530）里"host-org 全局共享"的 READ 放行分支完全不检查 `entry.getType()`——只要 `entry.getOrgID()` 是 host-org，任意类型的 `AssetEntry`（不只是 VIEWSHEET，也包括 WORKSHEET、library/script、table style 等）对任何非 host-org、非 siteAdmin 用户都放行 READ。区四场景表"可见性范围"一行描述的"依赖资源不可独立浏览"只是**没有浏览树 UI 去发现它**，不是访问控制——这条 bypass 本身就是敞开的。

```java
// AbstractAssetEngine.java:3526-3530
if(Tool.equals(permission, ResourceAction.READ) && SUtil.isDefaultVSGloballyVisible(user) &&
   Organization.getDefaultOrganizationID().equals(entry.getOrgID()) &&
   user != null && !((XPrincipal)user).getOrgId().equals(Organization.getDefaultOrganizationID())) {
   return true;
}
```

**完整复现链路（已逐跳读码验证，非推测）：**

1. `OpenWorksheetController.openWorksheet()`（`/ws/open` STOMP 端点，`community/core/src/main/java/inetsoft/web/composer/ws/OpenWorksheetController.java:92-107`）：唯一前置检查是 `checkPermission(principal, ResourceType.WORKSHEET, "*", ACCESS)`——跟具体资源、跟 org 都无关的功能总开关，任何有 composer 权限的普通用户都满足。随后 `AssetEntry entry = AssetEntry.createAssetEntry(event.id())`，`event.id()` 是客户端原始提交的字符串，未做任何签名/校验。
2. `AssetEntry.createAssetEntry(String identifier, String orgID, boolean forceUpdateOrgID)`（`AssetEntry.java:472-473`）：`orgID = forceUpdateOrgID || index==-1 || scope==TEMPORARY_SCOPE ? orgID : identifier.substring(index+1)`——`OpenWorksheetController` 调用的是 `forceUpdateOrgID=false` 的重载，标识符末段 `^orgID` 会被原样采信。客户端可以直接构造一个以 `^host-org` 结尾的标识符字符串。
3. `WorksheetEngine.openSheet()`（`permission = !"true".equals(entry.getProperty("isDashboard"))` = true，该属性从未被 `/ws/open` 设置）→ `AbstractAssetEngine.getSheet()`（`AbstractAssetEngine.java:2371`）：`checkAssetPermission(user, entry, READ, true)` → 命中上面的 bypass → 放行。
4. 全链路没有任何一层能拦下来：`@SwitchOrg` 切面不作用于本 controller 的方法；`MessageScopeInterceptor` 只在 STOMP 帧带 `sheetRuntimeId`（已开 runtime）时才生效，新开 worksheet 的请求没有这个 header；`getForbiddenSourcesMessage()` 的按数据源二次校验只在 `POST /api/ws/open` 的"是否提示恢复自动保存"预检接口里跑，真正打开走的 STOMP `/ws/open` 完全不经过它。

**标识符不需要猜——可以合法拿到：** 非 host-org 用户按上方"界面访问范围"一行走 Portal Edit 图标进 Composer 打开共享的 host-org viewsheet 后，`ViewsheetPropertyDialogService.getViewsheetInfo()`（`ViewsheetPropertyDialogService.java:118`：`newVSDialogModel.setDataSource(viewsheet.getBaseEntry())`）会把底层 worksheet 的完整 `AssetEntry`（含 `orgID=host-org`）原样序列化进返回给前端的 JSON。拿到这个标识符后原样回放到 `/ws/open`，就能在自己的 Composer 里把这个 worksheet **完整打开**——table assembly、字段绑定、SQL/query binding、mirror 引用全部可见，不是"只能看布局、看不到数据"那种受限体验（那个限制只针对 Edit 图标直接进入的那个 viewsheet 本身，对通过标识符另开的底层 worksheet 完全不适用）。

**影响范围：**
- 波及：WORKSHEET（已验证端到端可复现）；理论上同样波及 library/script、table style 等其他 `AssetEntry` 类型，只要存在类似"按客户端标识符直接打开"的入口（未逐一验证）。
- 不波及：`ResourceType.DATA_SOURCE`（数据源连接定义/凭据）——数据源走的是完全独立的 `SecurityEngine.checkPermission(DATA_SOURCE,...)`/`DefaultCheckPermissionStrategy` 机制，`isAllowedDefaultGlobalVSAction()` 的放行清单里明确只有 `CHART_TYPE`/`SHARE`/`VIEWSHEET_TOOLBAR_ACTION` 三项，不含 `DATA_SOURCE`，因此凭据不在泄露面内。

**建议修复方向：** 给 `checkAssetPermission0()` 这段 bypass 加类型限制（如 `entry.isViewsheet()`），只放行 VIEWSHEET，匹配文档一直描述的"只共享 dashboard"初衷；修复后补一条回归测试，断言 WORKSHEET（以及其他非 viewsheet 类型）在 `exposeDefaultOrgToAll` 开启时仍然拒绝。

**测试状态：** `[待补]`（阻塞于 **Issue #75631** 修复本身——修复前写"应该拒绝"的测试会红；建议随修复一起提交）

### 附注

- **host-org 机制的方向订正**：`isOpeningShareGlobalAsset()` 受益的是**非 host-org 的用户**（`orgID != currOrgID` 且 `currOrgID == host-org`），不是"host-org 自己的用户有特殊默认值"——跟直觉相反，命名容易让人误解。
- `SUtil.isDefaultVSGloballyVisible()` 要求 `SUtil.isMultiTenant()` 为真，而这在 `community/core` 测试 classpath 里结构性恒为 `false`（`LicenseManager.isEnterprise()` 探测不到 classpath 上的 `inetsoft.enterprise.EnterpriseConfig`），跟 `PermissionMatrixActionsS6Test` 同样的环境限制，需要 `Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS)`。
- SHARE 资源类型本身在 `DefaultCheckPermissionStrategy` 的默认放行清单上（跟 `CHART_TYPE_FOLDER` 等同属一批），所以测试对照用的 SHARE 资源需要 `markPermissionEdited()` 显式标记成"已配置但无授权"，才能拿到真实的 baseline 拒绝；`CHART_TYPE` 未选用，因为它有自己已测过的另一套默认放行机制（`SecurityEngine` 的 CHART_TYPE 父级重试回退，Bug #70538），混在一起会分不清是哪个机制起作用。
- `hasOrgEditedGrantAll()` 的 org 判断走的是 `OrganizationManager.getCurrentOrgID()`（ThreadContext 相关的当前上下文 org），不是被检查用户自己的 org——跟 `MultiTenantIsolationTest.scenario18A` 遇到的问题同源。"机制关闭"用例必须显式 `ThreadContext.setContextPrincipal(该用户)` 让两者对齐，否则会因为默认落到 host-org 而误触发跟本场景无关的默认放行清单；"机制开启"用例则相反，必须保持 `ThreadContext` 为空，让当前 org 上下文默认落到 host-org，这正是 `isOpeningShareGlobalAsset()` 需要的条件。
- SELF 和 host-org 都会在 `FileAuthorizationProvider.LoadPermissionsTask.initialize()`（L335-341）里各自跑一遍 `addDefaultAdminPermissions()` + `addDefaultRoleGrants()`，两者共享的默认授权路径（`COMPOSER`/`WORKSHEET`/`VIEWSHEET`/`DASHBOARD`/`SCHEDULE_TIME_RANGE`）走的是角色/组织授权，不是本文档要覆盖的差异点；本文档只聚焦 SELF **额外**拿到的 `addDefaultPermissionForSelfOrg()` 授权，以及 host-org **专属**的 `isOpeningShareGlobalAsset()` 机制。
- **`security.<orgID>.exposeDefaultOrgToAll` 不适合通过 SHARE 资源 + `checkPermission()` 来验证 org 隔离**：实测发现，当 `SUtil.isMultiTenant()` 为真时，`DefaultCheckPermissionStrategy.checkPermission()` 对 SHARE 类型走的继承查找/org-admin 分支会产出跟这个属性完全无关的放行结果（具体触发条件未能在排查预算内定位到单一根因，怀疑是 `ActionPermissionService.isOrgAdminAction()`/`checkOrgAdminPermission()` 组合路径），即使目标 org 的 SHARE 资源已用 `markPermissionEdited()` 标记成"已配置但无授权"也拦不住。这条规则改为直测 `SUtil.isDefaultVSGloballyVisible(Principal)` 本身（见上表"生效范围"行），绕开这套无关交互；`isOpeningShareGlobalAsset()`/`isAllowedDefaultGlobalVSAction()` 层面的验证仍由已落地的两条 `[M9]` 用例覆盖（同一张表"host-org CHART_TYPE/SHARE 全局默认可见性"行），不受影响。
