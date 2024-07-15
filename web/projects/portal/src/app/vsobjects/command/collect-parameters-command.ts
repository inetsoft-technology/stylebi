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
import { VariableInfo } from "../../common/data/variable-info";

/**
 * Command used to instruct the client to collect parameters.
 */
export interface CollectParametersCommand {
   /**
    * Whether parameters are disabled or not.
    */
   disableParameterSheet: boolean;

   /**
    * Whether it is open viewsheet.
    */
   isOpenSheet: boolean;

   /**
    * The list of disabled variables.
    */
   disabledVariables: String[];

   /**
    * The list of all variables.
    */
   variables: VariableInfo[];
}