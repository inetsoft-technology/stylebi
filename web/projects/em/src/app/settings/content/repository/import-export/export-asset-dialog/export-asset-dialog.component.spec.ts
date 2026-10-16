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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { NEVER } from "rxjs";
import { StompClientService } from "../../../../../../../../shared/stomp/stomp-client.service";
import { pressEscape, settle, trackDialog } from "../../../../../testing/escape-press";
import { SelectAssetsDialogComponent } from "../select-assets-dialog/select-assets-dialog.component";
import { ExportAssetDialogComponent } from "./export-asset-dialog.component";

/**
 * Bug #78722: Escape that closes Select Assets must not also cancel Export Assets behind it.
 */
describe("ExportAssetDialogComponent Escape", () => {
   let dialog: MatDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [NoopAnimationsModule, HttpClientTestingModule],
         providers: [{ provide: StompClientService, useValue: { connect: () => NEVER } }]
      });
      dialog = TestBed.inject(MatDialog);
   });

   afterEach(async () => {
      dialog.closeAll();
      await settle();
   });

   // opened as ContentRepositoryPageComponent opens it
   const openExport = async () => {
      const ref = dialog.open(ExportAssetDialogComponent, {
         disableClose: true, data: { selectedNodes: [] }
      });
      await settle();
      return ref;
   };

   it("closes only Select Assets when Escape is pressed in it", async () => {
      const exportRef = await openExport();
      const exportState = trackDialog(exportRef);
      exportRef.componentInstance.selectAssets();
      await settle();
      const selectRef = dialog.openDialogs[1];
      expect(selectRef.componentInstance).toBeInstanceOf(SelectAssetsDialogComponent);
      const selectState = trackDialog(selectRef);

      await pressEscape();

      expect(selectState.closed).toBe(true);
      expect(exportState.closed).toBe(false);
      expect(dialog.openDialogs).toEqual([exportRef]);

      // the next press, with Export on top, cancels it
      await pressEscape();
      expect(exportState).toEqual({ closed: true, result: false });
   });

   it("cancels on Escape when it is the only dialog, but not on Shift+Escape", async () => {
      const state = trackDialog(await openExport());

      await pressEscape({ shiftKey: true });
      expect(state.closed).toBe(false);

      await pressEscape();
      expect(state).toEqual({ closed: true, result: false });
   });
});
