# SmartSpawner Docs

Documentation site for [SmartSpawner](https://github.com/OpenVdra/SmartSpawner), built with [VitePress](https://vitepress.dev/).

## Structure

```text
docs/
|-- .vitepress/
|   |-- components/          # Shared Vue components
|   |-- theme/               # Theme layout and styles
|   `-- config.mts           # Navigation and sidebar config
|-- docs/
|   |-- features/
|   |   |-- index.md         # Feature overview
|   |   `-- *.md             # One page per feature
|   |-- integrations/
|   |   |-- index.md         # Integration overview
|   |   `-- *.md             # One page per integration topic
|   |-- developer-api/       # Developer API pages
|   `-- *.md                 # General documentation pages
|-- vi/
|   |-- docs/                # Vietnamese mirror of every documentation page
|   `-- index.md             # Vietnamese home page
|-- public/                  # Static assets
|-- index.md                 # Home page
`-- package.json
```

Keep feature and integration topics in their matching directories. Each topic should have its own Markdown file and sidebar entry; use each directory's `index.md` only as the overview page.

## Internationalization

VitePress serves English from `/` and Vietnamese from `/vi/`. Locale-specific
navigation, sidebars, edit links, and UI labels are configured in
`.vitepress/config.mts`. The language dropdown keeps the equivalent page path
when switching languages.

Every English page under `docs/` must have a translated counterpart under
`vi/docs/`. Use `/vi/docs/...` links inside Vietnamese Markdown.

## Development

```bash
npm install
npm run docs:dev
npm run docs:build
npm run docs:preview
```

The development server defaults to `http://localhost:5173`. Production output is generated in `.vitepress/dist/`.

## Custom Components

All components are registered through the VitePress theme.

| Component | Usage |
|---|---|
| `<CommandRow>` | Display a command with its permission node |
| `<PermCommandRow>` | Compact command and permission table row |
| `<PermRow>` | Standalone permission row |
| `<ConfigProperty>` | Config key with type, default, and description |
| `<ConfigGroup>` | Group multiple configuration properties |
| `<DocCard>` | Linked navigation card |
| `<FeatureCard>` | Feature detail card |
| `<CardGrid>` | Responsive card grid |
| `<LucideIcon>` | Render an icon from the shared Lucide registry |
| `<LanguageDropdown>` | Switch between corresponding English and Vietnamese pages |

## Contributing

Edit the relevant Markdown file under `docs/` and open a pull request. Register new pages in `.vitepress/config.mts` under `themeConfig.sidebar`.
