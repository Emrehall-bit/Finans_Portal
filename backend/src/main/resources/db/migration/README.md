Initial Flyway strategy

- Current schema is managed by Hibernate `ddl-auto=update`.
- Do not create a blind `V1__init.sql` from entities in this step.
- First safe migration step should be a reviewed baseline created from the real database schema, not inferred only from Java entities.
- After that baseline is prepared and verified, Flyway can be enabled in a controlled step.
