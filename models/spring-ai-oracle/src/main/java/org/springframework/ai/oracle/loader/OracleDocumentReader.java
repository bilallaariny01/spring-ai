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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

import oracle.jdbc.OracleType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Oracle document reader for local resources (file or directory) and Oracle database
 * tables.
 *
 * This reader always requires a {@link DataSource} because it uses Oracle
 * {@code DBMS_VECTOR_CHAIN.UTL_TO_TEXT} to extract text and metadata from both resource
 * and table sources. For local resources, use constructors that accept a
 * {@link DataSource} with a Path, Resource, or resource URL. For table sources, use the
 * constructor that accepts owner, table name, and column name.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentReader implements DocumentReader {

	public static final String METADATA_SOURCE = "source";

	private static final String COLUMN_ROWID = "rowid";

	private static final String COLUMN_TEXT = "text";

	private static final String COLUMN_METADATA = "metadata";

	private static final String META_TAG = "meta";

	private static final String META_NAME_ATTR = "name";

	private static final String META_CONTENT_ATTR = "content";

	private static final String FILE_NAME_METADATA = "file_name";

	private static final String ABSOLUTE_DIRECTORY_PATH_METADATA = "absolute_directory_path";

	private static final Pattern ORACLE_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]{0,127}$");

	private final DataSource dataSource;

	private final @Nullable Resource resource;

	private final @Nullable String owner;

	private final @Nullable String tableName;

	private final @Nullable String columnName;

	private final byte @Nullable [] PreferencesOson;

	/**
	 * Create a reader for a local resource URL.
	 * @param dataSource the Oracle data source
	 * @param resourceUrl the resource URL
	 */
	public OracleDocumentReader(DataSource dataSource, String resourceUrl) {
		this(dataSource, new DefaultResourceLoader().getResource(resourceUrl), null);
	}

	/**
	 * Create a reader for a local path and Oracle conversion preferences.
	 * @param dataSource the Oracle data source
	 * @param path file or directory path
	 */
	public OracleDocumentReader(DataSource dataSource, Path path) {
		this(dataSource, path.toUri().toString());
	}

	/**
	 * Create a reader for a local resource and Oracle conversion preferences.
	 * @param dataSource the Oracle data source
	 * @param resource the resource to read
	 */
	public OracleDocumentReader(DataSource dataSource, Resource resource) {
		this(dataSource, resource, null);
	}

	/**
	 * Internal constructor for resource-based loading with optional text conversion
	 * preferences.
	 * @param dataSource the Oracle data source
	 * @param resource source resource
	 * @param PreferencesOson Oracle JSON preferences bytes
	 */
	private OracleDocumentReader(DataSource dataSource, Resource resource, byte @Nullable [] PreferencesOson) {
		Assert.notNull(dataSource, "dataSource must not be null");
		Assert.notNull(resource, "resource must not be null");
		this.dataSource = dataSource;
		this.resource = resource;
		this.owner = null;
		this.tableName = null;
		this.columnName = null;
		this.PreferencesOson = (PreferencesOson != null) ? PreferencesOson.clone() : null;
	}

	/**
	 * Create a reader for a database table source.
	 * @param dataSource the Oracle data source
	 * @param owner table owner
	 * @param tableName table name
	 * @param columnName source text column name
	 */
	public OracleDocumentReader(DataSource dataSource, String owner, String tableName, String columnName) {
		this(dataSource, owner, tableName, columnName, null);
	}

	/**
	 * Internal constructor for table-based loading with optional text conversion
	 * preferences.
	 * @param dataSource the Oracle data source
	 * @param owner table owner
	 * @param tableName table name
	 * @param columnName source text column name
	 * @param PreferencesOson Oracle JSON preferences bytes
	 */
	private OracleDocumentReader(DataSource dataSource, String owner, String tableName, String columnName,
			byte @Nullable [] PreferencesOson) {
		Assert.notNull(dataSource, "dataSource must not be null");
		Assert.hasText(owner, "owner must not be empty");
		Assert.hasText(tableName, "tableName must not be empty");
		Assert.hasText(columnName, "columnName must not be empty");
		this.dataSource = dataSource;
		this.resource = null;
		this.owner = validateIdentifier(owner, "owner");
		this.tableName = validateIdentifier(tableName, "tablename");
		this.columnName = validateIdentifier(columnName, "colname");
		this.PreferencesOson = (PreferencesOson != null) ? PreferencesOson.clone() : null;
	}

	/**
	 * Create a builder for document loading scoped to a data source.
	 * @param dataSource the Oracle data source
	 * @return a new builder
	 */
	public static Builder builder(DataSource dataSource) {
		return builder().dataSource(dataSource);
	}

	/**
	 * Create a builder for resource-based document loading with Oracle conversion
	 * preferences.
	 * @param dataSource the Oracle data source
	 * @param resource the source resource
	 * @return a new builder
	 */
	public static Builder builder(DataSource dataSource, Resource resource) {
		return builder(dataSource).resource(resource);
	}

	/**
	 * Create a builder for resource-based document loading with explicit Oracle
	 * conversion preferences.
	 * @param dataSource the Oracle data source
	 * @param resource the source resource
	 * @param preferences document conversion preferences
	 * @return a new builder
	 */
	public static Builder builder(DataSource dataSource, Resource resource, OracleDocumentPreferences preferences) {
		return builder(dataSource, resource).preferences(preferences);
	}

	/**
	 * Create a builder for table-based document loading with Oracle conversion
	 * preferences.
	 * @param dataSource the Oracle data source
	 * @param owner table owner
	 * @param tableName table name
	 * @param columnName source text column name
	 * @return a new builder
	 */
	public static Builder builder(DataSource dataSource, String owner, String tableName, String columnName) {
		return builder(dataSource).tableSource(owner, tableName, columnName);
	}

	/**
	 * Create an empty builder for document loading.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Load documents from the configured source (resource or table).
	 * @return loaded documents
	 */
	@Override
	public List<Document> get() {
		try {
			Resource configuredResource = this.resource;
			if (configuredResource != null) {
				return loadDocumentsFromResource(configuredResource);
			}
			String configuredOwner = this.owner;
			String configuredTableName = this.tableName;
			String configuredColumnName = this.columnName;
			if (configuredOwner == null || configuredTableName == null || configuredColumnName == null) {
				throw new IllegalStateException("table source is required for table-based loading");
			}
			try (Connection connection = this.dataSource.getConnection()) {
				return loadDocumentsFromTable(connection, configuredOwner, configuredTableName, configuredColumnName);
			}
		}
		catch (SQLException | IOException ex) {
			throw new IllegalStateException("Failed to load documents using OracleDocumentReader", ex);
		}
		catch (RuntimeException ex) {
			if (ex instanceof IllegalStateException) {
				throw ex;
			}
			throw new IllegalStateException("Failed to load documents using OracleDocumentReader", ex);
		}
	}

	/**
	 * Load documents from a resource path, handling both single files and directories.
	 * @return loaded documents
	 * @throws IOException if file access fails
	 * @throws SQLException if Oracle text conversion fails
	 */
	private List<Document> loadDocumentsFromResource(Resource resource) throws IOException, SQLException {
		if (resource.isFile()) {
			Path path = resource.getFile().toPath().toAbsolutePath().normalize();
			if (Files.isDirectory(path)) {
				return loadDocumentsFromDirectory(path);
			}
			try (Connection connection = this.dataSource.getConnection()) {
				Document document = loadDocument(connection, path, this.PreferencesOson);
				return (document != null) ? List.of(document) : List.of();
			}
		}

		byte[] bytes;
		try (InputStream inputStream = resource.getInputStream()) {
			bytes = inputStream.readAllBytes();
		}
		try (Connection connection = this.dataSource.getConnection()) {
			Document document = loadDocument(connection, bytes, resource.getFilename(), null, resourceSource(resource),
					this.PreferencesOson);
			return (document != null) ? List.of(document) : List.of();
		}
	}

	/**
	 * Recursively load documents from a directory.
	 * @param root root directory
	 * @return loaded documents
	 * @throws IOException if directory traversal fails
	 * @throws SQLException if Oracle text conversion fails
	 */
	private List<Document> loadDocumentsFromDirectory(Path root) throws IOException, SQLException {
		List<Document> documents = new ArrayList<>();
		try (Connection connection = this.dataSource.getConnection(); Stream<Path> paths = Files.walk(root)) {
			paths.filter(Files::isRegularFile).forEach(path -> {
				try {
					Document document = loadDocument(connection, path, this.PreferencesOson);
					if (document != null) {
						documents.add(document);
					}
				}
				catch (IOException | SQLException ex) {
					throw new RuntimeException("cannot load document", ex);
				}
			});
		}
		return documents;
	}

	/**
	 * Convert a single file into a Spring AI document using Oracle text extraction.
	 * @param connection active JDBC connection
	 * @param path file path
	 * @param PreferencesOson optional Oracle JSON preferences
	 * @return created document or {@code null} if no row is returned
	 * @throws IOException if file reading fails
	 * @throws SQLException if Oracle text conversion fails
	 */
	private @Nullable Document loadDocument(Connection connection, Path path, byte @Nullable [] PreferencesOson)
			throws IOException, SQLException {
		Path fileName = path.getFileName();
		Path parent = path.getParent();
		return loadDocument(connection, Files.readAllBytes(path), (fileName != null) ? fileName.toString() : null,
				(parent != null) ? parent.toString() : null, path.toString(), PreferencesOson);
	}

	/**
	 * Convert resource bytes into a Spring AI document using Oracle text extraction.
	 * @param connection active JDBC connection
	 * @param bytes resource content
	 * @param fileName source file name, if available
	 * @param directoryPath absolute source directory path, if available
	 * @param source source description or URI
	 * @param PreferencesOson optional Oracle JSON preferences
	 * @return created document or {@code null} if no row is returned
	 * @throws SQLException if Oracle text conversion fails
	 */
	private @Nullable Document loadDocument(Connection connection, byte[] bytes, @Nullable String fileName,
			@Nullable String directoryPath, String source, byte @Nullable [] PreferencesOson) throws SQLException {
		Document document = null;
		try (PreparedStatement statement = connection
			.prepareStatement("select dbms_vector_chain.utl_to_text(?, ?) text, "
					+ "dbms_vector_chain.utl_to_text(?, json('{\"plaintext\":\"false\"}')) metadata from dual")) {
			Blob blob = connection.createBlob();
			try {
				blob.setBytes(1, bytes);
				statement.setBlob(1, blob);
				statement.setObject(2, PreferencesOson, OracleType.JSON);
				statement.setBlob(3, blob);

				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						String text = resultSet.getString(COLUMN_TEXT);
						String html = resultSet.getString(COLUMN_METADATA);
						Map<String, Object> metadata = getMetadata(html);
						if (fileName != null) {
							metadata.put(FILE_NAME_METADATA, fileName);
						}
						if (directoryPath != null) {
							metadata.put(ABSOLUTE_DIRECTORY_PATH_METADATA, directoryPath);
						}
						metadata.put(METADATA_SOURCE, source);
						document = new Document(text, metadata);
					}
				}
			}
			finally {
				blob.free();
			}
		}
		return document;
	}

	/**
	 * Resolve a stable metadata value for a stream-backed resource.
	 * @param resource source resource
	 * @return resource URI when available, otherwise its description
	 */
	private static String resourceSource(Resource resource) {
		try {
			return resource.getURI().toString();
		}
		catch (IOException ex) {
			return resource.getDescription();
		}
	}

	/**
	 * Load documents from a table column and enrich each row with metadata.
	 * @param connection active JDBC connection
	 * @param owner table owner
	 * @param table table name
	 * @param column source text column name
	 * @return loaded documents
	 * @throws SQLException if query execution fails
	 */
	private List<Document> loadDocumentsFromTable(Connection connection, String owner, String table, String column)
			throws SQLException {
		List<Document> documents = new ArrayList<>();
		String query = String.format("select rowid, dbms_vector_chain.utl_to_text(t.%s, ?) as text, "
				+ "dbms_vector_chain.utl_to_text(t.%s, json('{\"plaintext\":\"false\"}')) as metadata "
				+ "from %s.%s t", column, column, owner, table);
		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setObject(1, this.PreferencesOson, OracleType.JSON);

			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					String rowId = resultSet.getString(COLUMN_ROWID);
					String text = resultSet.getString(COLUMN_TEXT);
					String html = resultSet.getString(COLUMN_METADATA);
					Map<String, Object> metadata = getMetadata(html);
					metadata.put("owner", owner);
					metadata.put("table", table);
					metadata.put("column", column);
					metadata.put("rowid", rowId);
					documents.add(new Document(text, metadata));
				}
			}
		}
		return documents;
	}

	/**
	 * Extract metadata values from HTML meta tags.
	 * @param html metadata HTML fragment
	 * @return parsed metadata map
	 */
	private static Map<String, Object> getMetadata(String html) {
		Map<String, Object> metadata = new HashMap<>();
		org.jsoup.nodes.Document doc = Jsoup.parse(html);
		Elements metaTags = doc.getElementsByTag(META_TAG);
		for (Element metaTag : metaTags) {
			String name = metaTag.attr(META_NAME_ATTR);
			if (name.isEmpty()) {
				continue;
			}
			String content = metaTag.attr(META_CONTENT_ATTR);
			metadata.put(name, content);
		}
		return metadata;
	}

	/**
	 * Validate an Oracle identifier against the supported naming pattern.
	 * @param value identifier value
	 * @param fieldName field name used in error messages
	 * @return validated identifier
	 */
	private static String validateIdentifier(String value, String fieldName) {
		if (!ORACLE_IDENTIFIER_PATTERN.matcher(value).matches()) {
			throw new InvalidParameterException("Invalid table preference: invalid " + fieldName);
		}
		return value;
	}

	public static final class Builder {

		private @Nullable DataSource dataSource;

		private @Nullable Resource resource;

		private @Nullable String owner;

		private @Nullable String tableName;

		private @Nullable String columnName;

		private final OracleDocumentPreferences.Builder preferencesBuilder = OracleDocumentPreferences.builder();

		/**
		 * Create an empty builder. Configure source and preferences before build.
		 */
		private Builder() {
		}

		/**
		 * Set the Oracle data source.
		 * @param dataSource the Oracle data source
		 * @return this builder
		 */
		public Builder dataSource(DataSource dataSource) {
			Assert.notNull(dataSource, "dataSource must not be null");
			this.dataSource = dataSource;
			return this;
		}

		/**
		 * Set a resource source for document loading.
		 * @param resource source resource
		 * @return this builder
		 */
		public Builder resource(Resource resource) {
			Assert.notNull(resource, "resource must not be null");
			this.resource = resource;
			this.owner = null;
			this.tableName = null;
			this.columnName = null;
			return this;
		}

		/**
		 * Set a table source for document loading.
		 * @param owner table owner
		 * @param tableName table name
		 * @param columnName source text column
		 * @return this builder
		 */
		public Builder tableSource(String owner, String tableName, String columnName) {
			this.owner = owner;
			this.tableName = tableName;
			this.columnName = columnName;
			this.resource = null;
			return this;
		}

		/**
		 * Apply all document conversion preferences in one call.
		 * @param preferences document conversion preferences
		 * @return this builder
		 */
		public Builder preferences(OracleDocumentPreferences preferences) {
			Assert.notNull(preferences, "preferences must not be null");
			this.preferencesBuilder.plaintext(preferences.getPlaintext())
				.charset(preferences.getCharset())
				.format(preferences.getFormat());
			return this;
		}

		/**
		 * Build an {@link OracleDocumentReader} with the configured source and
		 * preferences.
		 * @return configured reader
		 */
		public OracleDocumentReader build() {
			Assert.notNull(this.dataSource, "dataSource must not be null");
			DataSource configuredDataSource = Objects.requireNonNull(this.dataSource);
			OracleDocumentPreferences resolvedPreferences = this.preferencesBuilder.build();
			byte @Nullable [] preferencesOson = resolvedPreferences.isEmpty() ? null
					: resolvedPreferences.toByteArray();
			Resource configuredResource = this.resource;
			if (configuredResource != null) {
				return new OracleDocumentReader(configuredDataSource, configuredResource, preferencesOson);
			}
			Assert.hasText(this.owner, "owner must not be empty");
			Assert.hasText(this.tableName, "tableName must not be empty");
			Assert.hasText(this.columnName, "columnName must not be empty");
			String configuredOwner = Objects.requireNonNull(this.owner);
			String configuredTableName = Objects.requireNonNull(this.tableName);
			String configuredColumnName = Objects.requireNonNull(this.columnName);
			return new OracleDocumentReader(configuredDataSource, configuredOwner, configuredTableName,
					configuredColumnName, preferencesOson);
		}

	}

}
