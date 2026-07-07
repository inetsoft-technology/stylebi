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
 * JoinNodeGraphComponent - single pass (+concurrency + memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngAfterViewInit registers draggable/endpoints and persists width
 *   Group 2 [Risk 2] - ngOnChanges re-registers reused nodes after graph refresh
 *   Group 3 [Risk 2] - icon selection, endpoint visibility, and selection emission
 *   Group 4 [Risk 2] - alias / refresh / query-property dialog actions
 */

import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { SimpleChange } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { ComponentTool } from "../../../../../../common/util/component-tool";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { GraphModel } from "../../../../model/datasources/database/physical-model/graph/graph-model";
import { DataPhysicalModelService } from "../../../../services/data-physical-model.service";
import { DataType } from "../join-thumbnail.service";
import { JoinNodeGraphComponent } from "./join-node-graph.component";

const CREATE_ALIAS_TABLE = "../api/data/physicalmodel/graph/alias";
const REFRESH_NODE = "../api/data/physicalmodel/graph/node/refresh";
const UPDATE_NODE_WIDTH = "../api/data/physicalmodel/graph/node/width/";
const EDIT_QUERY_TABLE_PROPERTIES = "../api/data/datasource/query/table/properties";

function makeGraph(overrides: Partial<GraphModel> = {}): GraphModel {
   return {
      node: {
         id: "1",
         name: "Orders",
         tableName: "Orders",
         label: "Orders",
         tooltip: "Orders table",
         treeLink: "/Orders",
         aliasSource: "Orders",
         outgoingAliasSource: "Orders",
      },
      edge: null,
      cols: [{ name: "order_id" } as any],
      bounds: new Rectangle(10, 20, 100, 50),
      showColumns: false,
      alias: false,
      autoAlias: false,
      sql: false,
      baseTable: false,
      autoAliasByOutgoing: false,
      designModeAlias: false,
      ...overrides,
   };
}

describe("JoinNodeGraphComponent - single pass", () => {
   let http: HttpTestingController;
   let fixedDropdownService: { open: ReturnType<typeof vi.fn> };
   let physicalModelService: {
      getTableName: ReturnType<typeof vi.fn>;
      getAutoAliasName: ReturnType<typeof vi.fn>;
      emitModelChange: ReturnType<typeof vi.fn>;
      aliasValidators: any[];
      aliasValidatorMessages: Record<string, string>;
   };

   beforeEach(() => {
      fixedDropdownService = {
         open: vi.fn().mockReturnValue({ componentInstance: {} }),
      };
      physicalModelService = {
         getTableName: vi.fn().mockReturnValue("Orders"),
         getAutoAliasName: vi.fn().mockReturnValue("Orders_Alias"),
         emitModelChange: vi.fn(),
         aliasValidators: [],
         aliasValidatorMessages: {},
      };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            { provide: NgbModal, useValue: { open: vi.fn() } },
            { provide: FixedDropdownService, useValue: fixedDropdownService },
            { provide: DataPhysicalModelService, useValue: physicalModelService },
         ],
      });

      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   function createComponent(overrides: Partial<JoinNodeGraphComponent> = {}) {
      const comp = new JoinNodeGraphComponent(
         { nativeElement: { focus: vi.fn(), clientWidth: 211 } } as any,
         TestBed.inject(NgbModal),
         TestBed.inject(HttpClient),
         TestBed.inject(DataPhysicalModelService),
         TestBed.inject(FixedDropdownService),
      );
      comp.runtimeId = "runtime-1";
      comp.graph = makeGraph();
      comp.graphEndpoints = [{ uuid: "ep-1" }, { uuid: "ep-2" }];
      Object.assign(comp, overrides);
      return comp;
   }

   describe("Group 1 - ngAfterViewInit", () => {
      it("should register drag handlers, endpoints, and persist width for physical nodes", () => {
         const comp = createComponent({ dataType: DataType.PHYSICAL });
         const draggableSpy = vi.spyOn(comp.onSetDraggable, "emit");
         const endpointSpy = vi.spyOn(comp.onAddEndpoint, "emit");
         const registerSpy = vi.spyOn(comp.onRegisterNode, "emit");

         comp.ngAfterViewInit();

         expect(draggableSpy).toHaveBeenCalledTimes(1);
         expect(endpointSpy).toHaveBeenCalledTimes(2);
         // nodeGraph is a private @ViewChild ElementRef — cast needed to verify the emitted element reference
         expect(registerSpy).toHaveBeenCalledWith([comp.graph, (comp as any).nodeGraph.nativeElement]);

         const req = http.expectOne(request => request.url === `${UPDATE_NODE_WIDTH}runtime-1`);
         expect(req.request.method).toBe("PUT");
         expect(req.request.params.get("table")).toBe("Orders");
         expect(req.request.params.get("width")).toBe("211");
         expect(req.request.params.get("alias")).toBe("Orders_Alias");
         req.flush(true);

         expect(comp.graph.bounds.width).toBe(211);
      });
   });

   describe("Group 2 - ngOnChanges", () => {
      it("should reinitialize endpoints and register the node when graph input changes", () => {
         const comp = createComponent();
         const endpointSpy = vi.spyOn(comp.onAddEndpoint, "emit");
         const registerSpy = vi.spyOn(comp.onRegisterNode, "emit");

         comp.ngOnChanges({
            graph: new SimpleChange(makeGraph({ node: { ...makeGraph().node, name: "Old" } }), makeGraph(), false),
         });

         expect(endpointSpy).toHaveBeenCalledTimes(2);
         // nodeGraph is a private @ViewChild ElementRef — cast needed to verify the emitted element reference
         expect(registerSpy).toHaveBeenCalledWith([comp.graph, (comp as any).nodeGraph.nativeElement]);
      });
   });

   describe("Group 3 - selection and endpoint visibility", () => {
      it("should show columns and emit node selection when clicking the icon while columns are hidden", () => {
         const comp = createComponent();
         const stopSpy = vi.spyOn(comp, "stopPropagation");
         const emitSpy = vi.spyOn(comp.onNodeSelected, "emit");
         const event = new MouseEvent("mousedown");

         comp.selectNodeByIcon(event);

         expect(stopSpy).toHaveBeenCalledWith(event);
         expect(comp.graph.showColumns).toBe(true);
         expect(emitSpy).toHaveBeenCalledWith(event);
      });

      it("should hide columns when clicking the icon while columns are already shown", () => {
         const comp = createComponent({ graph: makeGraph({ showColumns: true }) });
         const emitSpy = vi.spyOn(comp.onNodeSelected, "emit");
         const event = new MouseEvent("mousedown");

         comp.selectNodeByIcon(event);

         expect(comp.graph.showColumns).toBe(false);
         expect(emitSpy).toHaveBeenCalledWith(event);
      });

      it("should focus the node and emit showEndpoints on left click", () => {
         const comp = createComponent();
         const emitSpy = vi.spyOn(comp.onShowEndpoints, "emit");
         // nodeGraph is a private @ViewChild ElementRef — cast needed to spy on its focus method
         const focusSpy = vi.spyOn((comp as any).nodeGraph.nativeElement, "focus");

         comp.showEndpoint({ button: 0 } as MouseEvent);

         expect(focusSpy).toHaveBeenCalledTimes(1);
         expect(emitSpy).toHaveBeenCalledWith({ element: (comp as any).nodeGraph.nativeElement });
      });
   });

   describe("Group 4 - actions", () => {
      it("should create an alias through the dialog callback and refresh the model", () => {
         const comp = createComponent({ dataType: DataType.PHYSICAL });
         const dialog: any = {};
         vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _dialogType, onCommit) => {
            onCommit("Orders_Copy");
            return dialog;
         });

         (comp as any).createAlias();

         const req = http.expectOne(request => request.url === CREATE_ALIAS_TABLE);
         expect(req.request.method).toBe("POST");
         expect(req.request.params.get("alias")).toBe("Orders_Copy");
         expect(req.request.params.get("table")).toBe("Orders");
         req.flush(null);

         expect(physicalModelService.emitModelChange).toHaveBeenCalledTimes(1);
         expect(dialog.title).toBe("_#(js:Table Alias)");
      });

      it("should refresh an inline SQL table and emit model change", () => {
         const comp = createComponent({
            graph: makeGraph({ sql: true }),
         });

         (comp as any).refreshTable();

         const req = http.expectOne(request => request.url === REFRESH_NODE);
         expect(req.request.params.get("runtimeId")).toBe("runtime-1");
         expect(req.request.params.get("table")).toBe("Orders");
         req.flush({});

         expect(physicalModelService.emitModelChange).toHaveBeenCalledTimes(1);
      });

      it("should save query table properties and emit modified flags", () => {
         const comp = createComponent({ dataType: DataType.QUERY });
         const dialog: any = {};
         vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _dialogType, onCommit) => {
            onCommit({ name: "Orders", alias: "O" });
            return dialog;
         });
         const modifiedSpy = vi.spyOn(comp.onModified, "emit");
         const changedSpy = vi.spyOn(comp.onQueryPropertiesChanged, "emit");

         (comp as any).showQueryTablePropertiesDialog();

         const req = http.expectOne(request => request.url === EDIT_QUERY_TABLE_PROPERTIES);
         expect(req.request.method).toBe("POST");
         req.flush({});

         expect(modifiedSpy).toHaveBeenCalledWith(true);
         expect(changedSpy).toHaveBeenCalledWith(true);
         expect(dialog.runtimeId).toBe("runtime-1");
      });
   });
});
