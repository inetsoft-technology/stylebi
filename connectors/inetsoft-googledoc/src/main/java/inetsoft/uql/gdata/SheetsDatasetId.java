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
package inetsoft.uql.gdata;

/**
 * Composes and parses the SPI's {@code TabularDatasetRef} id for this connector, which is
 * naturally composite (spreadsheet + sheet).
 *
 * A Sheets dataset is one sheet, but a sheet's numeric id alone does not identify which
 * spreadsheet it lives in, so both are folded into the id -- modelled on
 * {@code AnalyticsDatasetId} (Google Analytics GA4), same separator, same escaping order, same
 * round-trip check.
 *
 * A Drive file id is {@code [A-Za-z0-9_-]} and a Sheets {@code sheetId} is a signed 32-bit
 * integer, so today neither component can ever contain {@code ~}, {@code .}, or {@code %}.
 * Escaping is applied anyway, on purpose: {@code TabularDatasetRef.id}'s "must not contain a
 * {@code .}" rule has to be satisfied by construction, not by trusting a grammar Google can
 * change -- the same distinction {@code TabularDatasetRef}'s own javadoc draws when it calls out
 * OData's dot-free grammar as an accident, not a guarantee.
 */
final class SheetsDatasetId {
   private SheetsDatasetId() {
   }

   private static final String SEPARATOR = "~";

   static String compose(String spreadsheetId, String sheetId) {
      return escape(spreadsheetId) + SEPARATOR + escape(sheetId);
   }

   record Parsed(String spreadsheetId, String sheetId) {}

   /**
    * Parses a dataset id previously produced by {@link #compose}.
    *
    * A shape check, not a membership check: it rejects anything that could not possibly have come
    * out of {@link #compose}, but does not confirm the (spreadsheet, sheet) pair is one this data
    * source's own {@code listDatasets} would actually enumerate today -- the same documented
    * residual as {@code AnalyticsDatasetId#parse}, and for the same reason (full membership
    * validation would mean re-running {@code listDatasets} on every {@code describeDataset} call).
    * {@link GDataCatalog#describeDataset} closes that gap itself, by resolving the sheet id
    * against a fresh {@code listSheetProperties} call.
    *
    * @throws IllegalArgumentException if {@code datasetId} has no separator, either decoded
    *         component is blank, or re-composing the decoded components does not reproduce
    *         {@code datasetId} exactly.
    */
   static Parsed parse(String datasetId) {
      // indexOf, not lastIndexOf: compose() escapes the separator out of both components, so the
      // first unescaped '~' is the only one that can occur.
      int sep = datasetId.indexOf(SEPARATOR);

      if(sep < 0) {
         throw new IllegalArgumentException("Not a Google Sheets dataset id: " + datasetId);
      }

      String spreadsheetId = unescape(datasetId.substring(0, sep));
      String sheetId = unescape(datasetId.substring(sep + 1));

      if(spreadsheetId.isBlank() || sheetId.isBlank()) {
         throw new IllegalArgumentException(
            "Not a Google Sheets dataset id (blank spreadsheet or sheet): " + datasetId);
      }

      if(!datasetId.equals(compose(spreadsheetId, sheetId))) {
         throw new IllegalArgumentException(
            "Not a Google Sheets dataset id (does not round-trip): " + datasetId);
      }

      return new Parsed(spreadsheetId, sheetId);
   }

   // Order matters both ways: '%' escaped FIRST (else escaping '.'/'~' would introduce new '%'
   // characters a later '%'->'%25' pass would double-escape), and unescaped LAST (else a literal
   // "%2E" that was never an escaped dot could be misread as one).
   private static String escape(String v) {
      return v.replace("%", "%25").replace(".", "%2E").replace(SEPARATOR, "%7E");
   }

   private static String unescape(String v) {
      return v.replace("%7E", SEPARATOR).replace("%2E", ".").replace("%25", "%");
   }
}
