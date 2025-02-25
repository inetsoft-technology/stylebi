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
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;

/**
 * DrillPath defines a drill path to a report or a URL.
 *
 * @version 9.1, 04/05/2007
 * @author InetSoft Technology Corp
 */
public class DrillPath implements XMLSerializable, Serializable, Cloneable {
   /**
    * Web link.
    */
   public static final int WEB_LINK = 1;
   /**
    * Link to a viewsheet.
    */
   public static final int VIEWSHEET_LINK = 8;

   public DrillPath() {
      this(null);
   }

   /**
    * Create a drill path.
    */
   public DrillPath(String name) {
      this.name = name == null ? "" : name;
   }

   /**
    * Copy the object with a new name.
    */
   public DrillPath copyDrillPath(String name) {
      DrillPath path = (DrillPath) this.clone();
      path.setName(name);

      return path;
   }

   /**
    * Set the drill path name.
    */
   private void setName(String name) {
      this.name = name == null ? "" : name;
   }

   /**
    * Get the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the hyperlink. It could be a report name (path) or a URL.
    * @param link for Web URL, the link must be the full URL including
    * the protocol. If the link is to another report, it should be the
    * report path as registered in report server.
    */
   public void setLink(String link) {
      this.link = link;
   }

   /**
    * Get the hyperlink.
    */
   public String getLink() {
      return link;
   }

   /**
    * Set the target frame.
    * @param targetFrame is the window name.
    */
   public void setTargetFrame(String targetFrame) {
      this.targetFrame = targetFrame;
   }

   /**
    * Get the target frame.
    */
   public String getTargetFrame() {
      return targetFrame;
   }

   /**
    * Set the tooltip. If tooltip is set, the tip is shown when mouse moves
    * into the hyperlink.
    */
   public void setToolTip(String tip) {
      this.tip = tip;
   }

   /**
    * Get the tooltip.
    */
   public String getToolTip() {
      return tip;
   }

   /**
    * Set the link type.
    * @param linkType link type.
    */
   public void setLinkType(int linkType) {
      this.linkType = linkType;
   }

   /**
    * Get the link type of this hyperlink
    * @return link type.
    */
   public int getLinkType() {
      return linkType;
   }

   /**
    * Set the drill subquery.
    * @param query the specified subquery.
    */
   public void setQuery(DrillSubQuery query) {
      this.query = query;
   }

   /**
    * Get drill subquery.
    * @return drill subquery.
    */
   public DrillSubQuery getQuery() {
      return query;
   }

   /**
    * Set whether to pass all report parameters to the link report.
    * @param pass true to pass all report parameters. It defaults to true.
    */
   public void setSendReportParameters(boolean pass) {
      this.passParams = pass;
   }

   /**
    * Check if to pass all report parameters to the linked report. It
    * defaults to true.
    */
   public boolean isSendReportParameters() {
      return passParams;
   }

   /**
    * Set whether to disable the prompting of the parameters on the target
    * report.
    */
   public void setDisablePrompting(boolean disable) {
      this.disablePrompting = disable;
   }

   /**
    * Check whether to disable the prompting of the parameters on the target
    * report.
    */
   public boolean isDisablePrompting() {
      return disablePrompting;
   }

   /**
    * Get all parameter names.
    */
   public Enumeration<String> getParameterNames() {
      if(params == null) {
         return Collections.emptyEnumeration();
      }

      return params.keys();
   }

   /**
    * Get the number of parameters defined for this link.
    */
   public int getParameterCount() {
      return (params == null) ? 0 : params.size();
   }

   /**
    * Get the field value for the parameter.
    */
   public String getParameterField(String name) {
      if(params == null) {
         return null;
      }

      return params.get(name);
   }

   /**
    * Get the type value for the hard-coded parameter.
    */
   public String getParameterType(String name) {
      if(types == null) {
         return null;
      }

      return types.get(name);
   }

   /**
    * Set the field name for the parameter.
    * @param name parameter name.
    * @param field data field name.
    */
   public void setParameterField(String name, String field) {
      if(field == null) {
         if(params != null) {
            params.remove(name);
         }
      }
      else {
         if(params == null) {
            params = new OrderedMap<>();
         }

         params.put(name, field);
      }
   }

   /**
    * Set the type name for the parameter.
    * @param name parameter name.
    * @param type type name.
    */
   public void setParameterType(String name, String type) {
      if(types == null) {
         types = new OrderedMap<>();
      }

      if(name != null && name.length() > 0) {
         types.put(name, type);
      }
   }

   /**
    * Judge whether the parameter is hard-coded or not.
    * @param name parameter name.
    */
   public boolean isParameterHardCoded(String name) {
      if((types == null) || (types.size() == 0)) {
         return false;
      }

      return types.containsKey(name);
   }

   /**
    * Remove a parameter field.
    * @param name parameter name.
    */
   public void removeParameterField(String name) {
      if(params != null) {
         params.remove(name);

         if(params.size() == 0) {
            params = null;
         }
      }

      if(types != null) {
         types.remove(name);

         if(types.size() == 0) {
            types = null;
         }
      }
   }

   /**
    * Remove all parameter fields.
    */
   public void removeAllParameterFields() {
      params = null;
      types = null;
   }

   /**
    * Get the hash code of the drill path.
    * @return the hash code of the drill path.
    */
   public int hashCode() {
      return name.hashCode();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof DrillPath)) {
         return false;
      }

      DrillPath path = (DrillPath) obj;

      return name.equals(path.name);
   }

   /**
    * Check if equals another object.
    */
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof DrillPath)) {
         return false;
      }

      DrillPath path = (DrillPath) obj;
      boolean eq = Tool.equals(link, path.link);

      if(!eq) {
         return false;
      }

      eq = Tool.equals(targetFrame, path.targetFrame) &&
           Tool.equals(tip, path.tip);

      if(!eq) {
         return false;
      }

      eq = disablePrompting == path.disablePrompting;

      if(!eq) {
         return false;
      }

      eq = passParams == path.passParams;

      if(!eq) {
         return false;
      }

      eq = linkType == path.linkType;

      if(!eq) {
         return false;
      }

      eq = Tool.equals(query, path.query);

      if(!eq) {
         return false;
      }

      eq = params == null ? params == path.params : params.equals(path.params);

      if(!eq) {
         return false;
      }

      eq = types == null ? types == path.types : types.equals(path.types);

      return eq;
   }

   /**
    * Get the string representaion.
    */
   @Override
   public String toString() {
      return toString(false);
   }

   public String toString(boolean full) {
      if(!full) {
         return name;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("drillPath{name: ")
         .append(Tool.escape(getName()))
         .append(", ")
         .append("disablePrompting: ")
         .append(isDisablePrompting())
         .append(", ")
         .append("passParams: ")
         .append(isSendReportParameters())
         .append(", ");

      if(getLink() != null) {
         sb.append("link: ")
            .append(Tool.escape(getLink()))
            .append(", ");
      }

      if(getTargetFrame() != null) {
         sb.append("targetFrame: ")
            .append(Tool.escape(getTargetFrame()))
            .append(", ");
      }

      if(getToolTip() != null) {
         sb.append("tooltip: ")
            .append(Tool.escape(getToolTip()))
            .append(", ");
      }

      sb.append("linkType: ")
         .append(getLinkType())
         .append(", ");

      if(getQuery() != null) {
         sb.append(getQuery().toString());
      }

      sb.append(", parameters{");
      Enumeration<String> keys = getParameterNames();

      while(keys.hasMoreElements()) {
         String pname = keys.nextElement();
         String field = getParameterField(pname);
         sb.append("name: ").append(Tool.escape(pname));
         sb.append(", field: ").append(Tool.escape(field)).append("}");
      }

      return sb.toString();
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         DrillPath path = (DrillPath) super.clone();
         path.params = Tool.deepCloneMap(params);
         path.types = Tool.deepCloneMap(types);

         return path;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<drillPath disablePrompting=\"" +
         isDisablePrompting() + "\"");

      writer.print(" passParams=\"" + isSendReportParameters() + "\"");

      writer.print(" name=\"" + Tool.escape(getName()) + "\"");

      if(getLink() != null) {
         writer.print(" link=\"" + Tool.escape(getLink()) + "\"");
      }

      if(getTargetFrame() != null) {
         writer.print(" targetFrame=\"" + Tool.escape(getTargetFrame()) +
            "\"");
      }

      if(getToolTip() != null) {
         writer.print(" toolTip=\"" + Tool.escape(getToolTip()) + "\"");
      }

      writer.print(" linkType=\"" + getLinkType() + "\"");

      writer.println(">");

      if(getQuery() != null) {
         getQuery().writeXML(writer);
      }

      Enumeration<String> keys = getParameterNames();

      while(keys.hasMoreElements()) {
         String pname = keys.nextElement();

         if(isParameterHardCoded(pname)) {
            continue;
         }

         String field = getParameterField(pname);

         writer.println("<parameterField name=\"" + Tool.escape(pname) +
            "\" field=\"" + Tool.escape(field) + "\"/>");
      }

      Enumeration<String> keys2 = getParameterNames();

      while(keys2.hasMoreElements()) {
         String pname = keys2.nextElement();

         if(!isParameterHardCoded(pname)) {
            continue;
         }

         String field = getParameterField(pname);
         String type = getParameterType(pname);

         writer.println("<parameterField2 name=\"" + Tool.escape(pname) +
            "\" field=\"" + Tool.escape(field) +
            "\" type=\"" + Tool.escape(type) + "\"/>");
      }

      writer.println("</drillPath>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String attr;

      if((attr = Tool.getAttribute(tag, "name")) != null) {
         setName(attr);
      }

      if((attr = Tool.getAttribute(tag, "link")) != null) {
         attr = handleDrillLinkOrgMismatch(attr);
         setLink(attr);
      }

      if((attr = Tool.getAttribute(tag, "targetFrame")) != null) {
         setTargetFrame(attr);
      }

      if((attr = Tool.getAttribute(tag, "toolTip")) != null) {
         setToolTip(attr);
      }

      if((attr = Tool.getAttribute(tag, "linkType")) != null) {
         setLinkType(Integer.parseInt(attr));
      }

      Element qnode = Tool.getChildNodeByTagName(tag, "subquery");
      boolean requiresBC = false;

      if(qnode != null) {
         DrillSubQuery subquery = new DrillSubQuery();
         subquery.parseXML(qnode);
         setQuery(subquery);
      }
      else {
         attr = Tool.getAttribute(tag, "query");

         if(attr != null) {
            DrillSubQuery subquery = new DrillSubQuery();
            subquery.setQuery(attr);
            setQuery(subquery);
            requiresBC = true;
         }
      }

      if((attr = Tool.getAttribute(tag, "disablePrompting")) != null) {
         setDisablePrompting(attr.equals("true"));
      }

      if((attr = Tool.getAttribute(tag, "passParams")) != null) {
         setSendReportParameters(attr.equals("true"));
      }

      NodeList list = Tool.getChildNodesByTagName(tag, "parameterField");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         String name = Tool.getAttribute(elem, "name");
         String field = Tool.getAttribute(elem, "field");

         setParameterField(name, field);
      }

      NodeList list2 = Tool.getChildNodesByTagName(tag, "parameterField2");

      for(int i = 0; i < list2.getLength(); i++) {
         Element elem = (Element) list2.item(i);
         String name = Tool.getAttribute(elem, "name");
         String field = Tool.getAttribute(elem, "field");
         String type = Tool.getAttribute(elem, "type");

         setParameterField(name, field);
         setParameterType(name, type);
      }

      if(requiresBC && (params == null || params.isEmpty())) {
         // create dummy parameter field for backward compatibility
         setParameterField("Parameter[0]", "Column[0]");
      }
   }

   public String handleDrillLinkOrgMismatch(String link) {
      String curOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      int orgIdx = link.lastIndexOf("^");

      if(orgIdx > 0) {
         String linkOrg = link.substring(orgIdx + 1);

         if(!Tool.equals(linkOrg, curOrgId)) {
            return link.substring(0,orgIdx + 1) + curOrgId;
         }
      }

      return link;
   }

   private String name = "";
   private String link = "";
   private String targetFrame = "";
   private String tip = "";
   private DrillSubQuery query = null;
   private OrderedMap<String, String> params;
   private OrderedMap<String, String> types; // for hard-coded parameters
   private boolean passParams = true;
   private boolean disablePrompting;
   private int linkType = DrillPath.WEB_LINK;

   private static final Logger LOG = LoggerFactory.getLogger(DrillPath.class);
}
