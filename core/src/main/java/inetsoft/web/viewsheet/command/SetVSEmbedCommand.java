/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.viewsheet.command;

import java.util.List;

/**
 * Command that informs the client of the embedded viewsheets.
 *
 * @since 12.3
 */
public class SetVSEmbedCommand implements ViewsheetCommand {
   public List<String> getIds() {
      return ids;
   }

   public void setIds(List<String> ids) {
      this.ids = ids;
   }

   private List<String> ids;
}
