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
 * AdditionalTableSelectionPaneComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — addAdditionalTables / removeAdditionalTables emit
 *   Group 2 [Risk 2] — selectedTableLabel: cube prefix stripping
 *   Group 3 [Risk 2] — isMergeable / findCompatibleTables via ngOnChanges
 *   Group 4 [Risk 2] — ngOnChanges prunes incompatible additionalTables (async emit)
 *   Group 5 [Risk 2] — multi-column intersection, alias matching, assembly guard
 *
 * Direct instantiation — OnPush component, no template.
 */

import { SourceInfoType } from "../../../binding/data/source-info-type";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { AdditionalTableSelectionPaneComponent } from "./additional-table-selection-pane.component";

function column(table: string, attribute: string, dataType = "string"): OutputColumnRefModel {
   return { table, attribute, alias: null, dataType, refType: 0 } as OutputColumnRefModel;
}

function tableNode(name: string, columns: OutputColumnRefModel[]): TreeNodeModel {
   return {
      type: "table",
      label: name,
      children: columns.map(c => ({ label: c.attribute, data: c })),
   } as TreeNodeModel;
}

function createPane() {
   return new AdditionalTableSelectionPaneComponent();
}

function applyPaneInputs(comp: AdditionalTableSelectionPaneComponent): void {
   comp.ngOnChanges({
      tree: { previousValue: null, currentValue: comp.tree, firstChange: true, isFirstChange: () => true },
      selectedColumns: {
         previousValue: null,
         currentValue: comp.selectedColumns,
         firstChange: true,
         isFirstChange: () => true,
      },
      additionalTables: {
         previousValue: null,
         currentValue: comp.additionalTables,
         firstChange: true,
         isFirstChange: () => true,
      },
   });
}

describe("AdditionalTableSelectionPaneComponent — add/remove tables [Group 1, Risk 3]", () => {

   it("should move selected compatible tables to additionalTables on add", () => {
      const comp = createPane();
      comp.selectedTable = "Orders";
      comp.selectedColumns = [column("Orders", "id")];
      comp.additionalTables = [];
      comp.tree = {
         children: [
            tableNode("Orders", [column("Orders", "id")]),
            tableNode("Archive", [column("Archive", "id")]),
         ],
      } as TreeNodeModel;
      applyPaneInputs(comp);
      const emitSpy = vi.spyOn(comp.onAdditionalTablesChanged, "emit");
      comp.compatibleTableSelection.selectWithEvent(0, { ctrlKey: false, shiftKey: false } as MouseEvent);

      comp.addAdditionalTables();

      expect(emitSpy).toHaveBeenCalledWith(["Archive"]);
   });

   it("should remove selected additional tables on remove", () => {
      const comp = createPane();
      comp.additionalTables = ["T1", "T2", "T3"];
      comp.additionalTableSelection.setSize(3);
      comp.additionalTableSelection.selectWithEvent(1, { ctrlKey: false, shiftKey: false } as MouseEvent);
      const emitSpy = vi.spyOn(comp.onAdditionalTablesChanged, "emit");

      comp.removeAdditionalTables();

      expect(emitSpy).toHaveBeenCalledWith(["T1", "T3"]);
   });
});

describe("AdditionalTableSelectionPaneComponent — selectedTableLabel [Group 2, Risk 2]", () => {

   it("should strip cube prefix from selected table label", () => {
      const comp = createPane();
      comp.selectedTable = "___inetsoft_cube_Sales";

      expect(comp.selectedTableLabel).toBe("Sales");
   });

   it("should return selectedTable unchanged when not a cube id", () => {
      const comp = createPane();
      comp.selectedTable = "Orders";

      expect(comp.selectedTableLabel).toBe("Orders");
   });
});

describe("AdditionalTableSelectionPaneComponent — compatible tables [Group 3, Risk 2]", () => {

   it("should report isMergeable when compatible tables exist", () => {
      const comp = createPane();
      comp.compatibleTables = ["Archive"];

      expect(comp.isMergeable()).toBe(true);
   });

   it("should find compatible tables sharing column name and mergeable type", () => {
      const comp = createPane();
      comp.selectedTable = "Orders";
      comp.selectedColumns = [column("Orders", "id", "integer")];
      comp.additionalTables = [];
      comp.tree = {
         children: [
            tableNode("Orders", [column("Orders", "id", "integer")]),
            tableNode("Archive", [column("Archive", "id", "short")]),
            tableNode("Other", [column("Other", "name", "string")]),
         ],
      } as TreeNodeModel;

      applyPaneInputs(comp);

      expect(comp.compatibleTables).toContain("Archive");
      expect(comp.compatibleTables).not.toContain("Orders");
      expect(comp.compatibleTables).not.toContain("Other");
   });
});

describe("AdditionalTableSelectionPaneComponent — prune incompatible [Group 4, Risk 2]", () => {

   it("should emit pruned additionalTables when some are no longer compatible", async () => {
      const comp = createPane();
      comp.selectedTable = "Orders";
      comp.selectedColumns = [column("Orders", "id")];
      comp.additionalTables = ["Archive", "Stale"];
      comp.tree = {
         children: [
            tableNode("Orders", [column("Orders", "id")]),
            tableNode("Archive", [column("Archive", "id")]),
         ],
      } as TreeNodeModel;
      const emitSpy = vi.spyOn(comp.onAdditionalTablesChanged, "emit");

      comp.ngOnChanges({
         tree: { previousValue: null, currentValue: comp.tree, firstChange: true, isFirstChange: () => true },
         selectedColumns: {
            previousValue: [],
            currentValue: comp.selectedColumns,
            firstChange: false,
            isFirstChange: () => false,
         },
         additionalTables: {
            previousValue: [],
            currentValue: comp.additionalTables,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      await Promise.resolve();

      expect(emitSpy).toHaveBeenCalledWith(["Archive"]);
   });
});

describe("AdditionalTableSelectionPaneComponent — multi-column and guards [Group 5, Risk 2]", () => {

   it("should require all selected columns when finding compatible tables", () => {
      const comp = createPane();
      comp.selectedTable = "Orders";
      comp.selectedColumns = [
         column("Orders", "id"),
         column("Orders", "qty"),
      ];
      comp.additionalTables = [];
      comp.tree = {
         children: [
            tableNode("Orders", [column("Orders", "id"), column("Orders", "qty")]),
            tableNode("Partner", [column("Partner", "id"), column("Partner", "qty")]),
            tableNode("Archive", [column("Archive", "id")]),
         ],
      } as TreeNodeModel;

      applyPaneInputs(comp);

      expect(comp.compatibleTables).toContain("Partner");
      expect(comp.compatibleTables).not.toContain("Archive");
   });

   it("should match compatible tables using column alias instead of attribute", () => {
      const comp = createPane();
      comp.selectedTable = "Orders";
      comp.selectedColumns = [{
         table: "Orders",
         attribute: "id",
         alias: "orderId",
         dataType: "string",
         refType: 0,
      } as OutputColumnRefModel];
      comp.additionalTables = [];
      comp.tree = {
         children: [
            tableNode("Orders", [{
               table: "Orders", attribute: "id", alias: "orderId", dataType: "string", refType: 0,
            } as OutputColumnRefModel]),
            tableNode("Archive", [{
               table: "Archive", attribute: "legacyId", alias: "orderId", dataType: "string", refType: 0,
            } as OutputColumnRefModel]),
         ],
      } as TreeNodeModel;

      applyPaneInputs(comp);

      expect(comp.compatibleTables).toContain("Archive");
   });

   it("should return no compatible tables when selected columns are assembly type", () => {
      const comp = createPane();
      comp.selectedTable = "Orders";
      comp.selectedColumns = [{
         table: "Orders",
         attribute: "id",
         alias: null,
         dataType: "string",
         refType: 0,
         properties: { type: SourceInfoType.VS_ASSEMBLY },
      } as OutputColumnRefModel];
      comp.additionalTables = [];
      comp.tree = {
         children: [
            tableNode("Orders", [column("Orders", "id")]),
            tableNode("Archive", [column("Archive", "id")]),
         ],
      } as TreeNodeModel;

      applyPaneInputs(comp);

      expect(comp.compatibleTables).toEqual([]);
   });

   it("should report isMergeable when only additionalTables are present", () => {
      const comp = createPane();
      comp.compatibleTables = [];
      comp.additionalTables = ["T1"];

      expect(comp.isMergeable()).toBe(true);
   });

   it("should throw when addAdditionalTables is called with null additionalTables", () => {
      const comp = createPane();
      comp.compatibleTables = ["Archive"];
      comp.additionalTables = null;
      comp.compatibleTableSelection.setSize(1);
      comp.compatibleTableSelection.selectWithEvent(0, { ctrlKey: false, shiftKey: false } as MouseEvent);

      expect(() => comp.addAdditionalTables()).toThrow();
   });
});
