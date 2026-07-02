# Permission Test Matrix

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`  
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）  
**Phase 2 M8 实现：** `PermissionMatrixResourcesTest`（区一 S2-S5）、`PermissionMatrixActionsTest`（区二 S6-S8）

图例：✓ = allowed　✗ = denied　— = n/a　`(ADMIN→)` = 由 ADMIN 隐含授权　`[P1]` = Phase 1 已覆盖　`[待补]` = 已在设计文档确认、尚未落到测试代码

下方按切片（Slice）列出具体场景，每一行大致对应 `PermissionMatrixResourcesTest`/`PermissionMatrixActionsTest` 里的一个 `MatrixTestCase`（或一个独立 `@Test` 方法），方便实现前逐条 review。资源路径为 fixture 示例路径（与 Phase 2 计划 Task 4-6 的常量一致），不是生产环境真实路径。总览矩阵（按资源组归类的能力速查表）见文末附录。

---

## 区一：内容访问权限 — `PermissionMatrixResourcesTest`

> **无 S1 切片**：siteAdmin/orgAdmin 对全部资源和 action 的完全访问是这两个角色的定义性质（`DefaultCheckPermissionStrategy` 对 `isSystemAdministrator`/`isOrgAdministrator` 的判断在查任何资源 grant 之前就直接放行），不依赖 fixture 里配置的具体权限，逐资源组重复断言价值很低，故不单独设切片。跨 org 负路径已由 Phase 1 `MultiTenantIsolationTest`（场景 13-18B）覆盖。siteAdmin/orgAdmin 仍会作为对照身份出现在 S2（`PermissionMatrixResourcesTest`）以及 S6/S7（`PermissionMatrixActionsTest`）里。

### S2 — 安全身份管理边界（SECURITY_* 资源组）

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgSecurityAdmin | SECURITY_ORGANIZATION | `matrix_org_id` | ADMIN | ✓ | 授权本身生效（第 2 行"拒绝"是否有意义的前提） |
| orgSecurityAdmin | ASSET | `mx/folder/item` | READ | ✗ | 无内容权，证明不外溢到非安全身份资源类型 |
| orgSecurityAdmin | SECURITY_USER | `targetUser`（从未单独 grant 给 orgSecAdmin） | ADMIN | ✓ | **跨类型级联**（`DefaultCheckPermissionStrategy` L57-67）：只看当前 org 的 SECURITY_ORGANIZATION 是否有 ADMIN，不看具体 resource 路径 |
| orgSecurityAdmin | SECURITY_GROUP | `targetGroup`（同上，从未单独 grant） | ADMIN | ✓ | 同上，跨类型级联 |
| orgSecurityAdmin | SECURITY_ROLE | `viewerRole`（本 org 自建角色，从未单独 grant） | ADMIN | ✓ | 同上；仅对本 org 自建角色生效，全局角色不生效（`isNotGlobalRole`） |
| orgSecurityAdmin | SECURITY_USER | `targetUser` | WRITE | ✓ | **级联放行的是原始 action**，不是"隐式提升成 ADMIN 再走 RWD 兜底" |
| orgSecurityAdmin | SECURITY_ROLE | `viewerRole` | ASSIGN | ✓ | 同上，ASSIGN 不属于 R/W/D，如果是 ADMIN→RWD 兜底机制就不会覆盖到它，证明这是两套不同机制 |
| orgSecurityAdmin | SECURITY_ROLE | 全局角色(org-less)（示例：`Everyone`） | ADMIN | ✗ `[待补]` | 负控制：`isNotGlobalRole` 挡掉全局角色；`SecurityTestDataBuilder` 目前没有创建 org-less 角色的方法，需要先补 fixture 能力 |
| identityAdmin-user(实例) | SECURITY_USER | `targetUser` | ADMIN | ✓ | |
| identityAdmin-user(实例) | SECURITY_USER | `anotherUser` | ADMIN | ✗ | 负路径：不越实例 |
| identityAdmin-user(实例) | SECURITY_USER | `targetUser` | WRITE | ✓ | ADMIN→隐含（`SecurityEngine` L826-832，通用兜底非 User 专属） |
| identityAdmin-user(实例) | SECURITY_USER | `targetUser` | DELETE | ✓ | 同上 |
| identityAdmin-user(通配) | SECURITY_USER | `targetUser` / `anotherUser` | ADMIN | ✓ / ✓ | 通配符对全部实例生效 |
| identityAdmin-user(通配) | ASSET | `mx/folder/item` | READ | ✗ | 仍然拿不到内容权 |
| identityAdmin-group(实例) | SECURITY_GROUP | `targetGroup` | ADMIN | ✓ | |
| identityAdmin-group(实例) | SECURITY_GROUP | `anotherGroup` | ADMIN | ✗ | 负路径：不越实例 |
| identityAdmin-group(实例) | SECURITY_GROUP | `targetGroup` | WRITE | ✓ | ADMIN→隐含 |
| identityAdmin-group(通配) | SECURITY_GROUP | `targetGroup` / `anotherGroup` | ADMIN | ✓ / ✓ | 通配符对全部实例生效 |
| identityAdmin-group(通配) | ASSET | `mx/folder/item` | READ | ✗ | |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `targetRole` | ASSIGN | ✓ | 写入的是 ASSIGN，不是 ADMIN |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `targetRole` | WRITE | ✗ | **关键负路径**：ASSIGN 不隐含 WRITE |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `targetRole` | DELETE | ✗ | ASSIGN 不隐含 DELETE |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `anotherRole` | ASSIGN | ✗ | 负路径：不越实例 |
| identityAdmin-role ⚠️ | ASSET | `mx/folder/item` | READ | ✗ | |

**S2-ROOT-CASCADE**（独立 `@Test`，验证 "Users"/"Groups"/"Organization Roles" 三种根节点各自级联到全部同类型实例，`DefaultCheckPermissionStrategy` L134-186）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| rootUserAdmin（Users 根 ADMIN） | SECURITY_USER | `targetUser` / `anotherUser` | ADMIN | ✓ / ✓ | 级联到**全部**用户，不只是配置过的 |
| rootGroupAdmin（Groups 根 ADMIN） | SECURITY_GROUP | `targetGroup` / `anotherGroup` | ADMIN | ✓ / ✓ | 级联到全部组 |
| rootRoleAdmin（Organization Roles 根 ADMIN） | SECURITY_ROLE | `targetRole` | ASSIGN | ✓ | 根节点授予的是 ADMIN 不是 ASSIGN |
| rootRoleAdmin（同上） | SECURITY_ROLE | `targetRole` | WRITE | ✓ | 根节点 ADMIN **能**拿到 W/D，与单个 role 的 ASSIGN-only 相反 |
| identityAdmin-role（对照组，非根节点） | SECURITY_ROLE | `anotherRole` | ASSIGN | ✗ | 证明单个 role 的 ASSIGN 授权不会被误判成根节点级联 |

**S2-GROUP-CHAIN**（独立 `@Test`，验证 Group ≥3 层 BFS 委派继承，User 无此维度）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| chainAdmin（adminChainGroup0 ADMIN） | SECURITY_GROUP | `adminChainGroup1` | ADMIN | ✓ | 1 跳 |
| chainAdmin（同上） | SECURITY_GROUP | `adminChainGroup2` | ADMIN | ✓ | 2 跳（孙节点）——只测 1 跳无法证明真 BFS |
| chainAdmin（同上） | SECURITY_GROUP | `adminChainSiblingGroup` | ADMIN | ✗ | 负路径：链外兄弟组不可达 |

### S3 — ADMIN 隐含语义 + 父子双向规则（Rule 1-3）

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| contentResourceAdmin | ASSET | `mx/folder/item`（本节点，ADMIN 授权点） | ADMIN | ✓ | 显式授权 |
| contentResourceAdmin | ASSET | `mx/folder/item` | READ | ✓ | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | WRITE | ✓ | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | DELETE | ✓ | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item/deep`（子节点） | READ | ✓ | **Rule 1（向下）**：父 ADMIN 传播给子节点 |
| no-grant | ASSET | `mx/folder`（父节点，仅子节点 `mx/folder/item/sub` 被授予 ADMIN） | ADMIN | ✗ | **Rule 2（向上不穿透）**：子 ADMIN 不给父 ADMIN |
| no-grant | ASSET | `mx/folder`（同上，祖父节点） | READ | ✗ | **Rule 3（跨级不穿透）**：孙节点 ADMIN 不给祖父访问权 |

### S4 — 内容访问三条链路 + Role/Group 层级 + AND/OR 变体

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgViewer-via-role | ASSET | `mx/folder/item` | READ | ✓ | User → Role → 资源 |
| orgViewer-via-role | ASSET | `mx/folder/item` | WRITE | ✗ | 只 grant 了 READ |
| orgViewer-via-role | ASSET | `mx/folder/item` | ADMIN | ✗ | 同上 |
| orgViewer-via-group | ASSET | `mx/folder/item` | READ | ✓ | User → Group → 资源（group 直接被授权） |
| orgViewer-via-group | ASSET | `mx/folder/item` | WRITE | ✗ | |
| orgViewer-via-group-role | ASSET | `mx/folder/item` | READ | ✓ | User → Group → Role → 资源 |
| orgViewer-via-group-role | ASSET | `mx/folder/item` | WRITE | ✗ | |

**S4-ROLE-HIERARCHY**（role1 继承父角色 role2，父角色持有实际 grant）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| roleHierarchyUser（role1→role2，role2 有 READ grant） | ASSET | `mx/folder/item` | READ | ✓ | 父角色授权正确传播 |
| roleHierarchyNoGrantUser（role3→role4，role4 **无** grant） | ASSET | `mx/folder/item` | READ | ✗ | 负路径：证明不是默认放行 |

**S4-GROUP-HIERARCHY**（group1 继承父组 group2，父组持有实际 grant）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| groupHierarchyUser（group1→group2，group2 有 READ grant） | ASSET | `mx/folder/item` | READ | ✓ | 父组授权正确传播 |

**S4-AND**（独立 `@Test`，`permission.andCondition=true`）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgViewer-via-role（同一资源同时有 role grant 和 group grant） | ASSET | `mx/folder/item` | READ | ✗ | AND 模式：只满足 role，不满足 group → denied |
| orgViewer-via-group-role（同时满足 group 和 role 两条 grant） | ASSET | `mx/folder/item` | READ | ✓ | AND 模式：两条都满足 → allowed |

### S5 — 层级继承（含 Rule 4：继承路径 W→D 提升）

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx/folder` 有 READ grant） | ASSET | `mx/folder/item`（子资源，自己无显式权限） | READ | ✓ | 父级授权正确传播给子资源 |
| no-grant | ASSET | `mx/folder/item` | READ | ✗ | 无父级授权、无继承 |
| no-grant | ASSET | `mx/folder`（父节点本身） | READ | ✗ | 父节点自己也无授权 |

**S5-RULE4-WRITE-PROMOTES-DELETE `[待补]`**（设计文档 Rule 4，`DefaultCheckPermissionStrategy.java` L392-450；Task 5 尚未实现，以下为待落地场景，用 sr13_8 `DPermission_Case.checkRepositorySetPermission` / `checkDataSourceSetPermission` 交叉验证过）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| partialGrantUser（父节点 `mx/folder2` 只 grant WRITE，不给 DELETE/ADMIN） | ASSET | `mx/folder2/item`（子资源，自己无显式权限） | WRITE | ✓ | 直接继承 |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | DELETE | ✓ | **提升**：父级 WRITE → 子级连带获得 DELETE |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | ADMIN | ✗ | 提升逻辑只处理 WRITE/DELETE，不会提升出 ADMIN |
| partialGrantUser（同上，子资源自己也设置了显式权限——哪怕是空的） | ASSET | `mx/folder2/item-explicit` | DELETE | ✗ | 负路径：子级有自己的显式权限时不走继承分支，提升不触发 |
| partialGrantDsUser（父节点 `mx_datasource_folder` 只 grant WRITE） | DATA_SOURCE | `mx_datasource_folder/ds1`（子资源） | DELETE | ✓ | 同一规则在 DataSource 分支的体现 |
| partialGrantDsUser（同上） | QUERY | `Model::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | 验证规则是通用继承机制，不是 Repository/Worksheet 专属 |
| partialGrantDsUser（同上） | CUBE | `cube1::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | 同上，覆盖 CUBE 子类型 |

---

## 区二：Security Action 权限 — `PermissionMatrixActionsTest`

### S6 — For-Org-× 边界（siteAdmin 允许，orgAdmin 拒绝）

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| siteAdmin | EM_COMPONENT | `monitoring/cache` | ACCESS | ✓ | |
| orgAdmin | EM_COMPONENT | `monitoring/cache` | ACCESS | ✗ | For Org × |
| siteAdmin | EM_COMPONENT | `monitoring/cluster` | ACCESS | ✓ | |
| orgAdmin | EM_COMPONENT | `monitoring/cluster` | ACCESS | ✗ | For Org × |
| siteAdmin | EM_COMPONENT | `settings/security/providers` | ACCESS | ✓ | |
| orgAdmin | EM_COMPONENT | `settings/security/providers` | ACCESS | ✗ | For Org × |
| siteAdmin | UPLOAD_DRIVERS | `*` | ACCESS | ✓ | |
| orgAdmin | UPLOAD_DRIVERS | `*` | ACCESS | ✗ | For Org × |
| siteAdmin | DEVICE | `*` | ACCESS | ✓ | |
| orgAdmin | DEVICE | `*` | ACCESS | ✗ | For Org × |

### S7 — For-Org-√ 边界（orgAdmin 允许，orgSecurityAdmin 拒绝——无 EM 访问权）

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgAdmin | EM_COMPONENT | `monitoring/dashboards` | ACCESS | ✓ | |
| orgSecurityAdmin | EM_COMPONENT | `monitoring/dashboards` | ACCESS | ✗ | 无 EM 访问权（只管安全身份） |
| orgAdmin | EM_COMPONENT | `settings/security/actions` | ACCESS | ✓ | |
| orgSecurityAdmin | EM_COMPONENT | `settings/security/actions` | ACCESS | ✗ | 同上 |
| orgAdmin | LOGIN_AS | `*` | ACCESS | ✓ | |
| orgAdmin | EM_COMPONENT | `settings/schedule/tasks` | ACCESS | ✓ | |

### S8 — 普通用户功能开关（取决于是否显式 grant）

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| viewer(有 grant，role 上有 AI_ASSISTANT ACCESS) | AI_ASSISTANT | `*` | ACCESS | ✓ | |
| viewer(无 grant) | AI_ASSISTANT | `*` | ACCESS | ✗ | |
| viewer(无 grant) | FREE_FORM_SQL | `*` | ACCESS | ✗ | |
| siteAdmin | FREE_FORM_SQL | `*` | ACCESS | ✓ | siteAdmin 恒放行，不受 dep-on-grant 限制 |

执行顺序：S2 → S3 → S4 → S5 → S6 → S7 → S8，低优先级行（no-grant / anonymous）按需补充。

---

## 附录：能力总览矩阵（按资源组归类）

> 下面两张表是原始的"用户类型 × 资源组/Security Action"速查表，用于快速查某个用户类型在某类资源上大致有什么能力；具体到某个 Action/某条边界规则的精确断言以上面按切片列出的场景表为准，两处如有出入以切片表 + 代码为准。

### 区一：用户类型 × 资源组（内容访问矩阵）

资源组定义见架构设计文档 § 区一。行为规则：
- `siteAdmin`（sysAdmin role）：跨所有 org、所有资源、所有 action 全通
- `orgAdmin`（Organization Administrator role）：本 org 全通；跨 org 全拒
- ADMIN 隐含语义（`SecurityEngine` L826-832）：拥有 ADMIN 则隐含 READ/WRITE/DELETE

Action 列：R=READ  W=WRITE  D=DELETE  A=ADMIN  S=SHARE

| 用户类型 | 层级内容资产(ASSET/REPORT) | 单体内容(VIEWSHEET/WS) | 数据层(DATA_SOURCE) | 调度(SCHEDULE_TASK) | 安全管理(SECURITY_USER/ORG) | 库资源(SCRIPT) |
|---|---|---|---|---|---|---|
| `siteAdmin` | ✓ R/W/D/A | ✓ R/W/D/A/S | ✓ R/W/D/A | ✓ R/W/D/A | ✓ R/W/D/A | ✓ R/W/D/A |
| `orgAdmin` | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A/S [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] | ✓ R/W/D/A [P1-need] |
| `orgSecurityAdmin` | ✗ R/W/D/A (无内容权) | ✗ | ✗ | ✗ | ✓ 任意 action，级联到全部 SECURITY_USER/GROUP + 本org SECURITY_ROLE 实例（含从未单独 grant 过的），不只是 SECURITY_ORGANIZATION 自身、也不只 ADMIN [S2] | ✗ |
| `identityAdmin-user(实例)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D({name}) / ✗ 其他实例 [S2] | ✗ |
| `identityAdmin-user(通配)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D(*) 全部实例 [S2] | ✗ |
| `identityAdmin-group(实例)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D({name}) / ✗ 其他实例 [S2] | ✗ |
| `identityAdmin-group(通配)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D(*) 全部实例 [S2] | ✗ |
| `identityAdmin-role` ⚠️非 ADMIN | ✗ | ✗ | ✗ | ✗ | ✓ ASSIGN({name}) 仅此action / ✗ W/D [S2] | ✗ |
| `contentResourceAdmin` | ✓ A + (ADMIN→)R/W/D [S3] | ✓ A + (ADMIN→)R/W/D/S [S3] | ✓ A + (ADMIN→)R/W/D [S3] | ✓ A + (ADMIN→)R/W/D [S3] | — | ✓ A + (ADMIN→)R/W/D [S3] |
| `orgViewer-via-role` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A/S [S4] | — | — | ✗ | — |
| `orgViewer-via-group` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A/S [S4] | — | — | ✗ | — |
| `orgViewer-via-group-role` | ✓ R / ✗ W/D/A [S4] | ✓ R / ✗ W/D/A/S [S4] | — | — | ✗ | — |
| `orgViewer-inherited` | ✓ R(子继父) / ✗ W/D/A [S5]；部分授权(仅 W)时子级会被提升出 D，见 S5-RULE4 [S5][待补] | — (非层级类型) | ✓ R(子继父)，部分授权(仅 W)提升 D 同样适用 [S5][待补] | — | ✗ | — |
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

### 区二：用户类型 × Security Action（功能开关矩阵）

Action 固定为 ACCESS 或 READ。For Org × 表示 orgAdmin 无法配置、仅 siteAdmin 可访问。

| 功能条目 | ResourceType | action | For Org | siteAdmin | orgAdmin | orgSecAdmin | viewer | no-grant |
|---|---|---|---|---|---|---|---|---|
| **EM 总入口** | EM | ACCESS | — | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Auditing | EM_COMPONENT | ACCESS | √ | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Monitoring/Cache | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Cluster | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Summary | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Log | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Dashboards | EM_COMPONENT | ACCESS | √ | ✓ | ✓ [S7] | ✗ | ✗ | ✗ |
| → Settings/Content/Drivers | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Content/MV | EM_COMPONENT | ACCESS | √ | ✓ | ✓ [S7] | ✗ | ✗ | ✗ |
| → Settings/Presentation/OrgSettings | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Presentation/Themes | EM_COMPONENT | ACCESS | √ | ✓ | ✓ [S7] | ✗ | ✗ | ✗ |
| → Settings/Schedule/Settings | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Schedule/Tasks | EM_COMPONENT | ACCESS | √ | ✓ | ✓ [S7] | ✗ | ✗ | ✗ |
| → Settings/Security/Providers | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Security/Actions | EM_COMPONENT | ACCESS | √ | ✓ | ✓ [S7] | ✗ | ✗ | ✗ |
| → Settings/General | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Notification | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Portal: Dashboard tab** | DASHBOARD | READ+WRITE | √ | ✓ | ✓ [S7] | — | dep on grant | ✗ |
| **Portal: Repository tab** | PORTAL_TAB | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Portal: Schedule tab** | SCHEDULER | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Bookmark: Open** | VIEWSHEET_ACTION | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Composer: Viewsheet** | VIEWSHEET | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **AI Assistant** | AI_ASSISTANT | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Login As** | LOGIN_AS | ACCESS | √ | ✓ | ✓ [S7] | ✗ | dep on grant | ✗ |
| **Upload Drivers** | UPLOAD_DRIVERS | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Edit Mobile Devices** | DEVICE | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Time Range** | SCHEDULE_OPTION | READ | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Free Form SQL** | FREE_FORM_SQL | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Cross Join** | CROSS_JOIN | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Profile** | PROFILE | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |

`dep on grant` = 取决于 Security Actions 中是否为该用户/组配置了该功能开关；默认关闭则 ✗。
