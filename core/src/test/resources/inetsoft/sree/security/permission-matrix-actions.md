# Permission Test Matrix — 区二：Security Action 权限（Actions）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）
**Phase 2 M8 实现：** 按切片拆成独立测试类——`PermissionMatrixActionsS6Test`/`S8Test`（均已落地），跟区一 `PermissionMatrixResourcesS2-5Test` 同构，不使用 `MatrixTestCase` 参数化 DSL，见 `2026-06-30-permission-test-phase2.md` 的设计变更说明。原计划的 S7 已评估后取消，理由见下方 S6 末尾"orgAdmin 角色级联 vs orgSecurityAdmin 权限级联"小节。
**姊妹文档：** 区一内容访问权限矩阵（S2-S5，按切片拆成 `PermissionMatrixResourcesS2Test`/`S3Test`/`S4Test`/`S5Test`）见 `permission-matrix-resources.md`

图例：✓ = allowed　✗ = denied　— = n/a

**"测试状态"列取值**（每个场景表都有这一列，用来回答"这一行有没有被自动化测试覆盖"，约定与 `permission-matrix-resources.md` 一致）：
- `[M8]` = 已落地并通过，后面附对应 `@Test` 方法名
- `[M8⚠️]` = 已落地，但实测结果与预期不符，测试已 `@Disabled` 并注明原因
- `[待补]` = 已在设计文档确认、尚未落到测试代码，后面附未落地的具体原因
- `[不补]` = 已评估过是否要补，结论是不补，后面附"已经在哪验证过"的具体说明

下方按切片（Slice）列出具体场景，每一行对应 `PermissionMatrixActionsS{6,8}Test` 里的一个独立 `@Test` 方法（不使用 `MatrixTestCase`/`@ParameterizedTest` DSL，理由见 `2026-06-30-permission-test-phase2.md` 的设计变更说明）。资源路径尽量使用生产真实 key（`ActionPermissionService`/`orgAdminActionExclusions` 里出现的原样字符串），不是随意编造的 fixture 路径。总览矩阵（按 Security Action 归类的能力速查表）见文末附录。

**每个切片的推进方式**（区一已验证有效，区二沿用）：先在本文档写这个切片的详细场景表，再照着表实现对应的 Java 测试类并跑通，最后把测试状态从占位回填成 `[M8]`（或发现分歧则 `[M8⚠️]` + `@Disabled` 并记录根因），再进入下一个切片——文档和代码是同一个循环里前后脚产出的，不会出现"文档写完了但代码对不上"的情况。遇到需要单独证明的机制（例如某个资源类型自己的层级/默认值行为），仿照区一 `S2-GROUP-CHAIN`/`S5-RULE4` 的做法在对应切片下加一个具名的独立小节，不硬塞进主表——S6 的 "DEVICE/SCHEDULE_OPTION 最小探查" 就是这种小节的例子。

---

> siteAdmin/orgAdmin 在区一（`permission-matrix-resources.md`）里已作为对照身份出现过；本文档的 S6 是它们在 Security Action 边界上的对照场景。

## S6 — For-Org-× 边界（orgAdmin 拒绝）

**关联机制：** `DefaultCheckPermissionStrategy.checkPermission()` 里一段 Security Action 专属的早退逻辑：`if(!ActionPermissionService.isOrgAdminAction(type, resource) && SUtil.isMultiTenant()) return false;`。`isOrgAdminAction()` 靠硬编码排除名单 `ActionPermissionService.orgAdminActionExclusions`（`EM_COMPONENT` 16 条 + `SCHEDULE_TASK` 3 条 + `UPLOAD_DRIVERS` 1 条）判断该 Action 是否 siteAdmin 专属；命中排除名单时，这条早退逻辑先于显式授权检查执行，即使 orgAdmin 对该资源有显式授权也会被挡住。Phase 1（`MultiTenantIsolationTest`/`PermissionHierarchyTest`）只测过 `VIEWSHEET`/`DATA_SOURCE`/`ASSET`/`SECURITY_USER` 的跨 org 隔离和角色/权限层级，从未触达这条路径，是完全不同的机制。

**范围收窄（讨论后确认）：** 不再断言 siteAdmin allow——siteAdmin 的全通是结构性保证（`isSysAdmin || isSiteAdmin(principal)` 在检查排除名单之前就已经 `return true`），跟区一 S1（siteAdmin/orgAdmin 全权访问）因"definitional，不依赖 fixture"不单独设切片是同一个理由；本切片只验证排除名单本身在挡 orgAdmin。

**环境限制（影响测试写法，跟区一 S2-S5 不同）：** `SUtil.isMultiTenant()` = `isSecurityEnabled() && LicenseManager.isEnterprise() && "security.users.multiTenant"属性`；`LicenseManager.isEnterprise()` 靠 `Class.forName("inetsoft.enterprise.EnterpriseConfig")` 探测 classpath 判断，`community/core` 测试模块的 classpath 里没有 enterprise 模块的类，`isEnterprise()` 在这个模块里结构性恒为 `false`——不管 `SecurityTestDataBuilder` 默认设的 `security.users.multiTenant=true` 属性是什么，`isMultiTenant()` 永远算不出 `true`。因此本切片每个用例都要用 `Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS)` 把 `SUtil.isMultiTenant()` 单独 stub 成 `true`（`DefaultCheckPermissionStrategyTest.java` 已有先例），不能像区一 S2-S5 那样直接裸调 `PermissionMatrixVerifier`。

**主表是穷举，不是代表性抽样**：`orgAdminActionExclusions` 是一份固定、可枚举的生产数据（不是"证明某个机制不依赖具体资源类型"那种可以抽 1 条代表的通用机制，参见区一 S3-S5 的 `*-CROSS-GROUP`）——未来谁改这个数组时手滑删掉或改错一条，只有真正断言过那一条的测试才抓得到。因此这里没有像区一那样挑 1-2 条代表，而是让测试直接读 `ActionPermissionService.orgAdminActionExclusions` 数组做参数化，20 条全部覆盖，且不会因为数组内容变化而跟文档/测试脱节。

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 | 结论 |
|---|---|---|---|---|---|---|---|
| orgAdmin（持有内置全局角色 `Organization Administrator`；对 `ActionPermissionService.orgAdminActionExclusions` 每一条都有显式 ACCESS 授权） | 逐条覆盖 `orgAdminActionExclusions` 的全部 20 条：`EM_COMPONENT`（`monitoring/cache`/`cluster`/`log`/`summary`、`settings/general`、`settings/presentation/org-settings`、`settings/properties`、`settings/security/provider`/`sso`/`googleSignIn`、`settings/content/data-space`/`drivers-and-plugins`、`settings/logging`、`settings/schedule/settings`/`status`、`notification`，共 16 条）、`SCHEDULE_TASK`（`__asset file backup__`/`__balance tasks__`/`__update assets dependencies__`，共 3 条）、`UPLOAD_DRIVERS`（`*`，1 条） | 见左列 | ACCESS | ✗（全部 20 条） | `[M8]` `orgAdmin_deniedOnEveryOrgAdminActionExclusion_whenMultiTenant`（`@ParameterizedTest`，直接从 `ActionPermissionService.orgAdminActionExclusions` 取参数，20 个 case 全过） | mock `isMultiTenant()=true`；排除名单挡在显式授权检查之前；参数直接读生产数组而不是在测试里手抄字符串，数组以后增删条目测试会自动跟上 | orgAdmin 即使对这 20 项每一项都有显式访问权，在多租户模式下一个都打不开——排除名单机制对全部条目一致生效，不只是抽样验证过的那几条 |
| orgAdmin（同一批授权中 `monitoring/cache` 那一条） | EM_COMPONENT | `monitoring/cache` | ACCESS | ✓ | `[M8]` `orgAdmin_emComponentMonitoringCache_allowedByGrant_whenNotMultiTenant` | mock `isMultiTenant()=false`；证明上一行的拒绝确实来自这条早退逻辑，不是别的原因（比如授权本身写错） | 关掉多租户模式后，同一条显式授权立刻生效——排除名单只在多租户模式下才拦截 |

**`DEVICE`/`SCHEDULE_OPTION`"Time Range" 最小探查：**

> 每个资源两条测试，机制不同（review 澄清后拆开，原先各只有一条 orgAdmin 测试，名称暗示走的是 `isMultiTenant`-gated 排除检查，实际两条都在 `checkOrgAdminPermission()` 的 `default` 分支——`return isOrgAdmin && ActionPermissionService.isOrgAdminAction(type, resource);`——被短路放行，在到达该门禁或显式授权/无权限默认放行检查之前就已经 `return true`，跟 mock 的 `isMultiTenant()` 值、跟 `DEVICE` 是否有显式授权都无关）：
>
> - orgAdmin 角色级联基线（钉住 Issue #75603/#75604 的真实生产场景本身——orgAdmin 角色单独满足 `checkOrgAdminPermission()` 的 `default` 分支门禁，在 `checkPermission()` 内部短路放行，跟第一部分"orgAdmin 角色级联 vs orgSecurityAdmin 权限级联"那对测试同一个机制）：
>   - `DEVICE`：`[M8]` `orgAdmin_device_allowedViaRoleCascade_whenMultiTenant`
>   - `SCHEDULE_OPTION`（`timeRange`）：`[M8]` `orgAdmin_scheduleOptionTimeRange_allowedViaRoleCascade_whenMultiTenant_unconfigured`
> - orgSecurityAdmin（无 `Organization Administrator` 角色，`checkOrgAdminPermission()` 的 `default` 分支门禁评估为 false，不会短路，真正走到 `isMultiTenant()`-gated 排除检查 + 其后的显式授权检查/无权限默认放行兜底分支）：
>   - `DEVICE`：显式授予 ACCESS 后，mock `isMultiTenant()=true` 时仍放行（排除名单不挡、显式授权检查生效）——`[M8]` `orgSecurityAdmin_device_allowedByGrant_whenMultiTenant_notInExclusionList`
>   - `SCHEDULE_OPTION`（`timeRange`）：完全没配置任何授权，mock `isMultiTenant()=true` 时仍放行（真正落到 `DefaultCheckPermissionStrategy` 的"无权限默认放行"兜底分支）——`[M8]` `orgSecurityAdmin_scheduleOptionTimeRange_allowedByDefault_whenMultiTenant_unconfigured`
>
> **结论（Issue #75603、#75604，均已修复）：**
> - **Issue #75603**：`DeviceController`（`community/core/src/main/java/inetsoft/web/composer/vs/objects/controller/DeviceController.java`）的 `newDevice`/`editDevice`/`deleteDevice` 已加上 `@Secured(@RequiredPermission(resourceType = DEVICE, resource = "*", actions = ACCESS))`，并新增 `checkOrgAllowedToEditDevices()` → `DeviceRegistry.isOrgAllowedToEditDevices()`（`!isEnterprise() || isSiteAdmin || currentOrg == defaultOrg`）挡住非默认 org 的 orgAdmin。回归测试 `DeviceControllerTest`（`newEditDeleteDevice_requireDeviceAccessPermission`、`newEditDeleteDevice_orgNotAllowed_rejectsAndDoesNotWrite`）已通过。
> - **Issue #75604**：`ScheduleTaskService.sanitizeConditions()` 的 `canUseTimeRange` 已叠加 `timeRangeAllowedForOrg = !isMultiTenant() || (isSiteAdmin(principal) && 同 org)`，跟 `createTaskDialogModel()` 算 UI 用的 `timeRangeEnabled` 口径一致。回归测试 `ScheduleTaskServiceSanitizeTest.sanitizeConditions_orgAdminMultiTenant_stripsTimeRangeDespiteDefaultAllow` 已通过。

**后续系统性排查：DEVICE/SCHEDULE_OPTION 这两个 bug 是不是孤例？（两条排查路线，均已核实）**

> 发现 #75603/#75604 后追问了一个更系统的问题："其他 For-Org-× 项会不会也有类似的『UI 隐藏但接口没真正拦』的问题？"分两条路线查，结论都记录在这里，避免以后重复排查同一个问题。

> **路线 1——`ActionPermissionService.java` 里还有没有其他"用内联 `isSiteAdmin`/`isMultiTenant` 隐藏树节点、但对应资源没进 `orgAdminActionExclusions`"的项（即 DEVICE/SCHEDULE_OPTION 那种模式）？**
>
> 通读全文件，逐一列出全部用这个内联判断控制 EM Actions 树节点可见性的地方（`getInternalScheduleTasksNode`/`getScheduleOptionsNode`/根节点构建/`isEMActionVisible` 共享辅助方法等），并对照每处的 `ResourceType`+路径是否出现在 `orgAdminActionExclusions` 里。**结论：除了已经报的 DEVICE 和 SCHEDULE_OPTION，其余全部能在排除名单里对上号**（`UPLOAD_DRIVERS`、三个内部调度任务、`notification`/`monitoring/{cache,cluster,summary,log}`/`settings/{general,properties,logging}` 等），属于"UI 藏 + 引擎也真的拒绝"的纵深防御，不是新漏洞。这条路线到此为止。

> **路线 2——已经在排除名单里、引擎层确实会拒绝的这 20 项，各自实际的写入/读取接口是不是真的接了权限检查？（即 `DeviceController` 那种"排除名单本身没错，但接口压根没查"的模式）**
>
> 逐条找到 20 项各自对应的真实 controller 端点，核对其 `@Secured`/`@RequiredPermission`/显式 `checkPermission()` 用的 `ResourceType`+资源字符串是否跟排除名单里的**精确一致**。18 项核实无误（`UPLOAD_DRIVERS`、`settings/general`、`settings/properties`、`settings/logging`、`settings/content/data-space`、`settings/content/drivers-and-plugins`、`settings/schedule/settings`、`settings/schedule/status`、`notification`、`monitoring/summary`、`monitoring/cache`、`monitoring/log`、`settings/security/provider`、`settings/security/sso`、`settings/presentation/org-settings` 等）。`settings/security/googleSignIn` 的写接口不在 community 模块里（企业版功能），community 里这个功能直接强制关闭，本次排查范围排除。**但另外 2 项发现了真实的、跟 `DeviceController` 同一种模式的越权漏洞，已分别报为 Issue #75605 和 Issue #75606：**
>
> - **Issue #75605（`monitoring/cluster` 资源字符串对不上）**：排除名单里排除的是精确字符串 `"monitoring/cluster"`，但 `ClusterController.java` 的 `pause-server`/`resume-server`/`cluster-status`/`cluster-enabled` 四个端点的 `@Secured` 用的资源字符串全部是 `"monitoring/cluster/reportCluster"`——`isOrgAdminAction()` 是精确字符串匹配，两者不是同一个字符串，排除名单对这几个端点完全不生效。任何 org admin 都能直接调 `POST /api/em/monitoring/cluster/pause-server`/`resume-server` 暂停/恢复整个共享集群，跟"这个功能 UI 上对 org admin 隐藏"的设计意图完全相反。比 DEVICE/SCHEDULE_OPTION 更严重，因为集群是跨租户共享的基础设施。
> - **Issue #75606（内部调度任务的保存路径绕过排除名单）**：`orgAdminActionExclusions` 专门排除了三个内部系统任务（`__asset file backup__`/`__balance tasks__`/`__update assets dependencies__`），但保存这些任务时唯一的门禁 `ScheduleTaskService.canDeleteInternalTask()` 是：
>   ```java
>   if(organizationManager.isSiteAdmin(principal) || organizationManager.isOrgAdmin(principal)) {
>      return true;   // 只要是 org admin 角色就直接放行，根本没调 checkPermission(SCHEDULE_TASK, ...)
>   }
>   return engine.checkPermission(principal, ResourceType.SCHEDULE_TASK, task.getName(), ResourceAction.WRITE);
>   ```
>   org admin 角色直接短路掉了引擎检查，能通过 `POST /api/em/schedule/task/save` 改这三个内部系统任务的调度条件（`saveTask` 对 internal task 只处理 conditions，不处理 actions，所以改不了任务本身要做什么，但能改时间/频率），跟排除名单"这三个任务连 org admin 都不该碰"的设计意图相反。另外 `ScheduleService.runScheduledTask()`（手动触发任务执行）也只检查 `hasTaskPermission(owner, READ)`，没有检查 `SCHEDULE_TASK` 权限，可能存在类似问题，但依赖内部任务 owner 身份的具体权限计算方式，本次未深入验证，留作这个 Issue 里的开放问题一并说明。
>
> 两个 Issue 的守护测试暂不预先补——跟 #75603/#75604 那一对同一个测试层级（`ClusterController` 的 `@Secured` 资源字符串走反射断言、`ScheduleTaskService.canDeleteInternalTask()` 走直接单元测试），等实际修复的时候再一并补上 `@Disabled` 守护用例，不单独提前做。

**orgAdmin 角色级联 vs orgSecurityAdmin 权限级联（原计划的 S7，评估后取消独立切片，并入 S6）：**

> 架构设计文档原把 S7 定义为"For-Org-√ 边界：orgAdmin 允许、orgSecurityAdmin 拒绝（无 EM 访问权）"，铺开成对多个资源分别验证。评估后发现：S7 想测的"orgAdmin 在没被排除的项上放行"其实是 `checkOrgAdminPermission()` 的 `default` 分支 `return isOrgAdmin && ActionPermissionService.isOrgAdminAction(type, resource);`——跟 S6 已经验证的 `isOrgAdminAction()`/排除名单是**同一段代码、同一个机制**（只是"命中排除名单→拒绝"和"没命中→放行"两个分支），S6 的 `DEVICE`/`SCHEDULE_OPTION` 探查已经顺带证明过"没命中→orgAdmin 放行"这半边，没必要再拿 `monitoring/dashboards`/`settings/security/actions`/`settings/schedule/tasks`/`LOGIN_AS` 重复验证同一件事。
>
> 但排查过程中发现一个真正没测过、值得留下的机制：`checkOrgAdminPermission()` 的入口守卫是 `if(!isOrgAdmin && !hasOrgAdminPermission) return false;`——持有 `Organization Administrator` 角色（`isOrgAdmin`）或持有 `SECURITY_ORGANIZATION` ADMIN 授权（`hasOrgAdminPermission`）任一满足即可进入下面的 `switch`；但 Action 类资源落到的 `default` 分支只看 `isOrgAdmin`，完全不看 `hasOrgAdminPermission`。也就是说 `orgSecurityAdmin`（只有 `SECURITY_ORGANIZATION` ADMIN 授权，不持有 `Organization Administrator` 角色，S2.1 已验证他们能级联管理 `SECURITY_USER`/`GROUP`/`ROLE`）对 EM Actions **完全拿不到级联访问权**——角色驱动和权限驱动这两条 org-admin 级联路径在 Action 资源上分道扬镳。S2/S6 都没有对照测过这一点。
>
> 只留一对最小对照（同一个非排除名单资源 `monitoring/dashboards`，两个身份都不在该资源上直接授权，只靠各自的 org-admin 级联资格）：
>
> | 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 | 结论 |
> |---|---|---|---|---|---|---|---|
> | orgAdmin（持有 `Organization Administrator` 角色，对 `monitoring/dashboards` 没有任何直接授权） | EM_COMPONENT | `monitoring/dashboards` | ACCESS | ✓ | `[M8]` `orgAdmin_emComponentMonitoringDashboards_allowedViaRoleCascade_whenMultiTenant` | mock `isMultiTenant()=true`；单靠角色级联放行，没有直接授权 | orgAdmin 光靠持有角色就能访问未被排除的 EM 功能，不需要单独授权 |
> | orgSecurityAdmin（只有 `SECURITY_ORGANIZATION` ADMIN 授权，不持有 `Organization Administrator` 角色，对 `monitoring/dashboards` 同样没有任何直接授权） | EM_COMPONENT | `monitoring/dashboards` | ACCESS | ✗ | `[M8]` `orgSecurityAdmin_emComponentMonitoringDashboards_deniedDespiteOrgPermission_whenMultiTenant` | mock `isMultiTenant()=true`；`hasOrgAdminPermission=true` 但 `default` 分支不看这个字段 | orgSecurityAdmin 即使对本 org 安全设置有完整 ADMIN 权限，也打不开任何 EM 功能——`SECURITY_ORGANIZATION` 授权的级联对 Action 资源不生效，只对 SECURITY_* 身份资源生效（S2.1） |

## S8 — 普通用户功能开关（取决于是否显式 grant）

> **机制范围说明**：本切片验证"管理员在 Security Actions 里编辑过某功能开关的权限记录后，普通用户是否按记录被放行/拒绝"，走的是跟区一 S4 完全相同的 User→Role/Group→资源 通用解析路径（架构设计文档 778 行）。**注意**：`VIEWSHEET_ACTION`/`VIEWSHEET_TOOLBAR_ACTION`/`SHARE`/`AI_ASSISTANT`/`SCHEDULE_OPTION`/`CHART_TYPE_FOLDER` 等一批资源类型在 `DefaultCheckPermissionStrategy`（L273-287）里都属于"该资源在这个 org 从未被编辑过时默认放行"的类型。下面除 S8-CHART-TYPE-DEFAULT-ASYMMETRY 外的所有 grant/no-grant 对照行，用的都是"管理员已经编辑过这条权限记录（`hasOrgEditedGrantAll=true`），只是没把这个身份加进去"的 fixture，不是"完全没人碰过"的 fixture——两者走不同分支，不要混淆。"完全没人碰过"的默认放行分支已经由 S6 的 `SCHEDULE_OPTION:timeRange` 场景代表性验证过一次，本切片不重复验证，除非该资源类型有专属的非对称默认值（见下方 Chart Types）。

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| viewer(有 grant，role0 上有 AI_ASSISTANT ACCESS) | AI_ASSISTANT | `*` | ACCESS | ✓ | `[M8]` `user0_aiAssistantAccess_allowed_viaRoleGrant` | 权限记录已编辑（授予 role0），via-role 单点检查 |
| viewer(无 grant，同一条已编辑的权限记录) | AI_ASSISTANT | `*` | ACCESS | ✗ | `[M8]` `user1_aiAssistantAccess_denied_editedRecordWithoutGrant` | 同一条记录下未被授权的身份 |
| viewer(无 grant，同一条已编辑的权限记录) | FREE_FORM_SQL | `*` | ACCESS | ✗ | `[M8]` `user1_freeFormSqlAccess_denied_editedRecordWithoutGrant` | |
| siteAdmin | FREE_FORM_SQL | `*` | ACCESS | ✓ | `[M8]` `siteAdmin_freeFormSqlAccess_allowed_bypassesGrantCheck` | siteAdmin 恒放行（在任何资源类型判断之前就短路），definitional，一个资源代表性验证即可，不逐项重复 |

### S8-BOOKMARK — Bookmark 四个条目（独立 flat 授权，`VIEWSHEET_ACTION`）

> 架构设计文档 641-644 行：`Bookmark`（Open and Create）/`OpenBookmark`/`ShareBookmark`/`ShareToAll` 是同级独立资源，`checkPermission()` 分别单独调用，机制上跟区一 S4 的 User→Role/Group→资源 三条链路完全一样（资源类型换成 `VIEWSHEET_ACTION`）。前端 `viewer-app.component.ts` 对这 4 个 flag 的组合展示逻辑（菜单可见性、禁用态）是 UI 层行为，不在本 Java 权限矩阵范围内，不在此处补测。

**S8-BOOKMARK-LANES**（在 `Bookmark` 上照搬 S4 三条链路 + 角色/组两条继承链，代表性验证 `VIEWSHEET_ACTION` 也走通用解析、不是 `ASSET` 专属；不在其余 3 个条目上重复展开）：

> **实现时发现的命名冲突（已修正）**：本表最初把 via-group 和 via-group-role 两行都写成"属于 `group0`"。按字面实现会是同一个 group——既直接授权（via-group）又持有 `role0`（via-group-role）——那 via-group-role 那一行的用户会先靠直接群组授权通过，根本没走到"群组→角色"这条解析路径，等于没测到东西。实现时拆成了两个不同的 group（`group0` 直接授权；`bookmarkRoleHolderGroup` 自己无授权、只持有 `role0`），跟 `PermissionMatrixResourcesS4Test` 的 `viewerGroup`/`roleHolderGroup` 拆分是同一个理由。下表已按实际拆分后的名字更新。

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| user0-via-role（持有 `role0`，`Bookmark` 对 `role0` 有 READ grant） | VIEWSHEET_ACTION | `Bookmark` | READ | ✓ | `[M8]` `user0_bookmarkRead_allowed_viaRole` | User → Role → 资源 |
| user1-via-role（不持有 `role0`，同一条已编辑记录） | VIEWSHEET_ACTION | `Bookmark` | READ | ✗ | `[M8]` `user1_bookmarkRead_denied_viaRole_notDefaultAllow` | 负路径，证明不是默认放行 |
| user0ViaGroup（属于 `group0`，`Bookmark` 对 `group0` 有 READ grant） | VIEWSHEET_ACTION | `Bookmark` | READ | ✓ | `[M8]` `user0ViaGroup_bookmarkRead_allowed_userGroupResource` | User → Group → 资源 |
| user0ViaGroupRole（自己不持有角色，靠所属 `bookmarkRoleHolderGroup` 持有的 `role0` 拿到 grant；`bookmarkRoleHolderGroup` 自己无直接授权） | VIEWSHEET_ACTION | `Bookmark` | READ | ✓ | `[M8]` `user0ViaGroupRole_bookmarkRead_allowed_userGroupRoleResource` | User → Group → Role → 资源 |
| user0ViaRoleHierarchy（持有 `role1`，`role1` 继承 `role2`，`Bookmark` 只对 `role2` 有 grant） | VIEWSHEET_ACTION | `Bookmark` | READ | ✓ | `[M8]` `user0ViaRoleHierarchy_bookmarkRead_allowed_parentRoleGrantPropagates` | 角色继承：跟 S4-ROLE-HIERARCHY 同一机制，验证在 `VIEWSHEET_ACTION` 上依然成立 |
| user0ViaGroupHierarchy（属于 `group1`，`group1` 继承 `group2`，`Bookmark` 只对 `group2` 有 grant） | VIEWSHEET_ACTION | `Bookmark` | READ | ✓ | `[M8]` `user0ViaGroupHierarchy_bookmarkRead_allowed_parentGroupGrantPropagates` | 组继承：跟 S4-GROUP-HIERARCHY 同一机制，验证在 `VIEWSHEET_ACTION` 上依然成立 |

> 角色/组继承机制本身跟资源类型无关（`AuthenticationProvider` 里角色/组链路解析代码不区分 `ResourceType`），S4-ROLE-HIERARCHY/S4-GROUP-HIERARCHY 已经在 `ASSET` 上证明过；这里加两行只是代表性确认同一条链路在 `VIEWSHEET_ACTION` 上没有被 Security Action 专属逻辑（比如排除名单）挡住，不打算在下面 S8-SWEEP 的每一项上都重复挂角色/组继承——那是把同一个通用机制在 38 个资源上验证 38 次，没有新增覆盖。

**S8-BOOKMARK-INDEPENDENCE**（证明 `OpenBookmark`/`ShareBookmark`/`ShareToAll` 三者互不隐含，只用 via-role 单链路代表性验证）：

> **实现时补的一处防呆**：`ShareBookmark`/`OpenBookmark` 跟 `Bookmark` 一样是"无权限默认放行"清单里的 `VIEWSHEET_ACTION` 类型。如果不对这两个资源也调用 `markPermissionEdited()`（哪怕是空授权），`shareToAllOnlyUser` 会因为"从未配置默认放行"而在这两项上恰好也通过——测试会通过，但不是因为验证了"三者互不隐含"，是因为压根没触发显式匹配分支。已在 fixture 里把 `Bookmark`/`ShareToAll`/`ShareBookmark`/`OpenBookmark` 四个资源都标记为已编辑。

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| shareToAllOnlyUser（只对 `ShareToAll` 有 grant，`ShareBookmark`/`OpenBookmark` 均已编辑但无 grant） | VIEWSHEET_ACTION | `ShareToAll` | READ | ✓ | `[M8]` `shareToAllOnlyUser_bookmarkFlags_independentlyChecked` | |
| shareToAllOnlyUser（同上） | VIEWSHEET_ACTION | `ShareBookmark` | READ | ✗ | `[M8]`（同一方法内追加断言） | 证明 `ShareToAll` 放行不会连带放行 `ShareBookmark` |
| shareToAllOnlyUser（同上） | VIEWSHEET_ACTION | `OpenBookmark` | READ | ✗ | `[M8]`（同一方法内追加断言） | 证明三者互不隐含 |

### S8-CHART-TYPE — Chart Types 两级继承（`CHART_TYPE` → `CHART_TYPE_FOLDER`，跨类型覆写）

> `ResourceType.java` L47-62 确认：`CHART_TYPE.getParent()` 是类型专属覆写，返回的父节点类型是**不同**的 `CHART_TYPE_FOLDER`（不是像 `ASSET.getParent()` 那样返回同类型的 `/`-split 父节点）。这跟区一 S3 给 `TABLE_STYLE`/`SCHEDULE_TASK_FOLDER` 做最小 Rule1-3 验证是同一个理由——不能只信任"跟 ASSET 一样"，要单独证明这个跨类型覆写本身爬得对。

> **实现时发现的额外机制（改变了本节两处结论）**：`SecurityEngine.checkPermission()`（不是 `DefaultCheckPermissionStrategy`，是外面包一层的方法）对 `CHART_TYPE` 有一段专属兜底（`SecurityEngine.java` L834-847）：如果内部解析判定拒绝，会**额外对父文件夹重新发起一次全新的顶层 `checkPermission()` 调用**，不是 `DefaultCheckPermissionStrategy` 内部那种"爬一次就完事"的climb。这条路径完全在架构设计文档和最初的代码走读之外，跑测试时才暴露出来，影响如下：
> - **HIERARCHY 的兄弟隔离行**：兄弟文件夹（`Line`）如果留成"完全不动"，会被这条兜底判定为默认放行，跟 `chartFolderGrantUser` 有没有 `Area` 的授权无关——测试会通过，但没测出"授权不跨兄弟节点泄漏"。已改成让 `Line` "已编辑但空授权"（真正的拒绝状态，而不是未配置），才能把这条岔路堵死。
> - **DEFAULT-ASYMMETRY 的叶子行结论反了**：`Others/Waterfall` 和其父文件夹 `Others` 都完全未配置时，实测是**放行**，不是文档 647 行说的默认拒绝——`DefaultCheckPermissionStrategy` 内部确实会判拒绝，但 `SecurityEngine` 的兜底会对 `Others` 重新发起一次顶层检查，而 `Others` 未配置本身命中默认放行清单，于是叶子也跟着放行了。也就是说"完全未配置"这个场景下 `CHART_TYPE_FOLDER` 和 `CHART_TYPE` 并没有真正的放行/拒绝不对称，只是走的代码路径不同，最终都收敛到放行。已把这一行的期望值和结论文字改成实测结果，架构设计文档 647 行的"不对称"结论需要订正。
>
> **这段兜底不是本次改动引入的**：`git log -L` 定位到 `SecurityEngine.java` 这段代码是历史提交 `41527a863`（"fix Bug #70538, for chart type, if user do not set the permission, to use the parent folder permission"）加的，2025-03 就上线生效——是产品既有的设计意图（子类型未配置时退回父分类的结果），不是这次测试暴露出的新缺陷，只是架构设计文档写"不对称"时没追到这段历史。真正的产品前端入口是 `GET /api/chart/getAvailableChartStyles`（`ChartStylesController` → `GraphTypeUtil.checkChartStylePermission()` → `SecurityEngine.checkPermission()`），Composer 绑定图表时的"图表类型"下拉框就是用这个接口过滤可选类型。
>
> **三种"分类 × 子类型"配置组合都已覆盖，只是分散在不同测试里，交叉说明如下**：
> 1. **分类 + 子类型都完全未配置** → `anyViewer_chartTypeOthersWaterfallRead_allowed_viaParentRetryFallback_notAnAsymmetry`（DEFAULT-ASYMMETRY，`Others`/`Others/Waterfall`）→ ✓ 放行
> 2. **子类型自己被明确拒绝（已编辑空记录），分类允许** → `chartFolderExplicitEmptyUser_barIntervalRead_denied_ownEmptyRecordBlocksPromotion`（HIERARCHY，`Bar`/`Bar/Interval`）→ ✗ 拒绝
> 3. **分类被明确拒绝（已编辑空记录），子类型完全未单独配置** → `chartFolderGrantUser_lineStepLineRead_denied_siblingFolderNotGranted`（HIERARCHY，`Line`/`Line/Step Line`）——这条测试最初是为了证明"兄弟隔离"写的，但它的 fixture 形状（`Line` 文件夹已编辑空授权、`Line/Step Line` 自己完全未碰）恰好精确对应组合 3，一并当作这个组合的验证，不用再补新测试 → ✗ 拒绝

**S8-CHART-TYPE-HIERARCHY**（`Area`/`Area/Step Area` 验证跨类型继承本身；`Line` 兄弟文件夹隔离；`Bar`/`Bar/Interval` 验证自身显式空记录挡住继承——用独立的 folder/child 对而不是复用 `Area/Step Area`，因为 `markPermissionEdited` 是资源级标记不是身份级，"未配置"和"已编辑空记录"两种状态不能共存在同一个资源上）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| chartFolderGrantUser（只对 `CHART_TYPE_FOLDER:Area` 有 READ grant，子类型 `Area/Step Area` 自己无权限记录） | CHART_TYPE | `Area/Step Area` | READ | ✓ | `[M8]` `chartFolderGrantUser_areaStepAreaRead_allowed_climbsToCrossTypeParent` | Rule1：子类型未配置时正确爬到跨类型的 `CHART_TYPE_FOLDER` 父节点 |
| chartFolderGrantUser（同上，只对 `Area` 文件夹有 grant；`Line` 文件夹已编辑但空授权，不是"完全不动"） | CHART_TYPE | `Line/Step Line` | READ | ✗ | `[M8]` `chartFolderGrantUser_lineStepLineRead_denied_siblingFolderNotGranted` | Rule2/3：兄弟文件夹 `Line` 未被授权，`Area` 的 grant 不跨兄弟节点生效——`Line` 必须显式标记已编辑，否则会被 `SecurityEngine` 的 `CHART_TYPE` 父级重试兜底默认放行（见上方说明） |
| chartFolderExplicitEmptyUser（`Bar/Interval` 自己有一条空的显式权限记录，`Bar` 文件夹本身有 grant） | CHART_TYPE | `Bar/Interval` | READ | ✗ | `[M8]` `chartFolderExplicitEmptyUser_barIntervalRead_denied_ownEmptyRecordBlocksPromotion` | 跟区一 S5-RULE4 同一条"子资源有自己的显式权限记录就不再走父级继承"规则，验证在跨类型 parent 下依然成立 |

**S8-CHART-TYPE-DEFAULT-ASYMMETRY**（架构设计文档 647 行标注的默认值不对称——实测证明这个结论是错的，两者并无不对称，见上方说明；两行都用从未 `grantPermission`/`markPermissionEdited` 过的全新 fixture 资源，不复用 Hierarchy 小节已显式配置过的 `Area`/`Line`/`Bar`）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| anyViewer（`CHART_TYPE_FOLDER:Point` 从未被编辑过任何权限记录） | CHART_TYPE_FOLDER | `Point` | READ | ✓ | `[M8]` `anyViewer_chartTypeFolderPointRead_allowed_unconfiguredDefaultsAllow` | `CHART_TYPE_FOLDER` 在"无权限默认放行"清单里，完全未配置 = 默认允许 |
| anyViewer（`Others/Waterfall` 及其父文件夹 `Others` 均从未被编辑过任何权限记录） | CHART_TYPE | `Others/Waterfall` | READ | ✓ | `[M8]` `anyViewer_chartTypeOthersWaterfallRead_allowed_viaParentRetryFallback_notAnAsymmetry` | `CHART_TYPE` 叶子本身不在默认放行清单里，但 `SecurityEngine.checkPermission()` 对 `CHART_TYPE` 的父级重试兜底（`SecurityEngine.java` L834-847，`DefaultCheckPermissionStrategy` 内部逻辑之外）会对同样未配置的父文件夹 `Others` 重新发起一次顶层检查，命中默认放行——跟上一行同样是"完全未配置"，结论**并不相反**，两者殊途同归都放行，只是代码路径不同 |

### S8-SWEEP — 其余全部独立功能开关（读生产树，grant user0 / 不 grant user1）

> **改用生产数据源，不再手抄资源字符串**：设计这一节时手写了几个资源路径（`PORTAL_TAB:Repository`、Social Sharing 的 "Copy Link"），去 `ActionPermissionService.java` 核对后发现两个都是错的——Portal "Repository" 标签对应的真实资源字符串是 `"Report"`（`getPortalTabsNode()` L400-409），Social Sharing 的 "Copy Link" 真实资源字符串是 `"link"`（`getSharingNode()` L716-723，注释就是"原 `link`"）；而且 Portal Tabs 整棵子树是 `PortalThemesManager.getPortalTab(i)` 跑出来的，不是编译期固定列表。跟 S6 发现 `monitoring/cluster` 资源字符串对不上是同一类风险，只是这次在写文档阶段就抓到了，没有流到生产漏洞。因此这一节不手抄字符串，改成跟 S6 的 `orgAdminActionExclusions` 一样的做法：测试直接调用 `ActionPermissionService.getActionTree(principal)`（用 siteAdmin 或非多租户环境调用，`addFilteredChildren` 只在 `isOrgAdmin=true` 时才会按 `isOrgAdminAction()` 过滤，siteAdmin/非多租户下拿到的是完整未过滤的树）拿到真实树，遍历 `folder()==false` 的叶子节点作为参数化数据源，排除下面这些已经在别处专门覆盖过的子树，剩下的全部纳入本切片：
>
> - `EM_COMPONENT`/`EM`（S6 主表已穷举 `orgAdminActionExclusions` 20 条 + S6 探查过 `monitoring/dashboards`）
> - `SCHEDULE_TASK`（内部任务，S6 主表已覆盖）
> - `DEVICE`、`SCHEDULE_OPTION:timeRange`、`UPLOAD_DRIVERS`（S6 的 DEVICE/SCHEDULE_OPTION 探查已覆盖，且 Issue #75603/#75604 修复后这两项自带更细的 multi-tenant 判断，跟这里的通用 grant/no-grant 场景不是同一回事，不要用同一个 fixture 套）
> - `CHART_TYPE`/`CHART_TYPE_FOLDER`（S8-CHART-TYPE 单独覆盖跨类型层级）
> - `AI_ASSISTANT`、`FREE_FORM_SQL`（已在 S8 主表覆盖）
> - `VIEWSHEET_ACTION`（Bookmark 四项，S8-BOOKMARK 单独覆盖）
>
> 排除之后剩下 **38 个叶子、共 39 个 (资源类型, 资源, action) 组合**（`DASHBOARD:*` 同时声明 READ+WRITE 两个 action，其余叶子都只有 1 个）。实测遍历结果（已用临时 debug 输出核对过，跟下面列的完全一致，不是估算）：
> - `VIEWSHEET_TOOLBAR_ACTION` 11 项：Edit/Email/Export/ExportExpandComponents/Import/PageNavigation/Print/Refresh/Schedule/ScheduleExpandComponents/"Social Sharing"（均 READ）
> - `VIEWSHEET`/`WORKSHEET`（Visual Composer，均 ACCESS）
> - `PHYSICAL_TABLE`（ACCESS）、`MY_DASHBOARDS`（READ）、`PORTAL_REPOSITORY_TREE_DRAG_AND_DROP`（ACCESS）、`MATERIALIZATION`（ACCESS）
> - Portal Tabs 子树：`DASHBOARD:*`（READ+WRITE）、`SCHEDULER:*`（ACCESS）、`PORTAL_TAB:Report`（READ，标签是"Repository"）、`PORTAL_TAB:Data`（ACCESS）——只有这 4 项，不是"若干项"：`PortalThemesManager` 在测试环境里没有持久化过 `portalthemes.xml`，`loadThemes()` 会回退读 `core/src/main/resources/inetsoft/sree/portal/portalthemes.xml` 这个生产默认配置，里面就是 Dashboard/Report/Schedule/Data 四个 tab，没有 "Design"（原设计草稿猜的"~2 项"和"可能有 Design"都不对）
> - `SCHEDULE_OPTION` 除 `timeRange` 外的 4 项：notificationEmail/saveToDisk/emailDelivery/startTime（均 READ）
> - `LOGIN_AS`（ACCESS，遍历前临时把 `login.loginAs` 设成 `"on"` 该节点才会出现，遍历完立即还原，参照 `PermissionMatrixResourcesS4Test` 的 `withAndCondition()` 同款做法）
> - `SHARE` 7 项：email/facebook/googlechat/linkedin/slack/twitter/**link**（均 ACCESS）
> - `PROFILE`/`CROSS_JOIN`/`CREATE_DATA_SOURCE`/`VIEWSHEET_CALCULATED_FIELD`/`WORKSHEET_EXPRESSION_COLUMN`（均 ACCESS）
>
> 数组以后新增/改名条目，测试自动跟上，不需要再手改字符串。
>
> **实现时又踩了两个跟 `getActionTree()` 本身无关、但会挡住测试跑起来的坑**：
> 1. `ComponentAuthorizationService.loadComponents()` 反序列化的 `view-components.json` 是 `web` 模块前端构建生成的资源（`web/target/generated-resources/gulp/...`），不在 `core` 自己的测试 classpath 上，直接 `new` 会因为读到 `null` 输入流而抛异常。这个依赖只会喂给 `EM_COMPONENT` 子树（本切片本来就排除），所以改用 Mockito mock，`getComponentTree()` 返回一个空 `ViewComponent` 占位，不影响遍历结果。
> 2. `PortalThemesManager` 的无参构造器（专门给"非 Spring 环境（测试等）"用的那个）传的 `cluster` 是 `null`；但 `loadThemes()` 一旦落到"读生产默认 XML"这条回退路径，末尾会调用 `save()`，`save()` 需要 `cluster.getLock(...)` 拿分布式锁，`null` 会直接 NPE。改用两参构造器 `new PortalThemesManager(Cluster.getInstance(), DataSpace.getDataSpace())`——`Cluster.getInstance()` 能拿到 `BaseTestConfiguration` 注册的 `MockCluster` bean，不是 `null`。

**S8-SWEEP-GRANT**（`@ParameterizedTest`，两个方法各覆盖全部 39 个组合，一个断言 user0 放行、一个断言 user1 拒绝）：

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 测试状态 | 备注 |
|---|---|---|---|---|---|---|
| user0（持有 `role0`；对遍历到的每个叶子资源，`role0` 都被授予该叶子声明的每个 action） | 见左列（`getActionTree()` 遍历出的全部叶子，见上方排除说明） | 见左列 | 见左列 | ✓（全部 39 条） | `[M8]` `user0_sweepLeafDeclaredAction_allowed_viaRoleGrant` | User → Role → 资源；跟 S6 主表同一个"读生产数据做参数化"模式 |
| user1（不持有 `role0`，同一批已编辑记录，对每个叶子都未被授权） | 同上 | 同上 | 同上 | ✗（全部 39 条） | `[M8]` `user1_sweepLeafDeclaredAction_denied_editedRecordWithoutGrant` | 负路径，证明不是默认放行（这批叶子里有几个类型本身在"无权限默认放行"清单里——`MY_DASHBOARDS`/`PORTAL_REPOSITORY_TREE_DRAG_AND_DROP`/`MATERIALIZATION`/`SCHEDULE_OPTION`/`VIEWSHEET_TOOLBAR_ACTION`/`SHARE`，`PORTAL_TAB:Report` 这一个具体资源也在清单里——但因为权限记录已编辑过 `hasOrgEditedGrantAll=true`，走的是显式匹配分支不是默认放行分支，跟 S6 的 `SCHEDULE_OPTION:timeRange` 默认放行场景不冲突） |

角色/组继承不在这 39 项上重复验证，理由见上方 S8-BOOKMARK-LANES 末尾说明——已经在 `Bookmark` 上代表性证明过，这里再验证一遍不会有新发现。

执行顺序：S6 → S8（主表 → BOOKMARK → CHART-TYPE → SWEEP），低优先级行（no-grant / anonymous）按需补充。区一（S2-S5）见 `permission-matrix-resources.md`。

---

## 附录：能力总览矩阵（用户类型 × Security Action）

> 下表是原始的"用户类型 × Security Action"速查表，用于快速查某个用户类型大致能访问哪些 EM 功能/开关；具体到某个 Action/某条边界规则的精确断言以上面按切片列出的场景表为准，两处如有出入以切片表 + 代码为准。区一（内容访问）的对应总览表见 `permission-matrix-resources.md`。

Action 固定为 ACCESS 或 READ。For Org × 表示 orgAdmin 无法配置、仅 siteAdmin 可访问。

| 功能条目 | ResourceType | action | For Org | siteAdmin | orgAdmin | orgSecAdmin | viewer | no-grant |
|---|---|---|---|---|---|---|---|---|
| **EM 总入口** | EM | ACCESS | — | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Auditing | EM_COMPONENT | ACCESS | √ | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Monitoring/Cache | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Cluster | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Summary | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Log | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Monitoring/Dashboards | EM_COMPONENT | ACCESS | √ | ✓ | ✓ [S6] | ✗ [S6] | ✗ | ✗ |
| → Settings/Content/Drivers | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Content/MV | EM_COMPONENT | ACCESS | √ | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Settings/Presentation/OrgSettings | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Presentation/Themes | EM_COMPONENT | ACCESS | √ | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Settings/Schedule/Settings | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Schedule/Tasks | EM_COMPONENT | ACCESS | √ | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Settings/Security/Providers | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Settings/Security/Actions | EM_COMPONENT | ACCESS | √ | ✓ | ✓ | ✗ | ✗ | ✗ |
| → Settings/General | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| → Notification | EM_COMPONENT | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Portal: Dashboard tab** | DASHBOARD | READ+WRITE | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Portal: Repository tab** | PORTAL_TAB | READ（资源 `Report`） | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Portal: Data tab** | PORTAL_TAB | ACCESS（资源 `Data`） | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Portal: Schedule tab** | SCHEDULER | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Bookmark: Open/Create/Share/ShareToAll** | VIEWSHEET_ACTION | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Dashboard Toolbar**（Edit/Email/Export/ExportExpandComponents/Import/PageNavigation/Print/Refresh/Schedule/ScheduleExpandComponents/Social Sharing，共 11 项） | VIEWSHEET_TOOLBAR_ACTION | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Composer: Viewsheet** | VIEWSHEET | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Composer: Worksheet** | WORKSHEET | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Physical Table** | PHYSICAL_TABLE | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **My Dashboard** | MY_DASHBOARDS | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Portal Repository Tree - Drag and Drop** | PORTAL_REPOSITORY_TREE_DRAG_AND_DROP | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Materialize Assets** | MATERIALIZATION | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Schedule Options**（Notification Email/Save to Disk/Email Delivery/Start Time，共 4 项） | SCHEDULE_OPTION | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **AI Assistant** | AI_ASSISTANT | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Login As** | LOGIN_AS | ACCESS | √ | ✓ | ✓ | ✗ | dep on grant [S8] | ✗ |
| **Social Sharing**（Email/Facebook/Google Chat/LinkedIn/Slack/Twitter/Copy Link，共 7 项，资源分别是 `email`/`facebook`/`googlechat`/`linkedin`/`slack`/`twitter`/`link`） | SHARE | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Upload Drivers** | UPLOAD_DRIVERS | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Edit Mobile Devices** | DEVICE | ACCESS | **×** | ✓ | ✗ [S6]（Issue #75603 已修复） | ✗ | ✗ | ✗ |
| **Time Range** | SCHEDULE_OPTION | READ | **×** | ✓ | ✗ [S6]（Issue #75604 已修复） | ✗ | ✗ | ✗ |
| **Free Form SQL** | FREE_FORM_SQL | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Cross Join** | CROSS_JOIN | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Create New DataSource** | CREATE_DATA_SOURCE | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Edit Dashboard Calculated Fields** | VIEWSHEET_CALCULATED_FIELD | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Edit Worksheet Expression Columns** | WORKSHEET_EXPRESSION_COLUMN | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Profile** | PROFILE | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |

`dep on grant` = 取决于 Security Actions 中是否为该用户/组配置了该功能开关；默认关闭则 ✗。
