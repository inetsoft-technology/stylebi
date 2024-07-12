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
import { AfterViewInit, Directive, ElementRef, HostListener, Input } from "@angular/core";
import { HelpUrlService } from "./help-url.service";

@Directive({
   selector: "[helpLink]"
})
export class HelpLinkDirective implements AfterViewInit {
   @Input() helpLink: string;
   private _helpUrl: string;

   constructor(private helpService: HelpUrlService, private element: ElementRef) {
      this.helpService.getHelpUrl()
         .subscribe((url) => this._helpUrl = url);
   }

   ngAfterViewInit() {
      let elem = this.element.nativeElement;

      if(!elem.getAttribute("title")) {
         elem.setAttribute("title", "_#(js:Help)");
      }

      let classAttr = elem.getAttribute("class");
      elem.setAttribute("class", "help-question-mark-icon cursor-pointer " + classAttr);
   }

   @HostListener("click", [])
   showSubDocument() {
      if(!!this._helpUrl) {
         window.open(this._helpUrl + "#cshid=" + this.helpLink);
      }
   }
}
