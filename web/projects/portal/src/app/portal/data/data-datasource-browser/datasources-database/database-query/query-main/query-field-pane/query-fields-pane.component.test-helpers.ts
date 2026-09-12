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
 * Shared test helpers for QueryFieldsPaneComponent P1 / P2 spec files.
 *
 * Mocking strategy:
 *   - TreeComponent, DropdownView, AttributeFormattingPane, BrowseFieldValuesDialogComponent
 *     — replaced via importOverrides to avoid their DI chains; DropdownViewStub exposes
 *     close() because the template calls `formatDropdown.close()` via a ViewChild ref.
 *   - NgbModal — MODAL_MOCK.open returns a fresh componentInstance with onCommit/onCancel/
 *     onApply Subjects so ComponentTool.showDialog subscriptions fire correctly.
 *   - DataQueryModelService — only emitModelChange() is used; stubbed as vi.fn().
 *   - DragService — getDragDataValues() used in drop(); stubbed as vi.fn().
 *   - HttpClient — provideHttpClient() + MSW handlers in portal.handlers.ts.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { QueryFieldsPaneComponent } from "./query-fields-pane.component";
import {
   QueryFieldPaneModel
} from "../../../../../model/datasources/database/query/query-field-pane-model";
import { QueryFieldModel } from "../../../../../model/datasources/database/query/query-field-model";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../../../../../widget/tree/tree.component";
import { DropdownView } from "../../../../../../../widget/dropdown-view/dropdown-view.component";
import {
   AttributeFormattingPane
} from "../../../database-physical-model/logical-model/attribute-editor/format-dialog/attribute-formatting-pane.component";
import {
   BrowseFieldValuesDialogComponent
} from "./browse-field-values/browse-field-values-dialog.component";
import { MessageDialog } from "../../../../../../../widget/dialog/message-dialog/message-dialog.component";
import { DataQueryModelService } from "../../data-query-model.service";
import { DragService } from "../../../../../../../widget/services/drag.service";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => {
      let resolveResult: (v: any) => void;
      let rejectResult: (v: any) => void;
      const result = new Promise<any>((res, rej) => {
         resolveResult = res;
         rejectResult = rej;
      });
      return {
         result,
         componentInstance: {
            onCommit: new Subject<any>(),
            onCancel: new Subject<any>(),
            onApply: new Subject<any>(),
         },
         close: vi.fn((v?: any) => resolveResult(v)),
         dismiss: vi.fn((v?: any) => rejectResult(v)),
      };
   }),
};

export const QUERY_MODEL_SERVICE_MOCK = {
   emitModelChange: vi.fn(),
};

export const DRAG_SERVICE_MOCK = {
   getDragDataValues: vi.fn().mockReturnValue(null),
};

export function resetMocks(): void {
   Object.values(MODAL_MOCK).forEach(m => typeof (m as any).mockClear === "function" && (m as any).mockClear());
   Object.values(QUERY_MODEL_SERVICE_MOCK).forEach(m => typeof (m as any).mockClear === "function" && (m as any).mockClear());
   Object.values(DRAG_SERVICE_MOCK).forEach(m => typeof (m as any).mockClear === "function" && (m as any).mockClear());
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
}

// ---------------------------------------------------------------------------
// Stub components (standalone: true required for importOverrides into standalone host)
// ---------------------------------------------------------------------------

@Component({ selector: "tree", standalone: true, template: "" })
export class TreeComponentStub {
   @Input() root: TreeNodeModel;
   @Input() showRoot: boolean;
   @Input() multiSelect: boolean;
   @Input() selectOnClick: boolean;
   @Input() draggable: boolean;
   @Input() iconFunction: any;
   @Input() selectedNodes: TreeNodeModel[];
   @Input() showTooltip: boolean;
   @Output() nodesSelected = new EventEmitter<TreeNodeModel[]>();
   @Output() dblclickNode = new EventEmitter<TreeNodeModel>();
   @Output() nodeDrop = new EventEmitter<any>();
}

@Component({ selector: "dropdown-view", standalone: true, template: "<ng-content></ng-content>" })
export class DropdownViewStub {
   @Input() label: string;
   @Output() closed = new EventEmitter<void>();
   close() {}
}

@Component({ selector: "attribute-formatting-pane", standalone: true, template: "" })
export class AttributeFormattingPaneStub {
   @Input() formatModel: any;
   @Output() onApply = new EventEmitter<any>();
}

@Component({ selector: "browse-field-values-dialog", standalone: true, template: "" })
export class BrowseFieldValuesDialogStub {
   @Input() title: string;
   @Input() values: string[];
   @Output() onClose = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

export function makeField(
   name: string,
   alias: string,
   overrides: Partial<QueryFieldModel> = {},
): QueryFieldModel {
   return { name, alias, dataType: "string", drillInfo: null, format: null, ...overrides };
}

export function makeModel(
   fields: QueryFieldModel[],
   expressionAllowed = false,
): QueryFieldPaneModel {
   return { fields, expressionAllowed };
}

/** Leaf node whose getFieldFullName() returns fieldName (single-part, no table prefix). */
export function makeLeafNode(fieldName: string): TreeNodeModel {
   return {
      label: fieldName,
      leaf: true,
      children: [],
      data: { properties: { attribute: fieldName } } as any,
   };
}

/** Root tree containing leafNodes under a single "Table1" parent node. */
export function makeFieldsTree(leafNodes: TreeNodeModel[]): TreeNodeModel {
   return {
      label: "root",
      leaf: false,
      children: [{ label: "Table1", leaf: false, children: leafNodes }],
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface QueryFieldsPaneRenderOpts {
   model?: QueryFieldPaneModel;
   runtimeId?: string;
}

export async function renderComponent(opts: QueryFieldsPaneRenderOpts = {}) {
   const model =
      opts.model ?? makeModel([makeField("col1", "col1"), makeField("col2", "col2")]);

   const { fixture } = await render(QueryFieldsPaneComponent, {
      componentProperties: {
         model,
         runtimeId: opts.runtimeId ?? "rt123",
      },
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: DataQueryModelService, useValue: QUERY_MODEL_SERVICE_MOCK },
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
      ],
      importOverrides: [
         { replace: TreeComponent, with: TreeComponentStub },
         { replace: DropdownView, with: DropdownViewStub },
         { replace: AttributeFormattingPane, with: AttributeFormattingPaneStub },
         { replace: BrowseFieldValuesDialogComponent, with: BrowseFieldValuesDialogStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = fixture.componentInstance;
   // Wait for ngOnInit HTTP (initDatabaseFieldsTree) to complete so all tests start stable.
   await waitFor(() => expect(comp.databaseFieldsTree).not.toBeNull());
   return { comp, fixture };
}
