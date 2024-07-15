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
import { ViewsheetCommand } from "./viewsheet-command";

/**
 * Class that encapsulates a command received from the server.
 */
export class ViewsheetCommandMessage {
   /**
    * The name of the assembly targeted by the command.
    */
   public assembly: string;

   /**
    * The type of the command.
    */
   public type: string;

   /**
    * The command object.
    */
   public command: ViewsheetCommand;

   /**
    * Creates a new instance of <tt>ViewsheetMessageCommand</tt>.
    *
    * @param assembly the name of the assembly targeted by the command.
    * @param type     the type of the command.
    * @param command  the command object.
    */
   constructor(assembly: string = null, type: string = null,
               command: ViewsheetCommand = null) {
      this.assembly = assembly === "" ? null : assembly;
      this.type = type;
      this.command = command;
   }
}