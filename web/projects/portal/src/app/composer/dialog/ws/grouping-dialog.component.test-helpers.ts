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
 * Shared test helpers for grouping-dialog.component P1/P2 spec files.
 *
 * These helpers use direct class instantiation (no TestBed) because the
 * component's constructor takes ModelService, AssetTreeService and NgbModal —
 * all of which are straightforward to stub without Angular DI.
 */

import { of } from "rxjs";
import { HttpResponse } from "@angular/common/http";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { GroupingDialog } from "./grouping-dialog.component";
import { Worksheet } from "../../data/ws/worksheet";
import { GroupingDialogModel } from "../../data/ws/grouping-dialog-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ConditionExpression } from "../../../common/data/condition/condition-expression";

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

export function makeAssetEntry(overrides: Partial<AssetEntry> = {}): AssetEntry {
   return {
      scope: 1,
      type: AssetType.QUERY,
      user: null,
      path: "Datasource/Query1",
      alias: null,
      identifier: "1^37^__NULL__^Datasource/Query1",
      properties: {},
      organization: null,
      ...overrides,
   };
}

export function makeGroupingDialogModel(overrides: Partial<GroupingDialogModel> = {}): GroupingDialogModel {
   return {
      newName: "Grouping1",
      oldName: "Grouping1",
      type: "-1",
      onlyFor: null,
      attribute: null,
      groupAllOthers: false,
      conditionExpressions: [],
      variableNames: [],
      ...overrides,
   };
}

export function makeWorksheet(overrides: Partial<Worksheet> = {}): Worksheet {
   const ws = new Worksheet();
   ws.runtimeId = "ws-runtime-1";
   Object.assign(ws, overrides);
   return ws;
}

export function makeConditionExpr(name = "Condition1"): ConditionExpression {
   return { name, list: { conditions: [], conjunction: 0 } as any };
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeModelServiceMock(): any {
   return {
      getModel: vi.fn().mockReturnValue(of(makeGroupingDialogModel())),
      // nodeExpanded expects res.body to be a TreeNodeModel (with .children array)
      sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: { children: [] } }))),
   };
}

export function makeAssetTreeServiceMock(): any {
   return {
      getAssetTreeNode: vi.fn().mockReturnValue(
         of({
            treeNodeModel: {
               children: [
                  {
                     label: "root",
                     leaf: false,
                     data: { path: "root" },
                     children: [],
                  },
               ],
            },
         })
      ),
   };
}

export function makeModalServiceMock(): any {
   return {
      open: vi.fn().mockReturnValue({
         result: Promise.resolve({ name: "NewGroup", list: { conditions: [], conjunction: 0 } }),
      }),
   };
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface GroupingDialogMocks {
   modelService: any;
   assetTreeService: any;
   modalService: any;
   worksheet: Worksheet;
}

export function makeComponent(overrides: Partial<GroupingDialogMocks> = {}): {
   comp: GroupingDialog;
   mocks: GroupingDialogMocks;
} {
   const modelService = overrides.modelService ?? makeModelServiceMock();
   const assetTreeService = overrides.assetTreeService ?? makeAssetTreeServiceMock();
   const modalService = overrides.modalService ?? makeModalServiceMock();
   const worksheet = overrides.worksheet ?? makeWorksheet();

   const comp = new GroupingDialog(modelService, assetTreeService, modalService);
   comp.worksheet = worksheet;
   comp.groupingName = null;

   const mocks: GroupingDialogMocks = { modelService, assetTreeService, modalService, worksheet };

   return { comp, mocks };
}

/**
 * Initialize the component with a pre-set model (bypasses HTTP).
 * This is the most common setup for unit tests.
 */
export function makeInitializedComponent(
   modelOverrides: Partial<GroupingDialogModel> = {},
   mocks: Partial<GroupingDialogMocks> = {}
): { comp: GroupingDialog; mocks: GroupingDialogMocks } {
   const { comp, mocks: createdMocks } = makeComponent(mocks);
   comp.model = makeGroupingDialogModel(modelOverrides);
   // Manually call ngOnInit which calls init() → initForm() + initRoot()
   // We bypass HTTP because comp.model is already set
   comp.ngOnInit();
   return { comp, mocks: createdMocks };
}
