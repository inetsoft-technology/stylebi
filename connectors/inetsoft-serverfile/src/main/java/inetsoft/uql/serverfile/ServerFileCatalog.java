/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.serverfile;

import inetsoft.uql.tabular.*;
import inetsoft.uql.util.filereader.ExcelFileSupport;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * ServerFile's whole {@link TabularCatalogProvider} implementation: enumeration, the Excel id
 * grammar, and {@code describeDataset}. Kept package-private and out of {@link ServerFileRuntime}
 * itself so the runtime stays a two-line delegator (SPI methods here, everything else -- runQuery,
 * testDataSource -- untouched).
 *
 * <p>NOT SHARED with {@link ServerFileUtil#getFileList} on purpose. That method is on the QUERY
 * execution path ({@link ServerFileRuntime#runQuery}) and NPEs on an unreadable directory by
 * design (catch-and-continue is correct there); this catalog throws a named exception instead,
 * because a partial catalog is indistinguishable from a complete one to the annotation pipeline
 * (see {@link TabularCatalogProvider#listDatasets(TabularDataSource)}'s javadoc). What IS shared is
 * the extension whitelist ({@link ServerFileUtil#isText}/{@link ServerFileUtil#isExcel}), so this
 * catalog can never enumerate a different set of files than {@code runQuery} would read.
 *
 * <h2>Excel ids -- option C</h2>
 * A non-Excel file is one dataset, id = its path relative to the data source root, normalised to
 * {@code /}. A single-sheet workbook is exactly that too -- no suffix. A workbook with MORE than
 * one sheet is one dataset PER sheet, id = {@code <relative path>#<sheet name>}. Listing a
 * workbook's sheet names is a cheap, near-constant-size read (see {@link ServerFileCatalogCache}'s
 * javadoc for the measured cost); this is what lets every Excel workbook be opened once per
 * enumeration without the cost scaling with the workbook's row count.
 *
 * <p>{@code #} is the separator because it is already the one wiz uses for this exact convention
 * (wiz's {@code tableMetadataService.ts} splits an id on the LAST {@code #}; wiz's
 * {@code tabularFileProbe.ts} builds ids the same way for the file pipeline this SPI conversion
 * replaces for ServerFile). Decoding here matches that -- split on the LAST {@code #} -- for the
 * same reason: a relative path may contain {@code #}, so the split has to favor the tail. This
 * carries over a pre-existing, unfixed ambiguity for the rare case of a sheet NAME itself
 * containing {@code #} (legal on Excel, unlike {@code :\/?*[]}) -- not introduced by this change,
 * not fixed by it either; see the design doc's D.1.
 */
final class ServerFileCatalog {
   private ServerFileCatalog() {
   }

   /** The bean-property names {@code describeDataset}'s {@code params} map is keyed by. */
   static final String PARAM_FILE_FOLDER = "fileFolder";
   static final String PARAM_EXCEL_SHEET = "excelSheet";

   /**
    * Sorted by id; throws rather than returning a partial or empty-on-failure catalog. Never
    * called directly by {@link ServerFileRuntime} -- see {@link ServerFileCatalogCache}.
    */
   static TabularCatalog listDatasets(ServerFileDataSource ds) throws Exception {
      File root = requireRoot(ds);
      String rootPath = root.getAbsolutePath();
      List<TabularDatasetRef> refs = new ArrayList<>();
      collect(ds, root, rootPath, refs);

      // Imposed here, not inherited from File#listFiles() (unordered) and not shared with
      // ServerFileUtil.getFileList (the query execution path, whose row order this must not
      // change). Locale-independent for the same reason TabularCatalogProvider's own
      // nameContains filter pins Locale.ROOT.
      refs.sort(Comparator.comparing(TabularDatasetRef::id));

      return new TabularCatalog(refs, List.of());   // never override listRelationships (C3)
   }

   static TabularDatasetSchema describeDataset(ServerFileDataSource ds, String datasetId)
      throws Exception
   {
      requireRoot(ds);
      Target target = decodeId(datasetId);

      // Guarded HERE, before touching the filesystem, not only inside applyQueryContract's
      // resolveTargetFile one layer later -- describeDataset takes a caller-supplied string too,
      // and a guard that exists only one layer away is not a guard.
      validateRelativePath(ds, target.relativePath());
      File resolved = resolveWithinRoot(ds, target.relativePath());

      if(!resolved.isFile()) {
         throw new IOException("Data source '" + ds.getName() + "' has no file at '" +
            target.relativePath() + "'.");
      }

      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);
      query.setFileFolder(resolved);

      if(target.sheet() != null) {
         query.setExcelSheet(target.sheet());
      }

      // The SAME object graph applyQueryContract builds at query-fill time -- this is what makes
      // the params round trip (A6) true by construction rather than by coincidence.
      ColumnDefinition[] columnDefs = ServerFileUtil.getColumnDefinition(query);

      if(columnDefs == null || columnDefs.length == 0) {
         throw new IOException("Data source '" + ds.getName() + "' target '" + datasetId +
            "' produced no columns.");
      }

      List<TabularColumn> columns = toTabularColumns(ds, datasetId, columnDefs);

      // Only the identity, nothing else: every other ServerFileQuery property is a read option a
      // freshly-built query derives with the exact defaults getColumnDefinition just read the
      // header under, so writing them would validate a value the catalog invented, and columns
      // must NOT appear here -- SelectableTabularQuery.getColumns() is lazy and self-heals from
      // fileFolder/excelSheet alone. See the design doc's D.7 for the full argument.
      Map<String, String> params = new LinkedHashMap<>();
      params.put(PARAM_FILE_FOLDER, target.relativePath());

      if(target.sheet() != null) {
         params.put(PARAM_EXCEL_SHEET, target.sheet());
      }

      // sampleable=true: a local file is unmetered and cheap to re-read for sample rows -- see
      // TabularDatasetSchema#sampleable()'s javadoc and the design doc's D.6.
      return new TabularDatasetSchema(datasetId, columns, List.of(), params, false, null, true);
   }

   /**
    * Maps declared header columns to {@link TabularColumn}s, with no new type inference (C2):
    * {@link DataType#type()} already IS the column's {@code XSchema} constant. Extracted so the
    * one loud behavior this round adds -- a null-typed column throws instead of reaching {@link
    * TabularColumn} and NPE-ing later, in {@code ServerFileTableNode}'s creator loop -- is
    * testable directly against a hand-built {@link ColumnDefinition} array, without needing a
    * fixture file whose header actually produces an unrecognized type (nothing in the normal
    * header-reading path can construct one).
    */
   static List<TabularColumn> toTabularColumns(ServerFileDataSource ds, String datasetId,
                                                ColumnDefinition[] columnDefs) throws Exception
   {
      List<TabularColumn> columns = new ArrayList<>(columnDefs.length);

      for(ColumnDefinition columnDef : columnDefs) {
         DataType type = columnDef.getType();

         // getColumnDefinition's own DataType.fromType(...) call returns null for any type string
         // outside the 12 known constants, with no null check at that call site -- today that null
         // NPEs later, in ServerFileTableNode's creator loop. Converted here into a named,
         // diagnosable throw instead of letting it reach TabularColumn.
         if(type == null) {
            throw new IOException("Data source '" + ds.getName() + "' target '" + datasetId +
               "' column '" + columnDef.getName() + "' has an unrecognized type.");
         }

         columns.add(new TabularColumn(columnDef.getName(), type.type()));
      }

      return columns;
   }

   /**
    * The data source's root folder, validated. Called from BOTH SPI entry points -- {@code
    * listDatasets} (via {@link ServerFileCatalogCache}, so the check re-runs on every cache miss)
    * and {@code describeDataset} directly -- in the same change on purpose: a precondition added
    * for one SPI method and forgotten for the other is exactly how Datagov's {@code
    * describeDataset} shipped a raw NullPointerException instead of a named exception.
    */
   private static File requireRoot(ServerFileDataSource ds) throws Exception {
      File file = ds.getFile();

      if(file == null) {
         throw new IOException("Data source '" + ds.getName() + "' has no root folder configured.");
      }

      if(!file.exists() || !file.isDirectory()) {
         throw new IOException("Data source '" + ds.getName() + "''s root folder '" +
            file.getPath() + "' does not exist or is not a directory.");
      }

      return file;
   }

   private static void collect(ServerFileDataSource ds, File dir, String rootPath,
                               List<TabularDatasetRef> out) throws Exception
   {
      File[] children = dir.listFiles();

      // dir.listFiles() returns null for an unreadable directory or a non-directory; the query
      // path's own ServerFileUtil.getFileList NPEs on this (acceptable there -- catch-and-continue
      // wraps it). The catalog must not: an empty result here would be an unannounced partial
      // catalog, which TabularCatalogProvider's contract forbids.
      if(children == null) {
         throw new IOException("Data source '" + ds.getName() + "' could not list '" +
            dir.getAbsolutePath() + "'.");
      }

      for(File child : children) {
         if(child.isDirectory()) {
            collect(ds, child, rootPath, out);
            continue;
         }

         String absolutePath = child.getAbsolutePath();

         if(!ServerFileUtil.isText(absolutePath) && !ServerFileUtil.isExcel(absolutePath)) {
            continue;   // outside the whitelist runQuery would also skip -- see class javadoc
         }

         String relativePath = relativize(rootPath, absolutePath);

         if(!ServerFileUtil.isExcel(absolutePath)) {
            out.add(new TabularDatasetRef(relativePath));
            continue;
         }

         String[] sheets = readSheetNames(ds, child, relativePath);

         if(sheets.length <= 1) {
            out.add(new TabularDatasetRef(relativePath));
         }
         else {
            for(String sheet : sheets) {
               out.add(new TabularDatasetRef(encodeId(relativePath, sheet)));
            }
         }
      }
   }

   private static String[] readSheetNames(ServerFileDataSource ds, File file, String relativePath)
      throws Exception
   {
      String[] sheets;

      try {
         sheets = ExcelFileSupport.getInstance().getSheetNames(file);
      }
      catch(Exception e) {
         throw new IOException("Data source '" + ds.getName() + "' could not read the sheet " +
            "list of '" + relativePath + "'.", e);
      }

      // An unreadable/corrupt workbook silently contributing nothing is exactly the partial
      // catalog the SPI forbids -- so a workbook that cannot be opened throws (C4), same as an
      // unreadable directory above.
      if(sheets == null || sheets.length == 0) {
         throw new IOException("Data source '" + ds.getName() + "' could not read the sheet " +
            "list of '" + relativePath + "'.");
      }

      return sheets;
   }

   private static String relativize(String rootPath, String absolutePath) {
      String rel = absolutePath.length() > rootPath.length()
         ? absolutePath.substring(rootPath.length()) : "";

      if(rel.startsWith(File.separator)) {
         rel = rel.substring(1);
      }

      return rel.replace('\\', '/');
   }

   // ----- id grammar: one encoder, one decoder, so they cannot drift -----

   private static String encodeId(String relativePath, String sheet) {
      return relativePath + "#" + sheet;
   }

   private static Target decodeId(String datasetId) {
      int idx = datasetId.lastIndexOf('#');
      return idx < 0 ? new Target(datasetId, null)
                      : new Target(datasetId.substring(0, idx), datasetId.substring(idx + 1));
   }

   private record Target(String relativePath, String sheet) {}

   // ----- guards, mirroring TabularQueryContractSupport.resolveTargetFile's three refusals -----
   // (own messages -- the shared layer's verbatim message is pinned separately, where it actually
   // fires, by ServerFileExcelAmbiguityMessageTest / the guard tests against real params maps).

   private static void validateRelativePath(ServerFileDataSource ds, String relativePath)
      throws Exception
   {
      String normalized = relativePath.replace('\\', '/');

      if(new File(normalized).isAbsolute() || normalized.startsWith("/") ||
         normalized.matches("^[A-Za-z]:.*"))
      {
         throw new IllegalArgumentException("Data source '" + ds.getName() + "': '" +
            relativePath + "' must be relative to the data source's root folder, not an " +
            "absolute path.");
      }

      for(String segment : normalized.split("/")) {
         if("..".equals(segment)) {
            throw new IllegalArgumentException("Data source '" + ds.getName() + "': '" +
               relativePath + "' must not contain '..'.");
         }
      }
   }

   private static File resolveWithinRoot(ServerFileDataSource ds, String relativePath)
      throws Exception
   {
      File configuredRoot = ds.getFile();
      File file = new File(configuredRoot, relativePath);
      String filePath = file.getCanonicalPath();
      String rootPath = configuredRoot.getCanonicalPath();

      if(!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
         throw new IllegalArgumentException("Data source '" + ds.getName() + "': '" +
            relativePath + "' resolves outside the data source's root folder.");
      }

      return file;
   }
}
