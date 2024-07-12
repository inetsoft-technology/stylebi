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
/**
 * Event used to communicate to the backend if the autosave file should be deleted
 * when closing a sheet, if it exists
 */
export class CloseSheetEvent {
   public deleteAutosave: boolean;

   // noSave is true when the user has specifically said no to a confirmation prompt
   // asking if they wanted to save before closing due to unsaved changed
   constructor(deleteAutosave: boolean) {
      this.deleteAutosave = deleteAutosave;
   }
}
