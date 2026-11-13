package com.digilocker.digilockerbackend.service;

import com.digilocker.digilockerbackend.dto.response.CitizenDocumentListResponse;
import com.digilocker.digilockerbackend.dto.response.CitizenDocumentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CitizenDocumentService {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public CitizenDocumentService(
            DynamoDbClient dynamoDbClient,
            @Value("${app.aws.dynamodb.table-name}") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public CitizenDocumentListResponse getDocuments(String citizenSub) {
        QueryRequest query = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :citizen AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":citizen", AttributeValue.builder().s("CITIZEN#" + citizenSub).build(),
                        ":prefix", AttributeValue.builder().s("DOC#").build()
                ))
                .build();

        List<CitizenDocumentResponse> documents = dynamoDbClient.queryPaginator(query)
                .items().stream()
                .map(this::toDocumentResponse)
                .toList();

        CitizenDocumentListResponse response = new CitizenDocumentListResponse();
        response.setDocuments(documents);
        response.setTotalCount(documents.size());
        return response;
    }

    private CitizenDocumentResponse toDocumentResponse(Map<String, AttributeValue> item) {
        CitizenDocumentResponse document = new CitizenDocumentResponse();
        document.setDocumentId(item.get("documentId").s());
        document.setDocumentType(item.get("documentType").s());
        document.setPassingYear(Integer.valueOf(item.get("passingYear").s()));
        document.setIssuerId(item.get("issuerId").s());
        document.setDocumentUri(item.get("documentUri").s());
        document.setClaimedAt(Instant.parse(item.get("claimedAt").s()));
        return document;
    }
}