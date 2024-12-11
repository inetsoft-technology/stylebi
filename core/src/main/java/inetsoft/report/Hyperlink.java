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
package inetsoft.report;

import inetsoft.graph.data.HRef;
import inetsoft.report.filter.DCMergeCell;
import inetsoft.report.filter.GroupedTable;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.uql.DrillPath;
import inetsoft.uql.DrillSubQuery;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Hyperlink defines a hyperlink to a report or a URL.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Hyperlink implements XMLSerializable, Serializable, Cloneable {
   /**
    * web link
    */
   public static final int WEB_LINK = 1;

   /**
    * Link to a viewsheet.
    */
   public static final int VIEWSHEET_LINK = 8;

   /**
    * message event
    */
   public static final int MESSAGE_LINK = 16;
   /**
    * Create an empty hyperlink. The setLink() must be called to set the
    * hyperlink before it can be used.
    */
   public Hyperlink() {
   }

   /**
    * Create a hyperlink.
    * @param link link
    */
   public Hyperlink(String link) {
      this(link, guessType(link));
   }

   /**
    * Create a hyperlink.
    * @param link link.
    * @param passParams <tt>true</tt> if send report parameters,
    * <tt>false</tt> otherwise.
    */
   public Hyperlink(String link, boolean passParams) {
      this(link);
      setSendReportParameters(passParams);
   }

   /**
    * Create a hyperlink.
    * @param link link.
    * @param passParams <tt>true</tt> if send report parameters,
    * @param scriptCreated <tt>true</tt> if create by script,
    * <tt>false</tt> otherwise.
    */
   public Hyperlink(String link, boolean passParams, boolean scriptCreated) {
      this(link, passParams);
      this.scriptCreated = scriptCreated;
   }

   /**
    * Create a hyperlink.
    * @param link link
    * @param type link type
    */
   public Hyperlink(String link, int type) {
      if(type == VIEWSHEET_LINK && !ASSET_ID_PATTERN.matcher(link).matches()) {
         // default to global viewsheet
         link = "1^128^__NULL__^" + link;
      }

      setLink(link);
      setLinkType(type);
   }

   /**
    * Set the hyperlink. It could be a report name (path) or a URL.
    * @param link for Web URL, the link must be the full URL including
    * the protocol. If the link is to another report, it should be the
    * report path as registered in report server.
    */
   public void setLink(String link) {
      this.link = link;
      this.dlink.setDValue(parseDLinkValue(link));
   }

   /**
    * parse the hyperlink dvalue.
    */
   private String parseDLinkValue(String link) {
      String res = "";

      if(VSUtil.isScriptValue(link) && link.indexOf("field['") >= 0) {
         String[] uriArrs = link.split("field\\['");

         for(String fragment : uriArrs) {
            int index = fragment.indexOf("']");

            if( index > 0) {
               String field = fragment.substring(0, index + 2);
               String replaceStr = "\"field['" + fragment.substring(0, index) + "']\"";

               fragment = fragment.replace(field, replaceStr);
            }

            res += fragment;
         }
      }
      else {
         res = link;
      }

      return res;
   }

   /**
    * Get runtime hyperlink value.
    */
   public String getLink() {
      return (String) dlink.getRValue();
   }

   /**
    * Get the hyperlink dvalue.
    */
   public DynamicValue getDLink() {
      return dlink;
   }

   /**
    * Get the hyperlink value.
    */
   public String getLinkValue() {
      return link;
   }

   /**
    * Set the hyperlink target frame. It is only used in DHTML viewer
    * @param targetFrame is the window name for hyperlink
    */
   public void setTargetFrame(String targetFrame) {
      this.targetFrame = targetFrame;
   }

   /**
    * Get the hyperlink target frame.
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
    * Set the viewsheet bookmark name.
    * @param bookmarkName the name of bookmark in vs.
    */
   public void setBookmarkName(String bookmarkName) {
      this.bookmarkName = bookmarkName;
   }

   /**
    * Get the viewsheet bookmark name.
    */
   public String getBookmarkName() {
      return bookmarkName;
   }

   /**
    * Set the viewsheet bookmark user.
    * @param bookmarkUser the user of bookmark in vs.
    */
   public void setBookmarkUser(String bookmarkUser) {
      this.bookmarkUser = bookmarkUser;
   }

   /**
    * Get the viewsheet bookmark user.
    */
   public String getBookmarkUser() {
      return bookmarkUser;
   }

   /**
    * Set the link type
    * Type should be one of the constants in inetsoft.report.Hyperlink
    * @param linkType link type
    */
   public void setLinkType(int linkType) {
      this.linkType = linkType;
   }

   /**
    * Get the link type of this hyperlink
    * @return link type
    */
   public int getLinkType() {
      return linkType;
   }

   /**
    * Set the link if is snapshot.
    * @param isSnapshot if is snapshot
    */
   public void setIsSnapshot(boolean isSnapshot) {
      this.isSnapshot = isSnapshot;
   }

   /**
    * If the link entry is snapshot.
    * @return isSnapshot
    */
   public boolean isSnapshot() {
      return isSnapshot;
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
    * Set whether to pass all report parameters to the link report.
    * @param pass true to pass all report parameters. It defaults to true.
    */
   public void setSendSelectionParameters(boolean pass) {
      passSelectionParams = pass;
   }

   /**
    * Check if to pass all report parameters to the linked report. It
    * defaults to true.
    */
   public boolean isSendSelectionParameters() {
      return passSelectionParams;
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
    * Set whether to apply the hyperlink to the row.
    */
   public void setApplyToRow(boolean applyToRow) {
      this.applyToRow = applyToRow;
   }

   /**
    * Check whether to apply the hyperlink to the row.
    */
   public boolean isApplyToRow() {
      return applyToRow;
   }

   /**
    * Get all parameter names.
    */
   public List<String> getParameterNames() {
      if(params == null) {
         return new ArrayList<>();
      }

      return params.keyList();
   }

   /**
    * Get the number of parameters defined for this link.
    */
   public int getParameterCount() {
      return (params == null) ? 0 : params.size();
   }

   /**
    * Get the label value for the parameter.
    */
   public String getParameterLabel(String name) {
      if(labels == null) {
         return null;
      }

      return labels.get(name);
   }

   /**
    * Get the field value for the parameter.
    */
   public String getParameterField(String name) {
      String result = getInternalParameter(name);

      if(result == null) {
         result = params == null ? null : params.get(name);
      }

      return result;
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

   public void clearNodeDateLevelFields() {
      if(this.nodeDateLevelFields != null) {
         this.nodeDateLevelFields.clear();
      }
   }

   /**
    * Set the none level filed.
    * @param fieldName
    */
   public void setNodeDateLevelField(String fieldName) {
      if(this.nodeDateLevelFields == null) {
         this.nodeDateLevelFields = new HashSet<>();
      }

      this.nodeDateLevelFields.add(fieldName);
   }

   /**
    * whether field is a none level date ref.
    * @param fieldName
    * @return
    */
   public boolean isNodeDateLevelField(String fieldName) {
      return this.nodeDateLevelFields != null && this.nodeDateLevelFields.contains(fieldName);
   }

   /**
    * Get internal parameters if the parameter name matches
    * internal parameter name.
    */
   private String getInternalParameter(String name) {
      if("link".equals(name)) {
         return getLink();
      }
      else if("target".equals(name)) {
         return getTargetFrame();
      }
      else if("tooltip".equals(name)) {
         return getToolTip();
      }
      else if("type".equals(name)) {
         switch(getLinkType()) {
         case Hyperlink.WEB_LINK:
            return "web";
         }
      }
      else if("sendReportParameters".equals(name)) {
         return "" + isSendReportParameters();
      }
      else if("sendSelectionParameters".equals(name)) {
         return "" + isSendSelectionParameters();
      }
      else if("disableParameterSheet".equals(name)) {
         return "" + isDisablePrompting();
      }

      return null;
   }

   /**
    * Set the label name for the parameter.
    * @param name parameter name.
    * @param label label name.
    */
   public void setParameterLabel(String name, String label) {
      if(labels == null) {
         labels = new OrderedMap();
      }

      if(name != null && name.length() > 0) {
         labels.put(name, label);
      }
   }

   /**
    * Set the field name for the parameter.
    * @param name parameter name.
    * @param field field name.
    */
   public void setParameterField(String name, String field) {
      if(!setInternalParameter(name, field)) {
         if(field == null) {
            if(params != null) {
               params.remove(name);
            }
         }
         else {
            if(params == null) {
               params = new OrderedMap();
            }

            params.put(name, field);
         }
      }
   }

   /**
    * Set the type name for the parameter.
    * @param name parameter name.
    * @param type type name.
    */
   public void setParameterType(String name, String type) {
      if(types == null) {
         types = new OrderedMap();
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
    * Set internal parameters if the parameter name matches
    * internal parameter name.
    */
   private boolean setInternalParameter(String name, String data) {
      if("link".equals(name)) {
         setLink(data == null ? "" : data.toString());
      }
      else if("target".equals(name)) {
         setTargetFrame(data == null ? "" : data.toString());
      }
      else if("tooltip".equals(name)) {
         setToolTip(data == null ? "" : data.toString());
      }
      else if("type".equals(name)) {
         if("web".equals(data)) {
            setLinkType(Hyperlink.WEB_LINK);
         }
      }
      else if("sendReportParameters".equals(name)) {
         setSendReportParameters("true".equals(data));
      }
      else if("sendSelectionParameters".equals(name)) {
         setSendSelectionParameters("true".equals(data));
      }
      else if("disableParameterSheet".equals(name)) {
         setDisablePrompting("true".equals(data));
      }
      else {
         return false;
      }

      return true;
   }

   /**
    * Remove a parameter field.
    * @param name parameter name.
    */
   public void removeParameterField(String name) {
      if(params != null) {
         params.remove(name);
      }

      if(params.size() == 0) {
         params = null;
      }
   }

   /**
    * Remove all parameter fields.
    */
   public void removeAllParameterFields() {
      params = null;
   }

   /**
    * encode sub query parameter variable
    */
   public static String getSubQueryParamVar(String qvar) {
      return Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX + qvar);
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof Hyperlink)) {
         return false;
      }

      Hyperlink hl2 = (Hyperlink) obj;
      boolean eq = link == null ? link == hl2.link : link.equals(hl2.link);

      if(!eq) {
         return false;
      }

      eq = Tool.equals(targetFrame, hl2.targetFrame) &&
           Tool.equals(tip, hl2.tip);

      if(!eq) {
         return false;
      }

      eq = Tool.equals(bookmarkName, hl2.bookmarkName) &&
           Tool.equals(bookmarkUser, hl2.bookmarkUser);

      if(!eq) {
         return false;
      }

      eq = passParams == hl2.passParams &&
         passSelectionParams == hl2.passSelectionParams &&
         disablePrompting == hl2.disablePrompting;

      if(!eq) {
         return false;
      }

      eq = linkType == hl2.linkType;

      if(!eq) {
         return false;
      }

      eq = isSnapshot == hl2.isSnapshot;

      if(!eq) {
         return false;
      }

      eq = (params == null || hl2.params == null) ? params == hl2.params :
         params.getHashtable().equals(hl2.params.getHashtable());

      if(!eq) {
         return false;
      }

      eq = (types == null || hl2.types == null) ? types == hl2.types :
         types.getHashtable().equals(hl2.types.getHashtable());

      return eq;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      if(link == null) {
         return super.toString();
      }

      return link;
   }

   /**
    * Check if this link is a report link.
    */
   private static int guessType(String link) {
      // if link has :// or starts with /, treat as http link
      if(link != null && !(link.indexOf("://") > 0 || link.startsWith("/"))) {
         try {
            if(!link.contains(".com") && !link.contains(".net") && !link.contains(".org")) {
               return Hyperlink.VIEWSHEET_LINK;
            }
         }
         catch(Exception exc) {
            LOG.warn("Failed to determine link type: " + link, exc);
         }
      }

      return Hyperlink.WEB_LINK;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         Hyperlink link2 = (Hyperlink) super.clone();
         link2.params = Tool.deepCloneMap(params);
         link2.types = Tool.deepCloneMap(types);
         link2.bookmarkName = bookmarkName;
         link2.bookmarkUser = bookmarkUser;

         return link2;
      }
      catch(Exception ex) {
         LOG.error("Failed ot clone hyperlink", ex);
      }

      return null;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<Hyperlink SendReportParameters=\"" +
                   isSendReportParameters() + "\" SendSelectionParameters=\"" +
                   isSendSelectionParameters() + "\" DisablePrompting=\"" +
                   isDisablePrompting() + "\" ApplyToRow=\"" +
                   isApplyToRow() + "\"");

      if(getLinkValue() != null) {
         writer.print(" Link=\"" + Tool.escape(getLinkValue()) + "\"");
      }

      if(getTargetFrame() != null) {
         writer.print(" TargetFrame=\"" + Tool.escape(getTargetFrame()) +
            "\"");
      }

      if(getToolTip() != null) {
         writer.print(" ToolTip=\"" + Tool.escape(getToolTip()) + "\"");
      }

      if(getBookmarkName() != null) {
         writer.print(" BookmarkName=\"" + Tool.escape(getBookmarkName()) +
            "\"");
      }

      if(getBookmarkUser() != null) {
         writer.print(" BookmarkUser=\"" + Tool.escape(getBookmarkUser()) +
            "\"");
      }

      writer.print(" LinkType=\"" + getLinkType() + "\"");
      writer.print(" IsSnapshot=\"" + isSnapshot() + "\"");

      writer.println(">");

      for(String pname : getParameterNames()) {
         if(isParameterHardCoded(pname)) {
            continue;
         }

         String field = getParameterField(pname);
         String label = getParameterLabel(pname);
         label = label == null ? field : label;

         writer.println("<ParameterField Name=\"" + Tool.escape(pname) +
            "\" Field=\"" + Tool.escape(field) +
            "\" Label=\"" + Tool.escape(label) + "\"/>");
      }

      for(String pname : getParameterNames()) {
         if(!isParameterHardCoded(pname)) {
            continue;
         }

         String field = getParameterField(pname);
         String label = getParameterLabel(pname);
         label = label == null ? field : label;
         String type = getParameterType(pname);

         writer.println("<ParameterField2 Name=\"" + Tool.escape(pname) +
            "\" Field=\"" + Tool.escape(field) +
            "\" Label=\"" + Tool.escape(label) +
            "\" Type=\"" + Tool.escape(type) + "\"/>");
      }

      writer.println("</Hyperlink>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String attr;

      if((attr = Tool.getAttribute(tag, "Link")) != null) {
         setLink(attr);
      }

      if((attr = Tool.getAttribute(tag, "TargetFrame")) != null) {
         setTargetFrame(attr);
      }

      if((attr = Tool.getAttribute(tag, "ToolTip")) != null) {
         setToolTip(attr);
      }

      if((attr = Tool.getAttribute(tag, "BookmarkName")) != null) {
         setBookmarkName(attr);
      }

      if((attr = Tool.getAttribute(tag, "BookmarkUser")) != null) {
         setBookmarkUser(attr);
      }

      if((attr = Tool.getAttribute(tag, "IsSnapshot")) != null) {
         setIsSnapshot(attr.equals("true"));
      }

      if((attr = Tool.getAttribute(tag, "LinkType")) != null) {
         setLinkType(Integer.parseInt(attr));
      }

      if((attr = Tool.getAttribute(tag, "SendReportParameters")) != null) {
         setSendReportParameters(attr.equalsIgnoreCase("true"));
      }

      if((attr = Tool.getAttribute(tag, "SendSelectionParameters")) != null) {
         setSendSelectionParameters(attr.equalsIgnoreCase("true"));
      }

      if((attr = Tool.getAttribute(tag, "DisablePrompting")) != null) {
         setDisablePrompting(attr.equals("true"));
      }

      if((attr = Tool.getAttribute(tag, "ApplyToRow")) != null) {
         setApplyToRow(attr.equals("true"));
      }

      NodeList list = Tool.getChildNodesByTagName(tag, "ParameterField");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         String name = Tool.getAttribute(elem, "Name");
         String field = Tool.getAttribute(elem, "Field");
         String label = Tool.getAttribute(elem, "Label");
         label = label == null ? field : label;

         setParameterField(name, field);
         setParameterLabel(name, label);
      }

      NodeList list2 = Tool.getChildNodesByTagName(tag, "ParameterField2");

      for(int i = 0; i < list2.getLength(); i++) {
         Element elem = (Element) list2.item(i);
         String name = Tool.getAttribute(elem, "Name");
         String field = Tool.getAttribute(elem, "Field");
         String label = Tool.getAttribute(elem, "Label");
         label = label == null ? field : label;
         String type = Tool.getAttribute(elem, "Type");

         setParameterField(name, field);
         setParameterLabel(name, label);
         setParameterType(name, type);
      }
   }

   /**
    * Check if the hyperlink is created by script.
    * @return
    */
   public boolean isScriptCreated() {
      return scriptCreated;
   }

   /**
    * Hyperlink.Ref contains the actual hyperlink and the link parameter
    * values.
    */
   public static class Ref extends HRef implements XMLSerializable {
      /**
       * Create an empty hyperlink. The setLink() must be called to set the
       * hyperlink before it can be used.
       */
      public Ref() {
         super();
      }

      /**
       * Create a hyperlink def.
       * @param link link.
       */
      public Ref(String link) {
         this(link, guessType(link), true);
      }

      /**
       * Create a hyperlink def.
       * @param link link.
       * @param passParams <tt>true</tt> if send report parameters,
       * <tt>false</tt> otherwise.
       */
      public Ref(String link, boolean passParams) {
         this(link);
         setSendReportParameters(passParams);
      }

      /**
       * Create a hyperlink def.
       * @param link link.
       * @param passParams <tt>true</tt> if send report parameters,
       * <tt>false</tt> otherwise.
       */
      public Ref(String link, boolean passParams, boolean passSelectionParams) {
         this(link, passParams);
         setSendSelectionParameters(passSelectionParams);
      }

      /**
       * Create a hyperlink ref.
       * @param link link.
       * @param type link type.
       */
      public Ref(String link, int type) {
         this(link, type, false);
      }

      /**
       * Create a hyperlink ref.
       * @param link link.
       * @param type link type.
       * @param isGuessed Check whether the type is guessed type.
       */
      public Ref(String link, int type, boolean isGuessed) {
         this();

         if(type == VIEWSHEET_LINK && !ASSET_ID_PATTERN.matcher(link).matches() && !isGuessed) {
            // default to global viewsheet
            if(!link.startsWith("1^128^__NULL__^")) {
               link = "1^128^__NULL__^" + link;
            }
         }

         setLink(link);
         setLinkType(type);
      }

      /**
       * Create a hyperlink def from a hyperlink definition.
       */
      public Ref(Hyperlink link) {
         this(link.getLink(), link.getLinkType());
         setBookmark(link);
         setTargetFrame(link.getTargetFrame());
         setToolTip(link.getToolTip());
         setSendReportParameters(link.isSendReportParameters());
         setSendSelectionParameters(link.isSendSelectionParameters());
         setDisablePrompting(link.isDisablePrompting());

         // use tip if name is not available
         if(link.getToolTip() != null && link.getToolTip().length() > 0) {
            setName(link.getToolTip());
         }

         // copy hyperlink
         // fix bug1318427277266
         for(String name : link.getParameterNames()) {
            String field = link.getParameterField(name);

            if(!link.isParameterHardCoded(name)) {
               setParameter(name, field);
            }
            else {
               String type = link.getParameterType(name);
               setParameter(name, Tool.getData(type, field));
            }
         }
      }

      /**
       * Create a hyperlink def from a table and a hyperlink definition.
       */
      public Ref(Hyperlink link, Map<?, ?> map) {
         this(link);
         setBookmark(link);
         setParameters(map, link);
         setToolTip(new ParameterFormat().format(localizeToolTip(link.getToolTip()), map));
      }

      /**
       * Create a hyperlink def from a table and a hyperlink definition.
       */
      public Ref(Hyperlink link, TableLens table, int row, int col) {
         this(link);
         setBookmark(link);
         setLinkData(link, table, row, col);
         setToolTip(new ParameterFormat().format(localizeToolTip(link.getToolTip()), table,
                                                 row, col));
      }

      /**
       * Create a hyperlink def from a drill path.
       */
      public Ref(DrillPath path) {
         setName(path.getName());
         setLinkType(path.getLinkType());
         setLink(path.getLink());
         setTargetFrame(path.getTargetFrame());
         setToolTip(path.getToolTip());
         setQuery(path.getQuery() == null ? null : path.getQuery().getQuery());
         setWsIdentifier(path.getQuery() == null ? null : path.getQuery().getWsIdentifier());
         setDisablePrompting(path.isDisablePrompting());
         setSendReportParameters(path.isSendReportParameters());
      }

      /**
       * Create a hyperlink def from a drill path.
       */
      public Ref(DrillPath path, Map<?, ?> map) {
         this(path);
         setParameters(map, path);
         setToolTip(new ParameterFormat().format(path.getToolTip(), map));
      }

      /**
       * Create a hyperlink def from a drill path.
       */
      public Ref(DrillPath path, TableLens table, int row, int col) {
         this(path);
         setDrillData(path, table, row, col);
         setToolTip(new ParameterFormat().format(path.getToolTip(), table,
                                                 row, col));
      }

      /**
       * Set drill data.
       */
      public void setDrillData(DrillPath path, TableLens table, int r, int c) {
         List<String> varNames = null;

         if(path.getLinkType() == WEB_LINK) {
            varNames = Util.parseVariablesFromLink(new ArrayList<>(), path.getLink());
         }

         Enumeration<String> names = path.getParameterNames();

         while(names.hasMoreElements()) {
            String name = names.nextElement();
            String field = path.getParameterField(name);

            if(varNames != null && varNames.contains(name)) {
               Object content = field.equals(StyleConstants.COLUMN) ? table.getObject(r, c) : field;
               content = Tool.encodeURL(String.valueOf(content));
               String var = "$(" + name + ")";
               String link = !Tool.isEmptyString(getLink()) ? getLink() : "";
               setLink(link.replace(var, String.valueOf(content)));
            }
            else {
               if(!path.isParameterHardCoded(name)) {
                  Object content = field.equals(StyleConstants.COLUMN) ? table.getObject(r, c) : field;
                  DrillSubQuery squery = path.getQuery();
                  String qname = squery == null ? null : squery.getWsIdentifier();
                  String pname = qname == null ? name : StyleConstants.PARAM_PREFIX + name;
                  setParameter(pname, content);
               }
               else {
                  String type = path.getParameterType(name);
                  setParameter(name, Tool.getData(type, field));
               }
            }
         }
      }

      /**
       * Set the link definition from link and extract parameter values
       * from a table row.
       */
      public void setLinkData(Hyperlink link, TableLens table, int row) {
         setLinkData(link, table, row, -1);
      }

      /**
       * Set the link definition from link and extract parameter values
       * from a table row.
       */
      private void setLinkData(Hyperlink link, TableLens table,
                               int row, int col)
      {
         String uri = link.getLink();

         // remember header -> column index
         Map<Object,Integer> colmap = new HashMap<>(3);

         for(int i = 0; i < table.getColCount(); i++) {
            Object header = table.getObject(0, i);

            if(header != null && !header.equals("")) {
               colmap.put(header, i);
            }
         }

         if(uri.startsWith("message:")) {
            uri = uri.substring(8);
         }

         // handle special case, extracting url from a column
         if(uri.startsWith("hyperlink:")) {
            uri = getColumnValue(table, row, col, uri.substring(10),
                                 colmap) + "";
         }
         else if(uri.indexOf("field['") >= 0) {
            String[] uriArrs = uri.split("field\\['");
            String res = "";

            for(String fragment : uriArrs) {
               int index = fragment.indexOf("']");

               if( index > 0) {
                  String field = fragment.substring(0, index);

                  fragment = getColumnValue(table, row, col, field,
                          colmap) + fragment.substring(index + 2);
               }

               res += fragment;
            }

            uri = res;
         }

         if(getLinkType() == WEB_LINK) {
            uri = uri.replaceAll("[\\r\\n|\\n|\\r]*", "");
         }

         setLink(uri);

         for(String name : link.getParameterNames()) {
            String field = link.getParameterField(name);

            if(!link.isParameterHardCoded(name)) {
               Object val = getColumnValue(table, row, col, field, colmap);
               setParameter(name, val);
            }
            else {
               String type = link.getParameterType(name);
               setParameter(name, Tool.getData(type, field));
            }
         }
      }

      /**
       * Get the column value from table or base table.
       */
      private Object getColumnValue(TableLens table, int row, int col,
                                    String field, Map<Object,Integer> colmap)
      {
         Integer c = colmap.get(field);
         RuntimeCalcTableLens calc = (RuntimeCalcTableLens)
            Util.getNestedTable(table, RuntimeCalcTableLens.class);

         if(c == null && col != -1 && calc != null) {
            Point p = calc.getFieldRowCol(field, row, col);

            if(p == null) {
               return null;
            }

            row = p.x;
            c = p.y;
         }

         if(c != null) {
            return getTableObject(table, row, c.intValue());
         }
         // don't find on the top most table, check for base tables
         else {
            int r2 = row;
            TableLens tbl2 = table;

            param:
            while(tbl2 instanceof TableFilter) {
               r2 = ((TableFilter) tbl2).getBaseRowIndex(r2);

               if(r2 > 0) {
                  tbl2 = ((TableFilter) tbl2).getTable();

                  for(int i = 0; i < tbl2.getColCount(); i++) {
                     Object header = tbl2.getObject(0, i);
                     String identifier = tbl2.getColumnIdentifier(i);

                     if(header != null && header.equals(field) ||
                        (identifier != null && (identifier.equals(field) ||
                        identifier.endsWith("." + field))))
                     {
                        return getTableObject(tbl2, r2, i);
                     }
                  }
               }
               else {
                  break;
               }
            }
         }

         return null;
      }

      private String localizeToolTip(String toolTip) {
         String localizeTooltip = Tool.localizeTextID(toolTip);

         if(localizeTooltip != null) {
            return localizeTooltip;
         }

         return toolTip;
      }

      /**
       * Get table object at special row and col.
       */
      private Object getTableObject(TableLens table, int row, int col) {
         if(table instanceof GroupedTable) {
            GroupedTable gtable = (GroupedTable) table;
            int level = gtable.getGroupColLevel(col);
            int nrow = gtable.getGroupFirstRow(row, level);
            return (nrow < 0 || gtable.getSummaryLevel(row) == 0) ?
               gtable.getObject(row, col) : gtable.getObject(nrow, col);
         }

         return table.getObject(row, col);
      }

      /**
       * Set the hyperlink name.
       */
      public void setName(String name) {
         this.name = name;
      }

      /**
       * Get the hyperlink name.
       */
      @Override
      public String getName() {
         return name;
      }

      /**
       * Set the query name.
       */
      public void setQuery(String query) {
         this.query = query;
      }

      /**
       * Get the query name.
       */
      public String getQuery() {
         return query;
      }

      public String getWsIdentifier() {
         return wsIdentifier;
      }

      public void setWsIdentifier(String wsIdentifier) {
         this.wsIdentifier = wsIdentifier;
      }

      /**
       * Set the viewsheet bookmark name.
       */
      public void setBookmarkName(String bookmarkName) {
         setParameter("__bookmarkName__", bookmarkName);
      }

      /**
       * Get the viewsheet bookmark name.
       */
      public String getBookmarkName() {
         return (String) getParameter("__bookmarkName__");
      }

      /**
       * Set the viewsheet bookmark user.
       */
      public void setBookmarkUser(String bookmarkUser) {
         setParameter("__bookmarkUser__", bookmarkUser);
      }

      /**
       * Get the viewsheet bookmark User.
       */
      public String getBookmarkUser() {
         return (String) getParameter("__bookmarkUser__");
      }

      /**
       * Set the link type
       * Type should be one of the constants in inetsoft.report.Hyperlink
       * @param linkType link type
       */
      public void setLinkType(int linkType) {
         this.linkType = linkType;
      }

      /**
       * Get the link type of this hyperlink
       * @return link type
       */
      public int getLinkType() {
         return linkType;
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
       * Set whether to pass all report parameters to the link report.
       * @param pass true to pass all report parameters. It defaults to true.
       */
      public void setSendSelectionParameters(boolean pass) {
         this.passSelectionParams = pass;
      }

      /**
       * Check if to pass all report parameters to the linked report. It
       * defaults to true.
       */
      public boolean isSendSelectionParameters() {
         return passSelectionParams;
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
       * Get the value for the parameter.
       */
      @Override
      public Object getParameter(String name) {
         Object result = getInternalParameter(name);

         if(result == null) {
            result = super.getParameter(name);
         }

         return result;
      }

      /**
       * Get internal parameters if the parameter name matches
       * internal parameter name.
       */
      private Object getInternalParameter(String name) {
         if("link".equals(name)) {
            return getLink();
         }
         else if("target".equals(name)) {
            return getTargetFrame();
         }
         else if("tooltip".equals(name)) {
            return getToolTip();
         }
         else if("type".equals(name)) {
            switch(getLinkType()) {
            case Hyperlink.WEB_LINK:
               return "web";
            }
         }
         else if("sendReportParameters".equals(name)) {
            return Boolean.valueOf(isSendReportParameters());
         }
         else if("sendSelectionParameters".equals(name)) {
            return "" + isSendSelectionParameters();
         }
         else if("disableParameterSheet".equals(name)) {
            return Boolean.valueOf(isDisablePrompting());
         }

         return null;
      }

      /**
       * Set the value for the parameter.
       * @param name parameter name.
       * @param data parameter value.
       */
      @Override
      public void setParameter(String name, Object data) {
         if(!setInternalParameter(name, data)) {
            super.setParameter(name, data);
         }
      }

      /**
       * Set internal parameters if the parameter name matches
       * internal parameter name.
       */
      private boolean setInternalParameter(String name, Object data) {
         if("link".equals(name)) {
            setLink(data == null ? "" : data.toString());
         }
         else if("target".equals(name)) {
            setTargetFrame(data == null ? "" : data.toString());
         }
         else if("tooltip".equals(name)) {
            setToolTip(data == null ? "" : data.toString());
         }
         else if("type".equals(name)) {
            if("web".equals(data)) {
               setLinkType(Hyperlink.WEB_LINK);
            }
         }
         else if("sendReportParameters".equals(name)) {
            setSendReportParameters("true".equals(data));
         }
         else if("sendSelectionParameters".equals(name)) {
            setSendSelectionParameters("true".equals(data));
         }
         else if("disableParameterSheet".equals(name)) {
            setDisablePrompting("true".equals(data));
         }
         else {
            return false;
         }

         return true;
      }

      /**
       * Set parameters of the Hyperlink.Ref.
       *
       * @param map the Map contains field-value pair values
       * @param link the HyperLink.Ref's corresponding Hyperlink
       */
      public void setParameters(Map<?, ?> map, Hyperlink link) {
         extractLink(link.getLink(), map);

         for(String name : link.getParameterNames()) {
            String field = link.getParameterField(name);

            if(!link.isParameterHardCoded(name)) {
               String field0 = field;

               if(!map.containsKey(field) && link.isNodeDateLevelField(field) &&
                  field.startsWith("None(") && field.endsWith(")"))
               {
                  field0 = field.substring(5, field.length() - 1);
               }

               if(map.containsKey(field0)) {
                  setParameter(name, map.get(field0));
               }
            }
            else {
               String type = link.getParameterType(name);
               setParameter(name, Tool.getData(type, field));
            }
         }
      }

      /**
       * Set parameters of the Hyperlink.Ref.
       *
       * @param map the Map contains field-value pair values
       * @param path the Hyperlink.Ref's corresponding DrillPath
       */
      private void setParameters(Map<?, ?> map, DrillPath path) {
         Enumeration<String> names = path.getParameterNames();
         extractLink(path.getLink(), map);

         while(names.hasMoreElements()) {
            String name = names.nextElement();
            String field = path.getParameterField(name);
            Object val = map.get(field);
            String pname = path.getQuery() == null ?
               name : StyleConstants.PARAM_PREFIX + name;
            setParameter(pname, val);
         }
      }

      /**
       * Extract the link for column hyperlink.
       */
      private void extractLink(String uri, Map map) {
         // handle special case, extracting url from a column
         if(uri == null) {
            return;
         }

         if(uri.startsWith("message:")) {
            uri = uri.substring(8);
         }

         if(uri.startsWith("hyperlink:")) {
            Object val = map.get(uri.substring(10));

            if(val instanceof DCMergeCell) {
               val = ((DCMergeCell) val).getOriginalData();
            }

            if(val != null) {
               setLink(val.toString());
            }
         }
         else if(uri.indexOf("field['") >= 0) {
            String[] uriArrs = uri.split("field\\['");
            String res = "";

            for(String fragment : uriArrs) {
               int index = fragment.indexOf("']");

               if( index > 0) {
                  String field = fragment.substring(0, index);

                  if(!map.containsKey(field) && field.startsWith("None(") && field.endsWith(")")) {
                     field = field.substring("None(".length(), field.length() - 1);
                  }

                  Object fieldValue = map.get(field);

                  if(fieldValue instanceof DCMergeCell) {
                     fieldValue = ((DCMergeCell) fieldValue).getOriginalData();
                  }

                  fragment = fieldValue + fragment.substring(index + 2);
               }

               res += fragment;
            }

            setLink(res);
         }
      }

      /**
       * Set the viewsheet bookmark info.
       */
      private void setBookmark(Hyperlink link) {
         if(link.getLinkType() != Hyperlink.VIEWSHEET_LINK) {
            return;
         }

         if(link.getBookmarkName() != null && link.getBookmarkUser() != null) {
            setBookmarkName(link.getBookmarkName());
            setBookmarkUser(link.getBookmarkUser());
         }
      }

      /**
       * Write this hyperlink definition to XML.
       */
      @Override
      public void writeXML(PrintWriter writer) {
         String link = getLink();
         String targetFrame = getTargetFrame();
         String tip = getToolTip();

         writer.print("<HyperlinkDef SendReportParameters=\"" +
            isSendReportParameters() +  "\" SendSelectionParameters=\"" +
            isSendSelectionParameters() + "\" DisablePrompting=\"" +
            isDisablePrompting() + "\"");

         if(name != null) {
            writer.print(" Name=\"" + Tool.escape(name) + "\"");
         }

         if(link != null) {
            writer.print(" Link=\"" + Tool.escape(link) + "\"");
         }

         if(query != null) {
            writer.print(" Query=\"" + Tool.escape(query) + "\"");
         }

         if(targetFrame != null && !targetFrame.equals("")) {
            writer.print(" TargetFrame=\"" + Tool.escape(targetFrame) + "\"");
         }

         if(tip != null) {
            writer.print(" ToolTip=\"" + Tool.escape(tip) + "\"");
         }

         writer.print(" LinkType=\"" + linkType + "\"");

         writer.println(">");

         Enumeration<?> keys = getParameterNames();

         while(keys.hasMoreElements()) {
            String pname = (String) keys.nextElement();
            Object field = getParameter(pname);

            writer.print("<ParameterValue Name=\"" + Tool.escape(pname) + "\"");

            String parameterType = Tool.getDataType(field);
            String str = Tool.getDataString(field);

            if(parameterType != null) {
               writer.print(" Type=\"" + parameterType + "\"");
            }

            writer.print(">");
            writer.print("<![CDATA[");
            writer.print(str);
            writer.print("]]>");

            writer.println("</ParameterValue>");
         }

         writer.println("</HyperlinkDef>");
      }

      /**
       * Parse and recreate a Hyperlink.
       */
      @Override
      public void parseXML(Element tag) throws IOException {
         String attr;

         if((attr = Tool.getAttribute(tag, "Name")) != null) {
            name = attr;
         }

         if((attr = Tool.getAttribute(tag, "Link")) != null) {
            setLink(attr);
         }

         if((attr = Tool.getAttribute(tag, "Query")) != null) {
            query = attr;
         }

         if((attr = Tool.getAttribute(tag, "TargetFrame")) != null) {
            setTargetFrame(attr);
         }

         if((attr = Tool.getAttribute(tag, "ToolTip")) != null) {
            setToolTip(attr);
         }

         if((attr = Tool.getAttribute(tag, "BookmarkName")) != null) {
            setBookmarkName(attr);
         }

         if((attr = Tool.getAttribute(tag, "BookmarkUser")) != null) {
            setBookmarkUser(attr);
         }

         if((attr = Tool.getAttribute(tag, "LinkType")) != null) {
            linkType = Integer.parseInt(attr);
         }

         if((attr = Tool.getAttribute(tag, "SendReportParameters")) != null) {
            setSendReportParameters(attr.equalsIgnoreCase("true"));
         }

         if((attr = Tool.getAttribute(tag, "SendSelectionParameters")) != null)
         {
            setSendSelectionParameters(attr.equalsIgnoreCase("true"));
         }

         if((attr = Tool.getAttribute(tag, "DisablePrompting")) != null) {
            setDisablePrompting(attr.equalsIgnoreCase("true"));
         }

         NodeList list = Tool.getChildNodesByTagName(tag, "ParameterValue");

         for(int i = 0; i < list.getLength(); i++) {
            Element elem = (Element) list.item(i);
            String name = Tool.getAttribute(elem, "Name");
            String type = Tool.getAttribute(elem, "Type");
            String value = Tool.getValue(elem);

            if(value != null && value.startsWith("\"") && value.endsWith("\""))
            {
               value = value.substring(1, value.length() - 1);
            }

            setParameter(name, Tool.getData(type, value));
         }
      }

      @Override
      public String toString() {
         StringWriter sw = new StringWriter();
         writeXML(new PrintWriter(sw, true));
         return sw.toString();
      }

      /**
       * Check if equals another Hyperlink.Ref.
       */
      @Override
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof Hyperlink.Ref)) {
            return false;
         }

         Hyperlink.Ref ref2 = (Hyperlink.Ref) obj;

         boolean result = name == null ?
            name == ref2.name : name.equals(ref2.name);

         if(!result) {
            return false;
         }

         result = linkType == ref2.linkType;

         if(!result) {
            return false;
         }

         result = passParams == ref2.passParams &&
            disablePrompting == ref2.disablePrompting;

         return result;
      }

      private String name = "hyperlink";
      private String query;
      private String wsIdentifier;
      private boolean passParams = true;
      private boolean passSelectionParams = false;
      private boolean disablePrompting;
      private int linkType = Hyperlink.WEB_LINK;
   }

   private DynamicValue dlink = new DynamicValue("");
   private String link = "";
   private String targetFrame = "";
   private String tip = null;
   private String bookmarkName = null;
   private String bookmarkUser = null;
   // fix bug1255144347369, here we shouldn't use hashtable because it
   // can't keep the order as the items are added
   private OrderedMap<String, String> params;
   private OrderedMap<String, String> labels;
   private OrderedMap<String, String> types; //for hard-coded param
   private Set<String> nodeDateLevelFields;
   private boolean passParams = true;
   private boolean passSelectionParams = false;
   private boolean disablePrompting;
   private int linkType = Hyperlink.WEB_LINK;
   private boolean isSnapshot = false;
   private boolean scriptCreated = false;
   private boolean applyToRow = false;

   private static final Pattern ASSET_ID_PATTERN =
      Pattern.compile("^[0-9]+\\^[0-9]+\\^[^\\^]+\\^[^\\^]+\\^[^\\^]+$");

   private static final Logger LOG =
      LoggerFactory.getLogger(Hyperlink.class);
}
