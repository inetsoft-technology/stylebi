/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TreeComponent } from "./tree.component";
import { TestUtils } from "../../common/test/test-utils";

describe("Tree Unit Case", () => {
   let fixture: ComponentFixture<TreeComponent>;
   let treeComponent: TreeComponent;
   let input: HTMLInputElement;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations:  [TreeComponent],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(TreeComponent);
      treeComponent = <TreeComponent>fixture.componentInstance;
      fixture.detectChanges();
   });

   //Bug #17221 search field can not input string
   it("Search field can be input string", () => {
      treeComponent.searchEnabled = true;
      fixture.detectChanges();

      input = fixture.debugElement.query(By.css("div.search-box input")).nativeElement;
      input.value = "chart";
      input.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      expect(input.getAttribute("ng-reflect-model")).toBe("chart");
   });

   //Bug #17336 should show infomation when no result seached.
   it("should show infor when no result found", () => {
      treeComponent.empty = true;
      treeComponent.searchStr = "aaa";
      fixture.detectChanges();

      let info: HTMLElement = fixture.debugElement.query(By.css("div:not(.class)")).nativeElement;
      expect(TestUtils.toString(info.textContent.trim())).toBe("common.searchFailed");
   });
});