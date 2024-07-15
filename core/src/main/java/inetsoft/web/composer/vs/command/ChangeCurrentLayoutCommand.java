/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.composer.vs.command;

import inetsoft.web.composer.model.vs.VSLayoutModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

public class ChangeCurrentLayoutCommand implements ViewsheetCommand {
   public ChangeCurrentLayoutCommand(VSLayoutModel layout) {
      this.layout = layout;
   }

   public VSLayoutModel getLayout() {
      return layout;
   }

   public void setLayout(VSLayoutModel layout) {
      this.layout = layout;
   }

   private VSLayoutModel layout;
}
