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
 * ConditionPane - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - init and input setter selection/reset contracts
 *   Group 2 [Risk 3] - condition/junction output contracts
 *   Group 3 [Risk 3] - insert/modify/save/delete/clear mutations
 *   Group 4 [Risk 2] - move/indent/unindent and expression rename mutations
 *
 * Out of scope this pass: pure predicate/display helpers.
 */

import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import {
   createConditionPane,
   makeCondition,
   makeConditionList,
   makeField,
   makeJunction
} from "./condition-pane.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ConditionPane - init and input setters [Group 1, Risk 3]", () => {
   it("should initialize empty list, empty condition, and OR junction when onlyOr is enabled", () => {
      const { comp } = createConditionPane();
      comp.onlyOr = true;

      comp.ngOnInit();

      expect(comp.junctionType).toBe(JunctionOperatorType.OR);
      expect(comp.conditionList).toEqual([]);
      expect(comp.condition).toEqual(comp.emptyCondition());
   });

   it("should select first or previously selected condition when conditionList is assigned", () => {
      const { comp } = createConditionPane();
      const list = makeConditionList();

      comp.conditionList = list;
      expect(comp.selectedIndex).toBe(0);
      expect(comp.condition.field.view).toBe("state");

      comp.selectedIndex = 2;
      comp.conditionList = list;
      expect(comp.selectedIndex).toBe(2);
      expect(comp.condition.field.view).toBe("city");
   });

   it("should reset to empty condition when conditionList becomes empty", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();

      comp.conditionList = [];

      expect(comp.selectedIndex).toBeNull();
      expect(comp.condition).toEqual(comp.emptyCondition());
   });

   it("should clear current condition when available fields no longer contain the selected field", () => {
      const { comp } = createConditionPane();
      (comp as any)._condition = makeCondition({ field: makeField("state") });
      comp.selectedIndex = null;

      comp.availableFields = [makeField("city")];

      expect(comp.condition).toEqual(comp.emptyCondition());
   });

   it("should keep current condition when available fields still contain the selected field", () => {
      const { comp } = createConditionPane();
      const state = makeField("state");
      (comp as any)._condition = makeCondition({ field: state });

      comp.availableFields = [{ ...state }];

      expect(comp.condition.field.view).toBe("state");
   });
});

describe("ConditionPane - condition and junction output contracts [Group 2, Risk 3]", () => {
   it("should emit changed condition for even selection and null for unchanged condition", () => {
      const { comp } = createConditionPane();
      const list = makeConditionList();
      comp.conditionList = list;
      const emitSpy = vi.spyOn(comp.conditionChange, "emit");

      comp.condition = makeCondition({ field: makeField("country") });
      expect(emitSpy).toHaveBeenLastCalledWith({
         selectedIndex: 0,
         condition: comp.condition
      });

      emitSpy.mockClear();
      comp.condition = list[0] as any;
      expect(emitSpy).toHaveBeenCalledWith(null);
   });

   it("should select junction rows, copy junction type, and emit dirty junction changes only when type differs", () => {
      const { comp } = createConditionPane();
      comp.conditionList = [
         makeCondition({ field: makeField("state") }),
         makeJunction(JunctionOperatorType.OR, 0),
         makeCondition({ field: makeField("city") })
      ];
      const junctionSpy = vi.spyOn(comp.junctionChange, "emit");

      comp.conditionItemSelected(1);

      expect(comp.selectedIndex).toBe(1);
      expect(comp.junctionType).toBe(JunctionOperatorType.OR);
      expect(junctionSpy).toHaveBeenCalledWith(null);

      junctionSpy.mockClear();
      comp.junctionType = JunctionOperatorType.AND;
      comp.updateDirtyJunction();

      expect(junctionSpy).toHaveBeenCalledWith({
         selectedIndex: 1,
         junctionType: JunctionOperatorType.AND
      });
   });
});

describe("ConditionPane - insert, modify, save, delete, and clear [Group 3, Risk 3]", () => {
   it("should insert valid condition and emit the new list", () => {
      const { comp } = createConditionPane();
      comp.conditionList = [];
      comp.condition = makeCondition({ field: makeField("state") });
      const emitSpy = vi.spyOn(comp.conditionListChange, "emit");

      expect(comp.insert()).toBe(true);

      expect(emitSpy).toHaveBeenCalledWith([
         expect.objectContaining({ field: expect.objectContaining({ view: "state" }), level: 0 })
      ]);
   });

   it("should modify selected condition while preserving its existing level", () => {
      const { comp } = createConditionPane();
      const list = makeConditionList();
      list[2].level = 3;
      comp.conditionList = list;
      comp.selectedIndex = 2;
      comp.condition = makeCondition({ field: makeField("country"), level: 99 });
      const emitSpy = vi.spyOn(comp.conditionListChange, "emit");

      comp.modify();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted[2]).toEqual(expect.objectContaining({
         field: expect.objectContaining({ view: "country" }),
         level: 3
      }));
   });

   it("should modify selected junction using current junction type and preserve level", () => {
      const { comp } = createConditionPane();
      const list = makeConditionList();
      list[1].level = 2;
      comp.conditionList = list;
      comp.selectedIndex = 1;
      comp.junctionType = JunctionOperatorType.OR;
      const emitSpy = vi.spyOn(comp.conditionListChange, "emit");

      comp.modify();

      expect(emitSpy.mock.calls[0][0][1]).toEqual({
         jsonType: "junction",
         type: JunctionOperatorType.OR,
         level: 2
      });
   });

   it("should return save options and save results for invalid, insert, and modify states", () => {
      const { comp } = createConditionPane();

      (comp as any)._condition = makeCondition({ values: [] });
      expect(comp.saveOption()).toBeNull();
      expect(comp.save()).toBeNull();

      (comp as any)._condition = makeCondition({ field: makeField("state") });
      comp.conditionList = [];
      comp.selectedIndex = null;
      expect(comp.saveOption()).toBe("insert");
      expect(comp.save()).toBe("insert");

      comp.conditionList = makeConditionList();
      comp.selectedIndex = 0;
      comp.condition = makeCondition({ field: makeField("city") });
      expect(comp.saveOption()).toBe("modify");
      expect(comp.save()).toBe("modify");
   });

   it("should delete selected condition, repair selection, and emit list", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();
      comp.selectedIndex = 4;
      const emitSpy = vi.spyOn(comp.conditionListChange, "emit");

      comp.delete();

      expect(comp.selectedIndex).toBe(2);
      expect(comp.condition.field.view).toBe("city");
      expect(emitSpy.mock.calls[0][0]).toHaveLength(3);
   });

   it("should clear selection, condition, and list", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();
      const emitSpy = vi.spyOn(comp.conditionListChange, "emit");

      comp.clear();

      expect(comp.selectedIndex).toBeNull();
      expect(comp.condition).toEqual(comp.emptyCondition());
      expect(emitSpy).toHaveBeenCalledWith([]);
   });
});

describe("ConditionPane - move, indent, unindent, and expression rename [Group 4, Risk 2]", () => {
   it("should move selected condition up and down while swapping levels", () => {
      const { comp } = createConditionPane();
      const list = makeConditionList();
      list[0].level = 0;
      list[2].level = 2;
      comp.conditionList = list;
      comp.selectedIndex = 2;
      let emitted: any[] = null;
      comp.conditionListChange.subscribe(value => emitted = value);

      comp.up();

      expect(comp.selectedIndex).toBe(0);
      expect(emitted[0].field.view).toBe("city");
      expect(emitted[0].level).toBe(0);
      expect(emitted[2].field.view).toBe("state");
      expect(emitted[2].level).toBe(2);

      comp.conditionList = emitted;
      comp.selectedIndex = 0;
      comp.down();

      expect(comp.selectedIndex).toBe(2);
   });

   it("should indent and unindent selected junction when guards allow it", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();
      comp.selectedIndex = 1;
      let emitted: any[] = null;
      comp.conditionListChange.subscribe(value => emitted = value);

      comp.indent();

      expect(emitted[1].level).toBe(1);

      comp.conditionList = emitted;
      comp.selectedIndex = 1;
      comp.unindent();

      expect(emitted[1].level).toBe(0);
   });

   it("should rename matching condition fields and emit the updated list", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();
      const emitSpy = vi.spyOn(comp.conditionListChange, "emit");

      comp.expressionRenamed({ oname: "city", nname: "city2" });

      expect(comp.conditionList[2].field).toEqual(expect.objectContaining({
         name: "city2",
         view: "city2",
         attribute: "city2"
      }));
      expect(comp.conditionList[0].field.view).toBe("state");
      expect(emitSpy).toHaveBeenCalledWith(comp.conditionList);
   });
});
