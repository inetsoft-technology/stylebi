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

/** Minimal NgbModal mock — ModelService.handleError → ComponentTool.showMessageDialog needs open(). */
export function createNgbModalMock() {
   return {
      open: vi.fn(() => ({
         componentInstance: {
            options: [] as unknown[],
            title: "",
            message: "",
            onCommit: new Subject<string>(),
         },
         result: Promise.resolve("ok"),
         close: vi.fn(),
         dismiss: vi.fn(),
      })),
   };
}

export const FIELD_MC_PROVIDERS = [
   { provide: BindingService, useValue: { bindingModel: { availableFields: [] }, getURLParams: vi.fn(() => null) } },
   { provide: BindingTreeService, useValue: { root: null, treeChanged: { subscribe: vi.fn() }, getSelection: vi.fn(), setSelection: vi.fn() } },
   { provide: RecentColorService, useValue: { getRecentColors: vi.fn(() => []), addColor: vi.fn() } },
   { provide: NgbModal, useValue: createNgbModalMock() }
];
