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
import { AfterViewInit, Directive, ElementRef, Input } from "@angular/core";

@Directive({
   selector: "[wExpandString]"
})
export class ExpandStringDirective implements AfterViewInit {
   @Input()
   set wExpandString(values: string[]) {
      this.values = values;
      this.expandString0();
   }

   @Input()
   set wExpandStringAttr(attribute: string) {
      this.attribute = attribute;
      this.expandString0();
   }

   private values: string[] = [];
   private attribute: string = null;
   private template: string = null;

   constructor(private host: ElementRef) {
   }

   ngAfterViewInit(): void {
      if(this.host.nativeElement) {
         const element = this.host.nativeElement;

         if(this.attribute) {
            this.template = element.getAttribute(this.attribute);
         }
         else {
            this.template = "";

            while(element.firstChild) {
               if(element.firstChild.nodeType === Node.TEXT_NODE) {
                  this.template += element.firstChild.nodeValue;
               }

               element.removeChild(element.firstChild);
            }
         }
      }

      this.expandString0();
   }

   private expandString0(): void {
      const content = ExpandStringDirective.expandString(this.template, this.values);

      if(content && this.host.nativeElement) {
         const element = this.host.nativeElement;

         if(this.attribute) {
            element.setAttribute(this.attribute, content);
         }
         else {
            while(element.firstChild) {
               element.removeChild(element.firstChild);
            }

            element.appendChild(element.ownerDocument.createTextNode(content));
         }
      }
   }

   public static expandString(template: string, values: string[]): string {
      let content = "";

      if(template) {
         let re = /[^%]?%s(\$\d+)?/g;
         let ch = 0;
         let index = 0;
         let match;

         while((match = re.exec(template)) != null) {
            if(match[0][0] != "%") {
               content += template.substring(ch, re.lastIndex - match[0].length + 1);
            }
            else {
               content += template.substring(ch, re.lastIndex - match[0].length);
            }

            let value;

            if(match[1]) {
               value = values[parseInt(match[1].substring(1), 10)];
            }
            else {
               value = values[index++];
            }

            content += value;
            ch = re.lastIndex;
         }

         if(ch < template.length) {
            content += template.substring(ch);
         }

      }

      return content;
   }
}
