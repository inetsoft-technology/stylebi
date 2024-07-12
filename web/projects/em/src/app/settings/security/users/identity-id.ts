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
export interface IdentityId {
   name: string;
   organization: string | null;
}

export function convertToKey(id: IdentityId) {
   return id.name + KEY_DELIMITER + id.organization;
}

export function convertKeyToID(key: string) {
   let ind = key.indexOf(KEY_DELIMITER);

   if(ind > 0) {
      let name = key.substring(0,ind);
      let org = key.substring(ind+KEY_DELIMITER.length);
      return {name: name, organization: org};
   }
   else {
      return {name: key, organization: ""};
   }
}

export function equalsIdentity(id: IdentityId, id2: IdentityId) {
   return id?.name === id2?.name && id?.organization === id2?.organization;
}

export function removeOrganization(key: string, organization: string) {
   let toRemove = KEY_DELIMITER + organization;
   return key.replace(toRemove, "");
}

export const KEY_DELIMITER: string = "~;~";