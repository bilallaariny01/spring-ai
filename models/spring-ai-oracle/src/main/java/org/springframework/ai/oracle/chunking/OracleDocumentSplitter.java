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

package org.springframework.ai.oracle.chunking;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import oracle.jdbc.OracleTypes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.Assert;

/**
 * Oracle Database text splitter using DBMS_VECTOR_CHAIN.UTL_TO_CHUNKS.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentSplitter extends TextSplitter {

	private static final String CHUNKING_SQL = "select json_value(t.column_value, '$.chunk_data' returning clob) "
			+ "as chunk_data from dbms_vector_chain.utl_to_chunks(?, ?) t "
			+ "order by json_value(t.column_value, '$.chunk_id' returning number)";

	private final DataSource dataSource;

	private final byte[] preferencesOson;

	/**
	 * Create a splitter with default Oracle chunking behavior.
	 * @param dataSource Oracle data source
	 */
	public OracleDocumentSplitter(DataSource dataSource) {
		this(dataSource, Builder.DEFAULT_PREFERENCES.toByteArray());
	}

	/**
	 * Internal constructor with optional OSON chunking preferences.
	 * @param dataSource Oracle data source
	 * @param preferencesOson Oracle JSON preferences bytes
	 */
	private OracleDocumentSplitter(DataSource dataSource, byte[] preferencesOson) {
		Assert.notNull(dataSource, "dataSource must not be null");
		this.dataSource = dataSource;

		this.preferencesOson = preferencesOson;
	}

	/**
	 * Create a builder for splitter configuration.
	 * @return splitter builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a builder for splitter configuration.
	 * @param dataSource Oracle data source
	 * @return splitter builder
	 */
	public static Builder builder(DataSource dataSource) {
		return builder().dataSource(dataSource);
	}

	/**
	 * Create a builder for splitter configuration with explicit preferences.
	 * @param dataSource Oracle data source
	 * @param preferences chunking preferences
	 * @return splitter builder
	 */
	public static Builder builder(DataSource dataSource, OracleChunkingPreferences preferences) {
		return builder(dataSource).preferences(preferences);
	}

	/**
	 * Split text using {@code DBMS_VECTOR_CHAIN.UTL_TO_CHUNKS}.
	 * @param text source text
	 * @return chunk text values
	 */
	@Override
	@NullMarked
	protected List<String> splitText(String text) {
		Assert.notNull(text, "text must not be null");

		if (text.isEmpty()) {
			return List.of(text);
		}

		List<String> chunks = new ArrayList<>();

		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(CHUNKING_SQL)) {
			Clob input = connection.createClob();
			try {
				input.setString(1, text);
				statement.setClob(1, input);
				if (this.preferencesOson == null) {
					statement.setNull(2, OracleTypes.JSON);
				}
				else {
					statement.setObject(2, this.preferencesOson, OracleTypes.JSON);
				}

				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						String chunkData = resultSet.getString("chunk_data");
						if (chunkData != null) {
							chunks.add(chunkData);
						}
					}
				}
			}
			finally {
				input.free();
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Failed to split text using Oracle DBMS_VECTOR_CHAIN.UTL_TO_CHUNKS", ex);
		}

		return chunks.isEmpty() ? List.of(text) : chunks;
	}

	public static final class Builder {

		private static final OracleChunkingPreferences DEFAULT_PREFERENCES = OracleChunkingPreferences.builder()
			.by("words")
			.build();

		private @Nullable DataSource dataSource;

		private OracleChunkingPreferences preferences = DEFAULT_PREFERENCES;

		/**
		 * Create an empty builder. Configure source and preferences before build.
		 */
		private Builder() {
		}

		/**
		 * Set Oracle data source.
		 * @param dataSource Oracle data source
		 * @return this builder
		 */
		public Builder dataSource(DataSource dataSource) {
			Assert.notNull(dataSource, "dataSource must not be null");
			this.dataSource = dataSource;
			return this;
		}

		/**
		 * Set structured chunking preferences.
		 * @param preferences chunking preferences
		 * @return this builder
		 */
		public Builder preferences(OracleChunkingPreferences preferences) {
			Assert.notNull(preferences, "preferences must not be null");
			this.preferences = preferences;
			return this;
		}

		/**
		 * Build a splitter from configured options.
		 * @return configured splitter
		 */
		public OracleDocumentSplitter build() {
			Assert.notNull(this.dataSource, "dataSource must not be null");
			DataSource configuredDataSource = Objects.requireNonNull(this.dataSource);
			return new OracleDocumentSplitter(configuredDataSource, this.preferences.toByteArray());
		}

	}

}
