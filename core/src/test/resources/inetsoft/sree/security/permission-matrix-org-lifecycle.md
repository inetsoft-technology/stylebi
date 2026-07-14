# Permission Test Matrix — 区五：组织生命周期操作

**关联规格：** `docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md`（"区五：组织生命周期操作"一节只保留机制摘要 + 指向本文档，详细场景表以本文档为准）
**Phase：** Phase 2 结束后新增，2026-07-10 收尾排查 #4 追加，不属于原始 Phase 2 范围
**执行明细：** `docs/superpowers/plans/2026-06-30-permission-test-phase2.md` Task 10
**姊妹文档：** 区一 `permission-matrix-resources.md`、区二 `permission-matrix-actions.md`、区三/区四 `permission-matrix-special.md`

**"测试状态"列取值**（与姊妹文档一致）：
- `[已落地]` = 已实现并通过，后面附对应 `@Test` 方法名
- `[待补]` = 已在本文档确认场景与预期，尚未落到测试代码
- `[待确认]` = 预期方向本身尚待产品/业务确认，测试只记录当前行为，不假定对错
- `[不补]` = 已评估过是否要补，结论是不补，后面附理由

> ✅ **场景 5 的"数据损坏"结论已撤回，确认不是产品缺陷**：最初以为复制组织（`copyOrganization(replace=false)`）会把源组织自己的内容权限授权就地损坏，用户在真实前端反复验证后无法重现——clone 前后源组织的用户始终能正常访问被授权的资源。深挖后确认根因是**测试基础设施的假象**，不是产品缺陷：`FileAuthorizationProvider` 的存储读写走的是 `Cluster.getReplicatedMap()`，生产环境背后是真正的 `IgniteCache`，Ignite 默认 `copyOnRead=true`（本项目 `IgniteCluster.getCacheConfiguration()` 也没有把它关掉）——每次 `get()` 都拿到一份反序列化出来的独立拷贝，改这份拷贝不会影响存储里已经落盘的那份。而测试用的 `MockCluster` 背后就是一个裸 `ConcurrentHashMap`，`get()` 返回的是存进去时的原始引用，没有这层保护，所以在测试里"改新组织那份权限对象"会连带把"源组织那份"也改坏——这在生产环境不会发生。详见下方场景 5 和"测试方法论"小节。

---

## 范围边界（已与产品确认）

本文档只覆盖**权限授权层**——某个操作前后，"谁对什么资源有什么权限"是否正确。

**明确排除、列为代办事项**（权限层完成后再评估是否要做、由谁做）：
- 改名/复制后，组织的全部 Settings 和资源（dashboard、MV、schedule task、autosave、data space 文件等）是否丢失——超出权限授权范围。
- 改名场景下资产依赖关系重写的正确性（`DependencyStorageService.copyStorageData()` 复用了跟普通资产改名同一套 `RenameTransformObject`/`DependenciesInfo`/`AssetEntry.cloneAssetEntry()` 数据结构，是好消息，但没有端到端验证）——同样超出权限范围。
- **主题（Theme）"当前生效主题"状态在克隆/改名时的同步问题**，见下方"已知问题"小节——同属 Settings 不丢失范畴，超出权限范围，本阶段不修不测，先记录根因，排期由产品决定。

---

## 已知问题（仅记录，权限层范围外——2026-07-13 实测反馈追加）

用户在真实前端反复克隆组织后观察到"主题相关的设置经常不对"。深挖代码后确认根因，**不是权限授权层的问题**，是组织"当前生效主题"这一状态在克隆/改名时三处状态没有同步好，落在上面"范围边界"里已经列为代办的"Settings 是否丢失"范畴内。记录于此，留给后续排期决定是否修、什么时候修。

**背景：一个组织"当前用哪个主题"由三份独立状态共同决定**：
1. `CustomThemesManager` 的 SreeEnv 属性 `portal.org.selectedThemeId.<orgId>`（`setOrgSelectedTheme()` 写入）
2. 主题对象自己的 `CustomTheme.organizations` 列表
3. `Organization.theme` 字段本身（`FSOrganization.theme`）

**根因**：`AbstractEditableAuthenticationProvider.copyThemes()`（`community/core/src/main/java/inetsoft/sree/security/AbstractEditableAuthenticationProvider.java:304-428`）只在"组织自己名下的自定义主题"分支（第 338-397 行，`theme.getOrgID().equals(fromOrgId)`）把这三份状态都同步了——把新克隆主题的 id 存进局部变量 `newOrgThemeId`（第 371 行），再由调用方 `copyOrganizationInternal()`（第 251 行）写进 `newOrg.setTheme(newOrgThemeId)`。

但另一个分支——"全局主题被某个组织设成 Default for This Organization"（第 398-415 行，`theme.getOrgID()==null && theme.getOrganizations().contains(fromOrgId)`，这是 `ThemeService.java`（enterprise）里 `defaultThemeOrg=true` 的正常产品路径，不区分主题本身是否 `global`）——只同步了状态 1、2，`newOrgThemeId` 在这个分支里从未被赋值。结果：源组织的默认主题若是一个全局主题，克隆/改名出来的新组织，`Organization.theme` 字段是空的（状态 3 没同步，状态 1/2 同步了，三者不一致）。这个逻辑不区分 `replace`，所以改名（org ID rename）场景同样受影响。

**为什么表现为"权限不对"而不是每次必现**：`Organization.theme` 字段在好几处被直接读取，大部分都有 fallback：
- `GlobalStyleController.java:112-127`、`HomePageController.java:87-102`、`AdminPageController.java:85-105`——都是 `xxx || customThemesManager.isCustomThemeApplied()/isEMDarkTheme()` 这种写法，`Organization.theme` 读到空时会摔进 `customThemesManager.getSelectedTheme(user)` 这条完整的解析链（该链会正确读到状态 1/2），实际访问页面时大概率还是能"侧漏对"，不容易被发现。
- **唯一没有 fallback 的地方**：`UserTreeService.java:1078-1089`——给 EM"编辑组织"对话框拼 `EditOrganizationPaneModel.theme` 字段的逻辑是纯 for 循环匹配 `organization.getTheme()`，匹配不到就是 `null`。**克隆出来的组织，只要源组织当时选的是全局主题（而不是组织自己名下的自定义主题），EM 里编辑这个新组织时主题下拉框会显示"未设置/default"**，即便实际访问该组织页面时 CSS 用的主题是对的——这个不一致大概率就是实测看到的"clone 之后主题/设置不对"的现象。附带风险：如果管理员在这个"看起来没选主题"的对话框里保存，可能会把组织实际生效的主题选择清掉（`themeID=null` 保存回去）。

**范围判断**：这属于"组织的 Settings 状态在克隆/改名时是否正确同步"，跟本文档测的"谁对什么资源有什么权限"是两个维度，按已确认的范围边界不在本阶段修/测范围内。如果后续要修，思路是在 `copyThemes()` 第 398-415 行分支里也把 `newOrgThemeId` 赋值成该全局主题的 id（不需要新建 clone，直接引用原主题 id 即可，因为这个分支本来就没有克隆新主题对象）。

---

## 产品规则（原始来源，逐条列出）

以下规则由产品/业务方提出，按操作分组；每条规则附核实结论、状态、对应场景编号。

### 删除

| 规则 | 内容 | 结论 |
|---|---|---|
| — | 删除 orgA，只清 orgA 的资源权限，不影响其他组织 | ✅ 已验证 → 场景 1 |

### 改名（Rename）

| 规则 | 内容 | 结论 |
|---|---|---|
| Rule 1 | 改 org ID 和改用户名是两套不同逻辑，要分开测 | ✅ 已确认——不同代码路径（`Identity.ORGANIZATION` vs `Identity.USER` 分支）→ 场景 2（org ID）+ 场景 3（用户名） |
| Rule 2 | 改完 org ID / 用户名之后，组织的所有 Settings 和资源不能丢 | 📌 范围决定：本文档只测权限这一层（场景 2/3 已覆盖权限部分）；"全部 Settings/资源不丢"超出权限范围，列为**代办**，本阶段不做，见上方"范围边界" |
| Note 1 | 组织内再检查一下 rename resource 的 dependency，确保跟之前的 rename dependency 逻辑正常工作 | 📌 同 Rule 2，列为**代办**，不在权限层范围内（已核实机制上复用同一套 `RenameTransformObject`/`DependenciesInfo`/`AssetEntry.cloneAssetEntry()`，是好消息，但没有端到端验证） |
| Note 2 | 改名再改回去（A→B→A），看资源情况（round-trip） | ✅ 权限层已验证一致、无孤儿 key → 场景 8（资源/Settings 层面的 round-trip 仍是代办，见"范围边界"） |
| （衍生，非产品原始规则，核实过程中发现）| 改名场景下，若身份持有全局 Administrator 角色，改名后是否还保留 | ✅ **已通过真实产品测试确认**——org ID 改名和用户名改名都会**保留**全局 Administrator 角色，这是期望行为 → 场景 7a/7b |

### 合并（Merge）

| 规则 | 内容 | 结论 |
|---|---|---|
| — | 输入一个已存在的 org ID 去改名（=合并），前端报错"An Organization with that ID already exists!"，rename 失败，不产生任何副作用 | ✅ 已核实——后端 `UserTreeService.editOrganization()` 的 `checkDuplicateOrgIDs()` 在任何权限逻辑之前就会拒绝，"合并进已存在组织"在真实路径上不可达 → 场景 4（验证拒绝 + 无副作用，待实现） |

### 复制（Copy / Clone）

| 规则 | 内容 | 结论 |
|---|---|---|
| Rule 1 | 克隆一个组织：所有 Identity 配置、settings、权限 + 所有资源和资产 + 所有（org scoped）presentation settings 都要复制 | ✅ **权限这部分已验证，符合预期**——新组织拿到一份独立的等价授权，源组织自己的授权不受影响（详见上方 callout 和场景 5）。资源/settings 部分超出权限范围，列为代办 |
| Rule 2 | 如果密码被复制，默认密码是 `success123` | ✅ 已跟产品确认，现状正确——密码必须管理员手动输入（必填 + 复杂度校验），代码里没有、也不需要硬编码默认值；测试用示例密码即可，不验证"默认值等于某字符串" |
| Rule 3 | 普通组织一般没有 administrator role 的用户；如果克隆 host org，默认会取消 administrator role 及其继承关系，特殊场景需要再手动设置 | ✅ 已验证——`copyIdentityRoles()` 会跳过全局 `Administrator` 角色成员关系，**但不区分是否是 host-org**，任何组织被复制都会剥离——复制场景符合 Rule 3 意图 → 场景 6 |

---

## 机制梳理

`AbstractEditableAuthenticationProvider` 用同一个 `copyOrganization(...)` 方法承载三种不同语义的操作，只靠 `replace` 参数和"目标 orgId 之前是否已存在"两个维度区分：

| 操作 | 调用方 | `replace` | 目标 orgId 之前是否存在 | 权限清理/迁移机制 |
|---|---|---|---|---|
| 删除 | `IdentityService`（`identity.getType()==ORGANIZATION && oID==null` 分支） | — | — | `AuthorizationChain.cleanOrganizationFromPermissions(orgId)`：按 `resourceOrgID` 精确匹配删除该组织全部权限记录 |
| 改名（org ID） | `UserTreeService.editOrganization()` → `IdentityService.setIdentity()` → `setOrganizationInfo()` → `syncIdentity()` → `copyOrganization(..., replace=true)` | `true` | 否——产品在这条路径上强制保证，见下方"合并" | `IdentityService.updateIdentityPermissions(Identity.ORGANIZATION, fromOrgIdentity, toOrgIdentity, fromOrgId, toOrgId, true)`：把源 org 的权限整体迁移到新 orgId 的 key 下，源 key 删除；身份的全局 Administrator 角色会保留（场景 7a，已通过真实产品测试确认） |
| 改名（用户名，同一 org 内） | `UserTreeService`（改用户名的方法）→ `IdentityService.setIdentity()`（`identity.getType()==USER` 分支） | — | — | `IdentityService.updateIdentityPermissions(Identity.USER, oldUserID, newUserID, orgId, orgId, true)`——orgId 前后不变，只搬用户名，跟改 org ID 是完全不同的两条代码路径（产品 Rule 1：两者分开测） |
| 合并（结论已修正，见下方） | 同"改名（org ID）" | `true` | 是 | **在真实调用路径上不可达**——`UserTreeService.editOrganization()` 在任何权限/复制逻辑之前调用 `checkDuplicateOrgIDs()`，命中已存在的 orgId 直接 `throw MessageException("em.duplicateOrganizationID")`，方法立即中断，不会执行到 `copyOrganization()` |
| 复制 | `UserTreeService`（EM"新建组织→从已有组织克隆"操作）→ `copyOrganization(..., replace=false)` | `false` | 否 | `copyUserToOrganization`/`copyGroupToOrganization`/`copyRoleToOrganization` 逐个复制身份及其权限；`copyIdentityRoles()` 会跳过全局 `isSystemAdministratorRole()` 角色成员关系 |

### 关于"合并"的结论修正

最初把"合并时目标组织已有权限被覆盖丢失"当作一个已确认的真实缺陷，这是错的。已核实 `UserTreeService.editOrganization()` 会在调用 `identityService.setIdentity()`（进而触发 `copyOrganization()`）**之前**先跑 `checkDuplicateOrgIDs()`（第 1248-1249 行），新 ID 命中任何已存在的组织都会直接抛异常、整个方法中断，前端表现为"An Organization with that ID already exists!"、rename 失败、不产生任何副作用。产品里**不存在"合并进已存在组织"这个可达路径**——`copyOrganization(..., replace=true)` 在真实调用链上只会用于"改名到一个全新的、之前不存在的 ID"。

此前的探测测试是直接绕过 `UserTreeService`、裸调内部方法 `IdentityService.updateIdentityPermissions()` 观察到的覆盖行为，属于内部方法在非法输入下的行为，不代表产品有这个缺陷。对应测试已重新定位为场景 4（见下方），断言方向从"发现真实缺陷"改为"验证前置校验会拒绝并且不产生副作用"。

### Rule 2 / Rule 3 核实结论（与产品讨论后）

- **复制默认密码**：产品确认现状正确——"复制"时密码必须由管理员手动输入（`create-organization-dialog.component.ts` 的 `passwordControl` 为必填 + 复杂度校验），代码里不存在、也不需要硬编码的默认密码（如 `success123`）。测试用一个符合复杂度规则的示例密码作为输入即可，不是要验证"默认值等于某个字符串"。
- **Administrator 角色继承剥离的适用范围**：`copyIdentityRoles()` 对"复制"路径会剥离全局 `Administrator` 角色的成员关系，符合 Rule 3 的意图（场景 6）。**改名路径不会剥离，保留该角色，已通过真实产品测试确认为期望行为**（场景 7a/7b）。

### 补充说明：host-org 改名限制（针对场景 7a）

`UserTreeService.editOrganization()` 靠 `em.security.writeDefaultOrgId` 挡掉了 host-org 自己 ID 的改名，在 `checkDuplicateOrgIDs()` 之前就会拒绝，所以 org ID 改名只会落在非 host-org 上。这跟场景 7a"改名保留 Administrator 角色"的结论是两件独立的事，不影响该结论。

---

## 场景清单（权限层）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 1 | 删除 orgA：orgA 的资源权限被清除，orgB 的权限不受影响 | 清除 / 不受影响 | `[已落地]` `delete_cleanOrganizationFromPermissions_removesOnlyTargetOrgGrants` |
| 2 | 改名 org ID：fromOrg → 全新 toOrgId（之前不存在）：fromOrg 的资源权限迁移到 toOrgId 的 key，fromOrgId 旧 key 被清除，无孤儿数据 | 迁移成功，无孤儿 | `[已落地]` `renameOrgId_updateIdentityPermissions_migratesGrantToTargetOrgAndRemovesSourceKey` |
| 3 | 改名用户名（同一 org 内）：alice → bob，orgId 不变：alice 的资源权限迁移到 bob 名下，alice 的旧授权被清除 | 授权对象在**同一个 key** 内把 grantee 从 alice 换成 bob（不像场景 2 那样迁移 key——同 org 不涉及 key 变化） | `[已落地]` `renameUsername_updateIdentityPermissions_renamesGranteeWithinSameOrgKey` |
| 4 | 尝试把 orgA 改名成一个已存在的 orgId（即"合并"）：验证前置校验拒绝且无副作用 | 拒绝 + 双方权限均不受影响 | `[已落地]` `renameOrgToExistingId_checkDuplicateOrgIDs_rejectsAndLeavesPermissionsUnchanged`——没有驱动完整的 `editOrganization()`（依赖较重：`SystemAdminService`/`AuthenticationProviderService` 等），而是用 `ReflectionTestUtils.invokeMethod()` 直调 `private` 方法 `checkDuplicateOrgIDs()`（跟 `permission-matrix-special.md` 里 `checkLoginAs()` 的探测方式同构），因为这个方法本身就是让"合并"不可达的那道闸门 |
| 5 | 复制 orgA → 全新 org（`replace=false`）：新 org 的资源权限是否正确继承自 orgA | orgA 保留自己的权限，新 org 拿到一份独立的等价授权 | `[已落地]` `copy_replaceFalse_contentPermissionGrantIsCopiedToNewOrg`——结论：**符合预期，确认不是产品缺陷**。驱动真实入口 `AbstractEditableAuthenticationProvider.copyOrganization(replace=false)`（不是挑权限相关的子方法单独调，因为这个场景问的正是"整条路径是否保留权限"）。测试类的 Spring 配置里用 `CopyOnReadClusterConfig` 覆盖了共享的 `MockCluster` bean（只在这一个测试类的上下文里生效，不影响其他测试），模拟生产环境 `IgniteCache` 默认的 `copyOnRead=true` 语义；改用这个更贴近生产的 Cluster 实现后，断言的是真实、正确的行为：新组织的 key 拿到一份独立的 `viewer READ` 授权，源组织自己 key 下的授权原样保留、不受影响。最初用共享的 `MockCluster`（裸 `ConcurrentHashMap`，`get()` 返回原始引用，没有 copy-on-read 保护）时误判成"源组织权限被就地损坏"，详见上方 callout 和"测试方法论"小节 |
| 6 | 复制场景下，orgA 的某个用户被手动赋予了全局 Administrator 角色：克隆后该用户在新 org 里不应保留这层角色关系 | 剥离 | `[已落地]` `copy_userWithGlobalAdministratorRole_copyIdentityRoles_stripsGlobalRoleButKeepsOrgScopedRoles`——直接反射调用私有方法 `copyIdentityRoles()`（不经过完整的 `copyOrganization()`/`copyUserToOrganization()`），因为该方法本身就是 Rule 3 描述的机制（跟场景 4 直调 `checkDuplicateOrgIDs()` 同构）。用一个只存在于内存里的 `FSUser`（角色数组 = 一个 org-scoped `viewer` + 全局 `Administrator`）驱动，断言返回的新角色数组：不含全局 `Administrator`（剥离），但含重新定位到新 org 的 `viewer`（普通角色正常保留，不是整体清空）。全局 `Administrator` 角色本身依赖 `FileAuthenticationProvider` 的一次性启动引导（`LoadRolesTask`），测试里用一条 `isSystemAdministratorRole()` 前置断言显式核实其存在，不隐式假设 |
| 7a | 改名 org ID 场景下，源身份若持有全局 Administrator 角色：改名后是否仍保留 | **保留**——已通过真实产品测试确认为期望行为 | `[不补]`——直接反射调用 `copyUserToOrganization()`/`copyIdentityRoles()`（跟场景 6 同样的探测方式）会剥离，跟真实改名全链路的结果不符：真实改名走 `IdentityService.setOrganizationInfo()` → `syncIdentity()` → `AbstractEditableAuthenticationProvider.copyOrganization(..., replace=true)`，依赖真实的 `IdentityThemeService`/`DashboardRegistryManager`/`DataCycleManager` 等协作对象，还原完整调用链的成本跟这一条断言不成比例，不做自动化，以产品实测结论为准 |
| 7b | 改名用户名场景下（同一 org 内），源身份若持有全局 Administrator 角色：改名后是否仍保留 | 保留（不剥离） | `[已落地]` `renameUsername_setUser_currentBehaviorPreservesGlobalAdministratorRole`。用户名改名走 `IdentityService.setUserInfo()`/`syncIdentity()` → `FileAuthenticationProvider.setUser()`，全程不经过 `copyIdentityRoles()`，角色数组原样落盘，跟场景 7a 的结论一致——改名场景保留全局 Administrator 角色 |
| 8 | 改名 org ID 后再改回原 ID（round-trip）：权限最终状态应跟改名前一致，不产生孤儿 key 或重复数据 | 一致 / 无孤儿 | `[已落地]` `renameOrgIdRoundTrip_updateIdentityPermissions_leavesStateIdenticalToBeforeTheRoundTrip`——A→B→A 两次调用 `IdentityService.updateIdentityPermissions()`，断言：中途 B 的 key 存在且 A 的 key 消失，round-trip 后 A 的 key 恢复且 B 无孤儿 key，最终 grantee 集合跟改名前完全相等。踩坑记录见下方"测试方法论"小节新增的一条 |

**测试文件（单文件，不按场景拆多个类，跟区三/四同构）：** `community/core/src/test/java/inetsoft/sree/security/PermissionMatrixOrgLifecycleTest.java`

---

## 测试方法论：MockCluster 与生产环境 copy-on-read 语义的差异

`FileAuthorizationProvider`（以及其他基于 `KeyValueStorage` 的存储）读写最终落在 `Cluster.getReplicatedMap()` 上。生产环境背后是真正的 `IgniteCache`，Ignite 默认 `copyOnRead=true`——每次 `get()` 都返回一份反序列化出来的独立拷贝，调用方拿到手之后随便改都不会影响存储里已经落盘的那份数据。这是 Ignite 故意做的默认保护。

测试环境用的 `MockCluster.getReplicatedMap()` 背后就是一个裸的 `ConcurrentHashMap`，`get()` 返回的是存进去时的**原始 Java 对象引用**，没有这层保护。任何生产代码里"先 `getPermissions()` 拿出来改一改、再存到另一个 key 下"这种模式（`IdentityService.updateIdentityPermissions()` 就是这么写的），在 `MockCluster` 下会连带把原 key 的数据也改坏，但在生产环境完全不会发生——这正是场景 5 一开始被误判成"数据损坏"的根因。

**后续写类似测试时的注意点**：如果测试场景涉及"从存储读出一个对象、修改后写回另一个 key"这种模式，不能直接信任 `MockCluster` 下观察到的结果——需要像 `PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig` 那样，在**该测试类自己的** `@ContextConfiguration` 里覆盖一份带 copy-on-read 语义的 `Cluster` bean（不要改动共享的 `MockCluster` 类本身，避免影响其他测试），确保观察到的行为跟生产一致。

## 测试方法论：`updateIdentityPermissions()` 的组织重命名，`oldOrgId` 对应的 `Organization` 必须是真实存在的记录

写场景 8（round-trip）时踩到的坑：`IdentityService.updateIdentityPermissions(Identity.ORGANIZATION, ...)` 内部先用 `provider.getOrganization(oldOrgId)` 把 `oldOrgId` 解析成一个真实的 `Organization` 对象，再把这个对象（不是原始 orgId 字符串）传给 `Permission.getOrgScopedRoleGrants(action, Organization)`。

如果 `oldOrgId` 对应的组织**在存储里根本不存在**（`getOrganization()` 返回 `null`），`getOrgScopedRoleGrants(action, Organization)` 会**静默地把查找范围退化成"global 组织"的授权范围**（`Organization organization` 参数为 `null` 时，方法内部 `thisOrgID = organization == null ? globalOrgId : ...`），而不是抛异常或者查 `oldOrgId` 本身范围的授权——这一跳会找不到任何东西迁移，看起来就像"权限凭空消失了"。

场景 8 第一版就是这样踩坑的：A→B 的第一跳没问题（orgA 在 builder 里创建过，是真实记录），但 B→A 第二跳时 orgB 从未被创建成真实的 `Organization`（测试只是把 orgB 当一个字符串传进 `updateIdentityPermissions`），于是第二跳的 `oldOrg` 解析成 `null`，退化成 global 范围查找，migrate 不到任何东西，round-trip 后 A 的授权凭空消失。

**真实产品流程里这个坑不会出现**，因为 `copyOrganizationInternal()`（无论 copy 还是 rename）会在做任何权限迁移之前，先用 `addOrganization(newOrg)` 把目标组织创建成真实记录——所以第二次改名发生时，无论是 A 还是 B，永远是"当前存在的那个真实组织"在被改名。

**后续写涉及"改名/迁移到另一个 org"的测试时的注意点**：如果测试绕过完整的 `copyOrganizationInternal()`、直接调 `updateIdentityPermissions()`（跟场景 2/3/8 一样，为了避免驱动过重的依赖），必须像场景 2 那样，把迁移路径上涉及的**每一个** orgId 都提前通过 `SecurityTestDataBuilder.addOrg()` 创建成真实记录——即使某个 orgId 只是"中间态"（比如 round-trip 里的 B），也不能省略。
