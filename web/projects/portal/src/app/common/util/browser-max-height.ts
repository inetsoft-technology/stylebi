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
declare const window: any;

/**
 * Above a certain browser-specific element height, browsers will no longer correctly render
 * the element with that height, and the application may exhibit unexpected behavior as a result.
 * This namespace calculates this max height.
 */
export namespace BrowserMaxHeight {
   let maxHeight: number;

   /**
    * Returns this browser's max height for an element.
    */
   export function getBrowserMaxHeight(): number {
      if(maxHeight == null) {
         maxHeight = measureBrowserMaxHeight();

         // safety, make sure the height is a valid number
         if(isNaN(maxHeight) || !isFinite(maxHeight) || !maxHeight) {
            // number reported by chrome when it's working properly
            maxHeight = 25165824;
         }
      }

      return maxHeight;
   }

  // eslint-disable-next-line no-inner-declarations
   function measureBrowserMaxHeight(): number {
      let element = window.document.createElement("div");
      element.style.visibility = "hidden";
      element.style.width = "1px";
      window.document.body.appendChild(element);

      const browserMaxHeight = measureMaxHeight(element);

      element.parentNode.removeChild(element);
      return browserMaxHeight;
   }

   /**
    * Measures the browser's max height using binary search.
    * Taken from: https://stackoverflow.com/questions/7719273/determine-maximum-possible-div-height
    *
    * @param element the element to measure the max height on.
    */
   // eslint-disable-next-line no-inner-declarations
   function measureMaxHeight(element: HTMLElement): number {
      const maxBound = Math.pow(2, 53);
      let lower: number;
      let upper: number;

      for(upper = 1; testHeight(element, upper); upper *= 2) {
         if(upper >= maxBound) {
            return maxBound;
         }

         lower = upper;
      }

      while(lower <= upper) {
         const mid = Math.floor((lower + upper) / 2);

         if(testHeight(element, mid)) {
            if(!testHeight(element, mid + 1)) {
               return mid;
            }
            else {
               lower = mid + 1;
            }
         }
         else {
            upper = mid - 1;
         }
      }

      return 0;
   }

   /**
    * Sets the height on the element in pixels and tests whether or not the element height is
    * correctly rendered.
    *
    * @param element the element to set the height on
    * @param height the height to set
    *
    * @return true if the clientHeight is the same as height, false otherwise.
    */
   // eslint-disable-next-line no-inner-declarations
   function testHeight(element: HTMLElement, height: number): boolean {
      element.style.height = height + "px";
      // allow for 1px round error when zoom is not 100%
      return Math.abs(element.clientHeight - height) <= 1;
   }
}
