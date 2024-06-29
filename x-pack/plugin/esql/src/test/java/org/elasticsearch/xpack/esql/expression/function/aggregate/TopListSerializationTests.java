/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.aggregate;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.expression.AbstractExpressionSerializationTests;

import java.io.IOException;
import java.util.List;

public class TopListSerializationTests extends AbstractExpressionSerializationTests<TopList> {
    @Override
    protected TopList createTestInstance() {
        Source source = randomSource();
        Expression field = randomChild();
        Expression limit = randomChild();
        Expression order = randomChild();
        return new TopList(source, field, limit, order);
    }

    @Override
    protected TopList mutateInstance(TopList instance) throws IOException {
        Source source = instance.source();
        Expression field = instance.field();
        Expression limit = instance.limitField();
        Expression order = instance.orderField();
        switch (between(0, 2)) {
            case 0 -> field = randomValueOtherThan(field, AbstractExpressionSerializationTests::randomChild);
            case 1 -> limit = randomValueOtherThan(limit, AbstractExpressionSerializationTests::randomChild);
            case 2 -> order = randomValueOtherThan(order, AbstractExpressionSerializationTests::randomChild);
        }
        return new TopList(source, field, limit, order);
    }

    @Override
    protected List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        return AggregateFunction.getNamedWriteables();
    }
}
