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

import static com.datastax.oss.common.sink.config.AuthenticatorConfig.KEYTAB_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.COMPRESSION_DEFAULT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.COMPRESSION_DRIVER_SETTING;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.COMPRESSION_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.CONCURRENT_REQUESTS_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.CONNECTION_POOL_LOCAL_SIZE;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.CONNECTION_POOL_LOCAL_SIZE_DEFAULT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.CONTACT_POINTS_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.DC_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.IGNORE_ERRORS;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.JAVA_DRIVER_SETTINGS_LIST_TYPE;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.LOCAL_DC_DRIVER_SETTING;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.METRICS_HIGHEST_LATENCY_DEFAULT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.METRICS_HIGHEST_LATENCY_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.METRICS_INTERVAL_DEFAULT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.PORT_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.QUERY_EXECUTION_TIMEOUT_DEFAULT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.QUERY_EXECUTION_TIMEOUT_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.SECURE_CONNECT_BUNDLE_DRIVER_SETTING;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.SECURE_CONNECT_BUNDLE_OPT;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.SSL_OPT_PREFIX;
import static com.datastax.oss.common.sink.config.CassandraSinkConfig.withDriverPrefix;
import static com.datastax.oss.common.sink.config.SslConfig.KEYSTORE_PATH_OPT;
import static com.datastax.oss.common.sink.config.SslConfig.OPENSSL_KEY_CERT_CHAIN_OPT;
import static com.datastax.oss.common.sink.config.SslConfig.OPENSSL_PRIVATE_KEY_OPT;
import static com.datastax.oss.common.sink.config.SslConfig.PROVIDER_OPT;
import static com.datastax.oss.common.sink.config.SslConfig.TRUSTSTORE_PATH_OPT;
import static com.datastax.oss.common.sink.config.TableConfig.MAPPING_OPT;
import static com.datastax.oss.common.sink.config.TableConfig.getTableSettingPath;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONTACT_POINTS;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.METRICS_SESSION_CQL_REQUESTS_INTERVAL;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.METRICS_SESSION_ENABLED;
import static com.datastax.oss.dsbulk.tests.assertions.TestAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datastax.oss.common.sink.ConfigException;
import com.datastax.oss.common.sink.config.CassandraSinkConfig.IgnoreErrorsPolicy;
import com.datastax.oss.common.sink.util.SinkUtil;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableMap;
import com.datastax.oss.driver.shaded.guava.common.collect.Maps;
import com.datastax.oss.dsbulk.tests.logging.LogInterceptingExtension;
import com.datastax.oss.dsbulk.tests.logging.LogInterceptor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(LogInterceptingExtension.class)
class CassandraSinkConfigTest {

  private static final String CONTACT_POINTS_DRIVER_SETTINGS = withDriverPrefix(CONTACT_POINTS);

  @Test
  void should_error_invalid_port() {
    Map<String, String> props =
        Maps.newHashMap(ImmutableMap.<String, String>builder().put(PORT_OPT, "foo").build());
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid value foo for configuration port");

    props.put(PORT_OPT, "0");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");

    props.put(PORT_OPT, "-1");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");
  }

  @Test
  void should_error_invalid_queryExecutionTimeout() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder().put(QUERY_EXECUTION_TIMEOUT_OPT, "foo").build());
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid value foo for configuration queryExecutionTimeout");

    props.put(QUERY_EXECUTION_TIMEOUT_OPT, "0");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");

    props.put(QUERY_EXECUTION_TIMEOUT_OPT, "-1");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");
  }

  @Test
  void should_error_invalid_metricsHighestLatency() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder().put(METRICS_HIGHEST_LATENCY_OPT, "foo").build());
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid value foo for configuration metricsHighestLatency");

    props.put(METRICS_HIGHEST_LATENCY_OPT, "0");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");

    props.put(METRICS_HIGHEST_LATENCY_OPT, "-1");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");
  }

  @Test
  void should_error_invalid_connectionPoolLocalSize() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder().put(CONNECTION_POOL_LOCAL_SIZE, "foo").build());
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid value foo for configuration connectionPoolLocalSize");

    props.put(CONNECTION_POOL_LOCAL_SIZE, "0");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");

    props.put(CONNECTION_POOL_LOCAL_SIZE, "-1");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");
  }

  @Test
  void should_error_invalid_maxConcurrentRequests() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder().put(CONCURRENT_REQUESTS_OPT, "foo").build());
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid value foo for configuration maxConcurrentRequests");

    props.put(CONCURRENT_REQUESTS_OPT, "0");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");

    props.put(CONCURRENT_REQUESTS_OPT, "-1");
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");
  }

  @Test
  void should_error_invalid_compression_type() {
    Map<String, String> props =
        Maps.newHashMap(ImmutableMap.<String, String>builder().put(COMPRESSION_OPT, "foo").build());
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format(
                "Invalid value foo for configuration %s: String must be one of (case insensitive): LZ4, NONE, SNAPPY",
                COMPRESSION_OPT));
  }

  @Test
  void should_error_missing_dc_with_contactPoints() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(CONTACT_POINTS_OPT, "127.0.0.1").build();
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format("When contact points is provided, %s must also be specified", DC_OPT));
  }

  @Test
  void should_error_empty_dc_with_contactPoints() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(CONTACT_POINTS_OPT, "127.0.0.1")
            .put(DC_OPT, "")
            .build();
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format("When contact points is provided, %s must also be specified", DC_OPT));
  }

  @Test
  void should_handle_dc_with_contactPoints() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(CONTACT_POINTS_OPT, "127.0.0.1, 127.0.1.1")
            .put(DC_OPT, "local")
            .build();

    CassandraSinkConfig d = new CassandraSinkConfig(props);
    assertThat(d.getContactPoints()).containsExactly("127.0.0.1", "127.0.1.1");
    assertThat(d.getLocalDc().get()).isEqualTo("local");
  }

  @Test
  void should_handle_dc_with_contactPoints_driver_prefix() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(CONTACT_POINTS_OPT, "127.0.0.1, 127.0.1.1")
            .put(LOCAL_DC_DRIVER_SETTING, "local")
            .build();

    CassandraSinkConfig d = new CassandraSinkConfig(props);
    assertThat(d.getContactPoints()).containsExactly("127.0.0.1", "127.0.1.1");
    assertThat(d.getLocalDc().get()).isEqualTo("local");
  }

  @Test
  void should_handle_port() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(PORT_OPT, "5725").build();

    CassandraSinkConfig d = new CassandraSinkConfig(props);
    assertThat(d.getPort()).isEqualTo(5725);
  }

  @Test
  void should_handle_maxConcurrentRequests() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(CONCURRENT_REQUESTS_OPT, "129").build();

    CassandraSinkConfig d = new CassandraSinkConfig(props);
    assertThat(d.getMaxConcurrentRequests()).isEqualTo(129);
  }

  @Test
  void should_handle_instance_name() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(SinkUtil.NAME_OPT, "myinst").build();

    CassandraSinkConfig d = new CassandraSinkConfig(props);
    assertThat(d.getInstanceName()).isEqualTo("myinst");
  }

  @Test
  void should_handle_secure_connect_bundle() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(SECURE_CONNECT_BUNDLE_OPT, "/location/to/bundle")
            .build();

    CassandraSinkConfig d = new CassandraSinkConfig(props);
    assertThat(d.getJavaDriverSettings().get(SECURE_CONNECT_BUNDLE_DRIVER_SETTING))
        .isEqualTo("/location/to/bundle");
  }

  @Test
  void should_throw_when_secure_connect_bundle_and_contact_points_provided() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(SECURE_CONNECT_BUNDLE_OPT, "/location/to/bundle")
            .put(CONTACT_POINTS_OPT, "127.0.0.1")
            .build();

    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format(
                "When %s parameter is specified you should not provide %s.",
                SECURE_CONNECT_BUNDLE_OPT, CONTACT_POINTS_OPT));
  }

  @Test
  void should_throw_when_secure_connect_bundle_and_local_dc_provided() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(SECURE_CONNECT_BUNDLE_OPT, "/location/to/bundle")
            .put(DC_OPT, "dc1")
            .build();

    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format(
                "When %s parameter is specified you should not provide %s.",
                SECURE_CONNECT_BUNDLE_OPT, DC_OPT));
  }

  @Test
  void should_throw_when_secure_connect_bundle_and_local_dc_driver_setting_provided() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(SECURE_CONNECT_BUNDLE_OPT, "/location/to/bundle")
            .put(LOCAL_DC_DRIVER_SETTING, "dc1")
            .build();

    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format(
                "When %s parameter is specified you should not provide %s.",
                SECURE_CONNECT_BUNDLE_OPT, DC_OPT));
  }

  @Test
  void should_throw_when_secure_connect_bundle_and_ssl_setting_provided() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(SECURE_CONNECT_BUNDLE_OPT, "/location/to/bundle")
            .put(PROVIDER_OPT, "JDK")
            .build();

    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format(
                "When %s parameter is specified you should not provide any setting under %s.",
                SECURE_CONNECT_BUNDLE_OPT, SSL_OPT_PREFIX));
  }

  @Test
  void should_parse_multiple_topic_configs() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(getTableSettingPath("mytopic", "MyKs", "MyTable", MAPPING_OPT), "c1=value.f1")
                .put(
                    getTableSettingPath("yourtopic", "MyKs2", "MyTable2", MAPPING_OPT),
                    "d1=value.f1")
                .build());
    CassandraSinkConfig d = new CassandraSinkConfig(props);
    Map<String, TopicConfig> topicConfigs = d.getTopicConfigs();
    assertThat(topicConfigs.size()).isEqualTo(2);
    assertTopic(
        "MyKs",
        "MyTable",
        ImmutableMap.<CqlIdentifier, CqlIdentifier>builder()
            .put(CqlIdentifier.fromInternal("c1"), CqlIdentifier.fromInternal("value.f1"))
            .build(),
        topicConfigs.get("mytopic"));
    assertTopic(
        "MyKs2",
        "MyTable2",
        ImmutableMap.<CqlIdentifier, CqlIdentifier>builder()
            .put(CqlIdentifier.fromInternal("d1"), CqlIdentifier.fromInternal("value.f1"))
            .build(),
        topicConfigs.get("yourtopic"));
  }

  @ParameterizedTest(name = "[{index}] topicName={0}")
  @MethodSource("correctTopicNames")
  void should_parse_correct_kafka_topic_names(String topicName) {
    // given
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(
                    String.format("topic.%s.%s.%s.%s", topicName, "ks", "tb", "mapping"),
                    "c1=value.f1")
                .build());
    // when
    CassandraSinkConfig d = new CassandraSinkConfig(props);
    Map<String, TopicConfig> topicConfigs = d.getTopicConfigs();

    // then
    assertThat(topicConfigs.size()).isEqualTo(1);
    assertTopic(
        "ks",
        "tb",
        ImmutableMap.<CqlIdentifier, CqlIdentifier>builder()
            .put(CqlIdentifier.fromInternal("c1"), CqlIdentifier.fromInternal("value.f1"))
            .build(),
        topicConfigs.get(topicName));
  }

  @ParameterizedTest(name = "[{index}] topicName={0}")
  @MethodSource("incorrectTopicNames")
  void should_not_parse_incorrect_kafka_topic_names(String topicName) {
    // given
    String settingName = String.format("topic.%s.%s.%s.%s", topicName, "ks", "tb", "mapping");
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder().put(settingName, "c1=value.f1").build());
    // when then
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The setting: "
                + settingName
                + " does not match topic.keyspace.table nor topic.codec regular expression pattern");
  }

  private static Stream<? extends Arguments> correctTopicNames() {
    return Stream.of(
        Arguments.of("org.datastax.init.event.history"),
        Arguments.of("org_datastax_init_event_history"),
        Arguments.of("org-datastax-init-event-history"),
        Arguments.of("org.datastax-init_event.history"),
        Arguments.of("t.codec.ttl"),
        Arguments.of("1.2_3.A_z"));
  }

  private static Stream<? extends Arguments> incorrectTopicNames() {
    return Stream.of(
        Arguments.of("org,datastax"),
        Arguments.of("org&topic"),
        Arguments.of("()"),
        Arguments.of("%"));
  }

  @Test
  void should_error_when_missing_topic_settings() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put("topics", "mytopic2")
                .put(getTableSettingPath("mytopic", "MyKs", "MyTable", MAPPING_OPT), "c1=value.f1")
                .build());

    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Missing topic settings (topic.mytopic2.*) for topic mytopic2");
  }

  @Test
  void should_handle_topics_list_with_spaces() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put("topics", "mytopic, mytopic2")
                .put(getTableSettingPath("mytopic", "MyKs", "MyTable", MAPPING_OPT), "c1=value.f1")
                .put(
                    getTableSettingPath("mytopic2", "MyKs2", "MyTable2", MAPPING_OPT),
                    "c1=value.f1")
                .build());

    CassandraSinkConfig config = new CassandraSinkConfig(props);
    assertThat(config.getTopicConfigs().size()).isEqualTo(2);
  }

  @Test
  void should_handle_topics_list_with_dots() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put("topics", "org.datastax.init.event.history, org.datastax.init.event.history2")
                .put(
                    getTableSettingPath(
                        "org.datastax.init.event.history", "MyKs", "MyTable", MAPPING_OPT),
                    "c1=value.f1")
                .put(
                    getTableSettingPath(
                        "org.datastax.init.event.history2", "MyKs2", "MyTable2", MAPPING_OPT),
                    "c1=value.f1")
                .build());

    CassandraSinkConfig config = new CassandraSinkConfig(props);
    assertThat(config.getTopicConfigs().size()).isEqualTo(2);
  }

  @Test
  void should_parse_codec_setting_per_topic() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(
                    getTableSettingPath(
                        "org.datastax.init.event.history", "MyKs2", "MyTable2", MAPPING_OPT),
                    "c1=value.f1")
                .put(getTableSettingPath("t1", "MyKs2", "MyTable2", MAPPING_OPT), "c1=value.f1")
                .put("topic.org.datastax.init.event.history.codec.timeZone", "Europe/Warsaw")
                .put("topic.t1.codec.timeZone", "Europe/Warsaw")
                .build());

    CassandraSinkConfig config = new CassandraSinkConfig(props);
    assertThat(config.getTopicConfigs().size()).isEqualTo(2);
  }

  @Test
  void should_not_match_setting_with_extra_suffix() {
    // given
    String settingName = String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", "mapping.extra");
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder().put(settingName, "c1=value.f1").build());
    // when then
    assertThatThrownBy(() -> new CassandraSinkConfig(props))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The setting: "
                + settingName
                + " does not match topic.keyspace.table nor topic.codec regular expression pattern");
  }

  @ParameterizedTest
  @MethodSource("topicKeyspaceTableSettings")
  void should_match_all_topic_keyspace_table_settings(String setting, String value) {
    // given
    String mappingSettingName = String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", "mapping");
    String settingName = String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", setting);
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(settingName, value)
                .put(mappingSettingName, "c1=value.f1")
                .build());
    // when then
    assertThatCode(() -> new CassandraSinkConfig(props)).doesNotThrowAnyException();
  }

  private static Stream<? extends Arguments> topicKeyspaceTableSettings() {
    return Stream.of(
        Arguments.of("consistencyLevel", "ANY"),
        Arguments.of("ttl", "1"),
        Arguments.of("nullToUnset", "true"),
        Arguments.of("deletesEnabled", "false"),
        Arguments.of("ttlTimeUnit", "SECONDS"),
        Arguments.of("timestampTimeUnit", "SECONDS"));
  }

  @Test
  void should_allow_query_setting_for_topic_keyspace_table() {
    // given
    String mappingSettingName = String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", "mapping");
    String querySettingName = String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", "query");
    String deletesEnabledSettingName =
        String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", "deletesEnabled");

    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(querySettingName, "query")
                .put(mappingSettingName, "c1=value.f1")
                // deletes needs to be disabled for query parameter
                .put(deletesEnabledSettingName, "false")
                .build());
    // when then
    assertThatCode(() -> new CassandraSinkConfig(props)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("topicKeyspaceTableCodecSettings")
  void should_match_all_topic_keyspace_table_codec_settings(String setting, String value) {
    // given
    String mappingSettingName = String.format("topic.%s.%s.%s.%s", "t1", "ks", "tb", "mapping");
    String settingName = String.format("topic.%s.codec.%s", "t1", setting);
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(settingName, value)
                .put(mappingSettingName, "c1=value.c1")
                .build());
    // when then
    assertThatCode(() -> new CassandraSinkConfig(props)).doesNotThrowAnyException();
  }

  private static Stream<? extends Arguments> topicKeyspaceTableCodecSettings() {
    return Stream.of(
        Arguments.of("locale", "locale"),
        Arguments.of("timeZone", "UK"),
        Arguments.of("timestamp", "some_timestamp"),
        Arguments.of("date", "some_date"),
        Arguments.of("time", "some_time"),
        Arguments.of("unit", "SECONDS"));
  }

  @Test
  void should_fill_with_default_metrics_settings_if_jmx_enabled() {
    // given
    Map<String, String> props =
        Maps.newHashMap(ImmutableMap.<String, String>builder().put("jmx", "true").build());
    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(props);

    // then
    assertThat(
            cassandraSinkConfig
                .getJavaDriverSettings()
                .get(withDriverPrefix(METRICS_SESSION_ENABLED) + ".0"))
        .isEqualTo("cql-requests");
    assertThat(
            cassandraSinkConfig
                .getJavaDriverSettings()
                .get(withDriverPrefix(METRICS_SESSION_ENABLED) + ".1"))
        .isEqualTo("cql-client-timeouts");
    assertThat(
            cassandraSinkConfig
                .getJavaDriverSettings()
                .get(withDriverPrefix(METRICS_SESSION_CQL_REQUESTS_INTERVAL)))
        .isEqualTo(METRICS_INTERVAL_DEFAULT);
  }

  @Test
  void should_override_default_metrics_settings_if_jmx_and_metrics_settings_are_provided_enabled() {
    // given
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put("jmx", "true")
                .put(withDriverPrefix(METRICS_SESSION_ENABLED) + ".0", "bytes-sent")
                .put(withDriverPrefix(METRICS_SESSION_CQL_REQUESTS_INTERVAL), "5 seconds")
                .build());
    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(props);

    // then
    assertThat(
            cassandraSinkConfig
                .getJavaDriverSettings()
                .get(withDriverPrefix(METRICS_SESSION_ENABLED) + ".0"))
        .isEqualTo("bytes-sent");
    assertThat(
            cassandraSinkConfig
                .getJavaDriverSettings()
                .get(withDriverPrefix(METRICS_SESSION_CQL_REQUESTS_INTERVAL)))
        .isEqualTo("5 seconds");
  }

  @ParameterizedTest
  @MethodSource("deprecatedSettingsProvider")
  void should_handle_deprecated_settings(
      Map<String, String> inputSettings, String driverSettingName, String expected) {
    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(inputSettings);

    // then
    assertThat(cassandraSinkConfig.getJavaDriverSettings().get(driverSettingName))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("javaDriverSettingProvider")
  void should_use_java_driver_setting(
      Map<String, String> inputSettings, String driverSettingName, String expected) {
    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(inputSettings);

    // then
    assertThat(cassandraSinkConfig.getJavaDriverSettings().get(driverSettingName))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("defaultSettingProvider")
  void should_set_default_driver_setting(String driverSettingName, String expectedDefault) {
    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(Collections.emptyMap());

    // then
    assertThat(cassandraSinkConfig.getJavaDriverSettings().get(driverSettingName))
        .isEqualTo(expectedDefault);
  }

  @Test
  void should_unpack_base64_secureBundle_legacy_name() throws Exception {
    should_unpack_base64_file(
        SECURE_CONNECT_BUNDLE_OPT,
        (CassandraSinkConfig config) -> {
          assertThat(config.isCloud()).isEqualTo(true);
          return config.getJavaDriverSettings().get(SECURE_CONNECT_BUNDLE_DRIVER_SETTING);
        });
  }

  @Test
  void should_unpack_base64_secureBundle() throws Exception {
    should_unpack_base64_file(
        SECURE_CONNECT_BUNDLE_DRIVER_SETTING,
        (CassandraSinkConfig config) -> {
          assertThat(config.isCloud()).isEqualTo(true);
          return config.getJavaDriverSettings().get(SECURE_CONNECT_BUNDLE_DRIVER_SETTING);
        });
  }

  @Test
  void should_unpack_base64_ssl_keystore() throws Exception {
    should_unpack_base64_file(
        KEYSTORE_PATH_OPT,
        (CassandraSinkConfig config) -> {
          return config.getSslConfig().getKeystorePath().toString();
        });
  }

  @Test
  void should_unpack_base64_ssl_trustore() throws Exception {
    should_unpack_base64_file(
        TRUSTSTORE_PATH_OPT,
        (CassandraSinkConfig config) -> {
          return config.getSslConfig().getTruststorePath().toString();
        });
  }

  @Test
  void should_unpack_base64_ssl_openssl_private_key() throws Exception {
    should_unpack_base64_file(
        OPENSSL_PRIVATE_KEY_OPT,
        (CassandraSinkConfig config) -> {
          return config.getSslConfig().getOpenSslPrivateKey().toString();
        });
  }

  @Test
  void should_unpack_base64_ssl_openssl_cert_chain() throws Exception {
    should_unpack_base64_file(
        OPENSSL_KEY_CERT_CHAIN_OPT,
        (CassandraSinkConfig config) -> {
          return config.getSslConfig().getOpenSslKeyCertChain().toString();
        });
  }

  @Test
  void should_unpack_base64_auth_keytab() throws Exception {
    should_unpack_base64_file(
        KEYTAB_OPT,
        (CassandraSinkConfig config) -> {
          return config.getAuthenticatorConfig().getKeyTabPath().toString();
        });
  }

  void should_unpack_base64_file(
      String entryName, Function<CassandraSinkConfig, String> parameterAccessor) throws Exception {

    byte[] zipFileContents = "foo".getBytes(UTF_8);
    String encoded = "base64:" + Base64.getEncoder().encodeToString(zipFileContents);
    Map<String, String> inputSettings = new HashMap<>();
    inputSettings.put(entryName, encoded);
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(inputSettings);

    String path = parameterAccessor.apply(cassandraSinkConfig);
    File file = new File(path);
    assertThat(file.isFile()).isEqualTo(true);
    Set<PosixFilePermission> posixFilePermissions = Files.getPosixFilePermissions(file.toPath());
    assertThat(posixFilePermissions)
        .contains(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    assertThat(posixFilePermissions.size())
        .as("bad permissions " + posixFilePermissions)
        .isEqualTo(2);
    byte[] content = Files.readAllBytes(file.toPath());
    assertThat(content).isEqualTo(zipFileContents);
  }

  @Test
  void should_transform_list_setting_to_indexed_typesafe_setting() {
    // given
    Map<String, String> connectorSettings = new HashMap<>();
    for (String listSettingName : JAVA_DRIVER_SETTINGS_LIST_TYPE) {
      connectorSettings.put(listSettingName, "a,b");
    }
    // when contact-points are provided the dc must also be provided
    connectorSettings.put(DC_OPT, "dc");

    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(connectorSettings);

    // then
    for (String listSettingName : JAVA_DRIVER_SETTINGS_LIST_TYPE) {
      assertThat(
              cassandraSinkConfig
                  .getJavaDriverSettings()
                  .get(String.format("%s.%s", listSettingName, "0")))
          .isEqualTo("a");
      assertThat(
              cassandraSinkConfig
                  .getJavaDriverSettings()
                  .get(String.format("%s.%s", listSettingName, "1")))
          .isEqualTo("b");
    }
  }

  @ParameterizedTest
  @MethodSource("contactPointsProvider")
  void should_handle_contact_points_provided_using_connector_and_driver_prefix(
      String connectorContactPoints,
      String javaDriverPrefixContactPoints,
      List<String> expected,
      Map<String, String> expectedDriverSettings) {
    // given
    Map<String, String> connectorSettings = new HashMap<>();
    if (connectorContactPoints != null) {
      connectorSettings.put(CONTACT_POINTS_OPT, connectorContactPoints);
    }
    if (javaDriverPrefixContactPoints != null) {
      connectorSettings.put(CONTACT_POINTS_DRIVER_SETTINGS, javaDriverPrefixContactPoints);
    }
    connectorSettings.put(LOCAL_DC_DRIVER_SETTING, "localDc");

    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(connectorSettings);

    // then
    assertThat(cassandraSinkConfig.getContactPoints()).isEqualTo(expected);
    for (Map.Entry<String, String> entry : expectedDriverSettings.entrySet()) {
      assertThat(cassandraSinkConfig.getJavaDriverSettings()).contains(entry);
    }
  }

  private static Stream<? extends Arguments> contactPointsProvider() {
    return Stream.of(
        Arguments.of("a, b", null, ImmutableList.of("a", "b"), Collections.emptyMap()),
        Arguments.of("a, b", "c", ImmutableList.of("a", "b"), Collections.emptyMap()),
        Arguments.of(
            null,
            " c, d",
            Collections.emptyList(), // setting provided with datastax-java-driver
            // prefix should not be returned by the getContactPoints() method
            ImmutableMap.of(
                CONTACT_POINTS_DRIVER_SETTINGS + ".0",
                "c",
                CONTACT_POINTS_DRIVER_SETTINGS + ".1",
                "d")) // pass cp setting to the driver directly
        );
  }

  private static Stream<? extends Arguments> defaultSettingProvider() {
    return Stream.of(
        Arguments.of(QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING, QUERY_EXECUTION_TIMEOUT_DEFAULT),
        Arguments.of(METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS, METRICS_HIGHEST_LATENCY_DEFAULT),
        Arguments.of(CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING, CONNECTION_POOL_LOCAL_SIZE_DEFAULT),
        Arguments.of(LOCAL_DC_DRIVER_SETTING, null),
        Arguments.of(COMPRESSION_DRIVER_SETTING, COMPRESSION_DEFAULT),
        Arguments.of(SECURE_CONNECT_BUNDLE_DRIVER_SETTING, null));
  }

  private static Stream<? extends Arguments> deprecatedSettingsProvider() {
    return Stream.of(
        Arguments.of(
            ImmutableMap.of(
                QUERY_EXECUTION_TIMEOUT_OPT, "10", QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING, "100"),
            QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING,
            "10 seconds"),
        Arguments.of(
            ImmutableMap.of(QUERY_EXECUTION_TIMEOUT_OPT, "10"),
            QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING,
            "10 seconds"),
        Arguments.of(
            ImmutableMap.of(
                METRICS_HIGHEST_LATENCY_OPT, "10", METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS, "100"),
            METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS,
            "10 seconds"),
        Arguments.of(
            ImmutableMap.of(METRICS_HIGHEST_LATENCY_OPT, "10"),
            METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS,
            "10 seconds"),
        Arguments.of(
            ImmutableMap.of(
                CONNECTION_POOL_LOCAL_SIZE, "10", CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING, "100"),
            CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING,
            "10"),
        Arguments.of(
            ImmutableMap.of(CONNECTION_POOL_LOCAL_SIZE, "10"),
            CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING,
            "10"),
        Arguments.of(
            ImmutableMap.of(DC_OPT, "dc", LOCAL_DC_DRIVER_SETTING, "dc_suppressed"),
            LOCAL_DC_DRIVER_SETTING,
            "dc"),
        Arguments.of(ImmutableMap.of(DC_OPT, "dc"), LOCAL_DC_DRIVER_SETTING, "dc"),
        Arguments.of(
            ImmutableMap.of(COMPRESSION_OPT, "lz4", COMPRESSION_DRIVER_SETTING, "none"),
            COMPRESSION_DRIVER_SETTING,
            "lz4"),
        Arguments.of(ImmutableMap.of(COMPRESSION_OPT, "lz4"), COMPRESSION_DRIVER_SETTING, "lz4"),
        Arguments.of(
            ImmutableMap.of(
                SECURE_CONNECT_BUNDLE_OPT, "path", SECURE_CONNECT_BUNDLE_DRIVER_SETTING, "path2"),
            SECURE_CONNECT_BUNDLE_DRIVER_SETTING,
            "path"),
        Arguments.of(
            ImmutableMap.of(SECURE_CONNECT_BUNDLE_OPT, "path"),
            SECURE_CONNECT_BUNDLE_DRIVER_SETTING,
            "path"));
  }

  private static Stream<? extends Arguments> javaDriverSettingProvider() {
    return Stream.of(
        Arguments.of(
            ImmutableMap.of(QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING, "100 seconds"),
            QUERY_EXECUTION_TIMEOUT_DRIVER_SETTING,
            "100 seconds"),
        Arguments.of(
            ImmutableMap.of(METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS, "100 seconds"),
            METRICS_HIGHEST_LATENCY_DRIVER_SETTINGS,
            "100 seconds"),
        Arguments.of(
            ImmutableMap.of(CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING, "100"),
            CONNECTION_POOL_LOCAL_SIZE_DRIVER_SETTING,
            "100"),
        Arguments.of(ImmutableMap.of(LOCAL_DC_DRIVER_SETTING, "dc"), LOCAL_DC_DRIVER_SETTING, "dc"),
        Arguments.of(
            ImmutableMap.of(COMPRESSION_DRIVER_SETTING, "lz4"), COMPRESSION_DRIVER_SETTING, "lz4"),
        Arguments.of(
            ImmutableMap.of(SECURE_CONNECT_BUNDLE_DRIVER_SETTING, "path"),
            SECURE_CONNECT_BUNDLE_DRIVER_SETTING,
            "path"));
  }

  @ParameterizedTest
  @MethodSource
  void should_handle_ignore_errors(
      String ignoreErrors,
      IgnoreErrorsPolicy expected,
      String expectedDeprecationLogWarning,
      LogInterceptor logs) {
    // given
    Map<String, String> connectorSettings = new HashMap<>();
    connectorSettings.put(IGNORE_ERRORS, ignoreErrors);

    // when
    CassandraSinkConfig cassandraSinkConfig = new CassandraSinkConfig(connectorSettings);

    // then
    assertThat(cassandraSinkConfig.getIgnoreErrors()).isEqualTo(expected);
    if (expectedDeprecationLogWarning != null) {
      assertThat(logs).hasMessageContaining(expectedDeprecationLogWarning);
    }
  }

  private static Stream<Arguments> should_handle_ignore_errors() {
    return Stream.of(
        Arguments.of("ALL", IgnoreErrorsPolicy.ALL, null),
        Arguments.of("all", IgnoreErrorsPolicy.ALL, null),
        Arguments.of("All", IgnoreErrorsPolicy.ALL, null),
        Arguments.of("NONE", IgnoreErrorsPolicy.NONE, null),
        Arguments.of("none", IgnoreErrorsPolicy.NONE, null),
        Arguments.of("None", IgnoreErrorsPolicy.NONE, null),
        Arguments.of("DRIVER", IgnoreErrorsPolicy.DRIVER, null),
        Arguments.of("driver", IgnoreErrorsPolicy.DRIVER, null),
        Arguments.of("Driver", IgnoreErrorsPolicy.DRIVER, null),
        // deprecated settings
        Arguments.of(
            "true",
            IgnoreErrorsPolicy.DRIVER,
            "Setting ignoreErrors=true is deprecated, please replace with ignoreErrors=Driver"),
        Arguments.of(
            "false",
            IgnoreErrorsPolicy.NONE,
            "Setting ignoreErrors=false is deprecated, please replace with ignoreErrors=None"));
  }

  private void assertTopic(
      String keyspace,
      String table,
      Map<CqlIdentifier, CqlIdentifier> mapping,
      TopicConfig config) {
    TableConfig tableConfig = config.getTableConfigs().iterator().next();
    assertThat(tableConfig.getKeyspace()).isEqualTo(CqlIdentifier.fromInternal(keyspace));
    assertThat(tableConfig.getTable()).isEqualTo(CqlIdentifier.fromInternal(table));
    assertThat(tableConfig.getMapping()).isEqualTo(mapping);
  }
}
