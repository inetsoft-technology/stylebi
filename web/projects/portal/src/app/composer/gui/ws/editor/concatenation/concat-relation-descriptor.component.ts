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

import { XConstants } from "../../../../../common/util/xconstants";

@Component({
   selector: "concat-relation-descriptor",
   templateUrl: "concat-relation-descriptor.component.html",
   changeDetection: ChangeDetectionStrategy.OnPush,
   styleUrls: ["concat-relation-descriptor.component.scss"]
})
export class ConcatRelationDescriptorComponent {
   @Input() operation: XConstants;
   @Output() onSelect: EventEmitter<void> = new EventEmitter<void>();
   readonly UNION = XConstants.UNION;
   readonly INTERSECT = XConstants.INTERSECT;
   readonly MINUS = XConstants.MINUS;

   select() {
      this.onSelect.emit();
   }
}