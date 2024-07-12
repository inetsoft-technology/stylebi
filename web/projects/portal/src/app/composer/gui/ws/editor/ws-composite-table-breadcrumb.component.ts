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
import {
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   Output
} from "@angular/core";

import { Worksheet } from "../../../data/ws/worksheet";
import { WSCompositeBreadcrumb } from "../../../data/ws/ws-composite-breadcrumb";

@Component({
   selector: "ws-composite-table-breadcrumb",
   templateUrl: "ws-composite-table-breadcrumb.component.html",
   styleUrls: ["ws-composite-table-breadcrumb.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WSCompositeTableBreadcrumbComponent {
   @Input() worksheet: Worksheet;
   @Input() breadcrumbs: WSCompositeBreadcrumb[];
   @Input() selectedBreadcrumb: WSCompositeBreadcrumb;
   @Output() onSelectBreadcrumb: EventEmitter<WSCompositeBreadcrumb> =
      new EventEmitter<WSCompositeBreadcrumb>();
   @Output() onClose: EventEmitter<void> = new EventEmitter<void>();
   @Output() onCancel: EventEmitter<void> = new EventEmitter<void>();

   selectBreadcrumb(breadcrumb: WSCompositeBreadcrumb) {
      this.onSelectBreadcrumb.emit(breadcrumb);
   }

   close() {
      this.onClose.emit();
   }

   cancel() {
      this.onCancel.emit();
   }
}