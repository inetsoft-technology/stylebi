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

import inetsoft.uql.asset.AliasDataRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class AliasDataRefModel extends ExpressionRefModel {
   public AliasDataRefModel() {
   }

   public AliasDataRefModel(AliasDataRef ref) {
      super(ref);

      if(ref.getDataRef() instanceof AttributeRef) {
         setRef(new AttributeRefModel((AttributeRef) ref.getDataRef()));
      }

      setName(ref.getName());
      setDataType(ref.getDataType());
   }

   @Override
   public DataRef createDataRef() {
      AliasDataRef ref = new AliasDataRef(getName(), getRef().createDataRef());
      super.setProperties(ref);

      return ref;
   }

   public DataRefModel getRef() {
      return ref;
   }

   public void setRef(DataRefModel ref) {
      this.ref = ref;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   private DataRefModel ref;
   private String name;
   private String dataType;

   @Component
   public static final class AliasDataRefModelFactory
      extends DataRefModelFactory<AliasDataRef, AliasDataRefModel>
   {
      @Override
      public Class<AliasDataRef> getDataRefClass() {
         return AliasDataRef.class;
      }

      @Override
      public AliasDataRefModel createDataRefModel(AliasDataRef dataRef) {
         return new AliasDataRefModel(dataRef);
      }
   }
}
