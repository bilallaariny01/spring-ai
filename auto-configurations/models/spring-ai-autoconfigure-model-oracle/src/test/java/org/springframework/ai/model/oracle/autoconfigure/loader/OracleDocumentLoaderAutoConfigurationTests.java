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

package org.springframework.ai.model.oracle.autoconfigure.loader;

import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OracleDocumentLoaderAutoConfiguration}.
 *
 * @author Spring AI Contributors
 */
class OracleDocumentLoaderAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(DataSource.class, () -> mock(DataSource.class))
		.withConfiguration(AutoConfigurations.of(OracleDocumentLoaderAutoConfiguration.class));

	@Test
	void createsResourceReaderForResourceOnly() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=classpath:/docs")
			.run(context -> assertThat(context.getBeansOfType(OracleDocumentReader.class)).hasSize(1)
				.containsKey("oracleResourceDocumentLoader"));
	}

	@Test
	void autoConfiguredClasspathReaderLoadsResource() throws Exception {
		byte[] content = "stream-backed content\n".getBytes(StandardCharsets.UTF_8);
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		PreparedStatement statement = mock(PreparedStatement.class);
		Blob blob = mock(Blob.class);
		ResultSet resultSet = mock(ResultSet.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(connection.createBlob()).thenReturn(blob);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString("text")).thenReturn("converted classpath content");
		when(resultSet.getString("metadata"))
			.thenReturn("<html><head><meta name=\"author\" content=\"Oracle\"></head></html>");

		new ApplicationContextRunner().withBean(DataSource.class, () -> dataSource)
			.withConfiguration(AutoConfigurations.of(OracleDocumentLoaderAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=classpath:/documents/stream.md")
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				assertThat(reader.get()).singleElement().satisfies(document -> assertClasspathDocument(document));
			});

		verify(blob).setBytes(eq(1L), eq(content));
		verify(blob).free();
	}

	private static void assertClasspathDocument(Document document) {
		assertThat(document.getText()).isEqualTo("converted classpath content");
		assertThat(document.getMetadata()).containsEntry("file_name", "stream.md").containsEntry("author", "Oracle");
		assertThat(document.getMetadata().get("source").toString()).endsWith("/documents/stream.md");
	}

	@Test
	void createsTableReaderForCompleteTableOnly() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle", "spring.ai.oracle.document-loader.table.owner=APP",
					"spring.ai.oracle.document-loader.table.table-name=DOCS",
					"spring.ai.oracle.document-loader.table.column-name=TEXT")
			.run(context -> assertThat(context.getBeansOfType(OracleDocumentReader.class)).hasSize(1)
				.containsKey("oracleTableDocumentLoader"));
	}

	@Test
	void rejectsResourceAndTableTogether() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=classpath:/docs",
					"spring.ai.oracle.document-loader.table.owner=APP",
					"spring.ai.oracle.document-loader.table.table-name=DOCS",
					"spring.ai.oracle.document-loader.table.column-name=TEXT")
			.run(context -> assertThat(context.getStartupFailure())
				.hasRootCauseMessage("Configure only one Oracle document-loader source: either resource or table"));
	}

	@Test
	void rejectsIncompleteTable() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle", "spring.ai.oracle.document-loader.table.owner=APP",
					"spring.ai.oracle.document-loader.table.table-name=DOCS")
			.run(context -> assertThat(context.getStartupFailure())
				.hasRootCauseMessage("Oracle document-loader table requires owner, table-name, and column-name"));
	}

	@Test
	void rejectsBlankTableValue() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle", "spring.ai.oracle.document-loader.table.owner=APP",
					"spring.ai.oracle.document-loader.table.table-name=",
					"spring.ai.oracle.document-loader.table.column-name=TEXT")
			.run(context -> assertThat(context.getStartupFailure())
				.hasRootCauseMessage("Oracle document-loader table requires owner, table-name, and column-name"));
	}

	@Test
	void rejectsBlankResource() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle", "spring.ai.oracle.document-loader.resource=")
			.run(context -> assertThat(context.getStartupFailure())
				.hasRootCauseMessage("Oracle document-loader resource must not be blank"));
	}

	@Test
	void createsNoReaderWhenNoSourceIsConfigured() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=oracle")
			.run(context -> assertThat(context.getBeansOfType(OracleDocumentReader.class)).isEmpty());
	}

}
