AthleteDb
---------

Steps toward making this example.

* Loaded Tamas's data
* created entity descriptor based on his data.

two entity types, noting that nationality could link to other type, position too.

so once that's loaded (gradle task run) you can do the codeGen task and get out thingys.

(TODO make branches for each step)
branch step1 will just have the model and java code.

branch step2 has generated artifacts

branch step3 has edited stuff.


in making transform, it occurs to me that a harmonize connection expects modules to be
available, but working on staging database.  so we use a connection to appserver that overrides
with database name of staging to do queries, then the in-place transform does an eval into prod

missing values from source requies logic -- null-node was a special new one for this particular
dataset


Team TDE needed extra step to go to denormalized doc.

