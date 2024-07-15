/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpResponse } from "@angular/common/http";
import { fakeAsync, tick } from "@angular/core/testing";
import { of as observableOf } from "rxjs";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../../common/util/component-tool";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";
import { DragService } from "../../../../widget/services/drag.service";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSAssemblyGraphPaneComponent } from "./ws-assembly-graph-pane.component";

describe("Worksheet Assembly Graph Pane Test", () => {
   let graphPane: WSAssemblyGraphPaneComponent;
   let viewsheetClientService: any;
   let dragService: DragService;

   beforeEach(() => {
      viewsheetClientService = { sendEvent: jest.fn() };
      viewsheetClientService.commands = observableOf([]);
      dragService = new DragService();
      const zone: any = { run: jest.fn(), runOutsideAngular: jest.fn() };
      const dropdownService: any = { open: jest.fn() };
      const modalService: any = { open: jest.fn() };
      const modelService: any = { sendModel: jest.fn() };
      modelService.sendModel.mockImplementation(() => observableOf(new HttpResponse({body: null})));
      const renderer: any = {
         removeAttribute: jest.fn(),
         appendChild: jest.fn(),
         list: jest.fn(),
         setStyle: jest.fn(),
         removeChild: jest.fn()
      };
      const document: any = {
         getElementById: jest.fn()
      };
      graphPane = new WSAssemblyGraphPaneComponent(zone, viewsheetClientService,
         dragService, dropdownService, modalService, modelService, renderer, document);
      graphPane.worksheet = new Worksheet();

      const jspAssemblyGraph: any = { getContainer: jest.fn() };
      jspAssemblyGraph.getContainer.mockImplementation(() => ({scrollTop: 0, scrollLeft: 0}));
      graphPane.worksheet.jspAssemblyGraph = jspAssemblyGraph;
   });

   it("should open a confirm dialog for deleting primary assembly", fakeAsync(() => {
      const primaryAssembly: WSAssembly = {
         name: "Primary Assembly",
         description: "",
         top: 0,
         left: 0,
         dependeds: [],
         dependings: [],
         primary: true,
         info: undefined,
         classType: undefined
      };
      graphPane.worksheet.currentFocusedAssemblies = [primaryAssembly];
      const showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");

      showConfirmDialog.mockImplementation(() => Promise.resolve("delete"));

      graphPane.removeFocusedAssemblies();
      tick();
      expect(showConfirmDialog).toHaveBeenCalled();
      expect(viewsheetClientService.sendEvent).toHaveBeenCalled();
   }));

   xit("should not open a confirm dialog for a non-primary assembly", fakeAsync(() => { // broken test
      const nonPrimaryAssembly: WSAssembly = {
         name: "NonPrimary Assembly",
         description: "",
         top: 0,
         left: 0,
         dependeds: [],
         dependings: [],
         primary: false,
         info: undefined,
         classType: undefined
      };
      graphPane.worksheet.currentFocusedAssemblies = [nonPrimaryAssembly];
      const showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("delete"));

      graphPane.removeFocusedAssemblies();
      tick();
      expect(showConfirmDialog).not.toHaveBeenCalled();
      expect(viewsheetClientService.sendEvent).toHaveBeenCalled();
   }));

   it("should open a confirm dialog and not send event if cancelled", fakeAsync(() => {
      const primaryAssembly: WSAssembly = {
         name: "Primary Assembly",
         description: "",
         top: 0,
         left: 0,
         dependeds: [],
         dependings: [],
         primary: true,
         info: undefined,
         classType: undefined
      };
      graphPane.worksheet.currentFocusedAssemblies = [primaryAssembly];
      const showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");

      showConfirmDialog.mockImplementation(() => Promise.resolve("cancel"));

      graphPane.removeFocusedAssemblies();
      tick();
      expect(showConfirmDialog).toHaveBeenCalled();
      expect(viewsheetClientService.sendEvent).not.toHaveBeenCalled();
   }));

   xit("should be able to open certain asset entries", () => {
      const dragEvent: any = { preventDefault: jest.fn(), stopPropagation: jest.fn() };

      const worksheetAsset: AssetEntry = {
         identifier: "id",
         type: AssetType.WORKSHEET
      } as AssetEntry;
      dragService.put(AssetTreeService.getDragName(AssetType.WORKSHEET),
         JSON.stringify([worksheetAsset]));
      graphPane.drop(dragEvent);
      expect(viewsheetClientService.sendEvent).toHaveBeenCalled();
      viewsheetClientService.sendEvent.mockClear();

      const columnAsset: AssetEntry = { type: AssetType.COLUMN } as AssetEntry;
      dragService.put(AssetTreeService.getDragName(AssetType.COLUMN),
         JSON.stringify([columnAsset]));
      graphPane.drop(dragEvent);
      expect(viewsheetClientService.sendEvent).toHaveBeenCalled();
      viewsheetClientService.sendEvent.mockClear();

      const queryAsset: AssetEntry = { type: AssetType.QUERY } as AssetEntry;
      dragService.put(AssetTreeService.getDragName(AssetType.QUERY),
         JSON.stringify([queryAsset]));
      graphPane.drop(dragEvent);
      expect(viewsheetClientService.sendEvent).toHaveBeenCalled();
      viewsheetClientService.sendEvent.mockClear();
   });
});