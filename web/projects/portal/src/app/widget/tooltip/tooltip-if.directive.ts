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
import { Input, Directive, ElementRef, AfterViewChecked, NgZone, OnDestroy } from "@angular/core";
import { DomService } from "../dom-service/dom.service";

@Directive({
   selector: "[tooltipIf]"
})
export class TooltipIfDirective implements AfterViewChecked, OnDestroy {
   @Input() disableTooltipIf: boolean;
   private oneeded: boolean = null;
   private ocontent: string = null;

   constructor(private host: ElementRef,
               private zone: NgZone,
               private domService: DomService) {
   }

   ngAfterViewChecked() {
      this.domService.requestRead(() => {
         if(this.disableTooltipIf) {
            return;
         }

         const elem = this.host.nativeElement;
         const needed: boolean = elem.scrollWidth > elem.clientWidth ||
            elem.scrollHeight > elem.clientHeight * 1.5;
         const content = elem.textContent ? elem.textContent : elem.value;
         const refreshTitle = this.ocontent != content;

         if(this.oneeded != needed || refreshTitle) {
            const hasTitle = elem.hasAttribute("title");

            this.domService.requestWrite(() => {
               this.oneeded = needed;
               this.ocontent = content;

               if(needed && (!hasTitle || refreshTitle)) {
                  if(hasTitle) {
                     elem.removeAttribute("title");
                  }

                  const title = content;

                  if(title && title.trim()) {
                     elem.setAttribute("title", title.trim());
                  }
                  else {
                     elem.removeAttribute("title");
                  }
               }
               else if(!needed && hasTitle) {
                  elem.removeAttribute("title");
               }
            });
         }
      });
   }

   ngOnDestroy() {
      this.domService.cancelAnimationFrame();
   }
}
