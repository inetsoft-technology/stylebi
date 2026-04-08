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
import { RepositoryEntryHelper } from "./repository-entry-helper";
import { RepositoryEntryType } from "./repository-entry-type.enum";

function node(type: RepositoryEntryType, label = ""): any {
   return { type, label };
}

describe("RepositoryEntryHelper", () => {
   describe("getTypeMetaLabel", () => {
      it("returns Folders label for folder types", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.FOLDER))
            .toBe("_#(js:Folders)");
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.WORKSHEET_FOLDER))
            .toBe("_#(js:Folders)");
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.DASHBOARD_FOLDER))
            .toBe("_#(js:Folders)");
      });

      it("returns Viewsheets label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.VIEWSHEET))
            .toBe("_#(js:Viewsheets)");
      });

      it("returns Worksheets label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.WORKSHEET))
            .toBe("_#(js:Worksheets)");
      });

      it("returns Data Sources label when parent is not a data source", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.DATA_SOURCE))
            .toBe("_#(js:Data Sources)");
      });

      it("returns Additional Sources when parent is also a data source", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(
            RepositoryEntryType.DATA_SOURCE, RepositoryEntryType.DATA_SOURCE))
            .toBe("_#(js:Additional Sources)");
      });

      it("returns null for DATA_MODEL type", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.DATA_MODEL))
            .toBeNull();
      });

      it("returns Scripts label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.SCRIPT))
            .toBe("_#(js:Scripts)");
      });

      it("returns Table Styles label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.TABLE_STYLE))
            .toBe("_#(js:Table Styles)");
      });

      it("returns Queries label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.QUERY))
            .toBe("_#(js:Queries)");
      });

      it("returns Logical Models label when parent is not a logic model", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.LOGIC_MODEL))
            .toBe("_#(js:Logical Models)");
      });

      it("returns Extended Models label when parent is a logic model", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(
            RepositoryEntryType.LOGIC_MODEL, RepositoryEntryType.LOGIC_MODEL))
            .toBe("_#(js:Extended Models)");
      });

      it("returns Physical Views label when parent is not a partition", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.PARTITION))
            .toBe("_#(js:Physical Views)");
      });

      it("returns Extended Views label when parent is a partition", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(
            RepositoryEntryType.PARTITION, RepositoryEntryType.PARTITION))
            .toBe("_#(js:Extended Views)");
      });

      it("returns Virtual Private Models label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.VPM))
            .toBe("_#(js:Virtual Private Models)");
      });

      it("returns Dashboards label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.DASHBOARD))
            .toBe("_#(js:Dashboards)");
      });

      it("returns Schedule Tasks label", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.SCHEDULE_TASK))
            .toBe("_#(js:Schedule Tasks)");
      });

      it("returns Others for unrecognised type", () => {
         expect(RepositoryEntryHelper.getTypeMetaLabel(RepositoryEntryType.REPLET))
            .toBe("_#(js:Others)");
      });
   });

   describe("getNodeTypeMetaLabel", () => {
      it("returns Viewsheets for a viewsheet node", () => {
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(node(RepositoryEntryType.VIEWSHEET)))
            .toBe("_#(js:Viewsheets)");
      });

      it("normalises PARTITION flag combinations to PARTITION", () => {
         // AUTO_SAVE_VS has the PARTITION flag; should resolve to Physical Views
         const partitionNode = node(RepositoryEntryType.PARTITION);
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(partitionNode))
            .toBe("_#(js:Physical Views)");
      });

      it("identifies folder type via FOLDER bit", () => {
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(node(RepositoryEntryType.WORKSHEET_FOLDER)))
            .toBe("_#(js:Folders)");
      });

      it("strips AUTO_SAVE_FILE flag before looking up label for viewsheet", () => {
         // AUTO_SAVE_VS = AUTO_SAVE_FILE | VIEWSHEET
         const autoSaveVs = node(RepositoryEntryType.AUTO_SAVE_VS);
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(autoSaveVs))
            .toBe("_#(js:Viewsheets)");
      });

      it("strips AUTO_SAVE_FILE flag before looking up label for worksheet", () => {
         const autoSaveWs = node(RepositoryEntryType.AUTO_SAVE_WS);
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(autoSaveWs))
            .toBe("_#(js:Worksheets)");
      });

      it("maps DATA_SOURCE node with Data Model label to DATA_MODEL type", () => {
         const dsNode = node(RepositoryEntryType.DATA_SOURCE, "_#(js:Data Model)");
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(dsNode)).toBeNull();
      });

      it("maps DATA_SOURCE node without Data Model label to Data Sources", () => {
         const dsNode = node(RepositoryEntryType.DATA_SOURCE, "My Source");
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(dsNode))
            .toBe("_#(js:Data Sources)");
      });

      it("uses parent node type for context-dependent labels", () => {
         const childNode = node(RepositoryEntryType.LOGIC_MODEL);
         const parentNode = node(RepositoryEntryType.LOGIC_MODEL);
         expect(RepositoryEntryHelper.getNodeTypeMetaLabel(childNode, parentNode))
            .toBe("_#(js:Extended Models)");
      });
   });
});
