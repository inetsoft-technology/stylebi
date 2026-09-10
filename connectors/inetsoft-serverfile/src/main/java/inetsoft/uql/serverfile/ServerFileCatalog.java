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
 * (wiz's {@code tableMetadataService.ts:283} and {@code tabularFileProbe.ts:226} both split a FILE-
 * category id on {@code #} -- but neither ever runs on a METADATA id: {@code tableMetadataService.ts}
 * gates that split behind {@code category === "FILE"}, and {@code tabularFileProbe.ts}'s split result
 * is fully overwritten by {@code describeDataset}'s own already-decoded {@code params} map, which is
 * always spread in afterward. Dataset ids stay opaque to wiz end to end (stylebi#5041); this format
 * is chosen only because it is already familiar, not because anything on the wiz side parses it).
 *
 * <p><b>The relative path is percent-escaped before it is used as (or joined into) an id</b> --
 * {@code #} -> {@code %23}, {@code %} -> {@code %25} (escaped first, so an escaped {@code %23}/
 * {@code %25} sequence that already existed in a real filename is never re-escaped or
 * double-unescaped) -- so the path portion of every id is GUARANTEED {@code #}-free. That is what
 * makes decoding correct: {@code decodeId} splits on the FIRST {@code #} in the full id, which can
 * therefore only ever be the genuine sheet separator -- never a {@code #} that was really part of a
 * plain file or directory NAME (closed in P6 review, R1-1), and never confused by a {@code #}
 * inside the SHEET NAME itself (Excel forbids {@code :\/?*[]} in a sheet name but not {@code #}; a
 * LAST-{@code #} split, tried first, put the separator at the wrong {@code #} for a sheet legally
 * named e.g. {@code Q#1} -- closed in P6 review, R1-5). Three failure modes this closes in total:
 * (1) an ordinary file like {@code sales#2026.csv} used to decode as path {@code sales} + sheet
 * {@code 2026.csv} and fail to resolve, even though nothing is wrong with that file; (2) a
 * multi-sheet workbook {@code book.xlsx} with a sheet legally named {@code Sheet1.csv} encoded to
 * {@code book.xlsx#Sheet1.csv} -- identical to the BARE id an unrelated plain file literally named
 * {@code book.xlsx#Sheet1.csv} would have had, which {@code TabularCatalogService} rejects as a
 * duplicate id, aborting the whole catalog; (3) a workbook with a sheet legally named {@code Q#1}
 * produced an id whose separator a last-{@code #} split located one character too late. The sheet
 * NAME itself is never escaped -- it does not need to be, once the path's own {@code #} is gone,
 * the first {@code #} in the id is unambiguous regardless of how many more {@code #} characters
 * the sheet name itself contains after it.
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
      return listDatasets(ds, ServerFileCatalog::listChildren);
   }

   /**
    * Test seam (R1-2): {@code lister} replaces the real {@code dir.listFiles()} call, so a test
    * can drive a DELIBERATELY reordered listing of the same file set between two calls and prove
    * the sort below is what makes the order stable -- not an incidental property of one real
    * filesystem's native order, which is not guaranteed stable by any filesystem's own contract and
    * is exactly the trap {@link TabularCatalog#datasets()}'s javadoc calls out by name. Production
    * always goes through the one-arg overload above, which always uses {@link #listChildren}; nothing
    * else calls this overload.
    */
   static TabularCatalog listDatasets(ServerFileDataSource ds, java.util.function.Function<File, File[]> lister)
      throws Exception
   {
      File root = requireRoot(ds);
      String rootPath = root.getAbsolutePath();
      List<TabularDatasetRef> refs = new ArrayList<>();
      collect(ds, root, rootPath, refs, lister);

      // Imposed here, not inherited from File#listFiles() (unordered) and not shared with
      // ServerFileUtil.getFileList (the query execution path, whose row order this must not
      // change). Locale-independent for the same reason TabularCatalogProvider's own
      // nameContains filter pins Locale.ROOT.
      refs.sort(Comparator.comparing(TabularDatasetRef::id));

      return new TabularCatalog(refs, List.of());   // never override listRelationships (C3)
   }

   private static File[] listChildren(File dir) {
      return dir.listFiles();
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
                               List<TabularDatasetRef> out,
                               java.util.function.Function<File, File[]> lister) throws Exception
   {
      File[] children = lister.apply(dir);

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
            collect(ds, child, rootPath, out, lister);
            continue;
         }

         String absolutePath = child.getAbsolutePath();

         if(!ServerFileUtil.isText(absolutePath) && !ServerFileUtil.isExcel(absolutePath)) {
            continue;   // outside the whitelist runQuery would also skip -- see class javadoc
         }

         String relativePath = relativize(rootPath, absolutePath);

         if(!ServerFileUtil.isExcel(absolutePath)) {
            out.add(new TabularDatasetRef(encodeId(relativePath, null)));
            continue;
         }

         String[] sheets = readSheetNames(ds, child, relativePath);

         if(sheets.length <= 1) {
            out.add(new TabularDatasetRef(encodeId(relativePath, null)));
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

   /**
    * {@code sheet == null} -- a non-Excel file or a single-sheet workbook -- yields the bare
    * escaped path, no suffix. Otherwise {@code <escaped path>#<sheet, unescaped>}. Called for
    * EVERY id this catalog emits (see {@link #collect}), not just the multi-sheet case, so the
    * escaping is applied uniformly and a bare id can never collide with a suffixed one.
    */
   private static String encodeId(String relativePath, String sheet) {
      String escapedPath = escapePath(relativePath);
      return sheet == null ? escapedPath : escapedPath + "#" + sheet;
   }

   /**
    * Splits on the FIRST {@code #}, not the last -- {@link #escapePath} guarantees the path
    * portion is {@code #}-free, so the first {@code #} in the full id is always the genuine
    * separator, whether or not the SHEET name (never escaped) contains further {@code #}
    * characters of its own (R1-5: a last-{@code #} split put the separator inside a sheet legally
    * named e.g. {@code Q#1}).
    */
   private static Target decodeId(String datasetId) {
      int idx = datasetId.indexOf('#');
      return idx < 0 ? new Target(unescapePath(datasetId), null)
                      : new Target(unescapePath(datasetId.substring(0, idx)),
                                   datasetId.substring(idx + 1));
   }

   /**
    * Percent-escapes a relative path so it can never contribute an unescaped {@code #} to an id --
    * {@code %} first, then {@code #}, so an escape sequence that was already literal text in a
    * real filename is never re-escaped. See the class javadoc for the two failure modes this
    * closes (R1-1) and why only the path, never the sheet name, is escaped.
    */
   private static String escapePath(String relativePath) {
      return relativePath.replace("%", "%25").replace("#", "%23");
   }

   /** Inverse of {@link #escapePath}: undo the LAST-applied escape first ({@code #}, then {@code %}). */
   private static String unescapePath(String escapedRelativePath) {
      return escapedRelativePath.replace("%23", "#").replace("%25", "%");
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
