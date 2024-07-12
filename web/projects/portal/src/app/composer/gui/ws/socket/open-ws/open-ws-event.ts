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
export class OpenWorksheetEvent {
   private id: string;
   private openAutoSavedFile: boolean = false;
   private gettingStartedWs: boolean = false;
   private createQuery: boolean = false;

   public setId(id: string) {
      this.id = id;
   }

   public setOpenAutoSavedFile(openAutoSavedFile: boolean) {
      this.openAutoSavedFile = openAutoSavedFile;
   }

   public setGettingStartedWs(gettingStartedWs: boolean) {
      this.gettingStartedWs = gettingStartedWs;
   }

   public setCreateQuery(createQuery: boolean) {
      this.createQuery = createQuery;
   }
}
