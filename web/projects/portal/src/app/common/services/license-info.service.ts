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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, from } from "rxjs";
import { LicenseInfo } from "../data/license-info";

@Injectable()
export class LicenseInfoService {
   private licenseInfo: Promise<LicenseInfo>;

   constructor(private http: HttpClient) {
   }

   getLicenseInfo(): Observable<LicenseInfo> {
      if(!this.licenseInfo) {
         this.licenseInfo = this.http.get<LicenseInfo>("../api/license-info").toPromise();
      }

      return from(this.licenseInfo);
   }
}
