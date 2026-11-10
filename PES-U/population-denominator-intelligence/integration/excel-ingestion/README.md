# excel-ingestion: WorldPop population column

This folder holds a proposed change to the HCM excel-ingestion service, shared for review.
It is not applied to `egovernments/health-campaign-services`. The HCM team decides whether and
how it is merged.

## What it does

When excel-ingestion generates the campaign boundary sheet, it adds a grey, locked column
`HCM_ADMIN_CONSOLE_WORLDPOP_POPULATION` right after the boundary code column, filled with the
WorldPop population estimate that PDI holds for each boundary code. The value is reference only:

- it is not part of the MDMS target schema, so it is never validated;
- it is removed before rows are saved, so it never reaches `eg_ex_in_sheet_data_temp` or
  project-factory;
- the lookup fails open: if PDI is down, slower than 3 seconds, or has no data for the
  hierarchy, the sheet is generated without the column.

The change follows section 9 of the solution design "WorldPop Population in the Excel Ingestion
Boundary Sheet".

## Files changed

All under `health-services/excel-ingestion/`.

| File | Change |
|------|--------|
| `config/ProcessingConstants.java` | Column name, description key, `REFERENCE_ONLY_COLUMNS` |
| `config/ExcelIngestionConfig.java`, `application.properties` | PDI host and path, feature flag, chunk size, timeout |
| `service/PopulationService.java` | New. One batched `_search` call to PDI per 5,000 codes |
| `generator/BoundaryHierarchySheetGenerator.java` | Adds and fills the column |
| `service/ConfigBasedProcessingService.java` | Removes reference-only columns before saving |
| Tests | `PopulationServiceTest`, `ReferenceOnlyColumnPersistenceTest`, two generator tests |

## Settings

| Property | Default |
|----------|---------|
| `egov.excel.worldpop-column-enabled` | `false` |
| `egov.pdi.host` | `http://localhost:8080/` |
| `egov.pdi.boundary.population.search.path` | `population/v1/boundary-population/_search` |
| `egov.pdi.search.chunk-size` | `5000` |
| `egov.pdi.search.timeout-ms` | `3000` |

With the flag off, excel-ingestion makes no call to PDI and behaves exactly as before.

## Applying and testing

The patch is made against `health-campaign-services` commit `c1dc738`.

```bash
cd health-campaign-services
git apply --check worldpop-population-column.patch
git apply worldpop-population-column.patch
cd health-services/excel-ingestion
mvn test -DskipTests=false
```

Verified on JDK 17: the patch applies cleanly and the full suite passes (670 tests, 0 failures).

## Rollout order

1. Apply the change with the flag off. This is safe before PDI is deployed.
2. Deploy PDI in the environment, upload boundary locations and complete a population run.
3. Turn the flag on in that environment, then check the generated sheet.

Do not turn the flag on until PDI is running in that environment. Rollback is turning the flag off.

The column header and description also need localization messages in the `hcm-admin-schemas`
module, for each language in use:

| Key | Message (en_IN) |
|-----|-----------------|
| `HCM_ADMIN_CONSOLE_WORLDPOP_POPULATION` | WorldPop estimated population (reference only) |
| `HCM_ADMIN_CONSOLE_WORLDPOP_POPULATION_DESCRIPTION` | WorldPop estimate for this boundary. Reference only; this value is not saved. |
