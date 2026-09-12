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
 * Pass 3 — Display tests for TaskConditionPane.
 * Asserts DOM structure: nav pills, button disabled states, view switching,
 * and condition-type block visibility based on model/input state.
 */

import { screen } from "@testing-library/angular";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
   makeChainedCondition,
   makeDailyCondition,
   makeModel,
   makeWeeklyCondition,
   renderTaskConditionPane,
   resetMocks,
} from "./task-condition-pane.test-helpers";

beforeEach(() => {
   resetMocks();
});
afterEach(() => vi.restoreAllMocks());

describe("TaskConditionPane — display tests", () => {

   // -------------------------------------------------------------------------
   // Edit view (listView = false, single condition)
   // -------------------------------------------------------------------------

   describe("edit view (single condition → listView=false)", () => {
      it("renders nav pills for all enabled options when userDefinedClasses is empty", async () => {
         await renderTaskConditionPane();

         // Default model has userDefinedClasses=[] → Custom (UserCondition) is spliced out
         // Remaining: Daily, Weekly, Monthly, Hourly, Run Once, Chained
         expect(screen.queryByText("_#(js:Daily)")).not.toBeNull();
         expect(screen.queryByText("_#(js:Weekly)")).not.toBeNull();
         expect(screen.queryByText("_#(js:Monthly)")).not.toBeNull();
         expect(screen.queryByText("_#(js:Hourly)")).not.toBeNull();
         expect(screen.queryByText("_#(js:Run Once)")).not.toBeNull();
         expect(screen.queryByText("_#(js:Chained)")).not.toBeNull();
      });

      it("does not render the Custom option when userDefinedClasses is empty", async () => {
         await renderTaskConditionPane({ model: makeModel({ userDefinedClasses: [] }) });

         expect(screen.queryByText("_#(js:Custom)")).toBeNull();
      });

      it("renders the Custom option when userDefinedClasses has entries", async () => {
         await renderTaskConditionPane({
            model: makeModel({
               userDefinedClasses: ["com.example.CustomCond"],
               userDefinedClassLabels: ["My Custom"],
            }),
         });

         expect(screen.queryByText("_#(js:Custom)")).not.toBeNull();
      });

      it("marks the active option nav link with the .selected CSS class", async () => {
         const { comp, fixture } = await renderTaskConditionPane();
         fixture.detectChanges();

         // Default first condition is EVERY_DAY → Daily nav link should have .selected
         const dailyLink = screen.getByText("_#(js:Daily)");
         expect(dailyLink.classList.contains("selected")).toBe(true);

         // Other links should NOT have .selected
         const weeklyLink = screen.getByText("_#(js:Weekly)");
         expect(weeklyLink.classList.contains("selected")).toBe(false);
      });

      it("renders the 'Multiple Schedules' button in the edit view", async () => {
         await renderTaskConditionPane();

         expect(
            screen.queryByRole("button", { name: "_#(Multiple Schedules)" })
         ).not.toBeNull();
      });

      it("'Multiple Schedules' button is enabled when form is valid", async () => {
         // Default daily condition with interval=1 → valid form
         const { comp, fixture } = await renderTaskConditionPane();
         fixture.detectChanges();

         const btn = screen.getByRole("button", { name: "_#(Multiple Schedules)" });
         expect(btn).not.toBeDisabled();
      });

      it("does not render the Cancel button when newTask is not set", async () => {
         await renderTaskConditionPane();

         // newTask is falsy by default → Cancel button absent
         expect(screen.queryByRole("button", { name: "_#(Cancel)" })).toBeNull();
      });

      it("renders the Cancel button when newTask=true", async () => {
         const { comp, fixture } = await renderTaskConditionPane();
         comp.newTask = true;
         fixture.detectChanges();

         expect(
            screen.queryByRole("button", { name: "_#(Cancel)" })
         ).not.toBeNull();
      });

      it("renders the 'Server Time Zone' checkbox when startTimeEnabled=true", async () => {
         await renderTaskConditionPane({ startTimeEnabled: true });

         // Label text for the server-timezone checkbox
         expect(screen.queryByText("_#(Show Server Time Zone)")).not.toBeNull();
      });

      it("does not render the edit view when listView=true (2 conditions)", async () => {
         // 2 conditions → updateValues() sets listView=true → edit view hidden
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         await renderTaskConditionPane({ model });

         // The edit-view div has class "condition-edit-view"
         expect(document.querySelector(".condition-edit-view")).toBeNull();
      });
   });

   // -------------------------------------------------------------------------
   // List view (model with 2+ conditions → listView=true)
   // -------------------------------------------------------------------------

   describe("list view (two conditions → listView=true)", () => {
      it("renders the list view container", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         await renderTaskConditionPane({ model });

         expect(document.querySelector(".condition-list-view")).not.toBeNull();
      });

      it("renders the Add button in list view", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         await renderTaskConditionPane({ model });

         expect(screen.queryByRole("button", { name: "_#(Add)" })).not.toBeNull();
      });

      it("Copy button is disabled when no condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Copy)" })).toBeDisabled();
      });

      it("Copy button is enabled when exactly one condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Copy)" })).not.toBeDisabled();
      });

      it("Copy button is disabled when more than one condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0, 1];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Copy)" })).toBeDisabled();
      });

      it("Delete button is disabled when no condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Delete)" })).toBeDisabled();
      });

      it("Delete button is enabled when at least one condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [1];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Delete)" })).not.toBeDisabled();
      });

      it("Edit button is disabled when no condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Edit)" })).toBeDisabled();
      });

      it("Edit button is enabled when exactly one condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Edit)" })).not.toBeDisabled();
      });

      it("Edit button is disabled when more than one condition is selected", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp, fixture } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0, 1];
         fixture.detectChanges();

         expect(screen.getByRole("button", { name: "_#(Edit)" })).toBeDisabled();
      });
   });

   // -------------------------------------------------------------------------
   // Condition-type block visibility
   // -------------------------------------------------------------------------

   describe("condition-type block visibility", () => {
      it("renders the TimeCondition block when condition is a daily condition", async () => {
         await renderTaskConditionPane({
            model: makeModel({ conditions: [makeDailyCondition()] }),
         });

         // TimeCondition block wraps the time-zone checkbox
         expect(screen.queryByText("_#(Show Server Time Zone)")).not.toBeNull();
      });

      it("renders the CompletionCondition block when condition is chained", async () => {
         await renderTaskConditionPane({
            model: makeModel({ conditions: [makeChainedCondition()] }),
         });

         // CompletionCondition block contains the "Run after" label
         expect(screen.queryByText("_#(Run after)")).not.toBeNull();
      });

      it("does not render the CompletionCondition block for a daily condition", async () => {
         await renderTaskConditionPane({
            model: makeModel({ conditions: [makeDailyCondition()] }),
         });

         expect(screen.queryByText("_#(Run after)")).toBeNull();
      });

      it("does not render the TimeCondition block for a chained condition", async () => {
         await renderTaskConditionPane({
            model: makeModel({ conditions: [makeChainedCondition()] }),
         });

         expect(screen.queryByText("_#(Show Server Time Zone)")).toBeNull();
      });
   });
});
