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
 * Bug #81634: a highlight condition whose field is no longer bound in the chart is marked as
 * unbound in the condition field combo. The mark is display only: the field is never replaced
 * and nothing is emitted.
 */
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { ConditionFieldComboComponent } from "./condition-field-combo.component";
import { ConditionFieldComboModel } from "./condition-field-combo-model";
import { ConditionItemPane } from "./condition-item-pane.component";

function makeRef(view: string): DataRef {
   return {
      classType: "ColumnRef",
      name: view,
      view,
      attribute: view,
      entity: "Order",
      dataType: XSchema.STRING,
      description: `${view} description`
   };
}

function makeModel(fields: DataRef[]): ConditionFieldComboModel {
   return {
      list: fields,
      tree: { label: "_#(js:root)", children: fields.map(f => ({ label: f.view, data: f, leaf: true })) }
   };
}

describe("ConditionFieldComboComponent unbound state [Bug #81634]", () => {
   let fixture: ComponentFixture<ConditionFieldComboComponent>;
   const bound = makeRef("Year(Order:Date)");
   const stale = makeRef("QuarterOfYear(Order:Date)");

   function create(field: DataRef, unbound: boolean): HTMLElement {
      fixture = TestBed.createComponent(ConditionFieldComboComponent);
      fixture.componentRef.setInput("fieldsModel", makeModel([bound]));
      fixture.componentRef.setInput("field", field);
      fixture.componentRef.setInput("unbound", unbound);
      fixture.detectChanges();
      return fixture.nativeElement.querySelector(".condition-field-combo-toggle");
   }

   beforeEach(() => {
      TestBed.configureTestingModule({ imports: [ConditionFieldComboComponent] });
   });

   it("marks a stale field as invalid and explains why, still showing its name", () => {
      const toggle = create(stale, true);
      const text = toggle.querySelector(".condition-field-combo-text");

      expect(toggle.classList.contains("is-invalid")).toBe(true);
      expect(text.textContent).toContain("QuarterOfYear(Order:Date)");
      expect(text.getAttribute("title")).toContain("not bound");
   });

   it("does not mark a bound field", () => {
      const toggle = create(bound, false);

      expect(toggle.classList.contains("is-invalid")).toBe(false);
      expect(toggle.querySelector(".condition-field-combo-text").getAttribute("title"))
         .toBe("Year(Order:Date) description");
   });

   it("neither replaces the field nor emits when unbound", () => {
      fixture = TestBed.createComponent(ConditionFieldComboComponent);
      const emitted = vi.fn();
      fixture.componentInstance.onSelectField.subscribe(emitted);
      fixture.componentRef.setInput("fieldsModel", makeModel([bound]));
      fixture.componentRef.setInput("field", stale);
      fixture.componentRef.setInput("unbound", true);
      fixture.detectChanges();

      expect(fixture.componentInstance.field).toBe(stale);
      expect(emitted).not.toHaveBeenCalled();
   });
});

describe("ConditionItemPane.isFieldUnbound [Bug #81634]", () => {
   const isUnbound = (condition: any, unboundFields: string[]) =>
      ConditionItemPane.prototype.isFieldUnbound.call({ condition, unboundFields });

   it("is true only for a field reported by the server", () => {
      const cond = { field: makeRef("QuarterOfYear(Order:Date)") };

      expect(isUnbound(cond, ["QuarterOfYear(Order:Date)"])).toBe(true);
      expect(isUnbound(cond, ["Other"])).toBe(false);
      expect(isUnbound(cond, [])).toBe(false);
      expect(isUnbound(cond, undefined)).toBe(false);
   });

   it("is false once the user picked another field or has no field", () => {
      expect(isUnbound({ field: makeRef("Year(Order:Date)") },
                       ["QuarterOfYear(Order:Date)"])).toBe(false);
      expect(isUnbound({ field: null }, ["QuarterOfYear(Order:Date)"])).toBe(false);
      expect(isUnbound(null, ["QuarterOfYear(Order:Date)"])).toBe(false);
   });
});
