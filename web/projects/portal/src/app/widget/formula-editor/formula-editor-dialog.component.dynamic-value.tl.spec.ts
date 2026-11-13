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
 * FormulaEditorDialog - the Expression editor of a dynamic value (dynamic-combo-box) pairs as a
 * `dynamicValue` context (bug #80459 follow-up): addressed by (assembly, property), derived BEFORE
 * the `isCondition` branch, hidden when the assembly or the property is missing, never falling
 * back to `assemblyMain`; agent pushes for exactly that (assembly, property) reach the host.
 */
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { Subject } from "rxjs";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ViewsheetCommandMessage } from "../../common/viewsheet-client";
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function dynamicDialog(property: string | undefined, assembly: string | undefined = "Text1",
                       withTransport = true)
{
   const { comp, ...rest } = createDialog();
   const commands = new Subject<ViewsheetCommandMessage>();
   comp.isVSContext = true;
   comp.isCondition = true;
   comp.contextTable = assembly;
   comp.assemblyName = assembly;
   comp.expression = "1";
   comp.dynamicProperty = property;

   if(withTransport) {
      comp.runtimeId = "Viewsheet1";
      comp.socketConnection = { commands } as any;
   }

   return { comp, commands, ...rest };
}

function push(commands: Subject<ViewsheetCommandMessage>, target: any) {
   commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", target));
}

describe("FormulaEditorDialog - dynamic value pairing (bug #80459)", () => {
   it("derives dynamicValue for the owning assembly and the property", () => {
      const { comp } = dynamicDialog("visible");

      expect(comp.canPair).toBe(true);
      expect(comp.editorContext).toEqual(
         { kind: "dynamicValue", assembly: "Text1", name: "visible" });
   });

   it("derives dynamicValue BEFORE isCondition: an isCondition combo with a property is not a vsConditionValue", () => {
      const { comp } = dynamicDialog("enabled");
      comp.isCondition = true;
      comp.conditionOperation = ConditionOperation.EQUAL_TO;
      comp.vsConditionScope = { assembly: "Other", highlightName: null };

      expect(comp.editorContext.kind).toBe("dynamicValue");
      expect(comp.editorContext).toEqual(
         { kind: "dynamicValue", assembly: "Text1", name: "enabled" });
   });

   it("keeps a namespaced name byte for byte", () => {
      const { comp } = dynamicDialog("dateComparison:customPeriods[0].start");

      expect(comp.editorContext.name).toBe("dateComparison:customPeriods[0].start");
   });

   it("prefers contextTable over assemblyName for the assembly", () => {
      const { comp } = dynamicDialog("visible");
      comp.assemblyName = "Stale";

      expect(comp.editorContext.assembly).toBe("Text1");
   });

   it.each([[undefined], [null], [""]])(
      "shows no button for the missing assembly %p", (assembly: any) =>
   {
      const { comp } = dynamicDialog("visible", assembly);
      comp.contextTable = assembly;
      comp.assemblyName = assembly;

      expect(comp.canPair).toBe(false);
   });

   it("shows no button for a blank property and never falls back to assemblyMain", () => {
      const { comp } = dynamicDialog("");

      expect(comp.canPair).toBe(false);
      expect(comp.editorContext?.kind).not.toBe("assemblyMain");
      expect(comp.editorContext).toEqual({ kind: "dynamicValue", assembly: "Text1", name: "" });
   });

   it("with no property keeps the non-addressable condition context and never assemblyMain", () => {
      const { comp } = dynamicDialog(undefined);

      expect(comp.canPair).toBe(false);
      expect(comp.editorContext?.kind).not.toBe("assemblyMain");
      expect(comp.editorContext?.kind).not.toBe("dynamicValue");
   });

   it("shows no button without a pairing transport", () => {
      const { comp } = dynamicDialog("visible", "Text1", false);

      expect(comp.canPair).toBe(false);
      expect(comp.editorContext).toBeNull();
   });

   it("carries the sheet type of the registered client to the connect button", () => {
      const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)),
                                     "formula-editor-dialog.component.html"), "utf8");
      const { comp } = dynamicDialog("visible");

      expect(html).toContain("[sheetType]=\"isVSContext ? 'VIEWSHEET' : 'WORKSHEET'\"");
      expect(comp.isVSContext).toBe(true);
   });

   it("applies an agent push for exactly this (assembly, property)", () => {
      const { comp, commands } = dynamicDialog("visible");
      const pushed: string[] = [];
      comp.agentScriptPushed.subscribe((t: string) => pushed.push(t));
      comp.ngOnInit();

      push(commands, { kind: "dynamicValue", assembly: "Text1", name: "visible", text: "1 > 0" });

      expect(comp.expression).toBe("1 > 0");
      expect(pushed).toEqual(["1 > 0"]);
   });

   it("ignores a push for another property, another assembly or another kind", () => {
      const { comp, commands } = dynamicDialog("visible");
      comp.ngOnInit();

      push(commands, { kind: "dynamicValue", assembly: "Text1", name: "enabled", text: "x" });
      push(commands, { kind: "dynamicValue", assembly: "Text2", name: "visible", text: "x" });
      push(commands, { kind: "vsConditionValue", assembly: "Text1", name: "visible", text: "x" });
      push(commands, { kind: "assemblyMain", assembly: "Text1", text: "x" });

      expect(comp.expression).toBe("1");
   });

   it("defers a push over unsaved human edits behind the reload banner", () => {
      const { comp, commands } = dynamicDialog("visible");
      comp.ngOnInit();
      comp.expression = "1 + 1";

      push(commands, { kind: "dynamicValue", assembly: "Text1", name: "visible", text: "3 > 0" });

      expect(comp.expression).toBe("1 + 1");
      expect(comp.staleExpression).toBe("3 > 0");
   });
});
