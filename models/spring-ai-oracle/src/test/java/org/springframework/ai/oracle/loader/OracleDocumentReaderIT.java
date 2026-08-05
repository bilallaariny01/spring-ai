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

package org.springframework.ai.oracle.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import oracle.jdbc.provider.oson.OsonFactory;
import oracle.sql.json.OracleJsonDatum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import org.springframework.ai.document.Document;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for OracleDocumentReader.
 *
 * @author Spring AI Contributors
 */
class OracleDocumentReaderIT {

	private static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23-faststart";

	private static volatile OracleContainer oracleContainer;

	private static final String UTL_TO_TEXT_SQL = "select dbms_vector_chain.utl_to_text(?, ?) as text from dual";

	private static final OsonFactory OSON_FACTORY = new OsonFactory();

	private static final com.fasterxml.jackson.databind.json.JsonMapper OSON_MAPPER = new com.fasterxml.jackson.databind.json.JsonMapper();

	/**
	 * Verify a single file can be loaded into one document.
	 */
	@Test
	@DisplayName("load from file")
	void loadFromFile() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");

		OracleDocumentReader reader = new OracleDocumentReader(dataSource(), markdownFile);

		List<Document> docs = reader.get();
		assertThat(docs).hasSize(1);
		Document doc = docs.get(0);
		assertThat(doc.getText()).contains("Once upon a markdown");
		assertThat(doc.getMetadata().get("file_name")).isEqualTo("story.md");
		assertThat(doc.getMetadata().get("absolute_directory_path"))
			.isEqualTo(markdownFile.getParent().toAbsolutePath().normalize().toString());
		assertThat(doc.getMetadata().get("source")).isEqualTo(markdownFile.toAbsolutePath().normalize().toString());
	}

	/**
	 * Verify directory loading reads nested files.
	 */
	@Test
	@DisplayName("load from dir")
	void loadFromDir() throws Exception {
		assumeUtlToTextAvailable();
		Path root = resourcePath("/example-files");

		OracleDocumentReader reader = new OracleDocumentReader(dataSource(), root);

		List<Document> docs = reader.get();
		assertThat(docs).isNotEmpty();
		assertThat(docs.stream().map(Document::getText).reduce("", (a, b) -> a + " " + b)).contains("Hello")
			.contains("World");
		Set<Object> fileNames = docs.stream()
			.map(doc -> doc.getMetadata().get("file_name"))
			.collect(Collectors.toSet());
		assertThat(fileNames).contains("one.md", "two.md");
		Set<Object> directories = docs.stream()
			.map(doc -> doc.getMetadata().get("absolute_directory_path"))
			.collect(Collectors.toSet());
		assertThat(directories).contains(root.toAbsolutePath().normalize().toString(),
				root.resolve("nested").toAbsolutePath().normalize().toString());
	}

	/**
	 * Verify resource URL loading works.
	 */
	@Test
	@DisplayName("load from resource URL")
	void loadFromResourceUrl() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");

		OracleDocumentReader reader = new OracleDocumentReader(dataSource(), markdownFile.toUri().toString());

		List<Document> docs = reader.get();
		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).contains("Once upon a markdown");
	}

	/**
	 * Verify Spring resource loading works.
	 */
	@Test
	@DisplayName("load from resource")
	void loadFromResource() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");

		OracleDocumentReader reader = new OracleDocumentReader(dataSource(), new FileSystemResource(markdownFile));

		List<Document> docs = reader.get();
		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).contains("Once upon a markdown");
	}

	/**
	 * Verify explicit builder preference can be applied.
	 */
	@Test
	@DisplayName("load from resource with explicit preference")
	void loadFromResourceWithPreference() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");
		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource(), new FileSystemResource(markdownFile))
			.preferences(OracleDocumentPreferences.builder().plaintext(false).build())
			.build();

		List<Document> docs = reader.get();
		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).contains("Once upon a markdown");
	}

	/**
	 * Verify charset and format preferences can be applied.
	 */
	@Test
	@DisplayName("load from resource with charset and format preferences")
	void loadFromResourceWithCharsetAndFormatPreference() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");
		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource(), new FileSystemResource(markdownFile))
			.preferences(OracleDocumentPreferences.builder().plaintext(true).charset("UTF8").format("TEXT").build())
			.build();

		List<Document> docs = reader.get();
		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).contains("Once upon a markdown");
	}

	/**
	 * Verify structured preferences can be applied.
	 */
	@Test
	@DisplayName("load from resource with structured preferences")
	void loadFromResourceWithStructuredPreference() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");
		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource(), new FileSystemResource(markdownFile))
			.preferences(OracleDocumentPreferences.builder().plaintext(true).charset("UTF8").format("TEXT").build())
			.build();

		List<Document> docs = reader.get();
		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).contains("Once upon a markdown");
	}

	/**
	 * Verify null preferences do not fail loading.
	 */
	@Test
	@DisplayName("load from resource with null preference")
	void loadFromResourceWithNullPreference() throws Exception {
		assumeUtlToTextAvailable();
		Path markdownFile = resourcePath("/example-files/story.md");

		assertThatCode(() -> new OracleDocumentReader(dataSource(), new FileSystemResource(markdownFile)).get())
			.doesNotThrowAnyException();
	}

	/**
	 * Verify table-based loading returns text and metadata fields.
	 */
	@Test
	@DisplayName("load from table")
	void loadFromTable() throws Exception {
		assumeUtlToTextAvailable();

		try (Connection conn = dataSource().getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("drop table docs purge");
		}
		catch (SQLException ignored) {
		}

		try (Connection conn = dataSource().getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("create table docs(id number primary key, text clob)");
			stmt.execute("insert into docs(id, text) values (1, 'story from table one')");
			stmt.execute("insert into docs(id, text) values (2, 'story from table two')");
		}

		String owner = oracleContainer().getUsername().toUpperCase();
		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource(), owner, "DOCS", "TEXT")
			.preferences(OracleDocumentPreferences.builder().plaintext(true).format("TEXT").build())
			.build();

		List<Document> docs = reader.get();
		assertThat(docs.size()).isGreaterThan(1);
		assertThat(docs.stream().map(Document::getText).reduce("", (a, b) -> a + " " + b)).contains("story from table");
		assertThat(docs.stream().map(doc -> doc.getMetadata().get("owner"))).containsOnly(owner);
		assertThat(docs.stream().map(doc -> doc.getMetadata().get("table"))).containsOnly("DOCS");
		assertThat(docs.stream().map(doc -> doc.getMetadata().get("column"))).containsOnly("TEXT");
		assertThat(docs.stream().map(doc -> doc.getMetadata().get("rowid"))).allMatch(value -> value != null);
	}

	/**
	 * Verify invalid table constructor arguments fail fast.
	 */
	@Test
	@DisplayName("load bad table args")
	void loadBadTableArgs() {
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource(), "", "docs", "text"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("owner must not be empty");
	}

	/**
	 * Verify missing file errors are surfaced.
	 */
	@Test
	@DisplayName("load missing file")
	void loadMissingFile() {
		assumeUtlToTextAvailable();
		OracleDocumentReader reader = new OracleDocumentReader(dataSource(), Paths.get("missing.md"));

		assertThatThrownBy(reader::get).isInstanceOf(RuntimeException.class)
			.hasRootCauseInstanceOf(NoSuchFileException.class);
	}

	/**
	 * Verify missing directory errors are surfaced.
	 */
	@Test
	@DisplayName("load missing directory")
	void loadMissingDirectory() {
		assumeUtlToTextAvailable();
		OracleDocumentReader reader = new OracleDocumentReader(dataSource(), Paths.get("missing-dir"));

		assertThatThrownBy(reader::get).isInstanceOf(RuntimeException.class)
			.hasRootCauseInstanceOf(NoSuchFileException.class);
	}

	/**
	 * Verify missing table errors are wrapped with SQL syntax cause.
	 */
	@Test
	@DisplayName("load missing table")
	void loadMissingTable() {
		assumeUtlToTextAvailable();

		String owner = oracleContainer().getUsername().toUpperCase();
		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource(), owner, "MISSING", "TEXT").build();

		assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class)
			.hasCauseInstanceOf(SQLSyntaxErrorException.class)
			.hasMessageContaining("Failed to load documents using OracleDocumentReader");
	}

	/**
	 * Build a container-backed data source for integration tests.
	 * @return configured data source
	 */
	private DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername(oracleContainer().getUsername());
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	/**
	 * Skip tests when {@code UTL_TO_TEXT} is unavailable.
	 */
	private void assumeUtlToTextAvailable() {
		try (Connection connection = dataSource().getConnection()) {
			Assumptions.assumeTrue(isUtlToTextInvokable(connection), "DBMS_VECTOR_CHAIN.UTL_TO_TEXT is not available.");
		}
		catch (SQLException ex) {
			Assertions.fail("Could not verify UTL_TO_TEXT availability.", ex);
		}
	}

	/**
	 * Probe whether {@code UTL_TO_TEXT} is invokable.
	 * @param connection JDBC connection
	 * @return {@code true} when invokable
	 * @throws SQLException on unexpected SQL failures
	 */
	private static boolean isUtlToTextInvokable(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(UTL_TO_TEXT_SQL)) {
			statement.setString(1, "probe");
			statement.setObject(2, toOracleJsonDatum("{\"plaintext\":\"true\"}"));
			try (ResultSet ignored = statement.executeQuery()) {
				return true;
			}
		}
		catch (SQLException ex) {
			if (ex.getErrorCode() == 904 || ex.getErrorCode() == 6550) {
				return false;
			}
			throw ex;
		}
	}

	/**
	 * Convert JSON text to an Oracle JSON datum.
	 * @param jsonValue JSON string
	 * @return Oracle JSON datum
	 */
	private static OracleJsonDatum toOracleJsonDatum(String jsonValue) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (com.fasterxml.jackson.core.JsonGenerator generator = OSON_FACTORY.createGenerator(out)) {
				OSON_MAPPER.writeValue(generator, OSON_MAPPER.readTree(jsonValue));
				generator.flush();
			}
			return new OracleJsonDatum(out.toByteArray());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to serialize JSON params for UTL_TO_TEXT", ex);
		}
	}

	/**
	 * Start and return the shared Oracle test container.
	 * @return running Oracle container
	 */
	private static synchronized OracleContainer oracleContainer() {
		if (oracleContainer == null) {
			oracleContainer = new OracleContainer(ORACLE_IMAGE_NAME).withStartupTimeout(Duration.ofMinutes(5))
				.withStartupAttempts(3)
				.withSharedMemorySize(2L * 1024L * 1024L * 1024L);
			oracleContainer.start();
		}
		return oracleContainer;
	}

	/**
	 * Resolve a classpath resource path.
	 * @param resource classpath resource name
	 * @return resolved filesystem path
	 * @throws Exception on resolution failures
	 */
	private static Path resourcePath(String resource) throws Exception {
		URL url = OracleDocumentReaderIT.class.getResource(resource);
		if (url == null) {
			throw new IllegalStateException("Missing resource: " + resource);
		}
		return Paths.get(url.toURI());
	}

}
