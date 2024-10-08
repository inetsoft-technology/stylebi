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
import { AfterViewInit, Directive, ElementRef, HostListener, Input } from "@angular/core";
import { HelpService } from "./help.service";
import { HelpLink } from "./help-link";

@Directive({
   selector: "[emHelpLink]"
})
export class HelpLinkDirective implements AfterViewInit {
   @Input() emHelpLink: string;
   private _helpUrl: string;

   constructor(private helpService: HelpService, private element: ElementRef) {
      this.helpService.getHelpUrl()
         .subscribe((helpUrl) => this._helpUrl = helpUrl);
   }

   ngAfterViewInit() {
      let elem = this.element.nativeElement;
      elem.setAttribute("title", "_#(js:Help)");
      let classAttr = elem.getAttribute("class");
      elem.setAttribute("class", "help-question-mark-icon cursor-pointer " + classAttr);
   }

   @HostListener("click", [])
   showSubDocument() {
      if(!!this._helpUrl) {
         window.open(this._helpUrl + "#cshid=" + this.emHelpLink);
      }
   }
}
