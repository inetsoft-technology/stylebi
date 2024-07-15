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

import inetsoft.report.internal.binding.BaseField;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

/**
 * Base field represents a column bind to the table.
 *
 * @version 12.3.
 * @author InetSoft Technology Corp
 */
public class BaseFieldModel extends AbstractDataRefModel {
   public BaseFieldModel() {
   }

   public BaseFieldModel(BaseField dataRef) {
      super(dataRef);

      setSource(dataRef.getSource());
      setGfld(dataRef.isGroupField());
      setVisible(dataRef.isVisible());
      setOrder(dataRef.getOrder());
      setDescription(dataRef.getDescription());
      setOption(dataRef.getOption());
   }

   public String getSource() {
      return source;
   }

   public void setSource(String src) {
      this.source = src;
   }

   public String getDescription() {
      return desc;
   }

   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Check if is group field.
    */
   public boolean isGfld() {
      return gfld;
   }

   /**
    * Set whether is a group field.
    */
   public void setGfld(boolean gfld) {
      this.gfld = gfld;
   }

   /**
    * Check if is visible.
    */
   public boolean isVisible() {
      return vis;
   }

   /**
    * Set whether is a field visible.
    */
   public void setVisible(boolean vis) {
      this.vis = vis;
   }

   /**
    * Get order.
    */
   public int getOrder() {
      return ord;
   }

   /**
    * Set order.
    */
   public void setOrder(int ord) {
      this.ord = ord;
   }

   public int getOption() {
      return option;
   }

   public void setOption(int option) {
      this.option = option;
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      BaseField ref = new BaseField(this.getEntity(), this.getAttribute());
      ref.setDataType(this.getDataType());
      ref.setSource(this.getSource());
      ref.setVisible(this.isVisible());
      ref.setOrder(this.getOrder());
      ref.setDescription(this.getDescription());
      ref.setDefaultFormula(this.getDefaultFormula());
      ref.setGroupField(this.isGfld());
      ref.setOption(this.getOption());
      ref.setRefType(this.getRefType());
      ref.setView(this.getView());

      return ref;
   }

   @Component
   public static final class BaseFieldModelFactory
      extends DataRefModelFactory<BaseField, BaseFieldModel>
   {
      @Override
      public Class<BaseField> getDataRefClass() {
         return BaseField.class;
      }

      @Override
      public BaseFieldModel createDataRefModel(BaseField dataRef) {
         return new BaseFieldModel(dataRef);
      }
   }

   private String source;
   private boolean gfld;
   private boolean vis;
   private int ord;
   private String desc;
   private int option;
}
