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
package inetsoft.uql.tabular;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Describes the layout information for the editor in TabularView
 *
 * @author InetSoft Technology Corp
 * @version 12.2
 */
@JsonDeserialize(using = TabularEditor.Deserializer.class)
public class TabularEditor implements XMLSerializable {
   /**
    * Gets the type of the editor.
    *
    * @return the editor type.
    */
   public Type getType() {
      return type;
   }

   /**
    * Sets the type of the editor.
    *
    * @param type the editor type.
    */
   public void setType(Type type) {
      this.type = type;
   }

   /**
    * Gets the type of elements in the list editor
    *
    * @return the subtype
    */
   public Type getSubtype() {
      return subtype;
   }

   /**
    * Sets the type of elements in the list editor
    *
    * @param subtype the subtype
    */
   public void setSubtype(Type subtype) {
      this.subtype = subtype;
   }

   /**
    * Gets the layout row in which to place the editor.
    *
    * @return the row index.
    */
   public int getRow() {
      return row;
   }

   /**
    * Sets the layout row in which to place the editor.
    *
    * @param row the row index.
    */
   public void setRow(int row) {
      this.row = row;
   }

   /**
    * Gets the layout column in which to place the editor.
    *
    * @return the column index.
    */
   public int getCol() {
      return col;
   }

   /**
    * Sets the layout column in which to place the editor.
    *
    * @param col the column index.
    */
   public void setCol(int col) {
      this.col = col;
   }

   /**
    * Gets whether or not the lines wrap when they get too wide
    * @return true if they wrap, false is not
    */
   public boolean isLineWrap() { return lineWrap; }

   /**
    * Set line wrapping
    * @param lineWrap
    */
   public void setLineWrap(boolean lineWrap) {
      this.lineWrap = lineWrap;
   }

   /**
    * Gets the alignment for the editor.
    *
    * @return the alignment.
    */
   public ViewAlign getAlign() {
      return align;
   }

   /**
    * Sets the alignment for the editor.
    *
    * @param align the alignment.
    */
   public void setAlign(ViewAlign align) {
      this.align = align;
   }

   /**
    * Gets the vertical alignment for the editor.
    *
    * @return the vertical alignment.
    */
   public ViewAlign getVerticalAlign() {
      return verticalAlign;
   }

   /**
    * Sets the vertical alignment for the editor.
    *
    * @param verticalAlign the vertical alignment.
    */
   public void setVerticalAlign(ViewAlign verticalAlign) {
      this.verticalAlign = verticalAlign;
   }

   /**
    * Gets the height of the text component.
    *
    * @return the height in rows of text.
    */
   public int getRows() {
      return rows;
   }

   /**
    * Sets the height of the text component.
    *
    * @param rows the height in rows of text.
    */
   public void setRows(int rows) {
      this.rows = rows;
   }

   /**
    * Gets the width of the text component.
    *
    * @return the width in characters.
    */
   public int getColumns() {
      return columns;
   }

   /**
    * Sets the width of the text component.
    *
    * @param columns the width in characters.
    */
   public void setColumns(int columns) {
      this.columns = columns;
   }

   /**
    * Gets the tags used to populate the drop down list.
    *
    * @return the tags.
    */
   public String[] getTags() {
      return tags;
   }

   /**
    * Sets the tags used to populate the drop down list.
    *
    * @param tags the tags.
    */
   public void setTags(String[] tags) {
      this.tags = tags;
   }

   /**
    * Gets the labels used to populate the drop down list.
    *
    * @return the labels.
    */
   public String[] getLabels() {
      return labels;
   }

   /**
    * Sets the labels used to populate the drop down list.
    *
    * @param labels the labels.
    */
   public void setLabels(String[] labels) {
      this.labels = labels;
   }

   /**
    * Gets the name of the method used to populate the drop down list tags.
    *
    * @return the method name.
    */
   public String getTagsMethod() {
      return tagsMethod;
   }

   /**
    * Sets the name of the method used to populate the drop down list tags.
    *
    * @param tagsMethod the method name.
    */
   public void setTagsMethod(String tagsMethod) {
      this.tagsMethod = tagsMethod;
   }

   /**
    * Gets the names of the properties on which the editor depends.
    */
   public String[] getDependsOn() {
      return dependsOn;
   }

   /**
    * Sets the names of the properties on which the editor depends.
    */
   public void setDependsOn(String[] dependsOn) {
      this.dependsOn = dependsOn;
   }

   /**
    * Gets the name of the method used to determine if the editor is enabled.
    *
    * @return the enabled method.
    */
   public String getEnabledMethod() {
      return enabledMethod;
   }

   /**
    * Sets the name of the method used to determine if the editor is enabled.
    *
    * @param enabledMethod the enabled method.
    */
   public void setEnabledMethod(String enabledMethod) {
      this.enabledMethod = enabledMethod;
   }

   /**
    * Gets the flag that determines if the editor is enabled.
    *
    * @return <tt>true</tt> if enabled; <tt>false</tt> otherwise.
    */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Sets the flag that determines if the editor is enabled.
    *
    * @param enabled <tt>true</tt> if enabled; <tt>false</tt> otherwise.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * Gets the fully-qualified class name of the custom editor.
    *
    * @return the editor class name.
    */
   public String getCustomEditor() {
      return customEditor;
   }

   public String getVisibleMethod() {
      return visibleMethod;
   }

   public void setVisibleMethod(String visibleMethod) {
      this.visibleMethod = visibleMethod;
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Sets the fully-qualified class name of the custom editor.
    *
    * @param customEditor the editor class name.
    */
   public void setCustomEditor(String customEditor) {
      this.customEditor = customEditor;
   }

   /**
    * Gets the flag that indicates if the property is defined.
    *
    * @return <tt>true</tt> if defined; <tt>false</tt> otherwise.
    */
   public boolean isDefined() {
      return defined;
   }

   /**
    * Sets the flag that indicates if the property is defined.
    *
    * @param defined <tt>true</tt> if defined; <tt>false</tt> otherwise.
    */
   public void setDefined(boolean defined) {
      this.defined = defined;
   }

   /**
    * Gets the names of the editor-specific properties.
    *
    * @return the property names.
    */
   public String[] getEditorPropertyNames() {
      return editorPropertyNames;
   }

   /**
    * Sets the names of the editor-specific properties.
    *
    * @param editorPropertyNames the property names.
    */
   public void setEditorPropertyNames(String[] editorPropertyNames) {
      this.editorPropertyNames = editorPropertyNames;
   }

   /**
    * Gets the values of the editor-specific properties.
    *
    * @return the property values.
    */
   public String[] getEditorPropertyValues() {
      return editorPropertyValues;
   }

   /**
    * Sets the values of the editor-specific properties.
    *
    * @param editorPropertyValues the property values.
    */
   public void setEditorPropertyValues(String[] editorPropertyValues) {
      this.editorPropertyValues = editorPropertyValues;
   }

   /**
    * Gets the names of the methods used to get the editor-specific properties.
    *
    * @return the property method names.
    */
   public String[] getEditorPropertyMethods() {
      return editorPropertyMethods;
   }

   /**
    * Sets the names of the methods used to get the editor-specific properties.
    *
    * @param editorPropertyMethods the property method names.
    */
   public void setEditorPropertyMethods(String[] editorPropertyMethods) {
      this.editorPropertyMethods = editorPropertyMethods;
   }

   /**
    * Gets the fully-qualified class name of the property.
    *
    * @return the property class name.
    */
   public String getPropertyType() {
      return propertyType;
   }

   /**
    * Sets the fully-qualified class name of the property.
    *
    * @param propertyType the property class name.
    */
   public void setPropertyType(String propertyType) {
      this.propertyType = propertyType;
   }

   /**
    * Gets the type of elements in the array or property value.
    *
    * @return the subtype.
    */
   public String getPropertySubtype() {
      return propertySubtype;
   }

   /**
    * Sets the type of elements in the array or property value.
    *
    * @param propertySubtype the subtype.
    */
   public void setPropertySubtype(String propertySubtype) {
      this.propertySubtype = propertySubtype;
   }

   /**
    * Gets the value of the property.
    *
    * @return the property value.
    */
   public Object getValue() {
      return value;
   }

   /**
    * Sets the value of the property.
    *
    * @param value the property value.
    */
   public void setValue(Object value) {
      this.value = value;
   }

   public boolean isAutocomplete() {
      return autocomplete;
   }

   public void setAutocomplete(boolean autocomplete) {
      this.autocomplete = autocomplete;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tabularEditor>");
      writer.format("<type><![CDATA[%s]]></type>%n", type);

      if(subtype != null) {
         writer.format("<subtype><![CDATA[%s]]></subtype>%n", subtype);
      }

      writer.format("<row><![CDATA[%s]]></row>%n", row);
      writer.format("<col><![CDATA[%s]]></col>%n", col);

      if(align != null) {
         writer.format("<align><![CDATA[%s]]></align>%n", align);
      }

      if(verticalAlign != null) {
         writer.format("<verticalAlign><![CDATA[%s]]></verticalAlign>%n", verticalAlign);
      }

      writer.format("<rows><![CDATA[%s]]></rows>%n", rows);
      writer.format("<columns><![CDATA[%s]]></columns>%n", columns);

      if(tags != null) {
         writer.println("<tags>");

         for(String tag : tags) {
            writer.format("<tag><![CDATA[%s]]></tag>%n", tag);
         }

         writer.println("</tags>");
      }

      if(labels != null) {
         writer.println("<labels>");

         for(String label : labels) {
            writer.format("<label><![CDATA[%s]]></label>%n", label);
         }

         writer.println("</labels>");
      }

      if(tagsMethod != null) {
         writer.format("<tagsMethod><![CDATA[%s]]></tagsMethod>%n", tagsMethod);
      }

      if(dependsOn != null) {
         writer.println("<dependsOn>");

         for(String name : dependsOn) {
            writer.format("<name><![CDATA[%s]]></name>%n", name);
         }

         writer.println("</dependsOn>");
      }

      if(customEditor != null) {
         writer.format("<customEditor><![CDATA[%s]]></customEditor>%n",
            customEditor);
      }

      if(editorPropertyMethods != null) {
         writer.println("<editorProperties>");

         for(int i = 0; i < editorPropertyMethods.length; i++) {
            writer.println("<property>");
            writer.format(
               "<name><![CDATA[%s]]></name>%n", editorPropertyNames[i]);

            if(editorPropertyValues[i] != null) {
               writer.format(
                  "<value><![CDATA[%s]]></value>%n", editorPropertyValues[i]);
            }

            if(editorPropertyMethods[i] != null) {
               writer.format(
                  "<method><![CDATA[%s]]></method>%n", editorPropertyMethods[i]);
            }

            writer.println("</property>");
         }

         writer.println("</editorProperties>");
      }

      if(enabledMethod != null) {
         writer.format("<enabledMethod><![CDATA[%s]]></enabledMethod>%n",
            enabledMethod);
      }

      writer.format("<enabled><![CDATA[%s]]></enabled>%n", enabled);
      writer.format("<defined><![CDATA[%s]]></defined>%n", defined);

      if(propertyType != null) {
         writer.format("<propertyType><![CDATA[%s]]></propertyType>%n",
            propertyType);
      }

      if(propertySubtype != null) {
         writer.format("<propertySubtype><![CDATA[%s]]></propertySubtype>%n",
            propertySubtype);
      }

      writer.format("<autocomplete><![CDATA[%s]]></autocomplete>%n", autocomplete);

      if(value != null) {
         writeValue(writer, value, type);
      }

      writer.println("</tabularEditor>");
   }

   private void writeValue(PrintWriter writer, Object value, Type type) {
      if(type == Type.LIST) {
         writer.println("<value>");
         Iterable list;

         if(value.getClass().isArray()) {
            list = Arrays.asList((Object[]) value);
         }
         else {
            list = (Iterable) value;
         }

         for(Object val : list) {
            if(val != null) {
               writeValue(writer, val, subtype);
            }
            else {
               writer.println("<value/>");
            }
         }

         writer.println("</value>");
      }
      else if(type == Type.FILE && value instanceof File) {
         File file = (File) value;

         writer.println("<value>");
         writer.format("<filePath><![CDATA[%s]]></filePath>%n",
            file.getAbsolutePath().replace("\\", "/"));
         writer.format("<fileType><![CDATA[%s]]></fileType>%n",
            file.isDirectory());
         writer.println("</value>");
      }
      else if(type == Type.DATE && value instanceof Date) {
         writer.format("<value><![CDATA[%s]]></value>%n",
            Tool.timeInstantFmt.get().format((Date) value));
      }
      else if(type == Type.COLUMN && value instanceof ColumnDefinition[]) {
         ColumnDefinition[] columns = (ColumnDefinition[]) value;
         writer.println("<value>");

         for(ColumnDefinition column : columns) {
            column.writeXML(writer);
         }

         writer.println("</value>");
      }
      else if(type == Type.PARAMETER && value instanceof QueryParameter) {
         writer.println("<value>");
         ((QueryParameter) value).writeXML(writer);
         writer.println("</value>");
      }
      else if(type == Type.HTTP_PARAMETER && value instanceof HttpParameter) {
         writer.println("<value>");
         ((HttpParameter) value).writeXML(writer);
         writer.println("</value>");
      }
      else if(type == Type.REST_PARAMETERS && value instanceof RestParameters) {
         writer.println("<value>");
         ((RestParameters) value).writeXML(writer);
         writer.println("</value>");
      }
      else {
         writer.format("<value><![CDATA[%s]]></value>%n", value.toString());
      }
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      String val = Tool.getChildValueByTagName(tag, "type");

      if(val != null) {
         type = Type.valueOf(val);
      }

      val = Tool.getChildValueByTagName(tag, "subtype");

      if(val != null) {
         subtype = Type.valueOf(val);
      }

      val = Tool.getChildValueByTagName(tag, "row");

      if(val != null) {
         row = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "col");

      if(val != null) {
         col = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "align");

      if(val != null) {
         align = ViewAlign.valueOf(val);
      }

      val = Tool.getChildValueByTagName(tag, "verticalAlign");

      if(val != null) {
         verticalAlign = ViewAlign.valueOf(val);
      }

      val = Tool.getChildValueByTagName(tag, "rows");

      if(val != null) {
         rows = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "columns");

      if(val != null) {
         columns = Integer.parseInt(val);
      }

      Element node = Tool.getChildNodeByTagName(tag, "tags");

      if(node != null) {
         NodeList tagList = Tool.getChildNodesByTagName(node, "tag");
         tags = new String[tagList.getLength()];

         for(int i = 0; i < tagList.getLength(); i++) {
            tags[i] = Tool.getValue(tagList.item(i));
         }
      }

      node = Tool.getChildNodeByTagName(tag, "labels");

      if(node != null) {
         NodeList labelList = Tool.getChildNodesByTagName(node, "label");
         labels = new String[labelList.getLength()];

         for(int i = 0; i < labelList.getLength(); i++) {
            labels[i] = Tool.getValue(labelList.item(i));
         }
      }

      tagsMethod = Tool.getChildValueByTagName(tag, "tagsMethod");

      node = Tool.getChildNodeByTagName(tag, "dependsOn");

      if(node != null) {
         NodeList dependsOnList = Tool.getChildNodesByTagName(node, "name");
         dependsOn = new String[dependsOnList.getLength()];

         for(int i = 0; i < dependsOnList.getLength(); i++) {
            dependsOn[i] = Tool.getValue(dependsOnList.item(i));
         }
      }

      if((node = Tool.getChildNodeByTagName(tag, "editorProperties")) != null) {
         NodeList nodes = Tool.getChildNodesByTagName(node, "property");
         editorPropertyNames = new String[nodes.getLength()];
         editorPropertyValues = new String[nodes.getLength()];
         editorPropertyMethods = new String[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            editorPropertyNames[i] =
               Tool.getValue(Tool.getChildNodeByTagName(element, "name"));

            if((node = Tool.getChildNodeByTagName(element, "value")) != null) {
               editorPropertyValues[i] = Tool.getValue(node);
            }

            if((node = Tool.getChildNodeByTagName(element, "method")) != null) {
               editorPropertyMethods[i] = Tool.getValue(node);
            }
         }
      }

      customEditor = Tool.getChildValueByTagName(tag, "customEditor");
      enabledMethod = Tool.getChildValueByTagName(tag, "enabledMethod");
      enabled = "true".equals(Tool.getChildValueByTagName(tag, "enabled"));
      defined = "true".equals(Tool.getChildValueByTagName(tag, "defined"));
      propertyType = Tool.getChildValueByTagName(tag, "propertyType");
      propertySubtype = Tool.getChildValueByTagName(tag, "propertySubtype");
      autocomplete = "true".equals(Tool.getChildValueByTagName(tag, "autocomplete"));

      node = Tool.getChildNodeByTagName(tag, "value");

      if(node != null) {
         value = parseValue(node, type);
      }
   }

   @SuppressWarnings("unchecked")
   private Object parseValue(Element node, Type type) throws Exception {
      Object value = null;

      if(type == Type.LIST) {
         NodeList valueNodes = Tool.getChildNodesByTagName(node, "value");
         Class collectionClass = Class.forName(propertyType);
         Class elementClass = Class.forName(propertySubtype);

         if(collectionClass.isArray()) {
            value = Array.newInstance(elementClass,
               valueNodes.getLength());
         }
         else {
            value = collectionClass.newInstance();
         }

         for(int i = 0; i < valueNodes.getLength(); i++) {
            Element valueNode = (Element) valueNodes.item(i);

            if(collectionClass.isArray()) {
               Array.set(value, i, parseValue(valueNode, subtype));
            }
            else {
               ((Collection) value).add(parseValue(valueNode, subtype));
            }
         }
      }
      else if(type == Type.COLUMN) {
         NodeList columnList = Tool.getChildNodesByTagName(node, "column");
         ColumnDefinition[] columns =
            new ColumnDefinition[columnList.getLength()];

         for(int i = 0; i < columnList.getLength(); i++) {
            Element columnNode = (Element) columnList.item(i);
            ColumnDefinition column = new ColumnDefinition();
            column.parseXML(columnNode);
            columns[i] = column;
         }

         value = columns;
      }
      else if(type == Type.PARAMETER) {
         Element paramNode = Tool.getChildNodeByTagName(node, "queryParameter");

         if(paramNode != null) {
            QueryParameter parameter = new QueryParameter();
            parameter.parseXML(paramNode);
            value = parameter;
         }
      }
      else if(type == Type.HTTP_PARAMETER) {
         final Element httpParamNode = Tool.getChildNodeByTagName(node, "httpParameter");

         if(httpParamNode != null) {
            final HttpParameter httpParameter = new HttpParameter();
            httpParameter.parseXML(httpParamNode);
            value = httpParameter;
         }
      }
      else if(type == Type.REST_PARAMETERS) {
         Element paramsNode = Tool.getChildNodeByTagName(node, "restParameters");

         if(paramsNode != null) {
            RestParameters parameters = new RestParameters();
            parameters.parseXML(paramsNode);
            value = parameters;
         }
      }
      else if(type == Type.FILE) {
         Element filePath = Tool.getChildNodeByTagName(node, "filePath");
         value = FileSystemService.getInstance().getFile(Tool.getValue(filePath));
      }
      else {
         String val = Tool.getValue(node);

         if(val != null) {
            if(type == Type.DATE) {
               value = Tool.parseDateTime(val);
            }
            else if(type == Type.INT) {
               value = Integer.parseInt(val);
            }
            else if(type == Type.LONG) {
               value = Long.parseLong(val);
            }
            else if(type == Type.SHORT) {
               value = Short.parseShort(val);
            }
            else if(type == Type.BYTE) {
               value = Byte.parseByte(val);
            }
            else if(type == Type.DOUBLE) {
               value = Double.parseDouble(val);
            }
            else if(type == Type.FLOAT) {
               value = Float.parseFloat(val);
            }
            else if(type == Type.BOOLEAN) {
               value = Boolean.parseBoolean(val);
            }
            else {
               value = val;
            }
         }
      }

      return value;
   }

   private Type type;
   private Type subtype;
   private int row;
   private int col;
   private ViewAlign align;
   private ViewAlign verticalAlign;
   private int rows;
   private int columns;
   private String[] tags;
   private String[] labels;
   private String tagsMethod;
   private String[] dependsOn;
   private String customEditor;
   private String enabledMethod;
   private boolean enabled;
   private boolean defined;
   private boolean lineWrap;
   private String[] editorPropertyNames;
   private String[] editorPropertyValues;
   private String[] editorPropertyMethods;
   private String propertyType;
   private String propertySubtype;
   private Object value;
   private String visibleMethod;
   private boolean visible = true;
   private boolean autocomplete;

   private static final Logger LOG =
      LoggerFactory.getLogger(TabularEditor.class);

   /**
    * Enumeration of the types of editors.
    */
   public enum Type {
      BOOLEAN, INT, LONG, SHORT, BYTE, FLOAT, DOUBLE, DATE, FILE, COLUMN,
      LIST, TEXT, TAGS, PARAMETER, HTTP_PARAMETER, AUTOCOMPLETE, CUSTOM,
      REST_PARAMETERS
   }

   public static final class Deserializer extends StdDeserializer<TabularEditor> {
      public Deserializer() {
         super(TabularEditor.class);
      }

      @Override
      public TabularEditor deserialize(JsonParser parser, DeserializationContext context)
         throws IOException
      {
         TabularEditor editor = new TabularEditor();
         JsonNode node = parser.getCodec().readTree(parser);
         JsonNode child;

         if((child = node.get("type")) != null) {
            editor.setType(
               child.textValue() != null ? Type.valueOf(child.textValue()) : null);
         }

         if((child = node.get("subtype")) != null) {
            editor.setSubtype(
               child.textValue() != null ? Type.valueOf(child.textValue()) : null);
         }

         if((child = node.get("row")) != null) {
            editor.setRow(child.asInt());
         }

         if((child = node.get("col")) != null) {
            editor.setCol(child.asInt());
         }

         if((child = node.get("align")) != null) {
            editor.setAlign(
               child.textValue() != null ? ViewAlign.valueOf(child.textValue()) : null);
         }

         if((child = node.get("verticalAlign")) != null) {
            editor.setVerticalAlign(
               child.textValue() != null ? ViewAlign.valueOf(child.textValue()) : null);
         }

         if((child = node.get("rows")) != null) {
            editor.setRows(child.asInt());
         }

         if((child = node.get("columns")) != null) {
            editor.setColumns(child.asInt());
         }

         if((child = node.get("tags")) != null) {
            String[] arr = new String[child.size()];

            for(int i = 0; i < child.size(); i++) {
               arr[i] = child.get(i).textValue();
            }

            editor.setTags(arr);
         }

         if((child = node.get("labels")) != null) {
            String[] arr = new String[child.size()];

            for(int i = 0; i < child.size(); i++) {
               arr[i] = child.get(i).textValue();
            }

            editor.setLabels(arr);
         }

         if((child = node.get("tagsMethod")) != null) {
            editor.setTagsMethod(child.textValue());
         }

         if((child = node.get("dependsOn")) != null) {
            String[] arr = new String[child.size()];

            for(int i = 0; i < child.size(); i++) {
               arr[i] = child.get(i).textValue();
            }

            editor.setDependsOn(arr);
         }

         if((child = node.get("customEditor")) != null) {
            editor.setCustomEditor(child.textValue());
         }

         if((child = node.get("enabledMethod")) != null) {
            editor.setEnabledMethod(child.textValue());
         }

         if((child = node.get("enabled")) != null) {
            editor.setEnabled(child.asBoolean());
         }

         if((child = node.get("defined")) != null) {
            editor.setDefined(child.asBoolean());
         }

         if((child = node.get("editorPropertyNames")) != null) {
            String[] arr = new String[child.size()];

            for(int i = 0; i < child.size(); i++) {
               arr[i] = child.get(i).textValue();
            }

            editor.setEditorPropertyNames(arr);
         }

         if((child = node.get("editorPropertyValues")) != null) {
            String[] arr = new String[child.size()];

            for(int i = 0; i < child.size(); i++) {
               arr[i] = child.get(i).textValue();
            }

            editor.setEditorPropertyValues(arr);
         }

         if((child = node.get("editorPropertyMethods")) != null) {
            String[] arr = new String[child.size()];

            for(int i = 0; i < child.size(); i++) {
               arr[i] = child.get(i).textValue();
            }

            editor.setEditorPropertyMethods(arr);
         }

         if((child = node.get("propertyType")) != null) {
            editor.setPropertyType(child.textValue());
         }

         if((child = node.get("propertySubtype")) != null) {
            editor.setPropertySubtype(child.textValue());
         }

         if((child = node.get("autocomplete")) != null) {
            editor.setAutocomplete(child.asBoolean());
         }

         if((child = node.get("value")) != null) {
            Object value = null;

            if(editor.getType() == Type.LIST) {
               try {
                  Class collectionClass = getClass(editor.getPropertyType());
                  Class elementClass = getClass(editor.getPropertySubtype());

                  if(collectionClass.isArray()) {
                     value = parser.getCodec()
                        .readValue(child.traverse(parser.getCodec()), collectionClass);
                  }
                  else {
                     value = parser.getCodec()
                        .readValue(child.traverse(parser.getCodec()),
                           context.getTypeFactory()
                              .constructCollectionType(collectionClass, elementClass));
                  }
               }
               catch(Exception e) {
                  LOG.warn(
                     "Failed to deserialize TabularEditor", e);
               }
            }
            else {
               Class cls = getClass(editor.getPropertyType());

               if(editor.getType() == Type.DATE) {
                  value = Tool.getData(cls, child.textValue());
               }
               else {
                  value = parser.getCodec()
                     .readValue(child.traverse(parser.getCodec()), cls);
               }
            }

            editor.setValue(value);
         }

         return editor;
      }

      private Class<?> getClass(String name) {
         try {
            return Class.forName(name);
         }
         catch(ClassNotFoundException e) {
         }

         return String.class;
      }
   }
}
