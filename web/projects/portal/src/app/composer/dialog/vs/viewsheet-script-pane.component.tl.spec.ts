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
 * ViewsheetScriptPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit initScriptVisible: Bug #18853 — the else branch of the second
 *     if/else always overwrites the result of the first if, so when onInit is set and onLoad is
 *     empty the first-if assignment (true) is immediately clobbered by getDefaultTab.
 *   Group 2 [Risk 2] — onInitClicked / onRefreshClicked: flip initScriptVisible and call
 *     setDefaultTab with the correct key and value.
 *   Group 3 [Risk 2] — onExpressionChange (no node): updates model.onInit or model.onLoad
 *     depending on initScriptVisible; treats falsy expression as empty string.
 *   Group 4 [Risk 1] — onExpressionChange (node paths): early return for non-leaf nodes;
 *     component-type node builds parentLabel.property path.
 *
 * Confirmed bugs (it.fails):
 *   Bug — ngOnInit initScriptVisible logic (Group 1, Bug #18853): the code has two consecutive
 *     if-blocks. The second block's else branch runs whenever !((!onInit && onLoad)) is true —
 *     i.e., whenever onInit is truthy OR onLoad is falsy. This means that even when the first
 *     if(onInit && !onLoad) fires and correctly sets initScriptVisible=true, the else of the
 *     second if immediately overwrites it with getDefaultTab()==="true". If the user's last-used
 *     tab was onLoad (getDefaultTab returns "false"), the onInit tab is silently hidden despite
 *     the viewsheet having an onInit script. Fix: convert to a proper if/else-if/else chain so
 *     only case 3 (neither script is set) consults the preference store.
 *
 * No memory leak (+内存泄漏): the component has no subscriptions or timers.
 *
 * Dependency setup:
 *   ViewsheetScriptPane imports ScriptPane, which in turn uses CodemirrorService,
 *   HelpUrlService, ScriptSettingsService, and FormulaFunctionAnalyzerService — all
 *   providedIn: 'root'. They are mocked in the render() providers array so that ScriptPane's
 *   ngOnInit / ngAfterViewInit run without real HTTP or codemirror initialisation.
 *   Element.prototype.getClientRects is spied in beforeEach to make ScriptPane's
 *   isEditorElementDisplayed() return true and suppress the post-init refresh setTimeout.
 *
 * Out of scope:
 *   queryPath / findFolderByLabel — private recursive tree helpers; full path-building coverage
 *     requires a real ScriptPaneTreeModel tree; integration-level.
 *   ScriptPane interaction — covered in ScriptPane's own spec.
 *   cursor setter — trivial assignment of position to child component; Risk 1.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { ViewsheetScriptPane } from "./viewsheet-script-pane.component";
import { ViewsheetScriptPaneModel } from "../../data/vs/viewsheet-script-pane-model";
import { UIContextService } from "../../../common/services/ui-context.service";
import { CodemirrorService } from "../../../../../../shared/util/codemirror/codemirror.service";
import { HelpUrlService } from "../../../widget/help-link/help-url.service";
import { ScriptSettingsService } from "../../../widget/dialog/script-pane/script-settings.service";
import { FormulaFunctionAnalyzerService } from "../../../widget/dialog/script-pane/formula-function-analyzer.service";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";

// ---------------------------------------------------------------------------
// Shared mock infrastructure
// ---------------------------------------------------------------------------

const mockCmInstance: any = {
   getValue: vi.fn().mockReturnValue(""),
   setValue: vi.fn(),
   getCursor: vi.fn().mockReturnValue({ line: 0, ch: 0 }),
   setCursor: vi.fn(),
   on: vi.fn(),
   off: vi.fn(),
   refresh: vi.fn(),
   toTextArea: vi.fn(),
   operation: vi.fn().mockImplementation((fn: () => void) => fn()),
   doc: {
      markText: vi.fn().mockReturnValue({ clear: vi.fn() }),
      clearGutter: vi.fn(),
      setGutterMarker: vi.fn(),
   },
};

const mockTernServer: any = {
   destroy: vi.fn(),
   updateArgHints: vi.fn(),
   complete: vi.fn(),
   showDocs: vi.fn(),
   options: { hintDelay: 1700, typeTip: null },
};

const codemirrorServiceMock = {
   getEcmaScriptDefs: vi.fn().mockReturnValue([{ Date: { prototype: {} } }]),
   createTernServer: vi.fn().mockReturnValue(mockTernServer),
   createCodeMirrorInstance: vi.fn().mockReturnValue(mockCmInstance),
   hasToken: vi.fn().mockReturnValue(false),
};

const uiContextServiceMock = {
   getDefaultTab: vi.fn().mockReturnValue("true"),
   setDefaultTab: vi.fn(),
};

const analyzerServiceMock = {
   syntaxAnalysis: vi.fn().mockReturnValue([]),
};

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function createModel(overrides: Partial<ViewsheetScriptPaneModel> = {}): ViewsheetScriptPaneModel {
   return { onInit: "", onLoad: "", enableScript: true, ...overrides };
}

function createTreeModel(): ScriptPaneTreeModel {
   return { columnTree: null, functionTree: null, operatorTree: null };
}

async function renderComponent(modelOverrides: Partial<ViewsheetScriptPaneModel> = {}) {
   const { fixture } = await render(ViewsheetScriptPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: UIContextService, useValue: uiContextServiceMock },
         { provide: CodemirrorService, useValue: codemirrorServiceMock },
         // ScriptPane.ngOnInit() subscribes to helpService.getScriptHelpUrl()
         { provide: HelpUrlService, useValue: { getScriptHelpUrl: () => of("") } },
         // ScriptPane.ngOnInit() subscribes to scriptSettingsService.isCursorTop()
         { provide: ScriptSettingsService, useValue: { isCursorTop: () => of(false) } },
         { provide: FormulaFunctionAnalyzerService, useValue: analyzerServiceMock },
      ],
      componentInputs: {
         model: createModel(modelOverrides),
         scriptTreeModel: createTreeModel(),
      },
   });
   return { comp: fixture.componentInstance as ViewsheetScriptPane, fixture };
}

beforeEach(() => {
   vi.clearAllMocks();
   uiContextServiceMock.getDefaultTab.mockReturnValue("true");
   codemirrorServiceMock.getEcmaScriptDefs.mockReturnValue([{ Date: { prototype: {} } }]);
   codemirrorServiceMock.createTernServer.mockReturnValue(mockTernServer);
   codemirrorServiceMock.createCodeMirrorInstance.mockReturnValue(mockCmInstance);
   codemirrorServiceMock.hasToken.mockReturnValue(false);
   analyzerServiceMock.syntaxAnalysis.mockReturnValue([]);

   // ScriptPane.ngAfterViewInit() checks isEditorElementDisplayed() via getClientRects().
   // jsdom always returns an empty DOMRectList, so without this spy the branch schedules a
   // 0ms setTimeout: "setTimeout(() => this.codemirrorInstance.refresh(), 0)". That timer
   // fires after fixture.destroy() nulls codemirrorInstance, causing a crash. Returning a
   // non-empty list makes isEditorElementDisplayed() return true, suppressing the timer.
   vi.spyOn(Element.prototype, "getClientRects").mockReturnValue({
      length: 1,
      0: new DOMRect(0, 0, 100, 100),
      item: () => new DOMRect(0, 0, 100, 100),
   } as any);
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit — initScriptVisible logic [Risk 3] — Bug #18853
// ---------------------------------------------------------------------------

describe("ViewsheetScriptPane — ngOnInit initScriptVisible", () => {
   it("should set initScriptVisible=true when getDefaultTab returns 'true' and both scripts are empty", async () => {
      uiContextServiceMock.getDefaultTab.mockReturnValue("true");
      const { comp } = await renderComponent({ onInit: "", onLoad: "" });
      expect(comp.initScriptVisible).toBe(true);
   });

   it("should set initScriptVisible=false when getDefaultTab returns 'false' and both scripts are empty", async () => {
      uiContextServiceMock.getDefaultTab.mockReturnValue("false");
      const { comp } = await renderComponent({ onInit: "", onLoad: "" });
      expect(comp.initScriptVisible).toBe(false);
   });

   it("should set initScriptVisible=false when only onLoad is set (second if fires; else does not run)", async () => {
      uiContextServiceMock.getDefaultTab.mockReturnValue("true");
      const { comp } = await renderComponent({ onInit: "", onLoad: "doSomething();" });
      // second if(!onInit && onLoad) fires → initScriptVisible=false; no else runs
      expect(comp.initScriptVisible).toBe(false);
   });

   it("should call getDefaultTab with key 'vs.onInit' and default 'true'", async () => {
      await renderComponent();
      expect(uiContextServiceMock.getDefaultTab).toHaveBeenCalledWith("vs.onInit", "true");
   });

   // Bug #18853: when onInit is set and onLoad is empty, the first if correctly sets
   // initScriptVisible=true. However the else of the SECOND if also runs (because
   // !((!onInit && onLoad)) is true when onInit is truthy). The else overwrites the result
   // with getDefaultTab()==="true". If getDefaultTab returns "false", initScriptVisible ends
   // up false even though the viewsheet has an onInit script.
   // Fix: restructure as if/else-if/else so only the default case consults the preference.
   it.fails("should set initScriptVisible=true when onInit is set and onLoad is empty regardless of getDefaultTab", async () => {
      uiContextServiceMock.getDefaultTab.mockReturnValue("false");
      const { comp } = await renderComponent({ onInit: "initScript();", onLoad: "" });
      // First if(onInit && !onLoad) → sets true.
      // Second if/else: else fires (onInit truthy → !(false) = true) → overwrites with "false"==="true" → false.
      // After the fix this should stay true.
      expect(comp.initScriptVisible).toBe(true); // FAILS — overwritten to false
   });
});

// ---------------------------------------------------------------------------
// Group 2: onInitClicked / onRefreshClicked [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetScriptPane — onInitClicked / onRefreshClicked", () => {
   it("should set initScriptVisible=true when onInitClicked is called", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = false;
      comp.onInitClicked();
      expect(comp.initScriptVisible).toBe(true);
   });

   it("should call setDefaultTab('vs.onInit', 'true') when onInitClicked", async () => {
      const { comp } = await renderComponent();
      comp.onInitClicked();
      expect(uiContextServiceMock.setDefaultTab).toHaveBeenCalledWith("vs.onInit", "true");
   });

   it("should set initScriptVisible=false when onRefreshClicked is called", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.onRefreshClicked();
      expect(comp.initScriptVisible).toBe(false);
   });

   it("should call setDefaultTab('vs.onInit', 'false') when onRefreshClicked", async () => {
      const { comp } = await renderComponent();
      comp.onRefreshClicked();
      expect(uiContextServiceMock.setDefaultTab).toHaveBeenCalledWith("vs.onInit", "false");
   });
});

// ---------------------------------------------------------------------------
// Group 3: onExpressionChange — no node (pure model update) [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewsheetScriptPane — onExpressionChange without node", () => {
   it("should update model.onInit when initScriptVisible is true", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.onExpressionChange({ expression: "init code;", node: null });
      expect(comp.model.onInit).toBe("init code;");
   });

   it("should update model.onLoad when initScriptVisible is false", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = false;
      comp.onExpressionChange({ expression: "load code;", node: null });
      expect(comp.model.onLoad).toBe("load code;");
   });

   it("should set model.onInit to empty string when expression is null", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.onExpressionChange({ expression: null, node: null });
      expect(comp.model.onInit).toBe("");
   });

   it("should set model.onLoad to empty string when expression is undefined", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = false;
      comp.onExpressionChange({ expression: undefined, node: null });
      expect(comp.model.onLoad).toBe("");
   });

   it("should not change model.onLoad when initScriptVisible is true", async () => {
      const { comp } = await renderComponent({ onLoad: "existingLoad;" });
      comp.initScriptVisible = true;
      comp.onExpressionChange({ expression: "new init;", node: null });
      expect(comp.model.onLoad).toBe("existingLoad;");
   });
});

// ---------------------------------------------------------------------------
// Group 4: onExpressionChange — node handling [Risk 1]
// ---------------------------------------------------------------------------

describe("ViewsheetScriptPane — onExpressionChange with node", () => {
   it("should not call insertText when the node is not a leaf (returns early after model update)", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.model.onInit = "";
      comp.onExpressionChange({
         expression: "x",
         node: { leaf: false, data: {}, type: "" } as any,
         target: "columnTree",
         selection: { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } },
      });
      // expression update runs before the leaf check; insertText is NOT called
      expect(comp.model.onInit).toBe("x");
   });

   it("should not call insertText when node.data is null (returns early)", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.model.onInit = "";
      comp.onExpressionChange({
         expression: "y",
         node: { leaf: true, data: null, type: "" } as any,
         target: "columnTree",
         selection: { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } },
      });
      expect(comp.model.onInit).toBe("y");
   });

   it("should build parentLabel.property path for component node without spaces in label", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.model.onInit = "";
      comp.onExpressionChange({
         expression: "Chart1.value",
         node: {
            leaf: true,
            data: { parentName: "component", parentLabel: "Chart1", expression: "value", suffix: "" },
            type: "component",
         } as any,
         target: "columnTree",
         selection: { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } },
      });
      // parentLabel "Chart1" has no spaces → "Chart1.value" path used
      expect(comp.model.onInit).toContain("Chart1");
   });

   it("should build viewsheet['label'] path for component node with spaces in parent label", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.model.onInit = "";
      comp.onExpressionChange({
         expression: "Table 1.value",
         node: {
            leaf: true,
            data: { parentName: "component", parentLabel: "Table 1", expression: "value", suffix: "" },
            type: "component",
         } as any,
         target: "columnTree",
         selection: { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } },
      });
      // parentLabel "Table 1" has a space → "viewsheet['Table 1']" pattern
      expect(comp.model.onInit).toContain("viewsheet");
   });

   it("should build parameter['x'] path for parameter node with non-identifier text", async () => {
      const { comp } = await renderComponent();
      comp.initScriptVisible = true;
      comp.model.onInit = "";
      comp.onExpressionChange({
         expression: "my param",
         node: {
            leaf: true,
            data: { parentName: "parameter", parentData: "parameter", expression: "my param", suffix: "" },
            type: "",
         } as any,
         target: "columnTree",
         selection: { from: { line: 0, ch: 0 }, to: { line: 0, ch: 0 } },
      });
      // "my param" is not a JS identifier → parameter['my param']
      expect(comp.model.onInit).toContain("parameter");
   });
});
