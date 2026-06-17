# embed Route Pre-scan Report

**日期**: 2026-06-08（2026-06-12 补充扫描，新增 1 个组件）
**候选组件数**: 6 | **建议推进**: 6 | **建议跳过**: 0 | **多 pass 组件**: 3

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
| 待审核 | EmbedChartComponent | 368 | 1 | 9 | **multi-pass** | ✅ 推进 |  |  | P1: embed-chart.component.interaction.tl.spec.ts<br>P2: embed-chart.component.risk.tl.spec.ts |
| 待审核 | VSChart | 1076 | 0 | 12 | **multi-pass** | ✅ 推进 | ⚠️ vs-chart.component.spec.ts | Jasmine bug, can't use DebugElement directly in expect | P1: vs-chart.component.interaction.tl.spec.ts<br>P2: vs-chart.component.risk.tl.spec.ts |
| 待审核 | MiniToolbar | 185 | 3 | 3 | **multi-pass** | ✅ 推进 |  |  | P1: mini-toolbar.component.interaction.tl.spec.ts<br>P2: mini-toolbar.component.risk.tl.spec.ts |
| 待审核 | ActionsContextmenuComponent | 154 | 0 | 1 | **single-pass** | ✅ 推进 | ⚠️ actions-contextmenu.component.spec.ts | test: should not create a dropdown when there are no visible actions | single pass (+内存泄漏) |
| ✅已测试 | DownloadTargetComponent | 84 | 1 | 4 | **single-pass** | ✅ 推进 |  |  | single pass (+竞态+内存泄漏) |

> **以下为 2026-06-12 补充扫描新增组件**

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
| 待审核 | EmbedViewerComponent | 201 | 0 | 1 | **single-pass** | ✅ 推进 |  |  | single pass (+内存泄漏) |

## 多 pass 组件详情

### EmbedChartComponent

**Pass 1** (`embed-chart.component.interaction.tl.spec.ts`)
- Methods: ngAfterViewInit, processSetRuntimeIdCommand, processSetViewsheetInfoCommand, processAddVSObjectCommand, processRefreshVSObjectCommand, processCollectParametersCommand, processEmbedErrorCommand, downloadStarted, openViewsheet0, closeViewsheetOnServer, updateVSInfo, showContextMenu, setServerUpdateInterval, onResize
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`embed-chart.component.risk.tl.spec.ts`)
- Methods: runtimeId, getAssemblyName, isMiniToolbarVisible, getToolbarTop, getToolbarLeft, getToolbarWidth, onMouseEnter, showMiniToolbar, openViewsheet, beforeDestroy, setAppSize, onOpenContextMenu, clearServerUpdateInterval, getVariableValues
- Reason: async≥3：竞态 / destructive / state inconsistency

### VSChart

**Pass 1** (`vs-chart.component.interaction.tl.spec.ts`)
- Methods: selected, model, resetShowEmptyAreaStatus, changeAutoRefresh, refresh, refreshChart, brushChart, brushChartArea, zoomChart, showData, showDetails, formatFunction, return, openMaxMode, closeMaxMode, showAllTitles, showAllAxes, showAllLegends, hideTitle, showTitle, hideAxis, hideLegend, showAnnotationDialog, addAnnotation, drill, drillAction, sendFlyover, showDataTip, endAxisResize, endLegendResize, sortAxis, onScroll, mouseLeave, getSelectedRegions, getSelectedString, clickHyperlink, clickEmptyPlotHyperlink, getHyperlinks, runtimeId, getActions, getMenuActions, getMoreActions, saveImageAs, addFilter, getFormats, captureChartSnapshot, processSetChartAreasCommand, checkNoData, emptyChart, sizeSliderTick, changeSizeRatio, changeTitle, titleResizeEnd, isDataTip, isForceTab, annotationContainerBounds, chartContainerBounds, showDCIcon, click, detectChanges, zoomInMap, zoomOutMap, zoomByWheel, panMap, dateComparisonTipStyle, isTipOverlapToolbar, showDateComparisonDialog, isPopupOrDataTipSource
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`vs-chart.component.risk.tl.spec.ts`)
- Methods: openWizardEnabled, actions, chartSelection, clearBrush, clearZoom, endLegendMove, selectRegion, selectTitle, clickSpecialHyperlink, clickTitleHyperlink, subscribe, clearSelection, showChartLoading, clearChartLoading, clearChartSnapshot, processClearChartLoadingCommand, processClearMapPanCommand, titleResizeMove, clearChartState, clearPanZoomMap
- Reason: async≥3：竞态 / destructive / state inconsistency

### MiniToolbar

**Pass 1** (`mini-toolbar.component.interaction.tl.spec.ts`)
- Methods: binding, alignLeft, miniToolbarHeight, navigate, getPreviousAction, focusNextItem, focusPreviousItem, doAction, onKeyUp, isFocused, topY, isPopComponent
- Reason: 回归主体：navigation / HTTP loading / lifecycle / user flows

**Pass 2** (`mini-toolbar.component.risk.tl.spec.ts`)
- Methods: forceShow, keyNavigation, getActions, getNextAction
- Reason: async≥3：竞态 / destructive / state inconsistency
