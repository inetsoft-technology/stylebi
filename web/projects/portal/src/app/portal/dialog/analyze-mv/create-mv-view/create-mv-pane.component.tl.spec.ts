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
 * CreateMVPane - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit: initializes selected MVs and emits the initial selection
 *   Group 2 [Risk 2] - selectionChanged / selectionAll / isModelSelected / isModelAllSelected
 *   Group 3 [Risk 2] - hide existing MV toggles and mutual exclusion with data variant
 *   Group 4 [Risk 2] - createOrUpdate / cycleChange guards and emitted payloads
 *   Group 5 [Risk 1] - showPlanClicked, runInBackgroundChanged, generateDataChanged, sortType
 *   Group 6 [Risk 1] - cancel is absent; component has no cancel action
 *
 * Confirmed bugs (it.fails): none
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";

import { ComponentTool } from "../../../../common/util/component-tool";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { CreateUpdateMvRequest } from "../../../../../../../shared/util/model/mv/create-update-mv-request";
import { MaterializedModel } from "../../../../../../../shared/util/model/mv/materialized-model";
import { NameLabelTuple } from "../../../../../../../shared/util/name-label-tuple";
import { SortColumnDirective } from "../../../../widget/directive/sort-column.directive";
import { CreateMVPane } from "./create-mv-pane.component";

@Component({ selector: "[sortColumn]", standalone: true, template: "" })
class SortColumnStub {
   @Input() data: any[];
   @Input() sortKey: any;
   @Input() sortType: any;
   @Output() sortTypeChanged = new EventEmitter<any>();
}

const MODAL_MOCK = {
   open: vi.fn(),
};

function makeModel(name: string): MaterializedModel {
   return { name } as MaterializedModel;
}

function makeCycle(name: string, label: string): NameLabelTuple {
   return { name, label } as NameLabelTuple;
}

interface RenderOpts {
   models?: MaterializedModel[];
   cycles?: NameLabelTuple[];
   mvCycle?: string;
   runInBackground?: boolean;
}

async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(CreateMVPane, {
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [{ replace: SortColumnDirective, with: SortColumnStub }],
      componentInputs: {
         models: opts.models ?? [],
         cycles: opts.cycles ?? [],
         mvCycle: opts.mvCycle ?? "",
         runInBackground: opts.runInBackground ?? false,
      },
   });

   return { comp: fixture.componentInstance as CreateMVPane, fixture };
}

afterEach(() => {
   vi.restoreAllMocks();
});

beforeEach(() => {
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});

describe("Group 1 - ngOnInit", () => {
   it("should initialize selectedMVs from model names and emit them", async () => {
      const models = [makeModel("mv1"), makeModel("mv2")];
      const { comp } = await renderComp({ models });
      const emitSpy = vi.spyOn(comp.selectedMVsChanged, "emit");

      comp.ngOnInit();

      expect(comp.selectedMVs).toEqual(["mv1", "mv2"]);
      expect(emitSpy).toHaveBeenCalledWith(["mv1", "mv2"]);
   });
});

describe("Group 2 - selection helpers", () => {
   it("should report a model as selected when its name is in selectedMVs", async () => {
      const { comp } = await renderComp({ models: [makeModel("mv1")] });
      comp.selectedMVs = ["mv1"];

      expect(comp.isModelSelected(makeModel("mv1"))).toBe(true);
   });

   it("should add and remove model names through selectionChanged", async () => {
      const model = makeModel("mv1");
      const { comp } = await renderComp({ models: [model] });
      const emitSpy = vi.spyOn(comp.selectedMVsChanged, "emit");

      comp.selectionChanged(true, model);
      const firstEmission = [...emitSpy.mock.calls[0][0]];
      expect(comp.selectedMVs).toEqual(["mv1"]);

      comp.selectionChanged(false, model);
      expect(comp.selectedMVs).toEqual([]);
      expect(firstEmission).toEqual(["mv1"]);
      expect(emitSpy).toHaveBeenNthCalledWith(2, []);
   });

   it("should report all selected only when every model name is present", async () => {
      const { comp } = await renderComp({ models: [makeModel("mv1"), makeModel("mv2")] });
      comp.selectedMVs = ["mv1", "mv2"];

      expect(comp.isModelAllSelected()).toBe(true);
   });

   it("should clear selectedMVs when selectionAll(false) is called", async () => {
      const { comp } = await renderComp({ models: [makeModel("mv1"), makeModel("mv2")] });
      const emitSpy = vi.spyOn(comp.selectedMVsChanged, "emit");

      comp.selectionAll(false);

      expect(comp.selectedMVs).toEqual([]);
      expect(emitSpy).toHaveBeenCalledWith([]);
   });
});

describe("Group 3 - hide toggles", () => {
   it("should emit hideExist and clear hideMVData when enabling hide existing MV", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.hideExist, "emit");
      comp.hideMVData = true;

      comp.changeHideExistingMV(true);

      expect(comp.hideMV).toBe(true);
      expect(comp.hideMVData).toBe(false);
      expect(emitSpy).toHaveBeenCalledWith(true);
   });

   it("should emit hideData and clear hideMV when enabling hide existing MV with data", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.hideData, "emit");
      comp.hideMV = true;

      comp.changeHideExistingMVWithData(true);

      expect(comp.hideMVData).toBe(true);
      expect(comp.hideMV).toBe(false);
      expect(emitSpy).toHaveBeenCalledWith(true);
   });
});

describe("Group 4 - action guards and payloads", () => {
   it("should show an error dialog when createOrUpdate is called without a selection", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve());
      const { comp } = await renderComp();

      comp.createOrUpdate();

      expect(dialogSpy).toHaveBeenCalledWith(
         MODAL_MOCK,
         "_#(js:Error)",
         "_#(js:select.materialized.view)",
      );
   });

   it("should emit a create payload with noData inverted from generateData", async () => {
      const { comp } = await renderComp({ models: [makeModel("mv1")], runInBackground: true });
      const emitSpy = vi.spyOn(comp.create, "emit");
      comp.selectedMVs = ["mv1"];
      comp.generateData = false;
      comp.mvCycle = "cycle-1";

      comp.createOrUpdate();

      expect(emitSpy).toHaveBeenCalledWith({
         mvNames: ["mv1"],
         noData: true,
         runInBackground: true,
         cycle: "cycle-1",
      } as CreateUpdateMvRequest);
   });

   it("should show an error dialog when cycleChange is called without a selection", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve());
      const { comp } = await renderComp();

      comp.cycleChange();

      expect(dialogSpy).toHaveBeenCalledWith(
         MODAL_MOCK,
         "_#(js:Error)",
         "_#(js:select.materialized.view)",
      );
   });

   it("should emit a cycle payload when selectedMVs is populated", async () => {
      const { comp } = await renderComp({ models: [makeModel("mv1")] });
      const emitSpy = vi.spyOn(comp.setCycle, "emit");
      comp.selectedMVs = ["mv1"];
      comp.mvCycle = "monthly";

      comp.cycleChange();

      expect(emitSpy).toHaveBeenCalledWith({
         mvNames: ["mv1"],
         cycle: "monthly",
      } as CreateUpdateMvRequest);
   });
});

describe("Group 5 - toggles and labels", () => {
   it("should emit showPlan with the current selection", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.showPlan, "emit");
      comp.selectedMVs = ["mv1", "mv2"];

      comp.showPlanClicked();

      expect(emitSpy).toHaveBeenCalledWith({ mvNames: ["mv1", "mv2"] } as CreateUpdateMvRequest);
   });

   it("should force generateData back to true when runInBackground is enabled", async () => {
      const { comp } = await renderComp({ runInBackground: false });
      comp.generateData = false;

      comp.runInBackgroundChanged(true);

      expect(comp.generateData).toBe(true);
   });

   it("should force runInBackground back to false when generateData is disabled", async () => {
      const { comp } = await renderComp({ runInBackground: true });

      comp.generateDataChanged(false);

      expect(comp.runInBackground).toBe(false);
   });

   it("should update sortType through changeSortType", async () => {
      const { comp } = await renderComp();

      comp.changeSortType("lastModifiedTime");

      expect(comp.sortType).toBe("lastModifiedTime");
   });
});
