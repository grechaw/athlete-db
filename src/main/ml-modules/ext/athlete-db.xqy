xquery version "1.0-ml";

module namespace athleteDb
    = "http://example.org/AthleteDb-0.0.1";

import module namespace es = "http://marklogic.com/entity-services"
    at "/MarkLogic/entity-services/entity-services.xqy";

declare option xdmp:mapping "false";


declare function athleteDb:extract-instance-Player(
    $source as node()?
) as map:map
{
    let $source-node :=
        if ( ($source instance of document-node())
            or (exists($source/Player)))
        then $source/node()
        else $source
    let $instance := json:object()
    (: Add the original source document as an attachment. (optional, for lineage) :)
        =>map:with('$attachments',
            typeswitch($source-node)
            case object-node() return xdmp:quote($source)
            case array-node() return xdmp:quote($source)
            default return $source)
    (: Add type information to the entity instance (required, do not change) :)
        =>map:with('$type', 'Player')
    return

    (: The following logic may not be required for every extraction       :)
    (: if this $source-node has no child elements, it has a reference key :)
    if (empty($source-node/*))
    then $instance=>map:with('$ref', $source-node/text())
    (: Otherwise, this source node contains instance data. Populate it. :)
    else

    $instance
    =>   map:with('name',                   xs:string($source-node/player/name))
    =>   map:with('position',               xs:string($source-node/player/position))
    =>   es:optional('jerseyNumber',        if ($source-node/player/jerseyNumber castable as xs:positiveInteger)
                                            then xs:positiveInteger($source-node/player/jerseyNumber)
                                            else ())
    =>   es:optional('dateOfBirth',         if ($source-node/player/dateOfBirth castable as xs:date)
                                            then xs:date($source-node/player/dateOfBirth)
                                            else ())
    =>   map:with('nationality',            xs:string($source-node/player/nationality))
    =>   es:optional('contractUntil',       if($source-node/player/contractUntil castable as xs:date)
                                            then xs:date($source-node/player/contractUntil)
                                            else ())
    =>   es:optional('marketValue',         if ($source-node/player/marketValue castable as xs:long)
                                            then xs:long($source-node/player/marketValue)
                                            else ())
    =>   map:with('teamId',                 athleteDb:extract-instance-Team($source-node/player/teamId))
};


declare function athleteDb:extract-instance-Team(
    $source as node()?
) as map:map
{
    let $source-node :=
        if ( ($source instance of document-node())
            or (exists($source/Team)))
        then $source/node()
        else $source
    let $instance := json:object()
    (: Add type information to the entity instance (required, do not change) :)
        =>map:with('$type', 'Team')
    return

    (: The following logic may not be required for every extraction       :)
    (: if this $source-node has no child elements, it has a reference key :)
    if (empty($source-node/*))
    then $instance=>map:with('$ref', $source-node/text())
    (: Otherwise, this source node contains instance data. Populate it. :)
    else

    (:
    The following code populates the properties of the 'Team'
    entity type. Ensure that all of the property paths are correct for your
    source data.  The general pattern is
    =>map:with('keyName', casting-function($source-node/path/to/data))
    but you may also wish to convert values
    =>map:with('dateKeyName',
          xdmp:parse-dateTime("[Y0001]-[M01]-[D01]T[h01]:[m01]:[s01].[f1][Z]",
          $source-node/path/to/data/in/the/source))
    You can also implement lookup functions,
    =>map:with('lookupKey',
          cts:search( collection('customers'),
              string($source-node/path/to/lookup/key))/id
    or populate the instance with constants.
    =>map:with('constantValue', 10)
    The output of this function should structurally match the output of
    es:model-get-test-instances($model)
    :)

    $instance
    =>   map:with('name',                   xs:string($source-node/team/name))
    =>   map:with('id',                     xs:integer($source-node/team/id))
    =>es:optional('crest',                  sem:iri($source-node/team/crest))
    =>   map:with('value',                  xs:long($source-node/team/value))
};





(:~
 : Turns an entity instance into an XML structure.
 : This out-of-the box implementation traverses a map structure
 : and turns it deterministically into an XML tree.
 : Using this function as-is should be sufficient for most use
 : cases, and will play well with other generated artifacts.
 : @param $entity-instance A map:map instance returned from one of the extract-instance
 :    functions.
 : @return An XML element that encodes the instance.
 :)
declare function athleteDb:instance-to-canonical-xml(
    $entity-instance as map:map
) as element()
{
    (: Construct an element that is named the same as the Entity Type :)
    element { map:get($entity-instance, "$type") }  {
        if ( map:contains($entity-instance, "$ref") )
        then map:get($entity-instance, "$ref")
        else
            for $key in map:keys($entity-instance)
            let $instance-property := map:get($entity-instance, $key)
            where ($key castable as xs:NCName)
            return
                typeswitch ($instance-property)
                (: This branch handles embedded objects.  You can choose to prune
                   an entity's representation of extend it with lookups here. :)
                case json:object+
                    return
                        for $prop in $instance-property
                        return element { $key } { athleteDb:instance-to-canonical-xml($prop) }
                (: An array can also treated as multiple elements :)
                case json:array
                    return
                        for $val in json:array-values($instance-property)
                        return
                            if ($val instance of json:object)
                            then element { $key } {
                                attribute datatype { "array" },
                                athleteDb:instance-to-canonical-xml($val) }
                            else element { $key } {
                                attribute datatype { "array" },
                                $val }
                (: A sequence of values should be simply treated as multiple elements :)
                case item()+
                    return
                        for $val in $instance-property
                        return element { $key } { $val }
                default return element { $key } { $instance-property }
    }
};


(:
 : Wraps a canonical instance (returned by instance-to-canonical-xml())
 : within an envelope patterned document, along with the source
 : document, which is stored in an attachments section.
 : @param $entity-instance an instance, as returned by an extract-instance
 : function
 : @return A document which wraps both the canonical instance and source docs.
 :)
declare function athleteDb:instance-to-envelope(
    $entity-instance as map:map
) as document-node()
{
    document {
        element es:envelope {
            element es:instance {
                element es:info {
                    element es:title { map:get($entity-instance,'$type') },
                    element es:version { "0.0.1" }
                },
                athleteDb:instance-to-canonical-xml($entity-instance)
            },
            element es:attachments {
                map:get($entity-instance, "$attachments")
            }
        }
    }
};


