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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.VSPortalHelper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.graph.EGraph;
import inetsoft.graph.geo.service.WebMapLimitException;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.*;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.DcFormatTableLens;
import inetsoft.report.internal.table.FormatTableLens2;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.io.viewsheet.OfficeExporterFactory;
import inetsoft.report.io.viewsheet.excel.CSVWSExporter;
import inetsoft.report.io.viewsheet.excel.WSExporter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.MaxRowsTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.log.LogContext;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.VSExportService;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.Principal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

@Service
@ClusterProxy
public class AssemblyImageService {

   public AssemblyImageService(ViewsheetService viewsheetService,
                               BinaryTransferService binaryTransferService)
   {
      this.viewsheetService = viewsheetService;
      this.binaryTransferService = binaryTransferService;
   }

   /**
    * @param vid       The runtime viewsheet id.
    * @param aid       The asset id (name).
    * @param width     The width of the image to be returned.
    * @param height    The height of the image to be returned.
    * @param principal The user which is logged into the browser.
    * @param svg       Flag for image format. svg if true, png otherwise
    */
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ImageRenderResult downloadAssemblyImage(@ClusterProxyKey String vid, String aid, double width, double height,
                                     double maxWidth, double maxHeight, boolean svg,
                                     Principal principal)
      throws Exception
   {
      return processGetAssemblyImage(vid, aid, width, height, maxWidth, maxHeight, null,
                              0, 0, 0, principal, svg, true);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ImageRenderResult processGetAssemblyImageInHostScope(@ClusterProxyKey String vid,
                                                               String aid, double width,
                                                               double height, double maxWidth,
                                                               double maxHeight, String aname,
                                                               int index, int row, int col,
                                                               Principal principal, boolean svg,
                                                               boolean export) throws Exception
   {
      return VSUtil.globalShareVsRunInHostScope(vid, principal, () -> processGetAssemblyImage(
         vid, aid, width, height, maxWidth, maxHeight, aname, index, row, col, principal, svg,
         export));
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ImageRenderResult processGetAssemblyImage(@ClusterProxyKey String vid, String aid, double width, double height,
                                       double maxWidth, double maxHeight, String aname,
                                       int index, int row, int col, Principal principal,
                                       boolean svg, boolean export)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vid, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String sheetName = box.getAssetEntry() == null ? "" : box.getAssetEntry().getPath();
      String assemblyName = Tool.byteDecode(aid);
      AtomicReference<ImageRenderResult> imageResultsRef = new AtomicReference<>();

      try {
         GroupedThread.withGroupedThread(groupedThread -> {
            groupedThread.addRecord(LogContext.DASHBOARD.getRecord(sheetName));
            groupedThread.addRecord(LogContext.ASSEMBLY.getRecord(assemblyName));
         });

         // for Feature #26586, add ui processing time record.
         ProfileUtils.addExecutionBreakDownRecord(box.getID(),
             ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
               ImageRenderResult result = processGetAssemblyImage1(vid, aid, width, height, maxWidth, maxHeight, aname, index,
                                        row, col, principal, svg, export);
              imageResultsRef.set(result);
         });
      }
      finally {
         GroupedThread.withGroupedThread(groupedThread -> {
            groupedThread.removeRecord(LogContext.DASHBOARD.getRecord(sheetName));
            groupedThread.removeRecord(LogContext.ASSEMBLY.getRecord(assemblyName));
         });
      }

      return imageResultsRef.get();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public MessageCommand checkExporting(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      MessageCommand messageCommand = new MessageCommand();

      if("true".equals(rvs.getProperty("__EXPORTING__"))) {
         messageCommand.setType(MessageCommand.Type.WARNING);
         messageCommand.setMessage(Catalog.getCatalog(principal)
                                      .getString("viewer.viewsheet.exporting"));
      }
      else {
         messageCommand.setType(MessageCommand.Type.OK);
      }

      return messageCommand;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SheetExportResult exportViewsheetTable(@ClusterProxyKey String runtimeId, String assemblyName,
                                                    String agent, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      RuntimeWorksheet worksheet = rvs.getRuntimeWorksheet();
      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();

      if(vbox == null) {
         return null;
      }

      TableLens lens = vbox.getVSTableLens(assemblyName, false);

      if(lens == null) {
         lens = new DefaultTableLens();
      }

      Tool.localizeHeader(lens);
      List<ColumnInfo> columns = new ArrayList<>();

      String fileName = VSExportService.getViewsheetFileName(rvs.getEntry());
      fileName = Tool.normalizeFileName(fileName + "_" + assemblyName);

      VSAssembly tableAssembly = (VSAssembly) rvs.getViewsheet().getAssembly(assemblyName);

      // @by stephenwebster, For Bug #26629
      // The exportExcel method is expecting the table name to be the name of the worksheet.
      // For viewsheet table export, get the source of the table instead.
      if(tableAssembly instanceof TableDataVSAssembly &&
         !"true".equals(SreeEnv.getProperty("table.export.hiddenColumns")))
      {
         TableDataVSAssembly tassembly = (TableDataVSAssembly) tableAssembly;
         SourceInfo srcInfo = tassembly.getSourceInfo();
         TableDataVSAssemblyInfo info = tassembly.getTableDataVSAssemblyInfo();
         assemblyName = srcInfo != null ? srcInfo.getSource() : assemblyName;

         TableLens finalLens = lens;
         int[] visCols = IntStream.range(0, lens.getColCount())
            .filter(c -> info.getColumnWidth2(c, finalLens) > 0 ||
               Double.isNaN(info.getColumnWidth2(c, finalLens)))
            .toArray();

         // ignore hidden column when exporting data from a table. (56152)
         if(visCols.length != lens.getColCount()) {
            lens = new ColumnMapFilter(lens, visCols);
         }
      }

      return exportToMemory(fileName, VSUtil.getVSAssemblyBinding(assemblyName),lens, columns, worksheet);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SheetExportResult exportWorksheet(@ClusterProxyKey String wsId, String vsId, String path, String assemblyName,
                                            String tableAssemblyName, String fileNameParam, Principal principal) throws Exception{
      RuntimeWorksheet rws = viewsheetService.getWorksheet(wsId, principal);
      Worksheet worksheet = rws.getWorksheet();
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      TableLens lens = box.getTableLens(tableAssemblyName, AssetQuerySandbox.RUNTIME_MODE);
      boolean isPreviewWsTable = path.startsWith("__PREVIEW_WORKSHEET__");
      List<ColumnInfo> columns =
         box.getColumnInfos(tableAssemblyName, AssetQuerySandbox.RUNTIME_MODE);

      if(isPreviewWsTable) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
         VSAssembly assembly = rvs.getViewsheet().getAssembly(assemblyName);
         ViewsheetSandbox vsBox = rvs.getViewsheetSandbox();
         DataTableAssembly tableAssembly =
            (DataTableAssembly) worksheet.getAssembly(tableAssemblyName);
         FormatTableLens2 formatLens = null;
         boolean appliedDC = false;

         if(assembly instanceof ChartVSAssembly) {
            appliedDC = ((ChartVSAssembly) assembly).getVSChartInfo().isAppliedDateComparison();
         }

         if(appliedDC && tableAssembly != null) {
            formatLens = new DcFormatTableLens(
               new MaxRowsTableLens(lens, 5002), getDcFormat(vsBox, assemblyName));
            formatLens.addTableFormat((DataVSAssembly) assembly, tableAssembly,
                                      tableAssembly.getColumnSelection(), null, null);
         }
         else {
            formatLens = new FormatTableLens2(lens);
            applyTheShowDetailFormat(formatLens, rws.getWorksheet(), tableAssemblyName);
         }

         lens = formatLens;
      }

      String fileName = fileNameParam == null ? fileNameParam : (Tool.normalizeFileName(
         getWorksheetFileName(rws.getEntry()) + "_" + tableAssemblyName));

      return exportToMemory(fileName, tableAssemblyName, lens, columns, rws);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Dimension getChartSize(@ClusterProxyKey String id, int width, int height, String chartId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Dimension chartSize = calculateDownloadSize(width, height, rvs, chartId);

      return chartSize;
   }

   public Dimension calculateDownloadSize(int width, int height, RuntimeViewsheet rvs,
                                          String chartId)
   {
      if(width > 0 && height > 0) {
         return new Dimension(width, height);
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return new Dimension(width, height);
      }

      final String name = Tool.byteDecode(chartId);
      Assembly assembly = vs.getAssembly(name);
      Dimension assemblySize = assembly.getPixelSize();

      if(height == 0 && width == 0) {
         return new Dimension((int) assemblySize.getWidth(), (int) assemblySize.getHeight());
      }
      else {
         double ratio = assemblySize.getWidth() / assemblySize.getHeight();

         if(width == 0) {
            width = (int) (height * ratio);
         }
         else {
            height = (int) (width / ratio);
         }

         return new Dimension(width, height);
      }
   }

   private SheetExportResult exportToMemory(String fileName,
                                            String assemblyName,
                                            TableLens lens,
                                            List<ColumnInfo> columns,
                                            RuntimeWorksheet worksheet) throws Exception {
      String mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      String suffix = "xlsx";
      String format = SreeEnv.getProperty("table.export.format");
      String threshold = SreeEnv.getProperty("table.export.cell.threshold.forcetocsv");
      int cellThreshold = -1;

      if(threshold != null) {
         try {
            cellThreshold = Integer.parseInt(threshold);
         } catch (NumberFormatException ignore) {
         }
      }

      int row = (lens == null) ? 1 : lens.getRowCount();
      int col = (lens == null) ? 1 : lens.getColCount();
      WSExporter exporter;

      if((lens != null && lens.moreRows(5000) && format == null) ||
         (cellThreshold != -1 && lens.getColCount() * lens.getRowCount() > cellThreshold) ||
         "csv".equalsIgnoreCase(format)) {
         exporter = new CSVWSExporter();
         mime = "text/csv";
         suffix = "csv";
      }
      else {
         exporter = OfficeExporterFactory.getInstance().createWorksheetExporter(row, col);
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      if(lens != null) {
         exporter.prepareSheet(assemblyName, worksheet, row, col);
         Class[] colTypes = exporter.getColTypes(lens);
         HashMap<Integer, Integer> map = getColumnMap(lens, columns);
         exporter.writeTable(lens, columns, colTypes, map);
      }

      exporter.write(baos);
      return new SheetExportResult(baos.toByteArray(), fileName, mime, suffix);
   }


   private String getWorksheetFileName(AssetEntry entry) {
      return VSExportService.getFileName(entry, "ExportedWorksheet");
   }

   private DateComparisonFormat getDcFormat(ViewsheetSandbox box, String assemblyName)
      throws Exception
   {
      if(box == null) {
         return null;
      }

      VGraphPair pair = box.getVGraphPair(assemblyName);
      EGraph graph = pair.getEGraph();

      return (DateComparisonFormat)
         GraphUtil.getAllScales(graph.getCoordinate()).stream()
            .map(s -> s.getAxisSpec().getTextSpec().getFormat())
            .filter(f -> f instanceof DateComparisonFormat).findFirst().orElse(null);
   }

   /**
    * Apply the format for show worksheet preview table.
    * @param formatLens format table lens.
    * @param worksheet worksheet
    * @param assemblyName table that contains format.
    */
   private void applyTheShowDetailFormat(FormatTableLens2 formatLens, Worksheet worksheet,
                                         String assemblyName)
   {
      TableAssemblyInfo tableAssemblyInfo = null;
      TableAssembly tableAssembly = null;

      if(worksheet != null && worksheet.getAssembly(assemblyName) instanceof TableAssembly) {
         tableAssembly = (TableAssembly) worksheet.getAssembly(assemblyName);

         if(tableAssembly == null || tableAssembly.getTableInfo() == null) {
            return;
         }

         tableAssemblyInfo = tableAssembly.getTableInfo();

         if(!Tool.equals(tableAssemblyInfo.getClassName(), "WSPreviewTable")) {
            return;
         }
      }

      if(tableAssemblyInfo != null && tableAssembly != null) {
         Map<String, VSCompositeFormat> formatMap = tableAssemblyInfo.getFormatMap();

         if(formatMap == null) {
            return;
         }

         for(String key : formatMap.keySet()) {
            if(formatMap.get(key) == null) {
               continue;
            }

            formatLens.addColumnFormat(key, tableAssembly.getColumnSelection(), formatMap.get(key));
         }
      }
   }

   private HashMap<Integer, Integer> getColumnMap(TableLens lens, List<ColumnInfo> colInfo) {
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens, true);
      HashMap<Integer, Integer> map = new HashMap<>();

      for(int i = 0; i < colInfo.size(); i++) {
         ColumnInfo info = colInfo.get(i);
         String attr = "null";
         ColumnRef ref = new ColumnRef(new AttributeRef(attr));

         // avoid the value at the specified cell is null.
         if(!Tool.equals(ref, info.getColumnRef())) {
            map.put(Util.findColumn(columnIndexMap, info, lens), i);
         }
         else {
            map.put(i, i);
         }
      }

      return map;
   }

   /**
    * Get assembly image.
    */
   public BufferedImage getAssemblyImage(VSAssembly assembly)
   //, Boolean dataTip, boolean raw)
   {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Viewsheet vs = info.getViewsheet();
      int type = assembly.getAssemblyType();
      VSObject obj = null;
      BufferedImage img = null;
      boolean raw = false;

      switch(type) {
      case AbstractSheet.ANNOTATION_LINE_ASSET:
         obj = new VSLine(vs);
         break;
      case AbstractSheet.ANNOTATION_RECTANGLE_ASSET:
         obj = new VSRectangle(vs);
         break;
      case AbstractSheet.IMAGE_ASSET:
         obj = new VSImage(vs);
         ImageVSAssemblyInfo imgInfo = (ImageVSAssemblyInfo) info;
         raw = imgInfo.isTile();

         if(imgInfo.getImage() != null) {
//            obj.setDataTipView(dataTip);
            obj.setAssemblyInfo(imgInfo);
            String path = imgInfo.getImage();
            Image rimg = VSUtil.getVSImage(imgInfo.getRawImage(),
                                           path, vs,
                                           obj.getContentWidth(),
                                           obj.getContentHeight(),
                                           imgInfo.getFormat(),
                                           new VSPortalHelper());
            Image img0;

            if(rimg == null) {
               rimg = Tool.getImage(this, EMPTY_IMAGE);
               Tool.waitForImage(rimg);
            }

            if(rimg instanceof MetaImage) {
               img0 = Tool.getBufferedImage(((MetaImage) rimg).getImage());
            }
            else {
               img0 = rimg;
            }

            raw = imgInfo.isAnimateGIF();
            ((VSImage) obj).setRawImage(img0);
         }
         break;
      case AbstractSheet.GROUPCONTAINER_ASSET:
         obj = new VSGroupContainer(vs);
//         obj.setDataTipView(dataTip);
         GroupContainerVSAssemblyInfo ginfo = (GroupContainerVSAssemblyInfo) info;
         obj.setAssemblyInfo(ginfo);
         Image rimg = null;
         raw = ginfo.isTile();

         if(ginfo.getBackgroundImage() != null) {
            rimg = VSUtil.getVSImage(null, ginfo.getBackgroundImage(), vs,
                                     obj.getContentWidth(),
                                     obj.getContentHeight(),
                                     ginfo.getFormat(),
                                     new VSPortalHelper());

            if(rimg == null) {
               rimg = Tool.getImage(this, EMPTY_IMAGE);
            }

            Tool.waitForImage(rimg);
         }
         else if(obj.isDataTipView()) {
            rimg = new BufferedImage(obj.getContentWidth(),
                                     obj.getContentHeight(),
                                     BufferedImage.TYPE_4BYTE_ABGR);
         }

         if(rimg != null) {
            ((VSGroupContainer) obj).setRawImage(img = Tool.getBufferedImage(rimg));
         }

         break;
      }

      if(obj instanceof VSFloatable && (img == null || !raw)) {
         obj.setViewsheet(vs);
         obj.setAssemblyInfo(info);
         img = (BufferedImage) ((VSFloatable) obj).getImage(false);
      }

      return img;
   }

   private ImageRenderResult processGetAssemblyImage1(String vid, String aid, double width, double height,
                                         double maxWidth, double maxHeight, String aname,
                                         int index, int row, int col, Principal principal,
                                         boolean svg, boolean export)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vid, principal);

      if(rvs == null || width <= 0 || height <= 0) {
         return null;
      }

      final Viewsheet vs = rvs.getViewsheet();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return null;
      }

      final String name = Tool.byteDecode(aid);
      Assembly assembly = vs.getAssembly(name);
      BufferedImage image = null;
      boolean rawEncodePNG = false;
      byte[] buf = null;
      Graphics2D svgGraphics = null;

      try {
         if(isRawImage(assembly) && canProcessRawImage(assembly)) {
            final String path;
            // for embedded vs
            final Viewsheet vs2;

            if(assembly instanceof ImageVSAssembly) {
               path = ((ImageVSAssembly) assembly).getImage();
               vs2 = ((ImageVSAssembly) assembly).getViewsheet();
            }
            else if(assembly instanceof GroupContainerVSAssembly) {
               path = ((GroupContainerVSAssembly) assembly).getBackgroundImage();
               vs2 = ((GroupContainerVSAssembly) assembly).getViewsheet();
            }
            else {
               path = null;
               vs2 = null;
            }

            rawEncodePNG = path == null || !path.endsWith(".svg");
            buf = VSUtil.getVSImageBytes(null, path, vs2, -1, -1, null, new VSPortalHelper());
         }
         else {
            final int dpi = 72;

            if(assembly instanceof ChartVSAssembly) {
               ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
               Dimension maxSize = info.getMaxSize();
               int scale = 2;

               // if maxSize is set in info, we should use it instead of passed in values.
               // since vschart/areas uses maxSize from info to get the chart areas, if the
               // maxSize of image is different from chart area, the current chart area
               // may be using a cancelled vgraph (ViewsheetSandbox cancels previous graph
               // if the chart size is changed and a new pair is created). this problem
               // happens more easily when chart is in max-mode and browser slightly resized
               // where multiple events are sent in quick succession.
               if(maxSize == null && maxWidth > 0 && maxHeight > 0) {
                  maxSize = new Dimension((int) maxWidth, (int) maxHeight);
                  scale = 1;
               }

               // pair.getPlotGraphics() may trigger script which in turn could request data,
               // and calls a lockWrite, resulting a deadlock. Only lock getVGraphPair. (42202)
               final VGraphPair pair;
               box.lockRead();

               try {
                  pair = box.getVGraphPair(name, false, maxSize, export, 1);

                  if(pair == null || !pair.isCompleted() || pair.isCancelled() ||
                     !pair.isPlotted())
                  {
                     return null;
                  }

                  if(svg) {
                     svgGraphics = getChartSVG(aname, row, col, index, pair, box, name);

                     if(svgGraphics == null) {
                        image = getChartImage(aname, row, col, index, pair, box, name, dpi * scale);
                     }
                  }
                  else {
                     image = getChartImage(aname, row, col, index, pair, box, name, dpi * scale);
                  }
               }
               // web map limit exceeded, disable web map for 24 hours.
               catch(WebMapLimitException ex) {
                  long hours24 = System.currentTimeMillis() + 24 * 60 * 60000L;
                  SreeEnv.setProperty("webmap.suspend.until", hours24 + "");

                  box.clearGraph(name);
                  return processGetAssemblyImage1(vid, aid, width, height, maxWidth, maxHeight, aname,
                                           index, row, col, principal, svg, export);
               }
               finally {
                  box.unlockRead();
               }
            }
            // non chart
            else {
               box.lockRead();

               try {
                  if(assembly instanceof GaugeVSAssembly) {
                     buf = getGaugeSVG(assembly, (int) width, (int) height, principal);

                     if(buf == null) {
                        image = getGaugeImage(assembly, (int) width, (int) height, principal);
                     }
                  }
                  else if(assembly instanceof ThermometerVSAssembly) {
                     image = getThermometerImage(assembly, (int) width, (int) height);
                  }
                  else if(assembly instanceof CylinderVSAssembly) {
                     image = getCylinderImage(assembly, (int) width, (int) height);
                  }
                  else if(assembly instanceof SlidingScaleVSAssembly) {
                     image = getSlidingScaleImage(assembly, (int) width, (int) height);
                  }
                  else if(assembly instanceof ShapeVSAssembly ||
                     assembly instanceof ImageVSAssembly ||
                     assembly instanceof GroupContainerVSAssembly)
                  {
                     Dimension pixelSize = assembly.getPixelSize();
                     DimensionD scale = new DimensionD(width / pixelSize.width,
                                                       height / pixelSize.height);

                     if(assembly instanceof ShapeVSAssembly) {
                        ((ShapeVSAssembly) assembly).setScalingRatio(scale);
                     }
                     else if(assembly instanceof ImageVSAssembly) {
                        ((ImageVSAssembly) assembly).setScalingRatio(scale);
                     }

                     image = getAssemblyImage((VSAssembly) assembly);
                  }
               }
               finally {
                  box.unlockRead();
               }
            }

            // avoid a broken image on browser
            if(buf == null && image == null && svgGraphics == null) {
               image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
               image.setRGB(0, 0, 0xffffff);
            }

            if(image != null) {
               buf = VSUtil.getImageBytes(image, dpi);
            }
            else if(svgGraphics != null) {
               ByteArrayOutputStream out = new ByteArrayOutputStream();
               SVGSupport.getInstance().writeSVG(svgGraphics, out);
               buf = out.toByteArray();
            }
         }
      }
      catch(Exception ex) {
         rvs.setProperty("_ERR_MESSAGE_", ex.getMessage());
         throw ex;
      }

      boolean isPNG = image != null || rawEncodePNG;
      int resultWidth = (int) width;
      int resultHeight = (int) height;

      if(image != null) {
         resultWidth = image.getWidth();
         resultHeight = image.getHeight();
      }

      String key = "/" + AssemblyImageService.class.getName() + "_" + vid + "_" + aid +
         "_" + index + "_" + row + "_" + col;
      BinaryTransfer imageData = binaryTransferService.createBinaryTransfer(key);
      binaryTransferService.setData(imageData, buf);
      return new ImageRenderResult(isPNG, imageData, resultWidth, resultHeight);
   }

   public static void writeSvg(OutputStream out, Graphics2D svg) throws Exception {
      SVGSupport.getInstance().writeSVG(svg, out);
   }

   // Check if should send me raw bytes
   private boolean isRawImage(Assembly assembly) {
      if(assembly instanceof ImageVSAssembly) {
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assembly.getInfo();
         return info.isAnimateGIF() || info.getImage() != null && info.getImage().endsWith(".svg");
      }
      else if(assembly instanceof GroupContainerVSAssembly) {
         GroupContainerVSAssemblyInfo info = (GroupContainerVSAssemblyInfo) assembly.getInfo();
         return info.isAnimateGIF() || info.getBackgroundImage() != null &&
            info.getBackgroundImage().endsWith(".svg");
      }

      return false;
   }

   /**
    * Get chart image.
    */
   private Graphics2D getChartSVG(String aname, int row, int col, int index, VGraphPair pair,
                                     ViewsheetSandbox box, String name)
   {
      Graphics2D image = null;

      if(aname == null) {
         SVGSupport svgSupport = SVGSupport.getInstance();
         final Graphics2D g = svgSupport.createSVGGraphics();
         Viewsheet vs = box.getViewsheet();
         ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);
         ChartVSAssemblyInfo info = chart.getChartInfo();
         Dimension2D size = pair.getRealSizeVGraph().getSize();
         Insets padding = info.getPadding();
         padding = padding == null ? new Insets(0, 0, 0, 0) : padding;

         // drag borders and background
         int imageW = (int) size.getWidth();
         int imageH = (int) size.getHeight();
         Insets borderSizes = getBorderSizes(chart);
         double width = imageW + borderSizes.left + borderSizes.right +
            padding.left + padding.right;
         double height = imageH + borderSizes.top + borderSizes.bottom +
            padding.top + padding.bottom;
         svgSupport.setCanvasSize(g, new Dimension((int) width, (int) height));

         drawBackground(g, chart, (int) width, (int) height);
         Graphics2D g2 = (Graphics2D) g.create();
         g2.translate(padding.left, padding.top);
         pair.paintChart(g2, true);
         g2.dispose();

         g.translate(borderSizes.left, borderSizes.top);
         drawBorders(g, chart, (int) width, (int) height);
         g.dispose();

         image = g;
      }
      else if("plot_area".equals(aname)) {
         int rcnt = pair.getData().getRowCount();

         // svg is more expensive than java graphics. very large svg can also crash the
         // browser, so only only use svg when the number of points is not excessive
         if(rcnt < 10000) {
            image = pair.getPlotGraphic(row, col);
         }
      }
      else if("x_title".equals(aname)) {
         image = pair.getXTitleGraphic(row, col);
      }
      else if("x2_title".equals(aname)) {
         image = pair.getX2TitleGraphic(row, col);
      }
      else if("y_title".equals(aname)) {
         image = pair.getYTitleGraphic(row, col);
      }
      else if("y2_title".equals(aname)) {
         image = pair.getY2TitleGraphic(row, col);
      }
      else if("top_x_axis".equals(aname)) {
         image = pair.getTopXGraphic(row, col);
      }
      else if("bottom_x_axis".equals(aname)) {
         image = pair.getBottomXGraphic(row, col);
      }
      else if("left_y_axis".equals(aname)) {
         image = pair.getLeftYGraphic(row, col);
      }
      else if("right_y_axis".equals(aname)) {
         image = pair.getRightYGraphic(row, col);
      }
      else if("facetTL".equals(aname)) {
         image = pair.getFacetTLGraphic();
      }
      else if("facetTR".equals(aname)) {
         image = pair.getFacetTRGraphic();
      }
      else if("facetBL".equals(aname)) {
         image = pair.getFacetBLGraphic();
      }
      else if("facetBR".equals(aname)) {
         image = pair.getFacetBRGraphic();
      }
      else if("legend_title".equals(aname)) {
         image = pair.getLegendTitleGraphic(index, row, col);
      }
      else if("legend_content".equals(aname)) {
         image = pair.getLegendContentGraphic(index, row, col);
      }

      return image;
   }

   private BufferedImage getChartImage(String aname, int row, int col, int index,
                                       VGraphPair pair, ViewsheetSandbox box, String name,
                                       int dpi)
   {
      BufferedImage image = null;

      if(aname == null) {
         Viewsheet vs = box.getViewsheet();
         ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);
         ChartVSAssemblyInfo info = chart.getChartInfo();
         Insets padding = info.getPadding();
         padding = padding == null ? new Insets(0, 0, 0, 0) : padding;

         image = pair.getImage(true, dpi);

         // drag borders and background
         int imageW = image.getWidth();
         int imageH = image.getHeight();
         Insets borderSizes = getBorderSizes(chart);
         double width = imageW + borderSizes.left + borderSizes.right +
            padding.left + padding.right;
         double height = imageH + borderSizes.top + borderSizes.bottom +
            padding.top + padding.bottom;

         BufferedImage image2 = new BufferedImage(
            (int) width, (int) height, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g2 = (Graphics2D) image2.getGraphics();

         drawBackground(g2, chart, (int) width, (int) height);
         g2.drawImage(image, padding.left, padding.top, null);
         drawBorders(g2, chart, (int) width, (int) height);
         g2.dispose();

         image = image2;
      }
      else if("plot_area".equals(aname)) {
         image = pair.getPlotImage(row, col);
      }
      else if("plot_background".equals(aname)) {
         image = pair.getPlotBackgroundImage();
      }
      else if("x_title".equals(aname)) {
         image = pair.getXTitleImage(row, col);
      }
      else if("x2_title".equals(aname)) {
         image = pair.getX2TitleImage(row, col);
      }
      else if("y_title".equals(aname)) {
         image = pair.getYTitleImage(row, col);
      }
      else if("y2_title".equals(aname)) {
         image = pair.getY2TitleImage(row, col);
      }
      else if("top_x_axis".equals(aname)) {
         image = pair.getTopXImage(row, col);
      }
      else if("bottom_x_axis".equals(aname)) {
         image = pair.getBottomXImage(row, col);
      }
      else if("left_y_axis".equals(aname)) {
         image = pair.getLeftYImage(row, col);
      }
      else if("right_y_axis".equals(aname)) {
         image = pair.getRightYImage(row, col);
      }
      else if("facetTL".equals(aname)) {
         image = pair.getFacetTLImage();
      }
      else if("facetTR".equals(aname)) {
         image = pair.getFacetTRImage();
      }
      else if("facetBL".equals(aname)) {
         image = pair.getFacetBLImage();
      }
      else if("facetBR".equals(aname)) {
         image = pair.getFacetBRImage();
      }
      else if("legend_title".equals(aname)) {
         image = pair.getLegendTitleImage(index, row, col);
      }
      else if("legend_content".equals(aname)) {
         image = pair.getLegendContentImage(index, row, col);
      }

      return image;
   }

   private Insets getBorderSizes(ChartVSAssembly chart) {
      VSCompositeFormat format = chart.getVSAssemblyInfo().getFormat();
      Insets borders = format.getBorders();
      double top = 0, left = 0, bottom = 0, right = 0;

      if(borders != null) {
         top = Common.getLineWidth(borders.top);
         left = Common.getLineWidth(borders.left);
         bottom = Common.getLineWidth(borders.bottom);
         right = Common.getLineWidth(borders.right);
      }

      return new Insets((int) Math.ceil(top), (int) Math.ceil(left),
                        (int) Math.ceil(bottom), (int) Math.ceil(right));
   }

   private void drawBackground(Graphics2D g2, ChartVSAssembly chart, int imageW, int imageH) {
      VSCompositeFormat format = chart.getVSAssemblyInfo().getFormat();

      if(format != null && format.getBackground() != null) {
         g2.setColor(format.getBackground());
         g2.fillRect(0, 0, imageW, imageH);
      }
   }

   private void drawBorders(Graphics2D g2, ChartVSAssembly chart, int imageW, int imageH) {
      VSCompositeFormat format = chart.getVSAssemblyInfo().getFormat();
      BorderColors borderColors = format.getBorderColors();
      Insets borders = format.getBorders();

      // set the default border color
      if(borderColors == null) {
         borderColors = new BorderColors(
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR);
      }

      if(borders != null) {
         ExportUtil.drawBorders(g2, new Point(0, 0), new Dimension(imageW - 1, imageH - 1),
                                borderColors, borders, format.getRoundCorner());
      }
   }

   private VSGauge getGaugeAssembly(Assembly assembly, int width, int height)
                           // boolean isDrawbg, boolean isMobile)
   {
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getInfo();
      int id = info.getFace();

      VSGauge gauge = VSGauge.getGauge(id);

      if(gauge instanceof DefaultHalfVSGauge) {
         int maxDim = Math.max(width, height);
         int minDim = Math.min(width, height);

         if(maxDim > (minDim * 2)) {
            maxDim = minDim * 2;
         }

         width = width > height ? maxDim : minDim;
         height = height > width ? maxDim : minDim;
      }
      else if(gauge instanceof DefaultVSGauge) {
         width = Math.min(width, height);
         height = width;
      }

      assert gauge != null;
      gauge.setPixelSize(new Dimension(width, height));
      gauge.setAssemblyInfo(info);
//      gauge.setDrawbg(isDrawbg);

      VSGauge.setGaugeName(info.getName());
      VSGauge.setGaugeClass(info.getFormat().getCSSFormat().getCSSClass());
      return gauge;
   }

   /**
    * Get svg gauge.
    */
   private byte[] getGaugeSVG(Assembly assembly, int width, int height, Principal principal) throws Exception
                           // boolean isDrawbg, boolean isMobile)
   {
      VSGauge gauge = getGaugeAssembly(assembly, width, height);

      if(!(gauge instanceof DefaultVSGauge)) {
         return null;
      }

      DefaultVSGauge defaultGauge = (DefaultVSGauge) gauge;
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getInfo();

      if(principal instanceof SRPrincipal) {
         Locale locale = ((SRPrincipal) principal).getLocale();

         if(locale != null && locale != Locale.getDefault() && info.getDefaultFormat() == null) {
            String pattern = "###.####";
            DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
            decimalFormat.applyPattern(pattern);

            info.setDefaultFormat(decimalFormat);
         }
      }

      Graphics2D g = defaultGauge.getContentSvg(info.isShadow());
      // get root would include the <defs> (for gradient). it's not in the base document
      SVGSupport svgSupport = SVGSupport.getInstance();
      Element root = svgSupport.getSVGRootElement(g);
      Document doc = svgSupport.getSVGDocument(g);
      Element svg = Tool.getChildNodeByTagName(doc, "svg");

      if(svg != null) {
         svg.appendChild(root);
      }

      return svgSupport.transcodeSVGImage(doc);
   }

   /**
    * Get image gauge.
    */
   private BufferedImage getGaugeImage(Assembly assembly, int width, int height, Principal principal)
                                    // boolean isDrawbg, boolean isMobile)
   {
      VSGauge gauge = getGaugeAssembly(assembly, width, height);
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getInfo();

      if(principal instanceof SRPrincipal) {
         Locale locale = ((SRPrincipal) principal).getLocale();

         if(locale != null && locale != Locale.getDefault() && info.getDefaultFormat() == null) {
            String pattern = "###.####";
            DecimalFormat decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
            decimalFormat.applyPattern(pattern);

            info.setDefaultFormat(decimalFormat);
         }
      }

      return gauge.getContentImage();
   }

   /**
    * Get thermometer image.
    */
   private BufferedImage getThermometerImage(Assembly assembly, int width,
                                             int height)//, boolean isDrawbg,
   //boolean isMobile)
   {
      ThermometerVSAssemblyInfo info =
         (ThermometerVSAssemblyInfo) assembly.getInfo();
      int id = info.getFace();

      VSThermometer thermometer = VSThermometer.getThermometer(id);

      assert thermometer != null;
      thermometer.setPixelSize(new Dimension(width, height));
      thermometer.setAssemblyInfo(info);
//      thermometer.setDrawbg(isDrawbg);

//      return getShadowImage(thermometer.getContentImage(), info.isShadow(),
//         isMobile);
      return thermometer.getContentImage();
   }

   /**
    * Get cylinder image.
    */
   private BufferedImage getCylinderImage(Assembly assembly, int width,
                                          int height)//, boolean isDrawbg,
   //boolean isMobile)
   {
      CylinderVSAssemblyInfo info = (CylinderVSAssemblyInfo) assembly.getInfo();
      int id = info.getFace();

      VSCylinder cylinder = VSCylinder.getCylinder(id);

      assert cylinder != null;
      cylinder.setPixelSize(new Dimension(width, height));
      cylinder.setAssemblyInfo(info);
//      cylinder.setDrawbg(isDrawbg);

//      return getShadowImage(cylinder.getContentImage(), info.isShadow(),
//         isMobile);
      return cylinder.getContentImage();
   }

   /**
    * Get sliding scale image.
    */
   private BufferedImage getSlidingScaleImage(Assembly assembly, int width, int height) {
      SlidingScaleVSAssemblyInfo info =
         (SlidingScaleVSAssemblyInfo) assembly.getInfo();
      int id = info.getFace();

      VSSlidingScale slidingScale = VSSlidingScale.getSlidingScale(id);

      assert slidingScale != null;
      slidingScale.setPixelSize(new Dimension(width, height));
      slidingScale.setAssemblyInfo(info);
//      slidingScale.setDrawbg(isDrawbg);

//      return getShadowImage(slidingScale.getContentImage(), info.isShadow(),
//         isMobile);
      return slidingScale.getContentImage();
   }

   /**
    * Check whether the assembly is a raw assembly that can be processed raw.
    *
    * @param assembly the assembly to check
    *
    * @return true if the assembly is an assembly that can be processed raw, false otherwise
    */
   private boolean canProcessRawImage(Assembly assembly) {
      if(assembly instanceof ImageVSAssembly) {
         final ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) assembly.getInfo();
         final boolean svg = info.getImage() != null && info.getImage().endsWith(".svg");

         if(svg) {
            return (!info.isScaleImageValue() || info.isMaintainAspectRatioValue());
         }
         else {
            return true;
         }
      }
      else {
         return assembly instanceof GroupContainerVSAssembly;
      }
   }

   private final String EMPTY_IMAGE = "/inetsoft/report/images/emptyimage.gif";
   private final ViewsheetService viewsheetService;
   private final BinaryTransferService binaryTransferService;

   public static final class ImageRenderResult implements Serializable {
      @Serial
      private static final long serialVersionUID = 1L;
      private final boolean isPng;
      private final BinaryTransfer imageData;
      private final int width;
      private final int height;

      public ImageRenderResult(boolean isPng, BinaryTransfer imageData, int width, int height) {
         this.isPng = isPng;
         this.imageData = imageData;
         this.width = width;
         this.height = height;
      }

      public boolean isPng() {
         return isPng;
      }

      public BinaryTransfer getImageData() {
         return imageData;
      }

      public int getWidth() {
         return width;
      }

      public int getHeight() {
         return height;
      }
   }

   public static final class SheetExportResult implements Serializable {
      private final byte[] data;
      private final String fileName;
      private final String mime;
      private final String suffix;

      public SheetExportResult(byte[] data, String fileName, String mime, String suffix) {
         this.data = data;
         this.fileName = fileName;
         this.mime = mime;
         this.suffix = suffix;
      }

      public byte[] getData() {
         return data;
      }

      public String getFileName() {
         return fileName;
      }

      public String getMime() {
         return mime;
      }

      public String getSuffix() {
         return suffix;
      }
   }

}
