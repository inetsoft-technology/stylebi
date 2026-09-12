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
 * Shared test helpers for variable-assembly-dialog.component P1/P2 spec files.
 *
 * Uses direct class instantiation — the component's constructor only needs
 * ModelService and NgbModal, both easily mocked.  ViewChild template refs
 * (variableListDialog, variableTableListDialog, defaultValueEditor) are
 * undefined in this mode; methods that open them receive the mock modalService
 * so the undefined ref is never actually used.
 */

import { of } from "rxjs";
import { VariableAssemblyDialog } from "./variable-assembly-dialog.component";
import { Worksheet } from "../../data/ws/worksheet";
import { VariableAssemblyDialogModel } from "../../data/ws/variable-assembly-dialog-model";
import { XSchema } from "../../../common/data/xschema";

// ---------------------------------------------------------------------------
// Model factory
// ---------------------------------------------------------------------------

export function makeModel(overrides: Partial<VariableAssemblyDialogModel> = {}): VariableAssemblyDialogModel {
   return {
      oldName: "testVar",
      newName: "",
      label: "Test Label",
      type: XSchema.STRING,
      defaultValue: null,
      selectionList: "none",
      displayStyle: 1,
      none: false,
      variableListDialogModel: { labels: [], values: [], dataType: XSchema.STRING },
      variableTableListDialogModel: null,
      otherVariables: ["var1", "var2"],
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Worksheet factory
// ---------------------------------------------------------------------------

export function makeWorksheet(overrides: Partial<{
   runtimeId: string;
   variables: any[];
   assemblyNames: (n: string) => string[];
}> = {}): Worksheet {
   return {
      runtimeId: "ws-rt-123",
      variables: [],
      assemblyNames: vi.fn(() => []),
      ...overrides,
   } as unknown as Worksheet;
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeModelService() {
   return {
      getModel: vi.fn().mockReturnValue(of(makeModel())),
      sendModel: vi.fn().mockReturnValue(of({ body: null })),
   };
}

export function makeModalService() {
   return {
      open: vi.fn().mockReturnValue({
         componentInstance: {},
         result: Promise.reject("dismissed"),
      }),
   };
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: VariableAssemblyDialog;
   modelSvc: ReturnType<typeof makeModelService>;
   modalSvc: ReturnType<typeof makeModalService>;
}

export function makeComponent(opts: {
   presetModel?: VariableAssemblyDialogModel;
   worksheet?: Worksheet;
   variableName?: string;
   modelSvc?: ReturnType<typeof makeModelService>;
   modalSvc?: ReturnType<typeof makeModalService>;
} = {}): ComponentResult {
   const modelSvc = opts.modelSvc ?? makeModelService();
   const modalSvc = opts.modalSvc ?? makeModalService();

   const comp = new VariableAssemblyDialog(modelSvc as any, modalSvc as any);
   comp.worksheet = opts.worksheet ?? makeWorksheet();
   comp.tables = [];

   if(opts.variableName !== undefined) {
      comp.variableName = opts.variableName;
   }

   if(opts.presetModel !== undefined) {
      comp.model = opts.presetModel;
      comp.ngOnInit();
   }
   else {
      // getModel is already mocked to return makeModel(); just trigger ngOnInit
      comp.ngOnInit();
   }

   return { comp, modelSvc, modalSvc };
}
