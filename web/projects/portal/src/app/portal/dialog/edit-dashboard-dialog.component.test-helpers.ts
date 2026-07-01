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
import { ChangeDetectorRef } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";

import { DashboardModel } from "../../common/data/dashboard-model";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";
import { EditDashboardDialog } from "./edit-dashboard-dialog.component";

type EditDashboardDialogTreeStub = {
   selectAndExpandToPath: ReturnType<typeof vi.fn>;
};

type EditDashboardDialogRepositoryTreeServiceStub = {
   getRootFolder: ReturnType<typeof vi.fn>;
};

type EditDashboardDialogChangeDetectorStub = {
   detectChanges: ReturnType<typeof vi.fn>;
};

type EditDashboardDialogModalStub = {
   open: ReturnType<typeof vi.fn>;
};

type EditDashboardDialogRootNode = {
   label: string;
   leaf: boolean;
   data: {
      name: string;
      path: string;
      type: number;
      label: string;
   };
   children: unknown[];
};

type EditDashboardDialogNodeData = {
   type: number;
   path: string;
   entry?: {
      identifier: string;
   };
};

type EditDashboardDialogNode = {
   data: EditDashboardDialogNodeData;
};

export function createEditDashboardDialogRootNode(): EditDashboardDialogRootNode {
   return {
      label: "Repository",
      leaf: false,
      data: {
         name: "",
         path: "/",
         type: 1,
         label: "",
      },
      children: [],
   };
}

export function makeDashboardModel(overrides: Partial<DashboardModel> = {}): DashboardModel {
   return {
      name: "Construction__GLOBAL",
      label: "Construction",
      type: "g",
      description: "test",
      path: "Examples/Construction Dashboard",
      identifier: "1^128^__NULL__^Examples/Construction Dashboard",
      enabled: false,
      ...overrides,
   };
}

export function makeEditDashboardDialogNode(
   overrides: Partial<EditDashboardDialogNodeData> = {},
): EditDashboardDialogNode {
   return {
      data: {
         type: 1,
         path: "Examples/Construction Dashboard",
         entry: {
            identifier: "1^128^__NULL__^Examples/Construction Dashboard",
         },
         ...overrides,
      },
   };
}

export function createEditDashboardDialog(options: {
   rootNode?: EditDashboardDialogRootNode;
} = {}) {
   const rootNode = options.rootNode ?? createEditDashboardDialogRootNode();
   const repositoryTreeService: EditDashboardDialogRepositoryTreeServiceStub = {
      getRootFolder: vi.fn().mockReturnValue(of(rootNode)),
   };
   const modalService: EditDashboardDialogModalStub = {
      open: vi.fn(),
   };
   const changeDetector: EditDashboardDialogChangeDetectorStub = {
      detectChanges: vi.fn(),
   };

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         { provide: RepositoryTreeService, useValue: repositoryTreeService },
         { provide: NgbModal, useValue: modalService },
         { provide: ChangeDetectorRef, useValue: changeDetector },
      ],
   });

   const comp = new EditDashboardDialog(
      TestBed.inject(RepositoryTreeService),
      TestBed.inject(HttpClient),
      TestBed.inject(NgbModal),
      TestBed.inject(ChangeDetectorRef),
   );

   return {
      comp,
      repositoryTreeService,
      modalService,
      changeDetector,
   };
}

export function attachEditDashboardTree(comp: EditDashboardDialog): EditDashboardDialogTreeStub {
   const tree = {
      selectAndExpandToPath: vi.fn(),
   };

   // Tests inject the minimal @ViewChild surface directly because ngOnInit calls the tree helper.
   (comp as unknown as { tree: EditDashboardDialogTreeStub }).tree = tree;
   return tree;
}
