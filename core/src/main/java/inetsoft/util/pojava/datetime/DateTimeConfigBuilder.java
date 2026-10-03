package inetsoft.util.pojava.datetime;

/*
 From org.pojava.datetime 3.0.0 by John Pile, moved to inetsoft.util.pojava.datetime by
 InetSoft Technology in 2026.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * DateTimeConfig has numerous values, some of which we want to control, sometimes making minor changes.
 * This Builder allows for incremental changes, producing an immutable config object once built.
 */
public class DateTimeConfigBuilder {

    private boolean isDmyOrder = false;
    private boolean isUnspecifiedCenturyAlwaysInPast = false;
    private int epochDOW = 4;
    private String format = "yyyy-MM-dd HH:mm:ss";
    private String defaultJdbcFormat = "yyyy-MM-dd HH:mm:ss.SSS";
    private TimeZone inputTimeZone = TimeZone.getDefault();
    private TimeZone outputTimeZone = TimeZone.getDefault();
    private Locale locale = Locale.getDefault();
    private String bcPrefix = "-";
    private MonthMap monthMap;
    /**
     * <p>
     * Support parsing of zones unlisted in TimeZone by translating to known zones. Got a zone
     * that's not supported or should be overridden? Fix it locally by updating your custom
     * tzMap!
     * </p>
     * <p/>
     * <pre>
     * // Example change CST from U.S. Central to Chinese.
     * class CustomTzMap {
     * 	private static Map&lt;String, String&amp;gt tzMap = DateTimeConfig.getTzMap();
     * 	static {
     * 		tzMap.put(&quot;CST&quot;, &quot;Asia/Hong_Kong&quot;);
     *    }
     * }
     * </pre>
     */
    private final Map<String, String> tzMap = new HashMap<String, String>();

    private final Map<String, TimeZone> tzCache = new HashMap<String, TimeZone>();

    public DateTimeConfigBuilder() {

    }

    public static DateTimeConfigBuilder newInstance() {
        DateTimeConfigBuilder builder = new DateTimeConfigBuilder();
        builder.monthMap = MonthMap.fromAllLocales();
        return builder;
    }

    public boolean isDmyOrder() {
        return isDmyOrder;
    }

    public void setDmyOrder(boolean isDmyOrder) {
        this.isDmyOrder = isDmyOrder;
    }

    public DateTimeConfigBuilder dmyOrder(boolean isDmyOrder) {
        this.isDmyOrder = isDmyOrder;
        return this;
    }

    public boolean isUnspecifiedCenturyAlwaysInPast() {
        return isUnspecifiedCenturyAlwaysInPast;
    }

    public void setUnspecifiedCenturyAlwaysInPast(boolean isUnspecifiedCenturyAlwaysInPast) {
        this.isUnspecifiedCenturyAlwaysInPast = isUnspecifiedCenturyAlwaysInPast;
    }

    public DateTimeConfigBuilder unspecifiedCenturyAlwaysInPast(boolean isUnspecifiedCenturyAlwaysInPast) {
        this.isUnspecifiedCenturyAlwaysInPast = isUnspecifiedCenturyAlwaysInPast;
        return this;
    }

    public int getEpochDOW() {
        return epochDOW;
    }

    public void setEpochDOW(int epochDOW) {
        this.epochDOW = epochDOW;
    }

    public DateTimeConfigBuilder epochDOW(int epochDOW) {
        this.epochDOW = epochDOW;
        return this;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public DateTimeConfigBuilder format(String format) {
        this.format = format;
        return this;
    }

    public String getDefaultJdbcFormat() {
        return defaultJdbcFormat;
    }

    public DateTimeConfigBuilder defaultJdbcFormat(String defaultJdbcFormat) {
        this.defaultJdbcFormat = defaultJdbcFormat;
        return this;
    }

    public void setDefaultJdbcFormat(String defaultJdbcFormat) {
        this.defaultJdbcFormat = defaultJdbcFormat;
    }

    public TimeZone getInputTimeZone() {
        return inputTimeZone;
    }

    public DateTimeConfigBuilder inputTimeZone(TimeZone inputTimeZone) {
        this.inputTimeZone = inputTimeZone;
        return this;
    }

    public void setInputTimeZone(TimeZone inputTimeZone) {
        this.inputTimeZone = inputTimeZone;
    }

    public TimeZone getOutputTimeZone() {
        return outputTimeZone;
    }

    public DateTimeConfigBuilder outputTimeZone(TimeZone outputTimeZone) {
        this.outputTimeZone = outputTimeZone;
        return this;
    }

    public void setOutputTimeZone(TimeZone outputTimeZone) {
        this.outputTimeZone = outputTimeZone;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public DateTimeConfigBuilder locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    public String getBcPrefix() {
        return bcPrefix;
    }

    public void setBcPrefix(String bcPrefix) {
        this.bcPrefix = bcPrefix;
    }

    /**
     * Set whether a formatted year should include a minus sign when B.C.
     *
     * @param bcPrefix
     * @return Prefix string in front of the year if BC (e.g. "-")
     */
    public DateTimeConfigBuilder bcPrefix(String bcPrefix) {
        this.bcPrefix = bcPrefix;
        return this;
    }

    public MonthMap getMonthMap() {
        return monthMap;
    }

    public void setMonthMap(MonthMap monthMap) {
        this.monthMap = monthMap;
    }

    public DateTimeConfigBuilder monthMap(MonthMap monthMap) {
        this.monthMap = monthMap;
        return this;
    }

    /**
     * Add your own uniquely named time zone to the list of interpreted zones.
     *
     * @param id the name identifying your time zone
     * @param tz a TimeZone object
     */
    public void addTimeZone(String id, TimeZone tz) {
        tzCache.put(id, tz);
    }

    public Map<String, TimeZone> getTzCache() {
        return tzCache;
    }

    public void addTzMap(String id1, String id2) {
        tzMap.put(id1, id2);
    }

    public Map<String, String> getTzMap() {
        return tzMap;
    }
}
