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

### 已确认的生产风险

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
| 3c | 编排层集成：`copyOrganizationInternal()` 完整流程下主题三态（`SreeEnv`/`CustomTheme.organizations`/`Organization.theme`）是否一致 | 组织自有主题分支应一致；全局主题被选中分支 `Organization.theme` 预期为空（已知问题，测试记录当前行为不假定应修复） | `[已落地]` `OrgLifecycleThemeOrchestrationTest#copy_orgOwnedSelectedTheme_threeStatesAgree`、`copy_globalThemeSelectedByFromOrg_organizationThemeFieldStaysNullDespiteSreeEnvAndOrganizationsListAgreeing`（`CustomThemesManager` 用 `mockStatic` 而非真实 bean 驱动——见该测试类头注释：community 下的 `CustomThemesImpl` 是纯空实现，`getCustomThemes()`/`setCustomThemes()` 不落地任何状态，且全代码库不存在 `getOrgSelectedTheme(String orgId)` 这个重载，真实 bean 无法满足本场景需要断言的状态，因此用 mock 捕获 `setOrgSelectedTheme(id, orgId)` 调用参数代替"SreeEnv 侧指针"的真实读取；除主题管理器本身外，`DataSpace`/`PortalThemesManager`/`OrganizationManager.runInOrgScope`/`ScheduleManager` 均走真实 Spring 上下文） |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 3d | 全局共享主题被组织选用后删除该组织，主题的 `organizations` 列表是否残留该 orgId | 已修复：`removeTheme()` 现在会摘除该 orgId，不再残留 | `[已落地]`——`IdentityThemeServiceTest#removeTheme_globalThemeStillListsDeletedOrg_organizationsEntryIsStripped` |

**Issue #75739（已修复）：** `IdentityThemeService.removeTheme(orgID)` 现在会遍历全局主题、摘除其 `organizations` 列表里的已删除 orgID，与 rename 路径（`IdentityService.updateCustomThemeOrganization()`）保持对称，不再残留孤儿 orgID。

### 已确认的生产风险

- **`CustomThemesImpl` 缓存的 KeyValueStorage 句柄被共享 LRU 驱逐后永久失效，且不会自愈（Issue #75735）**（实测复现，2026-07-23）：enterprise `CustomThemesImpl.init()`（`enterprise/src/main/java/inetsoft/enterprise/theme/CustomThemesImpl.java:47-55`）把 `themesKvStore` 缓存在实例字段里，`if(themesKvStore != null) return` 保证只在这个 Spring 单例的生命周期内获取一次 `keyValueStorageManager.getStorage("CustomThemes")`，之后永远复用同一个引用。这个 bucket 跟系统里所有其他 org 相关的 KeyValueStorage 消费者（各 org 的依赖反向索引、Dashboard 偏好等）共用同一个 `KeyValueStorageManager`（`community/core/src/main/java/inetsoft/storage/KeyValueStorageManager.java:120-135`）的 `MAX_SIZE=50` Caffeine LRU 缓存；持续 clone 组织会不断在这个共享缓存里开新的 per-org 桶，一旦累计不同 ID 超过 50 个，`"CustomThemes"` 就可能被判定为最近最少用而被驱逐，`removalListener` 随即调用 `storage.close()`。`LocalKeyValueStorage.close()`（`community/core/src/main/java/inetsoft/storage/LocalKeyValueStorage.java:170-178`）只置位 `isClosed`，不清空底层 `map`（集群里真正持久化数据的引用还在），但 `stream()`/`keys()` 一看 `isClosed==true` 就静默返回空流；`CustomThemesManager.getCustomThemes()` 正是靠 `themesKvStore.stream()` 读取全部主题，一旦踩中这次驱逐，从此这次调用永远返回空集合——表现为所有组织的主题列表同时消失，且与具体切换到哪个 org 无关（这是进程级单例状态）。写操作（`put`/`replaceAll`）走 `cluster.submit(id, task)`，不检查 `isClosed`，因此新建主题时的写入依旧真实落地到集群持久化存储——这就是"UI 上主题消失，但 Storage 里数据还在"的原因；随后再建主题需要读回刚写入的记录，走的是同一条坏掉的读路径，读不到就报 404（`claude/theme.md` 里 `ThemeURLConnection.java:65` "Theme with ID ... not found" 日志路径）。
  - **对比机制一同类风险，这里更严重**：`DependencyStorageService`（见"一、机制一"节"已确认的生产风险"）虽然共享同一个 LRU 缓存，但它的方法每次调用都会重新按 id 查找 storage，下一次调用能借着 `KeyValueStorageManager.get()`（`:104-118`）"发现引用已关闭就重建"的自愈逻辑恢复；`CustomThemesImpl.init()` 把结果焊死在实例字段里，永远不会再走查找路径，**不重启进程就永久损坏**，不是narrow race window。
  - **复现稳定性取决于环境累计状态，不是代码里写死的次数**：只要这个 JVM 进程此前已经积累了足够多不同的 per-org KeyValueStorage ID（来自其它 org 的历史操作），再 clone 一两次组织就会把计数顶过 50 并稳定触发；在一个刚启动、org 数量少的全新环境里则需要更多次操作才会偶然踩中——这也是同一个缺陷在不同环境下"偶发两次不再复现"和"稳定复现"两种报告并存的原因。
  - **修复可行性评估（暂不动手，先记录判断）**：`init()` 不该只在"字段为空"时获取一次，应该每次访问都重新调用 `keyValueStorageManager.getStorage("CustomThemes")`（该方法内部的 `get()` 本身就带自愈检查），或者干脆去掉这个实例字段缓存。改动只影响 `CustomThemesImpl` 这一个类的 `init()`/`getCustomThemes()`/`setCustomThemes()`，不改接口、不改调用方签名，回归风险低。

**测试覆盖：** `copyThemes()` 方法级已覆盖（3a）；编排层集成已覆盖（3c，`OrgLifecycleThemeOrchestrationTest`）；delete 路径（3d）回归测试已启用并通过。上述 KeyValueStorage 共享 LRU 风险目前只有实测复现记录，尚未落地自动化回归测试（复现依赖 JVM 进程累计的存储 ID 数量，同 `DependencyStorageService` 场景 1g 的局限一样难以在单元测试里确定性触发）。

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

**分工说明（避免与机制二混淆）：** Schedule Task 资产本身的**内容**（比如定时导出/推送某个 Viewsheet 时，Action 里对 `viewsheet` 属性的引用）走的是**机制二**的 `MigrateScheduleTask`（见二）。本节只覆盖 **Data Cycle**（一种生成"预生成任务"的业务对象）自身**存在性**的迁移，走的是完全独立的 `DataCycleManager`。

**机制说明：** `migrateDataCycles(Organization oorg, Organization norg, boolean replace)`（`DataCycleManager.java:863-887`）：`replace=true` 才删除源 entry，两条路径都刷新 `pregeneratedTasksMap` 缓存。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5a | `migrateDataCycles(oorg, norg, replace=false)`，从源组织自身上下文发起（见下方 5f 的前提说明） | 复制 `DataCycleAsset`（`orgId` 字段正确改写），不删源；`CycleInfo` 身份字段**理论上**应同步改写，实测确认并未（见 5e） | `[已落地]` `DataCycleManagerOrgLifecycleTest#copy_migrateDataCycles_copiesAssetAndLeavesSourceUntouched` |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5b | `replace=true`，从源组织自身上下文发起——**这不是测试图省事的简化前提，而是 EM 前端下的真实调用形状**：见下方"可达性分析"，改名必须先在右上角切到被改名的组织，两者天然一致 | 同上 + 删除源 entry，无孤儿 | `[已落地]` `DataCycleManagerOrgLifecycleTest#rename_migrateDataCycles_replaceTrue_copiesAssetAndRemovesSource` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5c | `clearDataCycles(orgId)`（共享背景 delete 清单 `:612`），从被删组织自身上下文发起 | 该组织所有 `DataCycleId` 精确清除，无残留 | `[已落地]` `DataCycleManagerOrgLifecycleTest#delete_clearDataCycles_removesAllCyclesForOrg` |
| 5d | 普通 Schedule Task（非 Data Cycle）Delete：靠 `indexedStorage.removeStorage(orgId)` 整桶清理 | 断言删除后确实不可读，防止未来 `IndexedStorage` 改分桶策略后失去覆盖 | `[已落地]` `DataCycleManagerOrgLifecycleTest#delete_indexedStorageRemoveStorage_wholeOrgBucketGone`——低优先级回归防护 |

**已确认缺陷 1（Bug #75756）：** `migrateCycleInfo()`（`DataCycleManager.java:937-957`）——`identityID.setOrgID(norg.getId())` 只修改局部变量，从未调用 `cycleInfo.setCreatedBy()`/`setLastModifiedBy()` 写回。Copy/Rename 后 `CycleInfo.createdBy`/`lastModifiedBy` 仍指向源组织用户身份，跟 `DataCycleAsset.orgId`（同一次迁移里另一个字段，在 `migrateDataCycles()` 里直接 `asset.setOrgId(norg.getId())` 正确改写）行为不一致。

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5e | 上述缺陷的回归基线 | 断言当前（错误）行为存在，作为后续修复基线：`createdBy`/`lastModifiedBy` 保持迁移前的值，`orgId` 字段则正确改写 | `[已落地]` `DataCycleManagerOrgLifecycleTest#migrateCycleInfo_createdByAndModifiedBy_remainStaleAfterMigration` |

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

**测试覆盖：** 7 个场景（5a-5f、5h）已落地，`DataCycleManagerOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/sree/internal/`），共 7 个 `@Test`，全部通过；5g（`clearDataCycles()` 的同类场景）待补。

---

### 3.4 Autosave 文件 / Task Save 文件

**Autosave blob 本体 — Copy/Rename 场景**（同一方法，行为相同）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6a | `updateBlobStorageName("__autoSave",...,copy=true)`，copy 调用（`copyStorages(replace=false)`） | 整桶流式复制，不删源 | `[已落地]`——`copy_copyStorages_autoSave_copiesBlobAndLeavesSourceUntouched` |
| 6b | 同一方法，rename 调用（`copyStorages(replace=true)`） | 同 copy 行为（删源另在 rename 清理块生效）——`copyStorages()` 内部对 `updateBlobStorageName("__autoSave",...)` 的 `copy` 实参是硬编码 `true`（`IdentityService.java:1145`），并不随外层 `rename`/`replace` 参数变化；确认 `copyStorages()` 本身单独调用时绝不删源 | `[已落地]`——`rename_copyStorages_autoSave_sourceSurvivesUntilSeparateRemoveStoragesCall` |

**Autosave 嵌入内容修正**（依赖机制二，见共享背景"跨机制依赖关系"）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6c | `AutoSaveUtils.migrateAutoSaveFiles()`，通过 `IdentityService.updateAutoSaveFiles()`（`AbstractEditableAuthenticationProvider.java:153` rename 分支直接调用、`:262` copy 分支包一层 `runInOrgScope(newOrgId,...)`） | 两种上下文下，嵌入的 viewsheet/worksheet 内容均正确重写（依赖机制二的 `MigrateViewsheetTask`/`MigrateWorksheetTask`） | `[待补]`——需要真实 viewsheet/worksheet autosave XML 素材 + `MigrateViewsheetTask`/`MigrateWorksheetTask` 依赖，落地成本明显高于 6a/6b/6d/7a，本轮暂缓 |

**Autosave — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6d | `removeBlobStorage("__autoSave",...)`（共享背景 delete 清单 `:1105-1111`，即 `IdentityService.removeStorages()`） | 整桶删除 | `[已落地]`——`delete_removeStorages_autoSave_wholeBucketGone` |

**Task Save 文件 — Rename 场景**（copy 不涉及）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 7a | `updateTaskSaveFiles()` → `externalStorageService.renameFolder()`，仅 rename 调用 | 组织 id 不同才调用 `renameFolder(oorg, norg)`；id 相同时直接跳过（`Tool.equals(oorg,norg)` 早退） | `[已落地]`（方法自身行为）——`updateTaskSaveFiles_orgsDiffer_renamesExternalStorageFolder`、`updateTaskSaveFiles_sameOrgId_noOp`；copy 路径确实不调用这一事实来自直接通读 `copyOrganizationInternal()`（`updateTaskSaveFiles()` 只出现在 `:154`，位于 `if(replace)` 分支内，copy 分支没有对应调用），未额外走 `copyOrganization()` 真实入口重新验证。`[待确认]`——copy 场景不复制 Task Save 文件是否符合预期，仍待产品/业务确认 |

**测试覆盖：** 4 个场景（6a/6b/6d/7a）已落地，`IdentityServiceAutoSaveOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/web/admin/security/`），共 5 个 `@Test`（7a 拆成两个方法），全部通过；6c（embedded 内容重写）待补。

---

### 3.5 Data Space 文件

**机制说明：** `copyDataSpace(fromOrg, toOrg, replace)`（`AbstractEditableAuthenticationProvider.java:444-491`）：`replace=true` 用 `dataspace.rename()`，`replace=false` 用 `dataspace.copy()`；若源是默认组织，copy 分支额外复制 MV 文件系统/Block 系统元数据。`setOrganizationInfo()` 入口另有独立实现 `updateOrgScopedDataSpace()`。

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
| 8c | `removeOrgScopedDataSpaceElements()` 按路径枚举删除（共享背景 delete 清单 `:614`） | 无孤儿路径 | `[已落地]` `OrgLifecycleDataSpaceIntegrationTest#delete_removeOrgScopedDataSpaceElements_allKnownPathShapes_noOrphans`——`getOrgScopedPaths()` 六个 OR 分支中的五个（`portal/{orgId}` 精确匹配、`portal/{orgId}/...` 前缀、`{orgId}__...` 前缀、`{orgId}` 精确匹配、`{orgId}/...` 前缀）逐一用独立 orgId 种子验证，删除后均无孤儿。第六个分支（`sreeUserData/...`）行为不同，见下方"已确认缺陷（Issue #75763）"——`delete_removeOrgScopedDataSpaceElements_sreeUserDataFile_survivesAsOrphan` 单独钉住这个反例，不算在"无孤儿"结论内 |

**已确认缺陷（Issue #75763，8c 落地时新发现，不在原场景清单里；已通过 EM 前端实测复现，2026-07-24）：** `DataSpace.getOrgScopedPaths()`（`DataSpace.java:404-410`）第六个 OR 分支——匹配 `sreeUserData/` 下的 per-user `UserEnv` 文件——实际上是死代码，从未按预期工作。真实的 per-user 文件名是 `{name}_{orgId}.xml`（`UserEnv.java:237`），文件名里不含 `IdentityID.KEY_DELIMITER`（`"~;~"`）。`IdentityID.getIdentityIDFromKey()`（`IdentityID.java:88-113`）只有在 key 里找到这个分隔符时才会从字符串本身解析出 orgID（`:96-105`）；找不到分隔符时（真实文件名的情况）会落到 `:106-112` 的 else 分支，**直接返回调用线程当前的 principal/`OrganizationManager` 组织上下文，完全不看传入的路径字符串**。因此 `getOrgScopedPaths()` 里 `Tool.equals(IdentityID.getIdentityIDFromKey(p).getOrgID(), oorg.getId() + ".xml")` 这个比较——把结果去跟 `"{targetOrgId}.xml"`这个带字面量后缀的字符串比——在真实数据下几乎不可能为真（除非某个组织的 ID 恰好就是字面量 `"{targetOrgId}.xml"`）。净效果：`sreeUserData/` 下的 per-user 文件从未被 `getOrgScopedPaths()` 匹配到：

- **改名（已实测复现）**：`copyDataSpace()`/`updateOrgScopedDataSpace()` 都靠 `getOrgScopedPaths()` 决定搬哪些文件，`{name}_{oldOrgId}.xml` 因此永远不会被搬到 `{name}_{newOrgId}.xml`。改名后任何 `UserEnv.getProperty()` 读取（此时按新 org ID 解析身份）都会落空、退回属性默认值——前端可见症状：Portal Preferences 对话框的 History Bar 开关（`PreferencesDialogController.java:60,135`）等 `UserEnv` 支持的用户级设置在组织改名后静默重置为系统默认值。复现步骤：用户登录后在 Preferences 里把 History Bar 改成跟系统默认值相反的状态并保存 → 站点管理员在 EM 里把该组织的 Organization ID（不是显示名）改掉 → 用户用新 ID 重新登录、再打开 Preferences → 之前设置的值消失，变回默认值。已按此步骤实测确认。旧文件本身留在旧 org ID 路径下成为孤儿。代码层面的机制由 `OrgLifecycleDataSpaceIntegrationTest#rename_sreeUserDataFile_neitherEntryPointRelocatesIt_currentBuggyBehaviorBaseline` 钉住（对 `copyDataSpace(replace=true)`/`updateOrgScopedDataSpace()` 两条独立入口分别验证：源文件都原地不动，新组织都没收到文件）——**有意不加 `@Disabled`**：这套测试对"已确认但未修复的缺陷"的约定是主动跑、断言当前（错误）行为，这样将来行为一变（不管是修复还是改坏）测试会立刻失败，逼着开发者更新它，而不是被静默遗忘；`@Disabled` 在本文档的测试里只留给测试基础设施本身不稳定的场景（如 4c 的 flaky 标注），不用于"这是个已知但还没修的 bug"。
- **删除**：`removeOrgScopedDataSpaceElements()` 同样靠 `getOrgScopedPaths()` 决定删哪些文件，这些文件永远删不到，组织删除后永久残留成孤儿存储——测试仅钉住当前行为（`delete_removeOrgScopedDataSpaceElements_sreeUserDataFile_survivesAsOrphan`），不假定应该怎么修。
- **复制**：新组织同样不会拿到源组织现有用户的 `sreeUserData` 文件（同一个 `getOrgScopedPaths()` 缺口）。

受影响的不只是 History Bar——任何经 `UserEnv.setProperty()`/`getProperty()` 持久化的用户级设置都受同样的改名丢失/删除孤儿影响，包括：`locale`（用户保存的语言偏好）、`annotation`（viewsheet 注解显示记忆）、`email`（分享/定时邮件对话框的自动填充缓存）、`vswizard.dialog.status`/`wswizard.dialog.status`（Composer 向导对话框开关状态记忆）、单条 repository 条目的"已读"标记（`RepositoryTreeController.java:508`）、worksheet 变量值记忆（`VariableAssemblyDialogService.java`）。

**测试覆盖：** 8a-8d 全部落地，`OrgLifecycleDataSpaceIntegrationTest.java`（`community/core/src/test/java/inetsoft/sree/security/`，与 8a 所在的 `AbstractEditableAuthenticationProviderStaticDepTest` 同包，复用其包内可见的 `StubProvider` 而非重复定义），共 5 个 `@Test`（含 Issue #75763 的删除路径 + 改名路径两个特征测试），全部通过、无 `@Disabled`；均直接调用 `copyDataSpace()`/`updateOrgScopedDataSpace()`/`removeOrgScopedDataSpaceElements()` 本身（反射 + 真实 `DataSpace` bean），不经过 `copyOrganizationInternal()`/`setOrganizationInfo()` 完整编排，因此不需要 `PortalThemesManager`/`DashboardRegistryManager` 之类的额外 bean 覆盖。

### 3.5 附录：DataSpace 资源盘点（2026-07-24 全量普查）

sreeUserData 这个缺陷找到后带出了一个自然的问题：`getOrgScopedPaths()` 六条匹配规则会不会漏掉别的资源？为回答这个问题，把 `community/core` 里所有经过 `DataSpace`（以及概念上常被一起提起、但物理上是独立 blob/KV 桶的"其它存储"）读写的资源类型过了一遍。结论：**`sreeUserData` 是目前找到的唯一一个"路径确实落在 `dataSpace` 桶里、但六条规则都没接住"的同类缺陷**；额外挖到两个性质不同但同样真实的问题（`PortalThemesManager` 品牌元数据、`emFavorites` 删除路径死代码），分别在下方独立说明。

**A. 落在 `dataSpace` 桶里、被 `getOrgScopedPaths()` 正确匹配（隔离且生效）：**

| # | 资源 | 路径形态（示例） | 匹配的规则 | 结论 |
|---|---|---|---|---|
| A1 | 组织级 Replet Registry | `{orgId}/repository.xml`（`RepletRegistry.java:244-250`） | 规则 5 `{orgId}/` | 正常 |
| A2 | 用户 "My Dashboard" Replet Registry | `portal/{orgId}/{user}/my dashboard/repository.xml`（`RepletRegistry.java:876-965`） | 规则 2 | 正常 |
| A3 | Dashboard Registry（组织级/用户级） | `portal/{orgId}/dashboard-registry.xml`、`portal/{orgId}/{user}/dashboard-registry.xml` | 规则 2 | 正常，已在三、3.2 详细覆盖 |
| A4 | 自定义 Shape 库（多租户模式） | `portal/{orgId}/shapes/{file}`（`ImageShapes.java:112-117`） | 规则 2 | 正常 |
| A5 | 按组织自定义 CSS | `portal/{orgId}/{cssFileName}`（`LookAndFeelService.java:611-620`） | 规则 2 | 正常——文件路径本身隔离生效，另外还有 `PortalThemesManager.cssEntries` 这份专属映射做二次保险（见下方"B16"，`cssEntries` 是唯一被 `copyOrganizationInternal()` 正确维护的品牌映射） |
| A6 | 按组织 Logo 文件本体 | `portal/{orgId}/{logoFile}`（`LookAndFeelService.java:495-527`） | 规则 2 | 文件本体隔离正常，**但引用它的 `PortalThemesManager.logoEntries` 映射不同步——见三、3.8** |
| A7 | 按组织 Favicon 文件本体 | `portal/{orgId}/{faviconFile}`（`LookAndFeelService.java:545-577`） | 规则 2 | 同 A6，**`faviconEntries` 映射不同步** |
| A8 | MV FileSystem 索引 | `{orgId}/fs.xml`（`AbstractFileSystem.java`） | 规则 5 | 正常，已在场景 8b 覆盖 |
| A9 | MV BlockSystem 索引 | `{orgId}/bs.xml`（`DefaultBlockSystem.java`） | 规则 5 | 正常，已在场景 8b 覆盖 |
| A10 | Legacy 报表部署导入：用户级模板 | `portal/{orgId}/{user}/my dashboard/{fname}`（`DeployManagerService.java:222-236`） | 规则 2 | 正常 |
| A11 | 每用户 `UserEnv` 偏好设置 | `sreeUserData/{name}_{orgId}.xml`（`UserEnv.java:237`） | 规则 6（**死代码，实际不匹配**） | **已确认缺陷，Issue #75763，见上方** |

**B. 落在 `dataSpace` 桶里、但设计上本来就不按组织隔离（非缺陷，如实记录）：**

| # | 资源 | 路径（示例） | 说明 |
|---|---|---|---|
| B12 | 全局默认 CSS | `portal/format.css`（`CSSDictionary.java:115-117`，`defaults.properties:278`） | 单一安装级默认值，不区分组织，设计如此 |
| B13 | 全局 Shape 库（单租户 fallback） | `portal/shapes/{file}`（`ImageShapes.java:119-121`） | 仅在关闭多租户时生效，设计如此 |
| B14 | `userformat.xml`（数字/小数格式） | `userformat.xml`（dir=null/home，`ExtendedDecimalFormat.java:388-415`、`LookAndFeelService.java:177-192`） | **设计如此，非缺陷**——所有组织共享同一份文件；即使是"按组织"的 Look and Feel 页面单独设置数字格式，写的也是这同一个文件。跟"改名/删除后丢失"性质不同，是"从一开始就没有做 per-org 隔离"，多租户下的实际影响是组织之间会互相覆盖对方的数字格式设置——这是设计取舍，不按缺陷跟踪，仅记录以防将来被误当成 sreeUserData 同类问题 |
| B15 | Legacy Data Cycle 配置 `cycle.xml` | DataSpace 根目录（`DataCycleManager.java:477-499`） | 一次性历史迁移进 `IndexedStorage` 后废弃，之后不再产生持续风险 |
| B16 | `portalthemes.xml`（`PortalThemesManager` 自身注册表） | DataSpace 根目录（`PortalThemesManager.java:566-597`，文件名可配，默认 `portalthemes.xml`） | 文件本身不该被 `getOrgScopedPaths()` 匹配——这是**正确设计**：组织数据不是靠文件路径隔离，而是在文件内部以 `cssEntries`/`logoEntries`/`faviconEntries`/`welcomePageEntries` 四个 `Map<orgId, ...>` 存的。但这四个 map 在组织生命周期操作下是否被正确同步，四个 map 待遇不一致，且这属于 EM "Presentation" 页面而非 DataSpace 路径匹配问题——**详见三、3.8（独立成节）** |
| B17 | 全局字体库 | `fonts/{file}`（`FontManager.java`） | 全局，设计如此 |
| B18 | 自定义 head 标签注入资源 | `web-assets/**`（`HomePageController.java`） | 全局，设计如此 |
| B19 | 图片选择器目录 | `images/**`（管理员配置的 `html.image.directory`） | 全局共享图库，设计如此 |
| B20 | `[待确认]` Legacy 报表部署导入：全局模板 | `templates/{fname}`、`templates/subreports/{fname}`、`ReportFiles/{fname}`（`DeployManagerService.java:213-244`） | 路径里完全不含 orgId，如果这个（前多租户时代的）导入功能在当前多租户环境下仍然可达，会是跟 sreeUserData 同类的"该隔离但没隔离"缺陷；但没能确认这条功能在当前版本是否还真的可达，暂不计入已确认缺陷，留作后续排查项（见六） |

**C. 完全不在 `dataSpace` 桶里的资源（独立 BlobStorage/KeyValueStorage 桶，`getOrgScopedPaths()` 物理上管不到，各自有专属清理机制，不在本节重复展开）：**

`__mv`/`__mvws`/`__mvBlock`/`__pdata`/`__library`/`__tableCacheStore`/`__autoSave`/`__recyclebin`/`__dependencyStorage`/`__dashboards`/`{orgId}__indexedStorage` 均按 org 分桶，`IdentityService.copyStorages()`/`removeStorages()` 对每个桶单独调用专属的 remove/copy 方法（见共享背景 delete 清单、一、二、三、3.2/3.4/3.6/3.7）——这些不是本轮普查的新发现，只做索引。本轮普查在这一类里新确认一个问题：

- **EM "Manage Favorites"（Issue #75766，全局单一 `emFavorites` KeyValueStorage 桶，非按组织分桶，key = identity 字符串）**——改名路径正确：`IdentityService.updateOrganizationMembers()`（`:883-895`）会把每个成员的收藏列表从旧 identity key 搬到新 identity key。但**删除路径的对应清理方法 `IdentityService.deleteOrganizationMembers()`（`:774-819`，含逐用户 `favorites.remove(...)`）在全代码库里找不到任何调用点——是写好了却从未接入组织删除调用链的死代码**（真实删除入口 `syncIdentity()` 的 `Identity.ORGANIZATION` 分支，`:608-640`，从未调用它），跟 11b（`RecycleBin.migrateEntries()`）、5e（`migrateCycleInfo()` 未写回）是同一种"实现了却没接线"的模式。组织真正被删除时，其所有成员在 EM 首页的收藏列表会永久残留在这个全局桶里成为孤儿——**已确认缺陷，待产品确认影响面后决定是否需要在组织删除流程里补上这个调用**。

（Portal 仓库树上的"星标收藏"——文件夹用 `RepletRegistry` 的 `favoritesUser` 属性、资产用 `AssetEntry.addFavoritesUser()`——都寄生在已经隔离好的资源里，不是独立路径，不产生新风险。）

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

**机制说明：** `RecycleBin`（`inetsoft.web.RecycleBin`）是按组织分桶的独立 `KeyValueStorage<Entry>`（`storeID = orgId.toLowerCase() + "__recyclebin"`，`RecycleBin.java:355`），存放门户资源树里"移入回收站"（而非彻底删除）的条目元数据（原路径、原名称、原权限 `Permission`、原用户 `IdentityID` 等）。与 Autosave/Task Save 一样，完全独立于机制二，不经过 `BlobIndexedStorage`/`IndexedStorage`。组织生命周期只接了两个调用点，都在 `IdentityService` 里：
- copy/rename 共用 `copyStorages()` 里的 `recycleBin.copyStorageData(oOrg.getId(), nOrg.getId())`（`IdentityService.java:1137`）——纯整桶 KV 复制，不删源；rename 场景的删源同样是后面单独调用的 `removeStorages()`（见下方 delete 场景），跟 6a/6b 的结论一致。
- delete 走 `removeStorages()` 里的 `recycleBin.removeStorage(orgID)`（`:1119`，即共享背景 delete 清单 `:1101`）。

`RecycleBin.renameFolder(oldPath, newPath)`（`:116-137`）是门户内"文件夹改名"时更新回收站条目 `originalPath` 前缀用的，跟组织级 rename 无关，不在本节范围内。

**"回收站里的 dashboard/auto save" 辨析（2026-07-24 追加，容易被术语混淆）：** `RecycleBin`/`RecycleUtils.java` 通读一遍，代码里**零处引用 autosave**——跟三、3.4 节的 `__autoSave` blob 桶完全独立，互不接触。"dashboard" 这个词在这里出现是两个跟三、3.2 `DashboardRegistryManager` 无关的用法：① `Tool.MY_DASHBOARD = "My Dashboards"`——Portal 里每个用户私有文件夹的名字（历史上也叫 "My Reports"）；② `RecycleUtils.getTypeLabel()` 把被回收的 **viewsheet** 类型标成 `"dashboard"` 文案。EM 前端 `web/projects/em/.../repository/auto-save-recycle-bin/` 目录把"回收站"和"自动保存文件夹"放在 Content > Repository 导航树的相邻节点，但两个页面组件打的是不同接口（autosave 走 `.../repository/autosave/...`），后端也是两个不相关的桶——这是 UI 导航分组造成的观感，不是同一套机制。

**已确认缺陷（Bug #75759，2026-07-24）：私有（"My Dashboards"）viewsheet 被移入回收站后，组织 clone（copy）之后在 Repository 树的 Recycle Bin 节点下看不到：** 报告的复现步骤是：删除一个私有 viewsheet（进入 `content/Repository/Recycle Bin`）→ clone 该组织 → 新组织下这个被回收的私有 viewsheet 和它所在的文件夹在树上都看不到了。

排查过程：EM 的 Recycle Bin 树由 `RepositoryRecycleBinController.getRecycleNodeFromAssets()`（`RepositoryRecycleBinController.java:145-159`）驱动——对 `securityProvider.getUsers()` 里的每个用户，构造一个 `AssetEntry(USER_SCOPE, entryType, "Recycle Bin", user)`，再用 `AssetRepository.getEntries()` 枚举其下的子条目，逐条去 `recycleBin.getEntry(childPath)` 找元数据、拼一行表格。私有回收项因此**同时依赖两层**：① `AssetRepository`/`IndexedStorage`（机制二）里必须存在这个用户的 "Recycle Bin" `AssetFolder` 容器，且容器里的子 `AssetEntry` 指向被回收 viewsheet 迁移后的真实 key；② `RecycleBin` 自己的 KV 桶（本节 11a）里必须有一条 key 匹配的元数据记录，两者缺一个，`getRecycleNodeFromAssetEntries()` 里 `binEntry != null` 判断就会跳过这一行，整条记录从树上消失。

已经用真实 `BlobIndexedStorage.copyStorageData()`（不 mock）直接验证了①这一层——`OrgLifecycleAssetContentMigrationTest#copy_userScopeRecycleBinFolder_childViewsheetAndFolderBothMigrate`（`community/core/src/test/java/inetsoft/uql/asset/sync/`）：构造一个 `USER_SCOPE`/`Type.FOLDER`、路径为 `"Recycle Bin"` 的 `AssetFolder`（`RecycleUtils.moveSheetToRecycleBin()` 第一次给某用户回收东西时用 `AssetRepository.addFolder()` 懒创建的同款容器），里面挂一个子 `AssetEntry` 指向被回收的私有 viewsheet；跑一次 `copyStorageData(fromOrg, toOrg, replace=false)`。**结果：容器本身、容器内子条目的 org/user 段、子条目指向的 viewsheet 内容——三者在目标组织下全部正确迁移，key 互相对得上，源组织侧也没有被这次复制污染（`AssetFolder` 是同一个对象被原地 `removeEntry`/`addEntry` 改造后只写回新 key，验证过没有连带改坏旧 key 读到的内容）**。这排除了"机制二迁移这个 USER_SCOPE 文件夹容器时漏处理"这个最直接的猜测——存储层这一步是正确的。

也就是说，Bug #75759 的根因大概率不在 ① 这一层，而在 ② 或者更上层——`RecycleBin` 自己的 KV 桶迁移（11a，"纯整桶复制"）本身按 key 不变的方式复制，key 里不含 orgID（`"Recycle Bin/{uuid}"`，见 `RecycleUtils.getRecycleBinPath()`），理论上应该原样可用；真正没有排除的是 `RepositoryRecycleBinController.getRecycleNodeFromAssets()` 依赖的 `securityProvider.getUsers()`（新 clone 出来的组织，这一步返回的用户列表是否及时/正确反映新组织的用户）、以及 `AssetRepository.getEntries()` 相对 `BlobIndexedStorage` 直接读写是否存在额外的一层缓存/索引（`copyStorageData()` 是绕过 `AssetRepository.addFolder()`/`setSheet()` 正常写入路径、直接写 `IndexedStorage` 的批量后台操作，如果 `AssetRepository` 在这层之上还维护了自己的目录/文件夹列表缓存且没有对着这批新写入的 key 失效/重建，就会解释"存储里数据都对、但树上看不到"这个症状）。这两个方向都还没有实测验证，先记录范围收窄的结论，不当作已定位的根因。

**已确认缺陷（2026-07-24，已通过真实 `RecycleBin`+`KeyValueStorageManager` 测试实锤，不再是仅代码读出的疑似）：** `RecycleBin` 类里定义了完整的一套身份字段重写逻辑——`migrateEntries()`/`migrateEntryPermission()`/`migratePermissionGrants()`（`:177-304`），用来把每条 `Entry` 内嵌的 `originalUser`（`IdentityID`）和 `permission`（`Permission`，按用户/组/角色/组织的授权）从旧组织重写成新组织。但全代码库搜索确认，`migrateEntries()` **没有任何调用点**——`IdentityService.copyStorages()` 只调用了 `copyStorageData()`（纯 KV 复制），从未调用 `migrateEntries()`——这与 5e（`migrateCycleInfo()` 写好了重写逻辑但没写回）是同一种"实现了却没接入调用链"的模式。

实测过程中又叠了两层新发现，就算以后有人把 `migrateEntries()` 接回 `copyStorages()`，光这一步也还不够：

1. **`migrateEntries()` 自己不落盘**：它只在传进来的 `Map<String, Entry>` 上原地改 `Entry` 对象的 `originalUser`/`permission` 字段，方法体里没有任何 `storage.put()`/持久化调用。`RecycleBinOrgLifecycleTest#migrateEntries_identityRewritten_permissionGrantsRewritten_butNeverPersisted` 实测确认：调用后立刻用同一个内存对象读，`originalUser`/`permission` 都已经改成新组织；但重新从 KeyValueStorage `getEntry()` 读一次同一个 key，读到的还是没被改过的旧值——不接一个显式的按 key `put()` 回写，这次修改就是一次性的，进程重启或缓存失效后就没了。
2. **`migratePermissionGrants()` 读权限用的是"当前组织"过滤过的 `getGrants()` 重载，不是按迁移源组织显式取**：`Permission.getGrants(action, identityType)`（2 参重载，`Permission.java:413-417`）内部用 `OrganizationManager.getCurrentOrgID()` 过滤，`migratePermissionGrants()`（`RecycleBin.java:224-266`）用的正是这个重载，而不是能显式传 orgId 的 3 参重载。`RecycleBinOrgLifecycleTest#migrateEntries_permissionGrantsSilentlyUnchanged_whenCurrentOrgDoesNotMatchGrantsOrg` 实测确认：只要调用 `migrateEntries()` 那一刻线程的"当前组织"不等于这条授权本身所属的组织（哪怕 `oorg`/`norg` 参数都传对了），`getGrants()` 就会返回空集合，`if(!grants.isEmpty())` 直接跳过 `setGrants()`，这条权限授权原封不动留在旧组织身份上——`originalUser` 字段的重写不受这个影响（它是直接字段赋值，不经过 `getGrants()`），只有 `permission` 这半边受影响，同一个方法内两个字段的可靠性并不一致。这跟三、3.3 节 5f/5h、三、3.8 节 Issue #75769 是同一种"当前组织上下文耦合"风险模式。

也就是说，组织复制/改名后，回收站里每条记录的 `originalUser`/`permission` 目前**必定**仍指向旧组织的身份字符串（因为第一层"从未调用"就已经生效），業务影响面（回收站 UI 是否真的读取/显示这些字段、错误的 `originalUser`/`permission` 会不会导致权限判断错误）仍待确认。

**Copy/Rename 场景**（同一方法，行为相同）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 11a | `copyStorageData(oId, id)`，copy/rename 共用 | 整桶流式复制，不删源（删源另在 delete 清理块生效，同 6a/6b 的结论） | `[已落地]` `RecycleBinOrgLifecycleTest#copy_copyStorageData_wholeBucketCopied_sourceUntouched`（真实 `RecycleBin`+`KeyValueStorageManager`；同时顺带钉住 11b 的"copy 侧不重写"证据：迁移后条目的 `originalUser` 仍是源组织） |
| 11b | 已确认缺陷：`migrateEntries()` 从未被调用；即使调用了也有两处额外问题（见上方说明） | 复制/改名后，`Entry.originalUser`/`Entry.permission` 仍停留在旧组织的身份字符串上，不会被重写；`migrateEntries()` 即使被接入，其修改也不会持久化，且权限授权部分还额外依赖"当前组织"上下文是否凑巧匹配 | `[已落地]` `RecycleBinOrgLifecycleTest#migrateEntries_identityRewritten_permissionGrantsRewritten_butNeverPersisted`（当前组织=源组织时：identity 和 permission 都能在内存里正确重写，但重新从存储读一次就打回原形）、`#migrateEntries_permissionGrantsSilentlyUnchanged_whenCurrentOrgDoesNotMatchGrantsOrg`（当前组织≠源组织时：`originalUser` 仍正确重写，但 `permission` 授权完全不动） |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 11c | `removeStorage(orgID)`（共享背景 delete 清单 `:1101`） | 整桶删除 | `[已落地]` `RecycleBinOrgLifecycleTest#delete_removeStorage_wholeBucketDeleted` |

**测试覆盖：** 11a-11c 全部落地，`RecycleBinOrgLifecycleTest.java`（`community/core/src/test/java/inetsoft/web/`），共 4 个 `@Test`，全部通过、无 `@Disabled`；直接调用 `RecycleBin` 的 public 方法（`copyStorageData()`/`removeStorage()`/`migrateEntries()`），不经过 `IdentityService.copyStorages()`/`removeStorages()` 完整编排；"当前组织"切换复用 `OrgLifecycleScopedPropertiesIntegrationTest` 已验证过的 `ThreadContext.setContextPrincipal(SRPrincipal)` + `OrganizationContextHolder.setCurrentOrgId()` 手法。另外，Bug #75759 排查过程中新增了 1 个相关测试，但落在机制二的测试文件里而不是本节——`OrgLifecycleAssetContentMigrationTest#copy_userScopeRecycleBinFolder_childViewsheetAndFolderBothMigrate`（`community/core/src/test/java/inetsoft/uql/asset/sync/`），验证的是 `BlobIndexedStorage.copyStorageData()` 对 USER_SCOPE "Recycle Bin" `AssetFolder` 容器+子条目的迁移，不是 `RecycleBin`（本节机制）自己的 KV 桶迁移，因此没有算进本节场景表——记在这里防止将来去 3.6/3.7 之外的地方漏找。

---

### 3.8 EM Presentation 设置同步（`PortalThemesManager`：Logo / Favicon / 欢迎页 / 登录横幅 + SreeEnv org-scoped 属性）

三、3.5 附录一的 B16 提到 `portalthemes.xml` 本身不该被 `getOrgScopedPaths()` 匹配是正确设计——组织数据靠文件内部四个 `Map<orgId, ...>` 隔离，不靠文件路径。这四个 map 有没有正确同步是 DataSpace 普查带出的第二个新发现，但它的根因（map 没有跟着组织生命周期操作更新）跟 DataSpace 的路径匹配机制完全无关，只是 `portalthemes.xml` 这个文件本身恰好存在 DataSpace 桶里而已；同时它在 EM UI 上跟日期/时间格式、Dashboard 设置等一大批 SreeEnv org-scoped 属性同属一个 "Presentation" 设置页。按用户实际报 bug 时用的功能分类（"Presentation 页面东西丢了"）而不是按底层存储桶分类，单独成节，与 3.1-3.7 平级。

**机制说明：** `PortalThemesManager`（`inetsoft.sree.portal.PortalThemesManager`）是单例，把品牌相关设置持久化在 DataSpace 根目录的单一全局文件 `portalthemes.xml`（`:566-597`，文件名可配）里，组织归属不靠文件路径隔离，而是文件内部四个独立的 `Map<orgId, ...>`：`cssEntries`/`logoEntries`/`faviconEntries`/`welcomePageEntries`（`:307-359`、`:1067`）。物理文件本体（`portal/{orgId}/{cssFile}`、`portal/{orgId}/{logoFile}`、`portal/{orgId}/{faviconFile}`）都落在 `dataSpace` 桶里且路径正确（见三、3.5 附录一 A5-A7），组织改名时会被 `getOrgScopedPaths()`/`updateOrgScopedDataSpace()` 正确搬到新路径——**问题不在文件本体，在这四个 map 有没有跟着物理文件的搬迁/复制/删除同步更新**。

**四个 map 待遇不一致，逐一核实（`grep` 全代码库 `LogoEntry|FaviconEntry|WelcomePage|CssEntr` 的调用点得到）：**

| Map | Copy（`copyOrganizationInternal(replace=false)`） | Rename（`copyOrganizationInternal(replace=true)`，含从 `setOrganizationInfo()`→`syncIdentity()` 触发的真实入口） | Delete（`syncIdentity()` 的 `ORGANIZATION` 分支） |
|---|---|---|---|
| `cssEntries` | **正确**：`AbstractEditableAuthenticationProvider.java:219-240` 无条件读源组织条目、复制物理文件、`manager.addCSSEntry(newOrgID, ...)` | **正确**：同一段 `:219-240` 逻辑对 rename 同样生效（`copyOrganizationInternal()` 是 copy/rename 共用方法），加上 `:287` 的 `manager.removeCSSEntry(fromOrgId)` 清掉旧条目 | **正确**：`:287` `removeCSSEntry(fromOrgId)` |
| `logoEntries` | **缺失**：`copyOrganizationInternal()` 全程没有任何 `addLogoEntry`/`getLogoEntries` 调用 | **缺失**：同上，rename 也不会碰这个 map——旧 orgId 的条目原地不动（指向一个已经被物理搬走、不再存在的旧路径），新 orgId 没有对应条目 | **缺失**：没有 `removeLogoEntry(fromOrgId)` 调用 |
| `faviconEntries` | **缺失**，理由同 `logoEntries` | **缺失**，理由同 `logoEntries` | **缺失**，理由同 `logoEntries` |
| `welcomePageEntries` | **缺失**，理由同 `logoEntries` | **缺失**，理由同 `logoEntries` | **缺失**，理由同 `logoEntries` |

`addLogoEntry`/`removeLogoEntry`/`addFaviconEntry`/`removeFaviconEntry`/`setWelcomePage(orgId,...)`/`removeWelcomePage(orgId)` 这几个方法确实存在（`PortalThemesManager.java:330-359`、`:468-488`），但全代码库里唯一的调用方是用户手动在 EM "Look and Feel"/"Welcome Page" 设置页面里显式改这几项时触发的 `LookAndFeelService`/`WelcomePageService`/`PresentationLoginBannerSettingsService`——组织生命周期代码（`AbstractEditableAuthenticationProvider`/`IdentityService`）里一次都没调用过这三组方法。

**"Login Banner" 也受影响，因为它跟 "Welcome Page" 共用同一个 `welcomePageEntries` map（2026-07-24 追加确认）：** `PresentationLoginBannerSettingsService.getModel()`/`setModel()`/`resetSettings()`（`PresentationLoginBannerSettingsService.java:34-112`）读写的都是同一个 `manager.getWelcomePage(orgId)`/`setWelcomePage(orgId, ...)`/`removeWelcomePage(orgId)` API、同一个 `PortalWelcomePage` 对象（`bannerType`/`banner` 字段），只是 EM UI 上呈现成"欢迎页"和"登录横幅"两个独立的设置面板。也就是说，`welcomePageEntries` 这一行的"缺失"结论同时覆盖了这两个前端功能——组织改名后，自定义登录横幅也会跟欢迎页一起静默变回默认值。

**已确认缺陷（新发现，2026-07-24，尚未建 Redmine issue）：** 组织改名后，自定义 Logo/Favicon/欢迎页/登录横幅会静默变回默认值——机制跟 Issue #75763（sreeUserData/History Bar）几乎一样：物理文件被正确搬到新路径，但引用它的元数据（这里是 `PortalThemesManager` 的 map，而不是查找键里的 orgId 段）没有跟着更新，读取时用新 orgId 去查 map 查不到，只能退回默认。组织复制时，新组织不会继承源组织的 Logo/Favicon/欢迎页/登录横幅设置。组织删除时，这三个 map 里的条目永久残留（不会造成用户可见症状，因为查这些 map 都是按当前组织 ID 查的，删除的组织不会再被查到，但数据本身是孤儿）。跟 CSS 形成直接对比——`cssEntries` 用的是完全一样的 `copyOrganizationInternal()` 入口，因为多写了 20 行代码就做对了，这不是架构限制，是这三个 map 当初实现时被漏掉。

**"Presentation" 页面里其余设置不受此缺陷影响，已实测验证（2026-07-24），不只是理论推断：** EM "Presentation" 设置页除了 Look and Feel/Welcome Page/Login Banner 之外，还有日期时间格式、Dashboard 设置、PDF 生成、导出菜单、报表/视图工具栏选项、分享设置、Composer 提示信息、时间设置、数据源可见性、Web Map 等一大堆子面板（见 `PresentationSettingsModel.java`）。这些全部经 `SreeEnv.getProperty(name, earlyLoaded, true)`/`setProperty(name, val, true)` 走的是 `"inetsoft.org." + orgID + "." + propertyName` 这个 key 格式（`PropertiesEngine.java:106-107`、`:206-220`、`:291-304`），而这个前缀格式正是 `AbstractEditableAuthenticationProvider.copyScopedProperties()`/`clearScopedProperties()`（`:145`、`:433`、`:493-519`）在改名时迁移的那一批属性。

排查过程中一度怀疑过一个具体假说——`PropertiesEngine.setProperty(name, val, orgScope=true)`（写路径，`:291-304`）构造 key 时用的是 `OrganizationManager.getInstance().getCurrentOrgID()` 原样返回值，而 `useAvailableOrgProperty()`（读路径，`:371-398`）显式多调用了一次 `.toLowerCase()`，看起来像是大小写不对称——但读代码确认 `OrganizationManager.getCurrentOrgID()`（无参版本，`OrganizationManager.java:64-68`）本身在返回前就已经统一转成小写，所以读写两边实际拼出来的 key 无论组织 ID 本身大小写如何都是一致的，这个假说不成立，已排除。

进一步用真实（非 mock）`SreeEnv`/`OrganizationManager`/`ThreadContext` 写了两层端到端测试直接验证，都在 `OrgLifecycleScopedPropertiesIntegrationTest.java`（`community/core/src/test/java/inetsoft/sree/security/`）：

1. `rename_copyScopedProperties_realRoundTrip_presentationStylePropertySurvives`——以 `fromOrgId` 身份写入一个 `format.date` 这类 org-scoped 属性，反射调用真实 `copyScopedProperties(fromOrgId, toOrgId, true)`，切换到 `toOrgId` 身份读回——**测试通过，属性正确迁移**；切回 `fromOrgId` 身份读取，正确退回全局默认值（不是残留旧值）。
2. `rename_realSetOrganizationInfoEntryPoint_presentationStylePropertySurvives`——不满足于第 1 条只测了孤立方法，进一步反射驱动**真实的完整入口** `IdentityService.setOrganizationInfo()`（`FileAuthenticationProvider` + `SecurityTestDataBuilder` 建的真实组织/用户，只 stub 掉跟属性无关的存储步骤）。第一次跑因为测试自己漏配了 `libManagerProvider`（传了 `null`），在 `syncIdentity()`（`:553`）最前面就直接 NPE，`copyOrganization()`/`copyScopedProperties()` 根本没机会执行——诊断输出显示新旧 org 的 key 都没变化，一度看着像是"确认了缺陷"，但反射抓异常打出来之后发现是测试自己的 mock 缺口，不是产品代码问题。补上 `mock(LibManagerProvider.class)` 之后重跑，**新组织的 key 正确出现、旧组织的 key 正确消失**——通过真实入口的验证同样是"迁移正常"的结论。

也就是说，`copyScopedProperties()` 本身、以及它被真实生产入口调用的这条完整链路，**两层测试都指向"迁移正确"**。

**但你反馈的是：强制刷新之后设置依然是默认值，`properties` 里这些 org01 相关的条目本身都不见了——这跟上面两条测试的结论直接矛盾，属于"代码分析/隔离测试说没问题，生产环境说有问题"这一类（跟三、3.3 节 Data Cycle 5f/5h 是同一种性质的矛盾，那边也是单元测试通过但真实环境行为不一致，原因未查清）。已提交 Issue #75769，后续再排查。** 需要进一步的现场信息才能继续往下查，而不是靠继续读代码：

- **改名当时（或改名前后）EM/服务端日志里有没有报错或警告？** 我自己在搭测试的过程中，只要少配一个依赖（比如这次的 `libManagerProvider`），`syncIdentity()`就会在真正跑到属性迁移那几行代码之前先因为空指针整个中断退出——如果生产环境里也有类似的、只在你们这套具体部署/插件/配置组合下才会触发的异常（哪怕业务上"看起来"改名成功了），效果会是一样的：`copyScopedProperties()` 那几行代码根本没机会执行，而不是执行了但结果不对。日志是目前能确认"到底有没有跑到这一步"最直接的证据。
- **这是不是集群/多节点部署？** `SreeEnv.save()`把改动落盘/落库后，别的节点要读到这份新值依赖各自的缓存/同步机制，单机测试完全测不到这类节点间不一致的窗口。
- **这些设置最早是在哪个组织上下文里保存的？** 如果保存的时候站点管理员的"当前组织"实际上不是 org01（比如 EM 顶部组织切换器状态跟你以为的不一致），那些属性从一开始可能就没有落在 `inetsoft.org.org01.` 这个前缀下，改名迁移自然也就"救不到"它们——这跟三、3.3 节 Data Cycle 那个"当前组织上下文耦合"缺陷是同一种可疑模式。

在得到以上任何一条线索之前，先不把这条计入"已确认缺陷"，按"代码与实测矛盾、原因未知"记录，制度上跟 5f/5h 保持一致处理。

**Copy / Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 12a | `cssEntries` 在 copy/rename 下正确同步（对照组，证明"做对是可行的"） | 新组织拿到条目，rename 时旧组织条目被移除 | `[待补]` |
| 12b | `logoEntries`/`faviconEntries`/`welcomePageEntries` 在 copy 下均不同步 | 新组织读不到源组织的 Logo/Favicon/欢迎页（含登录横幅，同一个 map）设置（即使物理文件路径本身是隔离的） | `[待补]` |
| 12c | 同上三个 map 在 rename 下均不同步 | 旧组织条目原地残留（指向已不存在的旧路径），新组织无对应条目——前端症状：改名后自定义品牌设置（含登录横幅）变回默认值 | `[待补]` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 12d | `cssEntries` 在 delete 下正确清理（对照组） | `removeCSSEntry(orgId)` 被调用，条目清除 | `[待补]` |
| 12e | `logoEntries`/`faviconEntries`/`welcomePageEntries` 在 delete 下均不清理 | 组织删除后，三个 map（含登录横幅共用的 `welcomePageEntries`）里对应的 orgId 条目永久残留成孤儿 | `[待补]` |

**测试覆盖：** 零，均待补——预计沿用真实 Spring 集成风格（`PortalThemesManager` 本身没有 community 空实现问题，不需要像 `CustomThemesManager` 那样 mock），新建 `OrgLifecyclePortalBrandingTest.java`，直接调用 `copyOrganizationInternal()`（`StubProvider`）+ 真实 `PortalThemesManager`/`DataSpace` bean。

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

**后续新发现如果跨越多个机制/资源类型，先记录在这里；能明确归位到某个具体章节的，随手搬过去，不要让这里膨胀成新的大杂烩。**

---

## 五、测试文件规划

| 测试类（均待建） | 覆盖场景 |
|---|---|
| `OrgLifecycleDependencyMigrationTest.java` | 一（1a-1c） |
| `OrgLifecycleAssetContentMigrationTest.java` | 二（2a-2j、10a-10c） |
| `OrgLifecycleThemeIntegrationTest.java` | 三、3.1（3a、3c、3d） |
| `OrgLifecycleDashboardMigrationTest.java` | 三、3.2（4a-4f） |
| `DataCycleManagerOrgLifecycleTest.java`（已建，`inetsoft.sree.internal`） | 三、3.3（5a-5f、5h 已落地，5g 待补） |
| `IdentityServiceAutoSaveOrgLifecycleTest.java`（已建，`inetsoft.web.admin.security`） | 三、3.4（6a/6b/6d/7a 已落地，6c 待补） |
| `OrgLifecycleDataSpaceIntegrationTest.java`（已建，`inetsoft.sree.security`） | 三、3.5（8a-8d 全部落地，另发现 `sreeUserData/` 孤儿新缺陷，见该节"已确认缺陷"） |
| `OrgLifecycleRepletRegistryIntegrationTest.java`（已建，`inetsoft.sree.security`） | 三、3.6（9a-9c 全部落地，另发现 `updateRepletRegistry()` 删除不落盘的新发现，见该节） |
| `RecycleBinOrgLifecycleTest.java`（已建，`inetsoft.web`） | 三、3.7（11a-11c 全部落地，另确认 `migrateEntries()` 除"从未调用"外还有"不落盘"+"当前组织上下文耦合"两处新发现） |
| `OrgLifecyclePortalBrandingTest.java` | 三、3.8（12a-12e，含 Logo/Favicon/欢迎页元数据失步新缺陷） |

机制一、机制二的测试类严格不共享基类/fixture 构造方法（见计划文档 Global Constraints）。

---

## 六、待产品确认的场景一览

| 场景 | 问题 | 归属章节 |
|---|---|---|
| 3d | 删除组织后全局主题 `organizations` 列表残留孤儿 orgId | 三、3.1 |
| 7a | Task Save 文件：copy 场景不复制是否符合预期 | 三、3.4 |
| 11b | 回收站已确认缺陷（已测试实锤，不再是疑似）：`RecycleBin.migrateEntries()` 写好了 `originalUser`/`permission` 身份字段重写逻辑，但 `IdentityService.copyStorages()` 从未调用它，只做了纯 KV 复制；就算以后接回调用链，实测还发现该方法本身不落盘（只改内存对象）、且权限授权部分依赖调用那一刻的"当前组织"上下文是否凑巧等于被迁移的源组织，两者都不满足时权限字段完全不会被重写——复制/改名后回收站条目的身份字段目前必定仍指向旧组织，业务影响面（是否真的被 UI 读取/依赖）待确认 | 三、3.7 |
| — | 无锁执行顺序窗口是否会被生产并发场景实际触发 | 共享背景 |
| Issue #75763 | `DataSpace.getOrgScopedPaths()` 的 `sreeUserData/` 匹配分支是死代码：真实 per-user 文件名（`{name}_{orgId}.xml`）不含 `IdentityID.KEY_DELIMITER`，`getIdentityIDFromKey()` 因此忽略路径本身、返回调用线程当前组织。已实测复现改名场景的前端症状——Preferences 对话框 History Bar 等 `UserEnv` 支持的用户级设置在组织改名后静默重置为系统默认值；删除场景则是永久孤儿存储，未单独实测（unit test 已钉住） | 三、3.5 |
| 5f/5h | Data Cycle 克隆场景：`getDataCycleIds()` 当前组织上下文耦合缺陷——单元测试 + 真实 Spring 装配核查都证实代码里确实这样写，但用户在真实运行环境里刻意避开"当前组织==源组织"这个前提后手动复测，克隆结果依然正确（含下游的 MV 调度——克隆后新组织下确实存在"DataCycle Task: cycle2"），两轮独立代码排查（含 enterprise/server 是否有覆盖实现）都没找到能解释这个矛盾的机制。原因未知，后续处理——留意是否是环境/构建版本差异，或是遗漏了某条实际调用路径 | 三、3.3 |
| 新发现 | `PortalThemesManager` 的 `logoEntries`/`faviconEntries`/`welcomePageEntries` 三个 map 在组织 copy/rename/delete 下均不同步（`cssEntries` 走的是同一个 `copyOrganizationInternal()` 入口，却被正确处理，说明不是架构限制，是这三个 map 当初漏写了对应调用）——组织改名后自定义 Logo/Favicon/欢迎页会静默变回默认值，复制不继承，删除后永久残留成孤儿；尚未建 Redmine issue，需产品确认影响面和优先级 | 三、3.8 |
| Issue #75766 | EM "Manage Favorites" 的 `IdentityService.deleteOrganizationMembers()`（含逐用户 `emFavorites` 清理）全代码库无任何调用点，是写好了却从未接入组织删除调用链的死代码（改名路径的 `updateOrganizationMembers()` 对应逻辑是正确接入的）——组织删除后其所有成员的 EM 收藏列表永久残留成孤儿；需产品确认影响面 | 三、3.5 附录 |
| `[待确认]` | Legacy 报表部署导入的全局模板路径（`templates/{fname}`、`ReportFiles/{fname}` 等，`DeployManagerService.java:213-244`）完全不含 orgId——如果这条（前多租户时代的）导入功能在当前多租户环境下仍可达，会是跟 sreeUserData 同类的"该隔离没隔离"缺陷；未能确认该功能当前是否还真的可达，暂不计入已确认缺陷 | 三、3.5 附录 |
| `[设计如此，非缺陷]` | `userformat.xml`（数字/小数格式设置）所有组织共享同一份全局文件，从设计上就没有 per-org 隔离，不属于"改名/删除后丢失"这类生命周期缺陷；仅记录以防被误当成 sreeUserData 同类问题 | 三、3.5 附录 |
| Issue #75769 | EM Presentation 页面里 SreeEnv org-scoped 属性（日期/时间格式、Dashboard 设置、PDF 生成、导出菜单、Composer 提示信息等 `inetsoft.org.{orgId}.*` 属性）在生产环境里改名后丢失、强制刷新也恢复不了，`properties` 里对应组织的条目直接消失——但两层实测（孤立调用 `copyScopedProperties()`、以及反射驱动真实 `IdentityService.setOrganizationInfo()` 完整入口）都显示迁移正确，代码与实测结论矛盾，原因未知，跟 5f/5h 同一种性质，需要现场日志/集群信息才能继续排查 | 三、3.8 |
| Issue #75759 | 私有（"My Dashboards"）viewsheet 移入回收站后，组织 clone 之后在 Repository 树的 Recycle Bin 节点下看不到——已用真实 `BlobIndexedStorage.copyStorageData()` 验证过 USER_SCOPE "Recycle Bin" `AssetFolder` 容器+子条目+被回收 viewsheet 内容三者在存储层全部正确迁移、key 互相对得上，排除了"机制二漏处理这个文件夹容器"这个最直接的猜测；根因大概率在更上层——`RepositoryRecycleBinController.getRecycleNodeFromAssets()` 依赖的 `securityProvider.getUsers()`（新 clone 组织的用户列表是否及时反映），或者 `AssetRepository.getEntries()` 相对 `BlobIndexedStorage` 直接读写是否存在额外一层未随批量迁移失效的缓存/索引——两个方向都还没有实测验证 | 三、3.7 |

> **已从本节移除（2026-07-23）**：原 4d/4e「dashboard 注册表 rename 内容不重写」——经人工复测，EM 改组织 ID 最终结果正确，属机制/测试隔离说明，**不按待产品确认的缺陷跟踪**（详见三、3.2「实现备注」）。
