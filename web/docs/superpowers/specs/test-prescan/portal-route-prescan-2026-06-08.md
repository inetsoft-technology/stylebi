# portal Route Pre-scan Report

**日期**: 2026-06-08（2026-06-12 补充扫描，新增 65 个组件）
**候选组件数**: 142 | **建议推进**: 142 | **建议跳过**: 0 | **多 pass 组件**: 37

## 状态说明
- 第一列「状态」初始为「待审核」，人工审核后改为 ✅已测试 / ⏭已跳过
- ⚠️ 有旧spec — 新测试通过后在同 PR 内删除旧 .spec.ts
- 「旧 spec 备注」列记录旧测试中不易从源码推断的 case，生成新测试时参考

## 分类说明
- **single-pass**：logic_lines ≤ 500 AND dispatch ≤ 2 AND async ≤ 5 → `ComponentName.tl.spec.ts`
- **multi-pass**：logic_lines > 500 OR dispatch ≥ 3 OR async > 5 → 多文件 pass 计划
- single-pass 按需追加：async≥3 竞态 / async≥1 内存泄漏 / dispatch≥3 边界

## 候选组件清单

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| 待审核 | PortalAppComponent | 339 | 0 | 16 | **multi-pass** | ✅ 推进 |  |  | P1: PortalAppComponent.interaction.tl.spec.ts<br>P2: PortalAppComponent.risk.tl.spec.ts |
| 待审核 | CustomTabComponent | 34 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | DashboardTabComponent | 215 | 3 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: DashboardTabComponent.interaction.tl.spec.ts<br>P2: DashboardTabComponent.risk.tl.spec.ts<br>P3: DashboardTabComponent.display.tl.spec.ts |
| 待审核 | DashboardLandingComponent | 50 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | AssetItemListViewComponent | 159 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DataDatasourceBrowserComponent | 756 | 0 | 21 | **multi-pass** | ✅ 推进 | ⚠️ data-datasource-browser.component.tl.spec.ts | 🔁 Regression-sensitive: refresh combines route params, sorting, selection mapping and status fetch.; 🔁 Regression-sensitive: refreshed objects repla | P1: DataDatasourceBrowserComponent.interaction.tl.spec.ts<br>P2: DataDatasourceBrowserComponent.risk.tl.spec.ts |
| 待审核 | DatasourceSelectionViewComponent | 127 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: DatasourceSelectionViewComponent.interaction.tl.spec.ts<br>P2: DatasourceSelectionViewComponent.risk.tl.spec.ts |
| 待审核 | DatabaseDataModelBrowserComponent | 723 | 0 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: DatabaseDataModelBrowserComponent.interaction.tl.spec.ts<br>P2: DatabaseDataModelBrowserComponent.risk.tl.spec.ts |
| 待审核 | DatabaseDataModelToolbarComponent | 126 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | DatabaseVPMBrowserComponent | 338 | 0 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: DatabaseVPMBrowserComponent.interaction.tl.spec.ts<br>P2: DatabaseVPMBrowserComponent.risk.tl.spec.ts |
| 待审核 | DataModelScriptPane | 247 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | DatabasePhysicalModelComponent | 1153 | 4 | 28 | **multi-pass** | ✅ 推进 |  |  | P1: DatabasePhysicalModelComponent.interaction.tl.spec.ts<br>P2: DatabasePhysicalModelComponent.risk.tl.spec.ts<br>P3: DatabasePhysicalModelComponent.display.tl.spec.ts |
| 待审核 | LogicalModelAttributeDialog | 107 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | AutoDrillDialog | 531 | 0 | 15 | **multi-pass** | ✅ 推进 |  |  | P1: AutoDrillDialog.interaction.tl.spec.ts<br>P2: AutoDrillDialog.risk.tl.spec.ts |
| 待审核 | ParameterDialog | 242 | 2 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | SelectWorksheetDialog | 131 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | AttributeFormattingPane | 232 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | LogicalModelEntityDialog | 59 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | LogicalModelExpressionDialog | 230 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: LogicalModelExpressionDialog.interaction.tl.spec.ts |
| 待审核 | LogicalModelPropertyPane | 591 | 0 | 2 | **multi-pass** | ✅ 推进 |  |  | P1: LogicalModelPropertyPane.interaction.tl.spec.ts |
| 待审核 | LogicalModelComponent | 275 | 2 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: LogicalModelComponent.interaction.tl.spec.ts<br>P2: LogicalModelComponent.risk.tl.spec.ts |
| 待审核 | PhysicalGraphPane | 237 | 0 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: PhysicalGraphPane.interaction.tl.spec.ts<br>P2: PhysicalGraphPane.risk.tl.spec.ts |
| 待审核 | PhysicalJoinEditPane | 120 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: PhysicalJoinEditPane.interaction.tl.spec.ts<br>P2: PhysicalJoinEditPane.risk.tl.spec.ts |
| 待审核 | PhysicalModelNetworkGraphComponent | 779 | 0 | 19 | **multi-pass** | ✅ 推进 |  |  | P1: PhysicalModelNetworkGraphComponent.interaction.tl.spec.ts<br>P2: PhysicalModelNetworkGraphComponent.risk.tl.spec.ts |
| 待审核 | DatabaseVPMComponent | 270 | 1 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: DatabaseVPMComponent.interaction.tl.spec.ts<br>P2: DatabaseVPMComponent.risk.tl.spec.ts |
| 待审核 | VPMConditionsComponent | 259 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | VPMHiddenColumnsComponent | 331 | 0 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: VPMHiddenColumnsComponent.interaction.tl.spec.ts<br>P2: VPMHiddenColumnsComponent.risk.tl.spec.ts |
| 待审核 | VPMTestComponent | 63 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | DatasourcesDatabaseComponent | 662 | 0 | 12 | **multi-pass** | ✅ 推进 |  |  | P1: DatasourcesDatabaseComponent.interaction.tl.spec.ts<br>P2: DatasourcesDatabaseComponent.risk.tl.spec.ts |
| 待审核 | DriverWizardComponent | 275 | 0 | 24 | **multi-pass** | ✅ 推进 |  |  | P1: DriverWizardComponent.interaction.tl.spec.ts<br>P2: DriverWizardComponent.risk.tl.spec.ts |
| 待审核 | EditPropertyDialogComponent | 50 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | DatasourcesDatasourceEditorComponent | 258 | 0 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: DatasourcesDatasourceEditorComponent.interaction.tl.spec.ts<br>P2: DatasourcesDatasourceEditorComponent.risk.tl.spec.ts |
| 待审核 | DatasourcesDatasourceComponent | 346 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: DatasourcesDatasourceComponent.interaction.tl.spec.ts<br>P2: DatasourcesDatasourceComponent.risk.tl.spec.ts |
| 待审核 | DatasourcesXmlaComponent | 602 | 0 | 35 | **multi-pass** | ✅ 推进 |  |  | P1: DatasourcesXmlaComponent.interaction.tl.spec.ts<br>P2: DatasourcesXmlaComponent.risk.tl.spec.ts |
| 待审核 | DataFolderBrowserComponent | 938 | 1 | 17 | **multi-pass** | ✅ 推进 | ⚠️ data-folder-browser.component.tl.spec.ts | Regression-sensitive: route scope, folder-first sorting, and selected-object remapping can; Regression-sensitive: failed search must not leave stale r | P1: DataFolderBrowserComponent.interaction.tl.spec.ts<br>P2: DataFolderBrowserComponent.risk.tl.spec.ts |
| 待审核 | DataFolderListViewComponent | 151 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | FilesBrowserComponent | 119 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | DataSourcesTreeViewComponent | 1463 | 7 | 19 | **multi-pass** | ✅ 推进 | ⚠️ data-sources-tree-view.component.tl.spec.ts | Group 1 — changeDataSourcesTree: always returns false [Risk 3] (confirmed bug); 🔁 Regression-sensitive: return false instead of found breaks the recu | P1: DataSourcesTreeViewComponent.interaction.tl.spec.ts<br>P2: DataSourcesTreeViewComponent.risk.tl.spec.ts<br>P3: DataSourcesTreeViewComponent.display.tl.spec.ts |
| 待审核 | DataTabComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | InputNameDescDialog | 113 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | MoveAssetDialogComponent | 121 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | AnalyzeMVDialog | 280 | 0 | 30 | **multi-pass** | ✅ 推进 |  |  | P1: AnalyzeMVDialog.interaction.tl.spec.ts<br>P2: AnalyzeMVDialog.risk.tl.spec.ts |
| 待审核 | AnalyzeMVPane | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | CreateMVPane | 112 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ArrangeDashboardDialog | 94 | 0 | 2 | **single-pass** | ✅ 推进 | ⚠️ arrange-dashboard-dialog.spec.ts | Bug #18799 not enabled dashboard should display in list | single pass (+内存泄漏) |
| 待审核 | AutoJoinTablesDialog | 144 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ChooseTableDialog | 204 | 1 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: ChooseTableDialog.interaction.tl.spec.ts<br>P2: ChooseTableDialog.risk.tl.spec.ts |
| 待审核 | EditDashboardDialog | 179 | 0 | 7 | **multi-pass** | ✅ 推进 | ⚠️ edit-dashboard-dialog.spec.ts | Bug #18620 Don't allow special characters in the name; Bug #21678 should allow & -+ | P1: EditDashboardDialog.interaction.tl.spec.ts<br>P2: EditDashboardDialog.risk.tl.spec.ts |
| 待审核 | InlineViewDialog | 66 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | PhysicalTableAliasesDialog | 101 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | PreferencesDialog | 81 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | VPMConditionDialog | 62 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PortalRedirectComponent | 26 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | RepositoryDesktopViewComponent | 65 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ReportTabComponent | 280 | 2 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: ReportTabComponent.interaction.tl.spec.ts<br>P2: ReportTabComponent.risk.tl.spec.ts |
| 待审核 | PortalReportComponent | 93 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | WelcomePageComponent | 39 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | TaskActionPane | 464 | 0 | 8 | **multi-pass** | ✅ 推进 | ⚠️ task-action-pane.component.spec.ts | Bug #19890 should pop up warning when to delete action; test: should not copy when no action is selected; test: should not copy when multiple actions  | P1: TaskActionPane.interaction.tl.spec.ts<br>P2: TaskActionPane.risk.tl.spec.ts |
| 待审核 | TaskConditionPane | 1073 | 3 | 3 | **multi-pass** | ✅ 推进 | ⚠️ task-condition-pane.spec.ts | Bug #19519 should show current date when not set; Bug #19687 should show set date; Bug #19517 select and deselect all function for weekly | P1: TaskConditionPane.interaction.tl.spec.ts<br>P2: TaskConditionPane.risk.tl.spec.ts<br>P3: TaskConditionPane.display.tl.spec.ts |
| 待审核 | ExecuteAsDialog | 190 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | TaskOptionsPane | 189 | 0 | 13 | **multi-pass** | ✅ 推进 | ⚠️ task-options-pane.component.spec.ts | Bug #19508; Bug #19745; Bug #21420 should get correct locale info when set 'Default' | P1: TaskOptionsPane.interaction.tl.spec.ts<br>P2: TaskOptionsPane.risk.tl.spec.ts |
| 待审核 | ScheduleTaskEditorComponent | 192 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: ScheduleTaskEditorComponent.interaction.tl.spec.ts<br>P2: ScheduleTaskEditorComponent.risk.tl.spec.ts |
| 待审核 | SelectDashboardDialog | 69 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | EditTaskFolderDialog | 69 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | MoveTaskDialogComponent | 97 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ScheduleTaskListComponent | 844 | 1 | 58 | **multi-pass** | ✅ 推进 |  |  | P1: ScheduleTaskListComponent.interaction.tl.spec.ts<br>P2: ScheduleTaskListComponent.risk.tl.spec.ts |
| 待审核 | InputNameDialog | 112 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ input-name-dialog.component.spec.ts | Bug #19762 Show the error message on the input; test: Show the error message on the input | single pass (+内存泄漏) |
| 待审核 | ActionsContextmenuComponent | 154 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ actions-contextmenu.component.spec.ts | test: should not create a dropdown when there are no visible actions | single pass (+内存泄漏) |
| 待审核 | NotificationsComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ notifications.component.spec.ts | 无 | single pass (+内存泄漏) |
| 待审核 | RepositoryTreeComponent | 461 | 0 | 5 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ResponsiveTabsComponent | 80 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | SplitPane | 120 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | TabularViewComponent | 59 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | TreeNodeComponent | 506 | 3 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: TreeNodeComponent.interaction.tl.spec.ts<br>P2: TreeNodeComponent.risk.tl.spec.ts<br>P3: TreeNodeComponent.display.tl.spec.ts |
| 待审核 | TreeComponent | 754 | 6 | 6 | **multi-pass** | ✅ 推进 | ⚠️ tree.spec.ts | Bug #17221 search field can not input string; (reading 'expanded')" after the fixture is destroyed.; Bug #17336 should show infomation when no result  | P1: TreeComponent.interaction.tl.spec.ts<br>P2: TreeComponent.risk.tl.spec.ts<br>P3: TreeComponent.display.tl.spec.ts |
| 待审核 | AiAssistantDialogComponent | 35 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | AiAssistantPanelComponent | 177 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |

## 多 pass 组件详情

### PortalAppComponent

**Pass 1** (`PortalAppComponent.interaction.tl.spec.ts`)
- Methods: openComposerEnabled, subscribe, showGettingStarted, refreshCreationPermissions, logOut, profiling, showDocument, getTab, getDashboardTabTooltip, getDataTabTooltip, getScheduleTabTooltip, isTabSelected, openComposer, launchComposer, showListings
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`PortalAppComponent.risk.tl.spec.ts`)
- Methods: updateAccessibility, setTabOrder, checkDefaultTab, showPreferences, handleMessageEvent
- Reason: async≥3：竞态 / destructive / state inconsistency

### DashboardTabComponent

**Pass 1** (`DashboardTabComponent.interaction.tl.spec.ts`)
- Methods: updateSelectedDashboard, selectTab, openArrangeDashboardDialog
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DashboardTabComponent.risk.tl.spec.ts`)
- Methods: selectDashboard, openDashboard, reloadDashboard, updateModel, newDashboard, editDashboard, deleteDashboard, editDeleteDashboardDisabled
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`DashboardTabComponent.display.tl.spec.ts`)
- Methods: getDashboardLabel
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### DataDatasourceBrowserComponent

**Pass 1** (`DataDatasourceBrowserComponent.interaction.tl.spec.ts`)
- Methods: clickItem, processSetComposedDashboardCommand, disableAction, updateDataSources, set, refreshDataSource, refreshSearchBrowser, getParentPath, getParentRouterLinkParams, updateSortOptions, manageDrivers, showListings, getDatasourceStatusIcon, isDataSourceFolder, isDataSource, toggleSearch, addFolder, renameFolder, getDatasourceIcon, editDataSource, handleResponseDatasource, createQuery, createPhysicalView, createVPM, processCreateQueryEventCommand, processMessageCommand, openComposer, getDataSourceObjectType, getAssemblyName, dragAsset, getEntryLabel, dropAssets, dataTreeDragToPane, then, selectAllChecked, selectAllChanged, getDateLabel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DataDatasourceBrowserComponent.risk.tl.spec.ts`)
- Methods: slice, addFolderConfig, toggleSelectTooltip, viewAssets, moveDisable, add, ngAfterViewInit, subscribe, refreshAllData, loadDataSourceStatus, fetchDataSourceStatuses, sortDataSources, search, clearSearch, moveDataSource, moveSelected, deleteItem, handleResponse, moveDataSources0, isSelectedItem, updateAssetSelection, toggleSelectionState, isSelectionDeletable, isSelectionEditable, updateSelection, deleteSelected, deleteSelected0, updateSelectedItems
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourceSelectionViewComponent

**Pass 1** (`DatasourceSelectionViewComponent.interaction.tl.spec.ts`)
- Methods: getListings, isCreateDisabled, cancel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatasourceSelectionViewComponent.risk.tl.spec.ts`)
- Methods: canDeactivate, create, subscribe
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabaseDataModelBrowserComponent

**Pass 1** (`DatabaseDataModelBrowserComponent.interaction.tl.spec.ts`)
- Methods: listModel, getListColumns, getRouterPath, toggleSelectionState, search, routeToFolder, getParentPath, getTypeLabel, getBasedView, refreshSearchBrowser, updateSortOptions, sortModels, editModel, changeSelectedItems, refreshModels, updateModels, getIcon, dragSupportFun, getChildren, clickItem, isRoot, addDataModelFolder, addPhysicalView, addLogicalModel0, addLogicalModel, addExtendModel, renameModel, isPhysicalView, isLogicalModel, isFolder, getActionMessage, getRenameMessage, actionCallback, refreshListAndTree, isBaseModelSelected, isExtend, setShowDetailsItem, openContextmenu, createActions, disableAction, getEntryIcon
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatabaseDataModelBrowserComponent.risk.tl.spec.ts`)
- Methods: clearSearch, subscribe, deleteModel, getDeleteMessage, moveModel, moveSelected, deleteSelected, moveDisable, dragAssetsItems, dropAssetsItems, moveModels0, dataTreeDragToPane, getAssetLabel
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabaseVPMBrowserComponent

**Pass 1** (`DatabaseVPMBrowserComponent.interaction.tl.spec.ts`)
- Methods: sortOptionsChanged, toggleSelectionState, editModel, addModel, renameModel, setShowDetailsItem, openTreeContextmenu, createActions
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatabaseVPMBrowserComponent.risk.tl.spec.ts`)
- Methods: editable, deletable, crrentSearchFolderLabel, search, reSearch, clearSearch, refreshModels, deleteModel, deleteSelected
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabasePhysicalModelComponent

**Pass 1** (`DatabasePhysicalModelComponent.interaction.tl.spec.ts`)
- Methods: physicalModel, databaseName, parent, modelInitializing, displayTitle, subscribe, refreshTreeSelectStatus, refreshLeafNodeSelectStatus, some, getTablePath, showAutoAliasDialog, showAutoAliasDialog0, createAutoAliasByGraph, showCreateAliasDialog, createAliasTable, forEach, findEditingInlineNode, toggleInlineViewEditor, editView, refreshDatabaseRoots, addAliasInlineNodes, isAliasTable, then, notify, save, onPhysicalGraph, selectPhysicalGraphNode, changeEditingTable, changeEditingTableByName, databaseParr, openFolder, isDuplicateTableName, createPhysicalTableModel, canDeactivate, search, expandAll, keepSelectedNodes, refreshModel, toggleTreeCollapsed, splitPaneDragEnd, onJoinEditing, onKeyDown, refreshEditingTable, createTableActions, isTable, isBaseTable
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatabasePhysicalModelComponent.risk.tl.spec.ts`)
- Methods: updateForeignJoins, createAlias, editAlias, treePaneCollapsed, refreshView, createTableNodeLabel, openPhysicalModel, createPhysicalModel, refreshWarnings, refreshWarnings0, refreshSupportFullOuterJoin, toggleRepositoryTreePane, expandNode, checkboxToggledNode, selectNode, pipe, loadDatabaseTree, removePhysicalTable, resetSearchMode, keepExpandedNodes, getExpandedPaths, ngDoCheck, onModified, graphNodesSelected, tableRemoved, selectAndExpandToPath, updateDataTreePane
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`DatabasePhysicalModelComponent.display.tl.spec.ts`)
- Methods: showAutoJoinTablesDialog, showCreateInlineViewDialog, showTreeContextMenu
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### AutoDrillDialog

**Pass 1** (`AutoDrillDialog.interaction.tl.spec.ts`)
- Methods: autoDrillModel, selectedDrills, editIndex, loadRepositoryTreeAndselectDrill, ngAfterViewInit, drillLabel, getDrillPathLabel, selectDrill, addDrill, getNewDrillName, openSelectWorksheetDialog, getWorksheetFields, selectNode, updateVariable, addParameter, editParameter, openParameterDialog, initFormControl, subscribe, getDisplayParam, changeLinkType, changeLinkTarget, selectAndExpandToPath, ok
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`AutoDrillDialog.risk.tl.spec.ts`)
- Methods: selectedAssetNode, editDrill, getDrillWorksheet, moveDrillDown, moveDrillUp, deleteDrill, getAssetNodeIdentifier, removeParameter, removeAllParameters, getFirstErrorMessage, nameControl, linkControl, clearForm, clearSelectedNode, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### LogicalModelExpressionDialog

**Pass 1** (`LogicalModelExpressionDialog.interaction.tl.spec.ts`)
- Methods: nameControl, parentControl, initFormControl, loadFields, ok, updateExpression, cancel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

### LogicalModelPropertyPane

**Pass 1** (`LogicalModelPropertyPane.interaction.tl.spec.ts`)
- Methods: logicalModel, editingEle, attributeOrderChanged, updateExistNames, getSelectedItem, isElementSelected, editingElement, moveEntityDown, moveEntityUp, deleteEntityByIndex, keyDown, deleteSelectedItem, checkOuterDependencies, deleteSelectedItem0, resetSelectedStatus, isEntity, hasSelected, validSelected, deleteEntity, getEntity, getAttribute, deleteAttribute, checkInvalidAttributes, sortElements, sortEntities, sortAttributes, showEntityDialog, trim, showAddAttributeDialog, getSelectedEntity, showAddExpressionDialog, addAttributes, checkModified, isDuplicateEntity, isDuplicateAttribute, shiftSelect, entityToggle, onDeleteAttribute, canDelete, itemDeletable, resetState
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

### LogicalModelComponent

**Pass 1** (`LogicalModelComponent.interaction.tl.spec.ts`)
- Methods: ngDoCheck, parent, displayTitle, notify, save, checkModified, canDeactivate, createExtendedModel, settings, lmContentHeight
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`LogicalModelComponent.risk.tl.spec.ts`)
- Methods: refreshModel, getSettings
- Reason: async≥3：竞态 / destructive / state inconsistency

### PhysicalGraphPane

**Pass 1** (`PhysicalGraphPane.interaction.tl.spec.ts`)
- Methods: viewport, restoreGraphViewModel, restoreJoinEditPaneModel, toggleFullScreen, zoomLayout, zoom, updateScale, isAutoLayoutSelected, isZoomItemSelected, zoomOutEnabled, zoomInEnabled, keydown, autoLayout, editJoinRuntimeId, closeJoinEditPane, fullScreenTooltip
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`PhysicalGraphPane.risk.tl.spec.ts`)
- Methods: modelInitializing, ngAfterViewChecked, updateGraphPaneSize, refreshPhysicalGraphModel
- Reason: async≥3：竞态 / destructive / state inconsistency

### PhysicalJoinEditPane

**Pass 1** (`PhysicalJoinEditPane.interaction.tl.spec.ts`)
- Methods: jsp, findColumnIndex
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`PhysicalJoinEditPane.risk.tl.spec.ts`)
- Methods: ngAfterViewInit, ngAfterViewChecked, sendHeartBeat, reorderColumns, movePosition
- Reason: async≥3：竞态 / destructive / state inconsistency

### PhysicalModelNetworkGraphComponent

**Pass 1** (`PhysicalModelNetworkGraphComponent.interaction.tl.spec.ts`)
- Methods: scale, bind, ngAfterViewChecked, ngAfterViewInit, scrollPosition, isAutoAliasNode, showJoinActions, getJoinActions, openJoinEditPane, setRepaintTimer, setContainer, checkJoinCompatibility, graphEndpoints, refreshDragSelection, addEndpoint, showEndpoints, each, hideEndpoints, registerNode, selectNode, isSameTableAutoAlias, fireSelectedNodesChanged, forEach, onSelectionBox, getThumbnailClasses, connectNode, isColumnExist, isHighlight, some, getJoinTooltip, convertToHTMLCharacterEntity, getSelectedNode
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`PhysicalModelNetworkGraphComponent.risk.tl.spec.ts`)
- Methods: connectionDeletable, hasBaseJoin, removeJoinCondition, deleteNode, refreshAnchors, setDraggable, moveNodes, contextMenu, createActions, clearTableEnabled, clearJoinEnabled, clearTable, clearJoin, keydown, removeSelectTables, doRemove, selectAll, clearSelection
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatabaseVPMComponent

**Pass 1** (`DatabaseVPMComponent.interaction.tl.spec.ts`)
- Methods: vpm, changeHiddenExpression, resetVPM, selectVPMTab, refreshedColumns, canDeactivate, updateLookupList
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatabaseVPMComponent.risk.tl.spec.ts`)
- Methods: refreshTestData, refreshOperations, subscribe, refreshVPM, isModified, saveVPM
- Reason: async≥3：竞态 / destructive / state inconsistency

### VPMHiddenColumnsComponent

**Pass 1** (`VPMHiddenColumnsComponent.interaction.tl.spec.ts`)
- Methods: selectedColumns, refreshFilterTreeModel, initDataSourceTree, openFolder, subscribe, expandAll, loadFullDatabaseTree, showLoadTimeOutMess, addHiddenColumn, supportAddAction, addNodeToHiddenColumn, addTableNodeToHiddenColumn, addColumnNodeToHiddenColumn, addAllColumnsToHidden0, selectGrantRole, selectAvailableRole, addGrantRoles, getTreeNodeIcon, getBaseName
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VPMHiddenColumnsComponent.risk.tl.spec.ts`)
- Methods: selectHiddenColumn, isSelectedHiddenColumn, hasSelectedHiddenColumn, clearHiddenSelected, addAllToHiddenColumns, addAllColumnsToHidden, removeHiddenColumn, clearHiddenColumns, clearSelectedGrantRoles, removeGrantRoles
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesDatabaseComponent

**Pass 1** (`DatasourcesDatabaseComponent.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, setModel, isCreateDB, updateAdditionalList, canDeactivate, beforeunloadHandler, updateFiles, createDriver, driverAvailable, databaseNameRequired, afterDatabaseSave, getDatabasePath, showMessage, editAdditional, checkDuplicate, renameAdditional, isDatabaseEqual, needSave, close, lastIndexOf, isCustom, setCustomUrl, testQueryEnabled, isFilePathEnabled, getDBUrl, typeChanged, refreshDefaultTestQuery, testQuery, setTestQuery, initForm, newProperty, selectProperty, isSelectedProperty, editProperty, showEditPropertyDialog, keys, changeCustomEditMode, refreshMetadata, subscribe
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatasourcesDatabaseComponent.risk.tl.spec.ts`)
- Methods: selectAdditional, additionalSelected, newAdditional, deleteAdditional, deleteAdditional0, additionalDataSources, deleteAdditionals, getSelectedAdditional, deleteProperty, getExistingNames
- Reason: async≥3：竞态 / destructive / state inconsistency

### DriverWizardComponent

**Pass 1** (`DriverWizardComponent.interaction.tl.spec.ts`)
- Methods: subscribe, searchMaven, selectDriverFile, catchError, error, scanDrivers, pluginExists, return, uploadFilesRequired, mavenCoordRequired, trackByIdx, selectDriver
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DriverWizardComponent.risk.tl.spec.ts`)
- Methods: addDriverFiles, removeDriverFiles, isNextDisabled, next, cancel, uploadDrivers, createDriver
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesDatasourceEditorComponent

**Pass 1** (`DatasourcesDatasourceEditorComponent.interaction.tl.spec.ts`)
- Methods: onViewChanged, onValidChanged, hasRefreshButton, refreshMetadata
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatasourcesDatasourceEditorComponent.risk.tl.spec.ts`)
- Methods: datasource, usedNames, nameValid, updateDatasourceName, authorize, pipe, refreshView, subscribe, initView, hasCancelButton, buttonClicked, clearButtonClicks, clearButtonLoading
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesDatasourceComponent

**Pass 1** (`DatasourcesDatasourceComponent.interaction.tl.spec.ts`)
- Methods: beforeunloadHandler, datasourceChanged, canDeactivate, originalName, updateAdditionalList, selectAdditional, getSelectedAdditional, additionalSelected, newAdditional, additionalDataSources, editAdditional, renameAdditional, checkDuplicate, ok, subscribe, saveDataSource, close, lastIndexOf, updateDatasourceValid
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatasourcesDatasourceComponent.risk.tl.spec.ts`)
- Methods: refreshDataSource, deleteAdditional, deleteAdditionals
- Reason: async≥3：竞态 / destructive / state inconsistency

### DatasourcesXmlaComponent

**Pass 1** (`DatasourcesXmlaComponent.interaction.tl.spec.ts`)
- Methods: refreshMetaLabel, selectedDimensionMember, model, updateEnable, setFormToModel, refreshSourceModel, selectCatalog, loadCatalogs, loadMetadata, expandSelectedTreeNode, expandSelectedTreeNode0, syncDomain, getMemberKey, getDimensionKey, getCSSIcon, selectedNode, changeAsDate, changeLocal, changeDatePattern, changeOriginalOrder, openAutoDrillDialog, updateFormatString, viewSampleData, testDatabase, canDeactivate, ok, close, lastIndexOf, clickLocaleListBtn, forEach
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DatasourcesXmlaComponent.risk.tl.spec.ts`)
- Methods: drillString, selectedDimension, initForm
- Reason: async≥3：竞态 / destructive / state inconsistency

### DataFolderBrowserComponent

**Pass 1** (`DataFolderBrowserComponent.interaction.tl.spec.ts`)
- Methods: getAssemblyName, refreshFolderBrowser, set, handleBrowserRefreshError, refreshSearchBrowser, toggleSelectTooltip, sortView, openFolder, editWorksheet, renameAsset, handleResponse, handleEditAssetError, viewAssets, toggleSelectionState, isFolderEditable, toggleSearch, search, findAsset, selectFile, convertToAssetItem, selectAllChecked, selectAllChanged, updateSelectedItems, addFolder, getRootAssets, materializeAsset, selectChanged, dragAssets, getEntryLabel, getEntryIcon, assetsDroped, dataTreeDragToPane, createInfoByAssetEntry, showWSDetailsByDataSourcesTree
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DataFolderBrowserComponent.risk.tl.spec.ts`)
- Methods: slice, processMessageCommand, addFolderConfig, moveSelectedConfig, newWorksheetDisabled, newWorksheet, isSelectionDeletable, isSelectionEditable, moveDisable, deleteSelected, moveAsset, deleteAsset, handleDeleteError, refreshBrowserContent, clearSearch, moveSelected, moveAssets, moveAssets0
- Reason: async≥3：竞态 / destructive / state inconsistency

### DataSourcesTreeViewComponent

**Pass 1** (`DataSourcesTreeViewComponent.interaction.tl.spec.ts`)
- Methods: add, processSetComposedDashboardCommand, processMessageCommand, initSeletedNodes, changeFolder, changeDataSourcesTree, getPrivateWorksheetNodeParent, getNodePath, replaceStr, getDataNavigationTree, isEditDataSource, updateSelectedNodes, keepExpandedNodes, selectNode, expandNode, openDatasourcesFolder, forEach, onNodeDrag, onNodeDrop, subscribe, getParentPath, getParentPath0, substring, checkDataFoldersDuplicate, setCurrentFolderPath, refreshPaths, openFolder, updateDatasourceNodes, selectAndExpandToDataModel, contextMenu, createActions, isVpmVisible, actionCallback, onScroll, hasMenuFunction, getDuplicateCheckUri, canCreateChildren, canCreateLogicalModel, canRename, canNewWorksheet, canNewDataSource, canCreateQuery, createQuery, openComposer, getDataSourceObjectType, addDataModel, splitModelName, encodeString, byteEncode, newFolderVisible, newFolder, newDataAsset, renameFolder, detailVisible, renameVisible, isDataSourceFolder, isDataWorksheetFolder
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`DataSourcesTreeViewComponent.risk.tl.spec.ts`)
- Methods: moveDataModelAssetItems, moveDataFolderItems, showMessage, moveDataSetsAndFolders, moveDatasourceAssets, moveDatasourceInfos, moveDataAssets, selectAndExpandToPath, canDelete, deleteDataModelFolder, deleteFolder, deleteVisible
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`DataSourcesTreeViewComponent.display.tl.spec.ts`)
- Methods: getEntryLabel, getIconFunction, getAssetIcon
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### AnalyzeMVDialog

**Pass 1** (`AnalyzeMVDialog.interaction.tl.spec.ts`)
- Methods: showCreateMVPage, refresh, setCycle
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`AnalyzeMVDialog.risk.tl.spec.ts`)
- Methods: refreshModels, analyzeMV, deleteMV, checkCompleted, processAnalyzeResult, map, create, showPlan, createOrUpdate, selectedMVsChanged, showPlanClicked, okClicked, closeDialog, informChangedAndClose, onRepositoryChanged
- Reason: async≥3：竞态 / destructive / state inconsistency

### ChooseTableDialog

**Pass 1** (`ChooseTableDialog.interaction.tl.spec.ts`)
- Methods: title, searchStart, initRoot, subscribe, initSelectedTable, selectAndExpandToPath, tableNameNull, selectNode, expandNode, openFolder, getTreeNodeIcon, ok
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ChooseTableDialog.risk.tl.spec.ts`)
- Methods: databaseParr, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### EditDashboardDialog

**Pass 1** (`EditDashboardDialog.interaction.tl.spec.ts`)
- Methods: nodeSelected
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`EditDashboardDialog.risk.tl.spec.ts`)
- Methods: closeDialog, okClicked, editDashboard
- Reason: async≥3：竞态 / destructive / state inconsistency

### ReportTabComponent

**Pass 1** (`ReportTabComponent.interaction.tl.spec.ts`)
- Methods: childRouteShown, viewType, add, init, getAssemblyName, processMessageCommand, historyBarEnabled, isEntryOpened, showEntry, reloadUrl, addRecentlyViewed, editViewsheet, subscribe, collapseTree
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ReportTabComponent.risk.tl.spec.ts`)
- Methods: deletedEntry
- Reason: async≥3：竞态 / destructive / state inconsistency

### TaskActionPane

**Pass 1** (`TaskActionPane.interaction.tl.spec.ts`)
- Methods: model, action, generalActionModel, actionNames, selectSheetError, isGeneralAction, addAction, copyAction, editAction, changeView, isValid, save, showSelectDashboardDialog, updateValues, updateEmailAutoComplete, getEmails, addToAutoCompleteList, initActions, setDefaultAction, setCurrentAction, getDefaultAction, getSheetPath
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`TaskActionPane.risk.tl.spec.ts`)
- Methods: setSelectedOption, checkSelectedOption, isTabSelected, changeActionType, changeViewsheet, getPrintLayout, clearHighlightConditions, getBookmarks, getHighlights, getParameters, getTableDataAssemblies, deleteAction, initForm, getAutoCompleteLists
- Reason: async≥3：竞态 / destructive / state inconsistency

### TaskConditionPane

**Pass 1** (`TaskConditionPane.interaction.tl.spec.ts`)
- Methods: model, startTime, condition, timeCondition, completionCondition, conditionNames, formStartTimeData, formStartTime, formEndTime, formDate, updateStartTime, updateStartTimeData, userSetStartTime, onStartTimeDataChanged, setStartTime, updateDate, dateChange, updateEndTime, setEndTime, setSelectedOption, changeConditionType, changeView, save, addCondition, copyCondition, editCondition, updateValues, updateTimesAndDates, changeMonthRadioOption, setLocalTimeZone, initForm, addIntervalControl, addDayOfMonthControl, addWeekOfMonthControl, updateDaysOfWeekStatus, updateMonthsOfYearStatus, getWeekdayControls, getMonthControls, updateWeekdayOnly, checkTimeNotNull, checkStartTimeData, timeSmallerThan, return, atLeastOneSelected, isPresent, updateList, selectAll, initConditions, getDefaultTimeConditionModel, saveConditionType, initTimeZone, currentTimeZoneOffset, changeServerTimeZone, updateTimeZone, convertToTimeZone, convertTimeCondition, timeConditions, convertTime, isTimeCondition, loadingTasks
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`TaskConditionPane.risk.tl.spec.ts`)
- Methods: deleteCondition, removeIntervalControl
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`TaskConditionPane.display.tl.spec.ts`)
- Methods: showMeridian
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### TaskOptionsPane

**Pass 1** (`TaskOptionsPane.interaction.tl.spec.ts`)
- Methods: locale, startDateChange, endDateChange, getExecuteAsType, disableExecuteAs, updateExecuteAs, save, openExecuteAsDialog, getGroupModel, initForm, loadingUsers
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`TaskOptionsPane.risk.tl.spec.ts`)
- Methods: model, clearStartDate, clearEndDate, clearUser, getExecuteAsName
- Reason: async≥3：竞态 / destructive / state inconsistency

### ScheduleTaskEditorComponent

**Pass 1** (`ScheduleTaskEditorComponent.interaction.tl.spec.ts`)
- Methods: resetConditionListView, updateTaskName, updateOldTaskName, updateConditionModel, updateActionModel, updateOptionsModel, onCloseEditor
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ScheduleTaskEditorComponent.risk.tl.spec.ts`)
- Methods: saveSuccess, executeAsGroup, onCancelTask
- Reason: async≥3：竞态 / destructive / state inconsistency

### ScheduleTaskListComponent

**Pass 1** (`ScheduleTaskListComponent.interaction.tl.spec.ts`)
- Methods: loadTasks, changeShowType, initTaskList, handleError, newTask, runTask, stopTask, disableTask, changeSortType, selectTask, openError, ngAfterContentChecked, onResize, selectFolderByPath, keepExpandedNodes, selectNode, substring, selectAll, findSelectedTasks, isToggleTasksEnabledDisabled, openTreeContextmenu, hasMenuFunction, hasMenu, createActions, createNewTaskFolderAction, createEditTaskFolderAction, dragTask, getTaskOwnerLabel, isCreateTaskEnabled, getDateLabel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ScheduleTaskListComponent.risk.tl.spec.ts`)
- Methods: currentFolder, selectAllChecked, isFullName, mergeChange, convertTaskOwner, newFolder, parentFolder, editTask, editTaskFolder, subscribe, getTaskName, removeItems, removeFolders, navigateToTaskEditor, loadTaskFolderTree, nodeDrop, getParentPath, getTaskModel, moveTasks, moveFolder, moveItems, getMovedPaths, removeEnable, createDeleteTaskFolderAction, getMutiEditPath
- Reason: async≥3：竞态 / destructive / state inconsistency

### TreeNodeComponent

**Pass 1** (`TreeNodeComponent.interaction.tl.spec.ts`)
- Methods: contextmenuListener, touchstartListener, touchendListener, toggleNode, clickSelectNode, mousedownSelectNode, tooltip, selectNode, doubleClickNode, hasChildren, notExpandableType, favoritesUser, dragStarted, dragOver, isDraggable, isHighLight, isSelected, isGrayedOut, getFieldName, onDrop, keepAllChildren, getSort, isToggleElementEventTarget, inSearchCollapsed, hasMenu
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`TreeNodeComponent.risk.tl.spec.ts`)
- Methods: touchmoveListener, updateInViewport
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`TreeNodeComponent.display.tl.spec.ts`)
- Methods: getIcon, getToggleIcon, nodeLabel, getVirtualScrollShowChildren
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### TreeComponent

**Pass 1** (`TreeComponent.interaction.tl.spec.ts`)
- Methods: root, showRoot, searchStr, canUseVirtualScroll, isRecentView, ngAfterViewChecked, searchEnabled, treeContainerMaxHeight, search, calculateBounds, expandNode, collapseNode, refreshScroll, doubleclickNode, clickNode, isSelectedNode, exclusiveSelectNode, selectNode, setHighLightNodes, enforceSinglePathToRoot, onDrag, onDragOver, onDrop, addShiftNodes, findNodes, addSelectedNodes, addNodeToArray, getParentNode, getNodeByData, getNodeData, getTopAncestor, forEach, selectAndExpandToNode, expandToNode, isHostGlobalParent, nodeEquals, deselectAllNodes, onScroll, focusedObservable, isFocusedNode, onKey, nextNode, prevNode, openHelp, switchRecent, getVirtualBottom
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`TreeComponent.risk.tl.spec.ts`)
- Methods: ngAfterViewInit, fixSelectedNodes, expandAll, clearSearchContent, onSearchEscape, subscribeVScroll, unSubscribeVScroll
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`TreeComponent.display.tl.spec.ts`)
- Methods: showHelpLink
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

---

> **以下为 2026-06-12 补充扫描新增组件**

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| 待审核 | ScheduleTabComponent | 20 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ActionAccordionComponent | 861 | 0 | 6 | **multi-pass** | ✅ 推进 | ⚠️ action-accordion.spec.ts | test: check clear all parameters; test: should get correct highlight name for alert | P1: ActionAccordionComponent.interaction.tl.spec.ts<br>P2: ActionAccordionComponent.risk.tl.spec.ts |
| 待审核 | ScheduleTaskDialogComponent | 57 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ schedule-task-dialog.spec.ts | Bug #21217 task name control (broken test) | single pass |
| 待审核 | AddParameterDialogComponent | 265 | 0 | 2 | **single-pass** | ✅ 推进 | ⚠️ add-parameter-dialog.component.spec.ts | test: should pop confirm dialog when create duplicate parameter; broken test: check can create parameter; test: check can edit parameter | single pass (+内存泄漏) |
| 待审核 | EditableTableComponent | 45 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ParameterTableComponent | 55 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ parameter-table.component.spec.ts | test: should show confirm when to delete parameter; test: check can edit parameter; test: should display timeinstant/array parameter correctly | single pass |
| 待审核 | CreateTaskFolderDialogComponent | 73 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | TaskFolderBrowserComponent | 82 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | RepositoryListViewComponent | 27 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | RepositoryMobileViewComponent | 45 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | RepositoryTreeViewComponent | 170 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ChangePasswordDialogComponent | 62 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | MvExceptionsPortalDialogComponent | 29 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DataNotificationsComponent | 19 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | MoveDataModelDialogComponent | 71 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | MoveDatasourceDialogComponent | 117 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | AssetDescriptionComponent | 28 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DataSourcesBrowserComponent | 157 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | DatasourceCategoryPaneComponent | 26 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DatasourceListingPaneComponent | 25 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DatasourceListingComponent | 32 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DatasourceSearchComponent | 23 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | LogicalModelAttributeEditorComponent | 304 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | LogicalModelColumnEditorComponent | 54 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ElementTreeNodeComponent | 205 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | LogicalModelEntityEditorComponent | 38 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | LogicalModelEntityPaneComponent | 97 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | LogicalModelExpressionEditorComponent | 182 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | PhysicalTableTreeNodeComponent | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PhysicalTableTreeComponent | 250 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PhysicalModelTableTreeNodeComponent | 81 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PhysicalModelTableTreeComponent | 44 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PhysicalStatusBarComponent | 21 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DataModelFolderBrowserComponent | 78 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ChoseAdditionalConnectionDialogComponent | 74 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ChosePhysicalViewDialogComponent | 41 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | SelectAttributePaneComponent | 73 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | SelectQueryFieldPaneComponent | 24 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | EditJoinTableColumnComponent | 81 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | EditJoinTableComponent | 79 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | JoinNodeGraphComponent | 268 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | LoadingIndicatorPaneComponent | 22 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PhysicalModelEditTableComponent | 36 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | AddJoinDialogComponent | 119 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | EditJoinDialogComponent | 81 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PhysicalTableJoinsComponent | 319 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | DatasourcesDatasourceDialogComponent | 37 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ViewSampleDataDialogComponent | 24 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DatabaseQueryComponent | 217 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | FieldsPaneComponent | 305 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | QueryConditionsPaneComponent | 79 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | BrowseFieldValuesDialogComponent | 26 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | EditDataTypeDialogComponent | 28 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | EditFieldDialogComponent | 97 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | QueryFieldsPaneComponent | 462 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: QueryFieldsPaneComponent.interaction.tl.spec.ts<br>P2: QueryFieldsPaneComponent.risk.tl.spec.ts |
| 待审核 | QueryGroupingPaneComponent | 53 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | QueryJoinEditPaneComponent | 124 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | QueryLinkGraphPaneComponent | 113 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | QueryLinkPaneComponent | 117 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | QueryNetworkGraphPaneComponent | 713 | 0 | 10 | **multi-pass** | ✅ 推进 |  |  | P1: QueryNetworkGraphPaneComponent.interaction.tl.spec.ts<br>P2: QueryNetworkGraphPaneComponent.risk.tl.spec.ts |
| 待审核 | QueryTablePropertiesDialogComponent | 57 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | QuerySortPaneComponent | 22 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | QueryPreviewTableComponent | 245 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | SqlQueryPreviewPaneComponent | 79 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | FreeFormSqlPaneComponent | 73 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |

---

## 补充扫描新增 multi-pass 组件详情（2026-06-12）

### ActionAccordionComponent

**Pass 1** (`ActionAccordionComponent.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, action setter, viewsheet setter, sheet getter, editAction, deleteAction, copyAction, checkClearAllParameters, clearAllParameters, addParameter, editParameter, deleteParameter, getHighlightName, getDeliveryFormatText, getSheetPath, getBookmarks, getHighlights, getParameters, getTableDataAssemblies, getPrintLayout, changeViewsheet, getOwner, initForm, updateEmailAutoComplete, getEmails, addToAutoCompleteList, getAutoCompleteLists, setDefaultAction, setCurrentAction, getDefaultAction, updateValues, setSelectedOption, checkSelectedOption, isTabSelected
- Reason: 回归主体：lifecycle, action CRUD, parameter management, form initialisation, sheet/viewsheet wiring

**Pass 2** (`ActionAccordionComponent.risk.tl.spec.ts`)
- Methods: ngAfterViewChecked, isGeneralAction, changeActionType, getGeneralActionModel, actionNames, clearHighlightConditions, selectSheetError, isValid, save, showSelectDashboardDialog, getSheetName
- Reason: async≥3：竞态 / destructive / state inconsistency (6 subscriptions)

### QueryFieldsPaneComponent

**Pass 1** (`QueryFieldsPaneComponent.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, isSelected, selectField, toggleSelectAll, moveUp, moveDown, editField, deleteField, addExpression, editExpression, addField, getFieldIcon, getDataType, getAlias, isExpression, refreshPreview, reset, ok, cancel, updateFields, sortFields, getFieldLabel
- Reason: 回归主体：field selection, ordering, CRUD, HTTP preview refresh

**Pass 2** (`QueryFieldsPaneComponent.risk.tl.spec.ts`)
- Methods: loadFields, fetchPreview, validateFields, refreshSearchFields, processFieldDrop, handleError
- Reason: async>5 (7 subscriptions)：竞态 / HTTP loading / error states

### QueryNetworkGraphPaneComponent

**Pass 1** (`QueryNetworkGraphPaneComponent.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, ngAfterViewChecked, ngOnDestroy, scale, bind, registerNode, selectNode, connectNode, addEndpoint, showEndpoints, hideEndpoints, openJoinEditPane, closeJoinEditPane, setRepaintTimer, setContainer, getSelectedNode, getJoinTooltip, checkJoinCompatibility, graphEndpoints, isHighlight, isSameTableAutoAlias, getJoinActions, showJoinActions, fireSelectedNodesChanged, onSelectionBox, getThumbnailClasses, each, forEach
- Reason: 回归主体：canvas graph lifecycle, node selection, join endpoint wiring

**Pass 2** (`QueryNetworkGraphPaneComponent.risk.tl.spec.ts`)
- Methods: connectionDeletable, removeJoinCondition, deleteNode, clearJoin, clearTable, clearTableEnabled, clearJoinEnabled, refreshAnchors, setDraggable, moveNodes, contextMenu, createActions, keydown, removeSelectTables, doRemove, selectAll, clearSelection, refreshDragSelection, isColumnExist, convertToHTMLCharacterEntity
- Reason: async>5 (10 subscriptions)：竞态 / destructive operations / state inconsistency
