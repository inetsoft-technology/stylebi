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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * VSChartAggregateRef, rather than just aggregate information, it holds the
 * attributes required to render itself, axis, label, title, etc.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class VSChartAggregateRef extends VSAggregateRef
   implements ChartAggregateRef, VSChartRef, HyperlinkRef, HighlightRef
{
   /**
    * Create a VSChartAggregateRef.
    */
   public VSChartAggregateRef() {
      this.refImpl = new ChartRefImpl();
      chartType = GraphTypes.CHART_AUTO;
      rtype = GraphTypes.CHART_AUTO;
      titleDesc = new TitleDescriptor(CSSConstants.Measure_Title);
      sframe = new StaticShapeFrameWrapper();
      cframe = new StaticColorFrameWrapper();
      lframe = new StaticLineFrameWrapper();
      tframe = new StaticTextureFrameWrapper();
      scframe = new StaticColorFrameWrapper();
      ((StaticColorFrameWrapper) scframe).setDefaultColor(new Color(0x7030a0));
      stframe = new StaticTextureFrameWrapper();
      zframe = new StaticSizeFrameWrapper();
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      this.refImpl.initDefaultFormat(vs);
   }

   /**
    * Get axis descriptor from this ref.
    * @return the axis descriptor.
    */
   @Override
   public AxisDescriptor getAxisDescriptor() {
      return this.refImpl.getAxisDescriptor();
   }

   @Override
   public void setAxisCSS(String css) {
      this.refImpl.setAxisCSS(css);
   }

   /**
    * Set the axis descriptor into this ref.
    * @param desc the axis descriptor.
    */
   public void setAxisDescriptor(AxisDescriptor desc) {
      refImpl.setAxisDescriptor(desc);
   }

   /**
    * Get runtime axis descriptor from this ref.
    * @return the axis descriptor.
    */
   @Override
   public AxisDescriptor getRTAxisDescriptor() {
      return rdesc;
   }

   /**
    * Set the runtime axis descriptor into this ref.
    * @param desc the axis descriptor.
    */
   @Override
   public void setRTAxisDescriptor(AxisDescriptor desc) {
      this.rdesc = desc;
   }

   /**
    * Get the data format for this measure.
    */
   @Override
   public CompositeTextFormat getTextFormat() {
      return refImpl.getTextFormat();
   }

   /**
    * Set the data format for this measure.
    */
   @Override
   public void setTextFormat(CompositeTextFormat fmt) {
      refImpl.setTextFormat(fmt);
   }

   /**
    * Get shape/texture frame of this ref.
    * @return the shape/texture frame.
    */
   @Override
   public ShapeFrame getShapeFrame() {
      return (ShapeFrame) sframe.getVisualFrame();
   }

   /**
    * Set the shape/texture frame of this ref.
    */
   @Override
   public void setShapeFrame(ShapeFrame shframe) {
      if(sframe != null) {
         sframe.setVisualFrame(shframe);
      }
   }

   /**
    * Get the size frame for this ref.
    */
   @Override
   public SizeFrame getSizeFrame() {
      return runtimeZframe != null ? (SizeFrame) runtimeZframe.getVisualFrame() :
         (SizeFrame) zframe.getVisualFrame();
   }

   /**
    * Set the size frame for this ref.
    */
   @Override
   public void setSizeFrame(SizeFrame zframe0) {
      if(zframe != null) {
         zframe.setVisualFrame(zframe0);
      }
   }

   /**
    * Get the runtime chart type on the ref.
    * @return the runtime chart type.
    */
   @Override
   public int getRTChartType() {
      return this.rtype;
   }

   /**
    * Set the runtime chart type on the ref.
    */
   @Override
   public void setRTChartType(int ctype) {
      this.rtype = ctype;
   }

   /**
    * Get the chart type on the ref.
    * @return the chart type.
    */
   @Override
   public int getChartType() {
      return this.chartType;
   }

   /**
    * Set the chart type on the ref.
    */
   @Override
   public void setChartType(int ctype) {
      this.chartType = ctype;
   }

   /**
    * Get the color frame wrapper.
    * @return the color frame wrapper.
    */
   @Override
   public ColorFrameWrapper getColorFrameWrapper() {
      return cframe;
   }

   /**
    * Set the color frame wrapper.
    * @param wrapper the color frame wrapper.
    */
   @Override
   public void setColorFrameWrapper(ColorFrameWrapper wrapper) {
      this.cframe = wrapper;
   }

   /**
    * Get the size frame wrapper.
    */
   @Override
   public SizeFrameWrapper getSizeFrameWrapper() {
      return zframe;
   }

   /**
    * Set the size frame wrapper.
    */
   @Override
   public void setSizeFrameWrapper(SizeFrameWrapper wrapper) {
      this.zframe = wrapper;
   }

   /**
    * Get the shape/texture frame wrapper.
    * @return the color frame wrapper.
    */
   @Override
   public ShapeFrameWrapper getShapeFrameWrapper() {
      return sframe;
   }

   /**
    * Set the shape frame wrapper.
    * @param wrapper the shape frame wrapper.
    */
   @Override
   public void setShapeFrameWrapper(ShapeFrameWrapper wrapper) {
      this.sframe = wrapper;
   }

   /**
    * Get the texture frame wrapper.
    * @return the texture frame wrapper.
    */
   @Override
   public TextureFrameWrapper getTextureFrameWrapper() {
      return tframe;
   }

   /**
    * Set the texture frame wrapper.
    * @param wrapper the texture frame wrapper.
    */
   @Override
   public void setTextureFrameWrapper(TextureFrameWrapper wrapper) {
      this.tframe = wrapper;
   }

   /**
    * Get the line frame wrapper.
    * @return the line frame wrapper.
    */
   @Override
   public LineFrameWrapper getLineFrameWrapper() {
      return lframe;
   }

   /**
    * Set the line frame wrapper.
    * @param wrapper the line frame wrapper.
    */
   @Override
   public void setLineFrameWrapper(LineFrameWrapper wrapper) {
      this.lframe = wrapper;
   }

   /**
    * Get the color frame of this ref.
    * @return the color frame.
    */
   @Override
   public ColorFrame getColorFrame() {
      return (ColorFrame) cframe.getVisualFrame();
   }

   /**
    * Set the color frame of this ref.
    */
   @Override
   public void setColorFrame(ColorFrame clFrame) {
      if(cframe != null) {
         cframe.setVisualFrame(clFrame);
      }
   }

   /**
    * Get the line frame of this ref.
    * @return the color frame.
    */
   @Override
   public LineFrame getLineFrame() {
      return (LineFrame) lframe.getVisualFrame();
   }

   /**
    * Set the line frame of this ref.
    */
   @Override
   public void setLineFrame(LineFrame lineFrame) {
      if(lframe != null) {
         lframe.setVisualFrame(lineFrame);
      }
   }

   /**
    * Get the color field.
    */
   @Override
   public AestheticRef getColorField() {
      return colorField;
   }

   /**
    * Set the color field.
    */
   @Override
   public void setColorField(AestheticRef field) {
      this.colorField = field;
   }

   /**
    * Get the shape field.
    */
   @Override
   public AestheticRef getShapeField() {
      return shapeField;
   }

   /**
    * Set the shape field.
    */
   @Override
   public void setShapeField(AestheticRef field) {
      this.shapeField = field;
   }

   /**
    * Get the size field.
    */
   @Override
   public AestheticRef getSizeField() {
      return sizeField;
   }

   /**
    * Set the size field.
    */
   @Override
   public void setSizeField(AestheticRef field) {
      this.sizeField = field;
   }

   /**
    * Get the text field.
    */
   @Override
   public AestheticRef getTextField() {
      return textField;
   }

   /**
    * Set the text field.
    */
   @Override
   public void setTextField(AestheticRef field) {
      this.textField = field;
   }

   /**
    * Get the runtime color field.
    */
   @Override
   public DataRef getRTColorField() {
      return colorField != null ? colorField.getRTDataRef() : null;
   }

   /**
    * Get the runtime shape field.
    */
   @Override
   public DataRef getRTShapeField() {
      return shapeField != null ? shapeField.getRTDataRef() : null;
   }

   /**
    * Get the runtime size field.
    */
   @Override
   public DataRef getRTSizeField() {
      return sizeField != null ? sizeField.getRTDataRef() : null;
   }

   /**
    * Get the runtime text field.
    */
   @Override
   public DataRef getRTTextField() {
      return textField != null ? textField.getRTDataRef() : null;
   }

   /**
    * Get the highlight group of this ref.
    * @return the highlight group.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return this.hlGroup;
   }

   /**
    * Set the highlight group of this ref.
    * @param hlGroup the highlight group.
    */
   @Override
   public void setHighlightGroup(HighlightGroup hlGroup) {
      this.hlGroup = hlGroup;
   }

   @Override
   public HighlightGroup getTextHighlightGroup() {
      return textHL;
   }

   @Override
   public void setTextHighlightGroup(HighlightGroup group) {
      this.textHL = group;
   }

   /**
    * Get the hyperlink of the ref.
    * @return the hyperlink.
    */
   @Override
   public Hyperlink getHyperlink() {
      return this.link;
   }

   /**
    * Set the hyperlink of the ref.
    * @param link the hyperlink.
    */
   @Override
   public void setHyperlink(Hyperlink link) {
      this.link = link;
   }

   /**
    * Get the summary color frame wrapper.
    */
   @Override
   public ColorFrameWrapper getSummaryColorFrameWrapper() {
      return scframe;
   }

   /**
    * Set the summary color frame wrapper.
    */
   @Override
   public void setSummaryColorFrameWrapper(ColorFrameWrapper wrapper) {
      this.scframe = wrapper;
   }

   /**
    * Get the summary texture frame wrapper.
    */
   @Override
   public TextureFrameWrapper getSummaryTextureFrameWrapper() {
      return stframe;
   }

   /**
    * Set the summary texture frame wrapper.
    */
   @Override
   public void setSummaryTextureFrameWrapper(TextureFrameWrapper wrapper) {
      this.stframe = wrapper;
   }

   /**
    * Get the summary color frame of the ref.
    * @return the summary color frame.
    */
   @Override
   public ColorFrame getSummaryColorFrame() {
      return (ColorFrame) scframe.getVisualFrame();
   }

   /**
    * Get the summary texture frame of the ref.
    * @return the summary texture frame.
    */
   @Override
   public TextureFrame getSummaryTextureFrame() {
      return (TextureFrame) stframe.getVisualFrame();
   }

   /**
    * Get the title descriptor of the ref.
    * @return the title descriptor.
    */
   @Override
   public TitleDescriptor getTitleDescriptor() {
      return this.titleDesc;
   }

   /**
    * Set the title descriptor of the ref.
    * @param titleDesc the title descriptor.
    */
   @Override
   public void setTitleDescriptor(TitleDescriptor titleDesc) {
      this.titleDesc = titleDesc;
   }

   /**
    * Get the texture frame.
    * @return the texture frame.
    */
   @Override
   public TextureFrame getTextureFrame() {
      return (TextureFrame) tframe.getVisualFrame();
   }

   /**
    * Set the texture frame.
    */
   @Override
   public void setTextureFrame(TextureFrame teFrame) {
      if(tframe != null) {
         tframe.setVisualFrame(teFrame);
      }
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      if((getRefType() & DataRef.MEASURE) != 0 && isAggregateEnabled()) {
         return XSchema.DOUBLE;
      }

      return super.getDataType();
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String chartType = Tool.getAttribute(elem, "chartType");

      if(chartType != null) {
         this.chartType = Integer.parseInt(chartType);
      }

      y2 = "true".equals(Tool.getAttribute(elem, "y2"));
      discrete = Boolean.valueOf(Tool.getAttribute(elem, "discrete"));
   }

   /**
    * Parse contents.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element node;

      refImpl.parseXML(elem);

      node = Tool.getChildNodeByTagName(elem, "shapeVisualFrame");
      // for bc, Legend rename to Visual
      node = node == null ?
             Tool.getChildNodeByTagName(elem, "shapeLegendFrame") : node;

      if(node != null) {
         sframe = (ShapeFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "colorVisualFrame");
      node = node == null ?
             Tool.getChildNodeByTagName(elem, "colorLegendFrame") : node;

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         cframe = (ColorFrameWrapper) VisualFrameWrapper.createVisualFrame(node);
      }

      node = Tool.getChildNodeByTagName(elem, "lineVisualFrame");
      node = node == null ?
             Tool.getChildNodeByTagName(elem, "lineLegendFrame") : node;

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         lframe = (LineFrameWrapper) VisualFrameWrapper.createVisualFrame(node);
      }

      node = Tool.getChildNodeByTagName(elem, "textureVisualFrame");
      node = node == null ?
             Tool.getChildNodeByTagName(elem, "textureLegendFrame") : node;

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         tframe = (TextureFrameWrapper)
            VisualFrameWrapper.createVisualFrame(node);
      }

      Element colorNode = Tool.getChildNodeByTagName(elem, "color");

      if(colorNode != null) {
         colorField = new VSAestheticRef();
         colorField.parseXML(Tool.getFirstChildNode(colorNode));
      }

      Element shapeNode = Tool.getChildNodeByTagName(elem, "shape");

      if(shapeNode != null) {
         shapeField = new VSAestheticRef();
         shapeField.parseXML(Tool.getFirstChildNode(shapeNode));
      }

      Element sizeNode = Tool.getChildNodeByTagName(elem, "size");

      if(sizeNode != null) {
         sizeField = new VSAestheticRef();
         sizeField.parseXML(Tool.getFirstChildNode(sizeNode));
      }

      Element textNode = Tool.getChildNodeByTagName(elem, "text");

      if(textNode != null) {
         textField = new VSAestheticRef();
         textField.parseXML(Tool.getFirstChildNode(textNode));
      }

      parseHighlightGroup(elem);

      node = Tool.getChildNodeByTagName(elem, "Hyperlink");

      if(node != null) {
         link = new Hyperlink();
         link.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "summaryColorVisualFrame");
      node = node == null ?
             Tool.getChildNodeByTagName(elem, "summaryColorLegendFrame") : node;

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         scframe = (ColorFrameWrapper)
            VisualFrameWrapper.createVisualFrame(node);
      }

      node = Tool.getChildNodeByTagName(elem, "summaryTextureVisualFrame");
      node = node == null ?
         Tool.getChildNodeByTagName(elem, "summaryTextureLegendFrame") : node;

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         stframe = (TextureFrameWrapper)
            VisualFrameWrapper.createVisualFrame(node);
      }

      node = Tool.getChildNodeByTagName(elem, "titleDescriptor");

      if(node != null) {
         titleDesc = new TitleDescriptor(CSSConstants.Measure_Title);
         titleDesc.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "sizeVisualFrame");

      if(node != null) {
         zframe = (SizeFrameWrapper) VisualFrameWrapper.createVisualFrame(
            Tool.getFirstChildNode(node));
      }
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(refImpl != null) {
         refImpl.writeXML(writer);
      }

      if(sframe != null) {
         writer.print("<shapeVisualFrame>");
         sframe.writeXML(writer);
         writer.print("</shapeVisualFrame>");
      }

      if(cframe != null) {
         writer.print("<colorVisualFrame>");
         cframe.writeXML(writer);
         writer.print("</colorVisualFrame>");
      }

      if(lframe != null) {
         writer.print("<lineVisualFrame>");
         lframe.writeXML(writer);
         writer.print("</lineVisualFrame>");
      }

      if(getColorField() != null) {
         writer.println("<color>");
         getColorField().writeXML(writer);
         writer.println("</color>");
      }

      if(getShapeField() != null) {
         writer.println("<shape>");
         getShapeField().writeXML(writer);
         writer.println("</shape>");
      }

      if(getSizeField() != null) {
         writer.println("<size>");
         getSizeField().writeXML(writer);
         writer.println("</size>");
      }

      if(getTextField() != null) {
         writer.println("<text>");
         getTextField().writeXML(writer);
         writer.println("</text>");
      }

      writeHighlightGroup(writer);

      if(link != null) {
         link.writeXML(writer);
      }

      if(scframe != null) {
         writer.print("<summaryColorVisualFrame>");
         scframe.writeXML(writer);
         writer.print("</summaryColorVisualFrame>");
      }

      if(stframe != null) {
         writer.print("<summaryTextureVisualFrame>");
         stframe.writeXML(writer);
         writer.print("</summaryTextureVisualFrame>");
      }

      if(tframe != null) {
         writer.print("<textureVisualFrame>");
         tframe.writeXML(writer);
         writer.print("</textureVisualFrame>");
      }

      if(titleDesc != null) {
         titleDesc.writeXML(writer);
      }

      if(zframe != null) {
         writer.print("<sizeVisualFrame>");
         zframe.writeXML(writer);
         writer.print("</sizeVisualFrame>");
      }
   }

   /**
    * Write attributes.
    * @param writer the print writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" chartType=\"" + chartType + "\" ");
      writer.print(" rchartType=\"" + rtype + "\" ");
      writer.print(" y2=\"" + y2 + "\" ");
      writer.print(" discrete=\"" + discrete + "\" ");
   }

   /**
    * Clone.
    */
   @Override
   public Object clone() {
      try {
         VSChartAggregateRef caggRef = (VSChartAggregateRef) super.clone();

         if(refImpl != null) {
            caggRef.refImpl = (ChartRefImpl) refImpl.clone();
         }

         if(sframe != null) {
            caggRef.sframe = (ShapeFrameWrapper) sframe.clone();
         }

         caggRef.setChartType(chartType);

         if(cframe != null) {
            caggRef.cframe = (ColorFrameWrapper) cframe.clone();
         }

         if(hlGroup != null) {
            caggRef.hlGroup = hlGroup.clone();
         }

         if(textHL != null) {
            caggRef.textHL = textHL.clone();
         }

         if(link != null) {
            caggRef.link = (Hyperlink) link.clone();
         }

         if(scframe != null) {
            caggRef.scframe = (ColorFrameWrapper) scframe.clone();
         }

         if(stframe != null) {
            caggRef.stframe = (TextureFrameWrapper) stframe.clone();
         }

         if(tframe != null) {
            caggRef.tframe = (TextureFrameWrapper) tframe.clone();
         }

         if(titleDesc != null) {
            caggRef.titleDesc = (TitleDescriptor) titleDesc.clone();
         }

         if(zframe != null) {
            caggRef.zframe = (SizeFrameWrapper) zframe.clone();
         }

         if(colorField != null) {
            caggRef.colorField = (AestheticRef) colorField.clone();
         }

         if(shapeField != null) {
            caggRef.shapeField = (AestheticRef) shapeField.clone();
         }

         if(sizeField != null) {
            caggRef.sizeField = (AestheticRef) sizeField.clone();
         }

         if(textField != null) {
            caggRef.textField = (AestheticRef) textField.clone();
         }

         return caggRef;
      }
      catch(Exception e) {
         LOG.error("Failed to clone VSChartAggregateRef", e);
         return null;
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if equqls another object by content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof VSChartAggregateRef)) {
         return false;
      }

      VSChartAggregateRef ref = (VSChartAggregateRef) obj;

      return Tool.equals(refImpl, ref.refImpl) &&
         Tool.equals(sframe, ref.sframe) &&
         Tool.equals(zframe, ref.zframe) &&
         chartType == ref.chartType && y2 == ref.y2 &&
         Tool.equals(cframe, ref.cframe) &&
         Tool.equals(hlGroup, ref.hlGroup) &&
         Tool.equals(textHL, ref.textHL) &&
         Tool.equals(link, ref.link) &&
         Tool.equals(scframe, ref.scframe) &&
         Tool.equals(stframe, ref.stframe) &&
         Tool.equals(tframe, ref.tframe) &&
         Tool.equals(lframe, ref.lframe) &&
         Tool.equalsContent(colorField, ref.colorField) &&
         Tool.equalsContent(shapeField, ref.shapeField) &&
         Tool.equalsContent(sizeField, ref.sizeField) &&
         Tool.equalsContent(textField, ref.textField) &&
         Tool.equalsContent(titleDesc, ref.titleDesc) &&
         discrete == ref.discrete;
   }

   /**
    * Check if color frame information has been modified from the default.
    */
   @Override
   public boolean isColorChanged() {
      return colorField == null && cframe != null && cframe.isChanged() ||
         colorField != null && colorField.isChanged();
   }

   /**
    * Check if shape frame information has been modified from the default.
    */
   @Override
   public boolean isShapeChanged() {
      return shapeField == null && sframe != null && sframe.isChanged() ||
         shapeField != null && shapeField.isChanged();
   }

   /**
    * Check if size frame information has been modified from the default.
    */
   @Override
   public boolean isSizeChanged() {
      return sizeField == null && zframe != null && zframe.isChanged() ||
         sizeField != null && sizeField.isChanged() ||
         // if runtime sizeframe set, don't set the size to default size.
         runtimeZframe != null;
   }

   /**
    * Set whether to display this measure on the secondary Y axis.
    */
   @Override
   public void setSecondaryY(boolean y2) {
      this.y2 = y2;
   }

   /**
    * Check whether to display this measure on the secondary Y axis.
    */
   @Override
   public boolean isSecondaryY() {
      return y2;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setDiscrete(boolean discrete) {
      this.discrete = discrete;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isDiscrete() {
      return discrete;
   }

   /**
    * Check this ref is treat as dimension or measure.
    */
   @Override
   public boolean isMeasure() {
      return !isDiscrete();
   }

   /**
    * Set if supports line.
    */
   public void setSupportsLine(boolean supports) {
      this.supportsLine = supports;
   }

   /**
    * Chekc if supports line.
    */
   public boolean isSupportsLine() {
      return supportsLine;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getFullName() {
      // @by gregm if this ChartRef is discrete, prefix with a label to avoid
      // name conflicts with other chart measures.
      if(isDiscrete()) {
         return DISCRETE_PREFIX + super.getFullName();
      }

      return super.getFullName();
   }

   /**
    * Get the full name.
    */
   @Override
   public String getFullName2() {
      if(isDiscrete()) {
         return DISCRETE_PREFIX + super.getFullName2();
      }

      return super.getFullName2();
   }

   public void setRuntimeSizeframe(SizeFrameWrapper runtimeZframe) {
      this.runtimeZframe = runtimeZframe;
   }

   public SizeFrameWrapper getRuntimeSizeframe() {
      return this.runtimeZframe;
   }

   private int chartType; // chart type when separated
   private ChartRefImpl refImpl; // impl contains axis, format, etc.
   private HighlightGroup hlGroup; // highlight defined on this measure
   private HighlightGroup textHL;
   private Hyperlink link; // hyperlink defined on this measure
   private TitleDescriptor titleDesc; // title defined on this measure (radar?)
   private AxisDescriptor rdesc; // runtime axid desc
   private ShapeFrameWrapper sframe; // shape frame
   private ColorFrameWrapper cframe; // color frame
   private LineFrameWrapper lframe; // line frame
   private TextureFrameWrapper tframe; // texture frame
   private ColorFrameWrapper scframe; // summary color frame
   private TextureFrameWrapper stframe; // summary texture frame
   private SizeFrameWrapper zframe; // size frame
   private SizeFrameWrapper runtimeZframe; // runtime size frame
   private AestheticRef colorField;
   private AestheticRef shapeField;
   private AestheticRef sizeField;
   private AestheticRef textField;
   private int rtype; // runtime chart type
   private boolean y2;
   private boolean supportsLine;
   private boolean discrete;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSChartAggregateRef.class);
}
