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
import { AssetType, getTypeForId } from "./asset-type";

export interface AssetEntry {
   scope: number;
   type: AssetType;
   user: string;
   path: string;
   alias: string;
   identifier: string;
   properties: {[property: string]: string};
   createdUsername?: string;
   createdDate?: number;
   modifiedUsername?: string;
   modifiedDate?: number;
   folder?: boolean;
   description?: string;
   organization: string;
}

export function createAssetEntry(assetId: string): AssetEntry {
   let assetEntry: AssetEntry = null;
   const match = /^(\d+)\^([-+]?\d+)\^([^^]+)\^([^^]+)(\^(.+))?$/.exec(assetId);

   if(match) {
      assetEntry = {
         scope: parseInt(match[1], 10),
         type: getTypeForId(parseInt(match[2], 10)),
         user: match[3] === "__NULL__" ? null : match[3],
         path: match[4],
         organization: match[6],
         alias: null,
         identifier: assetId,
         properties: {}
      };
   }

   return assetEntry;
}