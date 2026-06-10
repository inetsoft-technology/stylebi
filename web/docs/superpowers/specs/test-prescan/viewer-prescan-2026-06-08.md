# viewer Route Pre-scan Report

**日期**: 2026-06-08（2026-06-10 补充扫描，新增 11 个组件）
**候选组件数**: 39 | **建议推进**: 38 | **建议跳过**: 1 | **多 pass 组件**: 8

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
| 待审核 | ViewerRootComponent | 32 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ViewerViewComponent | 261 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ViewerEditComponent | 188 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | AiAssistantPanelComponent | 177 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | DownloadTargetComponent | 84 | 1 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | PageTabComponent | 98 | 0 | 3 | **single-pass** | ✅ 推进 | ⚠️ page-tab.component.spec.ts | 无 | single pass (+竞态+内存泄漏) |
| 待审核 | ViewerAppComponent | 3363 | 10 | 53 | **multi-pass** | ✅ 推进 | ⚠️ viewer-app.spec.ts | Bug #16456 TODO, logica changed, can not get fixed dropdown pane; Bug #19176 hide full screen in preview; Bug #16961 should refresh scale to screen vs | P1: ViewerAppComponent.interaction.tl.spec.ts<br>P2: ViewerAppComponent.risk.tl.spec.ts<br>P3: ViewerAppComponent.display.tl.spec.ts |
| 待审核 | VSBindingPane | 805 | 4 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: VSBindingPane.interaction.tl.spec.ts<br>P2: VSBindingPane.risk.tl.spec.ts<br>P3: VSBindingPane.display.tl.spec.ts |
| 待审核 | VsWizardComponent | 371 | 0 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | PagingControlComponent | 84 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ViewerMobileToolbarComponent | 72 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | VsBookmarkPaneComponent | 97 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ExportDialog | 103 | 1 | 3 | **single-pass** | ✅ 推进 | ⚠️ export-dialog.spec.ts | Bug #17235 should not; test: should show error | single pass (+竞态+内存泄漏) |
| ✅已测试 | EmailDialog | 103 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | ScheduleDialog | 106 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | BookmarkPropertyDialog | 58 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ProfilingDialog | 210 | 2 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | RemoveBookmarksDialog | 69 | 0 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | VSFormatsPane | 679 | 4 | 1 | **multi-pass** | ✅ 推进 | ⚠️ vs-formats-pane.spec.ts | Bug #16685, Bug #16689 check the aligment combox status; Bug #18597, BUg #18664 color,border, aligment status; Bug #18060, Bug #18342 for wrap text on | P1: VSFormatsPane.interaction.tl.spec.ts<br>P3: VSFormatsPane.display.tl.spec.ts |
| 待审核 | ShareEmailDialogComponent | 79 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ShareGoogleChatDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | ShareSlackDialog | 52 | 0 | 3 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |
| 待审核 | NotificationsComponent | 65 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ notifications.component.spec.ts | 无 | single pass (+内存泄漏) |
| 待审核 | VsWizardPane | 901 | 1 | 7 | **multi-pass** | ✅ 推进 |  |  | P1: VsWizardPane.interaction.tl.spec.ts<br>P2: VsWizardPane.risk.tl.spec.ts |
| 待审核 | ObjectWizardPane | 556 | 1 | 3 | **multi-pass** | ✅ 推进 |  |  | P1: ObjectWizardPane.interaction.tl.spec.ts<br>P2: ObjectWizardPane.risk.tl.spec.ts |
| 待审核 | ActionsContextmenuComponent | 154 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ actions-contextmenu.component.spec.ts | test: should not create a dropdown when there are no visible actions | single pass (+内存泄漏) |
| 待审核 | VariableInputDialog | 214 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ variable-input-dialog.spec.ts | Bug #16824 Make sure the default value of a boolean type is false | single pass |
| 待审核 | EditGeographicDialog | 47 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |

---

> **以下为 2026-06-10 补充扫描新增组件**

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| 待审核 | VSObjectContainer | 708 | 5 | 5 | **multi-pass** | ✅ 推进 |  |  | P1: VSObjectContainer.interaction.tl.spec.ts<br>P2: VSObjectContainer.risk.tl.spec.ts<br>P3: VSObjectContainer.display.tl.spec.ts |
| 待审核 | ShareLinkDialog | 35 | 0 | 0 | **single-pass** | ⏭ 跳过 |  |  | single pass |
| 待审核 | BindingEditor | 283 | 3 | 2 | **multi-pass** | ✅ 推进 |  |  | P1: BindingEditor.interaction.tl.spec.ts<br>P3: BindingEditor.display.tl.spec.ts |
| 待审核 | VSObjectView | 206 | 1 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | WizardToolBarComponent | 110 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ wizard-tool-bar.component.spec.ts | Only a single "should create" smoke test; no coverage of cancel() modal dialog flow, done(), undo()/redo() dispatch, undoEnabled/redoEnabled getter logic, or hiddenNewBlockChanged() output emission. | single pass |
| 待审核 | VsWizardObjectComponent | 324 | 3 | 1 | **multi-pass** | ✅ 推进 |  |  | P1: VsWizardObjectComponent.interaction.tl.spec.ts<br>P3: VsWizardObjectComponent.display.tl.spec.ts |
| 待审核 | WizardNewObject | 83 | 0 | 0 | **single-pass** | ✅ 推进 |  |  | single pass |
| 待审核 | ObjectWizardToolBarComponent | 96 | 1 | 2 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |
| 待审核 | WizardBindingTree | 421 | 2 | 4 | **single-pass** | ✅ 推进 | ⚠️ wizard-binding-tree.component.spec.ts | Only a single "should create" smoke test; no coverage of selectNodes, recommender, flatternTree, command processors, or context-menu flows — effectively empty. | P1: WizardBindingTree.interaction.tl.spec.ts (+竞态+内存泄漏) |
| 待审核 | VSWizardAggregatePane | 293 | 1 | 0 | **single-pass** | ✅ 推进 | ⚠️ wizard-aggregate-pane.component.spec.ts | Spec 是 stub，仅 'should create'，无方法覆盖。 | single pass |
| 待审核 | VSWizardPreviewPane | 63 | 0 | 0 | **single-pass** | ✅ 推进 | ⚠️ wizard-preview-pane.component.spec.ts | Only a single "should create" smoke test; does not cover changeDescription, setPreviewPaneSize, hasLegend, or processRefreshDescriptionCommand. | single pass |

## 多 pass 组件详情

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

### VSFormatsPane

**Pass 1** (`VSFormatsPane.interaction.tl.spec.ts`)
- Methods: focusedAssemblies, filter, color, get, colorType, backgroundColor, backgroundColorType, format, getFont, getAlignment, changeColor, isViewsheetSelected, isValueFillVisible, isNonEditableChartVOSelected, isFontDisabled, isEditDisabled, isChartEditableSelected, isAlignDisabled, isHAlignmentEnabled, isVAlignmentEnabled, isWrapTextDisabled, isBorderDisabled, isRoundCornerDisabled, isColorDisabled, isBackgroundDisabled, isDynamicColorDisabled, isCSSDisabled, updateCSS, changeAlphaWarning, updatePresenter, updatePresenterProperties, getComboMode, measureBarSelected, isInputType, selectedDetailCell, updateProperties, reset, openPresenterPropertyDialog, tableSelected, textSelected, shapeSelected, borderTooltip, roundCornerMax, isRoundTopCornersOnlyVisible
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 3** (`VSFormatsPane.display.tl.spec.ts`)
- Methods: getFormat, getColorLabel, closeFormat, isFormatDisabled, updateFormat, getBorderLabel, isFormattingDisabled, getCSSLabel, showPresenter
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

---

## 补充扫描新增 multi-pass 组件详情（2026-06-10）

### VSObjectContainer

**Pass 1** (`VSObjectContainer.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, ngOnChanges, ngOnDestroy, keyNavigation setter, scrollViewport setter, select(), showContextMenu(), showToolbar(), openWizardPane(), annotationMouseSelect(), removeAnnotationFromOverlay(), onMouseEnter(), submitClicked(), onMaxModeChange()
- Reason: Lifecycle hooks, user-triggered events (click/select/context-menu/drag), input setters that trigger side effects (containerRef scroll listener wiring, updateRendered on scrollViewport change), and EventEmitter output assertions

**Pass 2** (`VSObjectContainer.risk.tl.spec.ts`)
- Methods: constructor subscription setup (scaleService.getScale, popService.componentPop, dataTipService.showHideDataTip), obs.subscribe in keyNavigation setter, setTimeout in showingPopUpOrDataTip(), viewsheetClient.sendEvent in updateRendered(), updateRendered() rendered-objects state across multiple scrollViewport changes
- Reason: asyncZones=5 (at threshold): three constructor subscriptions feed shared state; setTimeout defers dim-draw creating a one-frame race; viewsheetClient.sendEvent must fire once per newly-rendered object and not again on re-render; renderedObjects Map must not be cleared mid-update

**Pass 3** (`VSObjectContainer.display.tl.spec.ts`)
- Methods: isAssemblyVisible(), isFilterInMaxModeView(), isMiniToolbarVisible(), toolbarForceHidden(), isSelected(), isAtBottom(), isMaxModeHidden(), popupShowing getter, isPopupShowing(), isFocused(), viewer getter, getAssemblyAsClass(), getAssemblyDivId(), getToolbarTop(), getActionsWidth(), getToolbarLeft(), getToolbarWidth(), zIndex(), needsZIndexBoost(), getVsObjectPosition() (all 6 objectType branches), getPopUpContentBoostZIndex(), getPopDimZIndex(), getPopDimWidth(), getPopDimHeight(), getActualWidth(), getChartDataAnnotations(), isChartAnnotationSelected(), getChartAnnotationTetherTo(), getChartAnnotationRestrictTo(), isObjectRendered(), isActivePopComponent(), trackByName(), getVariablesValues()
- Reason: dispatchPoints=5: getVsObjectPosition branches on 6 objectType values with nested viewer/embeddedVS/maxMode/dropdown/container conditions; isMiniToolbarVisible branches on containerType + objectType; zIndex branches on adhocFilter + popSource + dataTipSource + assemblyAnnotations

### BindingEditor

**Pass 1** (`BindingEditor.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngAfterViewInit, ngOnDestroy, assemblyName setter, bindingModel setter, variableValues setter, grayedOutFields setter, switchTab, updateData, updateFormat, updateChartMaxMode, openConsoleDialog, changeMessage
- Reason: Lifecycle hooks, input-driven state wiring, user-triggered tab switching, action dispatch, and the single async HTTP call with modal open

**Pass 3** (`BindingEditor.display.tl.spec.ts`)
- Methods: showHighLowPane, isBound, popUpWarning, formatsInactive, formatsDisabled, formatPaneVisible, miniToolbarHeight, bindingType, isVS, sizeChanged, hideDcTip, tableBindingModel, crosstabBindingModel
- Reason: Three dispatch-heavy methods (showHighLowPane 8-way chart-type branch, isBound 4-type model branch, popUpWarning 3-level severity branch) plus pure computed getters

### VsWizardObjectComponent

**Pass 1** (`VsWizardObjectComponent.interaction.tl.spec.ts`)
- Methods: ngOnInit, ngOnDestroy, select, editObject, onResizeStart, onResizeMove, onResizeEnd, onDragStart, onDragMove, onDragEnd, onMouseover, toEditMode, updateInteractable
- Reason: Lifecycle hooks, user-triggered drag/resize/select flows, and the editObject emit-vs-inline-edit branch are the core interaction paths

**Pass 3** (`VsWizardObjectComponent.display.tl.spec.ts`)
- Methods: isVSWizardObject, canEdit, saveOriginalBounds, isBoundsChanged, updateFollowPositions
- Reason: isVSWizardObject has an 11-branch objectType dispatch driving template visibility; canEdit and isBoundsChanged are pure conditionals; updateFollowPositions has follow vs non-follow branching on CSS application
