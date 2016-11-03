AthleteDb
---------

Step 1

* Tamas's data is loaded.
* I created an entity descriptor `src/main/resources/athlete-db.json`

Two entity types in the model, `Player` and `Team`.  Note that in future models,
nationality and position may be complex types, not just strings.

Running `./gradlew runExample` loads this model.

Running `./gradlew genCode` executes Entity Services methods on the model to generate code stubs.

Note there's a property in application.proeprties to set: `codegenDir`

In this step, we see the artifacts created by Entity Services code generation.
