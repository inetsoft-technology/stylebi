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
 * ConditionPane - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - move and indent guard predicates
 *   Group 2 [Risk 1] - listPaneHeight, buttonText, emptyCondition
 *   Group 3 [Risk 2] - isConditionValid valid/invalid states
 *
 * Out of scope this pass: mutation methods and EventEmitter contracts.
 */

import { ElementRef } from "@angular/core";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { GuiTool } from "../../common/util/gui-tool";
import {
   createConditionPane,
   makeCondition,
   makeConditionList,
   makeJunction
} from "./condition-pane.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ConditionPane - move and indent guard predicates [Group 1, Risk 2]", () => {
   it("should report canMoveUp and canMoveDown for even condition selections only", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();

      comp.selectedIndex = 0;
      expect(comp.canMoveUp()).toBe(false);
      expect(comp.canMoveDown()).toBe(true);

      comp.selectedIndex = 2;
      expect(comp.canMoveUp()).toBe(true);
      expect(comp.canMoveDown()).toBe(true);

      comp.selectedIndex = 4;
      expect(comp.canMoveUp()).toBe(true);
      expect(comp.canMoveDown()).toBe(false);

      comp.selectedIndex = 1;
      expect(comp.canMoveUp()).toBe(false);
      expect(comp.canMoveDown()).toBe(false);
   });

   it("should report canIndent only for junctions with same-level siblings", () => {
      const { comp } = createConditionPane();
      comp.conditionList = makeConditionList();
      comp.selectedIndex = 1;

      expect(comp.canIndent()).toBe(true);

      comp.selectedIndex = 0;
      expect(comp.canIndent()).toBe(false);

      comp.selectedIndex = null;
      expect(comp.canIndent()).toBe(false);
   });

   it("should report canUnindent only for non-root junctions", () => {
      const { comp } = createConditionPane();
      comp.conditionList = [
         makeCondition({ level: 0 }),
         makeJunction(JunctionOperatorType.AND, 1),
         makeCondition({ level: 1 })
      ];

      comp.selectedIndex = 1;
      expect(comp.canUnindent()).toBe(true);

      comp.conditionList[1].level = 0;
      expect(comp.canUnindent()).toBe(false);

      comp.selectedIndex = 0;
      expect(comp.canUnindent()).toBe(false);
   });
});

describe("ConditionPane - display getters [Group 2, Risk 1]", () => {
   it("should calculate listPaneHeight from condition pane and item pane rectangles", () => {
      const { comp } = createConditionPane();
      const itemPane = {};
      const conditionPane = {};
      comp.fillParent = true;
      comp.itemPane = { nativeElement: itemPane } as ElementRef<any>;
      comp.conditionPane = { nativeElement: conditionPane } as ElementRef<any>;
      vi.spyOn(GuiTool, "getElementRect").mockImplementation((element: any) => {
         return {
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            width: 0,
            height: element === itemPane ? 25 : 90
         };
      });

      expect(comp.listPaneHeight).toBe(65);
   });

   it("should return NaN listPaneHeight when fillParent or panes are missing", () => {
      const { comp } = createConditionPane();

      expect(Number.isNaN(comp.listPaneHeight)).toBe(true);
   });

   it("should switch buttonText between Append and Insert based on list content", () => {
      const { comp } = createConditionPane();
      comp.conditionList = [];

      expect(comp.buttonText).toBe("_#(js:Append)");

      comp.conditionList = makeConditionList();
      expect(comp.buttonText).toBe("_#(js:Insert)");
   });

   it("should create the default empty condition", () => {
      const { comp } = createConditionPane();

      expect(comp.emptyCondition()).toEqual({
         jsonType: "condition",
         field: null,
         operation: ConditionOperation.EQUAL_TO,
         values: [],
         level: 1,
         equal: false,
         negated: false
      });
   });
});

describe("ConditionPane - isConditionValid [Group 3, Risk 2]", () => {
   it("should validate null operation without values and reject equal-to without values", () => {
      const { comp } = createConditionPane();

      (comp as any)._condition = makeCondition({
         operation: ConditionOperation.NULL,
         values: []
      });
      expect(comp.isConditionValid()).toBe(true);

      (comp as any)._condition = makeCondition({
         operation: ConditionOperation.EQUAL_TO,
         values: []
      });
      expect(comp.isConditionValid()).toBe(false);
   });

   it("should reject BETWEEN with only one value", () => {
      const { comp } = createConditionPane();
      (comp as any)._condition = makeCondition({
         operation: ConditionOperation.BETWEEN,
         values: [{ value: "A", type: "VALUE" as any }]
      });

      expect(comp.isConditionValid()).toBe(false);
   });
});
