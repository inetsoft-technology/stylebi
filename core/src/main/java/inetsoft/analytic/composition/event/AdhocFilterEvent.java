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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.report.StyleFont;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Adhoc filter event.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AdhocFilterEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public AdhocFilterEvent() {
      super();
   }

   /**
    * Constructor.
    * @param entry asset entry.
    */
   public AdhocFilterEvent(AssetEntry entry) {
      this();
      this.entry = entry;
   }

   /**
    * Constructor.
    * @param entry asset entry.
    */
   public AdhocFilterEvent(AssetEntry entry, Point position) {
      this();
      put("x", "" + position.x);
      put("y", "" + position.y);
      this.entry = entry;
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Adhoc Filter");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return new String[0];
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      int x = Integer.parseInt((String) get("x"));
      int y = Integer.parseInt((String) get("y"));
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || entry == null) {
         return;
      }

      String target = (String) get("targetObj");
      Worksheet ws = vs.getBaseWorksheet();

      if(target == null || ws == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) vs.getAssembly(target);

      if(assembly.isEmbedded()) {
         vs = assembly.getViewsheet();
         ws = vs.getBaseWorksheet();
      }

      String dtype = entry.getProperty("dtype");
      String btableName = entry.getProperty("assembly");
      int sourceType = -1;
      String str = entry.getProperty("sourceType");

      if(str != null) {
         try {
            sourceType = Integer.parseInt(str);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      String columnName = entry.getProperty("attribute");
      CalculateRef ref = null;

      if(sourceType != XSourceInfo.VS_ASSEMBLY) {
         ref = getCalcRef(vs, btableName, columnName);
      }

      if(ref != null && !ref.isBaseOnDetail()) {
         String msg = Catalog.getCatalog().getString(
            "adhocfilter.calcfield.notsupport");
         MessageCommand msgcmd = new MessageCommand(msg, MessageCommand.ERROR);
         command.addCommand(msgcmd);
         return;
      }

      if(dtype == null) {
         dtype = sourceType == XSourceInfo.VS_ASSEMBLY ? XSchema.DOUBLE :
            findColumnType(vs, ws, btableName, columnName, command);

         if(dtype == null) {
            return;
         }

         entry.setProperty("dtype", dtype);
      }

      int type = AbstractSheet.SELECTION_LIST_ASSET;

      if(XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)) {
         type = AbstractSheet.TIME_SLIDER_ASSET;
      }

      String name = AssetUtil.getNextName(vs, type);
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(DragVSAssetEvent.createColumnRef(entry));

      VSAssembly vsassembly = DragVSAssetEvent.createSelectionVSAssembly(
         vs, type, dtype, name, btableName, columns, null);

      if(vsassembly instanceof TimeSliderVSAssembly) {
         ((TimeSliderVSAssembly) vsassembly).setSourceType(sourceType);
      }

      if(vsassembly == null) {
         return;
      }

      VSAssemblyInfo info = vsassembly.getVSAssemblyInfo();
      info.setPixelOffset(new Point(x, y));
      vsassembly.initDefaultFormat();
      ((SelectionVSAssemblyInfo) info).setCreatedByAdhoc(true);

      String p = (String) get("offsetPixel");
      Point offsetPixel = new Point(Integer.parseInt(p.split("X")[0]),
         Integer.parseInt(p.split("X")[1]));

      changeAssemblyToAdhocFilter(vsassembly, assembly, offsetPixel);

      String format = (String) get("format");
      String format_spec = (String) get("format_spec");

      if(format != null) {
         FormatInfo finfo = info.getFormatInfo();
         TableDataPath dpath = VSAssemblyInfo.OBJECTPATH;

         if(type == AbstractSheet.SELECTION_LIST_ASSET) {
            dpath = new TableDataPath(-1, TableDataPath.DETAIL);
         }

         VSCompositeFormat cfmt = finfo.getFormat(dpath);

         if(cfmt != null) {
            cfmt.getUserDefinedFormat().setFormatValue(format);
            cfmt.getUserDefinedFormat().setFormatExtentValue(format_spec);
            finfo.setFormat(dpath, cfmt);
         }
      }

      // Bug #2972, when adhoc filter, it will not apply layout and apply scale,
      // so should apply layout size manually for new adding vsassembly.
      if(get("container") != null) {
         VSAssemblyInfo cinfo = (VSAssemblyInfo) get("container");

         if(cinfo.getLayoutSize() != null) {
            applyLayoutSizeInContainer(vs, vsassembly, cinfo);
            AbstractLayout.applyScaleFont(vsassembly, vs.getRScaleFont());
         }
      }

      vs.addAssembly(vsassembly);
      VSEventUtil.addDeleteVSObject(rvs, this, vsassembly, getLinkURI(),
                                    command);
   }

   /**
    * Apply layout size for assembly in container.
    */
   private void applyLayoutSizeInContainer(Viewsheet vs, VSAssembly assembly,
                                           VSAssemblyInfo cinfo)
   {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Dimension layoutsize = info.getLayoutSize();
      Dimension clayoutsize = cinfo.getLayoutSize();

      if(layoutsize == null) {
         Dimension size = vs.getPixelSize(info);
         Dimension csize = vs.getPixelSize(cinfo);
         double radioH = (double) clayoutsize.height / (double) csize.height;
         layoutsize = new Dimension(clayoutsize.width,
                                    (int) (size.height * radioH));
         // @damianwysocki, Bug #9543
         // Removed grid, deprecated event, should be removed
//         Dimension gridsize = (Dimension) info.getSize().clone();
//
//         if(gridsize.height <= 1) {
//            if(info instanceof SelectionBaseVSAssemblyInfo) {
//               gridsize.height =
//                  ((SelectionBaseVSAssemblyInfo) info).getListHeight() + 1;
//            }
//            else if(info instanceof TimeSliderVSAssemblyInfo) {
//               gridsize.height =
//                  ((TimeSliderVSAssemblyInfo) info).getListHeight() + 1;
//            }
//
//            layoutsize = vs.getPixelSize(info.getPosition(), gridsize);
//         }

         Dimension nlayoutsize =
            new Dimension(clayoutsize.width, layoutsize.height);
         info.setLayoutSize(nlayoutsize);
      }
   }

   /**
    * Get calc ref by the column name.
    */
   private CalculateRef getCalcRef(Viewsheet vs, String btableName,
      String columnName)
   {
      CalculateRef[] crefs = vs.getCalcFields(btableName);

      if(crefs != null) {
         for(CalculateRef ref: crefs) {
            if(ref.getName().equals(columnName)) {
               return ref;
            }
         }
      }

      return null;
   }

   /**
    * Find column type.
    */
   private String findColumnType(Viewsheet vs, Worksheet ws, String btableName,
                                 String columnName, AssetCommand command)
   {
      for(Assembly assembly : ws.getAssemblies()) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly tassembly = (TableAssembly) assembly;

         if(tassembly.getName().equals(btableName)) {
            String dtype = getColumnDataType(tassembly , columnName);

            if(dtype != null) {
               return dtype;
            }
         }
      }

      CalculateRef ref = getCalcRef(vs, btableName, columnName);

      if(ref != null && ref.isBaseOnDetail()) {
         return ref.getDataType();
      }

      return null;
   }

   /**
    * Get binding column data type.
    */
   private String getColumnDataType(TableAssembly assembly, String cname) {
      ColumnSelection columns = assembly.getColumnSelection(true);
      boolean iscube = assembly instanceof CubeTableAssembly;

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef cref = (ColumnRef) columns.getAttribute(i);
         String refName = cref.getName();

         if(!iscube && refName.indexOf(".") != -1) {
            refName = refName.substring(refName.lastIndexOf(".") + 1);
         }

         if(refName.equals(cname)) {
            return cref.getDataType();
         }
      }

      return null;
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(entry != null) {
         writer.print("<entry>");
         entry.writeXML(writer);
         writer.print("</entry>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element node = Tool.getChildNodeByTagName(tag, "entry");

      if(node != null) {
         NodeList nodes =  node.getChildNodes();
         entry = AssetEntry.createAssetEntry((Element) nodes.item(0));
      }
   }

   private static void setFormats(VSAssembly moved) {
      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      FormatInfo fmtInfo = info.getFormatInfo();
      VSCompositeFormat titleFormat =
         fmtInfo.getFormat(VSAssemblyInfo.TITLEPATH);
      VSCompositeFormat objFormat =
         fmtInfo.getFormat(VSAssemblyInfo.OBJECTPATH);
      VSCompositeFormat detailformat =
         fmtInfo.getFormat(new TableDataPath(-1, TableDataPath.DETAIL));

      Map<String, Object> prop = new HashMap<>();
      prop.put("_titleFmt", titleFormat.getUserDefinedFormat());
      prop.put("_objectFmt", objFormat.getUserDefinedFormat());
      prop.put("_zindex", info.getZIndex());

      if(detailformat != null) {
         prop.put("_detailUserFmt", detailformat.getUserDefinedFormat());
      }

      if(moved instanceof SelectionListVSAssembly) {
         SelectionListVSAssembly sobj = (SelectionListVSAssembly) moved;
         SelectionList sl = sobj.getSelectionList();

         if(sl != null) {
            SelectionValue[] values = sl.getSelectionValues();
            Map<String, VSCompositeFormat> valuesFmt = new HashMap<>();

            for(SelectionValue val : values) {
               val.setFormat(createSelectionValueFormat(val, valuesFmt));
            }

            prop.put("_valuesFmt", valuesFmt);
         }
      }

      VSFormat adhocFilterFormat = new VSFormat();
      adhocFilterFormat.setBackgroundValue(Color.WHITE.getRGB() + "");

      titleFormat.setUserDefinedFormat(adhocFilterFormat);
      objFormat.setUserDefinedFormat(adhocFilterFormat);

      if(detailformat != null) {
         detailformat.setUserDefinedFormat(adhocFilterFormat);
      }

      ((AbstractSelectionVSAssembly) moved).setAhFilterProperty(prop);
   }

   /**
    * Change the assembly to adhoc filter.
    */
   protected static void changeAssemblyToAdhocFilter(VSAssembly moved,
      VSAssembly target, Point offsetPixel)
   {
      VSAssemblyInfo info = moved.getVSAssemblyInfo();

      if(info instanceof SelectionVSAssemblyInfo) {
         ((SelectionVSAssemblyInfo) info).setAdhocFilter(true);

         if(offsetPixel != null) {
            ((SelectionVSAssemblyInfo) info).setPixelOffset(offsetPixel);
         }
      }

      setFormats(moved);
      info.setZIndex(target.getVSAssemblyInfo().getZIndex() + 1);

      if(moved instanceof TimeSliderVSAssembly) {
         moved.setPixelSize(new Dimension(2 * AssetUtil.defw, 3 * AssetUtil.defh));
      }
      else {
         moved.setPixelSize(new Dimension(2 * AssetUtil.defw, 6 * AssetUtil.defh));
      }
   }

   private static VSCompositeFormat createSelectionValueFormat(
      SelectionValue val, Map<String, VSCompositeFormat> valuesFmt)
   {
      VSCompositeFormat vscfmt = val.getFormat();
      valuesFmt.put(selectionValueToKey(val),
         (VSCompositeFormat) vscfmt.clone());
      vscfmt = new VSCompositeFormat();
      VSFormat vsfmt = new VSFormat();
      vsfmt.setBackgroundValue(Color.WHITE.getRGB() + "");
      vsfmt.setFontValue(new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10));
      vscfmt.setUserDefinedFormat(vsfmt);

      return vscfmt;
   }

   private static String selectionValueToKey(SelectionValue val) {
      return val.getLabel() + "^" + val.getLevel() + "^" +
         val.getValue();
   }

   /**
    * Change the adhoc filter assembly to container child.
    */
   protected static void changeAdhocFilterToAssembly(
      AbstractSelectionVSAssembly moved)
   {
      Map<String, Object> prop = moved.getAhFilterProperty();
      VSAssemblyInfo info = moved.getVSAssemblyInfo();
      moved.getPixelSize().height = AssetUtil.defh;

      if(info instanceof SelectionVSAssemblyInfo) {
         SelectionVSAssemblyInfo sinfo = (SelectionVSAssemblyInfo) info;
         sinfo.setAdhocFilter(false);
         sinfo.setPixelOffset(null);
      }

      if(prop != null) {
         info.getFormat().setUserDefinedFormat(
            (VSFormat) prop.get("_objectFmt"));
         info.getFormatInfo().getFormat(
            VSAssemblyInfo.TITLEPATH).setUserDefinedFormat(
               (VSFormat) prop.get("_titleFmt"));
         info.setZIndex((Integer) prop.get("_zindex"));

         TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
         VSCompositeFormat dfmt = info.getFormatInfo().getFormat(path);

         if(dfmt != null) {
            dfmt.setUserDefinedFormat((VSFormat) prop.get("_detailUserFmt"));
         }

         if(moved instanceof SelectionListVSAssembly) {
            SelectionListVSAssembly sobj = (SelectionListVSAssembly) moved;
            SelectionList sl = sobj.getSelectionList();
            Map<String, VSCompositeFormat> valuesFmt =
               (HashMap<String, VSCompositeFormat>) prop.get("_valuesFmt");

            if(sl != null && valuesFmt != null) {
               SelectionValue[] values = sl.getSelectionValues();

               if(valuesFmt == null) {
                  valuesFmt = new HashMap<>();
               }

               for(SelectionValue val : values) {
                  VSCompositeFormat valueFormat =
                     valuesFmt.get(selectionValueToKey(val));

                  if(valueFormat == null) {
                     valueFormat = createSelectionValueFormat(val, valuesFmt);
                  }

                  val.setFormat(valueFormat);
               }
            }
         }

         moved.setAhFilterProperty(null);
      }
   }

   public AssetEntry getEntry() {
      return entry;
   }

   private AssetEntry entry;
}
