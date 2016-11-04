AthleteDb
---------

Step 1

* Tamas's data is loaded.
* I created an entity descriptor `src/main/resources/athlete-db.json`

Start the project by deploying the databases:

`./gradlew mlDeploy`


Two entity types in the model, `Player` and `Team`.  Note that in future models,
nationality and position may be complex types, not just strings.

Running `./gradlew runExample` loads this model.


If you want to see how Entity Services code is used to construct the
subsequent steps in this example, you can try this:

`./gradlew genCode`

This hook executes Entity Services methods on the model
to generate code stubs.  If you don't have paths set properly it will
throw an error, but it's not important to complete for this demo.

Note there's a property in application.properties to set: `codegenDir`

To get to the next step

`git checkout make-extraction`
