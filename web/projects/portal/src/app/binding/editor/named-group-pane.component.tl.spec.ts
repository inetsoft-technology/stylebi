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
 * NamedGroupPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnChanges/initGroups: deep-clone oldGroups into editable groups
 *   Group 2 [Risk 2] — getRootNode: group folders + Others partition for unassigned values
 *   Group 3 [Risk 2] — deleteEnabled: Others folder cannot be deleted
 *   Group 4 [Risk 3] — dropValue: move value between groups without duplication
 *   Group 5 [Risk 3] — addClick/deleteClick: group CRUD with name validation
 *
 * HTTP: no HTTP — tree drag-drop is in-memory only
 *
 * Out of scope:
 *   renameClick / editClick — open modal dialogs, covered via addClick/deleteClick contracts
 *   getGroupValues — private helper, covered via getRootNode tree structure
 */

import { NgZone } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetType } from "../../../../../shared/data/asset-type";
import { ComponentTool } from "../../common/util/component-tool";
import { DomService } from "../../widget/dom-service/dom.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { GroupCondition } from "../data/named-group-info";
import { NamedGroupPane } from "./named-group-pane.component";

function createGroup(name: string, values: any[] = []): GroupCondition {
   const g = new GroupCondition();
   g.name = name;
   g.value = values;
   return g;
}

function createPane(options: {
   oldGroups?: GroupCondition[];
   groups?: GroupCondition[];
   ngValues?: any[];
} = {}): NamedGroupPane {
   const comp = new NamedGroupPane({} as NgbModal, new NgZone({ enableLongStackTrace: false }), {} as DomService);
   comp.groups = options.groups ?? [];
   comp.oldGroups = options.oldGroups;
   comp.ngValues = options.ngValues ?? [];
   comp.ngOnChanges();
   return comp;
}

function folderNode(name: string, comp: NamedGroupPane): TreeNodeModel {
   return comp.root.children.find(c => c.data === name)!;
}

describe("NamedGroupPane — initGroups [Group 1, Risk 2]", () => {
   it("should clone oldGroups into groups on ngOnChanges", () => {
      const comp = createPane({
         oldGroups: [createGroup("East", ["NY", "NJ"])],
         groups: []
      });

      expect(comp.groups).toHaveLength(1);
      expect(comp.groups[0].name).toBe("East");
      expect(comp.groups[0].value).toEqual(["NY", "NJ"]);
      expect(comp.groups).not.toBe(comp.oldGroups);
   });

   it("should skip initGroups when oldGroups is empty", () => {
      const comp = createPane({ oldGroups: [], groups: [createGroup("West", ["CA"])] });

      expect(comp.groups).toHaveLength(1);
      expect(comp.groups[0].name).toBe("West");
   });
});

describe("NamedGroupPane — tree structure [Group 2, Risk 2]", () => {
   it("should build group folders and place unassigned values under Others", () => {
      const comp = createPane({
         groups: [createGroup("East", ["NY"])],
         ngValues: ["NY", "CA"]
      });

      expect(folderNode("East", comp).children).toHaveLength(1);
      const others = comp.root.children.find(c => c.data === "Others")!;
      expect(others.children.map(c => c.data)).toEqual(["CA"]);
   });
});

describe("NamedGroupPane — deleteEnabled [Group 3, Risk 2]", () => {
   it("should allow deleting custom group folders but not Others", () => {
      const comp = createPane({
         groups: [createGroup("East", ["NY"])],
         ngValues: ["NY", "CA"]
      });
      comp.selectNodes = [folderNode("East", comp)];

      expect(comp.deleteEnabled()).toBe(true);

      comp.selectNodes = [comp.root.children.find(c => c.data === "Others")!];
      expect(comp.deleteEnabled()).toBe(false);
   });
});

describe("NamedGroupPane — dropValue [Group 4, Risk 3]", () => {
   it("should move a value from one group to another", () => {
      const comp = createPane({
         groups: [createGroup("East", ["NY"]), createGroup("West", ["CA"])],
         ngValues: ["NY", "CA"]
      });
      comp.tree = {
         getParentNode: vi.fn(),
         deselectAllNodes: vi.fn()
      } as any;

      comp.selectNodes = [{
         dragData: "East^NY",
         data: "NY",
         leaf: true,
         type: AssetType.DATA + ""
      }];
      comp.dropToNode({ node: folderNode("West", comp) });

      expect(comp.groups.find(g => g.name === "East")!.value).toEqual([]);
      expect(comp.groups.find(g => g.name === "West")!.value).toContain("NY");
   });
});

describe("NamedGroupPane — addClick / deleteClick [Group 5, Risk 3]", () => {
   it("should add a new named group via dialog callback", () => {
      const comp = createPane({ groups: [] });
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _dialog, ok) => {
         ok("Midwest");
         return { title: "", existedNames: [] } as any;
      });

      comp.addClick({ stopPropagation: vi.fn() } as any);

      expect(comp.groups.some(g => g.name === "Midwest")).toBe(true);
      showDialogSpy.mockRestore();
   });

   it("should remove selected group folder on deleteClick", () => {
      const comp = createPane({ groups: [createGroup("East", ["NY"]), createGroup("West", ["CA"])] });
      comp.selectNodes = [folderNode("East", comp)];

      comp.deleteClick({} as MouseEvent);

      expect(comp.groups.some(g => g.name === "East")).toBe(false);
      expect(comp.selectNodes).toBeNull();
   });
});
