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
import { HttpClient } from "@angular/common/http";
import { Component, OnInit } from "@angular/core";
import { FeatureFlagValue } from "../../../../../../shared/feature-flags/feature-flags.service";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { PluginsModel } from "../../../../../../shared/util/model/plugins-model";
import { Secured } from "../../../secured";

@Secured({
   route: "/settings/content/drivers-and-plugins",
   label: "Drivers and Plugins",
   hiddenForMultiTenancy: true
})
@Searchable({
   route: "/settings/content/drivers-and-plugins",
   title: "Drivers and Plugins",
   keywords: []
})
@ContextHelp({
   route: "/settings/content/drivers-and-plugins",
   link: "EMSettingsContentDriversPlugins"
})
@Component({
   selector: "em-content-drivers-and-plugins-view",
   templateUrl: "./content-drivers-and-plugins-view.component.html",
   styleUrls: ["./content-drivers-and-plugins-view.component.scss"]
})
export class ContentDriversAndPluginsViewComponent implements OnInit {
   pluginsModel: PluginsModel;
   FeatureFlag = FeatureFlagValue;

   constructor(private pageTitle: PageHeaderService, private http: HttpClient) {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Drivers and Plugins)";
      this.http.get<PluginsModel>("../api/data/plugins")
         .subscribe(model => this.pluginsModel = model);
   }

}
