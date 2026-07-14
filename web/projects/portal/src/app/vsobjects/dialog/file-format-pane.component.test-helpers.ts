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
 * Shared fixtures for file-format-pane.component.{interaction,display}.tl.spec.ts.
 *
 * Direct instantiation — a single constructor dependency (NgbModal) with no `inject()`
 * calls. The component only ever uses NgbModal indirectly via
 * `ComponentTool.showMessageDialog(this.modalService, ...)`, so tests spy on that static
 * utility function directly rather than deep-mocking NgbModal's own API.
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FileFormatPane } from "./file-format-pane.component";
import { FileFormatPaneModel } from "../model/file-format-pane-model";

export function makeModel(overrides: Partial<FileFormatPaneModel> = {}): FileFormatPaneModel {
   return Object.assign({
      formatType: 0,
      matchLayout: false,
      expandSelections: false,
      includeCurrent: false,
      linkVisible: false,
      sendLink: false,
      selectedBookmarks: [],
      allBookmarks: [],
      allBookmarkLabels: [],
      expandEnabled: false,
      hasPrintLayout: false,
      onlyDataComponents: false,
      tableDataAssemblies: [],
   }, overrides);
}

export interface CreateComponentOpts {
   exportTypes?: { label: string, value: string }[];
   email?: boolean;
   model?: FileFormatPaneModel;
}

export function createComponent(opts: CreateComponentOpts = {}) {
   const modalService = {} as NgbModal;
   const comp = new FileFormatPane(modalService);
   comp.exportTypes = opts.exportTypes ?? [];
   comp.email = opts.email ?? false;
   comp.model = opts.model ?? makeModel();
   return { comp, modalService };
}
