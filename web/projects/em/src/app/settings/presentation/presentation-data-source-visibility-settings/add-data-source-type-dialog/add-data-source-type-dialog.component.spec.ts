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
import { pressEscape, settle, trackDialog } from "../../../../testing/escape-press";
import { AddDataSourceTypeDialogComponent } from "./add-data-source-type-dialog.component";

/**
 * Bug #78722: Escape that closes the open Data Source Type list must not also cancel the dialog.
 */
describe("AddDataSourceTypeDialogComponent Escape", () => {
   let dialog: MatDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({ imports: [NoopAnimationsModule, HttpClientTestingModule] });
      dialog = TestBed.inject(MatDialog);
   });

   afterEach(async () => {
      dialog.closeAll();
      await settle();
   });

   // opened as PresentationDataSourceVisibilitySettingsViewComponent opens it
   const openDialog = async () => {
      const ref = dialog.open(AddDataSourceTypeDialogComponent, {
         disableClose: true,
         data: { title: "t", listings: [{ key: "jdbc", value: "JDBC" }, { key: "rest", value: "REST" }], currTypes: [] }
      });
      await settle();
      return ref;
   };

   it("closes only the open list when Escape is pressed in it", async () => {
      const ref = await openDialog();
      const state = trackDialog(ref);
      const select = document.querySelector("mat-select") as HTMLElement;
      select.focus();
      (select.querySelector(".mat-mdc-select-trigger") as HTMLElement).click();
      await settle();
      expect(document.querySelector(".mat-mdc-select-panel")).not.toBeNull();

      await pressEscape();

      expect(document.querySelector(".mat-mdc-select-panel")).toBeNull();
      expect(state.closed).toBe(false);

      await pressEscape();
      expect(state).toEqual({ closed: true, result: null });
   });

   it("cancels on Escape when nothing is open in it", async () => {
      const state = trackDialog(await openDialog());

      await pressEscape();

      expect(state).toEqual({ closed: true, result: null });
   });
});
