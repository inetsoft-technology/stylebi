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
 * BindingTreeComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — selectNode/expandNode/collapseNode: event routing to outputs
 *   Group 2 [Risk 2] — searchStart: full-tree load once + search state toggle
 *   Group 3 [Risk 3] — nodeDrop: preventDefault/stopPropagation before emit (Bug #21779)
 *   Group 4 [Risk 3] — expandNode composer path: HTTP parameter validation + dialog open
 *   Group 5 [Risk 2] — ngOnChanges: restore virtual scroll when selectedNodes change
 *
 * HTTP: MSW via viewer.handlers — getConnectionParameters, set-connection-variables
 *
 * Suspected bugs (header only):
 *   validateBindingTree async subscribe — no ngOnDestroy teardown; prescan async_zones=2
 *
 * Out of scope:
 *   loadFullTree internals — covered indirectly via searchStart
 *   collapseAll / expandAll — toolbar actions, no prescan risk flag
 */

import { DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, screen } from "@testing-library/angular";
import { of } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { DragEvent } from "../../../common/data/drag-event";
import { DomService } from "../../../widget/dom-service/dom.service";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { BindingTreeComponent } from "./binding-tree.component";
import { BindingTreeService } from "./binding-tree.service";

function createTreeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "Query1",
      data: { path: "Query1", type: AssetType.DATA_SOURCE } as AssetEntry,
      children: [],
      expanded: false,
      ...overrides
   };
}

function createBindingTreeServiceMock(overrides: Partial<BindingTreeService> = {}): BindingTreeService {
   const root = createTreeNode({ label: "root", children: [createTreeNode()] });
   return {
      bindingTreeModel: root,
      virtualScrollDataSource: {
         restoreScrollTop: vi.fn(),
         refreshByRoot: vi.fn()
      },
      needUseVirtualScroll: false,
      expandNode: vi.fn(),
      collapseNode: vi.fn(),
      expandNodesCollapseOthersByRecord: vi.fn(),
      loadFullTree: vi.fn().mockResolvedValue(true),
      changeSearchState: vi.fn(),
      treeLoading: vi.fn().mockReturnValue(false),
      getSourceInfo: vi.fn().mockReturnValue({ source: "src", prefix: "p", type: "t" }),
      getNode: vi.fn().mockReturnValue({ children: [] }),
      ...overrides
   } as unknown as BindingTreeService;
}

async function renderBindingTree(
   serviceOverrides: Partial<BindingTreeService> = {},
   props: Record<string, unknown> = {},
   uiContext: { isVS: boolean } = { isVS: true },
   modelService?: Partial<ModelService>
) {
   const bindingTreeService = createBindingTreeServiceMock(serviceOverrides);
   const sendModel = vi.fn().mockReturnValue(of({ body: { parameters: [] } }));
   const result = await render(BindingTreeComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: BindingTreeService, useValue: bindingTreeService },
         { provide: ModelService, useValue: modelService ?? { sendModel } },
         { provide: NgbModal, useValue: {} },
         { provide: UIContextService, useValue: { isVS: () => uiContext.isVS } },
         { provide: DomService, useValue: {} }
      ],
      componentProperties: {
         rid: "vs1",
         composer: false,
         ...props
      }
   });
   result.fixture.detectChanges();
   return { ...result, bindingTreeService, sendModel };
}

function getTreeDebugElement(fixture: { debugElement: DebugElement }): DebugElement {
   return fixture.debugElement.query(By.directive(TreeComponent));
}

describe("BindingTreeComponent — node events [Group 1, Risk 2]", () => {
   it("should emit nodeSelected when selectNode is called", async () => {
      const node = createTreeNode();
      const selected = vi.fn();
      const { fixture } = await renderBindingTree();
      fixture.componentInstance.nodeSelected.subscribe(selected);

      getTreeDebugElement(fixture).triggerEventHandler("nodesSelected", [node]);

      expect(selected).toHaveBeenCalledWith(node);
   });

   it("should delegate expandNode to bindingTreeService in viewer mode", async () => {
      const expandNode = vi.fn();
      const node = createTreeNode();
      const expanded = vi.fn();
      const { fixture, bindingTreeService } = await renderBindingTree({ expandNode });
      fixture.componentInstance.nodeExpanded.subscribe(expanded);

      getTreeDebugElement(fixture).triggerEventHandler("nodeExpanded", node);

      expect(bindingTreeService.expandNode).toHaveBeenCalledWith(node);
      expect(expanded).toHaveBeenCalledWith(node);
   });

   it("should collapse node via service when not in composer", async () => {
      const collapseNode = vi.fn();
      const node = createTreeNode();
      const collapsed = vi.fn();
      const { fixture, bindingTreeService } = await renderBindingTree({ collapseNode }, { composer: false });
      fixture.componentInstance.nodeCollapsed.subscribe(collapsed);

      getTreeDebugElement(fixture).triggerEventHandler("nodeCollapsed", node);

      expect(bindingTreeService.collapseNode).toHaveBeenCalledWith(node);
      expect(collapsed).toHaveBeenCalledWith(node);
   });

   it("should skip collapseNode service call in composer mode", async () => {
      const collapseNode = vi.fn();
      const { fixture, bindingTreeService } = await renderBindingTree({ collapseNode }, { composer: true });

      getTreeDebugElement(fixture).triggerEventHandler("nodeCollapsed", createTreeNode());

      expect(bindingTreeService.collapseNode).not.toHaveBeenCalled();
   });
});

describe("BindingTreeComponent — searchStart [Group 2, Risk 2]", () => {
   it("should load full tree once when search starts", async () => {
      const loadFullTree = vi.fn().mockResolvedValue(true);
      const changeSearchState = vi.fn();
      const { fixture, bindingTreeService } = await renderBindingTree({ loadFullTree, changeSearchState });

      getTreeDebugElement(fixture).triggerEventHandler("searchStart", true);
      expect(bindingTreeService.changeSearchState).toHaveBeenCalledWith(true);

      await Promise.resolve();
      expect(loadFullTree).toHaveBeenCalledTimes(1);

      getTreeDebugElement(fixture).triggerEventHandler("searchStart", true);
      await Promise.resolve();
      expect(loadFullTree).toHaveBeenCalledTimes(1);
   });
});

describe("BindingTreeComponent — nodeDrop [Group 3, Risk 3]", () => {
   // 🔁 Regression-sensitive: Bug #21779 — browser default drop must be cancelled before emit
   it("should preventDefault and stopPropagation on drop event", async () => {
      const evt = {
         preventDefault: vi.fn(),
         stopPropagation: vi.fn()
      } as unknown as DragEvent;
      const dropped = vi.fn();
      const { fixture } = await renderBindingTree();
      fixture.componentInstance.onNodeDrop.subscribe(dropped);

      getTreeDebugElement(fixture).triggerEventHandler("nodeDrop", { evt });

      expect(evt.preventDefault).toHaveBeenCalled();
      expect(evt.stopPropagation).toHaveBeenCalled();
      expect(dropped).toHaveBeenCalledWith(evt);
   });
});

describe("BindingTreeComponent — composer expand HTTP [Group 4, Risk 3]", () => {
   it("should open variable dialog when expand returns connection parameters", async () => {
      const sendModel = vi.fn().mockReturnValue(of({
         body: { parameters: [{ name: "host", type: "string", value: "" }] }
      }));
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue({
         model: null
      } as any);

      const node = createTreeNode();
      const { fixture } = await renderBindingTree(
         {},
         { composer: true },
         { isVS: false },
         { sendModel } as unknown as ModelService
      );

      getTreeDebugElement(fixture).triggerEventHandler("nodeExpanded", node);

      expect(sendModel).toHaveBeenCalled();
      expect(showDialogSpy).toHaveBeenCalled();

      showDialogSpy.mockRestore();
   });

   it("should expand directly when node has no data", async () => {
      const expandNode = vi.fn();
      const { fixture, bindingTreeService } = await renderBindingTree(
         { expandNode },
         { composer: true },
         { isVS: false }
      );

      getTreeDebugElement(fixture).triggerEventHandler("nodeExpanded", createTreeNode({ data: null }));

      expect(bindingTreeService.expandNode).toHaveBeenCalled();
   });
});

describe("BindingTreeComponent — selectedNodes scroll restore [Group 5, Risk 2]", () => {
   it("should render tree and loading indicator based on service state", async () => {
      const { container } = await renderBindingTree({ treeLoading: vi.fn().mockReturnValue(false) });

      expect(container.querySelector("tree")).toBeTruthy();
      expect(screen.queryByText("_#(Loading)")).not.toBeInTheDocument();
   });

   it("should show loading indicator when tree is loading", async () => {
      const { container } = await renderBindingTree({ treeLoading: vi.fn().mockReturnValue(true) });

      expect(container.querySelector(".loading-container")).toBeTruthy();
      expect(screen.getByText("Loading")).toBeInTheDocument();
   });

   it("should restore virtual scroll position when selectedNodes change", async () => {
      const restoreScrollTop = vi.fn();
      const { rerender, bindingTreeService } = await renderBindingTree({
         virtualScrollDataSource: { restoreScrollTop, refreshByRoot: vi.fn() } as any
      });

      await rerender({
         componentProperties: {
            rid: "vs1",
            composer: false,
            selectedNodes: [createTreeNode()]
         }
      });

      expect(bindingTreeService.virtualScrollDataSource.restoreScrollTop).toHaveBeenCalled();
   });
});
