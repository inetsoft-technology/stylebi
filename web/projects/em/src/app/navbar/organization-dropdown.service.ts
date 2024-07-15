/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable, OnDestroy } from "@angular/core";
import { Subject } from "rxjs";

@Injectable({
   providedIn: "root"
})
export class OrganizationDropdownService implements OnDestroy  {
   private provider: string;
   private refreshSubject: Subject<any>;

   constructor() {
      this.refreshSubject = new Subject<any>();
   }

   public get onRefresh(): Subject<any> {
      return this.refreshSubject;
   }

   public refresh(provider?: string, providerChanged?: boolean) {
      this.provider = provider;
      this.refreshSubject.next({provider : provider, providerChanged: providerChanged});
   }

   public setProvider(providerName: string): void {
      this.provider = providerName;
   }

   public getProvider(): string {
      return this.provider;
   }

   ngOnDestroy(): void {
      if(!!this.refreshSubject) {
         this.refreshSubject.unsubscribe();
         this.refreshSubject = null;
      }
   }
}