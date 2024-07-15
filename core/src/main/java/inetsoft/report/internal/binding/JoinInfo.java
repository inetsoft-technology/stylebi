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
package inetsoft.report.internal.binding;

import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * JoinInfo stores the relationship for joining two data sources.
 *
 * @version 6.1 9/30/2004
 * @author larryl
 */
public class JoinInfo implements Serializable, XMLSerializable, Cloneable {
   public JoinInfo() {
   }

   @Deprecated
   public JoinInfo(SourceAttr s1, Field f1, SourceAttr s2, Field f2) {
      addSource(0, s1, f1);
      addSource(1, s2, f2);
   }

   /**
    * Get the number of sources in the join.
    *
    * @return the source count.
    */
   public int getSourceCount() {
      return sources.size();
   }

   /**
    * Gets the source at the specified index.
    *
    * @param index the index of the source.
    *
    * @return the source.
    */
   public SourceAttr getSource(int index) {
      return sources.get(index);
   }

   /**
    * Gets the field at the specified index.
    *
    * @param index the index of the field.
    *
    * @return the field.
    */
   public Field getField(int index) {
      return fields.get(index);
   }

   /**
    * Adds a source to this join.
    *
    * @param source the source to join.
    * @param field  the join field.
    */
   public void addSource(SourceAttr source, Field field) {
      sources.add(source);
      fields.add(field);
   }

   /**
    * Adds a source to this join at the specified index.
    *
    * @param index  the index of the source to add.
    * @param source the source to join.
    * @param field  the join field.
    */
   public void addSource(int index, SourceAttr source, Field field) {
      sources.add(index, source);
      fields.add(index, field);
   }

   /**
    * Sets the source at the specified index.
    *
    * @param index  the index of the source to replace.
    * @param source the source to join.
    * @param field  the join field.
    */
   public void setSource(int index, SourceAttr source, Field field) {
      sources.set(index, source);
      fields.set(index, field);
   }

   /**
    * Removes the source at the specified index.
    *
    * @param index the index of the source.
    */
   public void removeSource(int index) {
      sources.remove(index);
      fields.remove(index);
   }

   /**
    * Remove all sources.
    */
   public void removeAllSources() {
      sources.clear();
      fields.clear();
   }
   
   /**
    * Gets the flag that determines if the join is a union or an intersection
    * join.
    *
    * @return <tt>true</tt> if a union.
    */
   public boolean isUnion() {
      return union;
   }

   /**
    * Sets the flag that determines if the join is a union or an intersection
    * join.
    *
    * @param union <tt>true</tt> if a union.
    */
   public void setUnion(boolean union) {
      this.union = union;
   }

   /**
    * Set the lhs source of the join.
    */
   @Deprecated
   public void setSource1(SourceAttr src) {
      SourceAttr source = (SourceAttr) src.clone();
      // prevent infinite loop as we only support one level join
      source.removeAllJoinSources();
      source.removeAllJoinRelations();
      sources.set(0, source);
   }

   /**
    * Get the lhs source of the join.
    */
   @Deprecated
   public SourceAttr getSource1() {
      return sources.isEmpty() ? null : sources.get(0);
   }

   /**
    * Set the rhs source of the join.
    */
   @Deprecated
   public void setSource2(SourceAttr src) {
      SourceAttr source = (SourceAttr) src.clone();
      // prevent infinite loop as we only support one level join
      source.removeAllJoinSources();
      source.removeAllJoinRelations();
      sources.set(1, source);
   }

   /**
    * Get the rhs source of the join.
    */
   @Deprecated
   public SourceAttr getSource2() {
      return sources.size() < 2 ? null : sources.get(1);
   }

   /**
    * Set the lhs field of the join.
    */
   @Deprecated
   public void setField1(Field fld) {
      fields.set(0, fld);
   }

   /**
    * Set the rhs field of the join.
    */
   @Deprecated
   public Field getField1() {
      return fields.size() < 1 ? null : fields.get(0);
   }

   /**
    * Set the rhs field of the join.
    */
   @Deprecated
   public void setField2(Field fld) {
      fields.set(1, fld);
   }

   /**
    * Get the rhs field of the join.
    */
   @Deprecated
   public Field getField2() {
      return fields.size() < 2 ? null : fields.get(1);
   }

   /**
    * Write the xml segment to the destination writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.println("<joinInfo union=\"" + union + "\">");

      int cnt = Math.min(sources.size(), fields.size());

      for(int i = 0; i < cnt; i++) {
         writer.println("<source>");
         writer.println(
            "<source><![CDATA[" + sources.get(i).getSource() + "]]></source>");
         writer.println("<type>" + sources.get(i).getType() + "</type>");

         if(sources.get(i).getPrefix() != null) {
            writer.println("<prefix><![CDATA[" + sources.get(i).getPrefix() + "]]></prefix>");
         }

         fields.get(i).writeXML(writer);
         writer.println("</source>");
      }

      writer.println("</joinInfo>");
   }

   /**
    * Parse the xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
   }

   public final void parseXML(Element tag, SourceAttr parent) throws Exception {
      union = !"false".equals(Tool.getAttribute(tag, "union"));

      Element node = Tool.getChildNodeByTagName(tag, "source1");

      if(node != null) {
         Element filter = Tool.getChildNodeByTagName(node, "filter");
         SourceAttr source = new SourceAttr();
         Field field = null;

         if(filter != null) {
            source.parseXML(filter);
         }

         Element ref = Tool.getChildNodeByTagName(node, "dataRef");

         if(ref != null) {
            field = (Field)
               Class.forName(Tool.getAttribute(ref, "class")).newInstance();
            field.parseXML(ref);
         }

         addSource(0, source, field);
      }

      node = Tool.getChildNodeByTagName(tag, "source2");

      if(node != null) {
         Element filter = Tool.getChildNodeByTagName(node, "filter");
         SourceAttr source = new SourceAttr();
         Field field = null;

         if(filter != null) {
            source.parseXML(filter);
         }

         Element ref = Tool.getChildNodeByTagName(node, "dataRef");

         if(ref != null) {
            field = (Field)
               Class.forName(Tool.getAttribute(ref, "class")).newInstance();
            field.parseXML(ref);
         }

         addSource(1, source, field);
      }

      NodeList nodes = Tool.getChildNodesByTagName(tag, "source");

      for(int i = 0; i < nodes.getLength(); i++) {
         node = (Element) nodes.item(i);

         String source = null;
         String prefix = null;
         int type = XSourceInfo.NONE;

         Element elem = null;

         source = Tool.getChildValueByTagName(node, "source");
         type = Integer.parseInt(Tool.getChildValueByTagName(node, "type"));

         if((elem = Tool.getChildNodeByTagName(node, "prefix")) != null) {
            prefix = Tool.getValue(elem) == null ? "" : Tool.getValue(elem);
         }

         Element ref = Tool.getChildNodeByTagName(node, "dataRef");

         if(parent != null && source != null && ref != null) {
            SourceAttr sattr = null;

            if(Tool.equals(parent.getSource(), source) &&
               Tool.equals(parent.getPrefix(), prefix) &&
               parent.getType() == type)
            {
               sattr = parent;
            }
            else {
               for(int j = 0; j < parent.getJoinSourceCount(); j++) {
                  SourceAttr src = parent.getJoinSource(j);

                  if(Tool.equals(src.getSource(), source) &&
                     Tool.equals(src.getPrefix(), prefix) &&
                     src.getType() == type)
                  {
                     sattr = src;
                     break;
                  }
               }
            }

            if(sattr != null) {
               Field field = (Field)
                  Class.forName(Tool.getAttribute(ref, "class")).newInstance();
               field.parseXML(ref);

               addSource(sattr, field);
            }
         }
      }
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "JoinInfo[" + sources + ", " + fields + "]";
   }

   /**
    * Get the cloned object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         JoinInfo info = (JoinInfo) super.clone();
         info.sources = new ArrayList<>(this.sources);
         info.fields = new ArrayList<>(this.fields);
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone join info", ex);
      }

      return null;
   }

   /**
    * Test if content equals another object.
    */
   public boolean equalsContent(JoinInfo info) {
      if(info == null) {
         return false;
      }

      if(this.union != info.union) {
         return false;
      }

      if(this.getSourceCount() != info.getSourceCount()) {
         return false;
      }

      for(int i = 0; i < this.getSourceCount(); i++) {
         if(!this.getSource(i).equals(info.getSource(i))) {
            return false;
         }
      }

      if(this.fields.size() != info.fields.size()) {
         return false;
      }

      for(int i = 0; i < this.fields.size(); i++) {
         if(!this.getField(i).getName().equals(info.getField(i).getName())) {
            return false;
         }
      }

      return true;
   }

   private List<SourceAttr> sources = new ArrayList<>();
   private List<Field> fields = new ArrayList<>();
   private boolean union = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(JoinInfo.class);
}
