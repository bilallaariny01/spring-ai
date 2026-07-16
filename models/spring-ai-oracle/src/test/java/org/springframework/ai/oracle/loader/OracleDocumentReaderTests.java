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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OracleDocumentReader constructor and non-container error paths.
 *
 * @author Spring AI Contributors
 */
class OracleDocumentReaderTests {

	@TempDir
	Path tempDir;

	/**
	 * Verify table constructor rejects null data source.
	 */
	@Test
	void nullDataSourceForTableThrows() {
		assertThatThrownBy(() -> new OracleDocumentReader((DataSource) null, "APP", "DOCS", "TEXT"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("dataSource must not be null");
	}

	/**
	 * Verify table constructor rejects empty owner.
	 */
	@Test
	void emptyOwnerThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, "", "DOCS", "TEXT"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("owner must not be empty");
	}

	/**
	 * Verify table constructor rejects empty table name.
	 */
	@Test
	void emptyTableNameThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, "APP", "", "TEXT"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("tableName must not be empty");
	}

	/**
	 * Verify table constructor rejects empty column name.
	 */
	@Test
	void emptyColumnNameThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, "APP", "DOCS", ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("columnName must not be empty");
	}

	/**
	 * Verify invalid owner identifiers are rejected.
	 */
	@Test
	void invalidOwnerIdentifierThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, "APP;DROP", "DOCS", "TEXT"))
			.isInstanceOf(InvalidParameterException.class)
			.hasMessageContaining("invalid owner");
	}

	/**
	 * Verify invalid table identifiers are rejected.
	 */
	@Test
	void invalidTableIdentifierThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, "APP", "DOCS;", "TEXT"))
			.isInstanceOf(InvalidParameterException.class)
			.hasMessageContaining("invalid tablename");
	}

	/**
	 * Verify invalid column identifiers are rejected.
	 */
	@Test
	void invalidColumnIdentifierThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, "APP", "DOCS", "text;drop"))
			.isInstanceOf(InvalidParameterException.class)
			.hasMessageContaining("invalid colname");
	}

	/**
	 * Verify resource constructor rejects null resource.
	 */
	@Test
	void nullResourceThrows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatThrownBy(() -> new OracleDocumentReader(dataSource, (Resource) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resource must not be null");
	}

	/**
	 * Verify resource URL constructor rejects null data source.
	 */
	@Test
	void nullDataSourceForResourceUrlThrows() {
		assertThatThrownBy(() -> new OracleDocumentReader((DataSource) null, "file:/tmp/doc.md"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("dataSource must not be null");
	}

	/**
	 * Verify path constructor rejects null data source.
	 */
	@Test
	void nullDataSourceForPathThrows() {
		assertThatThrownBy(() -> new OracleDocumentReader((DataSource) null, Path.of("/tmp/doc.md")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("dataSource must not be null");
	}

	/**
	 * Verify resource constructor rejects null data source.
	 */
	@Test
	void nullDataSourceForResourceThrows() {
		assertThatThrownBy(() -> new OracleDocumentReader((DataSource) null, new FileSystemResource("doc.md")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("dataSource must not be null");
	}

	/**
	 * Verify null custom preference is allowed by constructor path.
	 */
	@Test
	void nullCustomPreferenceIsAllowed() throws Exception {
		Path file = this.tempDir.resolve("pref.md");
		Files.writeString(file, "x");
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		assertThatCode(() -> new OracleDocumentReader(dataSource, new FileSystemResource(file)))
			.doesNotThrowAnyException();
	}

	/**
	 * Verify builder rejects null structured preferences.
	 */
	@Test
	void builderRejectsNullPreferences() throws Exception {
		Path file = this.tempDir.resolve("pref-null.md");
		Files.writeString(file, "x");
		DriverManagerDataSource dataSource = new DriverManagerDataSource();

		assertThatThrownBy(
				() -> OracleDocumentReader.builder(dataSource, new FileSystemResource(file)).preferences(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("preferences must not be null");
	}

	/**
	 * Verify builder accepts valid structured preferences.
	 */
	@Test
	void builderAcceptsStructuredPreferences() throws Exception {
		Path file = this.tempDir.resolve("pref-structured.md");
		Files.writeString(file, "x");
		DriverManagerDataSource dataSource = new DriverManagerDataSource();

		assertThatCode(() -> OracleDocumentReader.builder(dataSource, new FileSystemResource(file))
			.preferences(OracleDocumentPreferences.builder().plaintext(true).format("text").build())
			.build()).doesNotThrowAnyException();
	}

	/**
	 * Verify builder without preferences stores null JSON preferences.
	 */
	@Test
	void builderWithoutPreferencesBindsNullJsonPreference() throws Exception {
		Path file = this.tempDir.resolve("pref-empty.md");
		Files.writeString(file, "x");
		DriverManagerDataSource dataSource = new DriverManagerDataSource();

		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource, new FileSystemResource(file)).build();

		assertThat(readPreferences(reader)).isNull();
	}

	/**
	 * Verify structured preferences are used as the effective configuration.
	 */
	@Test
	void builderUsesStructuredPreferencesAsSourceOfTruth() throws Exception {
		Path file = this.tempDir.resolve("pref-source-of-truth.md");
		Files.writeString(file, "x");
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		OracleDocumentPreferences preferences = OracleDocumentPreferences.builder()
			.plaintext(false)
			.charset("UTF8")
			.format("text")
			.build();

		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource, new FileSystemResource(file))
			.preferences(preferences)
			.build();

		assertThat(readPreferences(reader)).containsExactly(preferences.toByteArray());
	}

	/**
	 * Verify last provided structured preferences override previous preferences.
	 */
	@Test
	void builderPreferencesOverrideProvidedPreferences() throws Exception {
		Path file = this.tempDir.resolve("pref-override.md");
		Files.writeString(file, "x");
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		OracleDocumentPreferences base = OracleDocumentPreferences.builder()
			.plaintext(false)
			.charset("AL32UTF8")
			.format("binary")
			.build();
		OracleDocumentPreferences expected = OracleDocumentPreferences.builder()
			.plaintext(true)
			.charset("UTF8")
			.format("text")
			.build();

		OracleDocumentReader reader = OracleDocumentReader.builder(dataSource, new FileSystemResource(file))
			.preferences(base)
			.preferences(expected)
			.build();

		assertThat(readPreferences(reader)).containsExactly(expected.toByteArray());
	}

	/**
	 * Verify blank charset is rejected by builder.
	 */
	@Test
	void builderRejectsBlankCharset() {
		assertThatThrownBy(() -> OracleDocumentPreferences.builder().charset(" ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("charset must not be empty");
	}

	/**
	 * Verify unsupported format is rejected by builder.
	 */
	@Test
	void builderRejectsUnsupportedFormat() {
		assertThatThrownBy(() -> OracleDocumentPreferences.builder().format("json").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("format must be one of: BINARY, TEXT, IGNORE");
	}

	/**
	 * Verify table connection failures are wrapped.
	 */
	@Test
	void tableConnectionFailureIsWrapped() {
		SQLException sqlException = new SQLException("table connection failed");
		OracleDocumentReader reader = new OracleDocumentReader(failingDataSource(sqlException), "APP", "DOCS", "TEXT");

		assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to load documents using OracleDocumentReader")
			.hasCause(sqlException);
	}

	/**
	 * Verify resource connection failures are wrapped.
	 */
	@Test
	void resourceConnectionFailureIsWrapped() throws Exception {
		SQLException sqlException = new SQLException("resource connection failed");
		Path markdownFile = this.tempDir.resolve("single.md");
		Files.writeString(markdownFile, "# Title\n\nfile-token");
		OracleDocumentReader reader = new OracleDocumentReader(failingDataSource(sqlException), markdownFile);

		assertThatThrownBy(reader::get).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to load documents using OracleDocumentReader")
			.hasCause(sqlException);
	}

	/**
	 * Verify stream-backed resources are read without requiring a filesystem path.
	 */
	@Test
	void streamBackedResourceIsLoadedAndRetainsSourceMetadata() throws Exception {
		byte[] content = "stream-backed content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Resource resource = new ByteArrayResource(content, "stream resource") {
			@Override
			public String getFilename() {
				return "stream.md";
			}

			@Override
			public URI getURI() {
				return URI.create("memory:/stream.md");
			}
		};
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
		when(resultSet.getString("text")).thenReturn("converted content");
		when(resultSet.getString("metadata"))
			.thenReturn("<html><head><meta name=\"author\" content=\"Oracle\"></head></html>");

		List<Document> documents = new OracleDocumentReader(dataSource, resource).get();

		assertThat(documents).singleElement().satisfies(document -> {
			assertThat(document.getText()).isEqualTo("converted content");
			assertThat(document.getMetadata()).containsEntry("source", "memory:/stream.md")
				.containsEntry("file_name", "stream.md")
				.containsEntry("author", "Oracle")
				.doesNotContainKey("absolute_directory_path");
		});
		verify(blob).setBytes(1, content);
		verify(blob).free();
	}

	/**
	 * Create a data source that always fails with the supplied SQL exception.
	 * @param sqlException exception to throw
	 * @return failing data source
	 */
	private static DataSource failingDataSource(SQLException sqlException) {
		return new AbstractDataSource() {
			/**
			 * Always throws configured SQL exception.
			 * @return never returns
			 * @throws SQLException always thrown
			 */
			@Override
			public Connection getConnection() throws SQLException {
				throw sqlException;
			}

			/**
			 * Always throws configured SQL exception.
			 * @param username ignored
			 * @param password ignored
			 * @return never returns
			 * @throws SQLException always thrown
			 */
			@Override
			public Connection getConnection(String username, String password) throws SQLException {
				throw sqlException;
			}
		};
	}

	/**
	 * Read the private preferences field for assertion purposes.
	 * @param reader document reader instance
	 * @return raw preferences bytes
	 * @throws Exception on reflection failures
	 */
	private static byte[] readPreferences(OracleDocumentReader reader) throws Exception {
		java.lang.reflect.Field field = OracleDocumentReader.class.getDeclaredField("PreferencesOson");
		field.setAccessible(true);
		return (byte[]) field.get(reader);
	}

}
