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

import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatTableModule } from "@angular/material/table";
import { LoggingLevelTableComponent } from "./logging-level-table.component";
import { TableView } from "../../../common/util/table/table-view.component";
import { LogLevelDTO } from "../LogLevelDTO";

describe("LoggingLevelTableComponent", () => {
   let fixture: ComponentFixture<LoggingLevelTableComponent>;
   let component: LoggingLevelTableComponent;

   const hostOrgLevel: LogLevelDTO = {
      context: "DASHBOARD", name: "dashboard1", orgName: "Host Organization", level: "info"
   };
   const selfOrgLevel: LogLevelDTO = {
      context: "DASHBOARD", name: "dashboard1", orgName: "Self Organization", level: "debug"
   };

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            LoggingLevelTableComponent,
            TableView,
            NoopAnimationsModule,
            MatTableModule,
            MatCheckboxModule,
            MatCardModule,
            MatButtonModule
         ]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(LoggingLevelTableComponent);
      component = fixture.componentInstance;
      component.enterprise = true;
      component.isMultiTenant = true;
      component.loggingLevels = [hostOrgLevel, selfOrgLevel];
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // Bug #76499: two rows can share the same `name` while differing in orgName/level (the
   // backend identity is context+name+orgName, per LogLevelSetting.equals()/hashCode()).
   // Checking one such row must not select its sibling.
   //
   // Drives TableView.ngOnChanges()/refreshDataSource() directly (rather than through
   // fixture.detectChanges(), which fights MatTableDataSource's own async internals in this
   // harness) since that is the exact code path the diagnosis identified as the bug site.
   it("does not cross-select rows that share name but differ in orgName (bug #76499)", () => {
      const tableView = fixture.debugElement.query(By.directive(TableView))
         .componentInstance as TableView<LogLevelDTO>;

      const hostRow = component.keyedLoggingLevels
         .find((row) => row.orgName === "Host Organization");
      tableView.selection.select(hostRow as any);

      // Simulate logging-settings-view.component.html's
      // `[loggingLevels]="getLevels(model.logLevels)"`, which re-supplies a fresh array
      // reference (same underlying row objects) on every change-detection cycle and
      // re-triggers TableView.ngOnChanges -> refreshDataSource().
      const previousDataSource = tableView.dataSource;
      const freshDataSource = [...component.keyedLoggingLevels];
      tableView.dataSource = freshDataSource as any;
      tableView.ngOnChanges({
         dataSource: {
            previousValue: previousDataSource,
            currentValue: freshDataSource,
            firstChange: false,
            isFirstChange: () => false
         }
      } as any);

      expect(tableView.selection.selected.length).toBe(1);
      expect((tableView.selection.selected[0] as unknown as LogLevelDTO).orgName)
         .toBe("Host Organization");
   });
});
