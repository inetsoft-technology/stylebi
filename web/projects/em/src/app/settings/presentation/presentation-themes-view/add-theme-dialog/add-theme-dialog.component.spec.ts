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

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { AddThemeDialogComponent } from "./add-theme-dialog.component";

describe("AddThemeDialogComponent", () => {
   let dialogRef: any;

   async function createComponent(data: any): Promise<ComponentFixture<AddThemeDialogComponent>> {
      dialogRef = { close: vi.fn() };

      await TestBed.resetTestingModule().configureTestingModule({
         // the modal header resolves a help URL over HTTP on render; intercept it so the
         // request does not escape the test as an unhandled error
         imports: [NoopAnimationsModule, HttpClientTestingModule, AddThemeDialogComponent],
         providers: [
            { provide: MatDialogRef, useValue: dialogRef },
            { provide: MAT_DIALOG_DATA, useValue: data }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      const fixture = TestBed.createComponent(AddThemeDialogComponent);
      fixture.detectChanges();
      TestBed.inject(HttpTestingController).match(() => true).forEach(r => r.flush(""));
      return fixture;
   }

   // The id is derived from the name, so a free name is used verbatim.
   it("should use the name as the id when it does not collide", async () => {
      const fixture = await createComponent({ ids: ["other"], names: [] });
      fixture.componentInstance.form.get("name").setValue("MyTheme");

      fixture.componentInstance.commit();

      expect(dialogRef.close).toHaveBeenCalledWith(
         expect.objectContaining({ name: "MyTheme", id: "MyTheme" }));
   });

   // "default" is the built-in theme's id and doubles as the sentinel meaning "no custom theme
   // selected", so a theme taking it would be stored and listed but never applied. The name is
   // still the user's; only the id moves.
   it("should not assign the reserved id to a theme named default", async () => {
      const fixture = await createComponent({ ids: [], names: [] });
      fixture.componentInstance.form.get("name").setValue("default");

      fixture.componentInstance.commit();

      expect(dialogRef.close).toHaveBeenCalledWith(
         expect.objectContaining({ name: "default", id: "default1" }));
   });

   // The server also reports the reserved id as unavailable; the check must hold even when it
   // does not, so a stale or failed /themes/ids response cannot reintroduce the collision.
   it("should avoid the reserved id even when the server reports it", async () => {
      const fixture = await createComponent({ ids: ["default"], names: [] });
      fixture.componentInstance.form.get("name").setValue("default");

      fixture.componentInstance.commit();

      const arg = dialogRef.close.mock.calls[0][0];
      expect(arg.id).not.toBe("default");
   });

   // An existing id still gets the counter treatment.
   it("should make a colliding name unique", async () => {
      const fixture = await createComponent({ ids: ["Theme"], names: [] });
      fixture.componentInstance.form.get("name").setValue("Theme");

      fixture.componentInstance.commit();

      expect(dialogRef.close).toHaveBeenCalledWith(
         expect.objectContaining({ name: "Theme", id: "Theme1" }));
   });
});
