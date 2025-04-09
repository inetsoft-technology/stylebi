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
export interface IdentityId {
   name: string;
   orgID: string | null;
}

export function convertToKey(id: IdentityId) {
   return id.name + KEY_DELIMITER + id.orgID;
}

export function convertKeyToID(key: string) {
   let ind = key.indexOf(KEY_DELIMITER);

   if(ind > 0) {
      let name = key.substring(0,ind);
      let org = key.substring(ind+KEY_DELIMITER.length);
      return {name: name, orgID: org};
   }
   else {
      return {name: key, orgID: ""};
   }
}

export function convertMappedKeyToID(key: string) {
   const regex = /IdentityID\{name='(.*?)', orgID='(.*?)'\}/;
   const match = key.match(regex);

   if(match) {
      return { name: match[1], orgID: match[2] };
   }

   return { name: key, orgID: "" };
}

export function equalsIdentity(id: IdentityId, id2: IdentityId) {
   return id?.name === id2?.name && id?.orgID === id2?.orgID;
}

export function removeOrganization(key: string, organization: string) {
   let toRemove = KEY_DELIMITER + organization;
   return key.replace(toRemove, "");
}

export const KEY_DELIMITER: string = "~;~";
