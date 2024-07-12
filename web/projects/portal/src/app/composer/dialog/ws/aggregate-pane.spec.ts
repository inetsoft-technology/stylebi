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
import { async, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ColumnRef } from "../../../binding/data/column-ref";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { TestUtils } from "../../../common/test/test-utils";
import { AggregateDialogModel } from "../../data/ws/aggregate-dialog-model";
import { AggregatePane } from "./aggregate-pane.component";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { FeatureFlagsService } from "../../../../../../shared/feature-flags/feature-flags.service";
import { of as observableOf } from "rxjs";

describe("Aggregate Pane Unit Test", () => {
   let createColumnRef: (name: string) => ColumnRef = (name: string) => {
      return {
         dataRefModel: TestUtils.createMockDataRef(name),
         alias: name,
         width: 1,
         visible: true,
         valid: true,
         sql: true,
         description: "",
      };
   };
   let createMockAggregateRef: (name: string) => AggregateRef = (name: string) => {
      return {
         ref: TestUtils.createMockDataRef(name),
         ref2: TestUtils.createMockDataRef(name),
         formulaName: "WeightedAverage",
         percentage: false,
      };
   };
   let createModel: () => AggregateDialogModel = () => {
      return {
         name: "customer_id",
         columns: [createColumnRef("customer_id")],
         info: {
            groups: [],
            aggregates: [createMockAggregateRef("customer_id")],
            secondaryAggregates: [],
            crosstab: false
         },
         groupMap: {
            "customer_id": []
         },
         aliasMap: {}
      };
   };
   let dateLevelExamplesService = { loadDateLevelExamples: jest.fn(() => observableOf()) };
   let featureFlagsService = { isFeatureEnabled: jest.fn() };

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NgbModule, ReactiveFormsModule, FormsModule
         ],
         declarations: [
            AggregatePane
         ],
         providers: [
            { provide: DateLevelExamplesService, useValue: dateLevelExamplesService },
            { provide: FeatureFlagsService, useValue: featureFlagsService }]
      });
      TestBed.compileComponents();
   }));

   //for Bug #18411, with label is missing when aggregate is "WeightedAverage"
   it("with label is missing when aggregate is 'WeightedAverage'", () => {
      let fixture = TestBed.createComponent(AggregatePane);
      let aggrPane = <AggregatePane>fixture.componentInstance;
      let dataRef = TestUtils.createMockColumnRef("customer_id");
      aggrPane.trapFields = [];
      aggrPane.model = createModel();
      aggrPane.refList = [dataRef];
      aggrPane.rows = [{
         selectedRef: dataRef,
         isGroup: false,
         group: "None",
         dgroup: 0,
         percentageOption: 0,
         aggregate: {
            formulaName: "WeightedAverage",
            label: "WeightedAverage",
            name: "WEIGHTED AVG",
            twoColumns: true,
            hasN: false,
            supportPercentage: true
         },
         aggregateRef: dataRef,
         percentage: false,
      }];
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let lbls = fixture.nativeElement.querySelectorAll(".form-horizontal label");
         let withLbl = Array.prototype.slice.call(lbls).filter(e => e.textContent.trim() == "with:")[0];
         expect(withLbl).not.toBeNull();
      });
   });

   //Bug #18542, default value for aggregate
   it("default value for aggregate column", () => {
      let fixture = TestBed.createComponent(AggregatePane);
      let aggrPane = <AggregatePane>fixture.componentInstance;
      let dataRef = TestUtils.createMockColumnRef("customer_id");
      aggrPane.trapFields = [];
      aggrPane.model = createModel();
      aggrPane.refList = [dataRef];
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let firstAggregate = fixture.nativeElement.querySelectorAll("select#aggregate")[0];
         let aggregateOps = firstAggregate.querySelectorAll("option");
         expect(aggregateOps[0].textContent.trim()).not.toBe("None");
      });
   });
});
