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
 * DatasourcesXmlaComponent — Pass 1: Interaction tests (pure logic, no HTTP).
 *
 * Covers: refreshMetaLabel, selectedDimensionMember, drillString, selectedDimension,
 * form initialization and updateEnable, model setter, selectCatalog, getCSSIcon,
 * selectedNode (DIMENSION/MEASURE/LEVEL), changeAsDate/changeLocal/changeDatePattern,
 * changeOriginalOrder, canDeactivate, close, memory leak.
 *
 * HTTP flows (ngOnInit editing/new, loadCatalogs, loadMetadata, testDatabase,
 * updateFormatString, ok()) → Pass 2 (risk).
 *
 * Mocking strategy: ActivatedRoute.paramMap is a Subject that is never emitted here,
 * so ngOnInit subscribes without triggering HTTP. Component state is set directly.
 * No MSW is used in this file.
 */

import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { CubeItemType } from "../../model/datasources/database/cube/xmla/cube-item-type";
import {
   lastRenderedFixture,
   makeCube,
   makeCubeItemData,
   makeDimension,
   makeDimMember,
   makeDomain,
   makeMeasure,
   makeMetaInfo,
   makeTreeNode,
   makeXmlaModel,
   MODAL_MOCK,
   ROUTER_MOCK,
   renderXmla,
   resetMocks,
} from "./datasources-xmla.component.test-helpers";

beforeEach(() => {
   resetMocks();
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});
afterEach(() => {
   vi.restoreAllMocks();
   lastRenderedFixture?.destroy();
});

// ── Group 1: refreshMetaLabel ─────────────────────────────────────────────

describe("DatasourcesXmla — refreshMetaLabel", () => {

   it("returns 'Retrieve Metadata' when domain is null", async () => {
      const { comp } = await renderXmla({ model: makeXmlaModel({ domain: null }) });
      expect(comp.refreshMetaLabel).toContain("Retrieve Metadata");
   });

   it("returns 'Refresh Metadata' when domain.cubes is an empty array ([] is truthy)", async () => {
      const { comp } = await renderXmla({ model: makeXmlaModel({ domain: makeDomain([]) }) });
      expect(comp.refreshMetaLabel).toContain("Refresh Metadata");
   });

   it("returns 'Refresh Metadata' when domain has cubes", async () => {
      const { comp } = await renderXmla({
         model: makeXmlaModel({ domain: makeDomain([makeCube()]) }),
      });
      expect(comp.refreshMetaLabel).toContain("Refresh Metadata");
   });
});

// ── Group 2: selectedDimensionMember ─────────────────────────────────────

describe("DatasourcesXmla — selectedDimensionMember", () => {

   it("returns false when selectedCubeNode is null", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = null;
      expect(comp.selectedDimensionMember).toBe(false);
   });

   it("returns false when selectedCubeNode has no member", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = {};
      expect(comp.selectedDimensionMember).toBe(false);
   });

   it("returns true when member.classType is CubeDimMemberModel", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = { member: makeDimMember({ classType: "CubeDimMemberModel" }) };
      expect(comp.selectedDimensionMember).toBe(true);
   });

   it("returns false when member.classType is CubeMeasureModel", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = { member: makeMeasure({ classType: "CubeMeasureModel" }) };
      expect(comp.selectedDimensionMember).toBe(false);
   });
});

// ── Group 3: drillString ──────────────────────────────────────────────────

describe("DatasourcesXmla — drillString", () => {

   it("returns 'None' when no member is selected", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = {};
      expect(comp.drillString).toBe("None");
   });

   it("returns 'None' when drillInfo paths is empty", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = {
         member: makeDimMember({ metaInfo: makeMetaInfo({ drillInfo: { paths: [] } as any }) }),
      };
      expect(comp.drillString).toBe("None");
   });

   it("returns joined path names when drillInfo has paths", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = {
         member: makeDimMember({
            metaInfo: makeMetaInfo({
               drillInfo: { paths: [{ name: "Path1" }, { name: "Path2" }] as any },
            }),
         }),
      };
      expect(comp.drillString).toBe("Path1, Path2");
   });
});

// ── Group 4: selectedDimension ────────────────────────────────────────────

describe("DatasourcesXmla — selectedDimension", () => {

   it("returns null when no dimension is selected", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = {};
      expect(comp.selectedDimension).toBeNull();
   });

   it("returns the dimension when one is selected", async () => {
      const { comp } = await renderXmla();
      const dim = makeDimension({ uniqueName: "[Date]" });
      comp.selectedCubeNode = { dimension: dim };
      expect(comp.selectedDimension).toBe(dim);
   });
});

// ── Group 5: form initialization ──────────────────────────────────────────

describe("DatasourcesXmla — form initialization", () => {

   it("name control has required validator", async () => {
      const { comp } = await renderXmla();
      comp.form.get("name").setValue("");
      expect(comp.form.get("name").invalid).toBe(true);
   });

   it("url control has required validator", async () => {
      const { comp } = await renderXmla();
      comp.form.get("url").setValue("");
      expect(comp.form.get("url").invalid).toBe(true);
   });

   it("catalog control has required validator", async () => {
      const { comp } = await renderXmla();
      comp.form.get("catalog").setValue("");
      expect(comp.form.get("catalog").invalid).toBe(true);
   });

   it("description is not required", async () => {
      const { comp } = await renderXmla();
      comp.form.get("description").setValue("");
      expect(comp.form.get("description").valid).toBe(true);
   });
});

// ── Group 6: updateEnable — credential fields ──────────────────────────────

describe("DatasourcesXmla — updateEnable", () => {

   it("disables user/password/credentialId when requiresLogin is false", async () => {
      const { comp } = await renderXmla();
      comp.form.get("requiresLogin").setValue(false);

      expect(comp.form.get("user").disabled).toBe(true);
      expect(comp.form.get("password").disabled).toBe(true);
      expect(comp.form.get("credentialId").disabled).toBe(true);
   });

   it("enables user/password/credentialId when requiresLogin is true", async () => {
      const { comp } = await renderXmla();
      comp.form.get("requiresLogin").setValue(true);

      expect(comp.form.get("user").disabled).toBe(false);
      expect(comp.form.get("password").disabled).toBe(false);
   });
});

// ── Group 7: model setter ──────────────────────────────────────────────────

describe("DatasourcesXmla — model setter", () => {

   it("updates the form name control when model is set", async () => {
      const { comp } = await renderXmla();
      comp.model = makeXmlaModel({ name: "NewXmla" });
      expect(comp.form.get("name").value).toBe("NewXmla");
   });

   it("updates the form url control when model is set", async () => {
      const { comp } = await renderXmla();
      comp.model = makeXmlaModel({ url: "http://new-server/xmla" });
      expect(comp.form.get("url").value).toBe("http://new-server/xmla");
   });
});

// ── Group 8: selectCatalog ────────────────────────────────────────────────

describe("DatasourcesXmla — selectCatalog", () => {

   it("sets the form catalog control to the given value", async () => {
      const { comp } = await renderXmla();
      comp.selectCatalog("AdventureWorks");
      expect(comp.form.get("catalog").value).toBe("AdventureWorks");
   });
});

// ── Group 9: getCSSIcon ────────────────────────────────────────────────────

describe("DatasourcesXmla — getCSSIcon", () => {

   it("returns cube-icon for CUBE type", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.CUBE }) });
      expect(comp.getCSSIcon(node)).toBe("cube-icon");
   });

   it("returns dimension-icon for DIMENSION type without hierarchy", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.DIMENSION, hierarchy: false }) });
      expect(comp.getCSSIcon(node)).toBe("dimension-icon");
   });

   it("returns column-icon for DIMENSION with hierarchy and !userDefined", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.DIMENSION, hierarchy: true, userDefined: false }) });
      expect(comp.getCSSIcon(node)).toBe("column-icon");
   });

   it("returns user-group-icon for DIMENSION with hierarchy and userDefined", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.DIMENSION, hierarchy: true, userDefined: true }) });
      expect(comp.getCSSIcon(node)).toBe("user-group-icon");
   });

   it("returns measure-icon for MEASURE type", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.MEASURE }) });
      expect(comp.getCSSIcon(node)).toBe("measure-icon");
   });

   it("returns shape-circle-icon for LEVEL type", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.LEVEL }) });
      expect(comp.getCSSIcon(node)).toBe("shape-circle-icon");
   });

   it("returns dimension-icon for DIMENSION_FOLDER type", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: CubeItemType.DIMENSION_FOLDER }) });
      expect(comp.getCSSIcon(node)).toBe("dimension-icon");
   });

   it("returns folder-icon when type is unrecognized", async () => {
      const { comp } = await renderXmla();
      const node = makeTreeNode({ data: makeCubeItemData({ type: "unknown" }) });
      expect(comp.getCSSIcon(node)).toBe("folder-icon");
   });
});

// ── Group 10: selectedNode ────────────────────────────────────────────────

describe("DatasourcesXmla — selectedNode", () => {

   it("sets selectedCubeNode to {} when nodes is empty", async () => {
      const { comp } = await renderXmla();
      comp.model = makeXmlaModel({ domain: makeDomain([makeCube()]) });
      comp.selectedNode([]);
      expect(comp.selectedCubeNode).toEqual({});
   });

   it("sets selectedCubeNode.dimension for DIMENSION node", async () => {
      const dim = makeDimension({ uniqueName: "[Date]" });
      const cube = makeCube({ name: "SalesCube", dimensions: [dim] });
      const { comp } = await renderXmla({ model: makeXmlaModel({ domain: makeDomain([cube]) }) });

      const node = makeTreeNode({
         data: makeCubeItemData({ type: CubeItemType.DIMENSION, cubeName: "SalesCube", uniqueName: "[Date]" }),
      });

      comp.selectedNode([node]);

      expect(comp.selectedCubeNode.dimension).toBe(dim);
      expect(comp.selectedCubeNode.cube).toBe(cube);
   });

   it("sets selectedCubeNode.member for MEASURE node", async () => {
      const measure = makeMeasure({ uniqueName: "[Measures].[Sales]" });
      const cube = makeCube({ name: "SalesCube", measures: [measure] });
      const { comp } = await renderXmla({ model: makeXmlaModel({ domain: makeDomain([cube]) }) });

      const node = makeTreeNode({
         data: makeCubeItemData({ type: CubeItemType.MEASURE, cubeName: "SalesCube", uniqueName: "[Measures].[Sales]" }),
      });

      comp.selectedNode([node]);

      expect(comp.selectedCubeNode.member).toBe(measure);
   });

   it("sets selectedCubeNode.member for LEVEL node (CubeDimMemberModel)", async () => {
      const level = makeDimMember({ uniqueName: "[Date].[Year]", classType: "CubeDimMemberModel" });
      const dim = makeDimension({ members: [level] } as any);
      const cube = makeCube({ name: "SalesCube", dimensions: [dim] });
      const { comp } = await renderXmla({ model: makeXmlaModel({ domain: makeDomain([cube]) }) });

      const node = makeTreeNode({
         data: makeCubeItemData({ type: CubeItemType.LEVEL, cubeName: "SalesCube", uniqueName: "[Date].[Year]" }),
      });

      comp.selectedNode([node]);

      expect(comp.selectedCubeNode.member).toBe(level);
   });

   it("initializes metaInfo on member when it is null", async () => {
      const measure = makeMeasure({ uniqueName: "[Measures].[Count]", metaInfo: null });
      const cube = makeCube({ name: "SalesCube", measures: [measure] });
      const { comp } = await renderXmla({ model: makeXmlaModel({ domain: makeDomain([cube]) }) });

      const node = makeTreeNode({
         data: makeCubeItemData({ type: CubeItemType.MEASURE, cubeName: "SalesCube", uniqueName: "[Measures].[Count]" }),
      });

      comp.selectedNode([node]);

      expect(comp.selectedCubeNode.member.metaInfo).not.toBeNull();
      expect(comp.selectedCubeNode.member.metaInfo.drillInfo.paths).toEqual([]);
   });
});

// ── Group 11: changeAsDate / changeLocal / changeDatePattern ──────────────

describe("DatasourcesXmla — member metaInfo mutators", () => {

   function setupMemberSelection(comp: any) {
      const member = makeDimMember({
         classType: "CubeDimMemberModel",
         metaInfo: makeMetaInfo({ asDate: false, locale: null, datePattern: null }),
      });
      comp.selectedCubeNode = { member };
      return member;
   }

   it("changeAsDate toggles asDate from false to true", async () => {
      const { comp } = await renderXmla();
      const member = setupMemberSelection(comp);
      comp.changeAsDate();
      expect(member.metaInfo.asDate).toBe(true);
   });

   it("changeAsDate does nothing when not selectedDimensionMember", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = { member: makeMeasure({ classType: "CubeMeasureModel" }) };
      expect(() => comp.changeAsDate()).not.toThrow();
   });

   it("changeLocal sets locale on member metaInfo", async () => {
      const { comp } = await renderXmla();
      const member = setupMemberSelection(comp);
      comp.changeLocal("zh");
      expect(member.metaInfo.locale).toBe("zh");
   });

   it("changeDatePattern sets datePattern on member metaInfo", async () => {
      const { comp } = await renderXmla();
      const member = setupMemberSelection(comp);
      comp.changeDatePattern("yyyy-MM-dd");
      expect(member.metaInfo.datePattern).toBe("yyyy-MM-dd");
   });
});

// ── Group 12: changeOriginalOrder ─────────────────────────────────────────

describe("DatasourcesXmla — changeOriginalOrder", () => {

   it("toggles dimension originalOrder from false to true", async () => {
      const { comp } = await renderXmla();
      const dim = makeDimension({ originalOrder: false });
      comp.selectedCubeNode = { dimension: dim };
      comp.changeOriginalOrder();
      expect(dim.originalOrder).toBe(true);
   });

   it("does nothing when no dimension is selected", async () => {
      const { comp } = await renderXmla();
      comp.selectedCubeNode = {};
      expect(() => comp.changeOriginalOrder()).not.toThrow();
   });
});

// ── Group 13: canDeactivate ────────────────────────────────────────────────

describe("DatasourcesXmla — canDeactivate", () => {

   it("returns true when model matches originalModel", async () => {
      const { comp } = await renderXmla({ model: makeXmlaModel({ name: "Same" }) });
      (comp as any).originalModel = makeXmlaModel({ name: "Same" });

      const result = await comp.canDeactivate();

      expect(result).toBe(true);
   });

   it("opens dialog and resolves true when user clicks Yes", async () => {
      const { comp } = await renderXmla({ model: makeXmlaModel({ name: "Modified" }) });
      (comp as any).originalModel = makeXmlaModel({ name: "Original" });

      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("Yes"),
         componentInstance: { onCommit: new Subject(), onCancel: new Subject() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      const result = await comp.canDeactivate();

      expect(result).toBe(true);
   });

   it("opens dialog and resolves false when user clicks No", async () => {
      const { comp } = await renderXmla({ model: makeXmlaModel({ name: "Modified" }) });
      (comp as any).originalModel = makeXmlaModel({ name: "Original" });

      MessageDialog.lastMessage = null;
      (MessageDialog as any).lastMessageTS = 0;

      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("No"),
         componentInstance: { onCommit: new Subject(), onCancel: new Subject() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      const result = await comp.canDeactivate();

      expect(result).toBe(false);
   });
});

// ── Group 14: close ────────────────────────────────────────────────────────

describe("DatasourcesXmla — close", () => {

   it("navigates to /portal/tab/data/datasources", async () => {
      const { comp } = await renderXmla();
      comp.datasourcePath = "";
      comp.parentPath = "";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.anything(),
      );
   });

   it("uses parentPath as path when set", async () => {
      const { comp } = await renderXmla();
      comp.parentPath = "/folder1";
      comp.datasourcePath = "";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/folder1" }) }),
      );
   });

   it("derives parentPath from datasourcePath when parentPath is empty", async () => {
      const { comp } = await renderXmla();
      comp.parentPath = "";
      comp.datasourcePath = "folder/MyXmla";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "folder" }) }),
      );
   });
});

// ── Group 15: memory leak ──────────────────────────────────────────────────

describe("DatasourcesXmla — memory leak", () => {

   it("sets routeParamSubscription to null on fixture.destroy()", async () => {
      const { comp, fixture } = await renderXmla();

      const sub = (comp as any).routeParamSubscription;
      expect(sub).not.toBeNull();

      fixture.destroy();

      expect((comp as any).routeParamSubscription).toBeNull();
   });
});
