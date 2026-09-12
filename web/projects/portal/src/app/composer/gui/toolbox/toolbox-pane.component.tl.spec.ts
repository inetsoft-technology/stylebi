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
 * ToolboxPane — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: deployed=true → toolbox=toolboxDeployed; deployed=false → toolbox=toolbox
 *   Group 2 [Risk 2] — ngOnChanges inactive=true: calls cd.detach(); does NOT call cd.reattach()
 *   Group 3 [Risk 2] — ngOnChanges inactive=false: calls cd.reattach(); calls subscribeVScroll when inactive key changes
 *   Group 4 [Risk 2] — ngOnChanges containerView set: calls subscribeVScroll()
 *   Group 5 [Risk 2] — treeNodesLoaded with bindingRoot: combinationTreeRoot has 2 children; doNotShowNodes contains bindingRoot
 *   Group 6 [Risk 2] — treeNodesLoaded without bindingRoot: combinationTreeRoot has 1 child; doNotShowNodes is empty
 *   Group 7 [Risk 1] — ngOnDestroy: unsubscribes vScrollSubscription (memory-leak guard)
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { render } from "@testing-library/angular";
import { ToolboxPane } from "./toolbox-pane.component";
import { DomService } from "../../../widget/dom-service/dom.service";
import { TreeTool } from "../../../common/util/tree-tool";
import { toolbox, toolboxDeployed } from "./toolbox.config";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

const DOM_SERVICE_MOCK = { requestAnimationFrame: vi.fn() };

async function renderComponent(inputs: Partial<ToolboxPane> = {}) {
   return render(ToolboxPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentProviders: [
         { provide: DomService, useValue: DOM_SERVICE_MOCK },
      ],
      componentProperties: inputs,
   });
}

beforeEach(() => {
   DOM_SERVICE_MOCK.requestAnimationFrame.mockReset();
});

afterEach(() => vi.restoreAllMocks());

describe("ToolboxPane — ngOnInit toolbox selection", () => {
   // 🔁 Regression-sensitive: deployed mode must use toolboxDeployed instead of toolbox so that
   // enterprise-only toolbox items appear in the deployed environment.
   it("should set toolbox to toolboxDeployed when deployed=true", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({ deployed: true });
      const comp = fixture.componentInstance;

      expect(comp.toolbox).toBe(toolboxDeployed);
   });

   it("should set toolbox to default toolbox when deployed=false", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({ deployed: false });
      const comp = fixture.componentInstance;

      expect(comp.toolbox).toBe(toolbox);
   });

   it("should set toolbox to default toolbox when deployed is undefined", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({});
      const comp = fixture.componentInstance;

      expect(comp.toolbox).toBe(toolbox);
   });
});

describe("ToolboxPane — ngOnChanges inactive=true", () => {
   it("should call cd.detach() when inactive becomes true", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({ inactive: false });
      const comp = fixture.componentInstance;
      const detachSpy = vi.spyOn((comp as any).cd, "detach");
      const reattachSpy = vi.spyOn((comp as any).cd, "reattach");

      comp.inactive = true;
      comp.ngOnChanges({
         inactive: new SimpleChange(false, true, false),
      });

      expect(detachSpy).toHaveBeenCalled();
      expect(reattachSpy).not.toHaveBeenCalled();
   });
});

describe("ToolboxPane — ngOnChanges inactive=false", () => {
   it("should call cd.reattach() when inactive becomes false", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({ inactive: true });
      const comp = fixture.componentInstance;
      const detachSpy = vi.spyOn((comp as any).cd, "detach");
      const reattachSpy = vi.spyOn((comp as any).cd, "reattach");

      comp.inactive = false;
      comp.ngOnChanges({
         inactive: new SimpleChange(true, false, false),
      });

      expect(reattachSpy).toHaveBeenCalled();
      expect(detachSpy).not.toHaveBeenCalled();
   });

   it("should call subscribeVScroll (via reattach path) when inactive key is present and inactive=false", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({ inactive: true });
      const comp = fixture.componentInstance;
      const subscribeVScrollSpy = vi.spyOn(comp as any, "subscribeVScroll");

      comp.inactive = false;
      comp.ngOnChanges({
         inactive: new SimpleChange(true, false, false),
      });

      expect(subscribeVScrollSpy).toHaveBeenCalled();
   });
});

describe("ToolboxPane — ngOnChanges containerView set", () => {
   it("should call subscribeVScroll when containerView changes to a truthy value", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({ inactive: false });
      const comp = fixture.componentInstance;
      // mockImplementation prevents the real subscribeVScroll from running
      // (the real impl calls element.addEventListener which requires a DOM node)
      const subscribeVScrollSpy = vi.spyOn(comp as any, "subscribeVScroll").mockImplementation(() => {});

      comp.containerView = { some: "view" };
      comp.ngOnChanges({
         containerView: new SimpleChange(null, { some: "view" }, false),
      });

      expect(subscribeVScrollSpy).toHaveBeenCalled();
   });

   it("should NOT call subscribeVScroll when containerView changes to null/undefined", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      // Do NOT pass containerView via componentInputs — that triggers ngOnChanges during
      // rendering, before any spy exists, and crashes on element.addEventListener.
      // Set it directly on the instance so ngOnChanges is only called manually below.
      const { fixture } = await renderComponent({ inactive: false });
      const comp = fixture.componentInstance;
      comp.containerView = { some: "view" } as any;
      const subscribeVScrollSpy = vi.spyOn(comp as any, "subscribeVScroll").mockImplementation(() => {});

      comp.containerView = null;
      comp.ngOnChanges({
         containerView: new SimpleChange({ some: "view" }, null, false),
      });

      expect(subscribeVScrollSpy).not.toHaveBeenCalled();
   });
});

describe("ToolboxPane — treeNodesLoaded with bindingRoot", () => {
   it("should set combinationTreeRoot with 2 children and doNotShowNodes containing bindingRoot", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(false);
      const { fixture } = await renderComponent({});
      const comp = fixture.componentInstance;

      const bindingRoot: TreeNodeModel = {
         label: "Binding Root",
         children: [],
         leaf: false,
      };

      comp.treeNodesLoaded(bindingRoot);

      const combinationTreeRoot: TreeNodeModel = (comp as any).combinationTreeRoot;
      const doNotShowNodes: TreeNodeModel[] = (comp as any).doNotShowNodes;

      expect(combinationTreeRoot.children).toHaveLength(2);
      expect(combinationTreeRoot.children[0]).toBe(bindingRoot);
      expect(combinationTreeRoot.children[1]).toBe(comp.toolbox);
      expect(doNotShowNodes).toContain(bindingRoot);
   });
});

describe("ToolboxPane — treeNodesLoaded without bindingRoot", () => {
   it("should set combinationTreeRoot with 1 child and empty doNotShowNodes when bindingRoot is null", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(false);
      const { fixture } = await renderComponent({});
      const comp = fixture.componentInstance;

      comp.treeNodesLoaded(null);

      const combinationTreeRoot: TreeNodeModel = (comp as any).combinationTreeRoot;
      const doNotShowNodes: TreeNodeModel[] = (comp as any).doNotShowNodes;

      expect(combinationTreeRoot.children).toHaveLength(1);
      expect(combinationTreeRoot.children[0]).toBe(comp.toolbox);
      expect(doNotShowNodes).toHaveLength(0);
   });
});

describe("ToolboxPane — ngOnDestroy memory-leak guard", () => {
   it("should unsubscribe vScrollSubscription on destroy", async () => {
      vi.spyOn(TreeTool, "expandAllNodes").mockImplementation(() => {});
      const { fixture } = await renderComponent({});
      const comp = fixture.componentInstance;

      const mockSubscription = { unsubscribe: vi.fn() };
      (comp as any).vScrollSubscription = mockSubscription;

      comp.ngOnDestroy();

      expect(mockSubscription.unsubscribe).toHaveBeenCalledTimes(1);
   });
});
