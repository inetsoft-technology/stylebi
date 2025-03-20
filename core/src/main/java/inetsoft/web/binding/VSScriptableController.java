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
package inetsoft.web.binding;

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.analytic.web.adhoc.AdHocQueryHandler;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.web.binding.handler.VSTreeHandler;
import inetsoft.web.binding.model.ScriptPaneTreeModel;
import inetsoft.web.binding.model.ScriptTreeNodeData;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.analytic.web.adhoc.AdHocQueryHandler.DOT_FLAG;

/**
 * GetVSScriptableEvent.
 */
@RestController
public class VSScriptableController {
   public static final String[] CHART_CONSTANTS = new String[] {
      "MAP_TYPE_ASIA", "MAP_TYPE_CANADA", "MAP_TYPE_EUROPE",
      "MAP_TYPE_MEXICO", "MAP_TYPE_U.S.", "MAP_TYPE_WORLD", "CHART_AUTO",
      "CHART_BAR", "CHART_BAR_STACK", "CHART_3D_BAR", "CHART_3D_BAR_STACK",
      "CHART_PIE", "CHART_DONUT", "CHART_SUNBURST",
      "CHART_TREEMAP", "CHART_CIRCLE_PACKING", "CHART_ICICLE",
      "CHART_3D_PIE", "CHART_LINE", "CHART_LINE_STACK",
      "CHART_AREA", "CHART_AREA_STACK", "CHART_STOCK", "CHART_POINT",
      "CHART_POINT_STACK",
      "CHART_RADAR", "CHART_FILL_RADAR", "CHART_CANDLE", "CHART_BOXPLOT", "CHART_WATERFALL",
      "CHART_MEKKO", "CHART_STEP", "CHART_STEP_STACK",
      "CHART_JUMP", "CHART_STEP_AREA", "CHART_STEP_AREA_STACK",
      "CHART_PARETO", "CHART_MAP", "CHART_POLYGON", "BAR_MAX_COUNT",
      "BAR_3D_MAX_COUNT", "LINE_MAX_COUNT", "POINT_MAX_COUNT",
      "AREA_MAX_COUNT", "PIE_MAX_COUNT", "PIE_3D_MAX_COUNT",
      "RADAR_MAX_COUNT", "CANDLE_MAX_COUNT", "STOCK_MAX_COUNT",
      "PARETO_MAX_COUNT", "WATERFALL_MAX_COUNT", "SOLID_MASK",
      "DASH_MASK", "WIDTH_MASK", "NONE", "ULTRA_THIN_LINE",
      "THIN_THIN_LINE", "THIN_LINE", "MEDIUM_LINE", "THICK_LINE",
      "DOT_LINE", "DASH_LINE", "MEDIUM_DASH", "LARGE_DASH",
      "TOP", "RIGHT", "BOTTOM", "LEFT", "CENTER", "TOP_ALIGNMENT",
      "MIDDLE_ALIGNMENT", "BOTTOM_ALIGNMENT", "LEFT_ALIGNMENT",
      "CENTER_ALIGNMENT", "RIGHT_ALIGNMENT", "AUTO", "IN_PLACE",
      "YEAR_INTERVAL", "QUARTER_INTERVAL", "MONTH_INTERVAL",
      "WEEK_INTERVAL", "DAY_INTERVAL", "HOUR_INTERVAL", "MINUTE_INTERVAL",
      "SECOND_INTERVAL", "DIMENSION", "MEASURE", "TIME",
      "QUARTER_OF_YEAR_PART", "MONTH_OF_YEAR_PART", "WEEK_OF_YEAR_PART",
      "DAY_OF_MONTH_PART", "DAY_OF_WEEK_PART", "HOUR_OF_DAY_PART",
      "SORT_ASC", "SORT_DESC", "SORT_NONE", "SORT_ORIGINAL",
      "SORT_SPECIFIC", "SORT_VALUE_ASC", "SORT_VALUE_DESC",
      "NONE_DATE_GROUP", "YEAR_DATE_GROUP", "QUARTER_DATE_GROUP",
      "MONTH_DATE_GROUP", "WEEK_DATE_GROUP", "DAY_DATE_GROUP",
      "AM_PM_DATE_GROUP", "HOUR_DATE_GROUP", "MINUTE_DATE_GROUP",
      "SECOND_DATE_GROUP", "MILLISECOND_DATE_GROUP", "PART_DATE_GROUP",
      "QUARTER_OF_YEAR_DATE_GROUP", "MONTH_OF_YEAR_DATE_GROUP",
      "WEEK_OF_YEAR_DATE_GROUP", "WEEK_OF_MONTH_DATE_GROUP",
      "DAY_OF_YEAR_DATE_GROUP", "DAY_OF_MONTH_DATE_GROUP",
      "DAY_OF_WEEK_DATE_GROUP", "AM_PM_OF_DAY_DATE_GROUP",
      "HOUR_OF_DAY_DATE_GROUP", "NONE_FORMULA", "AVERAGE_FORMULA",
      "COUNT_FORMULA", "DISTINCTCOUNT_FORMULA", "MAX_FORMULA",
      "MIN_FORMULA", "PRODUCT_FORMULA", "SUM_FORMULA", "SET_FORMULA",
      "CONCAT_FORMULA", "STANDARDDEVIATION_FORMULA", "VARIANCE_FORMULA",
      "POPULATIONSTANDARDDEVIATION_FORMULA", "POPULATIONVARIANCE_FORMULA",
      "CORRELATION_FORMULA", "COVARIANCE_FORMULA", "MEDIAN_FORMULA",
      "MODE_FORMULA", "NTHLARGEST_FORMULA", "NTHMOSTFREQUENT_FORMULA",
      "NTHSMALLEST_FORMULA", "PTHPERCENTILE_FORMULA",
      "WEIGHTEDAVERAGE_FORMULA", "SUMWT_FORMULA", "SUMSQ_FORMULA",
      "FIRST_FORMULA", "LAST_FORMULA",
      "DATE_FORMAT", "DECIMAL_FORMAT", "PERCENT_FORMAT", "MESSAGE_FORMAT",
      "PERCENTAGE_NONE", "PERCENTAGE_OF_GROUP", "PERCENTAGE_OF_GRANDTOTAL",
      "PERCENTAGE_BY_COL", "PERCENTAGE_BY_ROW", "COLUMN", "LINEAR",
      "QUADRATIC", "CUBIC", "EXPONENTIAL", "LOGARITHMIC", "POWER",
      "AESTHETIC_COLOR", "AESTHETIC_SHAPE", "AESTHETIC_SIZE",
      "AESTHETIC_TEXT", "BINDING_FIELD", "HIGH", "LOW", "CLOSE",
      "OPEN", "STRING", "NUMBER", "DATE", "COUNTRY", "STATE", "PROVINCE",
      "CITY", "ZIP", "TEXTURE_STYLES", "M_LINE_STYLES", "TRENDLINE_TYPES"};
   public static final String[] CHART_CLASSES = new String[] {
      "EGraph", "LegendSpec", "TitleSpec", "TextSpec", "AxisSpec",
      "PlotSpec", "IntervalElement", "LineElement",
      "SchemaElement", "PointElement", "AreaElement", "PolarCoord",
      "RectCoord", "Rect25Coord", "ParallelCoord", "TriCoord",
      "FacetCoord", "LinearScale", "LogScale",
      "PowerScale", "TimeScale", "CategoricalScale", "LinearRange",
      "StackRange", "MultiTextFrame", "PieShapeFrame",
      "BrightnessColorFrame",
      "SaturationColorFrame", "BipolarColorFrame",
      "StaticColorFrame", "CircularColorFrame", "GradientColorFrame",
      "HeatColorFrame", "RainbowColorFrame", "CategoricalColorFrame",
      "StaticSizeFrame", "LinearSizeFrame", "CategoricalSizeFrame",
      "StaticTextureFrame", "LeftTiltTextureFrame",
      "RGBCubeColorFrame", "StackTextFrame", "SVGShape",
      "DefaultDataSet", "BoxPainter", "CandlePainter",
      "StockPainter",
      "VLabel",
      "DefaultForm", "ExponentialLineEquation", "LineEquation",
      "LogarithmicLineEquation", "PolynomialLineEquation.Linear",
      "PolynomialLineEquation.Quadratic", "PolynomialLineEquation.Cubic",
      "PowerLineEquation", "OrientationTextureFrame",
      "RightTiltTextureFrame", "GridTextureFrame", "CategoricalTextureFrame",
      "OvalShapeFrame", "FillShapeFrame", "OrientationShapeFrame",
      "PolygonShapeFrame", "TriangleShapeFrame", "CategoricalShapeFrame",
      "StaticShapeFrame", "VineShapeFrame", "ThermoShapeFrame",
      "StarShapeFrame", "SunShapeFrame", "BarShapeFrame", "ProfileShapeFrame",
      "DefaultTextFrame", "StaticLineFrame", "LinearLineFrame",
      "CategoricalLineFrame", "LineForm", "RectForm", "LabelForm",
      "TagForm", "ShapeForm", "GTexture", "GLine", "GShape.ImageShape",
      "BluesColorFrame", "BrBGColorFrame", "BuGnColorFrame", "BuPuColorFrame",
      "GnBuColorFrame", "GreensColorFrame", "GreysColorFrame", "OrangesColorFrame",
      "OrRdColorFrame", "PiYGColorFrame", "PRGnColorFrame", "PuBuColorFrame",
      "PuBuGnColorFrame", "PuOrColorFrame", "PuRdColorFrame", "PurplesColorFrame",
      "RdBuColorFrame", "RdGyColorFrame", "RdPuColorFrame", "RdYlGnColorFrame",
      "RedsColorFrame", "SpectralColorFrame", "RdYlBuColorFrame", "YlGnBuColorFrame",
      "YlGnColorFrame", "YlOrBrColorFrame", "YlOrRdColorFrame",
   };
   public static final String[] CALC_GLOBALCLASSES = new String[] {
      "abs(number)",
      "accrint(issue, firstCoupon, settlement, rate, par, frequency, basis)",
      "accrintm(issue, maturity, rate, par, basis)",
      "acos(number)",
      "acosh(number)",
      "amordegrc(cost, date_purchased, first_period, salvage, period, rate, basis)",
      "amorlinc(cost, date_purchased, first_period, salvage, period, rate, basis)",
      "and(conditions...)",
      "asin(number)",
      "asinh(number)",
      "atan(number)",
      "atan2(x, y)",
      "atanh(number)",
      "avedev(array)",
      "average(array)",
      "averagea(array)",
      "binomdist(number, trials, probability, cummulative)",
      "ceiling(number, significance)",
      "character(number)",
      "code(string)",
      "combin(number, num_chosen)",
      "concatenate(string)",
      "correl(array, array2)",
      "cos(number)",
      "cosh(number)",
      "count(array)",
      "counta(array)",
      "countblank(array)",
      "countdistinct()",
      "countif(array, condition)",
      "countn(array)",
      "coupdaybs(settlement, maturity, frequency, basis)",
      "coupdays(settlement, maturity, frequency, basis)",
      "coupdaysnc(settlement, maturity, frequency, basis)",
      "coupncd(settlement, maturity, frequency, basis)",
      "coupnum(settlement, maturity, frequency, basis)",
      "couppcd(settlement, maturity, frequency, basis)",
      "covar(array, array2)",
      "cumipmt(rate, nper, pv, start_period, end_period, type)",
      "cumprinc(rate, nper, pv, start_period, end_period, type)",
      "date()",
      "datevalue(date)",
      "day(date)",
      "dayofyear()",
      "days360(start_date, end_date[, method])",
      "db(cost, salvage, life, period, month)",
      "ddb(cost, salvage, life, period, factor)",
      "degrees(number)",
      "devsq(array)",
      "disc(settlement, maturity, pr, redemption, basis)",
      "dollar(number)",
      "duration(settlement, maturity, coupon, yld, frequency, basis)",
      "edate(date, months)",
      "effect(nominal_rate, npery)",
      "eomonth(date, months)",
      "even(number)",
      "exact(string1, string2)",
      "exp(number)",
      "expondist(x, lambda, cumulative)",
      "fact(number)",
      "factdouble(double)",
      "find(find_string, string, start_pos)",
      "fiscalmonth(date, startMonth, startDay, timeZone)",
      "fiscalmonth445(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalmonth454(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalmonth544(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalquarter(date, startMonth, startDay, timeZone)",
      "fiscalquarter445(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalquarter454(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalquarter544(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalweek(date, startMonth, startDay, timeZone)",
      "fiscalweek445(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalweek454(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalweek544(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalyear(date, startMonth, startDay, timeZone)",
      "fiscalyear445(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalyear454(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fiscalyear544(date, startYear, startMonth, startDay, yearsWith53Weeks, timeZone)",
      "fisher(x)",
      "fisherinv(y)",
      "fixed(number, decimals, no_comma)",
      "floor(number, significance)",
      "forecast(x, known_ys, known_xs)",
      "frequency(data_array, bins_array)",
      "fv(rate, nper, pmt, pv, type)",
      "fvschedule(principal, schedule)",
      "gcd(array)",
      "geomean(array)",
      "harmean(array)",
      "hour(date)",
      "hypgeomdist(samples, num_of_sample, population, num_of_population)",
      "iif(condition, value_if_true, value_if_false)",
      "integer(number)",
      "intercept(known_ys, known_xs)",
      "intrate(settlement, maturity, investment, redemption, basis)",
      "ipmt(rate, per, nper, pv, fv, type)",
      "ispmt(rate, per, nper, pv)",
      "kurt(array)",
      "large(array, nth)",
      "lcm(array)",
      "left(string, num_chars)",
      "len(string)",
      "ln(number)",
      "log(number, base)",
      "log10(number)",
      "lower(string)",
      "max(array)",
      "maxa(array)",
      "maxdate()",
      "mdeterm(array)",
      "mduration(settlement, maturity, coupon, yld, frequency, basis)",
      "median(array)",
      "mid(string, start_num, num_chars)",
      "min(array)",
      "mina(array)",
      "mindate()",
      "minute(date)",
      "minverse(array)",
      "mirr(values, finance_rate, reinvest_rate)",
      "mmult(array, array2)",
      "mod(number, divisor)",
      "mode(array)",
      "month(date)",
      "monthname(date)",
      "mround(number, multiple)",
      "multinomial(array)",
      "negbinomdist(num_of_failure, threshold, probability)",
      "networkdays(start_date, end_date, holidays)",
      "nominal(effect_rate, npery)",
      "none()",
      "not(condition)",
      "now()",
      "nper(rate, pmt, pv, fv, type)",
      "npv(rate, values)",
      "odd(number)",
      "or(conditions...)",
      "pearson(array, array2)",
      "percentile(array, percentile)",
      "percentrank(array, x, significance)",
      "permut(number, number_chosen)",
      "pi()",
      "pmt(rate, nper, pv, fv, type)",
      "poisson(x, mean, cumulative)",
      "power(number, power)",
      "ppmt(rate, per, nper, pv, fv, type)",
      "price(settlement, maturity, rate, yield, redemption, frequency, basis)",
      "pricedisc(settlement, maturity, discount, redemption, basis)",
      "pricemat(settlement, maturity, issue, rate, yield, basis)",
      "prob(xrange, prob_range, lower_limit, upper_limit)",
      "product(array)",
      "proper(string)",
      "pv(rate, nper, pmt, fv, type)",
      "quarter(date)",
      "quartile(array, quart)",
      "quotient(numerator, denominator)",
      "radians(number)",
      "rand()",
      "randbetween(bottom, top)",
      "rank(number, array, order)",
      "received(settlement, maturity, investment, discount, basis)",
      "replace(string, start_pos, num_chars, replace_string)",
      "rept(string, times)",
      "right(string, num_chars)",
      "roman(number)",
      "round(number, num_digits)",
      "rounddown(number, num_digits)",
      "roundup(number, num_digits)",
      "rsq(array, array2)",
      "search(find_string, string, start_pos)",
      "second(date)",
      "seriessum(x, n, m, coefficients)",
      "sign(number)",
      "sin(number)",
      "sinh(number)",
      "skew(array)",
      "sln(cost, salvage, life)",
      "slope(xarray, yarray)",
      "small(array, nth)",
      "sqrt(number)",
      "sqrtpi(number)",
      "standardize(x, mean, standard_dev)",
      "stdev(array)",
      "stdeva(array)",
      "stdevp(array)",
      "stdevpa(array)",
      "steyx(yarray, xarray)",
      "substitute(string, old_string, new_string, instance_num)",
      "subtotal(function_num, array)",
      "sum(array)",
      "sumif(array, condition, sum_range)",
      "sumproduct(array)",
      "sumsq(array)",
      "sumx2my2(xarray, yarray)",
      "sumx2py2(xarray, yarray)",
      "sumxmy2(xarray, yarray)",
      "syd(cost, salvage, life, per)",
      "t(string)",
      "tan(number)",
      "tanh(number)",
      "tbilleq(settlement, maturity, discount)",
      "tbillprice(settlement, maturity, discount)",
      "tbillyield(settlement, maturity, par)",
      "text(double, format)",
      "time(hour, minute, second)",
      "timevalue(date)",
      "today()",
      "trim(string)",
      "trimmean(array, percent)",
      "trunc(number, num_digits)",
      "upper(string)",
      "value(string)",
      "vara(array)",
      "varn()",
      "varp(array)",
      "varpa(array)",
      "vdb(cost, salvage, life, start_period, end_period, factor, flag)",
      "weekday(date[, return_type])",
      "weekdayname(date)",
      "weeknum(date[, return_type])",
      "weibull(x, alpha, beta, cumulative)",
      "weightedavg()",
      "workday(date, days, holidays)",
      "xirr(values, dates, guess)",
      "xnpv(rate, values, dates)",
      "year(date)",
      "yearfrac(start_date, end_date, basis)",
      "yielddisc(settlement, maturity, pr, redemption, basis)",
      "yieldmat(settlement, maturity, issue, rate, pr, basis)"
   };
   public static final String[] CHART_GLOBAL_CLASSES = new String[] {
      "Array()", "Boolean()", "Call()", "Continuation()",
      "Date()", "CALC", "Function()", "Infinity", "Iterator()",
      "Math", "NaN", "Namespace()", "Number()", "Object()", "Packages()",
      "QName()", "RegExp()", "Script()", "StopIteration", "String()", "StyleReport",
      "TOC", "With()", "XML()", "XMLList()",
      "XType", "alert()", "com", "confirm()",
      "createBulletGraph(measure, ranges, target, color, xdims, ydims, opts)",
      "dataBinding()", "dateAdd(interval, amount, date)",
      "dateDiff(interval, date1, date2)", "datePart(interval, date)",
      "decodeURI(encodedURI)", "decodeURIComponent(encodedURIComponent)", "edu",
      "encodeURI(uri)", "encodeURIComponent(uriComponent)", "escape()", "eval(x)",
      "formatDate(date, format_spec)",
      "formatNumber(number, format_spec[, round_option])", "getClass()",
      "getDate()", "getImage(imageResource)", "inArray()",
      "inGroups()", "inetsoft", "intersect()", "isArray(object)", "isDate(object)",
      "isFinite(number)", "isNaN(number)", "isNull(object)", "isNumber(object)",
      "java", "javax", "mapList(array, obj, options)", "net",
      "newInstance(classname)", "numberToString()", "org",
      "parseDate(string)", "parseFloat(string)", "parseInt(string, radix)",
      "refreshData()", "registerPackage()", "reprint()",
      "rowList(tableLens, range, options)", "rtrim(string)", "ltrim(string)",
      "setChanged()", "showReport(archive_report[,target])",
      "split(separator,limit)", "toArray()", "toList(arrObject, options)",
      "runQuery(query_name, parameters)",
      "undefined", "unescape()", "uneval()", "union()",
      "importClass()", "importPackage(string)", "confirmEvent",
      "viewsheetPath", "viewsheetAlias", "viewsheetUser", "exportFormat"
   };

   /**
    * Creates a new instance of <tt>VSScriptableController</tt>.
    */
   @Autowired
   public VSScriptableController(VSScriptableServiceProxy vsScriptableServiceProxy,
                                 VSScriptableService vsScriptableService, VSTreeHandler treeHandler) {
      this.vsScriptableServiceProxy = vsScriptableServiceProxy;
      this.vsScriptableService = vsScriptableService;
      this.treeHandler = treeHandler;
   }

   @GetMapping("/api/vsscriptable/scriptTree")
   public ScriptPaneTreeModel getScriptTree(
      @RequestParam("vsId") String vsId,
      @RequestParam(name = "assemblyName", required = false) String assemblyName,
      @RequestParam(name = "tableName", required = false) String tableName,
      @RequestParam(name = "isCondition", required = false) boolean isCondition,
      @RequestParam(name = "isVSOption", required = false) boolean isVSOption,
      Principal principal) throws Exception
   {
      Thread thread = Thread.currentThread();
      String vsContext = null;
      String assemblyContext = null;

      if(thread instanceof GroupedThread) {
         GroupedThread groupedThread = (GroupedThread) thread;
         vsContext = groupedThread.getRecord(LogContext.DASHBOARD);
         assemblyContext = groupedThread.getRecord(LogContext.ASSEMBLY);
         groupedThread.addRecord(LogContext.DASHBOARD, vsId);
         groupedThread.addRecord(LogContext.ASSEMBLY, assemblyName);
      }

      try {
         ScriptPaneTreeModel.Builder builder = ScriptPaneTreeModel.builder();
         builder.columnTree(
            getColumnTree(vsId, assemblyName, tableName, isCondition, isVSOption, principal));
         builder.functionTree(getFunctionTree(true, principal));
         builder.operatorTree(getOperationTree(principal));
         builder.scriptDefinitions(
            getScriptDefinition(vsId, assemblyName, tableName, isCondition, principal));
         return builder.build();
      }
      finally {
         if(thread instanceof GroupedThread) {
            GroupedThread groupedThread = (GroupedThread) thread;
            groupedThread.addRecord(LogContext.DASHBOARD, vsContext);
            groupedThread.addRecord(LogContext.ASSEMBLY, assemblyContext);
         }
      }
   }

   @GetMapping("/api/vsscriptable/scriptDefinition")
   public ObjectNode getScriptDefinition(
      @RequestParam("vsId") String vsId,
      @RequestParam(name = "assemblyName", required = false) String assemblyName,
      @RequestParam(name = "tableName", required = false) String tableName,
      @RequestParam(name = "isCondition", required = false) boolean isCondition,
      Principal principal) throws Exception
   {
      return vsScriptableServiceProxy.getScriptDefinition(vsId, assemblyName, tableName, isCondition, principal);
   }

   public ObjectNode getScriptDefinition(RuntimeWorksheet rws, TableAssembly table,
                                         Principal principal) throws Exception
   {
      return vsScriptableService.getScriptDefinition(rws, table, principal);
   }

   @GetMapping("/api/vsscriptable/columnTree")
   public TreeNodeModel getColumnTree(
      @RequestParam("vsId") String vsId,
      @RequestParam(name = "assemblyName", required = false) String assemblyName,
      @RequestParam(name = "tableName", required = false) String tableName,
      @RequestParam(name = "isCondition", required = false) boolean isCondition,
      @RequestParam(name = "isVSOption", required = false) boolean isVSOption,
      Principal principal) throws Exception
   {
      return vsScriptableServiceProxy.getColumnTree(vsId, assemblyName, tableName, isCondition, isVSOption, treeHandler, principal);
   }

   @GetMapping("/api/vsscriptable/functionTree")
   public TreeNodeModel getFunctionTree(
      @RequestParam("viewsheet") boolean viewsheet, Principal principal)
   {
      Catalog catalog = Catalog.getCatalog(principal);
      String rootLabel = catalog.getString("Functions");
      String rootName = "Functions";
      String jsFunctionLabel = catalog.getString("JavaScript Functions");
      String jsFunctionName = "JavaScript Functions";
      String excelFunctionLabel = catalog.getString("Excel-style Functions");
      String excelFunctionName = "Excel-style Functions";

      ItemMap functionMap = AdHocQueryHandler.getScriptFunctions(viewsheet);
      ItemMap excelFunctionMap = AdHocQueryHandler.getExcelScriptFunctions();

      TreeNodeModel jsFunctionsNode = createNode(
         jsFunctionLabel, null, false, rootName, rootLabel, null, jsFunctionName,
         null, getChildrenNodesFromMap(functionMap, jsFunctionName, jsFunctionLabel, catalog),
         false);
      TreeNodeModel excelFunctionsNode = createNode(
         excelFunctionLabel, null, false, rootName, rootLabel, null, excelFunctionName,
         null, getChildrenNodesFromMap(excelFunctionMap, excelFunctionName, excelFunctionLabel, catalog),
         false);

      return createNode(
         rootLabel, null, false, null, null, null, rootName,
         null, Arrays.asList(jsFunctionsNode, excelFunctionsNode), false);
   }

   @GetMapping("/api/vsscriptable/operationTree")
   public TreeNodeModel getOperationTree(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      String rootLabel = catalog.getString("Operators");
      String rootName = "Operators";

      ItemMap ops = AdHocQueryHandler.getScriptOperators();
      List<TreeNodeModel> fnodes = new ArrayList<>();
      Iterator<?> keys = ops.itemKeys();

      for(int i = 0; keys.hasNext(); i++) {
         String key = (String) keys.next();
         String funcLabel = catalog.getString(key);
         String funcName = "Operator" + i;

         List<TreeNodeModel> nodes = new ArrayList<>();
         String allFuncs = ops.getItem(key).toString();
         String[] funcs = allFuncs.split("\\$");

         for(int j = 0; j < funcs.length; j++) {
            String[] nodeInfo = funcs[j].split(";");
            String nodeName = "Operator" + i + "|" + j;
            String description;

            if(nodeInfo[1].contains("::")) {
               description = nodeInfo[1].substring(2);
            }
            else {
               description = nodeInfo[1];
            }

            String nodeLabel = nodeInfo[0];

            if(!description.trim().isEmpty()) {
               nodeLabel += " (" + catalog.getString(description) + ")";
            }

            String nodeData = nodeInfo[2];
            nodes.add(createNode(
               nodeLabel, nodeData, true, funcName, funcLabel, null, nodeName, null,
               Collections.emptyList()));
         }

         fnodes.add(createNode(
            funcLabel, null, false, rootName, rootLabel, null, funcName, null, nodes));
      }

      return createNode(
         rootLabel, null, false, null, null, null, rootName, null, fnodes, false);
   }

   @GetMapping("/api/vsscriptable/globalClassesTree")
   public TreeNodeModel getGlobalClassesTree()
   {
      String[] funcs = CHART_GLOBAL_CLASSES;
      List<TreeNodeModel> fnodes = new ArrayList<>();

      for(String func : funcs) {
         fnodes.add(createNode(func, func, true, "Global", "Global", null, func, null, Collections.emptyList()));
      }

      return createNode(
         "Global", null, false, null, null, null, "Global", null, fnodes, false);
   }

   @GetMapping("/api/vsscriptable/classesTree")
   public TreeNodeModel getClassesTree(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);

      final String rootName = "data";
      final String rootLabel = catalog.getString("Data");
      final String rootData = null;

      List<TreeNodeModel> children = new ArrayList<>();

      for(String className : CHART_CLASSES) {
         children.add(createNode(
            className, className, true, rootName, rootLabel, null, "class", null,
            Collections.emptyList()));
      }

      return createNode(
         rootLabel, rootData, false, null, null, null, rootName, null, children, true);
   }

   @GetMapping("api/vsscriptable/constantsTree")
   public TreeNodeModel getConstantsTree(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);

      final String rootName = "data";
      final String rootLabel = catalog.getString("Data");
      final String rootData = null;

      List<TreeNodeModel> children = new ArrayList<>();

      for(String constant : CHART_CONSTANTS) {
         children.add(createNode(
            constant, constant, true, rootName, rootLabel, null, "constant", null,
            Collections.emptyList()));
      }

      return createNode(
         rootLabel, rootData, false, null, null, null, rootName, null, children, true);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children)
   {
      return createNode(
         label, data, leaf, parentName, parentLabel, parentData, name, suffix, children,
         false, false);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean dot)
   {
      return createNode(
         label, data, leaf, parentName, parentLabel, parentData, name, suffix, children,
         false, dot);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded,
                                    boolean dot)
   {
      return createNode(
         label, data, leaf, parentName, parentLabel, parentData, name, suffix, children, expanded,
         null, dot, false);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded,
                                    List<String> fields, boolean dot, boolean component)
   {
      return TreeNodeModel.builder()
         .label(label)
         .leaf(leaf)
         .expanded(expanded)
         .children(children)
         .data(ScriptTreeNodeData.builder()
                  .data(data)
                  .dot(dot)
                  .parentName(parentName)
                  .parentLabel(parentLabel)
                  .parentData(parentData)
                  .name(name)
                  .suffix(suffix)
                  .fields(fields)
                  .build())
         .build();
   }

   private List<TreeNodeModel> getChildrenNodesFromMap(ItemMap map, String parentName,
                                                       String parentLabel, Catalog catalog)
   {
      List<TreeNodeModel> fnodes = new ArrayList<>();
      Iterator<?> keys = map.itemKeys();

      for(int i = 0; keys.hasNext(); i++) {
         String key = (String) keys.next();
         String funcLabel = key;
         List<String> labelInfo = Arrays.asList(funcLabel.split(";"));
         boolean dotScope = false;

         if(labelInfo.size() > 1 && labelInfo.contains(DOT_FLAG)) {
            dotScope = true;
            funcLabel = labelInfo.stream()
               .filter(part -> !Objects.equals(part, DOT_FLAG))
               .collect(Collectors.joining(";"));
         }

         funcLabel = catalog.getString(funcLabel);
         String funcName = "Function" + i;

         List<TreeNodeModel> nodes = new ArrayList<>();
         String allFuncs = map.getItem(key).toString();
         String[] funcNames = allFuncs.split("\\^");

         for(int j = 0; j < funcNames.length; j++) {
            String[] nodeInfo = funcNames[j].split(";");
            boolean dotItem = false;

            if(!dotScope && Arrays.asList(nodeInfo).contains(DOT_FLAG)) {
               dotItem = true;
            }

            nodes.add(createNode(
               nodeInfo[0], nodeInfo[1], true, funcName, funcLabel, null,
               "Function" + i + "|" + j, null, Collections.emptyList(),
               dotScope || dotItem));
         }

         fnodes.add(createNode(
            funcLabel, null, false, parentName, parentLabel, null, funcName,
            null, nodes));
      }

      return fnodes;
   }

   private final VSScriptableServiceProxy vsScriptableServiceProxy;
   private final VSScriptableService vsScriptableService;
   private final VSTreeHandler treeHandler;
}
