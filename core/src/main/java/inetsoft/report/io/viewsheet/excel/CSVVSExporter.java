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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.EncryptedCompressExporter;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class CSVVSExporter extends AbstractVSExporter implements EncryptedCompressExporter {
   public CSVVSExporter() {
   }

   public CSVVSExporter(OutputStream stream, Object extraConfig) {
      if(extraConfig instanceof CSVConfig) {
         CSVConfig config = (CSVConfig) extraConfig;
         String delimiter = config.isTabDelimited() ? "\t" : config.getDelimiter();

         if(delimiter == null) {
            delimiter = ",";
         }

         this.setDelim(delimiter);
         this.setQuote(config.getQuote());
         this.setKeepHeader(config.isKeepHeader());
         this.exportAssemblies = config.getExportAssemblies();
      }

      this.stream = stream;
   }

   @Override
   public int getFileFormatType() {
      return FileFormatInfo.EXPORT_TYPE_CSV;
   }

   @Override
   protected boolean needExport(VSAssembly assembly) {
      if(!super.needExport(assembly)) {
         return false;
      }

      return (assembly != null && assembly.isVisible() && (exportAssemblies == null ||
              exportAssemblies.size() == 0 || exportAssemblies.contains(assembly.getAbsoluteName())));
   }

   @Override
   protected void prepareSheet(Viewsheet vsheet, String sheet, ViewsheetSandbox box) throws Exception {
      super.prepareSheet(vsheet, sheet, box);
      this.currentBookmark = sheet;
   }

   @Override
   protected void writeChart(ChartVSAssembly originalAsm, ChartVSAssembly asm, VGraph graph,
                             DataSet data, BufferedImage img, boolean firstTime, boolean imgOnly)
   {
   }

   @Override
   protected void writeWarningText(Assembly[] assemblies, String warning, VSCompositeFormat format) {
   }

   @Override
   protected void writeImageAssembly(ImageVSAssembly assembly, XPortalHelper helper) {
   }

   @Override
   protected void writeTextInput(TextInputVSAssembly assembly) {
   }

   @Override
   protected void writeText(VSAssembly assembly, String txt) {
   }

   @Override
   protected void writeText(String text, Point pos, Dimension size, VSCompositeFormat format) {
   }

   @Override
   protected void writeTimeSlider(TimeSliderVSAssembly assm) {
   }

   @Override
   protected void writeCalendar(CalendarVSAssembly assm) {
   }

   @Override
   protected void writeGauge(GaugeVSAssembly assembly) {
   }

   @Override
   protected void writeThermometer(ThermometerVSAssembly assembly) {
   }

   @Override
   protected void writeCylinder(CylinderVSAssembly assembly) {
   }

   @Override
   protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
   }

   @Override
   protected void writeRadioButton(RadioButtonVSAssembly assembly) {
   }

   @Override
   protected void writeCheckBox(CheckBoxVSAssembly assembly) {
   }

   @Override
   protected void writeSlider(SliderVSAssembly assembly) {
   }

   @Override
   protected void writeSpinner(SpinnerVSAssembly assembly) {
   }

   @Override
   protected void writeComboBox(ComboBoxVSAssembly assembly) {
   }

   @Override
   protected void writeSelectionList(SelectionListVSAssembly assembly) {
   }

   @Override
   protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
   }

   @Override
   protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
      try {
         resultFiles.add(writeTableDataAssembly(assembly, lens));
      } catch (IOException e) {
         LOG.error("Failed to export table:" + assembly.getAbsoluteName(), e);
      }
   }

   @Override
   protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
      try {
         resultFiles.add(writeTableDataAssembly(assembly, lens));
      } catch (IOException e) {
         LOG.error("Failed to export crosstab:" + assembly.getAbsoluteName(), e);
      }
   }

   @Override
   protected void writeCalcTable(CalcTableVSAssembly assembly, VSTableLens lens) {
      try {
         resultFiles.add(writeTableDataAssembly(assembly, lens));
      } catch (IOException e) {
         LOG.error("Failed to export calc table:" + assembly.getAbsoluteName(), e);
      }
   }

   /**
    * Write a table data assembly to a csv file.
    * @param assembly table data assembly.
    * @param table table lens of table data assembly.
    * @return csv file.
    * @throws IOException
    */
   private File writeTableDataAssembly(VSAssembly assembly, TableLens table) throws IOException {
      if(assembly == null) {
         return null;
      }

      if(exportAssemblies != null && exportAssemblies.size() > 0 &&
         !exportAssemblies.contains(assembly.getAbsoluteName()))
      {
         return null;
      }

      String fileName = Tool.normalizeFileName(currentBookmark + "_" +
         assembly.getAbsoluteName());

      if(tmpDir == null) {
         this.createTmpDir();
      }

      File csvFile = fileSystemService.getFile(tmpDir.getPath(), fileName + "." + "csv");

      // Used to determine which columns are hidden
      if(((VSTableLens) table).getColumnWidths().length == 0) {
         ((VSTableLens) table).initTableGrid(assembly.getVSAssemblyInfo());
      }

      try(OutputStream out = new FileOutputStream(csvFile)) {
         CSVUtil.writeTableDataAssembly(table, out, getDelim(), getQuote(), isKeepHeader());
         final String limitMessage = box.getLimitMessage(assembly.getName());

         if(limitMessage != null) {
            final PrintWriter writer = new PrintWriter(out);
            writer.println("\n" + limitMessage);
            writer.flush();
         }
      }
      catch(UnsupportedEncodingException ex) {
         throw new IOException(ex);
      }

      return csvFile;
   }

   /**
    * Create a temporary directory for temporary files.
    */
   private void createTmpDir() throws IOException {
      String uuid =  UUID.randomUUID().toString();
      String dir = fileSystemService.getCacheDirectory() + File.separator + uuid;
      tmpDir = fileSystemService.getFile(dir);

      if(!tmpDir.mkdir()) {
         LOG.warn(
            "Failed to create temporary directory: " + tmpDir);
      }
   }

   @Override
   protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph, DataSet data, boolean imgOnly) {
   }

   @Override
   protected void writeVSTab(TabVSAssembly assembly) {
   }

   @Override
   protected void writeGroupContainer(GroupContainerVSAssembly assembly, XPortalHelper helper) {
   }

   @Override
   protected void writeShape(ShapeVSAssembly assembly) {
   }

   @Override
   protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {
   }

   @Override
   protected void writeSubmit(SubmitVSAssembly assembly) {
   }

   /**
    * Compress csv files then write to out put stream and remove the temp csv files.
    * @throws IOException
    */
   @Override
   public void write() throws IOException {
      List<String> compressFileNames = null;

      if(tmpDir == null) {
         this.createTmpDir();
      }

      String zipName = tmpDir.getPath() +
         (getAssetEntry() != null ? Tool.normalizeFileName(getAssetEntry().getName()) : "csvZip")
         + new Date().getTime()
         + ".zip";

      if(resultFiles != null) {
         compressFileNames = resultFiles.stream()
            .filter(csvFile -> csvFile != null)
            .map(csvFile -> csvFile.getPath())
            .collect(Collectors.toList());
      }

      if(excelFile != null) {
         compressFileNames.add(excelFile.getPath());
      }

      File zipFile = null;

      if(compressFileNames != null) {
         if(Tool.zipFiles(compressFileNames.toArray(new String[compressFileNames.size()]), zipName,
                 true, getPassword())) {
            zipFile = fileSystemService.getFile(Tool.convertUserFileName(zipName));
         }
      }

      if(zipFile != null) {
         try(FileInputStream inputStream = new FileInputStream(zipFile)){
            BufferedInputStream in = new BufferedInputStream(inputStream);
            byte[] buf = new byte[4096];
            int nread;

            while((nread = in.read(buf)) > 0) {
               stream.write(buf, 0, nread);
            }

            stream.close();
            in.close();
         }
         finally {
            removeCSVFiles();
            deleteFile(zipFile);
         }
      }
   }

   @Override
   public void setPassword(String password) {
      this.compressPassword = password;
   }

   @Override
   public String getPassword() {
      return this.compressPassword;
   }

   /**
    * Remove the tmp files and folder.
    */
   private void removeCSVFiles() {
      if(resultFiles != null) {
         for(File file : resultFiles) {
            if(file == null) {
               continue;
            }

            deleteFile(file);
         }
      }

      if(excelFile != null) {
         deleteFile(excelFile);
      }

      if(tmpDir != null) {
         deleteFile(tmpDir);
      }
   }

   /**
    * Delete a file. will retry after 6 seconds if delete failed.
    * @param file
    */
   private void deleteFile(File file) {
      if(file != null) {
         boolean removed = file.delete();

         if(!removed) {
            FileSystemService.getInstance().remove(file, 6000);
         }
      }
   }

   public String getDelim() {
      return delim;
   }

   public void setDelim(String delim) {
      this.delim = delim;
   }

   public String getQuote() {
      return quote;
   }

   public void setQuote(String quote) {
      this.quote = quote;
   }

   public boolean isKeepHeader() {
      return keepHeader;
   }

   public void setKeepHeader(boolean keepHeader) {
      this.keepHeader = keepHeader;
   }

   public List<File> getResultFiles() {
      return new ArrayList<>(resultFiles);
   }

   public void setExcelFile(File excelFile) {
      this.excelFile = excelFile;
   }

   private FileSystemService fileSystemService = FileSystemService.getInstance();
   private String delim = ",";
   private String quote = null;
   private boolean keepHeader = true;
   private File tmpDir;
   private List<File> resultFiles = new ArrayList<>();
   private File excelFile;
   private List<String> exportAssemblies = new ArrayList<>();
   private OutputStream stream;
   private String currentBookmark;
   private String compressPassword;

   private static final Logger LOG =
      LoggerFactory.getLogger(CSVVSExporter.class);
}
