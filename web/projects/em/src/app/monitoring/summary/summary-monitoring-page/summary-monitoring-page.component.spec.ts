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
import { SummaryMonitoringPageComponent } from "./summary-monitoring-page.component";
import { ServerModel } from "../summary-monitoring-view/server-model";

describe("SummaryMonitoringPageComponent", () => {
   let component: SummaryMonitoringPageComponent;
   let monitoringDataService: { cluster: string };

   beforeEach(() => {
      monitoringDataService = { cluster: "" };

      component = new SummaryMonitoringPageComponent(
         null, null, null, monitoringDataService as any, null, null, null, null, null, null);

      component.serverModel = <ServerModel> {
         serverUpTimeMap: {},
         serverDateTimeMap: {
            "node1": "10:00:00 AM",
            "node2": "3:00:00 PM",
            "local": "12:00:00 PM"
         },
         schedulerUpTimeMap: {},
         timestamp: Date.now(),
         isCloud: false,
         externalStoragePath: ""
      };
   });

   it("should read the local server time when clustering is disabled", () => {
      component.clusterNodes = [];

      expect(component.serverTime).toBe("12:00:00 PM");
   });

   it("should read the selected node's server time when clustering is enabled", () => {
      component.clusterNodes = ["node1", "node2"];

      component.selectedClusterNode = "node1";
      expect(component.serverTime).toBe("10:00:00 AM");

      component.selectedClusterNode = "node2";
      expect(component.serverTime).toBe("3:00:00 PM");
   });
});
