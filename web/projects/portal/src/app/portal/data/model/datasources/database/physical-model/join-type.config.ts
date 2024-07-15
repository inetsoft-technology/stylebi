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
import { JoinType } from "./join-type.enum";

export const joinMap: (op: JoinType) => string = (op: JoinType) => {
   let res: string;

   switch(+op) {
      case JoinType.EQUAL: res = "="; break;
      case JoinType.LEFT_OUTER: res = "*="; break;
      case JoinType.RIGHT_OUTER: res = "=*"; break;
      case JoinType.FULL_OUTER: res = "*=*"; break;
      case JoinType.GREATER: res = ">"; break;
      case JoinType.LESS: res = "<"; break;
      case JoinType.GREATER_EQUAL: res = ">="; break;
      case JoinType.LESS_EQUAL: res = "<="; break;
      case JoinType.NOT_EQUAL: res = "<>"; break;
      default:
   }

   return res;
};

export const operatorToJoinTitle: (op: JoinType) => string = (op: JoinType) => {
   let res: string;

   switch(op) {
      case JoinType.EQUAL: res = "_#(js:Equal Join)"; break;
      case JoinType.LEFT_OUTER: res = "_#(js:Left Join)"; break;
      case JoinType.RIGHT_OUTER: res = "_#(js:Right Join)"; break;
      case JoinType.FULL_OUTER: res = "_#(js:Full Join)"; break;
      case JoinType.GREATER: res = "_#(js:Greater Join)"; break;
      case JoinType.LESS: res = "_#(js:Less Join)"; break;
      case JoinType.GREATER_EQUAL: res = "_#(js:Greater Equal Join)"; break;
      case JoinType.LESS_EQUAL: res = "_#(js:Less Equal Join)"; break;
      case JoinType.NOT_EQUAL: res = "_#(js:Not Equal Join)"; break;
      default:
   }

   return res;
};
