/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { ErrorHandler } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { QueryConditionsPaneComponent } from "./query-conditions-pane.component";
import { ClauseModel } from "../../../../../model/datasources/database/vpm/condition/clause/clause-model";
import { OperationModel } from "../../../../../model/datasources/database/vpm/condition/clause/operation-model";
import { ClauseValueTypes } from "../../../../../model/datasources/database/vpm/condition/clause/clause-value-types";
import { XSchema } from "../../../../../../../common/data/xschema";

describe("QueryConditionsPaneComponent", () => {
   const operations: OperationModel[] = [
      { name: "equal to", symbol: "=" },
      { name: "one of", symbol: "IN" },
      { name: "greater than", symbol: ">" }
   ];

   let fixture: ComponentFixture<QueryConditionsPaneComponent>;
   let component: QueryConditionsPaneComponent;
   let errors: any[];

   const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
   };

   const setUp = async (expression: string, symbol: string) => {
      errors = [];
      TestBed.configureTestingModule({
         imports: [QueryConditionsPaneComponent],
         providers: [
            provideHttpClient(),
            provideHttpClientTesting(),
            { provide: ErrorHandler, useValue: { handleError: (e: any) => errors.push(e) } }
         ]
      });

      const field = { name: "ORDERS.QUANTITY", type: XSchema.INTEGER,
         columnName: "QUANTITY", tableName: "ORDERS" };
      const condition: ClauseModel = {
         type: "clause",
         junc: false,
         level: 0,
         negated: false,
         operation: operations.find((op) => op.symbol == symbol),
         value1: { type: ClauseValueTypes.FIELD, expression: field.name, field },
         value2: { type: ClauseValueTypes.VALUE, expression },
         value3: { type: ClauseValueTypes.VALUE, expression: null }
      };

      fixture = TestBed.createComponent(QueryConditionsPaneComponent);
      component = fixture.componentInstance;
      component.model = { fields: [field], conditions: [condition] };
      component.operations = operations;
      component.sessionOperations = [];
      component.databaseName = "db";
      await settle();
   };

   const element = (): HTMLElement => fixture.nativeElement;

   const typeNumber = async (val: string) => {
      const input = element().querySelector("input[type=number]") as HTMLInputElement;
      input.value = val;
      input.dispatchEvent(new Event("input"));
      await settle();
   };

   const changeOperator = async (name: string) => {
      const select = element().querySelector("select.operation_check_id") as HTMLSelectElement;
      select.selectedIndex = Array.from(select.options)
         .findIndex((option) => option.textContent.trim() == name);
      select.dispatchEvent(new Event("change"));
      await settle();
   };

   // the last Modify button is the condition list's, the one-of editor has its own
   const modifyButton = (container: Element = element()): HTMLButtonElement =>
      (Array.from(container.querySelectorAll("button")) as HTMLButtonElement[])
         .filter((button) => button.textContent.trim() == "_#(Modify)").pop();

   const savedValue = () => (<ClauseModel> component.model.conditions[0]).value2.expression;

   // Bug #77890
   it("should save a typed 0 after the operator changes", async () => {
      await setUp("3", "=");
      await typeNumber("0");

      await changeOperator("greater than");

      expect(errors).toEqual([]);
      expect(modifyButton().disabled).toBe(false);

      modifyButton().click();
      await settle();

      expect(savedValue()).toBe("0");
      expect((<ClauseModel> component.model.conditions[0]).operation.symbol).toBe(">");
   });

   it("should save a typed number in parentheses after the operator changes to one of", async () => {
      await setUp("3", "=");
      await typeNumber("5");

      await changeOperator("one of");

      expect(errors).toEqual([]);

      modifyButton().click();
      await settle();

      expect(savedValue()).toBe("(5)");
   });

   it("should save a one of value modified to 0", async () => {
      await setUp("(1,2)", "IN");
      (element().querySelector(".value-list .unhighlightable") as HTMLElement).click();
      await settle();
      await typeNumber("0");

      modifyButton(element().querySelector(".value-list-container")).click();
      await settle();
      modifyButton().click();
      await settle();

      expect(savedValue()).toBe("(0,2)");
   });
});
