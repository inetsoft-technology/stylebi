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
 * Shared test helpers for vpm-principal-dialog.component P1/P2 spec files.
 *
 * Uses direct class instantiation — constructor only takes ModelService.
 * FormGroup is created inside initForm() using Angular forms classes (no DI).
 */

import { of } from "rxjs";
import { VPMPrincipalDialogComponent } from "./vpm-principal-dialog.component";
import { VPMPrincipalDialogModel } from "../../data/ws/vpm-principal-dialog-model";
import { Worksheet } from "../../data/ws/worksheet";

// ---------------------------------------------------------------------------
// Model / worksheet factories
// ---------------------------------------------------------------------------

export function makeModel(overrides: Partial<VPMPrincipalDialogModel> = {}): VPMPrincipalDialogModel {
   return {
      vpmSelectable: true,
      vpmEnabled: true,
      sessionType: "user",
      sessionId: "admin",
      users: ["admin", "user1"],
      roles: ["analyst", "developer"],
      ...overrides,
   };
}

export function makeWorksheet(overrides: Partial<{ runtimeId: string }> = {}): Worksheet {
   return {
      runtimeId: "ws-rt-abc",
      ...overrides,
   } as unknown as Worksheet;
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeModelService(model: VPMPrincipalDialogModel = makeModel()) {
   return { getModel: vi.fn().mockReturnValue(of(model)) };
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: VPMPrincipalDialogComponent;
   modelSvc: ReturnType<typeof makeModelService>;
}

export function makeComponent(opts: {
   model?: VPMPrincipalDialogModel;
   modelSvc?: ReturnType<typeof makeModelService>;
   worksheet?: Worksheet;
   skipNgOnInit?: boolean;
} = {}): ComponentResult {
   const model = opts.model ?? makeModel();
   const modelSvc = opts.modelSvc ?? makeModelService(model);

   const comp = new VPMPrincipalDialogComponent(modelSvc as any);
   comp.worksheet = opts.worksheet ?? makeWorksheet();

   if(!opts.skipNgOnInit) {
      comp.ngOnInit();
   }

   return { comp, modelSvc };
}
