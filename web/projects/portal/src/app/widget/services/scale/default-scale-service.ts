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
import { Injectable } from "@angular/core";
import { Observable, of as observableOf } from "rxjs";
import { ScaleService } from "./scale-service";

/**
 * Default scale service doesn't allow changing the scale
 */
@Injectable()
export class DefaultScaleService extends ScaleService {
   private readonly scale = observableOf(1);

   public getScale(): Observable<number> {
      return this.scale;
   }

   getCurrentScale(): number {
      return 1;
   }

   public setScale(scale: number): void {
      throw new Error("Trying to set Viewsheet scale in non-scalable context");
   }
}
