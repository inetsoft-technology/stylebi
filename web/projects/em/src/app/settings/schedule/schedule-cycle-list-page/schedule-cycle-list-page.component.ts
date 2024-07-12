/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient } from "@angular/common/http";
import { Component } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ContextHelp } from "../../../context-help";
import { MonitoringDataService } from "../../../monitoring/monitoring-data.service";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { DataCycleInfo, DataCycleListModel } from "../model/data-cycle-list-model";

const TOPIC = "schedule";
const GET_DATA_CYCLE_NAMES_URI = "cycles/get-cycle-names";
const ADD_CYCLE_URI = "../api/em/schedule/add-cycle";
const REMOVE_CYCLES_URI = "../api/em/schedule/cycles/remove-cycles";
const UPDATE_CYCLES_URI = "schedule/cycles/update-cycles";

@Secured({
   route: "/settings/schedule/cycles",
   label: "Data Cycles"
})
@Searchable({
   route: "/settings/schedule/cycles",
   title: "Data Cycles",
   keywords: ["em.keyword.schedule", "em.keyword.dataCycle"]
})
@ContextHelp({
   route: "/settings/schedule/cycles",
   link: "EMSettingsScheduleCycleList"
})
@Component({
   selector: "em-schedule-cycle-list-page",
   templateUrl: "./schedule-cycle-list-page.component.html",
   styleUrls: ["./schedule-cycle-list-page.component.scss"]
})
export class ScheduleCycleListPageComponent {
   cycleList: Observable<any>;

   constructor(private pageTitle: PageHeaderService,
               private http: HttpClient,
               private router: Router,
               private dataService: MonitoringDataService,
               private dialog: MatDialog)
   {
      this.pageTitle.title = "_#(js:Data Cycles)";
      this.cycleList = this.dataService.connect(GET_DATA_CYCLE_NAMES_URI, TOPIC).pipe(
         map((list: DataCycleListModel) => list.cycles)
      );
   }

   addCycle(): void {
      this.http.get(ADD_CYCLE_URI).subscribe(
         (cycle: DataCycleInfo) => {
            this.dataService.sendMessage(UPDATE_CYCLES_URI);
            this.router.navigate(["/settings/schedule/cycles", cycle.name]);
         });
   }

   removeCycles(cycles: DataCycleInfo[]): void {
      let prompt = "_#(js:em.common.items.delete)";
      cycles.forEach(cycle => prompt += " " + cycle.name);
      this.dialog.open(MessageDialog, <MatDialogConfig>{
         data: {
            title: "_#(js:Confirm)",
            content: prompt,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.deleteCycles(cycles);
         }
      });
   }

   deleteCycles(cycles: DataCycleInfo[]): void {
      const model: DataCycleListModel = new DataCycleListModel(cycles);

      this.http.post(REMOVE_CYCLES_URI, model).subscribe(
         (success) => {
            this.dataService.sendMessage(UPDATE_CYCLES_URI);
         },
         (faild) => {
            this.dialog.open(MessageDialog, <MatDialogConfig>{
               data: {
                  title: "_#(js:Error)",
                  content: faild.error.message,
                  type: MessageDialogType.ERROR
               }
            });
         }
      );
   }
}
