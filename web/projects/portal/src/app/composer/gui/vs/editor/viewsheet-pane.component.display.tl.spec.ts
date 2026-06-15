/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * VSPane — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — processShowLoadingMaskCommand: sets vs.loading=true, increments
 *                        loadingEventCount when not preparingData; sets preparingData=true
 *                        when command.preparingData=true.
 *   Group 2  [Risk 2] — processSetCurrentFormatCommand: updates vs.currentFormat with
 *                        command.model; does NOT update when command.model is null/undefined.
 *   Group 3  [Risk 2] — getDataSourceCSSIcon: returns empty string when vs=null; returns
 *                        a CSS class string from GuiTool.getTreeNodeIconClass otherwise.
 *   Group 4  [Risk 1] — openFormatPane: calls vs.selectAssembly(model) and emits
 *                        onOpenFormatPane with model.
 *   Group 5  [Risk 1] — getSearchResultLabel: returns _searchResultLabel value.
 *   Group 6  [Risk 1] — displayPlaceholderDragElementModel: when vsPane set, adjusts
 *                        left/top by scrollLeft/scrollTop.
 *   Group 7  [Risk 1] — changeSearchMode/isSearchMode/search: delegates to
 *                        composerVsSearchService; entering search mode initializes
 *                        _searchResultLabel="0/0".
 */

import { makeMocks, renderComponent } from "./viewsheet-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: processShowLoadingMaskCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processShowLoadingMaskCommand", () => {

   // Regression-sensitive: loadingEventCount tracks in-flight operations; if not incremented
   // it will be decremented below zero by ClearLoadingCommand, potentially clearing loading
   // prematurely the next time.
   it("should set vs.loading=true", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.vs.loading = false;

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false });

      expect(comp.vs.loading).toBe(true);
   });

   it("should increment loadingEventCount when preparingData=false", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).loadingEventCount = 0;

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false });

      expect((comp as any).loadingEventCount).toBe(1);
   });

   it("should NOT increment loadingEventCount when preparingData=true", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).loadingEventCount = 0;

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: true });

      expect((comp as any).loadingEventCount).toBe(0);
   });

   it("should set preparingData=true when command.preparingData=true", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).preparingData = false;

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: true });

      expect((comp as any).preparingData).toBe(true);
   });

   it("should set preparingData=false when command.preparingData=false", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      (comp as any).preparingData = true;

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false });

      expect((comp as any).preparingData).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: processSetCurrentFormatCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — processSetCurrentFormatCommand", () => {

   // Regression-sensitive: vs.currentFormat drives the format toolbar; a null model should
   // be a no-op (server sends null to reset — we keep the existing format instead).
   it("should update vs.currentFormat when command.model is provided", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const formatModel: any = { font: { fontFamily: "Arial" }, color: "#ff0000" };

      mocks.dispatchCommand("SetCurrentFormatCommand", { model: formatModel });

      expect(comp.vs.currentFormat).toBe(formatModel);
   });

   it("should NOT update vs.currentFormat when command.model is null", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const originalFormat = comp.vs.currentFormat;

      mocks.dispatchCommand("SetCurrentFormatCommand", { model: null });

      expect(comp.vs.currentFormat).toBe(originalFormat);
   });

   it("should NOT update vs.currentFormat when command.model is undefined", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const originalFormat = comp.vs.currentFormat;

      mocks.dispatchCommand("SetCurrentFormatCommand", { model: undefined });

      expect(comp.vs.currentFormat).toBe(originalFormat);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getDataSourceCSSIcon [Risk 2]
// ---------------------------------------------------------------------------

describe("VSPane — getDataSourceCSSIcon", () => {

   it("should return empty string when vs is null", async () => {
      const { comp } = await renderComponent();
      (comp as any)._vs = null;

      expect(comp.getDataSourceCSSIcon()).toBe("");
   });

   it("should return empty string when vs is set with null baseEntry", async () => {
      const { comp } = await renderComponent();
      // vs is already set via makeViewsheet(); baseEntry may be null → GuiTool returns ""
      const result = comp.getDataSourceCSSIcon();

      expect(result).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 4: openFormatPane [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — openFormatPane", () => {

   it("should call vs.selectAssembly with model and emit onOpenFormatPane", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const selectSpy = vi.spyOn(comp.vs, "selectAssembly");
      const emitSpy = vi.fn();
      comp.onOpenFormatPane.subscribe(emitSpy);
      const model: any = { absoluteName: "Table1", objectType: "VSTable" };

      comp.openFormatPane(model);

      expect(selectSpy).toHaveBeenCalledWith(model);
      expect(emitSpy).toHaveBeenCalledWith(model);
   });
});

// ---------------------------------------------------------------------------
// Group 5: getSearchResultLabel [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — getSearchResultLabel", () => {

   it("should return the current _searchResultLabel value", async () => {
      const { comp } = await renderComponent();
      (comp as any)._searchResultLabel = "3/10";

      expect(comp.getSearchResultLabel()).toBe("3/10");
   });

   it("should return undefined when _searchResultLabel has not been set", async () => {
      const { comp } = await renderComponent();
      (comp as any)._searchResultLabel = undefined;

      expect(comp.getSearchResultLabel()).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 6: displayPlaceholderDragElementModel [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — displayPlaceholderDragElementModel", () => {

   it("should adjust left and top by vsPane scrollLeft/scrollTop when vsPane is set", async () => {
      const { comp } = await renderComponent();
      (comp as any).placeholderDragElementModel = {
         top: 50,
         left: 30,
         width: 100,
         height: 50,
         text: "drag",
         visible: true,
      };
      // Provide a fake vsPane with scroll offsets
      (comp as any).vsPane = {
         nativeElement: {
            scrollLeft: 20,
            scrollTop: 10,
         },
      };

      const model = comp.displayPlaceholderDragElementModel;

      expect(model.left).toBe(50);  // 30 + 20
      expect(model.top).toBe(60);   // 50 + 10
   });

   it("should return model unchanged when vsPane is null", async () => {
      const { comp } = await renderComponent();
      (comp as any).placeholderDragElementModel = {
         top: 50,
         left: 30,
         width: 100,
         height: 50,
         text: "drag",
         visible: true,
      };
      (comp as any).vsPane = null;

      const model = comp.displayPlaceholderDragElementModel;

      expect(model.left).toBe(30);
      expect(model.top).toBe(50);
   });
});

// ---------------------------------------------------------------------------
// Group 7: changeSearchMode / isSearchMode / search [Risk 1]
// ---------------------------------------------------------------------------

describe("VSPane — changeSearchMode / isSearchMode / search", () => {

   it("changeSearchMode should delegate to composerVsSearchService.changeSearchMode", async () => {
      const { comp, fixture } = await renderComponent();
      // Access composerVsSearchService via comp's private field
      const searchService = (comp as any).composerVsSearchService;
      const spy = vi.spyOn(searchService, "changeSearchMode");

      comp.changeSearchMode();

      expect(spy).toHaveBeenCalled();
   });

   it("changeSearchMode should initialize _searchResultLabel='0/0' when entering search mode", async () => {
      const { comp } = await renderComponent();
      const searchService = (comp as any).composerVsSearchService;
      // Mock isSearchMode to return true AFTER changeSearchMode is called (entering search)
      vi.spyOn(searchService, "changeSearchMode").mockImplementation(() => {
         vi.spyOn(searchService, "isSearchMode").mockReturnValue(true);
      });

      comp.changeSearchMode();

      expect((comp as any)._searchResultLabel).toBe("0/0");
   });

   it("isSearchMode should delegate to composerVsSearchService.isSearchMode", async () => {
      const { comp } = await renderComponent();
      const searchService = (comp as any).composerVsSearchService;
      vi.spyOn(searchService, "isSearchMode").mockReturnValue(true);

      expect(comp.isSearchMode()).toBe(true);
   });

   it("search should set composerVsSearchService.searchString", async () => {
      const { comp } = await renderComponent();
      const searchService = (comp as any).composerVsSearchService;
      // scrollToMatchedAssembly uses setTimeout; spy on it to avoid side effects
      vi.spyOn(comp as any, "scrollToMatchedAssembly").mockImplementation(() => {});

      comp.search("myQuery");

      expect(searchService.searchString).toBe("myQuery");
   });
});
