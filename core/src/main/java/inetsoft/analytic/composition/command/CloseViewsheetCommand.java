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
package inetsoft.analytic.composition.command;

import inetsoft.uql.asset.AssetEntry;

/**
 * Close viewsheet command.
 *
 * @version 8.5, 08/02/2006
 * @author InetSoft Technology Corp
 */
public class CloseViewsheetCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public CloseViewsheetCommand() {
      super();
   }

   /**
    * Constructor.
    * @param entry the specified worksheet entry.
    */
   public CloseViewsheetCommand(AssetEntry entry) {
      super();
      put("entry", entry);
   }
}