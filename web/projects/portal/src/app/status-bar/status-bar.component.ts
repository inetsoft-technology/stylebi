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
import { ChangeDetectionStrategy, Component, Input } from "@angular/core";
import { Status } from "./status";

@Component({
   selector: "status-bar",
   templateUrl: "status-bar.component.html",
   styleUrls: ["status-bar.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusBar {
   @Input() status: Status;
   @Input() status2: Status;
   @Input() editWorksheetPermission: boolean;

   get statusTooltip(): string {
      return this.getTooltip(this.status);
   }

   get status2Tooltip(): string {
      return this.getTooltip(this.status2);
   }

   statusClicked(status: Status) {
      if(status.clickListener && this.editWorksheetPermission) {
         status.clickListener();
      }
   }

   private getTooltip(status: Status): string {
      return !!status && !!status.text ? status.text.replace(/<b>|<\/b>/g, "") : "";
   }
}
