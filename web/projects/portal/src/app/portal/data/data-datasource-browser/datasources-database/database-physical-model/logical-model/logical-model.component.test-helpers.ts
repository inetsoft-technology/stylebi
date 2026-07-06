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
 * Shared test helpers for LogicalModelComponent P1/P2 spec files.
 *
 * Mocking strategy:
 *   - LogicalModelPropertyPane — complex DI chain → stubbed via importOverrides.
 *   - NotificationsComponent — used via @ViewChild("notifications"); stubbed with success/
 *     info/warning/danger as vi.fn() so tests can assert on notification calls.
 *   - DataModelNameChangeService — provides nameChangeObservable as a Subject that tests
 *     can emit to; re-created per renderComp() call to prevent cross-test bleed.
 *   - FolderChangeService — emitFolderChange spy.
 *   - LogicalModelService — used at component level (providers: [LogicalModelService]); the
 *     REAL service is used since it has no constructor DI. Tests access it via comp.lmService.
 *   - ActivatedRoute — provided as { paramMap: of(convertToParamMap({...})) }; makeCreateRoute()
 *     omits "create" in params so that editing=true; makeCreateRoute() includes it.
 *   - Router — { navigate: vi.fn() }.
 *   - HttpClient — provideHttpClient() + MSW handlers in portal.handlers.ts.
 */

import { Component, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { convertToParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";

import { LogicalModelComponent } from "./logical-model.component";
import { LogicalModelPropertyPane } from "./logical-model-property-pane.component";
import { NotificationsComponent } from "../../../../../../widget/notifications/notifications.component";
import { DataModelNameChangeService } from "../../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../../services/folder-change.service";
import { ActivatedRoute } from "@angular/router";
import { NameChangeModel } from "../../../../model/name-change-model";

// ---------------------------------------------------------------------------
// Stub components
// ---------------------------------------------------------------------------

@Component({ selector: "logical-model-property-pane", template: "" })
export class LogicalModelPropertyPaneStub {
   @Input() logicalModel: any;
   @Input() editing: boolean;
   @Input() loading: boolean;
}

// NotificationsComponent stub must expose the same public API the component calls.
@Component({ selector: "notifications", template: "" })
export class NotificationsStub {
   success = vi.fn();
   info = vi.fn();
   warning = vi.fn();
   danger = vi.fn();
}

// ---------------------------------------------------------------------------
// Route factories
// ---------------------------------------------------------------------------

/**
 * Create mode: includes "create" param → editing=false, no refreshModel().
 * Default has no "parent" → no createExtendedModel().
 */
export function makeCreateRoute(overrides: Record<string, string> = {}) {
   return {
      paramMap: of(convertToParamMap({
         databasePath: "testDB",
         physicalModelName: "physModel",
         logicalModelName: "LM1",
         create: "true",
         ...overrides,
      })),
   };
}

/**
 * Edit mode: no "create" param → editing=true, refreshModel() triggered.
 */
export function makeEditRoute(overrides: Record<string, string> = {}) {
   return {
      paramMap: of(convertToParamMap({
         databasePath: "testDB",
         physicalModelName: "physModel",
         logicalModelName: "LM1",
         ...overrides,
      })),
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface LMRenderOpts {
   route?: any;
}

export async function renderComp(opts: LMRenderOpts = {}) {
   const nameChangeSubject = new Subject<NameChangeModel>();
   const folderChangeMock = { emitFolderChange: vi.fn() };
   const routerMock = { navigate: vi.fn() };

   const route = opts.route ?? makeCreateRoute();

   const { fixture } = await render(LogicalModelComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: {} },
         { provide: DataModelNameChangeService, useValue: { nameChangeObservable: nameChangeSubject.asObservable() } },
         { provide: FolderChangeService, useValue: folderChangeMock },
         { provide: ActivatedRoute, useValue: route },
         { provide: Router, useValue: routerMock },
      ],
      importOverrides: [
         { replace: LogicalModelPropertyPane, with: LogicalModelPropertyPaneStub },
         { replace: NotificationsComponent, with: NotificationsStub },
      ],
   });
   const comp = fixture.componentInstance as LogicalModelComponent;
   return { comp, fixture, nameChangeSubject, folderChangeMock, routerMock };
}
