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
 * FormulaEditorDialog - the viewsheet Conditions/Highlight dialog's Connect to Claude pairing
 * (bug #80459): a `vsConditionValue` context scoped to the ONE condition value, hidden for an
 * operator with no value slot (NULL/NOT_NULL), without a name, or when the scope and the
 * dialog's `isHighlight` disagree; agent pushes for exactly this scope reach the host. (A blank
 * highlight name never reaches here: VSConditionDialog hands down no scope for it.)
 */
import { Subject } from "rxjs";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ViewsheetCommandMessage } from "../../common/viewsheet-client";
import { VSConditionPairingScope } from "../../composer/gui/wiz/editor-context";
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function conditionDialog(scope: VSConditionPairingScope | undefined,
                         operation: ConditionOperation | undefined = ConditionOperation.EQUAL_TO,
                         isHighlight = false, withTransport = true)
{
   const { comp, ...rest } = createDialog();
   const commands = new Subject<ViewsheetCommandMessage>();
   comp.isVSContext = true;
   comp.isCondition = true;
   comp.assemblyName = "Table1";
   comp.contextTable = "WsTable";
   comp.expression = "1";
   comp.isHighlight = isHighlight;
   comp.conditionOperation = operation;
   comp.vsConditionScope = scope;

   if(withTransport) {
      comp.runtimeId = "Viewsheet1-clone";
      comp.socketConnection = { commands } as any;
   }

   return { comp, commands, ...rest };
}

const FILTER: VSConditionPairingScope = { assembly: "Table1", highlightName: null };
const HIGHLIGHT: VSConditionPairingScope = { assembly: "Chart1", highlightName: "High Sales" };

describe("FormulaEditorDialog - viewsheet condition value pairing (bug #80459)", () => {
   it("mints the filter condition of the owning assembly, not the worksheet table", () => {
      const { comp } = conditionDialog(FILTER);

      expect(comp.canPair).toBe(true);
      expect(comp.editorContext).toEqual(
         { kind: "vsConditionValue", assembly: "Table1", name: "filter" });
   });

   it("mints a highlight context carrying the highlight name", () => {
      const { comp } = conditionDialog(HIGHLIGHT, ConditionOperation.GREATER_THAN, true);

      expect(comp.canPair).toBe(true);
      expect(comp.editorContext).toEqual(
         { kind: "vsConditionValue", assembly: "Chart1", name: "highlight:High Sales" });
   });

   it("offers the button for every operator that has a value slot", () => {
      for(const op of [ConditionOperation.EQUAL_TO, ConditionOperation.LESS_THAN,
         ConditionOperation.GREATER_THAN, ConditionOperation.STARTING_WITH,
         ConditionOperation.CONTAINS, ConditionOperation.LIKE, ConditionOperation.BETWEEN,
         ConditionOperation.ONE_OF])
      {
         expect(conditionDialog(FILTER, op).comp.canPair).toBe(true);
      }
   });

   it("shows no button for NULL / NOT_NULL, which have no value slot", () => {
      const { comp } = conditionDialog(FILTER, ConditionOperation.NULL);

      expect(comp.canPair).toBe(false);
   });

   it("shows no button when the operator is not known", () => {
      // null, not undefined: a default parameter would turn undefined into EQUAL_TO
      expect(conditionDialog(FILTER, null).comp.canPair).toBe(false);
      expect(conditionDialog(FILTER, ConditionOperation.NONE).comp.canPair).toBe(false);
   });

   it("shows no button without a scope", () => {
      expect(conditionDialog(undefined).comp.canPair).toBe(false);
   });

   it("fails closed when the scope and isHighlight disagree", () => {
      expect(conditionDialog(HIGHLIGHT, ConditionOperation.EQUAL_TO, false).comp.canPair)
         .toBe(false);
      expect(conditionDialog(FILTER, ConditionOperation.EQUAL_TO, true).comp.canPair)
         .toBe(false);
   });

   it("shows no button without a pairing transport", () => {
      const { comp } = conditionDialog(FILTER, ConditionOperation.EQUAL_TO, false, false);

      expect(comp.canPair).toBe(false);
      expect(comp.editorContext).toBeNull();
   });

   it("applies an agent push for exactly this scope and tells the host", () => {
      const { comp, commands } = conditionDialog(FILTER);
      const pushed: string[] = [];
      comp.agentScriptPushed.subscribe((t: string) => pushed.push(t));
      comp.ngOnInit();

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
         kind: "vsConditionValue", assembly: "Table1", name: "filter", text: "1000",
      } as any));

      expect(comp.expression).toBe("1000");
      expect(pushed).toEqual(["1000"]);
   });

   it("ignores a push for the other list of the same assembly or another assembly", () => {
      const { comp, commands } = conditionDialog(FILTER);
      comp.ngOnInit();

      for(const target of [{ assembly: "Table1", name: "highlight:Hi" },
                           { assembly: "Table2", name: "filter" }])
      {
         commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "vsConditionValue", ...target, text: "x",
         } as any));
      }

      expect(comp.expression).toBe("1");
   });

   it("ignores a push meant for the assembly's own script", () => {
      const { comp, commands } = conditionDialog(FILTER);
      comp.ngOnInit();

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
         kind: "assemblyMain", assembly: "Table1", text: "x",
      } as any));

      expect(comp.expression).toBe("1");
   });

   it("defers a push over unsaved human edits instead of overwriting them", () => {
      const { comp, commands } = conditionDialog(FILTER);
      comp.ngOnInit();
      comp.expression = "1 + 1";

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
         kind: "vsConditionValue", assembly: "Table1", name: "filter", text: "1000",
      } as any));

      expect(comp.expression).toBe("1 + 1");
      expect(comp.staleExpression).toBe("1000");
   });
});
