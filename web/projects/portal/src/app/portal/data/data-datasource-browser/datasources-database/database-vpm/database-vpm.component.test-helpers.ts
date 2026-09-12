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
 * Shared test helpers for DatabaseVPMComponent P1/P2 spec files.
 *
 * Mocking strategy:
 *   - VPMConditionsComponent, VPMHiddenColumnsComponent, VPMLookupComponent, VPMTestComponent —
 *     stubbed via importOverrides; stubs expose all @Input/@Output the parent template binds.
 *     VPMConditionsStub additionally exposes selectedCondition (mutated by resetVPM()).
 *   - NgbModal — empty object; P1 tests spy on ComponentTool.showConfirmDialog.
 *     P2 (saveVPM) provides a fuller MODAL_MOCK locally.
 *   - DataModelNameChangeService — nameChangeObservable is a Subject so tests can emit.
 *   - FolderChangeService — emitFolderChange spy.
 *   - ActivatedRoute — provided as { paramMap: of(convertToParamMap({...})) }.
 *   - Router — { navigate: vi.fn() }.
 *   - HttpClient — provideHttpClient() + MSW handlers in portal.handlers.ts.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { ActivatedRoute, convertToParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";

import { DatabaseVPMComponent } from "./database-vpm.component";
import { VPMConditionsComponent } from "./vpm-conditions/vpm-conditions.component";
import { VPMHiddenColumnsComponent } from "./vpm-hidden-columns/vpm-hidden-columns.component";
import { VPMLookupComponent } from "./vpm-lookup/vpm-lookup.component";
import { VPMTestComponent } from "./vpm-test/vpm-test.component";
import { FolderChangeService } from "../../../services/folder-change.service";
import { DataModelNameChangeService } from "../../../services/data-model-name-change.service";
import { NameChangeModel } from "../../../model/name-change-model";
export { NameChangeModel };

// ---------------------------------------------------------------------------
// Stub components
// ---------------------------------------------------------------------------

@Component({ selector: "vpm-conditions", template: "" })
export class VPMConditionsStub {
   @Input() databaseName: string;
   @Input() operations: any[];
   @Input() sessionOperations: any[];
   @Input() conditions: any[];
   @Output() refreshedColumns = new EventEmitter<boolean>();
   @Output() tableChange = new EventEmitter<void>();
   selectedCondition: any = null;
}

@Component({ selector: "vpm-hidden-columns", template: "" })
export class VPMHiddenColumnsStub {
   @Input() hidden: any;
   @Input() databaseName: string;
   @Input() availableRoles: any[];
   @Output() expressionChange = new EventEmitter<string>();
   @Output() hiddenColumnsChange = new EventEmitter<void>();
}

@Component({ selector: "vpm-lookup", template: "" })
export class VPMLookupStub {
   @Input() lookupList: string[];
   @Input() expression: string;
   @Output() expressionChange = new EventEmitter<string>();
}

@Component({ selector: "vpm-test", template: "" })
export class VPMTestStub {
   @Input() vpm: any;
   @Input() testData: any;
   @Input() databaseName: string;
}

// ---------------------------------------------------------------------------
// Route factories
// ---------------------------------------------------------------------------

/**
 * Create mode: "create" param present → editing=false, no refreshVPM().
 * Default vpmPath "myDB/myVPM" → databaseName="myDB", originalName="myVPM".
 */
export function makeCreateRoute(overrides: Record<string, string> = {}) {
   return {
      paramMap: of(convertToParamMap({
         vpmPath: "myDB/myVPM",
         create: "true",
         desc: "",
         ...overrides,
      })),
   };
}

/**
 * Edit mode: no "create" param → editing=true, refreshVPM() triggered.
 */
export function makeEditRoute(overrides: Record<string, string> = {}) {
   return {
      paramMap: of(convertToParamMap({
         vpmPath: "myDB/myVPM",
         ...overrides,
      })),
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface VPMRenderOpts {
   route?: any;
}

export async function renderComp(opts: VPMRenderOpts = {}) {
   const nameChangeSubject = new Subject<NameChangeModel>();
   const folderChangeMock = { emitFolderChange: vi.fn() };
   const routerMock = { navigate: vi.fn() };

   const route = opts.route ?? makeCreateRoute();

   const { fixture } = await render(DatabaseVPMComponent, {
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
         { replace: VPMConditionsComponent, with: VPMConditionsStub },
         { replace: VPMHiddenColumnsComponent, with: VPMHiddenColumnsStub },
         { replace: VPMLookupComponent, with: VPMLookupStub },
         { replace: VPMTestComponent, with: VPMTestStub },
      ],
   });
   const comp = fixture.componentInstance as DatabaseVPMComponent;
   return { comp, fixture, nameChangeSubject, folderChangeMock, routerMock };
}
