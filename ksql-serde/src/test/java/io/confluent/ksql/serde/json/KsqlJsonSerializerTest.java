/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.serde.json;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.ksql.GenericRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.Before;
import org.junit.Test;

public class KsqlJsonSerializerTest {

  private static final Schema ORDER_SCHEMA = SchemaBuilder.struct()
      .field("ordertime".toUpperCase(), Schema.OPTIONAL_INT64_SCHEMA)
      .field("orderid".toUpperCase(), Schema.OPTIONAL_INT64_SCHEMA)
      .field("itemid".toUpperCase(), Schema.OPTIONAL_STRING_SCHEMA)
      .field("orderunits".toUpperCase(), Schema.OPTIONAL_FLOAT64_SCHEMA)
      .field("arraycol".toUpperCase(),
          SchemaBuilder.array(Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build())
      .field("mapcol".toUpperCase(), SchemaBuilder
          .map(Schema.OPTIONAL_STRING_SCHEMA, Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build())
      .build();

  private static final Schema ADDRESS_SCHEMA = SchemaBuilder.struct()
      .field("NUMBER", Schema.OPTIONAL_INT64_SCHEMA)
      .field("STREET", Schema.OPTIONAL_STRING_SCHEMA)
      .field("CITY", Schema.OPTIONAL_STRING_SCHEMA)
      .field("STATE", Schema.OPTIONAL_STRING_SCHEMA)
      .field("ZIPCODE", Schema.OPTIONAL_INT64_SCHEMA)
      .optional().build();

  private static final Schema CATEGORY_SCHEMA = SchemaBuilder.struct()
      .field("ID", Schema.OPTIONAL_INT64_SCHEMA)
      .field("NAME", Schema.OPTIONAL_STRING_SCHEMA)
      .optional().build();

  private static final Schema ITEM_SCHEMA = SchemaBuilder.struct()
      .field("ITEMID", Schema.OPTIONAL_INT64_SCHEMA)
      .field("NAME", Schema.OPTIONAL_STRING_SCHEMA)
      .field("CATEGORIES", SchemaBuilder.array(CATEGORY_SCHEMA).optional().build())
      .optional().build();

  private static final Schema SCHEMA_WITH_STRUCT = SchemaBuilder.struct()
      .field("ordertime", Schema.OPTIONAL_INT64_SCHEMA)
      .field("orderid", Schema.OPTIONAL_INT64_SCHEMA)
      .field("itemid", ITEM_SCHEMA)
      .field("orderunits", Schema.OPTIONAL_INT32_SCHEMA)
      .field("arraycol", SchemaBuilder
          .array(Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build())
      .field("mapcol", SchemaBuilder
          .map(Schema.OPTIONAL_STRING_SCHEMA, Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build())
      .field("address", ADDRESS_SCHEMA)
      .build();

  private KsqlJsonSerializer serializer;

  @Before
  public void before() {
    serializer = new KsqlJsonSerializer(ORDER_SCHEMA);
  }

  @Test
  public void shouldSerializeRowCorrectly() {
    // Given:
    final GenericRow genericRow = new GenericRow(Arrays.asList(
        1511897796092L,
        1L,
        "item_1",
        10.0,
        Collections.singletonList(100.0),
        Collections.singletonMap("key1", 100.0)
    ));

    // When:
    final byte[] bytes = serializer.serialize("t1", genericRow);

    // Then:
    final String jsonString = new String(bytes, StandardCharsets.UTF_8);
    assertThat(jsonString, equalTo(
        "{"
            + "\"ORDERTIME\":1511897796092,"
            + "\"ORDERID\":1,"
            + "\"ITEMID\":\"item_1\","
            + "\"ORDERUNITS\":10.0,"
            + "\"ARRAYCOL\":[100.0],"
            + "\"MAPCOL\":{\"key1\":100.0}"
            + "}"));
  }

  @Test
  public void shouldSerializeRowWithNull() {
    // Given:
    final GenericRow genericRow = new GenericRow(Arrays.asList(
        1511897796092L,
        1L,
        "item_1",
        10.0,
        null,
        null
    ));

    // When:
    final byte[] bytes = serializer.serialize("t1", genericRow);

    // Then:
    final String jsonString = new String(bytes, StandardCharsets.UTF_8);
    assertThat(jsonString, equalTo(
        "{"
            + "\"ORDERTIME\":1511897796092,"
            + "\"ORDERID\":1,"
            + "\"ITEMID\":\"item_1\","
            + "\"ORDERUNITS\":10.0,"
            + "\"ARRAYCOL\":null,"
            + "\"MAPCOL\":null"
            + "}"));
  }

  @Test
  public void shouldHandleStruct() throws IOException {
    // Given:
    final GenericRow genericRow = buildStructGenericRow();
    serializer = new KsqlJsonSerializer(SCHEMA_WITH_STRUCT);

    // When:
    final byte[] bytes = serializer.serialize("", genericRow);

    // Then:
    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode jsonNode = objectMapper.readTree(bytes);
    assertThat(jsonNode.size(), equalTo(7));
    assertThat(jsonNode.get("ordertime").asLong(), equalTo(genericRow.getColumns().get(0)));
    assertThat(jsonNode.get("itemid").get("NAME").asText(), equalTo("Item_10"));
  }

  private static GenericRow buildStructGenericRow() {
    final List<Object> columns = new ArrayList<>();
    // ordertime
    columns.add(1234567L);
    //orderid
    columns.add(10L);
    //itemid
    final Struct category = new Struct(CATEGORY_SCHEMA);
    category.put("ID", Math.random() > 0.5 ? 1L : 2L);
    category.put("NAME", Math.random() > 0.5 ? "Produce" : "Food");

    final Struct item = new Struct(ITEM_SCHEMA);
    item.put("ITEMID", 10L);
    item.put("NAME", "Item_10");
    item.put("CATEGORIES", Collections.singletonList(category));

    columns.add(item);

    //units
    columns.add(10);

    columns.add(Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));

    final Map<String, Double> map = new HashMap<>();
    map.put("key1", 10.0);
    map.put("key2", 20.0);
    map.put("key3", 30.0);
    columns.add(map);

    final Struct address = new Struct(ADDRESS_SCHEMA);
    address.put("NUMBER", 101L);
    address.put("STREET", "University Ave.");
    address.put("CITY", "Palo Alto");
    address.put("STATE", "CA");
    address.put("ZIPCODE", 94301L);

    columns.add(address);

    return new GenericRow(columns);
  }
}