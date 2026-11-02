/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { NEVER } from "rxjs";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { pressEscape, settle, trackDialog } from "../../../../testing/escape-press";
import { MaterializeSheetDialogComponent } from "./materialize-sheet-dialog.component";

describe("MaterializeSheetDialogComponent", () => {
   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [
            MaterializeSheetDialogComponent,
            NoopAnimationsModule,
            MatDialogModule,
            HttpClientTestingModule,
         ],
         providers: [
            { provide: MatDialogRef, useValue: { close: vi.fn(), keydownEvents: () => NEVER } },
            { provide: MAT_DIALOG_DATA, useValue: [] },
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   });

   it("should create without NG0201 injection error for ErrorHandlerService", () => {
      const fixture = TestBed.createComponent(MaterializeSheetDialogComponent);
      fixture.detectChanges();
      expect(fixture.componentInstance).toBeTruthy();
   });
});

/**
 * Bug #78722: Escape that closes the open Full Data list must not also close Materialize.
 */
describe("MaterializeSheetDialogComponent Escape", () => {
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

   // opened as ContentRepositoryPageComponent opens it; a viewsheet shows the Full Data list
   const openDialog = async () => {
      const ref = dialog.open(MaterializeSheetDialogComponent, {
         disableClose: true,
         data: [{ type: RepositoryEntryType.VIEWSHEET, path: "vs1", label: "vs1", owner: null }]
      });
      await settle(10);
      return ref;
   };

   it("closes only the open Full Data list when Escape is pressed in it", async () => {
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
      expect(state).toEqual({ closed: true, result: null });
   });
});
