# Archive Heavy Package

This local dependency is intentionally large and file-heavy. It is installed by
the root npm project and by the nested pnpm project so the parser materializes
real `node_modules` layouts before producing `compiled.tar.gz`.
