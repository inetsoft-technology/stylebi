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
import { BehaviorSubject, Observable } from "rxjs";
import { HttpClient } from "@angular/common/http";
import { MonitoringDataService } from "./monitoring-data.service";
import { ColumnInfo } from "../common/util/table/column-info";

export const MonitorLevel = {
   HIGH: 10,
   MEDIUM: 5,
   LOW: 1,
   OFF: 0
};

@Injectable()
export class MonitorLevelService {
   private _monitorLevel = new BehaviorSubject<number>(MonitorLevel.OFF);

   constructor(private monitoringDataService: MonitoringDataService, private http: HttpClient) {
      this.monitoringDataService.connect("/monitor-level")
         .subscribe((level: number) => {
            if(this._monitorLevel.value !== level) {
               this._monitorLevel.next(level);
            }
         });
   }

   public getMonitorLevel(): number {
      return this._monitorLevel.getValue();
   }

   public getMonitorLevelLabel(level: number): string {
      let label = "";

      switch(level) {
         case MonitorLevel.OFF:
            label = "OFF";
            break;
         case MonitorLevel.LOW:
            label = "LOW";
            break;
         case MonitorLevel.MEDIUM:
            label = "MEDIUM";
            break;
         case MonitorLevel.HIGH:
            label = "HIGH";
            break;
         default:
            label = "OFF";
            break;
      }

      return label;
   }

   public monitorLevel(): Observable<number> {
      return this._monitorLevel.asObservable();
   }

   public monitorLevelForGuard(): Observable<any> {
      return this.http.get("../api/em/monitoring/level");
   }

   public filterColumns(cols: ColumnInfo[]): ColumnInfo[] {
      return cols.filter((col) => !col.level || col.level <= this._monitorLevel.value);
   }
}
