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
package inetsoft.web.binding.drm;

import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import org.springframework.stereotype.Component;

public class ColumnRefModel extends AbstractDataRefModel implements DataRefModelWrapper {
   public ColumnRefModel() {
   }

   public ColumnRefModel(ColumnRef dataRef) {
      super(dataRef);

      if(dataRef.getDataRef() instanceof DateRangeRef) {
         this.refModel = new DateRangeRefModel((DateRangeRef) dataRef.getDataRef());
      }
      else if(dataRef.getDataRef() instanceof NumericRangeRef) {
         this.refModel = new NumericRangeRefModel((NumericRangeRef) dataRef.getDataRef());
      }
      else if(dataRef.getDataRef() instanceof ColumnRef) {
         this.refModel = new ColumnRefModel((ColumnRef) dataRef.getDataRef());
      }
      else if(dataRef.getDataRef() instanceof AttributeRef) {
         this.refModel = new AttributeRefModel((AttributeRef) dataRef.getDataRef());
      }
      else if(dataRef.getDataRef() instanceof AggregateRef) {
         this.refModel = new AggregateRefModel((AggregateRef) dataRef.getDataRef());
      }
      else if(dataRef.getDataRef() instanceof ExpressionRef) {
         this.refModel = new ExpressionRefModel((ExpressionRef) dataRef.getDataRef());
      }

      this.alias = dataRef.getAlias();
      this.width = dataRef.getWidth();
      this.visible = dataRef.isVisible();
      this.valid = dataRef.isValid();
      this.sql = dataRef.isSQL();
      this.desc = dataRef.getDescription();
   }

   /**
    * @return the ref
    */
   @Override
   public DataRefModel getDataRefModel() {
      return refModel;
   }

   /**
    * @param refModel the ref to set
    */
   @Override
   public void setDataRefModel(DataRefModel refModel) {
      this.refModel = refModel;
   }

   /**
    * @return the alias
    */
   public String getAlias() {
      return alias;
   }

   /**
    * @param alias the alias to set
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * @return the width
    */
   public int getWidth() {
      return width;
   }

   /**
    * @param width the width to set
    */
   public void setWidth(int width) {
      this.width = width;
   }

   /**
    * @return the visible
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * @param visible the visible to set
    */
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * @return the valid
    */
   public boolean isValid() {
      return valid;
   }

   /**
    * @param valid the valid to set
    */
   public void setValid(boolean valid) {
      this.valid = valid;
   }

   /**
    * @return the sql
    */
   public boolean isSql() {
      return sql;
   }

   /**
    * @param sql the sql to set
    */
   public void setSql(boolean sql) {
      this.sql = sql;
   }

   /**
    * @return the description
    */
   public String getDescription() {
      return desc;
   }

   /**
    * @param desc the description to set
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * @return the order
    */
   public int getOrder() {
      return order;
   }

   /**
    * @param ord the order to set
    */
   public void setOrder(int ord) {
      this.order = ord;
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      DataRef ref;

      if(this.getDataRefModel() instanceof ColumnRefModel) {
         ref = new ColumnRef(this.getDataRefModel().createDataRef());
      }
      else {
         ColumnRef temp = new ColumnRef();
         temp.setAlias(this.getAlias());
         temp.setWidth(this.getWidth());
         temp.setVisible(this.isVisible());
         temp.setValid(this.isValid());
         temp.setSQL(this.isSql());
         temp.setDataType(this.getDataType());
         temp.setDescription(this.getDescription());
         temp.setView(this.getView());

         if(this.getDataRefModel() != null) {
            temp.setDataRef(this.getDataRefModel().createDataRef());
         }

         ref = temp;
      }

      return ref;
   }

   private DataRefModel refModel;
   private String alias;
   private int width;
   private boolean visible;
   private boolean valid;
   private boolean sql;
   private String desc;
   private int order;

   @Component
   public static final class ColumnRefModelFactory
      extends DataRefModelWrapperFactory<ColumnRef, ColumnRefModel>
   {
      @Override
      public Class<ColumnRef> getDataRefClass() {
         return ColumnRef.class;
      }

      @Override
      public ColumnRefModel createDataRefModel0(ColumnRef dataRef) {
         return new ColumnRefModel(dataRef);
      }
   }
}
