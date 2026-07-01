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

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subject } from "rxjs";

import { RepositoryClientService } from "../../../common/repository-client/repository-client.service";
import { RepositoryTreeService } from "../../../widget/repository-tree/repository-tree.service";
import { AnalyzeMVDialog } from "./analyze-mv-dialog.component";

interface AnalyzeMVDialogPrivateApi {
   informChangedAndClose(): void;
   onRepositoryChanged(): void;
}

interface AnalyzeMVDialogRepositoryClientStub {
   connect: ReturnType<typeof vi.fn>;
   repositoryChanged$: Subject<unknown>;
   repositoryChanged: Observable<unknown>;
}

interface AnalyzeMVDialogCreatePaneStub {
   createOrUpdate: ReturnType<typeof vi.fn>;
   showPlanClicked: ReturnType<typeof vi.fn>;
}

interface AnalyzeMVDialogAnalyzePaneStub {
   selectedMVs: unknown[];
}

interface AnalyzeMVDialogModalStub {
   open: ReturnType<typeof vi.fn>;
}

interface AnalyzeMVDialogResponseStatus {
   name: string;
   lastModifiedTimestamp: number;
   lastModifiedTime?: string;
}

interface AnalyzeMVDialogResponse {
   completed?: boolean;
   exception?: boolean;
   status?: AnalyzeMVDialogResponseStatus[];
   cycles?: { name: string; label: string }[];
   onDemand?: boolean;
   defaultCycle?: string;
   runInBackground?: boolean;
   dateFormat?: string;
   analysisId?: string;
}

interface AnalyzeMVDialogSelectedNode {
   identifier: string;
   path?: string;
   type: number;
}

interface AnalyzeMVDialogModel {
   name: string;
   hasData?: boolean;
   exists?: boolean;
   dataString?: string;
   existString?: string;
   lastModifiedTimestamp?: number;
   lastModifiedTime?: string;
}

export function createAnalyzeMVRepositoryClientStub(): AnalyzeMVDialogRepositoryClientStub {
   const repositoryChanged$ = new Subject<unknown>();

   return {
      connect: vi.fn(),
      repositoryChanged$,
      repositoryChanged: repositoryChanged$.asObservable(),
   };
}

export function createAnalyzeMVDialogModalStub(): AnalyzeMVDialogModalStub {
   return {
      open: vi.fn(),
   };
}

export function createAnalyzeMVDialog(options: {
   modalService?: AnalyzeMVDialogModalStub;
   repositoryClient?: AnalyzeMVDialogRepositoryClientStub;
 }) {
   const repositoryClient = options.repositoryClient ?? createAnalyzeMVRepositoryClientStub();
   const modalService = options.modalService ?? createAnalyzeMVDialogModalStub();

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         { provide: RepositoryTreeService, useValue: {} },
         { provide: RepositoryClientService, useValue: repositoryClient },
         { provide: NgbModal, useValue: modalService },
      ],
   });

   const comp = new AnalyzeMVDialog(
      TestBed.inject(RepositoryTreeService),
      TestBed.inject(HttpClient),
      TestBed.inject(RepositoryClientService),
      TestBed.inject(NgbModal),
   );

   return {
      comp,
      repositoryClient,
      modalService,
   };
}

export function asAnalyzeMVDialogPrivateApi(comp: AnalyzeMVDialog): AnalyzeMVDialogPrivateApi {
   // TL coverage needs access to repository-change side effects without changing production visibility.
   return comp as unknown as AnalyzeMVDialogPrivateApi;
}

export function attachAnalyzeMVCreatePane(comp: AnalyzeMVDialog): AnalyzeMVDialogCreatePaneStub {
   const pane = {
      createOrUpdate: vi.fn(),
      showPlanClicked: vi.fn(),
   };

   // Tests inject the minimal @ViewChild surface directly because the dialog logic calls child methods.
   (comp as unknown as { createMVPane: AnalyzeMVDialogCreatePaneStub }).createMVPane = pane;
   return pane;
}

export function attachAnalyzeMVAnalyzePane(
   comp: AnalyzeMVDialog,
   selectedMVs: unknown[] = [],
): AnalyzeMVDialogAnalyzePaneStub {
   const pane = { selectedMVs };

   // Tests inject the minimal @ViewChild surface directly because deleteMV reads the selection list.
   (comp as unknown as { analyzeMVPane: AnalyzeMVDialogAnalyzePaneStub }).analyzeMVPane = pane;
   return pane;
}

export function makeAnalyzeMVDialogResponse(
   overrides: Partial<AnalyzeMVDialogResponse> = {},
): AnalyzeMVDialogResponse {
   return {
      completed: true,
      exception: false,
      status: [],
      cycles: [],
      onDemand: false,
      defaultCycle: "daily",
      runInBackground: false,
      dateFormat: "yyyy-MM-dd",
      analysisId: "analysis-1",
      ...overrides,
   };
}

export function makeAnalyzeMVStatus(
   overrides: Partial<AnalyzeMVDialogResponseStatus> = {},
): AnalyzeMVDialogResponseStatus {
   return {
      name: "mv_one",
      lastModifiedTimestamp: 0,
      ...overrides,
   };
}

export function makeAnalyzeMVSelectedNode(
   overrides: Partial<AnalyzeMVDialogSelectedNode> = {},
): AnalyzeMVDialogSelectedNode {
   return {
      identifier: "folder/asset",
      path: "/folder/asset",
      type: 1,
      ...overrides,
   };
}

export function makeAnalyzeMVModel(
   overrides: Partial<AnalyzeMVDialogModel> = {},
): AnalyzeMVDialogModel {
   return {
      name: "mv_one",
      hasData: true,
      exists: true,
      lastModifiedTimestamp: 0,
      ...overrides,
   };
}
