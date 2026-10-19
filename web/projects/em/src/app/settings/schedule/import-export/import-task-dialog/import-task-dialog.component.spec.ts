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
import { HttpErrorResponse } from "@angular/common/http";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MessageDialog } from "../../../../common/util/message-dialog";
import { pressEscape, settle, trackDialog } from "../../../../testing/escape-press";
import { ImportTaskDialogComponent } from "./import-task-dialog.component";

/**
 * Bug #78722: Escape that closes an error message must not also cancel Import Tasks.
 */
describe("ImportTaskDialogComponent Escape", () => {
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

   // opened as ScheduleTaskListComponent opens it
   const openDialog = async () => {
      const ref = dialog.open(ImportTaskDialogComponent, { disableClose: true, data: {} });
      await settle();
      return ref;
   };

   it("closes only the error message when Escape is pressed in it", async () => {
      const ref = await openDialog();
      const state = trackDialog(ref);
      // the error message shown when an import request fails
      (ref.componentInstance as any).handleImportError(
         new HttpErrorResponse({ status: 500, error: { message: "failed" } })).subscribe({ error: () => {} });
      await settle();
      const messageRef = dialog.openDialogs[1];
      expect(messageRef.componentInstance).toBeInstanceOf(MessageDialog);
      const messageState = trackDialog(messageRef);

      await pressEscape();

      expect(messageState.closed).toBe(true);
      expect(state.closed).toBe(false);

      await pressEscape();
      expect(state).toEqual({ closed: true, result: null });
   });
});
