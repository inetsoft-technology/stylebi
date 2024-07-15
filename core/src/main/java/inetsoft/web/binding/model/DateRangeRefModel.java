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

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

public class DateRangeRefModel extends ExpressionRefModel {
   public DateRangeRefModel(){}

   public DateRangeRefModel(DateRangeRef ref) {
      super(ref);

      if(ref.getDataRef() instanceof AttributeRef) {
         setRef(new AttributeRefModel((AttributeRef) ref.getDataRef()));
      }
      else if(ref.getDataRef() instanceof ExpressionRef) {
         setRef(new ExpressionRefModel((ExpressionRef) ref.getDataRef()));
      }

      setDrill(ref.isApplyAutoDrill());
      setOption(ref.getDateOption());
      setOriginalType(ref.getOriginalType());
      setAutoCreate(ref.isAutoCreate());
   }

   @Override
   public DataRef createDataRef() {
      DateRangeRef ref = new DateRangeRef(getAttr());
      super.setProperties(ref);
      ref.setApplyAutoDrill(getDrill());
      ref.setDateOption(getOption());
      ref.setOriginalType(getOriginalType());

      if(getRef() != null) {
         ref.setDataRef(getRef().createDataRef());
      }

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

   public String getOriginalType() {
      return originalType;
   }

   public void setOriginalType(String originalType) {
      this.originalType = originalType;
   }

   public int getOption() {
      return option;
   }

   public void setOption(int option) {
      this.option = option;
   }

   public boolean getAutoCreate() {
      return autoCreate;
   }

   public void setAutoCreate(boolean autoCreate) {
      this.autoCreate = autoCreate;
   }

   public boolean getDrill() {
      return drill;
   }

   public void setDrill(boolean drill) {
      this.drill = drill;
   }

   private DataRefModel ref;
   private String attr;
   private String originalType = XSchema.STRING;
   private int option = DateRangeRef.YEAR_INTERVAL;
   private boolean autoCreate = true;
   private boolean drill = true;

   @Component
   public static final class DateRangeRefModelFactory
      extends DataRefModelFactory<DateRangeRef, DateRangeRefModel>
   {
      @Override
      public Class<DateRangeRef> getDataRefClass() {
         return DateRangeRef.class;
      }

      @Override
      public DateRangeRefModel createDataRefModel(DateRangeRef dataRef) {
         return new DateRangeRefModel(dataRef);
      }
   }
}
