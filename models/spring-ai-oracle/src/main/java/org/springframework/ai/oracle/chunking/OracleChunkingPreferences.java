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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import oracle.jdbc.provider.oson.OsonFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Structured chunking preferences for DBMS_VECTOR_CHAIN.UTL_TO_CHUNKS.
 *
 * @author Spring AI Contributors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OracleChunkingPreferences {

	private static final OsonFactory OSON_FACTORY = new OsonFactory();

	private static final JsonMapper LC4J_MAPPER = new JsonMapper();

	/**
	 * Unit used to measure chunk size, such as words, sentences, or vocabulary entries.
	 */
	@JsonProperty("by")
	private final @Nullable String by;

	/** Maximum size of each chunk, measured in the selected {@code by} unit. */
	@JsonProperty("max")
	private final @Nullable String max;

	/** Number of units shared by consecutive chunks to preserve context. */
	@JsonProperty("overlap")
	private final @Nullable String overlap;

	/** Boundary type used when splitting text into chunks. */
	@JsonProperty("split")
	private final @Nullable String split;

	/** Custom delimiters used when {@code split} is {@code custom}. */
	@JsonProperty("custom_list")
	private final @Nullable List<String> customList;

	/** Oracle vocabulary name used when {@code by} is {@code vocabulary}. */
	@JsonProperty("vocabulary")
	private final @Nullable String vocabulary;

	/** Language hint Oracle uses to identify suitable chunk boundaries. */
	@JsonProperty("language")
	private final @Nullable String language;

	/** Text normalization mode applied before chunking. */
	@JsonProperty("normalize")
	private final @Nullable String normalize;

	/** Normalization options used when {@code normalize} is {@code options}. */
	@JsonProperty("norm_options")
	private final @Nullable List<String> normOptions;

	/** Whether Oracle returns extended chunk metadata. */
	@JsonProperty("extended")
	private final @Nullable Boolean extended;

	/**
	 * Create immutable preferences from a builder.
	 * @param builder source builder
	 */
	private OracleChunkingPreferences(Builder builder) {
		this.by = builder.by;
		this.max = (builder.max != null) ? builder.max.toString() : null;
		this.overlap = (builder.overlap != null) ? builder.overlap.toString() : null;
		this.split = builder.split;
		this.customList = builder.customList;
		this.vocabulary = builder.vocabulary;
		this.language = builder.language;
		this.normalize = builder.normalize;
		this.normOptions = builder.normOptions;
		this.extended = builder.extended;
	}

	/**
	 * Create a new chunking preferences builder.
	 * @return preferences builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Serialize preferences to Oracle OSON bytes.
	 * @return serialized preferences
	 */
	public byte[] toByteArray() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (JsonGenerator gen = OSON_FACTORY.createGenerator(out)) {
				LC4J_MAPPER.writeValue(gen, this);
				gen.flush();
			}
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to serialize chunking preferences to Oracle OSON", ex);
		}
	}

	public static final class Builder {

		/**
		 * Unit used to measure chunk size, such as words, sentences, or vocabulary
		 * entries.
		 */
		private @Nullable String by;

		/** Maximum size of each chunk, measured in the selected {@code by} unit. */
		private @Nullable Integer max;

		/** Number of units shared by consecutive chunks to preserve context. */
		private @Nullable Integer overlap;

		/** Boundary type used when splitting text into chunks. */
		private @Nullable String split;

		/** Custom delimiters used when {@code split} is {@code custom}. */
		private @Nullable List<String> customList;

		/** Oracle vocabulary name used when {@code by} is {@code vocabulary}. */
		private @Nullable String vocabulary;

		/** Language hint Oracle uses to identify suitable chunk boundaries. */
		private @Nullable String language;

		/** Text normalization mode applied before chunking. */
		private @Nullable String normalize;

		/** Normalization options used when {@code normalize} is {@code options}. */
		private @Nullable List<String> normOptions;

		/** Whether Oracle returns extended chunk metadata. */
		private @Nullable Boolean extended;

		/**
		 * Set chunking strategy.
		 * @param by chunking mode
		 * @return this builder
		 */
		public Builder by(@Nullable String by) {
			if (by != null) {
				Assert.hasText(by, "by must not be empty");
			}
			this.by = by;
			return this;
		}

		/**
		 * Set maximum chunk size.
		 * @param max maximum chunk size
		 * @return this builder
		 */
		public Builder max(@Nullable Integer max) {
			if (max != null) {
				Assert.isTrue(max > 0, "max must be greater than 0");
			}
			this.max = max;
			return this;
		}

		/**
		 * Set overlap between adjacent chunks.
		 * @param overlap overlap size
		 * @return this builder
		 */
		public Builder overlap(@Nullable Integer overlap) {
			if (overlap != null) {
				Assert.isTrue(overlap >= 0, "overlap must be greater than or equal to 0");
			}
			this.overlap = overlap;
			return this;
		}

		/**
		 * Set split behavior.
		 * @param split split mode
		 * @return this builder
		 */
		public Builder split(@Nullable String split) {
			if (split != null) {
				Assert.hasText(split, "split must not be empty");
			}
			this.split = split;
			return this;
		}

		/**
		 * Set custom split delimiters.
		 * @param customList custom split values
		 * @return this builder
		 */
		public Builder customList(@Nullable List<String> customList) {
			if (customList != null) {
				Assert.notEmpty(customList, "customList must not be empty");
				Assert.noNullElements(customList, "customList must not contain null values");
				customList = List.copyOf(customList);
			}
			this.customList = customList;
			return this;
		}

		/**
		 * Set vocabulary name for vocabulary-based chunking.
		 * @param vocabulary vocabulary name
		 * @return this builder
		 */
		public Builder vocabulary(@Nullable String vocabulary) {
			if (vocabulary != null) {
				Assert.hasText(vocabulary, "vocabulary must not be empty");
			}
			this.vocabulary = vocabulary;
			return this;
		}

		/**
		 * Set language hint for chunking.
		 * @param language language name
		 * @return this builder
		 */
		public Builder language(@Nullable String language) {
			if (language != null) {
				Assert.hasText(language, "language must not be empty");
			}
			this.language = language;
			return this;
		}

		/**
		 * Set text normalization mode.
		 * @param normalize normalization mode
		 * @return this builder
		 */
		public Builder normalize(@Nullable String normalize) {
			if (normalize != null) {
				Assert.hasText(normalize, "normalize must not be empty");
			}
			this.normalize = normalize;
			return this;
		}

		/**
		 * Set normalization options.
		 * @param normOptions normalization option values
		 * @return this builder
		 */
		public Builder normOptions(@Nullable List<String> normOptions) {
			if (normOptions != null) {
				Assert.notEmpty(normOptions, "normOptions must not be empty");
				Assert.noNullElements(normOptions, "normOptions must not contain null values");
				normOptions = List.copyOf(normOptions);
			}
			this.normOptions = normOptions;
			return this;
		}

		/**
		 * Set whether extended mode is enabled.
		 * @param extended extended mode flag
		 * @return this builder
		 */
		public Builder extended(@Nullable Boolean extended) {
			this.extended = extended;
			return this;
		}

		/**
		 * Build validated chunking preferences.
		 * @return immutable preferences
		 */
		public OracleChunkingPreferences build() {
			if (this.vocabulary != null) {
				Assert.isTrue("vocabulary".equals(this.by), "vocabulary requires by to be set to 'vocabulary'");
			}
			if (this.customList != null) {
				Assert.isTrue("custom".equals(this.split), "customList requires split to be set to 'custom'");
			}
			if (this.normOptions != null) {
				Assert.isTrue("options".equals(this.normalize),
						"normOptions requires normalize to be set to 'options'");
			}
			return new OracleChunkingPreferences(this);
		}

	}

}
