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
import { Component, OnInit } from "@angular/core";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { LogSettingsModel } from "../log-settings-model";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { LogSettingsChanges, LoggingSettingsViewComponent } from "../logging-settings-view/logging-settings-view.component";
import { Tool } from "../../../../../../shared/util/tool";
import { EditorPanelComponent } from "../../../common/util/editor-panel/editor-panel.component";

@Secured({
   route: "/settings/logging",
   label: "Logging"
})
@Searchable({
   route: "/settings/logging",
   title: "Logging Settings",
   keywords: []
})
@ContextHelp({
   route: "/settings/logging",
   link: "EMSettingsLogging"
})
@Component({
    selector: "em-logging-settings-page",
    templateUrl: "./logging-settings-page.component.html",
    styleUrls: ["./logging-settings-page.component.scss"],
    imports: [EditorPanelComponent, LoggingSettingsViewComponent]
})
export class LoggingSettingsPageComponent implements OnInit {
   model: LogSettingsModel;
   newModel: LogSettingsModel;
   valid: boolean = false;

   constructor(private pageTitle: PageHeaderService,
               private http: HttpClient,
               private errorService: ErrorHandlerService)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Logging Settings)";
      this.getConfiguration();
   }

   getConfiguration() {
      this.http.get<LogSettingsModel>("../api/em/log/setting/get-configuration").subscribe((settings: LogSettingsModel) => {
         this.model = Tool.clone(settings);
         this.newModel = settings;
      });
   }

   setConfiguration() {
      this.http.post("../api/em/log/setting/set-configuration", this.newModel).subscribe({
         next: () => {
            this.model = this.newModel;
            this.valid = false;
         },
         // without this the server's rejection is swallowed and the page simply fails to
         // update, which is exactly the silent failure this change exists to end
         error: (error) => this.errorService.showDialog(error, "_#(js:em.common.log.saveFailed)")
      });
   }

   configurationChanged(settings: LogSettingsChanges) {
      this.newModel = settings.changes;
      this.valid = settings.valid;
   }

   reset(): void {
      this.newModel = Tool.clone(this.model);
      this.valid = false;
   }
}
