/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.Map.Entry;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

final class InsertSpanName extends ResultSetFutureCall<Void> {
  static class Factory extends DeduplicatingVoidCallFactory<Entry<String, String>> {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(CassandraStorage storage, int indexTtl) {
      super(storage.autocompleteTtl, storage.autocompleteCardinality);
      session = storage.session();
      Insert insertQuery = QueryBuilder.insertInto(Tables.SPAN_NAMES)
        .value("service_name", QueryBuilder.bindMarker("service_name"))
        .value("bucket", 0) // bucket is deprecated on this index
        .value("span_name", QueryBuilder.bindMarker("span_name"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      preparedStatement = session.prepare(insertQuery);
    }

    @Override protected InsertSpanName newCall(Entry<String, String> input) {
      return new InsertSpanName(this, input);
    }
  }

  final Factory factory;
  final Entry<String, String> input;

  InsertSpanName(Factory factory, Entry<String, String> input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("service_name", input.getKey())
      .setString("span_name", input.getValue()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertSpanName");
  }

  @Override public InsertSpanName clone() {
    return new InsertSpanName(factory, input);
  }
}
