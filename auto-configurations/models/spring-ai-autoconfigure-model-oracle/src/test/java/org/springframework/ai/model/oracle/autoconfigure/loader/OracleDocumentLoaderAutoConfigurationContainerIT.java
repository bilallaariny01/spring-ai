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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
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

import javax.sql.DataSource;

import oracle.jdbc.OracleType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.oracle.loader.OracleDocumentReader;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Container integration tests for Oracle document loader auto-configuration.
 *
 * Enabled only when {@code ORACLE_AUTOCONFIG_IT=true} is set.
 *
 * @author Spring AI Contributors
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_AUTOCONFIG_IT", matches = "(?i:true|1|yes)")
class OracleDocumentLoaderAutoConfigurationContainerIT {

	private static final String UTL_TO_TEXT_SQL = "select dbms_vector_chain.utl_to_text(?, ?) as text from dual";

	private static final DockerImageName ORACLE_IMAGE = DockerImageName.parse("gvenzl/oracle-free:23-faststart");

	private static volatile OracleContainer oracleContainer;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(DataSource.class, this::dataSource)
		.withConfiguration(AutoConfigurations.of(OracleDocumentLoaderAutoConfiguration.class));

	@Test
	void loadFromFile() throws IOException {
		assumeUtlToTextAvailable();
		Path markdown = Files.createTempFile("oracle-loader-", ".md");
		Files.writeString(markdown, "# Story\n\nOnce upon a markdown file.");

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=file:" + markdown.toAbsolutePath())
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				List<Document> docs = reader.get();
				assertThat(docs).hasSize(1);
				Document doc = docs.get(0);
				assertThat(doc.getText()).contains("Once upon a markdown");
				assertThat(doc.getMetadata().get("file_name")).isEqualTo(markdown.getFileName().toString());
				assertThat(doc.getMetadata().get("absolute_directory_path"))
					.isEqualTo(markdown.getParent().toAbsolutePath().normalize().toString());
				assertThat(doc.getMetadata().get("source")).isEqualTo(markdown.toAbsolutePath().normalize().toString());
			});
	}

	@Test
	void loadFromDir() throws IOException {
		assumeUtlToTextAvailable();
		Path root = Files.createTempDirectory("oracle-loader-dir-");
		Files.writeString(root.resolve("one.md"), "# One\n\nHello");
		Path nested = Files.createDirectories(root.resolve("nested"));
		Files.writeString(nested.resolve("two.md"), "# Two\n\nWorld");

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=file:" + root.toAbsolutePath())
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
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
						nested.toAbsolutePath().normalize().toString());
			});
	}

	@Test
	void loadFromResourceUrl() throws IOException {
		assumeUtlToTextAvailable();
		Path markdown = Files.createTempFile("oracle-loader-url-", ".md");
		Files.writeString(markdown, "# URL Story\n\nOnce upon a markdown URL.");
		String resourceUrl = markdown.toUri().toString();

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=" + resourceUrl)
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				List<Document> docs = reader.get();
				assertThat(docs).hasSize(1);
				assertThat(docs.get(0).getText()).contains("Once upon a markdown");
			});
	}

	@Test
	void loadFromResourceWithCharsetAndFormatPreference() throws IOException {
		assumeUtlToTextAvailable();
		Path markdown = Files.createTempFile("oracle-loader-pref-", ".md");
		Files.writeString(markdown, "# Pref Story\n\nOnce upon a markdown preference.");

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=file:" + markdown.toAbsolutePath(),
					"spring.ai.oracle.document-loader.preferences.plaintext=true",
					"spring.ai.oracle.document-loader.preferences.charset=UTF8",
					"spring.ai.oracle.document-loader.preferences.format=TEXT")
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				List<Document> docs = reader.get();
				assertThat(docs).hasSize(1);
				assertThat(docs.get(0).getText()).contains("Once upon a markdown");
			});
	}

	@Test
	void loadFromResourceWithNullPreference() throws IOException {
		assumeUtlToTextAvailable();
		Path markdown = Files.createTempFile("oracle-loader-null-", ".md");
		Files.writeString(markdown, "# Null Pref\n\nHello.");

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=file:" + markdown.toAbsolutePath())
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				assertThatCode(reader::get).doesNotThrowAnyException();
			});
	}

	@Test
	void loadFromTable() {
		assumeUtlToTextAvailable();
		String owner = oracleContainer().getUsername().toUpperCase();
		try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("drop table docs purge");
		}
		catch (SQLException ignored) {
		}

		try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("create table docs(id number primary key, text clob)");
			statement.execute("insert into docs(id, text) values (1, 'story from table one')");
			statement.execute("insert into docs(id, text) values (2, 'story from table two')");
		}
		catch (SQLException ex) {
			Assertions.fail("Could not prepare docs table", ex);
		}

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.table.owner=" + owner,
					"spring.ai.oracle.document-loader.table.table-name=DOCS",
					"spring.ai.oracle.document-loader.table.column-name=TEXT",
					"spring.ai.oracle.document-loader.preferences.plaintext=true",
					"spring.ai.oracle.document-loader.preferences.format=TEXT")
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				List<Document> docs = reader.get();
				assertThat(docs).hasSize(2);
				assertThat(docs.stream().map(Document::getText).reduce("", (a, b) -> a + " " + b))
					.contains("story from table");
				assertThat(docs.stream().map(doc -> doc.getMetadata().get("owner"))).containsOnly(owner);
				assertThat(docs.stream().map(doc -> doc.getMetadata().get("table"))).containsOnly("DOCS");
				assertThat(docs.stream().map(doc -> doc.getMetadata().get("column"))).containsOnly("TEXT");
				assertThat(docs.stream().map(doc -> doc.getMetadata().get("rowid"))).allMatch(value -> value != null);
			});
	}

	@Test
	void loadBadTableArgs() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle", "spring.ai.oracle.document-loader.table.owner=",
					"spring.ai.oracle.document-loader.table.table-name=DOCS",
					"spring.ai.oracle.document-loader.table.column-name=TEXT")
			.run(context -> assertThat(context.getStartupFailure()).isNotNull());
	}

	@Test
	void loadMissingFile() {
		assumeUtlToTextAvailable();
		Path missing = Paths.get("missing.md").toAbsolutePath();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=file:" + missing)
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class)
					.hasRootCauseInstanceOf(NoSuchFileException.class);
			});
	}

	@Test
	void loadMissingDirectory() {
		assumeUtlToTextAvailable();
		Path missing = Paths.get("missing-dir").toAbsolutePath();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.resource=file:" + missing)
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class)
					.hasRootCauseInstanceOf(NoSuchFileException.class);
			});
	}

	@Test
	void loadMissingTable() {
		assumeUtlToTextAvailable();
		String owner = oracleContainer().getUsername().toUpperCase();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-loader.table.owner=" + owner,
					"spring.ai.oracle.document-loader.table.table-name=MISSING",
					"spring.ai.oracle.document-loader.table.column-name=TEXT")
			.run(context -> {
				OracleDocumentReader reader = context.getBean(OracleDocumentReader.class);
				assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class)
					.hasCauseInstanceOf(SQLSyntaxErrorException.class)
					.hasMessageContaining("Failed to load documents using OracleDocumentReader");
			});
	}

	private DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername(oracleContainer().getUsername());
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	private void assumeUtlToTextAvailable() {
		try (Connection connection = dataSource().getConnection()) {
			Assumptions.assumeTrue(isUtlToTextInvokable(connection), "DBMS_VECTOR_CHAIN.UTL_TO_TEXT is not available.");
		}
		catch (SQLException ex) {
			Assertions.fail("Could not verify UTL_TO_TEXT availability.", ex);
		}
	}

	static boolean isUtlToTextInvokable(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(UTL_TO_TEXT_SQL)) {
			Blob blob = connection.createBlob();
			blob.setBytes(1, "probe".getBytes());
			statement.setBlob(1, blob);
			statement.setObject(2, null, OracleType.JSON);
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

	private static synchronized OracleContainer oracleContainer() {
		if (oracleContainer == null) {
			oracleContainer = new OracleContainer(ORACLE_IMAGE).withStartupTimeout(Duration.ofMinutes(5))
				.withStartupAttempts(3)
				.withSharedMemorySize(2L * 1024L * 1024L * 1024L);
			oracleContainer.start();
		}
		return oracleContainer;
	}

}
