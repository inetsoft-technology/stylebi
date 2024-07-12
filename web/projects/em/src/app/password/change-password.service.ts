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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../shared/util/tool";

@Injectable({
   providedIn: "root"
})
export class ChangePasswordService {
   constructor(private http: HttpClient, private snackBar: MatSnackBar) {
   }

   changePassword(password: string): Observable<boolean> {
      return this.http.put("../api/em/currentuser/password", {password}).pipe(
         map(() => true)
      );
   }

   verifyOldPassword(oldPassword: string): Observable<boolean> {
      return this.http.put<boolean>("../api/em/currentuser/password/verify", {
         password: oldPassword
      });
   }

   notify(message: string): void {
      this.snackBar.open(message, "_#(js:Close)", {
         duration: Tool.SNACKBAR_DURATION
      });
   }
}
