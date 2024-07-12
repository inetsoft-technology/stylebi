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
import { ComponentRef, EventEmitter } from "@angular/core";
import { Point } from "../../common/data/point";
import { Rectangle } from "../../common/data/rectangle";
import { FixedDropdownComponent } from "./fixed-dropdown.component";
import { ContentRef } from "./fixed-dropdown.service";
import { DropdownStackService } from "./dropdown-stack.service";

/**
 * Class which contains a reference to the dynamically created dropdown component.
 */
export class DropdownRef {
   private _closed: boolean = false;

   constructor(private dropdownCmptRef: ComponentRef<FixedDropdownComponent>,
               private stackService: DropdownStackService,
               private contentRef: ContentRef)
   {
      const dropdown = dropdownCmptRef.instance;

      dropdown.onClose.subscribe(() => {
         this.close();
      });

      dropdown.onOpen.subscribe(() => {
         const elem = this.dropdownCmptRef.location.nativeElement;
         const bounds = elem.getBoundingClientRect();
         const width = bounds.width > 0 ? bounds.width : elem.scrollWidth;
         const height = bounds.height > 0 ? bounds.height : elem.scrollHeight;
         dropdown.dropdownBounds = new Rectangle(bounds.left, bounds.top, width, height);
         this.stackService.push(dropdown);
      });
   }

   /**
    * The instance of component used as dropdown's content.
    * Undefined when a TemplateRef is used as dropdown's content.
    */
   get componentInstance(): any {
      if(this.contentRef.componentRef) {
         return this.contentRef.componentRef.instance;
      }
   }

   get viewRef(): any {
      if(this.contentRef) {
         return this.contentRef.viewRef;
      }
   }

   get dropdownInstance(): FixedDropdownComponent {
      return this.dropdownCmptRef.instance;
   }

   get closed(): boolean {
      return this._closed;
   }

   /**
    * Return true if closed, false otherwise.
    */
   public close(): boolean {
      if(!this._closed) {
         this._closed = true;
         this.removeDropdownElements();
         this.stackService.pop(this.dropdownInstance);
         return true;
      }

      return false;
   }

   public get closeEvent(): EventEmitter<any> {
      return this.dropdownCmptRef.instance.onClose;
   }

   private removeDropdownElements() {
      const dropdownNativeEl = this.dropdownCmptRef.location.nativeElement;

      if(!!dropdownNativeEl.parentNode) {
         dropdownNativeEl.parentNode.removeChild(dropdownNativeEl);
      }

      this.dropdownCmptRef.destroy();

      if(this.contentRef && this.contentRef.viewRef) {
         this.contentRef.viewRef.destroy();
      }

      this.contentRef = null;
   }
}
