# Java Unit Test Generation Prompt

> **All test code and comments must be written in English.**

---

## 0. 动笔前：意图 vs 实现分析

选择测试策略前，先回答四个问题，记录每条差距为 suspect：

```
1. 作者想实现什么？  → 读接口/抽象类契约、方法名、Javadoc
2. 代码实际做了什么？→ 读实现，不要相信方法名，相信代码
3. 两者有差距吗？    → 每个差距 = suspect = 必须有对应测试
4. 有哪些边界/遗漏？ → null、空集合、未 override 的父类 no-op、
                       集合类型不匹配（Set<A>.remove(B) 永不匹配）、
                       不同合法输入是否映射到同一内部 key / 标识、
                       catch 后吞异常但继续返回“成功形态”结果、
                       boolean 标记 × 历史状态是否存在 的组合分支
```

仅当意图与实现存在不一致时，才将该差距写入测试类文件头；若无差距则不写。每条 suspect 对应一个 enabled 或 `@Disabled` 测试：

```java
/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] setPermission(identity, null) → intent: remove entry
 *             actual: removePermission(IdentityID) not overridden → no-op in base class
 * [Suspect 2] authenticationChanged → intent: rewrite/strip grants for oldID
 *             actual: Set<PermissionIdentity>.remove(IdentityID) → equals() never matches
 */
```

---

## 1. 先判断被测类的类型

在动笔之前，先判断被测类属于哪种类型，选择对应的测试策略：

| 类型 | 典型特征 | 测试策略 |
|------|---------|---------|
| **数据模型 / 工具类** | 纯 POJO、无外部依赖、方法是 get/set/check/transform | → **决策树路径覆盖**（见第 2 节） |
| **行为编排 / 策略类** | 协调多个依赖、有业务流程、方法名是动词短语 | → **业务场景覆盖**（见第 2B 节） |
| **存储适配器 / Repository 类** | 封装存储后端、CRUD 方法语义、有 key 生成/解析逻辑、有生命周期管理 | → **存储契约 + 状态转换覆盖**（见第 2C 节） |

多种策略可以在同一个测试类里共存：数据模型内部有复杂分支时用决策树，同一个类对外暴露的业务契约用场景，存储层的 CRUD 和 key 映射用存储契约。

---

## 2. 数据模型/工具类：读源码，画决策树

读被测方法，提取分支节点，用字母标记，写在测试类文件头：

```java
/*
 * check() decision tree
 *  ├─ [A] type is invalid                   → false
 *  ├─ [B] grants.get(action) == null        → false
 *  ├─ [C] identity found by equals()        → true
 *  ├─ [D] found by equalsIgnoreCase() only  → true  (AD/LDAP fallback)
 *  └─ [E] not found by either               → false
 */
```

每个测试方法头部注释格式：`[路径 X]` + 触发条件 + 期望结果

```java
// [Path D] case-insensitive fallback → grants match regardless of case (AD/LDAP compat)
// Condition: exact equals() fails, equalsIgnoreCase() succeeds
@Test
void check_caseInsensitiveFallback_returnsTrue() { ... }
```

---

## 2B. 行为编排/策略类：提取业务场景

不要逐行追踪 if/else，而是站在调用方视角问以下四类问题，每个问题答案就是一个或一组场景：

```
1. 最基本的放行和拒绝是什么？
   → 满足最小授权条件  = allowed
   → 完全没有授权      = denied

2. 有哪些"绕过"路径？（特权身份、快捷通道）
   → 每条绕过路径单独一个场景

3. 授权可以通过哪些方式间接获得？（继承、委托、聚合）
   → 每条间接路径单独一个场景，包括链断裂时的 denied 场景

4. 多条件组合时逻辑是 AND 还是 OR？
   → 枚举关键组合：全满足 / 部分满足 / 全不满足
```

写成场景表（格式：前提 → 结果），然后每行对应一个或一组测试：

```
场景表模板：
  [基本放行]   最小授权条件成立                 → allowed
  [基本拒绝]   无任何授权                       → denied
  [绕过 #1]   特权身份 X                        → allowed（跳过所有检查）
  [间接 #1]   通过 A→B 委托链获得权限           → allowed
  [间接 #2]   委托链中 B 无权限（链断裂）        → denied
  [组合 AND]  条件 P 满足，条件 Q 不满足         → denied
  [组合 AND]  条件 P、Q 均满足                  → allowed
  [空输入]    关键参数为 null / empty            → denied 或 exception（明确哪个）
```

每个测试方法头部注释格式：`[场景]` + 前提 + 期望结果

```java
// [Scenario: indirect #1] user inherits READ via role chain A→B → allowed
// Setup: user has role A; role A inherits role B; role B has READ grant; recursive=true
@Test
void checkPermission_userGrantedViaRoleChain_returnsTrue() { ... }
```

---

## 2C. 存储适配器/Repository 类：覆盖存储契约

不要追踪业务决策逻辑，而是围绕五个维度验证存储行为：

```
1. CRUD round-trip    put(k,v) → get(k) == v
                      put(k,v1) → put(k,v2) → get(k) == v2   (overwrite)
                      put(k,v) → remove(k)  → get(k) == null

2. Key mapping        不同输入类型产生正确 key，list() 能将 key 正确解析回领域对象
                      同时验证不同合法输入不会意外映射到同一 key / 同一存储槽位

3. Bulk / scope       filter 只影响命中条目，不影响其他条目

4. Lifecycle          init 幂等（多次调用不重复初始化）
                      tearDown/close 幂等（storage 已为 null 时安全无抛）
                      若涉及 Future / 异步后端，补失败路径：异常、超时、中断后的行为语义

5. Event mutation     rename 事件 → 引用被重写
                      remove 事件 → 引用被清除

6. State carry-over   当前操作受历史状态影响时，覆盖“有旧状态 / 无旧状态” × 标记位组合
                      验证哪些字段继承旧值，哪些字段必须被本次输入覆盖
```

写成状态转换表（格式：前置状态 + 操作 → 期望后置状态），然后每行对应一个测试：

```
状态转换表模板：
  [Op: put→get]        空存储 + put(k,v)           → get(k) == v
  [Op: overwrite]      get(k)==v1 + put(k,v2)      → get(k) == v2
  [Op: remove]         get(k)==v  + remove(k)       → get(k) == null
  [Key: type-A]        输入类型 A                   → key 格式正确，list() 解析一致
  [Key: type-B]        输入类型 B（如 IdentityID）   → 使用正确序列化方式
  [Key: collision]     输入 A、B 均合法              → 不会写入同一 key / 不会互相覆盖
  [Bulk: scope-match]  scope=X 的条目               → 被批量操作影响
  [Bulk: scope-other]  scope=Y 的条目               → 不受影响
  [Lifecycle: init×2]  init() 已完成后再次调用       → 状态不变，无副作用
  [Lifecycle: close×2] close() 后再次调用            → 无异常
  [Failure: timeout]   底层写入超时 / 失败            → 返回值、持久化结果、线程状态符合契约
  [Event: rename]      存储含旧引用 + rename 事件    → 引用重写为新名
  [Event: remove]      存储含旧引用 + remove 事件    → 旧引用被清除
  [Carry: flag×history] 标记位变化 + 历史状态存在性   → 继承/覆盖规则正确
```

每个测试方法头部注释格式：`[维度: 操作→断言]` + 前置状态 + 期望后置状态

```java
// [Op: overwrite] second put on same key replaces first value
// Pre: get(k) == v1; Op: put(k, v2); Post: get(k) == v2
@Test
void put_sameKey_replacesExistingValue() { ... }
```

---

## 3. `@ParameterizedTest` 聚合规则

- **同一路径节点 / 同一场景类别** + 只是参数不同 → 合并为一个 `@ParameterizedTest`
- `@MethodSource` 里每个 `Arguments.of(...)` 前加一行注释说明变体意图：

```java
private static Stream<Arguments> cases() {
    return Stream.of(
        // ✓ user granted directly
        Arguments.of(userPerm, READ, true),
        // ✓ granted via role (inheritance)
        Arguments.of(rolePerm, READ, true),
        // ✗ no permission granted
        Arguments.of(null, READ, false)
    );
}
```

---

## 4. Risk 过滤（控制 case 数量）

| Risk | 含义 | 生成 |
|------|------|------|
| 3 | 权限绕过、多条件组合、继承链截断、suspect 列表中的每条缺陷 | 必须 |
| 2 | happy path、反向 false case | 每条路径/场景 ≥1 个 |
| 1 | 与已有 case 高度相似的变体 | 跳过 |
