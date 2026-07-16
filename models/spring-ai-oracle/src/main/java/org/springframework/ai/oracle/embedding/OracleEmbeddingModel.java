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
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.observation.ObservationRegistry;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.VECTOR;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.oracle.chunking.OracleChunk;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Oracle Database embedding model using DBMS_VECTOR_CHAIN.UTL_TO_EMBEDDINGS.
 *
 * @author Spring AI Contributors
 */
public final class OracleEmbeddingModel extends AbstractEmbeddingModel implements InitializingBean {

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private static final JsonMapper OBJECT_MAPPER = new JsonMapper();

	private static final String EMBEDDING_SQL = "select to_vector(json_value(t.column_value, '$.embed_vector' returning clob)) "
			+ "as vector from dbms_vector_chain.utl_to_embeddings(?, ?) t "
			+ "order by json_value(t.column_value, '$.embed_id' returning number)";

	private static final String SINGLE_EMBEDDING_SQL = "select dbms_vector_chain.utl_to_embedding(?, ?) as vector "
			+ "from dual";

	private static final String SET_PROXY_SQL = "begin utl_http.set_proxy(?); end;";

	private static final String LOAD_ONNX_SQL = "begin " + "dbms_data_mining.drop_model(?, force => true); "
			+ "dbms_vector.load_onnx_model(?, ?, ?, "
			+ "json('{\"function\" : \"embedding\", \"embeddingOutput\" : \"embedding\" , \"input\": {\"input\": [\"DATA\"]}}')); "
			+ "end;";

	private static final String LOAD_ONNX_CLOUD_SQL = "begin " + "dbms_data_mining.drop_model(?, force => true); "
			+ "dbms_vector.load_onnx_model_cloud(?, ?, ?, "
			+ "json('{\"function\" : \"embedding\", \"embeddingOutput\" : \"embedding\" , \"input\": {\"input\": [\"DATA\"]}}')); "
			+ "end;";

	private final DataSource dataSource;

	private final OracleEmbeddingOptions defaultOptions;

	private final ObservationRegistry observationRegistry;

	private final RetryTemplate retryTemplate;

	private final boolean initializeOnStartup;

	private final @Nullable String onnxDirectoryAlias;

	private final @Nullable String onnxFile;

	private final @Nullable String onnxModelName;

	private final @Nullable String onnxCredential;

	private final @Nullable String onnxUri;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Create an empty builder for Oracle embedding model configuration.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a builder for Oracle embedding model configuration.
	 * @param dataSource the data source used to connect to Oracle Database
	 * @return a new builder
	 */
	public static Builder builder(DataSource dataSource) {
		return builder().dataSource(dataSource);
	}

	/**
	 * Creates a new {@link OracleEmbeddingModel} with default options.
	 * @param dataSource the data source used to connect to Oracle Database
	 */
	public OracleEmbeddingModel(DataSource dataSource) {
		this(dataSource, null, null, null);
	}

	/**
	 * Creates a new {@link OracleEmbeddingModel} with the given default options.
	 * @param dataSource the data source used to connect to Oracle Database
	 * @param defaultOptions the default embedding options
	 */
	public OracleEmbeddingModel(DataSource dataSource, @Nullable OracleEmbeddingOptions defaultOptions) {
		this(dataSource, defaultOptions, null, null);
	}

	/**
	 * Creates a new {@link OracleEmbeddingModel} with the given default options and
	 * observation registry.
	 * @param dataSource the data source used to connect to Oracle Database
	 * @param defaultOptions the default embedding options
	 * @param observationRegistry the observation registry
	 */
	public OracleEmbeddingModel(DataSource dataSource, @Nullable OracleEmbeddingOptions defaultOptions,
			@Nullable ObservationRegistry observationRegistry) {
		this(dataSource, defaultOptions, observationRegistry, null);
	}

	/**
	 * Creates a new {@link OracleEmbeddingModel} with the given default options,
	 * observation registry, and retry template.
	 * @param dataSource the data source used to connect to Oracle Database
	 * @param defaultOptions the default embedding options
	 * @param observationRegistry the observation registry
	 * @param retryTemplate the retry template
	 */
	public OracleEmbeddingModel(DataSource dataSource, @Nullable OracleEmbeddingOptions defaultOptions,
			@Nullable ObservationRegistry observationRegistry, @Nullable RetryTemplate retryTemplate) {
		this(dataSource, defaultOptions, observationRegistry, retryTemplate, false, null, null, null);
	}

	/**
	 * Creates a new {@link OracleEmbeddingModel} with all configuration options.
	 * @param dataSource the data source used to connect to Oracle Database
	 * @param defaultOptions the default embedding options
	 * @param observationRegistry the observation registry
	 * @param retryTemplate the retry template
	 * @param initializeOnStartup whether to load an ONNX model during bean initialization
	 * @param onnxDirectoryAlias the Oracle directory alias containing the ONNX file
	 * @param onnxFile the ONNX file name
	 * @param onnxModelName the model name to use when loading the ONNX model
	 */
	public OracleEmbeddingModel(DataSource dataSource, @Nullable OracleEmbeddingOptions defaultOptions,
			@Nullable ObservationRegistry observationRegistry, @Nullable RetryTemplate retryTemplate,
			boolean initializeOnStartup, @Nullable String onnxDirectoryAlias, @Nullable String onnxFile,
			@Nullable String onnxModelName) {
		this(dataSource, defaultOptions, observationRegistry, retryTemplate, initializeOnStartup, onnxDirectoryAlias,
				onnxFile, onnxModelName, null, null);
	}

	/**
	 * Creates a new {@link OracleEmbeddingModel} with all configuration options.
	 * @param dataSource the data source used to connect to Oracle Database
	 * @param defaultOptions the default embedding options
	 * @param observationRegistry the observation registry
	 * @param retryTemplate the retry template
	 * @param initializeOnStartup whether to load an ONNX model during bean initialization
	 * @param onnxDirectoryAlias the Oracle directory alias containing the ONNX file
	 * @param onnxFile the ONNX file name
	 * @param onnxModelName the model name to use when loading the ONNX model
	 * @param onnxCredential the Oracle credential name used to access cloud object
	 * storage
	 * @param onnxUri the cloud URI of the ONNX file
	 */
	public OracleEmbeddingModel(DataSource dataSource, @Nullable OracleEmbeddingOptions defaultOptions,
			@Nullable ObservationRegistry observationRegistry, @Nullable RetryTemplate retryTemplate,
			boolean initializeOnStartup, @Nullable String onnxDirectoryAlias, @Nullable String onnxFile,
			@Nullable String onnxModelName, @Nullable String onnxCredential, @Nullable String onnxUri) {
		Assert.notNull(dataSource, "dataSource must not be null");
		OracleEmbeddingOptions options = (defaultOptions != null) ? defaultOptions
				: OracleEmbeddingOptions.builder().build();
		if (initializeOnStartup && !options.isPreferencesSet()) {
			@Nullable String modelName = StringUtils.hasText(onnxModelName) ? onnxModelName : options.getModel();
			if (StringUtils.hasText(modelName)) {
				options.setPreferences(OracleEmbeddingPreferences.builder()
					.provider("database")
					.model(Objects.requireNonNull(modelName))
					.build());
			}
		}
		Assert.notNull(options.getModel(), "model must not be null");
		Assert.notNull(options.getPreferences(), "preferences must not be null");
		Assert.notNull(options.getMetadataMode(), "metadataMode must not be null");
		this.dataSource = dataSource;
		this.defaultOptions = options;
		this.observationRegistry = (observationRegistry != null) ? observationRegistry : ObservationRegistry.NOOP;
		this.retryTemplate = (retryTemplate != null) ? retryTemplate : RetryUtils.DEFAULT_RETRY_TEMPLATE;
		this.initializeOnStartup = initializeOnStartup;
		this.onnxDirectoryAlias = onnxDirectoryAlias;
		this.onnxFile = onnxFile;
		this.onnxModelName = onnxModelName;
		this.onnxCredential = onnxCredential;
		this.onnxUri = onnxUri;
	}

	/**
	 * Initialize the model bean and optionally load an ONNX model into Oracle.
	 */
	@Override
	public void afterPropertiesSet() {
		if (!this.initializeOnStartup) {
			return;
		}

		String modelName = requiredText(
				StringUtils.hasText(this.onnxModelName) ? this.onnxModelName : this.defaultOptions.getModel(),
				"model must not be null or empty when initializeOnStartup is enabled");
		boolean hasLocalConfiguration = StringUtils.hasText(this.onnxDirectoryAlias)
				|| StringUtils.hasText(this.onnxFile);
		boolean hasCloudConfiguration = StringUtils.hasText(this.onnxCredential) || StringUtils.hasText(this.onnxUri);
		if (hasLocalConfiguration && hasCloudConfiguration) {
			throw new IllegalStateException("Only one ONNX load strategy can be configured: "
					+ "local (onnxDirectoryAlias/onnxFile) or cloud (onnxCredential/onnxUri)");
		}

		if (hasCloudConfiguration) {
			String uri = requiredText(this.onnxUri,
					"onnxUri must not be null or empty when cloud initialization is enabled");
			try (Connection connection = this.dataSource.getConnection()) {
				loadOnnxModelCloud(connection, this.onnxCredential, uri, modelName);
			}
			catch (SQLException ex) {
				throw new IllegalStateException(
						"Failed to load ONNX model during Oracle embedding model initialization", ex);
			}
			return;
		}

		String directoryAlias = requiredText(this.onnxDirectoryAlias,
				"onnxDirectoryAlias must not be null or empty when initializeOnStartup is enabled");
		String onnxFile = requiredText(this.onnxFile,
				"onnxFile must not be null or empty when initializeOnStartup is enabled");
		try (Connection connection = this.dataSource.getConnection()) {
			loadOnnxModel(connection, directoryAlias, onnxFile, modelName);
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Failed to load ONNX model during Oracle embedding model initialization",
					ex);
		}
	}

	/**
	 * Generate an embedding for the supplied text using this model's default options.
	 * @param text the text to embed
	 * @return the embedding vector
	 */
	@Override
	public float[] embed(String text) {
		Assert.notNull(text, "text must not be null");

		try {
			return RetryUtils.execute(this.retryTemplate, () -> {
				try (Connection connection = this.dataSource.getConnection()) {
					maybeSetProxy(connection, this.defaultOptions.getProxy());

					return embedSingleText(connection, text, this.defaultOptions.getPreferences());
				}
				catch (SQLException ex) {
					throw toAiException("Failed to generate Oracle embedding", ex);
				}
			});
		}
		catch (TransientAiException | NonTransientAiException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Failed to generate Oracle embedding", ex);
		}
	}

	/**
	 * Generate an embedding for a Spring AI {@link Document}.
	 * @param document the document to embed
	 * @return the embedding vector
	 */
	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "document must not be null");
		return this.embed(getEmbeddingContent(document));
	}

	/**
	 * Return the document content that will be sent for embedding.
	 * @param document the source document
	 * @return the formatted document content
	 */
	@Override
	public String getEmbeddingContent(Document document) {
		Assert.notNull(document, "document must not be null");
		return document.getFormattedContent(this.defaultOptions.getMetadataMode());
	}

	/**
	 * Generate embeddings for the instructions in the supplied request.
	 * @param request the embedding request
	 * @return the embedding response
	 */
	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		Assert.notNull(request, "request must not be null");
		EmbeddingRequest requestToUse = buildEmbeddingRequest(request);
		EmbeddingOptions requestOptions = requestToUse.getOptions();
		if (!(requestOptions instanceof OracleEmbeddingOptions)) {
			throw new IllegalStateException("Embedding options must be OracleEmbeddingOptions");
		}
		OracleEmbeddingOptions optionsToUse = (OracleEmbeddingOptions) requestOptions;
		String modelName = resolveModelName(optionsToUse);

		EmbeddingModelObservationContext observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(requestToUse)
			.provider("oracle-dbms-vector")
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				List<String> texts = requestToUse.getInstructions();
				List<Embedding> data;
				if (CollectionUtils.isEmpty(texts)) {
					data = new ArrayList<>();
				}
				else {
					try {
						data = RetryUtils.execute(this.retryTemplate, () -> {
							List<Embedding> attemptData = new ArrayList<>();
							try (Connection connection = this.dataSource.getConnection()) {
								maybeSetProxy(connection, optionsToUse.getProxy());

								boolean batching = optionsToUse.isBatching();
								byte[] preferences = optionsToUse.getPreferences();

								if (!batching) {
									for (String input : texts) {
										float[] vector = embedSingleText(connection, input, preferences);
										attemptData.add(new Embedding(vector, attemptData.size()));
									}
								}
								else {
									try (VectorArrayPayload payload = createVectorArrayPayload(connection, texts)) {
										embedWithPayload(connection, payload.getArray(), preferences, texts.size(),
												attemptData);
									}
								}
								return attemptData;
							}
							catch (SQLException | IOException ex) {
								throw toAiException("Failed to generate Oracle embeddings", ex);
							}
						});
					}
					catch (TransientAiException | NonTransientAiException ex) {
						throw ex;
					}
					catch (RuntimeException ex) {
						throw new IllegalStateException("Failed to generate Oracle embeddings", ex);
					}
				}

				Map<String, Object> metadata = Map.of("provider", "oracle-dbms-vector", "batching",
						optionsToUse.isBatching(), "preferencesOsonSize", optionsToUse.getPreferences().length);
				EmbeddingResponse response = new EmbeddingResponse(data,
						new EmbeddingResponseMetadata(modelName, new EmptyUsage(), metadata));
				observationContext.setResponse(response);
				return response;
			});
	}

	/**
	 * Merge request-specific options with this model's defaults.
	 * @param requestOptions options from the request
	 * @return merged Oracle embedding options
	 */
	OracleEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions requestOptions) {
		if (requestOptions == null) {
			return this.defaultOptions;
		}

		OracleEmbeddingOptions.Builder builder = OracleEmbeddingOptions.builder()
			.model(this.defaultOptions.getModel())
			.dimensions(this.defaultOptions.getDimensions())
			.preferences(this.defaultOptions.getPreferences())
			.proxy(this.defaultOptions.getProxy())
			.batching(this.defaultOptions.isBatching())
			.metadataMode(this.defaultOptions.getMetadataMode());

		if (requestOptions instanceof OracleEmbeddingOptions) {
			OracleEmbeddingOptions oracleOptions = (OracleEmbeddingOptions) requestOptions;
			String requestModel = oracleOptions.isModelSet() ? oracleOptions.getModel() : null;
			Integer requestDimensions = oracleOptions.getDimensions();
			byte[] requestPreferences = oracleOptions.isPreferencesSet() ? oracleOptions.getPreferences() : null;
			String requestProxy = oracleOptions.getProxy();
			Boolean requestBatching = oracleOptions.isBatchingSet() ? oracleOptions.isBatching() : null;
			MetadataMode requestMetadataMode = oracleOptions.isMetadataModeSet() ? oracleOptions.getMetadataMode()
					: null;

			builder.model(ModelOptionsUtils.mergeOption(requestModel, this.defaultOptions.getModel()))
				.dimensions(ModelOptionsUtils.mergeOption(requestDimensions, this.defaultOptions.getDimensions()))
				.preferences(ModelOptionsUtils.mergeOption(requestPreferences, this.defaultOptions.getPreferences()))
				.proxy(ModelOptionsUtils.mergeOption(requestProxy, this.defaultOptions.getProxy()))
				.batching(ModelOptionsUtils.mergeOption(requestBatching, this.defaultOptions.isBatching()))
				.metadataMode(
						ModelOptionsUtils.mergeOption(requestMetadataMode, this.defaultOptions.getMetadataMode()));
		}
		else {
			builder.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.defaultOptions.getModel()))
				.dimensions(ModelOptionsUtils.mergeOption(requestOptions.getDimensions(),
						this.defaultOptions.getDimensions()));
		}

		return builder.build();
	}

	/**
	 * Validate and normalize an embedding request using merged options.
	 * @param embeddingRequest the incoming embedding request
	 * @return a validated request with merged options
	 */
	private EmbeddingRequest buildEmbeddingRequest(EmbeddingRequest embeddingRequest) {
		OracleEmbeddingOptions mergedOptions = mergeOptions(embeddingRequest.getOptions());

		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		List<String> instructions = embeddingRequest.getInstructions();
		if (CollectionUtils.isEmpty(instructions)) {
			throw new IllegalArgumentException("No embedding input is provided - instructions list is empty");
		}
		boolean hasInvalidInput = instructions.stream().anyMatch(input -> !StringUtils.hasText(input));
		if (hasInvalidInput) {
			throw new IllegalArgumentException("No embedding input is provided - text must not be null or empty");
		}

		return new EmbeddingRequest(instructions, mergedOptions);
	}

	/**
	 * Resolve the model name to report in response metadata.
	 * @param options resolved embedding options
	 * @return the resolved model name
	 */
	private String resolveModelName(OracleEmbeddingOptions options) {
		String model = options.getModel();
		if (model != null) {
			return model;
		}
		String defaultModel = this.defaultOptions.getModel();
		return (defaultModel != null) ? defaultModel : "database";
	}

	/**
	 * Load an ONNX embedding model into Oracle Database.
	 * @param connection the Oracle database connection
	 * @param directoryAlias the Oracle directory alias that contains the ONNX file
	 * @param onnxFile the ONNX file name
	 * @param modelName the model name to register in Oracle Database
	 * @return {@code true} when the load statement completes
	 * @throws SQLException if Oracle fails to load the model
	 */
	public static boolean loadOnnxModel(Connection connection, String directoryAlias, String onnxFile, String modelName)
			throws SQLException {
		Assert.notNull(connection, "connection must not be null");
		Assert.hasText(directoryAlias, "directoryAlias must not be empty");
		Assert.hasText(onnxFile, "onnxFile must not be empty");
		Assert.hasText(modelName, "modelName must not be empty");

		try (PreparedStatement statement = connection.prepareStatement(LOAD_ONNX_SQL)) {
			statement.setObject(1, modelName);
			statement.setObject(2, directoryAlias);
			statement.setObject(3, onnxFile);
			statement.setObject(4, modelName);
			statement.execute();
			return true;
		}
	}

	/**
	 * Load an ONNX embedding model from cloud object storage into Oracle Database.
	 * @param connection the Oracle database connection
	 * @param credential the Oracle credential name used to access cloud object storage;
	 * may be {@code null} for pre-authenticated URLs
	 * @param uri the cloud URI of the ONNX model
	 * @param modelName the model name to register in Oracle Database
	 * @return {@code true} when the load statement completes
	 * @throws SQLException if Oracle fails to load the model
	 */
	public static boolean loadOnnxModelCloud(Connection connection, @Nullable String credential, String uri,
			String modelName) throws SQLException {
		Assert.notNull(connection, "connection must not be null");
		Assert.hasText(uri, "uri must not be empty");
		Assert.hasText(modelName, "modelName must not be empty");

		try (PreparedStatement statement = connection.prepareStatement(LOAD_ONNX_CLOUD_SQL)) {
			statement.setObject(1, modelName);
			statement.setObject(2, modelName);
			statement.setObject(3, credential);
			statement.setObject(4, uri);
			statement.execute();
			return true;
		}
	}

	/**
	 * Configure Oracle HTTP proxy for the current connection when provided.
	 * @param connection active JDBC connection
	 * @param proxy proxy value understood by {@code utl_http.set_proxy}
	 * @throws SQLException if proxy configuration fails
	 */
	private void maybeSetProxy(Connection connection, @Nullable String proxy) throws SQLException {
		if (!StringUtils.hasText(proxy)) {
			return;
		}
		try (PreparedStatement statement = connection.prepareStatement(SET_PROXY_SQL)) {
			statement.setObject(1, proxy);
			statement.execute();
		}
	}

	/**
	 * Execute a batched embedding call and append results to the output list.
	 * @param connection active JDBC connection
	 * @param payload Oracle vector payload input
	 * @param preferencesOson Oracle JSON preferences bytes
	 * @param expectedCount number of embeddings expected from Oracle
	 * @param output target list where embeddings are appended
	 * @throws SQLException if Oracle embedding execution fails
	 */
	private void embedWithPayload(Connection connection, Object payload, byte[] preferencesOson, int expectedCount,
			List<Embedding> output) throws SQLException {
		int initialSize = output.size();

		try (PreparedStatement statement = connection.prepareStatement(EMBEDDING_SQL)) {

			statement.setObject(1, payload);

			statement.setObject(2, preferencesOson, OracleType.JSON);

			try (ResultSet resultSet = statement.executeQuery()) {

				while (resultSet.next()) {

					VECTOR vectorObj = resultSet.getObject("vector", VECTOR.class);

					float[] vector = vectorObj.toFloatArray();
					output.add(new Embedding(vector, output.size()));
				}
			}
		}

		int actualCount = output.size() - initialSize;
		if (actualCount != expectedCount) {
			throw new IllegalStateException("Oracle embedding response count mismatch: expected " + expectedCount
					+ " but received " + actualCount);
		}
	}

	/**
	 * Generate one embedding vector for a single input text.
	 * @param connection active JDBC connection
	 * @param text input text to embed
	 * @param preferencesOson Oracle JSON preferences bytes
	 * @return the resulting embedding vector
	 * @throws SQLException if Oracle embedding execution fails
	 */
	private float[] embedSingleText(Connection connection, String text, byte[] preferencesOson) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SINGLE_EMBEDDING_SQL)) {
			Clob clob = connection.createClob();
			try {
				clob.setString(1, text);
				statement.setObject(1, clob);
				statement.setObject(2, preferencesOson, OracleTypes.JSON);

				try (ResultSet resultSet = statement.executeQuery()) {
					if (!resultSet.next()) {
						throw new IllegalStateException("Oracle embedding response must not be empty");
					}
					VECTOR vectorObj = resultSet.getObject("vector", VECTOR.class);
					return vectorObj.toFloatArray();
				}
			}
			finally {
				clob.free();
			}
		}
	}

	/**
	 * Convert input strings into the Oracle vector array payload format.
	 * @param connection active JDBC connection
	 * @param inputs texts to encode as Oracle chunks
	 * @return Oracle array payload and its temporary CLOB resources
	 * @throws SQLException if Oracle array creation fails
	 * @throws IOException if chunk serialization fails
	 */
	private VectorArrayPayload createVectorArrayPayload(Connection connection, List<String> inputs)
			throws SQLException, IOException {
		OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
		Clob[] payload = new Clob[inputs.size()];
		try {
			for (int i = 0; i < inputs.size(); i++) {
				Clob clob = connection.createClob();
				payload[i] = clob;
				clob.setString(1, OBJECT_MAPPER.writeValueAsString(new OracleChunk(i, inputs.get(i))));
			}
			Array array = oracleConnection.createOracleArray("SYS.VECTOR_ARRAY_T", payload);
			return new VectorArrayPayload(array, payload);
		}
		catch (SQLException | IOException ex) {
			try {
				freePayloadResources(null, payload);
			}
			catch (SQLException cleanupException) {
				ex.addSuppressed(cleanupException);
			}
			throw ex;
		}
	}

	private static void freePayloadResources(@Nullable Array array, Clob[] clobs) throws SQLException {
		SQLException failure = null;
		if (array != null) {
			try {
				array.free();
			}
			catch (SQLException ex) {
				failure = ex;
			}
		}
		for (Clob clob : clobs) {
			if (clob == null) {
				continue;
			}
			try {
				clob.free();
			}
			catch (SQLException ex) {
				if (failure == null) {
					failure = ex;
				}
				else {
					failure.addSuppressed(ex);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * Set the Micrometer observation convention used for embedding calls.
	 * @param observationConvention the observation convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Gets the default embedding options for this model.
	 * @return the default embedding options
	 */
	public OracleEmbeddingOptions getOptions() {
		return this.defaultOptions;
	}

	/**
	 * Convert low-level SQL exceptions into Spring AI retry-aware exceptions.
	 * @param message base error message
	 * @param ex original exception
	 * @return mapped runtime exception
	 */
	private RuntimeException toAiException(String message, Exception ex) {
		if (ex instanceof SQLTransientException || ex instanceof SQLRecoverableException) {
			return new TransientAiException(message, ex);
		}
		return new NonTransientAiException(message, ex);
	}

	/**
	 * Require a non-empty text value and fail with a state error otherwise.
	 * @param value candidate text
	 * @param message exception message when value is empty
	 * @return the validated text value
	 */
	private String requiredText(@Nullable String value, String message) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalStateException(message);
		}
		return value;
	}

	private static final class VectorArrayPayload implements AutoCloseable {

		private final Array array;

		private final Clob[] clobs;

		private VectorArrayPayload(Array array, Clob[] clobs) {
			this.array = array;
			this.clobs = clobs;
		}

		private Array getArray() {
			return this.array;
		}

		@Override
		public void close() throws SQLException {
			freePayloadResources(this.array, this.clobs);
		}

	}

	public static final class Builder {

		private @Nullable DataSource dataSource;

		private @Nullable OracleEmbeddingOptions defaultOptions;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable RetryTemplate retryTemplate;

		private boolean initializeOnStartup;

		private @Nullable String onnxDirectoryAlias;

		private @Nullable String onnxFile;

		private @Nullable String onnxModelName;

		private @Nullable String onnxCredential;

		private @Nullable String onnxUri;

		private Builder() {
		}

		/**
		 * Set Oracle data source.
		 * @param dataSource the data source used to connect to Oracle Database
		 * @return this builder
		 */
		public Builder dataSource(DataSource dataSource) {
			Assert.notNull(dataSource, "dataSource must not be null");
			this.dataSource = dataSource;
			return this;
		}

		/**
		 * Set default embedding options.
		 * @param defaultOptions default embedding options
		 * @return this builder
		 */
		public Builder defaultOptions(@Nullable OracleEmbeddingOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		/**
		 * Set observation registry used for telemetry.
		 * @param observationRegistry observation registry
		 * @return this builder
		 */
		public Builder observationRegistry(@Nullable ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Set retry template for embedding operations.
		 * @param retryTemplate retry template
		 * @return this builder
		 */
		public Builder retryTemplate(@Nullable RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		/**
		 * Enable or disable ONNX load during bean initialization.
		 * @param initializeOnStartup whether ONNX should be loaded during initialization
		 * @return this builder
		 */
		public Builder initializeOnStartup(boolean initializeOnStartup) {
			this.initializeOnStartup = initializeOnStartup;
			return this;
		}

		/**
		 * Set Oracle directory alias for local ONNX loading.
		 * @param onnxDirectoryAlias Oracle directory alias
		 * @return this builder
		 */
		public Builder onnxDirectoryAlias(@Nullable String onnxDirectoryAlias) {
			this.onnxDirectoryAlias = onnxDirectoryAlias;
			return this;
		}

		/**
		 * Set ONNX file name for local loading.
		 * @param onnxFile ONNX file name
		 * @return this builder
		 */
		public Builder onnxFile(@Nullable String onnxFile) {
			this.onnxFile = onnxFile;
			return this;
		}

		/**
		 * Set ONNX model name used when loading the model.
		 * @param onnxModelName model name
		 * @return this builder
		 */
		public Builder onnxModelName(@Nullable String onnxModelName) {
			this.onnxModelName = onnxModelName;
			return this;
		}

		/**
		 * Set Oracle credential used for cloud ONNX loading.
		 * @param onnxCredential credential name
		 * @return this builder
		 */
		public Builder onnxCredential(@Nullable String onnxCredential) {
			this.onnxCredential = onnxCredential;
			return this;
		}

		/**
		 * Set cloud URI for ONNX loading.
		 * @param onnxUri cloud URI
		 * @return this builder
		 */
		public Builder onnxUri(@Nullable String onnxUri) {
			this.onnxUri = onnxUri;
			return this;
		}

		/**
		 * Build a configured {@link OracleEmbeddingModel}.
		 * @return configured embedding model
		 */
		public OracleEmbeddingModel build() {
			DataSource configuredDataSource = Objects.requireNonNull(this.dataSource, "dataSource must not be null");
			return new OracleEmbeddingModel(configuredDataSource, this.defaultOptions, this.observationRegistry,
					this.retryTemplate, this.initializeOnStartup, this.onnxDirectoryAlias, this.onnxFile,
					this.onnxModelName, this.onnxCredential, this.onnxUri);
		}

	}

}
