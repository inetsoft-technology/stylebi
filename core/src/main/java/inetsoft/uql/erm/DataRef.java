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
package inetsoft.uql.erm;

import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.DataSerializable;
import inetsoft.util.XMLSerializable;

import java.io.*;
import java.util.Enumeration;

/**
 * DataRef holding a reference to a SQL expression or an attribute.
 *
 * @version 10.0
 * @author  InetSoft Technology Corp.
 */
public interface DataRef
   extends Cloneable, Serializable, XMLSerializable, Comparable, DataSerializable
{
   /**
    * Normal data ref.
    */
   int NONE = 0;
   /**
    * Dimension data ref.
    */
   int DIMENSION = 1;
   /**
    * Measure data ref.
    */
   int MEASURE = 2;
   /**
    * Cube data ref.
    */
   int CUBE = 4;
   /**
    * Model cube data ref.
    */
   int MODEL = 8;
   /**
    * Time dimension data ref.
    */
   int TIME = 16;
   /**
    * Cube dimension data ref.
    */
   int CUBE_DIMENSION = CUBE | DIMENSION;
   /**
    * Cube measure data ref.
    */
   int CUBE_MEASURE = CUBE | MEASURE;
   /**
    * Cube time dimension data ref.
    */
   int CUBE_TIME_DIMENSION = CUBE | TIME | DIMENSION;
   /**
    * Cube model dimension data ref.
    */
   int CUBE_MODEL_DIMENSION	= CUBE | MODEL | DIMENSION;
   /**
    * Cube model time dimension data ref.
    */
   int CUBE_MODEL_TIME_DIMENSION =
      CUBE | MODEL | TIME | DIMENSION;
   /**
    * Calculate based on aggregate value.
    */
   int AGG_CALC = 32;
   /**
    * Model aggregate expression
    */
   int AGG_EXPR = 64;

   /**
    * Get the ref type.
    *
    * @return the ref type, one of NONE, DIMENSION and MEASURE.
    */
   int getRefType();

   /**
    * Get the default formula.
    */
   String getDefaultFormula();

   /**
    * Check if the attribute is an expression.
    */
   default boolean isExpression() {
      return false;
   }

   /**
    * Check if the data ref is blank.
    * @return <tt>true</tt> if blank, <tt>false</tt> otherwise.
    */
   boolean isEmpty();

   /**
    * Get the attribute's parent entity.
    *
    * @return the name of the entity.
    */
   String getEntity();

   /**
    * Get the attribute's parent entity.
    *
    * @return an Enumeration with the name of the entity.
    */
   Enumeration getEntities();

   /**
    * Get the referenced attribute.
    *
    * @return the name of the attribute.
    */
   String getAttribute();

   /**
    * Get a list of all attributes that are referenced by this object.
    *
    * @return an Enumeration containing AttributeRef objects.
    */
   Enumeration getAttributes();

   /**
    * Determine if the entity is blank.
    *
    * @return <code>true</code> if entity is <code>null</code> or entity is
    *         equal to empty string ("").
    */
   boolean isEntityBlank();

   /**
    * Get the name of the field.
    *
    * @return the name of the field.
    */
   String getName();

   /**
    * Get the data type.
    *
    * @return the data type defined in XSchema.
    */
   String getDataType();

   /**
    * @return true if the data type is set directly on this data ref, false otherwise.
    * @exclude
    */
   default boolean isDataTypeSet() {
      return false;
   }

   /**
    * @return true if the data type is not null.
    * @exclude
    */
   default boolean hasDataType() {
      return true;
   }

   /**
    * Get the type node.
    * @return the type node of the column ref.
    */
   XTypeNode getTypeNode();

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field.
    */
   String toView();

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   Object clone();

   /**
    * Compare two column refs.
    * @param strict true to compare all properties of ColumnRef. Otherwise
    * only entity and attribute are compared.
    */
   boolean equals(Object obj, boolean strict);

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   void writeData(DataOutputStream output) throws IOException;

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   boolean parseData(DataInputStream input);

   /**
    * Get the address.
    */
   int addr();
}
