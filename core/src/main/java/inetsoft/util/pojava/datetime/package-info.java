/*
 From org.pojava.datetime 3.0.0 (Maven Central org.pojava:org.pojava.datetime:3.0.0, sources
 jar SHA-1 e490c88590ee81ae54839e99f2701c02c543cef8) by John Pile, licensed under the Apache
 License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */

/**
 * Date and time parsing from pojava 3.0.0, vendored so that its calendar arithmetic is always
 * Gregorian. The upstream Tm.calcTime() and the two DateTime.shift() methods use Calendar.getInstance(),
 * which takes the Buddhist or Japanese calendar of a th_TH or ja_JP_JP JVM default locale and
 * shifts every parsed year (#77605). Those three calls are the only changes to the upstream
 * sources besides the package name.
 */
package inetsoft.util.pojava.datetime;
