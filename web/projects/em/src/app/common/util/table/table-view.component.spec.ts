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

import { CommonModule } from "@angular/common";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatTableModule } from "@angular/material/table";
import { MatTooltipModule } from "@angular/material/tooltip";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { TableInfo } from "./table-info";
import { TableView } from "./table-view.component";

const tableInfo: TableInfo = {
   columns: [],
   selectionEnabled: false,
   title: ""
};

@Component({
   standalone: true,
   imports: [TableView],
   template: `<em-table-view [collapsible]="true" [dataSource]="data" [tableInfo]="info"></em-table-view>`
})
class TestHostComponent {
   data: any[] = [];
   info: TableInfo = { columns: [], selectionEnabled: false, title: "My Title" };
}

describe("TableViewComponent", () => {
   let component: TableView<any>;
   let fixture: ComponentFixture<TableView<any>>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            CommonModule,
            MatTableModule,
            MatTooltipModule,
            MatCheckboxModule,
            MatExpansionModule,
            MatFormFieldModule,
            TableView],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TableView);
      component = fixture.componentInstance;
      component.dataSource = [];
      component.tableInfo = tableInfo;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // Bug #75349: when a collapsible table has a title, the panel header (which
   // carries the rollup toggle) must be projected into the expansion panel's
   // always-visible header slot, NOT into the collapsible body. If it lands in
   // the body, clicking the toggle collapses the header along with the content
   // and the whole element disappears.
   it("should project the panel header outside the collapsible content (bug #75349)", () => {
      const hostFixture = TestBed.createComponent(TestHostComponent);
      hostFixture.detectChanges();

      const el: HTMLElement = hostFixture.nativeElement;
      const header = el.querySelector("mat-expansion-panel-header");
      const content = el.querySelector(".mat-expansion-panel-content");

      expect(header).toBeTruthy();
      // The header must not be nested inside the collapsible content region.
      expect(content?.contains(header)).toBeFalsy();
   });
});
