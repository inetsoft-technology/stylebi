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
package inetsoft.web.binding.drm;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.FormRef;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import org.springframework.stereotype.Component;

public class FormRefModel extends ColumnRefModel {
   public FormRefModel() {
   }

   public FormRefModel(FormRef dataRef) {
      super(dataRef);
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      FormRef temp = new FormRef();
      temp.setAlias(this.getAlias());
      temp.setWidth(this.getWidth());
      temp.setVisible(this.isVisible());
      temp.setValid(this.isValid());
      temp.setSQL(this.isSql());

      if(this.getDataRefModel() != null) {
         temp.setDataRef(this.getDataRefModel().createDataRef());
      }

      return temp;
   }

   private DataRefModel refModel;

   @Component
   public static final class FormRefModelFactory
      extends DataRefModelWrapperFactory<FormRef, FormRefModel>
   {
      @Override
      public Class<FormRef> getDataRefClass() {
         return FormRef.class;
      }

      @Override
      public FormRefModel createDataRefModel0(FormRef dataRef) {
         return new FormRefModel(dataRef);
      }
   }
}
