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
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
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
            { provide: MatDialogRef, useValue: { close: vi.fn() } },
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
