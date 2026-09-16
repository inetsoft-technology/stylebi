# Tabular datasource decision order

`add_table` can build a table against five kinds of datasource. Physical/logical-model tables
and ENDPOINT_CATALOG connectors (`endpoint`/`suffix` forms) already have their own discovery
path (`search_schema`, `list_logical_models`, `list_endpoint_lookups`). For the rest:

## METADATA (OData, Cassandra, Hive, Salesforce, GA4, Google Sheets, Facebook Ad Insights,
Elasticsearch, MongoDB, SharePoint Online, Aerospike, OrientDB) and FILE (OneDrive, ServerFile)

Both are self-discoverable — never ask the user something these tools can answer:

1. `list_tabular_targets(datasource)` — see what's available. If `enumerable: false`, this
   datasource is not actually METADATA/FILE-shaped the way you expected; fall through to the
   Rest/Rest.XML flow below instead.
2. `get_table_details(datasource, table)` on a candidate — preview its real columns.
3. `get_tabular_query_contract(datasource)` if the target needs more than its own id filled
   (rare for METADATA; common for FILE, which usually also needs `excelSheet` for a workbook or
   parse options like `firstRowHeader`).
4. `add_table` with `queryParams` — the target's own id plus whatever else step 3 required.

## Rest and Rest.XML (no predefined catalogue, no way to enumerate targets)

The TARGET (which resource, e.g. which country/report/record) cannot be discovered — self-
discovery tools do not apply to it by design, not because something is broken. But the
DATASOURCE itself — what host it's actually pointed at — is discoverable, and skipping this
step is how a plausible-looking `suffix`/`xpath` gets built against the wrong host or the wrong
XML shape entirely:

1. `get_tabular_datasource_config(datasource)` first. Answers what the datasource is actually
   configured to talk to (base URL, and any other connector-declared connection field) —
   without this, a caller who reasonably assumes a datasource named e.g. "REST XML" is pointed
   at some specific public API (say, World Bank's) has no way to confirm that guess before
   spending a live request finding out it's wrong. Every credential-shaped field comes back
   redacted to "is one configured", never its value — this call never needs to be withheld for
   privacy reasons.
2. `get_tabular_query_contract(datasource)` next. This is what turns an open-ended "describe
   your API" question into a precise one — the exact property names (`suffix`, `xpath`,
   `schema`, ...) and which are required.
3. Ask the user — in plain conversation, no dedicated tool exists or is needed for this — only
   for the fields the schema marks `required` that are not already known from the conversation
   so far, or that step 1 didn't already answer (e.g. confirming the datasource really is the
   API you both think it is). Every parameter marked `required: true` must be present before
   calling `add_table`; a guessed value (a guessed URL suffix, a guessed XPath) yields a 404 or
   a parse failure against a real, possibly billed, API — never fabricate one.
4. `add_table` with `queryParams` filled from the schema's property names and the user's
   answers.
