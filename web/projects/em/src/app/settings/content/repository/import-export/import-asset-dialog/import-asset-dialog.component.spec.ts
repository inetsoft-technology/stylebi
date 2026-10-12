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

import { UntypedFormBuilder } from "@angular/forms";
import { of } from "rxjs";
import { MessageDialogType } from "../../../../../common/util/message-dialog";
import { ImportAssetResponse } from "../../model/import-asset-response";
import { ImportAssetDialogComponent } from "./import-asset-dialog.component";

/**
 * Bug #77628: warnings about imported assets (e.g. secrets that could not be decrypted) are shown
 * as a warning, never as "Failed to import".
 */
describe("ImportAssetDialogComponent import result", () => {
   let dialog: { open: ReturnType<typeof vi.fn> };
   let component: ImportAssetDialogComponent;

   beforeEach(() => {
      dialog = { open: vi.fn(() => ({ afterClosed: () => of(null) })) };
      const http: any = { get: vi.fn(() => of([])) };
      const dialogRef: any = { close: vi.fn() };
      component = new ImportAssetDialogComponent(
         http, dialog as any, dialogRef, {}, new UntypedFormBuilder());
   });

   function complete(response: Partial<ImportAssetResponse>): any {
      (component as any).onImportComplete({
         failedAssets: [], ignoreUserAssets: [], complete: true, failed: false, ...response
      });
      return dialog.open.mock.calls[0][1].data;
   }

   it("shows warnings of a successful import as a warning", () => {
      const data = complete({ warnings: ["secrets could not be decrypted: ds1"] });

      expect(data.type).toBe(MessageDialogType.WARNING);
      expect(data.content).toContain("secrets could not be decrypted: ds1");
      expect(data.content).not.toContain("em.import.fail");
   });

   it("shows a successful import without warnings as success", () => {
      const data = complete({});

      expect(data.type).toBe(MessageDialogType.INFO);
   });

   it("keeps failed assets and adds the warnings", () => {
      const data = complete({ failedAssets: ["vs1"], failed: true, warnings: ["w1"] });

      expect(data.type).toBe(MessageDialogType.WARNING);
      expect(data.content).toContain("vs1");
      expect(data.content).toContain("w1");
   });
});
