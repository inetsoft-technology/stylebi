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
import { Component, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { interval, Subscription } from "rxjs";
import { switchMap } from "rxjs/operators";
import { ClusterNodesService } from "../cluster/cluster-nodes.service";
import { MonitoringDataService } from "../monitoring-data.service";

@Component({
   selector: "em-cluster-selector",
   templateUrl: "./cluster-selector.component.html",
   styleUrls: ["./cluster-selector.component.scss"]
})
export class ClusterSelectorComponent implements OnDestroy {
   @Input() refreshEnable = false;
   @Input() clusterNodes: string[];
   @Output() selectedNodeChange: EventEmitter<any> = new EventEmitter<any>();

   private subscriptions = new Subscription();

   constructor(private monitoringDataService: MonitoringDataService,
               private clusterNodesService: ClusterNodesService)
   {
      if(!this.clusterNodes) {
         this.subscriptions.add(this.clusterNodesService.getClusterNodes()
            .subscribe((data: string[]) => {
               this.clusterNodes = data;

               if(this.clusterNodes && !this.selectedNode) {
                  this.selectedNode = this.clusterNodes[0];
               }
            }
         ));

         //Refresh nodes list every 60 seconds
         this.subscriptions.add(
            interval(60000)
               .pipe(switchMap(() => this.clusterNodesService.getClusterNodes()))
               .subscribe((data: string[]) => {
                  this.clusterNodes = data;

                  // fallback if selected node is no longer valid
                  if(!data.includes(this.selectedNode)) {
                     this.selectedNode = data[0];
                  }
               })
         );
      }
   }

   ngOnDestroy(): void {
      if(!!this.subscriptions && !this.subscriptions.closed) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   get selectedNode(): string {
      return this.monitoringDataService.cluster;
   }

   set selectedNode(cluster: string) {
      this.monitoringDataService.cluster = cluster;
   }

   refresh(): void {
      this.monitoringDataService.refresh();
   }

   get clustorEnable(): boolean {
      return this.clusterNodes && this.clusterNodes.length > 0;
   }
}
