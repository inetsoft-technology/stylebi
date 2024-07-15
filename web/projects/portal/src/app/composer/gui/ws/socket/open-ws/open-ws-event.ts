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
