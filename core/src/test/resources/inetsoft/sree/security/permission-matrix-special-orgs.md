# Permission Test Matrix — 区四：特殊组织默认行为（host-org / SELF）

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`
**Phase 2 M9 实现：** `MultiTenantTestFixture` 内置 org 扩展（Task 7）+ `SpecialOrgDefaultsTest`（Task 8），均未开工，见 `2026-06-30-permission-test-phase2.md`。
**姊妹文档：** 区一 `permission-matrix-resources.md`、区二 `permission-matrix-actions.md`。区三（认证上下文/账号状态通路测试）原"特殊认证路径"场景里的 org self-signup 部分已拆出，并入本文档——因为 self-signup 落地哪个组织、该组织有什么默认权限，是同一个机制的两个角度；区三保留的 Google SSO / 账号非活跃状态 / Login As 三项跟本文档无关，继续留在架构设计文档里。

图例：✓ = allowed　✗ = denied　— = n/a

**"测试状态"列取值**（与 `permission-matrix-resources.md`/`permission-matrix-actions.md` 一致）：
- `[M9]` = 已落地并通过，后面附对应 `@Test` 方法名
- `[M9⚠️]` = 已落地，但实测结果与预期不符，测试已 `@Disabled` 并注明原因
- `[待补]` = 已在设计文档确认、尚未落到测试代码，后面附未落地的具体原因

本文档覆盖两个内置组织（`Organization.getDefaultOrganizationID()` = `host-org`，`Organization.getSelfOrganizationID()` = `SELF`，全局唯一、供 self-signup 用户共用）相对普通创建 org 的默认行为差异。均未开工，测试状态统一为 `[待补]`（Task 7 的 fixture 扩展尚未落地）。

---

## 场景表

| 场景 | 关联机制 | 触发条件 | 核心断言 | 测试状态 |
|---|---|---|---|---|
| self-signup org 落地（多租户） | `UserSignupService.java:169-170`：`SUtil.isMultiTenant() ? SELF : host-org` | 多租户模式下完成 self-signup | 新用户的 `IdentityID.orgID` = `Organization.getSelfOrganizationID()`；角色映射正确落地 | `[待补]` |
| self-signup org 落地（非多租户） | 同上 | 非多租户模式下完成 self-signup | 新用户的 `IdentityID.orgID` = `Organization.getDefaultOrganizationID()`；角色映射正确落地 | `[待补]` |
| SELF 默认权限清单 | `FileAuthorizationProvider.addDefaultPermissionForSelfOrg()` L416-436 | SELF org 用户，未经任何显式 `grantPermission` | 默认放行 `CREATE_DATA_SOURCE:*`、`PHYSICAL_TABLE:*`、`CROSS_JOIN:*`、`FREE_FORM_SQL:*`、`PORTAL_TAB:Data`、`REPORT:/`、`ASSET:/`；普通创建 org 同样未配置时**没有**这些默认授权（对照） | `[待补]` |
| SELF `DATA_SOURCE_FOLDER` 回退不对称 | `SecurityEngine.checkPermission()` L1086-1092，`isSelfAndNotAdmin` 分支 | SELF 非 admin 用户，`DATA_SOURCE_FOLDER` 未配置权限 | 默认 READ → ✗；普通创建 org 非 admin 用户同样场景默认 READ → ✓（对照） | `[待补]` |
| host-org CHART_TYPE/SHARE 全局默认可见性 | `DefaultCheckPermissionStrategy.isOpeningShareGlobalAsset()` L511-517 | 当前 org = `host-org`，且 `SUtil.isDefaultVSGloballyVisible()` 为真 | `CHART_TYPE`/`SHARE` 的默认可见性判断跨 org 生效；普通创建 org 下同样条件不生效（对照） | `[待补]` |

---

## 附注

- SELF 和 host-org 都会在 `FileAuthorizationProvider.LoadPermissionsTask.initialize()`（L335-341）里各自跑一遍 `addDefaultAdminPermissions()` + `addDefaultRoleGrants()`，两者共享的默认授权路径（`COMPOSER`/`WORKSHEET`/`VIEWSHEET`/`DASHBOARD`/`SCHEDULE_TIME_RANGE`）走的是角色/组织授权（`setRoleGrantsForOrg`/`setOrganizationGrantsForOrg`），不是本文档要覆盖的差异点；本文档只聚焦 SELF **额外**拿到的 `addDefaultPermissionForSelfOrg()` 授权，以及 host-org **专属**的 `isOpeningShareGlobalAsset()` 机制。
- `SecurityTestDataBuilder`/`MultiTenantTestFixture` 目前的 `addOrg(name, id)` 是任意创建 org 的通用方法；Task 7 需要新增的是"直接引用两个内置常量 ID（而非新建）"的支持，因为 `host-org`/`SELF` 在 `FileAuthenticationProvider`/`FileAuthorizationProvider` 里是启动时就固定存在的，不能像普通 org 一样任意命名。
