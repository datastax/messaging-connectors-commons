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
import com.datastax.oss.common.sink.AbstractSinkTask;
import com.datastax.oss.common.sink.config.CassandraSinkConfig.IgnoreErrorsPolicy;
import com.datastax.oss.common.sink.state.InstanceState;
import com.datastax.oss.common.sink.util.SinkUtil;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.apache.pulsar.client.api.schema.Field;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.core.Sink;
import org.apache.pulsar.io.core.SinkContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraSinkTask<T> implements Sink<T> {

  private static final String APPLICATION_NAME = "DataStax Pulsar Connector";
  private static final Logger log = LoggerFactory.getLogger(CassandraSinkTask.class);
  protected AbstractSinkTask processor;
  private String version;
  private final LocalSchemaRegistry schemaRegistry = new LocalSchemaRegistry();

  public CassandraSinkTask() {
    processor =
        new AbstractSinkTask() {
          @Override
          protected void handleFailure(
              AbstractSinkRecord record, Throwable e, String cql, Runnable failCounter) {
            PulsarSinkRecordImpl impl = (PulsarSinkRecordImpl) record;

            IgnoreErrorsPolicy ignoreErrors =
                processor.getInstanceState().getConfig().getIgnoreErrors();
            boolean driverFailure = cql != null;
            boolean ignore;
            if (ignoreErrors == IgnoreErrorsPolicy.NONE
                || (ignoreErrors == IgnoreErrorsPolicy.DRIVER && !driverFailure)) {
              ignore = false;

            } else {
              ignore = true;
            }

            failCounter.run();

            if (driverFailure) {
              log.warn(
                  "Error inserting/updating row for Pulsar record {}: {}\n   statement: {}}",
                  record,
                  e.getMessage(),
                  cql);
            } else {
              log.warn("Error decoding/mapping Pulsar record {}: {}", impl, e.getMessage());
            }
            if (log.isDebugEnabled()) {
              log.debug("Details of the error", e);
            }
            if (!ignore) {
              impl.getRecord().fail();
            } else {
              impl.getRecord().ack();
            }
          }

          @Override
          protected void handleSuccess(AbstractSinkRecord record) {
            PulsarSinkRecordImpl impl = (PulsarSinkRecordImpl) record;
            log.debug("ack record {}", impl);
            impl.getRecord().ack();
          }

          @Override
          public String version() {
            return getVersion();
          }

          @Override
          public String applicationName() {
            return APPLICATION_NAME;
          }
        };
  }

  @Override
  public void open(Map<String, Object> cfg, SinkContext sc) {
    log.info("start {}, config {}", getClass().getName(), cfg);
    try {
      Map<String, String> processorConfig = ConfigUtil.flatString(cfg);
      processorConfig.put(SinkUtil.NAME_OPT, sc.getSinkName());
      processor.start(processorConfig);
      log.debug("started {}", getClass().getName(), processorConfig);
    } catch (Throwable ex) {
      log.error("initialization error", ex);
      close();
      throw ex;
    }
  }

  @Override
  public void write(Record<T> record) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("write {}", record);
      Object rawvalue = record.getValue();
      if (rawvalue instanceof GenericRecord) {
        GenericRecord value = (GenericRecord) rawvalue;
        for (Field field : value.getFields()) {
          Object v = value.getField(field);
          String clazz = v != null ? v.getClass().toGenericString() : "";
          log.debug("field {} value {} class {}", field, v, clazz);
        }
      } else {
        log.debug("write {}", rawvalue);
      }
    }

    PulsarSinkRecordImpl pulsarSinkRecordImpl = buildRecordImpl(record);
    processor.put(Collections.singleton(pulsarSinkRecordImpl));
  }

  PulsarSinkRecordImpl buildRecordImpl(Record<?> record) {
    // TODO: batch records, in Kafka the system sends batches, here we
    // are procesing only one record at a time
    PulsarSchema schema = schemaRegistry.ensureAndUpdateSchema(record);
    PulsarSinkRecordImpl pulsarSinkRecordImpl =
        new PulsarSinkRecordImpl(record, schema, schemaRegistry);
    return pulsarSinkRecordImpl;
  }

  @Override
  public void close() {
    if (processor != null) {
      processor.stop();
    }
  }

  private String getVersion() {
    if (version != null) {
      return version;
    }
    synchronized (this) {
      if (version != null) {
        return version;
      }

      // Get the version from version.txt.
      version = "UNKNOWN";
      try (InputStream versionStream =
          CassandraSinkTask.class.getResourceAsStream(
              "/com/datastax/oss/pulsar/sink/version.txt")) {
        if (versionStream != null) {
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(versionStream, StandardCharsets.UTF_8));
          version = reader.readLine();
        }
      } catch (Exception e) {
        // swallow
      }
      return version;
    }
  }

  public AbstractSinkTask getProcessor() {
    return processor;
  }

  public InstanceState getInstanceState() {
    return processor.getInstanceState();
  }

  public LocalSchemaRegistry getSchemaRegistry() {
    return schemaRegistry;
  }
}
