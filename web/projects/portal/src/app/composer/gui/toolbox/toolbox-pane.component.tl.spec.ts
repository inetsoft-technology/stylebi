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
 *   Group 8 [Risk 2] — Bug #76714: replayed binding tree settles useVirtualScroll before the first check (no NG0100)
 */

import { Component, NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { render } from "@testing-library/angular";
import { BehaviorSubject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ToolboxPane } from "./toolbox-pane.component";
import { DomService } from "../../../widget/dom-service/dom.service";
import { TreeTool } from "../../../common/util/tree-tool";
import { toolbox, toolboxDeployed } from "./toolbox.config";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { ComposerBindingTree } from "./composer-binding-tree.component";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ModelService } from "../../../widget/services/model.service";
import { ComposerObjectService } from "../vs/composer-object.service";

const DOM_SERVICE_MOCK = { requestAnimationFrame: vi.fn() };

@Component({ selector: "tree", template: "", standalone: true })
class TreeComponentStub {}

function makeTreeServiceMock(subject = new BehaviorSubject<TreeNodeModel>(null)) {
   return { bindingTreeChanged: vi.fn(() => subject.asObservable()) };
}

async function renderComponent(inputs: Partial<ToolboxPane> = {}) {
   return render(ToolboxPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: BindingTreeService, useValue: makeTreeServiceMock() },
      ],
      componentProviders: [
         { provide: DomService, useValue: DOM_SERVICE_MOCK },
      ],
      componentProperties: inputs,
   });
}

/**
 * Renders ToolboxPane with its real ComposerBindingTree child (grandchildren stubbed) and runs
 * a single dev-mode change-detection pass followed by an explicit checkNoChanges(), which is the
 * only way to surface NG0100 in this harness.
 */
async function renderWithRealBindingTree(root: TreeNodeModel) {
   const subject = new BehaviorSubject<TreeNodeModel>(root);

   return render(ToolboxPane, {
      schemas: [NO_ERRORS_SCHEMA],
      detectChangesOnRender: false,
      autoDetectChanges: false,
      importOverrides: [{ replace: TreeComponent, with: TreeComponentStub }],
      providers: [
         { provide: BindingTreeService, useValue: makeTreeServiceMock(subject) },
         { provide: FixedDropdownService, useValue: { open: vi.fn() } },
         { provide: NgbModal, useValue: {} },
         { provide: ModelService, useValue: { getModel: vi.fn() } },
         { provide: ComposerObjectService, useValue: { removeObjects: vi.fn() } },
      ],
      componentProviders: [
         { provide: DomService, useValue: DOM_SERVICE_MOCK },
      ],
      componentProperties: { inactive: false },
      configureTestBed: (testBed: TestBed) => {
         testBed.overrideComponent(ComposerBindingTree, {
            set: { imports: [], schemas: [NO_ERRORS_SCHEMA] }
         });
      },
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

function makeLargeTree(): TreeNodeModel {
   const children: TreeNodeModel[] = [];

   for(let i = 0; i < 2000; i++) {
      children.push({ label: "Col" + i, leaf: true, children: [] });
   }

   return { label: "Data Source", expanded: true, leaf: false, children };
}

describe("ToolboxPane — Bug #76714 NG0100 on first check", () => {
   // 🔁 Regression (Bug #76714): the BindingTreeService BehaviorSubject replays synchronously.
   //    When only the child ComposerBindingTree consumed it, the replay called treeNodesLoaded()
   //    from the child's ngOnInit, flipping useVirtualScroll true -> false after the parent's
   //    [style.height.px] binding was already evaluated -> NG0100 'height': '0' -> ''.
   it("should not throw NG0100 when the binding tree is empty (new dashboard)", async () => {
      const { fixture } = await renderWithRealBindingTree(null);

      fixture.detectChanges(false);
      expect(() => fixture.checkNoChanges()).not.toThrow();
      expect(fixture.componentInstance.useVirtualScroll).toBe(false);
   });

   it("should not throw NG0100 when the binding tree is small (no virtual scroll)", async () => {
      const root: TreeNodeModel = {
         label: "Data Source",
         expanded: true,
         leaf: false,
         children: [
            { label: "Col1", leaf: true, children: [] },
            { label: "Col2", leaf: true, children: [] },
         ],
      };
      const { fixture } = await renderWithRealBindingTree(root);

      fixture.detectChanges(false);
      expect(() => fixture.checkNoChanges()).not.toThrow();
      expect(fixture.componentInstance.useVirtualScroll).toBe(false);
      expect((fixture.componentInstance as any).combinationTreeRoot.children[0]).toBe(root);
   });

   it("should keep virtual scroll on without NG0100 when the replayed tree is large", async () => {
      const { fixture } = await renderWithRealBindingTree(makeLargeTree());

      fixture.detectChanges(false);
      expect(() => fixture.checkNoChanges()).not.toThrow();
      expect(fixture.componentInstance.useVirtualScroll).toBe(true);
   });

   // Bug #76714 follow-up: the tree now reaches ToolboxPane via its own service subscription, not
   // the child output, so later emissions (data source added/changed) must still re-evaluate it.
   it("should re-evaluate useVirtualScroll when the binding tree changes after init", async () => {
      const subject = new BehaviorSubject<TreeNodeModel>(null);
      const { fixture } = await render(ToolboxPane, {
         schemas: [NO_ERRORS_SCHEMA],
         componentImports: [],
         providers: [
            { provide: BindingTreeService, useValue: makeTreeServiceMock(subject) },
         ],
         componentProviders: [
            { provide: DomService, useValue: DOM_SERVICE_MOCK },
         ],
         componentProperties: { inactive: false },
      });
      expect(fixture.componentInstance.useVirtualScroll).toBe(false);

      const large = makeLargeTree();
      subject.next(large);
      fixture.detectChanges();
      expect(fixture.componentInstance.useVirtualScroll).toBe(true);
      expect((fixture.componentInstance as any).combinationTreeRoot.children[0]).toBe(large);

      subject.next(null);
      fixture.detectChanges();
      expect(fixture.componentInstance.useVirtualScroll).toBe(false);
   });

   it("should call treeNodesLoaded once per emission (no duplicate refresh via the child output)", async () => {
      const spy = vi.spyOn(ToolboxPane.prototype, "treeNodesLoaded");
      const { fixture } = await renderWithRealBindingTree(null);

      fixture.detectChanges(false);

      expect(spy).toHaveBeenCalledTimes(1);
   });

   it("should unsubscribe from BindingTreeService on destroy", async () => {
      const subject = new BehaviorSubject<TreeNodeModel>(null);
      const { fixture } = await render(ToolboxPane, {
         schemas: [NO_ERRORS_SCHEMA],
         componentImports: [],
         providers: [
            { provide: BindingTreeService, useValue: makeTreeServiceMock(subject) },
         ],
         componentProviders: [
            { provide: DomService, useValue: DOM_SERVICE_MOCK },
         ],
      });
      const spy = vi.spyOn(fixture.componentInstance, "treeNodesLoaded");

      fixture.destroy();
      subject.next({ label: "after-destroy", children: [], leaf: false });

      expect(spy).not.toHaveBeenCalled();
   });
});
