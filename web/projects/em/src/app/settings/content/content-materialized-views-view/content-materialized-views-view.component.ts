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
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";

@Secured({
   route: "/settings/content/materialized-views",
   label: "Materialized Views",
   requiredLicenses: []
})
@Searchable({
   route: "/settings/content/materialized-views",
   title: "Materialized Views",
   keywords: []
})
@ContextHelp({
   route: "/settings/content/materialized-views",
   link: "EMSettingsContentMaterializedViews"
})
@Component({
   selector: "em-content-materialized-views-view",
   templateUrl: "./content-materialized-views-view.component.html",
   styleUrls: ["./content-materialized-views-view.component.scss"]
})
export class ContentMaterializedViewsViewComponent implements OnInit {
   constructor(private pageTitle: PageHeaderService) {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Materialized Views)";
   }
}
