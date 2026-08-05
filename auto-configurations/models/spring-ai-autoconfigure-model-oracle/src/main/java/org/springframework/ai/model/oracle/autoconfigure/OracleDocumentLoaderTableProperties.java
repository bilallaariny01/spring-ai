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

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

/**
 * Configuration properties for Oracle table document source.
 *
 * @author Spring AI Contributors
 */
public class OracleDocumentLoaderTableProperties {

	private @Nullable String owner;

	private @Nullable String tableName;

	private @Nullable String columnName;

	public @Nullable String getOwner() {
		return this.owner;
	}

	public void setOwner(@Nullable String owner) {
		this.owner = owner;
	}

	public @Nullable String getTableName() {
		return this.tableName;
	}

	public void setTableName(@Nullable String tableName) {
		this.tableName = tableName;
	}

	public @Nullable String getColumnName() {
		return this.columnName;
	}

	public void setColumnName(@Nullable String columnName) {
		this.columnName = columnName;
	}

	public boolean isConfigured() {
		return StringUtils.hasText(this.owner) || StringUtils.hasText(this.tableName)
				|| StringUtils.hasText(this.columnName);
	}

}
