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
import { TestBed, ComponentFixture, async } from "@angular/core/testing";
import { TestUtils } from "../../common/test/test-utils";
import { Tool } from "../../../../../shared/util/tool";
import { NgModule } from "@angular/core";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { NgModel, FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { AlphaDropdown } from "./alpha-dropdown.component";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { DropDownTestModule } from "../../common/test/test-module";
import { DebounceService } from "../services/debounce.service";

describe("alpha dropdown component unit case", () => {
   let fixture: ComponentFixture<AlphaDropdown>;
   let alphaDropdown: AlphaDropdown;
   let debounceService: any;

   beforeEach(() => {
      debounceService = { debounce: jest.fn() };
      TestBed.configureTestingModule({
         imports: [DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [AlphaDropdown, FixedDropdownDirective],
         providers: [
            NgbModal,
            {
               provide: DebounceService,
               useValue: debounceService
            }
         ]
      }).compileComponents();
      fixture = TestBed.createComponent(AlphaDropdown);
      alphaDropdown = <AlphaDropdown>fixture.componentInstance;
   });

   //Bug #19399 should keep alpha value
   it("apply alpha value to legend", () => {
      alphaDropdown.changeAlpha0(40);
      fixture.detectChanges();
      alphaDropdown.changeAlpha0(50);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector("input").getAttribute("ng-reflect-model")).toBe("50");
   });
});
