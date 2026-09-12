# Permission Test Matrix — 区一：内容访问权限（Resources）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）
**Phase 2 M8 实现：** 按切片拆成 4 个测试类——`PermissionMatrixResourcesS2Test`/`S3Test`/`S4Test`/`S5Test`（原计划是单个 `PermissionMatrixResourcesTest` + `MatrixTestCase` 参数化 DSL，已废弃，见 `docs/superpowers/plans/2026-06-30-permission-test-phase2.md` 的"修订说明"）
**姊妹文档：** 区二 Security Action 权限矩阵（S6/S8，`PermissionMatrixActionsS6Test`/`S8Test`）见 `permission-matrix-actions.md`

图例：✓ = allowed　✗ = denied　— = n/a　`(ADMIN→)` = 由 ADMIN 隐含授权　`[P1]` = Phase 1 已覆盖

**"测试状态"列取值**（每个场景表都有这一列，用来回答"这一行有没有被自动化测试覆盖"）：
- `[M8]` = 已在对应切片的 `PermissionMatrixResourcesS{N}Test.java`（如 S2 → `PermissionMatrixResourcesS2Test`）落地并通过，后面附对应的 `@Test` 方法名
- `[M8⚠️]` = 已落地，但实测结果与本表"预期"列不符，测试已 `@Disabled` 并注明原因（不会静默地把矛盾的断言标成"通过"）
- `[附加]` = M7 切片计划未分配、本文档补记的基线抽样（见文末"附加"小节）
- `[待补]` = 已在设计文档确认、尚未落到测试代码，后面附未落地的具体原因（缺 fixture 能力/需要先验证约定/依赖其他场景先落地等）
- `[不补]` = 已评估过是否要补，结论是不补——不是遗留欠账，而是同一条代码路径已经在别处验证过，再补一次没有新增覆盖，后面附"已经在哪验证过"的具体说明

下方按切片（Slice）列出具体场景，每一行大致对应对应切片测试类（`PermissionMatrixResourcesS2Test`/`S3Test`/`S4Test`/`S5Test`）里的一个独立 `@Test` 方法，方便实现前逐条 review。资源路径为 fixture 示例路径，不是生产环境真实路径。总览矩阵（按资源组归类的能力速查表）见文末附录。

### 资源组速查表（完整版见架构设计文档 § 区一 534-542 行）

| 资源组 | 代表 ResourceType | 适用 action | 层级继承 | 本文档覆盖情况 |
|---|---|---|---|---|
| Repository（Viewsheet 树） | `REPORT` | READ/WRITE/DELETE/ADMIN | 是，`/` 分隔真实父子继承 | S3/S4/S5 的 `*-CROSS-GROUP` 已在 `PermissionMatrixResourcesS3Test`/`S4Test`/`S5Test` 落地 `[M8]` |
| Worksheet | `ASSET` | READ/WRITE/DELETE/ADMIN | 是，`/` 分隔真实父子继承 | S3/S4/S5 主表已在 `PermissionMatrixResourcesS3Test`/`S4Test`/`S5Test` 落地 `[M8]` |
| Portal Dashboard | `DASHBOARD` | READ/WRITE/DELETE/ADMIN | **否**——扁平命名空间，`DASHBOARD.getParent()` 恒返回 `null` | 无切片分配，基线抽样已并入 `PermissionMatrixResourcesS3Test`（文末"附加"小节）`[M8]` |
| 数据源 | `DATA_SOURCE_FOLDER`/`DATA_SOURCE`/`CUBE`/`QUERY`（Logical Model） | READ/WRITE/DELETE/ADMIN | 是——文件夹用 `/`，model/cube 挂载用 `::` | S5-RULE4 覆盖 Rule 4 提升逻辑，含 `DATA_SOURCE`/`QUERY`/`CUBE` 三个子类型，已在 `PermissionMatrixResourcesS5Test` 落地 `[M8]`；`security.datasource.everyone` 兜底机制（true/false 两种状态）已在同一文件落地 `[M8]`；基础 ADMIN 语义（S3/S4 那套 Rule1-3）评估后决定不补 `[不补]`——`DATA_SOURCE_FOLDER.getParent()` 走的是和 `ASSET`/`REPORT` 完全相同的通用 `/`-分隔父子解析，没有自己的类型专属覆写，Rule1-3 已经用 REPORT 在 S3-CROSS-GROUP 证明过不是 `ASSET` 专属，再在 `DATA_SOURCE` 上重复第三次没有新增覆盖 |
| 调度 | `SCHEDULE_TASK_FOLDER`/`SCHEDULE_TASK`/`SCHEDULE_CYCLE` | READ/WRITE/DELETE/ADMIN/ASSIGN | 仅文件夹层继承；task/cycle 本身扁平 | `SCHEDULE_TASK`（不含 `SCHEDULE_TASK_FOLDER`）的基线抽样已并入 `PermissionMatrixResourcesS3Test`（文末"附加"小节）`[M8]`；`SCHEDULE_TASK_FOLDER` 的 `security.scheduletask.everyone` 兜底机制（true/false 两种状态）已在 `PermissionMatrixResourcesS5Test` 落地 `[M8]`；`SCHEDULE_TASK_FOLDER` 自身的 `/`-分隔层级继承（Rule1 向下级联 + Rule2/3 不向上/跨级穿透，最小化边界验证）已在 `PermissionMatrixResourcesS3Test` 落地 `[M8]` |
| 安全管理 | `SECURITY_USER`/`SECURITY_GROUP`/`SECURITY_ROLE`/`SECURITY_ORGANIZATION` | User/Group 走 ADMIN，Role 走 ASSIGN | 各类型独立合成根节点级联，非路径分隔符继承 | S2（按 S2.1-S2.5 分资源类型组织）：S2.1/S2.2/S2.4 实例级+根节点级联场景、S2.2 的 S2-GRANTEE-VARIETY、S2.3 的 S2-GROUP-CHAIN 已在 `PermissionMatrixResourcesS2Test` 落地 `[M8]`；S2.3 的 identityAdmin-group(通配) 调查后确认不是独立机制——EM UI 上等价于根节点级联，已并入 `rootGroupAdmin` 覆盖，不再单独列为待办；S2.4 的 identityAdmin-role WRITE/DELETE 断言（Issue #75567，已于 commit `b9049488a` 修复）`[M8]`；S2.1 的 orgSecurityAdmin 全局角色负控制、S2.4 的 S2-GLOBAL-ROLE-ROOT 根节点互相越界，两处独立发现此前复测为实测分歧、曾 `@Disabled`，已于 commit `0dd9c17ef`（Issue #75574，PR #4187）一并修复，四条测试均已启用并通过 `[M8]`，另有 `DefaultCheckPermissionStrategyTest` 补充的单元级回归覆盖 |
| 库资源 | `SCRIPT_LIBRARY`/`SCRIPT`（扁平单层） / `TABLE_STYLE_LIBRARY`/`TABLE_STYLE`（真正多级） | READ/WRITE/DELETE/ADMIN | Table Style 是，`~` 分隔；Script 否，固定单层 | `SCRIPT`/`SCRIPT_LIBRARY` 的基线抽样 + 根节点级联/Rule4 提升已并入 `PermissionMatrixResourcesS3Test`（文末"附加"小节）`[M8]`；`security.script.everyone`/`security.tablestyle.everyone` 兜底机制（true/false 两种状态）已在 `PermissionMatrixResourcesS5Test` 落地 `[M8]`；`TABLE_STYLE` 自身的 `~`-分隔多级继承（Rule1 向下级联 + Rule2/3 不向上/跨级穿透，最小化边界验证）已在 `PermissionMatrixResourcesS3Test` 落地 `[M8]` |

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
| 跨类型级联 | `SECURITY_ORGANIZATION` ADMIN → 级联到本 org 内 User/Group/org 自建 Role 的任意实例，不看具体资源路径；对全局角色不生效（Issue #75574，此前实测生效，已于 commit `0dd9c17ef` 修复） | S2.1 |
| 实例级直授 | 对某个具体 User/Group/Role 资源单独设 Administrator Permission（Role 是 ASSIGN），只对该实例生效，不跨实例 | S2.2 / S2.3 / S2.4 各自"实例"小节 |
| 通配符 | 仅 `SECURITY_USER` 有此机制：`provider.getPermission(SECURITY_USER, orgID)`（2-arg，key 是 org id 字符串本身），对本 org 全部用户生效，但该 key 本身也只能通过底层 API 写入，EM UI 触达不到；`SECURITY_GROUP` **已确认没有这第二条机制**——EM UI 上给 `"Groups"` 节点设权限，走的就是根节点级联那一条路径，两者是同一回事，不是"缺了一个功能" | S2.2 / S2.3 |
| 根节点级联 | 对 `"Users"`/`"Groups"`/`"Organization Roles"`/`"Roles"` 四个根节点资源设 ADMIN，级联到该类型全部实例；Role 有两个根，互相独立（S2-GLOBAL-ROLE-ROOT，此前实测未隔离，已于 commit `0dd9c17ef` 修复） | S2.2 / S2.3 / S2.4 |
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
| orgSecurityAdmin | SECURITY_ROLE | 全局角色(org-less，非 sysAdmin)，对照内置 `Organization Administrator`（`addGlobalRole("globalRole0")`） | ADMIN | ✗ | `[M8]` `orgSecurityAdmin_adminOnGlobalRole_denied_negativeControl` | 负控制：`isNotGlobalRole` 挡住了 L57-67 那条级联；`checkOrgAdminPermission()` 的 SECURITY_ROLE 分支此前显式把"角色 org 为 null 且角色自身非 sysAdmin"也判为可管理，不受 `isNotGlobalRole` 约束——Issue #75574（commit `0dd9c17ef`/PR #4187）已改为要求 `Tool.equals(orgID, role.getOrganizationID())`，全局角色（org=null）不再匹配，该分支不再放行 | orgSecurityAdmin 管不了全局角色（跨 org 共享），只有 siteAdmin 或持有全局 `Administrator` 角色的人才能管——**已于 commit `0dd9c17ef` 修复，测试已启用并通过** |
| orgSecurityAdmin | SECURITY_ROLE | 全局角色(org-less，sysAdmin=true)，即内置 `Administrator` 本尊 | ADMIN | ✗ | `[M8]` `orgSecurityAdmin_adminOnGlobalSysAdminRole_denied_negativeControl` | 同上机制；commit `0dd9c17ef` 同时改了 `checkOrgAdminPermission()` 和 org-admin SECURITY_ORGANIZATION 级联两条路径，都要求角色 org 与当前 org 一致，全局角色一律排除，不再单靠 `!isSiteAdmin` 兜底 | orgSecurityAdmin 管不了内置 `Administrator` 角色——**已于 commit `0dd9c17ef` 修复**；生产环境此前能通过直接 API 调用（`GET .../roles/Administrator~~_3b_~~__GLOBAL__/`）绕开 UI 树隐藏看到该角色，`RoleController.getRole()` 顶层 `@RequiredPermission(ADMIN)` 网关复用的正是同一个 `checkPermission()`，修复后网关直接拒绝，生产端问题一并解决 |

> **`[M8⚠️]` 发现 → 已修复：orgSecurityAdmin 曾经能管理全局角色（Issue #75574）**
>
> 上面两行全局角色负控制，跟下面 S2.4 的 ASSIGN/ADMIN 那条不同：**不只是 JUnit 里实测矛盾，曾经在真实部署的 EM 上用浏览器直接复现过**。复现方式：用只持有 `SECURITY_ORGANIZATION` ADMIN（没有 Organization Administrator 角色、不是 site admin）的 orgSecurityAdmin 账号登录 EM，Security > Users 树里正确地看不到 `Roles`（全局根）节点——UI 这一层没问题。但在浏览器 Console 里用同一个登录 session 直接调后端接口（绕开树，不经过 UI 点击）：
>
> ```
> GET /sree/api/em/security/providers/Primary/roles/Organization%20Administrator~~_3b_~~__GLOBAL__/
> GET /sree/api/em/security/providers/Primary/roles/Administrator~~_3b_~~__GLOBAL__/
> ```
>
> 两个内置全局角色当时**都返回 HTTP 200 和完整角色数据，其中 `"editable":true`**——接口本身没有守住这道边界，只有 UI 树把入口藏起来了，是"只在前端隐藏、后端没有独立校验"的反模式。
>
> **根因定位（修复前）：**
> - **`Organization Administrator`**（org=null，sysAdmin=false，orgAdmin=true）：根因已在 JUnit 里精确复现并定位——`checkOrgAdminPermission()`（`DefaultCheckPermissionStrategy.java`）的 `SECURITY_ROLE` 分支曾显式把"角色 org 为 null 且角色自身不是 sysAdmin"也判定为可管理，是一条完全独立于 `isNotGlobalRole()` 级联（L58-67）的放行路径。
> - **`Administrator`**（org=null，sysAdmin=true）：生产环境曾确认同样暴露（实测 200 + `editable:true`），当时怀疑的差异候选是 `RoleController.getRole()` 计算 `editableRoles` 时额外单独调用的 `checkPermission(..., ResourceAction.ASSIGN)` 路径。
>
> **已于 2026-07-07 commit `0dd9c17ef`（"Bug #75574: org-scoped role admin permission leaks onto global roles"，PR #4187）一并修复**，修复范围覆盖四条独立路径（不止上面两条）：`checkOrgAdminPermission()` 的 `SECURITY_ROLE` 分支、org-admin 的 `SECURITY_ORGANIZATION` 权限级联分支、`"Organization Roles"` 节点直授回退分支、以及下方 S2.4 提到的 ADMIN 累计权限 `getPermission()` 助手（见 S2-GLOBAL-ROLE-ROOT）——四处统一改为要求被检查角色自身的 org 与授权 org 一致，全局角色（org=null）一律排除在外。`RoleController.getRole()` 顶层的 `@RequiredPermission(ADMIN)` 网关复用同一个 `checkPermission()`，因此生产端"接口未独立校验"的暴露面随之收口，不需要再单独排查 ASSIGN 分支。修复提交同时新增了 `DefaultCheckPermissionStrategyTest`（单元级、mock provider）覆盖这四条路径，与本文档的矩阵集成测试互为补充。上面两行测试已移除 `@Disabled` 并通过。

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

Role 比 User/Group 多一层复杂度：典型授权是 ASSIGN 而不是 ADMIN，且有**两个**根节点——本 org 的 `"Organization Roles"` 和全局的 `"Roles"`，两者互相独立（S2-GLOBAL-ROLE-ROOT，此前实测未隔离，已于 commit `0dd9c17ef` 修复，详见本节末尾）。

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

**根节点级联（`"Roles"` 全局根）与两根越界发现（S2-GLOBAL-ROLE-ROOT，已修复）**：

> **发现 → 已修复：两个根节点对 ADMIN action 曾经不是真正独立的——跟 Issue #75574 根因相关，同一个 commit 一并修了。** 实现这个场景时用临时 debug 输出（加进 `DefaultCheckPermissionStrategy.java`，排查完就还原了，没留在生产代码里）追出来：`DefaultCheckPermissionStrategy` L134-154 那段"专门"区分两个根节点的代码，对全局角色其实**从来不会命中**——它的守卫条件 `Tool.equals(role.getOrganizationID(), currentOrgId)` 对全局角色（`role.getOrganizationID()` 为 null）算的是 `Tool.equals(null, "matrix_org_id")`，直接是 `null == "matrix_org_id"` → false，不管全局根有没有配置授权都不会通过。真正放行的是另一个完全独立的方法——私有的 `getPermission(ResourceType, String, ResourceAction, String)`，它在处理 ADMIN action 时走"累计权限"逻辑，当时对 `SECURITY_ROLE` 类型**无条件同时**查 `"Organization Roles"` 和 `"Roles"` 两个根节点的授权，把两边的 user/role/group/organization 授权列表合并进同一个集合里，不管被检查的角色实际属于哪个根：
>
> ```java
> // 修复前
> else if(currentType == ResourceType.SECURITY_ROLE) {
>    perm = provider.getPermission(currentType, new IdentityID("Organization Roles", ...));
>    if(perm != null) { users.addAll(...); ... }         // 合并进同一批集合
>    perm = provider.getPermission(currentType, new IdentityID("Roles", ...));
>    if(perm != null) { users.addAll(...); ... }         // 合并进同一批集合
> }
> ```
>
> 结果是：**在任意一个根节点上有 ADMIN，就会对全部角色（不分全局/本org）都有 ADMIN**——两个根节点对 ADMIN action 根本没有真正隔离，跟"两个根节点互相独立"的设计意图直接矛盾。
>
> **已于 2026-07-07 commit `0dd9c17ef`（PR #4187，与上方 S2.1 Issue #75574 同一提交）修复**：`getPermission()` 的 SECURITY_ROLE 分支改为先查被检查角色自己属于哪个根（`currentRole.getOrganizationID()` 为空 → 只并入 `"Roles"`；等于当前 org → 只并入 `"Organization Roles"`；属于其它 org → 两边都不并入），只合并被检查角色实际所属的那一个根，不再无条件合并两个根。下表两行原按设计意图（预期列）断言、`@Disabled`；修复后行为已与设计意图一致，两条测试都已移除 `@Disabled` 并通过。
>
> **两个根节点的现实可达性不对称，与本次修复无关，仍然成立，继续记录供后续参考：**
> - `rootRoleAdmin` 的授权（`Organization Roles` 根 ADMIN）——**多租户下能从 EM 页面正常配出来**，任何 org 管理员今天就能在自己 org 的 `Organization Roles` 节点上把 Administrator Permission 委派给某个用户。修复前，这个用户会连带拿到管理全部全局角色的权限（这是当时现实意义最大的那一半泄漏，修复后已不再泄漏）。
> - `rootGlobalRoleAdmin` 的授权（`Roles` 全局根 ADMIN）——**多租户下从 EM 页面设置不了**：前端 `UsersSettingsViewComponent.showPageEdit()`（`users-settings-view.component.ts` L328-338）专门判断了"选中节点是角色类型的根节点，且名字不是 `Organization Roles`"这种情况，直接不渲染编辑面板，Administrator Permissions 表格根本不会出现。绕开页面直接调接口写这条权限没有测过，留作未确认的后续跟进，不影响本次结论。单租户部署下 `showPageEdit()` 无条件放行，这个场景在单租户下是可达的。
>
> 下面两个用 `rootGlobalRoleAdmin` 的 `@Test` 之所以保留：这一层测试验证的是 `SecurityEngine.checkPermission()` 引擎本身的逻辑，给定一个已经存在的 Permission 对象，不管它是通过什么途径写进去的（架构设计文档里"权限逻辑测试"这一层的定义就是"与 HTTP 无关"）。

**Action A：给 rootGlobalRoleAdmin 授予 `"Roles"` 全局根节点（`SECURITY_ROLE:"Roles"`）的 Administrator Permission**——**多租户 EM UI 上设不了，见上方可达性说明**。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootGlobalRoleAdmin | SECURITY_ROLE | `addGlobalRole("globalRole0")` 创建的角色 | ADMIN | ✓ | `[M8]` `rootGlobalRoleAdmin_adminOnGlobalRole_allowed` | 全局根级联到全局角色，走的是上面"累计权限"合并路径（被检查角色 org=null → 只并入 `"Roles"` 根），不是 L134-154 那段专门代码——后者对全局角色从不命中 | rootGlobalRoleAdmin 能管理全局角色 globalRole0 |
| rootGlobalRoleAdmin | SECURITY_ROLE | `targetRole`（本 org 自建角色） | ADMIN | ✗ | `[M8]` `rootGlobalRoleAdmin_adminOnOrgRole_denied_rootsShouldBeIndependent` | 全局根不覆盖本 org 角色——两个根节点相互独立，已于 commit `0dd9c17ef` 修复 | rootGlobalRoleAdmin 管不了本 org 自建的 targetRole——**已修复，测试已启用并通过**；该授权本身在多租户 EM UI 上配不出来，见上方可达性说明 |

**Action B：给 rootRoleAdmin 授予 `"Organization Roles"` 本 org 根节点的 Administrator Permission**（同"根节点级联"一节的授权）——**多租户 EM UI 上能正常配置**。

| 登录用户 | 检查资源类型 | 资源 | 检查Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| rootRoleAdmin | SECURITY_ROLE | `addGlobalRole("globalRole0")` 创建的角色 | ADMIN | ✗ | `[M8]` `rootRoleAdmin_adminOnGlobalRole_denied_rootsShouldBeIndependent` | 反过来，本 org 根也不覆盖全局角色，已于 commit `0dd9c17ef` 修复 | rootRoleAdmin（本 org 角色根管理员）管不了全局角色 globalRole0——**已修复，测试已启用并通过**；这条泄漏此前能通过日常 EM 操作复现，是修复前现实意义更大的那一半 |

### S2.5 — 其他情况

当前为空。

## S3 — ADMIN 隐含语义 + 父子双向规则（Rule 1-3）

> **机制范围说明**：Rule 1-3（ADMIN 父子双向传播）来自 `DefaultCheckPermissionStrategy` 对 `ResourceType.isHierarchical()` 的通用向上/向下级联逻辑，对架构设计文档定义的全部"有真正父子继承的组"（Repository/`REPORT`、Worksheet/`ASSET`、数据源/`DATA_SOURCE_FOLDER` 等）一视同仁生效，不是 `ASSET` 专属行为；`Portal Dashboard`/`DASHBOARD` 是扁平命名空间，不适用 Rule 2/3。下表以 `ASSET` 作为主验证资源类型；ADMIN→READ/WRITE/DELETE 隐含兜底本身是与资源类型无关的通用机制（`SecurityEngine.java` L826-832，S2 已用 SECURITY_* 验证过），此处不重复跨组验证，只对 Rule 1-3 做跨组抽样，见下方 **S3-CROSS-GROUP**。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| contentResourceAdmin | ASSET | `mx/folder/item`（本节点，ADMIN 授权点） | ADMIN | ✓ | `[M8]` `contentResourceAdmin_adminOnItem_allowed` | 显式授权 |
| contentResourceAdmin | ASSET | `mx/folder/item` | READ | ✓ | `[M8]` `contentResourceAdmin_readWriteDeleteOnItem_allowed_adminImpliesRWD` | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | WRITE | ✓ | `[M8]`（同一方法，见上一行） | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | DELETE | ✓ | `[M8]`（同一方法，见上一行） | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item/deep`（子节点） | READ | ✓ | `[M8]` `contentResourceAdmin_readOnDeepChild_allowed_rule1DownwardCascade` | **Rule 1（向下）**：父 ADMIN 传播给子节点 |
| deepOnlyAdmin（仅子节点 `mx/folder/item/sub` 被授予 ADMIN，对 `mx/folder` 本身无授权） | ASSET | `mx/folder`（父节点） | ADMIN | ✗ | `[M8]` `deepOnlyAdmin_adminOnParentFolder_denied_rule2NoUpwardCascade` | **Rule 2（向上不穿透）**：子 ADMIN 不给父 ADMIN |
| deepOnlyAdmin（同上） | ASSET | `mx/folder`（同上，祖父节点） | READ | ✗ | `[M8]` `deepOnlyAdmin_readOnParentFolder_denied_rule3NoCrossLevelCascade` | **Rule 3（跨级不穿透）**：孙节点 ADMIN 不给祖父访问权 |

**S3-CROSS-GROUP**（用 `REPORT`（Repository 组）做代表性抽样，证明 Rule 1-3 不是 `ASSET` 专属。已在 `PermissionMatrixResourcesS3Test` 落地并通过；`DATA_SOURCE_FOLDER` 不再补第三组抽样，见上方资源组速查表"数据源"行的 `[不补]` 说明；`TABLE_STYLE`/`SCHEDULE_TASK_FOLDER` 各自的类型专属层级实现见下方"TABLE_STYLE / SCHEDULE_TASK_FOLDER own hierarchy"小节）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| contentResourceAdmin | REPORT | `mx_vs/folder/viewsheet1`（本节点，ADMIN 授权点） | ADMIN | ✓ | `[M8]` `contentResourceAdmin_adminOnReportItem_allowed_crossGroup` | 显式授权，作为下方 Rule 1 断言的前提 |
| contentResourceAdmin | REPORT | `mx_vs/folder/viewsheet1/deep`（子节点） | READ | ✓ | `[M8]` `contentResourceAdmin_readOnReportDeepChild_allowed_rule1CrossGroup` | Rule 1（向下）在 Repository 组同样成立 |
| deepOnlyAdmin（仅子节点 `mx_vs/folder/viewsheet1/sub` 被授予 ADMIN） | REPORT | `mx_vs/folder`（父节点） | ADMIN | ✗ | `[M8]` `deepOnlyAdmin_adminOnReportParentFolder_denied_rule2CrossGroup` | Rule 2（向上不穿透）在 Repository 组同样成立 |
| deepOnlyAdmin（同上） | REPORT | `mx_vs/folder`（同上，祖父节点） | READ | ✗ | `[M8]` `deepOnlyAdmin_readOnReportParentFolder_denied_rule3CrossGroup` | Rule 3（跨级不穿透）在 Repository 组同样成立 |

**TABLE_STYLE / SCHEDULE_TASK_FOLDER own hierarchy**（这两个类型各自有自己的 `ResourceType.getParent()` 覆写——`TABLE_STYLE` 用 `~` 分隔且真正多级，`SCHEDULE_TASK_FOLDER` 用 `/` 分隔但是和 `ASSET`/`REPORT` 各自独立的实现——跟 REPORT 用的通用 `/`-解析不是同一段代码，值得单独confirm 这两个覆写自己爬得对不对。最小化验证：只测 Rule 1（向下级联）+ Rule 2/3（不向上/跨级穿透），不重复"ADMIN 显式生效"和"ADMIN→RWD 隐含"（这两条已经在别处用通用机制验证过）。已在 `PermissionMatrixResourcesS3Test` 落地并通过）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| tableStyleAdmin（ADMIN 授予 `mxstyle~item`，`~` 分隔的中间节点） | TABLE_STYLE | `mxstyle~item~deep`（子节点） | READ | ✓ | `[M8]` `tableStyleAdmin_readOnDeepChild_allowed_rule1TableStyleHierarchy` | Rule 1（向下）：`TABLE_STYLE.getParent()` 沿 `~` 正确爬升 |
| tableStyleDeepOnlyAdmin（ADMIN 只授予 `mxstyle~item~sub`，比 `mxstyle` 深两级） | TABLE_STYLE | `mxstyle`（祖父节点） | ADMIN | ✗ | `[M8]` `tableStyleDeepOnlyAdmin_adminOnParentFolder_denied_rule2TableStyleHierarchy` | Rule 2（向上不穿透） |
| tableStyleDeepOnlyAdmin（同上） | TABLE_STYLE | `mxstyle`（同上） | READ | ✗ | `[M8]` `tableStyleDeepOnlyAdmin_readOnParentFolder_denied_rule3TableStyleHierarchy` | Rule 3（跨级不穿透）；`mxstyle` 自身另外授予了 `contentResourceAdmin` 一条无关的 READ，纯粹是为了让它的 `Permission` 非空——否则 `security.tablestyle.everyone` 兜底机制会把这行"应拒绝"的 READ 判成默认放行，见文末"附加"小节的"everyone"发现 |
| scheduleFolderAdmin（ADMIN 授予 `mxschedtree/item`，`/` 分隔的中间节点） | SCHEDULE_TASK_FOLDER | `mxschedtree/item/deep`（子节点） | READ | ✓ | `[M8]` `scheduleFolderAdmin_readOnDeepChild_allowed_rule1ScheduleFolderHierarchy` | Rule 1（向下）：`SCHEDULE_TASK_FOLDER.getParent()` 自己的覆写正确爬升，不依赖 `SCHEDULE_TASK`（本身扁平） |
| scheduleFolderDeepOnlyAdmin（ADMIN 只授予 `mxschedtree/item/sub`，比 `mxschedtree` 深两级） | SCHEDULE_TASK_FOLDER | `mxschedtree`（祖父节点） | ADMIN | ✗ | `[M8]` `scheduleFolderDeepOnlyAdmin_adminOnParentFolder_denied_rule2ScheduleFolderHierarchy` | Rule 2（向上不穿透） |
| scheduleFolderDeepOnlyAdmin（同上） | SCHEDULE_TASK_FOLDER | `mxschedtree`（同上） | READ | ✗ | `[M8]` `scheduleFolderDeepOnlyAdmin_readOnParentFolder_denied_rule3ScheduleFolderHierarchy` | Rule 3（跨级不穿透）；同上，`mxschedtree` 也另外授予了 `contentResourceAdmin` 一条无关 READ，避开 `security.scheduletask.everyone` 兜底 |

> **实现时发现：Rule 3 的"应拒绝"断言会被"everyone"兜底机制悄悄推翻。** 第一次草稿没有给 `mxstyle`/`mxschedtree` 这两个祖父节点额外挂无关的 READ 授权，结果 Rule 3 两行全部实测为"允许"而不是预期的"拒绝"——因为这两个祖父节点自己从未配置过任何权限（沿链路查到根都是空的），`SecurityEngine.checkPermission()` 的"everyone"兜底判定"完全未配置"就默认放行 READ（`security.tablestyle.everyone`/`security.scheduletask.everyone`，默认 `true`，机制详见文末"附加"小节），跟 `ASSET`/`REPORT`（没有这层兜底）的行为不一样。修法：给这两个祖父节点各自另外授予一条跟当前断言身份无关的 READ（授给 `contentResourceAdmin`），让 `Permission.isBlank()` 变成 `false`，兜底机制就不会触发，Rule 3 的拒绝断言才能真正测到目标代码路径（而不是被兜底路径掩盖）。

## S4 — 内容访问三条链路 + Role/Group 层级 + AND/OR 变体

> **机制范围说明**：User→Role/Group→资源三条链路、Role/Group 父子层级继承、AND/OR 变体，走的都是与资源类型无关的通用授权解析路径，架构设计文档 758 行将本切片的列范围定义为 "Repository / Worksheet" 两组。下表以 `ASSET` 为主验证资源类型；跨组抽样见下方 **S4-CROSS-GROUP**，只代表性验证一条链路，不要求三条链路 × AND/OR 在 `REPORT` 上重复展开。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-via-role | ASSET | `mx/folder/item` | WRITE | ✓ | `[M8]` `orgViewerViaRole_writeOnItem_allowed_userRoleResource_actionAgnostic` | User → Role → 资源；这一行故意授的是 WRITE 不是 READ（其余两条链路都是 READ），验证三条链路的解析机制本身跟 action 无关，不是隐式只认 READ |
| orgViewer-via-role | ASSET | `mx/folder/item` | READ | ✗ | `[M8]` `orgViewerViaRole_readAndAdminOnItem_denied_onlyWriteGranted` | 只 grant 了 WRITE |
| orgViewer-via-role | ASSET | `mx/folder/item` | ADMIN | ✗ | `[M8]`（同一方法，见上一行） | 同上 |
| orgViewer-via-group | ASSET | `mx/folder/item` | READ | ✓ | `[M8]` `orgViewerViaGroup_readOnItem_allowed_userGroupResource` | User → Group → 资源（group 直接被授权） |
| orgViewer-via-group | ASSET | `mx/folder/item` | WRITE | ✗ | `[M8]` `orgViewerViaGroup_writeOnItem_denied_onlyReadGranted` | |
| orgViewer-via-group-role | ASSET | `mx/folder/item` | READ | ✓ | `[M8]` `orgViewerViaGroupRole_readOnItem_allowed_userGroupRoleResource` | User → Group → Role → 资源（`orgViewerViaGroupRole` 自己不持有角色，靠所属的 `roleHolderGroup` 持有 `groupRole`） |
| orgViewer-via-group-role | ASSET | `mx/folder/item` | WRITE | ✗ | `[M8]` `orgViewerViaGroupRole_writeOnItem_denied_onlyReadGranted` | |

**S4-ROLE-HIERARCHY**（role1 继承父角色 role2，父角色持有实际 grant。已在 `PermissionMatrixResourcesS4Test` 落地并通过）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| roleHierarchyUser（role1→role2，role2 有 READ grant） | ASSET | `mx/folder/item` | READ | ✓ | `[M8]` `roleHierarchyUser_readOnItem_allowed_parentRoleGrantPropagates` | 父角色授权正确传播 |
| roleHierarchyNoGrantUser（role3→role4，role4 **无** grant） | ASSET | `mx/folder/item` | READ | ✗ | `[M8]` `roleHierarchyNoGrantUser_readOnItem_denied_notDefaultAllow` | 负路径：证明不是默认放行 |

**S4-GROUP-HIERARCHY**（group1 继承父组 group2，父组持有实际 grant。已在 `PermissionMatrixResourcesS4Test` 落地并通过）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| groupHierarchyUser（group1→group2，group2 有 READ grant） | ASSET | `mx/folder/item` | READ | ✓ | `[M8]` `groupHierarchyUser_readOnItem_allowed_parentGroupGrantPropagates` | 父组授权正确传播 |

**S4-AND**（独立 `@Test`，`permission.andCondition=true`。已在 `PermissionMatrixResourcesS4Test` 落地并通过）：

> 实现时改用了独立的 fixture，而不是直接复用主表的 `orgViewer-via-role`/`orgViewer-via-group-role`：主表的三条链路 fixture 都假设默认 OR 模式，如果直接在同一个资源上叠加 AND 场景，会让主表断言的可读性变差（要分清"这一步是 OR 模式判的还是 AND 模式判的"）。改用专门的资源 `mx/folder/anditem`（同时对 `viewerRole` 授 ROLE 授权、对 `andGroup` 授 GROUP 授权）+ 两个新用户：`andRoleOnlyUser`（只持有 `viewerRole`，不属于 `andGroup`）、`andBothUser`（既持有 `viewerRole` 又属于 `andGroup`），语义跟原设计一致（"只满足一条 vs 两条都满足"），只是不复用主表资源。

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| andRoleOnlyUser（只持有 `viewerRole`，`mx/folder/anditem` 同时对 `viewerRole`/`andGroup` 授权） | ASSET | `mx/folder/anditem` | READ | ✗ | `[M8]` `andRoleOnlyUser_readOnAndItem_denied_andModeRequiresBoth` | AND 模式：只满足 role，不满足 group → denied |
| andBothUser（持有 `viewerRole` 且属于 `andGroup`） | ASSET | `mx/folder/anditem` | READ | ✓ | `[M8]` `andBothUser_readOnAndItem_allowed_andModeBothSatisfied` | AND 模式：两条都满足 → allowed |

**S4-CROSS-GROUP**（用 `REPORT`（Repository 组）代表性抽样 via-role 一条链路，证明三条链路机制不是 `ASSET` 专属；不重复展开 via-group/via-group-role/AND 变体。已在 `PermissionMatrixResourcesS4Test` 落地并通过）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-via-role | REPORT | `mx_vs/folder/viewsheet1` | READ | ✓ | `[M8]` `orgViewerViaRole_readOnReportItem_allowed_crossGroup` | User → Role → 资源，在 Repository 组同样成立 |

## S5 — 层级继承（含 Rule 4：继承路径 W→D 提升）

> **机制范围说明**：父文件夹授权向子资源传播（含 Rule 4 W→D 提升）来自 `DefaultCheckPermissionStrategy` 的通用继承分支（L392-450），架构设计文档 759 行将本切片列范围定义为 "Repository / Worksheet + 数据源"。下表以 `ASSET` 为主验证资源类型；跨组抽样见下方 **S5-CROSS-GROUP**（基础继承传播）及 **S5-RULE4-WRITE-PROMOTES-DELETE**（Rule 4 提升逻辑，已覆盖 `DATA_SOURCE`/`QUERY`/`CUBE` 抽样）；另有 **S5-RULE5-INTERMEDIATE-PERMISSION-CAP**（中间文件夹已保存权限会封顶非 ADMIN 继承，ADMIN 不受影响，M8 实现 S3 时顺带发现）。全部已在 `PermissionMatrixResourcesS5Test` 落地并通过。
>
> **实现提醒**：本切片所有依赖非 ADMIN 继承的 fixture（除了 Rule 5 里验证 ADMIN 不受封顶影响的那一行）都必须对授权所在的祖先文件夹调用 `SecurityTestDataBuilder.markPermissionEdited()`，否则 `DefaultCheckPermissionStrategy` 的非 ADMIN 继承 `while` 循环不会在这个文件夹停下来，会一路爬到根节点、绕过文件夹自己的授权——实现时先漏调过一次，导致基础继承的用例误判为失败，见下方 S5-RULE5 的发现说明。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx/folder` 有 READ grant） | ASSET | `mx/folder/item`（子资源，自己无显式权限） | READ | ✓ | `[M8]` `orgViewerInherited_readOnItem_allowed_parentGrantPropagates` | 父级授权正确传播给子资源 |
| no-grant | ASSET | `mx/folder/item` | READ | ✗ | `[M8]` `noGrantUser_readOnItem_denied_noParentGrant` | 无父级授权、无继承 |
| no-grant | ASSET | `mx/folder`（父节点本身） | READ | ✗ | `[M8]` `noGrantUser_readOnFolder_denied_folderItselfUngranted` | 父节点自己也无授权 |

**S5-CROSS-GROUP**（用 `REPORT`（Repository 组）代表性抽样基础继承传播，证明机制不是 `ASSET` 专属；Rule 4 的跨组抽样见下方 S5-RULE4，已覆盖数据源，不在此重复。已在 `PermissionMatrixResourcesS5Test` 落地并通过）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx_vs/folder` 有 READ grant） | REPORT | `mx_vs/folder/viewsheet1`（子资源，自己无显式权限） | READ | ✓ | `[M8]` `orgViewerInherited_readOnReportItem_allowed_crossGroup` | 父级授权正确传播给子资源，在 Repository 组同样成立 |

**S5-RULE4-WRITE-PROMOTES-DELETE**（设计文档 Rule 4，`DefaultCheckPermissionStrategy.java` L392-450）。下表用 `ASSET`（Worksheet）+ `DATA_SOURCE`/`QUERY`/`CUBE`（数据源）两组做了跨组抽样，满足"证明 Rule 4 是通用继承机制、不是单一资源组专属"的要求，不需要再补 `REPORT` 的 Rule 4 场景。已在 `PermissionMatrixResourcesS5Test` 落地并通过：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| partialGrantUser（父节点 `mx/folder2` 只 grant WRITE，不给 DELETE/ADMIN） | ASSET | `mx/folder2/item`（子资源，自己无显式权限） | WRITE | ✓ | `[M8]` `partialGrantUser_writeOnItem_allowed_directInherit` | 直接继承 |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | DELETE | ✓ | `[M8]` `partialGrantUser_deleteOnItem_allowed_writePromotesDelete` | **提升**：父级 WRITE → 子级连带获得 DELETE |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | ADMIN | ✗ | `[M8]` `partialGrantUser_adminOnItem_denied_promotionExcludesAdmin` | 提升逻辑只处理 WRITE/DELETE，不会提升出 ADMIN |
| partialGrantUser（同上，子资源自己也设置了显式权限——哪怕是空的） | ASSET | `mx/folder2/item-explicit` | DELETE | ✗ | `[M8]` `partialGrantUser_deleteOnExplicitChild_denied_ownExplicitPermissionBlocksPromotion` | 负路径：子级有自己的显式权限时不走继承分支，提升不触发 |
| partialGrantDsUser（父节点 `mx_datasource_folder` 只 grant WRITE） | DATA_SOURCE | `mx_datasource_folder/ds1`（子资源） | DELETE | ✓ | `[M8]` `partialGrantDsUser_deleteOnDataSource_allowed_ruleAppliesToDataSource` | 同一规则在 DataSource 分支的体现 |
| partialGrantDsUser（同上） | QUERY | `Model::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | `[M8]` `partialGrantDsUser_deleteOnQuery_allowed_ruleAppliesToQuery` | 验证规则是通用继承机制，不是 Repository/Worksheet 专属 |
| partialGrantDsUser（同上） | CUBE | `mx_datasource_folder/ds1::cube1`（孙资源；注意 CUBE 的 resource key 顺序是"数据源在前、cube 名在后"，`::` 前是数据源路径——之前草稿误写成 `cube1::mx_datasource_folder/ds1`，跟 `ResourceType.CUBE.getParent()`/`AssetEventUtil.java` 的实际约定相反，已订正） | DELETE | ✓ | `[M8]` `partialGrantDsUser_deleteOnCube_allowed_ruleAppliesToCube` | 同上，覆盖 CUBE 子类型 |

**S5-RULE5-INTERMEDIATE-PERMISSION-CAP**（M8 实现 S3 时顺带发现，独立于 Rule 4。已在 `PermissionMatrixResourcesS5Test` 落地并通过）：

> **发现：中间文件夹一旦"自己保存过权限"，就会把非 ADMIN 的继承封顶在这个文件夹自己的权限范围内，祖先节点的 WRITE/DELETE 授权传不进来；但 ADMIN 授权不受这层封顶影响。**
>
> 验证方式：三层结构 `mx`（祖父，授予 `grandWriteUser` WRITE、`grandAdminUser` ADMIN）→ `mx/folder2`（父，显式只授予 `folderReadUser` READ，且这条权限标记为"已保存/已编辑"）→ `mx/folder2/item`（子，自己无显式权限）。实测结果：
>
| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| grandWriteUser（祖父 `mxcap` 授 WRITE；中间的 `mxcap/capFolder` 显式只授 `folderReadUser` READ，且已标记"已保存"） | ASSET | `mxcap/capFolder/item`（子资源，自己无显式权限） | DELETE | ✗ | `[M8]` `grandWriteUser_deleteOnItemUnderCappedFolder_denied_nonAdminCapped` | 祖父的 WRITE（含 Rule 4 提升出的 DELETE）传不到 `item`，被 `capFolder` 的显式 READ-only 权限挡住 |
| folderReadUser（同上设置） | ASSET | `mxcap/capFolder/item` | READ | ✓ | `[M8]` `folderReadUser_readOnItemUnderOwnFolder_allowed_basicInheritanceNotCapped` | `capFolder` 自己的 READ 正常向下传播给 `item`（基础继承，非"封顶"场景） |
| folderReadUser（同上） | ASSET | `mxcap/capFolder/item` | DELETE | ✗ | `[M8]` `folderReadUser_deleteOnItemUnderOwnFolder_denied_noPromotionSource` | `capFolder` 只授了 READ，没有 WRITE/DELETE 可提升 |
| grandAdminUser（祖父 `mxcap` 改授 ADMIN，`capFolder` 设置同上） | ASSET | `mxcap/capFolder/item` | DELETE | ✓ | `[M8]` `grandAdminUser_deleteOnItemUnderCappedFolder_allowed_adminBypassesCap` | 祖父的 **ADMIN** 不受 `capFolder` 封顶影响，照样传到 `item` |

> 根因：非 ADMIN action 的继承靠 `DefaultCheckPermissionStrategy` 里那个逐级向上爬的 `while` 循环，每爬一级都会用**当前这一级**的 `Permission.hasOrgEditedGrantAll(orgId)` 更新 `useParent`——一旦某一级"已保存"（`hasOrgEditedGrantAll=true`），循环立刻停在这一级，不再继续往上爬，最终只认这一级自己的权限（含 Rule 4 提升）。ADMIN action 走的是另一条完全独立的路径（私有 `getPermission()` 的"累加"分支），会无条件合并**整条祖先链**上的 ADMIN 授权，不受任何一级"已保存"状态的影响，所以能穿透这层封顶。
>
> **测试基础设施提醒**：`SecurityTestDataBuilder.grantPermission()` 本身不会把 `hasOrgEditedGrantAll` 设成 `true`（真实 EM 保存权限页面时才会设置，参见 `ResourcePermissionService.setResourceAdminPermissions()` 里的 `permission.updateGrantAllByOrg(orgId, tableModel.hasOrgEdited())`）。已给 builder 加了 `markPermissionEdited(type, resource, orgId)` 方法来正确模拟"这个资源的权限被真正保存过"，`capFolder` 这一行必须调用它，否则测出来的继承行为是错的（第一次草稿验证时就因为漏调这个方法，把 `folderReadUser` 的 READ 继承误判成失败）。

**Rule 4 提升对 GROUP/ROLE 被授权身份的验证**（提升逻辑对 USER/GROUP/ROLE/ORGANIZATION 四种被授权身份类型的处理代码是对称的，但之前所有 Rule4/Rule5 用例都只用过 USER。已在 `PermissionMatrixResourcesS5Test` 落地并通过）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| groupPromoUser（父节点 `mx/folder3` 只对 GROUP `promoGroup` grant WRITE，`groupPromoUser` 是该组成员） | ASSET | `mx/folder3/item`（子资源，自己无显式权限） | DELETE | ✓ | `[M8]` `groupPromoUser_deleteOnItem_allowed_writePromotionAppliesToGroupGrantee` | Rule 4 提升对 GROUP 被授权身份同样生效 |
| rolePromoUser（父节点 `mx/folder4` 只对 ROLE `promoRole` grant WRITE，`rolePromoUser` 持有该角色） | ASSET | `mx/folder4/item`（子资源，自己无显式权限） | DELETE | ✓ | `[M8]` `rolePromoUser_deleteOnItem_allowed_writePromotionAppliesToRoleGrantee` | Rule 4 提升对 ROLE 被授权身份同样生效 |

**边界守护 #1：多层封顶叠加，离资源最近的那层生效**（`PermissionMatrixResourcesS5Test` 落地并通过。最小化验证，只保留一行）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| topWriteUser（`mxstack` 授 WRITE 给 `topWriteUser`；更靠近资源的 `mxstack/mid` 也已保存权限，但授权对象是另一个不相关身份） | ASSET | `mxstack/mid/item`（子资源，自己无显式权限） | DELETE | ✗ | `[M8]` `topWriteUser_deleteOnItemUnderStackedCaps_denied_nearestCapWins` | 继承会停在离资源最近的 `mid` 这一层，`topWriteUser` 在更远的 `mxstack` 上的 WRITE 传不进来——证明多个封顶点叠加时不会"跳过近的、找到远的" |

**边界守护 #3：S4（角色层级）+ S5（文件夹继承）+ Rule 4 提升三者叠加**（`PermissionMatrixResourcesS5Test` 落地并通过。最小化验证，只保留一行）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| comboUser（`mx/combo` 只对 ROLE `comboParentRole` grant WRITE；`comboUser` 自己持有的是子角色 `comboChildRole`，靠角色继承链才拿到 `comboParentRole`） | ASSET | `mx/combo/item`（子资源，自己无显式权限） | DELETE | ✓ | `[M8]` `comboUser_deleteOnItem_allowed_roleHierarchyPlusFolderInheritancePlusPromotion` | 一次断言里同时验证了文件夹继承（S5）、Rule 4 提升、角色层级继承（S4）三层机制正确叠加 |

## 附加 — 未分配切片的资源组基线抽样（Portal Dashboard / 调度 / 库资源）

> 这三组在架构设计文档的 M7 切片计划（752-759 行）里**从未被分配到任何切片**：S2 只覆盖 `SECURITY_*`，S3/S4/S5 只覆盖 Repository/Worksheet/数据源。**已并入 `PermissionMatrixResourcesS3Test`**（不新建独立文件/切片）——本质上就是 S3 已经在测的"ADMIN 隐含 R/W/D"机制，只是换到了三个之前完全没测过的资源类型上：`DASHBOARD`/`SCHEDULE_TASK` 是扁平结构，没有 Rule1-3 可测；`SCRIPT` 虽然自己扁平，但 `SCRIPT.getParent()` 恒回到共享的 `SCRIPT_LIBRARY:*` 根节点，是一个真实的（只是单层）父子关系，所以额外补了 `SCRIPT_LIBRARY` 根节点级联（Rule1 式）和 WRITE→DELETE 提升（Rule4 式）两个场景。`TABLE_STYLE`/`SCHEDULE_TASK_FOLDER` 各自的类型专属层级实现（Rule1-3 最小化边界验证）已补在 S3-CROSS-GROUP 之后的"TABLE_STYLE / SCHEDULE_TASK_FOLDER own hierarchy"小节；调度组的 `ASSIGN` action 仍暂不展开，留作后续独立场景（可参照 S2 里 identityAdmin-role 对 ASSIGN 的处理方式）。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| contentResourceAdmin | DASHBOARD | `dashboard1`（本节点，ADMIN 授权点；扁平命名空间，无子节点） | ADMIN | ✓ | `[M8]` `contentResourceAdmin_adminOnDashboard_allowed` | 显式授权 |
| contentResourceAdmin | DASHBOARD | `dashboard1` | READ | ✓ | `[M8]` `contentResourceAdmin_readWriteDeleteOnDashboard_allowed_adminImpliesRWD` | ADMIN→隐含；DASHBOARD 无 Rule 1-3（扁平结构，无父子级联需要验证） |
| contentResourceAdmin | SCHEDULE_TASK | `mx_schedule_folder/task1`（`SCHEDULE_TASK`（不是 `SCHEDULE_TASK_FOLDER`）本身 `hierarchical=false`，路径里的"/"只是不透明字符，不会被解析成文件夹） | ADMIN | ✓ | `[M8]` `contentResourceAdmin_adminOnScheduleTask_allowed` | 显式授权 |
| contentResourceAdmin | SCHEDULE_TASK | `mx_schedule_folder/task1` | READ | ✓ | `[M8]` `contentResourceAdmin_readOnScheduleTask_allowed_adminImpliesRWD` | ADMIN→隐含 |
| contentResourceAdmin | SCRIPT | `mx_script_lib/script1`（SCRIPT 固定单层，`SCRIPT.getParent()` 恒回到 `SCRIPT_LIBRARY:*`） | ADMIN | ✓ | `[M8]` `contentResourceAdmin_adminOnScript_allowed` | 显式授权 |
| contentResourceAdmin | SCRIPT | `mx_script_lib/script1` | READ | ✓ | `[M8]` `contentResourceAdmin_readOnScript_allowed_adminImpliesRWD` | ADMIN→隐含 |
| scriptLibraryAdmin（在共享根节点 `SCRIPT_LIBRARY:"*"` 授 ADMIN，`mx_script_lib/script2` 从未单独授权过） | SCRIPT | `mx_script_lib/script2` | ADMIN | ✓ | `[M8]` `scriptLibraryAdmin_adminOnAnyScript_allowed_rootCascade` | Rule1 式根节点级联：`SCRIPT_LIBRARY` 根的 ADMIN 覆盖任意 script 实例 |
| scriptLibraryWriteUser（在 `SCRIPT_LIBRARY:"*"` 只授 WRITE，`mx_script_lib/script3` 从未单独授权过） | SCRIPT | `mx_script_lib/script3` | DELETE | ✓ | `[M8]` `scriptLibraryWriteUser_deleteOnAnyScript_allowed_rootWritePromotesDelete` | Rule4 式提升：`SCRIPT_LIBRARY` 根的 WRITE 提升出 DELETE，覆盖任意 script 实例；跟 S5-RULE4 的 `partialGrantUser` 是同一机制 |

> **发现：`DATA_SOURCE`/`SCRIPT`/`TABLE_STYLE`/`SCHEDULE_TASK_FOLDER` 还有一层额外的"everyone"兜底，默认值是 `true`，`ASSET`/`REPORT`/`DASHBOARD` 没有这层机制**（M8 实现 S3 时顺带发现，之前的资源组速查表和上面这张基线抽样表都没有意识到）：
>
> `SecurityEngine.checkPermission(Principal, ResourceType, String, ResourceAction)`（`SecurityEngine.java` 820-939 行，注意不是 `DefaultCheckPermissionStrategy`）在这四类资源类型上，当 `DefaultCheckPermissionStrategy` 判定拒绝之后，还会再走一层由系统属性控制的兜底：
>
> | 系统属性 | 默认值 | 控制的资源类型 |
> |---|---|---|
> | `security.datasource.everyone` | `true` | `DATA_SOURCE`/`DATA_SOURCE_FOLDER`/`DATA_MODEL_FOLDER`/`QUERY`/`QUERY_FOLDER`/`CUBE` |
> | `security.script.everyone` | `true` | `SCRIPT`/`SCRIPT_LIBRARY` |
> | `security.tablestyle.everyone` | `true` | `TABLE_STYLE`/`TABLE_STYLE_LIBRARY` |
> | `security.scheduletask.everyone` | `true` | `SCHEDULE_TASK_FOLDER`（不含 `SCHEDULE_TASK`/`SCHEDULE_CYCLE`，这两个本身是扁平的） |
>
> 效果：当某个资源**从未配置过任何权限**（`Permission.isBlank(orgId)`，沿链路查到根节点都是空的）时——
> - **属性为 `true`（默认值，即生产环境的默认行为）**：默认放行 **READ**（`allowed = action == READ`），WRITE/DELETE/ADMIN 仍然拒绝——完全没配置过的数据源/脚本/表样式/调度任务文件夹，任何登录用户都能看到。
> - **属性为 `false`**：不走这层兜底，退化成跟 `ASSET`/`REPORT` 一样——完全未配置时任何 action 都拒绝。
>
> 四个属性各自有一个 `SecurityEngine.updateSecurityXXXEveryoneValue()` 静态方法（`updateSecurityDatasourceEveryoneValue()` 等），可以绕开 10 秒缓存立即生效，方便测试里 toggle。
>
> **已在 `PermissionMatrixResourcesS5Test` 落地并通过 `[M8]`**（放在 S5 而不是 S3，是因为这个机制需要资源**从根节点开始完全没配置过**——S3 的 fixture 里 `SCRIPT_LIBRARY` 根节点已经有真实授权，会破坏这个前提；S5 的 fixture 从没碰过这四类资源各自的根节点，挑一个全新路径就能保证"全空"）。四个属性都测了，每个属性一个方法，方法内部同时验证 `true`（默认值，READ 放行/WRITE 拒绝）和显式 `false`（READ 也拒绝）两种状态：
> - `dataSourceEveryone_togglesDefaultReadOnUnconfiguredDataSource`
> - `scriptEveryone_togglesDefaultReadOnUnconfiguredScript`
> - `tableStyleEveryone_togglesDefaultReadOnUnconfiguredTableStyle`
> - `scheduleTaskFolderEveryone_togglesDefaultReadOnUnconfiguredFolder`

执行顺序：S2 → S3 → S4 → S5，低优先级行（no-grant / anonymous）按需补充；上方"附加"表已并入 `PermissionMatrixResourcesS3Test`（见该小节开头说明）。区二（S6-S8）见 `permission-matrix-actions.md`。

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
| `contentResourceAdmin` | ✓ A + (ADMIN→)R/W/D [S3] | ✓ A + (ADMIN→)R/W/D（扁平命名空间，不适用 Rule 2/3）[S3][M8] | ✓ A + (ADMIN→)R/W/D（基础 ADMIN 隐含语义评估后决定不补，与通用机制相同，见资源组速查表"数据源"行 `[不补]` 说明；Rule 4 提升逻辑已在 S5-RULE4 验证 [M8]） | ✓ A + (ADMIN→)R/W/D（`SCHEDULE_TASK` 本身扁平，不适用 Rule 2/3；`SCHEDULE_TASK_FOLDER` 自身 Rule1-3 已在 S3 落地）[S3][M8] | — | ✓ A + (ADMIN→)R/W/D，另有 `SCRIPT_LIBRARY` 根节点级联/WRITE→DELETE 提升；`TABLE_STYLE` 自身 Rule1-3 已在 S3 落地 [S3][M8] |
| `orgViewer-via-role` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A（评估后决定不补，见下方说明）[不补] | — | — | ✗ | — |
| `orgViewer-via-group` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A（同上）[不补] | — | — | ✗ | — |
| `orgViewer-via-group-role` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A（同上）[不补] | — | — | ✗ | — |
| `orgViewer-inherited` | ✓ R(子继父) / ✗ W/D/A [S5]；部分授权(仅 W)时子级会被提升出 D，见 S5-RULE4 [S5][M8] | — (扁平命名空间，无继承路径，不适用 S5) | ✓ R(子继父)，部分授权(仅 W)提升 D 同样适用 [S5][M8] | — | ✗ | — |
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

**DASHBOARD via S4 三条链路 `[不补]` 说明**：S4 存在的目的是证明 User→Role/Group/Group→Role 三条链路的解析机制与资源类型无关；这一点已经用 `ASSET`（主表）+ `REPORT`（S4-CROSS-GROUP）两个类型验证过——其中 `REPORT` 是真正有继承关系的类型，`ASSET` 也是，两者都能证明机制不依赖具体资源类型。`DASHBOARD` 是扁平命名空间，在它上面再跑一遍同样的三条链路解析代码，不会覆盖到任何新维度（既不测继承，也不测层级），纯属重复，因此评估后决定不补。
