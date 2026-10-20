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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import {
   TableCellResizeDialogComponent,
   TableCellResizeDialogResult
} from "./table-cell-resize-dialog.component";

describe("TableCellResizeDialogComponent follow the default density", () => {
   let fixture: ComponentFixture<TableCellResizeDialogComponent>;
   let dialog: TableCellResizeDialogComponent;

   const open = (followsDensity: boolean | null) => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, TableCellResizeDialogComponent],
         schemas: [NO_ERRORS_SCHEMA]
      });
      fixture = TestBed.createComponent(TableCellResizeDialogComponent);
      dialog = fixture.componentInstance;
      dialog.width = 96;
      dialog.height = 16;
      dialog.followsDensity = followsDensity;
      fixture.detectChanges();
   };

   const commit = (): TableCellResizeDialogResult => {
      let result: TableCellResizeDialogResult;
      dialog.onCommit.subscribe((r) => result = r);
      dialog.ok();
      return result;
   };

   const box = (): HTMLInputElement =>
      fixture.nativeElement.querySelector("#cellHeightFollowsDensity");

   it("hides the box when followsDensity is null", () => {
      open(null);

      expect(box()).toBeNull();
      expect(dialog.form.get("cellHeight").enabled).toBe(true);
      expect(commit().followsDensity).toBeUndefined();
   });

   it("starts ticked with the height greyed and reports true", () => {
      open(true);

      expect(box().checked).toBe(true);
      expect(dialog.form.get("cellHeight").disabled).toBe(true);
      expect(dialog.form.get("cellHeight").value).toBe(16);
      expect(commit()).toEqual({ width: 96, height: 16, followsDensity: true });
   });

   it("unticking makes the shown height editable and reports false", () => {
      open(true);

      dialog.form.get("followsDensity").setValue(false);
      fixture.detectChanges();

      expect(dialog.form.get("cellHeight").enabled).toBe(true);
      expect(dialog.form.get("cellHeight").value).toBe(16);
      expect(commit()).toEqual({ width: 96, height: 16, followsDensity: false });
   });

   it("starts unticked with the height editable", () => {
      open(false);

      expect(box().checked).toBe(false);
      expect(dialog.form.get("cellHeight").enabled).toBe(true);
   });
});
