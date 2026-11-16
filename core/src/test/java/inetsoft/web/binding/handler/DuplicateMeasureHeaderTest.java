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
package inetsoft.web.binding.handler;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81482: a crosstab that binds one column twice on the aggregates shelf. The crosstab lens
 * renders the later measure under the name suffixed {@code ".N"} ({@code Util.getDupHeader}), so
 * resolving a header by the measure's bare name found nothing for the second measure (rename:
 * "Could not find 'aggregates[1]'") or, for two plain measures, found the first measure's cells
 * and wrote the label there.
 *
 * <p>The crosstabs are rendered for real from an in-memory table, so the header paths asserted
 * here are the ones the lens produces, not ones the test made up.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DuplicateMeasureHeaderTest {
   private static final String SUM = "Sum(wind)";
   private static final String CHANGE = "Change from previous year of Year(D): Sum(wind)";

   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();
   private ViewsheetSandbox box;
   private Viewsheet vs;
   private SourceInfo source;

   @BeforeEach
   void setUp() throws Exception {
      fixture.setUp();
      List<Object[]> rows = new ArrayList<>();

      for(int year = 0; year < 2; year++) {
         for(int month = 0; month < 3; month++) {
            rows.add(new Object[] { java.sql.Date.valueOf(LocalDate.of(2024 + year, month + 1, 1)),
                                    100 + year * 6 + month });
         }
      }

      DcInMemoryViewsheetFixture.Sheet sheet = fixture.open("D", "wind", rows);
      box = sheet.box();
      vs = sheet.vs();
      source = sheet.source();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   /** The rendered crosstab and the shelf the binding service resolves its refs from. */
   private record Rendered(VSTableLens lens, List<DataRef> aggregates) {}

   private static VSAggregateRef aggregate(String formula, boolean change) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue(formula);

      if(change) {
         ChangeCalc calc = new ChangeCalc();
         calc.setFrom(ValueOfCalc.PREVIOUS_YEAR);
         calc.setColumnName("Year(D)");
         agg.setCalculator(calc);
      }

      return agg;
   }

   private static VSDimensionRef dateDim(int level) {
      VSDimensionRef dim = new VSDimensionRef();
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      dim.setDataRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   private Rendered render(String name, boolean sideBySide, VSAggregateRef... aggregates)
      throws Exception
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo((SourceInfo) source.clone());
      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      info.setDesignRowHeaders(new DataRef[] { dateDim(XConstants.QUARTER_DATE_GROUP) });
      info.setDesignColHeaders(new DataRef[] { dateDim(XConstants.YEAR_DATE_GROUP) });
      info.setDesignAggregates(aggregates);
      info.setSummarySideBySide(sideBySide);
      vs.addAssembly(crosstab);
      TableLens data = (TableLens) box.getData(name);
      assertNotNull(data);
      data.moreRows(Integer.MAX_VALUE);
      VSTableLens lens = box.getVSTableLens(name, false);
      lens.moreRows(Integer.MAX_VALUE);
      return new Rendered(lens, Arrays.asList(info.getAggregates()));
   }

   private static List<String> lastSegments(List<TableDataPath> paths) {
      return paths.stream().map(p -> p.getPath()[p.getPath().length - 1]).distinct()
         .collect(Collectors.toList());
   }

   private static List<TableDataPath> paths(Rendered r, int index) {
      return SetTableHeaderAliasHandler.findHeaderPaths(
         r.lens(), r.aggregates().get(index), index,
         SetTableHeaderAliasHandler.occurrence(r.aggregates(), index));
   }

   private static List<Integer> columns(Rendered r, int index) {
      return SetTableHeaderAliasHandler.findHeaderCells(
         r.lens(), r.aggregates().get(index), index,
         SetTableHeaderAliasHandler.occurrence(r.aggregates(), index))
         .stream().map(SetTableHeaderAliasHandler.HeaderCell::col).collect(Collectors.toList());
   }

   @Test
   void twoPlainMeasuresEachResolveToTheirOwnHeader() throws Exception {
      Rendered r = render("Plain", false, aggregate("Sum", false), aggregate("Sum", false));

      assertEquals(List.of(SUM, SUM), r.aggregates().stream()
         .map(a -> ((VSAggregateRef) a).getFullName()).collect(Collectors.toList()));
      assertEquals(0, SetTableHeaderAliasHandler.occurrence(r.aggregates(), 0));
      assertEquals(1, SetTableHeaderAliasHandler.occurrence(r.aggregates(), 1));
      // the second measure's header is the suffixed name, and it is not the first one's
      assertEquals(List.of(SUM), lastSegments(paths(r, 0)));
      assertEquals(List.of(SUM + ".1"), lastSegments(paths(r, 1)));
      assertTrue(Collections.disjoint(paths(r, 0), paths(r, 1)));
   }

   @Test
   void aMeasureWithACalculatorAfterAPlainOneResolvesToTheSuffixedName() throws Exception {
      Rendered r = render("PlainThenCalc", false, aggregate("Sum", false),
                          aggregate("Sum", true));

      assertEquals(CHANGE, ((VSAggregateRef) r.aggregates().get(1)).getFullName());
      // the name is its own, so it is occurrence 0 of that name, yet it renders suffixed
      assertEquals(0, SetTableHeaderAliasHandler.occurrence(r.aggregates(), 1));
      assertEquals(List.of(SUM), lastSegments(paths(r, 0)));
      assertEquals(List.of(CHANGE + ".1"), lastSegments(paths(r, 1)));
   }

   @Test
   void aPlainMeasureAfterACalculatorMeasureResolvesToTheSuffixedName() throws Exception {
      Rendered r = render("CalcThenPlain", false, aggregate("Sum", true),
                          aggregate("Sum", false));

      assertEquals(List.of(CHANGE), lastSegments(paths(r, 0)));
      assertEquals(List.of(SUM + ".1"), lastSegments(paths(r, 1)));
   }

   @Test
   void measuresOfDifferentFormulasStillResolveByTheirOwnNames() throws Exception {
      Rendered r = render("Different", false, aggregate("Sum", false),
                          aggregate("Average", true));

      assertEquals(List.of(SUM), lastSegments(paths(r, 0)));
      assertEquals(List.of("Change from previous year of Year(D): Average(wind)"),
                   lastSegments(paths(r, 1)));
   }

   @Test
   void sideBySideMeasuresResolveToTheirOwnColumns() throws Exception {
      Rendered r = render("SideBySide", true, aggregate("Sum", false), aggregate("Sum", false));

      // one measure column per year, then the grand total: the two measures interleave
      List<Integer> first = columns(r, 0);
      List<Integer> second = columns(r, 1);
      assertFalse(first.isEmpty());
      assertEquals(first.size(), second.size());
      assertTrue(Collections.disjoint(first, second), first + " vs " + second);
      assertEquals(List.of(SUM + ".1"), lastSegments(paths(r, 1)));
   }

   @Test
   void anOccurrenceThatDoesNotRenderResolvesToNothing() throws Exception {
      Rendered r = render("Missing", false, aggregate("Sum", false), aggregate("Sum", false));

      // never falls back to the first measure's header
      assertTrue(SetTableHeaderAliasHandler.findHeaderPaths(
         r.lens(), r.aggregates().get(0), 0, 2).isEmpty());
      assertNull(SetTableHeaderAliasHandler.findHeaderCell(
         r.lens(), r.aggregates().get(0), 0, 2));
   }

   @Test
   void aliasWrittenAtOneMeasureLeavesItsTwinAlone() throws Exception {
      Rendered r = render("Alias", false, aggregate("Sum", false), aggregate("Sum", false));
      FormatInfo formatInfo = new FormatInfo();

      for(TableDataPath path : paths(r, 1)) {
         SetTableHeaderAliasHandler.setAlias(path, formatInfo, "Second");
      }

      for(TableDataPath path : paths(r, 0)) {
         assertNull(formatInfo.getFormat(path), "the first measure must not carry the label");
      }

      for(TableDataPath path : paths(r, 1)) {
         assertEquals("Second", formatInfo.getFormat(path).getUserDefinedFormat()
            .getFormatExtentValue());
      }

      ClearTableHeaderAliasHandler.clearAliasAt(paths(r, 1), formatInfo);

      for(TableDataPath path : paths(r, 1)) {
         assertNull(formatInfo.getFormat(path).getUserDefinedFormat().getFormatValue());
      }
   }

   @Test
   void aggregateSegmentRecognizesTheNameAndItsDuplicateSuffixOnly() {
      assertTrue(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, SUM));
      assertTrue(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, SUM + ".1"));
      assertTrue(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, SUM + ".12"));
      assertFalse(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, SUM + ".x"));
      assertFalse(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, SUM + "1"));
      assertFalse(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, CHANGE));
      assertFalse(ClearTableHeaderAliasHandler.isAggregateSegment(SUM, null));
   }
}
