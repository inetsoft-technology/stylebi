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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { TestUtils } from "../../../common/test/test-utils";
import { ScriptPropertyDialogComponent } from "./script-property-dialog.component";

describe("ScriptPropertyDialogComponent", () => {
   let fixture: ComponentFixture<ScriptPropertyDialogComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ScriptPropertyDialogComponent, HttpClientTestingModule]
      }).compileComponents();

      fixture = TestBed.createComponent(ScriptPropertyDialogComponent);
      fixture.componentInstance.fontSize = 14;
      fixture.detectChanges();
   });

   const typeFontSize = (text: string) => {
      const input: HTMLInputElement =
         fixture.debugElement.query(By.css("number-stepper input")).nativeElement;
      input.value = text;
      input.dispatchEvent(new Event("input"));
      input.dispatchEvent(new Event("blur"));
      fixture.detectChanges();
   };

   const message = () => fixture.debugElement.query(By.css("span.invalid-feedback"));

   it("shows no font size message for a valid size", () => {
      expect(message()).toBeNull();
   });

   it("shows the font size message for a decimal size", () => {
      typeFontSize("2.5");

      expect(fixture.componentInstance.form.get("fontSize").invalid).toBe(true);
      expect(message()).not.toBeNull();
      expect(TestUtils.toString(message().nativeElement.textContent.trim()))
         .toBe("composer.script.fontSize");
   });

   it("hides the font size message once the size is valid again", () => {
      typeFontSize("2.5");
      typeFontSize("12");

      expect(message()).toBeNull();
   });
});
