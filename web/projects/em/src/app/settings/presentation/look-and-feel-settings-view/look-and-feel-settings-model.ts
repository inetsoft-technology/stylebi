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
import { FileData } from "../../../../../../shared/util/model/file-data";
import { UserFontModel } from "./user-font-model";
import { FontFaceModel } from "./font-face-model";

export interface LookAndFeelSettingsModel {
   ascending: boolean;
   repositoryTree: boolean;
   expand: boolean;
   customLogoEnabled: boolean;
   defaultLogo: boolean;
   defaultFavicon: boolean;
   defaultViewsheet: boolean;
   defaultFont: boolean;
   logoName: string;
   logoFile?: FileData;
   faviconName: string;
   faviconFile?: FileData;
   viewsheetName: string;
   viewsheetFile?: FileData;
   userformatFile?: FileData;
   userFonts?: string[];
   fontFaces: FontFaceModel[];
   newFontFaces?: UserFontModel[];
   deleteFontFaces?: FontFaceModel[];
   vsEnabled: boolean;
}
