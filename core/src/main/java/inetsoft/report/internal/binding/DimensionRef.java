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
package inetsoft.report.internal.binding;

import inetsoft.report.StyleConstants;
import inetsoft.uql.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Enumeration;

/**
 * Class holding a reference to an cube dimension.
 *
 * @author  InetSoft Technology Corp.
 * @since   6.0
 */
public class DimensionRef extends AbstractDataRef implements Field {
   /**
    * Unknown scope.
    */
   public static final int UNKNOWN_SCOPE = -2;

   /**
    * Constructor create a blank DimensionRef.
    */
   public DimensionRef() {
      super();
   }

   /**
    * Constructor create a blank DimensionRef.
    */
   public DimensionRef(DimensionRef ref) {
      this.datasource = ref.getDataSource();
      this.cube = ref.getCube();
      this.name = ref.getName();
      this.level = ref.getLevel();
      this.scope = ref.getScope();
      this.filter = ref.getFilter();
   }

   /**
    * Get self.
    */
   @Override
   public Field getField() {
      return this;
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      return CUBE_DIMENSION;
   }

   /**
    * Set the visibility of the field.
    *
    * @param visible true if is visible, false otherwise
    */
   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check the visibility of the field.
    *
    * @return true if is visible, false otherwise
    */
   @Override
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the sorting order of the field.
    *
    * @param order the specified sorting order defined in StyleConstants
    */
   @Override
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Get the sorting order of the field.
    *
    * @return the sorting order defined in StyleConstants
    */
   @Override
   public int getOrder() {
      return order;
   }

   /**
    * Set the data type of the field.
    *
    * @param type the specified data type defined in XSchema
    */
   @Override
   public void setDataType(String type) {
      this.dtype = type;
   }

   /**
    * Get the data type of the field.
    *
    * @return the data type defined in XSchema
    */
   @Override
   public String getDataType() {
      return dtype;
   }

   /**
    * Get the type node presentation of this field.
    *
    * @return the type node
    */
   @Override
   public XTypeNode getTypeNode() {
      return XSchema.createPrimitiveType(getDataType());
   }

   /**
    * Get a list of all the entities referenced by this object.
    *
    * @return an Enumeration containing the entity names.
    */
   @Override
   public Enumeration getEntities() {
      return null;
   }

   /**
    * Get the name of the cube.
    *
    * @return the cube name.
    */
   @Override
   public String getEntity() {
      return getCube();
   }

   /**
    * Get the name of the attribute to which this object holds a reference.
    *
    * @return the dimension name.
    */
   @Override
   public String getAttribute() {
      return getName();
   }

   /**
    * Get the name of the OLAP data source to which this reference refers.
    *
    * @return the name of a OLAP data source.
    */
   public String getDataSource() {
      return datasource;
   }

   /**
    * Set the name of the OLAP data source to which this reference refers.
    *
    * @param datasource the name of a OLAP data source.
    */
   public void setDataSource(String datasource) {
      this.datasource = datasource;
   }

   /**
    * Get the name of the cube to which this reference refers.
    *
    * @return the name of cube
    */
   public String getCube() {
      return cube;
   }

   /**
    * Set the name of the cube to which this reference refers.
    *
    * @param cube the name of cube
    */
   public void setCube(String cube) {
      this.cube = cube;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get the name of the dimension.
    *
    * @return the name of the dimension
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of the dimension.
    *
    * @param name the name of the dimension
    */
   public void setName(String name) {
      this.name = name;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get the dimension level.
    *
    * @return the dimension level
    */
   public String getLevel() {
      return level;
   }

   /**
    * Set the dimension level.
    *
    * @param level the dimensin level
    */
   public void setLevel(String level) {
      this.level = level;
   }

   /**
    * Get the dimension level scope.
    *
    * @return the dimension level scope
    */
   public int getScope() {
      // @by tonyy, bug1170060521593, try fixing unknown scope (old version)
      if(scope == UNKNOWN_SCOPE) {
         try {
            XRepository repository = XFactory.getRepository();
            XDomain domain = repository.getDomain(getDataSource());
            XCube cube = domain.getCube(getCube());
            XDimension xdim = cube.getDimension(getName());

            for(int i = 0; i < xdim.getLevelCount(); i++) {
               if(getLevel().equals(xdim.getLevelAt(i).getName())) {
                  scope = i;
                  break;
               }
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to get scope of dimension", ex);
         }
      }

      return scope;
   }

   /**
    * Set the dimension level scope.
    *
    * @param scope the dimensin level scope
    */
   public void setScope(int scope) {
      this.scope = scope;
   }

   /**
    * Get the filter.
    *
    * @return the filter
    */
   public Object getFilter() {
      return filter;
   }

   /**
    * Set the filter.
    *
    * @param filter the filter
    */
   public void setFilter(Object filter) {
      this.filter = filter;
   }

   /**
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isDate() {
      XTypeNode node = getTypeNode();
      return node != null && node.isDate();
   }

   /**
    * Check if is empty.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isEmpty() {
      return name == null || name.length() == 0;
   }

   /**
    * Set whether the field is processed in query generator.
    */
   @Override
   public void setProcessed(boolean processed) {
      this.processed = processed;
   }

   /**
    * Check if the field is processed in query generator.
    */
   @Override
   public boolean isProcessed() {
      return processed;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      DimensionRef ref = (DimensionRef) super.clone();
      return ref;
   }

   /**
    * Write the attributes of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print("visible=\"" + isVisible() + "\" ");
      writer.print("dataType=\"" + getDataType() + "\" ");
      writer.print("order=\"" + getOrder() + "\" ");

      if(getDataSource() != null) {
         writer.print("datasource=\"" + Tool.escape(getDataSource()) + "\" ");
      }

      writer.print("cube=\"" + Tool.escape(getCube()) + "\" ");
      writer.print("name=\"" + Tool.escape(getName()) + "\" ");
      writer.print("level=\"" + Tool.escape(getLevel()) + "\" ");
      writer.print("scope=\"" + getScope() + "\" ");

      if(getFilter() != null) {
         writer.print("filter=\"" + Tool.escape(getFilter().toString()) + "\" ");
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      String val = null;

      if((val = Tool.getAttribute(tag, "visible")) != null) {
         setVisible(val.equals("true"));
      }

      if((val = Tool.getAttribute(tag, "dataType")) != null) {
         setDataType(val);
      }

      if((val = Tool.getAttribute(tag, "order")) != null) {
         setOrder(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(tag, "datasource")) != null) {
         setDataSource(val);
      }

      if((val = Tool.getAttribute(tag, "cube")) != null) {
         setCube(val);
      }

      if((val = Tool.getAttribute(tag, "name")) != null) {
         setName(val);
      }

      if((val = Tool.getAttribute(tag, "level")) != null) {
         setLevel(val);
      }

      if((val = Tool.getAttribute(tag, "scope")) != null) {
         setScope(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(tag, "filter")) != null) {
         setFilter(val);
      }
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(super.equals(obj)) {
         if(!(obj instanceof DimensionRef)) {
            return false;
         }

         DimensionRef ref2 = (DimensionRef) obj;

         if(getDataSource() == null) {
            if(ref2.getDataSource() != null) {
               return false;
            }
         }
         else if(!getDataSource().equals(ref2.getDataSource())) {
            return false;
         }

         return true;
      }

      return false;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return getName();
   }

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field
    */
   @Override
   public String toView() {
      return toString();
   }

   /**
    * Create an embedded field.
    * @return the created embedded field.
    */
   @Override
   public Field createEmbeddedField() {
      return (Field) clone();
   }

   /**
    * Set whether is a group field.
    */
   @Override
   public void setGroupField(boolean gfld) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if is group field.
    */
   @Override
   public boolean isGroupField() {
      return false;
   }

   private boolean visible = true;
   private String dtype = XSchema.STRING;
   private int order = StyleConstants.SORT_NONE;
   private String datasource = null;
   private String cube = null;
   private String name = null;
   private String level = null;
   private int scope = -1;
   private Object filter = null;
   private boolean processed = false;

   private static final Logger LOG = LoggerFactory.getLogger(DimensionRef.class);
}
