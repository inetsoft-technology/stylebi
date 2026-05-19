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
import { Component } from "@angular/core";
import { ComponentFixture, fakeAsync, TestBed, tick } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { DropDownTestModule } from "../../common/test/test-module";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { CustomSelectComponent, CustomSelectOption } from "./custom-select.component";

@Component({
   template: `<custom-select [options]="options" [(ngModel)]="value"></custom-select>`
})
class TestHostComponent {
   value = "one";
   options: CustomSelectOption<string>[] = [
      { label: "One", value: "one" },
      { label: "Two", value: "two" }
   ];
}

describe("CustomSelectComponent", () => {
   let fixture: ComponentFixture<TestHostComponent>;

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [FormsModule, DropDownTestModule],
         declarations: [TestHostComponent, CustomSelectComponent, FixedDropdownDirective],
         providers: [FixedDropdownService]
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      fixture.detectChanges();
   });

   it("updates the value when an option is clicked", fakeAsync(() => {
      const trigger = fixture.debugElement.query(By.css(".custom-select-trigger")).nativeElement;

      trigger.click();
      fixture.detectChanges();
      tick();
      fixture.detectChanges();

      const options = document.querySelectorAll<HTMLButtonElement>(".custom-select-option");
      expect(options.length).toBe(2);

      options.item(1).click();
      fixture.detectChanges();
      tick();
      fixture.detectChanges();

      expect(fixture.componentInstance.value).toBe("two");
   }));
});
