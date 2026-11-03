/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer.vs.command;

import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Offer to switch the dashboard's mark after its page background crossed to the other side.
 */
public class OfferVizMarkCommand implements ViewsheetCommand {
   public OfferVizMarkCommand() {
   }

   public OfferVizMarkCommand(boolean dark) {
      this.dark = dark;
   }

   /**
    * Whether the offered mark is dark.
    */
   public boolean isDark() {
      return dark;
   }

   public void setDark(boolean dark) {
      this.dark = dark;
   }

   private boolean dark;
}
