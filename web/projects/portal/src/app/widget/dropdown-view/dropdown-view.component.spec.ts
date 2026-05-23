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
import { Component, ViewChild } from "@angular/core";
import { ComponentFixture, fakeAsync, TestBed, tick } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { DropDownTestModule } from "../../common/test/test-module";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { DropdownView } from "./dropdown-view.component";

@Component({
   template: `
      <dropdown-view #dropdown label="Alignment">
         <button class="apply-button" type="button" (click)="dropdown.close()">Apply</button>
      </dropdown-view>
   `
})
class TestHostComponent {
   @ViewChild("dropdown") dropdown: DropdownView;
}

describe("DropdownView", () => {
   let fixture: ComponentFixture<TestHostComponent>;

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [DropDownTestModule],
         declarations: [TestHostComponent, DropdownView, FixedDropdownDirective],
         providers: [FixedDropdownService]
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      fixture.detectChanges();
   });

   it("restores focus to the trigger after an explicit close", fakeAsync(() => {
      const trigger = fixture.debugElement.query(By.css(".dropdown-button")).nativeElement as HTMLButtonElement;

      trigger.click();
      fixture.detectChanges();
      tick();
      fixture.detectChanges();

      const applyButton = document.querySelector(".apply-button") as HTMLButtonElement;
      expect(applyButton).toBeTruthy();

      applyButton.click();
      fixture.detectChanges();
      tick();
      fixture.detectChanges();

      expect(document.activeElement).toBe(trigger);
   }));
});
