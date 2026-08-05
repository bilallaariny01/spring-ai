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

package org.springframework.ai.oracle.chunking;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.provider.oson.OsonFactory;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OracleDocumentSplitter} builder options and JDBC parameter
 * binding.
 *
 * @author Spring AI Contributors
 */
class OracleDocumentSplitterTests {

	/**
	 * Verify numeric chunk limits are encoded as strings required by Oracle's chunk
	 * parameter schema.
	 */
	@Test
	void preferencesSerializeMaxAndOverlapAsOsonStrings() throws Exception {
		OracleChunkingPreferences preferences = OracleChunkingPreferences.builder()
			.by("words")
			.max(100)
			.overlap(10)
			.build();

		try (JsonParser parser = new OsonFactory().createParser(preferences.toByteArray())) {
			JsonNode json = new JsonMapper().readTree(parser);

			assertThat(json.path("max").isTextual()).isTrue();
			assertThat(json.path("max").textValue()).isEqualTo("100");
			assertThat(json.path("overlap").isTextual()).isTrue();
			assertThat(json.path("overlap").textValue()).isEqualTo("10");
		}
	}

	/**
	 * Verify OSON preferences are bound when builder options are provided.
	 */
	@Test
	void splitWithBuilderOptionsBindsOsonPreference() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		Clob clob = mock(Clob.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(any(String.class))).thenReturn(statement);
		when(connection.createClob()).thenReturn(clob);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString("chunk_data")).thenReturn("chunk-1");

		OracleDocumentSplitter splitter = OracleDocumentSplitter.builder(dataSource)
			.preferences(OracleChunkingPreferences.builder()
				.by("words")
				.max(100)
				.split("sentence")
				.overlap(20)
				.language("american")
				.normalize("all")
				.extended(true)
				.build())
			.build();

		List<Document> chunks = splitter.split(new Document("sample text"));

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).getText()).isEqualTo("chunk-1");
		verify(clob).setString(1, "sample text");
		verify(statement).setClob(1, clob);
		verify(clob).free();
		verify(statement).setObject(eq(2), argThat(value -> value instanceof byte[] && ((byte[]) value).length > 0),
				eq(OracleTypes.JSON));
		verify(statement, never()).setNull(2, OracleTypes.JSON);
	}

	/**
	 * Verify SQL failures are wrapped in an illegal state exception.
	 */
	@Test
	void splitHandlesSqlFailure() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

		OracleDocumentSplitter splitter = new OracleDocumentSplitter(dataSource);

		assertThatThrownBy(() -> splitter.split(new Document("text"))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to split text using Oracle DBMS_VECTOR_CHAIN.UTL_TO_CHUNKS")
			.hasRootCauseInstanceOf(SQLException.class);
	}

	/**
	 * Verify empty input returns one empty chunk.
	 */
	@Test
	void splitEmptyInputReturnsSingleEmptyChunk() {
		DataSource dataSource = mock(DataSource.class);
		OracleDocumentSplitter splitter = new OracleDocumentSplitter(dataSource);

		List<Document> chunks = splitter.split(new Document(""));

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).getText()).isEmpty();
	}

	/**
	 * Verify non-positive max is rejected.
	 */
	@Test
	void builderRejectsNonPositiveMax() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().max(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("max must be greater than 0");
	}

	/**
	 * Verify negative overlap is rejected.
	 */
	@Test
	void builderRejectsNegativeOverlap() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().overlap(-1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("overlap must be greater than or equal to 0");
	}

	/**
	 * Verify blank split mode is rejected.
	 */
	@Test
	void builderRejectsBlankSplit() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().split(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("split must not be empty");
	}

	/**
	 * Verify blank chunking strategy is rejected.
	 */
	@Test
	void builderRejectsBlankBy() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().by(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("by must not be empty");
	}

	/**
	 * Verify blank normalize mode is rejected.
	 */
	@Test
	void builderRejectsBlankNormalize() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().normalize(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("normalize must not be empty");
	}

	/**
	 * Verify custom list requires custom split mode.
	 */
	@Test
	void builderRejectsCustomListWithoutCustomSplit() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().customList(List.of(",")).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("customList requires split to be set to 'custom'");
	}

	/**
	 * Verify vocabulary requires {@code by=vocabulary}.
	 */
	@Test
	void builderRejectsVocabularyWithoutVocabularyBy() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().vocabulary("v1").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("vocabulary requires by to be set to 'vocabulary'");
	}

	/**
	 * Verify normalization options require {@code normalize=options}.
	 */
	@Test
	void builderRejectsNormOptionsWithoutNormalizeOptions() {
		assertThatThrownBy(() -> OracleChunkingPreferences.builder().normOptions(List.of("punctuation")).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("normOptions requires normalize to be set to 'options'");
	}

	/**
	 * Verify builder rejects null data source.
	 */
	@Test
	void builderRejectsNullDataSource() {
		assertThatThrownBy(() -> OracleDocumentSplitter.builder(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("dataSource must not be null");
	}

	/**
	 * Verify constructor rejects null data source.
	 */
	@Test
	void constructorRejectsNullDataSource() {
		assertThatThrownBy(() -> new OracleDocumentSplitter(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("dataSource must not be null");
	}

}
