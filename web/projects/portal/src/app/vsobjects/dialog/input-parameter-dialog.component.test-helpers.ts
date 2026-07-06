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
 * Shared fixtures for
 * input-parameter-dialog.component.{interaction,risk,display}.tl.spec.ts.
 *
 * Direct instantiation — two constructor dependencies (NgbDateParserFormatter,
 * ChangeDetectorRef), neither with `inject()` calls, so no TestBed wiring is needed.
 * The component builds a REAL Angular UntypedFormGroup/FormControl tree itself
 * (initForm()); FormControl/FormGroup don't require DI to construct, so form validity
 * assertions exercise real Angular Forms validation logic, not a mock.
 *
 * `createComponent()` does NOT call ngOnInit() automatically — Pass 1 tests call it
 * explicitly so the pre-init vs. post-init model/form states stay distinguishable.
 */

import { ChangeDetectorRef } from "@angular/core";
import { NgbDateParserFormatter, NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { InputParameterDialog } from "./input-parameter-dialog.component";
import { InputParameterDialogModel } from "../model/input-parameter-dialog-model";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";

export function makeModel(overrides: Partial<InputParameterDialogModel> = {}): InputParameterDialogModel {
   return Object.assign({
      name: "param1",
      valueSource: "constant",
      type: XSchema.STRING,
      value: "",
   }, overrides);
}

export function makeField(overrides: Partial<DataRef> = {}): DataRef {
   return Object.assign({
      name: "city", attribute: "city", entity: "", dataType: XSchema.STRING, refType: 0,
   }, overrides) as DataRef;
}

function pad(n: number): string {
   return n < 10 ? "0" + n : "" + n;
}

export interface CreateComponentOpts {
   fields?: DataRef[];
   selectEdit?: boolean;
   model?: InputParameterDialogModel;
   viewsheetParameters?: string[];
}

export function createComponent(opts: CreateComponentOpts = {}) {
   // A minimal but functionally-real date-struct <-> string bridge, matching the shape
   // NgbDateParserFormatter callers expect ({year, month, day} <-> "YYYY-MM-DD").
   const ngbDateParserFormatter = {
      parse: vi.fn((value: string): NgbDateStruct => {
         if(!value) {
            return null;
         }

         const [year, month, day] = value.split("-").map(Number);
         return { year, month, day };
      }),
      format: vi.fn((date: NgbDateStruct): string => {
         return date ? `${date.year}-${pad(date.month)}-${pad(date.day)}` : "";
      }),
   };
   const changeDetectionRef = { detectChanges: vi.fn() };

   const comp = new InputParameterDialog(
      ngbDateParserFormatter as unknown as NgbDateParserFormatter,
      changeDetectionRef as unknown as ChangeDetectorRef
   );
   comp.fields = opts.fields ?? [];
   comp.selectEdit = opts.selectEdit ?? true;
   comp.viewsheetParameters = opts.viewsheetParameters ?? null;
   comp.model = opts.model ?? makeModel();

   return { comp, ngbDateParserFormatter, changeDetectionRef };
}
