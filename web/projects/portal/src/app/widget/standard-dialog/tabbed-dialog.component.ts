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
   Component,
   ContentChild,
   ContentChildren,
   EventEmitter,
   Input,
   Output,
   ViewEncapsulation
} from "@angular/core";
import { DialogButtonsDirective } from "./dialog-buttons.directive";
import { DialogTabDirective } from "./dialog-tab.directive";
import { UIContextService } from "../../common/services/ui-context.service";

@Component({
   selector: "w-tabbed-dialog",
   templateUrl: "./tabbed-dialog.component.html",
   styleUrls: ["./tabbed-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class TabbedDialogComponent {
   @Input() title: string;
   @Input() submitOnEnter: () => boolean = null;
   @Output() onClose = new EventEmitter<any>();
   @Output() onSubmit = new EventEmitter<void>();
   @ContentChildren(DialogTabDirective) tabs;
   @ContentChild(DialogButtonsDirective) buttons: DialogButtonsDirective;

   public constructor(private uiContextService: UIContextService) {
   }

   @Input()
   get defaultTab(): string {
      return this.uiContextService.getDefaultTab(this.title, null);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab(this.title, tab);
   }
}
