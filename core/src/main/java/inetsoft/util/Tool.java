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
package inetsoft.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Platform;
import com.veracode.annotation.CRLFCleanser;
import com.veracode.annotation.XSSCleanser;
import inetsoft.report.*;
import inetsoft.report.filter.ReversedComparer;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XDataSource;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.affinity.AffinitySupport;
import inetsoft.util.config.*;
import inetsoft.util.credential.*;
import inetsoft.util.css.*;
import inetsoft.util.encrypt.HcpVaultSecretsPasswordEncryption;
import inetsoft.util.swap.XSwapUtil;
import jakarta.servlet.http.HttpServletRequest;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.*;
import org.xerial.snappy.SnappyFramedInputStream;
import org.xerial.snappy.SnappyFramedOutputStream;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.*;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.Timestamp;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Common utility methods.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class Tool extends CoreTool {
   static {
      PropertiesEngine.getInstance().addPropertyChangeListener("string.compare.casesensitive", evt -> invalidateCaseSensitive());
   }
   /**
    * User defined type.
    */
   public static final String USER_DEFINED = "userDefined";
   /**
    * Role type.
    */
   public static final String ROLE = "role";
   /**
    * User type.
    */
   public static final String USER = "user";
   /**
    * Unknown type.
    */
   public static final String UNKNOWN = "unknown";
   /**
    * Persistent file prefix.
    */
   public static final String PERSISTENT_PREFIX = "__persistent__";

   /**
    * Persistent file prefix.
    */
   public static final String SHEET_NAME = "sheet_name";

   /**
    * Persistent file prefix.
    */
   public static final String TABLE_NAME = "table_name";

   /**
    * Persistent file prefix.
    */
   public static final String COLUMN_NAME = "column_name";

   /**
    * My dashboard prefix.
    */
   public static final String MY_DASHBOARD = "My Dashboards";

   /**
    * Worksheet prefix.
    */
   // @by ChrisSpagnoli bug1421224275206 2015-1-19
   public static final String WORKSHEET = "Worksheets";
   public static final String TEXT_LIMIT_PREFIX = "__text__limit__start__";
   public static final String TEXT_LIMIT_SUFFIX = "__text__limit__end__";
   public static final String COLUMN_LIMIT_PREFIX = "__column__limit__start__";
   public static final String COLUMN_LIMIT_SUFFIX = "__column__limit__end__";

   /**
    * Special values used as NULL for numeric types.
    */
   public static final double NULL_DOUBLE = -Double.MAX_VALUE;
   public static final float NULL_FLOAT = -Float.MAX_VALUE;
   public static final long NULL_LONG = Long.MIN_VALUE;
   public static final int NULL_INTEGER = Integer.MIN_VALUE;
   public static final short NULL_SHORT = Short.MIN_VALUE;
   public static final byte NULL_BYTE = Byte.MIN_VALUE;

   /**
    * Parse element to 2D array.
    * @param elem the element.
    */
   public static String[][] parseArray2DXML(Element elem) {
      NodeList rows = getChildNodesByTagName(elem, "row");
      java.util.List<String[]> list = new ArrayList<>();

      for(int i = 0; i < rows.getLength(); i++) {
         Element row = (Element) rows.item(i);
         NodeList cells = getChildNodesByTagName(row, "cell");
         String[] arr = new String[cells.getLength()];

         for(int j = 0; j < cells.getLength(); j++) {
            Element cell = (Element) cells.item(j);
            String val = getValue(cell);
            arr[j] = "__NULL__".equals(val) ? null : byteDecode(val);
         }

         list.add(arr);
      }

      int len = list.size() == 0 ? 0 : (list.get(0)).length;
      String[][] data = new String[list.size()][len];
      list.toArray(data);
      return data;
   }

   /**
    * Encode a url string for http, this function just used for the hyperlink(s)
    * which is defined for web link in hyperlink dialog, calling this method
    * just same as calling java.net.UREncoder.encode(str, "utf-8").
    */
   public static String encodeWebURL(String str) {
      return Tool.encodeURL(str);
   }

   /**
    * Encode a string for http posting. This method encodes characters that are
    * not either letters or digits to be html compatible.  For example the empty
    * replaced by a +, slash is replaced by %2F.
    */
   @XSSCleanser
   public static String encodeURL(String str) {
      return encodeURL(str, "UTF-8");
   }

   /**
    *  Escape and encode a string regarded as the path component of an URI with a given charset.
    */
   public static String encodeURL(String str, String charset) {
      if(str == null) {
         return null;
      }

      return UriUtils.encodeQueryParam(str, charset);
   }

   public static String encodeUriPath(String str) {
      return encodeUriPath(str, "UTF-8");
   }

   public static String encodeUriPath(String str, String charset) {
      if(str == null) {
         return null;
      }

      return UriUtils.encodePath(str, charset);
   }
   /**
    * Decode a string for http posting.
    */
   public static String decodeURL(String str) {
      return decodeURL(str, "UTF-8");
   }

   public static String decodeURL(String str, String charset) {
      str = byteDecode(str);

      if(str == null) {
         return null;
      }

      return UriUtils.decode(str, charset);
   }

   /**
    * Remove CRLF for CWE-93
    *
    * @param text the text to cleanse
    *
    * @return the text without any CRLF characters
    */
   @CRLFCleanser
   public static String cleanseCRLF(String text) {
      return text != null ? text.replace("\n", "").replace("\r", "") : null;
   }

   /**
    * Normalize a file name.
    */
   public static String normalizeFileName(String key) {
      return normalizeFileName(key, false);
   }

   public static String normalizeFileName(String key, boolean comma) {
      if(key == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < key.length(); i++) {
         char c = key.charAt(i);

         if(c == '\\' || c == '/' || c == ':' || c =='*' || c == '?' ||
            c == '"' || c == '<' || c == '>' || c == '|' || c == ';' || comma && c == ',')
         {
            sb.append('_');
            sb.append(Integer.toString(c));
            sb.append('_');
         }
         else  {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   /**
    * Return a string that conforms to an identifier by replacing all offending
    * characters with underscores.
    */
   public static String toIdentifier(String str) {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < str.length(); i++) {
         if(i == 0 && !Character.isJavaIdentifierStart(str.charAt(i)) ||
            !Character.isJavaIdentifierPart(str.charAt(i))) {
            buf.append("_");
         }
         else {
            buf.append(str.charAt(i));
         }
      }

      return buf.toString();
   }

   /**
    * Trim the white space from the end of string.
    * @param str original string.
    * @return trimmed string.
    */
   public static String trimEnd(String str) {
      if(str != null) {
         for(int idx = str.length() - 1; idx >= 0; idx--) {
            if(str.charAt(idx) > ' ') {
               return str.substring(0, idx + 1);
            }
         }
      }

      return str;
   }

   /**
    * Quick sort on an array of objects. java.util.Arrays.sort should be used
    * instead by users with JDK version 1.2 or higher.
    * @param arr the array of Objects to be sorted.
    * @param asc true to sort the array in ascending order, false otherwise.
    */
   public static void qsort(Object[] arr, boolean asc) {
      if(arr == null || arr.length < 2) {
         return;
      }

      qsort(arr, 0, arr.length - 1, asc,
            new inetsoft.report.filter.DefaultComparer());
   }

   /**
    * Quick sort on an array of objects. java.util.Arrays.sort should be used
    * instead by users with JDK version 1.2 or higher.
    * @param arr the array of Objects to be sorted.
    * @param lo0 the first index to start sorting from.
    * @param hi0 the last index to sort.
    * @param asc true to sort the array in ascending order, false otherwise.
    * @param comp the comparer object that compares the items in the array.
    */
   @SuppressWarnings("unchecked")
   public static void qsort(Object[] arr, int lo0, int hi0, boolean asc,
                            inetsoft.report.Comparer comp) {
      if(!asc) {
         comp = new ReversedComparer(comp);
      }

      // Tool.qsort includes last index, Arrays.sort excludes last index
      if(hi0 < arr.length) {
         hi0++;
      }

      if(lo0 >= hi0) {
         return;
      }

      Arrays.sort(arr, lo0, hi0, comp);
   }

   /**
    * A utility method that concat an array of Strings with the given delimiter.
    * @param strs the array of Strings that to be concated.
    * @param delim the delimiter.
    */
   public static String concat(String[] strs, char delim) {
      if(strs == null || strs.length == 0) {
         return "";
      }

      StringBuilder buf = new StringBuilder();

      for(String str : strs) {
         buf.append(str);
         buf.append(delim);
      }

      //remove the last delimiter
      return buf.substring(0, buf.length() - 1);
   }

   /**
    * A utility method that splits a delimited string into an array of Strings
    * and respect quotes. Delimiter inside quotes are not treated as delimiter.
    * @param str the original String which is to be split.
    * @param delim the delimiter to be used in splitting the string.
    * @param escape the escape character.
    */
   public static String[] splitWithQuote(String str, String delim,
                                         char escape) {
      if(str == null || str.length() == 0) {
         return new String[] {};
      }

      Vector<String> v = new Vector<>();
      int pos;

      while((pos = indexOfWithQuote(str, delim, escape)) >= 0) {
         v.addElement(str.substring(0, pos));
         str = str.substring(pos + delim.length());
      }

      v.addElement(str);

      String[] strs = new String[v.size()];
      v.copyInto(strs);

      // strip quotes
      for(int i = 0; i < strs.length; i++) {
         if(strs[i].startsWith("\"") && strs[i].endsWith("\"")) {
            strs[i] = strs[i].substring(1, strs[i].length() - 1);
         }
      }

      return strs;
   }

   /**
    * Removes all occurences of a character from a String.
    * @param str the original String.
    * @param ch the character to be deleted from the String.
    * @return a String stripped of ch characters.
    */
   public static String remove(String str, char ch) {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < str.length(); i++) {
         char ch2 = str.charAt(i);

         if(ch2 != ch) {
            buf.append(ch2);
         }
      }

      return buf.toString();
   }

   /**
    * Insert a value into a vector in sorted order. This utility method.
    * maintains a sorted vector of integers.
    * @param val integer value to be inserted into the vector.
    * @param yv a sorted vector.
    */
   public static void insertSorted(int val, List<Integer> yv) {
      int k;

      for(k = yv.size() - 1; k >= 0; k--) {
         int v = yv.get(k);

         if(val == v) {
            break;
         }
         else if(val > v) {
            yv.add(k + 1, val);
            break;
         }
      }

      if(k < 0) {
         yv.add(0, val);
      }
   }

   /**
    * Expands a classpath into it's component files. Wildcards are expanded into the
    * matching files.
    *
    * @param classpath the classpath to expand.
    *
    * @return the classpath component files.
    */
   public static String[] expandClasspath(String classpath) {
      Set<String> result = new LinkedHashSet<>();

      if(classpath != null && !classpath.trim().isEmpty()) {
         String separator = classpath.contains(";") ? ";" : File.pathSeparator;

         for(String entry : classpath.trim().split(separator)) {
            entry = entry.trim();

            if(!entry.isEmpty()) {
               FileSystemService fileSystemService = FileSystemService.getInstance();

               if(entry.charAt(entry.length() - 1) == '*') {
                  File[] files =
                     fileSystemService.getFile(
                        entry.substring(0, entry.length() - 1)).listFiles();

                  if(files != null) {
                     for(File file : files) {
                        if(file.isFile()) {
                           String name = file.getName().toLowerCase();

                           if(name.endsWith(".jar") || name.endsWith(".zip")) {
                              String path = file.getAbsolutePath();
                              result.add(path);
                           }
                        }
                     }
                  }
               }
               else {
                  String file = fileSystemService.getPath(entry).toAbsolutePath().toString();
                  result.add(file);
               }
            }
         }
      }

      return result.toArray(new String[0]);
   }

   private static final Map<File, Boolean> dirmap = new ConcurrentHashMap<>();

   /**
    * Utility method to find a file in the classpath.
    * @param name the name of the file to search.
    * @param isdir true to find a directory, false to find a file.
    */
   public static String findFileInClasspath(String cpath, String name, boolean isdir) {
      for(String path : expandClasspath(cpath)) {
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File pathFile = fileSystemService.getFile(path);
         boolean pathIsDir = dirmap.computeIfAbsent(pathFile, File::isDirectory);

         if(pathIsDir) {
            String full = path + name;
            File file;

            try{
               file = fileSystemService.getFile(full);

               if(isdir && file.isDirectory() || !isdir && file.isFile()) {
                  return full;
               }
            }
            catch(InvalidPathException exp) {
               //file not be found ignore
            }
         }
      }

      return null;
   }

   /**
    * A string is empty if it's null or contains nothing
    */
   public static boolean isEmptyString(String obj) {
      return obj == null || obj.isEmpty();
   }

   /**
    * Returns a String with a character repeated n times.
    * @param ch character to repeat.
    * @param len the length of the string - or the number of times to repeat ch.
    * @return String with ch repeated len times ch{n}.
    */
   public static String getChars(char ch, int len) {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < len; i++) {
         buf.append(ch);
      }

      return buf.toString();
   }

   /**
    * Removes a string from the array. This method reindexes the array.
    * @param arr array of Strings.
    * @param val string value to remove from the array.
    */
   public static String[] remove(String[] arr, String val) {
      for(int i = 0; i < arr.length; i++) {
         if(arr[i].equals(val)) {
            return remove(arr, i);
         }
      }

      return arr;
   }

   /**
    * Removes an IdentityID from the array. This method reindexes the array.
    * @param arr array of IdentityIds.
    * @param val IdentityID to remove from the array.
    */
   public static IdentityID[] remove(IdentityID[] arr, IdentityID val) {
      List<IdentityID> newIdList = new ArrayList<>();
      for(int i = 0; i < arr.length; i++) {
         if(!Tool.equals(arr[i],val))
         {
            newIdList.add(arr[i]);
         }
      }

      return newIdList.toArray(new IdentityID[0]);
   }

   /**
    * Remove value from array. This method reindexes the array.
    * @param arr array of Strings.
    * @param idx the index of the item to remove.
    */
   public static String[] remove(String[] arr, int idx) {
      String[] narr = new String[arr.length - 1];

      System.arraycopy(arr, 0, narr, 0, idx);
      System.arraycopy(arr, idx + 1, narr, idx, narr.length - idx);
      return narr;
   }

   /**
    * Replaces all occurances of a value in an array with a new value.
    *
    * @param source the source array.
    * @param oldValue the value to be replaced.
    * @param newValue the new value that will replace the old value.
    *
    * @return the modified array.
    */
   public static String[] replace(String[] source, Object oldValue, Object newValue) {
      String[] result = new String[source.length];

      for(int i = 0; i < source.length; i++) {
         if(source[i].equals(oldValue)) {
            result[i] = newValue.toString();
         }
         else {
            result[i] = source[i];
         }
      }

      return result;
   }

   /**
    * Localize the name.
    * @param id name to be localized.
    * @return a localized name.
    */
   public static String localizeTextID(String id) {
      if(id == null || id.equals("")) {
         return null;
      }

      Principal user = ThreadContext.getContextPrincipal();
      Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);

      return catalog.getIDString(id) == null ? null : localize(id);
   }

   /**
    * Localize the name.
    * @param name to be localized.
    * @return a localized name.
    */
   public static String localize(String name) {
      if(name == null || name.equals("")) {
         return name;
      }

      if(!name.contains("/")) {
         return Tool.localize0(name);
      }

      StringBuilder localized = new StringBuilder();
      int count = 0;

      for(int i = 0; i < name.length(); i++) {
         final char ch = name.charAt(i);

         if(ch == '/') {
            if(count > 0) {
               final String token = name.substring(i - count, i);
               final String localizedToken = Tool.localize0(token);
               localized.append(localizedToken);
               count = 0;
            }

            localized.append(ch);
         }
         else if(i == name.length() - 1) {
            final String token = name.substring(i - count);
            final String localizedToken = Tool.localize0(token);
            localized.append(localizedToken);
         }
         else {
            count++;
         }
      }

      return localized.toString();
   }

   /**
    * Localize the name.
    * @param name to be localized.
    * @return a localized name.
    */
   private static String localize0(String name) {
      Principal user = ThreadContext.getContextPrincipal();
      Catalog cata = Catalog.getCatalog(user, Catalog.REPORT);
      int idx = name.indexOf('(');
      String name0;
      String name1;
      StringBuilder sb = new StringBuilder();

      if(idx > 0 && name.endsWith(")")) {
         name0 = name.substring(0, idx);
         name1 = name.substring(idx + 1, name.lastIndexOf(')'));
         sb.append(Catalog.getCatalog().getString(name0));
         sb.append("(");
         sb.append(Tool.localize(name1));
         sb.append(")");
      }
      else if(name.indexOf(':') > 0) {
         idx = name.indexOf(':');
         name0 = name.substring(0, idx);
         name1 = name.substring(idx + 1);
         sb.append(cata.getString(name0));
         sb.append(":");
         sb.append(cata.getString(name1));
      }
      else if(name.indexOf('.') > 0) {
         idx = name.indexOf('.');
         name0 = name.substring(0, idx);
         name1 = name.substring(idx + 1);
         sb.append(cata.getString(name0));
         sb.append(".");
         sb.append(cata.getString(name1));
      }
      else {
         sb.append(cata.getString(name));
      }

      return sb.toString();
   }

   /**
    * Localize the tablelens headers.
    */
   public static void localizeHeader(TableLens lens) {
      // optimization, only check the header rows, not the whole table
      localizeHeader(lens, lens.getHeaderRowCount(), lens.getColCount());

      // for crosstab, check header cols
      if(TableDataDescriptor.CROSSTAB_TABLE == lens.getDescriptor().getType()) {
         localizeHeader(lens, lens.getRowCount(), lens.getHeaderColCount()
         );
      }
   }

   /**
    * Localize the tablelens headers.
    */
   public static void localizeHeader(TableLens lens, AssemblyInfo info,
      Map<String, String> localizeMap)
   {
      // optimization, only check the header rows, not the whole table
      localizeHeader(lens, lens.getHeaderRowCount(), lens.getColCount(),
         false, localizeMap);

      // for crosstab, check header cols
      // if the crosstab has no row headers, we should localize the fake
      // row header
      if(TableDataDescriptor.CROSSTAB_TABLE == lens.getDescriptor().getType()) {
         boolean checkGroupHeader = false;

         if(info instanceof CrosstabVSAssemblyInfo) {
            CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) info;
            VSCrosstabInfo tinfo = cinfo.getVSCrosstabInfo();
            int rowHCnt = tinfo.getDesignRowHeaders().length;
            int aggCnt = tinfo.getDesignAggregates().length;
            checkGroupHeader = rowHCnt == 0 && aggCnt > 0;
         }

         localizeHeader(lens, lens.getRowCount(), lens.getHeaderColCount(),
                        checkGroupHeader, localizeMap);
      }
   }

   /*
    * Localize the tablelens headers.
    */
   private static void localizeHeader(TableLens lens, int rows, int cols) {
      localizeHeader(lens, rows, cols, false, null);
   }

   /*
    * Localize the tablelens headers.
    */
   private static void localizeHeader(TableLens lens, int rows, int cols, boolean checkGroupHeader,
                                      Map<String, String> localizeMap)
   {
      TableDataDescriptor desc = lens.getDescriptor();

      if(desc == null) {
         return;
      }

      boolean calc = (desc.getType() == TableDataDescriptor.CALC_TABLE ||
         desc.getType() == TableDataDescriptor.CROSSTAB_TABLE);
      lens.setDisableFireEvent(false);
      int lastRow = -1;
      int lastCol = -1;
      String lastHeader = null;

      for(int row = 0; lens.moreRows(row) && row < rows; row++) {
         for(int col = 0; col < cols; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path.getType() == TableDataPath.HEADER || row == 0 ||
               (path.getType() == TableDataPath.GROUP_HEADER && col == 0 &&
                  checkGroupHeader) || path.getType() == TableDataPath.SUMMARY ||
               path.getType() == TableDataPath.GRAND_TOTAL) {
               Object header = lens.getObject(row, col);

               // @by larryl, if the header is a date, don't convert it to
               // a string so the date format (e.g. on crosstab header)
               // can be applied later
               if(header == null || header instanceof Date ||
                  header.getClass().isArray() ||
                  calc && header instanceof Number ||
                  path.getType() == TableDataPath.SUMMARY &&
                     !Catalog.getCatalog().getString("Total").equals(header) ||
                  path.getType() == TableDataPath.GRAND_TOTAL &&
                     !Catalog.getCatalog().getString("Grand Total").equals(header)) {
                  continue;
               }

               if(localizeMap != null) {
                  String textID = localizeMap.get(String.valueOf(header));

                  if(path.getType() == TableDataPath.GRAND_TOTAL) {
                     textID = localizeMap.get("Grand Total");
                  }
                  else if(path.getType() == TableDataPath.SUMMARY) {
                     textID = localizeMap.get("Group Total");
                  }

                  header = localizeTextID(textID) == null ? header : textID;
               }

               String header0 = Tool.localize(header.toString());
               lens.setObject(row, col, header0);

               lastRow = row;
               lastCol = col;
               lastHeader = header0;
            }
         }
      }

      if(lastRow != -1 && lastCol != -1) {
         lens.setDisableFireEvent(true);
         // trigger fire event.
         lens.setObject(lastRow, lastCol, lastHeader);
      }
   }

   /**
    * Convert an array to a vector.
    */
   public static Vector<Object> toVector(Object[] arr) {
      Vector<Object> vec = new Vector<>();

      if(arr != null) {
         for(Object item : arr) {
            vec.addElement(item);
         }
      }

      return vec;
   }

   /**
    * Encode special characters in a URL string. For example the character
    * '<' is replaced by the string "&lt;" and '>'.
    * @param str the string to encode.
    */
   public static String encodeHTML(String str) {
      return encodeHTML(str, true);
   }

   /**
    * Encode special characters in a URL string. For example the character
    * '<' is replaced by the string "&lt;" and '>'.
    * @param str the string to encode.
    * @param escape true to escape HTML characters.
    */
   public static String encodeHTML(String str, boolean escape) {
      StringBuilder buf = new StringBuilder();
      boolean esc = false;
      char prev = ' ';

      if(str == null) {
         return "";
      }

      int length = str.length();

      for(int i = 0; i < length; i++) {
         char c = str.charAt(i);

         if(escape && !esc && c == '\\') {
            esc = true;

            // @by billh, if backslash is not in front of the <>, that means
            // it is not used to escape html tags, and we keep the backslash
            // so normal backslash does not disappear from output
            if(i == length - 1) {
               buf.append('\\');
            }

            continue;
         }

         if(escape && esc) {
            // @by larryl, if backslash is not in front of the <>, that means
            // it is not used to escape html tags, and we keep the backslash
            // so normal backslash does not disappear from output
            if(c != '<' && c != '>') {
               buf.append('\\');
            }

            buf.append(c);
            esc = false;
         }
         else {
            switch(c) {
            case '<':
               buf.append("&lt;");
               break;
            case '>':
               buf.append("&gt;");
               break;
            case '&':
               buf.append("&amp;");
               break;
            case ' ':
               if(prev == ' ') {
                  buf.append("&nbsp;");
               }
               else {
                  buf.append(' ');
               }

               break;
            case '\n':
               buf.append("<br>");

               // skip next \r following a \n
               if(i + 1 < str.length()) {
                  if(str.charAt(i + 1) == '\r') {
                     i++;
                  }
               }

               break;
            case '\r':
               /*
                  @by mikec, if there is an widow \r, do not
                  make <br>, to be consistent with text paintable.
               buf.append("<br>");

               // skip next \n following a \r
               if(i + 1 < str.length()) {
                  if(str.charAt(i + 1) == '\n') {
                     i++;
                  }
               }
               break;
               */
            case '\t':
               buf.append("&nbsp;");
            default:
               buf.append(c);
               break;
            }
         }

         prev = c;
      }

      return buf.toString();
   }

   /**
    * Encodes the special characters in an XML string, including the
    * blank space character which is encoded to "&nbsp;".
    */
   public static String encodeXML(String str) {
      return encodeXML(str, encoding, false);
   }

   /**
    * Encodes the special characters in an XML attribute string. The apostrophe
    * character is not encoded, nor is the blank space character.
    */
   public static String encodeHTMLAttribute(String str) {
      return encodeXML(str, encodingXMLAttr, true);
   }

   /**
    * Escapes characters that cause problems in javascript. For example
    * ' is escaped to \' , " to \" , and  \ to \\.
    */
   public static String escapeJavascript(String str) {
      return encodeXML(str, encodingJS, false);
   }

   /**
    * Escape special characters in a string, but not including the
    * blank space character which is left untouched during the translation.
    * @param str the string to encode.
    */
   public static String escape(String str) {
      return encodeXML(str, encodingXML, true);
   }

   /**
    * Escape ]]> so it does not cause problem in CDATA section.
    */
   @XSSCleanser
   public static String encodeCDATA(String str) {
      return encodeString(str, encodingCDATA);
   }

   /**
    * Decode encoded ]]> in CDATA section.
    */
   public static String decodeCDATA(String str) {
      return encodeString(str, decodingCDATA);
   }

   /**
    * Replace the char in [0] with string in [1].
    * @param removeCtrl true to remove all ISO control characters.
    */
   public static String encodeXML(String str, String[][] encoding, boolean removeCtrl) {
      if(str == null) {
         return "";
      }

      StringBuilder buf = new StringBuilder();
      int length = str.length();

      encodeLoop:
      for(int i = 0; i < length; i++) {
         char c = str.charAt(i);

         if(c == '\u0000' || removeCtrl && Character.isISOControl(c) && c != '\n') {
            buf.append(" ");
            continue;
         }

         for(int j = 0; j < encoding[1].length; j++) {
            if(c == encoding[1][j].charAt(0)) {
               buf.append(encoding[0][j]);
               continue encodeLoop;
            }
         }

         buf.append(c);
      }

      return buf.toString();
   }

   /**
    * Encode a string by mapping the values from map[i][0] to map[i][1].
    */
   public static String encodeString(String data, String[][] map) {
      if(data == null) {
         return null;
      }

      String val = data;

      for(String[] entry : map) {
         StringBuilder sval = new StringBuilder();
         int begin = 0;
         int idx;
         int len = entry[0].length();

         while((idx = val.indexOf(entry[0], begin)) >= 0) {
            sval.append(val, begin, idx);
            sval.append(entry[1]);
            begin = idx + len;
         }

         sval.append(val.substring(begin));
         val = sval.toString();
      }

      return val;
   }

   /**
    * Check if a string contains cjk characters.
    * @param val the specified string value.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public static boolean containsCJK(String val) {
      if(val == null) {
         return false;
      }

      for(int i = 0; i < val.length(); i++) {
         char c = val.charAt(i);

         if(c >= 0x4E00 && c <= 0x9FBF) {
            return true;
         }
      }

      return false;
   }

   /**
    * Do not split the parameter by "," directly.
    * eg. use input "C\\\",D" should not to be splited.
    * Problem-solving steps is:
    * 1. replace "\\" to "[@_@]". "C\\\,D" --> "C[@_@]\",D"
    * 2. replace '\"' to "[^_^]". "C[@_@]\",D --> "C[@_@][^_^],D"
    * 3. replace "," to "[~_~]". "C[@_@][^_^],D" --> "C[@_@][^_^][~_~]D"
    * 4. replace '"' with "". "C[@_@][^_^][~_~]D" --> C[@_@][^_^][~_~]D
    * 5. split with ",". C[@_@][^_^][~_~]D --> [C[@_@][^_^][~_~]D]
    * 6. replace [@_@] back to "\\", [~_~] back to ","
    * and replace [^_^] back to "\"" [C[@_@][^_^][~_~]D] --> [C\\\",D]
    */
   public static String[] convertParameter(String str) {
      if(str == null) {
         return null;
      }

      // 1. replace "\\" to [@_@]
      str = str.replace("\\\\", "[@_@]");

      // 2. replace '\"' to [^_^]
      str = str.replace("\\\"", "[^_^]");

      // 3. replace "," to "[~_~]"
      Set<String> groups = new HashSet<>();
      String temp = str;
      int finish = parameterSplit(temp, groups);

      while(finish != -1) {
         finish = parameterSplit(temp = temp.substring(finish), groups);
      }

      for(String group : groups) {
         String res = group.replaceAll(",", "[~_~]");
         str = str.replace(group, res);
      }

      // 4. replace '"' to ""
      str = str.replace("\"", "");

      // 5. split with ","
      String[] values = str.split(",");

      // 6. replace [~_~] back to "," and replace [^_^] back to "\""
      java.util.List<String> results = new ArrayList<>();

      for(String value : values) {
         value = value.replace("[@_@]", "\\");
         value = value.replace("[~_~]", ",");
         value = value.replace("[^_^]", "\"");
         results.add(value);
      }

      return results.toArray(new String[0]);
   }

   /**
    * Split the parameter with the specified value.
    */
   private static int parameterSplit(String str, Set<String> groups) {
      Pattern p = Pattern.compile("(\".[^\"]+[\"])");
      Matcher m = p.matcher(str);

      if(m.find()) {
         String group = m.group(0);
         groups.add(group);
         return m.end();
      }

      return -1;
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source string.
    * @return encoded string.
    */
   public static String byteEncode(String source) {
      return byteEncode(source, false);
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source string.
    * @param isEncodeAll true to encode all charaters including Latin
    * characters.
    * @return encoded string.
    */
   public static String byteEncode(String source, boolean isEncodeAll) {
      return byteEncode(source, isEncodeAll, false);
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source string.
    * @param isEncodeAll true to encode all characters including Latin
    * characters.
    * @param isEncodeBlank true to encode white space.
    * @return encoded string.
    */
   public static String byteEncode(String source, boolean isEncodeAll, boolean isEncodeBlank) {
      if(source == null) {
         return null;
      }

      StringBuilder ret = new StringBuilder();
      int len = source.length();

      for(int i = 0; i < len; i++) {
         char ch = source.charAt(i);

         if(i > 0 && ch == '[' && source.charAt(i - 1) == '^') {
            ret.append(ch);
         }
         else {
            ret.append(byteEncode(ch, isEncodeAll, isEncodeBlank));
         }
      }

      return ret.toString();
   }

   /**
    * Encode non-ascii and url reserved characters to unicode enclosed in '[]'.
    */
   public static String byteEncode2(String source) {
      return byteEncode2(source, false);
   }

   /**
    * Encode non-ascii and url reserved characters to unicode enclosed in '[]'.
    * @param forHtml true if for encode string witch is put into html page.
    *  This encoding is for XSS security issue.
    */
   public static String byteEncode2(String source, boolean forHtml) {
      if(source == null) {
         return null;
      }

      StringBuilder ret = new StringBuilder();

      for(int i = 0; i < source.length(); i++) {
         char ch = source.charAt(i);

         if(i > 0 && ch == '[' && source.charAt(i - 1) == '^') {
            ret.append(ch);
         }
         else {
            ret.append(byteEncode2(ch, forHtml));
         }
      }

      return ret.toString();
   }

   /**
    * Encode a non-ascii character to unicode enclosed in '[]'.
    * @param ch source character.
    * @param isEncodeAll  true to encode all charaters including Latin.
    * characters.
    * @param isEncodeBlank true to encode white space.
    * @return encoded string.
    */
   private static String byteEncode(char ch, boolean isEncodeAll, boolean isEncodeBlank) {
      // no need to encode ascii
      if(ch < 128 && ch != '[' && ch != ']' && !isEncodeAll && ch != '/' && ch != '\'' &&
         ch != '=' && ch != '#' && ch != '&' && ch != '+' &&
         !(isEncodeBlank && ch == ' '))
      {
         return String.valueOf(ch);
      }
      else {
         return "~_" + Integer.toString(ch, 16) + "_~";
      }
   }

   /**
    * Encode a non-ascii, url reserved character and xml reserved character to
    * unicode enclosed in '[]'.
    * @param forHtml true if for encode string witch is put into html page.
    *  This encoding is for XSS security issue.
    */
   private static String byteEncode2(char ch, boolean forHtml) {
      if(ch < 128 && ch != '[' && ch != ']' && ch != '/' && ch != '\'' &&
         ch != '=' && ch != '%' && ch != '&' && ch != '?' &&
         ch != '#' && ch != '"' && ch != '<' && ch != '>' &&
         ch != ',' && ch != '\\' && ch != '+' && ch != '`' &&
         ch != '(' && ch != ')' && ch != '{' && ch != '}' &&
         ch != '|')
      {
         if(forHtml && (ch == ')' || ch == ';' || ch == '\n' ||
            ch == '-' || ch == '*' || ch == '|' || ch == '^'))
         {
            return "~_" + Integer.toString(ch, 16) + "_~";
         }

         return String.valueOf(ch);
      }
      else {
         return "~_" + Integer.toString(ch, 16) + "_~";
      }
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string
    */
   public static String byteDecode(String encString) {
      if(encString == null) {
         return null;
      }

      boolean newEncoding = NEW_BYTE_ENCODING_PATTERN.matcher(encString).find();
      boolean oldEncoding = !newEncoding && OLD_BYTE_ENCODING_PATTERN.matcher(encString).find();

      if(!newEncoding && !oldEncoding) {
         return encString;
      }

      Matcher matcher;

      if(newEncoding) {
         matcher = NEW_BYTE_ENCODING_PATTERN.matcher(encString);
      }
      else {
         matcher = OLD_BYTE_ENCODING_PATTERN.matcher(encString);
      }

      StringBuilder str = new StringBuilder();
      int lastPos = 0;

      while(matcher.find()) {
         if(oldEncoding && matcher.start() == 1 && encString.charAt(0) == '^') {
            // ignore encoded array, e.g. ^[x]^
            continue;
         }

         str.append(encString, lastPos, matcher.start());
         lastPos = matcher.end();
         char ch = (char) Integer.parseInt(matcher.group(1), 16);
         str.append(ch);
      }

      if(lastPos < encString.length()) {
         str.append(encString, lastPos, encString.length());
      }

      return str.toString();
   }

   /**
    * use ip to replace localhost
    */
   public static String replaceLocalhost(String linkUri) {
      String ip = Tool.getIP();
      String regx = "http(s)?://localhost.*";

      if(Pattern.matches(regx, linkUri) && ip != null && !ip.isEmpty()) {
         linkUri = linkUri.replaceFirst("localhost", ip);
      }

      return linkUri;
   }

   /**
    * Find the delimiter in the string and respect quotes.
    */
   public static int indexOfWithQuote(String str, String delim, char escape) {
      return indexOfWithQuote(str, delim, escape, 0, '\'', '"');
   }

   /**
    * Find the delimiter in the string and respect quotes.
    */
   public static int indexOfWithQuote(String str, String delim, char escape,
                                      int startIdx, char ... quotes)
   {

      boolean inQuote = false;
      char quoteChar = 0;

      for(int i = startIdx; i < str.length(); i++) {
         char character = str.charAt(i);
         boolean isQuote = false;

         for(char c : quotes) {
            if(character == c) {
               isQuote = true;
               break;
            }
         }

         if(isQuote) {
            if(quoteChar == 0) {
               quoteChar = character;
            }

            if(quoteChar == character &&
               (escape == 0 || i == 0 || str.charAt(i - 1) != escape))
            {
               inQuote = !inQuote;

               if(!inQuote) {
                  quoteChar = 0;
               }
            }
         }
         else if(!inQuote && str.startsWith(delim, i)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Merge two hashtables into one.
    * @param map1 first hashtable to copy from.
    * @param map2 second hashtable to copy from.
    * @return a Hashtable containing all the keys of the first and
    * second hashtables.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public static Hashtable mergeMap(Hashtable map1, Hashtable map2) {
      Hashtable returnmap = (Hashtable) map2.clone();
      Enumeration names = map1.keys();

      while(names.hasMoreElements()) {
         Object a = names.nextElement();
         Object b = map1.get(a);

         returnmap.put(a, b);
      }

      return returnmap;
   }

   /**
    * merge two arrays.
    */
   public static Object[] mergeArray(Object[] arr1, Object[] arr2) {
      if(arr1 == null && arr2 == null) {
         return new String[] {};
      }

      if(arr1 == null) {
         return arr2;
      }

      if(arr2 == null) {
         return arr1;
      }

      Object[] arr = (Object[]) Array.newInstance(
         arr1.getClass().getComponentType(), arr1.length + arr2.length);

      System.arraycopy(arr1, 0, arr, 0, arr1.length);
      System.arraycopy(arr2, 0, arr, arr1.length, arr2.length);

      return arr;
   }

   /**
    * Get the String representation of a Color object. For example
    * colorToHTMLString(Color.red) returns the String "#FF0000".
    * @param c the color object to get an html string representation of.
    * @return string representation of an html object.
    */
   public static String colorToHTMLString(Color c) {
      String str = "000000" + Integer.toString(c.getRGB() & 0xFFFFFF, 16);

      return str.substring(str.length() - 6);
   }

   /**
    * Get a Color object from a Hexadecimal String representation. For example
    * getColorFromHexString("#FF0000") returns Color.red
    * @param hexStr a string representation of a color.
    * @return Color object
    */
   public static Color getColorFromHexString(String hexStr) {
      Color cor = null;

      if(hexStr != null && !(hexStr.equals(NULL))) {
         if(hexStr.length() == 0) {
            return null;
         }

         int hexStrSize = hexStr.contains("#") ? hexStr.length() - 1 : hexStr.length();
         int i = hexStr.contains("#") ? 0 : 1;

         // get the red Hex string
         String redStr = hexStr.substring(1 - i, 3 - i);
         // get the green  Hex string
         String greenStr = hexStr.substring(3 - i, 5 - i);
         // get the blue string
         String blueStr = hexStrSize > 6 ? hexStr.substring(5 - i, 7 - i) : hexStr.substring(5 - i);
         //get alpha
         String alphaStr = hexStrSize > 6 ? hexStr.substring(7 - i) : "ff";

         cor = new Color(Integer.parseInt(redStr, 16),
            Integer.parseInt(greenStr, 16), Integer.parseInt(blueStr, 16),
            Integer.parseInt(alphaStr, 16));
      }

      return cor;
   }

   /**
    * Utility method to delete a file. Useful especially in recursively
    * deleting a directory that is not empty.
    * @param file the File object to delete.
    */
   public static void deleteFile(File file) {
      if(file == null || !file.exists()) {
         return;
      }

      if(file.isDirectory()) {
         File[] files = file.listFiles();
         assert files != null;

         for(File child : files) {
            deleteFile(child);
         }
      }

      file.delete();
   }

   /**
    * Utility method to copy a file from an inputstream to an outputstream.
    * @param in source inputstream.
    * @param out destination outputstream.
    * @return status of the file copy, true signals a successful copy.
    */
   public static boolean fileCopy(InputStream in, OutputStream out) throws IOException {
      return fileCopy(in, true, out, true);
   }

   /**
    * Utility method to copy a file from an inputstream to an outputstream.
    * @param in source inputstream.
    * @param releaseInput if release input stream after copy.
    * @param out destination outputstream.
    * @param releaseOutput if release output stream after copy.
    * @return status of the file copy, true signals a successful copy.
    */
   public static boolean fileCopy(InputStream in, boolean releaseInput,
                                  OutputStream out, boolean releaseOutput)
      throws IOException
   {
      byte[] bytes = new byte[1024 * 32];
      int size;

      try {
         while((size = in.read(bytes)) > 0) {
            out.write(bytes, 0, size);
         }

         out.flush();
      }
      catch(Throwable exc) {
         throw new IOException("Failed to copy file", exc);
      }

      return true;
   }

   /**
    * Lock a file.
    *
    * @param path path the absolute/relative path.
    */
   public static void lock(String path) {
      if(path == null) {
         throw new RuntimeException("Specify the file name you want to lock.");
      }

      Cluster.getInstance().lockKey(getLockFile(path));
   }

   /**
    * Unlock a file.
    *
    * @param path path the absolute/relative path.
    */
   public static void unlock(String path) {
      if(path == null) {
         throw new RuntimeException("Specify the file name you want to lock.");
      }

      try {
         Cluster.getInstance().unlockKey(getLockFile(path));
      }
      catch(IllegalMonitorStateException ex) {
         // this is to keep consistency with previous file lock, where it's ignored.
         LOG.warn("Unlock called by thread that doesn't own the lock: " + path, ex);
      }
   }

   /**
    * Get the lock file for the specified path.
    */
   private static String getLockFile(String path) {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File pathFile = fileSystemService.getFile(path);
      File homeFile = fileSystemService.getFile(ConfigurationContext.getContext().getHome());

      Path pathPath;
      Path homePath;

      try {
         pathPath = pathFile.getCanonicalFile().toPath().normalize();
      }
      catch(IOException ex) {
         pathPath = pathFile.getAbsoluteFile().toPath().normalize();
      }

      try {
         homePath = homeFile.getCanonicalFile().toPath().normalize();
      }
      catch(IOException ex) {
         homePath = homeFile.getAbsoluteFile().toPath().normalize();
      }

      if(pathPath.startsWith(homePath)) {
         return homePath.relativize(pathPath).toString();
      }

      return pathPath.toString();
   }

   /**
    * Load an image as a resource.
    */
   @SuppressWarnings("rawtypes")
   public static Image getImage(Object base, String res) {
      if(res == null) {
         return null;
      }

      Class<?> cls = (base == null) ? inetsoft.report.StyleConstants.class :
         ((base instanceof Class) ? ((Class) base) : base.getClass());
      String key = cls.getName() + "_" + res;
      Image img = imgcache.get(key);

      if(img == null) {
         InputStream is = getResourceInputStream(cls, res);

         if(is == null) {
            return null;
         }

         // don't hold too many images
         if(imgcache.size() > 100) {
            imgcache.clear();
         }

         img = getImage(is, false);
         imgcache.put(key, img);
      }

      return img;
   }

   /**
    * Get the resource input stream.
    */
   private static InputStream getResourceInputStream(Class<?> cls, String res) {
      InputStream is = null;

      if(res.startsWith("https://") || res.startsWith("http://")) {
         try {
            URL url = new URL(res);
            URLConnection conn = url.openConnection();
            conn.addRequestProperty("User-Agent", "Mozilla/4.76");
            is = conn.getInputStream();
         }
         catch(Exception ex) {
            LOG.warn("Failed to open http url: " + res, ex);
         }
      }
      else {
         is = cls.getResourceAsStream(res);
      }

      if(is == null) {
         File file = FileSystemService.getInstance().getFile(res);

         if(file.exists()) {
            try {
               is = new FileInputStream(res);
            }
            catch(Exception ex) {
               LOG.warn("Failed to open file: " + res, ex);
            }
         }
      }

      return is;
   }

   /**
    * Read an image from an input stream.
    */
   public static Image getImage(InputStream is) {
      return getImage(is, true);
   }

   /**
    * Read an image from an input stream.
    */
   public static Image getImage(InputStream is, boolean fromSpace) {
      try {
         return ImageIO.read(is);
      }
      catch(IOException | NoClassDefFoundError ex) {
         LOG.warn("Failed to read image", ex);
         return null;
      }
   }

   /**
    * Zips an array of files.
    * @param files array of files to zip.
    * @param zipname the name of the output zip.
    */
   public static boolean zipFiles(String[] files, String zipname, boolean type) {
      try {
         FileOutputStream os = new FileOutputStream(zipname);
         ZipOutputStream zip = new ZipOutputStream(os);

         for(String filePath : files) {
            File file = FileSystemService.getInstance().
               getFile(Tool.convertUserFileName(filePath));

            if(file.exists()) {
               byte[] buf = new byte[1024];
               int len;
               ZipEntry zipEntry = new ZipEntry(type ?
                                                   file.getName() :
                                                   file.getPath());

               try {
                  FileInputStream fin = new FileInputStream(file);
                  BufferedInputStream in = new BufferedInputStream(fin);

                  zip.putNextEntry(zipEntry);

                  while((len = in.read(buf)) >= 0) {
                     zip.write(buf, 0, len);
                  }

                  in.close();

                  zip.closeEntry();
               }
               catch(IOException ignore) {
               }
            }
         }

         zip.close();
         os.close();
         return true;
      }
      catch(IOException e) {
         LOG.error(
                     "Failed to create zip file: " +
                     arrayToString(files) + " >> " + zipname, e);
      }

      return false;
   }

   /**
    * Zips an array of files.
    * @param files array of files to zip.
    * @param zipname the name of the output zip.
    */
   public static boolean zipFiles(String[] files, String zipname, boolean type,
      String password)
   {
      if(password == null || password.length() == 0) {
         return zipFiles(files, zipname, type);
      }

      try {
         ExtAesZipFileEncrypter enc =
            new ExtAesZipFileEncrypter(zipname, password);
         enc.add(files, type);
         enc.close();

         return true;
      }
      catch(Throwable e) {
         LOG.error(
           "There was an error trying to encrypt the zip file. " +
           "You must install the Java Cryptography Extension (JCE) " +
           "Unlimited Strength Jurisdiction Policy Files. " +
                     "For more information, please see our " +
                     "documentation and release notes: " +
                     arrayToString(files) + " >> " + zipname, e);
      }

      return false;
   }

   /**
    * Create a hash code for image data.
    * @param w image width.
    * @param h image height.
    * @param data image data.
    */
   public static Object getImageKey(int w, int h, byte[] data) {
      int[] key = new int[256];

      for(int i = 0, pos = 0; i < data.length; i++) {
         int cir = key[pos] >>> 31;

         key[pos] = (key[pos] << 1) + cir;
         key[pos++] += data[i];

         if(pos == key.length) {
            pos = 0;
         }
      }

      Vector<Integer> vec = new Vector<>();

      vec.addElement(w);
      vec.addElement(h);
      vec.addElement(data.length);

      for(int element : key) {
         vec.addElement(element);
      }

      return vec;
   }

   /**
    * Convert a string to a valid file name.
    */
   public static String toFileName(String str) {
      return toFileName(str, "_");
   }

   /**
    * Convert a string to a valid file name.
    */
   public static String toFileName(String str, String replacement) {
      StringBuilder buf = new StringBuilder();
      boolean strict = "true".equals(SreeEnv.getProperty("filename.strict", "true"));

      for(int i = 0; i < str.length(); i++) {
         char ch = str.charAt(i);
         boolean ok = Character.isLetterOrDigit(ch);

         if(!ok && !strict) {
            switch(ch) {
            case ' ':
               // space allowed in middle of name.
               ok = i > 0 && i < str.length() - 1;
               break;
            case '-':
               // dash allowed in middle and non-consecutive (windows).
               ok = i > 0 && i < str.length() - 1 && str.charAt(i - 1) != '-';
               break;
            case ',':
               ok = true;
            }
         }

         buf.append(ok ? ch : replacement);
      }

      return buf.toString();
   }

   /**
    * Create a reversible hash for the specified String. This hash is not
    * secure, so it should not be used to transmit sensitive data over an
    * unencrypted network.
    *
    * @param pwd the clear text password.
    *
    * @return the hashed password.
    *
    * @deprecated use {@link #encryptPassword(String)} instead.
    */
   @SuppressWarnings("DeprecatedIsStillUsed")
   @Deprecated
   static String hashPassword(String pwd) {
      if(pwd == null || pwd.length() == 0) {
         return pwd;
      }

      StringBuilder buffer = new StringBuilder();
      int i;
      int j;
      int k;

      buffer.append("\\pwd");

      for(i = 0, j = 0; i < pwd.length(); i++, j++) {
         if(j == pwdMask.length) {
            j = 0;
         }

         k = ((pwd.charAt(i)) & 0xFF) ^ pwdMask[j];
         String hex = Integer.toString(k, 16);

         if(hex.length() == 1) {
            hex = "0" + hex;
         }

         buffer.append(hex);
      }

      return buffer.toString();
   }

   /**
    * Get the clear text password from a hash created by the hashPassword()
    * method.
    *
    * @param hash the hashed password.
    *
    * @return the clear text password.
    *
    * @deprecated use {@link #decryptPassword(String)} instead.
    */
   @SuppressWarnings("DeprecatedIsStillUsed")
   @Deprecated
   static String unhashPassword(String hash) {
      if(hash != null && hash.startsWith("\\pwd")) {
         hash = hash.substring(4);
         StringBuilder buffer = new StringBuilder();
         int i;
         int j;
         int k;

         for(i = 0, j = 0; i < hash.length(); i += 2, j++) {
            if(j == pwdMask.length) {
               j = 0;
            }

            if(i == hash.length() - 2) {
               k = Integer.parseInt(hash.substring(i), 16) ^ pwdMask[j];
            }
            else {
               k = Integer.parseInt(hash.substring(i, i + 2), 16) ^ pwdMask[j];
            }

            buffer.append((char) k);
         }

         hash = buffer.toString();
      }

      return hash;
   }

   /**
    * Encrypt a password by using a base password and salt.
    */
   public static String encryptPassword(String pw) {
      return encryptPassword(pw, true);
   }

   /**
    * Encrypt a password by using a base password and salt.
    */
   public static String encryptPassword(String pw, boolean forceLocal) {
      if(forceLocal) {
         // use local PasswordEncryption, when user force to user detail secret value insteadof a secret id.
         return PasswordEncryption.newLocalInstance(true).encryptPassword(pw);
      }

      return PasswordEncryption.newInstance().encryptPassword(pw);
   }

   /**
    * Decrypt a password to a PasswordCredential obj.
    */
   public static Credential decryptPasswordToCredential(
      String encryptedPassword, Class<? extends Credential> aClass, String dbType)
   {
      ObjectMapper mapper = new ObjectMapper();

      try {
         String result = null;

         if(isVaultDatabaseSecretsEngine(dbType)) {
            result = decryptDBPassword(encryptedPassword, dbType);
         }
         else {
            result = decryptPassword(encryptedPassword, false);
         }

         if(result != null) {
            return mapper.convertValue(mapper.readTree(result), aClass);
         }

         return null;
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Decrypt a password.
    */
   public static String decryptPassword(String encryptedPassword) {
      return decryptPassword(encryptedPassword, true);
   }

   /**
    * Decrypt a password.
    */
   public static String decryptPassword(String encryptedPassword, boolean local) {
      if(local) {
         return PasswordEncryption.newLocalInstance(false).decryptPassword(encryptedPassword);
      }

      return PasswordEncryption.newInstance().decryptPassword(encryptedPassword);
   }

   /**
    * Decrypt a password.
    */
   public static String decryptDBPassword(String encryptedPassword, String dbType) {
      if(!(PasswordEncryption.newInstance() instanceof HcpVaultSecretsPasswordEncryption)) {
         return encryptedPassword;
      }

      return PasswordEncryption.newInstance().decryptDBPassword(encryptedPassword, dbType);
   }

   public static boolean isVaultDatabaseSecretsEngine(String dbType) {
      return dbType != null && !dbType.isEmpty() &&
         PasswordEncryption.newInstance() instanceof HcpVaultSecretsPasswordEncryption;
   }

   public static void refreshDatabaseCredentials(XDataSource ds) {
      Credential credential = null;

      if(ds instanceof JDBCDataSource) {
         credential = ((JDBCDataSource) ds).getCredential();
      }
      else if(ds instanceof TabularDataSource) {
         credential = ((TabularDataSource) ds).getCredential();
      }
      else if(ds instanceof XMLADataSource) {
         credential = ((XMLADataSource) ds).getCredential();
      }

      if(credential instanceof CloudCredential) {
         Credential newCredential = Tool.decryptPasswordToCredential(
            credential.getId(), credential.getClass(), credential.getDBType());

         if(newCredential != null) {
            newCredential.setId(credential.getId());
            ((CloudCredential) credential).refreshCredential(newCredential);
         }
      }
   }

   public static JsonNode loadCredentials(String secretId) {
      return Tool.loadCredentials(secretId, false);
   }

   public static JsonNode loadCredentials(String secretId, boolean local) {
      if(Tool.isEmptyString(secretId)) {
         return null;
      }

      try {
         ObjectMapper mapper = new ObjectMapper();
         String credential = Tool.decryptPassword(secretId.trim(), local);
         return mapper.readTree(credential);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to load credential by secret ID '" + secretId + "'");
      }
   }

   public static String getClientSecretRealValue(String value, String secretKey) {
      if(Tool.isCloudSecrets()) {
         try {
            JsonNode credentials = Tool.loadCredentials(value);

            if(credentials != null && credentials.has(secretKey)) {
               return credentials.get(secretKey).asText();
            }
         }
         catch(Exception e) {
            return value;
         }
      }

      return value;
   }

   /**
    * Generates a random password.
    *
    * @return the generated password.
    */
   public static String generatePassword() {
      return new BigInteger(130, secureRandom).toString(32);
   }

   public static SecureRandom getSecureRandom() {
      return secureRandom;
   }

   static boolean cacheLocked(String name1, String name2, File f1) {
      return Tool.equals(name1, name2) && f1.exists();
   }

   /**
    * Get the cache directory for temp files.
    */
   // TODO: Replace calls to Tool.getCacheDirectory() with the methods available in the FileSystemService.
   public static String getCacheDirectory() {
      // @ankitmathur, As part of our initiative to consolidate File System access, placing the
      // logic of this method into FileSystemService.getCacheDirectory(). The new mechanism uses
      // Java NIO2 API.
      String cacheDir = null;

      try {
         cacheDir = FileSystemService.getInstance().getCacheDirectory();
      }
      catch (Exception ex) {
         LOG.warn("Exception getting cache directory from FileSystemService:" + ex);
      }

      return cacheDir;
   }

   /**
    * Create a temporary file in the cache directory.
    */
   public static File getCacheTempFile(String prefix, String suffix) {
      String cdir = getCacheDirectory();
      long findex = System.currentTimeMillis();
      prefix = Tool.toFileName(prefix);
      File file = new File(cdir, prefix + findex++ + "." + suffix);

      while(true) {
         try {
            if(file.createNewFile()) {
               break;
            }
         }
         catch(IOException ex) {
            // this should not happen and we should terminate here
            // otherwise it may stuck in an infinite loop
            LOG.error(
                        "Creating temp file caused IO Error: " + file, ex);
            return null;
         }
         catch(Exception ex) {
            LOG.error(
                        "Failed to create temp file: " + file, ex);
            return null;
         }

         file = new File(cdir, prefix + findex++ + "." + suffix);
      }

      return file;
   }

   public static boolean isMyReport(String path) {
      if(path == null) {
         return false;
      }

      return Tool.MY_DASHBOARD.equals(path) || path.startsWith(Tool.MY_DASHBOARD + "/");
   }

   /**
    * Get the build number of this software.
    */
   public static String getBuildNumber() {
      String buildno = buildNumber;

      if(buildno == null || buildno.equals("") || buildno.startsWith("@")) {
         return Catalog.getCatalog().getString("Development Build");
      }

      return buildno;
   }

   /**
    * Get the Style Report version. The version is in dot separated format,
    * e.g. 1.3.1.
    * @return Style Report version.
    */
   public static String getReportVersion() {
      return "1.0.0";
   }

   /**
    * Check if the number value is zero.
    */
   public static boolean isZero(Number val) {
      if(val instanceof Double) {
         return val.doubleValue() == 0d;
      }
      else if(val instanceof Float) {
         return val.floatValue() == 0f;
      }
      else if(val instanceof BigDecimal) {
         return val.doubleValue() == 0d;
      }
      else {
         return val.intValue() == 0;
      }
   }

   public static Frame getInvisibleFrame() {
      if(root == null) {
         root = new Frame("invisible");
         root.pack();
      }

      return root;
   }

   /**
    * Check if a class is a date class.
    * @param c class object to be checked.
    */
   public static boolean isDateClass(Class<?> c) {
      return c != null && java.util.Date.class.isAssignableFrom(c);
   }

   /**
    * Check if a class is a number class.
    * @param c class object to be checked.
    */
   public static boolean isNumberClass(Class<?> c) {
      return c != null && java.lang.Number.class.isAssignableFrom(c);
   }

   /**
    * Check if a class is a string class.
    * @param c class object to be checked.
    */
   public static boolean isStringClass(Class<?> c) {
      return c != null && java.lang.String.class.isAssignableFrom(c);
   }

   /**
    * Check if the specified string is a valid identifier. A valid identifier
    * should be neither null nor empty. A valid identifier should also start
    * with a letter or underscore and all remaining characters be a letter,
    * digit, or underscore.
    *
    * @param ident the identifier to check.
    *
    * @return <code>true</code> if the identifier is valid; <code>false<code>
    *         otherwise.
    */
   public static boolean isValidIdentifier(String ident) {
      return isValidIdentifier(ident, null);
   }

   /**
    * Check if the specified string is a valid identifier. A valid identifier
    * should be neither null nor empty. A valid identifier should also start
    * with a letter or underscore and all remaining characters be a letter,
    * digit, or underscore.
    *
    * @param ident the identifier to check.
    * @param com the component in which to display a warning message if the
    *          identifier is invalid. If <code>null</code>, no message will be
    *          displayed.
    *
    * @return <code>true</code> if the identifier is valid; <code>false<code>
    *         otherwise.
    */
   public static boolean isValidIdentifier(String ident, Component com) {
      return isValidIdentifier(ident, com, true);
   }

   /**
    * Check if the specified string is a valid identifier. A valid identifier
    * should be neither null nor empty. A valid identifier should also start
    * with a letter or underscore and all remaining characters be a letter,
    * digit, or underscore.
    *
    * @param ident the identifier to check.
    * @param com the component in which to display a warning message if the
    *            identifier is invalid. If <code>null</code>, no message will be
    *            displayed.
    * @param strict <code>true</code> if strict checking is enforced. If
    *               <code>false<code>, the space, dollar sign, minus sign,
    *               ampersand, and at symbol are all considered valid
    *               characters.
    *
    * @return <code>true</code> if the identifier is valid; <code>false<code>
    *         otherwise.
    */
   public static boolean isValidIdentifier(String ident, Component com,
                                           boolean strict) {
      return isValidIdentifier(ident, com, strict, false);
   }

   /**
    * Check if the specified string is a valid identifier. A valid identifier
    * should be neither null nor empty. A valid identifier should also start
    * with a letter or underscore and all remaining characters be a letter,
    * digit, or underscore.
    *
    * @param ident the identifier to check.
    * @param com the component in which to display a warning message if the
    *            identifier is invalid. If <code>null</code>, no message will be
    *            displayed.
    * @param strict <code>true</code> if strict checking is enforced. If
    *               <code>false<code>, the space, dollar sign, minus sign,
    *               ampersand, and at symbol are all considered valid
    *               characters.
    *
    * @return <code>true</code> if the identifier is valid; <code>false<code>
    *         otherwise.
    */
   public static boolean isValidIdentifier(String ident, Component com,
                                           boolean strict, boolean withSpace) {
      return isValidIdentifier(ident, com, strict, withSpace, null);
   }

   /**
    * Check if the specified string is a valid identifier. A valid identifier
    * should be neither null nor empty. A valid identifier should also start
    * with a letter or underscore and all remaining characters be a letter,
    * digit, or underscore.
    *
    * @param ident the identifier to check.
    * @param com the component in which to display a warning message if the
    *            identifier is invalid. If <code>null</code>, no message will be
    *            displayed.
    * @param strict <code>true</code> if strict checking is enforced. If
    *               <code>false<code>, the space, dollar sign, minus sign,
    *               ampersand, and at symbol are all considered valid
    *               characters.
    * @param withSpace <code>true</code> if the ident could be with space.
    *
    * @return <code>true</code> if the identifier is valid; <code>false<code>
    *         otherwise.
    */
   public static boolean isValidIdentifier(String ident, Component com,
                                           boolean strict, boolean withSpace,
                                           String type)
   {
      String invalidMsg = checkValidIdentifier(ident, com, strict, withSpace, type);
      return invalidMsg == null;
   }

   /**
    * Check if the specified string is a valid identifier. A valid identifier
    * should be neither null nor empty. A valid identifier should also start
    * with a letter or underscore and all remaining characters be a letter,
    * digit, or underscore.
    *
    * @param ident the identifier to check.
    * @param com the component in which to display a warning message if the
    *            identifier is invalid. If <code>null</code>, no message will be
    *            displayed.
    * @param strict <code>true</code> if strict checking is enforced. If
    *               <code>false<code>, the space, dollar sign, minus sign,
    *               ampersand, and at symbol are all considered valid
    *               characters.
    * @param withSpace <code>true</code> if the ident could be with space.
    *
    * @return the check result message if has invalid ident.
    */
   public static String checkValidIdentifier(String ident, Component com,
                                           boolean strict, boolean withSpace,
                                           String type)
   {
      Catalog catalog = Catalog.getCatalog();
      String msg = null;

      // is null or empty?
      if(ident == null || ident.length() == 0) {
         msg = catalog.getString("designer.property.emptyNullError");
         return msg;
      }

      if(ident.contains("]]>")) {
         msg = catalog.getString("designer.property.charSequenceError");

         return msg;
      }

      for(int i = 0; i < ident.length(); i++) {
         char c = ident.charAt(i);

         // not starts with a letter?
         if(i == 0 && !Character.isJavaIdentifierStart(c)) {
            if(isAdditionalChar(c, strict, false, type, true)) {
               continue;
            }

            boolean needSplit = true;

            if(type != null &&
               (type.equals(TABLE_NAME) || type.equals(COLUMN_NAME)))
            {
               msg = strict ?
                  catalog.getString("designer.property.colTabStartCharError",
                     ident) :
                  catalog.getString(
                     "designer.property.colTabStartCharDigitError", ident);
               needSplit = false;
            }
            else {
               msg = strict ?
                  catalog.getString("designer.property.startCharError",
                     ident) :
                  catalog.getString("designer.property.startCharDigitError",
                     ident);
            }

            return msg;
         }
         // any character is not a letter or digit?
         else if(i > 0 && !Character.isJavaIdentifierPart(c)) {
            if(isAdditionalChar(c, strict, withSpace, type)) {
               continue;
            }

            msg = type == null ?
               catalog.getString("designer.property.anyCharError", ident) :
               catalog.getString("designer.property.invalidNameChar", c);

            return msg;
         }
      }

      return msg;
   }

   /**
    * Check if is an additional char.
    */
   private static boolean isAdditionalChar(char c, boolean strict,
                                           boolean withSpace, String type)
   {
      return isAdditionalChar(c, strict, withSpace, type, false);
   }

   /**
    * Check if is an additional char.
    */
   private static boolean isAdditionalChar(char c, boolean strict,
      boolean withSpace, String type, boolean start)
   {
      if(withSpace && c == ' ') {
         return true;
      }

      if(strict) {
         return false;
      }

      // is digit
      if(c >= '0' && c <= '9') {
         return true;
      }

      if(start) {
         if(type == null || type.equals(SHEET_NAME)) {
            return false;
         }
      }

      if(type != null) {
         if(type.equals(SHEET_NAME)) {
            return isValidSheetNameChar(c);
         }
         else if(type.equals(TABLE_NAME) || type.equals(COLUMN_NAME)) {
            return isValidWSTabColNameChar(c);
         }
      }

      return c == ' ' || c == '$' || c == '-' || c == '&' || c == '@' ||
         c == '+' || c == '\'' || c == ',' || c == '.';
   }

   /**
    * Check if is a valid vs/ws name char.
    */
   private static boolean isValidSheetNameChar(char c) {
      return c != '%' && c != '^' && c != '\\' && c != '\"' && c != '\'' &&
         c != '/' && c != '<';
   }

   /**
    * Check if is a valid vs/ws name char.
    */
   private static boolean isValidWSTabColNameChar(char c) {
      return c == '#' || c == '$' || c == '%' || c == ' ';
   }

   /**
    * Get a brighter color. Handles black color.
    * @param c original color.
    * @return brighter color.
    */
   public static Color brighter(Color c) {
      return ((c.getRGB() & 0xFFFFFF) == 0) ? Color.gray : c.brighter();
   }

   /**
    * Get a darker color.
    * @param c original color.
    * @return darker color.
    */
   public static Color darker(Color c) {
      return c.darker();
   }

   /**
    * Set as server.
    */
   public static void setServer(boolean isServer) {
      if(!Tool.isServer) {
         Tool.isServer = isServer;
      }
   }

   /**
    * Check if the call is made from a servlet.
    */
   public static boolean isServer() {
      return isServer;
   }

   /**
    * Get the top-level root element.
    */
   public static Element findRootElement(Node elem) {
      if(elem == null || (elem instanceof Element)) {
         return (Element) elem;
      }

      NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         Element node = findRootElement(nlist.item(i));

         if(node != null) {
            return node;
         }
      }

      return null;
   }

   /**
    * Free a DOM tree.
    */
   public static void freeNode(Node root) {
      if(root == null) {
         return;
      }

      NodeList list = root.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         Node child = list.item(i);

         if(child == null) {
            continue;
         }

         freeNode(child);
         root.removeChild(child);
      }
   }

   /**
    * Compare two values and return >0, 0, or <0 for greater than, equal
    * to, and less than conditions.
    *
    * @param v1 first value.
    * @param v2 second value.
    */
   public static int compare(Object v1, Object v2) {
      return compare(v1, v2, isCaseSensitive(), true);
   }

   /**
    * Check if two object equals in content.
    */
   public static boolean equalsContent(Object obj, Object obj2) {
      if(obj == null || obj2 == null) {
         return obj == obj2;
      }

      if(!(obj instanceof ContentObject)) {
         return equals(obj, obj2);
      }

      ContentObject cobj = (ContentObject) obj;
      return cobj.equalsContent(obj2);
   }

   public static boolean equalsContent(Object[] arr1, Object[] arr2) {
      if(arr1 == null && arr2 == null) {
         return true;
      }

      if(arr1 == null || arr2 == null || arr1.length != arr2.length) {
         return false;
      }

      for(int i = 0; i < arr1.length; i++) {
         if(!equalsContent(arr1[i], arr2[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Hashes a password with no salt.
    *
    * @param password  the clear-text password.
    * @param algorithm the hash algorithm.
    *
    * @return the hashed password.
    */
   public static HashedPassword hash(String password, String algorithm) {
      return hash(password, algorithm, null, false);
   }

   /**
    * Hashes a password.
    *
    * @param password   the clear-text password to hash.
    * @param algorithm  the hash algorithm.
    * @param salt       the salt to add to the clear text password. The salt is ignore if it is
    *                   {@code null} or the bcrypt algorithm is used.
    * @param appendSalt {@code true} to append the salt to the clear text password;
    *                   {@code false} to prepend the salt to the clear text password.
    *
    * @return the hashed password.
    */
   public static HashedPassword hash(String password, String algorithm, String salt,
                                     boolean appendSalt)
   {
      return PasswordEncryption.newInstance().hash(password, algorithm, salt, appendSalt);
   }

   /**
    * Checks if a given clear text password matches a hashed password.
    *
    * @param hashedPassword the hashed password string.
    * @param clearPassword  the clear text password string.
    * @param algorithm      the hash algorithm.
    * @param salt           the password salt.
    * @param appendSalt     {@code true} to append the salt to the clear text password;
    *                       {@code false} to prepend the salt to the clear text password.
    *
    * @return {@code true} if the passwords match, {@code false} otherwise.
    */
   public static boolean checkHashedPassword(String hashedPassword, String clearPassword,
                                             String algorithm, String salt, boolean appendSalt)
   {
      return checkHashedPassword(
         hashedPassword, clearPassword, algorithm, salt, appendSalt, Tool::encodeAscii85);
   }

   /**
    * Checks if a given clear text password matches a hashed password.
    *
    * @param hashedPassword the hashed password string.
    * @param clearPassword  the clear text password string.
    * @param algorithm      the hash algorithm.
    * @param salt           the password salt.
    * @param appendSalt     {@code true} to append the salt to the clear text password;
    *                       {@code false} to prepend the salt to the clear text password.
    * @param encoder        a function that converts the hashed password bytes into a string.
    *
    * @return {@code true} if the passwords match, {@code false} otherwise.
    */
   public static boolean checkHashedPassword(String hashedPassword, String clearPassword,
                                             String algorithm, String salt, boolean appendSalt,
                                             Function<byte[], String> encoder)
   {
      return PasswordEncryption.newInstance()
         .checkHashedPassword(hashedPassword, clearPassword, algorithm, salt, appendSalt, encoder);
   }

   static String encodeAscii85(byte[] data) {
      return new String(Encoder.encodeAscii85(data));
   }

   /**
    * Create an empty image buffer.
    * @param w buffer width.
    * @param h buffer height.
    * @param transparent true to include transparency info.
    */
   public static Image createImage(int w, int h, boolean transparent) {
      int type = transparent ? BufferedImage.TYPE_INT_ARGB_PRE
         : BufferedImage.TYPE_INT_RGB;
      return new BufferedImage(w, h, type);
   }

   /**
    * Decode an image byte stream to create an image instance.
    */
   public static Image createImage(byte[] buf, int w, int h) {
      Object key = getImageKey(w, h, buf);
      Image img = imgcache.get(key);

      if(img == null) {
         // don't hold too many images
         if(imgcache.size() > 100) {
            imgcache.clear();
         }

         img = createImage0(buf, w, h);
         imgcache.put(key, img);
      }

      return img;
   }

   /**
    * Decode an image byte stream to create an image instance.
    */
   private static Image createImage0(byte[] buf, int w, int h) {
      BufferedImage img = new BufferedImage(w, h,
         BufferedImage.TYPE_4BYTE_ABGR);
      int[] pix = new int[w];
      int si = 0;
      boolean hasAlpha = buf.length >= w * h * 4;

      for(int i = 0; i < h; i++) {
         for(int j = 0; j < w; j++) {
            // order in R,G,B,A..
            int r = buf[si++] & 0xFF;
            int g = buf[si++] & 0xFF;
            int b = buf[si++] & 0xFF;
            int alpha = hasAlpha ? (buf[si++] & 0xFF) : 0xFF;

            // we had saved the information of transparent,
            // here, I donot make use of the alpha information..
            // because if add alpah, I had to modify many files
            // for example, when generator PDF,etc..,But these modification
            // will enhance any qulity of picture..
            // peterx@inetsoftcorp.com  2001.8.29..

            pix[j] = (alpha << 24) | (r << 16) | (g << 8) | b;
         }

         img.setRGB(0, i, w, 1, pix, 0, w);
      }

      return img;
   }

   /**
    * The method for keep hour value compatible with old value.
    * In old mode, we save 0-11 and 13 to 24 and hour value, 24 represemts
    * 12pm. This has been changed to use 0-23 as hour value, so if hour
    * is 24, we change it to 12.
    */
   public static int getCompatibleHour(int hr) {
      return hr == 24 ? 12 : hr;
   }

   /**
    * Check if case sensitive in current report/sree environment.
    * @return <tt>true</tt> if case sensitive, <tt>false</tt> otherwise.
    */
   public static boolean isCaseSensitive() {
      if(!sinited) {
         synchronized(Tool.class) {
            if(!sinited) {
               String cs = SreeEnv.getProperty("string.compare.casesensitive");
               sensitive = "true".equals(cs);
               sinited = true;
            }
         }
      }

      return sensitive;
   }

   public static void invalidateCaseSensitive() {
      sinited = false;
   }

   /**
    * Get the default date format.
    */
   public static SimpleDateFormat getDateFormat() {
      return getDateFormat(null);
   }

   /**
    * Get the date format.
    */
   public static SimpleDateFormat getDateFormat(Locale locale) {
      String prop = SreeEnv.getProperty("format.date");
      String key = locale + ":date:" + prop;
      SimpleDateFormat dateFmt = dateFmts.get(key);

      if(dateFmt == null) {
         if(prop == null || prop.equals("")) {
            prop = DEFAULT_DATE_PATTERN;
         }

         try {
            dateFmt = Tool.createDateFormat(prop, locale);
            dateFmt.format(new Date());
         }
         catch(Exception ex) {
            LOG.warn("Failed to create date format: " + prop, ex);
            dateFmt = Tool.createDateFormat(DEFAULT_DATE_PATTERN, locale);
         }

         dateFmts.put(key, dateFmt);
      }
      else {
         dateFmt = (SimpleDateFormat) dateFmt.clone();
      }

      return dateFmt;
   }

   public static String getDateFormatPattern() {
      String dateFormat = SreeEnv.getProperty("format.date.time");

      if(dateFormat == null) {
         return "YYYY-MM-DD HH:mm:ss";
      }

      dateFormat = dateFormat.replace("yyyy", "YYYY").replace("dd", "DD");

      return dateFormat;
   }

   /**
    * Get the time format.
    */
   public static SimpleDateFormat getTimeFormat(boolean schedule) {
      return getTimeFormat(null, schedule);
   }

   /**
    * Get the time format.
    */
   public static SimpleDateFormat getTimeFormat() {
      return getTimeFormat(null);
   }

   /**
    * Get the time format.
    */
   public static SimpleDateFormat getTimeFormat(Locale locale) {
      return getTimeFormat(locale, false);
   }

   /**
    * Get the time format.
    */
   public static SimpleDateFormat getTimeFormat(Locale locale, boolean schedule) {
      String prop = SreeEnv.getProperty("format.time");
      String key = locale + ":time:" + prop;
      boolean twelveHourSystem = SreeEnv.getBooleanProperty("schedule.time.12hours");

      if(schedule) {
         key += ":schedule:" + twelveHourSystem;
      }

      SimpleDateFormat timeFmt = dateFmts.get(key);

      if(timeFmt == null) {
         if(prop == null || prop.equals("")) {
            prop = DEFAULT_TIME_PATTERN;
         }

         if(twelveHourSystem && prop != null) {
            prop = prop.replace("HH", "hh");
         }

         try {
            timeFmt = Tool.createDateFormat(prop, locale);
            timeFmt.format(new Date());
         }
         catch(Exception ex) {
            LOG.warn("Failed to create time format: " + prop, ex);
            timeFmt = Tool.createDateFormat(DEFAULT_TIME_PATTERN, locale);
         }

         dateFmts.put(key, timeFmt);
      }
      else {
         timeFmt = (SimpleDateFormat) timeFmt.clone();
      }

      return timeFmt;
   }

   /**
    * Synchronizely call dateformat format.
    */
   public static String formatDateTime(long modified) {
      return modified == 0 ? "" : getDateTimeFormat().format(new Date(modified));
   }

   /**
    * Synchronizely call dateformat parse.
    */
   public static long parseDateTimeStr(String modified) throws ParseException {
      return Tool.isEmptyString(modified) ? 0 : getDateTimeFormat().parse(modified).getTime();
   }

   /**
    * Get the date and time format.
    */
   public static SimpleDateFormat getDateTimeFormat() {
      return getDateTimeFormat(null);
   }

   /**
    * Get the date and time format.
    */
   public static SimpleDateFormat getDateTimeFormat(Locale locale) {
      String prop = SreeEnv.getProperty("format.date.time");
      String key = locale + ":datetime:" + prop;
      SimpleDateFormat datetimeFmt = dateFmts.get(key);

      if(datetimeFmt == null) {
         if(prop == null || prop.equals("")) {
            prop = DEFAULT_DATETIME_PATTERN;
         }

         try {
            datetimeFmt = Tool.createDateFormat(prop, locale);
            datetimeFmt.format(new Date());
         }
         catch(Exception ex) {
            LOG.warn("Failed to create date/time format: " + prop, ex);
            datetimeFmt = Tool.createDateFormat(DEFAULT_DATETIME_PATTERN, locale);
         }

         dateFmts.put(key, datetimeFmt);
      }
      else {
         datetimeFmt = (SimpleDateFormat) datetimeFmt.clone();
      }

      return datetimeFmt;
   }

   /**
    * Encode null and array value to embed in HTML.
    */
   public static String encodeParameter(Object obj) {
      if(obj == null) {
         return NULL_PARAMETER_VALUE;
      }

      if(obj instanceof Object[]) {
         Object[] arr = (Object[]) obj;
         StringBuilder str = new StringBuilder("^[");

         for(int i = 0; i < arr.length; i++) {
            if(i > 0) {
               str.append(",");
            }

            str.append(encodeParameter(arr[i]));
         }

         return str.append("]^").toString();
      }
      else if(obj instanceof java.sql.Timestamp) {
         return TIMESTAMP_PARAMETER_PREFIX + formatDateTime((java.sql.Timestamp) obj);
      }
      else if(obj instanceof java.sql.Time) {
         return TIME_PARAMETER_PREFIX + formatTime((java.sql.Time) obj);
      }
      else if(obj instanceof Date) {
         return DATE_PARAMETER_PREFIX + formatDate((Date) obj);
      }

      String str = obj.toString();
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < str.length(); i++) {
         if(str.charAt(i) == '\r') {
            continue;
         }
         else if(str.charAt(i) == '"') {
            buf.append("^quot^");
            continue;
         }
         else if(str.charAt(i) == ',') {
            buf.append("\\,");
            continue;
         }

         buf.append(str.charAt(i));
      }

      return encodeNL(buf.toString());
   }

   /**
    * get prefix of date parameter.
    * @param date date string
    * @return null: not contains date prefix,
    * others, return DATE_PARAMETER_PREFIX | TIME_PARAMETER_PREFIX | TIMESTAMP_PARAMETER_PREFIX
    */
   public static String getDateParamPrefix(String date) {
      if(date == null) {
         return null;
      }

      if(date.startsWith(DATE_PARAMETER_PREFIX)) {
         return DATE_PARAMETER_PREFIX;
      }
      else if(date.startsWith(TIME_PARAMETER_PREFIX)) {
         return TIME_PARAMETER_PREFIX;
      }
      else if(date.startsWith(TIMESTAMP_PARAMETER_PREFIX)) {
         return TIMESTAMP_PARAMETER_PREFIX;
      }
      else {
         return null;
      }
   }

   /**
    * remove date parameter prefix of object.toString
    * @param data object
    * @return if data.toString contains date parameter prefix,
    *    return string that has been remove date parameter prefix
    * otherwise, return object self.
    */
   public static String removeDateParamPrefix(String data) {
      if(data == null) {
         return null;
      }

      String prefix = getDateParamPrefix(data);

      if(prefix != null) {
         data = data.substring(prefix.length());
      }

      return data;
   }

   /**
    * Decode the parameter value encoded using encodeParameter.
    */
   public static Object decodeParameter(String str) {
      if(str == null || str.equals(NULL_PARAMETER_VALUE)) {
         return null;
      }

      String prefix = getDateParamPrefix(str);

      if(prefix != null) {
         String dateStr = str.substring(prefix.length());

         // empty date string treated as null
         if(dateStr.isEmpty()) {
            return null;
         }

         try {
            if(str.startsWith(DATE_PARAMETER_PREFIX)) {
               Date d = parseDate(dateStr);

               return new java.sql.Date(d.getTime());
            }
            else if(str.startsWith(TIME_PARAMETER_PREFIX)) {
               Date d = parseTime(dateStr);

               return new java.sql.Time(d.getTime());
            }
            else{
               Date d = parseDateTime(dateStr);

               return new java.sql.Timestamp(d.getTime());
            }
         }
         catch(Throwable e) {
            LOG.error("Failed to decode date value: " + str, e);
         }

         return dateStr;
      }

      if(isParameterArray(str)) {
         str = str.substring(2, str.length() - 2);
         return splitParameterValue(str);
      }

      return decodeCommas(restoreQuota(decodeNL(str)));
   }

   public static String[] splitParameterValue(String value) {
      List<String> output = new ArrayList<>();

      if(value == null || value.isEmpty()) {
         return output.toArray(new String[0]);
      }

      int startIndex = 0;
      String quoted = "";
      int length = value.length();
      StringBuilder current = new StringBuilder();

      for(int index = 0; index < length; index++) {
         char ch = value.charAt(index);

         // support both single quote and double quote
         if(index == startIndex && (ch == '"' || ch == '\'')) {
            quoted = Character.toString(ch);
            continue;
         }

         if(ch == '\\') {
            index++;

            if(index < length) {
               current.append(value.charAt(index));
            }
            else {
               current.append("\\");
               output.add(current.toString());
            }

            continue;
         }

         if(!quoted.isEmpty() && ch == quoted.charAt(0)) {
            index++;

            if(index < length) {
               String beforeComma = "";

               // lookahead to comma
               while(index < length && value.charAt(index) != ',') {
                  beforeComma += value.charAt(index);
                  index++;
               }

               // malformed input, just append it to the value
               if(!beforeComma.trim().isEmpty()) {
                  current.append(beforeComma);
               }

               output.add(current.toString().trim());
               startIndex = index + 1;
               quoted = "";
               current.setLength(0);

               // skip space until quote
               for(int k = startIndex; k < length; k++) {
                  if(value.charAt(k) != ' ') {
                     startIndex = k;
                     index = k - 1;
                     break;
                  }
               }
            }
            else {
               output.add(current.toString().trim());
            }

            continue;
         }

         if(quoted.isEmpty() && ch == ',') {
            output.add(current.toString().trim());
            startIndex = index + 1;
            quoted = "";
            current.setLength(0);
            continue;
         }

         // skip leading space
         if(quoted.isEmpty() && ch == ' ' && index == startIndex) {
            startIndex++;
         }
         else {
            current.append(ch);
         }

         if(index == length - 1) {
            output.add(current.toString().trim());
         }
      }

      return output.toArray(new String[output.size()]);
   }

   /**
    * Check if is parameter array.
    */
   public static boolean isParameterArray(String str) {
      return str != null && str.startsWith("^[") && str.endsWith("]^");
   }

   /**
    * Replace "&quot;" with ".
    */
   private static String restoreQuota(String param) {
      int index = param.indexOf("^quot^");

      if(index < 0) {
         return param;
      }

      return param.substring(0, index) + '"' + restoreQuota(param.substring(index + 6));
   }

   public static String encodeCommas(String str) {
      str = Tool.replaceAll(str, ",", "\\,");

      return str;
   }

   public static String decodeCommas(String str) {
      str = Tool.replaceAll(str, "\\,", ",");

      return str;
   }

   /**
    * Change newline to \\n. Existing \\n is escaped as \\\\n.
    */
   public static String encodeNL(String str) {
      str = Tool.replaceAll(str, "\\", "\\\\");
      str = Tool.replaceAll(str, "\n", "\\n");

      return str;
   }

   /**
    * Change \\n to newline.
    */
   public static String decodeNL(String str) {
      if(str == null) {
         return null;
      }

      StringBuilder buf = new StringBuilder();
      int bslash = 0; // number of consecutive backslash

      for(int i = 0; i < str.length(); i++) {
         if(str.charAt(i) == '\\') {
            bslash++;
         }
         else if(str.charAt(i) == 'n' && bslash == 1) {
            buf.append("\n");
            bslash = 0;
         }
         else {
            for(int k = 0; k < bslash; k += 2) {
               buf.append("\\");
            }

            buf.append(str.charAt(i));
            bslash = 0;
         }
      }

      for(int k = 0; k < bslash; k += 2) {
         buf.append("\\");
      }

      return buf.toString();
   }

   /**
    * Create a texture paint.
    * @param t type, 0 - 6: / \ cross /more \more /less \less.
    * @param s space, 0 - packed, 1 sparse.
    * @param f force, 0 - thin, 1 med.
    */
   public static Paint createTexture(int t, int s, int f, Color color) {
      Dimension size;
      int w = (f == 0) ? 1 : 2;
      int sz = (s == 0) ? 4 : 6;

      if(t == 3 || t == 5) {
         size = new Dimension(sz, 2 * sz);
      }
      else if(t == 4 || t == 6) {
         size = new Dimension(2 * sz, sz);
      }
      else {
         size = new Dimension(sz, sz);
      }

      BufferedImage img = new BufferedImage(size.width, size.height,
                                            BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D g = (Graphics2D) img.getGraphics();

      g.setColor(Color.white);
      g.fillRect(0, 0, size.width, size.height);
      g.setStroke(new BasicStroke(w));
      g.setColor(color);

      switch(t) {
      case 0:
      case 3:
      case 5:
         g.drawLine(0, size.height - 1, size.width - 1, 0);
         break;
      case 2:
         g.drawLine(0, size.height - 1, size.width - 1, 0);
         // drop through
      case 1:
      case 4:
      case 6:
         g.drawLine(0, 0, size.width - 1, size.height - 1);
         break;
      }

      g.dispose();
      return new TexturePaint(img, new Rectangle2D.Float(0, 0, size.width,
                                                         size.height));
   }

   /**
    * Check if the path valid.
    */
   public static boolean isBracketPaired(String path) {
      boolean leftBracketFound = false;

      for(int i = 0; i < path.length(); i++) {
         if(path.charAt(i) == '{') {
            if(leftBracketFound) {
               return false;
            }
            else {
               leftBracketFound = true;
            }
         }
         else if(path.charAt(i) == '}') {
            if(leftBracketFound) {
               leftBracketFound = false;
            }
            else {
               return false;
            }
         }
      }

      return !leftBracketFound;
   }

   /**
    * Determines if an exception was caused by the client breaking the
    * connection and can be safely ignored.
    *
    * @param e the exception to test.
    * @param ignoreIO <tt>true</tt> to ignore all exceptions with a cause that
    *                 is an instance of IOException.
    *
    * @return <tt>true</tt> if the exception can be ignored.
    */
   public static boolean isBrokenPipeException(Throwable e, boolean ignoreIO) {
      boolean result = false;

      if(e.getCause() != null && e.getCause() instanceof IOException) {
         if(ignoreIO) {
            result = true;
         }
         else {
            String message = e.getCause().getMessage();

            for(String msg : BROKEN_PIPE_MSGS) {
               if(msg.equals(message)) {
                  result = true;
                  break;
               }
            }

            StackTraceElement[] trace = e.getCause().getStackTrace();

            for(StackTraceElement element : trace) {
               if(element.toString().contains("java.nio.channels.SocketChannel.write")) {
                  result = true;
                  break;
               }
            }
         }
      }

      return result;
   }

   /**
    * Convert an object to a byte array.
    * @param obj the specified object to be converted.
    * @return byte array.
    */
   public static byte[] convertObject(DataSerializable obj) {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(baos);
         obj.writeData(dos);
         return baos.toByteArray();
      }
      catch(IOException ex) {
         LOG.error("Failed to serialize an object: " + obj, ex);
      }

      return new byte[0];
   }

   /**
    * Gets the remote address of a request. If the request has been forwarded by
    * the cluster proxy, the address of the originating client is returned.
    *
    * @param request the HTTP request object.
    *
    * @return the remote IP address.
    */
   public static String getRemoteAddr(HttpServletRequest request) {
      return request.getHeader("remote_ip") == null ?
         request.getRemoteAddr() : request.getHeader("remote_ip");
   }

   /**
    * Gets the remote host of a request. If the request has been forwarded by
    * the cluster proxy, the host of the originating client is returned.
    *
    * @param request the HTTP request object.
    *
    * @return the remote host name.
    */
   public static String getRemoteHost(HttpServletRequest request) {
      return request.getHeader("remote_host") == null ?
         request.getRemoteHost() : request.getHeader("remote_host");
   }

   /**
    * Get the host name of this host.
    */
   public static String getHost() {
      String name = SreeEnv.getProperty("local.host.name");

      if(name != null) {
         //@by jasons, use manually set host name for machines with multiple
         //network interfaces
         return name;
      }

      try {
         InetAddress addr = getLocalIP();
         name = addr.getHostName();
      }
      catch(Exception ex) {
         // ignore it
      }

      if((name != null && name.length() > 0 && !name.startsWith("localhost")) ||
         !OperatingSystem.isUnix())
      {
         return name;
      }

      return getUnixLocalIpOrHost(false);
   }

   /**
    * Get the ip address of this host.
    */
   public static String getIP() {
      String phyip = SreeEnv.getEarlyLoadedProperty("local.ip.addr");

      if(phyip != null) {
         // @by jasons, use manually set IP address for machines with multiple
         // network interfaces
         synchronized(validatedAddresses) {
            // Cache IP address validation so that an invalid IP address doesn't
            // spam the log and maybe a slight performance benefit.
            if(validatedAddresses.containsKey(phyip)) {
               if(validatedAddresses.get(phyip)) {
                  return phyip;
               }
            }
            else {
               try {
                  // Validate that the configured IP address is actually
                  // assigned to this machine. This prevents configuration
                  // errors and spoofing license restrictions.
                  InetAddress address = InetAddress.getByName(phyip);

                  if(NetworkInterface.getByInetAddress(address) != null) {
                     validatedAddresses.put(phyip, true);
                     return phyip;
                  }
                  else {
                     validatedAddresses.put(phyip, false);
                     LOG.warn(
                        "Specified local IP address (local.ip.addr) is not " +
                        "valid for this machine: {}", phyip);
                  }
               }
               catch(Exception ex) {
                  validatedAddresses.put(phyip, false);
                  LOG.warn("Failed to validate local IP address (local.ip.addr): " +
                     phyip, ex);
               }
            }
         }

         phyip = null;
      }

      try {
         InetAddress addr = getLocalIP();
         phyip = addr.getHostAddress();
      }
      catch(Exception ignore) {
      }

      if(phyip != null && phyip.length() > 0 && !phyip.equals("127.0.0.1") ||
         !OperatingSystem.isUnix())
      {
         return phyip;
      }

      phyip = getUnixLocalIpOrHost(true);

      //bug1407894598896
      //if the os is unix/mac and the network is unavailable
      //the return ip is null, try to avoid it
      if(phyip == null && OperatingSystem.isUnix()) {
         try {
            phyip = InetAddress.getLocalHost().getHostAddress();
         }
         catch(Exception e) {
            //do nothing
         }
      }

      return phyip;
   }

   // @by: ChrisSpagnoli feature1366221225905 2014-9-30
   // Support RMI invocation to "localhost", as alternative to the local IP
   /**
    * Returns the ip address of this host,
    * overridden by the user setting the "rmi.localhost.ip" parameter in
    * the sree.properties file, in which case it returns "localhost").
    */
   public static String getRmiIP() {
      final String rmilocalhostip = SreeEnv.getProperty("rmi.localhost.ip");

      if(rmilocalhostip != null && rmilocalhostip.length() > 0 &&
         rmilocalhostip.trim().equalsIgnoreCase("true"))
      {
         return "localhost";
      }

      return Tool.getIP();
   }

   /**
    * Gets the best guess for the correct IP address of this machine.
    *
    * @return the local IP address.
    *
    * @throws IOException if an I/O error occurs.
    */
   public static InetAddress getLocalIP() throws IOException {
      synchronized(LOCAL_IP) {
         if(LOCAL_IP.get() == null) {
            LOCAL_IP.set(getLocalIP0());
         }
      }

      return LOCAL_IP.get();
   }

   /**
    * Gets the best guess for the correct IP address of this machine.
    *
    * @return the local IP address.
    *
    * @throws IOException if an I/O error occurs.
    */
   private static InetAddress getLocalIP0() throws IOException {
      InetAddress[] addresses = getIPAddresses(true);
      InetAddress address;

      if(addresses.length == 0) {
         // Fall back to the JVM default, this is non-deterministic on machines
         // with multiple network interfaces.
         address = InetAddress.getLocalHost();
      }
      else {
         address = addresses[0];
      }

      return address;
   }

   /**
    * If srcIP equals to "127.0.0.1" or "0:0:0:0:0:0:0:1", return real ip
    */
   public static String getRealIP(String srcIP) {
      String ip = srcIP;

      if("127.0.0.1".equals(srcIP) || "0:0:0:0:0:0:0:1".equals(srcIP) ||
         "localhost".equals(srcIP))
      {
         try {
            ip = getLocalIP().getHostAddress();
         }
         catch(Exception ignore) {
         }
      }

      return ip;
   }

   /**
    * Get local ip address or host name in Unix.
    * @param isIp true to get ip, false to get host name.
    */
   public static String getUnixLocalIpOrHost(boolean isIp) {
      String[] ips = getUnixLocalIpOrHost(isIp, false);
      return ips.length > 0 ? ips[0] : null;
   }

   /**
    * Get local ip address or host name in Unix.
    * @param isIp true to get ip, false to get host name.
    * @param all to get all the ips and host names or not.
    */
   public static String[] getUnixLocalIpOrHost(boolean isIp, boolean all) {
      String[] result;

      synchronized(IP_HOSTS) {
         if(IP_HOSTS.get() == null) {
            InetAddress[] addresses = getIPAddresses(false);
            result = new String[addresses.length];

            for(int i = 0; i < result.length; i++) {
               if(isIp) {
                  result[i] = addresses[i].getHostAddress();
               }
               else {
                  result[i] = addresses[i].getHostName();
               }
            }

            IP_HOSTS.set(result);
         }
         else if(all || IP_HOSTS.get().length == 0) {
            result = IP_HOSTS.get();
         }
         else {
            result = new String[] { IP_HOSTS.get()[0] };
         }
      }

      return result;
   }

   /**
    * Gets all available IP addresses on this machine.
    *
    * @param filterVm to filter out software network interfaces created for virtual
    *                 machine hosts.
    *
    * @return the IP addresses, sorted lexographically.
    */
   private static InetAddress[] getIPAddresses(boolean filterVm) {
      Map<String, InetAddress> allAddresses = new TreeMap<>();

      try {
         // Try to find a network interface that is up, not a loop back, and not
         // a virtual interface for a VM host. A network interface matching
         // these criteria is the best match for the network interface that is
         // bound to the external IP address of the machine.
         Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

         while(ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();

            if(iface.isUp() && !iface.isLoopback() && !iface.isPointToPoint() &&
               !iface.isVirtual())
            {
               if(filterVm) {
                  String name = iface.getName().toLowerCase();

                  if(name.contains("virtualbox") || name.contains("vmware")) {
                     continue;
                  }

                  // windows 7, the interface name is eth* and the display name
                  // contains the VMWare or VirtualBox description
                  name = iface.getDisplayName();

                  if(name != null) {
                     name = name.toLowerCase();

                     if(name.contains("virtualbox") || name.contains("vmware") ||
                        name.contains("virtual"))
                     {
                        continue;
                     }
                  }
               }

               Enumeration<InetAddress> addresses = iface.getInetAddresses();

               while(addresses.hasMoreElements()) {
                  InetAddress address = addresses.nextElement();

                  // our code assumes a IPv4 address in many locations, so for
                  // the time being, only match v4 addresses
                  if(address instanceof Inet4Address) {
                     allAddresses.put(address.getHostAddress(), address);
                  }
               }
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to list network interfaces", e);
      }

      return allAddresses.values().toArray(new InetAddress[0]);
   }

   /**
    * Convert html symbol to string.
    */
   public static String convertHTMLSymbol(String str) {
      if(str == null) {
         return null;
      }

      int idx = str.indexOf("&#");

      if(idx < 0) {
         return str;
      }

      StringBuilder rs = new StringBuilder();

      while(idx >= 0) {
         rs.append(str, 0, idx);
         str = str.substring(idx);
         int idx1 = str.indexOf("&#", 2);
         int idx2 = str.indexOf(';');

         if(idx2 < 0) {
            break;
         }

         // no ";" between two "&#"
         if(idx1 > 0 && idx1 < idx2) {
            rs.append(str, 0, idx1);
            str = str.substring(idx1);
            idx = str.indexOf("&#");
            continue;
         }

         String numStr = str.substring(2, idx2);

         try {
            rs.append((char) Integer.parseInt(numStr));
         }
         catch(NumberFormatException e) {
            rs.append("&#").append(numStr).append(";");
         }

         str = str.substring(idx2 + 1);
         idx = str.indexOf("&#");
      }

      return rs.append(str).toString();
   }

   /**
    * Check the file path is valid.
    * For veracode security, see http://cwe.mitre.org/data/definitions/73.html
    */
   public static boolean isFilePathValid(String path) {
      if(path == null || path.trim().length() == 0) {
         return false;
      }

      if(path.startsWith("http:") || path.startsWith("https:")) {
         return false;
      }

      return path.indexOf('?') <= 0 && path.indexOf('*') <= 0;
   }

   /**
    * Check the log is valid.
    * For veracode security, see http://cwe.mitre.org/data/definitions/117.html
    */
   public static boolean isLogValid(String log) {
      if(log == null || "".equals(log)) {
         return false;
      }

      return log.indexOf('\n') < 0 && log.indexOf('\r') < 0 &&
         !log.contains("%0d") && !log.contains("%0a");
   }

   /**
    * Find out if the array of strings contain string of second parameter.
    * @param strs - the array of string to look from.
    * @param str - the string trying to find.
    * @return true if it is found.
    */
   public static boolean contains(String[] strs, String str) {
      return contains(strs, str, true);
   }

   /**
    * Find out if the array of strings contain string of second parameter.
    * @param strs - the array of string to look from.
    * @param str - the string trying to find.
    * @param strict - <tt>true</tt> if case sensitive, <tt>false</tt> otherwise.
    * @return true if it is found.
    */
   public static boolean contains(String[] strs, String str, boolean strict) {
      return contains(strs, str, strict, false);
   }

   /**
    * Find out if the array of IdentityIDs contain IdentityID of second parameter.
    *
    * @param ids - the array of string to look from.
    * @param id  - the string trying to find.
    *
    * @return true if it is found.
    */
   public static boolean contains(IdentityID[] ids, IdentityID id) {
      return Arrays.stream(ids).map(IdentityID::convertToKey).anyMatch(i -> i.equals(id.convertToKey()));
   }

   /**
    * Find out if the array of strings contain string of second parameter.
    * @param strs - the array of string to look from.
    * @param str - the string trying to find.
    * @param strict - <tt>true</tt> if case sensitive, <tt>false</tt> otherwise.
    * @param supportNull - <tt>true</tt> if support null value, <tt>false</tt> otherwise.
    * @return true if it is found.
    */
   public static boolean contains(String[] strs, String str, boolean strict, boolean supportNull) {
      return contains(strs, str, strict, supportNull, sensitive);
   }

   public static boolean contains(String[] strs, String str, boolean strict, boolean supportNull,
                                  boolean sensitive)
   {
      if(strs == null || strs.length == 0 || str == null && !supportNull) {
         return false;
      }

      for(String str1 : strs) {
         if(Tool.equals(str1, str, sensitive)) {
            return true;
         }
      }

      return false;
   }

   // html encoding mapping
   private static final String[][] encoding = {
      {"&nbsp;", "&amp;", "&lt;", "&gt;", "&#39;", "&quot;"},
      {" ", "&", "<", ">", "'", "\""}
   };

   // encoding mapping for html attributes' value
   private static final String[][] encodingXMLAttr = {
      {"&amp;", "&lt;", "&gt;", "&quot;"}, {"&", "<", ">", "\""} };

   // encoding mapping for javascript escape characters
   private static final String[][] encodingJS = {
      {"\\\\", "\\'", "\\\""}, {"\\", "'", "\""} };

   // escape CDEnd ]]> in CDATA section
   private static final String[][] encodingCDATA = {
      {"]]", "]] "}, {"\0x0", "\\##@BUILD_IN_00xx00##//"}};
   private static final String[][] decodingCDATA = {
      {"]] ", "]]"}, {"\\##BUILD_IN_00xx00##//", "\0x0"}};

   private static final String[] BROKEN_PIPE_MSGS = {
      //standard IO
      "Broken pipe",
      // NIO
      "An established connection was aborted by the software in your host " +
      "machine",
      "An existing connection was forcibly closed by the remote host"
   };

   static class FontSizeMapper {
      FontSizeMapper() {
         String prop = SreeEnv.getProperty("html.ratio.font");

         if(prop != null) {
            fontratio = Double.parseDouble(prop);
         }

         prop = SreeEnv.getProperty("html.ratio.bigFont");

         if(prop != null) {
            lfontratio = Double.parseDouble(prop);
         }

         calculateFontFactors();
      }

      /**
       * Calculate the coefficient and exponent used to scale fonts.
       */
      private void calculateFontFactors() {
         if(lfontratio == fontratio) {
            fontexp = fontcoeff = 1.0;
            return;
         }

         // Use the least squares method to determine the power law equation
         // (y=A*x^B) that best fits the font scaling. The data points used in the
         // calculation are (1,1), (7,7*fontratio), (14,14*lfontratio). For a
         // description of the least squares method see
         // http://mathworld.wolfram.com/LeastSquaresFittingPowerLaw.html.
         //
         // The variable fontcoeff corresponds to the A in the equation above and
         // fontexp corresponds to B.
         //
         // In the formulas below, E is sigma (the summation operator), n is the
         // number of elements (will always be 3), i is the index of the point, and
         // Xi and Yi are the x and y values at point i:
         //
         //           n                        n            n
         //     n * (E   (ln(Xi) * ln(Yi))) - E   ln(Xi) * E   ln(Yi)
         //           i=1                      i=1          i=1
         // b = -----------------------------------------------------
         //                   n               n
         //              n * E   ln(Xi)^2 - (E   ln(Xi))^2
         //                   i=1             i=1
         //
         //
         //      n                n
         //     E   ln(Yi) - b * E   ln(Xi)
         //      i=1              i=1
         // a = ---------------------------
         //                  n
         //
         // A = e^a and B = b
         //
         // The first step is to loop through all the points calculating 4 sums:
         // logxlogy (log of the current x times the log of the current y)
         // logx     (log of the current x)
         // logy     (log of the current y)
         // logxsq   (log of the current y, squared)
         //
         // So, using these sums and n is 3.0, the above equations can be simplified
         // to:
         //
         //     3.0 * logxlogy - logx * logy
         // b = ----------------------------
         //         3.0 * logx - logx^2
         //
         //
         //     logy - b * logx
         // a = ---------------
         //           3.0
         //
         // Finally, using the sums, B is calculated. Then, from B and the sums, a
         // is calculated. A is then calculated by finding e (Euler's number) to
         // the power of a (e^a).
         //
         // Now fonts can be scaled by:
         // scaledSize = fontcoeff * size^fontexp

         double[][] pts = {
            { 14, fontratio * 14.0 },
            { 16, fontratio * 16.0 },
            { 48, lfontratio * 48.0 }
         };

         double logxlogy = 0.0;
         double logx = 0.0;
         double logy = 0.0;
         double logxsq = 0.0;

         for(int i = 0; i < 3; i++) {
            double lx = Math.log(pts[i][0]);
            double ly = Math.log(pts[i][1]);

            logxlogy += lx * ly;
            logx += lx;
            logy += ly;
            logxsq += Math.pow(lx, 2.0);
         }

         fontexp = (3.0 * logxlogy - logx * logy) /
            (3.0 * logxsq - Math.pow(logx, 2.0));
         double a = (logy - fontexp * logx) / 3.0;
         fontcoeff = Math.exp(a);
      }

      /**
       * Get adjusted font size.
       */
      public float getFontSize(double size, boolean logical) {
         if(logical || lfontratio == fontratio || size < 14) {
            return roundFloat(fontratio * size);
         }

         return roundFloat(fontcoeff * Math.pow(size, fontexp));
      }

      private float roundFloat(double old) {
         return (float) (((int) (old * 100)) / 100.0);
      }

      private double fontexp = 1.0;
      private double fontcoeff = 1.0;
      // middle-size font scaling ratio in HTML vs. Java
      private double fontratio = 0.9;
      // large-size font scaling ratio in HTML vs. Java
      private double lfontratio = 0.75;
   }

   /**
    * Normalize an object so same value with different types can be compared.
    */
   @SuppressWarnings("deprecation")
   public static Object normalize(Object obj) {
      if(obj instanceof Number) {
         if(!(obj instanceof Double)) {
            obj = ((Number) obj).doubleValue();
         }
      }
      else if(obj instanceof Timestamp) {
         obj = new Date(((Timestamp) obj).getTime());
      }
      // truncate time components for sql date
      else if(obj instanceof java.sql.Date) {
         java.sql.Date date = (java.sql.Date) obj;
         obj = new java.sql.Date(date.getYear(), date.getMonth(), date.getDate());
      }

      return obj;
   }

   /**
    * Extract the groups from an regular expression.
    */
   public static List<String> extractRegex(String pattern, String value) {
      Pattern p1 = Pattern.compile(pattern);
      Matcher matcher1 = p1.matcher(value);
      List<String> values = new ArrayList<>();

      while(matcher1.find()) {
         for(int i = 0; i < matcher1.groupCount(); i++) {
            String str = matcher1.group(i + 1);

            if(str != null && str.length() > 0) {
               values.add(matcher1.group(i + 1));
            }
         }
      }

      return values;
   }

   /**
    * Convert user specified class name.
    */
   public static String convertUserClassName(String cls) {
      return cls == null || Pattern.matches(ALL_PATTERN, cls) ? cls : null;
   }

   /**
    * Convert user specifield file name.
    */
   public static String convertUserFileName(String fname) {
      return fname == null || Pattern.matches(ALL_PATTERN, fname) ? fname : null;
   }

   /**
    * Convert user specifield log message.
    */
   public static String convertUserLogInfo(String lmsg) {
      return lmsg == null || Pattern.matches(ALL_PATTERN, lmsg) ? lmsg : null;
   }

   /**
    * Convert user specifield parameter.
    */
   public static String convertUserParameter(String parameter) {
      return parameter == null || Pattern.matches(ALL_PATTERN, parameter) ?
         parameter : null;
   }

   /**
    * Convert user specifield byte.
    */
   public static byte[] convertUserByte(byte[] buf) {
      return buf == null || buf.length > 0 ? buf : null;
   }

   /**
    * Generates a unique name with the specified maximum length.
    *
    * @param name      the original name.
    * @param maxLength the maximum length.
    *
    * @return the unique name.
    */
   public static String getUniqueName(String name, int maxLength) {
      String uniqueName;

      if(name.length() <= maxLength) {
         uniqueName = name;
      }
      else {
         try {
            MessageDigest digest = MessageDigest.getInstance("SHA");
            byte[] data = digest.digest(name.getBytes());
            StringBuilder buffer = new StringBuilder();

            for(byte n : data) {
               buffer.append(String.format("%02X", ((int) n) & 0xff));
            }

            int remaining = maxLength - buffer.length();

            if(remaining > 0) {
               uniqueName = name.substring(0, remaining) + buffer;
            }
            else {
               uniqueName = buffer.toString();
            }
         }
         catch(Exception exc) {
            throw new RuntimeException("Failed to generate unique name", exc);
         }
      }

      return uniqueName;
   }

   /**
    * Combine byte arrays into one.
    * @param arrays The byte arrays to concat.
    * @return A new byte array of length n1 + n2 + ... + nn, combining all
    * arguments.
    */
   public static byte[] combine(byte[]... arrays) {
      int totalLength = 0;

      if(arrays.length == 1) {
         return arrays[0];
      }

      for(byte[] array : arrays) {
         if(array != null) {
            totalLength += array.length;
         }
      }

      byte[] combined = new byte[totalLength];
      int pos = 0;

      for(byte[] array : arrays) {
         if(array != null) {
            System.arraycopy(array, 0, combined, pos, array.length);
            pos += array.length;
         }
      }

      return combined;
   }

   public static String getHelpBaseURL() {
      String uri = SreeEnv.getProperty("help.url");

      if(uri == null || uri.equals("")) {
         uri = "https://www.inetsoft.com/docs/stylebi/index.html";
      }

      return uri;
   }

   /**
    * Used in conjunction with writeUTF to provide a safe read
    * of long string values.
    * @param input the inputstream to read from
    * @return The full string value reconstructed from stream
    */
   public static String readUTF(DataInputStream input) throws IOException {
      int strlen = input.readInt();

      if(strlen == -1) {
         return null;
      }

      StringBuilder sbuf = new StringBuilder(input.readUTF());

      while(sbuf.length() != strlen) {
         sbuf.append(input.readUTF());
      }

      return sbuf.toString();
   }

   /**
    * Java's DataOutputStream writeUTF can only handle strings of length 64k
    * To avoid unnecessary writes, this method is used when the
    * UTFDataFormatException is thrown to write 64k chunks to the output
    * stream.
    * @param output the outputstream to write to
    * @param str the string to write out.
    */
   public static void writeUTF(DataOutputStream output, String str)
         throws IOException
   {
      if(str == null) {
         output.writeInt(-1);
         return;
      }
      else {
         output.writeInt(str.length());
      }

      try {
         output.writeUTF(str);
      }
      // handle chunking for abnormal cases
      catch (UTFDataFormatException ufe) {
         int c;
         int bytelen = 0;
         int startChunk = 0;
         int endChunk = 0;

         // Check ranges of characters to obtain the proper byte length of
         // string.  Then write the string in chunks as the limit is reached.
         for (int i = 0; i < str.length(); i++) {
            c = str.charAt(i);
            endChunk++;

            if ((c >= 0x0001) && (c <= 0x007F)) {
               bytelen++;
            } else if (c > 0x07FF) {
               bytelen += 3;
            } else {
               bytelen += 2;
            }

            // When byte limit is reached, write it out and reset
            // Force the last character to be read again.
            if(bytelen > 65535) {
               i--;
               endChunk--;
               output.writeUTF(str.substring(startChunk, endChunk));
               startChunk = endChunk;
               bytelen = 0;
            }
         }

         // write the last chunk
         output.writeUTF(str.substring(startChunk, endChunk));
      }
   }

   /**
    * Gets the number of CPU cores available to this JVM process.  Uses the
    * util/AffinitySupport class to invoke (via JNA) the appropriate
    * windows/linux native C library function to get the number of CPU cores
    * available to this JVM process.
    *
    * @return the number of cores.
    */
   public static int getAvailableCPUCores() {
      int result = -1;

      if(!Platform.isMac()) {
         try {
            AffinitySupport affinitySupport = AffinitySupport.FACTORY.getInstance();
            final int[] cpuCores = affinitySupport.getAffinity();
            result = cpuCores.length;
         }
         catch(Throwable e) {
            //for safety, catch all throwable
            LOG.debug("Failed to get affinity processors, using the " +
                         "total available processors for: " + e.getMessage(), e);
         }
      }

      if(result < 1) {
         // @by jasonshobe #78, if this fails, use the total number of cores,
         // affinity will not be supported on this platform
         result = Runtime.getRuntime().availableProcessors();
      }

      return result;
   }

   /**
    * Create an output stream to compress the output.
    */
   public static OutputStream createCompressOutputStream(OutputStream out)
      throws IOException
   {
      try {
         return new LZ4BlockOutputStream(out);
      }
      // LZ4 may not be supported
      catch(Exception ex) {
         return new SnappyFramedOutputStream(out);
      }
   }

   /**
    * Create an input stream to compress the input.
    */
   public static InputStream createUncompressInputStream(InputStream inp)
      throws IOException
   {
      if(!inp.markSupported()) {
         inp = new BufferedInputStream(inp);
      }

      inp.mark(100);
      byte[] buf = new byte[8];
      IOUtils.readFully(inp, buf);
      inp.reset();

      String header = new String(buf, 0, buf.length, StandardCharsets.UTF_8);
      boolean lz4 = header.equals("LZ4Block");

      if(lz4) {
         return new LZ4BlockInputStream(inp);
      }
      else {
         return new SnappyFramedInputStream(inp);
      }
   }

   /**
    * Get the viewsheet css bg color to hex string starts with # for html usage
    */
   public static String getVSCSSBgColorHexString() {
      // CSSDictionary.getDictionary() is for viewsheet ONLY
      Color bg = CSSDictionary.getDictionary().getBackground(
         new CSSParameter(CSSConstants.VIEWSHEET, null, null, null));

      if(bg == null) {
         //default color is white
         return "#ffffff";
      }

      String bgHex = String.format("%06x", bg.getRGB());

      if(bgHex.length() > 6) {
         bgHex = bgHex.substring(bgHex.length() - 6);
      }

      return "#" + bgHex;
   }

   /**
    * Convert Color object to string, like #ffcc00.
    */
   public static String toString(Color color) {
      if(color == null) {
         return null;
      }

      return String.format("#%06x", color.getRGB() & 0xffffff);
   }

   /**
    * Serialize object into bytes.
    */
   public static byte[] serialize(Object value) throws IOException {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream oout = new ObjectOutputStream(bout);

      oout.writeObject(value);
      oout.close();

      return bout.toByteArray();
   }
   // !!! can't have a deserialize() method since ObjectInputStream uses
   // the classloader of the calling object. This would cause problem
   // when it's used in a plugin (e.g. spark)

   /**
    * Converts an enumeration to a stream.
    *
    * @param e the enumeration to convert.
    *
    * @param <T> the type of element in the enumeration.
    *
    * @return a stream projection of the enumeration.
    */
   public static <T> Stream<T> toStream(Enumeration<T> e) {
      return toStream(e, false);
   }

   /**
    * Converts an enumeration to a stream.
    *
    * @param e        the enumeration to convert.
    * @param parallel <tt>true</tt> to create a parallel stream; <tt>false</tt> to create
    *                 a sequential stream.
    *
    * @param <T> the type of element in the enumeration.
    *
    * @return a stream projection of the enumeration.
    */
   public static <T> Stream<T> toStream(Enumeration<T> e, boolean parallel) {
      return StreamSupport.stream(
         Spliterators.spliteratorUnknownSize(
            new Iterator<T>() {
               public T next() {
                  return e.nextElement();
               }
               public boolean hasNext() {
                  return e.hasMoreElements();
               }
            },
            Spliterator.IMMUTABLE), parallel);
   }

   public static Map<String, String[]> parseQueryString(String query) {
      Map<String, String[]> parameters = new HashMap<>();

      if(query != null && !query.isEmpty()) {
         String[] pairs;

         if(query.charAt(0) == '?') {
            pairs = query.substring(1).split("&");
         }
         else {
            pairs = query.split("&");
         }

         for(String pair : pairs) {
            int index = pair.indexOf('=');
            String name;
            String value;

            try {
               if(index < 0) {
                  name = URLDecoder.decode(pair, "UTF-8");
                  value = "";
               }
               else {
                  name = URLDecoder.decode(pair.substring(0, index), "UTF-8");
                  value = URLDecoder.decode(pair.substring(index + 1), "UTF-8");
               }
            }
            catch(UnsupportedEncodingException e) {
               // shouldn't happen, UTF-8 is supported on all JVMs
               throw new RuntimeException("UTF-8 encoding is not supported", e);
            }

            String[] existing = parameters.get(name);

            if(existing == null) {
               parameters.put(name, new String[] { value });
            }
            else {
               String[] appended = new String[existing.length + 1];
               System.arraycopy(existing, 0, appended, 0, existing.length);
               appended[existing.length] = value;
               parameters.put(name, appended);
            }
         }
      }

      return parameters;
   }

   public static void dumpAllThreads() {
      ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
      Arrays.sort(threads, Comparator.comparing(ThreadInfo::getThreadName));

      for(ThreadInfo info : threads) {
         System.err.print(getThreadInfoStr(info));
      }
   }

   // ThreadInfo.toString() with larger MAX_FRAMES value
   private static String getThreadInfoStr(ThreadInfo info) {
      StringBuilder sb = new StringBuilder("\"" + info.getThreadName() + "\" " +
                                              info.getThreadState());

      if(info.getLockName() != null) {
         sb.append(" on ").append(info.getLockName());
      }

      if(info.getLockOwnerName() != null) {
         sb.append(" owned by \"").append(info.getLockOwnerName()).append("\" Id=")
            .append(info.getLockOwnerId());
      }

      if(info.isSuspended()) {
         sb.append(" (suspended)");
      }

      sb.append('\n');
      final int MAX_FRAMES = 30;
      int i = 0;
      StackTraceElement[] stackTrace = info.getStackTrace();

      for (; i < stackTrace.length && i < MAX_FRAMES; i++) {
         StackTraceElement ste = stackTrace[i];
         sb.append("\tat ").append(ste.toString());
         sb.append('\n');
         if (i == 0 && info.getLockInfo() != null) {
            Thread.State ts = info.getThreadState();
            switch (ts) {
            case BLOCKED:
               sb.append("\t-  blocked on ").append(info.getLockInfo());
               sb.append('\n');
               break;
            case WAITING:
            case TIMED_WAITING:
               sb.append("\t-  waiting on ").append(info.getLockInfo());
               sb.append('\n');
               break;
            default:
            }
         }

         MonitorInfo[] lockedMonitors = info.getLockedMonitors();
         for (MonitorInfo mi : lockedMonitors) {
            if (mi.getLockedStackDepth() == i) {
               sb.append("\t-  locked ").append(mi);
               sb.append('\n');
            }
         }
      }
      if (i < stackTrace.length) {
         sb.append("\t...");
         sb.append('\n');
      }

      LockInfo[] locks = info.getLockedSynchronizers();
      if (locks.length > 0) {
         sb.append("\n\tNumber of locked synchronizers = ").append(locks.length);
         sb.append('\n');
         for (LockInfo li : locks) {
            sb.append("\t- ").append(li);
            sb.append('\n');
         }
      }
      sb.append('\n');
      return sb.toString();
   }

   public static int getFirstDayOfWeek() {
      String firstDayProperty = Tool.firstDayProperty.get();
      Locale locale = ThreadContext.getLocale();
      String key = firstDayProperty + locale;

      return firstDayOfWeeks.computeIfAbsent(key, k -> {
         Integer firstDay = null;

         if(firstDayProperty != null) {
            switch(firstDayProperty.toLowerCase()) {
            case "sunday":
               firstDay = Calendar.SUNDAY;
               break;
            case "monday":
               firstDay = Calendar.MONDAY;
               break;
            case "tuesday":
               firstDay = Calendar.TUESDAY;
               break;
            case "wednesday":
               firstDay = Calendar.WEDNESDAY;
               break;
            case "thursday":
               firstDay = Calendar.THURSDAY;
               break;
            case "friday":
               firstDay = Calendar.FRIDAY;
               break;
            case "saturday":
               firstDay = Calendar.SATURDAY;
               break;
            }
         }

         if(firstDay == null) {
            Calendar c = GregorianCalendar.getInstance(locale);
            firstDay = c.getFirstDayOfWeek();
         }

         return firstDay;
      });
   }

   public static String getWeekStart() {
      String weekStartProperty = SreeEnv.getProperty("week.start");

      if(weekStartProperty == null) {
         return null;
      }

      String[] validDays = new String[]{ "sunday", "monday", "tuesday",
                                         "wednesday", "thursday", "friday",
                                         "saturday" };
      weekStartProperty = weekStartProperty.toLowerCase();

      boolean valid = Arrays.asList(validDays).contains(weekStartProperty);
      return valid ? weekStartProperty : null;
   }

   /**
    * <p>Returns a default value if the object passed is {@code null}.</p>
    *
    * <pre>
    * Tool.defaultIfNull(null, null)      = null
    * Tool.defaultIfNull(null, "")        = ""
    * Tool.defaultIfNull(null, "zz")      = "zz"
    * Tool.defaultIfNull("abc", *)        = "abc"
    * Tool.defaultIfNull(Boolean.TRUE, *) = Boolean.TRUE
    * </pre>
    *
    * @param <T> the type of the object
    * @param object  the {@code Object} to test, may be {@code null}
    * @param defaultValue  the default value to return, may be {@code null}
    * @return {@code object} if it is not {@code null}, defaultValue otherwise
    */
   public static <T> T defaultIfNull(final T object, final T defaultValue) {
      return object != null ? object : defaultValue;
   }

   /**
    * Encodes illegal characters in LDAP search filter
    */
   public static String encodeForLDAP(String input) {
      if(input == null) {
         return null;
      }

      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < input.length(); i++) {
         char c = input.charAt(i);

         switch(c) {
         case '\\':
            sb.append("\\5c");
            break;
         case '*':
            sb.append("\\2a");
            break;
         case '(':
            sb.append("\\28");
            break;
         case ')':
            sb.append("\\29");
            break;
         case '\0':
            sb.append("\\00");
            break;
         default:
            sb.append(c);
         }
      }

      return sb.toString();
   }

   public static <T> Future<T> makeAsync(Callable<T> fn) {
      AtomicReference<GroupedThread> reference = new AtomicReference<>();
      CompletableFuture<T> future = new CompletableFuture<T>() {
         @Override
         public boolean cancel(boolean mayInterruptIfRunning) {
            if(super.cancel(mayInterruptIfRunning)) {
               if(reference.get() != null) {
                  reference.get().cancel();
               }

               return true;
            }

            return false;
         }
      };
      GroupedThread thread = new GroupedThread(() -> {
         try {
            future.complete(fn.call());
         }
         catch(Exception e) {
            if(!future.isCancelled()) { // cancel completes exceptionally already
               future.completeExceptionally(e);
            }
         }
      });
      reference.set(thread);
      thread.start();
      return future;
   }

   public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
      Map<Object, Boolean> seen = new ConcurrentHashMap<>();
      return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
   }

   public static Object transform(Object oval, String type, Format format, boolean strict) throws Exception {
      if(format != null && ("".equals(oval) || "N/A".equals(oval) || "n/a".equals(oval))) {
         return null;
      }

      Object nval = getData(type, oval);

      if(oval != null && !"".equals(oval) && format != null && XSchema.isDateType(type)) {
         if(!(oval instanceof Date)) {
            try {
               nval = format.parseObject(oval.toString());
            }
            catch(Exception ex) {
               if(strict) {
                  throw ex;
               }

               DateTime dt = DateTime.parse(oval.toString());
               nval = dt.toDate();
            }
         }

         if(nval instanceof Date) {
            if(oval instanceof Date) {
               ((Date) nval).setTime(((Date) oval).getTime());
            }

            switch(type) {
            case XSchema.DATE:
               nval = new java.sql.Date(((Date) nval).getTime());
               break;
            case XSchema.TIME_INSTANT:
               nval = new Timestamp(((Date) nval).getTime());
               break;
            case XSchema.TIME:
               nval = new java.sql.Time(((Date) nval).getTime());
               break;
            }
         }
      }

      if(oval != null && nval == null && XSchema.isNumericType(type)) {
         String str = oval.toString();
         NumberFormat fmt = NumberFormats.INSTANCE.number;

         if(str.length() > 0) {
            if(str.endsWith("%")) {
               fmt = NumberFormats.INSTANCE.percent;
            }
            else if(str.charAt(0) == '$') {
               str = str.substring(1);
            }
            else if(str.startsWith("-$")) {
               str = "-" + str.substring(2);
            }
            else if(str.startsWith("($") && str.endsWith(")")) {
               str = "-" + str.substring(2, str.length() - 1);
            }

            nval = fmt.parse(str);
         }
      }

      return nval;
   }

   public static <T> T waitFor(long delay, long interval, Callable<T> producer,
                               Predicate<T> condition) throws Exception
   {
      if(delay > 0) {
         Thread.sleep(delay);
      }


      T value = producer.call();

      while(!condition.test(value)) {
         Thread.sleep(interval);
         value = producer.call();
      }

      return value;
   }

   public static void copy(ReadableByteChannel source, WritableByteChannel target)
      throws IOException
   {
      if(source instanceof FileChannel) {
         FileChannel file = (FileChannel) source;
         file.transferTo(file.position(), file.size() - file.position(), target);
      }
      else if(target instanceof FileChannel) {
         FileChannel file = (FileChannel) target;
         file.transferFrom(source, file.position(), Long.MAX_VALUE);
      }
      else {
         ByteBuffer buffer = ByteBuffer.allocate(8192);

         while(source.read(buffer) != -1) {
            XSwapUtil.flip(buffer);

            while(buffer.hasRemaining()) {
               target.write(buffer);
            }

            buffer.clear();
         }
      }
   }

   public static void copy(ReadableByteChannel source, WritableByteChannel target, long length)
      throws IOException
   {
      if(source instanceof FileChannel) {
         FileChannel file = (FileChannel) source;
         file.transferTo(file.position(), length, target);
      }
      else if(target instanceof FileChannel) {
         FileChannel file = (FileChannel) target;
         file.transferFrom(source, file.position(), length);
      }
      else {
         int c = (int) Math.min(length, 8192);
         ByteBuffer buffer = ByteBuffer.allocate(c);
         long bytesWritten = 0;

         while(bytesWritten < length) {
            buffer.limit((int) Math.min((length - bytesWritten), 8192));
            int read = source.read(buffer);

            if(read <= 0) {
               break;
            }

            XSwapUtil.flip(buffer);
            int written = target.write(buffer);
            bytesWritten += written;

            if(written != read) {
               break;
            }

            buffer.clear();
         }
      }
   }

   public static BigDecimal convertToBigDecimal(Object number) {
      if(number == null) {
         return null;
      }

      if(number instanceof BigDecimal) {
         return (BigDecimal) number;
      }
      else if(number instanceof Integer || number instanceof Long) {
         return new BigDecimal(((Number) number).longValue());
      }
      else if(number instanceof Double) {
         return BigDecimal.valueOf(((Number) number).doubleValue());
      }
      else {
         throw new IllegalArgumentException("Unsupported number type: " + number.getClass().getName());
      }
   }

   public static void closeQuietly(AutoCloseable closeable) {
      try {
         if(closeable != null) {
            closeable.close();
         }
      }
      catch(Exception ignore) {
      }
   }

   /**
    * Use StringBuilder instead of + to concatenate strings, to avoid creating too many new objects,
    * improve performance, and reduce memory overhead.
    */
   public static String createPathString(String... arr) {
      StringBuilder builder = new StringBuilder();

      for(int i = 0; i < arr.length; i++) {
         if(StringUtils.isEmpty(arr[i])) {
            continue;
         }

         if(!builder.isEmpty()) {
            builder.append("/");
         }

         builder.append(arr[i]);
      }

      return builder.toString();
   }

   public static String getTableStyleLabel(String name) {
      int index = name.lastIndexOf(LibManager.SEPARATOR);

      if(index < 0) {
         return name;
      }
      else {
         return name.replace(LibManager.SEPARATOR, "/");
      }
   }

   public static String buildString(Object... strs) {
      return buildString(50, strs);
   }

   public static String buildString(int capacity, Object... strs) {
      if(strs == null || strs.length == 0) {
         return "";
      }

      StringBuilder builder = new StringBuilder(capacity);

      for(int i = 0; i < strs.length; i++) {
         builder.append(strs[i]);
      }

      return builder.toString();
   }

   /**
    * Determine whether current environment uses cloud service to store secrets.
    */
   public static boolean isCloudSecrets() {
      return !SecretsType.LOCAL.getName().equals(InetsoftConfig.getInstance().getSecrets().getType());
   }

   /**
    * Try to decrypt a password using local encryption.
    */
   public static String decryptPasswordWithLocal(String encryptedPassword) {
      if(Tool.isEmptyString(encryptedPassword)) {
         return encryptedPassword;
      }

      try {
         return new JcePasswordEncryption().decryptPassword(encryptedPassword);
      }
      catch(Exception e1) {
         try {
            return new FipsPasswordEncryption().decryptPassword(encryptedPassword);
         }
         catch(Exception e2) {
            return encryptedPassword;
         }
      }
   }

   public static boolean isRunningInDocker() {
      try {
         try(Stream<String> lines = Files.lines(Paths.get("/proc/1/cgroup"))) {
            return lines.anyMatch(l -> l.contains("/docker/"));
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   public static <T> void mergeSort(T[] a, Comparator<? super T> c) {
      T[] aux = a.clone();
      mergeSort(aux, a, 0, a.length, 0, c);
   }

   public static <T> void mergeSort(List<T> list, Comparator<? super T> c) {
      Object[] a = list.toArray();
      Object[] aux = a.clone();
      mergeSort(aux, a, 0, a.length, 0, c);
      ListIterator<T> i = list.listIterator();

      for(Object e : a) {
         i.next();
         i.set((T) e);
      }
   }

   private static void mergeSort(Object[] src, Object[] dest, int low, int high, int off,
                                 Comparator c)
   {
      final int INSERTIONSORT_THRESHOLD = 7;
      int length = high - low;

      // Insertion sort on smallest arrays
      if(length < INSERTIONSORT_THRESHOLD) {
         for(int i = low; i < high; i++) {
            for(int j = i; j > low && c.compare(dest[j - 1], dest[j]) > 0; j--) {
               swap(dest, j, j - 1);
            }
         }
         return;
      }

      // Recursively sort halves of dest into src
      int destLow = low;
      int destHigh = high;
      low += off;
      high += off;
      int mid = (low + high) >>> 1;
      mergeSort(dest, src, low, mid, -off, c);
      mergeSort(dest, src, mid, high, -off, c);

      // If list is already sorted, just copy from src to dest.  This is an
      // optimization that results in faster sorts for nearly ordered lists.
      if(c.compare(src[mid - 1], src[mid]) <= 0) {
         System.arraycopy(src, low, dest, destLow, length);
         return;
      }

      // Merge sorted halves (now in src) into dest
      for(int i = destLow, p = low, q = mid; i < destHigh; i++) {
         if(q >= high || p < mid && c.compare(src[p], src[q]) <= 0) {
            dest[i] = src[p++];
         }
         else {
            dest[i] = src[q++];
         }
      }
   }


   private static void swap(Object[] x, int a, int b) {
      Object t = x[a];
      x[a] = x[b];
      x[b] = t;
   }

   private static final int[][] dateLevel = {
      {DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_OF_YEAR_PART},
      {DateRangeRef.QUARTER_INTERVAL, DateRangeRef.MONTH_OF_YEAR_PART},
      {DateRangeRef.MONTH_INTERVAL, DateRangeRef.DAY_OF_MONTH_PART},
      {DateRangeRef.WEEK_INTERVAL, DateRangeRef.DAY_OF_WEEK_PART},
      {DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_OF_DAY_PART},
      {DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL},
      {DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL},
      {DateRangeRef.SECOND_INTERVAL, DateRangeRef.SECOND_INTERVAL},
      {DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART},
      {DateRangeRef.MONTH_OF_YEAR_PART, DateRangeRef.DAY_OF_MONTH_PART},
      {DateRangeRef.WEEK_OF_YEAR_PART, DateRangeRef.DAY_OF_WEEK_PART},
      {DateRangeRef.DAY_OF_MONTH_PART, DateRangeRef.HOUR_OF_DAY_PART},
      {DateRangeRef.DAY_OF_WEEK_PART, DateRangeRef.HOUR_OF_DAY_PART},
      {DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART},
      {DateRangeRef.MINUTE_OF_HOUR_PART, DateRangeRef.SECOND_OF_MINUTE_PART},
      {DateRangeRef.SECOND_OF_MINUTE_PART, DateRangeRef.SECOND_OF_MINUTE_PART}};

   private static final String ALL_PATTERN = "[\\s\\S]*";
   private static final int[] pwdMask = { 106, 65, 115, 111, 78, 82, 85, 76,
      101, 115 };
   private static Frame root = null;
   private static volatile boolean sinited;
   private static boolean sensitive;

   private static final Color[] colors = {new Color(126, 194, 230),
                                          new Color(152, 230, 115), new Color(230, 195, 126),
                                          new Color(230, 126, 126), new Color(230, 126, 228),
                                          new Color(127, 126, 230), new Color(126, 230, 161),
                                          new Color(230, 226, 126), new Color(230, 126, 159),
                                          new Color(196, 126, 230), new Color(126, 229, 230),
                                          new Color(195, 230, 126), new Color(153, 153, 0),
                                          Color.orange, Color.cyan, Color.gray, Color.pink, Color.darkGray,
                                          new Color(150, 0, 150), new Color(25, 80, 150), new Color(150, 80, 25),
                                          new Color(126, 0, 0), new Color(0, 126, 0), new Color(0, 0, 126),
                                          new Color(240, 80, 240), new Color(80, 240, 80), new Color(50, 100, 150),
                                          new Color(100, 150, 200), new Color(150, 200, 250),
                                          new Color(150, 100, 50), new Color(200, 150, 100),
                                          new Color(250, 200, 150), new Color(60, 75, 60), new Color(90, 75, 45),
                                          new Color(128, 0, 64), new Color(64, 0, 128), new Color(70, 20, 38),
                                          new Color(100, 100, 30) };

   private static volatile String buildNumber;
   private static boolean isServer;
   private static final Map<Object, Image> imgcache = new HashMap<>(); // optimization

   private static final Map<String, Boolean> validatedAddresses = new HashMap<>();

   private static final Paint[] brush; // initialize brushes
   private static final AtomicReference<String[]> IP_HOSTS = new AtomicReference<>(null);
   private static final String prefix = "_#start_";
   private static final String suffix = "_#end_";
   private static final AtomicReference<InetAddress> LOCAL_IP = new AtomicReference<>(null);
   private static final Map<Object, Integer> firstDayOfWeeks = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(Tool.class);
   private static final Pattern NEW_BYTE_ENCODING_PATTERN = Pattern.compile("~_([0-9a-f]{1,4})_~");
   private static final Pattern OLD_BYTE_ENCODING_PATTERN = Pattern.compile("\\[([0-9a-f]{2,4})]");

   public static final String NULL_PARAMETER_VALUE = "^null^";

   public static final String DATE_PARAMETER_PREFIX = "^DATE^";
   public static final String TIME_PARAMETER_PREFIX = "^TIME^";
   public static final String TIMESTAMP_PARAMETER_PREFIX = "^TIMESTAMP^";

   private static final Map<String,SimpleDateFormat> dateFmts = new ConcurrentHashMap<>();
   private static SreeEnv.Value firstDayProperty = new SreeEnv.Value("week.start", 30000);

   enum NumberFormats {
      INSTANCE;

      final NumberFormat number = new ExtendedDecimalFormat("#,##0.##");
      final NumberFormat percent = NumberFormat.getPercentInstance();
   }

   private static final SecureRandom secureRandom;

   static {
      brush = new Paint[20];
      int idx = 0;

      // (/ \ cross /more \more /less \less) * (packed sparse) * (thin med)
      for(int f = 0; f < 2; f++) { // force
         for(int s = 0; s < 2; s++) { // space
            for(int t = 0; t < 5; t++) { // type
               brush[idx++] = createTexture(t, s, f, Color.black);
            }
         }
      }

      SecureRandom random = null;

      if(!SystemUtils.IS_OS_WINDOWS) {
         try {
            // Use /dev/urandom for both generateSeed() and nextBytes(). The blocking implementation
            // will use /dev/random, which will block if the entropy pool is empty and this can be
            // extremely slow. It is theoretically possible to exploit the use of /dev/urandom, but
            // there are no known exploits of this and it is much better than no source of entropy
            // like SHA1PRNG. We could consider having this controlled by a property if a customer
            // really wants it, but it can block up to a minute when getting 16 bytes for a salt.
            random = SecureRandom.getInstance("NativePRNGNonBlocking");
         }
         catch(NoSuchAlgorithmException ignore) {
         }
      }

      if(random == null) {
         try {
            random = SecureRandom.getInstance("SHA1PRNG");
         }
         catch(NoSuchAlgorithmException ignore) {
         }
      }

      if(random == null) {
         Random seedRandom = new Random(System.currentTimeMillis());
         byte[] seed = new byte[16];
         seedRandom.nextBytes(seed);
         random = new SecureRandom(seed);
      }

      secureRandom = random;

      Package pkg = Tool.class.getClassLoader().getDefinedPackage("inetsoft.util");

      if(pkg != null) {
         buildNumber = pkg.getImplementationVersion();
      }

      if(buildNumber == null) {
         buildNumber = "Development Build";
      }
   }
}
