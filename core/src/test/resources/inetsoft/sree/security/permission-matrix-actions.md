# Permission Test Matrix — 区二：Security Action 权限（Actions）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 1 覆盖范围：** `MultiTenantIsolationTest`（场景 13–18B）、`PermissionHierarchyTest`（场景 19–20）
**Phase 2 M8 实现：** `PermissionMatrixActionsTest`（S6-S8）
**姊妹文档：** 区一内容访问权限矩阵（S2-S5，按切片拆成 `PermissionMatrixResourcesS2Test`/`S3Test`/`S4Test`/`S5Test`）见 `permission-matrix-resources.md`

图例：✓ = allowed　✗ = denied　— = n/a　`[P1]` = Phase 1 已覆盖　`[待补]` = 已在设计文档确认、尚未落到测试代码

下方按切片（Slice）列出具体场景，每一行大致对应 `PermissionMatrixActionsTest` 里的一个 `MatrixTestCase`（或一个独立 `@Test` 方法），方便实现前逐条 review。资源路径为 fixture 示例路径（与 Phase 2 计划 Task 6 的常量一致），不是生产环境真实路径。总览矩阵（按 Security Action 归类的能力速查表）见文末附录。

---

> siteAdmin/orgAdmin 在区一（`permission-matrix-resources.md`）里已作为对照身份出现过；本文档的 S6/S7 是它们在 Security Action 边界上的对照场景。

## S6 — For-Org-× 边界（siteAdmin 允许，orgAdmin 拒绝）

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

## S7 — For-Org-√ 边界（orgAdmin 允许，orgSecurityAdmin 拒绝——无 EM 访问权）

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| orgAdmin | EM_COMPONENT | `monitoring/dashboards` | ACCESS | ✓ | |
| orgSecurityAdmin | EM_COMPONENT | `monitoring/dashboards` | ACCESS | ✗ | 无 EM 访问权（只管安全身份） |
| orgAdmin | EM_COMPONENT | `settings/security/actions` | ACCESS | ✓ | |
| orgSecurityAdmin | EM_COMPONENT | `settings/security/actions` | ACCESS | ✗ | 同上 |
| orgAdmin | LOGIN_AS | `*` | ACCESS | ✓ | |
| orgAdmin | EM_COMPONENT | `settings/schedule/tasks` | ACCESS | ✓ | |

## S8 — 普通用户功能开关（取决于是否显式 grant）

| 用户类型 | 资源类型 | 资源 | Action | 预期 | 备注 |
|---|---|---|---|---|---|
| viewer(有 grant，role 上有 AI_ASSISTANT ACCESS) | AI_ASSISTANT | `*` | ACCESS | ✓ | |
| viewer(无 grant) | AI_ASSISTANT | `*` | ACCESS | ✗ | |
| viewer(无 grant) | FREE_FORM_SQL | `*` | ACCESS | ✗ | |
| siteAdmin | FREE_FORM_SQL | `*` | ACCESS | ✓ | siteAdmin 恒放行，不受 dep-on-grant 限制 |

执行顺序：S6 → S7 → S8，低优先级行（no-grant / anonymous）按需补充。区一（S2-S5）见 `permission-matrix-resources.md`。

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
