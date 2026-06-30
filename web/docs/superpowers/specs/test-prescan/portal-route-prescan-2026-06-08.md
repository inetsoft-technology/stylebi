# portal Route Pre-scan Report

**日期**: 2026-06-08（2026-06-12 补充扫描，新增 65 个组件；2026-06-24 补充扫描，新增 6 个组件）
**候选组件数**: 148（原 142，2026-06-24 新增 6）| **建议推进**: 82 | **建议跳过**: 0 | **建议暂缓**: 66 | **待审核**: 121 | **多 pass 组件**: 41
**测试进度**: ✅已测试 59 / 148 | 待测 23 / 148 | ⏭ 跳过 0 / 148 | ⏸️暂缓 66 / 148

## 状态说明
- 第一列「状态」初始为「待审核」，人工审核后改为 ✅已测试 / ⏭已跳过 / ⏸️暂缓
- ⚠️ 有旧spec — 新测试通过后在同 PR 内删除旧 .spec.ts
- 「旧 spec 备注」列记录旧测试中不易从源码推断的 case，生成新测试时参考

## 分类说明
- **single-pass**：logic_lines ≤ 500 AND dispatch ≤ 2 AND async ≤ 5 → `ComponentName.tl.spec.ts`
- **multi-pass**：logic_lines > 500 OR dispatch ≥ 3 OR async > 5 → 多文件 pass 计划
- single-pass 按需追加：async≥3 竞态 / async≥1 内存泄漏 / dispatch≥3 边界

## 候选组件清单

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| ✅已测试 | PortalAppComponent | 339 | 0 | 16 | **multi-pass** | ✅ 推进 |  |  | P1: app.component.interaction.tl.spec.ts ✅<br>P2: app.component.risk.tl.spec.ts ✅ |
| ⏸️暂缓 | CustomTabComponent | 34 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | DashboardTabComponent | 215 | 3 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: dashboard-tab.component.interaction.tl.spec.ts<br>P2: dashboard-tab.component.risk.tl.spec.ts<br>P3: dashboard-tab.component.display.tl.spec.ts |
| ⏸️暂缓 | DashboardLandingComponent | 50 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | AssetItemListViewComponent | 159 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ✅已测试 | DataDatasourceBrowserComponent | 756 | 0 | 21 | **multi-pass** | ✅ 推进 | ⚠️ data-datasource-browser.component.tl.spec.ts | 🔁 Regression-sensitive: refresh combines route params, sorting, selection mapping and status fetch.; 🔁 Regression-sensitive: refreshed objects repla | P1: data-datasource-browser.component.interaction.tl.spec.ts<br>P2: data-datasource-browser.component.risk.tl.spec.ts |
| ✅已测试 | DatasourceSelectionViewComponent | 127 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: datasource-selection-view.component.interaction.tl.spec.ts<br>P2: datasource-selection-view.component.risk.tl.spec.ts |
| ✅已测试 | DatabaseDataModelBrowserComponent | 723 | 0 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: database-data-model-browser.component.interaction.tl.spec.ts<br>P2: database-data-model-browser.component.risk.tl.spec.ts |
| ✅已测试 | DatabaseDataModelToolbarComponent | 126 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | database-data-model-toolbar.component.tl.spec.ts (31/31 ✅) |
| ✅已测试 | DatabaseVPMBrowserComponent | 338 | 0 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: database-vpm-browser.component.interaction.tl.spec.ts<br>P2: database-vpm-browser.component.risk.tl.spec.ts |
| ✅已测试 | DataModelScriptPane | 247 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | data-model-script-pane.component.tl.spec.ts (21/21 ✅) |
| ✅已测试 | DatabasePhysicalModelComponent | 1153 | 4 | 28 | **multi-pass** | ✅ 推进 |  |  | P1: database-physical-model.component.interaction.tl.spec.ts<br>P2: database-physical-model.component.risk.tl.spec.ts<br>P3: database-physical-model.component.display.tl.spec.ts |
| ✅已测试 | LogicalModelAttributeDialog | 107 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | AutoDrillDialog | 531 | 0 | 15 | **multi-pass** | ✅ 推进 |  |  | P1: auto-drill-dialog.component.interaction.tl.spec.ts<br>P2: auto-drill-dialog.component.risk.tl.spec.ts |
| ✅已测试 | ParameterDialog | 242 | 2 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | SelectWorksheetDialog | 131 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 | AttributeFormattingPane | 232 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ⏸️暂缓 | LogicalModelEntityDialog | 59 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | LogicalModelExpressionDialog | 230 | 0 | 6 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | LogicalModelPropertyPane | 591 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 | LogicalModelComponent | 275 | 2 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: logical-model.component.interaction.tl.spec.ts<br>P2: logical-model.component.risk.tl.spec.ts |
| ✅已测试 | PhysicalGraphPane | 237 | 0 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: physical-graph-pane.component.interaction.tl.spec.ts<br>P2: physical-graph-pane.component.risk.tl.spec.ts |
| ✅已测试 | PhysicalJoinEditPane | 120 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: physical-join-edit-pane.component.interaction.tl.spec.ts<br>P2: physical-join-edit-pane.component.risk.tl.spec.ts |
| ✅已测试P1 | PhysicalModelNetworkGraphComponent | 779 | 0 | 19 | **multi-pass** | ✅ 推进 |  |  | P1: physical-model-network-graph.component.interaction.tl.spec.ts (37/37 ✅)<br>P2: physical-model-network-graph.component.risk.tl.spec.ts (worker crash, 搁置) |
| ✅已测试 | DatabaseVPMComponent | 270 | 1 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: database-vpm.component.interaction.tl.spec.ts (32/32 ✅)<br>P2: database-vpm.component.risk.tl.spec.ts (18/18 ✅) |
| ✅已测试 | VPMConditionsComponent | 259 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | vpm-conditions.component.tl.spec.ts (37/37 ✅) |
| ✅已测试 | VPMHiddenColumnsComponent | 331 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: vpm-hidden-columns.component.interaction.tl.spec.ts (29/29 ✅)<br>P2: vpm-hidden-columns.component.risk.tl.spec.ts (13/13 ✅) |
| ⏸️暂缓 | VPMTestComponent | 63 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | DatasourcesDatabaseComponent | 662 | 0 | 12 | **multi-pass** | ✅ 推进 |  |  | P1: datasources-database.component.interaction.tl.spec.ts<br>P2: datasources-database.component.risk.tl.spec.ts |
| 待审核 | DriverWizardComponent | 275 | 0 | 24 | **multi-pass** | ✅ 推进 |  |  | P1: driver-wizard.component.interaction.tl.spec.ts<br>P2: driver-wizard.component.risk.tl.spec.ts |
| ⏸️暂缓 | EditPropertyDialogComponent | 50 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | DatasourcesDatasourceEditorComponent | 258 | 0 | 8 | **multi-pass** | ✅ 推进 | ✅ datasources-datasource-editor.component.interaction.tl.spec.ts (35 tests)<br>✅ datasources-datasource-editor.component.risk.tl.spec.ts (10 tests) |  | P1: datasources-datasource-editor.component.interaction.tl.spec.ts<br>P2: datasources-datasource-editor.component.risk.tl.spec.ts |
| ✅已测试 | DatasourcesDatasourceComponent | 346 | 0 | 7 | **multi-pass** | ✅ 推进 | ✅ datasources-datasource.component.interaction.tl.spec.ts (33 tests)<br>✅ datasources-datasource.component.risk.tl.spec.ts (10 tests) |  | P1: datasources-datasource.component.interaction.tl.spec.ts<br>P2: datasources-datasource.component.risk.tl.spec.ts |
| ✅已测试 | DatasourcesXmlaComponent | 602 | 0 | 35 | **multi-pass** | ✅ 推进 | ✅ datasources-xmla.component.interaction.tl.spec.ts (47 tests)<br>✅ datasources-xmla.component.risk.tl.spec.ts (12 tests) |  | P1: datasources-xmla.component.interaction.tl.spec.ts<br>P2: datasources-xmla.component.risk.tl.spec.ts |
| ✅已测试 | DataFolderBrowserComponent | 938 | 1 | 17 | **multi-pass** | ✅ 推进 | ⚠️ data-folder-browser.component.tl.spec.ts | Regression-sensitive: route scope, folder-first sorting, and selected-object remapping can; Regression-sensitive: failed search must not leave stale r | P1: data-folder-browser.component.interaction.tl.spec.ts<br>P2: data-folder-browser.component.risk.tl.spec.ts |
| ✅已测试 | DataFolderListViewComponent | 151 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | data-folder-list-view.component.tl.spec.ts (18/18 ✅) |
| ✅已测试 | FilesBrowserComponent | 119 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 | DataSourcesTreeViewComponent | 1463 | 7 | 19 | **multi-pass** | ✅ 推进 | ⚠️ data-sources-tree-view.component.tl.spec.ts | Group 1 — changeDataSourcesTree: always returns false [Risk 3] (confirmed bug); 🔁 Regression-sensitive: return false instead of found breaks the recu | P1: data-sources-tree-view.component.interaction.tl.spec.ts<br>P2: data-sources-tree-view.component.risk.tl.spec.ts<br>P3: data-sources-tree-view.component.display.tl.spec.ts |
| ⏸️暂缓 | DataTabComponent | 65 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | InputNameDescDialog | 113 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | input-name-desc-dialog.component.tl.spec.ts (9/9 ✅) |
| ✅已测试 | MoveAssetDialogComponent | 121 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | move-asset-dialog.component.tl.spec.ts (11/11 ✅) |
| 待审核 | AnalyzeMVDialog | 280 | 0 | 30 | **multi-pass** | ✅ 推进 |  |  | P1: analyze-mv-dialog.component.interaction.tl.spec.ts<br>P2: analyze-mv-dialog.component.risk.tl.spec.ts |
| ⏸️暂缓 | AnalyzeMVPane | 58 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试 | CreateMVPane | 112 | 0 | 0 | **single-pass** | ✅已测试 |  |  | single pass |
| ⏸️暂缓 | ArrangeDashboardDialog | 94 | 0 | 2 | **single-pass** | 暂缓 | ⚠️ arrange-dashboard-dialog.spec.ts | Bug #18799 not enabled dashboard should display in list | single pass (+内存泄漏) |
| ✅已测试 | AutoJoinTablesDialog | 144 | 0 | 3 | **single-pass** | ✅已测试 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | ChooseTableDialog | 204 | 1 | 7 | **single-pass** | ✅已测试 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | EditDashboardDialog | 179 | 0 | 7 | **multi-pass** | ✅ 推进 | ⚠️ edit-dashboard-dialog.spec.ts | Bug #18620 Don't allow special characters in the name; Bug #21678 should allow & -+ | P1: edit-dashboard-dialog.component.interaction.tl.spec.ts<br>P2: edit-dashboard-dialog.component.risk.tl.spec.ts |
| ⏸️暂缓 | InlineViewDialog | 66 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | PhysicalTableAliasesDialog | 101 | 0 | 2 | **single-pass** | ✅已测试 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | PreferencesDialog | 81 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | VPMConditionDialog | 62 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | PortalRedirectComponent | 26 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | RepositoryDesktopViewComponent | 65 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核 | ReportTabComponent | 280 | 2 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: report-tab.component.interaction.tl.spec.ts<br>P2: report-tab.component.risk.tl.spec.ts |
| ⏸️暂缓 | PortalReportComponent | 93 | 0 | 3 | **single-pass** | 暂缓 |  |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | WelcomePageComponent | 39 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | TaskActionPane | 464 | 0 | 8 | **multi-pass** | ✅ 推进 | ~~⚠️ task-action-pane.component.spec.ts~~ | Bug #19890 should pop up warning when to delete action; test: should not copy when no action is selected; test: should not copy when multiple actions  | P1: task-action-pane.component.interaction.tl.spec.ts<br>P2: task-action-pane.component.risk.tl.spec.ts |
| ✅已测试 | TaskConditionPane | 1073 | 3 | 3 | **multi-pass** | ✅ 推进 | ~~⚠️ task-condition-pane.spec.ts~~ | Bug #19519 should show current date when not set; Bug #19687 should show set date; Bug #19517 select and deselect all function for weekly | P1: task-condition-pane.component.interaction.tl.spec.ts<br>P2: task-condition-pane.component.risk.tl.spec.ts<br>P3: task-condition-pane.component.display.tl.spec.ts |
| 待审核 | ExecuteAsDialog | 190 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | TaskOptionsPane | 189 | 0 | 13 | **multi-pass** | ✅ 推进 | ⚠️ task-options-pane.component.spec.ts | Bug #19508; Bug #19745; Bug #21420 should get correct locale info when set 'Default' | P1: task-options-pane.component.interaction.tl.spec.ts<br>P2: task-options-pane.component.risk.tl.spec.ts |
| 待审核 | ScheduleTaskEditorComponent | 192 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: schedule-task-editor.component.interaction.tl.spec.ts<br>P2: schedule-task-editor.component.risk.tl.spec.ts |
| ⏸️暂缓 | SelectDashboardDialog | 69 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | EditTaskFolderDialog | 69 | 0 | 4 | **single-pass** | 暂缓 |  |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | MoveTaskDialogComponent | 97 | 0 | 3 | **single-pass** | 暂缓 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | ScheduleTaskListComponent | 844 | 1 | 58 | **multi-pass** | ✅ 推进 |  |  | P1: schedule-task-list.component.interaction.tl.spec.ts<br>P2: schedule-task-list.component.risk.tl.spec.ts |
| 待审核 | InputNameDialog | 112 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ input-name-dialog.component.spec.ts | Bug #19762 Show the error message on the input; test: Show the error message on the input | single pass (+内存泄漏) |
| ✅已测试 | ActionsContextmenuComponent | 154 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ actions-contextmenu.component.spec.ts | test: should not create a dropdown when there are no visible actions | single pass (+内存泄漏) |
| ✅已测试 | NotificationsComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ notifications.component.spec.ts | 无 | single pass (+内存泄漏) |
| ✅已测试 | RepositoryTreeComponent | 461 | 0 | 5 | **single-pass** | ✅已测试 | 24/24✅ |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | ResponsiveTabsComponent | 80 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | SplitPane | 120 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ⏸️暂缓 | TabularViewComponent | 59 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试 | TreeNodeComponent | 506 | 3 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: tree-node.component.interaction.tl.spec.ts (74/74 ✅)<br>P2: tree-node.component.risk.tl.spec.ts (6/6 ✅)<br>P3: tree-node.component.display.tl.spec.ts (21/21 ✅) |
| ✅已测试 | TreeComponent | 754 | 6 | 6 | **multi-pass** | ✅ 推进 | ~~⚠️ tree.spec.ts~~ | Bug #17221: P1 search() 覆盖；Bug #17336: P3 DOM 用例覆盖 — 旧 spec 已可安全删除 | P1: tree.component.interaction.tl.spec.ts (47/47 ✅)<br>P2: tree.component.risk.tl.spec.ts (8/8 ✅)<br>P3: tree.component.display.tl.spec.ts (8/8 ✅，含 Bug #17336 DOM 覆盖) |
| ⏸️暂缓 | AiAssistantDialogComponent | 35 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试 | AiAssistantPanelComponent | 177 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |

## 多 pass 组件详情

### PortalAppComponent

**Pass 1** (`app.component.interaction.tl.spec.ts`)
- Methods: openComposerEnabled, subscribe, showGettingStarted, refreshCreationPermissions, logOut, profiling, showDocument, getTab, getDashboardTabTooltip, getDataTabTooltip, getScheduleTabTooltip, isTabSelected, openComposer, launchComposer, showListings
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`app.component.risk.tl.spec.ts`)
- Methods: updateAccessibility, setTabOrder, checkDefaultTab, showPreferences, handleMessageEvent
- Reason: async≥3：竞态 / destructive / state inconsistency

### DashboardTabComponent

**Pass 1** (`dashboard-tab.component.interaction.tl.spec.ts`)
- Methods: updateSelectedDashboard, selectTab, openArrangeDashboardDialog
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`dashboard-tab.component.risk.tl.spec.ts`)
- Methods: selectDashboard, openDashboard, reloadDashboard, updateModel, newDashboard, editDashboard, deleteDashboard, editDeleteDashboardDisabled
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`dashboard-tab.component.display.tl.spec.ts`)
- Methods: getDashboardLabel
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### DataDatasourceBrowserComponent

**Pass 1** (`data-datasource-browser.component.interaction.tl.spec.ts`)
- Methods: clickItem, processSetComposedDashboardCommand, disableAction, updateDataSources, set, refreshDataSource, refreshSearchBrowser, getParentPath, getParentRouterLinkParams, updateSortOptions, manageDrivers, showListings, getDatasourceStatusIcon, isDataSourceFolder, isDataSource, toggleSearch, addFolder, renameFolder, getDatasourceIcon, editDataSource, handleResponseDatasource, createQuery, createPhysicalView, createVPM, processCreateQueryEventCommand, processMessageCommand, openComposer, getDataSourceObjectType, getAssemblyName, dragAsset, getEntryLabel, dropAssets, dataTreeDragToPane, then, selectAllChecked, selectAllChanged, getDateLabel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`data-datasource-browser.component.risk.tl.spec.ts`)
- Methods: slice, addFolderConfig, toggleSelectTooltip, viewAssets, moveDisable, add, ngAfterViewInit, subscribe, refreshAllData, loadDataSourceStatus, fetchDataSourceStatuses, sortDataSources, search, clearSearch, moveDataSource, moveSelected, deleteItem, handleResponse, moveDataSources0, isSelectedItem, updateAssetSelection, toggleSelectionState, isSelectionDeletable, isSelectionEditable, updateSelection, deleteSelected, deleteSelected0, updateSelectedItems
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourceSelectionViewComponent

**Pass 1** (`datasource-selection-view.component.interaction.tl.spec.ts`)
- Methods: getListings, isCreateDisabled, cancel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`datasource-selection-view.component.risk.tl.spec.ts`)
- Methods: canDeactivate, create, subscribe
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabaseDataModelBrowserComponent

**Pass 1** (`database-data-model-browser.component.interaction.tl.spec.ts`)
- Methods: listModel, getListColumns, getRouterPath, toggleSelectionState, search, routeToFolder, getParentPath, getTypeLabel, getBasedView, refreshSearchBrowser, updateSortOptions, sortModels, editModel, changeSelectedItems, refreshModels, updateModels, getIcon, dragSupportFun, getChildren, clickItem, isRoot, addDataModelFolder, addPhysicalView, addLogicalModel0, addLogicalModel, addExtendModel, renameModel, isPhysicalView, isLogicalModel, isFolder, getActionMessage, getRenameMessage, actionCallback, refreshListAndTree, isBaseModelSelected, isExtend, setShowDetailsItem, openContextmenu, createActions, disableAction, getEntryIcon
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`database-data-model-browser.component.risk.tl.spec.ts`)
- Methods: clearSearch, subscribe, deleteModel, getDeleteMessage, moveModel, moveSelected, deleteSelected, moveDisable, dragAssetsItems, dropAssetsItems, moveModels0, dataTreeDragToPane, getAssetLabel
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabaseVPMBrowserComponent

**Pass 1** (`database-vpm-browser.component.interaction.tl.spec.ts`)
- Methods: sortOptionsChanged, toggleSelectionState, editModel, addModel, renameModel, setShowDetailsItem, openTreeContextmenu, createActions
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`database-vpm-browser.component.risk.tl.spec.ts`)
- Methods: editable, deletable, currentSearchFolderLabel, search, reSearch, clearSearch, refreshModels, deleteModel, deleteSelected
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabasePhysicalModelComponent

**Pass 1** (`database-physical-model.component.interaction.tl.spec.ts`)
- Methods: physicalModel, databaseName, parent, modelInitializing, displayTitle, subscribe, refreshTreeSelectStatus, refreshLeafNodeSelectStatus, some, getTablePath, showAutoAliasDialog, showAutoAliasDialog0, createAutoAliasByGraph, showCreateAliasDialog, createAliasTable, forEach, findEditingInlineNode, toggleInlineViewEditor, editView, refreshDatabaseRoots, addAliasInlineNodes, isAliasTable, then, notify, save, onPhysicalGraph, selectPhysicalGraphNode, changeEditingTable, changeEditingTableByName, databaseParr, openFolder, isDuplicateTableName, createPhysicalTableModel, canDeactivate, search, expandAll, keepSelectedNodes, refreshModel, toggleTreeCollapsed, splitPaneDragEnd, onJoinEditing, onKeyDown, refreshEditingTable, createTableActions, isTable, isBaseTable
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`database-physical-model.component.risk.tl.spec.ts`)
- Methods: updateForeignJoins, createAlias, editAlias, treePaneCollapsed, refreshView, createTableNodeLabel, openPhysicalModel, createPhysicalModel, refreshWarnings, refreshWarnings0, refreshSupportFullOuterJoin, toggleRepositoryTreePane, expandNode, checkboxToggledNode, selectNode, pipe, loadDatabaseTree, removePhysicalTable, resetSearchMode, keepExpandedNodes, getExpandedPaths, ngDoCheck, onModified, graphNodesSelected, tableRemoved, selectAndExpandToPath, updateDataTreePane
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`database-physical-model.component.display.tl.spec.ts`)
- Methods: showAutoJoinTablesDialog, showCreateInlineViewDialog, showTreeContextMenu
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### AutoDrillDialog

**Pass 1** (`auto-drill-dialog.component.interaction.tl.spec.ts`)
- Methods: autoDrillModel, selectedDrills, editIndex, loadRepositoryTreeAndselectDrill, ngAfterViewInit, drillLabel, getDrillPathLabel, selectDrill, addDrill, getNewDrillName, openSelectWorksheetDialog, getWorksheetFields, selectNode, updateVariable, addParameter, editParameter, openParameterDialog, initFormControl, subscribe, getDisplayParam, changeLinkType, changeLinkTarget, selectAndExpandToPath, ok
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`auto-drill-dialog.component.risk.tl.spec.ts`)
- Methods: selectedAssetNode, editDrill, getDrillWorksheet, moveDrillDown, moveDrillUp, deleteDrill, getAssetNodeIdentifier, removeParameter, removeAllParameters, getFirstErrorMessage, nameControl, linkControl, clearForm, clearSelectedNode, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### LogicalModelExpressionDialog

**Pass 1** (`logical-model-expression-dialog.component.tl.spec.ts`)
- Methods: nameControl, parentControl, initFormControl, loadFields, ok, updateExpression, cancel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

### LogicalModelPropertyPane

**Pass 1** (`logical-model-property-pane.component.tl.spec.ts`)
- Methods: logicalModel, editingEle, attributeOrderChanged, updateExistNames, getSelectedItem, isElementSelected, editingElement, moveEntityDown, moveEntityUp, deleteEntityByIndex, keyDown, deleteSelectedItem, checkOuterDependencies, deleteSelectedItem0, resetSelectedStatus, isEntity, hasSelected, validSelected, deleteEntity, getEntity, getAttribute, deleteAttribute, checkInvalidAttributes, sortElements, sortEntities, sortAttributes, showEntityDialog, trim, showAddAttributeDialog, getSelectedEntity, showAddExpressionDialog, addAttributes, checkModified, isDuplicateEntity, isDuplicateAttribute, shiftSelect, entityToggle, onDeleteAttribute, canDelete, itemDeletable, resetState
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

### LogicalModelComponent

**Pass 1** (`logical-model.component.interaction.tl.spec.ts`)
- Methods: ngDoCheck, parent, displayTitle, notify, save, checkModified, canDeactivate, createExtendedModel, settings, lmContentHeight
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`logical-model.component.risk.tl.spec.ts`)
- Methods: refreshModel, getSettings
- Reason: async≥3：竞态 / destructive / state inconsistency

### PhysicalGraphPane

**Pass 1** (`physical-graph-pane.component.interaction.tl.spec.ts`)
- Methods: viewport, restoreGraphViewModel, restoreJoinEditPaneModel, toggleFullScreen, zoomLayout, zoom, updateScale, isAutoLayoutSelected, isZoomItemSelected, zoomOutEnabled, zoomInEnabled, keydown, autoLayout, editJoinRuntimeId, closeJoinEditPane, fullScreenTooltip
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`physical-graph-pane.component.risk.tl.spec.ts`)
- Methods: modelInitializing, ngAfterViewChecked, updateGraphPaneSize, refreshPhysicalGraphModel
- Reason: async≥3：竞态 / destructive / state inconsistency

### PhysicalJoinEditPane

**Pass 1** (`physical-join-edit-pane.component.interaction.tl.spec.ts`)
- Methods: jsp, findColumnIndex
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`physical-join-edit-pane.component.risk.tl.spec.ts`)
- Methods: ngAfterViewInit, ngAfterViewChecked, sendHeartBeat, reorderColumns, movePosition
- Reason: async≥3：竞态 / destructive / state inconsistency

### PhysicalModelNetworkGraphComponent

**Pass 1** (`physical-model-network-graph.component.interaction.tl.spec.ts`)
- Methods: scale, bind, ngAfterViewChecked, ngAfterViewInit, scrollPosition, isAutoAliasNode, showJoinActions, getJoinActions, openJoinEditPane, setRepaintTimer, setContainer, checkJoinCompatibility, graphEndpoints, refreshDragSelection, addEndpoint, showEndpoints, each, hideEndpoints, registerNode, selectNode, isSameTableAutoAlias, fireSelectedNodesChanged, forEach, onSelectionBox, getThumbnailClasses, connectNode, isColumnExist, isHighlight, some, getJoinTooltip, convertToHTMLCharacterEntity, getSelectedNode
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`physical-model-network-graph.component.risk.tl.spec.ts`)
- Methods: connectionDeletable, hasBaseJoin, removeJoinCondition, deleteNode, refreshAnchors, setDraggable, moveNodes, contextMenu, createActions, clearTableEnabled, clearJoinEnabled, clearTable, clearJoin, keydown, removeSelectTables, doRemove, selectAll, clearSelection
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabaseVPMComponent

**Pass 1** (`database-vpm.component.interaction.tl.spec.ts`)
- Methods: vpm, changeHiddenExpression, resetVPM, selectVPMTab, refreshedColumns, canDeactivate, updateLookupList
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`database-vpm.component.risk.tl.spec.ts`)
- Methods: refreshTestData, refreshOperations, subscribe, refreshVPM, isModified, saveVPM
- Reason: async≥3：竞态 / destructive / state inconsistency

### VPMHiddenColumnsComponent

**Pass 1** (`vpm-hidden-columns.component.interaction.tl.spec.ts`)
- Methods: selectedColumns, refreshFilterTreeModel, initDataSourceTree, openFolder, subscribe, expandAll, loadFullDatabaseTree, showLoadTimeOutMess, addHiddenColumn, supportAddAction, addNodeToHiddenColumn, addTableNodeToHiddenColumn, addColumnNodeToHiddenColumn, addAllColumnsToHidden0, selectGrantRole, selectAvailableRole, addGrantRoles, getTreeNodeIcon, getBaseName
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`vpm-hidden-columns.component.risk.tl.spec.ts`)
- Methods: selectHiddenColumn, isSelectedHiddenColumn, hasSelectedHiddenColumn, clearHiddenSelected, addAllToHiddenColumns, addAllColumnsToHidden, removeHiddenColumn, clearHiddenColumns, clearSelectedGrantRoles, removeGrantRoles
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesDatabaseComponent

**Pass 1** (`datasources-database.component.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, setModel, isCreateDB, updateAdditionalList, canDeactivate, beforeunloadHandler, updateFiles, createDriver, driverAvailable, databaseNameRequired, afterDatabaseSave, getDatabasePath, showMessage, editAdditional, checkDuplicate, renameAdditional, isDatabaseEqual, needSave, close, lastIndexOf, isCustom, setCustomUrl, testQueryEnabled, isFilePathEnabled, getDBUrl, typeChanged, refreshDefaultTestQuery, testQuery, setTestQuery, initForm, newProperty, selectProperty, isSelectedProperty, editProperty, showEditPropertyDialog, keys, changeCustomEditMode, refreshMetadata, subscribe
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`datasources-database.component.risk.tl.spec.ts`)
- Methods: selectAdditional, additionalSelected, newAdditional, deleteAdditional, deleteAdditional0, additionalDataSources, deleteAdditionals, getSelectedAdditional, deleteProperty, getExistingNames
- Reason: async≥3：竞态 / destructive / state inconsistency

### DriverWizardComponent

**Pass 1** (`driver-wizard.component.interaction.tl.spec.ts`)
- Methods: subscribe, searchMaven, selectDriverFile, catchError, error, scanDrivers, pluginExists, return, uploadFilesRequired, mavenCoordRequired, trackByIdx, selectDriver
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`driver-wizard.component.risk.tl.spec.ts`)
- Methods: addDriverFiles, removeDriverFiles, isNextDisabled, next, cancel, uploadDrivers, createDriver
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesDatasourceEditorComponent

**Pass 1** (`datasources-datasource-editor.component.interaction.tl.spec.ts`)
- Methods: onViewChanged, onValidChanged, hasRefreshButton, refreshMetadata
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`datasources-datasource-editor.component.risk.tl.spec.ts`)
- Methods: datasource, usedNames, nameValid, updateDatasourceName, authorize, pipe, refreshView, subscribe, initView, hasCancelButton, buttonClicked, clearButtonClicks, clearButtonLoading
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesDatasourceComponent

**Pass 1** (`datasources-datasource.component.interaction.tl.spec.ts`)
- Methods: beforeunloadHandler, datasourceChanged, canDeactivate, originalName, updateAdditionalList, selectAdditional, getSelectedAdditional, additionalSelected, newAdditional, additionalDataSources, editAdditional, renameAdditional, checkDuplicate, ok, subscribe, saveDataSource, close, lastIndexOf, updateDatasourceValid
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`datasources-datasource.component.risk.tl.spec.ts`)
- Methods: refreshDataSource, deleteAdditional, deleteAdditionals
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesXmlaComponent

**Pass 1** (`datasources-xmla.component.interaction.tl.spec.ts`)
- Methods: refreshMetaLabel, selectedDimensionMember, model, updateEnable, setFormToModel, refreshSourceModel, selectCatalog, loadCatalogs, loadMetadata, expandSelectedTreeNode, expandSelectedTreeNode0, syncDomain, getMemberKey, getDimensionKey, getCSSIcon, selectedNode, changeAsDate, changeLocal, changeDatePattern, changeOriginalOrder, openAutoDrillDialog, updateFormatString, viewSampleData, testDatabase, canDeactivate, ok, close, lastIndexOf, clickLocaleListBtn, forEach
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`datasources-xmla.component.risk.tl.spec.ts`)
- Methods: drillString, selectedDimension, initForm
- Reason: async≥3：竞态 / destructive / state inconsistency

### DataFolderBrowserComponent

**Pass 1** (`data-folder-browser.component.interaction.tl.spec.ts`)
- Methods: getAssemblyName, refreshFolderBrowser, set, handleBrowserRefreshError, refreshSearchBrowser, toggleSelectTooltip, sortView, openFolder, editWorksheet, renameAsset, handleResponse, handleEditAssetError, viewAssets, toggleSelectionState, isFolderEditable, toggleSearch, search, findAsset, selectFile, convertToAssetItem, selectAllChecked, selectAllChanged, updateSelectedItems, addFolder, getRootAssets, materializeAsset, selectChanged, dragAssets, getEntryLabel, getEntryIcon, assetsDroped, dataTreeDragToPane, createInfoByAssetEntry, showWSDetailsByDataSourcesTree
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`data-folder-browser.component.risk.tl.spec.ts`)
- Methods: slice, processMessageCommand, addFolderConfig, moveSelectedConfig, newWorksheetDisabled, newWorksheet, isSelectionDeletable, isSelectionEditable, moveDisable, deleteSelected, moveAsset, deleteAsset, handleDeleteError, refreshBrowserContent, clearSearch, moveSelected, moveAssets, moveAssets0
- Reason: async≥3：竞态 / destructive / state inconsistency

### DataSourcesTreeViewComponent

**Pass 1** (`data-sources-tree-view.component.interaction.tl.spec.ts`)
- Methods: add, processSetComposedDashboardCommand, processMessageCommand, initSeletedNodes, changeFolder, changeDataSourcesTree, getPrivateWorksheetNodeParent, getNodePath, replaceStr, getDataNavigationTree, isEditDataSource, updateSelectedNodes, keepExpandedNodes, selectNode, expandNode, openDatasourcesFolder, forEach, onNodeDrag, onNodeDrop, subscribe, getParentPath, getParentPath0, substring, checkDataFoldersDuplicate, setCurrentFolderPath, refreshPaths, openFolder, updateDatasourceNodes, selectAndExpandToDataModel, contextMenu, createActions, isVpmVisible, actionCallback, onScroll, hasMenuFunction, getDuplicateCheckUri, canCreateChildren, canCreateLogicalModel, canRename, canNewWorksheet, canNewDataSource, canCreateQuery, createQuery, openComposer, getDataSourceObjectType, addDataModel, splitModelName, encodeString, byteEncode, newFolderVisible, newFolder, newDataAsset, renameFolder, detailVisible, renameVisible, isDataSourceFolder, isDataWorksheetFolder
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`data-sources-tree-view.component.risk.tl.spec.ts`)
- Methods: moveDataModelAssetItems, moveDataFolderItems, showMessage, moveDataSetsAndFolders, moveDatasourceAssets, moveDatasourceInfos, moveDataAssets, selectAndExpandToPath, canDelete, deleteDataModelFolder, deleteFolder, deleteVisible
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`data-sources-tree-view.component.display.tl.spec.ts`)
- Methods: getEntryLabel, getIconFunction, getAssetIcon
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### AnalyzeMVDialog

**Pass 1** (`analyze-mv-dialog.component.interaction.tl.spec.ts`)
- Methods: showCreateMVPage, refresh, setCycle
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`analyze-mv-dialog.component.risk.tl.spec.ts`)
- Methods: refreshModels, analyzeMV, deleteMV, checkCompleted, processAnalyzeResult, map, create, showPlan, createOrUpdate, selectedMVsChanged, showPlanClicked, okClicked, closeDialog, informChangedAndClose, onRepositoryChanged
- Reason: async≥3：竞态 / destructive / state inconsistency

### ChooseTableDialog

**Pass 1** (`choose-table-dialog.component.tl.spec.ts`)
- Methods: title, searchStart, initRoot, subscribe, initSelectedTable, selectAndExpandToPath, tableNameNull, selectNode, expandNode, openFolder, getTreeNodeIcon, ok, databaseParr, cancel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

### EditDashboardDialog

**Pass 1** (`edit-dashboard-dialog.component.interaction.tl.spec.ts`)
- Methods: nodeSelected
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`edit-dashboard-dialog.component.risk.tl.spec.ts`)
- Methods: closeDialog, okClicked, editDashboard
- Reason: async≥3：竞态 / destructive / state inconsistency

### ReportTabComponent

**Pass 1** (`report-tab.component.interaction.tl.spec.ts`)
- Methods: childRouteShown, viewType, add, init, getAssemblyName, processMessageCommand, historyBarEnabled, isEntryOpened, showEntry, reloadUrl, addRecentlyViewed, editViewsheet, subscribe, collapseTree
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`report-tab.component.risk.tl.spec.ts`)
- Methods: deletedEntry
- Reason: async≥3：竞态 / destructive / state inconsistency

### TaskActionPane

**Pass 1** (`task-action-pane.component.interaction.tl.spec.ts`)
- Methods: model, action, generalActionModel, actionNames, selectSheetError, isGeneralAction, addAction, copyAction, editAction, changeView, isValid, save, showSelectDashboardDialog, updateValues, updateEmailAutoComplete, getEmails, addToAutoCompleteList, initActions, setDefaultAction, setCurrentAction, getDefaultAction, getSheetPath
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`task-action-pane.component.risk.tl.spec.ts`)
- Methods: setSelectedOption, checkSelectedOption, isTabSelected, changeActionType, changeViewsheet, getPrintLayout, clearHighlightConditions, getBookmarks, getHighlights, getParameters, getTableDataAssemblies, deleteAction, initForm, getAutoCompleteLists
- Reason: async≥3：竞态 / destructive / state inconsistency

### TaskConditionPane

**Pass 1** (`task-condition-pane.component.interaction.tl.spec.ts`)
- Methods: model, startTime, condition, timeCondition, completionCondition, conditionNames, formStartTimeData, formStartTime, formEndTime, formDate, updateStartTime, updateStartTimeData, userSetStartTime, onStartTimeDataChanged, setStartTime, updateDate, dateChange, updateEndTime, setEndTime, setSelectedOption, changeConditionType, changeView, save, addCondition, copyCondition, editCondition, updateValues, updateTimesAndDates, changeMonthRadioOption, setLocalTimeZone, initForm, addIntervalControl, addDayOfMonthControl, addWeekOfMonthControl, updateDaysOfWeekStatus, updateMonthsOfYearStatus, getWeekdayControls, getMonthControls, updateWeekdayOnly, checkTimeNotNull, checkStartTimeData, timeSmallerThan, return, atLeastOneSelected, isPresent, updateList, selectAll, initConditions, getDefaultTimeConditionModel, saveConditionType, initTimeZone, currentTimeZoneOffset, changeServerTimeZone, updateTimeZone, convertToTimeZone, convertTimeCondition, timeConditions, convertTime, isTimeCondition, loadingTasks
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`task-condition-pane.component.risk.tl.spec.ts`)
- Methods: deleteCondition, removeIntervalControl
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`task-condition-pane.component.display.tl.spec.ts`)
- Methods: showMeridian
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### TaskOptionsPane

**Pass 1** (`task-options-pane.component.interaction.tl.spec.ts`)
- Methods: locale, startDateChange, endDateChange, getExecuteAsType, disableExecuteAs, updateExecuteAs, save, openExecuteAsDialog, getGroupModel, initForm, loadingUsers
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`task-options-pane.component.risk.tl.spec.ts`)
- Methods: model, clearStartDate, clearEndDate, clearUser, getExecuteAsName
- Reason: async≥3：竞态 / destructive / state inconsistency

### ScheduleTaskEditorComponent

**Pass 1** (`schedule-task-editor.component.interaction.tl.spec.ts`)
- Methods: resetConditionListView, updateTaskName, updateOldTaskName, updateConditionModel, updateActionModel, updateOptionsModel, onCloseEditor
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`schedule-task-editor.component.risk.tl.spec.ts`)
- Methods: saveSuccess, executeAsGroup, onCancelTask
- Reason: async≥3：竞态 / destructive / state inconsistency

### ScheduleTaskListComponent

**Pass 1** (`schedule-task-list.component.interaction.tl.spec.ts`)
- Methods: loadTasks, changeShowType, initTaskList, handleError, newTask, runTask, stopTask, disableTask, changeSortType, selectTask, openError, ngAfterContentChecked, onResize, selectFolderByPath, keepExpandedNodes, selectNode, substring, selectAll, findSelectedTasks, isToggleTasksEnabledDisabled, openTreeContextmenu, hasMenuFunction, hasMenu, createActions, createNewTaskFolderAction, createEditTaskFolderAction, dragTask, getTaskOwnerLabel, isCreateTaskEnabled, getDateLabel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`schedule-task-list.component.risk.tl.spec.ts`)
- Methods: currentFolder, selectAllChecked, isFullName, mergeChange, convertTaskOwner, newFolder, parentFolder, editTask, editTaskFolder, subscribe, getTaskName, removeItems, removeFolders, navigateToTaskEditor, loadTaskFolderTree, nodeDrop, getParentPath, getTaskModel, moveTasks, moveFolder, moveItems, getMovedPaths, removeEnable, createDeleteTaskFolderAction, getMutiEditPath
- Reason: async≥3：竞态 / destructive / state inconsistency

### TreeNodeComponent

**Pass 1** (`tree-node.component.interaction.tl.spec.ts`)
- Methods: contextmenuListener, touchstartListener, touchendListener, toggleNode, clickSelectNode, mousedownSelectNode, tooltip, selectNode, doubleClickNode, hasChildren, notExpandableType, favoritesUser, dragStarted, dragOver, isDraggable, isHighLight, isSelected, isGrayedOut, getFieldName, onDrop, keepAllChildren, getSort, isToggleElementEventTarget, inSearchCollapsed, hasMenu
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`tree-node.component.risk.tl.spec.ts`)
- Methods: touchmoveListener, updateInViewport
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`tree-node.component.display.tl.spec.ts`)
- Methods: getIcon, getToggleIcon, nodeLabel, getVirtualScrollShowChildren
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### TreeComponent

**Pass 1** (`tree.component.interaction.tl.spec.ts`)
- Methods: root, showRoot, searchStr, canUseVirtualScroll, isRecentView, ngAfterViewChecked, searchEnabled, treeContainerMaxHeight, search, calculateBounds, expandNode, collapseNode, refreshScroll, doubleclickNode, clickNode, isSelectedNode, exclusiveSelectNode, selectNode, setHighLightNodes, enforceSinglePathToRoot, onDrag, onDragOver, onDrop, addShiftNodes, findNodes, addSelectedNodes, addNodeToArray, getParentNode, getNodeByData, getNodeData, getTopAncestor, forEach, selectAndExpandToNode, expandToNode, isHostGlobalParent, nodeEquals, deselectAllNodes, onScroll, focusedObservable, isFocusedNode, onKey, nextNode, prevNode, openHelp, switchRecent, getVirtualBottom
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`tree.component.risk.tl.spec.ts`)
- Methods: ngAfterViewInit, fixSelectedNodes, expandAll, clearSearchContent, onSearchEscape, subscribeVScroll, unSubscribeVScroll
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`tree.component.display.tl.spec.ts`)
- Methods: showHelpLink
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

---

> **以下为 2026-06-12 补充扫描新增组件**

| 状态    | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|-------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| ⏸️暂缓 | ScheduleTabComponent | 20 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅ 已测试 | ActionAccordionComponent | 861 | 0 | 6 | **multi-pass** | ✅ 推进 | ⚠️ action-accordion.spec.ts | test: check clear all parameters; test: should get correct highlight name for alert | P1: action-accordion.component.interaction.tl.spec.ts<br>P2: action-accordion.component.risk.tl.spec.ts |
| ⏸️暂缓 | ScheduleTaskDialogComponent | 57 | 0 | 0 | **single-pass** | 暂缓 | ⚠️ schedule-task-dialog.spec.ts | Bug #21217 task name control (broken test) | single pass |
| 待审核   | AddParameterDialogComponent | 265 | 0 | 2 | **single-pass** | ✅ 推进 | ⚠️ add-parameter-dialog.component.spec.ts | test: should pop confirm dialog when create duplicate parameter; broken test: check can create parameter; test: check can edit parameter | single pass (+内存泄漏) |
| ⏸️暂缓 | EditableTableComponent | 45 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | ParameterTableComponent | 55 | 0 | 0 | **single-pass** | 暂缓 | ⚠️ parameter-table.component.spec.ts | test: should show confirm when to delete parameter; test: check can edit parameter; test: should display timeinstant/array parameter correctly | single pass |
| ⏸️暂缓 | CreateTaskFolderDialogComponent | 73 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | TaskFolderBrowserComponent | 82 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | RepositoryListViewComponent | 27 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | RepositoryMobileViewComponent | 45 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试  | RepositoryTreeViewComponent | 170 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | ChangePasswordDialogComponent | 62 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | MvExceptionsPortalDialogComponent | 29 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | DataNotificationsComponent | 19 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | MoveDataModelDialogComponent | 71 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试  | MoveDatasourceDialogComponent | 117 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | AssetDescriptionComponent | 28 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试  | DataSourcesBrowserComponent | 157 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | DatasourceCategoryPaneComponent | 26 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | DatasourceListingPaneComponent | 25 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | DatasourceListingComponent | 32 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | DatasourceSearchComponent | 23 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试   | LogicalModelAttributeEditorComponent | 304 | 0 | 3 | **single-pass** | ✅已测试 | 33/33✅ |  | single pass (+内存泄漏) |
| ⏸️暂缓 | LogicalModelColumnEditorComponent | 54 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | ElementTreeNodeComponent | 205 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ⏸️暂缓 | LogicalModelEntityEditorComponent | 38 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | LogicalModelEntityPaneComponent | 97 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| 待审核   | LogicalModelExpressionEditorComponent | 182 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | PhysicalTableTreeNodeComponent | 58 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | PhysicalTableTreeComponent | 250 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ⏸️暂缓 | PhysicalModelTableTreeNodeComponent | 81 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | PhysicalModelTableTreeComponent | 44 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | PhysicalStatusBarComponent | 21 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | DataModelFolderBrowserComponent | 78 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | ChoseAdditionalConnectionDialogComponent | 74 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | ChosePhysicalViewDialogComponent | 41 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | SelectAttributePaneComponent | 73 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | SelectQueryFieldPaneComponent | 24 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | EditJoinTableColumnComponent | 81 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | EditJoinTableComponent | 79 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | JoinNodeGraphComponent | 268 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | LoadingIndicatorPaneComponent | 22 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | PhysicalModelEditTableComponent | 36 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | AddJoinDialogComponent | 119 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | EditJoinDialogComponent | 81 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ✅已测试   | PhysicalTableJoinsComponent | 319 | 0 | 4 | **single-pass** | ✅已测试 | 24/24✅ |  | single pass (+内存泄漏) |
| ⏸️暂缓 | DatasourcesDatasourceDialogComponent | 37 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | ViewSampleDataDialogComponent | 24 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | DatabaseQueryComponent | 217 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试   | FieldsPaneComponent | 305 | 0 | 4 | **single-pass** | ✅已测试 | 38/38✅ |  | single pass (+竞态+内存泄漏) |
| ⏸️暂缓 | QueryConditionsPaneComponent | 79 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | BrowseFieldValuesDialogComponent | 26 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | EditDataTypeDialogComponent | 28 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| ⏸️暂缓 | EditFieldDialogComponent | 97 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ✅已测试  | QueryFieldsPaneComponent | 462 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: query-fields-pane.component.interaction.tl.spec.ts<br>P2: query-fields-pane.component.risk.tl.spec.ts |
| ⏸️暂缓 | QueryGroupingPaneComponent | 53 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | QueryJoinEditPaneComponent | 124 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核   | QueryLinkGraphPaneComponent | 113 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核   | QueryLinkPaneComponent | 117 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试  | QueryNetworkGraphPaneComponent | 713 | 0 | 10 | **multi-pass** | ✅ 推进 |  |  | P1: query-network-graph-pane.component.interaction.tl.spec.ts（29 tests, jsPlumb+jsdom mock pattern）<br>P2: 跳过（jsPlumb risk spec crashes MSW） |
| ⏸️暂缓 | QueryTablePropertiesDialogComponent | 57 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | QuerySortPaneComponent | 22 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass |
| 待审核   | QueryPreviewTableComponent | 245 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ⏸️暂缓 | SqlQueryPreviewPaneComponent | 79 | 0 | 1 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |
| ⏸️暂缓 | FreeFormSqlPaneComponent | 73 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass (+内存泄漏) |

---

## 补充扫描新增 multi-pass 组件详情（2026-06-12）

### ActionAccordionComponent

**Pass 1** (`action-accordion.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, action setter, viewsheet setter, sheet getter, editAction, deleteAction, copyAction, checkClearAllParameters, clearAllParameters, addParameter, editParameter, deleteParameter, getHighlightName, getDeliveryFormatText, getSheetPath, getBookmarks, getHighlights, getParameters, getTableDataAssemblies, getPrintLayout, changeViewsheet, getOwner, initForm, updateEmailAutoComplete, getEmails, addToAutoCompleteList, getAutoCompleteLists, setDefaultAction, setCurrentAction, getDefaultAction, updateValues, setSelectedOption, checkSelectedOption, isTabSelected
- Reason: 回归主体：lifecycle, action CRUD, parameter management, form initialisation, sheet/viewsheet wiring

**Pass 2** (`action-accordion.component.risk.tl.spec.ts`)
- Methods: ngAfterViewChecked, isGeneralAction, changeActionType, getGeneralActionModel, actionNames, clearHighlightConditions, selectSheetError, isValid, save, showSelectDashboardDialog, getSheetName
- Reason: async≥3：竞态 / destructive / state inconsistency (6 subscriptions)

### QueryFieldsPaneComponent

**Pass 1** (`query-fields-pane.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, isSelected, selectField, toggleSelectAll, moveUp, moveDown, editField, deleteField, addExpression, editExpression, addField, getFieldIcon, getDataType, getAlias, isExpression, refreshPreview, reset, ok, cancel, updateFields, sortFields, getFieldLabel
- Reason: 回归主体：field selection, ordering, CRUD, HTTP preview refresh

**Pass 2** (`query-fields-pane.component.risk.tl.spec.ts`)
- Methods: loadFields, fetchPreview, validateFields, refreshSearchFields, processFieldDrop, handleError
- Reason: async>5 (7 subscriptions)：竞态 / HTTP loading / error states

### QueryNetworkGraphPaneComponent

**Pass 1** (`query-network-graph-pane.component.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, ngAfterViewChecked, ngOnDestroy, scale, bind, registerNode, selectNode, connectNode, addEndpoint, showEndpoints, hideEndpoints, openJoinEditPane, closeJoinEditPane, setRepaintTimer, setContainer, getSelectedNode, getJoinTooltip, checkJoinCompatibility, graphEndpoints, isHighlight, isSameTableAutoAlias, getJoinActions, showJoinActions, fireSelectedNodesChanged, onSelectionBox, getThumbnailClasses, each, forEach
- Reason: 回归主体：canvas graph lifecycle, node selection, join endpoint wiring

**Pass 2** (`query-network-graph-pane.component.risk.tl.spec.ts`)
- Methods: connectionDeletable, removeJoinCondition, deleteNode, clearJoin, clearTable, clearTableEnabled, clearJoinEnabled, refreshAnchors, setDraggable, moveNodes, contextMenu, createActions, keydown, removeSelectTables, doRemove, selectAll, clearSelection, refreshDragSelection, isColumnExist, convertToHTMLCharacterEntity
- Reason: async>5 (10 subscriptions)：竞态 / destructive operations / state inconsistency

---

> **以下为 2026-06-24 补充扫描新增组件（6 个）**
> 来源：系统性缺口分析，portal 路由下的调度对话框、Email 编辑、参数页面及时间选择器。
> `logic_lines / dispatch / async_zones` 均为 `—`（待 prescan workflow 精确扫描）。

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| ✅已测试 | SimpleScheduleDialog | 540 | 3 | 5 | **multi-pass** | ✅ 推进 | ~~⚠️ simple-schedule-dialog.component.spec.ts~~ | Both test cases are marked it.skip, giving zero effective coverage; no core functionality (form init, history restore, timezone conversion, export visibility logic, okDisabled guard) is tested. | P1: simple-schedule-dialog.component.interaction.tl.spec.ts (25/25 ✅)<br>P2: simple-schedule-dialog.component.risk.tl.spec.ts (22/22 ✅)<br>P3: simple-schedule-dialog.component.display.tl.spec.ts (22/22 ✅) |
| 待审核 | StartTimeEditor | 173 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass |
| ✅已测试 | EmbeddedEmailPane | 469 | 4 | 5 | **multi-pass** | ✅ 推进 | ⚠️ email-addr-dialog.spec.ts | Covers add-user dedup and remove-user via DOM clicks on EmailAddrDialog host and direct EmbeddedEmailPane; does NOT cover select() multi-key logic, addressChange() format branches, reset() colon-delimited non-embeddedOnly parsing, searchUsers() mode toggle, moveFocus() keyboard dispatch, addIdentity() group path deduplication, or currentUser/usersNode async ordering. | P1: embedded-email-pane.component.interaction.tl.spec.ts (36/36 ✅)<br>P2: embedded-email-pane.component.risk.tl.spec.ts (15/15 ✅ + 1 expected fail)<br>P3: embedded-email-pane.component.display.tl.spec.ts (15/15 ✅) |
| 待审核 | EmailPane | 135 | 1 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: email-pane.interaction.tl.spec.ts<br>P2: email-pane.risk.tl.spec.ts |
| 待审核 | ParameterPage | 283 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | TimepickerComponent | 202 | 4 | 0 | **multi-pass** | ✅ 推进 |  |  | P1: timepicker.interaction.tl.spec.ts<br>P3: timepicker.display.tl.spec.ts |

---

## 2026-06-24 补充扫描 Multi-Pass 详情（4 个）

### SimpleScheduleDialog

**Pass 1** (`SimpleScheduleDialog.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, initForm, addEmail, addCCEmail, addBCCEmail, openEmailDialog, ok, cancel, changeStartTimeModel, selectConditionType, selectDayOfWeek, selectDaysOfWeek, changeEveryDay, formatChange, updateOnlyDataComponents
- Reason: User-triggered flows, lifecycle hooks, HTTP loading on ok(), and modal dialog interactions

**Pass 2** (`SimpleScheduleDialog.risk.tl.spec.ts`)
- Methods: ok (HTTP subscribe — OK vs error branch, addressHistory update, onCommit emit), initForm (valueChanges subscriptions for emails/cc/bcc), search (debounce observable)
- Reason: asyncZones = 5: email validation HTTP race, subscribe-driven form sync, and debounced typeahead observable

**Pass 3** (`SimpleScheduleDialog.display.tl.spec.ts`)
- Methods: okDisabled, dataSizeOptionVisible, showMeridian, isEmptyTable, setTimeZone, convertTimeZone, getTimezoneOffset, getHistoryModel, fixMonthCondition, getEmailUsers
- Reason: dispatchPoints = 3: pure conditional/label computations including timezone conversion math, export format visibility flags, and form disable guard

### EmbeddedEmailPane

**Pass 1** (`EmbeddedEmailPane.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, constructor (HTTP usersNode fetch + currentUser subscribe), initForm (form control + Subject subscribes), addIdentities, addIdentity, removeIdentities, nodeSelected, updateOtherEmail, updateSearchText, updateSearchIdentityText, searchUsers, reset, addressChange, changeEmail
- Reason: HTTP loading, lifecycle hooks, user-triggered add/remove/search flows, and address serialization are the primary integration surface

**Pass 2** (`EmbeddedEmailPane.risk.tl.spec.ts`)
- Methods: constructor subscribe ordering (usersNode arrives after reset), ngOnDestroy Subject unsubscribe, searchTextchanges$ debounce/distinctUntilChanged behaviour, searchIdentitychanges$ debounce, addresses setter initialAddresses null-guard, getUserAlias when usersNode empty vs populated
- Reason: asyncZones = 5; concurrent subscribe ordering and Subject cleanup on destroy are race/state-inconsistency risks

**Pass 3** (`EmbeddedEmailPane.display.tl.spec.ts`)
- Methods: select (ctrl/meta, shiftKey-empty, shiftKey-range, plain click), moveFocus (ArrowDown/ArrowUp/Backspace key dispatch), removeIdentities (USER vs GROUP vs other branch), addressChange (embeddedOnly vs non-embeddedOnly format), addDisable, getIdentityIcon, isSelectedIdentity, searchAllIdentities, sortIdentities, getGroupPath, trackByIndex, findNextNode, findIdentityIndex
- Reason: dispatchPoints = 4; label/icon computation and multi-key selection branches are pure conditional display logic

### EmailPane

**Pass 1** (`email-pane.interaction.tl.spec.ts`)
- Methods: ngOnInit, initForm (form control creation, validators, fromAddr enable/disable branch, isIE branch), ngOnDestroy (subscription teardown), getAddress (switch: to/cc/bcc/unknown), selectEmails (address lookup, modal open, result→form setValue, reject/cancel), model getter/setter, message getter/setter
- Reason: Covers component lifecycle, form initialization, fromAddressEnabled enable/disable conditional, and the modal open→result user flow — all directly user-triggered or lifecycle-driven interactions.

**Pass 2** (`email-pane.risk.tl.spec.ts`)
- Methods: initForm valueChanges subscriptions (toAddress/ccAddress/bccAddress/fromAddr/message → model sync), modalService.open().result.then resolve and reject paths, addressSearch debounce observable (term present with historyEmails, term absent, historyEmails absent)
- Reason: asyncZones = 6 triggers a risk pass. Tests the 5 subscribe-based form→model sync handlers for correctness and the Promise-based modal result chain (resolve updates form control; reject is silently swallowed), plus the debounce-filtered typeahead observable edge cases.

### TimepickerComponent

**Pass 1** (`timepicker.interaction.tl.spec.ts`)
- Methods: ngOnInit, model setter/getter, changeHour, handleHour, changeMinute, handleMinute, changeSecond, handleSecond, updateHour, updateMinute, updateSecond, toggleMeridian, propogateTimeChange, initForm
- Reason: User-triggered flows: keyboard arrow-key handlers, spinner step changes, direct input updates, meridian toggle, timeChange EventEmitter emission, form control synchronization, and ngOnInit wiring

**Pass 3** (`timepicker.display.tl.spec.ts`)
- Methods: setControlSize, formatTime, getTimeStruct, padNumber, toInteger, isNumber, NgbTime (all methods)
- Reason: Pure conditional/display logic: 3-way size switch (small/large/default), meridian vs 24h hour formatting, NaN/invalid handling in padNumber and NgbTime arithmetic helpers, getTimeStruct null guard
