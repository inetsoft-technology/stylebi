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
import { Observable, of as observableOf } from "rxjs";
import { DataInputPaneModel } from "../../data/vs/data-input-pane-model";
import { DataInputPane, PopupEmbeddedTable } from "./data-input-pane.component";

let createPopupTable: () => Observable<any> = () => {
   return observableOf({
      tableName: "Regions",
      numRows: 4,
      numPages: 1,
      page: 1,
      rowData: [
         ["1", "Northeast"],
         ["2", "Midwest"],
         ["3", "South"],
         ["4", "West"]
      ],
      pageData: [
         ["1", "Northeast"],
         ["2", "Midwest"],
         ["3", "South"],
         ["4", "West"]
      ],
      columnHeaders: ["Code", "Region"]
   });
};
describe("Data Input Pane Popup Table Test", () => {
   const createModel: () => DataInputPaneModel = () => {
      return {
         table: "Regions",
         tableLabel: "Regions",
         rowValue: "4",
         columnValue: "Region",
         variable: false,
         defaultValue: "",
         writeBackDirectly: false,
         targetTree: {
            children: [
               {
                  children: [],
                  collapsedIcon: null,
                  cssClass: null,
                  data: "",
                  dataLabel: null,
                  dragData: null,
                  dragName: null,
                  expanded: false,
                  expandedIcon: null,
                  icon: null,
                  label: "None",
                  leaf: true,
                  toggleCollapsedIcon: null,
                  toggleExpandedIcon: null,
                  type: null,
               },
               {
                  children: [
                     {
                        children: [],
                        collapsedIcon: null,
                        cssClass: null,
                        data: "Divisions",
                        dataLabel: null,
                        dragData: null,
                        dragName: null,
                        expanded: false,
                        expandedIcon: null,
                        icon: null,
                        label: "Divisions",
                        leaf: true,
                        toggleCollapsedIcon: null,
                        toggleExpandedIcon: null,
                        type: null,
                     },
                     {
                        children: [],
                        collapsedIcon: null,
                        cssClass: null,
                        data: "RawData",
                        dataLabel: null,
                        dragData: null,
                        dragName: null,
                        expanded: false,
                        expandedIcon: null,
                        icon: null,
                        label: "RawData",
                        leaf: true,
                        toggleCollapsedIcon: null,
                        toggleExpandedIcon: null,
                        type: null,
                     },
                     {
                        children: [],
                        collapsedIcon: null,
                        cssClass: null,
                        data: "Regions",
                        dataLabel: null,
                        dragData: null,
                        dragName: null,
                        expanded: false,
                        expandedIcon: null,
                        icon: null,
                        label: "Regions",
                        leaf: true,
                        toggleCollapsedIcon: null,
                        toggleExpandedIcon: null,
                        type: null,
                     },
                  ],
                  collapsedIcon: null,
                  cssClass: null,
                  data: "Tables",
                  dataLabel: null,
                  dragData: null,
                  dragName: null,
                  expanded: false,
                  expandedIcon: null,
                  icon: null,
                  label: "Tables",
                  leaf: false,
                  toggleCollapsedIcon: null,
                  toggleExpandedIcon: null,
                  type: null,
               },
               {
                  children: [],
                  collapsedIcon: null,
                  cssClass: null,
                  data: "Variables",
                  dataLabel: null,
                  dragData: null,
                  dragName: null,
                  expanded: false,
                  expandedIcon: null,
                  icon: null,
                  label: "Variables",
                  leaf: false,
                  toggleCollapsedIcon: null,
                  toggleExpandedIcon: null,
                  type: null,
               }
            ],
            collapsedIcon: null,
            cssClass: null,
            data: null,
            dataLabel: null,
            dragData: null,
            dragName: null,
            expanded: true,
            expandedIcon: null,
            icon: null,
            label: null,
            leaf: false,
            toggleCollapsedIcon: null,
            toggleExpandedIcon: null,
            type: null,
         }
      };
   };

   let dataInputPane: DataInputPane;
   let httpService: any;

   beforeEach(() => {
      httpService = { get: jest.fn() };
      dataInputPane = new DataInputPane(httpService);
      dataInputPane.model = createModel();
      dataInputPane.runtimeId = "Viewsheet1";
      dataInputPane.variableValues = [];
   });

   // Bug 10264 make sure in 'Choose Cells' dialog can select the last row.
   it("should select the cell when clicked", () => {
      createPopupTable().subscribe(
         (data: PopupEmbeddedTable) => {
            dataInputPane.popupTable = data;
            dataInputPane.loadPopupTable();
         },
         (err) => {
            // TODO handle error loading table data
         }
      );

      httpService.get.mockImplementation(() => observableOf([null, "Northeast", "Midwest", "South", "West"]));
      dataInputPane.selectPopupCell(3, 1);
      expect(dataInputPane.selectedRow).toEqual("4 : West");
      expect(httpService.get).toHaveBeenCalled();
      expect(httpService.get.mock.calls[0][0]).toEqual("../vs/dataInput/rows/Viewsheet1/Regions/Region");
   });

});