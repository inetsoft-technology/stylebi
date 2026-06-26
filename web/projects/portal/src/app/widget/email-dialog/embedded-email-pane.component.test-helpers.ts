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
 * Shared test helpers for embedded-email-pane.component P1/P2/P3 spec files.
 *
 * Mocking strategy:
 *   - ModelService is real (providedIn: 'root'); its HttpClient dep is satisfied
 *     by provideHttpClient() + MSW (expand-identity-node and get-current-user
 *     handlers already registered in portal.handlers.ts).
 *   - NgbModal is provided as MODAL_MOCK because ModelService injects it for
 *     error dialogs — no direct component modal usage in this component.
 *   - ShuffleListComponent, IdentityTreeComponent, EnterClickDirective, and
 *     ScrollableTableDirective are stubbed via importOverrides to avoid deep DI.
 *   - IdentityTreeComponent stub exposes searchMode, tree.selectedNodes, and
 *     nodeExpanded so searchUsers() ViewChild access doesn't throw.
 */

import { Component, Directive, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { provideHttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";

import { EmbeddedEmailPane } from "./embedded-email-pane.component";
import { ShuffleListComponent } from "../shuffle-list/shuffle-list.component";
import { IdentityTreeComponent } from "../identity-tree/identity-tree.component";
import { EnterClickDirective } from "../directive/enter-click.directive";
import { ScrollableTableDirective } from "../scrollable-table/scrollable-table.directive";
import { TreeNodeModel } from "../tree/tree-node-model";
import { IdentityModel } from "../../../../../em/src/app/settings/security/security-table-view/identity-model";
import { IdentityType } from "../../../../../shared/data/identity-type";
import { EmailAddrDialogModel } from "./email-addr-dialog-model";

// ---------------------------------------------------------------------------
// Stub components / directives
// ---------------------------------------------------------------------------

@Component({ selector: "w-shuffle-list", template: "", standalone: true })
export class ShuffleListStub {}

/**
 * IdentityTreeComponent stub must expose searchMode, tree.selectedNodes, and
 * nodeExpanded because searchUsers() assigns searchIdentityTree.searchMode and
 * calls searchIdentityTree.nodeExpanded(), and searchUsers(falsy) clears
 * identityTree.tree.selectedNodes.
 */
@Component({ selector: "identity-tree", template: "", standalone: true })
export class IdentityTreeStub {
   @Input() root: TreeNodeModel;
   @Input() showRoot: boolean;
   searchMode = false;
   tree = { selectedNodes: [] as TreeNodeModel[] };
   nodeExpanded = vi.fn();
}

@Directive({ selector: "[enterClick]", standalone: true })
export class EnterClickStub {}

@Directive({ selector: "table[wScrollableTable]", standalone: true })
export class ScrollableTableStub {}

// ---------------------------------------------------------------------------
// NgbModal mock — fresh Subject per open() call
// ---------------------------------------------------------------------------

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// ---------------------------------------------------------------------------
// Model factories
// ---------------------------------------------------------------------------

export function makeTreeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "TestNode",
      data: "node-data",
      type: String(IdentityType.USER),
      leaf: true,
      expanded: false,
      children: [],
      ...overrides,
   };
}

export function makeIdentity(name: string, type: number, orgID: string = null): IdentityModel {
   return {
      type,
      identityID: { name, orgID },
   };
}

export function makeEmailDialogModel(overrides: Partial<EmailAddrDialogModel> = {}): EmailAddrDialogModel {
   return {
      rootTree: {
         label: "Root",
         data: "",
         type: String(IdentityType.ROOT),
         leaf: false,
         expanded: true,
         children: [],
      },
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   addresses?: string;
   embeddedOnly?: boolean;
   showRoot?: boolean;
   model?: EmailAddrDialogModel;
   emailForm?: UntypedFormGroup;
}

export async function renderEmbeddedEmail(opts: RenderOpts = {}) {
   const { fixture } = await render(EmbeddedEmailPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      importOverrides: [
         { replace: ShuffleListComponent, with: ShuffleListStub },
         { replace: IdentityTreeComponent, with: IdentityTreeStub },
         { replace: EnterClickDirective, with: EnterClickStub },
         { replace: ScrollableTableDirective, with: ScrollableTableStub },
      ],
      componentInputs: {
         addresses: opts.addresses ?? "",
         embeddedOnly: opts.embeddedOnly ?? true,
         showRoot: opts.showRoot ?? true,
         model: opts.model ?? makeEmailDialogModel(),
         emailForm: opts.emailForm ?? new UntypedFormGroup({}),
      },
   });
   return { comp: fixture.componentInstance as EmbeddedEmailPane, fixture };
}
