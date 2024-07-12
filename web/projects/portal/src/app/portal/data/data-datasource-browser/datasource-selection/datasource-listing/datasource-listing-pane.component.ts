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
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from "@angular/core";
import { DataSourceListing } from "./datasource-listing";

@Component({
   selector: "datasource-listing-pane",
   templateUrl: "datasource-listing-pane.component.html",
   styleUrls: ["datasource-listing-pane.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class DatasourceListingPaneComponent {
   @Input() listings: DataSourceListing[];
   @Input() selectedListingName: string;
   @Output() selectedListingNameChange = new EventEmitter<string>();
   @Output() submitted = new EventEmitter<string>();

   select(listing: DataSourceListing): void {
      this.selectedListingNameChange.emit(listing.name);
   }
}
