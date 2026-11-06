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
 * Bug #80447 symptom 1 regression coverage for the two pure functions the live-refresh fix is
 * built on: identity matching (matchesAgentScriptRefresh) and the clobber guard
 * (shouldApplyAgentScriptPush). Both ViewsheetScriptPane and FormulaEditorDialog delegate to
 * these, so covering them here covers the decision logic for both dialogs without needing a full
 * Angular TestBed render.
 */
import { matchesAgentScriptRefresh, shouldApplyAgentScriptPush } from "./agent-script-refresh";
import { RefreshAgentScriptCommand } from "../../../vsobjects/command/refresh-agent-script-command";

function command(overrides: Partial<RefreshAgentScriptCommand> = {}): RefreshAgentScriptCommand {
   return { kind: "viewsheetOnInit", text: "foo();", ...overrides };
}

describe("matchesAgentScriptRefresh", () => {
   it("matches when kind, assembly and name are all equal", () => {
      expect(matchesAgentScriptRefresh(
         command({ kind: "calcField", assembly: "Orders", name: "Margin" }),
         { kind: "calcField", assembly: "Orders", name: "Margin" })).toBe(true);
   });

   it("does not match a different kind", () => {
      expect(matchesAgentScriptRefresh(
         command({ kind: "viewsheetOnInit" }),
         { kind: "viewsheetOnLoad" })).toBe(false);
   });

   it("does not match a different assembly", () => {
      expect(matchesAgentScriptRefresh(
         command({ kind: "assemblyMain", assembly: "Chart1" }),
         { kind: "assemblyMain", assembly: "Chart2" })).toBe(false);
   });

   it("does not match a different calc field name on the same table", () => {
      expect(matchesAgentScriptRefresh(
         command({ kind: "calcField", assembly: "Orders", name: "Margin" }),
         { kind: "calcField", assembly: "Orders", name: "Total" })).toBe(false);
   });

   it("treats an absent command assembly/name the same as an absent context assembly/name", () => {
      expect(matchesAgentScriptRefresh(
         command({ kind: "viewsheetOnInit", assembly: undefined, name: undefined }),
         { kind: "viewsheetOnInit" })).toBe(true);
   });

   it("returns false when context is null (dialog has no addressable location)", () => {
      expect(matchesAgentScriptRefresh(command(), null)).toBe(false);
   });

   it("returns false when context is undefined", () => {
      expect(matchesAgentScriptRefresh(command(), undefined)).toBe(false);
   });
});

describe("shouldApplyAgentScriptPush", () => {
   it("applies when the dialog's current text still matches the last known server text", () => {
      expect(shouldApplyAgentScriptPush("foo();", "foo();")).toBe(true);
   });

   it("defers when the human has edited since the last known server text", () => {
      expect(shouldApplyAgentScriptPush("foo(); // my edit", "foo();")).toBe(false);
   });

   it("treats null and empty string as equal (both mean 'nothing typed')", () => {
      expect(shouldApplyAgentScriptPush(null, "")).toBe(true);
      expect(shouldApplyAgentScriptPush("", undefined)).toBe(true);
   });

   it("defers when the human cleared text the server still has", () => {
      expect(shouldApplyAgentScriptPush("", "foo();")).toBe(false);
   });
});
