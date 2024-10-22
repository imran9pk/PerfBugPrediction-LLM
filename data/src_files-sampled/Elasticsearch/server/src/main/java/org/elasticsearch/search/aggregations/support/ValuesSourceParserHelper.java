package org.elasticsearch.search.aggregations.support;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.AbstractObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.script.Script;
import org.joda.time.DateTimeZone;

public final class ValuesSourceParserHelper {

    private ValuesSourceParserHelper() {} public static <T> void declareAnyFields(
            AbstractObjectParser<? extends ValuesSourceAggregationBuilder<ValuesSource, ?>, T> objectParser,
            boolean scriptable, boolean formattable) {
        declareFields(objectParser, scriptable, formattable, false, null);
    }

    public static <T> void declareNumericFields(
            AbstractObjectParser<? extends ValuesSourceAggregationBuilder<ValuesSource.Numeric, ?>, T> objectParser,
            boolean scriptable, boolean formattable, boolean timezoneAware) {
        declareFields(objectParser, scriptable, formattable, timezoneAware, ValueType.NUMERIC);
    }

    public static <T> void declareBytesFields(
            AbstractObjectParser<? extends ValuesSourceAggregationBuilder<ValuesSource.Bytes, ?>, T> objectParser,
            boolean scriptable, boolean formattable) {
        declareFields(objectParser, scriptable, formattable, false, ValueType.STRING);
    }

    public static <T> void declareGeoFields(
            AbstractObjectParser<? extends ValuesSourceAggregationBuilder<ValuesSource.GeoPoint, ?>, T> objectParser,
            boolean scriptable, boolean formattable) {
        declareFields(objectParser, scriptable, formattable, false, ValueType.GEOPOINT);
    }

    private static <VS extends ValuesSource, T> void declareFields(
            AbstractObjectParser<? extends ValuesSourceAggregationBuilder<VS, ?>, T> objectParser,
            boolean scriptable, boolean formattable, boolean timezoneAware, ValueType targetValueType) {


        objectParser.declareField(ValuesSourceAggregationBuilder::field, XContentParser::text,
            ParseField.CommonFields.FIELD, ObjectParser.ValueType.STRING);

        objectParser.declareField(ValuesSourceAggregationBuilder::missing, XContentParser::objectText,
            ParseField.CommonFields.MISSING, ObjectParser.ValueType.VALUE);

        objectParser.declareField(ValuesSourceAggregationBuilder::valueType, p -> {
            ValueType valueType = ValueType.resolveForScript(p.text());
            if (targetValueType != null && valueType.isNotA(targetValueType)) {
                throw new ParsingException(p.getTokenLocation(),
                        "Aggregation [" + objectParser.getName() + "] was configured with an incompatible value type ["
                                + valueType + "]. It can only work on value of type ["
                                + targetValueType + "]");
            }
            return valueType;
        }, ValueType.VALUE_TYPE, ObjectParser.ValueType.STRING);

        if (formattable) {
            objectParser.declareField(ValuesSourceAggregationBuilder::format, XContentParser::text,
                ParseField.CommonFields.FORMAT, ObjectParser.ValueType.STRING);
        }

        if (scriptable) {
            objectParser.declareField(ValuesSourceAggregationBuilder::script,
                    (parser, context) -> Script.parse(parser),
                    Script.SCRIPT_PARSE_FIELD, ObjectParser.ValueType.OBJECT_OR_STRING);
        }

        if (timezoneAware) {
            objectParser.declareField(ValuesSourceAggregationBuilder::timeZone, p -> {
                if (p.currentToken() == XContentParser.Token.VALUE_STRING) {
                    return DateTimeZone.forID(p.text());
                } else {
                    return DateTimeZone.forOffsetHours(p.intValue());
                }
            }, ParseField.CommonFields.TIME_ZONE, ObjectParser.ValueType.LONG);
        }
    }



}
