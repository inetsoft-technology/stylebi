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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * XMLAUtil class defines utilities methods to process xmla.
 *
 * @version 10.1, 1/20/2009
 * @author InetSoft Technology Corp
 */
public class XMLAUtil {
   public static String[][] encoding1 = {
         {"&", "<", ">", "\""},
         {"&amp;", "&lt;", "&gt;", "&quot;"}};

   /**
    * Get dimension from a certain cube.
    */
   public static Dimension getDimension(XMLAQuery query, String cube, DataRef ref) {
      if(query.getRuntimeCube() != null) {
         return getDimension(query.getRuntimeCube(), getNames(ref)[0]);
      }
      else {
         return getDimension(query.getDataSource().getFullName(), cube, getNames(ref)[0]);
      }
   }

   /**
    * Get dimension from a certain cube.
    */
   public static Dimension getDimension(String datasource, String cube,
                                        DataRef ref) {
      return getDimension(datasource, cube, getNames(ref)[0]);
   }

   /**
    * Get dimension level from a certain cube.
    */
   public static DimMember getLevel(XMLAQuery query, String cube,
                                    DataRef ref)
   {
      Dimension dim = getDimension(query, cube, ref);

      if(dim == null) {
         return null;
      }

      return (DimMember) dim.getLevel(getNames(ref)[1]);
   }

   /**
    * Get dimension level from a certain cube.
    */
   public static DimMember getLevel(String datasource, String cube,
                                    DataRef ref) {
      Dimension dim = getDimension(datasource, cube, ref);

      if(dim == null) {
         return null;
      }

      return (DimMember) dim.getLevel(getNames(ref)[1]);
   }

   /**
    * Get dimension from a certain cube.
    */
   public static Dimension getDimension(String datasource, String cube,
                                        String dim) {
      if(datasource == null || cube == null || dim == null) {
         return null;
      }

      try {
         XDomain domain =
            DataSourceRegistry.getRegistry().getDomain(datasource);

         if(domain == null) {
            return null;
         }

         return getDimension(domain.getCube(cube), dim);
      }
      catch(Exception e) {
      }

      return null;
   }

   public static Dimension getDimension(XCube xCube, String dim) {
      if(xCube == null) {
         return null;
      }

      return (Dimension) xCube.getDimension(dim);
   }

   /**
    * Get dimension groups.
    */
   public static Map getDimensionGroups(XCube xcube) {
      if(!(xcube instanceof Cube)) {
         return null;
      }

      Cube cube = (Cube) xcube;
      Map groups = new HashMap();
      Enumeration dimensions = cube.getDimensions();

      while(dimensions.hasMoreElements()) {
         Dimension dimension = (Dimension) dimensions.nextElement();
         String pdim = dimension.getParentDimension();

         if(pdim == null) {
            return null;
         }

         Vector vec = (Vector) groups.get(pdim);

         if(vec == null) {
            vec = new Vector();
            groups.put(pdim, vec);
         }

         vec.add(dimension);
      }

      return groups;
   }

   /**
    * Get XCube by data source name and cube name.
    * @param prefix the specified datasource name.
    * @param source the specified cube name.
    * @return XCube if any.
    */
   public static XCube getCube(String prefix, String source) {
      if(prefix == null || source == null) {
         return null;
      }

      DataSourceRegistry reg = DataSourceRegistry.getRegistry();
      XDomain domain = reg.getDomain(prefix);

      if(domain == null) {
         return null;
      }

      return domain.getCube(source);
   }

   /**
    * Get dimension member level number.
    * @param ref the specified data to get the level number.
    * @param dsName the data source name that the data ref in.
    * @param cubeName the cube name that the data ref in.
    * @return the level number that the ref in the cube, if -1 means not found.
    */
   public static int getLevelNumber(DataRef ref, String dsName, String cubeName)
   {
      XCube cb = getCube(dsName, cubeName);

      // cube not found
      if(cb == null) {
         return -1;
      }

      String dim = getEntity(ref);
      String level = getAttribute(ref);
      XDimension dimension = cb.getDimension(dim);

      // dimension not found
      if(dimension == null) {
         return -1;
      }

      return dimension.getScope(level);
   }

   /**
    * Sort the data ref(s) by dimension level number.
    * @param refs the ref(s) to sort.
    * @param dsName data source name that the refs from.
    * @param cubeName cube name that the refs from.
    */
   public static void sortRefsByLevel(final DataRef[] refs, final String dsName,
      final String cubeName)
   {
      Arrays.sort(refs, new Comparator() {
         @Override
         public int compare(Object obj1, Object obj2) {
            DataRef ref1 = (DataRef) obj1;
            DataRef ref2 = (DataRef) obj2;

            int idx1 = XMLAUtil.getLevelNumber(ref1, dsName, cubeName);
            int idx2 = XMLAUtil.getLevelNumber(ref2, dsName, cubeName);

            return idx1 - idx2;
         }
      });
   }

   /**
    * Check if a query is request memebers.
    * @param query the specified xmla query.
    * @return <tt>true</tt> if request memebers, <tt>false</tt> otherwise.
    */
   public static boolean isRequestMembers(XMLAQuery query) {
      return query.getMembersCount() == 1 && query.getMeasuresCount() == 0 &&
         query.getFilterNode() == null &&
         "false".equals(query.getProperty("noEmpty"));
   }

   /**
    * Encoding xml.
    */
   public static String encodingXML(String str, String[][] encoding) {
      if(str == null) {
         return "";
      }

      StringBuilder buf = new StringBuilder();

      encodeLoop:
      for(int i = 0; i < str.length(); i++) {
         for(int j = 0; j < encoding[0].length; j++) {
            if(str.charAt(i) == encoding[0][j].charAt(0)) {
               buf.append(encoding[1][j]);
               continue encodeLoop;
            }
         }

         buf.append(str.charAt(i));
      }

      return buf.toString();
   }

   /**
    * Do summary, for datas in the data parameter, for each row, if all the
    * dimension columns are same value, sum the measure values.
    * @param table the data node, used to get each column data type.
    * @param data the data values for the table node.
    */
   public static Object[][] groupAll(XMLATableNode table, Object[][] data) {
      if(data == null || data.length == 0 || data[0].length == 0) {
         return data;
      }

      // dimension column values key, datais String
      Map<String, Integer> keyMap = new HashMap();
      // row value, data is Object[]
      Vector values = new Vector();
      // add header
      values.add(data[0]);

      for(int i = 1; i < data.length; i++) {
         String key = createKey(table, data[i]);

         if(!keyMap.containsKey(key)) {
            keyMap.put(key, keyMap.size());
            values.add(data[i]);
            continue;
         }

         addValue(values, keyMap.get(key), data[i], table);
      }

      Object[][] sdata = new Object[values.size()][data[0].length];

      for(int i = 0; i < sdata.length; i++) {
         for(int j = 0; j < sdata[0].length; j++) {
            sdata[i][j] = ((Object[]) values.get(i))[j];
         }
      }

      return sdata;
   }

   /**
    * Get member object's full caption.
    */
   public static String getFullCaption(MemberObject mobj, XMLAQuery query) {
      try {
         String caption = mobj.caption == null ? "" : mobj.caption;

         if(!isDisplayFullCaption()) {
            return caption;
         }

         if(mobj.lNum == 0) {
            return "[" +  caption + "]";
         }

         MemberObject mobj0 = XMLAUtil.findAncestorMember(query, mobj, 1, true);

         return mobj0.fullCaption + ".[" + caption + "]";
      }
      catch(Exception e) {
         LOG.error("Failed to get member full caption", e);
      }

      return null;
   }

   /**
    * Get ancestor member object.
    */
   static MemberObject findAncestorMember(XMLAQuery query,
      MemberObject mobj, int offset, boolean uNameOnly) throws Exception
   {
      while(offset > 0) {
         String parent = mobj.parent;

         if(parent == null) {
            String cacheKey = getCacheKey(getSourceName(query), mobj.lName);
            // read only is enough
            XMLATableNode table =
               (XMLATableNode) getCachedResult(cacheKey, query, true);

            if(table != null) {
               mobj = table.findMember(mobj.uName, uNameOnly);
               parent = mobj.parent;
            }
         }

         if(parent != null && maps.get(parent) != null) {
            mobj = maps.get(parent);
         }
         else {
            // get cached table of parent level
            Cube cube = (Cube) getCube(query.getDataSource().getFullName(),
               query.getCube());

            Dimension dim = (Dimension) cube.getDimension(mobj.hierarchy);
            String key0 = dim.getIdentifier();
            DimMember mbr = null;

            if(mobj.plNum >= 0) {
               mbr = (DimMember) dim.getLevelAt(mobj.plNum);
            }
            else {
               mbr = (DimMember) dim.getLevelAt(mobj.lNum - 1);
            }

            String key1 = mbr.getUniqueName();
            key1 = getLevelUName(key0, key1);
            String cacheKey = getCacheKey(getSourceName(query), key1);
            // read only is enough
            XMLATableNode ptable =
               (XMLATableNode) getCachedResult(cacheKey, query, true);

            // get parent member object
            mobj = ptable == null ? null : ptable.findMember(parent, uNameOnly);

            if(mobj == null) {
               break;
            }

            if(parent != null) {
               maps.put(parent, mobj);
            }
         }

         offset--;
      }

      return mobj;
   }

   /**
    * Get source name.
    */
   public static String getSourceName(XMLAQuery query) {
      StringBuilder buffer = new StringBuilder();

      // append user name if any
      if(query.getProperty("RUN_USER") != null) {
         Principal user = (Principal) query.getProperty("RUN_USER");
         buffer.append(user.getName());
         buffer.append("___");
      }

      // append datasource name
      String prefix = query.getDataSource().getFullName();
      prefix = prefix.replace(' ', '_');
      prefix = prefix.replace('/', '_');
      prefix = prefix.replace('\\', '_');
      buffer.append(prefix);

      // append cube name
      buffer.append("___");
      buffer.append(query.getCube());

      return buffer.toString();
   }

   /**
    * Create a valid file name usable key.
    */
   private static String getCacheKey(String key) {
      return Tool.normalizeFileName(key);
   }

   /**
    * Get correct level unique name.
    */
   public static String getLevelUName(XMLAQuery query, DataRef col) {
      Cube cube = (Cube) query.getRuntimeCube();

      if(cube == null) {
         cube = (Cube) getCube(query.getDataSource().getFullName(), query.getCube());
      }

      String entity = getEntity(col);
      String attribute = getAttribute(col);

      if(Cube.ESSBASE.equals(cube.getType())) {
         return getLevelUName(entity, attribute);
      }

      return attribute;
   }

   /**
    * Get correct level unique name.
    */
   public static String getLevelUName(String entity, String attribute) {
      return attribute.indexOf(entity) < 0 ?
         entity + "." + attribute : attribute;
   }

   /**
    * Create a unique key for the specified query.
    * @param query the xmla query.
    * @return a unique key.
    */
   public static String getCacheKey(XMLAQuery query) throws Exception {
      if(!XMLAUtil.isRequestMembers(query)) {
         return null;
      }

      return getCacheKey(getSourceName(query),
         getLevelUName(query, query.getMemberRef(0)));
   }

   /**
    * Create a unique key for the specified datasource and data ref.
    * @param source the specified datasource.
    * @param level the specified level unique name.
    * @return a unique key.
    */
   public static String getCacheKey(String source, String level)
      throws Exception
   {
      StringBuilder buffer = new StringBuilder();
      buffer.append(source);
      buffer.append("___");
      buffer.append(level);

      return getCacheKey(buffer.toString());
   }

   /**
    * Get a cached query result.
    */
   public static Object getCachedResult(String cacheKey, XMLAQuery query,
                                        boolean readOnly) {
      if(query == null) {
         return getCachedResult(cacheKey);
      }

      Object data = cachedLevels.get(cacheKey);

      if(data == null) {
         data = getCachedResult(cacheKey);

         if(data != null) {
            cachedLevels.put(cacheKey, data);
         }
      }

      return data instanceof XMLATableNode && !readOnly ?
         ((XMLATableNode) data).clone() : data;
   }

   /**
    * Find a cached query result.
    * @param cacheKey cache key generated by query.
    * @return the cached is exist or not
    */
   public static boolean findCachedResult(String cacheKey) {
      if(cacheKey == null) {
         return false;
      }

      DataSpace space = DataSpace.getDataSpace();
      String file = cacheKey + ".cube.cache";
      String folder = getCachedFolder(cacheKey);
      return space.exists(cachedir + folder, file);
   }

   /**
    * Get a cached query result.
    * @param cacheKey cache key generated by query.
    * @return the cached object, or <code>null</code> if the result has not been
    * cached.
    */
   public static Object getCachedResult(String cacheKey) {
      if(cacheKey == null) {
         return null;
      }

      Object cached = null;
      DataSpace space = DataSpace.getDataSpace();
      String file = cacheKey + ".cube.cache";
      String folder = cachedir + getCachedFolder(cacheKey);

      try(ObjectInputStream stream = createObjectInput(space.getInputStream(folder, file))) {
         if(stream == null) {
            return null;
         }

         try {
            cached = stream.readObject();
         }
         catch(InvalidClassException | InvalidObjectException ex) {
            // ignore for backward compatibility reason
         }
      }
      catch(Exception exc) {
         LOG.error("Failed to get cached result", exc);
         return null;
      }

      return cached;
   }

   private static ObjectInputStream createObjectInput(InputStream input) throws IOException {
      return input == null ? null : new ObjectInputStream(input);
   }

   /**
    * Write a query result to the cache.
    * @param cacheKey cache key generated by query.
    * @param obj object to the cached.
    * @return a copy of the object that has been rewound.
    */
   public static Object writeCachedResult(String cacheKey, Object obj) {
      if(cacheKey == null || obj == null) {
         return obj;
      }

      DataSpace space = DataSpace.getDataSpace();
      String folder = cachedir + getCachedFolder(cacheKey);
      String file = cacheKey + ".cube.cache";

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream stream = tx.newStream(folder, file))
      {
         ObjectOutputStream output = new ObjectOutputStream(stream);
         output.writeObject(obj);
         output.flush();
         tx.commit();
      }
      catch(Throwable exc) {
         LOG.error("Failed write cube result cache", exc);
         return obj;
      }

      // put the data into cache
      cachedLevels.put(cacheKey, obj);
      return obj;
   }

   /**
    * Get the folder to store and get the cache result.
    * @param keyName the name of the cache file.
    */
   public static String getCachedFolder(String keyName) {
      String folder = "";
      String folderName = keyName.replaceAll("___", "^");
      int idx0 = folderName.indexOf("^");
      int idx1 = folderName.indexOf("^", idx0 + 1);

      if(idx1 != -1) {
         int idx2 = folderName.indexOf(".[", idx1);
         int idx3 = folderName.indexOf(".", idx2 + 1);

         if(idx2 != -1 && idx3 != -1) {
            folder = "/" + folderName.substring(0, idx3);
         }
      }

      return folder;
   }

   /**
    * Convert date from logical model to be comparable.
    */
   public static String getDateStr(String val) {
      if(val == null) {
         return null;
      }

      String[] arr = Tool.split(val, '.');
      StringBuilder buffer = new StringBuilder();

      for(int i = 0; i < arr.length; i++) {
         if(i > 0) {
            buffer.append(".");
         }

         if(arr[i].length() < 2) {
            buffer.append("0");
         }

         buffer.append(arr[i]);
      }

      return buffer.toString();
   }

   /**
    * Get header name from data ref.
    */
   public static String getHeader(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) ref;

         DataRef ref0 = column.getDataRef();

         if(ref0 instanceof AliasDataRef) {
            return ref0.getName();
         }

         return column.getHeaderName();
      }
      else if(ref instanceof AggregateRef) {
         AggregateRef aref = (AggregateRef) ref;
         ColumnRef column = (ColumnRef) aref.getDataRef();
         AggregateFormula formula = aref.getFormula();
         DataRef ref0 = column.getDataRef();

         if(ref0 instanceof AliasDataRef) {
            return ref0.getName();
         }

         String header = getHeader(column);

         if(AggregateFormula.NONE.equals(formula)) {
            return header;
         }

         return formula.getFormulaName() + "(" + header + ")";
      }

      return getAttribute(ref);
   }

   /**
    * Get measure name who has a formula.
    */
   public static String getCalcName(DataRef measure) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("[Measures].[");
      buffer.append(getHeader(measure));
      buffer.append("]");

      return buffer.toString();
   }

   /**
    * Check if query contains calculated member.
    */
   public static boolean hasCalcualtedMember(XMLAQuery query) {
      if(!(query instanceof XMLAQuery2)) {
         return false;
      }

      AggregateInfo ainfo = ((XMLAQuery2) query).getAggregateInfo();

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         SNamedGroupInfo ginfo = getGroupInfo(gref.getDataRef());

         if(ginfo != null && !ginfo.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get named group info from a column ref.
    */
   public static SNamedGroupInfo getGroupInfo(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ref = ((ColumnRef) ref).getDataRef();
      }

      if(!(ref instanceof NamedRangeRef)) {
         return null;
      }

      NamedRangeRef nref = (NamedRangeRef) ref;
      return (SNamedGroupInfo) nref.getNamedGroupInfo();
   }

   /**
    * Create key by dimension column value.
    */
   private static String createKey(XMLATableNode table, Object[] data) {
      StringBuilder buffer = new StringBuilder();

      for(int i = 0; i < data.length; i++) {
         if(!Number.class.isAssignableFrom(table.getType(i))) {
            if(i > 0) {
               buffer.append("_^_");
            }

            buffer.append(data[i]);
         }
      }

      return buffer.toString();
   }

   /**
    * Add the measure column values in two row.
    * @param values stored the each row values.
    * @param index the index of the row that data will be added to.
    * @param adata the added data.
    */
   private static void addValue(Vector values, int index, Object[] adata,
                                XMLATableNode table) {
      if(index == -1) {
         values.add(adata);
         return;
      }

      // add header
      Object[] odata = (Object[]) values.get(index + 1);

      for(int i = 0; i < odata.length; i++) {
         if(Number.class.isAssignableFrom(table.getType(i))) {
            odata[i] = add((Number) odata[i], (Number) adata[i]);
         }
      }
   }

   /**
    * Add two number value.
    */
   private static Number add(Number ovalue, Number nvalue) {
      double oval = ovalue == null ? 0 : ovalue.doubleValue();
      double nval = nvalue == null ? 0 : nvalue.doubleValue();
      double value = oval + nval;

      if(ovalue instanceof Integer) {
         return Integer.valueOf((int) value);
      }
      else if(ovalue instanceof Float) {
         return Float.valueOf((float) value);
      }
      else {
         return Double.valueOf(value);
      }
   }

   /**
    * Get dimension & level name from data ref.
    */
   static String[] getNames(DataRef ref) {
      String entity = getEntity(ref);
      String attr = getAttribute(ref);

      if(entity == null || entity.indexOf(Assembly.CUBE_VS) >= 0) {
         String entity0 = attr;
         int idx = entity0.lastIndexOf(".");

         if(idx >= 0) {
            entity = entity0.substring(0, idx);
            attr = entity0.substring(idx + 1);
         }
      }

      return new String[]{entity, attr};
   }

   /**
    * Get inner most AttributeRef.
    */
   public static DataRef getAttributeRef(DataRef ref) {
      if(ref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) ref;
         DataRef ref0 = column.getDataRef();

         if(ref0 instanceof NamedRangeRef) {
            return ((NamedRangeRef) ref0).getDataRef();
         }
         else if(ref0 instanceof AliasDataRef) {
            return ((AliasDataRef) ref0).getDataRef();
         }
      }

      return ref;
   }

   /**
    * Re-construct column ref in show details.
    */
   public static DataRef shrinkColumnRef(ColumnRef column) {
      DataRef ref = getAttributeRef(column);

      if(ref instanceof ColumnRef) {
         return column;
      }

      column.setDataRef(ref);
      return column;
   }

   /**
    * Get entity of inner most data ref.
    */
   public static String getEntity(DataRef ref) {
      ref = getAttributeRef(ref);
      return ref.getEntity();
   }

   /**
    * Get attribute of inner most data ref.
    */
   public static String getAttribute(DataRef ref) {
      ref = getAttributeRef(ref);
      return ref.getAttribute();
   }

   /**
    * Get display name of a full caption.
    */
   public static String getDisplayName(String name) {
      name = Tool.replaceAll(name, "[", "");
      name = Tool.replaceAll(name, "]", "");

      return name;
   }

   /**
    * Check if a string the the identity of member object.
    */
   public static boolean isIdentity(String id, MemberObject mobj) {
      String fullCaption = mobj.fullCaption;

      if(fullCaption == null) {
         return false;
      }

      boolean isDisplayFull = isDisplayFullCaption();

      if(!isDisplayFull) {
         if(Tool.equals(id, fullCaption)) {
            return true;
         }
      }
      else if(id.indexOf(fullCaption) >= 0) {
         return true;
      }

      fullCaption = getDisplayName(fullCaption);

      if(!isDisplayFull) {
         if(Tool.equals(id, fullCaption)) {
            return true;
         }
      }
      else if(id.indexOf(fullCaption) >= 0) {
         return true;
      }

      int idx = mobj.uName.indexOf(".");
      String prefix = idx > 0 ? mobj.uName.substring(0, idx) : "";

      if(!"".equals(prefix) && id.startsWith(prefix)) {
         String str = id.substring(prefix.length() + 1);

         if(str.equals(mobj.caption)) {
            return true;
         }

         str = getDisplayName(str);

         if(str.equals(mobj.caption)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check whether need to use full caption to display.
    */
   public static boolean isDisplayFullCaption() {
      return "true".equals(SreeEnv.getProperty(
         "olap.table.originalContent", "false"));
   }

   /**
    * Reset the member object maps.
    */
   public static void reset() {
      maps.clear();
   }

   static final String CATALOGS_REQUEST = "DBSCHEMA_CATALOGS";
   static final String CUBES_REQUEST = "MDSCHEMA_CUBES";
   static final String DIMENSIONS_REQUEST = "MDSCHEMA_DIMENSIONS";
   static final String HIERARCHIES_REQUEST = "MDSCHEMA_HIERARCHIES";
   static final String LEVELS_REQUEST = "MDSCHEMA_LEVELS";
   static final String MDSCHEMA_MEMBERS = "MDSCHEMA_MEMBERS";
   static final String MEASURES_REQUEST = "MDSCHEMA_MEASURES";
   static final String NONE_FORMULA = "NONE_FORMULA";

   private static Map<String, MemberObject> maps = new HashMap(); // member objects
   private static Map cachedLevels = new Hashtable();

   private static final Logger LOG =
      LoggerFactory.getLogger(XMLAUtil.class);
   private static final String cachedir = "cubeCache";
}
