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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ExpandableRowTableInfo } from "../../../common/util/table/expandable-row-table/expandable-row-table-info";
import { TableViewModule } from "../../../common/util/table/table-view.module";
import { ViewsheetMonitoringViewComponent } from "./viewsheet-monitoring-view.component";

const mockTableInfo: ExpandableRowTableInfo = {
   columns: [],
   mediumDeviceHeaders: [],
   selectionEnabled: true,
   title: ""
};

describe("ViewsheetMonitoringViewComponent", () => {
   let component: ViewsheetMonitoringViewComponent;
   let fixture: ComponentFixture<ViewsheetMonitoringViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            CommonModule,
            MatTableModule,
            TableViewModule,
         ],
         declarations: [ViewsheetMonitoringViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
      fixture = TestBed.createComponent(ViewsheetMonitoringViewComponent);
      component = fixture.componentInstance;
      fixture.componentInstance.dataSource = [];
      fixture.componentInstance.monitoringTableInfo = mockTableInfo;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
