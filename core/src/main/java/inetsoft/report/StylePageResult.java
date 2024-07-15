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
package inetsoft.report;

/**
 * Wrapper class that provides context for the result of a request for a StylePage.
 */
public class StylePageResult {
   public StylePageResult(StylePage page, boolean cancelled) {
      this.page = page;
      this.cancelled = cancelled;
   }

   public StylePage getPage() {
      return page;
   }

   public boolean isCancelled() {
      return cancelled;
   }

   private final StylePage page;
   private final boolean cancelled;
}
