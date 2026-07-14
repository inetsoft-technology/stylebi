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
 * VSConditionDialog — single pass
 *
 * Direct instantiation — six constructor dependencies (HttpClient, ModelService, NgbModal,
 * ConditionDialogService, Renderer2, ElementRef), none via `inject()`. `resizeable` is forced to
 * false in the test fixture so ngAfterViewInit's inherited BaseResizeableDialogComponent DOM
 * resize-handle logic (a separate, generic concern) never runs — see Out of scope.
 *
 * Scope: fills the gap flagged in the prescan against the existing vs-condition-dialog.spec.ts
 * (kept — it covers the dirty-condition confirm-dialog flow via conditionChanged+ok(), a
 * different concern from this logic-level suite): ngOnInit/highlightModel cloning,
 * checkConditionTrap's HTTP + trap-alert undo/keep branches, conditionListChanged's
 * shouldCheckTrap-gated field-set logic, getServerAppliedModel's field-stripping, and the
 * apply/cancel emitters — plus baseline coverage of every other public method.
 *
 * Risk-first coverage:
 *   Group 6 [Risk 3] — checkConditionTrap: HTTP dispatch + trap-alert undo/keep-and-continue
 *                       branches, the highest-value gap called out in the prescan
 *   Group 3 [Risk 3] — ok/apply (submit dispatch): checkTrap-present vs. internal-commit paths
 *   Group 5 [Risk 3] — conditionListChanged: the 4-guard gate for triggering a trap re-check
 *   Group 7 [Risk 2] — shouldCheckTrap/getUniqueFields: field-set comparison logic
 *   Group 1 [Risk 2] — ngOnInit: highlightModel cloning, stale-field replacement, checkpoint init
 *   Group 2/4/8 [Risk 1/2] — single-purpose lifecycle hook and setters
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope: BaseResizeableDialogComponent's inherited DOM resize-handle wiring in
 * ngAfterViewInit — a separate, generic base-class concern unrelated to condition-dialog logic;
 * disabled here via `resizeable = false` on every fixture.
 */

import { HttpResponse } from "@angular/common/http";
import { waitFor } from "@testing-library/angular";
import { of } from "rxjs";

import { VSConditionDialog } from "./vs-condition-dialog.component";
import { VSConditionItemPaneProvider } from "./vs-condition-item-pane-provider";
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { ComponentTool } from "../../common/util/component-tool";

afterEach(() => vi.restoreAllMocks());

function makeDataRef(name: string, dataType: string = XSchema.STRING): DataRef {
   return { name, view: name, attribute: name, dataType, entity: null } as DataRef;
}

function makeCondition(overrides: Partial<Condition> = {}): Condition {
   return {
      jsonType: "condition",
      field: makeDataRef("field1"),
      operation: ConditionOperation.EQUAL_TO,
      values: [{ type: ConditionValueType.VALUE, value: "x" }],
      level: 0,
      equal: true,
      negated: false,
      ...overrides,
   };
}

function makeModel(overrides: Partial<VSConditionDialogModel> = {}): VSConditionDialogModel {
   return {
      tableName: "Table1",
      fields: [makeDataRef("field1"), makeDataRef("field2")],
      conditionList: [],
      ...overrides,
   };
}

function createComponent(opts: {
   model?: VSConditionDialogModel;
   highlightModel?: VSConditionDialogModel;
   runtimeId?: string;
   assemblyName?: string;
   variableValues?: string[];
   checkTrap?: (callback: () => void, conditionModel: VSConditionDialogModel) => void;
} = {}) {
   const modelService = { sendModel: vi.fn(() => of(new HttpResponse({ body: null }))) };
   const modalService = {};
   const conditionService: any = {
      dirtyCondition: null,
      dirtyJunction: null,
      checkDirtyConditions: vi.fn().mockResolvedValue(true),
   };
   const renderer = {};
   const element = { nativeElement: {} };

   const comp = new VSConditionDialog(
      {} as any, modelService as any, modalService as any, conditionService, renderer as any, element as any);
   comp.resizeable = false;
   comp.runtimeId = opts.runtimeId ?? "rt1";
   comp.assemblyName = opts.assemblyName ?? "Table1";
   comp.variableValues = opts.variableValues ?? [];
   comp.nonSupportBrowseFields = [];
   comp.isHighlight = false;
   comp.checkTrap = opts.checkTrap;
   comp.model = opts.model !== undefined ? opts.model : makeModel();
   comp.highlightModel = opts.highlightModel;

   return { comp, modelService, modalService, conditionService };
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit
// ---------------------------------------------------------------------------

describe("VSConditionDialog — ngOnInit", () => {
   it("should clone highlightModel into model when highlightModel is provided", () => {
      const highlightModel = makeModel({ tableName: "HL" });
      const { comp } = createComponent({ model: undefined, highlightModel, checkTrap: () => {} });

      comp.ngOnInit();

      expect(comp.model).toEqual(highlightModel);
      expect(comp.model).not.toBe(highlightModel);
   });

   it("should default conditionList to an empty array when the model has none", () => {
      const { comp } = createComponent({ model: makeModel({ conditionList: undefined as any }), checkTrap: () => {} });

      comp.ngOnInit();

      expect(comp.model.conditionList).toEqual([]);
   });

   it("should construct a VSConditionItemPaneProvider from the component's inputs", () => {
      const { comp } = createComponent({ checkTrap: () => {} });

      comp.ngOnInit();

      expect(comp.provider).toBeInstanceOf(VSConditionItemPaneProvider);
   });

   it("should replace a condition's field with the matching field from model.fields (by view)", () => {
      const fullField = makeDataRef("field1");
      const staleField = { view: "field1" } as DataRef;
      const cond = makeCondition({ field: staleField });
      const model = makeModel({ fields: [fullField], conditionList: [cond] });
      const { comp } = createComponent({ model, checkTrap: () => {} });

      comp.ngOnInit();

      expect(comp.model.conditionList[0].field).toBe(fullField);
   });

   it("should leave a condition's field unchanged when no matching field is found by view", () => {
      const staleField = { view: "unknown" } as DataRef;
      const cond = makeCondition({ field: staleField });
      const model = makeModel({ fields: [makeDataRef("field1")], conditionList: [cond] });
      const { comp } = createComponent({ model, checkTrap: () => {} });

      comp.ngOnInit();

      expect(comp.model.conditionList[0].field).toBe(staleField);
   });

   it("should initialize the checkpoint and check for traps when no checkTrap callback is provided", () => {
      const { comp, modelService } = createComponent({ checkTrap: undefined });

      comp.ngOnInit();

      expect(comp.conditionListCheckpoint).toEqual(comp.model.conditionList);
      expect(modelService.sendModel).toHaveBeenCalled();
   });

   it("should not initialize the checkpoint or check for traps when a checkTrap callback is provided", () => {
      const { comp, modelService } = createComponent({ checkTrap: () => {} });

      comp.ngOnInit();

      expect(comp.conditionListCheckpoint).toBeUndefined();
      expect(modelService.sendModel).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngAfterViewInit
// ---------------------------------------------------------------------------

describe("VSConditionDialog — ngAfterViewInit", () => {
   it("should clear dirtyCondition and dirtyJunction", () => {
      const { comp, conditionService } = createComponent();
      conditionService.dirtyCondition = { selectedIndex: 0, condition: makeCondition() };
      conditionService.dirtyJunction = { selectedIndex: 0, junctionType: JunctionOperatorType.AND };

      comp.ngAfterViewInit();

      expect(conditionService.dirtyCondition).toBeNull();
      expect(conditionService.dirtyJunction).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3: ok / apply (submit dispatch)
// ---------------------------------------------------------------------------

describe("VSConditionDialog — cancel", () => {
   it("should emit onCancel with 'cancel'", () => {
      const { comp } = createComponent();
      const spy = vi.fn();
      comp.onCancel.subscribe(spy);

      comp.cancel();

      expect(spy).toHaveBeenCalledWith("cancel");
   });
});

describe("VSConditionDialog — ok / apply", () => {
   it("ok() should emit onCommit with the server-applied model when not dirty and no checkTrap is set", async () => {
      const model = makeModel({ conditionList: [makeCondition()] });
      const { comp } = createComponent({ model, checkTrap: undefined });
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);

      comp.ok();

      await waitFor(() => expect(spy).toHaveBeenCalledWith(
         { tableName: model.tableName, fields: [], conditionList: model.conditionList }));
   });

   it("apply() should emit onApply with the collapse flag and the server-applied model", async () => {
      const model = makeModel({ conditionList: [makeCondition()] });
      const { comp } = createComponent({ model, checkTrap: undefined });
      const spy = vi.fn();
      comp.onApply.subscribe(spy);

      comp.apply(true);

      await waitFor(() => expect(spy).toHaveBeenCalledWith(
         { collapse: true, result: { tableName: model.tableName, fields: [], conditionList: model.conditionList } }));
   });

   it("should not emit onCommit when checkDirtyConditions resolves false", async () => {
      const { comp, conditionService } = createComponent();
      conditionService.checkDirtyConditions.mockResolvedValue(false);
      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();
      // No positive signal to wait on (the guard suppresses emission entirely). Awaiting the
      // exact promise submit() attached its .then() to is deterministic rather than an arbitrary
      // flush count: .then() callbacks on the same promise run in registration order, so by the
      // time this await resumes, submit's success-branch logic has already run (and done nothing).
      await conditionService.checkDirtyConditions.mock.results[0].value;

      expect(commitSpy).not.toHaveBeenCalled();
   });

   it("should defer to the checkTrap callback instead of emitting directly when checkTrap is provided", async () => {
      const model = makeModel({ conditionList: [makeCondition()] });
      const checkTrap = vi.fn();
      const { comp } = createComponent({ model, checkTrap });
      const commitSpy = vi.fn();
      comp.onCommit.subscribe(commitSpy);

      comp.ok();

      await waitFor(() => expect(checkTrap).toHaveBeenCalledWith(expect.any(Function), model));
      expect(commitSpy).not.toHaveBeenCalled();

      // Invoking the callback the parent was handed should now emit onCommit.
      checkTrap.mock.calls[0][0]();
      expect(commitSpy).toHaveBeenCalledWith(
         { tableName: model.tableName, fields: [], conditionList: model.conditionList });
   });

   it("should wire conditionPane.save/isConditionValid/saveOption into checkDirtyConditions", async () => {
      const { comp, conditionService } = createComponent();
      comp.conditionPane = {
         save: vi.fn(() => "saved"),
         isConditionValid: vi.fn(() => true),
         saveOption: vi.fn(() => "modify"),
      } as any;

      comp.ok();

      await waitFor(() => expect(conditionService.checkDirtyConditions).toHaveBeenCalled());
      const [save, isValid, saveOption] = conditionService.checkDirtyConditions.mock.calls[0];

      expect(save()).toBe("saved");
      expect(isValid()).toBe(true);
      expect(saveOption()).toBe("modify");
      expect(comp.conditionPane.save).toHaveBeenCalled();
      expect(comp.conditionPane.isConditionValid).toHaveBeenCalled();
      expect(comp.conditionPane.saveOption).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: conditionChanged / junctionChanged
// ---------------------------------------------------------------------------

describe("VSConditionDialog — conditionChanged / junctionChanged", () => {
   it("conditionChanged should set conditionService.dirtyCondition", () => {
      const { comp, conditionService } = createComponent();
      const value = { selectedIndex: 1, condition: makeCondition() };

      comp.conditionChanged(value);

      expect(conditionService.dirtyCondition).toBe(value);
   });

   it("junctionChanged should set conditionService.dirtyJunction", () => {
      const { comp, conditionService } = createComponent();
      const value = { selectedIndex: 1, junctionType: JunctionOperatorType.OR };

      comp.junctionChanged(value);

      expect(conditionService.dirtyJunction).toBe(value);
   });
});

// ---------------------------------------------------------------------------
// Group 5: conditionListChanged
// ---------------------------------------------------------------------------

describe("VSConditionDialog — conditionListChanged", () => {
   it("should always update model.conditionList and clear dirty state", () => {
      const { comp, conditionService } = createComponent({ checkTrap: () => {} });
      conditionService.dirtyCondition = { selectedIndex: 0, condition: makeCondition() };
      conditionService.dirtyJunction = { selectedIndex: 0, junctionType: JunctionOperatorType.AND };
      const newList = [makeCondition()];

      comp.conditionListChanged(newList);

      expect(comp.model.conditionList).toBe(newList);
      expect(conditionService.dirtyCondition).toBeNull();
      expect(conditionService.dirtyJunction).toBeNull();
   });

   it("should not check for traps when a checkTrap callback is provided", () => {
      const { comp, modelService } = createComponent({ checkTrap: () => {} });
      comp.conditionListCheckpoint = [];

      comp.conditionListChanged([makeCondition()]);

      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should not check for traps when the new condition list is invalid", () => {
      const { comp, modelService } = createComponent({ checkTrap: undefined });
      comp.conditionListCheckpoint = [];

      comp.conditionListChanged([makeCondition({ values: [{ type: ConditionValueType.VALUE, value: "" }] })]);

      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should not check for traps when the new condition list is empty", () => {
      const { comp, modelService } = createComponent({ checkTrap: undefined });
      comp.conditionListCheckpoint = [];

      comp.conditionListChanged([]);

      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should not check for traps when no checkpoint has been established yet", () => {
      const { comp, modelService } = createComponent({ checkTrap: undefined });
      comp.conditionListCheckpoint = undefined as any;

      comp.conditionListChanged([makeCondition()]);

      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should check for traps when the list is valid, non-empty, and a checkpoint exists", () => {
      const { comp, modelService } = createComponent({ checkTrap: undefined });
      comp.conditionListCheckpoint = [];

      comp.conditionListChanged([makeCondition()]);

      expect(modelService.sendModel).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: checkConditionTrap (private, exercised via ngOnInit(true)/conditionListChanged()
// entry points and directly via `as any` cast for the early-return guard)
// ---------------------------------------------------------------------------

describe("VSConditionDialog — checkConditionTrap", () => {
   it("should skip the HTTP call and refresh the checkpoint when not forced and the field set is unchanged", () => {
      const cond = makeCondition();
      const { comp, modelService } = createComponent({ checkTrap: () => {}, model: makeModel({ conditionList: [cond] }) });
      comp.conditionListCheckpoint = [cond];

      (comp as any).checkConditionTrap();

      expect(modelService.sendModel).not.toHaveBeenCalled();
      expect(comp.conditionListCheckpoint).toEqual([cond]);
   });

   it("should send the HTTP request when forced, even if the field set is unchanged", () => {
      const cond = makeCondition();
      const { comp, modelService } = createComponent({ checkTrap: () => {}, model: makeModel({ conditionList: [cond] }) });
      comp.conditionListCheckpoint = [cond];

      (comp as any).checkConditionTrap(true);

      expect(modelService.sendModel).toHaveBeenCalledWith(
         expect.stringContaining("check-condition-trap"),
         expect.objectContaining({ newConditionList: [cond], oldConditionList: [cond] }));
   });

   it("should do nothing when the server response has no body", async () => {
      const { comp, modelService } = createComponent({ checkTrap: () => {} });
      comp.conditionListCheckpoint = [];

      (comp as any).checkConditionTrap(true);

      await waitFor(() => expect(modelService.sendModel).toHaveBeenCalled());
      expect(comp.conditionListCheckpoint).toEqual([]);
   });

   it("should update the checkpoint and grayed-out fields without prompting when no trap is found", async () => {
      const trapFields = [makeDataRef("trapped")];
      const { comp, modelService } = createComponent({ checkTrap: () => {} });
      modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: { showTrap: false, trapFields } })));
      comp.provider = new VSConditionItemPaneProvider({} as any, "rt1", "Table1", "Table1", [], false, []);
      const newList = [makeCondition()];
      comp.model.conditionList = newList;

      (comp as any).checkConditionTrap(true);

      await waitFor(() => expect(comp.conditionListCheckpoint).toEqual(newList));
      expect(comp.provider.getGrayedOutFields()).toBe(trapFields);
   });

   it("should show the trap alert and revert the condition list when the user chooses undo", async () => {
      const oldList = [makeCondition({ field: makeDataRef("old") })];
      const newList = [makeCondition({ field: makeDataRef("new") })];
      const { comp, modelService } = createComponent({ checkTrap: () => {} });
      modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: { showTrap: true, trapFields: [] } })));
      const trapAlertSpy = vi.spyOn(ComponentTool, "showTrapAlert").mockResolvedValue("undo");
      comp.model.conditionList = newList;
      comp.conditionListCheckpoint = oldList;

      try {
         (comp as any).checkConditionTrap(true);

         await waitFor(() => expect(comp.model.conditionList).toEqual(oldList));
         // Reverted via Tool.clone(checkpoint), not the same reference as the checkpoint array.
         expect(comp.model.conditionList).not.toBe(oldList);
      }
      finally {
         trapAlertSpy.mockRestore();
      }
   });

   it("should keep the new list and update the checkpoint when the user dismisses the trap alert", async () => {
      const newList = [makeCondition()];
      const trapFields = [makeDataRef("trapped")];
      const { comp, modelService } = createComponent({ checkTrap: () => {} });
      modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: { showTrap: true, trapFields } })));
      const trapAlertSpy = vi.spyOn(ComponentTool, "showTrapAlert").mockResolvedValue("continue");
      comp.provider = new VSConditionItemPaneProvider({} as any, "rt1", "Table1", "Table1", [], false, []);
      comp.model.conditionList = newList;

      try {
         (comp as any).checkConditionTrap(true);

         await waitFor(() => expect(comp.conditionListCheckpoint).toEqual(newList));
         expect(comp.model.conditionList).toBe(newList);
         expect(comp.provider.getGrayedOutFields()).toBe(trapFields);
      }
      finally {
         trapAlertSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 7: shouldCheckTrap / getUniqueFields (private, via `as any` cast — no public wrapper)
// ---------------------------------------------------------------------------

describe("VSConditionDialog — shouldCheckTrap / getUniqueFields", () => {
   it("should return true when the number of unique fields differs", () => {
      const { comp } = createComponent();
      // getUniqueFields reads every other element (index 0, 2, 4, ...), treating odd indices as
      // junction entries — a bare array of adjacent conditions with no junction slot between
      // them would silently drop the second condition instead of counting two fields.
      comp.model.conditionList = [
         makeCondition({ field: makeDataRef("a") }),
         { jsonType: "junction", type: JunctionOperatorType.AND },
         makeCondition({ field: makeDataRef("b") }),
      ];
      comp.conditionListCheckpoint = [makeCondition({ field: makeDataRef("a") })];

      expect((comp as any).shouldCheckTrap()).toBe(true);
   });

   it("should return true when a field in the new list is not present in the old list", () => {
      const { comp } = createComponent();
      comp.model.conditionList = [makeCondition({ field: makeDataRef("a") })];
      comp.conditionListCheckpoint = [makeCondition({ field: makeDataRef("b") })];

      expect((comp as any).shouldCheckTrap()).toBe(true);
   });

   it("should return false when the field sets are identical", () => {
      const { comp } = createComponent();
      comp.model.conditionList = [makeCondition({ field: makeDataRef("a") })];
      comp.conditionListCheckpoint = [makeCondition({ field: makeDataRef("a") })];

      expect((comp as any).shouldCheckTrap()).toBe(false);
   });

   it("should collect FIELD-type condition values as fields too", () => {
      const { comp } = createComponent();
      const cond = makeCondition({
         field: makeDataRef("a"),
         values: [{ type: ConditionValueType.FIELD, value: makeDataRef("b") }],
      });

      const fields = (comp as any).getUniqueFields([cond]);

      expect(fields.sort()).toEqual(["a", "b"]);
   });

   it("should skip junction entries and only read every other (condition) element", () => {
      const { comp } = createComponent();
      const conditionList = [
         makeCondition({ field: makeDataRef("a") }),
         { jsonType: "junction", type: JunctionOperatorType.AND },
         makeCondition({ field: makeDataRef("b") }),
      ];

      const fields = (comp as any).getUniqueFields(conditionList);

      expect(fields.sort()).toEqual(["a", "b"]);
   });

   it("should ignore a condition with a null field", () => {
      const { comp } = createComponent();
      const cond = makeCondition({ field: null as any });

      const fields = (comp as any).getUniqueFields([cond]);

      expect(fields).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 8: getServerAppliedModel
// ---------------------------------------------------------------------------

describe("VSConditionDialog — getServerAppliedModel", () => {
   it("should strip the fields array while preserving tableName and conditionList", () => {
      const conditionList = [makeCondition()];
      const { comp } = createComponent({
         model: makeModel({ tableName: "T1", fields: [makeDataRef("a")], conditionList }),
      });

      const result = comp.getServerAppliedModel();

      expect(result).toEqual({ tableName: "T1", fields: [], conditionList });
   });
});
