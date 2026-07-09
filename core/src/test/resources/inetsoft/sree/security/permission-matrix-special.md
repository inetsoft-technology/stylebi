# Permission Test Matrix — 区三/区四：认证上下文与特殊组织默认行为

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 2 M9 实现：** Task 7（`MultiTenantTestFixture` 内置 org 扩展）评估后确认无需改动；Task 8（`PermissionMatrixSpecialTest`）区四部分已落地，区三部分待补，见 `2026-06-30-permission-test-phase2.md`。
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
| **Google OAuth SSO** | Google OAuth SSO（`googleUser`）| 认证成功后权限解析与本地用户一致；org 归属、role 映射正确落地；不因认证来源差异绕过权限检查 | `[待补]` |
| **账号非活跃状态** | 用户账号被禁用（inactive / disabled）| 无论持有何种权限 grant，登录被拒或 session 立即失效；已有 session 的非活跃用户下次请求被拦截 | `[待补]` |
| **Login As 代理登录** | siteAdmin / 持有 `LOGIN_AS` Security Action 的用户切换到目标用户身份 | 代理期间权限以**目标用户**的授权为准，而非操作者本身；退出代理后恢复原身份权限；`LOGIN_AS` Action 本身受区二 Security Actions 管控（For Org √）| `[待补]` |

每个场景通路测试只需验证：
1. 认证/状态变更后 session 建立或失效符合预期
2. 至少一个资源访问断言（allowed 或 denied）与预期一致
3. 不会因该特殊路径绕过区一/区二的正常权限逻辑

> org self-signup 的组织落地/默认权限场景不放在这里——测的是"落地哪个 org、该 org 默认给什么权限"，跟本区"认证能否被触达"是不同维度，见下方区四场景表（评估后确认 `[不补]`）。

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
