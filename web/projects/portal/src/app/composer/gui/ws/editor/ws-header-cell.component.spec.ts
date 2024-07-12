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
import { ColumnRef } from "../../../../binding/data/column-ref";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { AttributeRef } from "../../../../common/data/attribute-ref";
import { DataRef } from "../../../../common/data/data-ref";
import { BoundTableAssembly } from "../../../data/ws/bound-table-assembly";
import { ColumnInfo } from "../../../data/ws/column-info";
import { EmbeddedTableAssembly } from "../../../data/ws/embedded-table-assembly";
import { WSTableAssembly } from "../../../data/ws/ws-table-assembly";
import { WSTableAssemblyInfo } from "../../../data/ws/ws-table-assembly-info";
import { WSHeaderCell } from "./ws-header-cell.component";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";

describe("WS Header Cell Tests", () => {
   let hostRef;
   let worksheetClient;
   let dropdownService;
   let cell;
   let featureFlagService;

   beforeEach(() => {
      hostRef = { nativeElement: {} };
      worksheetClient = { sendEvent: jest.fn() };
      dropdownService = { open: jest.fn() };
      featureFlagService = { isFeatureEnabled: jest.fn() };
      cell = new WSHeaderCell(hostRef, worksheetClient, dropdownService, featureFlagService);
   });

   it("should disable embedded table actions when aggregate", () => {
      const ref: ColumnRef = {
         dataRefModel: {
            classType: "AttributeRef"
         } as DataRef
      } as ColumnRef;
      const column: ColumnInfo = {
         ref: ref,
         name: "aggregate",
         alias: "agg",
         visible: true,
         aggregate: true,
         group: false,
         crosstab: false
      } as ColumnInfo;

      const table: WSTableAssembly = {
         colInfos: [column],
         info: {
            aggregate: true,
            hasAggregate: true,
            privateSelection: [ref]
         } as WSTableAssemblyInfo
      } as WSTableAssembly;
      cell.table = new EmbeddedTableAssembly(table);
      cell.colInfo = column;

      const actionGroup: AssemblyActionGroup[] = cell.createActions();
      expect(actionGroup.length).toBe(1);
      const actions = actionGroup[0].actions;

      expect(actions.find((a) => a.id() === "worksheet table-header insert-column").enabled())
         .toBeFalsy();
      expect(actions.find((a) => a.id() === "worksheet table-header append-column").enabled())
         .toBeFalsy();
      /*
      expect(actions.find((a) => a.id() === "worksheet table-header column-type").enabled())
         .toBeFalsy();
      */
   });

   //Bug #19040 should display show all and delete column action for crosstab group
   //Bug #19035 should invisible hide column for crosstab group when column info is hidden
   it("check column actions when is crosstab", () => {
      const column: ColumnInfo = {
         ref: {classType: "ColumnRef", name: "Year(Date)", attribute: "Year(Date)", entity: null},
         name: "Year(Date)",
         alias: "",
         visible: true,
         aggregate: false,
         group: true,
         crosstab: true
      } as ColumnInfo;

      const table: WSTableAssembly = {
         colInfos: [ column ],
         info: {
            aggregate: true,
            hasAggregate: true,
            privateSelection: [
               {classType: "ColumnRef", name: "Order.Number", attribute: "Num", entity: "Order"},
               {classType: "ColumnRef", name: "Year(Date)", attribute: "Year(Date)", entity: null},
               {classType: "ColumnRef", name: "Order.Date", attribute: "Date", entity: "Order"},
               {classType: "ColumnRef", name: "Order.Discount", attribute: "Discount", entity: "Order"},
               {classType: "ColumnRef", name: "Order.Paid", attribute: "Paid", entity: "Order"} ],
            publicSelection: [
               {classType: "ColumnRef", name: "Year(Date)", attribute: "Year(Date)", entity: null},
               {classType: "ColumnRef", name: "0", attribute: "0", entity: null},
               {classType: "ColumnRef", name: "1", attribute: "1", entity: null}]
         } as WSTableAssemblyInfo
      } as WSTableAssembly;
      cell.table = new BoundTableAssembly(table);
      cell.colInfo = column;

      const actionGroup: AssemblyActionGroup[] = cell.createActions();
      expect(actionGroup.length).toBe(1);
      const actions = actionGroup[0].actions;

      expect(actions.find((a) => a.id() === "worksheet table-header show-all-columns").visible())
         .toBeTruthy();
      expect(actions.find((a) => a.id() === "worksheet table-header delete-columns").visible())
         .toBeTruthy();

      const column2: ColumnInfo = {
         ref: {classType: "ColumnRef", name: "0", attribute: "0", entity: null},
         name: "0",
         alias: "",
         visible: true,
         aggregate: false,
         group: false,
         crosstab: true
      } as ColumnInfo;
      cell.table.colInfos = [column2];
      cell.colInfo = column2;
      const actionGroup1: AssemblyActionGroup[] = cell.createActions();
      const actions1 = actionGroup1[0].actions;

      expect(actions1.find((a) => a.id() === "worksheet table-header hide-column").enabled())
         .toBeFalsy();
   });

   //Bug #19855
   it("should have column type action enabled when the column is a regular expression", () => {
      const ref: ColumnRef = {
         dataRefModel: {
            classType: "AttributeRef"
         } as DataRef,
         expression: true
      } as ColumnRef;
      const column: ColumnInfo = {
         ref: ref,
         name: "exp",
         alias: null,
         visible: true,
         aggregate: false,
         group: false,
         crosstab: false
      } as ColumnInfo;

      const table: WSTableAssembly = {
         colInfos: [column],
         aggregateInfo: {
            aggregates: []
         },
         info: {
            aggregate: true,
            hasAggregate: true,
            privateSelection: [ref]
         } as WSTableAssemblyInfo
      } as WSTableAssembly;
      cell.table = new BoundTableAssembly(table);
      cell.colInfo = column;

      const actionGroup: AssemblyActionGroup[] = cell.createActions();
      const actions = actionGroup[0].actions;

      expect(actions.find((a) => a.id() === "worksheet table-header column-type").enabled())
         .toBeTruthy();
   });

   //Bug #19855
   it("should have column type action disabled when the column is an aggregated expression", () => {
      const ref: ColumnRef = {
         dataRefModel: {
            classType: "AttributeRef"
         } as DataRef,
         expression: true,
         name: "exp"
      } as ColumnRef;
      const column: ColumnInfo = {
         ref: ref,
         name: "exp",
         alias: null,
         visible: true,
         aggregate: false,
         group: false,
         crosstab: false
      } as ColumnInfo;

      const table: WSTableAssembly = {
         colInfos: [column],
         aggregateInfo: {
            aggregates: [{
               name: "exp"
            }]
         },
         info: {
            aggregate: true,
            hasAggregate: true,
            privateSelection: [ref]
         } as WSTableAssemblyInfo
      } as WSTableAssembly;
      cell.table = new BoundTableAssembly(table);
      cell.colInfo = column;

      const actionGroup: AssemblyActionGroup[] = cell.createActions();
      const actions = actionGroup[0].actions;

      expect(actions.find((a) => a.id() === "worksheet table-header column-type").enabled())
         .toBeFalsy();
   });
});
