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
 * WizardBindingTree — P1: Interaction (single pass + race-condition + memory-leak)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ngOnInit refreshSubject: emit → sendEvent GET_BINDING_TREE_URI
 *                        with correct reload flag
 *   Group 2  [Risk 3] — ngOnInit recommenderSubject: toRecommend=true → REFRESH_NODES_URI;
 *                        toRecommend=false → OBJECT_WIZARD_REFRESH; toRecommend reset to true
 *   Group 3  [Risk 3] — selectNodes(): sets selectedNodes, derives tableName from last node,
 *                        calls recommender (which sends REFRESH_NODES_URI)
 *   Group 4  [Risk 2] — recommender(): sends REFRESH_NODES_URI with selectedEntries mapped
 *                        from selectedNodes.data; tableName forwarded
 *   Group 5  [Risk 3] — processVSTrapCommand: "yes" → resends events with confirmed=true;
 *                        other result → pops selectedNodes
 *   Group 6  [Risk 3] — processVSWizardSourceChangeCommand: "ok" → sends REFRESH_FIELDS_URI;
 *                        other result → does NOT send REFRESH_FIELDS_URI
 *   Group 7  [Risk 2] — processRefreshWizardTreeCommand: calls resetTreeModel + reloadSelectedBinding;
 *                        forceRefresh+unchanged-paths → early return (no reset);
 *                        flatternTree merges Dimensions/Measures and sorts alphabetically
 *   Group 8  [baseline] — processSetWizardBindingTreeNodesCommand: selectedPaths + toRecommend
 *   Group 9  [baseline] — processSetGrayedOutFieldsCommand: sets treeInfo.grayedOutFields;
 *                          no-op when treeInfo is null
 *   Group 10 [baseline] — processReplaceColumnCommand: replaces matching paths in selectedPaths
 *   Group 11 [baseline] — processRefreshWizardTreeTriggerCommand: calls bindingTreeService.refresh
 *   Group 12 [baseline] — expandNode / collapsedNode / searchStart
 *   Group 13 [baseline] — openWizardTreeContextmenu: early-return guards; opens dropdown on
 *                          valid event
 *   Group 14 [baseline] — hasMenu() / getURLParams() / getAssemblyName()
 *   Group 15 [Risk 3]  — ngOnDestroy: unsubscribes from refreshSubject, recommenderSubject, and
 *                          CommandProcessor commands (memory-leak prevention)
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass:
 *   processOpenEditGeographicCommand — two-branch dialog requiring GeoProvider/VSGeoProvider
 *     wiring and EditGeographicDialog setup; display-pass candidate
 *   processInitWizardBindingTreeCommand — pure delegation to processSetWizardBindingTreeNodesCommand
 *     + processRefreshWizardTreeCommand, both individually tested in Groups 7–8
 *   getCSSIcon() — trivial delegation to GuiTool.getTreeNodeIconClass; zero branch logic
 *   virtualScrollDatasource getter — trivial delegation; zero branch logic
 *   needUseVirtualScroll getter — trivial delegation; zero branch logic
 *   hasMenuFunction() — returns arrow function delegating to hasMenu(); covered via hasMenu()
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { WizardBindingTree } from "./wizard-binding-tree.component";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { ModelService } from "../../../widget/services/model.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

// ---------------------------------------------------------------------------
// Module-level Subject instances — persistent across tests so .observed can
// be checked in the memory-leak group (same pattern as vs-wizard-pane tests).
// ---------------------------------------------------------------------------
const commandsSubject = new Subject<any>();
const refreshSubject = new Subject<boolean>();
const recommenderSubject = new Subject<boolean>();

// ---------------------------------------------------------------------------
// Shared mocks — vi.fn() instances reset in beforeEach, Subjects are not
// replaced (they must be the same reference the component subscribes to).
// ---------------------------------------------------------------------------
const SEND_EVENT_MOCK = vi.fn();

const VS_CLIENT_MOCK = {
   commands: commandsSubject,
   sendEvent: SEND_EVENT_MOCK,
};

const BINDING_TREE_SERVICE_MOCK: any = {
   refreshSubject,
   recommenderSubject,
   treeInfo: null as any,
   bindingTreeModel: null as any,
   virtualScrollDataSource: null as any,
   needUseVirtualScroll: false,
   selectedNodes: [] as TreeNodeModel[],
   selectedPaths: [] as string[],
   resetTreeModel: vi.fn(),
   reloadSelectedBinding: vi.fn(),
   expandAllChildren: vi.fn(),
   expandNode: vi.fn(),
   refresh: vi.fn(),
   getSelectedBindingNodePaths: vi.fn().mockReturnValue([]),
   showSourceChangedDialog: vi.fn(),
   getBindingTreeActions: vi.fn().mockReturnValue({ actions: [{ visible: false }] }),
};

const TREE_SERVICE_MOCK = {
   getTableName: vi.fn().mockReturnValue("Table1"),
};

const DROPDOWN_SERVICE_MOCK = {
   open: vi.fn().mockReturnValue({ componentInstance: { actions: null, sourceEvent: null } }),
};

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: {},
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeNode(label: string, data: any = {}): TreeNodeModel {
   return { label, data, children: [], leaf: true, expanded: false } as unknown as TreeNodeModel;
}

interface RenderOpts { runtimeId?: string; temporarySheet?: boolean; }

async function renderComponent(opts: RenderOpts = {}) {
   const result = await render(WizardBindingTree, {
      inputs: {
         runtimeId: opts.runtimeId ?? "vs1",
         temporarySheet: opts.temporarySheet ?? false,
      },
      providers: [
         { provide: VSWizardBindingTreeService, useValue: BINDING_TREE_SERVICE_MOCK },
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
         { provide: FixedDropdownService, useValue: DROPDOWN_SERVICE_MOCK },
         { provide: BindingTreeService, useValue: TREE_SERVICE_MOCK },
         { provide: ModelService, useValue: {} },
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance as WizardBindingTree, fixture: result.fixture };
}

beforeEach(() => {
   SEND_EVENT_MOCK.mockClear();
   BINDING_TREE_SERVICE_MOCK.resetTreeModel.mockClear();
   BINDING_TREE_SERVICE_MOCK.reloadSelectedBinding.mockClear();
   BINDING_TREE_SERVICE_MOCK.expandAllChildren.mockClear();
   BINDING_TREE_SERVICE_MOCK.expandNode.mockClear();
   BINDING_TREE_SERVICE_MOCK.refresh.mockClear();
   BINDING_TREE_SERVICE_MOCK.getSelectedBindingNodePaths.mockClear().mockReturnValue([]);
   BINDING_TREE_SERVICE_MOCK.showSourceChangedDialog.mockClear();
   BINDING_TREE_SERVICE_MOCK.getBindingTreeActions
      .mockClear()
      .mockReturnValue({ actions: [{ visible: false }] });
   BINDING_TREE_SERVICE_MOCK.treeInfo = null;
   BINDING_TREE_SERVICE_MOCK.bindingTreeModel = null;
   BINDING_TREE_SERVICE_MOCK.virtualScrollDataSource = null;
   BINDING_TREE_SERVICE_MOCK.selectedNodes = [];
   BINDING_TREE_SERVICE_MOCK.selectedPaths = [];

   TREE_SERVICE_MOCK.getTableName.mockClear().mockReturnValue("Table1");
   DROPDOWN_SERVICE_MOCK.open
      .mockClear()
      .mockReturnValue({ componentInstance: { actions: null, sourceEvent: null } });
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: {},
      close: vi.fn(),
      dismiss: vi.fn(),
   }));

   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: refreshSubject → GET_BINDING_TREE_URI [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — ngOnInit: refreshSubject triggers tree load", () => {
   // 🔁 Regression-sensitive: the binding tree loads by subscribing to refreshSubject.
   // A missing subscription or wrong URI means the tree never populates — the wizard
   // opens with an empty field list and the user cannot bind any data.
   it("should send GET_BINDING_TREE_URI with reload=true when refreshSubject emits true", async () => {
      await renderComponent();
      SEND_EVENT_MOCK.mockClear();

      refreshSubject.next(true);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/tree",
         expect.objectContaining({ reload: true }),
      );
   });

   it("should send GET_BINDING_TREE_URI with reload=false when refreshSubject emits false", async () => {
      await renderComponent();
      SEND_EVENT_MOCK.mockClear();

      refreshSubject.next(false);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/tree",
         expect.objectContaining({ reload: false }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit: recommenderSubject routing [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — ngOnInit: recommenderSubject routes by toRecommend flag", () => {
   // 🔁 Regression-sensitive: when toRecommend=true the component refreshes field bindings via
   // recommender(); when false it sends a plain object-wizard refresh. Swapping these branches
   // silently sends the wrong command, causing the binding pane to show stale fields.
   it("should send REFRESH_NODES_URI when toRecommend=true", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      comp.toRecommend = true;

      recommenderSubject.next(false);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/tree/node-changed",
         expect.any(Object),
      );
   });

   it("should send OBJECT_WIZARD_REFRESH URI when toRecommend=false", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      comp.toRecommend = false;

      recommenderSubject.next(false);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith("/events/vswizard/object-wizard/refresh");
   });

   it("should reset toRecommend to true after each recommenderSubject emit", async () => {
      const { comp } = await renderComponent();
      comp.toRecommend = false;

      recommenderSubject.next(false);

      expect(comp.toRecommend).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — selectNodes() [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — selectNodes()", () => {
   // 🔁 Regression-sensitive: selectNodes must set the selected binding nodes AND trigger
   // recommender so the server updates the field list. Missing either step causes an
   // inconsistent UI state where clicked fields appear selected but no refresh occurs.
   it("should update bindingTreeService.selectedNodes with the provided array", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.treeInfo = {
         tempAssemblyName: "Chart1",
         tempBinding: null,
         grayedOutFields: [],
      };
      const node = makeNode("Region", { path: "/db/q/Region", properties: {} });

      comp.selectNodes([node]);

      expect(BINDING_TREE_SERVICE_MOCK.selectedNodes).toEqual([node]);
   });

   it("should call recommender (send REFRESH_NODES_URI) after updating selected nodes", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      BINDING_TREE_SERVICE_MOCK.treeInfo = {
         tempAssemblyName: "Chart1",
         tempBinding: null,
         grayedOutFields: [],
      };
      const node = makeNode("Col", { path: "/db/t/Col", properties: {} });

      comp.selectNodes([node]);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/tree/node-changed",
         expect.any(Object),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4 — recommender() [Risk 2]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — recommender()", () => {
   // 🔁 Regression-sensitive: the event payload must include the AssetEntry data of each
   // selected node. If the map or filter is wrong, the server receives empty or incorrect
   // field references and the binding pane shows wrong available fields.
   it("should send REFRESH_NODES_URI with selectedEntries mapped from selectedNodes.data", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      const nodeData = { path: "/db/table/col", properties: {} };
      BINDING_TREE_SERVICE_MOCK.selectedNodes = [makeNode("col", nodeData)];

      comp.recommender("Table1");

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/tree/node-changed",
         expect.objectContaining({ selectedEntries: [nodeData], tableName: "Table1" }),
      );
   });

   it("should exclude nodes with null data from selectedEntries", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      const nodeData = { path: "/db/t/c", properties: {} };
      BINDING_TREE_SERVICE_MOCK.selectedNodes = [
         makeNode("nullNode", null),
         makeNode("goodNode", nodeData),
      ];

      comp.recommender();

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/tree/node-changed",
         expect.objectContaining({ selectedEntries: [nodeData] }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 5 — processVSTrapCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processVSTrapCommand()", () => {
   // 🔁 Regression-sensitive: on "yes" the component must resend each event with
   // confirmed=true so the server accepts the binding change despite the trap warning.
   // If confirmed is not set, the server re-raises the trap endlessly.
   it("should resend command events with confirmed=true when user clicks yes", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      const showTrapSpy = vi.spyOn(ComponentTool, "showTrapAlert").mockResolvedValue("yes" as any);

      try {
         comp.processVSTrapCommand({
            events: { "/events/vswizard/some/action": { field: "X" } },
         } as any);

         await waitFor(() =>
            expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
               "/events/vswizard/some/action",
               expect.objectContaining({ confirmed: true }),
            ),
         );
      }
      finally {
         showTrapSpy.mockRestore();
      }
   });

   it("should pop selectedNodes when user does not confirm trap", async () => {
      const { comp } = await renderComponent();
      const popSpy = vi.fn();
      BINDING_TREE_SERVICE_MOCK.selectedNodes = { pop: popSpy } as any;
      const showTrapSpy = vi.spyOn(ComponentTool, "showTrapAlert").mockResolvedValue("no" as any);

      try {
         comp.processVSTrapCommand({ events: {} } as any);

         await waitFor(() => expect(popSpy).toHaveBeenCalled());
         expect(SEND_EVENT_MOCK).not.toHaveBeenCalled();
      }
      finally {
         showTrapSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 6 — processVSWizardSourceChangeCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processVSWizardSourceChangeCommand()", () => {
   // 🔁 Regression-sensitive: when the user confirms, refreshBindingFields must send
   // REFRESH_FIELDS_URI so the server updates the field list for the new source.
   // If omitted, the binding pane keeps showing fields from the old source.
   it("should send REFRESH_FIELDS_URI after user confirms source change", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      const nodeData = { path: "/db/t/col", properties: {} };
      BINDING_TREE_SERVICE_MOCK.selectedNodes = [makeNode("col", nodeData)];
      BINDING_TREE_SERVICE_MOCK.treeInfo = { tempAssemblyName: "Chart1" };
      BINDING_TREE_SERVICE_MOCK.showSourceChangedDialog.mockResolvedValue("ok");

      comp.processVSWizardSourceChangeCommand({} as any);

      await waitFor(() =>
         expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
            "/events/vswizard/binding/tree/refresh-fields",
            expect.any(Object),
         ),
      );
   });

   it("should NOT send REFRESH_FIELDS_URI when user cancels source change", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      BINDING_TREE_SERVICE_MOCK.selectedNodes = [makeNode("col", { path: "/p", properties: {} })];
      BINDING_TREE_SERVICE_MOCK.showSourceChangedDialog.mockResolvedValue("cancel");

      comp.processVSWizardSourceChangeCommand({} as any);

      // Gate: wait for the dialog to have been shown, then assert no refresh event
      await waitFor(() =>
         expect(BINDING_TREE_SERVICE_MOCK.showSourceChangedDialog).toHaveBeenCalled(),
      );
      expect(SEND_EVENT_MOCK).not.toHaveBeenCalledWith(
         "/events/vswizard/binding/tree/refresh-fields",
         expect.anything(),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 7 — processRefreshWizardTreeCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processRefreshWizardTreeCommand()", () => {
   // 🔁 Regression-sensitive: both resetTreeModel and reloadSelectedBinding must be called.
   // Skipping either leaves the binding tree in an inconsistent state (stale labels or
   // wrong selected-node restoration).
   it("should call resetTreeModel with the tree model from the command", async () => {
      const { comp } = await renderComponent();
      const treeModel = { label: "root", children: [] } as any;

      (comp as any)["processRefreshWizardTreeCommand"]({
         forceRefresh: false,
         reload: true,
         treeModel,
         treeInfo: { tempAssemblyName: "C1", tempBinding: null, grayedOutFields: [] },
      });

      expect(BINDING_TREE_SERVICE_MOCK.resetTreeModel).toHaveBeenCalledWith(treeModel);
   });

   it("should call reloadSelectedBinding with the reload flag from the command", async () => {
      const { comp } = await renderComponent();

      (comp as any)["processRefreshWizardTreeCommand"]({
         forceRefresh: false,
         reload: true,
         treeModel: { label: "root", children: [] } as any,
         treeInfo: null,
      });

      expect(BINDING_TREE_SERVICE_MOCK.reloadSelectedBinding).toHaveBeenCalledWith(true);
   });

   it("should set treeInfo from the command", async () => {
      const { comp } = await renderComponent();
      const treeInfo = { tempAssemblyName: "C1", tempBinding: null, grayedOutFields: [] };

      (comp as any)["processRefreshWizardTreeCommand"]({
         forceRefresh: false,
         reload: false,
         treeModel: { label: "root", children: [] } as any,
         treeInfo,
      });

      expect(comp.treeInfo).toBe(treeInfo);
   });

   it("should return early when forceRefresh=true and paths are already in sync", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.getSelectedBindingNodePaths.mockReturnValue(["p1", "p2"]);
      BINDING_TREE_SERVICE_MOCK.selectedPaths = ["p1", "p2"];

      (comp as any)["processRefreshWizardTreeCommand"]({
         forceRefresh: true,
         reload: false,
         treeModel: { label: "root", children: [] },
         treeInfo: null,
      });

      expect(BINDING_TREE_SERVICE_MOCK.resetTreeModel).not.toHaveBeenCalled();
   });

   it("flatternTree should merge Dimensions+Measures and sort children alphabetically", async () => {
      const { comp } = await renderComponent();
      const dimNode = {
         label: "Dimensions",
         children: [makeNode("Zebra"), makeNode("Category")],
      } as any;
      const measNode = {
         label: "Measures",
         children: [makeNode("Amount")],
      } as any;
      const treeModel = { label: "root", children: [dimNode, measNode] } as any;
      BINDING_TREE_SERVICE_MOCK.bindingTreeModel = treeModel;

      (comp as any)["processRefreshWizardTreeCommand"]({
         forceRefresh: false,
         reload: false,
         treeModel,
         treeInfo: null,
      });

      // Dimensions + Measures merged and sorted: Amount < Category < Zebra
      const labels = treeModel.children.map((n: any) => n.label);
      expect(labels).toEqual(["Amount", "Category", "Zebra"]);
   });

   // End-to-end: CommandProcessor dispatch routes to processRefreshWizardTreeCommand
   it("should route RefreshWizardTreeCommand via commandsSubject to the handler", async () => {
      await renderComponent();
      const treeModel = { label: "root", children: [] } as any;

      commandsSubject.next({
         assembly: null,
         type: "RefreshWizardTreeCommand",
         command: { forceRefresh: false, reload: true, treeModel, treeInfo: null },
      });

      expect(BINDING_TREE_SERVICE_MOCK.resetTreeModel).toHaveBeenCalledWith(treeModel);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — processSetWizardBindingTreeNodesCommand [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processSetWizardBindingTreeNodesCommand()", () => {
   it("should set selectedPaths from the command payload", async () => {
      const { comp } = await renderComponent();

      (comp as any)["processSetWizardBindingTreeNodesCommand"]({
         selectedPaths: ["path/a", "path/b"],
         toRecommand: false,   // note: upstream uses "toRecommand" (typo preserved)
      });

      expect(BINDING_TREE_SERVICE_MOCK.selectedPaths).toEqual(["path/a", "path/b"]);
   });

   it("should set toRecommend from the command payload", async () => {
      const { comp } = await renderComponent();
      comp.toRecommend = true;

      (comp as any)["processSetWizardBindingTreeNodesCommand"]({
         selectedPaths: [],
         toRecommand: false,
      });

      expect(comp.toRecommend).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — processSetGrayedOutFieldsCommand [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processSetGrayedOutFieldsCommand()", () => {
   it("should update treeInfo.grayedOutFields with the command fields", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.treeInfo = { tempAssemblyName: "C1", grayedOutFields: [] };

      (comp as any)["processSetGrayedOutFieldsCommand"]({ fields: ["f1", "f2"] });

      expect(BINDING_TREE_SERVICE_MOCK.treeInfo.grayedOutFields).toEqual(["f1", "f2"]);
   });

   it("should be a no-op and not throw when treeInfo is null", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.treeInfo = null;

      expect(() =>
         (comp as any)["processSetGrayedOutFieldsCommand"]({ fields: ["f1"] }),
      ).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — processReplaceColumnCommand [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processReplaceColumnCommand()", () => {
   it("should replace matching paths in selectedPaths with the new paths", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.getSelectedBindingNodePaths.mockReturnValue(["old/path/col"]);

      (comp as any)["processReplaceColumnCommand"]({
         oldPaths: ["old/path/col"],
         paths: ["new/path/col"],
      });

      expect(BINDING_TREE_SERVICE_MOCK.selectedPaths).toEqual(["new/path/col"]);
   });

   it("should leave paths unchanged when there is no match in oldPaths", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.getSelectedBindingNodePaths.mockReturnValue(["unrelated/path"]);

      (comp as any)["processReplaceColumnCommand"]({
         oldPaths: ["other/path"],
         paths: ["new/path"],
      });

      expect(BINDING_TREE_SERVICE_MOCK.selectedPaths).toEqual(["unrelated/path"]);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — processRefreshWizardTreeTriggerCommand [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — processRefreshWizardTreeTriggerCommand()", () => {
   it("should call bindingTreeService.refresh(false)", async () => {
      const { comp } = await renderComponent();

      (comp as any)["processRefreshWizardTreeTriggerCommand"]({});

      expect(BINDING_TREE_SERVICE_MOCK.refresh).toHaveBeenCalledWith(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — expandNode / collapsedNode / searchStart [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — expandNode() / collapsedNode() / searchStart()", () => {
   it("expandNode() should delegate to bindingTreeService.expandNode", async () => {
      const { comp } = await renderComponent();
      const node = makeNode("col");

      comp.expandNode(node);

      expect(BINDING_TREE_SERVICE_MOCK.expandNode).toHaveBeenCalledWith(node);
   });

   it("collapsedNode() should not throw when virtualScrollDataSource is null", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.virtualScrollDataSource = null;

      expect(() => comp.collapsedNode(makeNode("col"))).not.toThrow();
   });

   it("searchStart() should set activeRoot to bindingTreeService.bindingTreeModel", async () => {
      const { comp } = await renderComponent();
      const root = { label: "root", children: [] } as any;
      BINDING_TREE_SERVICE_MOCK.bindingTreeModel = root;

      comp.searchStart();

      expect(comp.activeRoot).toBe(root);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — openWizardTreeContextmenu [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — openWizardTreeContextmenu()", () => {
   it("should return early without opening dropdown when event is null", async () => {
      const { comp } = await renderComponent();

      comp.openWizardTreeContextmenu(null as any);

      expect(DROPDOWN_SERVICE_MOCK.open).not.toHaveBeenCalled();
   });

   it("should return early without opening dropdown when event has fewer than 2 elements", async () => {
      const { comp } = await renderComponent();

      comp.openWizardTreeContextmenu([{ clientX: 0, clientY: 0 }] as any);

      expect(DROPDOWN_SERVICE_MOCK.open).not.toHaveBeenCalled();
   });

   it("should open dropdown and set actions when event is valid", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.treeInfo = {
         tempAssemblyName: "Chart1",
         tempBinding: null,
         grayedOutFields: [],
      };
      const mockActions = { actions: [{ visible: true }] };
      BINDING_TREE_SERVICE_MOCK.getBindingTreeActions.mockReturnValue(mockActions);
      const contextmenuInst: any = { actions: null, sourceEvent: null };
      DROPDOWN_SERVICE_MOCK.open.mockReturnValue({ componentInstance: contextmenuInst });
      const node = makeNode("col");

      comp.openWizardTreeContextmenu([{ clientX: 10, clientY: 20 }, node, [node]] as any);

      expect(DROPDOWN_SERVICE_MOCK.open).toHaveBeenCalledOnce();
      expect(contextmenuInst.actions).toBe(mockActions.actions);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — hasMenu() / getURLParams() / getAssemblyName() [baseline]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — hasMenu() / getURLParams() / getAssemblyName()", () => {
   it("hasMenu() should return true when at least one action group is visible", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.treeInfo = {
         tempAssemblyName: "C1",
         tempBinding: null,
         grayedOutFields: [],
      };
      BINDING_TREE_SERVICE_MOCK.getBindingTreeActions.mockReturnValue({
         actions: [{ visible: true }],
      });

      expect(comp.hasMenu(makeNode("n"))).toBe(true);
   });

   it("hasMenu() should return false when no action group is visible", async () => {
      const { comp } = await renderComponent();
      BINDING_TREE_SERVICE_MOCK.treeInfo = {
         tempAssemblyName: "C1",
         tempBinding: null,
         grayedOutFields: [],
      };
      BINDING_TREE_SERVICE_MOCK.getBindingTreeActions.mockReturnValue({
         actions: [{ visible: false }],
      });

      expect(comp.hasMenu(makeNode("n"))).toBe(false);
   });

   it("getURLParams() should include vsId=runtimeId and assemblyName=tempAssemblyName", async () => {
      const { comp } = await renderComponent({ runtimeId: "vs-test" });
      BINDING_TREE_SERVICE_MOCK.treeInfo = { tempAssemblyName: "Chart1" };

      const params = comp.getURLParams();

      expect(params.get("vsId")).toBe("vs-test");
      expect(params.get("assemblyName")).toBe("Chart1");
   });

   it("getAssemblyName() should return null", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 15 — ngOnDestroy: memory-leak prevention [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardBindingTree — ngOnDestroy: subscription cleanup", () => {
   // 🔁 Regression-sensitive: if ngOnDestroy does not unsubscribe from refreshSubject,
   // recommenderSubject, or CommandProcessor.commands, destroyed components continue
   // to call sendEvent and mutate stale state — a classic memory-leak race condition.
   it("should unsubscribe from refreshSubject after destroy", async () => {
      const { fixture } = await renderComponent();
      expect(refreshSubject.observed).toBe(true);

      fixture.destroy();

      expect(refreshSubject.observed).toBe(false);
   });

   it("should unsubscribe from recommenderSubject after destroy", async () => {
      const { fixture } = await renderComponent();
      expect(recommenderSubject.observed).toBe(true);

      fixture.destroy();

      expect(recommenderSubject.observed).toBe(false);
   });

   it("should unsubscribe from CommandProcessor commands after destroy", async () => {
      const { fixture } = await renderComponent();
      expect(commandsSubject.observed).toBe(true);

      fixture.destroy();

      expect(commandsSubject.observed).toBe(false);
   });
});
