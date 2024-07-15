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
package inetsoft.web.binding.model;

import inetsoft.uql.asset.NumericRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class NumericRangeRefModel extends ExpressionRefModel {

   public NumericRangeRefModel() {
   }

   public NumericRangeRefModel(NumericRangeRef ref) {
      super(ref);

      if(ref.getDataRef() instanceof AttributeRef) {
         setRef(new AttributeRefModel((AttributeRef) ref.getDataRef()));
      }

      setAttr(ref.getAttribute());
      setVinfo(new ValueRangeInfoModel(ref.getValueRangeInfo()));
   }

   @Override
   public DataRef createDataRef() {
      NumericRangeRef ref = new NumericRangeRef(getAttribute());
      super.setProperties(ref);
      ref.setDataRef(getRef().createDataRef());
      ref.setValueRangeInfo(getVinfo().convertFromModel());

      return ref;
   }

   public DataRefModel getRef() {
      return ref;
   }

   public void setRef(DataRefModel ref) {
      this.ref = ref;
   }

   public String getAttr() {
      return attr;
   }

   public void setAttr(String attr) {
      this.attr = attr;
   }

   public ValueRangeInfoModel getVinfo() {
      return vinfo;
   }

   public void setVinfo(ValueRangeInfoModel vinfo) {
      this.vinfo = vinfo;
   }

   private DataRefModel ref;
   private String attr;
   private ValueRangeInfoModel vinfo;

   @Component
   public static final class NumericRangeRefModelFactory
      extends DataRefModelFactory<NumericRangeRef, NumericRangeRefModel>
   {
      @Override
      public Class<NumericRangeRef> getDataRefClass() {
         return NumericRangeRef.class;
      }

      @Override
      public NumericRangeRefModel createDataRefModel(NumericRangeRef dataRef) {
         return new NumericRangeRefModel(dataRef);
      }
   }
}
