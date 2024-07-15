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
/**
 * Adapted from {@link https://stackoverflow.com/a/48962159}.
 *
 * Class which allows for constant-space iteration over an inclusive range of numbers.
 * This is particularly useful in conjunction with ngFor in a component template.
 */
export class Range implements Iterable<number> {
   constructor(public readonly start: number,
               public readonly end: number,
               public readonly step: number = 1)
   {
      if(start == null) {
         throw new Error("start must be a number.");
      }

      if(end == null) {
         throw new Error("end must be a number.");
      }

      if(step == null) {
         throw new Error("step must be a number.");
      }

      if(step === 0) {
         throw new Error("step must be non-zero");
      }
   }

   *[Symbol.iterator](): Iterator<number> {
      if(this.step < 0) {
         for(let n = this.end; n >= this.start; n += this.step) {
            yield n;
         }
      }
      else { // step > 0
         for(let n = this.start; n <= this.end; n += this.step) {
            yield n;
         }
      }
   }
}
