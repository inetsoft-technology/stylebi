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
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { pressEscape, settle, trackDialog } from "../../../../testing/escape-press";
import { AddFontFaceDialogComponent } from "../add-font-dialog/add-font-face-dialog.component";
import { EditFontsDialogComponent } from "./edit-fonts-dialog.component";

/**
 * Bug #78722: Escape that closes Add Font Face must not also discard the edits in Edit Fonts.
 */
describe("EditFontsDialogComponent Escape", () => {
   let dialog: MatDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [NoopAnimationsModule, HttpClientTestingModule]
      });
      dialog = TestBed.inject(MatDialog);
   });

   afterEach(async () => {
      dialog.closeAll();
      await settle();
   });

   // opened as LookAndFeelSettingsViewComponent opens it
   const openEditFonts = async (): Promise<MatDialogRef<EditFontsDialogComponent>> => {
      const ref = dialog.open(EditFontsDialogComponent, {
         disableClose: true,
         data: { userFonts: ["Roboto", "Lato"], fontFaces: [], deleteFontFaces: [], newFontFaces: [] }
      });
      await settle();
      return ref;
   };

   it("keeps Edit Fonts and its edits when Escape closes Add Font Face", async () => {
      const editRef = await openEditFonts();
      const editState = trackDialog(editRef);
      const editFonts = editRef.componentInstance;
      // an unsaved edit
      (editFonts as any).deleteFont("Lato");
      editFonts.add();
      await settle();
      const addRef = dialog.openDialogs[1];
      expect(addRef.componentInstance).toBeInstanceOf(AddFontFaceDialogComponent);
      const addState = trackDialog(addRef);

      await pressEscape();

      expect(addState.closed).toBe(true);
      expect(editState.closed).toBe(false);
      expect(dialog.openDialogs).toEqual([editRef]);

      editFonts.ok();
      await settle();
      expect(editState.closed).toBe(true);
      expect(editState.result.userFonts).toEqual(["Roboto"]);
   });

   it("closes with null on Escape when it is the only dialog", async () => {
      const state = trackDialog(await openEditFonts());

      await pressEscape();

      expect(state).toEqual({ closed: true, result: null });
   });
});
