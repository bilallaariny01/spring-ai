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

import java.io.IOException;
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

import com.fasterxml.jackson.databind.json.JsonMapper;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.oracle.chunking.OracleChunk;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OracleEmbeddingModel with a real Oracle database.
 *
 * @author Spring AI Contributors
 */
class OracleEmbeddingModelIT {

	private static final String UTL_TO_EMBEDDINGS_SQL = "select dbms_vector_chain.utl_to_embeddings(?, ?) as vectors "
			+ "from dual";

	private static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23-faststart";

	private static final String EMBED_TEXT = "Hello from Oracle integration test";

	private static final String ONNX_RESOURCE_PATH = "models/all_MiniLM_L12_v2.onnx";

	private static final String ONNX_DIRECTORY_ALIAS = "MODEL_DIR";

	private static final String ONNX_CONTAINER_DIRECTORY_PATH = "/tmp";

	private static final String ONNX_FILE = "all_MiniLM_L12_v2.onnx";

	private static final String ONNX_MODEL_NAME = "ALL_MINILM_L12_V2";

	private static final OracleEmbeddingPreferences ONNX_PREFERENCES = OracleEmbeddingPreferences.builder()
		.provider("database")
		.model(ONNX_MODEL_NAME)
		.build();

	private static final JsonMapper OBJECT_MAPPER = new JsonMapper();

	private static volatile boolean onnxModelPrepared;

	private static volatile OracleContainer oracleContainer;

	/**
	 * Verify that document metadata is included in embedding content when
	 * {@link MetadataMode#ALL} is configured.
	 */
	@Test
	void getEmbeddingContentUsesConfiguredMetadataMode() {
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource(),
				OracleEmbeddingOptions.builder().metadataMode(MetadataMode.ALL).build());

		Document document = new Document("hello", Map.of("source", "it-source"));
		String content = model.getEmbeddingContent(document);

		assertThat(content).contains("hello");
		assertThat(content).contains("source");
	}

	/**
	 * Verify that empty embedding requests fail fast with a validation error.
	 */
	@Test
	void callWithEmptyInputThrows() {
		OracleEmbeddingModel model = createModelWithDefaultOptions();
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> model.call(new EmbeddingRequest(List.of(), OracleEmbeddingOptions.builder().build())));
	}

	/**
	 * Verify that embedding plain text returns a non-empty vector.
	 */
	@Test
	void embedStringReturnsVector() {
		assertUtlToEmbeddingsAvailable();
		OracleEmbeddingModel model = createModelWithOnnxOptions();
		float[] embedding = model.embed(EMBED_TEXT);

		assertThat(embedding).isNotEmpty();
	}

	/**
	 * Verify that embedding a {@link Document} returns a non-empty vector.
	 */
	@Test
	void embedDocumentReturnsVector() {
		assertUtlToEmbeddingsAvailable();
		OracleEmbeddingModel model = createModelWithOnnxOptions();
		Document document = new Document(EMBED_TEXT, Map.of("source", "integration"));

		float[] embedding = model.embed(document);
		assertThat(embedding).isNotEmpty();
	}

	/**
	 * Verify batched embedding mode returns one vector per input and batching metadata.
	 */
	@Test
	void callWithBatchingEnabledReturnsEmbeddings() {
		assertUtlToEmbeddingsAvailable();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource(),
				OracleEmbeddingOptions.builder()
					.model(ONNX_MODEL_NAME)
					.preferences(onnxPreferences())
					.batching(true)
					.build());
		String text = EMBED_TEXT;

		EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(text, text + " second"),
				OracleEmbeddingOptions.builder()
					.model(ONNX_MODEL_NAME)
					.preferences(onnxPreferences())
					.batching(true)
					.build()));
		String provider = response.getMetadata().get("provider");
		Boolean batching = response.getMetadata().get("batching");
		Integer preferencesOsonSize = response.getMetadata().get("preferencesOsonSize");

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(provider).isEqualTo("oracle-dbms-vector");
		assertThat(batching).isTrue();
		assertThat(preferencesOsonSize).isNotNull().isPositive();
	}

	/**
	 * Verify non-batched mode returns one vector per input and reports batching as false.
	 */
	@Test
	void callWithBatchingDisabledReturnsEmbeddings() {
		assertUtlToEmbeddingsAvailable();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource(),
				OracleEmbeddingOptions.builder()
					.model(ONNX_MODEL_NAME)
					.preferences(onnxPreferences())
					.batching(false)
					.build());
		String text = EMBED_TEXT;

		EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(text, text + " second"),
				OracleEmbeddingOptions.builder()
					.model(ONNX_MODEL_NAME)
					.preferences(onnxPreferences())
					.batching(false)
					.build()));
		Boolean batching = response.getMetadata().get("batching");

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(batching).isFalse();
	}

	/**
	 * Verify request-level Oracle options override model defaults for a call.
	 */
	@Test
	void callHonorsOracleRequestOptionsOverrides() {
		assertUtlToEmbeddingsAvailable();
		OracleEmbeddingModel model = new OracleEmbeddingModel(dataSource(),
				OracleEmbeddingOptions.builder()
					.model(ONNX_MODEL_NAME)
					.preferences(onnxPreferences())
					.batching(true)
					.build());
		String text = EMBED_TEXT;
		OracleEmbeddingPreferences requestPreferences = onnxPreferences();

		EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(text),
				OracleEmbeddingOptions.builder()
					.model(ONNX_MODEL_NAME)
					.preferences(requestPreferences)
					.batching(false)
					.build()));
		Boolean batching = response.getMetadata().get("batching");
		Integer preferencesOsonSize = response.getMetadata().get("preferencesOsonSize");

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getModel()).isEqualTo(ONNX_MODEL_NAME);
		assertThat(batching).isFalse();
		assertThat(preferencesOsonSize).isEqualTo(requestPreferences.toByteArray().length);
	}

	/**
	 * Verify local ONNX model loading succeeds with a mounted model file.
	 * @throws SQLException if JDBC setup or loading fails
	 */
	@Test
	void loadOnnxModelLoadsModel() throws SQLException {
		oracleContainer().copyFileToContainer(MountableFile.forClasspathResource(ONNX_RESOURCE_PATH),
				ONNX_CONTAINER_DIRECTORY_PATH + "/" + ONNX_FILE);
		grantOnnxPrivileges();

		try (Connection connection = dataSource().getConnection()) {
			dropModelIfExists(connection, ONNX_MODEL_NAME);
			createOrReplaceDirectory(connection, ONNX_DIRECTORY_ALIAS, ONNX_CONTAINER_DIRECTORY_PATH);
			assertThat(OracleEmbeddingModel.loadOnnxModel(connection, ONNX_DIRECTORY_ALIAS, ONNX_FILE, ONNX_MODEL_NAME))
				.isTrue();
			assertThat(hasModel(connection, ONNX_MODEL_NAME)).isTrue();
		}
	}

	/**
	 * Create a model configured with baseline default options.
	 * @return configured embedding model
	 */
	private OracleEmbeddingModel createModelWithDefaultOptions() {
		return new OracleEmbeddingModel(dataSource(), OracleEmbeddingOptions.builder().build());
	}

	/**
	 * Create a model configured to use the test ONNX model and preferences.
	 * @return configured embedding model
	 */
	private OracleEmbeddingModel createModelWithOnnxOptions() {
		return new OracleEmbeddingModel(dataSource(),
				OracleEmbeddingOptions.builder().model(ONNX_MODEL_NAME).preferences(onnxPreferences()).build());
	}

	/**
	 * Build a data source for the application user in the test container.
	 * @return application data source
	 */
	private DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername(oracleContainer().getUsername());
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	/**
	 * Build an admin data source used to grant Oracle privileges required by tests.
	 * @return admin data source
	 */
	private DriverManagerDataSource adminDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername("system");
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	/**
	 * Return shared ONNX embedding preferences for this test class.
	 * @return ONNX preferences
	 */
	private OracleEmbeddingPreferences onnxPreferences() {
		return ONNX_PREFERENCES;
	}

	/**
	 * Drop an Oracle mining model if it already exists.
	 * @param connection active JDBC connection
	 * @param modelName model to drop
	 * @throws SQLException if Oracle reports an unexpected failure
	 */
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

	/**
	 * Create or replace an Oracle directory object mapped to a filesystem path.
	 * @param connection active JDBC connection
	 * @param directoryAlias Oracle directory name
	 * @param directoryPath filesystem path mapped by the directory
	 * @throws SQLException if the DDL execution fails
	 */
	private static void createOrReplaceDirectory(Connection connection, String directoryAlias, String directoryPath)
			throws SQLException {
		String escapedPath = directoryPath.replace("'", "''");
		String sql = "create or replace directory " + directoryAlias + " as '" + escapedPath + "'";
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	/**
	 * Grant the minimum Oracle privileges required for ONNX loading tests.
	 * @throws SQLException if privilege grants fail
	 */
	private void grantOnnxPrivileges() throws SQLException {
		String appUser = oracleContainer().getUsername().toUpperCase();
		try (Connection adminConnection = adminDataSource().getConnection();
				Statement statement = adminConnection.createStatement()) {
			statement.execute("grant create any directory to " + appUser);
			statement.execute("grant create mining model to " + appUser);
		}
	}

	/**
	 * Ensure the database can invoke {@code DBMS_VECTOR_CHAIN.UTL_TO_EMBEDDINGS}.
	 */
	private void assertUtlToEmbeddingsAvailable() {
		try (Connection connection = dataSource().getConnection()) {
			ensureOnnxModelLoaded(connection);
			boolean available = isUtlToEmbeddingsInvokable(connection);
			Assertions.assertTrue(available, "DBMS_VECTOR_CHAIN.UTL_TO_EMBEDDINGS is not available.");
		}
		catch (SQLException | IOException ex) {
			Assertions.fail("Could not verify UTL_TO_EMBEDDINGS availability.", ex);
		}
	}

	/**
	 * Lazily load the ONNX model into Oracle once per JVM execution.
	 * @param connection active JDBC connection
	 * @throws SQLException if setup or loading fails
	 */
	private void ensureOnnxModelLoaded(Connection connection) throws SQLException {
		if (onnxModelPrepared) {
			return;
		}
		synchronized (OracleEmbeddingModelIT.class) {
			if (onnxModelPrepared) {
				return;
			}
			grantOnnxPrivileges();
			dropModelIfExists(connection, ONNX_MODEL_NAME);
			oracleContainer().copyFileToContainer(MountableFile.forClasspathResource(ONNX_RESOURCE_PATH),
					ONNX_CONTAINER_DIRECTORY_PATH + "/" + ONNX_FILE);
			createOrReplaceDirectory(connection, ONNX_DIRECTORY_ALIAS, ONNX_CONTAINER_DIRECTORY_PATH);
			Assertions.assertTrue(
					OracleEmbeddingModel.loadOnnxModel(connection, ONNX_DIRECTORY_ALIAS, ONNX_FILE, ONNX_MODEL_NAME));
			onnxModelPrepared = true;
		}
	}

	/**
	 * Start the shared Oracle test container on first use and reuse it afterward.
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
	 * Check whether a mining model exists in the current schema.
	 * @param connection active JDBC connection
	 * @param modelName model name
	 * @return {@code true} when the model exists
	 * @throws SQLException if query execution fails
	 */
	private static boolean hasModel(Connection connection, String modelName) throws SQLException {
		try (PreparedStatement statement = connection
			.prepareStatement("select count(*) from user_mining_models where model_name = ?")) {
			statement.setString(1, modelName);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() && resultSet.getInt(1) > 0;
			}
		}
	}

	/**
	 * Probe whether {@code utl_to_embeddings} can be invoked in the connected database.
	 * @param connection active JDBC connection
	 * @return {@code true} when invocation succeeds
	 * @throws SQLException if invocation fails with an unexpected database error
	 * @throws IOException if probe payload serialization fails
	 */
	private static boolean isUtlToEmbeddingsInvokable(Connection connection) throws SQLException, IOException {
		try (PreparedStatement statement = connection.prepareStatement(UTL_TO_EMBEDDINGS_SQL)) {
			statement.setObject(1, createVectorArrayPayload(connection, List.of("probe")));
			statement.setObject(2, ONNX_PREFERENCES.toByteArray(), OracleTypes.JSON);
			try (ResultSet ignored = statement.executeQuery()) {
				return true;
			}
		}
		catch (SQLException ex) {
			// Metadata can be present while the SQL function is still not invokable in
			// this image.
			if (ex.getErrorCode() == 904 || ex.getErrorCode() == 6550) {
				return false;
			}
			throw ex;
		}
	}

	/**
	 * Build the Oracle vector-array payload expected by
	 * {@code dbms_vector_chain.utl_to_embeddings}.
	 * @param connection active JDBC connection
	 * @param inputs text inputs
	 * @return Oracle vector array payload
	 * @throws SQLException if JDBC operations fail
	 * @throws IOException if chunk serialization fails
	 */
	private static Array createVectorArrayPayload(Connection connection, List<String> inputs)
			throws SQLException, IOException {
		OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
		Clob[] payload = new Clob[inputs.size()];
		for (int i = 0; i < inputs.size(); i++) {
			Clob clob = connection.createClob();
			clob.setString(1, OBJECT_MAPPER.writeValueAsString(new OracleChunk(i, inputs.get(i))));
			payload[i] = clob;
		}
		return oracleConnection.createOracleArray("SYS.VECTOR_ARRAY_T", payload);
	}

}
