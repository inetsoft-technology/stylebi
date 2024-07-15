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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatSelectModule } from "@angular/material/select";
import { RouterTestingModule } from "@angular/router/testing";
import { of as observableOf } from "rxjs";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { MonitoringDataService } from "../monitoring-data.service";
import { ClusterSelectorComponent } from "./cluster-selector.component";

describe("ClusterSelectorComponent", () => {
   let component: ClusterSelectorComponent;
   let fixture: ComponentFixture<ClusterSelectorComponent>;

   beforeEach(async(() => {
      const monitoringDataService = {
         refresh: jest.fn()
      };
      const clusterNodesService = {
         getClusterNodes: jest.fn(() => observableOf([]))
      };

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            RouterTestingModule,
            MatSelectModule,
            MatButtonModule
         ],
         declarations: [ClusterSelectorComponent],
         providers: [
            { provide: MonitoringDataService, useValue: monitoringDataService },
            { provide: ClusterNodesService, useValue: clusterNodesService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ClusterSelectorComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
