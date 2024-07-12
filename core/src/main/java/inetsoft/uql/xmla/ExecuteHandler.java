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
package inetsoft.uql.xmla;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * ExecuteHandler is responsible for XMLA execute method.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */
class ExecuteHandler extends SAXHandler {
   /**
    * Constructor.
    * @handler XMLAHandler.
    */
   public ExecuteHandler(XMLAHandler handler) {
      super();
      this.handler = handler;
   }

   /**
    * Start element.
    * @throws SAXException
    */
   @Override
   public void startElement() throws SAXException {
      super.startElement();

      if("Axis".equals(lastTag)) {
         if("Axis0".equals(pparser.getAttributeValue("name"))) {
            state = AXIS0;
         }
         else if("Axis1".equals(pparser.getAttributeValue("name"))) {
            state = AXIS1;
         }
         else if("SlicerAxis".equals(pparser.getAttributeValue("name"))) {
            state = SLICER_AXIS;
         }
      }

      if(query.getMembersCount() > 0 && "Tuple".equals(lastTag) &&
         AXIS0.equals(state))
      {
         processingTuple = true;
         rows.add(new Object[query.getColumnCount()]);
      }

      if("Member".equals(lastTag) && processingTuple) {
         mobj = new MemberObject();
         mobj.hierarchy = pparser.getAttributeValue("Hierarchy");
      }

      if("CellData".equals(lastTag)) {
         state = CELL_DATA;
      }

      if("Cell".equals(lastTag) && CELL_DATA.equals(state)) {
         String ostr = pparser.getAttributeValue("CellOrdinal");

         if(ostr != null) {
            try {
               cellOrdinal = Integer.parseInt(ostr);
            }
            catch(Exception e) {
            }
         }
         else {
            cellOrdinal++;
         }
      }
   }

   /**
    * End element.
    */
   @Override
   public void endElement() {
      super.endElement();

      if(("Axis".equals(lastTag) || "CellData".equals(lastTag))
         && state != null)
      {
         state = null;
      }

      if("Tuple".equals(lastTag) && AXIS0.equals(state)) {
         processingTuple = false;
      }

      if("Member".equals(lastTag) && mobj != null) {
         mobj = null;
      }
   }

   /**
    * Parse contents.
    */
   @Override
   public void characters() {
      super.characters();

      String txt = getText();

      if(txt == null || txt.trim().length() <= 0 || txt.equals("\n")) {
         return;
      }

      // parse dimension members
      if(mobj != null) {
         if("UName".equals(lastTag)) {
            mobj.uName = txt;
         }

         if("Caption".equals(lastTag)) {
            mobj.caption = txt;
         }

         if("LName".equals(lastTag)) {
            fillLevelName(mobj);
         }

         if("LNum".equals(lastTag)) {
            DataRef[] refs = query.getMemberRefs(mobj.hierarchy);

            // hierarchy is the dimension name in xml result of SAP
            if(refs.length == 0) {
               int idx = mobj.lName.indexOf("].[");

               if(idx > 0) {
                  String hier = mobj.lName.substring(0, idx + 1);
                  refs = query.getMemberRefs(hier);
               }
            }

            fillLevelNumber(mobj);

            for(int i = refs.length - 1; i >= 0; i--) {
               int lvl0 = query.getLevelNumber(refs[i]);

               if(lvl0 > mobj.lNum) {
                  // ignore lower level
               }
               else if(lvl0 == mobj.lNum) {
                  fillMemberObj(refs[i], mobj);
               }
               else {
                  fillAncestor(refs[i], mobj, mobj.lNum - lvl0);
               }
            }

            String uName = truncateUniqueName(mobj.uName);

            if(!Tool.equals(mobj.uName, uName)) {
               mobj.uName = uName;
            }

            fixCaption(mobj, trimSuffix(mobj.caption));
         }
      }

      if((query.getMembersCount() == 0 && AXIS0.equals(state) ||
         AXIS1.equals(state)) && "UName".equals(lastTag))
      {
         String measure = txt;
         int idx = measure.indexOf(XMLAUtil.NONE_FORMULA);

         if(idx >= 0) {
            measure = measure.substring(0, idx);
            measure = measure + "]";
         }

         colIdxs.add(query.indexOfMeasure(measure));
      }

      if(query.getMembersCount() == 0 && rows.size() == 0) {
         rows.add(new Object[query.getColumnCount()]);
      }

      if("Value".equals(lastTag) && CELL_DATA.equals(state) &&
         colIdxs.size() > 0)
      {
         int idx = cellOrdinal / rows.size();
         int col = colIdxs.get(idx) + query.getMembersCount();
         int row = cellOrdinal % rows.size();
         Object[] arr = rows.get(row);
         DataRef measure = query.getMeasureRef(colIdxs.get(idx));
         String dtype = pparser.getAttributeValue("xsi:type");

         if(dtype == null) {
            dtype = measure.getDataType();
         }
         else if(dtype.startsWith("xsd:")) {
            dtype = dtype.substring(4);
         }

         Object data = getData(txt, dtype);
         arr[col] = data;
      }
   }

   /**
    * Fix caption and full caption.
    */
   protected void fixCaption(MemberObject mobj, String caption) {
      if(!Tool.equals(mobj.caption, caption)) {
         mobj.caption = caption;
         mobj.fullCaption = "[" +  mobj.caption + "]";
      }
   }

   /**
    * Get member name properly.
    */
   protected String trimSuffix(String name) {
      if(name == null) {
         return null;
      }

      int idx = name.indexOf("___^_^___");

      if(idx >= 0) {
         name = name.substring(0, idx);
      }

      return name;
   }

   /**
    * Truncate suffix of member unique name if necessary.
    */
   private String truncateUniqueName(String uName) {
      int idx1 = uName.lastIndexOf("[");
      int idx2 = uName.lastIndexOf("]");
      String prefix = null;

      if(idx1 >= 0 && idx2 >= 0) {
         prefix = uName.substring(0, idx1);
         String suffix = idx2 + 1 < uName.length() ?
            uName.substring(idx2 + 1) : "";
         uName = uName.substring(idx1 + 1, idx2);
         uName = trimSuffix(uName);

         return prefix + "[" + uName + "]" + suffix;
      }

      return trimSuffix(uName);
   }

   /**
    * Fill level name to member object.
    */
   protected void fillLevelName(MemberObject memberObj) {
      memberObj.lName = getText();
   }

   /**
    * Fill level number to member object.
    */
   protected void fillLevelNumber(MemberObject memberObj) {
      int lvl = -1;

      try {
         lvl = Integer.parseInt(getText());
      }
      catch(NumberFormatException ne) {
      }

      memberObj.lNum = lvl;
   }

   /**
    * Get the data that parse from a given xml element.
    * @return data that has been parsed.
    */
   public Object[][] getData() {
      rows.add(0, getHeaderRow());
      Object[][] arr = new Object[rows.size()][rows.get(0).length];
      rows.toArray(arr);

      return arr;
   }

   /**
    * Get the type class for each column.
    * @return all the type class.
    */
   public Class[] getTypes() {
      Class[] types = new Class[query.getColumnCount()];
      int dimsCnt = query.getMembersCount();
      int measuresCnt = query.getMeasuresCount();

      for(int i = 0; i < dimsCnt; i++) {
         DataRef dim = query.getMemberRef(i);
         types[i] = Tool.getDataClass(dim.getDataType());
      }

      for(int i = 0; i < measuresCnt; i++) {
         DataRef dim = query.getMeasureRef(i);
         types[i + dimsCnt] = Tool.getDataClass(dim.getDataType());
      }

      return types;
   }

   /**
    * Check if should cache all higher levels.
    */
   protected boolean cacheHigherLevels(ArrayList<MemberObject> mbrs) {
      return true;
   }

   /**
    * Write down cached levels if necessary.
    */
   protected void cacheLevelMembers(XMLAQuery query0, Principal user)
      throws Exception
   {
      if(checkCached(query0, user)) {
         return;
      }

      ArrayList<MemberObject> mbrs = handler.getMembers(query0, user);
      ColumnRef col = (ColumnRef) query0.getMemberRef(0);
      Dimension dim = XMLAUtil.getDimension(query0, query0.getCube(), col);
      DimMember level = XMLAUtil.getLevel(query0, query0.getCube(), col);

      if(cacheHigherLevels(mbrs)) {
         int lvlNo = level.getNumber();

         if(lvlNo > 0) {
            DimMember level0 = (DimMember) dim.getLevelAt(lvlNo - 1);
            XMLAQuery q = new XMLAQuery();
            q.setDataSource(query0.getDataSource());
            q.setCube(query0.getCube());
            q.setRuntimeCube(query0.getRuntimeCube());
            AttributeRef attr = new AttributeRef(dim.getIdentifier(),
                                                 level0.getUniqueName());
            ColumnRef col0 = new ColumnRef(attr);
            q.addMemberRef(col0);
            q.setProperty("noEmpty", "false");
            Principal user0 = (Principal) query.getProperty("RUN_USER");

            if(user0 != null) {
               q.setProperty("RUN_USER", user0);
            }

            cacheLevelMembers(q, user);
         }
      }

      for(int i = 0; i < mbrs.size(); i++) {
         MemberObject mobj = mbrs.get(i);
         mobj.fullCaption = getFullCaption(mobj, query0);
      }

      String header = VSUtil.getCaption(dim, level);
      XMLAUtil.writeCachedResult(XMLAUtil.getCacheKey(query0),
         new XMLATableNode(mbrs, header));
   }

   /**
    * Check cache data is existed or not.
    */
   protected boolean checkCached(XMLAQuery query, Principal user)
      throws Exception
   {
      String cacheKey = XMLAUtil.getCacheKey(query);

      if(cacheKey == null) {
         return false;
      }

      return XMLAUtil.getCachedResult(cacheKey, query, true) != null;
   }

   /**
    * Get cached result.
    */
   XMLATableNode getCachedData(Principal user, boolean readOnly) throws Exception {
      String cacheKey = XMLAUtil.getCacheKey(query);

      if(cacheKey == null) {
         return null;
      }

      XMLATableNode cached = (XMLATableNode)
         XMLAUtil.getCachedResult(cacheKey, query, readOnly);

      if(cached != null) {
         return cached;
      }

      cacheLevelMembers(query, user);
      return (XMLATableNode) XMLAUtil.getCachedResult(cacheKey, query, readOnly);
   }

   /**
    * get table header row.
    */
   private Object[] getHeaderRow() {
      int dimsCnt = query.getMembersCount();
      int measuresCnt = query.getMeasuresCount();
      Object[] data = new Object[dimsCnt + measuresCnt];

      for(int i = 0; i < dimsCnt; i++) {
         data[i] = XMLAUtil.getHeader(query.getMemberRef(i));
      }

      for(int i = 0; i < measuresCnt; i++) {
         data[dimsCnt + i] = XMLAUtil.getHeader(query.getMeasureRef(i));
      }

      return data;
   }

   /**
    * Put member object into the right place.
    */
   private void fillMemberObj(DataRef ref, MemberObject mobj) {
      if(rows.size() == 0) {
         return;
      }

      Object[] arr = rows.get(rows.size() - 1);
      int col = query.indexOfMember(ref);
      arr[col] = processCaption(ref, mobj);
   }

   /**
    * Process caption for named group.
    */
   protected MemberObject processCaption(DataRef ref, MemberObject mobj) {
      mobj.fullCaption = getFullCaption(mobj, query);
      return mobj;
   }

   /**
    * Get full caption.
    */
   protected String getFullCaption(MemberObject mobj, XMLAQuery query0) {
      return XMLAUtil.getFullCaption(mobj, query0);
   }

   /**
    * Put ancestor into the right place.
    */
   private void fillAncestor(DataRef ref, MemberObject mobj, int offset) {
      mobj = findAncestor(mobj, offset);

      if(mobj != null) {
         fillMemberObj(ref, mobj);
      }
   }

   /**
    * Find ancestor.
    */
   protected MemberObject findAncestor(MemberObject mobj, int offset) {
      try {
         return XMLAUtil.findAncestorMember(query, mobj, offset, true);
      }
      catch(Exception ex) {
         LOG.error("Failed to find ancestor member", ex);
      }

      return null;
   }

   /**
    * Get data.
    */
   private Object getData(String value, String dtype) {
      try {
         if(dtype == null) {
            dtype = XSchema.DOUBLE;
         }

         if(XSchema.INTEGER.equals(dtype) || "int".equals(dtype)) {
            return Integer.valueOf(Double.valueOf(value).intValue());
         }
         else if(XSchema.FLOAT.equals(dtype)) {
            Float val = Float.valueOf(value);

            return (val == Float.POSITIVE_INFINITY ||
                    val == Float.NEGATIVE_INFINITY ||
                    Double.isNaN(val)) ? null : val;
         }
         else if(XSchema.DOUBLE.equals(dtype) || XSchema.DECIMAL.equals(dtype))
         {
            Double val = Double.valueOf(value);

            return (val == null || val.equals(Double.POSITIVE_INFINITY) ||
                     val.equals(Double.NEGATIVE_INFINITY) ||
                     val.equals(Double.NaN)) ? null : val;
         }
         else if(XSchema.STRING.equals(dtype)) {
            return value;
         }
      }
      catch(Exception ex) {
         // dot not parse data such as NA or N/A which means not available
      }

      return null;
   }

   // flag indicates axis0
   private static final String AXIS0 = "AXIS0";
   // flag indicates axis1
   private static final String AXIS1 = "AXIS1";
   // flag indicates slicer axis
   private static final String SLICER_AXIS = "SLICER_AXIS";
   // flag indicates cell data
   private static final String CELL_DATA = "CELL_DATA";
   private static final Logger LOG =
      LoggerFactory.getLogger(ExecuteHandler.class);

   private List<Object[]> rows = new ArrayList();
   private List<Integer> colIdxs = new ArrayList();
   private String state;
   private int cellOrdinal = -1;
   private boolean processingTuple;
   private MemberObject mobj;
   protected XMLAHandler handler;
}
