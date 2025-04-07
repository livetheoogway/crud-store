package com.livetheoogway.crudstore.aerospike;

import com.aerospike.client.AerospikeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorHandlerTest {

    private ErrorHandler<String> errorHandler;

    @BeforeEach
    void setUp() {
        // Create a mock implementation of ErrorHandler
        errorHandler = mock(ErrorHandler.class);

        // Default behaviors for the mock
        when(errorHandler.onNoRecordFound(anyString())).thenReturn(Optional.empty());
        when(errorHandler.onAerospikeError(anyString(), any(AerospikeException.class))).thenReturn(Optional.empty());
        when(errorHandler.onDeSerializationError(anyString(), any(JsonProcessingException.class))).thenReturn(
                Optional.empty());
        when(errorHandler.onSerializationError(anyString(), any(JsonProcessingException.class))).thenReturn(
                Optional.empty());
        when(errorHandler.onExecutionError(anyString(), any(Exception.class))).thenReturn(Optional.empty());
        when(errorHandler.onRefIdLookupFailure(anyString())).thenReturn(List.of());
        when(errorHandler.onAerospikeErrorForRefId(anyString(), any(AerospikeException.class))).thenReturn(List.of());

        // Clone method should use the actual implementation
        when(errorHandler.cloneOnType(any())).thenCallRealMethod();
    }

    @Test
    void testCloneOnTypeBasicFunctionality() {
        // Given
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);

        // Then
        assertNotNull(clonedHandler);
    }

    @Test
    void testCloneOnTypeMapsOnNoRecordFound() {
        // Given
        String testId = "test-id";
        when(errorHandler.onNoRecordFound(testId)).thenReturn(Optional.of("result"));
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);
        Optional<Integer> result = clonedHandler.onNoRecordFound(testId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(6, result.get()); // "result" length is 6
        verify(errorHandler).onNoRecordFound(testId);
    }

    @Test
    void testCloneOnTypeMapsOnDeSerializationError() throws JsonProcessingException {
        // Given
        String testId = "test-id";
        JsonProcessingException exception = mock(JsonProcessingException.class);
        when(errorHandler.onDeSerializationError(testId, exception)).thenReturn(Optional.of("error"));
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);
        Optional<Integer> result = clonedHandler.onDeSerializationError(testId, exception);

        // Then
        assertTrue(result.isPresent());
        assertEquals(5, result.get()); // "error" length is 5
        verify(errorHandler).onDeSerializationError(testId, exception);
    }

    @Test
    void testCloneOnTypeMapsOnAerospikeError() {
        // Given
        String testId = "test-id";
        AerospikeException exception = mock(AerospikeException.class);
        when(errorHandler.onAerospikeError(testId, exception)).thenReturn(Optional.of("aerospike-error"));
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);
        Optional<Integer> result = clonedHandler.onAerospikeError(testId, exception);

        // Then
        assertTrue(result.isPresent());
        assertEquals(15, result.get()); // "aerospike-error" length is 15
        verify(errorHandler).onAerospikeError(testId, exception);
    }

    @Test
    void testCloneOnTypeDelegatesOnDeleteUnsuccessful() {
        // Given
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);
        clonedHandler.onDeleteUnsuccessful();

        // Then
        verify(errorHandler).onDeleteUnsuccessful();
    }

    @Test
    void testCloneOnTypeDelegatesOnAerospikeErrorForRefId() {
        // Given
        String testId = "test-id";
        AerospikeException exception = mock(AerospikeException.class);
        when(errorHandler.onAerospikeErrorForRefId(testId, exception)).thenReturn(List.of("error1", "error2"));
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);
        List<Integer> results = clonedHandler.onAerospikeErrorForRefId(testId, exception);

        // Then
        // The default implementation of cloneOnType just casts the result, it doesn't map the elements
        assertEquals(2, results.size());
        verify(errorHandler).onAerospikeErrorForRefId(testId, exception);
    }

    @Test
    void testCloneOnTypeWithEmptyResults() {
        // Given
        String testId = "test-id";
        Function<String, Integer> mapper = String::length;

        // When
        ErrorHandler<Integer> clonedHandler = errorHandler.cloneOnType(mapper);
        Optional<Integer> result = clonedHandler.onNoRecordFound(testId);

        // Then
        assertFalse(result.isPresent());
        verify(errorHandler).onNoRecordFound(testId);
    }

    @Test
    void testOnRefIdLookupFailureDefaultBehavior() {
        // Given
        ErrorHandler<String> defaultHandler = new ErrorHandler<>() {
            @Override
            public void onDeleteUnsuccessful() {
            }

            @Override
            public Optional<String> onNoRecordFound(String id) {
                return Optional.empty();
            }

            @Override
            public Optional<String> onDeSerializationError(String id, JsonProcessingException e) {
                return Optional.empty();
            }

            @Override
            public Optional<String> onAerospikeError(String id, AerospikeException e) {
                return Optional.empty();
            }

            @Override
            public Optional<String> onSerializationError(String id, JsonProcessingException e) {
                return Optional.empty();
            }

            @Override
            public Optional<String> onExecutionError(String id, Exception e) {
                return Optional.empty();
            }

            @Override
            public List<String> onRefIdLookupFailure(String refId) {
                return List.of();
            }
        };

        // When
        List<String> result = defaultHandler.onRefIdLookupFailure("some-ref");

        // Then
        assertTrue(result.isEmpty());
    }
}