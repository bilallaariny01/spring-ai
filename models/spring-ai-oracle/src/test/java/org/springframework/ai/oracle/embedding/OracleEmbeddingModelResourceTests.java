/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.oracle.embedding;

import java.sql.Array;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import oracle.jdbc.OracleConnection;
import oracle.sql.VECTOR;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.NonTransientAiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resource lifecycle tests for {@link OracleEmbeddingModel}.
 *
 * @author Spring AI Contributors
 */
class OracleEmbeddingModelResourceTests {

	@Test
	void singleEmbeddingFreesTemporaryClob() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		Clob clob = mock(Clob.class);
		ResultSet resultSet = embeddingResult(1.0f);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(connection.createClob()).thenReturn(clob);
		when(statement.executeQuery()).thenReturn(resultSet);

		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource);

		assertThat(model.embed("input")).containsExactly(1.0f);
		verify(clob).free();
	}

	@Test
	void batchedEmbeddingFreesArrayAndTemporaryClobs() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		OracleConnection oracleConnection = mock(OracleConnection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		Array array = mock(Array.class);
		Clob firstClob = mock(Clob.class);
		Clob secondClob = mock(Clob.class);
		ResultSet resultSet = mock(ResultSet.class);
		VECTOR firstVector = mock(VECTOR.class);
		VECTOR secondVector = mock(VECTOR.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.unwrap(OracleConnection.class)).thenReturn(oracleConnection);
		when(connection.createClob()).thenReturn(firstClob, secondClob);
		when(oracleConnection.createOracleArray(eq("SYS.VECTOR_ARRAY_T"), any())).thenReturn(array);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true, true, false);
		when(resultSet.getObject("vector", VECTOR.class)).thenReturn(firstVector, secondVector);
		when(firstVector.toFloatArray()).thenReturn(new float[] { 1.0f });
		when(secondVector.toFloatArray()).thenReturn(new float[] { 2.0f });

		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().batching(true).build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, options);

		EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("first", "second"), options));

		assertThat(response.getResults()).hasSize(2);
		verify(array).free();
		verify(firstClob).free();
		verify(secondClob).free();
	}

	@Test
	void batchedEmbeddingRejectsMissingResultsAndFreesResources() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		OracleConnection oracleConnection = mock(OracleConnection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		Array array = mock(Array.class);
		Clob firstClob = mock(Clob.class);
		Clob secondClob = mock(Clob.class);
		ResultSet resultSet = mock(ResultSet.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.unwrap(OracleConnection.class)).thenReturn(oracleConnection);
		when(connection.createClob()).thenReturn(firstClob, secondClob);
		when(oracleConnection.createOracleArray(eq("SYS.VECTOR_ARRAY_T"), any())).thenReturn(array);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(false);

		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().batching(true).build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, options);

		assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("first", "second"), options)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Failed to generate Oracle embeddings")
			.hasRootCauseMessage("Oracle embedding response count mismatch: expected 2 but received 0");
		verify(array).free();
		verify(firstClob).free();
		verify(secondClob).free();
	}

	@Test
	void partialBatchPayloadFailureFreesCreatedClobs() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		OracleConnection oracleConnection = mock(OracleConnection.class);
		Clob firstClob = mock(Clob.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.unwrap(OracleConnection.class)).thenReturn(oracleConnection);
		when(connection.createClob()).thenReturn(firstClob).thenThrow(new SQLException("cannot create CLOB"));

		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().batching(true).build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, options);

		assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("first", "second"), options)))
			.isInstanceOf(NonTransientAiException.class)
			.hasMessage("Failed to generate Oracle embeddings");
		verify(firstClob).free();
	}

	@Test
	void batchPayloadWriteFailureFreesCurrentClob() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		OracleConnection oracleConnection = mock(OracleConnection.class);
		Clob clob = mock(Clob.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.unwrap(OracleConnection.class)).thenReturn(oracleConnection);
		when(connection.createClob()).thenReturn(clob);
		doThrow(new SQLException("cannot write CLOB")).when(clob).setString(eq(1L), anyString());

		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().batching(true).build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, options);

		assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("input"), options)))
			.isInstanceOf(NonTransientAiException.class)
			.hasMessage("Failed to generate Oracle embeddings");
		verify(clob).free();
	}

	private ResultSet embeddingResult(float value) throws SQLException {
		ResultSet resultSet = mock(ResultSet.class);
		VECTOR vector = mock(VECTOR.class);
		when(resultSet.next()).thenReturn(true);
		when(resultSet.getObject("vector", VECTOR.class)).thenReturn(vector);
		when(vector.toFloatArray()).thenReturn(new float[] { value });
		return resultSet;
	}

}
