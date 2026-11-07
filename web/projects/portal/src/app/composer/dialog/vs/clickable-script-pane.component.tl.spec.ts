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
 * ClickableScriptPane — editorContext / agent script live-refresh
 *
 * G2 Task 2: pins the assembly wiring for the onClick/onEnter script pane so a
 * pane-minted pairing code names the assembly it was opened for. Direct
 * instantiation — ScriptPane child not rendered.
 *
 * Bug #80600: adds the live-refresh subscription this pane was missing (#80447 only wired
 * ViewsheetScriptPane/FormulaEditorDialog), including the guard that a push for the inactive
 * radio's kind must update the underlying model but not clobber the visible CodeMirror text.
 */

import { Subject } from "rxjs";
import { ClickableScriptPane } from "./clickable-script-pane.component";
import { ClickableScriptPaneModel } from "../../data/vs/clickable-script-pane-model";
import { ViewsheetCommandMessage } from "../../../common/viewsheet-client";

const uiContextServiceMock: any = {
   getDefaultTab: () => "false",
   setDefaultTab: () => {},
};

function createPane(): ClickableScriptPane {
   const comp = new ClickableScriptPane(uiContextServiceMock);
   comp.model = {
      scriptExpression: "",
      onClickExpression: "",
      scriptEnabled: true,
   } as ClickableScriptPaneModel;
   return comp;
}

function createPaneWithSocket(model: Partial<ClickableScriptPaneModel> = {}, assembly = "Text1") {
   const comp = createPane();
   comp.model = { scriptExpression: "", onClickExpression: "", scriptEnabled: true, ...model } as ClickableScriptPaneModel;
   comp.assembly = assembly;
   comp.runtimeId = "Viewsheet1";
   const commands = new Subject<ViewsheetCommandMessage>();
   comp.socketConnection = { commands } as any;
   comp.ngOnInit();
   return { comp, commands };
}

function agentScriptCommand(overrides: Partial<{ kind: string, assembly: string, text: string }>) {
   return new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
      kind: "assemblyMain", assembly: "Text1", text: "new();", ...overrides,
   } as any);
}

describe("ClickableScriptPane — editorContext", () => {
   it("should report assemblyMain when the Script radio is selected", () => {
      const comp = createPane();
      comp.assembly = "Table1";
      comp.onClick = false;

      expect(comp.editorContext).toEqual({ kind: "assemblyMain", assembly: "Table1" });
   });

   it("should report assemblyOnClick when the onClick/onEnter radio is selected", () => {
      const comp = createPane();
      comp.assembly = "Table1";
      comp.onClick = true;

      expect(comp.editorContext).toEqual({ kind: "assemblyOnClick", assembly: "Table1" });
   });

   it("should follow the radio when it is toggled", () => {
      const comp = createPane();
      comp.assembly = "Text1";
      comp.onClick = false;

      comp.toggleTextarea();
      expect(comp.editorContext.kind).toBe("assemblyOnClick");

      comp.toggleTextarea();
      expect(comp.editorContext.kind).toBe("assemblyMain");
   });
});

describe("ClickableScriptPane — agent script live-refresh (bug #80600)", () => {
   it("applies an assemblyMain push while the Script radio is visible", () => {
      const { comp, commands } = createPaneWithSocket({ scriptExpression: "old();" });
      comp.onClick = false;

      commands.next(agentScriptCommand({ kind: "assemblyMain", text: "new();" }));

      expect(comp.model.scriptExpression).toBe("new();");
      expect(comp.expression).toBe("new();");
      expect(comp.staleScript).toBeNull();
   });

   it("applies an assemblyOnClick push while the onClick radio is visible", () => {
      const { comp, commands } = createPaneWithSocket({ onClickExpression: "old();" });
      comp.onClick = true;

      commands.next(agentScriptCommand({ kind: "assemblyOnClick", text: "new();" }));

      expect(comp.model.onClickExpression).toBe("new();");
      expect(comp.expression).toBe("new();");
   });

   it("updates the inactive radio's model without clobbering the visible editor", () => {
      // onClickExpression starts empty so ngOnInit's own onClick-expression-present auto-select
      // (ClickableScriptPane.ngOnInit) does not flip the visible radio away from Script.
      const { comp, commands } = createPaneWithSocket({ scriptExpression: "visible script();" });
      expect(comp.onClick).toBe(false); // Script radio visible; onClick is the inactive tab

      commands.next(agentScriptCommand({ kind: "assemblyOnClick", text: "new();" }));

      expect(comp.model.onClickExpression).toBe("new();");
      expect(comp.expression).toBe("visible script();");
   });

   it("ignores a push for a different assembly", () => {
      const { comp, commands } = createPaneWithSocket({ scriptExpression: "old();" }, "Text1");
      comp.onClick = false;

      commands.next(agentScriptCommand({ assembly: "Text2", text: "irrelevant();" }));

      expect(comp.model.scriptExpression).toBe("old();");
   });

   it("defers (does not clobber) when the human has typed since this dialog opened", () => {
      const { comp, commands } = createPaneWithSocket({ scriptExpression: "old();" });
      comp.onClick = false;
      comp.model.scriptExpression = "old(); // my local edit";

      commands.next(agentScriptCommand({ kind: "assemblyMain", text: "new();" }));

      expect(comp.model.scriptExpression).toBe("old(); // my local edit");
      expect(comp.expression).toBe("old();"); // visible editor untouched
      expect(comp.staleScript).toEqual({ field: "scriptExpression", text: "new();" });
   });

   it("reloadStaleScript() applies the deferred push and clears the stale flag", () => {
      const { comp, commands } = createPaneWithSocket({ scriptExpression: "old();" });
      comp.onClick = false;
      comp.model.scriptExpression = "old(); // my local edit";
      commands.next(agentScriptCommand({ kind: "assemblyMain", text: "new();" }));

      comp.reloadStaleScript();

      expect(comp.model.scriptExpression).toBe("new();");
      expect(comp.expression).toBe("new();");
      expect(comp.staleScript).toBeNull();
   });

   it("does nothing when there is no socketConnection (no pairing transport)", () => {
      const comp = createPane();
      comp.assembly = "Text1";
      comp.model.scriptExpression = "old();";

      expect(() => comp.ngOnInit()).not.toThrow();
      expect(comp.model.scriptExpression).toBe("old();");
   });

   it("unsubscribes on destroy so a later push is a no-op", () => {
      const { comp, commands } = createPaneWithSocket({ scriptExpression: "old();" });
      comp.ngOnDestroy();

      commands.next(agentScriptCommand({ kind: "assemblyMain", text: "new();" }));

      expect(comp.model.scriptExpression).toBe("old();");
   });
});
