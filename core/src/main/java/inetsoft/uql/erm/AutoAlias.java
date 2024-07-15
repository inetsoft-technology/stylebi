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

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Vector;

/**
 * This class stores information on an auto-aliase table.
 *
 * @author  InetSoft Technology Corp.
 * @since   7.0
 */
public class AutoAlias implements Cloneable, Serializable {
   /**
    * Add an incoming join to the auto alias.
    */
   public void addIncomingJoin(IncomingJoin join) {
      joins.add(join);
   }

   /**
    * Get the total number of incoming joins in the auto alias.
    */
   public int getIncomingJoinCount() {
      return joins.size();
   }

   /**
    * Get the incoming join at the specified index.
    */
   public IncomingJoin getIncomingJoin(int idx) {
      return (IncomingJoin) joins.get(idx);
   }

   /**
    * Remove an incoming joins.
    */
   public void removeIncomingJoin(int idx) {
      joins.removeElementAt(idx);
   }

   /**
    * Remove incoming joins from the tabe.
    */
   public void removeIncomingJoin(String table) {
      for(int i = 0; i < joins.size(); i++) {
         IncomingJoin join = getIncomingJoin(i);

         if(table.equals(join.getSourceTable())) {
            joins.removeElementAt(i);
            i--;
         }
      }
   }

   /**
    * Remove all incoming joins.
    */
   public void removeAllIncomingJoins() {
      joins.removeAllElements();
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         AutoAlias obj = (AutoAlias) super.clone();

         obj.joins = Tool.deepCloneCollection(joins);

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Writes an XML representation of this object.
    */
   public void writeXML(PrintWriter writer) {
      writer.println("<autoAlias>");

      for(int i = 0; i < joins.size(); i++) {
         ((IncomingJoin) joins.get(i)).writeXML(writer);
      }

      writer.println("</autoAlias>");
   }

   /**
    * Parse the data from a xml representation.
    */
   public void parseXML(Element tag) throws DOMException {
      NodeList nlist = Tool.getChildNodesByTagName(tag, "incomingJoin");

      for(int i = 0; i < nlist.getLength(); i++) {
         IncomingJoin join = new IncomingJoin();

         join.parseXML((Element) nlist.item(i));
         addIncomingJoin(join);
      }
   }

   public static boolean isEmpty(AutoAlias autoAlias) {
      return autoAlias == null || autoAlias.getIncomingJoinCount() < 1;
   }

   /**
    * An incoming join causes a new alias to be created automatically in the
    * partition. If the keep outgoing join option is selected, the alias
    * includes all outgoing joins. All joins defined on the original table
    * are treated as outgoing joins if they are not marked as incoming joins.
    */
   public static class IncomingJoin implements Serializable, Cloneable {
      /**
       * Get the source table of an incoming join.
       */
      public String getSourceTable() {
         return table;
      }

      /**
       * Set the source table of an incoming join.
       */
      public void setSourceTable(String table) {
         this.table = table;
      }

      /**
       * Get the alias name of the aliased table for the incoming join.
       */
      public String getAlias() {
         return alias;
      }

      /**
       * Set the alias name of the aliased table for the incoming join.
       */
      public void setAlias(String alias) {
         this.alias = alias;
      }

      /**
       * Check if all outgoing joins should be kept for the auto-aliased
       * table.
       */
      public boolean isKeepOutgoing() {
         return keepOutgoing;
      }

      /**
       * Set whether to keep all outgoing joins.
       */
      public void setKeepOutgoing(boolean keep) {
         this.keepOutgoing = keep;
      }

      /**
       * Get the prefix for the aliased table created from outgoing joins.
       */
      public String getPrefix() {
         return prefix;
      }

      /**
       * Set the prefix for the aliased table created from outgoing joins.
       */
      public void setPrefix(String prefix) {
         this.prefix = prefix;
      }

      /**
       * Make a copy of this object.
       */
      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            LOG.error("Failed to clone auto-join object", ex);
         }

         return null;
      }

      /**
       * Write an xml representation of this alias.
       */
      public void writeXML(PrintWriter writer) {
         writer.println("<incomingJoin keepOutgoing=\"" + keepOutgoing + "\">");

         if(table != null) {
            writer.println("<table><![CDATA[" + table + "]]></table>");
         }

         if(alias != null) {
            writer.println("<alias><![CDATA[" + alias + "]]></alias>");
         }

         if(prefix != null) {
            writer.println("<prefix><![CDATA[" + prefix + "]]></prefix>");
         }

         writer.println("</incomingJoin>");
      }

      /**
       * Parse an xml representation of this alias.
       */
      public void parseXML(Element tag) {
         String attr;

         if((attr = Tool.getAttribute(tag, "keepOutgoing")) != null) {
            keepOutgoing = "true".equals(attr);
         }

         table = Tool.getChildValueByTagName(tag, "table");
         alias = Tool.getChildValueByTagName(tag, "alias");
         prefix = Tool.getChildValueByTagName(tag, "prefix");
      }

      private String table; // incoming join source table name
      private String alias; // table alias
      private boolean keepOutgoing = false;
      private String prefix; // outgoing alias prefix
   }

   private Vector<IncomingJoin> joins = new Vector<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(AutoAlias.class);
}
