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

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Oracle feature-availability error classification.
 *
 * @author Spring AI Contributors
 */
class OracleDocumentLoaderAvailabilityTests {

	@ParameterizedTest
	@ValueSource(ints = { 904, 6550 })
	void knownUnavailableErrorsReturnFalse(int errorCode) throws SQLException {
		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString()))
			.thenThrow(new SQLException("UTL_TO_TEXT is unavailable", "42000", errorCode));

		assertThat(OracleDocumentLoaderAutoConfigurationContainerIT.isUtlToTextInvokable(connection)).isFalse();
	}

	@Test
	void unexpectedSqlErrorIsPropagated() throws SQLException {
		Connection connection = mock(Connection.class);
		SQLException failure = new SQLException("invalid username or password", "72000", 1017);
		when(connection.prepareStatement(anyString())).thenThrow(failure);

		assertThatThrownBy(() -> OracleDocumentLoaderAutoConfigurationContainerIT.isUtlToTextInvokable(connection))
			.isSameAs(failure);
	}

	@Test
	void runtimeErrorIsPropagated() throws SQLException {
		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString())).thenThrow(new IllegalStateException("container unavailable"));

		assertThatThrownBy(() -> OracleDocumentLoaderAutoConfigurationContainerIT.isUtlToTextInvokable(connection))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("container unavailable");
	}

}
