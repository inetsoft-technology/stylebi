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
package inetsoft.uql.tabular;

import java.util.List;
import java.util.Map;

/**
 * The columns of one dataset.
 *
 * @param datasetId echoes the id that was asked for, so a result is self-identifying.
 * @param columns   non-empty and in source order. An implementation that finds no columns throws
 *                  rather than returning an empty list — see TabularCatalogProvider.
 * @param keyColumns names, from {@code columns}, that the source declares as this dataset's key;
 *                  empty when the source declares none. Never null.
 * @param params    binding parameters for this dataset, keyed by the connector's own query bean
 *                  property names (the same names {@code TabularUtil.getPropertyMap} derives from
 *                  its getters/setters, not a {@code @Property(label=...)} display string) so a
 *                  caller with this dataset's parameter contract in hand can match them up without
 *                  any translation table. Empty when the connector's target identity needs no such
 *                  parameters (e.g. the dataset id is itself the one property value a query needs)
 *                  or the connector has not implemented this yet. Never null — use the 3-arg
 *                  constructor below, which defaults to {@link Map#of()}, rather than passing null.
 * @param columnsMayBeIncomplete true when {@code columns} was only inferred from a bounded scan of
 *                  the source rather than read from the source's own declared metadata, so the
 *                  list above may be missing columns the source actually holds. False for every
 *                  connector that reads a declared schema (a JDBC catalog, an EDMX document, a list
 *                  endpoint) — see {@link TabularCatalogProvider#describeDataset}. Deliberately a
 *                  dataset-level fact, not a per-column one: every column returned by one
 *                  {@code describeDataset} call comes from the same scan and shares the same trust
 *                  level.
 * @param description the dataset's own description. Null means the connector said nothing about
 *                  this dataset — a different statement from an empty string, which would claim
 *                  the source declared a description and it happened to be empty.
 *                  <p>Two different kinds of text are allowed here, and they must stay visibly
 *                  separate, never blended into one composed sentence: (1) the source's own
 *                  declared description, verbatim — the same "never invented" contract {@link
 *                  TabularColumn#description()} carries at column level, applied to the dataset as
 *                  a whole; and (2), appended as a clearly separated second paragraph, a
 *                  connector's own STRUCTURAL notes about what its own extraction did — e.g. which
 *                  nested relationships it chose not to expose as {@link TabularRelationship}s and
 *                  why. What stays forbidden, for both this field and {@link
 *                  TabularColumn#description()} (which carries no such carve-out), is inventing
 *                  SEMANTICS: a connector must never compose a sentence describing what the
 *                  dataset MEANS, guessing at business intent the source never stated. The
 *                  distinction holds because this text reaches wiz's annotation LLM as durable,
 *                  never-overwritten context (see below) — a connector reporting what its own
 *                  extraction did is true by construction, so it cannot mislead the way a guessed
 *                  meaning could; laundering a guess into source truth here would corrupt context
 *                  the LLM is never able to correct against.
 *                  <p>Worth filling when the source has them, because unlike {@code
 *                  TabularColumn}'s own {@code description}, this one
 *                  reaches wiz's annotation LLM as durable table-level context: wiz's
 *                  {@code applyAnnotationToDoc} (in
 *                  {@code reconstructFullTableDoc.ts}) only ever overwrites {@code ai_context},
 *                  {@code isDimension}, {@code dimensionPrevalence}, {@code additive},
 *                  {@code preserved} and {@code state} on a table, carrying every other field
 *                  through unchanged — so a description supplied here is input the LLM reads and
 *                  never output the LLM replaces.
 * @param sampleable true when this connector's {@code describeDataset} target can be re-read
 *                  cheaply and without side effects to obtain a handful of sample rows — e.g. a
 *                  local file, as opposed to a metered REST call. Default {@code false}: every
 *                  existing connector reads a REMOTE, often metered source, so "cannot be sampled
 *                  for free" is the true default, not a placeholder. {@code TabularCatalogService}
 *                  writes this into the dataset COMMON extension only when {@code true} — the same
 *                  "present only when it says something" convention {@link #columnsMayBeIncomplete()}
 *                  follows — and wiz reads it to decide whether to probe the target for sample rows
 *                  through the existing generic tabular-probe endpoints. See the ServerFile catalog
 *                  SPI round's design doc for why this exists: flipping a connector from the file
 *                  pipeline to this SPI otherwise silently drops the sample rows an annotation pass
 *                  reads today, because a METADATA target's describe route carries no row payload.
 */
public record TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                                   List<String> keyColumns, Map<String, String> params,
                                   boolean columnsMayBeIncomplete, String description,
                                   boolean sampleable) {
   /**
    * Compatibility constructor for callers written before {@code sampleable} existed — every
    * existing connector's construction site. Defaults to {@code false}: none of Cassandra, Hive,
    * OData, SharePoint Online, ... reads a target that is cheap and side-effect-free to re-read for
    * sample rows, so "not sampleable" is the true fact about them, not just a compatibility default.
    */
   public TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                               List<String> keyColumns, Map<String, String> params,
                               boolean columnsMayBeIncomplete, String description)
   {
      this(datasetId, columns, keyColumns, params, columnsMayBeIncomplete, description, false);
   }

   /**
    * Compatibility constructor for callers written before {@code description} existed — every
    * existing connector's construction site. Defaults to {@code null}: "the connector said
    * nothing", which is the true state of every one of them until they opt in, not a placeholder
    * value standing in for an empty string.
    */
   public TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                               List<String> keyColumns, Map<String, String> params,
                               boolean columnsMayBeIncomplete)
   {
      this(datasetId, columns, keyColumns, params, columnsMayBeIncomplete, null);
   }

   /**
    * Compatibility constructor for callers written before {@code columnsMayBeIncomplete} existed —
    * every existing connector's construction site. Defaults to {@code false}: Cassandra, Hive,
    * OData, and SharePoint Online all read declared, source-published metadata rather than
    * inferring columns from a bounded scan, so "this list is authoritative" is the true fact about
    * them, not just a compatibility default.
    */
   public TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                               List<String> keyColumns, Map<String, String> params)
   {
      this(datasetId, columns, keyColumns, params, false);
   }

   /**
    * Compatibility constructor for callers written before {@code params} existed — every existing
    * connector-test construction site and any connector that has not opted into target binding
    * parameters yet. Defaults to an empty map, which {@code TabularCatalogService} treats
    * identically to "the connector said nothing": the COMMON extension's {@code params} key is
    * omitted entirely, not written empty.
    */
   public TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                               List<String> keyColumns)
   {
      this(datasetId, columns, keyColumns, Map.of());
   }
}
