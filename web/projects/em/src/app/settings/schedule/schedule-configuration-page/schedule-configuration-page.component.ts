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
import { Component, OnInit } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { HttpClient } from "@angular/common/http";
import { Secured } from "../../../secured";
import { ScheduleConfigurationModel } from "../model/schedule-configuration-model";
import { Tool } from "../../../../../../shared/util/tool";
import { ScheduleConfiguration } from "../schedule-configuration-view/schedule-configuration-view.component";

const SCHEDULE_CONFIGURATION_URL = "../api/em/settings/schedule/configuration";

@Secured({
   route: "/settings/schedule/settings",
   label: "Settings",
   hiddenForMultiTenancy: true
})
@Searchable({
   title: "Scheduler Settings",
   route: "/settings/schedule/settings",
   keywords: ["em.settings.schedule.settings", "em.settings.schedule.notification"]
})
@ContextHelp({
   route: "/settings/schedule/settings",
   link: "EMSettingsScheduleSettings"
})
@Component({
   selector: "em-schedule-configuration-page",
   templateUrl: "./schedule-configuration-page.component.html",
   styleUrls: ["./schedule-configuration-page.component.scss"]
})
export class ScheduleConfigurationPageComponent implements OnInit {
   model: ScheduleConfigurationModel;
   resetModel: ScheduleConfigurationModel;
   valid: boolean = true;
   changed: boolean = false;

   constructor(private http: HttpClient,
      private pageTitle: PageHeaderService,
      private dialog: MatDialog) {
      this.http.get(SCHEDULE_CONFIGURATION_URL)
         .subscribe((model: ScheduleConfigurationModel) => {
            this.model = model;
            this.resetModel = Tool.clone(this.model);
         });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Scheduler Settings)";
   }

   onConfigurationChange(config: ScheduleConfiguration) {
      this.model = config.model;
      this.valid = config.valid;
      this.changed = true;
   }

   onSubmit() {
      this.dialog.open(MessageDialog, <MatDialogConfig>{
         data: {
            title: "_#(js:Warning)",
            content: "_#(js:em.schedule.apply)",
            type: MessageDialogType.WARNING
         }
      });

      this.http.put(SCHEDULE_CONFIGURATION_URL, this.model)
         .subscribe(() => {
            for(let timeRange of this.model.timeRanges) {
               timeRange.modified = null;
            }

            this.resetModel = Tool.clone(this.model);
            this.changed = false;
         });
   }

   reset() {
      this.model = this.resetModel;
      this.resetModel = Tool.clone(this.model);
      this.changed = false;
   }
}
