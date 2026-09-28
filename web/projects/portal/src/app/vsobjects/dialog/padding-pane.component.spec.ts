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
import { UntypedFormGroup } from "@angular/forms";
import { PaddingPane } from "./padding-pane.component";

function createFixture(label?: string): ComponentFixture<PaddingPane> {
   TestBed.configureTestingModule({ imports: [PaddingPane] });
   const fixture = TestBed.createComponent(PaddingPane);
   fixture.componentInstance.model = { top: 0, left: 0, bottom: 0, right: 0 };
   fixture.componentInstance.form = new UntypedFormGroup({});

   if(label !== undefined) {
      fixture.componentInstance.label = label;
   }

   fixture.detectChanges();
   return fixture;
}

function legendText(fixture: ComponentFixture<PaddingPane>): string {
   return fixture.nativeElement.querySelector("legend").textContent.trim();
}

describe("PaddingPane label", () => {
   it("should render the default legend when no label is supplied", () => {
      // the localization macro is not expanded in a unit test, so the raw token is what renders
      expect(legendText(createFixture())).toBe("_#(js:Padding)");
   });

   it("should render the supplied label", () => {
      expect(legendText(createFixture("Cell Padding"))).toBe("Cell Padding");
   });
});
