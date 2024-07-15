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
package inetsoft.uql.xmla;

import inetsoft.uql.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * XMLAQuery object represents a query for MDX request in XMLA.
 *
 * @version 10.3, 4/8/2010
 * @author InetSoft Technology Corp
 */
public class XMLAQuery extends XQuery {
   /**
    * Create a query object with the specified type.
    */
   public XMLAQuery() {
      super(XDataSource.XMLA);
      setName("XMLAQuery");
   }

   /**
    * Get MDX String.
    */
   public String getMDXDefinition() {
      MDXHelper helper = MDXHelper.getMDXHelper(this);
      return helper.generateSentence();
   }


   /**
    * Get MDX String array.
    */
   public String[] getMDXDefinitions() {
      if("true".equals(getProperty("showDetail"))) {
         MDXHelper helper = MDXHelper.getMDXHelper(this);

         return helper.generateDrillThrough();
      }

      return new String[0];
   }

   /**
    * Get the XSelection object.
    */
   @Override
   public XSelection getSelection() {
      return null;
   }

   /**
    * Get the output type of the query. The return value is either the
    * root of a subtree of the a type tree, or a one level tree with
    * each child representing a table column.
    */
   @Override
   public XTypeNode getOutputType(Object session, boolean full) {
      return null;
   }

   /**
    * Set cube name.
    * @param cube cube name.
    */
   public void setCube(String cube) {
      this.cube = cube;
   }

   /**
    * Get cube name.
    * @return cube name.
    */
   public String getCube() {
      return cube;
   }

   /**
    * Get the runtime cube
    *
    * @return
    */
   public XCube getRuntimeCube() {
      return runtimeCube;
   }

   /**
    * Set the runtime cube.
    *
    * @param runtimeCube
    */
   public void setRuntimeCube(XCube runtimeCube) {
      this.runtimeCube = runtimeCube;
   }

   /**
    * Set filter node.
    * @param where0 the condition filter node.
    */
   public void setFilterNode(XNode where0) {
      convertNode(where0);
      this.where = where0;
   }

   /**
    * Get filter node.
    * @return the condition filter node.
    */
   public XNode getFilterNode() {
      return where;
   }

   /**
    * Get ConditionList.
    * @return condition list;
    */
   public ConditionList getConditionList() {
      return conditions;
   }

   /**
    * Set ConditionList.
    * @param conds the specified condition list.
    */
   public void setConditionList(ConditionList conds) {
      this.conditions = conds;
   }

   /**
    * Get column count.
    * @return fields count.
    */
   public int getColumnCount() {
      return dims.size() + measures.size();
   }

   /**
    * Get column count.
    * @return fields count.
    */
   public int getMembersCount() {
      return dims.size();
   }

   /**
    * Get column count.
    * @return fields count.
    */
   public int getMeasuresCount() {
      return measures.size();
   }

   /**
    * Get data ref of dimension member.
    * @param index the column index of expected data ref.
    * @return expected data ref.
    */
   public DataRef getMemberRef(int index) {
      return (DataRef) dims.get(index);
   }

   /**
    * Get dimension member index.
    * @param ref the specified dimension member.
    * @return the member index.
    */
   public int indexOfMember(DataRef ref) {
      return dims.indexOf(ref);
   }

   /**
    * Get selected dimensions on axis.
    * @return dimension unique name(s) if any.
    */
   public Collection<Dimension> getSelectedDimensions() {
      Set<Dimension> dims = new HashSet();

      for(int i = 0; i< getMembersCount(); i++) {
         dims.add(XMLAUtil.getDimension(getDataSource().getFullName(), cube,
            getMemberRef(i)));
      }

      return dims;
   }

   /**
    * Get touched dimensions in select and where sub clause.
    * @return all related dimensions of this query.
    */
   public Collection<Dimension> getTouchedDimensions() {
      Collection<Dimension> dims = getSelectedDimensions();
      getDimensions(where, dims);

      return dims;
   }

   /**
    * Get all dimensions that are contained in conditions.
    */
   private void getDimensions(XNode conditions, Collection<Dimension> dims) {
      if(conditions == null) {
         return;
      }

      int cnt = conditions.getChildCount();

      if(cnt == 0) {
         ConditionItem citem = (ConditionItem) conditions.getValue();
         DataRef ref = citem.getAttribute();

         if(ref == null) {
            return;
         }

         dims.add(
            XMLAUtil.getDimension(getDataSource().getFullName(), cube, ref));
         return;
      }

      for(int i = 0; i < cnt; i++) {
         getDimensions(conditions.getChild(i), dims);
      }
   }

   /**
    * Get suitable level count.
    */
   protected int getLevelCount(Dimension dim) {
      int mcnt = getMembersCount();
      int level = 0;

      for(int i = 0; i< mcnt; i++) {
         DataRef ref = getMemberRef(i);
         Dimension dimension =
            XMLAUtil.getDimension(getDataSource().getFullName(), cube, ref);

         if(!Tool.equals(dimension, dim)) {
            continue;
         }

         level = Math.max(level, getLevelNumber(ref));
      }

      return Math.min(level + 1, dim.getLevelCount());
   }

   /**
    * Get data ref of measure.
    * @param index the column index of expected measure.
    * @return expected measure.
    */
   public DataRef getMeasureRef(int index) {
      return (DataRef) measures.get(index);
   }

   /**
    * Get measure index.
    * @param name measure name.
    * @return measure index.
    */
   public int indexOfMeasure(String name) {
      for(int i = 0; i < getMeasuresCount(); i++) {
         DataRef ref = getMeasureRef(i);

         if(isRight(ref, name)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Check if a data ref has the specified name.
    */
   protected boolean isRight(DataRef ref, String name) {
      if(ref.getAttribute().equals(name)) {
         return true;
      }

      if(ref instanceof ColumnRef) {
         if(Tool.equals(((ColumnRef) ref).getCaption(), name)) {
            return true;
         }
      }

      if("true".equals(getProperty("richlist"))) {
         if(XMLAUtil.getAttribute(ref).equals(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Add a dimension member.
    * @param ref the specified dimension member dataref.
    */
   public void addMemberRef(DataRef ref) {
      ref = convertDataRef(ref);

      if(!dims.contains(ref)) {
         dims.add(ref);
      }
   }

   /**
    * Add a measure.
    * @param ref the specified measure dataref.
    */
   public void addMeasureRef(DataRef ref) {
      ref = convertDataRef(ref);

      if(!measures.contains(ref)) {
         measures.add(ref);
      }
   }

   /**
    * Get levels DataRef in the same dimension.
    * @param dim the specified dimension name.
    * @return an array of levels DataRef.
    */
   public DataRef[] getMemberRefs(String dim) {
      return getMemberRefs(dim, true);
   }

   /**
    * Get levels DataRef in the same dimension.
    * @param dim the specified dimension name.
    * @param all <tt>true</tt> to not ignore duplicate ones.
    * @return an array of levels DataRef.
    */
   public DataRef[] getMemberRefs(String dim, boolean all) {
      if(dim == null) {
         return new DataRef[0];
      }

      XCube cb = XMLAUtil.getCube(getDataSource().getFullName(), cube);
      XDimension dim0 = cb.getDimension(dim);

      if(dim0 instanceof Dimension) {
         dim = ((Dimension) dim0).getIdentifier();
      }

      List<DataRef> mbrs = getMembers(dim, all);

      Collections.sort(mbrs, new Comparator() {
         @Override
         public int compare(Object o1, Object o2) {
            if(o1 instanceof DataRef && o2 instanceof DataRef) {
               return getLevelNumber((DataRef) o1) -
                  getLevelNumber((DataRef) o2);
            }

            return 0;
         }

         public boolean equals(Object obj) {
            return obj instanceof Comparator;
         }
      });

      DataRef[] refs = new DataRef[mbrs.size()];
      mbrs.toArray(refs);

      return refs;
   }

   /**
    * Get member DataRefs in the same dimension.
    */
   protected List<DataRef> getMembers(String dim, boolean all) {
      List<DataRef> mbrs = new ArrayList();
      HashSet levels = new HashSet();

      for(int i = 0; i < getMembersCount(); i++) {
         DataRef ref = getMemberRef(i);

         if(!dim.equals(getEntity(ref))) {
            continue;
         }

         if(!all && levels.contains(getAttribute(ref))) {
            continue;
         }

         mbrs.add(ref);
         levels.add(getAttribute(ref));
      }

      return mbrs;
   }

   /**
    * Get dimension member level number.
    */
   public int getLevelNumber(DataRef ref) {
      XCube cb = XMLAUtil.getCube(getDataSource().getFullName(), getCube());
      String dim = getEntity(ref);
      String level = getAttribute(ref);
      XDimension dimension = cb.getDimension(dim);

      return dimension.getScope(level);
   }

   /**
    * Get the level above this level in the dimension.
    */
   public DataRef getUpperMember(DataRef ref, int offset) {
      int idx = getLevelNumber(ref);

      if(idx <= 0) {
         return null;
      }

      List<DataRef> mbrs = getMembers(getEntity(ref), false);

      for(int i = 0; i < mbrs.size(); i++) {
         int idx0 = getLevelNumber(mbrs.get(i));

         if(idx0 == idx - offset) {
            return mbrs.get(i);
         }
      }

      return null;
   }

   /**
    * Calculate the diff of level numbers.
    */
   public int diffLevel(DataRef ref0, DataRef ref1) {
      if(Tool.equals(ref0, ref1)) {
         return 0;
      }

      String dim0 = getEntity(ref0);
      String dim1 = getEntity(ref1);

      // meaningless if compare 2 levels from different dimensions
      if(!Tool.equals(dim0, dim1)) {
         return 0;
      }

      String ds = getDataSource().getFullName();
      String cb = getCube();

      return XMLAUtil.getLevelNumber(ref0, ds, cb) -
         XMLAUtil.getLevelNumber(ref1, ds, cb);
   }

   /**
    * Set the dimension hierarchy expanded paths. For example, if
    * the 'State' and 'City' are show, and only 'NJ' is expanded (cities in
    * NJ visible), the map contains: ["State", ["NJ"]]
    */
   public void setExpandedPaths(Map<String,Set<String>> expanded) {
      this.expanded = expanded;
   }

   /**
    * Get the dimension hierarchy expanded paths.
    */
   public Map<String,Set<String>> getExpandedPaths() {
      return expanded;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      StringBuilder buffer = new StringBuilder();
      buffer.append("XMLAQuery[");
      buffer.append("Dimensions:");
      buffer.append(dims);
      buffer.append(" Measures:");
      buffer.append(measures);
      buffer.append(" Conditions:");
      buffer.append(conditions);
      buffer.append("]");

      return buffer.toString();
   }

   /**
    * Check if a condition list contains measure column.
    */
   private boolean containsMeasure(ConditionList conds) {
      for(int i = 0; i < conds.getSize(); i++) {
         HierarchyItem item = conds.getItem(i);

         if(item instanceof ConditionItem) {
            DataRef ref = ((ConditionItem) item).getAttribute();
            String name = getAttribute(ref);

            if(indexOfMeasure(name) >= 0) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Convert filter node for backward compatibility.
    */
   private void convertNode(XNode node) {
      int cnt = node.getChildCount();

      if(cnt == 0) {
         ConditionItem citem = (ConditionItem) node.getValue();
         DataRef ref0 = citem.getAttribute();
         DataRef ref = convertDataRef(ref0);

         if(!Tool.equals(ref0, ref)) {
            citem.setAttribute(ref);
            replaceCondValues(citem);
            node.setAttribute("dimension", ref.getEntity());
         }
         else if(shouldConvert(citem.getCondition())) {
            replaceCondValues(citem);
         }

         return;
      }

      for(int i = 0; i < cnt; i++) {
         convertNode(node.getChild(i));
      }
   }

   /**
    * Check if it is necessary to convert condition values.
    */
   private boolean shouldConvert(Condition cond) {
      if(cond == null) {
         return false;
      }

      Object obj = cond.getValue(0);

      if(obj instanceof String) {
         String str = (String) obj;
         return str.indexOf('[') < 0;
      }

      return false;
   }

   /**
    * Convert condition values of a ConditionItem.
    */
   private void replaceCondValues(ConditionItem citem) {
      Condition cond = citem.getCondition();

      if(cond == null) {
         return;
      }

      XMLATableNode cached = null;

      try {
         XMLAQuery q = new XMLAQuery();
         q.setDataSource(getDataSource());
         q.setCube(getCube());
         q.addMemberRef(citem.getAttribute());
         q.setProperty("noEmpty", "false");
         XMLAHandler xhandler = new XMLAHandler();
         ExecuteHandler handler = xhandler.getExecuteHandler(q);
         cached = handler.getCachedData(null, true);
      }
      catch(Exception e) {
         // just ignore
      }

      if(cached == null) {
         return;
      }

      for(int i = 0; i < cond.getValueCount(); i++) {
         Object obj = cond.getValue(i);

         if(obj instanceof String) {
            String fullCaption = (String) obj;
            MemberObject mobj = cached.findMember(fullCaption, false);

            if(mobj == null && !XMLAUtil.isDisplayFullCaption()) {
               int last = fullCaption.length() - 1;
               int begin = fullCaption.lastIndexOf(".[");

               if(begin >= 0 && last > begin) {
                  fullCaption = fullCaption.substring(begin + 2, last);
                  mobj = cached.findMember(fullCaption, false);
               }
            }

            if(mobj == null) {
               continue;
            }

            cond.setValue(i, mobj.uName);
         }
      }
   }

   /**
    * Convert data ref for backward compatibility.
    */
   protected DataRef convertDataRef(DataRef ref) {
      String entity0 = null;
      String attr0 = null;
      String entity = ref.getEntity();
      String attr = ref.getAttribute();

      if(entity != null && entity.indexOf(Assembly.CUBE_VS) < 0 &&
         attr != null && attr.indexOf('[') >= 0)
      {
         return ref;
      }

      try {
         String dataSource = getDataSource().getFullName();
         Dimension dim = XMLAUtil.getDimension(dataSource, cube, ref);
         DimMember level = XMLAUtil.getLevel(dataSource, cube, ref);

         if(dim != null) {
            entity0 = dim.getIdentifier();
         }

         if(level != null) {
            attr0 = level.getUniqueName();
         }

         if(entity0 == null && attr0 == null &&
            (ref.getRefType() & DataRef.CUBE_MEASURE) != DataRef.CUBE_MEASURE)
         {
            Cube cubeObj = (Cube) XMLAUtil.getCube(dataSource, cube);

            if(Cube.ESSBASE.equals(cubeObj.getType())) {
               String[] names = XMLAUtil.getNames(ref);
               entity0 = names[0];
               attr0 = names[1];
               int idx = entity0.lastIndexOf('.');
               entity0 = idx >= 0 ? entity0.substring(idx + 1) : entity0;
               dim = XMLAUtil.getDimension(dataSource, cube, entity0);

               if(dim != null) {
                  entity0 = dim.getIdentifier();
                  level = (DimMember) dim.getLevel(attr0);

                  if(level == null) {
                     String caption = ((ColumnRef) ref).getCaption();
                     idx = caption.indexOf('.');
                     caption = idx >= 0 ? caption.substring(idx + 1) : caption;
                     level = (DimMember) dim.getLevel(caption);
                  }

                  if(level != null) {
                     attr0 = level.getUniqueName();
                  }
               }
            }
         }

         if((ref.getRefType() & DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE) {
            XCube cube0 = XMLAUtil.getCube(getDataSource().getFullName(), cube);

            if(cube0 != null) {
               Measure measure = (Measure) cube0.getMeasure(attr);

               if(measure != null) {
                  attr0 = measure.getUniqueName();
               }
               else if("true".equals(getProperty("richlist"))) {
                  measure = (Measure) cube0.getMeasure(
                     XMLAUtil.getAttribute(ref));

                  if(measure != null) {
                     attr0 = measure.getUniqueName();
                  }
               }
            }
         }

         if(entity0 != null || attr0 != null) {
            File tmpFile = FileSystemService.getInstance().getCacheTempFile("xmla", "tmp");
            PrintWriter pwriter = new PrintWriter(new OutputStreamWriter(
               new BufferedOutputStream(new FileOutputStream(tmpFile)),
               "UTF8"));
            ref.writeXML(pwriter);
            pwriter.close();

            try(InputStream inputStream = new FileInputStream(tmpFile)) {
               Document doc = Tool.safeParseXMLByDocumentBuilder(inputStream);
               Element elem = doc.getDocumentElement();
               replaceValue(elem, entity0, true);
               replaceValue(elem, attr0, false);

               return AbstractDataRef.createDataRef(elem);
            }
         }
      }
      catch(Exception e) {
      }

      return ref;
   }

   /**
    * Get entity of data ref.
    */
   protected String getEntity(DataRef ref) {
      return ref.getEntity();
   }

   /**
    * Get attribute of data ref.
    */
   protected String getAttribute(DataRef ref) {
      return ref.getAttribute();
   }

   /**
    * Replace entity or attribute.
    */
   private void replaceValue(Element elem, String val, boolean isEntity) {
      if(elem == null || val == null) {
         return;
      }

      String attr = isEntity ? "entity" : "attribute";
      elem.setAttribute(attr, val);
      Element dnode = Tool.getChildNodeByTagName(elem, "dataRef");
      replaceValue(dnode, val, isEntity);
   }

   Map<Integer, MemberObject> getAncestor(String groupName) {
      return groupAcestor.get(groupName);
   }

   void setAncestor(String groupName, Map<Integer, MemberObject> ancestors) {
      groupAcestor.put(groupName, ancestors);
   }

   void clearAncestors() {
      groupAcestor.clear();
   }
   
   /**
    * Generate the XML segment to represent XMLAQuery
    */
   @Override
   public void writeXML(PrintWriter writer) {
      //@temp yanie: writeXML&parseXML only used when RemoteEngine execute
      writer.println("<query_xmla>");
      
      if(cube != null) {
         writer.println("<cube><![CDATA[" + cube + "]]></cube>");
      }
      
      if(dims != null && dims.size() > 0) {
         writer.println("<dims>");
         
         for(int i = 0; i < dims.size(); i++) {
            DataRef dim = (DataRef) dims.get(i);
            dim.writeXML(writer);
         }
         
         writer.println("</dims>");
      }
      
      if(measures != null && measures.size() > 0) {
         writer.println("<measures>");
         
         for(int i = 0; i < measures.size(); i++) {
            DataRef measure = (DataRef) measures.get(i);
            measure.writeXML(writer);
         }
         
         writer.println("</measures>");
      }
      
      writeAggregateInfo(writer);
      
      if(conditions != null && conditions.getSize() > 0) {
         conditions.writeXML(writer);
      }

      super.writeXML(writer);
      writer.println("</query_xmla>");
   }
   
   /**
    * Parse the XML element that contains information on XMLAQuery.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      //@temp yanie: writeXML&parseXML only used when RemoteEngine execute
      NodeList nlist = Tool.getChildNodesByTagName(root, "cube");

      if(nlist != null && nlist.getLength() > 0) {
         cube = Tool.getValue(nlist.item(0));
      }
      
      parseDimMeas(dims, root, "dims");
      parseDimMeas(measures, root, "measures");
      parseAggregateInfo(root);
      
      nlist = Tool.getChildNodesByTagName(root, "conditions");
      
      if(nlist != null && nlist.getLength() > 0) {
         conditions.parseXML((Element) nlist);
      }

      super.parseXML(root);
   }

   private void parseDimMeas(ArrayList cols, Element root, String tag) {
      NodeList nlist =  Tool.getChildNodesByTagName(root, tag);

      if(nlist != null && nlist.getLength() > 0) {
         nlist =
            Tool.getChildNodesByTagName((Element) nlist.item(0), "dataRef");

         for(int i = 0; i < nlist.getLength(); i++) {
            Element element = (Element) nlist.item(i);

            try {
               DataRef ref = AbstractDataRef.createDataRef(element);
               cols.add(ref);
            }
            catch(Exception e) {
               LOG.error(
                  "Failed to parse xml: " + e.getMessage(), e);
            }
         }
      }
   }

   protected void writeAggregateInfo(PrintWriter writer) {
      //do nothing
   }
   
   protected void parseAggregateInfo(Element root) {
      //do nothing
   }

   private String cube;
   private ArrayList dims = new ArrayList();
   private ArrayList measures = new ArrayList();
   private XNode where;// root XNode of where clause
   private ConditionList conditions = new ConditionList();
   private Map<String, Map<Integer, MemberObject>> groupAcestor = new HashMap<>();
   private Map<String,Set<String>> expanded;
   private XCube runtimeCube;
   private static final Logger LOG =
      LoggerFactory.getLogger(XMLAQuery.class);
}
