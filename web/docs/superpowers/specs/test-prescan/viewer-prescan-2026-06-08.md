# viewer Route Pre-scan Report

**日期**: 2026-06-08（2026-06-10 补充扫描，新增 11 个组件；2026-06-24 补充扫描，新增 103 个组件）
**候选组件数**: 142（原 39，2026-06-24 新增 103）| **建议推进**: 110 | **建议跳过**: 21 | **建议暂缓**: 11 | **待审核**: 0 | **多 pass 组件**: 34
**测试进度**: ✅已测试 110 / 142 | 待测 0 / 142 | ⏭ 已跳过 21 / 142 | ⏸️暂缓 11 / 142

## 状态说明
- 第一列「状态」初始为「待审核」，人工审核后改为 ✅已测试 / ⏭已跳过 / ⏸️暂缓
- ⚠️ 有旧spec — 新测试通过后在同 PR 内删除旧 .spec.ts
- 「旧 spec 备注」列记录旧测试中不易从源码推断的 case，生成新测试时参考

## 分类说明
- **single-pass**：logic_lines ≤ 500 AND dispatch ≤ 2 AND async ≤ 5 → `component-file-name.component.tl.spec.ts`
- **multi-pass**：logic_lines > 500 OR dispatch ≥ 3 OR async > 5 → 多文件 pass 计划
- single-pass 按需追加：async≥3 竞态 / async≥1 内存泄漏 / dispatch≥3 边界

## 候选组件清单

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec                                                                               | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|--------------------------------------------------------------------------------------|-------------|-----------|
| ✅已测试 | ViewerRootComponent | 32 | 0 | 1 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+内存泄漏) |
| ✅已测试 | ViewerViewComponent | 261 | 0 | 3 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | ViewerEditComponent | 188 | 0 | 1 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+内存泄漏) |
| ✅已测试 | AiAssistantPanelComponent | 177 | 0 | 3 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | DownloadTargetComponent | 84 | 1 | 4 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | PageTabComponent | 98 | 0 | 3 | **single-pass** | ✅ 推进 |                                                                                      | 无 | ✅ page-tab.component.tl.spec.ts (33 tests) |
| ✅已测试 | ViewerAppComponent | 3363 | 10 | 53 | **multi-pass** | ✅ 推进 | ⚠️ viewer-app.spec.ts                                                                | Bug #16456 TODO, logica changed, can not get fixed dropdown pane; Bug #19176 hide full screen in preview; Bug #16961 should refresh scale to screen vs | ✅ P1: viewer-app.component.interaction.tl.spec.ts (73 tests)<br>✅ P2: viewer-app.component.risk.tl.spec.ts (42 tests)<br>✅ P3: viewer-app.component.display.tl.spec.ts (61 tests) |
| ✅已测试 | VSBindingPane | 805 | 4 | 5 | **multi-pass** | ✅ 推进 |                                                                                      |  | P1: vs-binding-pane.component.interaction.tl.spec.ts (31+1xfail)<br>P2: vs-binding-pane.component.risk.tl.spec.ts (21)<br>P3: vs-binding-pane.component.display.tl.spec.ts (19) |
| ✅已测试 | VsWizardComponent | 371 | 0 | 4 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | PagingControlComponent | 84 | 0 | 0 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass |
| ✅已测试 | ViewerMobileToolbarComponent | 72 | 0 | 0 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass |
| ✅已测试 | VsBookmarkPaneComponent | 97 | 0 | 0 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass |
| ✅已测试 | ExportDialog | 103 | 1 | 3 | **single-pass** | ✅ 推进 | ~~⚠️ export-dialog.spec.ts~~                                                         | Bug #17235 should not; test: should show error | single pass (+竞态+内存泄漏) |
| ✅已测试 | EmailDialog | 103 | 0 | 1 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+内存泄漏) |
| ✅已测试 | ScheduleDialog | 106 | 0 | 2 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+内存泄漏) |
| ✅已测试 | BookmarkPropertyDialog | 58 | 0 | 0 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass |
| ✅已测试 | ProfilingDialog | 210 | 2 | 2 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+内存泄漏) |
| ✅已测试 | RemoveBookmarksDialog | 69 | 0 | 2 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+内存泄漏) |
| ✅已测试 | VSFormatsPane | 679 | 4 | 1 | **multi-pass** | ✅ 推进 | ⚠️ vs-formats-pane.spec.ts                                                           | Bug #16685, Bug #16689 check the aligment combox status; Bug #18597, BUg #18664 color,border, aligment status; Bug #18060, Bug #18342 for wrap text on | ✅ P1: vs-formats-pane.component.interaction.tl.spec.ts (40 tests)<br>✅ P3: vs-formats-pane.component.display.tl.spec.ts (27 tests) |
| ✅已测试 | ShareEmailDialogComponent | 79 | 0 | 3 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | ShareGoogleChatDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | ShareSlackDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |                                                                                      |  | single pass (+竞态+内存泄漏) |
| ✅已测试 | NotificationsComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ notifications.component.spec.ts                                                   | 无 | single pass (+内存泄漏) |
| ✅已测试 | VsWizardPane | 901 | 1 | 7 | **multi-pass** | ✅ 推进 |                                                                                      |  | ✅ P1: vs-wizard-pane.component.interaction.tl.spec.ts (45 tests)<br>✅ P2: vs-wizard-pane.component.risk.tl.spec.ts (16 tests) |
| ✅已测试 | ObjectWizardPane | 556 | 1 | 3 | **multi-pass** | ✅ 推进 |                                                                                      |  | ✅ P1: object-wizard-pane.component.interaction.tl.spec.ts (51 tests)<br>✅ P2: object-wizard-pane.component.risk.tl.spec.ts (17 tests) |
| ✅已测试 | ActionsContextmenuComponent | 154 | 0 | 1 | **single-pass** | ✅ 推进 | ~~⚠️ actions-contextmenu.component.spec.ts~~ | test: should not create a dropdown when there are no visible actions | ✅ actions-contextmenu.component.tl.spec.ts (28 tests) |
| ✅已测试 | VariableInputDialog | 214 | 0 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ variable-input-dialog.spec.ts~~                                                     | Bug #16824 Make sure the default value of a boolean type is false | ✅ variable-input-dialog.component.tl.spec.ts (30 tests) |
| ✅已测试 | EditGeographicDialog | 47 | 0 | 1 | **single-pass** | ✅ 推进 |                                                                                      |  | ✅ edit-geographic-dialog.component.tl.spec.ts (16 tests) |

---

> **以下为 2026-06-10 补充扫描新增组件**

| 状态    | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划                                                                                                                                                               |
|-------|------|-------------|----------|-------------|------|------|---------|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ✅已测试  | VSObjectContainer | 708 | 5 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: vs-object-container.component.interaction.tl.spec.ts<br>P2: vs-object-container.component.risk.tl.spec.ts<br>P3: vs-object-container.component.display.tl.spec.ts |
| ⏭ 已跳过 | ShareLinkDialog | 35 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                           |
| ✅已测试  | BindingEditor | 283 | 3 | 2 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: binding-editor.component.interaction.tl.spec.ts (47 tests)<br>✅ P3: binding-editor.component.display.tl.spec.ts (27 tests)                                       |
| ✅已测试  | VSObjectView | 206 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | ✅ vs-object-view.component.tl.spec.ts (23 tests)                                                                                                                      |
| ✅已测试  | WizardToolBarComponent | 110 | 0 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ wizard-tool-bar.component.spec.ts~~ | Only a single "should create" smoke test; no coverage of cancel() modal dialog flow, done(), undo()/redo() dispatch, undoEnabled/redoEnabled getter logic, or hiddenNewBlockChanged() output emission. | ✅ wizard-tool-bar.component.tl.spec.ts (23 tests)                                                                                                                     |
| ✅已测试  | VsWizardObjectComponent | 324 | 3 | 1 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: vs-wizard-object.component.interaction.tl.spec.ts (40 tests)<br>✅ P3: vs-wizard-object.component.display.tl.spec.ts (24 tests)                                  |
| ✅已测试  | WizardNewObject | 83 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ wizard-new-object.component.tl.spec.ts (12 tests)                                                                                                                   |
| ✅已测试  | ObjectWizardToolBarComponent | 96 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | ✅ object-wizard-tool-bar.component.tl.spec.ts (20 tests)                                                                                                              |
| ✅已测试  | WizardBindingTree | 421 | 2 | 4 | **single-pass** | ✅ 推进 | ~~⚠️ wizard-binding-tree.component.spec.ts~~ | Only a single "should create" smoke test; no coverage of selectNodes, recommender, flatternTree, command processors, or context-menu flows — effectively empty. | P1: wizard-binding-tree.component.interaction.tl.spec.ts (+竞态+内存泄漏)                                                                                                   |
| ✅已测试  | VSWizardAggregatePane | 293 | 1 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ wizard-aggregate-pane.component.spec.ts~~ | Spec 是 stub，仅 'should create'，无方法覆盖。 | single pass                                                                                                                                                           |
| ✅已测试  | VSWizardPreviewPane | 63 | 0 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ wizard-preview-pane.component.spec.ts~~ | Only a single "should create" smoke test; does not cover changeDescription, setPreviewPaneSize, hasLegend, or processRefreshDescriptionCommand. | single pass                                                                                                                                                           |

## 多 pass 组件详情

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

### VSFormatsPane

**Pass 1** (`vs-formats-pane.component.interaction.tl.spec.ts`)
- Methods: focusedAssemblies, filter, color, get, colorType, backgroundColor, backgroundColorType, format, getFont, getAlignment, changeColor, isViewsheetSelected, isValueFillVisible, isNonEditableChartVOSelected, isFontDisabled, isEditDisabled, isChartEditableSelected, isAlignDisabled, isHAlignmentEnabled, isVAlignmentEnabled, isWrapTextDisabled, isBorderDisabled, isRoundCornerDisabled, isColorDisabled, isBackgroundDisabled, isDynamicColorDisabled, isCSSDisabled, updateCSS, changeAlphaWarning, updatePresenter, updatePresenterProperties, getComboMode, measureBarSelected, isInputType, selectedDetailCell, updateProperties, reset, openPresenterPropertyDialog, tableSelected, textSelected, shapeSelected, borderTooltip, roundCornerMax, isRoundTopCornersOnlyVisible
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 3** (`vs-formats-pane.component.display.tl.spec.ts`)
- Methods: getFormat, getColorLabel, closeFormat, isFormatDisabled, updateFormat, getBorderLabel, isFormattingDisabled, getCSSLabel, showPresenter
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

---

## 补充扫描新增 multi-pass 组件详情（2026-06-10）

### VSObjectContainer

**Pass 1** (`vs-object-container.component.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, ngOnChanges, ngOnDestroy, keyNavigation setter, scrollViewport setter, select(), showContextMenu(), showToolbar(), openWizardPane(), annotationMouseSelect(), removeAnnotationFromOverlay(), onMouseEnter(), submitClicked(), onMaxModeChange()
- Reason: Lifecycle hooks, user-triggered events (click/select/context-menu/drag), input setters that trigger side effects (containerRef scroll listener wiring, updateRendered on scrollViewport change), and EventEmitter output assertions

**Pass 2** (`vs-object-container.component.risk.tl.spec.ts`)
- Methods: constructor subscription setup (scaleService.getScale, popService.componentPop, dataTipService.showHideDataTip), obs.subscribe in keyNavigation setter, setTimeout in showingPopUpOrDataTip(), viewsheetClient.sendEvent in updateRendered(), updateRendered() rendered-objects state across multiple scrollViewport changes
- Reason: asyncZones=5 (at threshold): three constructor subscriptions feed shared state; setTimeout defers dim-draw creating a one-frame race; viewsheetClient.sendEvent must fire once per newly-rendered object and not again on re-render; renderedObjects Map must not be cleared mid-update

**Pass 3** (`vs-object-container.component.display.tl.spec.ts`)
- Methods: isAssemblyVisible(), isFilterInMaxModeView(), isMiniToolbarVisible(), toolbarForceHidden(), isSelected(), isAtBottom(), isMaxModeHidden(), popupShowing getter, isPopupShowing(), isFocused(), viewer getter, getAssemblyAsClass(), getAssemblyDivId(), getToolbarTop(), getActionsWidth(), getToolbarLeft(), getToolbarWidth(), zIndex(), needsZIndexBoost(), getVsObjectPosition() (all 6 objectType branches), getPopUpContentBoostZIndex(), getPopDimZIndex(), getPopDimWidth(), getPopDimHeight(), getActualWidth(), getChartDataAnnotations(), isChartAnnotationSelected(), getChartAnnotationTetherTo(), getChartAnnotationRestrictTo(), isObjectRendered(), isActivePopComponent(), trackByName(), getVariablesValues()
- Reason: dispatchPoints=5: getVsObjectPosition branches on 6 objectType values with nested viewer/embeddedVS/maxMode/dropdown/container conditions; isMiniToolbarVisible branches on containerType + objectType; zIndex branches on adhocFilter + popSource + dataTipSource + assemblyAnnotations

### BindingEditor

**Pass 1** (`binding-editor.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngAfterViewInit, ngOnDestroy, assemblyName setter, bindingModel setter, variableValues setter, grayedOutFields setter, switchTab, updateData, updateFormat, updateChartMaxMode, openConsoleDialog, changeMessage
- Reason: Lifecycle hooks, input-driven state wiring, user-triggered tab switching, action dispatch, and the single async HTTP call with modal open

**Pass 3** (`binding-editor.component.display.tl.spec.ts`)
- Methods: showHighLowPane, isBound, popUpWarning, formatsInactive, formatsDisabled, formatPaneVisible, miniToolbarHeight, bindingType, isVS, sizeChanged, hideDcTip, tableBindingModel, crosstabBindingModel
- Reason: Three dispatch-heavy methods (showHighLowPane 8-way chart-type branch, isBound 4-type model branch, popUpWarning 3-level severity branch) plus pure computed getters

### VsWizardObjectComponent

**Pass 1** (`vs-wizard-object.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, select, editObject, onResizeStart, onResizeMove, onResizeEnd, onDragStart, onDragMove, onDragEnd, onMouseover, toEditMode, updateInteractable
- Reason: Lifecycle hooks, user-triggered drag/resize/select flows, and the editObject emit-vs-inline-edit branch are the core interaction paths

**Pass 3** (`vs-wizard-object.component.display.tl.spec.ts`)
- Methods: isVSWizardObject, canEdit, saveOriginalBounds, isBoundsChanged, updateFollowPositions
- Reason: isVSWizardObject has an 11-branch objectType dispatch driving template visibility; canEdit and isBoundsChanged are pure conditionals; updateFollowPositions has follow vs non-follow branching on CSS application

---

> **以下为 2026-06-24 补充扫描新增组件（103 个）**
> 来源：系统性缺口分析，VSObjectContainer Layer-3 子组件（selection / table / calendar / slider / output / 输入控件 / 形状等）、图表渲染 UI（graph/）、vsObject 配置弹窗（vsobjects/dialog/）、数据绑定编辑器（binding/）、共享库（shared/）。
> `logic_lines / dispatch / async_zones` 均为 `—`（待 prescan workflow 精确扫描）；`旧 spec` 列已按文件系统人工核查。

| 状态     | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划                                                                                                                                                                                                 |
|--------|------|-------------|----------|-------------|------|------|---------|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ✅已测试   | VSSelection | 2050 | 3 | 9 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-selection.spec.ts~~ | Two tests are .skip'd (pendingSubmit, listSelectedString); covers topPosition bottom-tab math and quick-switch overlay positioning in depth, but leaves ngOnInit subscriptions, actions event dispatch, navigate keyboard nav, updateSelectionState toggle/singleSelection logic, onReverse tree reversal, and onSelectAll entirely untested. | P1: VSSelection.interaction.tl.spec.ts<br>P2: VSSelection.risk.tl.spec.ts<br>P3: VSSelection.display.tl.spec.ts                                                                                         |
| ✅已测试   | VSSelectionContainer | 240 | 1 | 3 | **single-pass** | ✅ 推进 |  |  | ✅ vs-selection-container.component.tl.spec.ts (20 tests)                                                                                                                                                |
| ✅已测试   | VSSelectionContainerChildren | 364 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | ✅ vs-selection-container-children.component.tl.spec.ts (18 tests)                                                                                                                                       |
| ✅已测试   | SelectionListCell | 567 | 5 | 1 | **multi-pass** | ✅ 推进 | ⚠️ selection-list-cell.component.spec.ts | Covers quick-switch, long-press state machine, toggleFolder flag reset, selectRegion guard, onMouseEnter/Leave, ngOnDestroy, and showText/showBar/border DOM assertions — but does NOT cover getIconClass() STATE_* enum branches, setMeasureWidths() negative-bar geometry paths, ariaSelected/ariaExpanded accessors, onResizeCellHeight* flow, onDragStart, isHTML(), or setMenuCell. | P1: SelectionListCell.interaction.tl.spec.ts<br>P3: SelectionListCell.display.tl.spec.ts                                                                                                                |
| ⏸️暂缓   | CurrentSelection | 70 | 0 | 1 | **single-pass** | 暂缓 | ⚠️ current-selection.spec.ts | Single test checks only text-decoration CSS style via DOM query; onUnselect/sendEvent, updateEditState outside-click listener, dragStart stopPropagation, title2ResizeMove ratio math, ngOnDestroy subscription cleanup, and set-actions subscription routing are all uncovered. | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | CollapseToggleButton | 31 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试   | VSCrosstab | 1415 | 5 | 6 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-crosstab.component.spec.ts~~ | Only one test covering Bug #17211 (condition action event); no coverage of async flows, selectCell multi-mode logic, drill/expand actions, date-comparison HTTP calls, or column hide/show. | P1: vs-crosstab.interaction.tl.spec.ts<br>P2: vs-crosstab.risk.tl.spec.ts<br>P3: vs-crosstab.display.tl.spec.ts                                                                                         |
| ✅已测试   | VSTable | 820 | 4 | 6 | **multi-pass** | ✅ 推进 | ⚠️ vs-table.spec.ts | Covers column-width expansion, shrink/tab top-shift, visibility, isDraggable, flyover-clear, and cell text-decoration — none of the 20-case action dispatch switch, selectCell multi-modifier paths (ctrl/shift/plain), DnD drop flows, loadTableData command, or annotation flows are tested. | P1: vs-table.component.interaction.tl.spec.ts (35 tests)<br>P2: vs-table.component.risk.tl.spec.ts (12 tests)<br>P3: vs-table.component.display.tl.spec.ts (35 tests)                                   |
| ✅已测试   | PreviewTableComponent | 841 | 3 | 4 | **multi-pass** | ✅ 推进 |  |  | P1: preview-table.interaction.tl.spec.ts<br>P2: preview-table.risk.tl.spec.ts<br>P3: preview-table.display.tl.spec.ts                                                                                   |
| ✅已测试P1 | VSCalcTable | 709 | 2 | 2 | **multi-pass** | ✅ 推进 |  |  | P1: vs-calctable.interaction.tl.spec.ts (23 tests)                                                                                                                                                      |
| ✅已测试   | VSTableCell | 460 | 2 | 5 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试   | VSPreviewTable | 127 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSSimpleCell | 33 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ⏸️暂缓   | TableCellResizeDialogComponent | 47 | 0 | 2 | **single-pass** | 暂缓 |  |  | P1: table-cell-resize-dialog.component.interaction.tl.spec.ts                                                                                                                                           |
| ✅已测试   | VSCalendar | 1274 | 4 | 7 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-calendar.spec.ts~~ | Only 4 trivial cases covered (toggleDropdown model flag, updateTitle title string, objectHeight setter for dropdown/non-dropdown); HTTP formatting calls, actions-event dispatch, keyboard navigation, clearCalendar branching, syncRangeHighlight, and ngOnInit/ngOnDestroy lifecycle are entirely untested. | P1: vs-calendar.interaction.tl.spec.ts<br>P2: vs-calendar.risk.tl.spec.ts<br>P3: vs-calendar.display.tl.spec.ts                                                                                         |
| ✅已测试    | MonthCalendar | 1016 | 4 | 1 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-calendar.spec.ts~~ | Covers only the parent VSCalendar component (toggleDropdown, updateTitle, objectHeight); no MonthCalendar-specific tests (clickCell, getSelectionString, paintRange, syncPeriod, etc.) at all. | P1: month-calendar.interaction.tl.spec.ts<br>P3: month-calendar.display.tl.spec.ts                                                                                                                      |
| ✅已测试    | YearCalendar | 544 | 3 | 0 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-calendar.spec.ts~~ | YearCalendar is imported only as a declaration dependency for VSCalendar tests; no direct tests cover clickCell, getSelectionString, syncPeriod, paintRange, nextYear, or any other YearCalendar method. | P1: year-calendar.interaction.tl.spec.ts<br>P3: year-calendar.display.tl.spec.ts                                                                                                                        |
| ✅已测试    | VSRangeSlider | 963 | 6 | 3 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-range-slider.component.spec.ts~~ | Covers getCurrentLabel (upperInclusive separator), getContainerLabel "(none)", CSS decoration, showDialog for edit-range action, and position/CSS layout — does NOT cover mouseMove handle dispatch, navigate keyboard flows, date-type dialog init paths, or updateSelections submitOnChange branching. | P1: VSRangeSlider.interaction.tl.spec.ts<br>P2: VSRangeSlider.risk.tl.spec.ts<br>P3: VSRangeSlider.display.tl.spec.ts                                                                                   |
| ✅已测试    | VSSlider | 373 | 2 | 4 | **single-pass** | ✅ 推进 | ~~⚠️ vs-slider.spec.ts~~ | Only 3 tests (currentVisible DOM toggle, text-decoration style binding, vertical center position); drag interaction, keyboard navigation, applySelection form-data branching, snap logic, and tick computation are entirely absent. | single pass                                                                                                                                                                                             |
| ✅已测试   | VSText | 665 | 2 | 2 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-text.component.spec.ts~~ | Only 1 active test (shadow CSS class) and 1 skipped broken test; no coverage of ngOnInit/ngOnDestroy, sendChangeEvent debouncing, clicked/clickHyperlink flows, tooltip resolution in modelChanged, actions subscription routing, getEllipsisText, or external URL messaging. | P1: vs-text.component.interaction.tl.spec.ts (19 tests)                                                                                                                                                 |
| ✅已测试    | VSImage | 166 | 4 | 1 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-image.component.spec.ts~~ | Covers hyperlink self/non-self open (bug #17228), annotation overflow (bug #17807), shadow CSS class (bug #20755); two tests are .skipped (alpha, border); no coverage of getSrc() 3-branch URL construction, clicked() popComponent toggle, getOpacity() data-tip vs normal path, or ngOnChanges popComponent registration. | P1: VSImage.interaction.tl.spec.ts<br>P3: VSImage.display.tl.spec.ts                                                                                                                                    |
| ⏭ 已跳过  | VSGauge | 78 | 2 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ vs-gauge.component.spec.ts | getTooltip 3-branch logic (customTooltipString / hyperlinks[0].tooltip / defaultAnnotationContent) and getOpacity popComponent branch are untested. | single pass                                                                                                                                                                                             |
| ✅已测试    | VSComboBox | 450 | 4 | 2 | **multi-pass** | ✅ 推进 | ~~⚠️ vs-combo-box.spec.ts~~ | Covers only 2 narrow DOM assertions (date placeholder text, h-alignment); omits onChange/applySelection STOMP dispatch, clearCalendar, calendar/serverTZ date parsing, navigate() keyboard flow, and form-submit subscription. | P1: vs-combo-box.interaction.tl.spec.ts<br>P3: vs-combo-box.display.tl.spec.ts                                                                                                                          |
| ⏸️暂缓   | VSCheckBox | 80 | 1 | 0 | **single-pass** | 暂缓 | ⚠️ vs-check-box.spec.ts | Only tests CSS H/V alignment rendering; isSelected, onChange toggle branches (deselect/select, refresh/non-refresh, ctrlDown), and applySelection debounce/fallback are entirely untested. | P1: vs-check-box.interaction.tl.spec.ts                                                                                                                                                                 |
| ⏸️暂缓   | VSRadioButton | 75 | 1 | 0 | **single-pass** | 暂缓 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试    | VSTextInput | 306 | 2 | 3 | **single-pass** | ✅ 推进 | ~~⚠️ vs-text-input.component.spec.ts~~ | Only 3 tests (DOM structure, manual date input, skipped border); leaves submit/validation modal, onKey enter branching, navigate keyboard flow, setDateFromDatepicker, applySelection debounce, ngOnChanges subscription wiring, and selected setter all uncovered. | single pass                                                                                                                                                                                             |
| ✅已测试    | VSSubmit | 115 | 0 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ vs-submit.spec.ts~~ | Covers only vAlign padding and fade-assembly disabled class; omits onClick debounce/sendEvent/globalSubmitService flow, navigate() keyboard handling, and initFocusColor() border/background color derivation. | single pass                                                                                                                                                                                             |
| ✅已测试    | VSTab | 278 | 2 | 2 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试    | VSViewsheet | 380 | 1 | 0 | **single-pass** | ✅ 推进 | ⚠️ vs-viewsheet.component.spec.ts | Only covers showIconContainer/isChildDropdownExpanded edge cases; all command-processing methods (processAddVSObjectCommand, processRefreshVSObjectCommand, processRemoveVSObjectCommand, processUpdateZIndexesCommand), selectViewsheet, ngOnChanges, showContextMenu, and removeSelectedAnnotations are entirely untested. | P1: vs-viewsheet.component.interaction.tl.spec.ts                                                                                                                                                       |
| ✅已测试    | VSAnnotation | 247 | 1 | 3 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSHiddenAnnotation | 21 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSGroupContainer | 52 | 1 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ vs-group-container.spec.ts | Covers only the alpha/opacity binding (bug #20760); getSrc() URL construction and all three onImageLoad() aspect-ratio branches are untested. | single pass                                                                                                                                                                                             |
| ✅已测试   | VSSpinner | 204 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | ✅ vs-spinner.component.tl.spec.ts (45 tests)                                                                                                                                                            |
| ⏭ 已跳过  | VSThermometer | 28 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSCylinder | 18 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSSlidingScale | 17 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试    | VSLine | 171 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSOval | 19 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSRectangle | 17 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试    | MiniMenu | 104 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSTitle | 46 | 0 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ vs-title.component.spec.ts | Covers only the titleVisible display toggle (Bug #18906); ngOnChanges editingTitle reset, resize event emissions, and context-provider getters are untested but are trivial pass-throughs. | single pass                                                                                                                                                                                             |
| ⏸️暂缓   | TitleCell | 78 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | VSInputLabelWrapper | 55 | 0 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ vs-input-label-wrapper.component.spec.ts | onLabelClick EventEmitter emit is not tested; all other public contracts (12 property getters, 2 host bindings) are covered. | single pass                                                                                                                                                                                             |
| ✅已测试   | ChartArea | 1435 | 3 | 4 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: chart-area.component.interaction.tl.spec.ts (97 tests)<br>✅ P2: chart-area.component.risk.tl.spec.ts (19 tests: 15 pass + 4 it.fails)<br>✅ P3: chart-area.component.display.tl.spec.ts (75 tests) |
| ✅已测试 | ChartAxisArea | 675 | 3 | 4 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: chart-axis-area.component.interaction.tl.spec.ts (55 tests)<br>✅ P2: chart-axis-area.component.risk.tl.spec.ts (16 tests)<br>✅ P3: chart-axis-area.component.display.tl.spec.ts (41 tests)        |
| ✅已测试    | ChartPlotArea | 586 | 4 | 3 | **multi-pass** | ✅ 推进 | 🗑️ 已删除 chart-plot-area.component.spec.ts | Legacy spec deleted; its structural region-binding check was trivial (framework Input passthrough, not component logic), and its never-run `it.skip` full-area-selection case was ported into Pass 1 as a real (non-mocked) ChartTool.getTreeRegions geometry integration test. | ✅ P1: chart-plot-area.component.interaction.tl.spec.ts (45 tests)<br>✅ P2: chart-plot-area.component.risk.tl.spec.ts (17 tests)<br>✅ P3: chart-plot-area.component.display.tl.spec.ts (36 tests)        |
| ✅已测试    | ChartLegendContainer | 229 | 3 | 0 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: chart-legend-container.component.interaction.tl.spec.ts (12 tests)<br>✅ P3: chart-legend-container.component.display.tl.spec.ts (17 tests)                                                        |
| ⏸️暂缓   | ChartLegendArea | 51 | 1 | 0 | **single-pass** | 暂缓 |  |  | single pass                                                                                                                                                                                             |
| ⏸️暂缓   | ChartTitleArea | 56 | 0 | 0 | **single-pass** | 暂缓 |  |  | single pass                                                                                                                                                                                             |
| ✅已测试    | ChartTargetLinesPane | 150 | 2 | 2 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏸️暂缓   | ChartPlotOptionsPaneComponent | 74 | 0 | 2 | **single-pass** | 暂缓 |  |  | single pass                                                                                                                                                                                             |
| ⏸️暂缓   | AxisPropertyDialog | 57 | 0 | 0 | **single-pass** | 暂缓 | ⚠️ axis-property-dialog.spec.ts | Covers only incrementValid/minmaxValid with a single "correct value" case; defaultTab aliasTab→labelTab fallback and all three EventEmitter outputs (ok/close/apply) are untested. | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | LegendFormatGeneralPane | 97 | 2 | 1 | **single-pass** | ⏭ 跳过 | ⚠️ legend-format-general-pane.spec.ts | Only one it-block (Bug #10107 Ignore Null checkbox); borderStyle getter/setter 10-branch mappings and onValueChange→model.title are untested. | single pass                                                                                                                                                                                             |
| ✅已测试    | DateComparisonIntervalPaneComponent | 510 | 6 | 1 | **multi-pass** | ✅ 推进 |  |  | P1: date-comparison-interval-pane.interaction.tl.spec.ts<br>P3: date-comparison-interval-pane.display.tl.spec.ts                                                                                        |
| ✅已测试    | DateComparisonPaneComponent | 165 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ date-comparison-pane.component.tl.spec.ts (34 tests, 1 it.fails)                                                                                                                                      |
| ✅已测试    | DateComparisonDialog | 144 | 2 | 1 | **single-pass** | ✅ 推进 |  |  | ✅ date-comparison-dialog.component.tl.spec.ts (44 tests)                                                                                                                                              |
| ✅已测试    | DateComparisonStandardPeriodsComponent | 174 | 4 | 0 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: date-comparison-standard-periods.component.interaction.tl.spec.ts (23 tests)<br>✅ P3: date-comparison-standard-periods.component.display.tl.spec.ts (25 tests, 1 it.fails)                        |
| ✅已测试    | HierarchyPropertyPane | 489 | 1 | 0 | **single-pass** | ✅ 推进 | hierarchy-property-pane.spec.ts (kept — real-DOM clear-button rendering check, a different concern from the logic-level ATL suite) | Only one test covers the clear button DOM check; all drag-and-drop state mutation methods (columnDragStart, contentDragEnter, contentDrop, newDimensionDrop, columnDrop, contentDragLeave, removeDuplicateMembers), getMemberName's 16-case date-level switch, getInputClass grayed-out logic, initLocalColumnList filtering, and isDragAccepted/isDateDroppable/setDateLevel are completely uncovered. | ✅ hierarchy-property-pane.component.tl.spec.ts (85 tests)                                                                                                                                         |
| ✅已测试    | HierarchyEditor | 107 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | HyperlinkDialog | 320 | 1 | 4 | **single-pass** | ⏭ 跳过 | ⚠️ hyperlink-dialog.component.spec.ts | submit/ok/apply flows with trapService.checkTrap callbacks are untested; expressionChange and selectType methods have no coverage; HTTP error paths in getBookmarks and updateParameters are not exercised. | single pass                                                                                                                                                                                             |
| ✅已测试    | ChartPropertyDialog | 123 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | single pass                                                                                                                                                                                             |
| ⏸️暂缓   | TipPane | 99 | 0 | 2 | **single-pass** | 暂缓 | ⚠️ tip-pane.spec.ts | Covers disable/enable state, flyover checkbox toggling tipView reset, and selectAll/clearAll; does NOT test the dataTipChanged cycle-dependency subscribe path, the editTip modal open/resolve flow, or the constructor getObjectChange subscription setting objectAddRemoved. | single pass                                                                                                                                                                                             |
| ✅已测试    | RangeSliderDataPane | 280 | 2 | 0 | **single-pass** | ✅ 推进 | range-slider-data-pane.spec.ts (kept — real-DOM composite button + focus-after-delete, a different concern from the logic-level suite) | Existing TestBed spec covers composite-mode button enable/disable states and focus-after-delete; does not test selectColumn rangeType dispatch (CUBE_DIMENSION/numeric/TIME/date branches), switchType model resets, ngAfterViewInit column pre-population, or updateSourceType assemblySource logic. | ✅ range-slider-data-pane.component.tl.spec.ts (54 tests, 1 it.fails)                                                                                                                                    |
| ✅已测试    | RangeSliderEditDialog | 162 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | ✅ range-slider-edit-dialog.component.tl.spec.ts (37 tests)                                                                                                                                              |
| ⏭ 已跳过  | RangeSliderSizePane | 115 | 1 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ range-slider-size-pane.component.spec.ts | The TIME/TIME_INSTANT/MEMBER data-type branch in initRanges (selectedColumns[0].dataType paths) is not explicitly exercised; all other core paths are covered. | single pass                                                                                                                                                                                             |
| ✅已测试 | InputParameterDialog | 308 | 6 | 5 | **multi-pass** | ✅ 推进 | input-parameter-dialog.component.spec.ts (kept — real-DOM validation-feedback rendering, a different concern from the logic-level suite) | Covers default state, name validation, and ok() field fallback; omits changeType/changeValidators/updateDateTime/isInvalid dispatch branches and changeValueSource transitions. | ✅ P1: input-parameter-dialog.interaction.tl.spec.ts (30 tests, 3 it.fails)<br>✅ P2: input-parameter-dialog.risk.tl.spec.ts (11 tests)<br>✅ P3: input-parameter-dialog.display.tl.spec.ts (44 tests) |
| ✅已测试    | ComboBoxEditor | 285 | 2 | 2 | **single-pass** | ✅ 推进 | combo-box-editor.spec.ts (kept — real-DOM calendar/embedded button-disable rendering) |  | ✅ combo-box-editor.component.tl.spec.ts (76 tests) |
| ✅已测试    | VSConditionDialog | 206 | 0 | 2 | **single-pass** | ✅ 推进 | vs-condition-dialog.component.spec.ts (kept — dirty-condition confirm dialog flow via real TestBed rendering, a different concern from the logic-level suite) | Only one test: dirty-condition confirm dialog via conditionChanged+ok(); does not cover ngOnInit/highlightModel cloning, checkConditionTrap HTTP + trap-alert undo/ok branches, conditionListChanged→shouldCheckTrap field-set logic, getServerAppliedModel stripping, or apply/cancel emitters. | ✅ vs-condition-dialog.component.tl.spec.ts (35 tests)                                                                                                                                             |
| ⏭ 已跳过  | HideColumnsDialog | 91 | 2 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ hide-columns-dialog.spec.ts | remove() auto-selection index calculation (Math.min), multi-select via shift/ctrl key combinations, and apply()/cancel() EventEmitter emissions are not tested | single pass                                                                                                                                                                                             |
| ✅已测试    | FileFormatPane | 128 | 3 | 0 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: file-format-pane.component.interaction.tl.spec.ts (25 tests)<br>P3: file-format-pane.component.display.tl.spec.ts (15 tests)                                                                                                                      |
| ⏸️暂缓   | HighlightDialog | 70 | 0 | 1 | **single-pass** | 暂缓 | ⚠️ highlight-dialog.component.spec.ts | Only 1 active test (validateHighlights called on ok()); editConditions modal flow, checkTrap callbacks, apply/cancel emits, and ngOnInit auto-selection are all uncovered; several tests are commented out. | single pass                                                                                                                                                                                             |
| ⏭ 已跳过  | RichTextDialog | 37 | 0 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ rich-text-dialog.component.spec.ts | All three tests are effectively non-asserting: one is it.skip, two use unresolved fixture.whenStable().then() chains so assertions never execute; ok()/cancel() emission and ngOnInit content-copy logic are not covered. | single pass                                                                                                                                                                                             |
| ✅已测试    | CalcTableLayoutPane | 467 | 2 | 1 | **single-pass** | ✅ 推进 | vs-calc-table-layout.spec.ts (kept — selectCell/getCellContent through a hand-built instance predating this suite) | Covers only selectCell and getCellContent; skips clickCell (async/setTimeout acknowledged in comment); misses resize flow (resizeCell/onMouseMove/onMouseUp), all three command processors, createSpanMap/findBaseCell, addShiftCells/mergeDimension/validateDimension, getCellStyle/setSpanCellStyle, and loading-count state. | ✅ vs-calc-table-layout.component.tl.spec.ts (74 tests)                                                                                                                                                         |
| ✅已测试 | CalcTableCellComponent | 183 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ calc-table-cell.component.tl.spec.ts (11 tests) |
| ✅已测试 | SortOption | 230 | 2 | 4 | **single-pass** | ✅ 推进 | ~~⚠️ sort-option.spec.ts~~ | openDialog() check-variables + availableValues HTTP flow, manualOrder assembly, and getCurrentOrder manual detection now covered via MSW. | ✅ sort-option.component.tl.spec.ts (11 tests) |
| ✅已测试 | TableFieldmc | 366 | 4 | 0 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: table-fieldmc.component.interaction.tl.spec.ts (9 tests)<br>✅ P3: table-fieldmc.component.display.tl.spec.ts (13 tests) |
| ✅已测试 | CalcDataPane | 392 | 3 | 2 | **multi-pass** | ✅ 推进 | ~~⚠️ calc-data-pane.spec.ts~~ | Bug regressions (changeCellType, duplicate name, calc-field disabled, formulaValue getter, setGroupType date branch) ported to TL tests. | ✅ P1: calc-data-pane.interaction.tl.spec.ts (11 tests)<br>✅ P3: calc-data-pane.display.tl.spec.ts (16 tests) |
| ⏭ 已跳过  | CalcAggregateOption | 290 | 1 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ calc-aggregate-option.spec.ts | Covers all 4 dispatch paths in getPercents, isPercent gating, isTwoColumns/secondCol combobox, and formula list by data type (number/date/boolean); nStr setter validation and submit/apply EventEmitter emission are not tested. | single pass |
| ⏭ 已跳过  | CalcGroupOption | 344 | 2 | 3 | **single-pass** | ⏭ 跳过 | ⚠️ calc-group-option.component.spec.ts | One skipped test for date-level dropdown (DATE/TIME/TIME_INSTANT branch of getDateLevelOpts); HTTP flows in openManualDialog and getVariables are entirely uncovered. | single pass |
| ✅已测试 | TableDataEditor | 171 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ table-data-editor.component.tl.spec.ts (13 tests) |
| ✅已测试 | GroupOption | 256 | 2 | 1 | **single-pass** | ✅ 推进 |  |  | ✅ group-option.component.tl.spec.ts (17 tests) |
| ⏭ 已跳过  | ExpertNamedGroupDialog | 221 | 1 | 0 | **single-pass** | ⏭ 跳过 | ⚠️ expert-named-group-dialog.spec.ts | moveUp/moveDown/canUp/canDown reorder logic, deleteGroup index-boundary selection, clearAll, conditionListChange group-list mutation, and selectCondition getter are not tested — non-trivial edge cases not derivable from source alone. | single pass |
| ✅已测试 | CalculatePaneDialog | 436 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ calculate-pane-dialog.component.tl.spec.ts (16 tests) |
| ✅已测试 | CalculatePane | 407 | 2 | 3 | **single-pass** | ✅ 推进 |  |  | ✅ calculate-pane.component.tl.spec.ts (6 tests) |
| ✅已测试 | ChartFieldmc | 352 | 3 | 0 | **multi-pass** | ✅ 推进 |  |  | ✅ P1: chart-fieldmc.component.interaction.tl.spec.ts (19 tests)<br>✅ P3: chart-fieldmc.component.display.tl.spec.ts (45 tests) |
| ✅已测试 | GeoMappingDialog | 389 | 2 | 2 | **single-pass** | ✅ 推进 | ~~⚠️ geo-mapping-dialog.component.spec.ts~~ | Covers button disable states and changeAlgorithm delegation only; omits add()/remove() mutation logic, checkDuplicateMapping() duplicate detection, getFilteredFeatures() filter behavior, and onCommit/onCancel event emissions. | ✅ geo-mapping-dialog.component.tl.spec.ts (13 tests) |
| ✅已测试 | DimensionEditor | 294 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | ✅ dimension-editor.component.tl.spec.ts (21 tests) |
| ✅已测试 | GeoOptionPane | 180 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | ✅ geo-option-pane.component.tl.spec.ts (6 tests) |
| ✅已测试 | ChartDataEditor | 161 | 2 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ chart-data-editor.component.spec.ts~~ | Only one trivial test (grayedOutValues passthrough); isPrimaryField(), displayLabel getter, dragOverField() Mekko override, checkDropValid(), and getDropType() are entirely untested. | ✅ chart-data-editor.component.tl.spec.ts (11 tests) |
| ✅已测试 | ColorMappingDialog | 165 | 0 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ color-mapping-dialog.spec.ts~~ | Manual input toggle date-level label/value round-trip and Bug #21331 duplicate option dedupe covered in tl spec. | ✅ color-mapping-dialog.component.tl.spec.ts (9 tests) |
| ✅已测试 | ShapeFieldMc | 195 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ shape-field-mc.component.tl.spec.ts (11 tests) |
| ✅已测试 | CategoricalColorPane | 132 | 0 | 6 | **multi-pass** | ✅ 推进 | ~~⚠️ categorical-color-pane.spec.ts~~ | Existing spec covers palette dialog open + icon paging + color editor count via TestBed DOM; does not cover shareColorsChange HTTP sync, lazy-load colorMappingDialog, resetted emit on map diff, or showColorValueFrame. | ✅ P1: categorical-color-pane.interaction.tl.spec.ts (12 tests)<br>✅ P2: categorical-color-pane.risk.tl.spec.ts (4 tests) |
| ✅已测试 | ColorFieldMc | 140 | 2 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ color-field-mc.component.tl.spec.ts (10 tests) |
| ✅已测试 | TextFieldMc | 101 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ text-field-mc.component.tl.spec.ts (7 tests) |
| ✅已测试 | LinearColorPane | 173 | 2 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ linear-color-pane.component.spec.ts~~ | Only one test covering gradientModel.fromColor after GradientColorEditor.changeColor(); switchColorModel, setBrewerColor, resetEditors, syncColors, and all three model-name getters are entirely untested. | ✅ linear-color-pane.component.tl.spec.ts (9 tests) |
| ✅已测试 | NamedGroupPane | 339 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ named-group-pane.component.tl.spec.ts (7 tests) |
| ✅已测试 | FormulaOption | 157 | 0 | 0 | **single-pass** | ✅ 推进 | ~~⚠️ formula-option.spec.ts~~ | Only 2 bug-regression cases (Bug #19370 dynamic expression kept in secondaryColumnValue, Bug #20322 null formulaOptionModel shows combo); ngOnInit flow, changeFormulaValue warning dialog, npValueChange clamping, getAvailableFields CalculateRef exclusion, fixSecondaryColumn null-clearing, isFormulaEnabled, hasN/isByFormula/isNValid predicates are all untested. | ✅ formula-option.component.tl.spec.ts (9 tests) |
| ✅已测试 | ChartTypeButton | 179 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | ✅ chart-type-button.component.tl.spec.ts (10 tests) |
| ✅已测试 | ChartStylePane | 227 | 3 | 0 | **multi-pass** | ✅ 推进 | ~~⚠️ chart-style-pane.component.spec.ts~~ | Only tests stackEnabled() for CHART_BAR/CHART_PIE (Bug #19389/#19135); getCssIcon mapping, stackChecked, createStyles with custom types, stackChanged index preservation, stylesToRows row-chunking, multiChanged dual-emit, multiDisabled, getImageBorder, and applyClick are all uncovered. | ✅ P1: chart-style-pane.component.interaction.tl.spec.ts (8 tests)<br>✅ P3: chart-style-pane.component.display.tl.spec.ts (8 tests) |
| ✅已测试 | Slider | 227 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ slider.component.tl.spec.ts (8 tests) |
| ✅已测试 | RangeSlider | 154 | 1 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ range-slider.component.tl.spec.ts (7 tests) |
| ✅已测试 | BindingTreeComponent | 203 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | ✅ binding-tree.component.tl.spec.ts (9 tests) |
| ✅已测试 | CkeditorWrapperComponent | 167 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | ✅ ckeditor-wrapper.component.tl.spec.ts (11 tests) |

---

## 2026-06-24 补充扫描 Multi-Pass 详情（26 个）

### VSSelection

**Pass 1** (`VSSelection.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, set controller (subscription wiring), set actions (all event.id cases: unselect/hide/show/reverse/sort/search/max-mode/apply/remove-child/select-subtree/clear-subtree/select-all/menu-actions/format-pane/more-actions), toggleMaxMode, toggleSearchDisplay, onSearch, onShow, onHide, onSelectAll, onUnselect, onSort, onReverse (submitOnChange branches), updateTitle, updateCellHeight, updateColumns, updateMeasures, updateTitleRatio, processExpandTreeNodesCommand, folderToggled, showAllValues, expandList, dragStart, headerClick, miniMenuClosed, resized
- Reason: User-triggered flows, lifecycle wiring, and all action-event dispatches are the primary correctness surface for this component.

**Pass 2** (`VSSelection.risk.tl.spec.ts`)
- Methods: onSearchKeyUp (500 ms debounce race — searchTimer cleared on rapid keypresses, searchPending flag prevents model refresh mid-search), constructor scaleService + selectionMobileService subscriptions (maxSelectionChanged toggleMaxMode side-effect), ngOnInit globalSubmitService.globalSubmit + updateSelections subscriptions (submitOnChange branching, unappliedSelections push + applySelections), set controller unappliedSubject + updateViewSubject wiring (subscription replaced on reassignment, no leak), set actions actionSubscription replaced without leak, onShow mouseUpListener registration + cleanup on outside click, ngOnDestroy full teardown (all subscriptions, renderer listeners, overlay cleanup)
- Reason: Nine async zones create race conditions and subscription-leak risks that are distinct from happy-path interaction and require targeted async/timer testing.

**Pass 3** (`VSSelection.display.tl.spec.ts`)
- Methods: updateSelectionState (toggle/toggleAll/singleSelection matrix: VSSelectionList vs VSSelectionTree singleSelectionLevels, cellSelected+toggle keeps selected, dropdown auto-hide on singleSelection click), navigate (all FocusRegions: SEARCH_BAR/CLEAR_SEARCH/MENU/DROPDOWN/cell index, UP/DOWN/LEFT/RIGHT/SPACE per region, scroll-into-view logic), set model objectType dispatch (VSSelectionList path vs VSSelectionTree expandAll/scriptApplied path, controller creation vs reuse, adhocFilterListener wiring), disPlayZIndex (viewer+dropdown+maxMode+pop combinations), topPosition (all 7 branches: viewer non-dropdown, viewer dropdown atBottom, viewer dropdown inBottomTab collapsed/expanded/with-search, composer dropdown inBottomTab collapsed-search/no-search), updateSingleSelection, selectCell/unSelectCell/updateCellState, reverseSelectionList vs reverseSelectionTree0 recursive logic, selectAll (force/included/compatible filtering, composite recursion), getIdentifier (parentNode chain), addDataPath (CellRegion switch), updateListSelectedString (state bitmask 1/9/10), calcCellWidth (numCols clamping), getBodyHeight (container vs dropdown vs normal), getTitleWidth (border margin math), getMeasureRatio
- Reason: Three dispatch points each with 3+ branches produce a large label/state computation surface that is pure and fast to test in isolation.

### SelectionListCell

**Pass 1** (`SelectionListCell.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges, ngOnDestroy, click(), toggleFolder(), onTouchStart(), onTouchEnd(), onTouchMove(), onTouchCancel(), onMouseEnter(), onMouseLeave(), onDragStart(), onResizeCellHeightStart(), onResizeCellHeight(), onResizeCellHeightEnd(), onResizeMeasuresMove(), onResizeMeasuresEnd(), setMenuCell(), selectRegion(), clickLabel(), selectedCells setter, cancelLongPress()
- Reason: All lifecycle hooks and user-triggered interaction flows including resize, drag, touch, and selection state changes

**Pass 3** (`SelectionListCell.display.tl.spec.ts`)
- Methods: getIconClass(), ariaSelected, ariaExpanded, height getter, getTreeIconClass(), getCellTooltip(), isFolder(), isList, toggleEnabled, vsWizard, isHTML(), updateModelInfo() barY switch (top/bottom/default vAlign), setMeasureWidths() bar geometry branches (positive-only / mixed / negative-only), setTextWidths(), setLabelHeight()
- Reason: Pure conditional display logic: 5 dispatch points across icon/state/geometry computations drive 3+ code paths each and are not covered by the existing spec

### VSCrosstab

**Pass 1** (`vs-crosstab.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, loadTableData, set actions (switch dispatch for all 25+ cases), drillClicked, drillAction, drillHandle, hideColumn, showColumns, showCrosstabDateComparisonDialog (dialog open path), processClearSelectionCommand, showPagingControl
- Reason: Covers lifecycle hooks, command processing, user-triggered action dispatcher flows, and primary HTTP-backed dialog open path

**Pass 2** (`vs-crosstab.risk.tl.spec.ts`)
- Methods: showCrosstabDateComparisonDialog (dual subscribe: getModel + onClear race), showAnnotateDialog (subscribe chain), showInputNameDialog hasDuplicateCheck (HTTP call), keepScroll state across drill events, actionSubscription teardown and re-setup on repeated actions setter calls
- Reason: asyncZones=6; targets async races, subscription lifecycle bugs, and state inconsistency during concurrent async operations

**Pass 3** (`vs-crosstab.display.tl.spec.ts`)
- Methods: isSorted (all 5 switch values), selectCell (ctrl/shift/plain 3-path selection logic), getCell (colHeader/totalCol/grandTotalCol/summarySideBySide branching), drillHandle (DrillTarget enum: FIELD/CROSSTAB/NONE dispatch), isTipOverlapToolbar, showDCIcon, sortEnable, sortDimensionEnable, isFullHorizontalWrapper, getObjectHeight (shrink branch), dateComparisonTipStyle
- Reason: dispatchPoints=5; covers all pure conditional/label-computation branches driven by model state

### VSTable

**Pass 1** (`vs-table.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, actions setter (all 20+ switch cases: export, multi-select, annotate title/cell, show-details, filter, selection-reset/apply, form-apply, edit, highlight, conditions, sort-column/-aggregate, insert-row, append-row, delete-rows, open/close-max-mode, cell size, menu/more actions, show-format-pane), loadTableData, sortColumn, sortClicked, formInputChanged, applyFormChanges, applyTable, resetTable, changeCellText, addRow, deleteRows, rowLinkClicked, linkClicked, addAssemblyAnnotation, addCellAnnotation, processUpdateSortInfoCommand
- Reason: User-triggered flows and server command handling form the core interaction surface; the existing spec leaves all of these untested.

**Pass 2** (`vs-table.risk.tl.spec.ts`)
- Methods: positionDataAnnotations (zone.run + setTimeout async ordering), selectCell mobile-dataTip setTimeout branch, actionSubscription subscribe/unsubscribe lifecycle on repeated actions setter calls, ngOnDestroy subscription cleanup race, richTextService.showAnnotationDialog subscribe chains in annotate title and annotate cell actions
- Reason: asyncZones = 6; these paths involve nested zone.run/setTimeout and subscription lifecycle where missed cleanup or ordering bugs cause silent state corruption.

**Pass 3** (`vs-table.display.tl.spec.ts`)
- Methods: isSorted (switch: ASC/DESC/none), drawDropRect (4-branch if/else: left-edge/right-edge/vsWizardPreview/replace), selectCell modifier-key branching (ctrlKey path, shiftKey range-select path, plain click path, right-click-on-selected guard), getWrapperWidth (devicePixelRatio+Chrome+border+atRight path), getRowVisibility (1/2/0 return), isHeaderSortEnabled, isHeaderSortVisible, displaySortNumber, sortPositionNum, getTooltip (rowHyperlinks+dataTip+ctrlSelect suffix path), showPagingControl, getDetailTableTopPosition, getHeaderRowHeight (wrapped vs non-wrapped)
- Reason: dispatchPoints = 4; pure conditional/label-computation branches need isolated display-pass coverage to avoid bloating interaction tests.

### PreviewTableComponent

**Pass 1** (`preview-table.interaction.tl.spec.ts`)
- Methods: ngOnDestroy, ngAfterContentChecked, ngAfterViewChecked, onClick, horizontalScroll, resizeEnd (HTTP PUT via modelService), formatClicked (subscribe to formatGetter), sortClicked, touchVScroll, touchHScroll, verticalScrollHandler, wheelScrollHandler, clickLink, apply, dragStart, dragOverTable, onLeave, dropOnTable, changeCellText, openVisibilityContextMenu, resizeListener
- Reason: User-triggered flows, ngOnInit/ngOnDestroy lifecycle, HTTP loading via modelService.putModel and formatGetter.subscribe

**Pass 2** (`preview-table.risk.tl.spec.ts`)
- Methods: resizeEnd async HTTP race (two subscribe branches for isDetails flag), updateWidths setTimeout deferred updateColumnRange, tableData setter Promise.resolve microtask (scrollLeft restore + re-init), colWidths setter Promise.resolve microtask (initColumnWidths after width change)
- Reason: asyncZones >= 3: deferred microtasks and setTimeout in column-width pipeline can cause state inconsistency between scrollLeft restoration and column range recalculation

**Pass 3** (`preview-table.display.tl.spec.ts`)
- Methods: getSortLabel (3-way switch), sortClicked switch transitions, selectCell conditional paths (ctrlKey/shiftKey/row==0/col==-1), isHeaderValid, getCellLabel, isTableStyleApplied, isSelected, selectRectangle, deselectCell, clearSelection, isRowVisible, tableBodyWidth, isForceTab, getTarget, updateVerticalScrollTooltip (tooltip placement left vs right)
- Reason: dispatchPoints >= 3: pure conditional/label-computation methods with 3+ distinct branches each need dedicated display-logic coverage

### VSCalcTable

**Pass 1** (`vs-calctable.interaction.tl.spec.ts`)
- Methods: model setter, actions setter (all 13 switch cases), getToolbarActions, openMaxMode, closeMaxMode, updateLayout, ngOnDestroy, setupVerticalScrollWrapper, wheelScrollHandler, getColumnWidthSum, getCellWidth, getColGroupColWidths, getCellColSpan, linkClicked, selectCell (ctrl/shift/plain branches + flyover + dataTip paths), isSelected, isHyperlink, selectTitle, resizeHeaderCellWidth, loadTableData, resetLastColWidth, loadHeaderRows, getHeaderMaxRow, showAnnotateDialog, addAnnotation, positionDataAnnotations, transposeArray, decorateDataPath, checkScroll, horizontalScrollWidth getter, horizontalScrollbarWidth getter, isFullHorizontalWrapper getter, getRightmostCellObjectFormat, getDetailTableTopPosition, showPagingControl, updateTableHeight
- Reason: All component logic fits in one interaction pass: ngOnInit/ngOnDestroy lifecycle, the large actions switch dispatch, the selectCell multi-branch user interaction, loadTableData command handler, and derived layout/scroll helpers. asyncZones=2 (below threshold for a risk pass) and dispatchPoints=2 (below threshold for a display pass), so only Pass 1 is warranted.

### VSCalendar

**Pass 1** (`vs-calendar.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, actions setter (onAssemblyActionEvent dispatch for all 10 event ids), clearCalendar (submitOnChange branches), toggleDropdown, onShow, onHide, toggleDoubleCalendar, toggleYear, applyCalendar, applyCalendar0, updateTitle, headerClick, miniMenuClosed, selectedDatesChange, syncDateChange, syncPeriods, selectRange
- Reason: Covers lifecycle subscription wiring, all user-triggered action-event dispatch paths, and state mutations driven by user interaction

**Pass 2** (`vs-calendar.risk.tl.spec.ts`)
- Methods: updateFormatedSelectedString (HTTP POST + subscribe), updateCalendarTitleView / updateCalendarTitleView0 (setTimeout + HTTP POST + subscribe), updateSelectionString (conditional HTTP trigger and globalSubmitService.updateState), ngOnInit globalSubmit subscribe flush, onKeyUp pendingChange flush when ctrlDown released
- Reason: asyncZones=7; tests HTTP race conditions, setTimeout deferral, subscribe teardown on destroy, and the pending-change accumulation/flush pattern

**Pass 3** (`vs-calendar.display.tl.spec.ts`)
- Methods: navigate() all NavigationKeys branches (DOWN/UP/LEFT/RIGHT/SPACE) and all SelectionRegions sub-branches, focus() all selectedCellRow dispatch cases, syncRangeHighlight() all date-count conditional branches, topPosition getter (viewer/embeddedVS/dropdownCalendar/atBottom/inBottomTab combinations), height getter, isMiniBarSelected, getIconColor (static), getRangeString (static), appendRange (static), calendarComparator (static), getPendingIconPosition, getHTMLText, bottomTabFlipped
- Reason: dispatchPoints=4; tests pure label/icon computation, complex multi-branch getters, and all static utility functions

### MonthCalendar

**Pass 1** (`month-calendar.interaction.tl.spec.ts`)
- Methods: ngOnChanges, ngAfterViewInit, nextYear, nextMonth, clickCell, clickDayTitle, clickMonthTitle, updateSelectedDates, syncDate, syncPeriod, paintRange, updateSelected, resetDays, selectedDateChanged, addDataPath, removeDataPath, dates getter/setter
- Reason: User-triggered flows, lifecycle hooks, navigation, and selection mutations that emit output events

**Pass 3** (`month-calendar.display.tl.spec.ts`)
- Methods: getSelectionString, isBtnEnabled, checkCurrentDate, isInRange, isLeapYear, getStartWeek, dateChanged, getDayCellBackground, isSelectedDayCell, isMonthSelected, isRowSelectable, getDay, getWeekName, getMonth, ariaDateLabel, getDateArray, getCurrentDateString, getCalendarHeight, getSelectedDates, resetOldDate, vsWizard getter, isCellFocused
- Reason: Pure conditional logic, label/string computation, and display-state derivation driven by 4 dispatch points across dateType/range/navigation branches

### YearCalendar

**Pass 1** (`year-calendar.interaction.tl.spec.ts`)
- Methods: ngOnChanges, ngAfterViewInit, nextYear, clickCell, clickYearTitle, clearSelection, syncDate, syncPeriod, paintRange, resetOldDate
- Reason: User-triggered flows (cell click, year navigation, title click) plus lifecycle hooks that drive state mutations and emit events

**Pass 3** (`year-calendar.display.tl.spec.ts`)
- Methods: getYear, getMonth, getSelectionString, getSelectedDates, isYearSelected, checkCurrentDate, isInRange, isBtnEnabled, getDateArray, getCurrentDateString, updateSelected, isCellFocused, vsWizard getter, getCalendarHeight, addDataPath, removeDataPath
- Reason: Pure computation and display-path dispatching driven by model flags (minimum, secondCalendar, dateType, range) across 3+ distinct branches

### VSRangeSlider

**Pass 1** (`VSRangeSlider.interaction.tl.spec.ts`)
- Methods: ngOnInit (globalSubmit subscribe), actions setter (onAssemblyActionEvent subscribe + switch dispatch), updateSelections/updateSelections0, mouseDown, mouseUp, snapToSide, showAdvancedPaneDialog (ModelService HTTP + sendEvent), toggleMaxMode, onHide, onShow, updateTitle, title2ResizeEnd, updateTitleRatio, ngOnDestroy
- Reason: User-triggered flows, lifecycle setup/teardown, HTTP loading, and WebSocket send-event calls are the primary integration contracts.

**Pass 2** (`VSRangeSlider.risk.tl.spec.ts`)
- Methods: mouseMove (Left/Right/Middle boundary clamping and index rollback), mouseUp (click-vs-drag disambiguation via timeHandleClicked, selectionChanged guard), moveMiddle (left/right edge clamping), handleMoved (min/max clamping), updateSelections (submitOnChange true/false branching, _unappliedSelections deferred flush)
- Reason: asyncZones >= 3 and these paths involve state mutation under timing/ordering constraints where off-by-one or race conditions are the primary defect risk.

**Pass 3** (`VSRangeSlider.display.tl.spec.ts`)
- Methods: toIncrementTimestamp (y/m/d/t), labelToTimestamp (y/m/d/t with brace-escaped values), getDateStrings (y/m/d/t month-end calculation), extractTimeIncrement (regex/colon/dash-count heuristics), getCurrentLabel (0/1/many labels, upperInclusive), getContainerLabel (all-selected vs partial), navigate (all key + menuFocus + mouseHandle combinations), focusSelectedHandle, clearNavSelection, getLabelPosition (overflow left/right/center), getBodyHeight, calculatePositions
- Reason: dispatchPoints >= 3: six switch/if-else dispatch tables drive pure label computation and keyboard navigation display — all are deterministic pure-logic paths suited to display-pass coverage.

### VSText

**Pass 1** (`vs-text.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, ngOnChanges, ngAfterViewInit, ngAfterViewChecked, actions (setter), updateOnChange (setter), selected (setter), model (setter), changeText, sendChangeEvent, clicked, clickHyperlink, onKeyDown, onEnterDown, textChanged, getDisplayText, modelChanged, changeCursor, showAnnotateDialog, processForceEditModeCommand, processUpdateExternalUrlCommand, sendExternalUrls, sendMessageToExternalFrame, createExternalUrlsMessage, getExternalFrameOrigin, updateUrlText, changeHeightOfTextarea, getContentSize, getAutoTextModel, presenter (getter), width (getter), whiteSpace (getter), isForceTab, hasPopComponentChange, isHTMLContent, getEllipsisText, getNoWordString, getWordString, updateClientSize, getNoWrapMaxWidth, unsubscribe, detectChanges, isPopupOrDataTipSource
- Reason: All methods assigned to interaction pass since asyncZones < 3 (no risk pass) and dispatchPoints < 3 (no display pass); covers lifecycle hooks, user-triggered flows, debounced text change, hyperlink click routing, external URL iframe messaging, and tooltip resolution.

### VSImage

**Pass 1** (`VSImage.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges, ngOnDestroy, clicked, onAssemblyActionEvent
- Reason: Lifecycle hooks, user-click flow (popComponent toggle + sendEvent), and action dispatch are the primary interaction surface

**Pass 3** (`VSImage.display.tl.spec.ts`)
- Methods: getSrc, getOpacity, onImageLoad, hasPopComponentChange, isForceTab, finishLoad, isPopupOrDataTipSource
- Reason: 4 dispatchPoints across getSrc (3-branch URL), getOpacity (data-tip vs normal), onImageLoad (scaleImage + preserveAspectRatio), and modelChanged tooltip selection drive display-path coverage

### VSComboBox

**Pass 1** (`vs-combo-box.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges, ngOnDestroy, model setter (init/valueChanged paths), onChange, onBlur, onInputDate, onEnter, selectItem, toggleDropdown, applySelection, clearCalendar, updateDate, selectLabel, clearLabelSelection, onKeyUp, onKeyDown, labelSearch
- Reason: User-triggered flows and lifecycle hooks that drive STOMP events, form subscriptions, and debounce dispatches

**Pass 3** (`vs-combo-box.display.tl.spec.ts`)
- Methods: inputPlaceholder, navigate, focusOnRegion, clearNavSelection, showCalendar, showTime, getDateString, getDate, getTimeInstant, getDateTime, isValidDate, isSelected, getLabelIndex, getValueIndex, updateHours, updateMinutes, updateSeconds, updateMeridian, applyDateSelection (conditional branches)
- Reason: Multi-branch dispatch points: 8-case focusOnRegion switch, 4-branch navigate() key handler, dataType-driven inputPlaceholder, and calendar/time conditional display logic

### ChartArea

**Pass 1** (`chart-area.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, ngOnChanges, model setter (subscription setup/teardown), selected setter, resetScrollPosition, clearSelection, selectRegion, sendFlyover, showDataTip, showTooltip, drill, showHyperlink, sortAxis, onWrapperScroll, onScrollArea, onScrollAxis, onDown, onMove, onDrop, dragOver, dragLeave, onAxisDrop, onAxisEnter, onAxisLeave, showPagingControl, pageDown, pageUp, pageLeft, pageRight, touchStart, touchMove, touchEnd, endAreaResize, startAxisResize, startLegendResize, endAxisResize, endLegendResize, endLegendMove, axisLoading, axisLoaded, plotLoading, plotLoaded, clearPan, changeCursor, selectLegendBackground, clearSelectionOnBackground
- Reason: Core lifecycle flows: subscription setup/teardown on model reassignment, user interaction emissions, scroll synchronization, and loading state transitions are the highest-value integration scenarios.

**Pass 2** (`chart-area.component.risk.tl.spec.ts`)
- Methods: model setter (rapid reassignment / subscription leak), clearCanvasSubject subscription (flyoverApplied race), scrollTop/scrollLeft subscriptions (stale assembly name guard), axisLoading/_loadingAxesSet cycle reset (Bug #74260), fireLoading/fireLoaded concurrent axis+plot state, axisLoaded sentinel empty-string path, ngOnDestroy subscription cleanup
- Reason: asyncZones=4: overlapping subscriptions on rapid model swaps, flyoverApplied flag inconsistency when two charts are mutual flyover targets, and _loadingAxesSet stale-entry logic are real race/state-inconsistency risks.

**Pass 3** (`chart-area.component.display.tl.spec.ts`)
- Methods: isDropAcceptable (dropType X/X2/Y/Y2/GEO branching, multiStyles bypass, cubeType vs bindingModel path), drawDropRegion (region hit-detection for all 5 drop zones, legend overlay, Y2 unsupported null-box), selectRegion (multiSelect/isCtrl vs isShift vs plain, radar point expansion, map MapSelectionEnabled guard), paintNoDataChart, noData getter, isY2Supported (14-chart-type exclusions), chartContainerWidth/Height/Top/Left (isVSChart vs non-VSChart padding), getBorderWidth, getPlotErrorStyle, chartContainerVisible, isNavMap, isDrawAxisBorder, isXAcceptable, isYAcceptable, isGeoAcceptable, getLegendContentHeight
- Reason: dispatchPoints=3: drop-type branching in isDropAcceptable and drawDropRegion have many exclusion conditions; selectRegion multi-mode logic and isY2Supported 14-type exclusion list are pure conditional paths best verified in isolation.

### ChartAxisArea

**Pass 1** (`chart-axis-area.interaction.tl.spec.ts`)
- Methods: ngOnChanges, onDown, onUp, onClick, onDblClick, onLeave, onMove, onScroll, clickSort, drillDown, drillUp, showDrill, hideDrill, hoverOverDrill, onAltDown, onAltUp, loaded, loading, updateChartObject
- Reason: User-triggered interactions, pointer/mouse event flows, drill/sort/hyperlink emission, scroll synchronization, and lifecycle change detection

**Pass 2** (`chart-axis-area.risk.tl.spec.ts`)
- Methods: updateCanvas (debounce -> ngAfterViewInit), onDown mouseUpResize listener registration, clearDocMouseListener, clearResizeStatus, onMove debounce for dataTip, onLeave debounce for dataTip
- Reason: Async races: debounce-scheduled canvas update, renderer.listen document mouseup listener lifecycle (leak risk if clearDocMouseListener not called), and concurrent debounce calls in onMove/onLeave for data tip emission

**Pass 3** (`chart-axis-area.display.tl.spec.ts`)
- Methods: cursor getter, emitCursor, createAxisResizeInfo, getSortIconTop, getDrillIconLeft, getDrillIconPosition, showSortIcon, minWidth, canvasX, canvasY
- Reason: Pure conditional/display computations driven by areaName switch (4 branches each in cursor and emitCursor) and axisType conditionals; min/max bounds calculations in createAxisResizeInfo across all 4 axis positions

### ChartPlotArea

**Pass 1** (`chart-plot-area.interaction.tl.spec.ts`)
- Methods: ngOnChanges (scroll debounce), onDown/onUp (pan mode start/end + onPanMap emit), onSelectionBox→selectChart (region emit, hyperlink emit, flyover emit, dataTipOnClick path), onContextMenu (unselected-region select), onLeave (debounce cancel + dataTip clear + flyover clear), onScroll (scroll sync emit), updateChartObject (canvas clear + selection redraw), loaded (success path + HTTP error path with modal/embed branch)
- Reason: Core user-triggered flows and the one HTTP side-effect (error recovery GET) all live here; this is the highest-value pass.

**Pass 2** (`chart-plot-area.risk.tl.spec.ts`)
- Methods: loaded (destroyed guard prevents modal after teardown, debounce suppresses duplicate errors), getSrc (oSrc change → fireOnLoading, same src → fireOnLoad, ignoreLoadEvent bypass), fireOnLoading/fireOnLoad (tile visibility + loaded=false gate), plotScaleInfo setter (clear vs draw branch, vertical vs horizontal scale)
- Reason: asyncZones=3; these methods have subtle state-ordering invariants (destroyed flag, oSrc string comparison, tile.loaded boolean) that can cause silent regressions under race/teardown conditions.

**Pass 3** (`chart-plot-area.display.tl.spec.ts`)
- Methods: onMove cursor paths (link match → pointer, hyperlinks → pointer, hasEmptyPlotLinkModel → pointer, else → inherit), changeCursor0 (altDown suppresses cursor, zone.run path), onAltDown/onAltUp (altDown toggle + cursor reset), getSingleClickRegions (text present filters vo; no-text takes first region only), selectIntersect (selectRows union expansion), scrollContainerWidth/scrollContainerHeight (scrollbar addition condition), isMaxModeHidden (sheetMaxMode + maxMode flags), click (mobile double-tap selectionBoxWithTouch toggle + setTimeout reset)
- Reason: dispatchPoints=4; all pure conditional/display logic is isolated here to keep interaction and risk passes focused.

### ChartLegendContainer

**Pass 1** (`chart-legend-container.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges, startMoveOrResize, endMoveOrResize, onMove, buildOutlineRectangle (drag outline flow), draggedEnoughDistance
- Reason: Covers lifecycle hooks, move/resize state transitions, and the drag interaction pipeline — these are the primary user-triggered flows

**Pass 3** (`chart-legend-container.component.display.tl.spec.ts`)
- Methods: getResizeEdges (5-case switch on legendOption), createLegendResizeInfo (4-case switch computing min/max), buildOutlineRectangle edge-area classification (5-branch if-else: TOP/RIGHT/BOTTOM/LEFT/CENTER)

### DateComparisonIntervalPaneComponent

**Pass 1** (`date-comparison-interval-pane.interaction.tl.spec.ts`)
- Methods: ngOnInit (firstDayOfWeekService subscription), updateLevelType, updateGranularityType, updateContextLevelType, updateVisibleLevel (with granularity reset side-effect), updateVisibleGranularity, updateContextLevel, isValidInterval, verifyEndDate, toDateIsValue, isEndDateDisable getter, showEndDate, showInclusive
- Reason: Covers component initialization (HTTP subscribe), all user-triggered model mutation methods, and boolean guards that react to model state changes

**Pass 3** (`date-comparison-interval-pane.display.tl.spec.ts`)
- Methods: visibleIntervalLevel getter, visibleGranularity getter, contextLevelValue getter, getIntervalLevels (7-branch dispatch on standardPeriodLevel + isCustomPeriod), getGranularities (7-case switch on level), granularitiesAllIntervalVisible (5-case switch on periodLevel.value), getContextLevels (4-branch dispatch on isValueType x intervalValueType), intervalLevelConvertToGroupLevel (5-branch bit-mask dispatch), toDateLabel getter (8-case outer switch with nested inner switches), toDate getter, getToDateLabel
- Reason: Pure computation methods with 6 distinct dispatch points across level/granularity/context filtering; all return display values or filtered arrays with no async involvement

### DateComparisonStandardPeriodsComponent

**Pass 1** (`DateComparisonStandardPeriodsComponent.interaction.tl.spec.ts`)
- Methods: ngOnChanges, updateVisibleLevel, updateLevelType, updatePreviousCountValue, updatePreviousCountType, updateValid, isValid, isInvalidStandardPeriodPreCount, isValidStandardPeriodEndDay, verifyEndDate, showInclusive, endDate (getter)
- Reason: User-triggered model mutations and validation flows that emit validChange; ngOnChanges lifecycle; endDate getter drives downstream display but also has branching guard logic tested via interaction

**Pass 3** (`DateComparisonStandardPeriodsComponent.display.tl.spec.ts`)
- Methods: visibleStandardPeriodLevel (getter), toDateLabel (getter), toDateVisible (getter), inclusiveLabel (getter), dayOfWeek (private), monthOfQuarter (private)
- Reason: Pure conditional/label computation via 4 dispatch points (visibleStandardPeriodLevel if/else-if, toDateLabel 5-case switch, inclusiveLabel 5-case switch, dayOfWeek 8-case switch); no async; all paths testable by varying @Input model values

### InputParameterDialog

**Pass 1** (`input-parameter-dialog.interaction.tl.spec.ts`)
- Methods: ngOnInit (form wiring + all valueChanges subscriptions), ok(), close(), changeValueSource(), changeName(), model setter (initial branch guards)
- Reason: User-triggered flows and lifecycle initialization

**Pass 2** (`input-parameter-dialog.risk.tl.spec.ts`)
- Methods: timeValue debounce (1000ms pipe + distinctUntilChanged), concurrent updateDate/updateTime/updateDateTime sequencing, model setter side-effects when form not yet initialized
- Reason: asyncZones=5 warrants async-race coverage: debounce timing, overlapping date/time updates, and pre-form setter guard

**Pass 3** (`input-parameter-dialog.display.tl.spec.ts`)
- Methods: changeType() (6 branches), updateValue() (5 branches), changeValidators() (9 branches), updateDateTime() (3 branches), isInvalid() (4 branches via switch), isGrayedOut(), isFormInvalid(), hasViewsheetParameters(), invalidDate()
- Reason: dispatchPoints=6 warrants dedicated display/conditional pass for all pure type-dispatch methods

### FileFormatPane

**Pass 1** (`FileFormatPane.interaction.tl.spec.ts`)
- Methods: ngOnInit, changeFormatType, updateOnlyDataComponents, selectBookmark, selectAll, clearAll
- Reason: User-triggered flows: format type initialization from exportTypes input, CSV error dialog, HTML bookmark reset, bookmark multi-select (shift/ctrl/plain click), select-all and clear-all actions

**Pass 3** (`FileFormatPane.display.tl.spec.ts`)
- Methods: getExport, matchLayoutVisible
- Reason: Pure conditional/label computation: 7-way string-to-enum mapping in getExport drives rendered type values; matchLayoutVisible 3-condition boolean drives whether matchLayout checkbox appears

### TableFieldmc

**Pass 1** (`table-fieldmc.interaction.tl.spec.ts`)
- Methods: constructor, field setter/getter, openFieldOption, toggled, processChange, changeAllRows, changeColumnValue, dragStart, getSourceAttr, getAllRows, tableBindingModel getter
- Reason: Covers user-triggered flows (dialog open, drag, column value change), lifecycle wiring, and state mutations that result from interaction events

**Pass 3** (`table-fieldmc.display.tl.spec.ts`)
- Methods: cellValue, tooltip, getFieldName, getFieldClassType, getTitle, showFieldOption, isAllRows, isEditEnable, isCrosstab, crosstabOption, getSource, getIndex, isOuterDimRef, isLastItem, getAggregateFullName, syncAgg, getColumnValue, setColumnValue
- Reason: Covers all label/value computation getters and conditional display logic: cellValue has 3+ branches on implyDynamic/comboType/field type; getAggregateFullName has 4 paths on formula shape; showFieldOption has 3 guard conditions; isEditEnable branches on fieldType and cube type

### CalcDataPane

**Pass 1** (`calc-data-pane.interaction.tl.spec.ts`)
- Methods: bindingModel setter (valueList population), columnValue setter (empty vs non-empty branch), setCellValue, setGroupType, setSumType, setExpansionValue, setExpansion, setCellName, changeCellType, openFormulaEdit, toggled, changeGroup, toggleDropdown
- Reason: User-triggered flows including setter side-effects, modal open via ComponentTool.showDialog, setTimeout-deferred setCellBinding calls, and form-field wiring

**Pass 3** (`calc-data-pane.display.tl.spec.ts`)
- Methods: formulaValue getter (BIND_COLUMN / BIND_FORMULA / other branches), field getter, isCalcField, isGroupAggregateDisabled, cellName getter, expansionModel getter, isGroup getter, isSum getter, columnValue getter, textValue getter, cellBindingEnabled getter, cellSelected getter, cellGroupEnabled getter, isGrayedOut, getDefaultFormula (number vs non-number type), getSelectedRef, isValidName, checkDeleteKey (switch 46/67+ctrl/86+ctrl/88+ctrl/68+ctrl paths)
- Reason: Pure conditional / label-computation logic: 3-branch formulaValue, checkDeleteKey switch dispatch, and all boolean getter derivations that drive template state

### ChartFieldmc

**Pass 1** (`chart-fieldmc.component.interaction.tl.spec.ts`)
- Methods: field setter (clone + _originalField), changeColumnValue, openChange (close branch: manualOrder reset + dateComparison confirm dialog + processChange fallback), processChange, changeAggregateFormula, convert, changeChartRef, dragStart
- Reason: Covers user-triggered mutation flows, NgbModal confirm dialog interaction, and the DnD drag-start branching on fieldType

**Pass 3** (`chart-fieldmc.component.display.tl.spec.ts`)
- Methods: cellLabel, cellValue, imgOpacity, isRefConvertEnabled, isSortSupported, isOuterDimRef (xfields path, yfields path, stock/candle edge cases), isVisibleChartTypeButton (4 return paths), geoBtnVisible, convertBtnTitle, getTitle, getFieldClassType, isEditMeasure, isChartRefMeasure, strippedDrillmemberVariables, isSecondaryAxisSupported, chartType, multiStyles, stackMeasures, bindingModel, params, aggregateField, getDisplayLabel
- Reason: dispatchPoints=3 driven by cellValue (4 branches), isOuterDimRef (xfields/yfields/stock-candle), and isVisibleChartTypeButton (3+ return paths) — pure conditional/label computation with no HTTP or subscriptions

### CategoricalColorPane

**Pass 1** (`categorical-color-pane.interaction.tl.spec.ts`)
- Methods: ngOnInit (getColorPalettes HTTP load), clickPaletteButton (dialog open/close + openDialog emit), clickColorMappingButton (lazy-load branch + cached branch), openColorMappingDialog (result callback: colorMaps update, useGlobal branch, dateFormat set, resetted emit on diff), applyClick, changeColor, reset, isResetted, getNumItems, getFrame, isDimension, isVS, showColorValueFrame
- Reason: Covers all user-triggered flows, HTTP loading on init, and dialog orchestration paths

**Pass 2** (`categorical-color-pane.risk.tl.spec.ts`)
- Methods: shareColorsChange (HTTP subscribe fires after share flag set — model sync race), resetted emit guard (only when Tool.isEquals differs), setTimeout openDialog false timing (4 occurrences), constructor getCustomChartFrames subscribe (completes after component init)
- Reason: asyncZones=6 triggers Pass 2; these paths involve async state races, conditional event emission, and timer-deferred emissions that are prone to ordering bugs

### ChartStylePane

**Pass 1** (`chart-style-pane.component.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnChanges, createStyles (with/without chartStyles and customChartTypes), stackChanged (index preservation), stackMeasuresChanged, updateChartType, multiChanged, applyClick, getItemIndex
- Reason: User-triggered flows and lifecycle hooks that drive Output emissions and internal state transitions

**Pass 3** (`chart-style-pane.component.display.tl.spec.ts`)
- Methods: getCssIcon (40+ case switch on item.data), stackEnabled (7-branch chartType check), stackChecked (7-branch chartType check), getImageBorder (selected vs unselected), multiDisabled, stylesToRows (row-chunking at 6 items)
- Reason: Pure conditional/label/icon computation methods with 3+ dispatch branches each
- Reason: Three distinct dispatch points each driven by legendOption or spatial position — pure conditional logic that maps inputs to output labels/values
