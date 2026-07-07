# Permission Test Matrix — 区一：内容访问权限（Resources）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）
**Phase 2 M8 实现：** 按切片拆成 4 个测试类——`PermissionMatrixResourcesS2Test`/`S3Test`/`S4Test`/`S5Test`（原计划是单个 `PermissionMatrixResourcesTest` + `MatrixTestCase` 参数化 DSL，已废弃，见 `docs/superpowers/plans/2026-06-30-permission-test-phase2.md` 的"修订说明"）
**姊妹文档：** 区二 Security Action 权限矩阵（S6-S8，`PermissionMatrixActionsTest`）见 `permission-matrix-actions.md`

图例：✓ = allowed　✗ = denied　— = n/a　`(ADMIN→)` = 由 ADMIN 隐含授权　`[P1]` = Phase 1 已覆盖

**"测试状态"列取值**（每个场景表都有这一列，用来回答"这一行有没有被自动化测试覆盖"）：
- `[M8]` = 已在对应切片的 `PermissionMatrixResourcesS{N}Test.java`（如 S2 → `PermissionMatrixResourcesS2Test`）落地并通过，后面附对应的 `@Test` 方法名
- `[M8⚠️]` = 已落地，但实测结果与本表"预期"列不符，测试已 `@Disabled` 并注明原因（不会静默地把矛盾的断言标成"通过"）
- `[附加]` = M7 切片计划未分配、本文档补记的基线抽样（见文末"附加"小节）
- `[待补]` = 已在设计文档确认、尚未落到测试代码，后面附未落地的具体原因（缺 fixture 能力/需要先验证约定/依赖其他场景先落地等）

下方按切片（Slice）列出具体场景，每一行大致对应对应切片测试类（`PermissionMatrixResourcesS2Test`/`S3Test`/`S4Test`/`S5Test`）里的一个独立 `@Test` 方法，方便实现前逐条 review。资源路径为 fixture 示例路径，不是生产环境真实路径。总览矩阵（按资源组归类的能力速查表）见文末附录。

### 资源组速查表（完整版见架构设计文档 § 区一 534-542 行）

| 资源组 | 代表 ResourceType | 适用 action | 层级继承 | 本文档覆盖情况 |
|---|---|---|---|---|
| Repository（Viewsheet 树） | `REPORT` | READ/WRITE/DELETE/ADMIN | 是，`/` 分隔真实父子继承 | S3/S4/S5 各有 `*-CROSS-GROUP` 抽样 `[待补]` |
| Worksheet | `ASSET` | READ/WRITE/DELETE/ADMIN | 是，`/` 分隔真实父子继承 | S3/S4/S5 主表全覆盖 |
| Portal Dashboard | `DASHBOARD` | READ/WRITE/DELETE/ADMIN | **否**——扁平命名空间，`DASHBOARD.getParent()` 恒返回 `null` | 无切片分配，仅文末附加基线抽样 `[待补]` |
| 数据源 | `DATA_SOURCE_FOLDER`/`DATA_SOURCE`/`CUBE`/`QUERY`（Logical Model） | READ/WRITE/DELETE/ADMIN | 是——文件夹用 `/`，model/cube 挂载用 `::` | 仅 S5-RULE4 覆盖 Rule 4 提升逻辑，基础 ADMIN/继承未验证 `[待补]` |
| 调度 | `SCHEDULE_TASK_FOLDER`/`SCHEDULE_TASK`/`SCHEDULE_CYCLE` | READ/WRITE/DELETE/ADMIN/ASSIGN | 仅文件夹层继承；task/cycle 本身扁平 | 无切片分配，仅文末附加基线抽样 `[待补]` |
| 安全管理 | `SECURITY_USER`/`SECURITY_GROUP`/`SECURITY_ROLE`/`SECURITY_ORGANIZATION` | User/Group 走 ADMIN，Role 走 ASSIGN | 各类型独立合成根节点级联，非路径分隔符继承 | S2（按 S2.1-S2.5 分资源类型组织）：S2.1/S2.2/S2.4 实例级+根节点级联场景、S2.2 的 S2-GRANTEE-VARIETY、S2.3 的 S2-GROUP-CHAIN 已在 `PermissionMatrixResourcesS2Test` 落地 `[M8]`；S2.3 的 identityAdmin-group(通配) 调查后确认不是独立机制——EM UI 上等价于根节点级联，已并入 `rootGroupAdmin` 覆盖，不再单独列为待办；S2.4 的 identityAdmin-role WRITE/DELETE 断言（Issue #75567，已于 commit `b9049488a` 修复）`[M8]`；S2.1 的 orgSecurityAdmin 全局角色负控制、S2.4 的 S2-GLOBAL-ROLE-ROOT 根节点互相越界，两处独立发现复测仍为实测分歧，`@Disabled` 状态不变，见各自 `[M8⚠️]` 说明（根因各不相同） |
| 库资源 | `SCRIPT_LIBRARY`/`SCRIPT`（扁平单层） / `TABLE_STYLE_LIBRARY`/`TABLE_STYLE`（真正多级） | READ/WRITE/DELETE/ADMIN | Table Style 是，`~` 分隔；Script 否，固定单层 | 无切片分配，仅文末附加基线抽样 `[待补]` |

---

> **无 S1 切片**：siteAdmin/orgAdmin 对全部资源和 action 的完全访问是这两个角色的定义性质（`DefaultCheckPermissionStrategy` 对 `isSystemAdministrator`/`isOrgAdministrator` 的判断在查任何资源 grant 之前就直接放行），不依赖 fixture 里配置的具体权限，逐资源组重复断言价值很低，故不单独设切片。跨 org 负路径已由 Phase 1 `MultiTenantIsolationTest`（场景 13-18B）覆盖。siteAdmin/orgAdmin 仍会作为对照身份出现在 S2（本文档）以及 S6/S7（`permission-matrix-actions.md`）里。

## S2 — 安全身份管理边界（SECURITY_* 资源组）

**术语约定（本节及以下所有 S2 子表通用；与 `2026-06-25-permission-test-architecture-design.md` 452-458 行的术语辨析保持一致）：**
- **对 `<resource>` 设置 "Administrator Permissions"** = 在 `<resource>` 对应的 `Permission` 对象上，为某个身份（User/Group/Role）写入 `ADMIN` action。例如给 `targetUser2`（一个 `SECURITY_USER` 资源）设置 "Administrator Permissions"，等价于在 EM 的 Security > Users > `targetUser2` > Permission 标签页里，把某个身份勾进 Administrator 列，底层写入的就是 `` `ADMIN` `` action。测试代码通过 `SecurityTestDataBuilder` 直接写这个 `Permission` 对象，不经过 EM UI，也没有一个"操作者"用户去点这个按钮——它只是 fixture 静态构造出的一条规则："资源 X 的 Permission 上，身份 Y 被赋予 `ADMIN`"。
- **"用户类型"/"登录用户"列指的是登录去做权限检查的测试账号**，它不一定等于被设置了 "Administrator Permissions" 的那个身份本身：
  - 多数行里，登录用户本身就是被授权的身份（例如 `identityAdminUser` 直接对 `targetUser` 设置了 "Administrator Permissions"），这种情况登录用户＝被授权身份，是同一个对象。
  - 少数行里（典型例子见下面 S2.2 的 S2-GRANTEE-VARIETY），被授权的身份是一个 Role/Group（例如 `targetUser2` 的 "Administrator Permissions" 授予了 `role0`），登录用户是**持有该 role / 属于该 group 的另一个 user**（例如 `viaRoleUser`）——登录用户自己从未被单独授权过，是靠"持有 role0"这层间接关系拿到权限的。这两种情况在表格里都会用括号注明具体设置，避免"谁被授权了什么"产生歧义。

本节按资源类型分为 S2.1 Organization / S2.2 Users / S2.3 Groups / S2.4 Organization Roles + Roles / S2.5 其他情况五类。

**机制速查表**（同一机制在不同资源类型下重复出现，横向对比见下表；纵向的具体场景/测试方法见对应资源类型小节）：

| 机制 | 一句话 | 详见 |
|---|---|---|
| 跨类型级联 | `SECURITY_ORGANIZATION` ADMIN → 级联到本 org 内 User/Group/org 自建 Role 的任意实例，不看具体资源路径；对全局角色**不**应该生效，但实测生效（已确认违反预期，Issue #75574） | S2.1 |
| 实例级直授 | 对某个具体 User/Group/Role 资源单独设 Administrator Permission（Role 是 ASSIGN），只对该实例生效，不跨实例 | S2.2 / S2.3 / S2.4 各自"实例"小节 |
| 通配符 | 仅 `SECURITY_USER` 有此机制：`provider.getPermission(SECURITY_USER, orgID)`（2-arg，key 是 org id 字符串本身），对本 org 全部用户生效，但该 key 本身也只能通过底层 API 写入，EM UI 触达不到；`SECURITY_GROUP` **已确认没有这第二条机制**——EM UI 上给 `"Groups"` 节点设权限，走的就是根节点级联那一条路径，两者是同一回事，不是"缺了一个功能" | S2.2 / S2.3 |
| 根节点级联 | 对 `"Users"`/`"Groups"`/`"Organization Roles"`/`"Roles"` 四个根节点资源设 ADMIN，级联到该类型全部实例；Role 有两个根，理论上应互相独立，实测未隔离（已确认违反设计意图） | S2.2 / S2.3 / S2.4 |
| Group 链式继承 | Group 对子孙组（≥3 层 BFS）传播委派权限，不向上穿透到父组/根节点 | S2.3 |
| 被授权身份多样性 | Administrator Permission 的被授权方不仅可以是 User，也可以是 Role/Group（含子组间接继承），多个身份间是 OR 关系 | S2.2 |

### S2.1 — Organization（`SECURITY_ORGANIZATION`：跨类型级联能力及边界）

orgSecurityAdmin 的授权点是 `SECURITY_ORGANIZATION` 资源本身；本节验证的是"对本 org 的 `SECURITY_ORGANIZATION` 设 ADMIN"这一单一授权能级联到哪些其它资源类型（正向能力），以及在哪里应该被挡住却没被挡住（边界/负控制）。User/Group/Role 各自的实例级、通配符、根节点级联等机制见 S2.2/S2.3/S2.4，此处不重复。

**Action：给 orgSecurityAdmin 授予组织节点（`SECURITY_ORGANIZATION`，`matrix_org_id`）的 Administrator Permission**——本表全部行共用这一次授权，不额外在 `"Users"`/`"Groups"`/任何具体资源上加授权；下表"检查资源类型/检查Action"两列是拿这一次授权去逐个验证能不能触达的目标，不是另外的授权点。

下表按"检查资源类型"分组排列（同一资源类型的不同示例/Action 相邻），方便对比"同一次组织授权在各资源类型上的效果"：

| 登录用户 | 检查资源类型 | 资源(示例) | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| orgSecurityAdmin | SECURITY_ORGANIZATION | `matrix_org_id` | ADMIN | ✓ | `[M8]` `orgSecurityAdmin_adminOnOwnOrgSecurityOrganization_allowed` | 授权本身生效（后面几行"拒绝/允许"是否有意义的前提） | orgSecurityAdmin 能管理本 org 的安全设置 |
| orgSecurityAdmin | ASSET | `mx/folder/item` | READ | ✗ | `[M8]` `orgSecurityAdmin_noContentPermission_assetReadDenied` | 无内容权，证明不外溢到非安全身份资源类型 | orgSecurityAdmin 看不到任何内容资源（报表/仪表盘等） |
| orgSecurityAdmin | SECURITY_USER | `targetUser`（从未单独 grant 给 orgSecAdmin） | ADMIN | ✓ | `[M8]` `orgSecurityAdmin_crossTypeCascade_allowedWithoutDirectGrant`（参数化，case `securityUserAdmin_allowedWithoutDirectGrant`） | **跨类型级联**（`DefaultCheckPermissionStrategy` L57-67）：只看当前 org 的 SECURITY_ORGANIZATION 是否有 ADMIN，不看具体 resource 路径 | orgSecurityAdmin 能管理 targetUser（编辑/删除），即使从没单独针对 targetUser 授权过 |
| orgSecurityAdmin | SECURITY_USER | `targetUser` | WRITE | ✓ | `[M8]` `orgSecurityAdmin_crossTypeCascade_grantsOriginalAction_notAdminOnly` | **级联放行的是原始 action**，不是"隐式提升成 ADMIN 再走 RWD 兜底" | orgSecurityAdmin 能编辑 targetUser 的信息 |
| orgSecurityAdmin | SECURITY_GROUP | `targetGroup`（同上，从未单独 grant） | ADMIN | ✓ | `[M8]` `orgSecurityAdmin_crossTypeCascade_allowedWithoutDirectGrant`（参数化，case `securityGroupAdmin_allowedWithoutDirectGrant`；测试用等价的 `anotherGroup`，同样从未单独 grant） | 同上，跨类型级联 | orgSecurityAdmin 能管理 targetGroup，同样即使从没单独授权过 |
| orgSecurityAdmin | SECURITY_ROLE | `viewerRole`（本 org 自建角色，从未单独 grant） | ADMIN | ✓ | `[M8]` `orgSecurityAdmin_crossTypeCascade_allowedWithoutDirectGrant`（参数化，case `orgOwnedRoleAdmin_allowedWithoutDirectGrant`；测试用等价的 `anotherRole`，同样从未单独 grant） | 同上；仅对本 org 自建角色生效，全局角色不生效（`isNotGlobalRole`） | orgSecurityAdmin 能管理本 org 自建的 viewerRole 角色（编辑/删除/分配） |
| orgSecurityAdmin | SECURITY_ROLE | `viewerRole` | ASSIGN | ✓ | `[M8]` `orgSecurityAdmin_crossTypeCascade_grantsOriginalAction_notAdminOnly`（同一方法，测试用等价的 `anotherRole`） | 同上，ASSIGN 不属于 R/W/D，如果是 ADMIN→RWD 兜底机制就不会覆盖到它，证明这是两套不同机制 | orgSecurityAdmin 能把 viewerRole 分配给其他用户 |
| orgSecurityAdmin | SECURITY_ROLE | 全局角色(org-less，非 sysAdmin)，对照内置 `Organization Administrator`（`addGlobalRole("globalRole0")`） | ADMIN | ✗ | `[M8⚠️]` `orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl`（`@Disabled`，JUnit 和真实生产环境都实测为允许，**确认是生产 bug，非测试假象**，见下方发现说明） | 负控制：`isNotGlobalRole` 确实挡住了 L57-67 那条级联，但 `checkOrgAdminPermission()`（L525-616）是另一条独立的级联，其 SECURITY_ROLE 分支（L591）显式把"角色 org 为 null 且角色自身非 sysAdmin"也判为可管理，不受 `isNotGlobalRole` 约束 | orgSecurityAdmin 管不了全局角色（跨 org 共享），只有 siteAdmin 或持有全局 `Administrator` 角色的人才能管——**已确认违反产品预期，且已在真实部署里用直接 API 调用复现（绕开了 UI 树的隐藏），不是测试环境的假象** |
| orgSecurityAdmin | SECURITY_ROLE | 全局角色(org-less，sysAdmin=true)，即内置 `Administrator` 本尊 | ADMIN | ✗ | `[M8⚠️]` `orgSecurityAdmin_adminOnGlobalSysAdminRole_denied_negativeControl`（`@Disabled`，**生产环境实测允许**，但同形状的 JUnit 复现返回拒绝——两者矛盾，根因未完全定位，见下方发现说明） | 同上机制，但 `checkOrgAdminPermission()` 的 `!isSiteAdmin` 分支理论上应该保护住 sysAdmin 角色本身；真实环境用直接 API 调用（`GET .../roles/Administrator~~_3b_~~__GLOBAL__/`）实测返回 200 且 `editable:true`，跟这条防护矛盾 | orgSecurityAdmin 管不了内置 `Administrator` 角色——**生产环境已确认违反预期（实测能看到、能编辑），但具体是哪条代码路径放行的还没查清楚，需要针对真实/预发环境再排查一次** |

> **`[M8⚠️]` 发现：orgSecurityAdmin 能管理全局角色——已确认生产 bug，非测试假象**
>
> 上面两行全局角色负控制，跟下面 S2.4 的 ASSIGN/ADMIN 那条不同：**不只是 JUnit 里实测矛盾，已经在真实部署的 EM 上用浏览器直接复现**。复现方式：用只持有 `SECURITY_ORGANIZATION` ADMIN（没有 Organization Administrator 角色、不是 site admin）的 orgSecurityAdmin 账号登录 EM，Security > Users 树里正确地看不到 `Roles`（全局根）节点——UI 这一层没问题。但在浏览器 Console 里用同一个登录 session 直接调后端接口（绕开树，不经过 UI 点击）：
>
> ```
> GET /sree/api/em/security/providers/Primary/roles/Organization%20Administrator~~_3b_~~__GLOBAL__/
> GET /sree/api/em/security/providers/Primary/roles/Administrator~~_3b_~~__GLOBAL__/
> ```
>
> 两个内置全局角色**都返回 HTTP 200 和完整角色数据，其中 `"editable":true`**——即接口本身没有守住这道边界，只有 UI 树把入口藏起来了，是"只在前端隐藏、后端没有独立校验"的反模式，任何拿到合法 orgSecurityAdmin session 的人都能通过直接调接口绕过。
>
> **两个角色的根因目前定位到不同程度：**
> - **`Organization Administrator`**（org=null，sysAdmin=false，orgAdmin=true）：根因已经在 JUnit 里精确复现并定位，见 `orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl` 的注释——`checkOrgAdminPermission()`（`DefaultCheckPermissionStrategy.java` L525-616）的 `SECURITY_ROLE` 分支（L591）显式把"角色 org 为 null 且角色自身不是 sysAdmin"也判定为可管理，是一条完全独立于 `isNotGlobalRole()` 级联（L58-67）的放行路径。
> - **`Administrator`**（org=null，sysAdmin=true）：生产环境已确认同样暴露（实测 200 + `editable:true`），**但根因还没有完全定位**。`checkOrgAdminPermission()` 的 `!isSiteAdmin` 分支理论上应该专门保护"角色自身是 sysAdmin"的这种情况，在 JUnit 里用完全同形状的角色复现时，这条分支以及方法里其它所有级联路径都返回拒绝，跟生产环境的实测结果矛盾。已知的差异候选：`RoleController.getRole()`（`RoleController.java` L232-236）在算 `editableRoles` 返回字段时，额外单独调用了一次 `checkPermission(..., ResourceAction.ASSIGN)`（不是 `@Secured` 网关用的 `ADMIN`），这条路径本次 JUnit 复现没有覆盖到；也不能排除生产/预发环境本身（多 provider、缓存等）跟这个隔离的单 provider fixture 有其它未知差异。**在有人对着真实/预发环境再排查一次、把这条路径也钉死之前，不要假设"改完 `Organization Administrator` 那条分支就等于修完了"。**（这条未定位的开放问题也是 S2.5 的占位内容，见文末）

### S2.2 — Users（`SECURITY_USER`）及子节点

覆盖机制：实例级直授、通配符、根节点级联（`"Users"` 根）、被授权身份多样性。orgSecurityAdmin 跨类型级联管理任意 User 实例的能力见 S2.1，此处不重复。

**identityAdmin-user(实例)**

**Action：给 identityAdminUser 授予 `targetUser`（`SECURITY_USER`）的 Administrator Permission**——只授权这一个具体用户实例，不碰通配符或根节点。

| 登录用户 | 检查资源类型 | 资源(示例) | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| identityAdminUser | SECURITY_USER | `targetUser` | ADMIN | ✓ | `[M8]` `identityAdminUser_adminOnTargetUser_allowed` | | identityAdminUser 能管理 targetUser（编辑/删除该用户） |
| identityAdminUser | SECURITY_USER | `anotherUser` | ADMIN | ✗ | `[M8]` `identityAdminUser_adminOnAnotherUser_denied_doesNotCrossInstance` | 负路径：不越实例 | identityAdminUser 看不到/管不了 anotherUser——只对被明确授权的那个用户生效 |
| identityAdminUser | SECURITY_USER | `targetUser` | WRITE | ✓ | `[M8]` `identityAdminUser_writeAndDeleteOnTargetUser_allowed_adminImpliesRWD` | ADMIN→隐含（`SecurityEngine` L826-832，通用兜底非 User 专属） | identityAdminUser 能编辑 targetUser 的信息 |
| identityAdminUser | SECURITY_USER | `targetUser` | DELETE | ✓ | `[M8]`（同一方法，见上一行） | 同上 | identityAdminUser 能删除 targetUser |

**identityAdmin-user(通配)**

**Action：给 identityAdminWildUser 授予 `SECURITY_USER` 通配符（`provider.getPermission(SECURITY_USER, orgID)`，key 是 org id 字符串本身）的 Administrator Permission**——不针对任何具体用户实例。

| 登录用户 | 检查资源类型 | 资源(示例) | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| identityAdminWildUser | SECURITY_USER | `targetUser` / `anotherUser` | ADMIN | ✓ / ✓ | `[M8]` `identityAdminWildUser_adminOnAnyUser_allowed`（参数化，两个 case） | 已确认：生产代码通配符检查是 `provider.getPermission(SECURITY_USER, orgID)`（`DefaultCheckPermissionStrategy.java` L369-376）——2-arg 重载委托给 `getPermission(type, orgID字符串, null)`，`null` org 参数在 `AuthorizationChain.fixOrgID()` 里被解析成 `OrganizationManager.getCurrentOrgID()`（即 `withContextPrincipal` 设置的当前 org）。resource key 是裸的 org id 字符串本身，不是 `"*"`，也不是 `IdentityID` key——跟 orgSecurityAdmin 那套 `convertToKey()` 约定完全不同 | identityAdminWildUser 能管理 EM 里的所有用户，包括 targetUser 和 anotherUser |
| identityAdminWildUser | ASSET | `mx/folder/item` | READ | ✗ | `[M8]` `identityAdminWildUser_noContentPermission_assetReadDenied` | 仍然拿不到内容权 | identityAdminWildUser 虽然能管理所有用户，但看不到任何内容资源 |

**根节点级联（`"Users"` 根）**

**Action：给 rootUserAdmin 授予 `"Users"` 根节点（`SECURITY_USER:"Users"`，字面意义上的具体资源，不是抽象概念）的 Administrator Permission**——下表"资源"列的 `targetUser`/`anotherUser` 是级联**验证目标**，不是授权点本身，本身没有被单独授权。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootUserAdmin | SECURITY_USER | `targetUser` / `anotherUser` | ADMIN | ✓ / ✓ | `[M8]` `rootUserAdmin_adminOnAnyUser_allowed`（参数化，两个 case） | 级联到**全部**用户，不只是配置过的 | rootUserAdmin 能管理 EM 里的每一个用户，不局限于 targetUser/anotherUser 这两个例子 |

> **顺带证明"根节点权限覆盖子节点自身权限"**（跟内容资源的继承方向相反）：`TARGET_USER` 已经在上面 `identityAdmin-user(实例)` 表格里被 `identityAdminUser` 单独 grant 过；`rootUserAdmin` 并不在那条独立授权名单里，但对第一行 `rootUserAdmin` × `targetUser` 的断言依然是 ✓（已用 `rootUserAdmin_adminOnAnyUser_allowed` 的 `targetUser_allowed` case 实测验证）——因为 `DefaultCheckPermissionStrategy` 对 SECURITY_USER 的根节点检查在方法最前面就 `return true` 了，根本不会走到 `targetUser` 自己的 `Permission` 对象。

**S2-GRANTEE-VARIETY**（现有用例里被授权身份全部是 `Identity.USER` 类型，从没测过 role/group 作为被授权身份，以及三者同时设置的 OR 组合。已在 `PermissionMatrixResourcesS2Test` 落地并通过，见下表 `[M8]`）：

> 下表"登录用户"列括号里描述的是 "Administrator Permissions" 的具体设置，不代表登录用户自己被授权——除非登录用户与被授权身份是同一个对象。例如第一行的设置是："`targetUser2` 的 "Administrator Permissions" 授予了身份 `role0`（一个 Role）；登录测试用的 `viaRoleUser` 这个 user 持有 `role0`"，所以 `viaRoleUser` 是通过持有该角色间接拿到权限，而不是被直接授权。

| 登录用户 | 检查资源类型 | 资源(示例) | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| viaRoleUser（设置：`targetUser2` 的 "Administrator Permissions" 授予 `role0`；viaRoleUser 持有 `role0`） | SECURITY_USER | `targetUser2` | ADMIN | ✓ | `[M8]` `viaRoleUser_adminOnTargetUser2_allowed_grantedToRoleNotUser` | 被授权身份类型是 ROLE，不是 USER；viaRoleUser 靠持有该角色间接拿到权限 | viaRoleUser 能管理 targetUser2——即使从没被单独授权过，只因为持有 role0 |
| noRoleUser（同一设置，但 noRoleUser 不持有 `role0`） | SECURITY_USER | `targetUser2` | ADMIN | ✗ | `[M8]` `noRoleUser_adminOnTargetUser2_denied_doesNotHoldGrantedRole` | 负路径：不持有该角色就不受益 | noRoleUser 看不到 targetUser2——同样没被单独授权，也不持有 role0 |
| viaGroupUser（设置：`targetUser2` 的 "Administrator Permissions" 授予 `group0`；viaGroupUser 是 `group0` 的直接成员） | SECURITY_USER | `targetUser2` | ADMIN | ✓ | `[M8]` `viaGroupUser_adminOnTargetUser2_allowed_grantedToGroupDirectMember` | 被授权身份类型是 GROUP；直接成员 | viaGroupUser 能管理 targetUser2——因为属于被授权的 group0 |
| viaSubGroupUser（同一设置；viaSubGroupUser 属于 `group0` 的子组 `group1`，自己不直接在 `group0` 里） | SECURITY_USER | `targetUser2` | ADMIN | ✓ | `[M8]` `viaSubGroupUser_adminOnTargetUser2_allowed_inheritsThroughParentGroup` | 被授权身份类型是 GROUP；子组成员间接继承（`checkUserGroupPermission` 递归向上找父组） | viaSubGroupUser 也能管理 targetUser2——即使只是 group0 的子组 group1 成员，间接继承同样生效 |
| viaAnyOneOfThreeUser（设置：`targetUser3` 的 "Administrator Permissions" 同时授予一个 user、一个 role、一个 group 三个不同身份；此登录用户只匹配其中 role 那一条，另两条对它不生效） | SECURITY_USER | `targetUser3` | ADMIN | ✓ | `[M8]` `viaAnyOneOfThreeUser_adminOnTargetUser3_allowed_orSemanticsMatchesOneOfThree` | OR 组合：三个被授权身份只需满足其一即放行（默认 OR 模式） | viaAnyOneOfThreeUser 能管理 targetUser3——三个被授权身份（user/role/group）中只要匹配一个就够 |

### S2.3 — Groups（`SECURITY_GROUP`）及子节点

覆盖机制：实例级直授、根节点级联（`"Groups"` 根）、链式继承。**不含独立的"通配符"机制**——见下方"identityAdmin-group(通配)"的调查结论：EM UI 上给 `"Groups"` 节点设置 Administrator Permission，跟根节点级联走的是同一个 resource key，两者是同一件事，不是两条独立路径。

**identityAdmin-group(实例)**

**Action：给 identityAdminGroupInstUser 授予 `targetGroup`（`SECURITY_GROUP`）的 Administrator Permission**——只授权这一个具体组实例。

| 登录用户 | 检查资源类型 | 资源(示例) | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| identityAdminGroupInstUser | SECURITY_GROUP | `targetGroup` | ADMIN | ✓ | `[M8]` `identityAdminGroupInstUser_adminOnTargetGroup_allowed` | | identityAdminGroupInstUser 能管理 targetGroup（编辑/删除该组） |
| identityAdminGroupInstUser | SECURITY_GROUP | `anotherGroup` | ADMIN | ✗ | `[M8]` `identityAdminGroupInstUser_adminOnAnotherGroup_denied_doesNotCrossInstance` | 负路径：不越实例 | identityAdminGroupInstUser 管不了 anotherGroup——只对被明确授权的那个组生效 |
| identityAdminGroupInstUser | SECURITY_GROUP | `targetGroup` | WRITE | ✓ | `[M8]` `identityAdminGroupInstUser_writeOnTargetGroup_allowed_adminImpliesRWD` | ADMIN→隐含 | identityAdminGroupInstUser 能编辑 targetGroup 的信息 |

**identityAdmin-group(通配) —— 调查后确认：不是独立场景，等价于下方"根节点级联"**

**调查结论：** `DefaultCheckPermissionStrategy.java` 全文搜索确认没有任何 `provider.getPermission(SECURITY_GROUP, orgID)`（或等价）的 2-arg 通配符查询——L369-376 那段通配符检查硬编码只判断 `type == ResourceType.SECURITY_USER`，SECURITY_GROUP 没有对应分支。进一步追查 EM 前端/后端的写入路径（`IdentityService.setIdentityPermissions()` / `UserTreeService.editUser()`，具体是 `UserTreeService.getRootGroupModelForOrg()`）：EM UI 上给 Security > Groups 树的 `"Groups"` 根节点设置 Administrator Permission，写入的 resource key 就是 `IdentityID("Groups", orgId).convertToKey()`——跟下方"根节点级联"表格里 `rootGroupAdmin` 测的**是同一个 key，同一条代码路径**。

**结论：** User 的情况是"根节点级联"和"裸 org id 通配符"两条独立机制并存（后者其实也触达不到 EM UI，只能走底层 API，见 `identityAdminWildUser` 的 fixture 说明）；Group 从一开始就只有"根节点级联"这一条路径，没有第二条。EM UI 上"给 Groups 节点设权限"就是根节点级联，不是另一个需要单独验证的"通配符"场景，因此不再单独补 fixture/测试，避免和下方 `rootGroupAdmin` 重复。

**根节点级联（`"Groups"` 根）**

**Action：给 rootGroupAdmin 授予 `"Groups"` 根节点（`SECURITY_GROUP:"Groups"`）的 Administrator Permission**——下表"资源"列的 `targetGroup`/`anotherGroup` 是级联验证目标，不是授权点本身。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootGroupAdmin | SECURITY_GROUP | `targetGroup` / `anotherGroup` | ADMIN | ✓ / ✓ | `[M8]` `rootGroupAdmin_adminOnAnyGroup_allowed`（参数化，两个 case） | 级联到全部组 | rootGroupAdmin 能管理 EM 里的每一个组 |

**S2-GROUP-CHAIN**（独立 `@Test`，验证 Group ≥3 层 BFS 委派继承，User 无此维度。已在 `PermissionMatrixResourcesS2Test` 落地并通过）：

> 该机制走的是 `DefaultCheckPermissionStrategy` L339-366 的祖先组回溯 BFS（`getSecurityResourceParents()`），与 S2-GRANTEE-VARIETY 的 `viaSubGroupUser`（登录身份的组成员关系回溯）是两条不同代码路径。fixture 里对 `adminChainGroup0`/`adminChainGroup1` 的授权需要用 `new IdentityID(组名, orgId).convertToKey()` 格式的 resource key，不是本文档其余 S2.3 实例级场景惯用的裸字符串。

**Action 1：给 chainAdmin 授予 `adminChainGroup0`（`SECURITY_GROUP`）的 Administrator Permission**——`adminChainGroup0` 是 `adminChainGroup1` 的父组，`adminChainGroup1` 是 `adminChainGroup2` 的父组（3 层链路）。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| chainAdmin | SECURITY_GROUP | `adminChainGroup1`（子组，1 跳） | ADMIN | ✓ | `[M8]` `chainAdmin_adminOnChildGroup_allowed_oneHopDown` | 1 跳 | chainAdmin 能管理它的子组 adminChainGroup1 |
| chainAdmin | SECURITY_GROUP | `adminChainGroup2`（孙组，2 跳） | ADMIN | ✓ | `[M8]` `chainAdmin_adminOnGrandchildGroup_allowed_twoHopsDown` | 2 跳（孙节点）——只测 1 跳无法证明真 BFS | chainAdmin 也能管理孙组 adminChainGroup2——跨两层同样生效 |
| chainAdmin | SECURITY_GROUP | `adminChainSiblingGroup`（链外兄弟组） | ADMIN | ✗ | `[M8]` `chainAdmin_adminOnUnrelatedSiblingGroup_denied_notOnChain` | 负路径：链外兄弟组不可达 | chainAdmin 管不了不在这条链路上的 adminChainSiblingGroup |

**Action 2：给 midChainAdmin 授予 `adminChainGroup1`（`SECURITY_GROUP`，链路中间节点）的 Administrator Permission**——不动 `adminChainGroup0`/`adminChainGroup2` 的授权，验证委派既能向下传播、又不会向上穿透。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| midChainAdmin | SECURITY_GROUP | `adminChainGroup2`（`adminChainGroup1` 的子组） | ADMIN | ✓ | `[M8]` `midChainAdmin_adminOnChildGroup_allowed_stillCascadesDownward` | 向下依然传播——复用现有 3 层结构，不需要新建 fixture | midChainAdmin 能管理它的子组 adminChainGroup2 |
| midChainAdmin | SECURITY_GROUP | `adminChainGroup0`（`adminChainGroup1` 的父组） | ADMIN | ✗ | `[M8]` `midChainAdmin_adminOnParentGroup_denied_doesNotClimbUpward` | **不向上穿透**——SECURITY_GROUP 版本的 Rule 2/3 | midChainAdmin 管不了它的父组 adminChainGroup0——权限不会向上传递给祖先 |
| midChainAdmin | SECURITY_GROUP | `Groups`（根节点） | ADMIN | ✗ | `[M8]` `midChainAdmin_adminOnGroupsRoot_denied_doesNotClimbToRoot` | 同上，不向上穿透到根节点 | midChainAdmin 更管不了 Groups 根节点——同样不向上传递 |

### S2.4 — Organization Roles / Roles（全局角色根）及子节点（`SECURITY_ROLE`）

Role 比 User/Group 多一层复杂度：典型授权是 ASSIGN 而不是 ADMIN，且有**两个**根节点——本 org 的 `"Organization Roles"` 和全局的 `"Roles"`，两者理论上应该互相独立（实测未隔离，见本节末尾 S2-GLOBAL-ROLE-ROOT）。

**identityAdmin-role(实例)**

**Action：给 identityAdminRoleUser 授予 `targetRole`（`SECURITY_ROLE`）的 Administrator Permission，写入的是 ASSIGN，不是 ADMIN**——Role 的典型授权方式跟 User/Group 不同，只授权这一个具体角色实例。

| 登录用户 | 检查资源类型 | 资源(示例) | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| identityAdminRoleUser | SECURITY_ROLE | `targetRole` | ASSIGN | ✓ | `[M8]` `identityAdminRoleUser_assignOnTargetRole_allowed` | | identityAdminRoleUser 能把 targetRole 分配给其他用户 |
| identityAdminRoleUser | SECURITY_ROLE | `targetRole` | WRITE | ✗ | `[M8]` `identityAdminRoleUser_writeOnTargetRole_denied_assignDoesNotImplyWrite` | **关键负路径**：ASSIGN 不隐含 WRITE（Issue #75567） | identityAdminRoleUser 不能编辑 targetRole 本身的定义（改名、改继承关系等） |
| identityAdminRoleUser | SECURITY_ROLE | `targetRole` | DELETE | ✗ | `[M8]` `identityAdminRoleUser_deleteOnTargetRole_denied_assignDoesNotImplyDelete` | ASSIGN 不隐含 DELETE（Issue #75567） | identityAdminRoleUser 不能删除 targetRole |
| identityAdminRoleUser | SECURITY_ROLE | `anotherRole` | ASSIGN | ✗ | `[M8]` `identityAdminRoleUser_assignOnAnotherRole_denied_doesNotCrossInstance` | 负路径：不越实例 | identityAdminRoleUser 不能分配 anotherRole——只对被明确授权的那个角色生效 |
| identityAdminRoleUser | ASSET | `mx/folder/item` | READ | ✗ | `[M8]` `identityAdminRoleUser_noContentPermission_assetReadDenied` | | identityAdminRoleUser 看不到任何内容资源 |

> **Issue #75567**（ASSIGN 被意外提升为完整 R/W/D/A）已于 commit `b9049488a` 修复。

**根节点级联（`"Organization Roles"` 本 org 根）**

**Action：给 rootRoleAdmin 授予 `"Organization Roles"` 本 org 根节点（`SECURITY_ROLE:"Organization Roles"`）的 Administrator Permission，写入的是 ADMIN，不是 ASSIGN**——`targetRole` 本身没有被单独授权。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootRoleAdmin | SECURITY_ROLE | `targetRole` | ASSIGN | ✓ | `[M8]` `rootRoleAdmin_assignOnTargetRole_allowed` | 根节点授予的是 ADMIN | rootRoleAdmin 能把 targetRole 分配给用户 |
| rootRoleAdmin | SECURITY_ROLE | `targetRole` | WRITE | ✓ | `[M8]` `rootRoleAdmin_writeOnTargetRole_allowed_rootAdminImpliesRWD` | 根节点 ADMIN **能**拿到 W/D，与单个 role 的 ASSIGN-only 相反 | rootRoleAdmin 还能直接编辑/删除 targetRole 本身，不像普通的单角色授权那样只能分配 |

> **本表不单独设"非根节点对照组"行**：上面 `identityAdmin-role(实例)` 表格里 `anotherRole` × ASSIGN × ✗ 一行已经是同一个 `MatrixTestCase`——principal、resource、action、预期结果完全一致，只是叙事角度不同（那边是证明"不越实例"，这里想证明"单个 role 的 ASSIGN 授权不会被误判成根节点级联"）。两个角度共用同一次断言即可，不需要在 Java 里重复实现。

**根节点级联（`"Roles"` 全局根）与两根越界发现（S2-GLOBAL-ROLE-ROOT）**：

> **发现：两个根节点对 ADMIN action 不是真正独立的——确认是设计违背，跟 Issue #75574 是两个不同的 bug。** 实现这个场景时用临时 debug 输出（加进 `DefaultCheckPermissionStrategy.java`，排查完就还原了，没留在生产代码里）追出来：`DefaultCheckPermissionStrategy` L134-154 那段"专门"区分两个根节点的代码，对全局角色其实**从来不会命中**——它的守卫条件 `Tool.equals(role.getOrganizationID(), currentOrgId)` 对全局角色（`role.getOrganizationID()` 为 null）算的是 `Tool.equals(null, "matrix_org_id")`，直接是 `null == "matrix_org_id"` → false，不管全局根有没有配置授权都不会通过。真正放行的是另一个完全独立的方法——私有的 `getPermission(ResourceType, String, ResourceAction, String)`（L625 起），它在处理 ADMIN action 时走"累计权限"逻辑，对 `SECURITY_ROLE` 类型（L744-763）**同时**查 `"Organization Roles"` 和 `"Roles"` 两个根节点的授权，把两边的 user/role/group/organization 授权列表合并进同一个集合里，不管被检查的角色实际属于哪个根：
>
> ```java
> else if(currentType == ResourceType.SECURITY_ROLE) {
>    perm = provider.getPermission(currentType, new IdentityID("Organization Roles", ...));
>    if(perm != null) { users.addAll(...); ... }         // 合并进同一批集合
>    perm = provider.getPermission(currentType, new IdentityID("Roles", ...));
>    if(perm != null) { users.addAll(...); ... }         // 合并进同一批集合
> }
> ```
>
> 结果是:**在任意一个根节点上有 ADMIN,就会对全部角色(不分全局/本org)都有 ADMIN**——两个根节点对 ADMIN action 根本没有真正隔离。这跟本节想验证的"两个根节点互相独立"直接矛盾。下表两行按设计意图（预期列）断言,已 `@Disabled`,没有为了跟当前行为一致而改成断言"允许"。
>
> **两个根节点的现实可达性不对称,已核实,别高估 `rootGlobalRoleAdmin` 这半边的严重程度：**
> - `rootRoleAdmin` 的授权(`Organization Roles` 根 ADMIN)——**多租户下能从 EM 页面正常配出来**,任何 org 管理员今天就能在自己 org 的 `Organization Roles` 节点上把 Administrator Permission 委派给某个用户，这个用户就会连带拿到管理全部全局角色的权限。这是这个发现里真正有现实意义的那一半。
> - `rootGlobalRoleAdmin` 的授权(`Roles` 全局根 ADMIN)——**多租户下从 EM 页面设置不了**：前端 `UsersSettingsViewComponent.showPageEdit()`（`users-settings-view.component.ts` L328-338）专门判断了"选中节点是角色类型的根节点,且名字不是 `Organization Roles`"这种情况,直接不渲染编辑面板,Administrator Permissions 表格根本不会出现。看起来是一个已经存在的、针对全局角色根节点的专门防护，页面层面确实挡住了。绕开页面直接调接口写这条权限（跟 #75574 同样的"UI 挡住但接口未必真的校验"模式）**没有测过**，留作未确认的后续跟进，不计入这次的结论。单租户部署下 `showPageEdit()` 无条件放行，这个场景在单租户下是可达的。
>
> 下面两个用 `rootGlobalRoleAdmin` 的 `@Test` 之所以保留：这一层测试验证的是 `SecurityEngine.checkPermission()` 引擎本身的逻辑，给定一个已经存在的 Permission 对象，不管它是通过什么途径写进去的（架构设计文档里"权限逻辑测试"这一层的定义就是"与 HTTP 无关"）。这两条测试的通过/跳过状态，不代表"多租户 EM UI 上能复现"这个结论——那个结论以上面这条可达性说明为准。

**Action A：给 rootGlobalRoleAdmin 授予 `"Roles"` 全局根节点（`SECURITY_ROLE:"Roles"`）的 Administrator Permission**——**多租户 EM UI 上设不了，见上方可达性说明**。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootGlobalRoleAdmin | SECURITY_ROLE | `addGlobalRole("globalRole0")` 创建的角色 | ADMIN | ✓ | `[M8]` `rootGlobalRoleAdmin_adminOnGlobalRole_allowed` | 全局根级联到全局角色（实际是走上面发现的那条"累计权限"合并路径,不是 L134-154 那段专门代码——后者对全局角色从不命中，但结果凑巧一致） | rootGlobalRoleAdmin 能管理全局角色 globalRole0 |
| rootGlobalRoleAdmin | SECURITY_ROLE | `targetRole`（本 org 自建角色） | ADMIN | ✗ | `[M8⚠️]` `rootGlobalRoleAdmin_adminOnOrgRole_denied_rootsShouldBeIndependent`（`@Disabled`，实测允许，见上方发现说明） | 全局根不该覆盖本 org 角色——两个根节点应该相互独立 | rootGlobalRoleAdmin 管不了本 org 自建的 targetRole——**代码逻辑上已确认违反设计意图**，但这个授权本身在多租户 EM UI 上配不出来，现实攻击面待接口层面确认 |

**Action B：给 rootRoleAdmin 授予 `"Organization Roles"` 本 org 根节点的 Administrator Permission**（同"根节点级联"一节的授权）——**多租户 EM UI 上能正常配置**。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootRoleAdmin | SECURITY_ROLE | `addGlobalRole("globalRole0")` 创建的角色 | ADMIN | ✗ | `[M8⚠️]` `rootRoleAdmin_adminOnGlobalRole_denied_rootsShouldBeIndependent`（`@Disabled`，实测允许，见上方发现说明） | 反过来，本 org 根也不该覆盖全局角色 | rootRoleAdmin（本 org 角色根管理员）管不了全局角色 globalRole0——**已确认违反设计意图，且能通过日常 EM 操作复现，是这个发现里现实意义更大的一半** |

### S2.5 — 其他情况

当前为空。

## S3 — ADMIN 隐含语义 + 父子双向规则（Rule 1-3）

> **机制范围说明**：Rule 1-3（ADMIN 父子双向传播）来自 `DefaultCheckPermissionStrategy` 对 `ResourceType.isHierarchical()` 的通用向上/向下级联逻辑，对架构设计文档定义的全部"有真正父子继承的组"（Repository/`REPORT`、Worksheet/`ASSET`、数据源/`DATA_SOURCE_FOLDER` 等）一视同仁生效，不是 `ASSET` 专属行为；`Portal Dashboard`/`DASHBOARD` 是扁平命名空间，不适用 Rule 2/3。下表以 `ASSET` 作为主验证资源类型；ADMIN→READ/WRITE/DELETE 隐含兜底本身是与资源类型无关的通用机制（`SecurityEngine.java` L826-832，S2 已用 SECURITY_* 验证过），此处不重复跨组验证，只对 Rule 1-3 做跨组抽样，见下方 **S3-CROSS-GROUP**。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| contentResourceAdmin | ASSET | `mx/folder/item`（本节点，ADMIN 授权点） | ADMIN | ✓ | `[待补]` | 显式授权 |
| contentResourceAdmin | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | WRITE | ✓ | `[待补]` | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | DELETE | ✓ | `[待补]` | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item/deep`（子节点） | READ | ✓ | `[待补]` | **Rule 1（向下）**：父 ADMIN 传播给子节点 |
| no-grant | ASSET | `mx/folder`（父节点，仅子节点 `mx/folder/item/sub` 被授予 ADMIN） | ADMIN | ✗ | `[待补]` | **Rule 2（向上不穿透）**：子 ADMIN 不给父 ADMIN |
| no-grant | ASSET | `mx/folder`（同上，祖父节点） | READ | ✗ | `[待补]` | **Rule 3（跨级不穿透）**：孙节点 ADMIN 不给祖父访问权 |

**S3-CROSS-GROUP `[待补]`**（用 `REPORT`（Repository 组）做代表性抽样，证明 Rule 1-3 不是 `ASSET` 专属；`DATA_SOURCE_FOLDER` 抽样视优先级另行补充，不要求三组全排列）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| contentResourceAdmin | REPORT | `mx_vs/folder/viewsheet1`（本节点，ADMIN 授权点） | ADMIN | ✓ | `[待补]` | 显式授权，作为下方 Rule 1 断言的前提 |
| contentResourceAdmin | REPORT | `mx_vs/folder/viewsheet1/deep`（子节点） | READ | ✓ | `[待补]` | Rule 1（向下）在 Repository 组同样成立 |
| no-grant | REPORT | `mx_vs/folder`（父节点，仅子节点 `mx_vs/folder/viewsheet1/sub` 被授予 ADMIN） | ADMIN | ✗ | `[待补]` | Rule 2（向上不穿透）在 Repository 组同样成立 |
| no-grant | REPORT | `mx_vs/folder`（同上，祖父节点） | READ | ✗ | `[待补]` | Rule 3（跨级不穿透）在 Repository 组同样成立 |

## S4 — 内容访问三条链路 + Role/Group 层级 + AND/OR 变体

> **机制范围说明**：User→Role/Group→资源三条链路、Role/Group 父子层级继承、AND/OR 变体，走的都是与资源类型无关的通用授权解析路径，架构设计文档 758 行将本切片的列范围定义为 "Repository / Worksheet" 两组。下表以 `ASSET` 为主验证资源类型；跨组抽样见下方 **S4-CROSS-GROUP**，只代表性验证一条链路，不要求三条链路 × AND/OR 在 `REPORT` 上重复展开。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-via-role | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | User → Role → 资源 |
| orgViewer-via-role | ASSET | `mx/folder/item` | WRITE | ✗ | `[待补]` | 只 grant 了 READ |
| orgViewer-via-role | ASSET | `mx/folder/item` | ADMIN | ✗ | `[待补]` | 同上 |
| orgViewer-via-group | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | User → Group → 资源（group 直接被授权） |
| orgViewer-via-group | ASSET | `mx/folder/item` | WRITE | ✗ | `[待补]` | |
| orgViewer-via-group-role | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | User → Group → Role → 资源 |
| orgViewer-via-group-role | ASSET | `mx/folder/item` | WRITE | ✗ | `[待补]` | |

**S4-ROLE-HIERARCHY**（role1 继承父角色 role2，父角色持有实际 grant）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| roleHierarchyUser（role1→role2，role2 有 READ grant） | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | 父角色授权正确传播 |
| roleHierarchyNoGrantUser（role3→role4，role4 **无** grant） | ASSET | `mx/folder/item` | READ | ✗ | `[待补]` | 负路径：证明不是默认放行 |

**S4-GROUP-HIERARCHY**（group1 继承父组 group2，父组持有实际 grant）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| groupHierarchyUser（group1→group2，group2 有 READ grant） | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | 父组授权正确传播 |

**S4-AND**（独立 `@Test`，`permission.andCondition=true`）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-via-role（同一资源同时有 role grant 和 group grant） | ASSET | `mx/folder/item` | READ | ✗ | `[待补]` | AND 模式：只满足 role，不满足 group → denied |
| orgViewer-via-group-role（同时满足 group 和 role 两条 grant） | ASSET | `mx/folder/item` | READ | ✓ | `[待补]` | AND 模式：两条都满足 → allowed |

**S4-CROSS-GROUP `[待补]`**（用 `REPORT`（Repository 组）代表性抽样 via-role 一条链路，证明三条链路机制不是 `ASSET` 专属；不重复展开 via-group/via-group-role/AND 变体）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-via-role | REPORT | `mx_vs/folder/viewsheet1` | READ | ✓ | `[待补]` | User → Role → 资源，在 Repository 组同样成立 |

## S5 — 层级继承（含 Rule 4：继承路径 W→D 提升）

> **机制范围说明**：父文件夹授权向子资源传播（含 Rule 4 W→D 提升）来自 `DefaultCheckPermissionStrategy` 的通用继承分支（L392-450），架构设计文档 759 行将本切片列范围定义为 "Repository / Worksheet + 数据源"。下表以 `ASSET` 为主验证资源类型；跨组抽样见下方 **S5-CROSS-GROUP**（基础继承传播）及 **S5-RULE4-WRITE-PROMOTES-DELETE**（Rule 4 提升逻辑，已规划 `DATA_SOURCE`/`QUERY`/`CUBE` 抽样）。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx/folder` 有 READ grant） | ASSET | `mx/folder/item`（子资源，自己无显式权限） | READ | ✓ | `[待补]` | 父级授权正确传播给子资源 |
| no-grant | ASSET | `mx/folder/item` | READ | ✗ | `[待补]` | 无父级授权、无继承 |
| no-grant | ASSET | `mx/folder`（父节点本身） | READ | ✗ | `[待补]` | 父节点自己也无授权 |

**S5-CROSS-GROUP `[待补]`**（用 `REPORT`（Repository 组）代表性抽样基础继承传播，证明机制不是 `ASSET` 专属；Rule 4 的跨组抽样见下方 S5-RULE4，已覆盖数据源，不在此重复）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx_vs/folder` 有 READ grant） | REPORT | `mx_vs/folder/viewsheet1`（子资源，自己无显式权限） | READ | ✓ | `[待补]` | 父级授权正确传播给子资源，在 Repository 组同样成立 |

**S5-RULE4-WRITE-PROMOTES-DELETE `[待补]`**（设计文档 Rule 4，`DefaultCheckPermissionStrategy.java` L392-450；Task 5 尚未实现，以下为待落地场景，用 sr13_8 `DPermission_Case.checkRepositorySetPermission` / `checkDataSourceSetPermission` 交叉验证过）。下表已经用 `ASSET`（Worksheet）+ `DATA_SOURCE`/`QUERY`/`CUBE`（数据源）两组做了跨组抽样，满足"证明 Rule 4 是通用继承机制、不是单一资源组专属"的要求，不需要再补 `REPORT` 的 Rule 4 场景：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| partialGrantUser（父节点 `mx/folder2` 只 grant WRITE，不给 DELETE/ADMIN） | ASSET | `mx/folder2/item`（子资源，自己无显式权限） | WRITE | ✓ | `[待补]` | 直接继承 |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | DELETE | ✓ | `[待补]` | **提升**：父级 WRITE → 子级连带获得 DELETE |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | ADMIN | ✗ | `[待补]` | 提升逻辑只处理 WRITE/DELETE，不会提升出 ADMIN |
| partialGrantUser（同上，子资源自己也设置了显式权限——哪怕是空的） | ASSET | `mx/folder2/item-explicit` | DELETE | ✗ | `[待补]` | 负路径：子级有自己的显式权限时不走继承分支，提升不触发 |
| partialGrantDsUser（父节点 `mx_datasource_folder` 只 grant WRITE） | DATA_SOURCE | `mx_datasource_folder/ds1`（子资源） | DELETE | ✓ | `[待补]` | 同一规则在 DataSource 分支的体现 |
| partialGrantDsUser（同上） | QUERY | `Model::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | `[待补]` | 验证规则是通用继承机制，不是 Repository/Worksheet 专属 |
| partialGrantDsUser（同上） | CUBE | `cube1::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | `[待补]` | 同上，覆盖 CUBE 子类型 |

## 附加 — 未分配切片的资源组基线抽样（Portal Dashboard / 调度 / 库资源）`[待补]`

> 这三组在架构设计文档的 M7 切片计划（752-759 行）里**从未被分配到任何切片**：S2 只覆盖 `SECURITY_*`，S3/S4/S5 只覆盖 Repository/Worksheet/数据源。在正式补切片之前，先用 `contentResourceAdmin` 的显式 ADMIN 授权 + 隐含 R/W/D 各给一行最基础的抽样，把"完全零验证"的状态往前推一步；调度组的 `ASSIGN` action、库资源组 `TABLE_STYLE` 的 `~` 分隔多级继承与 `SCRIPT` 单层结构等专属细节暂不展开，留作后续独立场景（可参照 S2 里 identityAdmin-role 对 ASSIGN 的处理方式、S2-GROUP-CHAIN 对多层继承的处理方式）。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| contentResourceAdmin | DASHBOARD | `dashboard1`（本节点，ADMIN 授权点；扁平命名空间，无子节点） | ADMIN | ✓ | `[待补]` | 显式授权 |
| contentResourceAdmin | DASHBOARD | `dashboard1` | READ | ✓ | `[待补]` | ADMIN→隐含；DASHBOARD 无 Rule 1-3（扁平结构，无父子级联需要验证） |
| contentResourceAdmin | SCHEDULE_TASK | `mx_schedule_folder/task1`（文件夹下的任务节点） | ADMIN | ✓ | `[待补]` | 显式授权 |
| contentResourceAdmin | SCHEDULE_TASK | `mx_schedule_folder/task1` | READ | ✓ | `[待补]` | ADMIN→隐含 |
| contentResourceAdmin | SCRIPT | `mx_script_lib/script1`（SCRIPT 固定单层，`SCRIPT.getParent()` 恒回到 `SCRIPT_LIBRARY:*`） | ADMIN | ✓ | `[待补]` | 显式授权 |
| contentResourceAdmin | SCRIPT | `mx_script_lib/script1` | READ | ✓ | `[待补]` | ADMIN→隐含 |

执行顺序：S2 → S3 → S4 → S5，低优先级行（no-grant / anonymous）按需补充；上方"附加"表待正式排入切片计划后再决定归属（新增独立切片，或并入 S3）。区二（S6-S8）见 `permission-matrix-actions.md`。

---

## 附录：能力总览矩阵（用户类型 × 资源组）

> 下表是原始的"用户类型 × 资源组"速查表，用于快速查某个用户类型在某类资源上大致有什么能力；具体到某个 Action/某条边界规则的精确断言以上面按切片列出的场景表为准，两处如有出入以切片表 + 代码为准。区二（Security Action）的对应总览表见 `permission-matrix-actions.md`。

资源组定义见架构设计文档 § 区一，共 7 组：Repository(`REPORT`)/Worksheet(`ASSET`)/Portal Dashboard(`DASHBOARD`)/数据源(`DATA_SOURCE*`/`QUERY`/`CUBE`)/调度(`SCHEDULE_TASK*`)/安全管理(`SECURITY_*`)/库资源(`SCRIPT*`/`TABLE_STYLE*`)。行为规则：
- `siteAdmin`（sysAdmin role）：跨所有 org、所有资源、所有 action 全通
- `orgAdmin`（Organization Administrator role）：本 org 全通；跨 org 全拒
- ADMIN 隐含语义（`SecurityEngine` L826-832）：拥有 ADMIN 则隐含 READ/WRITE/DELETE
- 7 组里只有调度组额外适用 `ASSIGN`（`SCHEDULE_TASK`/`SCHEDULE_CYCLE`），下表未单独展开，参见 `permission-matrix-actions.md`

Action 列：R=READ  W=WRITE  D=DELETE  A=ADMIN（无 SHARE：`SHARE` 是区二 Social Sharing 的独立 `ResourceType`，不属于区一任何资源组的适用 action，早期版本表格曾把它错标进内容资源列，已在本次修订移除）

> **列名订正**：原表第 2 列曾写作"单体内容(VIEWSHEET/WS)"，但 `ResourceType.VIEWSHEET`/`ResourceType.WORKSHEET` 并非内容权限类型——它们固定 `path="*"` + `ACCESS`，语义是"能否打开 Composer"的全局开关（见设计文档 578-579 行），不是本表意图覆盖的"单个内容资源实例的 R/W/D/A"。结合该列原有的"扁平命名空间、无继承"描述，实际对应的是 7 组之一的 **Portal Dashboard(`DASHBOARD`)**（`DASHBOARD.getParent()` 恒返回 `null`），已在本次修订更正列名和数值，原列名/数值属于早期版本遗留的错误标注。

| 用户类型 | 层级内容资产(ASSET/REPORT) | Portal Dashboard(DASHBOARD) | 数据层(DATA_SOURCE) | 调度(SCHEDULE_TASK) | 安全管理(SECURITY_USER/ORG) | 库资源(SCRIPT) |
|---|---|---|---|---|---|---|
| `siteAdmin` | ✓ R/W/D/A | ✓ R/W/D/A | ✓ R/W/D/A | ✓ R/W/D/A | ✓ R/W/D/A | ✓ R/W/D/A |
| `orgAdmin` | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] |
| `orgSecurityAdmin` | ✗ R/W/D/A (无内容权) | ✗ | ✗ | ✗ | ✓ 任意 action，级联到全部 SECURITY_USER/GROUP + 本org SECURITY_ROLE 实例（含从未单独 grant 过的），不只是 SECURITY_ORGANIZATION 自身、也不只 ADMIN [S2] | ✗ |
| `identityAdmin-user(实例)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D({name}) / ✗ 其他实例 [S2] | ✗ |
| `identityAdmin-user(通配)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D(*) 全部实例 [S2] | ✗ |
| `identityAdmin-group(实例)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D({name}) / ✗ 其他实例 [S2] | ✗ |
| `identityAdmin-role` ⚠️非 ADMIN | ✗ | ✗ | ✗ | ✗ | ✓ ASSIGN({name}) 仅此action / ✗ W/D [S2] | ✗ |
| `contentResourceAdmin` | ✓ A + (ADMIN→)R/W/D [S3] | ✓ A + (ADMIN→)R/W/D（扁平命名空间，不适用 Rule 2/3）[附加][待补] | ✓ A + (ADMIN→)R/W/D（基础 ADMIN 隐含尚未验证；仅 Rule 4 提升逻辑已在 S5-RULE4 验证）[待补] | ✓ A + (ADMIN→)R/W/D（现有切片未覆盖，见文末"附加"抽样）[附加][待补] | — | ✓ A + (ADMIN→)R/W/D（现有切片未覆盖，见文末"附加"抽样）[附加][待补] |
| `orgViewer-via-role` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A（现有 S4 用例尚未覆盖 DASHBOARD）[待补] | — | — | ✗ | — |
| `orgViewer-via-group` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A（同上）[待补] | — | — | ✗ | — |
| `orgViewer-via-group-role` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A（同上）[待补] | — | — | ✗ | — |
| `orgViewer-inherited` | ✓ R(子继父) / ✗ W/D/A [S5]；部分授权(仅 W)时子级会被提升出 D，见 S5-RULE4 [S5][待补] | — (扁平命名空间，无继承路径，不适用 S5) | ✓ R(子继父)，部分授权(仅 W)提升 D 同样适用 [S5][待补] | — | ✗ | — |
| `orgUser-no-grant` ⚑低 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |

**ADMIN 父子双向规则**（S3 须覆盖）：
- Rule 1（向下）：父 ADMIN → 子继承 ADMIN ✓
- Rule 2（向上不穿透）：子 ADMIN，父无权限 → 父仅 READ（listing），父不获 ADMIN ✗
- Rule 3（跨级不穿透）：孙 ADMIN，祖父无权限 → denied ✗
- **Rule 4（继承路径 W→D 提升，独立于 ADMIN 机制，见 S5-RULE4）**：子资源无自己的显式权限、靠继承借用祖先权限时，祖先节点上拥有 WRITE 或 DELETE 的身份会被自动提升为同时拥有 DELETE；不提升 ADMIN；子资源自己有显式权限（哪怕为空）时不触发

**identityAdmin-group 多层授权链**（S2 须覆盖，User 没有这个维度）：
- User 是固定两层结构（"Users" 根 → 具体 user），不需要测层级深度
- Group 是真正的多层结构（"Groups" 根 → group0 → group1 → ...），委派继承靠 BFS 反复向上走（`DefaultCheckPermissionStrategy.java` L339-366）；只测 1 跳不足以证明 BFS 真的支持多跳，必须构造 ≥3 层链路（group0→group1→group2），验证祖先 ADMIN 覆盖孙节点，同时验证链路外的兄弟 group 被拒绝

**identityAdmin 根节点级联**（S2 须覆盖，"Users"/"Groups"/"Organization Roles" 三种根节点各自独立）：
- `DefaultCheckPermissionStrategy` L134-186 在检查任意 SECURITY_USER/GROUP/ROLE 资源前无条件先查根节点 ADMIN，命中即放行——对该类型下**全部**实例生效，不只是显式配置过的那些
- Role 的根节点（"Organization Roles"）授予的是 ADMIN（不是 ASSIGN），所以根节点级联下的 role 反而能拿到 WRITE/DELETE；这与单个 role 的 ASSIGN-only 授权（不能 W/D）形成对比，两者须分别断言

**identityAdmin-group 父子可见性**（UserTreeService 层，属于单元测试范畴，不在本 Task 4 的 checkPermission 矩阵内）：
- 父 group 自身无 ADMIN 但有被授权的子节点 → 显示为只读可见（`UserTreeService.getGroup()` L220-224）
- 子节点也无权限 → 父节点整体不可见（`Optional.empty()`）
- 这条规则测的是树构建/可见性计算，不是 `SecurityEngine.checkPermission()` 的返回值，需要单独针对 `UserTreeService` 写单元测试（未纳入本计划，留作后续 M9 或新功能测试规范的待办项）

**AND/OR 变体**（S4 须覆盖，通过 `permission.andCondition=true` 系统属性激活）：
- AND 模式：同时设置了 role grant + group grant 时，via-role 用户（无 group）→ ✗ denied
- OR 模式（默认）：via-role 用户 → ✓ allowed
