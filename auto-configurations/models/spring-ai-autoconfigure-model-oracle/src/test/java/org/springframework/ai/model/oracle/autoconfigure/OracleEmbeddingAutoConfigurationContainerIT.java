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

import java.sql.Array;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.oracle.chunking.OracleChunk;
import org.springframework.ai.oracle.embedding.OracleEmbeddingModel;
import org.springframework.ai.oracle.embedding.OracleEmbeddingPreferences;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify Oracle embedding works through auto-configuration.
 *
 * Enabled only when {@code ORACLE_AUTOCONFIG_IT=true} is set.
 *
 * @author Spring AI Contributors
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_AUTOCONFIG_IT", matches = "(?i:true|1|yes)")
class OracleEmbeddingAutoConfigurationContainerIT {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final String UTL_TO_EMBEDDINGS_SQL = "select dbms_vector_chain.utl_to_embeddings(?, ?) as vectors"
			+ "\nfrom dual";

	private static final DockerImageName ORACLE_IMAGE = DockerImageName.parse("gvenzl/oracle-free:23-faststart");

	private static final String EMBED_TEXT = "Hello from Oracle autoconfiguration integration test";

	private static final String ONNX_DIRECTORY_ALIAS = "MODEL_DIR";

	private static final String ONNX_CONTAINER_DIRECTORY_PATH = "/tmp";

	private static final String ONNX_FILE = "all_MiniLM_L12_v2.onnx";

	private static final String ONNX_RESOURCE = "models/all_MiniLM_L12_v2.onnx";

	private static final String ONNX_MODEL_NAME = "ALL_MINILM_L12_V2";

	private static final String ONNX_CLOUD_JDBC_URL = System.getenv().getOrDefault("ORACLE_JDBC_URL", "");

	private static final String ONNX_CLOUD_JDBC_USERNAME = System.getenv().getOrDefault("ORACLE_USERNAME", "");

	private static final String ONNX_CLOUD_JDBC_PASSWORD = System.getenv().getOrDefault("ORACLE_PASSWORD", "");

	private static final OracleEmbeddingPreferences ONNX_PREFERENCES = OracleEmbeddingPreferences.builder()
		.provider("database")
		.model(ONNX_MODEL_NAME)
		.build();

	private static volatile boolean onnxModelPrepared;

	private static volatile boolean onnxArtifactsPrepared;

	private static volatile OracleContainer oracleContainer;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(DataSource.class, this::dataSource)
		.withConfiguration(AutoConfigurations.of(OracleEmbeddingAutoConfiguration.class));

	@Test
	void getEmbeddingContentUsesConfiguredMetadataMode() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.metadata-mode=all")
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				Document document = new Document("hello", Map.of("source", "it-source"));
				String content = model.getEmbeddingContent(document);
				assertThat(content).contains("hello");
				assertThat(content).contains("source");
			});
	}

	@Test
	void callWithEmptyInputThrows() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=oracle").run(context -> {
			OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
			Assertions.assertThrows(IllegalArgumentException.class,
					() -> model.call(new EmbeddingRequest(List.<String>of(), EmbeddingOptions.builder().build())));
		});
	}

	@Test
	void embedStringReturnsVector() {
		assertUtlToEmbeddingsAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.model=" + ONNX_MODEL_NAME,
					"spring.ai.oracle.embedding.preferences.provider=database",
					"spring.ai.oracle.embedding.preferences.model=" + ONNX_MODEL_NAME)
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				float[] embedding = model.embed(EMBED_TEXT);
				assertThat(embedding).isNotEmpty();
			});
	}

	@Test
	void embedDocumentReturnsVector() {
		assertUtlToEmbeddingsAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.model=" + ONNX_MODEL_NAME,
					"spring.ai.oracle.embedding.preferences.provider=database",
					"spring.ai.oracle.embedding.preferences.model=" + ONNX_MODEL_NAME)
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				Document document = new Document(EMBED_TEXT, Map.of("source", "integration"));
				float[] embedding = model.embed(document);
				assertThat(embedding).isNotEmpty();
			});
	}

	@Test
	void callWithBatchingEnabledReturnsEmbeddings() {
		assertUtlToEmbeddingsAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.model=" + ONNX_MODEL_NAME,
					"spring.ai.oracle.embedding.options.batching=true",
					"spring.ai.oracle.embedding.preferences.provider=database",
					"spring.ai.oracle.embedding.preferences.model=" + ONNX_MODEL_NAME)
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				EmbeddingResponse response = model.call(new EmbeddingRequest(
						List.of(EMBED_TEXT, EMBED_TEXT + " second"), EmbeddingOptions.builder().build()));
				String provider = response.getMetadata().get("provider");
				Boolean batching = response.getMetadata().get("batching");
				Integer preferencesOsonSize = response.getMetadata().get("preferencesOsonSize");

				assertThat(response.getResults()).hasSize(2);
				assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
				assertThat(provider).isEqualTo("oracle-dbms-vector");
				assertThat(batching).isTrue();
				assertThat(preferencesOsonSize).isNotNull().isPositive();
			});
	}

	@Test
	void callWithBatchingDisabledReturnsEmbeddings() {
		assertUtlToEmbeddingsAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.model=" + ONNX_MODEL_NAME,
					"spring.ai.oracle.embedding.options.batching=false",
					"spring.ai.oracle.embedding.preferences.provider=database",
					"spring.ai.oracle.embedding.preferences.model=" + ONNX_MODEL_NAME)
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				EmbeddingResponse response = model.call(new EmbeddingRequest(
						List.of(EMBED_TEXT, EMBED_TEXT + " second"), EmbeddingOptions.builder().build()));
				Boolean batching = response.getMetadata().get("batching");

				assertThat(response.getResults()).hasSize(2);
				assertThat(response.getResults().get(1).getOutput()).isNotEmpty();
				assertThat(batching).isFalse();
			});
	}

	@Test
	void callHonorsOracleRequestOptionsOverrides() {
		assertUtlToEmbeddingsAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.model=" + ONNX_MODEL_NAME,
					"spring.ai.oracle.embedding.options.batching=true",
					"spring.ai.oracle.embedding.preferences.provider=database",
					"spring.ai.oracle.embedding.preferences.model=" + ONNX_MODEL_NAME)
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(EMBED_TEXT),
						org.springframework.ai.oracle.embedding.OracleEmbeddingOptions.builder()
							.model(ONNX_MODEL_NAME)
							.preferences(org.springframework.ai.oracle.embedding.OracleEmbeddingPreferences.builder()
								.provider("database")
								.model(ONNX_MODEL_NAME)
								.build())
							.batching(false)
							.build()));
				Boolean batching = response.getMetadata().get("batching");

				assertThat(response.getResults()).hasSize(1);
				assertThat(response.getMetadata().getModel()).isEqualTo(ONNX_MODEL_NAME);
				assertThat(batching).isFalse();
			});
	}

	@Test
	void callHonorsExplicitDefaultBatchingRequestOption() {
		assertUtlToEmbeddingsAvailable();
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.options.model=" + ONNX_MODEL_NAME,
					"spring.ai.oracle.embedding.options.batching=false",
					"spring.ai.oracle.embedding.preferences.provider=database",
					"spring.ai.oracle.embedding.preferences.model=" + ONNX_MODEL_NAME)
			.run(context -> {
				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				EmbeddingResponse response = model
					.call(new EmbeddingRequest(List.of(EMBED_TEXT, EMBED_TEXT + " second"),
							org.springframework.ai.oracle.embedding.OracleEmbeddingOptions.builder()
								.batching(true)
								.build()));
				Boolean batching = response.getMetadata().get("batching");

				assertThat(response.getResults()).hasSize(2);
				assertThat(batching).isTrue();
			});
	}

	@Test
	void initializeOnStartupLoadsAndUsesOnnxModel() {
		try (Connection connection = dataSource().getConnection()) {
			ensureOnnxArtifactsPrepared(connection);
			dropModelIfExists(connection, ONNX_MODEL_NAME);
		}
		catch (SQLException ex) {
			Assertions.fail("Could not prepare ONNX directory/file before initialize-on-startup test", ex);
		}

		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=oracle",
					"spring.ai.oracle.embedding.initialize-on-startup=true",
					"spring.ai.oracle.embedding.onnx-directory-alias=" + ONNX_DIRECTORY_ALIAS,
					"spring.ai.oracle.embedding.onnx-file=" + ONNX_FILE,
					"spring.ai.oracle.embedding.onnx-model-name=" + ONNX_MODEL_NAME)
			.run(context -> {
				assertThat(context.getBeansOfType(OracleEmbeddingModel.class)).isNotEmpty();
				try (Connection connection = dataSource().getConnection()) {
					assertThat(hasModel(connection, ONNX_MODEL_NAME)).isTrue();
				}
				catch (SQLException ex) {
					Assertions.fail("Could not verify loaded ONNX model", ex);
				}

				OracleEmbeddingModel model = context.getBean(OracleEmbeddingModel.class);
				assertThat(model.embed(EMBED_TEXT)).isNotEmpty();
			});
	}

	private DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername(oracleContainer().getUsername());
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	private DriverManagerDataSource adminDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername("system");
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	private static void dropModelIfExists(Connection connection, String modelName) throws SQLException {
		try (PreparedStatement statement = connection
			.prepareStatement("begin dbms_data_mining.drop_model(?, force => true); end;")) {
			statement.setObject(1, modelName);
			statement.execute();
		}
		catch (SQLException ex) {
			// Ignore cleanup failures to keep the test robust across first/next runs.
		}
	}

	private static void createOrReplaceDirectory(Connection connection, String directoryAlias, String directoryPath)
			throws SQLException {
		String escapedPath = directoryPath.replace("'", "''");
		String sql = "create or replace directory " + directoryAlias + " as '" + escapedPath + "'";
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private void grantOnnxPrivileges() throws SQLException {
		String appUser = oracleContainer().getUsername().toUpperCase();
		try (Connection adminConnection = adminDataSource().getConnection();
				Statement statement = adminConnection.createStatement()) {
			statement.execute("grant create any directory to " + appUser);
			statement.execute("grant create mining model to " + appUser);
		}
	}

	private void assertUtlToEmbeddingsAvailable() {
		try (Connection connection = dataSource().getConnection()) {
			ensureOnnxModelLoaded(connection);
			boolean available = isUtlToEmbeddingsInvokable(connection);
			Assertions.assertTrue(available, "DBMS_VECTOR_CHAIN.UTL_TO_EMBEDDINGS is not available.");
		}
		catch (SQLException ex) {
			Assertions.fail("Could not verify UTL_TO_EMBEDDINGS availability.", ex);
		}
	}

	private void ensureOnnxModelLoaded(Connection connection) throws SQLException {
		if (onnxModelPrepared) {
			return;
		}
		synchronized (OracleEmbeddingAutoConfigurationContainerIT.class) {
			if (onnxModelPrepared) {
				return;
			}
			ensureOnnxArtifactsPrepared(connection);
			dropModelIfExists(connection, ONNX_MODEL_NAME);
			Assertions.assertTrue(
					OracleEmbeddingModel.loadOnnxModel(connection, ONNX_DIRECTORY_ALIAS, ONNX_FILE, ONNX_MODEL_NAME));
			onnxModelPrepared = true;
		}
	}

	private void ensureOnnxArtifactsPrepared(Connection connection) throws SQLException {
		if (onnxArtifactsPrepared) {
			return;
		}
		synchronized (OracleEmbeddingAutoConfigurationContainerIT.class) {
			if (onnxArtifactsPrepared) {
				return;
			}
			MountableFile resourceFile = MountableFile.forClasspathResource(ONNX_RESOURCE);
			oracleContainer().copyFileToContainer(resourceFile, ONNX_CONTAINER_DIRECTORY_PATH + "/" + ONNX_FILE);
			grantOnnxPrivileges();
			createOrReplaceDirectory(connection, ONNX_DIRECTORY_ALIAS, ONNX_CONTAINER_DIRECTORY_PATH);
			onnxArtifactsPrepared = true;
		}
	}

	private static boolean hasModel(Connection connection, String modelName) throws SQLException {
		try (PreparedStatement statement = connection
			.prepareStatement("select count(*) from user_mining_models where model_name = ?")) {
			statement.setString(1, modelName);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() && resultSet.getInt(1) > 0;
			}
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

	private static boolean isUtlToEmbeddingsInvokable(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(UTL_TO_EMBEDDINGS_SQL)) {
			statement.setObject(1, createVectorArrayPayload(connection, List.of("probe")));
			statement.setObject(2, ONNX_PREFERENCES.toByteArray(), OracleTypes.JSON);
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

	private static Array createVectorArrayPayload(Connection connection, List<String> inputs) throws SQLException {
		OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
		Clob[] payload = new Clob[inputs.size()];
		for (int i = 0; i < inputs.size(); i++) {
			Clob clob = connection.createClob();
			try {
				clob.setString(1, OBJECT_MAPPER.writeValueAsString(new OracleChunk(i, inputs.get(i))));
			}
			catch (JsonProcessingException ex) {
				throw new SQLException("Failed to serialize Oracle vector payload", ex);
			}
			payload[i] = clob;
		}
		return oracleConnection.createOracleArray("SYS.VECTOR_ARRAY_T", payload);
	}

}
