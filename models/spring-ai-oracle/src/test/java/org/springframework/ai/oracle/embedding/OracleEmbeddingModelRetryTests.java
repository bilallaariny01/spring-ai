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

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.List;

import javax.sql.DataSource;

import oracle.sql.VECTOR;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.jdbc.datasource.AbstractDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retry-specific tests for {@link OracleEmbeddingModel}.
 *
 * @author Spring AI Contributors
 */
class OracleEmbeddingModelRetryTests {

	/**
	 * Verify transient SQL errors are retried and mapped to transient AI exception.
	 */
	@Test
	void embedTransientErrorIsRetriedAndPreservesExceptionType() {
		TestRetryListener retryListener = new TestRetryListener();
		RetryTemplate retryTemplate = shortRetryTemplate(retryListener);
		OracleEmbeddingModel model = new OracleEmbeddingModel(new AlwaysTransientFailingDataSource(), null, null,
				retryTemplate);

		assertThatThrownBy(() -> model.embed("hello")).isInstanceOf(TransientAiException.class)
			.hasMessage("Failed to generate Oracle embedding");
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	/**
	 * Verify non-transient SQL errors are not retried.
	 */
	@Test
	void embedNonTransientErrorIsNotRetried() {
		TestRetryListener retryListener = new TestRetryListener();
		RetryTemplate retryTemplate = shortRetryTemplate(retryListener);
		OracleEmbeddingModel model = new OracleEmbeddingModel(new AlwaysNonTransientFailingDataSource(), null, null,
				retryTemplate);

		assertThatThrownBy(() -> model.embed("hello")).isInstanceOf(NonTransientAiException.class)
			.hasMessage("Failed to generate Oracle embedding");
		assertThat(retryListener.onErrorRetryCount).isZero();
	}

	/**
	 * Verify a recoverable connection failure obtains a new connection and retries.
	 */
	@Test
	void embedRecoverableConnectionFailureIsRetried() throws Exception {
		TestRetryListener retryListener = new TestRetryListener();
		RetryTemplate retryTemplate = shortRetryTemplate(retryListener);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		Clob clob = mock(Clob.class);
		ResultSet resultSet = embeddingResult(1.0f);

		when(dataSource.getConnection()).thenThrow(new SQLRecoverableException("connection lost"))
			.thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(connection.createClob()).thenReturn(clob);
		when(statement.executeQuery()).thenReturn(resultSet);

		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, null, null, retryTemplate);

		assertThat(model.embed("hello")).containsExactly(1.0f);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(1);
		verify(dataSource, times(2)).getConnection();
		verify(clob).free();
	}

	/**
	 * Verify partial embeddings from a failed attempt are discarded before retrying.
	 */
	@Test
	void partialResultsFromFailedAttemptAreDiscarded() throws Exception {
		TestRetryListener retryListener = new TestRetryListener();
		RetryTemplate retryTemplate = shortRetryTemplate(retryListener);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		PreparedStatement firstAttemptFirstInput = mock(PreparedStatement.class);
		PreparedStatement firstAttemptSecondInput = mock(PreparedStatement.class);
		PreparedStatement retryFirstInput = mock(PreparedStatement.class);
		PreparedStatement retrySecondInput = mock(PreparedStatement.class);
		ResultSet firstAttemptResult = embeddingResult(99.0f);
		ResultSet retryFirstResult = embeddingResult(1.0f);
		ResultSet retrySecondResult = embeddingResult(2.0f);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(firstAttemptFirstInput, firstAttemptSecondInput,
				retryFirstInput, retrySecondInput);
		when(connection.createClob()).thenReturn(mock(Clob.class), mock(Clob.class), mock(Clob.class),
				mock(Clob.class));
		when(firstAttemptFirstInput.executeQuery()).thenReturn(firstAttemptResult);
		when(firstAttemptSecondInput.executeQuery()).thenThrow(new SQLTransientException("temporary db issue"));
		when(retryFirstInput.executeQuery()).thenReturn(retryFirstResult);
		when(retrySecondInput.executeQuery()).thenReturn(retrySecondResult);

		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder().batching(false).build();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource, options, null, retryTemplate);

		EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("first", "second"), options));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults()).extracting(Embedding::getIndex).containsExactly(0, 1);
		assertThat(response.getResults().get(0).getOutput()).containsExactly(1.0f);
		assertThat(response.getResults().get(1).getOutput()).containsExactly(2.0f);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(1);
	}

	private ResultSet embeddingResult(float value) throws SQLException {
		ResultSet resultSet = mock(ResultSet.class);
		VECTOR vector = mock(VECTOR.class);
		when(resultSet.next()).thenReturn(true);
		when(resultSet.getObject("vector", VECTOR.class)).thenReturn(vector);
		when(vector.toFloatArray()).thenReturn(new float[] { value });
		return resultSet;
	}

	/**
	 * Create a short retry template that retries transient AI exceptions.
	 * @param retryListener listener capturing retry attempts
	 * @return configured retry template
	 */
	private RetryTemplate shortRetryTemplate(TestRetryListener retryListener) {
		RetryPolicy retryPolicy = RetryPolicy.builder().maxRetries(2).includes(TransientAiException.class).build();
		RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
		retryTemplate.setRetryListener(retryListener);
		return retryTemplate;
	}

	private static final class AlwaysTransientFailingDataSource extends AbstractDataSource {

		/**
		 * Always throws transient SQL exception.
		 * @return never returns
		 * @throws SQLTransientException always thrown
		 */
		@Override
		public java.sql.Connection getConnection() throws SQLTransientException {
			throw new SQLTransientException("temporary db issue");
		}

		/**
		 * Always throws transient SQL exception.
		 * @param username ignored
		 * @param password ignored
		 * @return never returns
		 * @throws SQLTransientException always thrown
		 */
		@Override
		public java.sql.Connection getConnection(String username, String password) throws SQLTransientException {
			throw new SQLTransientException("temporary db issue");
		}

	}

	private static final class AlwaysNonTransientFailingDataSource extends AbstractDataSource {

		/**
		 * Always throws non-transient SQL exception.
		 * @return never returns
		 * @throws SQLException always thrown
		 */
		@Override
		public java.sql.Connection getConnection() throws SQLException {
			throw new SQLException("permanent db issue");
		}

		/**
		 * Always throws non-transient SQL exception.
		 * @param username ignored
		 * @param password ignored
		 * @return never returns
		 * @throws SQLException always thrown
		 */
		@Override
		public java.sql.Connection getConnection(String username, String password) throws SQLException {
			throw new SQLException("permanent db issue");
		}

	}

	private static final class TestRetryListener implements RetryListener {

		private int onErrorRetryCount;

		/**
		 * Increment retry counter on each retry callback.
		 * @param retryPolicy retry policy
		 * @param retryable retry descriptor
		 */
		@Override
		public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable) {
			this.onErrorRetryCount++;
		}

	}

}
