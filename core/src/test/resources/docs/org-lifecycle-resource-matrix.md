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
| 3d | 全局共享主题被组织选用后删除该组织，主题的 `organizations` 列表是否残留该 orgId | **当前行为：残留** | `[已落地，@Disabled]`——`IdentityThemeServiceTest#removeTheme_globalThemeStillListsDeletedOrg_organizationsEntryIsStripped`（断言修复后的正确行为，标记 `@Disabled` 等 Issue #75739 修复后去掉即可） |

**已确认缺陷（Issue #75739）：** `IdentityThemeService.removeTheme(orgID)`（`:60-74`）只删除组织专属主题、重置该组织自己的 SreeEnv 选中指针，不遍历全局主题摘除其 `organizations` 列表里的该 orgID——对比 rename 路径（`IdentityService.updateCustomThemeOrganization()`：2163-2209）有对称摘除逻辑，delete 路径没有。

**实际影响（前端可复现，非纯数据残留）：** 真正下发主题的运行时路径 `CustomThemesImpl.getUserTheme()`（enterprise，`:290-346` 第4步"按组织匹配"）直接读这个未清理的 `organizations` 数组，不比对 SreeEnv 指针；而 `CustomThemeModel` 从不把这个数组序列化给前端，管理员在 EM 里看到的"Default for This Organization"是已经被正确清零的 SreeEnv 状态。一旦被删组织的 ID 被新组织复用（`UserTreeService` 的重名校验只查当前存在的组织，`:905-918`，不阻止复用已删除的 ID），新组织里没设个人主题偏好的用户会静默套用旧组织选过的主题，管理员在界面上完全查不出原因。复现步骤见 Issue #75739。

**测试覆盖：** `copyThemes()` 方法级已覆盖（3a）；编排层集成已覆盖（3c，`OrgLifecycleThemeOrchestrationTest`）；delete 路径（3d）已有回归测试但标记 `@Disabled`，等 Issue #75739 修复后去掉标记即生效。

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
| 4a | `DashboardManager.copyStorageData()`，copy/rename 都调用 | 整桶复制不删源 | `[待补]` |

**偏好设置 — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4b | `removeDashboardStorage()`（共享背景 delete 清单 `:1099`） | 整桶删除 | `[待补]` |

**注册表 — 依赖入口不同，两条路径行为不一样：**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4c | Copy：`copyDashboardRegistry()` | 逐用户 `cloneVSDashboard()` 写入新组织路径，源文件不受影响 | `[待补]` |
| 4d | Rename——仅走 `copyOrganizationInternal(replace=true)` | **当前行为：不迁移**，只清内存缓存，随后源文件被删除——数据丢失而非孤儿 | `[待确认]` |
| 4e | Rename——走完整 `setOrganizationInfo()` 入口 | `migrateRegistry(null,...)` 只迁移 admin 级，**用户级注册表文件不迁移**、随后被删除 | `[待确认]` |

**注册表 — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 4f | `removeOrgScopedDataSpaceElements()` 按路径前缀删除（共享背景 delete 清单 `:614`） | 无孤儿文件 | `[待补]` |

**已确认缺陷：** 4d/4e 两条 rename 路径下，用户级 dashboard 定义都不会被正确迁移，是否可接受需产品判断。

**测试覆盖：** 零。

---

### 3.3 Schedule Task / Data Cycle

**分工说明（避免与机制二混淆）：** Schedule Task 资产本身的**内容**（比如定时导出/推送某个 Viewsheet 时，Action 里对 `viewsheet` 属性的引用）走的是**机制二**的 `MigrateScheduleTask`（见二）。本节只覆盖 **Data Cycle**（一种生成"预生成任务"的业务对象）自身**存在性**的迁移，走的是完全独立的 `DataCycleManager`。

**机制说明：** `migrateDataCycles(Organization oorg, Organization norg, boolean replace)`（`DataCycleManager.java:863-887`）：`replace=true` 才删除源 entry，两条路径都刷新 `pregeneratedTasksMap` 缓存。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5a | `migrateDataCycles(oorg, norg, replace=false)` | 复制 `DataCycleAsset`，不删源；`CycleInfo` 身份字段**理论上**应同步改写（实际行为见"已确认缺陷"） | `[待补]` |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5b | `replace=true` | 同上 + 删除源 entry + 刷新缓存 | `[待补]` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5c | `clearDataCycles(orgId)`（共享背景 delete 清单 `:612`） | 该组织所有 `DataCycleId` 精确清除 | `[待补]` |
| 5d | 普通 Schedule Task（非 Data Cycle）Delete：靠 `indexedStorage.removeStorage(orgId)` 整桶清理 | 断言删除后确实不可读，防止未来 `IndexedStorage` 改分桶策略后失去覆盖 | `[待补]`——低优先级回归防护 |

**已确认缺陷：** `migrateCycleInfo()`（`DataCycleManager.java:937-957`）——`identityID.setOrgID(norg.getId())` 只修改局部变量，从未调用 `cycleInfo.setCreatedBy()`/`setLastModifiedBy()` 写回。Copy/Rename 后 `CycleInfo.createdBy`/`lastModifiedBy` 仍指向源组织用户身份。

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 5e | 上述缺陷的回归基线 | 断言当前（错误）行为存在，作为后续修复基线 | `[待补]` |

**测试覆盖：** 零。

---

### 3.4 Autosave 文件 / Task Save 文件

**Autosave blob 本体 — Copy/Rename 场景**（同一方法，行为相同）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6a | `updateBlobStorageName("__autoSave",...,copy=true)`，copy 调用 | 整桶流式复制，不删源 | `[待补]` |
| 6b | 同一方法，rename 调用 | 同 copy 行为（删源另在 rename 清理块生效）——需明确断言"这一步不删源" | `[待补]` |

**Autosave 嵌入内容修正**（依赖机制二，见共享背景"跨机制依赖关系"）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6c | `AutoSaveUtils.migrateAutoSaveFiles()`，copy 分支切换 `runInOrgScope(newOrgId)`、rename 分支不切换 | 两种上下文下，嵌入的 viewsheet/worksheet 内容均正确重写（依赖机制二的 `MigrateViewsheetTask`/`MigrateWorksheetTask`） | `[待补]` |

**Autosave — Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 6d | `removeBlobStorage("__autoSave",...)`（共享背景 delete 清单 `:1105-1111`） | 整桶删除 | `[待补]` |

**Task Save 文件 — Rename 场景**（copy 不涉及）

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 7a | `updateTaskSaveFiles()` → `externalStorageService.renameFolder()`，仅 rename 调用 | 断言 copy 路径确实不调用（当前设计如此）；rename 路径文件夹正确重命名 | `[待确认]`——copy 场景不复制是否符合预期未经产品确认 |

**测试覆盖：** 零。

---

### 3.5 Data Space 文件

**机制说明：** `copyDataSpace(fromOrg, toOrg, replace)`（`AbstractEditableAuthenticationProvider.java:444-491`）：`replace=true` 用 `dataspace.rename()`，`replace=false` 用 `dataspace.copy()`；若源是默认组织，copy 分支额外复制 MV 文件系统/Block 系统元数据。`setOrganizationInfo()` 入口另有独立实现 `updateOrgScopedDataSpace()`。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 8a | `copyDataSpace()` 方法级三分支（A/B/C） | 见机制说明 | `[已落地]`——`AbstractEditableAuthenticationProviderStaticDepTest`（与三、3.1 场景 3a 同一测试类） |
| 8b | 默认组织特例：额外复制 MV/Block 元数据 | 新组织拿到对应文件 | `[待补]` |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 8d | `setOrganizationInfo()` 入口独立的 `updateOrgScopedDataSpace()` | 确认这条独立实现的重命名行为跟 `copyDataSpace()` 一致，不产生行为分叉 | `[待补]` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 8c | `removeOrgScopedDataSpaceElements()` 按路径枚举删除（共享背景 delete 清单 `:614`） | 无孤儿路径 | `[待补]` |

**测试覆盖：** 方法级已覆盖 copy 分支；delete 路径、`setOrganizationInfo()` 独立实现零覆盖。

---

### 3.6 Replet Registry

**机制说明：** `copyRepletRegistry(fromOrgId, toOrgId)`（`IdentityService.java:1229-1269`）——**copy/rename 两条路径都无条件调用一次**，复制 folder + folderContextMap + 逐用户 copy，从不删源；rename 分支末尾另外显式调用 `updateRepletRegistry(fromOrgId, null)` 删源。

**Copy 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 9a | `copyRepletRegistry()` | 复制 folder+folderContextMap+逐用户，不删源 | `[待补]` |

**Rename 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 9b | copy 逻辑同 9a + 末尾 `updateRepletRegistry(fromOrgId, null)` 删源 | 源组织 registry 清空，新组织拿到完整副本 | `[待补]` |

**Delete 场景**

| # | 场景 | 预期 | 测试状态 |
|---|---|---|---|
| 9c | `updateRepletRegistry(orgId, null)` + `clearOrgCache(orgId)`（与 9b 删源逻辑完全相同，共享背景 delete 清单 `:615`/`:624`） | 无孤儿 | `[待补]` |

**测试覆盖：** 零。

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
| `OrgLifecycleScheduleMigrationTest.java` | 三、3.3（5a-5e） |
| `OrgLifecycleAutoSaveMigrationTest.java` | 三、3.4（6a-6d、7a） |
| `OrgLifecycleDataSpaceIntegrationTest.java` | 三、3.5（8a-8d） |
| `OrgLifecycleRepletRegistryMigrationTest.java` | 三、3.6（9a-9c） |

机制一、机制二的测试类严格不共享基类/fixture 构造方法（见计划文档 Global Constraints）。

---

## 六、待产品确认的场景一览

| 场景 | 问题 | 归属章节 |
|---|---|---|
| 3d | 删除组织后全局主题 `organizations` 列表残留孤儿 orgId | 三、3.1 |
| 4d | Dashboard 注册表：仅走 `copyOrganizationInternal(replace=true)` 时用户注册表不迁移即被删除 | 三、3.2 |
| 4e | Dashboard 注册表：`setOrganizationInfo()` 入口下用户级注册表从未迁移 | 三、3.2 |
| 7a | Task Save 文件：copy 场景不复制是否符合预期 | 三、3.4 |
| — | 无锁执行顺序窗口是否会被生产并发场景实际触发 | 共享背景 |
