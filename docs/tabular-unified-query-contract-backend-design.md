# Tabular unified query contract - backend design

> Scope: E:/inetsoft/stylebi/community, branch feat/tabular-unified-query-contract.
> Analysis baseline: bc3a88981 (tip of that branch at design time).
> Companion doc, wiz side: stylebi-wiz repo,
> docs/tabular/stylebi-tabular-query-param-contract.md - read that first; section 11 is the
> implementation record for the mechanism this document collapses.
>
> No source files were edited to produce this document. Every claim below was checked
> against the real source at the referenced path:line.

---

## Revision log (this pass, 2026-08-26)

Source: E:/inetsoft/stylebi-wiz/docs/tabular/统一契约方案-设计增补.md - decisions made in
discussion AFTER this document was first written. Sections previously marked ARBITRATED/
CONFIRMED are corrected in place where wrong, not silently overwritten; every change below is
also marked inline at its location.

FOUR CORRECTIONS (overturn things this document previously stated as settled):

- A1 (section 3.4, 3.2 step 4b, 3.4.1) - the Kind A/Kind B detection rule was WRONG. It tested
  Java type ("is RestParameters.class"); the correct rule is a RUNTIME check (does the live
  skeleton, read after this param's dependsOn prerequisites are set, come back non-empty with
  named elements). Consequence: ODataQuery.functionParameters (HttpParameter[]) was previously
  misclassified Kind B and refused; it is Kind A and is now filled by name like any other
  skeleton. The "exactly ONE recognized composite class" limitation is removed - no class name
  is enumerated in the detection rule at all.
- A2 (new section 3.4.2) - ServerFileQuery.columns carries `@Property(required = true)` while
  being Kind B - mechanically copying that into JSON Schema's `required` array would deadlock
  the agent (required, but always refused, no escape). Fixed: a Kind B composite never enters
  `required`, and is preferably omitted from the schema entirely.
- A3 (section 3.4's Kind B refusal message) - the message pointed the caller at
  "query-schema's elementParams", which is permanently empty for every Kind B composite - the
  very reason Kind B is unsupported. Message rewritten to stop citing an always-empty field.
- A4 (section 3.4, 11.4's required-array rule) - `@Property.required` is unreliable in all
  three directions (false-but-mandatory: pagination params; true-but-an-output:
  ServerFileQuery.columns; unmarked-but-indispensable: ODataQuery.entity). The generated
  `required` array is not a mechanical copy of it (11.4).

TWO NEW CAPABILITIES (sections 11-13, did not exist in this document before):

- Section 11 - GET .../query-schema gains a `queryParamsSchema` field: a JSON Schema
  projection of the existing, unchanged params[]/dependencyMatrix, built by a new
  TabularQueryParamsSchemaBuilder. Six fixed `x-` keywords carry what standard JSON Schema
  cannot express (section 11.5).
- Section 12 - a new `resolveTags` query parameter (default false) optionally inlines runtime
  candidate values into the schema, bounded by a timeout and a candidate-count cap, with three
  distinct degradation states (`unavailable` / `too-large` / unresolved `external`
  for a dependent param).
- Section 13 records what is explicitly NOT built this pass (a query-tags endpoint; catalogue
  ingestion of S1 connector metadata) and why deferring each is safe.

ONE FIX: section 10.1, previously an open CONTRADICTION against wiz's guidance text, is
confirmed resolved on the wiz side and is now recorded as closed.

New flagged decisions D8-D12 (section 8) and new risks (section 9) accompany the two new
capabilities. D2 (composite scope) is corrected in place, not overwritten - see the note
immediately following it in section 8.

## Revision log addendum (same pass, round 5 - human, scope)

Two corrections from the human, both narrowing scope. Everything else stands.

5. THE COMPOSER `add_table` PATH IS OUT OF SCOPE, REVERSING D1 (section 6, section 5's file
   list, D1 in section 8). `WorksheetAgentController.addTabularTable`, `EditRequest` and
   `WorksheetMutationSupport` are not touched, and `TabularEndpointBindingSupport` is NOT
   deleted - it keeps one consumer, the composer path. The earlier "community does not
   compile without it" argument was backwards: compilation breaks only because the design
   deleted a class a working caller uses, which argues for keeping the class. This was
   implemented and then reverted; see the commit "Leave the composer add_table path on its
   own contract".
6. THE STALE `designMaxRows` JUSTIFICATION in section 4.1 is corrected. `buildTabularTable`'s
   comment justifies persisting a cap by citing `createTables` setting `designMaxRows` to 0;
   per #75989 `designMaxRows` is "deliberately never set there any more" and the cap is
   applied per assembly (`WizUtil.applySampledPreviewCap`). The conclusion is unchanged - a
   server-invented default is still never persisted - but it now rests on the wrong-aggregates
   argument rather than on a mechanism that no longer works that way. The comment in the source
   should be corrected when this section is implemented.

## Revision log addendum (same pass, round 3 - coordinator, measured)

One change, made directly by the coordinator against ajv 8.20.0 in
`wiz-services/node_modules`; it corrects a keyword this document had left unspecified at the
schema root, and it is a correctness fix, not a refinement.

3. THE GENERATED SCHEMA ROOT CARRIES `"unevaluatedProperties": false`, NEVER
   `"additionalProperties": false` (sections 11.4, 11.6, 11.6.1, and two new assertions in
   7.9). `additionalProperties` sees only the sibling `properties` map, so with conditional
   params deliberately sunk into `allOf[].then` branches - the pruning this design is built on
   - a root `additionalProperties: false` would reject EVERY correctly-formed paginated
   request. Measured, not reasoned. The companion fact, same measurement: `require("ajv")`
   returns a draft-07 build that treats `unevaluatedProperties` as an unknown keyword and
   ignores it, producing a validator that appears to run and enforces nothing - which is why
   the mandatory `"$schema": ".../2020-12/schema"` line must never be dropped: with it, that
   consumer mistake throws at compile time instead of degrading silently. The
   `additionalProperties` INSIDE a Kind A composite is a different, correct usage (the value
   type of an open-ended map) and is unchanged.

## Revision log addendum (same pass, round 2 - coordinator course-correction)

Two changes, received after the round above was already written; everything else above stands
unchanged.

1. `x-valueSource` COLLAPSED FROM A ROUTING HINT TO A PLAIN FACT MARKER (sections 11.5, 11.5.1,
   11.5.2, 11.6, 11.6.1, 12.2, 12.4, 7.9, 13.1, 13.2). Reasoning: an agent only ever reaches
   this endpoint for a data source it already found, and how it found it (recall vs. a name the
   user gave it directly) already tells it where else to look - the schema restating "catalog
   vs. runtime" was redundant and something the backend cannot know reliably anyway. Renamed:
   `"runtime"` -> `"external"`, `"runtime-unavailable"` -> `"unavailable"`,
   `"runtime-large"` -> `"too-large"`; the `"catalog"` value (always wiz-only, never backend-
   emitted) is removed from the design entirely, along with the `annotationClass`/
   `classifyQueryClass`-keyed boundary reasoning that referenced it. `description` now carries
   the concrete source in plain language as a MANDATORY, strengthened requirement (11.5.2) -
   it is the only place that fact survives.
2. `resolveTags` RE-FILED FROM AN OPTIMIZATION TO LOAD-BEARING (new section 12.0; D10, section
   8, re-filed from a tuning nit to a primary risk). With no `query-tags` endpoint and no
   catalogue ingestion this pass, resolveTags is the ONLY source of candidate values for a
   METADATA-class connector on the one path that reaches one (the user naming it directly -
   METADATA-class sources are filtered out of `search_schema`, `organizationSourceTypes.ts:197`).
   A timeout or cap trip is therefore the feature failing outright, not a graceful degradation.
   Section 12.3 adds a SHIP-BLOCKING acceptance check (run resolveTags=true against a real
   METADATA-class connector before shipping), the same class of requirement as D6's read-back
   check; section 7.10 adds the corresponding test; section 9's timeout/cap risk entry is
   re-filed to match.
## 0. One-paragraph summary

WorksheetTable.TabularSource currently expresses three parallel contracts
(targetKind = "endpoint" / "file" / "query"), each with its own field set and its own
hand-written Java loop to fill a TabularQuery bean. The "query" kind, added most recently,
turns out to already subsume almost everything the other two do by hand - composite filling,
ordering, and candidate validation are all expressible as three general, connector-agnostic
capabilities driven off @PropertyEditor metadata the connectors already declare. This
document deletes "endpoint" and "file" outright, deletes targetKind itself, and replaces
TabularEndpointBindingSupport's five hand-written methods with one general routine built on
three capabilities (composite-by-name filling, dependsOn topological ordering, tagsMethod
candidate validation) plus two narrow rules (POST refusal, java.io.File path resolution).

---

## 1. What exists today, by tier

### 1.1 The wire type - WorksheetTable.TabularSource

core/src/main/java/inetsoft/web/wiz/model/WorksheetTable.java:177-351

Fields today: datasourcePath, targetKind, target, params (Map<String,String>,
file-kind option bag), parameters (Map<String,String>, endpoint-kind URL-token values),
queryParams (Map<String,Object>, query-kind, already exists), jsonPath, expanded,
expandedPath, maxRows, sampleRows, lookup, lookupExpandArrays, lookupTopLevelOnly.

getTarget() carries @JsonAlias("endpoint") (:236) - a caller-compatibility alias from
before target existed. Being deleted along with target.

### 1.2 The build path - WorksheetTableService

core/src/main/java/inetsoft/web/wiz/service/WorksheetTableService.java

- buildTabularTable (:1417-1598) - resolves the data source, creates the TabularQuery via
  TabularUtil.createQuery (:1503), switches on targetKind to one of three "apply contract"
  methods (:1517-1525), applies maxRows/row-cap policy (:1531-1536), applies sampleRows
  (:1542), builds the TabularTableAssembly, calls loadColumnSelection, and rejects an
  empty-column result with a kind-specific message (:1569-1593).
- applyEndpointContract (:1613-1641) - thin wrapper: rejects params/queryParams fields
  that don't belong to this kind, then delegates to
  TabularEndpointBindingSupport.applyEndpointContract + applyLookupChain.
- targetKindOf (:1653-1671) - normalizes/validates targetKind, defaulting absent to
  "endpoint".
- applyQueryContract (:1703-1748) - the general contract already partially built: looks
  up each queryParams name in pmap, hard-errors on an unknown name (:1725-1731), converts
  and writes via invokeWriteMethod+coerceParam rather than the swallowing
  PropertyMeta.setValue (:1733-1740), warns (does not refuse) on inapplicable-but-set params
  via TabularSchemaExtractor.findInapplicable (:1745, :1830-1841). Refuses composites by
  name - text() (:1856-1858) turns a Map/List value into its own toString(), which
  coerceParam then can't type-convert and rejects.
- rejectForeignFields / rejectQueryParams (:1758-1820) - cross-kind field policing, all
  deleted with the kinds they police.
- applyFileContract (:1903-1967) - resolves the file property (fileTargetProperty,
  :1978-2010, by name "fileFolder" first, else the sole File-typed property, else
  ambiguous-error), splits target on the last '#' into path+sheet (:1929-1935), resolves the
  path against the connector's root folder (resolveTargetFile, :2032-2078 - refuses absolute
  paths and "..", checks canonical containment, checks existence), writes it, reads back the
  canonical path to catch a silent no-op (:1949-1958), applies parsing options
  (applyFileParams, :2090-2114, by name against pmap, refusing unknown names), then resolves
  the Excel sheet (resolveExcelSheet, :2235-2313 - refuses to guess on an unqualified
  multi-sheet workbook, reconciles the #sheet suffix against params.excelSheet).
- coerceParam (:2134-2213) - text-to-typed-value conversion shared by the file and query
  contracts; unchanged for scalars, currently the reason composites are refused.
- invokeWriteMethod (:2358-2378) - calls the setter directly (not PropertyMeta.setValue) so
  a failed invocation throws instead of being swallowed with LOG.error.
- rowCapRequiredFor (:2396-2398) - !TARGET_KIND_FILE.equals(targetKind); the exemption being
  removed.
- sandboxSampleLimit (:421-465) - keys the sampling exemption on TARGET_KIND_FILE.equals(kind)
  (:450), computed from the REQUEST alone (targetKindOf(src), :437), with no reference to the
  resolved query class.
- Constants (:3857-3865): TARGET_KIND_ENDPOINT="endpoint", TARGET_KIND_FILE="file",
  TARGET_KIND_QUERY="query" - all deleted.

### 1.3 The shared reflection helper - TabularEndpointBindingSupport

core/src/main/java/inetsoft/web/wiz/service/TabularEndpointBindingSupport.java - entirely
deleted. Five public methods:

- applyEndpointContract (:70-175) - sets endpoint, reads back to confirm, refuses a POST
  endpoint (read AFTER endpoint-set, because updatePagination decides requestType),
  fills RestParameters by name (missing-required check, then unknown-name check), reads back
  suffix as the one end-to-end proof.
- applyCustomSuffix (:185-208) - sets suffix directly on a generic/custom query (no
  endpoint catalogue); reads back to catch the silent no-op case of calling this on a named
  connector, whose suffix getter is derived from endpoint and whose setter is a documented
  no-op.
- applyLookupChain (:216-269) - sets lookupEndpoint0..4 by name-validated-against-
  getLookupEndpoints(i), reading back every write (silent no-op past index 4 or on an unknown
  name).
- applyCustomLookupChain (:290-322) - sets lookupUrl{i}/lookupJsonPath{i}/lookupKey{i}/
  lookupIgnoreBaseUrl{i} for a generic query, in a REQUIRED order per level (lookupUrl{i}
  first, since its setter is what grows the backing list the other three write into) and checks
  the {param<i+1>} placeholder is literally present before writing anything.
- requireRowCapWhenPaged / assertKnownEndpoint - support helpers, both reached-by-name
  reflection on methods the connector plugin declares but core cannot import
  (getEndpoints(), getLookupEndpoints(int), isPaged()).

This class is shared by TWO callers, not one:
- WorksheetTableService.buildTabularTable (wiz-services' /api/wiz/ws/table write path).
- WorksheetAgentController.addTabularTable (core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetAgentController.java:575-677)
  - the composer MCP plugin's own add_table op, a THIRD, independent caller with its own
  request shape (EditRequest, see section 6).

### 1.4 The declarative metadata already available

- PropertyEditor annotation (core/src/main/java/inetsoft/uql/tabular/PropertyEditor.java):
  tags(), tagsMethod(), dependsOn(), enabledMethod() - all reflectable via
  PropertyMeta.getEditor().
- PropertyMeta.getDependsOn() (core/src/main/java/inetsoft/uql/tabular/PropertyMeta.java:77-83)
  - already exposes editor.dependsOn().
- TabularQuerySchema.Param (core/src/main/java/inetsoft/uql/tabular/TabularQuerySchema.java)
  carries dependsOn (:266-272), tagsMethod (:258-264), elementParams (:335-341,
  populated by extractElementParams), all produced by TabularSchemaExtractor - UNCHANGED
  by this design, per the fixed wire.
- dependsOn today has exactly ONE runtime reader anywhere in the codebase:
  TabularUtil.callEditorMethods (core/src/main/java/inetsoft/uql/tabular/TabularUtil.java:801-812)
  - used only to sequence the async, per-field tagsMethod fetch threads in the composer
  dialog's round trip. It has NO effect on a plain property write anywhere today. Adding
  dependsOn declarations for this design's benefit is therefore risk-free to existing UI
  behavior.
- Confirmed real annotations: EndpointJsonQuery.getParameters() has
  @PropertyEditor(dependsOn = "endpoint") (connectors/inetsoft-rest/.../EndpointJsonQuery.java:156);
  getEndpoint() has @PropertyEditor(tagsMethod = "getEndpoints", ...) (:83-85);
  getLookupEndpoint0() has dependsOn = "endpoint", tagsMethod = "getLookupEndpoints0" (:199-206);
  getLookupEndpoint1() has dependsOn = "lookupEndpoint0", tagsMethod = "getLookupEndpoints1"
  (:227-231) - the full chain is annotated 0 to 4, confirmed.
- Gap found: RestJsonQuery's CUSTOM, per-level, hand-authored lookup fields
  (lookupJsonPath0/lookupKey0/lookupIgnoreBaseUrl0, and the equivalents for levels 1-4;
  connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/json/RestJsonQuery.java:377-419)
  declare NO @PropertyEditor(dependsOn=...) at all today, even though applyCustomLookupChain's
  Java control flow currently hard-codes "write lookupUrl{i} before the other three" as an
  ordering requirement (its setter is what grows the backing list the other three write into;
  see the class javadoc quoted in section 1.3). See sections 4.2 and 8 (flagged decision).
- Gap found: RestParameter and HttpParameter - the two composite element classes this
  design supports (section 3.1) - declare NO @Property annotations on any of their own
  getters/setters (verified: core/src/main/java/inetsoft/uql/tabular/RestParameter.java,
  HttpParameter.java, full read, no @Property anywhere). Because
  TabularSchemaExtractor.extractElementParams (:538-563) calls
  TabularUtil.findProperties(element), which filters on @Property, ELEMENTPARAMS FOR
  BOTH RestParameters.parameters AND EndpointJsonQuery.additionalParameters IS EMPTY TODAY
  - not "populated and left for later extension" as it reads at first glance. See section 8.

### 1.5 The classification precedent this design reuses

WizDatabaseController.classifyQueryClass (core/src/main/java/inetsoft/web/wiz/controller/WizDatabaseController.java:970-985)
already classifies a resolved query class as FILE via
SelectableTabularQuery.class.isAssignableFrom(queryClass) (:985-986) - this is the exact,
already-precedented test this design reuses for the sampling exemption (section 5), replacing
TARGET_KIND_FILE.equals(kind).

### 1.6 The composer plugin's own tabular-binding path

WorksheetAgentController.addTabularTable (core/src/main/java/inetsoft/web/wiz/worksheet/WorksheetAgentController.java:575-677),
driven by EditRequest fields endpoint/parameters/lookup/lookupExpandArrays/
lookupTopLevelOnly/suffix/customLookups
(core/src/main/java/inetsoft/web/wiz/worksheet/EditRequest.java:244-300). This is NOT
mentioned in the fixed wire (which only fixes WorksheetTable.TabularSource), but it directly
calls the five TabularEndpointBindingSupport methods this design deletes, so it cannot be left
as-is - see section 6.

plugin/composer (in the stylebi-wiz repo, not community) exposes add_table with a
schema carrying the same endpoint/parameters/lookup/suffix/customLookups shape
(plugin/composer/src/tools/tableTools.ts, worksheetTools.ts, bindingTools.ts in that repo -
confirmed present by grep, not read in full since it's outside this repo). That TypeScript
tool schema is OUT OF THIS DOCUMENT'S SCOPE (wrong repo) but is coupled to the EditRequest
change in section 6 and must be updated in lockstep - flagged in section 8.

---

## 2. The new wire (fixed by the human decision; not altered here)

    public static class TabularSource {
       private String datasourcePath;
       private Map<String, Object> queryParams;
       private Integer maxRows;
       private Integer sampleRows;
       // getters/setters only - no targetKind, target, params, parameters, jsonPath,
       // expanded, expandedPath, lookup, lookupExpandArrays, lookupTopLevelOnly
    }

Deleted fields and their replacement: targetKind (discriminator is dead weight - tableType
"tabular table" already discriminates), target (becomes a named queryParams entry, e.g.
endpoint or a java.io.File-typed property), parameters (becomes a nested map under the
connector's own composite property name, e.g. queryParams.parameters), params (becomes flat
queryParams entries by the connector's own property names), jsonPath/expanded/
expandedPath (become ordinary queryParams entries - they already are @Property names),
lookup (becomes queryParams.lookupEndpoint0, .lookupEndpoint1, etc by name),
lookupExpandArrays/lookupTopLevelOnly (become queryParams.lookupExpanded / .lookupTopLevelOnly).

GET /api/wiz/tabular/query-schema?path=X (WizTabularController.java:260, returning
TabularQuerySchema) is UNCHANGED - confirmed no code path in scope touches it.

> REVISION NOTE: sections 3 onward were rewritten after coordinator arbitration (see section 8).
> The scope rule for composites is Kind A only (RestParameters); the three custom-lookup-URL
> validations and the Excel multi-sheet-ambiguity refusal, both originally proposed for removal,
> are KEPT per that arbitration. Section 10 is new: a diff against the concurrent wiz-side design.

---

## 3. The general routine

### 3.1 New shared class: TabularQueryContractSupport

Replaces TabularEndpointBindingSupport in the same package (inetsoft.web.wiz.service),
because it is used by the same two callers (WorksheetTableService.buildTabularTable,
WorksheetAgentController.addTabularTable; see section 6). One entry point:

    public final class TabularQueryContractSupport {
       // Fill query's bean properties from queryParams, by the connector's own
       // property names, applying (in order): dependsOn topological ordering, the two
       // narrow named rules (custom-lookup-URL placeholder validation, POST refusal),
       // tagsMethod candidate validation, Kind-A composite-by-name filling (RestParameters
       // only), java.io.File path resolution (+ Excel multi-sheet-ambiguity refusal),
       // scalar type coercion + write + read-back - then return a redacted, human-readable
       // description of what was set (for the empty-columns error message).
       public static String applyQueryContract(TabularQuery query,
                                                Map<String, PropertyMeta> pmap,
                                                TabularQuerySchema schema,
                                                Map<String, Object> queryParams, String dsName)
          throws Exception;
    }

schema is obtained by the caller via "new TabularSchemaExtractor().extract(query,
query.getType())" - THIS IS REQUIRED, NOT OPTIONAL, because the schema's params list order
is the only reliable tie-break for capability 2's topological sort (section 3.3): pmap is a
plain HashMap (TabularUtil.getPropertyMap, unordered), and TabularUtil.findProperties goes
through java.beans.Introspector.getBeanInfo, whose descriptor order is NOT guaranteed to be
declaration order - whereas TabularSchemaExtractor.extract's params list is explicitly built
in @View-declaration order (enrich, :574-633). For RestJsonQuery's custom lookup fields, the
@View2 declaration order (:60-63) is lookupUrl0, lookupJsonPath0, lookupKey0,
lookupIgnoreBaseUrl0 - exactly the order the current hand-written code enforces. Using schema
order as the stable tie-break reproduces this correctly WITHOUT hard-coding it, so long as
the connector's @View declares them in a sane order (true for every shipped connector; see
section 4.2 for the dependsOn addition that makes this explicit instead of coincidental).

### 3.2 Algorithm, as ordered steps

    applyQueryContract(query, pmap, schema, queryParams, dsName):

     1. if queryParams is null/empty:
           throw "tabularSource.queryParams is required ... Ask GET /api/wiz/tabular/query-schema
                 for the names this data source accepts."

     2. unknownTop = [name in queryParams.keySet() if schema.getParam(name) == null]
        if unknownTop non-empty:
           throw "'<names>' is not a parameter of data source '<dsName>'. It accepts: <sorted
                 pmap.keySet()>."

     2b. CUSTOM LOOKUP URL PLACEHOLDER VALIDATION (capability 6, section 3.9) - a NARROW,
         NAME-PATTERN rule, kept per coordinator arbitration alongside POST refusal, run here
         (before any write) because a malformed template must never reach the connector even
         once: for every name in queryParams matching "lookupUrl<i>" (i = 0..4):
            value = text(queryParams.get(name))
            if blank(value): throw "'<name>' must not be blank."
            placeholder = "{param" + (i+1) + "}"
            if not value.contains(placeholder):
               throw "'<name>' must contain the literal placeholder '<placeholder>' so the id
                     extracted via this level's jsonPath/key is substituted into the request --
                     got: '<value>'."

     3. order = topologicalSort(queryParams.keySet(), schema)   -- see 3.3; throws on a
                                                                    declared cycle

     4. applied = []
        for name in order:
           param  = schema.getParam(name)
           prop   = pmap.get(name)
           value  = queryParams.get(name)

           4a. TAGSMETHOD VALIDATION (capability 3, section 3.5) -- runs BEFORE the write,
               and only for a non-composite param -- REVISED (A1 consistency): the guard is
               now the SAME composite test step 4b uses (TabularSchemaExtractor.
               isCompositeType, File excluded), not a RestParameters.class-specific test. No
               shipped composite in this codebase declares a tagsMethod today (confirmed:
               neither EndpointJsonQuery.parameters nor ODataQuery.functionParameters do), so
               this had no observable effect before A1 - fixed here only because leaving a
               type-name-specific exclusion beside the corrected step 4b would restate the
               exact mistake A1 removes:
               if param.tagsMethod present and not isComposite(prop's type):
                  candidates = invokeTagsMethod(query, param.tagsMethod)   -- null on
                               reflection failure; not fatal
                  if candidates non-null/non-empty and text(value) not among candidates'
                     values:
                     throw naming name, dsName, and the candidate list (bounded to 20)

           4b. WRITE. THIS IS THE TWO-LAYER VALIDATION BOUNDARY: steps 1/2/2b validated
               TOP-LEVEL keys against pmap/schema, which needs no live query state.
               Everything from here down validates NESTED keys against a composite SKELETON
               that only exists correctly once this param's own dependsOn prerequisites were
               written in an earlier iteration of this same loop (guaranteed by step 3's
               order) -- see the paragraph after this list for why getting this backwards is
               the worst class of error.
               if prop.getDescriptor().getPropertyType() is composite -- per
                  TabularSchemaExtractor.isCompositeType (promoted from isScalar, section 5);
                  File is SCALAR under isScalar (:636-638), so a File-typed property never
                  reaches this branch -- it is handled two branches below, unchanged:
                  -- REVISED (A1): Kind A vs Kind B is now a RUNTIME check, not a Java-type
                  -- check -- see section 3.4 for why "type == RestParameters.class" was wrong
                  skeleton = prop.getValue(query)          -- re-read NOW; correct only because
                             this param's own dependsOn prerequisites were already written in
                             an earlier iteration of THIS SAME LOOP, per step 3's order
                  elements = elementsOf(skeleton)          -- section 3.4.1: unwraps
                             RestParameters.getParameters() (a List), a T[] array, or a
                             List directly; anything else (null, a non-collection object) -> null
                  namesOk  = elements != null AND not elements.isEmpty() AND every element's
                             getName() (reflectively invoked, zero-arg, no @Property needed) is
                             non-null
                  if namesOk:
                     fillNamedSkeleton(...)    -- KIND A, generalized, section 3.4.1
                  else:
                     throw the Kind-B refusal message (section 3.4, verbatim below)  -- KIND B
               else if prop.getDescriptor().getPropertyType() == java.io.File.class:
                  file = resolveTargetFile(query, requireString(value, name), dsName)
                  invokeWriteMethod(prop, query, file)
                  readBack = prop.getValue(query)
                  if not (readBack instanceof File f and f.getCanonicalPath()
                              .equals(file.getCanonicalPath())):
                     throw "Failed to point '<dsName>' at '<name>'=<value> ..."
                  EXCEL MULTI-SHEET AMBIGUITY REFUSAL (capability 5b, section 3.8) runs here,
                  immediately after the file write succeeds -- see section 3.8 for the exact
                  check and the VERBATIM message (a cross-repo contract).
               else:
                  coerced = coerceParam(prop, name, text(value), "Parameter")
                  invokeWriteMethod(prop, query, coerced)
                  readBack = prop.getValue(query)
                  if not text(readBack).equals(text(coerced)):                  -- NEW, 3.6
                     throw "Setting '<name>' to <shown> appears to have had no effect:
                           reading it back returned <shown readBack> instead. Either
                           '<name>' is derived from another property rather than directly
                           settable on this connector (check GET .../query-schema for a
                           property this one depends on), or the write failed silently.
                           See the server log."

           applied.add(name + "=" + describe(prop, prop.getValue(query)))

     5. warnInapplicable(query, queryParams.keySet(), dsName)   -- unchanged (top-level names
                                                                    only; nested composite keys
                                                                    are not checked)

     6. POST REFUSAL (capability 4, section 3.7) -- AFTER the whole fill, because
        updatePagination(endpoint) is what decides requestType:
        requestTypeProp = pmap.get("requestType")
        if requestTypeProp != null and "POST".equals(requestTypeProp.getValue(query)):
           throw "... is a POST endpoint, which cannot be created this way yet ..."

     7. return String.join(", ", applied)

TWO-LAYER VALIDATION ORDERING, restated because it is the single most important design point
in this document: steps 1/2/2b validate every TOP-LEVEL name against pmap/schema before
anything is written. Step 4's per-name work (tagsMethod validation, composite-skeleton read,
write, read-back) can only run AFTER every name this param's dependsOn lists has ALREADY
BEEN WRITTEN, which step 3's topological order guarantees. A composite skeleton like
EndpointJsonQuery.getParameters() derives its declared-parameter set from the endpoint
currently set on the bean, so reading it before endpoint is set returns a contract built for
NO endpoint (empty), and every nested key in the caller's queryParams.parameters map would
then be reported as UNKNOWN, when the real problem is that endpoint either was not sent or
was rejected earlier in the same request. Getting this order backwards reports "wrong param
name" as "empty skeleton" instead - hence step 3 (ordering) is a hard prerequisite of step 4,
not a parallel, independently-orderable concern.

### 3.3 Capability 2 - dependsOn topological ordering

    topologicalSort(names, schema):
       present = set(names)
       edges   = { n -> [d for d in schema.getParam(n).dependsOn if d in present and d != n]
                   for n in names }             -- self-edges dropped; edges to an absent
                                                 -- prerequisite dropped
       order   = schema.getParams() filtered to `present`, index-sorted   -- the FIFO seed
                 order (see 3.1 for why schema order, not pmap/Introspector order)
       run Kahn's algorithm over `edges`, popping ready nodes from `order`'s remaining
         sequence (deterministic, connector-declared tie-break)
       if any node remains unresolved when the queue empties:
          throw IllegalStateException(
             "The connector for '<dsName>' declares a dependsOn cycle among: <cyclic names,
             schema order>. This is a connector bug (its own @PropertyEditor annotations),
             not a request error.")
       return resolved order

A self-referential dependsOn (a param naming itself) is defused rather than treated as a
cycle - not observed in any shipped connector, but cheap to make safe.

### 3.4 Capability 1 - composite filling by name: Kind A vs Kind B, a RUNTIME rule (REVISED, A1)

REVISED THIS PASS (A1): the type-based detection this section previously stated -
"prop.getDescriptor().getPropertyType() is non-scalar AND is not RestParameters.class" - is
WRONG, and is replaced below by a runtime rule. It is wrong because the SAME Java type answers
both ways depending on which property it is, confirmed by direct source read of four
properties:

| Property | Java type | `getValue()` after prerequisites are set | Actual kind |
|---|---|---|---|
| EndpointJsonQuery.parameters | RestParameters | non-empty skeleton (endpoint set) | Kind A |
| ODataQuery.functionParameters | HttpParameter[] | non-empty skeleton (function set) | **Kind A** - previously misclassified Kind B by the type test |
| EndpointJsonQuery.additionalParameters | HttpParameter[] | null, always (no dependsOn at all) | Kind B |
| ServerFileQuery.columns | ColumnDefinition[] | null/empty until probed via a button, never as a byproduct of any @Property write | OUTPUT, not an input at all (A2, section 3.4.2) |

Rows 2 and 3 are the SAME Java type (HttpParameter[]) with OPPOSITE answers - so no test on
`prop.getDescriptor().getPropertyType()` can separate them, and the previous rule's exclusion
of "not RestParameters.class" from Kind A was never the right boundary. Verified directly:
ODataQuery.getFunctionParameters() (connectors/inetsoft-odata/src/main/java/inetsoft/uql/
odata/ODataQuery.java:106-112) returns `function.getParameters()` - the parameters DECLARED IN
THE SERVICE'S $metadata for the currently-selected function, already carrying names, once
`function` (dependsOn = "function", :104) has been set. And setFunctionParameters
(:115-147) does EXACTLY a by-name comparison on write: it walks
`oldParams[i].getName().equals(functionParameters[i].getName())` for each index and sets
`applyChanges = false` on any mismatch (:128-130) - SILENTLY, no exception, no log line. That
silent no-op on a name mismatch is precisely the failure class this whole contract exists to
catch (section 3.6's read-back check exists for exactly this reason), which is why this
property must be HANDLED, not refused: refusing it does not avoid the silent-failure risk, it
just relocates it to whatever ad-hoc suffix-writing workaround a caller would resort to instead.

THE CORRECTED RULE, evaluated at step 4b (section 3.2) when the topological order reaches the
param - i.e. after this param's own dependsOn prerequisites were already written in an earlier
iteration of the same loop:

    skeleton = prop.getValue(query)
    elements = elementsOf(skeleton)   -- section 3.4.1
    non-empty, elements' getName() all resolve  -> KIND A, fill by name (3.4.1)
    null / empty / unnamed                       -> KIND B, refuse by name (message below)

CONSEQUENCE, STATED PROMINENTLY: this REMOVES the "exactly ONE recognized composite class"
limitation this section previously declared. No class name is enumerated anywhere in the
detection rule (RestParameters is not special-cased; it simply happens to be the composite
every one of today's 65 catalogued connectors' traffic goes through). A THIRD-PARTY CONNECTOR
WHOSE OWN COMPOSITE PROPERTY RETURNS A NAMED SKELETON AFTER ITS OWN PREREQUISITES ARE SET IS
SUPPORTED AUTOMATICALLY, with no core-side change and no per-connector registration - the
routine is genuinely general for "any composite that resolves a named skeleton," not general
only across the two shapes this codebase happens to ship today.

Two concrete Kind A examples, both fillable through the SAME `fillNamedSkeleton` routine
(section 3.4.1) with no per-class code:

    queryParams: { endpoint: "Issues", parameters: { owner: "acme", repo: "app" } }
       -- EndpointJsonQuery.parameters (RestParameters), skeleton keyed off "endpoint"

    queryParams: { entity: "Orders", function: "GetOrderTotal",
                   functionParameters: { year: "2024" } }
       -- ODataQuery.functionParameters (HttpParameter[]), skeleton keyed off "function" -
          PREVIOUSLY REFUSED as Kind B by the wrong type test; correctly filled after A1.

The caller never needs to know either element class's own fields (RestParameter has
label/placeholder/split; HttpParameter has type/secret) - it only supplies a value, keyed by a
name the live skeleton already carries. elementParams is NOT required for either case: the set
of legal names is read off the LIVE SKELETON at request time (section 3.4.1), not off the
static schema - which is exactly as well for Kind A, since elementParams is empty for both
RestParameter and HttpParameter regardless (verified above).

KIND B - EMPTY, NO PREREQUISITE TO WAIT FOR. EndpointJsonQuery.additionalParameters is this:
it starts null, has no dependsOn, and never resolves to a skeleton under any sequence of
writes - the caller would have to build each element's name/value/type/secret from scratch,
which genuinely requires knowing the element's field structure, exactly what elementParams
cannot supply (it is empty for every composite element class in this codebase - RestParameter,
HttpParameter, QueryParameter, ColumnDefinition all carry ZERO @Property annotations on their
own getters/setters, verified by full read of each; TabularSchemaExtractor.extractElementParams,
:538-563, therefore produces an empty list for all four). KIND B IS OUT OF SCOPE THIS PASS,
PERMANENTLY UNTIL THE FOLLOW-ON BELOW LANDS, AND IS REFUSED BY NAME, never dropped silently.

REVISED MESSAGE (A3 - the previous wording was self-contradictory): the earlier text ended
with "See GET .../query-schema's elementParams for '<name>' ..." - but elementParams IS
PERMANENTLY EMPTY for every Kind B composite in this codebase, which is the very reason Kind B
is unsupported. That sentence pointed the caller at a field guaranteed to have nothing in it.
Corrected text (part of the message set):

    "'<name>' is a composite parameter this connector builds from a list of new elements,
     which is not supported yet. For an additional query-string or header value on a REST
     endpoint, write it directly into the endpoint's URL suffix / parameters instead. This
     connector's own query-schema does not carry a usable element description for '<name>'
     either (its element type declares no per-field metadata), so there is no other endpoint
     to check before concluding this connector has no way to express it through this API today."

NO @Property ANNOTATIONS ARE ADDED to RestParameter, HttpParameter, QueryParameter, or
ColumnDefinition in this pass (D2, section 8 - UNCHANGED by this correction: A1 makes MORE
composites usable through the existing four classes, it does not change whether those four
classes gain new annotations). Rejected explicitly by the coordinator: this design's own
section 9 (risks) had flagged that such an addition was never verified against whether
TabularUtil.findProperties or LayoutCreator carries some other assumption (e.g. a matching
@View reference) for a class with no @View at all - an unverified risk that would land on
the composer dialog's live rendering path for zero remaining production benefit, since Kind A
support after A1 no longer depends on any such addition at all.

FOLLOW-ON, NOT TAKEN NOW: to support Kind B later, add @Property to RestParameter's
name/label/placeholder/required/split/value and to HttpParameter's name/value/type/secret (and
the equivalent for QueryParameter/ColumnDefinition if those composites are ever prioritized) -
a small, one-time, core-side change (inetsoft.uql.tabular, not per-connector) that would cover
every connector using any of these shapes at once - AFTER FIRST VERIFYING it does not disturb
LayoutCreator/findProperties on a class with no @View. Deliberately not taken in this pass.

#### 3.4.1 Skeleton-fill-by-name (REVISED, A1 - generalized, not RestParameters-specific)

REVISED THIS PASS: this algorithm now operates on ANY composite the runtime rule (section 3.4)
classifies Kind A, not only RestParameters - it is the SAME routine section 3.2 step 4b calls
for both EndpointJsonQuery.parameters and ODataQuery.functionParameters (and any future
composite of any shape that resolves a named skeleton). The generalization is entirely in one
small helper, `elementsOf`, used both here and by step 4b's Kind A/B check:

    elementsOf(skeleton):
       if skeleton instanceof RestParameters rp: return rp.getParameters()   -- a List
       if skeleton is a T[] array:               return Arrays.asList(skeleton)
       if skeleton instanceof List:               return skeleton
       else:                                       return null

    reflectGetName(element)  -- element.getClass().getMethod("getName").invoke(element) as
                                String; null on NoSuchMethodException (never fatal here - the
                                caller, step 4b, already used this to decide Kind A vs B)
    reflectSetValue(element, text)  -- element.getClass().getMethod("setValue", String.class)
                                       .invoke(element, text); NoSuchMethodException here (as
                                       opposed to in the Kind A/B check) means the connector
                                       lied about being Kind A - throw naming the element's own
                                       class, a CONNECTOR BUG message, not a validation refusal
    reflectIsRequired(element)  -- element.getClass().getMethod("isRequired").invoke(element)
                                    as boolean; NoSuchMethodException -> false (this element
                                    type has no "required" concept at all - HttpParameter is
                                    exactly this: it has no isRequired(), unlike RestParameter,
                                    so a missing value on an HttpParameter-backed skeleton, e.g.
                                    ODataQuery.functionParameters, is simply left unset, never
                                    flagged missing. STATED LIMITATION, not a bug this pass
                                    introduces: today's connectors cannot express "this OData
                                    function parameter is mandatory" through this API at all,
                                    the same way $metadata's own nullable/required flag on a
                                    function parameter has no home in HttpParameter's fields
                                    either. Not fixable without HttpParameter itself changing,
                                    which is out of scope, section 3.4's D2/follow-on.)

    fillNamedSkeleton(query, prop, name, requestedValue, dsName):
       if not (requestedValue instanceof Map):
          throw "'<name>' must be a JSON object of {parameterName: value}, got: <shape>."

       skeleton = prop.getValue(query)         -- re-derive fresh; for RestParameters this is
                                                -- EndpointJsonQuery.getParameters() deriving
                                                -- from "endpoint" every call (:157-161); for
                                                -- HttpParameter[] this is
                                                -- ODataQuery.getFunctionParameters() deriving
                                                -- from "function" every call (:106-112)
       elements = elementsOf(skeleton)
       if elements == null or elements.isEmpty():
          throw "Could not read the parameter contract for '<name>' of '<dsName>'." -- should
                not happen: step 4b already confirmed non-empty/named before calling this

       supplied = new LinkedHashMap(requestedValue)
       missing  = []
       for element in elements:
          elementName = reflectGetName(element)      -- non-null, confirmed by step 4b
          v = supplied.remove(elementName)
          if v != null and not blank(text(v)): reflectSetValue(element, text(v).trim())
          else if reflectIsRequired(element): missing.add(elementName)

       if missing non-empty:
          throw "'<name>' of '<dsName>' requires: <missing>. Supply them; do not guess an
                identifier."
       if supplied non-empty (leftover nested keys):
          throw "'<name>' of '<dsName>' has no parameter(s) named: <supplied keys>. Its
                parameters are: <full list, required flagged where reflectIsRequired resolves,
                omitted where the element type has no such concept>." -- names the ENCLOSING
                composite, per the message-quality requirement

       prop's setter is invoked with the skeleton object itself (not a copy) -- writeback:
                                            RestParameters.getValue()/HttpParameter[]-typed
                                            getters both return a FRESH value on some calls
                                            (RestParameters, confirmed, EndpointJsonQuery.java:
                                            157-161; HttpParameter[], confirmed,
                                            ODataQuery.java:106-112), so mutating elements in
                                            place does NOT persist without this explicit set
       readBack = prop.getValue(query)     -- re-derive again
       for each filled name: confirm readBack's corresponding element's value equals what was
          set (reflectGetName to find the matching element, then compare its value the same
          way, reflectively); on mismatch: throw naming the composite AND the specific nested
          name (same message shape as the general scalar read-back check, section 3.6, but
          naming the enclosing composite too)

#### 3.4.2 ServerFileQuery.columns is an OUTPUT, not an input (A2 - NEW, would otherwise
     deadlock the agent)

ServerFileQuery.getColumns() carries `@Property(label = "Columns", required = true)`
(connectors/inetsoft-serverfile/src/main/java/inetsoft/uql/serverfile/ServerFileQuery.java:
271-275) while being Kind B under the rule above (no dependsOn; `super.getColumns()` stays
null/empty until the connector's own "Refresh Column Definitions" button calls loadColumns()
explicitly, :612-622 - never as a side effect of any @Property write, including fileFolder).
If `@Property.required` were translated mechanically into JSON Schema's `required` array
(section 11), the result DEADLOCKS THE AGENT: it sees `columns` marked required, tries to
construct a ColumnDefinition[] to satisfy it, is refused as Kind B, reads "not supported yet" -
but the schema still says required, with no way to satisfy it and no way to omit it. Its real
semantics is exactly what its own javadoc says: "column definitions that have been MODIFIED
FROM the source data" - a product of probing, read by loadOutputColumns() (:287-310), not
written by any caller. Confirmed: today's applyFileContract never sets this property, and file
tables still build correctly without it. FIX (detailed in section 11.4): a Kind B composite -
this one included - never enters the `required` array, and is preferably omitted from the
generated schema entirely, the same treatment as a derived no-op (`suffix` on a named
connector); if kept as a documentation stub, it carries `x-output: true` instead.

### 3.5 Capability 3 - tagsMethod candidate validation

Subsumes assertKnownEndpoint (getEndpoint() has tagsMethod = "getEndpoints") and all of
applyLookupChain's validation (getLookupEndpoint0() has tagsMethod = "getLookupEndpoints0",
dependsOn = "endpoint", etc), plus (newly, since it is generic) ServerFileQuery.excelSheet
(tagsMethod = "getExcelSheetNames", dependsOn = {"fileFolder"} - confirmed directly against
ServerFileQuery.java:120-123; an earlier internal note in this document's drafting mistakenly
assumed excelSheet had no tagsMethod at all, corrected here) - into one check, run
generically for ANY param with a declared tagsMethod, not specific to endpoint/lookup names.

    invokeTagsMethod(bean, methodName):
       -- moved into TabularUtil, extracted from the existing String[]/String[][] branch in
       -- TabularUtil.callEditorMethods (:817-833)
       method = bean.getClass().getMethod(methodName)   -- zero-arg, reached by name
       value  = method.invoke(bean)
       if value instanceof String[] s:    return pairs where label==value==s[i]
       if value instanceof String[][] p:  return p as-is   -- {label, value} per row,
                verified against callEditorMethods:824-832
       else: return null
       -- any reflection failure caught and returns null, NOT fatal

Validation: text(requestedValue) must equal some candidates[i][1] (the value slot, index 1).
On mismatch, the exact message:

    "'<name>' has no value '<requested>' among '<dsName>''s <label-for-name> choices.
     Choices: <candidates[i][1] sorted, sample capped at 20>."

Because excelSheet is now covered by this generic capability, a caller who supplies a WRONG
sheet name gets this message; a caller who supplies NO sheet name at all on an ambiguous
workbook is a different failure, covered by capability 5b (section 3.8), since tagsMethod
validation only fires when the param is actually present in queryParams.

applyLookupChain's own <=5 chain-length check becomes unnecessary: only lookupEndpoint0..4
are declared @Property names, so a caller-supplied lookupEndpoint5 is caught at step 2
(unknown top-level name) with the full legal-name list, a MORE SPECIFIC message than "chain
longer than 5" ever was. applyLookupChain's "read back after every write, because
setLookupEndpoint silently no-ops" is exactly what the general scalar read-back check
(section 3.6) does for every scalar param, not just this one.

### 3.6 The general read-back-equality check (new capability, ACCEPTED per D6)

THIS IS A GENUINELY NEW BEHAVIOR, not present in today's applyQueryContract. Today's
per-param "applied" message reads the property back for DISPLAY but never COMPARES it to
what was written - so a caller sending queryParams: {"suffix": "/v1/x"} against a NAMED
connector (whose setSuffix is a documented no-op) would have that value silently discarded
with NO ERROR AT ALL. This reproduces exactly the "tool accepted malformed input and produced
a plausible-but-wrong result" failure this codebase's own testing principle calls out, and is
the direct analog of applyCustomSuffixRejectsSilentNoOpOnNamedConnectorQuery (section 7, test
#17) - a scenario with NO HOME in the new design unless this check exists.

Design: after every scalar write, compare text(prop.getValue(query)) to text(coerced) (the
exact Java value just passed to the setter). Mismatch throws immediately, naming the
property and both values (secrets redacted). Exact message:

    "Setting '<name>' to <shown coerced> appears to have had no effect: reading it back
     returned <shown readBack> instead. Either '<name>' is derived from another property
     rather than directly settable on this connector (check GET .../query-schema for a
     property this one depends on), or the write failed silently. See the server log."

ACCEPTED AS DESIGNED (the broader, per-scalar-param version, not narrowed to only dependsOn
prerequisites/composites) - but gated on a REQUIRED implementation-time verification; see
section 7.8's acceptance criterion. NO GENERAL END-TO-END DERIVED-PROPERTY READ-BACK BEYOND
THIS is added: the per-param check already subsumes the old suffix-specific and
file-path-specific end-to-end proofs.

### 3.7 Capability 4 - POST refusal (unchanged in substance, moved verbatim)

pmap.get("requestType") read AFTER the whole fill loop (not per-param), because
updatePagination(endpoint), triggered as a side effect of writing endpoint, is what decides
requestType. Message unchanged:

    "Endpoint '<value of endpoint if present>' of '<dsName>' is a POST endpoint, which
     cannot be created this way yet: its request body comes from a template that only the
     connector's own dialog populates."

with the endpoint-name clause replaced by "The query on" when queryParams has no endpoint
entry at all.

### 3.8 Capability 5 - java.io.File params accept a string path, PLUS 5b - Excel
    multi-sheet-ambiguity refusal (KEPT per D4, cross-repo contract)

Detection: prop.getDescriptor().getPropertyType() == java.io.File.class (the same test
isFileProperty already uses). The caller names the property directly (e.g.
queryParams.fileFolder); no ambiguity-guessing across multiple File-typed properties is
needed since the caller picked one by name.

resolveTargetFile (:2032-2078) is reused VERBATIM - already connector-agnostic (resolves
relative to getRootFolder(), reached by name; refuses absolute paths and "..", checks
canonical containment, checks existence).

THE "path#sheet" SPLIT MOVES TO WIZ, ENTIRELY (unchanged from the earlier draft): the caller
sends queryParams.fileFolder (the path) and queryParams.excelSheet (the sheet) as TWO
SEPARATE entries. The topological sort already orders fileFolder before excelSheet for free
via the existing dependsOn = {"fileFolder"} declaration. NO BACKEND SPLITTER IS WRITTEN.

CAPABILITY 5b - KEPT, NOT DROPPED, per coordinator arbitration (D4). The principle stated
explicitly, since it also governs capability 6 (section 3.9): A CHECK SURVIVES WHEN ITS
DECLARATIVE EQUIVALENT DOES NOT EXIST AND DROPPING IT CONVERTS A PRE-FLIGHT REFUSAL INTO A
SILENT METERED (or, here, silently-wrong-annotation) FAILURE. Omitting a sheet name on a
multi-sheet workbook does not error under the connector's own default (first sheet) - it
silently binds the wrong sheet, and for wiz specifically this happens DURING ANNOTATION,
poisoning everything annotated from that file. This is confirmed as a live, exact-string
cross-repo contract: wiz-services/src/services/tabular/tabularFileClient.ts's
parseExcelSheetAmbiguity (:262-286) parses today's message with the regex
/\bhas (\d+) sheets, so one has to be named: (.+?)\. Put it in tabularSource\.params\.excelSheet/
- the STABLE anchor substring is "has (\d+) sheets, so one has to be named: (.+?)\." and the
UNSTABLE part is the remedy clause naming the now-deleted tabularSource.params.excelSheet.

Property-finding heuristic, matched EXACTLY to the wiz-side design's findSheetProperty
(stylebi-wiz docs/superpowers/specs/2026-08-25-tabular-unified-query-contract-wiz-design.md
section 4.3) so both sides agree on which property is "the sheet one": among
schema.getParams(), the sheet property is any param whose javaType is java.lang.String, whose
tagsMethod is non-empty, and whose dependsOn includes the file property's name.

CORRECTION (2026-08-27). This section previously recorded excelSheet as the unique property
matching all three on ServerFileQuery. It is not: encoding matches all three as well
(ServerFileQuery.java:138-143 -- dependsOn = {"fileFolder"}, tagsMethod = "getEncodingTypes"),
because the three conditions describe an editor that recomputes its choices when the file
changes rather than anything about selecting a sheet, and dependsOn is an editor-redraw
dependency rather than a semantic gate. OneDriveQuery matches nothing at all, since it names its
file with a String rather than a java.io.File. findSheetParam (TabularQueryContractSupport)
returns the FIRST match in schema.getParams() order, which is @View presentation order, and
ServerFileQuery's @View lists excelSheet above encoding -- so capability 5b names the right
property today by layout order, not by the heuristic being decisive. Reordering that @View would
have it tell a caller to supply queryParams.encoding to pick a sheet. The OTHER consumer of the
same heuristic, TabularQueryParamsSchemaBuilder's format: "file-path"/"file-sheet" role markers,
was removed for this reason: a property is identified by its own name, its description (the
connector's @Property label) and its pattern instead.

    -- runs in step 4b, immediately after a successful File-typed write:
    isExcel = callQueryMethod(query, "isExcel", dsName)         -- reflection, unchanged
    if isExcel == Boolean.TRUE:
       sheetNames = callQueryMethod(query, "getExcelSheetNames", dsName) as String[],
                    blanks dropped
       if sheetNames.length > 1:
          sheetProp = the schema param matching the heuristic above (relative to this file
                      property's name), or null if none exists on this connector
          if sheetProp != null and sheetProp.name not in queryParams.keySet():
             throw the VERBATIM message below

EXACT MESSAGE (part of the message set; cross-repo contract with tabularFileClient.ts, which
must update its regex to match the new anchor + remedy clause in the SAME change set as this
backend change, since a silent regex mismatch produces no error anywhere, only a silently
wrong sheet):

    "'<fileValue>' of '<dsName>' has <N> sheets, so one has to be named: <sheet1>, <sheet2>,
     .... Supply it as queryParams.<sheetPropName>."

Concretely, for ServerFile: "'2024/sales.xlsx' of 'SaaS/Files' has 3 sheets, so one has to be
named: Q1, Q2, Q3. Supply it as queryParams.excelSheet." The stable anchor substring
tabularFileClient.ts's regex should key on is unchanged in shape: "has (\d+) sheets, so one
has to be named: (.+?)\." - only the remedy clause changed, from "Put it in
tabularSource.params.excelSheet" to "Supply it as queryParams.<sheetPropName>.".

### 3.9 Capability 6 - custom lookup URL placeholder validation (KEPT per D3)

REJECTED FOR REMOVAL by the coordinator; an earlier draft of this document proposed dropping
these three checks as "connector-specific special cases." The coordinator's counter: the POST
refusal (capability 4) is exactly as connector-specific and was kept, and the cost of dropping
this one is concrete, not theoretical - a malformed lookup template fails SILENTLY, with every
row of the lookup requesting the same URL, which is ONE BILLED REQUEST PER ROW for a metered
API, and no error anywhere. Same survival principle as capability 5b (section 3.8): a check
survives when its declarative equivalent does not exist (there is no @PropertyEditor shape for
"this string must contain a literal my own array index determines") AND dropping it converts
a pre-flight refusal into a silent, metered failure.

Detection: by NAME PATTERN on the top-level queryParams keys, run at step 2b (section 3.2),
before any write: any key matching "lookupUrl<i>" for i = 0..4 (RestJsonQuery's custom,
hand-authored per-level lookup URL properties - distinct from EndpointJsonQuery's named
lookupEndpoint0..4, which are validated by capability 3 instead, since those select a
catalogued lookup by name rather than accepting a hand-authored template).

    for name matching "lookupUrl<i>" in queryParams.keySet():
       value = text(queryParams.get(name))
       if blank(value):
          throw "'<name>' must not be blank."
       placeholder = "{param" + (i+1) + "}"      -- RestJsonQuery auto-names each level's
                                                  -- substitution parameter "param" + (i+1),
                                                  -- 1-indexed by position
       if not value.contains(placeholder):
          throw "'<name>' must contain the literal placeholder '<placeholder>' so the id
                extracted via this level's jsonPath/key is substituted into the request --
                got: '<value>'."

This subsumes all three original applyCustomLookupChain checks: blank URL (first branch),
missing placeholder (second branch, i=0 case), and placeholder at the wrong position (second
branch, i>0 case - the SAME check, since a URL for lookupUrl1 containing "{param1}" instead
of "{param2}" simply fails the "contains <placeholder>" test for i=1). No separate
"chain-length > 5" check is needed: only lookupUrl0..4 are legal top-level names, so
lookupUrl5 is caught at step 2 (unknown name) with the full legal-name list.

---

## 4. buildTabularTable, after this change

    private AbstractTableAssembly buildTabularTable(Worksheet worksheet, WorksheetTable request)
       throws Exception
    {
       WorksheetTable.TabularSource src = request.getTabularSource();
       // datasourcePath required; physicalSource-mismatch rejection; columns/
       // expressionColumns/windowColumns rejection -- all unchanged from today.

       String dsName = src.getDatasourcePath();
       XDataSource dataSource = xrepository.getDataSource(dsName);   // unchanged
       TabularQuery query = TabularUtil.createQuery(dsName);         // unchanged
       Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
       TabularQuerySchema schema =
          new TabularSchemaExtractor().extract(query, dataSource.getType());

       String probeDesc = TabularQueryContractSupport.applyQueryContract(
          query, pmap, schema, src.getQueryParams(), dsName);

       // maxRows is OPTIONAL (revised 4.1). Supplied -> persisted, exactly as today.
       // Absent -> NOTHING is persisted; the probe below runs under a hint instead.
       if(src.getMaxRows() != null && src.getMaxRows() > 0) {
          query.setMaxRows(src.getMaxRows());
       }

       query.setSampleRowLimit(src.getSampleRows() == null ? 0
                                : Math.max(0, src.getSampleRows()));

       TabularTableAssembly table = new TabularTableAssembly(worksheet, request.getTableName());
       // ... unchanged: setQuery, setSourceInfo, empty-column check,
       //     EXCEPT loadColumnSelection now receives the probe hint from 4.1 instead of an
       //     empty VariableTable,
       // collapsed to the ONE branch that used to be the query-kind's, since there is only
       // one kind left.
    }

### 4.1 Row cap: OPTIONAL, with a probe-only default (REVISED ROUND 4 - supersedes D5)

REPLACES the earlier "unconditional requirement" design in this section. That was a
literal reading of "all tabular sources support a row cap" as "all tabular sources must be
GIVEN one"; the human has since corrected it. `requireMaxRows` as sketched previously is NOT
implemented, and nothing in this design rejects a request for lacking `maxRows`.

The rule has two halves, and the distinction between them is the whole point:

| `tabularSource.maxRows` | probe execution | persisted on the query |
|---|---|---|
| supplied (`> 0`) | small hint (see below) | YES - `query.setMaxRows(n)`, unchanged from today |
| absent | `HINT_MAX_ROWS = 20` | **NOTHING** |

`rowCapRequiredFor` is deleted (its `targetKind` argument no longer exists), and
`requireRowCapWhenPaged` is deleted with the rest of `TabularEndpointBindingSupport` — but
neither is replaced by a rejection. `isPaged()` no longer gates anything here.

THE LOAD-BEARING DISTINCTION: `XQuery.HINT_MAX_ROWS` bounds ONE execution; `setMaxRows` writes
`<maxrows>` and bounds EVERY future render. The default 20 must use the first and must never
reach the second. Concretely, at the probe call site — which today passes an empty table:

    // today
    table.loadColumnSelection(new VariableTable(), true, null);

    // this design
    VariableTable probeVars = new VariableTable();
    probeVars.put(XQuery.HINT_MAX_ROWS,
                  String.valueOf(Math.max(20, src.getSampleRows() == null ? 0 : src.getSampleRows())));
    table.loadColumnSelection(probeVars, true, null);

`max(20, sampleRows)` because a caller asking for sample rows must actually get that many; the
probe otherwise needs only enough rows to resolve column structure, for which 20 is ample.
`HINT_MAX_ROWS` travels through `VariableTable`, which is the parameter `loadColumnSelection`
already takes (`TabularTableAssembly.java:158`) — no new plumbing.

WHY THE DEFAULT IS NOT PERSISTED. `buildTabularTable`'s existing comment argues for persisting
rather than hinting, because "a hint bounds one execution, and what has to stay bounded is
every future render of this table", and it justifies that by noting `createTables` sets
`designMaxRows` to 0.

THAT JUSTIFICATION IS STALE, and the comment should be corrected while this section is being
implemented: `designMaxRows` is no longer where a cap lives. Per #75989 it is "deliberately
never set there any more" and the cap is applied PER ASSEMBLY instead
(`WizUtil.applySampledPreviewCap` / `sampledPreviewCap`, whose own javadoc records that
reading `designMaxRows` back "silently promoted a sampled render to full data"). So the
specific danger the comment cites no longer exists in the form it describes.

The CONCLUSION nevertheless stands, on a stronger reason than the one it was originally given.
A cap the CALLER chose is still persisted — that is their explicit instruction about this
table. A cap the SERVER invented must not be, because:

> A table silently truncated to 20 (or 5000) rows produces WRONG AGGREGATES. A chart bound to
> it shows an "order total" that is a fraction of the real figure, with nothing anywhere
> indicating the number is incomplete. Over-billing is visible and recoverable; a confidently
> wrong number reported to a user is neither.

So row limiting is an execution-time concern, not part of the table's definition. The known
cost of this choice, stated plainly rather than buried: a saved table bound to a paginated
metered API will, on each render, page until the service runs out of data unless the caller
supplied `maxRows`. That is the accepted trade, made deliberately.

MITIGATION, in wiz's guidance rather than in a backend rejection: the agent should confirm
with the user before binding a paginated, metered data source without a cap. That is the only
point in the flow where both failure modes — wrong numbers and a runaway bill — can be avoided
at once, because it is the only point where the intent behind the question is known.

### 4.2 Connector-side annotation addition (ACCEPTED per D7; narrowed from an earlier draft)

ONE companion change this pass, not two - the earlier draft's second item (@Property
additions to RestParameter/HttpParameter) is REJECTED per D2 (section 3.4) and moved to a
deliberate follow-on, not taken now.

ACCEPTED: RestJsonQuery's custom lookup levels (RestJsonQuery.java, levels 0-4, ~20
properties) - add @PropertyEditor(dependsOn = "lookupUrl{i}") to lookupJsonPath{i}/
lookupKey{i}/lookupIgnoreBaseUrl{i} for each level i. The coordinator's reasoning: dependsOn
is an EXISTING legal attribute on getters that already carry @PropertyEditor (distinct from
D2's rejected @Property addition, which would touch classes with no @PropertyEditor usage at
all today) - much lower risk. Verified risk-free on its own terms: dependsOn has exactly one
runtime reader anywhere in the codebase today (TabularUtil.callEditorMethods's async
tags-fetch sequencing in the composer dialog), and none of these four fields has a
tagsMethod, so the declaration currently has ZERO observable effect anywhere until this
design's topological sort reads it.

IMPLEMENTATION-TIME VERIFICATION STILL REQUIRED (same category the coordinator asked for on
D6): confirm adding dependsOn to a getter that ALREADY has @PropertyEditor (just adding one
more attribute to an existing annotation instance) does not surprise LayoutCreator in some
way not exercised by today's tests. This finding - that today's ordering works only by
coincidence of @View2 declaration order - is exactly the kind of thing this redesign should
be making explicit.

---

## 5. Every file that changes, and every deletion by file and method

WorksheetTable.java (core/src/main/java/inetsoft/web/wiz/model/):
  DELETE fields+getters/setters+javadoc: targetKind, target, params, parameters, jsonPath,
  expanded, expandedPath, lookup, lookupExpandArrays, lookupTopLevelOnly (:177-351). KEEP:
  datasourcePath, queryParams, maxRows, sampleRows. Rewrite the class javadoc (:69-78) to
  describe the single contract.

TabularEndpointBindingSupport.java (core/src/main/java/inetsoft/web/wiz/service/):
  DELETE THE ENTIRE FILE - applyEndpointContract, applyCustomSuffix, applyLookupChain,
  applyCustomLookupChain, requireRowCapWhenPaged, assertKnownEndpoint, setOptionalProperty,
  setRequiredProperty, getLookupEndpointsAt - every method in the class. (Its two narrow
  checks - custom-lookup-URL placeholder validation and the POST refusal - are NOT lost;
  they move into TabularQueryContractSupport as capabilities 6 and 4, sections 3.9 and 3.7.)

TabularQueryContractSupport.java (core/src/main/java/inetsoft/web/wiz/service/):
  NEW FILE. The general routine, section 3.

WorksheetTableService.java (core/src/main/java/inetsoft/web/wiz/service/):
  DELETE: applyEndpointContract (:1613-1641), applyFileContract (:1903-1967),
  fileTargetProperty (:1978-2010), isFileProperty (:2012-2017, moves into
  TabularQueryContractSupport), resolveTargetFile (:2032-2078, moves), applyFileParams and
  optionNames (:2090-2123, subsumed), targetKindOf (:1653-1671), rejectForeignFields and
  rejectQueryParams (:1758-1820), rowCapRequiredFor (:2396-2398), the three TARGET_KIND_*
  constants (:3857-3865). resolveExcelSheet/excelSheetNames (:2235-2329) are REWRITTEN, not
  deleted outright - their multi-sheet-ambiguity refusal (capability 5b) and message survive,
  their target#sheet-reconciliation logic does not (section 3.8). MOVE to
  TabularQueryContractSupport: coerceParam, text, describe, invokeWriteMethod,
  callQueryMethod. REWRITE: buildTabularTable per section 4, including the probe-hint change
  (4.1: loadColumnSelection receives HINT_MAX_ROWS = max(20, sampleRows) instead of an empty
  VariableTable, and maxRows is persisted only when supplied). NO requireMaxRows is added -
  maxRows is optional. CHANGE SIGNATURE: sandboxSampleLimit, per section 5.1 below.

TabularUtil.java (core/src/main/java/inetsoft/uql/tabular/):
  EXTRACT the String[]/String[][] tags-method-result normalization currently inline in
  callEditorMethods (:817-833) into a new "public static String[][] invokeTagsMethod(Object
  bean, String methodName)". callEditorMethods calls it instead of duplicating the logic. No
  behavior change to the existing async dialog path.

TabularSchemaExtractor.java (core/src/main/java/inetsoft/uql/tabular/):
  PROMOTE isScalar (:635-638, currently private) to a public static
  isCompositeType(Class<?> cls) (returning !isScalar(cls)).

TabularQuerySchema.java (core/src/main/java/inetsoft/uql/tabular/):
  ADD FIELD (section 11.2): private JsonNode queryParamsSchema, with getter/setter. params
  and dependencyMatrix are UNCHANGED - this is purely additive, and no other caller of this
  class exists besides WizTabularController.getQuerySchema (grep-confirmed), so it is safe.

TabularQueryParamsSchemaBuilder.java (core/src/main/java/inetsoft/uql/tabular/):
  NEW FILE (section 11.3-11.5, section 12). Builds the queryParamsSchema JsonNode from a
  TabularQuerySchema + a live (but still blank) TabularQuery instance; also implements the
  bounded resolveTags wrapper around TabularUtil.invokeTagsMethod (section 12.4).

WizTabularController.java (core/src/main/java/inetsoft/web/wiz/controller/):
  CHANGE getQuerySchema (:260-300, section 11.3): add
  @RequestParam(name = "resolveTags", required = false, defaultValue = "false") boolean
  resolveTags, and call TabularQueryParamsSchemaBuilder.build inside the existing try/finally
  connector-session block before returning schema. No other change to this method.

RestJsonQuery.java (connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/json/):
  ADD @PropertyEditor(dependsOn = "lookupUrl{i}") to lookupJsonPath{i}/lookupKey{i}/
  lookupIgnoreBaseUrl{i}, i = 0..4 (section 4.2, ACCEPTED per D7).

NOT CHANGED THIS PASS (D2): RestParameter.java, HttpParameter.java - no @Property additions;
see section 3.4's follow-on note.

EditRequest.java, WorksheetAgentController.java, WorksheetMutationSupport.java
(core/src/main/java/inetsoft/web/wiz/worksheet/):
  NOT CHANGED THIS PASS - see the revised section 6. An earlier draft had all three rewritten
  onto the general contract; that scope was withdrawn.

TabularEndpointBindingSupport.java (core/src/main/java/inetsoft/web/wiz/service/):
  NOT DELETED - see the revised section 6. It keeps exactly one consumer, the composer
  add_table path, and /ws/table no longer calls it at all.

### 5.1 sandboxSampleLimit signature change, precisely

Today: "static int sandboxSampleLimit(WorksheetTable request, WorksheetTableResponse
response)" (:421), computed purely from the request. The sampling exemption is now keyed on
the RESOLVED QUERY'S CLASS (SelectableTabularQuery.class.isAssignableFrom), which does not
exist until the assembly has been built. New signature:

    static int sandboxSampleLimit(Worksheet worksheet, WorksheetTable request,
                                  WorksheetTableResponse response)
    {
       // ... existing null/success/already-sampled/sampleRows<=0 checks, unchanged ...

       TabularQuery query = null;
       if(worksheet.getAssembly(request.getTableName()) instanceof TabularTableAssembly tta) {
          TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) tta.getTableInfo();
          query = info.getQuery();     // @Nullable
       }

       if(query == null || !SelectableTabularQuery.class.isAssignableFrom(query.getClass())) {
          return 0;
       }

       // ... existing ceiling/clamp logic, unchanged ...
    }

Call site applySandboxSampleRows already has worksheet in scope and already looks up the
same assembly a few lines later - pass worksheet into sandboxSampleLimit; a small duplicated
instanceof cast is an acceptable, low-risk choice rather than a required refactor.

---

## 6. The composer add_table path - OUT OF SCOPE (REVISED ROUND 5, reverses D1)

`WorksheetAgentController.addTabularTable` is NOT touched by this change, and
`TabularEndpointBindingSupport` is NOT deleted. This reverses D1 and everything an earlier
draft of this section specified; the rewrite it described was implemented and has since been
reverted (see the commit "Leave the composer add_table path on its own contract").

WHY THE EARLIER REASONING WAS BACKWARDS. The argument for pulling this path in was "community
does not compile without touching it, because it calls all five TabularEndpointBindingSupport
methods this design deletes." That is an argument for KEEPING THE CLASS, not for rewriting a
second caller. Deleting a class that has a working consumer, and then rewriting the consumer
to match, is a strictly larger change than not deleting it.

WHAT THIS CHANGE IS ACTUALLY FOR. The unified contract serves `/ws/table`, whose caller is the
query builder agent (`WorksheetTableController.createTables` ->
`WorksheetTableService.createTables` -> `buildTabularTable`). `addTabularTable` is a different
entry point, with a different wire (`EditRequest`), reached by a different client (the composer
MCP plugin, which does not route through wiz-services at all). Nothing about it was broken and
nothing about it is what this change set set out to fix.

WHY THIS IS NOT THE "TWO DRIFTING COPIES" PROBLEM THE OLD TEXT WARNED ABOUT. That problem was
three contracts branching inside ONE routine, discriminated by `targetKind`, where a change to
one branch silently altered the others' surroundings. What remains is two INDEPENDENT entry
points that each own their contract end to end:

    /ws/table  -> createTables -> buildTabularTable -> TabularQueryContractSupport  (new)
    composer   -> addTabularTable                   -> TabularEndpointBindingSupport (unchanged)

`TabularEndpointBindingSupport` now has exactly one consumer instead of two, which is strictly
less coupling than before this change, not more. Folding the composer path in later is a
possible future change; it is not a prerequisite for this one, and it should be justified on
its own terms rather than as a side effect of a deletion.

CONSEQUENTLY OUT OF SCOPE TOO: `plugin/composer`'s TypeScript `add_table` schema (the D1
extension). Its wire is unchanged, so it needs no change.

Design: delete EditRequest.endpoint/.parameters/.lookup/.lookupExpandArrays/
.lookupTopLevelOnly/.suffix/.customLookups; add one new field:

    // The whole tabular query, by the connector's own property names, for add_table when
    // binding a tabular/REST datasource -- see tabularSource.queryParams in the wiz
    // /ws/table contract for the identical shape and GET /api/wiz/tabular/query-schema for
    // what a given datasource accepts.
    Map<String, Object> queryParams,

addTabularTable rewrites to: resolve query/pmap/schema exactly as buildTabularTable does,
call TabularQueryContractSupport.applyQueryContract(query, pmap, schema, req.queryParams(),
dsName). No row-cap policy applies (revised 4.1: maxRows is optional); EditRequest has no
maxRows field for add_table today and does not need one added for this pass. The probe there
uses the same HINT_MAX_ROWS default. (Superseded question, kept for provenance: the row-cap
requirement is now unconditional, is not resolved in this pass - flagged section 8), then
proceed with the unchanged assembly-building tail.

EXTENDED PER D1, NOT LEFT AS A FOLLOW-UP: plugin/composer's TypeScript add_table tool schema
(plugin/composer/src/tools/tableTools.ts / worksheetTools.ts / bindingTools.ts, in the
stylebi-wiz repo, NOT community) currently exposes endpoint/parameters/lookup/suffix/
customLookups and MUST be updated in the SAME change set as the EditRequest change above -
the coordinator's ruling: "a Java wire that no longer matches its TypeScript caller is the
same breakage one layer up" as community failing to compile. This document does not specify
that TypeScript change (wrong repo, wrong tier - it is a plugin tool-schema concern), but it
is now a DELIVERY REQUIREMENT of this change set, not an item to track separately, and the
implementer must coordinate the two PRs to land together, or add_table breaks for every
caller between the two merges.

---

## 7. Test plan

### 7.1 TabularEndpointBindingSupportTest -> rewritten as TabularQueryContractSupportTest

23 tests today, entry point deleted. Mapping, by capability (REVISED from an earlier draft:
the three applyCustomLookupChain validation tests are now mapped to capability 6, KEPT, not
dropped, per D3):

- applyEndpointContractSetsEndpointAndBuildsSuffix -> tagsMethod validation (endpoint is
  valid) + scalar write/read-back, via queryParams={"endpoint":"Repos"}
- applyEndpointContractSubstitutesSuppliedParameterValues -> Kind A composite fill (3.4.1),
  via queryParams={"endpoint":"Repos","parameters":{"id":"42"}}
- applyEndpointContractRejectsMissingRequiredParameter -> Kind A fill, missing-required
  branch
- applyEndpointContractRejectsUnknownParameterName -> Kind A fill, unknown-nested-name branch
- applyEndpointContractRejectsUnknownEndpointName -> tagsMethod validation (3.5), unknown
  top-level value
- applyEndpointContractRejectsPostEndpoint -> POST refusal (3.7)
- requireRowCapWhenPagedThrowsForAPagedEndpoint / ...AllowsAnUnpagedEndpoint -> DELETED, not
  superseded: per the revised 4.1 a missing maxRows is no longer an error at all, so there is
  no equivalent assertion. The probe-hint behavior that replaces it is covered in 7.3.
- applyLookupChainSetsSingleLevel / ...SetsTwoLevelsInOrder -> dependsOn ordering (3.3) +
  tagsMethod validation, via queryParams={"endpoint":"Repos","lookupEndpoint0":"Issues",
  "lookupEndpoint1":"Comments"}
- applyLookupChainRejectsUnknownNameAtPositionZero / ...AtPositionOneNamingPositionOnesChoices
  -> tagsMethod validation, naming the right candidate set per chain position
- applyLookupChainRejectsChainLongerThanFive -> DELETED - a 6th level is an unknown top-level
  name, caught earlier with a better message (3.5)
- applyLookupChainDefaultsLeaveConnectorDefaultsUntouched / ...SuppliedFalseIsReadBack ->
  scalar write/read-back for lookupExpanded/lookupTopLevelOnly
- applyCustomSuffixSetsSuffixAndJsonPath -> scalar write/read-back, via
  queryParams={"suffix":"...", "jsonPath":"..."} against FakeCustomRestQuery
- applyCustomSuffixRejectsSilentNoOpOnNamedConnectorQuery -> THE GENERAL READ-BACK-EQUALITY
  CHECK (3.6), via queryParams={"suffix":"..."} against FakeNamedConnectorQuery
- applyCustomLookupChainSetsFourFieldsPerLevel -> dependsOn ordering (3.3) across
  lookupUrl0 -> {lookupJsonPath0,lookupKey0,lookupIgnoreBaseUrl0} (per 4.2's dependsOn
  addition)
- applyCustomLookupChainDefaultsIgnoreBaseUrlToFalse -> scalar default-preservation
- applyCustomLookupChainRejectsBlankUrl -> KEPT per D3 -> capability 6 (3.9), blank-URL branch
- applyCustomLookupChainRejectsUrlMissingPlaceholder -> KEPT per D3 -> capability 6 (3.9),
  missing-placeholder branch, i=0
- applyCustomLookupChainRejectsPlaceholderAtWrongPosition -> KEPT per D3 -> capability 6
  (3.9), same branch exercised at i=1
- applyCustomLookupChainRejectsChainLongerThanFive -> DELETED, same reasoning as the
  named-chain version above (lookupUrl5 is an unknown top-level name)

Net: 23 existing tests become roughly 20 direct-descendant cases (up from the ~17 an earlier
draft of this document proposed, now that capability 6 is kept) plus 3 explicitly dropped
(chain-length checks, made redundant by step 2's unknown-name message), plus new cases for
capabilities that had no test before (top-level unknown-name-with-full-list message; Kind-B
refusal by name; Excel multi-sheet-ambiguity refusal with the verbatim message; cycle
detection in the topological sort).

New fixtures needed: FakeNamedConnectorQuery (core/src/test/java/inetsoft/web/wiz/service/)
must gain real @PropertyEditor annotations it does not have today - verified: ZERO
@PropertyEditor annotations exist on this fixture currently. Add @PropertyEditor(tagsMethod
= "getEndpoints") to getEndpoint(), @PropertyEditor(dependsOn = "endpoint") to
getParameters(), @PropertyEditor(dependsOn = "endpoint", tagsMethod = "getLookupEndpoints0")
to getLookupEndpoint0(), @PropertyEditor(dependsOn = "lookupEndpoint0", tagsMethod =
"getLookupEndpoints1") to getLookupEndpoint1(). Also split its generic
getLookupEndpoints(int) into per-level zero-arg getLookupEndpoints0()/getLookupEndpoints1()
methods, because a tagsMethod target must be a zero-arg method reachable by
Class.getMethod(name) with no parameters - the fixture's current int-parameterized method
cannot be named as a tagsMethod value at all. A new FakeExcelLikeQuery fixture (File property
+ isExcel()/getExcelSheetNames() + a String property with dependsOn+tagsMethod matching the
capability 5b heuristic) is needed to test the Excel-ambiguity path without a real
ServerFileQuery instance.

### 7.2 WorksheetTableServiceKindFieldsTest - DELETE ENTIRELY (10 tests)

Every test pins rejectForeignFields/rejectQueryParams/targetKindOf-adjacent behavior over
fields that no longer exist.

### 7.3 WorksheetTableServiceRowCapTest - DELETE ENTIRELY (4 tests) [REVISED ROUND 4]

rowCapRequiredFor(String) is deleted and, per the revised 4.1, is NOT replaced by any
rejection - so there is no `requireMaxRows` to unit-test (an earlier draft of this section
specified one; disregard it). Replace instead with a class covering the probe-hint rule,
WorksheetTableServiceProbeHintTest:

- NO maxRows SUPPLIED -> the VariableTable handed to loadColumnSelection carries
  HINT_MAX_ROWS = "20", AND query.getMaxRows() is left at its default (assert the query was
  NOT capped). This second assertion is the one that matters: it is the only automated guard
  that a server-invented default never becomes a persisted truncation, which is the whole
  point of 4.1.
- NO maxRows, sampleRows = 50 -> HINT_MAX_ROWS = "50" (max(20, sampleRows)).
- maxRows = 5000 SUPPLIED -> query.getMaxRows() == 5000 (persisted), and the probe hint is
  still the small value, not 5000 (resolving column structure must not pull 5000 rows through
  a metered API).
- REQUEST WITHOUT maxRows IS ACCEPTED - explicitly assert no exception, on both a paginated
  and an unpaginated fixture. This pins the reversal: absence of a cap is legal now.

### 7.4 WorksheetTableServiceLookupWiringTest - REWRITE BOTH TESTS (2 tests)

Rewrite the JSON fixtures from {"tabularSource": {"datasourcePath": "myds", "endpoint":
"Repos", "lookup": ["Bogus"|"Issues"]}} to {"tabularSource": {"datasourcePath": "myds",
"queryParams": {"endpoint": "Repos", "lookupEndpoint0": "Bogus"|"Issues"}, "maxRows": 100}}
(maxRows kept in the fixture for realism only; per the revised 4.1 it is optional and its
absence would NOT fail these tests - a trap for
every rewritten integration-style test here and in 7.6). Requires the FakeNamedConnectorQuery
fixture change in 7.1. Assertion content carries over in spirit; literal message text
changes to the tagsMethod-validation wording (3.5).

### 7.5 WorksheetTableServiceShouldProbeTest - ONE-LINE FIXTURE EDIT (6 tests, 5 untouched)

Only tabularTableIsNotProbed's JSON literal needs "endpoint": "Charges" -> "queryParams":
{"endpoint": "Charges"}; shouldProbe's own logic is untouched.

### 7.6 WorksheetTableServicePermissionTest - FOUND BEYOND THE HUMAN'S LIST; 25 tests, ~13
    affected

- Four permission-gate tests (:268-393) - TRIVIAL FIXTURE EDIT, "endpoint": "Charges" ->
  "queryParams": {"endpoint": "Charges"}. Confirmed inert as far as these tests are
  concerned (mocked XRepository short-circuits first).
- Three targetKind-normalization tests (:406-494) - DELETE; the field no longer exists.
  Replace with one new test asserting "tabularSource.queryParams is required" when absent.
- Six sandboxSampleLimit tests (:667-777) - REWRITE ALL SIX for the new (Worksheet,
  WorksheetTable, WorksheetTableResponse) signature (5.1); needs a real/lightly-mocked
  Worksheet/TabularTableAssembly and a new SelectableTabularQuery test fixture (e.g.
  FakeSelectableFileQuery).
- The remaining ~12 tests (non-tabular permission gates) are untouched.

### 7.7 Untouched - confirmed by grep, no reference to any changed symbol

TabularSchemaExtractorTest (13), WizTabularControllerTest (23), WizTabularControllerSecurityTest
(14) - the extractor and the /query-schema endpoint are explicitly unchanged by this design.

### 7.8 New test coverage needed, INCLUDING A REQUIRED SHIP-BLOCKING ACCEPTANCE CRITERION (D6)

ACCEPTANCE CRITERION, NOT OPTIONAL (per coordinator ruling on D6): before this change ships,
run the general read-back-equality check (section 3.6) against RestJsonQuery's and
EndpointJsonQuery's REAL, full property set (52 parameters per the wiz-side task doc's
section 11.1 probe) and confirm it produces NO false positive from a setter that legitimately
normalizes its input (trims, clamps, case-folds). This was identified as the one part of this
design with no direct precedent to copy from the existing code, and the coordinator has made
running it a condition of shipping, not a residual risk to note and proceed past.

Other new coverage needed (capabilities with no prior test at all):

- CYCLE DETECTION in the topological sort (3.3): a purpose-built fixture with two properties
  whose @PropertyEditor(dependsOn=...) mutually reference each other; assert the specific
  IllegalStateException naming both, not a hang or a silent drop.
- KIND B REFUSAL BY NAME (3.4): a fixture property of HttpParameter[] type (or any other
  non-RestParameters composite) is refused by name with the exact message in section 3.4,
  never silently dropped. This REPLACES the earlier draft's "HttpParameter[] construct-fresh-
  list" coverage, which is no longer implemented.
- EXCEL MULTI-SHEET-AMBIGUITY REFUSAL, VERBATIM MESSAGE (3.8, capability 5b): a fixture with
  isExcel()==true and 3+ sheet names; omitting the sheet property throws the exact message
  string specified in section 3.8; supplying a valid sheet name proceeds; supplying an
  invalid one is instead caught by tagsMethod validation (3.5) with its own message.
- CUSTOM LOOKUP URL PLACEHOLDER VALIDATION (3.9, capability 6): blank lookupUrl0; missing
  placeholder at i=0; wrong-position placeholder at i=1 - the three tests carried over from
  TabularEndpointBindingSupportTest per 7.1, now driven through the general routine.
- java.io.File STRING-PATH RESOLUTION VIA queryParams (3.8, capability 5): relative path
  resolves and reads back; ".."/absolute path refused; nonexistent path refused naming the
  path. No existing test was found exercising fileTargetProperty/resolveTargetFile in
  isolation, so this is likely genuinely new coverage.

### 7.9 NEW: TabularQueryParamsSchemaBuilderTest (section 11/12, no prior test - new file)
    (REVISED - x-valueSource values renamed, catalog-emission assertion replaced)

- SCALAR MAPPING: javaType -> JSON Schema `type` for each of string/int/double/boolean/enum/
  File (File maps to `"string"`, per isScalar treating it as scalar); min/max -> minimum/
  maximum; a single pattern -> `pattern`; TWO patterns (ServerFileQuery.fileFolder, confirmed
  real case) -> `allOf` of two `{"pattern": ...}` entries, not a single field.
- DEPENDENCYMATRIX -> allOf/if-then, BOTH SHAPES: a single-axis gate (one `if`/`then` per
  value) and a combination gate (compound `if` with two `const` conditions, `required` listing
  both axis names) - fixture reproducing ActiveCampaignQuery's confirmed real shape
  (paginationType -> linkParamType -> linkRelation, :82-83). Assert the gated params
  (linkParamType, linkRelation) do NOT appear in top-level `properties`.
- ROOT KEYWORDS (11.4) - two assertions, both cheap and both guarding a silent failure:
  (a) the generated root carries `"unevaluatedProperties": false` and does NOT carry a root
      `"additionalProperties"` of any kind. A root `additionalProperties: false` rejects every
      branch-introduced param, i.e. every paginated request, and nothing else in this suite
      would notice.
  (b) the generated root carries `"$schema": "https://json-schema.org/draft/2020-12/schema"`.
      That line is what makes a consumer on a draft-07 validator fail at compile time instead
      of silently ignoring `unevaluatedProperties` and enforcing nothing.
  Neither assertion needs a JSON Schema validator on the Java side - both are plain JsonNode
  field checks on the builder's output.
- KIND A COMPOSITE, DEPENDSON PRESENT: EndpointJsonQuery.parameters-shaped fixture ->
  `{"type":"object","additionalProperties":{"type":"string"}}` + `x-skeleton` naming the
  dependsOn param; NEVER in `required`, regardless of `@Property.required=true` on the fixture.
- KIND A COMPOSITE, NO DEPENDSON, NAMED SKELETON ALREADY PRESENT ON A BLANK QUERY: a fixture
  composite that resolves immediately -> same object/additionalProperties shape, no
  `x-skeleton`.
- KIND B, SCHEMA-TIME DETECTABLE (no dependsOn, empty/unnamed on a blank query): OMITTED FROM
  THE SCHEMA BY DEFAULT (D9) - assert absence from both `properties` and `required`; a second
  test with the stub variant enabled asserts `x-output: true` is present and the name is still
  never in `required` (A2's deadlock case, ServerFileQuery.columns-shaped fixture with
  `@Property(required=true)`).
- REQUIRED ARRAY EXCLUSIONS (A4): a fixture property declared `@Property(required=true)` that
  is ALSO a Kind-B composite must never appear in `required`, even though `param.isRequired()`
  is true - the one directly testable case A2 exists to prevent.
- x-VALUESOURCE DEFAULT (resolveTags=false): every tagsMethod-bearing param gets
  `x-valueSource: "external"` and a matching `x-tagsMethod`; no `enum` is inlined; the backend
  NEVER emits any value outside the fixed three (`"external"`/`"unavailable"`/`"too-large"`)
  under any input - assert this directly, since the wire no longer carries any
  ingestion-state-dependent value for the backend to accidentally leak.
- x- KEY / description PAIRING, STRENGTHENED (11.5.2's revised, mandatory invariant): for
  every property that carries any `x-` key, assert its `description` is non-empty and
  (string-contains, not exact-match, to avoid over-pinning wording) names BOTH the concrete
  origin (the connector method/document, e.g. "$metadata", never the word "catalog") AND,
  where the param's `x-valueSource` is `"unavailable"` or `"too-large"`, the next action
  (retry vs. narrow) - not merely that the `x-` key's fact is restated somewhere.

### 7.10 NEW: TabularQueryParamsSchemaBuilderResolveTagsTest (section 12) (REVISED - values
     renamed; a real-connector case added per 12.3's ship-blocking acceptance check)

- DEPENDSON PARAM IS SKIPPED even with resolveTags=true: `x-valueSource` stays `"external"`,
  no `enum` is inlined, even when the fixture's tagsMethod would return a non-empty result if
  called (proves the skip is unconditional on dependsOn, not just "usually empty").
- NO-DEPENDSON PARAM, NORMAL CASE: fixture tagsMethod returns a small String[][] -> inlined
  `enum` (values) + `x-enumLabels` (labels, same index order) - the getEntityRefs() shape
  (12.5).
- TIMEOUT: fixture tagsMethod sleeps past 5s -> `x-valueSource: "unavailable"`, `description`
  telling the agent to retry (12.2), no `enum`, request as a whole still completes (does not
  hang or propagate the exception to the caller).
- OVER-CAP: fixture tagsMethod returns 201+ candidates -> `x-valueSource: "too-large"` +
  `x-candidateCount` equal to the real count, `description` telling the agent to narrow
  rather than retry (12.2), no `enum` inlined.
- EXCEPTION (not just timeout): fixture tagsMethod throws -> same `"unavailable"` path as
  timeout, confirming both degradations funnel to one outcome as designed (12.2).
- SHIP-BLOCKING, REAL CONNECTOR (NEW - per 12.3's acceptance check, same class of requirement
  as D6's 7.8 check, NOT covered by the fixture-based cases above): resolveTags=true against
  an actual ODataQuery pointed at a real OData service, `entity`'s tagsMethod
  (`getEntityRefs()`) genuinely invoked over the network - confirm candidates return inside
  the 5s timeout and under the 200-candidate cap. This is an integration-style test, not a
  fixture unit test, and is a CONDITION OF SHIPPING per 12.3, not optional coverage to add if
  time allows.

---

## 8. Flagged decisions - ARBITRATED (this section records the coordinator's rulings; the
    document above already reflects them)

D1 (was flagged decision 2) - EditRequest/WorksheetAgentController in scope: **REVERSED IN
ROUND 5. Out of scope; see the revised section 6.** The accepted reasoning was "community not
compiling is a hard constraint, so this is not a choice" - but that had the implication
backwards. Compilation breaks only because the design deletes
`TabularEndpointBindingSupport`, which a working, unrelated caller uses; that is an argument
for keeping the class, not for rewriting the caller. `TabularEndpointBindingSupport` is
retained with exactly one consumer (the composer path), `addTabularTable`, `EditRequest` and
`WorksheetMutationSupport` are untouched, and the plugin/composer TypeScript extension is
withdrawn along with the rest. Recorded rather than deleted because the failure mode is worth
recognising later: a hard constraint was cited for a scope increase that the constraint did
not actually require.

D2 (was flagged decisions 5 and 7) - composite scope: NARROWED to Kind A only (section 3.4).
RestParameters (fill-an-existing-skeleton) is supported; HttpParameter[]/QueryParameter/
ColumnDefinition (construct-fresh-list) are refused by name, and NO @Property annotations are
added to any of the four classes in this pass. Reasoning: the @Property-addition risk was
never verified against LayoutCreator/findProperties assumptions for a class with no @View,
an unverified risk on the composer dialog's LIVE RENDERING PATH for a capability with ZERO
production traffic (all 65 catalogued connectors go through RestParameters, and the wiz side
already has parameter names from its Tier-1 endpoint catalogue, not from elementParams).

D2 CORRECTED THIS PASS (A1, section 3.4): D2's SCOPE call (no @Property additions to
RestParameter/HttpParameter/QueryParameter/ColumnDefinition this pass) STANDS UNCHANGED. What
does NOT stand is the MECHANISM the paragraph above used to state that scope: "RestParameters
is supported, HttpParameter[] is refused" was a CLASS-NAME rule, and it is wrong - verified,
ODataQuery.functionParameters is HttpParameter[]-typed and IS Kind A (a real skeleton, derived
from the currently-set function, with named elements), while EndpointJsonQuery.
additionalParameters is the SAME Java type and is genuinely Kind B (starts null, never
resolves). Section 3.4/3.4.1 now state the correct, RUNTIME rule (skeleton non-empty AND
elements carry names, checked when dependsOn ordering reaches the param - never a Java-type
test), and section 3.2 step 4b was rewritten to match. This is a STRICT GENERALIZATION, not a
scope change: it makes MORE composites usable (any composite of any type that resolves a named
skeleton, including third-party connectors this document never enumerated), never fewer, and
every conclusion D2 drew about @Property additions being unnecessary this pass is unaffected -
if anything reinforced, since general Kind A support no longer depends on RestParameters being
special-cased at all.

D3 (was flagged decision 6) - the three applyCustomLookupChain validations: REJECTED,
KEPT (section 3.9, capability 6). The coordinator's counter to the earlier "drop it, it's
connector-specific" argument: POST refusal is equally connector-specific and was kept; the
cost of dropping is concrete (a malformed lookup template fails silently, one billed request
per row), not a purity concern. Principle stated explicitly in section 3.9 and reused for
D4: a check survives when its declarative equivalent does not exist AND dropping it converts
a pre-flight refusal into a silent, metered failure.

D4 (was flagged decision 4) - the Excel multi-sheet ambiguity refusal: REJECTED, KEPT
(section 3.8, capability 5b), same principle as D3, made worse by a confirmed live cross-repo
coupling: wiz-services' tabularFileClient.ts's parseExcelSheetAmbiguity parses today's exact
message; dropping the check silently collapses a multi-sheet workbook to one arbitrary sheet
DURING ANNOTATION, with no error anywhere. The verbatim replacement message is specified in
section 3.8 and labeled a cross-repo contract tabularFileClient.ts must update against in
the same change set.

D5 (was flagged decision 3) - unconditional requireMaxRows: **OVERTURNED IN ROUND 4, see the
revised section 4.1.** Your original objection was right and is now the design. What happened:
the coordinator read "all tabular sources support a row cap" as "must be given one" and
confirmed D5 literally; on reporting the resulting behavior-change sentence to the human, the
human corrected it. `maxRows` is now OPTIONAL. Absent, the probe runs under
`HINT_MAX_ROWS = max(20, sampleRows)` and NOTHING is persisted on the query; supplied, it is
persisted exactly as today. The governing reason is not cost but correctness: a
server-invented cap that got persisted would silently truncate the table and produce wrong
aggregates in every chart bound to it, which is worse than an over-large bill because it is
invisible. Record kept rather than rewritten, because the failure mode here - a plausible
literal reading of an ambiguous instruction, confirmed twice before anyone checked it against
intent - is worth being able to find later.

D6 (was flagged decision 1) - the general read-back-equality check: ACCEPTED as designed,
the broader per-scalar-param version (section 3.6), WITH a required, ship-blocking
acceptance criterion added at section 7.8: run it against RestJsonQuery's/
EndpointJsonQuery's real 52-parameter set and confirm no false positive from a legitimately-
normalizing setter before this change ships.

D7 (new, section 4.2's dependsOn addition to RestJsonQuery's custom lookup fields) -
ACCEPTED. Distinct from D2's rejected @Property additions and much lower risk (dependsOn is
an existing legal attribute on a getter that already carries @PropertyEditor). The same
"verify no LayoutCreator surprise" check applies at implementation time, per section 4.2.

D8 (NEW, section 11.4) - dependencyMatrix combination gates map to FLAT SIBLING allOf entries,
not literal nested if-inside-then. ACCEPTED, this document's own call, not yet put to the
coordinator. Reasoning: TabularSchemaExtractor.buildDependencyMatrix already resolves a two-
level gate to one compound condition (both axis values ANDed, :279-326) rather than a tree, so
a flat allOf entry per matrix key is a MECHANICAL, lossless translation of what is already
computed; true nesting would say the same thing more verbosely for identical semantics.
Alternative rejected: hand-building a literal `if` nested inside a `then`, which would require
re-deriving tree structure the extractor deliberately does not keep.

D9 (NEW, section 11.4) - a Kind B composite (schema-time-detected: no dependsOn, empty skeleton
on a blank query) is OMITTED FROM THE GENERATED SCHEMA BY DEFAULT, not represented with an
`x-output: true` stub. ACCEPTED, this document's own call. Reasoning: matches the existing
precedent for a derived no-op (`suffix` on a named connector is already precedented as "omit,
don't describe a property nobody can use"), and a stub costs schema size for a property that,
per A2, can never be filled anyway. Alternative, left available to the implementer rather than
foreclosed: keep a documentation-only stub with `x-output: true` if there is a concrete reason
an agent benefits from knowing the property exists (e.g. explaining in prose why a column list
it might expect is absent) - both shapes are specified in 11.4 so the implementer is not
blocked either way.

D10 (NEW, section 12.3) - RE-FILED THIS PASS from a tuning nit to a PRIMARY, SHIP-BLOCKING
RISK, per the coordinator's correction: resolveTags is not an optimization ("saves a round
trip") but, for a METADATA-class connector on the only path that reaches one, the SOLE source
of candidate values this pass (12.0) - there is no `query-tags` endpoint and no catalogue
ingestion to fall back to (section 13). A 5-second timeout that trips on a real connector's
`$metadata`/entity-listing call therefore does not make that connector "slower to use," it
makes it UNUSABLE THROUGH THIS API, full stop. resolveTags per-call timeout (5s) and candidate
cap (200) remain FIXED CONSTANTS, reasoned from one precedent (HttpAssistantDocSearchGateway)
plus general knowledge of typical enum sizes, NOT measured against any live connector in this
codebase. THIS DOCUMENT NO LONGER TREATS THAT AS AN ACCEPTABLE GAP TO CLOSE LATER: section
12.3/7.10 add a SHIP-BLOCKING ACCEPTANCE CHECK, the same class of requirement as D6's read-back
check (section 7.8) - resolveTags=true must be run against at least one real METADATA-class
connector (OData, the concrete case already cited) before this ships, confirming candidates
return inside the timeout and under the cap. If it does not, THE DEFAULTS THEMSELVES ARE WRONG
BEFORE SHIP, not a residual risk to note and proceed past - flagged here so the
coordinator/human can raise the timeout, raise the cap, or descope resolveTags for specific
slow connectors, rather than discovering the failure after this ships.

D11 (NEW, section 11.4) - the schema-time Kind A/B approximation is DELIBERATELY ASYMMETRIC:
it can mislabel a true Kind B composite as "potentially Kind A" (if such a composite happened
to declare a dependsOn while never actually resolving a named skeleton - not observed in this
codebase; both real Kind B cases, ServerFileQuery.columns and EndpointJsonQuery.
additionalParameters, declare no dependsOn at all), but it can NEVER mislabel a true Kind A
composite as unsupported, because "dependsOn non-empty" always yields the fillable-object
shape, never a refusal. ACCEPTED as the safe direction to err in: a caller told a param "might
be fillable" and then refused by name at write time (A1's real-time rule is authoritative) loses
one round trip; a caller told a genuinely fillable param does not exist loses the capability
entirely, reproducing A2's deadlock in a new place. This asymmetry is why 11.4 does not attempt
to be fully accurate at schema-generation time - it only needs to never be wrong in the
dangerous direction.

D12 (NEW, section 11.5) - the six `x-` keywords are a FIXED LIST for this pass, matching
exactly what the wiz-side design is being updated against. Not a design choice this document
is free to expand - stated here so a later editor of this document does not add a seventh `x-`
keyword unilaterally without the same cross-repo coordination this list already went through.

REMAINING, NOT YET ARBITRATED / NOT A DECISION AT ALL, RESTATED FOR VISIBILITY:

- EditRequest's own maxRows field (or lack of one) for add_table, given requireMaxRows is now
  unconditional (section 6) - not resolved in this pass.
- WorksheetMutationSupport.CustomLookupSpec's other callers were not exhaustively searched
  beyond the files grep found referencing it (section 5); confirm before deleting.

---

## 9. Risks / unconfirmed without a live server or a compiling build

- The java.beans.Introspector ordering claim (3.1) - that TabularUtil.findProperties's
  descriptor order is not reliably declaration order - is stated from general JDK knowledge,
  not verified by running it against a real connector class in this JVM/JDK version. Even if
  wrong, the schema-order tie-break is still the right choice, so this uncertainty does not
  change the design.
- Section 4.2's dependsOn addition and section 3.9/3.8's kept narrow checks were not verified
  to compile or run against a live LayoutCreator; both are called out as implementation-time
  verification steps, not closed.
- WorksheetMutationSupport.CustomLookupSpec's other callers were not exhaustively searched
  beyond the four files grep found referencing it (section 5); deleting it outright without
  re-checking at implementation time risks an unrelated compile break.
- Section 3.8's exact Excel-ambiguity message and section 3.4's Kind-B refusal message are
  this document's proposed text, not yet reviewed against tabularFileClient.ts's actual
  updated regex (that update happens on the wiz side, in the same change set per D4).
- A1's runtime Kind A/B rule (section 3.4) and 11.4's schema-time approximation of it are
  verified against exactly three properties by direct source read (EndpointJsonQuery.parameters,
  ODataQuery.functionParameters, EndpointJsonQuery.additionalParameters) plus one output-only
  case (ServerFileQuery.columns, A2). They are NOT verified against every composite property in
  every one of the 65 catalogued connectors - the rule is general by construction (reflection on
  getName()/setValue(String), not a class-name list), but no sweep confirming every shipped
  composite actually exposes getName() the same way has been run in this pass.
- ODataQuery.functionParameters, now routed through the generalized fillNamedSkeleton (3.4.1)
  instead of being refused (its prior, INCORRECT classification), has not been exercised against
  a live ODataQuery instance in this pass. In particular: whether the general read-back-equality
  check (3.6) correctly detects setFunctionParameters's documented silent-no-op-on-name-mismatch
  (ODataQuery.java:115-147) is asserted from reading that method's logic, not from running it.
  This is exactly the kind of case section 7.8's ship-blocking acceptance criterion (D6) exists
  to catch - it should be included in that same real-connector probe, not treated as covered by
  the existing 52-parameter RestJsonQuery/EndpointJsonQuery-only probe.
- 11.4's multiple-@Property.pattern()-entries-are-AND-ed assumption is based on one observed
  call site (ServerFileQuery.fileFolder) and the annotation's own javadoc states no combining
  rule at all. If a connector relies on OR semantics anywhere, the generated `allOf` would
  reject a value the connector itself accepts - not found in this pass's read, but not
  exhaustively searched either.
- 12.3's timeout (5s) and cap (200) defaults are reasoned from one comparable precedent
  (HttpAssistantDocSearchGateway) and general knowledge of typical enum sizes (ISO currencies,
  IANA timezones), NOT measured against any real connector's actual tagsMethod latency or
  candidate count in this codebase. ELEVATED THIS PASS (D10, section 8): this is no longer a
  minor tuning gap - resolveTags is the sole source of candidate values for a METADATA-class
  connector this pass (12.0), so a wrong default here means the connector is unusable through
  this API, not merely slower. Section 7.10 now requires a ship-blocking real-connector
  acceptance check for exactly this reason; THIS RISK ENTRY RECORDS THAT THE CHECK HAS NOT YET
  BEEN RUN, not that the risk is accepted as-is.
- ajv's strict-mode handling of the six `x-` keywords (section 11.5) is a wiz-side
  implementation detail this document does not control; the six-keyword list is fixed here so
  wiz can register them with `addKeyword` rather than disabling strict mode wholesale, but
  whether that registration actually happens is outside this document's verification.

---

## 10. Diff against the wiz-side design (new task, per coordinator)

Read in full: E:/inetsoft/stylebi-wiz/docs/superpowers/specs/2026-08-25-tabular-unified-query-
contract-wiz-design.md (970 lines). Their document is NOT edited; findings only, below, each
with which side should change and why.

### 10.1 REOPENED BY ROUND 4 - maxRows is now OPTIONAL, and the wiz side must be re-aligned

HISTORY, because this one flipped twice and the current state is the opposite of where it
started. Originally: this document flagged that wiz's guidance said maxRows was required only
"on anything paginated", contradicting D5's unconditional requirement. Wiz then fixed that -
their guidance was rewritten to state it unconditionally AND a fail-fast pre-check was added
to validateTabularSource. Then round 4 overturned D5 itself (see the revised 4.1): maxRows is
OPTIONAL, absent means a probe-only HINT_MAX_ROWS default and nothing persisted.

CONSEQUENCE - ACTION REQUIRED ON THE WIZ SIDE, not closed:

- their guidance text must stop saying maxRows is required, and should instead say it is
  optional, that omitting it caps only the initial probe, and that supplying it caps EVERY
  future render (that being the actual decision the agent is making);
- their validateTabularSource pre-check that rejects a missing/non-positive maxRows must be
  REMOVED - it would now reject requests this design accepts, which is the worst kind of
  cross-repo drift: a client refusing something the server supports, diagnosable only by
  reading both documents;
- the guidance should carry the mitigation from 4.1 instead: confirm with the user before
  binding a paginated, metered data source with no cap.

The coordinator is carrying this to the wiz design directly; recorded here so the two
documents cannot silently diverge on the point they have already disagreed about once.

### 10.2 GAP, NOT A CONTRADICTION - Kind B (additionalParameters etc.) refusal is not
    mentioned in wiz's guidance (their side should change)

D2 (section 3.4/8) narrows composite support to Kind A (RestParameters) only; Kind B
(HttpParameter[]/additionalParameters and similar) is refused by name with the message
specified in section 3.4. Wiz's section 6.2 coerceQueryParams comment ("Composite /
array-of-composite: pass the object/array through untouched. StyleBI's own reflective
fill-by-name is where THAT gets validated") is not WRONG - StyleBI does validate it, by
refusing it - but their agent-facing guidance (section 7.1) and describe_tabular_query's
compositeNote (section 3.7) do not distinguish "Kind A, describable via the endpoint
catalogue" from "Kind B, will always be refused, do not attempt." An agent has no way to
learn this short of trying it once and reading the refusal. NOT SILENT (refused loud, no
network call made, since capability 1's Kind-B branch runs before any request) - just an
avoidable round trip. WIZ SHOULD CHANGE (recommended, not required): have compositeNote
distinguish the two kinds explicitly, or fold the Kind-B refusal message's guidance ("write
it into suffix/parameters instead") into section 7.1's prose.

### 10.3 RESOLVED BY D2 - wiz's own section 10.0 wire-change request

Wiz's section 10.0 independently verified the exact same fact this document's section 3.4
verifies (RestParameters/RestParameter/HttpParameter/QueryParameter/ColumnDefinition all
carry zero @Property annotations, so elementParams is empty for all of them) and explicitly
asked the coordinator to arbitrate whether the backend should add a hand-authored
elementParams description for these four classes. D2 IS THE ANSWER: declined for this pass
(Kind A only, no @Property additions, see section 3.4's follow-on note), for reasons D2
states (unverified LayoutCreator/findProperties risk against zero production benefit). No
document needs to change here - this is confirmation that wiz's open question is now closed,
recorded here so neither side re-opens it independently.

### 10.4 VERIFIED CONSISTENT, NO CONTRADICTION - per-instance schema caching (their section
    5.2) against this document's schema-extraction behavior

Checked directly: WizTabularController.getQuerySchema (unchanged by this design,
:260-300) calls TabularUtil.createQuery(path) - resolving the QUERY INSTANCE for the actual
data source path, not the bare type - then TabularSchemaExtractor.extract(query, ...) against
that instance, on EVERY call, with NO backend-side caching at all. The controller's own
comment states exactly wiz's own justification for choosing per-instance over per-type
caching: "A visibility condition is free to read it -- a connector can offer different
parameters for different accounts." Wiz's choice is therefore not just reasonable but
independently confirmed by the code it is caching in front of. Nothing in this document
assumes any particular caching behavior on the wiz side either way, since the backend
recomputes fresh per call regardless.

### 10.5 OBSERVATION, NOT A CONTRADICTION - double coercion is safe but redundant

Wiz's coerceQueryParams (their section 6.2) converts scalar values to typed JSON (a string
"100" becomes the number 100) before POSTing. This document's coerceParam (section 3.2 step
4b's final branch, unchanged from today) then STRINGIFIES that value via text() and re-parses
it back to a typed Java value. For every case checked (int, boolean, float, enum) the
round-trip (typed -> string -> typed) is lossless and both sides' branch logic matches 1:1
(their comment says so explicitly: "deliberately the same rule set as the backend's own
coerceParam"). In practice, most malformed values will be caught by wiz's layer FIRST (it
runs before the network call), so the exact message a caller sees for an ordinary type
mismatch is usually wiz's own wording, not this document's coerceParam message - this
document's message set (section 3.2/existing coerceParam text) becomes a backstop for
whatever wiz's coercion does not catch (notably: values wiz's "unknown scalar shape: pass
through" branch does not recognize). NEITHER SIDE NEEDS TO CHANGE - flagging only because two
independent coercion layers agreeing by construction rather than by a shared contract is
worth noting as a maintenance risk if either side's rule set drifts later.

### 10.6 EVERY MESSAGE WIZ PARSES OR SURFACES, CHECKED

Only one exact-string parse was found in the sections read: tabularFileClient.ts's
parseExcelSheetAmbiguity (their section 8/9/11, this document's section 3.8, D4) - already
addressed with a verbatim replacement message and flagged as a same-change-set cross-repo
contract in both documents. No other regex/exact-match parsing of a backend error string was
found in the wiz design as read; every other backend failure surfaces through their existing
generic per-table catch-and-report path (worksheetService.ts's client.post catch block),
which does not depend on any specific wording.

### 10.7 NO WIRE CHANGE NEEDED

None of the above requires changing the fixed wire itself (section 2). 10.1 and 10.2 are
prose/guidance updates on the wiz side; 10.3 is a closed question; 10.4 and 10.6 are
confirmations; 10.5 is a noted-but-accepted redundancy.

---

## 11. queryParamsSchema - JSON Schema generation for GET .../query-schema (NEW THIS PASS)

### 11.1 Why JSON Schema, not a bespoke shape

The caller of GET /api/wiz/tabular/query-schema is an LLM agent (wiz), and JSON Schema is the
format it reads every day - every MCP/LLM tool definition already is one. A self-invented
convention (wiz's original draft used a hand-rolled ParamSummary with four buckets: required /
common / conditional / runtimeOnly) has to be learned from scratch; JSON Schema does not.

Three further consequences worth stating precisely, since they motivate specific mapping rules
below rather than being decorative:

1. PRUNING FALLS OUT OF THE STRUCTURE. dependencyMatrix already partitions parameters into
   "always relevant" and "relevant only once some other parameter has a given value" - which
   maps directly onto top-level `properties` (unconditional) plus `allOf`/`if`/`then` branches
   (conditional). The agent's first read of `properties` is already the minimal, unconditional
   set; nothing has to be filtered client-side.
2. THE SCHEMA IS EXECUTABLE. TabularQuerySchema.Param is a description; nothing stops a caller
   from sending a value that is wrong in a way the description could have ruled out. A JSON
   Schema can be hangfed straight to ajv (wiz-side) before any network round trip: a missing
   required field, a wrong type, or a parameter that does not apply under the branch currently
   selected are all caught locally.
3. COMPOSITE SHAPE NEEDS NO PROSE. `{"type":"object","additionalProperties":{"type":"string"}}`
   says everything `elementParams for RestParameters` would otherwise have to explain in words -
   and, since elementParams is empty for every composite class in this codebase (section 3.4),
   there is no prose fallback available anyway. See section 11.4's Kind A rule.

### 11.2 Wire shape: additive only

TabularQuerySchema (core/src/main/java/inetsoft/uql/tabular/TabularQuerySchema.java) gains ONE
new field:

    private JsonNode queryParamsSchema;   // com.fasterxml.jackson.databind.JsonNode
    public JsonNode getQueryParamsSchema() { return queryParamsSchema; }
    public void setQueryParamsSchema(JsonNode queryParamsSchema) { this.queryParamsSchema = queryParamsSchema; }

params (List<Param>) and dependencyMatrix are UNCHANGED, still populated by
TabularSchemaExtractor.extract exactly as today, and still returned in the same response. This
is not redundant: params[]/dependencyMatrix is the LOSSLESS view (it alone carries
visibleMethod/enabledMethod/editorType/group, none of which wiz's own heuristics can currently
do without - confirmed no other caller of TabularQuerySchema exists in this codebase besides
WizTabularController.getQuerySchema, grep-verified, so this addition is safe). queryParamsSchema
is a LOSSY PROJECTION of the same data, shaped for an LLM tool-call rather than a form renderer.
Both are computed from the same TabularSchemaExtractor.extract() call in the same request; there
is no second network round trip and no second probing pass.

### 11.3 New class: TabularQueryParamsSchemaBuilder

core/src/main/java/inetsoft/uql/tabular/TabularQueryParamsSchemaBuilder.java - NEW FILE, same
package as TabularSchemaExtractor (so it can call the isCompositeType promotion section 5
already plans - see 11.4's Kind A/B rule, which reuses the exact same runtime check section
3.4's A1 correction specifies for the write path, not a second, independently-invented test).

    public final class TabularQueryParamsSchemaBuilder {
       public static JsonNode build(TabularQuery query, TabularQuerySchema schema,
                                     boolean resolveTags);
    }

Called from WizTabularController.getQuerySchema (core/src/main/java/inetsoft/web/wiz/
controller/WizTabularController.java:266-300), immediately after the existing extract() call,
inside the same try/finally connector-session block (so a resolveTags=true tagsMethod
invocation is bound and accounted the same way every other connector-code call already is):

    TabularQuerySchema schema = new TabularSchemaExtractor()
       .extract(query, dataSource == null ? null : dataSource.getType());
    schema.setQueryParamsSchema(
       TabularQueryParamsSchemaBuilder.build(query, schema, resolveTags));
    return schema;

`query` here is the SAME instance TabularSchemaExtractor.extract just finished with - confirmed
by reading TabularSchemaExtractor.probe (:368-404): every dependencyMatrix probe constructs its
OWN fresh instance (`cls.getDeclaredConstructor().newInstance()`) and never touches the
`prototype` parameter's state, so `query` is still in its post-createQuery(path), pre-any-
@Property-write state (dataSource set, nothing else) when the builder runs. This matters twice:
it is why a Kind A/B guess made here (11.4) can only be a guess for a dependsOn-gated composite
(its true skeleton does not exist yet), and it is why resolveTags's tagsMethod calls for a
no-dependsOn parameter (section 12) see the same account/session state a real fill would.

Method signature adds `RequestParam resolveTags`:

    @GetMapping(value = "/tabular/query-schema", ...)
    public TabularQuerySchema getQuerySchema(
       @PermissionPath @RequestParam("path") String path,
       @RequestParam(name = "resolveTags", required = false, defaultValue = "false")
          boolean resolveTags,
       Principal principal) throws Exception

### 11.4 Mapping rules

Top-level shape:

    {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "properties": { ... unconditional params, by name ... },
      "required": [ ... see the required-array rule below ... ],
      "allOf": [ ... one entry per dependencyMatrix branch, see below ... ],
      "unevaluatedProperties": false
    }

BOTH of the last two lines are load-bearing, and `additionalProperties: false` is NOT a
substitute for the last one. Measured in this repo against ajv 8.20.0 (the version already
present in `wiz-services/node_modules`), on a schema of exactly the shape above:

| root keyword                   | `{paginationType: "LINK_ITERATION", linkParamValue: "next"}` | `{paginationType: "LINK_ITERATION", bogusParam: "x"}` |
|--------------------------------|---------------------------------------------|------------------------------|
| `additionalProperties: false`  | REJECTED (wrong - it is legal)              | rejected                     |
| `unevaluatedProperties: false` | accepted (correct)                          | REJECTED (correct)           |

`additionalProperties` only sees the names in the SIBLING `properties` map; a name introduced
inside an `allOf[].then` branch is invisible to it. Since every conditional param - all 52
pagination params on RestJsonQuery - lives in exactly such a branch, a root
`additionalProperties: false` would reject every correctly-formed paginated request.
`unevaluatedProperties` runs after the applicators and sees branch-introduced names, so it
accepts those and still rejects genuinely unknown ones.

The `$schema` line is what makes a consumer's mistake LOUD rather than silent, and this is
the reason it must never be dropped. `unevaluatedProperties` is a draft 2019-09/2020-12
keyword. A validator built for draft-07 - which is what `require("ajv")` returns by default -
treats it as an unknown keyword and IGNORES it, yielding a validator that accepts the correct
case and also accepts `bogusParam`: a check that appears to run and enforces nothing. Measured:

    require("ajv")            ->  valid-ok: true   rejects-bogus: FALSE   (silently inert)
    require("ajv/dist/2019")  ->  valid-ok: true   rejects-bogus: true
    require("ajv/dist/2020")  ->  valid-ok: true   rejects-bogus: true

With the `$schema` line present, the default draft-07 build does not silently ignore the
keyword - it throws at compile time:

    no schema with key or ref "https://json-schema.org/draft/2020-12/schema"

So emitting `$schema` converts "consumer used the wrong validator build" from a silent
degradation into a startup failure. That is the same principle this whole contract is built
on, applied to the contract's own description. (The wiz design's section 6.2.1 records the
consumer half of this, and its ajv instance imports `ajv/dist/2020` for exactly this reason.)

NOTE the two different jobs the word "additionalProperties" does in this document: at the
ROOT it is wrong, per the above; INSIDE a Kind A composite - `{"type": "object",
"additionalProperties": {"type": "string"}}` - it is correct and stays, because there it is
not a boolean gate but the value-type of an open-ended map whose legal keys are only known
at request time (see `x-skeleton`).

SCALAR PARAM (javaType is scalar per TabularSchemaExtractor.isScalar - String, a primitive/
wrapper, an enum, a Date, or java.io.File; File is scalar under today's isScalar, :636-638, so
`fileFolder` is mapped as a plain string, consistent with capability 5 treating it as a string
path, not specially here):

    "<name>": {
       "type": "<from javaType: string|integer|number|boolean>",
       "description": "<label>. <hints, joined>. <group note if any>",
       "minimum": <param.getMin(), if not null>,
       "maximum": <param.getMax(), if not null>,
       "pattern": "<param.getPattern().get(0), if exactly one>"
                  -- OR, if MORE THAN ONE pattern is declared (confirmed real case:
                     ServerFileQuery.fileFolder has two, :67-70): wrap in
                     "allOf": [ {"pattern": p0}, {"pattern": p1}, ... ]  -- each must match;
                     JSON Schema's own `pattern` keyword takes exactly one regex, so N>1
                     patterns cannot collapse into a single field. (This assumes
                     @Property.pattern()'s multiple entries are AND-ed, which is the
                     behavior actually observed at this one call site; the annotation
                     itself documents no combining rule - flagged as an assumption in
                     section 9.)
       "enum": [ <param.getTags(), if non-empty and tagsMethod is absent> ],
       "x-enumLabels": [ <param.getTagLabels(), parallel to enum> ]
    }

Every `x-` key is accompanied by the SAME fact restated in `description`, per the mandatory
rule in 11.5 - not shown above per field to keep this listing readable, but non-optional.

DEPENDENCYMATRIX -> allOf/if-then. TabularSchemaExtractor.buildDependencyMatrix (:191-229,
229-327) produces two shapes under the same map, keyed by axis name or by a combination key
containing " & " (:249-327, addCombinationGates) - both are mapped, not just the simple one:

    SINGLE AXIS   dependencyMatrix["paginationType"]["LINK_ITERATION"] = ["linkParamType"]
       ->
       { "if":   { "properties": {"paginationType": {"const": "LINK_ITERATION"}},
                   "required": ["paginationType"] },
         "then": { "properties": {"linkParamType": { ... that param's own mapped schema ... }} } }

    COMBINATION   dependencyMatrix["paginationType & linkParamType"]
                  ["paginationType=LINK_ITERATION & linkParamType=LINK_HEADER"] = ["linkRelation"]
       ->
       { "if":   { "properties": {"paginationType": {"const": "LINK_ITERATION"},
                   "linkParamType": {"const": "LINK_HEADER"}},
                   "required": ["paginationType", "linkParamType"] },
         "then": { "properties": {"linkRelation": { ... } } } }

Both are emitted as SIBLING entries in the top-level `allOf` array - not one nested inside the
other's `then`. This is a DELIBERATE, LOWER-EFFORT CHOICE (flagged in section 8): the extractor
already resolves a two-level gate to a single compound condition (both axis values ANDed
together, :279-326) rather than a literal tree, so a flat allOf entry per matrix key reproduces
it exactly with a mechanical, one-key-at-a-time translation. A caller reasoning about whether
`linkRelation` applies still has to satisfy both branches (the single-axis one that admits
`linkParamType` and the combination one that admits `linkRelation`) - true nesting (`if` inside
a `then`) would say the same thing more verbosely for identical semantics, at real
implementation cost for zero behavior change. Confirmed real example: ActiveCampaignQuery.java
(and ~50 sibling REST connectors) declare exactly this two-level shape (:82-83,
`linkParamType` visibleMethod = "isLinkIterationPagination", `linkRelation` visibleMethod =
"isLinkHeaderParamDisplayed").

A param appearing ONLY inside some `then` branch (i.e. `param.isConditional()` is true) is
OMITTED from the top-level `properties` map entirely - it is reachable only through the allOf
branch that admits it. This is what makes pruning automatic (11.1, point 1): an agent reading
top-level `properties` sees only what always applies.

COMPOSITE PARAM - KIND A vs KIND B, AT SCHEMA-GENERATION TIME. This is a DIFFERENT MOMENT than
section 3.4's runtime rule, and cannot always reach the same answer, because `query` here is
still blank (11.3): a composite gated by dependsOn (e.g. EndpointJsonQuery.parameters,
dependsOn = "endpoint") reads an EMPTY skeleton on a blank query for the SAME reason a genuinely
Kind-B composite does - the prerequisite that would populate it has not been set. The schema
builder therefore uses a WEAKER, SAFE-BY-CONSTRUCTION rule that never mis-labels a true Kind A
composite as unsupported (the failure mode that would deadlock the agent, per A2), at the cost
of sometimes describing an always-Kind-B composite as if it might be fillable:

    isComposite = TabularSchemaExtractor.isCompositeType(javaType) AND javaType != File.class
    if isComposite:
       if param.getDependsOn() is non-empty:
          -- cannot observe the real skeleton yet; assume POTENTIALLY Kind A
          emit {"type": "object", "additionalProperties": {"type": "string"},
                "x-skeleton": "<first dependsOn name>",
                "description": "Fill by name once '<dependsOn>' is set; legal keys are read
                                 from the live skeleton at write time, not listed here. See
                                 GET .../query-schema again after setting '<dependsOn>', or just
                                 try - an unknown key is refused by name, not silently dropped."}
          -- NEVER placed in "required" (A4/A2's rule), regardless of @Property.required
       else:
          skeleton = query's live value for this property (prop.getValue(query), read once,
                     same reflection path as capability 1's write-time read)
          elements = elementsOf(skeleton)  -- unwraps RestParameters.getParameters(), a T[]
                     array, or a List; else null. Section 3.4.1 defines this precisely; the
                     builder and the write path share the one implementation.
          if elements non-null and non-empty and every element's getName() (reflectively
             invoked) is non-null:
             -- Kind A, immediately fillable, no gating prerequisite
             emit {"type": "object", "additionalProperties": {"type": "string"},
                   "description": "Fill by name; legal keys: <elements' getName() values,
                                    joined>."}
          else:
             -- Kind B: no dependsOn AND empty/unnamed on a blank query - this is the one case
             -- the blank probe CAN answer definitively (ServerFileQuery.columns and
             -- EndpointJsonQuery.additionalParameters are exactly this: no dependsOn declared,
             -- always empty). OMIT FROM THE SCHEMA ENTIRELY (default) - same treatment as a
             -- derived no-op (`suffix` on a named connector). If the implementer instead
             -- chooses to keep a documentation-only stub (flagged decision, section 8):
             emit {"description": "Not settable - a product of probing, not an input.",
                   "x-output": true}
             -- and in EITHER case, NEVER place the name in "required".

REQUIRED ARRAY (per A4 - NOT a mechanical copy of @Property.required):

    required = [ name for name in unconditional params if
                    param.isRequired()                                   -- @Property/@View
                    AND NOT (isComposite(param) AND resolved Kind B above)  -- A2
                    AND name is present in top-level `properties`            -- never list a
                                                                               name that was
                                                                               pruned into a
                                                                               `then` branch
               ]

This still UNDER-reports (ODataQuery.entity carries no @Property.required=true yet is
indispensable - A4's second row) and can OVER-report nothing, by construction, because the one
correction this pass makes (A2/Kind B exclusion) only ever REMOVES a name, never adds one. The
schema itself carries this limitation as a top-level note (not a param-level `x-` key, since it
describes the array as a whole, not any one property):

    "description": "... 'required' lists what this connector's own @Property annotations and
                    layout declare mandatory when a parameter is unconditionally visible. It is
                    known to be incomplete: some connectors enforce a requirement only in their
                    own runtime code (pagination parameters are the common case), which this
                    list cannot see. A parameter's own 'description' is the more complete guide
                    when the two disagree."

### 11.5 The x- extension keywords - FIXED LIST, do not add others in this pass (REVISED -
    x-valueSource collapsed, see revision log)

JSON Schema validators ignore unknown keywords by default (ajv's `strict` mode is a wiz-side
concern - flagged as a cross-repo coordination point in section 8, not this document's to
resolve). `x-` is the OpenAPI-derived convention for "a validator ignores this; a reader should
not." This backend emits exactly these six, no others:

| keyword | type | emitted when | meaning |
|---|---|---|---|
| `x-valueSource` | `"external"` \| `"unavailable"` \| `"too-large"` | any param with a tagsMethod | the value is NOT carried in this contract - a plain fact marker, not a routing instruction. See 11.5.1 for why it no longer says WHERE to go. |
| `x-tagsMethod` | string | any param with a non-empty tagsMethod, REGARDLESS of resolveTags | the connector method name a future targeted lookup would invoke - an address, not a value. |
| `x-skeleton` | string | a Kind A composite gated by dependsOn (11.4) | which param's value determines this composite's legal key set. Absent when the composite has no gating prerequisite. |
| `x-enumLabels` | string[] | alongside an inlined `enum` | display labels, parallel by index to `enum`'s values. |
| `x-output` | `true` | a Kind B composite the implementer chose to keep as a stub (11.4) | this is a product of probing, not an input - never place its name in `required`. |
| `x-candidateCount` | number | alongside `x-valueSource: "too-large"` | how many candidates existed, so the agent can decide whether to narrow the question rather than guess (section 12). |

### 11.5.1 REVISED THIS PASS: x-valueSource collapsed to a fact marker, the catalog/runtime
     routing distinction removed

An earlier draft of this section gave `x-valueSource` a fourth, wiz-only value
(`"catalog"`) and had the backend reason about a boundary against it (never emit it,
wiz alone writes it, keyed off wiz's own `annotationClass === "ENDPOINT_CATALOG"`
classification). THAT ENTIRE DISTINCTION IS REMOVED, not merely narrowed - there is no
`classifyQueryClass`/`annotationClass`-based logic anywhere in this design any more.

REASONING (the human's, via the coordinator): an agent can only reach "fetch this schema" for
a data source it has already found. If it found the data source through `search_schema`, that
connector is by construction already ingested into wiz's recall index, and its candidate
values are already sitting in the recall result the agent is holding - restating "these values
came from a catalog" in the schema is redundant, and the backend has no way to know wiz's
ingest state well enough to say it reliably (it would be guessing at a fact that belongs to a
different system). The routing decision ("go check the catalog you already recalled from" vs.
"go ask the server") is therefore something the AGENT ALREADY KNOWS FROM ITS OWN CONTEXT -
which path got it here - and does not need the schema to repeat.

`x-valueSource` therefore only ever answers ONE question - "is the legal value set present in
this contract, yes or no, and if no, did resolving it just fail" - never "where should you go
instead." The three values, renamed to make this literal (old name -> new name):

    "runtime"             -> "external"      the value is not in this contract
    "runtime-unavailable" -> "unavailable"   resolveTags tried and failed/timed out
    "runtime-large"       -> "too-large"     resolveTags tried, count exceeded the cap

WHAT CARRIES THE SOURCE NOW: `description`, in plain language, strengthened into a MANDATORY
requirement (11.5.2) rather than a nice-to-have - since `x-valueSource` no longer names a
destination, the only place left that can is prose. Every tagsMethod-backed param's
`description` must name where its values actually come from (the connector's own concept: an
OData `$metadata` document, a jar-packaged list, a live account listing - never the word
"catalog", which is a wiz-internal concept this backend does not have visibility into). See
11.6 for exact worked wording.

### 11.5.2 Mandatory (STRENGTHENED THIS PASS): `description` carries the SOURCE, in plain
     language, not just the `x-` key's meaning restated

`x-` is read by programs (ajv, wiz's own pre-request checks); `description` is what an LLM
reliably reads - and now, after 11.5.1's collapse, `description` is the ONLY place the actual
origin of a value is stated at all, so this is no longer just "write the same fact twice for
two audiences," it is "write the ONE fact that matters in the ONE place that survives." Every
tagsMethod-backed param's `description` MUST name:

1. WHAT the value actually is (in the connector's own terms - "entity" for OData, not a generic
   "identifier");
2. WHERE it concretely comes from (the connector's `$metadata` document, a jar-packaged list, a
   live account/service listing - the real origin, never the word "catalog");
3. WHAT TO DO if this response did not resolve it - retry, ask the user, or narrow the request,
   per which `x-valueSource` value is present (11.5.1, 12.2's degradation-specific wording).

Worked example (OData `entity`, resolveTags=true, resolved successfully):

    "Entity. Values come from this OData service's $metadata document. This request attempted
    to resolve them - see 'enum' below for the current list. If 'enum' is absent or looks
    stale, ask the user to name the entity directly rather than guessing."

Same param, `resolveTags=true`, but the resolution failed (`x-valueSource: "unavailable"`):

    "Entity. Values come from this OData service's $metadata document. This request tried to
    fetch them and could not (timeout or connector error) - retry with resolveTags=true, or
    ask the user to name the entity directly."

This is a testable invariant, not aspirational prose - see the new coverage in section 7.9: for
every property carrying any `x-` key, its `description` must be non-empty and must
(string-contains) name both the concrete origin and, where applicable, the next action.

### 11.6 Worked example - EndpointJsonQuery-shaped connector, resolveTags=false (default)
    (REVISED - x-valueSource values renamed, description now names the concrete source)

    {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "properties": {
        "endpoint": {
          "type": "string",
          "description": "Endpoint. Values come from this connector's own catalogued
                          endpoint list (getEndpoints(), a listing packaged in the connector's
                          own jar). Not resolved in this response - if the endpoint name is not
                          already known from how this data source was reached, re-request with
                          resolveTags=true.",
          "x-valueSource": "external",
          "x-tagsMethod": "getEndpoints"
        },
        "parameters": {
          "type": "object",
          "additionalProperties": {"type": "string"},
          "description": "Fill by name once 'endpoint' is set; legal keys are read from the
                          live skeleton at write time, not listed here. See
                          GET .../query-schema again after setting 'endpoint', or just try -
                          an unknown key is refused by name, not silently dropped.",
          "x-skeleton": "endpoint"
        },
        "requestType": { "type": "string", "enum": ["GET"],
                          "description": "Request type. Values come from this connector's own
                                          fixed list (getRequestTypes()); resolved here because
                                          the list is small and free to compute.",
                          "x-valueSource": "external", "x-tagsMethod": "getRequestTypes" }
      },
      "required": [],
      "allOf": [],
      "unevaluatedProperties": false
    }
    -- "additionalParameters" (Kind B, no dependsOn, always null) does not appear at all.
    -- nothing is in "required": endpoint has no @Property(required=true) of its own (A4's
       third row - ODataQuery.entity is the same shape), and "parameters" is excluded from
       "required" per A2 regardless of what @Property says once the connector declares it so.
    -- "external" replaces the old "runtime": it says only "not in this contract," never
       "check a catalog" - see 11.5.1 for why that routing word was removed.

### 11.6.1 Worked example - dependencyMatrix nesting, resolveTags=false

For an EndpointJsonQuery connector with LINK_ITERATION pagination (ActiveCampaignQuery-shaped,
:82-83):

    "properties": {
      "paginationType": {"type": "string", "enum": ["NONE","PAGE_COUNT", ...],
                          "description": "Pagination strategy. Values come from this
                                          connector's own fixed list (getPaginationTypes()).",
                          "x-valueSource": "external", "x-tagsMethod": "getPaginationTypes"}
    },
    "allOf": [
      { "if":   {"properties": {"paginationType": {"const": "LINK_ITERATION"}},
                 "required": ["paginationType"]},
        "then": {"properties": {"linkParamType": {"type": "string",
                                 "enum": ["LINK_HEADER","LINK_BODY"], ...}}} },
      { "if":   {"properties": {"paginationType": {"const": "LINK_ITERATION"},
                                 "linkParamType": {"const": "LINK_HEADER"}},
                 "required": ["paginationType", "linkParamType"]},
        "then": {"properties": {"linkRelation": {"type": "string", ...}}} }
    ]

`linkParamType` and `linkRelation` are NOT in top-level `properties` - only `paginationType` is,
matching the pruning claim in 11.1.

THIS EXAMPLE IS ALSO THE PROOF that the root keyword must be `unevaluatedProperties`, not
`additionalProperties` (11.4). `linkParamType` and `linkRelation` exist ONLY inside
`allOf[].then`, so a root `additionalProperties: false` - which sees only the sibling
`properties` map - would reject a perfectly legal
`{paginationType: "LINK_ITERATION", linkParamType: "LINK_HEADER", linkRelation: "next"}`.
Measured against ajv 8.20.0, not reasoned. The pruning that makes the top level small is the
very thing that puts these names out of `additionalProperties`' reach, so the two decisions
are not independent: choosing to sink conditional params into branches REQUIRES the
`unevaluatedProperties` root.

---

## 12. resolveTags query parameter (NEW THIS PASS)

GET /api/wiz/tabular/query-schema?path=X&resolveTags=true - default false (11.3's
`@RequestParam(..., defaultValue = "false")`).

### 12.0 REVISED THIS PASS: resolveTags is LOAD-BEARING, not an optimization - it is the ONLY
     source of candidate values for METADATA-class connectors on the one path that reaches them

STATED PLAINLY, because the earlier draft of this document described resolveTags as "saves a
round trip" and that framing was wrong: with no `query-tags` endpoint and no catalogue
ingestion this pass (section 13), `resolveTags=true` is the ONLY mechanism by which an agent
can ever learn a METADATA-class connector's own candidate values (an OData `entity`, a
Salesforce object name) through this API. There is no fallback path to fall back TO.

Why there is no other path: METADATA-class data sources are filtered out of `search_schema`
today (wiz-side, `organizationSourceTypes.ts:197` - confirmed cited by the wiz-side design,
not independently re-read here since it is the other repo). An agent therefore never learns
one exists, or what its entities are called, through recall. The ONLY way it reaches a
METADATA-class connector at all is the user naming the data source directly - and once it does,
`describe_tabular_query(path, resolveTags=true)` is the only remaining way to learn what
values `entity` (or the equivalent) actually accepts. The full flow that has to work
end-to-end:

    user names an OData data source
       -> describe_tabular_query(path, resolveTags=true)
       -> backend invokes getEntityRefs() (one real service-document request)
       -> entity candidates inlined as "enum"
       -> agent picks "Customers" -> fills -> builds the table

If that request times out or the candidate count exceeds the cap, THE FEATURE HAS FAILED for
that connector on that call - this is not a graceful degradation into "the agent asks the
endpoint another way," because there is no other way this pass. Whoever implements this must
build and test it with that understanding, not as a nice-to-have inlining of an enum. See
12.3's REVISED risk framing and D10 (section 8) for the consequence this has for the timeout
and cap defaults, and 12.2's degradation wording for how the agent is told what to do when it
does fail.

### 12.1 Why default-off is load-bearing, not a style choice

A DIFFERENT question from 12.0's - 12.0 is about why the CAPABILITY, once invoked, is
load-bearing; this subsection is about why the DEFAULT stays false despite that.

The endpoint's own existing javadoc (WizTabularController.java:251-253) says it "returns a
description of the connector's shape and no value the user configured" - and that sentence is
literally why it is gated on READ rather than WRITE (unlike /tabular/definition next to it,
:302-304, which returns stored credentials and demands WRITE) and is safe to cache. Resolving
tags breaks BOTH properties at once if it were the default: it issues external requests
(getEntityRefs() is a real OData service-document call, not a jar read), it can return TENANT
DATA (a resolved Salesforce custom object name or a customer's own OData entity name is that
customer's business information, not connector shape), and the response stops being "a rarely-
changing structure" and becomes "data that changes when the customer's service changes." Making
it opt-in keeps the existing READ-gated, cacheable default response exactly as it is today, and
confines all three new costs to a caller that explicitly asked for them.

### 12.2 Per-parameter rule, resolveTags=true (REVISED - x-valueSource values renamed,
     degradation `description` text is now the agent's ONLY route to a next action)

    for each param with a non-empty tagsMethod:
       if param.getDependsOn() is non-empty:
          -- skip; keep x-valueSource: "external" (unchanged from the false case)
          -- WHY: the query used here (11.3) is blank, so a dependent tagsMethod is invoked
          -- with its own prerequisite unset and returns EMPTY - inlining an empty `enum` is
          -- WORSE than not inlining, because an agent reads an empty enum as "no legal value
          -- exists" rather than "this needs something else set first." Confirmed shape:
          -- getLookupEndpoints0() (dependsOn="endpoint") returns nothing meaningful before
          -- endpoint is set, same pattern as every dependsOn-gated tagsMethod in this codebase.
          -- description must say so explicitly: "... set '<dependsOn>' first, then re-request
          -- with resolveTags=true to see this param's legal values."
       else:
          invoke tagsMethod (12.4) with a bounded timeout
          if timeout or any thrown exception:
             x-valueSource: "unavailable"
             description must tell the agent to RETRY: "... this request tried to resolve
                these values and could not (timeout or connector error). Retry with
                resolveTags=true - on this connector there is no other way to learn them."
          else if candidate count > cap:
             x-valueSource: "too-large"
             x-candidateCount: <count>
             description must tell the agent to NARROW, not retry blindly: "... this connector
                has <count> candidates, too many to list. Ask the user to name the value
                directly rather than guessing from a partial list."
          else:
             inline "enum": [values], "x-enumLabels": [labels]  -- agent's next move: pick one

The three outcomes are DISTINCT x-valueSource VALUES, not one shared "could not resolve" flag,
because the agent's correct next action differs by which one it sees - and, per 11.5.1's
collapse, THE DESCRIPTION TEXT ABOVE IS NOT OPTIONAL COLOR: for a METADATA-class connector on
this path (12.0), it is the only place the agent is told what to do next at all. Getting this
prose wrong is not a documentation nit, it is the difference between an agent that retries
correctly and one that gives up or hallucinates a value.

### 12.3 Concrete timeout and cap defaults, and why (REVISED - re-filed from a tuning nit to a
     PRIMARY RISK, per D10's correction in section 8; a 5s timeout that trips is not "slower,"
     it is the connector becoming UNUSABLE through this API, per 12.0)

Cost is uneven and is NOT visible from the annotation alone: `getEndpoints()` reads a JSON file
packaged inside the connector's jar (effectively free, sub-millisecond); `getEntityRefs()`
issues a real HTTP request to the customer's OData service (getServiceDocumentRequest, network-
bound, billable against that service's own rate limits). Both are declared with the identical
`@PropertyEditor(tagsMethod = "...")` shape - nothing in the annotation distinguishes them.
Hence a per-call timeout and a per-call candidate cap are both REQUIRED, not tunable-later
niceties:

- TIMEOUT: 5 seconds per tagsMethod invocation. Precedent in this codebase for an external,
  connector-adjacent call of comparable shape: HttpAssistantDocSearchGateway's own outbound
  client uses a 5s CONNECT_TIMEOUT / 20s RESPONSE_TIMEOUT
  (core/src/main/java/inetsoft/web/wiz/docs/HttpAssistantDocSearchGateway.java:69,84-85). This
  document uses the shorter of the two (5s) for the reason stated at the time (a schema fetch
  can invoke MANY tagsMethods in one request) - BUT THE REASON THAT USED TO JUSTIFY THIS AS
  ACCEPTABLE NO LONGER HOLDS: it previously said a caller that hits "runtime-unavailable" can
  retry that one field's resolution narrowly once a `query-tags` endpoint exists (section
  13.1) - THAT ENDPOINT DOES NOT EXIST THIS PASS (section 13.1, unchanged), so on a
  METADATA-class connector there is, right now, no narrower retry path at all: a timeout here
  is the whole capability failing for that param, full stop (12.0). 5 SECONDS IS THEREFORE A
  RISK, NOT A SETTLED CHOICE - see the acceptance check below.
- CAP: 200 candidates. Chosen to comfortably admit realistic finite enumerations (ISO 4217
  currencies, ~180) while excluding open-ended ones (IANA timezone names, ~400) that are better
  served by narrowing than by inlining. NOT VERIFIED against a live connector's actual
  candidate counts in this pass. SAME ELEVATED SEVERITY AS THE TIMEOUT: for a METADATA-class
  connector whose real entity count sits just over 200, "too-large" is not a soft degradation
  today - the agent is told to ask the user to narrow, which may not be answerable for an
  entity NAME the user does not already know (they named the DATA SOURCE, not the entity).

SHIP-TIME ACCEPTANCE CHECK, REQUIRED (SAME CLASS OF REQUIREMENT AS D6'S READ-BACK CHECK,
section 7.8) - NOT OPTIONAL: before this ships, run `resolveTags=true` against AT LEAST ONE
REAL METADATA-class connector (OData is the concrete, already-cited case: an ODataQuery
pointed at a real service, `entity`'s tagsMethod `getEntityRefs()` actually invoked over the
network) and confirm candidates come back inside the 5s timeout and under the 200-candidate
cap. If either trips on a real, reasonably-sized service, THE DEFAULTS ARE WRONG BEFORE SHIP,
not an acceptable tuning gap to fix later - because 12.0 established there is no fallback for
this pass to fall back on. See D10 (section 8) for the decision record and section 9 for the
risk this check has not yet been run.

Both are FIXED CONSTANTS in TabularQueryParamsSchemaBuilder in this pass, not exposed as a
request parameter - keeping the wire (section 11.3) to the one boolean the task calls for, and
avoiding a caller-tunable timeout/cap becoming its own source of surprising behavior before
there is any evidence the fixed defaults are wrong for a real connector. This is unchanged from
the earlier draft; what changed is the CONSEQUENCE of the defaults being wrong, not the choice
to fix them as constants.

### 12.4 Implementation: a bounded wrapper around TabularUtil.invokeTagsMethod

TabularUtil.invokeTagsMethod (the extraction section 5 already plans, pulled out of
callEditorMethods's inline String[]/String[][] branch, :817-833) stays SYNCHRONOUS and
timeout-free - its existing caller (the composer dialog's async per-field fetch, already
running each tagsMethod on its own GroupedThread) is unaffected, and capability 3's write-path
validation (section 3.5) does not need a timeout either: it is validating one already-supplied
value against a candidate list, not eagerly resolving every enum in the schema.

The NEW timeout/cap wrapper lives in TabularQueryParamsSchemaBuilder itself, used ONLY by
resolveTags:

    static String[][] resolveWithBudget(TabularQuery query, String tagsMethod) {
       Future<String[][]> f = boundedExecutor.submit(
          () -> TabularUtil.invokeTagsMethod(query, tagsMethod));
       try {
          return f.get(5, TimeUnit.SECONDS);
       }
       catch(TimeoutException | ExecutionException ex) {
          f.cancel(true);   // best-effort; a reflective call already in flight cannot be
                             // forcibly interrupted, same caveat GroupedThread's own dialog
                             // path already lives with today
          return null;      // caller maps null to x-valueSource: "unavailable"
       }
    }

Same shape as the one other future.get(timeout, ...) precedent in this codebase
(WizVisualizationService.java:416, thumbnailFuture.get(THUMBNAIL_TIMEOUT_SECONDS, ...)) -
reusing an established pattern rather than inventing a new concurrency idiom for this one path.

### 12.5 String[][] -> enum/x-enumLabels

`getEntityRefs()`-style methods return `String[][]` (label/value pairs; confirmed shape at
TabularUtil.callEditorMethods:824-832, `pairs[i][0]` = label, `pairs[i][1]` = value). Mapping:
`enum` takes every `pairs[i][1]` (the value slot - what a caller must actually send back);
`x-enumLabels` takes every `pairs[i][0]`, parallel by index. Identical to how capability 3
(section 3.5) already reads the same shape for validation - one normalization, two consumers.

---

## 13. Explicitly out of scope this pass (NEW)

### 13.1 No query-tags endpoint

A separate endpoint that runs one named tagsMethod, with the caller's own partial parameter
values supplying the dependsOn prerequisites a dependent tagsMethod needs, is NOT built this
pass. A dependent param (section 12.2's "has dependsOn -> skip" case) is left at
`x-valueSource: "external"`, with `description` telling the agent plainly it cannot be
enumerated ahead of time and must be filled once its prerequisite is chosen (mirroring
capability 3's existing validate-on-write behavior, section 3.5). TIED TO 12.0'S REVISED
FRAMING: a dependent param and a resolveTags failure (12.2's "unavailable"/"too-large") are now
the SAME shape of gap - neither has anywhere else to go this pass - which is exactly why 12.2's
degradation `description` text was upgraded from color to load-bearing instruction.

The underlying mechanism for such an endpoint already exists and is not being built new when
this is eventually taken up: TabularQueryDialogController.refreshTabularView
(core/src/main/java/inetsoft/web/composer/ws/dialog/TabularQueryDialogController.java:67-69)
already accepts a partially-filled query, sets its VariableTable and any already-known
property values, and calls TabularUtil.refreshView (:75-94, dispatching to
TabularUtil.callEditorMethods, section 1.4), which runs every dependsOn-satisfied tagsMethod
and returns the refreshed candidates. Its wire type is TabularView, a layout tree - the wiz-side
task doc's own section 5.2 (per this pass's coordination, not independently re-verified against
that document's exact wording here since it lives in the other repo) already ruled that shape
out as something to expose to a tool caller. A future query-tags endpoint therefore REUSES this
mechanism (same reflective dispatch, same dependsOn-satisfied-tagsMethod invocation) behind a
NEW, narrower response shape - not a new capability to build from nothing.

### 13.2 No catalogue ingestion of connector metadata (the S1 metadata channel) (REVISED - the
     "catalog" wire value this section previously described no longer exists, see 11.5.1)

A later, independent pass may ingest an S1 connector's own declarative metadata (OData's
$metadata document, and equivalents for Salesforce/GA4/SharePoint/SAP) into wiz's own recall
index. That ingestion, its storage shape, and its staleness/versioning story are ALL OUT OF
SCOPE HERE and are not designed in this document.

REVISED: an earlier draft of this section described the benefit of that future ingestion as
"params like ODataQuery.entity stop being `x-valueSource: 'runtime'` and become `'catalog'`."
That WIRE-LEVEL upgrade path no longer exists - 11.5.1 removed the catalog/external
distinction from `x-valueSource` entirely, and the backend built by this document never
encodes ingestion state at all, ingested or not. What ingestion would actually change is
UPSTREAM of this endpoint: today, METADATA-class connectors are filtered out of `search_schema`
(`organizationSourceTypes.ts:197`, section 12.0), so the human always has to name them
directly, and `resolveTags=true` is always the only route to their candidate values (12.0). If
a future ingestion pass makes METADATA-class connectors recall-reachable, the SAME "an agent
already knows how it arrived" reasoning that justified collapsing `x-valueSource` (11.5.1)
would extend to them too - an agent that found an OData entity through `search_schema` would
already have its name from the recall result, the same way it already does for a catalogued
REST endpoint today, and `resolveTags` would move back from load-bearing to a
nice-to-have confirmation for that connector. THAT IS A RECALL-SIDE CHANGE, ENTIRELY WIZ-SIDE -
nothing in sections 11-12 needs to change on the backend when it happens, since this document's
builder never referenced ingestion state to begin with.
