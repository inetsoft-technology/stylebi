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
import { Component, EventEmitter, Output } from "@angular/core";
import { GettingStartedService } from "./service/getting-started.service";

@Component({
   selector: "whether-stay-composer-dialog",
   templateUrl: "whether-stay-composer-dialog.component.html",
   styleUrls: ["whether-stay-composer-dialog.component.scss"]
})
export class WhetherStayComposerDialog {
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   isCollapsed = true;

   ok(): void {
      this.onCommit.emit();
   }

   /**
    * Called when user clicks cancel on dialog. Close dialog.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
