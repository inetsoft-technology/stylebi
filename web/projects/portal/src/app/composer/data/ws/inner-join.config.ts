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
import { LVPair } from "../../../common/util/common-types";
import { XConstants } from "../../../common/util/xconstants";

export const operators: LVPair[] = [
   {label: "=", value: XConstants.INNER_JOIN},
   {label: ">", value: XConstants.GREATER_JOIN},
   {label: "<", value: XConstants.LESS_JOIN},
   {label: ">=", value: XConstants.GREATER_EQUAL_JOIN},
   {label: "<=", value: XConstants.LESS_EQUAL_JOIN},
   {label: "<>", value: XConstants.NOT_EQUAL_JOIN}
];

export const joinMap: (op: number) => string = (op: number) => {
   let res: string;

   switch(op) {
      case XConstants.INNER_JOIN: res = "="; break;
      case XConstants.LEFT_JOIN: res = "*="; break;
      case XConstants.RIGHT_JOIN: res = "=*"; break;
      case XConstants.FULL_JOIN: res = "*=*"; break;
      case XConstants.NOT_EQUAL_JOIN: res = "<>"; break;
      case XConstants.GREATER_JOIN: res = ">"; break;
      case XConstants.GREATER_EQUAL_JOIN: res = ">="; break;
      case XConstants.LESS_JOIN: res = "<"; break;
      case XConstants.LESS_EQUAL_JOIN: res = "<="; break;
      default:
   }

   return res;
};

export const operatorToJoinTitle: (op: number) => string = (op: number) => {
   let res: string;

   switch(op) {
      case XConstants.INNER_JOIN: res = "_#(js:Inner Join)"; break;
      case XConstants.LEFT_JOIN: res = "_#(js:Left Join)"; break;
      case XConstants.RIGHT_JOIN: res = "_#(js:Right Join)"; break;
      case XConstants.FULL_JOIN: res = "_#(js:Full Join)"; break;
      case XConstants.NOT_EQUAL_JOIN: res = "_#(js:Not Equal Join)"; break;
      case XConstants.GREATER_JOIN: res = "_#(js:Greater Join)"; break;
      case XConstants.GREATER_EQUAL_JOIN: res = "_#(js:Greater Equal Join)"; break;
      case XConstants.LESS_JOIN: res = "_#(js:Less Join)"; break;
      case XConstants.LESS_EQUAL_JOIN: res = "_#(js:Less Equal Join)"; break;
      default:
   }

   return res;
};
