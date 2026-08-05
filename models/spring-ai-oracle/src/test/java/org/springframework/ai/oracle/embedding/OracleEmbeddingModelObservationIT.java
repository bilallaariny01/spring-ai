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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Integration tests for observation instrumentation in {@link OracleEmbeddingModel}.
 *
 * @author Spring AI Contributors
 */
class OracleEmbeddingModelObservationIT {

	private static final String UTL_TO_EMBEDDINGS_SQL = "select dbms_vector_chain.utl_to_embeddings(?, ?) as vectors "
			+ "from dual";

	private static final String ORACLE_IMAGE_NAME = "gvenzl/oracle-free:23-faststart";

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
	 * Verify embedding operation emits expected observation tags and metadata.
	 */
	@Test
	void observationForEmbeddingOperation() {
		assertUtlToEmbeddingsAvailable();

		TestObservationRegistry observationRegistry = TestObservationRegistry.create();
		OracleEmbeddingModel embeddingModel = new OracleEmbeddingModel(dataSource(),
				OracleEmbeddingOptions.builder().model(ONNX_MODEL_NAME).preferences(onnxPreferences()).build(),
				observationRegistry);

		OracleEmbeddingOptions options = OracleEmbeddingOptions.builder()
			.model(ONNX_MODEL_NAME)
			.preferences(onnxPreferences())
			.dimensions(384)
			.build();

		EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of("Here comes the sun"), options);
		EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);

		Assertions.assertFalse(embeddingResponse.getResults().isEmpty());
		Assertions.assertNotNull(embeddingResponse.getMetadata());

		TestObservationRegistryAssert.assertThat(observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultEmbeddingModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("embedding " + ONNX_MODEL_NAME)
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.EMBEDDING.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), "oracle-dbms-vector")
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), ONNX_MODEL_NAME)
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), ONNX_MODEL_NAME)
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_EMBEDDING_DIMENSIONS.asString(), "384")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(embeddingResponse.getMetadata().getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(embeddingResponse.getMetadata().getUsage().getTotalTokens()))
			.hasBeenStarted()
			.hasBeenStopped();
	}

	/**
	 * Return shared ONNX preferences used by this test.
	 * @return ONNX preferences
	 */
	private OracleEmbeddingPreferences onnxPreferences() {
		return ONNX_PREFERENCES;
	}

	/**
	 * Build a container-backed data source for test user.
	 * @return data source
	 */
	private DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(oracleContainer().getJdbcUrl());
		dataSource.setUsername(oracleContainer().getUsername());
		dataSource.setPassword(oracleContainer().getPassword());
		return dataSource;
	}

	/**
	 * Build admin data source used for privilege grants.
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
	 * Assert that {@code UTL_TO_EMBEDDINGS} is invokable.
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
	 * Lazily load ONNX model once before embedding invocations.
	 * @param connection JDBC connection
	 * @throws SQLException on setup/load failures
	 */
	private void ensureOnnxModelLoaded(Connection connection) throws SQLException {
		if (onnxModelPrepared) {
			return;
		}
		synchronized (OracleEmbeddingModelObservationIT.class) {
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
	 * Start and return the shared Oracle test container.
	 * @return running container
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
	 * Probe whether {@code UTL_TO_EMBEDDINGS} is invokable.
	 * @param connection JDBC connection
	 * @return {@code true} when invokable
	 * @throws SQLException on unexpected SQL failures
	 * @throws IOException on payload serialization failures
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
			if (ex.getErrorCode() == 904 || ex.getErrorCode() == 6550) {
				return false;
			}
			throw ex;
		}
	}

	/**
	 * Create Oracle vector array payload for embedding calls.
	 * @param connection JDBC connection
	 * @param inputs text inputs
	 * @return Oracle array payload
	 * @throws SQLException on JDBC failures
	 * @throws IOException on payload serialization failures
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

	/**
	 * Drop model if present to keep reruns idempotent.
	 * @param connection JDBC connection
	 * @param modelName model name
	 * @throws SQLException on unexpected SQL failures
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
	 * Create or replace Oracle directory object.
	 * @param connection JDBC connection
	 * @param directoryAlias directory alias
	 * @param directoryPath mapped path
	 * @throws SQLException on DDL failures
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
	 * Grant required ONNX-related privileges to the app user.
	 * @throws SQLException on grant failures
	 */
	private void grantOnnxPrivileges() throws SQLException {
		String appUser = oracleContainer().getUsername().toUpperCase();
		try (Connection adminConnection = adminDataSource().getConnection();
				Statement statement = adminConnection.createStatement()) {
			statement.execute("grant create any directory to " + appUser);
			statement.execute("grant create mining model to " + appUser);
		}
	}

}
