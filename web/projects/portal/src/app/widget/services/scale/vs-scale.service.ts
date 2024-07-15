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
import { Injectable } from "@angular/core";
import { BehaviorSubject, Observable } from "rxjs";
import { share } from "rxjs/operators";
import { ScaleService } from "./scale-service";

/**
 * Provides the current viewsheet scale
 */
@Injectable()
export class VSScaleService extends ScaleService {
   private scaleSubject = new BehaviorSubject(1);

   public getScale(): Observable<number> {
      return this.scaleSubject.pipe(share());
   }

   public getCurrentScale(): number {
      return this.scaleSubject.getValue();
   }

   public setScale(scale: number): void {
      this.scaleSubject.next(scale);
   }
}
