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
import { Pipe, PipeTransform } from "@angular/core";
import { JunctionOperator } from "../../common/data/condition/junction-operator";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";

/**
 * JunctionOperator pipe
 *
 * Converts a junction operator into its string representation
 *
 */
@Pipe({
   name: "junctionOperatorToString"
})
export class JunctionOperatorPipe implements PipeTransform {
   transform(junction: JunctionOperator): string {
      let indent: string = "";

      for(let i = 0; i < junction.level; i++) {
         indent += "....";
      }

      return indent + (junction.type == JunctionOperatorType.AND ? "[and]" : "[or]");
   }
}
