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
package inetsoft.web.binding.drm;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import org.springframework.stereotype.Component;

public class CalculateRefModel extends ColumnRefModel {
   public CalculateRefModel() {
   }

   public CalculateRefModel(CalculateRef dataRef) {
      super(dataRef);

      if(dataRef.getDataRef() instanceof ExpressionRef) {
         this.setDataRefModel(
            new ExpressionRefModel((ExpressionRef) dataRef.getDataRef()));
      }

      this.baseOnDetail = dataRef.isBaseOnDetail();
   }

   /**
    * Set base on detail value or aggregate value.
    */
   public void setBaseOnDetail(boolean baseOnDetail) {
      this.baseOnDetail = baseOnDetail;
   }

   /**
    * Check the calculate ref based on detail value.
    * @return true if the calculate ref based on detail value.
    */
   public boolean isBaseOnDetail() {
      return baseOnDetail;
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      CalculateRef ref = new CalculateRef(baseOnDetail);
      ref.setSQL(this.isSql());

      if(this.getDataType() != null) {
         ref.setDataType(this.getDataType());
      }

      if(this.getDataRefModel() != null) {
         ref.setDataRef(this.getDataRefModel().createDataRef());
      }

      return ref;
   }

   private boolean baseOnDetail = true;

   @Component
   public static final class CalculateRefModelFactory
      extends DataRefModelWrapperFactory<CalculateRef, CalculateRefModel>
   {
      @Override
      public Class<CalculateRef> getDataRefClass() {
         return CalculateRef.class;
      }

      @Override
      public CalculateRefModel createDataRefModel0(CalculateRef dataRef) {
         return new CalculateRefModel(dataRef);
      }
   }
}
