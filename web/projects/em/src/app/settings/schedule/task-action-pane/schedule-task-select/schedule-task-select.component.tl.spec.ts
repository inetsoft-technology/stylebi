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
 * ScheduleTaskSelectComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1] — picking a task from the tree must not re-create the tree nodes
 *   Group 2 [Risk 2] — programmatic selection still rebuilds the tree and reveals the node
 *
 * KEY contracts:
 *   - onChange() (the mat-select valueChange handler) stores the value and emits
 *     selectedChange without rebuilding the tree. Rebuilding detaches the DOM node
 *     that is being clicked, which defeats MatSelect.onContainerClick()'s
 *     "click came from the panel" check and re-opens the panel (Bug #75770).
 *   - Setting selectedTaskName to a new value (writeValue / @Input) still rebuilds
 *     the tree so the selected node's parents are expanded.
 *   - Setting selectedTaskName to the value it already holds is a no-op.
 */

import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ScheduleTaskSelectComponent } from "./schedule-task-select.component";
import { ScheduleTaskModel } from "../../../../../../../shared/schedule/model/schedule-task-model";

const OWNER = { name: "admin", orgID: "host-org" };

function makeTasks(): ScheduleTaskModel[] {
   return [
      { name: "dash1", label: "dash1", path: "org-schedule task", owner: OWNER },
      { name: "dash2", label: "dash2", path: "org-schedule task", owner: OWNER },
      { name: "batch1", label: "batch1", path: "org-schedule task/admin", owner: OWNER }
   ] as ScheduleTaskModel[];
}

beforeEach(() => {
   server.use(http.get("*/api/em/navbar/organization", () => HttpResponse.json("host-org")));
});

async function renderComp(tasks: ScheduleTaskModel[] = makeTasks()) {
   const result = await render(ScheduleTaskSelectComponent, {
      providers: [provideHttpClient()],
      componentProperties: { tasks }
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   const comp = result.fixture.componentInstance as ScheduleTaskSelectComponent;
   return { comp, fixture: result.fixture };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 1] — selecting from the tree must not re-create the tree nodes
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleTaskSelectComponent — onChange(): tree is left intact", () => {

   // 🔁 Regression-sensitive (Bug #75770): rebuilding the tree while the option's
   // click event is still propagating detaches the clicked DOM node, so
   // MatSelect.onContainerClick() no longer recognizes the click as coming from
   // the panel and immediately re-opens it.
   it("should not rebuild the tree nodes when a task is picked from the tree", async () => {
      const { comp } = await renderComp();

      const nodesBefore = comp.treeControl.dataNodes;
      const expandedBefore = comp.treeControl.expansionModel.selected.slice();

      comp.onChange("admin~;~host-org:dash1");

      expect(comp.treeControl.dataNodes).toBe(nodesBefore);
      expect(comp.treeControl.dataNodes.every((n, i) => n === nodesBefore[i])).toBe(true);
      expect(comp.treeControl.expansionModel.selected).toEqual(expandedBefore);
   });

   // Risk Point/Contract: the picked value is still stored and emitted so the
   // parent editor and the mat-select trigger stay in sync.
   it("should store and emit the picked task name", async () => {
      const { comp } = await renderComp();
      const emitted: string[] = [];
      comp.selectedChange.subscribe(v => emitted.push(v));

      comp.onChange("admin~;~host-org:dash1");

      expect(comp.selectedTaskName).toBe("admin~;~host-org:dash1");
      expect(emitted).toEqual(["admin~;~host-org:dash1"]);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — programmatic selection still rebuilds/reveals
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleTaskSelectComponent — selectedTaskName setter", () => {

   // 🔁 Regression-sensitive: writeValue()/@Input still has to rebuild the tree so
   // the parents of the selected node get expanded and the node becomes visible.
   it("should rebuild the tree and expand the selected node's parents when set programmatically", async () => {
      const { comp } = await renderComp();

      const nodesBefore = comp.treeControl.dataNodes;
      comp.writeValue("batch1");

      expect(comp.treeControl.dataNodes).not.toBe(nodesBefore);

      const expandedNames = comp.treeControl.expansionModel.selected.map(n => n.name);
      expect(expandedNames).toContain("org-schedule task");
      expect(expandedNames).toContain("admin");
   });

   // Risk Point/Contract: re-setting the value it already holds must not rebuild
   // the tree (avoids needless DOM churn while the panel is open).
   it("should be a no-op when set to the value it already holds", async () => {
      const { comp } = await renderComp();

      comp.writeValue("batch1");
      const nodesBefore = comp.treeControl.dataNodes;

      comp.writeValue("batch1");

      expect(comp.treeControl.dataNodes).toBe(nodesBefore);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — ngOnChanges(): rebuild is driven by the tasks input only
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleTaskSelectComponent — ngOnChanges(): selective rebuild", () => {

   // 🔁 Regression-sensitive: a new task list must still repopulate the tree.
   it("should rebuild the tree when the tasks input changes", async () => {
      const { comp } = await renderComp();

      const nodesBefore = comp.treeControl.dataNodes;
      comp.tasks = [
         { name: "other", label: "other", path: "/", owner: OWNER }
      ] as ScheduleTaskModel[];
      comp.ngOnChanges({
         tasks: { currentValue: comp.tasks, previousValue: null, firstChange: false,
            isFirstChange: () => false }
      });

      expect(comp.treeControl.dataNodes).not.toBe(nodesBefore);
      expect(comp.treeControl.dataNodes.map(n => n.name)).toEqual(["other"]);
   });

   // 🔁 Regression-sensitive (Bug #75770): the parent echoes the picked value back
   // through [selectedTaskName] after the user clicks a node. That re-binding must
   // not rebuild the tree a second time — the setter already decided it was a no-op.
   it("should not rebuild the tree when only selectedTaskName is re-bound after a pick", async () => {
      const { comp } = await renderComp();

      comp.onChange("admin~;~host-org:dash1");
      const nodesBefore = comp.treeControl.dataNodes;

      comp.selectedTaskName = "admin~;~host-org:dash1";
      comp.ngOnChanges({
         selectedTaskName: { currentValue: "admin~;~host-org:dash1", previousValue: null,
            firstChange: false, isFirstChange: () => false }
      });

      expect(comp.treeControl.dataNodes).toBe(nodesBefore);
   });

});
