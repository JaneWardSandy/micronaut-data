/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.query.builder.jpa;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Builds JPA 1.0 String-based queries from the Query model.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JpaQueryBuilder extends AbstractSqlLikeQueryBuilder implements QueryBuilder {

    private static final NamingStrategy JPA_NAMING_STRATEGY = new NamingStrategy() {
        @Override
        public String mappedName(String name) {
            return name;
        }

        @Override
        public String mappedAssociatedName(String associatedName) {
            return associatedName;
        }

        @Override
        public String mappedName(Association association) {
            String providedName = association.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
            if (providedName != null) {
                return providedName;
            }
            return association.getName();
        }
    };

    /**
     * Default constructor.
     */
    public JpaQueryBuilder() {
        addCriterionHandler(QueryModel.EqualsAll.class, (ctx, criterion) -> {
            handleSubQuery(ctx, criterion, " = ALL (");
        });

        addCriterionHandler(QueryModel.NotEqualsAll.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " != ALL (");
        });

        addCriterionHandler(QueryModel.GreaterThanAll.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " > ALL (");
        });

        addCriterionHandler(QueryModel.GreaterThanSome.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " > SOME (");
        });

        addCriterionHandler(QueryModel.GreaterThanEqualsAll.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " >= ALL (");
        });

        addCriterionHandler(QueryModel.GreaterThanEqualsSome.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " >= SOME (");
        });

        addCriterionHandler(QueryModel.LessThanAll.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " < ALL (");
        });

        addCriterionHandler(QueryModel.LessThanSome.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " < SOME (");
        });

        addCriterionHandler(QueryModel.LessThanEqualsAll.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " <= ALL (");
        });

        addCriterionHandler(QueryModel.LessThanEqualsSome.class, (queryState, criterion) -> {
            handleSubQuery(queryState, criterion, " <= SOME (");
        });
    }

    @Override
    protected String quote(String persistedName) {
        return persistedName;
    }

    @Override
    public String getAliasName(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class, "alias")
                .orElseGet(() -> entity.getDecapitalizedName() + "_");
    }

    @Override
    protected String[] buildJoin(String alias, JoinPath joinPath, String joinType, StringBuilder target, Map<String, String> appliedJoinPaths, QueryState queryState) {
        Association[] associationPath = joinPath.getAssociationPath();
        String[] joinAliases;
        if (ArrayUtils.isEmpty(associationPath)) {
            throw new IllegalArgumentException("Invalid association path [" + joinPath.getPath() + "]");
        }
        List<Association> joinAssociationsPath = new ArrayList<>(associationPath.length);
        joinAliases = new String[associationPath.length];
        StringJoiner pathSoFar = new StringJoiner(".");
        List<String> aliases = new ArrayList<>();
        for (int i = 0; i < associationPath.length; i++) {
            Association association = associationPath[i];
            pathSoFar.add(association.getName());
            if (association instanceof Embedded) {
                joinAssociationsPath.add(association);
                continue;
            }
            String currentPath = pathSoFar.toString();
            String existingAlias = appliedJoinPaths.get(currentPath);
            if (existingAlias != null) {
                joinAliases[i] = existingAlias;
                aliases.add(existingAlias);
            } else {
                int finalI = i;
                JoinPath joinPathToUse = queryState.getQueryModel().getJoinPath(currentPath)
                        .orElseGet(() ->
                                new JoinPath(
                                        currentPath,
                                        Arrays.copyOfRange(associationPath, 0, finalI + 1),
                                        joinPath.getJoinType(),
                                        joinPath.getAlias().orElse(null))
                        );
                String currentAlias = getAliasName(joinPathToUse);
                joinAliases[i] = currentAlias;
                String lastJoinAlias = aliases.isEmpty() ? alias : CollectionUtils.last(aliases);
                target.append(joinType)
                    .append(lastJoinAlias).append(DOT)
                    .append(association.getName())
                    .append(SPACE)
                    .append(joinAliases[i]);
                aliases.add(currentAlias);
            }
            joinAssociationsPath.clear();
        }
        return joinAliases;
    }

    @Override
    protected String buildAdditionalWhereClause(QueryState queryState, AnnotationMetadata annotationMetadata) {
        StringBuilder additionalWhereBuff = new StringBuilder(buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata));
        List<JoinPath> joinPaths = queryState.getJoinPaths();
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            Set<String> addedJoinPaths = new HashSet<>();
            for (JoinPath joinPath : joinPaths) {
                String path = joinPath.getPath();
                if (addedJoinPaths.contains(path)) {
                    continue;
                }
                addedJoinPaths.add(path);
                String joinAdditionalWhere = buildAdditionalWhereString(joinPath, annotationMetadata);
                if (StringUtils.isNotEmpty(joinAdditionalWhere)) {
                    if (additionalWhereBuff.length() > 0) {
                        additionalWhereBuff.append(SPACE).append(AND).append(SPACE);
                    }
                    additionalWhereBuff.append(joinAdditionalWhere);
                }
            }
        }
        return additionalWhereBuff.toString();
    }

    @Override
    protected String getTableName(PersistentEntity entity) {
        return entity.getName();
    }

    @Override
    protected String getColumnName(PersistentProperty persistentProperty) {
        return persistentProperty.getName();
    }

    @Override
    protected void selectAllColumns(AnnotationMetadata annotationMetadata, QueryState queryState, StringBuilder queryBuffer) {
        queryBuffer.append(queryState.getRootAlias());
    }

    @Override
    protected void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity entity, String alias, StringBuilder queryBuffer) {
        queryBuffer.append(alias);
    }

    @Override
    protected void appendProjectionRowCount(StringBuilder queryString, String logicalName) {
        queryString.append(FUNCTION_COUNT)
                .append(OPEN_BRACKET)
                .append(logicalName)
                .append(CLOSE_BRACKET);
    }

    @Override
    protected final boolean computePropertyPaths() {
        return false;
    }

    @Override
    protected boolean isAliasForBatch(PersistentEntity persistentEntity, AnnotationMetadata annotationMetadata) {
        return true;
    }

    @Override
    protected Placeholder formatParameter(int index) {
        String n = "p" + index;
        return new Placeholder(":" + n, n);
    }

    @Override
    public String resolveJoinType(Join.Type jt) {
        return switch (jt) {
            case LEFT -> " LEFT JOIN ";
            case LEFT_FETCH -> " LEFT JOIN FETCH ";
            case RIGHT -> " RIGHT JOIN ";
            case RIGHT_FETCH -> " RIGHT JOIN FETCH ";
            case INNER, FETCH -> " JOIN FETCH ";
            default -> " JOIN ";
        };
    }

    @Nullable
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        // JPA doesn't require an insert statement
        return null;
    }

    @NonNull
    @Override
    protected StringBuilder appendDeleteClause(StringBuilder queryString) {
        return queryString.append(DELETE_CLAUSE);
    }

    @NonNull
    @Override
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        throw new UnsupportedOperationException("JPA-QL does not support pagination in query definitions");
    }

    @Override
    protected void appendCompoundAssociationProjection(QueryState queryState, StringBuilder queryString, Association association, PersistentPropertyPath propertyPath, String alias) {
        String joinAlias = queryState.computeAlias(propertyPath.getPath());
        queryString.append(joinAlias).append(AS_CLAUSE).append(alias != null ? alias : association.getName());
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentEntity entity) {
        return JPA_NAMING_STRATEGY;
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return JPA_NAMING_STRATEGY;
    }

}
