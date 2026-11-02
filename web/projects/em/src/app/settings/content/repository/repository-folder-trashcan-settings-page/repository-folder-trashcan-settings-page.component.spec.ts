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

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of } from "rxjs";
import { RepositoryFolderTrashcanTableModel } from "./repository-folder-trashcan-table-model";
import {
   RepositoryFolderTrashcanSettingsPageComponent,
   RepositoryTrashcanFolderEditorModel
} from "./repository-folder-trashcan-settings-page.component";

const DELETE_URI = "../api/em/content/repository/folder/trashcan/delete";
const RESTORE_URI = "../api/em/content/repository/folder/trashcan/restore";

const report = (name: string): RepositoryFolderTrashcanTableModel =>
   ({ name, version: "1", type: "Viewsheet" });

const reports = (...names: string[]): RepositoryFolderTrashcanTableModel[] => names.map(report);

const editorModel = (rows: RepositoryFolderTrashcanTableModel[]): RepositoryTrashcanFolderEditorModel =>
   ({ reports: rows } as RepositoryTrashcanFolderEditorModel);

describe("RepositoryFolderTrashcanSettingsPageComponent", () => {
   let fixture: ComponentFixture<RepositoryFolderTrashcanSettingsPageComponent>;
   let httpMock: HttpTestingController;
   let element: HTMLElement;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [RepositoryFolderTrashcanSettingsPageComponent, HttpClientTestingModule,
            NoopAnimationsModule],
         providers: [
            { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(true) }) } },
            { provide: MatSnackBar, useValue: { open: () => null } }
         ]
      });

      fixture = TestBed.createComponent(RepositoryFolderTrashcanSettingsPageComponent);
      httpMock = TestBed.inject(HttpTestingController);
      element = fixture.nativeElement;
      fixture.componentRef.setInput("model", editorModel(reports("a", "b", "c", "d")));

      // the content repository page reloads the editor after editorChanged: the same page
      // component gets a new model with new row objects for the remaining reports
      fixture.componentInstance.editorChanged.subscribe(() =>
         fixture.componentRef.setInput("model", editorModel(reports("b", "c", "d"))));
   });

   afterEach(() => httpMock.verify());

   const settle = async (ms = 0) => {
      for(let i = 0; i < 3; i++) {
         fixture.detectChanges();
         await new Promise((resolve) => setTimeout(resolve, ms));
      }

      fixture.detectChanges();
   };

   // the editor panel's apply (Restore) button is debounced by 200 ms
   const pastApplyDebounce = () => settle(100);

   const rows = (): HTMLElement[] => Array.from(element.querySelectorAll("tr.mat-mdc-row"));

   const checkbox = (row: HTMLElement): HTMLInputElement => row.querySelector("input[type=checkbox]");

   const rowName = (row: HTMLElement): string => row.querySelectorAll("td")[1].textContent.trim();

   const checkedNames = (): string[] => rows().filter((row) => checkbox(row).checked).map(rowName);

   const button = (label: string): HTMLButtonElement =>
      Array.from(element.querySelectorAll("button"))
         .find((b) => b.textContent.trim() === label) as HTMLButtonElement;

   const pick = async (name: string) => {
      checkbox(rows().find((row) => rowName(row) === name)).click();
      await settle();
   };

   // Bug #78721
   it("restores only the newly checked report after a delete reloads the reports", async () => {
      await settle();
      await pick("a");
      button("_#(Remove)").click();
      await settle();
      const remove = httpMock.expectOne(DELETE_URI);
      expect(remove.request.body).toEqual({ table: reports("a") });
      remove.flush(null);
      await settle();

      expect(rows().map(rowName)).toEqual(["b", "c", "d"]);
      expect(checkedNames()).toEqual([]);
      expect(button("_#(Restore)").disabled).toBe(true);
      expect(button("_#(Remove)").disabled).toBe(true);

      await pick("c");
      expect(button("_#(Restore)").disabled).toBe(false);
      button("_#(Restore)").click();
      await pastApplyDebounce();
      const restore = httpMock.expectOne(RESTORE_URI);
      expect(restore.request.body).toEqual({ table: reports("c") });
      restore.flush(null);
      await settle();
   });

   // Bug #78721
   it("deletes only the newly checked report after a restore reloads the reports", async () => {
      await settle();
      await pick("a");
      button("_#(Restore)").click();
      await pastApplyDebounce();
      const restore = httpMock.expectOne(RESTORE_URI);
      expect(restore.request.body).toEqual({ table: reports("a") });
      restore.flush(null);
      await settle();

      expect(rows().map(rowName)).toEqual(["b", "c", "d"]);
      expect(checkedNames()).toEqual([]);
      expect(button("_#(Remove)").disabled).toBe(true);

      await pick("d");
      expect(button("_#(Restore)").disabled).toBe(false);
      button("_#(Remove)").click();
      await settle();
      const remove = httpMock.expectOne(DELETE_URI);
      expect(remove.request.body).toEqual({ table: reports("d") });
      remove.flush(null);
      await settle();
   });
});
