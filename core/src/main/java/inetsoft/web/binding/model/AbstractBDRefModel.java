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
package inetsoft.web.binding.model;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ComboMode;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.web.binding.drm.*;

public abstract class AbstractBDRefModel extends AbstractDataRefModel
   implements BindingRefModel, DataRefModelWrapper
{
   public AbstractBDRefModel() {
   }

   public AbstractBDRefModel(VSDataRef ref) {
      super(ref);

      setFullName(ref.getFullName());
   }

   public AbstractBDRefModel(DataRef ref) {
      super(ref);
   }

   /**
    * Set the wrapped data ref.
    */
   @Override
   public void setDataRefModel(DataRefModel refModel) {
      this.refModel = refModel;
   }

   /**
    * Get the contained data ref.
    */
   @Override
   public DataRefModel getDataRefModel() {
      return refModel;
   }

   /**
    * Set the full name.
    */
   @Override
   public void setFullName(String fullName) {
      this.fullName = fullName;
   }

   /**
    * Get the full name.
    */
   @Override
   public String getFullName() {
      return fullName;
   }


   public ComboMode getComboType() {
      return comboType;
   }

   public void setComboType(ComboMode comboType) {
      this.comboType = comboType;
   }

   /**
    * Create a data ref.
    */
   @Override
   public abstract DataRef createDataRef();

   private String fullName;
   private DataRefModel refModel;
   private ComboMode comboType;
}
