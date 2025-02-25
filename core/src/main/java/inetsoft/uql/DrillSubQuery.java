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
package inetsoft.uql;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * DrillSubQuery represents a sub-query in a drill path.
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class DrillSubQuery implements XMLSerializable, Serializable, Cloneable {
   /**
    * Constructor.
    */
   public DrillSubQuery() {
      this.params = new Hashtable<>();
   }

   /**
    * Get parameter names.
    * @return all parameter names.
    */
   public Iterator<String> getParameterNames() {
      return params.keySet().iterator();
   }

   /**
    * Get a parameter with name.
    * @param name name of parameter specification.
    * @return parameter with specified name.
    */
   public String getParameter(String name) {
      return params.get(name);
   }

   /**
    * Set name and value of parameter in a container.
    * @param name name of parameter.
    * @param value value of parameter.
    */
   public void setParameter(String name, String value) {
      params.put(name, value);
   }

   /**
    * Get name of subquery.
    * @return name of subquery.
    */
   public String getQuery() {
      return query;
   }

   /**
    * Set name of subquery.
    * @param query name of subquery.
    */
   public void setQuery(String query) {
      this.query = query;
   }

   public String getWsIdentifier() {
      return wsEntry == null ? null : wsEntry.toIdentifier();
   }

   public AssetEntry getWsEntry() {
      return wsEntry;
   }

   public void setWsEntry(AssetEntry wsEntry) {
      this.wsEntry = wsEntry;
   }

   /**
    * Check if equals another query.
    * @param subquery another subquery.
    * @return true is equal with another subquery.
    *  and is not equal with another subquery.
    */
   public boolean equals(DrillSubQuery subquery) {
      if(subquery == null) {
         return false;
      }

      if(subquery.getQuery() == null || subquery.getQuery().length() == 0) {
         return false;
      }

      if(!Tool.equals(this.query, subquery.getQuery())) {
         return false;
      }

      if(!Tool.equals(this.wsEntry, subquery.getWsEntry())) {
         return false;
      }

      return Tool.equalsContent(this.params, subquery.params);
   }

   /**
    * Clear the subquery.
    */
   public void clear() {
      query = null;
      wsEntry = null;
      params.clear();
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         DrillSubQuery nquery = (DrillSubQuery) super.clone();
         nquery.params = (Hashtable<String, String>) params.clone();
         nquery.query = query;

         if(wsEntry != null) {
            nquery.wsEntry = (AssetEntry) wsEntry.clone();
         }

         return nquery;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Parse the XML element that contains information on this query.
    * @param tag the specified element xml tag represents wizard.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element wsNode = Tool.getChildNodeByTagName(tag, "worksheetEntry");

      if(wsNode != null) {
         wsNode = Tool.getFirstChildNode(wsNode);
         wsEntry = AssetEntry.createAssetEntry(wsNode);

         handleWSRefOrgMismatch();
      }

      NodeList list = Tool.getChildNodesByTagName(tag, "variableField");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         String queryKey = Tool.getAttribute(elem, "key");
         String value = Tool.getAttribute(elem, "value");

         if(queryKey != null && value != null) {
            setParameter(queryKey, value);
         }
      }
   }

   /**
    * Generate the XML segment to represent this query.
    * param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer){
      String subquery = getQuery() != null && getQuery().length() > 0 ?
         getQuery() : null;

      writer.print("<subquery");
   
      if(subquery != null) {
         writer.print(" qname=\"" + Tool.escape(subquery) + "\"");
      }

      writer.println(">");

      if(wsEntry != null) {
         writer.print("<worksheetEntry>");
         wsEntry.writeXML(writer);
         writer.print("</worksheetEntry>");
      }

      Iterator<String> keys = getParameterNames();

      while(keys.hasNext()) {
         String queryKey = keys.next();
         String value = getParameter(queryKey) == null ||
            getParameter(queryKey).length() == 0 ? " " : getParameter(queryKey);

         writer.println("<variableField key=\"" +
            Tool.escape(queryKey) + "\"");
         writer.print(" value=\"" + value);
         writer.println("\"/>");
      }

      writer.println("</subquery>");
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append(" DrillSubQuery{");

      if(wsEntry != null) {
         builder.append("wsIdentifier: ");
         builder.append(wsEntry.toIdentifier());
      }

      if(params != null) {
         builder.append(", params{");
         Set<Map.Entry<String, String>> entrySet = params.entrySet();

         for(Map.Entry<String, String> entry : entrySet) {
            builder.append(entry.getKey())
               .append(": ")
               .append(entry.getValue());
         }

         builder.append("}");
      }

      builder.append("}");
      return builder.toString();
   }

   public void handleWSRefOrgMismatch() {
      String curOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      String wsOrg = wsEntry.getOrgID();

      if(!Tool.equals(curOrgId, wsOrg)) {
         wsEntry.setOrgID(curOrgId);
      }
   }

   private String query;
   private AssetEntry wsEntry;
   private Hashtable<String, String> params;
   private static final Logger LOG =
      LoggerFactory.getLogger(XQuery.class);

}
