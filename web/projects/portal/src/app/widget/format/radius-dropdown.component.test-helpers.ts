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
 * Shared test helpers for radius-dropdown.component P1/P2 spec files.
 *
 * Uses direct class instantiation.  DebounceService is injected but not
 * used by the component — pass a bare empty object.  The debounceTime(500)
 * RxJS operator uses setTimeout internally, so tests that exercise the
 * emission path must call vi.useFakeTimers() / vi.advanceTimersByTime(500).
 */

import { RadiusDropdown } from "./radius-dropdown.component";
import { UntypedFormGroup } from "@angular/forms";

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: RadiusDropdown;
}

export function makeComponent(opts: {
   radius?: number;
   max?: number;
   disabled?: boolean;
   externalForm?: UntypedFormGroup;
   skipNgOnInit?: boolean;
} = {}): ComponentResult {
   const comp = new RadiusDropdown({} as any);

   // Set inputs BEFORE ngOnInit so they are available during initForm
   if(opts.radius !== undefined) comp.radius = opts.radius;
   if(opts.max !== undefined) comp.max = opts.max;
   if(opts.disabled !== undefined) comp.disabled = opts.disabled;
   if(opts.externalForm !== undefined) comp.form = opts.externalForm;

   if(!opts.skipNgOnInit) {
      comp.ngOnInit();
   }

   return { comp };
}
