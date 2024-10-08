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
import { CdkScrollable } from "@angular/cdk/overlay";
import { Directive, ElementRef, Input } from "@angular/core";

@Directive({
   selector: "[emNavigationScrollableItem]"
})
export class NavigationScrollableItemDirective {
   @Input() emNavigationScrollableItem: string;

   constructor(private element: ElementRef) {
   }

   public isInScrollView(scrollable: CdkScrollable): boolean {
      const childElement = this.element.nativeElement;
      const scrollableElement = scrollable.getElementRef().nativeElement;

      if(childElement && scrollableElement) {
         const childTop = childElement.offsetTop;
         const childBottom = childTop + childElement.clientHeight;
         const vpTop = scrollableElement.scrollTop;
         const vpBottom = vpTop + scrollableElement.clientHeight;

         return childTop >= vpTop && childTop <= vpBottom
            || childBottom >= vpTop && childBottom <= vpBottom
            || childTop < vpTop && childBottom > vpBottom;
      }

      return false;
   }
}
