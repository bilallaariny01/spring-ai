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

package org.springframework.ai.model.oracle.autoconfigure.chunking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.jdbc.OracleType;
import oracle.jdbc.provider.oson.OsonFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.oracle.chunking.OracleDocumentSplitter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Container integration tests for Oracle document splitter auto-configuration.
 *
 * Enabled only when {@code ORACLE_AUTOCONFIG_IT=true} is set.
 *
 * @author Spring AI Contributors
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_AUTOCONFIG_IT", matches = "(?i:true|1|yes)")
class OracleDocumentSplitterAutoConfigurationContainerIT {

	private static final String UTL_TO_CHUNKS_SQL = "select json_value(t.column_value, '$.chunk_data' returning clob) as chunk_data"
			+ "\nfrom dbms_vector_chain.utl_to_chunks(?, ?) t";

	private static final DockerImageName ORACLE_IMAGE = DockerImageName.parse("gvenzl/oracle-free:23-faststart");

	private static final OsonFactory OSON_FACTORY = new OsonFactory();

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final String CONTENT = "The tower is 324 meters (1,063 ft) tall, about the same height as an "
			+ "81-storey building. During its construction, the Eiffel Tower surpassed the Washington Monument.";

	private static volatile OracleContainer oracleContainer;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(DataSource.class, this::dataSource)
		.withConfiguration(AutoConfigurations.of(OracleDocumentSplitterAutoConfiguration.class));

	@Test
	void splitStringInputByChars() {
		assertUtlToChunksAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-splitter.preferences.by=chars",
					"spring.ai.oracle.document-splitter.preferences.max=50")
			.run(context -> {
				OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
				List<Document> chunks = splitter.split(new Document(CONTENT));
				assertThat(chunks.size()).isGreaterThan(1);
			});
	}

	@Test
	void splitStringInputWithDefaultConstructor() {
		assertUtlToChunksAvailable();
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=oracle").run(context -> {
			OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
			List<Document> chunks = splitter.split(new Document(CONTENT));
			assertThat(chunks).isNotEmpty();
			assertThat(chunks.stream().map(Document::getText).allMatch(text -> text != null && !text.isBlank()))
				.isTrue();
		});
	}

	@Test
	void splitStringInputByWords() {
		assertUtlToChunksAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-splitter.preferences.by=words",
					"spring.ai.oracle.document-splitter.preferences.max=10")
			.run(context -> {
				OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
				List<Document> chunks = splitter.split(new Document(CONTENT));
				assertThat(chunks.size()).isGreaterThan(1);
				assertThat(chunks.stream().map(Document::getText)).allMatch(chunk -> !chunk.trim().startsWith("{"));
			});
	}

	@Test
	void splitDocInputByChars() {
		assertUtlToChunksAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-splitter.preferences.by=chars",
					"spring.ai.oracle.document-splitter.preferences.max=50")
			.run(context -> {
				OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
				Document source = new Document(CONTENT, Map.of("a", 1, "b", 2));
				List<Document> chunks = splitter.split(source);
				assertThat(chunks.size()).isGreaterThan(1);
				Document firstChunk = chunks.get(0);
				assertThat(firstChunk.getMetadata().get("a")).isEqualTo(1);
				assertThat(firstChunk.getMetadata().get("b")).isEqualTo(2);
			});
	}

	@Test
	void splitDocInputWithNullPreferences() {
		assertUtlToChunksAvailable();
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=oracle").run(context -> {
			OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
			Document source = new Document(CONTENT, Map.of("source", "it", "version", 1));
			List<Document> chunks = splitter.split(source);
			assertThat(chunks).isNotEmpty();
			assertThat(chunks.get(0).getMetadata().get("source")).isEqualTo("it");
			assertThat(chunks.get(0).getMetadata().get("version")).isEqualTo(1);
		});
	}

	@Test
	void splitStringInputWithSplitAndOverlapPreference() {
		assertUtlToChunksAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-splitter.preferences.by=words",
					"spring.ai.oracle.document-splitter.preferences.max=10",
					"spring.ai.oracle.document-splitter.preferences.split=sentence")
			.run(context -> {
				OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
				List<Document> chunks = splitter.split(new Document(CONTENT));
				assertThat(chunks.size()).isGreaterThan(1);
			});
	}

	@Test
	void splitEmptyStringInput() {
		assertUtlToChunksAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.document-splitter.preferences.by=words",
					"spring.ai.oracle.document-splitter.preferences.max=10")
			.run(context -> {
				OracleDocumentSplitter splitter = context.getBean(OracleDocumentSplitter.class);
				List<Document> chunks = splitter.split(new Document(""));
				assertThat(chunks).hasSize(1);
				assertThat(chunks.get(0).getText()).isEmpty();
			});
	}

	private DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername(oracleContainer().getUsername());
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	private void assertUtlToChunksAvailable() {
		try (Connection connection = dataSource().getConnection()) {
			boolean available = isUtlToChunksInvokable(connection);
			Assertions.assertTrue(available, "DBMS_VECTOR_CHAIN.UTL_TO_CHUNKS is not available.");
		}
		catch (SQLException ex) {
			Assertions.fail("Could not verify UTL_TO_CHUNKS availability.", ex);
		}
	}

	private static boolean isUtlToChunksInvokable(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(UTL_TO_CHUNKS_SQL)) {
			Clob inputText = connection.createClob();
			inputText.setString(1, "chunk probe text");
			statement.setObject(1, inputText);
			statement.setObject(2, toOsonBytes(Map.of("by", "words", "max", 50)), OracleType.JSON);
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

	private static byte[] toOsonBytes(Object value) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (JsonGenerator generator = OSON_FACTORY.createGenerator(out)) {
				OBJECT_MAPPER.writeValue(generator, value);
				generator.flush();
			}
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to serialize chunking preferences to Oracle OSON", ex);
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
