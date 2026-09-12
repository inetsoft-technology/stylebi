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
package inetsoft.web.composer.model.vs;

import javax.annotation.Nullable;
import java.io.Serializable;

public class SaveViewsheetDialogModel implements Serializable {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getParentId() {
      return parentId;
   }

   public void setParentId(String parentId) {
      this.parentId = parentId;
   }

   public VSOptionsPaneModel getViewsheetOptionsPaneModel() {
      if(viewsheetOptionsPaneModel == null) {
         viewsheetOptionsPaneModel = new VSOptionsPaneModel();
      }

      return viewsheetOptionsPaneModel;
   }

   public void setViewsheetOptionsPaneModel(
      VSOptionsPaneModel viewsheetOptionsPaneModel)
   {
      this.viewsheetOptionsPaneModel = viewsheetOptionsPaneModel;
   }

   public boolean isUpdateDepend() {
      return updateDepend;
   }

   @Nullable
   public void setUpdateDepend(boolean updateDepend) {
      this.updateDepend = updateDepend;
   }

   private String name;
   private String parentId;
   private VSOptionsPaneModel viewsheetOptionsPaneModel;
   private boolean updateDepend;
}
