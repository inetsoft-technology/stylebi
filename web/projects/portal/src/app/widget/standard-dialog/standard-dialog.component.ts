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
import {
   Component,
   ContentChild,
   EventEmitter,
   Input,
   Output,
   ViewEncapsulation
} from "@angular/core";
import { DialogButtonsDirective } from "./dialog-buttons.directive";
import { DialogContentDirective } from "./dialog-content.directive";

@Component({
   selector: "w-standard-dialog",
   templateUrl: "./standard-dialog.component.html",
   styleUrls: ["./standard-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class StandardDialogComponent {
   @Input() title: string;
   @Input() submitOnEnter: () => boolean = null;
   @Input() cshid: string;
   @Output() onClose = new EventEmitter<any>();
   @Output() onSubmit = new EventEmitter<void>();
   @ContentChild(DialogContentDirective) content: DialogContentDirective;
   @ContentChild(DialogButtonsDirective) buttons: DialogButtonsDirective;
}
