# Developer API

SmartSpawner provides a Java API for reading and modifying spawners, listening to lifecycle events, and extending plugin behavior.

## Quick Navigation

<CardGrid>

<DocCard icon="Package" title="Installation" link="/developer-api/installation" desc="Add SmartSpawner as a dependency with JitPack." />

<DocCard icon="Wrench" title="API Creation" link="/developer-api/creation" desc="Get the API instance and initialize your integration." />

<DocCard icon="Server" title="Data Access" link="/developer-api/data-access" desc="Read and modify stack size, storage, experience, and other properties." />

<DocCard icon="Zap" title="Events" link="/developer-api/events" desc="Listen to spawner placement, removal, generation, selling, and other lifecycle events." />

<DocCard icon="Palette" title="GUI Layout API" link="/developer-api/gui-layout" desc="Register and inject custom GUI layout providers." />

<DocCard icon="Check" title="Validation" link="/developer-api/validation" desc="Validate spawner data and supported mob types." />

<DocCard icon="FileCode2" title="Examples" link="/developer-api/examples" desc="Follow complete examples for common integration patterns." />

</CardGrid>

## Overview

The API follows a provider pattern:

```java
SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
```

It supports:

- Reading and modifying spawner properties such as stack size, delay, and range
- Accessing and changing stored items and experience
- Creating and removing spawners programmatically
- Listening to placement, break, generation, and selling events
- Registering custom GUI layout providers
- Validating mob types and spawner data
