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
import { AssetType } from "../../../../../shared/data/asset-type";
import { TreeNodeModel } from "../tree/tree-node-model";
import { AssetTreeComponent } from "./asset-tree.component";
import { LoadAssetTreeNodesValidator } from "./load-asset-tree-nodes-validator";

let getAssetTreeNode: () => Observable<LoadAssetTreeNodesValidator> = () => {
   return observableOf({
         treeNodeModel: {
            children: [
               {
                  children: [],
                  collapsedIcon: null,
                  cssClass: null,
                  data: {type: AssetType.DATA_SOURCE_FOLDER},
                  dataLabel: "0^65605^__NULL__^/",
                  dragData: null,
                  dragName: "data_source_folder",
                  expanded: false,
                  expandedIcon: null,
                  icon: null,
                  label: "Data Source",
                  leaf: false,
                  toggleCollapsedIcon: null,
                  toggleExpandedIcon: null,
                  type: null,
               },
               {
                  children: [],
                  collapsedIcon: null,
                  cssClass: null,
                  data: {},
                  dataLabel: "1^1^__NULL__^/",
                  dragData: null,
                  dragName: "folder",
                  expanded: false,
                  expandedIcon: null,
                  icon: null,
                  label: "Global Worksheet",
                  leaf: false,
                  toggleCollapsedIcon: null,
                  toggleExpandedIcon: null,
                  type: null,
               }
            ],
            collapsedIcon: null,
            cssClass: null,
            data: {},
            dataLabel: null,
            dragData: null,
            dragName: null,
            expanded: false,
            expandedIcon: null,
            icon: null,
            label: null,
            leaf: false,
            toggleCollapsedIcon: null,
            toggleExpandedIcon: null,
            type: null
         },
         parameters: []
      }
      );
};
describe("Asset Tree Component Unit Test", () => {
   const createModel: () => TreeNodeModel = () => {
      return {
         children: [
            {
               children: [],
               collapsedIcon: null,
               cssClass: null,
               data: {},
               dataLabel: "1^1^__NULL__^/",
               dragData: null,
               dragName: "folder",
               expanded: true,
               expandedIcon: null,
               icon: null,
               label: "Global Worksheet",
               leaf: false,
               toggleCollapsedIcon: null,
               toggleExpandedIcon: null,
               type: null,
            }
         ],
         collapsedIcon: null,
         cssClass: null,
         data: {},
         dataLabel: null,
         dragData: null,
         dragName: null,
         expanded: false,
         expandedIcon: null,
         icon: null,
         label: null,
         leaf: false,
         toggleCollapsedIcon: null,
         toggleExpandedIcon: null,
         type: null
      };
   };

   let assetTreeComponent: AssetTreeComponent;
   let assetTreeService: any;
   let changeDetector: any;
   let assetClientService: any;
   let modalService: any;
   let debounceService: any;
   let zone: any;

   beforeEach(() => {
      assetTreeService = { getAssetTreeNode: jest.fn() };
      changeDetector = { markForCheck: jest.fn() };
      assetClientService = { connect: jest.fn() };
      modalService = { open: jest.fn() };
      debounceService = { debounce: jest.fn() };
      zone = { run: jest.fn() };

      assetTreeComponent = new AssetTreeComponent(assetTreeService, changeDetector, assetClientService, modalService, debounceService, zone);
      assetTreeService.getAssetTreeNode.mockImplementation(() => getAssetTreeNode());
      assetTreeComponent.root = createModel();
   });

   // Bug 10264 make sure asset tree node remain expanded.
   it("should persist node expand status after datasource change", () => {
      assetTreeComponent.datasources = true;
      assetTreeComponent.addDeleteDataSources();
      expect(assetTreeComponent.root.children[1].expanded).toBe(true);
   });
});