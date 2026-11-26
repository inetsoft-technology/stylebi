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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.DrillPath;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Tool;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;

import java.io.StringReader;
import java.util.*;

/**
 * The logical model validator of the data model bridge (design 4.4). The service validates almost
 * nothing at write time: it does not check that a table or column exists, that an expression
 * parses, that the folder exists, or that entity and attribute names are unique (a duplicate
 * attribute followed by another makes {@code LogicalModelService.createModel} throw an
 * {@code IndexOutOfBoundsException}, and otherwise the second one is silently dropped). So the
 * bridge checks all of it BEFORE calling the service and reports every problem, field-named, at
 * once (at most {@link #MAX_ERRORS}).
 *
 * <p>The table/column rule re-implements the private {@code LogicalModelService.validateAttribute}
 * and the expression rule of {@code LogicalModelController.checkExpressionSQL}, made stricter (see {@link #expressionProblem}); the parity test
 * {@code WizLogicalModelValidatorParityTest} pins both to a shared table of cases. Keys are
 * case-sensitive, as the registry's are.</p>
 */
final class WizLogicalModelValidator {
   static final int MAX_ERRORS = 50;

   static final String NAME_INVALID = "NAME_INVALID";
   static final String NAME_RESERVED = "NAME_RESERVED";
   static final String VIEW_NOT_FOUND = "VIEW_NOT_FOUND";
   static final String FOLDER_NOT_FOUND = "FOLDER_NOT_FOUND";
   static final String CONNECTION_NOT_FOUND = "CONNECTION_NOT_FOUND";
   static final String ENTITY_DUPLICATE = "ENTITY_DUPLICATE";
   static final String ATTRIBUTE_DUPLICATE = "ATTRIBUTE_DUPLICATE";
   static final String ATTRIBUTE_BINDING_AMBIGUOUS = "ATTRIBUTE_BINDING_AMBIGUOUS";
   static final String TABLE_NOT_IN_VIEW = "TABLE_NOT_IN_VIEW";
   static final String COLUMN_NOT_IN_TABLE = "COLUMN_NOT_IN_TABLE";
   static final String EXPRESSION_INVALID = "EXPRESSION_INVALID";
   static final String DATATYPE_INVALID = "DATATYPE_INVALID";
   static final String REFTYPE_INVALID = "REFTYPE_INVALID";
   static final String OLDNAME_UNKNOWN = "OLDNAME_UNKNOWN";
   static final String OLDNAME_REQUIRED = "OLDNAME_REQUIRED";
   static final String METADATA_UNAVAILABLE = "METADATA_UNAVAILABLE";
   static final String EXTENDED_UNSUPPORTED = "EXTENDED_UNSUPPORTED";
   static final String EXTENDED_VIEW_MISMATCH = "EXTENDED_VIEW_MISMATCH";
   static final String EXTENDED_FOLDER_MISMATCH = "EXTENDED_FOLDER_MISMATCH";
   static final String BASE_ELEMENT_READ_ONLY = "BASE_ELEMENT_READ_ONLY";
   static final String DRILL_INVALID = "DRILL_INVALID";
   /** A stored value the wire cannot carry, so an update would silently delete it. */
   static final String STORED_UNSUPPORTED = "STORED_UNSUPPORTED";

   WizLogicalModelValidator() {
   }

   /** What the validator needs from the live system; the bridge supplies one, tests fake it. */
   interface Env {
      XDataModel dataModel();

      boolean connectionExists(String connection);

      /**
       * The physical tables and columns of a view as the database reports them, with each column's
       * data type. Throws when the metadata cannot be read.
       */
      Map<String, Map<String, String>> metadata(XPartition view, String connection)
         throws Exception;

      /**
       * The asset identifier of the viewsheet a drill link names (a repository path or an asset
       * identifier), {@code null} when the caller can read no such viewsheet.
       */
      default String resolveViewsheet(String link) {
         return link;
      }

      /** The caller, the owner of a user-scope worksheet; {@code null} when not known. */
      default IdentityID caller() {
         return null;
      }

      /** Whether the worksheet {@code entry} exists and the caller may read it. */
      default boolean worksheetReadable(AssetEntry entry) {
         return true;
      }
   }

   enum Mode {
      CREATE, UPDATE
   }

   /** Errors (empty means valid), warnings, and the definition with filled data types. */
   record Result(List<Err> errors, List<String> warnings, Definition normalized) {
      boolean valid() {
         return errors.isEmpty();
      }
   }

   /**
    * Validates a definition.
    *
    * @param stored the stored model on an update (its entities decide the rename markers),
    *               {@code null} on a create.
    */
   Result validate(Env env, Definition definition, Mode mode, XLogicalModel stored) {
      return validate(env, definition, mode, stored, null);
   }

   /**
    * What an extended model needs to be validated: its base model and the reference content of
    * the elements it inherits. On a create the reference is the whole base model (an inherited
    * element may be left out, the bridge merges it in); on an update it is the stored extended
    * model's inherited elements, each of which must still be present and unchanged
    * ({@code requireAll}).
    */
   record Extended(XLogicalModel base, Definition reference, boolean requireAll) {
   }

   /** As above for an extended model ({@code extended} non-null). */
   Result validate(Env env, Definition definition, Mode mode, XLogicalModel stored,
                   Extended extended)
   {
      Run run = new Run(env, definition, mode, stored, extended);
      run.validate();
      return new Result(run.errors, run.warnings, run.normalized());
   }

   /**
    * Whether an expression parses completely. Returns the problem, or {@code null}.
    *
    * <p>An expression is accepted when it parses fully as a value expression, OR fully as a
    * search condition (a boolean or flag expression such as {@code T1.AMOUNT > 100},
    * {@code T1.NAME LIKE 'a%'}, {@code T1.ID IN (1,2,3)}, valid on MySQL and PostgreSQL). "Fully"
    * is asked of the PARSER: after the rule it must be at the end of input.</p>
    *
    * <p>That is deliberately stricter than {@code LogicalModelController.checkExpressionSQL},
    * which reads the lexer's next token after the parse, but the parser has already buffered its
    * lookahead from the lexer, so the token it reads is always the end of input and it only ever
    * notices an exception thrown mid-parse. A trailing operator ({@code 1 +}), an unclosed call
    * ({@code foo(}), a trailing word ({@code PRICE garbage}) and a stray {@code )} slip through
    * it; here none of them parses fully under either rule. A national string literal
    * ({@code N'text'}) is checked with its {@code N} prefix removed (the grammar does not know
    * it).</p>
    */
   static String expressionProblem(String expression) {
      String problem = problem(expression);

      if(problem == null) {
         return null;
      }

      String plain = NATIONAL.matcher(expression).replaceAll("'");
      return !plain.equals(expression) && problem(plain) == null ? null : problem;
   }

   private static String problem(String expression) {
      String asValue = problem(expression, false);

      if(asValue == null) {
         return null;
      }

      return problem(expression, true) == null ? null : asValue;
   }

   private static String problem(String expression, boolean condition) {
      SQLParser parser;

      try {
         parser = parse(expression, condition, false);
      }
      catch(Exception ex) {
         // report the error of the normal parse, which names the token
         return ex.toString();
      }

      return leftover(parser);
   }

   /**
    * Parses as {@code XUtil.parseSQLExpressionSyntax} does: normally, and again in guessing mode
    * (every syntax check, no semantic actions) when the normal parse fails on a construct that
    * only the model refuses, such as a scalar subquery.
    */
   private static SQLParser parse(String expression, boolean condition, boolean guessing)
      throws Exception
   {
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(expression)));

      if(guessing) {
         parser.getInputState().guessing = 1;
      }

      try {
         if(condition) {
            parser.search_condition();
         }
         else {
            parser.value_exp();
         }

         return parser;
      }
      catch(antlr.SemanticException | RuntimeException ex) {
         if(guessing) {
            throw ex;
         }

         try {
            return parse(expression, condition, true);
         }
         catch(Exception guessEx) {
            throw ex;
         }
      }
   }

   private static String leftover(SQLParser parser) {
      try {
         if(parser.LA(1) == antlr.Token.EOF_TYPE) {
            return null;
         }

         return "unexpected text after the expression: \"" + parser.LT(1).getText() + "\"";
      }
      catch(Exception e) {
         return e.toString();
      }
   }

   /** A national string literal prefix: an N right before a quote, not part of a word. */
   private static final java.util.regex.Pattern NATIONAL =
      java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_$.])[nN]'");

   /** One validation pass; holds the collected errors. */
   private static final class Run {
      Run(Env env, Definition definition, Mode mode, XLogicalModel stored, Extended ext) {
         this.ext = ext;
         this.env = env;
         this.def = definition;
         this.mode = mode;
         this.stored = stored;
      }

      void validate() {
         checkHeader();
         List<Entity> entities = def.entities() == null ? List.of() : def.entities();
         Set<String> entityNames = new HashSet<>();
         Set<String> storedEntities = stored == null ? Set.of() : storedEntityNames(stored);
         List<Item> entityItems = new ArrayList<>();

         for(int i = 0; i < entities.size(); i++) {
            entityItems.add(new Item(i, entities.get(i).name(), entities.get(i).oldName()));
         }

         Set<String> entityClaims = markers("entity", entityItems, storedEntities,
            i -> "entities[" + (blank(i.name()) ? i.index() : i.name()) + "]", true);
         lookAlikeEntities(entities, storedEntities, entityClaims);
         normalizedEntities = new ArrayList<>();

         for(int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            String path = "entities[" + (blank(entity.name()) ? i : entity.name()) + "]";

            if(blank(entity.name())) {
               add(path + ".name", "entity name is empty", NAME_INVALID);
            }
            else if(!entityNames.add(entity.name())) {
               add(path, "duplicate entity name \"" + entity.name() +
                  "\" (names are case-sensitive)", ENTITY_DUPLICATE);
            }

            normalizedEntities.add(checkEntity(entity, path));
         }

         if(ext != null) {
            checkBaseElements(entities);
         }

         if(mode == Mode.UPDATE && stored != null) {
            checkStoredRepresentable();
         }
      }

      /**
       * An inherited entity or attribute is the base model's: it may not be changed, renamed or
       * (on an update) removed here, except the Visible flag of an inherited attribute, which an
       * extended model may set (as the Portal editor does). The service silently ignores an edit of one, so the bridge
       * compares each with its reference and names the path.
       */
      private void checkBaseElements(List<Entity> submitted) {
         for(Entity ref : ext.reference().entities()) {
            Entity own = null;

            for(Entity entity : submitted) {
               if(ref.name().equals(entity.name())) {
                  own = entity;
               }
            }

            String path = "entities[" + ref.name() + "]";

            if(own == null) {
               if(ext.requireAll()) {
                  add(path, "\"" + ref.name() + "\" is inherited from the base model and cannot " +
                     "be removed here", BASE_ELEMENT_READ_ONLY);
               }

               continue;
            }

            if(!java.util.Objects.equals(text(ref.description()), text(own.description())) ||
               (ref.visible() == null || ref.visible()) != (own.visible() == null || own.visible()))
            {
               add(path, "\"" + ref.name() + "\" is inherited from the base model and cannot " +
                  "be changed here", BASE_ELEMENT_READ_ONLY);
            }

            List<Attribute> ownAttributes = own.attributes() == null ? List.of() :
               own.attributes();

            for(Attribute refAttribute : ref.attributes()) {
               Attribute match = null;

               for(Attribute attribute : ownAttributes) {
                  if(refAttribute.name().equals(attribute.name())) {
                     match = attribute;
                  }
               }

               String apath = path + ".attributes[" + refAttribute.name() + "]";

               if(match == null) {
                  if(ext.requireAll()) {
                     add(apath, "\"" + refAttribute.name() + "\" is inherited from the base " +
                        "model and cannot be removed here", BASE_ELEMENT_READ_ONLY);
                  }
               }
               else if(!WizLogicalModelReadBack.canon(refAttribute, false)
                  .equals(WizLogicalModelReadBack.canon(match, false)))
               {
                  add(apath, "\"" + refAttribute.name() + "\" is inherited from the base model " +
                     "and cannot be changed here", BASE_ELEMENT_READ_ONLY);
               }
            }

            // a renamed inherited element: the new name carries the old one as its marker
            for(Attribute attribute : ownAttributes) {
               String old = attribute.oldName();

               if(!blank(old) && !old.equals(attribute.name()) &&
                  ref.attributes().stream().anyMatch(a -> old.equals(a.name())))
               {
                  add(path + ".attributes[" + attribute.name() + "]",
                      "\"" + old + "\" is inherited from the base model and cannot be renamed " +
                         "here", BASE_ELEMENT_READ_ONLY);
               }
            }
         }

         for(Entity entity : submitted) {
            String old = entity.oldName();

            if(!blank(old) && !old.equals(entity.name()) &&
               ext.reference().entities().stream().anyMatch(e -> old.equals(e.name())))
            {
               add("entities[" + entity.name() + "]", "\"" + old + "\" is inherited from the " +
                  "base model and cannot be renamed here", BASE_ELEMENT_READ_ONLY);
            }
         }
      }

      private boolean isBaseAttribute(String entityName, String attributeName) {
         if(ext == null) {
            return false;
         }

         for(Entity ref : ext.reference().entities()) {
            if(ref.name().equals(entityName)) {
               return ref.attributes().stream().anyMatch(a -> a.name().equals(attributeName));
            }
         }

         return false;
      }

      private String text(String value) {
         return blank(value) ? null : value;
      }

      Definition normalized() {
         return new Definition(def.name(), def.description(), def.view(), def.folder(),
                               def.connection(), def.parent(), def.extended(),
                               normalizedEntities == null ? def.entities() : normalizedEntities);
      }

      // -- header ------------------------------------------------------------------------------

      private void checkHeader() {
         if(ext == null && !blank(def.parent())) {
            add("parent", "parent: extended logical models are not supported in this version",
                EXTENDED_UNSUPPORTED);
         }
         else if(ext != null && !ext.base().getName().equals(def.parent())) {
            add("parent", "parent \"" + def.parent() + "\" is not the base model \"" +
               ext.base().getName() + "\"", EXTENDED_UNSUPPORTED);
         }

         try {
            WizDataModelNames.requireValidModelName(def.name());
         }
         catch(IllegalArgumentException e) {
            add("name", e.getMessage(),
                WizDataModelNames.isReserved(def.name()) ? NAME_RESERVED : NAME_INVALID);
         }

         String view = def.view();

         if(ext != null) {
            String baseView = ext.base().getPartition();

            if(!blank(view) && !view.equals(baseView)) {
               add("view", "an extended model uses its base model's view \"" + baseView +
                  "\", not \"" + view + "\"", EXTENDED_VIEW_MISMATCH);
            }

            view = baseView;
         }
         else if(blank(view) && mode == Mode.UPDATE && stored != null) {
            view = stored.getPartition();
         }

         if(blank(view)) {
            add("view", "view is required", VIEW_NOT_FOUND);
         }
         else {
            partition = env.dataModel().getPartition(view);

            if(partition == null) {
               add("view", "no physical view named \"" + view + "\" in the data source",
                   VIEW_NOT_FOUND);
            }
            else if(ext != null) {
               partition = variant(partition, def.connection());
            }
         }

         String folder = def.folder();

         if(ext != null && !blank(def.description()) &&
            !def.description().equals(ext.reference().description()))
         {
            add("description", "the description of an extended model is its base model's " +
               "and cannot be changed here", BASE_ELEMENT_READ_ONLY);
         }

         if(ext != null) {
            if(!blank(folder) && !folder.equals(ext.base().getFolder())) {
               add("folder", "an extended model is in its base model's folder" +
                  (blank(ext.base().getFolder()) ? " (the root)" : " \"" +
                     ext.base().getFolder() + "\"") + ", not \"" + folder + "\"",
                   EXTENDED_FOLDER_MISMATCH);
            }
         }
         else if(!blank(folder) && !Arrays.asList(folders()).contains(folder) &&
            !(stored != null && Tool.equals(folder, stored.getFolder())))
         {
            add("folder", "no data-model folder \"" + folder + "\"", FOLDER_NOT_FOUND);
         }

         String connection = def.connection();

         if(!blank(connection) && !env.connectionExists(connection)) {
            add("connection", "no additional connection \"" + connection + "\" on the data source",
                CONNECTION_NOT_FOUND);
         }
      }

      /**
       * The partition of an extended model's attributes: the variant of the extended connection
       * when the view has one, else the first variant with no connection, else the view itself
       * (as {@code LogicalModelService.validateAttribute} resolves it).
       */
      private XPartition variant(XPartition view, String connection) {
         if(!blank(connection) && view.containPartition(connection)) {
            return view.getPartition(connection);
         }

         for(String name : view.getPartitionNames()) {
            if(view.getPartition(name).getConnection() == null) {
               return view.getPartition(name);
            }
         }

         return view;
      }

      private String[] folders() {
         String[] folders = env.dataModel().getFolders();
         return folders == null ? new String[0] : folders;
      }

      // -- entities and attributes -------------------------------------------------------------

      private Entity checkEntity(Entity entity, String path) {
         List<Attribute> attributes = entity.attributes() == null ? List.of() :
            entity.attributes();
         String storedName = storedEntityName(entity);
         XEntity storedEntity = stored == null || storedName == null ? null :
            stored.getEntity(storedName);
         Set<String> storedAttributes = storedEntity == null ? Set.of() :
            storedAttributeNames(storedEntity);
         List<Item> items = new ArrayList<>();

         for(int i = 0; i < attributes.size(); i++) {
            items.add(new Item(i, attributes.get(i).name(), attributes.get(i).oldName()));
         }

         Set<String> claims = markers("attribute", items, storedAttributes,
            i -> path + ".attributes[" + (blank(i.name()) ? i.index() : i.name()) + "]", false);
         lookAlikeAttributes(path, attributes, storedEntity, storedAttributes, claims);
         Set<String> names = new HashSet<>();
         List<Attribute> normalized = new ArrayList<>();

         for(int i = 0; i < attributes.size(); i++) {
            Attribute attribute = attributes.get(i);
            String apath = path + ".attributes[" + (blank(attribute.name()) ? i :
               attribute.name()) + "]";

            if(blank(attribute.name())) {
               add(apath + ".name", "attribute name is empty", NAME_INVALID);
            }
            else if(!names.add(attribute.name())) {
               add(apath, "duplicate attribute name \"" + attribute.name() +
                  "\" in entity \"" + entity.name() + "\" (names are case-sensitive)",
                   ATTRIBUTE_DUPLICATE);
            }

            // an inherited attribute is the base model's; it is compared, not re-validated
            normalized.add(isBaseAttribute(entity.name(), attribute.name()) ? attribute :
                              checkAttribute(attribute, apath,
                                             storedAttribute(storedEntity, attribute)));
         }

         return new Entity(entity.name(), entity.oldName(), entity.description(),
                           entity.visible(), entity.baseElement(), entity.errorMessage(),
                           normalized);
      }

      /** The stored attribute an update attribute refers to: its marker, else its own name. */
      private static XAttribute storedAttribute(XEntity storedEntity, Attribute attribute) {
         if(storedEntity == null) {
            return null;
         }

         String raw = attribute.oldName();
         String name = raw == null ? attribute.name() : raw;
         return blank(name) ? null : storedEntity.getAttribute(name);
      }

      private Attribute checkAttribute(Attribute attribute, String path,
                                       XAttribute storedAttribute)
      {
         boolean hasExpression = !blank(attribute.expression());
         boolean hasColumn = !blank(attribute.column()) || !blank(attribute.table());
         String dataType = attribute.dataType();

         if(hasExpression && hasColumn) {
            add(path, "give either table+column or expression, not both (a column attribute has " +
               "table and column; an expression attribute has expression)",
                ATTRIBUTE_BINDING_AMBIGUOUS);
         }
         else if(!hasExpression && !hasColumn) {
            add(path, "an attribute needs table+column or expression",
                ATTRIBUTE_BINDING_AMBIGUOUS);
         }
         else if(attribute.type() != null) {
            String type = hasExpression ? WizLogicalModelWire.TYPE_EXPRESSION :
               WizLogicalModelWire.TYPE_COLUMN;

            if(!type.equals(attribute.type())) {
               add(path + ".type", "type \"" + attribute.type() + "\" conflicts with the " +
                  (hasExpression ? "expression" : "table and column") + " given (that is a " +
                  type + " attribute)", ATTRIBUTE_BINDING_AMBIGUOUS);
            }
         }

         if(hasExpression && !hasColumn) {
            String problem = expressionProblem(attribute.expression());

            if(problem != null) {
               add(path + ".expression", "the expression cannot be parsed: " + problem,
                   EXPRESSION_INVALID);
            }

            if(blank(dataType)) {
               add(path + ".dataType", "dataType is required on an expression attribute",
                   DATATYPE_INVALID);
            }
         }
         else if(hasColumn && !hasExpression) {
            if(blank(attribute.table())) {
               add(path + ".table", "a column attribute needs a table",
                   ATTRIBUTE_BINDING_AMBIGUOUS);
            }
            else if(blank(attribute.column())) {
               add(path + ".column", "a column attribute needs a column",
                   ATTRIBUTE_BINDING_AMBIGUOUS);
            }
            else {
               String physical = checkColumn(attribute, path);

               if(blank(dataType) && physical != null) {
                  dataType = physical;
                  warnings.add(path + ".dataType: filled from the physical column as \"" +
                                  physical + "\"");
               }
            }
         }

         if(!blank(dataType) && !XSchema.isPrimitiveType(dataType)) {
            add(path + ".dataType", "dataType \"" + dataType + "\" is not a data type name",
                DATATYPE_INVALID);
         }

         if(attribute.refType() != null &&
            !WizLogicalModelWire.REF_TYPES.contains(attribute.refType()))
         {
            add(path + ".refType", "refType \"" + attribute.refType() + "\" must be one of " +
               WizLogicalModelWire.REF_TYPES, REFTYPE_INVALID);
         }

         Drill drill = checkDrill(attribute.drill(), path + ".drill", storedAttribute);

         return new Attribute(attribute.name(), attribute.oldName(), attribute.description(),
                              attribute.visible(), attribute.type(), attribute.table(),
                              attribute.column(), attribute.expression(), attribute.aggregate(),
                              attribute.parseable(), dataType, attribute.refType(),
                              attribute.formula(), attribute.browseData(),
                              attribute.browseQuery(), attribute.format(), drill,
                              attribute.baseElement(), attribute.errorMessage());
      }

      /**
       * The table/column rule of {@code LogicalModelService.validateAttribute}, with a loud
       * answer where the service only logs. Returns the physical column's data type when found.
       */
      private String checkColumn(Attribute attribute, String path) {
         if(partition == null) {
            return null;
         }

         String table = attribute.table();
         String column = attribute.column();
         XPartition aliased = partition.applyAutoAliases();
         List<String> replacements = partition.getAutoAliasReplacements(aliased, table);

         if(!replacements.isEmpty()) {
            add(path + ".table", "table \"" + table + "\" is no longer in view \"" +
               partition.getName() + "\": an Auto Alias replaced it with \"" +
               String.join("\", \"", replacements) + "\". Bind the attribute to one of those " +
               "tables or remove the Auto Alias", TABLE_NOT_IN_VIEW);
            return null;
         }

         if(!aliased.containsTable(table)) {
            add(path + ".table", "table \"" + table + "\" is not in view \"" + partition.getName() +
               "\"", TABLE_NOT_IN_VIEW);
            return null;
         }

         String tableName = table;

         if(aliased.isAlias(tableName)) {
            tableName = aliased.getAliasTable(tableName);
         }

         XPartition.PartitionTable xTable = aliased.getPartitionTable(table);

         if(xTable == null) {
            xTable = aliased.getPartitionTable(aliased.getAutoAliasTable(table));
         }

         if(xTable != null && xTable.getType() == 1 && xTable.getSql() != null) {
            for(inetsoft.uql.schema.XTypeNode node : xTable.getColumns()) {
               if(Tool.equals(node.getName(), column)) {
                  return node.getType();
               }
            }

            add(path + ".column", "column \"" + column + "\" is not in table \"" + tableName +
               "\"", COLUMN_NOT_IN_TABLE);
            return null;
         }

         Map<String, Map<String, String>> tables = metadata();

         if(tables == null) {
            return null;
         }

         Map<String, String> columns = tables.get(table);

         if(columns == null) {
            add(path + ".table", "table \"" + tableName + "\" is not in the database, or cannot be " +
                "resolved in the database's default schema", TABLE_NOT_IN_VIEW);
            return null;
         }

         if(!columns.containsKey(column)) {
            add(path + ".column", "column \"" + column + "\" is not in table \"" + tableName +
               "\"", COLUMN_NOT_IN_TABLE);
            return null;
         }

         return columns.get(column);
      }

      /** Reads the view's metadata once; one error, not one per attribute, when it fails. */
      private Map<String, Map<String, String>> metadata() {
         if(!metadataRead) {
            metadataRead = true;

            try {
               metadata = env.metadata(partition, blank(def.connection()) ? null :
                  def.connection());
            }
            catch(Exception e) {
               add("view", "the database metadata of view \"" + partition.getName() +
                  "\" could not be read, so the attribute columns cannot be verified: " +
                  e.getMessage(), METADATA_UNAVAILABLE);
            }
         }

         return metadata;
      }

      private Drill checkDrill(Drill drill, String path, XAttribute storedAttribute) {
         if(drill == null || drill.paths() == null) {
            return drill;
         }

         List<WizLogicalModelWire.DrillPath> resolved = new ArrayList<>();

         for(int i = 0; i < drill.paths().size(); i++) {
            WizLogicalModelWire.DrillPath drillPath = drill.paths().get(i);
            String ppath = path + ".paths[" + (blank(drillPath.name()) ? i : drillPath.name()) +
               "]";

            if(blank(drillPath.name())) {
               add(ppath + ".name", "a drill path needs a name", DRILL_INVALID);
            }

            if(drillPath.linkType() != null &&
               !WizLogicalModelWire.LINK_WEB.equals(drillPath.linkType()) &&
               !WizLogicalModelWire.LINK_VIEWSHEET.equals(drillPath.linkType()))
            {
               add(ppath + ".linkType", "linkType \"" + drillPath.linkType() +
                  "\" must be \"web\" or \"viewsheet\"", DRILL_INVALID);
            }

            if(drillPath.params() != null) {
               for(DrillParam param : drillPath.params()) {
                  if(blank(param.name())) {
                     add(ppath + ".params", "a drill parameter needs a name", DRILL_INVALID);
                  }
               }
            }

            if(drillPath.queryFields() != null && !drillPath.queryFields().isEmpty()) {
               add(ppath + ".queryFields", "queryFields are not stored by StyleBI; leave empty",
                   DRILL_INVALID);
            }

            resolved.add(resolveQuery(resolveViewsheetLink(drillPath, ppath, storedAttribute),
                                      ppath, storedAttribute));
         }

         return new Drill(resolved);
      }

      private static final String QUERY_ENTRY_FORMS =
         "give the worksheet as its asset identifier, as a ws:global:<path> or " +
         "ws:<user>:<path> string, or as the {\"path\", \"scope\"} object the worksheet " +
         "list returns as attach";

      /**
       * A drill's worksheet query is stored by the asset identifier of the worksheet, as the
       * Portal stores the one picked from the worksheet tree: a runQuery string or an attach
       * object is resolved to it, so what is written is what is stored, and a worksheet that does
       * not exist (or the caller cannot read) is refused. A stored query left as it is is not
       * looked at again, so a model whose worksheet was deleted later can still be edited
       * elsewhere.
       */
      private WizLogicalModelWire.DrillPath resolveQuery(
         WizLogicalModelWire.DrillPath drillPath, String ppath, XAttribute storedAttribute)
      {
         DrillQuery query = drillPath.query();

         if(query == null) {
            return drillPath;
         }

         String field = ppath + ".query.entry";
         AssetEntry entry = queryEntry(query, field);

         if(entry == null) {
            return drillPath;
         }

         String identifier = entry.toIdentifier();

         if(!storedQueryEntry(storedAttribute, drillPath, identifier) &&
            !env.worksheetReadable(entry))
         {
            add(field, "the worksheet \"" + entry.getPath() + "\" does not exist or cannot be " +
               "read; a drill query must name an existing worksheet (" + QUERY_ENTRY_FORMS + ")",
               DRILL_INVALID);
            return drillPath;
         }

         return new WizLogicalModelWire.DrillPath(
            drillPath.name(), drillPath.linkType(), drillPath.link(), drillPath.targetFrame(),
            drillPath.tip(), drillPath.passParams(), drillPath.disablePrompting(),
            drillPath.params(), new DrillQuery(identifier, null, query.params()),
            drillPath.queryFields());
      }

      /** The worksheet entry the query names, {@code null} (and an error added) when none. */
      private AssetEntry queryEntry(DrillQuery query, String field) {
         AssetEntry entry;

         if(query.attach() != null) {
            entry = attachEntry(query.attach(), field);

            if(entry == null) {
               return null;
            }
         }
         else if(blank(query.entry())) {
            add(field, "the worksheet of a drill query is needed (" + QUERY_ENTRY_FORMS + ")",
                DRILL_INVALID);
            return null;
         }
         else {
            try {
               entry = AssetEntry.createAssetEntry(query.entry());
            }
            catch(RuntimeException e) {
               entry = null;
            }

            if(entry == null) {
               add(field, "\"" + query.entry() + "\" is not a worksheet entry (" +
                  QUERY_ENTRY_FORMS + ")", DRILL_INVALID);
               return null;
            }
         }

         if(entry.getType() != AssetEntry.Type.WORKSHEET) {
            add(field, "the entry \"" + entry.getPath() + "\" is not a worksheet (" +
               QUERY_ENTRY_FORMS + ")", DRILL_INVALID);
            return null;
         }

         if(entry.getScope() != AssetRepository.GLOBAL_SCOPE &&
            entry.getScope() != AssetRepository.USER_SCOPE)
         {
            add(field, "the worksheet must be in the global or the user scope (" +
               QUERY_ENTRY_FORMS + ")", DRILL_INVALID);
            return null;
         }

         if(!blank(entry.getProperty("table.name"))) {
            add(field, "a table of the worksheet cannot be stored in a drill query; name the " +
               "worksheet without the :" + entry.getProperty("table.name") + " table segment",
               DRILL_INVALID);
            return null;
         }

         return entry;
      }

      private AssetEntry attachEntry(WizLogicalModelWire.WorksheetAttach attach, String field) {
         String scope = attach.scope() == null ? "" : attach.scope().trim().toLowerCase();

         if(scope.equals("global")) {
            return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                  attach.path(), null);
         }

         if(scope.equals("user")) {
            IdentityID caller = env.caller();

            if(caller == null) {
               add(field, "a user-scope worksheet cannot be resolved without the caller (" +
                  QUERY_ENTRY_FORMS + ")", DRILL_INVALID);
               return null;
            }

            return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                  attach.path(), caller);
         }

         add(field, "the scope \"" + attach.scope() + "\" must be \"global\" or \"user\"",
             DRILL_INVALID);
         return null;
      }

      /** Whether the stored attribute already has this drill path with this worksheet. */
      private boolean storedQueryEntry(XAttribute storedAttribute,
                                       WizLogicalModelWire.DrillPath drillPath, String identifier)
      {
         if(mode != Mode.UPDATE || storedAttribute == null ||
            storedAttribute.getXMetaInfo() == null ||
            storedAttribute.getXMetaInfo().getXDrillInfo() == null)
         {
            return false;
         }

         Enumeration<?> paths = storedAttribute.getXMetaInfo().getXDrillInfo().getDrillPaths();

         while(paths.hasMoreElements()) {
            DrillPath stored = (DrillPath) paths.nextElement();

            if(Tool.equals(stored.getName(), drillPath.name()) && stored.getQuery() != null &&
               stored.getQuery().getWsEntry() != null &&
               identifier.equals(stored.getQuery().getWsEntry().toIdentifier()))
            {
               return true;
            }
         }

         return false;
      }

      /**
       * A viewsheet drill is stored by the asset identifier of its target, as the Portal stores
       * the one picked from the repository tree: a repository path is resolved to it, and a
       * target that does not exist (or the caller cannot read) is refused. A stored drill path
       * left as it is is not looked at again, so a model whose target was deleted later can still
       * be edited elsewhere.
       */
      private WizLogicalModelWire.DrillPath resolveViewsheetLink(
         WizLogicalModelWire.DrillPath drillPath, String ppath, XAttribute storedAttribute)
      {
         if(!WizLogicalModelWire.LINK_VIEWSHEET.equals(drillPath.linkType()) ||
            blank(drillPath.link()) || storedViewsheetLink(storedAttribute, drillPath))
         {
            return drillPath;
         }

         String identifier = env.resolveViewsheet(drillPath.link());

         if(identifier == null) {
            add(ppath + ".link", "the viewsheet \"" + drillPath.link() + "\" does not exist or " +
               "cannot be read; a viewsheet drill must target an existing viewsheet (give its " +
               "repository path or its asset identifier)", DRILL_INVALID);
            return drillPath;
         }

         return new WizLogicalModelWire.DrillPath(
            drillPath.name(), drillPath.linkType(), identifier, drillPath.targetFrame(),
            drillPath.tip(), drillPath.passParams(), drillPath.disablePrompting(),
            drillPath.params(), drillPath.query(), drillPath.queryFields());
      }

      /** Whether the stored attribute already has this viewsheet drill path with this link. */
      private boolean storedViewsheetLink(XAttribute storedAttribute,
                                          WizLogicalModelWire.DrillPath drillPath)
      {
         if(mode != Mode.UPDATE || storedAttribute == null ||
            storedAttribute.getXMetaInfo() == null ||
            storedAttribute.getXMetaInfo().getXDrillInfo() == null)
         {
            return false;
         }

         Enumeration<?> paths = storedAttribute.getXMetaInfo().getXDrillInfo().getDrillPaths();

         while(paths.hasMoreElements()) {
            DrillPath stored = (DrillPath) paths.nextElement();

            if(stored.getLinkType() == DrillPath.VIEWSHEET_LINK &&
               Tool.equals(stored.getName(), drillPath.name()) &&
               Tool.equals(stored.getLink(), drillPath.link()))
            {
               return true;
            }
         }

         return false;
      }

      // -- rename markers ----------------------------------------------------------------------

      private record Item(int index, String name, String raw) {
      }

      /**
       * Checks the markers of one level and returns the stored names the submitted elements
       * claim (by identity or by an explicit marker). The rules: a marker must name a stored
       * element, claimed once; an element explicitly sent as new must not hide a stored one of
       * its own name. On a create no marker means anything.
       */
      private Set<String> markers(String label, List<Item> items, Set<String> storedNames,
                                  java.util.function.Function<Item, String> path,
                                  boolean entityLevel)
      {
         Set<String> claims = new HashSet<>();
         List<Item> explicitNew = new ArrayList<>();

         for(Item item : items) {
            String raw = item.raw();

            if(mode == Mode.CREATE) {
               if(raw != null && !raw.isEmpty() && !raw.equals(item.name())) {
                  add(path.apply(item) + ".oldName", "oldName \"" + raw + "\": a create has no " +
                     "stored " + label + " to rename", OLDNAME_UNKNOWN);
               }

               continue;
            }

            if(raw == null) {
               if(item.name() != null && storedNames.contains(item.name()) &&
                  !claims.add(item.name()))
               {
                  add(path.apply(item) + ".oldName", label + " \"" + item.name() +
                     "\" is claimed twice", OLDNAME_UNKNOWN);
               }
            }
            else if(raw.isEmpty()) {
               explicitNew.add(item);
            }
            else if(!storedNames.contains(raw)) {
               add(path.apply(item) + ".oldName", "no " + label + " \"" + raw +
                  "\" in the stored model" + (entityLevel ? "" : " entity"), OLDNAME_UNKNOWN);
            }
            else if(!claims.add(raw)) {
               add(path.apply(item) + ".oldName", label + " \"" + raw + "\" is claimed twice",
                   OLDNAME_UNKNOWN);
            }
         }

         for(Item item : explicitNew) {
            if(item.name() != null && storedNames.contains(item.name()) &&
               !claims.contains(item.name()))
            {
               add(path.apply(item) + ".oldName", label + " \"" + item.name() + "\" exists in " +
                  "the stored model; send oldName:\"" + item.name() + "\" to keep it (its " +
                  "dependents migrate), or rename or remove it first", OLDNAME_REQUIRED);
            }
         }

         return claims;
      }

      private void lookAlikeAttributes(String path, List<Attribute> attributes,
                                       XEntity storedEntity, Set<String> storedNames,
                                       Set<String> claims)
      {
         if(mode != Mode.UPDATE || storedEntity == null) {
            return;
         }

         for(Attribute attribute : attributes) {
            if(attribute.oldName() != null || blank(attribute.name()) ||
               storedNames.contains(attribute.name()))
            {
               continue;
            }

            for(String removed : storedNames) {
               if(claims.contains(removed)) {
                  continue;
               }

               XAttribute old = storedEntity.getAttribute(removed);

               if(old != null && sameBinding(old, attribute)) {
                  add(path + ".attributes[" + attribute.name() + "].oldName",
                      "looks like a rename of \"" + removed + "\"; give oldName:\"" + removed +
                         "\" to migrate dependents, or send oldName:null to say it is a new " +
                         "element and \"" + removed + "\" is deleted", OLDNAME_REQUIRED);
                  break;
               }
            }
         }
      }

      private boolean sameBinding(XAttribute old, Attribute attribute) {
         if(old instanceof ExpressionAttribute) {
            return !blank(attribute.expression()) &&
               Tool.equals(((ExpressionAttribute) old).getExpression(), attribute.expression());
         }

         return !blank(attribute.column()) && Tool.equals(old.getTable(), attribute.table()) &&
            Tool.equals(old.getColumn(), attribute.column());
      }

      private void lookAlikeEntities(List<Entity> entities, Set<String> storedNames,
                                     Set<String> claims)
      {
         if(mode != Mode.UPDATE || stored == null) {
            return;
         }

         for(Entity entity : entities) {
            if(entity.oldName() != null || blank(entity.name()) ||
               storedNames.contains(entity.name()))
            {
               continue;
            }

            Set<String> attributeNames = new HashSet<>();

            for(Attribute attribute : entity.attributes() == null ? List.<Attribute>of() :
               entity.attributes())
            {
               attributeNames.add(attribute.name());
            }

            if(attributeNames.isEmpty()) {
               continue;
            }

            for(String removed : storedNames) {
               if(!claims.contains(removed) &&
                  attributeNames.equals(storedAttributeNames(stored.getEntity(removed))))
               {
                  add("entities[" + entity.name() + "].oldName",
                      "looks like a rename of entity \"" + removed + "\"; give oldName:\"" +
                         removed + "\" to migrate dependents, or send oldName:null to say it " +
                         "is a new element and \"" + removed + "\" is deleted", OLDNAME_REQUIRED);
                  break;
               }
            }
         }
      }

      /** The stored entity an update entity refers to: its marker, else its own name. */
      private String storedEntityName(Entity entity) {
         if(stored == null || mode != Mode.UPDATE) {
            return null;
         }

         String raw = entity.oldName();

         if(raw == null) {
            return stored.getEntity(entity.name()) == null ? null : entity.name();
         }

         return raw.isEmpty() || stored.getEntity(raw) == null ? null : raw;
      }

      // -- stored state the wire cannot carry --------------------------------------------------

      /**
       * An update rebuilds every attribute from the wire, so a stored value the wire cannot carry
       * would be deleted without a word. Refuse naming it (an update that left it alone would be
       * indistinguishable from one that deleted it).
       */
      private void checkStoredRepresentable() {
         Enumeration<XEntity> entities = stored.getEntities();

         while(entities.hasMoreElements()) {
            XEntity entity = entities.nextElement();
            Enumeration<XAttribute> attributes = entity.getAttributes();

            while(attributes.hasMoreElements()) {
               XAttribute attribute = attributes.nextElement();

               if(entity.isBaseAttribute(attribute.getName())) {
                  continue;
               }

               XMetaInfo meta = attribute.getXMetaInfo();
               String at = "entities[" + entity.getName() + "].attributes[" +
                  attribute.getName() + "]";

               if(meta == null) {
                  continue;
               }

               if(meta.isAsDate() || !Tool.isEmptyString(meta.getDatePattern()) ||
                  meta.getLocale() != null)
               {
                  add(at, "the stored attribute has a date or locale setting (asDate, " +
                     "datePattern or locale) that this tool cannot carry, so an update would " +
                     "delete it; edit this model in the Data tab", STORED_UNSUPPORTED);
               }

               if(meta.getXDrillInfo() != null) {
                  Enumeration<?> paths = meta.getXDrillInfo().getDrillPaths();

                  while(paths.hasMoreElements()) {
                     DrillPath path = (DrillPath) paths.nextElement();

                     if(path.getLinkType() != DrillPath.WEB_LINK &&
                        path.getLinkType() != DrillPath.VIEWSHEET_LINK)
                     {
                        add(at + ".drill", "the stored drill path \"" + path.getName() +
                           "\" is of a link type this tool cannot carry, so an update would " +
                           "delete it; edit this model in the Data tab", STORED_UNSUPPORTED);
                     }
                  }
               }
            }
         }
      }

      // -- helpers -----------------------------------------------------------------------------

      private static Set<String> storedEntityNames(XLogicalModel model) {
         Set<String> names = new LinkedHashSet<>();
         Enumeration<XEntity> entities = model.getEntities();

         while(entities.hasMoreElements()) {
            names.add(entities.nextElement().getName());
         }

         return names;
      }

      private static Set<String> storedAttributeNames(XEntity entity) {
         Set<String> names = new LinkedHashSet<>();

         if(entity != null) {
            Enumeration<XAttribute> attributes = entity.getAttributes();

            while(attributes.hasMoreElements()) {
               names.add(attributes.nextElement().getName());
            }
         }

         return names;
      }

      private static boolean blank(String value) {
         return value == null || value.isBlank();
      }

      private void add(String field, String message, String code) {
         if(errors.size() < MAX_ERRORS) {
            errors.add(new Err(field, message, code));
         }
      }

      private final Extended ext;
      private final Env env;
      private final Definition def;
      private final Mode mode;
      private final XLogicalModel stored;
      private XPartition partition;
      private Map<String, Map<String, String>> metadata;
      private boolean metadataRead;
      private List<Entity> normalizedEntities;
      private final List<Err> errors = new ArrayList<>();
      private final List<String> warnings = new ArrayList<>();
   }
}
