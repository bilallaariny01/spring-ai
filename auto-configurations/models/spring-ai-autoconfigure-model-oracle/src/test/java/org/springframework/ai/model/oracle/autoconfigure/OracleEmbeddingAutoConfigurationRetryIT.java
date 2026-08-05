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

package org.springframework.ai.model.oracle.autoconfigure;

import java.sql.SQLException;
import java.sql.SQLTransientException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.oracle.embedding.OracleEmbeddingModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.jdbc.datasource.AbstractDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry-specific auto-configuration tests for {@link OracleEmbeddingModel}.
 *
 * @author Spring AI Contributors
 */
class OracleEmbeddingAutoConfigurationRetryIT {

	@Test
	void embedTransientErrorIsRetriedAndPreservesExceptionType() {
		TestRetryListener retryListener = new TestRetryListener();
		RetryTemplate retryTemplate = shortRetryTemplate(retryListener);

		new ApplicationContextRunner().withBean(DataSource.class, AlwaysTransientFailingDataSource::new)
			.withBean(RetryTemplate.class, () -> retryTemplate)
			.withConfiguration(AutoConfigurations.of(OracleEmbeddingAutoConfiguration.class))
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				assertThatThrownBy(() -> model.embed("hello")).isInstanceOf(TransientAiException.class)
					.hasMessage("Failed to generate Oracle embedding");
				assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
			});
	}

	@Test
	void embedNonTransientErrorIsNotRetried() {
		TestRetryListener retryListener = new TestRetryListener();
		RetryTemplate retryTemplate = shortRetryTemplate(retryListener);

		new ApplicationContextRunner().withBean(DataSource.class, AlwaysNonTransientFailingDataSource::new)
			.withBean(RetryTemplate.class, () -> retryTemplate)
			.withConfiguration(AutoConfigurations.of(OracleEmbeddingAutoConfiguration.class))
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				assertThatThrownBy(() -> model.embed("hello")).isInstanceOf(NonTransientAiException.class)
					.hasMessage("Failed to generate Oracle embedding");
				assertThat(retryListener.onErrorRetryCount).isZero();
			});
	}

	private RetryTemplate shortRetryTemplate(TestRetryListener retryListener) {
		RetryPolicy retryPolicy = RetryPolicy.builder().maxRetries(2).includes(TransientAiException.class).build();
		RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
		retryTemplate.setRetryListener(retryListener);
		return retryTemplate;
	}

	private static final class AlwaysTransientFailingDataSource extends AbstractDataSource {

		@Override
		public java.sql.Connection getConnection() throws SQLTransientException {
			throw new SQLTransientException("temporary db issue");
		}

		@Override
		public java.sql.Connection getConnection(String username, String password) throws SQLTransientException {
			throw new SQLTransientException("temporary db issue");
		}

	}

	private static final class AlwaysNonTransientFailingDataSource extends AbstractDataSource {

		@Override
		public java.sql.Connection getConnection() throws SQLException {
			throw new SQLException("permanent db issue");
		}

		@Override
		public java.sql.Connection getConnection(String username, String password) throws SQLException {
			throw new SQLException("permanent db issue");
		}

	}

	private static final class TestRetryListener implements RetryListener {

		private int onErrorRetryCount;

		@Override
		public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable) {
			this.onErrorRetryCount++;
		}

	}

}
