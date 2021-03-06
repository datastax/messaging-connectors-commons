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

import com.datastax.oss.common.sink.AbstractSinkRecord;
import com.datastax.oss.driver.api.core.cql.BoundStatement;

/** Simple container class to hold a SinkRecord and its associated BoundStatement. */
public class RecordAndStatement {
  private final AbstractSinkRecord record;
  private final String keyspaceAndTable;
  private final BoundStatement statement;

  public RecordAndStatement(
      AbstractSinkRecord record, String keyspaceAndTable, BoundStatement statement) {
    this.record = record;
    this.keyspaceAndTable = keyspaceAndTable;
    this.statement = statement;
  }

  public AbstractSinkRecord getRecord() {
    return record;
  }

  public String getKeyspaceAndTable() {
    return keyspaceAndTable;
  }

  public BoundStatement getStatement() {
    return statement;
  }
}
