/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.common.sink.config;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import org.apache.kafka.common.config.ConfigDef;

/** Generates Asiidoc documentation for a ConfigDef */
public class AsciiDocGenerator {

  public static String toAscidoc(ConfigDef configDef) {
    StringBuilder b = new StringBuilder();
    for (ConfigDef.ConfigKey key : sortedConfigs(configDef)) {
      if (key.internalConfig) {
        continue;
      }
      getConfigKeyAsciiDoc(configDef, key, b);
      b.append("\n");
    }
    return b.toString();
  }

  protected static void getConfigKeyAsciiDoc(
      ConfigDef configDef, ConfigDef.ConfigKey key, StringBuilder b) {
    b.append("``").append(key.name).append("`` +").append("\n");
    for (String docLine : key.documentation.split("\n")) {
      if (docLine.length() == 0) {
        continue;
      }
      b.append(docLine).append("\n\n");
    }
    b.append("* Type: ").append(getConfigValue(key, "Type")).append("\n");
    if (key.hasDefault()) {
      b.append("* Default: ").append(getConfigValue(key, "Default")).append("\n");
    }
    if (key.validator != null) {
      b.append("* Valid Values: ").append(getConfigValue(key, "Valid Values")).append("\n");
    }
    b.append("* Importance: ").append(getConfigValue(key, "Importance")).append("\n");
  }

  protected static String getConfigValue(ConfigDef.ConfigKey key, String headerName) {
    switch (headerName) {
      case "Name":
        return key.name;
      case "Description":
        return key.documentation;
      case "Type":
        return key.type.toString().toLowerCase(Locale.ROOT);
      case "Default":
        if (key.hasDefault()) {
          if (key.defaultValue == null) return "null";
          String defaultValueStr = ConfigDef.convertToString(key.defaultValue, key.type);
          if (defaultValueStr.isEmpty()) return "\"\"";
          else return defaultValueStr;
        } else return "";
      case "Valid Values":
        return key.validator != null ? key.validator.toString() : "";
      case "Importance":
        return key.importance.toString().toLowerCase(Locale.ROOT);
      default:
        throw new RuntimeException(
            "Can't find value for header '" + headerName + "' in " + key.name);
    }
  }

  protected static List<ConfigDef.ConfigKey> sortedConfigs(ConfigDef configDef) {
    final Map<String, Integer> groupOrd = new HashMap<>(configDef.groups().size());
    int ord = 0;
    for (String group : configDef.groups()) {
      groupOrd.put(group, ord++);
    }

    List<ConfigDef.ConfigKey> configs = new ArrayList<>(configDef.configKeys().values());
    Collections.sort(configs, (k1, k2) -> compare(k1, k2, groupOrd));
    return configs;
  }

  protected static int compare(
      ConfigDef.ConfigKey k1, ConfigDef.ConfigKey k2, Map<String, Integer> groupOrd) {
    int cmp =
        k1.group == null
            ? (k2.group == null ? 0 : -1)
            : (k2.group == null
                ? 1
                : Integer.compare(groupOrd.get(k1.group), groupOrd.get(k2.group)));
    if (cmp == 0) {
      cmp = Integer.compare(k1.orderInGroup, k2.orderInGroup);
      if (cmp == 0) {
        // first take anything with no default value
        if (!k1.hasDefault() && k2.hasDefault()) cmp = -1;
        else if (!k2.hasDefault() && k1.hasDefault()) cmp = 1;
        else {
          cmp = k1.importance.compareTo(k2.importance);
          if (cmp == 0) return k1.name.compareTo(k2.name);
        }
      }
    }
    return cmp;
  }

  public static void generateConfigDefDoc(Path path, String name, String title, ConfigDef configDef)
      throws IOException {
    try (FileWriter fileWriter = new FileWriter(path.resolve(name).toFile())) {
      PrintWriter pw = new PrintWriter(fileWriter);
      pw.append("== ").append(title).append("\n\n");
      pw.append(toAscidoc(configDef));
      pw.flush();
    }
  }
}
