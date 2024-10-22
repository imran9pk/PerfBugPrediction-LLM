package io.crate.lucene;

import io.crate.expression.operator.LikeOperators;
import io.crate.expression.symbol.Literal;
import io.crate.metadata.Reference;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.RegexpFlag;

import java.io.IOException;
import java.util.Locale;

class AnyNotLikeQuery extends AbstractAnyQuery {

    private static String negateWildcard(String wildCard) {
        return String.format(Locale.ENGLISH, "~(%s)", wildCard);
    }

    AnyNotLikeQuery(boolean ignoreCase) {
        super(ignoreCase);
    }

    @Override
    protected Query literalMatchesAnyArrayRef(Literal candidate, Reference array, LuceneQueryBuilder.Context context) throws IOException {
        String regexString = LikeOperators.patternToRegex((String) candidate.value(), LikeOperators.DEFAULT_ESCAPE, false);
        regexString = regexString.substring(1, regexString.length() - 1);
        String notLike = negateWildcard(regexString);

        return new RegexpQuery(new Term(
            array.column().fqn(),
            notLike),
            RegexpFlag.COMPLEMENT.value()
        );
    }

    @Override
    protected Query refMatchesAnyArrayLiteral(Reference candidate, Literal array, LuceneQueryBuilder.Context context) {
        String columnName = candidate.column().fqn();
        MappedFieldType fieldType = context.getFieldTypeOrNull(columnName);

        BooleanQuery.Builder andLikeQueries = new BooleanQuery.Builder();
        for (Object value : toIterable(array.value())) {
            andLikeQueries.add(
                LikeQuery.like(candidate.valueType(), fieldType, value, ignoreCase),
                BooleanClause.Occur.MUST);
        }
        return Queries.not(andLikeQueries.build());
    }
}
