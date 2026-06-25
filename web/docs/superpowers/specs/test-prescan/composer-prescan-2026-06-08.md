# composer Route Pre-scan Report

**日期**: 2026-06-08（2026-06-24 补充扫描，新增 30 个组件）
**候选组件数**: 110（原 80，2026-06-24 新增 30）| **建议推进**: 102 | **建议跳过**: 8 | **待审核**: 0 | **多 pass 组件**: 32
**测试进度**: ✅已测试 80 / 110 | 待测 24 / 110 | ⏭ 跳过 6 / 110

## 状态说明
- 第一列「状态」初始为「待审核」，人工审核后改为 ✅已测试 / ⏭已跳过
- ⚠️ 有旧spec — 新测试通过后在同 PR 内删除旧 .spec.ts
- 「旧 spec 备注」列记录旧测试中不易从源码推断的 case，生成新测试时参考

## 分类说明
- **single-pass**：logic_lines ≤ 500 AND dispatch ≤ 2 AND async ≤ 5 → `component-name.component.tl.spec.ts`
- **multi-pass**：logic_lines > 500 OR dispatch ≥ 3 OR async > 5 → 多文件 pass 计划
- single-pass 按需追加：async≥3 竞态 / async≥1 内存泄漏 / dispatch≥3 边界

## 候选组件清单

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| ✅已测试 | ComposerAppComponent | 93 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 | AiAssistantPanelComponent | 177 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | DownloadTargetComponent | 84 | 1 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 P1P2P3 | ComposerMainComponent | 2512 | 8 | 34 | **multi-pass** | ✅ 推进 |  | Bug #16301 set embeddedId set if opening an embedded vs; Bug #18803 should disable format pane when select device layout; Bug #18805 should enable for | P1: composer-main.component.interaction.tl.spec.ts ✅<br>P2: composer-main.component.risk.tl.spec.ts ✅<br>P3: composer-main.component.display.tl.spec.ts ✅ |
| ✅已测试 P1P2P3 | ComposerToolbarComponent | 1746 | 8 | 11 | **multi-pass** | ✅ 推进 |  | BUg #21103 should not show preview button on worksheet; Bug #17208 enable layout align when select multi object on layouts; Bug #16940 disbale move mo | P1: composer-toolbar.component.interaction.tl.spec.ts ✅<br>P2: composer-toolbar.component.risk.tl.spec.ts ✅<br>P3: composer-toolbar.component.display.tl.spec.ts ✅ |
| ✅已测试 | SplitPane | 120 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ split-pane.component.tl.spec.ts |
| ✅已测试 P1P2 | AssetTreePane | 843 | 1 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: asset-tree-pane.component.interaction.tl.spec.ts ✅<br>P2: asset-tree-pane.component.risk.tl.spec.ts ✅ |
| ✅已测试 | ToolboxPane | 144 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ toolbox-pane.component.tl.spec.ts |
| ✅已测试 | ScriptTreePane | 74 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ script-tree-pane.component.tl.spec.ts |
| ✅已测试 | ComponentsPane | 302 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ components-pane.component.tl.spec.ts |
| ✅已测试 | StyleTreePane | 97 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ style-tree-pane.component.tl.spec.ts |
| ✅已测试 P1P3 | VSFormatsPane | 679 | 4 | 1 | **multi-pass** | ✅ 推进 | ⚠️ vs-formats-pane.spec.ts | Bug #16685, Bug #16689 check the aligment combox status; Bug #18597, BUg #18664 color,border, aligment status; Bug #18060, Bug #18342 for wrap text on | P1: vs-formats-pane.component.interaction.tl.spec.ts ✅<br>P3: vs-formats-pane.component.display.tl.spec.ts ✅ |
| ✅已测试 | WSCompositeTableSidebarPane | 77 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ ws-composite-table-sidebar-pane.component.tl.spec.ts |
| ✅已测试 P1P2P3 | WSPaneComponent | 944 | 5 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: ws-pane.component.interaction.tl.spec.ts ✅<br>P2: ws-pane.component.risk.tl.spec.ts ✅<br>P3: ws-pane.component.display.tl.spec.ts ✅ |
| ✅已测试 P1P2P3 | VSPane | 2071 | 7 | 23 | **multi-pass** | ✅ 推进 | ⚠️ viewsheet-pane.component.spec.ts | Bug #10442 make sure to update send to back/front enabled after adding vs object to vs; Bug #16274 make sure to update send to back/front enabled afte | P1: viewsheet-pane.component.interaction.tl.spec.ts ✅<br>P2: viewsheet-pane.component.risk.tl.spec.ts ✅<br>P3: viewsheet-pane.component.display.tl.spec.ts ✅ |
| ✅已测试 P1P2P3 | ViewerAppComponent | 3363 | 10 | 53 | **multi-pass** | ✅ 推进 | ⚠️ viewer-app.spec.ts | Bug #16456 TODO, logica changed, can not get fixed dropdown pane; Bug #19176 hide full screen in preview; Bug #16961 should refresh scale to screen vs | P1: viewer-app.component.interaction.tl.spec.ts ✅<br>P2: viewer-app.component.risk.tl.spec.ts ✅<br>P3: viewer-app.component.display.tl.spec.ts ✅ |
| ✅已测试 | ScriptEditPaneComponent | 194 | 2 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ script-edit-pane.component.tl.spec.ts |
| ✅已测试 | StylePaneComponent | 89 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ style-pane.component.tl.spec.ts |
| ✅已测试 | ComposerEmptyEditor | 95 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ composer-empty-editor.component.tl.spec.ts |
| ✅已测试 | SheetTabSelectorComponent | 87 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ sheet-tab-selector.component.tl.spec.ts |
| ✅已测试 P1P2P3 | VSBindingPane | 805 | 4 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: vs-binding-pane.component.interaction.tl.spec.ts ✅<br>P2: vs-binding-pane.component.risk.tl.spec.ts ✅<br>P3: vs-binding-pane.component.display.tl.spec.ts ✅ |
| ✅已测试 | VsWizardComponent | 371 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ vs-wizard.component.tl.spec.ts |
| ✅已测试 | NotificationsComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 | 已删除 | 无 | single pass (+内存泄漏) ✅ notifications.component.tl.spec.ts |
| ✅已测试 | SaveViewsheetDialog | 95 | 0 | 1 | **single-pass** | ✅ 推进 | 已删除 | Bug #20421 check name for viewsheet | single pass (+内存泄漏) ✅ save-viewsheet-dialog.component.tl.spec.ts |
| ✅已测试 | SaveTableStyleDialog | 96 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ save-table-style-dialog.component.tl.spec.ts |
| ✅已测试 | SaveWorksheetDialog | 89 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 | SaveScriptDialog | 95 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ save-script-dialog.component.tl.spec.ts |
| ✅已测试 | EditCustomPatternsDialog | 86 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ edit-custom-patterns-dialog.component.tl.spec.ts |
| ✅已测试 | ViewsheetPropertyDialog | 112 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ viewsheet-property-dialog.component.tl.spec.ts |
| ✅已测试 | ToolbarGroup | 84 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 P1P2 | ImportCSVDialog  | 423 | 1 | 33 | **multi-pass** | ✅ 推进 | ⚠️ import-csv-dialog.component.spec.ts | test: should throw an error on empty file | P1: import-csv-dialog.component.interaction.tl.spec.ts ✅<br>P2: import-csv-dialog.component.risk.tl.spec.ts ✅ |
| ✅已测试 P1P2 | SQLQueryDialog | 386 | 0 | 10 | **multi-pass** | ✅ 推进 |  |  | P1: sql-query-dialog.component.interaction.tl.spec.ts ✅<br>P2: sql-query-dialog.component.risk.tl.spec.ts ✅ |
| ✅已测试 P1P2 | TabularQueryDialog | 374 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: tabular-query-dialog.component.interaction.tl.spec.ts ✅<br>P2: tabular-query-dialog.component.risk.tl.spec.ts ✅ |
| ✅已测试 P1P2 | GroupingDialog | 346 | 0 | 52 | **multi-pass** | ✅ 推进 |  |  | P1: grouping-dialog.component.interaction.tl.spec.ts ✅<br>P2: grouping-dialog.component.risk.tl.spec.ts ✅ |
| ✅已测试 | SelectDataSourceDialog | 46 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass ✅ select-data-source-dialog.component.tl.spec.ts |
| ✅已测试 | EmbeddedTableDialog | 49 | 0 | 1 | **single-pass** | ✅ 推进 | 已删除 | test: should not allow non-positive number of rows or columns; test: should not allow duplicate names | single pass (+内存泄漏) ✅ embedded-table-dialog.component.tl.spec.ts |
| ✅已测试 P1P2 | VariableAssemblyDialog | 334 | 0 | 26 | **multi-pass** | ✅ 推进 | 已删除 | Bug #20319 should allow some characters for variable | P1: variable-assembly-dialog.component.interaction.tl.spec.ts ✅<br>P2: variable-assembly-dialog.component.risk.tl.spec.ts ✅ |
| ✅已测试 | VariableInputDialog | 214 | 0 | 0 | **single-pass** | ✅ 推进 |  | Bug #16824 Make sure the default value of a boolean type is false | single pass ✅ variable-input-dialog.component.tl.spec.ts |
| ✅已测试 P1P2 | VPMPrincipalDialogComponent | 84 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: vpm-principal-dialog.component.interaction.tl.spec.ts ✅<br>P2: vpm-principal-dialog.component.risk.tl.spec.ts ✅ |
| ✅已测试 P1P2 | AssetTreeComponent | 705 | 2 | 8 | **multi-pass** | ✅ 推进 | 已删除 | Bug 10264 make sure asset tree node remain expanded. | P1: asset-tree.component.interaction.tl.spec.ts ✅<br>P2: asset-tree.component.risk.tl.spec.ts ✅ |
| ✅已测试 | FontPane | 129 | 0 | 1 | **single-pass** | ✅ 推进 | 已删除 | for Bug #19781 | single pass (+内存泄漏) ✅ font-pane.component.tl.spec.ts |
| ✅已测试 | DynamicComboBox | 323 | 0 | 1 | **single-pass** | ✅ 推进 | 已删除 | Bug #17341  and Bug #17765 disbale variable when no variable list and can not select disabled variable irem; Bug #17341; Bug #17765 | single pass (+内存泄漏) ✅ dynamic-combo-box.component.tl.spec.ts |
| ✅已测试 | AlphaDropdown | 42 | 0 | 0 | **single-pass** | ✅ 推进 | 已删除 | Bug #19399 should keep alpha value | single pass ✅ alpha-dropdown.component.tl.spec.ts |
| ✅已测试 | BindingBorderPane | 331 | 0 | 0 | **single-pass** | ✅ 推进 | 已删除 | Bug #10699 and Bug #17594 make sure when setting border style and color to default; test: should set borders to null when default borders selected | single pass ✅ binding-border-pane.component.tl.spec.ts |
| ✅已测试 P1P2 | RadiusDropdown | 74 | 0 | 10 | **multi-pass** | ✅ 推进 |  |  | P1: radius-dropdown.component.interaction.tl.spec.ts ✅<br>P2: radius-dropdown.component.risk.tl.spec.ts ✅ |
| ✅已测试 | FormattingPane | 185 | 0 | 0 | **single-pass** | ✅ 推进 | 已删除 | test: empty decimal format should increase decimal | single pass ✅ formatting-pane.component.tl.spec.ts |
| ✅已测试 | FormatCSSPane | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ format-css-pane.component.tl.spec.ts |
| ✅已测试 | PresenterPropertyDialog | 74 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ presenter-property-dialog.component.tl.spec.ts |
| ✅已测试 P1P2 | WSAssemblyGraphPaneComponent | 1080 | 2 | 20 | **multi-pass** | ✅ 推进 | 不存在 | test: should not open a confirm dialog for a non-primary assembly | P1: ws-assembly-graph-pane.component.interaction.tl.spec.ts ✅<br>P2: ws-assembly-graph-pane.component.risk.tl.spec.ts ✅ |
| ✅已测试 | WSCompositeTableFocusPaneComponent | 89 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 P1P2 | WSDetailsPaneComponent | 727 | 1 | 3 | **multi-pass** | ✅ 推进 |  |  | P1: ws-details-pane.component.interaction.tl.spec.ts ✅<br>P2: ws-details-pane.component.risk.tl.spec.ts ✅ |
| ✅已测试 | VSLoadingDisplay | 71 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 P1P2P3 | EditableObjectContainer | 1592 | 10 | 4 | **multi-pass** | ✅ 推进 | 不存在 | for Bug #9947; Bug #9817 ensure that when object is dropped the layout dialog is opened; Bug #19077 drag detail calcfield to form object the layout di | P1: editable-object-container.component.interaction.tl.spec.ts ✅<br>P2: editable-object-container.component.risk.tl.spec.ts ✅<br>P3: editable-object-container.component.display.tl.spec.ts ✅ |
| ✅已测试 P1P2 | ComposerSelectionContainerChildren | 512 | 1 | 4 | **multi-pass** | ✅ 推进 |  |  | P1: composer-selection-container-children.component.interaction.tl.spec.ts ✅<br>P2: composer-selection-container-children.component.risk.tl.spec.ts ✅ |
| ✅已测试 P1P2P3 | LayoutPane | 886 | 8 | 16 | **multi-pass** | ✅ 推进 |  |  | P1: layout-pane.component.interaction.tl.spec.ts ✅<br>P2: layout-pane.component.risk.tl.spec.ts ✅<br>P3: layout-pane.component.display.tl.spec.ts ✅ |
| ✅已测试 | ConsoleDialogComponent | 88 | 0 | 1 | **single-pass** | ✅ 推进 | 已删除 | callbacks queue dialog opens that NG0205 after the fixture is destroyed.; (after the fixture is destroyed) is a no-op instead of NG0205. | single pass (+内存泄漏) ✅ console-dialog.component.tl.spec.ts |
| ✅已测试 | PagingControlComponent | 84 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ paging-control.component.tl.spec.ts |
| ✅已测试 | ViewerMobileToolbarComponent | 72 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ viewer-mobile-toolbar.component.tl.spec.ts |
| ✅已测试 | VsBookmarkPaneComponent | 97 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ vs-bookmark-pane.component.tl.spec.ts |
| ✅已测试 P1P2P3 | VSObjectContainer | 633 | 3 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: vs-object-container.component.interaction.tl.spec.ts ✅<br>P2: vs-object-container.component.risk.tl.spec.ts ✅<br>P3: vs-object-container.component.display.tl.spec.ts ✅ |
| ✅已测试 | ExportDialog | 103 | 1 | 3 | **single-pass** | ✅ 推进 |  | Bug #17235 should not; test: should show error | single pass (+竞态+内存泄漏) ✅ export-dialog.component.tl.spec.ts |
| ✅已测试 | EmailDialog | 103 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ email-dialog.component.tl.spec.ts |
| ✅已测试 | ScheduleDialog | 106 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ schedule-dialog.component.tl.spec.ts |
| ✅已测试 | BookmarkPropertyDialog | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ bookmark-property-dialog.component.tl.spec.ts |
| ✅已测试 | ShareEmailDialogComponent | 79 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ share-email-dialog.component.tl.spec.ts |
| ✅已测试 | ShareGoogleChatDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ share-google-chat-dialog.component.tl.spec.ts |
| ✅已测试 | ShareSlackDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) ✅ share-slack-dialog.component.tl.spec.ts |
| ✅已测试 | RemoveBookmarksDialog | 69 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ remove-bookmarks-dialog.component.tl.spec.ts |
| ✅已测试 | BindingEditor | 267 | 1 | 1 | **single-pass** | ✅ 推进 | ⚠️ binding-editor.spec.ts | Bug #20245; for Bug #20163; test: Crosstab should not have a percent by option | single pass (+内存泄漏) ✅ binding-editor.component.tl.spec.ts |
| ✅已测试 | VSObjectView | 190 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) ✅ vs-object-view.component.tl.spec.ts |
| ✅已测试 P1P2 | VsWizardPane | 901 | 1 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: vs-wizard-pane.component.interaction.tl.spec.ts ✅<br>P2: vs-wizard-pane.component.risk.tl.spec.ts ✅ |
| ✅已测试 P1P2 | ObjectWizardPane | 556 | 1 | 3 | **multi-pass** | ✅ 推进 |  |  | P1: object-wizard-pane.component.interaction.tl.spec.ts ✅<br>P2: object-wizard-pane.component.risk.tl.spec.ts ✅ |
| ✅已测试 | ComponentTree | 86 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| ✅已测试 | ComposerBindingTree | 66 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 P1P2 | CodemirrorComponent | 417 | 1 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: codemirror.component.interaction.tl.spec.ts ✅<br>P2: codemirror.component.risk.tl.spec.ts ✅ |
| ✅已测试 | ViewsheetOptionsPane | 131 | 0 | 5 | **single-pass** | ✅ 推进 | 已删除 | #17036,the design mode data size should be disable when use worksheet; Bug #17303 Clear button should be enabled when has datasource; Bug #10157 Clear | single pass (+竞态+内存泄漏) ✅ viewsheet-options-pane.component.tl.spec.ts |
| ✅已测试 | FiltersPane | 119 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass ✅ filters-pane.component.tl.spec.ts |
| ✅已测试 | ScreensPane | 160 | 0 | 2 | **single-pass** | ✅ 推进 | ⚠️ screens-pane.spec.ts | Bug #19354 Click device 'Delete' button, confirm dialog should pop up.; Bug #18417, clear button should be disabled when no print layout; Bug #19349,  | single pass (+内存泄漏) ✅ screens-pane.component.tl.spec.ts |
| ✅已测试 | LocalizationPane | 81 | 0 | 0 | **single-pass** | ✅ 推进 | 已删除 | Bug #19630 Components node should display; Bug #19118 should focus on the selected column | single pass ✅ localization-pane.component.tl.spec.ts |
| ✅已测试 | ViewsheetScriptPane | 184 | 2 | 0 | **single-pass** | ✅ 推进 | 已删除 | TypeError when the timer fired after the fixture was destroyed.; Bug #18853 should select onRefresh by default; fixture is destroyed (which nulls code | single pass ✅ viewsheet-script-pane.component.tl.spec.ts |

## 多 pass 组件详情

### ComposerMainComponent

**Pass 1** (`composer-main.component.interaction.tl.spec.ts`)
- Methods: handleMessageEvent, ngAfterViewInit, focusedSheet, fixAutoSaveFiles, focusedViewsheet, focusedSheetPreview, onFocusedSheetChanged, getUpdatedLayoutPreviewSheet, onFocusedLibraryAssetChanged, onSheetUpdated, updateSidebar, updatelibrarySidebar, onSplitDragEnd, onTabClick, isActive, isActiveSheet, isSameTab, isSameSheet, isPrintLayout, trackByFn, closePreview, copySheet, cutSheet, checkRenamedAssembly, onTabSelected, onSheetSelected, onTabClosed, onLibClosed, onSheetClosed, onLibraryClosed, onToggleSnapToGrid, onToggleSnapToObjects, openViewsheetOptionDialog, getScriptPane, showCloseSheetConfirmMessage, dependencyChange, saveOnly, saveAndClose, saveAndCloseLib, closeLibTab, closeSheet, onSheetReload, onSaveViewsheet, onTransformFinished, onSaveWorksheet, setGrayedOutFields, copyAssembly, cutAssembly, pasteObjects, pasteWithCutFinish, forEach, bringAssemblyToFront, bringAssemblyForward, sendAssemblyToBack, sendAssemblyBackward, onLayoutObjectChange, openNewWorksheet, onNewTableStyle, getCustomPatternsTree, updateTableStyle, updateTableStylePreview, openNewScriptAsset, openNewWsWithWizard, createQueryOrUploadTable, openNewQuery, newUploadTable, openNewViewsheet, openWorksheet, openViewsheet, openSheet, openLibraryAsset, saveWorksheet, saveWorksheet0, then, saveTableStyle, saveTableStyleAs, onOpenCustomEdit, saveScript, saveScriptAs, saveViewsheet, saveViewsheet0, saveWorksheetAs, subscribe, saveWorksheetAs0, resolve, processSaveWorksheet, finishSave, closeTestDriveWorksheet, toggleImportDialog, saveViewsheetAs, saveViewsheetAs0, previewViewsheet, processNotification, updateSheet, worksheetCompositionChanged, editJoin, getIndexOfSheet, getIndexOfTab, navigateToExisting, navigateToExistingSheet, navigateToExistingTab, closeSheetOnServer, defaultSaveToFolder, createAssetEntry, focusedSheetAsWorksheet, layout, layoutName, layoutGuide, map, refreshChangedAssembly, changeBindingAssemblyName, openEditPane, refreshSelectViewsheet, refreshAssembly, isModalOpen, setKeydownListener, onKeydown, layoutRuntimeId, undoEnabled, redoEnabled, confirmSaveWorksheetWithoutPrimaryAssembly, checkCycle, beforeunloadHandler, toggleSplitPane, initComposerClient, listenGettingStartedEvent, add, gettingStartedCreateDashboard, closeComposer, createWorksheetIdentifier, getParentSocketConnection, newViewsheet, closeWizardPane, goToFullEditor, goToEditor, goToWizardPane, switchBindingToWizard, fullViewVisible, processPreviewMessageCommand, openScript, isIframe, asViewsheet, asWorksheet, asScript, asTableStyle, openVSOnPortal, onSaveWorksheetFinish
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`composer-main.component.risk.tl.spec.ts`)
- Methods: focusedTab, updateFocusedSheet, openAutoSaveFiles, recycleAutoSaveFiles, updateFocusedTab, checkRemovedAssembly, openScriptOptions, getScriptTreePane, removeAssembly, openAutoSaveAsset, getParent, worksheetCancel, sendEvent, clearFocusedObjects, isSheet, removeKeydownListener, refreshAiAssistantContext
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`composer-main.component.display.tl.spec.ts`)
- Methods: showPaste, showLinkVSInTab, getLinkVSLabel, updateFormat, layoutFormatObjects, layoutShowing, openFormatPane
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ComposerToolbarComponent

**Pass 1** (`composer-toolbar.component.interaction.tl.spec.ts`)
- Methods: sheet, crossJoinEnabled, tables, focusedObjects, alignObjects, layoutDistributeEnabled, layoutAlignEnabled, layoutResizeEnabled, openViewsheetWizard, notify, save, getTabularDataSourceTypes, save0, saveAs, options, preview, refresh, getTableStyleModel, undo, undoEnabled, redo, redoEnabled, currentLayout, layoutRuntimeId, cut, copy, paste, snapToGridChanged, snapToObjectsChanged, getAlignObj, layoutAlign, layoutDistribute, openVPMPrincipalDialog, getNextName, concatEnabled, newConcatTable, joinEnabled, newJoinTable, createCrossJoin, layoutWorksheetGraph, isInitializedWorksheet, isWorksheet, isViewsheet, isScript, isTableStyle, isObjectSelected, isPreview, scrollLeft, scrollRight, enableLayoutAlign, isPrintLayout, editPrintHeader, editPrintContent, editPrintFooter, isPrintLayoutSelected, selectGuideType, isGuideSelected, zoomLayout, zoom, isZoomItemSelected, zoomOutEnabled, zoomInEnabled, vs, worksheetOperationsDisabled, resizeListener, toggleFullScreen, getNonMenuToolbarActions, getOptionsTooltipText, openSession, getMergeMenuToolbarActions, saveTooltipText, saveAsTooltipText, previewOperations, editOperations, layoutOperations, snappingOperations, getVPMPrincipalToolbarActions, jdbcExists, sqlEnabled, pasteEnabled, isCompositeView, left, top, width, height
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`composer-toolbar.component.risk.tl.spec.ts`)
- Methods: loadDataSources, ngAfterViewInit, addDropdownListeners, subscribe, newWorksheet, forEach, fireMoveResize, layoutResize, enterParameters, onFullScreenChange
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`composer-toolbar.component.display.tl.spec.ts`)
- Methods: enableFormatPainter, layoutShowing, hiddenComposerIcon, getDatabaseLabel, getTabularLabel
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### AssetTreePane

**Pass 1** (`asset-tree-pane.component.interaction.tl.spec.ts`)
- Methods: openNodeSheet, openSheetFromAsset, forEach, addRecentlyViewed, getRecentRootFun, getEntryName, hasMenuFunction, hasMenu, openAssetTreeContextmenu, createActions, createNewFolderAction, createNewQueryAction, createOpenViewsheetAction, createOpenMetaViewsheetAction, createRenameViewsheetAction, createRenameWorksheetAction, createRenameFolderAction, createNewTableStyleAction, createNewScriptAction, containsAuditNodes, createNewFolder, sendAddFolderEvent, createOpenScriptAction, createOpenTableStyleAction, createRenameScriptAction, createRenameTableStyleAction, showRenameAssetDialog, renameAsset, isAssetOpened, containsOpenedLibraryAssets, dispatchRenameAssetEvent, showMessage, nodeDrag, isRejectFunction, isRejectNodes, nodeDrop
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`asset-tree-pane.component.risk.tl.spec.ts`)
- Methods: deleteEntries, createDeleteViewsheetAction, deleteRecentAssets, createDeleteWorksheetAction, createDeleteFolderAction, createDeleteScriptAction, dispatchRemoveAssetEvent, moveAssetToRecyclingBin, deleteAssets, dispatchChangeAssetEvent, confirm
- Reason: async≥3：竞态 / destructive / state inconsistency

### VSFormatsPane

**Pass 1** (`vs-formats-pane.component.interaction.tl.spec.ts`)
- Methods: focusedAssemblies, filter, color, get, colorType, backgroundColor, backgroundColorType, format, getFont, getAlignment, changeColor, isViewsheetSelected, isValueFillVisible, isNonEditableChartVOSelected, isFontDisabled, isEditDisabled, isChartEditableSelected, isAlignDisabled, isHAlignmentEnabled, isVAlignmentEnabled, isWrapTextDisabled, isBorderDisabled, isRoundCornerDisabled, isColorDisabled, isBackgroundDisabled, isDynamicColorDisabled, isCSSDisabled, updateCSS, changeAlphaWarning, updatePresenter, updatePresenterProperties, getComboMode, measureBarSelected, isInputType, selectedDetailCell, updateProperties, reset, openPresenterPropertyDialog, tableSelected, textSelected, shapeSelected, borderTooltip, roundCornerMax, isRoundTopCornersOnlyVisible
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 3** (`vs-formats-pane.component.display.tl.spec.ts`)
- Methods: getFormat, getColorLabel, closeFormat, isFormatDisabled, updateFormat, getBorderLabel, isFormattingDisabled, getCSSLabel, showPresenter
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### WSPaneComponent

**Pass 1** (`ws-pane.component.interaction.tl.spec.ts`)
- Methods: worksheet, active, onSplitDrag, concatenateTables, enterParams, processNotification, cleanup, getAssemblyName, addVariable, addGrouping, editJoin, selectCompositeTable, worksheetCompositionChanged, cut, copy, paste, insertColumns, replaceColumns, openAssemblyConditionDialog, openAggregateDialog, toggleAutoUpdate, selectColumnSource, oozColumnMouseEvent, getSourceTableName, addTable, fetchTableDataCount, touchAsset, initKeyListeners, updateSelectingColumnSource, openExistingWorksheet, refreshAssembly, confirm, processWSCollectVariablesCommand, processReopenSheetCommand, processOpenWorksheetCommand, processWSInitCommand, processRefreshWorksheetCommand, processWSAddAssemblyCommand, processWSEditAssemblyCommand, processWSRefreshAssemblyCommand, processMessageCommand, processExpiredSheetCommand, processWSFocusCompositeTableCommand, processUpdateUndoStateCommand, processWSExportCommand, processSaveSheetCommand, processCloseSheetCommand, processSetWorksheetInfoCommand, processForceNotCloseWorksheetCommand, processWSLoadTableDataCountCommand, updateLoadingMask, processWSFocusAssembliesCommand, processSetVPMPrincipalCommand, processWSSetMessageLevelsCommand, processWSFinishPasteWithCutCommand, processSaveWorksheetCommand
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ws-pane.component.risk.tl.spec.ts`)
- Methods: sqlEnabled, freeFormSqlEnabled, crossJoinEnabled, showGettingStartedMessage, expressionColumnEnabled, setup, worksheetCancel, openSortColumnDialog, editQuery, openSqlQueryDialog, openTabularQueryDialog, cancelLoading, subscribeToFocus, initDragAssetColumnsListener, destroyKeyListeners, openWorksheet, processWSRemoveAssemblyCommand, processWSMoveAssembliesCommand, processWSMoveSchemaTablesCommand, processClearLoadingCommand
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`ws-pane.component.display.tl.spec.ts`)
- Methods: toggleShowColumnName, processShowLoadingMaskCommand
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VSPane

**Pass 1** (`viewsheet-pane.component.interaction.tl.spec.ts`)
- Methods: vs, active, getAssemblyName, getSnapGridStyle, getBackgroundImage, ngAfterViewInit, zoom, detectChanges, isActionEnabled, isGroupActionEnabled, isUngroupActionEnabled, isBringToFrontActionEnabled, isSendToBackActionEnabled, isSnapshot, isVSSnapshot, processSetViewsheetInfoCommand, processUpdateLayoutCommand, processVSDependencyChangedCommand, processReopenSheetCommand, processCollectParametersCommand, enterParameters, processInitGridCommand, processUpdateLayoutUndoStateCommand, processUpdateUndoStateCommand, isInZone, processCloseSheetCommand, processSaveSheetCommand, processExpiredSheetCommand, processChangeCurrentLayoutCommand, processSetRuntimeIdCommand, processAddVSObjectCommand, applyAddVSObjectCommand, processAssemblyChangedCommand, processUpdateZIndexesCommand, processRefreshVSObjectCommand, applyRefreshVSObjectCommand, refreshGroupContainerOrder, processRenameVSObjectCommand, processPopulateVSObjectTreeCommand, processRefreshBindingTreeCommand, processExportVSCommand, processMessageCommand, processProgress, processVSTrapCommand, processSetGrayedOutFieldsCommand, confirm, drop, dragenter, dragover, mousedown, onKeydown, dblClick, paste, isModalOpen, trackByFn, onAssemblyActionEvent, copyAssembly, cutAssembly, bringAssemblyToFront, bringAssemblyForward, sendAssemblyToBack, sendAssemblyBackward, assemblyResized, processAssemblyResize, updateDragRulerGuides, onSnap, clickEvent, isTargetVSPane, isTargetShape, replaceObject, layoutToolbarVisible, mobileToolbarVisible, layoutChanged, layoutName, touchAsset, openEmbeddedViewsheet, openEditPane, openWizardPane, onSelectionBox, forEach, refreshStatusByVs, openWorksheet, refreshStatusByLayout, trimComma, substring, processOpenBindingPaneCommand, popupNotifications, onMaxModeChange, openConsoleDialog, getTemplateWidth, getTemplateHeight, onKeyUp, changeSearchMode, isSearchMode, search, nextFocus, previousFocus, scrollToMatchedAssembly, flatMap, scrollToAssembly, compareObjectByPosition, getStatusForStatusBar, getStatus2ForStatusBar, getSearchString, isVisible, searchInputKeyUp, isDefaultOrgAsset, isFilterInMaxModeView
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`viewsheet-pane.component.risk.tl.spec.ts`)
- Methods: openExistingViewsheet, processClearLoadingCommand, cancelViewsheetLoading, processRemoveVSObjectCommand, removeVSObject, addConsoleMessage, deselectObjects, resetCursor, removeAssembly, assemblyMoved, processAssemblyMoved, updateRulerGuides, updateSnapGuides, updateFormats, refreshStatus, updateRulerPosition
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`viewsheet-pane.component.display.tl.spec.ts`)
- Methods: processShowLoadingMaskCommand, processSetCurrentFormatCommand, showProgressDialog, getDataSourceCSSIcon, displayPlaceholderDragElementModel, openFormatPane, getSearchResultLabel
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ViewerAppComponent

**Pass 1** (`viewer-app.component.interaction.tl.spec.ts`)
- Methods: tabsHeight, runtimeId, getAssemblyName, ngAfterViewInit, forEach, initViewsheetConnection, ngAfterContentInit, ngAfterViewChecked, active, mobileToolbarVisible, pinchZoom, getClosestScrollParent, screenSize, scrolled, checkZoom, beforeunloadHandler, onKeyUp, onToolbarButtonFocus, nextToolbarButton, previousToolbarButton, onKeyDown, getPreviousSelectableAssembly, getNextSelectableAssembly, selectAssembly, mousedown, previousURLEnable, back, previousPage, nextPage, editViewsheet, subscribe, reopenExpiredViewsheet, refreshViewsheet, zoom, zoomLayout, isZoomItemSelected, zoomOutEnabled, zoomInEnabled, isMobile, emailViewsheet, scheduleViewsheet, printViewsheet, exportViewsheet, importExcel, importExcelFile, toggleAnnotations, getBookmarks, saveCurrentBookmarkDisabled, saveBookmark, gotoBookmark, doGotoBookmark, setDefaultBookmark, editBookmark, substring, toggleFullScreen, applyFullScreen, onFullScreenChange, closeViewsheetOnServer, closeViewsheet, openConditionDialog, openHighlightDialog, createTableActionHandler, processSetRuntimeIdCommand, processCollectParametersCommand, processUpdateSharedFiltersCommand, processDelayVisibilityCommand, processAddVSObjectCommand, processRefreshVSObjectCommand, processInitGridCommand, processSetPermissionsCommand, processSetExportTypesCommand, processMessageCommand, processSetComposedDashboardCommand, processProgress, processUpdateUndoStateCommand, processAnnotationChangedCommand, isInZone, filter, processSetViewsheetInfoCommand, processUpdateZIndexesCommand, processExpiredSheetCommand, onOpenContextMenu, showAnnotationDialog, addMobileActionSubsciption, isPermissionForbidden, addAnnotation, openViewsheet0, enterParameters, registerDataTipVisible, registerPopCompVisible, setDataTipOffsets, getComponentModel, setAppSize, getScaleSize, onViewerRootResize, getVariables, updateData, getDim, assign, getMinimumToolbarWidth, notifyParentFrame, submitData, preventMouseInteractions, processOpenComposerCommand, openComposer, hasBottomPadding, hasRightPadding, shareEmail, shareFacebook, shareHangouts, shareLinkedin, shareSlack, shareTwitter, shareLink, changeMaxMode, toggleDoubleCalendar, scroll, showHints, scrollLeft, scrollTop, openProfileDialog, openViewsheetOptionDialog, hideProfilingBanner, isPreviousPageVisible, isPreviousPageDisabled, isNextPageVisible, isNextPageDisabled, isEditVisible, isRefreshViewsheetVisible, isEmailVisible, isSocialSharingVisible, isShareEmailDisabled, isShareFacebookDisabled, isShareHangoutsDisabled, isShareLinkedInDisabled, isShareSlackDisabled, isShareTwitterDisabled, isShareLinkDisabled, isScheduleVisible, isPrintViewsheetVisible, isExportVisible, isImportExcelVisible, isZoomVisible, isAnnotationButtonVisible, isHideAnnotationsVisible, bookmarksVisible, isAddBookmarkDisabled, isShareToAllDisabled, isSetDefaultBookmarkVisible, isSetDefaultBookmarkDisabled, isEditBookmarkVisible, isEditBookmarkDisabled, isToggleFullScreenVisible, isCloseViewsheetVisible, setViewerToolbarDefinitions, createBookmarkButtonDefs, push, getReloadMessage, getViewerRootHeight, setMobileToolbarActions, moreActions, allowedActionsNum, isPageControlVisible, usePagingControl, updateScrollTop, checkExportStatus, getOrgId, loadingStateChanged, isDataTipOrPopComponentVisible, getScrollViewport, updateTabPositions
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`viewer-app.component.risk.tl.spec.ts`)
- Methods: onViewerRootResizeEvent, clearSelectedAssemblies, setServerUpdateInterval, clearServerUpdateInterval, showBookmarks, isBookmarkHome, deleteBookmark, deleteBookMarks, addBookmark, fullScreenApplied, beforeDestroy, processClearLoadingCommand, processRemoveVSObjectCommand, processClearScrollCommand, removeAnnotations, openViewsheet, openPreviewViewsheet, cancelViewsheetLoading, sendBookmarkEvent, deleteBookmarkByCondition, updateScrollLeft, pagingControlModel, processEmbedErrorCommand, handleDataTipPopComponentChanges, clearDataTipPopComponents, updateScrollViewport
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`viewer-app.component.display.tl.spec.ts`)
- Methods: onOpenChartFormatPane, processSetCurrentFormatCommand, processOpenAnnotationFormatDialogCommand, showProgressDialog, processShowLoadingMaskCommand, showViewsheetContextMenu, showScrollButton, showContextMenu, showBookmarkChangedDialog, updateFormat, closeFormatPane, getCurrentFormat, isShowAnnotationsVisible, showingActions
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VSBindingPane

**Pass 1** (`vs-binding-pane.component.interaction.tl.spec.ts`)
- Methods: runtimeId, aiAssistantPermission, touchAsset, sourceName, originalMode, getAssemblyName, params, updatePresenterProperties, refreshVSBinding, isActionVisible, calcTableEditorService, isMergeCellsActionEnabled, isSplitCellsActionEnabled, processSetVSBindingModelCommand, processOpenEditGeographicCommand, processRefreshVSObjectCommand, processAddVSObjectCommand, updateObjectModel, selectWholes, processRefreshBindingTreeCommand, processMessageCommand, processProgress, isInZone, processVSTrapCommand, processSetGrayedOutFieldsCommand, processVSBindingTrapCommand, processExpiredSheetCommand, getBindingType, confirm, updateData, closeHandler, isWizardExpiredCommand, closeHandler0, processOpenObjectWizardCommand, processCloseBindingPaneCommand, processAssemblyChangedCommand, goToWizard, then, openWizardPane, goToWizardVisible, hasExpression, hasExpressionRef, isExpressionAes, isCrosstab, isChart, haveDynamicBinding, popupNotifications, onResize, resizeObjectView, processRenameVSObjectCommand, addConsoleMessage, messageChange
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`vs-binding-pane.component.risk.tl.spec.ts`)
- Methods: isActionEnabled, isDeleteRowActionEnabled, isDeleteColumnActionEnabled, handleExpiredSheet, processClearLoadingCommand, cancelViewsheetLoading
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`vs-binding-pane.component.display.tl.spec.ts`)
- Methods: formatPaneDisabled, showProgressDialog, processSetCurrentFormatCommand, getCurrentFormat, createTextFormatEvent, updateFormat, createUpdateFormatEvent, isAggregateTextFormat, processShowLoadingMaskCommand
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ImportCSVDialog

**Pass 1** (`import-csv-dialog.component.interaction.tl.spec.ts`)
- Methods: ngAfterViewChecked, setEnabled, updateFile, updateForm, showLimitMessage, handleEmptyFile, reset, unpivotEnabled, onHeaderRename, validateHeaders, toString, validateFirstRow
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`import-csv-dialog.component.risk.tl.spec.ts`)
- Methods: initForm, clearProgress, ok, cancel, initFileToucher, updatePreviewTable, parsePreviewResponse
- Reason: async≥3：竞态 / destructive / state inconsistency

### SQLQueryDialog

**Pass 1** (`sql-query-dialog.component.interaction.tl.spec.ts`)
- Methods: checkIfNotSaved, detachedCrossJoin, ok, then, apply, checkQueryValidity, onSwitchChange, refreshModelOnModeChange
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`sql-query-dialog.component.risk.tl.spec.ts`)
- Methods: advancedEditing, subQuery, createForm, dataSourceChanged, loadDataSourceTree, clearDisabled, clear, updateNameValidation, cancel, subscribe, isApplyBtnDisabled, initOperations, destroyRuntimeQuery, isJoinEditView
- Reason: async≥3：竞态 / destructive / state inconsistency

### TabularQueryDialog

**Pass 1** (`tabular-query-dialog.component.interaction.tl.spec.ts`)
- Methods: displayTitle, helpDisplayTitle, createForm, validChanged, viewChanged, nestedViewChanged, hasRefreshButton, updateNameValidation, ok, apply
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`tabular-query-dialog.component.risk.tl.spec.ts`)
- Methods: authorize, refreshView, initView, hasCancelButton, buttonClicked, clearButtonClicks, clearButtonLoading, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### GroupingDialog

**Pass 1** (`grouping-dialog.component.interaction.tl.spec.ts`)
- Methods: init, initForm, initRoot, getCurrentSelectedNode, matchNode, checkOuterMirror, nodeExpanded, onlyForDisabled, addDisabled, editDisabled, upDisabled, downDisabled, addCondition, editCondition, getTooltip, getCSSIcon, ok
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`grouping-dialog.component.risk.tl.spec.ts`)
- Methods: updateOnlyFor, deleteDisabled, deleteCondition, moveConditionUp, moveConditionDown, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### VariableAssemblyDialog

**Pass 1** (`variable-assembly-dialog.component.interaction.tl.spec.ts`)
- Methods: init, initDefaultValueType, initForm, checkOuterMirror, getDefaultStrValue, showVariableListDialog, showVariableTableListDialog, valid, embeddedValid, queryValid, selectDefaultValueType, isExpressionDefaultValue, okDisabled, saveChanges, validateVariableList, defaultExpressionValueChange, getVariableTree, getVariableTreeModel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`variable-assembly-dialog.component.risk.tl.spec.ts`)
- Methods: cancelChanges
- Reason: async≥3：竞态 / destructive / state inconsistency

### VPMPrincipalDialogComponent

**Pass 1** (`vpm-principal-dialog.component.interaction.tl.spec.ts`)
- Methods: ok, resetSessionIds, setSessionIds, resetSessionId
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`vpm-principal-dialog.component.risk.tl.spec.ts`)
- Methods: initForm, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### AssetTreeComponent

**Pass 1** (`asset-tree.component.interaction.tl.spec.ts`)
- Methods: searchMode, handleAssetChangeEvent, refreshParentNodes, refreshSelectedNodes, recursiveRefreshSelectedNodes, refreshNodeChildren, loadAll, containsLoadAssetEvent, findAssetTreeNodeFromIdentifier, findAssetTreeNodeParentFromIdentifier, nodeExpanded, selectNodes, doubleclickNode, contextmenuTreeNode, getCSSIcon, getParentNode, getNodeByData, searchStrChange, checkDSPlaceholder, searchStart, updateUseVirtualScroll, hasLoadedAllNode, getNodeByPath, getPathToEntry, addExtraTrees, revalidateExtraTrees, refreshView, getNodeEqualsFun, isTableStyle
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`asset-tree.component.risk.tl.spec.ts`)
- Methods: loadAssetTree, addDeleteDataSources, addDeleteDataSources0, setupAssetClientService, createRefreshNodeEvent, removeDSLoadingPlaceholder, removeExtraTrees
- Reason: async≥3：竞态 / destructive / state inconsistency

### RadiusDropdown

**Pass 1** (`radius-dropdown.component.interaction.tl.spec.ts`)
- Methods: radius, disabled, max, updateRadiusStatus
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`radius-dropdown.component.risk.tl.spec.ts`)
- Methods: initForm
- Reason: async≥3：竞态 / destructive / state inconsistency

### WSAssemblyGraphPaneComponent

**Pass 1** (`ws-assembly-graph-pane.component.interaction.tl.spec.ts`)
- Methods: bind, isJoinWithExpr, ngAfterContentInit, ngAfterViewChecked, oozKeyDown, onKeyUp, getAssemblyName, cleanup, setContainer, trackByFn, getThumbnailClasses, selectAssembly, clickAssembly, onSelectionBox, updateLastClick, allowDrop, drop, openAssets, handleDragColumnsEvent, subscribe, cutAssembly, copyAssembly, toggleAutoUpdate, tableEndpoints, registerAssembly, startEditName, editName, connectAssemblies, createConnection, refreshAssembly, setDraggable, oozScroll, toggleEndpoints, each, hideEndpoints, addEndpoint, notify, checkJoinCompatibility, checkConcatCompatibility, processConcatCompatibilityCommand, refreshDragSelection, setRepaintTimer, openContextMenu, paste, setDimmedTypes, dimConnection, undimConnection, windowMouseup, cleanupMouseupListener, sendDragPasteEvent, isArrowKey, isWorksheetEmpty, selectDependent
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ws-assembly-graph-pane.component.risk.tl.spec.ts`)
- Methods: clearSelection, selectCompositeTable, removeFocusedAssemblies, removeAssemblies, then, destroyAssembly, causeConcatRejection, join, concat, forEach, dragPasteAssemblies, moveAssemblies, subscribeToFocus, refreshColumnSourceConnectionTypes, dimUnfocusedAssemblies, windowMousemove, arrowKeyMove, getArrowKeyMoveFactor, arrowKeyMoveEnd
- Reason: async≥3：竞态 / destructive / state inconsistency

### WSDetailsPaneComponent

**Pass 1** (`ws-details-pane.component.interaction.tl.spec.ts`)
- Methods: searchQuery, replace, headerNameChanged, populateTableModeButtons, getTableStatus, openAggregateDialog, openFormulaEditorDialog, openConditionDialog, openShowHideColumnsDialog, isSupportChangeColumnOrder, openReorderColumnsDialog, toggleSearchBar, checkEnterKey, searchPrevious, searchNext, onSearchResultUpdate, openImportCSVDialog, openChangeColumnTypeDialog, openChangeColumnDesDialog, getValueRangeParams, validateValueRangeModel, changeTableMode, setRuntime, setShowName, getTableModeButton, exportTable, toggleMirrorAutoUpdate, submitExpressionCallback, _submitColumnTypeCallback, runQuery, formatAll, openConsoleDialog, isTableButtonVisible, isTableButtonToggled, toggleWrapColumnHeaders, isWrapColumnHeadersEnabled
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ws-details-pane.component.risk.tl.spec.ts`)
- Methods: dataModeEnabled, updateRowRange, selectColumnSource, oozColumnMouseEvent, openDateRangeOptionDialog, openNumericRangeOptionDialog, openValueRangeDialog, clearSearch, cancelQuery
- Reason: async≥3：竞态 / destructive / state inconsistency

### EditableObjectContainer

**Pass 1** (`editable-object-container.component.interaction.tl.spec.ts`)
- Methods: vsObjectModel, calculateZIndex, selectionChild, selectionChildModelJson, stringify, lineModel, getTopPosition, getMinHeight, searchDisplayed, visible, isSearchResults, searchMode, isSearchFocus, getVariableValues, updateDropTarget, ngAfterViewInit, assemblySelected, onDragStart, onDragEnd, isOutside, onResizeStart, onResizeEnd, onDropzoneEnter, getAssembly, onDropzoneLeave, onDropzoneDrop, draggingCloseEnough, onLineDragEnd, onLineStartDragEnd, snapLine, onLineEndDragEnd, onLineDragBegin, preventMouseEvents, isShape, isFormAssembly, isFadeAssembly, isPreventResize, isPreventDrag, isViewsheet, isLocked, hasScript, conditionAction, hasSlideout, isDragBorderTop, isDragBorderBottom, isDragBorderAll, isDragBorder, isInteractDropZone, contextMenuOpen, select, onEnter, onLeave, enableInteraction, disableInteraction, drop, turnOnEditMode, onKeyDown, isCalcDroppable, isMiniToolbarVisible, miniToolbarWidth, hasMiniToolbar, isCalcDroppableType, isEnterEnabled, isDataRefAccepted, openEmbeddedViewsheet, changeMinHeightFromAutoText, openLayoutOptionDialog, endAnchorDrag, openEditPane, openWizardPane, hasMultipleSelectRegions, getLineRotationAngle, getLineLength, updateFocus, resizeObject, setMovingResizing, goToWizardVisible, clickEditButton, onMaxModeChange, onDetectViewChange, onMouseEnter, contextMenuClose, toolbarForceHidden
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`editable-object-container.component.risk.tl.spec.ts`)
- Methods: getMoveHandle, getMovedPosition, onDragMove, removeCopies, onResizeMove, onLineDragMove, onLineStartDragMove, onLineEndDragMove
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`editable-object-container.component.display.tl.spec.ts`)
- Methods: showSlideout, showLayoutOptionDialog, onTitleResizeMove, onTitleResizeEnd, popupShowing, showEdit
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ComposerSelectionContainerChildren

**Pass 1** (`composer-selection-container-children.component.interaction.tl.spec.ts`)
- Methods: vsObject, getObjectTop, setChildrenHeight, onEnter, onLeave, onContainerDragOver, isDragAcceptable, onDragOver, drop, isShape, openLayoutOptionDialog, select, childChanged, resizeAssembly, childWithBorder, processChangeVSSelectionTitleCommand, getBodyWidth, getPaddingHeight, getInnerWidth, droppedOnChild, isSelected, zIndex
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`composer-selection-container-children.component.risk.tl.spec.ts`)
- Methods: moveAssembly
- Reason: async≥3：竞态 / destructive / state inconsistency

### LayoutPane

**Pass 1** (`layout-pane.component.interaction.tl.spec.ts`)
- Methods: onLayoutResize, guideSize, getAssemblyName, getPrintBounds, getPLayoutSize, getSnapGridStyle, getBackgroundImage, drop, onSelectionBox, getLayoutObjects, onSnap, getNextObjectName, trackByFn, processAddLayoutObjectCommand, processRefreshLayoutObjectsCommand, updateObjectList, getGuideSize, getLayoutObjectSize, updateDimensions, processUpdateLayoutUndoStateCommand, mousedown, updateDimensions0, scrolled, assemblyResized, processAssemblyResize
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`layout-pane.component.risk.tl.spec.ts`)
- Methods: model, sizeGuidesVisible, refreshViewsheet, getLayoutSize, filter, print, processRemoveLayoutObjectsCommand, removeSelectedAssemblies, removeLayoutObject, moveObject, updateSnapGuides
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`layout-pane.component.display.tl.spec.ts`)
- Methods: showContent, showHeader, showFooter, processSetCurrentFormatCommand
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VSObjectContainer

**Pass 1** (`vs-object-container.component.interaction.tl.spec.ts`)
- Methods: keyNavigation, scrollViewport, ngAfterViewInit, isAssemblyVisible, isFilterInMaxModeView, isMiniToolbarVisible, toolbarForceHidden, trackByName, viewer, select, isSelected, isAtBottom, submitClicked, onMaxModeChange, isMaxModeHidden, isFocused, getAssemblyAsClass, getAssemblyDivId, getToolbarTop, getActionsWidth, getToolbarLeft, getToolbarWidth, checkContainerHasVerticalScrollbar, openWizardPane, isActivePopComponent, needsZIndexBoost, getPopUpContentBoostZIndex, zIndex, getVsObjectPosition, getSelectionBodyHeight, getSelectionCellHeight, max, getPopDimZIndex, drawPopDim, getPopDimWidth, getPopDimHeight, getActualWidth, onMouseEnter, getChartDataAnnotations, annotationMouseSelect, isChartAnnotationSelected, getChartAnnotationTetherTo, getChartAnnotationRestrictTo, isObjectRendered, updateRendered, isRectInitialized, isInScrollViewport
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`vs-object-container.component.risk.tl.spec.ts`)
- Methods: getVariablesValues, removeAnnotationFromOverlay
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`vs-object-container.component.display.tl.spec.ts`)
- Methods: showContextMenu, popupShowing, isPopupShowing, showToolbar, showingPopUpOrDataTip
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VsWizardPane

**Pass 1** (`vs-wizard-pane.component.interaction.tl.spec.ts`)
- Methods: gridRowCount, ngAfterViewInit, getVScrollWidth, runtimeId, linkUri, resizeListener, refreshGridRows, setKeydownListener, undoEnabled, redoEnabled, onKeydown, getAssemblyName, trackByFn, close, editWizardObject, changeFollowDirection, isFollow, openImageExplore, processSetWizardGridCommand, fileChanged, replaceObject, changeRows, changeCols, dragResizeStart, dragResizeEnd, mergeDimension, prepareBottomFollowAssemblies, prepareFollowRestriction, prepareRightFollowAssemblies, sortByXPosition, sortByYPosition, insertObject, changeNewObject, mouseOnWizardObject, getBottomRight, resizeObject, onSelectionBox, isTempAssembly, processUploadImageCommand, processAddVSObjectCommand, processRefreshVSObjectCommand, refreshVSObject, processUpdateUndoStateCommand, getFollowDirSrc, hiddenNewBlockChanged, keyDown
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`vs-wizard-pane.component.risk.tl.spec.ts`)
- Methods: removeKeydownListener, moveable, removeWizardObject, remove, processAssemblyMoved, moveWizardObject, moveAssembly, clearFocused, shouldClearFocused, processRemoveVSObjectCommand
- Reason: async≥3：竞态 / destructive / state inconsistency

### ObjectWizardPane

**Pass 1** (`object-wizard-pane.component.interaction.tl.spec.ts`)
- Methods: getAssemblyName, chartBindingModel, tableBindingModel, filterBindingModel, sourceName, assemblyType, autoOrder, showAutoOrder, assemblyName, availableFields, dimensions, measures, isDetail, isAssemblyBinding, startsWith, isFullEditorVisible, goToFullEditor, close, processTableWarningCommand, processRefreshRecommendCommand, processCloseObjectWizardCommand, processSetVSBindingModelCommand, processRefreshVsWizardBindingCommand, processSetWizardBindingFormatCommand, processRefreshVSObjectCommand, processAddVSObjectCommand, setVSObjectModel, updateVSObjectModel, isLatestTempAssembly, updateFormat, showLegend, onEditAggregate, onEditDimension, onUpdateDetails, onAddAggregate, onAddDimension, onAutoOrderChange, isRefInMeasures, isFullRefInMeasures, isRefInDimension, isFullRefInDimension, isRefInDetails, sendRefreshWizardBindingEvent, treePaneCollapsed, toggleRepositoryTreePane, isSupportFullEditor, isSelectionTree, changeSubtype, processShowRecommendLoadingCommand, processFireRecommandCommand, eventLoading, processMessageCommand, addConsoleMessage, processProgress, showProgressDialog, switchToMeta, processSwitchToMetaModeCommand, fixedFormulaMap, splitPaneDragEnd, messageChange
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`object-wizard-pane.component.risk.tl.spec.ts`)
- Methods: aiAssistantPermission, processRemoveVSObjectCommand, onEditSecondColumn, onDeleteAggregate, onDeleteDimension, processClearRecommendLoadingCommand
- Reason: async≥3：竞态 / destructive / state inconsistency

### CodemirrorComponent

**Pass 1** (`codemirror.component.interaction.tl.spec.ts`)
- Methods: sql, scriptDefinitions, ngAfterViewInit, setAnalysisResults, initCodeMirror, checkSyntax, docTooltip, renderAnalysisResults
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`codemirror.component.risk.tl.spec.ts`)
- Methods: analysisResults, expression, cursor, functionTreeRoot, ngAfterViewChecked, triggerSyntaxAnalyzer, function, destroyCodeMirror, isEditorElementDisplayed
- Reason: async≥3：竞态 / destructive / state inconsistency

---

> **以下为 2026-06-24 补充扫描新增组件（30 个）**
> 来源：系统性缺口分析，composer 路由下高频使用的 widget/ 共享组件（条件编辑器、公式/脚本、SQL 查询、日期编辑器等）及 format/ 边框组件。
> `logic_lines / dispatch / async_zones` 均为 `—`（待 prescan workflow 精确扫描）。

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| 待审核 | FormulaEditorDialog | 1105 | 4 | 9 | **multi-pass** | ✅ 推进 | ⚠️ formula-editor-dialog.component.spec.ts | Covers SQL-type warning dialogs, formula name character validation, duplicate-name guard in ok(), and 4 expressionChange node cases; does NOT cover isCycle/checkExpression cycle detection, showAggregateDialog modal flow, deleteAggregate, all HTTP subscribe paths in populateTrees, ngOnDestroy cleanup, validExpression getter, or getGrayedOutValues branching. | P1: FormulaEditorDialog.interaction.tl.spec.ts<br>P2: FormulaEditorDialog.risk.tl.spec.ts<br>P3: FormulaEditorDialog.display.tl.spec.ts |
| 待审核 | ScriptPane | 632 | 3 | 6 | **multi-pass** | ✅ 推进 | ⚠️ script-pane.component.spec.ts | Only 1 trivial smoke test (null defs init); all functional paths (itemClicked, getCSSIcon, insertText, isGrayedOutField, blockKeys, async subscribe flows, cursor guards, analysis results, destroyCodeMirror) are uncovered. | P1: script-pane.component.interaction.tl.spec.ts<br>P2: script-pane.component.risk.tl.spec.ts<br>P3: script-pane.component.display.tl.spec.ts |
| 待审核 | VSAssemblyScriptPane | 139 | 1 | 0 | **single-pass** | ✅ 推进 | ⚠️ vsassembly-script-pane.spec.ts | Covers only the data.name==="field" branch of onExpressionChange (1 of 8+ branches); component/parameter/COLUMN-TABLE/highlighted/axis-legend/colorLegend-title-axis/component-with-space branches and both private helpers are untested. | P1: vsassembly-script-pane.interaction.tl.spec.ts |
| 待审核 | SimpleQueryPaneComponent | 537 | 3 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: simple-query-pane.component.interaction.tl.spec.ts<br>P2: simple-query-pane.component.risk.tl.spec.ts<br>P3: simple-query-pane.component.display.tl.spec.ts |
| 待审核 | SQLQueryJoinDialog | 208 | 1 | 5 | **single-pass** | ✅ 推进 |  |  | P1: sql-query-join-dialog.component.interaction.tl.spec.ts |
| 待审核 | SQLQueryDialogListComponent | 160 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | AdditionalTableSelectionPaneComponent | 179 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | P1: AdditionalTableSelectionPaneComponent.interaction.tl.spec.ts |
| 待审核 | ConditionItemPane | 351 | 3 | 1 | **multi-pass** | ✅ 推进 |  |  | P1: ConditionItemPane.interaction.tl.spec.ts<br>P3: ConditionItemPane.display.tl.spec.ts |
| 待审核 | ConditionPane | 320 | 3 | 0 | **multi-pass** | ✅ 推进 | ⚠️ condition-pane.component.spec.ts | Covers only 4 cases (clear, delete, indent, insert-field-required-warning); leaves untested: modify, save/saveOption return values, up/down swap logic, canMoveUp/Down/Unindent guards, expressionRenamed field remapping, conditionItemSelected junction propagation, updateDirtyJunction emit, conditionList setter edge cases, availableFields setter field-exist check. | P1: condition-pane.interaction.tl.spec.ts<br>P3: condition-pane.display.tl.spec.ts |
| 待审核 | ConditionFieldComboComponent | 269 | 1 | 1 | **single-pass** | ⏭ 跳过 | ⚠️ condition-field-combo.spec.ts | getTooltip() 4-branch classType dispatch (GroupRef/AggregateRef/ColumnRef/else) and the startSearch()/closeSearch() search flow with setTimeout focus are not covered by existing tests. | single pass |
| 待审核 | ConditionEditor | 139 | 3 | 0 | **multi-pass** | ✅ 推进 |  |  | P1: condition-editor.component.interaction.tl.spec.ts<br>P3: condition-editor.component.display.tl.spec.ts |
| 待审核 | ValueEditor | 165 | 1 | 1 | **single-pass** | ✅ 推进 | ⚠️ value-editor.spec.ts | Covers template rendering by type and isBrowseEnabled CalculateRef guard only; does not test browseData() subscribe flow, getBrowseDataList() label mapping, selectValues() toggle, isSelected() date-transform matching, or ngOnChanges default date/boolean emission. | single pass |
| 待审核 | OneOfConditionEditor | 185 | 2 | 0 | **single-pass** | ✅ 推进 | ⚠️ one-of-condition-editor.component.spec.ts | Only one test (Bug #18994): delete button disabled after removing all items via DOM clicks; add(), modify(), valueChanged() special-type propagation, initValue() branching, and multi-select (ctrl/shift) are entirely untested. | single pass |
| 待审核 | SubqueryDialog | 111 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ subquery-dialog.component.spec.ts | Covers only Bug #9968 regression (single currentTable entry with empty columns does not crash); ngOnInit else-branch, changeSelectedTable, isValid permutations, ok/cancel emits, getTooltip branching, and dataRefsEqual are all untested. | single pass |
| 待审核 | ExpressionEditor | 127 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | HighlightPane | 148 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DateValueEditorComponent | 155 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | DynamicValueEditorComponent | 156 | 4 | 0 | **multi-pass** | ✅ 推进 |  |  | P1: dynamic-value-editor.interaction.tl.spec.ts<br>P3: dynamic-value-editor.display.tl.spec.ts |
| 待审核 | AutoCompleteText | 269 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ImagePreviewPane | 190 | 2 | 4 | **single-pass** | ✅ 推进 | ⚠️ image-preview-pane.spec.ts | Covers upload HTTP URL, alpha opacity rendering, layoutObject disabling animate-GIF checkbox, and clear button reset — but omits deleteUpload() confirm+HTTP-delete+tree mutation, selectImage() current-type branching, imageSrc getter fallback to emptyimage.gif, and initCurrentNode() dynamic-image ($,=) path. | single pass |
| 待审核 | VariableListEditor | 79 | 1 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ variable-list-editor.component.spec.ts | swap() and clear() not tested; DATE placeholder branch and CHARACTER maxlength omitted. | single pass |
| 待审核 | SimpleTableComponent | 109 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | FixedDropdownComponent | 165 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | SlideOutComponent | 113 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | BandPanel | 52 | 0 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ band-panel.component.spec.ts | Uses fragile ng-reflect-is-disabled attribute assertions and a DOM query for .entriChart-cb_id CSS class; field-filtering test checks rendered option count which is non-trivially derivable from source alone. | single pass |
| 待审核 | StatPanel | 38 | 0 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ stat-panel.component.spec.ts | ngOnInit auto-selection of first non-empty-label field into model.measure is not explicitly asserted, and changeAlphaWarning is untested; otherwise core paths covered. | single pass |
| 待审核 | ColorMap | 42 | 0 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ color-map.component.spec.ts | Covers all computed properties and both EventEmitter outputs via a 22-entry color table; no gaps notable. | single pass |
| 待审核 | FormatPresenterPane | 43 | 0 | 1 | **single-pass** | ⏭ 跳过 | ⚠️ format-presenter-pane.component.spec.ts | selectPresenter() emitter and getIcon() leaf/non-leaf paths are untested; only isPresenterDialogEnabled() and init mock are covered. | single pass |
| 待审核 | Ruler | 145 | 1 | 0 | **single-pass** | ✅ 推进 | ⚠️ ruler.component.spec.ts | Covers component creation and horizontal/vertical orientation positional styles only; guide styles (guideTopStyle/guideLeftStyle/guideWidthStyle/guideHeightStyle), scale setter normalization, updateRulerSize canvas tick-drawing paths, and the offsetParent===null early-exit guard are all untested. | single pass |
| 待审核 | BorderStylePane | 144 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |

---

## 2026-06-24 补充扫描 Multi-Pass 详情（7 个）

### FormulaEditorDialog

**Pass 1** (`FormulaEditorDialog.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, ngAfterViewInit, initForm, ok, cancel, showAggregateDialog, deleteAggregate, showContextMenu, createActions, isCycle, checkExpression, isDuplicateFormulaName, isDuplicateName
- Reason: User-triggered flows, lifecycle hooks, modal open/result handling, and destructive aggregate delete

**Pass 2** (`FormulaEditorDialog.risk.tl.spec.ts`)
- Methods: populateColumnTree (subscribe), populateFunctionTree (subscribe), populateOperatorTree (subscribe), populateScriptDefinitions (dual subscribe), initForm calcType.valueChanges subscribe, initForm formulaType.valueChanges forEach, ngAfterViewInit Promise.resolve microtask, showAggregateDialog modal result.then, subscriptions.unsubscribe on destroy
- Reason: 9 asyncZones require dedicated race/state-consistency testing: concurrent HTTP responses, valueChanges side-effects, modal promise resolution, and subscription teardown

**Pass 3** (`FormulaEditorDialog.display.tl.spec.ts`)
- Methods: expressionChange (all 15+ scriptData.data.name branches), checkValid (aggregateOnly vs sqlMergeable paths), populateColumnTree branch logic (isCube/vsId/isSqlType), populateFunctionTree (isCube/isSqlType/script branches), populateOperatorTree (isSqlType||isCube vs else), getGrayedOutValues (isModel vs attribute), validExpression, validFunctionRoot, title getter, aggregateOnly getter, scriptDefinitions getter, isSqlType, getFullName, getAggrExpression, hasMenu
- Reason: 4 dispatchPoints with 15+ branches in expressionChange alone, plus pure conditional label/expression-building methods covering all formula type and node-type permutations

### ScriptPane

**Pass 1** (`script-pane.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, ngOnChanges, ngAfterViewInit, itemClicked, onKeyUp, blockKeys, rightClick, expression setter, scriptDefinitions setter, sql setter, functionTreeRoot setter, operatorTreeRoot setter, cursor setter
- Reason: Lifecycle hooks, user-triggered flows, and input setters that wire together the CodeMirror instance and emit events

**Pass 2** (`script-pane.component.risk.tl.spec.ts`)
- Methods: subscribe callbacks in ngOnInit (helpURL, isCursorTop), setTimeout in ngAfterViewInit/ngAfterViewChecked, delayAutocomplete (cancel-and-restart race, clearTimeout), applyCursorPosition (cursorTopLoaded/cursorPositionApplied guard flags), destroyCodeMirror (ternServer.destroy, toTextArea, cancelAutocomplete cleanup)
- Reason: asyncZones=6 creates race-prone paths: competing subscribe+setTimeout sequences, debounce cancel races, and one-shot guard flags that misfire if ordering changes

**Pass 3** (`script-pane.component.display.tl.spec.ts`)
- Methods: getCSSIcon (6-branch if-else on node.data), functionOperatorTreeRoot getter (combined/single-root paths), expressionMissing getter, returnError getter, isGrayedOutField, ScriptPane.insertText (static, single-line and multi-line selection cases)
- Reason: dispatchPoints=3; pure display/computation methods with multiple branches that need dedicated coverage without CodeMirror side-effects

### SimpleQueryPaneComponent

**Pass 1** (`simple-query-pane.component.interaction.tl.spec.ts`)
- Methods: model setter (columnCache init, updateNumTables), nodeExpanded, columnsChange, deleteColumn, joinsChange, deleteJoin, newJoin, editJoin, getSQLString, getSqlParseResult, editSQLDirectly, nodeClicked, editConditions (dialog open/close), updateQueryTab (tab switching), goBackToPreviousTab
- Reason: Router/HTTP loading flows, ngOnInit-equivalent model setter, user-triggered dialog and tab flows

**Pass 2** (`simple-query-pane.component.risk.tl.spec.ts`)
- Methods: editConditions observable chain ordering (positions[] splice logic), droppedIntoColumnList (tableColumnsObs subscribe + complete handler), getTableColumns (observableConcat ordering), updateQueryTab HTTP error branch, columnCache race between multiple table subscriptions
- Reason: asyncZones=7 — async races, ordering invariants in observable chains, destructive state mutations under concurrent subscriptions

**Pass 3** (`simple-query-pane.component.display.tl.spec.ts`)
- Methods: addColumns (oldIndex < 0 / < index / > index / == index branches), deleteConditions (value1/value2/value3 table matching), joinToString, columnToString, isParseFailed, textChanged, supportsFullOuterJoin getter, setUpConditionDialogModel, iconFunction, tableCount getter
- Reason: dispatchPoints=3 — pure conditional/label-computation logic with 3+ branching paths needing coverage

### ConditionItemPane

**Pass 1** (`ConditionItemPane.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges, fieldChanged, operationChanged, onConditionValueChange, conditionChanged, updateCondition, openFormulaEdit (getColumnTree subscribe + dialogService.open modal result), getData/getVariables/getColumnTree/getScriptDefinitions provider delegation, fields setter (tree-building from DataRef array)
- Reason: Covers lifecycle hooks, user-triggered mutations, the single async subscribe inside openFormulaEdit, and the modal result promise chain

**Pass 3** (`ConditionItemPane.display.tl.spec.ts`)
- Methods: showUseList getter (3 branching conditions: DATE_IN, ColumnRef/DateRangeRef, isHighlight+isDateRange), isDateRange (6 isGroupRef name checks + dateType+grouped branch), getDefaultConditionValues switch (7 operation cases + TOP_N/BOTTOM_N guard), getDefaultConditionValue (string vs non-string type), formulaExpression getter, formulaType getter (isReportWorksheetSource path), isFormulaField, isReportWorksheetSource, getGrayedOutFields, isBrowseDataEnabled
- Reason: dispatchPoints=3 warrants a dedicated pass for pure conditional/label logic: showUseList branching, isDateRange groupRef pattern matching, and the operation-to-default-values switch

### ConditionPane

**Pass 1** (`condition-pane.interaction.tl.spec.ts`)
- Methods: ngOnInit, conditionList setter (all branches), availableFields setter, condition setter, selectCondition, conditionItemSelected, updateDirtyJunction, insert, modify, save, saveOption, delete, clear, up, down, indent, unindent, expressionRenamed
- Reason: User-triggered mutation flows, lifecycle initialization, and all EventEmitter outputs — the primary behavioral surface of the component

**Pass 3** (`condition-pane.display.tl.spec.ts`)
- Methods: canMoveUp, canMoveDown, canIndent, canUnindent, listPaneHeight, buttonText, emptyCondition, isConditionValid
- Reason: Pure conditional/guard computations driven by selectedIndex and conditionList state — dispatchPoints threshold met; these are display/predicate methods with no async or side effects

### ConditionEditor

**Pass 1** (`condition-editor.component.interaction.tl.spec.ts`)
- Methods: ngOnChanges, openChange, selectType, valueChanged, conditionValueChanged, conditionValuesChanged, updateChoiceQuery, closeDropDown, getSelectValues
- Reason: Covers all user-triggered flows, lifecycle hooks, and output emissions that require component rendering and interaction

**Pass 3** (`condition-editor.component.display.tl.spec.ts`)
- Methods: selectType (5-branch type-to-default-value dispatch), getChoiceQuery (3-branch MODEL/ASSET/other source.type dispatch), updateChoiceQuery (3-branch useList+source.type dispatch)
- Reason: Three dispatch points drive complex conditional label/value computation; pure branch coverage for each path without async concerns

### DynamicValueEditorComponent

**Pass 1** (`dynamic-value-editor.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges (today/forceToDefault/defaultValue branches), updateValue, updateType, dateChange, isCalendarVisible, isCalendarDisable, getType
- Reason: Covers lifecycle initialization (date fallback logic), input-driven change handling, user-triggered value/type/date mutations, and calendar visibility/disable state driven by valueModel.type and XSchema.isDateType

**Pass 3** (`dynamic-value-editor.display.tl.spec.ts`)
- Methods: getDateValueTypeNumber, getDateValueTypeStr, getPromptString, format (getter), mode (getter), isDate (getter)
- Reason: Covers the four 3+-branch dispatch methods: ComboMode↔ValueTypes bidirectional mapping, prompt-string selection by XSchema type, format string selection (DATE/TIME/TIME_INSTANT), and numeric/text mode computation
