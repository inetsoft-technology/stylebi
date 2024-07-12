/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NewAggrDialog } from "./new-aggr-dialog.component";
import { NewAggrDialogModel } from "./new-aggr-dialog-model";

describe("New Aggr Dialog Unit Test", () => {
   let createMockModel: () => NewAggrDialogModel = () => {
      return {
         id: "1",
         field: "state",
         fields: ["state", "id", "orderdate", "reseller"],
         fieldsType: ["string", "integer", "date", "boolean"],
         aggregate: "Count",
         with: "1",
         numValue: "1"
      };
   };
   let aggrDialog: NewAggrDialog;

   beforeEach(() => {
      aggrDialog = new NewAggrDialog();
   });

   //for Bug #19703
   // Bug #20255 load different value for different type
   it("should load right aggregate for different field type", () => {
      let model = createMockModel();
      aggrDialog.model = model;
      aggrDialog.fieldChanged("id");

      expect(model.aggregate).toEqual("Sum");
      expect(aggrDialog.formulas.length).toBe(23);
      expect(aggrDialog.isWithFormula()).toBe(false);

      model.aggregate = "First";
      expect(aggrDialog.isWithFormula()).toBe(true);
      model.aggregate = "Last";
      expect(aggrDialog.isWithFormula()).toBe(true);
      model.aggregate = "Correlation";
      expect(aggrDialog.isWithFormula()).toBe(true);
      model.aggregate = "Covariance";
      expect(aggrDialog.isWithFormula()).toBe(true);
      model.aggregate = "WeightedAverage";
      expect(aggrDialog.isWithFormula()).toBe(true);

      aggrDialog.fieldChanged("orderdate");
      expect(model.aggregate).toEqual("Count");
      expect(aggrDialog.isWithFormula()).toBe(false);
      expect(aggrDialog.formulas.length).toBe(11);
      expect(aggrDialog.formulas[0].value).toBe("Max");
      expect(aggrDialog.formulas[1].value).toBe("Min");
      expect(aggrDialog.formulas[2].value).toBe("Count");
      expect(aggrDialog.formulas[3].value).toBe("DistinctCount");
      expect(aggrDialog.formulas[4].value).toBe("First");
      expect(aggrDialog.formulas[5].value).toBe("Last");

      aggrDialog.fieldChanged("reseller");
      expect(model.aggregate).toEqual("Count");
      expect(aggrDialog.formulas.length).toBe(4);
      expect(aggrDialog.formulas[0].value).toBe("Count");
      expect(aggrDialog.formulas[1].value).toBe("DistinctCount");
      expect(aggrDialog.formulas[2].value).toBe("First");
      expect(aggrDialog.formulas[3].value).toBe("Last");
   });
});
