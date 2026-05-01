# Archive Pressure Fixture

This branch intentionally keeps the Solidity surface small while adding
non-contract files that exercise archive creation, transport, extraction, and
linked repository handling.

The fixture is useful for validating that analysis infrastructure can process:

- many non-Solidity archive members without increasing detector fan-out;
- cache/store-like folders that should be observable in materialized dependency
  metadata;
- symlink entries preserved by tar extraction;
- a moderate high-entropy binary payload that does not compress well.

The contracts under `contracts/` are unchanged from `with-linked-repo`.
