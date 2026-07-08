# Permission Test Matrix — 区二：Security Action 权限（Actions）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）
**Phase 2 M8 实现：** 按切片拆成独立测试类——`PermissionMatrixActionsS6Test`（已落地）/`S8Test`（未开工），跟区一 `PermissionMatrixResourcesS2-5Test` 同构，不使用 `MatrixTestCase` 参数化 DSL，见 `2026-06-30-permission-test-phase2.md` 的设计变更说明。原计划的 S7 已评估后取消，理由见下方 S6 末尾"orgAdmin 角色级联 vs orgSecurityAdmin 权限级联"小节。
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

**`DEVICE`/`SCHEDULE_OPTION`"Time Range" 最小探查（架构设计文档标注 For-Org-×，与 `orgAdminActionExclusions` 实际名单矛盾）：**

> 架构设计文档把 `DEVICE`（Edit Mobile Devices）和 `SCHEDULE_OPTION` 的 "Time Range" 都标注为 For-Org-×，但翻查 `ActionPermissionService.orgAdminActionExclusions` 实际排除名单（`EM_COMPONENT` 16 条 + `SCHEDULE_TASK` 3 条 + `UPLOAD_DRIVERS` 1 条），这两项根本不在里面。进一步查发现两者的"For Org ×"限制其实来自 `ActionPermissionService.getInternalScheduleTasksNode()`/`getScheduleOptionsNode()`/根节点构建方法里各自内联的 `!SUtil.isMultiTenant() || isSiteAdmin(principal)` 判断——这只控制 EM Actions 配置树**要不要把这个节点显示给 orgAdmin 配置**，不会调用 `SecurityEngine.checkPermission()` 走拒绝路径，跟 `orgAdminActionExclusions` 是完全独立的两套机制。
>
> 实测结果：
> - `DEVICE`：orgAdmin 被显式授予 ACCESS 后，即使 mock `isMultiTenant()=true`，`checkPermission()` 依然放行——`[M8]` `orgAdmin_device_allowedByGrant_evenWhenMultiTenant_notInExclusionList`
> - `SCHEDULE_OPTION`（`timeRange`）：`SCHEDULE_OPTION` 类型本身在 `DefaultCheckPermissionStrategy` 的"无权限默认放行"兜底分支里，orgAdmin 完全没配置任何授权、mock `isMultiTenant()=true` 时依然默认放行——`[M8]` `orgAdmin_scheduleOptionTimeRange_allowedByDefault_evenWhenMultiTenant`
>
> **结论：** `DEVICE`/`SCHEDULE_OPTION:timeRange` 的 For-Org-× 只是 EM Actions 配置树里"不给 orgAdmin 看到这个开关"的 UI 层隐藏，`SecurityEngine.checkPermission()` 本身并不拒绝——跟 Issue #75574 里"UI 树隐藏、接口未必真的校验"是同一种反模式。架构设计文档里这两项"For-Org-×"的标注已订正为"仅 UI 隐藏，接口未强制"。
>
> **后续追查确认：两条都是真实、可复现的越权漏洞，已分别报为 Issue #75603（Device）和 Issue #75604（Time Range）：**
> - **Issue #75603**：实际写入设备档案的接口 `DeviceController`（`community/core/src/main/java/inetsoft/web/composer/vs/objects/controller/DeviceController.java`）的 `newDevice`/`editDevice`/`deleteDevice` 三个方法**完全没有 `@Secured`/`@RequiredPermission` 注解，也没有任何 `checkPermission()` 调用**——不只是 orgAdmin，任何登录用户都能直接 POST `/api/composer/device/{new,edit,delete}` 创建/修改/删除设备档案，`DEVICE` 权限只在 `ViewsheetPropertyDialogService` 里算出来喂给 UI 展示用的 `editDevicesAllowed` 布尔值，从未真正接到这几个接口上。
> - **Issue #75604**：`ScheduleTaskService.sanitizeConditions()` 的 `canUseTimeRange` 只调用了 `checkPermission(SCHEDULE_OPTION, "timeRange")`，没有像同一个类里 `createTaskDialogModel()` 算 UI 用的 `timeRangeEnabled` 那样叠加 `!isMultiTenant() || isSiteAdmin(principal)` 判断。因为 `SCHEDULE_OPTION` 未配置时默认放行，orgAdmin 在多租户模式下 `canUseTimeRange` 恒为 `true`，能通过 `/api/em/schedule/task/save`（该接口的 `@Secured` 只要求 `SCHEDULER ACCESS` + `EM_COMPONENT:settings/schedule/tasks ACCESS`，orgAdmin 都能拿到，不涉及 `SCHEDULE_OPTION`）把一个命名时间段塞进任务的时间条件里，即使 UI 从未向这个 orgAdmin 展示过这个选项。
>
> **回归守护测试（均按 `@Disabled` 断言设计意图，不是当前行为，等修复后去掉 `@Disabled`）：**
> - Issue #75603：`community/core/src/test/java/inetsoft/web/composer/vs/objects/controller/DeviceControllerTest.java`（`newEditDeleteDevice_requireDeviceAccessPermission`）——用反射断言三个方法上应该有 `@Secured(@RequiredPermission(resourceType = DEVICE, actions = ACCESS))`，当前没有任何注解，断言失败
> - Issue #75604：`community/core/src/test/java/inetsoft/web/admin/schedule/ScheduleTaskServiceSanitizeTest.java`（`sanitizeConditions_orgAdminMultiTenant_stripsTimeRangeDespiteDefaultAllow`）——mock `isMultiTenant()=true`+`isSiteAdmin()=false`，断言 orgAdmin 提交的 `timeRange` 会被清掉，当前 `sanitizeConditions()` 没有这层判断，断言失败
>
> 顺带发现：`ScheduleTaskServiceSanitizeTest` 整个类此前缺 `@Tag("core")`，按本仓库 `core/pom.xml` 的 surefire `<groups>core</groups>` 配置，这个类原有的 ~22 个测试在 `mvn test` 下从未真正跑过；已补上这个 tag，顺带让这些既有测试也开始生效（跑后全部通过，不是本次改动引入的回归）。

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

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| viewer(有 grant，role 上有 AI_ASSISTANT ACCESS) | AI_ASSISTANT | `*` | ACCESS | ✓ | |
| viewer(无 grant) | AI_ASSISTANT | `*` | ACCESS | ✗ | |
| viewer(无 grant) | FREE_FORM_SQL | `*` | ACCESS | ✗ | |
| siteAdmin | FREE_FORM_SQL | `*` | ACCESS | ✓ | siteAdmin 恒放行，不受 dep-on-grant 限制 |

执行顺序：S6 → S8，低优先级行（no-grant / anonymous）按需补充。区一（S2-S5）见 `permission-matrix-resources.md`。

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
| **Portal: Dashboard tab** | DASHBOARD | READ+WRITE | √ | ✓ | ✓ | — | dep on grant | ✗ |
| **Portal: Repository tab** | PORTAL_TAB | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Portal: Schedule tab** | SCHEDULER | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Bookmark: Open** | VIEWSHEET_ACTION | READ | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Composer: Viewsheet** | VIEWSHEET | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **AI Assistant** | AI_ASSISTANT | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Login As** | LOGIN_AS | ACCESS | √ | ✓ | ✓ | ✗ | dep on grant | ✗ |
| **Upload Drivers** | UPLOAD_DRIVERS | ACCESS | **×** | ✓ | ✗ [S6] | ✗ | ✗ | ✗ |
| **Edit Mobile Devices** | DEVICE | ACCESS | **×**（仅 UI 隐藏，见下方 S6 探查） | ✓ | ✓ [S6] | ✗ | ✗ | ✗ |
| **Time Range** | SCHEDULE_OPTION | READ | **×**（仅 UI 隐藏，见下方 S6 探查） | ✓ | ✓ [S6] | ✗ | ✗ | ✗ |
| **Free Form SQL** | FREE_FORM_SQL | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Cross Join** | CROSS_JOIN | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |
| **Profile** | PROFILE | ACCESS | √ | ✓ | ✓ | — | dep on grant [S8] | ✗ |

`dep on grant` = 取决于 Security Actions 中是否为该用户/组配置了该功能开关；默认关闭则 ✗。
