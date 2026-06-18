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
 * Shared test helpers for data-auto-drill-dialog P1/P2 spec files.
 *
 * Mocking strategy:
 *   - NgbModal is provided as a vi.fn() object; individual dialog methods
 *     (showConfirmDialog, showMessageDialog, showDialog) are spied on per-test
 *     via vi.spyOn(ComponentTool, ...) rather than threading through modalService.result.
 *   - RepositoryTreeComponent and ModalHeaderComponent are stubbed via importOverrides:
 *     RepositoryTreeComponent has complex DI (RepositoryBaseComponent chain) and
 *     ModalHeaderComponent injects AiAssistantDialogService.
 *   - HttpClient uses provideHttpClient() + MSW (three endpoints registered in
 *     portal.handlers.ts: /tree, /autoDrill-parameters, /worksheet/fields).
 *   - Default makeDrillPath() uses WEB_LINK (linkType=1) so ngOnInit and selectDrill
 *     do not trigger /autoDrill-parameters or tree-expand HTTP calls. Override with
 *     linkType=8 for VIEWSHEET_LINK-specific tests.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, Subject } from "rxjs";

import { AutoDrillDialog } from "./data-auto-drill-dialog.component";
import { ModalHeaderComponent } from "../../../../../../../../widget/modal-header/modal-header.component";
import { RepositoryTreeComponent } from "../../../../../../../../widget/repository-tree/repository-tree.component";
import { AutoDrillInfoModel } from "../../../../../../model/datasources/database/physical-model/logical-model/auto-drill-info-model";
import { AutoDrillPathModel } from "../../../../../../model/datasources/database/physical-model/logical-model/auto-drill-path-model";
import { TreeNodeModel } from "../../../../../../../../widget/tree/tree-node-model";

// ---------------------------------------------------------------------------
// Stub components — replace imports that have deep/broken DI in jsdom
// ---------------------------------------------------------------------------

@Component({ selector: "modal-header", template: "" })
export class ModalHeaderStub {
   @Input() title: string;
   @Input() cshid: string;
   @Output() onCancel = new EventEmitter<void>();
}

@Component({ selector: "repository-tree", template: "" })
export class RepositoryTreeStub {
   selectedNode: TreeNodeModel = null;
   selectAndExpandToPath = vi.fn();
   getParentNode = vi.fn().mockReturnValue(null);
}

// ---------------------------------------------------------------------------
// NgbModal mock — fresh Subject per open() so ComponentTool subscriptions
// never see stale emissions from a previous test's modal instance.
// ---------------------------------------------------------------------------

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: EMPTY,
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// ---------------------------------------------------------------------------
// Model factories
// ---------------------------------------------------------------------------

export function makeDrillPath(overrides: Partial<AutoDrillPathModel> = {}): AutoDrillPathModel {
   return {
      name: "Test Drill",
      link: "https://example.com",
      targetFrame: "",
      tip: "",
      params: [],
      passParams: true,
      disablePrompting: false,
      linkType: 1, // WEB_LINK — avoids /autoDrill-parameters and tree-expand side-effects
      query: null,
      queryFields: ["this.column"],
      ...overrides,
   };
}

export function makeModel(paths: AutoDrillPathModel[] = []): AutoDrillInfoModel {
   return { paths };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   model?: AutoDrillInfoModel;
   portal?: boolean;
}

export async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(AutoDrillDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: RepositoryTreeComponent, with: RepositoryTreeStub },
      ],
      componentInputs: {
         autoDrillModel: opts.model ?? makeModel(),
         entities: [],
         fields: [],
         portal: opts.portal ?? true,
      },
   });
   return { comp: fixture.componentInstance as AutoDrillDialog, fixture };
}
