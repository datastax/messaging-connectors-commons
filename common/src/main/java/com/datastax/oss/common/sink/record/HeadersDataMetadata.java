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
package com.datastax.oss.common.sink.record;

import static com.datastax.oss.common.sink.record.StructDataMetadataSupport.getGenericType;

import com.datastax.oss.common.sink.AbstractSinkRecordHeader;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Metadata associated with a {@link StructData}. */
public class HeadersDataMetadata implements RecordMetadata {
  private final Iterable<AbstractSinkRecordHeader> headers;

  public HeadersDataMetadata(Iterable<AbstractSinkRecordHeader> headers) {
    this.headers = headers;
  }

  @Override
  public GenericType<?> getFieldType(@NonNull String field, @NonNull DataType cqlType) {
    for (AbstractSinkRecordHeader h : headers) {
      if (h.key().equals(field)) {
        return getGenericType(h.schema());
      }
    }
    throw new IllegalArgumentException(
        "The field: " + field + " is not present in the record headers: " + headers);
  }
}
