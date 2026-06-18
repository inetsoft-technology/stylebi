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
 * Shared test helpers for PhysicalModelNetworkGraphComponent P1/P2 spec files.
 *
 * jsPlumb mocking strategy:
 *   vi.mock() is blocked by the ATL runner for relative imports AND silently ignored for
 *   npm packages (pre-bundled by esbuild before test time).
 *
 *   Instead we use vi.spyOn(jsPlumbLib.jsPlumb, "getInstance") inside renderComp() BEFORE
 *   render().  This is a runtime spy (no hoisting needed) and intercepts the call in
 *   jspInitGraphMain() so the component constructor receives jspMock directly — no real
 *   jsPlumb DOM initialization occurs.  The spy is restored immediately after render().
 *
 *   jspMock is exported so spec files can reference the same object in assertions.
 *   Its call histories are cleared at the start of each renderComp() call to prevent
 *   cross-test accumulation.
 *
 *   setRepaintTimer() is also spied before render (then restored) to prevent its zero-delay
 *   setTimeout from firing after ngOnDestroy sets this.jsp = null and crashing.
 *
 *   HTMLElement.prototype.scrollTo is stubbed once (jsdom doesn't implement it);
 *   ngAfterViewInit calls it on the graphPane div.
 */

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Point } from "../../../../../../common/data/point";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { PhysicalModelNetworkGraphComponent } from "./physical-model-network-graph.component";
import { JoinNodeGraphComponent } from "../../common-components/join-node-graph/join-node-graph.component";
import { OutOfZoneDirective } from "../../../../../../widget/directive/out-of-zone.directive";
import { SelectionBoxDirective } from "../../../../../../widget/directive/selection-box.directive";
import { DataPhysicalModelService } from "../../../../services/data-physical-model.service";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DomService } from "../../../../../../widget/dom-service/dom.service";
import { GraphModel } from "../../../../model/datasources/database/physical-model/graph/graph-model";
import { GraphViewModel } from "../../../../model/datasources/database/physical-model/graph/graph-view-model";
import { jsPlumbLib } from "../../../../../../composer/gui/ws/jsplumb/jsplumb";

// ---------------------------------------------------------------------------
// Shared jsPlumb mock — exported so spec files can assert on its .calls
// ---------------------------------------------------------------------------

/**
 * Shared jsPlumb instance mock.  Every renderComp() call clears all call histories
 * so tests don't accumulate each other's calls.  Implementations (mockReturnValue, etc.)
 * are preserved because clearAllMocks() only clears calls/results, not implementations.
 */
export const jspMock = {
   restoreDefaults: vi.fn(),
   importDefaults: vi.fn(),
   registerEndpointTypes: vi.fn(),
   registerConnectionTypes: vi.fn(),
   setZoom: vi.fn(),
   bind: vi.fn(),
   deleteEveryConnection: vi.fn(),
   deleteEveryEndpoint: vi.fn(),
   reset: vi.fn(),
   isSuspendDrawing: vi.fn().mockReturnValue(false),
   setSuspendDrawing: vi.fn(),
   selectEndpoints: vi.fn().mockReturnValue({
      setVisible: vi.fn(), each: vi.fn(), length: 0, get: vi.fn(),
   }),
   getAllConnections: vi.fn().mockReturnValue([]),
   connect: vi.fn(),
   addEndpoint: vi.fn().mockReturnValue({ setVisible: vi.fn() }),
   clearDragSelection: vi.fn(),
   addToDragSelection: vi.fn(),
   unmakeEverySource: vi.fn(),
   draggable: vi.fn(),
   isSource: vi.fn().mockReturnValue(false),
   makeSource: vi.fn(),
   repaintEverything: vi.fn(),
   setContainer: vi.fn(),
   getConnections: vi.fn().mockReturnValue([]),
   deleteConnection: vi.fn(),
};

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({ selector: "join-node-graph", template: "" })
export class JoinNodeGraphStub {
   @Input() runtimeId: string;
   @Input() selected: boolean;
   @Input() graph: any;
   @Input() graphEndpoints: any[];
   @Output() onCreateAutoAlias = new EventEmitter<string>();
   @Output() onEditInlineView = new EventEmitter<string>();
   @Output() onNodeSelected = new EventEmitter<any>();
   @Output() onAddEndpoint = new EventEmitter<any>();
   @Output() onShowEndpoints = new EventEmitter<any>();
   @Output() onHideEndpoints = new EventEmitter<any>();
   @Output() onMoveNodes = new EventEmitter<any>();
   @Output() onModified = new EventEmitter<any>();
   @Output() onRemoveSelectedNodes = new EventEmitter<void>();
   @Output() onRegisterNode = new EventEmitter<any>();
   @Output() onSetDraggable = new EventEmitter<any>();
}

@Directive({ selector: "[outOfZone]" })
export class OutOfZoneDirectiveStub {}

@Directive({ selector: "[selectionBox]" })
export class SelectionBoxDirectiveStub {
   @Input() selectionBoxScale: number;
   @Input() selectionBoxBannedSelector: string;
   @Output() onSelectionBox = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Fixture factories
// ---------------------------------------------------------------------------

export function makeNode(id: string, overrides: Record<string, any> = {}) {
   return {
      id,
      name: id,
      tableName: id,
      label: id,
      tooltip: id,
      treeLink: `/table/${id}`,
      aliasSource: null,
      outgoingAliasSource: null,
      ...overrides,
   };
}

export function makeGraph(id: string, overrides: Partial<GraphModel> = {}): GraphModel {
   return {
      node: makeNode(id),
      edge: { input: [], output: [] },
      cols: [],
      bounds: new Rectangle(0, 0, 100, 50),
      showColumns: false,
      alias: false,
      autoAlias: false,
      sql: false,
      baseTable: false,
      autoAliasByOutgoing: false,
      designModeAlias: false,
      ...overrides,
   } as GraphModel;
}

export function makeViewModel(graphs: GraphModel[] = []): GraphViewModel {
   return { graphs };
}

// ---------------------------------------------------------------------------
// Setup helpers
// ---------------------------------------------------------------------------

/**
 * Populates the private `nodes` and `sourceIds` maps so that `refreshDragSelection()`
 * correctly resolves each graph to its element ID (reference equality check).
 */
export function setupNodes(
   comp: PhysicalModelNetworkGraphComponent,
   graphs: GraphModel[]
): void {
   const nodes: Record<string, GraphModel> = {};
   const sourceIds: Record<string, string> = {};
   graphs.forEach((g, i) => {
      const elemId = `elem-${i}-${g.node.id}`;
      nodes[elemId] = g;
      sourceIds[g.node.id] = elemId;
   });
   (comp as any).nodes = nodes;
   (comp as any).sourceIds = sourceIds;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface NetworkGraphRenderOpts {
   graphViewModel?: GraphViewModel;
   runtimeId?: string;
   selectedGraphModels?: GraphModel[];
}

export async function renderComp(opts: NetworkGraphRenderOpts = {}) {
   const graphViewModel = opts.graphViewModel ?? makeViewModel();
   const onRefreshSpy = vi.fn();
   const onModifiedSpy = vi.fn();
   const onNodeSelectedSpy = vi.fn();
   const onRemoveTableSpy = vi.fn();
   const onCreateAutoAliasSpy = vi.fn();
   const onEditInlineViewSpy = vi.fn();

   // Clear jspMock call histories to prevent cross-test accumulation.
   // vi.clearAllMocks() would also work but is broader; this is targeted.
   (Object.values(jspMock) as any[]).forEach(fn => {
      if(fn && typeof fn.mockClear === "function") {
         fn.mockClear();
      }
   });

   const fixedDropdownMock = {
      open: vi.fn().mockReturnValue({ componentInstance: { actions: null, sourceEvent: null } }),
   };

   const domServiceMock = {
      requestRead: vi.fn().mockImplementation((fn: any) => fn()),
   };

   // jsdom does not implement HTMLElement.scrollTo; stub it so ngAfterViewInit doesn't crash.
   if(!(HTMLElement.prototype as any).scrollTo) {
      (HTMLElement.prototype as any).scrollTo = vi.fn();
   }

   // Intercept jspInitGraphMain()'s jsPlumbLib.jsPlumb.getInstance() call so the component
   // constructor receives jspMock instead of a real jsPlumb instance.  This is a runtime
   // vi.spyOn (no hoisting) which works even though vi.mock() is blocked for relative imports
   // by the ATL runner.  Without this spy the real jsPlumb initialization in jsdom can
   // trigger HTTP requests that MSW's onUnhandledRequest:"error" turns into worker crashes.
   const getInstanceSpy = vi.spyOn(jsPlumbLib.jsPlumb, "getInstance").mockReturnValue(jspMock as any);

   // Prevent ngOnInit's setRepaintTimer() from scheduling a real setTimeout that would
   // fire after ngOnDestroy (which sets this.jsp = null) and crash the test.
   const repaintSpy = vi.spyOn(
      PhysicalModelNetworkGraphComponent.prototype as any, "setRepaintTimer"
   ).mockImplementation(() => {});

   const { fixture } = await render(PhysicalModelNetworkGraphComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         DataPhysicalModelService,
         { provide: NgbModal, useValue: {} },
         { provide: FixedDropdownService, useValue: fixedDropdownMock },
         { provide: DomService, useValue: domServiceMock },
      ],
      importOverrides: [
         { replace: JoinNodeGraphComponent, with: JoinNodeGraphStub },
         { replace: OutOfZoneDirective, with: OutOfZoneDirectiveStub },
         { replace: SelectionBoxDirective, with: SelectionBoxDirectiveStub },
      ],
      componentInputs: {
         runtimeId: opts.runtimeId ?? "rt-123",
         graphViewModel,
         highlightConnections: [],
         scrollPoint: new Point(),
         selectedGraphModels: opts.selectedGraphModels ?? [],
      },
      on: {
         onRefreshPhysicalGraph: onRefreshSpy,
         onModified: onModifiedSpy,
         onNodeSelected: onNodeSelectedSpy,
         onRemoveTable: onRemoveTableSpy,
         onCreateAutoAlias: onCreateAutoAliasSpy,
         onEditInlineView: onEditInlineViewSpy,
      },
   });

   getInstanceSpy.mockRestore();
   repaintSpy.mockRestore();

   const comp = fixture.componentInstance as PhysicalModelNetworkGraphComponent;
   // comp.jsp is already jspMock (set by constructor via getInstance spy above)

   return {
      comp, fixture,
      onRefreshSpy, onModifiedSpy, onNodeSelectedSpy, onRemoveTableSpy,
      onCreateAutoAliasSpy, onEditInlineViewSpy,
      fixedDropdownMock,
   };
}
