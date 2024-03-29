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

import com.datastax.oss.common.sink.ConfigException;
import com.datastax.oss.driver.internal.core.type.codec.registry.DefaultCodecRegistry;
import com.datastax.oss.driver.shaded.guava.common.base.Splitter;
import com.datastax.oss.dsbulk.codecs.api.ConversionContext;
import com.datastax.oss.dsbulk.codecs.api.ConvertingCodecFactory;
import com.datastax.oss.dsbulk.codecs.api.util.CodecUtils;
import com.datastax.oss.dsbulk.codecs.text.TextConversionContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

/** Topic-specific connector configuration. */
public class TopicConfig extends AbstractConfig {
  static final String TIME_PAT_OPT = "codec.time";
  static final String LOCALE_OPT = "codec.locale";
  static final String TIMEZONE_OPT = "codec.timeZone";
  static final String TIMESTAMP_PAT_OPT = "codec.timestamp";
  static final String DATE_PAT_OPT = "codec.date";
  static final String TIME_UNIT_OPT = "codec.unit";

  // Table settings are of the form "topic.mytopic.ks1.table1.setting"
  private static final Pattern TABLE_KS_PATTERN =
      Pattern.compile("^topic\\.[a-zA-Z0-9._-]+\\.([^.]+)\\.([^.]+)\\.");

  private final String topicName;
  private final Collection<TableConfig> tableConfigs;

  static String getTopicSettingPath(String topicName, String setting) {
    return String.format("topic.%s.%s", topicName, setting);
  }

  public TopicConfig(String topicName, Map<String, String> settings, boolean cloud) {
    super(makeTopicConfigDef(topicName), settings, false);

    Map<String, TableConfig.Builder> tableConfigBuilders = new LinkedHashMap<>();

    // Walk through the settings and separate out the table settings. Other settings
    // are effectively handled by our AbstractConfig super-class.
    settings.forEach(
        (name, value) -> {
          Matcher codecSettingPattern = CassandraSinkConfig.TOPIC_CODEC_PATTERN.matcher(name);
          Matcher tableKsSettingMatcher = TABLE_KS_PATTERN.matcher(name);

          // using codecSettingPattern to prevent including of
          // global topic level settings (under .codec prefix)
          if (!codecSettingPattern.matches() && tableKsSettingMatcher.lookingAt()) {
            TableConfig.Builder builder =
                tableConfigBuilders.computeIfAbsent(
                    tableKsSettingMatcher.group(),
                    t ->
                        new TableConfig.Builder(
                            topicName,
                            tableKsSettingMatcher.group(1),
                            tableKsSettingMatcher.group(2),
                            cloud));
            builder.addSetting(name, value);
          }
        });

    if (tableConfigBuilders.isEmpty()) {
      throw new ConfigException(
          String.format("Topic %s must have at least one table configuration", topicName));
    }

    tableConfigs =
        tableConfigBuilders
            .values()
            .stream()
            .map(TableConfig.Builder::build)
            .collect(Collectors.toList());
    this.topicName = topicName;
  }

  @NonNull
  public String getTopicName() {
    return topicName;
  }

  @NonNull
  public Collection<TableConfig> getTableConfigs() {
    return tableConfigs;
  }

  @Override
  @NonNull
  public String toString() {
    String[] codecSettings = {
      LOCALE_OPT, TIMEZONE_OPT, TIMESTAMP_PAT_OPT, DATE_PAT_OPT, TIME_PAT_OPT, TIME_UNIT_OPT
    };
    String codecString =
        Arrays.stream(codecSettings)
            .map(
                s ->
                    String.format(
                        "%s: %s",
                        s.substring("codec.".length()),
                        getString(getTopicSettingPath(topicName, s))))
            .collect(Collectors.joining(", "));

    return String.format(
        "name: %s, codec settings: %s%nTable configurations:%n%s",
        topicName,
        codecString,
        tableConfigs
            .stream()
            .map(
                t ->
                    Splitter.on("\n")
                        .splitToList(t.toString())
                        .stream()
                        .map(line -> "        " + line)
                        .collect(Collectors.joining("\n")))
            .collect(Collectors.joining("\n")));
  }

  @NonNull
  public ConvertingCodecFactory createCodecFactory(DefaultCodecRegistry defaultCodecRegistry) {
    ConversionContext context =
        new TextConversionContext()
            .setLocale(
                CodecUtils.parseLocale(getString(getTopicSettingPath(topicName, LOCALE_OPT))))
            .setTimestampFormat(getString(getTopicSettingPath(topicName, TIMESTAMP_PAT_OPT)))
            .setDateFormat(getString(getTopicSettingPath(topicName, DATE_PAT_OPT)))
            .setTimeFormat(getString(getTopicSettingPath(topicName, TIME_PAT_OPT)))
            .setTimeZone(ZoneId.of(getString(getTopicSettingPath(topicName, TIMEZONE_OPT))))
            .setTimeUnit(
                TimeUnit.valueOf(getString(getTopicSettingPath(topicName, TIME_UNIT_OPT))));
    return new ConvertingCodecFactory(defaultCodecRegistry, context);
  }

  /**
   * Build up a {@link ConfigDef} for the given topic specification.
   *
   * @param topicName name of topic
   * @return a ConfigDef of topic settings, where each setting name is the full setting path (e.g.
   *     topic.[topicname]).
   */
  @NonNull
  private static ConfigDef makeTopicConfigDef(String topicName) {
    return new ConfigDef()
        .define(
            getTopicSettingPath(topicName, LOCALE_OPT),
            ConfigDef.Type.STRING,
            "en_US",
            ConfigDef.Importance.HIGH,
            "The locale to use for locale-sensitive conversions.")
        .define(
            getTopicSettingPath(topicName, TIMEZONE_OPT),
            ConfigDef.Type.STRING,
            "UTC",
            ConfigDef.Importance.HIGH,
            "The time zone to use for temporal conversions that do not convey any explicit time zone information")
        .define(
            getTopicSettingPath(topicName, TIMESTAMP_PAT_OPT),
            ConfigDef.Type.STRING,
            "CQL_TIMESTAMP",
            ConfigDef.Importance.HIGH,
            "The temporal pattern to use for `String` to CQL `timestamp` conversion")
        .define(
            getTopicSettingPath(topicName, DATE_PAT_OPT),
            ConfigDef.Type.STRING,
            "ISO_LOCAL_DATE",
            ConfigDef.Importance.HIGH,
            "The temporal pattern to use for `String` to CQL `date` conversion")
        .define(
            getTopicSettingPath(topicName, TIME_PAT_OPT),
            ConfigDef.Type.STRING,
            "ISO_LOCAL_TIME",
            ConfigDef.Importance.HIGH,
            "The temporal pattern to use for `String` to CQL `time` conversion")
        .define(
            getTopicSettingPath(topicName, TIME_UNIT_OPT),
            ConfigDef.Type.STRING,
            "MILLISECONDS",
            ConfigDef.Importance.HIGH,
            "If the input is a string containing only digits that cannot be parsed using the `codec.timestamp` format, the specified time unit is applied to the parsed value. All `TimeUnit` enum constants are valid choices.");
  }
}
