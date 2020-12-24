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
package com.datastax.oss.sink.pulsar;

import com.datastax.oss.common.sink.AbstractSinkRecord;
import com.datastax.oss.common.sink.AbstractSinkRecordHeader;
import java.util.Collections;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.functions.api.Record;

/** @author enrico.olivelli */
public class PulsarSinkRecordImpl implements AbstractSinkRecord {
  private final Record<?> record;
  private final PulsarSchema schema;
  private final LocalSchemaRegistry schemaRegistry;

  public PulsarSinkRecordImpl(
      Record<?> record, PulsarSchema schema, LocalSchemaRegistry schemaRegistry) {
    this.record = record;
    this.schema = schema;
    this.schemaRegistry = schemaRegistry;
  }

  @Override
  public Iterable<AbstractSinkRecordHeader> headers() {
    return Collections.emptyList();
  }

  @Override
  public Object key() {
    return record.getKey().orElse(null);
  }

  @Override
  public Object value() {
    if (record.getValue() instanceof GenericRecord) {
      return PulsarStruct.ofRecord((Record<GenericRecord>) record, schemaRegistry);
    } else {
      return record.getValue();
    }
  }

  @Override
  public Long timestamp() {
    return record.getEventTime().orElse(null);
  }

  @Override
  public String topic() {
    return shortTopic(record);
  }

  public Record<?> getRecord() {
    return record;
  }

  public static String shortTopic(Record<?> record) {
    // persistent://tenant/namespace/topicname
    if (!record.getTopicName().isPresent()) {
      return null;
    }
    String longName = record.getTopicName().get();
    int lastSlash = longName.lastIndexOf('/');
    if (lastSlash > 0 && lastSlash <= longName.length()) {
      return longName.substring(lastSlash + 1);
    }
    throw new IllegalArgumentException("Unexpected topic name " + longName);
  }

  @Override
  public String toString() {
    return "PulsarSinkRecord{" + record + '}';
  }
}
