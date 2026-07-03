# Permission Test Matrix — 区一：内容访问权限（Resources）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）
**Phase 2 M8 实现：** `PermissionMatrixResourcesTest`（S2-S5）
**姊妹文档：** 区二 Security Action 权限矩阵（S6-S8，`PermissionMatrixActionsTest`）见 `permission-matrix-actions.md`

图例：✓ = allowed　✗ = denied　— = n/a　`(ADMIN→)` = 由 ADMIN 隐含授权　`[P1]` = Phase 1 已覆盖　`[待补]` = 已在设计文档确认、尚未落到测试代码　`[附加]` = M7 切片计划未分配、本文档补记的基线抽样（见文末"附加"小节）

下方按切片（Slice）列出具体场景，每一行大致对应 `PermissionMatrixResourcesTest` 里的一个 `MatrixTestCase`（或一个独立 `@Test` 方法），方便实现前逐条 review。资源路径为 fixture 示例路径（与 Phase 2 计划 Task 4-5 的常量一致），不是生产环境真实路径。总览矩阵（按资源组归类的能力速查表）见文末附录。

### 资源组速查表（完整版见架构设计文档 § 区一 534-542 行）

| 资源组 | 代表 ResourceType | 适用 action | 层级继承 | 本文档覆盖情况 |
|---|---|---|---|---|
| Repository（Viewsheet 树） | `REPORT` | READ/WRITE/DELETE/ADMIN | 是，`/` 分隔真实父子继承 | S3/S4/S5 各有 `*-CROSS-GROUP` 抽样 `[待补]` |
| Worksheet | `ASSET` | READ/WRITE/DELETE/ADMIN | 是，`/` 分隔真实父子继承 | S3/S4/S5 主表全覆盖 |
| Portal Dashboard | `DASHBOARD` | READ/WRITE/DELETE/ADMIN | **否**——扁平命名空间，`DASHBOARD.getParent()` 恒返回 `null` | 无切片分配，仅文末附加基线抽样 `[待补]` |
| 数据源 | `DATA_SOURCE_FOLDER`/`DATA_SOURCE`/`CUBE`/`QUERY`（Logical Model） | READ/WRITE/DELETE/ADMIN | 是——文件夹用 `/`，model/cube 挂载用 `::` | 仅 S5-RULE4 覆盖 Rule 4 提升逻辑，基础 ADMIN/继承未验证 `[待补]` |
| 调度 | `SCHEDULE_TASK_FOLDER`/`SCHEDULE_TASK`/`SCHEDULE_CYCLE` | READ/WRITE/DELETE/ADMIN/ASSIGN | 仅文件夹层继承；task/cycle 本身扁平 | 无切片分配，仅文末附加基线抽样 `[待补]` |
| 安全管理 | `SECURITY_USER`/`SECURITY_GROUP`/`SECURITY_ROLE`/`SECURITY_ORGANIZATION` | User/Group 走 ADMIN，Role 走 ASSIGN | 各类型独立合成根节点级联，非路径分隔符继承 | S2 全覆盖 |
| 库资源 | `SCRIPT_LIBRARY`/`SCRIPT`（扁平单层） / `TABLE_STYLE_LIBRARY`/`TABLE_STYLE`（真正多级） | READ/WRITE/DELETE/ADMIN | Table Style 是，`~` 分隔；Script 否，固定单层 | 无切片分配，仅文末附加基线抽样 `[待补]` |

---

> **无 S1 切片**：siteAdmin/orgAdmin 对全部资源和 action 的完全访问是这两个角色的定义性质（`DefaultCheckPermissionStrategy` 对 `isSystemAdministrator`/`isOrgAdministrator` 的判断在查任何资源 grant 之前就直接放行），不依赖 fixture 里配置的具体权限，逐资源组重复断言价值很低，故不单独设切片。跨 org 负路径已由 Phase 1 `MultiTenantIsolationTest`（场景 13-18B）覆盖。siteAdmin/orgAdmin 仍会作为对照身份出现在 S2（本文档）以及 S6/S7（`permission-matrix-actions.md`）里。

## S2 — 安全身份管理边界（SECURITY_* 资源组）

**术语约定（本节及以下所有 S2 子表通用；与 `2026-06-25-permission-test-architecture-design.md` 452-458 行的术语辨析保持一致）：**
- **对 `<resource>` 设置 "Administrator Permissions"** = 在 `<resource>` 对应的 `Permission` 对象上，为某个身份（User/Group/Role）写入 `ADMIN` action。例如给 `targetUser2`（一个 `SECURITY_USER` 资源）设置 "Administrator Permissions"，等价于在 EM 的 Security > Users > `targetUser2` > Permission 标签页里，把某个身份勾进 Administrator 列，底层写入的就是 `` `ADMIN` `` action。测试代码通过 `SecurityTestDataBuilder` 直接写这个 `Permission` 对象，不经过 EM UI，也没有一个"操作者"用户去点这个按钮——它只是 fixture 静态构造出的一条规则："资源 X 的 Permission 上，身份 Y 被赋予 `ADMIN`"。
- **"用户类型"/"登录用户"列指的是登录去做权限检查的测试账号**，它不一定等于被设置了 "Administrator Permissions" 的那个身份本身：
  - 多数行里，登录用户本身就是被授权的身份（例如 `identityAdminUser` 直接对 `targetUser` 设置了 "Administrator Permissions"），这种情况登录用户＝被授权身份，是同一个对象。
  - 少数行里（典型例子见下面 S2-GRANTEE-VARIETY），被授权的身份是一个 Role/Group（例如 `targetUser2` 的 "Administrator Permissions" 授予了 `role0`），登录用户是**持有该 role / 属于该 group 的另一个 user**（例如 `viaRoleUser`）——登录用户自己从未被单独授权过，是靠"持有 role0"这层间接关系拿到权限的。这两种情况在表格里都会用括号注明具体设置，避免"谁被授权了什么"产生歧义。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 | 结论 |
|---|---|---|---|---|---|---|
| orgSecurityAdmin | SECURITY_ORGANIZATION | `matrix_org_id` | ADMIN | ✓ | 授权本身生效（第 2 行"拒绝"是否有意义的前提） | orgSecurityAdmin 能管理本 org 的安全设置 |
| orgSecurityAdmin | ASSET | `mx/folder/item` | READ | ✗ | 无内容权，证明不外溢到非安全身份资源类型 | orgSecurityAdmin 看不到任何内容资源（报表/仪表盘等） |
| orgSecurityAdmin | SECURITY_USER | `targetUser`（从未单独 grant 给 orgSecAdmin） | ADMIN | ✓ | **跨类型级联**（`DefaultCheckPermissionStrategy` L57-67）：只看当前 org 的 SECURITY_ORGANIZATION 是否有 ADMIN，不看具体 resource 路径 | orgSecurityAdmin 能管理 targetUser（编辑/删除），即使从没单独针对 targetUser 授权过 |
| orgSecurityAdmin | SECURITY_GROUP | `targetGroup`（同上，从未单独 grant） | ADMIN | ✓ | 同上，跨类型级联 | orgSecurityAdmin 能管理 targetGroup，同样即使从没单独授权过 |
| orgSecurityAdmin | SECURITY_ROLE | `viewerRole`（本 org 自建角色，从未单独 grant） | ADMIN | ✓ | 同上；仅对本 org 自建角色生效，全局角色不生效（`isNotGlobalRole`） | orgSecurityAdmin 能管理本 org 自建的 viewerRole 角色（编辑/删除/分配） |
| orgSecurityAdmin | SECURITY_USER | `targetUser` | WRITE | ✓ | **级联放行的是原始 action**，不是"隐式提升成 ADMIN 再走 RWD 兜底" | orgSecurityAdmin 能编辑 targetUser 的信息 |
| orgSecurityAdmin | SECURITY_ROLE | `viewerRole` | ASSIGN | ✓ | 同上，ASSIGN 不属于 R/W/D，如果是 ADMIN→RWD 兜底机制就不会覆盖到它，证明这是两套不同机制 | orgSecurityAdmin 能把 viewerRole 分配给其他用户 |
| orgSecurityAdmin | SECURITY_ROLE | 全局角色(org-less)，如 `addGlobalRole("globalRole0")` | ADMIN | ✗ `[待补]` | 负控制：`isNotGlobalRole` 挡掉全局角色；builder 方法已在 Task 3 加了（`addGlobalRole`），Task 4 用例暂未写 | orgSecurityAdmin 管不了全局角色（跨 org 共享，如内置 Everyone），只有 siteAdmin 能管 |
| identityAdmin-user(实例) | SECURITY_USER | `targetUser` | ADMIN | ✓ | | identityAdminUser 能管理 targetUser（编辑/删除该用户） |
| identityAdmin-user(实例) | SECURITY_USER | `anotherUser` | ADMIN | ✗ | 负路径：不越实例 | identityAdminUser 看不到/管不了 anotherUser——只对被明确授权的那个用户生效 |
| identityAdmin-user(实例) | SECURITY_USER | `targetUser` | WRITE | ✓ | ADMIN→隐含（`SecurityEngine` L826-832，通用兜底非 User 专属） | identityAdminUser 能编辑 targetUser 的信息 |
| identityAdmin-user(实例) | SECURITY_USER | `targetUser` | DELETE | ✓ | 同上 | identityAdminUser 能删除 targetUser |
| identityAdmin-user(通配) | SECURITY_USER | `targetUser` / `anotherUser` | ADMIN | ✓ / ✓ | 通配符对全部实例生效 | identityAdminWildUser 能管理 EM 里的所有用户，包括 targetUser 和 anotherUser |
| identityAdmin-user(通配) | ASSET | `mx/folder/item` | READ | ✗ | 仍然拿不到内容权 | identityAdminWildUser 虽然能管理所有用户，但看不到任何内容资源 |
| identityAdmin-group(实例) | SECURITY_GROUP | `targetGroup` | ADMIN | ✓ | | identityAdminGroupInstUser 能管理 targetGroup（编辑/删除该组） |
| identityAdmin-group(实例) | SECURITY_GROUP | `anotherGroup` | ADMIN | ✗ | 负路径：不越实例 | identityAdminGroupInstUser 管不了 anotherGroup——只对被明确授权的那个组生效 |
| identityAdmin-group(实例) | SECURITY_GROUP | `targetGroup` | WRITE | ✓ | ADMIN→隐含 | identityAdminGroupInstUser 能编辑 targetGroup 的信息 |
| identityAdmin-group(通配) | SECURITY_GROUP | `targetGroup` / `anotherGroup` | ADMIN | ✓ / ✓ | 通配符对全部实例生效 | identityAdminGroupWildUser 能管理 EM 里的所有组 |
| identityAdmin-group(通配) | ASSET | `mx/folder/item` | READ | ✗ | | identityAdminGroupWildUser 看不到任何内容资源 |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `targetRole` | ASSIGN | ✓ | 写入的是 ASSIGN，不是 ADMIN | identityAdminRoleUser 能把 targetRole 分配给其他用户 |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `targetRole` | WRITE | ✗ | **关键负路径**：ASSIGN 不隐含 WRITE | identityAdminRoleUser 不能编辑 targetRole 本身的定义（改名、改继承关系等） |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `targetRole` | DELETE | ✗ | ASSIGN 不隐含 DELETE | identityAdminRoleUser 不能删除 targetRole |
| identityAdmin-role ⚠️ | SECURITY_ROLE | `anotherRole` | ASSIGN | ✗ | 负路径：不越实例 | identityAdminRoleUser 不能分配 anotherRole——只对被明确授权的那个角色生效 |
| identityAdmin-role ⚠️ | ASSET | `mx/folder/item` | READ | ✗ | | identityAdminRoleUser 看不到任何内容资源 |

**S2-GRANTEE-VARIETY `[待补]`**（对照 `docs/superpowers/specs/security/Administrator.md` §1.1.1/1.1.2 补的场景；现有用例里被授权身份全部是 `Identity.USER` 类型，从没测过 role/group 作为被授权身份，以及三者同时设置的 OR 组合）：

> 下表"登录用户"列括号里描述的是 "Administrator Permissions" 的具体设置，不代表登录用户自己被授权——除非登录用户与被授权身份是同一个对象。例如第一行的设置是："`targetUser2` 的 "Administrator Permissions" 授予了身份 `role0`（一个 Role）；登录测试用的 `viaRoleUser` 这个 user 持有 `role0`"，所以 `viaRoleUser` 是通过持有该角色间接拿到权限，而不是被直接授权。

| 登录用户 | 资源类型 | 资源(示例) | Action | 预期 | 备注 | 结论 |
|---|---|---|---|---|---|---|
| viaRoleUser（设置：`targetUser2` 的 "Administrator Permissions" 授予 `role0`；viaRoleUser 持有 `role0`） | SECURITY_USER | `targetUser2` | ADMIN | ✓ | 被授权身份类型是 ROLE，不是 USER；viaRoleUser 靠持有该角色间接拿到权限 | viaRoleUser 能管理 targetUser2——即使从没被单独授权过，只因为持有 role0 |
| noRoleUser（同一设置，但 noRoleUser 不持有 `role0`） | SECURITY_USER | `targetUser2` | ADMIN | ✗ | 负路径：不持有该角色就不受益 | noRoleUser 看不到 targetUser2——同样没被单独授权，也不持有 role0 |
| viaGroupUser（设置：`targetUser2` 的 "Administrator Permissions" 授予 `group0`；viaGroupUser 是 `group0` 的直接成员） | SECURITY_USER | `targetUser2` | ADMIN | ✓ | 被授权身份类型是 GROUP；直接成员 | viaGroupUser 能管理 targetUser2——因为属于被授权的 group0 |
| viaSubGroupUser（同一设置；viaSubGroupUser 属于 `group0` 的子组 `group1`，自己不直接在 `group0` 里） | SECURITY_USER | `targetUser2` | ADMIN | ✓ | 被授权身份类型是 GROUP；子组成员间接继承（`checkUserGroupPermission` 递归向上找父组） | viaSubGroupUser 也能管理 targetUser2——即使只是 group0 的子组 group1 成员，间接继承同样生效 |
| viaAnyOneOfThreeUser（设置：`targetUser3` 的 "Administrator Permissions" 同时授予一个 user、一个 role、一个 group 三个不同身份；此登录用户只匹配其中 role 那一条，另两条对它不生效） | SECURITY_USER | `targetUser3` | ADMIN | ✓ | OR 组合：三个被授权身份只需满足其一即放行（默认 OR 模式） | viaAnyOneOfThreeUser 能管理 targetUser3——三个被授权身份（user/role/group）中只要匹配一个就够 |

**S2-GLOBAL-ROLE-ROOT `[待补]`**（对照 Administrator.md §1.3；`"Roles"` 全局根与 `"Organization Roles"` 本 org 根是两个独立节点，S2-ROOT-CASCADE 目前只测了后者）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 | 结论 |
|---|---|---|---|---|---|---|
| rootGlobalRoleAdmin（`"Roles"` 全局根 ADMIN） | SECURITY_ROLE | `addGlobalRole("globalRole0")` 创建的角色 | ADMIN | ✓ | 全局根级联到全局角色 | rootGlobalRoleAdmin 能管理全局角色 globalRole0 |
| rootGlobalRoleAdmin（同上） | SECURITY_ROLE | `targetRole`（本 org 自建角色） | ADMIN | ✗ | 全局根不覆盖本 org 角色——两个根节点相互独立 | rootGlobalRoleAdmin 管不了本 org 自建的 targetRole——全局根节点权限不跨到 org 角色 |
| rootRoleAdmin（`"Organization Roles"` 本 org 根 ADMIN，已有的 S2-ROOT-CASCADE 场景） | SECURITY_ROLE | `addGlobalRole("globalRole0")` 创建的角色 | ADMIN | ✗ | 反过来，本 org 根也不覆盖全局角色，互不越界 | rootRoleAdmin（本 org 角色根管理员）管不了全局角色 globalRole0——反过来同样不越界 |

**S2-ROOT-CASCADE**（独立 `@Test`，验证 "Users"/"Groups"/"Organization Roles" 三种根节点各自级联到全部同类型实例，`DefaultCheckPermissionStrategy` L134-186）：三个根节点在代码里就是字面意义上的具体资源，不是抽象概念——`rootUserAdmin`/`rootGroupAdmin`/`rootRoleAdmin` 的 "Administrator Permissions" 分别设置在资源 `SECURITY_USER:"Users"`、`SECURITY_GROUP:"Groups"`、`SECURITY_ROLE:"Organization Roles"`（本 org 根；全局根是 `"Roles"`，见下面 S2-GLOBAL-ROLE-ROOT）上。下表"资源"列写的 `targetUser`/`anotherUser` 等是级联**验证目标**，不是授权点本身——授权点固定就是对应类型的根节点，这里不重复列出。另外这跟 `orgSecurityAdmin` 的授权点是两套不相关的机制：orgSecurityAdmin 授权点是完全独立的 `SECURITY_ORGANIZATION` 资源类型（见上文主表第 1 行），不落在这三个根节点上，只是效果都是"管理该类型下全部实例"，容易被误认为同一回事。

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 | 结论 |
|---|---|---|---|---|---|---|
| rootUserAdmin（Users 根 ADMIN） | SECURITY_USER | `targetUser` / `anotherUser` | ADMIN | ✓ / ✓ | 级联到**全部**用户，不只是配置过的 | rootUserAdmin 能管理 EM 里的每一个用户，不局限于 targetUser/anotherUser 这两个例子 |
| rootGroupAdmin（Groups 根 ADMIN） | SECURITY_GROUP | `targetGroup` / `anotherGroup` | ADMIN | ✓ / ✓ | 级联到全部组 | rootGroupAdmin 能管理 EM 里的每一个组 |
| rootRoleAdmin（Organization Roles 根 ADMIN） | SECURITY_ROLE | `targetRole` | ASSIGN | ✓ | 根节点授予的是 ADMIN 不是 ASSIGN | rootRoleAdmin 能把 targetRole 分配给用户 |
| rootRoleAdmin（同上） | SECURITY_ROLE | `targetRole` | WRITE | ✓ | 根节点 ADMIN **能**拿到 W/D，与单个 role 的 ASSIGN-only 相反 | rootRoleAdmin 还能直接编辑/删除 targetRole 本身，不像普通的单角色授权那样只能分配 |

> **顺带证明"根节点权限覆盖子节点自身权限"**（Administrator.md §1.1.3，跟内容资源的继承方向相反）：`TARGET_USER` 已经在 S2 主表里被 `identityAdminUser`（`identityAdmin-user(实例)` 那几行）单独 grant 过；`rootUserAdmin` 并不在那条独立授权名单里，但对第一行 `rootUserAdmin` × `targetUser` 的断言依然是 ✓——因为 `DefaultCheckPermissionStrategy` 对 SECURITY_USER 的根节点检查在方法最前面就 `return true` 了，根本不会走到 `targetUser` 自己的 `Permission` 对象。这条规则靠 fixture 复用已经被验证，不需要新增用例，只需要在 `s2RootCascadeVariant()` 这行断言旁边加一句注释点明即可。
>
> **本表不单独设"非根节点对照组"行**：主表 row 23（`identityAdmin-role` ⚠️ × `anotherRole` × ASSIGN × ✗）已经是同一个 `MatrixTestCase`——principal、resource、action、预期结果完全一致，只是叙事角度不同（那边是证明"不越实例"，这里想证明"单个 role 的 ASSIGN 授权不会被误判成根节点级联"）。两个角度共用同一次断言即可，不需要在 Java 里重复实现；review 这张表时可以直接对照主表 row 23。

**S2-GROUP-CHAIN**（独立 `@Test`，验证 Group ≥3 层 BFS 委派继承，User 无此维度）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 | 结论 |
|---|---|---|---|---|---|---|
| chainAdmin（adminChainGroup0 ADMIN） | SECURITY_GROUP | `adminChainGroup1` | ADMIN | ✓ | 1 跳 | chainAdmin（在 adminChainGroup0 上被授权）能管理它的子组 adminChainGroup1 |
| chainAdmin（同上） | SECURITY_GROUP | `adminChainGroup2` | ADMIN | ✓ | 2 跳（孙节点）——只测 1 跳无法证明真 BFS | chainAdmin 也能管理孙组 adminChainGroup2——跨两层同样生效 |
| chainAdmin（同上） | SECURITY_GROUP | `adminChainSiblingGroup` | ADMIN | ✗ | 负路径：链外兄弟组不可达 | chainAdmin 管不了不在这条链路上的 adminChainSiblingGroup |
| midChainAdmin（只在 `adminChainGroup1` 上 grant ADMIN，不动 group0/group2 的授权）`[待补]` | SECURITY_GROUP | `adminChainGroup2`（`adminChainGroup1` 的子组） | ADMIN | ✓ | 向下依然传播——复用现有 3 层结构，不需要新建 fixture | midChainAdmin（只在 adminChainGroup1 上被授权）能管理它的子组 adminChainGroup2 |
| midChainAdmin（同上）`[待补]` | SECURITY_GROUP | `adminChainGroup0`（`adminChainGroup1` 的父组） | ADMIN | ✗ | **不向上穿透**——对照 Administrator.md §1.2 Check2/3（该文档这两行资源类型误写成 `SECURITY_USER`，按上下文应为 `SECURITY_GROUP`），是 SECURITY_GROUP 版本的 Rule 2/3 | midChainAdmin 管不了它的父组 adminChainGroup0——权限不会向上传递给祖先 |
| midChainAdmin（同上）`[待补]` | SECURITY_GROUP | `Groups`（根节点） | ADMIN | ✗ | 同上，不向上穿透到根节点 | midChainAdmin 更管不了 Groups 根节点——同样不向上传递 |

## S3 — ADMIN 隐含语义 + 父子双向规则（Rule 1-3）

> **机制范围说明**：Rule 1-3（ADMIN 父子双向传播）来自 `DefaultCheckPermissionStrategy` 对 `ResourceType.isHierarchical()` 的通用向上/向下级联逻辑，对架构设计文档定义的全部"有真正父子继承的组"（Repository/`REPORT`、Worksheet/`ASSET`、数据源/`DATA_SOURCE_FOLDER` 等）一视同仁生效，不是 `ASSET` 专属行为；`Portal Dashboard`/`DASHBOARD` 是扁平命名空间，不适用 Rule 2/3。下表以 `ASSET` 作为主验证资源类型；ADMIN→READ/WRITE/DELETE 隐含兜底本身是与资源类型无关的通用机制（`SecurityEngine.java` L826-832，S2 已用 SECURITY_* 验证过），此处不重复跨组验证，只对 Rule 1-3 做跨组抽样，见下方 **S3-CROSS-GROUP**。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| contentResourceAdmin | ASSET | `mx/folder/item`（本节点，ADMIN 授权点） | ADMIN | ✓ | 显式授权 |
| contentResourceAdmin | ASSET | `mx/folder/item` | READ | ✓ | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | WRITE | ✓ | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item` | DELETE | ✓ | ADMIN→隐含 |
| contentResourceAdmin | ASSET | `mx/folder/item/deep`（子节点） | READ | ✓ | **Rule 1（向下）**：父 ADMIN 传播给子节点 |
| no-grant | ASSET | `mx/folder`（父节点，仅子节点 `mx/folder/item/sub` 被授予 ADMIN） | ADMIN | ✗ | **Rule 2（向上不穿透）**：子 ADMIN 不给父 ADMIN |
| no-grant | ASSET | `mx/folder`（同上，祖父节点） | READ | ✗ | **Rule 3（跨级不穿透）**：孙节点 ADMIN 不给祖父访问权 |

**S3-CROSS-GROUP `[待补]`**（用 `REPORT`（Repository 组）做代表性抽样，证明 Rule 1-3 不是 `ASSET` 专属；`DATA_SOURCE_FOLDER` 抽样视优先级另行补充，不要求三组全排列）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| contentResourceAdmin | REPORT | `mx_vs/folder/viewsheet1`（本节点，ADMIN 授权点） | ADMIN | ✓ | 显式授权，作为下方 Rule 1 断言的前提 |
| contentResourceAdmin | REPORT | `mx_vs/folder/viewsheet1/deep`（子节点） | READ | ✓ | Rule 1（向下）在 Repository 组同样成立 |
| no-grant | REPORT | `mx_vs/folder`（父节点，仅子节点 `mx_vs/folder/viewsheet1/sub` 被授予 ADMIN） | ADMIN | ✗ | Rule 2（向上不穿透）在 Repository 组同样成立 |
| no-grant | REPORT | `mx_vs/folder`（同上，祖父节点） | READ | ✗ | Rule 3（跨级不穿透）在 Repository 组同样成立 |

## S4 — 内容访问三条链路 + Role/Group 层级 + AND/OR 变体

> **机制范围说明**：User→Role/Group→资源三条链路、Role/Group 父子层级继承、AND/OR 变体，走的都是与资源类型无关的通用授权解析路径，架构设计文档 758 行将本切片的列范围定义为 "Repository / Worksheet" 两组。下表以 `ASSET` 为主验证资源类型；跨组抽样见下方 **S4-CROSS-GROUP**，只代表性验证一条链路，不要求三条链路 × AND/OR 在 `REPORT` 上重复展开。

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

**S4-CROSS-GROUP `[待补]`**（用 `REPORT`（Repository 组）代表性抽样 via-role 一条链路，证明三条链路机制不是 `ASSET` 专属；不重复展开 via-group/via-group-role/AND 变体）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgViewer-via-role | REPORT | `mx_vs/folder/viewsheet1` | READ | ✓ | User → Role → 资源，在 Repository 组同样成立 |

## S5 — 层级继承（含 Rule 4：继承路径 W→D 提升）

> **机制范围说明**：父文件夹授权向子资源传播（含 Rule 4 W→D 提升）来自 `DefaultCheckPermissionStrategy` 的通用继承分支（L392-450），架构设计文档 759 行将本切片列范围定义为 "Repository / Worksheet + 数据源"。下表以 `ASSET` 为主验证资源类型；跨组抽样见下方 **S5-CROSS-GROUP**（基础继承传播）及 **S5-RULE4-WRITE-PROMOTES-DELETE**（Rule 4 提升逻辑，已规划 `DATA_SOURCE`/`QUERY`/`CUBE` 抽样）。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx/folder` 有 READ grant） | ASSET | `mx/folder/item`（子资源，自己无显式权限） | READ | ✓ | 父级授权正确传播给子资源 |
| no-grant | ASSET | `mx/folder/item` | READ | ✗ | 无父级授权、无继承 |
| no-grant | ASSET | `mx/folder`（父节点本身） | READ | ✗ | 父节点自己也无授权 |

**S5-CROSS-GROUP `[待补]`**（用 `REPORT`（Repository 组）代表性抽样基础继承传播，证明机制不是 `ASSET` 专属；Rule 4 的跨组抽样见下方 S5-RULE4，已覆盖数据源，不在此重复）：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgViewer-inherited（父文件夹 `mx_vs/folder` 有 READ grant） | REPORT | `mx_vs/folder/viewsheet1`（子资源，自己无显式权限） | READ | ✓ | 父级授权正确传播给子资源，在 Repository 组同样成立 |

**S5-RULE4-WRITE-PROMOTES-DELETE `[待补]`**（设计文档 Rule 4，`DefaultCheckPermissionStrategy.java` L392-450；Task 5 尚未实现，以下为待落地场景，用 sr13_8 `DPermission_Case.checkRepositorySetPermission` / `checkDataSourceSetPermission` 交叉验证过）。下表已经用 `ASSET`（Worksheet）+ `DATA_SOURCE`/`QUERY`/`CUBE`（数据源）两组做了跨组抽样，满足"证明 Rule 4 是通用继承机制、不是单一资源组专属"的要求，不需要再补 `REPORT` 的 Rule 4 场景：

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| partialGrantUser（父节点 `mx/folder2` 只 grant WRITE，不给 DELETE/ADMIN） | ASSET | `mx/folder2/item`（子资源，自己无显式权限） | WRITE | ✓ | 直接继承 |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | DELETE | ✓ | **提升**：父级 WRITE → 子级连带获得 DELETE |
| partialGrantUser（同上） | ASSET | `mx/folder2/item` | ADMIN | ✗ | 提升逻辑只处理 WRITE/DELETE，不会提升出 ADMIN |
| partialGrantUser（同上，子资源自己也设置了显式权限——哪怕是空的） | ASSET | `mx/folder2/item-explicit` | DELETE | ✗ | 负路径：子级有自己的显式权限时不走继承分支，提升不触发 |
| partialGrantDsUser（父节点 `mx_datasource_folder` 只 grant WRITE） | DATA_SOURCE | `mx_datasource_folder/ds1`（子资源） | DELETE | ✓ | 同一规则在 DataSource 分支的体现 |
| partialGrantDsUser（同上） | QUERY | `Model::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | 验证规则是通用继承机制，不是 Repository/Worksheet 专属 |
| partialGrantDsUser（同上） | CUBE | `cube1::mx_datasource_folder/ds1`（孙资源） | DELETE | ✓ | 同上，覆盖 CUBE 子类型 |

## 附加 — 未分配切片的资源组基线抽样（Portal Dashboard / 调度 / 库资源）`[待补]`

> 这三组在架构设计文档的 M7 切片计划（752-759 行）里**从未被分配到任何切片**：S2 只覆盖 `SECURITY_*`，S3/S4/S5 只覆盖 Repository/Worksheet/数据源。在正式补切片之前，先用 `contentResourceAdmin` 的显式 ADMIN 授权 + 隐含 R/W/D 各给一行最基础的抽样，把"完全零验证"的状态往前推一步；调度组的 `ASSIGN` action、库资源组 `TABLE_STYLE` 的 `~` 分隔多级继承与 `SCRIPT` 单层结构等专属细节暂不展开，留作后续独立场景（可参照 S2 里 identityAdmin-role 对 ASSIGN 的处理方式、S2-GROUP-CHAIN 对多层继承的处理方式）。

| 用户类型 | 资源类型 | 资源(示例) | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| contentResourceAdmin | DASHBOARD | `dashboard1`（本节点，ADMIN 授权点；扁平命名空间，无子节点） | ADMIN | ✓ | 显式授权 |
| contentResourceAdmin | DASHBOARD | `dashboard1` | READ | ✓ | ADMIN→隐含；DASHBOARD 无 Rule 1-3（扁平结构，无父子级联需要验证） |
| contentResourceAdmin | SCHEDULE_TASK | `mx_schedule_folder/task1`（文件夹下的任务节点） | ADMIN | ✓ | 显式授权 |
| contentResourceAdmin | SCHEDULE_TASK | `mx_schedule_folder/task1` | READ | ✓ | ADMIN→隐含 |
| contentResourceAdmin | SCRIPT | `mx_script_lib/script1`（SCRIPT 固定单层，`SCRIPT.getParent()` 恒回到 `SCRIPT_LIBRARY:*`） | ADMIN | ✓ | 显式授权 |
| contentResourceAdmin | SCRIPT | `mx_script_lib/script1` | READ | ✓ | ADMIN→隐含 |

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
| `identityAdmin-group(通配)` | ✗ | ✗ | ✗ | ✗ | ✓ A→R/W/D(*) 全部实例 [S2] | ✗ |
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
