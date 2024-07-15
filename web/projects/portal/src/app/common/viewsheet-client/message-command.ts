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
import { ViewsheetCommand } from "./index";
import { AssetEvent } from "../../composer/gui/ws/socket/asset-event";

export type MessageType = "OK" |
   "TRACE" |
   "DEBUG" |
   "INFO" |
   "WARNING" |
   "ERROR" |
   "CONFIRM" |
   "PROGRESS" |
   "OVERRIDE";

/**
 * Command used to instruct the client to display a message to the user.
 */
export interface MessageCommand extends ViewsheetCommand {
   /**
    * The message to display.
    */
   message: string;

   /**
    * The type of message.
    */
   type: MessageType;

   /**
    * The events of message command.
    */
   events: {[key: string]: AssetEvent};

   /**
    * The events of message command for no option.
    */
   noEvents?: {[key: string]: AssetEvent};

   /**
    * The name of the assembly associated with this message command
    */
   assemblyName?: string;
}