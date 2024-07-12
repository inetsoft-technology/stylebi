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
import inetsoft.util.Tool;

/**
 * Abstract class holding a reference to a SQL expression or an attribute.
 *
 * @version 12.3
 * @author  InetSoft Technology Corp
 */
public abstract class AbstractDataRefModel implements DataRefModel {
   /**
   * Constructor.
   */
   public AbstractDataRefModel() {
   }

   /**
   * Constructor.
   */
   public AbstractDataRefModel(DataRef ref) {
      setName(ref.getName());
      setView(Tool.localize(ref.toView()));
      setDataType(ref.getDataType());
      setRefType(ref.getRefType());
      setDefaultFormula(ref.getDefaultFormula());
      setAttribute(ref.getAttribute());
      setEntity(ref.getEntity());
      setExpression(ref.isExpression());
      setEntityBlank(ref.isEntityBlank());
   }

   /**
    * Get the ref type.
    *
    * @return the ref type, one of NONE, DIMENSION and MEASURE.
    */
   @Override
   public int getRefType() {
      return refType;
   }

   /**
    * Set the ref type.
    *
    * @param refType, the type to be set..
    */
   @Override
   public void setRefType(int refType) {
      this.refType = refType;
   }

   /**
    * Get the name of the field.
    *
    * @return the name of the field.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of the field.
    *
    * @param name, the name to be set.
    */
   @Override
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the data type.
    *
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return dataType;
   }

   /**
    * Set the data type.
    *
    * @param dataType, the data type to be set.
    */
   @Override
   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   /**
    * Get the default formula.
    */
   @Override
   public String getDefaultFormula() {
      return defaultFormula;
   }

   /**
    * Set the default formula.
    */
   @Override
   public void setDefaultFormula(String defaultFormula) {
      this.defaultFormula = defaultFormula;
   }

   /**
    * Check if the attribute is an expression.
    *
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return expression;
   }

   /**
    * Set if the attribute is an expression.
    *
    * @param expression <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public void setExpression(boolean expression) {
      this.expression = expression;
   }

   /**
    * Get the referenced attribute.
    *
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return attribute;
   }

   /**
    * Set the referenced attribute.
    *
    * @param attribute, the name of the attribute to be set.
    */
   @Override
   public void setAttribute(String attribute) {
      this.attribute = attribute;
   }

   /**
    * Get the attribute's parent entity.
    *
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return entity;
   }

   /**
    * Set the attribute's parent entity.
    *
    * @param the name of the entity to be set.
    */
   @Override
   public void setEntity(String entity) {
      this.entity = entity;
   }

   /**
    * Determine if the entity is blank.
    *
    * @return <code>true</code> if entity is <code>null</code> or entity is
    *         equal to empty string ("").
    */
   @Override
   public boolean isEntityBlank() {
      return entityBlank;
   }

   /**
    * Set if the entity is blank.
    *
    * @param entityBlank, <code>true</code> if entity is <code>null</code> or entity is
    *                     equal to empty string ("").
    */
   @Override
   public void setEntityBlank(boolean entityBlank) {
      this.entityBlank = entityBlank;
   }

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field.
    */
   @Override
   public String getView() {
      return view;
   }

   /**
    * Set the view representation of this field.
    *
    * @param view, the view name to be set.
    */
   @Override
   public void setView(String view) {
      this.view = stripOuterPrefix(view);
   }

   public static String stripOuterPrefix(String view) {
      return view != null && view.contains("OUTER") ? view.replaceAll("(OUTER_|_\\d+)", "") : view;
   }

   /**
    * Create a data ref.
    */
   @Override
   public abstract DataRef createDataRef();

   private int refType;
   private String name;
   private String dataType;
   private String defaultFormula;
   private boolean expression;
   private String attribute;
   private String entity;
   private boolean entityBlank;
   private String view;
}
