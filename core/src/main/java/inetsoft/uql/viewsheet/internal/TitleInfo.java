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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is to store the title information about a vs assembly info.
 *
 * @version 10.4
 * @author InetSoft Technology Corp
 */
public class TitleInfo implements AssetObject {
   /**
    * Constructor.
    */
   public TitleInfo() {
      super();

      this.title = new DynamicValue2();
      this.titleVisible = new DynamicValue2();
      this.titleHeight = new DynamicValue2(AssetUtil.defh + "", XSchema.INTEGER);
   }

   /**
    * Constructor.
    * @param title the title info title value.
    */
   public TitleInfo(String title) {
      super();

      this.title = new DynamicValue2(title, XSchema.STRING);
      this.titleVisible = new DynamicValue2("true", XSchema.BOOLEAN);
      this.titleHeight = new DynamicValue2(AssetUtil.defh + "", XSchema.INTEGER);
   }

   /**
    * Get the run time title value.
    * @return title run time title value.
    */
   public String getTitle(VSCompositeFormat format, Viewsheet vs, String name) {
      if(vs != null && name != null && Tool.localizeTextID(VSUtil.getTextID(vs, name)) != null) {
         return Tool.localizeTextID(VSUtil.getTextID(vs, name));
      }

      Object val = title.getRuntimeValue(true);

      if(val == null) {
         return null;
      }

      Format fmt = format == null ? null :
         TableFormat.getFormat(format.getFormat(), format.getFormatExtent());
      String title0 = XUtil.format(fmt, val);
      return (title0 == null || title0.length() == 0) ? null : title0;
   }

   /**
    * Set the run time title value.
    * @param title the run time title value.
    */
   public void setTitle(String title) {
      this.title.setRValue(title);
   }

   /**
    * Get the design time title value.
    * @return title design time title value.
    */
   public String getTitleValue() {
      return title.getDValue();
   }

    /**
    * Set the design time title value.
    * @param title the design time title value.
    */
   public void setTitleValue(String title) {
      this.title.setDValue(title);
   }

   /**
    * Check whether title is visible in run time.
    * @return true if title is visible, otherwise false.
    */
   public boolean isTitleVisible() {
      return titleVisible.getBooleanValue(false, true);
   }

   /**
    * Set the run time title visible value.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisible(boolean visible) {
      titleVisible.setRValue(visible);
   }

   /**
    * Check whether title is visible in design time.
    * @return true if title is visible, otherwise false.
    */
   public boolean getTitleVisibleValue() {
      return titleVisible.getBooleanValue(true, true);
   }

    /**
    * Set the design time title visible value.
    * @param visible true if title is visible, otherwise false.
    */
   public void setTitleVisibleValue(String visible) {
      titleVisible.setDValue(visible);
   }

   /**
    * Get the run time title height value.
    * @return run time title height value.
    */
   public int getTitleHeight() {
      return titleHeight.getIntValue(false, getTitleHeightValue());
   }

   /**
    * Set the run time title height value.
    * @param height the run time title height value.
    */
   public void setTitleHeight(int height) {
      height = Math.max(0, height);
      titleHeight.setRValue(height);
   }

   /**
    * Get the design time title height value.
    * @return design time title height value.
    */
   public int getTitleHeightValue() {
      return titleHeight.getIntValue(true, AssetUtil.defh);
   }

   /**
    * Set the design time title height value.
    * @param height the design time title height value.
    */
   public void setTitleHeightValue(int height) {
      height = Math.max(0, height);
      titleHeight.setDValue(height + "");
   }

   /**
    * Check whether the title height was set by the author rather than left at the default.
    *
    * <p>The property dialogs currently set this flag only when the incoming height differs
    * from the stored {@link #getTitleHeightValue()}. If the displayed default height ever
    * becomes density-derived rather than fixed, that guard must move from comparing against
    * the stored height to comparing against the effective (density-derived) height. Two
    * things follow from that change, not just one:
    *
    * <ol>
    *   <li>the dialog guards' comparison must be rewritten to use the effective height; and</li>
    *   <li>the flag must be made to propagate. {@link #equals(Object)} deliberately excludes
    *       this flag, and each assembly info's {@code copyViewInfo} only transfers the whole
    *       {@code TitleInfo} when {@code !Tool.equals(titleInfo, cinfo.titleInfo)}, so two
    *       {@code TitleInfo}s differing only in this flag would not transfer. That cannot
    *       happen under today's stored-height guard, because the guard only fires when the
    *       height also changes. Under an effective-height guard it becomes routine: stored 20,
    *       effective 26, the author types 20 — the flag would be set on the clone and silently
    *       dropped on apply.</li>
    * </ol>
    *
    * <p>What the stored-height guard does and does not capture. An author who changes the height
    * to a value that happens to equal the default is captured, because the guard compares against
    * the stored value rather than against the default — changing 25 to 20 fires it and the flag
    * sticks. What cannot be captured is an author re-affirming a height already stored: typing 20
    * when 20 is stored produces state identical to never touching the field, so "keep this height
    * fixed" and "I did not touch it" arrive as the same input. It is read as the latter,
    * deliberately, because treating a no-op edit as an assertion would mark every assembly whose
    * dialog was ever opened.
    *
    * <p>An effective-height comparison narrows that further: an author could no longer pin the
    * value the dialog is showing them, because typing it would be indistinguishable from accepting
    * it. Resolving the ambiguity needs a signal the dialog does not carry — the title height is a
    * plain integer field with no unset state — so it would take an explicit use-the-default
    * affordance beside it, with this flag read from that directly rather than inferred from what
    * changed.
    *
    * <p>The flag is also one-way for most types. The only path that clears it is the table
    * reset-layout action, which exists on table infos alone, so a chart, calendar, selection list,
    * selection tree or range slider that acquires the flag has no route back to tracking the
    * default. The same affordance would close that.
    */
   public boolean isUserTitleHeight() {
      return userTitleHeight;
   }

   /**
    * Set whether the title height was set by the author.
    */
   public void setUserTitleHeight(boolean userTitleHeight) {
      this.userTitleHeight = userTitleHeight;
   }

   public Insets getPadding() {
      return padding.get();
   }

   public void setPadding(Insets padding, CompositeValue.Type type) {
      this.padding.setValue(padding, type);
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, title, vs);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writeXML(writer, null);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param format the specified title format.
    */
   public final void writeXML(PrintWriter writer, VSCompositeFormat format) {
      writeXML(writer, format, null, null);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param format the specified title format.
    * @param vs the viewsheet which hand the titleinfo.
    * @param name the viewsheet assembly name
    */
   public final void writeXML(PrintWriter writer, VSCompositeFormat format,
      Viewsheet vs, String name) {
      writer.print("<titleInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer, format, vs, name);
      writer.print("</titleInfo>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseXML(elem, AssetUtil.defh);
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    * @param defaultTitleHeight the owning type's default title height.
    */
   public final void parseXML(Element elem, int defaultTitleHeight) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "titleInfo");

      if(node != null) {
         parseAttributes(node, defaultTitleHeight);
         parseContents(node);
      }
      else {// for bc
         node = Tool.getChildNodeByTagName(elem, "titleValue");
         setTitleValue(node == null ? null : Tool.getValue(node));
         setTitleVisibleValue(Tool.getAttribute(elem, "titleVisible"));

         String heightAttr = VSUtil.getAttributeStr(elem, "titleHeight", null);
         setTitleHeightValue(Integer.parseInt(
            heightAttr != null ? heightAttr : AssetUtil.defh + ""));
         setUserTitleHeight(heightAttr != null && getTitleHeightValue() != defaultTitleHeight);
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" titleVisible=\"" + isTitleVisible() + "\"");
      writer.print(" titleVisibleValue=\"" + getTitleVisibleValue() + "\"");
      writer.print(" titleHeight=\"" + getTitleHeight() + "\"");
      writer.print(" titleHeightValue=\"" + getTitleHeightValue() + "\"");
      writer.print(" userTitleHeight=\"" + isUserTitleHeight() + "\"");
      writer.print(" padding=\"" + padding + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      parseAttributes(elem, AssetUtil.defh);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    * @param defaultTitleHeight the owning type's default title height.
    */
   protected void parseAttributes(Element elem, int defaultTitleHeight) {
      setTitleVisibleValue(Tool.getAttribute(elem, "titleVisibleValue"));

      String heightAttr = VSUtil.getAttributeStr(elem, "titleHeight", null);
      setTitleHeightValue(Integer.parseInt(
         heightAttr != null ? heightAttr : AssetUtil.defh + ""));
      padding.parse(Tool.getAttribute(elem, "padding"));

      // absent in files saved before the flag existed; derive from the type's default
      boolean derived = heightAttr != null && getTitleHeightValue() != defaultTitleHeight;
      String prop = Tool.getAttribute(elem, "userTitleHeight");
      setUserTitleHeight(prop == null ? derived : "true".equalsIgnoreCase(prop));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param format the specified title format.
    */
   protected void writeContents(PrintWriter writer, VSCompositeFormat format) {
      writeContents(writer, format, null, null);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param format the specified title format.
    * @param vs the viewsheet which hand the titleinfo.
    * @param name the viewsheet assembly name
    */
   protected void writeContents(PrintWriter writer, VSCompositeFormat format,
      Viewsheet vs, String name)
   {
      String title = getTitle(format, vs, name);

      if(title != null) {
         writer.print("<title>");
         writer.print("<![CDATA[" + title + "]]>");
         writer.println("</title>");
      }

      if(getTitleValue() != null) {
         writer.print("<titleValue>");
         writer.print("<![CDATA[" + getTitleValue() + "]]>");
         writer.println("</titleValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "titleValue");
      setTitleValue(node == null ? null : Tool.getValue(node));
   }

   /**
    * Returns a string representation of the object.
    */
   public String toString() {
      return super.toString() + "(" + title + ", " + titleVisible + ")";
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TitleInfo)) {
         return false;
      }

      TitleInfo info = (TitleInfo) obj;

      return Tool.equals(title, info.title) &&
         Tool.equals(getTitle(null, null, null), info.getTitle(null, null, null)) &&
         Tool.equals(titleVisible, info.titleVisible) &&
         isTitleVisible() == info.isTitleVisible() &&
         Tool.equals(titleHeight, info.titleHeight) &&
         Tool.equals(getTitleHeight(), info.getTitleHeight()) &&
         Tool.equals(padding, info.padding);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         TitleInfo info = (TitleInfo) super.clone();
         info.title = (DynamicValue2) title.clone();
         info.titleVisible = (DynamicValue2) titleVisible.clone();
         info.titleHeight = (DynamicValue2) titleHeight.clone();
         info.padding = (CompositeValue<Insets>) padding.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TitleInfo", ex);
      }

      return null;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   public List<DynamicValue> getViewDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(title);

      return list;
   }

   /**
    * Reset run time values.
    */
   public void resetRuntimeValues() {
      title.setRValue(null);
      titleVisible.setRValue(null);
      titleHeight.setRValue(null);
   }

   private DynamicValue2 title;
   private DynamicValue2 titleVisible;
   private DynamicValue2 titleHeight;
   private CompositeValue<Insets> padding = new CompositeValue<>(Insets.class, null);
   private boolean userTitleHeight = false;
   private static final Logger LOG = LoggerFactory.getLogger(TitleInfo.class);
}
