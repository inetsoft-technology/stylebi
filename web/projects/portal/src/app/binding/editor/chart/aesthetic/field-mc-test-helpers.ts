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

import { of, Subject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BindingTreeService } from "../../../widget/binding-tree/binding-tree.service";
import { BindingService } from "../../../services/binding.service";
import { RecentColorService } from "../../../../widget/color-picker/recent-color.service";
import { DateLevelExamplesService } from "../../../../common/services/date-level-examples.service";
import { ModelService } from "../../../../widget/services/model.service";

export const FIELD_MC_PROVIDERS = [
   { provide: BindingService, useValue: { bindingModel: { availableFields: [] }, getURLParams: vi.fn(() => null) } },
   { provide: BindingTreeService, useValue: { root: null, treeChanged: { subscribe: vi.fn() }, getSelection: vi.fn(), setSelection: vi.fn() } },
   { provide: RecentColorService, useValue: { getRecentColors: vi.fn(() => []), addColor: vi.fn() } },
   {
      provide: NgbModal,
      // ComponentTool.showMessageDialog dereferences modal.componentInstance.onCommit.subscribe(...)
      // directly (no optional chaining), so the mocked modal ref needs a real onCommit Subject.
      useValue: {
         open: vi.fn(() => ({
            componentInstance: { onCommit: new Subject<string>() },
            result: new Promise(() => {}),
         })),
      }
   },
   // A dimension-typed aesthetic field renders <dimension-editor>, whose ngOnInit fires a real
   // loadDateLevelExamples() HTTP call unless this is mocked (see chart-fieldmc.component's
   // equivalent fix — same root cause, different sibling components).
   { provide: DateLevelExamplesService, useValue: { loadDateLevelExamples: vi.fn(() => of({ dateLevelExamples: {} })) } },
   // Opening the shape/color dropdown instantiates static-shape-pane / categorical-color-pane,
   // whose ngOnInit fires real ModelService.getModel/sendModel HTTP calls (imageShapes,
   // colorpalettes, getColorMappingDialogModel) with no MSW handler registered for them.
   {
      provide: ModelService,
      useValue: { getModel: vi.fn(() => of(null)), sendModel: vi.fn(() => of({ body: null })) }
   }
];
