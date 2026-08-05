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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Chunk payload accepted by DBMS_VECTOR_CHAIN.UTL_TO_EMBEDDINGS.
 *
 * @author Spring AI Contributors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OracleChunk {

	private int id;

	private @Nullable Integer offset;

	private @Nullable Integer length;

	private @Nullable String data;

	/**
	 * Create an empty chunk payload.
	 */
	public OracleChunk() {
	}

	/**
	 * Create a chunk payload with an identifier and data.
	 * @param id the chunk identifier
	 * @param data the chunk text
	 */
	public OracleChunk(int id, String data) {
		this.id = id;
		this.data = data;
	}

	/**
	 * Return the chunk identifier.
	 * @return the chunk identifier
	 */
	@JsonProperty("chunk_id")
	public int getId() {
		return this.id;
	}

	/**
	 * Set the chunk identifier.
	 * @param id the chunk identifier
	 */
	@JsonProperty("chunk_id")
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * Return the chunk offset in the source text, if provided by Oracle.
	 * @return the chunk offset, or {@code null}
	 */
	@JsonProperty("chunk_offset")
	public @Nullable Integer getOffset() {
		return this.offset;
	}

	/**
	 * Set the chunk offset in the source text.
	 * @param offset the chunk offset, or {@code null}
	 */
	@JsonProperty("chunk_offset")
	public void setOffset(@Nullable Integer offset) {
		this.offset = offset;
	}

	/**
	 * Return the chunk length, if provided by Oracle.
	 * @return the chunk length, or {@code null}
	 */
	@JsonProperty("chunk_length")
	public @Nullable Integer getLength() {
		return this.length;
	}

	/**
	 * Set the chunk length.
	 * @param length the chunk length, or {@code null}
	 */
	@JsonProperty("chunk_length")
	public void setLength(@Nullable Integer length) {
		this.length = length;
	}

	/**
	 * Return the chunk text.
	 * @return the chunk text
	 */
	@JsonProperty("chunk_data")
	public @Nullable String getData() {
		return this.data;
	}

	/**
	 * Set the chunk text.
	 * @param data the chunk text
	 */
	@JsonProperty("chunk_data")
	public void setData(String data) {
		this.data = data;
	}

}
