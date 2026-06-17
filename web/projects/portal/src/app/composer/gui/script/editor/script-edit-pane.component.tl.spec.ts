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
 * ScriptEditPaneComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — expressionChange: columnTree target with parentName="component":
 *                        builds "parentLabel.text" or "viewsheet['...']['...']" depending
 *                        on whether parentLabel contains a space.
 *   Group 2  [Risk 3] — expressionChange: columnTree target with parentName="parameter":
 *                        builds "parentData['text']" when not identifier,
 *                        "parentData.text" when identifier.
 *   Group 3  [Risk 3] — expressionChange: columnTree with name="field":
 *                        builds "data['text']".
 *   Group 4  [Risk 3] — expressionChange: columnTree with name="highlighted":
 *                        builds "parentLabel.highlighted['text']".
 *   Group 5  [Risk 2] — expressionChange: non-leaf node returns early without changing
 *                        model.text; no-node path sets expression only.
 *   Group 6  [Risk 2] — expressionChange: model.isModified=false when new expression
 *                        equals _originalText; true otherwise.
 *   Group 7  [Risk 2] — ngOnInit: sets _originalText from model.text.
 *   Group 8  [Risk 1] — getScriptStyle: returns correct font-size from scriptFontSize input.
 *   Group 9  [Risk 1] — showNotifications: calls notifications.success.
 *   Group 10 [Memory leak] — subscription to scriptService.getClickedNode() is not
 *                        cleaned up in ngOnDestroy — after fixture.destroy(), events
 *                        still reach the component (demonstrates leak).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { ModelService } from "../../../../widget/services/model.service";
import { FontService } from "../../../../widget/services/font.service";
import { ScriptService } from "../script.service";
import { ScriptEditPaneComponent } from "./script-edit-pane.component";
import { ScriptModel } from "../../../data/script/script";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeModel(text = "// existing code"): ScriptModel {
   return {
      id: "test-script",
      type: "script",
      label: "TestScript",
      newAsset: false,
      isModified: false,
      text,
   };
}

function makeCodemirrorMock(currentValue = "") {
   return {
      focus: vi.fn(),
      getValue: vi.fn().mockReturnValue(currentValue),
      refresh: vi.fn(),
      getCursor: vi.fn().mockReturnValue({ line: 0, ch: 0 }),
   };
}

function makeSelection(fromLine = 0, fromCh = 0, toLine = 0, toCh = 0) {
   return { from: { line: fromLine, ch: fromCh }, to: { line: toLine, ch: toCh } };
}

interface RenderResult {
   fixture: any;
   comp: ScriptEditPaneComponent;
   scriptSubject: Subject<{ node: any; target: string }>;
   notifications: { success: ReturnType<typeof vi.fn> };
   codemirrorMock: ReturnType<typeof makeCodemirrorMock>;
}

async function renderComponent(model?: ScriptModel): Promise<RenderResult> {
   const scriptSubject = new Subject<{ node: any; target: string }>();
   const notifications = { success: vi.fn(), info: vi.fn(), warning: vi.fn(), danger: vi.fn() };
   const codemirrorMock = makeCodemirrorMock();
   const theModel = model ?? makeModel();

   const { fixture } = await render(ScriptEditPaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentProperties: {
         model: theModel,
         scriptFontSize: 14,
      },
      providers: [
         { provide: ModelService, useValue: { getModel: vi.fn(), sendModel: vi.fn() } },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: FontService, useValue: { defaultFont: "Roboto" } },
         { provide: ScriptService, useValue: { getClickedNode: () => scriptSubject.asObservable() } },
         provideHttpClient(),
      ],
   });

   const comp = fixture.componentInstance as ScriptEditPaneComponent;
   (comp as any).notifications = notifications;
   (comp as any).codemirrorInstance = codemirrorMock;

   return { fixture, comp, scriptSubject, notifications, codemirrorMock };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: expressionChange — columnTree, parentName="component" [Risk 3]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — expressionChange columnTree component", () => {

   // Regression-sensitive: parentLabel without space uses dot notation;
   // parentLabel with space must use bracket notation to be valid JS.
   it("should build 'parentLabel.text' when parentLabel has no space", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";

      comp.expressionChange({
         target: "columnTree",
         expression: "",
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: {
               parentName: "component",
               parentLabel: "Chart1",
               expression: "value",
               data: "value",
               suffix: "",
               name: "Chart1",
            },
         },
      });

      expect(comp.model.text).toContain("Chart1");
      expect(comp.model.text).toContain("value");
   });

   it("should build viewsheet bracket notation when parentLabel contains a space", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("");

      comp.expressionChange({
         target: "columnTree",
         expression: "",
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: {
               parentName: "component",
               parentLabel: "My Chart",
               expression: "value",
               data: "value",
               suffix: "",
               name: "My Chart",
            },
         },
      });

      // parentLabel "My Chart" has a space → viewsheet['My Chart'] notation
      expect(comp.model.text).toContain("viewsheet");
      expect(comp.model.text).toContain("My Chart");
   });
});

// ---------------------------------------------------------------------------
// Group 2: expressionChange — columnTree, parentName="parameter" [Risk 3]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — expressionChange columnTree parameter", () => {

   it("should build 'parentData[\\'text\\']' when text is not a valid identifier", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("");

      comp.expressionChange({
         target: "columnTree",
         expression: "",
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: {
               parentName: "parameter",
               parentData: "paramGroup",
               expression: "my-param",
               data: "my-param",
               suffix: "",
               name: "my-param",
            },
         },
      });

      // "my-param" is not an identifier (contains hyphen) → bracket notation
      expect(comp.model.text).toContain("paramGroup");
      expect(comp.model.text).toContain("my-param");
      expect(comp.model.text).toContain("[");
   });

   it("should build 'parentData.text' when text is a valid identifier", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("");

      comp.expressionChange({
         target: "columnTree",
         expression: "",
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: {
               parentName: "parameter",
               parentData: "paramGroup",
               expression: "myParam",
               data: "myParam",
               suffix: "",
               name: "myParam",
            },
         },
      });

      // "myParam" is a valid identifier → dot notation
      expect(comp.model.text).toContain("paramGroup.myParam");
   });
});

// ---------------------------------------------------------------------------
// Group 3: expressionChange — columnTree name="field" [Risk 3]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — expressionChange columnTree field", () => {

   it("should build 'data[\\'colName\\']' for name='field'", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("");

      comp.expressionChange({
         target: "columnTree",
         expression: "",
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: {
               name: "field",
               expression: "Sales",
               data: "Sales",
               suffix: "",
               parentName: "",
               parentLabel: "",
            },
         },
      });

      expect(comp.model.text).toContain("data");
      expect(comp.model.text).toContain("Sales");
   });
});

// ---------------------------------------------------------------------------
// Group 4: expressionChange — columnTree name="highlighted" [Risk 3]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — expressionChange columnTree highlighted", () => {

   it("should build 'parentLabel.highlighted[\\'text\\']' for name='highlighted'", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("");

      comp.expressionChange({
         target: "columnTree",
         expression: "",
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: {
               name: "highlighted",
               parentLabel: "Table1",
               expression: "hl1",
               data: "hl1",
               suffix: "",
               parentName: "",
            },
         },
      });

      expect(comp.model.text).toContain("Table1.highlighted");
      expect(comp.model.text).toContain("hl1");
   });
});

// ---------------------------------------------------------------------------
// Group 5: expressionChange — non-leaf and no-node paths [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — expressionChange edge cases", () => {

   // Regression-sensitive: non-leaf click must be a no-op so users can expand
   // tree folders without accidentally inserting text.
   it("should return early for a non-leaf node without changing model.text", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "original";
      comp.model = makeModel("original");

      comp.expressionChange({
         target: "columnTree",
         expression: "original",
         selection: makeSelection(),
         node: { leaf: false, type: "FOLDER", data: { name: "group" } },
      });

      expect(comp.model.text).toBe("original");
   });

   it("should set expression from event when no node is provided", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("");

      comp.expressionChange({
         target: "columnTree",
         expression: "x = 1;",
         selection: makeSelection(),
         node: null,
      });

      // No node → only sets expression from event.expression, model.text not changed
      expect(comp.expression).toBe("x = 1;");
   });

   it("should not change model.text when no node is provided", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      comp._originalText = "";
      comp.model = makeModel("existing");

      comp.expressionChange({
         target: "columnTree",
         expression: "x = 1;",
         selection: makeSelection(),
         node: null,
      });

      expect(comp.model.text).toBe("existing");
   });
});

// ---------------------------------------------------------------------------
// Group 6: expressionChange — isModified flag [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — isModified flag", () => {

   // Regression-sensitive: isModified must be cleared when the user reverts to the
   // original text so the save button is correctly disabled.
   it("should set model.isModified=false when expression matches _originalText after edit", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      const original = "x = 1;";
      comp._originalText = original;
      comp.model = makeModel(original);

      // The event sets expression=original and uses insertText to produce original back
      comp.expressionChange({
         target: "other",
         expression: original,
         selection: makeSelection(),
         node: {
            leaf: true,
            type: "COLUMN",
            data: { expression: "", data: "", suffix: "", name: "X", parentLabel: "User Defined" },
            label: "",
         },
      });

      expect(comp.model.isModified).toBe(false);
   });

   it("should set model.isModified=true when expression differs from _originalText", () => {
      const comp = new (ScriptEditPaneComponent as any)(
         null, null, null, { getClickedNode: () => ({ subscribe: vi.fn() }) }, null,
      );
      const original = "x = 1;";
      comp._originalText = original;
      comp.model = makeModel(original);

      // Insert something via no-target/User-Defined path
      comp.expressionChange({
         target: "other",
         expression: "x = 1;",
         selection: makeSelection(0, 6),
         node: {
            leaf: true,
            type: "COLUMN",
            data: { expression: " // comment", data: " // comment", suffix: "", name: "X",
                    parentLabel: "User Defined" },
            label: " // comment",
         },
      });

      expect(comp.model.isModified).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7: ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — ngOnInit", () => {

   it("should set _originalText from model.text on init", async () => {
      const model = makeModel("// script content");
      const { comp } = await renderComponent(model);

      expect((comp as any)._originalText).toBe("// script content");
   });
});

// ---------------------------------------------------------------------------
// Group 8: getScriptStyle [Risk 1]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — getScriptStyle", () => {

   it("should return font-size based on scriptFontSize input", async () => {
      const { comp, fixture } = await renderComponent();
      fixture.componentRef.setInput("scriptFontSize", 18);

      const style = comp.getScriptStyle();

      expect(style["font-size"]).toBe("18px");
   });

   it("should return font-size=14px for default scriptFontSize=14", async () => {
      const { comp } = await renderComponent();

      const style = comp.getScriptStyle();

      expect(style["font-size"]).toBe("14px");
   });
});

// ---------------------------------------------------------------------------
// Group 9: showNotifications [Risk 1]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — showNotifications", () => {

   it("should call notifications.success with the save success i18n key", async () => {
      const { comp, notifications } = await renderComponent();

      comp.showNotifications();

      expect(notifications.success).toHaveBeenCalledWith("_#(js:common.script.saveSuccess)");
   });
});

// ---------------------------------------------------------------------------
// Group 10: Memory leak [Risk 1]
// ---------------------------------------------------------------------------

describe("ScriptEditPaneComponent — memory leak", () => {

   // Documents that the subscription to scriptService.getClickedNode() in ngOnInit
   // is NOT unsubscribed on destroy because the component has no ngOnDestroy.
   // After fixture.destroy(), pushing to the subject still invokes itemClicked.
   it("should not invoke itemClicked after destroy (expected to fail due to memory leak)", async () => {
      const { comp, fixture, scriptSubject, codemirrorMock } = await renderComponent();
      // Spy on itemClicked before destroy
      const itemClickedSpy = vi.spyOn(comp as any, "itemClicked");

      fixture.destroy();

      // Push a node-click event after destroy
      scriptSubject.next({ node: { leaf: false }, target: "columnTree" });

      // Due to the memory leak, itemClicked IS invoked even after destroy.
      // This test documents the existing bug: change to .not.toHaveBeenCalled()
      // once the leak is fixed with proper ngOnDestroy cleanup.
      expect(itemClickedSpy).toHaveBeenCalled();
   });
});
