# 资源层机制梳理表 & 测试矩阵 — 组织生命周期操作（删除 / 改名 / 复制）

**关联计划：** `docs/superpowers/plans/2026-07-14-org-lifecycle-resource-integrity.md`
**姊妹文档（权限层，范围不同）：** `community/core/src/test/resources/inetsoft/sree/security/permission-matrix-org-lifecycle.md`
**机制二架构参考：** `claude/org-migration-content-rewrite.md`
**Phase：** 2026-07-20 按机制重新组织结构——原先按"文档信息类型"（机制表/delete清单/覆盖率/场景/...）组织，现按"机制一/机制二/其他机制"组织，与计划文档的心智模型对齐。

**"测试状态"列取值：**
- `[已落地]` = 已实现并通过，后面附对应 `@Test` 方法名
- `[待补]` = 已确认场景与预期，尚未落到测试代码
- `[待确认]` = 预期方向本身尚待产品/业务确认，测试只记录当前行为，不假定对错

> ⚠️ 本文档很多"预期"是**代码当前行为的如实记录**，不代表已获产品确认"这是我们想要的行为"。标 `[待确认]` 的场景，测试目的是钉住现状、防止意外回归，不是证明行为正确。

---

## 共享背景

以下内容跨越多个机制，是理解下面各章节的前提。判断标准：**删掉这条内容，某个机制小节会读不完整/读不懂吗？会才放这里，否则归位到具体机制/资源小节。**

### 组织生命周期操作总览

三种操作的顶层入口：
- **删除**：`IdentityService.syncIdentity()`，`identity.getType()==ORGANIZATION && oID==null` 分支
- **改名 / 复制**：`AbstractEditableAuthenticationProvider.copyOrganizationInternal(..., boolean replace, ...)`（`replace=true` 改名，`replace=false` 复制）

**入口复杂度**：生产环境从 EM UI 触发的"改名"，实际入口是 `IdentityService.setOrganizationInfo()`（`IdentityService.java:2047-2143`），它自己先做一部分资源迁移（Dashboard admin 级注册表、DataSpace 路径重命名、主题 `organizations` 列表调整），再调用 `syncIdentity()` → `copyOrganizationInternal(replace=true)`。部分资源类型的改名逻辑因此跑了两遍不同实现（见三、3.2 Dashboard）。

**只对 File/Virtual provider 生效，DB/LDAP provider 走不到这条代码（2026-07-28 排查确认）：** `copyOrganizationInternal()` 定义在 `AbstractEditableAuthenticationProvider` 上，全代码库（含 enterprise）只有 `FileAuthenticationProvider`/`VirtualAuthenticationProvider` 实现 `EditableAuthenticationProvider`；`DatabaseAuthenticationProvider`/`LdapAuthenticationProvider`/`GenericLdapAuthenticationProvider` 都是只读的 `AbstractAuthenticationProvider`，没有这些方法。EM 后端多处显式 `instanceof EditableAuthenticationProvider` 拦截（`IdentityService.deleteIdentities()`、`UserTreeService.createOrganization()` 等），provider 不可编辑时直接短路返回警告/`null`——组织增删改在 UI 层面对 DB/LDAP provider 根本不可达，不是"代码里有分支处理得好不好"的问题。两个可编辑 provider 也都没有重写任何 org 生命周期方法，全部继承同一份基类逻辑：**本文档记录的所有机制只有一条代码路径，不随 provider 类型变化。**

### Delete 路径完整调用清单（参考用，各小节引用其中行号，不重复贴代码）

`IdentityService.syncIdentity()`（`:598-630`）：
```
601  dashboardRegistryManager.clear(identityId)              — Dashboard 注册表内存缓存清空          → 三、3.2
607  eprovider.removeOrganization(identityId.orgID)          — 删除 Organization 实体
610  authoc.cleanOrganizationFromPermissions(orgID)          — 权限层（不在本文档范围）
612  dataCycleManager.clearDataCycles(orgID)                 — Data Cycle 清理                       → 三、3.3
613  removeOrgProperties(orgID)                              — SreeEnv 组织专属属性清理
614  removeOrgScopedDataSpaceElements(oOrg)                  — DataSpace 全部 org-scoped 路径删除     → 三、3.2 / 3.5
615  updateRepletRegistry(orgID, null)                       — Replet Registry 显式清空               → 三、3.6
616  themeService.removeTheme(orgID)                         — 组织专属 CustomTheme 清理（★孤儿点）   → 三、3.1
617  themesManager.removeCSSEntry(orgID)                     — Portal CSS 主题条目清理
620  removeStorages(orgID)                                   — 见下方展开
624  repletRegistryManager.clearOrgCache(orgID)               — Replet Registry 内存缓存清空           → 三、3.6
625  logManager.removeOrgLogLevels(orgID)
```

`removeStorages(orgID)`（`:1097-1113`）：
```
1098  removeOldOrgTaskFormScheduleServer(orgID)      — Schedule Server/Client 运行时缓存清理（不碰持久化存储）
1099  dashboardManager.removeDashboardStorage(orgID) — Dashboard 偏好 KeyValueStorage 整桶删除        → 三、3.2
1100  dependencyStorageService.removeDependencyStorage(orgID) — 依赖反向索引整桶删除                  → 一
1101  recycleBin.removeStorage(orgID)                — 回收站整桶删除
1102  indexedStorage.removeStorage(orgID)            — IndexedStorage 整桶删除（含所有 AssetEntry）  → 二
1103  libManagerProvider.getManager(orgID).close()    — Library Manager 关闭（只关句柄，见下方待确认）
1105-1111  removeBlobStorage(__mv/__mvws/__mvBlock/__pdata/__library/__tableCacheStore/__autoSave)   → 三、3.4
```

**待确认**：`libManagerProvider.getManager(orgID).close()`（`:1103`）只调用了 `close()`，是否真的清理了 Library 数据本体没有代码层面确认（`__library`/`__tableCacheStore` 两个 blob 桶另有 `removeBlobStorage` 显式删除，`close()` 更像是防句柄泄漏）——建议测试落地时显式断言，不要假设。

**结论**：绝大多数资源类型在 delete 路径下都有对应清理，跟 rename 分支末尾（`copyOrganizationInternal:280-298`）的源组织清理逻辑高度对称，多处是完全相同的方法调用。

### 机制一 / 机制二的一致性结论

`DependencyStorageService`（机制一）和 `MigrateDocumentTask`（机制二）生成新 key 的逻辑完全一致——都调用同一个 `AssetEntry.cloneAssetEntry(Organization).toIdentifier(true)` 序列，两边索引不会错位，不存在"反向依赖索引记的 key 和资产实际落地 key 对不上"的风险。

### KeyValueStorageManager 的共享 LRU 缓存（跨机制的存储层风险）

`KeyValueStorageManager`（`KeyValueStorageManager.java:42-138`）是整个进程唯一的 `KeyValueStorage` 实例池：`storages` 缓存上限 `MAX_SIZE=50`（Caffeine LRU），依赖反向索引存储、Dashboard 偏好、Autosave 等所有 `KeyValueStorage` 消费者共享同一个缓存实例，驱逐时的 `removalListener` 会调用 `storage.close()`。`get(id, loader)`（`:104-118`）在**重新按 id 查找**时会自愈——发现缓存里的实例已关闭就 evict 掉、重建一个新的——但这只保护"下一次按 id 查找"的调用方，不保护已经拿到引用、还在方法体里继续用这个引用的调用方。`LocalKeyValueStorage.stream()`/`.keys()`（`:136-152`）对已关闭的实例不抛异常，直接返回 `Stream.empty()`。

已用 `OrgLifecycleDependencyMigrationTest#closedStorageReference_streamSilentlyEmpty_insteadOfThrowing`（场景 1g）直接验证这个客户端症状：对一个已获取的 storage 引用调用 `close()`，之后 `stream()` 返回空而不是抛异常。测试证实的是这个机制本身，**没有**（也无法在单线程测试里确定性地）复现"50 个 store 同时活跃触发真实 LRU 驱逐、且驱逐恰好落在某个正在执行的迁移方法的读快照和删除源存储之间"这个完整时序窗口——这需要真实多组织并发的集群场景验证。对机制一的具体影响见下方"已确认的生产风险"。

### 无锁的执行顺序窗口（编排层风险，不属于任何单一存储机制）

`copyOrganizationInternal()` 的 `replace=true` 分支里，`identityService.updateIdentityPermissions(...)`（权限迁移，约 `:155-156`）先于 `identityService.copyStorages(...)`（机制一+机制二的入口，`:256`）执行，中间隔着角色/用户/组复制循环，**整个方法没有锁保护**。并发请求理论上可能读到"权限已指向新组织、资源内容未迁移完"的中间态。窗口存在有代码证据支撑，是否会被生产并发场景实际触发，需产品/运维确认组织级操作是否可能并发执行。

### 哪些资源类型依赖机制二（跨机制依赖关系，双向记录，避免遗漏）

机制二（`BlobIndexedStorage.migrateStorageData()` → `MigrateXxxTask`）不只覆盖 VS/WS/LM/Cube/Bookmark/ScheduleTask 本身，还被部分"其他机制"资源间接依赖：

| 资源类型 | 依赖机制二的哪部分 | 见章节 |
|---|---|---|
| Autosave 嵌入内容修正 | 内嵌的 viewsheet/worksheet 内容用 `MigrateViewsheetTask`/`MigrateWorksheetTask` 重写 | 三、3.4（场景 6c） |
| Data Source / Query / VPM / Partition / Data Model | 走 `BlobIndexedStorage` 通用兜底分支迁移容器 key（不重写内嵌身份字段） | 二（场景 10a-10c） |
| Schedule Task 内容（对 Viewsheet 的引用） | `MigrateScheduleTask` 重写 Action 里的 `viewsheet` 属性 | 二 附近；三、3.3 说明分工 |

不依赖机制二、完全独立的资源类型：主题、Dashboard 偏好设置与注册表、Data Cycle 自身存在性、Task Save 文件、Data Space 文件、Replet Registry。

### 测试方法论

- **Copy-on-read 语义**：任何"读出对象改一改再写别的 key"模式的断言，必须复用 `PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig`/`CopyOnReadDistributedMap`，不能直接信任共享 `MockCluster` 的观察结果。
- **接收 `Organization` 对象的迁移方法（`updateIdentityPermissions()`/`migrateDataCycles()` 等），`oldOrgId` 必须是真实存在的记录**——中间态组织（如 round-trip 测试里的 B）也要用 `SecurityTestDataBuilder.addOrg()` 建成真实记录，否则方法内部会静默退化成 global 范围查找。

---

## 一、机制一：依赖反向索引迁移（`DependencyStorageService`）

**机制说明：** `migrateStorageData(Organization oOrg, Organization nOrg, boolean removeOld)`（`DependencyStorageService.java:127-144`）是 copy/rename 共用入口：`copyStorageData()`（`removeOld=false`）用于 copy，`removeOld=true` 用于 rename。读出 `oStorage.stream()` 里的每条记录，生成新 key，`syncDependencyData()` 改写 `DependenciesInfo.dependencies`/`embedDependencies` 里每个 `AssetObject` 的 org 归属，写回新组织 storage。Spring 单例（`@Service`，包内可见构造函数）。

**测试环境技术结论**（并入自 `2026-07-14-org-lifecycle-resource-integrity.md`，与二"技术前置问题"对齐格式）：
- **Spring bean 装配**：`BaseTestConfiguration` 无组件扫描，测试类需在专属 `@Configuration` 里补 `@Bean`；测试类与 `DependencyStorageService` 同包（`inetsoft.uql.asset.sync`），可直接 `new DependencyStorageService(mgr)`，不需要反射。
- **Fixture 写入**：用 `OrganizationManager.runInOrgScope(fromOrgId, () -> { dependencyStorageService.put(key, info); return null; })`，走公开 API，不需要反射访问私有的 `getDependencyStorage(String)`。`DependenciesInfo` 是无参构造 + `setDependencies(List<AssetObject>)`/`setEmbedDependencies(...)`，测试里可直接照抄生产用法。
- **Copy-on-read 保护**：复用 `PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig`/`CopyOnReadDistributedMap`（已对 `get()`/`entrySet()`/`values()` 做深拷贝，`LocalKeyValueStorage.stream()` 底层就是 `entrySet().stream()`，自动受保护）。
- **清理**：`removeDependencyStorage(orgID)` 已存在，测试 `@AfterEach` 必须显式调用，避免跨测试泄漏。

### Copy 场景

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 1a | `fromOrgId` 播种一条 `DependenciesInfo`，调用 `copyStorageData(fromOrg, toOrg)` | `toOrgId` 出现同名新 key，`AssetObject` org 归属正确；`fromOrgId` 原 key 原样保留（须在 copy-on-read `Cluster` 下断言） | `[已落地]` `copy_seedsTargetOrg_leavesSourceOrgIntact` |

### Rename 场景

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 1b | `migrateStorageData(fromOrg, toOrg, removeOld=true)` | `toOrgId` 拿到迁移数据，`fromOrgId` 的 key 被清除，无孤儿 | `[已落地]` `rename_migratesAndRemovesSource_noOrphan` |

### Delete 场景

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 1c | `removeDependencyStorage(orgId)`（共享背景 delete 清单 `:1100`） | 整桶删除，无残留 | `[已落地]` `delete_removesWholeStorage` |

### 并发/幂等场景（集群场景补充）

`migrateStorageData()` 自身没有锁保护，以下场景针对性覆盖"多人/多请求在集群中并发操作同一个组织"时容易出现的资源乱象：

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 1d | 对同一 fromOrg/toOrg 重复调用一次 `migrateStorageData(...,removeOld=true)`（模拟客户端重试或集群重复请求） | 第二次调用不抛异常，不产生重复/脏数据 | `[已落地]` `duplicateRenameInvocation_secondCallIsNoOpNotError` |
| 1e | 两个不同的源组织先后 `migrateStorageData(...,removeOld=true)` 迁入同一个目标组织 | 目标组织的数据是可加的（两边都在），不会互相覆盖 | `[已落地]` `twoSourceOrgs_migrateIntoSameTarget_mergeAdditively` |
| 1f | 两个线程同时对同一 fromOrg/toOrg 调用 `migrateStorageData(...,removeOld=true)`（无锁下的真实并发冒烟测试，用真实线程池+`CyclicBarrier`触发） | 两边调用都不抛异常；不管两个线程实际交错顺序如何，最终目标组织数据无重复无丢失，源组织彻底清空 | `[已落地]` `concurrentDuplicateRename_noExceptionNoDuplicateNoLoss` |
| 1g | 已获取的 `KeyValueStorage` 引用在使用前被关闭（模拟共享背景"KeyValueStorageManager 的共享 LRU 缓存"里描述的驱逐） | `stream()`/`keys()` 静默返回空而不是抛异常——证实"已确认的生产风险"里静默丢失依赖数据的具体机制 | `[已落地]` `closedStorageReference_streamSilentlyEmpty_insteadOfThrowing` |

#### 已确认的生产风险

- **Ignite `copyOnRead` 的 scan query 语义未确认**：反编译 `ignite-core-2.18.0.jar` 追踪 `IgniteCacheProxyImpl.iterator()` 发现，Ignite 的 `copyOnRead=true` 只确认覆盖 `get()` 语义，`entrySet()`/scan query 路径未能确认是否遵循同一开关。若生产环境的 `cache.iterator()` 实际不受保护，`migrateStorageData()` 原地修改会真的连带改坏源组织未删除的 `DependenciesInfo`——**这将是真实的生产 bug**。测试用的 `CopyOnReadDistributedMap` 无论如何都会通过（总是深拷贝），**测试通过不能作为生产没有这个问题的证据**。需要在真实多节点 Ignite 集群实测，或找了解内部机制的人确认。
  - **补充说明：`copyOnRead` 的 true/false 具体各自意味着什么**：这个配置项控制的是"从缓存读出一个值时，给调用方的是缓存内部实际存的那个 Java 对象引用，还是一份新反序列化出来的独立副本"。`get()` 这条路径已反编译确认：`copyOnRead=true`（本项目实际配置，没有关掉）时，`get(key)` 内部会先反序列化出一份新对象再返回，调用方改这份副本碰不到缓存里真正存的那份；`entrySet()`/scan query（`KeyValueStorage.stream()` 底层走的就是这条）在 Ignite 内部是另一套面向批量遍历/查询游标的实现代码，跟 `get()` 不是同一段逻辑，反编译只在 `get()` 里看到"读出来再拷贝一份"这一步，**没能在这条路径里找到同样的证据**——是"没找到证据证明它做了"，不是"确认了它不做"。分两种情况看对 `migrateStorageData()`（尤其是 `replace=false` 的 copy，源组织理应保持不变）的实际后果：若 `entrySet()` 也遵循同一开关，行为符合预期、无影响；若不遵循、返回的是内部真实引用，`syncDependencyData()` 原地 `setDependencies(...)` 就会把源组织缓存里真正存的 `DependenciesInfo` 直接改成指向新组织——数据被静默污染，且不会有任何报错提示。
  - **修复可行性评估（暂不动手，先记录判断）**：`DependenciesInfo` 已经有一个现成的、真正深拷贝的 `clone()`（`Tool.clone()` → `deepCloneCollection()` 对列表里每个 `AssetEntry` 反射调用它自己的 `public Object clone()`，已确认该方法是 `public` 的，反射能找到，不会静默失效）。只要在 `syncDependencyData()`（以及改用户名走的 `syncDependencyUser()`）开头先 `.clone()` 一份，改这份 clone、返回 clone，源对象就完全不会被碰。影响面很小：只改这 1-2 个私有方法，不改签名、不改返回值类型，调用方无感知，不涉及其它类，回归风险低（用的是已有、被信赖的 `clone()` 实现，不是新写的克隆逻辑）。更重要的是，做了这个修复之后，**不管上面 true/false 哪种情况是生产环境的真实行为都无所谓**——源对象压根不会被碰，风险从根上消失，不再需要"找真实多节点集群实测"或"找懂 Ignite 内部机制的人确认"这个悬而未决的外部依赖。这跟下面两条风险（本质上要不要加锁、牺牲多少性能，属于产品取舍）不是一个量级——这条更像是一个可以直接判定"该修"的正经 bug fix。
- **KeyValueStorageManager 共享 LRU 缓存驱逐可能导致静默丢失整个组织的依赖数据**（机制见共享背景"KeyValueStorageManager 的共享 LRU 缓存"）：`migrateStorageData()`（`DependencyStorageService.java:127-144`）在方法开头各获取一次 `oStorage`/`nStorage` 引用后，整个方法体（读快照 `:131` → `syncDependencyData` → `putAll` → `removeOld` 时的 `removeDependencyStorage` `:142`）都复用这个已经拿到手的引用，不会重新按 id 查找。如果在读快照和删除源存储之间，这个引用被同一进程里其它并发活跃的组织操作挤出了共享的 50 个 store 缓存并被 `close()`，`oStorage.stream()` 会静默返回空——`data` 为空、`putAll` 被跳过，但 `removeOld` 分支照常执行、真实删除源组织的依赖索引整桶。结果是**该组织的依赖反向索引被完全静默清空，没有任何异常或日志**。测试组织数量少，单元测试里不会触发（缓存远没到 50 个），但在大量组织持续活跃、或批量组织操作的生产集群中是真实风险；不纳入 Task A 验收标准（无法在单线程测试里确定性复现这个时序窗口本身），测试报告需注明这个局限。
  - **修复可行性评估（暂不动手，先记录判断）**：有一个影响面很小的局部缓解——把方法体里 `.stream()` 读取前、`removeDependencyStorage()` 删除前，改成各自紧挨着重新调用一次 `getDependencyStorage(id)`，借用 `KeyValueStorageManager.get()`（`:104-118`）已有的"发现引用已关闭就重建"自愈逻辑。这个改动只影响 `DependencyStorageService` 一个方法，不碰 `KeyValueStorage` 接口本身，不影响 Dashboard/Autosave 等其它消费者。但它只是把竞态窗口从"整个方法执行期间"缩小到"重新查找和紧接着那一行代码之间"，**不是从原理上消除**——理论上仍可能撞上，只是概率低到几乎不可能被真正触发。真正堵死这个窗口需要下一条风险里的加锁方案，属于同一个根因。
- **`migrateStorageData()` 方法内部、读快照与删除源存储之间没有锁保护并发写入**（区别于共享背景"无锁的执行顺序窗口"——那是编排层/跨方法级别的风险，这是机制一自己方法内部的风险）：如果在 `oStorage.stream()`（`:131`）取得快照之后、`removeDependencyStorage(oOrg.getId())`（`:142`）真正删除源存储之前，恰好有正常用户操作往源组织写入了一条新的依赖记录（例如同一时刻有人在保存一个新 viewsheet），这条新记录会在快照之后写入、但随后整个源 store 被整桶删除——新写入的依赖记录会被无声丢弃。同样难以在单线程测试里确定性复现，产品/运维需要确认组织改名/删除操作是否会与该组织内的正常资源保存操作并发执行。
  - **修复可行性评估（暂不动手，先记录判断）**：要真正堵住这个洞,需要在 `migrateStorageData()` 执行期间给该 org 的依赖存储加锁，且**同一 org 下所有正常写入路径**（资产保存等）也要遵守同一把锁，否则锁了也白锁。`Cluster`/`DistributedMap` 已经暴露了 `lock(key)` 系列原语（见 `PermissionMatrixOrgLifecycleTest.CopyOnReadDistributedMap` 里透传的 `lock`/`unlock`），原语本身不缺，但牵连面很大：不是改一个方法，而是要在所有资产保存的入口点加锁检查，本质上是把共享背景里"无锁的执行顺序窗口"这个编排层问题从"权限迁移 vs 资源迁移"扩大到"资源迁移 vs 正常资源保存"。是否要为这类罕见并发场景牺牲正常保存操作的响应性（加锁意味着阻塞或报错），是产品/架构层面的取舍，不是局部 bug 修复，需要产品先拍板要不要做，再决定怎么做。

### 测试覆盖

`OrgLifecycleDependencyMigrationTest.java`（`community/core/src/test/java/inetsoft/uql/asset/sync/`）：场景 1a-1g 全部落地，7 个 `@Test` 全部通过。复用 `PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig`（该文件里的 `CopyOnReadClusterConfig`/`CopyOnReadCluster`/`CopyOnReadDistributedMap` 及外层类本身已放开为 `public`，专为跨包复用，不影响该文件其余测试——已重新跑过 `PermissionMatrixOrgLifecycleTest` 全量确认无回归）。

---

## 二、机制二：资产内容本体重写（`BlobIndexedStorage.migrateStorageData()` → `MigrateXxxTask`）

**机制说明：** 完整 Class Map、触发链路见 `claude/org-migration-content-rewrite.md`。关键结论：

- 按 `AssetEntry.Type` 分派 `MigrateViewsheetTask`/`MigrateWorksheetTask`/`MigrateLogicalModelTask`/`MigrateCubeTask`/`MigrateBookmarkTask`/`MigrateScheduleTask`；未匹配类型（`DATA_SOURCE`/`DATA_MODEL`/`VPM`/`PARTITION` 等）落入通用兜底分支，只重写容器 key，不解析对象内部字段。
- `executor.awaitTermination(Integer.MAX_VALUE, SECONDS)` 阻塞等待全部任务完成，**非 fire-and-forget**，`copyOrganizationInternal()` 返回时资产内容确定已迁移完毕。
- Data Source/Query 定义随通用兜底分支一并迁移——worksheet 侧 `SourceInfo`（纯名字引用，不含 orgID）在新组织下能正确解析，不存在"绑定断链"风险。
- Schedule Task 对 Viewsheet 的引用（`MigrateScheduleTask`）已确认覆盖。

**身份字段格式说明（决定要不要重写的根本原因，统一覆盖 10a-10c 与 2h 里所有 `createdBy`/`modifiedBy` 场景）：** Data Source/VPM/Partition/Data Model（通用兜底分支，`BlobIndexedStorage.java:649-676` 只对容器 `AssetEntry` key 做 `cloneAssetEntry`，完全不解析对象内部字段）和 Worksheet/Viewsheet（显式分派类型，`MigrateWorksheetTask`/`MigrateViewsheetTask.processAssemblies()` 里有 `Tool.equals(ouser, getOldName())` 判断）两条路径，在纯组织迁移下都不会重写 `createdBy`/`modifiedBy`——手段不同（一个完全不解析，一个判断条件恰好不满足），结果一致，且**都是正确行为，不是缺陷**：

- 字段本身是纯用户名字符串，从不编码 orgID，读回时靠 `OrganizationManager.getInstance().getCurrentOrgID()` 动态拼接当前组织解析，语义上天然是"当前组织里这个名字对应的人"。
- `MigrateWorksheetTask`/`MigrateViewsheetTask` 的 `(entry, oldUserName, newUserName)` 构造函数是为另一个真实场景设计的——EM 编辑用户改用户名（同组织内改名，`UserTreeService.java:1148` `editUser()` → `:1205` `renameUserAsset()` → `BlobIndexedStorage.migrateStorageData(oldName, newName)` → `:476/479`）。这种场景下旧用户名字符串确实会变成悬空引用，必须重写。组织迁移调用的是另一组 `(entry, oOrg, nOrg)` 构造函数，`oname`/`nname` 恒为 `null`，`Tool.equals()` 判断天然为 `false`——这不是碰巧触发的边界条件，而是准确反映了"组织迁移不涉及用户改名"这个事实。
- 对比 `defaultBookmarkUser`——用 `IdentityID.convertToKey()` 显式编码 `name~;~orgID`，源组织段写死在字符串里，copy/rename 时必须重写否则是过期值，`MigrateBookmarkTask` 因此需要显式处理（见下方 2h）。

系统里身份本身是 `(name, orgID)` 的 org-scoped 概念，不存在跨组织的全局用户实体；`createdBy`/`modifiedBy` 在纯组织迁移下不重写正是把组织隔离原则贯彻到底，反过来记录"最初来自哪个组织"才会破坏隔离。

**`XDataSourceWrapper` 类解析失败时静默丢数据——理论存在，生产环境不现实**：通用兜底分支对 Data Source 做读-写往返（`XDataSourceWrapper.parseXML()`/`writeXML()`）时，如果 `Drivers.getDriverClass()`（`PluginDriverProvider.java:78-103`）解析目标类失败会静默 `return null`，导致 `source` 字段留空、`writeXML()` 只写出两行空 XML 头，全程不抛异常。但触发前提是**系统里一个 `DriverService` 插件都没有**：`Config.getClass()` 对 JDBC 类数据源解析的是核心类 `inetsoft.uql.jdbc.JDBCDataSource`（所有关系型数据源共用同一个 type="jdbc"，见 `config.xml:22-30`），而 `Plugin.PluginClassLoader`（`Plugin.java:328-440`）默认父类加载器优先——只要装了**任意一个**连接器插件，`Class.forName` 就会委派到父加载器命中核心类成功，不会返回 null。生产 Docker 镜像默认打包 8 个以上 JDBC 连接器插件（`docker/pom.xml:177-244`），"零插件"这个前提跟真实部署矛盾，测试沙箱能触发纯粹是因为测试环境没装任何插件这一特有状态。即便真的发生，也不是永久沉默——`DataSourceRegistry.getDataSource()` 是全局通用读取路径，第一次真正打开/使用这个数据源时就会报错，只是错误信息不会直接指向"组织迁移时被清空"，排查会比较绕，但不需要作为紧急数据完整性缺陷跟进。

**绑定引用格式对照：**

| 绑定类型 | 载体类 | 含 orgID？ |
|---|---|---|
| worksheet 内部 mirror（WS→WS） | `MirrorAssemblyImpl` | 含 |
| viewsheet→base worksheet（VS→WS） | `Viewsheet.wentry` | 含 |
| viewsheet 内嵌/库 viewsheet（VS→VS） | `Viewsheet.ventry`（嵌套 `Viewsheet` 自己的字段，`setEntry()` 写入，**不是** `ViewsheetVSAssemblyInfo.entry`——2c 落地时实测确认两者是完全独立的两个字段，`entry` 是从 `ventry` 派生的自愈镜像字段，见下方说明） | 含 |
| worksheet→data source/query（WS→DB） | `SourceInfo` | 不含（纯名字引用） |

`Viewsheet.wentry` 解析时**没有**自愈，`Viewsheet.ventry` 解析时**有**自愈（跟 `MirrorAssemblyImpl` 一样，会用 `handleWSOrgMismatch()`/无条件 `setOrgID(当前组织)` 覆盖存储值）——**这跟本节最早的结论正好相反**，2c 落地时实测更正（详见下方"2c 落地时又踩到两个新坑"）。`wentry` 没有自愈兜底，如果 `Migrate*Task` 未来漏掉这个绑定类型的 XPath，会直接表现成用户可见的"改名后绑定失效"；`ventry`/mirror 那类有自愈的，漏了 XPath 也不会报错，但用原始 XML 断言的测试能抓到——调试时优先查 `wentry`，写测试时两类都要用原始 XML 断言。

**`ViewsheetVSAssemblyInfo.entry` 迁移后短暂过期——非缺陷，自愈镜像字段**：`MigrateViewsheetTask.updateViewsheet()` 只找直接子节点（`Tool.getChildNodeByTagName(assembly, "viewsheetEntry")`），只命中嵌套 `Viewsheet` 自己的 `ventry`，够不着嵌套更深两层的 `ViewsheetVSAssemblyInfo.entry`。但 `entry` 不是独立数据源——它是 `VSEventUtil.fixAssemblyInfo()`（`VSEventUtil.java:1744-1755`：`vsInfo.setEntry(svs.getEntry())`）从已迁移正确的 `ventry` 派生出来的运行时镜像，只要该嵌入 viewsheet 被打开/刷新一次就会自动用正确值覆盖；唯一读取点 `VSObjectModel.java:377-383` 只用它生成一段展示文案（`entry.getDescription()`），不参与资产解析、权限判断或跨组织绑定。迁移后短暂"过期"不会被任何生产路径观察到，不需要单独修复。

### Copy 场景

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 2a | worksheet 内部 mirror | `mirrorAssembly[@source]`/`assetDependency` 指向新组织，源资产不受影响 | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copy_worksheetMirror_pointsAtNewOrg_sourceUnaffected` |
| 2b | viewsheet→base worksheet（`wentry`） | `orgID` 正确，viewsheet 能正常反序列化加载到绑定的 worksheet——**建议第一个落地** | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copy_viewsheetToBaseWorksheet_bindingSurvivesMigration` |
| 2c | viewsheet 内嵌 viewsheet | 嵌套引用（`Viewsheet.ventry`）`orgID` 正确，且能识别到同样被迁移的库 viewsheet；`ViewsheetVSAssemblyInfo.entry` 短暂过期但自愈，无需重写（见上方"绑定引用格式对照"说明） | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copy_viewsheetEmbeddingNestedViewsheet_referenceRewritten` |
| 2f | 内联 SQL/Query 节点独立的 `orgId` 元素 | 正确重写为目标组织 ID | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copy_sqlBoundQuery_orgIdElementRewritten` |
| 2g | drill path、超链接 | 跨资产引用 org 归属正确 | `[待补]`——技术前置未探清，见下方说明 |
| 2h | bookmark 路径、`defaultBookmarkUser` | `defaultBookmarkUser` 走按组织判断的机制，确实会被正确重写 | `[已落地]` `copy_bookmarkDefaultUser_orgSegmentRewritten`（`modifiedBy`/`createdBy` 部分已并入 10c 统一说明，见上方"身份字段格式说明"） |
| 2i | 解析层自愈不一致的回归防护（不必单独立场景，作为 2b/2c 断言的一部分）：`wentry` 没有自愈，若迁移遗漏会被测试直接捕获；`ventry`/嵌套 viewsheet **有**自愈（2c 落地时更正，见上方绑定表），迁移遗漏不会自然报错，必须用原始 XML 断言才能捕获 | `wentry`：迁移后加载 viewsheet 不抛异常、绑定路径确实指向新组织下存在的资产；`ventry`：原始 XML 里的 org 段确实被重写，不能只信反序列化结果 | `[已落地]`（已随 2b/2c 的测试方法一起满足：2b 用反序列化对象断言 `wentry`，2c 改用原始 XML 断言 `ventry`，未单独建场景/方法） |
| 10a | Data Source 定义（通用兜底分支） | key 迁移正确，`SourceInfo` 能在新组织下解析到同名 data source | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copy_dataSource_keyMigrated_sourceInfoResolvesToSameNameDataSource` |
| 10b | VPM / Partition / Data Model（通用兜底分支） | key 迁移正确 | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copy_vpmPartitionDataModel_keyMigrated_identityFieldsNotRewritten`（VPM/Partition 是普通 POJO，不像 `JDBCDataSource` 需要 `Config`/`Drivers`/`Plugins` 一整条 bean 链；同时附带落实了 10c 的实测证据） |
| 10c | 纯组织迁移下 `createdBy`/`modifiedBy` 不被重写——覆盖通用兜底分支（Data Source/VPM/Partition/Data Model）与显式分派类型（Worksheet/Viewsheet）两条路径 | 两条路径都**不会**重写，符合预期：字段从不编码 orgID，靠当前组织上下文动态解析，无需重写（见上方"身份字段格式说明"） | `[已落地]` `copy_vpmPartitionDataModel_keyMigrated_identityFieldsNotRewritten`（通用兜底分支，作为 10b 一部分断言）、`copy_modifiedByCreatedBy_notRewritten_duringOrgOnlyMigration`（显式分派类型 Worksheet/Viewsheet） |
| 10d | Data Source 类解析失败时的静默丢数据链路（`Drivers`/`Config`/`XDataSourceWrapper`） | 链路本身存在，但生产环境触发前提（全系统零插件）不现实，即便触发也非永久沉默、首次使用即报错——符合预期，无需修复（见上方说明） | `[已落地]`（测试环境侧通过反射注入占位 `DriverService` 绕开验证链路本身，生产侧前提不现实，未单独建回归场景） |

### Rename 场景

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 2d | 对应 2a-2c、2f-2h 的 rename 版本 | 除迁移正确外，源组织侧资产被正确清理，不产生孤儿 | `[部分落地]` 2a/2b/2c 已覆盖（`rename_worksheetMirror_pointsAtNewOrg_sourceRemoved`、`rename_viewsheetToBaseWorksheet_bindingSurvivesMigration_sourceRemoved`、`rename_viewsheetEmbeddingNestedViewsheet_referenceRewritten_sourceRemoved`）；2f/2h 的 copy 版本已落地，但 rename 版本仍待补；2g（drill path/超链接）copy 版本本身也仍待补 |

### Delete 场景

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 2e | 组织删除后 `indexedStorage.removeStorage(orgId)` 整桶删除（共享背景 delete 清单 `:1102`） | viewsheet/worksheet 等资产随桶清除，不残留孤儿 | `[已落地]`（与 B4 rename 场景共用同一断言证据，见下）——`removeStorage(orgId)` 是同一个方法调用，B4 的三个 rename 测试（`rename_worksheetMirror_pointsAtNewOrg_sourceRemoved`、`rename_viewsheetToBaseWorksheet_bindingSurvivesMigration_sourceRemoved`、`rename_viewsheetEmbeddingNestedViewsheet_referenceRewritten_sourceRemoved`）本身就直接调用 `storage.removeStorage(fromOrgId)` 并断言迁移+source 资产被清空、无孤儿——2e 描述的"组织删除后整桶清理"和 rename 清理源组织走的是完全相同的代码路径（同一个 `indexedStorage.removeStorage(orgID)` 调用），不需要为触发入口不同（删除 vs 改名）重复建场景 |

### 边界断言（防止与资产改名管线混淆，见 `claude/org-migration-content-rewrite.md`"关系"一节）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 2j | mock/spy `RenameTransformHandler`/`AssetDependencyTransformer` | 组织迁移过程中断言其**未被调用**——把"两套机制互不越界"变成可执行的回归防护 | `[已落地]` `OrgLifecycleAssetContentMigrationTest#copyAndRename_neverTriggerAssetRenamePipeline`（只 mock `RenameTransformHandler`——`AssetDependencyTransformer` 不是 bean，且只在前者 `addTransformTask()` 之后才会被触发，验证前者零交互即可覆盖两者；覆盖 mirror/wentry/嵌套 viewsheet 三种 fixture 各跑一次 copy + 一次 rename） |

### 技术前置问题 — `[已探明，2026-07-21]`

- **Spring bean 装配**：`BlobIndexedStorage(BlobStorageManager)` 是公开单参构造函数，`blobStorageManager` 已是 `BaseTestConfiguration` 现成 bean，照抄 `ScheduleTestConfiguration`/`IntegrationTestConfiguration` 里 `new BlobIndexedStorage(blobStorageManager)` 的模式即可，不需要新 `@Configuration`、不需要反射。**API 不对称**：没有公开 3 参 `migrateStorageData(Organization, Organization, boolean)`（不像机制一），只有 `copyStorageData(oOrg, nOrg, rename)`（`removeOld` 内部固定 `false`）；rename 场景要另外调用 `removeStorage(fromOrgId)` 才算完整（详见 `claude/org-migration-content-rewrite.md` Entry Point 一节）。
- **fixture 构造**：绕开 `AssetRepository.setSheet`，直接 `storage.putXMLSerializable(entry.toIdentifier(true), obj)`。`Viewsheet(AssetEntry wentry)` 构造函数直接设置 `wentry`；`Worksheet` 需至少一个 assembly（如 `EmbeddedTableAssembly`）。key 必须用 `entry.toIdentifier(true)`（强制按 entry 自带 orgID 重新拼接）。
- **断言方式**：`storage.getXMLSerializable(newKey, null, newOrgId)` 做真正反序列化读取（`getDocument()` 只返回原始 DOM，不可用）；写入/读取路径共用同一 per-org blob 桶、无缓存层。`Viewsheet.getBaseEntry()` 是 `wentry` 对应 getter。

详见计划文档 Task B 技术前置问题一节（同一份结论）。

**B2 落地过程中新踩到的两个坑（2026-07-21，实测确认，已写进 `OrgLifecycleAssetContentMigrationTest.IndexedStorageConfig` 的 Javadoc）：**

1. **`new BlobIndexedStorage(blobStorageManager)` 直接调 `copyStorageData()` 不生效**：`migrateStorageData()` 派发的每个 `MigrateDocumentTask.process()` 内部走静态 `IndexedStorage.getIndexedStorage()`（Spring 容器按类型查找 bean），不认调用方手上未注册成 bean 的实例；找不到 bean 时每个子任务抛 `NoSuchBeanDefinitionException`，且 `migrateStorageData()` **把异常记日志吞掉、不上抛**——`copyStorageData()` 表面正常返回，实际什么都没迁移。必须用 `@Bean IndexedStorage indexedStorage(BlobStorageManager) { return new BlobIndexedStorage(...); }` 把它注册成 bean（本测试用专属最小 `@Configuration`，不拖带 `ScheduleTestConfiguration`/`IntegrationTestConfiguration` 的其它 mock bean），再 `@Autowired IndexedStorage` 强转回来用。
2. **`EmbeddedTableAssembly()`/`XEmbeddedTable()` 默认构造需要 `XSwapper` bean**：`BaseTestConfiguration` 没提供，需引入现成的 `SwapperTestConfiguration.class`。

**2a（worksheet 内部 mirror）落地时又踩到两个新坑：**

3. **必须断言原始 XML DOM（`storage.getDocument()`），不能像 2b 那样断言反序列化对象**：`MirrorAssemblyImpl.parseXML()` 会用 `OrganizationManager.getCurrentOrgID()` 无条件覆盖存储里的 org 段（`handleWSOrgMismatch()` 自愈），不管 `MigrateWorksheetTask` 实际写了什么——用 `getXMLSerializable()` 断言会把一个真实的迁移 bug 悄悄掩盖掉（对应 `claude/org-migration-content-rewrite.md`"Parse-time self-heal inconsistency"）。
4. **`mirrorAssembly` 元素嵌套两层**：`MirrorTableAssemblyInfo.writeXML()` 包了一层无属性的外层 `<mirrorAssembly>`，真正带 `source` 属性的是内层（`MirrorAssemblyImpl.writeXML()` 产生，与 `MigrateWorksheetTask` 自己的 XPath `.../mirrorAssembly/mirrorAssembly` 对应）。`getElementsByTagName("mirrorAssembly").item(0)` 是外层（空），要用 `item(1)`。

**2c（viewsheet 内嵌 viewsheet）落地时又踩到两个新坑，并顺带更正了架构文档一处错误结论：**

5. **`ViewsheetVSAssemblyInfo.entry` ≠ `Viewsheet.ventry`，架构文档旧版把两者搞混了**：两个字段 Javadoc 都写"the mirrored viewsheet entry"、都序列化成 `<viewsheetEntry><assetEntry>`，容易当成同一个东西。生产内嵌 viewsheet 走的是 `ComposerObjectService.addEmbeddedViewsheet()` → `assembly.setEntry(entry)`，写的是 `Viewsheet.ventry`（嵌套 `<assembly>` 元素的直接子节点），`MigrateViewsheetTask.updateViewsheet()` 重写的也是这个。`ViewsheetVSAssemblyInfo.entry` 是完全独立的另一个字段（嵌套在 `<assemblyInfo>` 内部深两层），生产内嵌流程根本不会写它，迁移也够不着；后续排查确认它是从 `ventry` 派生的自愈镜像字段、不参与实际解析，非缺陷，已记录在上面"绑定引用格式对照"说明里。`claude/org-migration-content-rewrite.md` 的 Binding 表和 self-heal 小节已同步更正。
6. **`ventry` 同样有 parse-time 自愈**：跟 2a 的 mirror 一样，`Viewsheet.ventry` 解析时会被无条件覆盖成当前运行时组织（`Viewsheet.java:4341-4342`），所以这个场景也必须断言原始 XML，不能用反序列化对象——架构文档旧版误以为这类绑定"没有自愈"，已更正。
7. **找嵌套 viewsheet assembly 元素时，别把外层文档根节点也算进去**：一个存储的 `Viewsheet` 自己 `writeXML()` 出来的根节点本身就是 `<assembly class="...Viewsheet">`（因为 `Viewsheet implements VSAssembly`），朴素的 `getElementsByTagName("assembly")` 找第一个匹配会先命中文档根节点自己，必须显式跳过 `doc.getDocumentElement()`。

**10a（Data Source 通用兜底分支）落地时踩到的坑，比前面几个都深：**

8. **`JDBCDataSource` 要挂一长串 bean 才能构造/解析**：`new JDBCDataSource()` → `initCredential()` → `CredentialService.getInstance()`（构造函数包私有，需反射构造）；`XDataSourceWrapper.parseXML()` → `Config.getConfig()` → `Config.getClass()` → `Drivers.getInstance()`（都不是 `@Service`，都要手动 `@Bean`，照抄 `IntegrationTestConfiguration` 里现成的最小组合 `:242-259`/`:486-487`，不拖带它其余更重的 bean）。
9. **零插件环境下 `Drivers.getDriverClass()` 静默返回 null，导致 `XDataSourceWrapper` 读-写往返后变成"空心"对象**：详见上面"机制说明"里"`XDataSourceWrapper` 类解析失败时静默丢数据"一节的完整链路描述（后续排查确认该前提在生产环境不现实，非缺陷）。测试环境侧构造这个场景不需要真装 JDBC 驱动插件——反射往 `Drivers` 私有的 `driverServices` 字段塞一个占位 `DriverService` 就够了（哪个类无所谓，只要存在，`Class.forName` 走正常的父类加载器委托就能找到 `JDBCDataSource`），见测试代码 `DataSourceConfig.drivers()` 的 Javadoc。

**2f/2h/10b 落地时没有新踩坑（2026-07-21）**：三者都直接复用了已有的 fixture/断言模式（`SourceInfo`/`XQuery.orgId` 用反序列化对象断言、`VSBookmark` 用 `VSUtil.createBookmarkIdentifier()` 复用生产的 key 拼接逻辑、VPM/Partition 是普通 POJO 不需要额外 bean）。唯一值得单独说一句的是 **10b 比 10a 简单得多**——`VirtualPrivateModel`/`XPartition` 不像 `JDBCDataSource` 那样需要 `Config`/`Drivers`/`Plugins` 一整条 bean 链，纯 POJO 直接 `new` 就能用。

**2g（drill path、超链接）仍未落地，原因记录一下**：超链接的绑定点是清楚的（`VSAssemblyInfo.setHyperlinkValue(Hyperlink)`，作用于 `TextVSAssembly`/`GaugeVSAssembly`/`ImageVSAssembly`），但 drill path（`XDrillInfo`/`DrillPath`，`MigrateDocumentTask.updateDrillPaths()` 的 XPath 是 `//XDrillInfo/drillPath`）具体挂在哪个可以直接构造的对象上还没探清楚——`XMetaInfo.setXDrillInfo()` 是已知的载体，但它通常挂在逻辑模型的 `XAttribute`（ERM 层）或图表维度引用上，不是一个能像 `EmbeddedTableAssembly`/`BoundTableAssembly` 那样直接 new 出来挂到 worksheet/viewsheet 的简单对象；仓库里也没有现成的"XDrillInfo 挂到 WSAssembly/VSAssembly"测试模板可抄。与其猜一个可能测不到真实代码路径的 fixture，不如先记录清楚、留到下次专门探一下这个绑定点，再动手写 2g。

### 测试覆盖

场景 2a、2b、2c、2f、2h（`defaultBookmarkUser` 会重写）、2j、10a、10b、10c（含 `modifiedBy`/`createdBy` 通用兜底分支与显式分派类型两条路径均不重写的证据）已落地（copy 方向），2d（rename 方向）针对 2a/2b/2c 也已落地（`rename_worksheetMirror_pointsAtNewOrg_sourceRemoved`、`rename_viewsheetToBaseWorksheet_bindingSurvivesMigration_sourceRemoved`、`rename_viewsheetEmbeddingNestedViewsheet_referenceRewritten_sourceRemoved`）——断言方式跟对应 copy 场景一致，两步 rename 模式复用 B1a 结论，未发现新坑。其余场景（2g、2d 里 2f/2h 对应的 rename 部分）待补，可直接复用同一套 `@ContextConfiguration`（`BaseTestConfiguration` + `SwapperTestConfiguration` + 专属 `IndexedStorageConfig` + 专属 `RenameTransformHandlerConfig` + 专属 `CredentialServiceConfig`/`DataSourceConfig`）。

---

## 三、其他机制（各自独立，资源层）

### 3.1 主题（Theme）

**机制说明：** `copyThemes()`（`AbstractEditableAuthenticationProvider.java:304-428`），copy/rename 共用同一方法。遍历所有 `CustomTheme`：组织自有主题分支 clone 一份新主题；全局主题被组织选中的分支只挂载 `organizations` 列表，不 clone。`replace=true` 时额外从 `organizations` 列表摘除源 orgId、`setOrgSelectedTheme(null, fromOrgId)`。

**Copy / Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 3a | `copyThemes()` 方法级六分支 | 各分支按上述机制行为 | `[已落地]`——`AbstractEditableAuthenticationProviderStaticDepTest`（对应历史 Bug #74719"全局主题被克隆"与 #74711"host org 主题被误当全局主题、CSS 编辑跨组织泄漏"，当前 `copyThemes()`（`:340`/`:398-415`）对 org 自有主题克隆新 UUID+新 orgID、对全局共享主题只挂载 `organizations` 不 clone，两条路径均有专属断言：`copyThemes_matchingTheme_replaceTrue_originalRemovedCloneAdded`、`copyThemes_globalThemeSelectedForFromOrg_replaceTrue_selectionPropagatedNoClone` 等） |
| 3c | 编排层集成：`copyOrganizationInternal()` 完整流程下主题三态（`SreeEnv`/`CustomTheme.organizations`/`Organization.theme`）是否一致 | 两条分支（组织自有主题、被选中的全局主题）三态都应一致 | `[已落地]` `OrgLifecycleThemeOrchestrationTest#copy_orgOwnedSelectedTheme_threeStatesAgree`、`copy_globalThemeSelectedByFromOrg_threeStatesAgree`（`CustomThemesManager` 用 `mockStatic` 而非真实 bean 驱动——见该测试类头注释：community 下的 `CustomThemesImpl` 是纯空实现，`getCustomThemes()`/`setCustomThemes()` 不落地任何状态，且全代码库不存在 `getOrgSelectedTheme(String orgId)` 这个重载，真实 bean 无法满足本场景需要断言的状态，因此用 mock 捕获 `setOrgSelectedTheme(id, orgId)` 调用参数代替"SreeEnv 侧指针"的真实读取；除主题管理器本身外，`DataSpace`/`PortalThemesManager`/`OrganizationManager.runInOrgScope`/`ScheduleManager` 均走真实 Spring 上下文） |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 3d | 全局共享主题被组织选用后删除该组织，主题的 `organizations` 列表是否残留该 orgId | 已修复：`removeTheme()` 现在会摘除该 orgId，不再残留 | `[已落地]`——`IdentityThemeServiceTest#removeTheme_globalThemeStillListsDeletedOrg_organizationsEntryIsStripped` |

**Issue #75739（已修复）：** `IdentityThemeService.removeTheme(orgID)` 现在会遍历全局主题、摘除其 `organizations` 列表里的已删除 orgID，与 rename 路径（`IdentityService.updateCustomThemeOrganization()`）保持对称，不再残留孤儿 orgID。

**已修复的生产风险**

- **`CustomThemesImpl` 缓存的 KeyValueStorage 句柄被共享 LRU 驱逐后永久失效，且不会自愈**（Issue #75784，PR #581，`ffd456e6`）：根因是 enterprise `CustomThemesImpl.init()` 把 `themesKvStore` 缓存在实例字段里，只在字段为空时获取一次 `keyValueStorageManager.getStorage("CustomThemes")`，之后永远复用同一引用；这个 bucket 与其他所有 org 相关 KeyValueStorage 消费者共用 `KeyValueStorageManager` 的 `MAX_SIZE=50` Caffeine LRU 缓存，一旦被驱逐关闭，`stream()`/`keys()` 静默返回空流而 `replaceAll()` 仍真实落地，表现为主题列表消失、新建主题时的全量 `replaceAll` 顺带清空所有组织的主题。**修复**：`init()` 改为 `themesKvStore != null && !themesKvStore.isClosed()` 才跳过，句柄被关闭后自动重新获取，与 `FileAuthenticationProvider.init()` 的自愈写法对齐，改动范围仅限 `init()`。**结果**：回归测试 `CustomThemesImplTest#init_cachedStoreClosed_reFetchesStorage` 验证句柄关闭后会重新拉取并读到正确数据；与机制一（`DependencyStorageService`）的自愈能力现已对齐，风险已消除。

**测试覆盖：** `copyThemes()` 方法级已覆盖（3a）；编排层集成已覆盖（3c，`OrgLifecycleThemeOrchestrationTest`）；delete 路径（3d）回归测试已启用并通过；KeyValueStorage 共享 LRU 风险（#75784）回归测试已启用并通过（`CustomThemesImplTest#init_cachedStoreClosed_reFetchesStorage`）。

---

### 3.2 Dashboard

两套独立存储，不要混为一谈：

| 存储 | 抽象 | 用途 |
|---|---|---|
| `DashboardManager` | `KeyValueStorage`，key = `{orgId}__dashboards` | 每个 identity 的 dashboard 偏好设置 |
| `DashboardRegistryManager`/`DashboardRegistry` | `DataSpace` 文件，`portal/{orgId}/[user/]dashboard-registry.xml` | 实际 VSDashboard 定义本体 |

**偏好设置 — Copy/Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4a | `DashboardManager.copyStorageData()`，copy/rename 都调用 | 整桶复制不删源 | `[已落地]` `DashboardManagerOrgLifecycleTest#copy_seedsTargetOrg_leavesSourceOrgIntact`（种子 `DashboardData` 到源组织桶，调用 `copyStorageData(fromOrgId, toOrgId)`，断言目标桶拿到同 key 同内容，源桶原样保留） |

**偏好设置 — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4b | `removeDashboardStorage()`（共享背景 delete 清单 `:1099`） | 整桶删除 | `[已落地]` `DashboardManagerOrgLifecycleTest#delete_removesWholeStorage`（断言删除后该 key 读不到任何残留） |

**注册表 — 依赖入口不同，两条路径行为不一样：**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4c | Copy：`copyDashboardRegistry()` | 逐用户 `cloneVSDashboard()` 写入新组织路径，源文件不受影响 | `[已落地，@Disabled]` `DashboardRegistryOrgLifecycleTest#copy_copyDashboardRegistry_adminAndUserRegistryCloned_sourceUnaffected`（断言逻辑本身没问题：admin 级 + `securityEngine.getOrgUsers()` 遍历出的用户级注册表都断言克隆成功、内嵌 viewsheet 引用的 orgID 被正确重写；源组织两级注册表断言原样不变。但独立复测发现间歇性失败——约 1/5~1/8 概率 `securityEngine.getOrgUsers(fromOrgId)` 在 `SecurityTestDataBuilder.setup()` 写完用户后读到空列表，疑似 `SecurityEngine` 内部缓存的 provider 与 builder 写入路径之间存在时序竞争，根因未查清，暂时 `@Disabled` 避免污染 CI，机制本身的结论不受影响） |
| 4d | Rename——仅走 `copyOrganizationInternal(replace=true)` | **机制行为（非独立产品入口）**：`copyDataSpace()`（`:146`）对 org-scoped 路径做无差别 `rename`，admin/用户级 `dashboard-registry.xml` 被搬到新组织路径，但本方法本身不调 `migrateRegistry()`，内容 org 段不在此步重写。生产里 `replace=true` 只从 `syncIdentity` 组织改名调用，且排在 `setOrganizationInfo`（4e，已重写内容）之后——本行是测试直接调 `copyOrganization(replace=true)` 钉住的子步骤行为，**不是 EM 用户可见的最终结果，不视为产品缺陷** | `[已落地]` `DashboardRegistryOrgLifecycleTest#rename_copyOrganizationInternal_dashboardFilesRelocatedByDataSpaceRename_contentNotRewritten`（admin + 用户级：新路径可读、内容 orgID 仍旧、旧路径不存在） |
| 4e | Rename——走完整 `setOrganizationInfo()` 入口（对齐 EM：先切到 fromOrg） | **EM UI 下 admin + 用户级内容都会重写**：`:2127` admin 级 `migrateRegistry(null, fromOrg, newOrg)`；用户级靠 `updateOrganizationMembers()` `:869`（参数取 `getCurrentOrgID()`，EM 改 ID 前必切组织故 current==fromOrg）。不测「current≠fromOrg」分支——该路径前端到不了，不按缺陷跟踪 | `[已落地]` `DashboardRegistryOrgLifecycleTest#rename_setOrganizationInfo_adminAndPerUserRegistryContentRewritten`（`OrganizationContextHolder`=fromOrg；直接读 DataSpace 上 `dashboard-registry.xml` 的 identifier org 段，admin + carol 均断言为 toOrgId） |

**注册表 — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4f | `removeOrgScopedDataSpaceElements()` 按路径前缀删除（共享背景 delete 清单 `:614`） | 无孤儿文件——admin 与用户级两种路径形态都会被扫到删除 | `[已落地]` `DashboardRegistryOrgLifecycleTest#delete_removeOrgScopedDataSpaceElements_bothAdminAndPerUserRegistryFilesRemoved`（实测确认 `getOrgScopedPaths()` 对 `portal/{orgId}/dashboard-registry.xml` 和嵌套一层的 `portal/{orgId}/{user}/dashboard-registry.xml` 都会命中前缀、两者都被删除，没有发现新孤儿点） |

**实现备注（非用户可见缺陷，2026-07-23 复测结论）：**
1. **`:869` 用 `getCurrentOrgID()` 而非显式 `fromOrg`**：实现依赖「调用时当前组织已是被改名组织」。EM UI 满足该前提，用户级内容会正确重写——**不按产品缺陷跟踪**。场景 4e 测试已改为对齐 EM（`OrganizationContextHolder.setCurrentOrgId(fromOrgId)`），断言 admin + 用户级均重写；可选加固仍是改为显式传 `fromOrg`/`oldOrgID`。
2. **4d（单独看 `copyOrganizationInternal(replace=true)`）只搬 DataSpace 路径、本步不重写 dashboard 内容**：方法职责边界，不是丢数据。EM 完整链先 4e 再进本步——**不按产品缺陷跟踪**；4d 测试仅钉住该子步骤机制。

**测试覆盖：** 6 个场景（4a-4f）全部落地，共 6 个 `@Test`，2 个测试类：`DashboardManagerOrgLifecycleTest`（`community/core/src/test/java/inetsoft/sree/web/dashboard/`，4a/4b）、`DashboardRegistryOrgLifecycleTest`（`community/core/src/test/java/inetsoft/sree/security/`，4c/4d/4e/4f）。4f 特别核查了"用户级路径是否会被 `getOrgScopedPaths()` 漏扫"这个疑点——实测确认不会漏扫，未发现新孤儿缺陷。4c 因间歇性失败标记 `@Disabled`（见上），实际稳定通过的是 5/6。

---

### 3.3 Schedule Task / Data Cycle

**分工说明（避免与机制二混淆）：** Schedule Task 资产本身的**内容**（比如定时导出/推送某个 Viewsheet 时，Action 里对 `viewsheet` 属性的引用）走的是**机制二**的 `MigrateScheduleTask`（见二）。本节覆盖两块完全独立、都不经过机制二的东西：**Data Cycle**（一种生成"预生成任务"的业务对象）自身**存在性**的迁移，走 `DataCycleManager`；以及 **Task Save 文件**——Schedule Task 执行时（如 `ViewsheetAction`/`IndividualAssetBackupAction` 配置"保存到磁盘"）产生的输出文件，走 `IdentityService.updateTaskSaveFiles()` → `ExternalStorageService`，跟 Data Cycle 存储机制互不相关，只是都属于"Schedule Task 相关但不是任务定义内容本身"这一类。

**机制说明：** `migrateDataCycles(Organization oorg, Organization norg, boolean replace)`（`DataCycleManager.java:863-887`）：`replace=true` 才删除源 entry，两条路径都刷新 `pregeneratedTasksMap` 缓存。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5a | `migrateDataCycles(oorg, norg, replace=false)`，从源组织自身上下文发起（见下方 5f 的前提说明） | 复制 `DataCycleAsset`（`orgId` 字段正确改写），不删源；`CycleInfo` 身份字段同步改写（见 5e） | `[已落地]` `DataCycleManagerOrgLifecycleTest#copy_migrateDataCycles_copiesAssetAndLeavesSourceUntouched` |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5b | `replace=true`，从源组织自身上下文发起——**这不是测试图省事的简化前提，而是 EM 前端下的真实调用形状**：见下方"可达性分析"，改名必须先在右上角切到被改名的组织，两者天然一致 | 同上 + 删除源 entry，无孤儿 | `[已落地]` `DataCycleManagerOrgLifecycleTest#rename_migrateDataCycles_replaceTrue_copiesAssetAndRemovesSource` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5c | `clearDataCycles(orgId)`（共享背景 delete 清单 `:612`），从被删组织自身上下文发起 | 该组织所有 `DataCycleId` 精确清除，无残留 | `[已落地]` `DataCycleManagerOrgLifecycleTest#delete_clearDataCycles_removesAllCyclesForOrg` |
| 5d | 普通 Schedule Task（非 Data Cycle）Delete：靠 `indexedStorage.removeStorage(orgId)` 整桶清理 | 断言删除后确实不可读，防止未来 `IndexedStorage` 改分桶策略后失去覆盖 | `[已落地]` `DataCycleManagerOrgLifecycleTest#delete_indexedStorageRemoveStorage_wholeOrgBucketGone`——低优先级回归防护 |

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5e | **已修复（Bug #75756，PR #4414）**：`migrateCycleInfo()`（`DataCycleManager.java:937-959`）的回归防护 | `createdBy`/`lastModifiedBy` 都被重写成目标组织下的同名用户身份，`orgId` 字段同样正确改写，与 `DataCycleAsset.orgId` 行为一致 | `[已落地]` `DataCycleManagerOrgLifecycleTest#migrateCycleInfo_createdByAndModifiedBy_rewrittenToTargetOrg` |

**已确认缺陷 2（新发现，落地 5a-5e 时实测确认，不在原场景清单里）：** `getDataCycleIds(String orgId)`（私有，`DataCycleManager.java:741-756`）调用的是 `IndexedStorage.getKeys(Filter)` 的**单参**重载，内部（`BlobIndexedStorage.getMetadataStorage(null)`）落回 `OrganizationManager.getCurrentOrgID()`（当前线程组织上下文），完全不使用传入的 `orgId` 参数去限定查询范围——这个参数只在拿到 key 集合之后，用来给结果 `DataCycleId` 贴标签。`migrateDataCycles()`/`clearDataCycles()` 的两个真实调用方——`AbstractEditableAuthenticationProvider.copyOrganizationInternal()`（`:273`）、`IdentityService.syncIdentity()`（`:622`）——都**没有**像同一方法里其它步骤那样把这次调用包在 `OrganizationManager.runInOrgScope(oldOrgId, ...)` 里。

**可达性分析（2026-07-23 补充，逐条核实 EM 前端调用链，结论：改名不可达，复制/删除可达）：** "当前组织"是挂在会话级 `XPrincipal` 上的真实状态——`EmPageHeaderController.setCurrOrg()`（`POST /api/em/pageheader/organization`，右上角组织选择器触发）把选中的 orgId 写进 `((XPrincipal) principal).setProperty("curr_org_id", orgID)`，`XPrincipal.getCurrentOrgId()` 优先读这个值；`page-header.service.ts` 的 `orgPages` 列表里 `"Security Settings Users"`/`"Data Cycles"` 都要求先选中一个组织。这解释了为什么普通的 Data Cycle 增删改（`ScheduleCycleService`）不受这个缺陷影响——那些方法走的是显式 `orgId` 参数的存储 API，且调用前 EM 已经强制切好了组织。但组织本身的改名/复制/删除是否也满足"当前组织==目标组织"这个前提，三条路径答案不一样：
- **改名——不可达，不按产品缺陷跟踪**：`setOrganization()` → `POST edit-organization` → `syncIdentity()` → `copyOrganizationInternal(replace=true)`。要打开 org0 的"编辑组织"面板，站点管理员必须先在右上角切到 org0（跟编辑 org0 的 Data Cycle 是同一个约束），因此 `migrateDataCycles(fromOrg, newOrg, true)` 执行时 `curr_org_id` 天然等于 `fromOrg`——跟三、3.2 节 Dashboard 注册表 4e 场景最终的结论同一性质：代码行为如实描述，前端到不了，不算产品缺陷。5b 场景因此不需要修改。
- **复制（Add Organization → "duplicate from"）——代码分析与实测结果矛盾，`[待确认]`，后续处理**：`CreateOrganizationDialogComponent` 的"复制来源"下拉框自己独立调用 `get-all-organizations` 拿全部组织列表，跟右上角 `PageHeaderService.currentOrgId`/会话的 `curr_org_id` **没有任何耦合**（组件代码里完全不引用它）。站点管理员可以停留在任意当前组织（包括从未手动切换过的默认组织）下，在弹窗里选 org0 作为复制来源——按代码分析，`migrateDataCycles(org0, newOrg, replace=false)` 执行时 `curr_org_id` 大概率跟 org0 对不上，应该会静默缺失源组织的 Data Cycle。5f 直接调 `migrateDataCycles()`、5h 往上多走一层驱动 `UserTreeService.createOrganization()` 实际调用的 `AbstractEditableAuthenticationProvider.copyOrganization(...)` 整条链路，两个单元测试结论一致，且已核实生产环境真实装配的 `IndexedStorage` bean（`EngineConfiguration.java:260-264`，即 `BlobIndexedStorage`）与测试用的完全相同，`enterprise/`/`server/` 也没有找到任何覆盖 `copyOrganization`/`DataCycleManager`/加 `runInOrgScope` 的代码。
  **但实际人工测试给出了相反结果**（2026-07-23，用户复测）：host org 管理员在右上角**刻意不选中源组织**的情况下，克隆一个本身带有 Data Cycle（cycle2）的组织，新组织里 cycle2 依然正常显示、内容无异常——没有复现 5f/5h 预测的"静默丢失"。两轮独立代码核查（含真实 Spring 装配确认）都没能找到能解释这个差异的机制，问题原因目前未知——**可能是环境/构建版本差异，也可能是遗漏了某个实际调用路径**，尚未查清。按本文档"如实记录当前行为，不假定对错"的原则，先记录这个矛盾，标记为待确认，后续再排查（不排除 5f/5h 两个单元测试本身反映的是一个只存在于该方法孤立调用下、但被生产环境别的机制掩盖掉的问题——即"代码里有这个坑，但目前找不到真实触发路径"）。
  **下游影响（MV 调度）同样未能复现（2026-07-23，用户复测）：** 分析认为，即使 `DataCycleAsset` 本身克隆正确，`MVDef.cycle`（`MVDef.java:2738`，纯字符串、不带 orgId、`MVManager.migrateStorageData()`/`updateMVDef()` 会完整复制 MV 定义与 `.mv` 数据但不改写这个字段）要正确挂到新组织的定时任务，还依赖 `DataCycleManager.generateTasks()` 在新组织桶里能找到同名 `DataCycleAsset`——按 5f/5h 的预测，这一步应该也会因为同一个 `getDataCycleIds()` 上下文缺陷而失败，新组织里不会生成"DataCycle Task: cycle2"这个调度任务。但用户实测克隆后新组织下确实存在"DataCycle Task: cycle2"，MV 的调度绑定并未表现出异常。这与上一条是同一个根源分歧（`migrateDataCycles()`/`getDataCycleIds()` 的实测行为与代码分析不符），不是独立的新问题——一并记录、一并留待后续排查，暂不重现。
- **删除（组织列表勾选删除）——可达，且是默认场景，不需要任何特殊操作**：`deleteIdentities()` 操作的是组织列表/树里勾选的节点；`users-settings-page.component.ts` 里选中/删除树节点不会触发任何切换当前组织的调用，这份组织列表本身面向站点管理员是跨组织的平铺清单，不受当前选中组织影响。最自然的路径就是：管理员登录后停在默认组织（从未手动切换），直接在列表里勾掉 org0、点删除——`clearDataCycles(org0)` 执行时 `curr_org_id` 还是默认组织，`getDataCycleIds(org0)` 实际扫的是别的桶。**表现：org0 删除后，它的 Data Cycle 资产永久残留在已经不存在的组织的 IndexedStorage 桶里，成为 EM 再也触达不到的孤儿数据。**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5f | 复制方向（内层）：`migrateDataCycles(replace=false)` 在当前组织上下文≠源组织时被调用（不包 `runInOrgScope`，对应 Add Organization "duplicate from" 独立于右上角选择器这一真实可达路径） | 静默不复制任何内容：目标组织读不到该 Data Cycle，源组织的原始条目也不受影响（不是崩溃，也不是数据损坏） | `[已落地]` `DataCycleManagerOrgLifecycleTest#migrateDataCycles_calledOutsideSourceOrgContext_silentlyMigratesNothing`（单元测试本身通过、断言的是方法级隔离行为）——`[待确认]` 与下方真实人工测试结果矛盾，尚未查清原因，见上方说明 |
| 5h | 复制方向（真实入口）：驱动 `AbstractEditableAuthenticationProvider.copyOrganization(...)`——`UserTreeService.createOrganization()` 处理 Add Organization "duplicate from" 时实际调用的同一个方法，而非直接调 `migrateDataCycles()` | 新建组织读不到源组织的 Data Cycle，跟 5f 结论一致，证明不是"只调内层方法才触发"的人为现象 | `[已落地]` `DataCycleManagerOrgLifecycleTest#cloneOrganization_viaRealCopyOrganizationEntryPoint_newOrgSilentlyMissingSourceDataCycle`——`[待确认]` 同上，跟真实人工测试结果矛盾，见上方说明 |
| 5g | 删除方向：`clearDataCycles(orgId)` 在当前组织上下文≠被删组织时被调用（不包 `runInOrgScope`，对应组织列表删除这一默认可达路径） | 静默不清理：被删组织的 Data Cycle 资产在组织本身删除后依然留在存储里，成为孤儿 | `[待补]` |

**Task Save 文件 — Rename 场景**（copy 不涉及；机制说明见上方"分工说明"）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 7a | `updateTaskSaveFiles()` → `externalStorageService.renameFolder()`，仅 rename 调用 | 组织 id 不同才调用 `renameFolder(oorg, norg)`；id 相同时直接跳过（`Tool.equals(oorg,norg)` 早退） | `[已落地]`（方法自身行为）——`updateTaskSaveFiles_orgsDiffer_renamesExternalStorageFolder`、`updateTaskSaveFiles_sameOrgId_noOp`；copy 路径确实不调用这一事实来自直接通读 `copyOrganizationInternal()`（`updateTaskSaveFiles()` 只出现在 `:154`，位于 `if(replace)` 分支内，copy 分支没有对应调用），未额外走 `copyOrganization()` 真实入口重新验证。`[待确认]`——copy 场景不复制 Task Save 文件是否符合预期，仍待产品/业务确认 |

**测试覆盖：** 8 个场景（5a-5f、5h、7a）已落地，`DataCycleManagerOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/sree/internal/`）共 7 个 `@Test`，全部通过；5g（`clearDataCycles()` 的同类场景）待补。7a 的测试方法实际落在三、3.4 的测试文件 `IdentityServiceAutoSaveOrgLifecycleTest.java` 里（跟 `updateAutoSaveFiles()`/`updateTaskSaveFiles()` 是 `IdentityService` 上两个相邻方法、代码邻接顺手一起测了，不是本节机制的一部分）——按文档主题挪到这里说明，测试代码本身不搬。

---

### 3.4 Autosave 文件

**EM 树节点对照（双向说明，跟三、3.7 开头的"EM 树节点结构备注"对应）：** 本节讲的 `__autoSave` 桶，在 EM UI 上对应 Content > Repository > **Recycle Bin > Auto Saved Files** 这个树节点——`Auto Saved Files` 在导航树上是"Recycle Bin"合成根节点下的一个子节点（`ContentRepositoryTreeService.getRecycleNodes()`，`:366-411`），但代码/存储跟 `RecycleBin`（三、3.7 讲的 `__recyclebin` 桶）完全无关，只是 UI 上放在一起，不要因为都叫"Recycle Bin/..."就以为是同一套机制。反过来找的话：三、3.7 开头也有一句指回本节。

**Autosave — 管理/恢复层背景（EM "Auto Saved Files" 树节点，2026-07-27 追加，代码实测非猜测）：** raw `__autoSave` 桶本身的 copy/rename/delete（下面 6a/6b/6d）只是最底层的整桶操作；EM 管理界面（Content > Repository > Recycle Bin > Auto Saved Files 子节点，见三、3.7"EM 树节点结构备注"）和用户侧的恢复流程是叠加在这个桶上的另一层独立代码，装的不是"被删除的资产"而是 Composer 编辑过程中**尚未正常保存的草稿快照**，语义上对应"异常退出/没保存"这个场景——下面 Copy/Rename 场景里的 6g/6i、以及"其他场景"里的 6e/6f/6h 都建立在这层机制上：

- **写入触发**：key 编码 `scope^TYPE^ownerUser^assetPath^ipAddress~`（`AutoSaveUtils.java:334-345`），内容是 viewsheet/worksheet 原始序列化 XML。写入不是独立定时器，而是寄生在 Composer 的 STOMP 心跳上：心跳时若检测到"设计态+有未保存改动"就发 `TouchAssetEvent`，后端 `TouchAssetService.touchAsset()`（`:54-208`）按"距上次 autosave 是否有新访问"节流后才真正落盘。
- **恢复提示（两处）**：① 打开 Composer 会话时，`SetPrincipalCommand`（`:55-61`）按用户+当前 IP 扫描其自动保存文件，前端弹"是否恢复上次未保存的文件"确认框；选"否"会把文件移入桶内 `recycle/` 子命名空间（`AutoSaveUtils.RECYCLE_PREFIX`，只是改名不是删除）。② 重新打开某个具体资产时，`OpenViewsheetController.validateOpen()`（`:100-113`）单独检查该资产是否存在自动保存文件并二次确认。
- **丢弃（Discard）动作统一入口**：`VSEventUtil.deleteAutoSavedFile()` 是关闭 Composer（`closeViewsheet()`/`closeWorksheet()`）和打开资产时选"否"（`AbstractAssetEngine.getSheet()` 非 `openAutoSaved`/非 `TEMPORARY_SCOPE` 分支）两类丢弃场景的统一入口；Save 走另一条永久删除路径，不算丢弃。修复前该方法对已保存 sheet 是永久删除、只有 untitled sheet 才移入回收站——这正是"已存在的 autosave 文件在 EM Auto Saved Files 树上看不到"的真正原因；PR #4408（Issue #75777，已修复）统一成不分场景一律移入回收站。
- **生命周期收尾**：正常点击 Save 会显式调 `AutoSaveUtils.deleteAutoSaveFile()`（`SaveViewsheetDialogService.java:218`/`SaveWorksheetDialogService.java:91`）清掉草稿；`AutoSaveService` 每 3 小时清理一次超过 7 天的自动保存文件（含 `recycle/` 子命名空间）。
- **EM 管理动作**：`Auto Saved Files` 节点只列 `recycle/` 子命名空间里的文件，管理员可 Restore（`AutoSaveController` 的 `restore` 接口，把草稿当新资产写回正常 `AssetRepository`）或 Delete，两个操作都落审计记录。

**Autosave — Copy/Rename 场景**（组织 copy/rename 会触发两个互相独立的机制：① `updateBlobStorageName()` 整桶流式复制/改名，同一方法 copy/rename 行为相同——6a/6b；② `migrateAutoSaveFiles()` 逐文件重写文件名里嵌的用户身份，属于上面"管理/恢复层"那套机制——6g/6i）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6a | `updateBlobStorageName("__autoSave",...,copy=true)`，copy 调用（`copyStorages(replace=false)`） | 整桶流式复制，不删源 | `[已落地]`——`copy_copyStorages_autoSave_copiesBlobAndLeavesSourceUntouched` |
| 6b | 同一方法，rename 调用（`copyStorages(replace=true)`） | 同 copy 行为（删源另在 rename 清理块生效）——`copyStorages()` 内部对 `updateBlobStorageName("__autoSave",...)` 的 `copy` 实参是硬编码 `true`（`IdentityService.java:1145`），并不随外层 `rename`/`replace` 参数变化；确认 `copyStorages()` 本身单独调用时绝不删源 | `[已落地]`——`rename_copyStorages_autoSave_sourceSurvivesUntilSeparateRemoveStoragesCall` |
| 6g | 管理员克隆或改名组织后，目标组织里的 autosave 草稿文件名是否正确重写为新组织的用户身份 | 文件名应正确改写为新组织身份字符串 | `[已落地]` `AutoSaveServiceOrgLifecycleTest#migrateAutoSaveFiles_explicitStorageOrgId_scopesToTargetBucket`——原怀疑 `migrateAutoSaveFiles()`（clone 分支专属调用）会被 `Principal` 自带的当前组织架空 `runInOrgScope`（曾单独编号为 6j），PR #4459 后方法签名改为显式 `storageOrgId` 参数、不再接收 `Principal`，这个问题已从根上消失，随本测试一并验证，不再单独建场景 |
| 6i | 管理员克隆或改名组织后，一个此前已被丢弃（放入 recycle）的 autosave 草稿，迁移后是否仍保持"已丢弃"状态 | 应保留 recycle 前缀，否则该草稿会被误还原成"活跃草稿"、从 "Auto Saved Files" 树上消失——已修复（Issue #75827，PR #4459 已合并） | `[已落地]` `AutoSaveServiceOrgLifecycleTest#migrateAutoSaveFiles_recycledDraft_keepsRecyclePrefixAfterMigration` |

**Autosave 嵌入内容修正**（依赖机制二，见共享背景"跨机制依赖关系"）：6c——`AutoSaveUtils.migrateAutoSaveFiles()` 是否正确重写嵌入的 viewsheet/worksheet 内容——不写 unit case，改走人工验证：copy/rename 一个带内嵌 viewsheet/worksheet 引用的 autosave 草稿，检查迁移后内容是否正确重写；不落地自动化测试的原因是需要真实 XML 素材+`MigrateViewsheetTask`/`MigrateWorksheetTask` 依赖，成本明显高于 6a/6b/6d。

**Autosave — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6d | `removeBlobStorage("__autoSave",...)`（共享背景 delete 清单 `:1105-1111`，即 `IdentityService.removeStorages()`） | 整桶删除 | `[已落地]`——`delete_removeStorages_autoSave_wholeBucketGone` |

**Autosave — 其他场景（与组织 clone/rename 无关）**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6e | 管理员/用户从 EM 把一个自动保存的草稿恢复为正式资产 | 恢复后的资产应落在草稿本身所属组织（即调用时传入的 `principal` 所属组织），而不是执行恢复操作那一刻线程上下文里恰好绑定的组织——**代码层面已确认是缺陷**（`AssetEntry` 4 参构造函数落到 `OrganizationManager.getCurrentOrgID()` 无参版本，取的是线程上下文组织，不是 `principal`）；2026-08-03 复核代码仍存在。**需要人工确认**：是否要修、按什么口径修，待产品/业务确认——范围比最初设想窄，只在管理员"代替他人"操作、或跨节点集群转发这类场景才会真的分叉 | `[已写好，@Disabled 待确认]` `AutoSaveServiceOrgLifecycleTest#restore_targetOrgResolvedFromThreadContext_notFromMethodPrincipal`（临时启用手动跑过一次，逻辑本身能跑通、断言按预期通过；`@Disabled` 是因为这一发现本身还没有走完确认流程，不是代码跑不通） |
| 6f | 组织里有个用户拥有 autosave 草稿，但该用户暂时没有被系统枚举到（如 SSO 同步延迟） | 该用户的草稿记录应仍显示在 "Auto Saved Files" 树上——**代码层面已确认是缺陷**（`addRecycleAutoSaved()` 枚举不到匹配用户时直接 `continue`，记录静默从树上消失，没有任何兜底或报错）；2026-08-03 复核代码仍存在。**需要人工确认**：这个场景在生产环境是否真的会发生、发生频率如何、要不要修，待产品/业务确认——与 Issue #75777 无关 | `[已写好，@Disabled 待确认]` `ContentRepositoryTreeServiceTest#addRecycleAutoSaved_ownerNotYetEnumerated_fileSilentlyDropped`（同上，临时启用验证过能跑通）——Issue #75777 已修复（PR #4408），根因是"已保存 sheet 的 autosave 草稿从未进入回收路径"，**跟本场景的用户枚举假设无关**（修复未改动 `getOrgUsers()`/`createUserNodes()`），两者已确认是独立问题 |
| 6h | 非默认组织存在超过 7 天的过期 autosave 草稿，等待每 3 小时一次的定时清理任务处理 | 该草稿应被清理；实际 `removeExpiredAutoSaveFiles()` 只清理默认组织的桶，非默认组织的过期草稿永远不会被清理，不抛异常，静默漏扫——**Bug #75887**，2026-08-03 复核代码仍存在 | `[已写好，@Disabled 待确认]` `AutoSaveServiceOrgLifecycleTest#removeExpiredAutoSaveFiles_onlyScansDefaultOrgBucket_nonDefaultOrgNeverListed`（同上，临时启用验证过能跑通）——注：`BlobStorage` 没有公开 API 可以倒填 lastModified，测试只能验证"桶的路由范围"这一层，验证不了"7 天后真的会被删"这个完整链路 |

**测试覆盖：** **Copy/Rename 场景**：6a/6b 已落地，`IdentityServiceAutoSaveOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/web/admin/security/`）——该文件另有 2 个 `@Test` 覆盖 7a（Task Save 文件 rename），场景文档已挪到三、3.3，测试代码本身仍在此文件里（代码邻接，未搬）；6g 已落地（正向验证，非 `@Disabled`），随 PR #4459（2026-07-31 合并）改写自原 `migrateAutoSaveFiles_runInOrgScope_correctlyScopesToNewOrgBucket`，一并覆盖了原先单独编号的 6j；6i 也随该 PR 新增 `migrateAutoSaveFiles_recycledDraft_keepsRecyclePrefixAfterMigration` 落地验证；6c 不写 unit case，改走人工验证（见上方说明）。**Delete 场景**：6d 已落地。**其他场景**：6e/6f/6h 三个疑似缺陷/功能缺口的测试代码已写在 `AutoSaveServiceOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/web/`，6e/6h）和新建的 `ContentRepositoryTreeServiceTest.java`（`community/core/src/test/java/inetsoft/web/admin/content/repository/`，6f）里，均标 `@Disabled` 待产品/后续确认——但三者都已临时启用手动跑通过一次，确认测试本身逻辑站得住、不是编译或断言写错；"当前组织"切换手法复用 `RecycleBinOrgLifecycleTest` 已验证过的 `ThreadContext.setContextPrincipal(SRPrincipal)` + `OrganizationContextHolder.setCurrentOrgId()`。

**用户级改名/删除（同组织内，区别于组织级 clone/rename/delete 用的 6a-6i 那一套，也区别于上面 6f 的"暂时枚举不到"假设）：** `IdentityService.syncIdentity()` 处理 `Identity.USER` 类型变更时另有专属逻辑（`:568-606`）——改名调 `updateUserAutoSaveFiles(oID, nID)`（`:2737-2770`），显式把该用户 recycle 里 autosave 文件名中嵌的旧 key 改写成新 key；删除调 `AutoSaveUtils.deleteUserAutoSaveFiles(identityId)`（`AutoSaveUtils.java:273-`），按注释"Used when a user is deleted so their drafts are not left orphaned"直接删除该用户全部 autosave 文件（active + recycle）。**2026-08-04 人工实测确认，均为预期行为，非缺陷**：EM 改用户名（`admin`→`admin1`）后，Auto Saved Files 树上正确显示 `admin1`、资源不丢失；删除用户后该用户节点从树上消失，且新建一个同名用户也看不到旧数据——数据是被真实删除了，不是隐藏成孤儿。

---

### 3.5 Data Space 文件

**机制说明：** `copyDataSpace(fromOrg, toOrg, replace)`（`AbstractEditableAuthenticationProvider.java:444-491`）：`replace=true` 用 `dataspace.rename()`，`replace=false` 用 `dataspace.copy()`；若源是默认组织，copy 分支额外复制 MV 文件系统/Block 系统元数据。`setOrganizationInfo()` 入口另有独立实现 `updateOrgScopedDataSpace()`。两者最终判断"哪些路径属于这个组织"都靠 `DataSpace.getOrgScopedPaths(Organization)`（`DataSpace.java:403-409`），本节下方"规则N"均指该方法源码里按从左到右书写顺序排列的六个 OR 条件：

| 规则 | 源码条件 | 匹配的路径形态 |
|---|---|---|
| 规则1 | `p.equals("portal/" + oorg.getId())` | `portal/{orgId}` 精确匹配 |
| 规则2 | `p.startsWith("portal/" + oorg.getId() + "/")` | `portal/{orgId}/...` 前缀 |
| 规则3 | `p.startsWith(oorg.getId() + "__")` | `{orgId}__...` 前缀 |
| 规则4 | `p.equals(oorg.getId())` | `{orgId}` 精确匹配 |
| 规则5 | `p.startsWith(oorg.getId() + "/")` | `{orgId}/...` 前缀 |
| 规则6 | `p.startsWith("sreeUserData/") && p.endsWith("_" + oorg.getId() + ".xml")` | `sreeUserData/..._{orgId}.xml`（Issue #75763 修复后的写法，此前是死代码，见下方"已修复"结论） |

三、3.6 Replet Registry 提到的"命中规则5"引用的就是这张表。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 8a | `copyDataSpace()` 方法级三分支（A/B/C） | 见机制说明 | `[已落地]`——`AbstractEditableAuthenticationProviderStaticDepTest`（与三、3.1 场景 3a 同一测试类） |
| 8b | 默认组织特例：额外复制 MV/Block 元数据 | 新组织拿到对应文件 | `[已落地]` `OrgLifecycleDataSpaceIntegrationTest#copy_defaultOrgSource_replaceFalse_copiesFsAndBlockSystemFiles`（真实 DataSpace，不 mock：种子文件写在 `AbstractFileSystem.getOrgPaths(null)[0]`/`DefaultBlockSystem.getOrgPaths(null)[0]` 这两个"未加组织段"的原始路径上——因为 `getOrgFileName()` 在 orgId 等于默认组织 ID 时原样返回路径，这正是该分支只对默认组织源触发的原因——断言新组织拿到的对应路径文件存在且内容一致，源文件保持不变（copy 不删源）） |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 8d | `setOrganizationInfo()` 入口独立的 `updateOrgScopedDataSpace()` | 确认这条独立实现的重命名行为跟 `copyDataSpace()` 一致，不产生行为分叉 | `[已落地]` `OrgLifecycleDataSpaceIntegrationTest#rename_updateOrgScopedDataSpaceEntry_consistentWithCopyDataSpaceEntry`（两条独立实现分别对各自一组等价的种子路径执行改名，断言净效果完全一致：源路径消失、目标路径存在且内容不变；`updateOrgScopedDataSpace()` 比 `copyDataSpace()` 多出的 `exists()` 前置检查和"新旧路径相同则跳过"这两处防御性分支，对真实存在的种子路径不产生任何行为差异） |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 8c | `removeOrgScopedDataSpaceElements()` 按路径枚举删除（共享背景 delete 清单 `:614`） | 无孤儿路径 | `[已落地]` `OrgLifecycleDataSpaceIntegrationTest#delete_removeOrgScopedDataSpaceElements_allKnownPathShapes_noOrphans`（五个路径形态）+ `#delete_removeOrgScopedDataSpaceElements_sreeUserDataFile_isRemoved`（第六个分支 `sreeUserData/...`，Issue #75763 修复后已改为断言无孤儿，见下方结论） |

**已修复（Issue #75763，PR #4381，2026-07-27）：** `sreeUserData/` 下的 per-user `UserEnv` 文件（History Bar、locale、annotation 等所有经 `UserEnv.setProperty()`/`getProperty()` 持久化的用户级设置）改名后丢失、删除后成孤儿。`DataSpace.getOrgScopedPaths()` 第六个 OR 分支改为直接按文件名后缀匹配（`p.endsWith("_" + oorg.getId() + ".xml")`），对所有此类设置一次性生效，`copyDataSpace`/`updateOrgScopedDataSpace`/`removeOrgScopedDataSpaceElements` 三个调用方同步修复。原两个"钉住当前错误行为"的特征测试已同步翻转为断言修复后行为：`rename_sreeUserDataFile_bothEntryPointsRelocateItToNewOrgId`、`delete_removeOrgScopedDataSpaceElements_sreeUserDataFile_isRemoved`。

**测试覆盖：** 8a-8d 全部落地，`OrgLifecycleDataSpaceIntegrationTest.java`（`community/core/src/test/java/inetsoft/sree/security/`，与 8a 所在的 `AbstractEditableAuthenticationProviderStaticDepTest` 同包，复用其包内可见的 `StubProvider` 而非重复定义），共 5 个 `@Test`（含 Issue #75763 的删除路径 + 改名路径两个特征测试），全部通过、无 `@Disabled`；均直接调用 `copyDataSpace()`/`updateOrgScopedDataSpace()`/`removeOrgScopedDataSpaceElements()` 本身（反射 + 真实 `DataSpace` bean），不经过 `copyOrganizationInternal()`/`setOrganizationInfo()` 完整编排，因此不需要 `PortalThemesManager`/`DashboardRegistryManager` 之类的额外 bean 覆盖。

**附录：DataSpace 资源盘点（2026-07-24 全量普查）**

sreeUserData 这个缺陷找到后带出了一个自然的问题：`getOrgScopedPaths()` 六条匹配规则会不会漏掉别的资源？为回答这个问题，把 `community/core` 里所有经过 `DataSpace`（以及概念上常被一起提起、但物理上是独立 blob/KV 桶的"其它存储"）读写的资源类型过了一遍。结论：**`sreeUserData` 是目前找到的唯一一个"路径确实落在 `dataSpace` 桶里、但六条规则都没接住"的同类缺陷**；额外挖到一个性质不同但同样真实的问题——`PortalThemesManager` 品牌元数据（下方独立说明），另有 `emFavorites` 删除路径死代码（不落在 `dataSpace` 桶里，机制上不属于本附录范围，已独立成节，见三、3.9）。

**A. 落在 `dataSpace` 桶里、被 `getOrgScopedPaths()` 正确匹配（隔离且生效）：**

| # | 资源 | 路径形态（示例） | 匹配的规则 | 结论 |
|---|---|---|---|---|
| A1 | 组织级 Replet Registry | `{orgId}/repository.xml`（`RepletRegistry.java:244-250`） | 规则 5 `{orgId}/` | 正常 |
| A2 | 用户 "My Dashboard" Replet Registry | `portal/{orgId}/{user}/my dashboard/repository.xml`（`RepletRegistry.java:876-965`） | 规则 2 | 正常 |
| A3 | Dashboard Registry（组织级/用户级） | `portal/{orgId}/dashboard-registry.xml`、`portal/{orgId}/{user}/dashboard-registry.xml` | 规则 2 | 正常，已在三、3.2 详细覆盖 |
| A4 | 自定义 Shape 库（多租户模式） | `portal/{orgId}/shapes/{file}`（`ImageShapes.java:112-117`） | 规则 2 | 正常 |
| A5 | 按组织自定义 CSS | `portal/{orgId}/{cssFileName}`（`LookAndFeelService.java:611-620`） | 规则 2 | 正常——文件路径本身隔离生效，另外还有 `PortalThemesManager.cssEntries` 这份专属映射做二次保险（见下方"B16"，`cssEntries` 是唯一被 `copyOrganizationInternal()` 正确维护的品牌映射） |
| A6 | 按组织 Logo 文件本体 | `portal/{orgId}/{logoFile}`（`LookAndFeelService.java:495-527`） | 规则 2 | 文件本体隔离正常，**但引用它的 `PortalThemesManager.logoEntries` 映射不同步——见三、3.8.1** |
| A7 | 按组织 Favicon 文件本体 | `portal/{orgId}/{faviconFile}`（`LookAndFeelService.java:545-577`） | 规则 2 | 同 A6，**`faviconEntries` 映射不同步** |
| A8 | MV FileSystem 索引 | `{orgId}/fs.xml`（`AbstractFileSystem.java`） | 规则 5 | 正常，已在场景 8b 覆盖 |
| A9 | MV BlockSystem 索引 | `{orgId}/bs.xml`（`DefaultBlockSystem.java`） | 规则 5 | 正常，已在场景 8b 覆盖 |
| A10 | Legacy 报表部署导入：用户级模板 | `portal/{orgId}/{user}/my dashboard/{fname}`（`DeployManagerService.java:222-236`） | 规则 2 | 正常 |
| A11 | 每用户 `UserEnv` 偏好设置 | `sreeUserData/{name}_{orgId}.xml`（`UserEnv.java:237`） | 规则 6（曾是死代码，**已修复**） | **已修复，Issue #75763，见上方** |

**B. 落在 `dataSpace` 桶里、但设计上本来就不按组织隔离（非缺陷，如实记录）：**

| # | 资源 | 路径（示例） | 说明 |
|---|---|---|---|
| B12 | 全局默认 CSS | `portal/format.css`（`CSSDictionary.java:115-117`，`defaults.properties:278`） | 单一安装级默认值，不区分组织，设计如此 |
| B13 | 全局 Shape 库（单租户 fallback） | `portal/shapes/{file}`（`ImageShapes.java:119-121`） | 仅在关闭多租户时生效，设计如此 |
| B14 | `userformat.xml`（数字/小数格式） | `userformat.xml`（dir=null/home，`ExtendedDecimalFormat.java:388-415`、`LookAndFeelService.java:177-192`） | **设计如此，非缺陷**——所有组织共享同一份文件；即使是"按组织"的 Look and Feel 页面单独设置数字格式，写的也是这同一个文件。跟"改名/删除后丢失"性质不同，是"从一开始就没有做 per-org 隔离"，多租户下的实际影响是组织之间会互相覆盖对方的数字格式设置——这是设计取舍，不按缺陷跟踪，仅记录以防将来被误当成 sreeUserData 同类问题 |
| B15 | Legacy Data Cycle 配置 `cycle.xml` | DataSpace 根目录（`DataCycleManager.java:477-499`） | 一次性历史迁移进 `IndexedStorage` 后废弃，之后不再产生持续风险 |
| B16 | `portalthemes.xml`（`PortalThemesManager` 自身注册表） | DataSpace 根目录（`PortalThemesManager.java:566-597`，文件名可配，默认 `portalthemes.xml`） | 文件本身不该被 `getOrgScopedPaths()` 匹配——这是**正确设计**：组织数据不是靠文件路径隔离，而是在文件内部以 `cssEntries`/`logoEntries`/`faviconEntries`/`welcomePageEntries` 四个 `Map<orgId, ...>` 存的。但这四个 map 在组织生命周期操作下是否被正确同步，四个 map 待遇不一致，且这属于 EM "Presentation" 页面而非 DataSpace 路径匹配问题——**详见三、3.8.1（独立成节）** |
| B17 | 全局字体库 | `fonts/{file}`（`FontManager.java`） | 全局，设计如此 |
| B18 | 自定义 head 标签注入资源 | `web-assets/**`（`HomePageController.java`） | 全局，设计如此 |
| B19 | 图片选择器目录 | `images/**`（管理员配置的 `html.image.directory`） | 全局共享图库，设计如此 |
| B20 | `[待确认]` Legacy 报表部署导入：全局模板 | `templates/{fname}`、`templates/subreports/{fname}`、`ReportFiles/{fname}`（`DeployManagerService.java:213-244`） | 路径里完全不含 orgId，如果这个（前多租户时代的）导入功能在当前多租户环境下仍然可达，会是跟 sreeUserData 同类的"该隔离但没隔离"缺陷；但没能确认这条功能在当前版本是否还真的可达，暂不计入已确认缺陷，留作后续排查项（见六） |

**C. 完全不在 `dataSpace` 桶里的资源（独立 BlobStorage/KeyValueStorage 桶，`getOrgScopedPaths()` 物理上管不到，各自有专属清理机制，不在本节重复展开）：**

`__mv`/`__mvws`/`__mvBlock`/`__pdata`/`__library`/`__tableCacheStore`/`__autoSave`/`__recyclebin`/`__dependencyStorage`/`__dashboards`/`{orgId}__indexedStorage` 均按 org 分桶，`IdentityService.copyStorages()`/`removeStorages()` 对每个桶单独调用专属的 remove/copy 方法（见共享背景 delete 清单、一、二、三、3.2/3.4/3.6/3.7）——这些不是本轮普查的新发现，只做索引。本轮普查在这一类里新发现的 `emFavorites` 桶不是按组织分桶（全局单一桶），机制上不属于 DataSpace 普查范围，已独立成节，见三、3.9。

（Portal 仓库树上的"星标收藏"——文件夹用 `RepletRegistry` 的 `favoritesUser` 属性、资产用 `AssetEntry.addFavoritesUser()`——都寄生在已经隔离好的资源里，不是独立路径，不产生新风险，跟三、3.9 的 EM "Manage Favorites" 是完全不同的两个功能，别混。）

---

### 3.6 Replet Registry

**RepletRegistry 就是 Portal 仓库树（Repository）的文件夹本体：** `RepletRegistry` 是组织级仓库文件夹结构（`{orgId}/repository.xml`，三、3.5 附录一 A1）以及每个用户 "My Dashboard" 私有文件夹树（`portal/{orgId}/{user}/...`，A2）的后端存储对象——文件夹的增删、别名/描述（`FolderContext`）、收藏标记都落在这个对象上，物理文件也确实落在 DataSpace 桶里。

**机制说明：** `copyRepletRegistry(fromOrgId, toOrgId)`（`IdentityService.java:1247-1276`）——**copy/rename 两条路径都无条件调用一次**（`copyOrganizationInternal():257`），逐个 `addFolder()` 复制组织级文件夹、`copyFolderContextMap()` 复制别名/描述、对每个源组织用户调用 `repletRegistryManager.copyUser()` 复制其 "My Dashboard" 树（物理是 `dataSpace.copy("portal/{oOrgId}/{user}", "portal/{nOrgId}/{user}")`），最后 `newRegistry.save()` 落盘；从不删源。rename 分支末尾另外显式调用 `updateRepletRegistry(fromOrgId, null)`（`:1230-1245`）删源——但这个方法本身**只在内存里 `removeFolder()`，从未调用 `save()`**（见下方新发现）。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 9a | `copyRepletRegistry()` | 复制 folder+folderContextMap+逐用户，不删源 | `[已落地]` `OrgLifecycleRepletRegistryIntegrationTest#copy_copyRepletRegistry_foldersContextAndUserRegistryCopied_sourceUntouched`（真实 `RepletRegistryManager`+`DataSpace`，`securityEngine.getOrgUsers()` mock 返回受控用户列表；断言新组织拿到文件夹、子文件夹、别名，以及 alice 的 `portal/{orgId}/alice` 物理目录被复制；源组织文件夹和源用户目录均保持不变） |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 9b | copy 逻辑同 9a + 末尾 `updateRepletRegistry(fromOrgId, null)` 删源 | 源组织 registry 清空，新组织拿到完整副本 | `[已落地]` `OrgLifecycleRepletRegistryIntegrationTest#rename_copyThenUpdateRepletRegistry_newOrgGetsCopy_sourceRegistryCleared`（按真实编排顺序先 `copyRepletRegistry()` 后 `updateRepletRegistry(fromOrgId, null)`，断言新组织拿到文件夹、源组织内存态文件夹被移除） |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 9c | `updateRepletRegistry(orgId, null)` + `clearOrgCache(orgId)`（与 9b 删源逻辑完全相同，共享背景 delete 清单 `:615`/`:624`） | 无孤儿 | `[已落地]` `OrgLifecycleRepletRegistryIntegrationTest#delete_updateRepletRegistry_removalIsInMemoryOnly_notPersistedToDisk`——单独验证这个方法本身是否"无孤儿"时，发现它并不自洽（见下方新发现），钉住当前行为，不代表生产环境真的会出现孤儿（见下方分析） |

**新发现（2026-07-24，9c 落地时发现）：`updateRepletRegistry()` 的文件夹删除只改内存，从未落盘，但生产环境里被 DataSpace 层的物理文件搬迁/删除"顺手"掩盖了，实际不产生孤儿：** `RepletRegistry.removeFolder(folder, true, false, false)`（`updateRepletRegistry()` 内部调用，`saveBeforeEvent=false`）只从内存 `folderMap` 里摘掉这个文件夹，`removeFolder()`/`updateRepletRegistry()` 都没有在之后调用 `RepletRegistry.save()`。单独测试验证：种子一个真实落盘的文件夹 → 调用 `updateRepletRegistry(orgId, null)` → 内存态确认文件夹已移除 → 但紧接着 `clearOrgCache(orgId)` 把这个内存态对象逐出缓存（`RepletRegistryManager` 的 `ResourceCache`）→ 再次 `getRegistry(orgId)` 强制从磁盘重新加载 → **文件夹又回来了**，因为磁盘上的 `{orgId}/repository.xml` 从来没被重写过。

但这个"只改内存不落盘"的缺口在真实的组织删除/改名流程里**目前不会表现为孤儿**，原因是同一次编排里，`{orgId}/repository.xml` 这个物理文件本身早就被另一条独立机制处理掉了：
- **删除**：`syncIdentity()` 的 `ORGANIZATION` 分支里，`removeOrgScopedDataSpaceElements(oOrg)`（`:624`）先于 `updateRepletRegistry(orgID, null)`（`:625`）一行执行——`{orgId}/repository.xml` 匹配 `getOrgScopedPaths()` 规则 5（三、3.5 附录一 A1），这一步已经把整个文件删掉了，`updateRepletRegistry()` 的内存态删除操作了一个"文件已经不存在"的对象，落不落盘都无所谓。
- **改名**：`copyOrganizationInternal()` 里 `copyDataSpace(fromOrganization, newOrg, replace=true)`（`:146`）在方法最开始就已经把 `{fromOrgId}/repository.xml` **重命名**到 `{toOrgId}/repository.xml`（DataSpace 层的 `dataspace.rename()`，同样命中规则 5）；等到 `updateRepletRegistry(fromOrgId, null)`（`:292`）在方法末尾运行时，`{fromOrgId}` 路径下已经没有文件了，而且此前一行 `RepletRegistryManager.getInstance().clearOrgCache(fromOrgId)`（`:279`）已经把内存缓存清空——所以 `getRegistry(fromOrgId)` 这时候要么命中一个跟物理文件已经脱节的陈旧缓存对象，要么（缓存已清空的情况下）重新创建一个空白 registry，`getAllFolders()` 返回空数组，整个删除循环等于没有实际改变任何数据。

也就是说：`updateRepletRegistry()` 这个方法本身**不是自洽/独立正确的**——它假设自己是清理文件夹列表的唯一手段，但实际上真正生效的物理文件迁移/删除，都是 DataSpace 路径匹配机制在别处独立完成的，`updateRepletRegistry()` 更像是个附带的、当前顺序下基本是空跑的收尾步骤。目前没有观察到用户可见影响，也不构成已确认缺陷（跟 userformat.xml 不同，这不是"设计如此"，而是"顺序凑巧掩盖了一个本该但没有 `save()` 的缺口"）；如果未来有人以为 `updateRepletRegistry()` 单独调用就能完成清理（比如脱离当前的调用顺序、或复用到新的场景），会立刻暴露这个缺口。记录以防将来误用，暂不计入待产品确认清单（不影响当前用户）。

**测试覆盖：** 9a-9c 全部落地，`OrgLifecycleRepletRegistryIntegrationTest.java`（`community/core/src/test/java/inetsoft/sree/security/`），共 3 个 `@Test`，全部通过、无 `@Disabled`；`copyRepletRegistry()`/`updateRepletRegistry()` 都是 `IdentityService` 上的 public 方法，直接调用，不需要反射；真实 `RepletRegistryManager`（仅需 `DataSpace` 构造）+ 真实 `DataSpace` bean，`SecurityEngine` 用 Mockito mock（只有 `getOrgUsers()` 被 `copyRepletRegistry()` 用到）；额外注册了一个 mock `AnalyticRepository` bean，因为 `RepletRegistryManager` 首次加载任意组织的 registry 时会无条件解析这个 Spring bean（`RepletRegistryManager.java:473`），而 `BaseTestConfiguration` 本身不提供它。

---

### 3.7 Recycle Bin（回收站）

**EM 树节点结构备注：** EM 树上"Recycle Bin"其实是一个合成根节点，下挂两个子节点——本节讲的 `Repository`（viewsheet/folder 被删除后按原路径存放的地方）和 `Auto Saved Files`（Composer 编辑中途未保存的草稿，完全独立的另一套机制、另一个物理存储桶，详见三、3.4"Autosave — 管理/恢复层"，不要混着找）。两者只是在导航树上做了父子分组（`ContentRepositoryTreeService.getRecycleNodes()`，`:366-411`），仅安全开启、管理员身份下可见。

**机制说明：** `RecycleBin`（`inetsoft.web.RecycleBin`）是按组织分桶的独立 `KeyValueStorage<Entry>`（`storeID = orgId.toLowerCase() + "__recyclebin"`，`RecycleBin.java:355`），存放门户资源树里"移入回收站"（而非彻底删除）的条目元数据（原路径、原名称、原权限 `Permission`、原用户 `IdentityID` 等）。与 Autosave/Task Save 一样，完全独立于机制二，不经过 `BlobIndexedStorage`/`IndexedStorage`。组织生命周期只接了两个调用点，都在 `IdentityService` 里：
- copy/rename 共用 `copyStorages()` 里的 `recycleBin.copyStorageData(oOrg.getId(), nOrg.getId())`（`IdentityService.java:1137`）——纯整桶 KV 复制，不删源；rename 场景的删源同样是后面单独调用的 `removeStorages()`（见下方 delete 场景），跟 6a/6b 的结论一致。
- delete 走 `removeStorages()` 里的 `recycleBin.removeStorage(orgID)`（`:1119`，即共享背景 delete 清单 `:1101`）。

`RecycleBin.renameFolder(oldPath, newPath)`（`:116-137`）是门户内"文件夹改名"时更新回收站条目 `originalPath` 前缀用的，跟组织级 rename 无关，不在本节范围内。

**"回收站里的 dashboard" 辨析（2026-07-24 追加，容易被术语混淆）：** 回收站里存放的资产，代码里类型就是 **viewsheet**（`AssetEntry.Type.VIEWSHEET`），跟三、3.2 节 `DashboardRegistryManager` 管理的 "Dashboard" 是完全不同的两个概念——"dashboard" 这个词在这里只是两处 UI/命名层面的用法，不代表底层对象类型变了：① `Tool.MY_DASHBOARD = "My Dashboards"`——Portal 里每个用户私有文件夹的名字（历史上也叫 "My Reports"），文件夹里放的仍然是 viewsheet；② `RecycleUtils.getTypeLabel()` 只是把被回收的 viewsheet 类型在展示层标成 `"dashboard"` 文案。

**已修复（Bug #75759，PR #4382，2026-07-27）：** 私有（"My Dashboards"）viewsheet 被移入回收站后，组织 clone（copy）之后在 Repository 树的 Recycle Bin 节点下看不到。新增 `RecycleBin.migrateStorageData(oorg, norg)`（复制条目 + 重写 `originalUser`/`permission` + 显式持久化，`RecycleBin.java:317-347`），`IdentityService.copyStorages()` 改为调用它而不是原始 `copyStorageData()`。`RecycleBinOrgLifecycleTest` 保留 11a/11b 对底层 `copyStorageData()`/`migrateEntries()` 单独调用时的旧行为特征测试（作为对照组），新增 `migrateStorageData_reScopesOriginalUser_andPersists_sourceUntouched` 验证修复后 `copyStorages()` 实际调用的入口方法。

**Copy/Rename 场景**（同一方法，行为相同）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 11a | `copyStorageData(oId, id)`，底层原始方法（已不是 `copyStorages()` 实际调用的入口，见 11d） | 整桶流式复制，不删源、不重写 `originalUser`/`permission`——作为对照组，证明 11d 的重写不是没做事 | `[已落地]` `RecycleBinOrgLifecycleTest#copy_copyStorageData_wholeBucketCopied_sourceUntouched` |
| 11b | `migrateEntries()`，底层原始方法（同样已不是 `copyStorages()` 实际调用的入口，见 11d）：单独调用会重写内存对象但不持久化，权限授权部分依赖"当前组织"上下文是否凑巧匹配 | 单独调用 `migrateEntries()`：`originalUser`/`permission` 在内存对象上被重写，但不落盘；权限授权部分若调用时"当前组织"≠源组织则完全不重写 | `[已落地]`（文档性，说明底层方法本身的局限）`RecycleBinOrgLifecycleTest#migrateEntries_identityRewritten_permissionGrantsRewritten_butNeverPersisted`、`#migrateEntries_permissionGrantsSilentlyUnchanged_whenCurrentOrgDoesNotMatchGrantsOrg` |
| 11d | **已修复（Bug #75759）：** `migrateStorageData(oorg, norg)`——`copyStorages()` 现在实际调用的入口，组合了 11a 的复制 + 11b 的重写 + 显式持久化 + `runInOrgScope` 包裹权限读取 | 目标组织读到的条目 `originalUser` 正确指向新组织并已持久化，源组织条目不受影响 | `[已落地]` `RecycleBinOrgLifecycleTest#migrateStorageData_reScopesOriginalUser_andPersists_sourceUntouched` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 11c | `removeStorage(orgID)`（共享背景 delete 清单 `:1101`） | 整桶删除 | `[已落地]` `RecycleBinOrgLifecycleTest#delete_removeStorage_wholeBucketDeleted` |

**测试覆盖：** 11a-11d 全部落地，`RecycleBinOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/web/`），共 5 个 `@Test`，全部通过、无 `@Disabled`；11a/11b 直接调用 `RecycleBin` 的底层 public 方法（`copyStorageData()`/`migrateEntries()`）作为对照组，11d 调用修复后实际接入 `copyStorages()` 的 `migrateStorageData()`；"当前组织"切换复用 `OrgLifecycleScopedPropertiesIntegrationTest` 已验证过的 `ThreadContext.setContextPrincipal(SRPrincipal)` + `OrganizationContextHolder.setCurrentOrgId()` 手法。另外，Bug #75759 排查过程中新增了 1 个相关测试，但落在机制二的测试文件里而不是本节——`OrgLifecycleAssetContentMigrationTest#copy_userScopeRecycleBinFolder_childViewsheetAndFolderBothMigrate`（`community/core/src/test/java/inetsoft/uql/asset/sync/`），验证的是 `BlobIndexedStorage.copyStorageData()` 对 USER_SCOPE "Recycle Bin" `AssetFolder` 容器+子条目的迁移，不是 `RecycleBin`（本节机制）自己的 KV 桶迁移，因此没有算进本节场景表——记在这里防止将来去 3.6/3.7 之外的地方漏找。

---

### 3.8 EM Presentation 设置同步

三、3.5 附录一的 B16 提到 `portalthemes.xml` 本身不该被 `getOrgScopedPaths()` 匹配是正确设计——组织数据靠文件内部四个 `Map<orgId, ...>` 隔离，不靠文件路径。这四个 map 有没有正确同步是 DataSpace 普查带出的第二个新发现，但它的根因（map 没有跟着组织生命周期操作更新）跟 DataSpace 的路径匹配机制完全无关，只是 `portalthemes.xml` 这个文件本身恰好存在 DataSpace 桶里而已；同时它在 EM UI 上跟日期/时间格式、Dashboard 设置等一大批 SreeEnv org-scoped 属性同属一个 "Presentation" 设置页。按用户实际报 bug 时用的功能分类（"Presentation 页面东西丢了"）而不是按底层存储桶分类，单独成节，与 3.1-3.7 平级。

**本节实际是两个存储机制完全独立的方向，按下面两个小节分开讲：**
- **3.8.1** `PortalThemesManager`：Logo / Favicon / 欢迎页 / 登录横幅——落在 `portalthemes.xml` 内部四个 `Map<orgId,...>` 里，不写 `sree.properties`。
- **3.8.2** 其余常规 Presentation 属性（日期时间格式、Dashboard 设置等）——走 `SreeEnv.getProperty`/`setProperty`，最终写入 `sree.properties`。

#### 3.8.1 PortalThemesManager：Logo / Favicon / 欢迎页 / 登录横幅

**机制说明：** `PortalThemesManager`（`inetsoft.sree.portal.PortalThemesManager`）是单例，把品牌相关设置持久化在 DataSpace 根目录的单一全局文件 `portalthemes.xml`（`:566-597`，文件名可配）里，组织归属不靠文件路径隔离，而是文件内部四个独立的 `Map<orgId, ...>`：`cssEntries`/`logoEntries`/`faviconEntries`/`welcomePageEntries`（`:307-359`、`:1067`）。物理文件本体（`portal/{orgId}/{cssFile}`、`portal/{orgId}/{logoFile}`、`portal/{orgId}/{faviconFile}`）都落在 `dataSpace` 桶里且路径正确（见三、3.5 附录一 A5-A7），组织改名时会被 `getOrgScopedPaths()`/`updateOrgScopedDataSpace()` 正确搬到新路径——**问题不在文件本体，在这四个 map 有没有跟着物理文件的搬迁/复制/删除同步更新**。

**四个 map 待遇不一致，逐一核实（`grep` 全代码库 `LogoEntry|FaviconEntry|WelcomePage|CssEntr` 的调用点得到）：**

| Map | Copy（`copyOrganizationInternal(replace=false)`） | Rename（`copyOrganizationInternal(replace=true)`，含从 `setOrganizationInfo()`→`syncIdentity()` 触发的真实入口） | Delete（`syncIdentity()` 的 `ORGANIZATION` 分支） |
|---|---|---|---|
| `cssEntries` | **正确**：`AbstractEditableAuthenticationProvider.java:219-240` 无条件读源组织条目、复制物理文件、`manager.addCSSEntry(newOrgID, ...)` | **正确**：同一段逻辑对 rename 同样生效，加上 `manager.removeCSSEntry(fromOrgId)` 清掉旧条目 | **正确**：`IdentityService.java:627` `removeCSSEntry(orgID)` |
| `logoEntries` | **仍缺失**：`copyOrganizationInternal()` 无 `addLogoEntry`/`getLogoEntries` 调用；修复方案见 PR #4469（Issue #75800，**OPEN 未合并**） | **仍缺失**：旧 orgId 条目原地不动（指向已被物理搬走、不存在的旧路径），新 orgId 无对应条目 | **已修复**：`IdentityService.java:628` `removeLogoEntry(orgID)`（PR #4462，Issue #75842） |
| `faviconEntries` | **仍缺失**，同 `logoEntries`，同一个 PR #4469 覆盖 | **仍缺失**，同 `logoEntries` | **已修复**：`IdentityService.java:629` `removeFaviconEntry(orgID)`（PR #4462） |
| `welcomePageEntries` | **已修复**：`AbstractEditableAuthenticationProvider.java:242-245` 读取源组织 `getWelcomePage(fromOrgId)`、`clone()` 后 `setWelcomePage(newOrgID, ...)`（PR #4454，Issue #75841） | **已修复**：同一段代码 copy/rename 共用，另加 `:295` `removeWelcomePage(fromOrgId)` 清理源组织条目 | **已修复**：`IdentityService.java:630` `removeWelcomePage(orgID)`（PR #4462） |

**当前结论：** `cssEntries`/`welcomePageEntries` 三态（copy/rename/delete）均已修复；`logoEntries`/`faviconEntries` 仅 delete 分支修复（PR #4462 已合并），copy/rename 分支仍缺失——PR #4469（Issue #75800）覆盖这个缺口但目前是 **OPEN 未合并**状态，main 上 `AbstractEditableAuthenticationProvider.java` 里没有任何 `LogoEntry`/`FaviconEntry` 调用可以佐证。

**前端手动验证 Logo/Favicon 的前置条件（2026-07-28 补充，验证 12b/12c 时会用到）：** EM "Look and Feel" 页面的 "Default Logo"/"Default Favicon" 勾选框默认不显示，由 `LookAndFeelSettingsModel.customLogoEnabled`（`LookAndFeelService.java:58`：`SreeEnv.getBooleanProperty("portal.customLogo.enabled", false, !globalProperty)`）门控，默认值 `false`。要让这两个勾选框出现，必须先用 **host-org 的 site admin** 身份进 **EM → Settings → Properties**（`booleanProperties` 列表里的 `portal.customLogo.enabled`，`web/projects/em/src/app/settings/properties/property-settings-view/properties-tool.ts:52`）手动把它设成 `true`——而 "Settings > Properties" 本身是 `hiddenForMultiTenancy: true` 的 host-org 专属页面（见 Issue #75808 排查结论）。设置后该属性按全局默认值下发给没有 org-scoped 覆盖的普通组织，之后到目标组织的 "Look and Feel" 页面才能看到并上传自定义 Logo/Favicon。自定义效果要去 **Portal**（不是 EM）侧确认：Logo 显示在 Portal 顶部品牌区域，Favicon 显示在浏览器标签页图标（渲染入口是 `PortalThemeController`，与 EM 设置页面是两条独立路径）。

**"Login Banner" 也受影响，因为它跟 "Welcome Page" 共用同一个 `welcomePageEntries` map（2026-07-24 追加确认）：** `PresentationLoginBannerSettingsService.getModel()`/`setModel()`/`resetSettings()`（`PresentationLoginBannerSettingsService.java:34-112`）读写的都是同一个 `manager.getWelcomePage(orgId)`/`setWelcomePage(orgId, ...)`/`removeWelcomePage(orgId)` API、同一个 `PortalWelcomePage` 对象（`bannerType`/`banner` 字段），只是 EM UI 上呈现成"欢迎页"和"登录横幅"两个独立的设置面板。也就是说，`welcomePageEntries` 这一行的"缺失"结论同时覆盖了这两个前端功能——组织改名后，自定义登录横幅也会跟欢迎页一起静默变回默认值。

**根因（机制说明）：** 组织改名/复制/删除后 Logo/Favicon/欢迎页/登录横幅静默变回默认值或残留成孤儿——机制跟 Issue #75763（sreeUserData/History Bar）一致：物理文件被正确搬到新路径，但引用它的元数据（`PortalThemesManager` 的 map）没有跟着更新，读取时用新 orgId 查 map 查不到，只能退回默认。同一现象被不同测试人员分别报为 Issue #75800/#75841/#75842。

**修复现状：** `welcomePageEntries`（含登录横幅）的 copy/rename/delete 三态、`logoEntries`/`faviconEntries` 的 delete 已修复并合并（PR #4454/#4462）；`logoEntries`/`faviconEntries` 的 copy/rename 缺口仍未修复，修复方案在 PR #4469（Issue #75800）但尚未合并。

**Copy / Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 12a | `cssEntries` 在 copy/rename 下正确同步（对照组，证明"做对是可行的"） | 新组织拿到条目，rename 时旧组织条目被移除 | `[已落地]` `OrgLifecyclePortalBrandingTest#copy_cssEntrySynced_newOrgGetsMapEntry_sourceEntryNotRemoved`、`#rename_cssEntrySynced_newOrgGetsMapEntry_sourceEntryRemoved`（只断言 `addCSSEntry`/`removeCSSEntry` 这两个 map 调用，不重复断言物理 CSS 文件搬迁——那部分是 `copyDataSpace()` 的常规 org-scoped 路径迁移，已被 8a/8b/8d 覆盖） |
| 12b | copy 下：`welcomePageEntries` 已同步，`logoEntries`/`faviconEntries` 仍不同步 | `welcomePageEntries`（含登录横幅）新组织正确继承（已修复，PR #4454）；`logoEntries`/`faviconEntries` 新组织仍读不到源组织设置（PR #4469 未合并） | `[待补]`（`logoEntries`/`faviconEntries` 缺口 PR 未合并，暂不落地；`welcomePageEntries` 已修复但暂无对应测试） |
| 12c | rename 下：`welcomePageEntries` 已同步，`logoEntries`/`faviconEntries` 仍不同步 | `welcomePageEntries` 旧组织条目正确清理、新组织正确继承（已修复，PR #4454）；`logoEntries`/`faviconEntries` 旧组织条目原地残留、新组织无对应条目（PR #4469 未合并） | `[待补]`（`logoEntries`/`faviconEntries` 缺口 PR 未合并，暂不落地；`welcomePageEntries` 已修复但暂无对应测试） |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 12d | `cssEntries` 在 delete 下正确清理（对照组） | `removeCSSEntry(orgId)` 被调用，条目清除 | `[已落地]`——已在 12e 的同一个测试里覆盖：`IdentityServiceAutoSaveOrgLifecycleTest#delete_syncIdentity_organizationDelete_clearsAllPortalThemesManagerBrandingEntries` 断言的四个 `verify` 里第一个就是 `removeCSSEntry(orgId)`，不需要单独再建一个 case |
| 12e | `logoEntries`/`faviconEntries`/`welcomePageEntries` 在 delete 下均清理（已修复，PR #4462） | `removeLogoEntry`/`removeFaviconEntry`/`removeWelcomePage` 均被调用，三个 map 里对应 orgId 条目不再残留 | `[已落地]` `IdentityServiceAutoSaveOrgLifecycleTest#delete_syncIdentity_organizationDelete_clearsAllPortalThemesManagerBrandingEntries` |

**测试覆盖（3.8.1）：** 12a 已落地，新建 `OrgLifecyclePortalBrandingTest.java`（`inetsoft.sree.security`），harness 仿照同目录 `OrgLifecycleThemeOrchestrationTest`（`BaseTestConfiguration` + mock `PortalThemesManager` bean + `StubProvider` 驱动真实 `copyOrganization(...)` 入口），额外注册了一个真实 `RepletRegistryManager` bean（rename 分支源组织清理会调用它的 `clearOrgCache()`，`BaseTestConfiguration` 本身不提供）；12d/12e 均已落地，但 12d 是顺带断言，没有单独的 case，落在 `IdentityServiceAutoSaveOrgLifecycleTest.java`；12b/12c 仍待补（阻塞在 PR #4469 未合并）。

#### 3.8.2 SreeEnv org-scoped 属性（写入 `sree.properties`）

**机制说明：** EM "Presentation" 设置页除 Look and Feel/Welcome Page/Login Banner（3.8.1）外的其余子面板（日期时间格式、Dashboard 设置、PDF 生成、导出菜单等，见 `PresentationSettingsModel.java`）都经 `SreeEnv.getProperty(name, earlyLoaded, true)`/`setProperty(name, val, true)` 走 `"inetsoft.org." + orgID + "." + propertyName` 这个 key 格式（`PropertiesEngine.java:106-107`、`:206-220`、`:291-304`），最终落盘到 `sree.properties`；改名时由 `AbstractEditableAuthenticationProvider.copyScopedProperties()`/`clearScopedProperties()`（`:145`、`:433`、`:493-519`）负责迁移。

13a/13b 两层测试确认 `copyScopedProperties()` 本身、以及它被真实入口 `IdentityService.setOrganizationInfo()` 调用的完整链路都迁移正确——这个结论成立，但只覆盖"属性已经保存在正确组织 key 下"之后的迁移步骤，不覆盖保存那一步本身（见下方 13c 与已修复结论）。

**已修复（Issue #75769，PR #4380，2026-07-27）：** EM Presentation 属性改名后丢失、强制刷新恢复不了。`UserTreeService.editOrganization()` 保存属性的逻辑改为包进 `OrganizationManager.runInOrgScope(oldOrg.getId(), () -> {...})`，强制按被编辑组织的 ID 写入。

**场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 13a | `copyScopedProperties()` rename 迁移已保存的属性 | 正确从源组织 key 迁移到目标组织 key，源组织 key 回退全局默认值 | `[已落地]` `rename_copyScopedProperties_realRoundTrip_presentationStylePropertySurvives` |
| 13b | 完整 `IdentityService.setOrganizationInfo()` 入口驱动同一次迁移 | 同 13a，证明不只是孤立方法正确 | `[已落地]` `rename_realSetOrganizationInfoEntryPoint_presentationStylePropertySurvives` |
| 13c | **已修复（Issue #75769）**：`UserTreeService.editOrganization()` 保存 Presentation 属性时，操作者当前组织 ≠ 被编辑组织（如 Site Admin 场景） | 属性应写入被编辑组织（`oldOrg.getId()`）的 key，不应写进操作者自己当前组织的 key | `[已落地]` `editOrganization_actingOrgDiffersFromEditedOrg_propertySavedUnderEditedOrgNotActorsOrg`——反射调用 `UserTreeService.editOrganization()`，`SystemAdminService` 等无关协作者全 mock 放行，`actingOrgId` 与被编辑组织不同；回归验证过：临时还原到 PR #4380 之前的代码会导致此测试失败（属性两边 key 都读不到），确认测试确实钉住了这个修复，而不是套套逻辑 |

**测试覆盖（3.8.2）：** 13a-13c 全部落地，`OrgLifecycleScopedPropertiesIntegrationTest.java`，共 3 个 `@Test`，全部通过。

---

### 3.9 EM Favorites（"Manage Favorites"）

**机制说明：** `emFavorites` 是全局单一 `KeyValueStorage`（不按组织分桶），key 为 identity 字符串（`name~;~orgID`），由 `FavoritesService`（`inetsoft.web.admin.favorites`）统一封装读写；`FavoritesController`/`IdentityService`/`UserTreeService` 都通过它访问。跟 Portal 仓库树上的"星标收藏"（`AssetEntry.addFavoritesUser()`）是完全不同的两个功能，见三、3.5 附录末尾的辨析。

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 14a | `IdentityService.updateOrganizationMembers()` 内逐用户调用 `favoritesService.moveFavorites(oldKey, newKey)` | 每个成员的收藏列表从旧 identity key 搬到新 identity key | `[已落地]`（方法级）`FavoritesServiceTest#moveFavorites_presentEntry_movesToNewKey`——组织生命周期编排层面未见专属集成测试，方法本身的行为已覆盖 |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 14b | **已修复（Issue #75766，PR #4386，2026-07-27）**：`syncIdentity()` 组织删除分支调用 `favoritesService.removeFavorites(orgID)`（`IdentityService.java:631`）——此前是写好却未接入调用链的死代码 | 组织删除后该组织所有成员的 emFavorites 条目被清除，不残留孤儿 | `[已落地]`（方法级）`FavoritesServiceTest#removeFavorites_org_removesOnlyMatchingOrgKeys` |

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 14c | **已修复（Issue #75808，PR #4425，`0fbd94e8b`，2026-07-31 复核确认合并入 main）**：`copyStorages()` 未调用任何 `FavoritesService` 方法，组织 clone 后新组织成员的 EM 收藏列表全部丢失（不是"不继承"这么简单，是直接消失，源组织侧不受影响） | `copyUserToOrganization()` 改为委派新增的 `IdentityService.copyUserFavorites(fromID, toID, replace)`：clone（`replace=false`）调用新增的 `FavoritesService.copyFavorites()`，复制且不影响源组织；rename（`replace=true`）调用既有的 `moveFavorites()` | `[已落地]` `FavoritesServiceTest#copyFavorites_presentEntry_copiesToNewKeyWithoutRemovingSource`/`copyFavorites_noEntry_doesNothing`/`copyFavorites_storageFailure_doesNotThrow`、`IdentityServiceTest#copyUserFavorites_replaceFalse_copiesFavoritesLeavingSourceIntact`/`copyUserFavorites_replaceTrue_movesFavorites`、`AbstractEditableAuthenticationProviderTest#copyUserToOrganization_replaceTrue_movesFavoritesInsteadOfCopying`（均方法级单测，未走完整编排） |

**Issue #75808 风险排查（2026-07-28，修复前）：** 提出此 issue 时一并评估了"若直接继承收藏，host-org clone 出的新组织是否会因此暴露 host 专属页面（如 Properties 设置页）"这个顾虑。查证结论：EM 收藏本身只是客户端存的路径字符串，点击走的是普通 `router.navigateByUrl()`，跟正常导航命中同一套 `authorizationGuard` → `ComponentAuthorizationController.componentAvailable()` 校验；`hiddenForMultiTenancy` 组件（已核实 `settings/properties` 确实标了此项）的访问许可取决于 `OrganizationManager.isSiteAdmin(principal)`，跟收藏夹里有没有这条记录无关——继承一条指向 host 专属页面的收藏，非 site admin 用户点了也会被现有守卫拦下重定向，不构成访问泄漏。这个排查结论与实际落地的修复一致：未按"是否 host-org"分支特殊处理，直接继承。

**测试覆盖：** 14a/14b/14c 均为方法级单元测试（`FavoritesServiceTest.java`/`IdentityServiceTest.java`，`community/core/src/test/java/inetsoft/web/admin/favorites/`、`.../security/`），未走 `IdentityService.copyStorages()`/`syncIdentity()` 完整编排；14c 的调用点（`AbstractEditableAuthenticationProvider.copyUserToOrganization()`）另有专属单测覆盖调用参数是否正确（见上表）。

---

## 四、跨机制一致性（常设章节——持续收集此类发现，不是一次性历史记录）

本节收集"影响不止一个机制/资源类型"的发现。已归位到具体章节的内容不在这里重复，只做索引：

| 发现 | 归属章节 |
|---|---|
| 无锁执行顺序窗口（权限迁移先于资源迁移） | 共享背景 |
| 机制一/机制二 key 生成一致性 | 共享背景 |
| Autosave 依赖机制二重写嵌入内容 | 共享背景"跨机制依赖关系"表；三、3.4 |
| Data Source/VPM 依赖机制二通用兜底分支 | 共享背景"跨机制依赖关系"表；二 |
| Schedule Task 内容 vs Data Cycle 存在性分工 | 共享背景"跨机制依赖关系"表；三、3.3 |

**与资产改名管线（`claude/rename-transform.md`）的边界：** 机制二已确认不会触发 `RenameTransformHandler`/`AssetDependencyTransformer`（见二"边界断言"场景 2j）。两套机制是完全独立实现，没有共享代码——目前没有发现行为不一致的实锤，但任何一边未来的 bug 修复/覆盖面扩展都应检查另一边，这是长期架构风险，非当前缺陷。

**后续待办（范围外，不在本文档任务范围内，并入自 `2026-07-14-org-lifecycle-resource-integrity.md`）：** 用户手动改名单个资源（worksheet/query/data source/logical model 等，而非组织本身改名）时依赖是否被正确更新，是完全独立于本文档的第三套机制（`RenameTransformHandler` → `DependencyTransformer` → `AssetDependencyTransformer` 家族，见 `claude/rename-transform.md`），尚未启动系统性审计。现状：全代码库只有 `TaskAssetDependencyTransformerTest.java`（`community/core/src/test/java/inetsoft/uql/asset/sync/`）一个测试，只覆盖 Schedule Task 一种引用类型；其余至少 10 种 transformer（worksheet/viewsheet 引用、logical model、SQL/物理表、REST/tabular 数据源、OLAP cube、内嵌 viewsheet、超链接、脚本函数、dashboard、schedule task 各一个 transformer 类）零覆盖。真要启动时建议单开一份独立计划/测试矩阵（不占用本文档的场景编号），优先级上先做 `AssetWSDependencyTransformer`（worksheet/viewsheet 引用，最常见类型）。

**后续新发现如果跨越多个机制/资源类型，先记录在这里；能明确归位到某个具体章节的，随手搬过去，不要让这里膨胀成新的大杂烩。**

---

## 五、测试文件规划

| 测试类（均待建） | 覆盖场景 |
|---|---|
| `OrgLifecycleDependencyMigrationTest.java` | 一（1a-1c） |
| `OrgLifecycleAssetContentMigrationTest.java` | 二（2a-2j、10a-10c） |
| `OrgLifecycleThemeIntegrationTest.java` | 三、3.1（3a、3c、3d） |
| `OrgLifecycleDashboardMigrationTest.java` | 三、3.2（4a-4f） |
| `DataCycleManagerOrgLifecycleTest.java`（已建，`inetsoft.sree.internal`） | 三、3.3（5a-5f、5h 已落地，5g 待补；Bug #75756 已修复——5e 断言修复后行为，5a/5e 粒度不同不合并；7a 场景文档在此章节，但测试方法落在 `IdentityServiceAutoSaveOrgLifecycleTest.java`，见下一行） |
| `IdentityServiceAutoSaveOrgLifecycleTest.java`（已建，`inetsoft.web.admin.security`） | 三、3.4（6a/6b/6d 已落地，6c 不写 unit case 改走人工验证）+ 三、3.3（7a 已落地，代码邻接，测试未搬） |
| `AutoSaveServiceOrgLifecycleTest.java`（已建，`inetsoft.web`） | 三、3.4 Copy/Rename 场景（6g/6i 对应 Issue #75827，已随 PR #4459（2026-07-31 合并）修复并落地，见该节说明）+ 三、3.4 其他场景（6e 已写好但 `@Disabled` 待产品/后续确认；6h 对应 **Bug #75887**，同样 `@Disabled` 待处理，均临时启用手动验证过能跑通） |
| `ContentRepositoryTreeServiceTest.java`（已建，`inetsoft.web.admin.content.repository`，`ContentRepositoryTreeService` 通用测试文件，非仅 autosave 专用，后续该类的其它场景可继续加在这里） | 三、3.4（6f 已写好但 `@Disabled` 待确认，同上验证过能跑通；已确认与 Issue #75777（已修复，PR #4408）是独立问题，见该节；现有 `ContentRepositoryTreeControllerTest` 是另一个类——controller 层、`treeService` 全 mock，未触达这里测的真实逻辑） |
| `AutoSaveUtilsTest.java`（已建，`inetsoft.web`） | 三、3.4"用户级改名/删除"说明——`deleteUserAutoSaveFiles_deletesOnlyMatchingUserFiles`/`_oneFailedDelete_continuesWithOthers`/`_nullUser_noStorageAccess` 已落地；对应的改名方法 `updateUserAutoSaveFiles()` 无自动化测试，仅人工验证 |
| `OrgLifecycleDataSpaceIntegrationTest.java`（已建，`inetsoft.sree.security`） | 三、3.5（8a-8d 全部落地；`sreeUserData/` 孤儿缺陷 Issue #75763 已修复，两个特征测试已翻转为断言修复后行为） |
| `OrgLifecycleRepletRegistryIntegrationTest.java`（已建，`inetsoft.sree.security`） | 三、3.6（9a-9c 全部落地，另发现 `updateRepletRegistry()` 删除不落盘的新发现，见该节） |
| `RecycleBinOrgLifecycleTest.java`（已建，`inetsoft.web`） | 三、3.7（11a/11b/11c/11d 全部落地；Bug #75759 已修复——新增 `migrateStorageData()` 作为 `copyStorages()` 实际接入的入口，同时解决"从未调用"+"不落盘"+"当前组织上下文耦合"三处发现） |
| `OrgLifecyclePortalBrandingTest.java`（已建，`inetsoft.sree.security`） | 三、3.8.1（12a 已落地；12b/12c 待补，阻塞在 PR #4469 未合并；12d/12e 落在 `IdentityServiceAutoSaveOrgLifecycleTest.java`，未搬到本文件） |
| `OrgLifecycleScopedPropertiesIntegrationTest.java`（已建，`inetsoft.sree.security`） | 三、3.8.2（13a-13c 全部落地；Issue #75769 已修复——根因在 `UserTreeService.editOrganization()` 保存属性时用了操作者当前组织而非被编辑组织，13c 直接驱动该方法验证修复） |
| `FavoritesServiceTest.java`（已建，`inetsoft.web.admin.favorites`） | 三、3.9（14a/14b/14c 均为方法级单元测试；14c/Issue #75808 已修复，PR #4425，测试同时分布在 `IdentityServiceTest.java`/`AbstractEditableAuthenticationProviderTest.java`） |

机制一、机制二的测试类严格不共享基类/fixture 构造方法（见计划文档 Global Constraints）。

---

## 六、待产品确认的场景一览

| 场景 | 问题 | 归属章节 |
|---|---|---|
| 7a | Task Save 文件：copy 场景不复制是否符合预期 | 三、3.3 |
| — | 无锁执行顺序窗口是否会被生产并发场景实际触发 | 共享背景 |
| 5f/5h | Data Cycle 克隆场景：`getDataCycleIds()` 当前组织上下文耦合缺陷——单元测试 + 真实 Spring 装配核查都证实代码里确实这样写，但用户在真实运行环境里刻意避开"当前组织==源组织"这个前提后手动复测，克隆结果依然正确（含下游的 MV 调度——克隆后新组织下确实存在"DataCycle Task: cycle2"），两轮独立代码排查（含 enterprise/server 是否有覆盖实现）都没找到能解释这个矛盾的机制。原因未知，后续处理——留意是否是环境/构建版本差异，或是遗漏了某条实际调用路径 | 三、3.3 |
| Issue #75800（PR #4469，OPEN 未合并） | `logoEntries`/`faviconEntries` 在组织 copy/rename 下仍不同步——新组织不继承源组织的 Logo/Favicon，rename 后旧组织条目原地残留成孤儿；`welcomePageEntries`（含登录横幅）与三者的 delete 分支已修复，见三、3.8.1 | 三、3.8.1 |
| `[待确认]` | Legacy 报表部署导入的全局模板路径（`templates/{fname}`、`ReportFiles/{fname}` 等，`DeployManagerService.java:213-244`）完全不含 orgId——如果这条（前多租户时代的）导入功能在当前多租户环境下仍可达，会是跟 sreeUserData 同类的"该隔离没隔离"缺陷；未能确认该功能当前是否还真的可达，暂不计入已确认缺陷 | 三、3.5 附录 |
| `[设计如此，非缺陷]` | `userformat.xml`（数字/小数格式设置）所有组织共享同一份全局文件，从设计上就没有 per-org 隔离，不属于"改名/删除后丢失"这类生命周期缺陷；仅记录以防被误当成 sreeUserData 同类问题 | 三、3.5 附录 |
| `[待确认]` | **SSO org-claim 映射（SAML `ORGID_CLAIM`/OIDC `orgID.claim`，2026-07-28 已排查）**：这是全局 `SreeEnv` 属性（只存"去 assertion/token 里读哪个字段名"），登录时拿 claim 值跟当前 `provider.getOrganizationIDs()` 做精确字符串匹配，不是内部按 orgID 存的映射表——组织生命周期代码不需要碰它，也没有内部孤儿数据风险。但组织改名后，如果外部 IdP 那侧配置的 claim 值还是旧 org ID，该组织所有 SSO 用户会立即登录失败，需要人工去 IdP 侧手动更新，StyleBI 无法自动同步——这是外部配置漂移问题，不是内部代码缺陷；是否需要在 EM 改名流程里加提示/警告，需产品决定 | 范围外（enterprise） |
| `[待确认]` | `SUtil.getEditableAuthenticationProvider(SecurityProvider)`（单参重载，仅 enterprise `SecurityApiService` REST API 使用，EM UI 不走这条，2026-07-28 排查发现）在 provider 链里找到第一个 `EditableAuthenticationProvider` 就返回，不检查该 provider 是否真的拥有目标组织；如果部署配置了两个及以上 File provider 串联（无代码阻止），走这条重载的 REST API 调用可能操作到错误 provider 的组织副本。已有 identity-aware 的安全重载（`getEditableAuthenticationProvider(SecurityProvider, IdentityID, int)`）但这条路径未使用它。是否属于真实会出现的部署拓扑，需产品/配置确认 | 范围外（enterprise） |

> **已从本节移除（2026-07-23）**：原 4d/4e「dashboard 注册表 rename 内容不重写」——经人工复测，EM 改组织 ID 最终结果正确，属机制/测试隔离说明，**不按待产品确认的缺陷跟踪**（详见三、3.2「实现备注」）。
>
> **已从本节移除（2026-07-28，均已修复，不再需要产品确认）：**
> - **3d**（三、3.1，Issue #75739）——`removeTheme()` 已摘除孤儿 orgId。
> - **11b/11d**（三、3.7，Bug #75759，PR #4382）——`RecycleBin.migrateStorageData()` 已接入 `copyStorages()`，重写并持久化 `originalUser`/`permission`。
> - **Issue #75763**（三、3.5，PR #4381）——`getOrgScopedPaths()` 的 `sreeUserData/` 分支已改为按文件名后缀匹配。
> - **Issue #75766**（三、3.5 附录，PR #4386）——`FavoritesService.removeFavorites(orgID)` 已接入组织删除流程。
> - **Issue #75769**（三、3.8.2，PR #4380）——`UserTreeService.editOrganization()` 已用 `runInOrgScope(oldOrg.getId(), ...)` 修复属性误存进操作者当前组织的问题；残留的自动化测试缺口见 3.8.2 场景 13c（`[待补]`，不是产品确认问题，不留在本节）。
>
> **已从本节移除（2026-07-29，已修复，不再需要产品确认）：**
> - **Bug #75756**（三、3.3，PR #4414）——`migrateCycleInfo()` 已补上 `cycleInfo.setCreatedBy()`/`setLastModifiedBy()` 写回，`CycleInfo.createdBy`/`lastModifiedBy` 不再残留源组织身份；5e 断言的就是修复后行为。
> - **Issue #75777**（三、3.4，PR #4408）——已保存 sheet 的 autosave 草稿改为丢弃时进回收路径而非直接删除，"Auto Saved Files" 树上看不到已存在草稿的问题已修复；根因跟 6f 的用户枚举假设无关，两者已确认是独立问题，6f 继续按待确认跟踪。
>
> **已从本节移除（2026-07-31，已修复）：**
> - **Issue #75808**（三、3.9，PR #4425，`0fbd94e8b`）——`copyUserToOrganization()` 已委派新增的 `IdentityService.copyUserFavorites()`，clone 复制收藏（源组织不受影响）、rename 迁移收藏，不再全部丢失。
>
> **已从本节移除（2026-08-03，已修复）：**
> - **Issue #75827**（三、3.4，PR #4459，`9de296f91`）——`AutoSaveUtils.migrateAutoSaveFiles()` 拼接迁移后文件名时补回剥离掉的 `RECYCLE_PREFIX`（6i 根因：rename/clone 共用同一方法，两条路径现象一致，已回归验证）；同时方法签名改为显式 `storageOrgId` 参数、不再接收 `Principal`，从根上消除"principal 自带组织抢先于 `runInOrgScope` 生效"这条路径（6j 根因）。rename 调用点（`:153` 处）行为未变，未引入回归。
> - **Issue #75784**（三、3.1，PR #581，`ffd456e6`）——`CustomThemesImpl` 缓存的 KeyValueStorage 句柄被共享 LRU 驱逐后永久失效已修复：`init()` 改为 `themesKvStore != null && !themesKvStore.isClosed()`，句柄被驱逐关闭后自动重新获取，不再永久返回空主题列表；回归测试 `CustomThemesImplTest#init_cachedStoreClosed_reFetchesStorage` 验证。
> - **Issue #75841**（三、3.8.1，PR #4454）——`welcomePageEntries`（含登录横幅）copy/rename 下不继承的缺口已修复：`copyOrganizationInternal()` 新增读取源组织 `getWelcomePage()`、clone 后 `setWelcomePage(newOrgID, ...)`，rename 分支另清理源组织条目。
> - **Issue #75842**（三、3.8.1，PR #4462）——`logoEntries`/`faviconEntries`/`welcomePageEntries` 在组织 delete 下不清理的缺口已修复：`IdentityService.java:628-630` 新增 `removeLogoEntry`/`removeFaviconEntry`/`removeWelcomePage` 调用；回归测试 `IdentityServiceAutoSaveOrgLifecycleTest#delete_syncIdentity_organizationDelete_clearsAllPortalThemesManagerBrandingEntries` 验证。`logoEntries`/`faviconEntries` 的 copy/rename 缺口不在这个 PR 范围内，仍未修复，见三、3.8.1 与本节 Issue #75800（PR #4469）。
>
> **已排查、无风险、不需要产品确认（2026-07-28）：** Hosted License 剩余时长/宽限期——状态只存在 Ignite 集群内存 map（`"inetsoft.enterprise.license.activeHostedSessions"`），key 是 `(licenseKey, instanceId, principalId)` 按用户会话，不按 orgID；真正的时长数据源是外部计费服务器，StyleBI 本地只是登录期间的短期缓存，每 3-5 分钟自己跟服务器对账一次。全文搜索确认 `copyOrganizationInternal()`/`syncIdentity()`/`copyStorages()`/`removeStorages()` 里零处引用这些 license 类——不存在需要迁移的、按 org 持久化的记录，组织删除/改名/复制不会产生孤儿或丢失。
