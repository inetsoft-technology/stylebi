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
import { NEVER } from "rxjs";

export function logFn(target: Object, propertyKey: string, descriptor: TypedPropertyDescriptor<any>) {
   let originalMethod = descriptor.value; // save a reference to the original method

   // NOTE: Do not use arrow syntax here. Use a function expression in
   // order to use the correct value of `this` in this method (see notes below)
   descriptor.value = function(...args: any[]) {
      for(let i = 0; i < args.length; i++) {
         console.log(`Arg ${i}: ${JSON.stringify(args[i])}`);  // pre
      }
      let result = originalMethod.apply(this, args);                 // run and store the result
      console.log(`The return value is: ${JSON.stringify(result)}`); // post
      return result;                                                 // return the result of the original method
   };

   return descriptor;
}

export function CatchPromise(message: string) {
   return function(target: Object, propertyKey: string, descriptor: TypedPropertyDescriptor<any>) {
      let originalMethod = descriptor.value;
      let never = NEVER;

      descriptor.value = function(...args: any[]) {
         let pnever: Promise<any> = never.toPromise();
         let result = <Promise<any>> originalMethod.apply(this, args);

         return result.catch(() => {
            console.error(message);
            return pnever;
         });
      };

      return descriptor;
   };
}