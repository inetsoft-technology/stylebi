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
package inetsoft.uql.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.xml.VersionControlComparators;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * FormatInfo contains composite cell format information. The format
 * information will be applied to format a composite cell assembly.
 * A separate format can be specified for each cell type.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class FormatInfo implements AssetObject {
   /**
    * Constructor.
    */
   public FormatInfo() {
      super();

      fmtmap = new Object2ObjectOpenHashMap<>();
      // we assume object format is never null
      fmtmap.put(VSAssemblyInfo.OBJECTPATH, new VSCompositeFormat());
   }

   /**
    * Apply the changes on the object format to all other formats.
    */
   public void applyFormat(VSCompositeFormat fmt) {
      VSCompositeFormat ofmt = getFormat(VSAssemblyInfo.OBJECTPATH);

      Object[][] props = {
         {"getAlignment", "setAlignment", int.class, 0,
          "isAlignmentDefined"},
         {"getAlignmentValue", "setAlignmentValue", int.class, 0,
          "isAlignmentValueDefined"},
         {"getBackground", "setBackground", Color.class, null,
          "isBackgroundDefined"},
         {"getBackgroundValue", "setBackgroundValue", String.class, null,
          "isBackgroundValueDefined"},
         {"getForeground", "setForeground", Color.class, null,
          "isForegroundDefined"},
         {"getForegroundValue", "setForegroundValue", String.class, null,
          "isForegroundValueDefined"},
         {"getBorders", "setBorders", Insets.class, null, "isBordersDefined"},
         {"getBordersValue", "setBordersValue", Insets.class, null,
          "isBordersValueDefined"},
         {"getBorderColors", "setBorderColors", BorderColors.class, null,
          "isBorderColorsDefined"},
         {"getBorderColorsValue", "setBorderColorsValue", BorderColors.class,
          null, "isBorderColorsValueDefined"},
         {"getFont", "setFont", Font.class, null, "isFontDefined"},
         {"getFontValue", "setFontValue", Font.class, null,
          "isFontValueDefined"},
         {"isWrapping", "setWrapping", boolean.class, Boolean.FALSE,
          "isWrappingDefined"},
         {"getWrappingValue", "setWrappingValue", boolean.class, Boolean.FALSE,
          "isWrappingValueDefined"},
         {"getAlpha", "setAlpha", int.class, 100,
          "isAlphaDefined"},
         {"getAlphaValue", "setAlphaValue", int.class, 100,
          "isAlphaValueDefined"},
         {"getFormat", "setFormat", String.class, null, "isFormatDefined"},
         {"getFormatValue", "setFormatValue", String.class, null,
          "isFormatValueDefined"},
         {"getFormatExtent", "setFormatExtent", String.class, null,
          "isFormatDefined"},
         {"getFormatExtentValue", "setFormatExtentValue", String.class, null,
          "isFormatValueDefined"},
         {"getPresenter", "setPresenter", PresenterRef.class, null,
          "isPresenterDefined"},
         {"getPresenterValue", "setPresenterValue", PresenterRef.class, null,
          "isPresenterValueDefined"}
      };

      Class<VSFormat> cls = VSFormat.class;
      boolean formatChanged = false;
      VSFormat ufmt = fmt.getUserDefinedFormat();
      VSFormat oufmt = ofmt.getUserDefinedFormat();
      VSFormat odfmt = ofmt.getDefaultFormat();

      for(Object[] prop : props) {
         try {
            String getter = (String) prop[0];
            String setter = (String) prop[1];
            String isFuncStr = (String) prop[4];
            Class<?> type = (Class<?>) prop[2];
            Object defaultVal = prop[3];
            Method getfunc = cls.getMethod(getter);
            Object val = getfunc.invoke(ufmt);
            Object oval = getfunc.invoke(oufmt);
            Object odval = getfunc.invoke(odfmt);
            oval = oval == null ? odval : oval;
            Object nvalPropDefined;
            Object ovalPropDefined;
            Method isfunc = cls.getMethod(isFuncStr);
            nvalPropDefined = isfunc.invoke(ufmt);
            ovalPropDefined = isfunc.invoke(oufmt);

            boolean ndefined = (Boolean) nvalPropDefined;
            boolean odefined = (Boolean) ovalPropDefined;

            // handle the properties populate of user defined format
            // with css format
            if((ndefined ^ odefined) || !Tool.equals(val, oval)) {
               Method setfunc = cls.getMethod(setter, type, boolean.class);

               for(TableDataPath path : fmtmap.keySet()) {
                  VSCompositeFormat cellfmt = fmtmap.get(path);

                  if(cellfmt == null) {
                     continue;
                  }
                  
                  boolean objPath = path.equals(VSAssemblyInfo.OBJECTPATH);
                  Object userVal = getfunc.invoke(cellfmt.getUserDefinedFormat());
                  Object defVal = getfunc.invoke(cellfmt.getDefaultFormat());
                  Object cellVal = objPath ?
                     userVal == null ? defVal : userVal :
                     defVal;

                  // don't copy borders, consistent with getFormat
                  boolean set = objPath || !"setBorders".equals(setter) &&
                                           (Tool.equals(cellVal, defaultVal) ||
                                            Tool.equals(cellVal, oval));

                  boolean isFormatExtSetter =
                     "setFormatExtent".equals(setter) ||
                     "setFormatExtentValue".equals(setter);

                  if(isFormatExtSetter && formatChanged) {
                     set = true;
                  }

                  // only set cell format if the property is not explicitly set
                  if(set && objPath) {
                     setfunc.invoke(cellfmt.getUserDefinedFormat(),
                                    val, ndefined);
                  }

                  boolean isFormatSetter = "setFormat".equals(setter) ||
                                           "setFormatValue".equals(setter);

                  if(isFormatSetter && set) {
                     formatChanged = true;
                  }
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to apply formats", ex);
         }
      }

      String cssID = fmt.getCSSFormat().getCSSID();
      String cssCls = fmt.getCSSFormat().getCSSClass();

      for(TableDataPath path : fmtmap.keySet()) {
         VSCompositeFormat cellfmt = fmtmap.get(path);

         if(cellfmt == null) {
            continue;
         }

         boolean objPath = path.equals(VSAssemblyInfo.OBJECTPATH);

         if(objPath) {
            cellfmt.getCSSFormat().setCSSID(cssID);
            cellfmt.getCSSFormat().setCSSClass(cssCls);
         }
         else {
            // copy object's format to cell's default if object's format or
            // its css value has been changed
            copyDefaultFormat(cellfmt.getDefaultFormat(), fmt);

            if(VSAssemblyInfo.TITLEPATH.equals(path)) {
               copyTitleRuntimeFormat(cellfmt.getUserDefinedFormat(), fmt);
            }
         }
      }
   }

   /**
    * Get the format of a table data path.
    * @param tpath the specified table data path.
    * @return the format of the table data path, <tt>null</tt> if not found.
    */
   public VSCompositeFormat getFormat(TableDataPath tpath) {
      return getFormat(tpath, true);
   }

   /**
    * Get the format of a table data path.
    * @param tpath the specified table data path.
    * @param shrink <tt>true</tt> if normal, <tt>false</tt> vscomposite.
    * @return the format of the table data path, <tt>null</tt> if not found.
    */
   public VSCompositeFormat getFormat(TableDataPath tpath, boolean shrink) {
      VSCompositeFormat fmt = fmtmap.get(tpath);

      if(!shrink && !VSAssemblyInfo.OBJECTPATH.equals(tpath)) {
         VSCompositeFormat objfmt = fmtmap.get(VSAssemblyInfo.OBJECTPATH);

         if(objfmt != null) {
            VSFormat deffmt = fmt == null ? new VSFormat() : fmt.getDefaultFormat();
            copyDefaultFormat(deffmt, objfmt);
            fmt = fmt == null ? new VSCompositeFormat() : fmt;
            fmt.setDefaultFormat(deffmt);
         }
      }

      return fmt;
   }

   /**
    * Copy object format setting to vscomposite's other tableDataPath.
    */
   protected void copyDefaultFormat(VSFormat tfmt, VSCompositeFormat sfmt) {
      VSCSSFormat cssfmt = sfmt.getCSSFormat();
      VSFormat userfmt = sfmt.getUserDefinedFormat();
      boolean vsTab = CSSConstants.TAB.equals(cssfmt.getCSSType());

      if(!tfmt.isAlignmentValueDefined() || cssfmt.isAlignmentValueDefined() ||
         userfmt.isAlignmentValueDefined() || userfmt.isAlignmentDefined())
      {
         tfmt.setAlignmentValue(sfmt.getAlignmentValue(), false);
         tfmt.setAlignment(sfmt.getAlignment(), false);
      }

      if(!tfmt.isWrappingValueDefined() || cssfmt.isWrappingValueDefined() ||
         userfmt.isWrappingValueDefined() || userfmt.isWrappingDefined())
      {
         tfmt.setWrappingValue(sfmt.getWrappingValue(), false);
         tfmt.setWrapping(sfmt.isWrapping(), false);
      }

      if(!tfmt.isForegroundValueDefined() ||
         cssfmt.isForegroundValueDefined() ||
         userfmt.isForegroundValueDefined() || userfmt.isForegroundDefined())
      {
         tfmt.setForegroundValue(sfmt.getForegroundValue(), false);
         tfmt.setForeground(sfmt.getForeground(), false);
      }

      if(!tfmt.isFontValueDefined() || cssfmt.isFontValueDefined() ||
         userfmt.isFontValueDefined() || userfmt.isFontDefined())
      {
         tfmt.setFontValue(sfmt.getFontValue(), false);
         Font sfont = sfmt.getFont();

         if(sfont != null) {
            sfont = sfont.deriveFont(sfont.getSize() / sfmt.getRScaleFont());
         }

         tfmt.setFont(sfont, false);
      }

      if(!tfmt.isBackgroundValueDefined() ||
         cssfmt.isBackgroundValueDefined() ||
         userfmt.isBackgroundValueDefined() || userfmt.isBackgroundDefined())
      {
         tfmt.setBackgroundValue(sfmt.getBackgroundValue(), false);
         tfmt.setBackground(sfmt.getBackground(), false);
      }

      if(!vsTab && (!tfmt.isBorderColorsValueDefined() ||
         cssfmt.isBorderColorsValueDefined() ||
         userfmt.isBorderColorsValueDefined() ||
         userfmt.isBorderColorsDefined()))
      {
         tfmt.setBorderColorsValue(sfmt.getBorderColorsValue(), false);
         tfmt.setBorderColors(sfmt.getBorderColors(), false);
      }

      if(!tfmt.isAlphaValueDefined() || cssfmt.isAlphaValueDefined() ||
         userfmt.isAlphaValueDefined() || userfmt.isAlphaDefined())
      {
         tfmt.setAlphaValue(sfmt.getAlphaValue(), false);
         tfmt.setAlpha(sfmt.getAlpha(), false);
      }

      if(!tfmt.isFormatValueDefined() || userfmt.isFormatValueDefined() ||
         userfmt.isFormatDefined())
      {
         tfmt.setFormatValue(sfmt.getFormatValue(), false);
         tfmt.setFormatExtentValue(sfmt.getFormatExtentValue(), false);
         tfmt.setFormat(sfmt.getFormat(), false);
         tfmt.setFormatExtent(sfmt.getFormatExtent(), false);
      }

      if(tfmt.getSpan() == null || userfmt.getSpan() != null) {
         tfmt.setSpan(sfmt.getSpan());
      }

      if(vsTab && (!tfmt.isRoundCornerValueDefined() || cssfmt.isRoundCornerValueDefined() ||
         userfmt.isRoundCornerValueDefined() || userfmt.isRoundCornerDefined()))
      {
         tfmt.setRoundCornerValue(sfmt.getRoundCornerValue(), false);
         tfmt.setRoundCorner(sfmt.getRoundCorner(), false);
      }
   }

   /**
    * Copy object format setting to title runtime format.
    */
   private void copyTitleRuntimeFormat(VSFormat tfmt, VSCompositeFormat sfmt) {
      VSUtil.copyFormat(tfmt, sfmt);
   }

   /**
    * Set the format of a table data path.
    * @param tpath the specified table data path.
    * @param fmt the specified format.
    */
   public void setFormat(TableDataPath tpath, VSCompositeFormat fmt) {
      synchronized(fmtmap) {
         if(fmt == null) {
            fmtmap.remove(tpath);
         }
         else {
            fmt.setFormatInfo(this);
            fmtmap.put(tpath, fmt);
         }
      }
   }

   /**
    * Get all the table data paths.
    * @return all the table data paths which have defined format.
    */
   public TableDataPath[] getPaths() {
      synchronized(fmtmap) {
         return fmtmap.entrySet().stream().filter(e -> e.getValue() != null).map(e -> e.getKey())
            .toArray(TableDataPath[]::new);
      }
   }

   /**
    * Get the map from datapaths to formats.
    */
   public Map<TableDataPath,VSCompositeFormat> getFormatMap() {
      return fmtmap;
   }

   /**
    * Get all formats defined in FormatInfo.
    */
   public Stream<VSCompositeFormat> getFormats() {
      return fmtmap.values().stream().filter(f -> f != null);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<formatInfo class=\"" + getClass().getName()+ "\">");
      writeContents(writer);
      writer.print("</formatInfo>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      synchronized(fmtmap) {
         for(Map.Entry<TableDataPath, VSCompositeFormat> entry
            : VersionControlComparators.sortTableDataPathKeyMap(fmtmap))
         {
            TableDataPath path = entry.getKey();
            VSCompositeFormat fmt = entry.getValue();

            if(fmt != null) {
               writer.print("<aFormat>");
               path.writeXML(writer);
               fmt.writeXML(writer);
               writer.print("</aFormat>");
            }
         }
      }
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public void writeData(DataOutputStream output) throws IOException {
      synchronized(fmtmap) {
         output.writeInt(fmtmap.size());

         for(TableDataPath path : fmtmap.keySet()) {
            VSCompositeFormat fmt = fmtmap.get(path);

            if(fmt != null) {
               path.writeData(output);
               fmt.writeData(output);
            }
         }
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      NodeList anodes = Tool.getChildNodesByTagName(elem, "aFormat");

      for(int i = 0; i < anodes.getLength(); i++) {
         Element anode = (Element) anodes.item(i);
         Element tnode = Tool.getNthChildNode(anode, 0);
         TableDataPath tpath = new TableDataPath();
         Element fnode = Tool.getNthChildNode(anode, 1);
         VSCompositeFormat fmt = new VSCompositeFormat();

         tpath.parseXML(tnode);
         fmt.parseXML(fnode);
         fmt.setFormatInfo(this);
         fmtmap.put(tpath, fmt);
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseContents(elem);
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "FormatInfo: [" + fmtmap + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public FormatInfo clone() {
      try {
         FormatInfo info = (FormatInfo) super.clone();
         info.fmtmap = Tool.deepCloneMap(fmtmap);

         for(Map.Entry<TableDataPath, VSCompositeFormat> entry : info.fmtmap.entrySet()) {
            VSCompositeFormat vsCompositeFormat = entry.getValue();

            if(vsCompositeFormat != null) {
               vsCompositeFormat.setFormatInfo(info);
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone FormatInfo", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof FormatInfo)) {
         return false;
      }

      FormatInfo info = (FormatInfo) obj;
      return fmtmap.equals(info.fmtmap);
   }

   /**
    * Check if this format info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return fmtmap.size() == 0;
   }

   /**
    * Reset the format info.
    */
   public void reset() {
      synchronized(fmtmap) {
         fmtmap.clear();
      }
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      Set<TableDataPath> keys = fmtmap.keySet();

      for(TableDataPath path : keys) {
         if(fmtmap.get(path) != null) {
            fmtmap.get(path).getUserDefinedFormat().resetRuntimeValues();
            fmtmap.get(path).getDefaultFormat().resetRuntimeValues();
         }
      }
   }

   private Map<TableDataPath, VSCompositeFormat> fmtmap;

   private static final Logger LOG = LoggerFactory.getLogger(FormatInfo.class);
}
