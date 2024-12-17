/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ccr.rest;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.Build;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShardChangesRestIT extends ESRestTestCase {
    private static final String CCR_SHARD_CHANGES_ENDPOINT = "/%s/ccr/shard_changes";
    private static final String BULK_INDEX_ENDPOINT = "/%s/_bulk";

    private static final String[] SHARD_RESPONSE_FIELDS = new String[] {
        "took_in_millis",
        "operations",
        "shard_id",
        "index",
        "settings_version",
        "max_seq_no_of_updates_or_deletes",
        "number_of_operations",
        "mapping_version",
        "aliases_version",
        "max_seq_no",
        "global_checkpoint" };
    private static final String[] NAMES = { "skywalker", "leia", "obi-wan", "yoda", "chewbacca", "r2-d2", "c-3po", "darth-vader" };
    @ClassRule
    public static ElasticsearchCluster cluster = ElasticsearchCluster.local()
        .distribution(DistributionType.DEFAULT)
        .setting("xpack.security.enabled", "false")
        .setting("xpack.license.self_generated.type", "trial")
        .build();

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    @Before
    public void assumeSnapshotBuild() {
        assumeTrue("/{index}/ccr/shard_changes endpoint only available in snapshot builds", Build.current().isSnapshot());
    }

    public void testShardChangesNoOperation() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(
            indexName,
            Settings.builder()
                .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
                .put(IndexSettings.INDEX_TRANSLOG_SYNC_INTERVAL_SETTING.getKey(), "1s")
                .build()
        );
        assertTrue(indexExists(indexName));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        assertOK(client().performRequest(shardChangesRequest));
    }

    public void testShardChangesDefaultParams() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        final Settings settings = Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            .put(IndexSettings.INDEX_TRANSLOG_SYNC_INTERVAL_SETTING.getKey(), "1s")
            .build();
        final String mappings = """
               {
                 "properties": {
                   "name": {
                     "type": "keyword"
                   }
                 }
               }
            """;
        createIndex(indexName, settings, mappings);
        assertTrue(indexExists(indexName));

        assertOK(client().performRequest(bulkRequest(indexName, randomIntBetween(10, 20))));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        final Response response = client().performRequest(shardChangesRequest);
        assertOK(response);
        assertShardChangesResponse(
            XContentHelper.convertToMap(JsonXContent.jsonXContent, EntityUtils.toString(response.getEntity()), false)
        );
    }

    public void testShardChangesWithAllParameters() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(
            indexName,
            Settings.builder()
                .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
                .put(IndexSettings.INDEX_TRANSLOG_SYNC_INTERVAL_SETTING.getKey(), "1s")
                .build()
        );
        assertTrue(indexExists(indexName));

        assertOK(client().performRequest(bulkRequest(indexName, randomIntBetween(100, 200))));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        shardChangesRequest.addParameter("from_seq_no", "0");
        shardChangesRequest.addParameter("max_operations_count", "1");
        shardChangesRequest.addParameter("poll_timeout", "10s");
        shardChangesRequest.addParameter("max_batch_size", "1MB");

        final Response response = client().performRequest(shardChangesRequest);
        assertOK(response);
        assertShardChangesResponse(
            XContentHelper.convertToMap(JsonXContent.jsonXContent, EntityUtils.toString(response.getEntity()), false)
        );
    }

    public void testShardChangesMultipleRequests() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(
            indexName,
            Settings.builder()
                .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
                .put(IndexSettings.INDEX_TRANSLOG_SYNC_INTERVAL_SETTING.getKey(), "1s")
                .build()
        );
        assertTrue(indexExists(indexName));

        assertOK(client().performRequest(bulkRequest(indexName, randomIntBetween(100, 200))));

        final Request firstRequest = new Request("GET", shardChangesEndpoint(indexName));
        firstRequest.addParameter("from_seq_no", "0");
        firstRequest.addParameter("max_operations_count", "10");
        firstRequest.addParameter("poll_timeout", "10s");
        firstRequest.addParameter("max_batch_size", "1MB");

        final Response firstResponse = client().performRequest(firstRequest);
        assertOK(firstResponse);
        assertShardChangesResponse(
            XContentHelper.convertToMap(JsonXContent.jsonXContent, EntityUtils.toString(firstResponse.getEntity()), false)
        );

        final Request secondRequest = new Request("GET", shardChangesEndpoint(indexName));
        secondRequest.addParameter("from_seq_no", "10");
        secondRequest.addParameter("max_operations_count", "10");
        secondRequest.addParameter("poll_timeout", "10s");
        secondRequest.addParameter("max_batch_size", "1MB");

        final Response secondResponse = client().performRequest(secondRequest);
        assertOK(secondResponse);
        assertShardChangesResponse(
            XContentHelper.convertToMap(JsonXContent.jsonXContent, EntityUtils.toString(secondResponse.getEntity()), false)
        );
    }

    public void testShardChangesInvalidFromSeqNo() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(indexName);
        assertTrue(indexExists(indexName));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        shardChangesRequest.addParameter("from_seq_no", "-1");
        final ResponseException ex = assertThrows(ResponseException.class, () -> client().performRequest(shardChangesRequest));
        assertResponseException(ex, RestStatus.BAD_REQUEST, "Validation Failed: 1: fromSeqNo [-1] cannot be lower than 0");
    }

    public void testShardChangesInvalidMaxOperationsCount() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(indexName);
        assertTrue(indexExists(indexName));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        shardChangesRequest.addParameter("max_operations_count", "-1");
        final ResponseException ex = assertThrows(ResponseException.class, () -> client().performRequest(shardChangesRequest));
        assertResponseException(ex, RestStatus.BAD_REQUEST, "Validation Failed: 1: maxOperationCount [-1] cannot be lower than 0");
    }

    public void testShardChangesNegativePollTimeout() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(indexName);
        assertTrue(indexExists(indexName));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        shardChangesRequest.addParameter("poll_timeout", "-1s");
        assertOK(client().performRequest(shardChangesRequest));
    }

    public void testShardChangesInvalidMaxBatchSize() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        createIndex(indexName);
        assertTrue(indexExists(indexName));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        shardChangesRequest.addParameter("max_batch_size", "-1MB");
        final ResponseException ex = assertThrows(ResponseException.class, () -> client().performRequest(shardChangesRequest));
        assertResponseException(
            ex,
            RestStatus.BAD_REQUEST,
            "failed to parse setting [max_batch_size] with value [-1MB] as a size in bytes"
        );
    }

    public void testShardChangesMissingIndex() throws IOException {
        final String indexName = randomAlphanumericOfLength(10).toLowerCase(Locale.ROOT);
        assertFalse(indexExists(indexName));

        final Request shardChangesRequest = new Request("GET", shardChangesEndpoint(indexName));
        final ResponseException ex = assertThrows(ResponseException.class, () -> client().performRequest(shardChangesRequest));
        assertResponseException(ex, RestStatus.BAD_REQUEST, "Failed to process shard changes for index [" + indexName + "]");
    }

    private static Request bulkRequest(final String indexName, int numberOfDocuments) {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < numberOfDocuments; i++) {
            sb.append(String.format(Locale.ROOT, "{ \"index\": { \"_id\": \"%d\" } }\n{ \"name\": \"%s\" }\n", i + 1, randomFrom(NAMES)));
        }

        final Request request = new Request("POST", bulkEndpoint(indexName));
        request.setJsonEntity(sb.toString());
        request.addParameter("refresh", "true");
        return request;
    }

    private static String shardChangesEndpoint(final String indexName) {
        return String.format(Locale.ROOT, CCR_SHARD_CHANGES_ENDPOINT, indexName);
    }

    private static String bulkEndpoint(final String indexName) {
        return String.format(Locale.ROOT, BULK_INDEX_ENDPOINT, indexName);
    }

    private void assertResponseException(final ResponseException ex, final RestStatus restStatus, final String error) {
        assertEquals(restStatus.getStatus(), ex.getResponse().getStatusLine().getStatusCode());
        assertThat(ex.getMessage(), Matchers.containsString(error));
    }

    private void assertShardChangesResponse(final Map<String, Object> shardChangesResponseBody) {
        for (final String fieldName : SHARD_RESPONSE_FIELDS) {
            final Object fieldValue = shardChangesResponseBody.get(fieldName);
            assertNotNull("Field " + fieldName + " is missing or has a null value.", fieldValue);

            if ("operations".equals(fieldName)) {
                if (fieldValue instanceof List<?> operationsList) {
                    assertFalse("Field 'operations' is empty.", operationsList.isEmpty());

                    for (final Object operation : operationsList) {
                        assertNotNull("Operation is null.", operation);
                        if (operation instanceof Map<?, ?> operationMap) {
                            assertNotNull("seq_no is missing in operation.", operationMap.get("seq_no"));
                            assertNotNull("op_type is missing in operation.", operationMap.get("op_type"));
                            assertNotNull("primary_term is missing in operation.", operationMap.get("primary_term"));
                        }
                    }
                }
            }
        }
    }
}