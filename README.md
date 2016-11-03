Step 2.

The instance converter has been edited, and a REST transform created that calls its code.
Now one can deploy the new modules and run the example again to see the process of creating
canonical envelopes from source data.

`./gradlew mlReloadModules`

loads the instance converter and harmonizing transformer into the
modules database.

`./gradlew runExample`

Runs the whole integration job, by invoking your REST transform
and entity services code, using Tamas'd data as a source and
the `athletedb-content` database as target.

To get to the next step

`git checkout runtime`
