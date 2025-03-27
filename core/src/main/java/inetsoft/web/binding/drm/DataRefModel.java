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

import com.fasterxml.jackson.annotation.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.*;

import java.io.Serializable;

/**
 * DataRefModel holding a reference to a SQL expression or an attribute.
 *
 * @version 12.3
 * @author  InetSoft Technology Corp.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY,
              property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = BDimensionRefModel.class, name = "BDimensionRef"),
   @JsonSubTypes.Type(value = BAggregateRefModel.class, name = "BAggregateRefModel"),
   @JsonSubTypes.Type(value = AggregateRefModel.class, name = "AggregateRef"),
   @JsonSubTypes.Type(value = AliasDataRefModel.class, name = "AliasDataRefModel"),
   @JsonSubTypes.Type(value = AttributeRefModel.class, name = "AttributeRef"),
   @JsonSubTypes.Type(value = BaseFieldModel.class, name = "BaseField"),
   @JsonSubTypes.Type(value = CalculateRefModel.class, name = "CalculateRef"),
   @JsonSubTypes.Type(value = ChartGeoRefModel.class, name = "geo"),
   @JsonSubTypes.Type(value = ChartAggregateRefModel.class, name = "aggregate"),
   @JsonSubTypes.Type(value = ChartDimensionRefModel.class, name = "dimension"),
   @JsonSubTypes.Type(value = AllChartAggregateRefModel.class, name = "allaggregate"),
   @JsonSubTypes.Type(value = ColumnRefModel.class, name = "ColumnRef"),
   @JsonSubTypes.Type(value = FormRefModel.class, name = "FormRef"),
   @JsonSubTypes.Type(value = DateRangeRefModel.class, name = "DateRangeRef"),
   @JsonSubTypes.Type(value = ExpressionRefModel.class, name = "ExpressionRef"),
   @JsonSubTypes.Type(value = FormulaFieldModel.class, name = "FormulaField"),
   @JsonSubTypes.Type(value = GroupRefModel.class, name = "GroupRef"),
   @JsonSubTypes.Type(value = NumericRangeRefModel.class, name = "NumericRangeRef"),
   @JsonSubTypes.Type(value = NamedRangeRefModel.class, name = "NamedRangeRefModel")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface DataRefModel extends Serializable {
   /**
    * Get the ref type.
    *
    * @return the ref type, one of NONE, DIMENSION and MEASURE.
    */
   int getRefType();

   /**
    * Set the ref type.
    *
    * @param refType the ref type to be set.
    */
   void setRefType(int refType);

   /**
    * Get the name of the field.
    *
    * @return the name of the field.
    */
   String getName();

   /**
    * Get the name of the field.
    *
    * @param name the name to be set.
    */
   void setName(String name);

   /**
    * Get the data type.
    *
    * @return the data type defined in XSchema.
    */
   String getDataType();

   /**
    * Set the data type.
    *
    * @param dataType, the data type to be set.
    */
   void setDataType(String dataType);

   /**
    * Get the default formula.
    */
   String getDefaultFormula();

   /**
    * Set the default formula.
    */
   void setDefaultFormula(String defaultFormula);

   /**
    * Check if the attribute is an expression.
    *
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   boolean isExpression();

   /**
    * Set if the attribute is an expression.
    *
    * @param expression, <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   void setExpression(boolean expression);

   /**
    * Get the referenced attribute.
    *
    * @return the name of the attribute.
    */
   String getAttribute();

   /**
    * Set the name of attribute.
    *
    * @param attribute, the name to be set.
    */
   void setAttribute(String attribute);

   /**
    * Get the attribute's parent entity.
    *
    * @return the name of the entity.
    */
   String getEntity();

   /**
    * Set the attribute's parent entity.
    *
    * @param entity, the name of the entity.
    */
   void setEntity(String entity);

   /**
    * Determine if the entity is blank.
    *
    * @return <code>true</code> if entity is <code>null</code> or entity is
    *         equal to empty string ("").
    */
   boolean isEntityBlank();

   /**
    * Set if the entity is blank.
    *
    * @param entityBlank, <code>true</code> if entity is <code>null</code> or entity is
    *         equal to empty string ("").
    */
   void setEntityBlank(boolean entityBlank);

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field.
    */
   String getView();

   /**
    * Set the view representation of this field.
    *
    * @param view, the view to be set.
    */
   void setView(String view);

   /**
    * Create a data ref.
    */
   DataRef createDataRef();
}
