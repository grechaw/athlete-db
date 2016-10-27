xquery version "1.0-ml";
module namespace ph = "http://marklogic.com/rest-api/transform/football-harmonizer";

declare namespace s = "http://www.w3.org/2005/xpath-functions";

import module namespace functx = "http://www.functx.com"
    at "/MarkLogic/functx/functx-1.0-nodoc-2007-01.xqy";

import module namespace hof = "http://marklogic.com/higher-order" 
    at "/MarkLogic/appservices/utils/higher-order.xqy";

import module namespace athleteDb 
    = "http://example.org/AthleteDb-0.0.1"
    at "/ext/athlete-db.xqy";

(:
 Applies an extraction function to the body.  This harmonizer
 splits on the number in the source's URI -- the integer at the end of the
 filename determines which extraction function is run.
 :)
declare function ph:transform(
        $context as map:map,
        $params as map:map,
        $body as document-node()
) as document-node()?
{
    let $uri := map:get($context, "uri")
    let $_ := xdmp:log(("Procesing URI " || $uri))
    let $instance := athleteDb:extract-instance-Player($body)

    (: denormalizing code.  can be in extraction too :)
    let $teamId := xs:integer($body//teamId)
    let $team := athleteDb:extract-instance-Team(
            fn:head(cts:search(collection("teams"), cts:json-property-value-query("id", $teamId))))
    let $_ := map:put($instance, "team", $team)
    let $_ := map:delete($instance, "teamId")
    let $doc-insert := function() {
        xdmp:document-insert(replace($uri, ".json", ".xml"),
                athleteDb:instance-to-envelope($instance),
                (xdmp:permission("rest-reader", "read"), xdmp:permission("rest-writer", "insert"), xdmp:permission("rest-writer", "update")),
                "athlete-envelopes")
        }
    let $_ := hof:apply-in(xdmp:database("athletedb-content"), $doc-insert)
    return document { " " }
};
