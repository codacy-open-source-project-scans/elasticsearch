/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */
package org.elasticsearch.index.fielddata.plain;

import org.apache.lucene.index.SortedNumericDocValues;
import org.elasticsearch.index.fielddata.FormattedDocValues;
import org.elasticsearch.search.DocValueFormat;

import java.io.IOException;

public final class FormattedSortedNumericDocValues implements FormattedDocValues {
    private final SortedNumericDocValues values;
    private final DocValueFormat format;

    public FormattedSortedNumericDocValues(SortedNumericDocValues values, DocValueFormat format) {
        this.values = values;
        this.format = format;
    }

    @Override
    public boolean advanceExact(int docId) throws IOException {
        return values.advanceExact(docId);
    }

    @Override
    public int docValueCount() {
        return values.docValueCount();
    }

    @Override
    public Object nextValue() throws IOException {
        return format.format(values.nextValue());
    }
}