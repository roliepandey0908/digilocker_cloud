package com.digilocker.digilockerbackend;

import com.digilocker.digilockerbackend.service.CitizenDocumentService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.paginators.QueryIterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CitizenDocumentServiceTest {

    @Test
    void queriesOnlyDocumentLinksForTheGivenCitizen() {
        DynamoDbClient client = mock(DynamoDbClient.class);

        when(client.queryPaginator(any(QueryRequest.class)))
                .thenAnswer(call -> new QueryIterable(client, call.getArgument(0)));

        when(client.query(any(QueryRequest.class))).thenAnswer(call -> {
            QueryRequest query = call.getArgument(0);
            assertEquals("DigiLockerData", query.tableName());
            assertEquals("PK = :citizen AND begins_with(SK, :prefix)",
                    query.keyConditionExpression());
            assertEquals("CITIZEN#test-citizen",
                    query.expressionAttributeValues().get(":citizen").s());
            assertEquals("DOC#", query.expressionAttributeValues().get(":prefix").s());
            return QueryResponse.builder().build();
        });

        var result = new CitizenDocumentService(client, "DigiLockerData")
                .getDocuments("test-citizen");

        assertTrue(result.getDocuments().isEmpty());
        assertEquals(Integer.valueOf(0), result.getTotalCount());
    }
}