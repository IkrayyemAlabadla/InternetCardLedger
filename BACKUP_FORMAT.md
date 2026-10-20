# Internet Card Ledger backup format

The application exports one UTF-8 JSON document with MIME type `application/json`.
Format version 1 has this top-level shape:

```json
{
  "format": "internet-card-ledger-backup",
  "version": 1,
  "createdAt": 1700000000000,
  "deviceId": "UUID",
  "customers": [],
  "sales": [],
  "payments": []
}
```

Every record contains its UUID, `createdAt`, `updatedAt`, `createdByDeviceId`,
`syncStatus`, and `isDeleted` fields in addition to its business fields. Monetary
values use `Long` minor units. Relationships retain their UUID references:
`sales.customerId`, `payments.customerId`, and `payments.saleId`.

Both active and soft-deleted rows are exported. Import accepts only version 1,
validates all required values, UUIDs, timestamps, enum values, duplicate IDs, and
relationships before opening the restore transaction. Unknown JSON fields are
ignored for forward compatibility, while every version-1 required field must be
present.

Merge compares records with the same UUID by `updatedAt`. A newer imported row
wins, an older row is ignored, and equal content is unchanged. Different content
with the same UUID and timestamp preserves the local row and marks it `CONFLICT`.
Replace deletes and restores all three tables in a single Room transaction after
an automatic internal safety backup has been written.
