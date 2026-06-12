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
 * Shared test fixtures for VSFormatsPane spec files.
 * Imported by VSFormatsPane.display.tl.spec.ts and VSFormatsPane.interaction.tl.spec.ts.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";

import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { ModelService } from "../../widget/services/model.service";
import { FontService } from "../../widget/services/font.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { VSFormatsPane } from "./vs-formats-pane.component";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { TestUtils } from "../../common/test/test-utils";

// FontService mock — returns fonts synchronously; no HTTP needed.
export const FONT_SERVICE_MOCK = {
   getAllFonts: vi.fn().mockReturnValue(of(["Arial", "Roboto"])),
};

// ModelService mock — getModel returns null by default; override per test as needed.
export const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of(null)),
};

// DebounceService mock — executes the callback synchronously so output events fire immediately.
export const DEBOUNCE_MOCK = {
   debounce: vi.fn().mockImplementation((_key: string, fn: () => void) => fn()),
   cancel: vi.fn(),
};

// NgbModal mock — each call returns a fresh ref with a resolvable result promise.
// Works for both showMessageDialog/showConfirmDialog (via onCommit) and direct modal.result usage.
export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => {
      let resolveResult: (val: any) => void;
      const result = new Promise<any>((res) => { resolveResult = res; });
      const onCommit = new Subject<string>();
      return {
         result,
         componentInstance: { onCommit },
         close: vi.fn().mockImplementation((val: any) => resolveResult(val)),
         dismiss: vi.fn(),
      };
   }),
};

export function resetMocks(): void {
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
   MODAL_MOCK.open.mockClear();
   DEBOUNCE_MOCK.debounce.mockClear();
   DEBOUNCE_MOCK.cancel.mockClear();
   FONT_SERVICE_MOCK.getAllFonts.mockClear();
   FONT_SERVICE_MOCK.getAllFonts.mockReturnValue(of(["Arial", "Roboto"]));
   MODEL_SERVICE_MOCK.getModel.mockClear();
   MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(null));
}

export interface RenderOptions {
   viewer?: boolean;
   vsId?: string;
   format?: FormatInfoModel | VSObjectFormatInfoModel;
}

export async function renderComponent(opts: RenderOptions = {}) {
   const { fixture } = await render(VSFormatsPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: FontService, useValue: FONT_SERVICE_MOCK },
         { provide: DebounceService, useValue: DEBOUNCE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      componentInputs: {
         viewer: opts.viewer ?? false,
         vsId: opts.vsId ?? "test-vs",
         format: opts.format ?? TestUtils.createMockVSObjectFormatInfoModel(),
      },
   });
   return { comp: fixture.componentInstance as VSFormatsPane, fixture };
}
