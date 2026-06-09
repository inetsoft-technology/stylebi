# composer Route Pre-scan Report

**日期**: 2026-06-08
**候选组件数**: 80 | **建议推进**: 78 | **建议跳过**: 2 | **多 pass 组件**: 25

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
| ✅已测试 | ComposerAppComponent | 93 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| ✅已测试 | AiAssistantPanelComponent | 177 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | DownloadTargetComponent | 84 | 1 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| ✅已测试 P1P2P3 | ComposerMainComponent | 2512 | 8 | 34 | **multi-pass** | ✅ 推进 | ⚠️ composer-main.spec.ts | Bug #16301 set embeddedId set if opening an embedded vs; Bug #18803 should disable format pane when select device layout; Bug #18805 should enable for | P1: composer-main.component.interaction.tl.spec.ts ✅<br>P2: composer-main.component.risk.tl.spec.ts ✅<br>P3: composer-main.component.display.tl.spec.ts ✅ |
| 待审核 | ComposerToolbarComponent | 1746 | 8 | 11 | **multi-pass** | ✅ 推进 | ⚠️ composer-toolbar.component.spec.ts | BUg #21103 should not show preview button on worksheet; Bug #17208 enable layout align when select multi object on layouts; Bug #16940 disbale move mo | P1: ComposerToolbarComponent.interaction.tl.spec.ts<br>P2: ComposerToolbarComponent.risk.tl.spec.ts<br>P3: ComposerToolbarComponent.display.tl.spec.ts |
| 待审核 | SplitPane | 120 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | AssetTreePane | 843 | 1 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: AssetTreePane.interaction.tl.spec.ts<br>P2: AssetTreePane.risk.tl.spec.ts |
| 待审核 | ToolboxPane | 144 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ScriptTreePane | 74 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ComponentsPane | 302 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | StyleTreePane | 97 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | VSFormatsPane | 679 | 4 | 1 | **multi-pass** | ✅ 推进 | ⚠️ vs-formats-pane.spec.ts | Bug #16685, Bug #16689 check the aligment combox status; Bug #18597, BUg #18664 color,border, aligment status; Bug #18060, Bug #18342 for wrap text on | P1: VSFormatsPane.interaction.tl.spec.ts<br>P3: VSFormatsPane.display.tl.spec.ts |
| 待审核 | WSCompositeTableSidebarPane | 77 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | WSPaneComponent | 944 | 5 | 6 | **multi-pass** | ✅ 推进 |  |  | P1: WSPaneComponent.interaction.tl.spec.ts<br>P2: WSPaneComponent.risk.tl.spec.ts<br>P3: WSPaneComponent.display.tl.spec.ts |
| 待审核 | VSPane | 2071 | 7 | 23 | **multi-pass** | ✅ 推进 | ⚠️ viewsheet-pane.component.spec.ts | Bug #10442 make sure to update send to back/front enabled after adding vs object to vs; Bug #16274 make sure to update send to back/front enabled afte | P1: VSPane.interaction.tl.spec.ts<br>P2: VSPane.risk.tl.spec.ts<br>P3: VSPane.display.tl.spec.ts |
| 待审核 | ViewerAppComponent | 3363 | 10 | 53 | **multi-pass** | ✅ 推进 | ⚠️ viewer-app.spec.ts | Bug #16456 TODO, logica changed, can not get fixed dropdown pane; Bug #19176 hide full screen in preview; Bug #16961 should refresh scale to screen vs | P1: ViewerAppComponent.interaction.tl.spec.ts<br>P2: ViewerAppComponent.risk.tl.spec.ts<br>P3: ViewerAppComponent.display.tl.spec.ts |
| 待审核 | ScriptEditPaneComponent | 194 | 2 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | StylePaneComponent | 89 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ComposerEmptyEditor | 95 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | SheetTabSelectorComponent | 87 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | VSBindingPane | 805 | 4 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: VSBindingPane.interaction.tl.spec.ts<br>P2: VSBindingPane.risk.tl.spec.ts<br>P3: VSBindingPane.display.tl.spec.ts |
| 待审核 | VsWizardComponent | 371 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | NotificationsComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ notifications.component.spec.ts | 无 | single pass (+内存泄漏) |
| 待审核 | SaveViewsheetDialog | 95 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ save-viewsheet-dialog.component.spec.ts | Bug #20421 check name for viewsheet | single pass (+内存泄漏) |
| 待审核 | SaveTableStyleDialog | 96 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | SaveWorksheetDialog | 89 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | SaveScriptDialog | 95 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | EditCustomPatternsDialog | 86 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ViewsheetPropertyDialog | 112 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ToolbarGroup | 84 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ImportCSVDialog | 423 | 1 | 33 | **multi-pass** | ✅ 推进 | ⚠️ import-csv-dialog.component.spec.ts | test: should throw an error on empty file | P1: ImportCSVDialog.interaction.tl.spec.ts<br>P2: ImportCSVDialog.risk.tl.spec.ts |
| 待审核 | SQLQueryDialog | 386 | 0 | 10 | **multi-pass** | ✅ 推进 |  |  | P1: SQLQueryDialog.interaction.tl.spec.ts<br>P2: SQLQueryDialog.risk.tl.spec.ts |
| 待审核 | TabularQueryDialog | 374 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: TabularQueryDialog.interaction.tl.spec.ts<br>P2: TabularQueryDialog.risk.tl.spec.ts |
| 待审核 | GroupingDialog | 346 | 0 | 52 | **multi-pass** | ✅ 推进 |  |  | P1: GroupingDialog.interaction.tl.spec.ts<br>P2: GroupingDialog.risk.tl.spec.ts |
| 待审核 | SelectDataSourceDialog | 46 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass |
| 待审核 | EmbeddedTableDialog | 49 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ embedded-table-dialog.spec.ts | test: should not allow non-positive number of rows or columns; test: should not allow duplicate names | single pass (+内存泄漏) |
| 待审核 | VariableAssemblyDialog | 334 | 0 | 26 | **multi-pass** | ✅ 推进 | ⚠️ variable-assembly-dialog.spec.ts | Bug #20319 should allow some characters for variable | P1: VariableAssemblyDialog.interaction.tl.spec.ts<br>P2: VariableAssemblyDialog.risk.tl.spec.ts |
| 待审核 | VariableInputDialog | 214 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ variable-input-dialog.spec.ts | Bug #16824 Make sure the default value of a boolean type is false | single pass |
| 待审核 | VPMPrincipalDialogComponent | 84 | 0 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: VPMPrincipalDialogComponent.interaction.tl.spec.ts<br>P2: VPMPrincipalDialogComponent.risk.tl.spec.ts |
| 待审核 | AssetTreeComponent | 705 | 2 | 8 | **multi-pass** | ✅ 推进 | ⚠️ asset-tree.component.spec.ts | Bug 10264 make sure asset tree node remain expanded. | P1: AssetTreeComponent.interaction.tl.spec.ts<br>P2: AssetTreeComponent.risk.tl.spec.ts |
| 待审核 | FontPane | 129 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ font-pane.spec.ts | for Bug #19781 | single pass (+内存泄漏) |
| 待审核 | DynamicComboBox | 323 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ dynamic-combo-box.spec.ts | Bug #17341  and Bug #17765 disbale variable when no variable list and can not select disabled variable irem; Bug #17341; Bug #17765 | single pass (+内存泄漏) |
| 待审核 | AlphaDropdown | 42 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ alpha-dropdown.component.spec.ts | Bug #19399 should keep alpha value | single pass |
| 待审核 | BindingBorderPane | 331 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ binding-border-pane.spec.ts | Bug #10699 and Bug #17594 make sure when setting border style and color to default; test: should set borders to null when default borders selected | single pass |
| 待审核 | RadiusDropdown | 74 | 0 | 10 | **multi-pass** | ✅ 推进 |  |  | P1: RadiusDropdown.interaction.tl.spec.ts<br>P2: RadiusDropdown.risk.tl.spec.ts |
| 待审核 | FormattingPane | 185 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ formatting-pane.spec.ts | test: empty decimal format should increase decimal | single pass |
| 待审核 | FormatCSSPane | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | PresenterPropertyDialog | 74 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | WSAssemblyGraphPaneComponent | 1080 | 2 | 20 | **multi-pass** | ✅ 推进 | ⚠️ ws-assembly-graph-pane.spec.ts | test: should not open a confirm dialog for a non-primary assembly | P1: WSAssemblyGraphPaneComponent.interaction.tl.spec.ts<br>P2: WSAssemblyGraphPaneComponent.risk.tl.spec.ts |
| 待审核 | WSCompositeTableFocusPaneComponent | 89 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | WSDetailsPaneComponent | 727 | 1 | 3 | **multi-pass** | ✅ 推进 |  |  | P1: WSDetailsPaneComponent.interaction.tl.spec.ts<br>P2: WSDetailsPaneComponent.risk.tl.spec.ts |
| 待审核 | VSLoadingDisplay | 71 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | EditableObjectContainer | 1592 | 10 | 4 | **multi-pass** | ✅ 推进 | ⚠️ editable-object-container.component.spec.ts | for Bug #9947; Bug #9817 ensure that when object is dropped the layout dialog is opened; Bug #19077 drag detail calcfield to form object the layout di | P1: EditableObjectContainer.interaction.tl.spec.ts<br>P2: EditableObjectContainer.risk.tl.spec.ts<br>P3: EditableObjectContainer.display.tl.spec.ts |
| 待审核 | ComposerSelectionContainerChildren | 512 | 1 | 4 | **multi-pass** | ✅ 推进 |  |  | P1: ComposerSelectionContainerChildren.interaction.tl.spec.ts<br>P2: ComposerSelectionContainerChildren.risk.tl.spec.ts |
| 待审核 | LayoutPane | 886 | 8 | 16 | **multi-pass** | ✅ 推进 |  |  | P1: LayoutPane.interaction.tl.spec.ts<br>P2: LayoutPane.risk.tl.spec.ts<br>P3: LayoutPane.display.tl.spec.ts |
| 待审核 | ConsoleDialogComponent | 88 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ console-dialog.component.spec.ts | callbacks queue dialog opens that NG0205 after the fixture is destroyed.; (after the fixture is destroyed) is a no-op instead of NG0205. | single pass (+内存泄漏) |
| 待审核 | PagingControlComponent | 84 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ViewerMobileToolbarComponent | 72 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | VsBookmarkPaneComponent | 97 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | VSObjectContainer | 633 | 3 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: VSObjectContainer.interaction.tl.spec.ts<br>P2: VSObjectContainer.risk.tl.spec.ts<br>P3: VSObjectContainer.display.tl.spec.ts |
| 待审核 | ExportDialog | 103 | 1 | 3 | **single-pass** | ✅ 推进 | ⚠️ export-dialog.spec.ts | Bug #17235 should not; test: should show error | single pass (+竞态+内存泄漏) |
| 待审核 | EmailDialog | 103 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ScheduleDialog | 106 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | BookmarkPropertyDialog | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ShareEmailDialogComponent | 79 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ShareGoogleChatDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ShareSlackDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | RemoveBookmarksDialog | 69 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | BindingEditor | 267 | 1 | 1 | **single-pass** | ✅ 推进 | ⚠️ binding-editor.spec.ts | Bug #20245; for Bug #20163; test: Crosstab should not have a percent by option | single pass (+内存泄漏) |
| 待审核 | VSObjectView | 190 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | VsWizardPane | 901 | 1 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: VsWizardPane.interaction.tl.spec.ts<br>P2: VsWizardPane.risk.tl.spec.ts |
| 待审核 | ObjectWizardPane | 556 | 1 | 3 | **multi-pass** | ✅ 推进 |  |  | P1: ObjectWizardPane.interaction.tl.spec.ts<br>P2: ObjectWizardPane.risk.tl.spec.ts |
| 待审核 | ComponentTree | 86 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ComposerBindingTree | 66 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | CodemirrorComponent | 417 | 1 | 8 | **multi-pass** | ✅ 推进 |  |  | P1: CodemirrorComponent.interaction.tl.spec.ts<br>P2: CodemirrorComponent.risk.tl.spec.ts |
| 待审核 | ViewsheetOptionsPane | 131 | 0 | 5 | **single-pass** | ✅ 推进 | ⚠️ viewsheet-options-pane.spec.ts | #17036,the design mode data size should be disable when use worksheet; Bug #17303 Clear button should be enabled when has datasource; Bug #10157 Clear | single pass (+竞态+内存泄漏) |
| 待审核 | FiltersPane | 119 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ScreensPane | 160 | 0 | 2 | **single-pass** | ✅ 推进 | ⚠️ screens-pane.spec.ts | Bug #19354 Click device 'Delete' button, confirm dialog should pop up.; Bug #18417, clear button should be disabled when no print layout; Bug #19349,  | single pass (+内存泄漏) |
| 待审核 | LocalizationPane | 81 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ localization-pane.component.spec.ts | Bug #19630 Components node should display; Bug #19118 should focus on the selected column | single pass |
| 待审核 | ViewsheetScriptPane | 184 | 2 | 0 | **single-pass** | ✅ 推进 | ⚠️ viewsheet-script-pane.spec.ts | TypeError when the timer fired after the fixture was destroyed.; Bug #18853 should select onRefresh by default; fixture is destroyed (which nulls code | single pass |

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

**Pass 1** (`ComposerToolbarComponent.interaction.tl.spec.ts`)
- Methods: sheet, crossJoinEnabled, tables, focusedObjects, alignObjects, layoutDistributeEnabled, layoutAlignEnabled, layoutResizeEnabled, openViewsheetWizard, notify, save, getTabularDataSourceTypes, save0, saveAs, options, preview, refresh, getTableStyleModel, undo, undoEnabled, redo, redoEnabled, currentLayout, layoutRuntimeId, cut, copy, paste, snapToGridChanged, snapToObjectsChanged, getAlignObj, layoutAlign, layoutDistribute, openVPMPrincipalDialog, getNextName, concatEnabled, newConcatTable, joinEnabled, newJoinTable, createCrossJoin, layoutWorksheetGraph, isInitializedWorksheet, isWorksheet, isViewsheet, isScript, isTableStyle, isObjectSelected, isPreview, scrollLeft, scrollRight, enableLayoutAlign, isPrintLayout, editPrintHeader, editPrintContent, editPrintFooter, isPrintLayoutSelected, selectGuideType, isGuideSelected, zoomLayout, zoom, isZoomItemSelected, zoomOutEnabled, zoomInEnabled, vs, worksheetOperationsDisabled, resizeListener, toggleFullScreen, getNonMenuToolbarActions, getOptionsTooltipText, openSession, getMergeMenuToolbarActions, saveTooltipText, saveAsTooltipText, previewOperations, editOperations, layoutOperations, snappingOperations, getVPMPrincipalToolbarActions, jdbcExists, sqlEnabled, pasteEnabled, isCompositeView, left, top, width, height
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ComposerToolbarComponent.risk.tl.spec.ts`)
- Methods: loadDataSources, ngAfterViewInit, addDropdownListeners, subscribe, newWorksheet, forEach, fireMoveResize, layoutResize, enterParameters, onFullScreenChange
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`ComposerToolbarComponent.display.tl.spec.ts`)
- Methods: enableFormatPainter, layoutShowing, hiddenComposerIcon, getDatabaseLabel, getTabularLabel
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### AssetTreePane

**Pass 1** (`AssetTreePane.interaction.tl.spec.ts`)
- Methods: openNodeSheet, openSheetFromAsset, forEach, addRecentlyViewed, getRecentRootFun, getEntryName, hasMenuFunction, hasMenu, openAssetTreeContextmenu, createActions, createNewFolderAction, createNewQueryAction, createOpenViewsheetAction, createOpenMetaViewsheetAction, createRenameViewsheetAction, createRenameWorksheetAction, createRenameFolderAction, createNewTableStyleAction, createNewScriptAction, containsAuditNodes, createNewFolder, sendAddFolderEvent, createOpenScriptAction, createOpenTableStyleAction, createRenameScriptAction, createRenameTableStyleAction, showRenameAssetDialog, renameAsset, isAssetOpened, containsOpenedLibraryAssets, dispatchRenameAssetEvent, showMessage, nodeDrag, isRejectFunction, isRejectNodes, nodeDrop
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`AssetTreePane.risk.tl.spec.ts`)
- Methods: deleteEntries, createDeleteViewsheetAction, deleteRecentAssets, createDeleteWorksheetAction, createDeleteFolderAction, createDeleteScriptAction, dispatchRemoveAssetEvent, moveAssetToRecyclingBin, deleteAssets, dispatchChangeAssetEvent, confirm
- Reason: async≥3：竞态 / destructive / state inconsistency

### VSFormatsPane

**Pass 1** (`VSFormatsPane.interaction.tl.spec.ts`)
- Methods: focusedAssemblies, filter, color, get, colorType, backgroundColor, backgroundColorType, format, getFont, getAlignment, changeColor, isViewsheetSelected, isValueFillVisible, isNonEditableChartVOSelected, isFontDisabled, isEditDisabled, isChartEditableSelected, isAlignDisabled, isHAlignmentEnabled, isVAlignmentEnabled, isWrapTextDisabled, isBorderDisabled, isRoundCornerDisabled, isColorDisabled, isBackgroundDisabled, isDynamicColorDisabled, isCSSDisabled, updateCSS, changeAlphaWarning, updatePresenter, updatePresenterProperties, getComboMode, measureBarSelected, isInputType, selectedDetailCell, updateProperties, reset, openPresenterPropertyDialog, tableSelected, textSelected, shapeSelected, borderTooltip, roundCornerMax, isRoundTopCornersOnlyVisible
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 3** (`VSFormatsPane.display.tl.spec.ts`)
- Methods: getFormat, getColorLabel, closeFormat, isFormatDisabled, updateFormat, getBorderLabel, isFormattingDisabled, getCSSLabel, showPresenter
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### WSPaneComponent

**Pass 1** (`WSPaneComponent.interaction.tl.spec.ts`)
- Methods: worksheet, active, onSplitDrag, concatenateTables, enterParams, processNotification, cleanup, getAssemblyName, addVariable, addGrouping, editJoin, selectCompositeTable, worksheetCompositionChanged, cut, copy, paste, insertColumns, replaceColumns, openAssemblyConditionDialog, openAggregateDialog, toggleAutoUpdate, selectColumnSource, oozColumnMouseEvent, getSourceTableName, addTable, fetchTableDataCount, touchAsset, initKeyListeners, updateSelectingColumnSource, openExistingWorksheet, refreshAssembly, confirm, processWSCollectVariablesCommand, processReopenSheetCommand, processOpenWorksheetCommand, processWSInitCommand, processRefreshWorksheetCommand, processWSAddAssemblyCommand, processWSEditAssemblyCommand, processWSRefreshAssemblyCommand, processMessageCommand, processExpiredSheetCommand, processWSFocusCompositeTableCommand, processUpdateUndoStateCommand, processWSExportCommand, processSaveSheetCommand, processCloseSheetCommand, processSetWorksheetInfoCommand, processForceNotCloseWorksheetCommand, processWSLoadTableDataCountCommand, updateLoadingMask, processWSFocusAssembliesCommand, processSetVPMPrincipalCommand, processWSSetMessageLevelsCommand, processWSFinishPasteWithCutCommand, processSaveWorksheetCommand
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`WSPaneComponent.risk.tl.spec.ts`)
- Methods: sqlEnabled, freeFormSqlEnabled, crossJoinEnabled, showGettingStartedMessage, expressionColumnEnabled, setup, worksheetCancel, openSortColumnDialog, editQuery, openSqlQueryDialog, openTabularQueryDialog, cancelLoading, subscribeToFocus, initDragAssetColumnsListener, destroyKeyListeners, openWorksheet, processWSRemoveAssemblyCommand, processWSMoveAssembliesCommand, processWSMoveSchemaTablesCommand, processClearLoadingCommand
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`WSPaneComponent.display.tl.spec.ts`)
- Methods: toggleShowColumnName, processShowLoadingMaskCommand
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VSPane

**Pass 1** (`VSPane.interaction.tl.spec.ts`)
- Methods: vs, active, getAssemblyName, getSnapGridStyle, getBackgroundImage, ngAfterViewInit, zoom, detectChanges, isActionEnabled, isGroupActionEnabled, isUngroupActionEnabled, isBringToFrontActionEnabled, isSendToBackActionEnabled, isSnapshot, isVSSnapshot, processSetViewsheetInfoCommand, processUpdateLayoutCommand, processVSDependencyChangedCommand, processReopenSheetCommand, processCollectParametersCommand, enterParameters, processInitGridCommand, processUpdateLayoutUndoStateCommand, processUpdateUndoStateCommand, isInZone, processCloseSheetCommand, processSaveSheetCommand, processExpiredSheetCommand, processChangeCurrentLayoutCommand, processSetRuntimeIdCommand, processAddVSObjectCommand, applyAddVSObjectCommand, processAssemblyChangedCommand, processUpdateZIndexesCommand, processRefreshVSObjectCommand, applyRefreshVSObjectCommand, refreshGroupContainerOrder, processRenameVSObjectCommand, processPopulateVSObjectTreeCommand, processRefreshBindingTreeCommand, processExportVSCommand, processMessageCommand, processProgress, processVSTrapCommand, processSetGrayedOutFieldsCommand, confirm, drop, dragenter, dragover, mousedown, onKeydown, dblClick, paste, isModalOpen, trackByFn, onAssemblyActionEvent, copyAssembly, cutAssembly, bringAssemblyToFront, bringAssemblyForward, sendAssemblyToBack, sendAssemblyBackward, assemblyResized, processAssemblyResize, updateDragRulerGuides, onSnap, clickEvent, isTargetVSPane, isTargetShape, replaceObject, layoutToolbarVisible, mobileToolbarVisible, layoutChanged, layoutName, touchAsset, openEmbeddedViewsheet, openEditPane, openWizardPane, onSelectionBox, forEach, refreshStatusByVs, openWorksheet, refreshStatusByLayout, trimComma, substring, processOpenBindingPaneCommand, popupNotifications, onMaxModeChange, openConsoleDialog, getTemplateWidth, getTemplateHeight, onKeyUp, changeSearchMode, isSearchMode, search, nextFocus, previousFocus, scrollToMatchedAssembly, flatMap, scrollToAssembly, compareObjectByPosition, getStatusForStatusBar, getStatus2ForStatusBar, getSearchString, isVisible, searchInputKeyUp, isDefaultOrgAsset, isFilterInMaxModeView
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VSPane.risk.tl.spec.ts`)
- Methods: openExistingViewsheet, processClearLoadingCommand, cancelViewsheetLoading, processRemoveVSObjectCommand, removeVSObject, addConsoleMessage, deselectObjects, resetCursor, removeAssembly, assemblyMoved, processAssemblyMoved, updateRulerGuides, updateSnapGuides, updateFormats, refreshStatus, updateRulerPosition
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`VSPane.display.tl.spec.ts`)
- Methods: processShowLoadingMaskCommand, processSetCurrentFormatCommand, showProgressDialog, getDataSourceCSSIcon, displayPlaceholderDragElementModel, openFormatPane, getSearchResultLabel
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ViewerAppComponent

**Pass 1** (`ViewerAppComponent.interaction.tl.spec.ts`)
- Methods: tabsHeight, runtimeId, getAssemblyName, ngAfterViewInit, forEach, initViewsheetConnection, ngAfterContentInit, ngAfterViewChecked, active, mobileToolbarVisible, pinchZoom, getClosestScrollParent, screenSize, scrolled, checkZoom, beforeunloadHandler, onKeyUp, onToolbarButtonFocus, nextToolbarButton, previousToolbarButton, onKeyDown, getPreviousSelectableAssembly, getNextSelectableAssembly, selectAssembly, mousedown, previousURLEnable, back, previousPage, nextPage, editViewsheet, subscribe, reopenExpiredViewsheet, refreshViewsheet, zoom, zoomLayout, isZoomItemSelected, zoomOutEnabled, zoomInEnabled, isMobile, emailViewsheet, scheduleViewsheet, printViewsheet, exportViewsheet, importExcel, importExcelFile, toggleAnnotations, getBookmarks, saveCurrentBookmarkDisabled, saveBookmark, gotoBookmark, doGotoBookmark, setDefaultBookmark, editBookmark, substring, toggleFullScreen, applyFullScreen, onFullScreenChange, closeViewsheetOnServer, closeViewsheet, openConditionDialog, openHighlightDialog, createTableActionHandler, processSetRuntimeIdCommand, processCollectParametersCommand, processUpdateSharedFiltersCommand, processDelayVisibilityCommand, processAddVSObjectCommand, processRefreshVSObjectCommand, processInitGridCommand, processSetPermissionsCommand, processSetExportTypesCommand, processMessageCommand, processSetComposedDashboardCommand, processProgress, processUpdateUndoStateCommand, processAnnotationChangedCommand, isInZone, filter, processSetViewsheetInfoCommand, processUpdateZIndexesCommand, processExpiredSheetCommand, onOpenContextMenu, showAnnotationDialog, addMobileActionSubsciption, isPermissionForbidden, addAnnotation, openViewsheet0, enterParameters, registerDataTipVisible, registerPopCompVisible, setDataTipOffsets, getComponentModel, setAppSize, getScaleSize, onViewerRootResize, getVariables, updateData, getDim, assign, getMinimumToolbarWidth, notifyParentFrame, submitData, preventMouseInteractions, processOpenComposerCommand, openComposer, hasBottomPadding, hasRightPadding, shareEmail, shareFacebook, shareHangouts, shareLinkedin, shareSlack, shareTwitter, shareLink, changeMaxMode, toggleDoubleCalendar, scroll, showHints, scrollLeft, scrollTop, openProfileDialog, openViewsheetOptionDialog, hideProfilingBanner, isPreviousPageVisible, isPreviousPageDisabled, isNextPageVisible, isNextPageDisabled, isEditVisible, isRefreshViewsheetVisible, isEmailVisible, isSocialSharingVisible, isShareEmailDisabled, isShareFacebookDisabled, isShareHangoutsDisabled, isShareLinkedInDisabled, isShareSlackDisabled, isShareTwitterDisabled, isShareLinkDisabled, isScheduleVisible, isPrintViewsheetVisible, isExportVisible, isImportExcelVisible, isZoomVisible, isAnnotationButtonVisible, isHideAnnotationsVisible, bookmarksVisible, isAddBookmarkDisabled, isShareToAllDisabled, isSetDefaultBookmarkVisible, isSetDefaultBookmarkDisabled, isEditBookmarkVisible, isEditBookmarkDisabled, isToggleFullScreenVisible, isCloseViewsheetVisible, setViewerToolbarDefinitions, createBookmarkButtonDefs, push, getReloadMessage, getViewerRootHeight, setMobileToolbarActions, moreActions, allowedActionsNum, isPageControlVisible, usePagingControl, updateScrollTop, checkExportStatus, getOrgId, loadingStateChanged, isDataTipOrPopComponentVisible, getScrollViewport, updateTabPositions
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ViewerAppComponent.risk.tl.spec.ts`)
- Methods: onViewerRootResizeEvent, clearSelectedAssemblies, setServerUpdateInterval, clearServerUpdateInterval, showBookmarks, isBookmarkHome, deleteBookmark, deleteBookMarks, addBookmark, fullScreenApplied, beforeDestroy, processClearLoadingCommand, processRemoveVSObjectCommand, processClearScrollCommand, removeAnnotations, openViewsheet, openPreviewViewsheet, cancelViewsheetLoading, sendBookmarkEvent, deleteBookmarkByCondition, updateScrollLeft, pagingControlModel, processEmbedErrorCommand, handleDataTipPopComponentChanges, clearDataTipPopComponents, updateScrollViewport
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`ViewerAppComponent.display.tl.spec.ts`)
- Methods: onOpenChartFormatPane, processSetCurrentFormatCommand, processOpenAnnotationFormatDialogCommand, showProgressDialog, processShowLoadingMaskCommand, showViewsheetContextMenu, showScrollButton, showContextMenu, showBookmarkChangedDialog, updateFormat, closeFormatPane, getCurrentFormat, isShowAnnotationsVisible, showingActions
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VSBindingPane

**Pass 1** (`VSBindingPane.interaction.tl.spec.ts`)
- Methods: runtimeId, aiAssistantPermission, touchAsset, sourceName, originalMode, getAssemblyName, params, updatePresenterProperties, refreshVSBinding, isActionVisible, calcTableEditorService, isMergeCellsActionEnabled, isSplitCellsActionEnabled, processSetVSBindingModelCommand, processOpenEditGeographicCommand, processRefreshVSObjectCommand, processAddVSObjectCommand, updateObjectModel, selectWholes, processRefreshBindingTreeCommand, processMessageCommand, processProgress, isInZone, processVSTrapCommand, processSetGrayedOutFieldsCommand, processVSBindingTrapCommand, processExpiredSheetCommand, getBindingType, confirm, updateData, closeHandler, isWizardExpiredCommand, closeHandler0, processOpenObjectWizardCommand, processCloseBindingPaneCommand, processAssemblyChangedCommand, goToWizard, then, openWizardPane, goToWizardVisible, hasExpression, hasExpressionRef, isExpressionAes, isCrosstab, isChart, haveDynamicBinding, popupNotifications, onResize, resizeObjectView, processRenameVSObjectCommand, addConsoleMessage, messageChange
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VSBindingPane.risk.tl.spec.ts`)
- Methods: isActionEnabled, isDeleteRowActionEnabled, isDeleteColumnActionEnabled, handleExpiredSheet, processClearLoadingCommand, cancelViewsheetLoading
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`VSBindingPane.display.tl.spec.ts`)
- Methods: formatPaneDisabled, showProgressDialog, processSetCurrentFormatCommand, getCurrentFormat, createTextFormatEvent, updateFormat, createUpdateFormatEvent, isAggregateTextFormat, processShowLoadingMaskCommand
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ImportCSVDialog

**Pass 1** (`ImportCSVDialog.interaction.tl.spec.ts`)
- Methods: ngAfterViewChecked, setEnabled, updateFile, updateForm, showLimitMessage, handleEmptyFile, reset, unpivotEnabled, onHeaderRename, validateHeaders, toString, validateFirstRow
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ImportCSVDialog.risk.tl.spec.ts`)
- Methods: initForm, clearProgress, ok, cancel, initFileToucher, updatePreviewTable, parsePreviewResponse
- Reason: async≥3：竞态 / destructive / state inconsistency

### SQLQueryDialog

**Pass 1** (`SQLQueryDialog.interaction.tl.spec.ts`)
- Methods: checkIfNotSaved, detachedCrossJoin, ok, then, apply, checkQueryValidity, onSwitchChange, refreshModelOnModeChange
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`SQLQueryDialog.risk.tl.spec.ts`)
- Methods: advancedEditing, subQuery, createForm, dataSourceChanged, loadDataSourceTree, clearDisabled, clear, updateNameValidation, cancel, subscribe, isApplyBtnDisabled, initOperations, destroyRuntimeQuery, isJoinEditView
- Reason: async≥3：竞态 / destructive / state inconsistency

### TabularQueryDialog

**Pass 1** (`TabularQueryDialog.interaction.tl.spec.ts`)
- Methods: displayTitle, helpDisplayTitle, createForm, validChanged, viewChanged, nestedViewChanged, hasRefreshButton, updateNameValidation, ok, apply
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`TabularQueryDialog.risk.tl.spec.ts`)
- Methods: authorize, refreshView, initView, hasCancelButton, buttonClicked, clearButtonClicks, clearButtonLoading, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### GroupingDialog

**Pass 1** (`GroupingDialog.interaction.tl.spec.ts`)
- Methods: init, initForm, initRoot, getCurrentSelectedNode, matchNode, checkOuterMirror, nodeExpanded, onlyForDisabled, addDisabled, editDisabled, upDisabled, downDisabled, addCondition, editCondition, getTooltip, getCSSIcon, ok
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`GroupingDialog.risk.tl.spec.ts`)
- Methods: updateOnlyFor, deleteDisabled, deleteCondition, moveConditionUp, moveConditionDown, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### VariableAssemblyDialog

**Pass 1** (`VariableAssemblyDialog.interaction.tl.spec.ts`)
- Methods: init, initDefaultValueType, initForm, checkOuterMirror, getDefaultStrValue, showVariableListDialog, showVariableTableListDialog, valid, embeddedValid, queryValid, selectDefaultValueType, isExpressionDefaultValue, okDisabled, saveChanges, validateVariableList, defaultExpressionValueChange, getVariableTree, getVariableTreeModel
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VariableAssemblyDialog.risk.tl.spec.ts`)
- Methods: cancelChanges
- Reason: async≥3：竞态 / destructive / state inconsistency

### VPMPrincipalDialogComponent

**Pass 1** (`VPMPrincipalDialogComponent.interaction.tl.spec.ts`)
- Methods: ok, resetSessionIds, setSessionIds, resetSessionId
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VPMPrincipalDialogComponent.risk.tl.spec.ts`)
- Methods: initForm, cancel
- Reason: async≥3：竞态 / destructive / state inconsistency

### AssetTreeComponent

**Pass 1** (`AssetTreeComponent.interaction.tl.spec.ts`)
- Methods: searchMode, handleAssetChangeEvent, refreshParentNodes, refreshSelectedNodes, recursiveRefreshSelectedNodes, refreshNodeChildren, loadAll, containsLoadAssetEvent, findAssetTreeNodeFromIdentifier, findAssetTreeNodeParentFromIdentifier, nodeExpanded, selectNodes, doubleclickNode, contextmenuTreeNode, getCSSIcon, getParentNode, getNodeByData, searchStrChange, checkDSPlaceholder, searchStart, updateUseVirtualScroll, hasLoadedAllNode, getNodeByPath, getPathToEntry, addExtraTrees, revalidateExtraTrees, refreshView, getNodeEqualsFun, isTableStyle
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`AssetTreeComponent.risk.tl.spec.ts`)
- Methods: loadAssetTree, addDeleteDataSources, addDeleteDataSources0, setupAssetClientService, createRefreshNodeEvent, removeDSLoadingPlaceholder, removeExtraTrees
- Reason: async≥3：竞态 / destructive / state inconsistency

### RadiusDropdown

**Pass 1** (`RadiusDropdown.interaction.tl.spec.ts`)
- Methods: radius, disabled, max, initForm, updateRadiusStatus
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`RadiusDropdown.risk.tl.spec.ts`)
- Methods: initForm
- Reason: async≥3：竞态 / destructive / state inconsistency

### WSAssemblyGraphPaneComponent

**Pass 1** (`WSAssemblyGraphPaneComponent.interaction.tl.spec.ts`)
- Methods: bind, isJoinWithExpr, ngAfterContentInit, ngAfterViewChecked, oozKeyDown, onKeyUp, getAssemblyName, cleanup, setContainer, trackByFn, getThumbnailClasses, selectAssembly, clickAssembly, onSelectionBox, updateLastClick, allowDrop, drop, openAssets, handleDragColumnsEvent, subscribe, cutAssembly, copyAssembly, toggleAutoUpdate, tableEndpoints, registerAssembly, startEditName, editName, connectAssemblies, createConnection, refreshAssembly, setDraggable, oozScroll, toggleEndpoints, each, hideEndpoints, addEndpoint, notify, checkJoinCompatibility, checkConcatCompatibility, processConcatCompatibilityCommand, refreshDragSelection, setRepaintTimer, openContextMenu, paste, setDimmedTypes, dimConnection, undimConnection, windowMouseup, cleanupMouseupListener, sendDragPasteEvent, isArrowKey, isWorksheetEmpty, selectDependent
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`WSAssemblyGraphPaneComponent.risk.tl.spec.ts`)
- Methods: clearSelection, selectCompositeTable, removeFocusedAssemblies, removeAssemblies, then, destroyAssembly, causeConcatRejection, join, concat, forEach, dragPasteAssemblies, moveAssemblies, subscribeToFocus, refreshColumnSourceConnectionTypes, dimUnfocusedAssemblies, windowMousemove, arrowKeyMove, getArrowKeyMoveFactor, arrowKeyMoveEnd
- Reason: async≥3：竞态 / destructive / state inconsistency

### WSDetailsPaneComponent

**Pass 1** (`WSDetailsPaneComponent.interaction.tl.spec.ts`)
- Methods: searchQuery, replace, headerNameChanged, populateTableModeButtons, getTableStatus, openAggregateDialog, openFormulaEditorDialog, openConditionDialog, openShowHideColumnsDialog, isSupportChangeColumnOrder, openReorderColumnsDialog, toggleSearchBar, checkEnterKey, searchPrevious, searchNext, onSearchResultUpdate, openImportCSVDialog, openChangeColumnTypeDialog, openChangeColumnDesDialog, getValueRangeParams, validateValueRangeModel, changeTableMode, setRuntime, setShowName, getTableModeButton, exportTable, toggleMirrorAutoUpdate, submitExpressionCallback, _submitColumnTypeCallback, runQuery, formatAll, openConsoleDialog, isTableButtonVisible, isTableButtonToggled, toggleWrapColumnHeaders, isWrapColumnHeadersEnabled
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`WSDetailsPaneComponent.risk.tl.spec.ts`)
- Methods: dataModeEnabled, updateRowRange, selectColumnSource, oozColumnMouseEvent, openDateRangeOptionDialog, openNumericRangeOptionDialog, openValueRangeDialog, clearSearch, cancelQuery
- Reason: async≥3：竞态 / destructive / state inconsistency

### EditableObjectContainer

**Pass 1** (`EditableObjectContainer.interaction.tl.spec.ts`)
- Methods: vsObjectModel, calculateZIndex, selectionChild, selectionChildModelJson, stringify, lineModel, getTopPosition, getMinHeight, searchDisplayed, visible, isSearchResults, searchMode, isSearchFocus, getVariableValues, updateDropTarget, ngAfterViewInit, assemblySelected, onDragStart, onDragEnd, isOutside, onResizeStart, onResizeEnd, onDropzoneEnter, getAssembly, onDropzoneLeave, onDropzoneDrop, draggingCloseEnough, onLineDragEnd, onLineStartDragEnd, snapLine, onLineEndDragEnd, onLineDragBegin, preventMouseEvents, isShape, isFormAssembly, isFadeAssembly, isPreventResize, isPreventDrag, isViewsheet, isLocked, hasScript, conditionAction, hasSlideout, isDragBorderTop, isDragBorderBottom, isDragBorderAll, isDragBorder, isInteractDropZone, contextMenuOpen, select, onEnter, onLeave, enableInteraction, disableInteraction, drop, turnOnEditMode, onKeyDown, isCalcDroppable, isMiniToolbarVisible, miniToolbarWidth, hasMiniToolbar, isCalcDroppableType, isEnterEnabled, isDataRefAccepted, openEmbeddedViewsheet, changeMinHeightFromAutoText, openLayoutOptionDialog, endAnchorDrag, openEditPane, openWizardPane, hasMultipleSelectRegions, getLineRotationAngle, getLineLength, updateFocus, resizeObject, setMovingResizing, goToWizardVisible, clickEditButton, onMaxModeChange, onDetectViewChange, onMouseEnter, contextMenuClose, toolbarForceHidden
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`EditableObjectContainer.risk.tl.spec.ts`)
- Methods: getMoveHandle, getMovedPosition, onDragMove, removeCopies, onResizeMove, onLineDragMove, onLineStartDragMove, onLineEndDragMove
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`EditableObjectContainer.display.tl.spec.ts`)
- Methods: showSlideout, showLayoutOptionDialog, onTitleResizeMove, onTitleResizeEnd, popupShowing, showEdit
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### ComposerSelectionContainerChildren

**Pass 1** (`ComposerSelectionContainerChildren.interaction.tl.spec.ts`)
- Methods: vsObject, getObjectTop, setChildrenHeight, onEnter, onLeave, onContainerDragOver, isDragAcceptable, onDragOver, drop, isShape, openLayoutOptionDialog, select, childChanged, resizeAssembly, childWithBorder, processChangeVSSelectionTitleCommand, getBodyWidth, getPaddingHeight, getInnerWidth, droppedOnChild, isSelected, zIndex
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ComposerSelectionContainerChildren.risk.tl.spec.ts`)
- Methods: moveAssembly
- Reason: async≥3：竞态 / destructive / state inconsistency

### LayoutPane

**Pass 1** (`LayoutPane.interaction.tl.spec.ts`)
- Methods: onLayoutResize, guideSize, getAssemblyName, getPrintBounds, getPLayoutSize, getSnapGridStyle, getBackgroundImage, drop, onSelectionBox, getLayoutObjects, onSnap, getNextObjectName, trackByFn, processAddLayoutObjectCommand, processRefreshLayoutObjectsCommand, updateObjectList, getGuideSize, getLayoutObjectSize, updateDimensions, processUpdateLayoutUndoStateCommand, mousedown, updateDimensions0, scrolled, assemblyResized, processAssemblyResize
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`LayoutPane.risk.tl.spec.ts`)
- Methods: model, sizeGuidesVisible, refreshViewsheet, getLayoutSize, filter, print, processRemoveLayoutObjectsCommand, removeSelectedAssemblies, removeLayoutObject, moveObject, updateSnapGuides
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`LayoutPane.display.tl.spec.ts`)
- Methods: showContent, showHeader, showFooter, processSetCurrentFormatCommand
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VSObjectContainer

**Pass 1** (`VSObjectContainer.interaction.tl.spec.ts`)
- Methods: keyNavigation, scrollViewport, ngAfterViewInit, isAssemblyVisible, isFilterInMaxModeView, isMiniToolbarVisible, toolbarForceHidden, trackByName, viewer, select, isSelected, isAtBottom, submitClicked, onMaxModeChange, isMaxModeHidden, isFocused, getAssemblyAsClass, getAssemblyDivId, getToolbarTop, getActionsWidth, getToolbarLeft, getToolbarWidth, checkContainerHasVerticalScrollbar, openWizardPane, isActivePopComponent, needsZIndexBoost, getPopUpContentBoostZIndex, zIndex, getVsObjectPosition, getSelectionBodyHeight, getSelectionCellHeight, max, getPopDimZIndex, drawPopDim, getPopDimWidth, getPopDimHeight, getActualWidth, onMouseEnter, getChartDataAnnotations, annotationMouseSelect, isChartAnnotationSelected, getChartAnnotationTetherTo, getChartAnnotationRestrictTo, isObjectRendered, updateRendered, isRectInitialized, isInScrollViewport
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VSObjectContainer.risk.tl.spec.ts`)
- Methods: getVariablesValues, removeAnnotationFromOverlay
- Reason: async≥3：竞态 / destructive / state inconsistency

**Pass 3** (`VSObjectContainer.display.tl.spec.ts`)
- Methods: showContextMenu, popupShowing, isPopupShowing, showToolbar, showingPopUpOrDataTip
- Reason: dispatch≥3：label/icon/conditional display / boundary inputs

### VsWizardPane

**Pass 1** (`VsWizardPane.interaction.tl.spec.ts`)
- Methods: gridRowCount, ngAfterViewInit, getVScrollWidth, runtimeId, linkUri, resizeListener, refreshGridRows, setKeydownListener, undoEnabled, redoEnabled, onKeydown, getAssemblyName, trackByFn, close, editWizardObject, changeFollowDirection, isFollow, openImageExplore, processSetWizardGridCommand, fileChanged, replaceObject, changeRows, changeCols, dragResizeStart, dragResizeEnd, mergeDimension, prepareBottomFollowAssemblies, prepareFollowRestriction, prepareRightFollowAssemblies, sortByXPosition, sortByYPosition, insertObject, changeNewObject, mouseOnWizardObject, getBottomRight, resizeObject, onSelectionBox, isTempAssembly, processUploadImageCommand, processAddVSObjectCommand, processRefreshVSObjectCommand, refreshVSObject, processUpdateUndoStateCommand, getFollowDirSrc, hiddenNewBlockChanged, keyDown
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`VsWizardPane.risk.tl.spec.ts`)
- Methods: removeKeydownListener, moveable, removeWizardObject, remove, processAssemblyMoved, moveWizardObject, moveAssembly, clearFocused, shouldClearFocused, processRemoveVSObjectCommand
- Reason: async≥3：竞态 / destructive / state inconsistency

### ObjectWizardPane

**Pass 1** (`ObjectWizardPane.interaction.tl.spec.ts`)
- Methods: getAssemblyName, chartBindingModel, tableBindingModel, filterBindingModel, sourceName, assemblyType, autoOrder, showAutoOrder, assemblyName, availableFields, dimensions, measures, isDetail, isAssemblyBinding, startsWith, isFullEditorVisible, goToFullEditor, close, processTableWarningCommand, processRefreshRecommendCommand, processCloseObjectWizardCommand, processSetVSBindingModelCommand, processRefreshVsWizardBindingCommand, processSetWizardBindingFormatCommand, processRefreshVSObjectCommand, processAddVSObjectCommand, setVSObjectModel, updateVSObjectModel, isLatestTempAssembly, updateFormat, showLegend, onEditAggregate, onEditDimension, onUpdateDetails, onAddAggregate, onAddDimension, onAutoOrderChange, isRefInMeasures, isFullRefInMeasures, isRefInDimension, isFullRefInDimension, isRefInDetails, sendRefreshWizardBindingEvent, treePaneCollapsed, toggleRepositoryTreePane, isSupportFullEditor, isSelectionTree, changeSubtype, processShowRecommendLoadingCommand, processFireRecommandCommand, eventLoading, processMessageCommand, addConsoleMessage, processProgress, showProgressDialog, switchToMeta, processSwitchToMetaModeCommand, fixedFormulaMap, splitPaneDragEnd, messageChange
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`ObjectWizardPane.risk.tl.spec.ts`)
- Methods: aiAssistantPermission, processRemoveVSObjectCommand, onEditSecondColumn, onDeleteAggregate, onDeleteDimension, processClearRecommendLoadingCommand
- Reason: async≥3：竞态 / destructive / state inconsistency

### CodemirrorComponent

**Pass 1** (`CodemirrorComponent.interaction.tl.spec.ts`)
- Methods: sql, scriptDefinitions, ngAfterViewInit, setAnalysisResults, initCodeMirror, checkSyntax, docTooltip, renderAnalysisResults
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`CodemirrorComponent.risk.tl.spec.ts`)
- Methods: analysisResults, expression, cursor, functionTreeRoot, ngAfterViewChecked, triggerSyntaxAnalyzer, function, destroyCodeMirror, isEditorElementDisplayed
- Reason: async≥3：竞态 / destructive / state inconsistency
