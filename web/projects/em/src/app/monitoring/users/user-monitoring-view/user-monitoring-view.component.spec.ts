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
import { CommonModule } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatDialogModule } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of as observableOf } from "rxjs";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { ExpandableRowTableInfo } from "../../../common/util/table/expandable-row-table/expandable-row-table-info";
import { TableViewModule } from "../../../common/util/table/table-view.module";
import { ClusterSelectorModule } from "../../cluster-selector/cluster-selector.module";
import { ClusterNodesService } from "../../cluster/cluster-nodes.service";
import { CollapsibleContainerModule } from "../../collapsible-container/collapsible-container.module";
import { MonitorLevelService } from "../../monitor-level.service";
import { MonitoringDataService } from "../../monitoring-data.service";
import { UsersRoutingModule } from "../users-routing.module";
import { UserMonitoringViewComponent } from "./user-monitoring-view.component";

const mockTableInfo: ExpandableRowTableInfo = {
   columns: [],
   mediumDeviceHeaders: [],
   selectionEnabled: true,
   title: ""
};

describe("UserMonitoringViewComponent", () => {
   let component: UserMonitoringViewComponent;
   let fixture: ComponentFixture<UserMonitoringViewComponent>;

   beforeEach(() => {
      const mockMonitorLevelService = {
         monitorLevel: jest.fn(() => observableOf(0)),
         filterColumns: jest.fn(() => [])
      };
      const clusterService = {
         getClusterNodes: jest.fn(() => observableOf())
      };
      const monitoringDataService = {
         connect: jest.fn(() => observableOf()),
         subscribe: jest.fn(),
         getClusterAddress: jest.fn(() => observableOf()),
         getMonitoringData: jest.fn(() => observableOf())
      };

      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            CommonModule,
            HttpClientTestingModule,
            MatDialogModule,
            MatIconModule,
            MatSelectModule,
            ClusterSelectorModule,
            CollapsibleContainerModule,
            MessageDialogModule,
            TableViewModule,
            UsersRoutingModule
         ],
         declarations: [
            UserMonitoringViewComponent
         ],
         providers: [
            {provide: MonitoringDataService, useValue: monitoringDataService},
            {provide: MonitorLevelService, useValue: mockMonitorLevelService},
            {provide: ClusterNodesService, useValue: clusterService}
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      }).compileComponents();
   });

   beforeEach(() => {
      fixture = TestBed.createComponent(UserMonitoringViewComponent);
      component = fixture.componentInstance;
      fixture.componentInstance.sessionTableInfo = mockTableInfo;
      fixture.componentInstance.failedLoginTableInfo = mockTableInfo;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
