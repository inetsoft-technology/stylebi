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

import inetsoft.report.ReportElement;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.security.Principal;
import java.util.*;

/**
 * SourceAttr keeps the source of query binding.
 * It can be one if the following type: QUERY, MODEL, REPORT, PARAMETER.
 *
 * @version 6.0 9/30/2003
 * @author mikec
 */
public class SourceAttr extends DataAttr implements XSourceInfo, Serializable {
   /**
    * Rotated data.
    */
   public static final String ROTATED = "[rotated]";

   /**
    * Embedded data.
    */
   public static final String EMBEDED = "EMBEDED_DATA";

   /**
    * Create a default source attr.
    */
   public SourceAttr() {
      this(QUERY);
   }

   /**
    * Create the source attr with specified type.
    * @type source type defined in XSourceInfo.
    */
   public SourceAttr(int type) {
      this(type, "", "");
   }

   /**
    * Create source attr with specified type and source name.
    * @type source type defined in XSourceInfo.
    */
   public SourceAttr(int type, String path) {
      this.type = type;
      analyzePath(path);
   }

   /**
    * Create source attr with specified type, prefix and source name.
    * @type source type defined in XSourceInfo.
    */
   public SourceAttr(int type, String prefix, String source) {
      this.type = type;
      this.prefix = prefix == null ? "" : prefix;
      this.source = source;
   }

   /**
    * Create source attr with specified source name.
    */
   public SourceAttr(String path) {
      analyzePath(path);
   }

   /**
    * Set the source type.
    * @type source type defined in XSourceInfo.
    */
   @Override
   public void setType(int tp) {
      this.type = tp;
   }

   /**
    * Get the source type.
    */
   @Override
   public int getType() {
      return type;
   }

   /**
    * Set the source prefix, such as datasource name, model name.
    */
   @Override
   public void setPrefix(String pre) {
      if(pre != null) {
         this.prefix = pre;
      }
   }

   /**
    * Get the source prefix.
    */
   @Override
   public String getPrefix() {
      return prefix;
   }

   /**
    * Set the source name.
    */
   @Override
   public void setSource(String name) {
      if(name != null) {
         analyzePath(name);
      }
      else {
         // null source is not allowed
         source = "";
      }
   }

   /**
    * Get the source name.
    */
   @Override
   public String getSource() {
      return source;
   }

   /**
    * Check if the source is blank.
    */
   public boolean isBlank() {
      return type == NONE || source == null || source.equals("");
   }

   /**
    * Check if the sort info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return isBlank();
   }

   /**
    * Get the number of sources that are joined with this source.
    */
   public int getJoinSourceCount() {
      return joinSources.size();
   }

   /**
    * Get a joined source.
    */
   public SourceAttr getJoinSource(int idx) {
      return joinSources.get(idx);
   }

   /**
    * Add a source to be joined with this source.
    */
   public void addJoinSource(SourceAttr source) {
      joinSources.add(source);
   }

   /**
    * Remove all join sources.
    */
   public void removeAllJoinSources() {
      joinSources.clear();
   }

   /**
    * Get the number of relations defined for the source joins.
    */
   public int getJoinRelationCount() {
      return joinRels.size();
   }

   /**
    * Get a join relation.
    */
   public JoinInfo getJoinRelation(int idx) {
      return joinRels.get(idx);
   }

   /**
    * Add a relation to the joins.
    */
   public void addJoinRelation(JoinInfo relation) {
      joinRels.add(relation);
   }

   /**
    * Remove all join relations.
    */
   public void removeAllJoinRelations() {
      joinRels.clear();
   }

   /**
    * Write XML segment of the source attr.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      Principal user = ThreadContext.getContextPrincipal();
      Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
      setProperty("label", catalog.getString(source));
      setProperty("plabel", catalog.getString(prefix));
      setProperty("description", getDescription());

      if(getAssetDescription() != null) {
         setProperty("assetDescription", getAssetDescription());
      }

      writer.print("type=\"" + type + "\" source=\"" + Tool.escape(source) +
         "\" " + (prefix == null ? "" : "prefix=\"" + Tool.escape(prefix) +
         "\""));
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      if(joinSources.size() > 0) {
         writer.println("<joinSources>");

         for(int i = 0; i < getJoinSourceCount(); i++) {
            getJoinSource(i).writeXML(writer);
         }

         writer.println("</joinSources>");
      }

      if(getType() == ASSET) {
         // CAUTION! This tag will be used to find dependency
         writer.println();
         writer.print("<assetDependency>");
         writer.print("<![CDATA[" + source + "]]>");
         writer.println("</assetDependency>");
      }

      if(joinRels.size() > 0) {
         writer.println("<joinRelations>");

         for(int i = 0; i < getJoinRelationCount(); i++) {
            getJoinRelation(i).writeXML(writer);
         }

         writer.println("</joinRelations>");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      String str;

      if((str = Tool.getAttribute(tag, "prefix")) != null) {
         prefix = str;
      }

      if((str = Tool.getAttribute(tag, "type")) != null) {
         setType(Integer.parseInt(str));
      }

      if(getType() == EMBEDDED_DATA) {
         source = EMBEDED;
      }
      else if((str = Tool.getAttribute(tag, "source")) != null) {
         source = str;
      }
   }

   @Override
   protected void parseContents(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "joinSources");

      if(node != null) {
         NodeList list = Tool.getChildNodesByTagName(node, "filter");

         for(int i = 0; i < list.getLength(); i++) {
            Element cnode = (Element) list.item(i);

            SourceAttr attr = new SourceAttr();

            attr.parseXML(cnode);
            addJoinSource(attr);
         }
      }

      node = Tool.getChildNodeByTagName(tag, "joinRelations");

      if(node != null) {
         NodeList list = Tool.getChildNodesByTagName(node, "joinInfo");

         for(int i = 0; i < list.getLength(); i++) {
            Element cnode = (Element) list.item(i);
            JoinInfo info = new JoinInfo();

            info.parseXML(cnode, this);
            addJoinRelation(info);
         }
      }
   }

   /**
    * Analyze a given query string, seperate it into prefix and source.
    * Used for backward compatibility purpose and for set query in java script.
    *
    * In older version stylereport(4.5 before),
    *   we used only one property to save the source.
    *     For query it save the query name "qname"
    *     for a parameter it save "variable::paraname"
    *     for a report element, it save "chart::chart1", "rtable::table2" like.
    *     for a data model, it save "Data Model/model name".
    *
    *  In version 5.1 or before.
    *   we use type for source type, but keep prefix for backward compatibility.
    *   prefix as "parameter" for a parameter
    *   prefix in the prefixes list for a report element source
    *   prefix as "Data Model" for a data model.
    *
    *  This logic it used to extract the type from the property or 5.1 version
    *   attrs.
    *
    *  In version 6.0 or later
    *   we save data source name as prefix for both query and logic model
    *   and save prefix to blank for local query, parameter and report element.
    *   for element based source, we put "table[rotated]" like string in source.
    *   the type will be used to flag the diffrence between each source.
    *
    *  Another place of using source is in java script
    *   where user use only one string to specify a source attr.
    *  In old version, user use "variable::paraname" for parameter
    *           "parameter/paraname" for parameter
    *           "chart::chart2" or "rchart::chart2" for report element
    *           "chart/chart2" or "chart/chart2" for report element
    *           "queryname" for query
    *           "Data Model/model name" for logic model
    *  In 6.0 or later:
    *   We require user use:
    *           "variable::paraname" for parameter
    *           "element::table2" or "element::table2[rotated]" for element
    *           "dsname/query::qname" for query
    *           "dsname/cube::cubename" for OLAP
    *   and     "dsname/logicModel::modelname" for logic model
    *   Logic MUST be kept for parse both these two kind of string.
    */
   private void analyzePath(String path) {
      String pre = getPrefix(path);
      String suf = getSuffix(path);

      if(pre != null) {
         prefix = pre;
      }

      if(suf != null) {
         source = suf;
      }

      if(prefix == null || source == null || source.startsWith("query::")){
         type = QUERY;
      }
      else if(prefix.equals("parameter") || source.startsWith("variable::")) {
         type = PARAMETER;
      }
      else if(prefix.equals("Data Model") || source.startsWith("logicModel::")){
         // will raise potential backward compatibility problem here,
         // since the old version do not save data source name for Data Model
         // @comment by mikec 2003-10-20
         type = MODEL;
      }
      else if(prefixes.get(prefix) != null ||
              source.startsWith("chart::") || source.startsWith("rchart::") ||
              source.startsWith("table::") || source.startsWith("rtable::") ||
              source.startsWith("section::") || source.startsWith("rsection::")
              || source.startsWith("element::")) {
         if(source.startsWith("rchart::") || source.startsWith("rtable::") ||
            source.startsWith("rsection::"))
         {
            source = source.substring(source.indexOf("::") + 2) + ROTATED;
         }

         type = REPORT;
      }
      else if(source.startsWith("cube::")) {
         type = CUBE;
      }
      else {
         if(path.startsWith("ws:")) {
            // @davidd 04-2011, Added support for worksheets, using the syntax
            // from runQuery: "ws:global:WSName"
            prefix = "";
            type = ASSET;
            source = AssetEntry.createAssetEntry(path).toIdentifier();
         }
         else {
            type = QUERY;
         }
      }

      if(source != null && source.indexOf("::") >= 0) {
         source = source.substring(source.indexOf("::") + 2);
      }
   }

   /**
    * Get the prefix of a query string.
    */
   private String getPrefix(String path) {
      String dxname = null;

      if(path != null) {
         int idx = path.lastIndexOf('/');
         int idx2 = path.indexOf('^');

         if(idx2 < 0 && idx > 0) {
            dxname = path.substring(0, idx);
         }
      }

      return dxname;
   }

   /**
    * Get the suffix of a query string.
    */
   private String getSuffix(String path) {
      String qname = null;

      if(path != null) {
         int idx = path.lastIndexOf('/');
         int idx2 = path.indexOf('^');

         if(idx2 < 0 && idx > 0) {
            qname = path.substring(idx + 1);
         }
         else {
            qname = path;
         }
      }

      return qname;
   }

   /**
    * Get the description.
    */
   public String getDescription() {
      if(type != ASSET || source == null || source.length() == 0) {
         Principal user = ThreadContext.getContextPrincipal();
         Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);

         if(type == EMBEDDED_DATA) {
            return catalog.getString("EMBEDDED_DATA");
         }

         return catalog.getString(getSource());
      }
      else {
         AssetEntry entry = AssetEntry.createAssetEntry(source);
         return entry.getDescription();
      }
   }

   /**
    * Get asset description.
    */
   public String getAssetDescription() {
      if(type != ASSET || source == null || source.length() == 0) {
         return null;
      }
      else {
         AssetEntry entry = AssetEntry.createAssetEntry(source);
         return entry.getDescription(true, true);
      }
   }

   /*
    * Get the string representation.
    */
   public String toString() {
      if(type != ASSET || source == null || source.length() == 0) {
         String result = source;

         if(prefix != null && prefix.length() > 0) {
            result = prefix + "." + result;
         }

         return result;
      }
      else {
         AssetEntry entry = AssetEntry.createAssetEntry(source);
         return entry.getDescription();
      }
   }

   /*
    * Get the string representation.
    */
   public String toView() {
      if(type != ASSET || source == null || source.length() == 0) {
         Principal user = ThreadContext.getContextPrincipal();
         Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
         String result = catalog.getString(getSource());

         if(type == EMBEDDED_DATA) {
            result = Catalog.getCatalog().getString("Embedded Data");
         }
         else if(prefix != null && prefix.length() > 0 && type != REPORT) {
            result = catalog.getString(prefix) + "." + result;
         }

         return result;
      }
      else {
         String path = source;
         AssetEntry entry = AssetEntry.createAssetEntry(path);
         return entry.getDescription();
      }
   }

   @Override
   public Object clone() {
      try {
         SourceAttr attr = (SourceAttr) super.clone();

         attr.joinSources = Tool.deepCloneCollection(joinSources);
         attr.joinRels = Tool.deepCloneCollection(joinRels);

         return attr;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Get the hashcode based on source name.
    */
   public int hashCode() {
      return source != null ? source.hashCode() : super.hashCode();
   }

   /**
    * Test if equals another object.
    */
   public boolean equals(Object obj) {
      return equals(obj, false);
   }

   /**
    * Test if equals another object.
    */
   public boolean equals(Object obj, boolean all) {
      if(obj == null || !(obj instanceof SourceAttr)) {
         return false;
      }

      SourceAttr sattr = (SourceAttr) obj;
      boolean eq = Tool.equals(getProperty(ReportElement.XNODEPATH),
                               sattr.getProperty(ReportElement.XNODEPATH));

      if(!eq) {
         return false;
      }

      // @by mikec, since we do not allow duplicate query name, we do
      // not need compare prefix when type is not MODEL.
      // This is because in old version we do not save query source name
      // as prefix, which will not carry to new version, so if we compare
      // prefix here, it will cause equals return false and will cause
      // column selection be repopulated in designer UI.(a bc problem)
      if(this.type == sattr.getType()) {
         eq = Tool.equals(this.source, sattr.getSource()) &&
            (Tool.equals(this.prefix, sattr.getPrefix()) || this.type != MODEL);

         if(all) {
            eq = eq && equalsContent(sattr);
         }

         return eq;
      }

      return false;
   }

   /**
    * Test if equals another object.
    */
   public boolean equalsContent(SourceAttr sattr) {
      if(sattr == null) {
         return false;
      }

      if(this.getJoinSourceCount() != sattr.getJoinSourceCount()) {
         return false;
      }

      for(int i = 0; i < this.getJoinSourceCount(); i++) {
         if(!this.getJoinSource(i).equals(sattr.getJoinSource(i))) {
            return false;
         }
      }

      if(this.getJoinRelationCount() != sattr.getJoinRelationCount()) {
         return false;
      }

      for(int i = 0; i < this.getJoinRelationCount(); i++) {
         if(!this.getJoinRelation(i).equalsContent(sattr.getJoinRelation(i))) {
            return false;
         }
      }

      return true;
   }

   /**
    * Test if the source attr is null.
    * @return true if is, false otherwise
    */
   public boolean isNull() {
      return (prefix == null || Tool.equals(prefix, "")) &&
         (source == null || Tool.equals(source, ""));
   }

   /**************************************************************************
     the prefix name, it is used to recognize the source type in older version,
     and more often used to save the datasource name.
     A valid prefix can be:
        datasource1
        "Data Model"  (in old version)
        "parameter"   (in old version)
        "chart"       (in old version)
        "table"       (in old version)
        "section"     (in old version)
   ***************************************************************************/
   private String prefix = "";

   /**************************************************************************
     the source name, it is a query name, a logic model name, a parameter name
     or an element name plus a rotation flag. example source name can be:
       Query1
       LogicModel2
       Parameter1
       Table1
       Chart1[rotated]
   ***************************************************************************/
   private String source = "";
   private int type = QUERY;
   private List<SourceAttr> joinSources = new ArrayList<>();
   private List<JoinInfo> joinRels = new ArrayList<>();

    private static Map<String, Byte> prefixes; //prefix for report data.
   static {
      prefixes = new HashMap<>();
      Byte sth = Byte.valueOf((byte)0);
      prefixes.put("chart", sth);
      prefixes.put("table", sth);
      prefixes.put("section", sth);
   }
}
