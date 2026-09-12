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
 * Shared test helpers for DatabaseVPMBrowserComponent P1/P2 spec files.
 *
 * Mocking strategy:
 *   - NotificationsComponent — stubbed via importOverrides; exposes success/info/warning/danger
 *     as vi.fn() so tests can assert notification calls via (comp.notifications as any).success.
 *   - FolderChangeService — { emitFolderChange: vi.fn() }.
 *   - FixedDropdownService — open() spy returning a mutable componentInstance stub.
 *   - NgbModal — empty object; tests spy on ComponentTool.showDialog / showConfirmDialog.
 *   - ActivatedRoute — { paramMap: of({databaseName, physicalModelName}), queryParamMap: of({}) }.
 *   - Router — { navigate: vi.fn() }.
 *   - HttpClient — provideHttpClient() + MSW handlers in portal.handlers.ts.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { ActivatedRoute, convertToParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";

import { DatabaseVPMBrowserComponent } from "./database-vpm-browser.component";
import { NotificationsComponent } from "../../../../../../widget/notifications/notifications.component";
import { FolderChangeService } from "../../../../services/folder-change.service";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { VPMBrowserInfo } from "../../../../model/datasources/database/vpm/vpm-browser-info";

// ── Stub components ──────────────────────────────────────────────────────────

@Component({ selector: "notifications", template: "", standalone: true })
export class NotificationsStub {
   success = vi.fn();
   info = vi.fn();
   warning = vi.fn();
   danger = vi.fn();
}

// ── Shared fixture data ──────────────────────────────────────────────────────

export const MOCK_VPM: VPMBrowserInfo = {
   type: "VPM",
   id: "SalesDB/VPM1",
   path: "SalesDB/VPM1",
   urlPath: "SalesDB/VPM1",
   name: "VPM1",
   createdBy: "admin",
   description: "Test VPM",
   createdDate: 1700000000000,
   editable: true,
   deletable: true,
   createdDateLabel: "2024-01-01",
   databaseName: "SalesDB",
};

export const MOCK_BROWSE_RESPONSE = {
   editable: true,
   deletable: true,
   items: [MOCK_VPM],
   names: ["VPM1"],
   dateFormat: "yyyy-MM-dd",
};

// ── Render helper ────────────────────────────────────────────────────────────

export interface VPMBrowserRenderOpts {
   databaseName?: string;
}

export async function renderComp(opts: VPMBrowserRenderOpts = {}) {
   const databaseName = opts.databaseName ?? "SalesDB";
   const routerMock = { navigate: vi.fn() };
   const folderChangeMock = { emitFolderChange: vi.fn() };
   const dropdownInstanceMock: { actions: any; sourceEvent: any } = {
      actions: null,
      sourceEvent: null,
   };
   const dropdownServiceMock = {
      open: vi.fn().mockReturnValue({ componentInstance: dropdownInstanceMock }),
   };

   const route = {
      paramMap: of(convertToParamMap({ databaseName, physicalModelName: "" })),
      queryParamMap: of(convertToParamMap({})),
   };

   const { fixture } = await render(DatabaseVPMBrowserComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: Router, useValue: routerMock },
         { provide: ActivatedRoute, useValue: route },
         { provide: NgbModal, useValue: {} },
         { provide: FolderChangeService, useValue: folderChangeMock },
         { provide: FixedDropdownService, useValue: dropdownServiceMock },
      ],
      importOverrides: [
         { replace: NotificationsComponent, with: NotificationsStub },
      ],
   });

   const comp = fixture.componentInstance as DatabaseVPMBrowserComponent;
   return { comp, fixture, routerMock, folderChangeMock, dropdownServiceMock, dropdownInstanceMock, route };
}
