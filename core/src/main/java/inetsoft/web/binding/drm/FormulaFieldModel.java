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

import inetsoft.report.internal.binding.FormulaField;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

/**
 * Formula Field represents a formula script field bind to the table.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class FormulaFieldModel extends AbstractDataRefModel {
   public FormulaFieldModel() {
   }

   public FormulaFieldModel(FormulaField ref) {
      super(ref);
      setExp(ref.getExpression());
      setFormulaType(ref.getType());
      setVisible(ref.isVisible());
      setOrder(ref.getOrder());
   }

   public String getExp() {
      return exp;
   }

   public void setExp(String exp) {
      this.exp = exp;
   }

   public String getFormulaType() {
      return formulaType;
   }

   public void setFormulaType(String formulaType) {
      this.formulaType = formulaType;
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

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      FormulaField ref = new FormulaField(this.getName(), this.getExp());
      ref.setDataType(this.getDataType());
      ref.setType(this.getFormulaType());
      ref.setOrder(this.getOrder());
      ref.setVisible(this.isVisible());

      return ref;
   }

   private String exp;
   private String formulaType;
   private int ord;
   private boolean vis;

   @Component
   public static final class FormulaFieldModelFactory
      extends DataRefModelFactory<FormulaField, FormulaFieldModel>
   {
      @Override
      public Class<FormulaField> getDataRefClass() {
         return FormulaField.class;
      }

      @Override
      public FormulaFieldModel createDataRefModel(FormulaField dataRef) {
         return new FormulaFieldModel(dataRef);
      }
   }
}
