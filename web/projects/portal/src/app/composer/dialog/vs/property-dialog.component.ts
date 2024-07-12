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
import { Directive, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { Subscription } from "rxjs";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";

@Directive()
export class PropertyDialog implements OnInit, OnDestroy {
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() runtimeId: string;
   @Input() openToScript: boolean = false;
   @Input() assemblyName: string;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();

   private oldScripts: string[];
   addRemoveSub: Subscription = null;
   objectAddRemoved: boolean = false;

   public constructor(protected uiContextService: UIContextService,
                      protected trapService: VSTrapService,
                      protected propertyDialogService: PropertyDialogService)
   {
      this.addRemoveSub = uiContextService.getObjectChange().subscribe(msg => {
         if(msg.action == "add" || msg.action == "delete" || msg.action == "rename") {
            this.objectAddRemoved = true;
         }
      });
   }

   ngOnInit() {
      this.oldScripts = this.getScripts().filter(a => !!a);
   }

   ngOnDestroy(): void {
      this.addRemoveSub.unsubscribe();
   }

   private checkScript(isApply: boolean, collapse: boolean = false) {
      const scripts = this.getScripts().filter(a => !!a);

      if(scripts + "" != this.oldScripts + "") {
         this.propertyDialogService.checkScript(this.runtimeId, scripts,
                                                () => this.closing(isApply, collapse), () => {});
      }
      else {
         this.closing(isApply, collapse);
      }
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      // to be implemented
   }

   protected getScripts(): string[] {
      return []; // t obe implemented
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.checkScript(false);
   }

   apply(event: boolean): void {
      this.checkScript(true, event);
   }
}
