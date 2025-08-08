/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.test.helper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class Utils {

    private static final SecureRandom random = new SecureRandom();

    private Utils() {}

    public static String randomStringGenerator(final String prefix, final int len, final String sourceSetString) {
        final int lengthOfSource = sourceSetString.length();
        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(sourceSetString.charAt(random.nextInt(lengthOfSource)));
        }
        return prefix + sb;
    }

    public static String randomStringGenerator(final String prefix, final int len) {
        return randomStringGenerator(prefix, len, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }

    public static String randomNameGenerator(final String prefix, final int lenOfRandomSuffix) {
        return randomStringGenerator(prefix, lenOfRandomSuffix);
    }

    public static LocalDate now() {
        return LocalDate.now(Clock.systemUTC());
    }

    /**
     * A record that formats a double value based on whether it's a whole number or not.
     * <p>
     * If the value is a whole number, the output will have one decimal place (e.g., 16.0). Otherwise, it will have two
     * decimal places (e.g., 16.90), but if the second decimal place is zero, it will be removed (so 16.90 becomes
     * 16.9).
     */
    public record DoubleFormatter(double value) {

        public String format() {
            boolean isWholeNumber = (value % 1.0 == 0);

            String result = isWholeNumber ? String.format("%.1f", value) : String.format("%.2f", value);

            // For non-whole numbers, remove trailing '0' if it exists
            if (!isWholeNumber && result.endsWith("0")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        }
    }

    /**
     * Splits a string by the given delimiter, trims each part, and omits empty strings.
     *
     * @param input
     *            the string to split
     * @param delimiter
     *            the delimiter to split by
     * @return a list of non-empty, trimmed strings
     */
    public static List<String> splitAndTrim(String input, String delimiter) {
        if (input == null || delimiter == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        int delimLen = delimiter.length();
        while (true) {
            int idx = input.indexOf(delimiter, start);
            if (idx == -1) {
                String part = input.substring(start).trim();
                if (!part.isEmpty()) {
                    result.add(part);
                }
                break;
            }
            String part = input.substring(start, idx).trim();
            if (!part.isEmpty()) {
                result.add(part);
            }
            start = idx + delimLen;
        }
        return result;
    }
}
