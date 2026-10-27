# MySQL audit columns

These scripts target the three MySQL schemas used by the services:

| Schema | `ldb_id` |
|---|---:|
| `moneypal_fd` | 1 |
| `moneypal_customer` | 2 |
| `moneypal_product` | 3 |

The apply runner discovers every `BASE TABLE` in those schemas at runtime, including framework tables such as `flyway_schema_history`. It does not include views or other schemas. Connect it to the same MySQL server selected by the running services. Compose databases and the previously inspected host MySQL are separate destinations, so check `@@hostname`, port, and version before applying.

## Column behavior

- `host_timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)`: UTC. The generated `BEFORE INSERT` and `BEFORE UPDATE` triggers assign `UTC_TIMESTAMP(6)`, independent of the client session timezone.
- `local_timestamp DATETIME(6) NULL DEFAULT NULL`: backfilled for existing rows. Future application writes must supply the originating node's UTC timestamp; MySQL cannot infer that client clock. The current services do not automatically populate this new generic column.
- `crud_value CHAR(1) NOT NULL DEFAULT 'C'`: triggers set `C` on insert and `U` on update. Reads and hard deletes are not represented in this column.
- `ldb_id BIGINT NOT NULL DEFAULT <schema id>`: values are 1 for FD, 2 for customer, and 3 for product.
- Existing rows use the first non-NULL recognized timestamp column, preferring `updated_*`, `modified_*`, `created_*`, then `inserted_*`, for both timestamp fields. Names ending in `_at` or `_date` are recognized when their MySQL type is `DATETIME` or `TIMESTAMP`. If none is present, both are set to the UTC time captured at the start of the apply run. Existing non-NULL audit values are preserved.
- Timestamp candidates are copied as stored. Since MySQL `DATETIME` has no timezone metadata, confirm that any recognized source columns contain UTC values before applying; otherwise edit the candidate-selection section in the generator to use the migration timestamp.
- Backfill statements update at most 1,000 rows at a time and order by primary key when available. The manifest prevents completed tables from being backfilled again, so future NULL `local_timestamp` values remain NULL.

The scripts create `_codex_audit_columns_manifest` in `moneypal_fd`, and then audit it like every other base table. It records columns and triggers created by this run. Rollback drops only recorded objects and preserves same-named columns that existed before the script ran.

## Requirements and apply

Use PowerShell and the MySQL command-line client. Set up a MySQL login path so credentials are not placed in command history or generated files:

```powershell
mysql_config_editor set --login-path=moneypal-audit --host=<mysql-host> --user=<migration-user> --password
```

The migration account needs metadata visibility and `SELECT`, `ALTER`, and `UPDATE` on the target tables; `CREATE`, `SELECT`, `INSERT`, and (for rollback) `DELETE` on `moneypal_fd`; `CREATE ROUTINE`/`DROP ROUTINE`; and `TRIGGER`/`DROP` trigger permissions. MySQL binary logging policy can require elevated privileges or DBA configuration for trigger creation. The project’s previous audit investigation found that the active host had binary logging with `log_bin_trust_function_creators=OFF` and the service account could not reliably install triggers. Use a migration account allowed by that server policy. If trigger creation fails, the columns/backfill may already have applied because MySQL DDL auto-commits, while the requested `crud_value='U'` behavior remains inactive; the manifest supports rerunning after resolving the privilege issue.

Before applying, take a database backup and pause application writes. Generate the SQL first; generation reads server metadata but does not alter the database:

```powershell
.\scripts\database\audit-columns\apply-audit-columns.ps1 -LoginPath moneypal-audit
```

Review `generated-audit-columns.sql`, especially the printed server identity and the timestamp candidates. To generate and execute in one command after review, use:

```powershell
.\scripts\database\audit-columns\apply-audit-columns.ps1 -LoginPath moneypal-audit -Apply
```

Optional `-HostName` and `-Port` override the login-path endpoint. `-UserName` can override the account name saved in the login path. Omit `-Port` to use the port saved in that login path. The generated SQL uses MySQL client `DELIMITER` directives for per-table backfill procedures and triggers; run it with the MySQL client rather than a generic SQL editor that does not support `DELIMITER`.

Run [verify-audit-columns.sql](verify-audit-columns.sql) after apply. An empty result means every discovered base table has all four column names. The query checks column presence; it does not verify data values, column definitions, or trigger installation.

## Roll back

Generate and review the compensating SQL:

```powershell
.\scripts\database\audit-columns\rollback-audit-columns.ps1 -LoginPath moneypal-audit
```

Execute it with `-Apply` only after confirming the target server and reviewing `generated-rollback-audit-columns.sql`:

```powershell
.\scripts\database\audit-columns\rollback-audit-columns.ps1 -LoginPath moneypal-audit -Apply
```

Rollback drops only columns and triggers recorded in the manifest, removes their stored values, and removes the manifest if this apply run created it. MySQL DDL auto-commits, so rollback is compensating rather than transactional.

## Verification status

The initial pass was applied on 2026-10-25 to LAPTOP-E1I47JH1:3306 (MySQL 8.0.44), the active host used by the three services. It covered 70 application tables plus the manifest helper table, for 71 total: 21 in moneypal_customer, 20 in moneypal_product, and 30 in moneypal_fd. It installed 142 audit triggers. A pre-change dump of each schema was saved outside the repository and MySQL read_only was restored to OFF after the apply.

On 2026-10-28, the apply flow added the four audit columns and insert/update triggers to six newer customer tables and four FD tables. The verified schema now has 81 base tables: 27 in moneypal_customer, 20 in moneypal_product, and 34 in moneypal_fd. All 81 tables have the four audit columns and both triggers (162 triggers total). The 31 existing rows across the 10 newly covered tables have non-NULL host and local timestamps, valid C/U CRUD values, and the expected schema ldb_id. A full pre-change dump of all three schemas was saved outside the repository; MySQL read_only was restored to OFF.

The generated runner is resumable after a partial apply: it catches up rows created while the migration was incomplete and finalizes the manifest table after other table metadata is recorded. `local_timestamp` remains nullable and application-set for future rows.
