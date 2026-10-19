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
import { CreateOrganizationDialogComponent } from "./create-organization-dialog.component";

/**
 * Bug #78722: Escape that closes the open Clone From list must not also cancel Create Organization.
 */
describe("CreateOrganizationDialogComponent Escape", () => {
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

   // opened as UsersSettingsViewComponent opens it
   const openDialog = async () => {
      const ref = dialog.open(CreateOrganizationDialogComponent, {
         disableClose: true, data: { name: "provider" }
      });
      await settle();
      return ref;
   };

   it("closes only the open list when Escape is pressed in it", async () => {
      const state = trackDialog(await openDialog());
      const select = document.querySelector("mat-select") as HTMLElement;
      select.focus();
      (select.querySelector(".mat-mdc-select-trigger") as HTMLElement).click();
      await settle();
      expect(document.querySelector(".mat-mdc-select-panel")).not.toBeNull();

      await pressEscape();

      expect(document.querySelector(".mat-mdc-select-panel")).toBeNull();
      expect(state.closed).toBe(false);

      await pressEscape();
      expect(state).toEqual({ closed: true, result: { newOrgString: null, proceed: false } });
   });
});
