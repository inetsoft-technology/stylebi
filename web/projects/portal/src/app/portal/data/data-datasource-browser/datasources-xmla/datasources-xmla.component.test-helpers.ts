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
 * Shared test helpers for DatasourcesXmlaComponent multi-pass TL specs.
 * Consumed by:
 *   datasources-xmla.component.interaction.tl.spec.ts
 *   datasources-xmla.component.risk.tl.spec.ts
 *
 * Mocking strategy: ActivatedRoute.paramMap is a controllable Subject so tests can choose
 * whether to trigger ngOnInit HTTP flows. GettingStartedService and Router are vi.fn() mocks.
 * NgbModal is mocked to prevent real dialog opening. All heavy child components are stubbed via
 * importOverrides and/or NO_ERRORS_SCHEMA: TreeComponent, LoadingIndicatorPaneComponent,
 * DropdownView, AttributeFormattingPane, DataNotificationsComponent.
 * FixedDropdownDirective is stubbed to prevent DI errors from @ViewChildren query.
 * HTTP calls are tested in the risk spec via MSW; this file sets up no HTTP handlers.
 */

import {
   Component,
   Directive,
   EventEmitter,
   Input,
   NO_ERRORS_SCHEMA,
   Output,
} from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { vi } from "vitest";
import { GettingStartedService } from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { AttributeFormattingPane } from "../datasources-database/database-physical-model/logical-model/attribute-editor/format-dialog/attribute-formatting-pane.component";
import { DataNotificationsComponent } from "../../data-notifications.component";
import { LoadingIndicatorPaneComponent } from "../datasources-database/common-components/loading-indicator-pane/loading-indicator-pane.component";
import { DropdownView } from "../../../../widget/dropdown-view/dropdown-view.component";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { TreeComponent } from "../../../../widget/tree/tree.component";
import { CubeItemType } from "../../model/datasources/database/cube/xmla/cube-item-type";
import { CubeDimMemberModel } from "../../model/datasources/database/cube/xmla/cube-dim-member-model";
import { CubeDimensionModel } from "../../model/datasources/database/cube/xmla/cube-dimension-model";
import { CubeMeasureModel } from "../../model/datasources/database/cube/xmla/cube-measure-model";
import { CubeModel } from "../../model/datasources/database/cube/xmla/cube-model";
import { DatasourceXmlaDefinitionModel } from "../../model/datasources/database/cube/xmla/datasource-xmla-definition-model";
import { DomainModel } from "../../model/datasources/database/cube/xmla/domain-model";
import { XMetaInfoModel } from "../../model/datasources/database/cube/metaInfo-model";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { DatasourcesXmlaComponent } from "./datasources-xmla.component";

// ---------------------------------------------------------------------------
// Child-component stubs
// ---------------------------------------------------------------------------

@Component({ selector: "tree-component", template: "", standalone: true })
class TreeComponentStub {
   @Input() root: TreeNodeModel;
   @Input() selectedNodes: TreeNodeModel[];
   @Input() showRoot = false;
   @Input() multiSelect = false;
   @Output() nodesSelected = new EventEmitter<TreeNodeModel[]>();
}

@Component({ selector: "loading-indicator-pane", template: "", standalone: true })
class LoadingIndicatorPaneStub {
   @Input() loading = false;
}

@Component({ selector: "dropdown-view", template: "", standalone: true })
class DropdownViewStub {
   @Input() label = "";
   @Input() width: number;
}

@Component({ selector: "attribute-formatting-pane", template: "", standalone: true })
class AttributeFormattingPaneStub {
   @Input() formatModel: any;
   @Output() formatModelChange = new EventEmitter<any>();
   @Output() formatStringChange = new EventEmitter<string>();
}

@Component({ selector: "data-notifications", template: "", standalone: true })
export class DataNotificationsStub {
   notifications = {
      danger: vi.fn(),
      success: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
   };
}

@Directive({ selector: "[fixedDropdown]", standalone: true })
class FixedDropdownDirectiveStub {
   @Input() id = "";
   closed = true;
   toggleDropdown = vi.fn();
}

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

export function makeXmlaModel(overrides: Partial<DatasourceXmlaDefinitionModel> = {}): DatasourceXmlaDefinitionModel {
   return {
      name: "TestXmla",
      description: "",
      parentPath: "",
      datasource: "",
      datasourceName: "",
      datasourceInfo: "",
      catalogName: "TestCatalog",
      url: "http://localhost/xmla",
      user: "",
      password: "",
      useCredential: false,
      credentialId: "",
      credentialVisible: false,
      login: false,
      domain: null,
      ...overrides,
   };
}

export function makeDomain(cubes: CubeModel[] = []): DomainModel {
   return { datasource: "TestXmla", cubes };
}

export function makeCube(overrides: Partial<CubeModel> = {}): CubeModel {
   return {
      name: "SalesCube",
      caption: "Sales",
      type: "OLAP",
      dimensions: [],
      measures: [],
      ...overrides,
   };
}

export function makeDimension(overrides: Partial<CubeDimensionModel> = {}): CubeDimensionModel {
   return {
      dimensionName: "Date",
      uniqueName: "[Date]",
      caption: "Date",
      originalOrder: false,
      timeDimension: false,
      classType: "DimensionModel",
      members: [],
      ...overrides,
   } as CubeDimensionModel;
}

export function makeMetaInfo(overrides: Partial<XMetaInfoModel> = {}): XMetaInfoModel {
   return {
      drillInfo: { paths: [] } as any,
      formatInfo: { format: "", formatSpec: "" },
      asDate: false,
      datePattern: null,
      locale: null,
      ...overrides,
   };
}

export function makeMeasure(overrides: Partial<CubeMeasureModel> = {}): CubeMeasureModel {
   return {
      name: "Sales Amount",
      type: "NUMERIC",
      dataRef: null,
      metaInfo: null,
      originalType: "",
      dateLevel: null,
      classType: "CubeMeasureModel",
      uniqueName: "[Measures].[Sales Amount]",
      folder: "",
      caption: "Sales Amount",
      ...overrides,
   } as CubeMeasureModel;
}

export function makeDimMember(overrides: Partial<CubeDimMemberModel> = {}): CubeDimMemberModel {
   return {
      name: "Year",
      type: "DIMENSION",
      dataRef: null,
      metaInfo: null,
      originalType: "",
      dateLevel: null,
      classType: "CubeDimMemberModel",
      uniqueName: "[Date].[Year]",
      caption: "Year",
      number: 0,
      cube: "SalesCube",
      dimension: "Date",
      ...overrides,
   } as unknown as CubeDimMemberModel;
}

export { CubeMeasureModel };

export function makeCubeItemData(overrides: any = {}): any {
   return {
      uniqueName: "[Date]",
      cubeName: "SalesCube",
      type: CubeItemType.DIMENSION,
      hierarchy: false,
      userDefined: false,
      ...overrides,
   };
}

export function makeTreeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "Node",
      data: null,
      children: [],
      leaf: true,
      expanded: false,
      ...overrides,
   } as TreeNodeModel;
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const ROUTER_MOCK = {
   navigate: vi.fn(),
};

export const paramMap$ = new Subject<any>();

export const ROUTE_MOCK = {
   paramMap: paramMap$.asObservable(),
   snapshot: { paramMap: { get: vi.fn().mockReturnValue(null) } },
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const GETTING_STARTED_MOCK = {
   isConnectTo: vi.fn().mockReturnValue(false),
   setDataSourcePath: vi.fn(),
   continue: vi.fn(),
};

export let lastRenderedFixture: any = null;

export function resetMocks(): void {
   Object.values(ROUTER_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   Object.values(MODAL_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   Object.values(GETTING_STARTED_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   GETTING_STARTED_MOCK.isConnectTo.mockReturnValue(false);
   lastRenderedFixture = null;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface RenderOpts {
   model?: DatasourceXmlaDefinitionModel;
}

export async function renderXmla(opts: RenderOpts = {}) {
   const { fixture } = await render(DatasourcesXmlaComponent, {
      imports: [ReactiveFormsModule],
      providers: [
         { provide: Router, useValue: ROUTER_MOCK },
         { provide: ActivatedRoute, useValue: ROUTE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: GettingStartedService, useValue: GETTING_STARTED_MOCK },
      ],
      importOverrides: [
         { replace: TreeComponent, with: TreeComponentStub },
         { replace: LoadingIndicatorPaneComponent, with: LoadingIndicatorPaneStub },
         { replace: DropdownView, with: DropdownViewStub },
         { replace: AttributeFormattingPane, with: AttributeFormattingPaneStub },
         { replace: DataNotificationsComponent, with: DataNotificationsStub },
         { replace: FixedDropdownDirective, with: FixedDropdownDirectiveStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   lastRenderedFixture = fixture;
   const comp = fixture.componentInstance as DatasourcesXmlaComponent;

   if(opts.model) {
      comp.model = opts.model;
      fixture.detectChanges();
   }

   return { comp, fixture };
}
