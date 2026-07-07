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
 * QueryLinkPaneComponent - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - initDataSourceTree and nodeExpanded HTTP loading
 *   Group 2 [Risk 2] - selection and editing-table synchronization
 *   Group 3 [Risk 3] - findSelectedNode recursion and lazy child loading
 *   Group 4 [Risk 1] - iconFunction and graph-pane delegation
 */

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";

import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { GuiTool } from "../../../../../../../common/util/gui-tool";
import { QueryLinkPaneModel } from "../../../../../model/datasources/database/query/query-link-pane-model";
import { QueryTableModel } from "../../../../../model/datasources/database/query/query-table-model";
import { DataQueryModelService } from "../../data-query-model.service";
import { QueryLinkPaneComponent } from "./query-link-pane.component";

function makeTreeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "node",
      leaf: false,
      expanded: false,
      children: [],
      data: { path: "/root", qualifiedName: "CAT.SCHEMA.TABLE" },
      ...overrides,
   };
}

function makeQueryTable(overrides: Partial<QueryTableModel> = {}): QueryTableModel {
   return {
      name: "TABLE",
      alias: "Table",
      qualifiedName: "CAT.SCHEMA.TABLE",
      ...overrides,
   };
}

function makeLinkModel(overrides: Partial<QueryLinkPaneModel> = {}): QueryLinkPaneModel {
   return {
      tables: [makeQueryTable()],
      ...overrides,
   };
}

function createComponent() {
   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         { provide: DataQueryModelService, useValue: {} },
      ],
   });

   const comp = new QueryLinkPaneComponent(
      TestBed.inject(HttpClient),
      TestBed.inject(DataQueryModelService),
   );

   comp.databaseName = "Orders";
   comp.linkModel = makeLinkModel();

   return { comp, http: TestBed.inject(HttpClient) };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("QueryLinkPaneComponent - single pass", () => {
   describe("Group 1 - tree loading", () => {
      it("should load the datasource tree on init when no root is provided", () => {
         const { comp, http } = createComponent();
         const root = makeTreeNode({ label: "root" });
         vi.spyOn(http, "post").mockReturnValue(of(root));

         comp.ngOnInit();

         expect(http.post).toHaveBeenCalledWith(
            "../api/data/datasource/query/data-source-tree",
            null,
            expect.objectContaining({
               params: expect.anything(),
            }),
         );
         expect(comp.dataSourceTreeRoot).toBe(root);
      });

      it("should expand a node by loading its children when nodeExpanded is called", () => {
         const { comp, http } = createComponent();
         const node = makeTreeNode({ data: { path: "/root/t1", qualifiedName: "CAT.SCHEMA.T1" } });
         const response = makeTreeNode({ children: [makeTreeNode({ leaf: true })] });
         vi.spyOn(http, "post").mockReturnValue(of(response));

         comp.nodeExpanded(node);

         expect(node.children).toEqual(response.children);
      });
   });

   describe("Group 2 - selection state", () => {
      it("should synchronize graph selection and editingTable when selecting a node", () => {
         const { comp } = createComponent();
         comp.linkModel = makeLinkModel({
            tables: [
               makeQueryTable({ qualifiedName: "CAT.SCHEMA.MATCH" }),
               makeQueryTable({ qualifiedName: "CAT.SCHEMA.OTHER" }),
            ],
         });
         const node = makeTreeNode({
            leaf: true,
            data: { path: "/root/match", qualifiedName: "CAT.SCHEMA.MATCH" },
         });

         comp.selectNode(node);

         expect(comp.selectedGraphNodePath).toBe("/root/match");
         expect(comp.editingTable).toEqual(expect.objectContaining({ qualifiedName: "CAT.SCHEMA.MATCH" }));
      });

      it("should clear editingTable when changeEditingTableByName receives an empty name", () => {
         const { comp } = createComponent();
         comp.editingTable = makeQueryTable();

         comp.changeEditingTableByName(null);

         expect(comp.editingTable).toBeNull();
      });
   });

   describe("Group 3 - recursive selected-node lookup", () => {
      it("should select a lazy-loaded descendant and expand its parent path", () => {
         const { comp, http } = createComponent();
         const leaf = makeTreeNode({
            leaf: true,
            data: { path: "/root/folder/leaf", qualifiedName: "CAT.SCHEMA.LEAF" },
         });
         const folder = makeTreeNode({
            label: "folder",
            children: [],
            data: { path: "/root/folder", qualifiedName: "CAT.SCHEMA.FOLDER" },
         });
         comp.dataSourceTreeRoot = makeTreeNode({
            children: [folder],
            data: { path: "/root", qualifiedName: "ROOT" },
         });
         vi.spyOn(http, "post").mockReturnValue(of(makeTreeNode({ children: [leaf] })));

         comp.onNodeSelected("/root/folder/leaf");

         expect(folder.expanded).toBe(true);
         expect(folder.children).toEqual([leaf]);
         expect(comp.selectedNodes).toEqual([leaf]);
      });
   });

   describe("Group 4 - helpers and delegation", () => {
      it("should delegate iconFunction to GuiTool.getTreeNodeIconClass", () => {
         const { comp } = createComponent();
         const iconSpy = vi.spyOn(GuiTool, "getTreeNodeIconClass").mockReturnValue("db-icon");
         const node = makeTreeNode();

         expect(comp.iconFunction(node)).toBe("db-icon");
         expect(iconSpy).toHaveBeenCalledWith(node, "");
      });

      it("should delegate isJoinEditView to the graph pane reference", () => {
         const { comp } = createComponent();
         comp.graphPane = { isJoinEditView: vi.fn(() => true) } as never;

         expect(comp.isJoinEditView()).toBe(true);
      });
   });
});
