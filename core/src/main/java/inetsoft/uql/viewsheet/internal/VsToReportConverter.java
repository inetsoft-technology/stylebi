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

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.*;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.BindingAttr;
import inetsoft.report.internal.binding.ChartOption;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.internal.table.TableHighlightAttr.HighlightTableLens;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.painter.ImagePainter;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Viewsheet utilities.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class VsToReportConverter {
   public VsToReportConverter(ViewsheetSandbox box) {
      this.box = box;
   }

   /**
    * Generate a reportsheet from the current viewsheet.
    */
   public ReportSheet generateReport() {
      Viewsheet vs = box.getViewsheet().clone();
      LayoutInfo layoutinfo = vs.getLayoutInfo();
      playout = layoutinfo.getPrintLayout();

      if(playout == null) {
         return new TabularSheet();
      }

      TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
      VSCompositeFormat format = vs.getFormatInfo().getFormat(dataPath);
      scalefont = playout.getScaleFont();
      report = createReportSheet(playout);
      report.setContextName(vs.getName());

      if(!Tool.equals(format.getBackground(), format.getDefaultFormat().getBackground())) {
         report.setUserBackground(format.getBackground());
      }

      Viewsheet vs0 = playout.apply(vs);
      HashMap<String, String> sectionMap = new HashMap<>();
      List<Assembly> allAssemblies = getAssemblies(vs0, playout);
      allAssemblies.addAll(getEditableAssemblies(vs0, playout.getEditableAssemblyLayouts()));
      TableLens lens;

      TextVSAssembly warningText = vs0.getWarningTextAssembly(false);

      if(warningText != null) {
         warningText.setTextValue("");
      }

      for(Assembly assembly : allAssemblies) {
         if(!isVisibleInPrintLayout((VSAssembly) assembly, false) ||
            assembly instanceof ContainerVSAssembly || assembly instanceof ShapeVSAssembly)
         {
            continue;
         }

         String name = assembly.getAbsoluteName();

         try {
            lens = box.getTableData(name);
         }
         catch(Exception ex) {
            LOG.error("Failed to get vs tablelens", ex);
            continue;
         }

         if(lens != null) {
            VSEventUtil.addWarningText(lens, box, name, false);
         }

         String limitMessage = box.getLimitMessage(name);

         if(limitMessage != null && warningText == null) {
            warningText = vs0.getWarningTextAssembly();
         }

         if(warningText != null && limitMessage != null && (warningText.getTextValue() == null ||
            !warningText.getTextValue().contains(limitMessage)))
         {
            warningText.setTextValue(warningText.getTextValue() + "\n" + limitMessage);
         }
      }

      if(warningText != null) {
         VSUtil.setAutoSizeTextHeight(warningText.getInfo(), vs0);

         if(!allAssemblies.contains(warningText)) {
            allAssemblies.add(warningText);
         }

         int maxY = getLayoutMaxY();
         VSAssemblyLayout layout = new VSAssemblyLayout(warningText.getAbsoluteName(),
            new Point(0, maxY + 10), warningText.getPixelSize());
         warningText.getVSAssemblyInfo().setLayoutPosition(layout.getPosition());
         warningText.getVSAssemblyInfo().setLayoutSize(layout.getSize());
         List<VSAssemblyLayout> layouts = playout.getVSAssemblyLayouts();

         if(layouts != null) {
            layouts.add(layout);
         }
      }

      VSAssembly[] sorted = sortByPosition(allAssemblies.toArray(new Assembly[0]));
      createReportSections(sorted, sectionMap);

      for(Assembly assembly : allAssemblies) {
         VSAssembly vsAssembly = (VSAssembly) assembly;

         // only add direct sub assemblies here, and further sub assemblies
         // will be added in the function which is used to add their
         // direct container.
         if(isInContainer(vsAssembly) ||
            VSUtil.isTipView(vsAssembly.getAbsoluteName(), vsAssembly.getViewsheet()) ||
            VSUtil.isPopComponent(vsAssembly.getAbsoluteName(), vsAssembly.getViewsheet()))
         {
            continue;
         }

         if(vsAssembly instanceof TextVSAssembly) {
            TextVSAssembly text = (TextVSAssembly) vsAssembly;
            ParameterTool ptool = new ParameterTool();

            if(ptool.containsParameter(text.getText())) {
               text.setTextValue(ptool.parseParameters(box.getVariableTable(),
                  text.getTextValue()));
            }
         }

         convertVSAssembly(vsAssembly, sectionMap.get(assembly.getAbsoluteName()));
      }

      addHeaderFooterElements(vs0, playout.getHeaderLayouts(),
                              headerSection.getID());
      addHeaderFooterElements(vs0, playout.getFooterLayouts(),
                              footerSection.getID());
      setSectionBreakable();
      return report;
   }

   /**
    * Get the max y of assemblies in print layout.
    * @return
    */
   private int getLayoutMaxY() {
      if(playout == null) {
         return 0;
      }

      List<VSAssemblyLayout> layouts = playout.getVSAssemblyLayouts();

      if(layouts == null) {
         return 0;
      }

      return layouts.stream()
         .filter(layout -> layout.getPosition() != null && layout.getSize() != null)
         .mapToInt(layout -> layout.getPosition().y + layout.getSize().height)
         .max()
         .orElse(0);
   }

   private List<Assembly> getAssemblies(Viewsheet vs, PrintLayout layout) {
      Assembly[] assemblies = vs.getAssemblies(true, true);
      return Arrays.stream(assemblies)
         .filter(assembly -> isVisibleInReport(assembly, layout))
         .collect(Collectors.toList());
   }

   private boolean isVisibleInReport(Assembly assembly, PrintLayout layout) {
      return isLayoutAssembly(assembly, layout) && !isOutOfBounds(assembly, layout);
   }

   private VSAssemblyLayout getVSAssemblyLayout(Assembly assembly, PrintLayout layout) {
      if(layout == null || assembly == null) {
         return null;
      }

      VSAssembly container = getTopContainer((VSAssembly) assembly);
      assembly = container == null ? assembly : container;
      String name = assembly.getAbsoluteName();
      int idx = name.indexOf(".");

      if(idx != -1) {
         return layout.getVSAssemblyLayout(name.substring(0, idx));
      }

      return layout.getVSAssemblyLayout(name);
   }

   /**
    * Check if the target vs assembly has vslayout in print layout.
    * @return
    */
   private boolean isLayoutAssembly(Assembly assembly, PrintLayout pLayout) {
      return getVSAssemblyLayout(assembly, pLayout) != null;
   }

   private boolean isOutOfBounds(Assembly assembly, PrintLayout layout) {
      double width = getPrintPageSize(layout).width;
      Rectangle rec = getPixelBounds((VSAssembly) assembly);
      return rec.x >= width;
   }

   /**
    * Create a reportsheet, and apply the viewsheet printlayout.
    */
   private TabularSheet createReportSheet(PrintLayout playout) {
      PrintInfo pinfo = playout.getPrintInfo();
      TabularSheet report = new TabularSheet();
      report.setPageSize(getInchSize(pinfo, playout.isHorizontalScreen()));
      Margin margin = pinfo.getMargin();
      report.setMargin(margin);
      int MARGIN_LEFT = (int) margin.left * 72;
      int MARGIN_TOP = (int) margin.top * 72;

      report.setHeaderFromEdge(pinfo.getInchHeaderFromEdge());
      report.setFooterFromEdge(pinfo.getInchFooterFromEdge());
      report.setPageNumberingStart(pinfo.getPageNumberingStart());

      headerSection = createReportSection();
      // section id may be duplicated with other section which in
      // section id may be duplicated with other section which in
      // other regions (footer/detail), so set id for header and footer.
      headerSection.setID("headerSection");
      float headerH = (float) (margin.top - pinfo.getInchHeaderFromEdge());
      getSectionContent(headerSection).setHeight(headerH);
      report.addHeaderElement(headerSection);

      footerSection = createReportSection();
      footerSection.setID("footerSection");
      footerSection.setProperty("vsPrintLayout", true + "");
      float footerH = pinfo.getInchFooterFromEdge();
      getSectionContent(footerSection).setHeight(footerH);
      report.addFooterElement(footerSection);

      return report;
   }

   // set band to non-breakable if it contains a chart and no other element
   private void setSectionBreakable() {
      for(LayoutSection layout : contentSections) {
         SectionLens inner = layout.section.getSection();
         SectionBand[] innerContent = inner.getSectionContent();

         for(SectionBand band : innerContent) {
            boolean breakable = band.getElementCount() == 0;

            for(int k = 0; k < band.getElementCount(); k++) {
               if(isBreakable(band.getElement(k))) {
                  breakable = true;
                  break;
               }
            }

            band.setBreakable(breakable);
         }
      }
   }

   // check if an element can be broken into pieces
   private static boolean isBreakable(ReportElement elem) {
      // don't break chart into multiple pages if can fit on one page
      if(elem instanceof ChartElement) {
         return ((ChartElement) elem).getLayout() == ReportSheet.PAINTER_BREAKABLE;
      }

      return true;
   }

   /**
    * Get a list of assemblies converted from the editable layout
    * @param vs current viewsheet to convert to report.
    * @param alayouts layouts which need to add to report header/footer.
    */
   private ArrayList<Assembly> getEditableAssemblies(Viewsheet vs,
                                                    ArrayList<VSAssemblyLayout> alayouts)
   {
      ArrayList<Assembly> assemblies = new ArrayList<>();

      if(alayouts == null || alayouts.size() == 0) {
         return assemblies;
      }

      for(VSAssemblyLayout alayout : alayouts) {
         VSAssembly assembly = null;

         if(alayout instanceof VSEditableAssemblyLayout) {
            VSEditableAssemblyLayout elayout =
               (VSEditableAssemblyLayout) alayout;
            String name = elayout.getName();
            VSAssemblyInfo info = elayout.getInfo();
            info.setLayoutPosition(elayout.getPosition());
            info.setLayoutSize(elayout.getSize());

            if(info instanceof TextVSAssemblyInfo) {
               assembly = new TextVSAssembly(vs, name);
            }
            else if(info instanceof ImageVSAssemblyInfo) {
               assembly = new ImageVSAssembly(vs, name);
            }

            if(assembly != null) {
               assembly.setVSAssemblyInfo(info);
               assemblies.add(assembly);
            }
         }
      }

      return assemblies;
   }

   /**
    * Add elements to the report header/footer.
    * @param vs current viewsheet to convert to report.
    * @param alayouts layouts which need to add to report header/footer.
    * @param sectionID sectionid of target section.
    */
   private void addHeaderFooterElements(Viewsheet vs,
                                        List<VSAssemblyLayout> alayouts,
                                        String sectionID)
   {
      if(alayouts == null || alayouts.size() == 0) {
         return;
      }

      for(VSAssemblyLayout alayout : alayouts) {
         VSAssembly assembly = null;

         if(alayout instanceof VSEditableAssemblyLayout) {
            VSEditableAssemblyLayout elayout =
               (VSEditableAssemblyLayout) alayout;
            String name = elayout.getName();
            VSAssemblyInfo info = elayout.getInfo();

            if(info instanceof TextVSAssemblyInfo) {
               assembly = new TextVSAssembly(vs, name);
            }
            else if(info instanceof ImageVSAssemblyInfo) {
               assembly = new ImageVSAssembly(vs, name);
            }

            if(assembly != null) {
               assembly.setVSAssemblyInfo(info);
            }
         }
         else {
            assembly = (VSAssembly) vs.getAssembly(alayout.getName());

            // only add direct sub assemblies here, and further sub assemblies
            // will be added in the function which is used to add their
            // direct container.
            if(assembly == null || isInContainer(assembly)) {
               continue;
            }
         }

         if(assembly != null) {
            convertVSAssembly(assembly, sectionID);
         }
      }
   }

   /**
    * Refresh Assembly size.
    * Return false if the whole assembly is out of the report content bounds.
    * else return true.
    */
   private boolean refreshAssemblySize(VSAssembly assembly) {
      if(assembly == null) {
         return false;
      }

      Rectangle rec = getPixelBounds(assembly);
      int pwidth = getPageContentSize().width;
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

      // if out of the header/footer bounds, don't display.
      if(rec.x >= pwidth) {
         info.setVisible(false);
         return false;
      }
      // if part out of the header/footer bounds, resize the assembly.
      else if(rec.x + rec.width > pwidth) {
         info.setLayoutSize(new Dimension(pwidth - rec.x, rec.height));
      }

      return true;
   }

   /**
    * Get page size in inch unit.
    */
   private Size getInchSize(PrintInfo pinfo, boolean islandscape) {
      DimensionD size = pinfo.getSize();
      String unit = pinfo.getUnit();
      double ratio = 1;

      if("mm".equals(unit)) {
         ratio = 1 / INCH_MM;
      }
      else if("points".equals(unit)) {
         ratio = 1 / INCH_POINT;
      }

      if(islandscape) {
         return new Size(size.getHeight() * ratio, size.getWidth() * ratio);
      }

      return new Size(size.getWidth() * ratio, size.getHeight() * ratio);
   }

    /**
    * Check if current assembly is visible in printlayout mode.
    */
   private boolean isVisibleInPrintLayout(VSAssembly assembly,
                                          boolean filterHeaderFooters)
   {
      // return false if need to filter out the header/footer elements
      if(filterHeaderFooters && isHeaderFooterElement(assembly)) {
         return false;
      }

      VSAssembly container = assembly.getContainer();

      while(container != null) {
         VSAssemblyInfo cinfo = (VSAssemblyInfo) container.getInfo();

         if(!cinfo.isVisible(true) || cinfo.isEmbedded() && !cinfo.isPrimary()) {
            return false;
         }

         container = container.getContainer();
      }

      Viewsheet vs = assembly.getViewsheet();

      while(vs != null && vs.isEmbedded()) {
         ViewsheetVSAssemblyInfo vinfo = (ViewsheetVSAssemblyInfo) vs.getInfo();
         VSAssembly vcontainer = vs.getContainer();
         boolean cvis = vcontainer == null ||
                        vcontainer.getVSAssemblyInfo().isVisible(true);
         boolean svis = vinfo.isVisible(true);

         if(!vs.isPrimary() || !cvis || !svis) {
            return false;
         }

         vs = vs.getViewsheet();
      }

      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

      return info.isVisible(true) && (!info.isEmbedded() || info.isPrimary());
   }

   /**
    * Check if current assembly is a header/footer element.
    */
   private boolean isHeaderFooterElement(VSAssembly assembly) {
      ArrayList<VSAssemblyLayout> layouts =
         new ArrayList<>(playout.getHeaderLayouts());
      layouts.addAll(playout.getFooterLayouts());

      for(int i = 0; i < layouts.size(); i++) {
         VSAssemblyLayout assemblyLayout = layouts.get(i);

         if(assemblyLayout.getName().equals(assembly.getAbsoluteName())) {
            return true;
         }
      }

      VSAssembly container = assembly.getContainer();

      while(container != null) {
         if(isHeaderFooterElement(container)) {
            return true;
         }

         container = container.getContainer();
      }

      Viewsheet vs = assembly.getViewsheet();

      while(vs != null && vs.isEmbedded()) {
         if(isHeaderFooterElement(vs)) {
            return true;
         }

         vs = vs.getViewsheet();
      }

      return false;
   }

   /**
    * Convert the vsassembly to report element.
    */
   private void convertVSAssembly(VSAssembly assembly, String sectionName) {
      if(!isVisibleInPrintLayout(assembly, false) ||
         isAnnotationComponent(assembly) || sectionName == null ||
         !refreshAssemblySize(assembly))
      {
         return;
      }

      assembly.setZIndex(zindex++);
      int type = assembly.getAssemblyType();
      String name = assembly.getAbsoluteName();
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      VSTableLens lens = null;
      String text = null;

      try {
         switch(type) {
         case AbstractSheet.TABLE_VIEW_ASSET:
         case AbstractSheet.EMBEDDEDTABLE_VIEW_ASSET:
         case AbstractSheet.CROSSTAB_ASSET:
         case AbstractSheet.FORMULA_TABLE_ASSET:
            // get a copy of the tablelens which without scale font.
            lens = box.getVSTableLens(name, false, scalefont);
            TableLens tableLens = lens.getTable();

            //when header rows too tall, to avoid the first page being empty.
            if(tableLens instanceof AttributeTableLens && lens.getHeaderRowCount() > 10) {
               AttributeTableLens attTablelens = (AttributeTableLens) tableLens;
               attTablelens.setHeaderRowCount(0);
            }

            copyTableRColumnWidth2((TableDataVSAssembly) assembly, box.getViewsheet(), lens);
            lens = getRegionTableLens(lens, (TableDataVSAssembly) assembly);
            addTable((TableDataVSAssembly) assembly, lens, sectionName);
            break;
         case AbstractSheet.CHART_ASSET:
            DataSet data = (DataSet) box.getData(name);
            addChart((ChartVSAssembly) assembly, data, sectionName);
            break;
         case AbstractSheet.SELECTION_LIST_ASSET:
            addSelectionList((SelectionListVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.SELECTION_TREE_ASSET:
            addSelectionTree((SelectionTreeVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.TIME_SLIDER_ASSET:
            if(assembly instanceof TimeSliderVSAssembly &&
               ((TimeSliderVSAssembly) assembly).getTimeSliderInfo() != null)
            {
               ((TimeSliderVSAssembly) assembly).getTimeSliderInfo().setCurrentVisible(true);
            }

            text = ((AbstractSelectionVSAssembly) assembly).
                  getDisplayValue(false);
            addTextBoxElement(assembly, text, sectionName);
            break;
         case AbstractSheet.CALENDAR_ASSET:
            addCalendar((CalendarVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.CURRENTSELECTION_ASSET:
            addCurrentSelection((CurrentSelectionVSAssembly) assembly,
               sectionName);
            break;
         case AbstractSheet.SLIDER_ASSET:
         case AbstractSheet.SPINNER_ASSET:
            text = ((NumericRangeVSAssemblyInfo) info).getValueLabel() + "";
            addTextBoxElement(assembly, text, sectionName);
            break;
         case AbstractSheet.COMBOBOX_ASSET:
            ComboBoxVSAssemblyInfo cinfo = (ComboBoxVSAssemblyInfo) info;
            String label = cinfo.getSelectedLabel();
            // for editable combobox, if the input value isn't in the dropdown
            // list, then need use getSelectedObject to get the selected value.
            label = label == null ? cinfo.getSelectedObject() + "" : label;
            addTextBoxElement(assembly, label, sectionName);
            break;
         case AbstractSheet.RADIOBUTTON_ASSET:
            addRadioButton((RadioButtonVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.CHECKBOX_ASSET:
            addCheckBox((CheckBoxVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.TEXTINPUT_ASSET:
            Object value = ((TextInputVSAssemblyInfo) info).getValue();

            if(value != null) {
               addTextBoxElement(assembly, value + "", sectionName);
            }

            break;
         case AbstractSheet.SUBMIT_ASSET:
            text = ((SubmitVSAssemblyInfo) info).getLabelName();
            addTextBoxElement(assembly, text, sectionName);
            break;
         case AbstractSheet.TEXT_ASSET:
            addText((TextVSAssembly) assembly, sectionName);
           break;
         case AbstractSheet.IMAGE_ASSET:
            addImage((ImageVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.GAUGE_ASSET:
            addImageElement(assembly, sectionName);
            break;
         case AbstractSheet.THERMOMETER_ASSET:
            addImageElement(assembly, sectionName);
            break;
         case AbstractSheet.SLIDING_SCALE_ASSET:
            addImageElement(assembly, sectionName);
            break;
         case AbstractSheet.CYLINDER_ASSET:
            addImageElement(assembly, sectionName);
            break;
         case AbstractSheet.LINE_ASSET:
            addLine((LineVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.RECTANGLE_ASSET:
            addRectangle((RectangleVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.OVAL_ASSET:
            addOval((OvalVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.ANNOTATION_ASSET:
         case AbstractSheet.ANNOTATION_LINE_ASSET:
         case AbstractSheet.ANNOTATION_RECTANGLE_ASSET:
            break;
         case AbstractSheet.TAB_ASSET:
            addTab((TabVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.GROUPCONTAINER_ASSET:
            addGroupContainer((GroupContainerVSAssembly) assembly, sectionName);
            break;
         case AbstractSheet.VIEWSHEET_ASSET:
            addViewsheet((Viewsheet) assembly, sectionName);
         default:
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to convert viewsheet to report", ex);
      }
   }

   /**
    * Get report page width in pixel.
    */
   private Dimension getPageContentSize() {
      Size pageSize = report.getPageSize();
      Margin margin = report.getMargin();

      int w = (int) ((pageSize.width - margin.left - margin.right) * 72.0);
      int h = (int) ((pageSize.height - margin.top - margin.bottom) * 72.0);

      return new Dimension(w, h);
   }

   /**
    * Create sections for reportsheet, and report elements will be put into
    * sections to display a proper layout.
    * @param assemblies assemblies array.
    * @param sectionMap <assemblyname, sectionid>.
    */
   private void createReportSections(Assembly[] assemblies, HashMap<String, String> sectionMap) {
      int pwidth = getPageContentSize().width;

      if(assemblies.length == 0) {
         return;
      }

      double end_y = 0; // bottom of the elements so far
      Rectangle bounds = null;
      SectionElementDef innersection = null;
      int pheight = getPageContentSize().height;
      int pageNum = 0;
      boolean acrossPages = false;

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];
         final Rectangle assemblybounds = getPixelBounds(assembly);
         int y = assemblybounds.y;
         int height = assemblybounds.height;
         int currPage = getPageNumber(y, pheight);

         if(assembly instanceof TabVSAssembly) {
            String name = ((TabVSAssembly) assembly).getSelected();
            Viewsheet vs = assembly.getViewsheet();
            VSAssembly selected = vs.getAssembly(name);
            Rectangle sub = getPixelBounds(selected);
            height = sub.y + sub.height - y;
         }

         if(assembly instanceof LineVSAssembly) {
            height += 5;
         }

         // fill the top empty space
         if(i == 0) {
            SectionElementDef filler = createReportSection();
            getSectionContent(filler).setHeight(y / 72f);
            addSection(filler, new Rectangle(0, 0, pwidth, y));
            bounds = new Rectangle(0, 0, pwidth, y + height);
         }

         // inside the current section band.
         if(y <= end_y && innersection != null) {
            // reset bounds
            bounds = new Rectangle(0, bounds.y, pwidth,
               Math.max(bounds.height, y + height - bounds.y));
            setSectionBounds(innersection.getID(), bounds.height);
            end_y = Math.max(end_y, y + height);
         }
         // start a new band.
         else {
            // 1. fix Bug #45814, use page break to keep the blank space in bottom of the page bottom
            // instead of using blank section, because the table data will be expanded which will
            // caused the blank space height are not fixed.
            // 2. still use the blank space to keep the blank space above the topmost element,
            // which should be safe and simple.
            // 3. if an element is across page boundary (e.g. title1), it will be pushed to the
            // next page, and then the contents below the text will be pushed to the page after
            // the next page. This is never desirable. so we don't insert a page break if the
            // previous element sits on the page boundary. (56608)
            if(currPage > pageNum && i != 0 && !acrossPages) {
               // keep blank page in elements.
               for(int k = pageNum; k < currPage; k++) {
                  addPageBreakElement();
               }

               int ny = currPage * pheight;
               height = height + y - ny;
               y = ny;
            }
            // add an empty filler section to maintain distance between elements
            // defined in layout
            else if(y > bounds.getMaxY()) {
               SectionElementDef filler = createReportSection();
               int fillh = y - bounds.y - bounds.height;
               getSectionContent(filler).setHeight(fillh / 72f);
               addSection(filler, new Rectangle(0, bounds.y + bounds.height, pwidth, fillh));
            }

            innersection = createReportSection();
            // set bounds for the new innersection
            bounds = new Rectangle(0, y, pwidth, height);
            addSection(innersection, bounds);
            end_y = y + height;
         }

         float sectinH = (float) bounds.height / (float) 72;
         getSectionContent(innersection).setHeight(sectinH);
         sectionMap.put(assembly.getAbsoluteName(), innersection.getID());
         acrossPages = getPageNumber(y + height, pheight) > currPage;
         pageNum = currPage;
      }
   }

   /**
    * @param y           the target y in page content.
    * @param pageHeight  the print layout page content height.
    * @return the page number of the target assembly layout.
    */
   private static int getPageNumber(int y, int pageHeight) {
      if(pageHeight <= 0) {
         return 0;
      }

      return (int) Math.ceil(y / pageHeight);
   }

   /**
    * Sort the assemblies by position.y in ascending order.
    */
   private VSAssembly[] sortByPosition(Assembly[] assemblies) {
      VSAssembly[] filtered = filterAssemblies(assemblies);

      Arrays.sort(filtered, new Comparator() {
         @Override
         public int compare(Object obj0, Object obj1) {
         int y0 = getPixelBounds((VSAssembly) obj0).y;
            int y1 = getPixelBounds((VSAssembly) obj1).y;

            if(y0 != y1) {
               return y0 - y1;
            }

            return 0;
         }
      });

      return filtered;
   }

   /**
    * Filter the assemblies which are not necessary when sorting the assemblies
    * by position.
    */
   private VSAssembly[] filterAssemblies(Assembly[] assemblies) {
      ArrayList<VSAssembly> list = new ArrayList<>();

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];

         // filter out the header/footer element, because we have already
         // created sections for header and footer, and if created sections
         // here again will cause the header/footer elements will be added to
         // detail pane too.
         if(!isVisibleInPrintLayout(assembly, true) ||
            isAnnotationComponent(assembly) || isInCurrentSelection(assembly))
         {
            continue;
         }

         list.add(assembly);
      }

      VSAssembly[] filtered = new VSAssembly[list.size()];
      list.toArray(filtered);

      return filtered;
   }

   /**
    * Create a section element which only show content sectionband.
    */
   private SectionElementDef createReportSection() {
      SectionElementDef s = new SectionElementDef(report);
      // SectionElementDef adds 1 to printHead.y at end of print, which leaves a
      // 1px gap between sections we use to hold element. set spacing to -1
      // to cancel the advance.
      s.setSpacing(-1);
      s.getSection().getSectionHeader()[0].setVisible(false);
      s.getSection().getSectionFooter()[0].setVisible(false);
      return s;
   }

   /**
    * Get content sectionband.
    */
   private static SectionBand getSectionContent(SectionElementDef s) {
      return (s.getSection().getSectionContent())[0];
   }

   /**
    * Get pixel bounds on top viewsheet.
    * @param assembly target assembly.
    */
   private Rectangle getPixelBounds(VSAssembly assembly) {
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      Point pos = info.getLayoutPosition();
      Dimension size = info.getLayoutSize();
      pos = pos != null ? pos : new Point(0, 0);
      size = size != null ? size : new Dimension(0, 0);

      return new Rectangle(pos.x, pos.y, size.width, size.height);
   }

   /**
    * Add reportelement to fixed position of the report section.
    */
   private void addElement(VSAssembly assembly, BaseElement elem, String sectionName) {
      Rectangle bounds = getPixelBounds(assembly);
      int zindex = assembly.getZIndex();

      if(assembly instanceof VSGroupContainer) {
         zindex = -1;
      }

      elem.setZIndex(assembly.getZIndex());
      addElement0(bounds, elem, sectionName);
   }

   /**
    * Add reportelement to fixed position of the report section.
    */
   private void addElement0(Rectangle bounds, ReportElement elem,
                            String sectionName)
   {
      SectionElementDef section = (SectionElementDef) report.getElement(sectionName);
      Rectangle sbounds = getSectionBounds(sectionName);
      int offsetX = 0;
      int offsetY = 0;

      if(sbounds != null) {
         offsetX = sbounds.x;
         offsetY = sbounds.y;
      }

      bounds = new Rectangle(bounds.x - offsetX, bounds.y - offsetY,
                             bounds.width, bounds.height);
      getSectionContent(section).addElement(elem, bounds);
   }

   /**
    * Convert vstable to report table and add to the reportsheet.
    */
   private void addTable(TableDataVSAssembly assembly, VSTableLens lens, String sectionName) {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getInfo();
      final Rectangle bounds;

      if(info.isTitleVisible()) {
         bounds = createTitle(assembly, sectionName, true);
      }
      else {
         bounds = subtractHiddenTitleFromBounds(assembly, true);
      }

      if(lens == null) {
         return;
      }

      TableElementDef tableelem = new TableElementDef(report, lens);
      tableelem.setKeepRowHeightOnPrint(info.isKeepRowHeightOnPrint());
      // use Manual Column Widths to keep the column width.
      tableelem.setEmbedWidth(true);
      VSAssemblyLayout layout = getVSAssemblyLayout(assembly, playout);
      int tableLayout = layout == null ? ReportSheet.TABLE_FIT_CONTENT_PAGE : layout.getTableLayout();
      // set to fit page which is closest to vs display
      tableelem.setLayout(tableLayout);
      // Because the vstablelens setted to the report table element will be
      // wrapped by other lens, hyperlink may cannot be get directly, we'd
      // better set the vstablelens and get the hyperlink form this vstablelens.
      tableelem.setVSTableLens(lens);
      int[] columnPixelW = calculateColumnWidths(info, lens);

      // Fix bug1432926796683, if total of column width is less than layout's
      // width, fit page; otherwise fit content.
      int totalw = 0;

      for(int i = 0; i < columnPixelW.length; i++) {
         totalw += columnPixelW[i];
      }

      if(totalw < info.getLayoutSize().width) {
         tableelem.setLayout(ReportSheet.TABLE_FIT_PAGE);
      }
      else if(totalw > info.getLayoutSize().width * 5 && tableLayout == ReportSheet.TABLE_FIT_PAGE) {
         tableelem.setLayout(ReportSheet.TABLE_FIT_CONTENT_PAGE);
      }

      // since report crosstab have no hscrollbar, however you resize the
      // columns width, at least one aggregate should not be wrapped in a new
      // table segment , so the columns width will not be exactly setted to the
      // fixed width user want be but scaled to a proper width. So even we
      // setted the fixed widths to report table, the column widths may not
      // be exactly same as the vs column width, and i think it's reasonable.
      tableelem.setFixedWidths(columnPixelW);
      tableelem.setFixedHeights(calculateRowHeights(info, lens));

      tableelem.setZIndex(assembly.getZIndex());
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         TableDataPath objPath = new TableDataPath(-1, TableDataPath.OBJECT);
         VSCompositeFormat fmt = finfo.getFormat(objPath, false);
         tableelem.setBorders(fmt.getBorders());
         tableelem.setBorderColors(fmt.getBorderColors());
         tableelem.setAlignment(fmt.getAlignment());
         tableelem.setFont(fmt.getFont());
         tableelem.setForeground(fmt.getForeground());
         tableelem.setBackground(fmt.getBackground());
      }

      addElement0(bounds, tableelem, sectionName);
   }

   private void copyTableRColumnWidth2(TableDataVSAssembly assembly, Viewsheet viewsheet, TableLens lens) {
      TableDataVSAssemblyInfo info = assembly.getTableDataVSAssemblyInfo();
      TableDataVSAssembly assembly1 =
         (TableDataVSAssembly) viewsheet.getAssembly(assembly.getAbsoluteName());
      TableDataVSAssemblyInfo info1 = assembly1.getTableDataVSAssemblyInfo();
      Map<TableDataPath, Double> rcolWidths2 = info1.getRColumnWidths2();

      rcolWidths2.forEach((path, width) -> {
         info.setRColumnWidth2(path, width, lens);
      });
   }

   /**
    * Calculate the pixel row heights.
    * This function is just for the table which applied printlayout.
    */
   private int[] calculateRowHeights(TableDataVSAssemblyInfo info, VSTableLens lens) {
      if(lens.getRowCount() == 0) {
         return new int[0];
      }

      int[] hs = new int[lens.getRowCount()];
      int[] heights = lens.getRowHeights();
      boolean defaultHeight = info.getDataRowHeight() == AssetUtil.defh;

      // get user set row heights
      for(int i = 0; i < hs.length; i++) {
         double h = lens.getRowHeight(i);
         boolean wrapLine = lens.isWrapLine(i);

         // Bug #46409: set h to -1 if current row is wrap line, make sure get right wrap row height
         // when row height was resized before setting wrapline.
         if(wrapLine || (Double.isNaN(h) || h < 0 || h == AssetUtil.defh) && heights != null && i < heights.length) {
            // don't use default VS table row height in report
            h = wrapLine || defaultHeight && heights[i] == AssetUtil.defh ? -1 : heights[i];
         }

         hs[i] = (int) h;
      }

      if(scalefont != 1) {
         for(int i = 0; i < hs.length; i++) {
            if(hs[i] > 0) {
               hs[i] = Math.round(hs[i] * scalefont);
            }
         }
      }

      return hs;
   }

   /**
    * Calculate the pixel column widths.
    * This funtion is just for the table which applied printlayout.
    */
   private int[] calculateColumnWidths(TableDataVSAssemblyInfo info,
                                       VSTableLens lens)
   {
      // Deprecated exporter logic, needs updating
      // @damianwysocki, Bug #9543
      // Removed grid, this exporter logic needs to be updated
      if(lens.getColCount() == 0) {
         return new int[0];
      }

      int totalWidth = 0;
      int totalPixelW = 0;
      int layoutPixelW = info.getLayoutSize().width;
      int[] ws = new int[lens.getColCount()];
      int[] widths = lens.getColumnWidths();

      // get user set column widths
      for(int i = 0; i < ws.length; i++) {
         double w = info.getColumnWidth2(i, lens);

         if((Double.isNaN(w) || w <= 0) && widths != null && i < widths.length) {
            w = widths[i];
         }

         if(scalefont != 1) {
            w = Math.round(w * scalefont);
         }

         ws[i] = (int) w;
         totalWidth += w;
      }

      Dimension infoSize = info.getPixelSize();
      totalPixelW += infoSize.width;

      if(totalWidth < layoutPixelW) {
         int remainWidth = layoutPixelW - totalWidth;

         if(remainWidth < totalWidth) {
            // Bug #43321, expand the last column with a non-zero width (not hidden)
            for(int i = ws.length -1; i >= 0; i--) {
               if(ws[i] > 0) {
                  ws[i] = ws[i] + remainWidth;
                  break;
               }
            }
         }
      }
      else if(totalWidth > layoutPixelW) {
         int[] nws;
         List<Integer> wsList = new ArrayList<>();
         int temp = 0;

         for(int i = 0; i < ws.length; i ++) {
            temp = ws[i];

            if(temp > layoutPixelW) {
               wsList.add(ws[i] - (temp - totalPixelW));
               //break;
            }
            else {
               wsList.add(ws[i]);
            }
         }

         nws = new int[wsList.size()];

         for(int i = 0; i < wsList.size(); i++) {
            nws[i] = wsList.get(i);
         }

         if(totalPixelW > totalWidth) {
            int wrappedTotal = 0;

            for(int cWidth : nws) {
               wrappedTotal += cWidth;

               if(wrappedTotal > layoutPixelW) {
                  wrappedTotal = cWidth;
               }
            }

            int remainWidth = totalPixelW - totalWidth;

            for(int i = ws.length -1; i >= 0; i--) {
               if(ws[i] > 0) {
                  nws[i] = nws[i] + Math.min(remainWidth, layoutPixelW - wrappedTotal);
                  break;
               }
            }
         }

         ws = nws;
      }

      return ws;
   }

   /**
    * Covert title cell of TitledVSAssembly to report textbox element.
    */
   private Rectangle createTitle(VSAssembly assembly, String sectionName,
                                 boolean onlytitle)
   {
      if(!(assembly instanceof TitledVSAssembly)) {
         return null;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      String title = ((TitledVSAssemblyInfo) info).getTitle();

      return createTitle(assembly, title, sectionName, onlytitle);
   }

   /**
    * Covert title cell of TitledVSAssembly to report textbox element.
    */
   private Rectangle createTitle(VSAssembly assembly, String title,
                                 String sectionName)
   {
      return createTitle(assembly, title, sectionName, false);
   }

   /**
    * Covert title cell of TitledVSAssembly to report textbox element.
    */
   private Rectangle createTitle(VSAssembly assembly, String title,
                                 String sectionName, boolean onlyTitle)
   {
      Rectangle bounds = getPixelBounds(assembly);
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      final int titleH = getTitleHeight(assembly, onlyTitle);
      Rectangle titlebounds = new Rectangle(bounds.x, bounds.y, bounds.width, titleH);
      DefaultTextLens textlens = new DefaultTextLens(title);
      TextBoxElementDef textbox = new TextBoxElementDef(report, textlens);

      VSCompositeFormat objfmt = info.getFormat();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat detailfmt = null;

      if(finfo != null) {
         detailfmt = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      applyFormat(textbox, objfmt, detailfmt, info, true);

      if(assembly instanceof CompoundVSAssembly) {
         textbox.setBackground(Color.WHITE);
         textbox.setZIndex(assembly.getZIndex() + 1);

         Font fn = textbox.getFont();
         int titlew = (int) Common.stringWidth(textbox.getText(), fn) + 3;

         if(titlew < bounds.width - 5) {
            titlebounds.x += 3;
            titlebounds.width = titlew;
         }
      }
      else {
         textbox.setZIndex(assembly.getZIndex());
      }

      if(assembly instanceof RadioButtonVSAssembly || assembly instanceof CheckBoxVSAssembly) {
         textbox.setBackground(null);
         textbox.setBorders(new Insets(0, 0, 0, 0));
         textbox.setBorder(StyleConstants.NO_BORDER);
      }

      addElement0(titlebounds, textbox, sectionName);
      // minus 1 to make sure title and content have no blank space.
      int y = bounds.y + titleH - 1;

      return new Rectangle(bounds.x, y, bounds.width, bounds.height - titleH);
   }

   private int getTitleHeight(VSAssembly assembly, boolean onlyTitle) {
      Rectangle bounds = getPixelBounds(assembly);
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      int titleH = Math.round(AssetUtil.defh * scalefont);

      if(info instanceof TitledVSAssemblyInfo) {
         TitledVSAssemblyInfo tinfo = (TitledVSAssemblyInfo) info;
         titleH = Math.round(tinfo.getTitleHeight() * scalefont);
      }

      // title height shouldn't be bigger than the assembly height, and
      // minus 5 pixel to make sure at least have space for content box.
      int contentGap = onlyTitle ? 0 : 5;
      titleH = Math.min(titleH, bounds.height - contentGap);

      return titleH;
   }

   private Rectangle subtractHiddenTitleFromBounds(VSAssembly assembly, boolean onlyTitle) {
      final Rectangle oldBounds = getPixelBounds(assembly);
      final int titleHeight = getTitleHeight(assembly, onlyTitle);

      final Rectangle newBounds = new Rectangle(oldBounds);
      newBounds.height -= titleHeight;

      return newBounds;
   }

   /**
    * Get the region table lens.
    * @param data the specified table lens.
    * @param table the specified table data assembly.
    */
   public VSTableLens getRegionTableLens(VSTableLens data,
                                         TableDataVSAssembly table)
   {
      if(data == null) {
         return data;
      }

      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getVSAssemblyInfo();
      data.initTableGrid(info);

      int width = data.getColCount();
      data.moreRows(Integer.MAX_VALUE);
      int height = data.getRowCount();

      HighlightTableLens hlens = (HighlightTableLens) Util.getNestedTable(
         data, HighlightTableLens.class);

      if(hlens != null) {
         hlens.setQuerySandbox(box.getConditionAssetQuerySandbox(table.getViewsheet()));
      }

      return new RegionTableLens(data, height, width);
   }

   /**
    * Convert ChartVSAssembly to ChartElementDef and add to the reportsheet.
    */
   private void addChart(ChartVSAssembly assembly, DataSet data,
                         String sectionName) throws Exception
   {
      ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Rectangle cbounds = getPixelBounds(assembly);
      Insets padding = cinfo.getPadding();
      VSCompositeFormat format = cinfo.getFormat();
      int corner = format != null ? format.getRoundCorner() : 0;
      Insets borders = format != null && format.getBorders() != null ?
         format.getBorders() : new Insets(0, 0, 0, 0);

      TextBoxElementDef borderTextBox = addTextBoxElement0(cinfo,
                                                           new TableDataPath(-1, TableDataPath.OBJECT),
                                                           "", (Rectangle) cbounds.clone(), sectionName);
      borderTextBox.setBorders(borders);

      if(corner > 0) {
         borderTextBox.setCornerSize(new Dimension(corner, corner));
         borderTextBox.setShape(StyleConstants.BOX_ROUNDED_RECTANGLE);
      }

      float topBW = (float) Math.ceil(Common.getLineWidth(borders.top));
      float leftBW = (float) Math.ceil(Common.getLineWidth(borders.left));
      float bottomBW = (float) Math.ceil(Common.getLineWidth(borders.bottom));
      float rightBW = (float) Math.ceil(Common.getLineWidth(borders.right));

      cbounds.x += padding.left + leftBW;
      cbounds.y += padding.top + topBW;
      cbounds.width -= padding.left + (leftBW + rightBW) * 2;
      cbounds.height -= padding.top + (topBW + bottomBW) * 2;

      cinfo.setLayoutPosition(new Point(cbounds.x, cbounds.y));
      cinfo.setLayoutSize(new Dimension(cbounds.width, cbounds.height));

      if(cinfo.isTitleVisible()) {
         assembly = assembly.clone();
         cinfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();

         String title = cinfo.getTitle();
         int theight = cinfo.getTitleHeight();
         TableDataPath tpath = new TableDataPath(-1, TableDataPath.TITLE);

         Rectangle tbounds = new Rectangle(cbounds.x, cbounds.y, cbounds.width - padding.right,
                                           theight);
         TextBoxElementDef def = addTextBoxElement0(cinfo, tpath, title, tbounds, sectionName);
         def.setBorder(StyleConstants.NO_BORDER);
         VSCompositeFormat tformat = assembly.getFormatInfo().getFormat(tpath);

         if(tformat != null) {
            Insets tborders = tformat.getBorders() != null ?
               tformat.getBorders() : new Insets(0, 0, 0, 0);
            def.setBorders(tborders);
         }

         cinfo.setLayoutPosition(new Point(cbounds.x, cbounds.y + theight));
         cinfo.setLayoutSize(new Dimension(cbounds.width, cbounds.height - theight));
         cbounds = getPixelBounds(assembly);
      }

      final VGraph graph = getVGraph(assembly, data);

      ChartElementDef chartelem = new ChartElementDef(report, data);
      int pwidth = getPageContentSize().width;
      int pheight = getPageContentSize().height;
      // copy xfields and yfields, then dimension hyperlink will be processed.
      copyChartInfo(assembly, chartelem);
      chartelem.setVGraph(graph);

      // don't wrap chart if can fit on one page
      chartelem.setLayout(cbounds.x + cbounds.width >= pwidth || cbounds.height >= pheight
                          ? ReportSheet.PAINTER_BREAKABLE : ReportSheet.PAINTER_NON_BREAK);
      addElement(assembly, chartelem, sectionName);

      FormatInfo finfo = cinfo.getFormatInfo();

      if(finfo != null) {
         TableDataPath objPath = new TableDataPath(-1, TableDataPath.OBJECT);
         VSCompositeFormat fmt = finfo.getFormat(objPath, false);
         chartelem.setBorders(fmt.getBorders());
         BorderColors bcolors = fmt.getBorderColors();
         chartelem.setBorderColor(bcolors != null ? bcolors.leftColor : null);
         chartelem.setBackground(fmt.getBackground());

         // set all borders to none since borders are handled on the textbox elem
         chartelem.setBorders(new Insets(0, 0, 0, 0));
      }

      if(data instanceof VSDataSet) {
         TableLens table = ((VSDataSet) data).getTable();
         chartelem.setData(table);
      }
   }

   private VGraph getVGraph(ChartVSAssembly assembly, DataSet data) throws Exception {
      final Rectangle bounds = getPixelBounds(assembly);
      Dimension size = new Dimension(bounds.width, bounds.height);
      // Add the padding left and top to the size since they will be subtracted later
      // when calculating the graph dimensions. Since we're moving the chart by the padding left
      // and top there is no need to reduce the graph size by these dimensions too
      size.width += assembly.getChartInfo().getPadding().left;
      size.height += assembly.getChartInfo().getPadding().top;
      VGraphPair pair = box.getVGraphPair(assembly.getAbsoluteName(), true, size, true,
         scalefont, true);
      data = data == null ? pair.getData() : data;
      VGraph graph = null;

      if(data != null && !(data.getRowCount() <= 0 &&
         data.getColCount() <= 0))
      {
         graph = pair.getExpandedVGraph();
      }

      return graph;
   }

   /**
    * Copy xfields and yfields from vs chart to report chart.
    */
   private void copyChartInfo(ChartVSAssembly vchart, ChartElementDef rchart) {
      AbstractChartInfo vinfo = vchart.getVSChartInfo();
      ChartInfo rinfo = rchart.getChartInfo();
      rinfo.setChartType(vinfo.getChartType());

      // add geo ref for map chart, to avoid paint no data label on the graph.
      if(vinfo instanceof VSMapInfo) {
         BindingAttr info = rchart.getBindingAttr();

         if(info != null && info.getBindingOption() instanceof ChartOption) {
            ChartOption coption = (ChartOption) info.getBindingOption();
            rinfo = new VSMapInfo();
            coption.setChartInfo(rinfo);
            ChartRef[] rGeoRefs = ((VSMapInfo) vinfo).getRTGeoFields();

            for(int i = 0; i < rGeoRefs.length; i++) {
               ((MapInfo) rinfo).addGeoField(rGeoRefs[i]);
            }
         }
      }

      for(int i = 0; i < vinfo.getXFieldCount(); i++) {
         rinfo.addXField(vinfo.getXField(i));
      }

      for(int i = 0; i < vinfo.getYFieldCount(); i++) {
         rinfo.addYField(vinfo.getYField(i));
      }

      for(int i = 0; i < vinfo.getGroupFieldCount(); i++) {
         rinfo.addGroupField(vinfo.getGroupField(i));
      }

      // add the AestheticRef to the report chart if the AestheticRef binding measure that has calculator,
      // legend item will find field by name.
      AestheticRef colorField = vinfo.getColorField();

      if(aestheticRefHasCalc(colorField)) {
         rinfo.setColorField(colorField);
      }

      AestheticRef sizeField = vinfo.getSizeField();

      if(aestheticRefHasCalc(sizeField)) {
         rinfo.setSizeField(sizeField);
      }

      AestheticRef shapeField = vinfo.getShapeField();

      if(aestheticRefHasCalc(shapeField)) {
         rinfo.setShapeField(shapeField);
      }

      AestheticRef textField = vinfo.getTextField();

      if(aestheticRefHasCalc(textField)) {
         rinfo.setTextField(textField);
      }
   }

   private boolean aestheticRefHasCalc(AestheticRef aestheticRef) {
      return aestheticRef != null && aestheticRef.getRTDataRef() != null && aestheticRef.getRTDataRef() instanceof ChartAggregateRef &&
         ((ChartAggregateRef) aestheticRef.getRTDataRef()).getCalculator() != null;
   }

   /**
    * Convert VSSelectionList to TextBoxElement and add to the report.
    */
   private void addSelectionList(SelectionListVSAssembly assembly,
                                 String sectionName)
   {
      if(!isInSelectionContainer(assembly)) {
         SelectionListVSAssemblyInfo info =
            (SelectionListVSAssemblyInfo)assembly.getInfo();
         Rectangle bounds = getPixelBounds(assembly);
         String text = getDisplayValue(assembly);

         if(info.isTitleVisible()) {
            boolean dropdown =
               info.getShowType() == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
            String title = info.getTitle();
            title = text.length() != 0 && dropdown ? title + "*" : title;
            boolean onlytitle = dropdown || text.length() == 0;
            bounds = createTitle(assembly, title, sectionName, onlytitle);
         }

         if(text.length() == 0) {
            return;
         }

         TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
         addTextBoxElement0(info, path, text, bounds, sectionName);
      }
   }

   /**
    * Check if the current selection is in a selection container.
    */
   private boolean isInSelectionContainer(VSAssembly assembly) {
      return assembly.getContainer() instanceof CurrentSelectionVSAssembly;
   }

   /**
    * Convert VSSelectionTree to TextBoxElement and add to the report.
    */
   private void addSelectionTree(SelectionTreeVSAssembly assembly,
                                 String sectionName)
   {
      if(isInSelectionContainer(assembly)) {
         return;
      }

      SelectionTreeVSAssemblyInfo info =
            (SelectionTreeVSAssemblyInfo) assembly.getInfo();
      Rectangle bounds = getPixelBounds(assembly);
      String text = getDisplayValue(assembly);

      if(info.isTitleVisible()) {
         boolean dropdown =
         info.getShowType() == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
         String title = info.getTitle();
         title = text.length() != 0 && dropdown ? title + "*" : title;
         boolean onlytitle = dropdown || text.length() == 0;
         bounds = createTitle(assembly, title, sectionName, onlytitle);
      }

      if(text.length() == 0) {
         return;
      }

      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      addTextBoxElement0(info, path, text, bounds, sectionName);
   }

    /**
    * Convert vs calendar to report textbox and add to fixed position in
    * the report section.
    */
   private void addCalendar(CalendarVSAssembly assembly, String sectionName) {
      CalendarVSAssemblyInfo info =
         (CalendarVSAssemblyInfo) assembly.getInfo();
      Rectangle bounds = getPixelBounds(assembly);
      // get the selected values, include title.
      String text = assembly.getDisplayValue(false);
      TableDataPath path = new TableDataPath(-1, TableDataPath.TITLE);
      addTextBoxElement0(info, path, text, bounds, sectionName);
   }

   /**
    * Convert SelectionContainer to TextBoxElement and add to the report.
    */
   private void addCurrentSelection(CurrentSelectionVSAssembly assembly,
                                    String sectionName)
   {
      Viewsheet vs = assembly.getViewsheet();
      CurrentSelectionVSAssemblyInfo info =
         (CurrentSelectionVSAssemblyInfo) assembly.getInfo();
      Rectangle bounds = getPixelBounds(assembly);

      // add text of the out selections.
      String text = "";

      // add text of the out selections.
      if(info.isShowCurrentSelection()) {
         text += getSelectionsText(vs, info.getOutSelectionNames());
      }

      String[] children = assembly.getAssemblies();

      if(!"".equals(text) && children.length != 0) {
         text += "\n";
      }

      // add text of the child selections.
      text += getSelectionsText(vs, children);

      if(info.isTitleVisible()) {
         bounds = createTitle(assembly, sectionName, "".equals(text.trim()));
      }

      if(!"".equals(text.trim())) {
         TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
         addTextBoxElement0(info, path, text, bounds, sectionName);
      }
   }

   /**
    * Return the text of the selections for selection container.
    * @param vs current viewsheet.
    * @param names the target selections of selection container.
    */
   private String getSelectionsText(Viewsheet vs, String[] names) {
      StringBuilder builder = new StringBuilder();

      for(int i = 0; i < names.length; i++) {
         VSAssembly sub = vs.getAssembly(names[i]);

         if(!(sub instanceof AbstractSelectionVSAssembly)) {
            return null;
         }

         String title = ((TitledVSAssembly) sub).getTitle();
         String value = "";

         if(sub instanceof SelectionListVSAssembly) {
            value = getDisplayValue((AbstractSelectionVSAssembly) sub);
         }

         if(sub instanceof SelectionTreeVSAssembly) {
            value = getDisplayValue((AbstractSelectionVSAssembly) sub);
         }

         if(sub instanceof TimeSliderVSAssembly ||
            sub instanceof CalendarVSAssembly)
         {
            value = ((AbstractSelectionVSAssembly) sub).getDisplayValue(true, true);
         }

         if(i != 0) {
            builder.append("\n");
         }

         builder.append(title + " : " + (value == null ? "" : value));
      }

      return builder.toString();
   }

   /**
    * Get display value.
    * @return the string to represent the selected value.
    */
   public String getDisplayValue(AbstractSelectionVSAssembly assembly) {
      String value = assembly.getDisplayValue(true, ", ");
      return value == null ? Catalog.getCatalog().getString("(All Selected)") : value.trim();
   }

   /**
    * Create a TextBoxElement and add to fixed position in the report section.
    * @param assembly textbox elem should use same bounds with this assembly.
    * @param text content of the textbox element.
    * @param sectionName add the textbox element to this section.
    */
   private void addTextBoxElement(VSAssembly assembly, String text,
                                  String sectionName)
   {
      if(text == null || "".equals(text.trim())) {
         return;
      }

      Rectangle bounds = getPixelBounds(assembly);
      addTextBoxElement0((VSAssemblyInfo) assembly.getInfo(), null, text,
         bounds, sectionName);
   }

   /**
    * Create a TextBoxElement and add to fixed position in the report section.
    * @param info info of the assembly will need to convert to report element.
    * @param path datapath will used to get the right format.
    * @param text content of the textbox element.
    * @param bounds textbox elem will be add to target section with this bounds.
    * @param sectionName add the textbox element to this section.
    */
    private TextBoxElementDef addTextBoxElement0(VSAssemblyInfo info, TableDataPath path,
      String text, Rectangle bounds, String sectionName)
   {
      DefaultTextLens textlens = new DefaultTextLens(text);
      TextBoxElementDef textbox = new TextBoxElementDef(report, textlens);

      VSCompositeFormat objfmt = info.getFormat();
      VSCompositeFormat detailfmt = null;
      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null && path != null &&
         path.getType() != TableDataPath.OBJECT)
      {
         detailfmt = finfo.getFormat(path);
      }

      boolean isTitle = path != null && path.getType() == TableDataPath.TITLE;
      applyFormat(textbox, objfmt, detailfmt, info, isTitle);

      if(info instanceof TextVSAssemblyInfo) {
         VSAssembly assembly = info.getViewsheet().getAssembly(
            info.getName());
         processHyperlink(assembly, textbox);
      }

      textbox.setZIndex(info.getZIndex());
      addElement0(bounds, textbox, sectionName);
      return textbox;
   }

   /**
    * Apply format for the report element.
    * @param elem target report element.
    * @param objfmt format of the vs object.
    * @param detailfmt format of a target path.
    * @param info the assembly info identifying the assembly
    * @param isTitle specifies whether this is a title format
    */
   private void applyFormat(TextBoxElementDef elem, VSCompositeFormat objfmt,
      VSCompositeFormat detailfmt, VSAssemblyInfo info, boolean isTitle)
   {
      Insets borders = null;
      Color bcolor = null;
      Color bgColor = null;
      Color fgColor = null;
      Font font = null;

      Insets detailborders = detailfmt != null ? detailfmt.getBorders() : null;
      Insets objborders = objfmt != null ? objfmt.getBorders() : null;

      // if title, we should use the border for
      if(isTitle) {
         borders = getTitleBorders(detailborders, objborders);
      }
      else {
         borders = detailborders != null ? detailborders : objborders;
      }

      // report textbox share same border color for all the borders, so we
      // just use left border color.
      if(detailfmt != null && detailfmt.getBorderColors() != null) {
         bcolor = detailfmt.getBorderColors().leftColor;
      }
      else if(objfmt != null && objfmt.getBorderColors() != null) {
         bcolor = objfmt.getBorderColors().leftColor;
      }

      if(detailfmt != null && detailfmt.getFont() != null) {
         font = detailfmt.getFont();
      }
      else if(objfmt != null && objfmt.getFont() != null) {
         font = objfmt.getFont();
      }

      if(detailfmt != null && detailfmt.getBackground() != null) {
         bgColor = detailfmt.getBackground();
      }
      else if(objfmt != null && objfmt.getBackground() != null) {
         bgColor = objfmt.getBackground();
      }

      if(detailfmt != null && detailfmt.getForeground() != null) {
         fgColor = detailfmt.getForeground();
      }
      else if(objfmt != null && objfmt.getForeground() != null) {
         fgColor = objfmt.getForeground();
      }

      // set default borders except for text and group container.
      if(!isTitle && !(info instanceof TextVSAssemblyInfo ||
                       info instanceof ChartVSAssemblyInfo ||
                       info instanceof GroupContainerVSAssemblyInfo ||
                       info instanceof SelectionListVSAssemblyInfo ||
                       info instanceof SelectionTreeVSAssemblyInfo ||
                       info instanceof TimeSliderVSAssemblyInfo))
      {
         if(borders == null) {
            borders = new Insets(StyleConstants.THIN_LINE,
               StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
               StyleConstants.THIN_LINE);
         }
         else {
            borders.top = borders.top == StyleConstants.NO_BORDER ?
               StyleConstants.THIN_LINE : borders.top;
            borders.left = borders.left == StyleConstants.NO_BORDER ?
               StyleConstants.THIN_LINE : borders.left;
            borders.right = borders.right == StyleConstants.NO_BORDER ?
               StyleConstants.THIN_LINE : borders.right;
            borders.bottom = borders.bottom == StyleConstants.NO_BORDER ?
               StyleConstants.THIN_LINE : borders.bottom;
         }
      }

      int align = !isTitle && info instanceof SelectionBaseVSAssemblyInfo ? objfmt.getAlignment() :
         detailfmt != null ? detailfmt.getAlignment() : objfmt.getAlignment();
      elem.setTextAlignment(align);
      elem.setBorders(borders);
      elem.setBorderColor(bcolor);
      elem.setFont(font);
      elem.setBackground(bgColor);
      elem.setForeground(fgColor);

      // if borders is null, should set border to no border.
      if(borders == null || borders.top == 0 && borders.left == 0 && borders.bottom == 0 &&
         borders.right == 0)
      {
         elem.setBorder(StyleConstants.NO_BORDER);
      }
   }

   /**
    * Get the title borders by combining use of the detail borders and
    * object borders.
    */
   private Insets getTitleBorders(Insets detailBorders, Insets objBorders) {
      if(detailBorders == null) {
         return objBorders;
      }

      if(objBorders == null) {
         return detailBorders;
      }

      int top = getDisplayBorder(detailBorders.top, objBorders.top);
      int left = getDisplayBorder(detailBorders.left, objBorders.left);
      int right = getDisplayBorder(detailBorders.right, objBorders.right);
      int bottom = detailBorders.bottom;

      return new Insets(top, left, bottom, right);
   }

   private int getDisplayBorder(int border0, int border1) {
      int p0 = getBorderPriority(border0);
      int p1 = getBorderPriority(border1);

      return p0 > p1 ? border0 : border1;
   }

   /**
    * Get the target border's priority.
    * Higher priority border will be displayed, because higher priority border
    * with a wider width and will conver the lower priority border.
    */
   private int getBorderPriority(int border) {
      switch(border) {
      case StyleConstants.NO_BORDER:
         return -1;
      case StyleConstants.LARGE_DASH:
         return 0;
      case StyleConstants.MEDIUM_DASH:
         return 1;
      case StyleConstants.DASH_LINE:
         return 2;
      case StyleConstants.DOT_LINE:
         return 3;
      case StyleConstants.THIN_LINE:
         return 4;
      case StyleConstants.DOUBLE_LINE:
         return 5;
      case StyleConstants.MEDIUM_LINE:
         return 6;
      case StyleConstants.THICK_LINE:
         return 7;
      }

      return -1;
   }

   /**
    * Convert vs radiobutton to report textbox and add to fixed position in
    * the report section.
    */
   private void addRadioButton(RadioButtonVSAssembly assembly,
                               String sectionName)
   {
      RadioButtonVSAssemblyInfo info = (RadioButtonVSAssemblyInfo) assembly.getInfo();
      Rectangle bounds = getPixelBounds(assembly);
      String text = info.getSelectedLabel();
      text = text == null ? "" : text.trim();
      int titleH = 0;

      if(info.isTitleVisible()) {
         Rectangle content = createTitle(assembly, sectionName, text.length() == 0);
         titleH = bounds.height - content.height;
         bounds.y += titleH / 3;
         bounds.height -= titleH / 3;
      }

      if("".equals(text.trim())) {
         return;
      }

      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      TextBoxElementDef textbox = addTextBoxElement0(info, path, text, bounds, sectionName);

      if(textbox.getBorderColor() == null) {
         textbox.setBorderColor(new Color(0xc8c8c8));
      }

      if(titleH > 0) {
         textbox.setPadding(new Insets(titleH / 3, 0, 0, 0));
      }
   }

   /**
    * Convert vs checkbox to report textbox and add to fixed position in
    * the report section.
    */
   private void addCheckBox(CheckBoxVSAssembly assembly, String sectionName) {
      CheckBoxVSAssemblyInfo cinfo = (CheckBoxVSAssemblyInfo) assembly.getInfo();
      Rectangle bounds = getPixelBounds(assembly);
      String[] selectedLabels = cinfo.getSelectedLabels();
      boolean noselected = selectedLabels == null || selectedLabels.length == 0;
      int titleH = 0;

      if(cinfo.isTitleVisible()) {
         Rectangle content = createTitle(assembly, sectionName, noselected);
         titleH = bounds.height - content.height;
         bounds.y += titleH / 3;
         bounds.height -= titleH / 3;
      }

      if(noselected) {
         return;
      }

      int count = 0;
      StringBuilder text = new StringBuilder();
      String[] labels = cinfo.getLabels();

      for(int i = 0; i < labels.length; i++) {
         if(Tool.contains(selectedLabels, labels[i])) {
            text.append(labels[i]);

            if(++count != selectedLabels.length) {
               text.append(", ");
            }
         }
      }

      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      TextBoxElementDef textbox = addTextBoxElement0(cinfo, path, text.toString(), bounds, sectionName);

      if(textbox.getBorderColor() == null) {
         textbox.setBorderColor(new Color(0xc8c8c8));
      }

      if(titleH > 0) {
         textbox.setPadding(new Insets(titleH / 3, 0, 0, 0));
      }
   }

   /**
    * Convert vs text to report text and add to fixed position in
    * the report section.
    */
   private void addText(TextVSAssembly assembly, String sectionName) {
      String text = assembly.getText();

      if(text == null || "".equals(text.trim())) {
         return;
      }

      TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) assembly.getInfo();
      VSCompositeFormat fmt = tinfo.getFormat().clone();

      if(tinfo.getHighlightForeground() != null) {
         fmt.getUserDefinedFormat().setForeground(
            tinfo.getHighlightForeground());
      }

      if(tinfo.getHighlightBackground() != null) {
         fmt.getUserDefinedFormat().setBackground(
            tinfo.getHighlightBackground());
      }

      if(tinfo.getHighlightFont() != null) {
         fmt.getUserDefinedFormat().setFont(tinfo.getHighlightFont());
      }

      DefaultTextLens textlens = new DefaultTextLens(text);
      TextBoxElementDef textbox = new TextBoxElementDef(report, textlens);

      int border = StyleConstants.NO_BORDER;
      Color bcolor = null;

      if(fmt.getBorders() != null) {
         border = fmt.getBorders().left;
      }

      if(fmt.getBorderColors() != null) {
         bcolor = fmt.getBorderColors().leftColor;
      }

      textbox.setBorder(border);
      textbox.setBorderColor(bcolor);
      textbox.setFont(fmt.getFont());
      textbox.setBackground(fmt.getBackground());
      textbox.setForeground(fmt.getForeground());
      textbox.setTextAlignment(fmt.getAlignment());
      textbox.setZIndex(assembly.getZIndex());
      textbox.setPadding(tinfo.getPadding());
      PresenterRef presenter  = fmt.getPresenter();

      if(presenter != null) {
         textbox.setPresenter(presenter);
         textbox.setData(assembly.getValue());
      }

      processHyperlink(assembly, textbox);
      Rectangle bounds = getPixelBounds(assembly);
      addElement0(bounds, textbox, sectionName);
   }

   /**
    * Convert vs image to report image and add to fixed position in
    * the report section.
    */
   private void addImage(ImageVSAssembly assembly, String sectionName) {
      Viewsheet vs = assembly.getViewsheet();
      VSImage obj = new VSImage(vs);
      ImageVSAssemblyInfo imgInfo = (ImageVSAssemblyInfo) assembly.getInfo();


      if(imgInfo.getImage() != null) {
         obj.setAssemblyInfo(imgInfo);
         String path = imgInfo.getImage();
         boolean isSVG = path.toLowerCase().endsWith(".svg");
         ImageElementDef imageElem = new ImageElementDef(report);

         if(isSVG && (path.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE) ||
                      path.startsWith(ImageVSAssemblyInfo.SKIN_IMAGE) ||
                      path.startsWith(ImageVSAssemblyInfo.SERVER_IMAGE)))
         {
            try {
               ImageLocation iloc = new ImageLocation(".");
               File tempFile = File.createTempFile("vsreport", ".svg");
               iloc.setPath(tempFile.getAbsolutePath());
               MetaImage image = new MetaImage(iloc);
               imageElem.setImage(image);
               byte[] svg = null;

               if(path.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
                  svg = vs.getUploadedImageBytes(
                     path.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length()));
               }
               else if(path.startsWith(ImageVSAssemblyInfo.SKIN_IMAGE)) {
                  svg = vs.getUploadedImageBytes(
                     path.substring(ImageVSAssemblyInfo.SKIN_IMAGE.length()));
               }
               else if(path.startsWith(ImageVSAssemblyInfo.SERVER_IMAGE)) {
                  String name = path.substring(ImageVSAssemblyInfo.SERVER_IMAGE.length());
                  final String dir = SreeEnv.getProperty("html.image.directory");

                  if(!Tool.isEmptyString(dir)) {
                     final String imagePath =
                        FileSystemService.getInstance().getPath(dir, name).toString();

                     try(final InputStream stream =
                            DataSpace.getDataSpace().getInputStream(null, imagePath))
                     {
                        svg = new byte[stream.available()];
                        stream.read(svg);
                     }
                  }
               }

               try(FileOutputStream output = new FileOutputStream(tempFile)) {
                  if(svg != null) {
                     output.write(svg);
                  }
               }

               FileSystemService.getInstance().remove(tempFile, 10 * 60000);
            }
            catch(Exception ex) {
               LOG.debug("Failed to create temp file: " + ex, ex);
            }
         }
         else {
            Image rimg = VSUtil.getVSImage(imgInfo.getRawImage(),
                                           imgInfo.getImage(), vs,
                                           obj.getContentWidth(),
                                           obj.getContentHeight(),
                                           imgInfo.getFormat(),
                                           new VSPortalHelper());

            if(rimg == null) {
               rimg = Tool.getImage(null,
                                    "/inetsoft/report/images/emptyimage.gif");
               Tool.waitForImage(rimg);
            }

            obj.setRawImage(rimg);

            BufferedImage image =  obj.getContentImage();
            imageElem.setImage(image);
         }

         processHyperlink(assembly, imageElem);
         addElement(assembly, imageElem, sectionName);
      }
   }

   /**
    * Convert vs imageable assembly to report painter element and add to
    * fixed position in the report section.
    */
   private void addImageElement(VSAssembly assembly, String sectionName) {
      try {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();
         Viewsheet vs = info.getViewsheet();
         int type = assembly.getAssemblyType();
         VSObject obj = null;

         switch(type) {
         case AbstractSheet.GAUGE_ASSET:
            obj = VSGauge.getGauge(((GaugeVSAssemblyInfo) info).getFace());
            break;
         case AbstractSheet.CYLINDER_ASSET:
            obj = VSCylinder.getCylinder(((CylinderVSAssemblyInfo) info).
               getFace());
            break;
         case AbstractSheet.THERMOMETER_ASSET:
            obj = VSThermometer.getThermometer(((ThermometerVSAssemblyInfo)
               info).getFace());
            break;
         case AbstractSheet.SLIDING_SCALE_ASSET:
            obj = VSSlidingScale.getSlidingScale(
               ((SlidingScaleVSAssemblyInfo) info).getFace());
            break;
         case AbstractSheet.TAB_ASSET:
            while(vs.isEmbedded()) {
               vs = vs.getViewsheet();
            }

            VSUtil.fixSelected((TabVSAssemblyInfo) info, vs, true);
            obj = new VSTab(vs);
            break;
         }

         BufferedImage img = null;

         if(obj != null) {
            obj.setViewsheet(vs);
            obj.setTheme(new FlexTheme(PortalThemesManager.getColorTheme()));
            obj.setAssemblyInfo(info);

            if(obj instanceof VSFloatable) {
               Rectangle bounds = getPixelBounds(assembly);

               if(obj instanceof VSGauge) {
                  ((VSGauge) obj).setDrawbg(false);
               }

               Dimension size = new Dimension(bounds.width, bounds.height);
               obj.setPixelSize(size);
               img = (BufferedImage) ((VSFloatable) obj).getImage(false);
            }

            addPainterElement(img, assembly, sectionName);
         }
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to convert imageable assembly to report painter", ex);
      }
   }

   /**
    * Convert vs line to report pagelayout.line and add to fixed position
    * in the report.
    */
   private void addLine(LineVSAssembly assembly, String sectionName) {
      LineVSAssemblyInfo info = (LineVSAssemblyInfo) assembly.getInfo();
      int beginArraw = info.getBeginArrowStyle();
      int endArraw = info.getEndArrowStyle();
      Point pos = info.getLayoutPosition();
      Dimension size = info.getLayoutSize();
      Point start = info.getStartPos();
      Point end = info.getEndPos();
      double x1 = start.x > end.x ? pos.x : pos.x + size.width;
      double y1 = start.y > end.y ? pos.y : pos.y + size.height;
      double x2 = end.x > start.x ? pos.x : pos.x + size.width;
      double y2 = end.y > start.y ? pos.y : pos.y + size.height;

      PageLayout.Line line = new PageLayout.Line(x1, y1, x2, y2);
      line.setColor(info.getFormat().getForeground());
      line.setStyle(info.getLineStyle());
      int arrowLoc = -1;

      if(beginArraw != StyleConstants.NO_BORDER &&
         endArraw != StyleConstants.NO_BORDER)
      {
         arrowLoc = PageLayout.Line.ALL;
      }
      else if(beginArraw != StyleConstants.NO_BORDER &&
         endArraw == StyleConstants.NO_BORDER)
      {
         arrowLoc = PageLayout.Line.END;
      }
      else if(beginArraw == StyleConstants.NO_BORDER &&
         endArraw != StyleConstants.NO_BORDER)
      {
         arrowLoc = PageLayout.Line.START;
      }

      line.setArrow(arrowLoc != -1);
      line.setArrowLocation(arrowLoc);
      line.setZIndex(assembly.getZIndex());
      addShape(line, sectionName);
   }

   /**
    * Convert vs rectangle to report pagelayout.rectangle and add to fixed
    * position in the report.
    */
   private void addRectangle(RectangleVSAssembly assembly, String sectionName) {
      RectangleVSAssemblyInfo info =
         (RectangleVSAssemblyInfo) assembly.getInfo();
      Dimension size = info.getLayoutSize();
      Point pos = info.getLayoutPosition();

      PageLayout.Rectangle rect =
      	new PageLayout.Rectangle(pos.x, pos.y, size.width, size.height);
      rect.setColor(info.getFormat().getForeground());
      GradientColor gradient = info.getFormat().getGradientColor();

      if(gradient != null && gradient.getColors() != null && gradient.getColors().length > 0) {
         int alpha = info.getFormat().getAlpha();
         gradient.setAlpha(alpha);

         if(gradient.getColors().length == 1 && gradient.getColors()[0] != null) {
            try {
               Color color = GraphUtil.parseColor(gradient.getColors()[0].getColor());
               rect.setFillColor(color);
            }
            catch(Exception e) {
               rect.setFillColor(Color.WHITE);
            }
         }
         else {
            rect.setFillColor(gradient.getPaint(size.width, size.height));
         }
      }
      else {
         rect.setFillColor(info.getFormat().getBackground());
      }

      rect.setStyle(info.getLineStyle());
      rect.setZIndex(assembly.getZIndex());
      addShape(rect, sectionName);
   }

   /**
    * Convert vs oval to report pagelayout.oval and add to fixed position
    * in the report.
    */
   private void addOval(OvalVSAssembly assembly, String sectionName) {
      OvalVSAssemblyInfo info = (OvalVSAssemblyInfo) assembly.getInfo();
      Point pos = info.getLayoutPosition();
      Dimension size = info.getLayoutSize();
      PageLayout.Oval oval =
         new PageLayout.Oval(pos.x, pos.y, size.width, size.height);
      GradientColor gradient = info.getFormat().getGradientColor();

      if(gradient != null && gradient.getColors() != null && gradient.getColors().length > 0) {
         gradient.setAlpha(info.getFormat().getAlpha());

         if(gradient.getColors().length == 1 && gradient.getColors()[0] != null) {
            try {
               Color color = GraphUtil.parseColor(gradient.getColors()[0].getColor());
               oval.setFillColor(color);
            }
            catch(Exception e) {
               oval.setFillColor(Color.WHITE);
            }
         }
         else {
            oval.setFillColor(gradient.getPaint(size.width, size.height));
         }
      }
      else {
         oval.setFillColor(info.getFormat().getBackground());
      }

      oval.setColor(info.getFormat().getForeground());
      oval.setStyle(info.getLineStyle());
      oval.setZIndex(assembly.getZIndex());
      addShape(oval, sectionName);
   }

   /**
    * Add target vs assembly's hyperlink to target report element.
    */
   private void processHyperlink(VSAssembly assembly, BaseElement elem) {
      if(assembly instanceof OutputVSAssembly) {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();
         Hyperlink link = info.getHyperlink();

         if(link == null) {
            return;
         }

         Hyperlink.Ref ref = info.getHyperlinkRef();
         Enumeration<String> names = ref.getParameterNames();

         while(names.hasMoreElements()) {
            String name = names.nextElement();
            Object field = ref.getParameter(name);
            link.setParameterType(name, Tool.getDataType(field));
            link.setParameterField(name, field + "");
         }

         ((PainterElementDef) elem).setHyperlink(link);
      }
   }

   private void addShape(PageLayout.Shape shape, String sectionName) {
      Rectangle sbounds = getSectionBounds(sectionName);

      if(sbounds != null) {
         if(shape instanceof PageLayout.Line) {
            PageLayout.Line line = (PageLayout.Line) shape;
            line.setX1(line.getX1() - sbounds.x);
            line.setX2(line.getX2() - sbounds.x);
            line.setY1(line.getY1() - sbounds.y);
            line.setY2(line.getY2() - sbounds.y);
         }
         else if(shape instanceof PageLayout.Rectangle) {
            PageLayout.Rectangle rect = (PageLayout.Rectangle) shape;
            rect.setX(rect.getX() - sbounds.x);
            rect.setY(rect.getY() - sbounds.y);
         }
         else if(shape instanceof PageLayout.Oval) {
            PageLayout.Oval oval = (PageLayout.Oval) shape;
            oval.setX(oval.getX() - sbounds.x);
            oval.setY(oval.getY() - sbounds.y);
         }
      }

      SectionElementDef section =
         (SectionElementDef) report.getElement(sectionName);
      getSectionContent(section).addShape(shape);
   }

   /**
    * Create a painter element and add to fixed position in the report section.
    */
   private void addPainterElement(BufferedImage image, VSAssembly assembly,
                                  String sectionname)
   {
      ImagePainter painter = new ImagePainter(image);
      PainterElementDef painterElem = new PainterElementDef(report, painter);
      processHyperlink(assembly, painterElem);
      addElement(assembly, painterElem, sectionname);
   }

   /**
    * Convert the tab assembly to report element and add to current report.
    */
   private void addTab(TabVSAssembly assembly, String sectionName) {
      Viewsheet vs = assembly.getViewsheet();

      while(vs.isEmbedded()) {
         vs = vs.getViewsheet();
      }

      TabVSAssemblyInfo info = (TabVSAssemblyInfo) assembly.getInfo();
      VSUtil.fixSelected(info, vs, true);
      // convert tabbar to image
      addImageElement(assembly, sectionName);

      String selected = info.getSelected();
      VSAssembly selAssembly = assembly.getViewsheet().
         getAssembly(selected);

      if(selAssembly instanceof Viewsheet) {
         addViewsheet((Viewsheet) selAssembly, sectionName);
      }
      else {
         convertVSAssembly(selAssembly, sectionName);
      }
   }

   private void addGroupContainer(GroupContainerVSAssembly assembly,
                                  String sectionname)
   {
      GroupContainerVSAssemblyInfo info =
         (GroupContainerVSAssemblyInfo) assembly.getInfo();
      String path = info.getBackgroundImage();

      // if have background image, then convert to report image.
      if(path != null) {
         VSGroupContainer container = new VSGroupContainer(info.getViewsheet());

         try {
            container.setTheme(
               new FlexTheme(PortalThemesManager.getColorTheme()));
         }
         catch(Exception ex) {
            LOG.error(
               "Failed to set theme for group container", ex);
         }

         container.setAssemblyInfo(info);
         Image rimg = VSUtil.getVSImage(null, path, assembly.getViewsheet(),
                                        container.getContentWidth(),
                                        container.getContentHeight(),
                                        container.getAssemblyInfo().getFormat(),
                                        new VSPortalHelper());
         container.setRawImage(rimg);

         BufferedImage image =  container.getContentImage();
         ImageElementDef imageElem = new ImageElementDef(report);
         imageElem.setImage(image);
         addElement(assembly, imageElem, sectionname);
      }
      else {
         TextBoxElementDef textbox =
            new TextBoxElementDef(report, new DefaultTextLens(""));
         VSCompositeFormat fmt = info.getFormat();

         if(fmt != null) {
            applyFormat(textbox, fmt, null, info, false);
         }

         addElement(assembly, textbox, sectionname);
      }

      String[] assemblies = assembly.getAssemblies();

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      Viewsheet vs = assembly.getViewsheet();
      Assembly[] vsasemblies = vs.getAssemblies(true, true);

      // vsasemblies is sorted by zindex, so convert container's sub assemblies
      // by the assembly order in vsasemblies.
      for(int i = 0; i < vsasemblies.length; i++) {
         if(Tool.contains(assemblies, vsasemblies[i].getName())) {
            VSAssembly sub = (VSAssembly) vsasemblies[i];
            VSAssembly container = sub.getContainer();

            // only add direct sub assemblies here, and further sub assemblies
            // will be added in the function which is used to add their
            // direct container.
            if(container == null ||
               !container.getAbsoluteName().equals(assembly.getAbsoluteName()))
            {
               continue;
            }

            convertVSAssembly(sub, sectionname);
         }
      }
   }

   /**
    * Add the viewsheet to the target section.
    */
   private void addViewsheet(Viewsheet vs, String sectionName) {
      Assembly[] assemblies = vs.getAssemblies(true, true);

      for(int i = 0; i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];
         Viewsheet vs0 = assembly.getViewsheet();

         // if current assembly is in a sub viewsheet, need to check if the
         // sub viewsheet is in a container, if yes, do not to convert it.
         // we just let the container to decide which one should be converted.
         if(!vs0.getAbsoluteName().equals(vs.getAbsoluteName()) &&
            (vs0.getContainer() != null))
         {
            continue;
         }

         if(assembly.getContainer() == null) {
            convertVSAssembly(assembly, sectionName);
         }
      }
   }

   /**
    * Check if target assembly is in a selection container.
    */
   private boolean isInCurrentSelection(VSAssembly assembly) {
      return assembly.getContainer() instanceof CurrentSelectionVSAssembly;
   }

   /**
    * Check if target assembly is annotation component.
    */
   private boolean isAnnotationComponent(VSAssembly assembly) {
      return assembly instanceof AnnotationVSAssembly
         || assembly instanceof AnnotationRectangleVSAssembly
         || assembly instanceof AnnotationLineVSAssembly;
   }

   /**
    * Check if target assembly is in a selection container.
    */
   private boolean isInContainer(VSAssembly assembly) {
      VSAssembly container = assembly.getContainer();

      if(container != null) {
         return true;
      }

      Viewsheet vs = assembly.getViewsheet();

      if(vs.isEmbedded()) {
         return isInContainer(vs);
      }

      return false;
   }

   /**
    * Get the top container of the target assembly.
    */
   private VSAssembly getTopContainer(VSAssembly assembly) {
      Viewsheet vs = assembly.getViewsheet();

      if(vs != null && vs.isEmbedded()) {
         return getTopContainer(vs);
      }

      VSAssembly topContainer = null;
      VSAssembly container = assembly.getContainer();

      while(container != null) {
         topContainer = container;
         container = container.getContainer();
      }

      return topContainer;
   }

  /**
   * Sort the paintables in the page by zindex, and set the sorted paintable
   * array to the page, then paintables in printlayout mode will be painted
   * by zindex.
   */
   public static void sortPaintableByZIndex(StylePage page) {
      if(page.getPaintableCount() == 0) {
         return;
      }

      Vector paintables = null;

      try {
         Field field = StylePage.class.getDeclaredField("items");
         field.setAccessible(true);
         Object obj = field.get(page);
         paintables = obj != null ? (Vector) obj : new Vector();
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to get paintable vector from the stylepage", ex);
      }

      if(paintables == null || paintables.size() == 0) {
         return;
      }

      Object[] parr = paintables.toArray();

      Arrays.sort(parr, new Comparator() {
         @Override
         public int compare(Object obj0, Object obj1) {
            if(obj0 instanceof Paintable && obj1 instanceof Paintable) {
               Paintable p0 = (Paintable) obj0;
               Paintable p1 = (Paintable) obj1;

               int idx0 = getPaintableZindex(p0);
               int idx1 = getPaintableZindex(p1);

               if(idx0 != idx1) {
                  return idx0 - idx1;
               }
            }

            return 0;
         }

         private int getPaintableZindex(Paintable pt) {
            if(pt instanceof ShapePaintable) {
               return ((ShapePaintable) pt).getShape().getZIndex();
            }

            BaseElement elem = (BaseElement) pt.getElement();
            return elem == null ? 0 : elem.getZIndex();
         }
      });

      page.setSortedPaintables(parr);
   }

   public static void addTempLayout(String vid, PrintLayout layout) {
      tempLayouts.put(vid, layout);
   }

   public static void removeTempLayout(String vid) {
      tempLayouts.remove(vid);
   }

   public static PrintLayout getTempLayout(String vid) {
      return tempLayouts.get(vid);
   }

   // methods for working of sections in report that are used to hold elements

   private Rectangle getSectionBounds(String name) {
      for(LayoutSection layout : contentSections) {
         if(name.equals(layout.section.getID())) {
            return layout.bounds;
         }
      }

      return null;
   }

   private void addPageBreakElement() {
      PageBreakElementDef pageBreak = new PageBreakElementDef(report);
      report.addElement(0, 0, pageBreak);
   }

   private void addSection(SectionElementDef section, Rectangle bounds) {
      contentSections.add(new LayoutSection(section, bounds));
      report.addElement(0, 0, section);
   }

   private void setSectionBounds(String name, int height) {
      for(LayoutSection layout : contentSections) {
         if(name.equals(layout.section.getID())) {
            getSectionContent(layout.section).setHeight(height);
            layout.bounds.height = height;
         }
      }
   }

   private static class LayoutSection {
      public LayoutSection(SectionElementDef section, Rectangle bounds) {
         this.section = section;
         this.bounds = bounds;
      }

      SectionElementDef section;
      Rectangle bounds;
   }

   public static Dimension getPrintPageSize(PrintLayout layout) {
      if(layout == null || layout.getPrintInfo() == null) {
         return null;
      }

      Margin margin = layout.getPrintInfo().getMargin();

      if(margin == null) {
         margin = new Margin();
      }

      Dimension pageSize = new Dimension();
      boolean horizontal = layout.isHorizontalScreen();
      String unit = layout.getPrintInfo().getUnit();
      double width = getPLayoutSize(layout.getPrintInfo().getSize().getWidth(), unit);
      double height = getPLayoutSize(layout.getPrintInfo().getSize().getHeight(), unit);
      double top = getPLayoutSize(margin.top, "inches");
      double left = getPLayoutSize(margin.left, "inches");
      double right = getPLayoutSize(margin.right, "inches");
      double bottom = getPLayoutSize(margin.bottom, "inches");

      width -= !horizontal ? left + right : top + bottom;
      height -= !horizontal ? top + bottom : left + right;

      if(horizontal) {
         pageSize.setSize(height, width);
      }
      else {
         pageSize.setSize(width, height);
      }

      return pageSize;
   }

   private static double getPLayoutSize(double asize, String unit) {
      double psize = asize;

      switch(unit) {
         case "inches":
            psize = asize * INCH_POINT;
            break;
         case "mm":
            psize = asize / INCH_MM * INCH_POINT;
            break;
         default:
            break;
      }

      return psize;
   }
   private int zindex = 0;
   private float scalefont = 1;
   private PrintLayout playout = null;
   private ViewsheetSandbox box = null;
   private TabularSheet report = new TabularSheet();
   // sections used to hold content elements
   private List<LayoutSection> contentSections = new ArrayList<>();
   private SectionElementDef headerSection = null; // section of header.
   private SectionElementDef footerSection = null; // section of footer.
   private static HashMap<String, PrintLayout> tempLayouts = new HashMap<>();
   private static double INCH_MM = 25.4;
   private static double INCH_POINT = 72;
   private static final Logger LOG =
      LoggerFactory.getLogger(VsToReportConverter.class);
}
