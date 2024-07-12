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
import { ChangeDetectionStrategy, Component, HostBinding, Input } from "@angular/core";
import { DataSourceListing } from "./datasource-listing";
import { Tool } from "../../../../../../../../shared/util/tool";

@Component({
   selector: "datasource-listing",
   templateUrl: "datasource-listing.component.html",
   styleUrls: ["datasource-listing.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class DatasourceListingComponent {
   @Input() listing: DataSourceListing;
   @Input() selected: boolean;

   @HostBinding("attr.title")
   get title(): string {
      return this.listing.name;
   }

   getIconUrl(): string | null {
      let url = null;

      if(this.listing.iconUrl) {
         const encodedListingName = Tool.encodeURIComponentExceptSlash(this.listing.name);
         url = `../images/portal/data/datasource-listing/icon/${encodedListingName}`;
      }

      return url;
   }
}
