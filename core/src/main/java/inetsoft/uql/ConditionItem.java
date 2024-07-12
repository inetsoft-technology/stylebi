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

import inetsoft.uql.erm.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;

/**
 * A Condition Item represents a item of condition in condition group.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ConditionItem implements Serializable, HierarchyItem {
   /**
    * Default constructor of Condition Item.
    */
   public ConditionItem() {
      this(new AttributeRef(null, ""), new Condition(), 0);
   }

   /**
    * Create a Condition Item.
    */
   public ConditionItem(DataRef attr, XCondition condition, int level) {
      this.attr = attr;
      this.condition = condition;
      this.level = level;
   }

   /**
    * Get the attribute name.
    */
   public DataRef getAttribute() {
      return attr;
   }

   /**
    * Get the condition.
    * @deprecated replaced by getXCondition
    */
   @Deprecated
   public Condition getCondition() {
      if(condition instanceof Condition) {
         return (Condition) condition;
      }
      else {
         return new Condition();
      }
   }

   /**
    * Get the condition.
    */
   public XCondition getXCondition() {
      return condition;
   }

   /**
    * Get the condition item level.
    */
   @Override
   public int getLevel() {
      return level;
   }

   /**
    * Get the condition item level.
    */
   @Override
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Get the attribute name.
    */
   public void setAttribute(DataRef ref) {
      if(ref != null) {
         this.attr = ref;
      }
   }

   /**
    * Set the condition.
    * @deprecated replaced by setXCondition
    */
   @Deprecated
   public void setCondition(Condition cond) {
      condition = cond;
   }

   /**
    * Set the XCondition.
    */
   public void setXCondition(XCondition cond) {
      condition = cond;
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      return new ConditionItem((DataRef) attr.clone(),
                               (XCondition) condition.clone(), level);
   }

   /**
    * Get string representation.
    */
   public String toString() {
      return toString(true);
   }

   /**
    * Get string representation.
    */
   public String toString(boolean shlvl) {
      StringBuilder buf = new StringBuilder();

      if(shlvl) {
         for(int i = 0; i < level; i++) {
            buf.append(".........");
         }
      }

      buf.append("[");

      String view = XUtil.toView(attr);

      if(view != null) {
         buf.append(view);
      }
      else {
         buf.append(attr.toString());
      }

      buf.append("]");
      buf.append(condition.toString());
      return buf.toString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(attr, condition, level);
   }

   /**
    * Write this data selection to XML.
    * @param writer the stream to output the XML text to
    */
   public void writeXML(PrintWriter writer) {
      XCondition cond = getXCondition();
      DataRef attr = getAttribute();

      writer.print("<condition level=\"" + getLevel() + "\" class=\"" +
                   cond.getClass().getName() + "\"");
      cond.writeAttributes(writer);
      writer.println(">");

      attr.writeXML(writer);
      cond.writeContents(writer);
      writer.println("</condition>");
   }

   /**
    * Read in the XML representation of this object.
    * @param ctag the XML element representing this object.
    */
   public void parseXML(Element ctag) throws Exception {
      String str;

      if((str = Tool.getAttribute(ctag, "level")) != null) {
         level = Integer.parseInt(str);
      }

      String cls = "inetsoft.uql.Condition";

      if((str = Tool.getAttribute(ctag, "class")) != null) {
         cls = str;
      }

      condition = (XCondition) Class.forName(cls).newInstance();
      condition.parseAttributes(ctag);

      // @By mikec, 2003-10-22 backward compatibility code to read
      // condition item before 6.0
      attr = null;

      String entity = Tool.getAttribute(ctag, "entity");
      entity = (entity == null || entity.equals("")) ? null : entity;
      String attrname = Tool.getAttribute(ctag, "attribute");

      if(entity != null && entity.contains(":")) {
         attr = new ExpressionRef(entity, attrname);
      }
      else if(attrname != null) {
         attr = new AttributeRef(entity, attrname);
      }

      NodeList nlist = ctag.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         if(!(nlist.item(i) instanceof Element)) {
            continue;
         }

         Element atag = (Element) nlist.item(i);

         if(atag.getTagName().equals("dataRef")) {
            // only override above attribtue if this dataref has an
            // attribute value
            if(attr == null ||
               (atag.getAttribute("attribute") != null &&
                atag.getAttribute("attribute").length() > 0))
            {
               attr = AbstractDataRef.createDataRef(atag);
            }
         }
      }

      if(attr == null) {
         attr = new AttributeRef(null, "");
      }

      condition.parseContents(ctag);
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ConditionItem)) {
         return false;
      }

      ConditionItem con2 = (ConditionItem) obj;
      boolean eq = Objects.equals(attr, con2.attr);

      eq = eq && (condition == null ?
         condition == con2.condition : condition.equals(con2.condition));
      eq = eq && level == con2.level;

      return eq;
   }

   private DataRef attr;
   private XCondition condition;
   private int level;
}
