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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { ModernizeBarComponent } from "./modernize-bar.component";

describe("ModernizeBarComponent", () => {
   let fixture: ComponentFixture<ModernizeBarComponent>;

   beforeEach(async() => {
      await TestBed.configureTestingModule({
         imports: [ModernizeBarComponent]
      }).compileComponents();

      fixture = TestBed.createComponent(ModernizeBarComponent);
   });

   it("renders the message it is given", () => {
      fixture.componentInstance.message = "Classic look";
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain("Classic look");
   });

   it("emits when the action is pressed", () => {
      const spy = vi.fn();
      fixture.componentInstance.onModernize.subscribe(spy);
      fixture.detectChanges();

      fixture.nativeElement.querySelector(".modernize-bar_action").click();

      expect(spy).toHaveBeenCalledTimes(1);
   });

   it("emits when dismissed", () => {
      const spy = vi.fn();
      fixture.componentInstance.onDismiss.subscribe(spy);
      fixture.detectChanges();

      fixture.nativeElement.querySelector(".modernize-bar_dismiss").click();

      expect(spy).toHaveBeenCalledTimes(1);
   });
});
