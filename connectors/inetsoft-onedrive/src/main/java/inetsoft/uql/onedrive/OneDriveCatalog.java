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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * OneDrive's whole {@link TabularCatalogProvider} implementation. Kept package-private and out of
 * {@link OneDriveRuntime} itself so the runtime stays a two-line delegator, matching
 * {@code ServerFileCatalog}'s own split -- SPI methods here, everything else (runQuery,
 * testDataSource) untouched.
 *
 * <h2>Excel ids -- option B' (one dataset per FILE, never per sheet)</h2>
 * Unlike ServerFile, a OneDrive workbook's sheet list is not available without downloading the
 * whole file first ({@code getExcelSheetNames()} needs {@code getTempFile()}), so opening every
 * workbook at enumeration time -- ServerFile's option C -- would mean downloading every workbook in
 * the drive on every {@code listDatasets} call. {@link #listDatasets} therefore emits exactly ONE id
 * per accepted file, at ZERO downloads. {@link #describeDataset} downloads the file exactly ONCE and
 * gets both the header and the sheet list from that one download (a single {@code loadFile()} inside
 * {@code OneDriveFileUtil#getColumnDefinition} primes {@code OneDriveQuery#getTempFile()}, so the
 * later {@code getExcelSheetNames()} call costs nothing more):
 *
 * <ul>
 *    <li>not a workbook (.csv/.txt): columns from the declared text header.
 *    <li>exactly one sheet: columns from that sheet; {@code params} pins the sheet's REAL name
 *        under the {@link #PARAM_EXCEL_SHEET_NAME} alias (see {@link OneDriveQuery#getExcelSheetName}
 *        -- {@code excelSheet} itself cannot be written through {@code applyQueryContract} today,
 *        because its {@code tagsMethod} rejects any value on a query with nothing downloaded yet).
 *    <li>two or more sheets: THROWS a named error listing the sheet count and names, rather than
 *        silently binding to the first one. OneDrive has no {@code checkExcelAmbiguity}-equivalent
 *        guard the way {@code java.io.File}-typed properties do (that guard is unreachable for a
 *        {@code String}-typed property -- see {@link #describeDataset}'s own javadoc below), so this
 *        connector's own refusal is the only thing standing between a multi-sheet workbook and a
 *        silent wrong-sheet bind.
 * </ul>
 *
 * <p>Nothing is lost by refusing multi-sheet workbooks: they are not annotatable through the file
 * pipeline today either (wiz's {@code parseExcelSheetAmbiguity} cannot parse the message
 * {@code TabularQueryContractSupport.checkExcelAmbiguity} throws for a {@code File}-typed property,
 * and that check is unreachable here in the first place -- see {@link #describeDataset}). This
 * connector's catalog is therefore strictly WEAKER than ServerFile's on this one axis (ServerFile
 * enumerates a sheet-level id per sheet and CAN annotate a multi-sheet workbook); that is a
 * deliberate, cost-driven asymmetry between two connectors that otherwise look alike, not an
 * oversight -- see the design doc's §4/§10.1.
 *
 * <h2>The id grammar</h2>
 * {@code id := escape(relativePath)}, with NO suffix ever appended under option B' -- a single-sheet
 * workbook's id is its bare escaped path, identical in shape to a plain CSV's. Every id still goes
 * through the SAME percent-escape ({@code %} -> {@code %25} first, then {@code #} -> {@code %23})
 * used by ServerFileCatalog, even though nothing in THIS round's grammar needs a separator to
 * protect -- so that the day a future round adds Graph's {@code /workbook/worksheets} API and a
 * {@code <path>#<sheet>} suffixed id (see the design doc's Option E), every id emitted by THIS round
 * is already guaranteed {@code #}-free, and no migration of previously-stored ids is needed. Escaping
 * unconditionally now, rather than only once a suffix exists, is what keeps that day free.
 */
final class OneDriveCatalog {
   private OneDriveCatalog() {
   }

   /** The bean-property names {@code describeDataset}'s {@code params} map is keyed by. */
   static final String PARAM_PATH = "path";
   static final String PARAM_EXCEL_SHEET_NAME = "excelSheetName";

   /**
    * = wiz's {@code MAX_ANNOTATABLE_FILES}, the bound the flip from the file pipeline to this SPI
    * otherwise loses (see the design doc's §2). {@code collectChildren} counts FOLDERS toward this
    * bound too ({@code OneDriveRuntime}'s own javadoc), so a folder-heavy drive truncates at fewer
    * than 5000 FILES -- documented here rather than compensated for with a larger number, because
    * {@link TabularCatalog#truncated()} reports the shortfall either way.
    */
   static final int MAX_ENTRIES = 5000;

   /**
    * Never called directly by {@link OneDriveRuntime} -- see {@link OneDriveCatalogCache}.
    */
   static TabularCatalog listDatasets(OneDriveDataSource ds) throws Exception {
      OneDriveQuery query = new OneDriveQuery();
      query.setDataSource(ds);
      return listDatasets(query);
   }

   /**
    * Test seam: {@code browsable} replaces the real, Graph-backed {@code browseChildren} call, so a
    * test can drive a DELIBERATELY reordered (or truncated, or failing) listing without a live
    * tenant or a constructed {@link OneDriveDataSource} -- nothing in enumeration needs the data
    * source's own identity (no configured root to validate; see this class's B8 javadoc on {@link
    * #describeDataset} for why there is none). Production always goes through the one-arg overload
    * above, which always passes the real {@link OneDriveQuery} (itself a {@link BrowsableQuery});
    * nothing else calls this overload.
    */
   static TabularCatalog listDatasets(BrowsableQuery browsable) throws Exception {
      BrowsableQuery.BrowseListing listing = browsable.browseChildren(
         "", true, browsable.getAcceptedExtensions(), MAX_ENTRIES);

      List<TabularDatasetRef> refs = new ArrayList<>();

      for(BrowsableQuery.BrowseEntry entry : listing.entries()) {
         if(entry.folder() || !isReadable(entry.path())) {
            // Filtered with the READER's own predicate (isExcel || isText), not with whatever
            // acceptTypes browseChildren was given -- collectChildren's own case-insensitive
            // accepts() would otherwise enumerate e.g. "REPORT.XLSX", which isExcel/isText (both
            // case-sensitive) would then read down the wrong branch. B17: the advertised set and
            // the describable set must be the same set by construction.
            continue;
         }

         refs.add(new TabularDatasetRef(encodeId(entry.path())));
      }

      // B3: the connector imposes its own stable order -- Graph's own listing order is not a
      // promise, and the default paged listDatasets re-enumerates per page, so an unstable native
      // order would skip or repeat a dataset across pages.
      refs.sort(Comparator.comparing(TabularDatasetRef::id));

      return new TabularCatalog(refs, List.of(), listing.truncated());   // never override listRelationships (D5)
   }

   private static boolean isReadable(String path) {
      return OneDriveFileUtil.isExcel(path) || OneDriveFileUtil.isText(path);
   }

   static TabularDatasetSchema describeDataset(OneDriveDataSource ds, String datasetId)
      throws Exception
   {
      return describeDataset(ds, datasetId, OneDriveCatalog::newQuery);
   }

   /**
    * Test seam: {@code queryFactory} replaces the fresh {@link OneDriveQuery} this method builds, so
    * a test can hand back a query whose {@code getFile()} is stubbed (mirroring
    * {@code OneDriveRuntimeTests}' own {@code spy(new OneDriveQuery())} pattern) instead of reaching
    * a real {@code GraphServiceClient}. Production always uses {@link #newQuery}; nothing else calls
    * this overload.
    *
    * <h2>B8 -- what guards the path</h2>
    * {@code getPath()} is a {@code String}, not a {@code java.io.File}, so
    * {@code TabularQueryContractSupport.resolveTargetFile}'s absolute-path/{@code ..}/containment
    * checks -- the ONLY place {@code checkExcelAmbiguity} is reachable from, too -- are never entered
    * for this connector; a {@code String} property falls through to {@code coerceParam}'s
    * {@code String} branch, which is a bare {@code return raw;}. There is also no configured root to
    * be "contained within" in the first place: {@code OneDriveDataSource} declares no folder/root
    * property at all -- the grant is the whole signed-in user's OneDrive, addressed through
    * {@code client.me().drive().root()}. So {@link #requireEmittableId} below is NOT an authorization
    * boundary and must never be described as one: the real boundary is the OAuth token's own scope
    * (whatever it cannot reach, no path can reach) plus {@code checkPermission(dsName, READ)}, which
    * gates who may call this method at all. What {@link #requireEmittableId} closes is narrower and
    * purely a catalog-identity concern: a caller entitled to USE this data source is not thereby
    * entitled to assert that an arbitrary string is one of ITS DATASETS -- {@link #listDatasets}
    * never emits an id with a leading {@code /}, a drive-letter shape, or a {@code ..} segment, so
    * {@code describeDataset} refuses one before ever reaching Graph, purely on the grounds that no
    * such id could have come from this connector's own {@link #listDatasets}.
    */
   static TabularDatasetSchema describeDataset(OneDriveDataSource ds, String datasetId,
                                                Function<OneDriveDataSource, OneDriveQuery> queryFactory)
      throws Exception
   {
      String path = requireEmittableId(ds, datasetId);

      OneDriveQuery query = queryFactory.apply(ds);
      query.setDataSource(ds);
      query.setPath(path);

      // The SAME object graph applyQueryContract builds at query-fill time (path -> the identity
      // property) -- this is what makes the params round trip (B7) true by construction.
      ColumnDefinition[] columnDefs = OneDriveFileUtil.getColumnDefinition(query);

      // D2: getColumnDefinition RETURNS NULL on a download failure or an unreadable header (three
      // separate return-null paths inside OneDriveFileUtil) -- every one of them must become a
      // named throw here, never an empty/null schema, which is indistinguishable from a successful
      // description of nothing.
      if(columnDefs == null || columnDefs.length == 0) {
         throw new IOException("Data source '" + ds.getName() + "' target '" + datasetId +
            "' produced no columns.");
      }

      Map<String, String> params = new LinkedHashMap<>();
      params.put(PARAM_PATH, path);

      if(OneDriveFileUtil.isExcel(path)) {
         // Free at this point (P-13): getColumnDefinition's own call above already downloaded the
         // file onto THIS query instance (loadFile() inside it), so getTempFile() is already
         // non-null and this reads the already-local temp file, no second download.
         String[] sheets = query.getExcelSheetNames();

         if(sheets == null || sheets.length == 0 ||
            (sheets.length == 1 && (sheets[0] == null || sheets[0].isEmpty())))
         {
            // getExcelSheetNames() has two sentinel returns, neither a genuine sheet list:
            // a one-element array holding "" when nothing was ever downloaded (P-1) -- reachable
            // here only if the download silently produced a temp file that then failed to parse as
            // a workbook, since a genuine download failure already returned null columns above --
            // and a one-element array holding null when the sheet-name read itself threw and was
            // swallowed by that method's own catch(Exception), leaving its uninitialised
            // `new String[1]` to be returned as-is.
            throw new IOException("Data source '" + ds.getName() + "' could not read the sheet " +
               "list of '" + path + "'.");
         }

         if(sheets.length > 1) {
            throw new IOException("Data source '" + ds.getName() + "' target '" + datasetId +
               "' is a workbook with " + sheets.length + " sheets (" +
               String.join(", ", sheets) + "); OneDrive's tabular catalog can only annotate a " +
               "single-sheet workbook. Every sheet of a multi-sheet workbook is currently " +
               "un-annotatable on this connector.");
         }

         params.put(PARAM_EXCEL_SHEET_NAME, sheets[0]);
      }

      List<TabularColumn> columns = toTabularColumns(ds, datasetId, columnDefs);

      // sampleable=true, conditional on the wiz-side buildProbeTable fix (03-reconcile.md §5):
      // one extra download of a file this connector has just proved it can read, not a new class
      // of cost -- see the design doc's §3.
      return new TabularDatasetSchema(datasetId, columns, List.of(), params,
                                      /* columnsMayBeIncomplete */ false, /* description */ null,
                                      /* sampleable */ true);
   }

   private static OneDriveQuery newQuery(OneDriveDataSource ds) {
      OneDriveQuery query = new OneDriveQuery();
      query.setDataSource(ds);
      return query;
   }

   /**
    * Maps declared header columns to {@link TabularColumn}s, with no new type inference: {@link
    * DataType#type()} already IS the column's {@code XSchema} constant. A null-typed column (a
    * header string outside {@code DataType}'s 12 known constants) throws instead of reaching
    * {@link TabularColumn} and failing later, less diagnosably.
    */
   private static List<TabularColumn> toTabularColumns(OneDriveDataSource ds, String datasetId,
                                                        ColumnDefinition[] columnDefs)
      throws IOException
   {
      List<TabularColumn> columns = new ArrayList<>(columnDefs.length);

      for(ColumnDefinition columnDef : columnDefs) {
         DataType type = columnDef.getType();

         if(type == null) {
            throw new IOException("Data source '" + ds.getName() + "' target '" + datasetId +
               "' column '" + columnDef.getName() + "' has an unrecognized type.");
         }

         columns.add(new TabularColumn(columnDef.getName(), type.type()));
      }

      return columns;
   }

   /**
    * Validates a caller-supplied id BEFORE any Graph call (local, free) -- see this method's own
    * B8 javadoc on {@link #describeDataset} for what this is and is not a guard against. Refuses:
    * blank, a leading {@code /}, a Windows-drive-letter shape, any {@code ..} path segment, and any
    * decoded path that does not satisfy the SAME reader predicate {@link #listDatasets} filters
    * enumeration by (B17) -- none of these shapes can have come from this connector's own
    * {@link #listDatasets}, regardless of whether Graph itself would also refuse them.
    */
   private static String requireEmittableId(OneDriveDataSource ds, String datasetId)
      throws IOException
   {
      if(datasetId == null || datasetId.isBlank()) {
         throw new IOException(
            "Data source '" + ds.getName() + "' was given a blank dataset id.");
      }

      String path = unescapeId(datasetId);

      if(path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
         throw new IllegalArgumentException("Data source '" + ds.getName() + "': '" + datasetId +
            "' is not one of its datasets.");
      }

      for(String segment : path.split("/")) {
         if("..".equals(segment)) {
            throw new IllegalArgumentException("Data source '" + ds.getName() + "': '" + datasetId +
               "' is not one of its datasets.");
         }
      }

      if(!isReadable(path)) {
         throw new IllegalArgumentException("Data source '" + ds.getName() + "': '" + datasetId +
            "' is not one of its datasets.");
      }

      return path;
   }

   // ----- id grammar: one encoder, one decoder, so they cannot drift -----

   /**
    * Percent-escapes a relative path -- {@code %} -> {@code %25} first, then {@code #} ->
    * {@code %23} -- so a filename that already, literally, contains {@code %23}/{@code %25} round
    * trips instead of being corrupted by {@link #unescapeId}. Injective on all of {@code String}:
    * after escaping, every literal {@code %} appears as {@code %25} and every literal {@code #} as
    * {@code %23}, and no other input can produce an unconsumed {@code %23}/{@code %25} (a literal
    * {@code %23} in the input becomes {@code %2523}). Applying {@link #unescapeId} recovers the
    * original for every input, so {@code escape} has a left inverse; with no suffix in this round's
    * grammar, {@code id -> path} is therefore a bijection onto the emitted id set, and no two
    * distinct files can collide.
    */
   private static String encodeId(String relativePath) {
      return relativePath.replace("%", "%25").replace("#", "%23");
   }

   /** Inverse of {@link #encodeId}: undo the LAST-applied escape first ({@code #}, then {@code %}). */
   private static String unescapeId(String datasetId) {
      return datasetId.replace("%23", "#").replace("%25", "%");
   }
}
