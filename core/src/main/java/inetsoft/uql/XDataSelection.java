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
package inetsoft.uql;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * An XDataSelection represents a selection of query columns or data model
 * attributes and a list of filter condtions to be applied to the resulting
 * data. To use XDataSelection with query columns, use AttributeRef objects
 * where the attribute property is the column name and the entity property is
 * <code>null</code>.
 * @see inetsoft.uql.erm.AttributeRef
 */
public class XDataSelection implements Cloneable, Serializable, XDynamicQuery {
   /**
    * Flag indicating that a given condition should be or'ed to the associated
    * column/attribute.
    */
   public static final int CONDITION_OR = JunctionOperator.OR;

   /**
    * Flag indicating that a given condition should be and'ed to the associated
    * column/attribute.
    */
   public static final int CONDITION_AND = JunctionOperator.AND;

   /**
    * Construct a new instance of DataSelection that is intended to be used
    * with the specified type of source data.
    *
    * @param fromModel a flag indicating where the data will come from.
    * <code>true</code> indicates that the selection will use the attributes
    * of a data model and <code>false</code> that a defined query will be used.
    *
    * @deprecated Use the XDataSelection(String) constructor instead.
    */
   @Deprecated
   public XDataSelection(boolean fromModel) {
      this(fromModel ? MODEL_TYPE : QUERY_TYPE);
   }

   /**
    * Construct a new instance of DataSelection that is intended to be used
    * with the specified type of source data.
    *
    * @param type the type of data selection being created. This may be
    *             <code>QUERY_TYPE</code> or <code>MODEL_TYPE</code>.
    */
   public XDataSelection(String type) {
      this.type = type;
   }

   /**
    * Sets the name of the source of the data selection. This should be the
    * name of either a defined query or data model.
    * @param source the name of the source of the data selection.
    */
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Get the name of the source of the data selection. This is the name of
    * either a defined query or data model.
    *
    * @return the name of the source of the data selection.
    */
   @Override
   public String getSource() {
      return source;
   }

   /**
    * Set whether the data selection comes from a data model or query.
    *
    * @param fromModel <code>true</code> if this is a selection of data model
    * attributes, <code>false</code> for a selection of query columns.
    *
    * @deprecated use the setType() method to set the type of this selection.
    */
   @Deprecated
   public void setFromModel(boolean fromModel) {
      if(MODEL_TYPE.equals(type) && fromModel ||
        !MODEL_TYPE.equals(type) && !fromModel)
      {
         return;
      }

      if(fromModel) {
         type = MODEL_TYPE;
      }
      else {
         type = QUERY_TYPE;
      }

      clear();
   }

   /**
    * Determine if the data selection comes from a data model or query.
    *
    * @return <code>true</code> if this is a selection of data model
    * attributes, <code>false</code> for a selection of query columns.
    *
    * @deprecated use the getType() method to determine the type of this
    *             selection.
    */
   @Deprecated
   public boolean isFromModel() {
      return MODEL_TYPE.equals(type);
   }

   /**
    * Get the type of this selection.
    *
    * @return the type flag of this selection.
    */
   @Override
   public String getType() {
      return type;
   }

   /**
    * Set the type of this selection.
    *
    * @param type the type flag of this selection.
    */
   public void setType(String type) {
      if(this.type.equals(type)) {
         return;
      }

      this.type = type;
      clear();
   }

   /**
    * Clear all attributes and conditions from this selection.
    */
   public void clear() {
      conditions.removeAllItems();
      attrs.clear();
      hiddens.clear();
   }

   /**
    * Add a data model attribute or expression to the selection.
    * @param attribute an DataRef object describing a data model attribute
    * or expression.
    */
   public void addAttribute(DataRef attribute) {
      attrs.addAttribute(attribute);
   }

   /**
    * Check if an attribute or expression is already defined in the selection.
    */
   public boolean containsAttribute(DataRef attribute) {
      return attrs.containsAttribute(attribute);
   }

   /**
    * Remove the specified attribute or expression from the selection.
    * @param attribute an DataRef object describing a data model attribute
    * or expression.
    */
   public void removeAttribute(DataRef attribute) {
      attrs.removeAttribute(attribute);
   }

   /**
    * Get a list of all attributes and expression in this selection.
    * @return a collection of DataRef objects.
    */
   public Enumeration getAttributes() {
      return attrs.getAttributes();
   }

   /**
    * Get an attribute or an expression.
    * @param idx attribute index.
    */
   public DataRef getAttribute(int idx) {
      return attrs.getAttribute(idx);
   }

   /**
    * Get the total number of attributes and expressions in this selection.
    * @return total number of attributes and expressions in this selection.
    */
   public int getAttributeCount() {
      return attrs.getAttributeCount();
   }

   /**
    * Remove all attributes and expressions from the selection.
    */
   public void removeAllAttributes() {
      attrs.removeAllAttributes();
   }

   /**
    * Add the data selection's user variables to the list.
    */
   public UserVariable[] getAllVariables() {
      return conditions.getAllVariables();
   }

   /**
    * Write this data selection to XML.
    * @param writer the stream to output the XML text to.
    */
   public void writeXML(PrintWriter writer) {
      if(source != null) {
         writer.println("<dataselection type=\"" + type + "\">");
         writer.println("<source><![CDATA[" + (source == null ? "" : source) +
                        "]]></source>");

         attrs.writeXML(writer);
         hiddens.writeXML(writer);
         conditions.writeXML(writer);

         if(group != null) {
            group.writeXML(writer);
         }

         writer.println("</dataselection>");
      }
   }

   /**
    * Read in the XML representation of this object.
    * @param tag the XML element representing this object.
    */
   public void parseXML(Element tag) throws Exception, DOMException {
      String attr = Tool.getAttribute(tag, "frommodel");

      if(attr != null) {
         setFromModel(Boolean.valueOf(attr).booleanValue());
      }

      if((attr = Tool.getAttribute(tag, "type")) != null) {
         setType(attr);
      }

      NodeList nlist = Tool.getChildNodesByTagName(tag, "source");

      if(nlist.getLength() > 0) {
         attr = Tool.getValue((Element) nlist.item(0));
         attr = (attr == null || attr.equals("")) ? null : attr;
         setSource(attr);
      }

      nlist = Tool.getChildNodesByTagName(tag, "ColumnSelection"); //6.0

      if(nlist.getLength() > 0) {
         attrs.parseXML((Element) nlist.item(0));
         hiddens.parseXML((Element) nlist.item(1));
      }
      else {
         nlist = Tool.getChildNodesByTagName(tag, "attributerefs"); //5.0

         if(nlist.getLength() > 0) {
            attrs.parseXML((Element) nlist.item(0));
         }
         else {
            nlist = Tool.getChildNodesByTagName(tag, "attributeref"); //4.2

            if(nlist.getLength() > 0) {
               attrs.parseXML(tag);
            }
         }
      }

      nlist = Tool.getChildNodesByTagName(tag, "conditions");

      if(nlist.getLength() > 0) {
         conditions.parseXML((Element) nlist.item(0));
      }
      else {
         nlist = Tool.getChildNodesByTagName(tag, "condition");

         if(nlist.getLength() > 0) {
            conditions.parseXML(tag);
         }
      }

      try {
         group.parseXML(tag);
      }
      catch(Exception ex) {
         group = null;
      }
   }

   @Override
   public Object clone() {
      XDataSelection sel = new XDataSelection(type);

      if(source != null) {
         sel.setSource(new String(source));
      }

      sel.setColumnSelection((ColumnSelection) attrs.clone());
      sel.setHiddenColumns((ColumnSelection) hiddens.clone());
      sel.setConditionList((ConditionList) conditions.clone());
      sel.allRows = (HashSet) allRows.clone();

      if(group != null) {
         sel.setGroup((GroupInfo) group.clone());
      }

      return sel;
   }

   /**
    * Set the column selection object.
    */
   public void setColumnSelection(ColumnSelection sel) {
      this.attrs = sel;
   }

   /**
    * Get the column selection object.
    */
   public ColumnSelection getColumnSelection() {
      return attrs;
   }

   /**
    * Set the hidden column selection object.
    */
   public void setHiddenColumns(ColumnSelection sel) {
      this.hiddens = sel;
   }

   /**
    * Get the hidden column selection object.
    */
   public ColumnSelection getHiddenColumns() {
      return hiddens;
   }

   /**
    * Set the row selection object.
    */
   public void setConditionList(ConditionList sel) {
      this.conditions = sel;
   }

   /**
    * Get the row selection object.
    */
   public ConditionList getConditionList() {
      return conditions;
   }

   /**
    * Get group attribute of binding.
    */
   public GroupInfo getGroup() {
      return group;
   }

   /**
    * Set group attribute of binding.
    */
   public void setGroup(GroupInfo group) {
      this.group = group;
   }

   /**
    * Set whether to include all rows from an entity. This is equivalent to
    * an outer join in SQL.
    */
   public void setAllRows(String entity, boolean flag) {
      if(flag) {
         allRows.add(entity);
      }
      else {
         allRows.remove(entity);
      }
   }

   /**
    * Check if all rows should be included in output for the specified entity.
    */
   public boolean isAllRows(String entity) {
      return entity != null && allRows.contains(entity);
   }

   /**
    * Determine if the query result should only contain unique rows.
    *
    * @return <code>true</code> if the query is distinct.
    */
   public boolean isDistinct() {
      return distinct;
   }

   /**
    * Set whether the query result should only contain unique rows.
    *
    * @param distinct <code>true</code> if the query is distinct.
    */
   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   /**
    * Determine if the grouping should be performed by the database (in a
    * SQL GROUP BY clause) or during post-processing by Style Report.
    *
    * @return <code>true</code> if the grouping should be performed by the
    *         database; <code>false</code> if it should be performed by
    *         StyleReport.
    */
   public boolean isSQLGroup() {
      return sqlGroup;
   }

   /**
    * Set whether the grouping should be performed by the database (in a
    * SQL GROUP BY clause) or during post-processing by Style Report.
    *
    * @param sqlGroup <code>true</code> if the grouping should be performed by
    *                 the database; <code>false</code> if it should be
    *                 performed by StyleReport.
    */
   public void setSQLGroup(boolean sqlGroup) {
      this.sqlGroup = sqlGroup;
   }

   /**
    * Set a property value. Property is generic interface for attaching
    * additional information to a query object. Properties are transient and
    * is not saved as part of the query definition.
    */
   @Override
   public void setProperty(String name, Object val) {
      propmap.put(name, val);
   }

   /**
    * Get a property value.
    */
   @Override
   public Object getProperty(String name) {
      return propmap.get(name);
   }

   public String toString() {
      StringWriter s = new StringWriter();
      PrintWriter p = new PrintWriter(s);

      writeXML(p);

      return s.toString();
   }

   private String source;
   private String type = QUERY_TYPE;

   // keep the order of the attributes
   private ColumnSelection attrs = new ColumnSelection();
   // keep the hidden columns
   private ColumnSelection hiddens = new ColumnSelection();
   // AttributeRef -> Vector
   private ConditionList conditions = new ConditionList();

   private GroupInfo group;    // group attribute of binding
   private HashSet allRows = new HashSet(); // entities
   private boolean distinct = false;
   private boolean sqlGroup = false;
   private HashMap propmap = new HashMap(); // properties
}

