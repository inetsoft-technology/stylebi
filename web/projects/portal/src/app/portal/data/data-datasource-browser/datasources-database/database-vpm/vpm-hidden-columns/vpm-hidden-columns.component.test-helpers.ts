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
 * Shared test helpers for VPMHiddenColumnsComponent P1/P2 spec files.
 *
 * Mocking strategy:
 *   - TreeComponent — stubbed via importOverrides; exposes selectedNodes as a settable
 *     property (P1 tests manipulate it directly via (comp.columnTree as any).selectedNodes).
 *   - DataModelScriptPane, LoadingIndicatorPaneComponent — minimal stubs.
 *   - NgbModal — empty object; individual tests spy on ComponentTool.showMessageDialog.
 *   - CurrentUserService — mock returning fixed orgID so getBaseName() tests are deterministic.
 *   - HttpClient — provideHttpClient() + MSW handlers in portal.handlers.ts.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";

import { VPMHiddenColumnsComponent } from "./vpm-hidden-columns.component";
import { TreeComponent } from "../../../../../../widget/tree/tree.component";
import { DataModelScriptPane } from "../../database-physical-model/data-model-script-pane/data-model-script-pane.component";
import { LoadingIndicatorPaneComponent } from "../../common-components/loading-indicator-pane/loading-indicator-pane.component";
import { CurrentUserService } from "../../../../../../../../../shared/util/current-user.service";
import { HiddenColumnsModel } from "../../../../model/datasources/database/vpm/hidden-columns-model";
import { TreeNodeModel } from "../../../../../../widget/tree/tree-node-model";
import { DatabaseTreeNodeType } from "../../../../model/datasources/database/database-tree-node-type";
import { DataRef } from "../../../../../../common/data/data-ref";
import { DataItem } from "../../../../model/datasources/database/vpm/test-data-model";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({ selector: "tree", template: "", standalone: true })
export class TreeComponentStub {
   @Input() root: TreeNodeModel;
   @Input() selectedNodes: TreeNodeModel[] = [];
   @Input() multiSelect: boolean;
   @Input() showIcon: boolean;
   @Input() iconFunction: any;
   @Input() nodeSelectable: boolean;
   @Input() showRoot: boolean;
   @Input() searchStr: string;
}

@Component({ selector: "data-model-script-pane", template: "", standalone: true })
export class DataModelScriptPaneStub {
   @Input() script: string;
   @Input() sql: boolean;
   @Output() expressionChange = new EventEmitter<string>();
}

@Component({ selector: "loading-indicator-pane", template: "", standalone: true })
export class LoadingIndicatorPaneStub {
   @Input() show: boolean;
}

// ---------------------------------------------------------------------------
// Data factories
// ---------------------------------------------------------------------------

export function makeHidden(overrides: Partial<HiddenColumnsModel> = {}): HiddenColumnsModel {
   return {
      hiddens: [],
      roles: [],
      name: null,
      script: null,
      ...overrides,
   };
}

export function makeDataRef(name: string, entity = "", overrides: Partial<DataRef> = {}): DataRef {
   return {
      classType: "AttributeRef",
      name,
      entity,
      attribute: name,
      caption: "",
      ...overrides,
   } as unknown as DataRef;
}

export function makeColumnNode(overrides: Record<string, any> = {}): TreeNodeModel {
   return {
      label: "col1",
      type: DatabaseTreeNodeType.COLUMN,
      leaf: true,
      children: [],
      childrenLoaded: false,
      data: {
         attribute: "col1",
         entity: "TABLE1",
         fullName: "TABLE1.col1",
         qualifiedName: "col1",
      },
      ...overrides,
   };
}

export function makeTableNode(
   columns: TreeNodeModel[] = [],
   overrides: Record<string, any> = {}
): TreeNodeModel {
   return {
      label: "TABLE1",
      type: DatabaseTreeNodeType.TABLE,
      leaf: false,
      children: columns,
      childrenLoaded: columns.length > 0,
      data: {
         attribute: null,
         entity: "TABLE1",
         fullName: "TABLE1",
         qualifiedName: "TABLE1",
      },
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface VPMHiddenRenderOpts {
   hidden?: HiddenColumnsModel;
   databaseName?: string;
   availableRoles?: DataItem[];
   currentOrgID?: string;
}

export async function renderComp(opts: VPMHiddenRenderOpts = {}) {
   const {
      hidden = makeHidden(),
      databaseName = "myDB",
      availableRoles = [],
      currentOrgID = "",
   } = opts;

   const currentUserMock = {
      getPortalCurrentUser: () =>
         of({ name: { name: "admin", orgID: currentOrgID } }),
   };

   const { fixture } = await render(VPMHiddenColumnsComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: {} },
         { provide: CurrentUserService, useValue: currentUserMock },
      ],
      importOverrides: [
         { replace: TreeComponent, with: TreeComponentStub },
         { replace: DataModelScriptPane, with: DataModelScriptPaneStub },
         { replace: LoadingIndicatorPaneComponent, with: LoadingIndicatorPaneStub },
      ],
      componentProperties: {
         hidden,
         databaseName,
         availableRoles,
      },
   });
   const comp = fixture.componentInstance as VPMHiddenColumnsComponent;
   return { comp, fixture };
}
